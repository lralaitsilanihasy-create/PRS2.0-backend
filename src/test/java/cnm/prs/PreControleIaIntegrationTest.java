package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import cnm.prs.entity.Anomalie;
import cnm.prs.entity.Compte;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.entity.SoaBeneficiaire;
import cnm.prs.enums.CategorieModePassation;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.SourceSignalement;
import cnm.prs.enums.StatutSignalement;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeSignalement;
import cnm.prs.repository.AnomalieLigneRepository;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.CompteRepository;
import cnm.prs.repository.RegleAnomalieRepository;
import cnm.prs.seed.ReglesPreControleSeeder;
import cnm.prs.service.PreControlePpmService;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 6) — <strong>la couche IA</strong>, contre
 * un faux serveur d'inférence à l'API compatible OpenAI (même harnais que le lot 1).
 *
 * <p>Ce que ces tests protègent, et qui est l'essentiel de cette étape : <strong>rien de ce que le modèle
 * rend n'est cru sur parole</strong>. Une piste sur une ligne inexistante, un type réservé aux règles, une
 * réponse illisible, un modèle bavard : tout cela doit être écarté sans bruit, et surtout sans jamais
 * devenir un constat opposable dans l'écran d'une PRMP.</p>
 *
 * <p>Et les invariants du lot restent tenus : une piste naît <strong>en piste</strong>
 * ({@code source = IA}), elle est <strong>écartable comme les autres</strong>, une analyse ne touche
 * <strong>jamais</strong> aux constats des règles, et chaque analyse est <strong>journalisée</strong>.</p>
 */
class PreControleIaIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER = 780;
    private static final int PPM = 780;
    private static final String MODELE = "modele-test";
    private static final HttpServer SERVEUR;
    private static final AtomicReference<String> REPONSE = new AtomicReference<>("{}");
    /** Le type de piste dont la réponse ci-dessus est la réponse ; les autres passes se taisent. */
    private static final AtomicReference<String> PASSE = new AtomicReference<>(null);
    private static final AtomicReference<String> DERNIERE_REQUETE = new AtomicReference<>("");
    private static final AtomicInteger APPELS = new AtomicInteger();
    private static final AtomicReference<Boolean> EN_PANNE = new AtomicReference<>(false);

    static {
        try {
            SERVEUR = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVEUR.createContext("/v1/models", ex -> repondre(ex, 200, "application/json",
                    "{\"data\":[{\"id\":\"" + MODELE + "\"}]}"));
            SERVEUR.createContext("/v1/chat/completions", PreControleIaIntegrationTest::completer);
            SERVEUR.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void proprietesAssistant(DynamicPropertyRegistry registry) {
        registry.add("app.ia.actif", () -> "true");
        registry.add("app.ia.base-url", () -> "http://127.0.0.1:" + SERVEUR.getAddress().getPort() + "/v1");
        registry.add("app.ia.modele", () -> MODELE);
        registry.add("app.ia.timeout-secondes", () -> "20");
    }

    /**
     * Faux serveur : rend, en un seul morceau, le JSON que le test a préparé <strong>pour la passe
     * demandée</strong>.
     *
     * <p>⚠️ Le service interroge le modèle <strong>une fois par type de piste</strong> (leçon de la
     * batterie de qualité : une question unique faisait se contredire un modèle de 9 milliards de
     * paramètres). Un faux serveur qui rendrait la même réponse aux trois passes créerait trois pistes là
     * où le test n'en attend qu'une — ce n'est pas le produit qui se tromperait, c'est le harnais. La
     * consigne nomme sa recherche en tête ({@code Recherche demandée : …}), et c'est ce que l'on lit ici
     * pour répondre à la bonne question. Les passes non préparées répondent « rien à signaler ».</p>
     */
    private static void completer(HttpExchange ex) throws IOException {
        String requete = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        DERNIERE_REQUETE.set(requete);
        APPELS.incrementAndGet();
        if (Boolean.TRUE.equals(EN_PANNE.get())) {
            repondre(ex, 500, "text/plain", "boom");
            return;
        }
        ex.getResponseHeaders().add("Content-Type", "text/event-stream");
        ex.sendResponseHeaders(200, 0);
        try (OutputStream out = ex.getResponseBody()) {
            String contenu = reponsePour(requete).replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n");
            out.write(("data: {\"choices\":[{\"delta\":{\"content\":\"" + contenu + "\"}}]}\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    /** La réponse préparée pour la passe que cette requête demande ; sinon, « rien à signaler ». */
    private static String reponsePour(String requete) {
        String passe = PASSE.get();
        return passe == null || requete.contains("Recherche demandée : " + passe)
                ? REPONSE.get() : "{\"pistes\":[]}";
    }

    private static void repondre(HttpExchange ex, int code, String type, String corps) throws IOException {
        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(code, octets.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(octets);
        }
    }

    @Autowired private ReglesPreControleSeeder seeder;
    @Autowired private PreControlePpmService preControle;
    @Autowired private AnomalieRepository anomalieRepository;
    @Autowired private AnomalieLigneRepository anomalieLigneRepository;
    @Autowired private RegleAnomalieRepository regleAnomalieRepository;
    @Autowired private CompteRepository compteRepository;

    @BeforeEach
    void planEtAssistant() {
        EN_PANNE.set(false);
        APPELS.set(0);
        REPONSE.set("{}");
        PASSE.set(null);

        natureRepository.save(new Nature(1, "Travaux", "Marches de travaux"));
        ModePassation aoo = new ModePassation();
        aoo.setIdMode(1);
        aoo.setLibelle("Appel d'offres ouvert");
        aoo.setCategorie(CategorieModePassation.NORMAL);
        modePassationRepository.save(aoo);
        SoaBeneficiaire soa = new SoaBeneficiaire();
        soa.setSoaCode("SOA-780");
        soa.setLibelle("Service beneficiaire de test");
        soaBeneficiaireRepository.save(soa);

        Dossier d = dossier(DOSSIER, "BROUILLON");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        Ppm p = ppm(PPM, DOSSIER, "PRMP001");
        p.setIdLocalite("ANT");
        p.setDateSignature(LocalDate.of(2026, 1, 10));
        ppmRepository.save(p);

        // Deux lignes que la règle du compte ne peut PAS rapprocher : comptes différents, même route.
        ligne(7801, "Entretien de la RN2 du PK 12 au PK 30", "23110", "400000000");
        ligne(7802, "Travaux d'entretien routier RN 2, section PK 30 à PK 45", "23119", "380000000");

        seeder.run();
        entityManager.flush();
        entityManager.clear();
    }

    private void ligne(int idDetail, String designation, String numCompte, String montant) {
        Marche m = marche(idDetail, DOSSIER, PPM);
        m.setIdNature(1);
        m.setIdMode(1);
        m.setMontEstim(new BigDecimal(montant));
        m.setDesignationMarche(designation);
        m.setFinancement("RPI");
        m.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        marcheRepository.save(m);
        if (!compteRepository.existsById(numCompte)) {
            Compte c = new Compte();
            c.setNumCompte(numCompte);
            c.setLibelle("Compte " + numCompte);
            compteRepository.save(c);
        }
        ServiceBeneficiaire b = new ServiceBeneficiaire();
        b.setIdBenef(idDetail * 10);
        b.setIdDetail(idDetail);
        b.setSoaCode("SOA-780");
        b.setNumCompte(numCompte);
        serviceBeneficiaireRepository.save(b);
    }

    private List<Anomalie> signalements() {
        return anomalieRepository.findByIdPpmOrderByIdAnomalie(PPM);
    }

    private List<Anomalie> pistes() {
        return signalements().stream().filter(a -> SourceSignalement.IA.name().equals(a.getSource())).toList();
    }

    // ------------------------------------------------------------------ 1. ce que l'assistant apporte

    @Test
    @DisplayName("Le fractionnement déguisé — même route sous deux comptes différents — devient une PISTE, "
            + "jamais un fait : source IA, et la phrase de hiérarchisation est rendue à l'écran")
    void fractionnementDeguise_devientUnePiste() throws Exception {
        PASSE.set(TypeSignalement.FRACTIONNEMENT_DEGUISE.name());
        REPONSE.set("""
                {"synthese":"Deux lignes semblent couvrir le même entretien de la RN 2 : à regarder d'abord.",
                 "pistes":[{"type":"FRACTIONNEMENT_DEGUISE","lignes":[7801,7802],
                 "constat":"Ces deux lignes semblent viser le même entretien de la RN 2, sous deux comptes différents.",
                 "suggestion":"Au lieu de : deux lignes.\\nLire : une seule opération, éventuellement allotie."}]}
                """);

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                // La phrase « où regarder d'abord » est composée par le serveur à partir des pistes
                // retenues, pas rendue par le modèle : une phrase générée de plus n'apporterait rien.
                .andExpect(jsonPath("$.synthese", containsString("À regarder d'abord")))
                .andExpect(jsonPath("$.synthese", containsString("7801")))
                .andExpect(jsonPath("$.resume.idPpm").value(PPM));
        entityManager.flush();
        entityManager.clear();

        List<Anomalie> pistes = pistes();
        assertThat(pistes).hasSize(1);
        assertThat(pistes.get(0).getTypeAnomalie()).isEqualTo(TypeSignalement.FRACTIONNEMENT_DEGUISE.name());
        assertThat(pistes.get(0).getSource()).isEqualTo(SourceSignalement.IA.name());
        assertThat(pistes.get(0).getStatut()).isEqualTo(StatutSignalement.OUVERT.name());
        assertThat(pistes.get(0).getDescription()).contains("semblent viser le même entretien");
        assertThat(anomalieLigneRepository.findByIdAnomalie(pistes.get(0).getIdAnomalie())).hasSize(2);
    }

    @Test
    @DisplayName("Le modèle reçoit les lignes du plan — objet, nature, compte, montant — et AUCUNE donnée "
            + "d'acteur : il n'a pas besoin de savoir de qui est ce plan pour juger d'un libellé")
    void leModele_neRecoitQueLesLignesDuPlan() throws Exception {
        REPONSE.set("{\"pistes\":[]}");

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        String envoye = DERNIERE_REQUETE.get();
        assertThat(envoye).contains("Entretien de la RN2", "23110", "Travaux", "400 000 000 Ar");
        assertThat(envoye).doesNotContain("PRMP001", "MEMANT1", "tokenPrmp");
    }

    // ------------------------------------------------------------------ 2. ce qu'on refuse au modèle

    @Test
    @DisplayName("Une piste sur une ligne qui n'existe pas dans ce plan est jetée : une hallucination ne "
            + "devient jamais un signalement")
    void ligneInventee_jetee() throws Exception {
        PASSE.set(TypeSignalement.OBJET_IMPRECIS.name());
        REPONSE.set("{\"pistes\":[{\"type\":\"OBJET_IMPRECIS\",\"lignes\":[999999],"
                + "\"constat\":\"Objet trop vague sur cette ligne.\"}]}");

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        assertThat(pistes()).isEmpty();
    }

    @Test
    @DisplayName("Le type d'une piste ne vient JAMAIS du modèle : il est celui de la passe. Un modèle qui "
            + "réclamerait un type de règle — le mode, les seuils — n'obtient pas le terrain des faits")
    void leTypeNeVientJamaisDuModele() throws Exception {
        PASSE.set(TypeSignalement.FRACTIONNEMENT_DEGUISE.name());
        REPONSE.set("{\"pistes\":[{\"type\":\"MODE_SOUS_LE_SEUIL\",\"lignes\":[7801,7802],"
                + "\"constat\":\"Le mode me semble insuffisant sur ces deux lignes.\"}]}");

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        // La piste existe — le modèle a bien vu deux lignes — mais elle porte le type de la PASSE, et sa
        // source est IA. Aucun constat de règle n'a pu naître d'une réponse de modèle.
        assertThat(pistes()).hasSize(1);
        assertThat(pistes().get(0).getTypeAnomalie())
                .isEqualTo(TypeSignalement.FRACTIONNEMENT_DEGUISE.name());
        assertThat(pistes().get(0).getSource()).isEqualTo(SourceSignalement.IA.name());
        assertThat(signalements())
                .noneMatch(a -> TypeSignalement.MODE_SOUS_LE_SEUIL.name().equals(a.getTypeAnomalie())
                        && SourceSignalement.IA.name().equals(a.getSource()));
    }

    @Test
    @DisplayName("Une piste de fractionnement sur des lignes du MÊME compte est refusée par le serveur : "
            + "ce cas est celui d'une règle opposable, le redire en piste ne serait que du bruit")
    void fractionnementSurUnMemeCompte_refuseParLeServeur() throws Exception {
        // Deux lignes de plus, sur un même compte : c'est le terrain exact de FRACTIONNEMENT_COMPTE.
        ligne(7803, "Fourniture de tables pour l'EPP d'Ambohimanga", "61125", "60000000");
        ligne(7804, "Fourniture de bancs pour l'EPP d'Ambohimanga", "61125", "60000000");
        entityManager.flush();
        entityManager.clear();
        PASSE.set(TypeSignalement.FRACTIONNEMENT_DEGUISE.name());
        REPONSE.set("{\"pistes\":[{\"lignes\":[7803,7804],"
                + "\"constat\":\"Ces deux lignes semblent équiper la même école.\"}]}");

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        assertThat(pistes()).isEmpty();
    }

    @Test
    @DisplayName("Une réponse illisible ne fait pas échouer l'analyse : aucune piste, et l'écran reste servi")
    void reponseIllisible_aucunePisteEtAucuneErreur() throws Exception {
        REPONSE.set("Je ne peux pas répondre à cette demande.");

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resume.idPpm").value(PPM));
        entityManager.flush();
        entityManager.clear();

        assertThat(pistes()).isEmpty();
    }

    @Test
    @DisplayName("Un modèle bavard est plafonné : au-delà de la limite, les pistes sont ignorées — la "
            + "fatigue d'alerte tue ces fonctionnalités")
    void modeleBavard_plafonne() throws Exception {
        StringBuilder brutes = new StringBuilder("{\"pistes\":[");
        for (int i = 0; i < 20; i++) {
            brutes.append(i == 0 ? "" : ",")
                    .append("{\"type\":\"OBJET_IMPRECIS\",\"lignes\":[")
                    .append(i % 2 == 0 ? 7801 : 7802)
                    .append("],\"constat\":\"Objet peu explicite, remarque n° ").append(i).append(".\"}");
        }
        PASSE.set(TypeSignalement.OBJET_IMPRECIS.name());
        REPONSE.set(brutes.append("]}").toString());

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        // Deux lignes seulement : les clés se répètent, et les doublons sont écartés avec le plafond.
        assertThat(pistes()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Serveur d'inférence en panne : 503 explicite, et les constats des règles restent intacts")
    void serveurEnPanne_503EtReglesIntactes() throws Exception {
        preControle.executer(PPM);
        entityManager.flush();
        int constatsDesRegles = signalements().size();
        EN_PANNE.set(true);

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("règles")));
        entityManager.flush();
        entityManager.clear();

        assertThat(signalements()).hasSize(constatsDesRegles);
        assertThat(pistes()).isEmpty();
    }

    // ------------------------------------------------------------------ 3. les invariants du lot

    @Test
    @DisplayName("Une analyse ne touche jamais aux constats des règles, et une exécution des règles ne "
            + "lève jamais une piste : deux populations, deux mécanismes")
    void lesDeuxPopulations_neSeMelangentJamais() throws Exception {
        PASSE.set(TypeSignalement.FRACTIONNEMENT_DEGUISE.name());
        REPONSE.set("{\"pistes\":[{\"type\":\"FRACTIONNEMENT_DEGUISE\",\"lignes\":[7801,7802],"
                + "\"constat\":\"Même route, deux comptes.\"}]}");
        preControle.executer(PPM);
        entityManager.flush();
        int constatsDesRegles = (int) signalements().stream()
                .filter(a -> SourceSignalement.REGLE.name().equals(a.getSource())).count();

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertThat(pistes()).hasSize(1);

        // Les règles retournent : la piste doit survivre, intacte et ouverte.
        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();
        assertThat(pistes()).hasSize(1);
        assertThat(pistes().get(0).getStatut()).isEqualTo(StatutSignalement.OUVERT.name());
        assertThat(signalements().stream()
                .filter(a -> SourceSignalement.REGLE.name().equals(a.getSource())).count())
                .isEqualTo(constatsDesRegles);
    }

    @Test
    @DisplayName("Une piste s'écarte comme un constat, avec motif — et une nouvelle analyse la retrouve "
            + "écartée : la PRMP ne remotive pas à chaque fois")
    void piste_ecartableEtRetrouvee() throws Exception {
        PASSE.set(TypeSignalement.FRACTIONNEMENT_DEGUISE.name());
        REPONSE.set("{\"pistes\":[{\"type\":\"FRACTIONNEMENT_DEGUISE\",\"lignes\":[7801,7802],"
                + "\"constat\":\"Même route, deux comptes.\"}]}");
        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        int id = pistes().get(0).getIdAnomalie();

        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenPrmp)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"Deux sections distinctes de la RN 2, marchés indépendants.\","
                        + "\"avertissementLu\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("IA"));
        entityManager.flush();
        entityManager.clear();

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        List<Anomalie> apres = pistes();
        assertThat(apres).hasSize(1);
        assertThat(apres.get(0).getIdAnomalie()).isEqualTo(id);
        assertThat(apres.get(0).getStatut()).isEqualTo(StatutSignalement.ECARTE.name());
        assertThat(apres.get(0).getCommentaireTraitement()).contains("Deux sections distinctes");
        assertThat(apres.get(0).getTypeActeurTraitement()).isEqualTo(TypeActeur.PRMP.name());
    }

    @Test
    @DisplayName("Un type de piste éteint depuis l'administration n'est plus proposé au modèle, et ses "
            + "anciennes pistes ne sont pas levées")
    void typeEteint_nEstPlusPropose() throws Exception {
        PASSE.set(TypeSignalement.FRACTIONNEMENT_DEGUISE.name());
        REPONSE.set("{\"pistes\":[{\"type\":\"FRACTIONNEMENT_DEGUISE\",\"lignes\":[7801,7802],"
                + "\"constat\":\"Même route, deux comptes.\"}]}");
        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertThat(pistes()).hasSize(1);

        RegleAnomalie regle = regleAnomalieRepository
                .findByCodeRegle(TypeSignalement.FRACTIONNEMENT_DEGUISE.name()).orElseThrow();
        regle.setActif(Boolean.FALSE);
        regleAnomalieRepository.save(regle);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        assertThat(pistes()).hasSize(1);
        assertThat(pistes().get(0).getStatut()).isEqualTo(StatutSignalement.OUVERT.name());
    }

    @Test
    @DisplayName("Chaque analyse est journalisée : le plan examiné, le modèle et le nombre de pistes — on "
            + "doit toujours savoir ce que l'assistant a dit et sur quoi")
    void analyse_journalisee() throws Exception {
        PASSE.set(TypeSignalement.OBJET_IMPRECIS.name());
        REPONSE.set("{\"pistes\":[{\"type\":\"OBJET_IMPRECIS\",\"lignes\":[7801],"
                + "\"constat\":\"L'objet ne dit pas la consistance des travaux.\"}]}");

        mvc.perform(post("/api/pre-controle/ppm/" + PPM + "/analyse-ia").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        entityManager.flush();

        assertThat(auditLogRepository.findAll()).anySatisfy(ligne -> {
            assertThat(ligne.getNomTable()).isEqualTo("assistant_ia");
            assertThat(ligne.getTypeAction()).isEqualTo("ANALYSE_PRE_CONTROLE");
            assertThat(ligne.getChampModifie()).isEqualTo(MODELE);
            assertThat(ligne.getNouvelleValeur()).contains("Pistes retenues : 1");
        });
    }
}
