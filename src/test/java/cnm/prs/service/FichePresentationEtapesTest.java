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

    @Test
    @DisplayName("⚠️ 2026-10-20 (« fiche-presentation-marche ») — l'écart à l'estimation : signé, une décimale, les deux montants ; nul sans l'un d'eux")
    void ecart() {
        assertThat(FichePresentationService.ecart(new java.math.BigDecimal("179570562"), new java.math.BigDecimal("231563789")))
                .startsWith("−22,5 %").contains("179 570 562", "231 563 789");
        assertThat(FichePresentationService.ecart(new java.math.BigDecimal("110"), new java.math.BigDecimal("100"))).startsWith("+10,0 %");
        assertThat(FichePresentationService.ecart(null, java.math.BigDecimal.TEN)).isNull();
        assertThat(FichePresentationService.ecart(java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO)).isNull();
    }

    @Test
    @DisplayName("⚠️ 2026-10-20 (« fiche-presentation-marche », Q1) — prévu / réalisé : lancement par la publication, attribution, étape à venir, étape sans date omise")
    void prevuRealise() {
        java.util.Map<String, cnm.prs.entity.ChampFicheMarche> champs = new java.util.LinkedHashMap<>();
        for (String[] c : java.util.List.of(new String[] { "B04-CP-01", "Date de lancement de la mise en concurrence" },
                new String[] { "B04-CP-04", "Date d'attribution du contrat-cadre" },
                new String[] { "B04-CP-05", "Date de notification du contrat-cadre au(x) titulaire(s)" },
                new String[] { "B04-CP-06", "Envoi des demandes d'offres optimisées" })) {
            cnm.prs.entity.ChampFicheMarche champ = new cnm.prs.entity.ChampFicheMarche();
            champ.setCode(c[0]);
            champ.setLibelle(c[1]);
            champ.setActif(true);
            champ.setType("DATE");
            champs.put(c[0], champ);
        }
        cnm.prs.dto.FicheMarcheDto fiche = new cnm.prs.dto.FicheMarcheDto();
        fiche.setValeurs(java.util.Map.of("B04-CP-01", "2026-10-19", "B04-CP-04", "2026-12-04", "B04-CP-05", "2099-12-21", "B04-CP-06", "2026-11-24"));
        FicheMarcheService.EtatVersion ev = new FicheMarcheService.EtatVersion(1, fiche, champs, null, null);   // champs sans filtre de type ni de catégorie
        cnm.prs.entity.Attribution a = new cnm.prs.entity.Attribution();
        a.setAttribueLe(java.time.LocalDateTime.of(2026, 12, 2, 10, 0));
        List<Ligne> l = FichePresentationService.prevuRealise(ev, a, null, java.time.LocalDate.of(2026, 10, 20), null);
        assertThat(l).extracting(Ligne::code).containsExactly("B04-CP-01", "B04-CP-04", "B04-CP-05", "B04-CP-06");
        assertThat(l).extracting(Ligne::valeur).containsExactly("prévu le 19/10/2026 ; réalisé le 20/10/2026",
                "prévu le 04/12/2026 ; réalisé le 02/12/2026", "prévu le 21/12/2099 ; non réalisé",
                "prévu le 24/11/2026");   // ⚠️ « …-ecarts », §B2 : les offres optimisées, « prévu » seul
        assertThat(l.get(0).libelle()).isEqualTo("Date de lancement de la mise en concurrence");
    }
}
