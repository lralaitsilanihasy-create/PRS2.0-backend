package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.repository.ActionDossierRepository;
import cnm.prs.repository.TacheDossierRepository;
import cnm.prs.service.JournalDossierService;

/**
 * ⚠️ <strong>La réattribution laisse sa trace au chronométrage</strong> (règle du pilote, 2026-09-04 —
 * vérification du tableau « Chronométrage &amp; délais » sur le dossier 100286).
 *
 * <p><strong>L'asymétrie constatée.</strong> Sur un dossier Président → CC → Membre, le journal du
 * circuit portait les deux gestes — {@code DISPATCH} du Président, puis {@code REATTRIBUTION} du CC —
 * mais le chronométrage n'avait qu'une tâche {@code DISPATCH#1}. Le passage par le CC n'existait nulle
 * part dans la table des passages, alors qu'un retrait suivi d'un re-dispatch, lui, en produisait bien
 * une seconde. Le chemin réel doit se lire aux deux endroits, avec les mêmes acteurs.</p>
 *
 * <p>Le test 3 est celui qui compte : il confronte les deux tables plutôt que de vérifier chacune dans
 * son coin — c'est leur ACCORD qui était rompu, pas le contenu de l'une ou de l'autre.</p>
 */
class ChronoReattributionIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private TacheDossierRepository tacheDossierRepository;

    @Autowired
    private ActionDossierRepository actionDossierRepository;

    /** Dossier central PRÊT_DISPATCH avec sa réception complète — le point de départ du circuit. */
    private void dossierPretADispatcher(int id) {
        Dossier d = dossier(id, "PRET_DISPATCH");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        receptionRepository.save(reception(id, id, "CTRCC1", true));
    }

    private String corps(int idDispatch, int idReception, String membre) {
        return "{\"idDispatch\":" + idDispatch + ",\"idReception\":" + idReception
                + ",\"imCtrlMembre\":\"" + membre + "\",\"interimDispatch\":false}";
    }

    private List<TacheDossier> dispatchs(int idDossier) {
        return tacheDossierRepository.findByIdDossierOrderByDatePriseEnChargeAsc(idDossier).stream()
                .filter(t -> EtapeCircuit.DISPATCH.name().equals(t.getEtape()))
                .sorted(java.util.Comparator.comparing(TacheDossier::getOccurrence)).toList();
    }

    private List<ActionDossier> journal(int idDossier) {
        return actionDossierRepository.findByIdDossierOrderByDateActionAscIdActionAsc(idDossier);
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("1 — Président → CC puis CC → Membre : DISPATCH#1 (P) et DISPATCH#2 (CC), closes "
            + "instantanément, prévision standard")
    void reattribution_ouvreUneOccurrenceAuNomDuReattribueur() throws Exception {
        dossierPretADispatcher(9901);

        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps(9901, 9901, "CTRCC1")))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/dispatchs/9901").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corps(9901, 9901, "CTRMEM")))
                .andExpect(status().isOk());

        List<TacheDossier> taches = dispatchs(9901);
        Assertions.assertEquals(2, taches.size(),
                "le geste du CC doit exister au chronométrage, pas seulement au journal");

        TacheDossier premier = taches.get(0);
        TacheDossier second = taches.get(1);
        Assertions.assertEquals(1, premier.getOccurrence());
        Assertions.assertEquals("CTRPRE", premier.getImActeur(), "la première est celle du Président");
        Assertions.assertEquals(2, second.getOccurrence());
        Assertions.assertEquals("CTRCC1", second.getImActeur(), "la seconde porte l'auteur du geste");

        // Instantanées : un acte ponctuel n'a pas de durée à mesurer.
        for (TacheDossier t : taches) {
            Assertions.assertNotNull(t.getDateFin(), "un geste instantané est clos d'emblée");
            Assertions.assertEquals(t.getDatePriseEnCharge(), t.getDateFin(),
                    "prise en charge et fin au même horodatage");
            Assertions.assertTrue(Boolean.TRUE.equals(t.getPrevisionStandard()),
                    "personne n'a saisi de prévision : c'est le référentiel qui la donne");
            Assertions.assertNotNull(t.getPrevisionHeures());
        }
    }

    @Test
    @DisplayName("2 — Dispatch DIRECT, sans réattribution : une seule occurrence (anti-régression)")
    void dispatchDirect_uneSeuleOccurrence() throws Exception {
        dossierPretADispatcher(9902);

        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps(9902, 9902, "CTRMEM")))
                .andExpect(status().isCreated());

        Assertions.assertEquals(1, dispatchs(9902).size(),
                "sans changement d'attributaire, rien ne s'ajoute");
        Assertions.assertEquals("CTRPRE", dispatchs(9902).get(0).getImActeur());
    }

    @Test
    @DisplayName("3 — ⚠️ Le journal et le chronométrage racontent le MÊME chemin : mêmes acteurs, même ordre")
    void journalEtChronometrage_memeChemin() throws Exception {
        dossierPretADispatcher(9903);

        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps(9903, 9903, "CTRCC1")))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/dispatchs/9903").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corps(9903, 9903, "CTRMEM")))
                .andExpect(status().isOk());

        // Le journal : DISPATCH par le Président, puis REATTRIBUTION par le CC.
        List<String> auteursJournal = journal(9903).stream()
                .filter(a -> JournalDossierService.DISPATCH.equals(a.getTypeAction())
                        || JournalDossierService.REATTRIBUTION.equals(a.getTypeAction()))
                .map(ActionDossier::getAuteur).toList();
        Assertions.assertEquals(List.of("CTRPRE", "CTRCC1"), auteursJournal);

        // Le chronométrage : les MÊMES acteurs, dans le MÊME ordre. C'est cet accord qui manquait —
        // le chemin réel se lisait au journal et pas au tableau des passages.
        List<String> auteursChrono = dispatchs(9903).stream().map(TacheDossier::getImActeur).toList();
        Assertions.assertEquals(auteursJournal, auteursChrono,
                "les deux tables doivent nommer les mêmes acteurs dans le même ordre");

        // Et l'étape courante ne bouge pas : le dossier reste à examiner, la réattribution n'est pas
        // un retour en arrière du circuit.
        mvc.perform(get("/api/dossiers/9903/chronometrage").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.etapeCourante").value("EXAMEN"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.attributaire").value("CTRMEM"));
    }

    @Test
    @DisplayName("3 bis — La REPRISE compte aussi : le CC qui se remet le dossier laisse sa ligne")
    void reprise_compteCommeUnGeste() throws Exception {
        dossierPretADispatcher(9904);

        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps(9904, 9904, "CTRCC1")))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/dispatchs/9904").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corps(9904, 9904, "CTRMEM")))
                .andExpect(status().isOk());
        // Le « Retirer » du CC est un PUT vers LUI-MÊME : c'est un changement d'attributaire, donc un
        // geste — le même appel le couvre, sans règle supplémentaire.
        mvc.perform(put("/api/dispatchs/9904").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corps(9904, 9904, "CTRCC1")))
                .andExpect(status().isOk());

        List<TacheDossier> taches = dispatchs(9904);
        Assertions.assertEquals(3, taches.size(), "dispatch, réattribution, reprise : trois gestes");
        Assertions.assertEquals(List.of("CTRPRE", "CTRCC1", "CTRCC1"),
                taches.stream().map(TacheDossier::getImActeur).toList());
    }
}
