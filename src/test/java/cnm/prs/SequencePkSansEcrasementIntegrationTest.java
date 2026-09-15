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
    @Autowired
    private cnm.prs.repository.NotificationRepository notificationRepositoryDuTest;

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

    @Test
    @DisplayName("Notifications — une notification créée par l'Administrateur (POST, PK cliente) sur la prochaine "
            + "valeur de seq_notification n'est pas écrasée par la notification suivante émise par le serveur")
    void notificationServeur_nEcrasePasLaNotificationDeLAdministrateur() throws Exception {
        int prochaine = prochaineValeur("seq_notification");

        mvc.perform(post("/api/notifications").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idNotification\":" + prochaine + ",\"typeNotif\":\"RECTIFICATION_PRMP\","
                        + "\"destinataireIm\":\"CTRMEM\",\"titre\":\"Notification de l'administrateur\","
                        + "\"corps\":\"posee a la main\",\"lu\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idNotification").value(prochaine));
        entityManager.flush();
        entityManager.clear();

        cnm.prs.entity.Notification emise = notificationService.emettre(1,
                cnm.prs.enums.TypeNotification.RECTIFICATION_PRMP, "CTRVER", null, "Notification serveur", "corps");
        entityManager.flush();
        entityManager.clear();

        assertThat(emise.getIdNotification()).as("PK libre pour la notification serveur").isNotEqualTo(prochaine);
        cnm.prs.entity.Notification admin =
                notificationRepositoryDuTest.findById(prochaine).orElseThrow();
        assertThat(admin.getTitre()).as("la notification de l'administrateur n'est pas écrasée")
                .isEqualTo("Notification de l'administrateur");
        assertThat(admin.getDestinataireIm()).isEqualTo("CTRMEM");
        assertThat(notificationRepositoryDuTest.findById(emise.getIdNotification()).orElseThrow().getTitre())
                .isEqualTo("Notification serveur");
    }

    @Test
    @DisplayName("Navettes de PV — une navette créée par l'Administrateur (POST, PK cliente) sur la prochaine valeur "
            + "de seq_pv_navette n'est pas écrasée par la navette que le circuit ajoute à la soumission du projet")
    void navetteDuCircuit_nEcrasePasLaNavetteDeLAdministrateur() throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":93,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        int prochaine = prochaineValeur("seq_pv_navette");

        mvc.perform(post("/api/pv-navettes").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idNavette\":" + prochaine + ",\"idPv\":93,\"numNavette\":1,\"sens\":\"SOUMISSION\","
                        + "\"imActeur\":\"CTRADM\",\"dateAction\":\"2026-09-15T10:00:00\","
                        + "\"commentaire\":\"navette de l'administrateur\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idNavette").value(prochaine));

        // Le Membre soumet le projet : le circuit ajoute sa navette (PK serveur).
        mvc.perform(post("/api/pv-examens/93/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"projet pret\"}"))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        cnm.prs.entity.PvNavette admin = pvNavetteRepository.findById(prochaine).orElseThrow();
        assertThat(admin.getCommentaire()).as("la navette de l'administrateur n'est pas écrasée")
                .isEqualTo("navette de l'administrateur");
        assertThat(admin.getNumNavette()).isEqualTo(1);
        assertThat(admin.getImActeur()).isEqualTo("CTRADM");
        assertThat(pvNavetteRepository.findAll()).filteredOn(n -> n.getIdPv().equals(93))
                .as("la navette du circuit s'ajoute, sous une PK libre").hasSize(2)
                .anyMatch(n -> "projet pret".equals(n.getCommentaire()) && n.getIdNavette() != prochaine);
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
