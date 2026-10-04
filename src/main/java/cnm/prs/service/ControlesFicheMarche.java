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
 * <tr><td>ASSURANCE_DECENNALE (⚠️ 2026-10-02)</td><td>BATIMENT (oui/non), ASSURANCE (texte)</td><td>bloquant</td></tr>
 * <tr><td>LIQUIDITE_DOUBLE (⚠️ V59)</td><td>MONTANT, POURCENTAGE (par lot)</td><td>bloquant</td></tr>
 * <tr><td>CA_MOYENNE (⚠️ V59)</td><td>CA (montant), MEILLEURES, ANNEES (nombres)</td><td>bloquant</td></tr>
 * <tr><td>REFERENCES_CUMUL (⚠️ V59)</td><td>NOMBRE, MONTANT (par lot)</td><td>bloquant</td></tr>
 * <tr><td>PIECES_OFFRE_EXIGEES (⚠️ V61)</td><td>TEXTE (B04-PI-01) — ou la liste des pièces OFFRE ; travaux seulement</td><td>bloquant</td></tr>
 * <tr><td>PIECES_EN_DOUBLE (⚠️ V61)</td><td>TEXTE (B03-CQ-01) à sa valeur par défaut avec une liste ADMINISTRATIVE remplie</td><td>avertissement</td></tr>
 * <tr><td>MATERIEL_EXIGE (⚠️ V60)</td><td>TEXTE (B03-QT-09) — ou la liste du matériel ; travaux seulement</td><td>bloquant</td></tr>
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
    /**
     * ⚠️ 2026-10-02 (demande front « référentiel des travaux routiers », §B2) — l'assurance décennale, exigée pour des
     * travaux de bâtiment seulement : le CCAP-T ne l'imprime que sous {@code BATIMENT}.
     */
    public static final String ASSURANCE_DECENNALE = "ASSURANCE_DECENNALE";
    /** ⚠️ V59 (2026-10-02, §B2) — les seuils de qualification calculés du DPAO-T (clause 6.3). */
    public static final String LIQUIDITE_DOUBLE = "LIQUIDITE_DOUBLE";
    public static final String CA_MOYENNE = "CA_MOYENNE";
    public static final String REFERENCES_CUMUL = "REFERENCES_CUMUL";
    /** ⚠️ V60 (2026-10-03, matériel et personnel des travaux, §B3) — le matériel exigé, en liste ou en texte. */
    public static final String MATERIEL_EXIGE = "MATERIEL_EXIGE";
    /** ⚠️ V61 (2026-10-03, pièces de l'offre des travaux, §B3) — les pièces de l'offre, en liste ou en texte ; le doublon. */
    public static final String PIECES_OFFRE_EXIGEES = "PIECES_OFFRE_EXIGEES";
    public static final String PIECES_EN_DOUBLE = "PIECES_EN_DOUBLE";
    /** ⚠️ 2026-10-04 (soumission en ligne, lot 1c, §B8, Q5) — la plateforme ne sait faire que la signature simple. */
    public static final String SIGNATURE_EN_LIGNE = "SIGNATURE_EN_LIGNE";
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
    /** ⚠️ V66 (2026-10-04, soumission en ligne, lot 2, §B1) — règle 12 : le dépositaire de la part de secours est désigné. */
    public static final String SE_DEPOSITAIRE = "SE_DEPOSITAIRE";
    /** ⚠️ V66 (lot 2, §B3, S1) — avertissement : le quorum égale le nombre de membres. */
    public static final String SE_QUORUM_MARGE = "SE_QUORUM_MARGE";
    /** ⚠️ V66 (lot 2, §B4) — avertissement de la cérémonie : parts disponibles ≤ quorum. */
    public static final String SE_MARGE_EPUISEE = "SE_MARGE_EPUISEE";
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
    public record Besoin(List<BesoinFiche.Article> articles, boolean aCommande, boolean travaux) {

        /** Fournitures et services. */
        public Besoin(List<BesoinFiche.Article> articles, boolean aCommande) {
            this(articles, aCommande, false);
        }
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
        assuranceDecennale(roles.get(ASSURANCE_DECENNALE), valeurs, bloquants, ok);
        liquiditeDouble(roles.get(LIQUIDITE_DOUBLE), valeurs, nbLots, bloquants, ok);
        caMoyenne(roles.get(CA_MOYENNE), valeurs, nbLots, bloquants, ok);
        referencesCumul(roles.get(REFERENCES_CUMUL), valeurs, nbLots, bloquants, ok);
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
            // ⚠️ V66 (2026-10-04, soumission en ligne, lot 2) — règle 12 (dépositaire, bloquante) et S1 (marge, avertissement).
            depositaire(se.internes(), bloquants, ok);
            quorumMarge(se.internes(), avertissements);
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
        // ⚠️ V66 — le dépositaire manquant a sa propre règle (12) : il ne compte pas deux fois.
        boolean complets = RemiseElectronique.anomalies(i, publication).stream().allMatch(a -> SE_DEPOSITAIRE.equals(a.regle()));
        if (complets) {
            ok.add(new Controle(PARAMETRES_INTERNES_INCOMPLETS, List.of(), BLOC_REMISE, "Paramètres internes de la procédure complets."));
        } else {
            bloquants.add(new Controle(PARAMETRES_INTERNES_INCOMPLETS, List.of(), BLOC_REMISE,
                    "Les paramètres internes de la procédure sont incomplets : à compléter par le responsable de la procédure."));
        }
    }

    /** ⚠️ V66 — règle 12, {@code SE_DEPOSITAIRE} : le dépositaire de la part de secours est désigné (ADR-0013 S3). Bloquante. */
    private static void depositaire(RemiseElectronique.Internes i, List<Controle> bloquants, List<Controle> ok) {
        if (i != null && i.depositaire() != null) {
            ok.add(new Controle(SE_DEPOSITAIRE, List.of(), BLOC_REMISE, "Dépositaire de la part de secours désigné : "
                    + i.depositaire().nom() + (i.depositaire().organisme() == null ? "" : " (" + i.depositaire().organisme() + ")") + "."));
        } else {
            bloquants.add(new Controle(SE_DEPOSITAIRE, List.of(), BLOC_REMISE, RemiseElectronique.MESSAGE_DEPOSITAIRE));
        }
    }

    /** ⚠️ V66 — S1, {@code SE_QUORUM_MARGE} : le quorum égale le nombre de membres. Avertissement, jamais bloquant. */
    private static void quorumMarge(RemiseElectronique.Internes i, List<Controle> avertissements) {
        if (i != null && i.quorumSansMarge() && !RemiseElectronique.quorumInvalide(i)) {
            avertissements.add(new Controle(SE_QUORUM_MARGE, List.of(), BLOC_REMISE, RemiseElectronique.MESSAGE_QUORUM_MARGE));
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

    /**
     * ⚠️ V60 (2026-10-03, §B3) — {@code MATERIEL_EXIGE} (rôle {@code TEXTE}) : une fiche de travaux dit son matériel, par la
     * liste du matériel ({@code nbMateriel} lignes) <strong>ou</strong> par le texte qui porte le rôle. Bloquant. Appelée
     * par le service pour une fiche de travaux, après le bilan, qu'elle complète. Elle ne vaut que là où le champ au rôle
     * est servi, c'est-à-dire là où la clause 6.3 du DPAO-T l'imprime (quantité fixe, à commande) : le contrat-cadre de
     * travaux n'a pas ce champ, et la règle ne lui dit rien.
     */
    public static void materielExige(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs, int nbMateriel,
            BilanControlesDto bilan) {
        ChampFicheMarche texte = champsOuverts.stream()
                .filter(c -> controles(c).stream().anyMatch(x -> MATERIEL_EXIGE.equals(x[0]) && "TEXTE".equals(x[1])))
                .findFirst().orElse(null);
        if (texte == null) {
            return;
        }
        String code = texte.getCode();
        String bloc = texte.codeBloc();
        List<String> champs = List.of(code);
        if (nbMateriel > 0) {
            bilan.ok().add(new Controle(MATERIEL_EXIGE, champs, "B13", "Matériel exigé : " + nbMateriel + " ligne(s)."));
        } else if (renseigne(valeurs, code)) {
            bilan.ok().add(new Controle(MATERIEL_EXIGE, champs, bloc, "Matériel exigé : décrit par « " + texte.getLibelle() + " »."));
        } else {
            bilan.bloquants().add(new Controle(MATERIEL_EXIGE, champs, "B13", "Le matériel exigé n'est pas dit : remplissez la "
                    + "liste du matériel, ou « " + texte.getLibelle() + " »."));
        }
    }

    /** Le champ ouvert qui porte {@code regle:TEXTE}, ou {@code null}. */
    private static ChampFicheMarche texteAuRole(List<ChampFicheMarche> champsOuverts, String regle) {
        return champsOuverts.stream()
                .filter(c -> controles(c).stream().anyMatch(x -> regle.equals(x[0]) && "TEXTE".equals(x[1])))
                .findFirst().orElse(null);
    }

    /**
     * ⚠️ V61 (2026-10-03, §B3) — {@code PIECES_OFFRE_EXIGEES} (rôle {@code TEXTE}) : une fiche de travaux dit les pièces de
     * l'offre, par la liste des pièces {@code OFFRE} ({@code nbOffre}) <strong>ou</strong> par le texte au rôle. Bloquant ;
     * muette là où le champ n'est pas servi (contrat-cadre), comme {@link #materielExige}.
     */
    public static void piecesOffreExigees(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs, long nbOffre,
            BilanControlesDto bilan) {
        ChampFicheMarche texte = texteAuRole(champsOuverts, PIECES_OFFRE_EXIGEES);
        if (texte == null) {
            return;
        }
        List<String> champs = List.of(texte.getCode());
        if (nbOffre > 0) {
            bilan.ok().add(new Controle(PIECES_OFFRE_EXIGEES, champs, "B14", "Pièces de l'offre : " + nbOffre + " pièce(s)."));
        } else if (renseigne(valeurs, texte.getCode())) {
            bilan.ok().add(new Controle(PIECES_OFFRE_EXIGEES, champs, texte.codeBloc(), "Pièces de l'offre : décrites par « "
                    + texte.getLibelle() + " »."));
        } else {
            bilan.bloquants().add(new Controle(PIECES_OFFRE_EXIGEES, champs, "B14", "Les pièces de l'offre ne sont pas dites : "
                    + "remplissez la liste des pièces de l'offre, ou « " + texte.getLibelle() + " »."));
        }
    }

    /**
     * ⚠️ V61 (2026-10-03, §B3, H3) — {@code PIECES_EN_DOUBLE} (rôle {@code TEXTE}) : la liste des pièces administratives est
     * remplie ({@code nbAdministratives}) et le texte au rôle vaut encore sa valeur par défaut — le DPAO imprimerait les
     * pièces deux fois. Avertissement, jamais bloquant ; égalité jugée blancs de bord et fins de ligne confondus.
     */
    public static void piecesEnDouble(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs, long nbAdministratives,
            BilanControlesDto bilan) {
        ChampFicheMarche texte = texteAuRole(champsOuverts, PIECES_EN_DOUBLE);
        if (texte == null || nbAdministratives == 0 || texte.getValeurDefaut() == null) {
            return;
        }
        String v = valeurs.get(texte.getCode());
        if (v != null && normaliserTexte(v).equals(normaliserTexte(texte.getValeurDefaut().replace("\\n", "\n")))) {
            bilan.avertissements().add(new Controle(PIECES_EN_DOUBLE, List.of(texte.getCode()), texte.codeBloc(),
                    "Les pièces administratives sont en liste, et « " + texte.getLibelle() + " » garde sa valeur par défaut : "
                            + "le DPAO les imprimera deux fois. Videz ce texte, ou gardez-y ce que la liste ne dit pas."));
        }
    }


    /**
     * ⚠️ 2026-10-04 (soumission en ligne, lot 1c, §B8, Q5) — {@code SIGNATURE_EN_LIGNE} : le mode est électronique et
     * {@code B04-SE-05} exige un niveau au-dessus de « Simple ». La plateforme ne sait encore faire que la signature
     * simple : la procédure n'apparaîtra pas parmi les procédures en ligne. Avertissement, jamais bloquant.
     */
    public static void signatureEnLigne(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs,
            Map<String, Object> cadrage, BilanControlesDto bilan) {
        if (!RemiseElectronique.electronique(cadrage)) {
            return;
        }
        ChampFicheMarche niveau = champsOuverts.stream().filter(c -> SIGNATURE_CHAMP.equals(c.getCode())).findFirst().orElse(null);
        if (niveau != null && RemiseElectronique.rangNiveau(valeurs.get(niveau.getCode())) > 0) {
            bilan.avertissements().add(new Controle(SIGNATURE_EN_LIGNE, List.of(niveau.getCode()), niveau.codeBloc(),
                    "La plateforme n'accepte pour l'instant que la signature simple : la procédure ne pourra pas s'ouvrir en ligne."));
        }
    }

    /** Le niveau de signature électronique exigé. */
    static final String SIGNATURE_CHAMP = "B04-SE-05";
    private static String normaliserTexte(String s) {
        return s.replace("\r\n", "\n").strip();
    }

    // ------------------------------------------------------------------ règles V59 (seuils de qualification calculés)

    /** La clé d'un champ pour le lot {@code n} (1…), ou sa clé nue s'il ne se saisit pas par lot. */
    private static String cleDuLot(ChampFicheMarche c, int n, int nbLots) {
        return LotsFiche.parLot(c, nbLots) ? LotsFiche.cle(c.getCode(), n) : c.getCode();
    }

    private static boolean renseigne(Map<String, String> valeurs, String cle) {
        String v = valeurs.get(cle);
        return v != null && !v.isBlank();
    }

    /**
     * ⚠️ V59 (2026-10-02, §B2.1) — {@code LIQUIDITE_DOUBLE} (rôles {@code MONTANT}, {@code POURCENTAGE}) : la liquidité
     * minimale s'exige en montant <strong>ou</strong> en pourcentage du montant de l'offre, pas les deux pour un même lot.
     * Bloquant.
     */
    private static void liquiditeDouble(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, int nbLots,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche montant = r == null ? null : r.get("MONTANT");
        ChampFicheMarche pourcentage = r == null ? null : r.get("POURCENTAGE");
        if (montant == null || pourcentage == null) {
            return;
        }
        boolean evalue = false;
        boolean double_ = false;
        for (int n = 1; n <= Math.max(1, nbLots); n++) {
            String m = cleDuLot(montant, n, nbLots);
            String p = cleDuLot(pourcentage, n, nbLots);
            boolean aM = renseigne(valeurs, m);
            boolean aP = renseigne(valeurs, p);
            evalue |= aM || aP;
            if (aM && aP) {
                double_ = true;
                bloquants.add(new Controle(LIQUIDITE_DOUBLE, List.of(m, p), pourcentage.codeBloc(),
                        "La liquidité minimale s'exige en montant ou en pourcentage de l'offre, pas les deux"
                                + (LotsFiche.alloti(nbLots) ? " (lot " + n + ")" : "") + " : videz « " + montant.getLibelle()
                                + " » ou « " + pourcentage.getLibelle() + " »."));
            }
            if (!LotsFiche.parLot(montant, nbLots) && !LotsFiche.parLot(pourcentage, nbLots)) {
                break;
            }
        }
        if (evalue && !double_) {
            ok.add(new Controle(LIQUIDITE_DOUBLE, List.of(montant.getCode(), pourcentage.getCode()), pourcentage.codeBloc(),
                    "Liquidité minimale exigée sous une seule forme."));
        }
    }

    /**
     * ⚠️ V59 (2026-10-02, §B2.2) — {@code CA_MOYENNE} (rôles {@code CA}, {@code MEILLEURES}, {@code ANNEES}) : le chiffre
     * d'affaires en moyenne des n meilleures des m dernières années — n et m vont ensemble, n ≤ m, et seulement si le
     * chiffre d'affaires minimum est renseigné. Bloquant.
     */
    private static void caMoyenne(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, int nbLots,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche ca = r == null ? null : r.get("CA");
        ChampFicheMarche meilleures = r == null ? null : r.get("MEILLEURES");
        ChampFicheMarche annees = r == null ? null : r.get("ANNEES");
        if (ca == null || meilleures == null || annees == null) {
            return;
        }
        BigDecimal n = nombre(valeurs.get(meilleures.getCode()));
        BigDecimal m = nombre(valeurs.get(annees.getCode()));
        if (n == null && m == null) {
            return;
        }
        List<String> champs = List.of(meilleures.getCode(), annees.getCode());
        int avant = bloquants.size();
        if (n == null || m == null) {
            bloquants.add(new Controle(CA_MOYENNE, champs, meilleures.codeBloc(), "« " + meilleures.getLibelle() + " » et « "
                    + annees.getLibelle() + " » vont ensemble : renseignez les deux, ou aucun."));
        } else if (n.compareTo(m) > 0) {
            bloquants.add(new Controle(CA_MOYENNE, champs, meilleures.codeBloc(), "Le chiffre d'affaires se calcule sur les "
                    + n.toPlainString() + " meilleures des " + m.toPlainString() + " dernières années : il ne peut y avoir plus "
                    + "de meilleures années que d'années."));
        }
        boolean caRenseigne = LotsFiche.cles(ca, nbLots).stream().anyMatch(k -> renseigne(valeurs, k));
        if (!caRenseigne) {
            bloquants.add(new Controle(CA_MOYENNE, List.of(ca.getCode()), ca.codeBloc(), "Un chiffre d'affaires en moyenne des "
                    + "meilleures années exige son montant : renseignez « " + ca.getLibelle() + " »."));
        }
        if (bloquants.size() == avant) {
            ok.add(new Controle(CA_MOYENNE, champs, meilleures.codeBloc(), "Chiffre d'affaires moyen : "
                    + n.toPlainString() + " meilleures des " + m.toPlainString() + " dernières années."));
        }
    }

    /**
     * ⚠️ V59 (2026-10-02, §B2.3) — {@code REFERENCES_CUMUL} (rôles {@code NOMBRE}, {@code MONTANT}) : le nombre maximal de
     * marchés cumulables et le montant cumulé minimum (par lot) vont ensemble. Bloquant.
     */
    private static void referencesCumul(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, int nbLots,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche nombre = r == null ? null : r.get("NOMBRE");
        ChampFicheMarche montant = r == null ? null : r.get("MONTANT");
        if (nombre == null || montant == null) {
            return;
        }
        boolean aNombre = renseigne(valeurs, nombre.getCode());
        List<String> cles = LotsFiche.cles(montant, nbLots);
        List<String> manquants = cles.stream().filter(k -> !renseigne(valeurs, k)).toList();
        if (!aNombre && manquants.size() == cles.size()) {
            return;
        }
        if (!aNombre) {
            bloquants.add(new Controle(REFERENCES_CUMUL, List.of(nombre.getCode()), nombre.codeBloc(), "Un montant cumulé des "
                    + "marchés de référence exige le nombre de marchés cumulables : renseignez « " + nombre.getLibelle() + " »."));
        } else if (!manquants.isEmpty()) {
            bloquants.add(new Controle(REFERENCES_CUMUL, manquants, montant.codeBloc(), "Un cumul de marchés de référence exige "
                    + "son montant : renseignez « " + montant.getLibelle() + " »" + (LotsFiche.parLot(montant, nbLots)
                            ? " pour " + (manquants.size() == 1 ? "le lot " : "les lots ")
                                    + String.join(", ", manquants.stream().map(k -> String.valueOf(LotsFiche.lotDe(k))).toList())
                            : "") + "."));
        } else {
            ok.add(new Controle(REFERENCES_CUMUL, List.of(nombre.getCode(), montant.getCode()), montant.codeBloc(),
                    "Références : nombre de marchés cumulables et montant cumulé renseignés."));
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
                String nom = "L'article " + (besoin.travaux() && a.numeroPrix() != null ? "n° " + a.numeroPrix() : a.ordre())
                        + (lot == null ? "" : " du lot " + lot) + " (« " + a.designation() + " »)";
                if (besoin.travaux()) {
                    // ⚠️ V59 (2026-10-02, §B1.4) — un article du DQE : numéro de prix, série, unité, quantité positive ; la
                    // caractéristique n'est pas exigée (les spécifications techniques des travaux sont une pièce rédigée).
                    List<String> manque = new ArrayList<>();
                    if (a.numeroPrix() == null || a.numeroPrix().isBlank()) {
                        manque.add("de numéro de prix");
                    }
                    if (a.serie() == null || a.serie().isBlank()) {
                        manque.add("de série");
                    }
                    if (a.unite() == null || a.unite().isBlank()) {
                        manque.add("d'unité");
                    }
                    java.math.BigDecimal q = besoin.aCommande() ? a.quantiteMax() : a.quantite();
                    if (q == null || q.signum() <= 0) {
                        manque.add(besoin.aCommande() ? "de quantité maximum positive" : "de quantité positive");
                    }
                    if (!manque.isEmpty()) {
                        complet = false;
                        bloquants.add(new Controle(BESOIN_INCOMPLET, List.of(), BLOC_BESOIN,
                                nom + " n'a pas " + String.join(", ", manque) + "."));
                    }
                } else if (a.caracteristiques() == null || a.caracteristiques().isEmpty()) {
                    complet = false;
                    bloquants.add(new Controle(BESOIN_INCOMPLET, List.of(), BLOC_BESOIN,
                            nom + " n'a aucune caractéristique exigée."));
                }
                if (besoin.aCommande() && a.quantiteMin() != null && a.quantiteMax() != null
                        && a.quantiteMin().compareTo(a.quantiteMax()) > 0) {
                    bloquants.add(new Controle(QUANTITES_ORDRE, List.of(), BLOC_BESOIN, nom + " : la quantité minimum ("
                            + ValeursPpmService.montant(a.quantiteMin()) + ") dépasse la quantité maximum ("
                            + ValeursPpmService.montant(a.quantiteMax()) + ")."));
                }
            }
        }
        if (complet) {
            ok.add(new Controle(BESOIN_INCOMPLET, List.of(), BLOC_BESOIN, besoin.travaux()
                    ? "Détail quantitatif et estimatif complet : chaque lot a ses articles, chaque article son numéro de prix, "
                            + "sa série, son unité et sa quantité."
                    : "Besoin complet : chaque lot a ses articles, chaque article ses caractéristiques."));
        }
    }

    /**
     * {@code GARANTIE_MANQUANTE} (rôle {@code FORME}, le champ qui dit quel modèle de garantie est joint) : une garantie
     * de soumission exigée ({@code garantieSoumission = OUI}) se génère par lot, au montant du lot — il faut donc sa
     * forme (C1, C2). Bloquant. Les montants par lot sont, eux, exigés par leur caractère obligatoire.
     */
    /**
     * ⚠️ 2026-10-02 — {@code ASSURANCE_DECENNALE} (rôles {@code BATIMENT} et {@code ASSURANCE}) : des travaux de bâtiment
     * ({@code BATIMENT = OUI}) exigent l'assurance de responsabilité civile décennale, que le CCAP-T imprime alors ; hors
     * bâtiment, rien n'est exigé ni constaté (une route n'a pas de garantie décennale à imprimer).
     */
    private static void assuranceDecennale(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche batiment = r == null ? null : r.get("BATIMENT");
        ChampFicheMarche assurance = r == null ? null : r.get("ASSURANCE");
        if (batiment == null || assurance == null || !"OUI".equalsIgnoreCase(valeurs.get(batiment.getCode()))) {
            return;
        }
        String v = valeurs.get(assurance.getCode());
        if (v == null || v.isBlank()) {
            bloquants.add(new Controle(ASSURANCE_DECENNALE, List.of(batiment.getCode(), assurance.getCode()), assurance.codeBloc(),
                    "Des travaux de bâtiment exigent l'assurance de responsabilité civile décennale : renseignez « "
                            + assurance.getLibelle() + " »."));
        } else {
            ok.add(new Controle(ASSURANCE_DECENNALE, List.of(batiment.getCode(), assurance.getCode()), assurance.codeBloc(),
                    "Travaux de bâtiment : assurance décennale renseignée."));
        }
    }

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
