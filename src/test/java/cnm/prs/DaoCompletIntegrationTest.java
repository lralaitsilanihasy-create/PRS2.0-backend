package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ 2026-10-06 (demande front « le DAO complet », §B1-B3 ; V73) — sans Word (tests : {@code app.dao-complet.actif=false}) : les
 * spécifications techniques (dépôt .docx, gardes, avertissement, recopie à la révision) ; la liste des documents qui préfère le DAO
 * complet quand il existe (les classeurs restent). L'assemblage par Word a son test marqué {@code word} ({@code DaoCompletWordTest}).
 */
class DaoCompletIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;
    @Autowired private DocumentFicheMarcheRepository documentRepository;
    @Autowired private cnm.prs.service.PiecesExemptees piecesExemptees;
    @Autowired private cnm.prs.repository.PieceJointeDossierRepository pieceJointeDossierRepository;
    @Autowired private cnm.prs.repository.SpecificationsFicheRepository specificationsRepository;

    @BeforeEach
    void jeu() throws Exception {
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
        Marche l = marche(9901, 9900, 9900);
        l.setIdMode(92);
        l.setIdNature(natureFournitures());
        l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        l.setDesignationMarche("Acquisition de matériels informatiques");
        marcheRepository.save(l);
        for (String f : List.of("referentiel-champs-fiche-marche-fournitures.csv", "referentiel-champs-fiche-dao-travaux.csv")) {
            assertThat(champService.importerCsv(new ClassPathResource("fiche-marche/" + f).getFile().toPath()).rejets()).isEmpty();
        }
    }

    @Test
    @DisplayName("⚠️ 17/10 (« specifications-donnees-et-plans-pdf », A2-A3) — fournitures : plus de fichier de spécifications (409 "
            + "SPECIFICATIONS_HORS_PERIMETRE) ; sans article, avertissement qui demande le besoin ; avec, une information sans Word ; les "
            + "prescriptions générales (B12-BE-01) se saisissent au B12")
    void specifications() throws Exception {
        Long idDmc = creerDmc();
        String url = "/api/fiches-marche/" + idDmc + "/specifications";
        mvc.perform(get(url).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        assertThat(JsonPath.<List<String>>read(fiche(idDmc), "$.bilanControles.avertissements[?(@.regle=='SPECIFICATIONS_ABSENTES')].message"))
                .singleElement().asString().contains("Saisissez le besoin").doesNotContain("Word");
        deposer(url, "specs.docx", docx("Devis descriptif")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPECIFICATIONS_HORS_PERIMETRE"));
        besoinDeTest(idDmc);
        assertThat(JsonPath.<List<String>>read(fiche(idDmc), "$.bilanControles.avertissements[*].regle")).doesNotContain("SPECIFICATIONS_ABSENTES");
        assertThat(JsonPath.<List<String>>read(fiche(idDmc), "$.bilanControles.ok[?(@.regle=='SPECIFICATIONS_DU_BESOIN')].message"))
                .containsExactly("Le DAO complet portera le tableau des spécifications tiré du besoin.");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B12").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B12-BE-01\":\"Emballage d'origine scellé.\\nNotice en français.\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valeurs.B12-BE-01").value("Emballage d'origine scellé.\nNotice en français."));

        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        String documents = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(documents, "$[*].type")).doesNotContain("DAO_COMPLET", "DESCRIPTIF_PLANS").contains("DPAO", "AE");
    }

    @Test
    @DisplayName("Le DAO complet, quand il existe, remplace les documents séparés dans la liste (titre « Dossier d'appel d'offres "
            + "complet »), les classeurs restent")
    void listePrefereLeDaoComplet() throws Exception {
        Long idDmc = creerDmc();
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        int idFiche = JsonPath.read(fiche(idDmc), "$.idFiche");
        for (String ext : List.of("docx", "pdf")) {
            documentRepository.save(new DocumentFicheMarche(null, idFiche, "DAO_COMPLET", ext, "DAO_COMPLET_test_v1." + ext, 4L, "0".repeat(64),
                    LocalDateTime.now(), "%PDF".getBytes(), null, null));
        }
        String documents = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.extension!='xlsx' && @.type!='BORDEREAU_ESTIMATIF')].type")).containsOnly("DAO_COMPLET").hasSize(2);   // ⚠️ V117 : l'estimatif est listé à part du DAO
        assertThat(JsonPath.<List<String>>read(documents, "$[?(@.type=='DAO_COMPLET')].libelle")).containsOnly("Dossier d'appel d'offres complet");
        // ⚠️ C6 (recette du 06/10) — le DAO complet en tête, avant les classeurs.
        assertThat(JsonPath.<List<String>>read(documents, "$[*].type")).startsWith("DAO_COMPLET", "DAO_COMPLET");
    }

    @Test
    @DisplayName("⚠️ E2 (07/10) : un dossier qui porte le DAO complet n'a plus à joindre le CCAG, ni le CCTP quand la fiche a ses "
            + "spécifications techniques (⚠️ 17/10 : en fournitures, un besoin à caractéristiques) ; sans DAO complet, rien n'est exempté")
    void ccagEtCctpDansLeDaoComplet() throws Exception {
        Long idDmc = creerDmc();
        besoinDeTest(idDmc);   // ⚠️ 17/10 — en fournitures, les spécifications sont le besoin (articles à caractéristiques), plus un Word
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        int idFiche = JsonPath.read(fiche(idDmc), "$.idFiche");
        int ccag = seedTypePiece("Cahier des clauses administratives générales", true, "DMC", 7);
        int cctp = seedTypePiece("Cahier des clauses techniques particulières", true, "DMC", 8);
        for (int[] t : new int[][] { { ccag, 0 }, { cctp, 1 } }) {
            cnm.prs.entity.TypePieceJointe tp = typePieceJointeRepository.findById(t[0]).orElseThrow();
            tp.setCode(t[1] == 0 ? "CCAG" : "CCTP");
            typePieceJointeRepository.save(tp);
        }
        dossierRepository.save(dossierLoc(9910, "BROUILLON", "ANT", "PRMP001"));
        assertThat(piecesExemptees.de(9910)).isEmpty();
        DocumentFicheMarche dao = documentRepository.save(new DocumentFicheMarche(null, idFiche, "DAO_COMPLET", "pdf", "DAO_COMPLET_test_v1.pdf", 4L,
                "0".repeat(64), LocalDateTime.now(), "%PDF".getBytes(), null, null));
        cnm.prs.entity.PieceJointeDossier p = new cnm.prs.entity.PieceJointeDossier();
        p.setIdDossier(9910);
        p.setIdTypePiece(seedTypePiece("Dossier d'appel d'offres complet", true, "DMC", 6));
        p.setNomFichier(dao.getNomFichier());
        p.setFormat("PDF");
        p.setApresLettreRenvoi(false);
        p.setIdDocumentFiche(dao.getIdDocument());
        pieceJointeDossierRepository.save(p);
        assertThat(piecesExemptees.de(9910)).containsExactlyInAnyOrder(ccag, cctp);
        entityManager.flush();
        jdbcTemplate.update("DELETE FROM t_fiche_caracteristique WHERE \"ID_ARTICLE\" IN (SELECT \"ID_ARTICLE\" FROM t_fiche_article WHERE \"ID_FICHE\" = ?)", idFiche);
        entityManager.clear();
        assertThat(piecesExemptees.de(9910)).containsExactly(ccag);
    }

    @Test
    @DisplayName("⚠️ 17/10 (« specifications-donnees-et-plans-pdf », B1) — la limite de 50 Mo ne vaut que pour la route des spécifications : "
            + "ailleurs, un fichier de plus de 20 Mo reste refusé (400 « Fichier trop volumineux »)")
    void limiteDeTailleAilleurs() throws Exception {
        Long idDmc = creerDmc();
        byte[] gros = new byte[20 * 1024 * 1024 + 1];
        mvc.perform(multipart("/api/fiches-marche/" + idDmc + "/import").file(new MockMultipartFile("fichier", "DAO.pdf", "application/pdf", gros))
                .header("Authorization", tokenPrmp)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Fichier trop volumineux : la taille maximale autorisée est dépassée."));
    }

    @Test
    @DisplayName("⚠️ 17/10 (« abandonner-revision-repartir-version ») — abandonner la révision (la fiche revient à sa dernière version "
            + "validée, aucune version créée) ; repartir d'une version figée antérieure (numéro suivant, contenu de l'ancienne) ; refus "
            + "nommés ; journal du plan")
    void abandonnerEtRepartir() throws Exception {
        Long idDmc = creerDmc();
        String base = "/api/fiches-marche/" + idDmc;
        mvc.perform(delete(base + "/revision").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREMIERE_VERSION"));
        besoinDeTest(idDmc);
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        mvc.perform(delete(base + "/revision").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAS_DE_REVISION"));

        // Une v2 qui diffère de la v1 par ses prescriptions générales.
        mvc.perform(post(base + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(put(base + "/blocs/B12").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B12-BE-01\":\"Notice en français.\"}}")).andExpect(status().isOk());
        mvc.perform(post(base + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isOk());

        // Repartir de la v1 : une v3 au contenu de la v1.
        mvc.perform(post(base + "/reviser").param("depuis", "9").header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        String v3 = mvc.perform(post(base + "/reviser").param("depuis", "1").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(v3, "$.version")).isEqualTo(3);
        assertThat(JsonPath.<String>read(v3, "$.statut")).isEqualTo("BROUILLON");
        assertThat(JsonPath.<Map<String, Object>>read(v3, "$.valeurs")).doesNotContainKey("B12-BE-01");
        assertThat(mvc.perform(get(base + "/articles").header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsString())
                .contains("\"designation\"");
        mvc.perform(post(base + "/reviser").param("depuis", "1").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVISION_OUVERTE"));

        // Abandonner la v3 : la fiche revient à la v2, validée, sans version créée.
        String v2 = mvc.perform(delete(base + "/revision").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(v2, "$.version")).isEqualTo(2);
        assertThat(JsonPath.<String>read(v2, "$.statut")).isEqualTo("VALIDEE");
        assertThat(JsonPath.<Map<String, Object>>read(fiche(idDmc), "$.valeurs")).containsEntry("B12-BE-01", "Notice en français.");

        String journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='FICHE_REVISION_OUVERTE')].detail"))
                .containsExactly("DAO, révision ouverte depuis la version 1 : version 3 en brouillon");
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='FICHE_REVISION_ABANDONNEE')].detail"))
                .containsExactly("DAO, révision de la version 3 abandonnée : la fiche revient à la version 2");

        // Un brouillon qui porte une migration documentée du mode de remise (R8) ne s'abandonne pas.
        mvc.perform(post(base + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        cnm.prs.entity.FicheMarche brouillon = ficheMarcheRepository.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).orElseThrow();
        brouillon.setModeMigreLe(LocalDateTime.now());
        ficheMarcheRepository.save(brouillon);
        mvc.perform(delete(base + "/revision").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODE_MIGRE"));
    }

    @Autowired private cnm.prs.repository.FicheMarcheRepository ficheMarcheRepository;
    @Autowired private cnm.prs.repository.PieceSousTypeRepository pieceSousTypeRepository;

    @Test
    @DisplayName("⚠️ 17/10 (« canevas-rapport-evaluation ») — fournitures à quantités fixes : le canevas de rapport fourni par "
            + "l'application (données et PDF, plan du rapport, grille de l'examen préliminaire, articles du besoin) ; « tout autre "
            + "document utile » listé facultatif ; un dossier sans fiche n'a pas de canevas")
    void canevasRapport() throws Exception {
        int ordre = 1;
        for (String[] p : List.of(new String[] {"CANEVAS_RAPPORT", "Canevas de rapport d'évaluation", "PAPIER"},
                new String[] {"AUTRE_DOCUMENT", "Tout autre document utile à l'appréciation du dossier", null})) {
            cnm.prs.entity.TypePieceJointe t = typePieceJointeRepository.findFirstByCode(p[0]).orElseGet(() -> {
                cnm.prs.entity.TypePieceJointe n = new cnm.prs.entity.TypePieceJointe();
                n.setLibellePiece(p[1]);
                n.setObligatoire(false);
                n.setOrdre(0);
                n.setCode(p[0]);
                return typePieceJointeRepository.save(n);
            });
            cnm.prs.entity.PieceSousType a = new cnm.prs.entity.PieceSousType(t.getIdTypePiece(), "DAOO", !"AUTRE_DOCUMENT".equals(p[0]),
                    ordre++, null, "CANEVAS_RAPPORT".equals(p[0]) ? "AUTRE" : null);
            a.setCondition(p[2]);
            pieceSousTypeRepository.save(a);
        }
        Long idDmc = creerDmc();
        besoinDeTest(idDmc);
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        String cree = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.<Integer>read(cree, "$.idDossier");

        String exigees = mvc.perform(get("/api/dossiers/" + id + "/pieces-exigees").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(exigees, "$[?(@.code=='CANEVAS_RAPPORT')].fourniePar")).containsExactly("APPLICATION");
        assertThat(JsonPath.<List<String>>read(exigees, "$[?(@.code=='CANEVAS_RAPPORT')].lien"))
                .containsExactly("/api/dossiers/" + id + "/canevas-rapport");
        assertThat(JsonPath.<List<Boolean>>read(exigees, "$[?(@.code=='AUTRE_DOCUMENT')].obligatoire")).containsExactly(false);
        assertThat(mvc.perform(post("/api/dossiers/" + id + "/soumettre").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString()).doesNotContain("Canevas de rapport").doesNotContain("Tout autre document");

        String c = mvc.perform(get("/api/dossiers/" + id + "/canevas-rapport").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(c, "$.categorie")).isEqualTo("FOURNITURES_SERVICES");
        assertThat(JsonPath.<List<String>>read(c, "$.sections[*].code")).containsExactly("REFERENCES", "PLIS_RECUS",
                "EXAMEN_PRELIMINAIRE", "CONFORMITE", "EVALUATION", "CLASSEMENT", "POST_QUALIFICATION", "ATTRIBUTION", "SIGNATURES");
        // La grille de l'examen préliminaire : les vérifications du rapport, sans la visite des lieux (travaux seulement).
        assertThat(JsonPath.<List<List<String>>>read(c, "$.sections[?(@.code=='EXAMEN_PRELIMINAIRE')].tableau.rangees").get(0))
                .hasSize(cnm.prs.service.EvaluationService.VERIFICATIONS.size() - 1)
                .first().isEqualTo(cnm.prs.service.EvaluationService.VERIFICATIONS.get("AE_PRIX"));
        assertThat(JsonPath.<List<String>>read(c, "$.sections[?(@.code=='REFERENCES')].lignes[?(@.code=='OBJET')].valeur"))
                .containsExactly("Acquisition de matériels informatiques");
        assertThat(JsonPath.<List<List<String>>>read(c, "$.sections[?(@.code=='CONFORMITE')].tableau.rangees").get(0)).isNotEmpty();
        assertThat(JsonPath.<List<List<String>>>read(c, "$.sections[?(@.code=='ATTRIBUTION')].tableau.rangees").get(0))
                .containsExactly("Marché");
        byte[] pdf = mvc.perform(get("/api/dossiers/" + id + "/canevas-rapport/pdf").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        mvc.perform(get("/api/dossiers/9900/canevas-rapport").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANEVAS_INDISPONIBLE"));
    }

    @Test
    @DisplayName("⚠️ 18/10 (« avis-specifique-en-donnees ») — B1 : l'avis spécifique du dossier né d'une fiche, en données (mentions du "
            + "modèle type et leur valeur imprimée, version présentée) ; B2 : les articles d'une version donnée, 404 version inconnue")
    void avisEnDonneesEtArticlesDUneVersion() throws Exception {
        Long idDmc = creerDmc();
        besoinDeTest(idDmc);
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        String cree = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.<Integer>read(cree, "$.idDossier");

        String a = mvc.perform(get("/api/dossiers/" + id + "/avis-specifique").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(a, "$.gabarit")).isEqualTo("AVIS-F");
        assertThat(JsonPath.<Boolean>read(a, "$.lettre")).isFalse();
        assertThat(JsonPath.<Integer>read(a, "$.versionFiche")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(a, "$.mentions[*].code")).containsExactly("OBJET", "LOTS_MAX", "FORME_PRIX",
                "ADRESSE_CONSULTATION", "ADRESSE_RETRAIT", "DATE_HEURE_LIMITE", "HEURE_OUVERTURE", "COUT_DOSSIER", "COMPTE_PAIEMENT",
                "FORMES_GARANTIE", "MONTANT_GARANTIE");
        assertThat(JsonPath.<List<String>>read(a, "$.mentions[?(@.code=='OBJET')].valeur"))
                .containsExactly("Acquisition de matériels informatiques");
        // Q2 : une mention non renseignée a une valeur nulle.
        assertThat(JsonPath.<List<Object>>read(a, "$.mentions[?(@.renseignee==false)].valeur")).allSatisfy(v -> assertThat(v).isNull());

        String courants = mvc.perform(get("/api/fiches-marche/" + idDmc + "/articles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String v1 = mvc.perform(get("/api/fiches-marche/" + idDmc + "/articles").param("version", "1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(v1, "$")).isNotEmpty();
        assertThat(v1).isEqualTo(courants);
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/articles").param("version", "99").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/dossiers/9900/avis-specifique").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVIS_INDISPONIBLE"));
    }

    @Test
    @DisplayName("⚠️ 17/10 (« pieces-dmc-par-les-donnees ») — dossier né d'une fiche validée, remise papier : fiche de présentation, "
            + "calendrier et contrôle du plan fournis par l'application (plus exigés à la soumission) ; fiche de présentation en données "
            + "et en PDF ; plan et PV sur le dossier ; un dossier sans fiche n'a pas de fiche de présentation")
    void piecesFourniesParLesDonnees() throws Exception {
        // Les quatre premières pièces du DAOO (V95), les trois internes en PAPIER (V102, V115), comme en production.
        int ordre = 1;
        for (String[] p : List.of(new String[] {"FICHE_PRESENTATION", "Fiche de présentation datée et signée", "PAPIER"},
                new String[] {"CALENDRIER", "Calendrier annuel de passation mis à jour et signé", "PAPIER"},
                new String[] {"AGPM_CONTROLE", "AGPM (ou PPM) signé et contrôlé, avec le PV de la Commission", "PAPIER"},
                new String[] {"PUBLICATION_AGPM", "Publication de l'AGPM", null})) {
            cnm.prs.entity.TypePieceJointe t = typePieceJointeRepository.findFirstByCode(p[0]).orElseGet(() -> {
                cnm.prs.entity.TypePieceJointe n = new cnm.prs.entity.TypePieceJointe();
                n.setLibellePiece(p[1]);
                n.setObligatoire(false);
                n.setOrdre(0);
                n.setCode(p[0]);
                return typePieceJointeRepository.save(n);
            });
            cnm.prs.entity.PieceSousType a = new cnm.prs.entity.PieceSousType(t.getIdTypePiece(), "DAOO", true, ordre++, null, null);
            a.setCondition(p[2]);
            pieceSousTypeRepository.save(a);
        }
        Long idDmc = creerDmc();
        besoinDeTest(idDmc);
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", new LinkedHashMap<>());
        String cree = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.<Integer>read(cree, "$.idDossier");

        String exigees = mvc.perform(get("/api/dossiers/" + id + "/pieces-exigees").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(exigees, "$[?(@.fourniePar=='APPLICATION')].code"))
                .containsExactlyInAnyOrder("FICHE_PRESENTATION", "CALENDRIER", "AGPM_CONTROLE");
        assertThat(JsonPath.<List<String>>read(exigees, "$[?(@.code=='FICHE_PRESENTATION')].lien"))
                .containsExactly("/api/dossiers/" + id + "/fiche-presentation");
        assertThat(JsonPath.<List<Integer>>read(exigees, "$[?(@.code=='AGPM_CONTROLE')].idDossierPlan")).containsExactly(9900);
        assertThat(JsonPath.<List<Integer>>read(exigees, "$[?(@.code=='AGPM_CONTROLE')].idPvPlan")).hasSize(1).doesNotContainNull();
        assertThat(JsonPath.<List<Object>>read(exigees, "$[?(@.code=='PUBLICATION_AGPM')].fourniePar")).containsOnlyNulls();
        String erreurs = mvc.perform(post("/api/dossiers/" + id + "/soumettre").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString();
        assertThat(erreurs).doesNotContain("Fiche de présentation datée et signée").doesNotContain("Calendrier annuel de passation")
                .doesNotContain("AGPM (ou PPM) signé et contrôlé");

        String dossier = mvc.perform(get("/api/dossiers/" + id).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(dossier, "$.idDossierPlan")).isEqualTo(9900);
        assertThat(JsonPath.<Integer>read(dossier, "$.idPvPlan")).isNotNull();

        String fp = mvc.perform(get("/api/dossiers/" + id + "/fiche-presentation").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fp, "$.sections[*].code"))
                .containsExactly("IDENTIFICATION", "MARCHE", "PROCEDURE", "COHERENCE", "VALIDATION");
        assertThat(JsonPath.<List<String>>read(fp, "$.sections[?(@.code=='MARCHE')].lignes[?(@.code=='OBJET')].valeur"))
                .containsExactly("Acquisition de matériels informatiques");
        assertThat(JsonPath.<List<String>>read(fp, "$.sections[?(@.code=='MARCHE')].lignes[?(@.code=='MODE')].valeur"))
                .containsExactly("Appel d'offres ouvert");
        assertThat(JsonPath.<List<String>>read(fp, "$.sections[?(@.code=='VALIDATION')].lignes[?(@.code=='VERSION')].valeur"))
                .containsExactly("1");
        assertThat(JsonPath.<List<String>>read(fp, "$.sections[?(@.code=='VALIDATION')].lignes[?(@.code=='VALIDEE_PAR')].valeur"))
                .hasSize(1);
        byte[] pdf = mvc.perform(get("/api/dossiers/" + id + "/fiche-presentation/pdf").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        mvc.perform(get("/api/dossiers/9900/fiche-presentation").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FICHE_PRESENTATION_INDISPONIBLE"));
    }

    private ResultActions deposer(String url, String nom, byte[] contenu) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, url).file(new MockMultipartFile("fichier", nom,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", contenu)).header("Authorization", tokenPrmp));
    }

    static byte[] docx(String texte) throws Exception {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            d.createParagraph().createRun().setText(texte);
            d.write(o);
            return o.toByteArray();
        }
    }

    private String fiche(Long idDmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Long creerDmc() throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
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
