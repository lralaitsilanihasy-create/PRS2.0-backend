package cnm.prs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Marche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ Audit 2026-08-27 (lot C, volet 4) — {@code MiseAJourPpmController} n'était appelé par AUCUN test
 * HTTP. Scénario réaliste : le dossier 1 (seed standard) est mené jusqu'à {@code CLOTURE} (circuit FAVR
 * complet, cf. {@code cloturerDossier1}), une mise à jour y est ouverte, ses versions sont listées, une
 * de ses lignes de marché copiées est supprimée puis restaurée — avec vérification des corps de réponse
 * ET des effets en base.
 *
 * <p>⚠️ Import PDF laissé de côté (endpoint {@code .../mise-a-jour/import}) — le fixture PDF
 * ({@code pdfAvecTexte}) est <strong>privé</strong> à {@code ImportPpmIntegrationTest} et calibré pour
 * le tableau de la saisie initiale, pas le format de rapprochement d'une mise à jour ; le déplacer/adapter
 * dépasse le périmètre de ce volet.</p>
 */
class MiseAJourPpmIntegrationTest extends CnmIntegrationTestSupport {

    /**
     * ⚠️ {@code MiseAJourPpmService.exigerProprietaire} (privé, distinct de celui de
     * {@code DossierService}) exige {@code dossier.getIdPrmp()} — sans repli pour un dossier « sans
     * propriétaire connu ». Le dossier 1 du seed standard ({@code CnmIntegrationTestSupport.dossier})
     * ne porte ni {@code idPrmp} ni {@code idEntiteContract} (seul le PPM 1 porte {@code idPrmp}) : les
     * autres endpoints du circuit tolèrent cette absence, celui-ci non. Renseigné ici uniquement, sans
     * toucher au seed partagé.
     */
    private void enrichirDossier1PourPrmp() {
        cnm.prs.entity.Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setIdPrmp("PRMP001");
        d.setIdEntiteContract(1);
        dossierRepository.save(d);
    }

    /**
     * Ouvre une mise à jour et <strong>rapporte le corps de la réponse</strong> si elle est refusée.
     *
     * <p>⚠️ 2026-08-28 — sans cela, un échec ne dit que « expected:&lt;201&gt; but was:&lt;409&gt; », sans
     * révéler LEQUEL des trois refus de {@code creerMiseAJour} s'applique (statut incompatible,
     * brouillon déjà ouvert, PV signé absent). Le corps n'apparaît nulle part ailleurs : MockMvc ne
     * l'imprime pas et un 409 n'est pas journalisé côté serveur. Ces deux tests passent en local et
     * échouent en CI depuis le 2026-08-28 ; quatre hypothèses ont déjà été écartées faute de ce
     * message — dont la version de PostgreSQL, testée en montant la CI en 18.</p>
     */
    private String ouvrirMiseAJour(int idDossier, String motif) throws Exception {
        var res = mvc.perform(post("/api/saisies/ppm/" + idDossier + "/mise-a-jour")
                        .header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":\"" + motif + "\"}"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(201, res.getResponse().getStatus(),
                "ouverture de mise a jour refusee -- corps : " + res.getResponse().getContentAsString());
        return res.getResponse().getContentAsString();
    }

    @Test
    @DisplayName("POST mise-a-jour -> 201 BROUILLON rattaché ; GET versions liste les deux ; PATCH supprimer/restaurer une ligne copiée")
    void miseAJour_creation_versions_supprimerRestaurer() throws Exception {
        enrichirDossier1PourPrmp();
        // Le dossier 1 porte au moins une ligne de marché à recopier dans la nouvelle version.
        marcheRepository.save(marche(9800, 1, 1));

        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        cloturerDossier1(9801, tokenVer);
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));

        // Ouverture de la mise à jour : 201, BROUILLON, rattaché au dossier 1.
        String rep = ouvrirMiseAJour(1, "Rectification budgetaire");
        org.junit.jupiter.api.Assertions.assertEquals("BROUILLON", JsonPath.read(rep, "$.statut"));
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) (Integer) JsonPath.read(rep, "$.idDossierParent"));
        int idNouveau = (int) (Integer) JsonPath.read(rep, "$.idDossier");

        // Depuis le point d'entrée 1 : le brouillon de mise à jour n'y figure PAS encore — une mise à
        // jour EN COURS n'est pas une version tant qu'elle n'est pas soumise (cf. javadoc chaineVersions).
        mvc.perform(get("/api/dossiers/1/versions").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==" + idNouveau + ")]", hasSize(0)));
        // Depuis le brouillon lui-même (point d'entrée), sa propre filiation remonte jusqu'à l'ancêtre 1.
        mvc.perform(get("/api/dossiers/" + idNouveau + "/versions").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==" + idNouveau + ")]", hasSize(1)));

        // La ligne de marché 9800 a été copiée sous une NOUVELLE pk, rattachée au nouveau dossier.
        java.util.List<Marche> lignesCopiees = marcheRepository.findByIdDossier(idNouveau);
        org.junit.jupiter.api.Assertions.assertEquals(1, lignesCopiees.size());
        int idLigneCopiee = lignesCopiees.get(0).getIdDetail();
        org.junit.jupiter.api.Assertions.assertNotEquals(9800, idLigneCopiee, "PK neuve, pas celle de la source");
        org.junit.jupiter.api.Assertions.assertFalse(Boolean.TRUE.equals(lignesCopiees.get(0).getSupprimee()));

        // Suppression logique (restaurable) : 204, effet vérifié en base ET par relecture HTTP.
        mvc.perform(patch("/api/marches/" + idLigneCopiee + "/supprimer").header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(
                Boolean.TRUE.equals(marcheRepository.findById(idLigneCopiee).orElseThrow().getSupprimee()));
        mvc.perform(get("/api/marches/" + idLigneCopiee).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supprimee").value(true));

        // Restauration : 204, la ligne redevient active.
        mvc.perform(patch("/api/marches/" + idLigneCopiee + "/restaurer").header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(
                Boolean.TRUE.equals(marcheRepository.findById(idLigneCopiee).orElseThrow().getSupprimee()));
        mvc.perform(get("/api/marches/" + idLigneCopiee).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supprimee").value(false));
    }

    @Test
    @DisplayName("POST mise-a-jour : motif vide -> 400 ; profil non-PRMP -> 403 ; dossier non éligible (statut du circuit) -> 409")
    void miseAJour_creation_casDErreur() throws Exception {
        enrichirDossier1PourPrmp();
        // Dossier 1 encore EXAMINE (seed standard) : statut hors STATUTS_MISE_A_JOUR_OUVERTE -> 409.
        mvc.perform(post("/api/saisies/ppm/1/mise-a-jour").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Motif\"}"))
                .andExpect(status().isConflict());

        // Motif vide -> 400 (validation du DTO), avant même la garde de statut.
        mvc.perform(post("/api/saisies/ppm/1/mise-a-jour").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"\"}"))
                .andExpect(status().isBadRequest());

        // Profil non-PRMP -> 403 (garde de contrôleur).
        mvc.perform(post("/api/saisies/ppm/1/mise-a-jour").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Motif\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Une deuxième mise à jour ne peut pas s'ouvrir tant que la première est encore un brouillon -> 409")
    void miseAJour_doublonBrouillon_refuse409() throws Exception {
        enrichirDossier1PourPrmp();
        marcheRepository.save(marche(9810, 1, 1));
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        cloturerDossier1(9811, tokenVer);

        ouvrirMiseAJour(1, "Premiere mise a jour");

        mvc.perform(post("/api/saisies/ppm/1/mise-a-jour").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Deuxieme tentative\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH supprimer/restaurer : réservé PRMP (403 au Membre) ; refusé (409) hors brouillon de mise à jour")
    void supprimerRestaurer_casDErreur() throws Exception {
        enrichirDossier1PourPrmp();
        // Dossier 1 CLOTURE (pas BROUILLON) porte la ligne 9820 : la suppression y est refusée (409).
        marcheRepository.save(marche(9820, 1, 1));
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        cloturerDossier1(9821, tokenVer);

        mvc.perform(patch("/api/marches/9820/supprimer").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/marches/9820/supprimer").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/marches/9820/restaurer").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }
}
