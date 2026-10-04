package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.threshold.SecretShare;
import org.bouncycastle.crypto.threshold.ShamirSecretSplitter;
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
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.ExclusionArmp;
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
import cnm.prs.repository.CeremonieClesRepository;
import cnm.prs.repository.CleDetenteurRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.ExclusionArmpRepository;
import cnm.prs.repository.FicheMarcheValeurRepository;
import cnm.prs.repository.NotificationRepository;
import cnm.prs.repository.OffreJournalRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.SeanceJournalRepository;
import cnm.prs.repository.SeanceRepository;
import cnm.prs.service.ChampFicheMarcheService;
import cnm.prs.service.DechiffrementOffre;
import cnm.prs.service.ParametreService;
import cnm.prs.service.RemiseElectronique;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 4 ; ADR-0013 ; V69) — l'ouverture des plis en séance, de bout en bout :
 * des offres <strong>réellement scellées</strong> ici comme le navigateur le fait (ZIP avec manifeste, K tirée, morceau AES-256-GCM,
 * K partagée par BouncyCastle et chaque part chiffrée RSA-OAEP pour la clé publiée), déposées par les routes du lot 3 ; puis la
 * séance : heure, ouverture, parts chiffrées servies à leur seul détenteur, apport en une fois, part de secours et son motif, quorum,
 * déchiffrement de toutes les offres ensemble, écartement d'une entreprise exclue depuis son dépôt, lecture, pièces, PV et sa
 * publication ; la carence ; S5.
 */
class SeanceIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    private static final SecureRandom HASARD = new SecureRandom();
    /** Les paires des trois détenteurs (deux membres, la part de secours), tirées une fois pour la classe. */
    private static final List<KeyPair> PAIRES = new ArrayList<>();

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
    @Autowired private SeanceJournalRepository seanceJournal;
    @Autowired private SeanceRepository seanceRepository;
    @Autowired private OffreJournalRepository offreJournal;

    private final LocalDate aujourdhui = LocalDate.now();
    private String tokenVer;
    private String tokenUgpm;
    private String jetonA;
    private String jetonB;
    private String jetonM1;
    private String jetonM2;
    private Long idDmc;
    private String base;
    /** Empreinte de la clé publiée → la paire (dans l'ordre de GET …/cles). */
    private final Map<String, KeyPair> parEmpreinte = new LinkedHashMap<>();
    private List<String> empreintes;

    @BeforeAll
    static void paires() throws Exception {
        java.util.stream.IntStream.range(0, 3).parallel().forEach(i -> {
            try {
                KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
                g.initialize(3072);
                synchronized (PAIRES) {
                    PAIRES.add(g.generateKeyPair());
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
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
        tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", "ANT");
        RemiseElectronique.Parametres p = parametres.remiseElectronique();
        parametres.fixerRemiseElectronique(new RemiseElectronique.Parametres(p.plateformeUrl(), p.fuseau(), "Simple",
                p.tailleMaxPlateformeMo(), p.delaiMinRemiseJours(), p.assistance(), p.quorumDefaut(), p.verificationPartJours()));
        ficheEnLigne();
        base = "/api/fiches-marche/" + idDmc + "/seance";
        empreintes = JsonPath.read(mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/cles")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.detenteurs[*].empreinte");
        candidats.save(new CompteCandidat("C900000041", "a@seance.mg", "034 41 411 41", "Rabe", "Paul", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        candidats.save(new CompteCandidat("C900000042", "b@seance.mg", "034 42 422 42", "Rasoa", "Lova", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        jetonA = bearer("a@seance.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000041", null);
        jetonB = bearer("b@seance.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000042", null);
        declarer(jetonA, "1111222333", "BTP Alpha");
        declarer(jetonB, "4444555666", "BTP Beta");
    }

    @Test
    @DisplayName("Séance : rien avant l'heure (parts refusées, ouverture prématurée), dépôts clos exigés ; ouverte, chaque membre reçoit "
            + "ses seules parts, les apporte en une fois (incomplètes, invalides : 409) ; la part de secours exige un motif ; au quorum, "
            + "toutes les offres s'ouvrent ensemble — l'entreprise exclue depuis son dépôt est écartée sans déchiffrement ; lecture, "
            + "pièces (UGPM 403), PV produit, publié sans alertes, notifié")
    void seance() throws Exception {
        String offreA = deposer(jetonA, "1111222333", "BTP Alpha", "12500000", "1500000");
        String offreB = deposer(jetonB, "4444555666", "BTP Beta", "11900000");
        candidats.save(new CompteCandidat("C900000043", "c@seance.mg", "034 43 433 43", "Rakoto", "Fara", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        String jetonC = bearer("c@seance.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000043", null);
        declarer(jetonC, "7777888999", "BTP Gamma");
        String offreC = deposer(jetonC, "7777888999", "BTP Gamma", "13100000");   // manifeste v1 : garantie sans montant
        mvc.perform(get(base).header("Authorization", tokenPrmp)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("A_VENIR"))
                .andExpect(jsonPath("$.quorum").value(2)).andExpect(jsonPath("$.offres.length()").value(3));
        mvc.perform(get(base).header("Authorization", tokenAdmin)).andExpect(status().isForbidden());
        mvc.perform(get(base + "/mes-parts").header("Authorization", jetonM1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEANCE_NON_OUVERTE"));
        // L'heure d'ouverture passée mais pas la date limite : les dépôts ne sont pas clos.
        changer("B04-OP-02", aujourdhui.minusDays(1).toString());
        changer("B04-OP-03", "09:00");
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPOTS_NON_CLOS"));
        // La date limite passe ; l'heure d'ouverture n'est pas encore là.
        changer("B04-LR-03", aujourdhui.minusDays(1).toString());
        changer("B04-OP-02", aujourdhui.plusDays(1).toString());
        mvc.perform(get(base).header("Authorization", jetonM1)).andExpect(jsonPath("$.ouverteDans").isNumber());
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEANCE_PREMATUREE")).andExpect(jsonPath("$.details.heureOuverture").value(aujourdhui.plusDays(1) + "T09:00"));
        changer("B04-OP-02", aujourdhui.minusDays(1).toString());
        mvc.perform(post(base + "/ouvrir").header("Authorization", jetonM1)).andExpect(status().isForbidden());
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("OUVERTE"));
        assertThat(typesCao()).contains("PARTS_ATTENDUES");

        // Les parts : à chacun les siennes.
        String parts1 = mvc.perform(get(base + "/mes-parts").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(parts1, "$[*].idOffre")).containsExactlyInAnyOrder(offreA, offreB, offreC);
        mvc.perform(get(base + "/mes-parts").header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        mvc.perform(get(base + "/mes-parts").header("Authorization", jetonM1).param("role", "SECOURS")).andExpect(status().isForbidden());
        String corps1 = apport(parts1, null);
        mvc.perform(post(base + "/parts").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"parts\":[{\"idOffre\":\"" + offreA + "\",\"partClaire\":\"" + Base64.getEncoder().encodeToString(new byte[33]) + "\"}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PART_INVALIDE"));
        String unePart = JsonPath.<List<Map<String, Object>>>read(corps1, "$.parts").stream().filter(m -> offreA.equals(m.get("idOffre")))
                .map(m -> (String) m.get("partClaire")).findFirst().orElseThrow();
        mvc.perform(post(base + "/parts").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"parts\":[{\"idOffre\":\"" + offreA + "\",\"partClaire\":\"" + unePart + "\"}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PARTS_INCOMPLETES"))
                .andExpect(jsonPath("$.details.offres[0]").value(offreB));
        mvc.perform(post(base + "/parts").header("Authorization", jetonM1).contentType(JSON).content(corps1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("OUVERTE")).andExpect(jsonPath("$.membres[0].partsApportees").value(true))
                .andExpect(jsonPath("$.membres[0].present").value(true)).andExpect(jsonPath("$.offres[0].partsRecues").value(1));
        mvc.perform(get(base + "/lecture").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEANCE_NON_DECHIFFREE"));
        mvc.perform(put(base + "/presences").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"presents\":[\"INCONNU\"],\"autres\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(put(base + "/presences").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"presents\":[\"" + comptes.get(1) + "\"],\"autres\":[{\"nom\":\"RAKOTO Hery\",\"qualite\":\"PRMP\"}]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.autres[0].nom").value("RAKOTO Hery")).andExpect(jsonPath("$.membres[0].present").value(true));
        // L'entreprise de B est exclue après son dépôt : son offre sera écartée, non déchiffrée.
        exclusionRepository.save(new ExclusionArmp(null, "4444555666", "BTP Beta", "Fraude", "ARMP-2026-77", aujourdhui, null, LocalDateTime.now()));

        // La part de secours, avec un motif : le quorum est atteint, tout s'ouvre.
        String partsS = mvc.perform(get(base + "/mes-parts").header("Authorization", tokenVer).param("role", "SECOURS")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String corpsS = apport(partsS, null);
        mvc.perform(post(base + "/parts").header("Authorization", tokenVer).param("role", "SECOURS").contentType(JSON).content(corpsS))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_ABSENT"));
        String seance = mvc.perform(post(base + "/parts").header("Authorization", tokenVer).param("role", "SECOURS").contentType(JSON)
                .content(apport(partsS, "M. RASOA, membre, a oublié sa phrase secrète"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(seance, "$.etat")).isEqualTo("DECHIFFREE");
        assertThat(JsonPath.<Boolean>read(seance, "$.secoursEmploye")).isTrue();
        assertThat(offreRepository.findById(offreA).orElseThrow().getIntegrite()).isEqualTo("INTACTE");
        assertThat(offreRepository.findById(offreB).orElseThrow().getEtat()).isEqualTo(Offre.ECARTEE);
        mvc.perform(get(base + "/mes-parts").header("Authorization", jetonM2)).andExpect(status().isConflict());   // plus de séance ouverte

        // La lecture.
        String lecture = mvc.perform(get(base + "/lecture").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(lecture, "$.offres[*].idOffre")).containsExactly(offreA, offreC);
        assertThat(JsonPath.<String>read(lecture, "$.offres[0].integrite")).isEqualTo("INTACTE");
        assertThat(JsonPath.<String>read(lecture, "$.offres[0].acteEngagement.montantHt")).isEqualTo("12500000");
        assertThat(JsonPath.<String>read(lecture, "$.offres[0].entreprise.nif")).isEqualTo("1111222333");
        assertThat(JsonPath.<String>read(lecture, "$.offres[0].entreprise.verification.statut")).isEqualTo("NON_VERIFIE");
        assertThat(JsonPath.<List<Boolean>>read(lecture, "$.offres[0].pieces[*].empreinteConforme")).containsOnly(true);
        assertThat(JsonPath.<String>read(lecture, "$.offres[0].garantie.codeVerification")).isEqualTo("GAR-0001");
        // ⚠️ Arbitrages du pilote (§B3) : manifeste v2, le montant et l'émetteur ; sous le minimum B05-GS-03 (1 600 000), une alerte.
        assertThat(JsonPath.<Number>read(lecture, "$.offres[0].garantie.montant").longValue()).isEqualTo(1_500_000L);
        assertThat(JsonPath.<String>read(lecture, "$.offres[0].garantie.monnaie")).isEqualTo("MGA");
        assertThat(JsonPath.<String>read(lecture, "$.offres[0].garantie.emetteur")).isEqualTo("BNI Madagascar");
        assertThat(JsonPath.<List<String>>read(lecture, "$.offres[0].alertes[?(@.type=='GARANTIE_INSUFFISANTE')].message"))
                .containsExactly("Garantie de 1 500 000 pour un minimum de 1 600 000 fixé par la fiche.");
        // Manifeste v1 : la garantie se lit, sans montant ni émetteur ; l'offre reste lisible, pas d'alerte.
        assertThat(JsonPath.<Boolean>read(lecture, "$.offres[1].garantie.presente")).isTrue();
        assertThat(JsonPath.<Object>read(lecture, "$.offres[1].garantie.montant")).isNull();
        assertThat(JsonPath.<Object>read(lecture, "$.offres[1].garantie.emetteur")).isNull();
        assertThat(JsonPath.<String>read(lecture, "$.offres[1].integrite")).isEqualTo("INTACTE");
        assertThat(JsonPath.<List<String>>read(lecture, "$.offres[1].alertes[*].type")).doesNotContain("GARANTIE_INSUFFISANTE");
        assertThat(JsonPath.<List<String>>read(lecture, "$.offres[0].piecesManquantes")).contains("Reçu du paiement des frais de dossier");
        assertThat(JsonPath.<String>read(lecture, "$.nonOuvertes[0].etat")).isEqualTo("ECARTEE");
        assertThat(JsonPath.<String>read(lecture, "$.nonOuvertes[0].motif")).contains("exclue par l'ARMP", "ARMP-2026-77");
        // ⚠️ §B1 : les pièces, aux membres de la CAO seulement — la PRMP, l'UGPM et le responsable reçoivent un 403 nommé.
        byte[] ae = mvc.perform(get(base + "/offres/" + offreA + "/pieces/acte-engagement.pdf").header("Authorization", jetonM1))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(ae, StandardCharsets.UTF_8)).isEqualTo("%PDF-1.4 acte d'engagement de BTP Alpha");
        for (String jeton : List.of(tokenPrmp, tokenUgpm, tokenVer)) {
            mvc.perform(get(base + "/offres/" + offreA + "/pieces/acte-engagement.pdf").header("Authorization", jeton))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PIECE_RESERVEE_CAO"));
        }
        mvc.perform(get(base + "/offres/" + offreA + "/pieces/manifeste.json").header("Authorization", jetonM2)).andExpect(status().isNotFound());
        mvc.perform(get(base + "/offres/" + offreB + "/pieces/acte-engagement.pdf").header("Authorization", jetonM1)).andExpect(status().isNotFound());

        // ⚠️ §B2 : le PV produit attend la signature des membres présents ; ni publié ni notifié avant.
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/pv")).andExpect(status().isNotFound());
        mvc.perform(post(base + "/pv/signer").header("Authorization", jetonM1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PV_NON_PRODUIT"));
        mvc.perform(post(base + "/pv").header("Authorization", jetonM1).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/pv").header("Authorization", tokenVer).contentType(JSON).content("{\"observations\":\"Séance sans incident.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("PV_A_SIGNER")).andExpect(jsonPath("$.pv.produit").value(true))
                .andExpect(jsonPath("$.pv.publie").value(false)).andExpect(jsonPath("$.pv.signe").value(false))
                .andExpect(jsonPath("$.pv.signatures.length()").value(0)).andExpect(jsonPath("$.pv.signaturesAttendues.length()").value(2));
        mvc.perform(put(base + "/presences").header("Authorization", tokenVer).contentType(JSON).content("{\"presents\":[]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEANCE_CLOSE"));
        assertThat(typesCao()).contains("PV_A_SIGNER");
        assertThat(notificationRepository.findPourPrmp("PRMP001", null)).extracting(Notification::getTypeNotif).doesNotContain("PV_OUVERTURE");
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/pv")).andExpect(status().isNotFound());
        mvc.perform(post(base + "/pv/signer").header("Authorization", tokenPrmp)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NON_PRESENT"));
        mvc.perform(post(base + "/pv/signer").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("PV_A_SIGNER")).andExpect(jsonPath("$.pv.signatures[0].im").value(comptes.get(0)))
                .andExpect(jsonPath("$.pv.signatures[0].president").value(true)).andExpect(jsonPath("$.pv.signatures[0].empechement").value(false))
                .andExpect(jsonPath("$.pv.signaturesAttendues[0].im").value(comptes.get(1)));
        mvc.perform(post(base + "/pv/signer").header("Authorization", jetonM1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEJA_SIGNE"));
        // Q1 : l'empêchement d'un membre présent, constaté par le président (motif porté au PV).
        mvc.perform(post(base + "/pv/empechement").header("Authorization", jetonM2).contentType(JSON)
                .content("{\"im\":\"" + comptes.get(1) + "\",\"motif\":\"Parti\"}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/pv/empechement").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"im\":\"" + comptes.get(1) + "\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_ABSENT"));
        mvc.perform(post(base + "/pv/empechement").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"im\":\"INCONNU\",\"motif\":\"Parti\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NON_SIGNATAIRE"));
        mvc.perform(post(base + "/pv/empechement").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"im\":\"" + comptes.get(1) + "\",\"motif\":\"Appelé en urgence avant la fin de la séance\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("CLOSE")).andExpect(jsonPath("$.pv.signe").value(true)).andExpect(jsonPath("$.pv.publie").value(true))
                .andExpect(jsonPath("$.pv.signatures[1].empechement").value(true))
                .andExpect(jsonPath("$.pv.signatures[1].motif").value("Appelé en urgence avant la fin de la séance"))
                .andExpect(jsonPath("$.pv.signaturesAttendues.length()").value(0));
        mvc.perform(post(base + "/pv/signer").header("Authorization", jetonM2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEJA_SIGNE"));
        byte[] pv = mvc.perform(get(base + "/pv").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(texteDuPdf(pv)).contains("Président de la commission) — signé électroniquement sur la plateforme le",
                "empêché de signer : Appelé en urgence avant la fin de la séance (constaté par", "montant : 1 500 000 MGA",
                "émetteur : BNI Madagascar", "GARANTIE_INSUFFISANTE").doesNotContain("signature attendue");
        byte[] publie = mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/pv")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(texteDuPdf(publie)).contains("signé électroniquement sur la plateforme").doesNotContain("GARANTIE_INSUFFISANTE");
        assertThat(notificationRepository.findPourPrmp("PRMP001", null)).extracting(Notification::getTypeNotif).contains("PV_OUVERTURE");
        assertThat(notificationRepository.findPourRefEtType("C900000041", "CANDIDAT")).extracting(Notification::getTypeNotif).contains("PV_OUVERTURE");
        assertThat(seanceJournal.findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction())
                .contains("OUVERTURE", "APPORT", "SECOURS", "ECARTEMENT", "OUVERTURE_OFFRE", "DECHIFFREMENT", "PV", "SIGNATURE", "EMPECHEMENT",
                        "PV_SIGNE")
                .allSatisfy(a -> assertThat(a).isNotBlank());
        assertThat(seanceJournal.findByIdDmcOrderByDateAscIdAsc(idDmc)).noneMatch(j -> j.getDetail() != null && j.getDetail().contains(unePart));
    }

    @Test
    @DisplayName("Carence : sans offre, la séance s'ouvre déchiffrée et produit un PV de carence ; S5 : refusé tant que le quorum reste "
            + "possible, constaté quand les parts sont perdues — PV de constat, soumissionnaires avertis")
    void carenceEtIllisible() throws Exception {
        changer("B04-LR-03", aujourdhui.minusDays(1).toString());
        changer("B04-OP-02", aujourdhui.minusDays(1).toString());
        changer("B04-OP-03", "09:00");
        // Carence : aucune offre déposée.
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("DECHIFFREE"));
        mvc.perform(get(base + "/lecture").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.offres.length()").value(0));
        mvc.perform(post(base + "/pv").header("Authorization", tokenVer).contentType(JSON).content("{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("CLOSE")).andExpect(jsonPath("$.pv.signe").value(true));   // personne de présent : signé d'office
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEANCE_DEJA_OUVERTE"));
    }

    @Test
    @DisplayName("S5 : le constat est refusé tant que le quorum reste possible, puis accepté quand les parts sont perdues")
    void illisible() throws Exception {
        deposer(jetonA, "1111222333", "BTP Alpha", "12500000");
        changer("B04-LR-03", aujourdhui.minusDays(1).toString());
        changer("B04-OP-02", aujourdhui.minusDays(1).toString());
        changer("B04-OP-03", "09:00");
        mvc.perform(post(base + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isOk());
        mvc.perform(post(base + "/constater-illisible").header("Authorization", tokenVer).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/constater-illisible").header("Authorization", tokenVer).contentType(JSON).content("{\"motif\":\"Parts perdues\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUORUM_POSSIBLE")).andExpect(jsonPath("$.details.possibles").value(3));
        for (CleDetenteur c : cleRepository.findByIdDmcAndDateArchivageIsNullOrderByIdCleAsc(idDmc)) {
            if (!"MEMBRE".equals(c.getRole()) || !c.getIm().equals(comptes.get(0))) {   // seul le premier membre garde sa part
                c.setEtatPart(CleDetenteur.PERDUE);
                cleRepository.save(c);
            }
        }
        // ⚠️ §B2 : le membre présent signe aussi le PV de constat ; la séance reste ILLISIBLE.
        mvc.perform(put(base + "/presences").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"presents\":[\"" + comptes.get(0) + "\"]}")).andExpect(status().isOk());
        mvc.perform(post(base + "/constater-illisible").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"motif\":\"Deux parts perdues sur trois\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("ILLISIBLE"))
                .andExpect(jsonPath("$.pv.produit").value(true)).andExpect(jsonPath("$.pv.signe").value(false))
                .andExpect(jsonPath("$.pv.signaturesAttendues[0].im").value(comptes.get(0)));
        assertThat(notificationRepository.findPourRefEtType("C900000041", "CANDIDAT")).extracting(Notification::getTypeNotif)
                .contains("OFFRES_ILLISIBLES");
        mvc.perform(get(base + "/pv").header("Authorization", tokenPrmp)).andExpect(status().isOk());

        // ⚠️ §B4.2 : la conservation — sans durée fixée, rien ne se purge ; échue, l'Administrateur purge.
        String idOffre = offreRepository.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc).get(0).getIdOffre();
        java.nio.file.Path conteneur = java.nio.file.Path.of(offreRepository.findById(idOffre).orElseThrow().getChemin());
        assertThat(conteneur).exists();
        String conservation = "/api/admin/offres/conservation";
        mvc.perform(get(conservation).header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.annees").isEmpty()).andExpect(jsonPath("$.echues.length()").value(0));
        mvc.perform(post(conservation + "/" + idDmc + "/purger").header("Authorization", tokenAdmin)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSERVATION_NON_FIXEE"));
        mvc.perform(put("/api/parametres/candidats").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"offreConservationAnnees\":101}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("offreConservationAnnees"));
        mvc.perform(put("/api/parametres/candidats").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"offreConservationAnnees\":5}")).andExpect(status().isOk()).andExpect(jsonPath("$.offreConservationAnnees").value(5))
                .andExpect(jsonPath("$.tailleMaxPieceMo").value(10));
        // Le PV de constat n'est pas encore signé : la conservation n'a pas commencé.
        mvc.perform(post(conservation + "/" + idDmc + "/purger").header("Authorization", tokenAdmin)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSERVATION_EN_COURS"));
        mvc.perform(post(base + "/pv/signer").header("Authorization", jetonM1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("ILLISIBLE")).andExpect(jsonPath("$.pv.signe").value(true));
        mvc.perform(post(conservation + "/" + idDmc + "/purger").header("Authorization", tokenAdmin)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSERVATION_EN_COURS")).andExpect(jsonPath("$.details.echeance").isNotEmpty());
        Seance s = seanceRepository.findById(idDmc).orElseThrow();
        s.setCloseLe(s.getCloseLe().minusYears(5).minusDays(1));
        seanceRepository.save(s);
        mvc.perform(get(conservation).header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        mvc.perform(get(conservation).header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.annees").value(5)).andExpect(jsonPath("$.echues[0].idDmc").value(idDmc))
                .andExpect(jsonPath("$.echues[0].offresAPurger").value(1));
        mvc.perform(post(conservation + "/" + idDmc + "/purger").header("Authorization", tokenAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.offresPurgees").value(1));
        assertThat(conteneur).doesNotExist();
        Offre purgee = offreRepository.findById(idOffre).orElseThrow();
        assertThat(purgee.getPurgeeLe()).isNotNull();
        assertThat(purgee.getChemin()).isNull();
        assertThat(purgee.getEmpreinte()).isNotBlank();
        assertThat(offreJournal.findAll()).anyMatch(j -> idOffre.equals(j.getIdOffre()) && "PURGE_CONSERVATION".equals(j.getAction()));
        assertThat(seanceJournal.findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction()).contains("PURGE_CONSERVATION");
        mvc.perform(get(base + "/pv").header("Authorization", tokenPrmp)).andExpect(status().isOk());   // le PV reste
        mvc.perform(get(conservation).header("Authorization", tokenAdmin)).andExpect(jsonPath("$.echues.length()").value(0));
        mvc.perform(put("/api/parametres/candidats").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"offreConservationAnnees\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.offreConservationAnnees").isEmpty());
    }

    // ------------------------------------------------------------------ le navigateur, simulé

    /** Scelle et dépose une offre comme le navigateur : ZIP + manifeste, K, morceau AES-GCM, parts Shamir chiffrées RSA-OAEP. */
    private String deposer(String jeton, String nif, String raison, String montantHt) throws Exception {
        return deposer(jeton, nif, raison, montantHt, null);
    }

    /** {@code montantGarantie} non nul : un manifeste v2 (⚠️ §B3), la garantie avec son montant, sa monnaie et son émetteur. */
    private String deposer(String jeton, String nif, String raison, String montantHt, String montantGarantie) throws Exception {
        String idOffre = UUID.randomUUID().toString();
        byte[] aePdf = ("%PDF-1.4 acte d'engagement de " + raison).getBytes(StandardCharsets.UTF_8);
        byte[] garantie = "%PDF-1.4 garantie".getBytes(StandardCharsets.UTF_8);
        String manifeste = "{\"version\":" + (montantGarantie == null ? 1 : 2) + ",\"idDmc\":" + idDmc + ",\"lot\":null,\"entreprise\":{\"nif\":\"" + nif + "\",\"raisonSociale\":\"" + raison
                + "\"},\"groupement\":null,\"acteEngagement\":{\"montantHt\":\"" + montantHt + "\",\"montantTtc\":\"15000000\",\"monnaie\":\"MGA\","
                + "\"delai\":90,\"delaiUnite\":\"JOURS\",\"validiteJours\":90,\"rabais\":null},\"pieces\":[{\"code\":\"AE\",\"nomFichier\":"
                + "\"acte-engagement.pdf\",\"taille\":" + aePdf.length + ",\"sha256\":\"" + sha(aePdf) + "\"}],\"garantie\":{\"codeVerification\":"
                + "\"GAR-0001\",\"nomFichier\":\"garantie.pdf\"" + (montantGarantie == null ? "" : ",\"montant\":" + montantGarantie
                + ",\"monnaie\":\"MGA\",\"emetteur\":\"BNI Madagascar\"") + "},\"dateScellement\":\"" + LocalDateTime.now() + "\"}";
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(z)) {
            for (Map.Entry<String, byte[]> e : Map.of("manifeste.json", manifeste.getBytes(StandardCharsets.UTF_8), "acte-engagement.pdf", aePdf,
                    "garantie.pdf", garantie).entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        byte[] clair = z.toByteArray();
        byte[] k = new byte[32];
        HASARD.nextBytes(k);
        // K partagée : n = 3, seuil 2 ; part = y (32 octets) ‖ x, au format de shamir-secret-sharing (abscisse i + 1, vérifiée ici).
        SecretShare[] shares = ShamirSecretSplitter.getInstance(ShamirSecretSplitter.Algorithm.AES, 32, HASARD).resplit(k, 2, 3).getSecretShares();
        List<byte[]> parts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            byte[] y = shares[i].getEncoded();
            byte[] p = java.util.Arrays.copyOf(y, 33);
            p[32] = (byte) (i + 1);
            parts.add(p);
        }
        assertThat(DechiffrementOffre.recombiner(List.of(parts.get(0), parts.get(2)))).isEqualTo(k);
        StringBuilder ps = new StringBuilder();
        for (int i = 0; i < empreintes.size(); i++) {
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            rsa.init(Cipher.ENCRYPT_MODE, parEmpreinte.get(empreintes.get(i)).getPublic(), OAEP);
            ps.append(i == 0 ? "" : ",").append("{\"empreinte\":\"").append(empreintes.get(i)).append("\",\"part\":\"")
                    .append(Base64.getEncoder().encodeToString(rsa.doFinal(parts.get(i)))).append("\"}");
        }
        String enTete = "{\"version\":1,\"idOffre\":\"" + idOffre + "\",\"idDmc\":" + idDmc + ",\"lot\":null,\"algorithmes\":[\"AES-256-GCM\","
                + "\"RSA-OAEP-3072-SHA256\",\"SHAMIR-GF256\"],\"tailleMorceau\":4194304,\"nombreMorceaux\":1,\"tailleContenu\":" + clair.length
                + ",\"quorum\":2,\"n\":3,\"parts\":[" + ps + "]}";
        byte[] iv = new byte[12];
        HASARD.nextBytes(iv);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, "AES"), new GCMParameterSpec(128, iv));
        aes.updateAAD(DechiffrementOffre.donneesAuthentifiees(idOffre, 0, true, sha(enTete.getBytes(StandardCharsets.UTF_8))));
        byte[] ct = aes.doFinal(clair);
        byte[] morceau = new byte[12 + ct.length];
        System.arraycopy(iv, 0, morceau, 0, 12);
        System.arraycopy(ct, 0, morceau, 12, ct.length);
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        md.update(enTete.getBytes(StandardCharsets.UTF_8));
        md.update(morceau);
        String empreinte = HexFormat.of().formatHex(md.digest());
        mvc.perform(post("/api/candidat/offres").header("Authorization", jeton).contentType(JSON).content("{\"idDmc\":" + idDmc
                + ",\"lot\":null,\"enTete\":" + jsonTexte(enTete) + "}")).andExpect(status().isCreated());
        mvc.perform(put("/api/candidat/offres/" + idOffre + "/morceaux/0").header("Authorization", jeton).header("X-Empreinte", sha(morceau))
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(morceau)).andExpect(status().isOk());
        mvc.perform(post("/api/candidat/offres/" + idOffre + "/sceller").header("Authorization", jeton).contentType(JSON)
                .content("{\"empreinte\":\"" + empreinte + "\"}")).andExpect(status().isOk());
        return idOffre;
    }

    /** Le corps d'un apport : chaque part chiffrée servie, déchiffrée par la clé privée correspondante (le navigateur du détenteur). */
    private String apport(String partsChiffrees, String motif) throws Exception {
        List<Map<String, Object>> lignes = JsonPath.read(partsChiffrees, "$");
        StringBuilder b = new StringBuilder("{\"parts\":[");
        for (int i = 0; i < lignes.size(); i++) {
            Map<String, Object> l = lignes.get(i);
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            rsa.init(Cipher.DECRYPT_MODE, parEmpreinte.get((String) l.get("empreinteCle")).getPrivate(), OAEP);
            byte[] claire = rsa.doFinal(Base64.getDecoder().decode((String) l.get("part")));
            b.append(i == 0 ? "" : ",").append("{\"idOffre\":\"").append(l.get("idOffre")).append("\",\"partClaire\":\"")
                    .append(Base64.getEncoder().encodeToString(claire)).append("\"}");
        }
        b.append("]").append(motif == null ? "" : ",\"motif\":\"" + motif + "\"").append("}");
        return b.toString();
    }

    private final List<String> comptes = new ArrayList<>();

    private List<String> typesCao() {
        List<String> out = new ArrayList<>();
        for (String k : comptes) {
            notificationRepository.findPourRefEtType(k, "MEMBRE_CAO").forEach(n -> out.add(n.getTypeNotif()));
        }
        return out;
    }

    private static String sha(byte[] b) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
    }

    private static String jsonTexte(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void declarer(String jeton, String nif, String raison) throws Exception {
        mvc.perform(put("/api/candidat/entreprise").header("Authorization", jeton).contentType(JSON).content("{\"raisonSociale\":\"" + raison
                + "\",\"nif\":\"" + nif + "\",\"adresse\":\"Lot " + nif + "\",\"representant\":{\"nom\":\"Rakoto\",\"prenom\":\"Jean\"}}"))
                .andExpect(status().isOk());
    }

    /** Une fiche électronique validée, CAO, paramètres internes, cérémonie close avec trois vraies clés, avis posé, dépôts ouverts. */
    private void ficheEnLigne() throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        idDmc = ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"modeRemise\":\"ELECTRONIQUE\",\"garantieSoumission\":\"OUI\",\"alloti\":\"NON\","
                        + "\"variantes\":\"NON\",\"groupement\":\"NON\",\"provenance\":\"NATIONAL\",\"typePrix\":\"UNITAIRES\","
                        + "\"prixRevisable\":\"NON\",\"avance\":\"NON\",\"penalites\":\"CCAG\"}}"))
                .andExpect(status().isOk());
        besoinDeTest(idDmc);
        Map<String, String> donnees = new LinkedHashMap<>();
        donnees.put("B02-OB-03", "AOO 0004/MESupReS/2026");
        donnees.put("B04-CD-02", "C1");
        donnees.put("B04-LR-03", aujourdhui.plusDays(60).toString());
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
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", donnees);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isCreated());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenPrmp).contentType(JSON)
                .content(CaoIntegrationTest.corpsCao("m1@seance.mg", "m2@seance.mg"))).andExpect(status().isOk());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"quorum\":2,\"dateCeremonie\":\"" + aujourdhui.plusDays(9) + "T09:00\",\"depositaire\":{\"nom\":\"Rakoto Jean\"}}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"));
        String internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        comptes.clear();
        comptes.addAll(JsonPath.read(internes, "$.membresCommission[*].im"));
        jetonM1 = bearer("m1@seance.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, comptes.get(0), null);
        jetonM2 = bearer("m2@seance.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, comptes.get(1), null);
        parEmpreinte.clear();
        for (int i = 0; i < 3; i++) {
            KeyPair kp = PAIRES.get(i);
            String spki = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            CleDetenteur c = new CleDetenteur();
            c.setIdDmc(idDmc);
            c.setRole(i < 2 ? CleDetenteur.MEMBRE : CleDetenteur.SECOURS);
            c.setIm(i < 2 ? comptes.get(i) : null);
            c.setClePublique(spki);
            c.setEmpreinte(sha(kp.getPublic().getEncoded()));
            c.setEnvChiffre("AA==");
            c.setEnvIv("AA==");
            c.setEnvSel("AA==");
            c.setEnvIterations(600_000);
            c.setEnvKdf("PBKDF2-SHA-256");
            c.setEnvAlgorithme("AES-256-GCM");
            c.setEtatPart(CleDetenteur.PUBLIEE);
            c.setDatePublication(LocalDateTime.now());
            c.setRemplacements(0);
            cleRepository.save(c);
            parEmpreinte.put(c.getEmpreinte(), kp);
        }
        ceremonieRepository.save(new CeremonieCles(idDmc, CeremonieCles.CLOSE, LocalDateTime.now(), false, LocalDateTime.now(), null, null, null));
        int idFiche = JsonPath.read(mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
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
        changer("B04-SE-03", aujourdhui.minusDays(1) + "T08:00");
    }

    private void changer(String code, String valeur) throws Exception {
        int idFiche = JsonPath.read(mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString(), "$.idFiche");
        cnm.prs.entity.FicheMarcheValeur v = valeurRepository.findByIdFiche(idFiche).stream().filter(x -> x.getCodeChamp().equals(code))
                .findFirst().orElseGet(() -> new cnm.prs.entity.FicheMarcheValeur(null, idFiche, code, null, false));
        v.setValeur(valeur);
        valeurRepository.save(v);
    }
}
