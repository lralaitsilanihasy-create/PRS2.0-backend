package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.DaoCompletFile;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.repository.DaoCompletFileRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.service.ChampFicheMarcheService;
import cnm.prs.service.DaoCompletFileService;
import cnm.prs.service.DaoCompletWord;

/**
 * ⚠️ 2026-10-28 (demande front « dao-complet-en-file », Q1-Q5 ; V144) — la validation n'attend plus Word : la version entre en file,
 * l'ouvrier unique l'assemble ensuite. Word est simulé ({@link DaoCompletWord} remplacé, assemblage actif) et l'ouvrier ne part pas tout
 * seul ({@code app.dao-complet.file-auto=false}) : le test le fait passer ({@link DaoCompletFileService#traiterTout}).
 */
@TestPropertySource(properties = "app.dao-complet.file-auto=false")
class DaoCompletFileIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @MockitoBean private DaoCompletWord word;
    @Autowired private DaoCompletFileService file;
    @Autowired private DaoCompletFileRepository fileRepository;
    @Autowired private DocumentFicheMarcheRepository documentRepository;
    @Autowired private FicheMarcheRepository ficheMarcheRepository;
    @Autowired private ChampFicheMarcheService champService;

    @BeforeEach
    void jeu() throws Exception {
        when(word.actif()).thenReturn(true);
        when(word.assembler(any(DaoCompletWord.Charte.class), any())).thenReturn(new DaoCompletWord.Resultat("docx".getBytes(), "%PDF".getBytes()));
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m92);
        dossierRepository.save(dossierLoc(9900, "CLOTURE", "ANT", "PRMP001"));
        Dossier plan = dossierRepository.findById(9900).orElseThrow();
        plan.setIdEntiteContract(1);
        dossierRepository.save(plan);
        ppmRepository.save(ppm(9900, 9900, "PRMP001"));
        receptionRepository.save(reception(9900, 9900, "CTRCC1", true));
        dispatchRepository.save(dispatch(9900, 9900, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9900, 9900, "CTRMEM"));
        seedPvSigne(9900, 9900);
        for (int id : new int[] { 9901, 9902 }) {
            Marche l = marche(id, 9900, 9900);
            l.setIdMode(92);
            l.setIdNature(natureFournitures());
            l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
            l.setDesignationMarche("Acquisition de matériels informatiques " + id);
            marcheRepository.save(l);
        }
        assertThat(champService.importerCsv(new ClassPathResource("fiche-marche/referentiel-champs-fiche-marche-fournitures.csv").getFile()
                .toPath()).rejets()).isEmpty();
    }

    @Test
    @DisplayName("Q1-Q4 — la validation répond sans Word, la version est EN_FILE n° 1 ; l'ouvrier l'assemble : PRET, DAO complet "
            + "enregistré, PRMP prévenue")
    void validationPuisOuvrier() throws Exception {
        Long idDmc = creerDmc(9901);
        String valide = valider(idDmc);
        verify(word, never()).assembler(any(DaoCompletWord.Charte.class), any());
        assertThat(JsonPath.<String>read(valide, "$.daoComplet.etat")).isEqualTo("EN_FILE");
        assertThat(JsonPath.<Integer>read(valide, "$.daoComplet.rang")).isEqualTo(1);
        assertThat(JsonPath.<String>read(fiche(idDmc), "$.daoComplet.etat")).isEqualTo("EN_FILE");

        assertThat(file.traiterTout()).isEqualTo(1);
        verify(word, times(1)).assembler(any(DaoCompletWord.Charte.class), any());
        String lue = fiche(idDmc);
        assertThat(JsonPath.<String>read(lue, "$.daoComplet.etat")).isEqualTo("PRET");
        assertThat(JsonPath.<Object>read(lue, "$.daoComplet.rang")).isNull();
        assertThat(JsonPath.<String>read(lue, "$.daoComplet.depuis")).isNotNull();
        int idFiche = JsonPath.read(lue, "$.idFiche");
        assertThat(documentRepository.findByIdFicheOrderByIdDocumentAsc(idFiche)).filteredOn(d -> "DAO_COMPLET".equals(d.getType())).hasSize(2);
        assertThat(notificationRepository.findAll()).filteredOn(n -> "DAO_COMPLET_PRET".equals(n.getTypeNotif()))
                .extracting(cnm.prs.entity.Notification::getDestinataireRef).contains("PRMP001");
        assertThat(file.traiterTout()).isZero();   // rien de plus à faire
    }

    @Test
    @DisplayName("Q2 — ordre d'arrivée, la tête de file pour l'avis spécifique ; une version remplacée par une révision validée sort de la file")
    void ordreTeteEtVersionRemplacee() throws Exception {
        Long a = creerDmc(9901);
        Long b = creerDmc(9902);
        valider(a);
        valider(b);
        assertThat(JsonPath.<Integer>read(fiche(a), "$.daoComplet.rang")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(fiche(b), "$.daoComplet.rang")).isEqualTo(2);
        FicheMarche vb = ficheMarcheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(b).orElseThrow();
        assertThat(file.mettreEnTete(vb)).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(fiche(a), "$.daoComplet.rang")).isEqualTo(2);

        // Une révision de A validée avant que l'ouvrier ne passe : seule la v2 est assemblée.
        mvc.perform(post("/api/fiches-marche/" + a + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        valider(a);
        assertThat(file.traiterTout()).isEqualTo(3);   // B, A v1 (retirée), A v2
        verify(word, times(2)).assembler(any(DaoCompletWord.Charte.class), any());
        FicheMarche v1 = ficheMarcheRepository.findByIdDmcOrderByNumeroVersionAsc(a).get(0);
        assertThat(fileRepository.findById(v1.getIdFiche())).isEmpty();
        assertThat(JsonPath.<String>read(fiche(a), "$.daoComplet.etat")).isEqualTo("PRET");
        assertThat(JsonPath.<String>read(fiche(b), "$.daoComplet.etat")).isEqualTo("PRET");
    }

    @Test
    @DisplayName("Q2-Q3 — un échec laisse la version en file avec son motif, reprise plus tard sans bloquer ; au 3e, ECHEC ; l'avis la relance")
    void echecsPuisAbandon() throws Exception {
        Long idDmc = creerDmc(9901);
        valider(idDmc);
        when(word.assembler(any(DaoCompletWord.Charte.class), any())).thenThrow(new IllegalStateException("Assemblage du DAO complet : délai de 300 s dépassé."));
        assertThat(file.traiterTout()).isEqualTo(1);
        String lue = fiche(idDmc);
        assertThat(JsonPath.<String>read(lue, "$.daoComplet.etat")).isEqualTo("EN_FILE");
        assertThat(JsonPath.<String>read(lue, "$.daoComplet.motif")).contains("délai de 300 s");
        assertThat(file.traiterTout()).isZero();   // reprise dans 10 min : l'ouvrier ne la reprend pas tout de suite
        int idFiche = JsonPath.read(lue, "$.idFiche");
        for (int i = 0; i < 2; i++) {
            DaoCompletFile f = fileRepository.findById(idFiche).orElseThrow();
            f.setRepriseApres(null);
            fileRepository.save(f);
            file.traiterTout();
        }
        DaoCompletFile f = fileRepository.findById(idFiche).orElseThrow();
        assertThat(f.getEtat()).isEqualTo("ECHEC");
        assertThat(f.getEssais()).isEqualTo(3);
        lue = fiche(idDmc);
        assertThat(JsonPath.<String>read(lue, "$.daoComplet.etat")).isEqualTo("ECHEC");
        assertThat(JsonPath.<Object>read(lue, "$.daoComplet.rang")).isNull();

        // L'avis spécifique qui l'attend la remet en tête, pour une nouvelle série d'essais.
        assertThat(file.mettreEnTete(ficheMarcheRepository.findById(idFiche).orElseThrow())).isEqualTo(1);
        f = fileRepository.findById(idFiche).orElseThrow();
        assertThat(f.getEtat()).isEqualTo("EN_FILE");
        assertThat(f.getEssais()).isZero();
        assertThat(f.isPrioritaire()).isTrue();
    }

    @Test
    @DisplayName("Q5 — le dossier né de la fiche se crée pendant la préparation ; sa soumission est refusée en 409 DAO_COMPLET_EN_PREPARATION "
            + "{ rang } ; prêt, le DAO complet rejoint le dossier et la garde ne joue plus")
    void soumissionAttendLeDaoComplet() throws Exception {
        cnm.prs.entity.TypePieceJointe dao = typePieceJointeRepository.findById(seedTypePiece("Dossier d'appel d'offres", true, "DMC", 6))
                .orElseThrow();
        dao.setCode("DAO_COMPLET");   // la pièce sous laquelle les documents de la fiche rejoignent le dossier
        typePieceJointeRepository.save(dao);
        Long idDmc = creerDmc(9901);
        valider(idDmc);
        String cree = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDossier = JsonPath.read(cree, "$.idDossier");
        mvc.perform(post("/api/dossiers/" + idDossier + "/soumettre").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAO_COMPLET_EN_PREPARATION"))
                .andExpect(jsonPath("$.details.rang").value(1));

        clearInvocations(word);
        assertThat(file.traiterTout()).isEqualTo(1);
        assertThat(pieceJointeDossierRepository.findByIdDossierAndIdDocumentFicheIsNotNull(idDossier))
                .extracting(p -> documentRepository.findById(p.getIdDocumentFiche()).orElseThrow().getType()).contains("DAO_COMPLET");
        String refus = mvc.perform(post("/api/dossiers/" + idDossier + "/soumettre").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString();
        assertThat(refus).doesNotContain("DAO_COMPLET_EN_PREPARATION");
    }

    @Test
    @DisplayName("Q3 — sans Word sur le serveur : SANS_WORD, rien n'entre en file, la soumission n'attend rien")
    void sansWord() throws Exception {
        when(word.actif()).thenReturn(false);
        Long idDmc = creerDmc(9901);
        String valide = valider(idDmc);
        assertThat(JsonPath.<String>read(valide, "$.daoComplet.etat")).isEqualTo("SANS_WORD");
        assertThat(fileRepository.findAll()).isEmpty();
    }

    // ------------------------------------------------------------------ outils

    @Autowired private cnm.prs.repository.NotificationRepository notificationRepository;
    @Autowired private cnm.prs.repository.PieceJointeDossierRepository pieceJointeDossierRepository;

    /** Remplit la première version, puis valide ; rend la réponse de la validation. */
    private String valider(Long idDmc) throws Exception {
        if (ficheMarcheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream().noneMatch(f -> "VALIDEE".equals(f.getStatut()))) {
            remplirObligatoires(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        }
        return mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String fiche(Long idDmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long idDmc = ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"alloti\":\"NON\",\"variantes\":\"NON\",\"groupement\":\"NON\",\"provenance\":\"NATIONAL\","
                        + "\"typePrix\":\"UNITAIRES\",\"prixRevisable\":\"NON\",\"garantieSoumission\":\"NON\",\"avance\":\"NON\","
                        + "\"penalites\":\"CCAG\"}}"))
                .andExpect(status().isOk());
        return idDmc;
    }
}
