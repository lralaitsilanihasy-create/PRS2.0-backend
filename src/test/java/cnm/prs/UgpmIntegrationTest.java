package cnm.prs;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import cnm.prs.entity.Dossier;
import java.util.List;
import cnm.prs.entity.Capm;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Unites de gestion de la passation des marches (UGPM) : creation par l'Administrateur ou par
 * auto-inscription, pieces jointes, recherches, modification, suppression, et perimetre partage
 * avec la PRMP de tutelle.
 */
class UgpmIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("UGPM par tutelle — PRMP : ses propres unités (autre tutelle → 403) ; contrôleurs : toute tutelle, sans filtre de localité ; vue restreinte hors Administrateur")
    void ugpms_parTutelle_ouvertALaPrmpEtAuxControleurs() throws Exception {
        ugpmRepository.save(ugpm("UGPM011", "PRMP001", "Rabe", "Tiana"));
        compteAuthRepository.save(new CompteAuth("ugpm.tiana", passwordEncoder.encode("pw"), "UGPM", "UGPM011", true));
        prmpRepository.save(prmp("PRMP003", "ANT"));
        ugpmRepository.save(ugpm("UGPM012", "PRMP003", "Autre", "Unite"));

        // La PRMP consulte ses propres unités rattachées (plus de 403 silencieux à l'ouverture du modal).
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idUgpm=='UGPM011')]", hasSize(1)));
        // Mais pas celles d'une autre tutelle.
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP003").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());

        // ⚠️ 2026-08-20 — les contrôleurs qui instruisent les dossiers lisent TOUTE tutelle : ils
        // doivent savoir quelle unité a saisi le dossier examiné. Pas de filtre de localité (le
        // répertoire des PRMP est déjà national, et l'UGPM n'a pas de localité propre).
        String tokenVerif = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenSecretaire = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        String tokenAssistant = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        // Un CC d'une AUTRE localité (TMS) lit malgré tout la tutelle : c'est le cas qu'un filtre par
        // localité casserait (PRMP à cheval sur plusieurs localités via ses entités contractantes).
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        for (String jeton : List.of(tokenMembre, tokenVerif, tokenSecretaire, tokenAssistant, tokenCc, tokenCcTms, tokenPresident)) {
            mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", jeton))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.idUgpm=='UGPM011')]", hasSize(1)));
        }

        // Étendue des données — le contrôleur reçoit ce que l'écran affiche, et rien de plus :
        // ni pièce d'identité (état civil, sans usage pour l'instruction) ni login (identifiant
        // d'authentification). L'Administrateur, lui, garde la fiche complète.
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[0].nomUgpm").value("Rabe"))
                .andExpect(jsonPath("$[0].prenomsUgpm").value("Tiana"))
                .andExpect(jsonPath("$[0].idUgpm").value("UGPM011"))
                .andExpect(jsonPath("$[0].libelle").isNotEmpty())
                .andExpect(jsonPath("$[0].emailUgpm").isNotEmpty())
                .andExpect(jsonPath("$[0].telUgpm").isNotEmpty())
                .andExpect(jsonPath("$[0].cin").value(nullValue()))
                .andExpect(jsonPath("$[0].dateCin").value(nullValue()))
                .andExpect(jsonPath("$[0].lieuCin").value(nullValue()))
                .andExpect(jsonPath("$[0].login").value(nullValue()));
        // La PRMP elle-même passe par la même vue restreinte (l'écran n'affiche pas davantage).
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[0].cin").value(nullValue()))
                .andExpect(jsonPath("$[0].login").value(nullValue()));
        // Administrateur : accès inchangé, toutes tutelles, fiche complète (CIN + login).
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP003").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idUgpm=='UGPM012')]", hasSize(1)));
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[0].cin").value("303033334444"))
                .andExpect(jsonPath("$[0].login").value("ugpm.tiana"));

        // Le reste de la ressource demeure réservé à l'Administrateur : la liste complète notamment.
        mvc.perform(get("/api/ugpms").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        // Chargé de publication : hors du périmètre d'instruction, pas d'ouverture.
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001")
                .header("Authorization", bearer("CTRPUB", ProfilUtilisateur.CHARGE_PUBLICATION, TypeActeur.CONTROLEUR, "CTRPUB", "ANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("UGPM : crée un dossier sous sa PRMP de tutelle (cree_par=UGPM), ne peut PAS soumettre (403) ; la PRMP le voit et le soumet")
    void ugpm_creation_scoping_soumissionReserveePrmp() throws Exception {
        // Token UGPM : ref = PRMP001 (tutelle) → périmètre de la PRMP ; login « UGPM1 » = créateur.
        String tokenUgpm = bearer("UGPM1", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"X\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-UGPM\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":1000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenUgpm)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        // Dossier stampé PRMP de tutelle (PRMP001) + cree_par = login UGPM.
        Dossier d = dossierRepository.findById(idDoss).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("PRMP001", d.getIdPrmp());
        org.junit.jupiter.api.Assertions.assertEquals("UGPM1", d.getCreePar());

        // L'UGPM ne peut PAS soumettre → 403 (réservé PRMP).
        mvc.perform(post("/api/dossiers/" + idDoss + "/soumettre").header("Authorization", tokenUgpm))
                .andExpect(status().isForbidden());

        // La PRMP de tutelle voit le dossier (scoping périmètre) et le soumet → soumis_par = login PRMP.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + ")]", hasSize(1)));
        mvc.perform(post("/api/dossiers/" + idDoss + "/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals("PRMP001",
                dossierRepository.findById(idDoss).orElseThrow().getSoumisPar());
    }

    @Test
    @DisplayName("UGPM : voit et lit ce qu'elle a saisi sous sa tutelle (liste, détail, PPM, marché) ; "
            + "une UGPM d'une AUTRE tutelle reste hors périmètre (liste vide, 403)")
    void ugpm_lecture_partageLePerimetreDeSaTutelle() throws Exception {
        // ⚠️ Correctif 2026-08-26 — la claim « ref » de l'UGPM porte l'ID_PRMP de TUTELLE et sa localité
        // est nulle. Les scopings qui testaient « profil == PRMP » à la main excluaient l'UGPM : elle
        // retombait sur la branche localité → liste vide / 403 sur ce qu'elle venait elle-même de saisir.
        String tokenUgpm = bearer("UGPM1", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"X\",\"dateSignature\":\"2026-01-10\","
                + "\"reference\":\"PPM-UGPM-LECTURE\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":1000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenUgpm)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        Integer idPpm = ppmRepository.findByIdDossier(idDoss).stream().findFirst().orElseThrow().getIdPpm();
        Integer idMarche = marcheRepository.findByIdPpm(idPpm).get(0).getIdDetail();

        // Liste des dossiers : l'UGPM voit ce qu'elle vient de saisir (périmètre = celui de sa tutelle).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + ")]", hasSize(1)));
        // Détail du dossier, PPM rattaché, PPM lu directement, marché : 200, jamais 403.
        mvc.perform(get("/api/dossiers/" + idDoss).header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDossier").value(idDoss));
        mvc.perform(get("/api/dossiers/" + idDoss + "/ppm").header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPpm").value(idPpm));
        mvc.perform(get("/api/ppms/" + idPpm).header("Authorization", tokenUgpm))
                .andExpect(status().isOk());
        mvc.perform(get("/api/marches/" + idMarche).header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPpm").value(idPpm));
        mvc.perform(get("/api/marches").header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDetail==" + idMarche + ")]", hasSize(1)));
        // « Mes PPM & marchés » exclut les brouillons par construction (findVisiblesParPrmp) : une fois
        // le dossier soumis par la PRMP de tutelle, l'UGPM le suit dans cette liste comme sa tutelle.
        mvc.perform(get("/api/ppms").header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPpm==" + idPpm + ")]", hasSize(0)));
        mvc.perform(post("/api/dossiers/" + idDoss + "/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        mvc.perform(get("/api/ppms").header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPpm==" + idPpm + ")]", hasSize(1)));

        // Une UGPM d'une AUTRE tutelle ne voit rien de ce périmètre : ni la liste, ni le détail.
        prmpRepository.save(prmp("PRMP003", "ANT"));
        String tokenUgpmAutre = bearer("UGPM2", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP003", null);
        mvc.perform(get("/api/dossiers").header("Authorization", tokenUgpmAutre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + ")]", hasSize(0)));
        mvc.perform(get("/api/dossiers/" + idDoss).header("Authorization", tokenUgpmAutre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ppms/" + idPpm).header("Authorization", tokenUgpmAutre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/marches/" + idMarche).header("Authorization", tokenUgpmAutre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin crée une UGPM + compte actif ; login UGPM → rôle UGPM, périmètre = PRMP de tutelle ; tutelle inconnue → 409")
    void ugpm_admin_creation_et_login() throws Exception {
        // Identité obligatoire (mêmes champs que la PRMP, sauf arrêté/date de nomination).
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean Paul\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMX\",\"libelle\":\"UGPM Test\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugpmx\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPrmpTutelle").value("PRMP001"))
                .andExpect(jsonPath("$.nomUgpm").value("Rakoto"))
                .andExpect(jsonPath("$.dateCin").value("2010-05-20"))
                .andExpect(jsonPath("$.emailUgpm").value("ugpm@ex.mg"))
                .andExpect(jsonPath("$.login").value("ugpmx"));   // login exposé (lecture seule)
        org.junit.jupiter.api.Assertions.assertTrue(ugpmRepository.existsById("UGPMX"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpmx").isPresent());

        // Login réel → rôle UGPM, ref = PRMP de tutelle (le scoping fonctionne comme une PRMP).
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ugpmx\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("UGPM"))
                .andExpect(jsonPath("$.ref").value("PRMP001"));

        // PRMP de tutelle inconnue → 409.
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMY\",\"libelle\":\"X\",\"idPrmpTutelle\":\"NOPE\"," + identite
                        + "\"login\":\"ugpmy\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/ugpms multipart : fiche + pièces CIN/PHOTO (pas d'arrêté) ; GET pièce ; CIN>5Mo/ARRETE/photo-PDF → 400 ; non-admin → 403")
    void ugpm_creationAvecPieces() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        byte[] dataJson = ("{\"idUgpm\":\"UGPJ\",\"libelle\":\"UGPM Pieces\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                + "\"login\":\"ugpj\",\"motDePasse\":\"Ugpm@1234\"}").getBytes(StandardCharsets.UTF_8);
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };
        byte[] pdf = "%PDF-1.4 pas une image".getBytes(StandardCharsets.US_ASCII);

        // --- Écritures/lectures réussies d'abord (une exception métier marque la tx rollback-only). ---
        // Création multipart : data JSON + CIN (JPEG) + photo (PNG). Pas d'arrêté.
        mvc.perform(multipart("/api/ugpms").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", dataJson))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg))
                .file(new MockMultipartFile("photo", "photo.png", "image/png", png)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idUgpm").value("UGPJ"));

        // JSON pur (sans pièces) → 201 (rétro-compat).
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPJ2\",\"libelle\":\"X\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugpj2\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        // Téléchargement des pièces stockées (CIN + PHOTO).
        mvc.perform(get("/api/ugpms/UGPJ/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
        mvc.perform(get("/api/ugpms/UGPJ/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        // --- Cas d'erreur ensuite. ---
        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(get("/api/ugpms/UGPJ/pieces/CIN").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());

        // Pièce absente → 404.
        mvc.perform(get("/api/ugpms/INCONNU/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());

        // type = ARRETE_NOMIN → 400 (l'UGPM n'a pas d'arrêté), au dépôt comme au téléchargement.
        mvc.perform(multipart("/api/ugpms/UGPJ/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "a.pdf", "application/pdf", pdf)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/ugpms/UGPJ/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());

        // Photo = image seulement : un PDF en PHOTO → 400.
        mvc.perform(multipart("/api/ugpms/UGPJ/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "p.pdf", "application/pdf", pdf)))
                .andExpect(status().isBadRequest());

        // CIN > 5 Mo → 400 (contrôle de taille au niveau service).
        byte[] gros = new byte[6 * 1024 * 1024];
        gros[0] = (byte) 0xFF; gros[1] = (byte) 0xD8; gros[2] = (byte) 0xFF;   // JPEG magic
        byte[] data3 = ("{\"idUgpm\":\"UGPJ3\",\"libelle\":\"X\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                + "\"login\":\"ugpj3\",\"motDePasse\":\"Ugpm@1234\"}").getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/ugpms").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data3))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", gros)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/ugpms/{id}/pieces/{type} : supprime une pièce (UGPM conservée) ; PHOTO intacte ; absente/inconnu → 404 ; ARRETE_NOMIN → 400 ; non-admin → 403")
    void ugpm_suppressionPiece() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        byte[] data = ("{\"idUgpm\":\"UGPDP\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                + "\"login\":\"ugpdp\",\"motDePasse\":\"Ugpm@1234\"}").getBytes(StandardCharsets.UTF_8);
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };

        // --- Écritures / lectures 200 d'abord. ---
        // Création avec CIN + PHOTO.
        mvc.perform(multipart("/api/ugpms").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg))
                .file(new MockMultipartFile("photo", "photo.png", "image/png", png)))
                .andExpect(status().isCreated());
        // Suppression de la CIN → 204.
        mvc.perform(delete("/api/ugpms/UGPDP/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        // La PHOTO subsiste, l'UGPM aussi.
        mvc.perform(get("/api/ugpms/UGPDP/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/ugpms/UGPDP").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
        // En base : il ne reste que la PHOTO sous la clé UGPDP.
        org.junit.jupiter.api.Assertions.assertEquals(1, pieceJointeRepository.findByLogin("UGPDP").size());

        // --- Cas d'erreur ensuite. ---
        // CIN désormais absente → 404 (téléchargement et re-suppression).
        mvc.perform(get("/api/ugpms/UGPDP/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/ugpms/UGPDP/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // type = ARRETE_NOMIN → 400 (l'UGPM n'a pas d'arrêté).
        mvc.perform(delete("/api/ugpms/UGPDP/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
        // UGPM inconnue → 404.
        mvc.perform(delete("/api/ugpms/INCONNU/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(delete("/api/ugpms/UGPDP/pieces/PHOTO").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/ugpms/{id} : purge aussi les pièces (t_piece_jointe) — pas d'orphelin")
    void ugpm_deleteFiche_purgePieces() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Purge\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        byte[] data = ("{\"idUgpm\":\"UGPPG\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                + "\"login\":\"ugppg\",\"motDePasse\":\"Ugpm@1234\"}").getBytes(StandardCharsets.UTF_8);
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        mvc.perform(multipart("/api/ugpms").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(1, pieceJointeRepository.findByLogin("UGPPG").size());

        // DELETE de la fiche → 204 + pièces purgées (et compte retiré, déjà couvert ailleurs).
        mvc.perform(delete("/api/ugpms/UGPPG").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(pieceJointeRepository.findByLogin("UGPPG").isEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/register/ugpm : auto-inscription publique EN_ATTENTE ; login refusé avant validation ; validée par l'Admin → login OK (UGPM) ; GET /api/auth/prmps public ; tutelle inconnue / déjà pris → 409")
    void ugpm_autoInscription() throws Exception {
        // GET /api/auth/prmps (public, sans token) → contient la PRMP001 du seed.
        mvc.perform(get("/api/auth/prmps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')]", hasSize(1)));

        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        String data = "{\"login\":\"ugpm.reg\",\"motDePasse\":\"Ugpm@1234\",\"idUgpm\":\"UGPREG\","
                + "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\",\"cin\":\"101234567890\","
                + "\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\",\"emailUgpm\":\"ugpm.reg@ex.mg\","
                + "\"telUgpm\":\"0340000000\",\"idPrmpTutelle\":\"PRMP001\"}";
        MockMultipartFile dataPart = new MockMultipartFile("data", "", "application/json",
                data.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile cin = new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg);

        // Auto-inscription publique (sans token) → 201, compte EN_ATTENTE.
        mvc.perform(multipart("/api/auth/register/ugpm").file(dataPart).file(cin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeActeur").value("UGPM"))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.actif").value(false));

        // Connexion refusée avant validation → 401.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ugpm.reg\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isUnauthorized());

        // Visible par l'Admin dans les inscriptions en attente (type UGPM + tutelle).
        mvc.perform(get("/api/inscriptions/en-attente").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.login=='ugpm.reg')].type", hasItem("UGPM")))
                .andExpect(jsonPath("$[?(@.login=='ugpm.reg')].idPrmpTutelle", hasItem("PRMP001")));

        // Validation par l'Admin (pas d'entités à instruire) → compte activé.
        mvc.perform(post("/api/inscriptions/ugpm.reg/valider").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutCompte").value("ACTIF"));

        // Login OK maintenant → rôle UGPM, ref = PRMP de tutelle.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ugpm.reg\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("UGPM"))
                .andExpect(jsonPath("$.ref").value("PRMP001"));

        // Pièces ré-affectées à la validation : accessibles par l'id (UGPREG), plus par le login.
        mvc.perform(get("/api/ugpms/UGPREG/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
        org.junit.jupiter.api.Assertions.assertTrue(pieceJointeRepository.findByLogin("ugpm.reg").isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(1, pieceJointeRepository.findByLogin("UGPREG").size());

        // --- Cas d'erreur ensuite. ---
        // Tutelle inconnue → 409.
        String dataNope = data.replace("UGPREG", "UGPRG2").replace("ugpm.reg", "ugpm.rg2")
                .replace("\"idPrmpTutelle\":\"PRMP001\"", "\"idPrmpTutelle\":\"NOPE\"");
        mvc.perform(multipart("/api/auth/register/ugpm")
                .file(new MockMultipartFile("data", "", "application/json", dataNope.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isConflict());
        // login déjà pris → 409 (réutilise ugpm.reg).
        mvc.perform(multipart("/api/auth/register/ugpm").file(dataPart).file(cin))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/ugpms/{id} : lit une UGPM (identité) ; id inconnu → 404")
    void ugpm_findById() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMG\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugpmg\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/ugpms/UGPMG").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUgpm").value("UGPMG"))
                .andExpect(jsonPath("$.idPrmpTutelle").value("PRMP001"))
                .andExpect(jsonPath("$.nomUgpm").value("Rakoto"))
                .andExpect(jsonPath("$.emailUgpm").value("ugpm@ex.mg"))
                .andExpect(jsonPath("$.login").value("ugpmg"));   // login exposé (lecture seule)

        // Id inconnu → 404.
        mvc.perform(get("/api/ugpms/INCONNU").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/ugpms/par-tutelle/{idPrmp} : liste les UGPM d'une PRMP ; tutelle inconnue → liste vide")
    void ugpm_parTutelle() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        for (String im : new String[] { "UGPMT1", "UGPMT2" }) {
            mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idUgpm\":\"" + im + "\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                            + "\"login\":\"" + im.toLowerCase() + "\",\"motDePasse\":\"Ugpm@1234\"}"))
                    .andExpect(status().isCreated());
        }

        // Les 2 UGPM de PRMP001 (matricules attendus, login exposé).
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idUgpm", containsInAnyOrder("UGPMT1", "UGPMT2")))
                .andExpect(jsonPath("$[?(@.idUgpm=='UGPMT1')].idPrmpTutelle", containsInAnyOrder("PRMP001")));

        // PRMP de tutelle inconnue → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/ugpms/par-tutelle/NOPE").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/ugpms/par-localite/{idLocalite} : UGPM via la localité de leur PRMP de tutelle ; localité sans PRMP → vide ; non-admin → 403")
    void ugpm_parLocalite() throws Exception {
        // PRMP001 est rattachée (ACTIVE) à ANT via le seed. Une UGPM sous PRMP001 hérite donc de ANT.
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPLOCA\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugploca\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        // par-localite ANT → contient l'UGPM (via la localité de sa PRMP de tutelle).
        mvc.perform(get("/api/ugpms/par-localite/ANT").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idUgpm", hasItem("UGPLOCA")));

        // Localité sans PRMP rattachée → liste vide (filtre) : l'UGPM existante n'y fuit pas.
        mvc.perform(get("/api/ugpms/par-localite/ZZ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Non-admin → 403.
        mvc.perform(get("/api/ugpms/par-localite/ANT").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/ugpms/par-nom/{nom} : recherche partielle insensible à la casse ; aucun résultat → vide ; non-admin → 403")
    void ugpm_parNom() throws Exception {
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPNOM\",\"idPrmpTutelle\":\"PRMP001\","
                        + "\"nomUgpm\":\"RANDRIANARISOA\",\"prenomsUgpm\":\"Jean\","
                        + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                        + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\","
                        + "\"login\":\"ugpnom\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        // Partiel « NDRIA » → trouve RANDRIANARISOA.
        mvc.perform(get("/api/ugpms/par-nom/NDRIA").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idUgpm", hasItem("UGPNOM")));
        // Insensible à la casse : « randria ».
        mvc.perform(get("/api/ugpms/par-nom/randria").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idUgpm", hasItem("UGPNOM")));
        // Aucun résultat → liste vide (pas de 404).
        mvc.perform(get("/api/ugpms/par-nom/ZZQQ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        // Non-admin → 403.
        mvc.perform(get("/api/ugpms/par-nom/RANDRIA").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/ugpms/suppression-lot : tolérant → bilan supprimes/introuvables (+ comptes nettoyés) ; liste vide → 400")
    void ugpm_suppressionLot() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        for (String im : new String[] { "UGPML1", "UGPML2" }) {
            mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idUgpm\":\"" + im + "\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                            + "\"login\":\"" + im.toLowerCase() + "\",\"motDePasse\":\"Ugpm@1234\"}"))
                    .andExpect(status().isCreated());
        }

        // Lot tolérant : 2 existantes + 1 absente → 200, bilan, pas d'échec global.
        mvc.perform(post("/api/ugpms/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"matricules\":[\"UGPML1\",\"UGPML2\",\"INCONNU\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supprimes", containsInAnyOrder("UGPML1", "UGPML2")))
                .andExpect(jsonPath("$.introuvables", containsInAnyOrder("INCONNU")));
        // UGPM + comptes supprimés.
        org.junit.jupiter.api.Assertions.assertFalse(ugpmRepository.existsById("UGPML1"));
        org.junit.jupiter.api.Assertions.assertFalse(ugpmRepository.existsById("UGPML2"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpml1").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpml2").isEmpty());

        // Liste vide → 400 (validation @NotEmpty).
        mvc.perform(post("/api/ugpms/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"matricules\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/ugpms/{id} : modifie les champs métier ; id inconnu → 404 ; tutelle inconnue → 409")
    void ugpm_modifier() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMM\",\"libelle\":\"Avant\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugpmm\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        // Modification des champs métier (libellé + identité).
        String modif = "{\"libelle\":\"Apres\",\"idPrmpTutelle\":\"PRMP001\",\"nomUgpm\":\"Randria\","
                + "\"prenomsUgpm\":\"Paul\",\"cin\":\"101234567890\",\"dateCin\":\"2011-06-21\","
                + "\"lieuCin\":\"Toamasina\",\"emailUgpm\":\"ugpm.new@ex.mg\",\"telUgpm\":\"0341112222\"}";
        mvc.perform(put("/api/ugpms/UGPMM").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(modif))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUgpm").value("UGPMM"))       // PK inchangée
                .andExpect(jsonPath("$.libelle").value("Apres"))
                .andExpect(jsonPath("$.nomUgpm").value("Randria"))
                .andExpect(jsonPath("$.emailUgpm").value("ugpm.new@ex.mg"))
                .andExpect(jsonPath("$.lieuCin").value("Toamasina"))
                .andExpect(jsonPath("$.login").value("ugpmm"));      // login inchangé, exposé (lecture seule)

        // Le compte n'est pas touché par la modification.
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpmm").isPresent());

        // Id inconnu → 404.
        mvc.perform(put("/api/ugpms/INCONNU").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(modif))
                .andExpect(status().isNotFound());

        // Tutelle inconnue → 409.
        String modifNope = modif.replace("\"idPrmpTutelle\":\"PRMP001\"", "\"idPrmpTutelle\":\"NOPE\"");
        mvc.perform(put("/api/ugpms/UGPMM").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(modifNope))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /api/ugpms/{id} multipart : maj identité + remplace pièces ; pièce absente inchangée ; JSON conservé ; inconnu → 404 ; photo PDF → 400")
    void ugpm_modificationAvecPieces() throws Exception {
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPPUT\",\"libelle\":\"Avant\",\"idPrmpTutelle\":\"PRMP001\","
                        + "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\",\"cin\":\"101234567890\","
                        + "\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\",\"emailUgpm\":\"ugpm@ex.mg\","
                        + "\"telUgpm\":\"0340000000\",\"login\":\"ugpput\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };
        byte[] pdf = "%PDF-1.4 pas une image".getBytes(StandardCharsets.US_ASCII);
        byte[] data = ("{\"libelle\":\"Apres\",\"idPrmpTutelle\":\"PRMP001\",\"nomUgpm\":\"Randria\","
                + "\"prenomsUgpm\":\"Paul\",\"cin\":\"101234567890\",\"dateCin\":\"2011-06-21\","
                + "\"lieuCin\":\"Toamasina\",\"emailUgpm\":\"ugpm.new@ex.mg\",\"telUgpm\":\"0341112222\"}")
                .getBytes(StandardCharsets.UTF_8);

        // --- Écritures réussies d'abord. ---
        // PUT multipart : maj identité + dépose CIN (JPEG) + PHOTO (PNG). MockMvc : builder POST forcé en PUT.
        mvc.perform(multipart("/api/ugpms/UGPPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg))
                .file(new MockMultipartFile("photo", "photo.png", "image/png", png))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Apres"))
                .andExpect(jsonPath("$.nomUgpm").value("Randria"))
                .andExpect(jsonPath("$.emailUgpm").value("ugpm.new@ex.mg"));
        mvc.perform(get("/api/ugpms/UGPPUT/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
        mvc.perform(get("/api/ugpms/UGPPUT/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));

        // PUT multipart avec SEULEMENT la CIN (PNG) : CIN remplacée, PHOTO laissée inchangée.
        mvc.perform(multipart("/api/ugpms/UGPPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("cin", "cin.png", "image/png", png))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isOk());
        mvc.perform(get("/api/ugpms/UGPPUT/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));   // remplacée
        mvc.perform(get("/api/ugpms/UGPPUT/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));   // inchangée

        // PUT JSON pur (sans pièces) → 200 (rétro-compat).
        mvc.perform(put("/api/ugpms/UGPPUT").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(new String(data, StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        // --- Cas d'erreur ensuite. ---
        // UGPM inconnue → 404.
        mvc.perform(multipart("/api/ugpms/INCONNU").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isNotFound());

        // Photo = image seulement : un PDF en PHOTO → 400.
        mvc.perform(multipart("/api/ugpms/UGPPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("photo", "p.pdf", "application/pdf", pdf))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/ugpms/{id} : supprime l'UGPM et son compte ; id inconnu → 404")
    void ugpm_delete() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMD\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugpmd\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertTrue(ugpmRepository.existsById("UGPMD"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpmd").isPresent());

        mvc.perform(delete("/api/ugpms/UGPMD").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        // UGPM et compte associé supprimés.
        org.junit.jupiter.api.Assertions.assertFalse(ugpmRepository.existsById("UGPMD"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpmd").isEmpty());

        // Id inconnu → 404.
        mvc.perform(delete("/api/ugpms/INCONNU").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("UGPM : création sans champ d'identité obligatoire (nomUgpm) → 400")
    void creation_ugpm_sans_identite_400() throws Exception {
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMI\",\"idPrmpTutelle\":\"PRMP001\",\"prenomsUgpm\":\"Jean\","
                        + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\","
                        + "\"lieuCin\":\"Antananarivo\",\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\","
                        + "\"login\":\"ugpmi\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("UGPM : création sans PRMP de tutelle → 400 (validation)")
    void creation_ugpm_sans_prmp_tutelle_400() throws Exception {
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMZ\",\"libelle\":\"X\",\"login\":\"ugpmz\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PRMP : sa liste de dossiers inclut les BROUILLON créés par ses UGPM")
    void prmp_voit_brouillons_de_ses_ugpm() throws Exception {
        // UGPM (ref = PRMP001) crée un dossier BROUILLON → stampé PRMP001.
        String tokenUgpm = bearer("UGPM1", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);
        String resp = mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenUgpm)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DAO\",\"idEntiteContract\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        org.junit.jupiter.api.Assertions.assertEquals("UGPM1",
                dossierRepository.findById(idDoss).orElseThrow().getCreePar());

        // La PRMP de tutelle voit ce BROUILLON dans sa liste (scoping par périmètre ID_PRMP).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.statut=='BROUILLON')]", hasSize(1)));
    }
}
