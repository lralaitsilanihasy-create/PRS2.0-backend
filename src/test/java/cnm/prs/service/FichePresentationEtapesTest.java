package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FichePresentationDto.Ligne;

/** ⚠️ Recette du 17/10 (remarque 3) — du calendrier du plan, les seules étapes du DAO, une ligne chacune (libellés du plan 100379). */
class FichePresentationEtapesTest {

    @Test
    @DisplayName("Établissement, transmission, lancement, remise, ouverture : la première étape de chaque préfixe, libellé court, dates jj/mm/aaaa")
    void etapesDuDao() {
        String dates = "Détermination des besoins : recueil et centralisation des besoins : 2026-01-05 ; "
                + "Établissement du DAO/DC : sur la base du « cahier de charges » et spécifications particulières : 2026-02-02 → 2026-02-20 ; "
                + "Transmission du DAO/DC à la CNM pour examen et avis : 2026-02-23 ; "
                + "Examen et avis de la CNM : 2026-03-02 ; "
                + "Remise en forme du DAO/DC : sans objet ; "
                + "LANCEMENT : 2026-03-16 ; "
                + "Lancement du DAO/DC : avis spécifique d'appel public à la concurrence : 2026-03-17 ; "
                + "Remise des offres : propositions : 2026-04-15 ; "
                + "Ouverture des plis : 2026-04-15 ; "
                + "Évaluation et classement des offres : 2026-04-20 → 2026-05-04";
        List<Ligne> l = FichePresentationService.etapesDuDao(dates);
        assertThat(l).extracting(Ligne::code)
                .containsExactly("ETABLISSEMENT_DAO", "TRANSMISSION_DAO", "LANCEMENT", "REMISE_OFFRES", "OUVERTURE_PLIS");
        assertThat(l).extracting(Ligne::libelle)
                .containsExactly("Établissement du DAO/DC", "Transmission du DAO/DC à la CNM pour examen et avis", "Lancement",
                        "Remise des offres", "Ouverture des plis");
        assertThat(l).extracting(Ligne::valeur)
                .containsExactly("02/02/2026 → 20/02/2026", "23/02/2026", "16/03/2026", "15/04/2026", "15/04/2026");
        assertThat(FichePresentationService.etapesDuDao(null)).isEmpty();
    }
}
