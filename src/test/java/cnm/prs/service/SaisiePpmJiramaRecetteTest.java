package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import cnm.prs.dto.SaisiePpmImportResult;
import cnm.prs.repository.CompteRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.NatureRepository;
import cnm.prs.repository.SoaBeneficiaireRepository;

/**
 * ⚠️ 2026-11-04 (demande front « lecture-ppm-jirama », B1 à B3) — la lecture des plans PDF réels de la JIRAMA, sur le poste du pilote
 * ({@code ~/Downloads}, ou {@code PRS_PPM_DIR}) ; ignoré quand les fichiers sont absents (CI).
 */
class SaisiePpmJiramaRecetteTest {

    private static Path pdf(String nom) {
        String env = System.getenv("PRS_PPM_DIR");
        Path p = (env != null && !env.isBlank() ? Path.of(env) : Path.of(System.getProperty("user.home"), "Downloads")).resolve(nom);
        return Files.isRegularFile(p) ? p : null;
    }

    private static SaisiePpmImportResult lire(String nom) throws Exception {
        Path p = pdf(nom);
        assumeTrue(p != null, "PDF absent : " + nom);
        SaisiePpmImportService s = new SaisiePpmImportService(mock(EntiteContractRepository.class), mock(NatureRepository.class),
                mock(ModePassationRepository.class), mock(CompteRepository.class), mock(SoaBeneficiaireRepository.class));
        return s.importer(new MockMultipartFile("fichier", nom, "application/pdf", Files.readAllBytes(p)));
    }

    @Test
    @DisplayName("PPM 0 — B3 et B1 : « d'audit 2024 » reste dans l'objet, le montant est 116 000 000, le compte 604001 (pas 6040 + « 01 » "
            + "collé au montant du bénéficiaire)")
    void anneeEtCompteDuPlanInitial() throws Exception {
        SaisiePpmImportResult r = lire("JIRAMA PPM 0.pdf");
        assertThat(r.marches()).hasSize(40);
        var audit = r.marches().stream().filter(m -> m.designationMarche().contains("rapport d'audit")).findFirst().orElseThrow();
        assertThat(audit.designationMarche()).endsWith("d'audit 2024");
        assertThat(audit.montEstim()).isEqualByComparingTo("116000000");
        assertThat(audit.beneficiaires()).singleElement().satisfies(b -> {
            assertThat(b.numCompte()).isEqualTo("604001");
            assertThat(b.ancMontBenef()).isEqualByComparingTo("116000000");
        });
    }

    @Test
    @DisplayName("MAJ 1 — B1 et B2 : compte 602212 (pas 6022 + « 12 » recollé à l'objet), lots séparés par « – » ; aucun compte tronqué")
    void compteEtLotsDeLaMiseAJour() throws Exception {
        SaisiePpmImportResult r = lire("JIRAMA PPM MAJ 1.pdf");
        assertThat(r.marches()).hasSize(56);
        var pieces = r.marches().stream().filter(m -> m.designationMarche().contains("DEUTZ 400KW")).findFirst().orElseThrow();
        assertThat(pieces.designationMarche()).contains("DEUTZ 400KW – Lot 2").endsWith("(CONTRAT-CADRE)").doesNotContain("¿");
        assertThat(pieces.beneficiaires()).extracting(b -> b.numCompte()).containsExactly("602212");
        List<String> comptes = r.marches().stream().flatMap(m -> m.beneficiaires().stream()).map(b -> b.numCompte()).toList();
        assertThat(comptes).allMatch(c -> c == null || c.length() == 6, "tous les comptes de la JIRAMA ont six chiffres");
    }
}
