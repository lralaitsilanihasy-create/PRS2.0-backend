package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cnm.prs.dto.FicheMarcheDto;

/**
 * ⚠️ <strong>Les formulaires administratifs du candidat — gabarit provisoire</strong> (V46, demande front du 2026-09-25,
 * §B8) : fiches de renseignements A1 à A4 et garanties de soumission C1 (bancaire) et C2 (caution personnelle et
 * solidaire), toutes catégories.
 *
 * <p><strong>Ce ne sont pas les modèles officiels.</strong> Le pilote les fournira ; ils seront remplis tels quels, seuls
 * les blancs étant des champs. En attendant, chaque pièce est un gabarit filigrané « MODÈLE PROVISOIRE – NON OFFICIEL »
 * qui <em>liste</em> les blancs — ceux que la fiche remplit, avec leur valeur, et ceux que le candidat complète — sans
 * écrire aucune phrase réglementaire. Il sert à éprouver le pré-remplissage.</p>
 *
 * <ul>
 *   <li>A1 à A4 : une pièce par fiche exigée ({@code B04-CD-01}) et par lot si la ligne est allotie. A1 porte la
 *       rubrique A1-b (groupement), « non applicable » si le cadrage n'admet pas le groupement.</li>
 *   <li>C1 / C2 : une pièce par forme retenue ({@code B04-CD-02}) et par lot, au montant du lot ({@code B05-GS-03#n}) en
 *       chiffres et en lettres ; validité de la garantie ({@code B05-GS-04}) en nombre et en ordinal (« cent cinquième
 *       (105ème) jour »). C2 : remise des offres ({@code B04-LR-03}) et fin de validité des offres, calculée
 *       ({@code B04-LR-03} + {@code B04-VO-01} jours).</li>
 * </ul>
 *
 * <p>Classe pure : la fiche figée et les désignations des lots du plan en entrée, des modèles de document en sortie.</p>
 */
public final class FormulairesCandidat {

    public static final String FILIGRANE = "MODÈLE PROVISOIRE – NON OFFICIEL";

    /** Les champs qui commandent ou alimentent les formulaires (§B8). */
    static final String FICHES_EXIGEES = "B04-CD-01";
    static final String FORME_GARANTIE = "B04-CD-02";
    static final String REFERENCE_AOO = "B02-OB-03";
    static final String OBJET = "B02-OB-01";
    static final String AUTORITE = "B01-AC-01";
    static final String ADRESSE = "B01-AC-02";
    static final String MONTANT_GARANTIE = "B05-GS-03";
    static final String VALIDITE_GARANTIE = "B05-GS-04";
    static final String REMISE_OFFRES = "B04-LR-03";
    static final String VALIDITE_OFFRES = "B04-VO-01";

    private static final String A_COMPLETER = "……………… (à compléter par le candidat)";
    private static final String NON_RENSEIGNE = "— (non renseigné dans la fiche)";
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Map<String, String> TITRES = Map.of(
            "A1", "Fiche de renseignements A1",
            "A2", "Fiche de renseignements A2",
            "A3", "Fiche de renseignements A3",
            "A4", "Fiche de renseignements A4",
            "C1", "Garantie bancaire de soumission (C1)",
            "C2", "Caution personnelle et solidaire de soumission (C2)");

    private FormulairesCandidat() {
    }

    public static String titre(String type) {
        return TITRES.get(type);
    }

    /**
     * @param lots désignations des lots du plan, dans l'ordre des rangs (vide : ligne non allotie)
     */
    public static List<DocumentFicheModele> generer(FicheMarcheDto fiche, List<String> lots, LocalDateTime validation) {
        List<DocumentFicheModele> documents = new ArrayList<>();
        List<Integer> rangs = new ArrayList<>();
        boolean alloti = Boolean.TRUE.equals(fiche.getSaisieParLot()) && lots.size() > 1;
        if (alloti) {
            for (int n = 1; n <= lots.size(); n++) {
                rangs.add(n);
            }
        } else {
            rangs.add(null);
        }
        String pied = "Plan " + (fiche.getRefeDossier() == null ? "—" : fiche.getRefeDossier()) + " · ligne "
                + fiche.getIdDetail() + " · fiche marché version " + fiche.getVersion()
                + (validation == null ? "" : " validée le " + validation.toLocalDate().format(JOUR))
                + " · gabarit provisoire, en attente du modèle officiel";

        for (String a : ChampFicheMarcheListe.valeurs(valeur(fiche, FICHES_EXIGEES, null))) {
            if (!TITRES.containsKey(a) || !a.startsWith("A")) {
                continue;
            }
            for (Integer lot : rangs) {
                List<DocumentFicheModele.Rubrique> rubriques = new ArrayList<>();
                rubriques.add(new DocumentFicheModele.Rubrique("En-tête pré-rempli par la fiche", entete(fiche, lot, lots)));
                rubriques.add(new DocumentFicheModele.Rubrique("À compléter par le candidat", List.of(
                        new DocumentFicheModele.Ligne("Date", A_COMPLETER),
                        new DocumentFicheModele.Ligne("Identité, capacités, références", A_COMPLETER))));
                if ("A1".equals(a)) {
                    boolean groupement = fiche.getCadrage() != null
                            && "OUI".equalsIgnoreCase(String.valueOf(fiche.getCadrage().get("groupement")));
                    rubriques.add(new DocumentFicheModele.Rubrique(
                            "A1-b — Renseignements additionnels lorsque le candidat est un groupement",
                            List.of(new DocumentFicheModele.Ligne("A1-b", groupement ? A_COMPLETER
                                    : "Non applicable : le cadrage n'admet pas le groupement."))));
                }
                documents.add(modele(a, lot, fiche, rubriques, pied));
            }
        }

        String forme = valeur(fiche, FORME_GARANTIE, null);
        List<String> garanties = forme == null ? List.of()
                : forme.contains("C1") && forme.contains("C2") ? List.of("C1", "C2") : List.of(forme.trim().toUpperCase());
        for (String c : garanties) {
            if (!TITRES.containsKey(c)) {
                continue;
            }
            for (Integer lot : rangs) {
                documents.add(modele(c, lot, fiche, "C1".equals(c) ? c1(fiche, lot, lots) : c2(fiche, lot, lots), pied));
            }
        }
        return documents;
    }

    private static DocumentFicheModele modele(String type, Integer lot, FicheMarcheDto fiche,
            List<DocumentFicheModele.Rubrique> rubriques, String pied) {
        return new DocumentFicheModele(type, titre(type) + (lot == null ? "" : " — lot " + lot), fiche.getDesignationMarche(),
                List.of(new DocumentFicheModele.Bloc(titre(type), rubriques)), pied, lot, List.of(), FILIGRANE);
    }

    private static List<DocumentFicheModele.Ligne> entete(FicheMarcheDto fiche, Integer lot, List<String> lots) {
        return List.of(
                new DocumentFicheModele.Ligne("N° d'appel d'offres et titre", referenceEtTitre(fiche)),
                new DocumentFicheModele.Ligne("Autorité contractante", ou(valeur(fiche, AUTORITE, null))),
                new DocumentFicheModele.Ligne("Lot visé", lotVise(lot, lots)));
    }

    private static List<DocumentFicheModele.Rubrique> c1(FicheMarcheDto fiche, Integer lot, List<String> lots) {
        String montant = montant(valeur(fiche, MONTANT_GARANTIE, lot));
        return List.of(
                new DocumentFicheModele.Rubrique("Blancs pré-remplis par la fiche", List.of(
                        new DocumentFicheModele.Ligne("A : (nom et adresse de l'Acheteur)", acheteur(fiche)),
                        new DocumentFicheModele.Ligne("Titre du marché", titreMarche(fiche, lot, lots)),
                        new DocumentFicheModele.Ligne("Au profit de", ou(valeur(fiche, AUTORITE, null))),
                        new DocumentFicheModele.Ligne("À concurrence d'un montant de (chiffres et lettres)", montant),
                        new DocumentFicheModele.Ligne("Validité de la garantie", validiteGarantie(fiche)))),
                new DocumentFicheModele.Rubrique("Laissés au candidat et à son garant", List.of(
                        new DocumentFicheModele.Ligne("Nom du candidat, date, signature", A_COMPLETER),
                        new DocumentFicheModele.Ligne("Banque, adresse, cachet", A_COMPLETER))));
    }

    private static List<DocumentFicheModele.Rubrique> c2(FicheMarcheDto fiche, Integer lot, List<String> lots) {
        LocalDate remise = ControlesFicheMarche.date(valeur(fiche, REMISE_OFFRES, null));
        BigDecimal jours = ControlesFicheMarche.nombre(valeur(fiche, VALIDITE_OFFRES, null));
        String expiration = remise == null || jours == null ? NON_RENSEIGNE
                : remise.plusDays(jours.longValue()).format(JOUR) + " (remise des offres + " + jours.toPlainString() + " jours)";
        String reference = valeur(fiche, REFERENCE_AOO, null);
        return List.of(
                new DocumentFicheModele.Rubrique("Blancs pré-remplis par la fiche", List.of(
                        new DocumentFicheModele.Ligne("Pour (dénomination et adresse de l'Autorité contractante)", acheteur(fiche)),
                        new DocumentFicheModele.Ligne("Sur (objet du marché et références de l'appel d'offres)",
                                titreMarche(fiche, lot, lots) + (reference == null ? "" : " — appel d'offres " + reference)),
                        new DocumentFicheModele.Ligne("Au plus tard le (remise des offres)",
                                remise == null ? NON_RENSEIGNE : remise.format(JOUR)),
                        new DocumentFicheModele.Ligne("Validité de l'offre expirant le", expiration),
                        new DocumentFicheModele.Ligne("Ladite caution s'élève à (chiffres et lettres)",
                                montant(valeur(fiche, MONTANT_GARANTIE, lot))),
                        new DocumentFicheModele.Ligne("Validité de la caution", validiteGarantie(fiche)))),
                new DocumentFicheModele.Rubrique("Laissés au candidat et à la caution", List.of(
                        new DocumentFicheModele.Ligne("Organisme de caution, siège social", A_COMPLETER),
                        new DocumentFicheModele.Ligne("Nom et adresse du candidat", A_COMPLETER),
                        new DocumentFicheModele.Ligne("Lieu, date, signature, cachet", A_COMPLETER))));
    }

    // ------------------------------------------------------------------ valeurs

    /** La valeur d'un champ (saisie, reprise du plan, reflet) ; pour un lot, {@code CODE#n} puis le code nu. */
    static String valeur(FicheMarcheDto fiche, String code, Integer lot) {
        for (Map<String, String> m : java.util.Arrays.asList(fiche.getValeurs(), fiche.getValeursPpm(), fiche.getValeursCadrage())) {
            if (m == null) {
                continue;
            }
            String v = lot == null ? null : m.get(LotsFiche.cle(code, lot));
            v = v != null ? v : m.get(code);
            if (v != null && !v.isBlank() && !"null".equalsIgnoreCase(v.trim())) {
                return v.trim();
            }
        }
        return null;
    }

    private static String referenceEtTitre(FicheMarcheDto fiche) {
        String ref = valeur(fiche, REFERENCE_AOO, null);
        String objet = valeur(fiche, OBJET, null);
        return (ref == null ? "N° non renseigné" : ref) + " — " + (objet == null ? fiche.getDesignationMarche() : objet);
    }

    private static String acheteur(FicheMarcheDto fiche) {
        String nom = valeur(fiche, AUTORITE, null);
        String adresse = valeur(fiche, ADRESSE, null);
        return nom == null ? NON_RENSEIGNE : nom + (adresse == null ? "" : ", " + adresse);
    }

    private static String titreMarche(FicheMarcheDto fiche, Integer lot, List<String> lots) {
        String objet = valeur(fiche, OBJET, null);
        String titre = objet == null ? ou(fiche.getDesignationMarche()) : objet;
        return lot == null ? titre : titre + " — " + lotVise(lot, lots);
    }

    private static String lotVise(Integer lot, List<String> lots) {
        if (lot == null) {
            return "Marché non alloti";
        }
        String d = lot - 1 < lots.size() ? lots.get(lot - 1) : null;
        return "Lot " + lot + (d == null || d.isBlank() ? "" : " : " + d);
    }

    private static String montant(String brut) {
        BigDecimal m = ControlesFicheMarche.nombre(brut);
        return m == null ? NON_RENSEIGNE : ValeursPpmService.montant(m) + " Ariary (" + MontantEnLettres.ariary(m) + ")";
    }

    /** « jusqu'au cent cinquième (105ème) jour » après la date limite de remise des offres. */
    private static String validiteGarantie(FicheMarcheDto fiche) {
        BigDecimal n = ControlesFicheMarche.nombre(valeur(fiche, VALIDITE_GARANTIE, null));
        if (n == null || n.signum() <= 0) {
            return NON_RENSEIGNE;
        }
        long j = n.longValue();
        return "jusqu'au " + NombreEnLettres.ordinal(j) + " (" + j + (j == 1 ? "er" : "ème") + ") jour";
    }

    private static String ou(String v) {
        return v == null ? NON_RENSEIGNE : v;
    }

    /** Lecture d'une valeur de liste à choix multiples (« A1,A3 »). */
    private static final class ChampFicheMarcheListe {
        static List<String> valeurs(String v) {
            return cnm.prs.entity.ChampFicheMarche.liste(v);
        }
    }
}
