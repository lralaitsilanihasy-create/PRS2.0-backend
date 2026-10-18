package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.dto.MentionsDto;

/**
 * ⚠️ R3c (manuel révisé pour l'application, lot 1, §B3 ; arbitrage Q3 du pilote du 10/10 : « mentions complètes ») — les mentions
 * du modèle type que chaque gabarit de publication doit porter (avis : 2-II-A P11, travaux 2-II-B P11 ; lettre des prestations
 * intellectuelles : 2-II-F P16 ; lettre de l'appel d'offres restreint : 2-II-D pièce 5), vérifiées deux fois :
 * <ul>
 *   <li>sur le <strong>gabarit</strong> : le texte du modèle porte la mention (un jeton ou une phrase) — un écart est un défaut du
 *   modèle, à corriger ;</li>
 *   <li>sur le <strong>document rendu</strong> pour une fiche : la valeur n'est pas en pointillés — un écart est une saisie à
 *   compléter —, ou la mention est sans objet pour cette fiche (lot unique, garantie non requise, pas de visite des lieux).</li>
 * </ul>
 * Le compte bancaire de l'ARMP tient lieu des « trois formes de paiement » et de l'« adresse de l'agent comptable » (décision du
 * pilote du 01/10) ; la lettre des prestations intellectuelles n'a pas de coût de dossier (aucun champ : écart nommé).
 */
public final class MentionsPublication {

    /**
     * Une mention : son code, où la chercher au gabarit, où lire sa valeur au document (les groupes capturés sont les valeurs : une
     * occurrence vaut si aucun de ses groupes n'est en pointillés), quand elle est sans objet, et l'écart à nommer si le gabarit ne
     * peut pas la porter.
     */
    record Mention(String code, String libelle, Pattern gabarit, Pattern document, Predicate<FicheMarcheDto> sansObjet,
            String detailAbsente) {

        Mention(String code, String libelle, String gabarit, String document) {
            this(code, libelle, gabarit, document, null);
        }

        Mention(String code, String libelle, String gabarit, String document, Predicate<FicheMarcheDto> sansObjet) {
            this(code, libelle, Pattern.compile(gabarit, Pattern.MULTILINE), Pattern.compile(document, Pattern.MULTILINE), sansObjet, null);
        }

        Mention sansChamp(String detail) {
            return new Mention(code, libelle, gabarit, document, sansObjet, detail);
        }
    }

    private static final String P = FormulairesCandidat.POINTILLES;

    // ------------------------------------------------------------------ avis (2-II-A P11)
    static final Mention OBJET = new Mention("OBJET", "Objet du marché", "«\\s*\\{\\{B02-OB-01\\}\\}\\s*»",
            "(?:pour fournir|travaux suivants :) «\\s*([^»]*?)\\s*»");   // la phrase de l'objet, pas « à quantités fixes »
    static final Mention LOTS_MAX = new Mention("LOTS_MAX", "Nombre maximum de lots par candidat", "\\{\\{B02-AU-07\\}\\}",
            "plus de (\\S+) lot\\(s\\)|(lot unique indivisible)", f -> !"OUI".equalsIgnoreCase(cadrage(f, "alloti")));
    static final Mention FORME_PRIX = new Mention("FORME_PRIX", "Forme des prix", "conclu à prix (unitaire|forfaitaire)",
            "(conclu à prix (?:unitaire|forfaitaire)(?: et à prix forfaitaire)?)");
    static final Mention ADRESSE_CONSULTATION = new Mention("ADRESSE_CONSULTATION", "Adresse de consultation du dossier",
            "Adresse : \\{\\{B04-DS-09\\}\\}", "^- Adresse : (.+)$");
    static final Mention ADRESSE_RETRAIT = new Mention("ADRESSE_RETRAIT", "Lieu de retrait (achat) du dossier",
            "retiré auprès de \\{\\{B01-AC-01\\}\\}", "retiré auprès de (.+?) moyennant");
    static final Mention DATE_HEURE_LIMITE = new Mention("DATE_HEURE_LIMITE", "Date et heure limites de remise des offres",
            "au plus tard le \\{\\{(?:B04-LR-03\\}\\}, \\{\\{B04-LR-04|B04-OV-02\\.heureLocale|B04-CP-02\\.heureLocale)\\}\\}",
            "au plus tard le (.+?) et seront ouvert");
    static final Mention HEURE_OUVERTURE = new Mention("HEURE_OUVERTURE", "Heure d'ouverture des plis",
            "ouvert(?:s|es) immédiatement après l’heure limite", "(ouvert(?:s|es) immédiatement après l’heure limite)");
    static final Mention COUT_DOSSIER = new Mention("COUT_DOSSIER", "Coût du dossier", "\\{\\{B04-DS-05", "\\(Ar ([^)]+)\\)");
    static final Mention COMPTE_PAIEMENT = new Mention("COMPTE_PAIEMENT",
            "Modalités de paiement : compte bancaire de l'ARMP (tient lieu des trois formes de paiement et de l'adresse de l'agent comptable)",
            "compte bancaire de l’ARMP : \\{\\{PARAM\\.compte-dao\\}\\}", "compte bancaire de l’ARMP : (.+?)\\.?$");
    static final Mention FORMES_GARANTIE = new Mention("FORMES_GARANTIE", "Trois formes de la garantie de soumission",
            "caution personnelle et solidaire.*garantie bancaire.*chèque de banque",
            "(caution personnelle et solidaire.*chèque de banque)|(garantie de soumission n’est pas requise)");
    static final Mention MONTANT_GARANTIE = new Mention("MONTANT_GARANTIE", "Montant de la garantie de soumission", "\\{\\{B05-G[SQ]-03",
            "d’un montant de (.+?), ou son équivalent|(garantie de soumission n’est pas requise)");
    static final Mention VISITE_LIEUX = new Mention("VISITE_LIEUX", "Visite des lieux obligatoire (travaux)", "visite des lieux",
            "(visite des lieux est obligatoire)", f -> !"OUI".equalsIgnoreCase(valeur(f, "B04-VL-02")));
    static final Mention REUNION_PREPARATOIRE = new Mention("REUNION_PREPARATOIRE", "Réunion préparatoire (travaux)", "réunion préparatoire",
            "réunion préparatoire est prévue : (.+)$", f -> !"OUI".equalsIgnoreCase(valeur(f, "B04-RP-01")));

    // ------------------------------------------------------------------ lettres (2-II-F P16, 2-II-D pièce 5)
    static final Mention REFERENCE = new Mention("OBJET", "Référence et objet du marché",
            "Référence: \\{\\{B02-OB-03\\}\\} — \\{\\{B02-OB-01\\}\\}", "^Référence: (.+)$");
    static final Mention LISTE_CANDIDATS = new Mention("LISTE_CANDIDATS", "Liste des candidats invités", "\\{\\{LETTRE\\.candidats\\}\\}",
            "^- (.+)$");
    static final Mention MODE_SELECTION = new Mention("MODE_SELECTION", "Mode de sélection (prestations intellectuelles)", "\\{\\{SI:SFQC\\}\\}",
            "(de la qualité technique de la proposition|d’un budget prédéterminé|de la meilleure proposition financière|exclusivement de la qualité technique)");
    static final Mention DATE_HEURE_LIMITE_PI = new Mention("DATE_HEURE_LIMITE", "Date et heure limites de remise des propositions",
            "au plus tard le \\{\\{B04-LH-02\\.heureLocale\\}\\}", "au plus tard le (.+?) ;");
    static final Mention HEURE_OUVERTURE_PI = new Mention("HEURE_OUVERTURE", "Heure d'ouverture des propositions",
            "ouvertes immédiatement après l’heure limite", "(ouvertes immédiatement après l’heure limite)");
    static final Mention ADRESSE_REMISE_PI = new Mention("ADRESSE_REMISE", "Adresse de remise des propositions", "parvenir à \\{\\{B04-LH-01\\}\\}",
            "parvenir à (.+?) au plus tard");
    static final Mention ADRESSE_REMISE_AOR = new Mention("ADRESSE_REMISE", "Adresse de remise des offres",
            "parvenir à \\{\\{(?:B04-LR-02|B01-AC-02)\\}\\}", "parvenir à (.+?) au plus tard");
    static final Mention ADRESSE_ACCUSE = new Mention("ADRESSE_ACCUSE", "Adresse de la PRMP pour l'accusé de réception",
            "\\{\\{B01-AC-02\\}\\} \\(\\{\\{B01-AC-06\\}\\}\\):", "^(.+?) \\((.+)\\):$");
    static final Mention COUT_DC = COUT_DOSSIER.sansChamp("aucun champ de la fiche de prestations intellectuelles ne porte le coût du dossier "
            + "de consultation : il est remis avec la lettre ; à outiller si un dossier payant doit être prévu (manuel 2-II-F P16)");
    static final Mention COMPTE_PI = COMPTE_PAIEMENT.sansChamp("sans coût du dossier de consultation, la lettre ne porte pas de modalité de "
            + "paiement (manuel 2-II-F P16)");

    static final List<Mention> AVIS_F = List.of(OBJET, LOTS_MAX, FORME_PRIX, ADRESSE_CONSULTATION, ADRESSE_RETRAIT, DATE_HEURE_LIMITE,
            HEURE_OUVERTURE, COUT_DOSSIER, COMPTE_PAIEMENT, FORMES_GARANTIE, MONTANT_GARANTIE);
    static final List<Mention> AVIS_T = List.of(OBJET, LOTS_MAX, FORME_PRIX, ADRESSE_CONSULTATION, ADRESSE_RETRAIT, DATE_HEURE_LIMITE,
            HEURE_OUVERTURE, COUT_DOSSIER, COMPTE_PAIEMENT, FORMES_GARANTIE, MONTANT_GARANTIE, VISITE_LIEUX, REUNION_PREPARATOIRE);
    static final List<Mention> LETTRE_PI = List.of(REFERENCE, LISTE_CANDIDATS, MODE_SELECTION, ADRESSE_REMISE_PI, DATE_HEURE_LIMITE_PI,
            HEURE_OUVERTURE_PI, ADRESSE_ACCUSE, COUT_DC, COMPTE_PI);
    static final List<Mention> LETTRE_AOR = List.of(REFERENCE, LISTE_CANDIDATS, ADRESSE_CONSULTATION, ADRESSE_RETRAIT, COUT_DOSSIER,
            COMPTE_PAIEMENT, ADRESSE_REMISE_AOR, DATE_HEURE_LIMITE, HEURE_OUVERTURE, FORMES_GARANTIE, MONTANT_GARANTIE, ADRESSE_ACCUSE);

    private MentionsPublication() {
    }

    /** Les mentions attendues d'un gabarit ({@code AVIS-F}, {@code AVIS-T}, {@code LETTRE-PI}, {@code LETTRE-AOR}) ; vide sinon. */
    public static List<Mention> mentions(String sigle) {
        return switch (sigle == null ? "" : sigle) {
            case "AVIS-F" -> AVIS_F;
            case "AVIS-T" -> AVIS_T;
            case "LETTRE-PI" -> LETTRE_PI;
            case ModelesDao.LETTRE_AOR -> LETTRE_AOR;
            default -> List.of();
        };
    }

    /**
     * Vérifie un gabarit et le document qu'il rend pour une fiche : chaque mention est-elle au gabarit, et renseignée (ou sans objet)
     * sur la fiche ?
     */
    public static MentionsDto verifier(String sigle, FichierCommande.Modele modele, DocumentLibre rendu, FicheMarcheDto fiche) {
        String gabarit = normaliser(new DocumentLibre(sigle, null, modele.elements(), null).texte());
        String document = normaliser(rendu.texte());
        List<MentionsDto.Mention> resultats = new ArrayList<>();
        boolean gabaritComplet = true;
        boolean documentComplet = true;
        for (Mention m : mentions(sigle)) {
            boolean presente = m.gabarit().matcher(gabarit).find();
            boolean sansObjet = presente && m.sansObjet() != null && m.sansObjet().test(fiche);
            Boolean renseignee = !presente || sansObjet ? null : renseignee(m.document(), document);
            String detail = !presente ? (m.detailAbsente() != null ? m.detailAbsente() : "absente du gabarit " + sigle + " : mention à ajouter au modèle")
                    : sansObjet ? "sans objet pour cette fiche"
                    : Boolean.TRUE.equals(renseignee) ? null : "non renseignée sur la fiche (pointillés) : saisie à compléter";
            gabaritComplet &= presente;
            documentComplet &= presente && (sansObjet || Boolean.TRUE.equals(renseignee));
            // ⚠️ 2026-10-18 (« avis-specifique-en-donnees », §B1, Q2) — la valeur telle que le document l'imprime, nulle en pointillés.
            String valeur = presente && (sansObjet || Boolean.TRUE.equals(renseignee)) ? valeur(m.document(), document) : null;
            resultats.add(new MentionsDto.Mention(m.code(), m.libelle(), presente, renseignee, sansObjet, detail, valeur));
        }
        return new MentionsDto(sigle, gabaritComplet, documentComplet, resultats);
    }

    /** Une occurrence du motif dont aucun groupe capturé n'est en pointillés. */
    static boolean renseignee(Pattern document, String texte) {
        Matcher m = document.matcher(texte);
        while (m.find()) {
            boolean ok = true;
            boolean unGroupe = false;
            for (int g = 1; g <= m.groupCount(); g++) {
                String v = m.group(g);
                if (v == null) {
                    continue;
                }
                unGroupe = true;
                if (v.contains(P) || v.contains("{{") || v.isBlank()) {   // pointillés, ou jeton laissé tel quel (valeur du plan absente)
                    ok = false;
                }
            }
            if (ok && (unGroupe || m.groupCount() == 0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ⚠️ 2026-10-18 (« avis-specifique-en-donnees », §B1) — la valeur imprimée : les groupes capturés de la première occurrence
     * renseignée (un seul groupe : sa valeur ; plusieurs : jointes par « — »), ou {@code null} si aucune ne l'est (Q2).
     */
    static String valeur(Pattern document, String texte) {
        Matcher m = document.matcher(texte);
        while (m.find()) {
            List<String> groupes = new ArrayList<>();
            boolean ok = true;
            for (int g = 1; g <= m.groupCount(); g++) {
                String v = m.group(g);
                if (v == null) {
                    continue;
                }
                if (v.contains(P) || v.contains("{{") || v.isBlank()) {
                    ok = false;
                }
                groupes.add(v.trim());
            }
            if (ok && !groupes.isEmpty()) {
                return String.join(" — ", groupes);
            }
        }
        return null;
    }

    static String normaliser(String s) {
        return s == null ? "" : s.replace(' ', ' ').replace(' ', ' ');
    }

    private static String cadrage(FicheMarcheDto f, String cle) {
        Object v = f == null || f.getCadrage() == null ? null : f.getCadrage().get(cle);
        return v == null ? null : String.valueOf(v);
    }

    private static String valeur(FicheMarcheDto f, String code) {
        return f == null || f.getValeurs() == null ? null : f.getValeurs().get(code);
    }
}
