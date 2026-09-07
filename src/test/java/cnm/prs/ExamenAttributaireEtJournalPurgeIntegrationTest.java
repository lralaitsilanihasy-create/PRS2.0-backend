package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.repository.PvNavetteRepository;
import cnm.prs.repository.TacheDossierRepository;
import cnm.prs.service.JournalDossierService;
import cnm.prs.service.JournalTraitementService;

/**
 * ⚠️ <strong>Signalement pilote du 2026-09-07 (dossier réel 00001)</strong> — deux constats après le
 * parcours dispatch → examen → visa → retour → re-soumission par le Président → retrait du dispatch :
 * <ol>
 *   <li>le chronométrage montrait une tâche <em>EXAMEN#2</em> au nom de {@code PRES001}, instantanée, à
 *       prévision standard : la re-soumission du projet faite <em>pour</em> le Membre créait l'occurrence au
 *       nom du déclencheur ;</li>
 *   <li>le journal oubliait l'examen après le retrait : ses événements étaient dérivés des navettes et du
 *       PV, que la purge du circuit efface.</li>
 * </ol>
 *
 * <p>Décor : le dossier 1 du socle ({@code EXAMINE}, réception 1, dispatch 1 CTRCC1 → CTRMEM, examen 1),
 * dispatcheur {@code CTRPRE}. La délégation Président → Membre du socle autorise le Président à soumettre
 * le projet à la place du Membre — c'est exactement le geste qui a produit l'examen fantôme.</p>
 */
class ExamenAttributaireEtJournalPurgeIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private TacheDossierRepository tacheRepository;
    @Autowired
    private PvNavetteRepository pvNavetteRepository;

    // ------------------------------------------------------------------ constat 1 : examen fantôme

    @Test
    @DisplayName("Test attendu n° 1 — après un retour au Membre, la re-soumission par le PRÉSIDENT ne crée aucune "
            + "tâche EXAMEN à son nom : la seule tâche EXAMEN reste celle de l'attributaire")
    void resoumissionParLePresident_aucuneTacheExamenAuNomDuPresident() throws Exception {
        creerProjet(90);
        soumettre(90, tokenMembre);   // le Membre soumet sans prise en charge → occurrence instantanée à SON nom
        List<TacheDossier> apresMembre = tachesExamen();
        assertThat(apresMembre).hasSize(1);
        assertThat(apresMembre.get(0).getImActeur()).isEqualTo("CTRMEM");

        retourner(90, tokenPresident);
        soumettre(90, tokenPresident);   // re-soumission POUR le Membre (délégation Président → Membre)

        List<TacheDossier> apresPresident = tachesExamen();
        assertThat(apresPresident).extracting(TacheDossier::getImActeur).doesNotContain("CTRPRE");
        assertThat(apresPresident).as("aucune occurrence créée par la transition du Président").hasSize(1);
        mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taches[?(@.etape=='EXAMEN' && @.imActeur=='CTRPRE')]", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("Test attendu n° 2 — aucune tâche EXAMEN instantanée (prise en charge = fin) née d'une transition du "
            + "Président ; la tâche que l'attributaire a prise en charge est bien celle qui se clôt à sa re-soumission")
    void tacheExamen_priseEnChargeParLAttributaire_closeParSaResoumission() throws Exception {
        creerProjet(91);
        soumettre(91, tokenMembre);
        retourner(91, tokenPresident);
        // Le Membre prend en charge la reprise de son examen : une occurrence OUVERTE, à son nom, prévision réelle.
        mvc.perform(post("/api/dossiers/1/prise-en-charge").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionHeures\":6}"))
                .andExpect(status().isOk());
        assertThat(tacheRepository.ouvertes(1, EtapeCircuit.EXAMEN.name())).hasSize(1);

        // Le Président retourne encore et re-soumet à sa place : rien d'instantané ne naît, et la tâche
        // ouverte du Membre est celle qui se clôt.
        soumettre(91, tokenPresident);
        List<TacheDossier> taches = tachesExamen();
        assertThat(taches).hasSize(2);
        assertThat(taches).extracting(TacheDossier::getImActeur).containsOnly("CTRMEM");
        assertThat(taches).allMatch(t -> t.getDateFin() != null, "toutes closes");
        TacheDossier reprise = taches.stream().filter(t -> !Boolean.TRUE.equals(t.getPrevisionStandard())).findFirst().orElseThrow();
        assertThat(reprise.getPrevisionHeures()).isEqualTo(6);
        assertThat(reprise.getDateFin()).isAfterOrEqualTo(reprise.getDatePriseEnCharge());
        assertThat(taches).noneMatch(t -> "CTRPRE".equals(t.getImActeur())
                && t.getDateFin() != null && t.getDateFin().equals(t.getDatePriseEnCharge()));
    }

    // ------------------------------------------------------------------ constat 2 : journal après purge

    @Test
    @DisplayName("Journal — l'examen SURVIT à l'annulation du dispatch : soumission d'examen et retour restent au "
            + "journal alors que navettes et PV sont purgés ; rien n'est écrit en double avant la purge")
    void journal_examenSurvitALAnnulationDuDispatch() throws Exception {
        creerProjet(92);
        soumettre(92, tokenMembre);
        retourner(92, tokenPresident);

        // Avant la purge : événements dérivés, une seule occurrence chacun (aucune copie écrite en double).
        List<String> avant = types();
        assertThat(avant).containsOnlyOnce(JournalTraitementService.SOUMISSION_EXAMEN, JournalTraitementService.RETOUR_RECTIFICATION);
        assertThat(pvNavetteRepository.findAll()).anyMatch(n -> n.getIdPv().equals(92));

        // Le dispatcheur (Président) annule le dispatch : purge de l'aval (examen, PV, navettes).
        mvc.perform(post("/api/dispatchs/1/annuler").header("Authorization", tokenPresident))
                .andExpect(status().isNoContent());
        assertThat(pvExamenRepository.existsById(92)).isFalse();
        assertThat(pvNavetteRepository.findAll()).noneMatch(n -> n.getIdPv().equals(92));

        // Après la purge : les événements sont là, figés, une fois chacun, avec l'opérateur et le retrait.
        String journal = mvc.perform(get("/api/dossiers/1/journal").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> apres = JsonPath.read(journal, "$[*].typeAction");
        assertThat(apres).containsOnlyOnce(JournalTraitementService.SOUMISSION_EXAMEN,
                JournalTraitementService.RETOUR_RECTIFICATION, JournalDossierService.RETRAIT_DISPATCH);
        List<String> auteurs = JsonPath.read(journal, "$[?(@.typeAction=='SOUMISSION_EXAMEN')].auteur");
        assertThat(auteurs).containsExactly("CTRMEM");
        List<String> details = JsonPath.read(journal, "$[?(@.typeAction=='RETOUR_RECTIFICATION')].detail");
        assertThat(details.get(0)).contains("à revoir");
        // Les copies figées sont de vraies lignes : elles portent un identifiant, contrairement aux dérivés.
        List<Object> ids = JsonPath.read(journal, "$[?(@.typeAction=='SOUMISSION_EXAMEN')].idAction");
        assertThat(ids.get(0)).isNotNull();
    }

    // ------------------------------------------------------------------ helpers

    private void creerProjet(int idPv) throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + idPv + ",\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
    }

    private void soumettre(int idPv, String token) throws Exception {
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"prêt\"}"))
                .andExpect(status().isOk());
    }

    private void retourner(int idPv, String token) throws Exception {
        mvc.perform(post("/api/pv-examens/" + idPv + "/retourner").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"à revoir\"}"))
                .andExpect(status().isOk());
    }

    private List<TacheDossier> tachesExamen() {
        return tacheRepository.findByIdDossierOrderByDatePriseEnChargeAsc(1).stream()
                .filter(t -> EtapeCircuit.EXAMEN.name().equals(t.getEtape())).toList();
    }

    private List<String> types() throws Exception {
        String resp = mvc.perform(get("/api/dossiers/1/journal").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(resp, "$[*].typeAction");
    }
}
