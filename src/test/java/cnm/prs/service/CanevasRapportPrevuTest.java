package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.CanevasRapportDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.Dossier;
import cnm.prs.repository.DossierRepository;

/** ⚠️ 17/10 (« canevas-rapport-evaluation ») — le contrat-cadre n'a pas de canevas ; les prestations intellectuelles en ont deux parties. */
class CanevasRapportPrevuTest {

    @Test
    @DisplayName("Canevas prévu à quantités fixes et à commandes, pas au contrat-cadre")
    void prevu() {
        for (String type : new String[] {"QUANTITE_FIXE", "A_COMMANDE"}) {
            assertThat(CanevasRapportService.prevu(new FicheMarcheService.EtatVersion(1, new FicheMarcheDto(), Map.of(), type,
                    "FOURNITURES_SERVICES"))).as(type).isTrue();
        }
        assertThat(CanevasRapportService.prevu(new FicheMarcheService.EtatVersion(1, new FicheMarcheDto(), Map.of(), "CONTRAT_CADRE",
                "TRAVAUX"))).isFalse();
    }

    @Test
    @DisplayName("Q2 — prestations intellectuelles : un seul canevas, partie technique puis partie finale, chacune signée ; la version "
            + "soumise est celle présentée")
    void prestationsIntellectuellesEnDeuxParties() {
        DossierRepository dossiers = mock(DossierRepository.class);
        FicheMarcheService fiches = mock(FicheMarcheService.class);
        SousCriteresFiche sousCriteres = mock(SousCriteresFiche.class);
        BesoinFiche besoin = mock(BesoinFiche.class);
        Dossier d = new Dossier();
        d.setIdDossier(7);
        d.setIdDmc(3L);
        d.setVersionFicheSoumise(2);
        when(dossiers.findById(7)).thenReturn(Optional.of(d));
        FicheMarcheDto etat = new FicheMarcheDto();
        etat.setDesignationMarche("Étude de faisabilité");
        when(fiches.versionPresentee(3L, 2)).thenReturn(Optional.of(new FicheMarcheService.VersionPresentee(40,
                new FicheMarcheService.EtatVersion(2, etat, Map.of(), "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES"))));
        when(sousCriteres.lister(any())).thenReturn(List.of());
        CanevasRapportService service = new CanevasRapportService(dossiers, fiches, mock(ValeursPpmService.class), besoin, sousCriteres,
                null, null);

        CanevasRapportDto c = service.contenu(7);
        assertThat(c.versionFiche()).isEqualTo(2);
        assertThat(c.sections()).extracting(CanevasRapportDto.Section::code).containsExactly("REFERENCES", "PROPOSITIONS_RECUES",
                "EXAMEN_PRELIMINAIRE", "EVALUATION_TECHNIQUE", "SIGNATURES", "OUVERTURE_FINANCIERE", "EVALUATION_FINALE", "NEGOCIATIONS",
                "SIGNATURES_FINALE");
        assertThat(c.sections()).extracting(CanevasRapportDto.Section::partie).containsExactly("TECHNIQUE", "TECHNIQUE", "TECHNIQUE",
                "TECHNIQUE", "TECHNIQUE", "FINALE", "FINALE", "FINALE", "FINALE");
        assertThat(c.sections().get(0).lignes()).extracting(l -> l.valeur()).contains("Étude de faisabilité");
    }
}
