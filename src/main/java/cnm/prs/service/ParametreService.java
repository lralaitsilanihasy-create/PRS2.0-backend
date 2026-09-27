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
}
