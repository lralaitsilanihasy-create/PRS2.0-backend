package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.MGF1ParameterSpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import org.junit.jupiter.api.BeforeAll;
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
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.CompteCao;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Notification;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.TypeDmc;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.CeremonieClesRepository;
import cnm.prs.repository.CleDetenteurRepository;
import cnm.prs.repository.CompteCaoRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.FicheMarcheValeurRepository;
import cnm.prs.repository.NotificationRepository;
import cnm.prs.service.CeremonieService;
import cnm.prs.service.ChampFicheMarcheService;
import cnm.prs.service.ParametreService;
import cnm.prs.service.RemiseElectronique;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 2b ; ADR-0013 ; V66, ⚠️ V67 : les détenteurs sont les membres de la
 * CAO, comptes {@code MEMBRE_CAO}) — la cérémonie des clés et la procédure de secours S1 à S4 : dépositaire et règle 12,
 * publication des clés (contrôles SPKI / empreinte / enveloppe), droits de lecture par identité, clôture (clés manquantes
 * nommées, paramètres et CAO figés, notifications aussi par courriel), clés publiques aux candidats, garde de l'avis (§B2.6),
 * défi S2 (interopérabilité JCA, temps constant), part perdue, remplacement S4, réouverture (un membre de la CAO remplacé par la
 * PRMP), rappel de vérification.
 *
 * <p>Jeu : plan 9900, ligne 9901 (fournitures à quantité fixe) ; responsable CTRVER ; CAO désignée par la PRMP : deux membres
 * ({@code m1@cao.mg} président, {@code m2@cao.mg}) et un expert adjoint ; quorum 2 (S1 : marge nulle, voulu pour exercer les
 * avertissements). Les paires RSA naissent ici par la JCA, comme WebCrypto les produirait.</p>
 */
class CeremonieIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    @Autowired private ChampFicheMarcheService champService;
    @Autowired private ParametreService parametres;
    @Autowired private CeremonieClesRepository ceremonieRepository;
    @Autowired private CleDetenteurRepository cleRepository;
    @org.springframework.test.context.bean.override.mockito.MockitoBean private cnm.prs.service.EmailService email;
    @Autowired private CompteCaoRepository comptesCao;
    @Autowired private DocumentFicheMarcheRepository documentRepository;
    @Autowired private FicheMarcheValeurRepository valeurRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private CeremonieService ceremonieService;

    private final LocalDate aujourdhui = LocalDate.now();
    private String tokenVer;
    private String tokenM1;
    private String tokenM2;
    private String k1;
    private String k2;
    private Long idDmc;
    private String base;

    /** Une paire de clés d'un détenteur, telle que le navigateur la produirait. */
    private record Paire(KeyPair cles, String spki, String empreinte) {
        String corps() {
            return corps(empreinte, 600_000);
        }

        String corps(String empreinteAnnoncee, int iterations) {
            return "{\"clePublique\":\"" + spki + "\",\"empreinte\":\"" + empreinteAnnoncee + "\",\"enveloppe\":{\"chiffre\":\""
                    + Base64.getEncoder().encodeToString(new byte[48]) + "\",\"iv\":\"" + Base64.getEncoder().encodeToString(new byte[12])
                    + "\",\"sel\":\"" + Base64.getEncoder().encodeToString(new byte[16]) + "\",\"iterations\":" + iterations
                    + ",\"kdf\":\"PBKDF2-SHA-256\",\"algorithme\":\"AES-256-GCM\"}}";
        }

        String dechiffrer(String chiffreBase64) throws Exception {
            Cipher c = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            c.init(Cipher.DECRYPT_MODE, cles.getPrivate(), OAEP);
            return Base64.getEncoder().encodeToString(c.doFinal(Base64.getDecoder().decode(chiffreBase64)));
        }
    }

    /** Les paires de 3072 bits, lentes à produire : tirées une fois pour la classe, en parallèle. */
    private static final java.util.concurrent.ConcurrentLinkedDeque<Paire> POOL = new java.util.concurrent.ConcurrentLinkedDeque<>();

    @BeforeAll
    static void paires() {
        java.util.stream.IntStream.range(0, 8).parallel().forEach(i -> {
            try {
                POOL.add(produire(3072));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static Paire paire(int bits) throws Exception {
        if (bits == 3072) {
            Paire p = POOL.poll();
            if (p != null) {
                return p;
            }
        }
        return produire(bits);
    }

    private static Paire produire(int bits) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(bits);
        KeyPair k = g.generateKeyPair();
        byte[] spki = k.getPublic().getEncoded();
        return new Paire(k, Base64.getEncoder().encodeToString(spki),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(spki)));
    }

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
        TypePieceJointe t = typePieceJointeRepository.findById(seedTypePiece("Dossier d'appel d'offres complet", true, "DMC", 1)).orElseThrow();
        t.setCode("DAO_COMPLET");
        typePieceJointeRepository.save(t);
        tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        RemiseElectronique.Parametres p = parametres.remiseElectronique();
        parametres.fixerRemiseElectronique(new RemiseElectronique.Parametres(p.plateformeUrl(), p.fuseau(), "Simple",
                p.tailleMaxPlateformeMo(), p.delaiMinRemiseJours(), p.assistance(), p.quorumDefaut(), p.verificationPartJours()));
        idDmc = ficheElectronique();
        base = "/api/fiches-marche/" + idDmc + "/ceremonie";
        // ⚠️ V67 (lot 2a, Q11) — la PRMP désigne la CAO ; les comptes des membres sont activés ici directement.
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenPrmp).contentType(JSON)
                .content(CaoIntegrationTest.corpsCao("m1@cao.mg", "m2@cao.mg"))).andExpect(status().isOk());
        k1 = activer("m1@cao.mg");
        k2 = activer("m2@cao.mg");
        tokenM1 = bearer("m1@cao.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, k1, null);
        tokenM2 = bearer("m2@cao.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, k2, null);
    }

    @Test
    @DisplayName("Cérémonie : dépositaire (règle 12, journal, S1), lecture réservée au responsable et aux membres de la CAO, publication "
            + "contrôlée (SPKI 3072, empreinte, enveloppe), enveloppe au seul propriétaire, clôture (manquants nommés, paramètres et "
            + "CAO figés, notifications), clés publiques aux candidats après l'avis, états sur la fiche, espace du membre")
    void ceremonie() throws Exception {
        String fiche = fiche(tokenPrmp);
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[*].regle")).contains("SE_DEPOSITAIRE").doesNotContain("SE_CAO");
        assertThat(JsonPath.<String>read(fiche, "$.ceremonie")).isEqualTo("A_VENIR");
        assertThat(JsonPath.<String>read(fiche, "$.cao")).isEqualTo("COMPLETE");
        internes(tokenVer, "{\"quorum\":2,\"dateCeremonie\":\"" + aujourdhui.plusDays(9) + "T09:00\",\"depositaire\":{\"nom\":\"\",\"organisme\":\"ARMP\"}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("depositaire"));
        String internes = internes(tokenVer, corpsInternes(2, "Rakoto Jean")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(internes, "$.etat")).isEqualTo("COMPLETS");
        assertThat(JsonPath.<String>read(internes, "$.partDeSecours.etat")).isEqualTo("DESIGNE");
        assertThat(JsonPath.<String>read(internes, "$.partDeSecours.depositaire.nom")).isEqualTo("Rakoto Jean");
        assertThat(JsonPath.<Integer>read(internes, "$.nombreParts")).isEqualTo(2);   // la part de secours n'y compte pas
        assertThat(JsonPath.<List<String>>read(internes, "$.membresCommission[*].im")).containsExactly(k1, k2);
        assertThat(JsonPath.<List<String>>read(internes, "$.membresCommission[*].profil")).containsOnly("MEMBRE_CAO");
        assertThat(JsonPath.<List<String>>read(internes, "$.avertissements[*].regle")).containsExactly("SE_QUORUM_MARGE");
        assertThat(JsonPath.<List<String>>read(internes, "$.journal[*].champ")).contains("depositaire", "membresCommission");
        fiche = fiche(tokenPrmp);
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[*].regle")).doesNotContain("SE_DEPOSITAIRE", "PARAMETRES_INTERNES_INCOMPLETS");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.ok[*].regle")).contains("SE_DEPOSITAIRE", "SE_CAO");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.avertissements[?(@.regle=='SE_QUORUM_MARGE')].message"))
                .containsExactly(RemiseElectronique.MESSAGE_QUORUM_MARGE);
        // CLE_A_PUBLIER vers les deux membres désignés (type MEMBRE_CAO).
        assertThat(typesCao(k1)).contains("CLE_A_PUBLIER");
        assertThat(typesCao(k2)).contains("CLE_A_PUBLIER");

        // Lecture : responsable et membres ; Administrateur, PRMP, contrôleurs (non membres) : 403.
        String c = mvc.perform(get(base).header("Authorization", tokenVer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(c, "$.etat")).isEqualTo("A_VENIR");
        assertThat(JsonPath.<Integer>read(c, "$.n")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(c, "$.quorum")).isEqualTo(2);
        assertThat(JsonPath.<Boolean>read(c, "$.premierDepot")).isFalse();
        assertThat(JsonPath.<List<String>>read(c, "$.detenteurs[*].role")).containsExactly("MEMBRE", "MEMBRE", "SECOURS");
        assertThat(JsonPath.<List<String>>read(c, "$.detenteurs[*].etatPart")).containsOnly("ABSENTE");
        assertThat(JsonPath.<String>read(c, "$.detenteurs[0].nom")).isEqualTo("RABE Paul");
        assertThat(JsonPath.<String>read(c, "$.detenteurs[0].im")).isEqualTo(k1);
        assertThat(JsonPath.<String>read(c, "$.detenteurs[2].nom")).isEqualTo("Rakoto Jean");
        mvc.perform(get(base).header("Authorization", tokenM1)).andExpect(status().isOk());
        mvc.perform(get(base).header("Authorization", tokenM2)).andExpect(status().isOk());
        for (String t : List.of(tokenAdmin, tokenPrmp, tokenPresident, tokenMembre, tokenCc)) {
            mvc.perform(get(base).header("Authorization", t)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/fiches-marche/999999/ceremonie").header("Authorization", tokenVer)).andExpect(status().isNotFound());

        // Publication : contrôles du corps, puis 201 ; les non-membres (responsable compris) : 403.
        Paire membre = paire(3072);
        publier(tokenM1, "/cles", membre.corps("deadbeef", 600_000)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPREINTE_INVALIDE"));
        publier(tokenM1, "/cles", membre.corps(membre.empreinte(), 1000)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENVELOPPE_INVALIDE"));
        publier(tokenM1, "/cles", paire(2048).corps()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLE_INVALIDE"));
        publier(tokenM1, "/cles", "{\"clePublique\":\"pas du spki\",\"empreinte\":\"x\",\"enveloppe\":{}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLE_INVALIDE"));
        publier(tokenM1, "/cles", membre.corps()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBRE")).andExpect(jsonPath("$.im").value(k1))
                .andExpect(jsonPath("$.nom").value("RABE Paul"))
                .andExpect(jsonPath("$.empreinte").value(membre.empreinte())).andExpect(jsonPath("$.etatPart").value("PUBLIEE"))
                .andExpect(jsonPath("$.clePublique").value(membre.spki())).andExpect(jsonPath("$.remplacements").value(0));
        publier(tokenM1, "/cles", membre.corps()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLE_EXISTANTE"));
        publier(tokenVer, "/cles", membre.corps()).andExpect(status().isForbidden());
        publier(tokenMembre, "/cles", membre.corps()).andExpect(status().isForbidden());
        // L'enveloppe : à son propriétaire seul.
        mvc.perform(get(base + "/cles/mienne").header("Authorization", tokenM1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.iterations").value(600000)).andExpect(jsonPath("$.kdf").value("PBKDF2-SHA-256"))
                .andExpect(jsonPath("$.chiffre").isNotEmpty());
        mvc.perform(get(base + "/cles/mienne").header("Authorization", tokenM2)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/cles/mienne").header("Authorization", tokenVer)).andExpect(status().isForbidden());
        c = mvc.perform(get(base).header("Authorization", tokenM2)).andReturn().getResponse().getContentAsString();
        assertThat(c).doesNotContain("chiffre", "enveloppe");

        // Clôture : les manquants sont nommés.
        mvc.perform(post(base + "/cloturer").header("Authorization", tokenVer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLES_INCOMPLETES"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("RASOA Lova"), org.hamcrest.Matchers.containsString("la part de secours"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("RABE")))));
        // ⚠️ V71 (demande du 05/10) — la part de secours : le dépositaire seul la publie ; le responsable : 403 GESTE_DU_DEPOSITAIRE.
        Paire secours = paire(3072);
        String tokenD = jetonDepositaire();
        publier(tokenM1, "/cles/secours", secours.corps()).andExpect(status().isForbidden());
        publier(tokenVer, "/cles/secours", secours.corps()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GESTE_DU_DEPOSITAIRE"));
        publier(tokenD, "/cles/secours", secours.corps()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SECOURS")).andExpect(jsonPath("$.im").isEmpty())
                .andExpect(jsonPath("$.nom").value("Rakoto Jean")).andExpect(jsonPath("$.generePar").value("DEPOSITAIRE"));
        mvc.perform(get(base + "/cles/secours").header("Authorization", tokenD)).andExpect(status().isOk());
        mvc.perform(get(base + "/cles/secours").header("Authorization", tokenVer)).andExpect(status().isForbidden());
        mvc.perform(get(base + "/cles/secours").header("Authorization", tokenM1)).andExpect(status().isForbidden());
        Paire cc = paire(3072);
        publier(tokenM2, "/cles", cc.corps()).andExpect(status().isCreated());
        internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(internes, "$.partDeSecours.etat")).isEqualTo("PUBLIEE");

        // Clés publiques : 404 tant que la cérémonie n'est pas close.
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/cles")).andExpect(status().isNotFound());
        mvc.perform(post(base + "/cloturer").header("Authorization", tokenM1)).andExpect(status().isForbidden());
        String close = mvc.perform(post(base + "/cloturer").header("Authorization", tokenVer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(close, "$.etat")).isEqualTo("CLOSE");
        assertThat(JsonPath.<String>read(close, "$.dateCloture")).isNotNull();
        assertThat(JsonPath.<List<String>>read(close, "$.detenteurs[*].etatPart")).containsOnly("PUBLIEE");
        assertThat(JsonPath.<List<String>>read(close, "$.avertissements[*].regle")).containsExactly("SE_MARGE_EPUISEE");   // quorum = membres
        assertThat(typesCao(k1)).contains("CLES_PUBLIEES");
        assertThat(typesCao(k2)).contains("CLES_PUBLIEES");
        assertThat(types("CTRVER")).contains("MARGE_QUORUM");
        assertThat(notificationRepository.findPourPrmp("PRMP001", null)).extracting(Notification::getTypeNotif).contains("CLES_PUBLIEES");
        assertThat(JsonPath.<String>read(fiche(tokenPrmp), "$.ceremonie")).isEqualTo("CLOSE");
        // Close : une clé ne se publie plus ; les paramètres et la CAO sont figés (sauf à l'identique).
        publier(tokenM1, "/cles", membre.corps()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CEREMONIE_CLOSE"));
        internes(tokenVer, corpsInternes(2, "Rakoto Jean")).andExpect(status().isOk());
        internes(tokenVer, corpsInternes(2, "Rabe Paul")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CEREMONIE_CLOSE"));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenPrmp).contentType(JSON)
                .content(CaoIntegrationTest.corpsCao("m1@cao.mg", "m3@cao.mg"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CEREMONIE_CLOSE"));
        String journal = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$.journal[*].champ")).contains("clePubliee", "ceremonieClose");
        assertThat(JsonPath.<List<String>>read(journal, "$.journal[?(@.champ=='ceremonieClose')].nouvelleValeur").get(0))
                .contains(membre.empreinte(), secours.empreinte(), cc.empreinte()).doesNotContain(membre.spki());

        // L'espace du membre voit la cérémonie et sa part.
        String mes = mvc.perform(get("/api/cao/mes-procedures").header("Authorization", tokenM1)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(mes, "$[0].etatCeremonie")).isEqualTo("CLOSE");
        assertThat(JsonPath.<String>read(mes, "$[0].etatPart")).isEqualTo("PUBLIEE");

        // Les clés publiques aux candidats : une fois la fiche validée et l'avis imprimé (procédure en ligne).
        valider();
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/cles")).andExpect(status().isNotFound());   // pas encore lancée
        poserAvis();
        String cles = mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/cles")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(cles, "$.quorum")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(cles, "$.n")).isEqualTo(3);
        assertThat(JsonPath.<List<String>>read(cles, "$.algorithmes")).containsExactly("AES-256-GCM", "RSA-OAEP-3072-SHA256", "SHAMIR-GF256");
        assertThat(JsonPath.<List<String>>read(cles, "$.detenteurs[*].empreinte")).containsExactly(membre.empreinte(), secours.empreinte(), cc.empreinte());
        assertThat(JsonPath.<List<String>>read(cles, "$.detenteurs[*].role")).containsExactly("MEMBRE", "SECOURS", "MEMBRE");
        assertThat(cles).doesNotContain(k1, k2, "RABE", "Rakoto", "m1@cao.mg", "\"im\"", "\"nom\"", "chiffre");
    }

    @Test
    @DisplayName("Secours : défi S2 (déchiffré par la JCA comme WebCrypto le ferait, usage unique, échec journalisé, part de "
            + "secours par son dépositaire), rappel de vérification, part perdue (marge, notification), remplacement S4 (supprimée "
            + "avant le premier dépôt, archivée après), réouverture (parts absentes, un membre de la CAO remplacé par la PRMP, "
            + "DEPOT_EXISTANT), garde de l'avis §B2.6")
    void secours() throws Exception {
        internes(tokenVer, corpsInternes(2, "Rakoto Jean")).andExpect(status().isOk());
        String tokenD = jetonDepositaire();
        Paire membre = paire(3072);
        Paire cc = paire(3072);
        Paire secours = paire(3072);
        publier(tokenM1, "/cles", membre.corps()).andExpect(status().isCreated());
        publier(tokenM2, "/cles", cc.corps()).andExpect(status().isCreated());
        publier(tokenD, "/cles/secours", secours.corps()).andExpect(status().isCreated());
        mvc.perform(post(base + "/cloturer").header("Authorization", tokenVer)).andExpect(status().isOk());

        // S2 — le défi.
        String defi = mvc.perform(post(base + "/defi").header("Authorization", tokenM1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.expire").isNotEmpty()).andReturn().getResponse().getContentAsString();
        int idDefi = JsonPath.read(defi, "$.idDefi");
        String clair = membre.dechiffrer(JsonPath.read(defi, "$.chiffre"));
        assertThat(Base64.getDecoder().decode(clair)).hasSize(32);
        mvc.perform(post(base + "/defi/" + idDefi).header("Authorization", tokenM2).contentType(JSON).content("{\"clair\":\"" + clair + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/defi/" + idDefi).header("Authorization", tokenM1).contentType(JSON).content("{\"clair\":\"" + clair + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etatPart").value("VERIFIEE"))
                .andExpect(jsonPath("$.derniereVerification").isNotEmpty());
        mvc.perform(post(base + "/defi/" + idDefi).header("Authorization", tokenM1).contentType(JSON).content("{\"clair\":\"" + clair + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFI_EXPIRE"));   // usage unique
        String defi2 = mvc.perform(post(base + "/defi").header("Authorization", tokenM2)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post(base + "/defi/" + JsonPath.<Integer>read(defi2, "$.idDefi")).header("Authorization", tokenM2).contentType(JSON)
                .content("{\"clair\":\"" + Base64.getEncoder().encodeToString(new byte[32]) + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFI_ECHOUE"));
        mvc.perform(post(base + "/defi/999999").header("Authorization", tokenM2).contentType(JSON).content("{\"clair\":\"AA==\"}"))
                .andExpect(status().isNotFound());
        // La part de secours : ⚠️ V71 son dépositaire, avec sa phrase.
        mvc.perform(post(base + "/defi").header("Authorization", tokenM1).param("role", "SECOURS")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/defi").header("Authorization", tokenVer).param("role", "SECOURS")).andExpect(status().isForbidden());
        String defiS = mvc.perform(post(base + "/defi").header("Authorization", tokenD).param("role", "SECOURS")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post(base + "/defi/" + JsonPath.<Integer>read(defiS, "$.idDefi")).header("Authorization", tokenD).contentType(JSON)
                .content("{\"clair\":\"" + secours.dechiffrer(JsonPath.read(defiS, "$.chiffre")) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SECOURS")).andExpect(jsonPath("$.etatPart").value("VERIFIEE"));
        String journal = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$.journal[*].champ")).contains("defiReussi", "defiEchoue");
        assertThat(JsonPath.<String>read(journal, "$.partDeSecours.etat")).isEqualTo("VERIFIEE");

        // Part perdue : la marge, le responsable notifié.
        mvc.perform(post(base + "/cles/perdue").header("Authorization", tokenM2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etatPart").value("PERDUE"));
        String c = mvc.perform(get(base).header("Authorization", tokenVer)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(c, "$.avertissements[*].regle")).containsExactly("SE_MARGE_EPUISEE");
        assertThat(types("CTRVER")).contains("PART_PERDUE");
        // S4 — remplacer avant le premier dépôt : l'ancienne est supprimée.
        Paire cc2 = paire(3072);
        mvc.perform(put(base + "/cles").header("Authorization", tokenM2).contentType(JSON).content(cc2.corps())).andExpect(status().isOk())
                .andExpect(jsonPath("$.etatPart").value("PUBLIEE")).andExpect(jsonPath("$.remplacements").value(1))
                .andExpect(jsonPath("$.empreinte").value(cc2.empreinte())).andExpect(jsonPath("$.derniereVerification").isEmpty());
        assertThat(cleRepository.findAll().stream().filter(k -> idDmc.equals(k.getIdDmc()))).hasSize(3);
        journal = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$.journal[?(@.champ=='cleRemplacee')].ancienneValeur")).containsExactly("MEMBRE " + cc.empreinte());
        // S4 — après le premier dépôt (lot 3) : archivée, jamais supprimée ; la cérémonie ne se refait pas.
        CeremonieCles ceremonie = ceremonieRepository.findById(idDmc).orElseThrow();
        ceremonie.setPremierDepot(true);
        ceremonieRepository.save(ceremonie);
        Paire cc3 = paire(3072);
        mvc.perform(put(base + "/cles").header("Authorization", tokenM2).contentType(JSON).content(cc3.corps())).andExpect(status().isOk())
                .andExpect(jsonPath("$.remplacements").value(2));
        assertThat(cleRepository.findAll().stream().filter(k -> idDmc.equals(k.getIdDmc()))).hasSize(4)
                .filteredOn(k -> k.getDateArchivage() != null).hasSize(1).first().matches(k -> cc2.empreinte().equals(k.getEmpreinte()));
        mvc.perform(post(base + "/rouvrir").header("Authorization", tokenVer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPOT_EXISTANT"));
        ceremonie.setPremierDepot(false);
        ceremonieRepository.save(ceremonie);

        // §B2.6 — la fiche validée, le dossier soumis, le PV signé : l'avis se publie, cérémonie close…
        valider();
        // Le rappel : rien à 60 jours de la date limite ; à 3 jours, le seul membre non vérifié depuis la clôture (m2), une fois.
        assertThat(ceremonieService.rappelerVerifications()).isZero();
        changer("B04-LR-03", aujourdhui.plusDays(3).toString());
        assertThat(ceremonieService.rappelerVerifications()).isEqualTo(1);
        assertThat(ceremonieService.rappelerVerifications()).isZero();
        assertThat(typesCao(k2)).contains("PART_A_VERIFIER");
        assertThat(typesCao(k1)).doesNotContain("PART_A_VERIFIER");
        changer("B04-LR-03", ouvrable(aujourdhui.plusDays(60)).toString());
        int idDossier = creerDossier();
        pvSigne(idDossier);
        disponibilite().andExpect(jsonPath("$.disponible").value(true));
        // … et plus dès qu'elle est rouverte.
        mvc.perform(post(base + "/rouvrir").header("Authorization", tokenM1)).andExpect(status().isForbidden());
        String rouverte = mvc.perform(post(base + "/rouvrir").header("Authorization", tokenVer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(rouverte, "$.etat")).isEqualTo("A_REFAIRE");
        assertThat(JsonPath.<List<String>>read(rouverte, "$.detenteurs[*].etatPart")).containsOnly("ABSENTE");
        assertThat(JsonPath.<String>read(fiche(tokenPrmp), "$.ceremonie")).isEqualTo("A_REFAIRE");
        disponibilite().andExpect(jsonPath("$.disponible").value(false)).andExpect(jsonPath("$.raison").value("CEREMONIE_NON_CLOSE"));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"datePublication\":\"" + aujourdhui.plusDays(10) + "\",\"jmpNumero\":\"123\",\"jmpDate\":\"" + aujourdhui + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AVIS_INDISPONIBLE"))
                .andExpect(jsonPath("$.details.raison").value("CEREMONIE_NON_CLOSE"));
        // Rouverte : la PRMP remplace m2 par m3 dans la CAO (fiche validée ou non), le responsable peut retoucher les paramètres.
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenPrmp).contentType(JSON)
                .content(CaoIntegrationTest.corpsCao("m1@cao.mg", "m3@cao.mg"))).andExpect(status().isOk());
        String k3 = activer("m3@cao.mg");
        String tokenM3 = bearer("m3@cao.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, k3, null);
        assertThat(typesCao(k3)).contains("CLE_A_PUBLIER");
        internes(tokenVer, corpsInternes(2, "Rakoto Jean")).andExpect(status().isOk());
        publier(tokenM1, "/cles", membre.corps()).andExpect(status().isCreated());   // les anciennes lignes sont supprimées : la même paire resert
        publier(tokenM3, "/cles", cc.corps()).andExpect(status().isCreated());
        publier(tokenM2, "/cles", cc.corps()).andExpect(status().isForbidden());   // plus membre
        publier(tokenD, "/cles/secours", secours.corps()).andExpect(status().isCreated());
        mvc.perform(post(base + "/cloturer").header("Authorization", tokenVer)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("CLOSE"));
        disponibilite().andExpect(jsonPath("$.disponible").value(true));
        journal = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$.journal[*].champ")).contains("ceremonieRouverte", "clePerdue");
        assertThat(JsonPath.<List<String>>read(journal, "$.journal[?(@.champ=='membresCommission')].nouvelleValeur")).hasSize(2);
    }

    @Test
    @DisplayName("Dépositaire (V71) : adresse obligatoire, exclusions nommées, compte créé et invité (CLE_A_PUBLIER), connexion refusée "
            + "avant l'activation, renvoi d'invitation, activation publique, espace /api/depositaire hors coquille interne ; clé de "
            + "secours publiée par lui seul ; ancien geste (RESPONSABLE) géré par le responsable et remplacé par le dépositaire ; "
            + "changement de dépositaire (Q2) : le nouveau remplace la clé")
    void depositaire() throws Exception {
        String sansAdresse = "{\"quorum\":2,\"dateCeremonie\":\"" + aujourdhui.plusDays(9) + "T09:00\",\"depositaire\":{\"nom\":\"Rakoto Jean\"}}";
        internes(tokenVer, sansAdresse).andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[0].champ").value("depositaire.email"));
        internes(tokenVer, sansAdresse.replace("\"nom\":\"Rakoto Jean\"", "\"nom\":\"Rakoto Jean\",\"email\":\"m1@cao.mg\""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEPOSITAIRE_INCOMPATIBLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("membre de la commission")));
        String internes = internes(tokenVer, corpsInternes(2, "Rakoto Jean")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String idD = JsonPath.read(internes, "$.partDeSecours.depositaire.compte.idCompte");
        assertThat(idD).matches("D\\d{9}");
        assertThat(JsonPath.<String>read(internes, "$.partDeSecours.depositaire.compte.etat")).isEqualTo("INVITE");
        assertThat(JsonPath.<String>read(internes, "$.partDeSecours.depositaire.email")).isEqualTo("rakoto.jean@secours.mg");
        assertThat(typesDepositaire(idD)).containsExactly("CLE_A_PUBLIER");
        // Le même dépositaire, de nouveau enregistré : ni nouveau compte ni nouvelle invitation.
        internes(tokenVer, corpsInternes(2, "Rakoto Jean")).andExpect(status().isOk())
                .andExpect(jsonPath("$.partDeSecours.depositaire.compte.idCompte").value(idD));
        assertThat(typesDepositaire(idD)).hasSize(1);
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{\"login\":\"rakoto.jean@secours.mg\",\"motDePasse\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPTE_A_ACTIVER"));
        String inviter = "/api/fiches-marche/" + idDmc + "/parametres-internes/depositaire/inviter";
        mvc.perform(post(inviter).header("Authorization", tokenM1)).andExpect(status().isForbidden());
        mvc.perform(post(inviter).header("Authorization", tokenVer)).andExpect(status().isOk());
        mvc.perform(post("/api/depositaire/activation").contentType(JSON).content("{\"email\":\"rakoto.jean@secours.mg\",\"code\":\"000000\","
                + "\"motDePasse\":\"Secours2026\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_INVALIDE"));
        mvc.perform(post("/api/depositaire/activation").contentType(JSON).content("{\"email\":\"rakoto.jean@secours.mg\",\"code\":\""
                + code("rakoto.jean@secours.mg") + "\",\"motDePasse\":\"Secours2026\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("ACTIF"));
        assertThat(typesDepositaire(idD)).containsExactly("CLE_A_PUBLIER", "CLE_A_PUBLIER");   // après l'activation
        mvc.perform(post(inviter).header("Authorization", tokenVer)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_ACTIF"));
        String reponse = mvc.perform(post("/api/auth/login").contentType(JSON)
                .content("{\"login\":\"rakoto.jean@secours.mg\",\"motDePasse\":\"Secours2026\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEPOSITAIRE")).andExpect(jsonPath("$.ref").value(idD))
                .andReturn().getResponse().getContentAsString();
        assertThat(reponse).contains("Rakoto Jean");
        String tokenD = bearer("rakoto.jean@secours.mg", ProfilUtilisateur.DEPOSITAIRE, TypeActeur.DEPOSITAIRE, idD, null);

        // L'espace : ses procédures ; aucune route interne, ni l'espace CAO, ni la lecture de la séance.
        mvc.perform(get("/api/depositaire/procedures").header("Authorization", tokenD)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idDmc").value(idDmc)).andExpect(jsonPath("$[0].etatCeremonie").value("A_VENIR"))
                .andExpect(jsonPath("$[0].etatPart").value("ABSENTE")).andExpect(jsonPath("$[0].generePar").isEmpty())
                .andExpect(jsonPath("$[0].etatSeance").value("A_VENIR")).andExpect(jsonPath("$[0].secoursDemande").isEmpty());
        mvc.perform(get("/api/depositaire/procedures").header("Authorization", tokenM1)).andExpect(status().isForbidden());
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenD)).andExpect(status().isForbidden());
        mvc.perform(get("/api/cao/mes-procedures").header("Authorization", tokenD)).andExpect(status().isForbidden());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/seance").header("Authorization", tokenD)).andExpect(status().isForbidden());
        mvc.perform(get(base).header("Authorization", tokenD)).andExpect(status().isOk());
        mvc.perform(post(base + "/cloturer").header("Authorization", tokenD)).andExpect(status().isForbidden());

        // Sa clé : publiée par lui seul.
        Paire secours = paire(3072);
        publier(tokenVer, "/cles/secours", secours.corps()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GESTE_DU_DEPOSITAIRE"));
        publier(tokenD, "/cles", secours.corps()).andExpect(status().isForbidden());   // pas membre de la CAO
        publier(tokenD, "/cles/secours", secours.corps()).andExpect(status().isCreated()).andExpect(jsonPath("$.generePar").value("DEPOSITAIRE"));
        assertThat(cleRepository.findFirstByIdDmcAndRoleAndDateArchivageIsNull(idDmc, CleDetenteur.SECOURS).orElseThrow().getIdDepositaire())
                .isEqualTo(idD);
        mvc.perform(get("/api/depositaire/procedures").header("Authorization", tokenD)).andExpect(jsonPath("$[0].etatPart").value("PUBLIEE"))
                .andExpect(jsonPath("$[0].generePar").value("DEPOSITAIRE")).andExpect(jsonPath("$[0].cleARemplacer").value(false));

        // L'ancien geste (une clé générée chez le responsable) : elle se gère par lui ; le dépositaire la remplace.
        CleDetenteur ancienne = cleRepository.findFirstByIdDmcAndRoleAndDateArchivageIsNull(idDmc, CleDetenteur.SECOURS).orElseThrow();
        ancienne.setGenerePar(CleDetenteur.PAR_RESPONSABLE);
        ancienne.setIdDepositaire(null);
        cleRepository.save(ancienne);
        mvc.perform(get(base).header("Authorization", tokenVer)).andExpect(jsonPath("$.detenteurs[2].generePar").value("RESPONSABLE"));
        mvc.perform(get(base + "/cles/secours").header("Authorization", tokenVer)).andExpect(status().isOk());
        mvc.perform(get(base + "/cles/secours").header("Authorization", tokenD)).andExpect(status().isForbidden());
        mvc.perform(get("/api/depositaire/procedures").header("Authorization", tokenD)).andExpect(jsonPath("$[0].etatPart").value("ABSENTE"))
                .andExpect(jsonPath("$[0].cleARemplacer").value(true));
        Paire secours2 = paire(3072);
        mvc.perform(put(base + "/cles/secours").header("Authorization", tokenVer).contentType(JSON).content(secours2.corps()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GESTE_DU_DEPOSITAIRE"));
        mvc.perform(put(base + "/cles/secours").header("Authorization", tokenD).contentType(JSON).content(secours2.corps()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generePar").value("DEPOSITAIRE")).andExpect(jsonPath("$.remplacements").value(1));

        // Q2 — un autre dépositaire : invité, il ne relit pas la clé de l'ancien, il la remplace ; l'ancien ne voit plus la procédure.
        internes = internes(tokenVer, corpsInternes(2, "Rasoa Hanta")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String idD2 = JsonPath.read(internes, "$.partDeSecours.depositaire.compte.idCompte");
        assertThat(idD2).isNotEqualTo(idD);
        assertThat(typesDepositaire(idD2)).containsExactly("CLE_A_PUBLIER");
        String tokenD2 = bearer("rasoa.hanta@secours.mg", ProfilUtilisateur.DEPOSITAIRE, TypeActeur.DEPOSITAIRE, idD2, null);
        mvc.perform(get(base + "/cles/secours").header("Authorization", tokenD2)).andExpect(status().isForbidden());
        mvc.perform(get(base + "/cles/secours").header("Authorization", tokenD)).andExpect(status().isForbidden());
        mvc.perform(put(base + "/cles/secours").header("Authorization", tokenD2).contentType(JSON).content(paire(3072).corps()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.remplacements").value(2));
        mvc.perform(get(base + "/cles/secours").header("Authorization", tokenD2)).andExpect(status().isOk());
        mvc.perform(get("/api/depositaire/procedures").header("Authorization", tokenD)).andExpect(jsonPath("$.length()").value(0));
        assertThat(mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString()).contains("rasoa.hanta@secours.mg");
    }

    // ------------------------------------------------------------------ outils

    private Long ficheElectronique() throws Exception {
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
        donnees.put("B02-OB-03", "AOO 0002/MESupReS/2026");
        donnees.put("B04-CD-02", "C1");
        donnees.put("B04-LR-03", ouvrable(aujourdhui.plusDays(60)).toString());
        donnees.put("B04-LR-04", "10:00");
        donnees.put("B04-SE-02", "https://depot.cnm.mg");
        donnees.put("B04-SE-03", aujourdhui.plusDays(10) + "T08:00");
        donnees.put("B04-SE-05", "Simple");
        donnees.put("B04-SE-06", "À définir par l'Administrateur (liste officielle des prestataires de certification)");
        donnees.put("B04-SE-17", aujourdhui.plusDays(10) + "T08:00");
        donnees.put("B05-GS-03", "1600000");
        donnees.put("B05-GS-04", "105");
        donnees.put("B04-VO-01", "75");
        remplirObligatoires(dmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", donnees);
        mvc.perform(post("/api/fiches-marche/" + dmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isCreated());
        return dmc;
    }

    /** Active directement le compte MEMBRE_CAO d'une adresse (l'activation par code a son test, {@code CaoIntegrationTest}). */
    private String activer(String email) {
        CompteCao c = comptesCao.findByEmail(email).orElseThrow();
        c.setEtat(CompteCao.ACTIF);
        c.setDateActivation(LocalDateTime.now());
        comptesCao.save(c);
        CompteAuth a = compteAuthRepository.findByLogin(email).orElseThrow();
        a.setActif(true);
        a.setMotDePasse(passwordEncoder.encode("Commission2026"));
        compteAuthRepository.save(a);
        return c.getIdCompte();
    }

    private String corpsInternes(int quorum, String depositaire) {
        return "{\"quorum\":" + quorum + ",\"dateCeremonie\":\"" + aujourdhui.plusDays(9) + "T09:00\",\"depositaire\":{\"nom\":\"" + depositaire
                + "\",\"organisme\":\"ARMP\",\"fonction\":\"Directeur\",\"contact\":\"034\",\"email\":\"" + adresse(depositaire) + "\"}}";
    }

    private ResultActions internes(String token, String corps) throws Exception {
        return mvc.perform(put("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", token).contentType(JSON).content(corps));
    }

    private ResultActions publier(String token, String chemin, String corps) throws Exception {
        return mvc.perform(post(base + chemin).header("Authorization", token).contentType(JSON).content(corps));
    }

    private String fiche(String token) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> types(String im) {
        return notificationRepository.findPourControleur(im).stream().map(Notification::getTypeNotif).toList();
    }

    private List<String> typesCao(String idCompte) {
        return notificationRepository.findPourRefEtType(idCompte, "MEMBRE_CAO").stream().map(Notification::getTypeNotif).toList();
    }

    /** ⚠️ V71 — l'adresse dérivée du nom du dépositaire (« Rakoto Jean » → rakoto.jean@secours.mg). */
    private static String adresse(String nom) {
        return nom.toLowerCase(java.util.Locale.ROOT).replace(' ', '.') + "@secours.mg";
    }

    /** ⚠️ V71 — un jeton pour le dépositaire désigné (son compte lu dans les paramètres internes). */
    private String jetonDepositaire() throws Exception {
        String internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        return bearer(JsonPath.read(internes, "$.partDeSecours.depositaire.email"), ProfilUtilisateur.DEPOSITAIRE, TypeActeur.DEPOSITAIRE,
                JsonPath.read(internes, "$.partDeSecours.depositaire.compte.idCompte"), null);
    }

    private List<String> typesDepositaire(String idCompte) {
        return notificationRepository.findPourRefEtType(idCompte, "DEPOSITAIRE").stream().map(Notification::getTypeNotif).toList();
    }

    private String code(String adresse) {
        org.mockito.ArgumentCaptor<String> corps = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(email, org.mockito.Mockito.atLeastOnce()).envoyer(org.mockito.ArgumentMatchers.eq(adresse),
                org.mockito.ArgumentMatchers.anyString(), corps.capture());
        List<String> codes = corps.getAllValues().stream().map(java.util.regex.Pattern.compile("code d'activation : (\\d{6})")::matcher)
                .filter(java.util.regex.Matcher::find).map(m -> m.group(1)).toList();
        assertThat(codes).isNotEmpty();
        return codes.get(codes.size() - 1);
    }

    private void valider() throws Exception {
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"));
    }

    private ResultActions disponibilite() throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/disponibilite").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
    }

    private int creerDossier() throws Exception {
        String corps = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corps, "$.idDossier");
    }

    /** Le PV signé FAV, dossier au statut PV_SIGNE : l'avis est disponible, cérémonie close. */
    private void pvSigne(int idDossier) {
        receptionRepository.save(reception(9950, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(9950, 9950, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9950, 9950, "CTRMEM"));
        seedPvSigne(9950, 9950);
        PvExamen pv = pvExamenRepository.findSignesParDossierRows(idDossier).get(0);
        pv.setIdAvis("FAV");
        pvExamenRepository.save(pv);
        Dossier d = dossierRepository.findById(idDossier).orElseThrow();
        d.setStatut("PV_SIGNE");
        dossierRepository.save(d);
    }

    /** L'avis spécifique posé directement : la procédure est lancée (en ligne). */
    private void poserAvis() throws Exception {
        int idFiche = JsonPath.read(fiche(tokenPrmp), "$.idFiche");
        DocumentFicheMarche d = new DocumentFicheMarche();
        d.setIdFiche(idFiche);
        d.setType("AVIS");
        d.setExtension("pdf");
        d.setNomFichier("AVIS_test_v1_01.pdf");
        d.setTailleOctets(4L);
        d.setEmpreinte("0".repeat(64));
        d.setDateGeneration(LocalDateTime.now());
        d.setContenu("%PDF".getBytes());
        d.setPublication("{\"datePublication\":\"" + aujourdhui.plusDays(10) + "\"}");
        documentRepository.save(d);
    }

    private void changer(String code, String valeur) throws Exception {
        int idFiche = JsonPath.read(fiche(tokenPrmp), "$.idFiche");
        cnm.prs.entity.FicheMarcheValeur v = valeurRepository.findByIdFiche(idFiche).stream().filter(x -> x.getCodeChamp().equals(code))
                .findFirst().orElseThrow();
        v.setValeur(valeur);
        valeurRepository.save(v);
    }
}
