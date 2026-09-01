package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import cnm.prs.service.PvDocumentContexte;

/**
 * ⚠️ Aperçu du bloc VISA refondu (arbitrage du pilote, 2026-09-01) — produit un PDF RÉEL, destiné à la
 * validation du libellé par le pilote (« le pilote validera sur un rendu »).
 *
 * <p>Tagué {@code word} : la conversion .docx → PDF pilote Microsoft Word, absent des runners Linux.
 * Ce test ne garde donc rien en CI — les règles, elles, sont éprouvées hors Word dans
 * {@code PvVisaInterimIntegrationTest} via {@code PvDocumentService.contexte(pv)}. Celui-ci ne sert
 * qu'à fabriquer la pièce à montrer.</p>
 *
 * <p>Le fichier est écrit dans {@code target/apercu/}, hors du dépôt : c'est un rendu daté, pas un
 * livrable versionné.</p>
 */
class ApercuBlocVisaTest extends CnmIntegrationTestSupport {

    private PvDocumentContexte contexte(String ligneViseur) {
        return new PvDocumentContexte(
                LocalDate.of(2026, 6, 23),
                "00007/DDP/CRM-TMS/PV/2026",
                LocalDate.of(2026, 6, 15),
                "Ministère de l'Économie et des Finances",
                2026,
                "TOAMASINA",
                "TOAMASINA",
                null,                                   // pas de Président : c'est le CC qui a visé
                "RANDRIA Paul",                         // Chef de la Commission (bloc « Étaient présents »)
                "RAKOTO Jean",                          // Membre attributaire
                "RASOA Vero",                           // Secrétaire de séance
                ligneViseur,
                null,
                List.of(new PvDocumentContexte.Observation(
                        "Conformité au budget", "AU_LIEU_DE", "LIRE")));
    }

    @Test
    @DisplayName("Aperçu — PV régional visé PAR INTÉRIM : rendu PDF pour validation du libellé par le pilote")
    @Tag("word")
    void apercu_regional_parInterim() throws Exception {
        byte[] pdf = pvDocumentGenerator.genererPdf(
                contexte("Visé par : RANDRIA Paul, Chef de la Commission — par intérim"),
                cnm.prs.service.PvDocumentGenerator.modele(cnm.prs.service.PvDocumentGenerator.SensAvis.AFSR, false, false));
        Path sortie = Path.of("target", "apercu", "apercu-visa-regional-interim.pdf");
        Files.createDirectories(sortie.getParent());
        Files.write(sortie, pdf);
        // ⚠️ Un aperçu qu'on ne relit pas ne prouve rien : sans cette assertion, un placeholder resté
        // en place ou une ligne perdue à la dérivation produirait un PDF valide et muet.
        String texte = texteDuPdf(pdf);
        assertTrue(texte.contains("Visé par : RANDRIA Paul, Chef de la Commission — par intérim"),
                "la ligne du viseur, mention comprise, est imprimée : " + sortie.toAbsolutePath());
        assertTrue(!texte.contains("<VISEUR>"), "le placeholder est bien substitué");
    }

    @Test
    @DisplayName("Aperçu — PV central visé normalement : la ligne est là, SANS mention (R1 + R2)")
    @Tag("word")
    void apercu_central_visaNormal() throws Exception {
        byte[] pdf = pvDocumentGenerator.genererPdf(
                contexte("Visé par : RAKOTOARISOA Hery, Président de la Commission Nationale des Marchés"),
                cnm.prs.service.PvDocumentGenerator.modele(cnm.prs.service.PvDocumentGenerator.SensAvis.AFSR, false, true));
        Path sortie = Path.of("target", "apercu", "apercu-visa-central-normal.pdf");
        Files.createDirectories(sortie.getParent());
        Files.write(sortie, pdf);
        String texte = texteDuPdf(pdf);
        assertTrue(texte.contains("Visé par : RAKOTOARISOA Hery, Président de la Commission Nationale des Marchés"),
                "la ligne du viseur est imprimée sur un PV central : " + sortie.toAbsolutePath());
        assertTrue(!texte.contains("par intérim"), "jamais de mention d'intérim en Centrale (R2)");
    }
}
