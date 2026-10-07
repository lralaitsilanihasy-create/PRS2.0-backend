package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.EvaluationDecision;
import cnm.prs.entity.EvaluationDemande;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Notification;
import cnm.prs.entity.Offre;
import cnm.prs.entity.Seance;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.EntrepriseRepository;
import cnm.prs.repository.EvaluationDecisionRepository;
import cnm.prs.repository.EvaluationDemandeRepository;
import cnm.prs.repository.EvaluationJournalRepository;
import cnm.prs.repository.NotificationRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.SeanceRepository;
import cnm.prs.service.ChampFicheMarcheService;
import cnm.prs.service.ParametreService;
import cnm.prs.service.RemiseElectronique;

/**
 * ⚠️ 2026-10-07 (demande front « évaluation des offres », lot 1, tranche 1a ; V76) — l'ouverture de l'évaluation après le PV signé,
 * la déclaration préalable, l'examen préliminaire sur la grille pré-remplie, l'arrêt et la réouverture d'une étape par le président,
 * les décisions en ajout seul ; les demandes de précisions de la PRMP et la réponse du candidat. La séance close et les offres ouvertes
 * sont posées en base (le déchiffrement est éprouvé par {@code SeanceIntegrationTest}).
 */
class EvaluationIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;
    @Autowired private ParametreService parametres;
    @Autowired private CompteCandidatRepository candidats;
    @Autowired private EntrepriseRepository entrepriseRepository;
    @Autowired private OffreRepository offreRepository;
    @Autowired private SeanceRepository seanceRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EvaluationDecisionRepository decisionRepository;
    @Autowired private EvaluationDemandeRepository demandeRepository;
    @Autowired private EvaluationJournalRepository journalRepository;
    @Autowired private cnm.prs.service.StockageOffres stockage;

    private final LocalDate aujourdhui = LocalDate.now();
    private final List<String> comptes = new ArrayList<>();
    private String tokenVer;
    private String tokenUgpm;
    private String jetonA;
    private String jetonB;
    private String jetonM1;
    private String jetonM2;
    private Long idDmc;
    private String base;

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
        l.setMontEstim(new java.math.BigDecimal("12000000"));   // ⚠️ tranche 1c : l'estimation des indicateurs de prix
        marcheRepository.save(l);
        capmRepository.save(new cnm.prs.entity.Capm(9901, "Lancement de l'appel d'offres", 1, 92, null));
        marchePrevisionRepository.save(new MarchePrevision(9901, 9901, 9901, aujourdhui.plusDays(10), aujourdhui.plusDays(10), null, null));
        for (String f : List.of("referentiel-champs-fiche-marche-fournitures.csv", "referentiel-champs-fiche-dao-travaux.csv")) {
            assertThat(champService.importerCsv(new ClassPathResource("fiche-marche/" + f).getFile().toPath()).rejets()).isEmpty();
        }
        tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", "ANT");
        RemiseElectronique.Parametres p = parametres.remiseElectronique();
        parametres.fixerRemiseElectronique(new RemiseElectronique.Parametres(p.plateformeUrl(), p.fuseau(), "Simple",
                p.tailleMaxPlateformeMo(), p.delaiMinRemiseJours(), p.assistance(), p.quorumDefaut(), p.verificationPartJours()));
        ficheEnLigne();
        base = "/api/fiches-marche/" + idDmc + "/evaluation";
        candidats.save(new CompteCandidat("C900000051", "a@eval.mg", "034 51 511 51", "Rabe", "Paul", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        candidats.save(new CompteCandidat("C900000052", "b@eval.mg", "034 52 522 52", "Rasoa", "Lova", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        candidats.save(new CompteCandidat("C900000053", "c@eval.mg", "034 53 533 53", "Rakoto", "Fara", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        jetonA = bearer("a@eval.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000051", null);
        jetonB = bearer("b@eval.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000052", null);
        declarer(jetonA, "1111222333", "BTP Alpha");
        declarer(jetonB, "4444555666", "BTP Beta");
        declarer(bearer("c@eval.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000053", null), "7777888999", "BTP Gamma");
    }

    @Test
    @DisplayName("§B1-§B2 : ouverte après le PV signé par le responsable ; déclaration préalable exigée, membre en conflit écarté des "
            + "décisions ; grille pré-remplie (prix, garantie, NIF en double par groupement, intégrité) ; écarter exige motif, clause et "
            + "qualification ; décisions remplacées gardées ; le président arrête l'étape complète, la rouvre avec un motif ; journal")
    void examenPreliminaire() throws Exception {
        String a = offre("C900000051", "1111222333", "BTP Alpha", 1, "INTACTE", null, "12500000");
        String b = offre("C900000052", "4444555666", "BTP Beta", 2, "ALTEREE", null, "11900000");
        String c = offre("C900000053", "7777888999", "BTP Gamma", 3, "INTACTE", "7777888999,1111222333", "13100000");
        Offre ecartee = offreRepository.findById(offre("C900000052", "4444555666", "BTP Beta", 4, "INTACTE", null, "1")).orElseThrow();
        ecartee.setEtat(Offre.ECARTEE);
        ecartee.setMotifEcartement("Entreprise exclue par l'ARMP");
        offreRepository.save(ecartee);

        mvc.perform(get(base).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEANCE_NON_CLOSE"));
        seanceClose();
        mvc.perform(post(base + "/ouvrir").header("Authorization", jetonM1)).andExpect(status().isForbidden());
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        String ev = mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.etat").value("EN_COURS")).andReturn().getResponse().getContentAsString();
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_DEJA_OUVERTE"));
        assertThat(notificationRepository.findPourRefEtType(comptes.get(0), "MEMBRE_CAO")).extracting(Notification::getTypeNotif)
                .contains("EVALUATION_OUVERTE");
        assertThat(JsonPath.<List<String>>read(ev, "$.declarations[*].membre")).containsExactly(comptes.get(0), comptes.get(1));
        assertThat(JsonPath.<List<Boolean>>read(ev, "$.declarations[*].president")).containsExactly(true, false);
        assertThat(JsonPath.<List<Object>>read(ev, "$.declarations[*].signeeLe")).containsOnlyNulls();
        assertThat(JsonPath.<List<Integer>>read(ev, "$.lots[*].lot")).containsExactly(1);
        assertThat(JsonPath.<String>read(ev, "$.lots[0].etape")).isEqualTo("CONFORMITE");
        assertThat(JsonPath.<List<String>>read(ev, "$.lots[0].offres[*].idOffre")).containsExactly(a, b, c);
        assertThat(JsonPath.<List<String>>read(ev, "$.nonEvaluees[*].etat")).containsExactly("ECARTEE");
        // La grille pré-remplie : des constats, la CAO décide.
        assertThat(verif(ev, a, "AE_PRIX", "proposee")).isEqualTo(true);
        assertThat(verif(ev, a, "GARANTIE", "proposee")).isEqualTo(true);
        assertThat(verif(ev, a, "INTEGRITE", "proposee")).isEqualTo(true);
        assertThat(verif(ev, b, "INTEGRITE", "proposee")).isEqualTo(false);
        assertThat(verif(ev, a, "OFFRE_UNIQUE", "proposee")).isEqualTo(false);
        assertThat((String) verif(ev, a, "OFFRE_UNIQUE", "constat")).contains("n° 3 (BTP Gamma)");
        assertThat(verif(ev, b, "OFFRE_UNIQUE", "proposee")).isEqualTo(true);
        assertThat(verif(ev, a, "FRAIS_DOSSIER", "proposee")).isNull();
        assertThat(JsonPath.<List<Object>>read(ev, "$.lots[0].offres[*].conformite.decision")).containsOnlyNulls();
        for (String jeton : List.of(tokenPrmp, tokenUgpm, jetonM2)) {
            mvc.perform(get(base).header("Authorization", jeton)).andExpect(status().isOk());
        }
        mvc.perform(get(base).header("Authorization", jetonA)).andExpect(status().isForbidden());

        // P6 : la déclaration d'abord ; un membre en conflit ne décide rien.
        conformite(jetonM2, a, "{\"decision\":\"CONFORME\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DECLARATION_MANQUANTE"));
        mvc.perform(post(base + "/declaration").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/declaration").header("Authorization", jetonM2).contentType(JSON)
                .content("{\"conflit\":true,\"precision\":\"Parent du gérant de BTP Beta\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.declarations[1].conflit").value(true));
        mvc.perform(post(base + "/declaration").header("Authorization", jetonM2).contentType(JSON).content("{\"conflit\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_DECLARE"));
        conformite(jetonM2, a, "{\"decision\":\"CONFORME\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBRE_EN_CONFLIT"));
        mvc.perform(post(base + "/declaration").header("Authorization", jetonM1).contentType(JSON).content("{\"conflit\":false}"))
                .andExpect(status().isOk());

        // Écarter : motif, clause, qualification.
        conformite(jetonM1, b, "{\"decision\":\"REJETEE\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DECISION_INVALIDE"));
        conformite(jetonM1, b, "{\"decision\":\"ECARTEE\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        conformite(jetonM1, b, "{\"decision\":\"ECARTEE\",\"motif\":\"Offre altérée\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLAUSE_OBLIGATOIRE"));
        conformite(jetonM1, b, "{\"decision\":\"ECARTEE\",\"motif\":\"Offre altérée\",\"clause\":\"IC 22.1\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUALIFICATION_OBLIGATOIRE"));
        conformite(jetonM1, b, "{\"decision\":\"CONFORME\",\"verifications\":[{\"code\":\"INCONNU\",\"satisfaite\":true}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VERIFICATION_INCONNUE"));
        conformite(jetonM1, a, "{\"decision\":\"CONFORME\",\"verifications\":[{\"code\":\"OFFRE_UNIQUE\",\"satisfaite\":true,"
                + "\"observation\":\"Le groupement de BTP Gamma ne compte pas BTP Alpha : NIF saisi par erreur\"}]}").andExpect(status().isOk());

        // Arrêter : le président, une fois chaque offre décidée.
        mvc.perform(post(base + "/lots/1/etapes/CONFORMITE/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_INCOMPLETE"))
                .andExpect(jsonPath("$.details.offres[0]").value(2)).andExpect(jsonPath("$.details.offres[1]").value(3));
        conformite(jetonM1, b, "{\"decision\":\"ECARTEE\",\"qualification\":\"IRRECEVABLE\",\"motif\":\"Offre altérée à l'ouverture\","
                + "\"clause\":\"IC 22.1\"}").andExpect(status().isOk());
        conformite(jetonM1, c, "{\"decision\":\"CONFORME\"}").andExpect(status().isOk());
        String apres = conformite(jetonM1, c, "{\"decision\":\"ECARTEE\",\"qualification\":\"NON_CONFORME\",\"motif\":\"Seconde offre du "
                + "même candidat (groupement)\",\"clause\":\"IC 2.3\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(apres, "$.lots[0].offres[2].ecartee.qualification")).isEqualTo("NON_CONFORME");
        assertThat(verif(apres, a, "OFFRE_UNIQUE", "satisfaite")).isEqualTo(true);
        assertThat((String) verif(apres, a, "OFFRE_UNIQUE", "observation")).contains("saisi par erreur");
        assertThat(decisionRepository.findAll()).filteredOn(d -> d.getIdOffre().equals(c)).extracting(EvaluationDecision::getDecision)
                .containsExactly("CONFORME", "ECARTEE");
        assertThat(decisionRepository.findAll()).filteredOn(d -> d.getIdOffre().equals(c) && d.getRemplaceeLe() == null).hasSize(1);
        mvc.perform(post(base + "/lots/1/etapes/EVALUATION/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_PRECEDENTE_OUVERTE"));
        mvc.perform(post(base + "/lots/1/etapes/CONFORMITE/arreter").header("Authorization", jetonM2).contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/lots/1/etapes/CONFORMITE/arreter").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"observation\":\"Deux offres écartées\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].etape").value("EVALUATION"))
                .andExpect(jsonPath("$.lots[0].etapesArretees[0].etape").value("CONFORMITE"))
                .andExpect(jsonPath("$.lots[0].etapesArretees[0].observation").value("Deux offres écartées"));
        conformite(jetonM1, a, "{\"decision\":\"ECARTEE\",\"qualification\":\"IRRECEVABLE\",\"motif\":\"x\",\"clause\":\"y\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_ARRETEE"));
        mvc.perform(post(base + "/lots/1/etapes/CONFORMITE/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_ARRETEE"));
        // ⚠️ Tranche 1b — l'étape 3 s'arrête une fois chaque offre retenue évaluée (ici la n° 1, seule retenue) ; l'étape 4 attend la 3.
        mvc.perform(post(base + "/lots/1/etapes/EVALUATION/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_INCOMPLETE"))
                .andExpect(jsonPath("$.details.offres[0]").value(1)).andExpect(jsonPath("$.details.offres.length()").value(1));
        mvc.perform(post(base + "/lots/1/etapes/ANORMALES/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_PRECEDENTE_OUVERTE"));
        mvc.perform(post(base + "/lots/1/etapes/AUTRE/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ETAPE_INCONNUE"));
        mvc.perform(post(base + "/lots/2/etapes/CONFORMITE/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isNotFound());

        // Rouvrir : le président, avec un motif ; l'arrêt rouvert reste au registre.
        mvc.perform(post(base + "/lots/1/etapes/CONFORMITE/rouvrir").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(post(base + "/lots/1/etapes/CONFORMITE/rouvrir").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"motif\":\"Pièce de BTP Alpha à revoir\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].etape").value("CONFORMITE")).andExpect(jsonPath("$.lots[0].etapesArretees.length()").value(0));
        mvc.perform(post(base + "/lots/1/etapes/CONFORMITE/rouvrir").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"motif\":\"encore\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_NON_ARRETEE"));
        mvc.perform(post(base + "/lots/1/etapes/CONFORMITE/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isOk());

        String journal = mvc.perform(get(base + "/journal").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[*].action")).containsSubsequence("OUVERTURE", "DECLARATION", "DECLARATION",
                "CONFORMITE", "ARRET", "REOUVERTURE", "ARRET");
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.action=='CONFORMITE')].detail")).anyMatch(d -> d.contains("CONFORME → ECARTEE"));
        mvc.perform(get(base + "/journal").header("Authorization", tokenUgpm)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("§B2, art. 35-VI : la PRMP demande des précisions (question exigée, délai de la fiche par défaut), le candidat les lit et "
            + "répond une fois dans le délai (texte, fichier PDF) ; la CAO lit la réponse et son fichier ; notifications des deux côtés")
    void precisions() throws Exception {
        String a = offre("C900000051", "1111222333", "BTP Alpha", 1, "INTACTE", null, "12500000");
        seanceClose();
        mvc.perform(post(base + "/offres/" + a + "/precisions").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"question\":\"?\"}")).andExpect(status().isNotFound());   // évaluation non ouverte
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isCreated());
        mvc.perform(post(base + "/offres/" + a + "/precisions").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUESTION_OBLIGATOIRE"));
        mvc.perform(post(base + "/offres/" + a + "/precisions").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"question\":\"Q\"}")).andExpect(status().isForbidden());
        String d = mvc.perform(post(base + "/offres/" + a + "/precisions").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"question\":\"Précisez la marque des onduleurs proposés.\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.delaiJours").value(5)).andExpect(jsonPath("$.etat").value("EN_ATTENTE"))
                .andReturn().getResponse().getContentAsString();
        long idDemande = ((Number) JsonPath.read(d, "$.idDemande")).longValue();
        assertThat(notificationRepository.findPourRefEtType("C900000051", "CANDIDAT")).extracting(Notification::getTypeNotif)
                .contains("PRECISION_DEMANDEE");
        mvc.perform(get(base).header("Authorization", jetonM1)).andExpect(jsonPath("$.lots[0].offres[0].precisionsEnAttente").value(1));

        String url = "/api/candidat/offres/" + a + "/precisions";
        mvc.perform(get(url).header("Authorization", jetonB)).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].question").value("Précisez la marque des onduleurs proposés."));
        mvc.perform(multipart(url + "/" + idDemande + "/reponse").header("Authorization", jetonA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEXTE_OBLIGATOIRE"));
        mvc.perform(multipart(url + "/" + idDemande + "/reponse").file(new MockMultipartFile("fichier", "fiche.txt", "text/plain", "x".getBytes()))
                .param("texte", "Marque APC").header("Authorization", jetonA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FORMAT_INVALIDE"));
        mvc.perform(multipart(url + "/" + idDemande + "/reponse").file(new MockMultipartFile("fichier", "fiche-technique.pdf",
                "application/pdf", "%PDF-1.4 fiche".getBytes(StandardCharsets.UTF_8))).param("texte", "Marque APC, modèle Smart-UPS 1500.")
                .header("Authorization", jetonA)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("REPONDUE"))
                .andExpect(jsonPath("$.fichier").value("fiche-technique.pdf"));
        mvc.perform(multipart(url + "/" + idDemande + "/reponse").param("texte", "encore").header("Authorization", jetonA))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_REPONDU"));
        assertThat(notificationRepository.findPourPrmp("PRMP001", null)).extracting(Notification::getTypeNotif).contains("PRECISION_RECUE");
        mvc.perform(get(base + "/offres/" + a + "/precisions").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reponse").value("Marque APC, modèle Smart-UPS 1500."));
        byte[] f = mvc.perform(get(base + "/demandes/" + idDemande + "/fichier").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(f, StandardCharsets.UTF_8)).startsWith("%PDF");
        mvc.perform(get(base + "/demandes/" + idDemande + "/fichier").header("Authorization", jetonA)).andExpect(status().isForbidden());

        // Une seconde demande, délai saisi, expirée : la réponse est refusée.
        String d2 = mvc.perform(post(base + "/offres/" + a + "/precisions").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"question\":\"Délai de garantie ?\",\"delaiJours\":2}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.delaiJours").value(2)).andReturn().getResponse().getContentAsString();
        EvaluationDemande x = demandeRepository.findById(((Number) JsonPath.read(d2, "$.idDemande")).longValue()).orElseThrow();
        x.setEcheance(LocalDateTime.now().minusMinutes(1));
        demandeRepository.save(x);
        mvc.perform(multipart(url + "/" + x.getId() + "/reponse").param("texte", "12 mois").header("Authorization", jetonA))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELAI_DEPASSE"));
        mvc.perform(get(url).header("Authorization", jetonA)).andExpect(jsonPath("$[1].etat").value("EXPIREE"));
        assertThat(journalRepository.findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction())
                .contains("PRECISION_DEMANDEE", "PRECISION_RECUE");
    }

    @Test
    @DisplayName("§B3 (tranche 1b) : corrections proposées depuis le bordereau scellé (lettres, prix unitaire), montant évalué hors taxes "
            + "(corrections retenues, rabais, préférence de la fiche, critère du DAO), refus du candidat constaté, classement, égalité en tête "
            + "à départager avant l'arrêt, tableau du guide")
    void evaluationDetaillee() throws Exception {
        String a = offre("C900000051", "1111222333", "BTP Alpha", 1, "INTACTE", null, "12600000");
        String b = offre("C900000052", "4444555666", "BTP Beta", 2, "INTACTE", null, "11900000");
        String c = offre("C900000053", "7777888999", "BTP Gamma", 3, "INTACTE", null, "9000000");
        List<Integer> articles = JsonPath.read(mvc.perform(get("/api/fiches-marche/" + idDmc + "/articles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$[*].idArticle");
        // Le bordereau de A : 10 × 1 000 000 (en lettres « un million cent mille ») + 5 × 500 000 = 12 500 000, l'acte dit 12 600 000.
        clair(a, "{\"acteEngagement\":{\"montantHt\":\"12600000\"},\"formulaires\":{\"bordereau\":[{\"idArticle\":" + articles.get(0)
                + ",\"prixUnitaireHt\":\"1000000\",\"prixEnLettres\":\"un million cent mille\"},{\"idArticle\":" + articles.get(1)
                + ",\"prixUnitaireHt\":\"500000\"}]}}");
        seanceClose();
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isCreated());
        mvc.perform(post(base + "/declaration").header("Authorization", jetonM1).contentType(JSON).content("{\"conflit\":false}"))
                .andExpect(status().isOk());
        conformite(jetonM1, a, "{\"decision\":\"CONFORME\"}").andExpect(status().isOk());
        conformite(jetonM1, b, "{\"decision\":\"CONFORME\"}").andExpect(status().isOk());
        conformite(jetonM1, c, "{\"decision\":\"ECARTEE\",\"qualification\":\"NON_CONFORME\",\"motif\":\"Spécifications non respectées\","
                + "\"clause\":\"CCAP 3\"}").andExpect(status().isOk());
        montant(jetonM1, a, "{\"corrections\":[]}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_PRECEDENTE_OUVERTE"));
        mvc.perform(post(base + "/lots/1/etapes/CONFORMITE/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isOk());

        // Les corrections proposées.
        String proposees = mvc.perform(get(base + "/offres/" + a + "/corrections-proposees").header("Authorization", tokenUgpm))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(proposees, "$[*].regle")).containsExactly("LETTRES_PREVALENT", "PU_PREVAUT");
        assertThat(JsonPath.<Number>read(proposees, "$[0].avant").longValue()).isEqualTo(10_000_000L);
        assertThat(JsonPath.<Number>read(proposees, "$[0].apres").longValue()).isEqualTo(11_000_000L);
        assertThat(JsonPath.<Number>read(proposees, "$[1].avant").longValue()).isEqualTo(12_600_000L);
        assertThat(JsonPath.<Number>read(proposees, "$[1].apres").longValue()).isEqualTo(12_500_000L);
        mvc.perform(get(base + "/offres/" + b + "/corrections-proposees").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));   // sans bordereau scellé
        mvc.perform(get(base + "/offres/" + c + "/corrections-proposees").header("Authorization", jetonM1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFRE_ECARTEE"));

        // Les refus nommés.
        montant(jetonM1, c, "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFRE_ECARTEE"));
        montant(jetonM1, a, "{\"corrections\":[{\"libelle\":\"x\",\"avant\":1,\"apres\":2,\"regle\":\"AU_JUGE\",\"retenue\":true}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REGLE_INCONNUE"));
        montant(jetonM1, a, "{\"rabais\":{\"montant\":-1}}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RABAIS_INVALIDE"));
        montant(jetonM1, a, "{\"preference\":{\"eligible\":true}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        montant(jetonM1, b, "{\"criteres\":[{\"libelle\":\"Délai\",\"montant\":310000}]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRITERE_INVALIDE"));
        montant(jetonM1, a, "{\"refusCandidat\":{\"motif\":\"Refuse la correction\"}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLAUSE_OBLIGATOIRE"));

        // Q2 : le refus du candidat, constaté par la CAO, écarte l'offre à l'étape 3 ; une nouvelle saisie le remplace.
        String refus = montant(jetonM1, a, "{\"refusCandidat\":{\"motif\":\"Le candidat refuse la correction des lettres\",\"clause\":\"IC 28.2\"}}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(refus, "$.lots[0].offres[0].ecartee.etape")).isEqualTo("EVALUATION");
        assertThat(JsonPath.<Object>read(refus, "$.lots[0].offres[0].evaluation.montantEvalue")).isNull();
        // A : 12 600 000 + 1 000 000 (lettres) − 100 000 (report) = 13 500 000, rabais 100 000, éligible : 13 400 000.
        String ev = montant(jetonM1, a, "{\"corrections\":" + retenues(proposees) + ",\"rabais\":{\"montant\":100000,\"lecture\":\"Rabais de "
                + "100 000 sur le total\"},\"preference\":{\"eligible\":true,\"motif\":\"Entreprise de droit malgache\"}}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(ev, "$.lots[0].offres[0].evaluation.prixCorrige").longValue()).isEqualTo(13_500_000L);
        assertThat(JsonPath.<Number>read(ev, "$.lots[0].offres[0].evaluation.montantEvalue").longValue()).isEqualTo(13_400_000L);
        assertThat(JsonPath.<Object>read(ev, "$.lots[0].offres[0].ecartee")).isNull();
        // B : 11 900 000, non éligible : + 10 % (1 190 000), critère du DAO + 310 000 = 13 400 000 — égalité avec A.
        ev = montant(jetonM1, b, "{\"criteres\":[{\"libelle\":\"Délai de livraison\",\"montant\":310000,\"justification\":\"Six semaines "
                + "au lieu de quatre\"}]}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(ev, "$.lots[0].offres[1].evaluation.preference.ajustement").longValue()).isEqualTo(1_190_000L);
        assertThat(JsonPath.<Number>read(ev, "$.lots[0].offres[1].evaluation.montantEvalue").longValue()).isEqualTo(13_400_000L);
        assertThat(JsonPath.<List<Integer>>read(ev, "$.lots[0].offres[*].rang")).containsExactly(1, 1, null);
        assertThat(JsonPath.<List<Boolean>>read(ev, "$.lots[0].offres[*].exAequo")).containsExactly(true, true, null);
        mvc.perform(post(base + "/lots/1/etapes/EVALUATION/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EGALITE_A_DEPARTAGER"))
                .andExpect(jsonPath("$.details.offres[0]").value(1)).andExpect(jsonPath("$.details.offres[1]").value(2));
        mvc.perform(post(base + "/lots/1/departage").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"ordre\":[\"" + b + "\",\"" + c + "\"],\"motif\":\"x\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDRE_INVALIDE"));
        mvc.perform(post(base + "/lots/1/departage").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"ordre\":[\"" + b + "\",\"" + a + "\"]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(post(base + "/lots/1/departage").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"ordre\":[\"" + b + "\",\"" + a + "\"],\"motif\":\"Délai d'exécution plus court\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].offres[0].rang").value(2)).andExpect(jsonPath("$.lots[0].offres[1].rang").value(1))
                .andExpect(jsonPath("$.lots[0].offres[1].exAequo").value(false));
        mvc.perform(post(base + "/lots/1/etapes/EVALUATION/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lots[0].etape").value("ANORMALES"));
        montant(jetonM1, b, "{}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_ARRETEE"));
        mvc.perform(post(base + "/lots/1/etapes/ANORMALES/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_INCOMPLETE"));   // ⚠️ tranche 1c : chaque offre classée examinée au regard de son prix

        // Le tableau du guide : par rang, l'offre écartée en dernier avec son motif.
        String tableau = mvc.perform(get(base + "/lots/1/tableau").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(tableau, "$[*].numero")).containsExactly(2, 1, 3);
        assertThat(JsonPath.<List<Integer>>read(tableau, "$[*].rang")).containsExactly(1, 2, null);
        assertThat(JsonPath.<Number>read(tableau, "$[1].rabais").longValue()).isEqualTo(100_000L);
        assertThat(JsonPath.<Number>read(tableau, "$[0].ajustements").longValue()).isEqualTo(1_500_000L);
        assertThat(JsonPath.<String>read(tableau, "$[0].garantie")).isEqualTo("1 700 000 MGA");
        assertThat(JsonPath.<String>read(tableau, "$[2].motifRejet")).isEqualTo("Spécifications non respectées (CCAP 3)");
        assertThat(JsonPath.<Boolean>read(tableau, "$[2].conforme")).isFalse();
        assertThat(journalRepository.findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction()).contains("MONTANT", "DEPARTAGE");
    }

    @Test
    @DisplayName("§B4-§B5 (tranche 1c) : indicateurs de prix, offre suspectée, aucun rejet sans justification demandée puis reçue, "
            + "reclassement ; post-qualification au tour par tour, critères du DAO tous décidés, le suivant après un échec ; proposition")
    void anormalesEtQualification() throws Exception {
        String a = offre("C900000051", "1111222333", "BTP Alpha", 1, "INTACTE", null, "12500000");
        String b = offre("C900000052", "4444555666", "BTP Beta", 2, "INTACTE", null, "11900000");
        String c = offre("C900000053", "7777888999", "BTP Gamma", 3, "INTACTE", null, "9000000");
        String jetonC = bearer("c@eval.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000053", null);
        seanceClose();
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isCreated());
        mvc.perform(post(base + "/declaration").header("Authorization", jetonM1).contentType(JSON).content("{\"conflit\":false}"))
                .andExpect(status().isOk());
        for (String o : List.of(a, b, c)) {
            conformite(jetonM1, o, "{\"decision\":\"CONFORME\"}").andExpect(status().isOk());
        }
        arreter("CONFORMITE").andExpect(status().isOk());
        for (String o : List.of(a, b, c)) {
            montant(jetonM1, o, "{\"preference\":{\"eligible\":true,\"motif\":\"Entreprise nationale\"}}").andExpect(status().isOk());
        }
        mvc.perform(get(base + "/lots/1/qualification").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASSEMENT_NON_ARRETE"));
        anormale(c, "{\"suspectee\":false}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_PRECEDENTE_OUVERTE"));
        arreter("EVALUATION").andExpect(status().isOk());

        // Les indicateurs : C est à 25 % sous l'estimation.
        String ind = mvc.perform(get(base + "/lots/1/indicateurs-prix").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(ind, "$.estimation").longValue()).isEqualTo(12_000_000L);
        assertThat(JsonPath.<Number>read(ind, "$.moyenne").longValue()).isEqualTo(11_133_333L);
        assertThat(JsonPath.<List<Integer>>read(ind, "$.offres[*].numero")).containsExactly(3, 2, 1);
        assertThat(JsonPath.<Number>read(ind, "$.offres[0].ecartEstimation").doubleValue()).isEqualTo(-25.0);
        arreter("ANORMALES").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_INCOMPLETE"))
                .andExpect(jsonPath("$.details.offres.length()").value(3));

        // C suspectée : pas de rejet sans demande écrite, ni avant la réponse ou l'échéance.
        anormale(c, "{\"suspectee\":true}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        anormale(c, "{\"suspectee\":true,\"motif\":\"25 % sous l'estimation\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].offres[2].anormale.decision").value("SUSPECTEE"));
        anormale(c, "{\"suspectee\":true,\"decision\":\"REJETEE\",\"motif\":\"Prix non tenable\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JUSTIFICATION_NON_DEMANDEE"));
        mvc.perform(post(base + "/offres/" + c + "/justification").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"elements\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/offres/" + c + "/justification").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ELEMENTS_OBLIGATOIRES"));
        mvc.perform(post(base + "/offres/" + c + "/justification").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"elements\":\"Sous-détails des prix unitaires des articles 1 et 2\",\"delaiJours\":3}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("JUSTIFICATION")).andExpect(jsonPath("$.delaiJours").value(3));
        mvc.perform(post(base + "/offres/" + c + "/justification").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"elements\":\"encore\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_DEMANDEE"));
        assertThat(notificationRepository.findPourRefEtType("C900000053", "CANDIDAT")).extracting(Notification::getTypeNotif)
                .contains("JUSTIFICATION_DEMANDEE");
        anormale(c, "{\"suspectee\":true,\"decision\":\"REJETEE\",\"motif\":\"Prix non tenable\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELAI_EN_COURS"));
        mvc.perform(get("/api/candidat/offres/" + c + "/justification").header("Authorization", jetonA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/candidat/offres/" + c + "/justification").header("Authorization", jetonC)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].question").value("Sous-détails des prix unitaires des articles 1 et 2"));
        mvc.perform(multipart("/api/candidat/offres/" + c + "/justification/reponse").param("texte", "Stock acquis l'an dernier.")
                .header("Authorization", jetonC)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("REPONDUE"));
        assertThat(notificationRepository.findPourPrmp("PRMP001", null)).extracting(Notification::getTypeNotif).contains("JUSTIFICATION_RECUE");
        String ev = anormale(c, "{\"suspectee\":true,\"decision\":\"REJETEE\",\"motif\":\"Justification insuffisante : stock non prouvé\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(ev, "$.lots[0].offres[2].ecartee.etape")).isEqualTo("ANORMALES");
        assertThat(JsonPath.<String>read(ev, "$.lots[0].offres[2].anormale.justification.etat")).isEqualTo("REPONDUE");
        assertThat(JsonPath.<List<Integer>>read(ev, "$.lots[0].offres[*].rang")).containsExactly(2, 1, null);   // reclassement
        anormale(a, "{\"suspectee\":false}").andExpect(status().isOk());
        anormale(b, "{\"suspectee\":false}").andExpect(status().isOk());
        arreter("ANORMALES").andExpect(status().isOk()).andExpect(jsonPath("$.lots[0].etape").value("QUALIFICATION"));

        // La post-qualification : B (premier classé), puis A après son échec.
        String q = mvc.perform(get(base + "/lots/1/qualification").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(q, "$.idOffre")).isEqualTo(b);
        List<String> codes = JsonPath.read(q, "$.criteres[*].code");
        assertThat(codes).startsWith("JURIDIQUE");
        assertThat(JsonPath.<List<String>>read(q, "$.criteres[*].groupe")).allMatch(g -> List.of("JURIDIQUE", "FINANCIERE", "TECHNIQUE").contains(g));
        qualifier(a, criteres(codes, null) + ",\"decision\":\"QUALIFIE\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAS_LE_TOUR_DE_CETTE_OFFRE"));
        qualifier(b, "\"criteres\":[],\"decision\":\"QUALIFIE\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRITERES_INCOMPLETS"));
        qualifier(b, criteres(codes, "JURIDIQUE") + ",\"decision\":\"NON_QUALIFIE\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        String echec = criteres(codes, "JURIDIQUE").replace("\"NON_SATISFAIT\"}", "\"NON_SATISFAIT\",\"motif\":\"Attestation fiscale périmée\"}");
        qualifier(b, echec + ",\"decision\":\"QUALIFIE\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUALIFICATION_INCOHERENTE"));
        qualifier(b, echec + ",\"decision\":\"NON_QUALIFIE\",\"motif\":\"Situation fiscale non régulière\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLAUSE_OBLIGATOIRE"));
        qualifier(b, echec + ",\"decision\":\"NON_QUALIFIE\",\"motif\":\"Situation fiscale non régulière\",\"clause\":\"IC 6.3\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lots[0].offres[1].ecartee.etape").value("QUALIFICATION"));
        mvc.perform(get(base + "/lots/1/qualification").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idOffre").value(a));
        arreter("QUALIFICATION").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_INCOMPLETE"))
                .andExpect(jsonPath("$.details.offres[0]").value(1));
        qualifier(a, criteres(codes, null) + ",\"decision\":\"QUALIFIE\"}").andExpect(status().isOk());
        String fin = arreter("QUALIFICATION").andExpect(status().isOk()).andExpect(jsonPath("$.lots[0].etape").value("RAPPORT"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(fin, "$.lots[0].proposition.idOffre")).isEqualTo(a);
        assertThat(JsonPath.<Number>read(fin, "$.lots[0].proposition.montant").longValue()).isEqualTo(12_500_000L);
        assertThat(JsonPath.<Boolean>read(fin, "$.lots[0].proposition.infructueux")).isFalse();
        assertThat(JsonPath.<String>read(fin, "$.lots[0].offres[0].qualification.decision")).isEqualTo("QUALIFIE");
        String tableau = mvc.perform(get(base + "/lots/1/tableau").header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(tableau, "$[*].qualifie")).containsExactly(false, true, null);
        assertThat(journalRepository.findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction())
                .contains("ANORMALE", "JUSTIFICATION_DEMANDEE", "JUSTIFICATION_RECUE", "QUALIFICATION");

        // ⚠️ Tranche 1d (§B6) — le rapport : produit par le responsable, signé par les membres appelés (observation, empêchement).
        mvc.perform(get(base + "/rapport").header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/rapport").header("Authorization", jetonM1).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/rapport/signer").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RAPPORT_NON_PRODUIT"));
        String r = mvc.perform(post(base + "/rapport").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"observations\":\"Évaluation conduite sans incident.\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("RAPPORT_A_SIGNER")).andExpect(jsonPath("$.rapport.signe").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(r, "$.rapport.signaturesAttendues[*].im")).containsExactly(comptes.get(0), comptes.get(1));
        mvc.perform(post(base + "/rapport").header("Authorization", tokenVer).contentType(JSON).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RAPPORT_DEJA_PRODUIT"));
        anormale(a, "{\"suspectee\":false}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVALUATION_CLOSE"));
        mvc.perform(post(base + "/lots/1/etapes/QUALIFICATION/rouvrir").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"motif\":\"x\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RAPPORT_SIGNE"));
        assertThat(notificationRepository.findPourRefEtType(comptes.get(1), "MEMBRE_CAO")).extracting(Notification::getTypeNotif)
                .contains("RAPPORT_A_SIGNER");
        mvc.perform(get("/api/kpis/badges").header("Authorization", jetonM2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.compteurs.rapportsASigner").value(1)).andExpect(jsonPath("$.compteurs.evaluationsEnCours").value(0));
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.compteurs.demandesEvaluationEnAttente").value(0));
        String pdf = texteDuPdf(mvc.perform(get(base + "/rapport").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(pdf.replaceAll("\\s+", " ")).contains("RAPPORT D'ÉVALUATION DES OFFRES", "3. Examen préliminaire", "Rang 1 — offre n° 2",
                "Justification du prix", "Stock acquis l'an dernier.", "non qualifiée — Situation fiscale non régulière (IC 6.3)",
                "La commission propose d'attribuer le marché à BTP Alpha (offre n° 1), pour un montant de 12 500 000 Ariary hors taxes",
                "Évaluation conduite sans incident.", "signature attendue", "absence de conflit d'intérêts");
        byte[] docx = mvc.perform(get(base + "/rapport").param("format", "docx").header("Authorization", jetonM2)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(docx, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
        mvc.perform(post(base + "/rapport/signer").header("Authorization", tokenPrmp).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/rapport/signer").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"observation\":\"Réserve sur le délai d'exécution proposé.\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rapport.signatures[0].observation").value("Réserve sur le délai d'exécution proposé."));
        mvc.perform(post(base + "/rapport/signer").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_SIGNE"));
        mvc.perform(post(base + "/rapport/empechement").header("Authorization", jetonM2).contentType(JSON)
                .content("{\"im\":\"" + comptes.get(1) + "\",\"motif\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/rapport/empechement").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"im\":\"" + comptes.get(1) + "\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_ABSENT"));
        mvc.perform(post(base + "/rapport/empechement").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"im\":\"" + comptes.get(1) + "\",\"motif\":\"En mission\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("CLOSE")).andExpect(jsonPath("$.rapport.signe").value(true))
                .andExpect(jsonPath("$.rapport.signaturesAttendues.length()").value(0));
        assertThat(texteDuPdf(mvc.perform(get(base + "/rapport").header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsByteArray())
                .replaceAll("\\s+", " ")).contains("Observation : Réserve sur le délai d'exécution proposé.", "empêché de signer : En mission")
                .doesNotContain("signature attendue");
        assertThat(notificationRepository.findPourPrmp("PRMP001", null)).extracting(Notification::getTypeNotif).contains("RAPPORT_EVALUATION");
        mvc.perform(get("/api/kpis/badges").header("Authorization", jetonM2)).andExpect(jsonPath("$.compteurs.rapportsASigner").value(0));
    }

    @Test
    @DisplayName("Rabais structuré (07/10, Q4) : la séance le chiffre et le contrôle en alertes ; l'étape 3 propose le rabais inconditionnel "
            + "sur le prix corrigé, sa correction se motive ; le rabais conditionnel n'est pas appliqué lot par lot")
    void rabaisStructure() throws Exception {
        String a = offre("C900000051", "1111222333", "BTP Alpha", 1, "INTACTE", null, "12500000",
                "{\"nature\":\"POURCENTAGE\",\"valeur\":2,\"condition\":\"AUCUNE\",\"libelle\":\"Rabais de 2 %\"}");
        String b = offre("C900000052", "4444555666", "BTP Beta", 2, "INTACTE", null, "11900000",
                "{\"nature\":\"MONTANT\",\"valeur\":100000,\"condition\":\"LOTS\",\"lots\":[1,2]}");
        seanceClose();
        String lecture = mvc.perform(get("/api/fiches-marche/" + idDmc + "/seance/lecture").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(lecture, "$.offres[0].rabais.montant").longValue()).isEqualTo(250_000L);
        assertThat(JsonPath.<String>read(lecture, "$.offres[0].rabais.lecture")).isEqualTo("2 % du montant hors taxes, soit 250 000 Ariary");
        assertThat(JsonPath.<String>read(lecture, "$.offres[1].rabais.lecture"))
                .isEqualTo("100 000 Ariary hors taxes, si les lots 1, 2 sont attribués au candidat");
        assertThat(JsonPath.<Object>read(lecture, "$.offres[1].rabais.montant")).isNull();
        // La procédure n'a qu'un lot : le lot 2 est inconnu de la fiche — une alerte, jamais un refus.
        assertThat(JsonPath.<List<String>>read(lecture, "$.offres[1].alertes[*].type")).contains("RABAIS_LOTS");
        assertThat(JsonPath.<List<String>>read(lecture, "$.offres[0].alertes[*].type")).doesNotContain("RABAIS_LOTS", "RABAIS_INVALIDE");

        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isCreated());
        mvc.perform(post(base + "/declaration").header("Authorization", jetonM1).contentType(JSON).content("{\"conflit\":false}"))
                .andExpect(status().isOk());
        conformite(jetonM1, a, "{\"decision\":\"CONFORME\"}").andExpect(status().isOk());
        String ev = conformite(jetonM1, b, "{\"decision\":\"CONFORME\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(ev, "$.lots[0].offres[0].rabaisDeclare.nature")).isEqualTo("POURCENTAGE");
        assertThat(JsonPath.<String>read(ev, "$.lots[0].offres[1].rabaisDeclare.condition")).isEqualTo("LOTS");
        arreter("CONFORMITE").andExpect(status().isOk());
        String eligible = "\"preference\":{\"eligible\":true,\"motif\":\"Entreprise nationale\"}";
        // Inconditionnel : proposé sur le prix corrigé (Q3), retenu tel quel sans saisie.
        ev = montant(jetonM1, a, "{" + eligible + "}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(ev, "$.lots[0].offres[0].evaluation.rabais.propose").longValue()).isEqualTo(250_000L);
        assertThat(JsonPath.<Number>read(ev, "$.lots[0].offres[0].evaluation.rabais.montant").longValue()).isEqualTo(250_000L);
        assertThat(JsonPath.<Number>read(ev, "$.lots[0].offres[0].evaluation.montantEvalue").longValue()).isEqualTo(12_250_000L);
        assertThat(JsonPath.<String>read(ev, "$.lots[0].offres[0].evaluation.rabais.lecture")).isEqualTo("2 % du montant hors taxes, soit 250 000 Ariary");
        montant(jetonM1, a, "{" + eligible + ",\"rabais\":{\"montant\":200000}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        ev = montant(jetonM1, a, "{" + eligible + ",\"rabais\":{\"montant\":200000,\"motif\":\"Le rabais ne porte que sur les fournitures\"}}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(ev, "$.lots[0].offres[0].evaluation.rabais.motif")).isEqualTo("Le rabais ne porte que sur les fournitures");
        assertThat(JsonPath.<Number>read(ev, "$.lots[0].offres[0].evaluation.montantEvalue").longValue()).isEqualTo(12_300_000L);
        // Conditionnel : pas appliqué lot par lot.
        montant(jetonM1, b, "{" + eligible + ",\"rabais\":{\"montant\":100000}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RABAIS_CONDITIONNEL"));
        ev = montant(jetonM1, b, "{" + eligible + "}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(ev, "$.lots[0].offres[1].evaluation.rabais.montant").longValue()).isZero();
        assertThat(JsonPath.<Object>read(ev, "$.lots[0].offres[1].evaluation.rabais.propose")).isNull();
        assertThat(JsonPath.<List<Integer>>read(ev, "$.lots[0].offres[1].evaluation.rabais.lots")).containsExactly(1, 2);
    }

    // ------------------------------------------------------------------ outils

    private ResultActions arreter(String etape) throws Exception {
        return mvc.perform(post(base + "/lots/1/etapes/" + etape + "/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"));
    }

    private ResultActions anormale(String idOffre, String corps) throws Exception {
        return mvc.perform(put(base + "/offres/" + idOffre + "/anormale").header("Authorization", jetonM1).contentType(JSON).content(corps));
    }

    /** {@code corps} commence après « { » : les critères puis la décision. */
    private ResultActions qualifier(String idOffre, String corps) throws Exception {
        return mvc.perform(put(base + "/offres/" + idOffre + "/qualification").header("Authorization", jetonM1).contentType(JSON)
                .content("{" + corps));
    }

    /** Les critères, tous satisfaits sauf {@code echec} (non satisfait, sans motif), au début d'un corps de qualification. */
    private static String criteres(List<String> codes, String echec) {
        return "\"criteres\":[" + codes.stream().map(c -> "{\"code\":\"" + c + "\",\"decision\":\"" + (c.equals(echec) ? "NON_SATISFAIT" : "SATISFAIT")
                + "\"}").collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    private ResultActions montant(String jeton, String idOffre, String corps) throws Exception {
        return mvc.perform(put(base + "/offres/" + idOffre + "/montant").header("Authorization", jeton).contentType(JSON).content(corps));
    }

    /** Le contenu déchiffré d'une offre, comme la séance le range : une archive portant le manifeste. */
    private void clair(String idOffre, String manifeste) throws Exception {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(o)) {
            z.putNextEntry(new java.util.zip.ZipEntry("manifeste.json"));
            z.write(manifeste.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        stockage.ecrireClair(idOffre, o.toByteArray());
    }

    /** Les corrections proposées, toutes retenues. */
    private static String retenues(String proposees) {
        List<Map<String, Object>> l = JsonPath.read(proposees, "$");
        StringBuilder s = new StringBuilder("[");
        for (Map<String, Object> c : l) {
            s.append(s.length() == 1 ? "" : ",").append("{\"ligne\":").append(c.get("ligne")).append(",\"libelle\":\"")
                    .append(String.valueOf(c.get("libelle")).replace("\"", "\\\"")).append("\",\"avant\":").append(c.get("avant"))
                    .append(",\"apres\":").append(c.get("apres")).append(",\"regle\":\"").append(c.get("regle")).append("\",\"retenue\":true}");
        }
        return s.append("]").toString();
    }

    private ResultActions conformite(String jeton, String idOffre, String corps) throws Exception {
        return mvc.perform(put(base + "/offres/" + idOffre + "/conformite").header("Authorization", jeton).contentType(JSON).content(corps));
    }

    private static Object verif(String ev, String idOffre, String code, String champ) {
        List<Object> v = JsonPath.read(ev, "$.lots[0].offres[?(@.idOffre=='" + idOffre + "')].conformite.verifications[?(@.code=='" + code
                + "')]." + champ);
        return v.get(0);
    }

    /** Une offre ouverte, posée en base avec sa lecture : acte d'engagement, garantie (1 700 000 MGA, au-dessus du minimum). */
    private String offre(String idCandidat, String nif, String raison, int numero, String integrite, String groupement, String montantHt) {
        return offre(idCandidat, nif, raison, numero, integrite, groupement, montantHt, null);
    }

    /** ⚠️ Rabais structuré — {@code rabais} : le JSON de l'objet du manifeste format 4 (ou nul). */
    private String offre(String idCandidat, String nif, String raison, int numero, String integrite, String groupement, String montantHt,
            String rabais) {
        Offre o = new Offre();
        o.setIdOffre(UUID.randomUUID().toString());
        o.setIdDmc(idDmc);
        o.setIdCandidat(idCandidat);
        o.setIdEntreprise(entrepriseRepository.findByIdCandidat(idCandidat).orElseThrow().getIdEntreprise());
        o.setNif(nif);
        o.setRaisonSociale(raison);
        o.setEtat(Offre.DEPOSEE);
        o.setDateCreation(LocalDateTime.now().minusDays(2));
        o.setDateDepot(LocalDateTime.now().minusDays(2));
        o.setNumero(numero);
        o.setEnTete("{}");
        o.setNombreMorceaux(1);
        o.setTailleMorceau(1);
        o.setQuorum(2);
        o.setN(3);
        o.setEmpreintesDetenteurs("x");
        o.setGroupementNifs(groupement);
        o.setIntegrite(integrite);
        o.setMotifLecture("ALTEREE".equals(integrite) ? "empreinte du conteneur différente" : null);
        o.setOuverteLe(LocalDateTime.now().minusDays(1));
        o.setLecture("{\"acteEngagement\":{\"montantHt\":\"" + montantHt + "\",\"montantTtc\":\"" + montantHt + "0\",\"delai\":\"60 jours\""
                + (rabais == null ? "" : ",\"rabais\":" + rabais) + "},"
                + "\"garantie\":{\"codeVerification\":\"GAR-" + numero + "\",\"nomFichier\":\"garantie.pdf\",\"montant\":\"1700000\","
                + "\"monnaie\":\"MGA\",\"emetteur\":\"BNI Madagascar\"},\"pieces\":[]}");
        offreRepository.save(o);
        return o.getIdOffre();
    }

    private void seanceClose() {
        Seance s = new Seance();
        s.setIdDmc(idDmc);
        s.setEtat(Seance.CLOSE);
        s.setOuverteLe(LocalDateTime.now().minusDays(1));
        s.setCloseLe(LocalDateTime.now().minusHours(20));
        s.setPvSigneLe(LocalDateTime.now().minusHours(20));
        seanceRepository.save(s);
    }

    private void declarer(String jeton, String nif, String raison) throws Exception {
        mvc.perform(put("/api/candidat/entreprise").header("Authorization", jeton).contentType(JSON).content("{\"raisonSociale\":\"" + raison
                + "\",\"nif\":\"" + nif + "\",\"adresse\":\"Lot " + nif + "\",\"representant\":{\"nom\":\"Rakoto\",\"prenom\":\"Jean\"}}"))
                .andExpect(status().isOk());
    }

    /** Une fiche électronique validée (délai de réponse aux demandes : 5 jours), un responsable, une CAO de deux membres. */
    private void ficheEnLigne() throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        idDmc = ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"modeRemise\":\"ELECTRONIQUE\",\"garantieSoumission\":\"OUI\",\"alloti\":\"NON\","
                        + "\"variantes\":\"NON\",\"groupement\":\"NON\",\"provenance\":\"NATIONAL\",\"typePrix\":\"UNITAIRES\","
                        + "\"prixRevisable\":\"NON\",\"avance\":\"NON\",\"penalites\":\"CCAG\"}}"))
                .andExpect(status().isOk());
        // ⚠️ Tranche 1b — deux articles (quantités 10 et 5), pour les corrections proposées depuis le bordereau.
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/articles").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"articles\":[{\"designation\":\"Ordinateur portable\",\"unite\":\"U\",\"quantite\":10,\"caracteristiques\":"
                        + "[{\"libelle\":\"Mémoire\",\"exigence\":\"16 Go\"}]},{\"designation\":\"Onduleur\",\"unite\":\"U\",\"quantite\":5,"
                        + "\"caracteristiques\":[{\"libelle\":\"Puissance\",\"exigence\":\"1500 VA\"}]}]}"))
                .andExpect(status().isOk());
        Map<String, String> donnees = new LinkedHashMap<>();
        donnees.put("B02-OB-03", "AOO 0007/MESupReS/2026");
        donnees.put("B04-CD-02", "C1");
        donnees.put("B04-LR-03", ouvrable(aujourdhui.plusDays(60)).toString());
        donnees.put("B04-LR-04", "10:00");
        donnees.put("B04-SE-02", "https://depot.cnm.mg");
        donnees.put("B04-SE-03", aujourdhui.plusDays(10) + "T08:00");
        donnees.put("B04-SE-05", "Simple");
        donnees.put("B04-SE-06", "À définir par l'Administrateur (liste officielle des prestataires de certification)");
        donnees.put("B04-SE-10", "OUI");
        donnees.put("B04-SE-17", aujourdhui.plusDays(10) + "T08:00");
        donnees.put("B04-OP-13", "OUI");
        donnees.put("B05-GS-03", "1600000");
        donnees.put("B05-GS-04", "105");
        donnees.put("B04-VO-01", "75");
        donnees.put("B06-EP-01", "5");
        donnees.put("B03-CQ-08", "OUI");
        donnees.put("B06-EO-09", "10");
        donnees.put("B06-EO-02", "Délai de livraison : pénalité d'évaluation au-delà de quatre semaines");
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", donnees);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isCreated());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenPrmp).contentType(JSON)
                .content(CaoIntegrationTest.corpsCao("m1@eval.mg", "m2@eval.mg"))).andExpect(status().isOk());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"quorum\":2,\"dateCeremonie\":\"" + aujourdhui.plusDays(9) + "T09:00\",\"depositaire\":{\"nom\":\"Rakoto Jean\",\"email\":\"rakoto.depositaire@secours.mg\"}}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"));
        String internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        comptes.clear();
        comptes.addAll(JsonPath.read(internes, "$.membresCommission[*].im"));
        jetonM1 = bearer("m1@eval.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, comptes.get(0), null);
        jetonM2 = bearer("m2@eval.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, comptes.get(1), null);
    }
}
