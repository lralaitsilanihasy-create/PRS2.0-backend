package cnm.prs;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.entity.Capm;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.enums.CategorieModePassation;

/**
 * ⚠️ <strong>Justifications de la fiche de présentation</strong> (arbitrage du pilote, 2026-09-01) —
 * les trois gardes, leur cumul, et surtout leurs <strong>bornes</strong> : ce qui n'est PAS exigé
 * compte autant que ce qui l'est, une garde trop large bloquant des saisies parfaitement régulières.
 *
 * <p>Le classement étant refait serveur, les fixtures posent des référentiels sans ambiguïté : un mode
 * dérogatoire, un mode normal, et un plancher de délai de 30 jours pour éprouver le {@code <} strict.</p>
 */
class FicheJustificationsIntegrationTest extends CnmIntegrationTestSupport {

    /** Mode de droit commun, plancher de publicité à 30 jours. */
    private static final int MODE_NORMAL = 41;

    /** Mode dérogatoire, même plancher — pour isoler la catégorie du délai. */
    private static final int MODE_DEROGATOIRE = 42;

    /** Mode sans plancher : aucun délai n'est calculable, donc aucun délai aménagé. */
    private static final int MODE_SANS_PLANCHER = 43;

    private static final int CAPM_LANCEMENT = 41;
    private static final int CAPM_OUVERTURE = 42;

    @BeforeEach
    void referentielsFiche() {
        natureRepository.save(new Nature(1, "Travaux", null));

        ModePassation normal = new ModePassation(MODE_NORMAL, "Appel d'offres ouvert fiche", null, null, 30, null);
        normal.setCategorie(CategorieModePassation.NORMAL);
        modePassationRepository.save(normal);

        ModePassation derogatoire = new ModePassation(MODE_DEROGATOIRE, "Gre a gre fiche", null, null, 30, null);
        derogatoire.setCategorie(CategorieModePassation.DEROGATOIRE);
        modePassationRepository.save(derogatoire);

        ModePassation sansPlancher = new ModePassation(MODE_SANS_PLANCHER, "Mode sans plancher fiche", null, null, null, null);
        sansPlancher.setCategorie(CategorieModePassation.NORMAL);
        modePassationRepository.save(sansPlancher);

        // Libellés volontairement « habillés » : la résolution passe par la normalisation du dépôt
        // (numéro d'ordre, casse, accents, apostrophes), pas par une égalité stricte au mot-clé.
        capmRepository.save(new Capm(CAPM_LANCEMENT, "2 - Lancement de l'appel d'offres", 1, null, null));
        capmRepository.save(new Capm(CAPM_OUVERTURE, "3 - Ouverture des plis", 2, null, null));
    }

    // ------------------------------------------------------------------ les trois gardes

    @Test
    @DisplayName("Création — marché à mode dérogatoire sans justification → 400 ciblé sur marches[0].justifModeDerogatoire")
    void creation_modeDerogatoire_sansJustification() throws Exception {
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_DEROGATOIRE, "2026-03-01", "2026-06-01", null, null, null),
                        "\"justificationFiche\":\"Fiche motivee\",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[0].justifModeDerogatoire"));

        // Rien n'a été écrit : la garde passe AVANT la création du dossier.
        org.junit.jupiter.api.Assertions.assertTrue(
                marcheRepository.findAll().stream().noneMatch(m -> "Marche fiche".equals(m.getDesignationMarche())));
    }

    @Test
    @DisplayName("Création — délai lancement→ouverture sous le plancher sans justification → 400 sur marches[0].justifDelaiAmenage")
    void creation_delaiAmenage_sansJustification() throws Exception {
        // 10 jours pour un plancher de 30 : délai aménagé. Le mode est NORMAL, donc la liste ① reste vide.
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_NORMAL, "2026-03-01", "2026-03-11", null, null, null),
                        "\"justificationFiche\":\"Fiche motivee\",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[0].justifDelaiAmenage"));
    }

    @Test
    @DisplayName("Création — une liste non vide et justification globale absente → 400 sur justificationFiche")
    void creation_globaleAbsente() throws Exception {
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_DEROGATOIRE, "2026-03-01", "2026-06-01", "Motif du mode", null, null), "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[0].champ").value("justificationFiche"));
    }

    // ------------------------------------------------------------------ cumul et exhaustivité

    @Test
    @DisplayName("Cumul — un marché à la fois dérogatoire ET à délai aménagé rend DEUX erreurs dans le même 400")
    void cumul_deuxJustificationsSurUnMemeMarche() throws Exception {
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_DEROGATOIRE, "2026-03-01", "2026-03-11", null, null, null),
                        "\"justificationFiche\":\"Fiche motivee\",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs", hasSize(2)))
                .andExpect(jsonPath("$.erreurs[*].champ", containsInAnyOrder(
                        "marches[0].justifModeDerogatoire", "marches[0].justifDelaiAmenage")));
    }

    @Test
    @DisplayName("Toutes les erreurs d'un coup — deux marchés fautifs + globale absente → 3 erreurs, index de ligne exacts")
    void toutesLesErreursDUnSeulCoup() throws Exception {
        String corps = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\",\"marches\":["
                + ligne(MODE_NORMAL, "2026-03-01", "2026-06-01", null, null, null) + ","
                + ligne(MODE_DEROGATOIRE, "2026-03-01", "2026-06-01", null, null, null) + ","
                + ligne(MODE_NORMAL, "2026-03-01", "2026-03-11", null, null, null) + "]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs", hasSize(3)))
                .andExpect(jsonPath("$.erreurs[*].champ", containsInAnyOrder(
                        "marches[1].justifModeDerogatoire", "marches[2].justifDelaiAmenage", "justificationFiche")));
    }

    // ------------------------------------------------------------------ les bornes de la règle

    @Test
    @DisplayName("Listes vides — mode normal, délai conforme, quantité fixe → 201 sans aucune justification")
    void listesVides_rienNEstExige() throws Exception {
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_NORMAL, "2026-03-01", "2026-06-01", null, null, null), "")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Égalité au plancher — 30 jours pour un minimum de 30 → conforme, aucune justification exigée")
    void egaliteAuPlancher_estConforme() throws Exception {
        // 2026-03-01 → 2026-03-31 = exactement 30 jours calendaires. La règle est un « < » strict.
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_NORMAL, "2026-03-01", "2026-03-31", null, null, null), "")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Une seule des deux dates — aucun délai calculable, donc aucun délai aménagé (pas de refus au doigt mouillé)")
    void sansLesDeuxDates_pasDeClassement() throws Exception {
        String ligne = "{\"designationMarche\":\"Marche fiche\",\"montEstim\":1000000,\"idNature\":1,\"idMode\":" + MODE_NORMAL
                + ",\"processus\":[{\"idCapm\":" + CAPM_LANCEMENT + ",\"dateDebut\":\"2026-03-01\"}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\",\"marches\":["
                        + ligne + "]}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Mode sans plancher — délai très court mais aucun minimum réglementaire → rien d'exigé")
    void sansPlancher_pasDeClassement() throws Exception {
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_SANS_PLANCHER, "2026-03-01", "2026-03-02", null, null, null), "")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Blancs — une justification faite d'espaces vaut ABSENTE et ne satisfait pas la garde")
    void blancsValentAbsence() throws Exception {
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_DEROGATOIRE, "2026-03-01", "2026-06-01", "   ", null, null),
                        "\"justificationFiche\":\"   \",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs", hasSize(2)))
                .andExpect(jsonPath("$.erreurs[*].champ", containsInAnyOrder(
                        "marches[0].justifModeDerogatoire", "justificationFiche")));
    }

    @Test
    @DisplayName("Contrat-cadre — aucune justification par ligne, seule la globale est exigée")
    void contratCadre_seuleLaGlobaleEstExigee() throws Exception {
        String ligne = "{\"designationMarche\":\"Marche fiche\",\"montEstim\":1000000,\"idNature\":1,\"idMode\":" + MODE_NORMAL
                + ",\"formeMarche\":\"CONTRAT_CADRE\",\"processus\":[{\"idCapm\":" + CAPM_LANCEMENT
                + ",\"dateDebut\":\"2026-03-01\"},{\"idCapm\":" + CAPM_OUVERTURE + ",\"dateDebut\":\"2026-06-01\"}]}";
        String sansGlobale = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\",\"marches\":["
                + ligne + "]}";

        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(sansGlobale))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[0].champ").value("justificationFiche"));

        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sansGlobale.replace("\"marches\":[", "\"justificationFiche\":\"Contrats cadres motives\",\"marches\":[")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Le classement du client n'est pas cru — justification envoyée hors règle : acceptée et stockée, aucune erreur inventée")
    void classementDuClientIgnore() throws Exception {
        // Mode normal, délai conforme : le serveur ne classe cette ligne nulle part. Les justifications
        // envoyées quand même sont simplement conservées — on ne refuse pas ce que la règle n'interdit pas.
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_NORMAL, "2026-03-01", "2026-06-01", "Zele", "Zele aussi", null), "")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idDossier = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        Marche m = marcheRepository.findByIdDossier(idDossier).get(0);
        org.junit.jupiter.api.Assertions.assertEquals("Zele", m.getJustifModeDerogatoire());
        org.junit.jupiter.api.Assertions.assertEquals("Zele aussi", m.getJustifDelaiAmenage());
    }

    // ------------------------------------------------------------------ mise à jour et lecture

    @Test
    @DisplayName("Mise à jour — la garde s'applique aussi au PUT, et une ligne renvoyée SANS les champs conserve ses justifications")
    void miseAJour_gardeEtConservation() throws Exception {
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_DEROGATOIRE, "2026-03-01", "2026-06-01", "Motif initial du mode", null, null),
                        "\"justificationFiche\":\"Fiche initiale\",")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idDossier = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int idDetail = marcheRepository.findByIdDossier(idDossier).get(0).getIdDetail();

        // Ligne renvoyée SANS les justifications : elles survivent, et la garde est satisfaite par le stocké.
        String edition = "{\"exercice\":2026,\"signataire\":\"S\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R\","
                + "\"marches\":[{\"idDetail\":" + idDetail + ",\"designationMarche\":\"Marche fiche renommee\","
                + "\"montEstim\":1000000,\"idNature\":1,\"idMode\":" + MODE_DEROGATOIRE + "}]}";
        mvc.perform(put("/api/saisies/ppm/" + idDossier).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals("Motif initial du mode",
                marcheRepository.findById(idDetail).orElseThrow().getJustifModeDerogatoire());
        org.junit.jupiter.api.Assertions.assertEquals("Fiche initiale",
                ppmRepository.findByIdDossier(idDossier).get(0).getJustificationFiche());

        // Un blanc explicite EFFACE — et la garde le refuse aussitôt, la ligne restant dérogatoire.
        mvc.perform(put("/api/saisies/ppm/" + idDossier).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(edition.replace("\"idMode\":" + MODE_DEROGATOIRE + "}",
                        "\"idMode\":" + MODE_DEROGATOIRE + ",\"justifModeDerogatoire\":\"  \"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[0].justifModeDerogatoire"));
    }

    @Test
    @DisplayName("Mise à jour — une ligne NOUVELLE dérogatoire ajoutée à un plan existant est refusée comme à la création")
    void miseAJour_ligneNouvelleDerogatoire() throws Exception {
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_NORMAL, "2026-03-01", "2026-06-01", null, null, null), "")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idDossier = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int idDetail = marcheRepository.findByIdDossier(idDossier).get(0).getIdDetail();

        String edition = "{\"exercice\":2026,\"signataire\":\"S\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R\","
                + "\"justificationFiche\":\"Fiche motivee\",\"marches\":["
                + "{\"idDetail\":" + idDetail + ",\"designationMarche\":\"Marche fiche\",\"montEstim\":1000000,"
                + "\"idNature\":1,\"idMode\":" + MODE_NORMAL + "},"
                + ligne(MODE_DEROGATOIRE, "2026-03-01", "2026-06-01", null, null, null) + "]}";
        mvc.perform(put("/api/saisies/ppm/" + idDossier).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[1].justifModeDerogatoire"));
    }

    @Test
    @DisplayName("Lecture — les trois champs ressortent sur GET /api/marches/{id} et GET /api/ppms/{id}")
    void lecture_exposeLesTroisChamps() throws Exception {
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(saisie(ligne(MODE_DEROGATOIRE, "2026-03-01", "2026-03-11",
                        "Motif du mode", "Motif du delai", null), "\"justificationFiche\":\"Fiche motivee\",")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idDossier = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int idDetail = marcheRepository.findByIdDossier(idDossier).get(0).getIdDetail();
        int idPpm = ppmRepository.findByIdDossier(idDossier).get(0).getIdPpm();

        mvc.perform(get("/api/marches/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.justifModeDerogatoire").value("Motif du mode"))
                .andExpect(jsonPath("$.justifDelaiAmenage").value("Motif du delai"));
        mvc.perform(get("/api/ppms/" + idPpm).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.justificationFiche").value("Fiche motivee"));
    }

    // ------------------------------------------------------------------ fabriques de corps JSON

    /** Corps de saisie complet autour d'une ligne, avec un fragment de justification globale (ou ""). */
    private String saisie(String ligne, String globale) {
        return "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + globale + "\"marches\":[" + ligne + "]}";
    }

    /**
     * Ligne de marché avec ses deux dates prévisionnelles (lancement / ouverture) et, en option, ses
     * justifications. {@code forme} nulle laisse le défaut {@code QUANTITE_FIXE}.
     */
    private String ligne(int idMode, String lancement, String ouverture,
            String justifMode, String justifDelai, String forme) {
        StringBuilder b = new StringBuilder("{\"designationMarche\":\"Marche fiche\",\"montEstim\":1000000,")
                .append("\"idNature\":1,\"idMode\":").append(idMode);
        if (forme != null) {
            b.append(",\"formeMarche\":\"").append(forme).append("\"");
        }
        if (justifMode != null) {
            b.append(",\"justifModeDerogatoire\":\"").append(justifMode).append("\"");
        }
        if (justifDelai != null) {
            b.append(",\"justifDelaiAmenage\":\"").append(justifDelai).append("\"");
        }
        b.append(",\"processus\":[{\"idCapm\":").append(CAPM_LANCEMENT).append(",\"dateDebut\":\"").append(lancement)
                .append("\"},{\"idCapm\":").append(CAPM_OUVERTURE).append(",\"dateDebut\":\"").append(ouverture)
                .append("\"}]}");
        return b.toString();
    }
}
