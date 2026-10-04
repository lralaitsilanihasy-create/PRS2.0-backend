package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Capm;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.CompteCaoRepository;
import cnm.prs.service.ChampFicheMarcheService;
import cnm.prs.service.EmailService;
import cnm.prs.service.ParametreService;
import cnm.prs.service.RemiseElectronique;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 2a ; Q11 du pilote ; V67) — la commission d'appel d'offres :
 * désignation par la PRMP seule (400 par champ, exclusions par construction 409 {@code MEMBRE_EXCLU}), comptes {@code MEMBRE_CAO}
 * créés à la désignation et invités par courriel (code capté sur le courriel simulé), activation publique, connexion par
 * l'adresse (409 {@code COMPTE_A_ACTIVER} avant), espace {@code /api/cao}, aucune route interne, {@code membresCommission}
 * dérivé (400 s'il est envoyé, candidats 410), règle 13 {@code SE_CAO}, état sur la fiche, décision PDF, renvoi d'invitation.
 */
class CaoIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final Pattern CODE = Pattern.compile("code d'activation : (\\d{6})");

    @MockitoBean private EmailService email;
    @Autowired private ChampFicheMarcheService champService;
    @Autowired private ParametreService parametres;
    @Autowired private CompteCaoRepository comptesCao;
    @Autowired private CompteCandidatRepository candidats;

    private final LocalDate aujourdhui = LocalDate.now();
    private String tokenVer;
    private String tokenUgpm;
    private Long idDmc;
    private Long autreDmc;

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
        for (int d : List.of(9901, 9902)) {
            Marche l = marche(d, 9900, 9900);
            l.setIdMode(92);
            l.setIdNature(natureFournitures());
            l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
            l.setDesignationMarche("Acquisition de matériels informatiques " + d);
            marcheRepository.save(l);
        }
        capmRepository.save(new Capm(9901, "Lancement de l'appel d'offres", 1, 92, null));
        marchePrevisionRepository.save(new MarchePrevision(9901, 9901, 9901, aujourdhui.plusDays(10), aujourdhui.plusDays(10), null, null));
        marchePrevisionRepository.save(new MarchePrevision(9902, 9902, 9901, aujourdhui.plusDays(10), aujourdhui.plusDays(10), null, null));
        for (String f : List.of("referentiel-champs-fiche-marche-fournitures.csv", "referentiel-champs-fiche-dao-travaux.csv")) {
            assertThat(champService.importerCsv(new ClassPathResource("fiche-marche/" + f).getFile().toPath()).rejets()).isEmpty();
        }
        tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", "ANT");
        RemiseElectronique.Parametres p = parametres.remiseElectronique();
        parametres.fixerRemiseElectronique(new RemiseElectronique.Parametres(p.plateformeUrl(), p.fuseau(), "Simple",
                p.tailleMaxPlateformeMo(), p.delaiMinRemiseJours(), p.assistance(), p.quorumDefaut(), p.verificationPartJours()));
        idDmc = ficheElectronique(9901);
        autreDmc = ficheElectronique(9902);
    }

    @Test
    @DisplayName("Désignation : lecture ABSENTE et règle 13 bloquante ; PRMP seule (UGPM, Administrateur 403) ; 400 par champ ; "
            + "exclusions contrôleur / candidat / PRMP (409 MEMBRE_EXCLU, la raison sans le compte) ; CAO complète : comptes à activer, "
            + "invitations, état sur la fiche, règle 13 ok ; membres dérivés dans les paramètres internes (400 si envoyés, candidats 410) ; "
            + "mise à jour (un membre omis est retiré, son compte reste) ; décision PDF ; renvoi d'invitation")
    void designation() throws Exception {
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("ABSENTE")).andExpect(jsonPath("$.decision").isEmpty())
                .andExpect(jsonPath("$.anomalies[0].regle").value("CAO_INCOMPLETE"));
        String fiche = fiche(tokenPrmp);
        assertThat(JsonPath.<String>read(fiche, "$.cao")).isEqualTo("ABSENTE");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[*].regle")).contains("SE_CAO");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='SE_CAO')].message"))
                .containsExactly(RemiseElectronique.MESSAGE_CAO);

        cao(tokenUgpm, corpsCao("m1@cao.mg", "m2@cao.mg")).andExpect(status().isForbidden());
        cao(tokenAdmin, corpsCao("m1@cao.mg", "m2@cao.mg")).andExpect(status().isForbidden());
        cao(tokenVer, corpsCao("m1@cao.mg", "m2@cao.mg")).andExpect(status().isForbidden());
        String mauvais = cao(tokenPrmp, "{\"membres\":[{\"nom\":\"Rabe\",\"prenom\":\"Paul\",\"email\":\"m1@cao.mg\",\"qualite\":\"MEMBRE\"},"
                + "{\"nom\":\"Randria\",\"prenom\":\"Hery\",\"email\":\"exp@cao.mg\",\"qualite\":\"EXPERT_ADJOINT\",\"president\":true},"
                + "{\"nom\":\"X\",\"prenom\":\"Y\",\"email\":\"pas une adresse\",\"qualite\":\"AUTRE\"}]}")
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(mauvais, "$.erreurs[*].champ")).contains("decision.reference", "decision.date",
                "membres[0].origine", "membres[1].president", "membres[2].email", "membres[2].qualite", "membres");

        // Exclusions par construction.
        Controleur mem = controleurRepository.findById("CTRMEM").orElseThrow();
        String refus = cao(tokenPrmp, corpsCao(mem.getEmailCont(), "m2@cao.mg")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBRE_EXCLU")).andReturn().getResponse().getContentAsString();
        assertThat(refus).contains("contrôleur de la CNM").doesNotContain("CTRMEM");
        candidats.save(new CompteCandidat("C900000021", "cand@entreprise.mg", "034 00 000 00", "Rabe", "Paul", CompteCandidat.CONFIRME,
                false, LocalDateTime.now(), LocalDateTime.now(), null, null));
        cao(tokenPrmp, corpsCao("m1@cao.mg", "cand@entreprise.mg")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("candidat")));
        String emailPrmp = prmpRepository.findById("PRMP001").orElseThrow().getEmailPrmp();
        cao(tokenPrmp, corpsCao(emailPrmp, "m2@cao.mg")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PRMP")));

        // La désignation : comptes à activer, invitations, état sur la fiche.
        String cao = cao(tokenPrmp, corpsCao("m1@cao.mg", "m2@cao.mg")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(cao, "$.etat")).isEqualTo("COMPLETE");
        assertThat(JsonPath.<String>read(cao, "$.decision.reference")).isEqualTo("DEC-001/2026");
        assertThat(JsonPath.<Boolean>read(cao, "$.decision.fichier")).isFalse();
        assertThat(JsonPath.<List<String>>read(cao, "$.membres[*].qualite")).containsExactly("MEMBRE", "MEMBRE", "EXPERT_ADJOINT");
        assertThat(JsonPath.<String>read(cao, "$.membres[0].compte.etat")).isEqualTo("INVITE");
        assertThat(JsonPath.<String>read(cao, "$.membres[0].compte.idCompte")).startsWith("K").hasSize(10);
        assertThat(JsonPath.<Object>read(cao, "$.membres[2].compte")).isNull();
        assertThat(JsonPath.<List<String>>read(cao, "$.anomalies[*].regle")).containsExactlyInAnyOrder("DECISION_SANS_FICHIER", "COMPTES_NON_ACTIVES");
        assertThat(JsonPath.<List<String>>read(cao, "$.anomalies[?(@.regle=='COMPTES_NON_ACTIVES')].message")).containsExactly("2 membres n'ont pas activé leur compte.");
        String k1 = JsonPath.read(cao, "$.membres[0].compte.idCompte");
        int idM2 = JsonPath.read(cao, "$.membres[1].id");
        fiche = fiche(tokenPrmp);
        assertThat(JsonPath.<String>read(fiche, "$.cao")).isEqualTo("COMPLETE");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[*].regle")).doesNotContain("SE_CAO");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.ok[*].regle")).contains("SE_CAO");
        CompteAuth auth = compteAuthRepository.findByLogin("m1@cao.mg").orElseThrow();
        assertThat(auth.getTypeActeur()).isEqualTo("MEMBRE_CAO");
        assertThat(auth.getActif()).isFalse();
        assertThat(auth.getRefActeur()).isEqualTo(k1);
        // La CAO se lit par qui lit la fiche.
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenVer)).andExpect(status().isOk());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenUgpm)).andExpect(status().isOk());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenAdmin)).andExpect(status().isOk());

        // Les paramètres internes : membres dérivés, lus et plus choisis.
        String internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(internes, "$.membresCommission[*].im")).containsExactly(k1, JsonPath.read(cao, "$.membres[1].compte.idCompte"));
        assertThat(JsonPath.<List<String>>read(internes, "$.membresCommission[*].nom")).containsExactly("RABE Paul", "RASOA Lova");
        assertThat(JsonPath.<List<String>>read(internes, "$.membresCommission[*].profil")).containsOnly("MEMBRE_CAO");
        assertThat(JsonPath.<Integer>read(internes, "$.nombreParts")).isEqualTo(2);
        assertThat(JsonPath.<String>read(internes, "$.etat")).isEqualTo("INCOMPLETS");
        assertThat(JsonPath.<List<String>>read(internes, "$.journal[*].champ")).contains("membresCommission");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"membresCommission\":[\"CTRMEM\"],\"quorum\":2}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("membresCommission"))
                .andExpect(jsonPath("$.erreurs[0].message").value(RemiseElectronique.MESSAGE_MEMBRES_CAO));
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes/candidats").header("Authorization", tokenVer))
                .andExpect(status().isGone());

        // Invitation : le code est dans le courriel ; renvoi 200 ; compte actif 409.
        assertThat(code("m2@cao.mg")).hasSize(6);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/cao/membres/" + idM2 + "/inviter").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.compte.etat").value("INVITE"));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/cao/membres/" + idM2 + "/inviter").header("Authorization", tokenUgpm))
                .andExpect(status().isForbidden());

        // Mise à jour : m2 omis est retiré, m4 entre ; le compte de m2 reste.
        String maj = cao(tokenPrmp, corpsCao("m1@cao.mg", "m4@cao.mg")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(maj, "$.membres[*].email")).containsExactly("m1@cao.mg", "m4@cao.mg", "exp@cao.mg");
        assertThat(comptesCao.findByEmail("m2@cao.mg")).isPresent();
        internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(internes, "$.journal[?(@.champ=='membresCommission')].nouvelleValeur")).hasSize(2);

        // La décision signée : un PDF, 10 Mo au plus, par la PRMP.
        mvc.perform(multipart("/api/fiches-marche/" + idDmc + "/cao/decision").file(new MockMultipartFile("fichier", "d.txt", "text/plain",
                "bonjour".getBytes())).header("Authorization", tokenPrmp)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FORMAT_INVALIDE"));
        mvc.perform(multipart("/api/fiches-marche/" + idDmc + "/cao/decision").file(new MockMultipartFile("fichier", "d.pdf", "application/pdf",
                "%PDF-1.4 decision".getBytes())).header("Authorization", tokenUgpm)).andExpect(status().isForbidden());
        String avecPdf = mvc.perform(multipart("/api/fiches-marche/" + idDmc + "/cao/decision").file(new MockMultipartFile("fichier", "d.pdf",
                "application/pdf", "%PDF-1.4 decision".getBytes())).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(avecPdf, "$.decision.fichier")).isTrue();
        assertThat(JsonPath.<List<String>>read(avecPdf, "$.anomalies[*].regle")).doesNotContain("DECISION_SANS_FICHIER");
    }

    @Test
    @DisplayName("Compte MEMBRE_CAO : connexion refusée COMPTE_A_ACTIVER avant l'activation ; activation publique (code faux 400, mot "
            + "de passe faible 400, adresse inconnue 404, bon code 200 puis idempotent) ; connexion par l'adresse (rôle, ref, nom) ; "
            + "espace /api/cao (mes procédures, la procédure, 403 ailleurs) ; aucune route interne (403)")
    void compte() throws Exception {
        String cao = cao(tokenPrmp, corpsCao("m1@cao.mg", "m2@cao.mg")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String k1 = JsonPath.read(cao, "$.membres[0].compte.idCompte");

        mvc.perform(post("/api/auth/login").contentType(JSON).content("{\"login\":\"M1@cao.mg\",\"motDePasse\":\"n'importe quoi\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPTE_A_ACTIVER"));

        String code = code("m1@cao.mg");
        activer("m1@cao.mg", "000000", "Commission2026").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_INVALIDE"));
        activer("m1@cao.mg", code, "court").andExpect(status().isBadRequest());
        activer("inconnu@cao.mg", code, "Commission2026").andExpect(status().isNotFound());
        activer("m1@cao.mg", code, "Commission2026").andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("ACTIF"));
        activer("m1@cao.mg", "000000", "Autre2026mdp").andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("ACTIF"));   // idempotent
        assertThat(comptesCao.findByEmail("m1@cao.mg").orElseThrow().getDateActivation()).isNotNull();
        String relu = mvc.perform(get("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(relu, "$.membres[0].compte.etat")).isEqualTo("ACTIF");
        assertThat(JsonPath.<List<String>>read(relu, "$.anomalies[?(@.regle=='COMPTES_NON_ACTIVES')].message")).containsExactly("1 membre n'a pas activé son compte.");
        int idM1 = JsonPath.read(relu, "$.membres[0].id");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/cao/membres/" + idM1 + "/inviter").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPTE_ACTIF"));

        // Connexion par l'adresse.
        mvc.perform(post("/api/auth/login").contentType(JSON).content("{\"login\":\"m1@cao.mg\",\"motDePasse\":\"mauvais\"}"))
                .andExpect(status().isUnauthorized());
        org.springframework.mock.web.MockHttpServletResponse login = mvc.perform(post("/api/auth/login").contentType(JSON)
                .content("{\"login\":\"M1@cao.mg\",\"motDePasse\":\"Commission2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBRE_CAO")).andExpect(jsonPath("$.typeActeur").value("MEMBRE_CAO"))
                .andExpect(jsonPath("$.ref").value(k1)).andExpect(jsonPath("$.nomAffichage").value("RABE Paul"))
                .andReturn().getResponse();
        // Le JWT ne circule que dans le cookie PRS_SESSION (plan cookie HttpOnly, phase 3) : on l'en extrait.
        String poseCookie = login.getHeaders("Set-Cookie").stream().filter(h -> h.startsWith("PRS_SESSION=")).findFirst().orElseThrow();
        String jeton = "Bearer " + poseCookie.substring("PRS_SESSION=".length(), poseCookie.indexOf(';'));

        // L'espace du membre.
        String mes = mvc.perform(get("/api/cao/mes-procedures").header("Authorization", jeton)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(mes, "$[*].idDmc")).containsExactly(idDmc.intValue());
        assertThat(JsonPath.<Boolean>read(mes, "$[0].president")).isTrue();
        assertThat(JsonPath.<String>read(mes, "$[0].etatCeremonie")).isEqualTo("A_VENIR");
        assertThat(JsonPath.<String>read(mes, "$[0].etatPart")).isEqualTo("ABSENTE");
        assertThat(JsonPath.<String>read(mes, "$[0].objet")).isEqualTo("Acquisition de matériels informatiques 9901");
        assertThat(JsonPath.<String>read(mes, "$[0].dateLimite")).isEqualTo(aujourdhui.plusDays(60) + "T10:00");
        mvc.perform(get("/api/cao/procedures/" + idDmc).header("Authorization", jeton)).andExpect(status().isOk())
                .andExpect(jsonPath("$.president").value(true)).andExpect(jsonPath("$.cao.etat").value("COMPLETE"))
                .andExpect(jsonPath("$.procedure.objet").value("Acquisition de matériels informatiques 9901"))
                .andExpect(jsonPath("$.procedure.signatureExigee").value("Simple"));
        mvc.perform(get("/api/cao/procedures/" + autreDmc).header("Authorization", jeton)).andExpect(status().isForbidden());
        mvc.perform(get("/api/cao/procedures/999999").header("Authorization", jeton)).andExpect(status().isNotFound());
        mvc.perform(get("/api/cao/mes-procedures").header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        // Aucune route interne.
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", jeton)).andExpect(status().isForbidden());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", jeton)).andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/mes").header("Authorization", jeton)).andExpect(status().isForbidden());
        // Mais sa cérémonie, oui (lot 2b, garde par identité).
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/ceremonie").header("Authorization", jeton)).andExpect(status().isOk())
                .andExpect(jsonPath("$.detenteurs[0].im").value(k1)).andExpect(jsonPath("$.detenteurs[0].nom").value("RABE Paul"));
        mvc.perform(get("/api/fiches-marche/" + autreDmc + "/ceremonie").header("Authorization", jeton)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ outils

    private Long ficheElectronique(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long dmc = ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + dmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"modeRemise\":\"ELECTRONIQUE\",\"garantieSoumission\":\"OUI\",\"alloti\":\"NON\","
                        + "\"variantes\":\"NON\",\"groupement\":\"NON\",\"provenance\":\"NATIONAL\",\"typePrix\":\"UNITAIRES\","
                        + "\"prixRevisable\":\"NON\",\"avance\":\"NON\",\"penalites\":\"CCAG\"}}"))
                .andExpect(status().isOk());
        besoinDeTest(dmc);
        Map<String, String> donnees = new LinkedHashMap<>();
        donnees.put("B02-OB-03", "AOO 000" + (idDetail - 9900) + "/MESupReS/2026");
        donnees.put("B04-CD-02", "C1");
        donnees.put("B04-LR-03", aujourdhui.plusDays(60).toString());
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

    /** Une CAO : deux membres (le premier président, agent de l'entité ; le second expert de l'objet) et un expert adjoint. */
    static String corpsCao(String email1, String email2) {
        return "{\"decision\":{\"reference\":\"DEC-001/2026\",\"date\":\"2026-09-30\"},\"membres\":["
                + "{\"nom\":\"Rabe\",\"prenom\":\"Paul\",\"email\":\"" + email1 + "\",\"telephone\":\"034 11 111 11\",\"qualite\":\"MEMBRE\","
                + "\"origine\":\"ENTITE_CONTRACTANTE\",\"fonction\":\"Chef de service\",\"service\":\"DAF\",\"president\":true},"
                + "{\"nom\":\"Rasoa\",\"prenom\":\"Lova\",\"email\":\"" + email2 + "\",\"qualite\":\"MEMBRE\",\"origine\":\"EXPERT_OBJET\","
                + "\"organisme\":\"Université d'Antananarivo\",\"domaine\":\"Informatique\"},"
                + "{\"nom\":\"Randria\",\"prenom\":\"Hery\",\"email\":\"exp@cao.mg\",\"qualite\":\"EXPERT_ADJOINT\",\"organisme\":\"Cabinet X\","
                + "\"domaine\":\"Réseaux\"}]}";
    }

    private ResultActions cao(String token, String corps) throws Exception {
        return mvc.perform(put("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", token).contentType(JSON).content(corps));
    }

    private ResultActions activer(String email, String code, String motDePasse) throws Exception {
        return mvc.perform(post("/api/cao/activation").contentType(JSON).content("{\"email\":\"" + email + "\",\"code\":\"" + code
                + "\",\"motDePasse\":\"" + motDePasse + "\"}"));
    }

    private String fiche(String token) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Le dernier code d'activation envoyé à l'adresse (courriel simulé). */
    private String code(String adresse) {
        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).envoyer(eq(adresse), anyString(), corps.capture());
        List<String> codes = corps.getAllValues().stream().map(CODE::matcher).filter(Matcher::find).map(m -> m.group(1)).toList();
        assertThat(codes).isNotEmpty();
        return codes.get(codes.size() - 1);
    }
}
