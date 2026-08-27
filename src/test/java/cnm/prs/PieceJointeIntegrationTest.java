package cnm.prs;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import cnm.prs.dto.PieceJointeMetaDto;
import cnm.prs.entity.Avis;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.TypeDossier;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.exception.BadRequestException;

/**
 * Pieces jointes par type de dossier : referentiel tr_type_piece_jointe, depot (magic-bytes,
 * formats autorises), pieces obligatoires a la soumission, depot apres lettre de renvoi et
 * telechargement du contenu.
 */
class PieceJointeIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Pièces jointes : stockage PDF (magic-bytes), remplacement par type, rejet d'un type non autorisé")
    void pieceJointe_stockageRemplacementRejet() throws Exception {
        byte[] pdf = "%PDF-1.4 contenu arrete".getBytes(StandardCharsets.US_ASCII);
        PieceJointeMetaDto meta = pieceJointeService.stocker("PRMP001", TypePieceJointe.ARRETE_NOMIN,
                new MockMultipartFile("arrete", "arrete.pdf", "application/pdf", pdf));
        assertTrue("application/pdf".equals(meta.format()), "format PDF détecté par magic-bytes");
        assertTrue(meta.hashSha256() != null && meta.hashSha256().length() == 64, "SHA-256 calculé");

        // Re-dépôt du même type → remplacement (le contenu récupéré est le plus récent).
        byte[] pdf2 = "%PDF-1.7 version corrigee".getBytes(StandardCharsets.US_ASCII);
        pieceJointeService.stocker("PRMP001", TypePieceJointe.ARRETE_NOMIN,
                new MockMultipartFile("arrete", "arrete2.pdf", "application/pdf", pdf2));
        byte[] recupere = pieceJointeService.telecharger("PRMP001", TypePieceJointe.ARRETE_NOMIN).getContenu();
        assertTrue(new String(recupere, StandardCharsets.US_ASCII).contains("version corrigee"),
                "le dernier dépôt remplace le précédent");

        // Type non autorisé (texte brut) → 400 (magic-bytes non reconnus).
        assertThrows(BadRequestException.class, () -> pieceJointeService.stocker("PRMP001",
                TypePieceJointe.CIN, new MockMultipartFile("cin", "cin.txt", "text/plain",
                        "ceci n'est pas une image".getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    @DisplayName("Référentiel pièces jointes : CRUD par l'Administrateur (201/200/204) + filtre ?typeDossier")
    void type_piece_crud_admin_ok() throws Exception {
        // Création.
        String body = "{\"libellePiece\":\"Plan de passation\",\"obligatoire\":true,"
                + "\"idTypeDossier\":\"DDP\",\"ordre\":1}";
        String json = mvc.perform(post("/api/type-piece-jointes").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypePiece").isNumber())
                .andExpect(jsonPath("$.libellePiece").value("Plan de passation"))
                .andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.parse(json).read("$.idTypePiece");

        // Mise à jour.
        String maj = "{\"libellePiece\":\"Plan de passation des marchés\",\"obligatoire\":false,"
                + "\"idTypeDossier\":\"DDP\",\"ordre\":2}";
        mvc.perform(put("/api/type-piece-jointes/" + id).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(maj))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libellePiece").value("Plan de passation des marchés"))
                .andExpect(jsonPath("$.obligatoire").value(false));

        // Filtre par type de dossier (authentifié).
        mvc.perform(get("/api/type-piece-jointes?typeDossier=DDP").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idTypePiece==" + id + ")]", hasSize(1)));

        // Suppression.
        mvc.perform(delete("/api/type-piece-jointes/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Référentiel pièces jointes : écriture interdite à un non-Administrateur (403)")
    void type_piece_non_admin_403() throws Exception {
        String body = "{\"libellePiece\":\"X\",\"obligatoire\":true,\"idTypeDossier\":\"PPM\",\"ordre\":1}";
        mvc.perform(post("/api/type-piece-jointes").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Upload pièce à la création : PRMP propriétaire, magic-bytes PDF → 201, apresLettreRenvoi=false")
    void piece_upload_creation_ok() throws Exception {
        Dossier d = dossier(140, "BROUILLON"); d.setIdTypeDossier("DDP"); d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT"); dossierRepository.save(d);
        int type = seedTypePiece("Plan de passation", true, "DDP",1);

        byte[] pdf = "%PDF-1.4 contenu plan".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart("/api/piece-jointe-dossiers")
                .file(new MockMultipartFile("data", "", "application/json",
                        ("{\"idDossier\":140,\"idTypePiece\":" + type + "}").getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("fichier", "plan.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.format").value("PDF"))
                .andExpect(jsonPath("$.apresLettreRenvoi").value(false))
                .andExpect(jsonPath("$.libellePiece").value("Plan de passation"));

        mvc.perform(get("/api/piece-jointe-dossiers?dossier=140").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Upload pièce : format non autorisé (.docx) → 400")
    void piece_upload_format_invalide_400() throws Exception {
        Dossier d = dossier(141, "BROUILLON"); d.setIdTypeDossier("DDP"); d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT"); dossierRepository.save(d);
        int type = seedTypePiece("Plan de passation", true, "DDP",1);

        byte[] docx = "PK ceci est un .docx".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart("/api/piece-jointe-dossiers")
                .file(new MockMultipartFile("data", "", "application/json",
                        ("{\"idDossier\":141,\"idTypePiece\":" + type + "}").getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("fichier", "plan.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Upload pièce après lettre de renvoi (dossier SOUMIS + idLettre) → 201, apresLettreRenvoi=true")
    void piece_upload_apres_lettre_ok() throws Exception {
        Dossier d = dossier(142, "SOUMIS"); d.setIdTypeDossier("DDP"); d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT"); dossierRepository.save(d);
        int type = seedTypePiece("Avis de non-objection", false, "DDP",1);
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1); l.setIdDossier(142); l.setObjetLettre("Renvoi"); l.setStatut("SIGNE");
        int idLettre = lettreRenvoiRepository.save(l).getIdLettre();

        byte[] pdf = "%PDF-1.5 piece complementaire".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart("/api/piece-jointe-dossiers")
                .file(new MockMultipartFile("data", "", "application/json",
                        ("{\"idDossier\":142,\"idTypePiece\":" + type + ",\"idLettre\":" + idLettre + "}")
                                .getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("fichier", "complement.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apresLettreRenvoi").value(true))
                .andExpect(jsonPath("$.idLettre").value(idLettre));
    }

    @Test
    @DisplayName("Soumission : pièce obligatoire manquante → 400 {champ:piecesJointes}")
    void piece_obligatoire_manquante_400() throws Exception {
        Dossier d = dossier(143, "BROUILLON"); d.setRefeDossier(null); d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT"); dossierRepository.save(d);
        Ppm ppm = ppmLocalise(43, 143, "ANT"); ppm.setIdPrmp("PRMP001"); ppmRepository.save(ppm);
        marcheRepository.save(marche(431, 143, 43));
        seedTypePiece("Plan de passation des marchés", true, "DDP",1); // obligatoire, non fournie

        mvc.perform(post("/api/dossiers/143/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("piecesJointes"))
                .andExpect(jsonPath("$.erreurs[0].message")
                        .value("La pièce 'Plan de passation des marchés' est obligatoire."));
    }

    @Test
    @DisplayName("Téléchargement du contenu d'une pièce → 200 + octets identiques")
    void piece_download_ok() throws Exception {
        Dossier d = dossier(144, "BROUILLON"); d.setIdTypeDossier("DDP"); d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT"); dossierRepository.save(d);
        int type = seedTypePiece("Plan de passation", true, "DDP",1);

        byte[] pdf = "%PDF-1.6 contenu a telecharger".getBytes(StandardCharsets.US_ASCII);
        String created = mvc.perform(multipart("/api/piece-jointe-dossiers")
                .file(new MockMultipartFile("data", "", "application/json",
                        ("{\"idDossier\":144,\"idTypePiece\":" + type + "}").getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("fichier", "plan.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idPiece = com.jayway.jsonpath.JsonPath.parse(created).read("$.idPiece");

        byte[] recupere = mvc.perform(get("/api/piece-jointe-dossiers/" + idPiece + "/contenu")
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(java.util.Arrays.equals(pdf, recupere), "le contenu téléchargé est identique à l'envoyé");
    }

    @Test
    @DisplayName("Référentiel pièces jointes : 5 pièces pour le type PPM (filtre ?typeDossier=PPM)")
    void type_piece_ppm_liste_ok() throws Exception {
        seedReferentielPieces();
        mvc.perform(get("/api/type-piece-jointes?typeDossier=DDP").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    @DisplayName("Référentiel pièces jointes : 8 pièces pour le type DAO (filtre ?typeDossier=DAO)")
    void type_piece_dao_liste_ok() throws Exception {
        seedReferentielPieces();
        mvc.perform(get("/api/type-piece-jointes?typeDossier=DMC").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));
    }

    @Test
    @DisplayName("Référentiel pièces jointes : 7 pièces pour le type MAOO (filtre ?typeDossier=MAOO)")
    void type_piece_maoo_liste_ok() throws Exception {
        seedReferentielPieces();
        mvc.perform(get("/api/type-piece-jointes?typeDossier=DDM").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)));
    }

    @Test
    @DisplayName("Saisie PPM (multipart) : pièce obligatoire absente → 400 {champ:piecesJointes}")
    void creation_sans_piece_obligatoire_400() throws Exception {
        seedTypePiece("Plan de passation des marchés signé", true, "DDP",1); // obligatoire
        int opt = seedTypePiece("Avis de non-objection (si requis)", false, "DDP",2); // optionnelle

        // On fournit uniquement la pièce optionnelle : l'obligatoire manque → 400.
        byte[] pdf = "%PDF-1.4 avis".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart("/api/saisies/ppm")
                .file(new MockMultipartFile("data", "", "application/json",
                        "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\"}"
                                .getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("piece_" + opt, "avis.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("piecesJointes"))
                .andExpect(jsonPath("$.erreurs[0].message")
                        .value("La pièce 'Plan de passation des marchés signé' est obligatoire."));

        // Aucune création persistée (validation avant persistance).
        mvc.perform(get("/api/dossiers?statut=BROUILLON").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idTypeDossier=='PPM')]", hasSize(0)));
    }

    @Test
    @DisplayName("Saisie PPM (multipart) : toutes les pièces obligatoires fournies → 201")
    void creation_avec_toutes_pieces_ok() throws Exception {
        int oblig = seedTypePiece("Plan de passation des marchés signé", true, "DDP",1);
        int opt = seedTypePiece("Avis de non-objection (si requis)", false, "DDP",2);

        byte[] pdf = "%PDF-1.4 piece".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart("/api/saisies/ppm")
                .file(new MockMultipartFile("data", "", "application/json",
                        "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\"}"
                                .getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("piece_" + oblig, "ppm.pdf", "application/pdf", pdf))
                .file(new MockMultipartFile("piece_" + opt, "avis.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.idTypeDossier").value("DDP"));
    }

    @Test
    @DisplayName("Saisie PPM (multipart) : pièce optionnelle omise mais obligatoire fournie → 201")
    void creation_sans_piece_optionnelle_ok() throws Exception {
        int oblig = seedTypePiece("Plan de passation des marchés signé", true, "DDP",1);
        seedTypePiece("Avis de non-objection (si requis)", false, "DDP",2); // optionnelle, non fournie

        byte[] pdf = "%PDF-1.4 piece".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart("/api/saisies/ppm")
                .file(new MockMultipartFile("data", "", "application/json",
                        "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\"}"
                                .getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("piece_" + oblig, "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.idTypeDossier").value("DDP"));
    }

    /**
     * Garnit le référentiel H2 avec le jeu initial complet (20 lignes : PPM 5, DAO 8, MAOO 7),
     * miroir de la migration {@code 2026-06-26_type_piece_jointe_seed.sql}. Le type de dossier MAOO
     * (absent du seed de base) est ajouté pour satisfaire la FK {@code tr_type_dossier}.
     */
    private void seedReferentielPieces() {
        typeDossierRepository.save(new TypeDossier("DDM", "Dossier de Marché"));
        sousTypeDossierRepository.save(new SousTypeDossier("MAOO", "Marché sur Appel d'Offres Ouvert", "DDM"));
        sousTypeDossierRepository.save(new SousTypeDossier("MAOR", "Marché sur Appel d'Offres Ouvert Restreint", "DDM"));
        // PPM (5)
        seedTypePiece("Plan de passation des marchés signé", true, "DDP",1);
        seedTypePiece("Budget prévisionnel de l'exercice", true, "DDP",2);
        seedTypePiece("Arrêté ou décision portant nomination de la PRMP", true, "DDP",3);
        seedTypePiece("Tableau récapitulatif des marchés", true, "DDP",4);
        seedTypePiece("Avis de non-objection (si requis)", false, "DDP",5);
        // DAO (8)
        seedTypePiece("Dossier d'appel d'offres complet", true, "DMC",1);
        seedTypePiece("Cahier des clauses administratives générales", true, "DMC",2);
        seedTypePiece("Cahier des clauses techniques particulières", true, "DMC",3);
        seedTypePiece("Avis d'appel d'offres", true, "DMC",4);
        seedTypePiece("Estimation du coût des travaux/fournitures", true, "DMC",5);
        seedTypePiece("Garantie de soumission", true, "DMC",6);
        seedTypePiece("Avis de non-objection (si requis)", false, "DMC",7);
        seedTypePiece("Rapport d'évaluation des offres", false, "DMC",8);
        // MAOO (7)
        seedTypePiece("Projet de marché signé", true, "DDM",1);
        seedTypePiece("Cahier des charges", true, "DDM",2);
        seedTypePiece("Devis estimatif détaillé", true, "DDM",3);
        seedTypePiece("Procès-verbal d'ouverture des offres", true, "DDM",4);
        seedTypePiece("Rapport d'analyse des offres", true, "DDM",5);
        seedTypePiece("Attestation de capacité financière", false, "DDM",6);
        seedTypePiece("Avis de non-objection (si requis)", false, "DDM",7);
    }
}
