package cnm.prs;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PieceJointeMetaDto;
import cnm.prs.entity.Avis;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.DelegationProfil;
import cnm.prs.entity.DemandeRetrait;
import cnm.prs.entity.Dispatch;
import cnm.prs.entity.Dossier;
import java.util.List;

import cnm.prs.entity.Capm;
import cnm.prs.entity.Examen;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.Localite;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Ugpm;
import cnm.prs.entity.Profile;
import cnm.prs.entity.Reception;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.Ministere;
import cnm.prs.entity.Organigramme;
import cnm.prs.entity.PrmpEntite;
import cnm.prs.entity.PrmpEntiteDemande;
import cnm.prs.entity.TypeDossier;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.exception.BadRequestException;
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
import cnm.prs.service.PvDocumentContexte;
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
 * Tests d'intégration de bout en bout : authentification JWT, autorisations par profil,
 * workflow du PV et comportements automatiques. Exécutés sur une base H2 isolée
 * (cf. src/test/resources/application.properties), chaque test étant transactionnel et
 * annulé en fin d'exécution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CnmWorkflowIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TokenService tokenService;
    @Autowired private cnm.prs.security.PermissionService permissionService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PieceJointeService pieceJointeService;
    @Autowired private NotificationService notificationService;
    @Autowired private PieceJointeRepository pieceJointeRepository;
    @Autowired private PrmpEntiteDemandeRepository prmpEntiteDemandeRepository;

    @Autowired private LocaliteRepository localiteRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ControleurRepository controleurRepository;
    @Autowired private PrmpRepository prmpRepository;
    @Autowired private CompteAuthRepository compteAuthRepository;
    @Autowired private AvisRepository avisRepository;
    @Autowired private DossierRepository dossierRepository;
    @Autowired private ReceptionRepository receptionRepository;
    @Autowired private DispatchRepository dispatchRepository;
    @Autowired private ExamenRepository examenRepository;
    @Autowired private cnm.prs.repository.SessionUtilisateurRepository sessionUtilisateurRepository;
    @Autowired private cnm.prs.repository.IndicateurCtrlRepository indicateurCtrlRepository;
    @Autowired private PpmRepository ppmRepository;
    @Autowired private MarcheRepository marcheRepository;
    @Autowired private cnm.prs.seed.FormeMarcheMigration formeMarcheMigration;
    @Autowired private MarchePrevisionRepository marchePrevisionRepository;
    @Autowired private cnm.prs.repository.CapmRepository capmRepository;
    @Autowired private cnm.prs.repository.ExamenDetailRepository examenDetailRepository;
    @Autowired private cnm.prs.repository.PointsCtrlRepository pointsCtrlRepository;
    @Autowired private cnm.prs.repository.LettreRenvoiRepository lettreRenvoiRepository;
    @Autowired private cnm.prs.service.LettreRenvoiDocumentService lettreRenvoiDocumentService;
    @Autowired private DemandeRetraitRepository demandeRetraitRepository;
    @Autowired private DelegationProfilRepository delegationProfilRepository;
    @Autowired private NatureRepository natureRepository;
    @Autowired private ModePassationRepository modePassationRepository;
    @Autowired private cnm.prs.repository.TypeDmcRepository typeDmcRepository;
    @Autowired private cnm.prs.repository.DossierMecRepository dossierMecRepository;
    @Autowired private cnm.prs.repository.LotRepository lotRepository;
    @Autowired private cnm.prs.repository.TrancheRepository trancheRepository;
    @Autowired private cnm.prs.service.PvDocumentGenerator pvDocumentGenerator;
    @Autowired private cnm.prs.service.ReferenceService referenceService;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private TypeDossierRepository typeDossierRepository;
    @Autowired private MinistereRepository ministereRepository;
    @Autowired private OrganigrammeRepository organigrammeRepository;
    @Autowired private EntiteContractRepository entiteContractRepository;
    @Autowired private cnm.prs.repository.CategorieEntiteRepository categorieEntiteRepository;
    @Autowired private PrmpEntiteRepository prmpEntiteRepository;
    @Autowired private cnm.prs.repository.TypePieceJointeRepository typePieceJointeRepository;
    @Autowired private cnm.prs.repository.PublicationRepository publicationRepository;
    @Autowired private cnm.prs.repository.SousTypeDossierRepository sousTypeDossierRepository;
    @Autowired private cnm.prs.repository.PvExamenRepository pvExamenRepository;
    @Autowired private cnm.prs.repository.PvNavetteRepository pvNavetteRepository;
    @Autowired private cnm.prs.repository.ObservationControleRepository observationControleRepository;
    @Autowired private cnm.prs.repository.CopieDossierRepository copieDossierRepository;
    @Autowired private cnm.prs.repository.LettreRenvoiLueRepository lueRepository;
    @Autowired private cnm.prs.repository.DemandeRetraitVueRepository demandeRetraitVueRepository;
    @Autowired private cnm.prs.repository.CompteRepository compteRepository;
    @Autowired private cnm.prs.repository.SoaBeneficiaireRepository soaBeneficiaireRepository;
    @Autowired private cnm.prs.repository.ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    @Autowired private cnm.prs.repository.UgpmRepository ugpmRepository;
    // Gardes des contrôleurs de pilotage / circuit / bénéficiaires (2026-08-24).
    @Autowired private cnm.prs.repository.EcheanceRepository echeanceRepository;
    @Autowired private cnm.prs.repository.AnomalieRepository anomalieRepository;
    @Autowired private cnm.prs.repository.RegleAnomalieRepository regleAnomalieRepository;
    @Autowired private cnm.prs.repository.IndicateurPrmpRepository indicateurPrmpRepository;
    @Autowired private cnm.prs.repository.SnapshotStatsRepository snapshotStatsRepository;

    private String tokenPresident;
    private String tokenCc;
    private String tokenMembre;
    private String tokenAdmin;
    private String tokenPrmp;
    private String tokenPublication;

    @BeforeEach
    void seed() {
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

    // ------------------------------------------------------------------
    // Détermination automatique du mode de passation (§3.1, Module 02)
    // ------------------------------------------------------------------


    @Test
    @DisplayName("POST /api/marches : PK idDetail générée serveur (seq_marche) — id client ignoré, deux PRMP → PK distinctes, aucune collision")
    void marche_pkServeur_ignoreClient_pasDeCollisionEntreDeuxPrmp() throws Exception {
        // Référentiels + règle : montEstim 500M / Travaux / ANT → mode 2 (AOR), pour garantir un 201.
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));

        // Deux brouillons PPM, un par PRMP (même localité ANT).
        Dossier d1 = dossier(60, "BROUILLON");
        d1.setIdTypeDossier("DDP"); d1.setIdPrmp("PRMP001"); d1.setIdLocalite("ANT");
        dossierRepository.save(d1);
        ppmRepository.save(ppm(60, 60, "PRMP001"));

        prmpRepository.save(prmp("PRMP002", "ANT"));
        String tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");
        Dossier d2 = dossier(61, "BROUILLON");
        d2.setIdTypeDossier("DDP"); d2.setIdPrmp("PRMP002"); d2.setIdLocalite("ANT");
        dossierRepository.save(d2);
        ppmRepository.save(ppm(61, 61, "PRMP002"));

        // Les deux PRMP envoient le MÊME idDetail client (99001) — il doit être ignoré des deux côtés.
        String r1 = mvc.perform(post("/api/marches").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":99001,\"idDossier\":60,\"idPpm\":60,\"montEstim\":500000000,"
                        + "\"idNature\":1,\"statut\":\"PREVU\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String r2 = mvc.perform(post("/api/marches").header("Authorization", tokenPrmp2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":99001,\"idDossier\":61,\"idPpm\":61,\"montEstim\":500000000,"
                        + "\"idNature\":1,\"statut\":\"PREVU\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int id1 = com.jayway.jsonpath.JsonPath.read(r1, "$.idDetail");
        int id2 = com.jayway.jsonpath.JsonPath.read(r2, "$.idDetail");
        // Réponse : idDetail généré présent, id client (99001) ignoré des deux côtés, PK distinctes (aucune collision).
        org.junit.jupiter.api.Assertions.assertNotEquals(99001, id1);
        org.junit.jupiter.api.Assertions.assertNotEquals(99001, id2);
        org.junit.jupiter.api.Assertions.assertTrue(id1 >= 300001 && id2 >= 300001);
        org.junit.jupiter.api.Assertions.assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("Réinitialisation Admin : l'Admin force un nouveau mot de passe")
    void adminReinitialiseMotDePasse() throws Exception {
        String body = "{\"nouveauMotDePasse\":\"Reinit#2026\"}";
        // Un non-admin ne peut pas réinitialiser → 403.
        mvc.perform(post("/api/comptes-auth/CTRMEM/reinitialiser-mot-de-passe").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // L'Admin réinitialise le mot de passe de CTRMEM.
        mvc.perform(post("/api/comptes-auth/CTRMEM/reinitialiser-mot-de-passe").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        // CTRMEM se connecte avec le nouveau mot de passe.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRMEM\",\"motDePasse\":\"Reinit#2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBRE"));
        // Compte inexistant → 404.
        mvc.perform(post("/api/comptes-auth/INCONNU/reinitialiser-mot-de-passe").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Changer son mot de passe : nouveau mdp actif, ancien rejeté, garde du mdp actuel")
    void changerMotDePasse() throws Exception {
        // Changement réussi (le mot de passe seedé est « pw »).
        mvc.perform(post("/api/mon-compte/changer-mot-de-passe").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ancienMotDePasse\":\"pw\",\"nouveauMotDePasse\":\"Nouveau#2026\"}"))
                .andExpect(status().isOk());

        // Connexion avec le nouveau mot de passe → OK.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRMEM\",\"motDePasse\":\"Nouveau#2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBRE"));

        // Connexion avec l'ancien mot de passe → refusée.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRMEM\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isUnauthorized());

        // Mauvais mot de passe actuel → 400.
        mvc.perform(post("/api/mon-compte/changer-mot-de-passe").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ancienMotDePasse\":\"FAUX\",\"nouveauMotDePasse\":\"Encore#2026\"}"))
                .andExpect(status().isBadRequest());

        // Sans jeton → 401.
        mvc.perform(post("/api/mon-compte/changer-mot-de-passe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ancienMotDePasse\":\"pw\",\"nouveauMotDePasse\":\"Autre#2026\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Auto-inscription PRMP : compte inactif → activation Admin → connexion")
    void autoInscriptionPrmp_validationAdmin() throws Exception {
        String inscription = "{"
                + "\"login\":\"prmp.new\",\"motDePasse\":\"Passw0rd!\",\"idPrmp\":\"PRMP777\","
                + "\"nomPrmp\":\"Rakoto\",\"prenomsPrmp\":\"Nouvelle\","
                + "\"arreteNomin\":\"ARR-2026-777\",\"dateNomin\":\"2026-01-01\",\"cin\":\"101010101010\","
                + "\"dateCin\":\"2010-01-01\",\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"new@prmp.mg\","
                + "\"telPrmp\":\"0340000000\"}";

        // Inscription publique (sans jeton) → 201, compte inactif.
        mvc.perform(post("/api/auth/register/prmp").contentType(MediaType.APPLICATION_JSON).content(inscription))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actif").value(false))
                .andExpect(jsonPath("$.typeActeur").value("PRMP"));

        // L'Administrateur est notifié de l'inscription en attente.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVELLE_INSCRIPTION')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVELLE_INSCRIPTION')].destinataireIm", hasItem("CTRADM")));

        // Connexion refusée tant que le compte n'est pas validé → 401.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.new\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isUnauthorized());

        // Un non-administrateur ne peut pas activer → 403.
        mvc.perform(post("/api/comptes-auth/prmp.new/activer").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // L'Administrateur valide le compte.
        mvc.perform(post("/api/comptes-auth/prmp.new/activer").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true));

        // La connexion fonctionne désormais, avec le rôle PRMP.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.new\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PRMP"));

        // Réinscription avec le même login → 409.
        mvc.perform(post("/api/auth/register/prmp").contentType(MediaType.APPLICATION_JSON).content(inscription))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Login : le rôle et la localité sont déduits du profil")
    void login_resoutRoleEtLocalite() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRCC1\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CHEF_COMMISSION"))
                .andExpect(jsonPath("$.localite").value("ANT"))
                // ⚠️ Phase 3 du plan cookie : le jeton ne sort plus dans le corps — cookie seul.
                .andExpect(jsonPath("$.token").value(nullValue()));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRPRE\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRESIDENT"))
                .andExpect(jsonPath("$.localite").doesNotExist());

        // La PRMP n'a plus de localité propre : la claim localite est absente de sa réponse de connexion.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"PRMP001\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRMP"))
                .andExpect(jsonPath("$.localite").doesNotExist());
    }

    @Test
    @DisplayName("Login : nomAffichage « Nom Prénoms » résolu serveur pour les 3 types d'acteur — dont l'UGPM, "
            + "dont le « ref » désigne la tutelle et la fiche est fermée en lecture")
    void login_nomAffichage_tousTypesActeur() throws Exception {
        // Contrôleur (vaut pour tous les rôles CNM, Administrateur compris).
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRCC1\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomAffichage").value("NomCTRCC1 Prenoms"));

        // PRMP.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"PRMP001\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomAffichage").value("Nom Prenoms"));

        // UGPM : « ref » porte la PRMP de tutelle (périmètre), le nom vient de nomAffichage.
        Ugpm u = new Ugpm();
        u.setIdUgpm("UGPM002");
        u.setLibelle("UGPM du ministère");
        u.setIdPrmpTutelle("PRMP001");
        u.setNomUgpm("Rakoto");
        u.setPrenomsUgpm("Jean Claude");
        u.setCin("202022223333");
        u.setDateCin(LocalDate.of(2011, 6, 6));
        u.setLieuCin("Antananarivo");
        u.setEmailUgpm("ugpm002@min.mg");
        u.setTelUgpm("0330000002");
        ugpmRepository.save(u);
        compteAuthRepository.save(new CompteAuth("UGPM002", passwordEncoder.encode("pw"), "UGPM", "UGPM002", true));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"UGPM002\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("UGPM"))
                .andExpect(jsonPath("$.nomAffichage").value("Rakoto Jean Claude"))
                .andExpect(jsonPath("$.ref").value("PRMP001"));   // périmètre = tutelle, inchangé
    }

    @Test
    @DisplayName("Login : fiche sans nom → nomAffichage retombe sur le login (jamais vide côté front)")
    void login_nomAffichage_repliSurLogin() throws Exception {
        Prmp sansNom = prmp("PRMP950", "ANT");
        sansNom.setNomPrmp("");
        sansNom.setPrenomsPrmp("");
        prmpRepository.save(sansNom);
        compteAuthRepository.save(new CompteAuth("prmp.anon", passwordEncoder.encode("pw"), "PRMP", "PRMP950", true));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.anon\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomAffichage").value("prmp.anon"));
    }

    @Test
    @DisplayName("Login : mauvais mot de passe → 401")
    void login_mauvaisMotDePasse() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRCC1\",\"motDePasse\":\"faux\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Endpoint protégé sans token → 401")
    void endpointProtege_sansToken() throws Exception {
        mvc.perform(get("/api/dossiers")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Référentiel public d'entités : accessible SANS jeton (écran d'inscription)")
    void entitesPubliques_sansToken() throws Exception {
        mvc.perform(get("/api/auth/entites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idEntiteContract==1)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idEntiteContract==1)].idLocalite", hasItem("ANT")));
    }

    /**
     * ⚠️ Durcissement (2026-08-24) — {@code GET /api/auth/prmps} servait anonymement le référentiel
     * réduit des PRMP, c'est-à-dire la <strong>liste des comptes de connexion existants</strong>,
     * alors que {@code POST /api/auth/login} n'est pas limité en débit : énumération de comptes puis
     * martelage. La route est sortie du {@code permitAll} de {@code /api/auth/**} et réservée à
     * l'Administrateur. Ce test verrouille la fermeture : il échouera si quelqu'un remet la route
     * dans le {@code permitAll} (401 → 200) ou l'ouvre à un profil authentifié quelconque (403 → 200).
     */
    @Test
    @DisplayName("Référentiel des PRMP : anonyme → 401, PRMP authentifiée → 403, Administrateur → 200")
    void prmpsReferentiel_reserveAdministrateur() throws Exception {
        mvc.perform(get("/api/auth/prmps")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/prmps").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/prmps").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')]", hasSize(1)));
    }

    /**
     * ⚠️ Non-régression du durcissement ci-dessus : en extrayant {@code GET /api/auth/prmps} du
     * {@code permitAll} de {@code /api/auth/**}, il aurait été facile de fermer tout le préfixe et de
     * casser l'ouverture de session et l'écran d'inscription. Les routes réellement publiques
     * doivent rester joignables <strong>sans aucun jeton</strong> : sans elles, plus personne ne peut
     * se connecter ni s'inscrire.
     */
    @Test
    @DisplayName("Routes publiques préservées : login et référentiel d'entités répondent sans jeton")
    void routesPubliques_restentOuvertesSansJeton() throws Exception {
        // Login : 200 avec de bons identifiants — la route n'est pas passée derrière l'authentification.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRCC1\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk());
        // Mauvais mot de passe : 401 émis par l'authentification métier, PAS par le filtre de sécurité
        // (une route devenue protégée renverrait 401 aussi — le 200 ci-dessus lève l'ambiguïté).
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRCC1\",\"motDePasse\":\"faux\"}"))
                .andExpect(status().isUnauthorized());
        // Référentiel public des entités contractantes (écran d'inscription PRMP) : toujours ouvert.
        mvc.perform(get("/api/auth/entites")).andExpect(status().isOk());
    }

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
    @DisplayName("Inscription PRMP v2 (multipart) : compte EN_ATTENTE + déclarations + pièces ; ≥1 entité requise")
    void inscriptionV2_multipart() throws Exception {
        String data = "{\"login\":\"prmp.v2\",\"motDePasse\":\"Passw0rd!\",\"idPrmp\":\"PRMP900\","
                + "\"nomPrmp\":\"Rakoto\",\"prenomsPrmp\":\"V2\","
                + "\"arreteNomin\":\"ARR-2026-900\",\"dateNomin\":\"2026-01-01\",\"cin\":\"909090909090\","
                + "\"dateCin\":\"2010-01-01\",\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"v2@prmp.mg\","
                + "\"telPrmp\":\"0340000900\",\"idEntites\":[1],"
                + "\"entitesNonListees\":[{\"libelle\":\"Nouvelle Autorite\",\"adresse\":\"Adr\",\"idLocalite\":\"ANT\"}]}";
        MockMultipartFile dataPart = new MockMultipartFile("data", "", "application/json",
                data.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile arrete = new MockMultipartFile("arrete", "arrete.pdf", "application/pdf",
                "%PDF-1.4 arrete".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile cin = new MockMultipartFile("cin", "cin.png", "image/png",
                new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 });

        // Inscription multipart → 201, compte EN_ATTENTE.
        mvc.perform(multipart("/api/auth/register/prmp").file(dataPart).file(arrete).file(cin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.actif").value(false));

        // 2 déclarations (1 existante + 1 proposée) et 2 pièces (arrêté + CIN) enregistrées.
        assertTrue(prmpEntiteDemandeRepository.findByLogin("prmp.v2").size() == 2, "2 déclarations d'entités");
        assertTrue(pieceJointeRepository.findByLogin("prmp.v2").size() == 2, "2 pièces (arrêté + CIN)");

        // L'Administrateur est notifié de l'inscription.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVELLE_INSCRIPTION')]", hasSize(1)));

        // Connexion refusée tant que non validée → 401.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.v2\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isUnauthorized());

        // Aucune entité déclarée (ni existante ni proposée) → 400.
        String sansEntite = "{\"login\":\"prmp.v3\",\"motDePasse\":\"Passw0rd!\",\"idPrmp\":\"PRMP901\","
                + "\"nomPrmp\":\"Rakoto\",\"prenomsPrmp\":\"V3\","
                + "\"arreteNomin\":\"ARR-2026-901\",\"dateNomin\":\"2026-01-01\",\"cin\":\"901901901901\","
                + "\"dateCin\":\"2010-01-01\",\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"v3@prmp.mg\","
                + "\"telPrmp\":\"0340000901\",\"idEntites\":[],\"entitesNonListees\":[]}";
        MockMultipartFile dataSansEntite = new MockMultipartFile("data", "", "application/json",
                sansEntite.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/auth/register/prmp").file(dataSansEntite).file(arrete).file(cin))
                .andExpect(status().isBadRequest());
    }

    /**
     * Les PK des déclarations d'entités venaient d'un compteur local ({@code max(ID_DEMANDE) + 1} puis
     * {@code ++}) : deux inscriptions simultanées lisaient le même maximum et la seconde échouait en
     * violation d'unicité. C'est le site le plus exposé de la série — l'inscription est le SEUL acte du
     * système ouvert à un utilisateur non authentifié, donc le seul dont deux exécutions concurrentes
     * ne supposent aucune coordination préalable entre acteurs.
     *
     * <p>La concurrence n'est pas reproductible sur H2, mais le corollaire du compteur local l'est :
     * une inscription déclare PLUSIEURS entités d'affilée, et une séquence consommée une seule fois
     * puis incrémentée localement resterait en retard sur les lignes écrites — l'inscription suivante
     * réattribuerait les mêmes ids et, {@code save()} sur PK assignée étant un merge, ÉCRASERAIT les
     * déclarations de la première. D'où le décompte global après DEUX inscriptions : 4 déclarations,
     * toutes distinctes. Un retour au compteur local ferait tomber ce total.
     */
    @Test
    @DisplayName("Inscription : PK des déclarations allouées par seq_prmp_entite_demande — deux inscriptions ne s'écrasent pas")
    void inscription_pkServeur_sequenceConsommeeParLigne() throws Exception {
        MockMultipartFile arrete = new MockMultipartFile("arrete", "arrete.pdf", "application/pdf",
                "%PDF-1.4 arrete".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile cin = new MockMultipartFile("cin", "cin.png", "image/png",
                new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 });
        long avant = prmpEntiteDemandeRepository.count();

        // Deux inscriptions successives, deux déclarations chacune (1 existante + 1 proposée).
        for (int k = 1; k <= 2; k++) {
            String data = "{\"login\":\"prmp.seq" + k + "\",\"motDePasse\":\"Passw0rd!\",\"idPrmp\":\"PRMPS0" + k + "\","
                    + "\"nomPrmp\":\"Rakoto\",\"prenomsPrmp\":\"Seq" + k + "\","
                    + "\"arreteNomin\":\"ARR-2026-91" + k + "\",\"dateNomin\":\"2026-01-01\","
                    + "\"cin\":\"91" + k + "91" + k + "91" + k + "91" + k + "\",\"dateCin\":\"2010-01-01\","
                    + "\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"seq" + k + "@prmp.mg\","
                    + "\"telPrmp\":\"034000091" + k + "\",\"idEntites\":[1],"
                    + "\"entitesNonListees\":[{\"libelle\":\"Autorite " + k + "\",\"adresse\":\"Adr\",\"idLocalite\":\"ANT\"}]}";
            mvc.perform(multipart("/api/auth/register/prmp")
                    .file(new MockMultipartFile("data", "", "application/json", data.getBytes(StandardCharsets.UTF_8)))
                    .file(arrete).file(cin))
                    .andExpect(status().isCreated());
        }

        // 2 + 2 déclarations bien présentes : aucune n'a été écrasée par la seconde inscription.
        org.junit.jupiter.api.Assertions.assertEquals(avant + 4, prmpEntiteDemandeRepository.count(),
                "déclarations écrasées par un compteur local");
        org.junit.jupiter.api.Assertions.assertEquals(2, prmpEntiteDemandeRepository.findByLogin("prmp.seq1").size());
        org.junit.jupiter.api.Assertions.assertEquals(2, prmpEntiteDemandeRepository.findByLogin("prmp.seq2").size());

        // Et les PK viennent de la séquence (plage de test), pas d'un comptage de lignes.
        org.junit.jupiter.api.Assertions.assertTrue(
                prmpEntiteDemandeRepository.findAll().stream().allMatch(d -> d.getIdDemande() >= 1800001),
                "idDemande hors de la plage de seq_prmp_entite_demande");
    }

    @Test
    @DisplayName("Validation inscription : partielle (entité libre activée, conflit signalé, proposée créée) → ACTIF + login")
    void inscription_validationPartielle() throws Exception {
        entiteContractRepository.save(entite(5, 1, "ANT")); // entité libre
        prmpRepository.save(prmp("PRMP900", "ANT"));
        compteAuthRepository.save(new CompteAuth("prmp.val", passwordEncoder.encode("pw"), "PRMP", "PRMP900", false));
        // Déclarations en attente : existante libre (5), existante déjà prise (1 = PRMP001 dans le seed), proposée.
        prmpEntiteDemandeRepository.save(demande(9001, "prmp.val", 5, null));
        prmpEntiteDemandeRepository.save(demande(9002, "prmp.val", 1, null));
        prmpEntiteDemandeRepository.save(demande(9003, "prmp.val", null, "Nouvelle Autorite"));

        // Lecture réservée à l'Admin.
        mvc.perform(get("/api/inscriptions/en-attente").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/inscriptions/en-attente").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.login=='prmp.val')]", hasSize(1)));

        // Validation : on accepte l'entité proposée (9003) avec un organigramme existant (1).
        String body = "{\"entitesProposees\":[{\"idDemande\":9003,\"accepter\":true,\"idOrganigramme\":1}]}";
        mvc.perform(post("/api/inscriptions/prmp.val/valider").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutCompte").value("ACTIF"))
                .andExpect(jsonPath("$.validees.length()").value(2))    // entité 5 + entité proposée créée
                .andExpect(jsonPath("$.conflits.length()").value(1));   // entité 1 déjà rattachée

        // Compte activé → login OK (rôle PRMP, sans localité).
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.val\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRMP"))
                .andExpect(jsonPath("$.localite").doesNotExist());

        // 2 affectations actives pour PRMP900 (entité 5 + entité proposée).
        assertTrue(prmpEntiteRepository.findByIdPrmpAndActifTrue("PRMP900").size() == 2, "2 affectations actives");
    }

    @Test
    @DisplayName("Refus inscription : REFUSE + motif, login refusé, réservé Admin")
    void inscription_refus() throws Exception {
        prmpRepository.save(prmp("PRMP901", "ANT"));
        compteAuthRepository.save(new CompteAuth("prmp.ref", passwordEncoder.encode("pw"), "PRMP", "PRMP901", false));
        prmpEntiteDemandeRepository.save(demande(9100, "prmp.ref", 1, null));
        String body = "{\"motif\":\"Arrêté de nomination non conforme\"}";

        // Refus réservé à l'Admin.
        mvc.perform(post("/api/inscriptions/prmp.ref/refuser").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // L'Admin refuse → 204.
        mvc.perform(post("/api/inscriptions/prmp.ref/refuser").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        // Login toujours refusé (compte non activé).
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.ref\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Notifications : /mes scopé, comptage non-lues, marquer lu (refus si pas la mienne), liste globale Admin-only")
    void notifications_meScopeLectureGlobalAdmin() throws Exception {
        // 2 notifications pour CTRMEM, 1 pour CTRPRE. Les ids sont RELUS sur les entités renvoyées :
        // la PK vient de seq_notification, elle n'est plus 1/2/3 et ne doit plus être devinée ici.
        var n1 = notificationService.emettreControleur(TypeNotification.PRET_DISPATCH, "CTRMEM", null, 1, TypeObjet.DOSSIER, 1, "Notif 1", "corps");
        notificationService.emettreControleur(TypeNotification.PRET_DISPATCH, "CTRMEM", null, 2, TypeObjet.DOSSIER, 2, "Notif 2", "corps");
        var n3 = notificationService.emettreControleur(TypeNotification.PRET_DISPATCH, "CTRPRE", null, 3, TypeObjet.DOSSIER, 1, "Notif 3", "corps");

        // Scoping : CTRMEM voit ses 2, CTRPRE voit sa 1.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.length()").value(1));

        // Comptage des non-lues.
        mvc.perform(get("/api/notifications/mes/non-lues/count").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.nonLues").value(2));

        // Marquer la 1re notification de CTRMEM comme lue → lu=true ; le compteur descend à 1.
        mvc.perform(post("/api/notifications/" + n1.getIdNotification() + "/lu").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lu").value(true));
        mvc.perform(get("/api/notifications/mes/non-lues/count").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.nonLues").value(1));

        // Marquer la notification de CTRPRE en tant que CTRMEM → 403.
        mvc.perform(post("/api/notifications/" + n3.getIdNotification() + "/lu").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // Tout marquer lu (CTRMEM) → 1 restante traitée, puis 0 non-lue.
        mvc.perform(post("/api/notifications/lire-tout").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.traitees").value(1));
        mvc.perform(get("/api/notifications/mes/non-lues/count").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.nonLues").value(0));

        // Liste globale : interdite à un non-Admin (403), autorisée à l'Admin (200).
        mvc.perform(get("/api/notifications").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    /**
     * La PK de notification était allouée par {@code max(ID_NOTIFICATION) + 1}. Une notification est
     * presque toujours émise <em>dans la transaction métier de l'appelant</em> — validation, dispatch,
     * rectification — et le projet ne pose aucun {@code Propagation.REQUIRES_NEW} : deux transitions
     * simultanées lisaient le même maximum, et la violation d'unicité de la seconde annulait l'acte
     * métier lui-même, pas seulement son avis.
     *
     * <p>⚠️ H2 ne rejoue pas la concurrence : ce test ne démontre pas l'absence de collision, il fige
     * l'origine de la PK. {@code seq_notification} rend des valeurs hors de portée d'un comptage de
     * lignes — un retour au {@code max+1} redonnerait 1 et 2 sur une table vide et échouerait ici.
     */
    @Test
    @DisplayName("Notifications : PK allouée par seq_notification — plus de max(ID_NOTIFICATION)+1 dans la transaction de l'appelant")
    void notification_pkServeur_vientDeLaSequence() throws Exception {
        var n1 = notificationService.emettreControleur(TypeNotification.PRET_DISPATCH, "CTRMEM", null,
                1, TypeObjet.DOSSIER, 1, "Notif A", "corps");
        var n2 = notificationService.emettreControleur(TypeNotification.PRET_DISPATCH, "CTRMEM", null,
                2, TypeObjet.DOSSIER, 2, "Notif B", "corps");

        // Plage de seq_notification (START 500001 en test) : sur une table vide, max+1 aurait donné 1 et 2.
        org.junit.jupiter.api.Assertions.assertTrue(n1.getIdNotification() >= 500001,
                "idNotification hors de la plage de seq_notification : " + n1.getIdNotification());
        org.junit.jupiter.api.Assertions.assertTrue(n2.getIdNotification() >= 500001,
                "idNotification hors de la plage de seq_notification : " + n2.getIdNotification());
        org.junit.jupiter.api.Assertions.assertNotEquals(n1.getIdNotification(), n2.getIdNotification());

        // L'id de la ressource exposée est bien celui de la séquence : le GET /mes le rend tel quel.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.titre=='Notif A')].idNotification", hasItem(n1.getIdNotification())));
    }

    @Test
    @DisplayName("Notification message : l'envoi notifie le destinataire (NOUVEAU_MESSAGE, objet MESSAGE), pas l'expéditeur")
    void notification_nouveauMessage() throws Exception {
        // Le Membre envoie un message au CC.
        mvc.perform(post("/api/messages/envoyer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destinataireIm\":\"CTRCC1\",\"sujet\":\"Question\",\"corps\":\"Bonjour\"}"))
                .andExpect(status().isCreated());

        // Le CC (destinataire) reçoit une notification NOUVEAU_MESSAGE pointant l'objet MESSAGE.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVEAU_MESSAGE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVEAU_MESSAGE')].typeObjet", hasItem("MESSAGE")));

        // L'expéditeur (Membre) n'a pas de notification de message.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVEAU_MESSAGE')]", hasSize(0)));
    }

    @Test
    @DisplayName("Notification dispatch : le Membre assigné reçoit EXAMEN_A_FAIRE sur le dossier dispatché")
    void notification_examenAFaire() throws Exception {
        // Dossier PRET_DISPATCH d'ANT avec une réception fraîche.
        dossierRepository.save(dossier(20, "PRET_DISPATCH"));
        receptionRepository.save(reception(40, 20, "CTRSEC", true)); // CTRSEC = localité ANT
        // Le CC d'ANT dispatche le dossier au Membre CTRMEM (titulaire, même localité).
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":50,\"idReception\":40,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());

        // Le Membre assigné reçoit EXAMEN_A_FAIRE pointant le dossier 20.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='EXAMEN_A_FAIRE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='EXAMEN_A_FAIRE')].idObjet", hasItem(20)));
    }

    @Test
    @DisplayName("Notification PV : la soumission d'un projet de PV notifie le CC et le Président (PV_A_VALIDER, objet PV)")
    void notification_pvAValider() throws Exception {
        // Création d'un PV sur l'examen 1 (chaîne → localité ANT), par le Membre.
        int idPv = creerPvEtLireId(tokenMembre, "{\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}");
        // Soumission du projet → PROJET_SOUMIS.
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"a valider\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));

        // Le CC d'ANT reçoit PV_A_VALIDER pointant CE PV (objet PV) — id relu, plus deviné.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')].idObjet", hasItem(idPv)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')].typeObjet", hasItem("PV")));
        // Le Président de la CNM aussi.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')]", hasSize(1)));
    }

    @Test
    @DisplayName("Notification navette : retour (PV_A_RECTIFIER) et acceptation (PV_ACCEPTE) notifient le Membre auteur")
    void notification_navettePvAuteur() throws Exception {
        // Création + soumission d'un PV (auteur CTRMEM, localité ANT).
        int idPv = creerPvEtLireId(tokenMembre, "{\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}");
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"v1\"}"))
                .andExpect(status().isOk());

        // Le CC retourne le PV pour rectification → le Membre auteur reçoit PV_A_RECTIFIER (objet PV).
        mvc.perform(post("/api/pv-examens/" + idPv + "/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"commentaire\":\"corriger la synthese\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("EN_RECTIFICATION"));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_RECTIFIER')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_RECTIFIER')].idObjet", hasItem(idPv)));

        // Re-soumission puis acceptation par le CC → le Membre auteur reçoit PV_ACCEPTE.
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"v2\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_ACCEPTE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_ACCEPTE')].idObjet", hasItem(idPv)));
    }

    @Test
    @DisplayName("Statut examen (⚠️ règle déplacée 2026-08-01) : la création laisse le dossier DISPATCHE (brouillon), "
            + "c'est la SOUMISSION de l'examen qui le passe EXAMINE")
    void statut_examenAvanceVersExamine() throws Exception {
        dossierRepository.save(dossier(30, "PRET_DISPATCH"));
        receptionRepository.save(reception(60, 30, "CTRSEC", true)); // ANT
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":80,\"idReception\":60,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        // Avant examen : DISPATCHE (à examiner).
        mvc.perform(get("/api/dossiers/30").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("DISPATCHE"));

        // Le Membre crée l'examen (brouillon de progression) → le dossier RESTE DISPATCHE.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":80,\"idDispatch\":80,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/30").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("DISPATCHE"));

        // La soumission de l'examen (projet de PV) fait passer le dossier EXAMINE.
        mvc.perform(post("/api/examens/80/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/30").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("EXAMINE"));

        // Exclusivité : présent en ?statut=EXAMINE, absent de ?statut=DISPATCHE.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident).param("statut", "EXAMINE"))
                .andExpect(jsonPath("$[?(@.idDossier==30)]", hasSize(1)));
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident).param("statut", "DISPATCHE"))
                .andExpect(jsonPath("$[?(@.idDossier==30)]", hasSize(0)));
    }

    @Test
    @DisplayName("Statut examen : signer le PV (favorable avec réserves) fait passer le dossier EXAMINE → EN_VERIFICATION")
    void statut_signaturePvAvanceVersPvSigne() throws Exception {
        // Dossier 1 = EXAMINE (seed). PV FAVR (≥ 1 observation requise) sur l'examen 1, soumis, accepté, co-signé.
        ajouterObservationExamen1();
        int idPv = creerPvEtLireId(tokenMembre, "{\"idExamen\":1,\"imCtrlMembre\":\"CTRMEM\","
                + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}");
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAVR\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("SIGNE"));

        // Le dossier 1 (avis FAVR) est passé EXAMINE → EN_VERIFICATION.
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
    }

    /**
     * POST /api/pv-examens et RELIT l'{@code idPv} attribué par le serveur.
     *
     * <p>Depuis que la PK vient de {@code seq_pv_examen}, l'{@code idPv} du corps est IGNORÉ : le
     * conserver comme clé pour la suite du circuit viserait un PV inexistant (404) — ou pire, sur une
     * base peuplée, le PV d'un autre examen. Tous les helpers de PV lisent donc l'id renvoyé.
     */
    private int creerPvEtLireId(String token, String corps) throws Exception {
        String r = mvc.perform(post("/api/pv-examens").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(r, "$.idPv");
    }

    /**
     * Crée un PV avec l'avis donné sur l'examen 1 (dossier 1) et le porte à SIGNE (Membre + Président).
     * Rend l'{@code idPv} RÉELLEMENT attribué par {@code seq_pv_examen} — l'appelant ne peut plus le
     * choisir, et ne doit donc plus le supposer.
     */
    private int signerPvAvecAvis(String avis) throws Exception {
        // ⚠️ Cohérence avis ↔ observations (2026-08-01) : FAVR exige ≥ 1 observation à l'examen.
        if ("FAVR".equals(avis)) {
            ajouterObservationExamen1();
        }
        int idPv = creerPvEtLireId(tokenMembre, "{\"idExamen\":1,\"imCtrlMembre\":\"CTRMEM\","
                + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}");
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        // ⚠️ Clôture de navette (2026-08-01) : l'acceptation pose l'avis global + le secrétaire de séance.
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"" + avis
                        + "\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("SIGNE"));
        return idPv;
    }

    /** Pose une observation (point non conforme) sur l'examen 1 — pré-requis d'un avis FAVR (cohérence 2026-08-01). */
    private void ajouterObservationExamen1() {
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

    @Test
    @DisplayName("Branchement signature (⚠️ 2026-08-02) — avis FAVORABLE (FAV) → dossier EN_VERIFICATION + PRMP PV_SIGNE + vérificateur DECISION_A_TRANSMETTRE")
    void signature_avisFavorable_clotureAuto() throws Exception {
        signerPvAvecAvis("FAV");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='DECISION_A_TRANSMETTRE')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Branchement signature (⚠️ 2026-08-02) — avis DÉFAVORABLE (DEF) → dossier EN_VERIFICATION + PRMP PV_SIGNE + vérificateur DECISION_A_TRANSMETTRE")
    void signature_avisDefavorable_clotureAuto() throws Exception {
        signerPvAvecAvis("DEF");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='DECISION_A_TRANSMETTRE')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Branchement signature (⚠️ 2026-08-02) — avis NE SE PRONONCE PAS (NSP) → dossier EN_VERIFICATION (idem DEF) + notifs PRMP + vérificateur")
    void signature_avisNeSePrononce_clotureAuto() throws Exception {
        signerPvAvecAvis("NSP");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='DECISION_A_TRANSMETTRE')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Branchement signature — avis FAVORABLE AVEC RÉSERVE (FAVR) → dossier EN_VERIFICATION + vérificateur PV_A_VERIFIER + PRMP PV_SIGNE")
    void signature_avisReserve_enVerification() throws Exception {
        signerPvAvecAvis("FAVR");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VERIFIER')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Verrou examen : modifiable tant que EXAMINE, verrouillé (409) dès la signature du PV")
    void verrou_examenJusquaSignature() throws Exception {
        // Dossier 1 = EXAMINE (seed) : l'examen 1 est modifiable.
        mvc.perform(put("/api/examens/1").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1,\"idDispatch\":1,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isOk());

        // Signer le PV (FAV) de l'examen 1 → dossier auto-clôturé (CLOTURE), examen définitif.
        int idPv = creerPvEtLireId(tokenMembre, "{\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}");
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isOk());

        // Examen verrouillé (dossier ≠ EXAMINE) : update de l'examen et écriture d'un détail → 409.
        mvc.perform(put("/api/examens/1").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1,\"idDispatch\":1,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":500,\"idExamen\":1,\"idPtControle\":1,\"conforme\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Autorisation examen : réservé au Membre attributaire ; un autre Membre → 403 ; CC par délégation → OK")
    void autorisation_examenReserveeAttributaire() throws Exception {
        // Dossier dispatché au Membre CTRMEM.
        dossierRepository.save(dossier(40, "PRET_DISPATCH"));
        receptionRepository.save(reception(70, 40, "CTRSEC", true)); // ANT
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":90,\"idReception\":70,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());

        // Un AUTRE Membre d'ANT (non attributaire) → 403.
        String tokenAutreMembre = bearer("CTRMEM2", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM2", "ANT");
        mvc.perform(post("/api/examens").header("Authorization", tokenAutreMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":90,\"idDispatch\":90,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isForbidden());

        // Le Membre attributaire (CTRMEM) → 201.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":90,\"idDispatch\":90,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());

        // Délégation : le CC peut instruire l'examen à la place d'un Membre de sa localité → 201.
        dossierRepository.save(dossier(41, "PRET_DISPATCH"));
        receptionRepository.save(reception(71, 41, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":91,\"idReception\":71,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/examens").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":91,\"idDispatch\":91,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Listes Membre : à-examiner (DISPATCHE assignés) et examinés (paginé) scopés à l'attributaire")
    void listes_membreScopeesAttributaire() throws Exception {
        controleurRepository.save(controleur("CTRMEM2", 5, "ANT")); // 2e Membre d'ANT
        // Dossier 50 DISPATCHE assigné à CTRMEM ; dossier 51 DISPATCHE assigné à CTRMEM2.
        dossierRepository.save(dossier(50, "DISPATCHE"));
        receptionRepository.save(reception(80, 50, "CTRSEC", true));
        dispatchRepository.save(dispatch(95, 80, "CTRCC1", "CTRMEM"));
        dossierRepository.save(dossier(51, "DISPATCHE"));
        receptionRepository.save(reception(81, 51, "CTRSEC", true));
        dispatchRepository.save(dispatch(96, 81, "CTRCC1", "CTRMEM2"));

        // à-examiner de CTRMEM : son dossier 50, pas celui de l'autre Membre (51).
        mvc.perform(get("/api/dossiers/a-examiner").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==50)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==51)]", hasSize(0)));

        // CTRMEM examine son dossier 50 puis SOUMET l'examen (⚠️ 2026-08-01 : la transition
        // DISPATCHE → EXAMINE se fait à la soumission, plus à la création).
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":95,\"idDispatch\":95,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/examens/95/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());

        // Exclusivité : 50 quitte à-examiner et entre dans examinés (paginé → $.content).
        mvc.perform(get("/api/dossiers/a-examiner").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idDossier==50)]", hasSize(0)));
        mvc.perform(get("/api/dossiers/examines").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.idDossier==50)]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.idDossier==51)]", hasSize(0)));
    }

    /** Positionne le contexte de sécurité sur un JWT du profil donné (test direct de la garde centrale). */
    private void authentifierProfil(ProfilUtilisateur profil) {
        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
                .withTokenValue("test").header("alg", "HS256").subject("test")
                .claim("role", profil.name()).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
    }

    @Test
    @DisplayName("Délégation ascendante — matrice complète : titulaire + les 9 paires de la table, rien d'autre")
    void delegation_matricePaires() {
        java.util.List<ProfilUtilisateur> hierarchie = java.util.List.of(
                ProfilUtilisateur.PRESIDENT, ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.CHEF_COMMISSION,
                ProfilUtilisateur.MEMBRE, ProfilUtilisateur.VERIFICATEUR, ProfilUtilisateur.ASSISTANT_CONTROLEUR);
        java.util.Map<ProfilUtilisateur, java.util.Set<ProfilUtilisateur>> paires = java.util.Map.of(
                ProfilUtilisateur.PRESIDENT, java.util.Set.of(
                        ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.CHEF_COMMISSION, ProfilUtilisateur.MEMBRE,
                        ProfilUtilisateur.VERIFICATEUR, ProfilUtilisateur.ASSISTANT_CONTROLEUR),
                ProfilUtilisateur.CHEF_COMMISSION, java.util.Set.of(
                        ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.MEMBRE,
                        ProfilUtilisateur.VERIFICATEUR, ProfilUtilisateur.ASSISTANT_CONTROLEUR));
        java.util.List<ProfilUtilisateur> tousProfils = java.util.List.of(
                ProfilUtilisateur.PRESIDENT, ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.CHEF_COMMISSION,
                ProfilUtilisateur.MEMBRE, ProfilUtilisateur.VERIFICATEUR, ProfilUtilisateur.ASSISTANT_CONTROLEUR,
                ProfilUtilisateur.PRMP, ProfilUtilisateur.CHARGE_PUBLICATION, ProfilUtilisateur.ADMINISTRATEUR);
        try {
            // Chaque profil exerce SES tâches ; Président les 5 subordonnés ; CC les 4 ; PERSONNE d'autre
            // (négatifs : Secrétaire, Membre, Vérificateur, Assistant, PRMP, Chargé de publication,
            // Administrateur n'exercent la tâche d'aucun autre) — aucune paire hors table.
            for (ProfilUtilisateur courant : tousProfils) {
                authentifierProfil(courant);
                for (ProfilUtilisateur cible : hierarchie) {
                    boolean attendu = courant == cible
                            || paires.getOrDefault(courant, java.util.Set.of()).contains(cible);
                    org.junit.jupiter.api.Assertions.assertEquals(attendu,
                            permissionService.peutExercer(cible.name()), courant + " -> " + cible);
                }
            }
            // ⚠️ Anti-régression : LE cas qu'un modèle de rang casserait — le CC est SOUS le Secrétaire
            // dans la hiérarchie (Président > Secrétaire > CC > ...) mais hérite de ses droits parce que
            // la paire CC → Secrétaire est LISTÉE dans la table. Jamais de comparaison de rangs.
            authentifierProfil(ProfilUtilisateur.CHEF_COMMISSION);
            assertTrue(permissionService.peutExercer("SECRETAIRE"));
            // Non transitif et pas de réciprocité de rang : le Secrétaire (au-dessus du CC) n'exerce rien d'autre.
            authentifierProfil(ProfilUtilisateur.SECRETAIRE);
            assertFalse(permissionService.peutExercer("CHEF_COMMISSION"));
            assertFalse(permissionService.peutExercer("MEMBRE"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Délégation — recette : désactiver une paire (actif=false) retire l'habilitation sans changement "
            + "de code ; la réactiver la rend")
    void delegation_recette_toggleActif() throws Exception {
        // Président crée une lettre de renvoi — tâche du CC, exercée via la paire 7 (Président → CC).
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isCreated());
        // L'Admin DÉSACTIVE la paire → l'habilitation disparaît (403), sans aucun changement de code.
        mvc.perform(put("/api/delegation-profils/7").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":7,\"idProfileDelegant\":2,\"idProfileDelegue\":3,\"actif\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isForbidden());
        // Le CC titulaire reste habilité (la désactivation ne touche que la paire Président → CC).
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isCreated());
        // RÉACTIVATION → l'habilitation revient.
        mvc.perform(put("/api/delegation-profils/7").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":7,\"idProfileDelegant\":2,\"idProfileDelegue\":3,\"actif\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Délégation — unicité : une seule ligne par paire (délégant, délégué), doublon rejeté")
    void delegation_unicitePaire() {
        // La paire Président (2) → Secrétaire (4) existe déjà (id 1) : un doublon viole UQ_DELEGATION_PAIRE.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> delegationProfilRepository.saveAndFlush(delegation(90, 2, 4)));
    }

    @Test
    @DisplayName("Délégation ascendante — auto-attribution du Président (circuit court) : dispatch à soi-même (201), "
            + "examen par lui-même, puis signature COMPLÈTE par lui seul — part Membre (attributaire) ET part "
            + "Président (⚠️ décision produit 2026-08-15 : verrou d'auto-co-signature levé, paire → Membre active)")
    void delegation_autoAttributionPresident_circuitCourt() throws Exception {
        dossierRepository.save(dossier(4601, "PRET_DISPATCH"));
        receptionRepository.save(reception(5601, 4601, "CTRSEC", true)); // ANT
        // Le Président se dispatche le dossier à LUI-MÊME : attributaire couvert par la paire
        // Président → Membre (active) → 201. Le CC d'ANT est associé automatiquement.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5601,\"idReception\":5601,\"imCtrlMembre\":\"CTRPRE\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRPRE"));
        // Il examine lui-même (examen réservé à l'attributaire — c'est lui, §2.4) puis soumet.
        mvc.perform(post("/api/examens").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":5601,\"idDispatch\":5601,\"imCtrlMembre\":\"CTRPRE\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/examens/5601/soumettre").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        // Projet de PV : l'attributaire est DÉRIVÉ du dispatch (= CTRPRE), navette classique via le CC.
        String projet = mvc.perform(post("/api/pv-examens").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":5601,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRPRE\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRPRE"))
                .andReturn().getResponse().getContentAsString();
        int idPv = com.jayway.jsonpath.JsonPath.read(projet, "$.idPv");
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk());
        // Signature de la part MEMBRE par le Président attributaire : OK (acte d'identité — il est l'attributaire).
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRPRE"));
        // La part PRESIDENT par la MÊME personne (⚠️ décision produit 2026-08-15) : le verrou
        // d'auto-co-signature est levé pour un signataire couvert par la paire → Membre active —
        // le Président clôt SEUL la signature du PV (deux actions successives).
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.imCtrlPresident").value("CTRPRE"));
    }

    @Test
    @DisplayName("Dispatch — garde de l'attributaire : Secrétaire (aucune paire → Membre) → 409 ; matricule inconnu "
            + "→ 409 ; CC refusé quand la paire CC → Membre est désactivée, accepté quand elle est réactivée "
            + "(data-driven) ; même garde au PUT")
    void dispatch_gardeAttributaireMembre() throws Exception {
        dossierRepository.save(dossier(4602, "PRET_DISPATCH"));
        receptionRepository.save(reception(5602, 4602, "CTRSEC", true)); // ANT
        // Secrétaire attributaire : aucune paire Secrétaire → Membre dans la table → dossier inexaminable → 409.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5602,\"idReception\":5602,\"imCtrlMembre\":\"CTRSEC\",\"interimDispatch\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("inexaminable")));
        // Matricule inconnu → refus explicite.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5602,\"idReception\":5602,\"imCtrlMembre\":\"ZZZ999\",\"interimDispatch\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("aucun contrôleur")));
        // L'Admin DÉSACTIVE la paire 5 (CC → Membre) → le CC ne peut plus être attributaire → 409.
        mvc.perform(put("/api/delegation-profils/5").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":5,\"idProfileDelegant\":3,\"idProfileDelegue\":5,\"actif\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5602,\"idReception\":5602,\"imCtrlMembre\":\"CTRCC1\",\"interimDispatch\":false}"))
                .andExpect(status().isConflict());
        // RÉACTIVÉE → le même dispatch (auto-attribution du CC) passe, SANS changement de code.
        mvc.perform(put("/api/delegation-profils/5").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":5,\"idProfileDelegant\":3,\"idProfileDelegue\":5,\"actif\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5602,\"idReception\":5602,\"imCtrlMembre\":\"CTRCC1\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRCC1"));
        // Même garde à la correction (PUT) : re-cibler un Secrétaire est refusé.
        mvc.perform(put("/api/dispatchs/5602").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5602,\"idReception\":5602,\"imCtrlMembre\":\"CTRSEC\",\"interimDispatch\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("inexaminable")));
    }

    @Test
    @DisplayName("Dispatch — association CC seulement quand le Président dispatche à un Membre : CC auto-attribué → "
            + "sans imCtrlCc ; CC → Membre → imCtrlCc client ignoré ; Président → Membre → association + copie "
            + "DISPATCH_CC conservées ; Président → lui-même → pas d'association")
    void dispatch_associationCcSelonDispatcheur() throws Exception {
        // 1) Le CC s'auto-dispatche → aucune association CC (une seule apparition : Rôle Membre).
        dossierRepository.save(dossier(4603, "PRET_DISPATCH"));
        receptionRepository.save(reception(5603, 4603, "CTRSEC", true)); // ANT
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5603,\"idReception\":5603,\"imCtrlMembre\":\"CTRCC1\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlCc").value(nullValue()));
        // 2) Le CC dispatche à un Membre, imCtrlCc = lui-même envoyé par le client → IGNORÉ (forcé à null).
        dossierRepository.save(dossier(4604, "PRET_DISPATCH"));
        receptionRepository.save(reception(5604, 4604, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5604,\"idReception\":5604,\"imCtrlCc\":\"CTRCC1\","
                        + "\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlCc").value(nullValue()));
        // Aucune copie DISPATCH_CC émise pour ces deux dispatchs (le CC est l'acteur du dispatch).
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.typeNotif=='DISPATCH_CC')]", hasSize(0)));
        // 3) Président → Membre → comportement conservé : CC de la localité auto-associé + copie DISPATCH_CC.
        dossierRepository.save(dossier(4605, "PRET_DISPATCH"));
        receptionRepository.save(reception(5605, 4605, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5605,\"idReception\":5605,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.typeNotif=='DISPATCH_CC')]", hasSize(1)));
        // 4) Président → LUI-MÊME (auto-attribution) → pas d'association (copie sans objet).
        dossierRepository.save(dossier(4606, "PRET_DISPATCH"));
        receptionRepository.save(reception(5606, 4606, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5606,\"idReception\":5606,\"imCtrlMembre\":\"CTRPRE\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlCc").value(nullValue()));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.typeNotif=='DISPATCH_CC')]", hasSize(1))); // toujours une seule
        // Même règle au PUT : le CC corrige son dispatch en renvoyant imCtrlCc = lui-même → ignoré.
        mvc.perform(put("/api/dispatchs/5604").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5604,\"idReception\":5604,\"imCtrlCc\":\"CTRCC1\","
                        + "\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imCtrlCc").value(nullValue()));
    }

    @Test
    @DisplayName("Reprise — association CC : IM_CTRL_CC effacé quand il désigne l'attributaire (doublon "
            + "auto-attribution) ou le dispatcheur lui-même ; les associations légitimes sont conservées")
    void migration_associationCcInvalide() {
        dossierRepository.save(dossier(4607, "DISPATCHE"));
        receptionRepository.save(reception(5607, 4607, "CTRSEC", true));
        dossierRepository.save(dossier(4608, "DISPATCHE"));
        receptionRepository.save(reception(5608, 4608, "CTRSEC", true));
        dossierRepository.save(dossier(4609, "DISPATCHE"));
        receptionRepository.save(reception(5609, 4609, "CTRSEC", true));
        // Doublon historique : CC auto-attribué ET associé à son propre dispatch (cas 00002/PPM/CNM/2026).
        dispatchRepository.save(dispatch(5607, 5607, "CTRCC1", "CTRCC1"));
        // CC dispatcheur associé à lui-même (copie de son propre dispatch).
        Dispatch avecDispatcheur = dispatch(5608, 5608, "CTRCC1", "CTRMEM");
        avecDispatcheur.setImCtrlDispatch("CTRCC1");
        dispatchRepository.save(avecDispatcheur);
        // Association légitime (Président → Membre, CC tiers) : conservée.
        Dispatch legitime = dispatch(5609, 5609, "CTRCC1", "CTRMEM");
        legitime.setImCtrlDispatch("CTRPRE");
        dispatchRepository.save(legitime);

        new cnm.prs.seed.AssociationCcDispatchMigration(dispatchRepository).run();

        org.junit.jupiter.api.Assertions.assertNull(
                dispatchRepository.findById(5607).orElseThrow().getImCtrlCc(),
                "auto-attribution : l'association CC (doublon Membre+CC) doit être effacée");
        org.junit.jupiter.api.Assertions.assertNull(
                dispatchRepository.findById(5608).orElseThrow().getImCtrlCc(),
                "dispatcheur CC : la copie de son propre dispatch doit être effacée");
        org.junit.jupiter.api.Assertions.assertEquals("CTRCC1",
                dispatchRepository.findById(5609).orElseThrow().getImCtrlCc(),
                "Président → Membre : l'association légitime est conservée");
    }

    @Test
    @DisplayName("Co-signature PV : rôle↔acteur authentifié, identité enregistrée (Membre attributaire + Président réel)")
    void cosignature_authentificationEtIdentite() throws Exception {
        // PV sur examen 1 (Membre CTRMEM), porté à PROJET_ACCEPTE.
        int idPv = creerPvEtLireId(tokenMembre, "{\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}");
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk());

        // Un Membre ne peut PAS falsifier la signature Président → 403.
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isForbidden());
        // Un AUTRE Membre (non attributaire) ne peut pas signer comme MEMBRE → 403.
        String tokenAutreMembre = bearer("CTRMEM2", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM2", "ANT");
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenAutreMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM2\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isForbidden());

        // Le Membre attributaire signe → reste PROJET_ACCEPTE (le co-signataire manque).
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        // Le Président réel co-signe → SIGNE, identités enregistrées (plus de « — »).
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRMEM"))
                .andExpect(jsonPath("$.imCtrlPresident").value("CTRPRE"));
    }

    @Test
    @DisplayName("Co-signature PV par le CC : CC de la localité OK (identité enregistrée), CC d'une autre localité → 403")
    void cosignature_ccDeLaLocalite() throws Exception {
        int idPv = creerPvEtLireId(tokenMembre, "{\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}");
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());

        // Un CC d'une AUTRE localité (TMS) ne peut pas co-signer un PV d'ANT → 403.
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC2\",\"role\":\"CC\"}"))
                .andExpect(status().isForbidden());
        // Le CC de la localité (ANT) co-signe → SIGNE, identité enregistrée.
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"role\":\"CC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"));
    }

    // ------------------------------------------------------------------
    // Autorisations par profil
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Référentiel : écriture interdite au Membre (403), permise à l'Admin (201)")
    void referentiel_ecritureAdminSeulement() throws Exception {
        String body = "{\"idLocalite\":\"TMS\",\"libelleLocalite\":\"Toamasina\"}";

        mvc.perform(post("/api/localites").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Référentiel : lecture ouverte à tout utilisateur authentifié (200)")
    void referentiel_lectureOuverte() throws Exception {
        mvc.perform(get("/api/localites").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Dispatch : interdit au Membre (403)")
    void dispatch_interditAuMembre() throws Exception {
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":9,\"idReception\":1,\"interimDispatch\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("INTERIM_DISPATCH : titulaire dans sa localité (false), intérim hors localité (true)")
    void interimDispatch_conditionnel() throws Exception {
        // Les gardes du dispatch exigent un dossier PRET_DISPATCH et une réception sans dispatch.
        // On prépare des dossiers PRET_DISPATCH avec une réception dédiée chacun (ANT et TMS).
        dossierRepository.save(dossier(10, "PRET_DISPATCH"));
        dossierRepository.save(dossier(11, "PRET_DISPATCH"));
        dossierRepository.save(dossier(12, "PRET_DISPATCH"));
        dossierRepository.save(dossier(13, "PRET_DISPATCH"));
        receptionRepository.save(reception(20, 10, "CTRSEC", true)); // ANT (CTRSEC = localité ANT)
        receptionRepository.save(reception(21, 11, "CTRSEC", true)); // ANT
        receptionRepository.save(reception(22, 12, "CTRCC2", true)); // TMS (CTRCC2 = localité TMS)
        receptionRepository.save(reception(23, 13, "CTRCC2", true)); // TMS

        // Cas conformes (CC d'ANT) — réceptions fraîches.
        // Dossier d'ANT en titulaire.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":30,\"idReception\":20,\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        // Dossier de TMS en intérim.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":31,\"idReception\":22,\"interimDispatch\":true}"))
                .andExpect(status().isCreated());

        // Cas non conformes (409) — la précondition passe (réceptions fraîches PRET_DISPATCH),
        // c'est la règle d'intérim qui bloque.
        // CC dans sa localité mais marqué intérim.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":32,\"idReception\":21,\"interimDispatch\":true}"))
                .andExpect(status().isConflict());
        // CC hors de sa localité mais marqué titulaire.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":33,\"idReception\":23,\"interimDispatch\":false}"))
                .andExpect(status().isConflict());
        // Le Président dispatche toujours en titulaire.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":34,\"idReception\":21,\"interimDispatch\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("KPIs : tableau de bord (pipeline + taux de conformité) réservé Président/Admin")
    void kpis_tableauBord() throws Exception {
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbDossiersSoumis").value(2))
                .andExpect(jsonPath("$.nbDossiersConformes").value(0))
                .andExpect(jsonPath("$.tauxConformitePct").value(0.0))
                .andExpect(jsonPath("$.pipelineParStatut.EXAMINE").value(2))
                .andExpect(jsonPath("$.topNonConformite").isArray());
        // Réservé : un Membre n'accède pas aux KPIs globaux.
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Rapport PDF : généré pour le Président, interdit au Membre")
    void rapport_dossiersPdf() throws Exception {
        byte[] pdf = mvc.perform(get("/api/rapports/dossiers").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 100, "le PDF doit être non vide");
        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).equals("%PDF"), "en-tête PDF attendu");

        // Un Membre ne peut pas générer le rapport (réservé Président / Admin).
        mvc.perform(get("/api/rapports/dossiers").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Export Excel : .xlsx généré pour le Président, interdit au Membre")
    void rapport_dossiersExcel() throws Exception {
        byte[] xlsx = mvc.perform(get("/api/rapports/dossiers/excel").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(xlsx.length > 100, "le classeur doit être non vide");
        assertTrue(xlsx[0] == 'P' && xlsx[1] == 'K', "signature ZIP/xlsx attendue (PK)");

        mvc.perform(get("/api/rapports/dossiers/excel").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Rapport par localité : CC autorisé (forcé sur sa localité), Président peut cibler une localité")
    void rapport_parLocalite() throws Exception {
        // Le Chef de commission peut désormais générer le rapport : il est forcé sur sa propre localité (ANT).
        byte[] pdfCc = mvc.perform(get("/api/rapports/dossiers").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(new String(pdfCc, 0, 4, StandardCharsets.US_ASCII).equals("%PDF"), "en-tête PDF attendu");

        // Idem en Excel.
        byte[] xlsxCc = mvc.perform(get("/api/rapports/dossiers/excel").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(xlsxCc[0] == 'P' && xlsxCc[1] == 'K', "signature ZIP/xlsx attendue (PK)");

        // Le Président peut cibler explicitement une localité via ?localite=.
        mvc.perform(get("/api/rapports/dossiers").header("Authorization", tokenPresident).param("localite", "ANT"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Messagerie : envoi, réception, marquage lu et confidentialité")
    void messagerie_envoiReceptionLu() throws Exception {
        // Le Membre envoie un message au CC. L'id est RELU dans la réponse, jamais deviné : la PK vient
        // de seq_message depuis le 2026-08-25, elle ne vaut plus 1 et n'est plus prévisible.
        String envoi = mvc.perform(post("/api/messages/envoyer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destinataireIm\":\"CTRCC1\",\"sujet\":\"Question\",\"corps\":\"Bonjour\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expediteurIm").value("CTRMEM"))
                .andExpect(jsonPath("$.destinataireIm").value("CTRCC1"))
                .andExpect(jsonPath("$.lu").value(false))
                .andReturn().getResponse().getContentAsString();
        int idMsg = com.jayway.jsonpath.JsonPath.read(envoi, "$.idMessage");
        org.junit.jupiter.api.Assertions.assertTrue(idMsg >= 1200001, "idMessage hors de seq_message : " + idMsg);

        // Boîte de réception du CC : 1 message ; envoyés du Membre : 1.
        mvc.perform(get("/api/messages/recus").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sujet").value("Question"));
        mvc.perform(get("/api/messages/envoyes").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        // Le CC marque le message comme lu.
        mvc.perform(post("/api/messages/" + idMsg + "/lu").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lu").value(true));

        // L'expéditeur (non destinataire) ne peut pas marquer lu → 403.
        mvc.perform(post("/api/messages/" + idMsg + "/lu").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // Un tiers ne peut pas lire le message (confidentialité) → 403.
        mvc.perform(get("/api/messages/" + idMsg).header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Audit automatique : une écriture API est tracée dans t_audit_log (§3.8)")
    void audit_traceLesEcritures() throws Exception {
        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLocalite\":\"TMS\",\"libelleLocalite\":\"Toamasina\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nomTable").value("localites"))
                .andExpect(jsonPath("$[0].typeAction").value("CREATE"))
                .andExpect(jsonPath("$[0].imActeur").value("CTRADM"));
    }

    /**
     * L'id du journal était alloué par {@code max(ID_LOG) + 1}, lu <em>dans la transaction métier de
     * l'appelant</em> — le projet ne pose aucun {@code Propagation.REQUIRES_NEW}. Deux écritures
     * concurrentes lisaient donc le même maximum et inséraient la même PK : la violation d'unicité de
     * la seconde annulait toute la transaction métier, et l'utilisateur voyait son dossier non validé
     * pour un message de doublon qui ne décrivait pas son action.
     *
     * <p>⚠️ Ce test ne prouve pas l'absence de collision : H2 en transaction unique ne reproduit ni les
     * séquences PostgreSQL sous charge ni les SQLSTATE réels. Il verrouille ce qui reste observable et
     * qui suffit à empêcher le retour en arrière : la PK vient de {@code seq_audit_log}, elle n'est
     * plus une fonction du contenu de la table. Un {@code max+1} redonnerait 1 puis 2 sur une table
     * vide — l'assertion de plage échouerait aussitôt.
     */
    @Test
    @DisplayName("Audit : PK du journal allouée par seq_audit_log — plus de max(ID_LOG)+1 dans la transaction de l'appelant")
    void audit_pkServeur_vientDeLaSequence() throws Exception {
        // Deux écritures API tracées par l'intercepteur → deux entrées de journal.
        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLocalite\":\"TMS\",\"libelleLocalite\":\"Toamasina\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLocalite\":\"FIA\",\"libelleLocalite\":\"Fianarantsoa\"}"))
                .andExpect(status().isCreated());

        String journal = mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        int id1 = com.jayway.jsonpath.JsonPath.read(journal, "$[0].idLog");
        int id2 = com.jayway.jsonpath.JsonPath.read(journal, "$[1].idLog");
        // Plage de seq_audit_log (START 400001 en test) : sur une table vide, max+1 aurait donné 1 et 2.
        org.junit.jupiter.api.Assertions.assertTrue(id1 >= 400001, "idLog hors de la plage de seq_audit_log : " + id1);
        org.junit.jupiter.api.Assertions.assertTrue(id2 >= 400001, "idLog hors de la plage de seq_audit_log : " + id2);
        org.junit.jupiter.api.Assertions.assertNotEquals(id1, id2);
    }

    /**
     * Le journal d'audit est la pièce probante du contrôle : sans cette garde, un administrateur
     * pouvait, après une action litigieuse, réécrire l'entrée qui l'atteste et l'attribuer à un tiers
     * en remplaçant {@code imActeur} — la substitution ne laissant elle-même aucune trace, puisque
     * l'intercepteur ne journalise que le nom de la table, pas les valeurs réécrites. Le verbe reste
     * routé mais refuse explicitement (409, §3.8), au même titre que la suppression.
     */
    @Test
    @DisplayName("Audit immuable (§3.8) : PUT sur une entrée du journal est refusé — imActeur non réattribuable")
    void audit_modificationInterdite() throws Exception {
        // Une écriture réussie produit l'entrée n°1, imputée à l'administrateur qui l'a faite.
        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLocalite\":\"TMS\",\"libelleLocalite\":\"Toamasina\"}"))
                .andExpect(status().isCreated());

        // L'id de l'entrée est LU dans le journal, jamais deviné : depuis que la PK vient de
        // seq_audit_log, un « 1 » codé en dur ne désignerait plus aucune ligne — le 409 serait alors
        // rendu sur une entrée inexistante et ne prouverait plus que l'entrée réelle est protégée.
        String journal = mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int idLog = com.jayway.jsonpath.JsonPath.read(journal, "$[0].idLog");

        // Tentative de réattribution de l'action à un tiers (CTRMEM) — refusée avant toute écriture.
        mvc.perform(put("/api/audit-logs/" + idLog).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLog\":" + idLog + ",\"dateAction\":\"2026-01-01T00:00:00\",\"imActeur\":\"CTRMEM\","
                        + "\"nomTable\":\"localites\",\"typeAction\":\"CREATE\"}"))
                .andExpect(status().isConflict());

        // L'entrée est intacte : toujours une seule, toujours imputée à CTRADM.
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].imActeur").value("CTRADM"));
    }

    /**
     * Une entrée d'audit est <em>constatée</em> par le serveur, jamais <em>déclarée</em> par un client :
     * l'unique voie d'écriture est {@code AuditLogService#enregistrer}, qui prend {@code imActeur} du
     * principal courant. Le POST du CRUD générique laissait au contraire le client choisir l'acteur,
     * la date et la table — de quoi fabriquer une preuve au nom d'un tiers. Écriture refusée (409, §3.8).
     */
    @Test
    @DisplayName("Audit immuable (§3.8) : POST sur le journal est refusé — pas d'entrée forgée au nom d'un tiers")
    void audit_creationParApiInterdite() throws Exception {
        mvc.perform(post("/api/audit-logs").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLog\":99,\"dateAction\":\"2026-01-01T00:00:00\",\"imActeur\":\"CTRMEM\","
                        + "\"nomTable\":\"dossiers\",\"typeAction\":\"DELETE\"}"))
                .andExpect(status().isConflict());

        // Rien n'a été écrit — ni l'entrée forgée, ni une trace de la tentative (refus = pas d'écriture).
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Module 10 : écriture comptes/hiérarchie réservée Admin, lecture ouverte, sessions Admin-only")
    void module10_gestionComptes() throws Exception {
        // Création d'un contrôleur interdite au Membre (403), avant même la validation.
        mvc.perform(post("/api/controleurs").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imControleur\":\"CTRX\",\"idProfile\":5,\"transversal\":false}"))
                .andExpect(status().isForbidden());
        // Lecture des contrôleurs ouverte (l'UI affiche les noms).
        mvc.perform(get("/api/controleurs").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        // Sessions utilisateur : réservées à l'Administrateur (lecture comprise).
        mvc.perform(get("/api/session-utilisateurs").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/session-utilisateurs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Visibilité dossiers : CC (localité ANT) et Président voient le dossier ANT")
    void visibilite_dossiers() throws Exception {
        // Le CC d'ANT ne voit que le dossier d'ANT (1), pas celui de TMS (2).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // Le Président voit toutes les localités (2 dossiers).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        // La PRMP voit ses propres dossiers (lien PPM → dossier).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // Une PRMP sans dossier ne voit rien.
        String tokenAutrePrmp = bearer("PRMPXX", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPXX", "ANT");
        mvc.perform(get("/api/dossiers").header("Authorization", tokenAutrePrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Robustesse PK : création sans identifiant assigné → 400 (au lieu de 500)")
    void pk_idManquant_renvoie400() throws Exception {
        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"libelleLocalite\":\"X\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Délégation : tâche déléguée exécutable par le titulaire ou un profil délégué, sinon 403")
    void delegation_tachesDelegables() throws Exception {
        String body = "{\"idDossier\":1,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":false}";
        // Secrétaire titulaire : autorisé.
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        mvc.perform(put("/api/receptions/1").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        // CC délégué (délégation Secrétaire → CC active) : autorisé.
        mvc.perform(put("/api/receptions/1").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        // Membre sans délégation pour la réception : interdit.
        mvc.perform(put("/api/receptions/1").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Délégation limitée à la localité : agir sur un dossier d'une autre localité → 403")
    void delegation_contrainteLocalite() throws Exception {
        // Le Président (toutes localités) peut agir sur le dossier 2 (TMS).
        mvc.perform(post("/api/receptions").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":2,\"numPassage\":2,\"typePassage\":\"RETOUR\","
                        + "\"imCtrlRecept\":\"CTRPRE\",\"complet\":false}"))
                .andExpect(status().isCreated());
        // Le CC d'ANT, même délégué, ne peut pas agir sur un dossier de TMS → 403.
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":2,\"numPassage\":2,\"typePassage\":\"RETOUR\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Filtre localité étendu : réceptions/dispatch/examen limités à la localité")
    void filtreLocalite_etendu() throws Exception {
        // Réceptions : CC d'ANT n'en voit qu'une (ANT), le Président les deux.
        mvc.perform(get("/api/receptions").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/receptions").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        // Accès direct à une réception hors localité → 403 ; dans la localité → 200.
        mvc.perform(get("/api/receptions/2").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/receptions/1").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        // Dispatch et examen aussi filtrés (seuls ceux d'ANT existent).
        mvc.perform(get("/api/dispatchs").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/examens").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // La PRMP n'accède pas aux ressources internes.
        mvc.perform(get("/api/receptions").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Enregistrement secrétariat : la date de réception comporte l'heure (yyyy-MM-dd HH:mm)")
    void enregistrement_liste_ok() throws Exception {
        // La réception 1 (localité ANT) est seedée à 2026-06-02 10:30.
        mvc.perform(get("/api/receptions").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idReception==1)].dateReception", hasItem("2026-06-02 10:30")));
    }

    @Test
    @DisplayName("Enregistrement secrétariat : dateSoumission présente et non nulle pour un dossier récent")
    void enregistrement_soumission_ok() throws Exception {
        // Dossier récent (ANT) avec une date/heure de soumission, et sa réception (CC ANT).
        Dossier d = dossier(150, "SOUMIS");
        d.setIdLocalite("ANT");
        d.setIdPrmp("PRMP001");
        d.setDateSoumission(LocalDateTime.of(2026, 6, 20, 9, 15));
        dossierRepository.save(d);
        receptionRepository.save(reception(150, 150, "CTRCC1", true));

        mvc.perform(get("/api/receptions/150").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateReception").value("2026-06-02 10:30"))
                .andExpect(jsonPath("$.dateSoumission").value("2026-06-20 09:15"));
    }

    @Test
    @DisplayName("Réception — dateReception « yyyy-MM-dd » sans heure → 201 (plus d'erreur de parsing index 10)")
    void reception_creation_date_simple_ok() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d = dossier(300, "SOUMIS");
        d.setIdLocalite("ANT");
        d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":300,\"numPassage\":1,\"typePassage\":\"INITIAL\",\"complet\":true,"
                        + "\"dateReception\":\"2026-06-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dateReception", org.hamcrest.Matchers.startsWith("2026-06-30")));
    }

    @Test
    @DisplayName("Réception — reference persistée (snapshot immuable) : GET la renvoie et elle survit à la mutation de dossier.refeDossier")
    void reception_reference_persistee_immuable() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d = dossier(330, "SOUMIS");
        d.setIdLocalite("ANT");
        d.setIdTypeDossier("DDP");
        d.setIdSousType("PPM");
        dossierRepository.save(d);

        // POST : la réponse porte la référence structurée <seq>/PPM/CNM/<annee> (segment = sous-type ;
        // dossier de la localité centrale ANT → CNM, ⚠️ règle corrigée 2026-08-04).
        String resp = mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":330,\"numPassage\":1,\"typePassage\":\"INITIAL\",\"complet\":true,"
                        + "\"dateReception\":\"2026-06-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference", org.hamcrest.Matchers.matchesPattern("\\d{5}/PPM/CNM/\\d{4}")))
                .andReturn().getResponse().getContentAsString();
        int idRec = com.jayway.jsonpath.JsonPath.read(resp, "$.idReception");
        String refRecept = com.jayway.jsonpath.JsonPath.read(resp, "$.reference");

        // GET liste : la référence est bien PERSISTÉE sur t_reception (plus null).
        mvc.perform(get("/api/receptions").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idReception==" + idRec + ")].reference", hasItem(refRecept)));

        // Mutation de dossier.refeDossier (simule la restauration de la réf PPM après retrait accepté).
        Dossier maj = dossierRepository.findById(330).orElseThrow();
        maj.setRefeDossier("00007/DGB/PPM/2026");
        dossierRepository.save(maj);

        // La référence de la réception ne bouge pas (snapshot immuable, indépendant du dossier).
        mvc.perform(get("/api/receptions/" + idRec).header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(refRecept))
                .andExpect(jsonPath("$.reference", org.hamcrest.Matchers.not("00007/DGB/PPM/2026")));
    }

    @Test
    @DisplayName("Réception — parsing : date simple → 30/06/2026 (heure complétée) ; date-heure préservée")
    void reception_date_stockee_correctement() {
        // Date seule « yyyy-MM-dd » : jour correct, heure complétée par le serveur (non nulle).
        java.time.LocalDateTime dSimple = cnm.prs.mapper.ReceptionMapper.toLocalDateTime("2026-06-30");
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDate.of(2026, 6, 30), dSimple.toLocalDate());
        // Une date-heure complète « yyyy-MM-dd HH:mm » reste correctement parsée (heure conservée).
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDateTime.of(2026, 6, 30, 14, 30),
                cnm.prs.mapper.ReceptionMapper.toLocalDateTime("2026-06-30 14:30"));
    }

    @Test
    @DisplayName("Dispatch — dateDispatch « yyyy-MM-dd » sans heure → 201 (heure complétée, plus d'erreur index 10)")
    void dispatch_date_simple_acceptee() throws Exception {
        dossierRepository.save(dossier(310, "PRET_DISPATCH"));
        receptionRepository.save(reception(410, 310, "CTRSEC", true));   // CTRSEC = localité ANT
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":310,\"idReception\":410,\"imCtrlMembre\":\"CTRMEM\","
                        + "\"interimDispatch\":false,\"dateDispatch\":\"2026-06-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dateDispatch", org.hamcrest.Matchers.startsWith("2026-06-30 ")));
    }

    @Test
    @DisplayName("Dispatch — parsing : date simple → 30/06/2026 (heure complétée) ; date-heure préservée")
    void dispatch_date_parsing_ok() {
        java.time.LocalDateTime dSimple = cnm.prs.mapper.DispatchMapper.toLocalDateTime("2026-06-30");
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDate.of(2026, 6, 30), dSimple.toLocalDate());
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDateTime.of(2026, 6, 30, 14, 30),
                cnm.prs.mapper.DispatchMapper.toLocalDateTime("2026-06-30 14:30"));
    }

    @Test
    @DisplayName("Dispatch — la liste exclut les dossiers BROUILLON (dispatch orphelin après retrait accepté)")
    void dispatch_liste_exclut_brouillon() throws Exception {
        // Dossier redevenu BROUILLON mais qui conserve un dispatch (cas du retrait accepté).
        dossierRepository.save(dossier(320, "BROUILLON"));
        receptionRepository.save(reception(420, 320, "CTRSEC", true));   // CTRSEC = ANT
        dispatchRepository.save(dispatch(320, 420, "CTRCC1", "CTRMEM"));
        mvc.perform(get("/api/dispatchs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                // Le dispatch du dossier BROUILLON est exclu ; aucun dossier BROUILLON dans la liste.
                .andExpect(jsonPath("$[?(@.idDispatch==320)]", hasSize(0)));
    }

    @Test
    @DisplayName("Référence dossier — séquence globale unique (2 localités, même année → numéros distincts/consécutifs)")
    void reference_sequence_unique_globale() {
        // Segment affiché = sous-type (PPM) ; clé de compteur = famille (DDP).
        String rAnt = referenceService.generer("PPM", "DDP", "ANT", false, 2099);
        String rTms = referenceService.generer("PPM", "DDP", "TMS", false, 2099);
        // Numéros distincts ET consécutifs malgré des localités différentes (plus de « 00001 » partagé).
        org.junit.jupiter.api.Assertions.assertEquals("00001/PPM/CRM-ANT/2099", rAnt);
        org.junit.jupiter.api.Assertions.assertEquals("00002/PPM/CRM-TMS/2099", rTms);
    }

    @Test
    @DisplayName("Référence dossier — compteur indexé sur la FAMILLE : PPM et PPM-AGPM (même famille DDP) se suivent")
    void reference_compteurFamille_sousTypesMeleés() {
        // Deux sous-types de la même famille DDP → segment distinct mais numérotation CONTINUE (clé = DDP).
        org.junit.jupiter.api.Assertions.assertEquals("00001/PPM/CRM-ANT/2097",
                referenceService.generer("PPM", "DDP", "ANT", false, 2097));
        org.junit.jupiter.api.Assertions.assertEquals("00002/PPM-AGPM/CRM-ANT/2097",
                referenceService.generer("PPM-AGPM", "DDP", "ANT", false, 2097));
        // Une autre famille (DMC) a sa propre séquence, repartant à 1.
        org.junit.jupiter.api.Assertions.assertEquals("00001/DAO/CRM-ANT/2097",
                referenceService.generer("DAO", "DMC", "ANT", false, 2097));
    }

    @Test
    @DisplayName("Référence dossier — incrément strictement croissant sans saut ni doublon (5 dossiers)")
    void reference_sequence_increment_correct() {
        for (int i = 1; i <= 5; i++) {
            org.junit.jupiter.api.Assertions.assertEquals(String.format("%05d/PPM/CRM-ANT/2098", i),
                    referenceService.generer("PPM", "DDP", "ANT", false, 2098));
        }
    }

    /** Examen ANT (circuit via réception CTRSEC) sur un dossier à {@code refeDossier} structuré donné. */
    private int seedExamenAvecRefe(int base, String refeDossier) {
        Dossier d = dossier(base, "EXAMINE");
        d.setIdLocalite("ANT");
        d.setRefeDossier(refeDossier);
        dossierRepository.save(d);
        receptionRepository.save(reception(base, base, "CTRSEC", true));    // circuit ANT
        dispatchRepository.save(dispatch(base, base, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(base, base, "CTRMEM"));
        return base;   // idExamen
    }

    @Test
    @DisplayName("Réf. lettre — 2 lettres du MÊME dossier → numéros distincts (plus de répétition)")
    void lettre_reference_sequence_meme_dossier() throws Exception {
        seedExamenAvecRefe(340, "00007/DDP/CRM-ANT/2096");
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":340}"))
                .andExpect(jsonPath("$.refLettre").value("00001/DDP/CRM-ANT/LR/2096"));
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":340}"))
                .andExpect(jsonPath("$.refLettre").value("00002/DDP/CRM-ANT/LR/2096"));
    }

    @Test
    @DisplayName("Réf. lettre — séquence globale (2 dossiers/localités différents → numéros distincts/consécutifs)")
    void lettre_reference_sequence_unique_globale() throws Exception {
        seedExamenAvecRefe(341, "00001/DDP/CRM-ANT/2097");
        seedExamenAvecRefe(342, "00009/DDP/CRM-TMS/2097");   // dossier différent, localité TMS dans la réf
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":341}"))
                .andExpect(jsonPath("$.refLettre").value("00001/DDP/CRM-ANT/LR/2097"));
        // Numéro de séquence GLOBAL (00002) malgré une localité différente — pas un « 00001 » partagé.
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":342}"))
                .andExpect(jsonPath("$.refLettre").value("00002/DDP/CRM-TMS/LR/2097"));
    }

    @Test
    @DisplayName("Réf. lettre — incrément continu sans saut ni doublon (5 lettres)")
    void lettre_reference_increment_continu() throws Exception {
        seedExamenAvecRefe(343, "00001/DDP/CRM-ANT/2098");
        for (int i = 1; i <= 5; i++) {
            mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":343}"))
                    .andExpect(jsonPath("$.refLettre").value(String.format("%05d/DDP/CRM-ANT/LR/2098", i)));
        }
    }

    @Test
    @DisplayName("Réf. lettre — hérite du segment SOUS-TYPE du refeDossier, tiret compris (PPM-AGPM)")
    void lettre_refLettre_heriteSegmentSousType() throws Exception {
        // refeDossier au nouveau format sous-type (segment PPM-AGPM avec tiret) → la lettre le reprend tel quel.
        seedExamenAvecRefe(344, "00013/PPM-AGPM/CRM-ANT/2095");
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":344}"))
                .andExpect(jsonPath("$.refLettre").value("00001/PPM-AGPM/CRM-ANT/LR/2095"));
    }


    @Test
    @DisplayName("Tableau de bord Président : compteurs de contenu présents (6 sections, valeurs ≥ 0)")
    void dashboard_compteurs_president_ok() throws Exception {
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compteurs").exists())
                .andExpect(jsonPath("$.compteurs.predispatch").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.compteurs.dispatch").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.compteurs.projetsPV").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.compteurs.lettresRenvoi").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.compteurs.pvDefinitifs").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.compteurs.demandesRetrait").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Tableau de bord CC : compteurs filtrés sur sa localité (Président voit le global)")
    void dashboard_compteurs_cc_localite_ok() throws Exception {
        // Un dossier PRET_DISPATCH en ANT, un autre en TMS.
        Dossier ant = dossier(170, "PRET_DISPATCH"); ant.setIdLocalite("ANT"); dossierRepository.save(ant);
        Dossier tms = dossier(171, "PRET_DISPATCH"); tms.setIdLocalite("TMS"); dossierRepository.save(tms);

        // CC d'ANT : ne compte que le dossier de sa localité.
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compteurs.predispatch").value(1));

        // Président : vue globale → compte les deux localités.
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compteurs.predispatch").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("Menu PRMP : compteurs présents (5 sections, valeurs ≥ 0), filtrés sur la PRMP du JWT")
    void dashboard_compteurs_prmp_ok() throws Exception {
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brouillons").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.ppmMarches").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.dossiersARectifier").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.dossiersVerifies").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.lettresRenvoi").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu PRMP : 2 brouillons de la PRMP → brouillons = 2")
    void dashboard_brouillons_ok() throws Exception {
        Dossier b1 = dossier(180, "BROUILLON"); b1.setIdPrmp("PRMP001"); dossierRepository.save(b1);
        Dossier b2 = dossier(181, "BROUILLON"); b2.setIdPrmp("PRMP001"); dossierRepository.save(b2);

        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brouillons").value(2));
    }

    @Test
    @DisplayName("Menu PRMP : dossier EN_ATTENTE_DECISION_PRMP → dossiersARectifier = 1")
    void dashboard_rectifier_ok() throws Exception {
        Dossier d = dossier(182, "EN_ATTENTE_DECISION_PRMP"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);

        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dossiersARectifier").value(1));
    }

    @Test
    @DisplayName("Menu PRMP : lettre SIGNÉE d'un dossier de la PRMP → lettresRenvoi ≥ 1")
    void dashboard_lettres_ok() throws Exception {
        // Le dossier 1 a un PPM de PRMP001 (seed) ; on lui attache une lettre SIGNÉE.
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1); l.setIdDossier(1); l.setObjetLettre("Renvoi"); l.setStatut("SIGNE");
        lettreRenvoiRepository.save(l);

        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Menu vérificateur : compteurs présents (3 sections, valeurs ≥ 0), filtrés sur sa localité")
    void dashboard_compteurs_verificateur_ok() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(get("/api/kpis/mes-compteurs-verificateur").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aVerifier").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.verifies").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.enAttentePrmp").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu vérificateur : dossier EN_VERIFICATION de sa localité → aVerifier = 1")
    void dashboard_verif_aVerifier_ok() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        Dossier d = dossier(190, "EN_VERIFICATION"); dossierRepository.save(d);
        receptionRepository.save(reception(190, 190, "CTRCC1", true)); // réception ANT (CTRCC1)

        mvc.perform(get("/api/kpis/mes-compteurs-verificateur").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aVerifier").value(1))
                .andExpect(jsonPath("$.enAttentePrmp").value(0));
    }

    @Test
    @DisplayName("Menu vérificateur : dossier EN_ATTENTE_DECISION_PRMP → enAttentePrmp = 1 (compté aussi dans aVerifier)")
    void dashboard_verif_enAttentePrmp_ok() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        Dossier d = dossier(191, "EN_ATTENTE_DECISION_PRMP"); dossierRepository.save(d);
        receptionRepository.save(reception(191, 191, "CTRCC1", true)); // réception ANT

        mvc.perform(get("/api/kpis/mes-compteurs-verificateur").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enAttentePrmp").value(1))
                .andExpect(jsonPath("$.aVerifier").value(1));
    }

    @Test
    @DisplayName("Menu secrétaire : compteurs présents (2 sections, valeurs ≥ 0), filtrés sur sa localité")
    void dashboard_compteurs_secretaire_ok() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        mvc.perform(get("/api/kpis/mes-compteurs-secretaire").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aReceptionner").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.receptions").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu secrétaire : dossier SOUMIS sans réception de sa localité → aReceptionner = 1")
    void dashboard_sec_aReceptionner_ok() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d = dossier(200, "SOUMIS"); d.setIdLocalite("ANT"); dossierRepository.save(d); // pas de réception

        mvc.perform(get("/api/kpis/mes-compteurs-secretaire").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aReceptionner").value(1));
    }

    @Test
    @DisplayName("Menu secrétaire : réceptions de sa localité comptées (réception ANT seedée) → receptions ≥ 1")
    void dashboard_sec_receptions_ok() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // La réception 1 (CTRCC1, localité ANT) est seedée dans @BeforeEach.
        mvc.perform(get("/api/kpis/mes-compteurs-secretaire").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receptions").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Menu membre : compteurs présents (2 sections, valeurs ≥ 0), filtrés sur le Membre attributaire")
    void dashboard_compteurs_membre_ok() throws Exception {
        mvc.perform(get("/api/kpis/mes-compteurs-membre").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aExaminer").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.examines").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu membre : dossier DISPATCHE qui lui est attribué → aExaminer = 1")
    void dashboard_membre_aExaminer_ok() throws Exception {
        // Dossier DISPATCHE + réception + dispatch attribué à CTRMEM (le Membre du token).
        Dossier d = dossier(210, "DISPATCHE"); dossierRepository.save(d);
        receptionRepository.save(reception(210, 210, "CTRCC1", true));
        dispatchRepository.save(dispatch(210, 210, "CTRCC1", "CTRMEM"));

        mvc.perform(get("/api/kpis/mes-compteurs-membre").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aExaminer").value(1));
    }

    @Test
    @DisplayName("Menu membre : dossier EXAMINE attribué (seed) → examines ≥ 1")
    void dashboard_membre_examines_ok() throws Exception {
        // Le dossier 1 (EXAMINE) est dispatché à CTRMEM dans @BeforeEach.
        mvc.perform(get("/api/kpis/mes-compteurs-membre").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examines").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Menu chargé de publication : compteurs présents (3 sections, valeurs ≥ 0)")
    void dashboard_compteurs_publication_ok() throws Exception {
        mvc.perform(get("/api/kpis/mes-compteurs-publication").header("Authorization", tokenPublication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aPublier").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.publiees").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.retirees").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu chargé de publication : publication EN_ATTENTE → aPublier = 1")
    void dashboard_pub_aPublier_ok() throws Exception {
        seedPublication(300, "EN_ATTENTE");
        mvc.perform(get("/api/kpis/mes-compteurs-publication").header("Authorization", tokenPublication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aPublier").value(1));
    }

    @Test
    @DisplayName("Menu chargé de publication : publication PUBLIE → publiees = 1")
    void dashboard_pub_publiees_ok() throws Exception {
        seedPublication(301, "PUBLIE");
        mvc.perform(get("/api/kpis/mes-compteurs-publication").header("Authorization", tokenPublication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publiees").value(1));
    }

    @Test
    @DisplayName("Menu assistant contrôleur : compteurs présents (2 sections, valeurs ≥ 0), filtrés sur sa localité")
    void dashboard_compteurs_assistant_ok() throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        mvc.perform(get("/api/kpis/mes-compteurs-assistant").header("Authorization", tokenAss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.pvDefinitifs").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu assistant contrôleur : lettre SIGNÉE de sa localité → lettresRenvoi ≥ 1")
    void dashboard_assist_lettres_ok() throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        // Examen 1 → dispatch 1 → réception 1 (CTRCC1, ANT) : une lettre SIGNÉE sur cet examen.
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1); l.setIdDossier(1); l.setObjetLettre("Renvoi"); l.setStatut("SIGNE");
        lettreRenvoiRepository.save(l);

        mvc.perform(get("/api/kpis/mes-compteurs-assistant").header("Authorization", tokenAss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Menu assistant contrôleur : PV signé de sa localité → pvDefinitifs ≥ 1")
    void dashboard_assist_pvDefinitifs_ok() throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        seedPvSigne(400, 1); // PV signé sur l'examen 1 (localité ANT via réception 1)

        mvc.perform(get("/api/kpis/mes-compteurs-assistant").header("Authorization", tokenAss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pvDefinitifs").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Menu administrateur : compteurs présents (3 sections, valeurs ≥ 0)")
    void dashboard_compteurs_admin_ok() throws Exception {
        mvc.perform(get("/api/kpis/mes-compteurs-admin").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inscriptionsEnAttente").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.comptes").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.journalAudit").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu administrateur : inscription PRMP EN_ATTENTE → inscriptionsEnAttente = 1")
    void dashboard_admin_inscriptions_ok() throws Exception {
        CompteAuth c = new CompteAuth("prmp.att", "x", "PRMP", "prmp.att", false);
        c.setStatut("EN_ATTENTE");
        compteAuthRepository.save(c);

        mvc.perform(get("/api/kpis/mes-compteurs-admin").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inscriptionsEnAttente").value(1));
    }

    @Test
    @DisplayName("Menu administrateur : comptes seedés comptés → comptes ≥ 1")
    void dashboard_admin_comptes_ok() throws Exception {
        // @BeforeEach crée plusieurs comptes d'authentification.
        mvc.perform(get("/api/kpis/mes-compteurs-admin").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comptes").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Lettre de renvoi : marquée lue à la consultation du détail par la PRMP propriétaire")
    void lettre_marquee_lue_apres_consultation() throws Exception {
        int id = seedLettreSignee();
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        assertTrue(lueRepository.existsByIdLettreAndIdPrmp(id, "PRMP001"), "trace de lecture créée");
    }

    @Test
    @DisplayName("Lettre de renvoi : 2ᵉ consultation → pas de doublon de lecture (UNIQUE)")
    void lettre_deja_lue_pas_doublon() throws Exception {
        int id = seedLettreSignee();
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenPrmp)).andExpect(status().isOk());
        assertTrue(lueRepository.count() == 1, "une seule entrée de lecture malgré 2 consultations");
    }

    @Test
    @DisplayName("Compteur PRMP : lettre SIGNÉE non lue → lettresRenvoi = 1")
    void compteur_lettre_non_lue() throws Exception {
        seedLettreSignee(); // non lue
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(1));
    }

    @Test
    @DisplayName("Compteur PRMP : lettre SIGNÉE lue (consultée) → exclue, lettresRenvoi = 0")
    void compteur_lettre_lue_exclu() throws Exception {
        int id = seedLettreSignee();
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(0));
    }

    @Test
    @DisplayName("LettreRenvoiDto : flag lue = true après consultation par la PRMP")
    void lettre_dto_lue_flag() throws Exception {
        int id = seedLettreSignee();
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lue").value(true));
    }

    @Test
    @DisplayName("Demandes de retrait : ouverture de l'écran (mes-demandes) → consultation enregistrée")
    void demande_retrait_vue_maj_ok() throws Exception {
        mvc.perform(get("/api/demande-retraits/mes-demandes").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        assertTrue(demandeRetraitVueRepository.findByIdPrmp("PRMP001").isPresent(),
                "date de dernière consultation enregistrée pour la PRMP");
    }

    @Test
    @DisplayName("Compteur PRMP : demande décidée (ACCEPTEE) après la dernière vue → demandesRetraitNouvelles = 1")
    void compteur_demandes_nouvelles_ok() throws Exception {
        // Aucune consultation préalable → seuil = époque → la décision récente est comptée.
        seedDemandeDecision("ACCEPTEE", LocalDateTime.of(2026, 6, 20, 9, 0));
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandesRetraitNouvelles").value(1));
    }

    @Test
    @DisplayName("Compteur PRMP : demande décidée AVANT la dernière vue → exclue, demandesRetraitNouvelles = 0")
    void compteur_demandes_anciennes_exclu() throws Exception {
        seedDemandeDecision("ACCEPTEE", LocalDateTime.of(2026, 1, 1, 0, 0)); // décision ancienne
        demandeRetraitVueRepository.save(
                new cnm.prs.entity.DemandeRetraitVue(null, "PRMP001", LocalDateTime.of(2026, 6, 1, 0, 0)));
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandesRetraitNouvelles").value(0));
    }

    @Test
    @DisplayName("Compteur PRMP : demande EN_ATTENTE → jamais comptée, demandesRetraitNouvelles = 0")
    void compteur_demandes_en_attente_exclu() throws Exception {
        seedDemandeDecision("EN_ATTENTE", null); // pas de décision
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandesRetraitNouvelles").value(0));
    }

    /** Crée une demande de retrait de PRMP001 (dossier 1) au statut/date de décision donnés. */
    private void seedDemandeDecision(String statut, LocalDateTime dateDecision) {
        DemandeRetrait d = demandeRetrait(0, 1, "PRMP001");
        d.setStatut(statut);
        d.setDateDecision(dateDecision);
        demandeRetraitRepository.save(d);
    }

    /** Crée une lettre de renvoi SIGNÉE sur l'examen/dossier 1 (PPM de PRMP001) ; renvoie sa PK. */
    private int seedLettreSignee() {
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1);
        l.setIdDossier(1);
        l.setObjetLettre("Renvoi");
        l.setStatut("SIGNE");
        return lettreRenvoiRepository.save(l).getIdLettre();
    }

    /** Crée un PV signé H2 sur un examen (PK manuelle, avis seedé). */
    private void seedPvSigne(int idPv, int idExamen) {
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(idPv);
        pv.setIdExamen(idExamen);
        pv.setIdAvis("FAV");
        pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("SIGNE");
        pv.setNbNavettes(0);
        pvExamenRepository.save(pv);
    }

    /** Crée une publication H2 au statut donné (PK manuelle). */
    private void seedPublication(int id, String statut) {
        cnm.prs.entity.Publication p = new cnm.prs.entity.Publication();
        p.setIdPublication(id);
        p.setTypeObjet("PV");
        p.setIdObjet(1);
        p.setStatutPubli(statut);
        publicationRepository.save(p);
    }

    @Test
    @DisplayName("DispatchDto : dateDispatch comporte l'heure (yyyy-MM-dd HH:mm)")
    void dispatch_dto_datetime_ok() throws Exception {
        // Le dispatch 1 (localité ANT) est seedé à 2026-06-03 14:45.
        mvc.perform(get("/api/dispatchs").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDispatch==1)].dateDispatch", hasItem("2026-06-03 14:45")));
    }

    @Test
    @DisplayName("DispatchDto : datePredispatch = date/heure de réception du dossier par le secrétaire")
    void dispatch_dto_predispatch_ok() throws Exception {
        // Dispatch 1 → réception 1 (dossier 1), seedée à 2026-06-02 10:30.
        mvc.perform(get("/api/dispatchs").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDispatch==1)].datePredispatch", hasItem("2026-06-02 10:30")));
    }

    @Test
    @DisplayName("DispatchDto : datePredispatch = null si le dossier n'a aucune réception datée")
    void dispatch_dto_predispatch_null_ok() throws Exception {
        // Réception sans date (dossier 161) + son dispatch → datePredispatch null.
        Dossier d = dossier(161, "DISPATCHE");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        Reception r = new Reception();
        r.setIdReception(161);
        r.setIdDossier(161);
        r.setNumPassage(1);
        r.setTypePassage("INITIAL");
        r.setImCtrlRecept("CTRCC1");
        r.setComplet(false); // dateReception laissée à null
        receptionRepository.save(r);
        dispatchRepository.save(dispatch(161, 161, "CTRCC1", "CTRMEM"));

        mvc.perform(get("/api/dispatchs/161").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datePredispatch").value(nullValue()));
    }

    @Test
    @DisplayName("Scoping PPM/Marché : PRMP voit les siens, CC sa localité (hors brouillon), Président tout ; hors périmètre → 403")
    void scoping_ppmEtMarche() throws Exception {
        String tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");
        prmpRepository.save(prmp("PRMP002", "ANT")); // FK t_dossier/t_ppm.ID_PRMP

        // Dossiers SOUMIS (estampillés localité) avec PPM/marché de PRMP différentes / localités différentes.
        dossierRepository.save(dossierLoc(200, "SOUMIS", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(201, "SOUMIS", "ANT", "PRMP002"));
        dossierRepository.save(dossierLoc(202, "SOUMIS", "TMS", "PRMP001"));
        dossierRepository.save(dossierLoc(203, "BROUILLON", "ANT", "PRMP001")); // brouillon → invisible des contrôleurs
        ppmRepository.save(ppm(200, 200, "PRMP001"));
        ppmRepository.save(ppm(201, 201, "PRMP002"));
        ppmRepository.save(ppm(202, 202, "PRMP001"));
        ppmRepository.save(ppm(203, 203, "PRMP001"));
        marcheRepository.save(marche(800, 200, 200));
        marcheRepository.save(marche(801, 201, 201));
        marcheRepository.save(marche(802, 202, 202));

        // PRMP001 ne voit QUE ses PPM NON-brouillon (200, 202) : son brouillon (203) est exclu de
        // « Mes PPM & marchés » (écran « Mes brouillons » dédié) ; ceux de PRMP002 (201) restent invisibles.
        mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPpm==200)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==202)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==203)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idPpm==201)]", hasSize(0)));
        // PRMP002 ne voit que le sien (201).
        mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp2))
                .andExpect(jsonPath("$[?(@.idPpm==201)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==200)]", hasSize(0)));
        // CC ANT voit les PPM ANT non brouillon (200, 201), pas TMS (202) ni le brouillon (203).
        mvc.perform(get("/api/ppms").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.idPpm==200)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==201)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==202)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idPpm==203)]", hasSize(0)));
        // Président voit tout, y compris TMS (202).
        mvc.perform(get("/api/ppms").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.idPpm==202)]", hasSize(1)));

        // GET /{id} hors périmètre → 403 : PRMP001 sur le PPM de PRMP002 ; CC sur un brouillon.
        mvc.perform(get("/api/ppms/201").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ppms/203").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());

        // Marchés : même scoping. PRMP001 voit 800/802 mais pas 801 ; CC ANT voit 800 mais pas 802 (TMS).
        mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDetail==800)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDetail==801)]", hasSize(0)));
        mvc.perform(get("/api/marches").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.idDetail==800)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDetail==802)]", hasSize(0)));
        // GET /{id} : marché de PRMP002 → 403 pour PRMP001 ; marché ANT visible au Membre d'ANT.
        mvc.perform(get("/api/marches/801").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/marches/800").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mes PPM & marchés : GET /api/ppms (PRMP) exclut les BROUILLON ; « Mes brouillons » (GET /api/dossiers?statut=BROUILLON) inchangé")
    void mesPpm_exclutBrouillons_mesBrouillonsInchange() throws Exception {
        // PRMP001 : 3 dossiers non-brouillon (SOUMIS) + 2 brouillons, chacun avec son PPM.
        dossierRepository.save(dossierLoc(210, "SOUMIS", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(211, "SOUMIS", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(212, "EXAMINE", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(213, "BROUILLON", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(214, "BROUILLON", "ANT", "PRMP001"));
        for (int i = 210; i <= 214; i++) {
            ppmRepository.save(ppm(i, i, "PRMP001"));
        }

        // « Mes PPM & marchés » : les 3 non-brouillon présents, les 2 brouillons (213, 214) absents.
        mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPpm==210)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==211)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==212)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==213)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idPpm==214)]", hasSize(0)));

        // « Mes brouillons » : GET /api/dossiers?statut=BROUILLON renvoie toujours les 2 brouillons (non régressé).
        mvc.perform(get("/api/dossiers?statut=BROUILLON").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==213)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==214)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==210)]", hasSize(0)));

        // Badge du menu (KpiService.ppmMarches) : aligné sur la liste — même critère hors BROUILLON.
        String ppms = mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString();
        int tailleListe = ((List<?>) com.jayway.jsonpath.JsonPath.read(ppms, "$")).size();
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ppmMarches").value(tailleListe));
    }

    @Test
    @DisplayName("GET /api/dossiers/{id}/ppm : résout l'idPpm d'un brouillon (même sans marché) pour le propriétaire ; 403 hors périmètre ; 404 sans PPM")
    void dossierPpm_resolution_ok() throws Exception {
        // Brouillon PPM de PRMP001, SANS aucun marché → GET /api/marches ne peut pas fournir l'idPpm.
        dossierRepository.save(dossierLoc(220, "BROUILLON", "ANT", "PRMP001"));
        ppmRepository.save(ppm(220, 220, "PRMP001"));
        // Dossier BROUILLON de PRMP001 SANS PPM rattaché (cas 404).
        dossierRepository.save(dossierLoc(221, "BROUILLON", "ANT", "PRMP001"));

        // Propriétaire → 200 + PpmDto complet (idPpm résolu) même sans marché.
        mvc.perform(get("/api/dossiers/220/ppm").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPpm").value(220))
                .andExpect(jsonPath("$.idDossier").value(220));

        // Autre PRMP → hors périmètre → 403.
        String tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");
        prmpRepository.save(prmp("PRMP002", "ANT"));
        mvc.perform(get("/api/dossiers/220/ppm").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());

        // Dossier sans PPM rattaché → 404.
        mvc.perform(get("/api/dossiers/221/ppm").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Complétion après lettre de renvoi : dépôt PRMP (idLettre) → dossier DISPATCHE + notif unique au Membre + réapparaît dans a-examiner")
    void completionApresRenvoi_notifieMembreEtRouvreExamen() throws Exception {
        // Dossier déjà examiné puis remis PRET_DISPATCH par la signature de la lettre (signer() testé ailleurs — dépend de Word).
        Dossier d = dossierLoc(400, "PRET_DISPATCH", "ANT", "PRMP001");
        d.setIdTypeDossier("DDP");
        d.setRefeDossier("00004/DDP/CRM-ANT/2026");
        dossierRepository.save(d);
        receptionRepository.save(reception(400, 400, "CTRCC1", true));
        dispatchRepository.save(dispatch(400, 400, "CTRCC1", "CTRMEM"));   // Membre attributaire = CTRMEM
        examenRepository.save(examen(400, 400, "CTRMEM"));
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(400); l.setIdDossier(400); l.setObjetLettre("Renvoi"); l.setStatut("SIGNE");
        int idLettre = lettreRenvoiRepository.save(l).getIdLettre();
        int idType = seedTypePiece("Pièce complémentaire", false, "DDP",1);

        byte[] pdf = "%PDF-1.4 piece complementaire".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile data = new MockMultipartFile("data", "", "application/json",
                ("{\"idDossier\":400,\"idTypePiece\":" + idType + ",\"idLettre\":" + idLettre + "}").getBytes(StandardCharsets.UTF_8));
        MockMultipartFile fichier = new MockMultipartFile("fichier", "piece.pdf", "application/pdf", pdf);

        // 1er dépôt après renvoi (PRMP propriétaire) → 201.
        mvc.perform(multipart("/api/piece-jointe-dossiers").file(data).file(fichier)
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated());

        // Le dossier est rouvert à l'examen (PRET_DISPATCH → DISPATCHE, dispatch existant réutilisé).
        mvc.perform(get("/api/dossiers/400").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.statut").value("DISPATCHE"));
        // Il réapparaît dans la file « à examiner » du Membre attributaire.
        mvc.perform(get("/api/dossiers/a-examiner").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idDossier==400)]", hasSize(1)));
        // Le Membre reçoit UNE notification PIECE_AJOUTEE_APRES_RENVOI.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PIECE_AJOUTEE_APRES_RENVOI' && @.idObjet==400)]", hasSize(1)));

        // 2e dépôt : le dossier est déjà DISPATCHE → pas de ré-avance ni de 2e notification (regroupée).
        MockMultipartFile data2 = new MockMultipartFile("data", "", "application/json",
                ("{\"idDossier\":400,\"idTypePiece\":" + idType + ",\"idLettre\":" + idLettre + "}").getBytes(StandardCharsets.UTF_8));
        MockMultipartFile fichier2 = new MockMultipartFile("fichier", "piece2.pdf", "application/pdf", pdf);
        mvc.perform(multipart("/api/piece-jointe-dossiers").file(data2).file(fichier2)
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PIECE_AJOUTEE_APRES_RENVOI' && @.idObjet==400)]", hasSize(1)));
    }

    @Test
    @DisplayName("Filtre serveur ?statut sur /api/dossiers : scoping conservé, statut inconnu → 400")
    void dossiers_filtreStatut() throws Exception {
        dossierRepository.save(dossierLoc(210, "SOUMIS", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(211, "BROUILLON", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(212, "CLOTURE", "ANT", "PRMP001"));

        // PRMP001 : ?statut=SOUMIS ne renvoie que 210 (pas 211 brouillon ni 212 clôturé).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "SOUMIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==210)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==211)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idDossier==212)]", hasSize(0)));
        // ?statut=BROUILLON renvoie 211 (la PRMP voit ses brouillons).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "BROUILLON"))
                .andExpect(jsonPath("$[?(@.idDossier==211)]", hasSize(1)));
        // Sans filtre : 210, 211 et 212 présents.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==210)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==212)]", hasSize(1)));
        // Scoping conservé : le CC ANT avec ?statut=SOUMIS voit 210, jamais le brouillon 211.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenCc).param("statut", "SOUMIS"))
                .andExpect(jsonPath("$[?(@.idDossier==210)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==211)]", hasSize(0)));
        // Statut inconnu → 400.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "NIMPORTEQUOI"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Réceptions : filtre ?idDossier scopé et test /existe (déjà réceptionné) sans charger l'historique")
    void receptions_parDossierEtExiste() throws Exception {
        // Dossier ANT déjà réceptionné = dossier 1 (réception 1, CTRCC1). Dossier ANT sans réception = 220.
        dossierRepository.save(dossierLoc(220, "SOUMIS", "ANT", "PRMP001"));

        // CC ANT : ?idDossier=1 ne renvoie que la réception du dossier 1.
        mvc.perform(get("/api/receptions").header("Authorization", tokenCc).param("idDossier", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // /existe : dossier 1 → reçu ; dossier 220 (aucune réception) → non reçu.
        mvc.perform(get("/api/receptions/dossier/1/existe").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.recu").value(true));
        mvc.perform(get("/api/receptions/dossier/220/existe").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.recu").value(false));
        // Isolation localité : CC ANT n'obtient pas les réceptions du dossier 2 (TMS).
        mvc.perform(get("/api/receptions").header("Authorization", tokenCc).param("idDossier", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // La PRMP (ressource interne) → liste vide même par dossier.
        mvc.perform(get("/api/receptions").header("Authorization", tokenPrmp).param("idDossier", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Filtre demandes de retrait : PRMP voit les siennes, CC celles de sa localité")
    void filtreLocalite_demandeRetrait() throws Exception {
        // PRMP001 voit sa demande.
        mvc.perform(get("/api/demande-retraits").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // Le CC d'ANT voit la demande de sa localité (dossier ANT).
        mvc.perform(get("/api/demande-retraits").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // Une autre PRMP ne voit rien.
        String tokenAutrePrmp = bearer("PRMPXX", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPXX", "ANT");
        mvc.perform(get("/api/demande-retraits").header("Authorization", tokenAutrePrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Demande de retrait — création OK : identité JWT, EN_ATTENTE, notif DEMANDE_RETRAIT_A_VALIDER au CC + Président")
    void retrait_creation_ok() throws Exception {
        Dossier d = dossier(120, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        mvc.perform(posterRetrait("{\"idDossier\":120,\"motifRetrait\":\"Erreur de saisie\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDemandeRetrait").isNumber())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.idPrmp").value("PRMP001"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='DEMANDE_RETRAIT_A_VALIDER')].destinataireIm", hasItem("CTRCC1")))
                .andExpect(jsonPath("$[?(@.typeNotif=='DEMANDE_RETRAIT_A_VALIDER')].destinataireIm", hasItem("CTRPRE")));
    }

    @Test
    @DisplayName("Demande de retrait — identité du demandeur = JWT (corps idPrmp ignoré)")
    void retrait_creation_identiteJWT() throws Exception {
        Dossier d = dossier(121, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        mvc.perform(posterRetrait("{\"idDossier\":121,\"idPrmp\":\"USURP\",\"motifRetrait\":\"x\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPrmp").value("PRMP001"));
    }

    @Test
    @DisplayName("Demande de retrait — non propriétaire → 403")
    void retrait_creation_nonProprietaire_403() throws Exception {
        // Dossier non possédé par PRMP001 (idPrmp null) → la PRMP connectée n'est pas propriétaire.
        Dossier d = dossier(122, "SOUMIS"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        mvc.perform(posterRetrait("{\"idDossier\":122,\"motifRetrait\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Demande de retrait — dossier à PV signé (PV_SIGNE) → 409 (au-delà de « avant PV signé », §3.3)")
    void retrait_creation_pvSigne_409() throws Exception {
        // §3.3 — le retrait est refusé dès que le PV est signé. Dossier PV_SIGNE de PRMP001, sans demande préalable.
        Dossier d = dossier(124, "PV_SIGNE"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        mvc.perform(posterRetrait("{\"idDossier\":124,\"motifRetrait\":\"x\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Retrait §3.3 — un dossier EXAMINE est retirable (listé + POST 201) ; un PV_SIGNE ni listé ni acceptable (409)")
    void retrait_avantPvSigne_examineRetirable_pvSigneRefuse() throws Exception {
        // Deux dossiers de PRMP001 : l'un examiné (avant PV signé → éligible), l'autre PV signé (au-delà → refusé).
        Dossier ex = dossier(700, "EXAMINE"); ex.setIdLocalite("ANT"); ex.setIdPrmp("PRMP001");
        dossierRepository.save(ex);
        Dossier pv = dossier(701, "PV_SIGNE"); pv.setIdLocalite("ANT"); pv.setIdPrmp("PRMP001");
        dossierRepository.save(pv);

        // La liste déroulante des retirables inclut l'EXAMINE, exclut le PV_SIGNE (même ensemble que la garde).
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==700)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==701)]", hasSize(0)));

        // POST retrait sur l'EXAMINE → 201 (créé, EN_ATTENTE).
        mvc.perform(posterRetrait("{\"idDossier\":700,\"motifRetrait\":\"corriger avant PV\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));

        // POST retrait sur le PV_SIGNE → 409 (au-delà de la limite).
        mvc.perform(posterRetrait("{\"idDossier\":701,\"motifRetrait\":\"x\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Retrait §3.3 — acceptation sur un EXAMINE avec circuit complet : dossier→BROUILLON + purge FK-safe de tout l'historique")
    void retrait_accepte_purgeCircuitComplet() throws Exception {
        // Dossier EXAMINE de PRMP001 portant tout l'enchaînement de circuit (avant PV signé → le PV est un projet).
        Dossier d = dossier(710, "EXAMINE"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        d.setRefeDossier("00009/DDP/CRM-ANT/2026");                      // réf de réception (à invalider)
        dossierRepository.save(d);
        Ppm p = ppm(710, 710, "PRMP001"); p.setReference("00010/DGB/PPM/2026"); ppmRepository.save(p);   // réf initiale
        receptionRepository.save(reception(710, 710, "CTRCC1", true));
        dispatchRepository.save(dispatch(710, 710, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(710, 710, "CTRMEM"));
        // Détail d'examen (7100) + observation (petit-enfant). Le point de contrôle référencé doit exister (FK).
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(1); pc.setLibelPointCtrl("Montant"); pc.setObligatoire(true); pc.setIdTypeDossier("DDP");
        pointsCtrlRepository.save(pc);
        ExamenDetail ed = new ExamenDetail();
        ed.setIdDetailExamen(7100); ed.setIdExamen(710); ed.setIdPtControle(1); ed.setConforme(false);
        examenDetailRepository.save(ed);
        cnm.prs.entity.ObservationControle obs = new cnm.prs.entity.ObservationControle();
        obs.setIdDetail(7100); obs.setAuLieuDe("500000"); obs.setLire("5000000"); obs.setOrdre(1);
        observationControleRepository.save(obs);
        // Projet de PV (710) + navette (enfant).
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(710); pv.setIdExamen(710); pv.setIdAvis("FAV"); pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("BROUILLON"); pv.setNbNavettes(1);
        pvExamenRepository.save(pv);
        cnm.prs.entity.PvNavette nav = new cnm.prs.entity.PvNavette();
        nav.setIdNavette(7101); nav.setIdPv(710); nav.setNumNavette(1); nav.setSens("ALLER");
        nav.setImActeur("CTRMEM"); nav.setDateAction(LocalDateTime.of(2026, 6, 5, 9, 0));
        pvNavetteRepository.save(nav);
        // Copie de dossier (enfant du dispatch) + lettre de renvoi + accusé de lecture (petit-enfant).
        cnm.prs.entity.CopieDossier cop = new cnm.prs.entity.CopieDossier();
        cop.setIdCopie(7102); cop.setIdDispatch(710); cop.setIdDossier(710); cop.setImDestinataire("CTRMEM");
        cop.setTypeCopie("MEMBRE"); cop.setDateTransmission(LocalDateTime.of(2026, 6, 5, 9, 0)); cop.setAccuseReception(false);
        copieDossierRepository.save(cop);
        cnm.prs.entity.LettreRenvoi lr = new cnm.prs.entity.LettreRenvoi();
        lr.setIdExamen(710); lr.setIdDossier(710); lr.setObjetLettre("Renvoi"); lr.setStatut("SIGNE");
        int idLettre = lettreRenvoiRepository.save(lr).getIdLettre();
        cnm.prs.entity.LettreRenvoiLue lue = new cnm.prs.entity.LettreRenvoiLue();
        lue.setIdLettre(idLettre); lue.setIdPrmp("PRMP001"); lue.setDateLecture(LocalDateTime.of(2026, 6, 6, 9, 0));
        lueRepository.save(lue);

        int drId = demandeRetraitRepository.save(demandeRetrait(0, 710, "PRMP001")).getIdDemandeRetrait();

        // Acceptation → 200 : aucune violation de FK malgré le circuit complet.
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"));

        // Dossier → BROUILLON avec sa référence initiale (PPM) restaurée.
        Dossier apres = dossierRepository.findById(710).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("BROUILLON", apres.getStatut());
        org.junit.jupiter.api.Assertions.assertEquals("00010/DGB/PPM/2026", apres.getRefeDossier());
        // Tout le circuit est purgé (feuilles → racine).
        org.junit.jupiter.api.Assertions.assertFalse(receptionRepository.existsById(710), "réception purgée");
        org.junit.jupiter.api.Assertions.assertFalse(dispatchRepository.existsById(710), "dispatch purgé");
        org.junit.jupiter.api.Assertions.assertFalse(examenRepository.existsById(710), "examen purgé");
        org.junit.jupiter.api.Assertions.assertFalse(examenDetailRepository.existsById(7100), "détail d'examen purgé");
        org.junit.jupiter.api.Assertions.assertTrue(observationControleRepository.findByIdDetailOrderByOrdreAsc(7100).isEmpty(), "observations purgées");
        org.junit.jupiter.api.Assertions.assertFalse(pvExamenRepository.existsById(710), "PV purgé");
        org.junit.jupiter.api.Assertions.assertFalse(pvNavetteRepository.existsById(7101), "navette purgée");
        org.junit.jupiter.api.Assertions.assertFalse(copieDossierRepository.existsById(7102), "copie purgée");
        org.junit.jupiter.api.Assertions.assertFalse(lettreRenvoiRepository.existsById(idLettre), "lettre de renvoi purgée");
        org.junit.jupiter.api.Assertions.assertFalse(lueRepository.existsByIdLettreAndIdPrmp(idLettre, "PRMP001"), "accusé de lecture purgé");
    }

    @Test
    @DisplayName("Demande de retrait — doublon EN_ATTENTE → 409")
    void retrait_creation_doublonEnAttente_409() throws Exception {
        Dossier d = dossier(123, "PRET_DISPATCH"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        mvc.perform(posterRetrait("{\"idDossier\":123,\"motifRetrait\":\"x\"}"))
                .andExpect(status().isCreated());
        mvc.perform(posterRetrait("{\"idDossier\":123,\"motifRetrait\":\"y\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Retrait — lettre obligatoire : absente, non-PDF ou trop volumineuse → 400 (magic-bytes, pas le Content-Type déclaré)")
    void retrait_lettre_validations400() throws Exception {
        Dossier d = dossier(125, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        MockMultipartFile data = new MockMultipartFile("data", "", "application/json",
                "{\"idDossier\":125,\"motifRetrait\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        // Partie « fichier » absente → 400.
        mvc.perform(multipart("/api/demande-retraits").file(data).header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("obligatoire")));
        // Contenu non-PDF (PNG déguisé en Content-Type application/pdf) → 400 : la validation lit les magic-bytes.
        mvc.perform(multipart("/api/demande-retraits").file(data)
                .file(new MockMultipartFile("fichier", "lettre.pdf", "application/pdf",
                        new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A }))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("PDF")));
        // PDF trop volumineux (> 10 Mo) → 400.
        byte[] gros = new byte[10 * 1024 * 1024 + 1];
        gros[0] = '%'; gros[1] = 'P'; gros[2] = 'D'; gros[3] = 'F'; gros[4] = '-';
        mvc.perform(multipart("/api/demande-retraits").file(data)
                .file(new MockMultipartFile("fichier", "lettre.pdf", "application/pdf", gros))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("volumineuse")));
        // Aucune demande créée par ces tentatives.
        org.junit.jupiter.api.Assertions.assertTrue(
                demandeRetraitRepository.findAll().stream().noneMatch(dr -> Integer.valueOf(125).equals(dr.getIdDossier())));
    }

    @Test
    @DisplayName("Retrait — lettre jointe : DTO expose nomFichier/tailleFichier ; GET document pour PRMP demanderesse + décideur, 403 hors périmètre")
    void retrait_lettre_creationEtLecture() throws Exception {
        Dossier d = dossier(126, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        byte[] pdf = lettreRetraitPdf().getBytes();
        String rep = mvc.perform(posterRetrait("{\"idDossier\":126,\"motifRetrait\":\"avec lettre\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomFichier").value("lettre-retrait.pdf"))
                .andExpect(jsonPath("$.tailleFichier").value(pdf.length))
                .andReturn().getResponse().getContentAsString();
        int id = Integer.parseInt(rep.replaceAll(".*\"idDemandeRetrait\":(\\d+).*", "$1"));

        // Lecture de la lettre : PRMP demanderesse, CC de la localité (décideur), Président — 200, PDF sain.
        mvc.perform(get("/api/demande-retraits/" + id + "/document").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("lettre-retrait.pdf")))
                .andExpect(content().bytes(pdf));
        mvc.perform(get("/api/demande-retraits/" + id + "/document").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(get("/api/demande-retraits/" + id + "/document").header("Authorization", tokenPresident))
                .andExpect(status().isOk());

        // Hors périmètre : CC d'une autre localité, Membre (non décideur), autre PRMP → 403.
        mvc.perform(get("/api/demande-retraits/" + id + "/document")
                .header("Authorization", bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/demande-retraits/" + id + "/document").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/demande-retraits/" + id + "/document")
                .header("Authorization", bearer("PRMP999", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP999", "ANT")))
                .andExpect(status().isForbidden());

        // Les listes exposent les métadonnées de la lettre (jamais le contenu).
        mvc.perform(get("/api/demande-retraits/mes-demandes").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + id + ")].nomFichier", hasItem("lettre-retrait.pdf")));
    }

    @Test
    @DisplayName("Retrait — la lettre SURVIT à l'acceptation (purge du circuit) : elle justifie la décision ; rétro-compat : demande sans pièce → nomFichier null + document 404")
    void retrait_lettre_survitAcceptation_etRetroCompat() throws Exception {
        Dossier d = dossier(127, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        String rep = mvc.perform(posterRetrait("{\"idDossier\":127,\"motifRetrait\":\"lettre à conserver\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = Integer.parseInt(rep.replaceAll(".*\"idDemandeRetrait\":(\\d+).*", "$1"));
        // Acceptation par le CC → purge du circuit + dossier BROUILLON… mais la lettre reste lisible
        // (stockage dédié t_piece_demande_retrait, hors t_piece_jointe_dossier purgée avec le circuit).
        mvc.perform(post("/api/demande-retraits/" + id + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$.nomFichier").value("lettre-retrait.pdf"));
        mvc.perform(get("/api/demande-retraits/" + id + "/document").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(content().bytes(lettreRetraitPdf().getBytes()));

        // Rétro-compat — demande créée avant l'obligation (sans pièce) : valide, nomFichier null, document 404 explicite.
        Dossier ancien = dossier(128, "SOUMIS"); ancien.setIdLocalite("ANT"); ancien.setIdPrmp("PRMP001");
        dossierRepository.save(ancien);
        int idAncien = demandeRetraitRepository.save(demandeRetrait(0, 128, "PRMP001")).getIdDemandeRetrait();
        mvc.perform(get("/api/demande-retraits/" + idAncien).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomFichier").value(nullValue()));
        mvc.perform(get("/api/demande-retraits/" + idAncien + "/document").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DossierDto — auteur de la saisie : creePar/soumisPar exposés + noms lisibles résolus serveur (UGPM et PRMP) ; login inconnu → nom null")
    void dossier_auteurSaisie_creeParEtNomsResolus() throws Exception {
        // UGPM rattachée à PRMP001, avec son compte : c'est elle qui a saisi le dossier.
        ugpmRepository.save(ugpm("UGPM010", "PRMP001", "Rasoa", "Hanta Miora"));
        compteAuthRepository.save(new CompteAuth("ugpm.hanta", passwordEncoder.encode("pw"), "UGPM", "UGPM010", true));

        Dossier d = dossierLoc(940, "SOUMIS", "ANT", "PRMP001");
        d.setCreePar("ugpm.hanta");     // login de l'agent UGPM (pas l'idUgpm : c'est tout l'objet de la résolution)
        d.setSoumisPar("PRMP001");      // soumission réservée à la PRMP
        dossierRepository.save(d);

        // Détail : logins bruts + noms lisibles, convention « Nom Prénoms » (celle du nomAffichage du login).
        mvc.perform(get("/api/dossiers/940").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creePar").value("ugpm.hanta"))
                .andExpect(jsonPath("$.creeParNom").value("Rasoa Hanta Miora"))
                .andExpect(jsonPath("$.soumisPar").value("PRMP001"))
                .andExpect(jsonPath("$.soumisParNom").value("Nom Prenoms"));

        // Les listes portent la même information (résolution en lot, pas de N+1).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==940)].creeParNom", hasItem("Rasoa Hanta Miora")));

        // Login sans compte (agent supprimé) → nom non résolu : le front garde le login brut.
        Dossier orphelin = dossierLoc(941, "SOUMIS", "ANT", "PRMP001");
        orphelin.setCreePar("compte.disparu");
        dossierRepository.save(orphelin);
        mvc.perform(get("/api/dossiers/941").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creePar").value("compte.disparu"))
                .andExpect(jsonPath("$.creeParNom").value(nullValue()))
                .andExpect(jsonPath("$.soumisParNom").value(nullValue()));

        // Champs en LECTURE SEULE : une valeur envoyée par le client est ignorée (traçabilité serveur).
        mvc.perform(put("/api/dossiers/940").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":940,\"statut\":\"SOUMIS\",\"idLocalite\":\"ANT\",\"idPrmp\":\"PRMP001\","
                        + "\"creePar\":\"usurpateur\",\"soumisPar\":\"usurpateur\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creePar").value("ugpm.hanta"))
                .andExpect(jsonPath("$.soumisPar").value("PRMP001"));
    }

    @Test
    @DisplayName("UGPM par tutelle — PRMP : ses propres unités (autre tutelle → 403) ; contrôleurs : toute tutelle, sans filtre de localité ; vue restreinte hors Administrateur")
    void ugpms_parTutelle_ouvertALaPrmpEtAuxControleurs() throws Exception {
        ugpmRepository.save(ugpm("UGPM011", "PRMP001", "Rabe", "Tiana"));
        compteAuthRepository.save(new CompteAuth("ugpm.tiana", passwordEncoder.encode("pw"), "UGPM", "UGPM011", true));
        prmpRepository.save(prmp("PRMP003", "ANT"));
        ugpmRepository.save(ugpm("UGPM012", "PRMP003", "Autre", "Unite"));

        // La PRMP consulte ses propres unités rattachées (plus de 403 silencieux à l'ouverture du modal).
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idUgpm=='UGPM011')]", hasSize(1)));
        // Mais pas celles d'une autre tutelle.
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP003").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());

        // ⚠️ 2026-08-20 — les contrôleurs qui instruisent les dossiers lisent TOUTE tutelle : ils
        // doivent savoir quelle unité a saisi le dossier examiné. Pas de filtre de localité (le
        // répertoire des PRMP est déjà national, et l'UGPM n'a pas de localité propre).
        String tokenVerif = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenSecretaire = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        String tokenAssistant = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        // Un CC d'une AUTRE localité (TMS) lit malgré tout la tutelle : c'est le cas qu'un filtre par
        // localité casserait (PRMP à cheval sur plusieurs localités via ses entités contractantes).
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        for (String jeton : List.of(tokenMembre, tokenVerif, tokenSecretaire, tokenAssistant, tokenCc, tokenCcTms, tokenPresident)) {
            mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", jeton))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.idUgpm=='UGPM011')]", hasSize(1)));
        }

        // Étendue des données — le contrôleur reçoit ce que l'écran affiche, et rien de plus :
        // ni pièce d'identité (état civil, sans usage pour l'instruction) ni login (identifiant
        // d'authentification). L'Administrateur, lui, garde la fiche complète.
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[0].nomUgpm").value("Rabe"))
                .andExpect(jsonPath("$[0].prenomsUgpm").value("Tiana"))
                .andExpect(jsonPath("$[0].idUgpm").value("UGPM011"))
                .andExpect(jsonPath("$[0].libelle").isNotEmpty())
                .andExpect(jsonPath("$[0].emailUgpm").isNotEmpty())
                .andExpect(jsonPath("$[0].telUgpm").isNotEmpty())
                .andExpect(jsonPath("$[0].cin").value(nullValue()))
                .andExpect(jsonPath("$[0].dateCin").value(nullValue()))
                .andExpect(jsonPath("$[0].lieuCin").value(nullValue()))
                .andExpect(jsonPath("$[0].login").value(nullValue()));
        // La PRMP elle-même passe par la même vue restreinte (l'écran n'affiche pas davantage).
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[0].cin").value(nullValue()))
                .andExpect(jsonPath("$[0].login").value(nullValue()));
        // Administrateur : accès inchangé, toutes tutelles, fiche complète (CIN + login).
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP003").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idUgpm=='UGPM012')]", hasSize(1)));
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[0].cin").value("303033334444"))
                .andExpect(jsonPath("$[0].login").value("ugpm.tiana"));

        // Le reste de la ressource demeure réservé à l'Administrateur : la liste complète notamment.
        mvc.perform(get("/api/ugpms").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        // Chargé de publication : hors du périmètre d'instruction, pas d'ouverture.
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001")
                .header("Authorization", bearer("CTRPUB", ProfilUtilisateur.CHARGE_PUBLICATION, TypeActeur.CONTROLEUR, "CTRPUB", "ANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Actualités — CRUD admin : INACTIF forcé à la création, validations 400 (profils/HTML/dates), visibilité par profil ciblé")
    void actualites_cycleAdmin_visibiliteParProfil() throws Exception {
        // Réservé à l'Administrateur.
        mvc.perform(post("/api/actualites").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"x\",\"profilsCibles\":[\"MEMBRE\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/actualites").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // Validations 400 : profils vides / inconnu, HTML dans le markdown, expiration avant publication.
        mvc.perform(post("/api/actualites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"x\",\"profilsCibles\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/actualites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"x\",\"profilsCibles\":[\"PILOTE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("PILOTE")));
        mvc.perform(post("/api/actualites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"<script>alert(1)</script>\",\"profilsCibles\":[\"MEMBRE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Markdown")));
        mvc.perform(post("/api/actualites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"x\",\"profilsCibles\":[\"MEMBRE\"],"
                        + "\"datePublication\":\"2026-09-01\",\"dateExpiration\":\"2026-08-01\"}"))
                .andExpect(status().isBadRequest());

        // Création OK — markdown avec autolien et « a < b » (le garde HTML ne bloque pas le markdown légitime).
        int id = creerActualite("Nouvelle procedure", "## Bonjour\\n\\nVoir <https://cnm.mg> - seuil : a < b.",
                "\"MEMBRE\",\"PRMP\"");
        mvc.perform(get("/api/actualites/" + id).header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statut").value("INACTIF"))
                .andExpect(jsonPath("$.imAuteur").value("CTRADM"))
                .andExpect(jsonPath("$.profilsCibles", containsInAnyOrder("MEMBRE", "PRMP")));

        // INACTIF : personne ne la voit, même ciblé.
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idActualite==" + id + ")]", hasSize(0)));

        // Activation (PUT) → visible pour les profils ciblés uniquement, filtrage serveur.
        activerActualite(id, "Nouvelle procedure", "## Bonjour", "\"MEMBRE\",\"PRMP\"");
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idActualite==" + id + ")].titre", hasItem("Nouvelle procedure")));
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idActualite==" + id + ")]", hasSize(1)));
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.idActualite==" + id + ")]", hasSize(0)));
    }

    @Test
    @DisplayName("Actualités — interrupteur global, fenêtre de dates, expiration→ARCHIVE automatique, DELETE=archivage, tri")
    void actualites_interrupteur_datesEtArchivage() throws Exception {
        int id1 = creerActualite("Annonce recente", "corps", "\"MEMBRE\"");
        activerActualite(id1, "Annonce recente", "corps", "\"MEMBRE\"");

        // Interrupteur global : coupe le modal pour tous, d'un coup ; bascule réservée à l'Admin.
        mvc.perform(get("/api/parametres/actualites-actives").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true));
        mvc.perform(put("/api/parametres/actualites-actives").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"actif\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/parametres/actualites-actives").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"actif\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(put("/api/parametres/actualites-actives").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"actif\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idActualite==" + id1 + ")]", hasSize(1)));

        // Publication future → pas encore visible.
        LocalDate demain = LocalDate.now().plusDays(1);
        mvc.perform(put("/api/actualites/" + id1).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Annonce recente\",\"contenuMd\":\"corps\",\"profilsCibles\":[\"MEMBRE\"],"
                        + "\"statut\":\"ACTIF\",\"datePublication\":\"" + demain + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idActualite==" + id1 + ")]", hasSize(0)));

        // Expiration atteinte → bascule automatique en ARCHIVE à la lecture (archiveur système = null).
        LocalDate avantHier = LocalDate.now().minusDays(2);
        LocalDate hier = LocalDate.now().minusDays(1);
        mvc.perform(put("/api/actualites/" + id1).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Annonce recente\",\"contenuMd\":\"corps\",\"profilsCibles\":[\"MEMBRE\"],"
                        + "\"statut\":\"ACTIF\",\"datePublication\":\"" + avantHier + "\",\"dateExpiration\":\"" + hier + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idActualite==" + id1 + ")]", hasSize(0)));
        mvc.perform(get("/api/actualites/" + id1).header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statut").value("ARCHIVE"))
                .andExpect(jsonPath("$.dateArchivage").isNotEmpty())
                .andExpect(jsonPath("$.imArchiveur").value(nullValue()));
        // Archivée = historique : plus modifiable (409), re-DELETE refusé (409).
        mvc.perform(put("/api/actualites/" + id1).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"x\",\"profilsCibles\":[\"MEMBRE\"]}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/actualites/" + id1).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());

        // DELETE = archivage manuel (traçé) — jamais de suppression physique : reste listée côté admin.
        int id2 = creerActualite("A archiver", "corps", "\"MEMBRE\"");
        mvc.perform(delete("/api/actualites/" + id2).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/actualites/" + id2).header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statut").value("ARCHIVE"))
                .andExpect(jsonPath("$.imArchiveur").value("CTRADM"));

        // Tri : publication effective décroissante (la plus récente d'abord).
        int idAncienne = creerActualite("Ancienne", "corps", "\"MEMBRE\"");
        activerActualite(idAncienne, "Ancienne", "corps", "\"MEMBRE\"", LocalDate.now().minusDays(5));
        int idRecente = creerActualite("Recente", "corps", "\"MEMBRE\"");
        activerActualite(idRecente, "Recente", "corps", "\"MEMBRE\"", LocalDate.now().minusDays(1));
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[0].idActualite").value(idRecente))
                .andExpect(jsonPath("$[1].idActualite").value(idAncienne));
    }

    @Test
    @DisplayName("Actualités — images : JPEG seul (magic-bytes) → 400, > 10 Mo → 413, redimensionnement 1600 px, lecture authentifiée, ordre")
    void actualites_images_jpegRedimensionne() throws Exception {
        int id = creerActualite("Avec images", "corps", "\"MEMBRE\"");

        // Non-JPEG (PNG déguisé) → 400 ; JPEG > 10 Mo → 413 ; réservé à l'Admin.
        mvc.perform(multipart("/api/actualites/" + id + "/images")
                .file(new MockMultipartFile("fichier", "logo.jpg", "image/jpeg",
                        new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A }))
                .header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
        byte[] trosGros = new byte[10 * 1024 * 1024 + 1];
        trosGros[0] = (byte) 0xFF; trosGros[1] = (byte) 0xD8; trosGros[2] = (byte) 0xFF;
        mvc.perform(multipart("/api/actualites/" + id + "/images")
                .file(new MockMultipartFile("fichier", "photo.jpg", "image/jpeg", trosGros))
                .header("Authorization", tokenAdmin))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/actualites/" + id + "/images")
                .file(new MockMultipartFile("fichier", "p.jpg", "image/jpeg", jpegDeTest(40, 20)))
                .header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // JPEG petit : stocké tel quel, ordre 1 ; le suivant prend l'ordre 2.
        mvc.perform(multipart("/api/actualites/" + id + "/images")
                .file(new MockMultipartFile("fichier", "banniere.jpg", "image/jpeg", jpegDeTest(40, 20)))
                .header("Authorization", tokenAdmin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordre").value(1))
                .andExpect(jsonPath("$.nomFichier").value("banniere.jpg"));
        // JPEG trop large (3200 px) : redimensionné au serveur à 1600 px (proportionnel).
        String repImage = mvc.perform(multipart("/api/actualites/" + id + "/images")
                .file(new MockMultipartFile("fichier", "panorama.jpg", "image/jpeg", jpegDeTest(3200, 100)))
                .header("Authorization", tokenAdmin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordre").value(2))
                .andReturn().getResponse().getContentAsString();
        int idImage = Integer.parseInt(repImage.replaceAll(".*\"idImage\":(\\d+).*", "$1"));

        // Lecture par un utilisateur authentifié (le modal du Membre) : image/jpeg, largeur réduite à 1600.
        byte[] servie = mvc.perform(get("/api/actualites/" + id + "/images/" + idImage)
                .header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        java.awt.image.BufferedImage relue = javax.imageio.ImageIO
                .read(new java.io.ByteArrayInputStream(servie));
        org.junit.jupiter.api.Assertions.assertEquals(1600, relue.getWidth(), "largeur plafonnée");
        org.junit.jupiter.api.Assertions.assertEquals(50, relue.getHeight(), "hauteur proportionnelle");

        // Métadonnées dans le DTO (jamais le binaire) ; mauvaise actualité → 404 ; suppression → 204 puis 404.
        mvc.perform(get("/api/actualites/" + id).header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[1].idImage").value(idImage));
        mvc.perform(get("/api/actualites/999999/images/" + idImage).header("Authorization", tokenMembre))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/actualites/" + id + "/images/" + idImage).header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/actualites/" + id + "/images/" + idImage).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/actualites/" + id + "/images/" + idImage).header("Authorization", tokenMembre))
                .andExpect(status().isNotFound());
    }

    /** POST admin d'une actualité (statut forcé INACTIF) — {@code profilsJson} : liste JSON sans crochets. */
    private int creerActualite(String titre, String contenuMd, String profilsJson) throws Exception {
        String rep = mvc.perform(post("/api/actualites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"" + titre + "\",\"contenuMd\":\"" + contenuMd
                        + "\",\"profilsCibles\":[" + profilsJson + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("INACTIF"))
                .andReturn().getResponse().getContentAsString();
        return Integer.parseInt(rep.replaceAll(".*\"idActualite\":(\\d+).*", "$1"));
    }

    private void activerActualite(int id, String titre, String contenuMd, String profilsJson) throws Exception {
        activerActualite(id, titre, contenuMd, profilsJson, null);
    }

    /** PUT admin : passe l'actualité ACTIF (avec date de publication optionnelle). */
    private void activerActualite(int id, String titre, String contenuMd, String profilsJson,
            LocalDate datePublication) throws Exception {
        String dates = datePublication == null ? "" : ",\"datePublication\":\"" + datePublication + "\"";
        mvc.perform(put("/api/actualites/" + id).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"" + titre + "\",\"contenuMd\":\"" + contenuMd
                        + "\",\"profilsCibles\":[" + profilsJson + "],\"statut\":\"ACTIF\"" + dates + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIF"));
    }

    /** JPEG réel généré en mémoire (aplat), aux dimensions demandées. */
    private static byte[] jpegDeTest(int largeur, int hauteur) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(largeur, hauteur,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.ORANGE);
        g.fillRect(0, 0, largeur, hauteur);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("Décision retrait — CC de la localité accepte → ACCEPTEE, dossier BROUILLON, notif RETRAIT_ACCEPTE")
    void decision_accepter_parCc_dossierBrouillon() throws Exception {
        Dossier d = dossier(130, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        d.setRefeDossier("00002/DDP/CRM-ANT/2026");   // réf. posée à la réception, à invalider au retrait
        dossierRepository.save(d);
        Ppm p = ppm(130, 130, "PRMP001"); p.setReference("00003/DGB/PPM/2026"); ppmRepository.save(p);  // réf initiale
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 130, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"));
        // Le dossier repasse en BROUILLON avec sa RÉFÉRENCE INITIALE restaurée (celle du PPM), la réf de
        // réception étant invalidée.
        Dossier apres = dossierRepository.findById(130).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("BROUILLON", apres.getStatut());
        org.junit.jupiter.api.Assertions.assertEquals("00003/DGB/PPM/2026", apres.getRefeDossier());
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='RETRAIT_ACCEPTE')]", hasSize(1)));
    }

    @Test
    @DisplayName("Retrait accepté — le dossier BROUILLON reste entièrement modifiable (édition PPM acceptée)")
    void retrait_accepte_dossier_modifiable() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        Dossier d = dossier(135, "SOUMIS");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        d.setRefeDossier("00006/DDP/CRM-ANT/2026");   // réf de réception (à invalider)
        dossierRepository.save(d);
        Ppm p135 = ppm(135, 135, "PRMP001"); p135.setReference("00005/DGB/PPM/2026"); ppmRepository.save(p135);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 135, "PRMP001")).getIdDemandeRetrait();
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        // Réf. initiale (PPM) restaurée dès le retrait.
        org.junit.jupiter.api.Assertions.assertEquals("00005/DGB/PPM/2026",
                dossierRepository.findById(135).orElseThrow().getRefeDossier());
        // Dossier BROUILLON issu du retrait → édition PPM (en-tête + ligne de marché) acceptée (200).
        // Ligne NOUVELLE à l'édition → ≥1 processus obligatoire (règle corrigée, comme au POST).
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        String edition = "{\"exercice\":2027,\"signataire\":\"Maj retrait\",\"dateSignature\":\"2026-02-01\","
                + "\"reference\":\"PPM-135-v2\",\"marches\":[{\"montEstim\":500000000,\"idNature\":1,"
                + "\"statut\":\"PREVU\",\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(put("/api/saisies/ppm/135").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"));
    }

    @Test
    @DisplayName("Retrait accepté — l'API renvoie la référence initiale (PPM) du dossier BROUILLON")
    void brouillon_retrait_api_retourne_ref_initiale() throws Exception {
        Dossier d = dossier(140, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        d.setRefeDossier("00002/DDP/CRM-ANT/2026");   // réf de réception (à remplacer)
        dossierRepository.save(d);
        Ppm p = ppm(140, 140, "PRMP001"); p.setReference("00004/DGB/PPM/2026"); ppmRepository.save(p);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 140, "PRMP001")).getIdDemandeRetrait();
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        // GET du dossier (« Mes brouillons ») → refeDossier = référence initiale (PPM), pas la réf de réception.
        mvc.perform(get("/api/dossiers/140").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refeDossier").value("00004/DGB/PPM/2026"));
    }

    @Test
    @DisplayName("Retrait accepté — la réception résiduelle est supprimée → dossier resoumis réapparaît dans a-receptionner")
    void retrait_accepte_supprime_reception_et_reReceptionnable() throws Exception {
        Dossier d = dossier(145, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        d.setIdTypeDossier("DDP");
        d.setRefeDossier("00003/DDP/CRM-ANT/2026");   // réf de réception résiduelle
        dossierRepository.save(d);
        Ppm p = ppm(145, 145, "PRMP001"); p.setReference("00009/DGB/PPM/2026"); ppmRepository.save(p);
        marcheRepository.save(marche(1450, 145, 145));                  // un PPM doit comporter un marché pour être soumis
        receptionRepository.save(reception(1450, 145, "CTRSEC", true)); // réception résiduelle (bloque a-receptionner)
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 145, "PRMP001")).getIdDemandeRetrait();

        // Acceptation du retrait → dossier BROUILLON + réception supprimée.
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertFalse(receptionRepository.existsByIdDossier(145),
                "La réception résiduelle doit être supprimée à l'acceptation du retrait");

        // Resoumission par la PRMP → SOUMIS.
        mvc.perform(post("/api/dossiers/145/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        // Le dossier réapparaît dans la worklist du Secrétaire (SOUMIS sans réception) → re-réceptionnable.
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        mvc.perform(get("/api/dossiers/a-receptionner").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==145)]", hasSize(1)));
    }

    @Test
    @DisplayName("Décision retrait — le Président accepte (toutes localités) → ACCEPTEE, dossier BROUILLON")
    void decision_accepter_parPresident_ok() throws Exception {
        Dossier d = dossier(131, "PRET_DISPATCH"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 131, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"))
                .andExpect(jsonPath("$.imCtrlCc").value("CTRPRE"));
        org.junit.jupiter.api.Assertions.assertEquals("BROUILLON",
                dossierRepository.findById(131).orElseThrow().getStatut());
    }

    @Test
    @DisplayName("Décision retrait — un CC d'une autre localité (dossier TMS) → 403")
    void decision_parCcAutreLocalite_403() throws Exception {
        Dossier d = dossier(132, "SOUMIS"); d.setIdLocalite("TMS"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 132, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Décision retrait — refus : REFUSEE + motif, dossier inchangé, notif RETRAIT_REFUSE")
    void decision_refuser_dossierInchange() throws Exception {
        Dossier d = dossier(133, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 133, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(post("/api/demande-retraits/" + drId + "/refuser").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Dossier incomplet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSEE"))
                .andExpect(jsonPath("$.obsDecision").value("Dossier incomplet"));
        // Dossier inchangé (toujours SOUMIS, donc visible).
        mvc.perform(get("/api/dossiers/133").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("SOUMIS"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='RETRAIT_REFUSE')]", hasSize(1)));
    }

    @Test
    @DisplayName("Décision retrait — demande déjà traitée → 409")
    void decision_dejaTraitee_409() throws Exception {
        Dossier d = dossier(134, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 134, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Dropdown retirables §3.3 : SOUMIS/PRET_DISPATCH/DISPATCHE/EXAMINE de la PRMP ; exclut PV_SIGNE et les dossiers d'autrui")
    void retrait_dropdown_retirables() throws Exception {
        Dossier a = dossier(150, "SOUMIS"); a.setIdLocalite("ANT"); a.setIdPrmp("PRMP001"); dossierRepository.save(a);
        Dossier b = dossier(151, "PRET_DISPATCH"); b.setIdLocalite("ANT"); b.setIdPrmp("PRMP001"); dossierRepository.save(b);
        Dossier c = dossier(152, "EXAMINE"); c.setIdLocalite("ANT"); c.setIdPrmp("PRMP001"); dossierRepository.save(c);
        Dossier di = dossier(148, "DISPATCHE"); di.setIdLocalite("ANT"); di.setIdPrmp("PRMP001"); dossierRepository.save(di);
        Dossier pv = dossier(149, "PV_SIGNE"); pv.setIdLocalite("ANT"); pv.setIdPrmp("PRMP001"); dossierRepository.save(pv);
        Dossier e = dossier(153, "SOUMIS"); e.setIdLocalite("ANT"); dossierRepository.save(e); // sans propriétaire
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==150)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==151)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==152)]", hasSize(1)))   // EXAMINE désormais retirable (§3.3)
                .andExpect(jsonPath("$[?(@.idDossier==148)]", hasSize(1)))   // DISPATCHE retirable
                .andExpect(jsonPath("$[?(@.idDossier==149)]", hasSize(0)))   // PV_SIGNE exclu (au-delà de la limite)
                .andExpect(jsonPath("$[?(@.idDossier==153)]", hasSize(0)));  // dossier d'autrui
    }

    @Test
    @DisplayName("Retirables — dossier BROUILLON de la PRMP → absent")
    void retirables_brouillon_exclu() throws Exception {
        Dossier d = dossier(154, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==154)]", hasSize(0)));
    }

    @Test
    @DisplayName("Retirables — dossier SOUMIS de la PRMP → présent")
    void retirables_soumis_inclus() throws Exception {
        Dossier d = dossier(155, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==155)]", hasSize(1)));
    }

    @Test
    @DisplayName("Retirables — dossier PRET_DISPATCH de la PRMP → présent")
    void retirables_pret_dispatch_inclus() throws Exception {
        Dossier d = dossier(156, "PRET_DISPATCH"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==156)]", hasSize(1)));
    }

    @Test
    @DisplayName("Retirables — dossier SOUMIS avec demande EN_ATTENTE → absent")
    void retirables_demande_en_attente_exclu() throws Exception {
        Dossier d = dossier(157, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        demandeRetraitRepository.save(demandeRetrait(0, 157, "PRMP001"));   // statut EN_ATTENTE
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==157)]", hasSize(0)));
    }

    @Test
    @DisplayName("Retirables — dossier SOUMIS avec demande REFUSEE → absent (pas de nouvelle demande)")
    void retirables_demande_refusee_exclu() throws Exception {
        Dossier d = dossier(159, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        DemandeRetrait dr = demandeRetrait(0, 159, "PRMP001");
        dr.setStatut("REFUSEE");
        demandeRetraitRepository.save(dr);
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==159)]", hasSize(0)));
    }

    @Test
    @DisplayName("Retirables — dossier SOUMIS d'une autre PRMP → absent")
    void retirables_autre_prmp_exclu() throws Exception {
        prmpRepository.save(prmp("PRMP009", "ANT"));
        Dossier d = dossier(158, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP009"); dossierRepository.save(d);
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==158)]", hasSize(0)));
    }

    @Test
    @DisplayName("À valider : CC voit les EN_ATTENTE de sa localité (ANT), pas TMS ; le Président voit les deux")
    void retrait_aValider_scopeLocalite() throws Exception {
        Dossier ant = dossier(160, "SOUMIS"); ant.setIdLocalite("ANT"); ant.setIdPrmp("PRMP001"); dossierRepository.save(ant);
        Dossier tms = dossier(161, "SOUMIS"); tms.setIdLocalite("TMS"); tms.setIdPrmp("PRMP001"); dossierRepository.save(tms);
        int drAnt = demandeRetraitRepository.save(demandeRetrait(0, 160, "PRMP001")).getIdDemandeRetrait();
        int drTms = demandeRetraitRepository.save(demandeRetrait(0, 161, "PRMP001")).getIdDemandeRetrait();

        mvc.perform(get("/api/demande-retraits/a-valider").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drAnt + ")]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drTms + ")]", hasSize(0)));
        mvc.perform(get("/api/demande-retraits/a-valider").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drAnt + ")]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drTms + ")]", hasSize(1)));
    }

    @Test
    @DisplayName("Historique : une demande décidée (REFUSEE) y apparaît, et plus dans « à valider »")
    void retrait_historique() throws Exception {
        Dossier d = dossier(162, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 162, "PRMP001")).getIdDemandeRetrait();
        mvc.perform(post("/api/demande-retraits/" + drId + "/refuser").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/demande-retraits/historique").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drId + ")]", hasSize(1)));
        mvc.perform(get("/api/demande-retraits/a-valider").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.idDemandeRetrait==" + drId + ")]", hasSize(0)));
    }

    @Test
    @DisplayName("Suppression marché — dossier BROUILLON avec prévisions → 204, marché + prévisions supprimés")
    void marche_delete_brouillonAvecPrevisions_supprime() throws Exception {
        Dossier d = dossier(180, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        ppmRepository.save(ppm(280, 180, "PRMP001"));
        marcheRepository.save(marche(380, 180, 280));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        marchePrevisionRepository.save(new MarchePrevision(480, 380, 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));
        marchePrevisionRepository.save(new MarchePrevision(481, 380, 1, LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 30), null, null));

        mvc.perform(delete("/api/marches/380").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(380));
        org.junit.jupiter.api.Assertions.assertTrue(marchePrevisionRepository.findByIdDetail(380).isEmpty());
    }

    @Test
    @DisplayName("Suppression marché — dossier SOUMIS → 409 (pas un brouillon)")
    void marche_delete_dossierSoumis_409() throws Exception {
        Dossier d = dossier(181, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        ppmRepository.save(ppm(281, 181, "PRMP001"));
        marcheRepository.save(marche(381, 181, 281));
        mvc.perform(delete("/api/marches/381").header("Authorization", tokenPrmp)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Suppression PPM — BROUILLON propriétaire avec marchés → 204, cascade marchés + prévisions")
    void ppm_delete_brouillonProprioAvecMarches_cascade() throws Exception {
        Dossier d = dossier(182, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        ppmRepository.save(ppm(282, 182, "PRMP001"));
        marcheRepository.save(marche(382, 182, 282));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        marchePrevisionRepository.save(new MarchePrevision(482, 382, 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));

        mvc.perform(delete("/api/ppms/282").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(ppmRepository.existsById(282));
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(382));
        org.junit.jupiter.api.Assertions.assertTrue(marchePrevisionRepository.findByIdDetail(382).isEmpty());
    }

    @Test
    @DisplayName("Suppression PPM — non propriétaire → 403")
    void ppm_delete_nonProprietaire_403() throws Exception {
        prmpRepository.save(prmp("PRMP002", "ANT"));
        Dossier d = dossier(183, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP002"); dossierRepository.save(d);
        ppmRepository.save(ppm(283, 183, "PRMP002"));
        mvc.perform(delete("/api/ppms/283").header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Suppression PPM — dossier SOUMIS → 409")
    void ppm_delete_dossierSoumis_409() throws Exception {
        Dossier d = dossier(184, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        ppmRepository.save(ppm(284, 184, "PRMP001"));
        mvc.perform(delete("/api/ppms/284").header("Authorization", tokenPrmp)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Suppression — portée limitée : autre marché du même PPM et autre PPM de la même PRMP restent intacts")
    void suppression_voisinsIntacts() throws Exception {
        Dossier d = dossier(170, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        ppmRepository.save(ppm(200, 170, "PRMP001"));
        ppmRepository.save(ppm(201, 170, "PRMP001"));               // PPM voisin
        marcheRepository.save(marche(300, 170, 200));
        marcheRepository.save(marche(301, 170, 200));               // marché voisin (même PPM)
        marcheRepository.save(marche(302, 170, 201));               // marché du PPM voisin
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        marchePrevisionRepository.save(new MarchePrevision(400, 300, 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));
        marchePrevisionRepository.save(new MarchePrevision(401, 301, 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));
        marchePrevisionRepository.save(new MarchePrevision(402, 302, 1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));

        // Supprime le marché 300 → 300 + prévision 400 partis ; 301/401 et 302/402 intacts.
        mvc.perform(delete("/api/marches/300").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(300));
        org.junit.jupiter.api.Assertions.assertTrue(marchePrevisionRepository.findByIdDetail(300).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(marcheRepository.existsById(301));
        org.junit.jupiter.api.Assertions.assertFalse(marchePrevisionRepository.findByIdDetail(301).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(marcheRepository.existsById(302));

        // Supprime le PPM 200 → 200 + marché restant 301 + prévision 401 partis ; PPM 201 + marché 302 + prévision 402 intacts.
        mvc.perform(delete("/api/ppms/200").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(ppmRepository.existsById(200));
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(301));
        org.junit.jupiter.api.Assertions.assertTrue(ppmRepository.existsById(201));
        org.junit.jupiter.api.Assertions.assertTrue(marcheRepository.existsById(302));
        org.junit.jupiter.api.Assertions.assertFalse(marchePrevisionRepository.findByIdDetail(302).isEmpty());
    }

    // ------------------------------------------------------------------
    // Workflow du PV
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Workflow PV : cycle complet BROUILLON → SIGNE avec gardes et navette")
    void workflowPv_cycleComplet() throws Exception {
        // Création : le statut envoyé (SIGNE) est ignoré, le PV démarre en BROUILLON. L'idPv envoyé
        // l'est aussi (PK de seq_pv_examen) : la suite du circuit travaille sur l'id RENVOYÉ.
        String creation = mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":1,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"SIGNE\",\"nbNavettes\":99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"))
                .andExpect(jsonPath("$.nbNavettes").value(0))
                .andReturn().getResponse().getContentAsString();
        int idPv = com.jayway.jsonpath.JsonPath.read(creation, "$.idPv");
        org.junit.jupiter.api.Assertions.assertNotEquals(1, idPv, "l'idPv du corps doit être ignoré");

        soumettre(idPv, tokenMembre).andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));

        // Retour interdit au Membre.
        mvc.perform(post("/api/pv-examens/" + idPv + "/retourner").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"x\"}"))
                .andExpect(status().isForbidden());

        // Retour sans commentaire interdit (garde métier).
        mvc.perform(post("/api/pv-examens/" + idPv + "/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isConflict());

        // Retour valide par le CC.
        mvc.perform(post("/api/pv-examens/" + idPv + "/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"commentaire\":\"Corriger la synthèse\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("EN_RECTIFICATION"));

        soumettre(idPv, tokenMembre).andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));

        // ⚠️ Clôture de navette (2026-08-01) : l'acceptation pose l'avis global + le secrétaire de séance.
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));

        // Une seule signature ne suffit pas.
        signer(idPv, tokenMembre, "CTRMEM", "MEMBRE").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));

        // Co-signature → SIGNE.
        signer(idPv, tokenPresident, "CTRPRE", "PRESIDENT").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.datePv").isNotEmpty());

        // 4 navettes tracées (SOUMISSION, RETOUR_RECTIF, SOUMISSION, ACCEPTATION). L'id de navette est
        // RELU : la PK vient de seq_pv_navette et ne vaut plus 1 (NUM_NAVETTE, lui, reste 1..4).
        String navettes = mvc.perform(get("/api/pv-navettes").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        int idNavette = com.jayway.jsonpath.JsonPath.read(navettes, "$[0].idNavette");

        // PV signé non éditable.
        mvc.perform(put("/api/pv-examens/" + idPv).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":1,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"SIGNE\",\"nbNavettes\":4}"))
                .andExpect(status().isConflict());

        // Navette non supprimable.
        mvc.perform(delete("/api/pv-navettes/" + idNavette).header("Authorization", tokenMembre))
                .andExpect(status().isConflict());

        // [Auto] La PRMP du dossier reçoit une notification PV_SIGNE.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')].destinataireEmail", hasItem("prmp@min.mg")));
    }

    // ------------------------------------------------------------------
    // Comportements automatiques
    // ------------------------------------------------------------------

    @Test
    @DisplayName("[Auto] Réception complète → dossier PRET_DISPATCH")
    void auto_pretDispatch() throws Exception {
        mvc.perform(put("/api/receptions/1").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":true}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PRET_DISPATCH"));

        // [Auto] Notification PRET_DISPATCH adressée au Président et au CC de la localité.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeNotif=='PRET_DISPATCH')]", hasSize(2)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PRET_DISPATCH')].destinataireIm", hasItem("CTRPRE")))
                .andExpect(jsonPath("$[?(@.typeNotif=='PRET_DISPATCH')].destinataireIm", hasItem("CTRCC1")));
    }

    @Test
    @DisplayName("[Auto] Circuit FAVR (⚠️ 2026-08-02) : obs. levées → OBSERVATIONS_LEVEES, transmission SIGMP → "
            + "DECISION_TRANSMISE_SIGMP, archivage Assistant → CLOTURE + CLOTURE_ELIGIBLE")
    void auto_cloture() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        // PV FAVR amené à SIGNE → dossier EN_VERIFICATION, périmètre d'observations figé.
        int idPv = signerPvAvecAvis("FAVR");

        // ⚠️ Décision produit 2026-08-15 : premier passage = rappel (MAINTENUE), la PRMP rectifie et
        // resoumet, puis le vérificateur LÈVE l'observation → OBSERVATIONS_LEVEES.
        String obs = mvc.perform(get("/api/observations-pv").header("Authorization", tokenVer).param("dossier", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idObs = com.jayway.jsonpath.JsonPath.read(obs, "$[0].idObservationPv");
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"decisions\":[{\"idObservationPv\":" + idObs
                        + ",\"decision\":\"MAINTENUE\",\"precision\":\"a rectifier\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"decisions\":[{\"idObservationPv\":" + idObs + ",\"decision\":\"LEVEE\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("OBSERVATIONS_LEVEES"));

        // Le vérificateur transmet la décision à SIGMP → DECISION_TRANSMISE_SIGMP.
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("DECISION_TRANSMISE_SIGMP"));

        // L'Assistant archive le PV → dossier CLOTURE.
        mvc.perform(post("/api/pv-examens/" + idPv + "/archiver").header("Authorization", tokenAss))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CLOTURE"));

        // [Auto] Le Chargé de publication est alerté que le dossier clôturé est éligible.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='CLOTURE_ELIGIBLE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='CLOTURE_ELIGIBLE')].destinataireIm", hasItem("CTRPUB")));
    }

    @Test
    @DisplayName("Tâche du Vérificateur (⚠️ délégation ascendante 2026-08-14) : le CC statue un passage via la "
            + "paire CC→Vérificateur ; un Secrétaire (aucune paire) → 403")
    void verif_parNonVerificateur_403() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        signerPvAvecAvis("FAVR"); // dossier 1 → EN_VERIFICATION, périmètre d'observations figé

        // Négatif : un Secrétaire (aucune paire Secrétaire → Vérificateur en table) → 403.
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"decisions\":[{\"idObservationPv\":1,\"decision\":\"LEVEE\"}]}"))
                .andExpect(status().isForbidden());

        // Le CC exerce la tâche du Vérificateur (paire active CC → Vérificateur, même localité).
        // ⚠️ Décision produit 2026-08-15 : premier passage = rappel (MAINTENUE) — la levée n'est
        // possible qu'après une resoumission de la PRMP.
        passageObservationDossier1(tokenCc, "MAINTENUE", "a rectifier");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
    }

    @Test
    @DisplayName("Circuit court CC (⚠️ décisions produit 2026-08-15) : auto-désignation Secrétaire de séance "
            + "(paire active ; désactivée → 409) ; signature COMPLÈTE par le CC seul — part Membre + part CC "
            + "(paire → Membre désactivée → 403, réactivée → SIGNE) ; passage statué sur SES PROPRES observations")
    void circuitCourtCc_secretaireSeanceParDelegation_etPassageParAttributaire() throws Exception {
        // Dossier ANT auto-attribué par le CC (garde attributaire : paire CC → Membre active).
        // Enrichi PPM + ligne de marché : support du diff de rectification (2026-08-15).
        Dossier d4610 = dossierLoc(4610, "PRET_DISPATCH", "ANT", "PRMP001");
        d4610.setIdTypeDossier("DDP");
        dossierRepository.save(d4610);
        ppmRepository.save(ppm(4610, 4610, "PRMP001"));
        natureRepository.save(new Nature(1, "Travaux", null));
        Marche m4610 = marche(46100, 4610, 4610);
        m4610.setIdLigneOrigine(46100);
        m4610.setMontEstim(new java.math.BigDecimal("500000000"));
        m4610.setIdNature(1);
        m4610.setFormeMarche(cnm.prs.enums.FormeMarche.QUANTITE_FIXE);
        marcheRepository.save(m4610);
        receptionRepository.save(reception(5610, 4610, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5610,\"idReception\":5610,\"imCtrlMembre\":\"CTRCC1\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        // Examen par le CC attributaire, avec un point NON CONFORME (source des observations du PV FAVR).
        mvc.perform(post("/api/examens").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":5610,\"idDispatch\":5610,\"imCtrlMembre\":\"CTRCC1\"}"))
                .andExpect(status().isCreated());
        if (!pointsCtrlRepository.existsById(990)) {
            PointsCtrl pc = new PointsCtrl();
            pc.setIdPointCtrl(990); pc.setLibelPointCtrl("Contrôle test"); pc.setObligatoire(true);
            pc.setIdTypeDossier("DDP");
            pointsCtrlRepository.save(pc);
        }
        ExamenDetail nonConforme = new ExamenDetail();
        nonConforme.setIdDetailExamen(991); nonConforme.setIdExamen(5610); nonConforme.setIdPtControle(990);
        nonConforme.setIdDetail(46100);   // point évalué SUR la ligne de marché (complétude par marché)
        nonConforme.setConforme(false);
        examenDetailRepository.save(nonConforme);
        String pvBody = mvc.perform(post("/api/examens/5610/soumettre").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idPv = com.jayway.jsonpath.JsonPath.read(pvBody, "$.idPv");
        // Navette : le CC soumet SON projet puis l'accepte lui-même (accepteur = auteur — circuit court réel).
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        // Garde élargie, négatifs : un Secrétaire (aucune paire → Vérificateur) reste refusé…
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAVR\",\"idSecretaireSeance\":\"CTRSEC\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("délégation active vers Vérificateur")));
        // … tout comme un CC d'une AUTRE localité (paire active mais hors périmètre : CTRCC2 = TMS, dossier ANT).
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAVR\",\"idSecretaireSeance\":\"CTRCC2\"}"))
                .andExpect(status().isConflict());
        // Data-driven : paire 6 (CC → Vérificateur) DÉSACTIVÉE → l'auto-désignation du CC est refusée.
        mvc.perform(put("/api/delegation-profils/6").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":6,\"idProfileDelegant\":3,\"idProfileDelegue\":6,\"actif\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAVR\",\"idSecretaireSeance\":\"CTRCC1\"}"))
                .andExpect(status().isConflict());
        // RÉACTIVÉE → le CC SE DÉSIGNE LUI-MÊME Secrétaire de séance (décision produit : « moi-même ⤴ »).
        mvc.perform(put("/api/delegation-profils/6").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":6,\"idProfileDelegant\":3,\"idProfileDelegue\":6,\"actif\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAVR\",\"idSecretaireSeance\":\"CTRCC1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSecretaireSeance").value("CTRCC1"));
        // Signature COMPLÈTE par le CC seul (⚠️ décision produit 2026-08-15) : part Membre (attributaire)…
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());
        // … puis sa part CC. Data-driven : paire 5 (CC → Membre) DÉSACTIVÉE → signature bloquée (403,
        // l'endpoint exige « exercer la tâche du Membre ») ; RÉACTIVÉE → le CC clôt seul → SIGNE.
        mvc.perform(put("/api/delegation-profils/5").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":5,\"idProfileDelegant\":3,\"idProfileDelegue\":5,\"actif\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"role\":\"CC\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/delegation-profils/5").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":5,\"idProfileDelegant\":3,\"idProfileDelegue\":5,\"actif\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"role\":\"CC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"));
        mvc.perform(get("/api/dossiers/4610").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        // Q1a : le MÊME CC (attributaire, auteur des observations) statue le passage via la paire CC → Vérificateur
        // — tâche de PROFIL, aucune restriction au Secrétaire de séance ni garde de séparation.
        // ⚠️ Décision produit 2026-08-15 : au PREMIER passage la levée est impossible (les observations
        // du PV sont réputées avec objet) — leveePossible=false au front, LEVEE → 409, tout MAINTENUE.
        String obs = mvc.perform(get("/api/observations-pv").header("Authorization", tokenCc).param("dossier", "4610"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leveePossible").value(false))
                .andReturn().getResponse().getContentAsString();
        int idObs = com.jayway.jsonpath.JsonPath.read(obs, "$[0].idObservationPv");
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":4610,\"decisions\":[{\"idObservationPv\":" + idObs + ",\"decision\":\"LEVEE\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("première rectification")));
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":4610,\"decisions\":[{\"idObservationPv\":" + idObs
                        + ",\"decision\":\"MAINTENUE\",\"precision\":\"a rectifier\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/4610").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
        // Diff de rectification (2026-08-15) — avant toute correction : aucun instantané → 409.
        mvc.perform(get("/api/dossiers/4610/diff-rectification").header("Authorization", tokenCc))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Aucune rectification")));
        // La PRMP corrige EN PLACE (structure figée, mise à jour par idDetail). Le PREMIER PUT du cycle
        // fige l'état pré-correction ; le second ne re-fige pas (le diff compare toujours à l'AVANT).
        String entete = "{\"exercice\":2026,\"signataire\":\"PRMP Test\",\"dateSignature\":\"2026-06-01\","
                + "\"reference\":\"PPM-4610\",\"marches\":[{\"idDetail\":46100,\"formeMarche\":\"QUANTITE_FIXE\","
                + "\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\",\"designationMarche\":\"";
        mvc.perform(put("/api/saisies/ppm/4610").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(entete + "Marche 46100 rectifie\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/saisies/ppm/4610").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(entete + "Marche 46100 rectifie v2\"}]}"))
                .andExpect(status().isOk());
        // Le CC (vérificateur par délégation) voit CE QUE LA PRMP A CHANGÉ : ligne MODIFIEE,
        // designation avant → après (comparée à l'état d'AVANT la première correction), cycle non clos.
        mvc.perform(get("/api/dossiers/4610/diff-rectification").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fige").value(false))
                .andExpect(jsonPath("$.recap.modifiees").value(1))
                .andExpect(jsonPath("$.lignes[0].type").value("MODIFIEE"))
                .andExpect(jsonPath("$.lignes[0].champs[?(@.champ=='designationMarche')].avant",
                        hasItem("Marche 46100")))
                .andExpect(jsonPath("$.lignes[0].champs[?(@.champ=='designationMarche')].apres",
                        hasItem("Marche 46100 rectifie v2")));
        // La PRMP rectifie et resoumet → la levée devient possible (leveePossible=true au front).
        mvc.perform(post("/api/dossiers/4610/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk());
        // Après resoumission, le diff du cycle CLOS reste servi (fige=true, motif de la rectification).
        mvc.perform(get("/api/dossiers/4610/diff-rectification").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fige").value(true))
                .andExpect(jsonPath("$.motifMaj").value("corrige"));
        mvc.perform(get("/api/observations-pv").header("Authorization", tokenCc).param("dossier", "4610"))
                .andExpect(jsonPath("$[0].leveePossible").value(true));
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":4610,\"decisions\":[{\"idObservationPv\":" + idObs + ",\"decision\":\"LEVEE\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/4610").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("OBSERVATIONS_LEVEES"));
    }

    @Test
    @DisplayName("Badges de menu agrégés (audit front 2026-08-16) : GET /api/kpis/badges route sur le profil du "
            + "connecté et renvoie ses compteurs en un appel (mêmes DTOs que les mes-compteurs*)")
    void kpis_badgesAgreges_parProfil() throws Exception {
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profil").value("PRMP"))
                .andExpect(jsonPath("$.compteurs.brouillons").isNumber())
                .andExpect(jsonPath("$.compteurs.demandesRetraitNouvelles").isNumber());
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.profil").value("CHEF_COMMISSION"))
                .andExpect(jsonPath("$.compteurs.predispatch").isNumber());
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.profil").value("PRESIDENT"))
                .andExpect(jsonPath("$.compteurs.predispatch").isNumber());
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenSec))
                .andExpect(jsonPath("$.profil").value("SECRETAIRE"))
                .andExpect(jsonPath("$.compteurs.aReceptionner").isNumber());
    }

    @Test
    @DisplayName("Pagination serveur (audit front 2026-08-16) : ?page=&size= sur dossiers/ppms/marches → enveloppe "
            + "Page ; sans page → liste plate (rétro-compatible)")
    void listes_paginationServeur() throws Exception {
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident)
                .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)));
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/ppms").header("Authorization", tokenPresident)
                .param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mvc.perform(get("/api/marches").header("Authorization", tokenPresident)
                .param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Cookie de session HttpOnly (plan phases 1-3, 2026-08-17) : login pose PRS_SESSION SANS jeton "
            + "dans le corps (phase 3) ; l'API accepte le cookie seul ; mutation cookie-seul sans XSRF → 403, "
            + "avec XSRF → garde CSRF passée (409 métier) ; Bearer inchangé ; logout vide le cookie")
    void cookieSession_phase3() throws Exception {
        // 1) Login PRMP : cookie de session posé (HttpOnly, SameSite=Strict) ; ⚠️ phase 3 — le corps
        // ne porte PLUS le jeton (token: null), le profil d'affichage reste servi.
        org.springframework.mock.web.MockHttpServletResponse login = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"PRMP001\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(nullValue()))
                .andExpect(jsonPath("$.role").value("PRMP"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn().getResponse();
        String poseCookie = login.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("PRS_SESSION=")).findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(poseCookie, "le login doit poser PRS_SESSION");
        assertTrue(poseCookie.contains("HttpOnly"), "cookie HttpOnly attendu");
        assertTrue(poseCookie.contains("SameSite=Strict"), "SameSite=Strict attendu");
        // Le JWT ne circule plus que dans le cookie : on l'extrait du Set-Cookie.
        String jeton = poseCookie.substring("PRS_SESSION=".length(), poseCookie.indexOf(';'));
        assertTrue(!jeton.isBlank(), "le cookie doit porter le JWT");
        jakarta.servlet.http.Cookie session = new jakarta.servlet.http.Cookie("PRS_SESSION", jeton);

        // 2) Lecture authentifiée par COOKIE SEUL (aucun en-tête Authorization) + XSRF-TOKEN posé.
        org.springframework.mock.web.MockHttpServletResponse lecture =
                mvc.perform(get("/api/kpis/badges").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profil").value("PRMP"))
                .andReturn().getResponse();
        jakarta.servlet.http.Cookie xsrf = lecture.getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(xsrf,
                "le cookie XSRF-TOKEN doit être posé dès la première réponse (chargement immédiat)");

        // 3) Mutation cookie-seul SANS X-XSRF-TOKEN → 403 (garde CSRF, ciblée sur le canal cookie).
        mvc.perform(post("/api/dossiers/1/resoumettre").cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"x\"}"))
                .andExpect(status().isForbidden());
        // 4) La même avec le jeton XSRF → la garde CSRF passe : échec MÉTIER (409, dossier non EN_ATTENTE).
        mvc.perform(post("/api/dossiers/1/resoumettre")
                .cookie(session, new jakarta.servlet.http.Cookie("XSRF-TOKEN", xsrf.getValue()))
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"x\"}"))
                .andExpect(status().isConflict());
        // 5) Bearer : exempté de CSRF, comportement inchangé (même 409 métier, sans jeton XSRF).
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"x\"}"))
                .andExpect(status().isConflict());
        // 6) Logout (route publique) : le cookie de session est vidé (Max-Age=0).
        org.springframework.mock.web.MockHttpServletResponse logout = mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        String suppression = logout.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("PRS_SESSION=")).findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(suppression, "le logout doit vider PRS_SESSION");
        assertTrue(suppression.contains("Max-Age=0"), "suppression du cookie (Max-Age=0) attendue");
    }

    @Test
    @DisplayName("En-têtes de sécurité (audit front 2026-08-16) : nosniff + CSP posés sur les réponses de l'API")
    void securite_headers() throws Exception {
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; object-src 'none'; frame-ancestors 'self'"));
    }

    @Test
    @DisplayName("Diff de mise à jour — lecture ÉLARGIE (2026-08-15) : les contrôleurs du circuit lisent le diff "
            + "d'une version dans leur localité (plus de 403) ; la PRMP propriétaire inchangée")
    void diffMaj_lectureElargieAuCircuit() throws Exception {
        // Version v1 (parent) et v2 (successeur) seedées directement : même ligne (idLigneOrigine),
        // montant modifié — le diff à la volée doit la classer MODIFIEE.
        Dossier parent = dossierLoc(4620, "REMPLACE", "ANT", "PRMP001");
        parent.setIdTypeDossier("DDP");
        dossierRepository.save(parent);
        ppmRepository.save(ppm(4620, 4620, "PRMP001"));
        Marche v1 = marche(46200, 4620, 4620);
        v1.setIdLigneOrigine(46200);
        v1.setMontEstim(new java.math.BigDecimal("500000000"));
        v1.setFormeMarche(cnm.prs.enums.FormeMarche.QUANTITE_FIXE);
        v1.setDesignationMarche("Marche version");
        marcheRepository.save(v1);
        Dossier version = dossierLoc(4621, "SOUMIS", "ANT", "PRMP001");
        version.setIdTypeDossier("DDP");
        version.setIdDossierParent(4620);
        dossierRepository.save(version);
        Ppm p2 = ppm(4621, 4621, "PRMP001");
        p2.setNumMaj(1);
        p2.setMotifMaj("maj test");
        ppmRepository.save(p2);
        Marche v2 = marche(46210, 4621, 4621);
        v2.setIdLigneOrigine(46200);
        v2.setMontEstim(new java.math.BigDecimal("600000000"));
        v2.setFormeMarche(cnm.prs.enums.FormeMarche.QUANTITE_FIXE);
        v2.setDesignationMarche("Marche version");
        marcheRepository.save(v2);

        // Contrôleurs de la localité : 200 (hier 403 — le tableau partagé retrouve son surlignage).
        mvc.perform(get("/api/dossiers/4621/diff").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[0].type").value("MODIFIEE"))
                .andExpect(jsonPath("$.lignes[0].champs[?(@.champ=='montEstim')].avant", hasItem("500000000")));
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(get("/api/dossiers/4621/diff").header("Authorization", tokenVer))
                .andExpect(status().isOk());
        // La PRMP propriétaire lit toujours son diff.
        mvc.perform(get("/api/dossiers/4621/diff").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Secrétaire de séance par délégation : le Président (sans localité, paire Président → Vérificateur "
            + "active) est désignable sur un dossier de n'importe quelle localité")
    void secretaireSeance_presidentParDelegation() throws Exception {
        // PV sur l'examen 1 (dossier 1, ANT, attributaire CTRMEM), soumis par le Membre.
        int idPv = creerPvEtLireId(tokenMembre, "{\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}");
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        // Le CC accepte en désignant le PRÉSIDENT Secrétaire de séance : couvert par la paire
        // Président → Vérificateur active, et sans localité → accepté partout.
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRPRE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSecretaireSeance").value("CTRPRE"));
    }

    @Test
    @DisplayName("Vérification réservée aux PV FAVR : avis FAV → 409")
    void verif_surAvisNonReserve_409() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis("FAV"); // dossier 1 → EN_VERIFICATION, PV 81 SIGNE avis FAV
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":81,\"obsLevees\":true}"))
                .andExpect(status().isConflict());
    }

    /** Statue l'unique observation du périmètre du dossier 1 via le circuit des observations (⚠️ 2026-08-02). */
    private void passageObservationDossier1(String tokenVer, String decision, String precision) throws Exception {
        String obs = mvc.perform(get("/api/observations-pv").header("Authorization", tokenVer).param("dossier", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idObs = com.jayway.jsonpath.JsonPath.read(obs, "$[0].idObservationPv");
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":1,\"decisions\":[{\"idObservationPv\":" + idObs + ",\"decision\":\"" + decision
                        + "\"" + (precision == null ? "" : ",\"precision\":\"" + precision + "\"") + "}]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Passage obs. MAINTENUE (⚠️ 2026-08-02) → EN_ATTENTE_DECISION_PRMP + notif OBSERVATION_VERIFICATION (PRMP) ; saisie libre refusée 409")
    void verif_obsNonLevees_attenteDecisionPrmp() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis("FAVR"); // dossier 1 → EN_VERIFICATION, périmètre figé
        // 1er passage : observation MAINTENUE → dossier EN_ATTENTE_DECISION_PRMP + notif PRMP.
        passageObservationDossier1(tokenVer, "MAINTENUE", "reserve a lever");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
        // La PRMP du dossier reçoit l'observation (refeDossier + rappel auto-généré) via OBSERVATION_VERIFICATION.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='OBSERVATION_VERIFICATION')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='OBSERVATION_VERIFICATION')].destinataireRef", hasItem("PRMP001")));
        // Saisie libre (texte client) refusée : le périmètre est figé → 409.
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":82,\"observation\":\"ok\",\"obsLevees\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Worklist : obs. non levées → dossier dans /en-attente-prmp ET conservé dans /a-verifier (lecture seule), visible PRMP via ?statut")
    void verif_obsNonLevees_attentePrmp_worklist() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis("FAVR"); // dossier 1 → EN_VERIFICATION
        passageObservationDossier1(tokenVer, "MAINTENUE", "averina");
        // Vérificateur : le dossier est dans « En attente PRMP » ET reste dans « à vérifier » (lecture seule).
        mvc.perform(get("/api/dossiers/en-attente-prmp").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));   // conservé (EN_ATTENTE_DECISION_PRMP)
        // PRMP propriétaire : le dossier apparaît via le filtre de statut.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "EN_ATTENTE_DECISION_PRMP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));
    }

    @Test
    @DisplayName("Worklist : un dossier EN_ATTENTE_DECISION_PRMP est en lecture seule — vérification refusée 409")
    void verif_attentePrmp_lectureSeule_409() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis("FAVR"); // dossier 1 → EN_VERIFICATION
        passageObservationDossier1(tokenVer, "MAINTENUE", "averina"); // → dossier 1 EN_ATTENTE_DECISION_PRMP
        // Le dossier reste dans « à vérifier » mais toute nouvelle vérification est refusée (lecture seule).
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":88,\"observation\":\"encore\",\"obsLevees\":true}"))
                .andExpect(status().isConflict());
    }

    /**
     * Amène le dossier 1 à EN_ATTENTE_DECISION_PRMP (PV FAVR signé + observation MAINTENUE par CTRVER).
     * Rend l'{@code idPv} attribué par {@code seq_pv_examen}, sur lequel porte la vérification créée.
     */
    private int dossier1EnAttenteDecisionPrmp(String tokenVer) throws Exception {
        int idPv = signerPvAvecAvis("FAVR");
        passageObservationDossier1(tokenVer, "MAINTENUE", "averina");
        return idPv;
    }

    @Test
    @DisplayName("Resoumission PRMP : EN_ATTENTE_DECISION_PRMP → EN_VERIFICATION + notif vérificateur + audit + motif visible")
    void resoumission_retourEnVerification() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        int idPv = dossier1EnAttenteDecisionPrmp(tokenVer);

        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        // Notif RECTIFICATION_PRMP au vérificateur du dossier (CTRVER).
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='RECTIFICATION_PRMP')].destinataireIm", hasItem("CTRVER")));
        // Motif visible sur le passage côté vérificateur.
        mvc.perform(get("/api/verifications").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idPv==" + idPv + ")].motifRectif", hasItem("corrige")));
        // Le vérificateur statue de nouveau (dossier de retour en EN_VERIFICATION) : LEVÉE → OBSERVATIONS_LEVEES.
        passageObservationDossier1(tokenVer, "LEVEE", null);
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("OBSERVATIONS_LEVEES"));
    }

    @Test
    @DisplayName("Resoumission PRMP : motif vide → 400")
    void resoumission_motifVide_400() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        dossier1EnAttenteDecisionPrmp(tokenVer);
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Resoumission PRMP : dossier hors EN_ATTENTE_DECISION_PRMP (EN_VERIFICATION) → 409")
    void resoumission_horsAttente_409() throws Exception {
        signerPvAvecAvis("FAVR"); // dossier 1 → EN_VERIFICATION (pas EN_ATTENTE)
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Historique d'échanges (dossier clôturé) : observations + rectifications PRMP ; accessible PRMP et vérificateur")
    void historique_echanges_dossierCloture() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        int idPv = signerPvAvecAvis("FAVR"); // dossier 1 → EN_VERIFICATION, périmètre figé
        // Passage 1 : observation MAINTENUE → resoumission (rect1).
        passageObservationDossier1(tokenVer, "MAINTENUE", "obs1");
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"rect1\"}"))
                .andExpect(status().isOk());
        // Passage 2 : observation MAINTENUE → resoumission (rect2).
        passageObservationDossier1(tokenVer, "MAINTENUE", "obs2");
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"rect2\"}"))
                .andExpect(status().isOk());
        // Passage final : observation LEVÉE → OBSERVATIONS_LEVEES, puis SIGMP + archivage → CLOTURE.
        passageObservationDossier1(tokenVer, "LEVEE", null);
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + idPv + "/archiver").header("Authorization", tokenAss))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));

        // Historique : 3 observations (passages auto-générés, dont la levée finale) + 2 rectifications.
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                // Fil entrelacé (chaîne de réponse) : passage, rect1, passage, rect2, passage final.
                .andExpect(jsonPath("$[0].type").value("OBSERVATION"))
                .andExpect(jsonPath("$[1].type").value("RECTIFICATION")).andExpect(jsonPath("$[1].texte").value("rect1"))
                .andExpect(jsonPath("$[1].acteur").value("PRMP001"))
                .andExpect(jsonPath("$[2].type").value("OBSERVATION"))
                .andExpect(jsonPath("$[3].type").value("RECTIFICATION")).andExpect(jsonPath("$[3].texte").value("rect2"))
                .andExpect(jsonPath("$[4].type").value("OBSERVATION"))
                .andExpect(jsonPath("$[4].obsLevees").value(true));
        // Accessible aussi par la PRMP.
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("Historique d'échanges : dossier non clôturé (EN_VERIFICATION) → 403")
    void historique_echanges_horsCloture_403() throws Exception {
        signerPvAvecAvis("FAVR"); // dossier 1 → EN_VERIFICATION (pas CLOTURE)
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vérification : identité enregistrée = JWT (CurrentUser.ref), jamais le corps ; ID auto-généré")
    void verif_identiteDepuisJwt() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        int idPv = signerPvAvecAvis("FAVR");
        // ⚠️ 2026-08-02 : le passage est créé PAR LE SERVEUR depuis les décisions — l'identité vient du JWT.
        passageObservationDossier1(tokenVer, "MAINTENUE", null);
        mvc.perform(get("/api/verifications").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==" + idPv + ")].imCtrlVerif", hasItem("CTRVER")))
                .andExpect(jsonPath("$[?(@.idPv==" + idPv + ")].idVerification").exists())
                .andExpect(jsonPath("$[?(@.idPv==" + idPv + ")].dateVerif").exists());
    }

    @Test
    @DisplayName("Worklist vérificateur « à-vérifier » : EN_VERIFICATION de la localité ; scope localité respecté")
    void worklist_aVerifier_listeEnVerification() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenVerTms = bearer("CTRVER2", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER2", "TMS");
        signerPvAvecAvis("FAVR"); // dossier 1 (ANT) → EN_VERIFICATION

        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));
        // Exclusif de l'historique « vérifiés ».
        mvc.perform(get("/api/dossiers/verifies").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.idDossier==1)]", hasSize(0)));
        // Scope localité : un vérificateur TMS ne voit pas le dossier ANT.
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVerTms))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(0)));
    }

    @Test
    @DisplayName("Worklist vérificateur « vérifiés » (⚠️ bascule 2026-08-04) : le dossier y entre à la transmission SIGMP")
    void worklist_verifies_inclutAutoClotures() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis("FAV"); // dossier 1 (ANT) → EN_VERIFICATION, PV 71 SIGNE

        // Avant transmission : encore une action à faire → dans « à vérifier », pas dans « vérifiés ».
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));

        // Transmission de la décision à SIGMP → bascule instantanée vers « vérifiés ».
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/verifies").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.idDossier==1)]", hasSize(1)));
        // Exclusif de la file « à vérifier ».
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(0)));
    }

    @Test
    @DisplayName("Création PV : imCtrlMembre dérivé de l'attribution (dispatch), le corps est ignoré")
    void creationPv_imCtrlMembreDeriveDeLAttribution() throws Exception {
        // Examen 1 → dispatch 1 → attributaire CTRMEM ; le corps tente d'usurper « USURP ».
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":60,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"USURP\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRMEM"));
    }

    @Test
    @DisplayName("Création PV : examen sans Membre attributaire (dispatch) → 409")
    void creationPv_examenSansAttributaire_409() throws Exception {
        dossierRepository.save(dossier(60, "DISPATCHE"));
        receptionRepository.save(reception(60, 60, "CTRCC1", true));
        dispatchRepository.save(dispatch(60, 60, "CTRCC1", null)); // dispatch sans attributaire
        examenRepository.save(examen(60, 60, "CTRMEM"));
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":61,\"idExamen\":60,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Circuit complet : Réception → PRET_DISPATCH → Dispatch → Examen soumis → PV(navette → SIGNE) → SIGMP → Archivage → CLOTURE")
    void circuitComplet_boutEnBout() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Dossier de test neuf (id 3), distinct des dossiers seedés.
        dossierRepository.save(dossier(3, "EXAMINE"));

        // 1) Réception complète par le Secrétaire → [Auto] dossier PRET_DISPATCH.
        //    L'id de réception (PK technique) est alloué par le serveur (séquence) : on le capture pour la suite.
        String recBody = mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":3,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idRec = com.jayway.jsonpath.JsonPath.read(recBody, "$.idReception");
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("PRET_DISPATCH"));

        // 2) Dispatch par le CC (titulaire dans sa localité ANT).
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":3,\"idReception\":" + idRec + ",\"imCtrlDispatch\":\"CTRCC1\",\"imCtrlCc\":\"CTRCC1\","
                        + "\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        // Le dispatch fait avancer le dossier à DISPATCHE (règle ajoutée).
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("DISPATCHE"));

        // 3) Examen par le Membre (brouillon) puis SOUMISSION → dossier EXAMINE + Projet de PV créé.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":3,\"idDispatch\":3,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());
        String pvBody = mvc.perform(post("/api/examens/3/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"))
                .andReturn().getResponse().getContentAsString();
        int idPv = com.jayway.jsonpath.JsonPath.read(pvBody, "$.idPv");
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EXAMINE"));

        // 4) Navette : soumettre → accepter (clôture de navette : avis FAV + secrétaire), co-signature → SIGNE.
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"))
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        // Un seul signataire ne suffit pas : le PV reste PROJET_ACCEPTE.
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));

        // 5) Signature (FAV) → EN_VERIFICATION ; transmission SIGMP puis archivage Assistant → CLOTURE.
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":3}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + idPv + "/archiver").header("Authorization", tokenAss))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CLOTURE"));
    }

    @Test
    @DisplayName("Transitions interdites : rôle non autorisé → 403, saut d'étape du PV → 409")
    void transitionsInterdites() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");

        // Rôle : un Vérificateur ne peut pas dispatcher → 403.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"interimDispatch\":false}"))
                .andExpect(status().isForbidden());

        // PV en BROUILLON sur l'examen 1 — id relu (PK de seq_pv_examen), plus deviné.
        int idPv = creerPvEtLireId(tokenMembre, "{\"idExamen\":1,\"idAvis\":\"FAV\","
                + "\"imCtrlMembre\":\"CTRMEM\",\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}");

        // Rôle : un Secrétaire ne peut pas accepter un projet de PV (réservé CC / Président) → 403.
        // Ciblé sur le PV RÉEL : sur un id inexistant, le 403 ne prouverait plus que la garde de rôle
        // passe avant la recherche — un 404 aurait aussi bien pu convenir au test.
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRSEC\"}"))
                .andExpect(status().isForbidden());

        // Saut d'étape : un PV en BROUILLON ne peut être ni accepté ni signé → 409.
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Préconditions du circuit : dispatch hors PRET_DISPATCH / doublon, examen hors circuit, vérif hors PV SIGNE → 409")
    void preconditionsCircuit_bloquent() throws Exception {
        // (a) Dispatch d'un dossier non PRET_DISPATCH (dossier 2 = EXAMINE, réception 2 sans dispatch) → 409.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":40,\"idReception\":2,\"interimDispatch\":false}"))
                .andExpect(status().isConflict());

        // (b) Anti-doublon : un dossier PRET_DISPATCH qui a déjà un dispatch → 2e dispatch refusé.
        dossierRepository.save(dossier(14, "PRET_DISPATCH"));
        receptionRepository.save(reception(24, 14, "CTRSEC", true));
        dispatchRepository.save(dispatch(41, 24, "CTRCC1", "CTRMEM"));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":42,\"idReception\":24,\"interimDispatch\":false}"))
                .andExpect(status().isConflict());

        // (c) Examen d'un dossier non dispatché (dispatch 1 → dossier 1 = EXAMINE, pas DISPATCHE) → 409.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":40,\"idDispatch\":1,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isConflict());

        // (d) Vérification sur un PV non SIGNE (BROUILLON) → 409 (par un vérificateur, pour atteindre la garde PV SIGNE).
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":5,\"idExamen\":1,\"idAvis\":\"FAVR\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":5,\"obsLevees\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Dispatch → dossier DISPATCHE ; examen refusé tant que le dossier n'est pas dispatché")
    void dispatch_avanceDossierADispatche() throws Exception {
        // A) Dossier PRET_DISPATCH avec un dispatch SEEDÉ en direct (le dossier reste PRET_DISPATCH) :
        //    l'examen est refusé car le dossier n'est pas DISPATCHE.
        dossierRepository.save(dossier(15, "PRET_DISPATCH"));
        receptionRepository.save(reception(25, 15, "CTRSEC", true));
        dispatchRepository.save(dispatch(45, 25, "CTRCC1", "CTRMEM"));
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":45,\"idDispatch\":45,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isConflict());

        // B) Dispatch VIA L'API → le dossier passe à DISPATCHE, et l'examen devient alors permis.
        dossierRepository.save(dossier(16, "PRET_DISPATCH"));
        receptionRepository.save(reception(26, 16, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":46,\"idReception\":26,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/16").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("DISPATCHE"));
        // L'examen est permis pour le Membre attributaire (CTRMEM).
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":46,\"idDispatch\":46,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Soumission dossier (§3.1, Option C) : la PRMP soumet → SOUMIS (réf. générée à la réception, null avant) + Secrétaire/CC notifiés ; re-soumission → 409")
    void soumissionDossier_ok() throws Exception {
        // Brouillon PPM de la PRMP courante (PRMP001), localisé ANT, avec son PPM.
        Dossier d = dossier(3, "BROUILLON");
        d.setRefeDossier(null);
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        Ppm ppm = ppmLocalise(30, 3, "ANT");
        ppm.setIdPrmp("PRMP001");
        ppmRepository.save(ppm);
        marcheRepository.save(marche(31, 3, 30)); // un PPM doit comporter au moins un marché (règle ajoutée)

        // Soumission par la PRMP → 200, statut SOUMIS, refeDossier null (réf. posée à la réception).
        mvc.perform(post("/api/dossiers/3/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"))
                .andExpect(jsonPath("$.refeDossier").doesNotExist());

        // Le Secrétaire et le CC de la localité sont notifiés.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='DOSSIER_SOUMIS')].destinataireIm", hasItem("CTRSEC")))
                .andExpect(jsonPath("$[?(@.typeNotif=='DOSSIER_SOUMIS')].destinataireIm", hasItem("CTRCC1")));

        // Re-soumission → 409 (le dossier n'est plus BROUILLON).
        mvc.perform(post("/api/dossiers/3/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Soumission dossier (§3.1) refus : dossier d'une autre PRMP → 403, localité indéterminable → 400, non-PRMP → 403")
    void soumissionDossier_refus() throws Exception {
        // Jeton PRMP SANS localité (pour forcer l'échec de résolution de localité).
        String tokenPrmpSansLoc = bearer("PRMP001", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP001", null);
        // Une autre PRMP propriétaire.
        prmpRepository.save(prmp("PRMPXX", "ANT"));
        // (4) Brouillon DAO appartenant à une AUTRE PRMP.
        Dossier d4 = dossier(4, "BROUILLON");
        d4.setIdTypeDossier("DMC");
        d4.setIdPrmp("PRMPXX");
        dossierRepository.save(d4);
        // (5) Brouillon DAO de PRMP001, sans localité ni PPM.
        Dossier d5 = dossier(5, "BROUILLON");
        d5.setRefeDossier(null);
        d5.setIdTypeDossier("DMC");
        d5.setIdPrmp("PRMP001");
        dossierRepository.save(d5);

        // (a) Dossier d'une autre PRMP → 403.
        mvc.perform(post("/api/dossiers/4/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        // (b) Dossier nu + PRMP sans localité → aucune localité résoluble → 400.
        mvc.perform(post("/api/dossiers/5/soumettre").header("Authorization", tokenPrmpSansLoc))
                .andExpect(status().isBadRequest());
        // (c) Un non-PRMP ne peut pas soumettre → 403.
        mvc.perform(post("/api/dossiers/5/soumettre").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Soumission dossier SANS PPM (DAO/MAOO) : la localité du dossier (dérivée de l'entité à la saisie) → refeDossier null (réf. à la réception) + ID_LOCALITE estampillé + Secrétaire notifié et le voit")
    void soumissionDossier_sansPpm() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Brouillon DAO sans PPM, de PRMP001, dont la localité (ANT) a été dérivée de l'entité à la saisie.
        Dossier d = dossier(6, "BROUILLON");
        d.setRefeDossier(null);
        d.setIdTypeDossier("DMC");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);

        // PRMP001 soumet → localité = ANT (celle du dossier), SOUMIS, refeDossier null + ID_LOCALITE (plus de repli PRMP).
        mvc.perform(post("/api/dossiers/6/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"))
                .andExpect(jsonPath("$.refeDossier").doesNotExist())
                .andExpect(jsonPath("$.idLocalite").value("ANT"));
        // Le Secrétaire de la localité (ANT) est notifié.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='DOSSIER_SOUMIS')].destinataireIm", hasItem("CTRSEC")));
        // Et le dossier est désormais visible par le Secrétaire AVANT toute réception (via ID_LOCALITE).
        mvc.perform(get("/api/dossiers/6").header("Authorization", tokenSec))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Affectations PRMP↔entité (§3.1) : lecture scopée, unicité une PRMP active par entité (409), écriture Admin only")
    void prmpEntites_scopeUniciteEtAutorisation() throws Exception {
        // Lecture scopée : l'Administrateur voit toutes les affectations (les 2 seedées de PRMP001).
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')]", hasSize(2)));
        // La PRMP ne voit que les siennes.
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')]", hasSize(2)));
        // Une autre PRMP (sans affectation) ne voit rien.
        String tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", null);
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // Un contrôleur (ni Admin ni PRMP) → liste vide.
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        // Unicité : l'entité 1 est déjà rattachée à PRMP001 → tentative pour une autre PRMP → 409.
        prmpRepository.save(prmp("PRMP002", "ANT"));
        mvc.perform(post("/api/prmp-entites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"PRMP002\",\"idEntiteContract\":1,\"actif\":true}"))
                .andExpect(status().isConflict());

        // Écriture réservée à l'Admin : une PRMP ne peut pas créer d'affectation → 403.
        entiteContractRepository.save(entite(3, 1, "ANT"));
        mvc.perform(post("/api/prmp-entites").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"PRMP001\",\"idEntiteContract\":3,\"actif\":true}"))
                .andExpect(status().isForbidden());

        // L'Admin affecte une entité libre (3) à PRMP001 → 201, active.
        mvc.perform(post("/api/prmp-entites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"PRMP001\",\"idEntiteContract\":3,\"actif\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEntiteContract").value(3))
                .andExpect(jsonPath("$.actif").value(true));
    }

    @Test
    @DisplayName("Visibilité dossier via ID_LOCALITE : dossier localisé (sans PPM ni réception) visible par sa localité, pas une autre")
    void visibiliteDossierViaIdLocalite() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Dossiers estampillés, sans PPM ni réception.
        Dossier dAnt = dossier(7, "RECU");
        dAnt.setIdLocalite("ANT");
        dossierRepository.save(dAnt);
        Dossier dTms = dossier(8, "RECU");
        dTms.setIdLocalite("TMS");
        dossierRepository.save(dTms);

        mvc.perform(get("/api/dossiers").header("Authorization", tokenSec))
                .andExpect(jsonPath("$[?(@.idDossier==7)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==8)]", hasSize(0)));
        mvc.perform(get("/api/dossiers/7").header("Authorization", tokenSec)).andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/8").header("Authorization", tokenSec)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Visibilité dossier via PPM (Option A) : un dossier à PPM de la localité est visible/consultable sans réception")
    void visibiliteDossierViaPpm() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Dossier soumis (PPM de localité ANT), AUCUNE réception.
        dossierRepository.save(dossier(3, "RECU"));
        ppmRepository.save(ppmLocalise(30, 3, "ANT"));
        // Dossier soumis (PPM de localité TMS), AUCUNE réception.
        dossierRepository.save(dossier(4, "RECU"));
        ppmRepository.save(ppmLocalise(40, 4, "TMS"));

        // Le Secrétaire d'ANT voit le dossier 3 (PPM de sa localité), pas le 4 (PPM TMS).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==3)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==4)]", hasSize(0)));

        // Accès direct : 200 sur le 3 (sa localité via PPM), 403 sur le 4 (autre localité).
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenSec))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/4").header("Authorization", tokenSec))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Garde réception : la 1ʳᵉ réception doit se faire dans la localité du dossier (via ID_LOCALITE)")
    void receptionDansLocaliteDuDossier() throws Exception {
        // Dossier estampillé TMS, aucune réception préalable.
        Dossier d = dossier(9, "RECU");
        d.setIdLocalite("TMS");
        dossierRepository.save(d);

        // Le Président (toutes localités) peut réceptionner (succès d'abord → pas de rollback-only).
        mvc.perform(post("/api/receptions").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":9,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRPRE\",\"complet\":false}"))
                .andExpect(status().isCreated());
        // Un contrôleur d'ANT (CC, délégué Secrétaire) ne peut pas réceptionner un dossier TMS → 403.
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":9,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Façade saisie PPM : dossier BROUILLON + PPM + marché (mode auto), invisible des contrôleurs puis visible après soumission")
    void saisiePpm_facade() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");

        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        // Localité dérivée de l'entité 1 (= ANT) ; AUCUN id (dossier/PPM/marché) dans le corps → alloués serveur.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,"
                + "\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-60\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.idTypeDossier").value("DDP"))
                .andExpect(jsonPath("$.idLocalite").value("ANT"))
                .andExpect(jsonPath("$.idPrmp").value("PRMP001"))
                .andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        org.junit.jupiter.api.Assertions.assertTrue(idDoss >= 100001);   // PK serveur (séquence), pas de collision avec les seeds
        // La ligne de marché conserve le mode SAISI (2) — plus de détermination automatique.
        mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + ")].idMode", hasItem(2)));
        // Le brouillon est invisible du Secrétaire.
        mvc.perform(get("/api/dossiers/" + idDoss).header("Authorization", tokenSec))
                .andExpect(status().isForbidden());
        // Soumission → SOUMIS → devient visible.
        mvc.perform(post("/api/dossiers/" + idDoss + "/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SOUMIS"));
        mvc.perform(get("/api/dossiers/" + idDoss).header("Authorization", tokenSec))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Saisie PPM — nature/mode par libellé : résolus (dédup normalisée) ou créés à la volée (tr_nature/tr_mode)")
    void saisiePpm_natureModeALaVolee() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));   // référentiel existant
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        long naturesAvant = natureRepository.count();
        long modesAvant = modePassationRepository.count();

        // Marché A : natureLibelle « TRAVAUX » (≈ existant → résolu, aucun doublon) + modeLibelle « Achat Direct » (créé).
        // Marché B : natureLibelle « Fournitures et services » (créé). Aucun idNature/idMode fourni.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-AV\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":1000000,\"natureLibelle\":\"TRAVAUX\",\"modeLibelle\":\"Achat Direct\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"B\",\"montEstim\":2000000,\"natureLibelle\":\"Fournitures et services\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        // Dédup : « TRAVAUX » ne crée PAS de doublon → +1 nature seulement (Fournitures et services) ; +1 mode (Achat Direct).
        org.junit.jupiter.api.Assertions.assertEquals(naturesAvant + 1, natureRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(modesAvant + 1, modePassationRepository.count());

        // Les marchés portent des ids résolus (A.idNature = Travaux existant = 1 ; A.idMode et B.idNature non nuls).
        mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='A')].idNature", hasItem(1)))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='A')].idMode",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='B')].idNature",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.notNullValue())));
    }

    @Test
    @DisplayName("Saisie PPM — mode + suffixe de source (RPI/PIP) collé au libellé : résolu au noyau, jamais RPI→PIP, aucun doublon")
    void saisiePpm_modeSuffixeSource() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);
        modePassationRepository.save(new ModePassation(4, "Consultation des Prix Ouverte", null, null, null, null));
        modePassationRepository.save(new ModePassation(8, "CONSULTATION DE PRIX OUVERTE PIP", null, null, null, null));
        long modesAvant = modePassationRepository.count();   // 3 : aucun ne doit être créé à la volée

        // Trois marchés dont le modeLibelle porte un suffixe de source collé (cas réel PDF/ré-import).
        // CPO-RPI : suffixe RPI (aucune variante RPI) → mode BASE idMode=4 (JAMAIS idMode=8 « … PIP »).
        // CPO-PIP : suffixe PIP (source exacte) → variante distincte idMode=8.
        // AOO-RPI : coquille singulier + RPI → idMode=1 déclencheur d'AGPM, résolu (pas de création sans le drapeau).
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-SRC\","
                + "\"marches\":["
                + "{\"designationMarche\":\"CPO-RPI\",\"montEstim\":500000000,\"idNature\":1,\"modeLibelle\":\"CONSULTATION DE PRIX OUVERTE RPI\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"CPO-PIP\",\"montEstim\":400000000,\"idNature\":1,\"modeLibelle\":\"CONSULTATION DE PRIX OUVERTE PIP\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"AOO-RPI\",\"montEstim\":600000000,\"idNature\":1,\"modeLibelle\":\"APPEL D'OFFRE OUVERT RPI\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        // Aucun mode créé à la volée : les trois libellés se résolvent au référentiel existant.
        org.junit.jupiter.api.Assertions.assertEquals(modesAvant, modePassationRepository.count());

        mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='CPO-RPI')].idMode", hasItem(4)))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='CPO-PIP')].idMode", hasItem(8)))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.designationMarche=='AOO-RPI')].idMode", hasItem(1)));
    }

    @Test
    @DisplayName("Référentiel catégorie-entites — CRUD : lecture ouverte, écriture ADMINISTRATEUR (403 sinon), {id}=libellé")
    void categorieEntites_crud() throws Exception {
        // Lecture ouverte à tout authentifié.
        mvc.perform(get("/api/categorie-entites").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        String body = "{\"libelle\":\"SERVICE\",\"niveauHierarchique\":5}";
        // POST réservé ADMINISTRATEUR (Membre → 403).
        mvc.perform(post("/api/categorie-entites").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/categorie-entites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("SERVICE"))
                .andExpect(jsonPath("$.niveauHierarchique").value(5));
        // GET {id} = libellé.
        mvc.perform(get("/api/categorie-entites/SERVICE").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.niveauHierarchique").value(5));
        // PUT (niveau modifié) — ADMINISTRATEUR.
        mvc.perform(put("/api/categorie-entites/SERVICE").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"libelle\":\"SERVICE\",\"niveauHierarchique\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.niveauHierarchique").value(7));
        // DELETE — ADMINISTRATEUR.
        mvc.perform(delete("/api/categorie-entites/SERVICE").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/categorie-entites/SERVICE").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Entité contractante — niveauHierarchique DÉRIVÉ de categorieEntite (source unique, valeur client ignorée) ; catégorie inconnue → 400")
    void entiteContract_niveauDeriveDeLaCategorie() throws Exception {
        categorieEntiteRepository.save(new cnm.prs.entity.CategorieEntite("MINISTERE", 1));
        categorieEntiteRepository.save(new cnm.prs.entity.CategorieEntite("DIRECTION", 4));

        // POST : catégorie DIRECTION ; le client tente niveau=99 → ignoré, dérivé à 4 (organigramme 1 seedé au setup).
        String post = "{\"idEntiteContract\":100,\"libelleEntite\":\"Direction X\",\"adresse\":\"Rue Y\","
                + "\"categorieEntite\":\"DIRECTION\",\"idOrganigramme\":1,\"niveauHierarchique\":99}";
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(post))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categorieEntite").value("DIRECTION"))
                .andExpect(jsonPath("$.niveauHierarchique").value(4));

        // PUT : catégorie → MINISTERE, niveau re-dérivé à 1 (le 99 fourni est ignoré).
        String put = "{\"idEntiteContract\":100,\"libelleEntite\":\"Direction X\",\"adresse\":\"Rue Y\","
                + "\"categorieEntite\":\"MINISTERE\",\"idOrganigramme\":1,\"niveauHierarchique\":99}";
        mvc.perform(put("/api/entite-contracts/100").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(put))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.niveauHierarchique").value(1));

        // Catégorie hors référentiel → 400.
        String bad = "{\"idEntiteContract\":101,\"libelleEntite\":\"Z\",\"adresse\":\"W\","
                + "\"categorieEntite\":\"INCONNU\",\"idOrganigramme\":1}";
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Entité contractante — création par la PRMP (import PPM) : 201 + niveau dérivé + rattachement EN ATTENTE (actif=false), idEntiteParent null accepté")
    void entiteContract_creationParPrmp_autoRattachementEnAttente() throws Exception {
        categorieEntiteRepository.save(new cnm.prs.entity.CategorieEntite("DIRECTION", 4));
        // PRMP001 crée une entité (autorité hors périmètre) : PK assignée client, idEntiteParent absent.
        String post = "{\"idEntiteContract\":200,\"libelleEntite\":\"Nouvelle Direction\",\"adresse\":\"Rue Z\","
                + "\"categorieEntite\":\"DIRECTION\",\"idOrganigramme\":1}";
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(post))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEntiteContract").value(200))
                .andExpect(jsonPath("$.niveauHierarchique").value(4))          // dérivé de DIRECTION
                .andExpect(jsonPath("$.idEntiteParent").value(nullValue()));   // null accepté
        // Rattachement auto EN ATTENTE créé pour la PRMP courante.
        List<cnm.prs.entity.PrmpEntite> liens = prmpEntiteRepository.findByIdPrmp("PRMP001").stream()
                .filter(l -> l.getIdEntiteContract() != null && l.getIdEntiteContract().intValue() == 200).toList();
        org.junit.jupiter.api.Assertions.assertEquals(1, liens.size());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, liens.get(0).getActif());
        // Visible dans le GET scopé PRMP (le front filtrera actif=true pour la sélection).
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idEntiteContract==200)].actif", hasItem(false)));
    }

    @Test
    @DisplayName("Entité contractante — création par l'ADMIN : aucun rattachement prmp-entites auto (l'Admin n'est pas une PRMP enregistrée)")
    void entiteContract_creationParAdmin_sansRattachement() throws Exception {
        long liensAvant = prmpEntiteRepository.count();
        String post = "{\"idEntiteContract\":201,\"libelleEntite\":\"Entite Admin\",\"adresse\":\"Rue A\",\"idOrganigramme\":1}";
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(post))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(liensAvant, prmpEntiteRepository.count());
    }

    @Test
    @DisplayName("Rattachement prmp-entites — approbation ADMIN d'un lien EN ATTENTE : PUT {actif:true} l'active (visible scopé PRMP) ; unicité 409 à l'activation si conflit")
    void prmpEntite_approbationAdmin_activeEtUnicite() throws Exception {
        entiteContractRepository.save(entite(300, 1, "ANT"));                 // entité cible
        prmpEntiteRepository.save(prmpEntite(50, "PRMP001", 300, false));     // lien EN ATTENTE (comme auto-créé)
        // ADMIN approuve → actif=true.
        String put = "{\"idPrmpEntite\":50,\"idPrmp\":\"PRMP001\",\"idEntiteContract\":300,\"actif\":true}";
        mvc.perform(put("/api/prmp-entites/50").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(put))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(true));
        // Devient sélectionnable par la PRMP (GET scopé, actif=true).
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idEntiteContract==300)].actif", hasItem(true)));
        // Unicité à l'activation : un 2e lien EN ATTENTE (PRMP002 ↔ même entité) ne peut PAS être activé → 409.
        prmpRepository.save(prmp("PRMP002", "ANT"));
        prmpEntiteRepository.save(prmpEntite(51, "PRMP002", 300, false));
        String put2 = "{\"idPrmpEntite\":51,\"idPrmp\":\"PRMP002\",\"idEntiteContract\":300,\"actif\":true}";
        mvc.perform(put("/api/prmp-entites/51").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(put2))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Entité contractante — PUT persiste idLocalite (régression : le PUT l'ignorait, désormais aligné sur le POST)")
    void entiteContract_putPersisteIdLocalite() throws Exception {
        // POST avec idLocalite=ANT (persiste déjà), puis PUT vers TMS → doit persister aussi.
        String post = "{\"idEntiteContract\":250,\"libelleEntite\":\"E\",\"adresse\":\"A\",\"idOrganigramme\":1,\"idLocalite\":\"ANT\"}";
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(post))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocalite").value("ANT"));
        String put = "{\"idEntiteContract\":250,\"libelleEntite\":\"E\",\"adresse\":\"A\",\"idOrganigramme\":1,\"idLocalite\":\"TMS\"}";
        mvc.perform(put("/api/entite-contracts/250").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(put))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLocalite").value("TMS"));      // réponse du PUT
        // Relecture (GET) : idLocalite bien persisté.
        mvc.perform(get("/api/entite-contracts/250").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLocalite").value("TMS"));
    }

    @Test
    @DisplayName("Saisie PPM — numCompte absent de tr_compte : créé à la volée (pas de 409 FK sur t_marche.NUM_COMPTE)")
    void saisiePpm_compteALaVolee() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        // numCompte « 9999-NEW » absent de tr_compte → sans résolution-ou-création, l'INSERT du marché viole la FK (409).
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-CPT\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":1000000,\"idNature\":1,\"idMode\":2,\"numCompte\":\"9999-NEW\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // Le compte a été créé à la volée dans tr_compte (résolution, jamais suppression).
        org.junit.jupiter.api.Assertions.assertTrue(compteRepository.existsById("9999-NEW"));
    }

    @Test
    @DisplayName("Saisie PPM — bénéficiaires cohérents : 1 ligne t_service_beneficiaire par élément + soa/compte à la volée")
    void saisiePpm_beneficiaires_ok() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        long benefAvant = serviceBeneficiaireRepository.count();

        // montEstim 3 000 000 = 1 000 000 + 2 000 000 ; soaCode/numCompte inexistants → créés à la volée.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-BEN\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":3000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaCode\":\"00-21-0-J00-00000\",\"numCompte\":\"C-A\",\"ancMontBenef\":1000000},"
                + "{\"soaCode\":\"00-21-0-J00-11111\",\"numCompte\":\"C-B\",\"ancMontBenef\":2000000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        org.junit.jupiter.api.Assertions.assertEquals(benefAvant + 2, serviceBeneficiaireRepository.count());
        org.junit.jupiter.api.Assertions.assertTrue(soaBeneficiaireRepository.existsById("00-21-0-J00-00000"));
        org.junit.jupiter.api.Assertions.assertTrue(soaBeneficiaireRepository.existsById("00-21-0-J00-11111"));
        org.junit.jupiter.api.Assertions.assertTrue(compteRepository.existsById("C-A"));
        org.junit.jupiter.api.Assertions.assertTrue(compteRepository.existsById("C-B"));
    }

    @Test
    @DisplayName("Saisie PPM — bénéficiaires incohérents : Σ ancMontBenef ≠ montEstim → 400 ciblé marches[0].beneficiaires")
    void saisiePpm_beneficiaires_incoherent_400() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        // montEstim 3 000 000 mais Σ = 1 000 000 + 1 500 000 = 2 500 000 → 400.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-KO\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":3000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaCode\":\"S1\",\"numCompte\":\"C1\",\"ancMontBenef\":1000000},"
                + "{\"soaCode\":\"S2\",\"numCompte\":\"C2\",\"ancMontBenef\":1500000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[0].beneficiaires"));
    }

    @Test
    @DisplayName("UGPM : crée un dossier sous sa PRMP de tutelle (cree_par=UGPM), ne peut PAS soumettre (403) ; la PRMP le voit et le soumet")
    void ugpm_creation_scoping_soumissionReserveePrmp() throws Exception {
        // Token UGPM : ref = PRMP001 (tutelle) → périmètre de la PRMP ; login « UGPM1 » = créateur.
        String tokenUgpm = bearer("UGPM1", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"X\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-UGPM\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":1000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenUgpm)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        // Dossier stampé PRMP de tutelle (PRMP001) + cree_par = login UGPM.
        Dossier d = dossierRepository.findById(idDoss).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("PRMP001", d.getIdPrmp());
        org.junit.jupiter.api.Assertions.assertEquals("UGPM1", d.getCreePar());

        // L'UGPM ne peut PAS soumettre → 403 (réservé PRMP).
        mvc.perform(post("/api/dossiers/" + idDoss + "/soumettre").header("Authorization", tokenUgpm))
                .andExpect(status().isForbidden());

        // La PRMP de tutelle voit le dossier (scoping périmètre) et le soumet → soumis_par = login PRMP.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + ")]", hasSize(1)));
        mvc.perform(post("/api/dossiers/" + idDoss + "/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals("PRMP001",
                dossierRepository.findById(idDoss).orElseThrow().getSoumisPar());
    }

    @Test
    @DisplayName("Admin crée une UGPM + compte actif ; login UGPM → rôle UGPM, périmètre = PRMP de tutelle ; tutelle inconnue → 409")
    void ugpm_admin_creation_et_login() throws Exception {
        // Identité obligatoire (mêmes champs que la PRMP, sauf arrêté/date de nomination).
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean Paul\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMX\",\"libelle\":\"UGPM Test\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugpmx\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPrmpTutelle").value("PRMP001"))
                .andExpect(jsonPath("$.nomUgpm").value("Rakoto"))
                .andExpect(jsonPath("$.dateCin").value("2010-05-20"))
                .andExpect(jsonPath("$.emailUgpm").value("ugpm@ex.mg"))
                .andExpect(jsonPath("$.login").value("ugpmx"));   // login exposé (lecture seule)
        org.junit.jupiter.api.Assertions.assertTrue(ugpmRepository.existsById("UGPMX"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpmx").isPresent());

        // Login réel → rôle UGPM, ref = PRMP de tutelle (le scoping fonctionne comme une PRMP).
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ugpmx\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("UGPM"))
                .andExpect(jsonPath("$.ref").value("PRMP001"));

        // PRMP de tutelle inconnue → 409.
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMY\",\"libelle\":\"X\",\"idPrmpTutelle\":\"NOPE\"," + identite
                        + "\"login\":\"ugpmy\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/ugpms multipart : fiche + pièces CIN/PHOTO (pas d'arrêté) ; GET pièce ; CIN>5Mo/ARRETE/photo-PDF → 400 ; non-admin → 403")
    void ugpm_creationAvecPieces() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        byte[] dataJson = ("{\"idUgpm\":\"UGPJ\",\"libelle\":\"UGPM Pieces\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                + "\"login\":\"ugpj\",\"motDePasse\":\"Ugpm@1234\"}").getBytes(StandardCharsets.UTF_8);
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };
        byte[] pdf = "%PDF-1.4 pas une image".getBytes(StandardCharsets.US_ASCII);

        // --- Écritures/lectures réussies d'abord (une exception métier marque la tx rollback-only). ---
        // Création multipart : data JSON + CIN (JPEG) + photo (PNG). Pas d'arrêté.
        mvc.perform(multipart("/api/ugpms").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", dataJson))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg))
                .file(new MockMultipartFile("photo", "photo.png", "image/png", png)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idUgpm").value("UGPJ"));

        // JSON pur (sans pièces) → 201 (rétro-compat).
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPJ2\",\"libelle\":\"X\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugpj2\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        // Téléchargement des pièces stockées (CIN + PHOTO).
        mvc.perform(get("/api/ugpms/UGPJ/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
        mvc.perform(get("/api/ugpms/UGPJ/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        // --- Cas d'erreur ensuite. ---
        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(get("/api/ugpms/UGPJ/pieces/CIN").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());

        // Pièce absente → 404.
        mvc.perform(get("/api/ugpms/INCONNU/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());

        // type = ARRETE_NOMIN → 400 (l'UGPM n'a pas d'arrêté), au dépôt comme au téléchargement.
        mvc.perform(multipart("/api/ugpms/UGPJ/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "a.pdf", "application/pdf", pdf)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/ugpms/UGPJ/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());

        // Photo = image seulement : un PDF en PHOTO → 400.
        mvc.perform(multipart("/api/ugpms/UGPJ/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "p.pdf", "application/pdf", pdf)))
                .andExpect(status().isBadRequest());

        // CIN > 5 Mo → 400 (contrôle de taille au niveau service).
        byte[] gros = new byte[6 * 1024 * 1024];
        gros[0] = (byte) 0xFF; gros[1] = (byte) 0xD8; gros[2] = (byte) 0xFF;   // JPEG magic
        byte[] data3 = ("{\"idUgpm\":\"UGPJ3\",\"libelle\":\"X\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                + "\"login\":\"ugpj3\",\"motDePasse\":\"Ugpm@1234\"}").getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/ugpms").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data3))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", gros)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/ugpms/{id}/pieces/{type} : supprime une pièce (UGPM conservée) ; PHOTO intacte ; absente/inconnu → 404 ; ARRETE_NOMIN → 400 ; non-admin → 403")
    void ugpm_suppressionPiece() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        byte[] data = ("{\"idUgpm\":\"UGPDP\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                + "\"login\":\"ugpdp\",\"motDePasse\":\"Ugpm@1234\"}").getBytes(StandardCharsets.UTF_8);
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };

        // --- Écritures / lectures 200 d'abord. ---
        // Création avec CIN + PHOTO.
        mvc.perform(multipart("/api/ugpms").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg))
                .file(new MockMultipartFile("photo", "photo.png", "image/png", png)))
                .andExpect(status().isCreated());
        // Suppression de la CIN → 204.
        mvc.perform(delete("/api/ugpms/UGPDP/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        // La PHOTO subsiste, l'UGPM aussi.
        mvc.perform(get("/api/ugpms/UGPDP/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/api/ugpms/UGPDP").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
        // En base : il ne reste que la PHOTO sous la clé UGPDP.
        org.junit.jupiter.api.Assertions.assertEquals(1, pieceJointeRepository.findByLogin("UGPDP").size());

        // --- Cas d'erreur ensuite. ---
        // CIN désormais absente → 404 (téléchargement et re-suppression).
        mvc.perform(get("/api/ugpms/UGPDP/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/ugpms/UGPDP/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // type = ARRETE_NOMIN → 400 (l'UGPM n'a pas d'arrêté).
        mvc.perform(delete("/api/ugpms/UGPDP/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
        // UGPM inconnue → 404.
        mvc.perform(delete("/api/ugpms/INCONNU/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(delete("/api/ugpms/UGPDP/pieces/PHOTO").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/ugpms/{id} : purge aussi les pièces (t_piece_jointe) — pas d'orphelin")
    void ugpm_deleteFiche_purgePieces() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Purge\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        byte[] data = ("{\"idUgpm\":\"UGPPG\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                + "\"login\":\"ugppg\",\"motDePasse\":\"Ugpm@1234\"}").getBytes(StandardCharsets.UTF_8);
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        mvc.perform(multipart("/api/ugpms").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(1, pieceJointeRepository.findByLogin("UGPPG").size());

        // DELETE de la fiche → 204 + pièces purgées (et compte retiré, déjà couvert ailleurs).
        mvc.perform(delete("/api/ugpms/UGPPG").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(pieceJointeRepository.findByLogin("UGPPG").isEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/register/ugpm : auto-inscription publique EN_ATTENTE ; login refusé avant validation ; validée par l'Admin → login OK (UGPM) ; tutelle inconnue / déjà pris → 409")
    void ugpm_autoInscription() throws Exception {
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        String data = "{\"login\":\"ugpm.reg\",\"motDePasse\":\"Ugpm@1234\",\"idUgpm\":\"UGPREG\","
                + "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\",\"cin\":\"101234567890\","
                + "\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\",\"emailUgpm\":\"ugpm.reg@ex.mg\","
                + "\"telUgpm\":\"0340000000\",\"idPrmpTutelle\":\"PRMP001\"}";
        MockMultipartFile dataPart = new MockMultipartFile("data", "", "application/json",
                data.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile cin = new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg);

        // Auto-inscription publique (sans token) → 201, compte EN_ATTENTE.
        mvc.perform(multipart("/api/auth/register/ugpm").file(dataPart).file(cin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeActeur").value("UGPM"))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.actif").value(false));

        // Connexion refusée avant validation → 401.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ugpm.reg\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isUnauthorized());

        // Visible par l'Admin dans les inscriptions en attente (type UGPM + tutelle).
        mvc.perform(get("/api/inscriptions/en-attente").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.login=='ugpm.reg')].type", hasItem("UGPM")))
                .andExpect(jsonPath("$[?(@.login=='ugpm.reg')].idPrmpTutelle", hasItem("PRMP001")));

        // Validation par l'Admin (pas d'entités à instruire) → compte activé.
        mvc.perform(post("/api/inscriptions/ugpm.reg/valider").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutCompte").value("ACTIF"));

        // Login OK maintenant → rôle UGPM, ref = PRMP de tutelle.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ugpm.reg\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("UGPM"))
                .andExpect(jsonPath("$.ref").value("PRMP001"));

        // Pièces ré-affectées à la validation : accessibles par l'id (UGPREG), plus par le login.
        mvc.perform(get("/api/ugpms/UGPREG/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
        org.junit.jupiter.api.Assertions.assertTrue(pieceJointeRepository.findByLogin("ugpm.reg").isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(1, pieceJointeRepository.findByLogin("UGPREG").size());

        // --- Cas d'erreur ensuite. ---
        // Tutelle inconnue → 409.
        String dataNope = data.replace("UGPREG", "UGPRG2").replace("ugpm.reg", "ugpm.rg2")
                .replace("\"idPrmpTutelle\":\"PRMP001\"", "\"idPrmpTutelle\":\"NOPE\"");
        mvc.perform(multipart("/api/auth/register/ugpm")
                .file(new MockMultipartFile("data", "", "application/json", dataNope.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isConflict());
        // login déjà pris → 409 (réutilise ugpm.reg).
        mvc.perform(multipart("/api/auth/register/ugpm").file(dataPart).file(cin))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/ugpms/{id} : lit une UGPM (identité) ; id inconnu → 404")
    void ugpm_findById() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMG\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugpmg\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/ugpms/UGPMG").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUgpm").value("UGPMG"))
                .andExpect(jsonPath("$.idPrmpTutelle").value("PRMP001"))
                .andExpect(jsonPath("$.nomUgpm").value("Rakoto"))
                .andExpect(jsonPath("$.emailUgpm").value("ugpm@ex.mg"))
                .andExpect(jsonPath("$.login").value("ugpmg"));   // login exposé (lecture seule)

        // Id inconnu → 404.
        mvc.perform(get("/api/ugpms/INCONNU").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/ugpms/par-tutelle/{idPrmp} : liste les UGPM d'une PRMP ; tutelle inconnue → liste vide")
    void ugpm_parTutelle() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        for (String im : new String[] { "UGPMT1", "UGPMT2" }) {
            mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idUgpm\":\"" + im + "\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                            + "\"login\":\"" + im.toLowerCase() + "\",\"motDePasse\":\"Ugpm@1234\"}"))
                    .andExpect(status().isCreated());
        }

        // Les 2 UGPM de PRMP001 (matricules attendus, login exposé).
        mvc.perform(get("/api/ugpms/par-tutelle/PRMP001").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idUgpm", containsInAnyOrder("UGPMT1", "UGPMT2")))
                .andExpect(jsonPath("$[?(@.idUgpm=='UGPMT1')].idPrmpTutelle", containsInAnyOrder("PRMP001")));

        // PRMP de tutelle inconnue → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/ugpms/par-tutelle/NOPE").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/ugpms/par-localite/{idLocalite} : UGPM via la localité de leur PRMP de tutelle ; localité sans PRMP → vide ; non-admin → 403")
    void ugpm_parLocalite() throws Exception {
        // PRMP001 est rattachée (ACTIVE) à ANT via le seed. Une UGPM sous PRMP001 hérite donc de ANT.
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPLOCA\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugploca\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        // par-localite ANT → contient l'UGPM (via la localité de sa PRMP de tutelle).
        mvc.perform(get("/api/ugpms/par-localite/ANT").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idUgpm", hasItem("UGPLOCA")));

        // Localité sans PRMP rattachée → liste vide (filtre) : l'UGPM existante n'y fuit pas.
        mvc.perform(get("/api/ugpms/par-localite/ZZ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Non-admin → 403.
        mvc.perform(get("/api/ugpms/par-localite/ANT").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/ugpms/par-nom/{nom} : recherche partielle insensible à la casse ; aucun résultat → vide ; non-admin → 403")
    void ugpm_parNom() throws Exception {
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPNOM\",\"idPrmpTutelle\":\"PRMP001\","
                        + "\"nomUgpm\":\"RANDRIANARISOA\",\"prenomsUgpm\":\"Jean\","
                        + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                        + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\","
                        + "\"login\":\"ugpnom\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        // Partiel « NDRIA » → trouve RANDRIANARISOA.
        mvc.perform(get("/api/ugpms/par-nom/NDRIA").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idUgpm", hasItem("UGPNOM")));
        // Insensible à la casse : « randria ».
        mvc.perform(get("/api/ugpms/par-nom/randria").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idUgpm", hasItem("UGPNOM")));
        // Aucun résultat → liste vide (pas de 404).
        mvc.perform(get("/api/ugpms/par-nom/ZZQQ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        // Non-admin → 403.
        mvc.perform(get("/api/ugpms/par-nom/RANDRIA").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/ugpms/suppression-lot : tolérant → bilan supprimes/introuvables (+ comptes nettoyés) ; liste vide → 400")
    void ugpm_suppressionLot() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        for (String im : new String[] { "UGPML1", "UGPML2" }) {
            mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idUgpm\":\"" + im + "\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                            + "\"login\":\"" + im.toLowerCase() + "\",\"motDePasse\":\"Ugpm@1234\"}"))
                    .andExpect(status().isCreated());
        }

        // Lot tolérant : 2 existantes + 1 absente → 200, bilan, pas d'échec global.
        mvc.perform(post("/api/ugpms/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"matricules\":[\"UGPML1\",\"UGPML2\",\"INCONNU\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supprimes", containsInAnyOrder("UGPML1", "UGPML2")))
                .andExpect(jsonPath("$.introuvables", containsInAnyOrder("INCONNU")));
        // UGPM + comptes supprimés.
        org.junit.jupiter.api.Assertions.assertFalse(ugpmRepository.existsById("UGPML1"));
        org.junit.jupiter.api.Assertions.assertFalse(ugpmRepository.existsById("UGPML2"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpml1").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpml2").isEmpty());

        // Liste vide → 400 (validation @NotEmpty).
        mvc.perform(post("/api/ugpms/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"matricules\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/ugpms/{id} : modifie les champs métier ; id inconnu → 404 ; tutelle inconnue → 409")
    void ugpm_modifier() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMM\",\"libelle\":\"Avant\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugpmm\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        // Modification des champs métier (libellé + identité).
        String modif = "{\"libelle\":\"Apres\",\"idPrmpTutelle\":\"PRMP001\",\"nomUgpm\":\"Randria\","
                + "\"prenomsUgpm\":\"Paul\",\"cin\":\"101234567890\",\"dateCin\":\"2011-06-21\","
                + "\"lieuCin\":\"Toamasina\",\"emailUgpm\":\"ugpm.new@ex.mg\",\"telUgpm\":\"0341112222\"}";
        mvc.perform(put("/api/ugpms/UGPMM").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(modif))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUgpm").value("UGPMM"))       // PK inchangée
                .andExpect(jsonPath("$.libelle").value("Apres"))
                .andExpect(jsonPath("$.nomUgpm").value("Randria"))
                .andExpect(jsonPath("$.emailUgpm").value("ugpm.new@ex.mg"))
                .andExpect(jsonPath("$.lieuCin").value("Toamasina"))
                .andExpect(jsonPath("$.login").value("ugpmm"));      // login inchangé, exposé (lecture seule)

        // Le compte n'est pas touché par la modification.
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpmm").isPresent());

        // Id inconnu → 404.
        mvc.perform(put("/api/ugpms/INCONNU").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(modif))
                .andExpect(status().isNotFound());

        // Tutelle inconnue → 409.
        String modifNope = modif.replace("\"idPrmpTutelle\":\"PRMP001\"", "\"idPrmpTutelle\":\"NOPE\"");
        mvc.perform(put("/api/ugpms/UGPMM").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(modifNope))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PUT /api/ugpms/{id} multipart : maj identité + remplace pièces ; pièce absente inchangée ; JSON conservé ; inconnu → 404 ; photo PDF → 400")
    void ugpm_modificationAvecPieces() throws Exception {
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPPUT\",\"libelle\":\"Avant\",\"idPrmpTutelle\":\"PRMP001\","
                        + "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\",\"cin\":\"101234567890\","
                        + "\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\",\"emailUgpm\":\"ugpm@ex.mg\","
                        + "\"telUgpm\":\"0340000000\",\"login\":\"ugpput\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());

        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };
        byte[] pdf = "%PDF-1.4 pas une image".getBytes(StandardCharsets.US_ASCII);
        byte[] data = ("{\"libelle\":\"Apres\",\"idPrmpTutelle\":\"PRMP001\",\"nomUgpm\":\"Randria\","
                + "\"prenomsUgpm\":\"Paul\",\"cin\":\"101234567890\",\"dateCin\":\"2011-06-21\","
                + "\"lieuCin\":\"Toamasina\",\"emailUgpm\":\"ugpm.new@ex.mg\",\"telUgpm\":\"0341112222\"}")
                .getBytes(StandardCharsets.UTF_8);

        // --- Écritures réussies d'abord. ---
        // PUT multipart : maj identité + dépose CIN (JPEG) + PHOTO (PNG). MockMvc : builder POST forcé en PUT.
        mvc.perform(multipart("/api/ugpms/UGPPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg))
                .file(new MockMultipartFile("photo", "photo.png", "image/png", png))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Apres"))
                .andExpect(jsonPath("$.nomUgpm").value("Randria"))
                .andExpect(jsonPath("$.emailUgpm").value("ugpm.new@ex.mg"));
        mvc.perform(get("/api/ugpms/UGPPUT/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
        mvc.perform(get("/api/ugpms/UGPPUT/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));

        // PUT multipart avec SEULEMENT la CIN (PNG) : CIN remplacée, PHOTO laissée inchangée.
        mvc.perform(multipart("/api/ugpms/UGPPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("cin", "cin.png", "image/png", png))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isOk());
        mvc.perform(get("/api/ugpms/UGPPUT/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));   // remplacée
        mvc.perform(get("/api/ugpms/UGPPUT/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));   // inchangée

        // PUT JSON pur (sans pièces) → 200 (rétro-compat).
        mvc.perform(put("/api/ugpms/UGPPUT").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(new String(data, StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        // --- Cas d'erreur ensuite. ---
        // UGPM inconnue → 404.
        mvc.perform(multipart("/api/ugpms/INCONNU").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isNotFound());

        // Photo = image seulement : un PDF en PHOTO → 400.
        mvc.perform(multipart("/api/ugpms/UGPPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("photo", "p.pdf", "application/pdf", pdf))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/ugpms/{id} : supprime l'UGPM et son compte ; id inconnu → 404")
    void ugpm_delete() throws Exception {
        String identite = "\"nomUgpm\":\"Rakoto\",\"prenomsUgpm\":\"Jean\","
                + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\",\"lieuCin\":\"Antananarivo\","
                + "\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\",";
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMD\",\"idPrmpTutelle\":\"PRMP001\"," + identite
                        + "\"login\":\"ugpmd\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertTrue(ugpmRepository.existsById("UGPMD"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpmd").isPresent());

        mvc.perform(delete("/api/ugpms/UGPMD").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        // UGPM et compte associé supprimés.
        org.junit.jupiter.api.Assertions.assertFalse(ugpmRepository.existsById("UGPMD"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ugpmd").isEmpty());

        // Id inconnu → 404.
        mvc.perform(delete("/api/ugpms/INCONNU").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("UGPM : création sans champ d'identité obligatoire (nomUgpm) → 400")
    void creation_ugpm_sans_identite_400() throws Exception {
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMI\",\"idPrmpTutelle\":\"PRMP001\",\"prenomsUgpm\":\"Jean\","
                        + "\"cin\":\"101234567890\",\"dateCin\":\"2010-05-20\","
                        + "\"lieuCin\":\"Antananarivo\",\"emailUgpm\":\"ugpm@ex.mg\",\"telUgpm\":\"0340000000\","
                        + "\"login\":\"ugpmi\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("UGPM : création sans PRMP de tutelle → 400 (validation)")
    void creation_ugpm_sans_prmp_tutelle_400() throws Exception {
        mvc.perform(post("/api/ugpms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idUgpm\":\"UGPMZ\",\"libelle\":\"X\",\"login\":\"ugpmz\",\"motDePasse\":\"Ugpm@1234\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PRMP : ne peut pas soumettre un dossier d'une autre PRMP → 403 (hors périmètre)")
    void prmp_ne_soumet_pas_dossier_autre_prmp_403() throws Exception {
        // Dossier BROUILLON appartenant à une autre PRMP : le contrôle propriétaire (403) précède statut/contenu.
        Dossier autre = dossier(64, "BROUILLON");
        autre.setIdTypeDossier("DDP");
        autre.setIdPrmp("PRMP999");
        autre.setIdLocalite("ANT");
        dossierRepository.save(autre);
        mvc.perform(post("/api/dossiers/64/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PRMP : sa liste de dossiers inclut les BROUILLON créés par ses UGPM")
    void prmp_voit_brouillons_de_ses_ugpm() throws Exception {
        // UGPM (ref = PRMP001) crée un dossier BROUILLON → stampé PRMP001.
        String tokenUgpm = bearer("UGPM1", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);
        String resp = mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenUgpm)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DAO\",\"idEntiteContract\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        org.junit.jupiter.api.Assertions.assertEquals("UGPM1",
                dossierRepository.findById(idDoss).orElseThrow().getCreePar());

        // La PRMP de tutelle voit ce BROUILLON dans sa liste (scoping par périmètre ID_PRMP).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss + " && @.statut=='BROUILLON')]", hasSize(1)));
    }

    @Test
    @DisplayName("Façade saisie DAO : dossier DAO BROUILLON ; type PPM refusé")
    void saisieDossier_dao() throws Exception {
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DAO\",\"idEntiteContract\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.idTypeDossier").value("DMC"))       // famille déduite du sous-type
                .andExpect(jsonPath("$.idSousType").value("DAO"))          // sous-type choisi (legacy idTypeDossier)
                .andExpect(jsonPath("$.idLocalite").value("ANT"))      // dérivée de l'entité 1
                .andExpect(jsonPath("$.idEntiteContract").value(1))
                .andExpect(jsonPath("$.idDossier").isNumber());        // PK attribuée par le serveur (séquence)
        // Le type PPM est refusé par cette façade (utiliser /api/saisies/ppm).
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"PPM\",\"idEntiteContract\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Auto-PK : un id envoyé par le client est IGNORÉ ; le serveur attribue depuis la séquence")
    void autopk_idClientIgnore() throws Exception {
        String resp = mvc.perform(post("/api/dossiers").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":777,\"statut\":\"BROUILLON\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        org.junit.jupiter.api.Assertions.assertNotEquals(777, id);          // id client ignoré
        org.junit.jupiter.api.Assertions.assertTrue(id >= 100001);          // PK serveur (séquence seq_dossier)
    }

    @Test
    @DisplayName("Intégrité type↔contenu : PPM sans t_ppm → soumission 409 ; PPM attaché à un dossier DAO → 409")
    void integrite_typeContenu() throws Exception {
        // Brouillon PPM sans aucun t_ppm rattaché → soumission refusée.
        Dossier dPpmVide = dossier(63, "BROUILLON");
        dPpmVide.setIdTypeDossier("DDP");
        dPpmVide.setIdPrmp("PRMP001");
        dPpmVide.setIdLocalite("ANT");
        dossierRepository.save(dPpmVide);
        mvc.perform(post("/api/dossiers/63/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict());

        // Brouillon DAO (sans propriétaire) ; y attacher un PPM via l'endpoint Admin → 409.
        Dossier dDao = dossier(64, "BROUILLON");
        dDao.setIdTypeDossier("DMC");
        dossierRepository.save(dDao);
        mvc.perform(post("/api/ppms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPpm\":64,\"idDossier\":64,\"exercice\":2026,\"signataire\":\"X\","
                        + "\"dateSignature\":\"2026-01-10\",\"reference\":\"P64\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Soumission PPM : un PPM sans marché → 409 ; avec au moins un marché → OK (⚠️ règle ajoutée)")
    void soumission_ppmSansMarche() throws Exception {
        // Brouillon PPM de PRMP001 avec son t_ppm mais AUCUN marché → soumission refusée (409).
        Dossier d = dossier(90, "BROUILLON");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(90, 90, "PRMP001"));
        mvc.perform(post("/api/dossiers/90/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict());

        // Ajout d'au moins une ligne de marché → la soumission passe (SOUMIS).
        marcheRepository.save(marche(900, 90, 90));
        mvc.perform(post("/api/dossiers/90/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));
    }

    @Test
    @DisplayName("Endpoints bruts restreints : POST /api/dossiers et /api/ppms réservés Admin ; façade réservée PRMP")
    void endpointsBruts_restreints() throws Exception {
        String dossierBody = "{\"idDossier\":65,\"statut\":\"BROUILLON\"}";
        // PRMP ne peut pas créer un dossier brut → 403 ; Admin → 201.
        mvc.perform(post("/api/dossiers").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(dossierBody))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dossiers").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(dossierBody))
                .andExpect(status().isCreated());
        // PRMP ne peut pas créer un PPM brut → 403.
        mvc.perform(post("/api/ppms").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPpm\":65,\"idDossier\":65,\"exercice\":2026,\"signataire\":\"X\","
                        + "\"dateSignature\":\"2026-01-10\",\"reference\":\"P65\"}"))
                .andExpect(status().isForbidden());
        // La façade de saisie est réservée PRMP : un Membre → 403.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DAO\",\"idEntiteContract\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Reprise brouillon PRMP : la PRMP voit/rouvre son brouillon DAO (via t_dossier.idPrmp), pas une autre PRMP")
    void brouillonDao_visiblePourSaProprePrmp() throws Exception {
        String tokenAutrePrmp = bearer("PRMPYY", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPYY", "ANT");
        // Brouillon DAO de PRMP001 (aucun PPM).
        Dossier d = dossier(80, "BROUILLON");
        d.setIdTypeDossier("DMC");
        d.setIdPrmp("PRMP001");
        dossierRepository.save(d);

        // PRMP001 voit son brouillon dans sa liste et peut l'ouvrir.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==80)]", hasSize(1)));
        mvc.perform(get("/api/dossiers/80").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("BROUILLON"));
        // Une autre PRMP ne le voit pas.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenAutrePrmp))
                .andExpect(jsonPath("$[?(@.idDossier==80)]", hasSize(0)));
        mvc.perform(get("/api/dossiers/80").header("Authorization", tokenAutrePrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Saisie : la localité vient de l'ENTITÉ choisie (même PRMP, 2 localités) ; entité hors PRMP → 403 ; entité sans localité → 400")
    void saisieLocalite_deLEntite() throws Exception {
        // Entité 9 existe mais non affectée à PRMP001 ; entité 10 affectée mais sans localité.
        entiteContractRepository.save(entite(9, 1, "ANT"));
        entiteContractRepository.save(entite(10, 1, null));
        prmpEntiteRepository.save(prmpEntite(10, "PRMP001", 10, true));

        // Même PRMP (PRMP001), 2 entités de localités différentes → 2 dossiers de localités différentes.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":82,\"idTypeDossier\":\"DAO\",\"idEntiteContract\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocalite").value("ANT"));
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":83,\"idTypeDossier\":\"DAO\",\"idEntiteContract\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocalite").value("TMS"));
        // Entité non rattachée à la PRMP → 403.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":84,\"idTypeDossier\":\"DAO\",\"idEntiteContract\":9}"))
                .andExpect(status().isForbidden());
        // Entité rattachée mais sans localité → 400.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":85,\"idTypeDossier\":\"DAO\",\"idEntiteContract\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Édition d'un brouillon PPM : en-tête mis à jour + lignes réconciliées (maj/ajout/retrait), mode recalculé")
    void editionPpm_facade() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "AOO", null, null, null, null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        modePassationRepository.save(new ModePassation(4, "Cotation", null, null, null, null));

        // Saisie initiale (sans id) : marché 150M (mode SAISI 4) et 500M (mode SAISI 2), entité 1 (ANT).
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        String creation = "{\"idEntiteContract\":1,\"exercice\":2026,"
                + "\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-120-v1\","
                + "\"marches\":[{\"montEstim\":150000000,\"idNature\":1,\"idMode\":4,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"montEstim\":500000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String cresp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(creation))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(cresp, "$.idDossier");

        // Le frontend lit les marchés du brouillon pour connaître leurs PK serveur (réconciliation par idDetail).
        String m1 = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = com.jayway.jsonpath.JsonPath.read(m1, "$[?(@.idDossier==" + idDoss + ")].idDetail");
        int idM150 = Math.min(ids.get(0), ids.get(1));   // créé en premier (150M)
        int idM500 = Math.max(ids.get(0), ids.get(1));   // créé en second (500M)
        mvc.perform(get("/api/marches/" + idM150).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.idMode").value(4));
        mvc.perform(get("/api/marches/" + idM500).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.idMode").value(2));

        // Édition : en-tête + idM150 → 1,5 Md (mode SAISI 1), idM500 retiré, nouvelle ligne 500M ajoutée (mode SAISI 2).
        // Règle corrigée : une ligne NOUVELLE à l'édition exige ≥1 processus (comme au POST) ; la ligne
        // mise à jour idM150 omet la liste → ses processus existants sont conservés.
        String edition = "{\"exercice\":2027,\"signataire\":\"RABE Maj\",\"dateSignature\":\"2026-02-01\",\"reference\":\"PPM-120-v2\","
                + "\"marches\":[{\"idDetail\":" + idM150 + ",\"montEstim\":1500000000,\"idNature\":1,\"idMode\":1,\"statut\":\"PREVU\"},"
                + "{\"montEstim\":500000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"));
        // En-tête mis à jour (brouillon lu par son id — hors liste « Mes PPM & marchés »).
        int idPpm120 = ppmRepository.findByIdDossier(idDoss).get(0).getIdPpm();
        mvc.perform(get("/api/ppms/" + idPpm120).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.reference").value("PPM-120-v2"))
                .andExpect(jsonPath("$.exercice").value(2027));
        // idM150 : mode saisi 1 conservé ; idM500 supprimé → 404 ; la nouvelle ligne 500M (PK ≠ idM500) a le mode saisi 2.
        mvc.perform(get("/api/marches/" + idM150).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.idMode").value(1));
        mvc.perform(get("/api/marches/" + idM500).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        String m2 = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString();
        List<Integer> idsV2 = com.jayway.jsonpath.JsonPath.read(m2, "$[?(@.idDossier==" + idDoss + ")].idDetail");
        int idNew = idsV2.get(0).intValue() == idM150 ? idsV2.get(1) : idsV2.get(0);
        org.junit.jupiter.api.Assertions.assertNotEquals(idM500, idNew);
        mvc.perform(get("/api/marches/" + idNew).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.idMode").value(2));
    }

    @Test
    @DisplayName("Édition de brouillon : gardes — dossier soumis → 409 ; non-propriétaire → 403")
    void editionPpm_gardes() throws Exception {
        String tokenAutrePrmp = bearer("PRMPZZ", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPZZ", "ANT");
        String edition = "{\"exercice\":2026,\"signataire\":\"X\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R\",\"marches\":[]}";
        // Brouillon PPM (121) de PRMP001 — pour le test de propriété.
        String r121 = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"X\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R121\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss121 = com.jayway.jsonpath.JsonPath.read(r121, "$.idDossier");
        // Brouillon PPM (122) de PRMP001 — soumis ensuite (donc non éditable).
        String r122 = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"X\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R122\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss122 = com.jayway.jsonpath.JsonPath.read(r122, "$.idDossier");
        int idPpm122 = ppmRepository.findByIdDossier(idDoss122).get(0).getIdPpm();
        marcheRepository.save(marche(1220, idDoss122, idPpm122)); // un PPM doit comporter au moins un marché avant soumission
        mvc.perform(post("/api/dossiers/" + idDoss122 + "/soumettre").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        // Dossier soumis → non éditable.
        mvc.perform(put("/api/saisies/ppm/" + idDoss122).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isConflict());
        // Brouillon d'une autre PRMP → 403.
        mvc.perform(put("/api/saisies/ppm/" + idDoss121).header("Authorization", tokenAutrePrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Édition PPM — ré-import complet (régression) : les sous-objets des lignes (bénéficiaires, lots, processus) sont créés comme au POST")
    void editionPpm_reImportComplet_sousObjetsCrees() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        capmRepository.save(new Capm(2, "OUVERTURE", 2, null, null));
        // Brouillon initial (import v1) : 1 ligne complète (bénéficiaire + lot + processus).
        String creation = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-RI\","
                + "\"marches\":[{\"designationMarche\":\"Ancienne ligne\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaCode\":\"00-61-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":1000000}],"
                + "\"lots\":[{\"designationLot\":\"Lot ancien\"}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-03-01\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(creation))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int ancienId = marcheRepository.findByIdDossier(idDoss).get(0).getIdDetail();

        // Ré-import front : PUT avec lignes SANS idDetail et sous-objets complets (payload front inchangé).
        String edition = "{\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-RI\","
                + "\"marches\":[{\"designationMarche\":\"Ligne reimportee (contrat cadre)\",\"formeMarche\":\"CONTRAT_CADRE\","
                + "\"montEstim\":3000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaCode\":\"00-61-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":1000000},"
                + "{\"soaCode\":\"00-62-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":2000000}],"
                + "\"lots\":[{\"designationLot\":\"Lot 1\"},{\"designationLot\":\"Lot 2\"}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-03-01\"},"
                + "{\"idCapm\":2,\"dateDebut\":\"2026-03-02\",\"dateFin\":\"2026-04-01\"}]}]}";
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isOk());

        List<Marche> marches = marcheRepository.findByIdDossier(idDoss);
        org.junit.jupiter.api.Assertions.assertEquals(1, marches.size());
        int nouvelId = marches.get(0).getIdDetail();
        org.junit.jupiter.api.Assertions.assertNotEquals(ancienId, nouvelId);
        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.FormeMarche.CONTRAT_CADRE,
                marches.get(0).getFormeMarche());
        // Les sous-objets de la nouvelle ligne existent (le bug les laissait tous à zéro).
        org.junit.jupiter.api.Assertions.assertEquals(2, marchePrevisionRepository.findByIdDetail(nouvelId).size());
        org.junit.jupiter.api.Assertions.assertEquals(2, lotRepository.findByIdDetail(nouvelId).size());
        org.junit.jupiter.api.Assertions.assertEquals(2, serviceBeneficiaireRepository.findAll().stream()
                .filter(b -> b.getIdDetail() != null && b.getIdDetail().intValue() == nouvelId).count());
        // Ceux de l'ancienne ligne retirée ont bien disparu (cascade).
        org.junit.jupiter.api.Assertions.assertTrue(marchePrevisionRepository.findByIdDetail(ancienId).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(lotRepository.findByIdDetail(ancienId).isEmpty());
    }

    @Test
    @DisplayName("Édition PPM — ligne mise à jour : listes absentes → enfants conservés ; fournies → remplacement ; validations actives (Σ, processus)")
    void editionPpm_majPartielle_semantiqueEnfants() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        String creation = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-MP\","
                + "\"marches\":[{\"designationMarche\":\"Ligne\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaCode\":\"00-61-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":1000000}],"
                + "\"lots\":[{\"designationLot\":\"Lot unique\"}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-03-01\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(creation))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int idDetail = marcheRepository.findByIdDossier(idDoss).get(0).getIdDetail();

        // 1) MAJ sans aucune liste (undefined) → enfants CONSERVÉS.
        String enTete = "{\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-MP\",";
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enTete + "\"marches\":[{\"idDetail\":" + idDetail
                        + ",\"designationMarche\":\"Ligne MAJ\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\"}]}"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(1, marchePrevisionRepository.findByIdDetail(idDetail).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, lotRepository.findByIdDetail(idDetail).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, serviceBeneficiaireRepository.findAll().stream()
                .filter(b -> b.getIdDetail() != null && b.getIdDetail().intValue() == idDetail).count());

        // 2) Bénéficiaires fournis (2, Σ ok) + lots fournis VIDES → bénéficiaires REMPLACÉS (pas dupliqués),
        //    lots tous retirés, processus (absents) conservés.
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enTete + "\"marches\":[{\"idDetail\":" + idDetail
                        + ",\"designationMarche\":\"Ligne MAJ\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                        + "\"beneficiaires\":[{\"soaCode\":\"00-61-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":400000},"
                        + "{\"soaCode\":\"00-62-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":600000}],"
                        + "\"lots\":[]}]}"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(2, serviceBeneficiaireRepository.findAll().stream()
                .filter(b -> b.getIdDetail() != null && b.getIdDetail().intValue() == idDetail).count());
        org.junit.jupiter.api.Assertions.assertEquals(0, lotRepository.findByIdDetail(idDetail).size());
        org.junit.jupiter.api.Assertions.assertEquals(1, marchePrevisionRepository.findByIdDetail(idDetail).size());

        // 3) Validations actives (le PUT ne les saute plus) : Σ bénéficiaires ≠ montant → 400 ciblé.
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enTete + "\"marches\":[{\"idDetail\":" + idDetail
                        + ",\"designationMarche\":\"Ligne MAJ\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                        + "\"beneficiaires\":[{\"soaCode\":\"00-61-0-D10-00000\",\"numCompte\":\"2441\",\"ancMontBenef\":999}]}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[0].beneficiaires"));

        // 4) Remplacement des processus par une liste VIDE → 400 (invariant ≥1 processus par marché).
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enTete + "\"marches\":[{\"idDetail\":" + idDetail
                        + ",\"designationMarche\":\"Ligne MAJ\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                        + "\"processus\":[]}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[0].processus"));

        // 5) Ligne NOUVELLE sans processus → 400 (même règle qu'au POST).
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(enTete + "\"marches\":[{\"designationMarche\":\"Nouvelle sans processus\","
                        + "\"montEstim\":500000,\"idNature\":1,\"statut\":\"PREVU\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("marches[0].processus"));
    }

    @Test
    @DisplayName("File à réceptionner : dossiers SOUMIS de la localité sans réception (Secrétaire) ; cloisonnement et exclusions")
    void fileAReceptionner() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // SOUMIS ANT sans réception → à réceptionner.
        Dossier a = dossier(110, "SOUMIS"); a.setIdLocalite("ANT"); dossierRepository.save(a);
        // BROUILLON ANT → exclu.
        Dossier b = dossier(111, "BROUILLON"); b.setIdLocalite("ANT"); dossierRepository.save(b);
        // SOUMIS TMS → pas pour le Secrétaire d'ANT.
        Dossier c = dossier(112, "SOUMIS"); c.setIdLocalite("TMS"); dossierRepository.save(c);
        // SOUMIS ANT déjà réceptionné → exclu.
        Dossier d = dossier(113, "SOUMIS"); d.setIdLocalite("ANT"); dossierRepository.save(d);
        receptionRepository.save(reception(113, 113, "CTRSEC", false));

        // Secrétaire d'ANT : seul le 110.
        mvc.perform(get("/api/dossiers/a-receptionner").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==110)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==111)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idDossier==112)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idDossier==113)]", hasSize(0)));
        // Le Président voit toutes les localités (110 ANT + 112 TMS).
        mvc.perform(get("/api/dossiers/a-receptionner").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.idDossier==110)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==112)]", hasSize(1)));
        // Un Membre n'y a pas accès → 403.
        mvc.perform(get("/api/dossiers/a-receptionner").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Réception interdite si le dossier est en BROUILLON → 409")
    void receptionBrouillon_interdite() throws Exception {
        Dossier d = dossier(67, "BROUILLON");
        d.setIdLocalite("ANT");
        d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        mvc.perform(post("/api/receptions").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":67,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRPRE\",\"complet\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("KPIs par localité : le CC ne voit que sa localité ; le Président voit tout")
    void kpisParLocalite() throws Exception {
        Dossier a = dossier(140, "SOUMIS"); a.setIdLocalite("ANT"); dossierRepository.save(a);
        Dossier b = dossier(141, "PRET_DISPATCH"); b.setIdLocalite("ANT"); dossierRepository.save(b);
        Dossier c = dossier(142, "SOUMIS"); c.setIdLocalite("TMS"); dossierRepository.save(c);
        Dossier e = dossier(143, "BROUILLON"); e.setIdLocalite("ANT"); dossierRepository.save(e);

        // CC d'ANT : pipeline ANT (SOUMIS, PRET_DISPATCH, BROUILLON), mais nbDossiersSoumis exclut le BROUILLON.
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbDossiersSoumis").value(2))   // 140, 141 ; le BROUILLON 143 exclu
                .andExpect(jsonPath("$.pipelineParStatut.SOUMIS").value(1))
                .andExpect(jsonPath("$.pipelineParStatut.PRET_DISPATCH").value(1))
                .andExpect(jsonPath("$.pipelineParStatut.BROUILLON").value(1));
        // Président : global → SOUMIS = 2 (140 ANT + 142 TMS).
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineParStatut.SOUMIS").value(2));
    }

    @Test
    @DisplayName("Publication : workflow EN_ATTENTE → PUBLIE → RETIRE + compteur de consultations")
    void publication_workflow() throws Exception {
        // Création : statut/consultations envoyés ignorés → EN_ATTENTE / 0.
        mvc.perform(post("/api/publications").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPublication\":1,\"typeObjet\":\"PPM\",\"idObjet\":1,"
                        + "\"statutPubli\":\"PUBLIE\",\"nbConsultations\":99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPubli").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.nbConsultations").value(0));
        // Publication.
        mvc.perform(post("/api/publications/1/publier").header("Authorization", tokenPublication))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPubli").value("PUBLIE"));
        // Consultation (ouverte à tout authentifié) → compteur incrémenté.
        mvc.perform(post("/api/publications/1/consulter").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nbConsultations").value(1));
        // Retrait documenté.
        mvc.perform(post("/api/publications/1/retirer").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRetrait\":\"Erreur de publication\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPubli").value("RETIRE"));
        // Un Membre ne peut pas publier.
        mvc.perform(post("/api/publications/1/publier").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Document public : intégrité SHA-256 (empreinte + vérification)")
    void documentPublic_integriteSha256() throws Exception {
        mvc.perform(post("/api/publications").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPublication\":1,\"typeObjet\":\"PPM\",\"idObjet\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/document-publics").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDocPublic\":1,\"idPublication\":1,\"libelleDoc\":\"PV\"}"))
                .andExpect(status().isCreated());

        String contenu = Base64.getEncoder().encodeToString("contenu du document".getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/api/document-publics/1/empreinte").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON).content("{\"contenuBase64\":\"" + contenu + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hashSha256").isNotEmpty());

        // Même contenu → conforme.
        mvc.perform(post("/api/document-publics/1/verifier-integrite").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON).content("{\"contenuBase64\":\"" + contenu + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conforme").value(true));

        // Contenu altéré → non conforme.
        String altere = Base64.getEncoder().encodeToString("contenu altéré".getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/api/document-publics/1/verifier-integrite").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON).content("{\"contenuBase64\":\"" + altere + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conforme").value(false));
    }

    @Test
    @DisplayName("Référence réception : localité centrale (utilisateur transversal) -> 00001/PPM/CNM/2026")
    void reference_localite_centrale() throws Exception {
        Dossier d = dossier(300, "SOUMIS"); d.setIdTypeDossier("DDP"); d.setIdSousType("PPM"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(300, 300, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":300,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRPRE\",\"complet\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("00001/PPM/CNM/2026"));
        // Persistée sur le dossier (REFE_DOSSIER écrasée).
        mvc.perform(get("/api/dossiers/300").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.refeDossier").value("00001/PPM/CNM/2026"));
    }

    @Test
    @DisplayName("Référence réception (⚠️ règle corrigée 2026-08-04) : dossier régional TMS -> 00001/PPM/CRM-TMS/2026")
    void reference_localite_crm() throws Exception {
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        Dossier d = dossier(301, "SOUMIS"); d.setIdTypeDossier("DDP"); d.setIdSousType("PPM"); d.setIdLocalite("TMS");
        dossierRepository.save(d);
        ppmRepository.save(ppm(301, 301, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":301,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC2\",\"complet\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("00001/PPM/CRM-TMS/2026"));
    }

    @Test
    @DisplayName("Référence réception : compteur auto-incrémenté par la BDD (00001 puis 00002, même contexte)")
    void reference_incrementee_automatiquement() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d1 = dossier(302, "SOUMIS"); d1.setIdTypeDossier("DDP"); d1.setIdSousType("PPM"); d1.setIdLocalite("ANT"); dossierRepository.save(d1);
        Dossier d2 = dossier(303, "SOUMIS"); d2.setIdTypeDossier("DDP"); d2.setIdSousType("PPM"); d2.setIdLocalite("ANT"); dossierRepository.save(d2);
        ppmRepository.save(ppm(302, 302, "PRMP001"));
        ppmRepository.save(ppm(303, 303, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":302,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00001/PPM/CNM/2026"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":303,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00002/PPM/CNM/2026"));
    }

    @Test
    @DisplayName("Référence réception : compteur GLOBAL par année (CNM, CRM-TMS, CNM → 00001, 00002, 00003)")
    void reference_isolee_par_contexte() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        String tokenSecTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        Dossier ant = dossier(304, "SOUMIS"); ant.setIdTypeDossier("DDP"); ant.setIdSousType("PPM"); ant.setIdLocalite("ANT"); dossierRepository.save(ant);
        Dossier tms = dossier(305, "SOUMIS"); tms.setIdTypeDossier("DDP"); tms.setIdSousType("PPM"); tms.setIdLocalite("TMS"); dossierRepository.save(tms);
        Dossier cnm = dossier(306, "SOUMIS"); cnm.setIdTypeDossier("DDP"); cnm.setIdSousType("PPM"); cnm.setIdLocalite("ANT"); dossierRepository.save(cnm);
        ppmRepository.save(ppm(304, 304, "PRMP001"));
        ppmRepository.save(ppm(305, 305, "PRMP001"));
        ppmRepository.save(ppm(306, 306, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":304,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00001/PPM/CNM/2026"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenSecTms)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":305,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC2\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00002/PPM/CRM-TMS/2026"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":306,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRPRE\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00003/PPM/CNM/2026"));
    }

    @Test
    @DisplayName("Référence réception : segment = SOUS-TYPE verbatim (PPM-AGPM avec tiret) ; compteur continu indexé sur la famille DDP")
    void reference_segmentSousType_verbatim() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Deux dossiers de la MÊME famille DDP mais de sous-types différents (PPM et PPM-AGPM).
        Dossier ppm = dossier(307, "SOUMIS"); ppm.setIdTypeDossier("DDP"); ppm.setIdSousType("PPM"); ppm.setIdLocalite("ANT"); dossierRepository.save(ppm);
        Dossier agpm = dossier(308, "SOUMIS"); agpm.setIdTypeDossier("DDP"); agpm.setIdSousType("PPM-AGPM"); agpm.setIdLocalite("ANT"); dossierRepository.save(agpm);
        ppmRepository.save(ppm(307, 307, "PRMP001"));
        ppmRepository.save(ppm(308, 308, "PRMP001"));

        // 1re réception : segment = sous-type PPM, compteur famille DDP = 00001.
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":307,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00001/PPM/CNM/2026"));
        // 2e : segment PPM-AGPM VERBATIM (tiret conservé) ; le compteur DDP CONTINUE → 00002 (pas 00001).
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":308,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00002/PPM-AGPM/CNM/2026"));
    }

    @Test
    @DisplayName("Référence réception : dossier historique sans sous-type → repli sur la famille (DDP) dans le segment")
    void reference_sansSousType_repliFamille() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Dossier « historique » : famille présente, sous-type absent → segment = famille (repli défensif).
        Dossier d = dossier(309, "SOUMIS"); d.setIdTypeDossier("DDP"); d.setIdSousType(null); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(309, 309, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":309,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference", org.hamcrest.Matchers.matchesPattern("\\d{5}/DDP/CNM/\\d{4}")));
    }

    @Test
    @DisplayName("Référence réception : pas de doublon sur 2 réceptions (unicité garantie par l'UPSERT BDD)")
    void reference_concurrence() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d1 = dossier(307, "SOUMIS"); d1.setIdTypeDossier("DDP"); d1.setIdLocalite("ANT"); dossierRepository.save(d1);
        Dossier d2 = dossier(308, "SOUMIS"); d2.setIdTypeDossier("DDP"); d2.setIdLocalite("ANT"); dossierRepository.save(d2);
        ppmRepository.save(ppm(307, 307, "PRMP001"));
        ppmRepository.save(ppm(308, 308, "PRMP001"));

        // L'incrément est fait par la BDD (UPSERT atomique, verrou de ligne) : deux réceptions du même
        // contexte obtiennent des valeurs distinctes -> aucun doublon, même sous concurrence réelle.
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":307,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00001/DDP/CNM/2026"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":308,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00002/DDP/CNM/2026"));
    }

    @Test
    @DisplayName("Rectification PPM : PATCH sur dossier EN_ATTENTE_DECISION_PRMP -> 200, champ mis a jour, statut inchange")
    void rectifier_ppm_ok() throws Exception {
        Dossier d = dossier(400, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(400, 400, "PRMP001"));

        mvc.perform(patch("/api/ppms/400/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":400,\"exercice\":2026,\"signataire\":\"Sign\",\"dateSignature\":\"2026-01-10\","
                        + "\"reference\":\"PPM-REF-400\",\"libelle\":\"Libelle rectifie\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Libelle rectifie"));
        mvc.perform(get("/api/dossiers/400").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
    }

    @Test
    @DisplayName("Rectification PPM hors attente : dossier EN_VERIFICATION -> 409")
    void rectifier_ppm_horsAttente_409() throws Exception {
        Dossier d = dossier(402, "EN_VERIFICATION"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(420, 402, "PRMP001"));

        mvc.perform(patch("/api/ppms/420/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":402,\"exercice\":2026,\"signataire\":\"S\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rectification PPM par verificateur -> 403")
    void rectifier_ppm_verificateur_403() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        Dossier d = dossier(403, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(430, 403, "PRMP001"));

        mvc.perform(patch("/api/ppms/430/rectifier").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":403,\"exercice\":2026,\"signataire\":\"S\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Rectification marche : PATCH sur dossier EN_ATTENTE_DECISION_PRMP -> 200, objet mis a jour, statut inchange")
    void rectifier_marche_ok() throws Exception {
        Dossier d = dossier(401, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(410, 401, "PRMP001"));
        marcheRepository.save(marche(411, 401, 410));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));

        mvc.perform(patch("/api/marches/411/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":401,\"idPpm\":410,\"designationMarche\":\"Objet rectifie\","
                        + "\"montEstim\":5000000,\"idMode\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designationMarche").value("Objet rectifie"));
        mvc.perform(get("/api/dossiers/401").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
    }

    @Test
    @DisplayName("Rectification marche hors attente : dossier EN_VERIFICATION -> 409")
    void rectifier_marche_horsAttente_409() throws Exception {
        Dossier d = dossier(404, "EN_VERIFICATION"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(440, 404, "PRMP001"));
        marcheRepository.save(marche(441, 404, 440));

        mvc.perform(patch("/api/marches/441/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":404,\"idPpm\":440,\"designationMarche\":\"X\",\"montEstim\":1000}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rectification marche par verificateur -> 403")
    void rectifier_marche_verificateur_403() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        Dossier d = dossier(405, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(450, 405, "PRMP001"));
        marcheRepository.save(marche(451, 405, 450));

        mvc.perform(patch("/api/marches/451/rectifier").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":405,\"idPpm\":450,\"designationMarche\":\"X\",\"montEstim\":1000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Rectification PPM sans idDossier (identite figee) -> 200")
    void rectifier_ppm_sansIdentite_ok() throws Exception {
        Dossier d = dossier(406, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(460, 406, "PRMP001"));
        mvc.perform(patch("/api/ppms/460/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exercice\":2026,\"signataire\":\"Sign\",\"dateSignature\":\"2026-05-10\",\"reference\":\"R\",\"libelle\":\"L\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("L"));
    }

    @Test
    @DisplayName("Rectification marche sans idDossier/idPpm (identite figee) -> 200")
    void rectifier_marche_sansIdentite_ok() throws Exception {
        Dossier d = dossier(407, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(470, 407, "PRMP001"));
        marcheRepository.save(marche(471, 407, 470));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        mvc.perform(patch("/api/marches/471/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"designationMarche\":\"Objet\",\"montEstim\":1000,\"idMode\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designationMarche").value("Objet"));
    }

    @Test
    @DisplayName("Rectification marche : designation > 500 caracteres -> 400 nommant designationMarche (le contenu reste valide)")
    void rectifier_marche_designationTropLongue_400() throws Exception {
        // Le PATCH de rectification ne dispense QUE les champs d'identite (idDossier/idPpm), figes serveur.
        // Les contraintes de contenu doivent continuer de s'appliquer : sans cela le texte trop long part
        // jusqu'a la base et revient en 409 « Violation d'une contrainte de donnees », sans nommer le champ,
        // alors que le meme corps en PUT donne un 400 exploitable par le front.
        Dossier d = dossier(408, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(480, 408, "PRMP001"));
        marcheRepository.save(marche(481, 408, 480));

        mvc.perform(patch("/api/marches/481/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"designationMarche\":\"" + "X".repeat(501) + "\",\"montEstim\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='designationMarche')]", hasSize(1)));
    }

    @Test
    @DisplayName("Rectification PPM : reference > 100 caracteres -> 400 nommant reference (le contenu reste valide)")
    void rectifier_ppm_referenceTropLongue_400() throws Exception {
        // Meme raison que pour le marche : la dispense porte sur idDossier (identite figee), pas sur le contenu.
        Dossier d = dossier(409, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(490, 409, "PRMP001"));

        mvc.perform(patch("/api/ppms/490/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exercice\":2026,\"signataire\":\"Sign\",\"dateSignature\":\"2026-05-10\","
                        + "\"reference\":\"" + "R".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='reference')]", hasSize(1)));
    }

    @Test
    @DisplayName("Creation marche : idDossier/idPpm restent exiges (le groupe Identite n'est pas tombe avec la rectification)")
    void creation_marche_identiteToujoursExigee_400() throws Exception {
        // Garde-fou du decoupage en groupes : deplacer les @NotNull d'identite dans un groupe dedie ne doit
        // pas les desactiver sur POST/PUT, ou l'on creerait des lignes orphelines sans rattachement.
        mvc.perform(post("/api/marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":9999,\"designationMarche\":\"Objet\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='idDossier')]", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[?(@.champ=='idPpm')]", hasSize(1)));
    }

    @Test
    @DisplayName("Marche : montEstim negatif -> 400 nommant le champ (un montant negatif fausse l'invariant beneficiaires)")
    void marche_montEstimNegatif_400() throws Exception {
        // montEstim n'avait aucune contrainte numerique : -1 etait accepte, ecrit en base, puis compare a la
        // somme des montants par beneficiaire (SaisieService) et au montant precedent dans le diff de
        // rectification. La ligne restait qualifiee comme si de rien n'etait. Refuse a l'entree, avec le champ.
        seedBrouillonMarche(62);

        mvc.perform(post("/api/marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":62,\"idPpm\":62,\"designationMarche\":\"Objet\",\"montEstim\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='montEstim')]", hasSize(1)));
    }

    @Test
    @DisplayName("Marche : montEstim a plus de 2 decimales -> 400 (la colonne numeric(38,2) arrondissait en silence)")
    void marche_montEstimTropDeDecimales_400() throws Exception {
        // t_marche.MONT_ESTIM est numeric(38,2) : une 3e decimale etait tronquee par la base sans le dire,
        // le montant relu differait de celui envoye. Mieux vaut un refus explicite qu'une valeur alteree.
        seedBrouillonMarche(63);

        mvc.perform(post("/api/marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":63,\"idPpm\":63,\"designationMarche\":\"Objet\",\"montEstim\":1000.123}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='montEstim')]", hasSize(1)));
    }

    @Test
    @DisplayName("Marche : montEstim zero, absent ou a 2 decimales reste accepte (la contrainte ne tranche pas le metier)")
    void marche_montEstimLegitimes_201() throws Exception {
        // Non-regression, c'est le point de la correction : le zero (ligne saisie non chiffree) et l'absence de
        // montant (null — anomalie CHAMP_MANQUANT a l'import, pas un refus HTTP) restent valides. D'ou
        // @PositiveOrZero et non @Positive : la contrainte ferme le cas absurde sans arbitrer le metier.
        seedBrouillonMarche(64);

        mvc.perform(post("/api/marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":64,\"idPpm\":64,\"designationMarche\":\"Zero\",\"montEstim\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":64,\"idPpm\":64,\"designationMarche\":\"Sans montant\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":64,\"idPpm\":64,\"designationMarche\":\"Decimales\",\"montEstim\":1000.25}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.montEstim").value(1000.25));
    }

    @Test
    @DisplayName("Rectification marche : montEstim negatif -> 400 (la contrainte de contenu vaut aussi sur le PATCH)")
    void rectifier_marche_montEstimNegatif_400() throws Exception {
        // Le chemin de rectification est celui ou l'absence de validation coutait le plus cher : le dossier est
        // deja passe en examen, un montant negatif y reecrirait la ligne examinee sans aucun controle.
        Dossier d = dossier(410, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(495, 410, "PRMP001"));
        marcheRepository.save(marche(496, 410, 495));

        mvc.perform(patch("/api/marches/496/rectifier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"designationMarche\":\"Objet\",\"montEstim\":-500000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='montEstim')]", hasSize(1)));
    }

    /** Brouillon PPM de PRMP001 (ANT) prêt à recevoir des lignes de marché — dossier et PPM portent le même id. */
    private void seedBrouillonMarche(int id) {
        Dossier d = dossier(id, "BROUILLON");
        d.setIdTypeDossier("DDP"); d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(id, id, "PRMP001"));
    }

    @Test
    @DisplayName("Erreur de validation : corps expose erreurs[].champ/message")
    void validation_erreurs_format() throws Exception {
        mvc.perform(post("/api/marches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs").isArray())
                .andExpect(jsonPath("$.erreurs[0].champ").exists())
                .andExpect(jsonPath("$.erreurs[0].message").exists());
    }

    @Test
    @DisplayName("Mauvais verbe sur une route existante -> 405 avec l'en-tete Allow, jamais 500")
    void mauvaisVerbe_405AvecAllow() throws Exception {
        // Le @ExceptionHandler(Exception.class) du GlobalExceptionHandler interceptait
        // HttpRequestMethodNotSupportedException avant le resolveur par defaut de Spring : un mauvais verbe
        // sur N'IMPORTE QUELLE route de l'API repondait 500, message d'exception dans le corps. Un client ne
        // pouvait pas distinguer une route mal appelee d'une panne serveur, et un moniteur de disponibilite
        // comptait une erreur 5xx a chaque sonde maladroite. Le defaut touchait toute l'API, pas une route.
        mvc.perform(delete("/api/marches").header("Authorization", tokenPrmp))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(header().string("Allow", containsString("POST")));
    }

    @Test
    @DisplayName("Mauvais verbe : le corps reste un ErrorResponse standard (statut 405, chemin appele)")
    void mauvaisVerbe_corpsStandard() throws Exception {
        // Le 405 doit rester exploitable comme les autres erreurs : meme enveloppe, meme champ path.
        mvc.perform(put("/api/dossiers").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.path").value("/api/dossiers"));
    }

    /**
     * Même famille de défaut que le 405 ci-dessus, et pour la même raison : une exception MVC de Spring
     * qu'aucun {@code @ExceptionHandler} ne déclarait tombait dans le filet {@code Exception.class} du
     * GlobalExceptionHandler et sortait en <strong>500 générique</strong>. Ici la faute est entièrement du
     * côté de l'appelant — un paramètre du mauvais type — et le message générique du 500 ne lui dit ni
     * quel paramètre, ni ce qui était attendu : il ne peut pas corriger, et croit le serveur en panne.
     *
     * <p>Le test couvre les trois liaisons réellement exposées par l'API (entier, booléen, date) sur trois
     * contrôleurs distincts, parce que le défaut n'était pas propre à une route : il portait sur tout
     * paramètre typé du projet. Il vérifie aussi que le corps garde la forme {@code erreurs[]} des autres
     * 400 — sans quoi le front devrait écrire un second chemin de traitement pour la même classe d'erreur.</p>
     */
    @Test
    @DisplayName("Parametre de requete du mauvais type -> 400 nommant le parametre, jamais 500")
    void parametreMauvaisType_400NommantLeParametre() throws Exception {
        // Entier : ?ppm= sur /api/marches.
        mvc.perform(get("/api/marches?ppm=abc").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erreurs", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[0].champ").value("ppm"))
                .andExpect(jsonPath("$.erreurs[0].message", containsString("numérique")));

        // Booléen : ?lu= sur /api/notifications/mes. « oui » est le piège naturel d'un client francophone.
        mvc.perform(get("/api/notifications/mes?lu=oui").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("lu"))
                .andExpect(jsonPath("$.erreurs[0].message", containsString("true")));

        // Variable de chemin : /api/marches/{id} attend un entier — même exception, même traitement.
        mvc.perform(get("/api/marches/abc").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("id"));

        // Le message général reste exploitable et le chemin appelé est bien reporté (enveloppe ErrorResponse).
        mvc.perform(get("/api/capm?mode=tous").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/capm"))
                .andExpect(jsonPath("$.message", containsString("mode")));
    }

    @Test
    @DisplayName("PV projets vs definitifs : un PV signe quitte /pv-examens et apparait dans /pv-examens/definitifs")
    void pv_projets_et_definitifs() throws Exception {
        // PV non signé (BROUILLON) sur examen 1. Les deux ids sont RELUS (PK de seq_pv_examen).
        int idBrouillon = creerPvEtLireId(tokenMembre, "{\"idExamen\":1,\"idAvis\":\"FAV\","
                + "\"imCtrlMembre\":\"CTRMEM\",\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}");
        // PV signé (FAV) sur examen 1.
        int idSigne = signerPvAvecAvis("FAV");

        // Projets : contient le BROUILLON, exclut le SIGNE.
        mvc.perform(get("/api/pv-examens").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==" + idBrouillon + ")]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPv==" + idSigne + ")]", hasSize(0)));
        // Définitifs : contient le SIGNE, exclut le BROUILLON.
        mvc.perform(get("/api/pv-examens/definitifs").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==" + idSigne + ")]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPv==" + idBrouillon + ")]", hasSize(0)));
    }

    @Test
    @DisplayName("PV refePv : derivee de refeDossier (.../YYYY -> .../PV/YYYY)")
    void pv_refePv_generee() throws Exception {
        Dossier d = dossier(500, "EXAMINE"); d.setRefeDossier("00003/DDP/CRM-ANT/2026"); dossierRepository.save(d);
        receptionRepository.save(reception(500, 500, "CTRSEC", true));
        dispatchRepository.save(dispatch(500, 500, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(500, 500, "CTRMEM"));

        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":201,\"idExamen\":500,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refePv").value("00003/DDP/CRM-ANT/PV/2026"));
    }

    @Test
    @DisplayName("PV refePv : hérite du segment SOUS-TYPE du refeDossier, tiret compris (PPM-AGPM)")
    void pv_refePv_heriteSegmentSousType() throws Exception {
        // refeDossier au nouveau format sous-type (PPM-AGPM) → refePv insère /PV/ en gardant le segment.
        Dossier d = dossier(502, "EXAMINE"); d.setRefeDossier("00013/PPM-AGPM/CRM-ANT/2026"); dossierRepository.save(d);
        receptionRepository.save(reception(502, 502, "CTRSEC", true));
        dispatchRepository.save(dispatch(502, 502, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(502, 502, "CTRMEM"));

        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":204,\"idExamen\":502,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refePv").value("00013/PPM-AGPM/CRM-ANT/PV/2026"));
    }

    @Test
    @DisplayName("PV refePv unique : deux PV sur le meme dossier -> 409")
    void pv_refePv_unique() throws Exception {
        Dossier d = dossier(501, "EXAMINE"); d.setRefeDossier("00007/DDP/CRM-ANT/2026"); dossierRepository.save(d);
        receptionRepository.save(reception(501, 501, "CTRSEC", true));
        dispatchRepository.save(dispatch(501, 501, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(501, 501, "CTRMEM"));

        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":202,\"idExamen\":501,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":203,\"idExamen\":501,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Suppression cohérente : supprimer le dernier PPM d'un brouillon supprime aussi le dossier")
    void suppression_coherente() throws Exception {
        Dossier d = dossier(190, "BROUILLON"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(290, 190, "PRMP001"));
        marcheRepository.save(marche(390, 190, 290));

        mvc.perform(delete("/api/ppms/290").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(dossierRepository.existsById(190));
        // Absent de « Mes brouillons » (GET /api/dossiers?statut=BROUILLON) ET de GET /api/dossiers.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "BROUILLON"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.idDossier==190)]", hasSize(0)));
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==190)]", hasSize(0)));
    }

    @Test
    @DisplayName("Suppression PPM d'un brouillon AVEC historique (réception) → PPM supprimé (204), dossier conservé (traces FK)")
    void suppression_brouillonAvecHistorique_conserveDossier() throws Exception {
        Dossier d = dossier(191, "BROUILLON"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(291, 191, "PRMP001"));
        receptionRepository.save(reception(591, 191, "CTRSEC", true)); // trace de circuit → pas de hard delete

        mvc.perform(delete("/api/ppms/291").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(ppmRepository.existsById(291));
        org.junit.jupiter.api.Assertions.assertTrue(dossierRepository.existsById(191)); // conservé (porte une réception)
    }

    @Test
    @DisplayName("Suppression dossier — BROUILLON propriétaire → 204, cascade PPM/marché, absent de Mes brouillons")
    void suppression_brouillon_ok() throws Exception {
        Dossier d = dossier(600, "BROUILLON"); d.setIdTypeDossier("DDP"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(600, 600, "PRMP001"));
        marcheRepository.save(marche(600, 600, 600));

        mvc.perform(delete("/api/dossiers/600").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(dossierRepository.existsById(600));
        org.junit.jupiter.api.Assertions.assertFalse(ppmRepository.existsById(600));
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(600));
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "BROUILLON"))
                .andExpect(jsonPath("$[?(@.idDossier==600)]", hasSize(0)));
    }

    @Test
    @DisplayName("Suppression dossier — BROUILLON AVEC historique (réception+retrait+notif) → 204, cascade historique")
    void suppression_brouillon_avec_historique_ok() throws Exception {
        Dossier d = dossier(603, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        receptionRepository.save(reception(603, 603, "CTRSEC", true));
        demandeRetraitRepository.save(demandeRetrait(0, 603, "PRMP001"));
        notificationService.emettre(603, TypeNotification.PRET_DISPATCH, "CTRMEM", null, "Titre", "Corps");

        mvc.perform(delete("/api/dossiers/603").header("Authorization", tokenPrmp)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(dossierRepository.existsById(603));
        org.junit.jupiter.api.Assertions.assertFalse(receptionRepository.existsByIdDossier(603));
        org.junit.jupiter.api.Assertions.assertFalse(demandeRetraitRepository.existsByIdDossier(603));
    }

    @Test
    @DisplayName("Suppression dossier — statut SOUMIS → 409")
    void suppression_hors_brouillon_409() throws Exception {
        Dossier d = dossier(601, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);
        mvc.perform(delete("/api/dossiers/601").header("Authorization", tokenPrmp)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Suppression dossier — autre PRMP → 403")
    void suppression_autre_prmp_403() throws Exception {
        prmpRepository.save(prmp("PRMP002", "ANT"));
        Dossier d = dossier(602, "BROUILLON"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP002"); dossierRepository.save(d);
        mvc.perform(delete("/api/dossiers/602").header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Suppression dossier — id inexistant → 404")
    void suppression_inexistant_404() throws Exception {
        mvc.perform(delete("/api/dossiers/99999").header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Saisie PPM — reference auto 00001/DGB/PPM/2026 (acronyme du libelle entite)")
    void ppm_reference_generee() throws Exception {
        EntiteContract e = entite(700, 1, "ANT"); e.setLibelleEntite("Direction Générale du Budget");
        entiteContractRepository.save(e);
        prmpEntiteRepository.save(prmpEntite(700, "PRMP001", 700, true));
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":700,\"exercice\":2026,\"dateSignature\":\"2026-01-10\",\"marches\":[]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        // Le brouillon n'apparaît plus dans la liste « Mes PPM & marchés » : on le lit par son id (propriétaire).
        int idPpm = ppmRepository.findByIdDossier(idDoss).get(0).getIdPpm();
        mvc.perform(get("/api/ppms/" + idPpm).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.reference").value("00001/DGB/PPM/2026"));
    }

    @Test
    @DisplayName("Saisie PPM — reference incrementee 00002 sur 2e PPM meme entite/annee")
    void ppm_reference_incrementee() throws Exception {
        EntiteContract e = entite(701, 1, "ANT"); e.setLibelleEntite("Direction Générale du Budget");
        entiteContractRepository.save(e);
        prmpEntiteRepository.save(prmpEntite(701, "PRMP001", 701, true));
        String body = "{\"idEntiteContract\":701,\"exercice\":2026,\"dateSignature\":\"2026-01-10\",\"marches\":[]}";
        int d1 = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString(), "$.idDossier");
        int d2 = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString(), "$.idDossier");
        int p1 = ppmRepository.findByIdDossier(d1).get(0).getIdPpm();
        int p2 = ppmRepository.findByIdDossier(d2).get(0).getIdPpm();
        mvc.perform(get("/api/ppms/" + p1).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.reference").value("00001/DGB/PPM/2026"));
        mvc.perform(get("/api/ppms/" + p2).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.reference").value("00002/DGB/PPM/2026"));
    }

    @Test
    @DisplayName("Saisie PPM — compteur isole par entite : DRT -> 00001/DRT/PPM/2026")
    void ppm_reference_isolee() throws Exception {
        EntiteContract e = entite(702, 1, "ANT"); e.setLibelleEntite("Direction Régionale des Travaux");
        entiteContractRepository.save(e);
        prmpEntiteRepository.save(prmpEntite(702, "PRMP001", 702, true));
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":702,\"exercice\":2026,\"dateSignature\":\"2026-01-10\",\"marches\":[]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int idPpm = ppmRepository.findByIdDossier(idDoss).get(0).getIdPpm();
        mvc.perform(get("/api/ppms/" + idPpm).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.reference").value("00001/DRT/PPM/2026"));
    }

    @Test
    @DisplayName("Saisie PPM — signataire auto depuis le profil PRMP (prenoms + nom)")
    void ppm_signataire_depuis_prmp() throws Exception {
        EntiteContract e = entite(703, 1, "ANT"); e.setLibelleEntite("Direction Générale du Budget");
        entiteContractRepository.save(e);
        prmpEntiteRepository.save(prmpEntite(703, "PRMP001", 703, true));
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":703,\"exercice\":2026,\"dateSignature\":\"2026-01-10\",\"marches\":[]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int idPpm = ppmRepository.findByIdDossier(idDoss).get(0).getIdPpm();
        mvc.perform(get("/api/ppms/" + idPpm).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.signataire").value("Prenoms Nom"));
    }

    @Test
    @DisplayName("CAPM — CRUD Administrateur : POST/PUT/DELETE → 201/200/204")
    void capm_crud_admin() throws Exception {
        mvc.perform(post("/api/capm").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCapm\":10,\"libelleProcessus\":\"NEGOCIATION\",\"ordre\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idCapm").value(10))
                .andExpect(jsonPath("$.ordre").value(5));
        mvc.perform(put("/api/capm/10").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCapm\":10,\"libelleProcessus\":\"NEGOCIATION MAJ\",\"ordre\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelleProcessus").value("NEGOCIATION MAJ"))
                .andExpect(jsonPath("$.ordre").value(6));
        mvc.perform(delete("/api/capm/10").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("CAPM — écriture interdite hors Administrateur → 403")
    void capm_crud_non_admin() throws Exception {
        mvc.perform(post("/api/capm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCapm\":11,\"libelleProcessus\":\"X\",\"ordre\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Saisie PPM — marché sans processus → 400 (marches[0].processus)")
    void marche_sans_processus_400() throws Exception {
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\"}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].processus')].message",
                        hasItem("Au moins un processus est obligatoire.")));
    }

    @Test
    @DisplayName("Saisie PPM — processus avec idCapm inexistant → 400 (marches[0].processus[0].idCapm)")
    void processus_idCapm_invalide_400() throws Exception {
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":999,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].processus[0].idCapm')]").exists());
    }

    @Test
    @DisplayName("Saisie PPM — processus sans dateDebut → 400 (marches[0].processus[0].dateDebut)")
    void processus_sans_dateDebut_400() throws Exception {
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].processus[0].dateDebut')].message",
                        hasItem("La date de début est obligatoire.")));
    }

    @Test
    @DisplayName("Saisie PPM — marché + processus complets → 201 + prévisions triées par ordre CAPM")
    void brouillon_avec_processus_ok() throws Exception {
        // Mode déterminable (évite la notif MODE_NON_DETERMINE, hors sujet) : 500M → AOR (mode 2).
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        capmRepository.save(new Capm(3, "OUVERTURE", 3, null, null));
        // Processus envoyés dans le désordre (3 puis 1) → la lecture doit les trier par ordre (1 avant 3).
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":3,\"dateDebut\":\"2026-03-01\",\"dateFin\":\"2026-03-31\"},"
                + "{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-02-28\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        String m = mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString();
        int idDetail = ((List<Integer>) com.jayway.jsonpath.JsonPath.read(m,
                "$[?(@.idDossier==" + idDoss + ")].idDetail")).get(0);
        // 2 prévisions triées par t_capm.ORDRE ASC → idCapm 1 (ordre 1) avant idCapm 3 (ordre 3).
        mvc.perform(get("/api/marche-previsions?marche=" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idCapm").value(1))
                .andExpect(jsonPath("$[0].ordre").value(1))
                .andExpect(jsonPath("$[0].dateDebut").value("2026-02-01"))
                .andExpect(jsonPath("$[1].idCapm").value(3))
                .andExpect(jsonPath("$[1].ordre").value(3));
    }

    @Test
    @DisplayName("Saisie PPM — processus dateDebut >= dateFin → 400 (cohérence interne)")
    void processus_datefin_avant_datedebut_400() throws Exception {
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-06-30\",\"dateFin\":\"2026-06-01\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].processus[0].dateFin')].message",
                        hasItem("La date de fin doit être postérieure à la date de début.")));
    }

    @Test
    @DisplayName("Saisie PPM — chevauchement entre processus consécutifs → 400 (séquence)")
    void processus_sequence_chevauchement_400() throws Exception {
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        capmRepository.save(new Capm(2, "DAO", 2, null, null));
        // processus[1] (DAO) commence 02-15, avant la fin de processus[0] (LANCEMENT) le 03-01 → chevauchement.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-03-01\"},"
                + "{\"idCapm\":2,\"dateDebut\":\"2026-02-15\",\"dateFin\":\"2026-04-01\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].processus[1].dateDebut')]").exists());
    }

    @Test
    @DisplayName("Saisie PPM — dates cohérentes et ordonnées → 201")
    void processus_sequence_ok() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        capmRepository.save(new Capm(2, "DAO", 2, null, null));
        // dateDebut[2] = dateFin[1] (03-01) → contiguïté autorisée (>=).
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-03-01\"},"
                + "{\"idCapm\":2,\"dateDebut\":\"2026-03-01\",\"dateFin\":\"2026-04-01\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Saisie PPM — corps mal formé (date JJ/MM/AAAA, id libellé) → 400 avec le champ fautif")
    void saisie_corps_illisible_400() throws Exception {
        // dateSignature non-ISO → 400 + champ dateSignature
        String dateKo = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"23/06/2026\","
                + "\"marches\":[{\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(dateKo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='dateSignature')]").exists());

        // idEntiteContract = libellé (string) → 400 + champ idEntiteContract
        String idKo = "{\"idEntiteContract\":\"Direction Générale du Budget\",\"exercice\":2026,\"dateSignature\":\"2026-06-23\","
                + "\"marches\":[{\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(idKo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='idEntiteContract')]").exists());
    }

    @Test
    @DisplayName("Observation-controle — création d'une ligne (Membre) → 201")
    void observation_creation_ok() throws Exception {
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(1); pc.setLibelPointCtrl("Montant"); pc.setObligatoire(true); pc.setIdTypeDossier("DDP");
        pointsCtrlRepository.save(pc);
        ExamenDetail d = new ExamenDetail();
        d.setIdDetailExamen(520); d.setIdExamen(1); d.setIdPtControle(1); d.setConforme(false);
        examenDetailRepository.save(d);
        mvc.perform(post("/api/observation-controles").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":520,\"auLieuDe\":\"500000\",\"lire\":\"5000000\",\"ordre\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idObservation").exists())
                .andExpect(jsonPath("$.idDetail").value(520));
    }

    @Test
    @DisplayName("Examen-détail — non conforme sans lignes d'observation → 400")
    void observation_non_conforme_sans_lignes_400() throws Exception {
        // examen 1 = EXAMINE (seed, modifiable) ; conforme=false + observations vide → 400 (avant save).
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":510,\"idExamen\":1,\"idPtControle\":1,\"conforme\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='observations')].message",
                        hasItem("Au moins une ligne d'observation est obligatoire si le point est non conforme.")));
    }

    @Test
    @DisplayName("Examen-détail — conforme sans lignes d'observation → 200")
    void observation_conforme_sans_lignes_ok() throws Exception {
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(1); pc.setLibelPointCtrl("Montant"); pc.setObligatoire(true); pc.setIdTypeDossier("DDP");
        pointsCtrlRepository.save(pc);
        ExamenDetail d = new ExamenDetail();
        d.setIdDetailExamen(511); d.setIdExamen(1); d.setIdPtControle(1); d.setConforme(true);
        examenDetailRepository.save(d);
        mvc.perform(put("/api/examen-details/511").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":511,\"idExamen\":1,\"idPtControle\":1,\"conforme\":true,\"observations\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conforme").value(true));
    }

    @Test
    @DisplayName("Soumission examen → Projet de PV créé (toujours un PV)")
    void examen_soumettre_pv_ok() throws Exception {
        mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idExamen").value(1))
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"));
    }

    @Test
    @DisplayName("Soumission examen sans secrétaire de séance → 201 (⚠️ 2026-08-01 : optionnel, posé à la clôture de navette)")
    void soumission_examen_sans_secretaire_400() throws Exception {
        mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idAvis\":\"FAV\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"));
    }

    @Test
    @DisplayName("Soumission examen — secrétaire non vérificateur (autre profil/localité) → 400")
    void soumission_examen_secretaire_invalide_400() throws Exception {
        // CTRMEM est un MEMBRE (pas un vérificateur) → secrétaire de séance invalide.
        mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRMEM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idSecretaireSeance"));
    }

    @Test
    @DisplayName("Soumission examen — secrétaire vérificateur valide → 201, PV avec secrétaire de séance")
    void soumission_examen_secretaire_ok() throws Exception {
        mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"))
                .andExpect(jsonPath("$.idSecretaireSeance").value("CTRVER"))
                .andExpect(jsonPath("$.nomSecretaireSeance").value("Prenoms NomCTRVER"));
    }

    // --- Document du Projet de PV (génération directe via le générateur, modèle Word central) ---

    private PvDocumentContexte ctxPv(String nomPresident, String nomChefCommission,
            java.util.List<PvDocumentContexte.Observation> observations) {
        return ctxPv(java.time.LocalDate.of(2026, 6, 23), nomPresident, nomChefCommission, observations);
    }

    private PvDocumentContexte ctxPv(java.time.LocalDate dateExamen, String nomPresident, String nomChefCommission,
            java.util.List<PvDocumentContexte.Observation> observations) {
        return new PvDocumentContexte(
                dateExamen,                                 // date d'examen
                "00007/DDP/CRM-ANT/PV/2026",               // refPv
                java.time.LocalDate.of(2026, 6, 15),       // date de réception
                "Ministère de l'Économie et des Finances", // entité contractante
                2026,                                       // exercice
                "ANTANANARIVO",                             // localité (libellé)
                "ANTANANARIVO",                             // chef-lieu (⚠️ 2026-08-04, lieu « A …, le »)
                nomPresident, nomChefCommission,
                "Paul MEMBRE", "Vero VERIFICATEUR",
                null,                                       // numMaj (⚠️ 2026-08-05) : null = plan INITIAL
                observations);
    }

    private java.util.List<PvDocumentContexte.Observation> troisObservations() {
        return java.util.List.of(
                new PvDocumentContexte.Observation("Conformité au budget", "AU_LIEU_DE_A", "LIRE_ALPHA"),
                new PvDocumentContexte.Observation("Conformité au budget", "AU_LIEU_DE_B", "LIRE_BRAVO"),
                new PvDocumentContexte.Observation("Délais de passation", "AU_LIEU_DE_C", "LIRE_CHARLIE"));
    }

    @Test
    @DisplayName("Document PV — le PDF contient l'image de l'emblème")
    void document_pv_genere_embleme_present() throws Exception {
        byte[] pdf = pvDocumentGenerator.genererPdf(ctxPv("Jean PRESIDENT", null, troisObservations()));
        assertTrue(contientImage(pdf), "le PDF du PV contient au moins un objet image (emblème)");
    }

    @Test
    @DisplayName("Document PV — date d'examen en toutes lettres dans « L'an … » (année + et le + jour mois)")
    void document_pv_date_examen_toutes_lettres() throws Exception {
        byte[] pdf = pvDocumentGenerator.genererPdf(ctxPv("Jean PRESIDENT", null, troisObservations()));
        assertTrue(texteDuPdf(pdf).contains("deux mille vingt-six et le vingt-trois juin"),
                "la date d'examen apparaît au format « année et le jour mois » en toutes lettres");
    }

    @Test
    @DisplayName("Date « L'an » — format année + et le + jour mois (23/06/2019)")
    void date_examen_an_format_ok() {
        org.junit.jupiter.api.Assertions.assertEquals("deux mille dix-neuf et le vingt-trois juin",
                cnm.prs.service.NombreEnLettres.dateExamenPourLAn(java.time.LocalDate.of(2019, 6, 23)));
    }

    @Test
    @DisplayName("Date « L'an » — 30/06/2026 → « deux mille vingt-six et le trente juin »")
    void date_examen_an_2026_ok() {
        org.junit.jupiter.api.Assertions.assertEquals("deux mille vingt-six et le trente juin",
                cnm.prs.service.NombreEnLettres.dateExamenPourLAn(java.time.LocalDate.of(2026, 6, 30)));
    }

    @Test
    @DisplayName("Document PV — « Séance du » reste en chiffres « 30 juin 2026 »")
    void document_pv_seance_format_chiffres() throws Exception {
        byte[] pdf = pvDocumentGenerator.genererPdf(
                ctxPv(java.time.LocalDate.of(2026, 6, 30), "Jean PRESIDENT", null, troisObservations()));
        assertTrue(texteDuPdf(pdf).contains("Séance du 30 juin 2026"),
                "« Séance du » reste au format chiffres");
    }

    @Test
    @DisplayName("Document PV — « L'an … » au format toutes lettres (année + et le + jour mois)")
    void document_pv_lan_format_lettres() throws Exception {
        byte[] pdf = pvDocumentGenerator.genererPdf(
                ctxPv(java.time.LocalDate.of(2026, 6, 30), "Jean PRESIDENT", null, troisObservations()));
        // L'apostrophe de « L'an » est courbe dans le modèle → on valide la date + le texte fixe qui suit.
        assertTrue(texteDuPdf(pdf).contains(
                "deux mille vingt-six et le trente juin, la Commission Centrale des Marchés"),
                "le paragraphe « L'an … » porte la date au format toutes lettres");
    }

    @Test
    @DisplayName("Document PV — bloc présents filtré : PV sans Président → ligne Président absente")
    void document_pv_presents_filtre_signataires() throws Exception {
        // Signé par le Membre + le Chef de commission, pas par le Président.
        byte[] pdf = pvDocumentGenerator.genererPdf(ctxPv(null, "Chef COMMISSION", troisObservations()));
        assertFalse(texteDuPdf(pdf).contains("Président de la Commission Nationale des Marchés"),
                "la ligne Président est retirée quand le Président n'a pas signé");
    }

    @Test
    @DisplayName("Document PV — ANNEXE : une ligne par observation (3 observations → 3 lignes)")
    void document_pv_annexe_observations_multiples() throws Exception {
        String texte = texteDuPdf(pvDocumentGenerator.genererPdf(
                ctxPv("Jean PRESIDENT", null, troisObservations())));
        assertTrue(texte.contains("LIRE_ALPHA") && texte.contains("LIRE_BRAVO") && texte.contains("LIRE_CHARLIE"),
                "les 3 observations apparaissent dans l'ANNEXE");
    }

    @Test
    @DisplayName("Document PV — aucun placeholder résiduel <...>")
    void document_pv_aucun_placeholder() throws Exception {
        String texte = texteDuPdf(pvDocumentGenerator.genererPdf(
                ctxPv("Jean PRESIDENT", "Chef COMMISSION", troisObservations())));
        assertFalse(java.util.regex.Pattern.compile("<[A-Z]").matcher(texte).find(),
                "aucun placeholder <...> ne subsiste dans le PDF du PV");
    }

    @Test
    @DisplayName("Document PV — titre « COMMISSION CENTRALE » sans « /REGIONALE »")
    void document_pv_titre_sans_regionale() throws Exception {
        String texte = texteDuPdf(pvDocumentGenerator.genererPdf(
                ctxPv("Jean PRESIDENT", null, troisObservations())));
        assertTrue(texte.contains("PROCES-VERBAL DE LA COMMISSION CENTRALE"),
                "le titre porte « COMMISSION CENTRALE »");
        assertFalse(texte.contains("REGIONALE"), "« /REGIONALE » est retiré du titre");
    }

    @Test
    @DisplayName("Document PV — phrase d'avis « Commission Centrale » sans « /Régionale »")
    void document_pv_avis_sans_regionale() throws Exception {
        String texte = texteDuPdf(pvDocumentGenerator.genererPdf(
                ctxPv("Jean PRESIDENT", null, troisObservations())));
        assertTrue(texte.contains("La Commission Centrale des Marchés émet un AVIS FAVORABLE"),
                "la phrase d'avis porte « Commission Centrale »");
        assertFalse(texte.contains("Régionale"), "« /Régionale » est retiré de la phrase d'avis");
    }

    @Test
    @DisplayName("Téléchargement PV — GET /document renvoie le PDF stocké (FSX)")
    void pv_document_telechargement_ok() throws Exception {
        byte[] contenu = "%PDF-1.5 contenu du PV".getBytes(StandardCharsets.US_ASCII);
        java.nio.file.Path fichier = java.nio.file.Files.createTempFile("pv-doc-", ".pdf");
        java.nio.file.Files.write(fichier, contenu);
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(80);
        pv.setIdExamen(1);
        pv.setIdAvis("FAVR");
        pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("BROUILLON");
        pv.setNbNavettes(0);
        pv.setCheminDocument(fichier.toString());
        pvExamenRepository.save(pv);

        var resp = mvc.perform(get("/api/pv-examens/80/document").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(MediaType.APPLICATION_PDF_VALUE, resp.getContentType());
        org.junit.jupiter.api.Assertions.assertArrayEquals(contenu, resp.getContentAsByteArray());
    }

    @Test
    @DisplayName("Téléchargement PV — PV non éligible sans document → 404")
    void pv_document_absent_404() throws Exception {
        seedPvSigne(81, 1);   // PV avis FAV (non éligible) sans CHEMIN_DOCUMENT → pas de régénération
        mvc.perform(get("/api/pv-examens/81/document").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PvExamenDto.documentDisponible : projet éligible → true (cotation incluse) ; PV SIGNE → true seulement quand CHEMIN_DOCUMENT est posé (fenêtre de génération post-commit → false)")
    void pv_documentDisponible_refleteEligibilite() throws Exception {
        modePassationRepository.save(new ModePassation(5, "Demande de cotation", null, null, null, null));

        // ÉLIGIBLE MALGRÉ un marché en « Demande de cotation » : le mode ne conditionne plus l'éligibilité AFSR.
        cnm.prs.entity.Marche mCot = marche(9600, 1, 1); mCot.setIdMode(5); marcheRepository.save(mCot);
        cnm.prs.entity.PvExamen pvFavr = new cnm.prs.entity.PvExamen();
        pvFavr.setIdPv(600); pvFavr.setIdExamen(1); pvFavr.setIdAvis("FAVR"); pvFavr.setImCtrlMembre("CTRMEM");
        pvFavr.setStatutPv("PROJET_ACCEPTE"); pvFavr.setNbNavettes(0);
        pvExamenRepository.save(pvFavr);

        // NON ÉLIGIBLE pour un vrai motif : avis NSP (⚠️ 2026-08-03 : seul avis SANS modèle Word —
        // FAV → AF et DEF → ANF ont désormais leur gabarit).
        dossierRepository.save(dossierLoc(502, "EXAMINE", "ANT", "PRMP001"));
        receptionRepository.save(reception(502, 502, "CTRCC1", true));   // CTRCC1 = localité ANT
        dispatchRepository.save(dispatch(502, 502, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(502, 502, "CTRMEM"));
        ppmRepository.save(ppm(502, 502, "PRMP001"));
        marcheRepository.save(marche(9601, 502, 502));
        cnm.prs.entity.PvExamen pvDef = new cnm.prs.entity.PvExamen();
        pvDef.setIdPv(601); pvDef.setIdExamen(502); pvDef.setIdAvis("NSP"); pvDef.setImCtrlMembre("CTRMEM");
        pvDef.setStatutPv("SIGNE"); pvDef.setNbNavettes(0);
        pvExamenRepository.save(pvDef);

        // PROJET FAVR + ANT + PPM + cotation → true : « un document sera produit à la signature »
        // (cas signalé 00008/DDP/CRM-ANT/PV/2026 — le mode ne bloque plus).
        mvc.perform(get("/api/pv-examens/600").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDisponible").value(true));
        // ⚠️ 2026-08-19 (génération post-commit) — PV SIGNE : le flag dit « fichier prêt MAINTENANT ».
        // Signé sans CHEMIN_DOCUMENT (fenêtre de génération) → false ; chemin posé → true.
        pvFavr.setStatutPv("SIGNE");
        pvExamenRepository.save(pvFavr);
        mvc.perform(get("/api/pv-examens/600").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDisponible").value(false));
        pvFavr.setCheminDocument("PV/pv-600.pdf");
        pvExamenRepository.save(pvFavr);
        mvc.perform(get("/api/pv-examens/600").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDisponible").value(true));
        // Avis NSP → non éligible (aucun modèle Word pour « ne se prononce pas »).
        mvc.perform(get("/api/pv-examens/601").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDisponible").value(false));
    }

    @Test
    @DisplayName("Garde-fou dossier↔PV : suppression du PV signé d'un dossier EN_VERIFICATION → dossier remis à EXAMINE")
    void suppressionPvSigne_realigneDossierEnVerification() throws Exception {
        // Chaîne complète + PV signé FAVR → dossier EN_VERIFICATION (comme après une signature FAVR).
        dossierRepository.save(dossierLoc(700, "EN_VERIFICATION", "ANT", "PRMP001"));
        receptionRepository.save(reception(700, 700, "CTRCC1", true));
        dispatchRepository.save(dispatch(700, 700, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(700, 700, "CTRMEM"));
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(700); pv.setIdExamen(700); pv.setIdAvis("FAVR"); pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("SIGNE"); pv.setNbNavettes(0);
        pvExamenRepository.save(pv);

        // Suppression du PV signé (Administrateur).
        mvc.perform(delete("/api/pv-examens/700").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());

        // Le dossier ne reste pas bloqué EN_VERIFICATION (« PV signé introuvable ») : il redevient EXAMINE.
        mvc.perform(get("/api/dossiers/700").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EXAMINE"));
    }

    @Test
    @DisplayName("ServiceBeneficiaire : numCompte (FK tr_compte) exposé + SOA_CODE de 17 car. accepté (round-trip API)")
    void serviceBeneficiaire_numCompteEtSoaCodeLong_ok() throws Exception {
        // Chaîne marché (FK ID_DETAIL) + référentiels (FK NUM_COMPTE / SOA_CODE).
        dossierRepository.save(dossier(800, "BROUILLON"));
        ppmRepository.save(ppm(800, 800, "PRMP001"));
        marcheRepository.save(marche(9700, 800, 800));
        compteRepository.save(new cnm.prs.entity.Compte("CPT-BENEF-01", "Compte bénéficiaire", null, null));
        // SOA_CODE de 17 caractères (> ancien maximum 15) — prouve l'allongement à 25.
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-21-0-J00-00000", "SOA test"));

        String body = "{\"idBenef\":9700,\"idDetail\":9700,\"soaCode\":\"00-21-0-J00-00000\","
                + "\"numCompte\":\"CPT-BENEF-01\",\"ancMontBenef\":1000000,\"nouvMontBenef\":1200000}";
        // ⚠️ Écriture réservée PRMP/UGPM (garde 2026-08-24) et PK allouée serveur : on relit l'id RENVOYÉ,
        // plus celui envoyé — l'id client est désormais ignoré (cf. ServiceBeneficiaireService#create).
        String cree = mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numCompte").value("CPT-BENEF-01"))
                .andExpect(jsonPath("$.soaCode").value("00-21-0-J00-00000"))
                .andReturn().getResponse().getContentAsString();
        int idBenef = com.jayway.jsonpath.JsonPath.read(cree, "$.idBenef");

        // Relecture : compte + code SOA long persistés et exposés.
        mvc.perform(get("/api/service-beneficiaires/" + idBenef).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numCompte").value("CPT-BENEF-01"))
                .andExpect(jsonPath("$.soaCode").value("00-21-0-J00-00000"));
    }

    @Test
    @DisplayName("DELETE /api/marches/{id} : cascade les bénéficiaires (t_service_beneficiaire) — plus de 409 FK")
    void suppressionMarche_cascadeBeneficiaires() throws Exception {
        Dossier d = dossierLoc(810, "BROUILLON", "ANT", "PRMP001"); d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        ppmRepository.save(ppm(810, 810, "PRMP001"));
        marcheRepository.save(marche(9810, 810, 810));
        compteRepository.save(new cnm.prs.entity.Compte("CPT-810", "Compte", null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-21-0-J00-00000", "SOA"));
        // Bénéficiaire rattaché au marché 9810 → sans cascade, DELETE renverrait 409 (FK).
        String benef = mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idBenef\":9810,\"idDetail\":9810,\"soaCode\":\"00-21-0-J00-00000\","
                        + "\"numCompte\":\"CPT-810\",\"ancMontBenef\":1000000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idBenef = com.jayway.jsonpath.JsonPath.read(benef, "$.idBenef");   // PK allouée serveur

        // Suppression du marché → 204 (cascade en transaction), pas de 409.
        mvc.perform(delete("/api/marches/9810").header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        // Le bénéficiaire a été supprimé en cascade ; le marché a disparu.
        mvc.perform(get("/api/service-beneficiaires/" + idBenef).header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/marches/9810").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Import PPM PDF (read-only) : parse l'en-tête + résout l'entité + avertissements ; non-PDF → 400")
    void importPpm_pdf_prefill_ok() throws Exception {
        EntiteContract e = entite(900, 1, "ANT"); e.setLibelleEntite("MINISTERE ECONOMIE");
        entiteContractRepository.save(e);
        natureRepository.save(new Nature(1, "Fournitures et services", null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES - Exercice 2026",
                "Autorite Contractante : MINISTERE ECONOMIE",
                "Documentation et abonnement Fournitures et services 1 005 000",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercice").value(2026))
                .andExpect(jsonPath("$.idEntiteContract").value(900))
                .andExpect(jsonPath("$.autoriteContractante").value("MINISTERE ECONOMIE"))
                .andExpect(jsonPath("$.dateSignature").value("2026-04-14"))
                .andExpect(jsonPath("$.avertissements").isArray())
                .andExpect(jsonPath("$.marches").isArray());

        // Robustesse : fichier non-PDF → 400 (pas de données partielles silencieuses).
        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "x.txt", "text/plain", "ceci n'est pas un pdf".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Import PPM PDF — parsing calibré du tableau : OBJET multi-lignes recomposé, montants, bénéficiaire, 3 prévisions")
    void importPpm_tableauCalibre_ok() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));

        // Structure du PPM officiel : en-tête doc, en-tête colonnes (+ sous-colonnes ignorées), 1 ligne de données
        // (NATURE + OBJET sur 3 lignes, puis ligne montants), puis « Fait à … ». Sans accents (police Helvetica).
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "Date d'etablissement du Document initial: 14/04/2026",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "SERVICE BENEFICIAIRE COMPTE MONTANT ESTIMATIF PAR BENEFICIAIRE",
                "Travaux Travaux de fabrication et",
                "installation des etageres",
                "metalliques fixes",
                "5 550 000.00 Achat Direct RPI 00-21-0-J00-00000 6211 5 550 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le _ _ /_ _ /_ _ _ _");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercice").value(2026))
                .andExpect(jsonPath("$.dateSignature").value("2026-04-14"))
                .andExpect(jsonPath("$.marches", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].designationMarche").value("Travaux de fabrication et installation des etageres metalliques fixes"))
                .andExpect(jsonPath("$.marches[0].natureLibelle").value("Travaux"))
                .andExpect(jsonPath("$.marches[0].idNature").value(1))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("Achat Direct"))
                .andExpect(jsonPath("$.marches[0].idMode").value(nullValue()))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-21-0-J00-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("6211"))
                .andExpect(jsonPath("$.marches[0].previsions[0].processus").value("LANCEMENT"))
                .andExpect(jsonPath("$.marches[0].previsions[0].dateDebut").value("2026-06-01"))
                .andExpect(jsonPath("$.marches[0].previsions[1].dateDebut").value("2026-06-02"))
                .andExpect(jsonPath("$.marches[0].previsions[2].dateDebut").value("2026-06-12"))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*Achat Direct.*/)]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — lots extraits de la désignation (« repartis en 04 Lots : Lot 01 : … ») : 4 lots + désignation INTÉGRALE conservée")
    void importPpm_lotsExtraitsDeLaDesignation() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        // Cas réel observé (sans accents — police Helvetica du fixture) : allotissement décrit dans l'OBJET.
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "Date d'etablissement du Document initial: 14/04/2026",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Travaux de traitement des points noirs sur la route reliant Fenomanana et",
                "Analamarina dans le district de Faratsiho - Vakinakaratra repartis en 04 Lots :",
                "Lot 01 : traitement de breche et chaussee a Ampakandrano; Lot 02 : traitement de",
                "breche et de la chaussee a Ambatofotsikely; Lot 03 : traitement de la chaussee a",
                "Ambohiborona ; Lot 04 : traitement de la digue vers Analamarina.",
                "1 150 000 000.00 Appel d'offres ouvert RPI 00-21-0-J00-00000 6211 1 150 000 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                // Désignation INTÉGRALE conservée, énumération des lots comprise (décision revisitée
                // 2026-07-18 : le doublon texte/lots[] est accepté et voulu).
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Travaux de traitement des points noirs sur la route reliant Fenomanana et "
                                + "Analamarina dans le district de Faratsiho - Vakinakaratra repartis en 04 Lots : "
                                + "Lot 01 : traitement de breche et chaussee a Ampakandrano; Lot 02 : traitement de "
                                + "breche et de la chaussee a Ambatofotsikely; Lot 03 : traitement de la chaussee a "
                                + "Ambohiborona ; Lot 04 : traitement de la digue vers Analamarina."))
                .andExpect(jsonPath("$.marches[0].lots", hasSize(4)))
                .andExpect(jsonPath("$.marches[0].lots[0].designationLot").value("traitement de breche et chaussee a Ampakandrano"))
                .andExpect(jsonPath("$.marches[0].lots[1].designationLot").value("traitement de breche et de la chaussee a Ambatofotsikely"))
                .andExpect(jsonPath("$.marches[0].lots[2].designationLot").value("traitement de la chaussee a Ambohiborona"))
                .andExpect(jsonPath("$.marches[0].lots[3].designationLot").value("traitement de la digue vers Analamarina"))
                // Champs descriptifs non portés par le texte → null (aucun contrôle de somme, règle actée).
                .andExpect(jsonPath("$.marches[0].lots[0].montLot").value(nullValue()))
                // Pas d'avertissement d'allotissement quand l'extraction réussit.
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*Allotissement.*/)]", hasSize(0)));
    }

    @Test
    @DisplayName("Import PPM — lots (variantes SIGMP) : « repartie en trois 3 lots: lot n1: … - lot n2: … », « deux 2 lots », et « LOT N°01: … LOT N°02: … » sans annonce")
    void importPpm_lotsVariantes() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                // L14 : annonce lettres + chiffres, séparateur « - », 3 lots.
                "Travaux Rehabilitation de routes repartie en trois 3 lots: lot n1: piste Est - lot n2: piste Ouest - lot n3: piste Nord",
                "500 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 500 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                // L15 : annonce « deux 2 lots », 2 lots.
                "Travaux Entretien de pistes repartie en deux 2 lots: lot n1: piste A - lot n2: piste B",
                "400 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 400 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                // L18 : SANS annonce, marqueurs « LOT N°01: » / « LOT N°02: ».
                "Travaux Construction de batiments LOT N°01: batiment A LOT N°02: batiment B",
                "300 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 300 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(3)))
                // L14 : 3 lots (séparateur « - » retiré du texte de lot).
                .andExpect(jsonPath("$.marches[0].lots", hasSize(3)))
                .andExpect(jsonPath("$.marches[0].lots[0].designationLot").value("piste Est"))
                .andExpect(jsonPath("$.marches[0].lots[1].designationLot").value("piste Ouest"))
                .andExpect(jsonPath("$.marches[0].lots[2].designationLot").value("piste Nord"))
                .andExpect(jsonPath("$.marches[0].anomalies[?(@.champ=='lot')]", hasSize(0)))
                // L15 : 2 lots.
                .andExpect(jsonPath("$.marches[1].lots", hasSize(2)))
                .andExpect(jsonPath("$.marches[1].lots[0].designationLot").value("piste A"))
                .andExpect(jsonPath("$.marches[1].lots[1].designationLot").value("piste B"))
                // L18 : 2 lots SANS annonce (≥2 marqueurs suffisent).
                .andExpect(jsonPath("$.marches[2].lots", hasSize(2)))
                .andExpect(jsonPath("$.marches[2].lots[0].designationLot").value("batiment A"))
                .andExpect(jsonPath("$.marches[2].lots[1].designationLot").value("batiment B"));
    }

    @Test
    @DisplayName("Import PPM — lots : objet COMMENÇANT par le marqueur (« LOT N°01:… LOT N°02:… » en position 0, séparés par un espace) → 2 lots")
    void importPpm_lotsObjetCommenceParMarqueur() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        // Cas réel 188-SIGMP L18 : la désignation COMMENCE par « LOT N°01: » (aucun préambule), lots séparés
        // par un simple espace avant « LOT N°02: ». (Sans accents — police du fixture.)
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux LOT N°01:Travaux de rehabilitation du batiment A au siege de la direction generale LOT N°02:Travaux de rehabilitation du logement de fonction du DG a Fort-Duchesne.",
                "300 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 300 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].lots", hasSize(2)))
                .andExpect(jsonPath("$.marches[0].lots[0].designationLot").value(
                        "Travaux de rehabilitation du batiment A au siege de la direction generale"))
                .andExpect(jsonPath("$.marches[0].lots[1].designationLot").value(
                        "Travaux de rehabilitation du logement de fonction du DG a Fort-Duchesne"))
                // Extraction réussie → pas d'anomalie « lot ». Désignation intégrale (le marqueur y reste).
                .andExpect(jsonPath("$.marches[0].anomalies[?(@.champ=='lot')]", hasSize(0)))
                .andExpect(jsonPath("$.marches[0].designationMarche", org.hamcrest.Matchers.startsWith("LOT N°01:")));
    }

    @Test
    @DisplayName("Import PPM — lots incohérents (« trois 3 lots » annoncés, 2 marqueurs) : lots vides + anomalie champ:lot LOT_INCOHERENT")
    void importPpm_lotsIncoherent_anomalie() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Rehabilitation repartie en trois 3 lots: lot n1: piste A - lot n2: piste B",
                "500 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 500 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                // 3 annoncés mais 2 marqueurs → rien d'extrait + anomalie « lot » pour la revue front.
                .andExpect(jsonPath("$.marches[0].lots", hasSize(0)))
                .andExpect(jsonPath("$.marches[0].anomalies[?(@.champ=='lot' && @.type=='LOT_INCOHERENT' && @.gravite=='A_VERIFIER')]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — allotissement incohérent (« 03 Lots » annoncés, 2 segments) : lots vides, désignation intégrale + avertissement")
    void importPpm_lotsCompteIncoherent() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Rehabilitation de pistes repartis en 03 Lots : Lot 01 : piste A; Lot 02 : piste B",
                "5 550 000.00 Achat Direct RPI 00-21-0-J00-00000 6211 5 550 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                // Dans le doute : rien d'extrait, désignation INTÉGRALE (comportement antérieur conservé).
                .andExpect(jsonPath("$.marches[0].lots", hasSize(0)))
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Rehabilitation de pistes repartis en 03 Lots : Lot 01 : piste A; Lot 02 : piste B"))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*Allotissement.*3 lot.*2 segment.*/)]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — MODE|FINANCEMENT|SERVICE aplatis (cellules multi-lignes SIGMP, RPI) : mode net, financement=RPI, service→soaLibelle (dernier mot isolé conservé)")
    void importPpm_modeFinancementServiceMultiligne() throws Exception {
        // 460 SIGMP : mode + RPI + « Service Administratif et Financier », « Financier » seul sur sa ligne
        // physique (piège BRUIT_PAGE : « Financier » ⊃ « finan » ne doit PAS être filtré).
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "FOURNITURES Fourniture de test 6 000 000.00 CONSULTATION DE PRIX OUVERTE RPI Service Administratif et",
                "Financier",
                "6111 6 000 000.00 21/05/2026 01/06/2026 08/06/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].designationMarche").value("Fourniture de test"))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("CONSULTATION DE PRIX OUVERTE"))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].beneficiaires", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value(nullValue()))
                // « Financier » conservé (BRUIT_PAGE) + service capté après le financement (découpage).
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaLibelle").value("Service Administratif et Financier"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("6111"));
    }

    @Test
    @DisplayName("Import PPM — financement « FR » + service textuel « TOUT SERVICE » (163 SIGMP) : mode net, financement=FR, service→soaLibelle")
    void importPpm_financementFrServiceTextuel() throws Exception {
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "FOURNITURES Fourniture eau 2 480 000.00 ACHAT DIRECT FR TOUT SERVICE 6471 2 480 000.00 23/03/2026 24/03/2026 25/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("ACHAT DIRECT"))
                .andExpect(jsonPath("$.marches[0].financement").value("FR"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaLibelle").value("TOUT SERVICE"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value(nullValue()))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("6471"));
    }

    @Test
    @DisplayName("Import PPM — mode variante « … PIP » collé au financement RPI (379) : le mode CONSERVE son PIP (idMode=8), financement=RPI, SOA codé")
    void importPpm_modeVariantePipFinancementRpi() throws Exception {
        modePassationRepository.save(new ModePassation(8, "CONSULTATION DE PRIX OUVERTE PIP", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "FOURNITURES Fourniture test 75 000 000.00 CONSULTATION DE PRIX OUVERTE PIP RPI 00-61-0-D10-00000 2317 75 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches[0].idMode").value(8))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("CONSULTATION DE PRIX OUVERTE PIP"))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-61-0-D10-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaLibelle").value(nullValue()));
    }

    @Test
    @DisplayName("Saisie PPM — bénéficiaire par soaLibelle (sans code) : SOA résolu-ou-créé par libellé, dédupliqué, code SOA dérivé")
    void saisiePpm_soaBeneficiaireParLibelle() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        long soaAvant = soaBeneficiaireRepository.count();

        // 3 marchés : A et B portent le MÊME service textuel (dédup) ; C un autre → 2 SOA créés.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-SOA\","
                + "\"marches\":["
                + "{\"designationMarche\":\"A\",\"montEstim\":2480000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaLibelle\":\"Tout Service\",\"numCompte\":\"6471\",\"ancMontBenef\":2480000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"B\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaLibelle\":\"TOUT SERVICE\",\"numCompte\":\"6472\",\"ancMontBenef\":1000000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"C\",\"montEstim\":500000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaLibelle\":\"Autre Service\",\"numCompte\":\"6473\",\"ancMontBenef\":500000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // 2 SOA créés (« Tout Service » dédupliqué malgré la casse, + « Autre Service »).
        org.junit.jupiter.api.Assertions.assertEquals(soaAvant + 2, soaBeneficiaireRepository.count());
        List<cnm.prs.entity.SoaBeneficiaire> soas = soaBeneficiaireRepository.findAll();
        cnm.prs.entity.SoaBeneficiaire tout = soas.stream()
                .filter(s -> "Tout Service".equals(s.getLibelle())).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(tout.getSoaCode());   // code dérivé du libellé
        org.junit.jupiter.api.Assertions.assertTrue(tout.getSoaCode().length() <= 25);
        org.junit.jupiter.api.Assertions.assertEquals(1, soas.stream()
                .filter(s -> "Tout Service".equals(s.getLibelle())).count());
        org.junit.jupiter.api.Assertions.assertEquals(1, soas.stream()
                .filter(s -> "Autre Service".equals(s.getLibelle())).count());
        // Les lignes bénéficiaires de A et B référencent le MÊME code SOA (dédup par libellé).
        List<cnm.prs.entity.ServiceBeneficiaire> benefs = serviceBeneficiaireRepository.findAll();
        long distinctsToutService = benefs.stream()
                .filter(b -> tout.getSoaCode().equals(b.getSoaCode())).count();
        org.junit.jupiter.api.Assertions.assertEquals(2, distinctsToutService);
    }

    @Test
    @DisplayName("Import PPM xlsx — colonnes explicites : 2 marchés (dont multi-bénéficiaire + lots), référentiels résolus, 0 anomalie")
    void importXlsx_ok() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        String[] entetes = { "objet", "forme", "nature", "montant estimatif", "mode", "financement",
                "soa", "compte", "montant beneficiaire", "date lancement", "date ouverture", "date attribution",
                "lots", "exercice" };
        Object[][] lignes = {
                { "Travaux de rehabilitation de la RN 13", "CONTRAT_CADRE", "Travaux", 500000000L,
                        "Consultation de prix ouverte", "RPI", "00-61-0-D10-00000", "2441", 500000000L,
                        "06/03/2026", "16/03/2026", "27/03/2026", "Lot A | Lot B", 2026L },
                // Marché à 2 bénéficiaires : 2e ligne = objet vide (continuation).
                { "Fourniture de materiel", "", "Travaux", 3000000L, "Consultation de prix ouverte", "RPI",
                        "00-61-0-D10-00000", "2441", 1000000L, "06/03/2026", "16/03/2026", "27/03/2026", "", "" },
                { "", "", "", "", "", "", "00-61-0-D10-00000", "2441", 2000000L, "", "", "", "", "" } };
        byte[] xlsx = xlsxAvecLignes(entetes, lignes);

        mvc.perform(multipart("/api/saisies/ppm/import-xlsx")
                .file(new MockMultipartFile("fichier", "ppm.xlsx", "application/vnd.ms-excel", xlsx))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(2)))
                .andExpect(jsonPath("$.exercice").value(2026))
                .andExpect(jsonPath("$.nbAVerifier").value(0))
                // Marché 0 : transcription exacte + référentiels résolus + lots + forme.
                .andExpect(jsonPath("$.marches[0].designationMarche").value("Travaux de rehabilitation de la RN 13"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(500000000.0))
                .andExpect(jsonPath("$.marches[0].formeMarche").value("CONTRAT_CADRE"))
                .andExpect(jsonPath("$.marches[0].idNature").value(1))
                .andExpect(jsonPath("$.marches[0].idMode").value(1))
                .andExpect(jsonPath("$.marches[0].beneficiaires", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-61-0-D10-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(500000000.0))
                .andExpect(jsonPath("$.marches[0].lots", hasSize(2)))
                .andExpect(jsonPath("$.marches[0].lots[0].designationLot").value("Lot A"))
                .andExpect(jsonPath("$.marches[0].previsions[0].dateDebut").value("2026-03-06"))
                .andExpect(jsonPath("$.marches[0].anomalies", hasSize(0)))
                // Marché 1 : 2 bénéficiaires (Σ = montant), 0 anomalie.
                .andExpect(jsonPath("$.marches[1].beneficiaires", hasSize(2)))
                .andExpect(jsonPath("$.marches[1].montEstim").value(3000000.0))
                .andExpect(jsonPath("$.marches[1].anomalies", hasSize(0)));
    }

    @Test
    @DisplayName("Import PPM xlsx — montant ≠ Σ bénéficiaires → anomalie MONTANT_INCOHERENT BLOQUANT (pas d'auto-correction en tableur)")
    void importXlsx_montantIncoherent() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        String[] entetes = { "objet", "nature", "montant estimatif", "mode", "financement", "soa", "compte",
                "montant beneficiaire", "date lancement", "date ouverture", "date attribution" };
        Object[][] lignes = { { "Travaux divers", "Travaux", 500000000L, "Consultation de prix ouverte", "RPI",
                "00-61-0-D10-00000", "2441", 400000000L, "06/03/2026", "16/03/2026", "27/03/2026" } };

        mvc.perform(multipart("/api/saisies/ppm/import-xlsx")
                .file(new MockMultipartFile("fichier", "ppm.xlsx", "application/vnd.ms-excel",
                        xlsxAvecLignes(entetes, lignes)))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbAVerifier").value(1))
                .andExpect(jsonPath("$.marches[0].anomalies[?(@.champ=='montEstim' && @.type=='MONTANT_INCOHERENT' && @.gravite=='BLOQUANT' && @.corrige==false)]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM xlsx — gabarit téléchargeable (.xlsx, en-têtes + notice)")
    void importXlsx_gabarit() throws Exception {
        byte[] gabarit = mvc.perform(get("/api/saisies/ppm/import-xlsx/gabarit").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        // Magic bytes d'un .xlsx (archive ZIP « PK »).
        org.junit.jupiter.api.Assertions.assertTrue(gabarit.length > 100
                && gabarit[0] == 'P' && gabarit[1] == 'K');
        // Le gabarit se ré-importe (les lignes d'exemple sont exploitables) → ≥1 marché.
        mvc.perform(multipart("/api/saisies/ppm/import-xlsx")
                .file(new MockMultipartFile("fichier", "gabarit.xlsx", "application/vnd.ms-excel", gabarit))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(2)));
    }

    /** Génère en mémoire un .xlsx (POI) : ligne 0 = en-têtes, puis les lignes de données — pour tester l'import tableur. */
    private byte[] xlsxAvecLignes(String[] entetes, Object[][] lignes) throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Marchés");
            org.apache.poi.ss.usermodel.Row h = sheet.createRow(0);
            for (int i = 0; i < entetes.length; i++) {
                h.createCell(i).setCellValue(entetes[i]);
            }
            for (int r = 0; r < lignes.length; r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
                for (int c = 0; c < lignes[r].length; c++) {
                    Object v = lignes[r][c];
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                    if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(v));
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("Import PPM — normalisation étendue : « APPEL D'OFFRE OUVERT » (singulier) résout idMode=1, modeLibelle CANONIQUE, sans avertissement")
    void importPpm_modeSingulierResolu() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Rehabilitation de la route reliant Tsiatosika",
                "23 100 000 000.00 APPEL D'OFFRE OUVERT RPI 00-21-0-J00-00000 6211 23 100 000 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].idMode").value(1))
                // Libellé CANONIQUE du référentiel (pas le texte brut du PDF) — le badge AGPM du front s'aligne.
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("Appel d'offres ouvert"))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*Mode de passation.*/)]", hasSize(0)));
    }

    @Test
    @DisplayName("Import PPM — régression 161-PPM MTP : fragment d'objet (n° de route) collé au montant ou isolé sur sa ligne → recollé à l'objet, montant réaligné")
    void importPpm_fragmentObjetCollageMontant_regression() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));

        // Deux formes réelles du bug (161-PPM MTP.pdf) :
        //  A) le n° de route « 33 » est collé au montant sur la même ligne physique (contamination) ;
        //  B) le n° de route « 44 » est SEUL sur sa propre ligne physique (fragment autrefois filtré).
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Travaux de reparation de la breche au",
                "PK 38+800 de la RNT 33 590 000 000.00 CONSULTATION DE PRIX OUVERTE RPI 00-61-0-D10-00000 2441 590 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Travaux Travaux d'urgence pour la construction",
                "d'un dalot au PK 194+450 sur la RNS",
                "44",
                "140 000 000.00 CONSULTATION DE PRIX OUVERTE RPI 00-61-0-D10-00000 2441 140 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(2)))
                // A) « 33 » recollé, montant réaligné sur 590 000 000 (plus de 33 590 000 000).
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Travaux de reparation de la breche au PK 38+800 de la RNT 33"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(590000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(590000000.00))
                // B) « 44 » (fragment isolé) conservé et recollé, montant correct 140 000 000.
                .andExpect(jsonPath("$.marches[1].designationMarche").value(
                        "Travaux d'urgence pour la construction d'un dalot au PK 194+450 sur la RNS 44"))
                .andExpect(jsonPath("$.marches[1].montEstim").value(140000000.00))
                .andExpect(jsonPath("$.marches[1].beneficiaires[0].ancMontBenef").value(140000000.00))
                // La correction est tracée (non silencieuse) — une par marché.
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*recoll.*objet.*/)]", hasSize(2)));
    }

    @Test
    @DisplayName("Import PPM — invariant SYMÉTRIQUE : fragment collé au MONTANT BÉNÉFICIAIRE (« 3 125 000 000 » pour un estimatif 125M) → recollé à l'objet, bénéficiaire réaligné")
    void importPpm_fragmentMontantBeneficiaire_regression() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        // Cas réel 379.PPM : montEstim correct (125M) mais le montant bénéficiaire est contaminé par un « 3 ».
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Fourniture d'habillement pour le personnel",
                "125 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 3 125 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                // « 3 » recollé à l'objet ; montant bénéficiaire ramené à 125M (== estimatif).
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Fourniture d'habillement pour le personnel 3"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(125000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(125000000.00))
                // Correction tracée + anomalie montant auto-corrigée (à confirmer).
                .andExpect(jsonPath("$.marches[0].anomalies[?(@.champ=='montEstim' && @.type=='MONTANT_INCOHERENT' && @.corrige==true)]", hasSize(1)))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*recoll.*objet.*/)]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — invariant symétrique sur la colonne NOUVEAU : fragment collé au nouveau montant bénéficiaire → recollé, réaligné")
    void importPpm_fragmentNouveauMontantBeneficiaire_regression() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Appel d'offres ouvert", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        // montEstim 500M + nouvMontEstim 600M ; colonne « ancien » cohérente, mais le NOUVEAU montant
        // bénéficiaire est contaminé par un « 4 » (« 4 600 000 000 » au lieu de 600 000 000).
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Rehabilitation de la route nationale",
                "500 000 000.00 600 000 000.00 Appel d'offres ouvert RPI 00-61-0-D10-00000 2441 500 000 000.00 4 600 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Rehabilitation de la route nationale 4"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(500000000.00))
                .andExpect(jsonPath("$.marches[0].nouvMontEstim").value(600000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(500000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].nouvMontBenef").value(600000000.00))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*nouveau montant.*/)]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — anomalies structurées : ligne propre (0), montant auto-corrigé, objet tronqué, référentiel inconnu ; nbAVerifier")
    void importPpm_anomaliesStructurees() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA test"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte test", null, null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                // [0] PROPRE : tout résout, montant == Σ, objet fini par un mot → 0 anomalie.
                "Travaux Rehabilitation de la route principale du secteur",
                "500 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 500 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                // [1] fragment « 33 » collé au montant → MONTANT_INCOHERENT auto-corrigé.
                "Travaux Reparation de la breche au PK 38+800 de la RNT",
                "33 590 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 590 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                // [2] objet tronqué (finit par RNS sans numéro), montant cohérent → OBJET_TRONQUE_PROBABLE.
                "Travaux Travaux de reparation du pont sur la RNS",
                "400 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 400 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                // [3] mode absent du référentiel → REFERENTIEL_INCONNU / mode (objet fini par un mot).
                "Travaux Rehabilitation de la route secondaire du district",
                "300 000 000.00 Achat Direct RPI 00-61-0-D10-00000 2441 300 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(4)))
                // 3 marchés sur 4 portent ≥1 anomalie ([0] est propre).
                .andExpect(jsonPath("$.nbAVerifier").value(3))
                .andExpect(jsonPath("$.marches[0].anomalies", hasSize(0)))
                // [1] montant auto-corrigé (champ + type + corrige exacts) ; objet et montant réalignés.
                .andExpect(jsonPath("$.marches[1].anomalies[?(@.champ=='montEstim' && @.type=='MONTANT_INCOHERENT' && @.gravite=='A_VERIFIER' && @.corrige==true)]", hasSize(1)))
                .andExpect(jsonPath("$.marches[1].montEstim").value(590000000.00))
                .andExpect(jsonPath("$.marches[1].designationMarche").value("Reparation de la breche au PK 38+800 de la RNT 33"))
                // [2] objet tronqué probable.
                .andExpect(jsonPath("$.marches[2].anomalies[?(@.champ=='objet' && @.type=='OBJET_TRONQUE_PROBABLE' && @.gravite=='A_VERIFIER')]", hasSize(1)))
                // [3] mode inconnu au référentiel.
                .andExpect(jsonPath("$.marches[3].anomalies[?(@.champ=='mode' && @.type=='REFERENTIEL_INCONNU')]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — mode non résolu mais proche : avertissement « vouliez-vous dire … ? » (pas d'auto-résolution fuzzy)")
    void importPpm_modeSuggestion() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Appel d'offres ouvert", null, null, null, null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Rehabilitation de piste",
                "5 550 000.00 Apel d'offres ouvert RPI 00-21-0-J00-00000 6211 5 550 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches[0].idMode").value(nullValue()))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("Apel d'offres ouvert"))
                .andExpect(jsonPath(
                        "$.avertissements[?(@ =~ /.*Apel d'offres ouvert.*vouliez-vous dire.*Appel d'offres ouvert.*/)]",
                        hasSize(1)));
    }

    @Test
    @DisplayName("Saisie à la volée — « APPEL D'OFFRE OUVERT » résout le mode canonique (pas de doublon) → sous-type PPM-AGPM (contournement AGPM fermé)")
    void saisie_modeSingulier_fermeContournementAgpm() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);
        long modesAvant = modePassationRepository.count();

        // La coquille du PDF (singulier) part telle quelle à la création — avant : quasi-doublon sans declencheAgpm.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"designationMarche\":\"Route Tsiatosika\",\"montEstim\":23100000000,\"idNature\":1,"
                + "\"modeLibelle\":\"APPEL D'OFFRE OUVERT\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                // Sous-type dérivé PPM-AGPM : la règle AGPM s'applique malgré la coquille.
                .andExpect(jsonPath("$.idSousType").value("PPM-AGPM"))
                .andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        // Résolu sur le mode canonique 1 — AUCUN doublon créé.
        org.junit.jupiter.api.Assertions.assertEquals(1,
                marcheRepository.findByIdDossier(idDoss).get(0).getIdMode());
        org.junit.jupiter.api.Assertions.assertEquals(modesAvant, modePassationRepository.count());
    }

    @Test
    @DisplayName("Saisie à la volée — libellé réellement nouveau toujours créé ; libellé proche d'un mode AGPM créé MAIS signalé (audit)")
    void saisie_modeNouveau_creeEtSignaleSiProcheAgpm() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);

        // (1) Libellé réellement nouveau → créé à la volée (garde inchangée).
        String b1 = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"designationMarche\":\"M1\",\"montEstim\":1000,\"idNature\":1,"
                + "\"modeLibelle\":\"Concours d'architecture\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(b1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idSousType").value("PPM"));   // pas déclencheur
        org.junit.jupiter.api.Assertions.assertTrue(modePassationRepository.findAll().stream()
                .anyMatch(m -> "Concours d'architecture".equals(m.getLibelle())), "mode nouveau créé");

        // (2) Libellé non résolu mais PROCHE du mode AGPM (« ouver » tronqué, distance 1) → créé + signal audit.
        String b2 = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"designationMarche\":\"M2\",\"montEstim\":1000,\"idNature\":1,"
                + "\"modeLibelle\":\"Appel d'offre ouver\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(b2))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION_MODE_PROCHE_AGPM')]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — régression 161-PPM MTP : « 11,700 Km » dans la désignation ne casse plus le découpage des colonnes")
    void importPpm_nombreDansDesignation_regression() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);

        // Ligne réelle de 161-PPM MTP.pdf : kilométrage « 11,700 Km (contrat cadre) » DANS la désignation.
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Travaux d'amenagement de la voie rapide dans la Commune Urbaine de Sambava,",
                "croisement Menagisy vers Ambodisatrana de longueur 11,700 Km (contrat cadre)",
                "15 000 000 000.00 APPEL D'OFFRE OUVERT RPI 00-61-0-D10-00000 2441 15 000 000 000.00 06/03/2026 20/03/2026 31/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                // Désignation INTÉGRALE, kilométrage compris — plus de troncature sur « 11,70 ».
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Travaux d'amenagement de la voie rapide dans la Commune Urbaine de Sambava, "
                                + "croisement Menagisy vers Ambodisatrana de longueur 11,700 Km (contrat cadre)"))
                // Forme du marché relevée dans l'objet : « (contrat cadre) » → CONTRAT_CADRE.
                .andExpect(jsonPath("$.marches[0].formeMarche").value("CONTRAT_CADRE"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(15000000000.00))
                // Mode résolu canonique (règle pluriel) — plus d'avertissement « Km contrat ».
                .andExpect(jsonPath("$.marches[0].idMode").value(1))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("Appel d'offres ouvert"))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-61-0-D10-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("2441"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(15000000000.00))
                .andExpect(jsonPath("$.marches[0].previsions[0].dateDebut").value("2026-03-06"))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*Km.*/)]", hasSize(0)));
    }

    @Test
    @DisplayName("Import PPM — régression 161-PPM MTP : dimensions « 2,00 x 2,00 m » + point kilométrique « PK 208+000 » dans la désignation")
    void importPpm_dimensionsEtPkDansDesignation_regression() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        ModePassation cpo = new ModePassation(1, "Consultation de prix ouverte", null, null, null, null);
        modePassationRepository.save(cpo);

        // Ligne réelle de 161-PPM MTP.pdf : dimensions d'un dalot DANS la désignation, et « 208 » du
        // point kilométrique qui, sur une mauvaise ancre, se fait passer pour un numéro de compte.
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Travaux d'urgence sur la construction d'un dalot 2,00 x 2,00 m et reparation",
                "du chaussee sur la RNS 5A PK 208+000 Fokontany Angalovanga District Vohemar",
                "200 000 000.00 CONSULTATION DE PRIX OUVERTE RPI 00-61-0-D10-00000 2441 200 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                // Désignation INTÉGRALE, dimensions et PK compris — plus d'ancrage sur « 2,00 ».
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Travaux d'urgence sur la construction d'un dalot 2,00 x 2,00 m et reparation "
                                + "du chaussee sur la RNS 5A PK 208+000 Fokontany Angalovanga District Vohemar"))
                // Aucune forme mentionnée dans l'objet → défaut QUANTITE_FIXE.
                .andExpect(jsonPath("$.marches[0].formeMarche").value("QUANTITE_FIXE"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(200000000.00))
                .andExpect(jsonPath("$.marches[0].idMode").value(1))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("Consultation de prix ouverte"))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-61-0-D10-00000"))
                // Le vrai compte 2441 — plus le « 208 » du point kilométrique.
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("2441"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(200000000.00))
                .andExpect(jsonPath("$.marches[0].previsions[0].dateDebut").value("2026-03-06"))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*reparation.*/)]", hasSize(0)));
    }

    @Test
    @DisplayName("Import PPM — forme du marché relevée dans l'objet : « (Contrat Cadre) » → CONTRAT_CADRE, « marches a commandes » → A_COMMANDE, désignations intégrales")
    void importPpm_formeMarcheDetectee() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Travaux d'entretien periodique de la RN 13 (Contrat Cadre)",
                "15 000 000.00 APPEL D'OFFRE OUVERT RPI 00-61-0-D10-00000 2441 15 000 000.00 06/03/2026 20/03/2026 31/03/2026",
                "Travaux Fourniture de carburants, marches a commandes, pour le parc automobile",
                "5 000 000.00 ACHAT DIRECT RPI 00-61-0-D10-00000 2441 5 000 000.00 06/03/2026 20/03/2026 31/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(2)))
                // On relève, on ne retire pas : la mention reste dans la désignation.
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Travaux d'entretien periodique de la RN 13 (Contrat Cadre)"))
                .andExpect(jsonPath("$.marches[0].formeMarche").value("CONTRAT_CADRE"))
                // Pluriels tolérés (« marches a commandes ») ; frontières de mots respectées.
                .andExpect(jsonPath("$.marches[1].designationMarche").value(
                        "Fourniture de carburants, marches a commandes, pour le parc automobile"))
                .andExpect(jsonPath("$.marches[1].formeMarche").value("A_COMMANDE"));
    }

    @Test
    @DisplayName("Saisie PPM — formeMarche : explicite conservée, absente → défaut QUANTITE_FIXE, code inconnu → 400 ciblé")
    void saisiePpm_formeMarche() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-FM\","
                + "\"marches\":[{\"designationMarche\":\"Fourniture de carburant\",\"formeMarche\":\"A_COMMANDE\",\"montEstim\":1000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"Construction batiment\",\"montEstim\":2000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss
                        + " && @.designationMarche=='Fourniture de carburant')].formeMarche", hasItem("A_COMMANDE")))
                // Absente à la saisie → défaut serveur QUANTITE_FIXE (jamais null).
                .andExpect(jsonPath("$[?(@.idDossier==" + idDoss
                        + " && @.designationMarche=='Construction batiment')].formeMarche", hasItem("QUANTITE_FIXE")));

        // Code inconnu → 400 ciblé.
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"A_COMMANDE\"", "\"FORFAIT\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Forme de marché inconnue")));
    }

    @Test
    @DisplayName("Migration forme du marché — lignes historiques (colonne NULL) reprises depuis la désignation, idempotente")
    void formeMarcheMigration_repriseHistorique() throws Exception {
        Dossier d = dossier(9601, "SOUMIS");
        d.setIdLocalite("ANT");
        d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(9601, 9601, "PRMP001"));
        // Lignes « historiques » : colonne FORME_MARCHE à NULL (état d'avant l'ajout du champ).
        Marche cadre = marche(96010, 9601, 9601);
        cadre.setDesignationMarche("Travaux d'amenagement de la voie rapide (Contrat cadre)");
        cadre.setFormeMarche(null);
        Marche commande = marche(96011, 9601, 9601);
        commande.setDesignationMarche("Fourniture de carburant, marche a commande, pour le parc");
        commande.setFormeMarche(null);
        Marche fixe = marche(96012, 9601, 9601);
        fixe.setDesignationMarche("Construction d'un batiment administratif");
        fixe.setFormeMarche(null);
        marcheRepository.saveAll(java.util.List.of(cadre, commande, fixe));
        org.junit.jupiter.api.Assertions.assertEquals(3, marcheRepository.findByFormeMarcheIsNull().size());

        formeMarcheMigration.run();

        // Formes dérivées des désignations (mêmes motifs que l'import) ; plus aucune ligne à reprendre.
        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.FormeMarche.CONTRAT_CADRE,
                marcheRepository.findById(96010).orElseThrow().getFormeMarche());
        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.FormeMarche.A_COMMANDE,
                marcheRepository.findById(96011).orElseThrow().getFormeMarche());
        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.FormeMarche.QUANTITE_FIXE,
                marcheRepository.findById(96012).orElseThrow().getFormeMarche());
        org.junit.jupiter.api.Assertions.assertTrue(marcheRepository.findByFormeMarcheIsNull().isEmpty());
    }

    @Test
    @DisplayName("Import PPM PDF multi-pages : les 2 pages sont lues (en-tête/pied répétés ignorés, borne sur le dernier « Fait à … »)")
    void importPpm_multiPages_ok() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        natureRepository.save(new Nature(2, "Fournitures", null));

        // Page 1 : en-tête doc + tableau (1 marché), puis pied de page RÉPÉTÉ (« Fait à … » + n° de page).
        String[] page1 = {
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "Date d'etablissement du Document initial: 14/04/2026",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "SERVICE BENEFICIAIRE COMPTE MONTANT ESTIMATIF PAR BENEFICIAIRE",
                "Travaux Travaux de fabrication et",
                "installation des etageres",
                "5 550 000.00 Achat Direct RPI 00-21-0-J00-00000 6211 5 550 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le 14 avril 2026",   // pied répété : NE doit PAS clore le tableau
                "Page 1 sur 2" };
        // Page 2 : en-tête de colonnes REJOUÉ, 2e marché, puis pied final (dernier « Fait à … » = vraie fin).
        String[] page2 = {
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "SERVICE BENEFICIAIRE COMPTE MONTANT ESTIMATIF PAR BENEFICIAIRE",
                "Fournitures Fournitures de bureau",
                "diverses",
                "1 200 000.00 Appel d'Offres RPI 00-21-0-J00-00001 6212 1 200 000.00 03/07/2026 04/07/2026 14/07/2026",
                "Fait a Antananarivo le 14 avril 2026",
                "Page 2 sur 2" };

        byte[] pdf = pdfMultiPages(page1, page2);
        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                // Les DEUX marchés (page 1 et page 2) sont lus.
                .andExpect(jsonPath("$.marches", hasSize(2)))
                .andExpect(jsonPath("$.marches[0].designationMarche").value("Travaux de fabrication et installation des etageres"))
                .andExpect(jsonPath("$.marches[0].natureLibelle").value("Travaux"))
                .andExpect(jsonPath("$.marches[1].designationMarche").value("Fournitures de bureau diverses"))
                .andExpect(jsonPath("$.marches[1].natureLibelle").value("Fournitures"))
                .andExpect(jsonPath("$.marches[1].modeLibelle").value("Appel d'Offres"))
                .andExpect(jsonPath("$.marches[1].previsions[0].dateDebut").value("2026-07-03"));
    }

    @Test
    @DisplayName("Import PPM — format MIDSP : en-tête multi-lignes ignoré, NATURE MAJ (dont 2 lignes), mode multi-lignes, multi-bénéficiaires, nouvMontEstim, PIP")
    void importPpm_formatMidsp_ok() throws Exception {
        natureRepository.save(new Nature(1, "FOURNITURES", null));
        natureRepository.save(new Nature(2, "TRAVAUX", null));
        natureRepository.save(new Nature(3, "PRESTATIONS DE SERVICE", null));

        // Page 1 : en-tête doc + en-tête de colonnes ÉCLATÉ sur plusieurs lignes + 2 marchés.
        String[] page1 = {
                "PPM_26-488-0078 page 1/2 18/06/2026 05:55",
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE DE L'INDUSTRIALISATION ET DU DEVELOPPEMENT DU",
                "SECTEUR PRIVE",
                "Nom de la PRMP: RAHERIVELO Fanja - MIDSP",
                "Date d'etablissement du Document initial: 03/02/2026",
                "NATURE OBJET", "MONTANT", "ESTIMATIF", "INITIAL", "NOUVEAU", "MONTANT", "ESTIMATIF",
                "MODE DE PASSATION FINAN-", "CEMENT", "Informations sur le Beneficiaire DATE",
                "SERVICE", "BENEFICIAIRE COMPTE",
                // Marché 1 : NATURE MAJ seule, OBJET multi-lignes, 2 bénéficiaires (anc + nouv), nouvMontEstim.
                "FOURNITURES",
                "Fourniture de cartes recharges",
                "telephoniques pour le Ministere",
                "645 442 000.00 645 442 000.00 ACHAT DIRECT RPI",
                "00-34-0-A00-00000",
                "00-34-0-B00-00000",
                "6263",
                "35 000 000.00",
                "25 000 000.00",
                "35 000 000.00",
                "25 000 000.00",
                "22/06/2026 29/06/2026 30/06/2026",
                // Marché 2 : mode sur 2 lignes (CONSULTATION DE / PRIX OUVERTE), 1 bénéficiaire (anc + nouv).
                "TRAVAUX",
                "Travaux d'entretien de batiments",
                "53 200 000.00 107 000 000.00 CONSULTATION DE",
                "PRIX OUVERTE",
                "RPI 00-34-0-B10-00000 6211 53 200 000.00 107 000 000.00 29/06/2026 10/07/2026 22/07/2026",
                "Fait a Antananarivo le _ _/_ _/_ _ _ _" };
        // Page 2 : NATURE sur 2 lignes, financement PIP, 1 bénéficiaire SANS nouveau montant, pas de nouvMontEstim.
        String[] page2 = {
                "PPM_26-488-0078 page 2/2 18/06/2026 05:55",
                "PRESTATIONS DE",
                "SERVICE",
                "Frais de colloque pour le Ministere",
                "20 000 000.00 CONSULTATION DE PRIX OUVERTE PIP 00-34-0-D00-00000 6225 20 000 000.00 22/06/2026 02/07/2026 13/07/2026",
                "Fait a Antananarivo le 03 fevrier 2026",
                "LA PERSONNE RESPONSABLE DES MARCHES PUBLICS" };

        byte[] pdf = pdfMultiPages(page1, page2);
        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                // Autorité contractante recomposée sur 2 lignes.
                .andExpect(jsonPath("$.autoriteContractante").value("MINISTERE DE L'INDUSTRIALISATION ET DU DEVELOPPEMENT DU SECTEUR PRIVE"))
                .andExpect(jsonPath("$.marches", hasSize(3)))
                // Marché 1 : nature MAJ, nouvMontEstim capté, 2 bénéficiaires alignés (anc + nouv), compte partagé.
                .andExpect(jsonPath("$.marches[0].natureLibelle").value("FOURNITURES"))
                .andExpect(jsonPath("$.marches[0].idNature").value(1))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("ACHAT DIRECT"))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(645442000.00))
                .andExpect(jsonPath("$.marches[0].nouvMontEstim").value(645442000.00))
                .andExpect(jsonPath("$.marches[0].designationMarche").value("Fourniture de cartes recharges telephoniques pour le Ministere"))
                .andExpect(jsonPath("$.marches[0].beneficiaires", hasSize(2)))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-34-0-A00-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("6263"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(35000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].nouvMontBenef").value(35000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[1].soaCode").value("00-34-0-B00-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[1].ancMontBenef").value(25000000.00))
                // Marché 2 : mode recomposé sur 2 lignes + nouvMontEstim.
                .andExpect(jsonPath("$.marches[1].natureLibelle").value("TRAVAUX"))
                .andExpect(jsonPath("$.marches[1].modeLibelle").value("CONSULTATION DE PRIX OUVERTE"))
                .andExpect(jsonPath("$.marches[1].nouvMontEstim").value(107000000.00))
                .andExpect(jsonPath("$.marches[1].beneficiaires", hasSize(1)))
                // Marché 3 : NATURE sur 2 lignes, financement PIP, 1 bénéficiaire SANS nouveau montant.
                .andExpect(jsonPath("$.marches[2].natureLibelle").value("PRESTATIONS DE SERVICE"))
                .andExpect(jsonPath("$.marches[2].idNature").value(3))
                .andExpect(jsonPath("$.marches[2].financement").value("PIP"))
                .andExpect(jsonPath("$.marches[2].nouvMontEstim").value(nullValue()))
                .andExpect(jsonPath("$.marches[2].beneficiaires", hasSize(1)))
                .andExpect(jsonPath("$.marches[2].beneficiaires[0].ancMontBenef").value(20000000.00))
                .andExpect(jsonPath("$.marches[2].beneficiaires[0].nouvMontBenef").value(nullValue()));
    }

    @Test
    @DisplayName("nomPrmp élargi : 60 car. accepté à la création PRMP (était 400 à 50) ; >100 → 400")
    void prmp_nomLong_accepte() throws Exception {
        String nom60 = "R".repeat(60);
        String reste = "\"prenomsPrmp\":\"Jean\",\"arreteNomin\":\"ARR-1\",\"dateNomin\":\"2024-01-15\","
                + "\"cin\":\"101011112222\",\"dateCin\":\"2010-05-05\",\"lieuCin\":\"Antananarivo\","
                + "\"emailPrmp\":\"a@b.mg\",\"telPrmp\":\"0330000001\"}";
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMNOM1\",\"nomPrmp\":\"" + nom60 + "\"," + reste))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomPrmp").value(nom60));

        // Au-delà de 100 → 400 (borne).
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMNOM2\",\"nomPrmp\":\"" + "R".repeat(101) + "\"," + reste))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/controleurs/{id} : contrôleur sans activité → 204 (+ compte/sessions/indicateurs nettoyés) ; avec activité → 409 ; inconnu → 404")
    void controleur_delete_gardeEtNettoyage() throws Exception {
        // Contrôleur « propre » (aucune participation métier) + compte + une session + un indicateur.
        controleurRepository.save(controleur("CTRDEL", 6, "ANT"));
        compteAuthRepository.save(new cnm.prs.entity.CompteAuth("ctrdel", "x",
                cnm.prs.enums.TypeActeur.CONTROLEUR.name(), "CTRDEL", true));
        cnm.prs.entity.SessionUtilisateur s = new cnm.prs.entity.SessionUtilisateur();
        s.setIdSession("SESS-CTRDEL");
        s.setImControleur("CTRDEL");
        sessionUtilisateurRepository.save(s);
        cnm.prs.entity.IndicateurCtrl ic = new cnm.prs.entity.IndicateurCtrl();
        ic.setIdIndicateur(990001);
        ic.setImControleur("CTRDEL");
        ic.setPeriode("2026-06");
        indicateurCtrlRepository.save(ic);

        mvc.perform(delete("/api/controleurs/CTRDEL").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        // Contrôleur + compte + données dérivées supprimés.
        org.junit.jupiter.api.Assertions.assertFalse(controleurRepository.existsById("CTRDEL"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ctrdel").isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(sessionUtilisateurRepository.existsById("SESS-CTRDEL"));
        org.junit.jupiter.api.Assertions.assertFalse(indicateurCtrlRepository.existsById(990001));

        // CTRMEM est membre de l'examen 1 (seed) → activité métier → 409, le contrôleur subsiste.
        mvc.perform(delete("/api/controleurs/CTRMEM").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertTrue(controleurRepository.existsById("CTRMEM"));

        // Inconnu → 404.
        mvc.perform(delete("/api/controleurs/INCONNU").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/controleurs multipart : fiche + photo (opt.), GET ; type≠PHOTO/PDF/>5Mo → 400 ; JSON conservé ; DELETE purge la photo ; non-admin → 403")
    void controleur_photo() throws Exception {
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };
        byte[] pdf = "%PDF-1.4 pas une image".getBytes(StandardCharsets.US_ASCII);
        byte[] data = "{\"imControleur\":\"CTRPHO\",\"idProfile\":6,\"transversal\":false,\"nomCont\":\"Photo\"}"
                .getBytes(StandardCharsets.UTF_8);

        // --- Écritures réussies d'abord. ---
        // Création multipart : data + photo (JPEG).
        mvc.perform(multipart("/api/controleurs").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imControleur").value("CTRPHO"));
        // Téléchargement de la photo stockée.
        mvc.perform(get("/api/controleurs/CTRPHO/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
        // Dépôt ultérieur (remplace la photo par un PNG).
        mvc.perform(multipart("/api/controleurs/CTRPHO/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "p.png", "image/png", png)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/controleurs/CTRPHO/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG));
        // JSON pur (sans photo) → 201 (rétro-compat).
        mvc.perform(post("/api/controleurs").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imControleur\":\"CTRPHO2\",\"idProfile\":6,\"transversal\":false}"))
                .andExpect(status().isCreated());
        // DELETE purge la photo (t_piece_jointe, clé imControleur) — pas d'orphelin.
        mvc.perform(delete("/api/controleurs/CTRPHO").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(pieceJointeRepository.findByLogin("CTRPHO").isEmpty());

        // --- Cas d'erreur ensuite (CTRPHO2 existe, sans photo). ---
        // type ≠ PHOTO → 400 (le contrôleur n'a pas d'autre pièce).
        mvc.perform(multipart("/api/controleurs/CTRPHO2/pieces/CIN").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "c.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isBadRequest());
        // Photo = image seulement : un PDF → 400.
        mvc.perform(multipart("/api/controleurs/CTRPHO2/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "p.pdf", "application/pdf", pdf)))
                .andExpect(status().isBadRequest());
        // Contrôleur inconnu → 404.
        mvc.perform(multipart("/api/controleurs/INCONNU/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "p.png", "image/png", png)))
                .andExpect(status().isNotFound());
        // Photo > 5 Mo → 400 (contrôle de taille).
        byte[] gros = new byte[6 * 1024 * 1024];
        gros[0] = (byte) 0xFF; gros[1] = (byte) 0xD8; gros[2] = (byte) 0xFF;   // JPEG magic
        byte[] data3 = "{\"imControleur\":\"CTRPHO3\",\"idProfile\":6,\"transversal\":false}"
                .getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/controleurs").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data3))
                .file(new MockMultipartFile("photo", "big.jpg", "image/jpeg", gros)))
                .andExpect(status().isBadRequest());
        // ⚠️ Lecture ouverte aux authentifiés (photo affichée dans l'UI) — dépôt/suppression restent Admin.
        mvc.perform(multipart("/api/controleurs/CTRPHO2/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "p.png", "image/png", png)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/controleurs/CTRPHO2/pieces/PHOTO").header("Authorization", tokenMembre))
                .andExpect(status().isOk());   // lecture autorisée à tout authentifié
        mvc.perform(multipart("/api/controleurs/CTRPHO2/pieces/PHOTO").header("Authorization", tokenMembre)
                .file(new MockMultipartFile("fichier", "p.png", "image/png", png)))
                .andExpect(status().isForbidden());   // dépôt : Admin uniquement
    }

    @Test
    @DisplayName("PUT /api/controleurs/{id} multipart : maj fiche + remplace photo ; photo absente inchangée ; JSON conservé ; inconnu → 404 ; PDF → 400")
    void controleur_modificationAvecPhoto() throws Exception {
        controleurRepository.save(controleur("CTRPUT", 6, "ANT"));
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] pdf = "%PDF-1.4 pas une image".getBytes(StandardCharsets.US_ASCII);
        byte[] data = "{\"imControleur\":\"CTRPUT\",\"idProfile\":6,\"transversal\":false,\"nomCont\":\"Apres\"}"
                .getBytes(StandardCharsets.UTF_8);

        // --- Écritures réussies d'abord. ---
        // PUT multipart : maj fiche + dépose la photo (JPEG). MockMvc : builder POST forcé en PUT.
        mvc.perform(multipart("/api/controleurs/CTRPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", jpeg))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomCont").value("Apres"));
        mvc.perform(get("/api/controleurs/CTRPUT/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));

        // PUT multipart SANS photo : fiche mise à jour, photo laissée inchangée.
        mvc.perform(multipart("/api/controleurs/CTRPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isOk());
        mvc.perform(get("/api/controleurs/CTRPUT/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));   // inchangée

        // PUT JSON pur (sans photo) → 200 (rétro-compat).
        mvc.perform(put("/api/controleurs/CTRPUT").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(new String(data, StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        // --- Cas d'erreur ensuite. ---
        // Contrôleur inconnu → 404.
        mvc.perform(multipart("/api/controleurs/INCONNU").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json",
                        "{\"imControleur\":\"INCONNU\",\"transversal\":false}".getBytes(StandardCharsets.UTF_8)))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isNotFound());

        // Photo = image seulement : un PDF → 400.
        mvc.perform(multipart("/api/controleurs/CTRPUT").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("photo", "p.pdf", "application/pdf", pdf))
                .with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/controleurs/{id}/pieces/PHOTO : supprime la photo (contrôleur conservé) ; absente/inconnu → 404 ; type≠PHOTO → 400 ; non-admin → 403")
    void controleur_suppressionPhoto() throws Exception {
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] data = "{\"imControleur\":\"CTRDPH\",\"idProfile\":6,\"transversal\":false}"
                .getBytes(StandardCharsets.UTF_8);

        // --- Écritures / lectures 200 d'abord. ---
        // Création avec photo.
        mvc.perform(multipart("/api/controleurs").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated());
        // Suppression de la photo → 204.
        mvc.perform(delete("/api/controleurs/CTRDPH/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        // La photo est partie de la base ; le contrôleur, lui, subsiste.
        org.junit.jupiter.api.Assertions.assertTrue(pieceJointeRepository.findByLogin("CTRDPH").isEmpty());
        mvc.perform(get("/api/controleurs/CTRDPH").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());

        // --- Cas d'erreur ensuite. ---
        // Photo déjà absente → 404.
        mvc.perform(delete("/api/controleurs/CTRDPH/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // type ≠ PHOTO → 400.
        mvc.perform(delete("/api/controleurs/CTRDPH/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
        // Contrôleur inconnu → 404.
        mvc.perform(delete("/api/controleurs/INCONNU/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(delete("/api/controleurs/CTRDPH/pieces/PHOTO").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/controleurs/par-localite/{idLocalite} : contrôleurs affectés ; transversal (localité nulle) exclu ; inconnue → vide")
    void controleur_parLocalite() throws Exception {
        // Seed : CTRCC2 en TMS ; CTRPRE a une localité NULLE (transversal).
        mvc.perform(get("/api/controleurs/par-localite/TMS").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].imControleur", hasItem("CTRCC2")))
                .andExpect(jsonPath("$[?(@.imControleur=='CTRPRE')]", hasSize(0)));   // localité nulle → exclu

        // Localité sans contrôleur → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/controleurs/par-localite/ZZ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/controleurs/par-profil/{idProfile} : contrôleurs d'un profil ; profil inconnu → vide")
    void controleur_parProfil() throws Exception {
        // Seed : profil 3 (Chef de commission) = CTRCC1 (ANT) + CTRCC2 (TMS).
        mvc.perform(get("/api/controleurs/par-profil/3").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].imControleur", containsInAnyOrder("CTRCC1", "CTRCC2")));

        // Profil inexistant → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/controleurs/par-profil/99").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/controleurs/par-superieur/{imSuperieur} : subordonnés directs ; supérieur sans subordonné → vide")
    void controleur_parSuperieur() throws Exception {
        // Un subordonné dont le supérieur hiérarchique est CTRCC1.
        Controleur sub = controleur("CTRSUB", 5, "ANT");
        sub.setIdSuperieur("CTRCC1");
        controleurRepository.save(sub);

        mvc.perform(get("/api/controleurs/par-superieur/CTRCC1").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].imControleur", hasItem("CTRSUB")));

        // Contrôleur sans subordonné → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/controleurs/par-superieur/CTRSUB").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/controleurs/par-nom/{nom} : recherche partielle insensible à la casse ; aucun résultat → vide")
    void controleur_parNom() throws Exception {
        Controleur c = controleur("CTRNOM", 5, "ANT");
        c.setNomCont("RASOANAIVO");
        controleurRepository.save(c);

        // Partiel interne « soana ».
        mvc.perform(get("/api/controleurs/par-nom/soana").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].imControleur", hasItem("CTRNOM")));
        // Insensible à la casse : « RASOA ».
        mvc.perform(get("/api/controleurs/par-nom/RASOA").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].imControleur", hasItem("CTRNOM")));
        // Aucun résultat → liste vide (pas de 404).
        mvc.perform(get("/api/controleurs/par-nom/ZZQQ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("POST /api/controleurs/suppression-lot : tolérant → bilan supprimes/introuvables/bloques ; vide → 400 ; non-admin → 403")
    void controleur_suppressionLot() throws Exception {
        // Contrôleur « propre » (aucune activité) + compte. CTRMEM (seed) est membre de l'examen 1 → bloqué.
        controleurRepository.save(controleur("CTRLOT", 6, "ANT"));
        compteAuthRepository.save(new cnm.prs.entity.CompteAuth("ctrlot", "x",
                cnm.prs.enums.TypeActeur.CONTROLEUR.name(), "CTRLOT", true));

        mvc.perform(post("/api/controleurs/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"matricules\":[\"CTRLOT\",\"CTRMEM\",\"INCONNU\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supprimes", containsInAnyOrder("CTRLOT")))
                .andExpect(jsonPath("$.bloques", containsInAnyOrder("CTRMEM")))
                .andExpect(jsonPath("$.introuvables", containsInAnyOrder("INCONNU")));
        org.junit.jupiter.api.Assertions.assertFalse(controleurRepository.existsById("CTRLOT"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("ctrlot").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(controleurRepository.existsById("CTRMEM"));   // bloqué, subsiste

        // Liste vide → 400.
        mvc.perform(post("/api/controleurs/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"matricules\":[]}"))
                .andExpect(status().isBadRequest());

        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(post("/api/controleurs/suppression-lot").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"matricules\":[\"CTRMEM\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/prmps/par-localite/{idLocalite} : PRMP via entités contractantes ACTIVES ; inactif exclu ; localité sans PRMP → vide")
    void prmp_parLocalite() throws Exception {
        // Seed : PRMP001 rattachée (ACTIVE) à l'entité 1 (ANT). On ajoute PRMPINA rattachée à ANT mais INACTIVE.
        prmpRepository.save(prmp("PRMPINA", "ANT"));
        entiteContractRepository.save(entite(951, 1, "ANT"));
        prmpEntiteRepository.save(prmpEntite(9510, "PRMPINA", 951, false));

        mvc.perform(get("/api/prmps/par-localite/ANT").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idPrmp", hasItem("PRMP001")))          // rattachement actif → présent
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMPINA')]", hasSize(0)));    // rattachement inactif → exclu

        // Localité sans aucune entité rattachée → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/prmps/par-localite/ZZ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/prmps/par-entite/{idEntiteContract} : PRMP via affectation ACTIVE (0 ou 1) ; inactive exclue ; entité sans PRMP → vide")
    void prmp_parEntite() throws Exception {
        // Seed : PRMP001 rattachée (ACTIVE) à l'entité 1. Affectation INACTIVE de PRMPINE à l'entité 952.
        mvc.perform(get("/api/prmps/par-entite/1").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idPrmp", containsInAnyOrder("PRMP001")));

        prmpRepository.save(prmp("PRMPINE", "ANT"));
        entiteContractRepository.save(entite(952, 1, "ANT"));
        prmpEntiteRepository.save(prmpEntite(9520, "PRMPINE", 952, false));   // inactive
        mvc.perform(get("/api/prmps/par-entite/952").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));   // affectation inactive → exclue

        // Entité sans affectation → vide.
        mvc.perform(get("/api/prmps/par-entite/888888").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/prmps/par-nom/{nom} : recherche partielle insensible à la casse ; aucun résultat → vide")
    void prmp_parNom() throws Exception {
        Prmp p = prmp("PRMPNOM", "ANT");
        p.setNomPrmp("RAKOTOARISOA");
        prmpRepository.save(p);

        // Partiel « AKOT » → trouve RAKOTOARISOA.
        mvc.perform(get("/api/prmps/par-nom/AKOT").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idPrmp", hasItem("PRMPNOM")));
        // Insensible à la casse : « rakoto ».
        mvc.perform(get("/api/prmps/par-nom/rakoto").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idPrmp", hasItem("PRMPNOM")));
        // Aucun résultat → liste vide (pas de 404).
        mvc.perform(get("/api/prmps/par-nom/ZZQQ").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /** Les quatre lectures de LISTE de la ressource — celles qui exposaient le répertoire entier. */
    private static final List<String> PRMP_LECTURES_LISTE = List.of(
            "/api/prmps", "/api/prmps/par-localite/ANT", "/api/prmps/par-entite/1", "/api/prmps/par-nom/Nom");

    /**
     * ⚠️ Durcissement (2026-08-24) — les cinq lectures de {@code /api/prmps} ne portaient
     * <strong>aucune</strong> garde et servaient la fiche <strong>complète</strong> : numéro de carte
     * d'identité, date et lieu de délivrance, à <em>tout</em> utilisateur authentifié, quel que soit
     * son profil et sa localité. C'est de la donnée personnelle, sans usage métier hors gestion des
     * comptes.
     *
     * <p>Ce test verrouille la vue réduite : il échouera dès qu'une des cinq routes se remettra à
     * servir le triptyque CIN à un contrôleur. Il vérifie dans le même mouvement que les champs dont
     * les écrans dépendent réellement continuent d'être servis — identité, matricule, arrêté de
     * nomination et sa date, courriel, téléphone : exactement ce qu'affiche l'onglet « Entité
     * contractante » du détail d'un plan de passation, seul écran de contrôleur à lire ce
     * répertoire. Sans cette seconde moitié, une fermeture trop large viderait l'écran au lieu de le
     * protéger, et rien ne le signalerait.</p>
     */
    @Test
    @DisplayName("GET /api/prmps (5 lectures) : un contrôleur reçoit la vue RÉDUITE — ni cin, ni dateCin, ni lieuCin")
    void prmpLectures_vueReduiteHorsAdministrateur() throws Exception {
        // Fiche unitaire : absence explicite des trois champs sensibles…
        mvc.perform(get("/api/prmps/PRMP001").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cin").doesNotExist())
                .andExpect(jsonPath("$.dateCin").doesNotExist())
                .andExpect(jsonPath("$.lieuCin").doesNotExist())
                // … et présence de tout ce que l'écran consomme réellement.
                .andExpect(jsonPath("$.idPrmp").value("PRMP001"))
                .andExpect(jsonPath("$.nomPrmp").value("Nom"))
                .andExpect(jsonPath("$.prenomsPrmp").value("Prenoms"))
                .andExpect(jsonPath("$.arreteNomin").value("ARR-001"))
                .andExpect(jsonPath("$.dateNomin").value("2024-01-15"))
                .andExpect(jsonPath("$.emailPrmp").value("prmp@min.mg"))
                .andExpect(jsonPath("$.telPrmp").value("0330000001"));

        // Les quatre lectures de liste : la ligne est bien servie, mais aucune ne porte de CIN.
        for (String url : PRMP_LECTURES_LISTE) {
            mvc.perform(get(url).header("Authorization", tokenCc))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].idPrmp", hasItem("PRMP001")))
                    .andExpect(jsonPath("$[*].nomPrmp", hasItem("Nom")))
                    .andExpect(jsonPath("$[*].cin", everyItem(nullValue())))
                    .andExpect(jsonPath("$[*].dateCin", everyItem(nullValue())))
                    .andExpect(jsonPath("$[*].lieuCin", everyItem(nullValue())));
        }
    }

    /**
     * Revers du test précédent : la vue réduite ne doit pas devenir la <em>seule</em> vue.
     * L'Administrateur gère les comptes — état civil, rapprochement avec la pièce d'identité déposée
     * — et l'écran d'administration des PRMP édite les dix champs de {@code PrmpDto} puis les renvoie
     * en bloc par {@code PUT}. S'il lisait la vue réduite, le premier enregistrement écraserait
     * {@code cin}/{@code dateCin}/{@code lieuCin} avec des {@code null} : une fermeture de sécurité
     * qui détruit des données. Ce test l'interdit sur les cinq lectures.
     */
    @Test
    @DisplayName("GET /api/prmps (5 lectures) : l'Administrateur reçoit la fiche COMPLÈTE — cin, dateCin, lieuCin servis")
    void prmpLectures_ficheCompletePourAdministrateur() throws Exception {
        mvc.perform(get("/api/prmps/PRMP001").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cin").value("101011112222"))
                .andExpect(jsonPath("$.dateCin").value("2010-05-05"))
                .andExpect(jsonPath("$.lieuCin").value("Antananarivo"));

        for (String url : PRMP_LECTURES_LISTE) {
            mvc.perform(get(url).header("Authorization", tokenAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')].cin", hasItem("101011112222")))
                    .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')].dateCin", hasItem("2010-05-05")))
                    .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')].lieuCin", hasItem("Antananarivo")));
        }
    }

    /**
     * Garde-fou de la séparation : réduire la vue « pour tout le monde sauf l'Administrateur » aurait
     * privé la PRMP de sa <strong>propre</strong> fiche — elle est la première concernée par les
     * données qu'elle a elle-même déclarées à l'inscription, et c'est un droit d'accès, pas une
     * faveur. L'arbitrage se fait donc <strong>ligne à ligne</strong> et non par route : la PRMP
     * retrouve sa fiche complète y compris au milieu d'une liste, et reste en vue réduite sur celle
     * d'une consœur. Ce test échouera si quelqu'un remonte l'arbitrage au niveau de la route (la
     * PRMP perdrait sa fiche, ou les verrait toutes en entier).
     *
     * <p>Il ferme aussi le contournement le plus tentant : l'<strong>UGPM</strong>, dont le claim
     * {@code ref} porte l'identifiant de sa PRMP de <em>tutelle</em> ({@code Visibilite#estPrmp} les
     * traite comme un même périmètre). Partager un périmètre d'instruction n'est pas partager une
     * pièce d'identité — l'UGPM reste en vue réduite sur la fiche de sa tutelle.</p>
     */
    @Test
    @DisplayName("GET /api/prmps : une PRMP lit sa PROPRE fiche complète ; celle d'une autre, et l'UGPM de tutelle, restent réduites")
    void prmpLectures_prmpLitSaPropreFicheComplete() throws Exception {
        prmpRepository.save(prmp("PRMPAUT", "ANT"));
        // Claim ref = PRMP001 : l'UGPM porte l'identifiant de sa PRMP de tutelle, pas le sien.
        String tokenUgpm = bearer("UGPM1", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);

        // Sa propre fiche (claim ref = PRMP001) : complète.
        mvc.perform(get("/api/prmps/PRMP001").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cin").value("101011112222"))
                .andExpect(jsonPath("$.dateCin").value("2010-05-05"))
                .andExpect(jsonPath("$.lieuCin").value("Antananarivo"));

        // Celle d'une autre PRMP : réduite, malgré le profil PRMP.
        mvc.perform(get("/api/prmps/PRMPAUT").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomPrmp").value("Nom"))
                .andExpect(jsonPath("$.cin").doesNotExist())
                .andExpect(jsonPath("$.dateCin").doesNotExist())
                .andExpect(jsonPath("$.lieuCin").doesNotExist());

        // L'UGPM sur la fiche de sa tutelle : réduite — le périmètre est partagé, pas la CIN.
        mvc.perform(get("/api/prmps/PRMP001").header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomPrmp").value("Nom"))
                .andExpect(jsonPath("$.cin").doesNotExist())
                .andExpect(jsonPath("$.dateCin").doesNotExist())
                .andExpect(jsonPath("$.lieuCin").doesNotExist());

        // Dans la liste : sa ligne complète, celle de l'autre réduite — l'arbitrage est bien par ligne.
        mvc.perform(get("/api/prmps").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')].cin", hasItem("101011112222")))
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMPAUT')].cin", everyItem(nullValue())));
    }

    /**
     * Les cinq lectures retombaient sur {@code anyRequest().authenticated()} : n'importe quel profil
     * y avait droit. Elles portent désormais la même liste de profils que
     * {@code GET /api/ugpms/par-tutelle/{idPrmp}} — les instructeurs du circuit, plus l'Administrateur
     * et les acteurs PRMP/UGPM. Le {@code CHARGE_PUBLICATION} n'instruit aucun dossier et son espace
     * (publications, documents publics, notifications) ne lit pas ce répertoire : il en est exclu.
     * Ce test échouera si la garde disparaît des routes — l'oubli exact qui a créé la faille.
     */
    @Test
    @DisplayName("GET /api/prmps (5 lectures) : profil hors circuit (chargé de publication) → 403")
    void prmpLectures_profilHorsCircuitRefuse() throws Exception {
        mvc.perform(get("/api/prmps/PRMP001").header("Authorization", tokenPublication))
                .andExpect(status().isForbidden());
        for (String url : PRMP_LECTURES_LISTE) {
            mvc.perform(get(url).header("Authorization", tokenPublication))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("POST /api/prmps multipart : fiche + pièces (optionnelles), GET pièce ; CIN > 5 Mo → 400 ; dépôt ultérieur ; non-admin → 403")
    void prmp_creationAvecPieces() throws Exception {
        byte[] dataJson = ("{\"idPrmp\":\"IMPCS\",\"nomPrmp\":\"Testy\",\"prenomsPrmp\":\"Piece\","
                + "\"arreteNomin\":\"ARR-1\",\"dateNomin\":\"2024-01-10\",\"cin\":\"301234567890\","
                + "\"dateCin\":\"2012-02-02\",\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"pc@cnm.mg\","
                + "\"telPrmp\":\"0331112233\"}").getBytes(StandardCharsets.UTF_8);
        byte[] pdf = "%PDF-1.4 arrete de nomination".getBytes(StandardCharsets.US_ASCII);   // %PDF (magic)
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };

        // Création multipart : data JSON + arrêté (PDF) + CIN (JPEG) ; photo omise (optionnelle).
        mvc.perform(multipart("/api/prmps").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", dataJson))
                .file(new MockMultipartFile("arrete", "arrete.pdf", "application/pdf", pdf))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPrmp").value("IMPCS"));

        // Téléchargement de l'arrêté stocké.
        mvc.perform(get("/api/prmps/IMPCS/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(get("/api/prmps/IMPCS/pieces/ARRETE_NOMIN").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());

        // Dépôt ultérieur d'une pièce (photo) sur une PRMP existante → puis téléchargeable.
        mvc.perform(multipart("/api/prmps/IMPCS/pieces/PHOTO").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("fichier", "photo.png", "image/png", png)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/prmps/IMPCS/pieces/PHOTO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());

        // CIN > 5 Mo → 400 (contrôle de taille au niveau service).
        byte[] gros = new byte[6 * 1024 * 1024];
        gros[0] = (byte) 0xFF; gros[1] = (byte) 0xD8; gros[2] = (byte) 0xFF;   // JPEG magic
        byte[] data2 = new String(dataJson, StandardCharsets.UTF_8).replace("IMPCS", "IMPCS2").getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/prmps").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data2))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", gros)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/prmps/{id}/pieces/{type} : supprime une pièce (PRMP conservée) ; autres pièces intactes ; absente/inconnu → 404 ; non-admin → 403")
    void prmp_suppressionPiece() throws Exception {
        byte[] data = ("{\"idPrmp\":\"IMPDP\",\"nomPrmp\":\"Testy\",\"prenomsPrmp\":\"Del\",\"arreteNomin\":\"ARR-1\","
                + "\"dateNomin\":\"2024-01-10\",\"cin\":\"301234567890\",\"dateCin\":\"2012-02-02\","
                + "\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"dp@cnm.mg\",\"telPrmp\":\"0331112233\"}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] pdf = "%PDF-1.4 arrete".getBytes(StandardCharsets.US_ASCII);
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };

        // --- Écritures / lectures 200 d'abord. ---
        // Création avec ARRETE_NOMIN (PDF) + CIN (JPEG).
        mvc.perform(multipart("/api/prmps").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("arrete", "arrete.pdf", "application/pdf", pdf))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated());
        // Suppression de l'arrêté → 204.
        mvc.perform(delete("/api/prmps/IMPDP/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        // La CIN subsiste, la PRMP aussi.
        mvc.perform(get("/api/prmps/IMPDP/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
        mvc.perform(get("/api/prmps/IMPDP").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
        // En base : il ne reste que la CIN sous la clé IMPDP.
        org.junit.jupiter.api.Assertions.assertEquals(1, pieceJointeRepository.findByLogin("IMPDP").size());

        // --- Cas d'erreur ensuite. ---
        // Arrêté désormais absent → 404 (téléchargement et re-suppression).
        mvc.perform(get("/api/prmps/IMPDP/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/prmps/IMPDP/pieces/ARRETE_NOMIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // PRMP inconnue → 404.
        mvc.perform(delete("/api/prmps/INCONNU/pieces/CIN").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(delete("/api/prmps/IMPDP/pieces/CIN").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/prmps/{id} : purge aussi les pièces (t_piece_jointe) — pas d'orphelin")
    void prmp_deleteFiche_purgePieces() throws Exception {
        byte[] data = ("{\"idPrmp\":\"IMPPG\",\"nomPrmp\":\"Testy\",\"prenomsPrmp\":\"Purge\",\"arreteNomin\":\"ARR-1\","
                + "\"dateNomin\":\"2024-01-10\",\"cin\":\"301234567890\",\"dateCin\":\"2012-02-02\","
                + "\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"pg@cnm.mg\",\"telPrmp\":\"0331112233\"}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0 };
        mvc.perform(multipart("/api/prmps").header("Authorization", tokenAdmin)
                .file(new MockMultipartFile("data", "", "application/json", data))
                .file(new MockMultipartFile("cin", "cin.jpg", "image/jpeg", jpeg)))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(1, pieceJointeRepository.findByLogin("IMPPG").size());

        // DELETE de la fiche (aucune donnée liée) → 204 + pièces purgées.
        mvc.perform(delete("/api/prmps/IMPPG").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(pieceJointeRepository.findByLogin("IMPPG").isEmpty());
    }

    @Test
    @DisplayName("POST /api/prmps + credentials : compte PRMP actif + login immédiat ; sans → fiche seule ; login/idPrmp pris → 409 ; mdp manquant/<8 → 400")
    void prmp_creationAvecCompte() throws Exception {
        String base = "\"nomPrmp\":\"Testy\",\"prenomsPrmp\":\"Cpt\",\"arreteNomin\":\"ARR-1\",\"dateNomin\":\"2024-01-10\","
                + "\"cin\":\"301234567890\",\"dateCin\":\"2012-02-02\",\"lieuCin\":\"Antananarivo\","
                + "\"emailPrmp\":\"c@cnm.mg\",\"telPrmp\":\"0331112233\"";

        // Avec login + motDePasse → 201, fiche + compte PRMP actif.
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPCPT\"," + base + ",\"login\":\"imp.cpt\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPrmp").value("IMPCPT"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("imp.cpt").isPresent());
        // Connexion immédiate → rôle PRMP, ref = idPrmp.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"imp.cpt\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRMP"))
                .andExpect(jsonPath("$.ref").value("IMPCPT"));

        // Sans credentials → 201, fiche seule (aucun compte).
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPNC\"," + base + "}"))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertTrue(
                compteAuthRepository.findByRefActeurAndTypeActeur("IMPNC", "PRMP").isEmpty());

        // login déjà pris → 409 ; idPrmp déjà pris → 409.
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPX\"," + base + ",\"login\":\"imp.cpt\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPCPT\"," + base + "}"))
                .andExpect(status().isConflict());

        // login sans motDePasse → 400 ; motDePasse < 8 → 400.
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPY\"," + base + ",\"login\":\"imp.y\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/prmps").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"IMPZ\"," + base + ",\"login\":\"imp.z\",\"motDePasse\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/prmps/{id} : PRMP sans données → 204 (+ compte supprimé) ; avec dossier → 409 ; inconnue → 404")
    void prmp_delete_gardeEtCompte() throws Exception {
        // PRMP « propre » (aucune donnée liée) + son compte d'authentification.
        prmpRepository.save(prmp("PRMPDEL", "ANT"));
        compteAuthRepository.save(new cnm.prs.entity.CompteAuth("prmpdel", "x",
                cnm.prs.enums.TypeActeur.PRMP.name(), "PRMPDEL", true));

        mvc.perform(delete("/api/prmps/PRMPDEL").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(prmpRepository.existsById("PRMPDEL"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("prmpdel").isEmpty());

        // PRMP avec un dossier lié → 409 (garde), la PRMP subsiste.
        prmpRepository.save(prmp("PRMPDEL2", "ANT"));
        Dossier d = dossier(970, "BROUILLON");
        d.setIdPrmp("PRMPDEL2");
        dossierRepository.save(d);
        mvc.perform(delete("/api/prmps/PRMPDEL2").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertTrue(prmpRepository.existsById("PRMPDEL2"));

        // Inconnue → 404.
        mvc.perform(delete("/api/prmps/INCONNU").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/prmps/suppression-lot : tolérant → bilan supprimes/introuvables/bloques ; vide → 400 ; non-admin → 403")
    void prmp_suppressionLot() throws Exception {
        // PRMP propre + compte ; PRMP avec un dossier lié (bloquée).
        prmpRepository.save(prmp("PRMPLOT1", "ANT"));
        compteAuthRepository.save(new cnm.prs.entity.CompteAuth("prmplot1", "x",
                cnm.prs.enums.TypeActeur.PRMP.name(), "PRMPLOT1", true));
        prmpRepository.save(prmp("PRMPLOT2", "ANT"));
        Dossier d = dossier(971, "BROUILLON");
        d.setIdPrmp("PRMPLOT2");
        dossierRepository.save(d);

        // Lot tolérant : 1 propre → supprimée, 1 à données liées → bloquée, 1 absente → introuvable.
        mvc.perform(post("/api/prmps/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"matricules\":[\"PRMPLOT1\",\"PRMPLOT2\",\"INCONNU\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supprimes", containsInAnyOrder("PRMPLOT1")))
                .andExpect(jsonPath("$.bloques", containsInAnyOrder("PRMPLOT2")))
                .andExpect(jsonPath("$.introuvables", containsInAnyOrder("INCONNU")));
        org.junit.jupiter.api.Assertions.assertFalse(prmpRepository.existsById("PRMPLOT1"));
        org.junit.jupiter.api.Assertions.assertTrue(compteAuthRepository.findByLogin("prmplot1").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(prmpRepository.existsById("PRMPLOT2"));   // bloquée, subsiste

        // Liste vide → 400.
        mvc.perform(post("/api/prmps/suppression-lot").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"matricules\":[]}"))
                .andExpect(status().isBadRequest());

        // Non-admin → 403 (le sous-chemin est sécurisé par @PreAuthorize).
        mvc.perform(post("/api/prmps/suppression-lot").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"matricules\":[\"PRMPLOT2\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Champs élargis : libelleEntite jusqu'à 150 accepté (intitulé de ministère long, 69 car.) ; >150 → 400")
    void entite_libelleLong_accepte() throws Exception {
        String ministere = "MINISTERE DE L'INDUSTRIALISATION ET DU DEVELOPPEMENT DU SECTEUR PRIVE"; // 68 car.
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":950,\"libelleEntite\":\"" + ministere
                        + "\",\"adresse\":\"Anosy\",\"idOrganigramme\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelleEntite").value(ministere));

        // Au-delà de 150 → 400 (borne).
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":951,\"libelleEntite\":\"" + "X".repeat(151)
                        + "\",\"adresse\":\"Anosy\",\"idOrganigramme\":1}"))
                .andExpect(status().isBadRequest());
    }

    /** PDF multi-pages (PDFBox) : une page physique par tableau de lignes — pour tester le parsing multi-pages. */
    private byte[] pdfMultiPages(String[]... pages) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            for (String[] lignes : pages) {
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
                doc.addPage(page);
                try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                        new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 11);
                    cs.setLeading(16f);
                    cs.newLineAtOffset(50, 750);
                    for (String l : lignes) {
                        cs.showText(l);
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Génère en mémoire un PDF (PDFBox) contenant les lignes de texte fournies — pour tester le parsing d'import. */
    private byte[] pdfAvecTexte(String... lignes) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                    new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 11);
                cs.setLeading(16f);
                cs.newLineAtOffset(50, 750);
                for (String l : lignes) {
                    cs.showText(l);
                    cs.newLine();
                }
                cs.endText();
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Rend l'examen 1 (dossier 1, ppm 1) éligible (1 ligne de marché en AOO) puis crée + signe un PV FAVR. */
    private int signerPvEligible() throws Exception {
        modePassationRepository.save(new ModePassation(1, "AOO", null, null, null, null));
        cnm.prs.entity.Marche m = marche(9500, 1, 1);   // dossier 1, ppm 1
        m.setIdMode(1);                                  // appel d'offres ouvert
        marcheRepository.save(m);
        return signerPvAvecAvis("FAVR");                 // → SIGNE → génération du document si éligible
    }

    @Test
    @DisplayName("Signature PV éligible → réponse immédiate (SIGNE) ; le document est produit APRÈS COMMIT : chemin NULL + documentDisponible=false dans la fenêtre")
    void signature_pv_genere_document_ok() throws Exception {
        int idPv = signerPvEligible();
        cnm.prs.entity.PvExamen pv = pvExamenRepository.findById(idPv).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("SIGNE", pv.getStatutPv());
        // ⚠️ 2026-08-19 — la génération (Word, plusieurs secondes) est sortie du chemin de la signature :
        // elle part APRÈS COMMIT (PvDocumentTache). Dans la transaction de test (jamais commitée),
        // l'événement ne part pas — le chemin reste NULL, exactement comme pendant la fenêtre de
        // génération en prod, et documentDisponible est false (contrat front : « fichier prêt maintenant »).
        org.junit.jupiter.api.Assertions.assertNull(pv.getCheminDocument(),
                "la signature ne produit plus le document dans sa transaction");
        mvc.perform(get("/api/pv-examens/" + idPv).header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.documentDisponible").value(false));
    }

    @Test
    @DisplayName("Téléchargement PV après signature → 200 application/pdf")
    void document_pv_telechargement_ok() throws Exception {
        int idPv = signerPvEligible();
        var resp = mvc.perform(get("/api/pv-examens/" + idPv + "/document").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(MediaType.APPLICATION_PDF_VALUE, resp.getContentType());
        assertTrue(resp.getContentAsByteArray().length > 0, "le PDF n'est pas vide");
    }

    @Test
    @DisplayName("PV signé sans document (ancien) → régénération paresseuse au téléchargement → 200")
    void migration_pv_anciens_sans_document() throws Exception {
        int idPv = signerPvEligible();
        cnm.prs.entity.PvExamen pv = pvExamenRepository.findById(idPv).orElseThrow();
        pv.setCheminDocument(null);            // simule un PV signé avant le correctif (chemin_document NULL)
        pvExamenRepository.save(pv);
        mvc.perform(get("/api/pv-examens/" + idPv + "/document").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertNotNull(
                pvExamenRepository.findById(idPv).orElseThrow().getCheminDocument(),
                "chemin_document régénéré à la demande");
    }

    @Test
    @DisplayName("Grille de contrôle — point « Conformité au budget » non conforme → observations chargées (>= 1)")
    void pv_detail_observations_chargees() throws Exception {
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(1);
        pc.setLibelPointCtrl("Conformité au budget");
        pc.setObligatoire(true);
        pc.setIdTypeDossier("DDP");
        pointsCtrlRepository.save(pc);
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":530,\"idExamen\":1,\"idPtControle\":1,\"conforme\":false,"
                        + "\"observations\":[{\"auLieuDe\":\"250 000 000\",\"lire\":\"200 000 000\",\"ordre\":1}]}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/examen-details/530").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conforme").value(false))
                .andExpect(jsonPath("$.observations.length()").value(1));
    }

    @Test
    @DisplayName("PV définitifs — nomSecretaireSeance peuplé dans la liste (pas seulement le détail)")
    void pv_definitifs_nom_secretaire_peuple() throws Exception {
        cnm.prs.entity.PvExamen pv = new cnm.prs.entity.PvExamen();
        pv.setIdPv(120);
        pv.setIdExamen(1);
        pv.setIdAvis("FAVR");
        pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("SIGNE");
        pv.setNbNavettes(0);
        pv.setIdSecretaireSeance("CTRVER");
        pvExamenRepository.save(pv);
        mvc.perform(get("/api/pv-examens/definitifs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==120)].nomSecretaireSeance", hasItem("Prenoms NomCTRVER")));
    }

    @Test
    @DisplayName("Lettre de renvoi — création à la clôture de navette (CC/Président, objetLettre ignoré) → 201 BROUILLON")
    void lettre_creation_pendant_examen_ok() throws Exception {
        // objetLettre encore envoyé par un ancien frontend : ignoré (compat rétroactive), pas d'erreur.
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1,\"objetLettre\":\"Renvoi du dossier\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idExamen").value(1))
                .andExpect(jsonPath("$.idDossier").value(1))
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.objetLettre").doesNotExist());
    }

    @Test
    @DisplayName("Lettre de renvoi — création sans objetLettre → 201 (objet désormais fixe)")
    void lettre_creation_sans_objet_ok() throws Exception {
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idExamen").value(1))
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.objetLettre").doesNotExist());
    }

    @Test
    @DisplayName("Lettre de renvoi — le DTO ne contient plus objetLettre")
    void lettre_dto_sans_objet() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLettre").value(id))
                .andExpect(jsonPath("$.objetLettre").doesNotExist());
    }

    @Test
    @DisplayName("Lettre de renvoi — refLettre au format {seqLettreGlobal}/{type}/{code_localite}/LR/{annee}")
    void lettre_ref_format_ok() throws Exception {
        // refeDossier structuré → refLettre reprend type/localité/année mais avec le compteur GLOBAL des
        // lettres (00001 pour la 1ère), pas le numéro du dossier (00007).
        Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setRefeDossier("00007/DDP/CRM-ANT/2026");
        dossierRepository.save(d);
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1,\"objetLettre\":\"Renvoi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refLettre").value("00001/DDP/CRM-ANT/LR/2026"));
    }

    @Test
    @DisplayName("Lettre de renvoi — détail d'une lettre SIGNE → nomSignataire (prénoms nom) non vide")
    void lettre_detail_signataire_ok() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imSignataire").value("CTRCC1"))
                .andExpect(jsonPath("$.nomSignataire").value("Prenoms NomCTRCC1"));
    }

    @Test
    @DisplayName("Assistant contrôleur — login ASSANT1/Test@1234 → 200, role ASSISTANT_CONTROLEUR")
    void assistant_login_ok() throws Exception {
        controleurRepository.save(controleur("ASSANT1", 9, "ANT"));
        compteAuthRepository.save(new CompteAuth("ASSANT1",
                passwordEncoder.encode("Test@1234"), "CONTROLEUR", "ASSANT1", true));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"ASSANT1\",\"motDePasse\":\"Test@1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ASSISTANT_CONTROLEUR"));
    }

    @Test
    @DisplayName("Assistant contrôleur — accès GET /api/lettre-renvois → 200")
    void assistant_acces_lettre_renvoi_ok() throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        mvc.perform(get("/api/lettre-renvois").header("Authorization", tokenAss))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Assistant contrôleur — accès GET /api/pv-examens → 200")
    void assistant_acces_pv_ok() throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        mvc.perform(get("/api/pv-examens").header("Authorization", tokenAss))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Lettre de renvoi — N lettres sur le même examen → 201 chacune")
    void lettre_multiple_meme_examen_ok() throws Exception {
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1,\"objetLettre\":\"Lettre 1\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1,\"objetLettre\":\"Lettre 2\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Lettre signée → PRMP notifiée (LETTRE_RENVOI_RECUE)")
    void lettre_signee_prmp_notifiee() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='LETTRE_RENVOI_RECUE')]", hasSize(1)));
    }

    @Test
    @DisplayName("Lettre signée → Assistant contrôleur notifié (LETTRE_RENVOI_COPIE)")
    void lettre_signee_assistant_notifie() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='LETTRE_RENVOI_COPIE')].destinataireIm", hasItem("CTRASS")));
    }

    @Test
    @DisplayName("PV signé avis DÉFAVORABLE (⚠️ 2026-08-02) → l'Assistant est notifié PV_A_ARCHIVER à la transmission SIGMP")
    void pv_signe_avis_defav_assistant_notifie() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis("DEF");   // dossier 1 → EN_VERIFICATION
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sens").value("NON_APPROUVE"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_ARCHIVER')].destinataireIm", hasItem("CTRASS")));
    }

    @Test
    @DisplayName("PV signé avis FAVR → Assistant NON notifié à la signature (PV_A_ARCHIVER n'arrive qu'après SIGMP)")
    void pv_signe_avis_favr_assistant_non_notifie() throws Exception {
        signerPvAvecAvis("FAVR");
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_ARCHIVER')]", hasSize(0)));
    }

    @Test
    @DisplayName("Clôture (FAVR, ⚠️ 2026-08-02) : levée + SIGMP + archivage Assistant → dossier CLOTURE")
    void dossier_cloture_assistant_notifie() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        int idPv = signerPvAvecAvis("FAVR");   // dossier 1 → EN_VERIFICATION
        // ⚠️ Décision produit 2026-08-15 : premier passage = rappel (MAINTENUE), puis la PRMP rectifie
        // et resoumet — la levée n'est possible qu'ensuite.
        passageObservationDossier1(tokenVer, "MAINTENUE", "a rectifier"); // → EN_ATTENTE_DECISION_PRMP
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk());                              // → EN_VERIFICATION
        passageObservationDossier1(tokenVer, "LEVEE", null);   // → OBSERVATIONS_LEVEES
        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sens").value("APPROUVE"))
                .andExpect(jsonPath("$.leveeObservations").value(true));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_ARCHIVER')].destinataireIm", hasItem("CTRASS")));
        mvc.perform(post("/api/pv-examens/" + idPv + "/archiver").header("Authorization", tokenAss))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));
    }

    @Test
    @DisplayName("Assistant contrôleur hors localité → accès lettre 403")
    void assistant_acces_lettre_autre_localite_403() throws Exception {
        int id = seedLettreSoumise();   // examen 1 → localité ANT
        String tokenAssTms = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "TMS");
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenAssTms))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PRMP — GET /api/lettre-renvois/mes-lettres (lecture seule) → 200")
    void prmp_mes_lettres_lecture_seule() throws Exception {
        mvc.perform(get("/api/lettre-renvois/mes-lettres").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Lettre de renvoi — un Membre tente de signer → 403")
    void lettre_signer_membre_403() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lettre de renvoi — le CC signe → SIGNE")
    void lettre_signer_cc_ok() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SIGNE"))
                .andExpect(jsonPath("$.imSignataire").value("CTRCC1"));
    }

    @Test
    @DisplayName("Lettre de renvoi — le Président signe → SIGNE")
    void lettre_signer_president_ok() throws Exception {
        int id = seedLettreSoumise();
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SIGNE"))
                .andExpect(jsonPath("$.imSignataire").value("CTRPRE"));
    }

    @Test
    @DisplayName("Signature lettre (centrale ANT) : le CC signe → 200")
    void signature_centrale_cc_ok() throws Exception {
        int id = seedLettreSoumiseLoc(710, "ANT");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SIGNE"));
    }

    @Test
    @DisplayName("Signature lettre (centrale ANT) : le Président signe → 200")
    void signature_centrale_president_ok() throws Exception {
        int id = seedLettreSoumiseLoc(711, "ANT");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SIGNE"));
    }

    @Test
    @DisplayName("Signature lettre (régionale TMS) : le CC signe → 200")
    void signature_regionale_cc_ok() throws Exception {
        int id = seedLettreSoumiseLoc(712, "TMS");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SIGNE"));
    }

    @Test
    @DisplayName("Signature lettre (régionale TMS) : le Président signe → 403")
    void signature_regionale_president_403() throws Exception {
        int id = seedLettreSoumiseLoc(713, "TMS");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenPresident))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Document : signature centrale → PDF téléchargeable (200, application/pdf)")
    void document_genere_centrale_ok() throws Exception {
        byte[] pdf = signerEtPdf(714, "ANT", tokenCc);
        assertTrue(pdf.length > 0 && new String(pdf, 0, 4, StandardCharsets.ISO_8859_1).equals("%PDF"),
                "PDF généré (en-tête %PDF)");
    }

    @Test
    @DisplayName("Document : signature régionale → PDF téléchargeable (200, application/pdf)")
    void document_genere_regionale_ok() throws Exception {
        int id = seedLettreSoumiseLoc(715, "TMS");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Document : texte EXACT du modèle (pas une paraphrase)")
    void document_texte_identique_modele() throws Exception {
        String texte = texteDuPdf(signerEtPdf(740, "ANT", tokenCc));
        assertTrue(texte.contains("Commission Nationale des Marchés renvoie")
                && texte.contains("une séance ultérieure en demandant au service de"),
                "phrase exacte du modèle présente dans le PDF");
    }

    @Test
    @DisplayName("Document : le PDF contient l'image de l'emblème")
    void document_contient_image() throws Exception {
        assertTrue(contientImage(signerEtPdf(741, "ANT", tokenCc)), "le PDF contient au moins un objet image");
    }

    @Test
    @DisplayName("Document : signataire = nom réel seul (pas de texte parasite)")
    void document_signataire_sans_texte_parasite() throws Exception {
        String texte = texteDuPdf(signerEtPdf(742, "ANT", tokenCc));
        assertFalse(texte.contains("Le Président ou le Chef de Commission,"),
                "pas de libellé de rôle parasite codé en dur");
        assertTrue(texte.contains("NomCTRCC1"), "nom réel du signataire présent");
    }

    @Test
    @DisplayName("Document : aucun placeholder résiduel <...>")
    void document_aucun_placeholder_residuel() throws Exception {
        String texte = texteDuPdf(signerEtPdf(743, "ANT", tokenCc));
        assertFalse(java.util.regex.Pattern.compile("<[A-Z _]+>").matcher(texte).find(),
                "aucun placeholder <...> ne subsiste dans le texte du PDF");
    }

    @Test
    @DisplayName("Document : en-tête républicain présent")
    void document_genere_entete_present() throws Exception {
        String texte = texteDuPdf(signerEtPdf(744, "ANT", tokenCc));
        assertTrue(texte.contains("REPOBLIKAN") && texte.contains("MADAGASIKARA"),
                "en-tête républicain présent dans le PDF");
    }

    @Test
    @DisplayName("Document : corps de la lettre saisi présent")
    void document_genere_corps_lettre_present() throws Exception {
        assertTrue(texteDuPdf(signerEtPdf(745, "ANT", tokenCc)).contains("Corps de la lettre de renvoi"),
                "texte du corps présent dans le PDF");
    }

    @Test
    @DisplayName("Document : PDF stocké sur le FSX (répertoire LR/) sous un nom dérivé de refLettre — posé APRÈS la signature, pas pendant")
    void document_genere_stocke_fsx_ok() throws Exception {
        int id = seedLettreSoumiseLoc(730, "ANT");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        // ⚠️ 2026-08-19 — la production du document est sortie de la transaction de signature : dans la
        // transaction de test (jamais commitée) l'événement AFTER_COMMIT ne part pas, le chemin reste
        // donc NULL — exactement l'état de la fenêtre de génération en production.
        org.junit.jupiter.api.Assertions.assertNull(
                lettreRenvoiRepository.findById(id).orElseThrow().getCheminDocument(),
                "la signature ne produit plus le document dans sa transaction");
        // Le téléchargement conserve la régénération paresseuse : c'est lui qui pose le chemin ici.
        mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        String chemin = lettreRenvoiRepository.findById(id).orElseThrow().getCheminDocument();
        assertTrue(chemin != null && java.nio.file.Files.exists(java.nio.file.Path.of(chemin)),
                "fichier PDF présent sur le FSX : " + chemin);
        assertTrue(chemin.endsWith("00007_DDP_CRM-ANT_LR_2026.pdf"),
                "nom de fichier dérivé de refLettre avec '/' remplacés par '_'");
    }

    @Test
    @DisplayName("Signature lettre : statut SIGNE et documentDisponible=false dès la réponse — la réponse n'attend jamais Word")
    void lettre_signature_reponse_immediate_sans_document() throws Exception {
        // Empêche que quelqu'un remette un jour la conversion Word dans la transaction de signature :
        // la réponse doit décrire une lettre signée SANS document, et le front sait l'afficher ainsi
        // (même contrat que PvExamenDto.documentDisponible pour un PV SIGNE).
        int id = seedLettreSoumiseLoc(750, "ANT");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SIGNE"))
                .andExpect(jsonPath("$.imSignataire").value("CTRCC1"))
                .andExpect(jsonPath("$.documentDisponible").value(false));
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SIGNE"))
                .andExpect(jsonPath("$.documentDisponible").value(false));
    }

    @Test
    @DisplayName("Signature lettre : reste acquise même si la production du document est impossible — l'acte métier ne dépend plus de Word")
    void lettre_signature_reste_acquise_si_generation_impossible() throws Exception {
        // Invariant central du correctif du 2026-08-19. Avant, la conversion Word se faisait DANS la
        // transaction : une machine sans Word, un plantage du convertisseur ou un FSX indisponible
        // annulaient une signature pourtant valide. On sabote ici l'étape de stockage (le répertoire
        // cible est un FICHIER : createDirectories échoue) — la signature doit malgré tout aboutir.
        java.nio.file.Path fichierBloquant = java.nio.file.Files.createTempFile("prs-lr-impossible", ".lock");
        Object cheminInitial = org.springframework.test.util.ReflectionTestUtils
                .getField(lettreRenvoiDocumentService, "cheminStockageLr");
        org.springframework.test.util.ReflectionTestUtils.setField(lettreRenvoiDocumentService,
                "cheminStockageLr", fichierBloquant.toString());
        int id = seedLettreSoumiseLoc(751, "ANT");
        try {
            mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("SIGNE"));
            // La signature est acquise en base, et les effets de bord métier ont bien eu lieu.
            org.junit.jupiter.api.Assertions.assertEquals("SIGNE",
                    lettreRenvoiRepository.findById(id).orElseThrow().getStatut(),
                    "la lettre reste SIGNE : aucune défaillance de production du document ne l'annule");
            org.junit.jupiter.api.Assertions.assertEquals("EN_ATTENTE_PIECES",
                    dossierRepository.findById(751).orElseThrow().getStatut(),
                    "les effets métier de la signature (suspension de l'examen) sont eux aussi acquis");
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(lettreRenvoiDocumentService,
                    "cheminStockageLr", cheminInitial);
            java.nio.file.Files.deleteIfExists(fichierBloquant);
        }
    }

    @Test
    @DisplayName("Document pendant la fenêtre de génération : GET /{id}/document sert quand même le PDF (régénération paresseuse) puis documentDisponible passe à true")
    void lettre_document_pendant_fenetre_regenere() throws Exception {
        // Le front actuel affiche le bouton PDF dès le statut SIGNE (il ne lit pas encore
        // documentDisponible) : un clic pendant la fenêtre ne doit pas donner un 404, mais le PDF —
        // lentement, comme la signature le faisait avant. C'est le filet qui rend le correctif
        // invisible pour l'utilisateur tant que le front n'est pas aligné.
        int id = seedLettreSoumiseLoc(752, "ANT");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDisponible").value(false));
        mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDisponible").value(true));
    }

    @Test
    @DisplayName("Document : lettre non signée → 404 (la régénération paresseuse ne fabrique pas le PDF d'un brouillon)")
    void lettre_document_brouillon_404() throws Exception {
        // Garde-fou de la régénération paresseuse ajoutée au téléchargement : elle ne doit servir que
        // les lettres SIGNE. Sans cette condition, un brouillon deviendrait téléchargeable en PDF
        // officiel — un document non signé qui a l'apparence d'un document signé.
        int id = seedLettreSoumiseLoc(753, "ANT");   // statut SOUMIS
        mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", tokenCc))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Document régional : en-tête contient la localité du dossier (TOAMASINA)")
    void document_genere_localite_dossier_ok() throws Exception {
        int id = seedLettreSoumiseLoc(731, "TMS");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        byte[] pdf = mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(texteDuPdf(pdf).contains("COMMISSION REGIONALE DES MARCHES TOAMASINA"),
                "localité du dossier injectée dans l'en-tête régional");
    }

    @Test
    @DisplayName("Document régional : signataire « Le Chef de la Commission Régionale des Marchés »")
    void document_genere_signataire_regional_ok() throws Exception {
        int id = seedLettreSoumiseLoc(733, "TMS");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        byte[] pdf = mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(texteDuPdf(pdf).contains("Le Chef de la Commission Régionale des Marchés"),
                "ligne signataire régionale corrigée dans le modèle");
    }

    /** Signe une lettre (dossier localisé) et renvoie le PDF téléchargé. */
    private byte[] signerEtPdf(int idDossier, String localite, String token) throws Exception {
        int id = seedLettreSoumiseLoc(idDossier, localite);
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", token))
                .andExpect(status().isOk());
        return mvc.perform(get("/api/lettre-renvois/" + id + "/document").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    /** Texte extrait du PDF (PDFBox), espaces normalisés (FOP coupe les lignes au fil de la mise en page). */
    private String texteDuPdf(byte[] pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
        }
    }

    /** Vrai si le PDF contient au moins un objet image (PDFBox). */
    private boolean contientImage(byte[] pdf) throws Exception {
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

    /** Crée un dossier localisé (entité 1) + une lettre SOUMIS (examen 1) ; renvoie la PK de la lettre. */
    private int seedLettreSoumiseLoc(int idDossier, String localite) {
        Dossier d = dossier(idDossier, "EXAMINE");
        d.setIdLocalite(localite);
        d.setIdEntiteContract(1);
        dossierRepository.save(d);
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1);
        l.setIdDossier(idDossier);
        l.setRefLettre("00007/DDP/CRM-" + localite + "/LR/2026");   // contient des '/' (à nettoyer dans le nom de fichier)
        l.setObjetLettre("Renvoi");
        l.setCorpsLettre("Corps de la lettre de renvoi.");
        l.setDateLettre(LocalDate.of(2026, 6, 20));
        l.setDateExamen(LocalDate.of(2026, 6, 15));
        l.setStatut("SOUMIS");
        return lettreRenvoiRepository.save(l).getIdLettre();
    }

    /** Lettre de renvoi de l'examen 1 (localité ANT) au statut SOUMIS. */
    private int seedLettreSoumise() {
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1); l.setIdDossier(1); l.setObjetLettre("Renvoi"); l.setStatut("SOUMIS");
        return lettreRenvoiRepository.save(l).getIdLettre();
    }

    // ------------------------------------------------------------------
    // Pièces jointes par type de dossier (référentiel + upload + lettre de renvoi)
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // PPM avec AGPM (cas « appel d'offres ouvert », §3.1 Module 03)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PPM-AGPM : declencheAgpm exposé/persisté sur le mode ; agpmRequis dérivé sur le PPM (true si ≥1 marché en appel d'offres ouvert, sinon false)")
    void ppmAgpm_marqueurMode_etAgpmRequisDerive() throws Exception {
        // Le marqueur « appel d'offres ouvert » est administrable et persisté sur le mode (write + read).
        mvc.perform(post("/api/mode-passations").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":1,\"libelle\":\"Appel d'offres ouvert\",\"declencheAgpm\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.declencheAgpm").value(true));
        // Mode ordinaire (non déclencheur) : declencheAgpm null = false.
        modePassationRepository.save(new ModePassation(4, "Cotation", null, null, null, null));

        // PPM 9500 : un marché en appel d'offres ouvert (mode 1) → agpmRequis = true.
        // Dossiers SOUMIS (non brouillon) pour figurer dans « Mes PPM » (findVisiblesParPrmp exclut les BROUILLON).
        Dossier d1 = dossier(9500, "SOUMIS");
        d1.setIdTypeDossier("DDP"); d1.setIdPrmp("PRMP001"); d1.setIdLocalite("ANT");
        dossierRepository.save(d1);
        ppmRepository.save(ppm(9500, 9500, "PRMP001"));
        Marche m1 = marche(95001, 9500, 9500); m1.setIdMode(1); marcheRepository.save(m1);

        // PPM 9501 : uniquement un marché ordinaire (mode 4) → agpmRequis = false.
        Dossier d2 = dossier(9501, "SOUMIS");
        d2.setIdTypeDossier("DDP"); d2.setIdPrmp("PRMP001"); d2.setIdLocalite("ANT");
        dossierRepository.save(d2);
        ppmRepository.save(ppm(9501, 9501, "PRMP001"));
        Marche m2 = marche(95011, 9501, 9501); m2.setIdMode(4); marcheRepository.save(m2);

        // Le front lit agpmRequis sur le PPM (dérivé serveur, non recalculé côté front).
        mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPpm==9500 && @.agpmRequis==true)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==9501 && @.agpmRequis==false)]", hasSize(1)));
    }

    @Test
    @DisplayName("PPM-AGPM : soumission d'un PPM en appel d'offres ouvert SANS pièce AGPM → 400 {piecesJointes} ; avec AGPM → SOUMIS ; PPM ordinaire non concerné")
    void ppmAgpm_soumission_exigeAgpmConditionnel() throws Exception {
        // Pièce AGPM au référentiel : repérée par son code stable, OBLIGATOIRE statique = false (conditionnelle).
        int idAgpm = seedTypePieceCode("Avis Général de Passation de Marché", "AGPM", false, "DDP",6);
        // Mode déclencheur (appel d'offres ouvert) + mode ordinaire.
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);
        modePassationRepository.save(new ModePassation(4, "Cotation", null, null, null, null));

        // (1) PPM avec un marché en appel d'offres ouvert, AGPM non fournie → soumission refusée (400).
        Dossier d = dossier(9502, "BROUILLON");
        d.setRefeDossier(null); d.setIdTypeDossier("DDP"); d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(9502, 9502, "PRMP001"));
        Marche m = marche(95021, 9502, 9502); m.setIdMode(1); marcheRepository.save(m);

        mvc.perform(post("/api/dossiers/9502/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='piecesJointes')]", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[?(@.champ=='piecesJointes')].message",
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("AGPM"))));

        // Dépôt de la pièce AGPM (PRMP propriétaire) via le mécanisme existant, puis soumission → 200 SOUMIS.
        byte[] pdf = "%PDF-1.4 AGPM".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart("/api/piece-jointe-dossiers")
                .file(new MockMultipartFile("data", "", "application/json",
                        ("{\"idDossier\":9502,\"idTypePiece\":" + idAgpm + "}").getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("fichier", "agpm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/dossiers/9502/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));

        // (2) PPM ordinaire (aucun marché en appel d'offres ouvert) → AGPM non requise, soumission OK sans AGPM.
        Dossier d2 = dossier(9503, "BROUILLON");
        d2.setRefeDossier(null); d2.setIdTypeDossier("DDP"); d2.setIdPrmp("PRMP001"); d2.setIdLocalite("ANT");
        dossierRepository.save(d2);
        ppmRepository.save(ppm(9503, 9503, "PRMP001"));
        Marche m2 = marche(95031, 9503, 9503); m2.setIdMode(4); marcheRepository.save(m2);
        mvc.perform(post("/api/dossiers/9503/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));
    }

    // ------------------------------------------------------------------
    // Familles + sous-types de dossier (⚠️ règle ajoutée — hiérarchie tr_type_dossier → tr_sous_type_dossier)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Sous-types : lecture par famille ouverte ; écritures réservées ADMINISTRATEUR ; famille inconnue → 404")
    void sousTypes_referentiel() throws Exception {
        // Lecture par famille (remplit le dropdown de saisie) — ouverte à tout authentifié.
        mvc.perform(get("/api/sous-type-dossiers/par-famille/DDP").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idSousType=='PPM')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idSousType=='PPM-AGPM')]", hasSize(1)));
        // Famille inconnue → 404 explicite.
        mvc.perform(get("/api/sous-type-dossiers/par-famille/ZZZ").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
        // Liste ouverte : l'Admin ajoute un sous-type (ex. DAOX sous DMC) → 201 ; un Membre → 403.
        mvc.perform(post("/api/sous-type-dossiers").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"DAOX\",\"libelleSousType\":\"Variante DAO\",\"idTypeDossier\":\"DMC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDossier").value("DMC"));
        mvc.perform(post("/api/sous-type-dossiers").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"DAOY\",\"libelleSousType\":\"x\",\"idTypeDossier\":\"DMC\"}"))
                .andExpect(status().isForbidden());
        // Création sur une famille inconnue → 404.
        mvc.perform(post("/api/sous-type-dossiers").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"XYZ\",\"libelleSousType\":\"x\",\"idTypeDossier\":\"ZZZ\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sous-type DDP recalculé serveur : saisie mode ordinaire → PPM ; édition vers appel d'offres ouvert → PPM-AGPM ; retour → PPM")
    void sousType_ddp_recalcule() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);
        modePassationRepository.save(new ModePassation(4, "Cotation", null, null, null, null));

        // Saisie façade avec un marché en mode ordinaire → famille DDP, sous-type PPM.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"designationMarche\":\"M1\",\"montEstim\":1000000,\"idNature\":1,\"idMode\":4,"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDossier").value("DDP"))
                .andExpect(jsonPath("$.idSousType").value("PPM"))
                .andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int idDetail = marcheRepository.findByIdDossier(idDoss).get(0).getIdDetail();

        // Édition : la ligne passe en appel d'offres ouvert → le sous-type dérive vers PPM-AGPM.
        String v2 = "{\"exercice\":2026,\"signataire\":\"S\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R\","
                + "\"marches\":[{\"idDetail\":" + idDetail + ",\"designationMarche\":\"M1\",\"montEstim\":1000000,"
                + "\"idNature\":1,\"idMode\":1}]}";
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(v2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSousType").value("PPM-AGPM"));

        // Retour au mode ordinaire → le sous-type redescend à PPM (source de vérité = les marchés).
        String v3 = v2.replace("\"idMode\":1}", "\"idMode\":4}");
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(v3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSousType").value("PPM"));
    }

    @Test
    @DisplayName("GET /api/dossiers : filtres serveur ?type= (famille) et ?sousType= ; valeur inconnue → 400")
    void dossiers_filtres_typeEtSousType() throws Exception {
        Dossier a = dossier(9600, "SOUMIS"); a.setIdTypeDossier("DDP"); a.setIdSousType("PPM-AGPM");
        a.setIdPrmp("PRMP001"); a.setIdLocalite("ANT"); dossierRepository.save(a);
        Dossier b = dossier(9601, "SOUMIS"); b.setIdTypeDossier("DDP"); b.setIdSousType("PPM");
        b.setIdPrmp("PRMP001"); b.setIdLocalite("ANT"); dossierRepository.save(b);
        Dossier c = dossier(9602, "SOUMIS"); c.setIdTypeDossier("DMC"); c.setIdSousType("DAO");
        c.setIdPrmp("PRMP001"); c.setIdLocalite("ANT"); dossierRepository.save(c);

        mvc.perform(get("/api/dossiers?type=DDP").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9600)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9601)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9602)]", hasSize(0)));
        mvc.perform(get("/api/dossiers?sousType=PPM-AGPM").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9600)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9601)]", hasSize(0)));
        // Filtres combinables avec le statut, et valeur inconnue → 400.
        mvc.perform(get("/api/dossiers?statut=SOUMIS&type=DMC&sousType=DAO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9602)]", hasSize(1)));
        mvc.perform(get("/api/dossiers?type=XXX").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dossiers?sousType=XXX").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
    }

    /**
     * Prérequis de la pagination de l'écran « Mes dossiers » (PRMP), le plus consulté : il téléchargeait
     * la liste entière puis la filtrait en mémoire sur deux critères — la famille et l'appartenance au
     * groupe BROUILLON. Paginer sans descendre ces filtres côté serveur reviendrait à découper l'ensemble
     * NON filtré : les pages seraient trouées et {@code totalElements} compterait des dossiers absents de
     * l'écran. {@code ?type=} existait déjà ; ce test verrouille {@code ?brouillon=}, son articulation avec
     * {@code ?type=}, et surtout les deux invariants qu'il ne doit jamais violer :
     * <ul>
     * <li>sans paramètre, la réponse est <strong>strictement inchangée</strong> (aucun appelant existant
     * ne voit de différence — les brouillons restent servis à la PRMP) ;</li>
     * <li>un filtre n'élargit <strong>jamais</strong> le périmètre de visibilité (§1) : il s'applique
     * à l'intérieur, jamais à sa place.</li>
     * </ul>
     * {@code brouillon=false} signifie <strong>tout sauf</strong> BROUILLON, pas « SOUMIS » : d'où le
     * dossier CLOTURE du jeu d'essai, qui périrait si quelqu'un remplaçait la négation par une égalité.
     */
    @Test
    @DisplayName("GET /api/dossiers : filtre ?brouillon=true|false (combinable à ?type=), appliqué dans le "
            + "périmètre et AVANT la pagination ; absent → réponse inchangée ; valeur invalide → 400")
    void dossiers_filtreBrouillon_dansLePerimetreEtAvantPagination() throws Exception {
        prmpRepository.save(prmp("PRMP002", "ANT"));
        String tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");

        Dossier b1 = dossierLoc(9610, "BROUILLON", "ANT", "PRMP001"); b1.setIdTypeDossier("DDP");
        Dossier s1 = dossierLoc(9611, "SOUMIS", "ANT", "PRMP001"); s1.setIdTypeDossier("DDP");
        // CLOTURE : ni BROUILLON ni SOUMIS — doit sortir avec brouillon=false.
        Dossier c1 = dossierLoc(9612, "CLOTURE", "ANT", "PRMP001"); c1.setIdTypeDossier("DDP");
        Dossier b2 = dossierLoc(9613, "BROUILLON", "ANT", "PRMP001"); b2.setIdTypeDossier("DMC");
        // Brouillon d'une AUTRE PRMP, même localité et même famille : le filtre ne doit pas le faire apparaître.
        Dossier autre = dossierLoc(9614, "BROUILLON", "ANT", "PRMP002"); autre.setIdTypeDossier("DDP");
        dossierRepository.saveAll(java.util.List.of(b1, s1, c1, b2, autre));

        // 1) NON-RÉGRESSION : sans paramètre, la PRMP reçoit toujours ses brouillons ET ses dossiers déposés.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9610)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9611)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9612)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9613)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9614)]", hasSize(0)));

        // 2) brouillon=true : les seuls BROUILLON, toutes familles confondues.
        mvc.perform(get("/api/dossiers?brouillon=true").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9610)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9613)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9611)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idDossier==9612)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.statut!='BROUILLON')]", hasSize(0)));

        // 3) brouillon=false : TOUT sauf BROUILLON — le CLOTURE en fait partie, pas seulement le SOUMIS.
        mvc.perform(get("/api/dossiers?brouillon=false").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9611)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9612)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9610)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idDossier==9613)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.statut=='BROUILLON')]", hasSize(0)));

        // 4) Les deux filtres combinés : exactement ce que demande un écran « DDP / Déposés ».
        mvc.perform(get("/api/dossiers?type=DDP&brouillon=false").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9611)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9612)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9610)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idDossier==9613)]", hasSize(0)));
        // Et « DDP / Brouillons » : ne ramène pas le brouillon DDP de PRMP002 (périmètre intact).
        mvc.perform(get("/api/dossiers?type=DDP&brouillon=true").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9610)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9614)]", hasSize(0)));
        // Symétrique : PRMP002 filtrant à l'identique ne voit que le sien.
        mvc.perform(get("/api/dossiers?type=DDP&brouillon=true").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9614)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9610)]", hasSize(0)));

        // 5) Combinaison avec ?statut= : conjonction, donc contradiction → liste vide (et non 400).
        mvc.perform(get("/api/dossiers?statut=BROUILLON&brouillon=false").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 6) Valeur invalide → 400 explicite, comme ?statut= et ?type= (et non le 500 opaque du filet général).
        mvc.perform(get("/api/dossiers?brouillon=oui").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dossiers?page=0&size=10&brouillon=oui").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest());

        // 7) PAGINÉ : le découpage porte sur l'ensemble DÉJÀ filtré. On compare totalElements à la taille
        // de la liste plate pour les mêmes filtres : c'est l'invariant qui interdit de paginer avant de
        // filtrer (sinon totalElements compterait des dossiers que l'écran n'affiche pas).
        String plate = mvc.perform(get("/api/dossiers?type=DDP&brouillon=false").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int attendu = com.jayway.jsonpath.JsonPath.<Integer>read(plate, "$.length()");
        assertTrue(attendu >= 2, "le jeu d'essai doit fournir au moins les dossiers 9611 et 9612");
        mvc.perform(get("/api/dossiers?type=DDP&brouillon=false&page=0&size=1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(attendu));
        // Page entière : même contenu que la liste plate filtrée, brouillons exclus.
        mvc.perform(get("/api/dossiers?type=DDP&brouillon=false&page=0&size=100").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(attendu)))
                .andExpect(jsonPath("$.content[?(@.idDossier==9611)]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.idDossier==9612)]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.statut=='BROUILLON')]", hasSize(0)))
                .andExpect(jsonPath("$.content[?(@.idDossier==9614)]", hasSize(0)));
    }

    /**
     * La recherche par référence de la barre supérieure faisait un {@code forkJoin} sur la liste des
     * dossiers ET celle des PPM — deux tables complètes — à chaque soumission, pour retrouver UNE
     * référence. {@code ?reference=} descend ce travail côté serveur, sur les deux ressources : la
     * recherche interroge les deux parce qu'un dossier sans {@code refeDossier} s'affiche sous la
     * référence de son PPM.
     *
     * <p>La comparaison est une <strong>sous-chaîne insensible à la casse</strong>, et non une égalité :
     * le front compare par {@code includes()} sur la valeur repliée en minuscules, et l'utilisateur saisit
     * un fragment. Une égalité exacte aurait rendu un contrat plus strict et une fonction morte — d'où
     * l'assertion sur le fragment, qui périrait si quelqu'un « resserrait » le filtre en {@code equals}.</p>
     *
     * <p>Les deux invariants du motif de filtrage sont vérifiés comme pour {@code ?brouillon=} : le filtre
     * s'applique <strong>dans</strong> le périmètre (jamais à sa place — c'est le point sensible ici,
     * puisqu'une référence connue suffirait sinon à lire le dossier d'une autre PRMP) et <strong>avant</strong>
     * le découpage en page, sans quoi {@code totalElements} compterait des lignes que l'écran n'affiche pas.</p>
     */
    @Test
    @DisplayName("GET /api/dossiers et /api/ppms : filtre ?reference= (sous-chaîne, casse indifférente), "
            + "dans le périmètre et AVANT la pagination ; absent → réponse inchangée")
    void filtreReference_dossiersEtPpms_dansLePerimetreEtAvantPagination() throws Exception {
        prmpRepository.save(prmp("PRMP002", "ANT"));
        String tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");

        Dossier a = dossierLoc(9660, "SOUMIS", "ANT", "PRMP001"); a.setIdTypeDossier("DDP");
        a.setRefeDossier("DOS-2026-ALPHA-001");
        Dossier b = dossierLoc(9661, "BROUILLON", "ANT", "PRMP001"); b.setIdTypeDossier("DDP");
        b.setRefeDossier("DOS-2026-ALPHA-002");
        Dossier c = dossierLoc(9662, "SOUMIS", "ANT", "PRMP001"); c.setIdTypeDossier("DMC");
        c.setRefeDossier("DOS-2026-BETA-001");
        // Même référence, autre PRMP : le piège du sujet. Une référence connue ne doit pas ouvrir un dossier
        // hors périmètre — le filtre restreint, il n'autorise pas.
        Dossier intrus = dossierLoc(9663, "SOUMIS", "ANT", "PRMP002"); intrus.setIdTypeDossier("DDP");
        intrus.setRefeDossier("DOS-2026-ALPHA-003");
        dossierRepository.saveAll(java.util.List.of(a, b, c, intrus));

        Ppm p1 = ppm(9670, 9660, "PRMP001"); p1.setReference("PPM-2026-ALPHA");
        Ppm p2 = ppm(9671, 9662, "PRMP001"); p2.setReference("PPM-2026-BETA");
        Ppm pIntrus = ppm(9672, 9663, "PRMP002"); pIntrus.setReference("PPM-2026-ALPHA-BIS");
        ppmRepository.saveAll(java.util.List.of(p1, p2, pIntrus));

        // 1) NON-RÉGRESSION : sans le paramètre, les deux listes sont strictement celles d'avant.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9660)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9662)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9663)]", hasSize(0)));
        mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPpm==9670)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==9671)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==9672)]", hasSize(0)));

        // 2) SEUL : sous-chaîne, et non égalité — « ALPHA » n'est la référence complète d'aucun dossier.
        mvc.perform(get("/api/dossiers?reference=ALPHA").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.idDossier==9660)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9661)]", hasSize(1)));
        // Casse indifférente : le front replie la saisie en minuscules, le serveur doit faire de même.
        mvc.perform(get("/api/dossiers?reference=alpha-001").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDossier").value(9660));
        mvc.perform(get("/api/ppms?reference=beta").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idPpm").value(9671));

        // 3) PÉRIMÈTRE : PRMP001 connaît la référence de PRMP002 — elle ne la lui ouvre pas.
        mvc.perform(get("/api/dossiers?reference=DOS-2026-ALPHA-003").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/ppms?reference=PPM-2026-ALPHA-BIS").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        // Symétrique : PRMP002 cherchant le même fragment ne voit que le sien.
        mvc.perform(get("/api/dossiers?reference=ALPHA").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDossier").value(9663));

        // 4) COMBINÉ en ET avec les filtres existants — pas de remplacement, pas d'union.
        mvc.perform(get("/api/dossiers?reference=ALPHA&brouillon=false").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDossier").value(9660));
        mvc.perform(get("/api/dossiers?reference=BETA&type=DDP").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));   // BETA existe, mais en DMC : conjonction vide
        mvc.perform(get("/api/dossiers?reference=BETA&type=DMC").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDossier").value(9662));

        // 5) Sans correspondance → liste vide, pas 400 : une référence est du texte libre.
        mvc.perform(get("/api/dossiers?reference=INEXISTANT").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        // Valeur vide = pas de filtre (le champ de recherche vidé ne doit pas masquer la liste).
        mvc.perform(get("/api/dossiers?reference=").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9662)]", hasSize(1)));

        // 6) PAGINÉ : le découpage porte sur l'ensemble DÉJÀ filtré — totalElements compte 2, pas le périmètre.
        mvc.perform(get("/api/dossiers?reference=ALPHA&page=0&size=1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/ppms?reference=ALPHA&page=0&size=10").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].idPpm").value(9670));
    }

    @Test
    @DisplayName("Grille d'examen par sous-type : PPM = points communs seuls ; PPM-AGPM = communs + spécifique ; gardes 400")
    void grilleExamen_parSousType() throws Exception {
        // 2 points COMMUNS famille DDP (idSousType null) + 1 point SPÉCIFIQUE au sous-type PPM-AGPM.
        PointsCtrl c1 = new PointsCtrl();
        c1.setIdPointCtrl(801); c1.setLibelPointCtrl("Montants cohérents"); c1.setObligatoire(true);
        c1.setIdTypeDossier("DDP"); c1.setOrdrePointCtrl(1);
        pointsCtrlRepository.save(c1);
        PointsCtrl c2 = new PointsCtrl();
        c2.setIdPointCtrl(802); c2.setLibelPointCtrl("Signataire habilité"); c2.setObligatoire(true);
        c2.setIdTypeDossier("DDP"); c2.setOrdrePointCtrl(2);
        pointsCtrlRepository.save(c2);
        PointsCtrl s1 = new PointsCtrl();
        s1.setIdPointCtrl(803); s1.setLibelPointCtrl("AGPM joint et conforme"); s1.setObligatoire(true);
        s1.setIdTypeDossier("DDP"); s1.setIdSousType("PPM-AGPM"); s1.setOrdrePointCtrl(3);
        pointsCtrlRepository.save(s1);

        // Grille effective d'un PPM : les 2 communs, PAS le point AGPM.
        mvc.perform(get("/api/points-ctrls?sousType=PPM").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.idPointCtrl==803)]", hasSize(0)));
        // Grille effective d'un PPM-AGPM : communs + spécifique (3 points) — grille PPM ≠ PPM-AGPM.
        mvc.perform(get("/api/points-ctrls?sousType=PPM-AGPM").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.idPointCtrl==803)]", hasSize(1)));
        // Filtre famille (écran admin) : tous les points DDP, spécifiques compris.
        mvc.perform(get("/api/points-ctrls?typeDossier=DDP").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        // Gardes : sous-type inconnu → 400 ; sous-type hors famille → 400.
        mvc.perform(get("/api/points-ctrls?sousType=XXX").header("Authorization", tokenMembre))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/points-ctrls?typeDossier=DMC&sousType=PPM-AGPM").header("Authorization", tokenMembre))
                .andExpect(status().isBadRequest());
        // Admin : création d'un point ciblant un sous-type d'une AUTRE famille → 400 (cohérence dropdown).
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":804,\"libelPointCtrl\":\"x\",\"obligatoire\":true,"
                        + "\"idTypeDossier\":\"DDP\",\"idSousType\":\"DAO\"}"))
                .andExpect(status().isBadRequest());
        // Admin : création cohérente (sous-type de la même famille) → 201 avec idSousType exposé.
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":805,\"libelPointCtrl\":\"Controle DAOR\",\"obligatoire\":false,"
                        + "\"idTypeDossier\":\"DMC\",\"idSousType\":\"DAOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idSousType").value("DAOR"));
    }

    @Test
    @DisplayName("Grille — la portée (LIGNE/DOSSIER) est exposée dans le DTO ; défaut LIGNE ; création avec portée")
    void grille_exposePortee() throws Exception {
        PointsCtrl ligne = new PointsCtrl();
        ligne.setIdPointCtrl(810); ligne.setLibelPointCtrl("Objet"); ligne.setObligatoire(true);
        ligne.setIdTypeDossier("DDP"); ligne.setOrdrePointCtrl(1);   // portee non fixée → défaut LIGNE
        pointsCtrlRepository.save(ligne);
        PointsCtrl dossier = new PointsCtrl();
        dossier.setIdPointCtrl(811); dossier.setLibelPointCtrl("fractionnement illicite"); dossier.setObligatoire(true);
        dossier.setIdTypeDossier("DDP"); dossier.setOrdrePointCtrl(2);
        dossier.setPortee(cnm.prs.enums.PorteePointCtrl.DOSSIER);
        pointsCtrlRepository.save(dossier);

        mvc.perform(get("/api/points-ctrls?sousType=PPM").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPointCtrl==810)].portee", hasItem("LIGNE")))
                .andExpect(jsonPath("$[?(@.idPointCtrl==811)].portee", hasItem("DOSSIER")));

        // Création admin avec portée DOSSIER → exposée ; code inconnu → 400.
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":812,\"libelPointCtrl\":\"Coherence globale\",\"obligatoire\":true,"
                        + "\"idTypeDossier\":\"DDP\",\"portee\":\"DOSSIER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.portee").value("DOSSIER"));
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":813,\"libelPointCtrl\":\"x\",\"obligatoire\":true,"
                        + "\"idTypeDossier\":\"DDP\",\"portee\":\"MARCHE\"}"))
                .andExpect(status().isBadRequest());
        // Création sans portée → défaut LIGNE en sortie.
        mvc.perform(post("/api/points-ctrls").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPointCtrl\":814,\"libelPointCtrl\":\"Nature\",\"obligatoire\":true,"
                        + "\"idTypeDossier\":\"DDP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.portee").value("LIGNE"));
    }

    /** Circuit DDP/PPM EXAMINE (dossier + 2 marchés + PPM + réception/dispatch/examen ANT) pour l'examen par ligne. */
    private void seedCircuitExamenParLigne() {
        Dossier d = dossier(6000, "EXAMINE");
        d.setIdTypeDossier("DDP"); d.setIdSousType("PPM"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(6000, 6000, "PRMP001"));
        marcheRepository.save(marche(60001, 6000, 6000));
        marcheRepository.save(marche(60002, 6000, 6000));
        receptionRepository.save(reception(6000, 6000, "CTRSEC", true));   // localité ANT
        dispatchRepository.save(dispatch(6000, 6000, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(6000, 6000, "CTRMEM"));
        // Grille DDP : 2 points LIGNE (901, 902) + 1 point DOSSIER (903 = fractionnement).
        pointLigne(901, "Forme", 1);
        pointLigne(902, "Mode de passation", 2);
        PointsCtrl frac = new PointsCtrl();
        frac.setIdPointCtrl(903); frac.setLibelPointCtrl("fractionnement illicite"); frac.setObligatoire(true);
        frac.setIdTypeDossier("DDP"); frac.setOrdrePointCtrl(3);
        frac.setPortee(cnm.prs.enums.PorteePointCtrl.DOSSIER);
        pointsCtrlRepository.save(frac);
    }

    private void pointLigne(int id, String libelle, int ordre) {
        PointsCtrl p = new PointsCtrl();
        p.setIdPointCtrl(id); p.setLibelPointCtrl(libelle); p.setObligatoire(true);
        p.setIdTypeDossier("DDP"); p.setOrdrePointCtrl(ordre);   // portee défaut LIGNE
        pointsCtrlRepository.save(p);
    }

    @Test
    @DisplayName("Examen par ligne — résultat porté par marché + unicité (idExamen,idDetail,idPtControle) + cohérence portée/idDetail")
    void examen_resultatParLigne_etUnicite() throws Exception {
        seedCircuitExamenParLigne();

        // Point LIGNE (901) évalué sur le marché 60001 → 201, idDetail renvoyé.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70001,\"idExamen\":6000,\"idDetail\":60001,\"idPtControle\":901,\"conforme\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDetail").value(60001));
        // Doublon exact du triplet → 400 idPtControle.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70002,\"idExamen\":6000,\"idDetail\":60001,\"idPtControle\":901,\"conforme\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idPtControle"));
        // Même point, AUTRE marché (60002) → 201 (triplet distinct).
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70003,\"idExamen\":6000,\"idDetail\":60002,\"idPtControle\":901,\"conforme\":true}"))
                .andExpect(status().isCreated());
        // Point DOSSIER (903) AVEC idDetail → 400 idDetail (s'évalue une seule fois, sans ligne).
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70004,\"idExamen\":6000,\"idDetail\":60001,\"idPtControle\":903,\"conforme\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idDetail"));
        // idDetail hors du dossier → 400 idDetail.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70005,\"idExamen\":6000,\"idDetail\":99999,\"idPtControle\":901,\"conforme\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idDetail"));
        // Point DOSSIER (903) sans idDetail → 201, idDetail null.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70006,\"idExamen\":6000,\"idPtControle\":903,\"conforme\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDetail").doesNotExist());
    }

    @Test
    @DisplayName("Examen par ligne — complétude à la soumission (400 tant que toutes les lignes ne sont pas traitées) + avis suggéré")
    void examen_completude_soumission_etAvisSuggere() throws Exception {
        seedCircuitExamenParLigne();

        // Aucun détail → avisSuggere null.
        mvc.perform(get("/api/examens/6000").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.avisSuggere").doesNotExist());
        // Soumission alors que la grille n'est pas remplie → 400 ciblé « grille ».
        mvc.perform(post("/api/examens/6000/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("grille"));

        // Remplir : 2 points LIGNE × 2 marchés (4) + 1 point DOSSIER (1) = 5 évaluations.
        detailLigne(70101, 901, 60001, true, null);
        detailLigne(70102, 901, 60002, true, null);
        detailLigne(70103, 902, 60001, true, null);
        detailLigne(70104, 902, 60002, true, null);
        // Point DOSSIER non conforme (avec observation) → avis suggéré DEFAVORABLE.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":70105,\"idExamen\":6000,\"idPtControle\":903,\"conforme\":false,"
                        + "\"observations\":[{\"auLieuDe\":\"3 marchés\",\"lire\":\"1 marché\",\"ordre\":1}]}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/examens/6000").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.avisSuggere").value("DEF"));

        // Grille complète → soumission 201 (Projet de PV créé).
        mvc.perform(post("/api/examens/6000/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idAvis\":\"DEF\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idExamen").value(6000));
    }

    @Test
    @DisplayName("Examen par ligne — avis suggéré FAVORABLE quand tous les points sont conformes")
    void examen_avisSuggere_favorable() throws Exception {
        seedCircuitExamenParLigne();
        detailLigne(70201, 901, 60001, true, null);
        mvc.perform(get("/api/examens/6000").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.avisSuggere").value("FAV"));
    }

    private void detailLigne(int idDetailExamen, int idPt, int idMarche, boolean conforme, String obs) throws Exception {
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetailExamen\":" + idDetailExamen + ",\"idExamen\":6000,\"idDetail\":" + idMarche
                        + ",\"idPtControle\":" + idPt + ",\"conforme\":" + conforme + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("ANNEXE PV — préfixe du libellé par ligne de marché (unitaire, sans Word) : [Marché « … »] / [Dossier]")
    void pvAnnexe_prefixeLibelle() {
        org.junit.jupiter.api.Assertions.assertEquals("[Marché « Travaux RN13 »] Cohérence",
                cnm.prs.service.PvDocumentService.prefixerLibelle(42, "Cohérence", "Travaux RN13"));
        // Point dossier (idDetail null) → [Dossier].
        org.junit.jupiter.api.Assertions.assertEquals("[Dossier] fractionnement illicite",
                cnm.prs.service.PvDocumentService.prefixerLibelle(null, "fractionnement illicite", null));
        // Ligne sans désignation → repli « n°<id> ».
        org.junit.jupiter.api.Assertions.assertEquals("[Marché « n°7 »] Objet",
                cnm.prs.service.PvDocumentService.prefixerLibelle(7, "Objet", null));
    }

    @Test
    @DisplayName("Saisie dossier DMC/DDM : idSousType choisi (DAOR) → famille déduite ; sous-type DDP refusé ; sous-type inconnu → 400")
    void saisieDossier_sousTypeChoisi() throws Exception {
        // Nouveau contrat : idSousType → famille déduite du référentiel.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"DAOR\",\"idEntiteContract\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDossier").value("DMC"))
                .andExpect(jsonPath("$.idSousType").value("DAOR"));
        // Un sous-type de la famille DDP (planification) est refusé sur cette façade.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"PPM-AGPM\",\"idEntiteContract\":1}"))
                .andExpect(status().isConflict());
        // Sous-type inconnu → 400 ciblé.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"NIMPORTE\",\"idEntiteContract\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='idSousType')]", hasSize(1)));
    }

    /** Crée un type de pièce dans le référentiel H2 et renvoie sa PK générée. */
    private int seedTypePiece(String libelle, boolean obligatoire, String typeDossier, int ordre) {
        cnm.prs.entity.TypePieceJointe t = new cnm.prs.entity.TypePieceJointe();
        t.setLibellePiece(libelle);
        t.setObligatoire(obligatoire);
        t.setIdTypeDossier(typeDossier);
        t.setOrdre(ordre);
        return typePieceJointeRepository.save(t).getIdTypePiece();
    }

    /**
     * Comme {@link #seedTypePiece} mais avec un {@code code} stable (ex. {@code AGPM}) — support de
     * l'obligation conditionnelle. Renvoie la PK générée.
     */
    private int seedTypePieceCode(String libelle, String code, boolean obligatoire, String typeDossier, int ordre) {
        cnm.prs.entity.TypePieceJointe t = new cnm.prs.entity.TypePieceJointe();
        t.setLibellePiece(libelle);
        t.setCode(code);
        t.setObligatoire(obligatoire);
        t.setIdTypeDossier(typeDossier);
        t.setOrdre(ordre);
        return typePieceJointeRepository.save(t).getIdTypePiece();
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

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions soumettre(int idPv, String token) throws Exception {
        return mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions signer(int idPv, String token, String acteur, String role)
            throws Exception {
        return mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"" + acteur + "\",\"role\":\"" + role + "\"}"));
    }

    /** POST multipart d'une demande de retrait pour la PRMP connectée : partie {@code data} (DTO JSON) + lettre PDF valide. */
    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder posterRetrait(String dataJson) {
        return multipart("/api/demande-retraits")
                .file(new MockMultipartFile("data", "", "application/json", dataJson.getBytes(StandardCharsets.UTF_8)))
                .file(lettreRetraitPdf())
                .header("Authorization", tokenPrmp);
    }

    private static MockMultipartFile lettreRetraitPdf() {
        return new MockMultipartFile("fichier", "lettre-retrait.pdf", "application/pdf",
                "%PDF-1.4 lettre de demande de retrait datee et signee".getBytes(StandardCharsets.US_ASCII));
    }

    private String bearer(String login, ProfilUtilisateur role, TypeActeur type, String ref, String loc) {
        return "Bearer " + tokenService.generer(login, role.name(), type, ref, loc);
    }

    private Localite localite(String id, String libelle) {
        // referencement + code localite retirés du contrat/entité (2026-07-17) — colonnes BD dépréciées.
        Localite l = new Localite();
        l.setIdLocalite(id);
        l.setLibelleLocalite(libelle);
        return l;
    }

    private Profile profile(int id, String libelle) {
        Profile p = new Profile();
        p.setIdProfile(id);
        p.setProfile(libelle);
        return p;
    }

    private Controleur controleur(String im, int profile, String localite) {
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

    private Prmp prmp(String id, String localite) {
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

    private DelegationProfil delegation(int id, int delegant, int delegue) {
        DelegationProfil d = new DelegationProfil();
        d.setIdDelegation(id);
        d.setIdProfileDelegant(delegant);
        d.setIdProfileDelegue(delegue);
        d.setActif(true);
        return d;
    }

    private DemandeRetrait demandeRetrait(int id, int dossier, String idPrmp) {
        DemandeRetrait d = new DemandeRetrait();
        // ID_DEMANDE_RETRAIT est auto-généré (IDENTITY) : ne pas le fixer (sinon entité détachée).
        d.setIdDossier(dossier);
        d.setIdPrmp(idPrmp);
        d.setMotifRetrait("Motif de retrait");
        d.setDateDemande(LocalDateTime.of(2026, 6, 5, 10, 0));
        d.setStatut("EN_ATTENTE");
        return d;
    }

    private Ppm ppm(int id, int dossier, String idPrmp) {
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

    private Ppm ppmLocalise(int id, int dossier, String localite) {
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

    @Test
    @DisplayName("DMC : le type est dérivé du mode de passation (Achat Direct → BC)")
    void dmc_type_derive_du_mode() throws Exception {
        Long idBc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "BC", "Bon de Commande", true))
                .getIdTypeDmc();
        ModePassation mode = new ModePassation(90, "Achat Direct", null, null, null, null);
        mode.setIdTypeDmc(idBc);
        modePassationRepository.save(mode);
        Marche m = marche(9700, 1, 1); m.setIdMode(90); marcheRepository.save(m);

        mvc.perform(post("/api/dmcs/par-marche/9700").header("Authorization", tokenAdmin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDetail").value(9700))
                .andExpect(jsonPath("$.typeDmcCode").value("BC"))
                .andExpect(jsonPath("$.statut").value("A_PREPARER"));
    }

    @Test
    @DisplayName("DMC : mode non mappé → erreur explicite de configuration, aucun DMC créé")
    void dmc_mode_non_mappe_erreur_explicite() throws Exception {
        modePassationRepository.save(new ModePassation(91, "Gré à gré", null, null, null, null)); // ID_TYPE_DMC null
        Marche m = marche(9701, 1, 1); m.setIdMode(91); marcheRepository.save(m);

        mvc.perform(post("/api/dmcs/par-marche/9701").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertTrue(dossierMecRepository.findByIdDetail(9701).isEmpty());
    }

    @Test
    @DisplayName("DMC : unicité 1-1 par marché (2e création → 409)")
    void dmc_unique_par_marche() throws Exception {
        Long idBc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "BC", "Bon de Commande", true))
                .getIdTypeDmc();
        ModePassation mode = new ModePassation(90, "Achat Direct", null, null, null, null);
        mode.setIdTypeDmc(idBc);
        modePassationRepository.save(mode);
        Marche m = marche(9702, 1, 1); m.setIdMode(90); marcheRepository.save(m);

        mvc.perform(post("/api/dmcs/par-marche/9702").header("Authorization", tokenAdmin))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/dmcs/par-marche/9702").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DMC : changement de mode re-dérive le type si le DMC est A_PREPARER")
    void changement_mode_redérive_type_si_a_preparer() throws Exception {
        Long idBc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "BC", "Bon de Commande", true))
                .getIdTypeDmc();
        Long idDao = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "DAO", "Dossier d'Appel d'Offres", true))
                .getIdTypeDmc();
        ModePassation m90 = new ModePassation(90, "Achat Direct", null, null, null, null);
        m90.setIdTypeDmc(idBc); modePassationRepository.save(m90);
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(idDao); modePassationRepository.save(m92);
        // Dossier BROUILLON de PRMP001 (autorise la modification du marché).
        Dossier d = dossier(9710, "BROUILLON");
        d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT"); d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        Marche m = marche(9703, 9710, 1); m.setIdMode(90); marcheRepository.save(m);

        // DMC créé → BC.
        mvc.perform(post("/api/dmcs/par-marche/9703").header("Authorization", tokenAdmin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.typeDmcCode").value("BC"));
        // Changement de mode du marché → 92 (Appel d'offres ouvert = DAO).
        mvc.perform(put("/api/marches/9703").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":9710,\"idPpm\":1,\"designationMarche\":\"M\",\"statut\":\"PREVU\",\"idMode\":92}"))
                .andExpect(status().isOk());
        // DMC re-dérivé → DAO.
        mvc.perform(get("/api/dmcs/par-marche/9703").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.typeDmcCode").value("DAO"));
    }

    @Test
    @DisplayName("DMC : la suppression du marché supprime son DMC (cascade)")
    void suppression_marche_cascade_dmc() throws Exception {
        Long idBc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "BC", "Bon de Commande", true))
                .getIdTypeDmc();
        ModePassation mode = new ModePassation(90, "Achat Direct", null, null, null, null);
        mode.setIdTypeDmc(idBc); modePassationRepository.save(mode);
        Dossier d = dossier(9711, "BROUILLON");
        d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT"); d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        Marche m = marche(9705, 9711, 1); m.setIdMode(90); marcheRepository.save(m);

        mvc.perform(post("/api/dmcs/par-marche/9705").header("Authorization", tokenAdmin))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertTrue(dossierMecRepository.existsByIdDetail(9705));
        // Suppression du marché (brouillon, propriétaire PRMP001) → DMC supprimé.
        mvc.perform(delete("/api/marches/9705").header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(dossierMecRepository.existsByIdDetail(9705));
    }

    @Test
    @DisplayName("DMC : POST /api/mode-passations dérive automatiquement le type de DMC du libellé (sinon fourni conservé / sinon null)")
    void mode_create_autoMap_typeDmc() throws Exception {
        Long dao = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "DAO", "Dossier d'Appel d'Offres", true))
                .getIdTypeDmc();
        Long dc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "DC", "Dossier de Consultation", true))
                .getIdTypeDmc();

        // (a) « Appel d'offres ouvert » sans idTypeDmc → DAO (dérivé).
        mvc.perform(post("/api/mode-passations").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":95,\"libelle\":\"Appel d'offres ouvert\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDmc").value(dao.intValue()));
        // (b) « Demande de cotation » → DC (mot-clé « cotation »).
        mvc.perform(post("/api/mode-passations").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":96,\"libelle\":\"Demande de cotation\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDmc").value(dc.intValue()));
        // (c) libellé sans mot-clé → null (à mapper en admin).
        mvc.perform(post("/api/mode-passations").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":97,\"libelle\":\"Régie\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDmc").value(org.hamcrest.Matchers.nullValue()));
        // (d) idTypeDmc explicite fourni → conservé (pas écrasé par l'heuristique).
        mvc.perform(post("/api/mode-passations").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":98,\"libelle\":\"Appel d'offres ouvert\",\"idTypeDmc\":" + dc + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDmc").value(dc.intValue()));
    }

    @Test
    @DisplayName("Modes : catégorie NORMAL/DEROGATOIRE — GET l'expose (null = non classé), PUT la persiste, valeur inconnue → 400 (champ categorie)")
    void mode_categorie_declaratif() throws Exception {
        modePassationRepository.save(new ModePassation(70, "Gré à gré", null, null, null, null));

        // GET : le champ est servi, null tant que l'admin n'a pas classé.
        mvc.perform(get("/api/mode-passations/70").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value(nullValue()));

        // PUT (admin) : categorie DEROGATOIRE persiste et se relit.
        mvc.perform(put("/api/mode-passations/70").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":70,\"libelle\":\"Gré à gré\",\"categorie\":\"DEROGATOIRE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value("DEROGATOIRE"));
        mvc.perform(get("/api/mode-passations/70").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value("DEROGATOIRE"));

        // PUT : valeur hors enum → 400 ciblant le champ categorie (handler Jackson global).
        mvc.perform(put("/api/mode-passations/70").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":70,\"libelle\":\"Gré à gré\",\"categorie\":\"EXCEPTIONNEL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("categorie"));
    }

    @Test
    @DisplayName("Modes : reprise CATEGORIE au démarrage — NORMAL sur les modes DECLENCHE_AGPM non classés, sans écraser un classement admin")
    void mode_categorie_migration() throws Exception {
        ModePassation aoo = new ModePassation(71, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);
        modePassationRepository.save(new ModePassation(72, "Gré à gré", null, null, null, null));   // non marqué → reste non classé
        ModePassation dejaClasse = new ModePassation(73, "Consultation des Prix Ouverte", null, null, null, null);
        dejaClasse.setDeclencheAgpm(true);
        dejaClasse.setCategorie(cnm.prs.enums.CategorieModePassation.DEROGATOIRE);   // classement admin : intouchable
        modePassationRepository.save(dejaClasse);

        new cnm.prs.seed.CategorieModePassationMigration(modePassationRepository).run();

        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.CategorieModePassation.NORMAL,
                modePassationRepository.findById(71).orElseThrow().getCategorie());
        org.junit.jupiter.api.Assertions.assertNull(modePassationRepository.findById(72).orElseThrow().getCategorie());
        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.CategorieModePassation.DEROGATOIRE,
                modePassationRepository.findById(73).orElseThrow().getCategorie());
    }

    @Test
    @DisplayName("DMC : un mode créé À LA VOLÉE (saisie PPM) reçoit aussi le type de DMC dérivé du libellé")
    void mode_alaVolee_autoMap_typeDmc() throws Exception {
        Long bc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "BC", "Bon de Commande", true))
                .getIdTypeDmc();
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-DMC\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":1000000,\"natureLibelle\":\"Travaux\",\"modeLibelle\":\"Achat Direct\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // Le mode « Achat Direct » créé à la volée porte idTypeDmc = BC (dérivé).
        ModePassation cree = modePassationRepository.findAll().stream()
                .filter(m -> "Achat Direct".equals(m.getLibelle())).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(bc, cree.getIdTypeDmc());
    }

    @Test
    @DisplayName("Saisie PPM — lots[] : une ligne t_lot par lot, rattachée au marché ; sans lots[] → aucun lot (rétro-compat)")
    void saisiePpm_lots() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        // Marché A : 2 lots ; marché B : aucun lot (rétro-compat).
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-LOTS\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":3000000,\"natureLibelle\":\"Travaux\",\"modeLibelle\":\"Appel d'offres ouvert\",\"statut\":\"PREVU\","
                + "\"lots\":[{\"designationLot\":\"Lot 1 - Gros oeuvre\",\"montLot\":2000000,\"qteLot\":1,\"uniteLot\":\"U\"},"
                + "{\"designationLot\":\"Lot 2 - Finitions\",\"montLot\":1000000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]},"
                + "{\"designationMarche\":\"B\",\"montEstim\":500000,\"natureLibelle\":\"Travaux\",\"modeLibelle\":\"Appel d'offres ouvert\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");

        List<cnm.prs.entity.Marche> marches = marcheRepository.findByIdDossier(idDoss);
        cnm.prs.entity.Marche a = marches.stream().filter(m -> "A".equals(m.getDesignationMarche())).findFirst().orElseThrow();
        cnm.prs.entity.Marche b = marches.stream().filter(m -> "B".equals(m.getDesignationMarche())).findFirst().orElseThrow();

        // Marché A : 2 lots t_lot, rattachés au marché + dossier.
        List<cnm.prs.entity.Lot> lotsA = lotRepository.findByIdDetail(a.getIdDetail());
        org.junit.jupiter.api.Assertions.assertEquals(2, lotsA.size());
        org.junit.jupiter.api.Assertions.assertTrue(lotsA.stream().allMatch(l -> idDoss == l.getIdDossier()));
        org.junit.jupiter.api.Assertions.assertTrue(lotsA.stream()
                .anyMatch(l -> "Lot 1 - Gros oeuvre".equals(l.getDesignationLot())
                        && new java.math.BigDecimal("2000000").compareTo(l.getMontLot()) == 0
                        && Integer.valueOf(1).equals(l.getQteLot()) && "U".equals(l.getUniteLot())));
        // Marché B : aucun lot (rétro-compat).
        org.junit.jupiter.api.Assertions.assertTrue(lotRepository.findByIdDetail(b.getIdDetail()).isEmpty());

        // Suppression du dossier BROUILLON (avec lots) → cascade partagée retire aussi les t_lot (pas d'orphelin FK).
        mvc.perform(delete("/api/dossiers/" + idDoss).header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(lotRepository.findByIdDetail(a.getIdDetail()).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(marcheRepository.existsById(a.getIdDetail()));
    }

    /**
     * Les PK des ressources filles du marché (lot, bénéficiaire, date prévisionnelle, tranche) étaient
     * allouées par {@code max(id) + 1} : deux PRMP saisissant à la même seconde lisaient le même maximum
     * et la seconde échouait en violation d'unicité.
     *
     * <p>La séquence corrige cela, mais introduit un piège que ce test verrouille et que H2 sait, lui,
     * reproduire : consommer la séquence <strong>une seule fois</strong> puis incrémenter un compteur
     * local — ce que faisaient {@code SaisieService.creerLots} / {@code creerBeneficiaires} et
     * {@code MiseAJourPpmService.copierLignes} — laisse la séquence en retard sur les lignes réellement
     * écrites. La saisie SUIVANTE réattribue alors des identifiants déjà pris, et comme {@code save()}
     * sur PK assignée est un <em>merge</em>, elle écrase silencieusement les lignes de la précédente au
     * lieu de s'y ajouter. D'où la vérification par le NOMBRE de lignes après deux saisies, et pas
     * seulement par la plage des ids : une régression vers le compteur local ferait chuter ce compte.
     *
     * <p>⚠️ Ce test ne démontre pas l'absence de collision concurrente — H2 ne la reproduit pas. Il fige
     * l'origine des PK et l'absence d'écrasement entre deux saisies successives.
     */
    @Test
    @DisplayName("Ressources filles du marché : PK de seq_lot / seq_service_beneficiaire / seq_marche_prevision — séquence consommée à chaque ligne, deux saisies ne s'écrasent pas")
    void ressourcesFilles_pkServeur_sequenceConsommeeParLigne() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        // Deux saisies successives, identiques en structure : 2 marchés, 2 lots et 2 bénéficiaires chacun.
        // Attendu au total : 8 lots, 8 bénéficiaires, 4 dates prévisionnelles.
        // ⚠️ DEUX lignes filles par marché au minimum : avec une seule, un compteur local et la séquence
        // consommée ligne à ligne donnent le même résultat, et la régression passerait inaperçue.
        String marche = "{\"designationMarche\":\"%1$s\",\"montEstim\":3000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"lots\":[{\"designationLot\":\"Lot %1$s-1\",\"montLot\":2000000},"
                + "{\"designationLot\":\"Lot %1$s-2\",\"montLot\":1000000}],"
                + "\"beneficiaires\":[{\"soaCode\":\"SOA-1\",\"numCompte\":\"CPT-1\",\"ancMontBenef\":1000000},"
                + "{\"soaCode\":\"SOA-2\",\"numCompte\":\"CPT-2\",\"ancMontBenef\":2000000}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}";
        for (String suffixe : List.of("I", "II")) {
            String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\","
                    + "\"reference\":\"PPM-SEQ-" + suffixe + "\",\"marches\":["
                    + String.format(marche, "A" + suffixe) + ","
                    + String.format(marche, "B" + suffixe) + "]}";
            mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }

        // Aucune ligne écrasée : la séquence a bien été consommée une fois PAR ligne, sur les deux saisies.
        List<cnm.prs.entity.Lot> lots = lotRepository.findAll();
        List<cnm.prs.entity.ServiceBeneficiaire> benefs = serviceBeneficiaireRepository.findAll();
        List<cnm.prs.entity.MarchePrevision> previsions = marchePrevisionRepository.findAll();
        org.junit.jupiter.api.Assertions.assertEquals(8, lots.size(), "lots écrasés par un compteur local");
        org.junit.jupiter.api.Assertions.assertEquals(8, benefs.size(), "bénéficiaires écrasés par un compteur local");
        org.junit.jupiter.api.Assertions.assertEquals(4, previsions.size(), "prévisions écrasées par un compteur local");

        // Et les PK viennent bien des séquences (plages de test), pas d'un comptage de lignes.
        org.junit.jupiter.api.Assertions.assertTrue(lots.stream().allMatch(l -> l.getIdLot() >= 600001));
        org.junit.jupiter.api.Assertions.assertTrue(benefs.stream().allMatch(b -> b.getIdBenef() >= 700001));
        org.junit.jupiter.api.Assertions.assertTrue(previsions.stream().allMatch(p -> p.getIdPrevision() >= 800001));

        // Tranche : PK de seq_tranche, l'idTranche envoyé par le client (777) est ignoré.
        Integer idLot = lots.get(0).getIdLot();
        String r = mvc.perform(post("/api/tranches").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTranche\":777,\"idLot\":" + idLot + ",\"lieuTrc\":\"Antananarivo\",\"montTrc\":1000000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idTranche = com.jayway.jsonpath.JsonPath.read(r, "$.idTranche");
        org.junit.jupiter.api.Assertions.assertNotEquals(777, idTranche);
        org.junit.jupiter.api.Assertions.assertTrue(idTranche >= 900001, "idTranche hors de seq_tranche : " + idTranche);
    }

    @Test
    @DisplayName("GET /api/lots/par-marche/{idDetail} : lots d'une ligne de marché ; aucun/inconnu → liste vide")
    void lot_parMarche() throws Exception {
        marcheRepository.save(marche(9800, 1, 1));
        for (int k = 1; k <= 2; k++) {
            cnm.prs.entity.Lot l = new cnm.prs.entity.Lot();
            l.setIdLot(8000 + k);
            l.setIdDossier(1);
            l.setIdDetail(9800);
            l.setDesignationLot("Lot " + k);
            l.setMontLot(new java.math.BigDecimal(k + "000000"));
            lotRepository.save(l);
        }

        mvc.perform(get("/api/lots/par-marche/9800").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].designationLot", containsInAnyOrder("Lot 1", "Lot 2")))
                .andExpect(jsonPath("$[?(@.idDetail==9800)]", hasSize(2)));

        // Marché sans lot / inconnu → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/lots/par-marche/99999").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/lots/par-dossier/{idDossier} : agrège les lots de toutes les lignes de marché ; aucun/inconnu → liste vide")
    void lot_parDossier() throws Exception {
        // Dossier 7777 : marché 9810 (2 lots) + marché 9811 (1 lot).
        dossierRepository.save(dossier(7777, "BROUILLON"));   // FK t_lot.ID_DOSSIER → t_dossier
        marcheRepository.save(marche(9810, 7777, 1));
        marcheRepository.save(marche(9811, 7777, 1));
        int[][] seed = { {8101, 9810}, {8102, 9810}, {8103, 9811} };
        for (int[] s : seed) {
            cnm.prs.entity.Lot l = new cnm.prs.entity.Lot();
            l.setIdLot(s[0]);
            l.setIdDossier(7777);
            l.setIdDetail(s[1]);
            l.setDesignationLot("Lot " + s[0]);
            lotRepository.save(l);
        }

        mvc.perform(get("/api/lots/par-dossier/7777").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.idDetail==9810)]", hasSize(2)))
                .andExpect(jsonPath("$[?(@.idDetail==9811)]", hasSize(1)));

        // Dossier sans lot / inconnu → liste vide (filtre, pas de 404).
        mvc.perform(get("/api/lots/par-dossier/88888").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Processus prévisionnels : dateFin optionnelle — saisie/prevision sans dateFin → 201 ; séquence non contrainte si dateFin précédente absente ; dateFin présente toujours contrôlée")
    void processus_dateFin_optionnelle() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        capmRepository.save(new Capm(2, "DAO", 2, null, null));

        // Saisie : p0 (capm1) SANS dateFin ; p1 (capm2) démarre AVANT p0 → séquence non contrainte (skip) → 201.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-DFOPT\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":1000000,\"natureLibelle\":\"Travaux\",\"modeLibelle\":\"Appel d'offres ouvert\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\"},"
                + "{\"idCapm\":2,\"dateDebut\":\"2026-01-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        cnm.prs.entity.Marche a = marcheRepository.findByIdDossier(idDoss).get(0);
        // La prévision du processus capm1 a bien DATE_FIN null.
        cnm.prs.entity.MarchePrevision p0 = marchePrevisionRepository.findByIdDetail(a.getIdDetail()).stream()
                .filter(p -> Integer.valueOf(1).equals(p.getIdCapm())).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(p0.getDateFin());

        // POST /api/marche-previsions sans dateFin → 201.
        dossierRepository.save(dossier(7778, "BROUILLON"));
        marcheRepository.save(marche(9830, 7778, 1));
        mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrevision\":990020,\"idDetail\":9830,\"idCapm\":1,\"dateDebut\":\"2026-03-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dateFin").value(org.hamcrest.Matchers.nullValue()));

        // Régression : dateFin PRÉSENTE reste contrôlée (dateDebut ≥ dateFin → 400).
        mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrevision\":990021,\"idDetail\":9830,\"idCapm\":2,\"dateDebut\":\"2026-05-01\",\"dateFin\":\"2026-04-01\"}"))
                .andExpect(status().isBadRequest());
    }

    // ————————————————————————————————————————————————————————————————————————————————————————————
    // Périmètre des ressources FILLES du marché (lots, tranches, dates prévisionnelles, DMC).
    //
    // Ces quatre ressources n'avaient aucune garde : ni @PreAuthorize, ni entrée dans SecurityConfig,
    // ni appel à Visibilite. Elles retombaient sur `.anyRequest().authenticated()` et servaient la
    // table entière. Le symptôme mesurable : une PRMP recevait 403 sur GET /api/marches/{id} d'un
    // marché d'une autre entité, mais 200 — avec les lots — sur GET /api/lots/par-marche/{le même id},
    // et son DELETE passait. Ces tests fixent le contraire, endpoint par endpoint.
    // ————————————————————————————————————————————————————————————————————————————————————————————

    /**
     * Deux lignes de marché comparables, une par PRMP, dans la <strong>même localité</strong> (ANT) et
     * toutes deux hors brouillon : marché 900 (dossier 300 / PPM 300, PRMP001) et marché 901 (dossier 301 /
     * PPM 301, PRMP002). Le seul contraste est la <strong>propriété</strong> — c'est bien elle, et non la
     * localité, que les gardes des ressources filles doivent voir.
     *
     * @return le jeton de PRMP002 (PRMP001 = {@code tokenPrmp} du seed commun)
     */
    private String seedDeuxMarchesDeDeuxPrmp() {
        prmpRepository.save(prmp("PRMP002", "ANT"));
        dossierRepository.save(dossierLoc(300, "SOUMIS", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(301, "SOUMIS", "ANT", "PRMP002"));
        ppmRepository.save(ppm(300, 300, "PRMP001"));
        ppmRepository.save(ppm(301, 301, "PRMP002"));
        marcheRepository.save(marche(900, 300, 300));
        marcheRepository.save(marche(901, 301, 301));
        return bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");
    }

    /** Lot minimal rattaché à une ligne de marché (champs NOT NULL renseignés). */
    private void seedLot(int idLot, int idDossier, int idDetail) {
        cnm.prs.entity.Lot l = new cnm.prs.entity.Lot();
        l.setIdLot(idLot);
        l.setIdDossier(idDossier);
        l.setIdDetail(idDetail);
        l.setDesignationLot("Lot " + idLot);
        lotRepository.save(l);
    }

    @Test
    @DisplayName("Lots — périmètre hérité de la ligne de marché : liste scopée, 403 pour la PRMP d'une autre entité (lecture et DELETE), écriture fermée au circuit")
    void scoping_lots_heriteDuMarcheParent() throws Exception {
        String tokenPrmp2 = seedDeuxMarchesDeDeuxPrmp();
        seedLot(8500, 300, 900);   // lot d'un marché de PRMP001
        seedLot(8501, 301, 901);   // lot d'un marché de PRMP002

        // La liste ne renvoie plus la table entière : chaque PRMP ne voit que les lots de SES marchés.
        mvc.perform(get("/api/lots").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idLot==8500)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idLot==8501)]", hasSize(0)));
        mvc.perform(get("/api/lots").header("Authorization", tokenPrmp2))
                .andExpect(jsonPath("$[?(@.idLot==8501)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idLot==8500)]", hasSize(0)));
        // Le Membre d'ANT voit les deux : même localité, dossiers non brouillon (§1) — le scoping filtre,
        // il n'aveugle pas le circuit qui doit examiner ces dossiers.
        mvc.perform(get("/api/lots").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idLot==8500)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idLot==8501)]", hasSize(1)));

        // Le cœur de la faille : même id de marché, deux réponses opposées selon la ressource.
        mvc.perform(get("/api/marches/901").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/lots/par-marche/901").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/lots/8501").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        // Le marché dont elle est propriétaire reste servi (aucune régression d'usage légitime).
        mvc.perform(get("/api/lots/par-marche/900").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        // par-dossier reste un FILTRE (liste vide, jamais 403) : le dossier d'autrui ne rend rien.
        mvc.perform(get("/api/lots/par-dossier/301").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // DELETE : hors périmètre → 403 (PRMP d'une autre entité) ; hors rôle → 403 (circuit interne,
        // Président compris : les lignes d'un PPM appartiennent à la PRMP, le circuit ne les édite pas).
        mvc.perform(delete("/api/lots/8501").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/lots/8501").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/lots/8501").header("Authorization", tokenPresident))
                .andExpect(status().isForbidden());
        assertTrue(lotRepository.existsById(8501));
        // Le propriétaire, lui, supprime le sien.
        mvc.perform(delete("/api/lots/8500").header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Tranches — périmètre remonté lot → marché : liste scopée, 403 pour la PRMP d'une autre entité (lecture et DELETE), écriture fermée au circuit")
    void scoping_tranches_heriteDuMarcheParent() throws Exception {
        String tokenPrmp2 = seedDeuxMarchesDeDeuxPrmp();
        seedLot(8500, 300, 900);
        seedLot(8501, 301, 901);
        trancheRepository.save(new cnm.prs.entity.Tranche(7500, "Antananarivo", new BigDecimal("1000000"), 8500, null));
        trancheRepository.save(new cnm.prs.entity.Tranche(7501, "Antananarivo", new BigDecimal("2000000"), 8501, null));

        // La chaîne t_tranche.ID_LOT → t_lot.ID_DETAIL → périmètre du marché est bien parcourue.
        mvc.perform(get("/api/tranches").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idTranche==7500)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idTranche==7501)]", hasSize(0)));
        mvc.perform(get("/api/tranches").header("Authorization", tokenPrmp2))
                .andExpect(jsonPath("$[?(@.idTranche==7501)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idTranche==7500)]", hasSize(0)));

        mvc.perform(get("/api/tranches/7501").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tranches/7500").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/tranches/7501").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/tranches/7501").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        assertTrue(trancheRepository.existsById(7501));
        mvc.perform(delete("/api/tranches/7500").header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Dates prévisionnelles — périmètre hérité de la ligne de marché : liste scopée, 403 pour la PRMP d'une autre entité (lecture, ?marche=, DELETE), écriture fermée au circuit")
    void scoping_marchePrevisions_heriteDuMarcheParent() throws Exception {
        String tokenPrmp2 = seedDeuxMarchesDeDeuxPrmp();
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));   // FK t_marche_prevision.ID_CAPM
        marchePrevisionRepository.save(new MarchePrevision(9500, 900, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));
        marchePrevisionRepository.save(new MarchePrevision(9501, 901, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null));

        // Le calendrier prévisionnel de toutes les entités n'est plus lisible par tout porteur de jeton.
        mvc.perform(get("/api/marche-previsions").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrevision==9500)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPrevision==9501)]", hasSize(0)));
        mvc.perform(get("/api/marche-previsions").header("Authorization", tokenPrmp2))
                .andExpect(jsonPath("$[?(@.idPrevision==9501)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPrevision==9500)]", hasSize(0)));
        // Le Membre d'ANT garde la vue dont son examen dépend (les deux dossiers sont de sa localité).
        mvc.perform(get("/api/marche-previsions").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idPrevision==9500)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPrevision==9501)]", hasSize(1)));

        mvc.perform(get("/api/marche-previsions?marche=901").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/marche-previsions/9501").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        // L'appel que fait réellement le front (dates d'UN marché à soi) reste servi, trié comme avant.
        mvc.perform(get("/api/marche-previsions?marche=900").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ordre").value(1));

        mvc.perform(delete("/api/marche-previsions/9501").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/marche-previsions/9501").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        assertTrue(marchePrevisionRepository.existsById(9501));
        mvc.perform(delete("/api/marche-previsions/9500").header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DMC — verrou strict ADMINISTRATEUR sur toute la ressource, lectures comprises (aucun écran front ne la consomme)")
    void scoping_dmcs_reserveAdministrateur() throws Exception {
        seedDeuxMarchesDeDeuxPrmp();

        // Aucun profil autre que l'Administrateur n'entre, même sur son propre marché : la ressource est
        // fermée par défaut plutôt que dotée d'un périmètre théorique qu'aucun usage ne vient valider.
        mvc.perform(get("/api/dmcs/par-marche/900").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dmcs/par-marche/900").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dmcs/par-marche/900").header("Authorization", tokenPresident))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dmcs/1").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dmcs/par-marche/900").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());

        // L'Administrateur passe la garde : 404 (aucun DMC sur ce marché), pas 403 — la ressource reste
        // opérationnelle pour lui.
        mvc.perform(get("/api/dmcs/par-marche/900").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    /**
     * Le détail d'un marché doit servir <strong>exactement</strong> ce que la liste montre — ni plus, ni
     * moins. La garde de {@code GET /api/marches/{id}} testait le seul profil {@code PRMP} là où la liste
     * et les ressources filles passent par {@code Visibilite.estPrmp()}, qui couvre aussi l'UGPM (claim
     * {@code ref} = ID_PRMP de tutelle). Une UGPM voyait donc un marché dans la liste et lisait ses lots,
     * mais recevait 403 sur le détail du même id : une asymétrie qui ne protégeait rien, l'information
     * étant déjà servie par une autre porte.
     *
     * <p>Ce test fixe les deux versants indissociables : l'ouverture (sa tutelle) <strong>et</strong> le
     * refus (une autre PRMP) — c'est ce second point que l'alignement ne doit jamais coûter.</p>
     */
    @Test
    @DisplayName("Détail d'un marché — l'UGPM entre au titre de sa PRMP de tutelle (comme en liste), 403 sur le marché d'une autre PRMP")
    void detailMarche_ugpmAligneeSurLaListeDeSaTutelle() throws Exception {
        seedDeuxMarchesDeDeuxPrmp();
        // ref = PRMP001 (tutelle), aucune localité propre : le périmètre est celui de sa PRMP, pas une localité.
        String tokenUgpm = bearer("UGPM1", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);

        // Cohérence liste ↔ détail, dans les deux sens : ce que la liste montre est lisible en détail…
        mvc.perform(get("/api/marches").header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDetail==900)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDetail==901)]", hasSize(0)));
        mvc.perform(get("/api/marches/900").header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDetail").value(900));
        // …et ce qu'elle masque reste refusé. NON-RÉGRESSION CENTRALE : l'alignement ne donne accès
        // au marché d'AUCUNE autre PRMP, pas plus par le détail que par les lots.
        mvc.perform(get("/api/marches/901").header("Authorization", tokenUgpm))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/lots/par-marche/901").header("Authorization", tokenUgpm))
                .andExpect(status().isForbidden());

        // La PRMP de tutelle elle-même : inchangée (le sien passe, celui d'autrui non).
        mvc.perform(get("/api/marches/900").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        mvc.perform(get("/api/marches/901").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    /**
     * Garde-fou de l'alignement précédent : il ne devait toucher que l'UGPM. Un contrôleur reste borné à
     * sa localité et le circuit à ce que la localité lui donne — si la garde du détail avait été relâchée
     * (retour systématique, périmètre ignoré), le Membre de TOA obtiendrait 200 sur des marchés d'ANT et
     * ce test échouerait.
     */
    @Test
    @DisplayName("Détail d'un marché — profils du circuit inchangés : Membre hors localité 403 (liste vide), Membre de la localité 200, Président/Admin 200")
    void detailMarche_perimetreDesControleursInchange() throws Exception {
        seedDeuxMarchesDeDeuxPrmp();   // marchés 900 et 901, tous deux en localité ANT
        String tokenMembreToa = bearer("CTRTOA", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRTOA", "TOA");

        // Contrôleur d'une AUTRE localité : rien en liste, rien en détail.
        mvc.perform(get("/api/marches").header("Authorization", tokenMembreToa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDetail==900)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idDetail==901)]", hasSize(0)));
        mvc.perform(get("/api/marches/900").header("Authorization", tokenMembreToa))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/marches/901").header("Authorization", tokenMembreToa))
                .andExpect(status().isForbidden());

        // Contrôleur d'ANT : les deux dossiers sont de sa localité et non brouillon (§1) — accès conservé.
        mvc.perform(get("/api/marches/900").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        mvc.perform(get("/api/marches/901").header("Authorization", tokenMembre))
                .andExpect(status().isOk());

        // Président et Administrateur voient tout, sans condition de localité.
        mvc.perform(get("/api/marches/901").header("Authorization", tokenPresident))
                .andExpect(status().isOk());
        mvc.perform(get("/api/marches/901").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Lots & prévisions : PK allouée serveur — id client ignoré, deux PRMP → PK distinctes (le front alloue par max() sur une liste désormais scopée)")
    void ressourcesFilles_pkServeur_ignoreClientEtEviteLEcrasement() throws Exception {
        String tokenPrmp2 = seedDeuxMarchesDeDeuxPrmp();
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        // Le front calcule son id par max() sur la liste REÇUE de /api/lots. Cette liste étant désormais
        // scopée, deux PRMP calculent la même valeur — sans PK serveur, le second POST viendrait écraser
        // (merge sur PK assignée) le lot du premier. Ici les deux envoient le même id client.
        String l1 = mvc.perform(post("/api/lots").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLot\":501,\"idDossier\":300,\"idDetail\":900,\"designationLot\":\"Lot A\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String l2 = mvc.perform(post("/api/lots").header("Authorization", tokenPrmp2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLot\":501,\"idDossier\":301,\"idDetail\":901,\"designationLot\":\"Lot B\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idLot1 = com.jayway.jsonpath.JsonPath.read(l1, "$.idLot");
        int idLot2 = com.jayway.jsonpath.JsonPath.read(l2, "$.idLot");
        org.junit.jupiter.api.Assertions.assertNotEquals(idLot1, idLot2);
        // Les deux lots coexistent : aucun n'a été écrasé.
        org.junit.jupiter.api.Assertions.assertEquals("Lot A", lotRepository.findById(idLot1).orElseThrow().getDesignationLot());
        org.junit.jupiter.api.Assertions.assertEquals("Lot B", lotRepository.findById(idLot2).orElseThrow().getDesignationLot());

        // Même invariant sur les dates prévisionnelles.
        String p1 = mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrevision\":701,\"idDetail\":900,\"idCapm\":1,\"dateDebut\":\"2026-03-01\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String p2 = mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrevision\":701,\"idDetail\":901,\"idCapm\":1,\"dateDebut\":\"2026-03-01\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idPrev1 = com.jayway.jsonpath.JsonPath.read(p1, "$.idPrevision");
        int idPrev2 = com.jayway.jsonpath.JsonPath.read(p2, "$.idPrevision");
        org.junit.jupiter.api.Assertions.assertNotEquals(idPrev1, idPrev2);
        assertTrue(marchePrevisionRepository.existsById(idPrev1) && marchePrevisionRepository.existsById(idPrev2));

        // Et une PRMP ne peut pas créer chez l'autre, id serveur ou pas : le marché visé est contrôlé.
        mvc.perform(post("/api/lots").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":301,\"idDetail\":901,\"designationLot\":\"Intrusion\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * {@code MarchePrevisionDto.idPrevision} portait encore {@code @NotNull}, hérité de l'époque où la PK
     * était assignée par le client. Depuis le passage aux séquences le serveur l'écrase : le client était
     * donc refusé en 400 sur un champ dont la valeur n'allait pas être utilisée, et devait inventer un
     * nombre quelconque pour que sa requête passe. Ce test fige la seule règle qui vaille pour une
     * ressource du régime 1 — l'absence de l'identifiant est acceptée, l'identifiant réel vient de la
     * séquence — et vérifie qu'aucune autre validation n'a été emportée avec la contrainte retirée :
     * {@code idDetail}, {@code idCapm} et {@code dateDebut} restent obligatoires, et le périmètre du
     * marché visé reste contrôlé. Sans cette dernière assertion, assouplir le contrat pourrait ouvrir
     * une porte au lieu d'en fermer une.
     */
    @Test
    @DisplayName("Prévision sans idPrevision : acceptée, la PK venant de la séquence — les autres champs restent obligatoires")
    void prevision_sansIdentifiant_accepteeEtPkServeur() throws Exception {
        String tokenPrmp2 = seedDeuxMarchesDeDeuxPrmp();
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        // Aucun idPrevision dans le corps : le serveur alloue et renvoie l'id réel.
        String cree = mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":900,\"idCapm\":1,\"dateDebut\":\"2026-03-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPrevision").exists())
                .andReturn().getResponse().getContentAsString();
        int idPrev = com.jayway.jsonpath.JsonPath.read(cree, "$.idPrevision");
        assertTrue(marchePrevisionRepository.existsById(idPrev),
                "l'id renvoyé doit désigner la ligne réellement écrite");

        // Explicitement null : même traitement — l'appelant n'a plus à inventer de nombre.
        mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrevision\":null,\"idDetail\":900,\"idCapm\":1,\"dateDebut\":\"2026-04-01\"}"))
                .andExpect(status().isCreated());

        // Ce qui reste obligatoire l'est toujours : le 400 n'a pas disparu, il a changé de motif.
        mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCapm\":1,\"dateDebut\":\"2026-03-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='idDetail')]", hasSize(1)));
        mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":900,\"dateDebut\":\"2026-03-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='idCapm')]", hasSize(1)));
        mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":900,\"idCapm\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='dateDebut')]", hasSize(1)));

        // L'assouplissement ne relâche pas le périmètre : sans id à fournir, le marché visé reste contrôlé.
        mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":900,\"idCapm\":1,\"dateDebut\":\"2026-03-01\"}"))
                .andExpect(status().isForbidden());
    }

    private Marche marche(int idDetail, int dossier, int ppm) {
        Marche m = new Marche();
        m.setIdDetail(idDetail);
        m.setIdDossier(dossier);
        m.setIdPpm(ppm);
        m.setDesignationMarche("Marche " + idDetail);
        m.setStatut("PREVU");
        return m;
    }

    private Ministere ministere(int id) {
        Ministere m = new Ministere();
        m.setIdMinistere(id);
        m.setLibelleMinistere("Ministere " + id);
        return m;
    }

    private Organigramme organigramme(int id, int ministere) {
        Organigramme o = new Organigramme();
        o.setIdOrganigramme(id);
        o.setActif(true);
        o.setIdMinistere(ministere);
        o.setLibelle("Organigramme " + id);
        return o;
    }

    private EntiteContract entite(int id, int organigramme, String localite) {
        EntiteContract e = new EntiteContract();
        e.setIdEntiteContract(id);
        e.setLibelleEntite("Entite " + id);
        e.setAdresse("Adresse");
        e.setIdOrganigramme(organigramme);
        e.setNiveauHierarchique(1);
        e.setIdLocalite(localite);
        return e;
    }

    private PrmpEntite prmpEntite(int id, String prmp, int entite, boolean actif) {
        PrmpEntite pe = new PrmpEntite();
        pe.setIdPrmpEntite(id);
        pe.setIdPrmp(prmp);
        pe.setIdEntiteContract(entite);
        pe.setActif(actif);
        return pe;
    }

    private PrmpEntiteDemande demande(int id, String login, Integer idEntite, String libellePropose) {
        PrmpEntiteDemande d = new PrmpEntiteDemande();
        d.setIdDemande(id);
        d.setLogin(login);
        d.setIdEntiteContract(idEntite);
        if (libellePropose != null) {
            d.setLibellePropose(libellePropose);
            d.setAdressePropose("Adresse proposée");
            d.setIdLocalitePropose("ANT");
        }
        d.setStatutDemande("EN_ATTENTE");
        d.setDateDeclaration(LocalDate.of(2026, 1, 1));
        return d;
    }

    private Avis avis(String id, String libelle) {
        Avis a = new Avis();
        a.setIdAvis(id);
        a.setLibelleAvis(libelle);
        return a;
    }

    private Dossier dossier(int id, String statut) {
        Dossier d = new Dossier();
        d.setIdDossier(id);
        d.setRefeDossier("DOS-" + id);
        d.setDateRef(LocalDate.of(2026, 6, 1));
        d.setStatut(statut);
        return d;
    }

    /** Fiche UGPM minimale rattachée à une PRMP de tutelle (champs NOT NULL renseignés). */
    private Ugpm ugpm(String idUgpm, String idPrmpTutelle, String nom, String prenoms) {
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

    private Dossier dossierLoc(int id, String statut, String localite, String idPrmp) {
        Dossier d = dossier(id, statut);
        d.setIdLocalite(localite);
        d.setIdPrmp(idPrmp);
        return d;
    }

    private Reception reception(int id, int dossier, String imRecept, boolean complet) {
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

    private Dispatch dispatch(int id, int reception, String cc, String membre) {
        Dispatch d = new Dispatch();
        d.setIdDispatch(id);
        d.setIdReception(reception);
        d.setImCtrlCc(cc);
        d.setImCtrlMembre(membre);
        d.setDateDispatch(LocalDateTime.of(2026, 6, 3, 14, 45));
        d.setInterimDispatch(false);
        return d;
    }

    private Examen examen(int id, int dispatch, String membre) {
        Examen e = new Examen();
        e.setIdExamen(id);
        e.setIdDispatch(dispatch);
        e.setImCtrlMembre(membre);
        e.setDateExamen(LocalDate.of(2026, 6, 4));
        return e;
    }

    // ————————————————————————————————————————————————————————————————————————————————————————————
    // Gardes des neuf contrôleurs restés ouverts : pilotage, circuit, bénéficiaires.
    //
    // anomalies · copie-dossiers · echeances · indicateur-ctrls · indicateur-prmps · pv-navettes ·
    // service-beneficiaires · snapshot-statss · soa-beneficiaires n'avaient AUCUNE garde : ni
    // @PreAuthorize, ni entrée dans SecurityConfig, ni appel à Visibilite. Toutes retombaient sur
    // `.anyRequest().authenticated()` : un simple jeton valide — celui d'une PRMP, partie contrôlée —
    // servait la table entière et acceptait les écritures. Ces tests fixent, ressource par ressource,
    // le périmètre servi ET le refus, pour que la fermeture ne puisse pas être défaite sans le voir.
    // ————————————————————————————————————————————————————————————————————————————————————————————

    /**
     * Deux lignes de marché comparables, une par PRMP, dans la <strong>même localité</strong> (ANT) et
     * toutes deux hors brouillon : marché 920 (dossier 320 / PPM 320, PRMP001) et marché 921 (dossier
     * 321 / PPM 321, PRMP003). Le seul contraste est la <strong>propriété</strong> — c'est elle, et non
     * la localité, que doivent voir les gardes des ressources filles du marché.
     *
     * @return le jeton de PRMP003 (PRMP001 = {@code tokenPrmp} du seed commun)
     */
    private String seedDeuxMarchesPourGardes() {
        prmpRepository.save(prmp("PRMP003", "ANT"));
        dossierRepository.save(dossierLoc(320, "SOUMIS", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(321, "SOUMIS", "ANT", "PRMP003"));
        ppmRepository.save(ppm(320, 320, "PRMP001"));
        ppmRepository.save(ppm(321, 321, "PRMP003"));
        marcheRepository.save(marche(920, 320, 320));
        marcheRepository.save(marche(921, 321, 321));
        return bearer("PRMP003", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP003", "ANT");
    }

    /**
     * Un circuit complet par localité (dossier → réception → dispatch → examen) : 330 en ANT (reçu par
     * CTRCC1) et 331 en TMS (reçu par CTRCC2). Support des ressources du circuit interne, dont le
     * périmètre est la localité du contrôleur réceptionnaire.
     */
    private void seedCircuitDeuxLocalites() {
        dossierRepository.save(dossierLoc(330, "EXAMINE", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(331, "EXAMINE", "TMS", "PRMP001"));
        receptionRepository.save(reception(330, 330, "CTRCC1", true));
        receptionRepository.save(reception(331, 331, "CTRCC2", true));
        dispatchRepository.save(dispatch(330, 330, "CTRCC1", "CTRMEM"));
        dispatchRepository.save(dispatch(331, 331, "CTRCC2", "CTRMEM"));
        examenRepository.save(examen(330, 330, "CTRMEM"));
        examenRepository.save(examen(331, 331, "CTRMEM"));
    }

    /** Jalon minimal rattaché à une ligne de marché (champs NOT NULL renseignés). */
    private void seedEcheance(int id, int idDetail) {
        cnm.prs.entity.Echeance e = new cnm.prs.entity.Echeance();
        e.setIdEcheance(id);
        e.setIdDetail(idDetail);
        e.setTypeJalon("LANCEMENT");
        e.setDatePrevue(LocalDate.of(2026, 9, 1));
        echeanceRepository.save(e);
    }

    /** Anomalie minimale ; {@code idDetail} nul = anomalie de niveau PPM (sans ligne rattachée). */
    private void seedAnomalie(int id, Integer idDetail, Integer idPpm) {
        cnm.prs.entity.Anomalie a = new cnm.prs.entity.Anomalie();
        a.setIdAnomalie(id);
        a.setIdDetail(idDetail);
        a.setIdPpm(idPpm);
        a.setIdRegleAnomalie(1);
        a.setTypeAnomalie("MONTANT");
        a.setDescription("Ecart de montant constate");
        anomalieRepository.save(a);
    }

    @Test
    @DisplayName("Échéances — périmètre hérité de la ligne de marché : liste scopée, 403 hors périmètre, "
            + "écriture fermée à tous sauf Administrateur (aucun écran n'écrit le calendrier)")
    void gardes_echeances_perimetreDuMarcheEtEcritureAdmin() throws Exception {
        String tokenPrmp3 = seedDeuxMarchesPourGardes();
        seedEcheance(8900, 920);   // jalon d'un marché de PRMP001
        seedEcheance(8901, 921);   // jalon d'un marché de PRMP003

        // La liste ne renvoie plus la table entière : chaque PRMP ne voit que les jalons de SES marchés.
        mvc.perform(get("/api/echeances").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idEcheance==8900)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idEcheance==8901)]", hasSize(0)));
        mvc.perform(get("/api/echeances").header("Authorization", tokenPrmp3))
                .andExpect(jsonPath("$[?(@.idEcheance==8901)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idEcheance==8900)]", hasSize(0)));
        // Le Membre d'ANT voit les deux : même localité, dossiers non brouillon (§1) — le scoping filtre,
        // il n'aveugle pas le circuit qui doit examiner ces dossiers.
        mvc.perform(get("/api/echeances").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idEcheance==8900)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idEcheance==8901)]", hasSize(1)));

        // Détail : le jalon d'autrui est refusé, le sien reste servi (aucune régression d'usage légitime —
        // c'est l'appel du calendrier PRMP, seul écran consommateur de la ressource).
        mvc.perform(get("/api/echeances/8901").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/echeances/8900").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        // Écriture : le calendrier des jalons est alimenté par le suivi automatique, aucun écran ne l'écrit.
        String corps = "{\"idEcheance\":8902,\"idDetail\":920,\"typeJalon\":\"LANCEMENT\",\"datePrevue\":\"2026-10-01\"}";
        mvc.perform(post("/api/echeances").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/echeances").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/echeances/8901").header("Authorization", tokenPrmp3))
                .andExpect(status().isForbidden());
        assertTrue(echeanceRepository.existsById(8901));
        // L'Administrateur passe la garde : la ressource reste opérationnelle pour l'exploitation.
        mvc.perform(post("/api/echeances").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/echeances/8901").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Anomalies — périmètre hérité de la ligne signalée : liste scopée, 403 hors périmètre, "
            + "anomalie de niveau PPM réservée Président/Admin, écriture Administrateur seule")
    void gardes_anomalies_perimetreDuMarcheEtEcritureAdmin() throws Exception {
        String tokenPrmp3 = seedDeuxMarchesPourGardes();
        cnm.prs.entity.RegleAnomalie regle = new cnm.prs.entity.RegleAnomalie();
        regle.setIdRegleAnomalie(1);
        regle.setCodeRegle("ECART_MONTANT");
        regleAnomalieRepository.save(regle);
        seedAnomalie(8600, 920, 320);    // anomalie sur une ligne de PRMP001
        seedAnomalie(8601, 921, 321);    // anomalie sur une ligne de PRMP003
        seedAnomalie(8602, null, 320);   // anomalie de niveau PPM : aucune ligne dont hériter

        // La description d'une anomalie nomme le défaut constaté sur le marché d'autrui : elle sort du
        // périmètre de qui n'est pas concerné.
        mvc.perform(get("/api/anomalies").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idAnomalie==8600)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idAnomalie==8601)]", hasSize(0)));
        mvc.perform(get("/api/anomalies").header("Authorization", tokenPrmp3))
                .andExpect(jsonPath("$[?(@.idAnomalie==8601)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idAnomalie==8600)]", hasSize(0)));
        mvc.perform(get("/api/anomalies/8601").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/anomalies/8600").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        // Anomalie de niveau PPM : sans ligne parente, aucun périmètre à hériter → Président/Admin seuls.
        mvc.perform(get("/api/anomalies/8602").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/anomalies/8602").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/anomalies/8602").header("Authorization", tokenPresident))
                .andExpect(status().isOk());

        // Écriture : une anomalie est CONSTATÉE par le serveur. Laisser un client la clore (statut,
        // imTraitement) reviendrait à laisser effacer le constat qui le vise.
        String corps = "{\"idAnomalie\":8603,\"idDetail\":920,\"idRegleAnomalie\":1,"
                + "\"statut\":\"TRAITEE\",\"imTraitement\":\"CTRMEM\"}";
        mvc.perform(post("/api/anomalies").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/anomalies/8600").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/anomalies/8600").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/anomalies/8600").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Indicateurs contrôleur — périmètre nominatif : un contrôleur ne voit QUE ses propres "
            + "indicateurs (403 sur ceux d'un collègue), la PRMP n'en voit aucun, écriture Administrateur")
    void gardes_indicateurCtrl_perimetreNominatif() throws Exception {
        cnm.prs.entity.IndicateurCtrl iMembre = new cnm.prs.entity.IndicateurCtrl();
        iMembre.setIdIndicateur(8700);
        iMembre.setImControleur("CTRMEM");
        iMembre.setPeriode("2026-06");
        iMembre.setNbExamens(12);
        indicateurCtrlRepository.save(iMembre);
        cnm.prs.entity.IndicateurCtrl iCc = new cnm.prs.entity.IndicateurCtrl();
        iCc.setIdIndicateur(8701);
        iCc.setImControleur("CTRCC1");
        iCc.setPeriode("2026-06");
        iCc.setNbExamens(30);
        indicateurCtrlRepository.save(iCc);

        // La performance individuelle est une donnée d'évaluation : le Membre voit la sienne, pas celle
        // de son Chef de commission — et réciproquement.
        mvc.perform(get("/api/indicateur-ctrls").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idIndicateur==8700)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idIndicateur==8701)]", hasSize(0)));
        mvc.perform(get("/api/indicateur-ctrls/8701").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/indicateur-ctrls/8700").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        // La PRMP — partie contrôlée — n'a rien à connaître des notes des contrôleurs du CNM.
        mvc.perform(get("/api/indicateur-ctrls").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/indicateur-ctrls/8700").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        // Le Président garde la vue « tous les membres de toutes les commissions » (§3.8, Module 09).
        mvc.perform(get("/api/indicateur-ctrls").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.idIndicateur==8700)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idIndicateur==8701)]", hasSize(1)));

        // Écriture : un indicateur que le contrôleur évalué pourrait éditer ne mesurerait plus rien.
        String corps = "{\"idIndicateur\":8700,\"imControleur\":\"CTRMEM\",\"periode\":\"2026-06\",\"nbExamens\":999}";
        mvc.perform(put("/api/indicateur-ctrls/8700").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/indicateur-ctrls/8700").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/indicateur-ctrls/8700").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Indicateurs PRMP — périmètre de propriété : une PRMP ne voit QUE son bilan (403 sur celui "
            + "d'une homologue), le circuit n'en voit aucun, écriture Administrateur")
    void gardes_indicateurPrmp_perimetreDePropriete() throws Exception {
        String tokenPrmp3 = seedDeuxMarchesPourGardes();
        indicateurPrmpRepository.save(indicateurPrmp(8800, "PRMP001"));
        indicateurPrmpRepository.save(indicateurPrmp(8801, "PRMP003"));

        // Le bilan annuel (taux de conformité, retours, retraits) JUGE la PRMP : le comparatif inter-PRMP
        // est une vue de pilotage du Président, pas une donnée que les PRMP se lisent entre elles.
        mvc.perform(get("/api/indicateur-prmps").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idIndicateurPrmp==8800)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idIndicateurPrmp==8801)]", hasSize(0)));
        mvc.perform(get("/api/indicateur-prmps/8801").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/indicateur-prmps/8800").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        mvc.perform(get("/api/indicateur-prmps").header("Authorization", tokenPrmp3))
                .andExpect(jsonPath("$[?(@.idIndicateurPrmp==8801)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idIndicateurPrmp==8800)]", hasSize(0)));
        // Un Membre n'a pas de périmètre sur cette ressource : liste vide, détail refusé.
        mvc.perform(get("/api/indicateur-prmps").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/indicateur-prmps/8800").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/indicateur-prmps").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.idIndicateurPrmp==8800)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idIndicateurPrmp==8801)]", hasSize(1)));

        // Écriture : le bilan d'une PRMP ne se corrige pas par la PRMP qu'il évalue.
        String corps = "{\"idIndicateurPrmp\":8800,\"idPrmp\":\"PRMP001\",\"exercice\":2026,\"nbPpmSoumis\":9,"
                + "\"nbDossiersSoumis\":9,\"nbDossiersConformes\":9,\"nbDossiersNonConformes\":0,"
                + "\"nbRetours\":0,\"nbRetraits\":0}";
        mvc.perform(put("/api/indicateur-prmps/8800").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/indicateur-prmps/8800").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Instantanés statistiques — périmètre par localité : un CC voit la sienne (403 sur une autre "
            + "et sur l'agrégat national), la PRMP n'en voit aucun, écriture Administrateur")
    void gardes_snapshotStats_perimetreParLocalite() throws Exception {
        snapshotStatsRepository.save(snapshot(8400, "ANT"));
        snapshotStatsRepository.save(snapshot(8401, "TMS"));
        snapshotStatsRepository.save(snapshot(8402, null));   // agrégat national

        // Motif habituel (§1) : le CC d'ANT ne consolide pas les chiffres des autres localités, et
        // l'agrégat national — qui n'appartient à aucune localité — ne lui est pas servi non plus.
        mvc.perform(get("/api/snapshot-statss").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idSnapshot==8400)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idSnapshot==8401)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idSnapshot==8402)]", hasSize(0)));
        mvc.perform(get("/api/snapshot-statss/8401").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/snapshot-statss/8402").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/snapshot-statss/8400").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        // La PRMP est un acteur externe au circuit : aucun instantané.
        mvc.perform(get("/api/snapshot-statss").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/snapshot-statss/8400").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/snapshot-statss").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$", hasSize(3)));

        // Écriture : l'instantané est un agrégat calculé, pas une donnée déclarative.
        String corps = "{\"idSnapshot\":8403,\"dateSnapshot\":\"2026-07-01\",\"idLocalite\":\"ANT\",\"exercice\":2026}";
        mvc.perform(post("/api/snapshot-statss").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/snapshot-statss").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Copies de dossier — périmètre par localité du dossier : un CC voit la sienne (403 sur une "
            + "autre localité), la PRMP n'en voit aucune, écriture Administrateur")
    void gardes_copieDossiers_perimetreParLocalite() throws Exception {
        seedCircuitDeuxLocalites();
        copieDossierRepository.save(copieDossier(8300, 330, 330, "CTRMEM"));   // ANT
        copieDossierRepository.save(copieDossier(8301, 331, 331, "CTRCC2"));   // TMS

        // La copie de dossier trace À QUI le CNM a transmis quel dossier : c'est la cartographie du
        // circuit interne, matricules compris. La PRMP — partie contrôlée — n'a pas à la lire.
        mvc.perform(get("/api/copie-dossiers").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/copie-dossiers/8300").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/copie-dossiers").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idCopie==8300)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idCopie==8301)]", hasSize(0)));
        mvc.perform(get("/api/copie-dossiers/8301").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/copie-dossiers/8300").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        mvc.perform(get("/api/copie-dossiers").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.idCopie==8300)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idCopie==8301)]", hasSize(1)));

        // Écriture : un accusé de réception posable par n'importe quel porteur de jeton attesterait une
        // transmission qui n'a pas eu lieu.
        String corps = "{\"idCopie\":8300,\"idDispatch\":330,\"idDossier\":330,\"imDestinataire\":\"CTRMEM\","
                + "\"typeCopie\":\"DISPATCH_CC\",\"dateTransmission\":\"2026-06-10T09:00:00\","
                + "\"accuseReception\":true,\"dateAccuse\":\"2026-06-11T09:00:00\"}";
        mvc.perform(put("/api/copie-dossiers/8300").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/copie-dossiers/8300").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        assertFalse(copieDossierRepository.findById(8300).orElseThrow().getAccuseReception());
        mvc.perform(put("/api/copie-dossiers/8300").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Navettes de PV — périmètre par localité du PV : un Membre voit la sienne (403 sur une autre "
            + "localité), la PRMP n'en voit aucune")
    void gardes_pvNavettes_perimetreParLocalite() throws Exception {
        seedCircuitDeuxLocalites();
        seedPvSigne(830, 330);   // PV du circuit ANT
        seedPvSigne(831, 331);   // PV du circuit TMS
        pvNavetteRepository.save(navette(8200, 830, "RETOUR_RECTIF", "CTRCC1", "A corriger : montant"));
        pvNavetteRepository.save(navette(8201, 831, "RETOUR_RECTIF", "CTRCC2", "A corriger : piece"));

        // Le commentaire d'une navette est l'échange interne de la commission sur un dossier : la PRMP,
        // dont le dossier est examiné, n'a pas à lire les demandes de rectification adressées au Membre.
        mvc.perform(get("/api/pv-navettes").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/pv-navettes/8200").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());

        // Le circuit d'ANT garde SA navette — c'est l'appel réel de l'écran PV (Membre/Président/CC).
        mvc.perform(get("/api/pv-navettes").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idNavette==8200)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idNavette==8201)]", hasSize(0)));
        mvc.perform(get("/api/pv-navettes/8201").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pv-navettes/8200").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        mvc.perform(get("/api/pv-navettes").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.idNavette==8200)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idNavette==8201)]", hasSize(1)));
    }

    @Test
    @DisplayName("Navettes de PV — historique immuable (§3.5) : création et modification refusées en 409 comme "
            + "l'était déjà la suppression ; IM_ACTEUR et DATE_ACTION ne peuvent plus être réattribués")
    void gardes_pvNavettes_historiqueImmuable() throws Exception {
        seedCircuitDeuxLocalites();
        seedPvSigne(830, 330);
        pvNavetteRepository.save(navette(8200, 830, "RETOUR_RECTIF", "CTRCC1", "A corriger : montant"));

        // Le défaut réel : delete() refusait déjà, mais update() réécrivait TOUS les champs. Un acteur
        // pouvait donc attribuer sa propre demande de rectification à un collègue — sans que la
        // substitution laisse elle-même de trace. Ici CTRMEM tente de faire porter la sienne à CTRCC1.
        String reecriture = "{\"idNavette\":8200,\"idPv\":830,\"numNavette\":1,\"sens\":\"ACCEPTATION\","
                + "\"imActeur\":\"CTRMEM\",\"dateAction\":\"2026-01-01T08:00:00\",\"commentaire\":\"Reecrit\"}";
        mvc.perform(put("/api/pv-navettes/8200").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(reecriture))
                .andExpect(status().isConflict());
        // Même l'Administrateur ne réécrit pas la pièce probante : l'immuabilité ne connaît pas de rôle.
        mvc.perform(put("/api/pv-navettes/8200").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(reecriture))
                .andExpect(status().isConflict());

        // Création : une navette est constatée par le serveur (PvExamenService#ajouterNavette), jamais
        // déclarée par un client — sinon un mouvement se forge au nom d'un tiers.
        String forgee = "{\"idNavette\":8299,\"idPv\":830,\"numNavette\":9,\"sens\":\"ACCEPTATION\","
                + "\"imActeur\":\"CTRPRE\",\"dateAction\":\"2026-01-01T08:00:00\"}";
        mvc.perform(post("/api/pv-navettes").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(forgee))
                .andExpect(status().isConflict());
        // Suppression : refus déjà en place (§3.5), conservé — les trois verbes disent la même chose.
        mvc.perform(delete("/api/pv-navettes/8200").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());

        // Contrôle d'effet : la navette d'origine est intacte, et aucune navette forgée n'existe.
        cnm.prs.entity.PvNavette apres = pvNavetteRepository.findById(8200).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("CTRCC1", apres.getImActeur());
        org.junit.jupiter.api.Assertions.assertEquals("RETOUR_RECTIF", apres.getSens());
        org.junit.jupiter.api.Assertions.assertEquals("A corriger : montant", apres.getCommentaire());
        assertFalse(pvNavetteRepository.existsById(8299));
    }

    @Test
    @DisplayName("Services bénéficiaires — périmètre hérité de la ligne de marché : liste scopée, 403 pour la "
            + "PRMP d'une autre entité (lecture et DELETE), écriture fermée au circuit, PK allouée serveur")
    void gardes_serviceBeneficiaires_perimetreDuMarcheParent() throws Exception {
        String tokenPrmp3 = seedDeuxMarchesPourGardes();
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("SOA-GARDE", "SOA de test"));
        serviceBeneficiaireRepository.save(beneficiaire(8100, 920, "SOA-GARDE"));
        serviceBeneficiaireRepository.save(beneficiaire(8101, 921, "SOA-GARDE"));

        // La ventilation budgétaire (SOA, montants ancien/nouveau) d'une entité ne sort plus chez l'autre.
        mvc.perform(get("/api/service-beneficiaires").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idBenef==8100)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idBenef==8101)]", hasSize(0)));
        mvc.perform(get("/api/service-beneficiaires").header("Authorization", tokenPrmp3))
                .andExpect(jsonPath("$[?(@.idBenef==8101)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idBenef==8100)]", hasSize(0)));
        // Le circuit d'ANT voit les deux : l'examen des dossiers en dépend (§1).
        mvc.perform(get("/api/service-beneficiaires").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idBenef==8100)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idBenef==8101)]", hasSize(1)));

        mvc.perform(get("/api/service-beneficiaires/8101").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/service-beneficiaires/8100").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        // DELETE : hors périmètre → 403 (PRMP d'une autre entité) ; hors rôle → 403 (circuit interne,
        // Président compris : la ventilation d'un PPM appartient à la PRMP, le circuit ne l'édite pas).
        mvc.perform(delete("/api/service-beneficiaires/8101").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/service-beneficiaires/8101").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/service-beneficiaires/8101").header("Authorization", tokenPresident))
                .andExpect(status().isForbidden());
        assertTrue(serviceBeneficiaireRepository.existsById(8101));
        mvc.perform(delete("/api/service-beneficiaires/8100").header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());

        // PK serveur : le front alloue son id par max() sur la liste REÇUE, désormais SCOPÉE — deux PRMP
        // calculent donc la même valeur. Sans PK serveur, le second POST écraserait (merge) la ligne du
        // premier. Ici les deux envoient le même id client et doivent obtenir des lignes distinctes.
        String corps1 = "{\"idBenef\":8101,\"idDetail\":920,\"soaCode\":\"SOA-GARDE\",\"ancMontBenef\":1000}";
        String corps2 = "{\"idBenef\":8101,\"idDetail\":921,\"soaCode\":\"SOA-GARDE\",\"ancMontBenef\":2000}";
        String r1 = mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps1))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String r2 = mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenPrmp3)
                .contentType(MediaType.APPLICATION_JSON).content(corps2))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id1 = com.jayway.jsonpath.JsonPath.read(r1, "$.idBenef");
        int id2 = com.jayway.jsonpath.JsonPath.read(r2, "$.idBenef");
        org.junit.jupiter.api.Assertions.assertNotEquals(id1, id2);
        // La ligne 8101 de PRMP003, visée par l'id client, n'a pas été écrasée.
        org.junit.jupiter.api.Assertions.assertEquals(921,
                serviceBeneficiaireRepository.findById(8101).orElseThrow().getIdDetail());

        // Et une PRMP ne peut pas écrire chez l'autre, id serveur ou pas : le marché visé est contrôlé.
        mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps2))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Référentiel SOA — lecture ouverte à tout authentifié, création ouverte à la PRMP (import PPM) "
            + "mais refusée au circuit, renommage et suppression réservés à l'Administrateur")
    void gardes_soaBeneficiaires_referentielCadre() throws Exception {
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("SOA-EXISTANT", "Libelle initial"));

        // Lecture : référentiel sans périmètre (code + libellé), ouverte comme les autres référentiels.
        mvc.perform(get("/api/soa-beneficiaires").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.soaCode=='SOA-EXISTANT')]", hasSize(1)));

        // Création : c'est l'usage RÉEL de l'écran de soumission — la ventilation importée cite des codes
        // SOA absents du référentiel, que la PRMP enregistre avant de pouvoir soumettre. Fermer ce POST
        // à l'Administrateur seul bloquerait la soumission de tout PPM citant un SOA nouveau.
        mvc.perform(post("/api/soa-beneficiaires").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"soaCode\":\"SOA-NOUVEAU\",\"libelle\":\"Cree par la PRMP\"}"))
                .andExpect(status().isCreated());
        // Le circuit interne, lui, n'alimente pas le référentiel budgétaire.
        mvc.perform(post("/api/soa-beneficiaires").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"soaCode\":\"SOA-MEMBRE\",\"libelle\":\"Refuse\"}"))
                .andExpect(status().isForbidden());

        // Renommer ou retirer un code que d'AUTRES entités utilisent reste à l'Administrateur : une PRMP
        // qui renommerait SOA-EXISTANT changerait le libellé lu par toutes les autres.
        String corps = "{\"soaCode\":\"SOA-EXISTANT\",\"libelle\":\"Libelle detourne\"}";
        mvc.perform(put("/api/soa-beneficiaires/SOA-EXISTANT").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/soa-beneficiaires/SOA-EXISTANT").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals("Libelle initial",
                soaBeneficiaireRepository.findById("SOA-EXISTANT").orElseThrow().getLibelle());
        mvc.perform(put("/api/soa-beneficiaires/SOA-EXISTANT").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/soa-beneficiaires/SOA-EXISTANT").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
    }

    private cnm.prs.entity.IndicateurPrmp indicateurPrmp(int id, String idPrmp) {
        cnm.prs.entity.IndicateurPrmp i = new cnm.prs.entity.IndicateurPrmp();
        i.setIdIndicateurPrmp(id);
        i.setIdPrmp(idPrmp);
        i.setExercice(2026);
        i.setNbPpmSoumis(3);
        i.setNbDossiersSoumis(5);
        i.setNbDossiersConformes(4);
        i.setNbDossiersNonConformes(1);
        i.setNbRetours(2);
        i.setNbRetraits(0);
        return i;
    }

    /** Instantané statistique ; {@code idLocalite} nul = agrégat national. */
    private cnm.prs.entity.SnapshotStats snapshot(int id, String idLocalite) {
        cnm.prs.entity.SnapshotStats s = new cnm.prs.entity.SnapshotStats();
        s.setIdSnapshot(id);
        s.setDateSnapshot(LocalDate.of(2026, 6, 30));
        s.setIdLocalite(idLocalite);
        s.setExercice(2026);
        s.setNbDossiersRecus(10);
        return s;
    }

    private cnm.prs.entity.CopieDossier copieDossier(int id, int idDispatch, int idDossier, String destinataire) {
        cnm.prs.entity.CopieDossier c = new cnm.prs.entity.CopieDossier();
        c.setIdCopie(id);
        c.setIdDispatch(idDispatch);
        c.setIdDossier(idDossier);
        c.setImDestinataire(destinataire);
        c.setTypeCopie("DISPATCH_CC");
        c.setDateTransmission(LocalDateTime.of(2026, 6, 10, 9, 0));
        c.setAccuseReception(false);
        return c;
    }

    private cnm.prs.entity.PvNavette navette(int id, int idPv, String sens, String acteur, String commentaire) {
        cnm.prs.entity.PvNavette n = new cnm.prs.entity.PvNavette();
        n.setIdNavette(id);
        n.setIdPv(idPv);
        n.setNumNavette(1);
        n.setSens(sens);
        n.setImActeur(acteur);
        n.setDateAction(LocalDateTime.of(2026, 6, 12, 11, 0));
        n.setCommentaire(commentaire);
        return n;
    }

    private cnm.prs.entity.ServiceBeneficiaire beneficiaire(int id, int idDetail, String soaCode) {
        cnm.prs.entity.ServiceBeneficiaire b = new cnm.prs.entity.ServiceBeneficiaire();
        b.setIdBenef(id);
        b.setIdDetail(idDetail);
        b.setSoaCode(soaCode);
        b.setAncMontBenef(new BigDecimal("1000000"));
        return b;
    }
}
