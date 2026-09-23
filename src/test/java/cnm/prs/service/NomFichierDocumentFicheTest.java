package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ⚠️ Fiche marché, lot 2a (2026-09-23, §B2) — nom de fichier des documents générés : type, plan, ligne, version. */
class NomFichierDocumentFicheTest {

    @Test
    @DisplayName("Exemple de la demande : référence du plan assainie (barres → tirets), ligne, version, extension")
    void exempleDeLaDemande() {
        assertThat(DocumentsFicheMarcheService.nomFichier("DPAO", "00001/PPM-AGPM/CNM/2026", 302873, 2, "docx"))
                .isEqualTo("DPAO_00001-PPM-AGPM-CNM-2026_302873_v2.docx");
    }

    @Test
    @DisplayName("Caractères hors [A-Za-z0-9-] repliés en un seul tiret, sans tiret en bord ; référence absente nommée")
    void assainissement() {
        assertThat(DocumentsFicheMarcheService.nomFichier("AE", " /00002 // MTP / 2026/ ", 7, 1, "pdf"))
                .isEqualTo("AE_00002-MTP-2026_7_v1.pdf");
        assertThat(DocumentsFicheMarcheService.nomFichier("CCAP", null, 7, 3, "pdf")).isEqualTo("CCAP_sans-reference_7_v3.pdf");
    }
}
