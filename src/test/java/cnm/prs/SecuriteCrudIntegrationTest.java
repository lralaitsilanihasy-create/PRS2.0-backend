package cnm.prs;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Avis;
import cnm.prs.entity.Capm;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.CopieDossier;
import cnm.prs.entity.Dispatch;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Echeance;
import cnm.prs.entity.Examen;
import cnm.prs.entity.IndicateurPrmp;
import cnm.prs.entity.Localite;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Profile;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.PvNavette;
import cnm.prs.entity.Reception;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.entity.Tranche;
import cnm.prs.entity.TypeDmc;
import cnm.prs.entity.TypeDossier;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDmc;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.AvisRepository;
import cnm.prs.repository.CapmRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.CopieDossierRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EcheanceRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.IndicateurPrmpRepository;
import cnm.prs.repository.LocaliteRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.ProfileRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.PvNavetteRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.ServiceBeneficiaireRepository;
import cnm.prs.repository.TrancheRepository;
import cnm.prs.repository.TypeDmcRepository;
import cnm.prs.repository.TypeDossierRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.security.TokenService;

/**
 * ⚠️ LOT 3a (2026-08-26) — matrice d'autorisation des CRUD génériques fermés par ce lot
 * (plan de travaux §3.1). Avant ce lot, <strong>tout authentifié</strong> — y compris une PRMP,
 * acteur externe — lisait et écrivait les lots, tranches, bénéficiaires, prévisions, échéances,
 * copies, navettes, anomalies et indicateurs de <em>n'importe quel</em> dossier.
 *
 * <p>Chaque ressource fermée est couverte par au moins : une lecture depuis une PRMP
 * <strong>étrangère</strong> (liste scopée / 403), une écriture depuis un profil non autorisé (403),
 * et — pour les enfants de saisie PPM — la <strong>non-régression du flux réel</strong> du modal
 * d'édition d'un brouillon (PRMP propriétaire + BROUILLON → 201/200/204, et 409 hors brouillon).</p>
 *
 * <p>Fixtures : trois dossiers — 5001 BROUILLON de PRMP001 (ANT), 5002 BROUILLON de PRMP002 (TMS),
 * 5003 EXAMINE de PRMP001 (ANT, réceptionné en ANT, porteur du circuit navette/copie).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecuriteCrudIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TokenService tokenService;

    @Autowired private LocaliteRepository localiteRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private ControleurRepository controleurRepository;
    @Autowired private PrmpRepository prmpRepository;
    @Autowired private TypeDossierRepository typeDossierRepository;
    @Autowired private DossierRepository dossierRepository;
    @Autowired private PpmRepository ppmRepository;
    @Autowired private MarcheRepository marcheRepository;
    @Autowired private LotRepository lotRepository;
    @Autowired private TrancheRepository trancheRepository;
    @Autowired private ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    @Autowired private MarchePrevisionRepository marchePrevisionRepository;
    @Autowired private EcheanceRepository echeanceRepository;
    @Autowired private CapmRepository capmRepository;
    @Autowired private AvisRepository avisRepository;
    @Autowired private ReceptionRepository receptionRepository;
    @Autowired private DispatchRepository dispatchRepository;
    @Autowired private ExamenRepository examenRepository;
    @Autowired private PvExamenRepository pvExamenRepository;
    @Autowired private PvNavetteRepository pvNavetteRepository;
    @Autowired private CopieDossierRepository copieDossierRepository;
    @Autowired private IndicateurPrmpRepository indicateurPrmpRepository;
    @Autowired private TypeDmcRepository typeDmcRepository;
    @Autowired private DossierMecRepository dossierMecRepository;
    @Autowired private TypePieceJointeRepository typePieceJointeRepository;
    @Autowired private PieceJointeDossierRepository pieceJointeDossierRepository;

    private String tokenPresident;
    private String tokenCc;
    private String tokenMembre;
    private String tokenAdmin;
    /** PRMP propriétaire des dossiers 5001 (BROUILLON) et 5003 (EXAMINE), localité ANT. */
    private String tokenPrmp1;
    /** PRMP <strong>étrangère</strong> : propriétaire du seul dossier 5002 (BROUILLON), localité TMS. */
    private String tokenPrmp2;
    /** ⚠️ C1 — pièce jointe du BROUILLON 5001 (PRMP001) : un brouillon est masqué aux contrôleurs. */
    private int idPieceBrouillon;
    /** ⚠️ C1 — pièce jointe du dossier 5003 EXAMINE (PRMP001, réceptionné en ANT). */
    private int idPieceExaminee;

    @BeforeEach
    void seed() {
        localiteRepository.save(localite("ANT", "Antananarivo"));
        localiteRepository.save(localite("TMS", "Toamasina"));
        typeDossierRepository.save(new TypeDossier("DDP", "Dossier de Planification"));

        profileRepository.save(profile(2, "Président"));
        profileRepository.save(profile(3, "Chef de commission"));
        profileRepository.save(profile(5, "Membre"));
        profileRepository.save(profile(8, "Administrateur"));
        controleurRepository.save(controleur("CTRPRE", 2, null));
        controleurRepository.save(controleur("CTRCC1", 3, "ANT"));
        controleurRepository.save(controleur("CTRMEM", 5, "ANT"));
        controleurRepository.save(controleur("CTRADM", 8, "ANT"));

        prmpRepository.save(prmp("PRMP001"));
        prmpRepository.save(prmp("PRMP002"));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        capmRepository.save(new Capm(2, "OUVERTURE", 2, null, null));

        // --- Dossier 5001 : BROUILLON de PRMP001 (ANT), avec toute sa descendance de saisie.
        dossierRepository.save(dossier(5001, "BROUILLON", "ANT", "PRMP001"));
        ppmRepository.save(ppm(5001, 5001, "PRMP001"));
        marcheRepository.save(marche(5101, 5001, 5001));
        lotRepository.save(lot(5201, 5001, 5101));
        trancheRepository.save(tranche(5601, 5201));
        serviceBeneficiaireRepository.save(beneficiaire(5501, 5101));
        marchePrevisionRepository.save(prevision(5301, 5101, 1));
        echeanceRepository.save(echeance(5401, 5101));

        // --- Dossier 5002 : BROUILLON de PRMP002 (TMS) — le dossier « d'autrui ».
        dossierRepository.save(dossier(5002, "BROUILLON", "TMS", "PRMP002"));
        ppmRepository.save(ppm(5002, 5002, "PRMP002"));
        marcheRepository.save(marche(5102, 5002, 5002));
        lotRepository.save(lot(5202, 5002, 5102));
        marchePrevisionRepository.save(prevision(5302, 5102, 1));

        // --- Dossier 5003 : EXAMINE de PRMP001 (ANT) — plus un brouillon, et porteur du circuit interne.
        dossierRepository.save(dossier(5003, "EXAMINE", "ANT", "PRMP001"));
        ppmRepository.save(ppm(5003, 5003, "PRMP001"));
        marcheRepository.save(marche(5103, 5003, 5003));
        lotRepository.save(lot(5203, 5003, 5103));
        avisRepository.save(avis("FAV", "Favorable"));
        receptionRepository.save(reception(5003, 5003, "CTRCC1"));
        dispatchRepository.save(dispatch(5003, 5003, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(5003, 5003, "CTRMEM"));
        pvExamenRepository.save(pvExamen(5003, 5003));
        pvNavetteRepository.save(navette(5703, 5003));
        copieDossierRepository.save(copie(5803, 5003, 5003));

        indicateurPrmpRepository.save(indicateur(5901, "PRMP001"));
        indicateurPrmpRepository.save(indicateur(5902, "PRMP002"));

        // ⚠️ Audit 2026-08-27 (C1) — une pièce jointe sur le brouillon 5001 et une sur le dossier
        // examiné 5003, pour éprouver le périmètre de LECTURE (liste, unitaire, contenu binaire).
        int typePiece = typePieceJointeRepository
                .save(new TypePieceJointe(null, "Plan de passation des marches", null, true, "DDP", 1, null))
                .getIdTypePiece();
        idPieceBrouillon = pieceJointeDossierRepository.save(pieceJointe(5001, typePiece)).getIdPiece();
        idPieceExaminee = pieceJointeDossierRepository.save(pieceJointe(5003, typePiece)).getIdPiece();

        tokenPresident = bearer("CTRPRE", ProfilUtilisateur.PRESIDENT, TypeActeur.CONTROLEUR, "CTRPRE", null);
        tokenCc = bearer("CTRCC1", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC1", "ANT");
        tokenMembre = bearer("CTRMEM", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM", "ANT");
        tokenAdmin = bearer("CTRADM", ProfilUtilisateur.ADMINISTRATEUR, TypeActeur.CONTROLEUR, "CTRADM", "ANT");
        tokenPrmp1 = bearer("PRMP001", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP001", "ANT");
        tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "TMS");
    }

    // ------------------------------------------------------------------
    // A — Enfants de saisie PPM : lots
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Lots §1/§3.1 — une PRMP étrangère ne voit que SES lots dans la liste, et reçoit 403 sur le dossier d'autrui")
    void lots_prmpEtrangere_listeScopeeEt403() throws Exception {
        // PRMP002 ne voit que le lot de son propre dossier (5202), jamais ceux de PRMP001 (5201 / 5203).
        mvc.perform(get("/api/lots").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idLot==5202)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idLot==5201)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idLot==5203)]", hasSize(0)));

        // Accès unitaire au lot d'autrui → 403 (et non un 200 silencieux comme avant le LOT 3a).
        mvc.perform(get("/api/lots/5201").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/lots/par-dossier/5001").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/lots/par-marche/5101").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lots §1 — le Membre voit les lots des dossiers soumis de SA localité, jamais les brouillons")
    void lots_controleur_scopeParLocaliteSansBrouillon() throws Exception {
        mvc.perform(get("/api/lots").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idLot==5203)]", hasSize(1))) // dossier 5003 EXAMINE, ANT
                .andExpect(jsonPath("$[?(@.idLot==5201)]", hasSize(0))) // dossier 5001 BROUILLON → masqué
                .andExpect(jsonPath("$[?(@.idLot==5202)]", hasSize(0))); // dossier 5002 : autre localité
    }

    @Test
    @DisplayName("Lots §3.1 — NON-RÉGRESSION du modal d'édition : la PRMP propriétaire crée, modifie et supprime un lot de SON brouillon")
    void lots_prmpProprietaireSurBrouillon_crudComplet() throws Exception {
        String corps = "{\"idLot\":5210,\"idDossier\":5001,\"idDetail\":5101,\"designationLot\":\"Lot ajoute\","
                + "\"montLot\":1000000,\"qteLot\":2,\"uniteLot\":\"U\"}";
        mvc.perform(post("/api/lots").header("Authorization", tokenPrmp1)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLot").value(5210));

        mvc.perform(put("/api/lots/5210").header("Authorization", tokenPrmp1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idLot\":5210,\"idDossier\":5001,\"idDetail\":5101,\"designationLot\":\"Lot modifie\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designationLot").value("Lot modifie"));

        mvc.perform(delete("/api/lots/5210").header("Authorization", tokenPrmp1))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Lots §3.1 — écriture sur le brouillon d'autrui → 403 ; sur son propre dossier qui n'est plus BROUILLON → 409")
    void lots_ecriture_403HorsPerimetre_409HorsBrouillon() throws Exception {
        // PRMP002 tente de modifier le lot du brouillon de PRMP001 → 403 (propriété).
        mvc.perform(put("/api/lots/5201").header("Authorization", tokenPrmp2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idLot\":5201,\"idDossier\":5001,\"idDetail\":5101,\"designationLot\":\"Pirate\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/lots/5201").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());

        // PRMP001 sur SON dossier 5003, mais il est EXAMINE → 409 (la structure d'un dossier soumis est figée).
        mvc.perform(put("/api/lots/5203").header("Authorization", tokenPrmp1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idLot\":5203,\"idDossier\":5003,\"idDetail\":5103,\"designationLot\":\"Trop tard\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/lots/5203").header("Authorization", tokenPrmp1))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Lots §3.1 — un contrôleur (Membre, Chef de commission) n'écrit jamais dans un PPM : 403")
    void lots_ecritureParControleur_403() throws Exception {
        String corps = "{\"idLot\":5211,\"idDossier\":5001,\"idDetail\":5101,\"designationLot\":\"Lot CC\"}";
        mvc.perform(post("/api/lots").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/lots").header("Authorization", tokenCc)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/lots/5201").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Lots §3.1 — un lot ne peut pas être rattaché au marché d'autrui en déclarant son propre dossier")
    void lots_rattachementCroise_403() throws Exception {
        // idDossier = son brouillon à elle, mais idDetail = la ligne de marché de PRMP001 → refusé.
        mvc.perform(post("/api/lots").header("Authorization", tokenPrmp2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idLot\":5212,\"idDossier\":5002,\"idDetail\":5101,\"designationLot\":\"Croise\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // A — Enfants de saisie PPM : prévisions, tranches, bénéficiaires
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Dates prévisionnelles §1/§3.1 — liste scopée pour la PRMP étrangère, 403 sur le marché d'autrui")
    void previsions_lectureScopee() throws Exception {
        mvc.perform(get("/api/marche-previsions").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrevision==5302)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPrevision==5301)]", hasSize(0)));

        mvc.perform(get("/api/marche-previsions?marche=5101").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/marche-previsions/5301").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Dates prévisionnelles §3.1 — NON-RÉGRESSION : la PRMP propriétaire crée/modifie/supprime sur SON brouillon ; 403 sur celui d'autrui")
    void previsions_ecriture_proprietaireOkEtranger403() throws Exception {
        mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idPrevision\":5310,\"idDetail\":5101,\"idCapm\":2,\"dateDebut\":\"2026-05-01\","
                                + "\"dateFin\":\"2026-05-31\"}"))
                .andExpect(status().isCreated());

        mvc.perform(put("/api/marche-previsions/5310").header("Authorization", tokenPrmp1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idPrevision\":5310,\"idDetail\":5101,\"idCapm\":2,\"dateDebut\":\"2026-06-01\","
                                + "\"dateFin\":\"2026-06-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateDebut").value("2026-06-01"));

        mvc.perform(delete("/api/marche-previsions/5310").header("Authorization", tokenPrmp1))
                .andExpect(status().isNoContent());

        // La même écriture, depuis la PRMP étrangère, sur le marché de PRMP001 → 403.
        mvc.perform(post("/api/marche-previsions").header("Authorization", tokenPrmp2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idPrevision\":5311,\"idDetail\":5101,\"idCapm\":2,\"dateDebut\":\"2026-05-01\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/marche-previsions/5301").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tranches §1/§3.1 — lecture scopée, écriture refusée à la PRMP étrangère (403) et au Membre (403)")
    void tranches_lectureEtEcritureFermees() throws Exception {
        mvc.perform(get("/api/tranches").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idTranche==5601)]", hasSize(0)));
        mvc.perform(get("/api/tranches/5601").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());

        String corps = "{\"idTranche\":5610,\"idLot\":5201,\"lieuTrc\":\"Antananarivo\",\"montTrc\":500000}";
        mvc.perform(post("/api/tranches").header("Authorization", tokenPrmp2)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tranches").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());

        // La PRMP propriétaire, sur SON brouillon, passe (non-régression de la saisie).
        mvc.perform(post("/api/tranches").header("Authorization", tokenPrmp1)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Bénéficiaires §1/§3.1 — lecture scopée, écriture refusée hors périmètre (403) et au Membre (403)")
    void beneficiaires_lectureEtEcritureFermees() throws Exception {
        mvc.perform(get("/api/service-beneficiaires").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idBenef==5501)]", hasSize(0)));
        mvc.perform(get("/api/service-beneficiaires/5501").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());

        String corps = "{\"idBenef\":5510,\"idDetail\":5101,\"ancMontBenef\":1000000}";
        mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenPrmp2)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/service-beneficiaires").header("Authorization", tokenPrmp1)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // B — Échéances
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Échéances Module 04 — la PRMP lit le calendrier de SES marchés seulement ; l'écriture est réservée à l'Administrateur")
    void echeances_lectureScopeeEcritureAdmin() throws Exception {
        // La PRMP propriétaire voit son jalon ; l'étrangère ne le voit pas.
        mvc.perform(get("/api/echeances").header("Authorization", tokenPrmp1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idEcheance==5401)]", hasSize(1)));
        mvc.perform(get("/api/echeances").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idEcheance==5401)]", hasSize(0)));
        mvc.perform(get("/api/echeances/5401").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());

        String corps = "{\"idEcheance\":5410,\"idDetail\":5101,\"typeJalon\":\"LANCEMENT\",\"datePrevue\":\"2026-09-01\"}";
        mvc.perform(post("/api/echeances").header("Authorization", tokenPrmp1)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/echeances").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/echeances").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // B bis — Pièces jointes de dossier (⚠️ audit 2026-08-27, constat C1)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Pièces jointes C1 §1/§3.1 — la PRMP étrangère n'obtient ni la liste, ni la fiche, ni le CONTENU binaire d'une pièce d'autrui (403)")
    void piecesJointes_prmpEtrangere_403SurLesTroisLectures() throws Exception {
        // Avant le correctif, ces trois lectures répondaient 200 à tout authentifié : le contenu du
        // dossier d'autrui se téléchargeait en itérant sur les identifiants de pièce.
        mvc.perform(get("/api/piece-jointe-dossiers?dossier=5001").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/piece-jointe-dossiers/" + idPieceBrouillon).header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/piece-jointe-dossiers/" + idPieceBrouillon + "/contenu")
                        .header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
        // Le dossier examiné de PRMP001 lui est tout aussi fermé (elle n'est ni propriétaire ni de la localité).
        mvc.perform(get("/api/piece-jointe-dossiers/" + idPieceExaminee + "/contenu")
                        .header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Pièces jointes C1 §1 — un contrôleur lit les pièces des dossiers de SA localité, jamais celles d'un BROUILLON (403)")
    void piecesJointes_controleur_localiteOuiBrouillonNon() throws Exception {
        // Dossier 5003 (EXAMINE, réceptionné en ANT) : le Membre d'ANT lit la fiche et le contenu.
        mvc.perform(get("/api/piece-jointe-dossiers?dossier=5003").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPiece==" + idPieceExaminee + ")]", hasSize(1)));
        mvc.perform(get("/api/piece-jointe-dossiers/" + idPieceExaminee + "/contenu")
                        .header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        // Dossier 5001 : un BROUILLON reste invisible aux contrôleurs (§1), pièces comprises.
        mvc.perform(get("/api/piece-jointe-dossiers?dossier=5001").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/piece-jointe-dossiers/" + idPieceBrouillon).header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Pièces jointes C1 — NON-RÉGRESSION : la PRMP propriétaire et le Président lisent normalement les trois vues")
    void piecesJointes_proprietaireEtPresident_lecturesOk() throws Exception {
        mvc.perform(get("/api/piece-jointe-dossiers?dossier=5001").header("Authorization", tokenPrmp1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPiece==" + idPieceBrouillon + ")]", hasSize(1)));
        mvc.perform(get("/api/piece-jointe-dossiers/" + idPieceBrouillon).header("Authorization", tokenPrmp1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libellePiece").value("Plan de passation des marches"));
        mvc.perform(get("/api/piece-jointe-dossiers/" + idPieceBrouillon + "/contenu")
                        .header("Authorization", tokenPrmp1))
                .andExpect(status().isOk());
        // Le Président voit toutes les localités, brouillons compris.
        mvc.perform(get("/api/piece-jointe-dossiers/" + idPieceBrouillon + "/contenu")
                        .header("Authorization", tokenPresident))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // C — Navettes de PV : immuabilité
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Navette §3.5 — PUT /api/pv-navettes/{id} renvoie 409 pour TOUS, Administrateur compris (l'immuabilité n'est plus contournable)")
    void navette_putToujours409() throws Exception {
        String corps = "{\"idNavette\":5703,\"idPv\":5003,\"numNavette\":1,\"sens\":\"ACCEPTATION\","
                + "\"imActeur\":\"CTRMEM\",\"dateAction\":\"2026-06-05T09:00:00\",\"commentaire\":\"reecrit\"}";
        mvc.perform(put("/api/pv-navettes/5703").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/pv-navettes/5703").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/pv-navettes/5703").header("Authorization", tokenPresident)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isConflict());
        // Le DELETE reste bloqué au même titre (comportement d'origine, conservé).
        mvc.perform(delete("/api/pv-navettes/5703").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Navette §1/§3.5 — lecture scopée à la localité du contrôleur ; la PRMP ne voit aucune navette ; POST réservé à l'Administrateur")
    void navette_lectureScopeeEtCreationAdmin() throws Exception {
        mvc.perform(get("/api/pv-navettes").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idNavette==5703)]", hasSize(1)));
        // La PRMP est un acteur externe : liste vide et 403 sur l'accès unitaire.
        mvc.perform(get("/api/pv-navettes").header("Authorization", tokenPrmp1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/pv-navettes/5703").header("Authorization", tokenPrmp1))
                .andExpect(status().isForbidden());

        String corps = "{\"idNavette\":5704,\"idPv\":5003,\"numNavette\":2,\"sens\":\"ACCEPTATION\","
                + "\"imActeur\":\"CTRMEM\",\"dateAction\":\"2026-06-06T09:00:00\"}";
        mvc.perform(post("/api/pv-navettes").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pv-navettes").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // D — Copies de dossier
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Copies de dossier §1 — lecture scopée à la localité, invisible pour la PRMP ; écriture Administrateur seul")
    void copies_lectureScopeeEcritureAdmin() throws Exception {
        mvc.perform(get("/api/copie-dossiers").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idCopie==5803)]", hasSize(1)));
        mvc.perform(get("/api/copie-dossiers").header("Authorization", tokenPrmp1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/copie-dossiers/5803").header("Authorization", tokenPrmp1))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/copie-dossiers/5803").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/copie-dossiers/5803").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------
    // E/F — Anomalies, statistiques, indicateurs contrôleurs
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Anomalies §3.1/§3.5 — ni la PRMP ni le Membre n'y accèdent (403) ; Président et Administrateur lisent ; écriture Administrateur seul")
    void anomalies_reserveesPresidentEtAdmin() throws Exception {
        mvc.perform(get("/api/anomalies").header("Authorization", tokenPrmp1))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/anomalies").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/anomalies").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/anomalies").header("Authorization", tokenPresident))
                .andExpect(status().isOk());
        mvc.perform(get("/api/anomalies").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
        // Le Président lit mais n'écrit pas : l'anomalie est détectée par les règles, pas saisie.
        mvc.perform(delete("/api/anomalies/1").header("Authorization", tokenPresident))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Statistiques et indicateurs contrôleurs §3.2 — pilotage réservé au Président et à l'Administrateur ; écriture Administrateur seul")
    void statistiques_reserveesPilotage() throws Exception {
        for (String chemin : new String[] { "/api/snapshot-statss", "/api/indicateur-ctrls" }) {
            mvc.perform(get(chemin).header("Authorization", tokenPrmp1))
                    .andExpect(status().isForbidden());
            mvc.perform(get(chemin).header("Authorization", tokenCc))
                    .andExpect(status().isForbidden());
            mvc.perform(get(chemin).header("Authorization", tokenPresident))
                    .andExpect(status().isOk());
            mvc.perform(delete(chemin + "/1").header("Authorization", tokenPresident))
                    .andExpect(status().isForbidden());
        }
    }

    // ------------------------------------------------------------------
    // G — Indicateurs PRMP
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Indicateurs PRMP §3.1 « Mes indicateurs » — chaque PRMP ne voit que les siens ; le Président voit tout ; écriture Administrateur seul")
    void indicateursPrmp_chacunLesSiens() throws Exception {
        mvc.perform(get("/api/indicateur-prmps").header("Authorization", tokenPrmp1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP002')]", hasSize(0)));
        mvc.perform(get("/api/indicateur-prmps").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP002')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')]", hasSize(0)));

        // Accès unitaire aux indicateurs de l'autre → 403.
        mvc.perform(get("/api/indicateur-prmps/5902").header("Authorization", tokenPrmp1))
                .andExpect(status().isForbidden());
        // Le Membre n'a pas à connaître la performance des PRMP : liste vide.
        mvc.perform(get("/api/indicateur-prmps").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        // Le Président voit les deux.
        mvc.perform(get("/api/indicateur-prmps").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP002')]", hasSize(1)));

        // La PRMP ne fabrique pas ses propres indicateurs.
        mvc.perform(delete("/api/indicateur-prmps/5901").header("Authorization", tokenPrmp1))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // H — DMC
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DMC §1/§3.1 — création réservée à l'Administrateur ; lecture scopée au dossier de la ligne de marché")
    void dmc_creationAdminEtLectureScopee() throws Exception {
        TypeDmc type = typeDmcRepository.save(new TypeDmc(null, "DAO", "Dossier d'appel d'offres", true));
        DossierMec dmc = new DossierMec();
        dmc.setIdDetail(5101);   // ligne de marché du dossier 5001 (PRMP001)
        dmc.setIdTypeDmc(type.getIdTypeDmc());
        dmc.setStatut(StatutDmc.A_PREPARER);
        dmc.setDateCreation(LocalDateTime.of(2026, 6, 1, 8, 0));
        dossierMecRepository.save(dmc);

        // Création : Administrateur seul.
        mvc.perform(post("/api/dmcs/par-marche/5102").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dmcs/par-marche/5102").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // Lecture : la PRMP propriétaire y accède, l'étrangère non.
        mvc.perform(get("/api/dmcs/par-marche/5101").header("Authorization", tokenPrmp1))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dmcs/par-marche/5101").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Écart assumé — référentiel SOA
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Référentiel SOA — création ouverte à la PRMP (import PPM), modification et suppression réservées à l'Administrateur")
    void soaBeneficiaire_referentiel() throws Exception {
        mvc.perform(post("/api/soa-beneficiaires").header("Authorization", tokenPrmp1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soaCode\":\"00-21-0-J00-00001\",\"libelle\":\"SOA importe\"}"))
                .andExpect(status().isCreated());

        // Le référentiel reste lisible par tous (listes déroulantes de la saisie).
        mvc.perform(get("/api/soa-beneficiaires").header("Authorization", tokenMembre))
                .andExpect(status().isOk());

        // Renommer ou retirer un code touche toutes les PRMP : Administrateur seul.
        mvc.perform(put("/api/soa-beneficiaires/00-21-0-J00-00001").header("Authorization", tokenPrmp1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soaCode\":\"00-21-0-J00-00001\",\"libelle\":\"Renomme\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/soa-beneficiaires/00-21-0-J00-00001").header("Authorization", tokenPrmp1))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/soa-beneficiaires/00-21-0-J00-00001").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private String bearer(String login, ProfilUtilisateur role, TypeActeur type, String ref, String loc) {
        return "Bearer " + tokenService.generer(login, role.name(), type, ref, loc);
    }

    private Localite localite(String id, String libelle) {
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

    /** PRMP avec un mandat en cours (nomination + 3 ans court jusqu'en 2027) — sinon toute écriture est suspendue. */
    private Prmp prmp(String id) {
        Prmp p = new Prmp();
        p.setIdPrmp(id);
        p.setNomPrmp("Nom" + id);
        p.setPrenomsPrmp("Prenoms");
        p.setArreteNomin("ARR-" + id);
        p.setDateNomin(LocalDate.of(2024, 1, 15));
        p.setCin("10101111" + id.substring(id.length() - 4));
        p.setDateCin(LocalDate.of(2010, 5, 5));
        p.setLieuCin("Antananarivo");
        p.setEmailPrmp(id.toLowerCase() + "@min.mg");
        p.setTelPrmp("0330000001");
        return p;
    }

    private Dossier dossier(int id, String statut, String localite, String idPrmp) {
        Dossier d = new Dossier();
        d.setIdDossier(id);
        d.setRefeDossier("DOS-" + id);
        d.setDateRef(LocalDate.of(2026, 6, 1));
        d.setStatut(statut);
        d.setIdLocalite(localite);
        d.setIdPrmp(idPrmp);
        d.setIdTypeDossier("DDP");
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

    private Marche marche(int idDetail, int dossier, int ppm) {
        Marche m = new Marche();
        m.setIdDetail(idDetail);
        m.setIdDossier(dossier);
        m.setIdPpm(ppm);
        m.setDesignationMarche("Marche " + idDetail);
        m.setStatut("PREVU");
        return m;
    }

    private Lot lot(int idLot, int dossier, int idDetail) {
        Lot l = new Lot();
        l.setIdLot(idLot);
        l.setIdDossier(dossier);
        l.setIdDetail(idDetail);
        l.setDesignationLot("Lot " + idLot);
        return l;
    }

    private Tranche tranche(int idTranche, int idLot) {
        Tranche t = new Tranche();
        t.setIdTranche(idTranche);
        t.setIdLot(idLot);
        t.setLieuTrc("Lieu " + idTranche);
        return t;
    }

    private ServiceBeneficiaire beneficiaire(int idBenef, int idDetail) {
        ServiceBeneficiaire s = new ServiceBeneficiaire();
        s.setIdBenef(idBenef);
        s.setIdDetail(idDetail);
        return s;
    }

    private MarchePrevision prevision(int id, int idDetail, int idCapm) {
        MarchePrevision p = new MarchePrevision();
        p.setIdPrevision(id);
        p.setIdDetail(idDetail);
        p.setIdCapm(idCapm);
        p.setDateDebut(LocalDate.of(2026, 2, 1));
        p.setDateFin(LocalDate.of(2026, 2, 28));
        return p;
    }

    private Echeance echeance(int id, int idDetail) {
        Echeance e = new Echeance();
        e.setIdEcheance(id);
        e.setIdDetail(idDetail);
        e.setTypeJalon("LANCEMENT");
        e.setDatePrevue(LocalDate.of(2026, 3, 1));
        return e;
    }

    private Avis avis(String id, String libelle) {
        Avis a = new Avis();
        a.setIdAvis(id);
        a.setLibelleAvis(libelle);
        return a;
    }

    private Reception reception(int id, int dossier, String imRecept) {
        Reception r = new Reception();
        r.setIdReception(id);
        r.setIdDossier(dossier);
        r.setNumPassage(1);
        r.setTypePassage("INITIAL");
        r.setImCtrlRecept(imRecept);
        r.setDateReception(LocalDateTime.of(2026, 6, 2, 10, 30));
        r.setComplet(true);
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

    /** Projet de PV (statut BROUILLON : pas de co-signature exigée par {@code t_pv_examen_cosignataire_check}). */
    private PvExamen pvExamen(int idPv, int idExamen) {
        PvExamen pv = new PvExamen();
        pv.setIdPv(idPv);
        pv.setIdExamen(idExamen);
        pv.setIdAvis("FAV");
        pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("BROUILLON");
        pv.setNbNavettes(1);
        return pv;
    }

    private PvNavette navette(int idNavette, int idPv) {
        PvNavette n = new PvNavette();
        n.setIdNavette(idNavette);
        n.setIdPv(idPv);
        n.setNumNavette(1);
        n.setSens("SOUMISSION");
        n.setImActeur("CTRMEM");
        n.setDateAction(LocalDateTime.of(2026, 6, 5, 9, 0));
        n.setCommentaire("Projet soumis");
        return n;
    }

    private CopieDossier copie(int idCopie, int idDispatch, int idDossier) {
        CopieDossier c = new CopieDossier();
        c.setIdCopie(idCopie);
        c.setIdDispatch(idDispatch);
        c.setIdDossier(idDossier);
        c.setImDestinataire("CTRMEM");
        c.setTypeCopie("MEMBRE");
        c.setDateTransmission(LocalDateTime.of(2026, 6, 3, 15, 0));
        c.setAccuseReception(false);
        return c;
    }

    /** Pièce jointe PDF minimale d'un dossier (PK auto) — magic-bytes cohérents avec le format déclaré. */
    private PieceJointeDossier pieceJointe(int idDossier, int idTypePiece) {
        PieceJointeDossier p = new PieceJointeDossier();
        p.setIdDossier(idDossier);
        p.setIdTypePiece(idTypePiece);
        p.setNomFichier("plan-" + idDossier + ".pdf");
        p.setContenu("%PDF-1.4 contenu du dossier ".concat(String.valueOf(idDossier))
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        p.setFormat("PDF");
        p.setTaille(32L);
        p.setDateUpload(LocalDateTime.of(2026, 6, 1, 9, 0));
        p.setApresLettreRenvoi(false);
        return p;
    }

    private IndicateurPrmp indicateur(int id, String idPrmp) {
        IndicateurPrmp i = new IndicateurPrmp();
        i.setIdIndicateurPrmp(id);
        i.setIdPrmp(idPrmp);
        i.setExercice(2026);
        i.setNbPpmSoumis(1);
        i.setNbDossiersSoumis(1);
        i.setNbDossiersConformes(1);
        i.setNbDossiersNonConformes(0);
        i.setNbRetours(0);
        i.setNbRetraits(0);
        return i;
    }
}
