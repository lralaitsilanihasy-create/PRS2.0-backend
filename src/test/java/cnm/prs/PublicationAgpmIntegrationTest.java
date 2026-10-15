package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.PieceSousType;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.service.PublicationAgpmService;

/**
 * ⚠️ V118 (2026-10-15, demande front « pièces de l'AMI et publication de l'AGPM », §B2 ; arbitrages H1 figés, H2 filiation) — la
 * publication de l'AGPM déposée une fois sur le plan contrôlé : gardes du dépôt, remplacement, lecture héritée d'une version
 * précédente du plan, reprise d'office par le dossier de la ligne, indication au refus de soumission.
 *
 * <p>Jeu : plan 9900 (PRMP001, PV signé favorable), ligne 9901 en appel d'offres ouvert ; plan 9800 brouillon.</p>
 */
class PublicationAgpmIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired private cnm.prs.repository.PieceSousTypeRepository associations;
    @Autowired private PublicationAgpmService service;
    @Autowired private cnm.prs.repository.PieceJointeDossierRepository pieceJointeDossierRepository;

    @BeforeEach
    void jeu() {
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m92);
        Dossier plan = dossierLoc(9900, "CLOTURE", "ANT", "PRMP001");
        plan.setIdTypeDossier("DDP");
        plan.setIdEntiteContract(1);
        dossierRepository.save(plan);
        ppmRepository.save(ppm(9900, 9900, "PRMP001"));
        receptionRepository.save(reception(9900, 9900, "CTRCC1", true));
        dispatchRepository.save(dispatch(9900, 9900, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9900, 9900, "CTRMEM"));
        seedPvSigne(9900, 9900);
        Marche l = marche(9901, 9900, 9900);
        l.setIdMode(92);
        l.setIdNature(natureFournitures());
        l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        l.setDesignationMarche("Acquisition de mobilier");
        marcheRepository.save(l);
        Dossier brouillon = dossierLoc(9800, "BROUILLON", "ANT", "PRMP001");
        brouillon.setIdTypeDossier("DDP");
        dossierRepository.save(brouillon);
    }

    @Test
    @DisplayName("Dépôt : gardes (profil, plan, contrôle, PDF, champs) ; 201 puis lecture et scan ; un second dépôt remplace le premier")
    void depot() throws Exception {
        deposer(9900, tokenMembre, pdfMinimal(), "JMP", "123", "2026-01-15", "AGPM-1").andExpect(status().isForbidden());
        deposer(9800, tokenPrmp, pdfMinimal(), "JMP", "123", "2026-01-15", "AGPM-1").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NON_CONTROLE"));
        int dao = dossierSaisiHorsFiche("DMC", "DAOO", tokenPrmp);
        deposer(dao, tokenPrmp, pdfMinimal(), "JMP", "123", "2026-01-15", "AGPM-1").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAS_UN_PLAN"));
        deposer(9900, tokenPrmp, "pas un pdf".getBytes(), "", null, "2099-01-01", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[*].champ").value(org.hamcrest.Matchers.containsInAnyOrder("fichier", "journal",
                        "dateParution", "referenceAgpm")));

        deposer(9900, tokenPrmp, pdfMinimal(), "Journal des Marchés Publics", "123", "2026-01-15", "AGPM-2026-01")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.enVigueur").value(true));
        mvc.perform(get("/api/dossiers/9900/publication-agpm").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.journal").value("Journal des Marchés Publics")).andExpect(jsonPath("$.numero").value("123"))
                .andExpect(jsonPath("$.dateParution").value("2026-01-15")).andExpect(jsonPath("$.heritee").value(false));
        byte[] scan = mvc.perform(get("/api/dossiers/9900/publication-agpm/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(scan).isEqualTo(pdfMinimal());

        deposer(9900, tokenPrmp, pdfMinimal(), "Midi Madagasikara", null, "2026-01-20", "AGPM-2026-01").andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/9900/publication-agpm").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.journal").value("Midi Madagasikara")).andExpect(jsonPath("$.numero").doesNotExist());
        assertThat(publicationAgpmRepository.findAll()).hasSize(2).filteredOn(p -> p.getRemplaceeLe() == null).hasSize(1);
    }

    @Test
    @DisplayName("H2 : une version suivante du plan hérite de la publication ; sans publication → 404")
    void filiation() throws Exception {
        Dossier v2 = dossierLoc(9902, "BROUILLON", "ANT", "PRMP001");
        v2.setIdTypeDossier("DDP");
        v2.setIdDossierParent(9900);
        dossierRepository.save(v2);
        mvc.perform(get("/api/dossiers/9902/publication-agpm").header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        publicationAgpmDeTest(9900);
        mvc.perform(get("/api/dossiers/9902/publication-agpm").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idDossierPlan").value(9900)).andExpect(jsonPath("$.heritee").value(true));
    }

    @Test
    @DisplayName("Reprise d'office : le dossier de la ligne reçoit la pièce PUBLICATION_AGPM s'il l'exige et ne l'a pas ; sans publication, "
            + "le refus de soumission dit qu'elle se dépose sur le plan")
    void reprise() throws Exception {
        cnm.prs.entity.TypePieceJointe type = typePieceJointeRepository.findFirstByCode("PUBLICATION_AGPM").orElseGet(() -> {
            cnm.prs.entity.TypePieceJointe t = typePieceJointeRepository.findById(seedTypePiece("Publication de l'AGPM (journal)", false, null, 0))
                    .orElseThrow();
            t.setCode("PUBLICATION_AGPM");
            return typePieceJointeRepository.save(t);
        });
        associations.save(new PieceSousType(type.getIdTypePiece(), "DAOO", true, 1, null, null));
        String dmc = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long idDmc = ((Number) JsonPath.read(dmc, "$.idDmc")).longValue();
        int id = dossierSaisiHorsFiche("DMC", "DAOO", tokenPrmp);

        // Sans plan rattaché (pas de fiche), le refus nomme la pièce et dit où la déposer.
        mvc.perform(post("/api/dossiers/" + id + "/soumettre").header("Authorization", tokenPrmp)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[*].message").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(
                        "Elle se dépose une fois sur le plan de passation contrôlé"))));

        Dossier d = dossierRepository.findById(id).orElseThrow();
        d.setIdDmc(idDmc);
        dossierRepository.save(d);
        assertThat(service.joindreSiAbsente(d)).isFalse();   // le plan n'a pas de publication
        publicationAgpmDeTest(9900);
        assertThat(service.joindreSiAbsente(d)).isTrue();
        assertThat(service.joindreSiAbsente(d)).isFalse();   // déjà jointe
        assertThat(pieceJointeDossierRepository.findByIdDossier(id)).extracting(p -> p.getNomFichier())
                .containsExactly("PUBLICATION_AGPM_AGPM-9900.pdf");
        mvc.perform(get("/api/dossiers/" + id + "/publication-agpm").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idDossierPlan").value(9900));
    }

    private ResultActions deposer(int idDossier, String jeton, byte[] pdf, String journal, String numero, String date, String reference)
            throws Exception {
        var req = multipart("/api/dossiers/" + idDossier + "/publication-agpm")
                .file(new MockMultipartFile("fichier", "jmp.pdf", "application/pdf", pdf)).header("Authorization", jeton);
        if (journal != null) {
            req.param("journal", journal);
        }
        if (numero != null) {
            req.param("numero", numero);
        }
        if (date != null) {
            req.param("dateParution", date);
        }
        if (reference != null) {
            req.param("referenceAgpm", reference);
        }
        return mvc.perform(req);
    }
}
