package cnm.prs;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import cnm.prs.entity.Dossier;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;

/**
 * Communication interne : notifications emises le long du circuit (dispatch, PV, navette, lettre
 * signee, assistant controleur), scoping de /mes, comptage des non-lues, et messagerie (envoi,
 * reception, marquage lu, confidentialite).
 *
 * <p><b>Ordre imposé (⚠️ ne pas renommer la classe à la légère)</b> :
 * Les identifiants issus des séquences PostgreSQL ({@code seq_message}, {@code seq_notification} —
 * LOT 3b) ne sont jamais supposés : chaque test LIT l'id rendu par le service (une séquence ne se
 * rejoue pas d'un test à l'autre, le rollback ne la remet pas à zéro). Aucun test de cette classe
 * ne dépend de l'ordre d'exécution.</p>
 */
class CommunicationInterneIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Lot D §4 — la liste globale de supervision est plafonnée à 500 notifications, "
            + "les plus récentes")
    void notifications_listeGlobale_plafonnee() throws Exception {
        // ⚠️ Audit 2026-08-27 (lot D §4) : t_notification grossit à chaque événement du circuit, pour
        // chaque destinataire ; l'écran d'administration en demandait la TOTALITÉ. Semis direct en SQL
        // (505 lignes) — passer par le service émettrait autant de courriels et de flux SSE.
        java.util.List<Object[]> lignes = new java.util.ArrayList<>();
        for (int i = 0; i < 505; i++) {
            lignes.add(new Object[] {
                    jdbcTemplate.queryForObject("select nextval('seq_notification')", Long.class),
                    "PRET_DISPATCH", "CTRMEM",
                    java.sql.Timestamp.valueOf(java.time.LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(i)) });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO public.t_notification (\"ID_NOTIFICATION\", \"TYPE_NOTIF\", "
                        + "\"DESTINATAIRE_IM\", \"DATE_ENVOI\") VALUES (?, ?, ?, ?)",
                lignes);

        String corps = mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(500))
                .andReturn().getResponse().getContentAsString();

        // Ce sont bien les PLUS RÉCENTES : la toute première semée est écartée par le plafond.
        java.util.List<String> dates = com.jayway.jsonpath.JsonPath.read(corps, "$[*].dateEnvoi");
        org.junit.jupiter.api.Assertions.assertFalse(dates.contains("2026-01-01T00:00:00"),
                "le plafond écarte les plus anciennes, pas les plus récentes");
    }

    @Test
    @DisplayName("Notifications : /mes scopé, comptage non-lues, marquer lu (refus si pas la mienne), liste globale Admin-only")
    void notifications_meScopeLectureGlobalAdmin() throws Exception {
        // 2 notifications pour CTRMEM, 1 pour CTRPRE.
        // ⚠️ LOT 3b (2026-08-26) — les ids ne sont plus 1, 2, 3 : ID_NOTIFICATION vient désormais de la
        // séquence seq_notification (l'ancien max+1 n'était pas atomique) et une séquence ne se rejoue
        // pas d'un test à l'autre. On retient donc les identifiants RENDUS par le service au lieu de
        // les supposer.
        int notifMembre1 = notificationService.emettreControleur(TypeNotification.PRET_DISPATCH, "CTRMEM", null, 1, TypeObjet.DOSSIER, 1, "Notif 1", "corps").getIdNotification();
        notificationService.emettreControleur(TypeNotification.PRET_DISPATCH, "CTRMEM", null, 2, TypeObjet.DOSSIER, 2, "Notif 2", "corps");
        int notifPresident = notificationService.emettreControleur(TypeNotification.PRET_DISPATCH, "CTRPRE", null, 3, TypeObjet.DOSSIER, 1, "Notif 3", "corps").getIdNotification();

        // Scoping : CTRMEM voit ses 2, CTRPRE voit sa 1.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.length()").value(1));

        // Comptage des non-lues.
        mvc.perform(get("/api/notifications/mes/non-lues/count").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.nonLues").value(2));

        // Marquer la première notif comme lue (CTRMEM) → lu=true ; le compteur descend à 1.
        mvc.perform(post("/api/notifications/" + notifMembre1 + "/lu").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lu").value(true));
        mvc.perform(get("/api/notifications/mes/non-lues/count").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.nonLues").value(1));

        // Marquer la notif de CTRPRE en tant que CTRMEM → 403.
        mvc.perform(post("/api/notifications/" + notifPresident + "/lu").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // Tout marquer lu (CTRMEM) → 1 restante traitée, puis 0 non-lue.
        mvc.perform(post("/api/notifications/lire-tout").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.traitees").value(1));
        mvc.perform(get("/api/notifications/mes/non-lues/count").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.nonLues").value(0));

        // Liste globale : interdite à un non-Admin (403), autorisée à l'Admin (200).
        mvc.perform(get("/api/notifications").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Notification message : l'envoi notifie le destinataire (NOUVEAU_MESSAGE, objet MESSAGE), pas l'expéditeur")
    void notification_nouveauMessage() throws Exception {
        // Le Membre envoie un message au CC.
        mvc.perform(post("/api/messages/envoyer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destinataireIm\":\"CTRCC1\",\"sujet\":\"Question\",\"corps\":\"Bonjour\"}"))
                .andExpect(status().isCreated());

        // Le CC (destinataire) reçoit une notification NOUVEAU_MESSAGE pointant l'objet MESSAGE.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVEAU_MESSAGE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVEAU_MESSAGE')].typeObjet", hasItem("MESSAGE")));

        // L'expéditeur (Membre) n'a pas de notification de message.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVEAU_MESSAGE')]", hasSize(0)));
    }

    @Test
    @DisplayName("Notification dispatch : le Membre assigné reçoit EXAMEN_A_FAIRE sur le dossier dispatché")
    void notification_examenAFaire() throws Exception {
        // Dossier PRET_DISPATCH d'ANT avec une réception fraîche.
        dossierRepository.save(dossier(20, "PRET_DISPATCH"));
        receptionRepository.save(reception(40, 20, "CTRSEC", true)); // CTRSEC = localité ANT
        // ⚠️ 2026-09-03 — dossier CENTRAL : le dispatch relève du Président. Le sujet du test est la
        // notification EXAMEN_A_FAIRE au Membre assigné, l’acteur du dispatch y est incident.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":50,\"idReception\":40,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());

        // Le Membre assigné reçoit EXAMEN_A_FAIRE pointant le dossier 20.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='EXAMEN_A_FAIRE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='EXAMEN_A_FAIRE')].idObjet", hasItem(20)));
    }

    @Test
    @DisplayName("Notification PV : la soumission d'un projet de PV notifie le CC et le Président (PV_A_VALIDER, objet PV)")
    void notification_pvAValider() throws Exception {
        // Création d'un PV sur l'examen 1 (chaîne → localité ANT), par le Membre.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":70,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        // Soumission du projet → PROJET_SOUMIS.
        mvc.perform(post("/api/pv-examens/70/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"a valider\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));

        // ⚠️ 2026-08-31 — PV_A_VALIDER ne part plus « aux P/CC au sens large » mais au SEUL DISPATCHEUR,
        // lui seul pouvant viser (§4). Le dispatcheur de la fixture est CTRPRE.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')].idObjet", hasItem(70)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')].typeObjet", hasItem("PV")));
        // Le CC d'ANT, non dispatcheur, ne la reçoit PLUS : on ne lui annonce pas une tâche qu'il
        // recevrait en 403.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')]", hasSize(0)));
    }

    @Test
    @DisplayName("Notification navette : retour (PV_A_RECTIFIER) et acceptation (PV_ACCEPTE) notifient le Membre auteur")
    void notification_navettePvAuteur() throws Exception {
        // Création + soumission d'un PV (auteur CTRMEM, localité ANT).
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":71,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/71/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"v1\"}"))
                .andExpect(status().isOk());

        // Le CC retourne le PV pour rectification → le Membre auteur reçoit PV_A_RECTIFIER (objet PV).
        mvc.perform(post("/api/pv-examens/71/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"commentaire\":\"corriger la synthese\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("EN_RECTIFICATION"));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_RECTIFIER')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_RECTIFIER')].idObjet", hasItem(71)));

        // Re-soumission puis VISA par le dispatcheur → le Membre auteur reçoit PV_ACCEPTE.
        mvc.perform(post("/api/pv-examens/71/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"v2\"}"))
                .andExpect(status().isOk());
        viser(71, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_ACCEPTE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_ACCEPTE')].idObjet", hasItem(71)));
    }

    @Test
    @DisplayName("Messagerie : envoi, réception, marquage lu et confidentialité")
    void messagerie_envoiReceptionLu() throws Exception {
        // Le Membre envoie un message au CC.
        // ⚠️ Suivi du LOT 2.3 (2026-08-27) — l'id n'est plus supposé (== 1) mais LU dans la réponse :
        // ID_MESSAGE vient de la séquence seq_message (LOT 3b), qu'aucun rollback ne remet à zéro —
        // supposer 1 n'était vrai que si ce test était le premier de la JVM à créer un message.
        // Même motif que notifications_meScopeLectureGlobalAdmin ci-dessus.
        String reponse = mvc.perform(post("/api/messages/envoyer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destinataireIm\":\"CTRCC1\",\"sujet\":\"Question\",\"corps\":\"Bonjour\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expediteurIm").value("CTRMEM"))
                .andExpect(jsonPath("$.destinataireIm").value("CTRCC1"))
                .andExpect(jsonPath("$.lu").value(false))
                .andReturn().getResponse().getContentAsString();
        int idMessage = com.jayway.jsonpath.JsonPath.read(reponse, "$.idMessage");

        // Boîte de réception du CC : 1 message ; envoyés du Membre : 1.
        mvc.perform(get("/api/messages/recus").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sujet").value("Question"));
        mvc.perform(get("/api/messages/envoyes").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        // Le CC marque le message comme lu.
        mvc.perform(post("/api/messages/" + idMessage + "/lu").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lu").value(true));

        // L'expéditeur (non destinataire) ne peut pas marquer lu → 403.
        mvc.perform(post("/api/messages/" + idMessage + "/lu").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // Un tiers ne peut pas lire le message (confidentialité) → 403.
        mvc.perform(get("/api/messages/" + idMessage).header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lettre signée → PRMP notifiée (LETTRE_RENVOI_RECUE)")
    void lettre_signee_prmp_notifiee() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='LETTRE_RENVOI_RECUE')]", hasSize(1)));
        // ⚠️ Audit lot B — la notification était émise par E-MAIL SEUL (destinataireRef nul) : invisible
        // de « mes notifications » dès que l'e-mail du compte diffère de t_prmp.EMAIL_PRMP. Elle est
        // désormais portée par la PRMP et pointe son dossier.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='LETTRE_RENVOI_RECUE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='LETTRE_RENVOI_RECUE')].idObjet", hasItem(1)));
    }

    @Test
    @DisplayName("Lettre signée → Assistant contrôleur notifié (LETTRE_RENVOI_COPIE)")
    void lettre_signee_assistant_notifie() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='LETTRE_RENVOI_COPIE')].destinataireIm", hasItem("CTRASS")));
    }

    @Test
    @DisplayName("PV signé avis DÉFAVORABLE (⚠️ 2026-08-02) → l'Assistant est notifié PV_A_ARCHIVER à la transmission SIGMP")
    void pv_signe_avis_defav_assistant_notifie() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(120, "DEF");   // dossier 1 → EN_VERIFICATION
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sens").value("NON_APPROUVE"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_ARCHIVER')].destinataireIm", hasItem("CTRASS")));
    }

    @Test
    @DisplayName("PV signé avis FAVR → Assistant NON notifié à la signature (PV_A_ARCHIVER n'arrive qu'après SIGMP)")
    void pv_signe_avis_favr_assistant_non_notifie() throws Exception {
        signerPvAvecAvis(121, "FAVR");
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_ARCHIVER')]", hasSize(0)));
    }
}
