package cnm.prs;

import static org.hamcrest.Matchers.containsInAnyOrder;
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
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.Localite;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
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
    @Autowired private MarchePrevisionRepository marchePrevisionRepository;
    @Autowired private cnm.prs.repository.CapmRepository capmRepository;
    @Autowired private cnm.prs.repository.ExamenDetailRepository examenDetailRepository;
    @Autowired private cnm.prs.repository.PointsCtrlRepository pointsCtrlRepository;
    @Autowired private cnm.prs.repository.LettreRenvoiRepository lettreRenvoiRepository;
    @Autowired private DemandeRetraitRepository demandeRetraitRepository;
    @Autowired private DelegationProfilRepository delegationProfilRepository;
    @Autowired private NatureRepository natureRepository;
    @Autowired private ModePassationRepository modePassationRepository;
    @Autowired private cnm.prs.service.PvDocumentGenerator pvDocumentGenerator;
    @Autowired private cnm.prs.service.ReferenceService referenceService;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private TypeDossierRepository typeDossierRepository;
    @Autowired private MinistereRepository ministereRepository;
    @Autowired private OrganigrammeRepository organigrammeRepository;
    @Autowired private EntiteContractRepository entiteContractRepository;
    @Autowired private PrmpEntiteRepository prmpEntiteRepository;
    @Autowired private cnm.prs.repository.TypePieceJointeRepository typePieceJointeRepository;
    @Autowired private cnm.prs.repository.PublicationRepository publicationRepository;
    @Autowired private cnm.prs.repository.PvExamenRepository pvExamenRepository;
    @Autowired private cnm.prs.repository.LettreRenvoiLueRepository lueRepository;
    @Autowired private cnm.prs.repository.DemandeRetraitVueRepository demandeRetraitVueRepository;
    @Autowired private cnm.prs.repository.CompteRepository compteRepository;
    @Autowired private cnm.prs.repository.SoaBeneficiaireRepository soaBeneficiaireRepository;
    @Autowired private cnm.prs.repository.ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    @Autowired private cnm.prs.repository.UgpmRepository ugpmRepository;

    private String tokenPresident;
    private String tokenCc;
    private String tokenMembre;
    private String tokenAdmin;
    private String tokenPrmp;
    private String tokenPublication;

    @BeforeEach
    void seed() {
        localiteRepository.save(localite("ANT", "Antananarivo"));
        typeDossierRepository.save(new TypeDossier("PPM", "Plan de passation des marchés"));
        typeDossierRepository.save(new TypeDossier("DAO", "Dossier d'appel d'offres"));

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

        // Délégations actives (orientation MLD : délégant = profil qui exerce,
        // délégué = profil dont la tâche est exercée). Président (2) et CC (3) exercent
        // les tâches de Secrétaire (4), Membre (5) et Vérificateur (6).
        delegationProfilRepository.save(delegation(1, 2, 4));
        delegationProfilRepository.save(delegation(2, 2, 5));
        delegationProfilRepository.save(delegation(3, 2, 6));
        delegationProfilRepository.save(delegation(4, 3, 4));
        delegationProfilRepository.save(delegation(5, 3, 5));
        delegationProfilRepository.save(delegation(6, 3, 6));

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
        d1.setIdTypeDossier("PPM"); d1.setIdPrmp("PRMP001"); d1.setIdLocalite("ANT");
        dossierRepository.save(d1);
        ppmRepository.save(ppm(60, 60, "PRMP001"));

        prmpRepository.save(prmp("PRMP002", "ANT"));
        String tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");
        Dossier d2 = dossier(61, "BROUILLON");
        d2.setIdTypeDossier("PPM"); d2.setIdPrmp("PRMP002"); d2.setIdLocalite("ANT");
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
                .andExpect(jsonPath("$.token").isNotEmpty());

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
        // 2 notifications pour CTRMEM, 1 pour CTRPRE (émises via le service ; ids 1, 2, 3).
        notificationService.emettreControleur(TypeNotification.PRET_DISPATCH, "CTRMEM", null, 1, TypeObjet.DOSSIER, 1, "Notif 1", "corps");
        notificationService.emettreControleur(TypeNotification.PRET_DISPATCH, "CTRMEM", null, 2, TypeObjet.DOSSIER, 2, "Notif 2", "corps");
        notificationService.emettreControleur(TypeNotification.PRET_DISPATCH, "CTRPRE", null, 3, TypeObjet.DOSSIER, 1, "Notif 3", "corps");

        // Scoping : CTRMEM voit ses 2, CTRPRE voit sa 1.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.length()").value(1));

        // Comptage des non-lues.
        mvc.perform(get("/api/notifications/mes/non-lues/count").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.nonLues").value(2));

        // Marquer la notif 1 comme lue (CTRMEM) → lu=true ; le compteur descend à 1.
        mvc.perform(post("/api/notifications/1/lu").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lu").value(true));
        mvc.perform(get("/api/notifications/mes/non-lues/count").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$.nonLues").value(1));

        // Marquer la notif de CTRPRE (id 3) en tant que CTRMEM → 403.
        mvc.perform(post("/api/notifications/3/lu").header("Authorization", tokenMembre))
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
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":70,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        // Soumission du projet → PROJET_SOUMIS.
        mvc.perform(post("/api/pv-examens/70/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"a valider\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));

        // Le CC d'ANT reçoit PV_A_VALIDER pointant le PV 70 (objet PV).
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')].idObjet", hasItem(70)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')].typeObjet", hasItem("PV")));
        // Le Président de la CNM aussi.
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_VALIDER')]", hasSize(1)));
    }

    @Test
    @DisplayName("Notification navette : retour (PV_A_RECTIFIER) et acceptation (PV_ACCEPTE) notifient le Membre auteur")
    void notification_navettePvAuteur() throws Exception {
        // Création + soumission d'un PV (auteur CTRMEM, localité ANT).
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":71,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/71/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"v1\"}"))
                .andExpect(status().isOk());

        // Le CC retourne le PV pour rectification → le Membre auteur reçoit PV_A_RECTIFIER (objet PV).
        mvc.perform(post("/api/pv-examens/71/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"commentaire\":\"corriger la synthese\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("EN_RECTIFICATION"));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_RECTIFIER')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_RECTIFIER')].idObjet", hasItem(71)));

        // Re-soumission puis acceptation par le CC → le Membre auteur reçoit PV_ACCEPTE.
        mvc.perform(post("/api/pv-examens/71/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"v2\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/71/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_ACCEPTE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_ACCEPTE')].idObjet", hasItem(71)));
    }

    @Test
    @DisplayName("Statut examen : créer un examen fait passer le dossier DISPATCHE → EXAMINE (listes exclusives)")
    void statut_examenAvanceVersExamine() throws Exception {
        dossierRepository.save(dossier(30, "PRET_DISPATCH"));
        receptionRepository.save(reception(60, 30, "CTRSEC", true)); // ANT
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":80,\"idReception\":60,\"imCtrlMembre\":\"CTRMEM\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        // Avant examen : DISPATCHE (à examiner).
        mvc.perform(get("/api/dossiers/30").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("DISPATCHE"));

        // Le Membre crée l'examen → le dossier passe EXAMINE.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":80,\"idDispatch\":80,\"imCtrlMembre\":\"CTRMEM\"}"))
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
        // Dossier 1 = EXAMINE (seed). PV FAVR sur l'examen 1, soumis, accepté, puis co-signé.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":90,\"idExamen\":1,\"idAvis\":\"FAVR\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/90/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/90/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/90/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/90/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("SIGNE"));

        // Le dossier 1 (avis FAVR) est passé EXAMINE → EN_VERIFICATION.
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
    }

    /** Crée un PV avec l'avis donné sur l'examen 1 (dossier 1) et le porte à SIGNE (Membre + Président). */
    private void signerPvAvecAvis(int idPv, String avis) throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + idPv + ",\"idExamen\":1,\"idAvis\":\"" + avis + "\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("SIGNE"));
    }

    @Test
    @DisplayName("Branchement signature — avis FAVORABLE (FAV) → dossier CLOTURE auto + PRMP PV_SIGNE + vérificateur PV_POUR_INFO")
    void signature_avisFavorable_clotureAuto() throws Exception {
        signerPvAvecAvis(94, "FAV");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_POUR_INFO')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Branchement signature — avis DÉFAVORABLE (DEF) → dossier CLOTURE + PRMP PV_SIGNE + vérificateur PV_POUR_INFO")
    void signature_avisDefavorable_clotureAuto() throws Exception {
        signerPvAvecAvis(95, "DEF");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_POUR_INFO')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Branchement signature — avis NE SE PRONONCE PAS (NSP) → dossier CLOTURE (idem DEF) + notifs PRMP + vérificateur")
    void signature_avisNeSePrononce_clotureAuto() throws Exception {
        signerPvAvecAvis(96, "NSP");
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_SIGNE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_POUR_INFO')].destinataireIm", hasItem("CTRVER")));
    }

    @Test
    @DisplayName("Branchement signature — avis FAVORABLE AVEC RÉSERVE (FAVR) → dossier EN_VERIFICATION + vérificateur PV_A_VERIFIER + PRMP PV_SIGNE")
    void signature_avisReserve_enVerification() throws Exception {
        signerPvAvecAvis(97, "FAVR");
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
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":91,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/91/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/91/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/91/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/91/signer").header("Authorization", tokenPresident)
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

        // CTRMEM examine son dossier 50 → il passe EXAMINE.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":95,\"idDispatch\":95,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());

        // Exclusivité : 50 quitte à-examiner et entre dans examinés (paginé → $.content).
        mvc.perform(get("/api/dossiers/a-examiner").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idDossier==50)]", hasSize(0)));
        mvc.perform(get("/api/dossiers/examines").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.idDossier==50)]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.idDossier==51)]", hasSize(0)));
    }

    @Test
    @DisplayName("Co-signature PV : rôle↔acteur authentifié, identité enregistrée (Membre attributaire + Président réel)")
    void cosignature_authentificationEtIdentite() throws Exception {
        // PV sur examen 1 (Membre CTRMEM), porté à PROJET_ACCEPTE.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":92,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/92/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/92/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isOk());

        // Un Membre ne peut PAS falsifier la signature Président → 403.
        mvc.perform(post("/api/pv-examens/92/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isForbidden());
        // Un AUTRE Membre (non attributaire) ne peut pas signer comme MEMBRE → 403.
        String tokenAutreMembre = bearer("CTRMEM2", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM2", "ANT");
        mvc.perform(post("/api/pv-examens/92/signer").header("Authorization", tokenAutreMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM2\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isForbidden());

        // Le Membre attributaire signe → reste PROJET_ACCEPTE (le co-signataire manque).
        mvc.perform(post("/api/pv-examens/92/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        // Le Président réel co-signe → SIGNE, identités enregistrées (plus de « — »).
        mvc.perform(post("/api/pv-examens/92/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRMEM"))
                .andExpect(jsonPath("$.imCtrlPresident").value("CTRPRE"));
    }

    @Test
    @DisplayName("Co-signature PV par le CC : CC de la localité OK (identité enregistrée), CC d'une autre localité → 403")
    void cosignature_ccDeLaLocalite() throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":93,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/93/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/93/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/93/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk());

        // Un CC d'une AUTRE localité (TMS) ne peut pas co-signer un PV d'ANT → 403.
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        mvc.perform(post("/api/pv-examens/93/signer").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC2\",\"role\":\"CC\"}"))
                .andExpect(status().isForbidden());
        // Le CC de la localité (ANT) co-signe → SIGNE, identité enregistrée.
        mvc.perform(post("/api/pv-examens/93/signer").header("Authorization", tokenCc)
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
        String body = "{\"idLocalite\":\"TMS\",\"libelleLocalite\":\"Toamasina\","
                + "\"referencement\":\"REF-TMS\",\"localite\":\"TMS\"}";

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
        // Le Membre envoie un message au CC.
        mvc.perform(post("/api/messages/envoyer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destinataireIm\":\"CTRCC1\",\"sujet\":\"Question\",\"corps\":\"Bonjour\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idMessage").value(1))
                .andExpect(jsonPath("$.expediteurIm").value("CTRMEM"))
                .andExpect(jsonPath("$.destinataireIm").value("CTRCC1"))
                .andExpect(jsonPath("$.lu").value(false));

        // Boîte de réception du CC : 1 message ; envoyés du Membre : 1.
        mvc.perform(get("/api/messages/recus").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sujet").value("Question"));
        mvc.perform(get("/api/messages/envoyes").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        // Le CC marque le message comme lu.
        mvc.perform(post("/api/messages/1/lu").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lu").value(true));

        // L'expéditeur (non destinataire) ne peut pas marquer lu → 403.
        mvc.perform(post("/api/messages/1/lu").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // Un tiers ne peut pas lire le message (confidentialité) → 403.
        mvc.perform(get("/api/messages/1").header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Audit automatique : une écriture API est tracée dans t_audit_log (§3.8)")
    void audit_traceLesEcritures() throws Exception {
        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLocalite\":\"TMS\",\"libelleLocalite\":\"Toamasina\","
                        + "\"referencement\":\"REF-TMS\",\"localite\":\"TMS\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nomTable").value("localites"))
                .andExpect(jsonPath("$[0].typeAction").value("CREATE"))
                .andExpect(jsonPath("$[0].imActeur").value("CTRADM"));
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
                .content("{\"libelleLocalite\":\"X\",\"referencement\":\"R\",\"localite\":\"X\"}"))
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
        d.setIdTypeDossier("PPM");
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
        d.setIdTypeDossier("PPM");
        dossierRepository.save(d);

        // POST : la réponse porte la référence structurée <seq>/PPM/CRM-ANT/<annee>.
        String resp = mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":330,\"numPassage\":1,\"typePassage\":\"INITIAL\",\"complet\":true,"
                        + "\"dateReception\":\"2026-06-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference", org.hamcrest.Matchers.matchesPattern("\\d{5}/PPM/CRM-ANT/\\d{4}")))
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
        String rAnt = referenceService.generer("PPM", "ANT", false, 2099);
        String rTms = referenceService.generer("PPM", "TMS", false, 2099);
        // Numéros distincts ET consécutifs malgré des localités différentes (plus de « 00001 » partagé).
        org.junit.jupiter.api.Assertions.assertEquals("00001/PPM/CRM-ANT/2099", rAnt);
        org.junit.jupiter.api.Assertions.assertEquals("00002/PPM/CRM-TMS/2099", rTms);
    }

    @Test
    @DisplayName("Référence dossier — incrément strictement croissant sans saut ni doublon (5 dossiers)")
    void reference_sequence_increment_correct() {
        for (int i = 1; i <= 5; i++) {
            org.junit.jupiter.api.Assertions.assertEquals(String.format("%05d/PPM/CRM-ANT/2098", i),
                    referenceService.generer("PPM", "ANT", false, 2098));
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
        seedExamenAvecRefe(340, "00007/PPM/CRM-ANT/2096");
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":340}"))
                .andExpect(jsonPath("$.refLettre").value("00001/PPM/CRM-ANT/LR/2096"));
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":340}"))
                .andExpect(jsonPath("$.refLettre").value("00002/PPM/CRM-ANT/LR/2096"));
    }

    @Test
    @DisplayName("Réf. lettre — séquence globale (2 dossiers/localités différents → numéros distincts/consécutifs)")
    void lettre_reference_sequence_unique_globale() throws Exception {
        seedExamenAvecRefe(341, "00001/PPM/CRM-ANT/2097");
        seedExamenAvecRefe(342, "00009/PPM/CRM-TMS/2097");   // dossier différent, localité TMS dans la réf
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":341}"))
                .andExpect(jsonPath("$.refLettre").value("00001/PPM/CRM-ANT/LR/2097"));
        // Numéro de séquence GLOBAL (00002) malgré une localité différente — pas un « 00001 » partagé.
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":342}"))
                .andExpect(jsonPath("$.refLettre").value("00002/PPM/CRM-TMS/LR/2097"));
    }

    @Test
    @DisplayName("Réf. lettre — incrément continu sans saut ni doublon (5 lettres)")
    void lettre_reference_increment_continu() throws Exception {
        seedExamenAvecRefe(343, "00001/PPM/CRM-ANT/2098");
        for (int i = 1; i <= 5; i++) {
            mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenMembre)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":343}"))
                    .andExpect(jsonPath("$.refLettre").value(String.format("%05d/PPM/CRM-ANT/LR/2098", i)));
        }
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
        d.setIdTypeDossier("PPM");
        d.setRefeDossier("00004/PPM/CRM-ANT/2026");
        dossierRepository.save(d);
        receptionRepository.save(reception(400, 400, "CTRCC1", true));
        dispatchRepository.save(dispatch(400, 400, "CTRCC1", "CTRMEM"));   // Membre attributaire = CTRMEM
        examenRepository.save(examen(400, 400, "CTRMEM"));
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(400); l.setIdDossier(400); l.setObjetLettre("Renvoi"); l.setStatut("SIGNE");
        int idLettre = lettreRenvoiRepository.save(l).getIdLettre();
        int idType = seedTypePiece("Pièce complémentaire", false, "PPM", 1);

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
        mvc.perform(post("/api/demande-retraits").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":120,\"motifRetrait\":\"Erreur de saisie\"}"))
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
        mvc.perform(post("/api/demande-retraits").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":121,\"idPrmp\":\"USURP\",\"motifRetrait\":\"x\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPrmp").value("PRMP001"));
    }

    @Test
    @DisplayName("Demande de retrait — non propriétaire → 403")
    void retrait_creation_nonProprietaire_403() throws Exception {
        // Dossier non possédé par PRMP001 (idPrmp null) → la PRMP connectée n'est pas propriétaire.
        Dossier d = dossier(122, "SOUMIS"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        mvc.perform(post("/api/demande-retraits").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":122,\"motifRetrait\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Demande de retrait — dossier non éligible (EXAMINE) → 409")
    void retrait_creation_dossierNonEligible_409() throws Exception {
        // dossier 1 = EXAMINE (seed), possédé par PRMP001 (via PPM 1).
        mvc.perform(post("/api/demande-retraits").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1,\"motifRetrait\":\"x\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Demande de retrait — doublon EN_ATTENTE → 409")
    void retrait_creation_doublonEnAttente_409() throws Exception {
        Dossier d = dossier(123, "PRET_DISPATCH"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        dossierRepository.save(d);
        mvc.perform(post("/api/demande-retraits").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":123,\"motifRetrait\":\"x\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/demande-retraits").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":123,\"motifRetrait\":\"y\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Décision retrait — CC de la localité accepte → ACCEPTEE, dossier BROUILLON, notif RETRAIT_ACCEPTE")
    void decision_accepter_parCc_dossierBrouillon() throws Exception {
        Dossier d = dossier(130, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        d.setRefeDossier("00002/PPM/CRM-ANT/2026");   // réf. posée à la réception, à invalider au retrait
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
        d.setIdTypeDossier("PPM");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        d.setRefeDossier("00006/PPM/CRM-ANT/2026");   // réf de réception (à invalider)
        dossierRepository.save(d);
        Ppm p135 = ppm(135, 135, "PRMP001"); p135.setReference("00005/DGB/PPM/2026"); ppmRepository.save(p135);
        int drId = demandeRetraitRepository.save(demandeRetrait(0, 135, "PRMP001")).getIdDemandeRetrait();
        mvc.perform(post("/api/demande-retraits/" + drId + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        // Réf. initiale (PPM) restaurée dès le retrait.
        org.junit.jupiter.api.Assertions.assertEquals("00005/DGB/PPM/2026",
                dossierRepository.findById(135).orElseThrow().getRefeDossier());
        // Dossier BROUILLON issu du retrait → édition PPM (en-tête + ligne de marché) acceptée (200).
        String edition = "{\"exercice\":2027,\"signataire\":\"Maj retrait\",\"dateSignature\":\"2026-02-01\","
                + "\"reference\":\"PPM-135-v2\",\"marches\":[{\"montEstim\":500000000,\"idNature\":1,"
                + "\"statut\":\"PREVU\"}]}";
        mvc.perform(put("/api/saisies/ppm/135").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(edition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON"));
    }

    @Test
    @DisplayName("Retrait accepté — l'API renvoie la référence initiale (PPM) du dossier BROUILLON")
    void brouillon_retrait_api_retourne_ref_initiale() throws Exception {
        Dossier d = dossier(140, "SOUMIS"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
        d.setRefeDossier("00002/PPM/CRM-ANT/2026");   // réf de réception (à remplacer)
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
        d.setIdTypeDossier("PPM");
        d.setRefeDossier("00003/PPM/CRM-ANT/2026");   // réf de réception résiduelle
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
    @DisplayName("Dropdown retirables : SOUMIS + PRET_DISPATCH de la PRMP ; exclut EXAMINE et les dossiers d'autrui")
    void retrait_dropdown_retirables() throws Exception {
        Dossier a = dossier(150, "SOUMIS"); a.setIdLocalite("ANT"); a.setIdPrmp("PRMP001"); dossierRepository.save(a);
        Dossier b = dossier(151, "PRET_DISPATCH"); b.setIdLocalite("ANT"); b.setIdPrmp("PRMP001"); dossierRepository.save(b);
        Dossier c = dossier(152, "EXAMINE"); c.setIdLocalite("ANT"); c.setIdPrmp("PRMP001"); dossierRepository.save(c);
        Dossier e = dossier(153, "SOUMIS"); e.setIdLocalite("ANT"); dossierRepository.save(e); // sans propriétaire
        mvc.perform(get("/api/dossiers/retirables").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==150)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==151)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==152)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idDossier==153)]", hasSize(0)));
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
        capmRepository.save(new Capm(1, "LANCEMENT", 1));
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
        capmRepository.save(new Capm(1, "LANCEMENT", 1));
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
        capmRepository.save(new Capm(1, "LANCEMENT", 1));
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
        // Création : le statut envoyé (SIGNE) est ignoré, le PV démarre en BROUILLON.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":1,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"SIGNE\",\"nbNavettes\":99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"))
                .andExpect(jsonPath("$.nbNavettes").value(0));

        soumettre(tokenMembre).andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));

        // Retour interdit au Membre.
        mvc.perform(post("/api/pv-examens/1/retourner").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"x\"}"))
                .andExpect(status().isForbidden());

        // Retour sans commentaire interdit (garde métier).
        mvc.perform(post("/api/pv-examens/1/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isConflict());

        // Retour valide par le CC.
        mvc.perform(post("/api/pv-examens/1/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"commentaire\":\"Corriger la synthèse\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("EN_RECTIFICATION"));

        soumettre(tokenMembre).andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));

        mvc.perform(post("/api/pv-examens/1/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));

        // Une seule signature ne suffit pas.
        signer(tokenMembre, "CTRMEM", "MEMBRE").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));

        // Co-signature → SIGNE.
        signer(tokenPresident, "CTRPRE", "PRESIDENT").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.datePv").isNotEmpty());

        // 4 navettes tracées (SOUMISSION, RETOUR_RECTIF, SOUMISSION, ACCEPTATION).
        mvc.perform(get("/api/pv-navettes").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));

        // PV signé non éditable.
        mvc.perform(put("/api/pv-examens/1").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":1,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"SIGNE\",\"nbNavettes\":4}"))
                .andExpect(status().isConflict());

        // Navette non supprimable.
        mvc.perform(delete("/api/pv-navettes/1").header("Authorization", tokenMembre))
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
    @DisplayName("[Auto] Vérification (FAVR) obs. levées → dossier CLOTURE")
    void auto_cloture() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        // PV FAVR amené à SIGNE → dossier EN_VERIFICATION ; le vérificateur lève les observations → CLOTURE.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":1,\"idExamen\":1,\"idAvis\":\"FAVR\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        soumettre(tokenMembre).andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/1/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isOk());
        signer(tokenMembre, "CTRMEM", "MEMBRE").andExpect(status().isOk());
        signer(tokenPresident, "CTRPRE", "PRESIDENT").andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));

        mvc.perform(post("/api/verifications").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":1,\"obsLevees\":true}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("CLOTURE"));

        // [Auto] Le Chargé de publication est alerté que le dossier clôturé est éligible.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='CLOTURE_ELIGIBLE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='CLOTURE_ELIGIBLE')].destinataireIm", hasItem("CTRPUB")));
    }

    @Test
    @DisplayName("Vérification réservée au profil VÉRIFICATEUR : un CC (profil délégable) → 403")
    void verif_parNonVerificateur_403() throws Exception {
        signerPvAvecAvis(80, "FAVR"); // dossier 1 → EN_VERIFICATION
        mvc.perform(post("/api/verifications").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":80,\"obsLevees\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vérification réservée aux PV FAVR : avis FAV (auto-clôturé) → 409")
    void verif_surAvisNonReserve_409() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(81, "FAV"); // dossier 1 → CLOTURE, PV 81 SIGNE avis FAV
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":81,\"obsLevees\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Vérification obs. non levées → EN_ATTENTE_DECISION_PRMP + notif OBSERVATION_VERIFICATION (PRMP) ; 2e vérification refusée 409")
    void verif_obsNonLevees_attenteDecisionPrmp() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(82, "FAVR"); // dossier 1 → EN_VERIFICATION
        // 1er passage : observations NON levées → dossier EN_ATTENTE_DECISION_PRMP + notif PRMP.
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":82,\"observation\":\"reserve a lever\",\"obsLevees\":false}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
        // La PRMP du dossier reçoit l'observation (refeDossier + texte) via OBSERVATION_VERIFICATION.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='OBSERVATION_VERIFICATION')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='OBSERVATION_VERIFICATION')].destinataireRef", hasItem("PRMP001")));
        // 2e vérification refusée : le dossier n'est plus EN_VERIFICATION (en attente PRMP) → 409.
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":82,\"observation\":\"ok\",\"obsLevees\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Worklist : obs. non levées → dossier dans /en-attente-prmp ET conservé dans /a-verifier (lecture seule), visible PRMP via ?statut")
    void verif_obsNonLevees_attentePrmp_worklist() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(84, "FAVR"); // dossier 1 → EN_VERIFICATION
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":84,\"observation\":\"averina\",\"obsLevees\":false}"))
                .andExpect(status().isCreated());
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
        signerPvAvecAvis(88, "FAVR"); // dossier 1 → EN_VERIFICATION
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":88,\"observation\":\"averina\",\"obsLevees\":false}"))
                .andExpect(status().isCreated()); // → dossier 1 EN_ATTENTE_DECISION_PRMP
        // Le dossier reste dans « à vérifier » mais toute nouvelle vérification est refusée (lecture seule).
        mvc.perform(get("/api/dossiers/a-verifier").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idDossier==1)]", hasSize(1)));
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":88,\"observation\":\"encore\",\"obsLevees\":true}"))
                .andExpect(status().isConflict());
    }

    /** Amène le dossier 1 à EN_ATTENTE_DECISION_PRMP (PV FAVR signé + vérif obsLevees=false par CTRVER). */
    private void dossier1EnAttenteDecisionPrmp(int idPv, String tokenVer) throws Exception {
        signerPvAvecAvis(idPv, "FAVR");
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":" + idPv + ",\"observation\":\"averina\",\"obsLevees\":false}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Resoumission PRMP : EN_ATTENTE_DECISION_PRMP → EN_VERIFICATION + notif vérificateur + audit + motif visible")
    void resoumission_retourEnVerification() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        dossier1EnAttenteDecisionPrmp(85, tokenVer);

        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        // Notif RECTIFICATION_PRMP au vérificateur du dossier (CTRVER).
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='RECTIFICATION_PRMP')].destinataireIm", hasItem("CTRVER")));
        // Motif visible sur le passage côté vérificateur.
        mvc.perform(get("/api/verifications").header("Authorization", tokenVer))
                .andExpect(jsonPath("$[?(@.idPv==85)].motifRectif", hasItem("corrige")));
        // Le vérificateur peut de nouveau vérifier (dossier de retour en EN_VERIFICATION).
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":85,\"observation\":\"ok\",\"obsLevees\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));
    }

    @Test
    @DisplayName("Resoumission PRMP : motif vide → 400")
    void resoumission_motifVide_400() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        dossier1EnAttenteDecisionPrmp(86, tokenVer);
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Resoumission PRMP : dossier hors EN_ATTENTE_DECISION_PRMP (EN_VERIFICATION) → 409")
    void resoumission_horsAttente_409() throws Exception {
        signerPvAvecAvis(87, "FAVR"); // dossier 1 → EN_VERIFICATION (pas EN_ATTENTE)
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Historique d'échanges (dossier clôturé) : observations + rectifications PRMP ; accessible PRMP et vérificateur")
    void historique_echanges_dossierCloture() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(90, "FAVR"); // dossier 1 → EN_VERIFICATION
        // Passage 1 : obs non levées → resoumission (rect1).
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":90,\"observation\":\"obs1\",\"obsLevees\":false}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"rect1\"}"))
                .andExpect(status().isOk());
        // Passage 2 : obs non levées → resoumission (rect2).
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":90,\"observation\":\"obs2\",\"obsLevees\":false}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"rect2\"}"))
                .andExpect(status().isOk());
        // Passage final : obs levées → CLOTURE.
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":90,\"observation\":\"final\",\"obsLevees\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.statut").value("CLOTURE"));

        // Historique : 3 observations (dont la clôture obsLevees=true) + 2 rectifications.
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                // Fil entrelacé (chaîne de réponse) : obs1, rect1, obs2, rect2, final.
                .andExpect(jsonPath("$[0].type").value("OBSERVATION")).andExpect(jsonPath("$[0].texte").value("obs1"))
                .andExpect(jsonPath("$[1].type").value("RECTIFICATION")).andExpect(jsonPath("$[1].texte").value("rect1"))
                .andExpect(jsonPath("$[1].acteur").value("PRMP001"))
                .andExpect(jsonPath("$[2].type").value("OBSERVATION")).andExpect(jsonPath("$[2].texte").value("obs2"))
                .andExpect(jsonPath("$[3].type").value("RECTIFICATION")).andExpect(jsonPath("$[3].texte").value("rect2"))
                .andExpect(jsonPath("$[4].type").value("OBSERVATION")).andExpect(jsonPath("$[4].texte").value("final"))
                .andExpect(jsonPath("$[4].obsLevees").value(true));
        // Accessible aussi par la PRMP.
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("Historique d'échanges : dossier non clôturé (EN_VERIFICATION) → 403")
    void historique_echanges_horsCloture_403() throws Exception {
        signerPvAvecAvis(91, "FAVR"); // dossier 1 → EN_VERIFICATION (pas CLOTURE)
        mvc.perform(get("/api/dossiers/1/historique-echanges").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Vérification : identité enregistrée = JWT (CurrentUser.ref), jamais le corps ; ID auto-généré")
    void verif_identiteDepuisJwt() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(83, "FAVR");
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":83,\"imCtrlVerif\":\"FAKE\",\"obsLevees\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlVerif").value("CTRVER"))
                .andExpect(jsonPath("$.idVerification").isNumber())
                .andExpect(jsonPath("$.dateVerif").isNotEmpty());
    }

    @Test
    @DisplayName("Worklist vérificateur « à-vérifier » : EN_VERIFICATION de la localité ; scope localité respecté")
    void worklist_aVerifier_listeEnVerification() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String tokenVerTms = bearer("CTRVER2", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER2", "TMS");
        signerPvAvecAvis(70, "FAVR"); // dossier 1 (ANT) → EN_VERIFICATION

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
    @DisplayName("Worklist vérificateur « vérifiés » : inclut les dossiers AUTO-CLÔTURÉS (FAV) en lecture seule")
    void worklist_verifies_inclutAutoClotures() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(71, "FAV"); // dossier 1 (ANT) → CLOTURE auto, PV 71 SIGNE

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
    @DisplayName("Circuit complet : Réception → PRET_DISPATCH → Dispatch → Examen → PV(navette → SIGNE) → Vérification → CLOTURE")
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

        // 3) Examen par le Membre.
        mvc.perform(post("/api/examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":3,\"idDispatch\":3,\"imCtrlMembre\":\"CTRMEM\"}"))
                .andExpect(status().isCreated());

        // 4) Projet de PV par le Membre → toujours créé en BROUILLON.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":3,\"idExamen\":3,\"idAvis\":\"FAVR\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPv").value("BROUILLON"));

        // 5) Navette : soumettre → accepter, puis co-signature Membre + Président → SIGNE.
        mvc.perform(post("/api/pv-examens/3/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"))
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));
        mvc.perform(post("/api/pv-examens/3/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        // Un seul signataire ne suffit pas : le PV reste PROJET_ACCEPTE.
        mvc.perform(post("/api/pv-examens/3/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        mvc.perform(post("/api/pv-examens/3/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\"}"))
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));

        // 6) Vérification (FAVR → EN_VERIFICATION) avec observations levées → [Auto] dossier CLOTURE.
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":" + idRec + ",\"idPv\":3,\"obsLevees\":true}"))
                .andExpect(status().isCreated());
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

        // Rôle : un Secrétaire ne peut pas accepter un projet de PV (réservé CC / Président) → 403.
        mvc.perform(post("/api/pv-examens/1/accepter").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRSEC\"}"))
                .andExpect(status().isForbidden());

        // Saut d'étape : un PV en BROUILLON ne peut être ni accepté ni signé → 409.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":4,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/4/accepter").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/pv-examens/4/signer").header("Authorization", tokenMembre)
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
        d.setIdTypeDossier("PPM");
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
        d4.setIdTypeDossier("DAO");
        d4.setIdPrmp("PRMPXX");
        dossierRepository.save(d4);
        // (5) Brouillon DAO de PRMP001, sans localité ni PPM.
        Dossier d5 = dossier(5, "BROUILLON");
        d5.setRefeDossier(null);
        d5.setIdTypeDossier("DAO");
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
        d.setIdTypeDossier("DAO");
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

        capmRepository.save(new Capm(1, "LANCEMENT", 1));
        // Localité dérivée de l'entité 1 (= ANT) ; AUCUN id (dossier/PPM/marché) dans le corps → alloués serveur.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,"
                + "\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-60\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.idTypeDossier").value("PPM"))
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
        capmRepository.save(new Capm(1, "LANCEMENT", 1));
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
    @DisplayName("Saisie PPM — numCompte absent de tr_compte : créé à la volée (pas de 409 FK sur t_marche.NUM_COMPTE)")
    void saisiePpm_compteALaVolee() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1));

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
        capmRepository.save(new Capm(1, "LANCEMENT", 1));
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
        capmRepository.save(new Capm(1, "LANCEMENT", 1));
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
        capmRepository.save(new Capm(1, "LANCEMENT", 1));

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
        autre.setIdTypeDossier("PPM");
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
                .andExpect(jsonPath("$.idTypeDossier").value("DAO"))
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
        dPpmVide.setIdTypeDossier("PPM");
        dPpmVide.setIdPrmp("PRMP001");
        dPpmVide.setIdLocalite("ANT");
        dossierRepository.save(dPpmVide);
        mvc.perform(post("/api/dossiers/63/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict());

        // Brouillon DAO (sans propriétaire) ; y attacher un PPM via l'endpoint Admin → 409.
        Dossier dDao = dossier(64, "BROUILLON");
        dDao.setIdTypeDossier("DAO");
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
        d.setIdTypeDossier("PPM");
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
        d.setIdTypeDossier("DAO");
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
        capmRepository.save(new Capm(1, "LANCEMENT", 1));
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
        String edition = "{\"exercice\":2027,\"signataire\":\"RABE Maj\",\"dateSignature\":\"2026-02-01\",\"reference\":\"PPM-120-v2\","
                + "\"marches\":[{\"idDetail\":" + idM150 + ",\"montEstim\":1500000000,\"idNature\":1,\"idMode\":1,\"statut\":\"PREVU\"},"
                + "{\"montEstim\":500000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\"}]}";
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
        Dossier d = dossier(300, "SOUMIS"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT");
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
    @DisplayName("Référence réception : localité régionale ANT -> 00001/PPM/CRM-ANT/2026")
    void reference_localite_crm() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d = dossier(301, "SOUMIS"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(301, 301, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":301,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("00001/PPM/CRM-ANT/2026"));
    }

    @Test
    @DisplayName("Référence réception : compteur auto-incrémenté par la BDD (00001 puis 00002, même contexte)")
    void reference_incrementee_automatiquement() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d1 = dossier(302, "SOUMIS"); d1.setIdTypeDossier("PPM"); d1.setIdLocalite("ANT"); dossierRepository.save(d1);
        Dossier d2 = dossier(303, "SOUMIS"); d2.setIdTypeDossier("PPM"); d2.setIdLocalite("ANT"); dossierRepository.save(d2);
        ppmRepository.save(ppm(302, 302, "PRMP001"));
        ppmRepository.save(ppm(303, 303, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":302,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00001/PPM/CRM-ANT/2026"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":303,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00002/PPM/CRM-ANT/2026"));
    }

    @Test
    @DisplayName("Référence réception : compteur GLOBAL par année (CRM-ANT, CRM-TMS, CNM → 00001, 00002, 00003)")
    void reference_isolee_par_contexte() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        String tokenSecTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        Dossier ant = dossier(304, "SOUMIS"); ant.setIdTypeDossier("PPM"); ant.setIdLocalite("ANT"); dossierRepository.save(ant);
        Dossier tms = dossier(305, "SOUMIS"); tms.setIdTypeDossier("PPM"); tms.setIdLocalite("TMS"); dossierRepository.save(tms);
        Dossier cnm = dossier(306, "SOUMIS"); cnm.setIdTypeDossier("PPM"); cnm.setIdLocalite("ANT"); dossierRepository.save(cnm);
        ppmRepository.save(ppm(304, 304, "PRMP001"));
        ppmRepository.save(ppm(305, 305, "PRMP001"));
        ppmRepository.save(ppm(306, 306, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":304,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00001/PPM/CRM-ANT/2026"));
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
    @DisplayName("Référence réception : pas de doublon sur 2 réceptions (unicité garantie par l'UPSERT BDD)")
    void reference_concurrence() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d1 = dossier(307, "SOUMIS"); d1.setIdTypeDossier("PPM"); d1.setIdLocalite("ANT"); dossierRepository.save(d1);
        Dossier d2 = dossier(308, "SOUMIS"); d2.setIdTypeDossier("PPM"); d2.setIdLocalite("ANT"); dossierRepository.save(d2);
        ppmRepository.save(ppm(307, 307, "PRMP001"));
        ppmRepository.save(ppm(308, 308, "PRMP001"));

        // L'incrément est fait par la BDD (UPSERT atomique, verrou de ligne) : deux réceptions du même
        // contexte obtiennent des valeurs distinctes -> aucun doublon, même sous concurrence réelle.
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":307,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00001/PPM/CRM-ANT/2026"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":308,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00002/PPM/CRM-ANT/2026"));
    }

    @Test
    @DisplayName("Rectification PPM : PATCH sur dossier EN_ATTENTE_DECISION_PRMP -> 200, champ mis a jour, statut inchange")
    void rectifier_ppm_ok() throws Exception {
        Dossier d = dossier(400, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
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
        Dossier d = dossier(402, "EN_VERIFICATION"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
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
        Dossier d = dossier(403, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
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
        Dossier d = dossier(401, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
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
        Dossier d = dossier(404, "EN_VERIFICATION"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
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
        Dossier d = dossier(405, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
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
        Dossier d = dossier(406, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
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
        Dossier d = dossier(407, "EN_ATTENTE_DECISION_PRMP"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
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
    @DisplayName("PV projets vs definitifs : un PV signe quitte /pv-examens et apparait dans /pv-examens/definitifs")
    void pv_projets_et_definitifs() throws Exception {
        // PV non signé (BROUILLON) sur examen 1.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":96,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        // PV signé (FAV) sur examen 1.
        signerPvAvecAvis(95, "FAV");

        // Projets : contient 96 (BROUILLON), exclut 95 (SIGNE).
        mvc.perform(get("/api/pv-examens").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==96)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPv==95)]", hasSize(0)));
        // Définitifs : contient 95 (SIGNE), exclut 96 (BROUILLON).
        mvc.perform(get("/api/pv-examens/definitifs").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPv==95)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPv==96)]", hasSize(0)));
    }

    @Test
    @DisplayName("PV refePv : derivee de refeDossier (.../YYYY -> .../PV/YYYY)")
    void pv_refePv_generee() throws Exception {
        Dossier d = dossier(500, "EXAMINE"); d.setRefeDossier("00003/PPM/CRM-ANT/2026"); dossierRepository.save(d);
        receptionRepository.save(reception(500, 500, "CTRSEC", true));
        dispatchRepository.save(dispatch(500, 500, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(500, 500, "CTRMEM"));

        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":201,\"idExamen\":500,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refePv").value("00003/PPM/CRM-ANT/PV/2026"));
    }

    @Test
    @DisplayName("PV refePv unique : deux PV sur le meme dossier -> 409")
    void pv_refePv_unique() throws Exception {
        Dossier d = dossier(501, "EXAMINE"); d.setRefeDossier("00007/PPM/CRM-ANT/2026"); dossierRepository.save(d);
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
        Dossier d = dossier(190, "BROUILLON"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
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
        Dossier d = dossier(191, "BROUILLON"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
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
        Dossier d = dossier(600, "BROUILLON"); d.setIdTypeDossier("PPM"); d.setIdLocalite("ANT"); d.setIdPrmp("PRMP001");
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
    @DisplayName("Saisie PPM — processus sans dateFin → 400 (marches[0].processus[0].dateFin)")
    void processus_sans_dateFin_400() throws Exception {
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].processus[0].dateFin')].message",
                        hasItem("La date de fin est obligatoire.")));
    }

    @Test
    @DisplayName("Saisie PPM — marché + processus complets → 201 + prévisions triées par ordre CAPM")
    void brouillon_avec_processus_ok() throws Exception {
        // Mode déterminable (évite la notif MODE_NON_DETERMINE, hors sujet) : 500M → AOR (mode 2).
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1));
        capmRepository.save(new Capm(3, "OUVERTURE", 3));
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
        capmRepository.save(new Capm(1, "LANCEMENT", 1));
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
        capmRepository.save(new Capm(1, "LANCEMENT", 1));
        capmRepository.save(new Capm(2, "DAO", 2));
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
        capmRepository.save(new Capm(1, "LANCEMENT", 1));
        capmRepository.save(new Capm(2, "DAO", 2));
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
        pc.setIdPointCtrl(1); pc.setLibelPointCtrl("Montant"); pc.setObligatoire(true); pc.setIdTypeDossier("PPM");
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
        pc.setIdPointCtrl(1); pc.setLibelPointCtrl("Montant"); pc.setObligatoire(true); pc.setIdTypeDossier("PPM");
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
    @DisplayName("Soumission examen sans secrétaire de séance → 400")
    void soumission_examen_sans_secretaire_400() throws Exception {
        mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idAvis\":\"FAV\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("idSecretaireSeance"));
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
                "00007/PPM/CRM-ANT/PV/2026",               // refPv
                java.time.LocalDate.of(2026, 6, 15),       // date de réception
                "Ministère de l'Économie et des Finances", // entité contractante
                2026,                                       // exercice
                "ANTANANARIVO",                             // localité (libellé)
                nomPresident, nomChefCommission,
                "Paul MEMBRE", "Vero VERIFICATEUR", observations);
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
    @DisplayName("PvExamenDto.documentDisponible : true dès FAVR+ANT+PPM quel que soit le mode (cotation incluse) ; false si avis ≠ FAVR")
    void pv_documentDisponible_refleteEligibilite() throws Exception {
        modePassationRepository.save(new ModePassation(5, "Demande de cotation", null, null, null, null));

        // ÉLIGIBLE MALGRÉ un marché en « Demande de cotation » : le mode ne conditionne plus l'éligibilité AFSR.
        cnm.prs.entity.Marche mCot = marche(9600, 1, 1); mCot.setIdMode(5); marcheRepository.save(mCot);
        cnm.prs.entity.PvExamen pvFavr = new cnm.prs.entity.PvExamen();
        pvFavr.setIdPv(600); pvFavr.setIdExamen(1); pvFavr.setIdAvis("FAVR"); pvFavr.setImCtrlMembre("CTRMEM");
        pvFavr.setStatutPv("SIGNE"); pvFavr.setNbNavettes(0);
        pvExamenRepository.save(pvFavr);

        // NON ÉLIGIBLE pour un vrai motif : avis DEF (≠ FAVR) — nouvelle chaîne centrale/PPM.
        dossierRepository.save(dossierLoc(502, "EXAMINE", "ANT", "PRMP001"));
        receptionRepository.save(reception(502, 502, "CTRCC1", true));   // CTRCC1 = localité ANT
        dispatchRepository.save(dispatch(502, 502, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(502, 502, "CTRMEM"));
        ppmRepository.save(ppm(502, 502, "PRMP001"));
        marcheRepository.save(marche(9601, 502, 502));
        cnm.prs.entity.PvExamen pvDef = new cnm.prs.entity.PvExamen();
        pvDef.setIdPv(601); pvDef.setIdExamen(502); pvDef.setIdAvis("DEF"); pvDef.setImCtrlMembre("CTRMEM");
        pvDef.setStatutPv("SIGNE"); pvDef.setNbNavettes(0);
        pvExamenRepository.save(pvDef);

        // FAVR + ANT + PPM + cotation → documentDisponible = true (cas signalé 00008/PPM/CRM-ANT/PV/2026).
        mvc.perform(get("/api/pv-examens/600").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentDisponible").value(true));
        // Avis DEF → non éligible (le modèle AFSR ne couvre que le FAVR).
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
        mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numCompte").value("CPT-BENEF-01"))
                .andExpect(jsonPath("$.soaCode").value("00-21-0-J00-00000"));

        // Relecture : compte + code SOA long persistés et exposés.
        mvc.perform(get("/api/service-beneficiaires/9700").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numCompte").value("CPT-BENEF-01"))
                .andExpect(jsonPath("$.soaCode").value("00-21-0-J00-00000"));
    }

    @Test
    @DisplayName("DELETE /api/marches/{id} : cascade les bénéficiaires (t_service_beneficiaire) — plus de 409 FK")
    void suppressionMarche_cascadeBeneficiaires() throws Exception {
        Dossier d = dossierLoc(810, "BROUILLON", "ANT", "PRMP001"); d.setIdTypeDossier("PPM");
        dossierRepository.save(d);
        ppmRepository.save(ppm(810, 810, "PRMP001"));
        marcheRepository.save(marche(9810, 810, 810));
        compteRepository.save(new cnm.prs.entity.Compte("CPT-810", "Compte", null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-21-0-J00-00000", "SOA"));
        // Bénéficiaire rattaché au marché 9810 → sans cascade, DELETE renverrait 409 (FK).
        mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idBenef\":9810,\"idDetail\":9810,\"soaCode\":\"00-21-0-J00-00000\","
                        + "\"numCompte\":\"CPT-810\",\"ancMontBenef\":1000000}"))
                .andExpect(status().isCreated());

        // Suppression du marché → 204 (cascade en transaction), pas de 409.
        mvc.perform(delete("/api/marches/9810").header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        // Le bénéficiaire a été supprimé en cascade ; le marché a disparu.
        mvc.perform(get("/api/service-beneficiaires/9810").header("Authorization", tokenPrmp))
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
        // Non-admin → 403 (sous-chemin sécurisé par @PreAuthorize).
        mvc.perform(get("/api/controleurs/CTRPHO2/pieces/PHOTO").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
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
    private void signerPvEligible(int idPv) throws Exception {
        modePassationRepository.save(new ModePassation(1, "AOO", null, null, null, null));
        cnm.prs.entity.Marche m = marche(9500, 1, 1);   // dossier 1, ppm 1
        m.setIdMode(1);                                  // appel d'offres ouvert
        marcheRepository.save(m);
        signerPvAvecAvis(idPv, "FAVR");                  // → SIGNE → génération du document si éligible
    }

    @Test
    @DisplayName("Signature PV éligible → document généré et stocké (chemin_document non NULL + fichier présent)")
    void signature_pv_genere_document_ok() throws Exception {
        signerPvEligible(110);
        cnm.prs.entity.PvExamen pv = pvExamenRepository.findById(110).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(pv.getCheminDocument(),
                "chemin_document renseigné après la signature finale");
        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of(pv.getCheminDocument())),
                "le fichier PDF est présent sur le FSX");
    }

    @Test
    @DisplayName("Téléchargement PV après signature → 200 application/pdf")
    void document_pv_telechargement_ok() throws Exception {
        signerPvEligible(111);
        var resp = mvc.perform(get("/api/pv-examens/111/document").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertEquals(MediaType.APPLICATION_PDF_VALUE, resp.getContentType());
        assertTrue(resp.getContentAsByteArray().length > 0, "le PDF n'est pas vide");
    }

    @Test
    @DisplayName("PV signé sans document (ancien) → régénération paresseuse au téléchargement → 200")
    void migration_pv_anciens_sans_document() throws Exception {
        signerPvEligible(112);
        cnm.prs.entity.PvExamen pv = pvExamenRepository.findById(112).orElseThrow();
        pv.setCheminDocument(null);            // simule un PV signé avant le correctif (chemin_document NULL)
        pvExamenRepository.save(pv);
        mvc.perform(get("/api/pv-examens/112/document").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertNotNull(
                pvExamenRepository.findById(112).orElseThrow().getCheminDocument(),
                "chemin_document régénéré à la demande");
    }

    @Test
    @DisplayName("Grille de contrôle — point « Conformité au budget » non conforme → observations chargées (>= 1)")
    void pv_detail_observations_chargees() throws Exception {
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(1);
        pc.setLibelPointCtrl("Conformité au budget");
        pc.setObligatoire(true);
        pc.setIdTypeDossier("PPM");
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
    @DisplayName("Lettre de renvoi — création pendant l'examen (Membre, objetLettre ignoré) → 201 BROUILLON")
    void lettre_creation_pendant_examen_ok() throws Exception {
        // objetLettre encore envoyé par un ancien frontend : ignoré (compat rétroactive), pas d'erreur.
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenMembre)
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
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenMembre)
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
        d.setRefeDossier("00007/PPM/CRM-ANT/2026");
        dossierRepository.save(d);
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1,\"objetLettre\":\"Renvoi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refLettre").value("00001/PPM/CRM-ANT/LR/2026"));
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
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1,\"objetLettre\":\"Lettre 1\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenMembre)
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
    @DisplayName("PV signé avis DÉFAVORABLE → Assistant contrôleur notifié (PV_DEFINITIF_COPIE)")
    void pv_signe_avis_defav_assistant_notifie() throws Exception {
        signerPvAvecAvis(120, "DEF");
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_DEFINITIF_COPIE')].destinataireIm", hasItem("CTRASS")));
    }

    @Test
    @DisplayName("PV signé avis FAVR → Assistant contrôleur NON notifié (pas de PV_DEFINITIF_COPIE)")
    void pv_signe_avis_favr_assistant_non_notifie() throws Exception {
        signerPvAvecAvis(121, "FAVR");
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_DEFINITIF_COPIE')]", hasSize(0)));
    }

    @Test
    @DisplayName("Dossier clôturé après vérification (FAVR) → Assistant contrôleur notifié (CLOTURE_COPIE_ASSISTANT)")
    void dossier_cloture_assistant_notifie() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        signerPvAvecAvis(122, "FAVR");   // dossier 1 → EN_VERIFICATION
        mvc.perform(post("/api/verifications").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idReception\":1,\"idPv\":122,\"observation\":\"ok\",\"obsLevees\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='CLOTURE_COPIE_ASSISTANT')].destinataireIm", hasItem("CTRASS")));
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
    @DisplayName("Document : PDF stocké sur le FSX (répertoire LR/) à la signature")
    void document_genere_stocke_fsx_ok() throws Exception {
        int id = seedLettreSoumiseLoc(730, "ANT");
        mvc.perform(post("/api/lettre-renvois/" + id + "/signer").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        String chemin = lettreRenvoiRepository.findById(id).orElseThrow().getCheminDocument();
        assertTrue(chemin != null && java.nio.file.Files.exists(java.nio.file.Path.of(chemin)),
                "fichier PDF présent sur le FSX : " + chemin);
        assertTrue(chemin.endsWith("00007_PPM_CRM-ANT_LR_2026.pdf"),
                "nom de fichier dérivé de refLettre avec '/' remplacés par '_'");
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
        l.setRefLettre("00007/PPM/CRM-" + localite + "/LR/2026");   // contient des '/' (à nettoyer dans le nom de fichier)
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
                + "\"idTypeDossier\":\"PPM\",\"ordre\":1}";
        String json = mvc.perform(post("/api/type-piece-jointes").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypePiece").isNumber())
                .andExpect(jsonPath("$.libellePiece").value("Plan de passation"))
                .andReturn().getResponse().getContentAsString();
        int id = com.jayway.jsonpath.JsonPath.parse(json).read("$.idTypePiece");

        // Mise à jour.
        String maj = "{\"libellePiece\":\"Plan de passation des marchés\",\"obligatoire\":false,"
                + "\"idTypeDossier\":\"PPM\",\"ordre\":2}";
        mvc.perform(put("/api/type-piece-jointes/" + id).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(maj))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libellePiece").value("Plan de passation des marchés"))
                .andExpect(jsonPath("$.obligatoire").value(false));

        // Filtre par type de dossier (authentifié).
        mvc.perform(get("/api/type-piece-jointes?typeDossier=PPM").header("Authorization", tokenPrmp))
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
        Dossier d = dossier(140, "BROUILLON"); d.setIdTypeDossier("PPM"); d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT"); dossierRepository.save(d);
        int type = seedTypePiece("Plan de passation", true, "PPM", 1);

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
        Dossier d = dossier(141, "BROUILLON"); d.setIdTypeDossier("PPM"); d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT"); dossierRepository.save(d);
        int type = seedTypePiece("Plan de passation", true, "PPM", 1);

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
        Dossier d = dossier(142, "SOUMIS"); d.setIdTypeDossier("PPM"); d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT"); dossierRepository.save(d);
        int type = seedTypePiece("Avis de non-objection", false, "PPM", 1);
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
        Dossier d = dossier(143, "BROUILLON"); d.setRefeDossier(null); d.setIdTypeDossier("PPM");
        d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT"); dossierRepository.save(d);
        Ppm ppm = ppmLocalise(43, 143, "ANT"); ppm.setIdPrmp("PRMP001"); ppmRepository.save(ppm);
        marcheRepository.save(marche(431, 143, 43));
        seedTypePiece("Plan de passation des marchés", true, "PPM", 1); // obligatoire, non fournie

        mvc.perform(post("/api/dossiers/143/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("piecesJointes"))
                .andExpect(jsonPath("$.erreurs[0].message")
                        .value("La pièce 'Plan de passation des marchés' est obligatoire."));
    }

    @Test
    @DisplayName("Téléchargement du contenu d'une pièce → 200 + octets identiques")
    void piece_download_ok() throws Exception {
        Dossier d = dossier(144, "BROUILLON"); d.setIdTypeDossier("PPM"); d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT"); dossierRepository.save(d);
        int type = seedTypePiece("Plan de passation", true, "PPM", 1);

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
        mvc.perform(get("/api/type-piece-jointes?typeDossier=PPM").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    @DisplayName("Référentiel pièces jointes : 8 pièces pour le type DAO (filtre ?typeDossier=DAO)")
    void type_piece_dao_liste_ok() throws Exception {
        seedReferentielPieces();
        mvc.perform(get("/api/type-piece-jointes?typeDossier=DAO").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));
    }

    @Test
    @DisplayName("Référentiel pièces jointes : 7 pièces pour le type MAOO (filtre ?typeDossier=MAOO)")
    void type_piece_maoo_liste_ok() throws Exception {
        seedReferentielPieces();
        mvc.perform(get("/api/type-piece-jointes?typeDossier=MAOO").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)));
    }

    @Test
    @DisplayName("Saisie PPM (multipart) : pièce obligatoire absente → 400 {champ:piecesJointes}")
    void creation_sans_piece_obligatoire_400() throws Exception {
        seedTypePiece("Plan de passation des marchés signé", true, "PPM", 1); // obligatoire
        int opt = seedTypePiece("Avis de non-objection (si requis)", false, "PPM", 2); // optionnelle

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
        int oblig = seedTypePiece("Plan de passation des marchés signé", true, "PPM", 1);
        int opt = seedTypePiece("Avis de non-objection (si requis)", false, "PPM", 2);

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
                .andExpect(jsonPath("$.idTypeDossier").value("PPM"));
    }

    @Test
    @DisplayName("Saisie PPM (multipart) : pièce optionnelle omise mais obligatoire fournie → 201")
    void creation_sans_piece_optionnelle_ok() throws Exception {
        int oblig = seedTypePiece("Plan de passation des marchés signé", true, "PPM", 1);
        seedTypePiece("Avis de non-objection (si requis)", false, "PPM", 2); // optionnelle, non fournie

        byte[] pdf = "%PDF-1.4 piece".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart("/api/saisies/ppm")
                .file(new MockMultipartFile("data", "", "application/json",
                        "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\"}"
                                .getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("piece_" + oblig, "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.idTypeDossier").value("PPM"));
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
     * Garnit le référentiel H2 avec le jeu initial complet (20 lignes : PPM 5, DAO 8, MAOO 7),
     * miroir de la migration {@code 2026-06-26_type_piece_jointe_seed.sql}. Le type de dossier MAOO
     * (absent du seed de base) est ajouté pour satisfaire la FK {@code tr_type_dossier}.
     */
    private void seedReferentielPieces() {
        typeDossierRepository.save(new TypeDossier("MAOO", "Marché par appel d'offres ouvert"));
        // PPM (5)
        seedTypePiece("Plan de passation des marchés signé", true, "PPM", 1);
        seedTypePiece("Budget prévisionnel de l'exercice", true, "PPM", 2);
        seedTypePiece("Arrêté ou décision portant nomination de la PRMP", true, "PPM", 3);
        seedTypePiece("Tableau récapitulatif des marchés", true, "PPM", 4);
        seedTypePiece("Avis de non-objection (si requis)", false, "PPM", 5);
        // DAO (8)
        seedTypePiece("Dossier d'appel d'offres complet", true, "DAO", 1);
        seedTypePiece("Cahier des clauses administratives générales", true, "DAO", 2);
        seedTypePiece("Cahier des clauses techniques particulières", true, "DAO", 3);
        seedTypePiece("Avis d'appel d'offres", true, "DAO", 4);
        seedTypePiece("Estimation du coût des travaux/fournitures", true, "DAO", 5);
        seedTypePiece("Garantie de soumission", true, "DAO", 6);
        seedTypePiece("Avis de non-objection (si requis)", false, "DAO", 7);
        seedTypePiece("Rapport d'évaluation des offres", false, "DAO", 8);
        // MAOO (7)
        seedTypePiece("Projet de marché signé", true, "MAOO", 1);
        seedTypePiece("Cahier des charges", true, "MAOO", 2);
        seedTypePiece("Devis estimatif détaillé", true, "MAOO", 3);
        seedTypePiece("Procès-verbal d'ouverture des offres", true, "MAOO", 4);
        seedTypePiece("Rapport d'analyse des offres", true, "MAOO", 5);
        seedTypePiece("Attestation de capacité financière", false, "MAOO", 6);
        seedTypePiece("Avis de non-objection (si requis)", false, "MAOO", 7);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions soumettre(String token) throws Exception {
        return mvc.perform(post("/api/pv-examens/1/soumettre").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions signer(String token, String acteur, String role)
            throws Exception {
        return mvc.perform(post("/api/pv-examens/1/signer").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"" + acteur + "\",\"role\":\"" + role + "\"}"));
    }

    private String bearer(String login, ProfilUtilisateur role, TypeActeur type, String ref, String loc) {
        return "Bearer " + tokenService.generer(login, role.name(), type, ref, loc);
    }

    private Localite localite(String id, String libelle) {
        Localite l = new Localite();
        l.setIdLocalite(id);
        l.setLibelleLocalite(libelle);
        l.setReferencement("REF-" + id);
        l.setLocalite(id);
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
}
