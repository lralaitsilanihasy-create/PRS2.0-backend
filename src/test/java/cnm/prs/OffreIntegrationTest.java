package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Capm;
import cnm.prs.entity.CeremonieCles;
import cnm.prs.entity.CleDetenteur;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.ExclusionArmp;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Notification;
import cnm.prs.entity.Offre;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.CeremonieClesRepository;
import cnm.prs.repository.CleDetenteurRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.ExclusionArmpRepository;
import cnm.prs.repository.FicheMarcheValeurRepository;
import cnm.prs.repository.NotificationRepository;
import cnm.prs.repository.OffreJournalRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.service.ChampFicheMarcheService;
import cnm.prs.service.OffreService;
import cnm.prs.service.ParametreService;
import cnm.prs.service.RemiseElectronique;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 3 ; ADR-0013 §4, §7 ; V68) — le dépôt scellé d'une offre : horloge
 * et pièces attendues publiques, conditions §B1 dans l'ordre (codes stables), en-tête contrôlé, morceaux rejouables et vérifiés,
 * scellement (manquants nommés, empreinte recalculée, numéro, {@code premierDepot}, accusé PDF et courriel), une offre par lot,
 * remplacement et retrait, registre des dépôts (le nombre avant la date limite, le registre après), purge des dépôts
 * abandonnés et clôture notifiée.
 *
 * <p>Jeu : la fiche électronique de {@code CeremonieIntegrationTest} (plan 9900, ligne 9901), validée ; CAO, paramètres internes ;
 * la cérémonie close et ses trois clés sont posées en base (les clés ont leurs propres tests), l'avis aussi. Le contenu des
 * morceaux est aléatoire : le serveur ne déchiffre rien, il compte et hache.</p>
 */
class OffreIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final int MORCEAU = 4 * 1024 * 1024;

    @Autowired private ChampFicheMarcheService champService;
    @Autowired private ParametreService parametres;
    @Autowired private CeremonieClesRepository ceremonieRepository;
    @Autowired private CleDetenteurRepository cleRepository;
    @Autowired private DocumentFicheMarcheRepository documentRepository;
    @Autowired private FicheMarcheValeurRepository valeurRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private CompteCandidatRepository candidats;
    @Autowired private ExclusionArmpRepository exclusionRepository;
    @Autowired private OffreRepository offreRepository;
    @Autowired private OffreJournalRepository offreJournal;
    @Autowired private OffreService offreService;

    private final LocalDate aujourdhui = LocalDate.now();
    private final Random hasard = new Random(42);
    private String tokenVer;
    private String jetonA;
    private String jetonB;
    private Long idDmc;
    private List<String> empreintes;

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
        capmRepository.save(new Capm(9901, "Lancement de l'appel d'offres", 1, 92, null));
        marchePrevisionRepository.save(new MarchePrevision(9901, 9901, 9901, aujourdhui.plusDays(10), aujourdhui.plusDays(10), null, null));
        for (String f : List.of("referentiel-champs-fiche-marche-fournitures.csv", "referentiel-champs-fiche-dao-travaux.csv")) {
            assertThat(champService.importerCsv(new ClassPathResource("fiche-marche/" + f).getFile().toPath()).rejets()).isEmpty();
        }
        tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        RemiseElectronique.Parametres p = parametres.remiseElectronique();
        parametres.fixerRemiseElectronique(new RemiseElectronique.Parametres(p.plateformeUrl(), p.fuseau(), "Simple",
                p.tailleMaxPlateformeMo(), p.delaiMinRemiseJours(), p.assistance(), p.quorumDefaut(), p.verificationPartJours()));
        idDmc = ficheEnLigne();
        empreintes = JsonPath.read(mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/cles")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.detenteurs[*].empreinte");
        candidats.save(new CompteCandidat("C900000031", "a@offre.mg", "034 31 311 31", "Rabe", "Paul", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        candidats.save(new CompteCandidat("C900000032", "b@offre.mg", "034 32 322 32", "Rasoa", "Lova", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        jetonA = bearer("a@offre.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000031", null);
        jetonB = bearer("b@offre.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000032", null);
    }

    @Test
    @DisplayName("Dépôt : horloge et pièces publiques ; entreprise absente 409 ; en-tête contrôlé (400, CLES_INDISPONIBLES) ; morceaux "
            + "vérifiés et rejouables ; scellement (manquants nommés, empreinte, numéro, premierDepot, fichier, accusé PDF et courriel) ; "
            + "OFFRE_SCELLEE, OFFRE_EXISTANTE ; remplacement ; retrait ; le nombre seul avant la date limite ; offres d'autrui 403")
    void depot() throws Exception {
        String horloge = mvc.perform(get("/api/horloge")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(horloge, "$.maintenant")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        assertThat(JsonPath.<String>read(horloge, "$.fuseau")).isEqualTo("Indian/Antananarivo");
        String pieces = mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/pieces")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(pieces, "$[*].code")).startsWith("AE", "RECU-DAO", "GARANTIE");
        assertThat(JsonPath.<List<String>>read(pieces, "$[0:3].rubrique")).containsOnly("OFFRE");
        mvc.perform(get("/api/procedures-en-ligne/999999/pieces")).andExpect(status().isNotFound());
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc)).andExpect(jsonPath("$.remplacementAutorise").value(true))
                .andExpect(jsonPath("$.depotsOuverts").value(true)).andExpect(jsonPath("$.etat").value("OUVERTE"));

        Contenu c1 = contenu(MORCEAU + 100);
        creer(jetonA, enTete(c1.idOffre(), c1.taille(), empreintes), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENTREPRISE_ABSENTE"));
        declarer(jetonA, "1111 222 333");
        creer(jetonA, enTete(c1.idOffre(), c1.taille(), List.of(empreintes.get(1), empreintes.get(0), empreintes.get(2))), null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLES_INDISPONIBLES"));
        creer(jetonA, enTete(c1.idOffre(), c1.taille(), empreintes).replace("\"version\":1", "\"version\":2"), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EN_TETE_INVALIDE"));
        creer(jetonA, enTete(c1.idOffre(), 10, empreintes).replace("\"nombreMorceaux\":1", "\"nombreMorceaux\":2"), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EN_TETE_INVALIDE"));
        String t1 = enTete(c1.idOffre(), c1.taille(), empreintes);
        creer(jetonA, t1, null).andExpect(status().isCreated()).andExpect(jsonPath("$.etat").value("EN_COURS"))
                .andExpect(jsonPath("$.nombreMorceaux").value(2)).andExpect(jsonPath("$.recus").value(0))
                .andExpect(jsonPath("$.idOffre").value(c1.idOffre()));

        // Morceaux : empreinte vérifiée, rejouables, dans n'importe quel ordre.
        morceau(jetonA, c1.idOffre(), 1, c1.morceaux().get(1), "0".repeat(64)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPREINTE_DIFFERENTE"));
        morceau(jetonA, c1.idOffre(), 1, c1.morceaux().get(1), sha(c1.morceaux().get(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.recus").value(1));
        morceau(jetonA, c1.idOffre(), 2, c1.morceaux().get(1), sha(c1.morceaux().get(1))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MORCEAU_INVALIDE"));
        morceau(jetonB, c1.idOffre(), 1, c1.morceaux().get(1), sha(c1.morceaux().get(1))).andExpect(status().isForbidden());
        sceller(jetonA, c1.idOffre(), c1.empreinte(t1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MORCEAU_MANQUANT"))
                .andExpect(jsonPath("$.details.rangs[0]").value(0));
        morceau(jetonA, c1.idOffre(), 0, c1.morceaux().get(0), sha(c1.morceaux().get(0))).andExpect(status().isOk())
                .andExpect(jsonPath("$.recus").value(2));
        morceau(jetonA, c1.idOffre(), 0, c1.morceaux().get(0), sha(c1.morceaux().get(0))).andExpect(status().isOk())
                .andExpect(jsonPath("$.recus").value(2));   // rejoué
        sceller(jetonA, c1.idOffre(), "f".repeat(64)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMPREINTE_DIFFERENTE"));
        assertThat(ceremonieRepository.findById(idDmc).orElseThrow().getPremierDepot()).isFalse();
        String accuse = sceller(jetonA, c1.idOffre(), c1.empreinte(t1)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(accuse, "$.offre.etat")).isEqualTo("DEPOSEE");
        assertThat(JsonPath.<Integer>read(accuse, "$.offre.numero")).isEqualTo(1);
        assertThat(JsonPath.<String>read(accuse, "$.offre.empreinte")).isEqualTo(c1.empreinte(t1));
        assertThat(JsonPath.<String>read(accuse, "$.entreprise.nif")).isEqualTo("1111222333");
        assertThat(JsonPath.<Integer>read(accuse, "$.n")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(accuse, "$.quorum")).isEqualTo(2);
        assertThat(JsonPath.<List<String>>read(accuse, "$.empreintesDetenteurs")).isEqualTo(empreintes);
        assertThat(ceremonieRepository.findById(idDmc).orElseThrow().getPremierDepot()).isTrue();
        Path conteneur = Path.of(offreRepository.findById(c1.idOffre()).orElseThrow().getChemin());
        assertThat(Files.size(conteneur)).isEqualTo(4 + t1.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + c1.morceaux().get(0).length + c1.morceaux().get(1).length);
        assertThat(conteneur.resolveSibling(c1.idOffre())).doesNotExist();   // les morceaux sont retirés
        assertThat(notificationRepository.findPourRefEtType("C900000031", "CANDIDAT")).extracting(Notification::getTypeNotif)
                .contains("ACCUSE_DEPOT");
        byte[] pdf = mvc.perform(get("/api/candidat/offres/" + c1.idOffre() + "/accuse").header("Authorization", jetonA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        mvc.perform(get("/api/candidat/offres/" + c1.idOffre() + "/accuse").header("Authorization", jetonB)).andExpect(status().isForbidden());
        morceau(jetonA, c1.idOffre(), 1, c1.morceaux().get(1), sha(c1.morceaux().get(1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFRE_SCELLEE"));

        // Une offre déposée par lot et par entreprise.
        Contenu c2 = contenu(500);
        String t2 = enTete(c2.idOffre(), c2.taille(), empreintes);
        creer(jetonA, t2, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OFFRE_EXISTANTE"));
        // La PRMP : le nombre seul, avant la date limite ; l'Administrateur ne lit pas les dépôts.
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/depots").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.clos").value(false)).andExpect(jsonPath("$.nombre").value(1)).andExpect(jsonPath("$.depots").isEmpty());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/depots").header("Authorization", tokenVer)).andExpect(status().isOk());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/depots").header("Authorization", tokenAdmin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/depots").header("Authorization", jetonA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.depots.nombre").value(1))
                .andExpect(jsonPath("$.depots.clos").value(false));

        // Remplacement : l'ancienne passe REMPLACEE au scellement de la nouvelle, jamais avant.
        creer(jetonA, t2, c1.idOffre()).andExpect(status().isCreated()).andExpect(jsonPath("$.remplace").value(c1.idOffre()));
        assertThat(offreRepository.findById(c1.idOffre()).orElseThrow().getEtat()).isEqualTo(Offre.DEPOSEE);
        morceau(jetonA, c2.idOffre(), 0, c2.morceaux().get(0), sha(c2.morceaux().get(0))).andExpect(status().isOk());
        sceller(jetonA, c2.idOffre(), c2.empreinte(t2)).andExpect(status().isOk()).andExpect(jsonPath("$.offre.numero").value(2));
        assertThat(offreRepository.findById(c1.idOffre()).orElseThrow().getEtat()).isEqualTo(Offre.REMPLACEE);
        assertThat(offreRepository.findById(c1.idOffre()).orElseThrow().getRemplaceePar()).isEqualTo(c2.idOffre());
        String mes = mvc.perform(get("/api/candidat/offres").header("Authorization", jetonA)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(mes, "$[*].etat")).containsExactlyInAnyOrder("DEPOSEE", "REMPLACEE");
        assertThat(JsonPath.<List<String>>read(mes, "$[*].reference")).containsOnly("AOO 0003/MESupReS/2026");
        mvc.perform(get("/api/candidat/offres").header("Authorization", jetonB)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/candidat/offres/" + c2.idOffre()).header("Authorization", jetonB)).andExpect(status().isForbidden());

        // Retrait : conservé, marqué RETIREE.
        mvc.perform(delete("/api/candidat/offres/" + c2.idOffre()).header("Authorization", jetonB)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/candidat/offres/" + c2.idOffre()).header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("RETIREE")).andExpect(jsonPath("$.dateRetrait").isNotEmpty());
        assertThat(Path.of(offreRepository.findById(c2.idOffre()).orElseThrow().getChemin())).exists();
        assertThat(notificationRepository.findPourRefEtType("C900000031", "CANDIDAT")).extracting(Notification::getTypeNotif)
                .contains("OFFRE_RETIREE");
        assertThat(offreJournal.findByIdOffreOrderByDateAscIdAsc(c1.idOffre())).extracting(j -> j.getAction())
                .contains("CREATION", "SCELLEMENT_REFUSE", "SCELLEMENT", "REMPLACEMENT");
        assertThat(offreJournal.findByIdOffreOrderByDateAscIdAsc(c2.idOffre())).extracting(j -> j.getAction()).contains("RETRAIT");
        mvc.perform(delete("/api/candidat/offres/" + c2.idOffre()).header("Authorization", jetonA)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFRE_NON_DEPOSEE"));
    }

    @Test
    @DisplayName("Conditions et échéance : exclusion de l'ARMP (message, groupement), remplacement interdit, dépôts pas encore ouverts, "
            + "clés indisponibles ; purge des dépôts abandonnés ; à la date limite DELAI_DEPASSE, registre des dépôts et DEPOTS_CLOS notifié")
    void conditions() throws Exception {
        declarer(jetonA, "1111222333");
        declarer(jetonB, "4444555666");
        // Exclusion du NIF de B ; un groupement de A qui l'inclut.
        exclusionRepository.save(new ExclusionArmp(null, "4444555666", "BTP Exclu", "Fraude", "ARMP-2026-12", aujourdhui.minusDays(5), null,
                LocalDateTime.now()));
        Contenu c = contenu(100);
        creer(jetonB, enTete(c.idOffre(), c.taille(), empreintes), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENTREPRISE_EXCLUE"))
                .andExpect(jsonPath("$.message").value("Votre entreprise (NIF 4444555666) est exclue des marchés publics par la décision "
                        + "de l'ARMP ARMP-2026-12 du " + aujourdhui.minusDays(5).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + ", sans date de fin. Vous ne pouvez pas déposer d'offre pendant cette période."));
        mvc.perform(post("/api/candidat/offres").header("Authorization", jetonA).contentType(JSON).content("{\"idDmc\":" + idDmc
                + ",\"lot\":null,\"enTete\":" + jsonTexte(enTete(c.idOffre(), c.taille(), empreintes)) + ",\"groupementNifs\":[\"4444 555 666\"]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENTREPRISE_EXCLUE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Un membre du groupement (NIF 4444555666, BTP Exclu)")));

        // Dépôt d'A, puis remplacement interdit par la fiche.
        String t = enTete(c.idOffre(), c.taille(), empreintes);
        creer(jetonA, t, null).andExpect(status().isCreated());
        morceau(jetonA, c.idOffre(), 0, c.morceaux().get(0), sha(c.morceaux().get(0))).andExpect(status().isOk());
        sceller(jetonA, c.idOffre(), c.empreinte(t)).andExpect(status().isOk());
        changer("B04-SE-10", "NON");
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc)).andExpect(jsonPath("$.remplacementAutorise").value(false));
        Contenu c2 = contenu(100);
        creer(jetonA, enTete(c2.idOffre(), c2.taille(), empreintes), c.idOffre()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMPLACEMENT_INTERDIT"));
        mvc.perform(delete("/api/candidat/offres/" + c.idOffre()).header("Authorization", jetonA)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REMPLACEMENT_INTERDIT"));
        changer("B04-SE-10", "OUI");

        // Purge d'un dépôt abandonné (aucun morceau depuis 24 h) : fichiers et ligne, le journal reste.
        exclusionRepository.deleteAll();
        Contenu c3 = contenu(100);
        creer(jetonB, enTete(c3.idOffre(), c3.taille(), empreintes), null).andExpect(status().isCreated());
        morceau(jetonB, c3.idOffre(), 0, c3.morceaux().get(0), sha(c3.morceaux().get(0))).andExpect(status().isOk());
        Offre abandonnee = offreRepository.findById(c3.idOffre()).orElseThrow();
        abandonnee.setDernierMorceau(LocalDateTime.now().minusHours(25));
        offreRepository.save(abandonnee);
        assertThat(offreService.entretenir()[0]).isEqualTo(1);
        assertThat(offreRepository.findById(c3.idOffre())).isEmpty();
        assertThat(offreJournal.findByIdOffreOrderByDateAscIdAsc(c3.idOffre())).extracting(j -> j.getAction()).contains("CREATION", "PURGE");

        // Dépôts pas encore ouverts, puis clés indisponibles (cérémonie rouverte en base).
        changer("B04-SE-03", aujourdhui.plusDays(5) + "T08:00");
        Contenu c4 = contenu(100);
        creer(jetonB, enTete(c4.idOffre(), c4.taille(), empreintes), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROCEDURE_FERMEE"));
        changer("B04-SE-03", aujourdhui.minusDays(1) + "T08:00");
        CeremonieCles ceremonie = ceremonieRepository.findById(idDmc).orElseThrow();
        ceremonie.setEtat(CeremonieCles.A_REFAIRE);
        ceremonieRepository.save(ceremonie);
        creer(jetonB, enTete(c4.idOffre(), c4.taille(), empreintes), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLES_INDISPONIBLES"));
        ceremonie.setEtat(CeremonieCles.CLOSE);
        ceremonieRepository.save(ceremonie);
        creer(jetonB, enTete(c4.idOffre(), c4.taille(), empreintes), null).andExpect(status().isCreated());

        // La date limite passe : plus de morceau, le registre, DEPOTS_CLOS une fois, le dépôt en cours purgé.
        changer("B04-LR-03", aujourdhui.minusDays(1).toString());
        morceau(jetonB, c4.idOffre(), 0, c4.morceaux().get(0), sha(c4.morceaux().get(0))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELAI_DEPASSE"));
        String registre = mvc.perform(get("/api/fiches-marche/" + idDmc + "/depots").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(registre, "$.clos")).isTrue();
        assertThat(JsonPath.<Integer>read(registre, "$.nombre")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(registre, "$.depots[*].nif")).containsExactly("1111222333");
        assertThat(JsonPath.<List<String>>read(registre, "$.depots[*].empreinte")).containsExactly(c.empreinte(t));
        int[] r = offreService.entretenir();
        assertThat(r).containsExactly(1, 1);
        assertThat(offreService.entretenir()).containsExactly(0, 0);
        assertThat(notificationRepository.findPourPrmp("PRMP001", null)).extracting(Notification::getTypeNotif).contains("DEPOTS_CLOS");
        assertThat(notificationRepository.findPourControleur("CTRVER")).extracting(Notification::getTypeNotif).contains("DEPOTS_CLOS");
    }

    // ------------------------------------------------------------------ le contenu chiffré (simulé)

    /** Un contenu de {@code taille} octets en morceaux chiffrés simulés (iv + chiffré + étiquette : 28 octets de plus chacun). */
    private record Contenu(String idOffre, long taille, List<byte[]> morceaux) {
        String empreinte(String enTete) throws Exception {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(enTete.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            morceaux.forEach(sha::update);
            return HexFormat.of().formatHex(sha.digest());
        }
    }

    private Contenu contenu(long taille) {
        List<byte[]> m = new java.util.ArrayList<>();
        long reste = taille;
        do {
            int n = (int) Math.min(MORCEAU, reste);
            byte[] b = new byte[n + 28];
            hasard.nextBytes(b);
            m.add(b);
            reste -= n;
        } while (reste > 0);
        return new Contenu(UUID.randomUUID().toString(), taille, m);
    }

    private String enTete(String idOffre, long taille, List<String> cles) {
        long nombre = Math.max(1, (taille + MORCEAU - 1) / MORCEAU);
        StringBuilder parts = new StringBuilder();
        for (String e : cles) {
            parts.append(parts.length() == 0 ? "" : ",").append("{\"empreinte\":\"").append(e).append("\",\"part\":\"")
                    .append(java.util.Base64.getEncoder().encodeToString(new byte[384])).append("\"}");
        }
        return "{\"version\":1,\"idOffre\":\"" + idOffre + "\",\"idDmc\":" + idDmc + ",\"lot\":null,\"algorithmes\":[\"AES-256-GCM\","
                + "\"RSA-OAEP-3072-SHA256\",\"SHAMIR-GF256\"],\"tailleMorceau\":" + MORCEAU + ",\"nombreMorceaux\":" + nombre
                + ",\"tailleContenu\":" + taille + ",\"quorum\":2,\"n\":3,\"parts\":[" + parts + "]}";
    }

    private static String sha(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    /** Une chaîne JSON (l'en-tête voyage comme une chaîne : ses octets exacts sont hachés). */
    private static String jsonTexte(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private ResultActions creer(String jeton, String enTete, String remplace) throws Exception {
        return mvc.perform(post("/api/candidat/offres").header("Authorization", jeton).contentType(JSON).content("{\"idDmc\":" + idDmc
                + ",\"lot\":null,\"enTete\":" + jsonTexte(enTete) + ",\"remplace\":" + (remplace == null ? "null" : "\"" + remplace + "\"") + "}"));
    }

    private ResultActions morceau(String jeton, String idOffre, int rang, byte[] octets, String empreinte) throws Exception {
        return mvc.perform(put("/api/candidat/offres/" + idOffre + "/morceaux/" + rang).header("Authorization", jeton)
                .header("X-Empreinte", empreinte).contentType(MediaType.APPLICATION_OCTET_STREAM).content(octets));
    }

    private ResultActions sceller(String jeton, String idOffre, String empreinte) throws Exception {
        return mvc.perform(post("/api/candidat/offres/" + idOffre + "/sceller").header("Authorization", jeton).contentType(JSON)
                .content("{\"empreinte\":\"" + empreinte + "\"}"));
    }

    private void declarer(String jeton, String nif) throws Exception {
        mvc.perform(put("/api/candidat/entreprise").header("Authorization", jeton).contentType(JSON).content("{\"raisonSociale\":\"BTP "
                + nif.replace(" ", "").substring(0, 4) + "\",\"nif\":\"" + nif + "\",\"adresse\":\"Lot " + nif + "\",\"representant\":"
                + "{\"nom\":\"Rakoto\",\"prenom\":\"Jean\"}}")).andExpect(status().isOk());
    }

    /** Une fiche électronique validée, CAO et paramètres internes, cérémonie close (trois clés posées en base), avis posé, dépôts ouverts. */
    private Long ficheEnLigne() throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long dmc = ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + dmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"modeRemise\":\"ELECTRONIQUE\",\"garantieSoumission\":\"OUI\",\"alloti\":\"NON\","
                        + "\"variantes\":\"NON\",\"groupement\":\"NON\",\"provenance\":\"NATIONAL\",\"typePrix\":\"UNITAIRES\","
                        + "\"prixRevisable\":\"NON\",\"avance\":\"NON\",\"penalites\":\"CCAG\"}}"))
                .andExpect(status().isOk());
        besoinDeTest(dmc);
        Map<String, String> donnees = new LinkedHashMap<>();
        donnees.put("B02-OB-03", "AOO 0003/MESupReS/2026");
        donnees.put("B04-CD-02", "C1");
        donnees.put("B04-LR-03", aujourdhui.plusDays(60).toString());
        donnees.put("B04-LR-04", "10:00");
        donnees.put("B04-SE-02", "https://depot.cnm.mg");
        donnees.put("B04-SE-03", aujourdhui.plusDays(10) + "T08:00");
        donnees.put("B04-SE-05", "Simple");
        donnees.put("B04-SE-06", "À définir par l'Administrateur (liste officielle des prestataires de certification)");
        donnees.put("B04-SE-10", "OUI");
        donnees.put("B04-SE-17", aujourdhui.plusDays(10) + "T08:00");
        donnees.put("B05-GS-03", "1600000");
        donnees.put("B05-GS-04", "105");
        donnees.put("B04-VO-01", "75");
        remplirObligatoires(dmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", donnees);
        mvc.perform(post("/api/fiches-marche/" + dmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isCreated());
        mvc.perform(put("/api/fiches-marche/" + dmc + "/cao").header("Authorization", tokenPrmp).contentType(JSON)
                .content(CaoIntegrationTest.corpsCao("m1@cao.mg", "m2@cao.mg"))).andExpect(status().isOk());
        mvc.perform(put("/api/fiches-marche/" + dmc + "/parametres-internes").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"quorum\":2,\"dateCeremonie\":\"" + aujourdhui.plusDays(9) + "T09:00\",\"depositaire\":{\"nom\":\"Rakoto Jean\"}}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/fiches-marche/" + dmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"));
        // La cérémonie close, ses trois clés (les clés elles-mêmes ont leurs tests : ici, des empreintes suffisent).
        String internes = mvc.perform(get("/api/fiches-marche/" + dmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        List<String> membres = JsonPath.read(internes, "$.membresCommission[*].im");
        int i = 0;
        for (String im : membres) {
            cleRepository.save(cle(dmc, CleDetenteur.MEMBRE, im, i++));
        }
        cleRepository.save(cle(dmc, CleDetenteur.SECOURS, null, i));
        ceremonieRepository.save(new CeremonieCles(dmc, CeremonieCles.CLOSE, LocalDateTime.now(), false, LocalDateTime.now(), null, null, null));
        // L'avis posé : la procédure est lancée ; l'ouverture des dépôts passée.
        int idFiche = JsonPath.read(mvc.perform(get("/api/fiches-marche/" + dmc).header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString(), "$.idFiche");
        DocumentFicheMarche d = new DocumentFicheMarche();
        d.setIdFiche(idFiche);
        d.setType("AVIS");
        d.setExtension("pdf");
        d.setNomFichier("AVIS_test_v1_01.pdf");
        d.setTailleOctets(4L);
        d.setEmpreinte("0".repeat(64));
        d.setDateGeneration(LocalDateTime.now());
        d.setContenu("%PDF".getBytes());
        d.setPublication("{\"datePublication\":\"" + aujourdhui + "\"}");
        documentRepository.save(d);
        this.idDmc = dmc;
        changer("B04-SE-03", aujourdhui.minusDays(1) + "T08:00");
        return dmc;
    }

    private static CleDetenteur cle(Long idDmc, String role, String im, int i) {
        CleDetenteur c = new CleDetenteur();
        c.setIdDmc(idDmc);
        c.setRole(role);
        c.setIm(im);
        c.setClePublique("spki-" + i);
        c.setEmpreinte(String.valueOf((char) ('a' + i)).repeat(64));
        c.setEnvChiffre("AA==");
        c.setEnvIv("AA==");
        c.setEnvSel("AA==");
        c.setEnvIterations(600_000);
        c.setEnvKdf("PBKDF2-SHA-256");
        c.setEnvAlgorithme("AES-256-GCM");
        c.setEtatPart(CleDetenteur.PUBLIEE);
        c.setDatePublication(LocalDateTime.now());
        c.setRemplacements(0);
        return c;
    }

    private void changer(String code, String valeur) throws Exception {
        int idFiche = JsonPath.read(mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString(), "$.idFiche");
        cnm.prs.entity.FicheMarcheValeur v = valeurRepository.findByIdFiche(idFiche).stream().filter(x -> x.getCodeChamp().equals(code))
                .findFirst().orElseThrow();
        v.setValeur(valeur);
        valeurRepository.save(v);
    }
}
