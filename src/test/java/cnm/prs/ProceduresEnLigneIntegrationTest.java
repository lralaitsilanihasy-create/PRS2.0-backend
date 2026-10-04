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
        String limite = aujourdhui.plusDays(60) + "T10:00";
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

        changer("B04-LR-03", aujourdhui.plusDays(60).toString());
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
        donnees.put("B04-LR-03", aujourdhui.plusDays(60).toString());
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

        mvc.perform(post("/api/fiches-marche/" + dmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isCreated());
        mvc.perform(put("/api/fiches-marche/" + dmc + "/parametres-internes").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"membresCommission\":[\"CTRMEM\",\"CTRCC1\"],\"quorum\":2,\"dateCeremonie\":\""
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
