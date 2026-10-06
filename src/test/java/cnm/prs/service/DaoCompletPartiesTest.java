package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.SpecificationsFiche;
import cnm.prs.repository.SpecificationsFicheRepository;

/** ⚠️ 2026-10-06 (DAO complet) — le plan ARMP : ordre des parties, titre de section sur la première pièce seule, lots dans l'ordre. */
class DaoCompletPartiesTest {

    @Test
    @DisplayName("IC, DPAO, formulaires, AE lot par lot, CCAP puis LF, spécifications, CCAG ; avis et classeurs exclus")
    void ordre() {
        SpecificationsFicheRepository specs = mock(SpecificationsFicheRepository.class);
        SpecificationsFiche s = new SpecificationsFiche();
        s.setContenu(new byte[] { 9 });
        when(specs.findById(7)).thenReturn(Optional.of(s));
        DaoCompletService service = new DaoCompletService(null, null, null, specs);
        FicheMarcheDto etat = new FicheMarcheDto();
        etat.setCategorie("FOURNITURES_SERVICES");
        List<DocumentFicheMarche> docs = List.of(doc("LF", "docx", null, 1), doc("AE", "docx", 2, 2), doc("CCAP", "docx", null, 3),
                doc("AE", "docx", 1, 4), doc("A2", "docx", null, 5), doc("DPAO", "docx", null, 6), doc("DPAO", "pdf", null, 7),
                doc("BP", "xlsx", null, 8), doc("A1", "docx", null, 9), doc("AVIS", "docx", null, 10));
        List<DaoCompletWord.Partie> p = service.parties(etat, 7, docs);
        assertThat(p).extracting(DaoCompletWord.Partie::titre).containsExactly("Section I — Instructions aux candidats",
                "Section II — Données particulières de l'appel d'offres", "Section III — Formulaires de soumission", null,
                "Section IV — Acte d'engagement", null, "Section V — Cahier des clauses administratives particulières", null,
                "Spécifications techniques", "Section VI — Cahier des clauses administratives générales");
        assertThat(p.subList(1, 9)).extracting(x -> (int) x.docx()[0]).containsExactly(6, 9, 5, 4, 2, 3, 1, 9);
    }

    @Test
    @DisplayName("Prestations intellectuelles : libellés DPIC et CPS")
    void prestationsIntellectuelles() {
        SpecificationsFicheRepository specs = mock(SpecificationsFicheRepository.class);
        when(specs.findById(7)).thenReturn(Optional.empty());
        FicheMarcheDto etat = new FicheMarcheDto();
        etat.setCategorie("PRESTATIONS_INTELLECTUELLES");
        etat.setValeursPpm(Map.of("OBJET", "Étude"));
        DaoCompletService service = new DaoCompletService(null, null, null, specs);
        assertThat(service.parties(etat, 7, List.of(doc("DPIC", "docx", null, 1), doc("CCAP", "docx", null, 2))))
                .extracting(DaoCompletWord.Partie::titre).containsExactly("Section I — Instructions aux candidats",
                        "Section II — Données particulières des instructions aux candidats", "Section V — Cahier des prescriptions spéciales",
                        "Section VI — Cahier des clauses administratives générales");
        assertThat(service.entete(etat)).isEqualTo("Dossier de consultation — Étude");
    }

    private static DocumentFicheMarche doc(String type, String ext, Integer lot, int marque) {
        return new DocumentFicheMarche(marque, 7, type, ext, type + "." + ext, 1L, "", LocalDateTime.now(), new byte[] { (byte) marque }, lot,
                null);
    }
}
