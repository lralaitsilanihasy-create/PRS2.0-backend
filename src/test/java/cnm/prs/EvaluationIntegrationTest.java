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
        mvc.perform(post(base + "/lots/1/etapes/EVALUATION/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_NON_DISPONIBLE"));
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

    // ------------------------------------------------------------------ outils

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
        o.setLecture("{\"acteEngagement\":{\"montantHt\":\"" + montantHt + "\",\"montantTtc\":\"" + montantHt + "0\",\"delai\":\"60 jours\"},"
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
        besoinDeTest(idDmc);
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
