package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.GabaritDto;

/**
 * ⚠️ 2026-10-02 (demande front « gabarits ») — les phrases des modèles qui impriment un champ, relevées sur les modèles
 * réels. Pur : ni base, ni Spring.
 */
class GabaritsDaoTest {

    private final GabaritsDao dao = new GabaritsDao(new ModelesDao());

    @Test
    @DisplayName("Recette : B09-MA-03 (travaux) a le seul gabarit du CCAP ; B03-NT-01 celui de l'AE une fois ; B10-PC-01, "
            + "paragraphe fait du seul jeton, aucun ; B02-OB-03 l'AE avec « du ___ et, en particulier… », sans doublon")
    void recette() {
        assertThat(dao.gabarits("B09-MA-03", "QUANTITE_FIXE", "TRAVAUX")).containsExactly(new GabaritDto("CCAP",
                "La diminution dans la masse des travaux au delà de ",
                " de la masse initiale des travaux ouvre droit à indemnisation pour l'Entrepreneur.", null));
        assertThat(dao.gabarits("B03-NT-01", "QUANTITE_FIXE", "TRAVAUX"))
                .containsExactly(new GabaritDto("AE", "Est désigné comme Comptable Assignataire de paiement le ", "", null));
        assertThat(dao.gabarits("B10-PC-01", "QUANTITE_FIXE", "TRAVAUX")).isEmpty();
        List<GabaritDto> ob03 = dao.gabarits("B02-OB-03", "QUANTITE_FIXE", "TRAVAUX");
        assertThat(ob03).filteredOn(g -> g.document().equals("AE"))
                .contains(new GabaritDto("AE", "Après avoir pris connaissance du Dossier d'Appel d'Offres N° ",
                        " du ___ et, en particulier, des documents composant le Cahier des Prescriptions Spéciales :", null));
        assertThat(ob03).doesNotHaveDuplicates();
        assertThat(ob03).extracting(GabaritDto::document).isSortedAccordingTo(
                java.util.Comparator.comparingInt(GabaritsDao.ORDRE_DOCUMENTS::indexOf));
    }

    @Test
    @DisplayName("Suffixe du jeton ; périmètre par catégorie et par forme (un champ des travaux n'a pas de gabarit en "
            + "fournitures ; l'avis et la lettre suivent leur catégorie) ; balises SI / FINSI retirées, autres jetons « ___ »")
    void suffixesEtPerimetre() {
        assertThat(dao.gabarits("B05-GQ-03", "QUANTITE_FIXE", "TRAVAUX")).extracting(GabaritDto::suffixe)
                .contains("parLot", "lettres");
        assertThat(dao.gabarits("B09-MA-03", "QUANTITE_FIXE", "FOURNITURES_SERVICES")).isEmpty();
        assertThat(dao.sigles("QUANTITE_FIXE", "TRAVAUX")).containsExactly("DPAO-T", "CCAP-T", "AE-T", "AVIS-T");
        assertThat(dao.sigles("CONTRAT_CADRE", "FOURNITURES_SERVICES")).containsExactly("DPAC-CC", "AE-CC", "AVIS-F");
        assertThat(dao.sigles("QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES")).contains("LETTRE-PI").doesNotContain("AVIS-F");
        for (String code : List.of("B05-GQ-03", "B02-OB-03", "B03-QT-14")) {
            assertThat(dao.gabarits(code, "QUANTITE_FIXE", "TRAVAUX")).as(code)
                    .allMatch(g -> !(g.avant() + g.apres()).contains("{{"));
        }
    }
}
