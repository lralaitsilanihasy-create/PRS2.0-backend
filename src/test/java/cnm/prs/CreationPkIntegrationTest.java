package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Avis;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dispatch;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Examen;
import cnm.prs.entity.Localite;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Profile;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.PvNavette;
import cnm.prs.entity.Reception;
import cnm.prs.entity.Tranche;
import cnm.prs.entity.TypeDossier;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.AvisRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.LocaliteRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.ProfileRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.PvNavetteRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.TrancheRepository;
import cnm.prs.repository.TypeDossierRepository;
import cnm.prs.security.TokenService;

/**
 * ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
 *
 * <p>Avant ce lot, une cinquantaine de services CRUD faisaient {@code repository.save(toEntity(dto))}
 * avec la PK du corps de requête. Or {@code save()} sur une PK déjà présente ne fait pas un INSERT
 * mais un <strong>MERGE</strong> : {@code POST /api/xxx} portant {@code {"id": 42, ...}} écrasait
 * silencieusement la ligne 42 — <em>y compris celle d'une autre PRMP</em> — et répondait 201.</p>
 *
 * <p>Deux réponses selon la nature de la clé, toutes deux couvertes ici :</p>
 * <ul>
 *   <li><strong>409</strong> quand la PK est sémantique (référentiel à clé naturelle) ou qu'aucun
 *       écran ne la calcule — la collision est une erreur de saisie, elle doit être dite ;</li>
 *   <li><strong>réallocation</strong> (« Voie B ») quand un écran réel calcule la PK en
 *       {@code max + 1} côté client : la PK proposée est honorée si elle est libre, sinon le serveur
 *       en alloue une neuve à la séquence. Refuser casserait le flux du modal PPM.</li>
 * </ul>
 *
 * <p>Chaque cas vérifie <strong>les deux moitiés</strong> de la garde : le code HTTP <em>et</em>
 * l'intégrité de la ligne d'origine — seule preuve que rien n'a été écrasé.</p>
 *
 * <p>Fixtures : dossier 6001 BROUILLON de PRMP001 (ANT) et sa descendance de saisie ; dossier 6002
 * BROUILLON de PRMP002 (TMS) — le dossier « d'autrui » ; dossier 6003 EXAMINE de PRMP001, porteur
 * du circuit PV / navette.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CreationPkIntegrationTest extends AbstractIntegrationTest {

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
    @Autowired private AvisRepository avisRepository;
    @Autowired private ReceptionRepository receptionRepository;
    @Autowired private DispatchRepository dispatchRepository;
    @Autowired private ExamenRepository examenRepository;
    @Autowired private PvExamenRepository pvExamenRepository;
    @Autowired private PvNavetteRepository pvNavetteRepository;

    private String tokenAdmin;
    private String tokenMembre;
    /** PRMP propriétaire du dossier 6001 (BROUILLON) et du dossier 6003 (EXAMINE). */
    private String tokenPrmp1;
    /** PRMP <strong>étrangère</strong> : propriétaire du seul dossier 6002 (BROUILLON). */
    private String tokenPrmp2;

    @BeforeEach
    void seed() {
        localiteRepository.save(localite("ANT", "Antananarivo"));
        localiteRepository.save(localite("TMS", "Toamasina"));
        typeDossierRepository.save(new TypeDossier("DDP", "Dossier de Planification"));

        profileRepository.save(profile(3, "Chef de commission"));
        profileRepository.save(profile(5, "Membre"));
        profileRepository.save(profile(8, "Administrateur"));
        controleurRepository.save(controleur("CTRCC1", 3, "ANT"));
        controleurRepository.save(controleur("CTRMEM", 5, "ANT"));
        controleurRepository.save(controleur("CTRADM", 8, "ANT"));

        prmpRepository.save(prmp("PRMP001"));
        prmpRepository.save(prmp("PRMP002"));

        // --- 6001 : BROUILLON de PRMP001 (ANT) — le dossier éditable, avec sa descendance de saisie.
        dossierRepository.save(dossier(6001, "BROUILLON", "ANT", "PRMP001"));
        ppmRepository.save(ppm(6001, 6001, "PRMP001"));
        marcheRepository.save(marche(6101, 6001, 6001));
        lotRepository.save(lot(6201, 6001, 6101));
        trancheRepository.save(tranche(6601, 6201));

        // --- 6002 : BROUILLON de PRMP002 (TMS) — le dossier « d'autrui ».
        dossierRepository.save(dossier(6002, "BROUILLON", "TMS", "PRMP002"));
        ppmRepository.save(ppm(6002, 6002, "PRMP002"));
        marcheRepository.save(marche(6102, 6002, 6002));

        // --- 6003 : EXAMINE de PRMP001 (ANT) — porteur du circuit PV / navette.
        dossierRepository.save(dossier(6003, "EXAMINE", "ANT", "PRMP001"));
        avisRepository.save(avis("FAV", "Favorable"));
        receptionRepository.save(reception(6003, 6003, "CTRCC1"));
        dispatchRepository.save(dispatch(6003, 6003, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(6003, 6003, "CTRMEM"));
        pvExamenRepository.save(pvExamen(6003, 6003));
        pvNavetteRepository.save(navette(6703, 6003));

        tokenAdmin = bearer("CTRADM", ProfilUtilisateur.ADMINISTRATEUR, TypeActeur.CONTROLEUR, "CTRADM", "ANT");
        tokenMembre = bearer("CTRMEM", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM", "ANT");
        tokenPrmp1 = bearer("PRMP001", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP001", "ANT");
        tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "TMS");
    }

    // ------------------------------------------------------------------
    // 1 — POST sur un identifiant déjà pris : 409, et la ligne d'origine INTACTE
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Référentiel à clé naturelle — POST /api/localites sur un id existant : 409, le libellé d'origine est intact")
    void referentielCleString_idExistant_409EtLigneIntacte() throws Exception {
        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idLocalite\":\"ANT\",\"libelleLocalite\":\"Ecrasement\"}"))
                .andExpect(status().isConflict());

        // La clé est sémantique : aucune réallocation, et surtout aucun écrasement.
        assertThat(localiteRepository.findById("ANT")).get()
                .extracting(Localite::getLibelleLocalite).isEqualTo("Antananarivo");
    }

    @Test
    @DisplayName("Enfant de dossier — POST /api/tranches sur un id existant : 409, la tranche d'origine est intacte")
    void enfantDeDossier_idExistant_409EtLigneIntacte() throws Exception {
        // PRMP001 est bien propriétaire du lot 6201 (dossier 6001, BROUILLON) : la garde d'écriture
        // du LOT 3a passe, c'est donc bien la garde de PK du LOT 3b qui répond.
        mvc.perform(post("/api/tranches").header("Authorization", tokenPrmp1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idTranche\":6601,\"idLot\":6201,\"lieuTrc\":\"Ecrasement\"}"))
                .andExpect(status().isConflict());

        assertThat(trancheRepository.findById(6601)).get()
                .extracting(Tranche::getLieuTrc).isEqualTo("Lieu 6601");
    }

    @Test
    @DisplayName("Ressource à séquence — POST /api/pv-navettes sur un id existant : 409, la navette d'origine est intacte")
    void ressourceASequence_idExistant_409EtLigneIntacte() throws Exception {
        mvc.perform(post("/api/pv-navettes").header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idNavette\":6703,\"idPv\":6003,\"numNavette\":9,\"sens\":\"RETOUR\","
                                + "\"imActeur\":\"CTRADM\",\"dateAction\":\"2026-07-01T08:00:00\","
                                + "\"commentaire\":\"Ecrasement\"}"))
                .andExpect(status().isConflict());

        assertThat(pvNavetteRepository.findById(6703)).get()
                .extracting(PvNavette::getCommentaire, PvNavette::getNumNavette)
                .containsExactly("Projet soumis", 1);
    }

    // ------------------------------------------------------------------
    // 2 — Allocation par séquence : deux créations successives ne partagent jamais un id
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PK serveur — deux envois successifs sans id reçoivent deux identifiants distincts (séquence seq_message)")
    void pkServeur_creationsSuccessives_idsDistincts() throws Exception {
        String corps = "{\"destinataireIm\":\"CTRADM\",\"sujet\":\"Sujet\",\"corps\":\"Texte\"}";

        int premier = idEntier(mvc.perform(post("/api/messages/envoyer").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "idMessage");

        int second = idEntier(mvc.perform(post("/api/messages/envoyer").header("Authorization", tokenMembre)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "idMessage");

        // Avant le LOT 3b, l'allocation max+1 n'était pas atomique : deux envois concurrents
        // obtenaient le même identifiant, et le second écrasait le premier.
        assertThat(premier).isNotEqualTo(second);
    }

    // ------------------------------------------------------------------
    // 3 — Non-régression du flux front : réallocation, ni 409 ni écrasement
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Lots (Voie B) — POST avec un id déjà pris par une AUTRE PRMP : le lot d'autrui est intact, le lot créé reçoit un autre id")
    void lots_idPrisParAutrePrmp_reallocationEtLigneDautruiIntacte() throws Exception {
        // Le modal PPM calcule ID_LOT en max+1 sur la liste QU'IL REÇOIT, désormais scopée (LOT 3a) :
        // le max vu par PRMP002 n'est plus le max global, elle propose donc un id déjà pris par PRMP001.
        String corps = "{\"idLot\":6201,\"idDossier\":6002,\"idDetail\":6102,\"designationLot\":\"Lot de PRMP002\"}";

        int premier = idEntier(mvc.perform(post("/api/lots").header("Authorization", tokenPrmp2)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLot").exists())
                .andReturn().getResponse().getContentAsString(), "idLot");

        int second = idEntier(mvc.perform(post("/api/lots").header("Authorization", tokenPrmp2)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "idLot");

        // Le flux n'est pas cassé (201, pas 409) et les deux lots créés sont bien distincts…
        assertThat(premier).isNotEqualTo(6201);
        assertThat(second).isNotEqualTo(6201);
        assertThat(premier).isNotEqualTo(second);

        // …et surtout : le lot 6201 de PRMP001 n'a pas bougé d'un pouce.
        assertThat(lotRepository.findById(6201)).get()
                .extracting(Lot::getDesignationLot, Lot::getIdDossier, Lot::getIdDetail)
                .containsExactly("Lot 6201", 6001, 6101);
    }

    private int idEntier(String json, String champ) {
        return JsonPath.read(json, "$." + champ);
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

    /** PRMP avec un mandat en cours (nomination + 3 ans) — sinon toute écriture est suspendue. */
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
}
