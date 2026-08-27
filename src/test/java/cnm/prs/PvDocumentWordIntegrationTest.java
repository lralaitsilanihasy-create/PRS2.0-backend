package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import cnm.prs.service.PvDocumentContexte;

/**
 * Document du Projet de PV - generation directe par le generateur, sur le modele Word central.
 *
 * <p>Les neuf tests marques {@code @Tag("word")} convertissent le docx en PDF via MS Word
 * (documents4j) : ils sont exclus en CI Linux ({@code -DexcludedGroups=word}) et ne tournent
 * qu'en local. Les deux tests de format de date « L'an ... » restent purement unitaires.</p>
 */
class PvDocumentWordIntegrationTest extends CnmIntegrationTestSupport {

    private PvDocumentContexte ctxPv(String nomPresident, String nomChefCommission,
            java.util.List<PvDocumentContexte.Observation> observations) {
        return ctxPv(java.time.LocalDate.of(2026, 6, 23), nomPresident, nomChefCommission, observations);
    }

    private PvDocumentContexte ctxPv(java.time.LocalDate dateExamen, String nomPresident, String nomChefCommission,
            java.util.List<PvDocumentContexte.Observation> observations) {
        return new PvDocumentContexte(
                dateExamen,                                 // date d'examen
                "00007/DDP/CRM-ANT/PV/2026",               // refPv
                java.time.LocalDate.of(2026, 6, 15),       // date de réception
                "Ministère de l'Économie et des Finances", // entité contractante
                2026,                                       // exercice
                "ANTANANARIVO",                             // localité (libellé)
                "ANTANANARIVO",                             // chef-lieu (⚠️ 2026-08-04, lieu « A …, le »)
                nomPresident, nomChefCommission,
                "Paul MEMBRE", "Vero VERIFICATEUR",
                null,                                       // numMaj (⚠️ 2026-08-05) : null = plan INITIAL
                observations);
    }

    private java.util.List<PvDocumentContexte.Observation> troisObservations() {
        return java.util.List.of(
                new PvDocumentContexte.Observation("Conformité au budget", "AU_LIEU_DE_A", "LIRE_ALPHA"),
                new PvDocumentContexte.Observation("Conformité au budget", "AU_LIEU_DE_B", "LIRE_BRAVO"),
                new PvDocumentContexte.Observation("Délais de passation", "AU_LIEU_DE_C", "LIRE_CHARLIE"));
    }

    @Test
    @DisplayName("Document PV — le PDF contient l'image de l'emblème")
    @org.junit.jupiter.api.Tag("word") // conversion docx→PDF via MS Word (documents4j) — exclu en CI Linux (voir .github/workflows/ci.yml)
    void document_pv_genere_embleme_present() throws Exception {
        byte[] pdf = pvDocumentGenerator.genererPdf(ctxPv("Jean PRESIDENT", null, troisObservations()));
        assertTrue(contientImage(pdf), "le PDF du PV contient au moins un objet image (emblème)");
    }

    @Test
    @DisplayName("Document PV — date d'examen en toutes lettres dans « L'an … » (année + et le + jour mois)")
    @org.junit.jupiter.api.Tag("word") // conversion docx→PDF via MS Word (documents4j) — exclu en CI Linux (voir .github/workflows/ci.yml)
    void document_pv_date_examen_toutes_lettres() throws Exception {
        byte[] pdf = pvDocumentGenerator.genererPdf(ctxPv("Jean PRESIDENT", null, troisObservations()));
        assertTrue(texteDuPdf(pdf).contains("deux mille vingt-six et le vingt-trois juin"),
                "la date d'examen apparaît au format « année et le jour mois » en toutes lettres");
    }

    @Test
    @DisplayName("Date « L'an » — format année + et le + jour mois (23/06/2019)")
    void date_examen_an_format_ok() {
        org.junit.jupiter.api.Assertions.assertEquals("deux mille dix-neuf et le vingt-trois juin",
                cnm.prs.service.NombreEnLettres.dateExamenPourLAn(java.time.LocalDate.of(2019, 6, 23)));
    }

    @Test
    @DisplayName("Date « L'an » — 30/06/2026 → « deux mille vingt-six et le trente juin »")
    void date_examen_an_2026_ok() {
        org.junit.jupiter.api.Assertions.assertEquals("deux mille vingt-six et le trente juin",
                cnm.prs.service.NombreEnLettres.dateExamenPourLAn(java.time.LocalDate.of(2026, 6, 30)));
    }

    @Test
    @DisplayName("Document PV — « Séance du » reste en chiffres « 30 juin 2026 »")
    @org.junit.jupiter.api.Tag("word") // conversion docx→PDF via MS Word (documents4j) — exclu en CI Linux (voir .github/workflows/ci.yml)
    void document_pv_seance_format_chiffres() throws Exception {
        byte[] pdf = pvDocumentGenerator.genererPdf(
                ctxPv(java.time.LocalDate.of(2026, 6, 30), "Jean PRESIDENT", null, troisObservations()));
        assertTrue(texteDuPdf(pdf).contains("Séance du 30 juin 2026"),
                "« Séance du » reste au format chiffres");
    }

    @Test
    @DisplayName("Document PV — « L'an … » au format toutes lettres (année + et le + jour mois)")
    @org.junit.jupiter.api.Tag("word") // conversion docx→PDF via MS Word (documents4j) — exclu en CI Linux (voir .github/workflows/ci.yml)
    void document_pv_lan_format_lettres() throws Exception {
        byte[] pdf = pvDocumentGenerator.genererPdf(
                ctxPv(java.time.LocalDate.of(2026, 6, 30), "Jean PRESIDENT", null, troisObservations()));
        // L'apostrophe de « L'an » est courbe dans le modèle → on valide la date + le texte fixe qui suit.
        assertTrue(texteDuPdf(pdf).contains(
                "deux mille vingt-six et le trente juin, la Commission Centrale des Marchés"),
                "le paragraphe « L'an … » porte la date au format toutes lettres");
    }

    @Test
    @DisplayName("Document PV — bloc présents filtré : PV sans Président → ligne Président absente")
    @org.junit.jupiter.api.Tag("word") // conversion docx→PDF via MS Word (documents4j) — exclu en CI Linux (voir .github/workflows/ci.yml)
    void document_pv_presents_filtre_signataires() throws Exception {
        // Signé par le Membre + le Chef de commission, pas par le Président.
        byte[] pdf = pvDocumentGenerator.genererPdf(ctxPv(null, "Chef COMMISSION", troisObservations()));
        assertFalse(texteDuPdf(pdf).contains("Président de la Commission Nationale des Marchés"),
                "la ligne Président est retirée quand le Président n'a pas signé");
    }

    @Test
    @DisplayName("Document PV — ANNEXE : une ligne par observation (3 observations → 3 lignes)")
    @org.junit.jupiter.api.Tag("word") // conversion docx→PDF via MS Word (documents4j) — exclu en CI Linux (voir .github/workflows/ci.yml)
    void document_pv_annexe_observations_multiples() throws Exception {
        String texte = texteDuPdf(pvDocumentGenerator.genererPdf(
                ctxPv("Jean PRESIDENT", null, troisObservations())));
        assertTrue(texte.contains("LIRE_ALPHA") && texte.contains("LIRE_BRAVO") && texte.contains("LIRE_CHARLIE"),
                "les 3 observations apparaissent dans l'ANNEXE");
    }

    @Test
    @DisplayName("Document PV — aucun placeholder résiduel <...>")
    @org.junit.jupiter.api.Tag("word") // conversion docx→PDF via MS Word (documents4j) — exclu en CI Linux (voir .github/workflows/ci.yml)
    void document_pv_aucun_placeholder() throws Exception {
        String texte = texteDuPdf(pvDocumentGenerator.genererPdf(
                ctxPv("Jean PRESIDENT", "Chef COMMISSION", troisObservations())));
        assertFalse(java.util.regex.Pattern.compile("<[A-Z]").matcher(texte).find(),
                "aucun placeholder <...> ne subsiste dans le PDF du PV");
    }

    @Test
    @DisplayName("Document PV — titre « COMMISSION CENTRALE » sans « /REGIONALE »")
    @org.junit.jupiter.api.Tag("word") // conversion docx→PDF via MS Word (documents4j) — exclu en CI Linux (voir .github/workflows/ci.yml)
    void document_pv_titre_sans_regionale() throws Exception {
        String texte = texteDuPdf(pvDocumentGenerator.genererPdf(
                ctxPv("Jean PRESIDENT", null, troisObservations())));
        assertTrue(texte.contains("PROCES-VERBAL DE LA COMMISSION CENTRALE"),
                "le titre porte « COMMISSION CENTRALE »");
        assertFalse(texte.contains("REGIONALE"), "« /REGIONALE » est retiré du titre");
    }

    @Test
    @DisplayName("Document PV — phrase d'avis « Commission Centrale » sans « /Régionale »")
    @org.junit.jupiter.api.Tag("word") // conversion docx→PDF via MS Word (documents4j) — exclu en CI Linux (voir .github/workflows/ci.yml)
    void document_pv_avis_sans_regionale() throws Exception {
        String texte = texteDuPdf(pvDocumentGenerator.genererPdf(
                ctxPv("Jean PRESIDENT", null, troisObservations())));
        assertTrue(texte.contains("La Commission Centrale des Marchés émet un AVIS FAVORABLE"),
                "la phrase d'avis porte « Commission Centrale »");
        assertFalse(texte.contains("Régionale"), "« /Régionale » est retiré de la phrase d'avis");
    }
}
