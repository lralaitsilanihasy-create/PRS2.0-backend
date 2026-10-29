package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
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
    @Autowired private cnm.prs.repository.PieceJointeDossierRepository pieceJointeDossierRepository;
    @Autowired private cnm.prs.seed.PointsCtrlDossierMarcheSeeder pointsCtrlSeeder;
    @Autowired private cnm.prs.repository.AttributionRepository attributionRepository;
    @Autowired private cnm.prs.repository.EvaluationRapportRepository rapportRepository;
    @Autowired private cnm.prs.service.TentativesRefusees tentativesRefusees;
    @Autowired private cnm.prs.repository.PieceSousTypeRepository pieceSousTypeRepository;
    @Autowired private cnm.prs.repository.DocumentFicheMarcheRepository documentFicheMarcheRepository;
    @Autowired private cnm.prs.service.AttributionService attributionService;

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
        // ⚠️ 2026-10-15 (« modeles-guide-cao », §B3.1, §B3.2) — la cote du guide : C, SC, NC, SO ; elle fixe la vérification.
        conformite(jetonM1, a, "{\"decision\":\"CONFORME\",\"verifications\":[{\"code\":\"AUTRES_CONDITIONS\",\"cote\":\"X\"}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COTE_INVALIDE"));
        conformite(jetonM1, a, "{\"decision\":\"CONFORME\",\"verifications\":[{\"code\":\"AUTRES_CONDITIONS\",\"cote\":\"NC\",\"satisfaite\":true}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COTE_INCOHERENTE"));
        conformite(jetonM1, a, "{\"decision\":\"CONFORME\",\"specifications\":[{\"idArticle\":1,\"idCaracteristique\":1,\"cote\":\"C\"}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SPECIFICATION_INCONNUE"));
        mvc.perform(get(base + "/offres/" + a + "/specifications").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));   // offre déposée par pièces : pas de tableau en ligne
        mvc.perform(get(base + "/offres/" + a + "/specifications").header("Authorization", tokenUgpm)).andExpect(status().isForbidden());
        String cotee = conformite(jetonM1, a, "{\"decision\":\"CONFORME\",\"verifications\":[{\"code\":\"OFFRE_UNIQUE\",\"satisfaite\":true,"
                + "\"observation\":\"Le groupement de BTP Gamma ne compte pas BTP Alpha : NIF saisi par erreur\"},{\"code\":\"AUTRES_CONDITIONS\","
                + "\"cote\":\"sc\",\"observation\":\"Calendrier joint incomplet, sans incidence\"}]}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(verif(cotee, a, "AUTRES_CONDITIONS", "cote")).isEqualTo("SC");
        assertThat(verif(cotee, a, "AUTRES_CONDITIONS", "satisfaite")).isEqualTo(true);
        assertThat(verif(cotee, a, "AUTRES_CONDITIONS", "coteProposee")).isNull();
        assertThat(verif(cotee, a, "OFFRE_UNIQUE", "cote")).isEqualTo("C");
        assertThat(verif(cotee, a, "FRAIS_DOSSIER", "cote")).isEqualTo("SO");
        assertThat(verif(cotee, a, "AE_DATE_SIGNE", "cote")).isNull();
        assertThat(verif(cotee, b, "INTEGRITE", "cote")).isEqualTo("NC");

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
        // Trié par identifiant : findAll() ne garantit aucun ordre (une décision remplacée est réécrite, et Postgres la déplace).
        assertThat(decisionRepository.findAll(org.springframework.data.domain.Sort.by("id"))).filteredOn(d -> d.getIdOffre().equals(c))
                .extracting(EvaluationDecision::getDecision)
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
        // ⚠️ 2026-10-29 (« notifications-espaces-externes », §B4) — l'étape suivante attend les décisions : chaque membre le sait.
        assertThat(notificationRepository.findAll()).filteredOn(n -> "EVALUATION_ETAPE".equals(n.getTypeNotif()))
                .hasSize(2).allMatch(n -> "MEMBRE_CAO".equals(n.getDestinataireType()) && n.getCorps().contains("lot 1")
                        && n.getCorps().contains("« évaluation et comparaison des offres » attend vos décisions"));
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
        // ⚠️ R9b (§B4 n° 9, QB2) — une correction calculée reprend une proposition ; REPORT / AUTRE se motivent et sont signalées.
        montant(jetonM1, a, "{\"corrections\":[{\"ligne\":1,\"libelle\":\"x\",\"avant\":10000000,\"apres\":10500000,\"regle\":\"LETTRES_PREVALENT\","
                + "\"retenue\":true}]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CORRECTION_NON_PROPOSEE"))
                .andExpect(jsonPath("$.details.regle").value("LETTRES_PREVALENT"));
        montant(jetonM1, b, "{\"corrections\":[{\"libelle\":\"Total\",\"avant\":1,\"apres\":2,\"regle\":\"pu_prevaut\",\"retenue\":true}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CORRECTION_NON_PROPOSEE"));   // sans bordereau
        montant(jetonM1, b, "{\"corrections\":[{\"libelle\":\"Total\",\"avant\":1,\"apres\":2,\"regle\":\"AUTRE\",\"retenue\":false}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        String horsCalcul = montant(jetonM1, b, "{\"corrections\":[{\"libelle\":\"Report du sous-total\",\"avant\":1,\"apres\":2,\"regle\":\"REPORT\","
                + "\"retenue\":false,\"motif\":\"Sous-total mal reporté page 3\"}]}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(horsCalcul, "$.lots[0].offres[1].evaluation.corrections[*].ecart")).containsExactly(true);
        assertThat(JsonPath.<List<String>>read(horsCalcul, "$.lots[0].offres[1].evaluation.corrections[*].motif"))
                .containsExactly("Sous-total mal reporté page 3");
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
        // ⚠️ V116 (Q11, §B4) — le détail estimatif du lot : lu par un membre de la commission, jamais par un candidat.
        String est = mvc.perform(get(base + "/lots/1/estimation").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(est, "$.totalEstime").longValue()).isEqualTo(12_000_000L);
        assertThat(JsonPath.<Number>read(est, "$.montantPlan").longValue()).isEqualTo(12_000_000L);
        assertThat(JsonPath.<List<String>>read(est, "$.articles[*].designation")).containsExactly("Ordinateur portable", "Onduleur");
        assertThat(JsonPath.<List<Number>>read(est, "$.articles[*].prixUnitaireEstime")).extracting(Number::longValue)
                .containsExactly(1_000_000L, 400_000L);
        assertThat(JsonPath.<List<Number>>read(est, "$.articles[*].montant")).extracting(Number::longValue)
                .containsExactly(10_000_000L, 2_000_000L);
        mvc.perform(get(base + "/lots/1/estimation").header("Authorization", jetonA)).andExpect(status().isForbidden());
        mvc.perform(get(base + "/lots/9/estimation").header("Authorization", jetonM1)).andExpect(status().isNotFound());
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
        // ⚠️ R9b (§B4 n° 10, QB3) — l'échéance passée sans réponse vaut réponse tracée : le rejet passe, le défaut est écrit ; puis
        // l'échéance est rétablie pour la suite (le candidat répond, un nouveau rejet remplace celui-ci).
        EvaluationDemande justif = demandeRepository.findByIdOffreAndTypeOrderByDemandeeLeAscIdAsc(c, "JUSTIFICATION").get(0);
        LocalDateTime echeanceJustif = justif.getEcheance();
        justif.setEcheance(LocalDateTime.now().minusHours(1));
        demandeRepository.saveAndFlush(justif);
        anormale(c, "{\"suspectee\":true,\"decision\":\"REJETEE\",\"motif\":\"Pas de justification\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].offres[2].anormale.defautReponse").value(true));
        assertThat(journalRepository.findByIdDmcOrderByDateAscIdAsc(idDmc)).filteredOn(x -> "ANORMALE".equals(x.getAction()))
                .extracting(x -> x.getDetail()).anyMatch(t -> t.contains("défaut de réponse du candidat"));
        justif.setEcheance(echeanceJustif);
        demandeRepository.saveAndFlush(justif);
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
        // ⚠️ R1 (manuel révisé, §B2) — le quorum (surcharge « 1 ») et la qualité des signataires attendus.
        assertThat(JsonPath.<List<String>>read(r, "$.rapport.signaturesAttendues[*].qualite")).containsExactly("PRESIDENT", "MEMBRE");
        assertThat(JsonPath.<String>read(r, "$.rapport.quorum.regle")).isEqualTo("1");
        assertThat(JsonPath.<Boolean>read(r, "$.rapport.quorum.atteint")).isFalse();
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
                "Évaluation conduite sans incident.", "signature attendue", "absence de conflit d'intérêts",
                // ⚠️ 2026-10-15 (« modeles-guide-cao », §B3) — grilles cotées, comparaison par ajustement, qualification complète.
                "Grille de conformité — Offre n° 1 (BTP Alpha)", "Valeur relevée", "Acte d'engagement daté et signé", "Marge de préférence",
                "Critères additionnels", "Conversion en monnaie unique : sans objet", "Qualification — Offre n° 1 (BTP Alpha)", "Exigence du DAO");
        byte[] docx = mvc.perform(get(base + "/rapport").param("format", "docx").header("Authorization", jetonM2)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(docx, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
        mvc.perform(post(base + "/rapport/signer").header("Authorization", tokenPrmp).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        // ⚠️ R1 (§B2, arbitrage : pas d'impasse) — l'empêchement du président lui-même, sans représentant désigné, rendrait le quorum
        // inatteignable : refusé.
        mvc.perform(post(base + "/rapport/empechement").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"im\":\"" + comptes.get(0) + "\",\"motif\":\"Absent\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUORUM_NON_ATTEINT")).andExpect(jsonPath("$.details.presidentSigne").value(false));
        mvc.perform(post(base + "/rapport/signer").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"observation\":\"Réserve sur le délai d'exécution proposé.\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rapport.signatures[0].observation").value("Réserve sur le délai d'exécution proposé."))
                .andExpect(jsonPath("$.rapport.quorum.atteint").value(true)).andExpect(jsonPath("$.rapport.quorum.presidentSigne").value(true));
        mvc.perform(post(base + "/rapport/signer").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_SIGNE"));
        // ⚠️ V120 (« modeles-guide-cao », §B5) — avant la dernière signature, le président retire le rapport : les signatures tombent, le
        // rapport retiré reste lisible, l'évaluation reprend et le rapport suivant cite le retrait.
        mvc.perform(post(base + "/rapport/retirer").header("Authorization", jetonM2).contentType(JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/rapport/retirer").header("Authorization", jetonM1).contentType(JSON).content("{\"motif\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        String retrait = mvc.perform(post(base + "/rapport/retirer").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"motif\":\"Demander des éclaircissements sur le délai d'exécution\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("EN_COURS")).andExpect(jsonPath("$.rapport").doesNotExist())
                .andExpect(jsonPath("$.rapportsRetires.length()").value(1))
                .andExpect(jsonPath("$.rapportsRetires[0].motif").value("Demander des éclaircissements sur le délai d'exécution"))
                .andExpect(jsonPath("$.rapportsRetires[0].retirePar").value(comptes.get(0)))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(retrait, "$.rapportsRetires[0].signatures")).contains(" le ");
        Number idRetire = JsonPath.read(retrait, "$.rapportsRetires[0].id");
        mvc.perform(get(base + "/rapport").header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        assertThat(texteDuPdf(mvc.perform(get(base + "/rapport/retires/" + idRetire).header("Authorization", jetonM2)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray())).contains("RAPPORT D'ÉVALUATION DES OFFRES");
        mvc.perform(get(base + "/rapport/retires/999999").header("Authorization", jetonM2)).andExpect(status().isNotFound());
        assertThat(notificationRepository.findPourRefEtType(comptes.get(1), "MEMBRE_CAO")).extracting(Notification::getTypeNotif)
                .contains("RAPPORT_RETIRE");
        assertThat(journalRepository.findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction()).contains("RAPPORT_RETIRE");
        mvc.perform(post(base + "/lots/1/etapes/QUALIFICATION/rouvrir").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"motif\":\"Éclaircissements reçus\"}")).andExpect(status().isOk());
        arreter("QUALIFICATION").andExpect(status().isOk());
        mvc.perform(post(base + "/rapport").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"observations\":\"Évaluation conduite sans incident.\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rapport.signatures.length()").value(0));
        mvc.perform(post(base + "/rapport/signer").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"observation\":\"Réserve sur le délai d'exécution proposé.\"}")).andExpect(status().isOk());
        assertThat(texteDuPdf(mvc.perform(get(base + "/rapport").header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsByteArray())
                .replaceAll("\\s+", " ")).contains("a été retiré le", "la commission refusant de le valider. Motif : Demander des éclaircissements "
                        + "sur le délai d'exécution.");
        mvc.perform(post(base + "/rapport/empechement").header("Authorization", jetonM2).contentType(JSON)
                .content("{\"im\":\"" + comptes.get(1) + "\",\"motif\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/rapport/empechement").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"im\":\"" + comptes.get(1) + "\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_ABSENT"));
        mvc.perform(post(base + "/rapport/empechement").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"im\":\"" + comptes.get(1) + "\",\"motif\":\"En mission\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("CLOSE")).andExpect(jsonPath("$.rapport.signe").value(true))
                .andExpect(jsonPath("$.rapport.signaturesAttendues.length()").value(0));
        mvc.perform(post(base + "/rapport/retirer").header("Authorization", jetonM1).contentType(JSON).content("{\"motif\":\"Trop tard\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RAPPORT_SIGNE"));   // H3 : signé, l'évaluation est close
        assertThat(texteDuPdf(mvc.perform(get(base + "/rapport").header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsByteArray())
                .replaceAll("\\s+", " ")).contains("Observation : Réserve sur le délai d'exécution proposé.", "empêché de signer : En mission",
                        // ⚠️ R1 (§B1, Q4 du pilote) — le rapport signé vaut PV de validation : la section « Séance de validation ».
                        "10. Séance de validation", "Rapport validé par la commission le", "Membres ayant validé :", "(Président), le",
                        "Empêchements constatés :", "En mission", "Quorum de la commission : 1 membre(s) au moins — 1 requis, 1 obtenu(s) : atteint ; président compté",
                        "Observations de désaccord portées à la signature :")
                .doesNotContain("signature attendue");
        assertThat(notificationRepository.findPourPrmp("PRMP001", null)).extracting(Notification::getTypeNotif).contains("RAPPORT_EVALUATION");
        mvc.perform(get("/api/kpis/badges").header("Authorization", jetonM2)).andExpect(jsonPath("$.compteurs.rapportsASigner").value(0));

        // ⚠️ Lot 2, tranche 2a (§B1-§B2) — le dossier de marché du lot, au contrôle de la Commission.
        typeDossierRepository.save(new cnm.prs.entity.TypeDossier("DDM", "Dossier de Marché"));
        sousTypeDossierRepository.save(new cnm.prs.entity.SousTypeDossier("MAOO", "Marché sur Appel d'Offres Ouvert", "DDM"));
        for (String[] t : List.of(new String[] { "Projet de marché signé", "PROJET_MARCHE" }, new String[] { "Cahier des charges", "CAHIER_CHARGES" },
                new String[] { "Devis estimatif détaillé", "DEVIS_ESTIMATIF" }, new String[] { "Procès-verbal d'ouverture des offres", "PV_OUVERTURE" },
                new String[] { "Rapport d'analyse des offres", "RAPPORT_ANALYSE" },
                new String[] { "Questions et réponses des candidats", "QUESTIONS_REPONSES" })) {   // ⚠️ R7 (V109) : la famille DDM n'est pas au référentiel de test
            cnm.prs.entity.TypePieceJointe tp = typePieceJointeRepository.findById(seedTypePiece(t[0], true, "DDM", 1)).orElseThrow();
            tp.setCode(t[1]);
            typePieceJointeRepository.save(tp);
        }
        String att = base.replace("/evaluation", "/attribution");
        mvc.perform(get(att).header("Authorization", jetonM2)).andExpect(status().isOk()).andExpect(jsonPath("$.lots[0].etat").value("PROPOSE"))
                .andExpect(jsonPath("$.lots[0].proposition.idOffre").value(a)).andExpect(jsonPath("$.lots[0].dossierMarche").isEmpty());
        mvc.perform(post(att + "/lots/1/dossier").header("Authorization", jetonM1)).andExpect(status().isForbidden());
        mvc.perform(post(att + "/lots/9/dossier").header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        // ⚠️ R7 (V109) — une question répondue (posée en base : la fenêtre J−10 est passée) est jointe d'office au dossier de marché.
        cnm.prs.entity.QuestionCandidat question = new cnm.prs.entity.QuestionCandidat();
        question.setIdDmc(idDmc);
        question.setIdCandidat("C900000051");
        question.setQuestion("Les échantillons sont-ils exigés à la remise des offres ?");
        question.setPoseeLe(LocalDateTime.now().minusDays(40));
        question.setReponse("Non : ils sont exigés de l'attributaire avant la notification.");
        question.setRepondueLe(LocalDateTime.now().minusDays(39));
        question.setPublieeLe(LocalDateTime.now().minusDays(39));
        questionRepository.save(question);
        String cree = mvc.perform(post(att + "/lots/1/dossier").header("Authorization", tokenPrmp)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lots[0].etat").value("AU_CONTROLE")).andExpect(jsonPath("$.lots[0].dossierMarche.sousType").value("MAOO"))
                .andExpect(jsonPath("$.lots[0].dossierMarche.statut").value("BROUILLON")).andExpect(jsonPath("$.lots[0].projetDisponible").value(true))
                .andReturn().getResponse().getContentAsString();
        int idDossier = JsonPath.read(cree, "$.lots[0].dossierMarche.idDossier");
        // ⚠️ 2026-10-27 (« dossier-marche-remise-papier », P4) — un dossier né de l'attribution n'est pas papier.
        mvc.perform(get("/api/dossiers/" + idDossier).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.procedure.idDmc").value(idDmc.intValue())).andExpect(jsonPath("$.procedure.remisePapier").value(false));
        mvc.perform(post(att + "/lots/1/dossier").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOSSIER_EXISTANT")).andExpect(jsonPath("$.idDossier").value(idDossier));
        assertThat(dossierRepository.findById(idDossier).orElseThrow().getIdTypeDossier()).isEqualTo("DDM");
        assertThat(dossierRepository.findById(idDossier).orElseThrow().getIdDmc()).isNull();   // le lien du DAO reste au dossier DAO
        // ⚠️ 2026-10-20 (« dossier-marche-donnees », §B1) — la Commission lit la procédure du dossier de marché qu'elle contrôle, dès
        // la soumission (Q1 : le brouillon est hors de son périmètre), journal compris (Q3), en lecture seule.
        cnm.prs.entity.Dossier ddm = dossierRepository.findById(idDossier).orElseThrow();
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", ddm.getIdLocalite());
        String sea = base.replace("/evaluation", "/seance");
        List<String> lectures = List.of(sea, base, base + "/lots/1/tableau", base + "/journal", att);
        for (String route : lectures) {
            mvc.perform(get(route).header("Authorization", tokenSec)).andExpect(status().isForbidden());
        }
        ddm.setStatut("SOUMIS");
        dossierRepository.save(ddm);
        for (String route : lectures) {
            mvc.perform(get(route).header("Authorization", tokenSec)).andExpect(status().isOk());
        }
        mvc.perform(get(sea + "/lecture").header("Authorization", tokenSec))
                .andExpect(res -> assertThat(res.getResponse().getStatus()).isNotEqualTo(403));
        // ⚠️ 2026-10-27 (« lectures-cnm-dossier-marche ») — L1 : les tentatives refusées hors délai, au PV de la séance ; L2 : le tableau
        // de conformité d'une offre, une fois le rapport signé ; Q2 : le STAT servi avec le NIF.
        tentativesRefusees.creation(idDmc, "C900000051", null, 1, null, LocalDateTime.now().minusDays(1), LocalDateTime.now().minusDays(2));
        mvc.perform(get(sea).header("Authorization", tokenSec)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pv.tentativesRefusees[0].geste").value("CREATION"))
                .andExpect(jsonPath("$.pv.tentativesRefusees[0].motif").value("DELAI_DEPASSE"));
        mvc.perform(get(base.replace("/evaluation", "/depots")).header("Authorization", tokenSec)).andExpect(status().isForbidden());
        String specs = base + "/offres/" + a + "/specifications";
        mvc.perform(get(specs).header("Authorization", tokenSec)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        cnm.prs.entity.EvaluationRapport rapport = rapportRepository.findById(idDmc).orElseThrow();
        LocalDateTime signeLe = rapport.getSigneLe();
        rapport.setSigneLe(null);
        rapportRepository.save(rapport);
        mvc.perform(get(specs).header("Authorization", tokenSec)).andExpect(status().isForbidden());
        rapport.setSigneLe(signeLe);
        rapportRepository.save(rapport);
        cnm.prs.entity.Entreprise alpha = entrepriseRepository.findById(offreRepository.findById(a).orElseThrow().getIdEntreprise()).orElseThrow();
        alpha.setStat("41101112026000123");
        entrepriseRepository.save(alpha);
        mvc.perform(get(sea + "/lecture").header("Authorization", tokenSec)).andExpect(status().isOk())
                .andExpect(jsonPath("$.offres[?(@.idOffre=='" + a + "')].entreprise.stat").value(hasItem("41101112026000123")));
        mvc.perform(get(sea + "/offres/" + a + "/pieces/offre.pdf").header("Authorization", tokenSec)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PIECE_RESERVEE_CAO"));
        mvc.perform(post(att + "/lots/1/attribuer").header("Authorization", tokenSec).contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(base).header("Authorization", bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "XXX")))
                .andExpect(status().isForbidden());
        ddm.setStatut("BROUILLON");
        dossierRepository.save(ddm);
        // ⚠️ 2026-10-19 (« dossier-marche-lecture », §B1) — avant l'attribution, le montant de l'offre proposée par le rapport ;
        // §B2 : le dossier de marché sur la ligne des éligibles.
        mvc.perform(get("/api/dossiers/" + idDossier + "/actes-gestion").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.montantInitialHt").value(12500000)).andExpect(jsonPath("$.sourceMontantInitial").value("PROPOSITION"));
        mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].dossiersMarche[0].lot").value(hasItem(1)))
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].dossiersMarche[0].idDossier").value(hasItem(idDossier)))
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].dossiersMarche[0].statut").value(hasItem("BROUILLON")))
                // ⚠️ 2026-10-20 (« liste-dossier-marche ») — §B1 le numéro de la procédure ; §B2 l'attribution par lot, règle de l'écran.
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].numeroProcedure").value(hasItem(notNullValue())))
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].attributions[0].etat").value(hasItem("AU_CONTROLE")))
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].attributions[0].rapportSigneLe").value(hasItem(notNullValue())))
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].attributions[0].propose.candidat").value(hasItem("BTP Alpha")))
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].attributions[0].propose.montant").value(hasItem(12500000)))
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].attributions[0].propose.nbTitulaires").value(hasItem(1)));
        List<String> codesPieces = pieceJointeDossierRepository.findAll().stream().filter(x -> x.getIdDossier() == idDossier)
                .map(x -> typePieceJointeRepository.findById(x.getIdTypePiece()).orElseThrow().getCode()).toList();
        assertThat(codesPieces).contains("PROJET_MARCHE", "CAHIER_CHARGES", "RAPPORT_ANALYSE", "QUESTIONS_REPONSES");
        assertThat(texteDuPdf(pieceJointeDossierRepository.findAll().stream().filter(x -> x.getIdDossier() == idDossier
                && "QUESTIONS_REPONSES".equals(typePieceJointeRepository.findById(x.getIdTypePiece()).orElseThrow().getCode()))
                .findFirst().orElseThrow().getContenu())).contains("QUESTIONS DES CANDIDATS ET RÉPONSES", "échantillons", "avant la notification");
        String projet = texteDuPdf(mvc.perform(get(att + "/lots/1/projet").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(projet).contains("PROJET DE MARCHÉ", "BTP Alpha", "NIF 1111222333", "12 500 000 Ariary hors taxes",
                "douze millions cinq cent mille ariary", "Article 3 — Pièces constitutives");
        // ⚠️ R10 (§B5, H2) — offre déposée en ligne : l'original signé de l'acte d'engagement est annoncé à remettre.
        assertThat(projet).contains("original signé à remettre avant la signature du marché");
        // ⚠️ 2026-10-10 (demande « projet-de-marche ») — les mentions obligatoires de l'art. 60 de la loi n° 2016-055.
        assertThat(projet).contains("Article 2 — Base légale", "et 60 de la loi n° 2016-055", "nommée par",
                "Article 5 — Délai d'exécution", "pénalités de retard", "Article 6 — Réception", "réception partielle",
                "Article 7 — Règlement", "Comptable public assignataire chargé du paiement :", "Imputation budgétaire :",
                "Domiciliation bancaire des paiements :", "Article 8 — Résiliation", "article 76", "Date de notification du marché : ……")
                .doesNotContain("Droit applicable");
        // Le geste de regénération : PRMP ou UGPM ; la pièce du dossier de marché (brouillon) est remplacée.
        mvc.perform(post(att + "/lots/1/projet").header("Authorization", jetonM1)).andExpect(status().isForbidden());
        mvc.perform(post(att + "/lots/1/projet").header("Authorization", tokenUgpm)).andExpect(status().isOk());
        byte[] refait = attributionRepository.findById(new cnm.prs.entity.Attribution.Cle(idDmc, 1)).orElseThrow().getProjetPdf();
        assertThat(pieceJointeDossierRepository.findAll().stream().filter(x -> x.getIdDossier() == idDossier
                && "PROJET_MARCHE".equals(typePieceJointeRepository.findById(x.getIdTypePiece()).orElseThrow().getCode())))
                .singleElement().satisfies(x -> assertThat(x.getContenu()).isEqualTo(refait));
        assertThat(new String(mvc.perform(get(att + "/lots/1/projet").param("format", "docx").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsByteArray(), 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
        // L'avis de la Commission (PV signé du dossier de marché) remonte au lot.
        receptionRepository.save(reception(9950, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(9950, 9950, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9950, 9950, "CTRMEM"));
        seedPvSigne(9950, 9950);
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].etat").value("AVIS_RENDU"))
                .andExpect(jsonPath("$.lots[0].dossierMarche.avis").value("FAV"));

        // ⚠️ Tranche 2b (§B3) — l'attribution, à l'offre proposée par la CAO seule (Q4).
        mvc.perform(post(att + "/lots/1/informer").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateAffichage\":\"" + aujourdhui + "\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NON_ATTRIBUE"));
        mvc.perform(post(att + "/lots/1/attribuer").header("Authorization", tokenUgpm).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(att + "/lots/1/attribuer").header("Authorization", tokenPrmp).contentType(JSON).content("{\"idOffre\":\"" + b + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFRE_NON_PROPOSEE"));
        mvc.perform(post(att + "/lots/1/attribuer").header("Authorization", tokenPrmp).contentType(JSON).content("{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].etat").value("ATTRIBUE")).andExpect(jsonPath("$.lots[0].attributaire.idOffre").value(a))
                .andExpect(jsonPath("$.lots[0].attributaire.candidat").value("BTP Alpha"))
                .andExpect(jsonPath("$.lots[0].attributaire.montant").value(12500000));
        mvc.perform(post(att + "/lots/1/attribuer").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_ATTRIBUE"));
        mvc.perform(get("/api/dossiers/" + idDossier + "/actes-gestion").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.montantInitialHt").value(12500000)).andExpect(jsonPath("$.sourceMontantInitial").value("ATTRIBUTION"));
        mvc.perform(get("/api/candidat/offres/" + b + "/resultat").header("Authorization", jetonB)).andExpect(status().isNotFound());

        // §B4.1 — l'information : une lettre par candidat, signée de la PRMP ; le délai court de la plus tardive des deux dates (art. 78).
        mvc.perform(post(att + "/lots/1/informer").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DATE_AFFICHAGE_OBLIGATOIRE"));
        mvc.perform(post(att + "/lots/1/informer").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateAffichage\":\"" + aujourdhui.plusDays(1) + "\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATE_AFFICHAGE_INVALIDE"));
        mvc.perform(post(att + "/lots/1/informer").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateAffichage\":\"" + aujourdhui + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].etat").value("INFORME")).andExpect(jsonPath("$.lots[0].information.lettres.length()").value(3))
                // ⚠️ R6c (règle 6, QB1 ; V108) — réception présumée : l'information + 3 jours (paramètre RECEPTION_LETTRE semé).
                .andExpect(jsonPath("$.lots[0].delaiAttente.receptionLe").value(aujourdhui.plusDays(3).toString()))
                .andExpect(jsonPath("$.lots[0].delaiAttente.joursReception").value(3))
                .andExpect(jsonPath("$.lots[0].delaiAttente.debut").value(aujourdhui.plusDays(3).toString()))
                .andExpect(jsonPath("$.lots[0].delaiAttente.fin").value(aujourdhui.plusDays(13).toString()))
                .andExpect(jsonPath("$.lots[0].delaiAttente.signableLe").value(aujourdhui.plusDays(14).toString()))
                .andExpect(jsonPath("$.lots[0].delaiAttente.ecoule").value(false));
        mvc.perform(post(att + "/lots/1/informer").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateAffichage\":\"" + aujourdhui + "\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_INFORME"));
        assertThat(notificationRepository.findAll()).filteredOn(n -> "C900000052".equals(n.getDestinataireRef()))
                .extracting(Notification::getTypeNotif).contains("RESULTAT_DISPONIBLE");
        assertThat(notificationRepository.findAll()).filteredOn(n -> "C900000051".equals(n.getDestinataireRef()))
                .extracting(Notification::getTypeNotif).contains("ATTRIBUTION");
        mvc.perform(get("/api/candidat/offres/" + b + "/resultat").header("Authorization", jetonA)).andExpect(status().isForbidden());
        String resB = mvc.perform(get("/api/candidat/offres/" + b + "/resultat").header("Authorization", jetonB)).andExpect(status().isOk())
                .andExpect(jsonPath("$.retenu").value(false)).andExpect(jsonPath("$.attributaire").value("BTP Alpha"))
                .andExpect(jsonPath("$.signableLe").value(aujourdhui.plusDays(14).toString())).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(resB, "$.motifRejet")).contains("Situation fiscale non régulière");
        String lettreB = texteDuPdf(mvc.perform(get("/api/candidat/offres/" + b + "/resultat/lettre").header("Authorization", jetonB))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(lettreB).contains("LETTRE D'INFORMATION DU REJET DE L'OFFRE", "BTP Alpha", "12 500 000 Ariary hors taxes",
                "Situation fiscale non régulière", "signé électroniquement sur la plateforme le", "art. 52-II", "modèle provisoire");
        String lettreA = texteDuPdf(mvc.perform(get(att + "/lots/1/lettres/" + a).header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(lettreA).contains("LETTRE D'ATTRIBUTION", "votre offre est retenue");
        mvc.perform(get(att).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.lots[0].information.lettres[?(@.idOffre == '" + b + "')].lueLe").isNotEmpty())
                .andExpect(jsonPath("$.lots[0].information.lettres[?(@.idOffre == '" + c + "')].lueLe").value(org.hamcrest.Matchers.contains(
                        org.hamcrest.Matchers.nullValue())));
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/resultats")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attributaire").value("BTP Alpha")).andExpect(jsonPath("$[0].montant").value(12500000));

        // §B4.2 — les explications : le candidat non retenu demande, la PRMP répond par écrit.
        mvc.perform(post("/api/candidat/offres/" + a + "/explication").header("Authorization", jetonA).contentType(JSON)
                .content("{\"question\":\"?\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFRE_RETENUE"));
        mvc.perform(post("/api/candidat/offres/" + b + "/explication").header("Authorization", jetonB).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUESTION_OBLIGATOIRE"));
        String ex = mvc.perform(post("/api/candidat/offres/" + b + "/explication").header("Authorization", jetonB).contentType(JSON)
                .content("{\"question\":\"Quelle pièce fiscale manquait ?\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.etat").value("EN_ATTENTE")).andReturn().getResponse().getContentAsString();
        long idEx = JsonPath.<Number>read(ex, "$.id").longValue();
        assertThat(notificationRepository.findPourPrmp("PRMP001", null)).extracting(Notification::getTypeNotif).contains("EXPLICATION_DEMANDEE");
        mvc.perform(multipart(att + "/explications/" + idEx + "/reponse").param("texte", "x").header("Authorization", tokenUgpm))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(att + "/explications/" + idEx + "/reponse").file(new MockMultipartFile("fichier", "attestation.pdf",
                "application/pdf", "%PDF-1.4 x".getBytes(StandardCharsets.ISO_8859_1))).param("texte", "L'attestation fiscale datait de plus de six mois.")
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("REPONDUE"));
        mvc.perform(multipart(att + "/explications/" + idEx + "/reponse").param("texte", "x").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_REPONDU"));
        mvc.perform(get("/api/candidat/offres/" + b + "/explications").header("Authorization", jetonB))
                .andExpect(jsonPath("$[0].reponse").value("L'attestation fiscale datait de plus de six mois."));
        mvc.perform(get("/api/candidat/offres/" + b + "/explications/" + idEx + "/fichier").header("Authorization", jetonB)).andExpect(status().isOk());
        mvc.perform(get(att).header("Authorization", jetonM2)).andExpect(jsonPath("$.lots[0].explications[0].etat").value("REPONDUE"));

        // Le délai écoulé : le lot devient signable le lendemain du dixième jour franc.
        cnm.prs.entity.Attribution attr = attributionRepository.findById(new cnm.prs.entity.Attribution.Cle(idDmc, 1)).orElseThrow();
        attr.setInformeLe(attr.getInformeLe().minusDays(14));   // ⚠️ R6c : + 3 jours de réception présumée → début à J−11
        attr.setDateAffichage(attr.getDateAffichage().minusDays(15));
        attributionRepository.save(attr);
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].etat").value("SIGNABLE"))
                .andExpect(jsonPath("$.lots[0].delaiAttente.debut").value(aujourdhui.minusDays(11).toString()))
                .andExpect(jsonPath("$.lots[0].delaiAttente.ecoule").value(true));
        // ⚠️ 2d-1 (§B7) — le compteur de la PRMP et l'alerte du délai écoulé, émise une seule fois.
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.compteurs.lotsSignables").value(1)).andExpect(jsonPath("$.compteurs.lotsAAttribuer").value(0));
        attributionService.alerter();
        assertThat(notificationRepository.findAll()).filteredOn(n -> "DELAI_ATTENTE_ECOULE".equals(n.getTypeNotif())).hasSize(1);
        attributionService.alerter();
        assertThat(notificationRepository.findAll()).filteredOn(n -> "DELAI_ATTENTE_ECOULE".equals(n.getTypeNotif())).hasSize(1);
        assertThat(journalRepository.findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction())
                .contains("ATTRIBUTION", "INFORMATION", "LETTRE_LUE", "EXPLICATION_DEMANDEE", "EXPLICATION_REPONDUE");

        // ⚠️ Tranche 2c (§B4.3) — la mise au point, les recours (Q5), les pièces de l'attributaire (§B5), la signature.
        MockMultipartFile piece = new MockMultipartFile("fichier", "piece.pdf", "application/pdf", "%PDF-1.4 x".getBytes(StandardCharsets.ISO_8859_1));
        mvc.perform(multipart(att + "/lots/1/mise-au-point").file(piece).header("Authorization", tokenPrmp)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RAPPORT_OBLIGATOIRE"));
        mvc.perform(multipart(att + "/lots/1/mise-au-point").file(piece).param("rapport", "Calendrier de livraison précisé.")
                .header("Authorization", tokenUgpm)).andExpect(status().isForbidden());
        mvc.perform(multipart(att + "/lots/1/mise-au-point").file(piece).param("rapport", "Calendrier de livraison précisé.")
                .header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].miseAuPoint.rapport").value("Calendrier de livraison précisé."))
                .andExpect(jsonPath("$.lots[0].miseAuPoint.fichier.nom").value("piece.pdf"));
        mvc.perform(multipart(att + "/lots/1/recours").param("type", "AUTRE").param("dateReception", aujourdhui.toString())
                .param("requerant", "BTP Beta").param("objet", "x").header("Authorization", tokenPrmp)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_INVALIDE"));
        String rec = mvc.perform(multipart(att + "/lots/1/recours").file(piece).param("type", "REVISION_ARMP").param("dateReception", aujourdhui.toString())
                .param("requerant", "BTP Beta").param("objet", "Contestation du rejet fiscal").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lots[0].recours[0].bloquant").value(true))
                .andExpect(jsonPath("$.lots[0].recours[0].finSuspension").value(aujourdhui.plusDays(20).toString()))
                .andReturn().getResponse().getContentAsString();
        long idRecours = JsonPath.<Number>read(rec, "$.lots[0].recours[0].id").longValue();
        mvc.perform(multipart(att + "/lots/1/recours").param("type", "REEXAMEN").param("dateReception", aujourdhui.toString())
                .param("requerant", "BTP Gamma").param("objet", "Réexamen").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].recours[1].bloquant").value(false))
                .andExpect(jsonPath("$.lots[0].recours[1].echeanceReponse").value(aujourdhui.plusDays(10).toString()));
        // ⚠️ R9a (§B4 n° 13) — le réexamen devient suspensif par le paramètre, lu à la lecture ; on revient au défaut.
        mvc.perform(get("/api/parametres/recours-suspensifs").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.types.length()").value(2)).andExpect(jsonPath("$.connus.length()").value(3));
        mvc.perform(put("/api/parametres/recours-suspensifs").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"types\":[\"REEXAMEN\"]}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/parametres/recours-suspensifs").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"types\":[\"RECOURS_GRACIEUX\"]}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RECOURS_INVALIDE"));
        mvc.perform(put("/api/parametres/recours-suspensifs").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"types\":[\"refere\",\"REEXAMEN\",\"REVISION_ARMP\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.types[0]").value("REEXAMEN")).andExpect(jsonPath("$.types.length()").value(3));
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].recours[1].bloquant").value(true));
        mvc.perform(put("/api/parametres/recours-suspensifs").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"types\":[]}")).andExpect(status().isOk()).andExpect(jsonPath("$.types.length()").value(0));
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].recours[0].bloquant").value(false));
        mvc.perform(put("/api/parametres/recours-suspensifs").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"types\":[\"REVISION_ARMP\",\"REFERE\"]}")).andExpect(status().isOk());
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].recours[1].bloquant").value(false));
        mvc.perform(multipart(att + "/lots/1/signature").file(piece).param("dateSignature", aujourdhui.toString()).header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECOURS_EN_COURS"));
        mvc.perform(multipart(att + "/recours/" + idRecours + "/decision").param("date", aujourdhui.toString()).param("issue", "REJETE")
                .header("Authorization", tokenPrmp)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(multipart(att + "/recours/" + idRecours + "/decision").param("date", aujourdhui.toString()).param("issue", "REJETE")
                .param("motif", "Rejet confirmé par le CRD").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].recours[0].bloquant").value(false))
                .andExpect(jsonPath("$.lots[0].recours[0].decision.issue").value("REJETE"));
        mvc.perform(multipart(att + "/lots/1/signature").file(piece).param("dateSignature", aujourdhui.toString()).header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PIECES_NON_CONFORMES"));

        // §B5 — les pièces fiscales et sociales : sous 15 jours de la lettre d'attribution, fiscale < 6 mois, sociale < 3 mois.
        LocalDate informeLe = aujourdhui.minusDays(14);
        // ⚠️ R6c (règle 7) — une pièce plus ancienne que la limite (posée en base : le dépôt la refuse) ne peut être reconnue conforme ;
        // non conforme avec motif, elle passe ; la pièce valide déposée ensuite est la dernière de son type.
        cnm.prs.entity.AttributionPiece ancienne = new cnm.prs.entity.AttributionPiece();
        ancienne.setIdDmc(idDmc);
        ancienne.setLot(1);
        ancienne.setNature(cnm.prs.entity.AttributionPiece.FISCALE);
        ancienne.setNom("attestation-ancienne.pdf");
        ancienne.setFormat("application/pdf");
        ancienne.setTaille(3L);
        ancienne.setContenu(new byte[] {1, 2, 3});
        ancienne.setDeposeLe(LocalDateTime.now());
        ancienne.setDateDelivrance(informeLe.minusMonths(7));
        long idAncienne = pieceRepository.save(ancienne).getId();
        mvc.perform(post(att + "/lots/1/pieces/" + idAncienne + "/verifier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"conforme\":true}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PIECE_TROP_ANCIENNE"))
                .andExpect(jsonPath("$.details.limite").value(informeLe.minusMonths(6).toString()))
                .andExpect(jsonPath("$.details.dateDelivrance").value(informeLe.minusMonths(7).toString()));
        mvc.perform(post(att + "/lots/1/pieces/" + idAncienne + "/verifier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"conforme\":false,\"motif\":\"Attestation de plus de six mois\"}")).andExpect(status().isOk());
        mvc.perform(multipart("/api/candidat/offres/" + b + "/pieces-attributaire").file(piece).param("type", "FISCALE")
                .param("dateDelivrance", aujourdhui.toString()).header("Authorization", jetonB)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NON_ATTRIBUTAIRE"));
        mvc.perform(multipart("/api/candidat/offres/" + a + "/pieces-attributaire").file(piece).param("type", "FISCALE")
                .param("dateDelivrance", informeLe.minusMonths(7).toString()).header("Authorization", jetonA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PIECE_PERIMEE"));
        mvc.perform(multipart("/api/candidat/offres/" + a + "/pieces-attributaire").file(piece).param("type", "FISCALE")
                .param("dateDelivrance", informeLe.minusMonths(5).toString()).header("Authorization", jetonA)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.echeance").value(informeLe.plusDays(15).toString()));
        mvc.perform(multipart("/api/candidat/offres/" + a + "/pieces-attributaire").file(piece).param("type", "SOCIALE")
                .param("dateDelivrance", aujourdhui.toString()).header("Authorization", jetonA)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.pieces.length()").value(3))
                .andExpect(jsonPath("$.limiteFiscale").value(informeLe.minusMonths(6).toString()))
                .andExpect(jsonPath("$.limiteSociale").value(informeLe.minusMonths(3).toString()));
        assertThat(notificationRepository.findPourPrmp("PRMP001", null)).extracting(Notification::getTypeNotif).contains("PIECES_ATTRIBUTAIRE_DEPOSEES");
        mvc.perform(post(att + "/lots/1/retirer").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"Pièces absentes\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DELAI_EN_COURS"));
        String vue = mvc.perform(get(att).header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsString();
        List<Number> idsPieces = JsonPath.read(vue, "$.lots[0].piecesAttributaire.pieces[?(@.verifieeLe == null)].id");
        mvc.perform(post(att + "/lots/1/pieces/" + idsPieces.get(0).longValue() + "/verifier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"conforme\":false}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        for (Number id : idsPieces) {
            mvc.perform(post(att + "/lots/1/pieces/" + id.longValue() + "/verifier").header("Authorization", tokenPrmp).contentType(JSON)
                    .content("{\"conforme\":true}")).andExpect(status().isOk());
        }
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].piecesAttributaire.fiscaleConforme").value(true))
                .andExpect(jsonPath("$.lots[0].piecesAttributaire.socialeConforme").value(true));

        // ⚠️ R10 (§B5, H2) — l'offre est déposée en ligne : l'original signé de l'acte d'engagement, reconnu conforme, précède la signature.
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].piecesAttributaire.aeOriginalExige").value(true))
                .andExpect(jsonPath("$.lots[0].piecesAttributaire.aeOriginalConforme").value(false));
        mvc.perform(multipart(att + "/lots/1/signature").file(piece).param("dateSignature", aujourdhui.toString()).header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AE_ORIGINAL_MANQUANT"));
        mvc.perform(multipart(att + "/lots/1/acte-engagement-original").file(piece).param("dateRemise", aujourdhui.plusDays(1).toString())
                .header("Authorization", tokenPrmp)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DATE_INVALIDE"));
        mvc.perform(multipart(att + "/lots/1/acte-engagement-original").param("dateRemise", aujourdhui.toString())
                .header("Authorization", tokenPrmp)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FICHIER_OBLIGATOIRE"));
        mvc.perform(multipart(att + "/lots/1/acte-engagement-original").file(piece).param("dateRemise", aujourdhui.minusDays(1).toString())
                .header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(multipart("/api/candidat/offres/" + a + "/pieces-attributaire").file(piece).param("type", "ACTE_ENGAGEMENT_ORIGINAL")
                .header("Authorization", jetonA)).andExpect(status().isCreated());   // daté du jour de son dépôt
        vue = mvc.perform(get(att).header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsString();
        List<Number> originaux = JsonPath.read(vue, "$.lots[0].piecesAttributaire.pieces[?(@.type == 'ACTE_ENGAGEMENT_ORIGINAL')].id");
        assertThat(originaux).hasSize(2);
        mvc.perform(post(att + "/lots/1/pieces/" + originaux.get(1).longValue() + "/verifier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"conforme\":true}")).andExpect(status().isOk());
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].piecesAttributaire.aeOriginalConforme").value(true));
        mvc.perform(post(att + "/lots/1/projet").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        assertThat(texteDuPdf(mvc.perform(get(att + "/lots/1/projet").header("Authorization", tokenPrmp)).andReturn().getResponse()
                .getContentAsByteArray()).replaceAll("\\s+", " ")).contains("original signé remis le "
                        + aujourdhui.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        // La signature, l'enregistrement (Q6), la notification et sa réception, l'avis d'attribution.
        mvc.perform(multipart(att + "/lots/1/signature").param("dateSignature", aujourdhui.toString()).header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FICHIER_OBLIGATOIRE"));
        mvc.perform(post(att + "/lots/1/notification").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateNotification\":\"" + aujourdhui + "\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NON_SIGNE"));
        mvc.perform(multipart(att + "/lots/1/signature").file(piece).param("dateSignature", aujourdhui.toString()).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lots[0].etat").value("SIGNE"))
                .andExpect(jsonPath("$.lots[0].signature.dateSignature").value(aujourdhui.toString()));
        // ⚠️ 2026-10-10 — un marché signé : son projet ne se refait plus.
        mvc.perform(post(att + "/lots/1/projet").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MARCHE_SIGNE"));
        // ⚠️ R9a (§B4 n° 14, art. 54) — l'approbation précède la notification : autorité, date, décision jointe.
        mvc.perform(post(att + "/lots/1/notification").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateNotification\":\"" + aujourdhui + "\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MARCHE_NON_APPROUVE"));
        mvc.perform(multipart(att + "/lots/1/approbation").file(piece).param("dateApprobation", aujourdhui.toString())
                .header("Authorization", tokenPrmp)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHAMP_OBLIGATOIRE"));
        mvc.perform(multipart(att + "/lots/1/approbation").file(piece).param("autorite", "Le Ministre").param("dateApprobation",
                aujourdhui.minusDays(1).toString()).header("Authorization", tokenPrmp)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATE_INVALIDE"));
        mvc.perform(multipart(att + "/lots/1/approbation").param("autorite", "Le Ministre").param("dateApprobation", aujourdhui.toString())
                .header("Authorization", tokenPrmp)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FICHIER_OBLIGATOIRE"));
        mvc.perform(multipart(att + "/lots/1/approbation").file(piece).param("autorite", "Le Ministre").param("dateApprobation", aujourdhui.toString())
                .header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].approbation.autorite").value("Le Ministre"))
                .andExpect(jsonPath("$.lots[0].approbation.date").value(aujourdhui.toString()))
                .andExpect(jsonPath("$.lots[0].approbation.fichier").isNotEmpty());
        mvc.perform(multipart(att + "/lots/1/approbation").file(piece).param("autorite", "Le Ministre").param("dateApprobation", aujourdhui.toString())
                .header("Authorization", tokenPrmp)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_APPROUVE"));
        mvc.perform(post(att + "/lots/1/notification").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateNotification\":\"" + aujourdhui + "\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NON_ENREGISTRE"));
        mvc.perform(multipart(att + "/lots/1/enregistrement").file(piece).param("dateEnregistrement", aujourdhui.toString())
                .param("reference", "ENR-2026-0042").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].enregistrement.reference").value("ENR-2026-0042"));
        mvc.perform(post(att + "/lots/1/avis").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"datePublication\":\"" + aujourdhui + "\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NON_NOTIFIE"));
        mvc.perform(post(att + "/lots/1/notification").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateNotification\":\"" + aujourdhui + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].etat").value("NOTIFIE")).andExpect(jsonPath("$.lots[0].notification.recueLe").isEmpty())
                .andExpect(jsonPath("$.lots[0].avisAttribution.depart").value(aujourdhui.toString()))
                .andExpect(jsonPath("$.lots[0].avisAttribution.echeance").value(aujourdhui.plusDays(30).toString()))
                .andExpect(jsonPath("$.lots[0].avisAttribution.enRetard").value(false));
        mvc.perform(get("/api/candidat/offres/" + a + "/resultat").header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.retenu").value(true)).andExpect(jsonPath("$.marcheDisponible").value(true))
                .andExpect(jsonPath("$.notificationRecueLe").isNotEmpty());
        mvc.perform(get("/api/candidat/offres/" + a + "/marche").header("Authorization", jetonA)).andExpect(status().isOk());
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].notification.receptionDeclaree").value(false));
        // ⚠️ R6c (règle 8) — les 30 jours partent de la réception (ici l'accusé de lecture) ; alerte à J+20, une fois ; retard passé J+30.
        cnm.prs.entity.Attribution notifiee = attributionRepository.findById(new cnm.prs.entity.Attribution.Cle(idDmc, 1)).orElseThrow();
        notifiee.setDateNotification(aujourdhui.minusDays(23));
        notifiee.setNotificationRecueLe(aujourdhui.minusDays(21).atStartOfDay());
        attributionRepository.save(notifiee);
        mvc.perform(get(att).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.lots[0].avisAttribution.depart").value(aujourdhui.minusDays(21).toString()))
                .andExpect(jsonPath("$.lots[0].avisAttribution.echeance").value(aujourdhui.plusDays(9).toString()))
                .andExpect(jsonPath("$.lots[0].avisAttribution.enRetard").value(false));
        attributionService.alerter();
        assertThat(notificationRepository.findAll()).filteredOn(n -> "ECHEANCE_AVIS_ATTRIBUTION".equals(n.getTypeNotif())).hasSize(1)
                .allSatisfy(n -> assertThat(n.getCorps()).contains("20 jours", aujourdhui.plusDays(9).format(JOUR_FR)));
        attributionService.alerter();
        assertThat(notificationRepository.findAll()).filteredOn(n -> "ECHEANCE_AVIS_ATTRIBUTION".equals(n.getTypeNotif())).hasSize(1);
        notifiee.setNotificationRecueLe(aujourdhui.minusDays(31).atStartOfDay());
        attributionRepository.save(notifiee);
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.compteurs.avisAPublier").value(1))
                .andExpect(jsonPath("$.compteurs.avisEnRetard").value(1));
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].avisAttribution.enRetard").value(true));
        mvc.perform(post(att + "/lots/1/avis").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"datePublication\":\"" + aujourdhui + "\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.lots[0].etat").value("PUBLIE"))
                .andExpect(jsonPath("$.lots[0].avisAttribution.enRetard").value(true));
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.compteurs.avisEnRetard").value(0));
        String avis = texteDuPdf(mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/avis-attribution/1")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(avis).contains("AVIS D'ATTRIBUTION DE MARCHÉ", "BTP Alpha", "12 500 000 Ariary hors taxes", "modèle provisoire");
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/resultats")).andExpect(jsonPath("$[0].avisDisponible").value(true));
        mvc.perform(post(att + "/lots/1/retirer").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_SIGNE"));
        assertThat(journalRepository.findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction()).contains("MISE_AU_POINT", "RECOURS",
                "DECISION_RECOURS", "PIECE_ATTRIBUTAIRE", "PIECE_VERIFIEE", "SIGNATURE_MARCHE", "ENREGISTREMENT", "NOTIFICATION",
                "NOTIFICATION_RECUE", "AVIS_ATTRIBUTION");
        // La grille d'examen du dossier de marché, semée au démarrage là où la famille DDM existe.
        assertThat(pointsCtrlSeeder.semer()).isEqualTo(9);
        assertThat(pointsCtrlSeeder.semer()).isZero();
        assertThat(journalRepository.findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction()).contains("DOSSIER_MARCHE");

        // ⚠️ 2d-2 (Q8) — la réattribution après le retrait (le retrait réussi est posé en base : son parcours est éprouvé à part).
        cnm.prs.entity.Attribution retire = attributionRepository.findById(new cnm.prs.entity.Attribution.Cle(idDmc, 1)).orElseThrow();
        retire.setSigneLe(null);
        retire.setDateSignature(null);
        retire.setNotifieLe(null);
        retire.setDateNotification(null);
        retire.setAvisPublieLe(null);
        retire.setRetireLe(java.time.LocalDateTime.now());
        retire.setMotifRetrait("Pièces fiscales et sociales non produites");
        attributionRepository.save(retire);
        mvc.perform(post(att + "/lots/1/reattribuer").header("Authorization", tokenUgpm).contentType(JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(att + "/lots/1/reattribuer").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        // B est non qualifiée, C rejetée (anormalement basse) : aucun suivant — l'infructuosité étant exclue, le sans suite seul.
        mvc.perform(post(att + "/lots/1/reattribuer").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"Pièces absentes\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUCUN_SUIVANT_ELIGIBLE"));
        // B redevient éligible (sa décision de post-qualification annulée) : la réattribution rouvre la post-qualification pour elle.
        decisionRepository.findAll().stream().filter(d -> d.getIdOffre().equals(b) && "QUALIFICATION".equals(d.getEtape()) && d.getRemplaceeLe() == null)
                .forEach(d -> {
                    d.setRemplaceeLe(java.time.LocalDateTime.now());
                    decisionRepository.save(d);
                });
        String reat = mvc.perform(post(att + "/lots/1/reattribuer").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"Pièces absentes à l'échéance\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(reat, "$.lots[0].etat")).isEqualTo("EN_EVALUATION");
        assertThat(JsonPath.<String>read(reat, "$.lots[0].reprises[0].type")).isEqualTo("REATTRIBUTION");
        assertThat(JsonPath.<String>read(reat, "$.lots[0].reprises[0].idOffreRetiree")).isEqualTo(a);
        assertThat(JsonPath.<String>read(reat, "$.lots[0].reprises[0].note")).startsWith("Offres valides jusqu'au");
        mvc.perform(get(base).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.etat").value("EN_COURS"));
        mvc.perform(get(base + "/lots/1/qualification").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idOffre").value(b));
        assertThat(decisionRepository.findAll()).filteredOn(d -> d.getIdOffre().equals(a) && "QUALIFICATION".equals(d.getEtape())
                && d.getRemplaceeLe() == null).extracting(cnm.prs.entity.EvaluationDecision::getDecision).containsExactly("NON_QUALIFIE");
        assertThat(pieceRepository.findAll()).filteredOn(p -> p.getIdDmc().equals(idDmc)).allMatch(p -> p.getArchiveLe() != null);
        mvc.perform(post(att + "/lots/1/reattribuer").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NON_RETIRE"));

        // ⚠️ 2d-3 (§B6, Q10) — la déclaration sans suite : un dossier DSS au circuit de la Commission, l'avis, la déclaration.
        typeDossierRepository.save(new cnm.prs.entity.TypeDossier("DSS", "Déclaration sans suite"));
        sousTypeDossierRepository.save(new cnm.prs.entity.SousTypeDossier("DSS", "Déclaration sans suite", "DSS"));
        cnm.prs.entity.TypePieceJointe motifsType = typePieceJointeRepository.findById(seedTypePiece("Motifs de la déclaration sans suite", true,
                "DSS", 1)).orElseThrow();
        motifsType.setCode("MOTIFS_SANS_SUITE");
        typePieceJointeRepository.save(motifsType);
        String ss = "/api/fiches-marche/" + idDmc + "/sans-suite";
        mvc.perform(post(ss).header("Authorization", tokenUgpm).contentType(JSON).content("{\"motifs\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post(ss).header("Authorization", tokenPrmp).contentType(JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOTIFS_OBLIGATOIRES"));
        String dem = mvc.perform(post(ss).header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motifs\":\"Le besoin a disparu : le programme est annulé par la loi de finances rectificative.\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(dem, "$.courante.etat")).isEqualTo("A_SOUMETTRE");
        int idDss = JsonPath.read(dem, "$.courante.idDossier");
        assertThat(dossierRepository.findById(idDss).orElseThrow().getIdTypeDossier()).isEqualTo("DSS");
        assertThat(pieceJointeDossierRepository.findAll()).filteredOn(p -> p.getIdDossier().equals(idDss)).hasSize(1);
        mvc.perform(post(ss).header("Authorization", tokenPrmp).contentType(JSON).content("{\"motifs\":\"x\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SANS_SUITE_EN_COURS"));
        String decision = "{\"decision\":{\"reference\":\"DEC-SS-1\",\"date\":\"" + aujourdhui + "\"}}";
        mvc.perform(post(ss + "/declarer").header("Authorization", tokenPrmp).contentType(JSON).content(decision)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVIS_NON_FAVORABLE"));
        // La Commission reçoit le dossier et rend un avis favorable (PV signé posé en base).
        receptionRepository.save(reception(idDss, idDss, "CTRCC1", true));
        dispatchRepository.save(dispatch(idDss, idDss, "CTRCC1", "CTRMEM", "CTRPRE"));
        // ⚠️ R6c (règle 9 ; V108) — l'échéance des 5 jours passée sans avis (réception de fixture au 02/06) : l'alerte de la veille puis la
        // relance RELANCE_SANS_SUITE à la PRMP, au membre examinateur et au Président, une fois chacune.
        assertThat(sansSuiteService.suivre()).isEqualTo(2);
        assertThat(notificationRepository.findAll()).filteredOn(n -> "RELANCE_SANS_SUITE".equals(n.getTypeNotif()))
                .extracting(Notification::getDestinataireRef).contains("PRMP001", "CTRMEM", "CTRPRE");
        assertThat(sansSuiteService.suivre()).isZero();
        mvc.perform(get(ss).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.courante.relanceLe").isNotEmpty())
                .andExpect(jsonPath("$.courante.echeanceDepassee").value(true));
        examenRepository.save(examen(idDss, idDss, "CTRMEM"));
        seedPvSigne(idDss, idDss);
        String favorable = mvc.perform(get(ss).header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<String>read(favorable, "$.courante.etat")).isEqualTo("FAVORABLE");
        assertThat(JsonPath.<String>read(favorable, "$.courante.echeance")).isNotNull();
        mvc.perform(post(ss + "/declarer").header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":{}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DECISION_OBLIGATOIRE"));
        mvc.perform(post(ss + "/declarer").header("Authorization", tokenPrmp).contentType(JSON).content(decision)).andExpect(status().isOk())
                .andExpect(jsonPath("$.declaree").value(true)).andExpect(jsonPath("$.courante.etat").value("DECLAREE"));
        assertThat(notificationRepository.findPourRefEtType("C900000052", "CANDIDAT")).extracting(Notification::getTypeNotif)
                .contains("PROCEDURE_SANS_SUITE");
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/resultats")).andExpect(jsonPath("$[0].sansSuite").value(true))
                .andExpect(jsonPath("$[0].motifsSansSuite").value(org.hamcrest.Matchers.startsWith("Le besoin a disparu")));
        mvc.perform(post(att + "/lots/1/attribuer").header("Authorization", tokenPrmp).contentType(JSON).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SANS_SUITE_DECLAREE"));
        mvc.perform(post(ss).header("Authorization", tokenPrmp).contentType(JSON).content("{\"motifs\":\"x\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SANS_SUITE_DECLAREE"));
        mvc.perform(get(ss + "/" + JsonPath.<Number>read(favorable, "$.courante.id").longValue() + "/motifs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
    }


    @Autowired private cnm.prs.repository.AttributionPieceRepository pieceRepository;
    @Autowired private cnm.prs.service.SansSuiteService sansSuiteService;
    @Autowired private cnm.prs.repository.QuestionCandidatRepository questionRepository;
    private static final java.time.format.DateTimeFormatter JOUR_FR = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

    @Autowired private cnm.prs.repository.FicheMarcheRepository ficheMarcheRepository;
    @Autowired private cnm.prs.repository.ContratVigueurRepository contratRepository;

    @Test
    @DisplayName("MS-1 (16/10, contrat en vigueur, V124) : la PRMP lit ses contrats et leurs alertes, le badge compte la reconduction à décider ; "
            + "la reconduction se décide par écrit, dans la limite des reconductions ; une décision par période ; échu, plus rien")
    void contratEnVigueur() throws Exception {
        cnm.prs.entity.Attribution notifiee = new cnm.prs.entity.Attribution();
        notifiee.setIdDmc(idDmc);
        notifiee.setLot(1);
        notifiee.setEtat("NOTIFIE");
        attributionRepository.save(notifiee);
        LocalDate effet = aujourdhui.minusMonths(10);
        cnm.prs.entity.ContratVigueur c = new cnm.prs.entity.ContratVigueur();
        c.setIdDmc(idDmc);
        c.setLot(1);
        c.setTypeMarche("CONTRAT_CADRE");
        c.setDateEffet(effet);
        c.setPeriodeMois(12);
        c.setDateFin(effet.plusMonths(12).minusDays(1));
        c.setDureeMaxMois(24);
        c.setReconductible(true);
        c.setReconductionsMax(1);
        c.setPreavisMois(3);
        c.setCreeLe(LocalDateTime.now());
        long id = contratRepository.save(c).getId();
        String url = "/api/contrats-en-vigueur";
        // ⚠️ 2026-10-29 (« contrat-cadre-4a », §B2) — un titulaire, pour les notifications de la décision et de l'échéance.
        String titulaire = offre("C900000053", "7777888999", "BTP Gamma", 1, "INTACTE", null, "5000000");
        cnm.prs.entity.AttributionTitulaire tit = new cnm.prs.entity.AttributionTitulaire();
        tit.setIdDmc(idDmc);
        tit.setLot(1);
        tit.setIdOffre(titulaire);
        tit.setRang(1);
        tit.setMontant(new java.math.BigDecimal("5000000"));
        tit.setRetenuLe(LocalDateTime.now());
        titulaireRepository.save(tit);
        // La tâche planifiée : RECONDUCTION_A_DECIDER à la PRMP, une fois pour la période.
        assertThat(contratService.rappeler()).isEqualTo(1);
        assertThat(contratService.rappeler()).isZero();
        assertThat(notificationRepository.findAll()).filteredOn(n -> "RECONDUCTION_A_DECIDER".equals(n.getTypeNotif()))
                .extracting(Notification::getDestinataireType).contains("PRMP");

        String liste = mvc.perform(get(url).header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Number>>read(liste, "$[*].id")).extracting(Number::longValue).containsExactly(id);
        assertThat(JsonPath.<String>read(liste, "$[0].etat")).isEqualTo("EN_VIGUEUR");
        assertThat(JsonPath.<List<String>>read(liste, "$[0].alertes")).containsExactly("RECONDUCTION_A_DECIDER");
        assertThat(JsonPath.<String>read(liste, "$[0].finMaximale")).isEqualTo(effet.plusMonths(24).minusDays(1).toString());
        mvc.perform(get(url).header("Authorization", jetonA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.compteurs.reconductionsADecider").value(1));

        // La décision écrite de la PRMP (AE-CC, art. 7.3).
        String rec = url + "/" + id + "/reconduction";
        mvc.perform(post(rec).header("Authorization", tokenUgpm).contentType(JSON).content("{\"decision\":\"RECONDUIRE\"}")).andExpect(status().isForbidden());
        mvc.perform(post(rec).header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"PEUT-ETRE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DECISION_INVALIDE"));
        mvc.perform(post(rec).header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"RECONDUIRE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DECISION_OBLIGATOIRE"));
        String decision = ",\"reference\":\"DEC-2026-12\",\"dateDecision\":\"" + aujourdhui + "\"}";
        LocalDate finReconduite = effet.plusMonths(12).plusMonths(12).minusDays(1);
        mvc.perform(post(rec).header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"RECONDUIRE\"" + decision))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dateFin").value(finReconduite.toString()))
                .andExpect(jsonPath("$.reconductions").value(1)).andExpect(jsonPath("$.decisions[0].horsPreavis").value(true))
                .andExpect(jsonPath("$.alertes.length()").value(0));
        mvc.perform(post(rec).header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"RECONDUIRE\"" + decision))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECONDUCTIONS_EPUISEES"));
        mvc.perform(post(rec).header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"NE_PAS_RECONDUIRE\"" + decision))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decisions.length()").value(2));
        // §B2 — chaque titulaire apprend la décision, avec la date de fin qui en résulte (espace candidat, objet PROCEDURE).
        assertThat(notificationRepository.findAll()).filteredOn(n -> "C900000053".equals(n.getDestinataireRef()))
                .filteredOn(n -> n.getTypeNotif().startsWith("CONTRAT_"))
                .extracting(Notification::getTypeNotif, Notification::getTypeObjet, n -> n.getCorps().contains(finReconduite.format(
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))))
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("CONTRAT_RECONDUIT", "PROCEDURE", true),
                        org.assertj.core.groups.Tuple.tuple("CONTRAT_NON_RECONDUIT", "PROCEDURE", true));
        mvc.perform(post(rec).header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"NE_PAS_RECONDUIRE\"" + decision))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_DECIDE"));

        // Échu : plus de reconduction, l'alerte ECHU.
        cnm.prs.entity.ContratVigueur echu = contratRepository.findById(id).orElseThrow();
        echu.setDateFin(aujourdhui.minusDays(1));
        contratRepository.save(echu);
        mvc.perform(get(url + "/" + id).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("ECHU")).andExpect(jsonPath("$.alertes[0]").value("ECHU"));
        mvc.perform(post(rec).header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"NE_PAS_RECONDUIRE\"" + decision))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRAT_ECHU"));
        // §B2 — l'échéance : CONTRAT_ECHU à la PRMP et au titulaire, une fois.
        assertThat(contratService.rappeler()).isEqualTo(1);
        assertThat(contratService.rappeler()).isZero();
        assertThat(notificationRepository.findAll()).filteredOn(n -> "CONTRAT_ECHU".equals(n.getTypeNotif()))
                .extracting(Notification::getDestinataireType).contains("PRMP", "CANDIDAT");
    }

    @Autowired private cnm.prs.repository.AttributionTitulaireRepository titulaireRepository;
    @Autowired private cnm.prs.repository.RemiseConcurrenceRepository remiseRepository;
    @Autowired private cnm.prs.service.ContratVigueurService contratService;

    @Test
    @DisplayName("MS-2 (16/10, remise en concurrence, V125, décret 2019-1064 art. 20-30) : tous les titulaires sont consultés et notifiés ; "
            + "les réponses, en ligne ou papier, restent fermées jusqu'à l'échéance ; le président de la CAO évalue (écart au prix moyen, "
            + "moins-disante conforme) ; la PRMP attribue au seul proposé et chacun est informé ; sans réponse, l'infructuosité")
    void remiseEnConcurrence() throws Exception {
        String a = offre("C900000051", "1111222333", "BTP Alpha", 1, "INTACTE", null, "12500000");
        String b = offre("C900000052", "4444555666", "BTP Beta", 2, "INTACTE", null, "11900000");
        cnm.prs.entity.Attribution notifiee = new cnm.prs.entity.Attribution();
        notifiee.setIdDmc(idDmc);
        notifiee.setLot(1);
        notifiee.setEtat("NOTIFIE");
        notifiee.setReferenceEnregistrement("CC-2026/017");
        notifiee.setDateSignature(aujourdhui.minusMonths(1));
        attributionRepository.save(notifiee);
        for (String[] t : List.of(new String[] { a, "1", "12500000" }, new String[] { b, "2", "11900000" })) {
            cnm.prs.entity.AttributionTitulaire tit = new cnm.prs.entity.AttributionTitulaire();
            tit.setIdDmc(idDmc);
            tit.setLot(1);
            tit.setIdOffre(t[0]);
            tit.setRang(Integer.valueOf(t[1]));
            tit.setMontant(new java.math.BigDecimal(t[2]));
            tit.setRetenuLe(LocalDateTime.now());
            titulaireRepository.save(tit);
        }
        cnm.prs.entity.ContratVigueur c = new cnm.prs.entity.ContratVigueur();
        c.setIdDmc(idDmc);
        c.setLot(1);
        c.setTypeMarche("CONTRAT_CADRE");
        c.setDateEffet(aujourdhui.minusMonths(1));
        c.setPeriodeMois(12);
        c.setDateFin(aujourdhui.plusMonths(11));
        c.setDureeMaxMois(12);
        c.setReconductible(false);
        c.setCreeLe(LocalDateTime.now());
        // ⚠️ 2026-10-29 (« contrat-cadre-4a », §B3) — un calendrier structuré : tous les 3 mois, première remise dans 10 jours.
        c.setPeriodiciteMois(3);
        c.setPremiereRemise(aujourdhui.plusDays(10));
        long idContrat = contratRepository.save(c).getId();
        mvc.perform(get("/api/contrats-en-vigueur/" + idContrat).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.prochaineRemise").value(aujourdhui.plusDays(10).toString()))
                .andExpect(jsonPath("$.periodiciteMois").value(3)).andExpect(jsonPath("$.alertes[0]").value("REMISE_A_LANCER"));
        assertThat(contratService.rappeler()).isEqualTo(1);
        assertThat(contratService.rappeler()).isZero();
        assertThat(notificationRepository.findAll()).filteredOn(n -> "REMISE_A_LANCER".equals(n.getTypeNotif()))
                .extracting(Notification::getDestinataireType).contains("PRMP");
        String url = "/api/contrats-en-vigueur/" + idContrat + "/remises";
        String echeance = LocalDateTime.now().plusDays(5).withNano(0).withSecond(0).toString();
        String corps = "{\"objet\":\"Fourniture de 40 ordinateurs portables\",\"specifications\":\"Processeur 8 cœurs, 16 Go\",\"delaiExecution\":\"30 jours\","
                + "\"validiteJours\":90,\"lieuLivraison\":\"Antananarivo\",\"echeance\":\"" + echeance + "\"}";

        // Le lancement : PRMP ou UGPM ; tous les titulaires consultés et notifiés ; la lettre d'invitation de l'annexe 1.
        mvc.perform(post(url).header("Authorization", jetonM1).contentType(JSON).content(corps)).andExpect(status().isForbidden());
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content("{\"objet\":\"x\",\"delaiExecution\":\"30 jours\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHAMP_OBLIGATOIRE"));
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content(corps.replace(echeance, LocalDateTime.now().minusHours(1)
                .withNano(0).toString()))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ECHEANCE_INVALIDE"));
        String lancee = mvc.perform(post(url).header("Authorization", tokenUgpm).contentType(JSON).content(corps)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("REMISE_EN_CONCURRENCE")).andExpect(jsonPath("$.etat").value("LANCEE"))
                .andExpect(jsonPath("$.numero").value(1)).andExpect(jsonPath("$.lettreDisponible").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(lancee, "$.consultes[*].idOffre")).containsExactlyInAnyOrder(a, b);
        long id = JsonPath.<Number>read(lancee, "$.id").longValue();
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content(corps)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMISE_EN_COURS"));
        String lettre = texteDuPdf(mvc.perform(get("/api/remises/" + id + "/lettre").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(lettre).contains("CONTRAT-CADRE N° CC-2026/017", "LETTRE D'INVITATION", "Fourniture de 40 ordinateurs portables",
                "dont vous êtes titulaire", "ANNEXE 1", "ARTICLE 9. ATTRIBUTION DU MARCHÉ", "Le délai d'exécution est fixé à 30 jours",
                "90 jours", "Processeur 8 cœurs");
        assertThat(notificationRepository.findAll()).filteredOn(n -> "REMISE_CONCURRENCE".equals(n.getTypeNotif()))
                .extracting(Notification::getDestinataireRef).containsExactlyInAnyOrder("C900000051", "C900000052");
        // §B3 — la remise lancée dans la fenêtre satisfait l'échéance : plus d'alerte, la suivante est servie.
        mvc.perform(get("/api/contrats-en-vigueur/" + idContrat).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.prochaineRemise").value(aujourdhui.plusDays(10).plusMonths(3).toString()))
                .andExpect(jsonPath("$.alertes").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("REMISE_A_LANCER"))));

        // Les réponses : en ligne par le titulaire (un non-consulté ne voit rien), sur papier par la PRMP ; fermées jusqu'à l'échéance.
        String jetonC = bearer("c@eval.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000053", null);
        MockMultipartFile devis = new MockMultipartFile("fichier", "devis.pdf", "application/pdf", "%PDF-1.4 x".getBytes(StandardCharsets.ISO_8859_1));
        mvc.perform(get("/api/candidat/remises").header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id)).andExpect(jsonPath("$[0].resultat").value("EN_COURS"));
        mvc.perform(get("/api/candidat/remises/" + id + "/lettre").header("Authorization", jetonA)).andExpect(status().isOk());
        mvc.perform(multipart("/api/candidat/remises/" + id + "/reponse").file(devis).param("nature", "DEVIS").param("montantHt", "9000000")
                .header("Authorization", jetonC)).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/candidat/remises/" + id + "/reponse").file(devis).param("nature", "OFFRE").param("montantHt", "9000000")
                .header("Authorization", jetonA)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NATURE_INVALIDE"));
        mvc.perform(multipart("/api/candidat/remises/" + id + "/reponse").file(devis).param("nature", "DEVIS").param("montantHt", "9000000")
                .param("delai", "20 jours").header("Authorization", jetonA)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.maReponse.montantHt").value(9000000));
        mvc.perform(multipart("/api/remises/" + id + "/reponses").file(devis).param("idOffre", b).param("nature", "FACTURE_PRO_FORMA")
                .param("montantHt", "8000000").header("Authorization", tokenPrmp)).andExpect(status().isCreated());
        String fermee = mvc.perform(get("/api/remises/" + id).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.echeancePassee").value(false)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(fermee, "$.reponses[*].montantHt")).containsOnlyNulls().hasSize(2);
        assertThat(notificationRepository.findAll()).extracting(Notification::getTypeNotif).contains("REMISE_REPONSE_RECUE");
        long idReponse = JsonPath.<Number>read(fermee, "$.reponses[0].id").longValue();
        mvc.perform(get("/api/remises/" + id + "/reponses/" + idReponse + "/fichier").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPONSES_FERMEES"));
        mvc.perform(post("/api/remises/" + id + "/evaluation").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ECHEANCE_NON_PASSEE"));

        // L'échéance passée : les réponses s'ouvrent, le dépôt se ferme.
        cnm.prs.entity.RemiseConcurrence x = remiseRepository.findById(id).orElseThrow();
        x.setEcheance(LocalDateTime.now().minusMinutes(1).withNano(0));
        remiseRepository.save(x);
        mvc.perform(multipart("/api/candidat/remises/" + id + "/reponse").file(devis).param("nature", "DEVIS").param("montantHt", "8500000")
                .header("Authorization", jetonA)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ECHEANCE_PASSEE"));
        mvc.perform(get("/api/remises/" + id + "/reponses/" + idReponse + "/fichier").header("Authorization", jetonM1)).andExpect(status().isOk());

        // L'évaluation : le président seul ; une décision par réponse ; la non-conformité motivée ; la moins-disante conforme proposée.
        String evaluation = "/api/remises/" + id + "/evaluation";
        String decisions = "{\"prixMoyen\":6000000,\"decisions\":[{\"idOffre\":\"" + a + "\",\"conforme\":true},{\"idOffre\":\"" + b
                + "\",\"conforme\":false%s}]}";
        mvc.perform(post(evaluation).header("Authorization", jetonM2).contentType(JSON).content(String.format(decisions, "")))
                .andExpect(status().isForbidden());
        mvc.perform(post(evaluation).header("Authorization", jetonM1).contentType(JSON).content("{\"decisions\":[{\"idOffre\":\"" + a + "\",\"conforme\":true}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DECISIONS_INCOMPLETES")).andExpect(jsonPath("$.details.offres[0]").value(b));
        mvc.perform(post(evaluation).header("Authorization", jetonM1).contentType(JSON).content(String.format(decisions, "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(post(evaluation).header("Authorization", jetonM1).contentType(JSON)
                .content(String.format(decisions, ",\"motif\":\"Spécifications non respectées\"").replace("]}", "],\"idOffreProposee\":\"" + b + "\"}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFRE_NON_CONFORME"));
        String evaluee = mvc.perform(post(evaluation).header("Authorization", jetonM1).contentType(JSON)
                .content(String.format(decisions, ",\"motif\":\"Spécifications non respectées\""))).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("EVALUEE")).andExpect(jsonPath("$.idOffreProposee").value(a))
                .andExpect(jsonPath("$.rapportDisponible").value(true)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Number>>read(evaluee, "$.reponses[?(@.idOffre == '" + a + "')].ecartPrixMoyen")).extracting(Number::doubleValue)
                .containsExactly(50.0);
        assertThat(JsonPath.<List<String>>read(evaluee, "$.alertes")).isEmpty();   // une seule remise au-delà de 30 % : pas encore délié
        String rapport = texteDuPdf(mvc.perform(get("/api/remises/" + id + "/rapport").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(rapport).contains("RAPPORT D'ÉVALUATION SIMPLIFIÉ", "La commission propose d'attribuer le marché subséquent à BTP Alpha",
                "non conforme — Spécifications non respectées", "6 000 000 Ariary hors taxes");

        // La décision de la PRMP : au seul proposé ; chacun est informé.
        mvc.perform(post("/api/remises/" + id + "/attribution").header("Authorization", tokenUgpm).contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/remises/" + id + "/attribution").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("ATTRIBUEE")).andExpect(jsonPath("$.idOffreRetenue").value(a))
                .andExpect(jsonPath("$.montantRetenu").value(9000000));
        mvc.perform(get("/api/candidat/remises").header("Authorization", jetonA)).andExpect(jsonPath("$[0].resultat").value("RETENU"));
        mvc.perform(get("/api/candidat/remises").header("Authorization", jetonB)).andExpect(jsonPath("$[0].resultat").value("NON_RETENU"))
                .andExpect(jsonPath("$[0].motif").value(org.hamcrest.Matchers.containsString("Spécifications non respectées")));
        assertThat(notificationRepository.findAll()).filteredOn(n -> "REMISE_RESULTAT".equals(n.getTypeNotif()))
                .extracting(Notification::getDestinataireRef).contains("C900000051", "C900000052");

        // Une seconde remise sans réponse : rien à proposer, l'infructuosité motivée ; l'absence comptée.
        long id2 = JsonPath.<Number>read(mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content(corps))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(2)).andReturn().getResponse().getContentAsString(), "$.id").longValue();
        cnm.prs.entity.RemiseConcurrence x2 = remiseRepository.findById(id2).orElseThrow();
        x2.setEcheance(LocalDateTime.now().minusMinutes(1).withNano(0));
        remiseRepository.save(x2);
        String vide = mvc.perform(post("/api/remises/" + id2 + "/evaluation").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idOffreProposee").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(vide, "$.consultes[*].absencesConsecutives")).containsExactly(1, 1);
        mvc.perform(post("/api/remises/" + id2 + "/attribution").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUCUNE_PROPOSITION"));
        mvc.perform(post("/api/remises/" + id2 + "/infructueuse").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(post("/api/remises/" + id2 + "/infructueuse").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"Aucune réponse reçue\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("INFRUCTUEUSE"));
        mvc.perform(get(url).header("Authorization", jetonM1)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/remises").header("Authorization", jetonA)).andExpect(status().isForbidden());
    }

    /** Un contrat en vigueur du lot 1, effet {@code effet}, fin un an moins un jour après. */
    private long contrat(String type, LocalDate effet, String estime, String minimum, String maximum) {
        cnm.prs.entity.ContratVigueur c = new cnm.prs.entity.ContratVigueur();
        c.setIdDmc(idDmc);
        c.setLot(1);
        c.setTypeMarche(type);
        c.setDateEffet(effet);
        c.setPeriodeMois(12);
        c.setDateFin(effet.plusYears(1).minusDays(1));
        c.setDureeMaxMois(12);
        c.setMontantEstime(estime == null ? null : new java.math.BigDecimal(estime));
        c.setMontantMinimum(minimum == null ? null : new java.math.BigDecimal(minimum));
        c.setMontantMaximum(maximum == null ? null : new java.math.BigDecimal(maximum));
        c.setCreeLe(LocalDateTime.now());
        return contratRepository.save(c).getId();
    }

    @Test
    @DisplayName("MS-3 (16/10, bons de commande, V126, décret 2019-1064 art. 8 et 33) : le bon du contrat-cadre suit le marché subséquent "
            + "attribué, au titulaire retenu, dans son montant ; émis par la PRMP, lu par le titulaire (accusé) ; annulé, il sort des cumuls ; "
            + "la valeur estimée dépassée est signalée")
    void bonsCommandeContratCadre() throws Exception {
        String a = offre("C900000051", "1111222333", "BTP Alpha", 1, "INTACTE", null, "12500000");
        cnm.prs.entity.Attribution notifiee = new cnm.prs.entity.Attribution();
        notifiee.setIdDmc(idDmc);
        notifiee.setLot(1);
        notifiee.setEtat("NOTIFIE");
        notifiee.setReferenceEnregistrement("CC-2026/017");
        attributionRepository.save(notifiee);
        long idContrat = contrat("CONTRAT_CADRE", aujourdhui.minusMonths(1), "5000000", null, null);
        cnm.prs.entity.RemiseConcurrence x = new cnm.prs.entity.RemiseConcurrence();
        x.setIdContrat(idContrat);
        x.setNumero(1);
        x.setMode(cnm.prs.entity.RemiseConcurrence.REMISE_EN_CONCURRENCE);
        x.setEtat(cnm.prs.entity.RemiseConcurrence.LANCEE);
        x.setObjet("Fourniture de 40 ordinateurs portables");
        x.setSpecifications("16 Go");
        x.setDelaiExecution("30 jours");
        x.setLieuLivraison("Antananarivo");
        x.setEcheance(LocalDateTime.now().minusDays(1));
        x.setLanceeLe(LocalDateTime.now().minusDays(10));
        long idRemise = remiseRepository.save(x).getId();
        String url = "/api/contrats-en-vigueur/" + idContrat + "/bons-commande";
        String ligne = "{\"designation\":\"Ordinateur portable\",\"unite\":\"u\",\"quantite\":%s,\"prixUnitaire\":200000}";
        String bon = "{\"idRemise\":" + idRemise + ",\"lignes\":[" + String.format(ligne, 20) + "]}";

        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content("{\"lignes\":[" + String.format(ligne, 1) + "]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REMISE_OBLIGATOIRE"));
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content(bon)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMISE_NON_ATTRIBUEE"));
        x = remiseRepository.findById(idRemise).orElseThrow();
        x.setEtat(cnm.prs.entity.RemiseConcurrence.ATTRIBUEE);
        x.setIdOffreRetenue(a);
        x.setMontantRetenu(new java.math.BigDecimal("9000000"));
        x.setDecideeLe(LocalDateTime.now().minusDays(1));
        remiseRepository.save(x);
        mvc.perform(post(url).header("Authorization", tokenUgpm).contentType(JSON).content(bon)).andExpect(status().isForbidden());
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content("{\"idRemise\":" + idRemise + ",\"lignes\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIGNES_OBLIGATOIRES"));
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content("{\"idRemise\":" + idRemise
                + ",\"lignes\":[{\"designation\":\"x\",\"quantite\":1}]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRIX_OBLIGATOIRE"));
        // Le bon : objet, délai et lieu repris du marché subséquent ; 20 × 200 000 = 4 000 000.
        String emis = mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content(bon)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1)).andExpect(jsonPath("$.idOffre").value(a)).andExpect(jsonPath("$.montantHt").value(4000000))
                .andExpect(jsonPath("$.objet").value("Fourniture de 40 ordinateurs portables")).andExpect(jsonPath("$.delaiLivraison").value("30 jours"))
                .andExpect(jsonPath("$.numeroRemise").value(1)).andReturn().getResponse().getContentAsString();
        long id1 = JsonPath.<Number>read(emis, "$.id").longValue();
        String pdf = texteDuPdf(mvc.perform(get("/api/bons-commande/" + id1 + "/document").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(pdf).contains("BON DE COMMANDE N° 1", "Contrat-cadre n° CC-2026/017", "Marché subséquent « Fourniture de 40 ordinateurs portables »",
                "BTP Alpha, NIF 1111222333", "4 000 000", "Délai de livraison : 30 jours");
        assertThat(notificationRepository.findAll()).filteredOn(n -> "BON_COMMANDE".equals(n.getTypeNotif()))
                .extracting(Notification::getDestinataireRef).containsExactly("C900000051");
        // Au-delà du montant retenu : refusé.
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content(bon.replace(String.format(ligne, 20), String.format(ligne, 30))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTANT_SUBSEQUENT_DEPASSE"))
                .andExpect(jsonPath("$.details.dejaCommande").value(4000000));
        // Le titulaire lit son bon : l'accusé de réception.
        mvc.perform(get("/api/candidat/bons-commande").header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id1)).andExpect(jsonPath("$[0].accuseLe").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/candidat/bons-commande/" + id1 + "/document").header("Authorization", jetonB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/candidat/bons-commande/" + id1 + "/document").header("Authorization", jetonA)).andExpect(status().isOk());
        mvc.perform(get("/api/bons-commande/" + id1).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.accuseLe").isNotEmpty());
        assertThat(notificationRepository.findAll()).extracting(Notification::getTypeNotif).contains("BON_COMMANDE_ACCUSE");
        // L'annulation, motivée, sort le bon des cumuls ; le bon de 6 000 000 passe alors.
        mvc.perform(post("/api/bons-commande/" + id1 + "/annulation").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(post("/api/bons-commande/" + id1 + "/annulation").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"Quantités erronées\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("ANNULE"));
        mvc.perform(post("/api/bons-commande/" + id1 + "/annulation").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"x\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_ANNULE"));
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content(bon.replace(String.format(ligne, 20), String.format(ligne, 30))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(2));
        // La consommation : le marché subséquent et son reste ; la valeur estimée (5 000 000) dépassée, signalée.
        mvc.perform(get(url).header("Authorization", tokenUgpm)).andExpect(status().isOk()).andExpect(jsonPath("$.cumulHt").value(6000000))
                .andExpect(jsonPath("$.subsequents[0].commande").value(6000000)).andExpect(jsonPath("$.subsequents[0].reste").value(3000000))
                .andExpect(jsonPath("$.bons.length()").value(2)).andExpect(jsonPath("$.alertes[0]").value("MONTANT_ESTIME_DEPASSE"));
        mvc.perform(get("/api/contrats-en-vigueur/" + idContrat).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.alertes").value(org.hamcrest.Matchers.hasItem("MONTANT_ESTIME_DEPASSE")));
        mvc.perform(get(url).header("Authorization", jetonM1)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MS-3 (16/10, marché à commandes, loi 2016-055 art. 30) : les bons à l'attributaire, au plus le maximum de l'année "
            + "contractuelle ; le minimum non atteint à l'approche de la fin d'année est signalé ; plus de bon après l'échéance")
    void bonsCommandeMarcheACommandes() throws Exception {
        String a = offre("C900000051", "1111222333", "BTP Alpha", 1, "INTACTE", null, "12500000");
        cnm.prs.entity.Attribution notifiee = new cnm.prs.entity.Attribution();
        notifiee.setIdDmc(idDmc);
        notifiee.setLot(1);
        notifiee.setEtat("NOTIFIE");
        notifiee.setIdOffreAttribuee(a);
        notifiee.setMontant(new java.math.BigDecimal("10000000"));       // ⚠️ 29/10 (Q1) : TTC / HT de l'offre = 20 %
        notifiee.setMontantTtc(new java.math.BigDecimal("12000000"));
        attributionRepository.save(notifiee);
        long idContrat = contrat("A_COMMANDE", aujourdhui.minusYears(1).plusDays(20), null, "8000000", "10000000");
        String url = "/api/contrats-en-vigueur/" + idContrat + "/bons-commande";
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content("{\"objet\":\"Ramettes\",\"delaiLivraison\":\"10 jours\","
                + "\"lignes\":[{\"idArticle\":999999,\"quantite\":1,\"prixUnitaire\":1}]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARTICLE_INCONNU"));
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content("{\"objet\":\"Ramettes\",\"lignes\":[{\"designation\":\"Ramette\","
                + "\"quantite\":1,\"prixUnitaire\":1}]}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHAMP_OBLIGATOIRE"));
        String bon = "{\"objet\":\"Ramettes de papier\",\"delaiLivraison\":\"10 jours\",\"lignes\":[{\"designation\":\"Ramette A4\",\"unite\":\"ramette\","
                + "\"quantite\":%s,\"prixUnitaire\":20000}]}";
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content(String.format(bon, 300))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.idOffre").value(a)).andExpect(jsonPath("$.idRemise").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.montantHt").value(6000000)).andExpect(jsonPath("$.lignes[0].prixDuBordereau").value(false))
                // ⚠️ 29/10 (« bons-reception », §B2) — la TVA de l'offre retenue, figée à l'émission.
                .andExpect(jsonPath("$.tauxTva").value(20)).andExpect(jsonPath("$.montantTva").value(1200000))
                .andExpect(jsonPath("$.montantTtc").value(7200000));
        // ⚠️ 29/10 (« marche-a-commandes-champs », D3) — le maximum en montant n'est plus un plafond : le bon passe, le dépassement est signalé.
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content(String.format(bon, 250))).andExpect(status().isCreated());
        // ⚠️ 29/10 (§B1) — le plafond en quantité d'un article du besoin (min 7, max 8) ; une ligne libre n'est pas bornée.
        List<Integer> articles = JsonPath.read(mvc.perform(get("/api/fiches-marche/" + idDmc + "/articles").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString(), "$[*].idArticle");
        cnm.prs.entity.FicheArticle article = ficheArticleRepository.findById(articles.get(0)).orElseThrow();
        article.setQuantiteMin(new java.math.BigDecimal("7"));
        article.setQuantiteMax(new java.math.BigDecimal("8"));
        ficheArticleRepository.save(article);
        String parArticle = "{\"objet\":\"Ordinateurs\",\"delaiLivraison\":\"10 jours\",\"lignes\":[{\"idArticle\":" + articles.get(0)
                + ",\"quantite\":%s,\"prixUnitaire\":1000}]}";
        String pdf = texteDuPdf(mvc.perform(get("/api/bons-commande/" + JsonPath.<Number>read(mvc.perform(post(url).header("Authorization", tokenPrmp)
                .contentType(JSON).content(String.format(parArticle, 6))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id").longValue() + "/document").header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsByteArray())
                .replaceAll("\\s+", " ");
        assertThat(pdf).contains("TVA (20 %)", "1 200", "Total toutes taxes comprises", "7 200", "toutes taxes comprises");
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content(String.format(parArticle, 3))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUANTITE_MAX_DEPASSEE")).andExpect(jsonPath("$.details.rang").value(1))
                .andExpect(jsonPath("$.details.quantiteMax").value(8)).andExpect(jsonPath("$.details.dejaCommande").value(6))
                .andExpect(jsonPath("$.details.demande").value(3));
        // ⚠️ 29/10 (D1-D3) — quantités et maximum sur la durée ; le contrat finit dans 19 jours : la quantité minimale (7) non atteinte.
        mvc.perform(get(url).header("Authorization", tokenPrmp)).andExpect(status().isOk()).andExpect(jsonPath("$.cumulAnnee").value(11006000))
                .andExpect(jsonPath("$.resteMaximum").value(-1006000)).andExpect(jsonPath("$.anneeFin").value(aujourdhui.plusDays(19).toString()))
                .andExpect(jsonPath("$.alertes").value(org.hamcrest.Matchers.contains("MAXIMUM_ESTIME_DEPASSE", "QUANTITE_MIN_NON_ATTEINTE")))
                .andExpect(jsonPath("$.cumulTtc").value(13207200)).andExpect(jsonPath("$.articles[0].commandeDuree").value(6))
                .andExpect(jsonPath("$.articles[0].resteQuantite").value(2)).andExpect(jsonPath("$.articles[1].resteQuantite").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/contrats-en-vigueur/" + idContrat).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.alertes").value(org.hamcrest.Matchers.hasItem("MAXIMUM_ESTIME_DEPASSE")));
        // ⚠️ 29/10 (D5) — reconductible tant que la durée totale ne dépasse pas trois ans (12 mois + 12 mois ≤ 36).
        cnm.prs.entity.ContratVigueur c = contratRepository.findById(idContrat).orElseThrow();
        c.setReconductible(true);
        c.setDureeMaxMois(36);
        contratRepository.save(c);
        mvc.perform(post("/api/contrats-en-vigueur/" + idContrat + "/reconduction").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"decision\":\"RECONDUIRE\",\"reference\":\"DEC-AC-01\",\"dateDecision\":\"" + aujourdhui + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dateFin").value(aujourdhui.plusDays(19).plusYears(1).toString()));
        c = contratRepository.findById(idContrat).orElseThrow();
        c.setDateFin(aujourdhui.minusDays(1));
        contratRepository.save(c);
        mvc.perform(post(url).header("Authorization", tokenPrmp).contentType(JSON).content(String.format(bon, 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRAT_ECHU"));
    }

    @Autowired private cnm.prs.repository.BonCommandeRepository bonRepository;
    @Autowired private cnm.prs.repository.ReceptionCommandeRepository receptionCommandeRepository;
    @Autowired private cnm.prs.repository.FicheArticleRepository ficheArticleRepository;

    @Test
    @DisplayName("⚠️ 2026-10-29 (« bons-reception », §B3, Q3) : la livraison et la réception par ligne ; reçu au plus le livré, montant "
            + "réceptionné sur les quantités reçues ; un reliquat laisse le bon RECU_PARTIEL, la dernière livraison prononce la réception")
    void receptionPartielle() throws Exception {
        String a = offre("C900000051", "1111222333", "BTP Alpha", 1, "INTACTE", null, "12500000");
        cnm.prs.entity.Attribution notifiee = new cnm.prs.entity.Attribution();
        notifiee.setIdDmc(idDmc);
        notifiee.setLot(1);
        notifiee.setEtat("NOTIFIE");
        notifiee.setIdOffreAttribuee(a);
        attributionRepository.save(notifiee);
        long idContrat = contrat("A_COMMANDE", aujourdhui.minusMonths(1), null, null, null);
        long idBon = JsonPath.<Number>read(mvc.perform(post("/api/contrats-en-vigueur/" + idContrat + "/bons-commande").header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"objet\":\"Fournitures\",\"delaiLivraison\":\"30 jours\",\"lignes\":[{\"designation\":\"Ramette A4\","
                        + "\"quantite\":100,\"prixUnitaire\":20000},{\"designation\":\"Toner\",\"quantite\":10,\"prixUnitaire\":50000}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id").longValue();
        String com = mvc.perform(put("/api/contrats-en-vigueur/" + idContrat + "/commission-reception").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"decision\":{\"reference\":\"DEC-REC-02\",\"date\":\"" + aujourdhui + "\"},\"membres\":[{\"nom\":\"Rabe\",\"prenom\":\"Hery\","
                        + "\"email\":\"p1@rec.mg\"}]}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String jetonR = bearer("p1@rec.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, JsonPath.read(com, "$.membres[0].idCompte"), null);
        String livraison = "/api/candidat/bons-commande/" + idBon + "/livraison";

        // Première livraison : 60 ramettes, pas de toner ; au-delà du reliquat, refusé.
        mvc.perform(post(livraison).header("Authorization", jetonA).contentType(JSON).content("{\"lignes\":[{\"rang\":1,\"quantiteLivree\":120}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUANTITE_LIVREE_INVALIDE"))
                .andExpect(jsonPath("$.details.reliquat").value(100));
        long r1 = JsonPath.<Number>read(mvc.perform(post(livraison).header("Authorization", jetonA).contentType(JSON)
                .content("{\"lignes\":[{\"rang\":1,\"quantiteLivree\":60}]}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.receptions[0].lignes[0].quantiteLivree").value(60)).andExpect(jsonPath("$.receptions[0].lignes[1].quantiteLivree").value(0))
                .andExpect(jsonPath("$.receptions[0].valeurLivree").value(1200000)).andReturn().getResponse().getContentAsString(), "$.receptions[0].id").longValue();
        String pv1 = "/api/receptions-commandes/" + r1;
        mvc.perform(post(pv1 + "/pv").header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"RECEPTION\",\"lignes\":[{\"rang\":1,"
                + "\"quantiteRecue\":70}]}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUANTITE_RECUE_INVALIDE"));
        mvc.perform(post(pv1 + "/pv").header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"REFACTION\",\"motif\":\"x\","
                + "\"montantRefaction\":1000000,\"lignes\":[{\"rang\":1,\"quantiteRecue\":50}]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REFACTION_INVALIDE"));
        mvc.perform(post(pv1 + "/pv").header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"RECEPTION\",\"lignes\":[{\"rang\":1,"
                + "\"quantiteRecue\":50}]}")).andExpect(status().isOk()).andExpect(jsonPath("$.montantReceptionne").value(1000000))
                .andExpect(jsonPath("$.lignes[0].quantiteRecue").value(50));
        String texte = texteDuPdf(mvc.perform(get(pv1 + "/pv").header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(texte).contains("Commandé", "Livré", "Reçu", "Reliquat", "Montant réceptionné : 1 000 000");
        for (String j : List.of(tokenPrmp, jetonR)) {
            mvc.perform(post(pv1 + "/signature").header("Authorization", j)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/receptions-commandes/bons/" + idBon).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etatReception").value("RECU_PARTIEL")).andExpect(jsonPath("$.lignes[0].commande").value(100))
                .andExpect(jsonPath("$.lignes[0].livre").value(60)).andExpect(jsonPath("$.lignes[0].recu").value(50))
                .andExpect(jsonPath("$.lignes[0].reliquat").value(50)).andExpect(jsonPath("$.lignes[1].reliquat").value(10));

        // Seconde livraison, sans lignes : tout le reliquat (50 ramettes, 10 toners) ; reçue, la réception provisoire du bon est prononcée.
        long r2 = JsonPath.<Number>read(mvc.perform(post(livraison).header("Authorization", jetonA).contentType(JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.receptions[1].lignes[0].quantiteLivree").value(50))
                .andExpect(jsonPath("$.receptions[1].lignes[1].quantiteLivree").value(10)).andReturn().getResponse().getContentAsString(),
                "$.receptions[1].id").longValue();
        mvc.perform(post("/api/receptions-commandes/" + r2 + "/pv").header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"RECEPTION\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.montantReceptionne").value(1500000));
        for (String j : List.of(tokenPrmp, jetonR)) {
            mvc.perform(post("/api/receptions-commandes/" + r2 + "/signature").header("Authorization", j)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/receptions-commandes/bons/" + idBon).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etatReception").value(org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.is("RECU_PROVISOIRE"),
                        org.hamcrest.Matchers.is("RECU_DEFINITIF")))).andExpect(jsonPath("$.lignes[0].reliquat").value(0))
                .andExpect(jsonPath("$.lignes[1].reliquat").value(0));
        mvc.perform(post(livraison).header("Authorization", jetonA).contentType(JSON).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEJA_RECU"));
    }

    @Test
    @DisplayName("MS-4 (16/10, réception, V127, loi 2016-055 art. 13) : la commission de réception désignée par la PRMP ; la livraison "
            + "déclarée par le titulaire ; le PV établi par le président (retard et pénalité indicative), signé par chacun ; sans garantie, la "
            + "réception provisoire vaut définitive")
    void receptionCommandes() throws Exception {
        String a = offre("C900000051", "1111222333", "BTP Alpha", 1, "INTACTE", null, "12500000");
        cnm.prs.entity.Attribution notifiee = new cnm.prs.entity.Attribution();
        notifiee.setIdDmc(idDmc);
        notifiee.setLot(1);
        notifiee.setEtat("NOTIFIE");
        notifiee.setIdOffreAttribuee(a);
        attributionRepository.save(notifiee);
        long idContrat = contrat("A_COMMANDE", aujourdhui.minusMonths(1), null, null, null);
        String bon = mvc.perform(post("/api/contrats-en-vigueur/" + idContrat + "/bons-commande").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"objet\":\"Ramettes de papier\",\"delaiLivraison\":\"10 jours\",\"lignes\":[{\"designation\":\"Ramette A4\",\"quantite\":100,"
                        + "\"prixUnitaire\":20000}]}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idBon = JsonPath.<Number>read(bon, "$.id").longValue();
        cnm.prs.entity.BonCommande b = bonRepository.findById(idBon).orElseThrow();
        b.setEmisLe(LocalDateTime.now().minusDays(20));   // notifié il y a 20 jours, délai de 10 jours : 10 jours de retard
        bonRepository.save(b);

        // La commission (Q1) : décision de la PRMP, saisie par l'UGPM ; la PRMP préside.
        String url = "/api/contrats-en-vigueur/" + idContrat + "/commission-reception";
        String membres = "\"membres\":[{\"nom\":\"Rabe\",\"prenom\":\"Hery\",\"email\":\"r1@rec.mg\",\"fonction\":\"Magasinier\"%s},"
                + "{\"nom\":\"Rasoa\",\"prenom\":\"Nirina\",\"email\":\"r2@rec.mg\"}]";
        String decision = "\"decision\":{\"reference\":\"DEC-REC-01\",\"date\":\"" + aujourdhui + "\"}";
        mvc.perform(put(url).header("Authorization", tokenUgpm).contentType(JSON).content("{" + decision + "," + String.format(membres, ",\"president\":true") + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(url).header("Authorization", jetonA).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        String com = mvc.perform(put(url).header("Authorization", tokenUgpm).contentType(JSON).content("{" + decision + "," + String.format(membres, "") + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.presideeParPrmp").value(true)).andExpect(jsonPath("$.membres.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        List<String> idComptes = JsonPath.read(com, "$.membres[*].idCompte");
        String jetonR1 = bearer("r1@rec.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, idComptes.get(0), null);
        String jetonR2 = bearer("r2@rec.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, idComptes.get(1), null);
        assertThat(notificationRepository.findAll()).filteredOn(n -> "COMMISSION_RECEPTION".equals(n.getTypeNotif())).hasSize(2);

        // La livraison, déclarée par le titulaire ; une seule réception en cours.
        String livraison = "/api/candidat/bons-commande/" + idBon + "/livraison";
        String livree = mvc.perform(post(livraison).header("Authorization", jetonA).contentType(JSON).content("{\"observations\":\"Livré au magasin\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.etatReception").value("EN_RECEPTION")).andReturn().getResponse().getContentAsString();
        long idReception = JsonPath.<Number>read(livree, "$.receptions[0].id").longValue();
        mvc.perform(post(livraison).header("Authorization", jetonA).contentType(JSON).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECEPTION_EN_COURS"));
        mvc.perform(get("/api/receptions-commandes/miennes").header("Authorization", jetonR1)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reception.id").value(idReception)).andExpect(jsonPath("$[0].aSigner").value(false));

        // Le PV : par le président (la PRMP) ; le retard et la pénalité indicative (2 000 000 × 1 ‰ × 10 jours).
        String pv = "/api/receptions-commandes/" + idReception;
        mvc.perform(post(pv + "/pv").header("Authorization", jetonR1).contentType(JSON).content("{\"decision\":\"RECEPTION\"}")).andExpect(status().isForbidden());
        mvc.perform(post(pv + "/pv").header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"REJET\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(post(pv + "/pv").header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"RECEPTION\",\"reserves\":\"Deux ramettes abîmées\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("PV_ETABLI")).andExpect(jsonPath("$.joursRetard").value(10))
                .andExpect(jsonPath("$.penaliteIndicative").value(20000)).andExpect(jsonPath("$.attendus.length()").value(3));
        String texte = texteDuPdf(mvc.perform(get(pv + "/pv").header("Authorization", jetonR2)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(texte).contains("PROCÈS-VERBAL DE RÉCEPTION PROVISOIRE", "décision n° DEC-REC-01", "Retard constaté : 10 jour(s)", "20 000",
                "Réserves : Deux ramettes abîmées", "en attente de signature");
        mvc.perform(get("/api/candidat/bons-commande/" + idBon + "/receptions/" + idReception + "/pv").header("Authorization", jetonA))
                .andExpect(status().isNotFound());   // pas avant la signature

        // Les signatures : chacun la sienne ; la dernière prononce la réception.
        mvc.perform(post(pv + "/signature").header("Authorization", jetonR1)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("PV_ETABLI"));
        mvc.perform(post(pv + "/signature").header("Authorization", jetonR1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_SIGNE"));
        mvc.perform(post(pv + "/signature").header("Authorization", tokenUgpm)).andExpect(status().isForbidden());
        mvc.perform(post(pv + "/signature").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(post(pv + "/signature").header("Authorization", jetonR2)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("SIGNE"))
                .andExpect(jsonPath("$.montantReceptionne").value(2000000));
        assertThat(notificationRepository.findAll()).filteredOn(n -> "RECEPTION_PRONONCEE".equals(n.getTypeNotif()))
                .extracting(Notification::getDestinataireRef).contains("C900000051");
        // Le délai de garantie de la fiche (B09-DG-01) court : réception provisoire seulement.
        String recu = mvc.perform(get("/api/candidat/bons-commande/" + idBon + "/receptions").header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etatReception").value("RECU_PROVISOIRE")).andReturn().getResponse().getContentAsString();
        int garantie = JsonPath.<Integer>read(recu, "$.garantieMois");
        assertThat(JsonPath.<String>read(recu, "$.garantieJusquau")).isEqualTo(aujourdhui.plusMonths(garantie).toString());
        mvc.perform(get("/api/candidat/bons-commande/" + idBon + "/receptions/" + idReception + "/pv").header("Authorization", jetonA))
                .andExpect(status().isOk());
        mvc.perform(post(livraison).header("Authorization", jetonA).contentType(JSON).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEJA_RECU"));
        String definitive = "/api/receptions-commandes/bons/" + idBon + "/definitive";
        mvc.perform(post(definitive).header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GARANTIE_EN_COURS"));
        mvc.perform(get("/api/receptions-commandes/bons/" + idBon).header("Authorization", jetonM1)).andExpect(status().isForbidden());

        // La garantie écoulée : l'alerte, puis la réception définitive, établie et signée.
        cnm.prs.entity.ReceptionCommande provisoire = receptionCommandeRepository.findById(idReception).orElseThrow();
        provisoire.setDateReception(aujourdhui.minusMonths(garantie).minusDays(1));
        receptionCommandeRepository.save(provisoire);
        mvc.perform(get("/api/receptions-commandes/bons/" + idBon).header("Authorization", jetonR1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.alertes[0]").value("RECEPTION_DEFINITIVE_A_PRONONCER"));
        mvc.perform(post(definitive).header("Authorization", jetonR1)).andExpect(status().isForbidden());
        long idDefinitive = JsonPath.<Number>read(mvc.perform(post(definitive).header("Authorization", tokenPrmp)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.etatReception").value("EN_RECEPTION_DEFINITIVE")).andReturn().getResponse().getContentAsString(),
                "$.receptions[1].id").longValue();
        String pvd = "/api/receptions-commandes/" + idDefinitive;
        mvc.perform(post(pvd + "/pv").header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"REJET\",\"motif\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DECISION_INVALIDE"));
        mvc.perform(post(pvd + "/pv").header("Authorization", tokenPrmp).contentType(JSON).content("{\"decision\":\"RECEPTION\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.penaliteIndicative").value(org.hamcrest.Matchers.nullValue()));
        for (String j : List.of(tokenPrmp, jetonR1, jetonR2)) {
            mvc.perform(post(pvd + "/signature").header("Authorization", j)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/receptions-commandes/bons/" + idBon).header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etatReception").value("RECU_DEFINITIF")).andExpect(jsonPath("$.alertes.length()").value(0));
    }

    @Test
    @DisplayName("Tranche MT (15/10, contrat-cadre multi-attributaire, décret 2019-1064 art. 14-17) : la post-qualification décide chaque "
            + "offre classée sans s'arrêter à la première qualifiée ; un seul titulaire qualifié est proposé avec le constat de l'art. 17 ; "
            + "après réouverture, la proposition retient tous les qualifiés dans l'ordre du classement")
    void plusieursTitulaires() throws Exception {
        String a = offre("C900000051", "1111222333", "BTP Alpha", 1, "INTACTE", null, "12500000");
        String b = offre("C900000052", "4444555666", "BTP Beta", 2, "INTACTE", null, "11900000");
        String c = offre("C900000053", "7777888999", "BTP Gamma", 3, "INTACTE", null, "9000000");
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
        arreter("EVALUATION").andExpect(status().isOk());
        for (String o : List.of(a, b, c)) {
            anormale(o, "{\"suspectee\":false}").andExpect(status().isOk());
        }
        arreter("ANORMALES").andExpect(status().isOk());
        // La fiche validée devient un contrat-cadre multi-attributaire (seul le cadrage est lu par l'évaluation).
        cnm.prs.entity.FicheMarche validee = ficheMarcheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                .filter(f -> "VALIDEE".equals(f.getStatut())).reduce((x, y) -> y).orElseThrow();
        String cadrage = validee.getCadrage() == null || validee.getCadrage().isBlank() ? "{}" : validee.getCadrage().trim();
        validee.setCadrage("{\"attributaires\":\"MULTI\"" + (cadrage.equals("{}") ? "}" : "," + cadrage.substring(1)));
        ficheMarcheRepository.saveAndFlush(validee);

        // Classement : C (9 M), B (11,9 M), A (12,5 M). C qualifiée : l'étape ne s'arrête pas, B attend sa décision.
        String q = mvc.perform(get(base + "/lots/1/qualification").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(q, "$.idOffre")).isEqualTo(c);
        List<String> codes = JsonPath.read(q, "$.criteres[*].code");
        String echec = criteres(codes, "JURIDIQUE").replace("\"NON_SATISFAIT\"}", "\"NON_SATISFAIT\",\"motif\":\"Attestation fiscale périmée\"}")
                + ",\"decision\":\"NON_QUALIFIE\",\"motif\":\"Situation fiscale non régulière\",\"clause\":\"IC 6.3\"}";
        qualifier(c, criteres(codes, null) + ",\"decision\":\"QUALIFIE\"}").andExpect(status().isOk());
        arreter("QUALIFICATION").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ETAPE_INCOMPLETE"))
                .andExpect(jsonPath("$.details.offres[0]").value(2));
        mvc.perform(get(base + "/lots/1/qualification").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idOffre").value(b));
        qualifier(b, echec).andExpect(status().isOk());
        qualifier(a, echec).andExpect(status().isOk());
        String seul = arreter("QUALIFICATION").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(seul, "$.lots[0].proposition.idOffre")).isEqualTo(c);
        assertThat(JsonPath.<Boolean>read(seul, "$.lots[0].proposition.infructueux")).isFalse();
        assertThat(JsonPath.<Boolean>read(seul, "$.lots[0].proposition.seulTitulaireQualifie")).isTrue();   // décret, art. 17
        assertThat(JsonPath.<List<String>>read(seul, "$.lots[0].proposition.titulaires[*].idOffre")).containsExactly(c);

        // Réouverture : A finalement qualifiée — deux titulaires, dans l'ordre du classement.
        mvc.perform(post(base + "/lots/1/etapes/QUALIFICATION/rouvrir").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"motif\":\"Attestation fiscale régularisée\"}")).andExpect(status().isOk());
        qualifier(a, criteres(codes, null) + ",\"decision\":\"QUALIFIE\"}").andExpect(status().isOk());
        String deux = arreter("QUALIFICATION").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(deux, "$.lots[0].proposition.titulaires[*].idOffre")).containsExactly(c, a);
        assertThat(JsonPath.<List<Integer>>read(deux, "$.lots[0].proposition.titulaires[*].rang")).containsExactly(1, 3);
        assertThat(JsonPath.<List<String>>read(deux, "$.lots[0].proposition.titulaires[*].candidat")).containsExactly("BTP Gamma", "BTP Alpha");
        assertThat(JsonPath.<Boolean>read(deux, "$.lots[0].proposition.seulTitulaireQualifie")).isFalse();
        assertThat(JsonPath.<String>read(deux, "$.lots[0].proposition.idOffre")).isEqualTo(c);   // la tête reste le premier classé
        // Le rapport propose les titulaires, rang par rang.
        mvc.perform(post(base + "/rapport").header("Authorization", tokenVer).contentType(JSON).content("{}")).andExpect(status().isOk());
        String pdf = texteDuPdf(mvc.perform(get(base + "/rapport").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(pdf).contains("La commission propose de retenir comme titulaires du contrat-cadre les 2 candidat(s) qualifié(s)",
                "– rang 1 : BTP Gamma (offre n° 3)", "– rang 3 : BTP Alpha (offre n° 1)")
                .doesNotContain("La commission propose d'attribuer le marché à", "qu'un seul titulaire");

        // ⚠️ MT-b1 (V122) — l'attribution à plusieurs titulaires : le rapport signé, le dossier de marché et son projet de contrat-cadre.
        mvc.perform(post(base + "/rapport/signer").header("Authorization", jetonM1).contentType(JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(post(base + "/rapport/empechement").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"im\":\"" + comptes.get(1) + "\",\"motif\":\"En mission\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("CLOSE"));
        typeDossierRepository.save(new cnm.prs.entity.TypeDossier("DDM", "Dossier de Marché"));
        sousTypeDossierRepository.save(new cnm.prs.entity.SousTypeDossier("MAOO", "Marché sur Appel d'Offres Ouvert", "DDM"));
        for (String[] t : List.of(new String[] { "Projet de marché signé", "PROJET_MARCHE" }, new String[] { "Cahier des charges", "CAHIER_CHARGES" },
                new String[] { "Devis estimatif détaillé", "DEVIS_ESTIMATIF" }, new String[] { "Procès-verbal d'ouverture des offres", "PV_OUVERTURE" },
                new String[] { "Rapport d'analyse des offres", "RAPPORT_ANALYSE" })) {
            cnm.prs.entity.TypePieceJointe tp = typePieceJointeRepository.findById(seedTypePiece(t[0], true, "DDM", 1)).orElseThrow();
            tp.setCode(t[1]);
            typePieceJointeRepository.save(tp);
        }
        // ⚠️ 2026-10-19 (« dossier-marche-contrat-cadre ») — le dossier de mise en concurrence en DAOO-CC, au PV signé ; MAOO-CC et sa
        // liste (les trois pièces internes en PAPIER, comme V102) ; le bordereau estimatif que la fiche a produit.
        cnm.prs.entity.SousTypeDossier maooCc = new cnm.prs.entity.SousTypeDossier("MAOO-CC", "Marché sur appel d'offres ouvert — contrat-cadre", "DDM");
        maooCc.setGrilleBase("MAOO");
        sousTypeDossierRepository.save(maooCc);
        int ordre = 1;
        for (String[] t : List.of(new String[] { "FICHE_PRESENTATION", "PAPIER" }, new String[] { "PV_CNM_DMC", "PAPIER" },
                new String[] { "PV_OUVERTURE", null }, new String[] { "RAPPORT_ANALYSE", null }, new String[] { "PV_VALIDATION_CAO", "PAPIER" },
                new String[] { "DEVIS_ESTIMATIF", null }, new String[] { "PROJET_MARCHE", null }, new String[] { "AUTRE_DOCUMENT", null })) {
            cnm.prs.entity.TypePieceJointe tp = typePieceJointeRepository.findFirstByCode(t[0]).orElseGet(() -> {
                cnm.prs.entity.TypePieceJointe n = typePieceJointeRepository.findById(seedTypePiece(t[0], false, null, 0)).orElseThrow();
                n.setCode(t[0]);
                return typePieceJointeRepository.save(n);
            });
            cnm.prs.entity.PieceSousType ps = new cnm.prs.entity.PieceSousType(tp.getIdTypePiece(), "MAOO-CC", !"AUTRE_DOCUMENT".equals(t[0]),
                    ordre++, null, null);
            ps.setCondition(t[1]);
            pieceSousTypeRepository.save(ps);
        }
        Dossier mec = dossierLoc(9960, "CLOTURE", "ANT", "PRMP001");
        mec.setIdTypeDossier("DMC");
        mec.setIdSousType("DAOO-CC");
        mec.setIdDmc(idDmc);
        dossierRepository.save(mec);
        receptionRepository.save(reception(9960, 9960, "CTRCC1", true));
        dispatchRepository.save(dispatch(9960, 9960, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9960, 9960, "CTRMEM"));
        seedPvSigne(9960, 9960);
        String estimatif = documentFicheMarcheRepository.findByIdFicheOrderByIdDocumentAsc(validee.getIdFiche()).stream()
                .filter(x -> "BORDEREAU_ESTIMATIF".equals(x.getType()) && "pdf".equals(x.getExtension())).findFirst().orElseThrow().getNomFichier();
        String att = base.replace("/evaluation", "/attribution");
        String creation = mvc.perform(post(att + "/lots/1/dossier").header("Authorization", tokenPrmp)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lots[0].dossierMarche.sousType").value("MAOO-CC"))
                .andReturn().getResponse().getContentAsString();
        int idDossier = JsonPath.read(creation, "$.lots[0].dossierMarche.idDossier");
        dossierMarcheContratCadre(idDossier, estimatif);
        String projet = texteDuPdf(mvc.perform(get(att + "/lots/1/projet").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(projet).contains("PROJET DE CONTRAT-CADRE", "et les 2 titulaire(s) suivant(s)", "BTP Gamma, NIF 7777888999", "BTP Alpha, NIF 1111222333",
                "BTP Gamma : offre indicative de 9 000 000 Ariary hors taxes", "décret n° 2019-1064, art. 16", "Pour BTP Gamma :", "Pour BTP Alpha :")
                .doesNotContain("PROJET DE MARCHÉ");
        receptionRepository.save(reception(9950, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(9950, 9950, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9950, 9950, "CTRMEM"));
        seedPvSigne(9950, 9950);

        // La PRMP retient parmi les proposés seulement (art. 14) ; une partie seulement se motive ; sans corps : tous les proposés.
        mvc.perform(post(att + "/lots/1/attribuer").header("Authorization", tokenPrmp).contentType(JSON).content("{\"titulaires\":[\"" + b + "\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFRE_NON_PROPOSEE"))
                .andExpect(jsonPath("$.details.offres[0]").value(b));
        mvc.perform(post(att + "/lots/1/attribuer").header("Authorization", tokenPrmp).contentType(JSON).content("{\"titulaires\":[\"" + c + "\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(post(att + "/lots/1/attribuer").header("Authorization", tokenPrmp).contentType(JSON).content("{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].etat").value("ATTRIBUE")).andExpect(jsonPath("$.lots[0].attributaire.idOffre").value(c))
                .andExpect(jsonPath("$.lots[0].titulaires.length()").value(2))
                .andExpect(jsonPath("$.lots[0].titulaires[0].idOffre").value(c)).andExpect(jsonPath("$.lots[0].titulaires[1].idOffre").value(a))
                .andExpect(jsonPath("$.lots[0].titulaires[1].montant").value(12500000));

        // L'information : une lettre d'attribution à chaque titulaire, la liste des titulaires au candidat non retenu.
        String info = mvc.perform(post(att + "/lots/1/informer").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateAffichage\":\"" + aujourdhui + "\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(info, "$.lots[0].information.lettres[?(@.type == 'ATTRIBUTION')].idOffre")).containsExactlyInAnyOrder(a, c);
        assertThat(JsonPath.<List<String>>read(info, "$.lots[0].information.lettres[?(@.type == 'NON_RETENU')].idOffre")).containsExactly(b);
        String lettreA = texteDuPdf(mvc.perform(get(att + "/lots/1/lettres/" + a).header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(lettreA).contains("LETTRE D'ATTRIBUTION DU CONTRAT-CADRE", "Le contrat-cadre compte 2 titulaire(s) : BTP Gamma (offre n° 3) ; "
                + "BTP Alpha (offre n° 1)", "offre indicative est de 12 500 000 Ariary hors taxes", "par chacun des titulaires");
        String lettreB = texteDuPdf(mvc.perform(get("/api/candidat/offres/" + b + "/resultat/lettre").header("Authorization", jetonB))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(lettreB).contains("LETTRE D'INFORMATION DU REJET DE L'OFFRE", "Le contrat-cadre est attribué aux 2 titulaire(s) suivant(s) : "
                + "BTP Gamma (offre n° 3) ; BTP Alpha (offre n° 1)", "décret n° 2019-1064, art. 15");
        mvc.perform(get("/api/candidat/offres/" + a + "/resultat").header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.retenu").value(true)).andExpect(jsonPath("$.attributaire").value("BTP Gamma ; BTP Alpha"))
                .andExpect(jsonPath("$.montant").value(12500000));
        mvc.perform(get("/api/candidat/offres/" + b + "/resultat").header("Authorization", jetonB)).andExpect(status().isOk())
                .andExpect(jsonPath("$.retenu").value(false)).andExpect(jsonPath("$.attributaire").value("BTP Gamma ; BTP Alpha"))
                .andExpect(jsonPath("$.montant").value(org.hamcrest.Matchers.nullValue()));

        // ⚠️ MT-b2 (V123, réponse 1 du pilote) — les pièces de CHAQUE titulaire : C les produit conformes, A une fiscale non conforme.
        String jetonC = bearer("c@eval.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000053", null);
        MockMultipartFile piece = new MockMultipartFile("fichier", "piece.pdf", "application/pdf", "%PDF-1.4 x".getBytes(StandardCharsets.ISO_8859_1));
        for (String[] t : List.of(new String[] {c, jetonC}, new String[] {a, jetonA})) {
            for (String type : List.of("FISCALE", "SOCIALE", "ACTE_ENGAGEMENT_ORIGINAL")) {
                mvc.perform(multipart("/api/candidat/offres/" + t[0] + "/pieces-attributaire").file(piece).param("type", type)
                        .param("dateDelivrance", aujourdhui.toString()).header("Authorization", t[1])).andExpect(status().isCreated());
            }
        }
        mvc.perform(get("/api/candidat/offres/" + a + "/pieces-attributaire").header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pieces.length()").value(3));   // les siennes seulement
        String suivi = mvc.perform(get(att).header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(suivi, "$.lots[0].suiviTitulaires[*].idOffre")).containsExactly(c, a);
        for (Number id : JsonPath.<List<Number>>read(suivi, "$.lots[0].suiviTitulaires[0].pieces.pieces[*].id")) {
            mvc.perform(post(att + "/lots/1/pieces/" + id.longValue() + "/verifier").header("Authorization", tokenPrmp).contentType(JSON)
                    .content("{\"conforme\":true}")).andExpect(status().isOk());
        }
        Number fiscaleA = JsonPath.<List<Number>>read(suivi, "$.lots[0].suiviTitulaires[1].pieces.pieces[?(@.type == 'FISCALE')].id").get(0);
        mvc.perform(post(att + "/lots/1/pieces/" + fiscaleA.longValue() + "/verifier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"conforme\":false,\"motif\":\"Attestation illisible\"}")).andExpect(status().isOk());
        assertThat(notificationRepository.findAll()).filteredOn(n -> "C900000051".equals(n.getDestinataireRef()))
                .extracting(Notification::getTypeNotif).contains("PIECE_ATTRIBUTAIRE_VERIFIEE");

        // Le délai d'attente écoulé et l'échéance des pièces passée : A bloque la signature ; deux titulaires, pas d'infructuosité.
        cnm.prs.entity.Attribution attr = attributionRepository.findById(new cnm.prs.entity.Attribution.Cle(idDmc, 1)).orElseThrow();
        attr.setInformeLe(attr.getInformeLe().minusDays(16));
        attr.setDateAffichage(attr.getDateAffichage().minusDays(16));
        attributionRepository.save(attr);
        mvc.perform(multipart(att + "/lots/1/signature").file(piece).param("dateSignature", aujourdhui.toString()).header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PIECES_NON_CONFORMES"))
                .andExpect(jsonPath("$.details.offres[0]").value(1));
        String infructueux = "{\"motif\":\"Un seul titulaire\",\"decision\":{\"reference\":\"D-1\",\"date\":\"" + aujourdhui + "\"}}";
        mvc.perform(post(att + "/lots/1/infructueux").header("Authorization", tokenPrmp).contentType(JSON).content(infructueux))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_ATTRIBUE"));

        // Le retrait d'un titulaire (réponse 2) : nommé, sans pièces conformes ; le contrat-cadre continue avec C.
        mvc.perform(post(att + "/lots/1/retirer").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"Pièce fiscale non conforme\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TITULAIRE_OBLIGATOIRE"));
        mvc.perform(post(att + "/lots/1/retirer").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"x\",\"idOffre\":\"" + c + "\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PIECES_CONFORMES"));
        mvc.perform(post(att + "/lots/1/retirer").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"Pièce fiscale non conforme\",\"idOffre\":\"" + a + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].etat").value(org.hamcrest.Matchers.not("RETIRE")))
                .andExpect(jsonPath("$.lots[0].suiviTitulaires[1].retrait.motif").value("Pièce fiscale non conforme"))
                .andExpect(jsonPath("$.lots[0].attributaire.idOffre").value(c));
        mvc.perform(get("/api/candidat/offres/" + a + "/resultat").header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.retire").value(true)).andExpect(jsonPath("$.attributaire").value("BTP Gamma"));
        // Un seul titulaire reste : l'infructuosité redevient possible (art. 17) — le geste va jusqu'à sa validation (ici, le motif).
        mvc.perform(post(att + "/lots/1/infructueux").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));

        // La signature par la PRMP et le titulaire restant, l'approbation, l'enregistrement, la notification et l'avis.
        mvc.perform(multipart(att + "/lots/1/signature").file(piece).param("dateSignature", aujourdhui.toString()).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lots[0].etat").value("SIGNE"));
        mvc.perform(multipart(att + "/lots/1/approbation").file(piece).param("autorite", "Le Ministre").param("dateApprobation", aujourdhui.toString())
                .header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(multipart(att + "/lots/1/enregistrement").file(piece).param("dateEnregistrement", aujourdhui.toString())
                .header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(post(att + "/lots/1/notification").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateNotification\":\"" + aujourdhui + "\"}")).andExpect(status().isOk());
        assertThat(notificationRepository.findAll()).filteredOn(n -> "C900000053".equals(n.getDestinataireRef()))
                .extracting(Notification::getTypeNotif).contains("MARCHE_NOTIFIE");
        assertThat(notificationRepository.findAll()).filteredOn(n -> "C900000051".equals(n.getDestinataireRef()))
                .extracting(Notification::getTypeNotif).doesNotContain("MARCHE_NOTIFIE");
        mvc.perform(get("/api/candidat/offres/" + c + "/resultat").header("Authorization", jetonC)).andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationRecueLe").isNotEmpty()).andExpect(jsonPath("$.marcheDisponible").value(true));
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].suiviTitulaires[0].notificationRecueLe").isNotEmpty())
                .andExpect(jsonPath("$.lots[0].notification.recueLe").isNotEmpty());
        mvc.perform(post(att + "/lots/1/avis").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"datePublication\":\"" + aujourdhui + "\"}")).andExpect(status().isOk());
        String avis = texteDuPdf(mvc.perform(get(att + "/lots/1/avis").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(avis).contains("Contrat-cadre attribué à 1 titulaire(s)", "BTP Gamma, NIF 7777888999 : offre indicative de 9 000 000 Ariary",
                "décret n° 2019-1064, art. 16").doesNotContain("BTP Alpha", "Attributaire :");
    }

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
    /**
     * ⚠️ 2026-10-19 (demande front « dossier-marche-contrat-cadre ») — le dossier de marché d'un contrat-cadre : la procédure servie
     * (B2), les pièces jointes d'office marquées et gardées (B3), le devis estimatif de la fiche et les pièces internes fournies par
     * l'application (B4), la fiche de présentation du marché.
     */
    private void dossierMarcheContratCadre(int idDossier, String estimatif) throws Exception {
        mvc.perform(get("/api/dossiers/" + idDossier).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idSousType").value("MAOO-CC")).andExpect(jsonPath("$.idDmc").doesNotExist())
                .andExpect(jsonPath("$.idDossierParent").doesNotExist())
                .andExpect(jsonPath("$.procedure.idDmc").value(idDmc)).andExpect(jsonPath("$.procedure.lot").value(1))
                .andExpect(jsonPath("$.procedure.idDossierMiseEnConcurrence").value(9960))
                .andExpect(jsonPath("$.procedure.refeDossierMiseEnConcurrence").value("DOS-9960"))
                // ⚠️ 2026-10-19 (« dossier-marche-lecture », §B3) — l'objet ; la ligne n'a pas plusieurs lots : pas d'intitulé.
                .andExpect(jsonPath("$.procedure.designationMarche").isNotEmpty())
                .andExpect(jsonPath("$.procedure.intituleLot").doesNotExist());
        // §B1, Q1 — contrat-cadre à plusieurs titulaires : aucun montant initial.
        mvc.perform(get("/api/dossiers/" + idDossier + "/actes-gestion").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.montantInitialHt").doesNotExist()).andExpect(jsonPath("$.sourceMontantInitial").doesNotExist());
        String pieces = mvc.perform(get("/api/piece-jointe-dossiers").param("dossier", String.valueOf(idDossier)).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(pieces, "$[*].produitePar")).isNotEmpty().containsOnly("ATTRIBUTION");
        assertThat(JsonPath.<List<String>>read(pieces, "$[?(@.nomFichier=='" + estimatif + "')].nomFichier")).hasSize(1);
        int idEstimatif = JsonPath.<List<Integer>>read(pieces, "$[?(@.nomFichier=='" + estimatif + "')].idPiece").get(0);
        int idTypeDevis = JsonPath.<List<Integer>>read(pieces, "$[?(@.nomFichier=='" + estimatif + "')].idTypePiece").get(0);
        int idTypeAutre = typePieceJointeRepository.findFirstByCode("AUTRE_DOCUMENT").orElseThrow().getIdTypePiece();
        assertThat(JsonPath.<List<Integer>>read(pieces, "$[?(@.nomFichier =~ /bordereau-offre-.*/)].idTypePiece")).doesNotContain(idTypeDevis);
        // Ni retrait ni remplacement à la main, Administrateur compris ; « tout autre document » reste libre.
        mvc.perform(delete("/api/piece-jointe-dossiers/" + idEstimatif).header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIECE_PRODUITE_PAR_APPLICATION"));
        mvc.perform(delete("/api/piece-jointe-dossiers/" + idEstimatif).header("Authorization", tokenAdmin)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIECE_PRODUITE_PAR_APPLICATION"));
        org.springframework.mock.web.MockMultipartFile pdf = new org.springframework.mock.web.MockMultipartFile("fichier", "autre.pdf",
                "application/pdf", "%PDF-1.4 autre".getBytes(StandardCharsets.ISO_8859_1));
        mvc.perform(multipart("/api/piece-jointe-dossiers").file(pdf).file(data(idDossier, idTypeDevis)).header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIECE_PRODUITE_PAR_APPLICATION"));
        mvc.perform(multipart("/api/piece-jointe-dossiers").file(pdf).file(data(idDossier, idTypeAutre)).header("Authorization", tokenPrmp)).andExpect(status().isCreated());
        // B4 — les pièces internes, fournies par l'application.
        String exigees = mvc.perform(get("/api/dossiers/" + idDossier + "/pieces-exigees").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(exigees, "$[?(@.fourniePar=='APPLICATION')].code"))
                .containsExactlyInAnyOrder("FICHE_PRESENTATION", "PV_CNM_DMC", "PV_VALIDATION_CAO");
        // ⚠️ 2026-10-19 (« dossier-marche-lecture », Q4) — le PV d'un dossier de mise en concurrence n'a pas de modèle : pas de lien, idPv.
        assertThat(JsonPath.<List<Object>>read(exigees, "$[?(@.code=='PV_CNM_DMC')].lien")).containsOnlyNulls();
        int idPvMec = JsonPath.<List<Integer>>read(exigees, "$[?(@.code=='PV_CNM_DMC')].idPv").get(0);
        // Q3 — la PRMP du dossier de mise en concurrence (sans PPM) lit son PV signé ; le document reste absent (404, pas 403).
        mvc.perform(get("/api/pv-examens/" + idPvMec).header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(get("/api/pv-examens/" + idPvMec + "/document").header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pv-examens/" + idPvMec).header("Authorization", bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT")))
                .andExpect(status().isForbidden());
        assertThat(JsonPath.<List<String>>read(exigees, "$[?(@.code=='PV_VALIDATION_CAO')].lien").get(0)).startsWith("/api/piece-jointe-dossiers/");
        // La fiche de présentation du marché : ⚠️ 2026-10-20 (« fiche-presentation-marche », §B1) la sienne — l'attribution (un titulaire
        // par ligne, l'écart à l'estimation), la mise en concurrence, la séance, l'évaluation ; plus le calendrier du plan ni la cohérence.
        // ⚠️ 2026-10-20 (« liste-dossier-marche », §B2) — plusieurs titulaires proposés : le rang 1 et leur nombre.
        mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].attributions[0].propose.candidat").value(hasItem("BTP Gamma")))
                .andExpect(jsonPath("$[?(@.idDmc==" + idDmc + ")].attributions[0].propose.nbTitulaires").value(hasItem(2)));
        // ⚠️ 2026-10-20 (« fiche-presentation-marche-ecarts », §B1) — le créateur est tracé par sa référence d'acteur (en recette, IMP001
        // pour le login PRMP001) : son nom se lit par la référence, pas comme un login.
        cnm.prs.entity.Prmp autre = prmp("IMPX01", "ANT");
        autre.setNomPrmp("RAKOTO");
        prmpRepository.save(autre);
        cnm.prs.entity.Attribution creee = attributionRepository.findById(new cnm.prs.entity.Attribution.Cle(idDmc, 1)).orElseThrow();
        creee.setDossierCreePar("IMPX01");
        attributionRepository.saveAndFlush(creee);
        String fiche = mvc.perform(get("/api/dossiers/" + idDossier + "/fiche-presentation").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idDmc").value(idDmc))
                .andExpect(jsonPath("$.sections[?(@.code=='ATTRIBUTION')].lignes[?(@.code=='LOT')].valeur").value(hasItem("1")))
                .andExpect(jsonPath("$.sections[?(@.code=='MISE_EN_CONCURRENCE')].lignes[?(@.code=='DOSSIER_MISE_EN_CONCURRENCE')].valeur")
                        .value(hasItem("DOS-9960")))
                .andExpect(jsonPath("$.sections[?(@.code=='ATTRIBUTION')].lignes[?(@.code=='TITULAIRE_1')].valeur")
                        .value(hasItem(containsString("BTP Gamma (offre n° 3)"))))
                .andExpect(jsonPath("$.sections[?(@.code=='EVALUATION')].lignes[?(@.code=='OFFRES_EVALUEES')]").isNotEmpty())
                .andExpect(jsonPath("$.sections[?(@.code=='EVALUATION')].lignes[?(@.code=='RAPPORT_SIGNE')]").isNotEmpty())
                .andExpect(jsonPath("$.sections[?(@.code=='SEANCE')].lignes[?(@.code=='OUVERTE_LE')]").isNotEmpty())
                .andExpect(jsonPath("$.sections[?(@.code=='VALIDATION')].lignes[?(@.code=='DOSSIER_CREE_LE')]").isNotEmpty())
                .andExpect(jsonPath("$.sections[?(@.code=='VALIDATION')].lignes[?(@.code=='VERSION')]").isEmpty())
                .andExpect(jsonPath("$.sections[?(@.code=='VALIDATION')].lignes[?(@.code=='DOSSIER_CREE_PAR')].valeur")
                        .value(hasItem(containsString("RAKOTO"))))
                .andReturn().getResponse().getContentAsString();
        List<String> codesSections = JsonPath.read(fiche, "$.sections[*].code");
        assertThat(codesSections).startsWith("IDENTIFICATION", "MARCHE", "ATTRIBUTION", "MISE_EN_CONCURRENCE")
                .containsSubsequence("ATTRIBUTION", "MISE_EN_CONCURRENCE", "SEANCE", "EVALUATION", "VALIDATION")
                .doesNotContain("CALENDRIER_PLAN", "COHERENCE");
        assertThat(JsonPath.<List<String>>read(fiche, "$.sections[?(@.code=='PROCEDURE')].lignes[*].code"))
                .noneMatch(c -> c.startsWith("B04-CP-")).doesNotContain("DATE_LIMITE");
        assertThat(JsonPath.<List<String>>read(fiche, "$.sections[*].lignes[*].code")).doesNotContain("TITULAIRES");
        // ⚠️ 2026-10-20 (« generateur-modeles-pv », lot G1) — l'aperçu d'un modèle sur ce dossier de marché : procédure, marché et une
        // ligne de tableau par titulaire proposé, lus par les règles de la fiche de présentation ; rien n'est enregistré au dossier.
        String modele = mvc.perform(post("/api/modeles-pv").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"PV-DDM\",\"libelle\":\"PV du marché\",\"nature\":\"PV\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long idModele = ((Number) JsonPath.read(modele, "$.id")).longValue();
        // ⚠️ 2026-10-21 (« modele-pv-page ») — le modèle est une page, l'aperçu un PDF.
        String page = cnm.prs.service.ModelePvPageTest.document(
                cnm.prs.service.ModelePvPageTest.paragraphe("Lot {{PROCEDURE.LOT}} — {{MARCHE.DOSSIER_MEC}} — {{MARCHE.ESTIMATION}}"),
                cnm.prs.service.ModelePvPageTest.tableau(List.of("Rang", "Titulaire"), "TITULAIRES",
                        List.of("{{TIT.RANG}}", "{{TIT.CANDIDAT}} — {{TIT.MONTANT_HT}}"))).toString();
        mvc.perform(post("/api/modeles-pv/" + idModele + "/versions").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"document\":" + page + "}")).andExpect(status().isOk());
        byte[] apercu = mvc.perform(post("/api/modeles-pv/" + idModele + "/versions/1/apercu").param("dossier", String.valueOf(idDossier))
                .header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String texteApercu;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(apercu)) {
            texteApercu = cnm.prs.service.ModelePvPageTest.trie().getText(doc);
        }
        assertThat(texteApercu).contains("Lot 1 — DOS-9960", "Rang Titulaire").containsPattern("\\n1 BTP Gamma — ");
        assertThat(texteDuPdf(mvc.perform(get("/api/dossiers/" + idDossier + "/fiche-presentation/pdf").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())).contains("Dossier de marché", "Attribution",
                        "Mise en concurrence", "Évaluation des offres");
    }

    /** La partie {@code data} d'un dépôt de pièce. */
    private static org.springframework.mock.web.MockMultipartFile data(int idDossier, int idTypePiece) {
        return new org.springframework.mock.web.MockMultipartFile("data", "", "application/json",
                ("{\"idDossier\":" + idDossier + ",\"idTypePiece\":" + idTypePiece + "}").getBytes(StandardCharsets.UTF_8));
    }

    private void ficheEnLigne() throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        idDmc = ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"modeRemise\":\"ELECTRONIQUE\",\"garantieSoumission\":\"OUI\",\"alloti\":\"NON\","
                        + "\"variantes\":\"NON\",\"groupement\":\"NON\",\"provenance\":\"NATIONAL\",\"typePrix\":\"UNITAIRES\","
                        + "\"prixRevisable\":\"NON\",\"avance\":\"NON\",\"penalites\":\"CCAG\"}}"))
                .andExpect(status().isOk());
        // ⚠️ Tranche 1b — deux articles (quantités 10 et 5), pour les corrections proposées depuis le bordereau. ⚠️ V116 (Q11) : prix
        // estimés 10 × 1 000 000 + 5 × 400 000 = 12 000 000, le montant du plan.
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/articles").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"articles\":[{\"designation\":\"Ordinateur portable\",\"unite\":\"U\",\"quantite\":10,\"prixUnitaireEstime\":1000000,\"caracteristiques\":"
                        + "[{\"libelle\":\"Mémoire\",\"exigence\":\"16 Go\"}]},{\"designation\":\"Onduleur\",\"unite\":\"U\",\"quantite\":5,\"prixUnitaireEstime\":400000,"
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
        CaoIntegrationTest.joindreDecision(mvc, idDmc, tokenPrmp);
        // ⚠️ R1 (manuel révisé, §B2) — la surcharge du quorum de la CAO par procédure : validée (400), puis réglée à 1 membre (une CAO
        // de deux membres ; la majorité vaudrait l'unanimité), le président restant requis.
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"quorum\":2,\"caoQuorum\":\"la moitié\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUORUM_REGLE_INVALIDE"));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"quorum\":2,\"caoQuorum\":\"1\",\"dateCeremonie\":\"" + aujourdhui.plusDays(9) + "T09:00\",\"depositaire\":{\"nom\":\"Rakoto Jean\",\"email\":\"rakoto.depositaire@secours.mg\"}}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"));
        String internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        comptes.clear();
        comptes.addAll(JsonPath.read(internes, "$.membresCommission[*].im"));
        assertThat(JsonPath.<String>read(internes, "$.caoQuorum")).isEqualTo("1");
        assertThat(JsonPath.<String>read(internes, "$.caoQuorumEffectif")).isEqualTo("1");
        jetonM1 = bearer("m1@eval.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, comptes.get(0), null);
        jetonM2 = bearer("m2@eval.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, comptes.get(1), null);
    }
}
