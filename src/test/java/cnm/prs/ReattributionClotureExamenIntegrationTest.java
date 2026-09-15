package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
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
 * ⚠️ <strong>La réattribution clôt l'examen du sortant</strong> (signalement pilote du 2026-09-08,
 * dossier 00305 — {@code docs/demande-backend-2026-09-08-reattribution-cloture-occurrence-examen.md}).
 *
 * <p>À l'origine, le chronométrage traçait bien le geste du redispatcheur ({@code DISPATCH} n+1) mais
 * laissait l'occurrence {@code EXAMEN} du sortant <strong>ouverte à jamais</strong>, mettant le nouvel
 * attributaire en impasse — l'étape paraissait prise en charge par quelqu'un qui n'était plus là.</p>
 *
 * <p>⚠️ <strong>2026-09-12</strong> — l'impasse a disparu avec la prise en charge elle-même. Ce qui
 * <em>reste</em>, et qui compte désormais davantage : la <strong>mesure</strong>. Le délai d'une étape
 * court depuis la fin de la précédente ; si l'examen abandonné n'était pas fermé, tout le temps du
 * sortant se déverserait sur l'examen de son successeur, qui paraîtrait avoir mis des jours là où il
 * commence à peine. Fermer, et non supprimer : le passage a eu lieu, sa durée est mesurée jusqu'au
 * retrait.</p>
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

    private void reattribuerAuCc() throws Exception {
        mvc.perform(put("/api/dispatchs/" + DISPATCH).header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":" + DISPATCH + ",\"idReception\":" + RECEPTION
                        + ",\"imCtrlMembre\":\"CTRCC1\",\"interimDispatch\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRCC1"));
    }

    private List<TacheDossier> examens() {
        return tacheRepository.findParDossier(DOSSIER).stream()
                .filter(t -> EtapeCircuit.EXAMEN.name().equals(t.getEtape())).toList();
    }

    // ------------------------------------------------------------------ 1. la réattribution ferme

    @Test
    @DisplayName("Recette 1 — après réattribution au CC, l'examen du Membre sortant est fermé à son nom : "
            + "le passage a eu lieu, sa durée entamée reste mesurée")
    void reattribution_fermeLExamenDuSortant() throws Exception {
        reattribuerAuCc();

        List<TacheDossier> examens = examens();
        assertThat(examens).as("l'examen du sortant est FERMÉ, pas supprimé : le passage a eu lieu")
                .hasSize(1);
        TacheDossier sortant = examens.get(0);
        assertThat(sortant.getImActeur()).as("mesuré au nom du sortant, jamais du réattribueur")
                .isEqualTo("CTRMEM");
        assertThat(sortant.getDateFin()).as("la fin est posée à l'instant de la réattribution").isNotNull();
    }

    @Test
    @DisplayName("Recette 2 & 3 — l'examen du NOUVEL attributaire repart à zéro : son entrée est l'instant "
            + "de la réattribution, pas le dispatch initial — le temps du sortant ne lui est pas imputé")
    void apresReattribution_lExamenDuSuccesseurRepartDeLaReattribution() throws Exception {
        reattribuerAuCc();

        String chrono = mvc.perform(get("/api/dossiers/" + DOSSIER + "/chronometrage")
                .header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapeCourante").value("EXAMEN"))
                .andExpect(jsonPath("$.attributaire").value("CTRCC1"))
                .andReturn().getResponse().getContentAsString();

        // Le dernier passage est l'examen EN COURS du successeur ; le précédent, le DISPATCH qui l'a
        // ouvert. L'un entre là où l'autre s'arrête : c'est toute la règle.
        List<Object> enCours = com.jayway.jsonpath.JsonPath.read(chrono, "$.etapes[?(@.enCours==true)].etape");
        assertThat(enCours).containsExactly("EXAMEN");
        String entreeCourante = com.jayway.jsonpath.JsonPath.<List<String>>read(
                chrono, "$.etapes[?(@.enCours==true)].entree").get(0);
        List<String> finsDispatch = com.jayway.jsonpath.JsonPath.read(chrono, "$.etapes[?(@.etape=='DISPATCH')].fin");
        assertThat(entreeCourante).as("l'examen du successeur commence à la réattribution")
                .isEqualTo(finsDispatch.get(finsDispatch.size() - 1));

        // L'examen du sortant, lui, garde sa propre borne de fin : les deux ne se recouvrent pas.
        assertThat(examens()).hasSize(1);
        assertThat(examens().get(0).getImActeur()).isEqualTo("CTRMEM");
    }

    // ------------------------------------------------------------------ 1 bis. ... sans lui voler son nom

    /**
     * ⚠️ <strong>Recette Q2 du 2026-09-16 — « Examiné par » faux après une réattribution.</strong>
     *
     * <p>Constat à l'écran : le CC réattribue l'examen à un Membre, le Membre examine, et jusqu'à la
     * soumission du PV la frise du dossier et le volet « Examiné par » nommaient <strong>le sortant</strong>.
     * La cause n'est pas une passe mal rattachée : depuis la refonte du 2026-09-12, une passe n'est
     * qu'une <em>fin</em> et rien n'en rouvre. Entre la réattribution et la soumission du PV, la seule
     * fin d'EXAMEN écrite est celle que la réattribution pose au nom du sortant — or le dossier passe
     * pourtant à {@code EXAMINE} dès que le successeur rend son avis, et la frise lit alors ce dernier
     * passage. {@code ChronometrageService.cloturerExamen}, qui remet l'examen au nom de l'attributaire,
     * n'arrive qu'à {@code POST /pv-examens/{id}/soumettre} : bien trop tard.</p>
     *
     * <p>Correctif <strong>en lecture seule</strong> : la frise nomme l'EXAMEN d'après l'<strong>attributaire
     * courant</strong>, comme elle le fait déjà du DISPATCH. Aucune passe n'est écrite, déplacée ni
     * réécrite — les durées sont celles des trois tests voisins, inchangées.</p>
     */
    @Test
    @DisplayName("⚠️ Recette Q2 — après réattribution, « Examiné par » nomme le NOUVEL attributaire dès son "
            + "examen, sans attendre la soumission du PV (dossier ET /gestes)")
    void apresReattribution_lExamenEstNommeAuNouvelAttributaire_avantToutPv() throws Exception {
        reattribuerAuCc();
        // Le nouvel attributaire examine et soumet son examen : dossier EXAMINE, projet de PV en BROUILLON.
        // Aucun geste n'a encore soumis le PV — c'est exactement la fenêtre du défaut.
        examinerEtSoumettre(tokenCc, "CTRCC1");
        assertThat(pvExamenRepository.statutsPvParDossier(DOSSIER)).as("le PV n'est pas soumis")
                .containsExactly("BROUILLON");

        // 1. La page dossier.
        String dossier = mvc.perform(get("/api/dossiers/" + DOSSIER).header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EXAMINE"))
                .andExpect(jsonPath("$.acteursEtapes.EXAMEN").value("Prenoms NomCTRCC1"))
                .andExpect(jsonPath("$.acteursEtapes.PROJET_PV").value("Prenoms NomCTRCC1"))
                .andExpect(jsonPath("$.acteursEtapes.DISPATCH").value("Prenoms NomCTRCC1"))
                .andReturn().getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.<String>read(dossier, "$.acteursEtapes.EXAMEN"))
                .as("jamais le sortant").isNotEqualTo("Prenoms NomCTRMEM");

        // 2. La même frise, servie par la page dossier guidée.
        String gestes = mvc.perform(get("/api/dossiers/" + DOSSIER + "/gestes").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(gestes, "$.taches[*].geste"))
                .as("le nouvel attributaire a bien la main").contains("SOUMETTRE_PV");
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(
                gestes, "$.taches[*].dossier.acteursEtapes.EXAMEN"))
                .as("/gestes sert la même frise que le dossier").containsOnly("Prenoms NomCTRCC1");

        // 3. Le chronométrage n'a pas bougé d'un pouce : la passe du sortant est toujours là, close, à SON
        // nom — le correctif ne touche qu'à la lecture de la frise.
        assertThat(examens()).hasSize(1);
        assertThat(examens().get(0).getImActeur()).isEqualTo("CTRMEM");
        assertThat(examens().get(0).getDateFin()).isNotNull();
    }

    @Test
    @DisplayName("Non-régression — SANS réattribution, rien ne change : aucune fin d'EXAMEN n'étant écrite "
            + "avant la soumission du PV, la clé reste non datée DONC non nommée (nommé ⇔ daté)")
    void sansReattribution_laFriseEstInchangee() throws Exception {
        examinerEtSoumettre(tokenMembre, "CTRMEM");

        mvc.perform(get("/api/dossiers/" + DOSSIER).header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EXAMINE"))
                .andExpect(jsonPath("$.acteursEtapes.DISPATCH").value("Prenoms NomCTRMEM"))
                .andExpect(jsonPath("$.datesEtapes.EXAMEN").doesNotExist())
                .andExpect(jsonPath("$.acteursEtapes.EXAMEN").doesNotExist())
                .andExpect(jsonPath("$.acteursEtapes.PROJET_PV").doesNotExist());
        assertThat(examens()).as("aucune passe d'EXAMEN tant que le PV n'est pas soumis").isEmpty();
    }

    /** Le titulaire examine et soumet son examen : dossier {@code EXAMINE}, projet de PV en {@code BROUILLON}. */
    private void examinerEtSoumettre(String jeton, String im) throws Exception {
        mvc.perform(post("/api/examens").header("Authorization", jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":" + DISPATCH + ",\"idDispatch\":" + DISPATCH
                        + ",\"imCtrlMembre\":\"" + im + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/examens/" + DISPATCH + "/soumettre").header("Authorization", jeton)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idAvis\":\"FAV\"}"))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ 2. le retrait ferme aussi

    @Test
    @DisplayName("Symétrie — le RETRAIT du dispatch ferme lui aussi l'examen en cours : la purge de l'aval "
            + "efface les sources, le chronomètre doit être arrêté avant qu'elles disparaissent")
    void retraitDuDispatch_fermeAussiLEtapeEnCours() throws Exception {
        mvc.perform(post("/api/dispatchs/" + DISPATCH + "/annuler").header("Authorization", tokenPresident))
                .andExpect(status().isNoContent());

        List<TacheDossier> examens = examens();
        assertThat(examens).hasSize(1);
        assertThat(examens.get(0).getDateFin()).isNotNull();
        assertThat(examens.get(0).getImActeur()).as("au nom de celui à qui l'examen revenait")
                .isEqualTo("CTRMEM");
    }
}
