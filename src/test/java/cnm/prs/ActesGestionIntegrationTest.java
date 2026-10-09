package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.Reception;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.exception.BusinessRuleException;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M5a, §B1 et §B5 ; V98) — les actes de gestion déposés depuis le marché : marché
 * DDM au PV favorable exigé, saisie libre d'un acte refusée, rang de l'avenant (et sa référence), faits du marché déclarés puis hérités,
 * garde-fous de l'avenant (réception, solde, plafond du tiers, cumul des seuls avenants soumis).
 */
class ActesGestionIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired private cnm.prs.service.ActesGestionService service;
    @Autowired private cnm.prs.service.ReceptionService receptionService;

    private int marche;

    @BeforeEach
    void marcheControle() throws Exception {
        if (!typeDossierRepository.existsById("DDM")) {
            typeDossierRepository.save(new cnm.prs.entity.TypeDossier("DDM", "Dossier de marché"));
        }
        if (!sousTypeDossierRepository.existsById("MAOO")) {
            sousTypeDossierRepository.save(new SousTypeDossier("MAOO", "Marché sur appel d'offres ouvert", "DDM"));
        }
        marche = nouveauMarche();
        receptionRepository.save(reception(97001, marche, "CTRSEC", true));
        dispatchRepository.save(dispatch(97001, 97001, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(97001, 97001, "CTRMEM"));
        seedPvSigne(97001, 97001);
    }

    private int nouveauMarche() throws Exception {
        String cree = mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"MAOO\",\"idEntiteContract\":1}")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        int id = JsonPath.read(cree, "$.idDossier");
        Dossier d = dossierRepository.findById(id).orElseThrow();
        d.setStatut("CLOTURE");
        dossierRepository.save(d);
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions deposer(int idMarche, String corps) throws Exception {
        return mvc.perform(post("/api/dossiers/" + idMarche + "/actes-gestion").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    @Test
    @DisplayName("Dépôt depuis un marché contrôlé, rang, faits déclarés puis hérités, plafond du tiers, réception et solde")
    void avenants() throws Exception {
        // Saisie libre refusée ; marché sans PV favorable refusé ; dossier hors DDM refusé.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"AVN\",\"idEntiteContract\":1}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTE_DEPUIS_LE_MARCHE"));
        int nonControle = nouveauMarche();
        deposer(nonControle, "{\"sousType\":\"DR\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MARCHE_NON_CONTROLE"));
        deposer(1, "{\"sousType\":\"DR\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAS_UN_MARCHE"));
        deposer(marche, "{\"sousType\":\"MAOO\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SOUS_TYPE_HORS_DGC"));

        // Avenant n° 1 : le montant initial et la catégorie, inconnus du serveur (ni attribution ni fiche), se déclarent.
        deposer(marche, "{\"sousType\":\"AVN\",\"montantHt\":200000}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MONTANT_INITIAL_OBLIGATOIRE"));
        deposer(marche, "{\"sousType\":\"AVN\",\"montantHt\":200000,\"montantInitialHt\":900000}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORIE_OBLIGATOIRE"));
        String avn1 = deposer(marche, "{\"sousType\":\"AVN\",\"montantHt\":200000,\"montantInitialHt\":900000,\"categorie\":\"TRAVAUX\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rang").value(1)).andExpect(jsonPath("$.statutDossier").value("BROUILLON"))
                .andReturn().getResponse().getContentAsString();
        int dossierAvn1 = JsonPath.read(avn1, "$.idDossier");
        assertThat(dossierRepository.findById(dossierAvn1).orElseThrow().getIdTypeDossier()).isEqualTo("DGC");

        // Avenant n° 2 : faits hérités ; le n° 1 encore en brouillon n'entre pas dans le cumul.
        String avn2 = deposer(marche, "{\"sousType\":\"AVN\",\"montantHt\":250000}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.rang").value(2)).andReturn().getResponse().getContentAsString();
        int dossierAvn2 = JsonPath.read(avn2, "$.idDossier");

        // Le n° 1 soumis compte : 200 000 + 250 000 > 300 000 (tiers de 900 000).
        Dossier d1 = dossierRepository.findById(dossierAvn1).orElseThrow();
        d1.setStatut("SOUMIS");
        dossierRepository.save(d1);
        mvc.perform(put("/api/actes-gestion/" + dossierAvn2).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantHt\":250000}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AVENANT_PLAFOND"))
                .andExpect(jsonPath("$.details.plafondHt").value(300000.00));
        mvc.perform(put("/api/actes-gestion/" + dossierAvn2).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantHt\":100000}")).andExpect(status().isOk()).andExpect(jsonPath("$.montantHt").value(100000));

        // Travaux : la réception provisoire ne bloque pas, la définitive (passée) oui ; le solde réglé aussi.
        LocalDate hier = LocalDate.now().minusDays(1);
        mvc.perform(put("/api/actes-gestion/" + dossierAvn2).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantHt\":100000,\"dateReceptionProvisoire\":\"" + hier + "\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/actes-gestion/" + dossierAvn2).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantHt\":100000,\"dateReceptionDefinitive\":\"" + hier + "\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVENANT_APRES_RECEPTION"));
        mvc.perform(put("/api/actes-gestion/" + dossierAvn2).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantHt\":100000,\"dateSolde\":\"" + hier + "\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVENANT_APRES_SOLDE"));
        // Un acte déjà soumis ne se modifie plus.
        mvc.perform(put("/api/actes-gestion/" + dossierAvn1).header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantHt\":1}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOSSIER_NON_BROUILLON"));

        // Une résiliation : sans rang ni garde-fou d'avenant.
        deposer(marche, "{\"sousType\":\"DR\",\"montantHt\":5}").andExpect(status().isCreated()).andExpect(jsonPath("$.rang").doesNotExist())
                .andExpect(jsonPath("$.montantHt").doesNotExist());

        // La vue du marché.
        mvc.perform(get("/api/dossiers/" + marche + "/actes-gestion").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.avisMarche").value("FAV")).andExpect(jsonPath("$.montantInitialHt").value(900000))
                .andExpect(jsonPath("$.sourceMontantInitial").value("DECLARE")).andExpect(jsonPath("$.categorie").value("TRAVAUX"))
                .andExpect(jsonPath("$.cumulAvenantsHt").value(200000)).andExpect(jsonPath("$.plafondAvenantsHt").value(300000.00))
                .andExpect(jsonPath("$.rangAvenantSuivant").value(3)).andExpect(jsonPath("$.actes.length()").value(3))
                .andExpect(jsonPath("$.actes[0].compteDansLeCumul").value(true)).andExpect(jsonPath("$.actes[1].compteDansLeCumul").value(false));
        mvc.perform(get("/api/actes-gestion/" + dossierAvn2).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idDossierMarche").value(marche));

        // La référence de l'avenant porte son rang.
        Reception r = reception(97002, dossierAvn2, "CTRSEC", true);
        receptionRepository.save(r);
        String reference = ReflectionTestUtils.invokeMethod(receptionService, "genererReference", r);
        assertThat(reference).contains("/AVN2/");
    }

    @Test
    @DisplayName("Soumission : un dossier DGC né hors du marché est refusé ; un avenant devenu au-delà du tiers aussi")
    void soumission() throws Exception {
        Dossier libre = dossier(97100, "BROUILLON");
        libre.setIdTypeDossier("DGC");
        libre.setIdSousType("DR");
        libre.setIdPrmp("PRMP001");
        dossierRepository.save(libre);
        assertThatThrownBy(() -> service.exigerAvantSoumission(libre)).isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("ACTE_SANS_MARCHE");

        String a = deposer(marche, "{\"sousType\":\"AVN\",\"montantHt\":250000,\"montantInitialHt\":900000,\"categorie\":\"FOURNITURES_SERVICES\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String b = deposer(marche, "{\"sousType\":\"AVN\",\"montantHt\":250000}").andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        Dossier da = dossierRepository.findById((Integer) JsonPath.read(a, "$.idDossier")).orElseThrow();
        da.setStatut("SOUMIS");
        dossierRepository.save(da);
        Dossier db = dossierRepository.findById((Integer) JsonPath.read(b, "$.idDossier")).orElseThrow();
        assertThatThrownBy(() -> service.exigerAvantSoumission(db)).isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("AVENANT_PLAFOND");

        // Un avenant antérieur à l'avis défavorable sort du cumul.
        receptionRepository.save(reception(97003, da.getIdDossier(), "CTRSEC", true));
        dispatchRepository.save(dispatch(97003, 97003, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(97003, 97003, "CTRMEM"));
        seedPvSigne(97003, 97003);
        PvExamen pv = pvExamenRepository.findById(97003).orElseThrow();
        pv.setIdAvis("DEF");
        pvExamenRepository.save(pv);
        service.exigerAvantSoumission(db);
    }
}
