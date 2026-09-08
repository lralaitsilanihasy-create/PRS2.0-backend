package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.repository.TacheDossierRepository;

/**
 * ⚠️ <strong>La réattribution clôt l'occurrence EXAMEN du sortant</strong> (signalement pilote du
 * 2026-09-08, dossier 00305 —
 * {@code docs/demande-backend-2026-09-08-reattribution-cloture-occurrence-examen.md}).
 *
 * <p>Le Membre avait pris l'examen en charge ; le Président a redispatché au CC. Le chronométrage
 * traçait bien le geste du redispatcheur ({@code DISPATCH} n+1) mais laissait l'occurrence
 * {@code EXAMEN} du sortant <strong>ouverte à jamais</strong>. Le nouvel attributaire s'en trouvait en
 * <strong>impasse</strong> : l'étape paraissait déjà prise en charge — par quelqu'un qui n'était plus
 * là — donc aucun « Prendre en charge » ne lui était offert, et sans prise en charge il ne pouvait rien
 * faire. L'attribution, elle, était juste : c'est la vie de l'occurrence qui manquait une étape.</p>
 *
 * <p>Ce que ces tests protègent : la <strong>fermeture</strong> de l'occurrence sortante (et non sa
 * suppression — le passage a eu lieu), l'<strong>absence d'occurrence ouverte</strong> après le geste,
 * la <strong>sortie d'impasse</strong> du nouvel attributaire, et la même symétrie sur le
 * <strong>retrait</strong> du dispatch, où la purge de l'aval laissait le même orphelin.</p>
 */
class ReattributionClotureExamenIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER = 960;
    private static final int RECEPTION = 960;
    private static final int DISPATCH = 960;

    @Autowired
    private TacheDossierRepository tacheRepository;

    /** Dossier dispatché au Membre CTRMEM, qui n'a pas encore ouvert d'examen (réattribution possible). */
    @BeforeEach
    void dossierDispatcheAuMembre() throws Exception {
        dossierRepository.save(dossierLoc(DOSSIER, "PRET_DISPATCH", "ANT", "PRMP001"));
        receptionRepository.save(reception(RECEPTION, DOSSIER, "CTRCC1", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":" + DISPATCH + ",\"idReception\":" + RECEPTION
                        + ",\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
    }

    /** Le Membre attributaire ouvre son occurrence EXAMEN — le geste que la réattribution va interrompre. */
    private void membrePrendEnChargeLExamen() throws Exception {
        mvc.perform(post("/api/dossiers/" + DOSSIER + "/prise-en-charge").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionHeures\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("EXAMEN"))
                .andExpect(jsonPath("$.imActeur").value("CTRMEM"))
                .andExpect(jsonPath("$.enCours").value(true));
    }

    private List<TacheDossier> examens() {
        return tacheRepository.findByIdDossierOrderByDatePriseEnChargeAsc(DOSSIER).stream()
                .filter(t -> EtapeCircuit.EXAMEN.name().equals(t.getEtape())).toList();
    }

    // ------------------------------------------------------------------ 1. la réattribution ferme

    @Test
    @DisplayName("Recette 1 — après réattribution au CC, l'occurrence EXAMEN du Membre sortant porte une "
            + "fin ; plus aucune occurrence EXAMEN ouverte, et sa durée entamée reste mesurée")
    void reattribution_clotLOccurrenceDuSortant() throws Exception {
        membrePrendEnChargeLExamen();

        mvc.perform(put("/api/dispatchs/" + DISPATCH).header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":" + DISPATCH + ",\"idReception\":" + RECEPTION
                        + ",\"imCtrlMembre\":\"CTRCC1\",\"interimDispatch\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRCC1"));

        List<TacheDossier> examens = examens();
        assertThat(examens).as("l'occurrence du sortant est FERMÉE, pas supprimée : le passage a eu lieu")
                .hasSize(1);
        TacheDossier sortante = examens.get(0);
        assertThat(sortante.getImActeur()).isEqualTo("CTRMEM");
        assertThat(sortante.getDatePriseEnCharge()).isNotNull();
        assertThat(sortante.getDateFin()).as("la fin est posée à l'instant de la réattribution").isNotNull();
        assertThat(tacheRepository.ouvertes(DOSSIER, EtapeCircuit.EXAMEN.name())).isEmpty();
    }

    @Test
    @DisplayName("Recette 2 & 3 — le nouvel attributaire n'est plus en impasse : aucune tâche EXAMEN "
            + "ouverte au chronométrage, il prend en charge à neuf et obtient l'occurrence n° 2")
    void apresReattribution_leNouvelAttributaireSortDeLImpasse() throws Exception {
        membrePrendEnChargeLExamen();
        mvc.perform(put("/api/dispatchs/" + DISPATCH).header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":" + DISPATCH + ",\"idReception\":" + RECEPTION
                        + ",\"imCtrlMembre\":\"CTRCC1\",\"interimDispatch\":false}"))
                .andExpect(status().isOk());

        // Ce que lit le widget : l'étape est bien EXAMEN, attendue du NOUVEL attributaire, et plus
        // aucune tâche de cette étape n'est en cours — donc le bouton « Prendre en charge » s'affiche.
        mvc.perform(get("/api/dossiers/" + DOSSIER + "/chronometrage").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeCourante").value("EXAMEN"))
                .andExpect(jsonPath("$.attributaire").value("CTRCC1"))
                .andExpect(jsonPath("$.acteursAttendus", hasItem("CTRCC1")))
                .andExpect(jsonPath("$.taches[?(@.etape=='EXAMEN' && @.enCours==true)]", hasSize(0)));

        // Et le geste passe : occurrence n° 2, à SON nom, sans toucher à celle du sortant.
        mvc.perform(post("/api/dossiers/" + DOSSIER + "/prise-en-charge").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"previsionHeures\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etape").value("EXAMEN"))
                .andExpect(jsonPath("$.occurrence").value(2))
                .andExpect(jsonPath("$.imActeur").value("CTRCC1"))
                .andExpect(jsonPath("$.enCours").value(true));

        List<TacheDossier> examens = examens();
        assertThat(examens).hasSize(2);
        assertThat(examens.get(0).getImActeur()).isEqualTo("CTRMEM");
        assertThat(examens.get(0).getDateFin()).isNotNull();
        assertThat(examens.get(1).getImActeur()).isEqualTo("CTRCC1");
        assertThat(examens.get(1).getDateFin()).isNull();
    }

    // ------------------------------------------------------------------ 2. le retrait ferme aussi

    @Test
    @DisplayName("Symétrie — le RETRAIT du dispatch clôt lui aussi l'occurrence EXAMEN ouverte : la purge "
            + "de l'aval effaçait les sources sans fermer les chronomètres qui en dépendaient")
    void retraitDuDispatch_clotAussiLOccurrenceOuverte() throws Exception {
        membrePrendEnChargeLExamen();
        dossierEnStatut("DISPATCHE");

        mvc.perform(post("/api/dispatchs/" + DISPATCH + "/annuler").header("Authorization", tokenPresident))
                .andExpect(status().isNoContent());

        assertThat(tacheRepository.ouvertes(DOSSIER, EtapeCircuit.EXAMEN.name()))
                .as("plus aucune occurrence EXAMEN ouverte après le retrait").isEmpty();
        List<TacheDossier> examens = examens();
        assertThat(examens).hasSize(1);
        assertThat(examens.get(0).getDateFin()).isNotNull();
        assertThat(examens.get(0).getImActeur()).isEqualTo("CTRMEM");
    }

    /** Le retrait exige un dossier DISPATCHE ou EXAMINE ; la prise en charge, elle, ne change pas le statut. */
    private void dossierEnStatut(String statut) {
        dossierRepository.findById(DOSSIER).ifPresent(d -> {
            d.setStatut(statut);
            dossierRepository.save(d);
        });
    }
}
