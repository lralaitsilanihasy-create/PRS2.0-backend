package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Capm;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.FicheMarcheValeur;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Notification;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.FicheMarcheValeurRepository;
import cnm.prs.repository.RetraitDaoRepository;
import cnm.prs.service.ChampFicheMarcheService;
import cnm.prs.service.ParametreService;
import cnm.prs.service.RemiseElectronique;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1c, §B8) — les procédures ouvertes en ligne : critères de la
 * liste (validée, électronique, lancée par l'avis, date limite, signature simple), états {@code A_VENIR} / {@code OUVERTE} /
 * {@code CLOSE}, accès public à la liste et au détail, documents réservés au candidat, registre des retraits lu par la PRMP,
 * avertissement du bilan sur la signature (Q5).
 *
 * <p>Jeu : celui de {@code RemiseElectroniqueIntegrationTest} (plan 9900, ligne 9901 de fournitures à quantité fixe), dates
 * placées par rapport à aujourd'hui ; l'avis spécifique est posé directement (son impression a ses propres tests).</p>
 */
class ProceduresEnLigneIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String AVERTISSEMENT = "La plateforme n'accepte pour l'instant que la signature simple : la procédure "
            + "ne pourra pas s'ouvrir en ligne.";

    @Autowired private ChampFicheMarcheService champService;
    @Autowired private ParametreService parametres;
    @Autowired private CompteCandidatRepository candidats;
    @Autowired private DocumentFicheMarcheRepository documentRepository;
    @Autowired private FicheMarcheValeurRepository valeurRepository;
    @Autowired private RetraitDaoRepository retraitRepository;
    @Autowired private cnm.prs.repository.NotificationRepository notificationRepository;
    @Autowired private cnm.prs.repository.RecuJournalRepository recuJournal;

    private final LocalDate aujourdhui = LocalDate.now();
    private String tokenVer;
    private String jetonCandidat;
    private Long idDmc;
    private int idFiche;

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
        candidats.save(new CompteCandidat("C900000011", "retrait@entreprise.mg", "034 22 222 22", "Rabe", "Paul",
                CompteCandidat.CONFIRME, false, LocalDateTime.now(), LocalDateTime.now(), null, null));
        jetonCandidat = bearer("retrait@entreprise.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000011", null);
        // Q5 : par défaut l'Administrateur exige au moins « Avancée » (V50) ; la plateforme ne fait que « Simple ».
        RemiseElectronique.Parametres p = parametres.remiseElectronique();
        parametres.fixerRemiseElectronique(new RemiseElectronique.Parametres(p.plateformeUrl(), p.fuseau(), "Simple",
                p.tailleMaxPlateformeMo(), p.delaiMinRemiseJours(), p.assistance(), p.quorumDefaut()));
        idDmc = ficheElectroniqueValidee();
    }

    @Test
    @DisplayName("Liste : vide avant l'avis (détail 404) ; après l'avis la procédure paraît, à venir, lue sur la fiche ; ouverte "
            + "quand l'ouverture des dépôts est passée ; close (hors liste, lisible) une fois la date limite passée ; "
            + "une signature Avancée la retire (404)")
    void liste() throws Exception {
        mvc.perform(get("/api/procedures-en-ligne")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc)).andExpect(status().isNotFound());

        poserAvis();
        String liste = mvc.perform(get("/api/procedures-en-ligne")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(liste, "$[*].idDmc")).containsExactly(idDmc.intValue());
        String limite = ouvrable(aujourdhui.plusDays(60)) + "T10:00";
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("AOO 0001/MESupReS/2026"))
                .andExpect(jsonPath("$.objet").value("Acquisition de matériels informatiques"))
                .andExpect(jsonPath("$.categorie").value("FOURNITURES_SERVICES"))
                .andExpect(jsonPath("$.lots.length()").value(0))
                .andExpect(jsonPath("$.datePublication").value(aujourdhui.plusDays(10).toString()))
                .andExpect(jsonPath("$.dateOuvertureDepots").value(aujourdhui.plusDays(10) + "T08:00"))
                .andExpect(jsonPath("$.dateLimite").value(limite))
                .andExpect(jsonPath("$.signatureExigee").value("Simple"))
                .andExpect(jsonPath("$.tailleMaxFichierMo").value(50))
                .andExpect(jsonPath("$.tailleMaxOffreMo").value(500))
                .andExpect(jsonPath("$.assistance").isNotEmpty())
                .andExpect(jsonPath("$.etat").value("A_VENIR"))
                .andExpect(jsonPath("$.parametresInternes").doesNotExist())
                .andExpect(jsonPath("$.membresCommission").doesNotExist());

        changer("B04-SE-03", aujourdhui.minusDays(1) + "T08:00");
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc)).andExpect(jsonPath("$.etat").value("OUVERTE"));

        changer("B04-LR-03", aujourdhui.minusDays(1).toString());
        mvc.perform(get("/api/procedures-en-ligne")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("CLOSE"));

        changer("B04-LR-03", ouvrable(aujourdhui.plusDays(60)).toString());
        changer("B04-SE-05", "Avancée");
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc)).andExpect(status().isNotFound());
        mvc.perform(get("/api/procedures-en-ligne/999999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Retrait : documents réservés au candidat (sans session 401, agent 403) ; liste sans l'avis ; chaque "
            + "téléchargement inscrit au registre ; registre lu par la PRMP de la fiche seule ; document inconnu 404")
    void retrait() throws Exception {
        poserAvis();
        String url = "/api/procedures-en-ligne/" + idDmc + "/documents";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        String docs = mvc.perform(get(url).header("Authorization", jetonCandidat)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> codes = JsonPath.read(docs, "$[*].code");
        assertThat(codes).isNotEmpty().noneMatch(c -> c.startsWith("AVIS_")).anyMatch(c -> c.startsWith("DPAO_"));
        assertThat(JsonPath.<List<Integer>>read(docs, "$[*].version")).containsOnly(1);
        assertThat(JsonPath.<List<String>>read(docs, "$[*].intitule")).doesNotContainNull();

        String dpao = codes.stream().filter(c -> c.startsWith("DPAO_") && c.endsWith(".pdf")).findFirst().orElseThrow();
        byte[] contenu = mvc.perform(get(url + "/" + dpao).header("Authorization", jetonCandidat)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(dpao)))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(contenu, 0, 4)).isEqualTo("%PDF");
        mvc.perform(get(url + "/" + dpao).header("Authorization", jetonCandidat)).andExpect(status().isOk());
        mvc.perform(get(url + "/AVIS_inconnu.pdf").header("Authorization", jetonCandidat)).andExpect(status().isNotFound());
        mvc.perform(get(url + "/" + dpao).header("Authorization", tokenPrmp)).andExpect(status().isForbidden());
        assertThat(retraitRepository.findByIdDmcOrderByDateRetraitAscIdRetraitAsc(idDmc)).hasSize(2);

        String registre = mvc.perform(get("/api/fiches-marche/" + idDmc + "/retraits").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(registre, "$[*].compte")).containsExactly("retrait@entreprise.mg", "retrait@entreprise.mg");
        assertThat(JsonPath.<List<String>>read(registre, "$[*].document")).containsOnly(dpao);
        assertThat(JsonPath.<List<Integer>>read(registre, "$[*].version")).containsOnly(1);
        assertThat(JsonPath.<List<Object>>read(registre, "$[*].entreprise")).containsOnlyNulls();
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/retraits").header("Authorization", tokenAdmin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/retraits").header("Authorization", jetonCandidat)).andExpect(status().isForbidden());
        String autrePrmp = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/retraits").header("Authorization", autrePrmp)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Retrait après paiement (V72) : frais et compte servis ; sans reçu validé, le document répond 403 FRAIS_NON_REGLES "
            + "(rien au registre) ; le reçu se dépose (entreprise exigée, champs, type réel), attend la PRMP (RECU_EN_ATTENTE), se refuse "
            + "avec motif, se redépose, se valide par l'UGPM (décision définitive) ; le retrait s'ouvre et le registre dit son reçu ; "
            + "RECU-DAO déjà fourni ; journal et notifications")
    void retraitApresPaiement() throws Exception {
        poserAvis();
        poser("B04-DS-05", "50000");
        String procedure = mvc.perform(get("/api/procedures-en-ligne/" + idDmc)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(procedure, "$.retraitPayant")).isTrue();
        assertThat(JsonPath.<Number>read(procedure, "$.fraisDossier[0].montant").intValue()).isEqualTo(50000);
        assertThat(JsonPath.<Object>read(procedure, "$.fraisDossier[0].lot")).isNull();
        mvc.perform(put("/api/candidat/entreprise").header("Authorization", jetonCandidat).contentType(JSON).content("{\"raisonSociale\":"
                + "\"Info Plus\",\"nif\":\"3000111222\",\"adresse\":\"Lot 1\",\"representant\":{\"nom\":\"Rabe\",\"prenom\":\"Paul\"}}"))
                .andExpect(status().isOk());

        // §B4 — sans reçu validé : la liste oui, le document non ; rien au registre.
        String url = "/api/procedures-en-ligne/" + idDmc + "/documents";
        String dpao = JsonPath.<List<String>>read(mvc.perform(get(url).header("Authorization", jetonCandidat)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[*].code").stream().filter(c -> c.startsWith("DPAO_") && c.endsWith(".pdf"))
                .findFirst().orElseThrow();
        mvc.perform(get(url + "/" + dpao).header("Authorization", jetonCandidat)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FRAIS_NON_REGLES"));
        assertThat(retraitRepository.findByIdDmcOrderByDateRetraitAscIdRetraitAsc(idDmc)).isEmpty();
        assertThat(JsonPath.<List<Boolean>>read(mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/pieces").header("Authorization",
                jetonCandidat)).andReturn().getResponse().getContentAsString(), "$[?(@.code=='RECU-DAO')].dejaFourni")).containsExactly(false);

        // §B2 — le dépôt.
        String recus = "/api/procedures-en-ligne/" + idDmc + "/recus";
        byte[] pdf = "%PDF-1.4 reçu BNI".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String data = "{\"montant\":40000,\"referencePaiement\":\"VIR-2026-001\",\"datePaiement\":\"" + aujourdhui + "\",\"banque\":\"BNI\"}";
        candidats.save(new CompteCandidat("C900000012", "sans@entreprise.mg", "034 22 222 23", "Sans", "Entreprise",
                CompteCandidat.CONFIRME, false, LocalDateTime.now(), LocalDateTime.now(), null, null));
        String sansEntreprise = bearer("sans@entreprise.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000012", null);
        deposer(recus, sansEntreprise, pdf, data).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENTREPRISE_ABSENTE"));
        deposer(recus, jetonCandidat, pdf, "{\"montant\":40000}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[*].champ", org.hamcrest.Matchers.hasItems("referencePaiement", "datePaiement")));
        deposer(recus, jetonCandidat, "bonjour".getBytes(), data).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FORMAT_INVALIDE"));
        deposer(recus, tokenPrmp, pdf, data).andExpect(status().isForbidden());
        String depose = deposer(recus, jetonCandidat, pdf, data).andExpect(status().isCreated()).andExpect(jsonPath("$.etat").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.lots").isEmpty()).andExpect(jsonPath("$.entreprise").isEmpty())
                .andReturn().getResponse().getContentAsString();
        int premier = JsonPath.read(depose, "$.idRecu");
        deposer(recus, jetonCandidat, pdf, data).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECU_EN_ATTENTE"));
        mvc.perform(get(recus + "/mien").header("Authorization", jetonCandidat)).andExpect(status().isOk())
                .andExpect(jsonPath("$.referencePaiement").value("VIR-2026-001"));
        assertThat(new String(mvc.perform(get(recus + "/mien/fichier").header("Authorization", jetonCandidat)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
        mvc.perform(get(recus + "/mien").header("Authorization", sansEntreprise)).andExpect(status().isNotFound());
        assertThat(notificationRepository.findPourPrmp("PRMP001", null)).extracting(Notification::getTypeNotif).contains("RECU_A_VALIDER");

        // §B3 — la PRMP et l'UGPM ; ni l'Administrateur ni le candidat.
        String prmp = "/api/fiches-marche/" + idDmc + "/recus";
        mvc.perform(get(prmp).header("Authorization", tokenAdmin)).andExpect(status().isForbidden());
        mvc.perform(get(prmp).header("Authorization", jetonCandidat)).andExpect(status().isForbidden());
        mvc.perform(get(prmp).header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entreprise.nif").value("3000111222")).andExpect(jsonPath("$[0].compte").value("retrait@entreprise.mg"))
                .andExpect(jsonPath("$[0].fraisAttendus").value(50000)).andExpect(jsonPath("$[0].montantInsuffisant").value(true));
        mvc.perform(get(prmp + "/" + premier + "/fichier").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(post(prmp + "/" + premier + "/refuser").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_ABSENT"));
        mvc.perform(post(prmp + "/" + premier + "/refuser").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"Montant inférieur aux frais (50 000 Ar)\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("REFUSE")).andExpect(jsonPath("$.decidePar").value("PRMP"));
        mvc.perform(post(prmp + "/" + premier + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECU_DEJA_DECIDE"));
        assertThat(notificationRepository.findPourRefEtType("C900000011", "CANDIDAT")).extracting(Notification::getTypeNotif).contains("RECU_REFUSE");
        mvc.perform(get(url + "/" + dpao).header("Authorization", jetonCandidat)).andExpect(status().isForbidden());

        // Un nouveau reçu, validé par l'UGPM : le retrait s'ouvre, le registre dit le reçu ; un troisième n'est plus utile.
        String second = deposer(recus, jetonCandidat, pdf, data.replace("40000", "50000").replace("VIR-2026-001", "VIR-2026-002"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idSecond = JsonPath.read(second, "$.idRecu");
        String ugpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", "ANT");
        mvc.perform(get(prmp).header("Authorization", ugpm)).andExpect(status().isOk()).andExpect(jsonPath("$[0].etat").value("EN_ATTENTE"))
                .andExpect(jsonPath("$[1].etat").value("REFUSE")).andExpect(jsonPath("$[0].montantInsuffisant").value(false));
        mvc.perform(post(prmp + "/" + idSecond + "/valider").header("Authorization", ugpm)).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("VALIDE")).andExpect(jsonPath("$.decidePar").value("UGPM"));
        deposer(recus, jetonCandidat, pdf, data).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECU_DEJA_VALIDE"));
        assertThat(notificationRepository.findPourRefEtType("C900000011", "CANDIDAT")).extracting(Notification::getTypeNotif).contains("RECU_VALIDE");
        mvc.perform(get(url + "/" + dpao).header("Authorization", jetonCandidat)).andExpect(status().isOk());
        String registre = mvc.perform(get("/api/fiches-marche/" + idDmc + "/retraits").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(registre, "$[*].recu.etat")).containsExactly("VALIDE");
        assertThat(JsonPath.<List<String>>read(registre, "$[*].recu.referencePaiement")).containsExactly("VIR-2026-002");
        // §B5 — le reçu validé est la preuve du paiement : RECU-DAO n'est plus exigé, déjà fourni pour ce candidat.
        String pieces = mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/pieces").header("Authorization", jetonCandidat))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(pieces, "$[?(@.code=='RECU-DAO')].obligatoire")).containsExactly(false);
        assertThat(JsonPath.<List<Boolean>>read(pieces, "$[?(@.code=='RECU-DAO')].dejaFourni")).containsExactly(true);
        assertThat(recuJournal.findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction())
                .containsExactly("RECU_DEPOSE", "RECU_REFUSE", "RECU_DEPOSE", "RECU_VALIDE");
    }

    @Test
    @DisplayName("Retrait libre : un dossier sans frais, ou une procédure lancée avant la bascule (H2), se retire sans reçu")
    void retraitLibre() throws Exception {
        poserAvis();
        String url = "/api/procedures-en-ligne/" + idDmc + "/documents";
        String dpao = JsonPath.<List<String>>read(mvc.perform(get(url).header("Authorization", jetonCandidat)).andReturn().getResponse()
                .getContentAsString(), "$[*].code").stream().filter(c -> c.startsWith("DPAO_") && c.endsWith(".pdf")).findFirst().orElseThrow();
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc)).andExpect(jsonPath("$.retraitPayant").value(false))
                .andExpect(jsonPath("$.fraisDossier").isEmpty());
        mvc.perform(get(url + "/" + dpao).header("Authorization", jetonCandidat)).andExpect(status().isOk());
        // Des frais, mais l'avis imprimé avant la bascule : la procédure garde le retrait libre.
        poser("B04-DS-05", "50000");
        DocumentFicheMarche avis = documentRepository.findByIdFicheOrderByIdDocumentAsc(idFiche).stream().filter(d -> "AVIS".equals(d.getType()))
                .findFirst().orElseThrow();
        avis.setDateGeneration(LocalDateTime.of(2026, 1, 1, 0, 0));
        documentRepository.save(avis);
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc)).andExpect(jsonPath("$.retraitPayant").value(false))
                .andExpect(jsonPath("$.fraisDossier[0].montant").value(50000));
        mvc.perform(get(url + "/" + dpao).header("Authorization", jetonCandidat)).andExpect(status().isOk());
        deposer("/api/procedures-en-ligne/" + idDmc + "/recus", jetonCandidat, "%PDF".getBytes(), "{}").andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions deposer(String url, String jeton, byte[] fichier, String data) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(url)
                .file(new org.springframework.mock.web.MockMultipartFile("fichier", "recu.pdf", "application/pdf", fichier))
                .file(new org.springframework.mock.web.MockMultipartFile("data", "", "application/json", data.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .header("Authorization", jeton));
    }

    /** Pose (ou crée) une valeur de la version validée. */
    private void poser(String code, String valeur) {
        FicheMarcheValeur v = valeurRepository.findByIdFiche(idFiche).stream().filter(x -> x.getCodeChamp().equals(code)).findFirst()
                .orElseGet(() -> new FicheMarcheValeur(null, idFiche, code, null, false));
        v.setValeur(valeur);
        valeurRepository.save(v);
    }

    // ------------------------------------------------------------------ outils

    /**
     * Une fiche électronique validée : l'avertissement de signature paraît au bilan tant que B04-SE-05 vaut « Avancée »,
     * disparaît à « Simple » ; responsable et paramètres internes complets.
     */
    private Long ficheElectroniqueValidee() throws Exception {
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
        donnees.put("B02-OB-03", "AOO 0001/MESupReS/2026");
        donnees.put("B04-CD-02", "C1");
        donnees.put("B04-LR-03", ouvrable(aujourdhui.plusDays(60)).toString());
        donnees.put("B04-LR-04", "10:00");
        donnees.put("B04-SE-02", "https://depot.cnm.mg");
        donnees.put("B04-SE-03", aujourdhui.plusDays(10) + "T08:00");
        donnees.put("B04-SE-05", "Avancée");
        donnees.put("B04-SE-06", "À définir par l'Administrateur (liste officielle des prestataires de certification)");
        donnees.put("B04-SE-17", aujourdhui.plusDays(10) + "T08:00");
        donnees.put("B05-GS-03", "1600000");
        donnees.put("B05-GS-04", "105");
        donnees.put("B04-VO-01", "75");
        remplirObligatoires(dmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", donnees);
        mvc.perform(get("/api/fiches-marche/" + dmc).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.bilanControles.avertissements[?(@.regle=='SIGNATURE_EN_LIGNE')].message").value(AVERTISSEMENT))
                .andExpect(jsonPath("$.bilanControles.avertissements[?(@.regle=='SIGNATURE_EN_LIGNE')].champs[0]").value("B04-SE-05"));
        donnees.put("B04-SE-05", "Simple");   // un PUT de bloc le remplacerait en entier
        remplirObligatoires(dmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", donnees);
        String fiche = mvc.perform(get("/api/fiches-marche/" + dmc).header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.avertissements[*].regle")).doesNotContain("SIGNATURE_EN_LIGNE");
        idFiche = JsonPath.read(fiche, "$.idFiche");

        // ⚠️ V67 (lot 2a, Q11) — les membres détenteurs de parts sont ceux de la CAO, désignée par la PRMP.
        mvc.perform(put("/api/fiches-marche/" + dmc + "/cao").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"decision\":{\"reference\":\"DEC-001/2026\",\"date\":\"2026-09-30\"},\"membres\":["
                        + "{\"nom\":\"Rabe\",\"prenom\":\"Paul\",\"email\":\"m1@cao.mg\",\"qualite\":\"MEMBRE\",\"origine\":\"ENTITE_CONTRACTANTE\",\"service\":\"DAF\",\"president\":true},"
                        + "{\"nom\":\"Rasoa\",\"prenom\":\"Lova\",\"email\":\"m2@cao.mg\",\"qualite\":\"MEMBRE\",\"origine\":\"EXPERT_OBJET\",\"organisme\":\"Université\",\"domaine\":\"Informatique\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/fiches-marche/" + dmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isCreated());
        mvc.perform(put("/api/fiches-marche/" + dmc + "/parametres-internes").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"quorum\":2,\"depositaire\":{\"nom\":\"Rakoto Jean\",\"email\":\"rakoto.depositaire@secours.mg\"},\"dateCeremonie\":\""
                        + aujourdhui.plusDays(9) + "T09:00\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/fiches-marche/" + dmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"));
        return dmc;
    }

    /** L'avis spécifique imprimé (la procédure est lancée), avec sa date de publication. */
    private void poserAvis() {
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

    /** Change une valeur de la version validée (le temps qui passe, sans attendre). */
    private void changer(String code, String valeur) {
        FicheMarcheValeur v = valeurRepository.findByIdFiche(idFiche).stream().filter(x -> x.getCodeChamp().equals(code))
                .findFirst().orElseThrow();
        v.setValeur(valeur);
        valeurRepository.save(v);
    }
}
