package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ <strong>Signalement front 2026-09-10</strong> — la soumission d'une mise à jour était refusée en
 * <strong>400</strong> « Le PPM daté et signé des versions antérieures est obligatoire », sans issue :
 * la pièce est constituée par l'application, la PRMP n'a aucun moyen de la produire.
 *
 * <p>La cause n'était pas la réparation — elle tourne bien, et pose le PV du prédécesseur — mais sa
 * <strong>source</strong> : le PPM antérieur était recopié depuis la pièce « Projet de PPM » (type 1)
 * de l'ancêtre, qui est <strong>facultative au référentiel</strong>. Sur les trois dossiers du pilote,
 * comme dans ce test, aucun ancêtre n'en porte : l'exigence réclamait un document que la chaîne
 * n'avait jamais contenu.</p>
 *
 * <p>Ces tests couvrent les deux versants : la chaîne <strong>muette</strong> (l'exigence tombe, la
 * soumission passe) et la chaîne <strong>documentée</strong> (le PPM de l'ancêtre est repris, et
 * l'exigence garde ses dents).</p>
 */
class SoumissionMiseAJourPiecesHistoriqueIntegrationTest extends CnmIntegrationTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private cnm.prs.repository.PieceJointeDossierRepository pieceJointeDossierRepository;

    /** Type « Projet de Plan de passation des marchés » du référentiel DDP — facultatif, cf. V1. */
    private static final int TYPE_PPM_SIGNE = 1;
    private static final int TYPE_PV_PRECEDENT = 22;
    private static final int TYPE_PPM_ANTERIEUR = 23;

    /**
     * {@code MiseAJourPpmService.exigerProprietaire} exige {@code dossier.getIdPrmp()} ; le seed standard
     * ne le porte pas (cf. {@code MiseAJourPpmIntegrationTest}).
     */
    private void enrichirDossier1PourPrmp() {
        cnm.prs.entity.Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setIdPrmp("PRMP001");
        d.setIdEntiteContract(1);
        // ⚠️ Le seed standard laisse le TYPE à null : la copie l'hérite, et la cohérence type↔contenu
        // refuse alors la soumission (« type null ne doit pas porter de PPM ») avant même nos gardes.
        d.setIdTypeDossier("DDP");
        // ⚠️ Idem pour la localité : ni le dossier ni son PPM n'en portent au seed, et la soumission la
        // réclame (« Localité indéterminée ») — encore avant nos gardes.
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
    }

    /** Mène le dossier 1 jusqu'à CLOTURE puis ouvre une mise à jour ; renvoie l'id de la nouvelle version. */
    private int ouvrirMiseAJourSurDossier1Cloture() throws Exception {
        enrichirDossier1PourPrmp();
        marcheRepository.save(marche(9800, 1, 1));
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        cloturerDossier1(9801, tokenVer);

        var res = mvc.perform(post("/api/saisies/ppm/1/mise-a-jour").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Rectification budgetaire\"}"))
                .andReturn();
        Assertions.assertEquals(201, res.getResponse().getStatus(),
                "ouverture de mise a jour refusee -- corps : " + res.getResponse().getContentAsString());
        return (int) (Integer) JsonPath.read(res.getResponse().getContentAsString(), "$.idDossier");
    }

    private void deposer(int idDossier, int idTypePiece, String nom) {
        PieceJointeDossier p = new PieceJointeDossier();
        p.setIdDossier(idDossier);
        p.setIdTypePiece(idTypePiece);
        p.setNomFichier(nom);
        p.setContenu(("contenu de " + nom).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        p.setFormat("pdf");
        p.setTaille(10L);
        p.setDateUpload(java.time.LocalDateTime.now());
        p.setApresLettreRenvoi(Boolean.FALSE);
        p.setVersionCorrigee(Boolean.FALSE);
        pieceJointeDossierRepository.save(p);
    }

    @Test
    @DisplayName("Ancêtre sans PPM déposé : la soumission de la mise à jour passe (l'exigence tombe faute de source)")
    void soumission_miseAJour_chaineSansPpm_passe() throws Exception {
        int idMaj = ouvrirMiseAJourSurDossier1Cloture();
        // Le décor reproduit le pilote : l'ancêtre n'a jamais porté de « Projet de PPM » (type 1 facultatif).
        Assertions.assertTrue(pieceJointeDossierRepository.findByIdDossier(1).stream()
                .noneMatch(p -> TYPE_PPM_SIGNE == p.getIdTypePiece()), "decor : l'ancetre ne doit porter aucun PPM");

        var res = mvc.perform(post("/api/dossiers/" + idMaj + "/soumettre").header("Authorization", tokenPrmp))
                .andReturn();
        Assertions.assertEquals(200, res.getResponse().getStatus(),
                "soumission refusee -- corps : " + res.getResponse().getContentAsString());
        Assertions.assertEquals("SOUMIS", JsonPath.read(res.getResponse().getContentAsString(), "$.statut"));

        // Le PV du prédécesseur, lui, EST constitué : l'application le détient et sait le régénérer.
        Assertions.assertTrue(pieceJointeDossierRepository
                .existsByIdDossierAndIdTypePiece(idMaj, TYPE_PV_PRECEDENT), "le PV du precedent reste exige");
    }

    @Test
    @DisplayName("Ancêtre avec PPM déposé : la pièce est reprise sur la mise à jour, l'exigence garde ses dents")
    void soumission_miseAJour_chaineAvecPpm_reprendLaPiece() throws Exception {
        int idMaj = ouvrirMiseAJourSurDossier1Cloture();
        // Le PPM est déposé sur l'ancêtre APRÈS l'ouverture de la version : c'est la réparation de la
        // garde de soumission qui doit le retrouver, pas la copie initiale.
        deposer(1, TYPE_PPM_SIGNE, "PPM-initial.pdf");
        Assertions.assertFalse(pieceJointeDossierRepository
                .existsByIdDossierAndIdTypePiece(idMaj, TYPE_PPM_ANTERIEUR), "decor : la piece 23 manque encore");
        // ⚠️ Cette relecture n'est pas décorative : elle force le flush de l'insertion ci-dessus avant que
        // la garde n'interroge la chaîne. Sans elle, la réparation ne voit pas encore la pièce et le test
        // échoue pour une raison qui n'a rien à voir avec la règle éprouvée ici.
        Assertions.assertTrue(pieceJointeDossierRepository.existsByIdDossierAndIdTypePiece(1, TYPE_PPM_SIGNE),
                "decor : l'ancetre doit bien porter le PPM depose -- pieces de l'ancetre = "
                        + pieceJointeDossierRepository.findByIdDossier(1).stream()
                                .map(p -> p.getIdTypePiece() + ":" + p.getNomFichier()).toList());

        var res = mvc.perform(post("/api/dossiers/" + idMaj + "/soumettre").header("Authorization", tokenPrmp))
                .andReturn();
        Assertions.assertEquals(200, res.getResponse().getStatus(),
                "soumission refusee -- corps : " + res.getResponse().getContentAsString());

        Assertions.assertTrue(pieceJointeDossierRepository
                .existsByIdDossierAndIdTypePiece(idMaj, TYPE_PPM_ANTERIEUR),
                "le PPM de l'ancetre doit etre repris comme piece d'historique");
    }
}
