package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import cnm.prs.entity.Avis;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.DelegationProfil;
import cnm.prs.entity.DemandeRetrait;
import cnm.prs.entity.Dispatch;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Examen;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.Localite;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Ugpm;
import cnm.prs.entity.Profile;
import cnm.prs.entity.Reception;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.Ministere;
import cnm.prs.entity.Organigramme;
import cnm.prs.entity.PrmpEntite;
import cnm.prs.entity.TypeDossier;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.repository.AvisRepository;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DelegationProfilRepository;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.LocaliteRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.NatureRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.ProfileRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.MinistereRepository;
import cnm.prs.repository.OrganigrammeRepository;
import cnm.prs.repository.PrmpEntiteRepository;
import cnm.prs.repository.TypeDossierRepository;
import cnm.prs.repository.PieceJointeRepository;
import cnm.prs.repository.PrmpEntiteDemandeRepository;
import cnm.prs.security.TokenService;
import cnm.prs.service.NotificationService;
import cnm.prs.service.PieceJointeService;

/**
 * Socle commun des tests d'integration metier issus de l'ex-CnmWorkflowIntegrationTest (LOT 2.3) :
 * configuration Spring, jeu de donnees de depart et helpers partages par plusieurs domaines.
 *
 * <p><b>Un seul contexte Spring</b> : les annotations de test vivent ici et sont heritees par toutes
 * les classes filles, qui n'en ajoutent aucune. Spring reutilise donc le meme contexte en cache pour
 * toute la suite - Flyway ne migre qu'une fois et le conteneur PostgreSQL reste unique
 * (cf. {@link AbstractIntegrationTest}).</p>
 *
 * <p><b>Regle de rattachement</b> : un helper utilise par plusieurs domaines vit ici en
 * {@code protected} ; un helper propre a un seul domaine reste {@code private} dans sa classe.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class CnmIntegrationTestSupport extends AbstractIntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected TokenService tokenService;
    @Autowired protected cnm.prs.security.PermissionService permissionService;
    @Autowired protected cnm.prs.security.LoginRateLimiter loginRateLimiter;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected PieceJointeService pieceJointeService;
    @Autowired protected NotificationService notificationService;
    @Autowired protected PieceJointeRepository pieceJointeRepository;
    @Autowired protected PrmpEntiteDemandeRepository prmpEntiteDemandeRepository;
    @Autowired protected LocaliteRepository localiteRepository;
    @Autowired protected ProfileRepository profileRepository;
    @Autowired protected ControleurRepository controleurRepository;
    @Autowired protected PrmpRepository prmpRepository;
    @Autowired protected CompteAuthRepository compteAuthRepository;
    @Autowired protected AvisRepository avisRepository;
    @Autowired protected DossierRepository dossierRepository;
    @Autowired protected ReceptionRepository receptionRepository;
    @Autowired protected DispatchRepository dispatchRepository;
    @Autowired protected ExamenRepository examenRepository;
    @Autowired protected cnm.prs.repository.SessionUtilisateurRepository sessionUtilisateurRepository;
    @Autowired protected cnm.prs.repository.IndicateurCtrlRepository indicateurCtrlRepository;
    @Autowired protected PpmRepository ppmRepository;
    @Autowired protected MarcheRepository marcheRepository;
    @Autowired protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired protected MarchePrevisionRepository marchePrevisionRepository;
    @Autowired protected cnm.prs.repository.CapmRepository capmRepository;
    @Autowired protected cnm.prs.repository.ExamenDetailRepository examenDetailRepository;
    @Autowired protected cnm.prs.repository.ExamenPieceRepository examenPieceRepository;
    @Autowired protected cnm.prs.repository.PointsCtrlRepository pointsCtrlRepository;
    @Autowired protected cnm.prs.repository.LettreRenvoiRepository lettreRenvoiRepository;
    @Autowired protected DemandeRetraitRepository demandeRetraitRepository;
    @Autowired protected DelegationProfilRepository delegationProfilRepository;
    @Autowired protected NatureRepository natureRepository;
    @Autowired protected ModePassationRepository modePassationRepository;
    @Autowired protected cnm.prs.repository.TypeDmcRepository typeDmcRepository;
    @Autowired protected cnm.prs.repository.DossierMecRepository dossierMecRepository;
    @Autowired protected cnm.prs.repository.LotRepository lotRepository;
    @Autowired protected cnm.prs.service.PvDocumentGenerator pvDocumentGenerator;
    @Autowired protected cnm.prs.service.ReferenceService referenceService;
    @Autowired protected jakarta.persistence.EntityManager entityManager;
    @Autowired protected TypeDossierRepository typeDossierRepository;
    @Autowired protected MinistereRepository ministereRepository;
    @Autowired protected OrganigrammeRepository organigrammeRepository;
    @Autowired protected EntiteContractRepository entiteContractRepository;
    @Autowired protected cnm.prs.repository.CategorieEntiteRepository categorieEntiteRepository;
    @Autowired protected PrmpEntiteRepository prmpEntiteRepository;
    @Autowired protected cnm.prs.repository.TypePieceJointeRepository typePieceJointeRepository;
    @Autowired protected cnm.prs.repository.PublicationRepository publicationRepository;
    @Autowired protected cnm.prs.repository.SousTypeDossierRepository sousTypeDossierRepository;
    @Autowired protected cnm.prs.repository.PvExamenRepository pvExamenRepository;
    @Autowired protected cnm.prs.repository.PvNavetteRepository pvNavetteRepository;
    @Autowired protected cnm.prs.repository.ObservationControleRepository observationControleRepository;
    @Autowired protected cnm.prs.repository.CopieDossierRepository copieDossierRepository;
    @Autowired protected cnm.prs.repository.LettreRenvoiLueRepository lueRepository;
    @Autowired protected cnm.prs.repository.DemandeRetraitVueRepository demandeRetraitVueRepository;
    @Autowired protected cnm.prs.repository.CompteRepository compteRepository;
    @Autowired protected cnm.prs.repository.SoaBeneficiaireRepository soaBeneficiaireRepository;
    @Autowired protected cnm.prs.repository.ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    @Autowired protected cnm.prs.repository.UgpmRepository ugpmRepository;
    @Autowired protected cnm.prs.repository.AuditLogRepository auditLogRepository;
    protected String tokenPresident;
    protected String tokenCc;
    protected String tokenMembre;
    protected String tokenAdmin;
    protected String tokenPrmp;
    protected String tokenPublication;

    /**
     * ⚠️ LOT 2 (2026-08-26) — exécute une migration Flyway de REPRISE DE DONNÉES (V2-V4, ex-runners
     * Java supprimés) dans la transaction du test : flush d'abord (les entités créées par le test
     * doivent être visibles du SQL), exécution du fichier réel de db/migration/, puis clear (le
     * cache de premier niveau serait périmé après l'UPDATE SQL). Le test valide ainsi le SQL
     * réellement livré, sur PostgreSQL réel — chose impossible du temps de H2.
     */
    protected void executerMigrationFlyway(String nomFichier) {
        String sql;
        try {
            sql = new String(
                    new org.springframework.core.io.ClassPathResource("db/migration/" + nomFichier)
                            .getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Migration introuvable : " + nomFichier, e);
        }
        entityManager.flush();
        jdbcTemplate.execute(sql);
        entityManager.clear();
    }

    @BeforeEach
    protected void seed() {
        // ⚠️ Audit 2026-08-27 (lot E) — les compteurs du limiteur de debit vivent dans le bean, hors
        // transaction : ils ne sont donc PAS annules avec le reste. Sans cette remise a zero, les
        // echecs de connexion d'une classe de test (toutes vues de la meme adresse 127.0.0.1)
        // s'accumuleraient jusqu'a verrouiller l'adresse pour les classes suivantes.
        loginRateLimiter.reinitialiser();
        localiteRepository.save(localite("ANT", "Antananarivo"));
        // Familles (tr_type_dossier) + sous-types initiaux (tr_sous_type_dossier), hiérarchie §familles.
        typeDossierRepository.save(new TypeDossier("DDP", "Dossier de Planification"));
        typeDossierRepository.save(new TypeDossier("DMC", "Dossier de Mise en Concurrence"));
        sousTypeDossierRepository.save(new SousTypeDossier("PPM", "Plan de Passation de Marché", "DDP"));
        sousTypeDossierRepository.save(new SousTypeDossier("PPM-AGPM",
                "Plan de Passation de Marché et Avis Général de Passation de Marché", "DDP"));
        sousTypeDossierRepository.save(new SousTypeDossier("DAO", "Dossier d'Appel d'Offres", "DMC"));
        sousTypeDossierRepository.save(new SousTypeDossier("DAOR", "Dossier d'Appel d'Offres Restreint", "DMC"));

        profileRepository.save(profile(1, "PRMP"));
        profileRepository.save(profile(2, "Président"));
        profileRepository.save(profile(3, "Chef de commission"));
        profileRepository.save(profile(4, "Secrétaire"));
        profileRepository.save(profile(5, "Membre"));
        profileRepository.save(profile(6, "Contrôleur vérificateur"));
        profileRepository.save(profile(7, "Chargé de publication"));
        profileRepository.save(profile(8, "Administrateur"));
        profileRepository.save(profile(9, "Assistant contrôleur"));

        controleurRepository.save(controleur("CTRPRE", 2, null));   // Président, voit tout
        controleurRepository.save(controleur("CTRCC1", 3, "ANT"));  // Chef de commission
        controleurRepository.save(controleur("CTRSEC", 4, "ANT"));  // Secrétaire
        controleurRepository.save(controleur("CTRMEM", 5, "ANT"));  // Membre
        controleurRepository.save(controleur("CTRVER", 6, "ANT"));  // Contrôleur vérificateur
        controleurRepository.save(controleur("CTRADM", 8, "ANT"));  // Administrateur
        controleurRepository.save(controleur("CTRPUB", 7, null));   // Chargé de publication
        controleurRepository.save(controleur("CTRASS", 9, "ANT"));  // Assistant contrôleur (ANT)
        prmpRepository.save(prmp("PRMP001", "ANT"));

        // Délégation ascendante (⚠️ 2026-08-14) — les 9 PAIRES OFFICIELLES (orientation MLD :
        // délégant = profil qui exerce, délégué = profil dont la tâche est exercée) :
        // Président (2) → Secrétaire (4), CC (3), Membre (5), Vérificateur (6), Assistant (9) ;
        // CC (3) → Secrétaire (4), Membre (5), Vérificateur (6), Assistant (9).
        // Table EXPLICITE, pas de rang : la paire CC → Secrétaire est listée alors que le CC est
        // SOUS le Secrétaire dans la hiérarchie.
        delegationProfilRepository.save(delegation(1, 2, 4));
        delegationProfilRepository.save(delegation(2, 2, 5));
        delegationProfilRepository.save(delegation(3, 2, 6));
        delegationProfilRepository.save(delegation(4, 3, 4));
        delegationProfilRepository.save(delegation(5, 3, 5));
        delegationProfilRepository.save(delegation(6, 3, 6));
        delegationProfilRepository.save(delegation(7, 2, 3));
        delegationProfilRepository.save(delegation(8, 2, 9));
        delegationProfilRepository.save(delegation(9, 3, 9));

        String hash = passwordEncoder.encode("pw");
        compteAuthRepository.save(new CompteAuth("CTRPRE", hash, "CONTROLEUR", "CTRPRE", true));
        compteAuthRepository.save(new CompteAuth("CTRCC1", hash, "CONTROLEUR", "CTRCC1", true));
        compteAuthRepository.save(new CompteAuth("CTRMEM", hash, "CONTROLEUR", "CTRMEM", true));
        compteAuthRepository.save(new CompteAuth("CTRADM", hash, "CONTROLEUR", "CTRADM", true));
        compteAuthRepository.save(new CompteAuth("PRMP001", hash, "PRMP", "PRMP001", true));

        // Circuit amont pour le workflow PV.
        avisRepository.save(avis("FAV", "Favorable"));
        avisRepository.save(avis("FAVR", "Favorable avec réserves"));
        avisRepository.save(avis("DEF", "Défavorable"));
        avisRepository.save(avis("NSP", "Ne se prononce pas"));
        dossierRepository.save(dossier(1, "EXAMINE"));
        receptionRepository.save(reception(1, 1, "CTRCC1", false));
        dispatchRepository.save(dispatch(1, 1, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(1, 1, "CTRMEM"));
        ppmRepository.save(ppm(1, 1, "PRMP001")); // PPM du dossier 1 appartenant à PRMP001

        // Seconde localité (TMS) : un CC, un dossier et sa réception — pour la règle d'intérim.
        localiteRepository.save(localite("TMS", "Toamasina"));
        controleurRepository.save(controleur("CTRCC2", 3, "TMS"));
        dossierRepository.save(dossier(2, "EXAMINE"));
        receptionRepository.save(reception(2, 2, "CTRCC2", false));

        // Une demande de retrait de PRMP001 sur le dossier 1 (localité ANT).
        demandeRetraitRepository.save(demandeRetrait(1, 1, "PRMP001"));

        // Entités contractantes localisées + affectations de PRMP001 (entité 1 = ANT, entité 2 = TMS).
        // La localité d'un dossier saisi est dérivée de l'entité choisie.
        ministereRepository.save(ministere(1));
        organigrammeRepository.save(organigramme(1, 1));
        entiteContractRepository.save(entite(1, 1, "ANT"));
        entiteContractRepository.save(entite(2, 1, "TMS"));
        prmpEntiteRepository.save(prmpEntite(1, "PRMP001", 1, true));
        prmpEntiteRepository.save(prmpEntite(2, "PRMP001", 2, true));

        tokenPresident = bearer("CTRPRE", ProfilUtilisateur.PRESIDENT, TypeActeur.CONTROLEUR, "CTRPRE", null);
        tokenCc = bearer("CTRCC1", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC1", "ANT");
        tokenMembre = bearer("CTRMEM", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM", "ANT");
        tokenAdmin = bearer("CTRADM", ProfilUtilisateur.ADMINISTRATEUR, TypeActeur.CONTROLEUR, "CTRADM", "ANT");
        tokenPrmp = bearer("PRMP001", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP001", "ANT");
        tokenPublication = bearer("CTRPUB", ProfilUtilisateur.CHARGE_PUBLICATION, TypeActeur.CONTROLEUR, "CTRPUB", null);
    }

    /**
     * Crée un PV avec l'avis donné sur l'examen 1 (dossier 1) et le porte à SIGNE.
     *
     * <p>⚠️ Ordre B (co-signature, 2026-08-28) — le <strong>Président signe d'abord</strong> et désigne
     * le Membre co-signataire, qui signe ensuite. L'ordre inverse (Membre puis Président), utilisé
     * jusqu'ici, part désormais en 409 : la part Membre n'est pas ouverte tant que personne n'a été
     * désigné. C'est le P/CC qui choisit, jamais l'antériorité.</p>
     */
    protected void signerPvAvecAvis(int idPv, String avis) throws Exception {
        // ⚠️ Cohérence avis ↔ observations (2026-08-01) : FAVR exige ≥ 1 observation à l'examen.
        if ("FAVR".equals(avis)) {
            ajouterObservationExamen1();
        }
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + idPv + ",\"idExamen\":1,\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        // ⚠️ VISA (2026-08-31) : un seul geste — avis + secrétaire de séance + co-signataire + part du
        // rôle. Remplace l'ancien couple « accepter » puis « signer(PRESIDENT) ». Le visa est réservé
        // au DISPATCHEUR : c'est CTRPRE dans la fixture, d'où le token Président et non celui du CC.
        viser(idPv, tokenPresident, "CTRPRE", avis, "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("SIGNE"));
    }

    /**
     * Passe la (les) observation(s) courante(s) du dossier 1 par la décision donnée (⚠️ 2026-08-02) —
     * partagé par {@code VerificationIntegrationTest} et {@code MiseAJourPpmIntegrationTest} (mène le
     * dossier 1 jusqu'à CLOTURE).
     */
    protected void passageObservationDossier1(String tokenVer, String decision, String precision) throws Exception {
        String obs = mvc.perform(get("/api/observations-pv").header("Authorization", tokenVer).param("dossier", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idObs = com.jayway.jsonpath.JsonPath.read(obs, "$[0].idObservationPv");
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"decisions\":[{\"idObservationPv\":" + idObs + ",\"decision\":\"" + decision
                        + "\"" + (precision == null ? "" : ",\"precision\":\"" + precision + "\"") + "}]}"))
                .andExpect(status().isOk());
    }

    /**
     * Mène le dossier 1 (localité ANT) jusqu'à CLOTURE par le circuit FAVR complet : PV signé, rappel
     * MAINTENUE, resoumission de la PRMP, levée, transmission SIGMP puis archivage par l'Assistant.
     * Laisse derrière lui un historique d'échanges et une transmission SIGMP à cloisonner.
     */
    protected void cloturerDossier1(int idPv, String tokenVer) throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR,
                "CTRASS", "ANT");
        signerPvAvecAvis(idPv, "FAVR");
        passageObservationDossier1(tokenVer, "MAINTENUE", "a rectifier");
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk());
        passageObservationDossier1(tokenVer, "LEVEE", null);
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + idPv + "/archiver").header("Authorization", tokenAss))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));
    }

    /** Pose une observation (point non conforme) sur l'examen 1 — pré-requis d'un avis FAVR (cohérence 2026-08-01). */
    protected void ajouterObservationExamen1() {
        if (!pointsCtrlRepository.existsById(990)) {
            PointsCtrl pc = new PointsCtrl();
            pc.setIdPointCtrl(990); pc.setLibelPointCtrl("Contrôle test"); pc.setObligatoire(true);
            pc.setIdTypeDossier("DDP");
            pointsCtrlRepository.save(pc);
        }
        ExamenDetail d = new ExamenDetail();
        d.setIdDetailExamen(990); d.setIdExamen(1); d.setIdPtControle(990); d.setConforme(false);
        examenDetailRepository.save(d);
    }

    /** Crée une lettre de renvoi SIGNÉE sur l'examen/dossier 1 (PPM de PRMP001) ; renvoie sa PK. */
    protected int seedLettreSignee() {
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1);
        l.setIdDossier(1);
        l.setObjetLettre("Renvoi");
        l.setStatut("SIGNE");
        return lettreRenvoiRepository.save(l).getIdLettre();
    }

    /** Crée un PV signé H2 sur un examen (PK manuelle, avis seedé). */
    protected void seedPvSigne(int idPv, int idExamen) {
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(idPv);
        pv.setIdExamen(idExamen);
        pv.setIdAvis("FAV");
        pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("SIGNE");
        // ⚠️ Schéma réel (contrainte t_pv_examen_cosignataire_check, migration 2026-06-17) : un PV
        // SIGNE porte la signature du Membre ET d'un co-signataire — H2 ne l'imposait pas, le
        // PostgreSQL de Testcontainers oui. La fixture reflète désormais l'invariant de production.
        pv.setDateSignatureMembre(LocalDate.now());
        pv.setDateSignaturePresident(LocalDate.now());
        pv.setNbNavettes(0);
        pvExamenRepository.save(pv);
    }

    /** Texte extrait du PDF (PDFBox), espaces normalisés (FOP coupe les lignes au fil de la mise en page). */
    protected String texteDuPdf(byte[] pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
        }
    }

    /** Vrai si le PDF contient au moins un objet image (PDFBox). */
    protected boolean contientImage(byte[] pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            for (org.apache.pdfbox.pdmodel.PDPage page : doc.getPages()) {
                org.apache.pdfbox.pdmodel.PDResources res = page.getResources();
                if (res == null) {
                    continue;
                }
                for (org.apache.pdfbox.cos.COSName name : res.getXObjectNames()) {
                    if (res.getXObject(name) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /** Lettre de renvoi de l'examen 1 (localité ANT) au statut SOUMIS. */
    protected int seedLettreSoumise() {
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1); l.setIdDossier(1); l.setObjetLettre("Renvoi"); l.setStatut("SOUMIS");
        return lettreRenvoiRepository.save(l).getIdLettre();
    }

    /** Crée un type de pièce dans le référentiel H2 et renvoie sa PK générée. */
    protected int seedTypePiece(String libelle, boolean obligatoire, String typeDossier, int ordre) {
        cnm.prs.entity.TypePieceJointe t = new cnm.prs.entity.TypePieceJointe();
        t.setLibellePiece(libelle);
        t.setObligatoire(obligatoire);
        t.setIdTypeDossier(typeDossier);
        t.setOrdre(ordre);
        return typePieceJointeRepository.save(t).getIdTypePiece();
    }

    protected org.springframework.test.web.servlet.ResultActions soumettre(String token) throws Exception {
        return mvc.perform(post("/api/pv-examens/1/soumettre").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"));
    }

    /**
     * ⚠️ VISA (2026-08-31) — clôture de navette en un geste. {@code avis} et {@code commentaire} sont
     * facultatifs (avis absent = celui du Membre conservé) ; secrétaire et co-signataire ne le sont pas.
     */
    protected org.springframework.test.web.servlet.ResultActions viser(int idPv, String token, String acteur,
            String avis, String idSecretaireSeance, String imMembreCoSignataire) throws Exception {
        StringBuilder corps = new StringBuilder("{\"imActeur\":\"").append(acteur).append("\"");
        if (avis != null) {
            corps.append(",\"idAvis\":\"").append(avis).append("\"");
        }
        if (idSecretaireSeance != null) {
            corps.append(",\"idSecretaireSeance\":\"").append(idSecretaireSeance).append("\"");
        }
        if (imMembreCoSignataire != null) {
            corps.append(",\"imMembreCoSignataire\":\"").append(imMembreCoSignataire).append("\"");
        }
        corps.append("}");
        return mvc.perform(post("/api/pv-examens/" + idPv + "/viser").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(corps.toString()));
    }

    protected org.springframework.test.web.servlet.ResultActions signer(String token, String acteur, String role)
            throws Exception {
        return signer(token, acteur, role, null);
    }

    /**
     * ⚠️ Co-signature (2026-08-28) — surcharge avec désignation du Membre co-signataire, obligatoire
     * quand le rôle signé est PRESIDENT ou CC (ordre B : la part Membre n'est ouverte qu'ensuite).
     */
    protected org.springframework.test.web.servlet.ResultActions signer(String token, String acteur, String role,
            String imMembreCoSignataire) throws Exception {
        String corps = "{\"imActeur\":\"" + acteur + "\",\"role\":\"" + role + "\""
                + (imMembreCoSignataire == null ? ""
                        : ",\"imMembreCoSignataire\":\"" + imMembreCoSignataire + "\"")
                + "}";
        return mvc.perform(post("/api/pv-examens/1/signer").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    protected String bearer(String login, ProfilUtilisateur role, TypeActeur type, String ref, String loc) {
        return "Bearer " + tokenService.generer(login, role.name(), type, ref, loc);
    }

    protected Localite localite(String id, String libelle) {
        // referencement + code localite retirés du contrat/entité (2026-07-17) — colonnes BD dépréciées.
        Localite l = new Localite();
        l.setIdLocalite(id);
        l.setLibelleLocalite(libelle);
        return l;
    }

    protected Profile profile(int id, String libelle) {
        Profile p = new Profile();
        p.setIdProfile(id);
        p.setProfile(libelle);
        return p;
    }

    protected Controleur controleur(String im, int profile, String localite) {
        Controleur c = new Controleur();
        c.setImControleur(im);
        c.setNomCont("Nom" + im);
        c.setPrenomsCont("Prenoms");
        c.setEmailCont(im.toLowerCase() + "@cnm.mg");
        c.setIdProfile(profile);
        c.setIdLocalite(localite);
        c.setTransversal(false);
        return c;
    }

    protected Prmp prmp(String id, String localite) {
        Prmp p = new Prmp();
        p.setIdPrmp(id);
        p.setNomPrmp("Nom");
        p.setPrenomsPrmp("Prenoms");
        p.setArreteNomin("ARR-001");
        p.setDateNomin(LocalDate.of(2024, 1, 15));
        p.setCin("101011112222");
        p.setDateCin(LocalDate.of(2010, 5, 5));
        p.setLieuCin("Antananarivo");
        p.setEmailPrmp("prmp@min.mg");
        p.setTelPrmp("0330000001");
        return p;
    }

    protected DelegationProfil delegation(int id, int delegant, int delegue) {
        DelegationProfil d = new DelegationProfil();
        d.setIdDelegation(id);
        d.setIdProfileDelegant(delegant);
        d.setIdProfileDelegue(delegue);
        d.setActif(true);
        return d;
    }

    protected DemandeRetrait demandeRetrait(int id, int dossier, String idPrmp) {
        DemandeRetrait d = new DemandeRetrait();
        // ID_DEMANDE_RETRAIT est auto-généré (IDENTITY) : ne pas le fixer (sinon entité détachée).
        d.setIdDossier(dossier);
        d.setIdPrmp(idPrmp);
        d.setMotifRetrait("Motif de retrait");
        d.setDateDemande(LocalDateTime.of(2026, 6, 5, 10, 0));
        d.setStatut("EN_ATTENTE");
        return d;
    }

    protected Ppm ppm(int id, int dossier, String idPrmp) {
        Ppm p = new Ppm();
        p.setIdPpm(id);
        p.setIdDossier(dossier);
        p.setExercice(2026);
        p.setSignataire("Signataire");
        p.setDateSignature(LocalDate.of(2026, 1, 10));
        p.setReference("PPM-REF-" + id);
        p.setIdPrmp(idPrmp);
        return p;
    }

    protected Ppm ppmLocalise(int id, int dossier, String localite) {
        Ppm p = new Ppm();
        p.setIdPpm(id);
        p.setIdDossier(dossier);
        p.setExercice(2026);
        p.setSignataire("Signataire");
        p.setDateSignature(LocalDate.of(2026, 1, 10));
        p.setReference("PPM-REF-" + id);
        p.setIdLocalite(localite);
        return p;
    }

    protected Marche marche(int idDetail, int dossier, int ppm) {
        Marche m = new Marche();
        m.setIdDetail(idDetail);
        m.setIdDossier(dossier);
        m.setIdPpm(ppm);
        m.setDesignationMarche("Marche " + idDetail);
        m.setStatut("PREVU");
        return m;
    }

    protected Ministere ministere(int id) {
        Ministere m = new Ministere();
        m.setIdMinistere(id);
        m.setLibelleMinistere("Ministere " + id);
        return m;
    }

    protected Organigramme organigramme(int id, int ministere) {
        Organigramme o = new Organigramme();
        o.setIdOrganigramme(id);
        o.setActif(true);
        o.setIdMinistere(ministere);
        o.setLibelle("Organigramme " + id);
        return o;
    }

    protected EntiteContract entite(int id, int organigramme, String localite) {
        EntiteContract e = new EntiteContract();
        e.setIdEntiteContract(id);
        e.setLibelleEntite("Entite " + id);
        e.setAdresse("Adresse");
        e.setIdOrganigramme(organigramme);
        e.setNiveauHierarchique(1);
        e.setIdLocalite(localite);
        return e;
    }

    protected PrmpEntite prmpEntite(int id, String prmp, int entite, boolean actif) {
        PrmpEntite pe = new PrmpEntite();
        pe.setIdPrmpEntite(id);
        pe.setIdPrmp(prmp);
        pe.setIdEntiteContract(entite);
        pe.setActif(actif);
        return pe;
    }

    protected Avis avis(String id, String libelle) {
        Avis a = new Avis();
        a.setIdAvis(id);
        a.setLibelleAvis(libelle);
        return a;
    }

    protected Dossier dossier(int id, String statut) {
        Dossier d = new Dossier();
        d.setIdDossier(id);
        d.setRefeDossier("DOS-" + id);
        d.setDateRef(LocalDate.of(2026, 6, 1));
        d.setStatut(statut);
        return d;
    }

    /** Fiche UGPM minimale rattachée à une PRMP de tutelle (champs NOT NULL renseignés). */
    protected Ugpm ugpm(String idUgpm, String idPrmpTutelle, String nom, String prenoms) {
        Ugpm u = new Ugpm();
        u.setIdUgpm(idUgpm);
        u.setLibelle("Unite " + idUgpm);
        u.setIdPrmpTutelle(idPrmpTutelle);
        u.setNomUgpm(nom);
        u.setPrenomsUgpm(prenoms);
        u.setCin("303033334444");
        u.setDateCin(LocalDate.of(2012, 3, 3));
        u.setLieuCin("Antananarivo");
        u.setEmailUgpm(idUgpm.toLowerCase() + "@min.mg");
        u.setTelUgpm("0330000010");
        return u;
    }

    protected Dossier dossierLoc(int id, String statut, String localite, String idPrmp) {
        Dossier d = dossier(id, statut);
        d.setIdLocalite(localite);
        d.setIdPrmp(idPrmp);
        return d;
    }

    protected Reception reception(int id, int dossier, String imRecept, boolean complet) {
        Reception r = new Reception();
        r.setIdReception(id);
        r.setIdDossier(dossier);
        r.setNumPassage(1);
        r.setTypePassage("INITIAL");
        r.setImCtrlRecept(imRecept);
        r.setDateReception(LocalDateTime.of(2026, 6, 2, 10, 30));
        r.setComplet(complet);
        return r;
    }

    /**
     * Dispatch de fixture — ⚠️ 2026-08-31 : le <strong>dispatcheur</strong> vaut {@code CTRPRE} par
     * défaut. Il ne l'était pas jusqu'ici ({@code IM_CTRL_DISPATCH} restait NULL), ce qui n'avait
     * aucune conséquence tant que personne ne le lisait. Depuis le visa, il porte l'habilitation :
     * un dispatch sans dispatcheur rend le PV invisable. La surcharge à cinq arguments sert les tests
     * qui ont besoin d'un dispatcheur nommé (CC dispatcheur, tiers non habilité).
     */
    protected Dispatch dispatch(int id, int reception, String cc, String membre) {
        return dispatch(id, reception, cc, membre, "CTRPRE");
    }

    protected Dispatch dispatch(int id, int reception, String cc, String membre, String dispatcheur) {
        Dispatch d = new Dispatch();
        d.setIdDispatch(id);
        d.setIdReception(reception);
        d.setImCtrlCc(cc);
        d.setImCtrlMembre(membre);
        d.setImCtrlDispatch(dispatcheur);
        d.setDateDispatch(LocalDateTime.of(2026, 6, 3, 14, 45));
        d.setInterimDispatch(false);
        return d;
    }

    protected Examen examen(int id, int dispatch, String membre) {
        Examen e = new Examen();
        e.setIdExamen(id);
        e.setIdDispatch(dispatch);
        e.setImCtrlMembre(membre);
        e.setDateExamen(LocalDate.of(2026, 6, 4));
        return e;
    }
}
