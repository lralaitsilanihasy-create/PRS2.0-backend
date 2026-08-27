package cnm.prs;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Prmp;
import cnm.prs.enums.TypeActeur;

/**
 * Fiches des acteurs : controleurs (photo, recherches, suppression unitaire et en lot) et PRMP
 * (pieces jointes, compte associe, recherches, gardes de suppression).
 */
class ControleurPrmpIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("nomPrmp élargi : 60 car. accepté à la création PRMP (était 400 à 50) ; >100 → 400")
    void prmp_nomLong_accepte() throws Exception {
        String nom60 = "R".repeat(60);
        String reste = "\"prenomsPrmp\":\"Jean\",\"arreteNomin\":\"ARR-1\",\"dateNomin\":\"2024-01-15\","
                + "\"cin\":\"101011112222\",\"dateCin\":\"2010-05-05\",\"lieuCin\":\"Antananarivo\","
                + "\"emailPrmp\":\"a@b.mg\",\"telPrmp\":\"0330000001\"}";
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMNOM1\",\"nomPrmp\":\"" + nom60 + "\"," + reste))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomPrmp").value(nom60));

        // Au-delà de 100 → 400 (borne).
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMNOM2\",\"nomPrmp\":\"" + "R".repeat(101) + "\"," + reste))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/controleurs/{id} : contrôleur sans activité → 204 (+ compte/sessions/indicateurs nettoyés) ; avec activité → 409 ; inconnu → 404")
    void controleur_delete_gardeEtNettoyage() throws Exception {
        // Contrôleur « propre » (aucune participation métier) + compte + une session + un indicateur.
        controleurRepository.save(controleur("CTRDEL", 6, "ANT"));
        compteAuthRepository.save(new cnm.prs.entity.CompteAuth("ctrdel", "x",
                cnm.prs.enums.TypeActeur.CONTROLEUR.name(), "CTRDEL", true));
        cnm.prs.entity.SessionUtilisateur s = new cnm.prs.entity.SessionUtilisateur();
        s.setIdSession("SESS-CTRDEL");
        s.setImControleur("CTRDEL");
        sessionUtilisateurRepository.save(s);
        cnm.prs.entity.IndicateurCtrl ic = new cnm.prs.entity.IndicateurCtrl();
        ic.setIdIndicateur(990001);
        ic.setImControleur("CTRDEL");
        ic.setPeriode("2026-06");
        indicateurCtrlRepository.save(ic);

        mvc.perform(delete("/api/controleurs/CTRDEL").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        // Contrôleur + compte + données dérivées supprimés.
        org.junit.jupiter.api.Assertions.assertFalse(controleurRepository.existsById("CTRDEL"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ctrdel").isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(sessionUtilisateurRepository.existsById("SESS-CTRDEL"));
        org.junit.jupiter.api.Assertions.assertFalse(indicateurCtrlRepository.existsById(990001));

        // CTRMEM est membre de l'examen 1 (seed) → activité métier → 409, le contrôleur subsiste.
        mvc.perform(delete("/api/controleurs/CTRMEM").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertTrue(controleurRepository.existsById("CTRMEM"));

        // Inconnu → 404.
        mvc.perform(delete("/api/controleurs/INCONNU").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/controleurs multipart : fiche + photo (opt.), GET ; type≠PHOTO/PDF/>5Mo → 400 ; JSON conservé ; DELETE purge la photo ; non-admin → 403")
    void controleur_photo() throws Exception {
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };
        byte[] pdf = "%PDF-1.4 pas une image".getBytes(StandardCharsets.US_ASCII);
        byte[] data = "{\"imControleur\":\"CTRPHO\",\"idProfile\":6,\"transversal\":false,\"nomCont\":\"Photo\"}"
                .getBytes(StandardCharsets.UTF_8);

        // --- Écritures réussies d'abord. ---
        // Création multipart : data + photo (JPEG).
        mvc.perform(multipart("/api/controleurs").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imControleur").value("CTRPHO"));
        // Téléchargement de la photo stockée.
        mvc.perform(get("/api/controleurs/CTRPHO/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
        // Dépôt ultérieur (remplace la photo par un PNG).
        mvc.perform(multipart("/api/controleurs/CTRPHO/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "p.png", "image/png", png)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/controleurs/CTRPHO/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        // JSON pur (sans photo) → 201 (rétro-compat).
        mvc.perform(post("/api/controleurs").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imControleur\":\"CTRPHO2\",\"idProfile\":6,\"transversal\":false}"))
                .andExpect(status().isCreated());
        // DELETE purge la photo (t_piece_jointe, clé imControleur) — pas d'orphelin.
        mvc.perform(delete("/api/controleurs/CTRPHO").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(pieceJointeRepository.findByLogin("CTRPHO").isEmpty());

        // --- Cas d'erreur ensuite (CTRPHO2 existe, sans photo). ---
        // type ≠ PHOTO → 400 (le contrôleur n'a pas d'autre pièce).
        mvc.perform(multipart("/api/controleurs/CTRPHO2/pieces/CIN").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "c.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isBadRequest());
        // Photo = image seulement : un PDF → 400.
        mvc.perform(multipart("/api/controleurs/CTRPHO2/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "p.pdf", "application/pdf", pdf)))
                .andExpect(status().isBadRequest());
        // Contrôleur inconnu → 404.
        mvc.perform(multipart("/api/controleurs/INCONNU/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "p.png", "image/png", png)))
                .andExpect(status().isNotFound());
        // Photo > 5 Mo → 400 (contrôle de taille).
        byte[] gros = new byte[6 * 1024 * 1024];
        gros[0] = (byte) 0xFF; gros[1] = (byte) 0xD8; gros[2] = (byte) 0xFF;   // JPEG magic
        byte[] data3 = "{\"imControleur\":\"CTRPHO3\",\"idProfile\":6,\"transversal\":false}"
                .getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/controleurs").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data3))
                .file(new MockMultipartFile("photo", "big.jpg", "image/jpeg", gros)))
                .andExpect(status().isBadRequest());
        // ⚠️ Lecture ouverte aux authentifiés (photo affichée dans l'UI) — dépôt/suppression restent Admin.
        mvc.perform(multipart("/api/controleurs/CTRPHO2/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "p.png", "image/png", png)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/controleurs/CTRPHO2/pieces/PHOTO").header("Authorization", tokenMembre))
                .andExpect(status().isOk());   // lecture autorisée à tout authentifié
        mvc.perform(multipart("/api/controleurs/CTRPHO2/pieces/PHOTO").header("Authorization", tokenMembre)
                .file(new MockMultipartFile("fichier", "p.png", "image/png", png)))
                .andExpect(status().isForbidden());   // dépôt : Admin uniquement
    }

    @Test
    @DisplayName("PUT /api/controleurs/{id} multipart : maj fiche + remplace photo ; photo absente inchangée ; JSON conservé ; inconnu → 404 ; PDF → 400")
    void controleur_modificationAvecPhoto() throws Exception {
        controleurRepository.save(controleur("CTRPUT", 6, "ANT"));
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] pdf = "%PDF-1.4 pas une image".getBytes(StandardCharsets.US_ASCII);
        byte[] data = "{\"imControleur\":\"CTRPUT\",\"idProfile\":6,\"transversal\":false,\"nomCont\":\"Apres\"}"
                .getBytes(StandardCharsets.UTF_8);

        // --- Écritures réussies d'abord. ---
        // PUT multipart : maj fiche + dépose la photo (JPEG). MockMvc : builder POST forcé en PUT.
        mvc.perform(multipart("/api/controleurs/CTRPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", jpeg))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomCont").value("Apres"));
        mvc.perform(get("/api/controleurs/CTRPUT/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));

        // PUT multipart SANS photo : fiche mise à jour, photo laissée inchangée.
        mvc.perform(multipart("/api/controleurs/CTRPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isOk());
        mvc.perform(get("/api/controleurs/CTRPUT/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));   // inchangée

        // PUT JSON pur (sans photo) → 200 (rétro-compat).
        mvc.perform(put("/api/controleurs/CTRPUT").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(new String(data, StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        // --- Cas d'erreur ensuite. ---
        // Contrôleur inconnu → 404.
        mvc.perform(multipart("/api/controleurs/INCONNU").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json",
                        "{\"imControleur\":\"INCONNU\",\"transversal\":false}".getBytes(StandardCharsets.UTF_8)))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isNotFound());

        // Photo = image seulement : un PDF → 400.
        mvc.perform(multipart("/api/controleurs/CTRPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("photo", "p.pdf", "application/pdf", pdf))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/controleurs/{id}/pieces/PHOTO : supprime la photo (contrôleur conservé) ; absente/inconnu → 404 ; type≠PHOTO → 400 ; non-admin → 403")
    void controleur_suppressionPhoto() throws Exception {
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] data = "{\"imControleur\":\"CTRDPH\",\"idProfile\":6,\"transversal\":false}"
                .getBytes(StandardCharsets.UTF_8);

        // --- Écritures / lectures 200 d'abord. ---
        // Création avec photo.
        mvc.perform(multipart("/api/controleurs").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated());
        // Suppression de la photo → 204.
        mvc.perform(delete("/api/controleurs/CTRDPH/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        // La photo est partie de la base ; le contrôleur, lui, subsiste.
        org.junit.jupiter.api.Assertions.assertTrue(pieceJointeRepository.findByLogin("CTRDPH").isEmpty());
        mvc.perform(get("/api/controleurs/CTRDPH").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());

        // --- Cas d'erreur ensuite. ---
        // Photo déjà absente → 404.
        mvc.perform(delete("/api/controleurs/CTRDPH/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // type ≠ PHOTO → 400.
        mvc.perform(delete("/api/controleurs/CTRDPH/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
        // Contrôleur inconnu → 404.
        mvc.perform(delete("/api/controleurs/INCONNU/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(delete("/api/controleurs/CTRDPH/pieces/PHOTO").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/controleurs/par-localite/{idLocalite} : contrôleurs affectés ; transversal (localité nulle) exclu ; inconnue → vide")
    void controleur_parLocalite() throws Exception {
        // Seed : CTRCC2 en TMS ; CTRPRE a une localité NULLE (transversal).
        mvc.perform(get("/api/controleurs/par-localite/TMS").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].imControleur", hasItem("CTRCC2")))
                .andExpect(jsonPath("$[?(@.imControleur=='CTRPRE')]", hasSize(0)));   // localité nulle → exclu

        // Localité sans contrôleur → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/controleurs/par-localite/ZZ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/controleurs/par-profil/{idProfile} : contrôleurs d'un profil ; profil inconnu → vide")
    void controleur_parProfil() throws Exception {
        // Seed : profil 3 (Chef de commission) = CTRCC1 (ANT) + CTRCC2 (TMS).
        mvc.perform(get("/api/controleurs/par-profil/3").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].imControleur", containsInAnyOrder("CTRCC1", "CTRCC2")));

        // Profil inexistant → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/controleurs/par-profil/99").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/controleurs/par-superieur/{imSuperieur} : subordonnés directs ; supérieur sans subordonné → vide")
    void controleur_parSuperieur() throws Exception {
        // Un subordonné dont le supérieur hiérarchique est CTRCC1.
        Controleur sub = controleur("CTRSUB", 5, "ANT");
        sub.setIdSuperieur("CTRCC1");
        controleurRepository.save(sub);

        mvc.perform(get("/api/controleurs/par-superieur/CTRCC1").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].imControleur", hasItem("CTRSUB")));

        // Contrôleur sans subordonné → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/controleurs/par-superieur/CTRSUB").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/controleurs/par-nom/{nom} : recherche partielle insensible à la casse ; aucun résultat → vide")
    void controleur_parNom() throws Exception {
        Controleur c = controleur("CTRNOM", 5, "ANT");
        c.setNomCont("RASOANAIVO");
        controleurRepository.save(c);

        // Partiel interne « soana ».
        mvc.perform(get("/api/controleurs/par-nom/soana").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].imControleur", hasItem("CTRNOM")));
        // Insensible à la casse : « RASOA ».
        mvc.perform(get("/api/controleurs/par-nom/RASOA").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].imControleur", hasItem("CTRNOM")));
        // Aucun résultat → liste vide (pas de 404).
        mvc.perform(get("/api/controleurs/par-nom/ZZQQ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("POST /api/controleurs/suppression-lot : tolérant → bilan supprimes/introuvables/bloques ; vide → 400 ; non-admin → 403")
    void controleur_suppressionLot() throws Exception {
        // Contrôleur « propre » (aucune activité) + compte. CTRMEM (seed) est membre de l'examen 1 → bloqué.
        controleurRepository.save(controleur("CTRLOT", 6, "ANT"));
        compteAuthRepository.save(new cnm.prs.entity.CompteAuth("ctrlot", "x",
                cnm.prs.enums.TypeActeur.CONTROLEUR.name(), "CTRLOT", true));

        mvc.perform(post("/api/controleurs/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"matricules\":[\"CTRLOT\",\"CTRMEM\",\"INCONNU\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supprimes", containsInAnyOrder("CTRLOT")))
                .andExpect(jsonPath("$.bloques", containsInAnyOrder("CTRMEM")))
                .andExpect(jsonPath("$.introuvables", containsInAnyOrder("INCONNU")));
        org.junit.jupiter.api.Assertions.assertFalse(controleurRepository.existsById("CTRLOT"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ctrlot").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(controleurRepository.existsById("CTRMEM"));   // bloqué, subsiste

        // Liste vide → 400.
        mvc.perform(post("/api/controleurs/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"matricules\":[]}"))
                .andExpect(status().isBadRequest());

        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(post("/api/controleurs/suppression-lot").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"matricules\":[\"CTRMEM\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/prmps/par-localite/{idLocalite} : PRMP via entités contractantes ACTIVES ; inactif exclu ; localité sans PRMP → vide")
    void prmp_parLocalite() throws Exception {
        // Seed : PRMP001 rattachée (ACTIVE) à l'entité 1 (ANT). On ajoute PRMPINA rattachée à ANT mais INACTIVE.
        prmpRepository.save(prmp("PRMPINA", "ANT"));
        entiteContractRepository.save(entite(951, 1, "ANT"));
        prmpEntiteRepository.save(prmpEntite(9510, "PRMPINA", 951, false));

        mvc.perform(get("/api/prmps/par-localite/ANT").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idPrmp", hasItem("PRMP001")))          // rattachement actif → présent
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMPINA')]", hasSize(0)));    // rattachement inactif → exclu

        // Localité sans aucune entité rattachée → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/prmps/par-localite/ZZ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/prmps/par-entite/{idEntiteContract} : PRMP via affectation ACTIVE (0 ou 1) ; inactive exclue ; entité sans PRMP → vide")
    void prmp_parEntite() throws Exception {
        // Seed : PRMP001 rattachée (ACTIVE) à l'entité 1. Affectation INACTIVE de PRMPINE à l'entité 952.
        mvc.perform(get("/api/prmps/par-entite/1").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idPrmp", containsInAnyOrder("PRMP001")));

        prmpRepository.save(prmp("PRMPINE", "ANT"));
        entiteContractRepository.save(entite(952, 1, "ANT"));
        prmpEntiteRepository.save(prmpEntite(9520, "PRMPINE", 952, false));   // inactive
        mvc.perform(get("/api/prmps/par-entite/952").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));   // affectation inactive → exclue

        // Entité sans affectation → vide.
        mvc.perform(get("/api/prmps/par-entite/888888").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/prmps/par-nom/{nom} : recherche partielle insensible à la casse ; aucun résultat → vide")
    void prmp_parNom() throws Exception {
        Prmp p = prmp("PRMPNOM", "ANT");
        p.setNomPrmp("RAKOTOARISOA");
        prmpRepository.save(p);

        // Partiel « AKOT » → trouve RAKOTOARISOA.
        mvc.perform(get("/api/prmps/par-nom/AKOT").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idPrmp", hasItem("PRMPNOM")));
        // Insensible à la casse : « rakoto ».
        mvc.perform(get("/api/prmps/par-nom/rakoto").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idPrmp", hasItem("PRMPNOM")));
        // Aucun résultat → liste vide (pas de 404).
        mvc.perform(get("/api/prmps/par-nom/ZZQQ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("POST /api/prmps multipart : fiche + pièces (optionnelles), GET pièce ; CIN > 5 Mo → 400 ; dépôt ultérieur ; non-admin → 403")
    void prmp_creationAvecPieces() throws Exception {
        byte[] dataJson = ("{\"idPrmp\":\"IMPCS\",\"nomPrmp\":\"Testy\",\"prenomsPrmp\":\"Piece\","
                + "\"arreteNomin\":\"ARR-1\",\"dateNomin\":\"2024-01-10\",\"cin\":\"301234567890\","
                + "\"dateCin\":\"2012-02-02\",\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"pc@cnm.mg\","
                + "\"telPrmp\":\"0331112233\"}").getBytes(StandardCharsets.UTF_8);
        byte[] pdf = "%PDF-1.4 arrete de nomination".getBytes(StandardCharsets.US_ASCII);   // %PDF (magic)
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };

        // Création multipart : data JSON + arrêté (PDF) + CIN (JPEG) ; photo omise (optionnelle).
        mvc.perform(multipart("/api/prmps").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", dataJson))
                .file(new MockMultipartFile("arrete", "arrete.pdf", "application/pdf", pdf))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPrmp").value("IMPCS"));

        // Téléchargement de l'arrêté stocké.
        mvc.perform(get("/api/prmps/IMPCS/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(get("/api/prmps/IMPCS/pieces/ARRETE_NOMIN").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());

        // Dépôt ultérieur d'une pièce (photo) sur une PRMP existante → puis téléchargeable.
        mvc.perform(multipart("/api/prmps/IMPCS/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "photo.png", "image/png", png)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/prmps/IMPCS/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());

        // CIN > 5 Mo → 400 (contrôle de taille au niveau service).
        byte[] gros = new byte[6 * 1024 * 1024];
        gros[0] = (byte) 0xFF; gros[1] = (byte) 0xD8; gros[2] = (byte) 0xFF;   // JPEG magic
        byte[] data2 = new String(dataJson, StandardCharsets.UTF_8).replace("IMPCS", "IMPCS2").getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/prmps").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data2))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", gros)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/prmps/{id}/pieces/{type} : supprime une pièce (PRMP conservée) ; autres pièces intactes ; absente/inconnu → 404 ; non-admin → 403")
    void prmp_suppressionPiece() throws Exception {
        byte[] data = ("{\"idPrmp\":\"IMPDP\",\"nomPrmp\":\"Testy\",\"prenomsPrmp\":\"Del\",\"arreteNomin\":\"ARR-1\","
                + "\"dateNomin\":\"2024-01-10\",\"cin\":\"301234567890\",\"dateCin\":\"2012-02-02\","
                + "\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"dp@cnm.mg\",\"telPrmp\":\"0331112233\"}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] pdf = "%PDF-1.4 arrete".getBytes(StandardCharsets.US_ASCII);
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };

        // --- Écritures / lectures 200 d'abord. ---
        // Création avec ARRETE_NOMIN (PDF) + CIN (JPEG).
        mvc.perform(multipart("/api/prmps").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("arrete", "arrete.pdf", "application/pdf", pdf))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated());
        // Suppression de l'arrêté → 204.
        mvc.perform(delete("/api/prmps/IMPDP/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        // La CIN subsiste, la PRMP aussi.
        mvc.perform(get("/api/prmps/IMPDP/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
        mvc.perform(get("/api/prmps/IMPDP").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
        // En base : il ne reste que la CIN sous la clé IMPDP.
        org.junit.jupiter.api.Assertions.assertEquals(1, pieceJointeRepository.findByLogin("IMPDP").size());

        // --- Cas d'erreur ensuite. ---
        // Arrêté désormais absent → 404 (téléchargement et re-suppression).
        mvc.perform(get("/api/prmps/IMPDP/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/prmps/IMPDP/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // PRMP inconnue → 404.
        mvc.perform(delete("/api/prmps/INCONNU/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(delete("/api/prmps/IMPDP/pieces/CIN").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/prmps/{id} : purge aussi les pièces (t_piece_jointe) — pas d'orphelin")
    void prmp_deleteFiche_purgePieces() throws Exception {
        byte[] data = ("{\"idPrmp\":\"IMPPG\",\"nomPrmp\":\"Testy\",\"prenomsPrmp\":\"Purge\",\"arreteNomin\":\"ARR-1\","
                + "\"dateNomin\":\"2024-01-10\",\"cin\":\"301234567890\",\"dateCin\":\"2012-02-02\","
                + "\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"pg@cnm.mg\",\"telPrmp\":\"0331112233\"}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        mvc.perform(multipart("/api/prmps").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(1, pieceJointeRepository.findByLogin("IMPPG").size());

        // DELETE de la fiche (aucune donnée liée) → 204 + pièces purgées.
        mvc.perform(delete("/api/prmps/IMPPG").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(pieceJointeRepository.findByLogin("IMPPG").isEmpty());
    }

    @Test
    @DisplayName("POST /api/prmps + credentials : compte PRMP actif + login immédiat ; sans → fiche seule ; login/idPrmp pris → 409 ; mdp manquant/<8 → 400")
    void prmp_creationAvecCompte() throws Exception {
        String base = "\"nomPrmp\":\"Testy\",\"prenomsPrmp\":\"Cpt\",\"arreteNomin\":\"ARR-1\",\"dateNomin\":\"2024-01-10\","
                + "\"cin\":\"301234567890\",\"dateCin\":\"2012-02-02\",\"lieuCin\":\"Antananarivo\","
                + "\"emailPrmp\":\"c@cnm.mg\",\"telPrmp\":\"0331112233\"";

        // Avec login + motDePasse → 201, fiche + compte PRMP actif.
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPCPT\"," + base + ",\"login\":\"imp.cpt\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPrmp").value("IMPCPT"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("imp.cpt").isPresent());
        // Connexion immédiate → rôle PRMP, ref = idPrmp.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"imp.cpt\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRMP"))
                .andExpect(jsonPath("$.ref").value("IMPCPT"));

        // Sans credentials → 201, fiche seule (aucun compte).
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPNC\"," + base + "}"))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertTrue(
                compteAuthRepository.findByRefActeurAndTypeActeur("IMPNC", "PRMP").isEmpty());

        // login déjà pris → 409 ; idPrmp déjà pris → 409.
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPX\"," + base + ",\"login\":\"imp.cpt\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPCPT\"," + base + "}"))
                .andExpect(status().isConflict());

        // login sans motDePasse → 400 ; motDePasse < 8 → 400.
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPY\"," + base + ",\"login\":\"imp.y\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPZ\"," + base + ",\"login\":\"imp.z\",\"motDePasse\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/prmps/{id} : PRMP sans données → 204 (+ compte supprimé) ; avec dossier → 409 ; inconnue → 404")
    void prmp_delete_gardeEtCompte() throws Exception {
        // PRMP « propre » (aucune donnée liée) + son compte d'authentification.
        prmpRepository.save(prmp("PRMPDEL", "ANT"));
        compteAuthRepository.save(new cnm.prs.entity.CompteAuth("prmpdel", "x",
                cnm.prs.enums.TypeActeur.PRMP.name(), "PRMPDEL", true));

        mvc.perform(delete("/api/prmps/PRMPDEL").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(prmpRepository.existsById("PRMPDEL"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("prmpdel").isEmpty());

        // PRMP avec un dossier lié → 409 (garde), la PRMP subsiste.
        prmpRepository.save(prmp("PRMPDEL2", "ANT"));
        Dossier d = dossier(970, "BROUILLON");
        d.setIdPrmp("PRMPDEL2");
        dossierRepository.save(d);
        mvc.perform(delete("/api/prmps/PRMPDEL2").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertTrue(prmpRepository.existsById("PRMPDEL2"));

        // Inconnue → 404.
        mvc.perform(delete("/api/prmps/INCONNU").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/prmps/suppression-lot : tolérant → bilan supprimes/introuvables/bloques ; vide → 400 ; non-admin → 403")
    void prmp_suppressionLot() throws Exception {
        // PRMP propre + compte ; PRMP avec un dossier lié (bloquée).
        prmpRepository.save(prmp("PRMPLOT1", "ANT"));
        compteAuthRepository.save(new cnm.prs.entity.CompteAuth("prmplot1", "x",
                cnm.prs.enums.TypeActeur.PRMP.name(), "PRMPLOT1", true));
        prmpRepository.save(prmp("PRMPLOT2", "ANT"));
        Dossier d = dossier(971, "BROUILLON");
        d.setIdPrmp("PRMPLOT2");
        dossierRepository.save(d);

        // Lot tolérant : 1 propre → supprimée, 1 à données liées → bloquée, 1 absente → introuvable.
        mvc.perform(post("/api/prmps/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"matricules\":[\"PRMPLOT1\",\"PRMPLOT2\",\"INCONNU\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supprimes", containsInAnyOrder("PRMPLOT1")))
                .andExpect(jsonPath("$.bloques", containsInAnyOrder("PRMPLOT2")))
                .andExpect(jsonPath("$.introuvables", containsInAnyOrder("INCONNU")));
        org.junit.jupiter.api.Assertions.assertFalse(prmpRepository.existsById("PRMPLOT1"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("prmplot1").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(prmpRepository.existsById("PRMPLOT2"));   // bloquée, subsiste

        // Liste vide → 400.
        mvc.perform(post("/api/prmps/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"matricules\":[]}"))
                .andExpect(status().isBadRequest());

        // Non-admin → 403 (le sous-chemin est sécurisé par @PreAuthorize).
        mvc.perform(post("/api/prmps/suppression-lot").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"matricules\":[\"PRMPLOT2\"]}"))
                .andExpect(status().isForbidden());
    }
}
