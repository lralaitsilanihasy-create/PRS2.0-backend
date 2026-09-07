package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Ppm;
import cnm.prs.service.ParametreService;

/**
 * ⚠️ <strong>L'appel à manifestation d'intérêt déclenche l'AGPM, sous condition de montant</strong>
 * (arbitrage pilote du 2026-09-07, « Suite » de {@code docs/demande-backend-2026-09-07-reference-ppm-agpm.md}).
 *
 * <p>L'AMI sort de l'exclusion posée par la V21 sans devenir un déclencheur inconditionnel : un marché AMI
 * ne rend l'AGPM requis que si <strong>son</strong> montant estimé atteint un <strong>seuil administrable</strong>.
 * La règle se dit en deux morceaux, chacun modifiable sans redéploiement : <em>quels modes</em> sont
 * conditionnels ({@code agpmSiSeuil}, référentiel des modes) et <em>à partir de quel montant</em>
 * ({@code AGPM_SEUIL_MONTANT}, paramètres système).</p>
 *
 * <p>Ce qui est éprouvé ici : la comparaison est faite <strong>par marché</strong> et non sur le total du
 * dossier, elle porte sur le montant <strong>en vigueur</strong>, elle suit le seuil quand le pilote
 * l'ajuste, et elle emporte tout le reste — sous-type, référence, numéro de PV — par la mécanique V21.</p>
 */
class AgpmConditionnelAmiIntegrationTest extends CnmIntegrationTestSupport {

    private static final int MODE_AMI = 9;
    private static final int MODE_CONSULTATION = 4;

    @Autowired
    private ParametreService parametreService;

    @BeforeEach
    void referentiels() {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        ModePassation ami = new ModePassation(MODE_AMI, "Appel à manifestation d'intérêt", null, null, null, null);
        ami.setDeclencheAgpm(Boolean.FALSE);   // pas un appel d'offres : jamais inconditionnel
        ami.setAgpmSiSeuil(Boolean.TRUE);      // mais conditionnel au montant
        modePassationRepository.save(ami);
        ModePassation consultation =
                new ModePassation(MODE_CONSULTATION, "Consultation des Prix Ouverte", null, null, null, null);
        consultation.setDeclencheAgpm(Boolean.FALSE);
        consultation.setAgpmSiSeuil(Boolean.FALSE);
        modePassationRepository.save(consultation);
    }

    // ------------------------------------------------------------------ le seuil décide

    @Test
    @DisplayName("Un marché AMI AU-DESSUS du seuil → PPM-AGPM (référence et sous-type suivent) ; EN DESSOUS → PPM")
    void marcheAmi_auDessusDuSeuil_declencheAgpm() throws Exception {
        parametreService.fixerSeuilAgpmMontant(new BigDecimal("100000000"));

        int auDessus = creerPpmAvecMarcheAmi("500000000");
        assertThat(dossierRepository.findById(auDessus).orElseThrow().getIdSousType()).isEqualTo("PPM-AGPM");
        assertThat(referenceDu(auDessus)).contains("/PPM-AGPM/");

        int enDessous = creerPpmAvecMarcheAmi("50000000");
        assertThat(dossierRepository.findById(enDessous).orElseThrow().getIdSousType()).isEqualTo("PPM");
        assertThat(referenceDu(enDessous)).contains("/PPM/").doesNotContain("/PPM-AGPM/");
    }

    @Test
    @DisplayName("Le seuil est atteint À L'ÉGALITÉ (« ≥ seuil ») — la borne appartient au déclenchement")
    void marcheAmi_aLEgaliteDuSeuil_declenche() throws Exception {
        parametreService.fixerSeuilAgpmMontant(new BigDecimal("100000000"));
        int idDossier = creerPpmAvecMarcheAmi("100000000");
        assertThat(dossierRepository.findById(idDossier).orElseThrow().getIdSousType()).isEqualTo("PPM-AGPM");
    }

    @Test
    @DisplayName("La comparaison est PAR MARCHÉ, pas sur le total : deux marchés AMI sous le seuil dont la somme "
            + "le dépasse ne déclenchent pas")
    void comparaisonParMarche_pasLeTotalDuDossier() throws Exception {
        parametreService.fixerSeuilAgpmMontant(new BigDecimal("100000000"));
        int idDossier = creerPpmAvecMarcheAmi("60000000");
        int idPpm = ppmRepository.findByIdDossier(idDossier).stream().findFirst().orElseThrow().getIdPpm();

        // Second marché AMI, sous le seuil lui aussi : 60M + 60M = 120M > 100M, et pourtant PPM.
        mvc.perform(post("/api/marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":" + idDossier + ",\"idPpm\":" + idPpm + ",\"designationMarche\":\"AMI 2\","
                        + "\"montEstim\":60000000,\"idNature\":1,\"idMode\":" + MODE_AMI + ",\"statut\":\"PREVU\"}"))
                .andExpect(status().isCreated());

        assertThat(dossierRepository.findById(idDossier).orElseThrow().getIdSousType())
                .as("un seul marché doit franchir le seuil, la somme ne compte pas").isEqualTo("PPM");
    }

    @Test
    @DisplayName("Le montant retenu est celui EN VIGUEUR : un nouveau montant estimatif prime sur l'initial")
    void montantEnVigueur_leNouveauPrime() throws Exception {
        parametreService.fixerSeuilAgpmMontant(new BigDecimal("100000000"));
        // Initial sous le seuil, nouveau au-dessus → déclenche.
        int idDossier = creerPpmAvecMarcheAmi("50000000", "\"nouvMontEstim\":500000000,");
        assertThat(dossierRepository.findById(idDossier).orElseThrow().getIdSousType()).isEqualTo("PPM-AGPM");
    }

    @Test
    @DisplayName("Un marché AMI SANS montant ne déclenche rien — un montant absent ne franchit aucun seuil")
    void marcheAmiSansMontant_neDeclenchePas() throws Exception {
        parametreService.fixerSeuilAgpmMontant(BigDecimal.ZERO);
        Dossier d = dossier(620, "BROUILLON");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        Ppm ppm = ppmLocalise(620, 620, "ANT");
        ppm.setIdPrmp("PRMP001");
        ppmRepository.save(ppm);
        cnm.prs.entity.Marche m = marche(621, 620, 620);
        m.setIdMode(MODE_AMI);
        m.setMontEstim(null);
        marcheRepository.save(m);

        dossierIntegriteService.recalculerSousTypeDdp(620);
        assertThat(dossierRepository.findById(620).orElseThrow().getIdSousType()).isEqualTo("PPM");
    }

    // ------------------------------------------------------------------ le seuil est administrable

    @Test
    @DisplayName("Le seuil s'ajuste depuis l'administration, sans redéploiement : le PPM lu bascule au prochain "
            + "recalcul ; lecture ouverte, écriture réservée à l'Administrateur, valeur négative refusée")
    void seuil_administrable() throws Exception {
        parametreService.fixerSeuilAgpmMontant(new BigDecimal("100000000"));
        int idDossier = creerPpmAvecMarcheAmi("50000000");
        int idPpm = ppmRepository.findByIdDossier(idDossier).stream().findFirst().orElseThrow().getIdPpm();
        assertThat(dossierRepository.findById(idDossier).orElseThrow().getIdSousType()).isEqualTo("PPM");
        mvc.perform(get("/api/ppms/" + idPpm).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.agpmRequis").value(false));

        // Le pilote abaisse le seuil : le dérivé agpmRequis suit immédiatement (il est lu, pas stocké).
        mvc.perform(put("/api/parametres/agpm-seuil-montant").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"seuil\":10000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuil").value(10000000));
        mvc.perform(get("/api/ppms/" + idPpm).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.agpmRequis").value(true));

        // Le sous-type et la référence, eux, se posent au prochain recalcul du plan.
        dossierIntegriteService.recalculerSousTypeDdp(idDossier);
        assertThat(dossierRepository.findById(idDossier).orElseThrow().getIdSousType()).isEqualTo("PPM-AGPM");
        assertThat(referenceDu(idDossier)).contains("/PPM-AGPM/");

        // Lecture ouverte à tout authentifié ; écriture réservée ; valeur négative refusée.
        mvc.perform(get("/api/parametres/agpm-seuil-montant").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seuil").value(10000000));
        mvc.perform(put("/api/parametres/agpm-seuil-montant").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"seuil\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/parametres/agpm-seuil-montant").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"seuil\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Les modes hors appel d'offres restent inchangés : une consultation des prix ne déclenche jamais, "
            + "quel que soit le montant et quel que soit le seuil")
    void modesHorsAppelDOffres_inchanges() throws Exception {
        parametreService.fixerSeuilAgpmMontant(BigDecimal.ZERO);   // le seuil le plus permissif
        Dossier d = dossier(630, "BROUILLON");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        Ppm ppm = ppmLocalise(630, 630, "ANT");
        ppm.setIdPrmp("PRMP001");
        ppmRepository.save(ppm);
        cnm.prs.entity.Marche m = marche(631, 630, 630);
        m.setIdMode(MODE_CONSULTATION);
        m.setMontEstim(new BigDecimal("900000000"));
        marcheRepository.save(m);

        dossierIntegriteService.recalculerSousTypeDdp(630);
        assertThat(dossierRepository.findById(630).orElseThrow().getIdSousType()).isEqualTo("PPM");
    }

    @Test
    @DisplayName("Seuil absent ou illisible = zéro : tout marché AMI déclenche, faute de seuil saisi (le défaut "
            + "penche du côté de la publicité)")
    void seuilAbsent_vautZero() throws Exception {
        assertThat(parametreService.seuilAgpmMontant()).isEqualByComparingTo(BigDecimal.ZERO);
        int idDossier = creerPpmAvecMarcheAmi("1");
        assertThat(dossierRepository.findById(idDossier).orElseThrow().getIdSousType()).isEqualTo("PPM-AGPM");
    }

    // ------------------------------------------------------------------ helpers

    @Autowired
    private cnm.prs.service.DossierIntegriteService dossierIntegriteService;

    private int creerPpmAvecMarcheAmi(String montEstim) throws Exception {
        return creerPpmAvecMarcheAmi(montEstim, "");
    }

    /** Crée un PPM par la façade, avec une ligne en appel à manifestation d'intérêt. */
    private int creerPpmAvecMarcheAmi(String montEstim, String fragmentSupplementaire) throws Exception {
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\","
                + "\"reference\":\"PPM-AMI\",\"marches\":[{\"designationMarche\":\"Étude\",\"montEstim\":" + montEstim
                + "," + fragmentSupplementaire + "\"idNature\":1,\"idMode\":" + MODE_AMI + ",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-03-01\"}]}]}";
        String reponse = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.idDossier");
    }

    /** Avant réception, la référence du dossier EST celle du PPM (refeDossier n'est posé qu'à la réception). */
    private String referenceDu(int idDossier) {
        return ppmRepository.findByIdDossier(idDossier).stream().findFirst().orElseThrow().getReference();
    }
}
