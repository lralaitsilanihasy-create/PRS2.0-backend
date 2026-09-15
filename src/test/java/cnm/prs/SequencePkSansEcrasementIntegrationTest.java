package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import cnm.prs.entity.PointsCtrl;
import cnm.prs.seed.PointsCtrlFicheAgpmSeeder;

/**
 * ⚠️ <strong>Perte de données constatée en recette le 2026-09-15</strong> — quatre points de contrôle créés par
 * l'Administrateur ({@code POST /api/points-ctrls}) remplacés après un redémarrage.
 *
 * <p>Mécanisme : une table où coexistent une PK <strong>cliente</strong> honorée
 * ({@code ClePrimaire.reallouer} : l'écran d'administration calcule {@code max + 1}) et une allocation serveur
 * qui prend {@code nextval} <strong>tel quel</strong>. La PK cliente acceptée au-dessus du curseur ne consomme
 * pas la séquence ; le {@code nextval} suivant la rend une seconde fois, et le {@code save} — un
 * {@code merge} sur une PK présente — écrase la ligne sans erreur.</p>
 *
 * <p>Chaque test place la ligne « cliente » exactement sur la prochaine valeur de la séquence (lue puis remise
 * en place par {@code setval}), déclenche l'allocation serveur, et vérifie que la ligne cliente est intacte et
 * que la ligne serveur a reçu une PK libre.</p>
 */
class SequencePkSansEcrasementIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private PointsCtrlFicheAgpmSeeder pointsCtrlFicheAgpmSeeder;

    @Test
    @DisplayName("Seeder FICHE/AGPM — un point créé par l'Administrateur sur la prochaine valeur de seq_points_ctrl "
            + "survit au démarrage : intact, et les points semés reçoivent des PK libres")
    void seederPointsCtrl_nEcrasePasLePointDeLAdministrateur() throws Exception {
        int prochaine = prochaineValeur("seq_points_ctrl");

        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":" + prochaine + ",\"libelPointCtrl\":\"Point de l'administrateur\","
                        + "\"decriptPointCtrl\":\"cree en recette\",\"ordrePointCtrl\":1,\"obligatoire\":true,"
                        + "\"idTypeDossier\":\"DDP\",\"portee\":\"LIGNE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPointCtrl").value(prochaine));
        entityManager.flush();
        entityManager.clear();
        long avant = pointsCtrlRepository.count();

        pointsCtrlFicheAgpmSeeder.run();
        entityManager.flush();
        entityManager.clear();

        PointsCtrl admin = pointsCtrlRepository.findById(prochaine).orElseThrow();
        assertThat(admin.getLibelPointCtrl()).as("le point de l'administrateur n'est pas écrasé")
                .isEqualTo("Point de l'administrateur");
        assertThat(admin.getDecriptPointCtrl()).isEqualTo("cree en recette");
        assertThat(admin.getPortee().name()).isEqualTo("LIGNE");

        List<PointsCtrl> semes = pointsCtrlRepository.findAll().stream()
                .filter(p -> p.getIdPointCtrl() != prochaine).filter(p -> "DDP".equals(p.getIdTypeDossier()))
                .filter(p -> p.getPortee() != null && !"LIGNE".equals(p.getPortee().name()))
                .toList();
        assertThat(semes).as("les sept graines (FICHE, AGPM, SUPPRESSION) sont semées").hasSize(7);
        assertThat(pointsCtrlRepository.count()).as("sept lignes de plus, aucune remplacée").isEqualTo(avant + 7);
    }

    /**
     * Prochaine valeur que rendra {@code nextval} : consommée, puis remise en place par
     * {@code setval(…, false)} pour que l'allocation serveur la rende à nouveau.
     */
    private int prochaineValeur(String sequence) {
        Long valeur = jdbcTemplate.queryForObject("select nextval('" + sequence + "')", Long.class);
        jdbcTemplate.queryForObject("select setval('" + sequence + "', " + valeur + ", false)", Long.class);
        return valeur.intValue();
    }
}
