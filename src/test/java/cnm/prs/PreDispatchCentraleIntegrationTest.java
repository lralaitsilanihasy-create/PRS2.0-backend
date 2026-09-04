package cnm.prs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import cnm.prs.entity.Dispatch;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Examen;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;

/**
 * ⚠️ <strong>Pré-dispatch de la localité CENTRALE réservé au Président</strong> (règle du pilote,
 * 2026-09-03), avec ses trois précisions du même jour : dérogation de réattribution, retrait réservé
 * au dispatcheur, et examen réservé à l'attributaire.
 *
 * <p>Ce que ces tests protègent avant tout, c'est la <strong>frontière entre les deux régimes</strong> :
 * la centrale se referme sur le Président, les régionales ne bougent pas. Une garde trop large
 * paralyserait les CRM, qui représentent l'essentiel des dossiers.</p>
 */
class PreDispatchCentraleIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private cnm.prs.repository.NotificationRepository notificationRepository;

    /** Dossier central prêt à dispatcher, avec sa réception. */
    private int dossierCentralPret(int idDossier, int idReception) {
        Dossier d = dossier(idDossier, "PRET_DISPATCH");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        receptionRepository.save(reception(idReception, idDossier, "CTRCC1", true));
        return idReception;
    }

    private String corpsDispatch(int idDispatch, int idReception, String membre) {
        return "{\"idDispatch\":" + idDispatch + ",\"idReception\":" + idReception
                + ",\"imCtrlMembre\":\"" + membre + "\",\"interimDispatch\":false}";
    }

    // ------------------------------------------------------------------ 1 à 3 : la garde centrale

    @Test
    @DisplayName("1 — CC sur un dossier CENTRAL : POST /api/dispatchs → 403, le dispatch relève du Président")
    void cc_dispatchCentral_refuse() throws Exception {
        int rec = dossierCentralPret(7101, 7101);
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7101, rec, "CTRMEM")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Commission nationale")));
    }

    @Test
    @DisplayName("2 — Président sur le même dossier central → accepté (comportement inchangé)")
    void president_dispatchCentral_accepte() throws Exception {
        int rec = dossierCentralPret(7102, 7102);
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7102, rec, "CTRMEM")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("3 — ANTI-RÉGRESSION : le CC d'une RÉGIONALE dispatche toujours dans sa localité")
    void ccRegional_dispatch_accepte() throws Exception {
        // La garde ne doit fermer que la centrale : les CRM portent l'essentiel des dossiers.
        Dossier d = dossier(7103, "PRET_DISPATCH");
        d.setIdTypeDossier("DDP"); d.setIdPrmp("PRMP001"); d.setIdLocalite("TMS");
        dossierRepository.save(d);
        receptionRepository.save(reception(7103, 7103, "CTRCC2", true));
        controleurRepository.save(controleur("MEMTMS1", 5, "TMS"));
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR,
                "CTRCC2", "TMS");

        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7103, 7103, "MEMTMS1")))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ 4 et 5 : la réattribution

    @Test
    @DisplayName("4 — Dérogation : le CC ATTRIBUTAIRE réattribue un dossier central ; un CC tiers → 403")
    void ccAttributaire_reattribue() throws Exception {
        int rec = dossierCentralPret(7104, 7104);
        // Le Président dispatche AU CC (« Chef de commission ⤴ ») : c'est lui l'attributaire.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7104, rec, "CTRCC1")))
                .andExpect(status().isCreated());

        // Le CC attributaire peut réattribuer à un Membre, malgré la garde « centrale ».
        mvc.perform(put("/api/dispatchs/7104").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7104, rec, "CTRMEM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRMEM"));
        mvc.perform(get("/api/dossiers/7104").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("DISPATCHE"));
        // Le nouvel attributaire est notifié — le PUT ne prévenait personne jusqu'ici.
        org.junit.jupiter.api.Assertions.assertTrue(
                notificationRepository.findAll().stream()
                        .anyMatch(n -> "CTRMEM".equals(n.getDestinataireIm())
                                && TypeNotification.EXAMEN_A_FAIRE.name().equals(n.getTypeNotif())
                                && Integer.valueOf(7104).equals(n.getIdDossier())),
                "le nouvel attributaire doit être notifié EXAMEN_A_FAIRE");

        // Un CC qui n'est PAS l'attributaire courant reste refusé (la dérogation est nominative).
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR,
                "CTRCC2", "ANT");
        mvc.perform(put("/api/dispatchs/7104").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7104, rec, "CTRMEM")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5 — Réattribution avec un examen déjà entamé → 409 (le circuit propre passe par « Retirer »)")
    void reattribution_examenEntame_conflit() throws Exception {
        int rec = dossierCentralPret(7105, 7105);
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7105, rec, "CTRCC1")))
                .andExpect(status().isCreated());
        Examen e = new Examen();
        e.setIdExamen(7105);
        e.setIdDispatch(7105);
        e.setImCtrlMembre("CTRCC1");
        examenRepository.save(e);

        mvc.perform(put("/api/dispatchs/7105").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7105, rec, "CTRMEM")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("examen")));
    }

    // ------------------------------------------------------------------ 6 : l'annulation

    @Test
    @DisplayName("6 — Retrait réservé au DISPATCHEUR : CC dispatcheur accepté, CC attributaire ou tiers → 403")
    void annulation_reserveeAuDispatcheur() throws Exception {
        // (a) CC NON dispatcheur (le Président a dispatché) et attributaire → 403, pas d'auto-retrait.
        int rec = dossierCentralPret(7106, 7106);
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7106, rec, "CTRCC1")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/dispatchs/7106/annuler").header("Authorization", tokenCc))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("dispatcheur")));

        // (b) Après réattribution par le CC, IM_CTRL_DISPATCH devient le sien : il PEUT retirer au Membre.
        mvc.perform(put("/api/dispatchs/7106").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7106, rec, "CTRMEM")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dispatchs/7106/annuler").header("Authorization", tokenCc))
                .andExpect(status().isNoContent());

        // (c) Le Président n'est jamais restreint.
        int rec2 = dossierCentralPret(7107, 7107);
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7107, rec2, "CTRMEM")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/dispatchs/7107/annuler").header("Authorization", tokenPresident))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ 7 : l'examen

    @Test
    @DisplayName("7 — Examen réservé à l'ATTRIBUTAIRE : le dispatcheur et le CC en copie sont refusés (403)")
    void examen_reserveAAttributaire() throws Exception {
        int rec = dossierCentralPret(7108, 7108);
        // Président → Membre : le CC de la localité reçoit la copie, le Président est le dispatcheur.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7108, rec, "CTRMEM")))
                .andExpect(status().isCreated());

        String corpsExamen = "{\"idExamen\":7108,\"idDispatch\":7108,\"imCtrlMembre\":\"CTRMEM\"}";
        // Le dispatcheur (Président) : refusé. Avant le 2026-09-03, son profil ≠ MEMBRE l'exemptait.
        mvc.perform(post("/api/examens").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corpsExamen))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("attributaire")));
        // Le CC en copie : refusé lui aussi — suivre n'est pas examiner.
        mvc.perform(post("/api/examens").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corpsExamen))
                .andExpect(status().isForbidden());
        // L'attributaire : accepté.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(corpsExamen))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("7 bis — ANTI-RÉGRESSION circuit court : le CC ATTRIBUTAIRE (« moi-même ⤴ ») examine toujours")
    void examen_ccAttributaire_accepte() throws Exception {
        Dossier d = dossier(7109, "PRET_DISPATCH");
        d.setIdTypeDossier("DDP"); d.setIdPrmp("PRMP001"); d.setIdLocalite("TMS");
        dossierRepository.save(d);
        receptionRepository.save(reception(7109, 7109, "CTRCC2", true));
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR,
                "CTRCC2", "TMS");
        // Le CC régional s'auto-attribue : il EST l'attributaire, la garde le laisse passer.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON).content(corpsDispatch(7109, 7109, "CTRCC2")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/examens").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":7109,\"idDispatch\":7109,\"imCtrlMembre\":\"CTRCC2\"}"))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ 8 et 9 : notification, référentiel

    @Test
    @DisplayName("8 — PRET_DISPATCH d'un dossier CENTRAL : Président notifié, AUCUNE notification au CC")
    void pretDispatchCentral_nNotifiePasLeCc() throws Exception {
        Dossier d = dossier(7110, "SOUMIS");
        d.setIdTypeDossier("DDP"); d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":7110,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":true}"))
                .andExpect(status().isCreated());

        // Notifier le CC lui annoncerait une tâche qu'il recevra en 403 : un cul-de-sac.
        org.junit.jupiter.api.Assertions.assertTrue(notificationsPretDispatch(7110, "CTRPRE"),
                "le Président doit être notifié");
        org.junit.jupiter.api.Assertions.assertFalse(notificationsPretDispatch(7110, "CTRCC1"),
                "le CC ne doit PAS être notifié sur un dossier central");
    }

    @Test
    @DisplayName("8 bis — ANTI-RÉGRESSION : sur une RÉGIONALE, le Président ET le CC sont notifiés")
    void pretDispatchRegional_notifieLesDeux() throws Exception {
        Dossier d = dossier(7111, "SOUMIS");
        d.setIdTypeDossier("DDP"); d.setIdPrmp("PRMP001"); d.setIdLocalite("TMS");
        dossierRepository.save(d);
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR,
                "CTRCC2", "TMS");
        mvc.perform(post("/api/receptions").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":7111,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC2\",\"complet\":true}"))
                .andExpect(status().isCreated());

        org.junit.jupiter.api.Assertions.assertTrue(notificationsPretDispatch(7111, "CTRPRE"),
                "le Président doit être notifié");
        org.junit.jupiter.api.Assertions.assertTrue(notificationsPretDispatch(7111, "CTRCC2"),
                "le CC régional doit rester notifié");
    }

    @Test
    @DisplayName("9 — GET /api/localites expose estCentrale : true pour ANT, false ailleurs")
    void localites_exposeEstCentrale() throws Exception {
        mvc.perform(get("/api/localites").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idLocalite=='ANT')].estCentrale", hasItem(true)))
                
                .andExpect(jsonPath("$[?(@.idLocalite=='TMS')].estCentrale", hasItem(false)));
    }

    private boolean notificationsPretDispatch(int idDossier, String im) {
        return notificationRepository.findAll().stream()
                .anyMatch(n -> im.equals(n.getDestinataireIm())
                        && TypeNotification.PRET_DISPATCH.name().equals(n.getTypeNotif())
                        && Integer.valueOf(idDossier).equals(n.getIdDossier()));
    }

    /** Dispatch en base, pour les cas où le passage par l'API n'est pas le sujet. */
    @SuppressWarnings("unused")
    private Dispatch dispatchEnBase(int id, int rec, String membre, String dispatcheur) {
        return dispatchRepository.save(dispatch(id, rec, null, membre, dispatcheur));
    }
}
