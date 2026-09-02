package cnm.prs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.PorteePointCtrl;

/**
 * ⚠️ <strong>La fiche de présentation et l'AGPM entrent dans l'examen</strong> (règle du pilote,
 * 2026-09-02) — deux portées de plus, chacune avec sa grille.
 *
 * <p>Ce que ces tests protègent en priorité, c'est le <strong>piège des deux gardes</strong> : elles
 * testaient la portée par {@code == DOSSIER} et rangeaient tout le reste du côté « par ligne de
 * marché ». Sans correction, un point {@code FICHE} aurait exigé une évaluation par marché à la
 * soumission, et accepté un {@code idDetail} qu'il n'a pas. Les deux cas sont éprouvés ici.</p>
 *
 * <p>Le <strong>rattachement</strong> l'est aussi : les points {@code FICHE} sont communs à la famille
 * DDP — qui ne contient que {@code PPM} et {@code PPM-AGPM} — et les points {@code AGPM} spécifiques au
 * seul {@code PPM-AGPM}. Un dossier d'une autre famille n'en voit aucun, ce que le cas {@code DAO}
 * verrouille.</p>
 */
class ExamenFicheAgpmIntegrationTest extends CnmIntegrationTestSupport {

    /** Ids de la grille fabriquée par ce test : 9-11 pour la fiche (communs DDP), 12-14 pour l’AGPM. */
    private static final int FICHE_1 = 9;
    private static final int AGPM_1 = 12;

    /**
     * Points de la fiche (communs DDP) et de l'AGPM (spécifiques PPM-AGPM), créés par le test.
     *
     * <p>⚠️ Le seed réel vit dans {@code PointsCtrlFicheAgpmSeeder}, au démarrage : sur une base de test
     * neuve, les référentiels {@code tr_type_dossier} / {@code tr_sous_type_dossier} sont posés par la
     * fixture APRÈS le démarrage du contexte, si bien que le seeder s'abstient — à dessein. Les tests
     * fabriquent donc leur grille, comme le fait déjà {@code grilleExamen_parSousType}.</p>
     */
    @BeforeEach
    void grilleFicheEtAgpm() {
        creerPoint(FICHE_1, "Listes de la fiche cohérentes avec le plan", PorteePointCtrl.FICHE, null, 9);
        creerPoint(FICHE_1 + 1, "Justifications par marché recevables", PorteePointCtrl.FICHE, null, 10);
        creerPoint(FICHE_1 + 2, "Justification globale recevable", PorteePointCtrl.FICHE, null, 11);
        creerPoint(AGPM_1, "AGPM cohérent avec le PPM", PorteePointCtrl.AGPM, "PPM-AGPM", 12);
        creerPoint(AGPM_1 + 1, "Dates du DAO = lancement", PorteePointCtrl.AGPM, "PPM-AGPM", 13);
        creerPoint(AGPM_1 + 2, "Forme conforme au modèle", PorteePointCtrl.AGPM, "PPM-AGPM", 14);
    }

    private void creerPoint(int id, String libelle, PorteePointCtrl portee, String sousType, int ordre) {
        PointsCtrl p = new PointsCtrl();
        p.setIdPointCtrl(id);
        p.setLibelPointCtrl(libelle);
        p.setObligatoire(true);
        p.setIdTypeDossier("DDP");
        p.setIdSousType(sousType);
        p.setPortee(portee);
        p.setOrdrePointCtrl(ordre);
        pointsCtrlRepository.save(p);
    }

    // ------------------------------------------------------------------ portées

    @Test
    @DisplayName("Portées — le menu admin accepte les QUATRE valeurs, et refuse toujours le reste en 400")
    void portees_quatreValeursAcceptees() throws Exception {
        int id = 8100;
        for (String portee : new String[] { "LIGNE", "DOSSIER", "FICHE", "AGPM" }) {
            mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idPointCtrl\":" + (id++) + ",\"libelPointCtrl\":\"Point " + portee + "\","
                            + "\"obligatoire\":true,\"idTypeDossier\":\"DDP\",\"portee\":\"" + portee + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.portee").value(portee));
        }
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":8199,\"libelPointCtrl\":\"Point farfelu\",\"obligatoire\":true,"
                        + "\"idTypeDossier\":\"DDP\",\"portee\":\"AUTRE\"}"))
                .andExpect(status().isBadRequest())
                // Le message énumère les codes DEPUIS l'enum : il ne peut plus diverger de la liste réelle.
                .andExpect(jsonPath("$.message", containsString("FICHE")))
                .andExpect(jsonPath("$.message", containsString("AGPM")));
    }

    // ------------------------------------------------------------------ grille effective

    @Test
    @DisplayName("Grille — un PPM voit les 3 points FICHE et AUCUN point AGPM")
    void grille_ppm_ficheSansAgpm() throws Exception {
        mvc.perform(get("/api/points-ctrls?sousType=PPM").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.portee=='FICHE')]", hasSize(3)))
                .andExpect(jsonPath("$[?(@.portee=='AGPM')]", hasSize(0)));
    }

    @Test
    @DisplayName("Grille — un PPM-AGPM voit les 3 points FICHE ET les 3 points AGPM")
    void grille_ppmAgpm_ficheEtAgpm() throws Exception {
        mvc.perform(get("/api/points-ctrls?sousType=PPM-AGPM").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.portee=='FICHE')]", hasSize(3)))
                .andExpect(jsonPath("$[?(@.portee=='AGPM')]", hasSize(3)));
    }

    @Test
    @DisplayName("⚠️ Le commun DDP n'arrose PAS les autres familles — un DAO ne voit ni fiche ni AGPM")
    void grille_autreFamille_aucunPoint() throws Exception {
        // C'est ce cas qui justifie le rattachement retenu : la crainte qu'un point commun atteigne
        // DMC/DDM ne tient pas, la grille étant déjà filtrée par FAMILLE. DAO appartient à DMC.
        mvc.perform(get("/api/points-ctrls?sousType=DAO").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.portee=='FICHE')]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.portee=='AGPM')]", hasSize(0)));
    }

    // ------------------------------------------------------------------ stockage

    @Test
    @DisplayName("Stockage — un résultat FICHE s'enregistre comme un point DOSSIER (idDetail nul)")
    void stockage_ficheCommeDossier() throws Exception {
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":8201,\"idExamen\":1,\"idPtControle\":" + FICHE_1 + ","
                        + "\"conforme\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDetail").doesNotExist());
    }

    @Test
    @DisplayName("⚠️ Un idDetail sur un point FICHE est REFUSÉ — il n'a pas de ligne de marché")
    void stockage_ficheAvecIdDetail_refuse() throws Exception {
        // Avant le 2026-09-02, la garde testait « == DOSSIER » : FICHE serait passé au travers et un
        // résultat de fiche se serait accroché à un marché.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":8202,\"idExamen\":1,\"idPtControle\":" + FICHE_1 + ","
                        + "\"idDetail\":1,\"conforme\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idDetail"))
                .andExpect(jsonPath("$.erreurs[0].message", containsString("FICHE")));
    }

    @Test
    @DisplayName("⚠️ Un idDetail sur un point AGPM est REFUSÉ de la même façon")
    void stockage_agpmAvecIdDetail_refuse() throws Exception {
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":8203,\"idExamen\":1,\"idPtControle\":" + AGPM_1 + ","
                        + "\"idDetail\":1,\"conforme\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].message", containsString("AGPM")));
    }

    // ------------------------------------------------------------------ complétude

    @Test
    @DisplayName("⚠️ Complétude — un point FICHE non statué bloque la soumission, et UNE SEULE évaluation suffit")
    void completude_ficheExigeeUneSeuleFois() throws Exception {
        Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setIdTypeDossier("DDP");   // sans famille, la grille effective est vide et la garde ne dit rien
        d.setIdSousType("PPM");
        dossierRepository.save(d);

        String erreur = mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idAvis\":\"FAV\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        // Le point de fiche est réclamé, et il l'est comme une évaluation UNIQUE — jamais « — marché ... »,
        // ce qui aurait été le cas si la garde l'avait rangé du côté par-ligne.
        org.junit.jupiter.api.Assertions.assertTrue(erreur.contains("fiche de présentation"),
                "le point FICHE manquant doit être nommé au niveau fiche : " + erreur);
        org.junit.jupiter.api.Assertions.assertFalse(
                erreur.matches("(?s).*Listes de la fiche[^»]*».{0,20}— marché.*"),
                "un point FICHE ne doit JAMAIS être réclamé marché par marché : " + erreur);
    }

    @Test
    @DisplayName("Complétude — un dossier PPM ne réclame aucun point AGPM (grille effective par sous-type)")
    void completude_ppmSansAgpm() throws Exception {
        Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setIdTypeDossier("DDP");   // sans famille, la grille effective est vide et la garde ne dit rien
        d.setIdSousType("PPM");
        dossierRepository.save(d);

        String erreur = mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idAvis\":\"FAV\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(erreur.contains("projet d'AGPM"),
                "un PPM sans AGPM ne doit pas voir la grille AGPM : " + erreur);
    }

    @Test
    @DisplayName("Complétude — un PPM-AGPM réclame AUSSI les points AGPM")
    void completude_ppmAgpm_reclameAgpm() throws Exception {
        Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setIdTypeDossier("DDP");   // sans famille, la grille effective est vide et la garde ne dit rien
        d.setIdSousType("PPM-AGPM");
        dossierRepository.save(d);

        String erreur = mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idAvis\":\"FAV\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(erreur.contains("projet d'AGPM"),
                "un PPM-AGPM doit voir la grille AGPM : " + erreur);
    }
}
