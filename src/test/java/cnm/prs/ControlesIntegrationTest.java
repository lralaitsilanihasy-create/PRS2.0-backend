package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.service.PvExamenService;

/**
 * ⚠️ R5 (manuel révisé pour l'application, lot 1, §B6 ; V105, 2026-10-13) — le socle des contrôles : à la soumission d'un dossier de
 * marché conduit en ligne sans PV ni rapport signés, les deux règles bloquantes refusent la soumission (400, valeurs nommées) ; hors
 * ligne, elles sont enregistrées « sans objet » ; le Membre attributaire pose ses contrôles B (observation obligatoire si non
 * conforme) ; une non-conformité non levée interdit l'avis FAV, sa levée le rend possible ; la synthèse PDF.
 */
class ControlesIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private cnm.prs.repository.PieceSousTypeRepository associations;
    @Autowired private cnm.prs.repository.AttributionRepository attributionRepository;
    @Autowired private PvExamenService pvExamenService;

    @BeforeEach
    void jeu() {
        if (!typeDossierRepository.existsById("DDM")) {
            typeDossierRepository.save(new cnm.prs.entity.TypeDossier("DDM", "Dossier de marché"));
        }
        if (!sousTypeDossierRepository.existsById("MAOO")) {
            sousTypeDossierRepository.save(new SousTypeDossier("MAOO", "Marché sur appel d'offres ouvert", "DDM"));
        }
        // Les pièces du sous-type ne sont pas l'objet du test : aucune n'est exigée à la soumission.
        associations.findByIdSousTypeOrderByOrdreAscIdAsc("MAOO").forEach(a -> {
            a.setObligatoire(false);
            associations.save(a);
        });
    }

    @Test
    @DisplayName("Soumission d'un marché conduit en ligne sans séance : 400 CONTROLES_BLOQUANTS (P5, P3/P14, valeurs), rien d'enregistré ; "
            + "hors ligne : soumis, deux résultats NON_APPLICABLE du SERVEUR ; contrôle B du Membre (403 PRMP, 400 sans observation, 201) ; "
            + "avis FAV refusé tant que la non-conformité n'est pas levée, admis après ; synthèse PDF ; soumission inconnue → vide")
    void controles() throws Exception {
        String cree = mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idSousType\":\"MAOO\",\"idEntiteContract\":1}")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        int marche = JsonPath.read(cree, "$.idDossier");

        // En ligne : le marché est attribué depuis une procédure dont la séance n'est pas ouverte.
        dossierRepository.save(dossierLoc(97600, "CLOTURE", "ANT", "PRMP001"));
        ppmRepository.save(ppm(97600, 97600, "PRMP001"));
        cnm.prs.entity.Marche ligne = marche(97601, 97600, 97600);
        ligne.setIdNature(natureFournitures());
        marcheRepository.save(ligne);
        cnm.prs.entity.DossierMec dmc = new cnm.prs.entity.DossierMec();
        dmc.setIdDetail(97601);
        dmc.setIdTypeDmc(typeDmcRepository.findByCode("DAO").orElseThrow().getIdTypeDmc());
        dmc.setDateCreation(java.time.LocalDateTime.now());
        dmc = dossierMecRepository.save(dmc);
        cnm.prs.entity.Attribution a = new cnm.prs.entity.Attribution();
        a.setIdDmc(dmc.getIdDmc());
        a.setLot(1);
        a.setEtat(cnm.prs.entity.Attribution.ATTRIBUE);
        a.setIdDossier(marche);
        attributionRepository.saveAndFlush(a);

        String refus = mvc.perform(post("/api/dossiers/" + marche + "/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONTROLES_BLOQUANTS"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(refus, "$.details.controles[*].code"))
                .containsExactlyInAnyOrder("DOCUMENTS_EVALUATION_SIGNES", "QUORUM_COMMISSION");
        assertThat(JsonPath.<List<String>>read(refus, "$.details.controles[*].reference")).contains("2-III-A P5", "2-III-C P3 / P14");
        assertThat(JsonPath.<List<Boolean>>read(refus, "$.details.controles[?(@.code=='DOCUMENTS_EVALUATION_SIGNES')].valeurs.pvOuvertureSigne"))
                .containsExactly(false);
        assertThat(JsonPath.<List<Boolean>>read(refus, "$.details.controles[?(@.code=='QUORUM_COMMISSION')].valeurs.pvOuverture.constate"))
                .containsExactly(false);
        assertThat(dossierRepository.findById(marche).orElseThrow().getStatut()).isEqualTo("BROUILLON");
        mvc.perform(get("/api/dossiers/" + marche + "/controles").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // Hors ligne (marché saisi) : les règles sont sans objet, la soumission passe, les résultats sont enregistrés.
        attributionRepository.delete(a);
        attributionRepository.flush();
        mvc.perform(post("/api/dossiers/" + marche + "/soumettre").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));
        String liste = mvc.perform(get("/api/dossiers/" + marche + "/controles").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(liste, "$[*].regle")).containsExactlyInAnyOrder("DOCUMENTS_EVALUATION_SIGNES", "QUORUM_COMMISSION",
                "CORRECTIONS_HORS_CALCUL");   // ⚠️ R9b (§B4 n° 9) : signalée, non bloquante
        assertThat(JsonPath.<List<String>>read(liste, "$[*].resultat")).containsOnly("NON_APPLICABLE");
        assertThat(JsonPath.<List<String>>read(liste, "$[*].auteur")).containsOnly("SERVEUR");
        assertThat(JsonPath.<List<Boolean>>read(liste, "$[?(@.regle != 'CORRECTIONS_HORS_CALCUL')].bloquant")).containsOnly(true);
        assertThat(JsonPath.<List<Integer>>read(liste, "$[*].numeroSoumission")).containsOnly(1);
        assertThat(JsonPath.<List<Boolean>>read(liste, "$[*].valeurs.procedureEnLigne")).containsOnly(false);

        // Le contrôle B du Membre attributaire de l'examen en cours.
        receptionRepository.save(reception(9950, marche, "CTRCC1", true));
        dispatchRepository.save(dispatch(9950, 9950, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9950, 9950, "CTRMEM"));
        Dossier d = dossierRepository.findById(marche).orElseThrow();
        d.setStatut("EXAMINE");
        dossierRepository.saveAndFlush(d);
        String nonConforme = "{\"reference\":\"2-III-C P20\",\"regle\":\"REPONSE_CANDIDAT\",\"libelle\":\"Réponse du candidat après l'échéance\","
                + "\"resultat\":\"NON_CONFORME\",\"observation\":\"Réponse reçue hors délai\"}";
        mvc.perform(post("/api/dossiers/" + marche + "/controles").header("Authorization", tokenPrmp).contentType(JSON).content(nonConforme))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dossiers/" + marche + "/controles").header("Authorization", tokenMembre).contentType(JSON)
                .content("{\"regle\":\"REPONSE_CANDIDAT\",\"resultat\":\"NON_CONFORME\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='observation')]").isNotEmpty());
        mvc.perform(post("/api/dossiers/" + marche + "/controles").header("Authorization", tokenMembre).contentType(JSON)
                .content("{\"resultat\":\"BOF\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='resultat')]").isNotEmpty()).andExpect(jsonPath("$.erreurs[?(@.champ=='regle')]").isNotEmpty());
        mvc.perform(post("/api/dossiers/" + marche + "/controles").header("Authorization", tokenMembre).contentType(JSON).content(nonConforme))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.auteur").value("CTRMEM")).andExpect(jsonPath("$.numeroSoumission").value(1))
                .andExpect(jsonPath("$.resultat").value("NON_CONFORME")).andExpect(jsonPath("$.reference").value("2-III-C P20"));
        liste = mvc.perform(get("/api/dossiers/" + marche + "/controles").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(liste, "$[*].regle")).hasSize(4).contains("REPONSE_CANDIDAT");

        // La synthèse PDF (annexe du PV).
        byte[] pdf = mvc.perform(get("/api/dossiers/" + marche + "/controles/pdf").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse().getContentAsByteArray();
        assertThat(texteDuPdf(pdf).replaceAll("\\s+", " ")).contains("Synthèse des contrôles", "soumission n° 1", "Quorum de la commission",
                "Réponse du candidat", "Réponse reçue hors délai", "contrôle automatique", "4 contrôle(s), 1 non conforme(s)");   // ⚠️ R9b : + CORRECTIONS_HORS_CALCUL
        mvc.perform(get("/api/dossiers/" + marche + "/controles").param("soumission", "5").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        // Une non-conformité non levée interdit l'avis « Favorable » ; sa levée par le Membre (CONFORME sur la même règle) le rend possible.
        pvExamenService.validerCoherenceAvisPublic(9950, "FAVR");
        assertThatThrownBy(() -> pvExamenService.validerCoherenceAvisPublic(9950, "FAV")).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("non levé").hasMessageContaining("2-III-C P20");
        mvc.perform(post("/api/dossiers/" + marche + "/controles").header("Authorization", tokenMembre).contentType(JSON)
                .content("{\"regle\":\"REPONSE_CANDIDAT\",\"resultat\":\"CONFORME\",\"observation\":\"Réponse reçue dans le délai, vérifiée\"}"))
                .andExpect(status().isCreated());
        pvExamenService.validerCoherenceAvisPublic(9950, "FAV");
        assertThatThrownBy(() -> pvExamenService.validerCoherenceAvisPublic(9950, "FAVR")).isInstanceOf(BusinessRuleException.class);
    }
}
