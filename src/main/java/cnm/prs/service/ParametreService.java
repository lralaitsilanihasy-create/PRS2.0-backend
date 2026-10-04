package cnm.prs.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Parametre;
import cnm.prs.repository.ParametreRepository;
import cnm.prs.security.CurrentUser;

/**
 * Paramètres système ({@code t_parametre}, clé/valeur) — éditables sans redéploiement.
 */
@Service
@Transactional
public class ParametreService {

    /** Interrupteur global des actualités (spec 2026-08-18). */
    public static final String ACTUALITES_ACTIVES = "ACTUALITES_ACTIVES";

    /**
     * ⚠️ Arbitrage pilote (2026-09-07, suite) — <strong>seuil de montant</strong> au-delà duquel un marché
     * passé selon un mode à déclenchement <em>conditionnel</em> (l'appel à manifestation d'intérêt) rend
     * l'AGPM requis. Administrable : le pilote le saisit et l'ajuste depuis l'administration, sans
     * redéploiement — aucune valeur numérique n'est écrite dans le code.
     */
    public static final String AGPM_SEUIL_MONTANT = "AGPM_SEUIL_MONTANT";

    private final ParametreRepository repository;

    public ParametreService(ParametreRepository repository) {
        this.repository = repository;
    }

    /**
     * État de l'interrupteur global des actualités. Ligne absente = <strong>actif</strong> :
     * c'est un coupe-circuit (chaque actualité naît de toute façon INACTIF — l'activation
     * reste un acte délibéré), pas une seconde activation à cocher.
     */
    @Transactional(readOnly = true)
    public boolean actualitesActives() {
        return repository.findById(ACTUALITES_ACTIVES)
                .map(p -> !"false".equalsIgnoreCase(p.getValeur()))
                .orElse(true);
    }

    /**
     * Seuil courant du déclenchement AGPM conditionnel (cf. {@link #AGPM_SEUIL_MONTANT}).
     *
     * <p><strong>Ligne absente ou illisible = zéro</strong>, c'est-à-dire « tout marché du mode concerné
     * déclenche l'AGPM ». Le défaut penche du côté de la publicité : manquer un AGPM dû est un manquement
     * réglementaire, en produire un de trop ne l'est pas. Le pilote resserre ensuite d'une saisie.</p>
     */
    @Transactional(readOnly = true)
    public java.math.BigDecimal seuilAgpmMontant() {
        return repository.findById(AGPM_SEUIL_MONTANT)
                .map(Parametre::getValeur)
                .map(v -> {
                    try {
                        return new java.math.BigDecimal(v.trim());
                    } catch (RuntimeException e) {
                        return java.math.BigDecimal.ZERO;
                    }
                })
                .orElse(java.math.BigDecimal.ZERO);
    }

    /** Fixe le seuil (Administrateur) — upsert horodaté avec l'identité JWT. Refuse une valeur négative. */
    public java.math.BigDecimal fixerSeuilAgpmMontant(java.math.BigDecimal seuil) {
        if (seuil == null || seuil.signum() < 0) {
            throw new cnm.prs.exception.BadRequestException(
                    "Le seuil de déclenchement de l'AGPM doit être un montant positif ou nul.");
        }
        Parametre p = repository.findById(AGPM_SEUIL_MONTANT)
                .orElseGet(() -> new Parametre(AGPM_SEUIL_MONTANT, null, null, null));
        p.setValeur(seuil.toPlainString());
        p.setDateMaj(LocalDateTime.now());
        p.setImActeur(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        repository.save(p);
        return seuil;
    }

    /**
     * ⚠️ V45 (2026-09-25, formulaires du candidat, §B4) — contrôle du taux de la garantie de soumission rapportée au
     * montant maximum du lot : taux de référence (2 % au départ) et bornes basse et haute, à fixer par le pilote. Un
     * avertissement, jamais bloquant ; sans borne, le taux est seulement constaté. Aucune valeur dans le code.
     */
    public static final String FICHE_GARANTIE_TAUX_REFERENCE = "FICHE_GARANTIE_TAUX_REFERENCE";
    public static final String FICHE_GARANTIE_TAUX_BORNE_BASSE = "FICHE_GARANTIE_TAUX_BORNE_BASSE";
    public static final String FICHE_GARANTIE_TAUX_BORNE_HAUTE = "FICHE_GARANTIE_TAUX_BORNE_HAUTE";

    /** ⚠️ V45 — taux de TVA des bordereaux des prix générés (20 % au départ). */
    public static final String FICHE_TAUX_TVA = "FICHE_TAUX_TVA";

    /** Les trois paramètres du contrôle du taux de garantie ; {@code null} : non fixé. */
    public record TauxGarantie(java.math.BigDecimal reference, java.math.BigDecimal borneBasse,
            java.math.BigDecimal borneHaute) {
    }

    @Transactional(readOnly = true)
    public TauxGarantie tauxGarantie() {
        return new TauxGarantie(nombre(FICHE_GARANTIE_TAUX_REFERENCE), nombre(FICHE_GARANTIE_TAUX_BORNE_BASSE),
                nombre(FICHE_GARANTIE_TAUX_BORNE_HAUTE));
    }

    /**
     * Fixe les trois paramètres (Administrateur) ; une valeur nulle efface le paramètre. 400 : taux négatif ou au-delà
     * de 100 %, borne basse au-dessus de la borne haute.
     */
    public TauxGarantie fixerTauxGarantie(TauxGarantie t) {
        for (java.math.BigDecimal v : java.util.Arrays.asList(t.reference(), t.borneBasse(), t.borneHaute())) {
            if (v != null && (v.signum() < 0 || v.compareTo(new java.math.BigDecimal("100")) > 0)) {
                throw new cnm.prs.exception.BadRequestException("Un taux de garantie va de 0 à 100 %.");
            }
        }
        if (t.borneBasse() != null && t.borneHaute() != null && t.borneBasse().compareTo(t.borneHaute()) > 0) {
            throw new cnm.prs.exception.BadRequestException("La borne basse dépasse la borne haute.");
        }
        ecrire(FICHE_GARANTIE_TAUX_REFERENCE, t.reference());
        ecrire(FICHE_GARANTIE_TAUX_BORNE_BASSE, t.borneBasse());
        ecrire(FICHE_GARANTIE_TAUX_BORNE_HAUTE, t.borneHaute());
        return tauxGarantie();
    }

    /** ⚠️ V46 (2026-09-25, §B7) — le taux de TVA des bordereaux, en % ; {@code null} : non fixé. */
    public record TauxTva(java.math.BigDecimal taux) {
    }

    /** Fixe le taux de TVA (Administrateur) ; {@code null} l'efface. 400 hors 0–100. */
    public TauxTva fixerTauxTva(TauxTva t) {
        java.math.BigDecimal v = t == null ? null : t.taux();
        if (v != null && (v.signum() < 0 || v.compareTo(new java.math.BigDecimal("100")) > 0)) {
            throw new cnm.prs.exception.BadRequestException("Un taux de TVA va de 0 à 100 %.");
        }
        ecrire(FICHE_TAUX_TVA, v);
        return new TauxTva(tauxTva());
    }

    /** Taux de TVA des bordereaux ; {@code null} si non fixé (le bordereau n'a alors pas de ligne TVA). */
    @Transactional(readOnly = true)
    public java.math.BigDecimal tauxTva() {
        return nombre(FICHE_TAUX_TVA);
    }

    /**
     * ⚠️ V50 (2026-09-27, remise électronique, §B1.4) — les sept paramètres administrables de la remise électronique,
     * servis par {@code GET/PUT /api/parametres/fiche-remise-electronique} ({@link RemiseElectronique.Parametres}). Les
     * défauts « = paramètre » du référentiel ({@code valeurDefaut = PARAM:<CLE>}) se recopient à la création d'une fiche
     * depuis le paramètre du moment. {@code FICHE_SE_DELAI_MIN_REMISE_JOURS} : 30 proposé, à faire fixer par le pilote.
     */
    public static final String FICHE_SE_PLATEFORME_URL = "FICHE_SE_PLATEFORME_URL";
    public static final String FICHE_SE_FUSEAU = "FICHE_SE_FUSEAU";
    public static final String FICHE_SE_SIGNATURE_MIN = "FICHE_SE_SIGNATURE_MIN";
    public static final String FICHE_SE_TAILLE_MAX_PLATEFORME_MO = "FICHE_SE_TAILLE_MAX_PLATEFORME_MO";
    public static final String FICHE_SE_DELAI_MIN_REMISE_JOURS = "FICHE_SE_DELAI_MIN_REMISE_JOURS";
    public static final String FICHE_SE_ASSISTANCE = "FICHE_SE_ASSISTANCE";
    public static final String FICHE_SE_QUORUM_DEFAUT = "FICHE_SE_QUORUM_DEFAUT";
    /** Préfixe d'une valeur par défaut de champ qui se lit dans un paramètre ({@code PARAM:FICHE_SE_PLATEFORME_URL}). */
    public static final String PREFIXE_DEFAUT_PARAMETRE = "PARAM:";

    @Transactional(readOnly = true)
    public RemiseElectronique.Parametres remiseElectronique() {
        java.math.BigDecimal taille = nombre(FICHE_SE_TAILLE_MAX_PLATEFORME_MO);
        java.math.BigDecimal delai = nombre(FICHE_SE_DELAI_MIN_REMISE_JOURS);
        return new RemiseElectronique.Parametres(texte(FICHE_SE_PLATEFORME_URL), texte(FICHE_SE_FUSEAU),
                texte(FICHE_SE_SIGNATURE_MIN), taille == null ? null : taille.intValue(),
                delai == null ? null : delai.intValue(), texte(FICHE_SE_ASSISTANCE), texte(FICHE_SE_QUORUM_DEFAUT));
    }

    /**
     * Fixe les sept paramètres (Administrateur) : l'état complet, {@code null} efface. 400 nominatif : adresse non
     * {@code http}/{@code https}, niveau hors Qualifiée / Avancée / Simple, taille ou délai négatifs, quorum hors « n/m ».
     */
    public RemiseElectronique.Parametres fixerRemiseElectronique(RemiseElectronique.Parametres p) {
        if (p == null) {
            p = new RemiseElectronique.Parametres(null, null, null, null, null, null, null);
        }
        java.util.List<cnm.prs.exception.ErrorResponse.FieldError> erreurs = new java.util.ArrayList<>();
        if (p.plateformeUrl() != null && !p.plateformeUrl().isBlank() && !RemiseElectronique.urlValide(p.plateformeUrl().trim())) {
            erreurs.add(new cnm.prs.exception.ErrorResponse.FieldError("plateformeUrl",
                    "L'adresse de la plateforme attend une adresse http ou https (500 caractères au plus)."));
        }
        if (p.signatureMin() != null && !p.signatureMin().isBlank() && RemiseElectronique.rangNiveau(p.signatureMin()) < 0) {
            erreurs.add(new cnm.prs.exception.ErrorResponse.FieldError("signatureMin",
                    "Le niveau minimal de signature est Qualifiée, Avancée ou Simple."));
        }
        if (p.tailleMaxPlateformeMo() != null && p.tailleMaxPlateformeMo() <= 0) {
            erreurs.add(new cnm.prs.exception.ErrorResponse.FieldError("tailleMaxPlateformeMo",
                    "La taille maximale de la plateforme est un nombre de Mo strictement positif."));
        }
        if (p.delaiMinRemiseJours() != null && p.delaiMinRemiseJours() < 0) {
            erreurs.add(new cnm.prs.exception.ErrorResponse.FieldError("delaiMinRemiseJours",
                    "Le délai minimal entre publication et remise est un nombre de jours positif ou nul."));
        }
        if (p.quorumDefaut() != null && !p.quorumDefaut().isBlank() && !p.quorumDefaut().trim().matches("\\d+\\s*/\\s*\\d+")) {
            erreurs.add(new cnm.prs.exception.ErrorResponse.FieldError("quorumDefaut",
                    "Le quorum par défaut s'écrit « n/m » (par exemple 3/5)."));
        }
        if (!erreurs.isEmpty()) {
            throw new cnm.prs.exception.ChampsInvalidesException(erreurs);
        }
        ecrireTexte(FICHE_SE_PLATEFORME_URL, p.plateformeUrl());
        ecrireTexte(FICHE_SE_FUSEAU, p.fuseau());
        ecrireTexte(FICHE_SE_SIGNATURE_MIN, p.signatureMin() == null ? null
                : RemiseElectronique.NIVEAUX.get(RemiseElectronique.rangNiveau(p.signatureMin())));
        ecrireTexte(FICHE_SE_TAILLE_MAX_PLATEFORME_MO, p.tailleMaxPlateformeMo() == null ? null : String.valueOf(p.tailleMaxPlateformeMo()));
        ecrireTexte(FICHE_SE_DELAI_MIN_REMISE_JOURS, p.delaiMinRemiseJours() == null ? null : String.valueOf(p.delaiMinRemiseJours()));
        ecrireTexte(FICHE_SE_ASSISTANCE, p.assistance());
        ecrireTexte(FICHE_SE_QUORUM_DEFAUT, p.quorumDefaut() == null ? null : p.quorumDefaut().replace(" ", ""));
        return remiseElectronique();
    }

    // ------------------------------------------------------------------ candidats (⚠️ V63, 2026-10-04)

    public static final String CANDIDAT_VERIFICATION_NIF = "CANDIDAT_VERIFICATION_NIF";
    public static final String CANDIDAT_CONFIRMATION_TELEPHONE = "CANDIDAT_CONFIRMATION_TELEPHONE";
    public static final String CANDIDAT_INSCRIPTIONS_PAR_JOUR = "CANDIDAT_INSCRIPTIONS_PAR_JOUR";
    public static final String CANDIDAT_DELAI_CONFIRMATION_JOURS = "CANDIDAT_DELAI_CONFIRMATION_JOURS";
    public static final String CANDIDAT_DELAI_INACTIVITE_MOIS = "CANDIDAT_DELAI_INACTIVITE_MOIS";
    public static final String CANDIDAT_TAILLE_MAX_PIECE_MO = "CANDIDAT_TAILLE_MAX_PIECE_MO";
    static final java.util.Set<String> VERIFICATIONS_NIF = java.util.Set.of("AUTOMATIQUE", "SUR_PIECES");

    /**
     * ⚠️ V63 (demande front du 2026-10-04, soumission en ligne, §B7) — les paramètres des comptes candidats
     * ({@code GET / PUT /api/parametres/candidats}). Absents : leurs valeurs par défaut (SUR_PIECES, NON, 5, 7, 24, 10).
     */
    public record ParametresCandidats(String verificationNif, Boolean confirmationTelephone, Integer inscriptionsParJour,
            Integer delaiConfirmationJours, Integer delaiInactiviteMois, Integer tailleMaxPieceMo) {
    }

    @Transactional(readOnly = true)
    public ParametresCandidats candidats() {
        String nif = texte(CANDIDAT_VERIFICATION_NIF);
        return new ParametresCandidats(nif != null && VERIFICATIONS_NIF.contains(nif) ? nif : "SUR_PIECES",
                "OUI".equalsIgnoreCase(texte(CANDIDAT_CONFIRMATION_TELEPHONE)),
                entier(CANDIDAT_INSCRIPTIONS_PAR_JOUR, 5), entier(CANDIDAT_DELAI_CONFIRMATION_JOURS, 7),
                entier(CANDIDAT_DELAI_INACTIVITE_MOIS, 24), entier(CANDIDAT_TAILLE_MAX_PIECE_MO, 10));
    }

    /** Écriture par l'Administrateur ; un champ absent garde sa valeur ; 400 nominatif. */
    public ParametresCandidats fixerCandidats(ParametresCandidats p) {
        java.util.List<cnm.prs.exception.ErrorResponse.FieldError> erreurs = new java.util.ArrayList<>();
        if (p.verificationNif() != null && !VERIFICATIONS_NIF.contains(p.verificationNif())) {
            erreurs.add(new cnm.prs.exception.ErrorResponse.FieldError("verificationNif", "AUTOMATIQUE ou SUR_PIECES."));
        }
        borne(erreurs, "inscriptionsParJour", p.inscriptionsParJour(), 1, 1000);
        borne(erreurs, "delaiConfirmationJours", p.delaiConfirmationJours(), 1, 365);
        borne(erreurs, "delaiInactiviteMois", p.delaiInactiviteMois(), 1, 240);
        borne(erreurs, "tailleMaxPieceMo", p.tailleMaxPieceMo(), 1, 100);
        if (!erreurs.isEmpty()) {
            throw new cnm.prs.exception.ChampsInvalidesException(erreurs);
        }
        if (p.verificationNif() != null) {
            ecrireTexte(CANDIDAT_VERIFICATION_NIF, p.verificationNif());
        }
        if (p.confirmationTelephone() != null) {
            ecrireTexte(CANDIDAT_CONFIRMATION_TELEPHONE, p.confirmationTelephone() ? "OUI" : "NON");
        }
        ecrireEntier(CANDIDAT_INSCRIPTIONS_PAR_JOUR, p.inscriptionsParJour());
        ecrireEntier(CANDIDAT_DELAI_CONFIRMATION_JOURS, p.delaiConfirmationJours());
        ecrireEntier(CANDIDAT_DELAI_INACTIVITE_MOIS, p.delaiInactiviteMois());
        ecrireEntier(CANDIDAT_TAILLE_MAX_PIECE_MO, p.tailleMaxPieceMo());
        return candidats();
    }

    private int entier(String cle, int defaut) {
        java.math.BigDecimal n = nombre(cle);
        return n == null || n.signum() <= 0 ? defaut : n.intValue();
    }

    private void ecrireEntier(String cle, Integer valeur) {
        if (valeur != null) {
            ecrireTexte(cle, String.valueOf(valeur));
        }
    }

    private static void borne(java.util.List<cnm.prs.exception.ErrorResponse.FieldError> erreurs, String champ, Integer v, int min, int max) {
        if (v != null && (v < min || v > max)) {
            erreurs.add(new cnm.prs.exception.ErrorResponse.FieldError(champ, "De " + min + " à " + max + "."));
        }
    }

    /** La valeur texte d'un paramètre ; {@code null} si absent ou vide. */
    @Transactional(readOnly = true)
    public String texte(String cle) {
        return repository.findById(cle).map(Parametre::getValeur).filter(v -> v != null && !v.isBlank()).map(String::trim)
                .orElse(null);
    }

    /**
     * ⚠️ V50 — la valeur par défaut d'un champ du référentiel : telle quelle, ou lue dans le paramètre qu'elle nomme
     * ({@code PARAM:<CLE>}, {@code null} si le paramètre n'est pas fixé).
     */
    @Transactional(readOnly = true)
    public String valeurDefaut(String valeurDefaut) {
        if (valeurDefaut == null) {
            return null;
        }
        if (valeurDefaut.startsWith(PREFIXE_DEFAUT_PARAMETRE)) {
            return texte(valeurDefaut.substring(PREFIXE_DEFAUT_PARAMETRE.length()).trim());
        }
        return valeurDefaut;
    }

    private void ecrireTexte(String cle, String valeur) {
        if (valeur == null || valeur.isBlank()) {
            repository.deleteById(cle);
            return;
        }
        Parametre p = repository.findById(cle).orElseGet(() -> new Parametre(cle, null, null, null));
        p.setValeur(valeur.trim());
        p.setDateMaj(LocalDateTime.now());
        p.setImActeur(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        repository.save(p);
    }

    private java.math.BigDecimal nombre(String cle) {
        return repository.findById(cle).map(Parametre::getValeur).filter(v -> v != null && !v.isBlank()).map(v -> {
            try {
                return new java.math.BigDecimal(v.trim().replace(',', '.'));
            } catch (RuntimeException e) {
                return null;
            }
        }).orElse(null);
    }

    private void ecrire(String cle, java.math.BigDecimal valeur) {
        Parametre p = repository.findById(cle).orElseGet(() -> new Parametre(cle, null, null, null));
        p.setValeur(valeur == null ? null : valeur.stripTrailingZeros().toPlainString());
        p.setDateMaj(LocalDateTime.now());
        p.setImActeur(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        repository.save(p);
    }

    /** Bascule l'interrupteur (Administrateur) — upsert horodaté avec l'identité JWT. */
    public boolean basculerActualites(boolean actif) {
        Parametre p = repository.findById(ACTUALITES_ACTIVES)
                .orElseGet(() -> new Parametre(ACTUALITES_ACTIVES, null, null, null));
        p.setValeur(Boolean.toString(actif));
        p.setDateMaj(LocalDateTime.now());
        p.setImActeur(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        repository.save(p);
        return actif;
    }

    // ------------------------------------------------------------------ compte bancaire de l'ARMP (avis spécifique, §B8)

    /**
     * ⚠️ 2026-10-01 (décision du pilote, demande front « avis spécifique » §B8) — le prix du DAO se verse sur un
     * <strong>compte bancaire unique de l'ARMP</strong>, le même pour tous les avis, réglé une fois par l'Administrateur.
     */
    public static final String DAO_COMPTE_BANQUE = "DAO_COMPTE_BANQUE";
    public static final String DAO_COMPTE_TITULAIRE = "DAO_COMPTE_TITULAIRE";
    public static final String DAO_COMPTE_NUMERO = "DAO_COMPTE_NUMERO";

    /** Le compte, et sa dernière mise à jour (date, acteur) ; champs {@code null} s'il n'est pas réglé. */
    public record CompteDao(String banque, String titulaire, String numeroCompte, LocalDateTime misAJourLe, String misAJourPar) {
    }

    /** Le corps d'un réglage du compte : les trois sont exigés. */
    public record CompteDaoRequest(String banque, String titulaire, String numeroCompte) {
    }

    @Transactional(readOnly = true)
    public CompteDao compteDao() {
        Parametre dernier = java.util.stream.Stream.of(DAO_COMPTE_BANQUE, DAO_COMPTE_TITULAIRE, DAO_COMPTE_NUMERO)
                .map(repository::findById).flatMap(java.util.Optional::stream)
                .filter(p -> p.getDateMaj() != null).max(java.util.Comparator.comparing(Parametre::getDateMaj)).orElse(null);
        return new CompteDao(texte(DAO_COMPTE_BANQUE), texte(DAO_COMPTE_TITULAIRE), texte(DAO_COMPTE_NUMERO),
                dernier == null ? null : dernier.getDateMaj(), dernier == null ? null : dernier.getImActeur());
    }

    /** Règle le compte (Administrateur) : les trois informations exigées, 400 nominatif sinon. */
    @Transactional
    public CompteDao fixerCompteDao(CompteDaoRequest c) {
        CompteDaoRequest r = c == null ? new CompteDaoRequest(null, null, null) : c;
        java.util.List<cnm.prs.exception.ErrorResponse.FieldError> erreurs = new java.util.ArrayList<>();
        if (r.banque() == null || r.banque().isBlank()) {
            erreurs.add(new cnm.prs.exception.ErrorResponse.FieldError("banque", "La banque est obligatoire."));
        }
        if (r.titulaire() == null || r.titulaire().isBlank()) {
            erreurs.add(new cnm.prs.exception.ErrorResponse.FieldError("titulaire", "Le titulaire du compte est obligatoire."));
        }
        if (r.numeroCompte() == null || r.numeroCompte().isBlank()) {
            erreurs.add(new cnm.prs.exception.ErrorResponse.FieldError("numeroCompte", "Le numéro de compte est obligatoire."));
        }
        if (!erreurs.isEmpty()) {
            throw new cnm.prs.exception.ChampsInvalidesException(erreurs);
        }
        ecrireTexte(DAO_COMPTE_BANQUE, r.banque());
        ecrireTexte(DAO_COMPTE_TITULAIRE, r.titulaire());
        ecrireTexte(DAO_COMPTE_NUMERO, r.numeroCompte());
        return compteDao();
    }

    /**
     * Le compte tel que l'avis l'imprime ({@code {{PARAM.compte-dao}}}) : « {banque}, compte n° {numeroCompte} au nom de
     * {titulaire} » ; {@code null} s'il n'est pas entièrement réglé (l'avis imprime alors des pointillés).
     */
    @Transactional(readOnly = true)
    public String compteDaoTexte() {
        CompteDao c = compteDao();
        if (c.banque() == null || c.titulaire() == null || c.numeroCompte() == null) {
            return null;
        }
        return c.banque() + ", compte n° " + c.numeroCompte() + " au nom de " + c.titulaire();
    }

}
