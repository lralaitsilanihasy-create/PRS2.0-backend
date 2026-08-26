package cnm.prs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.Localite;
import cnm.prs.entity.Mandat;
import cnm.prs.entity.Ministere;
import cnm.prs.entity.Organigramme;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.PrmpEntite;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutMandat;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.LocaliteRepository;
import cnm.prs.repository.MandatRepository;
import cnm.prs.repository.MinistereRepository;
import cnm.prs.repository.OrganigrammeRepository;
import cnm.prs.repository.PrmpEntiteRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.UgpmRepository;
import cnm.prs.security.TokenService;

/**
 * ⚠️ Spec « Mandats PRMP » — tests d'intégration du cycle de vie des mandats, de la garde de
 * renouvellement unique, du standby de transition et de la séparation
 * <strong>attribution figée / opérateur courant</strong>.
 *
 * <p>Toutes les dates sont relatives à {@code LocalDate.now()} : ces tests portent sur des règles
 * temporelles, ils ne doivent pas se mettre à échouer en changeant d'année.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MandatsPrmpIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate AUJOURDHUI = LocalDate.now();

    @Autowired private MockMvc mvc;
    @Autowired private TokenService tokenService;
    @Autowired private LocaliteRepository localiteRepository;
    @Autowired private PrmpRepository prmpRepository;
    @Autowired private UgpmRepository ugpmRepository;
    @Autowired private MandatRepository mandatRepository;
    @Autowired private MinistereRepository ministereRepository;
    @Autowired private OrganigrammeRepository organigrammeRepository;
    @Autowired private EntiteContractRepository entiteContractRepository;
    @Autowired private PrmpEntiteRepository prmpEntiteRepository;
    @Autowired private DossierRepository dossierRepository;

    private String tokenAdmin;
    private String tokenSortante;
    private String tokenSuccesseur;
    private String tokenTierce;

    @BeforeEach
    void seed() {
        localiteRepository.save(localite("ANT", "Antananarivo"));
        prmpRepository.save(prmp("PRMPSOR", "Sortante"));
        prmpRepository.save(prmp("PRMPSUC", "Successeur"));
        prmpRepository.save(prmp("PRMPTIE", "Tierce"));
        ugpmRepository.save(ugpm("UGPM001", "PRMPSOR"));

        ministereRepository.save(ministere(90));
        organigrammeRepository.save(organigramme(90, 90));
        entiteContractRepository.save(entite(90, 90, "ANT"));
        prmpEntiteRepository.save(prmpEntite(90, "PRMPSOR", 90, true));

        tokenAdmin = bearer("CTRADM", ProfilUtilisateur.ADMINISTRATEUR, TypeActeur.CONTROLEUR, "CTRADM", "ANT");
        tokenSortante = bearer("PRMPSOR", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPSOR", "ANT");
        tokenSuccesseur = bearer("PRMPSUC", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPSUC", "ANT");
        tokenTierce = bearer("PRMPTIE", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPTIE", "ANT");
    }

    // ------------------------------------------------------------------ cycle de vie

    @Test
    @DisplayName("Mandat : durée 3 ans par défaut, numéro serveur — POST /api/mandats")
    void creation_dureeTroisAns_numeroUn() throws Exception {
        LocalDate debut = AUJOURDHUI.minusYears(1);
        creerMandat("PRMPSOR", "ARR-2024-001", debut, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroMandat").value(1))
                .andExpect(jsonPath("$.statut").value(StatutMandat.ACTIF.name()))
                .andExpect(jsonPath("$.dateFin").value(debut.plusYears(3).minusDays(1).toString()))
                .andExpect(jsonPath("$.titulaire").value("Prenoms Sortante"))
                .andExpect(jsonPath("$.implicite").value(false));
    }

    @Test
    @DisplayName("Reconduction : mandat distinct n°2 (nouvel arrêté, nouvelles dates) — jamais une prolongation")
    void reconduction_creeUnSecondMandatDistinct() throws Exception {
        creerMandat("PRMPSOR", "ARR-A", AUJOURDHUI.minusYears(5), null).andExpect(status().isCreated());
        creerMandat("PRMPSOR", "ARR-B", AUJOURDHUI.minusYears(1), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroMandat").value(2))
                .andExpect(jsonPath("$.refArrete").value("ARR-B"));
    }

    @Test
    @DisplayName("Renouvellement unique : un 3ᵉ mandat pour la même personne → 409 explicite")
    void troisiemeMandat_refuse409() throws Exception {
        creerMandat("PRMPSOR", "ARR-A", AUJOURDHUI.minusYears(8), null).andExpect(status().isCreated());
        creerMandat("PRMPSOR", "ARR-B", AUJOURDHUI.minusYears(4), null).andExpect(status().isCreated());

        creerMandat("PRMPSOR", "ARR-C", AUJOURDHUI.minusYears(1), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Renouvellement unique")))
                .andExpect(jsonPath("$.message").value(containsString("3ᵉ mandat est impossible")));
    }

    @Test
    @DisplayName("Reconduction sans nouvel arrêté (référence réutilisée) → 409")
    void reconduction_memeArrete_refusee() throws Exception {
        creerMandat("PRMPSOR", "ARR-A", AUJOURDHUI.minusYears(5), null).andExpect(status().isCreated());
        creerMandat("PRMPSOR", "ARR-A", AUJOURDHUI.minusYears(1), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Arrêté déjà utilisé")));
    }

    @Test
    @DisplayName("Prolongation déguisée : reconduction qui recouvre le mandat précédent → 409")
    void reconduction_chevauchante_refusee() throws Exception {
        creerMandat("PRMPSOR", "ARR-A", AUJOURDHUI.minusYears(1), null).andExpect(status().isCreated());
        creerMandat("PRMPSOR", "ARR-B", AUJOURDHUI, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("pas une prolongation")));
    }

    @Test
    @DisplayName("Un mandat ne peut excéder 3 ans (dateFin imposée trop lointaine) → 409")
    void dureeSuperieureATroisAns_refusee() throws Exception {
        LocalDate debut = AUJOURDHUI.minusYears(1);
        creerMandat("PRMPSOR", "ARR-A", debut, debut.plusYears(4))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("ne peut excéder 3 ans")));
    }

    @Test
    @DisplayName("Écriture réservée à l'Administrateur : une PRMP ne déclare pas son propre mandat → 403")
    void creation_parPrmp_refusee() throws Exception {
        mvc.perform(post("/api/mandats").header("Authorization", tokenSortante)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsMandat("PRMPSOR", "ARR-X", AUJOURDHUI.minusYears(1), null)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ lecture

    @Test
    @DisplayName("GET /api/mandats?ugpm= : historique chronologique de la PRMP de tutelle, avec statut")
    void historique_parUgpm_chronologique() throws Exception {
        mandatRepository.save(mandat("PRMPSOR", "ARR-A", AUJOURDHUI.minusYears(6), AUJOURDHUI.minusYears(3), 1));
        mandatRepository.save(mandat("PRMPSOR", "ARR-B", AUJOURDHUI.minusYears(1), AUJOURDHUI.plusYears(2), 2));

        mvc.perform(get("/api/mandats").param("ugpm", "UGPM001").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].refArrete").value("ARR-A"))
                .andExpect(jsonPath("$[0].statut").value(StatutMandat.ACHEVE.name()))
                .andExpect(jsonPath("$[1].refArrete").value("ARR-B"))
                .andExpect(jsonPath("$[1].statut").value(StatutMandat.ACTIF.name()));
    }

    @Test
    @DisplayName("GET /api/mandats/actif : 200 en fonction, 404 dès l'abrogation (état de vacance pour le front)")
    void actif_200_puis404ApresAbrogation() throws Exception {
        Mandat m = mandatRepository.save(
                mandat("PRMPSOR", "ARR-A", AUJOURDHUI.minusYears(1), AUJOURDHUI.plusYears(1), 1));

        mvc.perform(get("/api/mandats/actif").param("prmp", "PRMPSOR").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value(StatutMandat.ACTIF.name()));

        mvc.perform(post("/api/mandats/" + m.getIdMandat() + "/abroger").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"Fin de fonctions\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value(StatutMandat.ABROGE.name()));

        mvc.perform(get("/api/mandats/actif").param("prmp", "PRMPSOR").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Périmètre de lecture : une PRMP ne consulte pas les mandats d'une autre → 403")
    void lecture_horsPerimetre_refusee() throws Exception {
        mvc.perform(get("/api/mandats").param("prmp", "PRMPSUC").header("Authorization", tokenSortante))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ standby de transition

    @Test
    @DisplayName("Vacance : sans mandat actif, l'action de traitement est bloquée 409 VACANCE_PRMP")
    void vacance_bloqueLeTraitement() throws Exception {
        mandatRepository.save(mandat("PRMPSOR", "ARR-A", AUJOURDHUI.minusYears(5), AUJOURDHUI.minusYears(2), 1));
        dossierRepository.save(brouillon(9001, "PRMPSOR"));

        mvc.perform(delete("/api/dossiers/9001").header("Authorization", tokenSortante))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VACANCE_PRMP"))
                .andExpect(jsonPath("$.message").value("En attente de nomination de la nouvelle PRMP"));
    }

    @Test
    @DisplayName("Déblocage automatique : dès qu'un mandat redevient actif, le traitement repart sans rien rejouer")
    void vacance_deblocageAutomatiqueALaNomination() throws Exception {
        mandatRepository.save(mandat("PRMPSOR", "ARR-A", AUJOURDHUI.minusYears(5), AUJOURDHUI.minusYears(2), 1));
        dossierRepository.save(brouillon(9002, "PRMPSOR"));

        mvc.perform(delete("/api/dossiers/9002").header("Authorization", tokenSortante))
                .andExpect(status().isConflict());

        // Reconduction : le seul acte nécessaire — aucune reprise manuelle, aucune réattribution.
        creerMandat("PRMPSOR", "ARR-B", AUJOURDHUI.minusMonths(1), null).andExpect(status().isCreated());

        mvc.perform(delete("/api/dossiers/9002").header("Authorization", tokenSortante))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ attribution figée / opérateur courant

    @Test
    @DisplayName("Reprise du traitement : la PRMP en fonction agit sur le dossier de son prédécesseur, "
            + "l'attribution ne bouge pas et le journal porte l'opérateur courant")
    void successeur_reprendLeTraitement_sansReattribution() throws Exception {
        // Attribution figée à la sortante, sous son mandat.
        Mandat mandatSortante = mandatRepository.save(
                mandat("PRMPSOR", "ARR-A", AUJOURDHUI.minusYears(5), AUJOURDHUI.minusYears(2), 1));
        Dossier d = brouillon(9003, "PRMPSOR");
        d.setStatut(StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT.name());
        d.setIdMandatAttrib(mandatSortante.getIdMandat());
        dossierRepository.save(d);

        // Passation de témoin : la sortante quitte le poste, le successeur est nommé et affecté à l'entité.
        prmpEntiteRepository.save(prmpEntite(91, "PRMPSOR", 90, false));
        prmpEntiteRepository.save(prmpEntite(92, "PRMPSUC", 90, true));
        mandatRepository.save(mandat("PRMPSUC", "ARR-S", AUJOURDHUI.minusMonths(6), AUJOURDHUI.plusYears(2), 1));

        mvc.perform(post("/api/dossiers/9003/transmettre-complements-depot")
                .header("Authorization", tokenSuccesseur))
                .andExpect(status().isOk())
                // L'attribution reste celle du prédécesseur : aucune réattribution rétroactive.
                .andExpect(jsonPath("$.idPrmp").value("PRMPSOR"))
                .andExpect(jsonPath("$.idMandatAttrib").value(mandatSortante.getIdMandat()));

        // Le journal, lui, porte l'opérateur courant — c'est là que la reprise se lit.
        mvc.perform(get("/api/dossiers/9003/journal").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].typeAction").value("TRANSMISSION_COMPLEMENTS_DEPOT"))
                .andExpect(jsonPath("$[0].idPrmpOperateur").value("PRMPSUC"))
                .andExpect(jsonPath("$[0].nomOperateur").value("Prenoms Successeur"));
    }

    @Test
    @DisplayName("La garde reste fermée : une PRMP en fonction ailleurs n'accède pas au dossier → 403")
    void prmpTierce_horsPerimetre_refusee() throws Exception {
        mandatRepository.save(mandat("PRMPSOR", "ARR-A", AUJOURDHUI.minusYears(1), AUJOURDHUI.plusYears(1), 1));
        mandatRepository.save(mandat("PRMPTIE", "ARR-T", AUJOURDHUI.minusYears(1), AUJOURDHUI.plusYears(1), 1));
        Dossier d = brouillon(9004, "PRMPSOR");
        d.setStatut(StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT.name());
        dossierRepository.save(d);

        mvc.perform(post("/api/dossiers/9004/transmettre-complements-depot")
                .header("Authorization", tokenTierce))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ outillage

    private org.springframework.test.web.servlet.ResultActions creerMandat(String idPrmp, String refArrete,
            LocalDate debut, LocalDate fin) throws Exception {
        return mvc.perform(post("/api/mandats").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsMandat(idPrmp, refArrete, debut, fin)));
    }

    private String corpsMandat(String idPrmp, String refArrete, LocalDate debut, LocalDate fin) {
        return "{\"idPrmp\":\"" + idPrmp + "\",\"refArrete\":\"" + refArrete + "\",\"dateDebut\":\"" + debut + "\""
                + (fin == null ? "" : ",\"dateFin\":\"" + fin + "\"") + "}";
    }

    private Mandat mandat(String idPrmp, String refArrete, LocalDate debut, LocalDate fin, int numero) {
        Mandat m = new Mandat();
        m.setIdPrmp(idPrmp);
        m.setTitulaire(idPrmp);
        m.setRefArrete(refArrete);
        m.setDateDebut(debut);
        m.setDateFin(fin);
        m.setNumeroMandat(numero);
        m.setStatut(StatutMandat.ACTIF.name());   // valeur de cache ; l'API expose le statut dérivé
        return m;
    }

    private Dossier brouillon(int id, String idPrmp) {
        Dossier d = new Dossier();
        d.setIdDossier(id);
        d.setStatut(StatutDossier.BROUILLON.name());
        d.setIdLocalite("ANT");
        d.setIdPrmp(idPrmp);
        d.setIdEntiteContract(90);
        return d;
    }

    private String bearer(String login, ProfilUtilisateur role, TypeActeur type, String ref, String loc) {
        return "Bearer " + tokenService.generer(login, role.name(), type, ref, loc);
    }

    private Localite localite(String id, String libelle) {
        Localite l = new Localite();
        l.setIdLocalite(id);
        l.setLibelleLocalite(libelle);
        return l;
    }

    private Prmp prmp(String id, String nom) {
        Prmp p = new Prmp();
        p.setIdPrmp(id);
        p.setNomPrmp(nom);
        p.setPrenomsPrmp("Prenoms");
        p.setArreteNomin("ARR-NOMIN-" + id);
        p.setDateNomin(LocalDate.of(2024, 1, 15));
        p.setCin("101011112222");
        p.setDateCin(LocalDate.of(2010, 5, 5));
        p.setLieuCin("Antananarivo");
        p.setEmailPrmp("prmp@min.mg");
        p.setTelPrmp("0330000001");
        return p;
    }

    private Ugpm ugpm(String id, String tutelle) {
        Ugpm u = new Ugpm();
        u.setIdUgpm(id);
        u.setLibelle("UGPM " + id);
        u.setIdPrmpTutelle(tutelle);
        u.setNomUgpm("Agent");
        u.setPrenomsUgpm("Prenoms");
        u.setCin("202022223333");
        u.setDateCin(LocalDate.of(2011, 6, 6));
        u.setLieuCin("Antananarivo");
        u.setEmailUgpm("ugpm@min.mg");
        u.setTelUgpm("0330000002");
        return u;
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
}
