package cnm.prs;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.entity.Dossier;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ VISA PAR INTÉRIM (arbitrage du pilote, 2026-09-01) — extension de la contrainte du dispatcheur.
 *
 * <p>Depuis le 2026-08-31, seul le dispatcheur vise (403 sinon, délégation active comprise). Le pilote
 * ouvre une exception <em>justifiée</em> : un autre P/CC vise en joignant une <strong>note d'intérim</strong>
 * (PDF) qui matérialise l'absence. Sans levée de la garde de localité — contrairement à
 * {@code INTERIM_DISPATCH} au dispatch : suppléer n'étend pas le ressort.</p>
 *
 * <p><strong>Aucune vérification de l'absence réelle</strong> : le serveur ne peut pas la constater, et
 * une garde invérifiable donnerait l'illusion du contrôle. La note EST la justification, tracée sous la
 * responsabilité du signataire. Un dispatcheur présent peut donc recevoir un visa d'intérim.</p>
 */
class PvVisaInterimIntegrationTest extends CnmIntegrationTestSupport {

    /** Projet de PV soumis sur l'examen 1 (dossier 1, localité ANT = CENTRALE, dispatcheur CTRPRE). */
    private void projetSoumisCentral(int idPv) throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + idPv + ",\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Intérim — un P/CC non dispatcheur SANS note : 400 « note d'intérim requise », pas 403")
    void interim_sansNote_400() throws Exception {
        projetSoumisCentral(9501);
        // Le CC n'est pas le dispatcheur (CTRPRE l'est) : il PEUT suppléer, il lui manque la pièce.
        // D'où 400 et non 403 — le front affiche « joignez la note », pas « vous n'avez pas le droit ».
        viserParInterim(9501, tokenCc, "CTRCC1", "FAV", "CTRVER", "CTRMEM", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Note d'intérim requise")));
        mvc.perform(get("/api/pv-examens/9501").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"))
                .andExpect(jsonPath("$.viseParInterim").value(false));
    }

    @Test
    @DisplayName("Intérim — note d'un type non autorisé : 400 (le type est lu sur les OCTETS, pas sur le nom)")
    void interim_noteNonPdf_400() throws Exception {
        projetSoumisCentral(9502);
        // Nom et Content-Type annoncent un PDF ; le contenu n'en est pas un. Seuls les octets font foi.
        viserParInterim(9502, tokenCc, "CTRCC1", "FAV", "CTRVER", "CTRMEM",
                "ceci n'est pas un PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8), "note.pdf")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("PDF")));
    }

    @Test
    @DisplayName("Intérim — note fournie : le visa passe, viseParInterim=true, note téléchargeable")
    void interim_avecNote_ok() throws Exception {
        projetSoumisCentral(9503);
        viserParInterim(9503, tokenCc, "CTRCC1", "FAV", "CTRVER", "CTRMEM", pdfMinimal(), "absence-ctrpre.pdf")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"))
                .andExpect(jsonPath("$.viseParInterim").value(true))
                .andExpect(jsonPath("$.noteInterimNom").value("absence-ctrpre.pdf"))
                .andExpect(jsonPath("$.noteInterimDisponible").value(true))
                // La part signée reste dérivée du profil de l'acteur : le CC signe la part CC.
                .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"))
                // Le dispatcheur reste CTRPRE : l'intérim ne le remplace pas, il le supplée.
                .andExpect(jsonPath("$.imDispatcheur").value("CTRPRE"));
        mvc.perform(get("/api/pv-examens/9503/note-interim").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Intérim — la garde de localité TIENT : un CC d'une autre localité est refusé (403) malgré la note")
    void interim_ccHorsLocalite_403() throws Exception {
        projetSoumisCentral(9504);
        // ⚠️ Contrairement à INTERIM_DISPATCH au dispatch, l'intérim au visa ne lève PAS la localité
        // (arbitrage 3, 2026-09-01). Un CC de TMS ne supplée pas sur un dossier d'ANT, note ou pas.
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        viserParInterim(9504, tokenCcTms, "CTRCC2", "FAV", "CTRVER", "CTRMEM", pdfMinimal(), "note.pdf")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Intérim — profil hors P/CC : 403 même avec la note (la note ne crée pas l'habilitation)")
    void interim_horsPCC_403() throws Exception {
        projetSoumisCentral(9505);
        // Le Secrétaire a la paire → Membre mais n'est ni Président ni CC : le profil est vérifié AVANT
        // la note, pour qu'une pièce ne puisse jamais tenir lieu d'habilitation.
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        viserParInterim(9505, tokenSec, "CTRSEC", "FAV", "CTRVER", "CTRMEM", pdfMinimal(), "note.pdf")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Intérim — le chemin NORMAL est intact : le dispatcheur vise en JSON, sans note, viseParInterim=false")
    void cheminNormal_intact() throws Exception {
        projetSoumisCentral(9506);
        viser(9506, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viseParInterim").value(false))
                .andExpect(jsonPath("$.noteInterimDisponible").value(false));
        // Pas de note à télécharger sur un visa normal.
        mvc.perform(get("/api/pv-examens/9506/note-interim").header("Authorization", tokenPresident))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Intérim — un DISPATCHEUR qui joint une note obtient le visa normal : la note est ignorée "
            + "(l'absence réelle n'est pas vérifiable, décision du 01/09)")
    void dispatcheurAvecNote_visaNormal() throws Exception {
        projetSoumisCentral(9507);
        viserParInterim(9507, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM", pdfMinimal(), "inutile.pdf")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viseParInterim").value(false))
                .andExpect(jsonPath("$.noteInterimDisponible").value(false));
    }

    @Test
    @DisplayName("Intérim — la note est FERMÉE à la PRMP (403) : document interne, elle ne fait pas partie "
            + "de la décision notifiée")
    void note_fermeeALaPrmp() throws Exception {
        projetSoumisCentral(9508);
        viserParInterim(9508, tokenCc, "CTRCC1", "FAV", "CTRVER", "CTRMEM", pdfMinimal(), "note.pdf")
                .andExpect(status().isOk());
        // La PRMP voit les PV signés de ses dossiers ; la note, elle, lui est refusée. Sans quoi
        // l'arbitrage 4 — qui retire la mention d'intérim du PV central pour que l'extérieur ne
        // l'apprenne pas — serait contourné par une autre porte.
        mvc.perform(get("/api/pv-examens/9508/note-interim").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Document — CENTRALE visé par intérim : la ligne du viseur est là, SANS mention (R1 + R2)")
    void document_centrale_ligneSansMention() throws Exception {
        projetSoumisCentral(9509);
        viserParInterim(9509, tokenCc, "CTRCC1", "FAV", "CTRVER", "CTRMEM", pdfMinimal(), "note.pdf")
                .andExpect(status().isOk());
        cnm.prs.entity.PvExamen pv = pvExamenRepository.findById(9509).orElseThrow();
        assertTrue(Boolean.TRUE.equals(pv.getViseParInterim()), "le PV est bien visé par intérim");
        var ctx = contexte(9509);
        // R1 : la ligne nomme le viseur sur TOUS les PV — ici le CC, qui a posé la part CC.
        assertTrue(ctx.ligneViseur() != null && ctx.ligneViseur().startsWith("Visé par : "),
                "la ligne du viseur est présente même en Centrale : " + ctx.ligneViseur());
        assertTrue(ctx.ligneViseur().contains("Chef de la Commission"),
                "la qualité du viseur est celle du bloc « Étaient présents »");
        // R2 : rien ne révèle l'intérim en Centrale — l'arbitrage 4 du 01/09 est préservé.
        assertFalse(ctx.ligneViseur().contains("par intérim"),
                "aucune mention d'intérim en Centrale : " + ctx.ligneViseur());
    }

    @Test
    @DisplayName("Document — visa NORMAL : la ligne du viseur est présente et sans mention (R1)")
    void document_visaNormal_ligneSansMention() throws Exception {
        projetSoumisCentral(9510);
        viser(9510, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM").andExpect(status().isOk());
        var ctx = contexte(9510);
        assertTrue(ctx.ligneViseur() != null
                && ctx.ligneViseur().contains("Président de la Commission Nationale des Marchés"),
                "le visa normal nomme aussi son viseur : " + ctx.ligneViseur());
        assertFalse(ctx.ligneViseur().contains("par intérim"), "un visa normal ne porte pas la mention");
    }

    @Test
    @DisplayName("Document — mention « par intérim » PRÉSENTE sur un PV de localité RÉGIONALE (arbitrage 4)")
    void document_regionale_avecMention() throws Exception {
        // Chaîne complète sur une localité NON centrale (TMS), dispatcheur CTRPRE, CC de TMS = CTRCC2.
        // La fixture ne porte qu'un contrôleur de TMS (le CC) : le Membre et le Vérificateur manquent.
        controleurRepository.save(controleur("CTRMEM2", 5, "TMS"));
        controleurRepository.save(controleur("CTRVER2", 6, "TMS"));
        Dossier d = dossierLoc(9600, "EXAMINE", "TMS", "PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(9600, 9600, "PRMP001"));
        marcheRepository.save(marche(96000, 9600, 9600));
        receptionRepository.save(reception(9600, 9600, "CTRCC2", true));
        dispatchRepository.save(dispatch(9600, 9600, "CTRCC2", "CTRMEM2", "CTRPRE"));
        examenRepository.save(examen(9600, 9600, "CTRMEM2"));
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(9601); pv.setIdExamen(9600); pv.setIdAvis("FAV"); pv.setImCtrlMembre("CTRMEM2");
        pv.setStatutPv("PROJET_SOUMIS"); pv.setNbNavettes(0);
        pvExamenRepository.save(pv);

        // Le CC de TMS supplée le dispatcheur CTRPRE, sur un dossier de SA localité : autorisé avec note.
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        viserParInterim(9601, tokenCcTms, "CTRCC2", "FAV", "CTRVER2", "CTRMEM2", pdfMinimal(), "note.pdf")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viseParInterim").value(true));
        var ctx = contexte(9601);
        // La mention n'existe QUE là, et QUE dans ce cas : régional × intérim.
        assertTrue(ctx.ligneViseur() != null && ctx.ligneViseur().endsWith(" — par intérim"),
                "la ligne du viseur porte la mention hors localité centrale : " + ctx.ligneViseur());
        // ⚠️ R3 (2026-09-01) — la mention a DÉMÉNAGÉ : « Étaient présents » n'en porte plus trace, même
        // ici. Ce test vérifie le retrait autant que l'ajout ; sans lui, on aurait pu livrer les deux.
        assertFalse(presents(9601).contains("par intérim"),
                "le bloc « Étaient présents » est redevenu une simple liste : " + presents(9601));
    }

    /**
     * Contexte du document tel qu'il sera imprimé.
     *
     * <p>On interroge le CONTEXTE et non le PDF : produire le PDF pilote Word, ce qui rangerait ces
     * tests dans le groupe {@code word} exclu de la CI Linux. Une règle métier qui ne s'observe qu'avec
     * Word installé n'est pas une règle testée.</p>
     */
    private cnm.prs.service.PvDocumentContexte contexte(int idPv) {
        return pvDocumentService.contexte(pvExamenRepository.findById(idPv).orElseThrow());
    }

    /**
     * Noms du bloc « Étaient présents », à plat — sert à vérifier que la mention d'intérim n'y est PLUS.
     *
     * <p>⚠️ Le Secrétaire de séance a été retiré du bloc (règle du pilote, 2026-09-02) : il ne reste que
     * le Président, le Chef de commission et le Membre.</p>
     */
    private String presents(int idPv) {
        var ctx = contexte(idPv);
        return String.valueOf(ctx.nomPresident()) + " | " + String.valueOf(ctx.nomChefCommission())
                + " | " + String.valueOf(ctx.nomMembre());
    }
}
