package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.ObservationControle;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ <strong>Revue du 2026-09-14 — {@code POST/PUT/DELETE /api/observation-controles} sans garde.</strong>
 *
 * <p>Le profil Membre suffisait : tout Membre écrivait sur n'importe quel résultat d'examen, y compris après
 * la signature du PV, alors que {@code /api/examen-details} réserve l'écriture à l'attributaire (ou au CC /
 * Président par délégation, dans sa localité) et la verrouille dès {@code PV_SIGNE}. Une ligne d'observation
 * appartient à un résultat : elle suit désormais les mêmes gardes, résolues à partir de ce résultat.</p>
 *
 * <p><strong>Méthode.</strong> Chaque refus est comparé à l'écriture de référence — un PUT sur un résultat
 * témoin du même examen via {@code /api/examen-details}, avec le même jeton : même code HTTP et même message.
 * La règle éprouvée est donc « celle d'examen-details », pas une copie écrite ici.</p>
 *
 * <p>⚠️ Même revue — la règle « point non conforme ⇒ au moins une ligne » de {@code /api/examen-details}
 * s'applique aussi au retrait d'une ligne par cette route (DELETE, ou PUT qui la déplace) : 400, même corps
 * d'erreur ; un point repassé conforme n'est pas bloqué.</p>
 *
 * <p>Décor : dossier 1 du socle ({@code EXAMINE}, ANT, dispatch 1 → Membre {@code CTRMEM}, examen 1) ; un
 * résultat non conforme {@value #RESULTAT} porteur d'une ligne, et un résultat témoin {@value #TEMOIN}.</p>
 */
class ObservationControleEcritureGardeeIntegrationTest extends CnmIntegrationTestSupport {

    private static final int PT_RESULTAT = 7701;
    private static final int PT_TEMOIN = 7702;
    /** Résultat d'examen (examen 1) auquel appartient la ligne d'observation éprouvée. */
    private static final int RESULTAT = 7701;
    /** Résultat témoin (examen 1) : cible de l'écriture de référence sur /api/examen-details. */
    private static final int TEMOIN = 7702;

    private int idLigne;

    @BeforeEach
    void resultatAvecUneLigne() {
        point(PT_RESULTAT);
        point(PT_TEMOIN);
        examenDetailRepository.save(resultat(RESULTAT, 1, PT_RESULTAT, false));
        examenDetailRepository.save(resultat(TEMOIN, 1, PT_TEMOIN, true));
        idLigne = ligne(RESULTAT, "500000", "5000000", 1);
    }

    // ------------------------------------------------------------------ POST

    @Test
    @DisplayName("POST observation-controles — gardes d'examen-details : attributaire 201 ; autre Membre, CC d'une "
            + "autre localité, PRMP, Vérificateur 403 ; CC de la localité (délégation) 201 ; 409 dès PV_SIGNE")
    void post_memesGardesQueExamenDetails() throws Exception {
        Supplier<MockHttpServletRequestBuilder> creation = () -> post("/api/observation-controles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":" + RESULTAT + ",\"auLieuDe\":\"12\",\"lire\":\"15\",\"ordre\":2}");

        refuseeCommeExamenDetails(creation, tokenAutreMembre(), 403);
        refuseeCommeExamenDetails(creation, tokenCcTms(), 403);
        refuseeCommeExamenDetails(creation, tokenPrmp, 403);
        refuseeCommeExamenDetails(creation, tokenVerificateur(), 403);
        assertThat(lignesDu(RESULTAT)).as("aucune ligne écrite par un refus").isEqualTo(1);

        accepteeCommeExamenDetails(creation, tokenMembre)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDetail").value(RESULTAT));
        // Délégation : le CC de la localité écrit sur /examen-details, donc ici aussi.
        accepteeCommeExamenDetails(creation, tokenCc)
                .andExpect(status().isCreated());
        assertThat(lignesDu(RESULTAT)).isEqualTo(3);

        signerLePv();
        refuseeCommeExamenDetails(creation, tokenMembre, 409);
        assertThat(lignesDu(RESULTAT)).isEqualTo(3);
    }

    // ------------------------------------------------------------------ PUT

    @Test
    @DisplayName("PUT observation-controles — gardes d'examen-details : attributaire 200 ; autre Membre, CC d'une "
            + "autre localité, PRMP, Vérificateur 403 ; CC de la localité (délégation) 200 ; 409 dès PV_SIGNE")
    void put_memesGardesQueExamenDetails() throws Exception {
        Supplier<MockHttpServletRequestBuilder> modification = () -> put("/api/observation-controles/" + idLigne)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":" + RESULTAT + ",\"auLieuDe\":\"500000\",\"lire\":\"USURPE\",\"ordre\":1}");

        refuseeCommeExamenDetails(modification, tokenAutreMembre(), 403);
        refuseeCommeExamenDetails(modification, tokenCcTms(), 403);
        refuseeCommeExamenDetails(modification, tokenPrmp, 403);
        refuseeCommeExamenDetails(modification, tokenVerificateur(), 403);
        assertThat(lireDeLaLigne()).as("la ligne n'a pas bougé").isEqualTo("5000000");

        accepteeCommeExamenDetails(modification, tokenMembre)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lire").value("USURPE"));
        accepteeCommeExamenDetails(modification, tokenCc)
                .andExpect(status().isOk());

        signerLePv();
        refuseeCommeExamenDetails(modification, tokenMembre, 409);
    }

    @Test
    @DisplayName("PUT observation-controles — déplacer la ligne est une écriture sur le résultat VISÉ aussi : "
            + "vers un résultat d'une autre localité 403, vers un résultat dont le PV est signé 409")
    void put_deplacement_gardeAussiLeResultatVise() throws Exception {
        // Examen 2 : dossier 2 du socle, réceptionné en TMS — hors de la localité de CTRMEM.
        dispatchRepository.save(dispatch(2, 2, "CTRCC2", "CTRMEM"));
        examenRepository.save(examen(2, 2, "CTRMEM"));
        examenDetailRepository.save(resultat(7703, 2, PT_TEMOIN, false));
        // Examen 3 : dossier 3 (ANT) au PV signé, attribué au même Membre CTRMEM.
        dossierRepository.save(dossier(3, "PV_SIGNE"));
        receptionRepository.save(reception(3, 3, "CTRCC1", true));
        dispatchRepository.save(dispatch(3, 3, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(3, 3, "CTRMEM"));
        examenDetailRepository.save(resultat(7704, 3, PT_TEMOIN, false));

        mvc.perform(put("/api/observation-controles/" + idLigne).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":7703,\"auLieuDe\":\"a\",\"lire\":\"b\",\"ordre\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/observation-controles/" + idLigne).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":7704,\"auLieuDe\":\"a\",\"lire\":\"b\",\"ordre\":1}"))
                .andExpect(status().isConflict());
        assertThat(observationControleRepository.findById(idLigne).orElseThrow().getIdDetail())
                .as("la ligne reste sur son résultat").isEqualTo(RESULTAT);
    }

    // ------------------------------------------------------------------ DELETE

    @Test
    @DisplayName("DELETE observation-controles — gardes d'examen-details : attributaire 204 ; autre Membre, CC d'une "
            + "autre localité, PRMP, Vérificateur 403 ; CC de la localité (délégation) 204 ; 409 dès PV_SIGNE")
    void delete_memesGardesQueExamenDetails() throws Exception {
        refuseeCommeExamenDetails(() -> delete("/api/observation-controles/" + idLigne), tokenAutreMembre(), 403);
        refuseeCommeExamenDetails(() -> delete("/api/observation-controles/" + idLigne), tokenCcTms(), 403);
        refuseeCommeExamenDetails(() -> delete("/api/observation-controles/" + idLigne), tokenPrmp, 403);
        refuseeCommeExamenDetails(() -> delete("/api/observation-controles/" + idLigne), tokenVerificateur(), 403);
        assertThat(observationControleRepository.existsById(idLigne)).as("la ligne est toujours là").isTrue();

        int deuxieme = ligne(RESULTAT, "1", "2", 2);
        int troisieme = ligne(RESULTAT, "3", "4", 3);
        accepteeCommeExamenDetails(() -> delete("/api/observation-controles/" + deuxieme), tokenMembre)
                .andExpect(status().isNoContent());
        accepteeCommeExamenDetails(() -> delete("/api/observation-controles/" + troisieme), tokenCc)
                .andExpect(status().isNoContent());
        assertThat(lignesDu(RESULTAT)).isEqualTo(1);

        signerLePv();
        refuseeCommeExamenDetails(() -> delete("/api/observation-controles/" + idLigne), tokenMembre, 409);
        assertThat(observationControleRepository.existsById(idLigne)).isTrue();
    }

    // ------------------------------------------------------------------ point non conforme : au moins une ligne

    @Test
    @DisplayName("DELETE observation-controles — la dernière ligne d'un point NON CONFORME : 400 observations, même "
            + "corps d'erreur que /examen-details ; avec une autre ligne restante : 204")
    void delete_derniereLigneDUnPointNonConforme_400() throws Exception {
        MvcResult observation = mvc.perform(delete("/api/observation-controles/" + idLigne)
                .header("Authorization", tokenMembre)).andReturn();
        assertThat(observation.getResponse().getStatus()).isEqualTo(400);
        memeErreurQueExamenDetailsSansLigne(observation);
        assertThat(observationControleRepository.existsById(idLigne)).as("la ligne est toujours là").isTrue();

        ligne(RESULTAT, "1", "2", 2);
        mvc.perform(delete("/api/observation-controles/" + idLigne).header("Authorization", tokenMembre))
                .andExpect(status().isNoContent());
        assertThat(lignesDu(RESULTAT)).isEqualTo(1);
    }

    @Test
    @DisplayName("PUT observation-controles — déplacer la dernière ligne d'un point NON CONFORME vers un autre point : "
            + "400, même corps d'erreur que /examen-details ; s'il en garde une autre : 200")
    void put_deplacementDeLaDerniereLigne_400() throws Exception {
        Supplier<MockHttpServletRequestBuilder> versLeTemoin = () -> put("/api/observation-controles/" + idLigne)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":" + TEMOIN + ",\"auLieuDe\":\"500000\",\"lire\":\"5000000\",\"ordre\":1}");

        MvcResult observation = mvc.perform(versLeTemoin.get().header("Authorization", tokenMembre)).andReturn();
        assertThat(observation.getResponse().getStatus()).isEqualTo(400);
        memeErreurQueExamenDetailsSansLigne(observation);
        assertThat(observationControleRepository.findById(idLigne).orElseThrow().getIdDetail()).isEqualTo(RESULTAT);

        ligne(RESULTAT, "1", "2", 2);
        mvc.perform(versLeTemoin.get().header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDetail").value(TEMOIN));
    }

    @Test
    @DisplayName("Point repassé CONFORME par /examen-details : sa dernière ligne se supprime (204) ; repassé conforme "
            + "sans ligne, /examen-details les a déjà retirées et rien ne bloque")
    void pointRepasseConforme_derniereLigneSupprimable() throws Exception {
        // Le Membre corrige son constat : conforme, en gardant (pour l'instant) une ligne.
        mvc.perform(put("/api/examen-details/" + RESULTAT).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":" + RESULTAT + ",\"idExamen\":1,\"idPtControle\":" + PT_RESULTAT
                        + ",\"conforme\":true,\"observations\":[{\"auLieuDe\":\"500000\",\"lire\":\"5000000\","
                        + "\"ordre\":1}]}"))
                .andExpect(status().isOk());
        int ligneRestante = observationControleRepository.findByIdDetailOrderByOrdreAsc(RESULTAT).get(0)
                .getIdObservation();
        mvc.perform(delete("/api/observation-controles/" + ligneRestante).header("Authorization", tokenMembre))
                .andExpect(status().isNoContent());
        assertThat(lignesDu(RESULTAT)).isZero();

        // Repassé conforme SANS ligne : le PUT d'examen-details remplace les lignes, il n'y a plus rien à retirer.
        int autre = ligne(TEMOIN, "a", "b", 1);
        mvc.perform(put("/api/examen-details/" + TEMOIN).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":" + TEMOIN + ",\"idExamen\":1,\"idPtControle\":" + PT_TEMOIN
                        + ",\"conforme\":true,\"observations\":[]}"))
                .andExpect(status().isOk());
        assertThat(observationControleRepository.existsById(autre)).isFalse();
    }

    /**
     * Le refus reçu (400) est celui de {@code /api/examen-details} pour un point non conforme sans ligne : même
     * message, mêmes erreurs de champ. Référence : PUT du résultat, non conforme et sans observation.
     */
    private void memeErreurQueExamenDetailsSansLigne(MvcResult observation) throws Exception {
        MvcResult reference = mvc.perform(put("/api/examen-details/" + RESULTAT).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":" + RESULTAT + ",\"idExamen\":1,\"idPtControle\":" + PT_RESULTAT
                        + ",\"conforme\":false,\"observations\":[]}"))
                .andReturn();
        assertThat(reference.getResponse().getStatus()).as("référence examen-details").isEqualTo(400);
        assertThat(message(observation)).isEqualTo(message(reference));
        Object erreurs = JsonPath.read(observation.getResponse().getContentAsString(), "$.erreurs");
        Object erreursReference = JsonPath.read(reference.getResponse().getContentAsString(), "$.erreurs");
        assertThat(erreurs).as("mêmes erreurs de champ qu'examen-details").isEqualTo(erreursReference);
        assertThat(JsonPath.<String>read(observation.getResponse().getContentAsString(), "$.erreurs[0].champ"))
                .isEqualTo("observations");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * L'écriture d'observation est refusée avec {@code statut}, et l'écriture de référence sur
     * {@code /api/examen-details} avec le même jeton l'est à l'identique : même code, même message.
     */
    private void refuseeCommeExamenDetails(Supplier<MockHttpServletRequestBuilder> requete, String token, int statut)
            throws Exception {
        MvcResult observation = mvc.perform(requete.get().header("Authorization", token)).andReturn();
        MvcResult reference = ecritureDeReference(token);
        assertThat(observation.getResponse().getStatus()).as("observation-controles").isEqualTo(statut);
        assertThat(reference.getResponse().getStatus()).as("référence examen-details").isEqualTo(statut);
        assertThat(message(observation)).as("même message qu'examen-details").isEqualTo(message(reference));
    }

    /** L'écriture de référence passe (200) avec ce jeton ; renvoie le résultat de l'écriture d'observation. */
    private ResultActions accepteeCommeExamenDetails(Supplier<MockHttpServletRequestBuilder> requete, String token)
            throws Exception {
        assertThat(ecritureDeReference(token).getResponse().getStatus()).as("référence examen-details").isEqualTo(200);
        return mvc.perform(requete.get().header("Authorization", token));
    }

    /** PUT idempotent du résultat témoin, sur le même examen : la règle d'écriture d'un résultat. */
    private MvcResult ecritureDeReference(String token) throws Exception {
        return mvc.perform(put("/api/examen-details/" + TEMOIN).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":" + TEMOIN + ",\"idExamen\":1,\"idPtControle\":" + PT_TEMOIN
                        + ",\"conforme\":true,\"observations\":[]}"))
                .andReturn();
    }

    private static String message(MvcResult resultat) throws Exception {
        return JsonPath.read(resultat.getResponse().getContentAsString(), "$.message");
    }

    /** Le dossier 1 passe PV_SIGNE : l'examen devient définitif (§2.6). */
    private void signerLePv() {
        cnm.prs.entity.Dossier dossier = dossierRepository.findById(1).orElseThrow();
        dossier.setStatut("PV_SIGNE");
        dossierRepository.save(dossier);
    }

    private String tokenAutreMembre() {
        return bearer("CTRMEM2", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM2", "ANT");
    }

    private String tokenCcTms() {
        return bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
    }

    private String tokenVerificateur() {
        return bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
    }

    private long lignesDu(int idResultat) {
        return observationControleRepository.findByIdDetailOrderByOrdreAsc(idResultat).size();
    }

    private String lireDeLaLigne() {
        return observationControleRepository.findById(idLigne).orElseThrow().getLire();
    }

    private void point(int id) {
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(id);
        pc.setLibelPointCtrl("Point " + id);
        pc.setObligatoire(true);
        pc.setIdTypeDossier("DDP");
        pointsCtrlRepository.save(pc);
    }

    private static ExamenDetail resultat(int id, int idExamen, int idPoint, boolean conforme) {
        ExamenDetail d = new ExamenDetail();
        d.setIdDetailExamen(id);
        d.setIdExamen(idExamen);
        d.setIdPtControle(idPoint);
        d.setConforme(conforme);
        return d;
    }

    private int ligne(int idResultat, String auLieuDe, String lire, int ordre) {
        ObservationControle o = new ObservationControle();
        o.setIdDetail(idResultat);
        o.setAuLieuDe(auLieuDe);
        o.setLire(lire);
        o.setOrdre(ordre);
        return observationControleRepository.save(o).getIdObservation();
    }
}
