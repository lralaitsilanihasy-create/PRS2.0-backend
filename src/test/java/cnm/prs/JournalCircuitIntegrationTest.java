package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import cnm.prs.entity.ActionDossier;
import cnm.prs.entity.Dossier;
import cnm.prs.repository.ActionDossierRepository;
import cnm.prs.service.JournalDossierService;

/**
 * ⚠️ <strong>Journal du circuit</strong> (règle du pilote, 2026-09-04) — « Est-ce qu'on peut faire
 * apparaître les réattributions du CC et le retrait, c'est-à-dire toutes les étapes que le dossier a
 * fait ? »
 *
 * <p>Le chronométrage journalise les <em>durées</em>, mais le dispatch ne garde que son <strong>dernier
 * état</strong> : une réattribution écrase l'attributaire, un retrait supprime la ligne. Sans ces
 * traces, l'histoire du dossier est irrécupérable. C'est exactement ce que le test 4 vérifie — la
 * ligne de retrait <strong>survit</strong> à la disparition du dispatch qu'elle décrit.</p>
 */
class JournalCircuitIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private ActionDossierRepository actionDossierRepository;

    private int preparerDossierCentral(int id) {
        Dossier d = dossier(id, "PRET_DISPATCH");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        receptionRepository.save(reception(id, id, "CTRCC1", true));
        return id;
    }

    private String corps(int id, int rec, String membre) {
        return corps(id, rec, membre, null);
    }

    /** Corps de dispatch, avec une consigne facultative — c’est elle que le journal doit retenir. */
    private String corps(int id, int rec, String membre, String instructions) {
        return "{\"idDispatch\":" + id + ",\"idReception\":" + rec
                + ",\"imCtrlMembre\":\"" + membre + "\",\"interimDispatch\":false"
                + (instructions == null ? "" : ",\"instructions\":\"" + instructions + "\"") + "}";
    }

    private List<ActionDossier> journal(int idDossier, String type) {
        return actionDossierRepository.findByIdDossierOrderByDateActionAscIdActionAsc(idDossier).stream()
                .filter(a -> type.equals(a.getTypeAction())).toList();
    }

    @Test
    @DisplayName("1 — Le Président dispatche au CC AVEC consigne : ligne DISPATCH, opérateur Président, "
            + "SANS marqueur PRMP, consigne incluse au détail")
    void dispatch_tracé() throws Exception {
        int rec = preparerDossierCentral(7201);
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps(7201, rec, "CTRCC1", "Vérifier les seuils avant examen")))
                .andExpect(status().isCreated());

        List<ActionDossier> lignes = journal(7201, JournalDossierService.DISPATCH);
        Assertions.assertEquals(1, lignes.size(), "le dispatch doit laisser une trace");
        ActionDossier a = lignes.get(0);
        Assertions.assertTrue(a.getDetail() != null && a.getDetail().startsWith("à "),
                "le détail doit nommer l'attributaire : " + a.getDetail());
        // ⚠️ Complément du 2026-09-04 — le dispatch ne garde que la DERNIÈRE consigne : celle du
        // Président au CC disparaîtrait à la réattribution. Consignée ici, elle devient définitive.
        Assertions.assertTrue(a.getDetail().contains("consigne : « Vérifier les seuils avant examen »"),
                "la consigne doit figurer au détail : " + a.getDetail());
        Assertions.assertEquals("CTRPRE", a.getAuteur(), "l'auteur est le login de l'acteur");
        // ⚠️ Le marqueur « opérateur ≠ attributaire » du front s'allume sur idPrmpOperateur : le
        // renseigner avec un matricule de contrôleur serait un contresens.
        Assertions.assertNull(a.getIdPrmpOperateur(), "pas de PRMP opératrice pour un geste de contrôleur");
        Assertions.assertNull(a.getIdMandatOperateur(), "pas de mandat non plus");
        Assertions.assertNotNull(a.getNomOperateur(), "le nom du contrôleur doit être résolu");
    }

    @Test
    @DisplayName("1 bis — Sans consigne, le détail n'affiche aucune rubrique creuse")
    void dispatch_sansConsigne() throws Exception {
        int rec = preparerDossierCentral(7206);
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps(7206, rec, "CTRCC1")))
                .andExpect(status().isCreated());
        Assertions.assertFalse(journal(7206, JournalDossierService.DISPATCH).get(0).getDetail()
                .contains("consigne"), "aucune mention de consigne quand il n'y en a pas");
    }

    @Test
    @DisplayName("2 — Le CC réattribue à un Membre : REATTRIBUTION « de … à … » avec SA consigne (la nouvelle)")
    void reattribution_tracee() throws Exception {
        int rec = preparerDossierCentral(7202);
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps(7202, rec, "CTRCC1", "consigne du Président")))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/dispatchs/7202").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps(7202, rec, "CTRMEM", "consigne du CC")))
                .andExpect(status().isOk());

        List<ActionDossier> lignes = journal(7202, JournalDossierService.REATTRIBUTION);
        Assertions.assertEquals(1, lignes.size());
        String detail = lignes.get(0).getDetail();
        Assertions.assertTrue(detail != null && detail.startsWith("de ") && detail.contains(" à "),
                "le détail doit nommer l'ancien ET le nouveau : " + detail);
        // C'est la consigne du RÉATTRIBUEUR qui compte ici — celle du Président reste sur SA ligne.
        Assertions.assertTrue(detail.contains("consigne : « consigne du CC »"), detail);
        Assertions.assertTrue(journal(7202, JournalDossierService.DISPATCH).get(0).getDetail()
                .contains("consigne : « consigne du Président »"),
                "la consigne initiale survit sur la ligne de dispatch — c'est tout l'enjeu");
    }

    @Test
    @DisplayName("3 — Le CC reprend le dossier au Membre : ligne REPRISE, et non REATTRIBUTION")
    void reprise_tracee() throws Exception {
        int rec = preparerDossierCentral(7203);
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps(7203, rec, "CTRCC1")))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/dispatchs/7203").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corps(7203, rec, "CTRMEM")))
                .andExpect(status().isOk());
        // Le « Retirer » du CC est un PUT vers LUI-MÊME (le dossier revient dans SA file), pas une
        // annulation : c'est la distinction que le pilote voulait voir au journal.
        mvc.perform(put("/api/dispatchs/7203").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corps(7203, rec, "CTRCC1")))
                .andExpect(status().isOk());

        Assertions.assertEquals(1, journal(7203, JournalDossierService.REPRISE).size(),
                "une reprise doit être tracée comme telle");
        String detail = journal(7203, JournalDossierService.REPRISE).get(0).getDetail();
        Assertions.assertTrue(detail != null && detail.startsWith("reprise à "), detail);
        // La réattribution précédente reste, elle : le journal est append-only.
        Assertions.assertEquals(1, journal(7203, JournalDossierService.REATTRIBUTION).size());
    }

    @Test
    @DisplayName("4 — Retrait par le Président : la ligne SURVIT à la suppression du dispatch et au re-dispatch")
    void retrait_traceSurvitAuDispatch() throws Exception {
        int rec = preparerDossierCentral(7204);
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps(7204, rec, "CTRMEM")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/dispatchs/7204/annuler").header("Authorization", tokenPresident))
                .andExpect(status().isNoContent());

        // Le dispatch a disparu ; la trace, non — c'est tout l'intérêt d'un journal append-only.
        Assertions.assertFalse(dispatchRepository.existsById(7204), "le dispatch est supprimé");
        Assertions.assertEquals(1, journal(7204, JournalDossierService.RETRAIT_DISPATCH).size());

        // Re-dispatch : l'histoire s'accumule, elle ne se remplace pas.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps(7205, rec, "CTRMEM")))
                .andExpect(status().isCreated());
        Assertions.assertEquals(1, journal(7204, JournalDossierService.RETRAIT_DISPATCH).size());
        Assertions.assertEquals(2, journal(7204, JournalDossierService.DISPATCH).size(),
                "les deux dispatchs successifs doivent tous deux figurer");
    }

    @Test
    @DisplayName("5 — Le journal PRMP existant est inchangé, et la purge d'un brouillon purge toujours tout")
    void journalPrmp_inchange() throws Exception {
        // Un brouillon PRMP porte sa trace de CRÉATION comme avant.
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idDossier = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        Assertions.assertEquals(1, journal(idDossier, JournalDossierService.CREATION).size());
        // L'opérateur PRMP garde SES champs : la variante contrôleur ne les a pas neutralisés.
        Assertions.assertEquals("PRMP001",
                journal(idDossier, JournalDossierService.CREATION).get(0).getIdPrmpOperateur());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/dossiers/" + idDossier).header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        Assertions.assertTrue(
                actionDossierRepository.findByIdDossierOrderByDateActionAscIdActionAsc(idDossier).isEmpty(),
                "la purge d'un brouillon emporte son journal");
    }
}
