package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cnm.prs.dto.BilanControlesDto;
import cnm.prs.dto.BilanControlesDto.Controle;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.enums.SourceChampFiche;
import cnm.prs.enums.TypeChampFiche;

/**
 * ⚠️ <strong>Le catalogue des contrôles de la fiche marché</strong> (demande front du 2026-09-22, §B4 ; règles de
 * l'esquisse du pilote, quantité fixe).
 *
 * <p><strong>Comment une règle trouve ses champs.</strong> La liste nominative des champs n'est pas connue : une
 * règle ne peut donc pas nommer un code. C'est le <em>champ</em> qui nomme la règle, dans son attribut
 * {@code controle} — {@code REGLE} quand elle ne lit que lui, {@code REGLE:ROLE} quand elle en lit plusieurs
 * ({@code VALIDITE_GARANTIE_SUP_OFFRE:GARANTIE} et {@code :OFFRE}). Deux règles n'ont pas besoin de champ :
 * {@code OBLIGATOIRE} (tout champ obligatoire d'une rubrique ouverte) et {@code MONTANT_POSITIF} (tout
 * {@code MONTANT}). Une règle dont un rôle manque n'est pas évaluée — ni bloquante, ni « ok » : elle attend son
 * champ. Ce qui vient du cadrage (taux d'avance, type de prix) est lu dans le cadrage.</p>
 *
 * <p>Pur : des valeurs en entrée, un bilan en sortie, aucune lecture de base.</p>
 *
 * <table>
 * <tr><th>Règle</th><th>Rôles</th><th>Gravité</th></tr>
 * <tr><td>OBLIGATOIRE</td><td>—</td><td>bloquant</td></tr>
 * <tr><td>MONTANT_POSITIF</td><td>—</td><td>bloquant</td></tr>
 * <tr><td>DATES_ORDRE</td><td>LANCEMENT (à défaut : PPM), REMISE, OUVERTURE, ATTRIBUTION (à défaut : PPM)</td><td>bloquant</td></tr>
 * <tr><td>VALIDITE_GARANTIE_SUP_OFFRE</td><td>GARANTIE, OFFRE (jours)</td><td>bloquant</td></tr>
 * <tr><td>AVANCE_MAX_20</td><td>TAUX (à défaut : cadrage {@code tauxAvance})</td><td>bloquant</td></tr>
 * <tr><td>AVANCE_SUP_5_GARANTIE</td><td>TAUX (idem), GARANTIE (garantie de restitution)</td><td>bloquant</td></tr>
 * <tr><td>FORFAIT_60_40</td><td>RECEPTION (%), PV (%) — si cadrage {@code typePrix = FORFAITAIRE}</td><td>bloquant</td></tr>
 * <tr><td>PENALITES_PLAFOND_15</td><td>TAUX (%), DEROGATION (texte)</td><td>avertissement</td></tr>
 * <tr><td>INTERETS_MORATOIRES_TAUX</td><td>TAUX (%), BANQUE (%)</td><td>avertissement</td></tr>
 * <tr><td>DELAI_PAIEMENT_75</td><td>DELAI (jours)</td><td>avertissement</td></tr>
 * </table>
 */
public final class ControlesFicheMarche {

    public static final String OBLIGATOIRE = "OBLIGATOIRE";
    public static final String MONTANT_POSITIF = "MONTANT_POSITIF";
    public static final String DATES_ORDRE = "DATES_ORDRE";
    public static final String VALIDITE_GARANTIE_SUP_OFFRE = "VALIDITE_GARANTIE_SUP_OFFRE";
    public static final String AVANCE_MAX_20 = "AVANCE_MAX_20";
    public static final String AVANCE_SUP_5_GARANTIE = "AVANCE_SUP_5_GARANTIE";
    public static final String FORFAIT_60_40 = "FORFAIT_60_40";
    public static final String PENALITES_PLAFOND_15 = "PENALITES_PLAFOND_15";
    public static final String INTERETS_MORATOIRES_TAUX = "INTERETS_MORATOIRES_TAUX";
    public static final String DELAI_PAIEMENT_75 = "DELAI_PAIEMENT_75";
    /** ⚠️ V45 (2026-09-25) — le besoin et les garanties générées. */
    public static final String BESOIN_INCOMPLET = "BESOIN_INCOMPLET";
    public static final String QUANTITES_ORDRE = "QUANTITES_ORDRE";
    public static final String GARANTIE_MANQUANTE = "GARANTIE_MANQUANTE";
    public static final String GARANTIE_TAUX = "GARANTIE_TAUX";
    private static final String BLOC_BESOIN = "B12";

    /**
     * ⚠️ V50 (2026-09-27, remise électronique, §B3) — onze règles, toutes <strong>bloquantes</strong>, évaluées en mode
     * électronique seulement ({@code modeRemise = ELECTRONIQUE}). Les rôles sont ceux du référentiel (§B1.3) ; un champ
     * peut porter <strong>plusieurs</strong> contrôles séparés par des virgules ({@code DATES_ORDRE:REMISE,SE_HEURE_LIMITE:DATE}).
     * Les règles 6, 8, 10 et 11 lisent les paramètres internes et le responsable de la procédure ({@link RemiseElectroniqueBilan})
     * et se rangent sous le bloc {@code B04}.
     */
    public static final String SE_HEURE_LIMITE = "SE_HEURE_LIMITE";
    public static final String SE_OUVERTURE_DEPOTS = "SE_OUVERTURE_DEPOTS";
    public static final String SE_TAILLES = "SE_TAILLES";
    public static final String SE_SIGNATURE_MIN = "SE_SIGNATURE_MIN";
    public static final String SE_ORIGINAL_GARANTIE = "SE_ORIGINAL_GARANTIE";
    public static final String SE_QUORUM = "SE_QUORUM";
    public static final String SE_OUVERTURE_PLIS = "SE_OUVERTURE_PLIS";
    public static final String SE_CEREMONIE = "SE_CEREMONIE";
    public static final String SE_PRESTATAIRES = "SE_PRESTATAIRES";
    public static final String PARAMETRES_INTERNES_INCOMPLETS = "PARAMETRES_INTERNES_INCOMPLETS";
    public static final String RESPONSABLE_NON_DESIGNE = "RESPONSABLE_NON_DESIGNE";
    private static final String BLOC_REMISE = "B04";

    /** Libellés CAPM du plan dont les dates entrent dans {@code DATES_ORDRE} à défaut de champ. */
    public static final String PPM_LANCEMENT = "LANCEMENT";
    public static final String PPM_ATTRIBUTION = "ATTRIBUTION";

    private ControlesFicheMarche() {
    }

    /**
     * @param champsOuverts champs de la fiche dont la condition de cadrage est vraie, pour le type de marché
     * @param valeurs       valeurs saisies, par code (normalisées : nombres en chiffres, dates ISO)
     * @param cadrage       réponses du cadrage
     * @param datesPpm      dates prévisionnelles du plan par libellé CAPM ({@link #PPM_LANCEMENT}, {@link #PPM_ATTRIBUTION})
     */
    public static BilanControlesDto bilan(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs,
            Map<String, ?> cadrage, Map<String, LocalDate> datesPpm) {
        return bilan(champsOuverts, valeurs, cadrage, datesPpm, 0);
    }

    /**
     * ⚠️ 2026-09-25 (§B2) — {@code nbLots} : lots de la ligne au plan. Sur une ligne allotie, un champ {@code parLot} est
     * attendu (et, s'il est obligatoire, exigé) <strong>pour chaque lot</strong>, sous {@code CODE#n} : une ligne du
     * bilan par lot manquant, le rang dans le message ({@link LotsFiche}).
     */
    public static BilanControlesDto bilan(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs,
            Map<String, ?> cadrage, Map<String, LocalDate> datesPpm, int nbLots) {
        return bilan(champsOuverts, valeurs, cadrage, datesPpm, nbLots, null, null);
    }

    /**
     * ⚠️ V45 (2026-09-25, formulaires du candidat, §B4) — le besoin d'une fiche de fournitures ({@code articles}), et si
     * le marché est à commande (quantités minimum et maximum).
     */
    public record Besoin(List<BesoinFiche.Article> articles, boolean aCommande) {
    }

    /**
     * ⚠️ V45 — {@code besoin} : celui d'une fiche de fournitures, {@code null} hors du périmètre du besoin (pas de
     * contrôle {@code BESOIN_INCOMPLET} / {@code QUANTITES_ORDRE}) ; {@code taux} : paramètres administrables du contrôle
     * {@code GARANTIE_TAUX} ({@code null} : non évalué).
     */
    public static BilanControlesDto bilan(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs,
            Map<String, ?> cadrage, Map<String, LocalDate> datesPpm, int nbLots, Besoin besoin,
            ParametreService.TauxGarantie taux) {
        return bilan(champsOuverts, valeurs, cadrage, datesPpm, nbLots, besoin, taux, null);
    }

    /**
     * ⚠️ V50 (2026-09-27, remise électronique, §B3) — ce que les règles 1 à 11 lisent hors de la fiche : le mode, les
     * paramètres administrables ({@code FICHE_SE_*}), les paramètres internes de la procédure et la présence d'un
     * responsable désigné. {@code null}, ou {@code electronique = false} : aucune de ces règles n'est évaluée.
     */
    public record RemiseElectroniqueBilan(boolean electronique, RemiseElectronique.Parametres parametres,
            RemiseElectronique.Internes internes, boolean responsableDesigne) {
    }

    /** Les contrôles d'un champ, {@code {règle, rôle}} par contrôle ({@code REGLE} seule : rôle vide) ; V50 : plusieurs, séparés par des virgules. */
    static List<String[]> controles(ChampFicheMarche c) {
        List<String[]> out = new ArrayList<>();
        if (c.getControle() == null || c.getControle().isBlank()) {
            return out;
        }
        for (String un : c.getControle().split(",")) {
            if (un.isBlank()) {
                continue;
            }
            String[] parts = un.trim().split(":", 2);
            out.add(new String[] {parts[0].trim().toUpperCase(), parts.length > 1 ? parts[1].trim().toUpperCase() : ""});
        }
        return out;
    }

    /** ⚠️ V50 — {@code se} : le contexte de la remise électronique ({@link RemiseElectroniqueBilan}), {@code null} hors mode électronique. */
    public static BilanControlesDto bilan(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs,
            Map<String, ?> cadrage, Map<String, LocalDate> datesPpm, int nbLots, Besoin besoin,
            ParametreService.TauxGarantie taux, RemiseElectroniqueBilan se) {
        return bilan(champsOuverts, valeurs, cadrage, datesPpm, nbLots, besoin, taux, se, null);
    }

    /**
     * ⚠️ Lot D3 (2026-09-29, §B2.2.5) — et la catégorie de la fiche ({@code null} : fournitures et services), qui règle le
     * plafond des pénalités du CCAG : 10 % pour les prestations intellectuelles, 15 % sinon.
     */
    public static BilanControlesDto bilan(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs,
            Map<String, ?> cadrage, Map<String, LocalDate> datesPpm, int nbLots, Besoin besoin,
            ParametreService.TauxGarantie taux, RemiseElectroniqueBilan se, String categorie) {
        List<Controle> bloquants = new ArrayList<>();
        List<Controle> avertissements = new ArrayList<>();
        List<Controle> ok = new ArrayList<>();
        Map<String, Map<String, ChampFicheMarche>> roles = new LinkedHashMap<>();
        int nbSaisis = 0;
        int nbAttendus = 0;

        for (ChampFicheMarche c : champsOuverts) {
            // ⚠️ Lot 5 (2026-09-24) — un champ PIECE (formulaire à remplir, joint au dossier) ne se saisit pas dans la fiche :
            // ni attendu, ni obligatoire au bilan — sinon il bloquerait la validation et fausserait le compte.
            boolean saisie = SourceChampFiche.SAISIE.name().equals(c.getSource())
                    && !TypeChampFiche.PIECE.name().equals(c.getType());
            if (saisie) {
                boolean parLot = LotsFiche.parLot(c, nbLots);
                List<String> cles = LotsFiche.cles(c, nbLots);
                for (int i = 0; i < cles.size(); i++) {
                    String cle = cles.get(i);
                    String lot = parLot ? " (lot " + (i + 1) + ")" : "";
                    String v = valeurs.get(cle);
                    boolean vide = v == null || v.isBlank();
                    nbAttendus++;
                    if (!vide) {
                        nbSaisis++;
                    }
                    if (Boolean.TRUE.equals(c.getObligatoire()) && vide) {
                        bloquants.add(new Controle(OBLIGATOIRE, List.of(cle), c.codeBloc(),
                                "« " + c.getLibelle() + " »" + lot + " est obligatoire."));
                    }
                    if (TypeChampFiche.MONTANT.name().equals(c.getType()) && !vide) {
                        BigDecimal montant = nombre(v);
                        if (montant != null && montant.signum() <= 0) {
                            bloquants.add(new Controle(MONTANT_POSITIF, List.of(cle), c.codeBloc(),
                                    "« " + c.getLibelle() + " »" + lot + " doit être un montant strictement positif."));
                        } else if (montant != null) {
                            ok.add(new Controle(MONTANT_POSITIF, List.of(cle), c.codeBloc(),
                                    "« " + c.getLibelle() + " »" + lot + " : montant positif."));
                        }
                    }
                }
            }
            for (String[] rr : controles(c)) {   // V50 : plusieurs contrôles par champ
                roles.computeIfAbsent(rr[0], k -> new LinkedHashMap<>()).put(rr[1], c);
            }
        }

        datesOrdre(roles.get(DATES_ORDRE), valeurs, datesPpm, bloquants, ok);
        validiteGarantie(roles.get(VALIDITE_GARANTIE_SUP_OFFRE), valeurs, bloquants, ok);
        avance(roles.get(AVANCE_MAX_20), roles.get(AVANCE_SUP_5_GARANTIE), valeurs, cadrage, bloquants, ok);
        forfait(roles.get(FORFAIT_60_40), valeurs, cadrage, bloquants, ok);
        penalites(roles.get(PENALITES_PLAFOND_15), valeurs, plafondPenalites(categorie), avertissements, ok);
        interetsMoratoires(roles.get(INTERETS_MORATOIRES_TAUX), valeurs, avertissements, ok);
        delaiPaiement(roles.get(DELAI_PAIEMENT_75), valeurs, avertissements, ok);
        // ⚠️ V45 (2026-09-25, §B4) — le besoin, la garantie générée et son taux.
        besoin(besoin, nbLots, bloquants, ok);
        garantieManquante(roles.get(GARANTIE_MANQUANTE), valeurs, cadrage, bloquants, ok);
        garantieTaux(roles.get(GARANTIE_TAUX), valeurs, nbLots, taux, avertissements, ok);
        // ⚠️ V50 (2026-09-27, §B3) — la remise électronique : onze règles bloquantes, en mode électronique seulement.
        if (se != null && se.electronique()) {
            RemiseElectronique.Parametres p = se.parametres() == null
                    ? new RemiseElectronique.Parametres(null, null, null, null, null, null, null) : se.parametres();
            LocalDateTime echeance = RemiseElectronique.echeanceRemise(roles, valeurs);
            seHeureLimite(roles.get(SE_HEURE_LIMITE), valeurs, bloquants, ok);
            seOuvertureDepots(roles.get(SE_OUVERTURE_DEPOTS), roles.get(SE_HEURE_LIMITE), valeurs, echeance, p, bloquants, ok);
            seTailles(roles.get(SE_TAILLES), valeurs, p, bloquants, ok);
            seSignatureMin(roles.get(SE_SIGNATURE_MIN), valeurs, p, bloquants, ok);
            seOriginalGarantie(roles.get(SE_ORIGINAL_GARANTIE), valeurs, bloquants, ok);
            seQuorum(se.internes(), bloquants, ok);
            seOuverturePlis(roles.get(SE_OUVERTURE_PLIS), valeurs, echeance, bloquants, ok);
            LocalDateTime publication = publication(roles.get(SE_CEREMONIE), valeurs);
            seCeremonie(roles.get(SE_CEREMONIE), se.internes(), publication, bloquants, ok);
            sePrestataires(roles.get(SE_PRESTATAIRES), valeurs, bloquants, ok);
            parametresInternes(se.internes(), publication, bloquants, ok);
            responsable(se.responsableDesigne(), bloquants, ok);
        }

        return new BilanControlesDto(bloquants, avertissements, ok, nbSaisis, nbAttendus);
    }

    // ------------------------------------------------------------------ règles V50 (remise électronique)

    private static List<String> codes(ChampFicheMarche... champs) {
        List<String> out = new ArrayList<>();
        for (ChampFicheMarche c : champs) {
            if (c != null) {
                out.add(c.getCode());
            }
        }
        return out;
    }

    private static String bloc(String defaut, ChampFicheMarche... champs) {
        for (ChampFicheMarche c : champs) {
            if (c != null) {
                return c.codeBloc();
            }
        }
        return defaut;
    }

    private static boolean vide(String v) {
        return v == null || v.isBlank();
    }

    /** Règle 1 — {@code SE_HEURE_LIMITE} (rôles DATE, HEURE) : heure {@code HH:MM} et date un jour ouvrable (lundi-vendredi). */
    private static void seHeureLimite(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche date = r == null ? null : r.get(RemiseElectronique.ROLE_DATE);
        ChampFicheMarche heure = r == null ? null : r.get(RemiseElectronique.ROLE_HEURE);
        LocalDate d = date == null ? null : date(valeurs.get(date.getCode()));
        if (date == null || heure == null || d == null) {
            return;
        }
        String h = RemiseElectronique.heure(valeurs.get(heure.getCode()));
        List<String> champs = codes(date, heure);
        if (h == null || !JoursOuvres.estOuvre(d)) {
            bloquants.add(new Controle(SE_HEURE_LIMITE, champs, date.codeBloc(),
                    "En remise électronique, la date limite doit porter une heure (HH:MM) et tomber un jour ouvrable."));
        } else {
            ok.add(new Controle(SE_HEURE_LIMITE, champs, date.codeBloc(), "Date limite un jour ouvrable, heure " + h + "."));
        }
    }

    /** La date de publication de l'avis (rôle PUBLICATION de {@code SE_CEREMONIE}, à défaut de {@code SE_OUVERTURE_DEPOTS}). */
    private static LocalDateTime publication(Map<String, ChampFicheMarche> r, Map<String, String> valeurs) {
        ChampFicheMarche c = r == null ? null : r.get(RemiseElectronique.ROLE_PUBLICATION);
        return c == null ? null : RemiseElectronique.dateHeure(valeurs.get(c.getCode()));
    }

    /**
     * Règle 2 — {@code SE_OUVERTURE_DEPOTS} (rôles DEPOTS, PUBLICATION) : l'ouverture des dépôts précède l'échéance de
     * remise, et la publication précède la date limite d'au moins {@code delaiMinRemiseJours} jours.
     */
    private static void seOuvertureDepots(Map<String, ChampFicheMarche> r, Map<String, ChampFicheMarche> limite,
            Map<String, String> valeurs, LocalDateTime echeance, RemiseElectronique.Parametres p,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche depots = r == null ? null : r.get("DEPOTS");
        ChampFicheMarche publication = r == null ? null : r.get(RemiseElectronique.ROLE_PUBLICATION);
        ChampFicheMarche dateLimite = limite == null ? null : limite.get(RemiseElectronique.ROLE_DATE);
        LocalDate jourLimite = dateLimite == null ? null : date(valeurs.get(dateLimite.getCode()));
        LocalDateTime pub = publication == null ? null : RemiseElectronique.dateHeure(valeurs.get(publication.getCode()));
        if (depots == null || publication == null || jourLimite == null || pub == null) {
            return;
        }
        LocalDateTime borne = echeance != null ? echeance : jourLimite.atStartOfDay();
        LocalDateTime dep = RemiseElectronique.dateHeure(valeurs.get(depots.getCode()));
        Integer delai = p.delaiMinRemiseJours();
        boolean depotsOk = dep == null || dep.isBefore(borne);
        boolean publicationOk = delai == null
                || java.time.temporal.ChronoUnit.DAYS.between(pub.toLocalDate(), jourLimite) >= delai;
        List<String> champs = codes(depots, publication, dateLimite);
        if (depotsOk && publicationOk) {
            ok.add(new Controle(SE_OUVERTURE_DEPOTS, champs, depots.codeBloc(), "Ouverture des dépôts avant la date limite"
                    + (delai == null ? "" : ", publication au moins " + delai + " jours avant") + "."));
        } else {
            bloquants.add(new Controle(SE_OUVERTURE_DEPOTS, champs, depots.codeBloc(), "L'ouverture des dépôts doit précéder "
                    + "la date limite, et la publication la précéder d'au moins " + (delai == null ? "—" : delai) + " jours."));
        }
    }

    /** Règle 3 — {@code SE_TAILLES} (rôles FICHIER, OFFRE) : fichier ≤ offre ≤ {@code tailleMaxPlateformeMo}. */
    private static void seTailles(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, RemiseElectronique.Parametres p,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche fichier = r == null ? null : r.get("FICHIER");
        ChampFicheMarche offre = r == null ? null : r.get("OFFRE");
        BigDecimal f = fichier == null ? null : nombre(valeurs.get(fichier.getCode()));
        BigDecimal o = offre == null ? null : nombre(valeurs.get(offre.getCode()));
        if (f == null || o == null) {
            return;
        }
        Integer max = p.tailleMaxPlateformeMo();
        List<String> champs = codes(fichier, offre);
        if (f.compareTo(o) <= 0 && (max == null || o.compareTo(BigDecimal.valueOf(max)) <= 0)) {
            ok.add(new Controle(SE_TAILLES, champs, fichier.codeBloc(), "Tailles : " + f.stripTrailingZeros().toPlainString()
                    + " Mo par fichier ≤ " + o.stripTrailingZeros().toPlainString() + " Mo par offre"
                    + (max == null ? "" : " ≤ " + max + " Mo (plateforme)") + "."));
        } else {
            bloquants.add(new Controle(SE_TAILLES, champs, fichier.codeBloc(), "La taille par fichier doit être inférieure "
                    + "ou égale à la taille par offre, elle-même limitée à " + (max == null ? "—" : max) + " Mo par la plateforme."));
        }
    }

    /** Règle 4 — {@code SE_SIGNATURE_MIN} (rôle NIVEAU) : niveau exigé ≥ {@code signatureMin} (Qualifiée > Avancée > Simple). */
    private static void seSignatureMin(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            RemiseElectronique.Parametres p, List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche niveau = r == null ? null : r.get("NIVEAU");
        String v = niveau == null ? null : valeurs.get(niveau.getCode());
        int rang = RemiseElectronique.rangNiveau(v);
        int min = RemiseElectronique.rangNiveau(p.signatureMin());
        if (niveau == null || rang < 0 || min < 0) {
            return;
        }
        if (rang >= min) {
            ok.add(new Controle(SE_SIGNATURE_MIN, codes(niveau), niveau.codeBloc(), "Niveau de signature " + v.trim()
                    + " ≥ minimum " + p.signatureMin().trim() + "."));
        } else {
            bloquants.add(new Controle(SE_SIGNATURE_MIN, codes(niveau), niveau.codeBloc(), "Le niveau de signature exigé ne "
                    + "peut pas être inférieur au niveau minimal fixé par l'administrateur (" + p.signatureMin().trim() + ")."));
        }
    }

    /** Règle 5 — {@code SE_ORIGINAL_GARANTIE} (rôles EXIGE, LIEU, LIMITE) : original exigé ⇒ lieu et date limite renseignés. */
    private static void seOriginalGarantie(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche exige = r == null ? null : r.get("EXIGE");
        String v = exige == null ? null : valeurs.get(exige.getCode());
        if (exige == null || vide(v)) {
            return;
        }
        ChampFicheMarche lieu = r.get("LIEU");
        ChampFicheMarche limite = r.get("LIMITE");
        List<String> champs = codes(exige, lieu, limite);
        if (!"OUI".equalsIgnoreCase(v.trim())) {
            ok.add(new Controle(SE_ORIGINAL_GARANTIE, champs, exige.codeBloc(), "Original papier de la garantie non exigé."));
            return;
        }
        boolean complet = lieu != null && limite != null && !vide(valeurs.get(lieu.getCode())) && !vide(valeurs.get(limite.getCode()));
        if (complet) {
            ok.add(new Controle(SE_ORIGINAL_GARANTIE, champs, exige.codeBloc(), "Original papier exigé : lieu et date limite du dépôt renseignés."));
        } else {
            bloquants.add(new Controle(SE_ORIGINAL_GARANTIE, champs, exige.codeBloc(),
                    "L'original papier étant exigé, indiquez le lieu et la date limite de son dépôt."));
        }
    }

    /** Règle 6 — {@code SE_QUORUM} : 2 ≤ quorum ≤ nombre de membres, et le responsable ne détient pas de part. */
    private static void seQuorum(RemiseElectronique.Internes i, List<Controle> bloquants, List<Controle> ok) {
        if (i == null || (i.quorum() == null && i.responsable() == null)) {
            return;
        }
        if (RemiseElectronique.quorumInvalide(i)) {
            bloquants.add(new Controle(SE_QUORUM, List.of(), BLOC_REMISE, RemiseElectronique.MESSAGE_QUORUM));
        } else if (i.quorum() != null) {
            ok.add(new Controle(SE_QUORUM, List.of(), BLOC_REMISE, "Quorum de déchiffrement " + i.quorum() + " sur "
                    + i.membres().size() + " membre(s), responsable hors des détenteurs."));
        }
    }

    /** Règle 7 — {@code SE_OUVERTURE_PLIS} (rôles DELAI, DATE, HEURE) : ouverture = échéance de remise + délai (minutes). */
    private static void seOuverturePlis(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, LocalDateTime echeance,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche delai = r == null ? null : r.get(RemiseElectronique.ROLE_DELAI);
        ChampFicheMarche date = r == null ? null : r.get(RemiseElectronique.ROLE_DATE);
        ChampFicheMarche heure = r == null ? null : r.get(RemiseElectronique.ROLE_HEURE);
        BigDecimal minutes = delai == null ? null : nombre(valeurs.get(delai.getCode()));
        if (delai == null || date == null || heure == null || minutes == null || echeance == null) {
            return;
        }
        LocalDateTime ouverture = RemiseElectronique.echeance(valeurs.get(date.getCode()), valeurs.get(heure.getCode()));
        if (ouverture == null) {
            return;   // OBLIGATOIRE ou la règle 1 s'en chargent
        }
        List<String> champs = codes(delai, date, heure);
        String n = minutes.stripTrailingZeros().toPlainString();
        if (ouverture.equals(echeance.plusMinutes(minutes.longValue()))) {
            ok.add(new Controle(SE_OUVERTURE_PLIS, champs, date.codeBloc(), "Ouverture des plis " + n + " minutes après la date limite."));
        } else {
            bloquants.add(new Controle(SE_OUVERTURE_PLIS, champs, date.codeBloc(),
                    "La date et l'heure d'ouverture des plis sont calculées : date limite plus " + n + " minutes."));
        }
    }

    /** Règle 8 — {@code SE_CEREMONIE} (rôle PUBLICATION) : cérémonie des clés avant la publication de l'avis. */
    private static void seCeremonie(Map<String, ChampFicheMarche> r, RemiseElectronique.Internes i, LocalDateTime publication,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche pub = r == null ? null : r.get(RemiseElectronique.ROLE_PUBLICATION);
        if (pub == null || publication == null || i == null || i.dateCeremonie() == null) {
            return;
        }
        if (RemiseElectronique.ceremonieInvalide(i, publication)) {
            bloquants.add(new Controle(SE_CEREMONIE, codes(pub), pub.codeBloc(), RemiseElectronique.MESSAGE_CEREMONIE));
        } else {
            ok.add(new Controle(SE_CEREMONIE, codes(pub), pub.codeBloc(), "Cérémonie des clés avant la publication de l'avis."));
        }
    }

    /** Règle 9 — {@code SE_PRESTATAIRES} (rôles PRESTATAIRES, NIVEAU) : signature qualifiée ou avancée ⇒ un prestataire au moins. */
    private static void sePrestataires(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche prestataires = r == null ? null : r.get("PRESTATAIRES");
        ChampFicheMarche niveau = r == null ? null : r.get("NIVEAU");
        String v = niveau == null ? null : valeurs.get(niveau.getCode());
        if (prestataires == null || niveau == null || RemiseElectronique.rangNiveau(v) < 0) {
            return;
        }
        List<String> champs = codes(prestataires, niveau);
        if (RemiseElectronique.rangNiveau(v) == 0) {
            ok.add(new Controle(SE_PRESTATAIRES, champs, prestataires.codeBloc(), "Signature simple : aucun prestataire exigé."));
        } else if (!vide(valeurs.get(prestataires.getCode()))) {
            ok.add(new Controle(SE_PRESTATAIRES, champs, prestataires.codeBloc(), "Prestataires de certification acceptés renseignés."));
        } else {
            bloquants.add(new Controle(SE_PRESTATAIRES, champs, prestataires.codeBloc(),
                    "Pour une signature qualifiée ou avancée, indiquez au moins un prestataire de certification accepté."));
        }
    }

    /** Règle 10 — {@code PARAMETRES_INTERNES_INCOMPLETS} : l'écran des paramètres internes n'est pas complet ou invalide. */
    private static void parametresInternes(RemiseElectronique.Internes i, LocalDateTime publication,
            List<Controle> bloquants, List<Controle> ok) {
        if (RemiseElectronique.etat(i, publication) == RemiseElectronique.Etat.COMPLETS) {
            ok.add(new Controle(PARAMETRES_INTERNES_INCOMPLETS, List.of(), BLOC_REMISE, "Paramètres internes de la procédure complets."));
        } else {
            bloquants.add(new Controle(PARAMETRES_INTERNES_INCOMPLETS, List.of(), BLOC_REMISE,
                    "Les paramètres internes de la procédure sont incomplets : à compléter par le responsable de la procédure."));
        }
    }

    /** Règle 11 — {@code RESPONSABLE_NON_DESIGNE} : aucun responsable de la procédure actif. */
    private static void responsable(boolean designe, List<Controle> bloquants, List<Controle> ok) {
        if (designe) {
            ok.add(new Controle(RESPONSABLE_NON_DESIGNE, List.of(), BLOC_REMISE, "Responsable de la procédure désigné."));
        } else {
            bloquants.add(new Controle(RESPONSABLE_NON_DESIGNE, List.of(), BLOC_REMISE, "Aucun responsable de la procédure "
                    + "n'est désigné : la fiche ne peut pas être validée en remise électronique."));
        }
    }

    // ------------------------------------------------------------------ règles V45

    /**
     * {@code BESOIN_INCOMPLET} : chaque lot (le lot unique d'une ligne non allotie) a au moins un article, chaque article
     * au moins une caractéristique ; {@code QUANTITES_ORDRE} : à commande, quantité minimum ≤ maximum. Bloquants.
     */
    private static void besoin(Besoin besoin, int nbLots, List<Controle> bloquants, List<Controle> ok) {
        if (besoin == null) {
            return;
        }
        List<Integer> lots = new ArrayList<>();
        if (LotsFiche.alloti(nbLots)) {
            for (int n = 1; n <= nbLots; n++) {
                lots.add(n);
            }
        } else {
            lots.add(null);
        }
        boolean complet = true;
        for (Integer lot : lots) {
            List<BesoinFiche.Article> duLot = besoin.articles().stream()
                    .filter(a -> java.util.Objects.equals(a.lot(), lot)).toList();
            String nomLot = lot == null ? "Le besoin" : "Le lot " + lot;
            if (duLot.isEmpty()) {
                complet = false;
                bloquants.add(new Controle(BESOIN_INCOMPLET, List.of(), BLOC_BESOIN, nomLot + " n'a aucun article."));
            }
            for (BesoinFiche.Article a : duLot) {
                String nom = "L'article " + a.ordre() + (lot == null ? "" : " du lot " + lot) + " (« " + a.designation() + " »)";
                if (a.caracteristiques() == null || a.caracteristiques().isEmpty()) {
                    complet = false;
                    bloquants.add(new Controle(BESOIN_INCOMPLET, List.of(), BLOC_BESOIN,
                            nom + " n'a aucune caractéristique exigée."));
                }
                if (besoin.aCommande() && a.quantiteMin() != null && a.quantiteMax() != null
                        && a.quantiteMin() > a.quantiteMax()) {
                    bloquants.add(new Controle(QUANTITES_ORDRE, List.of(), BLOC_BESOIN, nom + " : la quantité minimum ("
                            + a.quantiteMin() + ") dépasse la quantité maximum (" + a.quantiteMax() + ")."));
                }
            }
        }
        if (complet) {
            ok.add(new Controle(BESOIN_INCOMPLET, List.of(), BLOC_BESOIN,
                    "Besoin complet : chaque lot a ses articles, chaque article ses caractéristiques."));
        }
    }

    /**
     * {@code GARANTIE_MANQUANTE} (rôle {@code FORME}, le champ qui dit quel modèle de garantie est joint) : une garantie
     * de soumission exigée ({@code garantieSoumission = OUI}) se génère par lot, au montant du lot — il faut donc sa
     * forme (C1, C2). Bloquant. Les montants par lot sont, eux, exigés par leur caractère obligatoire.
     */
    private static void garantieManquante(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, Map<String, ?> cadrage,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche forme = r == null ? null : r.get("FORME");
        if (forme == null || cadrage == null || !"OUI".equalsIgnoreCase(String.valueOf(cadrage.get("garantieSoumission")))) {
            return;
        }
        String v = valeurs.get(forme.getCode());
        if (v == null || v.isBlank()) {
            bloquants.add(new Controle(GARANTIE_MANQUANTE, List.of(forme.getCode()), forme.codeBloc(),
                    "Une garantie de soumission est exigée : choisissez le modèle joint (« " + forme.getLibelle()
                            + " »), qui sera produit pour chaque lot à son montant."));
        } else {
            ok.add(new Controle(GARANTIE_MANQUANTE, List.of(forme.getCode()), forme.codeBloc(),
                    "Modèle de garantie de soumission retenu : " + v + "."));
        }
    }

    /**
     * {@code GARANTIE_TAUX} (rôles {@code GARANTIE} et {@code MAXIMUM}, par lot) : la garantie rapportée au montant
     * maximum du lot, comparée aux bornes administrables ({@link ParametreService.TauxGarantie}). <strong>Avertissement,
     * jamais bloquant</strong> ; sans borne, le taux est seulement constaté.
     */
    private static void garantieTaux(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, int nbLots,
            ParametreService.TauxGarantie taux, List<Controle> avertissements, List<Controle> ok) {
        ChampFicheMarche garantie = r == null ? null : r.get("GARANTIE");
        ChampFicheMarche maximum = r == null ? null : r.get("MAXIMUM");
        if (garantie == null || maximum == null || taux == null) {
            return;
        }
        List<String> clesG = LotsFiche.cles(garantie, nbLots);
        List<String> clesM = LotsFiche.cles(maximum, nbLots);
        for (int i = 0; i < Math.min(clesG.size(), clesM.size()); i++) {
            BigDecimal g = nombre(valeurs.get(clesG.get(i)));
            BigDecimal m = nombre(valeurs.get(clesM.get(i)));
            if (g == null || m == null || m.signum() <= 0) {
                continue;
            }
            BigDecimal t = g.multiply(new BigDecimal("100")).divide(m, 2, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
            String lot = LotsFiche.alloti(nbLots) ? " du lot " + (i + 1) : "";
            String reference = taux.reference() == null ? "" : " (référence " + taux.reference().toPlainString() + " %)";
            List<String> champs = List.of(clesG.get(i), clesM.get(i));
            boolean horsBornes = (taux.borneBasse() != null && t.compareTo(taux.borneBasse()) < 0)
                    || (taux.borneHaute() != null && t.compareTo(taux.borneHaute()) > 0);
            String constat = "Garantie de soumission" + lot + " : " + t.toPlainString() + " % du montant maximum" + reference;
            if (horsBornes) {
                avertissements.add(new Controle(GARANTIE_TAUX, champs, garantie.codeBloc(), constat + ", hors des bornes "
                        + (taux.borneBasse() == null ? "—" : taux.borneBasse().toPlainString() + " %") + " à "
                        + (taux.borneHaute() == null ? "—" : taux.borneHaute().toPlainString() + " %") + "."));
            } else {
                ok.add(new Controle(GARANTIE_TAUX, champs, garantie.codeBloc(), constat + "."));
            }
        }
    }

    // ------------------------------------------------------------------ règles

    private static void datesOrdre(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            Map<String, LocalDate> datesPpm, List<Controle> bloquants, List<Controle> ok) {
        if (r == null) {
            return;
        }
        // ⚠️ Lot 4 (2026-09-23) — la notification, cinquième date du calendrier du contrat-cadre (rôle NOTIFICATION).
        // ⚠️ 2026-09-28 (modèle officiel du contrat-cadre, §B2) — trois étapes propres au contrat-cadre (DPAC art. 2) :
        // demandes d'offres optimisées et leur réception (après la séance d'évaluation, avant l'attribution), courriers de
        // rejet (après l'attribution, avant la notification). Une étape sans champ n'est pas comptée.
        List<String> etapes = List.of("LANCEMENT", "REMISE", "OUVERTURE", "OPTIMISEES_DEMANDE", "OPTIMISEES_RECEPTION",
                "ATTRIBUTION", "REJET", "NOTIFICATION");
        List<String> libelles = List.of("lancement", "remise des offres", "ouverture des plis",
                "demandes d'offres optimisées", "réception des offres optimisées", "attribution", "courriers de rejet",
                "notification");
        List<LocalDate> dates = new ArrayList<>();
        List<String> noms = new ArrayList<>();
        List<String> champs = new ArrayList<>();
        String bloc = null;
        for (int i = 0; i < etapes.size(); i++) {
            ChampFicheMarche c = r.get(etapes.get(i));
            LocalDate d = null;
            if (c != null) {
                d = date(valeurs.get(c.getCode()));   // 2026-09-28 : une date-heure compte pour sa date
                if (d != null) {
                    champs.add(c.getCode());
                    bloc = bloc == null ? c.codeBloc() : bloc;
                }
            } else if (datesPpm != null && (PPM_LANCEMENT.equals(etapes.get(i)) || PPM_ATTRIBUTION.equals(etapes.get(i)))) {
                d = datesPpm.get(etapes.get(i));
            }
            if (d != null) {
                dates.add(d);
                noms.add(libelles.get(i));
            }
        }
        if (dates.size() < 2) {
            return;
        }
        for (int i = 1; i < dates.size(); i++) {
            if (dates.get(i).isBefore(dates.get(i - 1))) {
                bloquants.add(new Controle(DATES_ORDRE, champs, bloc, "Dates dans l'ordre : " + noms.get(i) + " ("
                        + dates.get(i) + ") précède " + noms.get(i - 1) + " (" + dates.get(i - 1)
                        + ") — " + String.join(" < ", noms) + "."));   // 2026-09-28 : l'ordre des étapes présentes
                return;
            }
        }
        ok.add(new Controle(DATES_ORDRE, champs, bloc, "Dates dans l'ordre (" + String.join(" < ", noms) + ")."));
    }

    private static void validiteGarantie(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche garantie = r == null ? null : r.get("GARANTIE");
        ChampFicheMarche offre = r == null ? null : r.get("OFFRE");
        BigDecimal g = garantie == null ? null : nombre(valeurs.get(garantie.getCode()));
        BigDecimal o = offre == null ? null : nombre(valeurs.get(offre.getCode()));
        if (g == null || o == null) {
            return;
        }
        List<String> champs = List.of(garantie.getCode(), offre.getCode());
        if (g.compareTo(o) <= 0) {
            bloquants.add(new Controle(VALIDITE_GARANTIE_SUP_OFFRE, champs, garantie.codeBloc(),
                    "La validité de la garantie de soumission (" + g.stripTrailingZeros().toPlainString()
                            + " jours) doit dépasser la validité des offres (" + o.stripTrailingZeros().toPlainString() + " jours)."));
        } else {
            ok.add(new Controle(VALIDITE_GARANTIE_SUP_OFFRE, champs, garantie.codeBloc(),
                    "Validité de la garantie supérieure à celle des offres."));
        }
    }

    private static void avance(Map<String, ChampFicheMarche> max20, Map<String, ChampFicheMarche> sup5,
            Map<String, String> valeurs, Map<String, ?> cadrage, List<Controle> bloquants, List<Controle> ok) {
        if (cadrage == null || !"OUI".equalsIgnoreCase(String.valueOf(cadrage.get("avance")))) {
            return;
        }
        ChampFicheMarche champTaux = max20 != null && max20.get("TAUX") != null ? max20.get("TAUX")
                : sup5 != null ? sup5.get("TAUX") : null;
        BigDecimal taux = champTaux != null ? nombre(valeurs.get(champTaux.getCode())) : nombre(cadrage.get("tauxAvance"));
        if (taux == null) {
            return;
        }
        List<String> champs = champTaux == null ? List.of() : List.of(champTaux.getCode());
        String bloc = champTaux == null ? "B08" : champTaux.codeBloc();
        if (taux.compareTo(new BigDecimal("20")) > 0) {
            bloquants.add(new Controle(AVANCE_MAX_20, champs, bloc, "Avance de " + taux.stripTrailingZeros().toPlainString()
                    + " % : le plafond réglementaire est de 20 % du montant TTC."));
        } else {
            ok.add(new Controle(AVANCE_MAX_20, champs, bloc, "Avance ≤ 20 %."));
        }
        ChampFicheMarche garantie = sup5 == null ? null : sup5.get("GARANTIE");
        if (garantie != null && taux.compareTo(new BigDecimal("5")) > 0) {
            String v = valeurs.get(garantie.getCode());
            if (v == null || v.isBlank()) {
                bloquants.add(new Controle(AVANCE_SUP_5_GARANTIE, List.of(garantie.getCode()), garantie.codeBloc(),
                        "Avance de " + taux.stripTrailingZeros().toPlainString() + " % : au-delà de 5 %, la garantie de "
                                + "restitution d'avance (« " + garantie.getLibelle() + " ») doit être renseignée."));
            } else {
                ok.add(new Controle(AVANCE_SUP_5_GARANTIE, List.of(garantie.getCode()), garantie.codeBloc(),
                        "Garantie de restitution d'avance renseignée."));
            }
        }
    }

    private static void forfait(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, Map<String, ?> cadrage,
            List<Controle> bloquants, List<Controle> ok) {
        if (r == null || cadrage == null || !"FORFAITAIRE".equalsIgnoreCase(String.valueOf(cadrage.get("typePrix")))) {
            return;
        }
        ChampFicheMarche reception = r.get("RECEPTION");
        ChampFicheMarche pv = r.get("PV");
        BigDecimal rec = reception == null ? null : nombre(valeurs.get(reception.getCode()));
        BigDecimal surPv = pv == null ? null : nombre(valeurs.get(pv.getCode()));
        if (rec == null && surPv == null) {
            return;
        }
        List<String> champs = new ArrayList<>();
        if (reception != null) {
            champs.add(reception.getCode());
        }
        if (pv != null) {
            champs.add(pv.getCode());
        }
        String bloc = reception != null ? reception.codeBloc() : pv.codeBloc();
        boolean recOk = rec == null || rec.compareTo(new BigDecimal("60")) >= 0;
        boolean pvOk = surPv == null || surPv.compareTo(new BigDecimal("40")) <= 0;
        if (recOk && pvOk) {
            ok.add(new Controle(FORFAIT_60_40, champs, bloc, "Prix global forfaitaire : au moins 60 % à réception, au plus 40 % sur PV."));
        } else {
            bloquants.add(new Controle(FORFAIT_60_40, champs, bloc, "Prix global forfaitaire : au moins 60 % à la "
                    + "réception et au plus 40 % sur procès-verbal (saisi : " + (rec == null ? "—" : rec.stripTrailingZeros().toPlainString() + " %")
                    + " à réception, " + (surPv == null ? "—" : surPv.stripTrailingZeros().toPlainString() + " %") + " sur PV)."));
        }
    }

    /**
     * ⚠️ Lot D3 (2026-09-29, §B2.2.5) — le plafond des pénalités du CCAG de la catégorie : 10 % pour les prestations
     * intellectuelles, 15 % pour les fournitures et services et les travaux. Le code de la règle reste
     * {@code PENALITES_PLAFOND_15} (code stable), le message dit le plafond appliqué.
     */
    static BigDecimal plafondPenalites(String categorie) {
        return "PRESTATIONS_INTELLECTUELLES".equals(categorie) ? new BigDecimal("10") : new BigDecimal("15");
    }

    private static void penalites(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, BigDecimal plafond,
            List<Controle> avertissements, List<Controle> ok) {
        ChampFicheMarche taux = r == null ? null : r.get("TAUX");
        BigDecimal t = taux == null ? null : nombre(valeurs.get(taux.getCode()));
        if (t == null) {
            return;
        }
        String p = plafond.toPlainString();
        ChampFicheMarche derogation = r.get("DEROGATION");
        String d = derogation == null ? null : valeurs.get(derogation.getCode());
        if (t.compareTo(plafond) > 0 && (d == null || d.isBlank())) {
            avertissements.add(new Controle(PENALITES_PLAFOND_15, List.of(taux.getCode()), taux.codeBloc(),
                    "Pénalités de " + t.stripTrailingZeros().toPlainString() + " % : au-delà du plafond de " + p + " % du CCAG, "
                            + "la dérogation doit être précisée."));
        } else {
            ok.add(new Controle(PENALITES_PLAFOND_15, List.of(taux.getCode()), taux.codeBloc(),
                    t.compareTo(plafond) > 0 ? "Pénalités au-delà de " + p + " %, dérogation précisée."
                            : "Pénalités dans le plafond de " + p + " % du CCAG."));
        }
    }

    private static void interetsMoratoires(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            List<Controle> avertissements, List<Controle> ok) {
        ChampFicheMarche taux = r == null ? null : r.get("TAUX");
        ChampFicheMarche banque = r == null ? null : r.get("BANQUE");
        BigDecimal t = taux == null ? null : nombre(valeurs.get(taux.getCode()));
        // ⚠️ Lot D3 (2026-09-29, §B2.2.4) — un TAUX de type NOMBRE est une MAJORATION en points du taux directeur (le CPS
        // des prestations intellectuelles : « taux directeur … augmenté de n point(s) ») : il suffit qu'elle soit d'au moins
        // un point, sans taux de la Banque centrale à comparer.
        if (t != null && TypeChampFiche.NOMBRE.name().equals(taux.getType())) {
            List<String> champs = List.of(taux.getCode());
            if (t.compareTo(BigDecimal.ONE) < 0) {
                avertissements.add(new Controle(INTERETS_MORATOIRES_TAUX, champs, taux.codeBloc(),
                        "Intérêts moratoires : taux directeur majoré de " + t.stripTrailingZeros().toPlainString()
                                + " point(s) ; la majoration attendue est d'au moins un point."));
            } else {
                ok.add(new Controle(INTERETS_MORATOIRES_TAUX, champs, taux.codeBloc(),
                        "Intérêts moratoires : taux directeur majoré d'au moins un point."));
            }
            return;
        }
        BigDecimal b = banque == null ? null : nombre(valeurs.get(banque.getCode()));
        if (t == null || b == null) {
            return;
        }
        List<String> champs = List.of(taux.getCode(), banque.getCode());
        if (t.compareTo(b.add(BigDecimal.ONE)) < 0) {
            avertissements.add(new Controle(INTERETS_MORATOIRES_TAUX, champs, taux.codeBloc(),
                    "Intérêts moratoires de " + t.stripTrailingZeros().toPlainString() + " % : le taux attendu est au moins "
                            + "celui de la Banque centrale (" + b.stripTrailingZeros().toPlainString() + " %) majoré d'un point."));
        } else {
            ok.add(new Controle(INTERETS_MORATOIRES_TAUX, champs, taux.codeBloc(), "Intérêts moratoires ≥ taux Banque centrale + 1 point."));
        }
    }

    private static void delaiPaiement(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            List<Controle> avertissements, List<Controle> ok) {
        ChampFicheMarche delai = r == null ? null : r.get("DELAI");
        BigDecimal d = delai == null ? null : nombre(valeurs.get(delai.getCode()));
        if (d == null) {
            return;
        }
        if (d.compareTo(new BigDecimal("75")) > 0) {
            avertissements.add(new Controle(DELAI_PAIEMENT_75, List.of(delai.getCode()), delai.codeBloc(),
                    "Délai de paiement de " + d.stripTrailingZeros().toPlainString() + " jours : le délai réglementaire est de 75 jours au plus."));
        } else {
            ok.add(new Controle(DELAI_PAIEMENT_75, List.of(delai.getCode()), delai.codeBloc(), "Délai de paiement ≤ 75 jours."));
        }
    }

    // ------------------------------------------------------------------ lectures

    /** Un nombre depuis une valeur normalisée ; {@code null} si absent ou illisible. */
    public static BigDecimal nombre(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim().replace(" ", "").replace(',', '.');
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Une date ISO ({@code yyyy-MM-dd}) ; {@code null} si absente ou illisible. ⚠️ 2026-09-28 — une date-heure
     * ({@code yyyy-MM-ddTHH:mm}, type {@code DATE_HEURE}) vaut sa date : {@code DATES_ORDRE} compare la date limite du
     * contrat-cadre, désormais date et heure, aux autres dates du calendrier sur la date seule.
     */
    public static LocalDate date(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(v.trim());
        } catch (java.time.format.DateTimeParseException e) {
            LocalDateTime dh = RemiseElectronique.dateHeure(v);
            return dh == null ? null : dh.toLocalDate();
        }
    }
}
