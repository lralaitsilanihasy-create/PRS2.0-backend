package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Notification;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ 2026-10-07 (AMI en ligne, tranche AMI-a, §B1, §B2 ; V82) — la préparation de l'AMI d'une fiche de prestations intellectuelles
 * (critères pondérés sur 100), le projet d'avis, la publication (supports déclarés, avis signé, liste publique), la dispense ; le dépôt,
 * le remplacement et le retrait des expressions d'intérêt avant la date limite, l'accusé, et leur lecture fermée jusqu'à la date limite
 * (arbitrage Q2). Jeu : celui des lettres d'invitation (ligne 9901 PI, ligne 9902 de fournitures).
 */
class AmiIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String CRITERES = "\"criteres\":[{\"libelle\":\"Aptitude\",\"poids\":30},{\"libelle\":\"Références de missions similaires\","
            + "\"poids\":40},{\"libelle\":\"Expérience du personnel\",\"poids\":30}]";

    @Autowired private cnm.prs.service.ChampFicheMarcheService champService;
    @Autowired private cnm.prs.repository.AmiRepository amiRepository;
    @Autowired private cnm.prs.repository.CompteCandidatRepository candidats;
    @Autowired private cnm.prs.repository.NotificationRepository notificationRepository;

    private Long idDmc;
    private Long fournitures;
    private String base;
    private String jetonA;
    private String jetonB;
    private String tokenUgpm;

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
        natureRepository.save(new Nature(94, "Prestations intellectuelles", null, "PRESTATIONS_INTELLECTUELLES"));
        Marche l = marche(9901, 9900, 9900);
        l.setIdMode(92);
        l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        l.setIdNature(94);
        l.setDesignationMarche("Étude de faisabilité du schéma directeur");
        marcheRepository.save(l);
        Marche f = marcheDao(9902, 9900, 9900);
        f.setIdMode(92);
        f.setDesignationMarche("Fourniture de mobilier de bureau");
        marcheRepository.save(f);
        champService.importerCsv(new ClassPathResource(
                "fiche-marche/referentiel-champs-fiche-dao-prestations-intellectuelles.csv").getFile().toPath());
        idDmc = creerDmc(9901);
        fournitures = creerDmc(9902);
        base = "/api/fiches-marche/" + idDmc + "/ami";
        tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", "ANT");
        candidats.save(new CompteCandidat("C900000061", "a@ami.mg", "034 61 611 61", "Rabe", "Paul", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        candidats.save(new CompteCandidat("C900000062", "b@ami.mg", "034 62 622 62", "Rasoa", "Lova", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        jetonA = bearer("a@ami.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000061", null);
        jetonB = bearer("b@ami.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000062", null);
        mvc.perform(put("/api/candidat/entreprise").header("Authorization", jetonA).contentType(JSON).content("{\"raisonSociale\":\"Cabinet Alpha\","
                + "\"nif\":\"1111000111\",\"adresse\":\"Lot A\",\"representant\":{\"nom\":\"Rabe\",\"prenom\":\"Paul\"}}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Préparation (PRMP ou UGPM, fiche PI seule, critères pondérés sur 100), projet d'avis, publication (supports, avis signé, "
            + "liste publique, figé) ; dispense sur une autre fiche impossible hors PI")
    void preparationEtPublication() throws Exception {
        mvc.perform(get(base).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        mvc.perform(put("/api/fiches-marche/" + fournitures + "/ami").header("Authorization", tokenPrmp).contentType(JSON).content("{" + CRITERES + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORIE_SANS_AMI"));
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON).content("{\"criteres\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CRITERES_OBLIGATOIRES"));
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"criteres\":[{\"libelle\":\"Aptitude\",\"poids\":30},{\"libelle\":\"Références\",\"poids\":40}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PONDERATION_INVALIDE"));
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON)
                .content("{" + CRITERES + ",\"dateLimite\":\"" + LocalDateTime.now().minusDays(1).withNano(0) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DATE_LIMITE_INVALIDE"));
        mvc.perform(put(base).header("Authorization", tokenUgpm).contentType(JSON).content("{" + CRITERES + ",\"pieces\":[\"Lettre de "
                + "manifestation d'intérêt signée\",\"Références de missions similaires\"],\"noteMinimale\":60,\"dateLimite\":\""
                + LocalDateTime.now().plusDays(15).withNano(0) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("BROUILLON")).andExpect(jsonPath("$.criteres[1].code").value("C2"))
                .andExpect(jsonPath("$.nombreRetenus").value(6)).andExpect(jsonPath("$.objet").value("Étude de faisabilité du schéma directeur"))
                .andExpect(jsonPath("$.lectureOuverte").value(false));
        String projet = texteDuPdf(mvc.perform(get(base + "/avis").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(projet).contains("PROJET D'AVIS À MANIFESTATION D'INTÉRÊT", "Références de missions similaires", "liste restreinte de 6",
                "Note minimale de qualification : 60").doesNotContain("signé électroniquement");
        mvc.perform(get("/api/amis-en-ligne/" + idDmc)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/publier").header("Authorization", tokenUgpm).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/publier").header("Authorization", tokenPrmp).contentType(JSON).content("{\"publications\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUBLICATION_OBLIGATOIRE"));
        mvc.perform(post(base + "/publier").header("Authorization", tokenPrmp).contentType(JSON).content("{\"publications\":[{\"support\":"
                + "\"Journal des marchés publics de l'ARMP\",\"date\":\"" + LocalDate.now() + "\",\"reference\":\"JMP n° 412\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("PUBLIE")).andExpect(jsonPath("$.publications[0].reference").value("JMP n° 412"));
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON).content("{" + CRITERES + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AMI_PUBLIE"));
        mvc.perform(post(base + "/dispense").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AMI_PUBLIE"));
        mvc.perform(get("/api/amis-en-ligne")).andExpect(status().isOk()).andExpect(jsonPath("$[0].idDmc").value(idDmc))
                .andExpect(jsonPath("$[0].ouvert").value(true));
        String avis = texteDuPdf(mvc.perform(get("/api/amis-en-ligne/" + idDmc + "/avis")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(avis).contains("AVIS À MANIFESTATION D'INTÉRÊT", "signé électroniquement sur la plateforme", "modèle provisoire")
                .doesNotContain("PROJET D'AVIS");
        assertThat(journalRepository().findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction()).contains("AMI_PREPARE", "AMI_PUBLIE");
    }

    @Test
    @DisplayName("Dispense de publicité : motif exigé, PRMP seule, AMI figé ; pas d'avis")
    void dispense() throws Exception {
        mvc.perform(post(base + "/dispense").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(post(base + "/dispense").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"Sous le seuil\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("DISPENSE")).andExpect(jsonPath("$.motifDispense").value("Sous le seuil"));
        mvc.perform(get(base + "/avis").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMI_DISPENSE"));
    }

    @Test
    @DisplayName("Dépôt : pièces attendues exigées, accusé et empreinte, remplacement, retrait ; illisible avant la date limite (Q2), "
            + "lisible après ; plus de dépôt passé la date limite")
    void depotDesExpressions() throws Exception {
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON).content("{" + CRITERES + ",\"pieces\":[\"Références\"],"
                + "\"dateLimite\":\"" + LocalDateTime.now().plusDays(15).withNano(0) + "\"}")).andExpect(status().isOk());
        String url = "/api/candidat/amis/" + idDmc + "/expression";
        MockMultipartFile refs = new MockMultipartFile("fichiers", "refs.pdf", "application/pdf", "%PDF-1.4 r".getBytes(StandardCharsets.ISO_8859_1));
        String corps = "{\"lettre\":\"Nous manifestons notre intérêt.\",\"qualifications\":\"Dix ans d'études\",\"references\":[{\"intitule\":"
                + "\"Schéma directeur de Toamasina\",\"client\":\"Commune\",\"annee\":2024}],\"pieces\":[{\"libelle\":\"Références\",\"fichier\":\"refs.pdf\"}]}";
        mvc.perform(multipart(url).file(refs).param("expression", corps).header("Authorization", jetonA)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/publier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"publications\":[{\"support\":\"Journal national\",\"date\":\"" + LocalDate.now() + "\"}]}")).andExpect(status().isOk());
        mvc.perform(multipart(url).file(refs).param("expression", corps).header("Authorization", jetonB)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENTREPRISE_NON_DECLAREE"));
        mvc.perform(multipart(url).param("expression", "{\"lettre\":\"x\"}").header("Authorization", jetonA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PIECES_MANQUANTES")).andExpect(jsonPath("$.details.pieces[0]").value("Références"));
        mvc.perform(multipart(url).file(refs).param("expression", "{\"pieces\":[]}").header("Authorization", jetonA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LETTRE_OBLIGATOIRE"));
        String premiere = mvc.perform(multipart(url).file(refs).param("expression", corps).header("Authorization", jetonA))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(1)).andExpect(jsonPath("$.empreinte").isNotEmpty())
                .andExpect(jsonPath("$.pieces[0].libelle").value("Références")).andReturn().getResponse().getContentAsString();
        assertThat(notificationRepository.findAll()).filteredOn(n -> "C900000061".equals(n.getDestinataireRef()))
                .extracting(Notification::getTypeNotif).contains("AMI_EXPRESSION_DEPOSEE");
        mvc.perform(multipart(url).file(refs).param("expression", corps.replace("Dix ans", "Douze ans")).header("Authorization", jetonA))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(2)).andExpect(jsonPath("$.qualifications").value("Douze ans d'études"));
        long idPiece = JsonPath.<Number>read(mvc.perform(get(url).header("Authorization", jetonA)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.pieces[0].id").longValue();
        mvc.perform(get(url + "/pieces/" + idPiece).header("Authorization", jetonA)).andExpect(status().isOk());
        assertThat(JsonPath.<String>read(premiere, "$.id")).isNotBlank();
        // Q2 : le nombre se lit, le contenu non, avant la date limite.
        mvc.perform(get(base).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.nombreExpressions").value(1));
        mvc.perform(get(base + "/expressions").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURE_FERMEE"));
        mvc.perform(delete(url).header("Authorization", jetonA)).andExpect(status().isNoContent());
        mvc.perform(get(base).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.nombreExpressions").value(0));
        mvc.perform(multipart(url).file(refs).param("expression", corps).header("Authorization", jetonA)).andExpect(status().isCreated());
        // La date limite passée : lecture ouverte, plus de dépôt ni de retrait.
        cnm.prs.entity.Ami a = amiRepository.findById(idDmc).orElseThrow();
        a.setDateLimite(LocalDateTime.now().minusMinutes(1));
        amiRepository.save(a);
        mvc.perform(multipart(url).file(refs).param("expression", corps).header("Authorization", jetonA)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATE_LIMITE_DEPASSEE"));
        mvc.perform(delete(url).header("Authorization", jetonA)).andExpect(status().isConflict());
        String lues = mvc.perform(get(base + "/expressions").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(lues, "$[*].raisonSociale")).containsExactly("Cabinet Alpha");
        assertThat(JsonPath.<String>read(lues, "$[0].lettre")).isEqualTo("Nous manifestons notre intérêt.");
        String idExpression = JsonPath.read(lues, "$[0].id");
        long idP = JsonPath.<Number>read(lues, "$[0].pieces[0].id").longValue();
        mvc.perform(get(base + "/expressions/" + idExpression + "/pieces/" + idP).header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(get("/api/amis-en-ligne")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/amis-en-ligne/" + idDmc)).andExpect(status().isOk()).andExpect(jsonPath("$.ouvert").value(false));
    }

    private cnm.prs.repository.EvaluationJournalRepository journalRepository() {
        return journal;
    }

    @Autowired private cnm.prs.repository.EvaluationJournalRepository journal;

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
        if (idDetail == 9901) {
            remplirObligatoiresEtValider(id, "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES",
                    Map.of("B02-MS-01", "Budget prédéterminé dont le candidat propose la meilleure utilisation"));
        }
        return id;
    }
}
