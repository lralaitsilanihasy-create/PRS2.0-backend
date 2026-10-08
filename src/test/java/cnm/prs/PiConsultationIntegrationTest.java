package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import cnm.prs.entity.CeremonieCles;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Notification;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ 2026-10-08 (lot 3 PI, tranche PI-a, §B1, Q3, Q6 ; V84) — les sous-critères techniques de la fiche (somme contrôlée au bilan,
 * copiés à la révision), le budget disponible exigé par la méthode du budget prédéterminé ; la consultation restreinte : les invités
 * écrits à l'impression des lettres (adresse électronique saisie), leur lettre, la procédure visible de ses seuls invités. Jeu : celui
 * des lettres d'invitation.
 */
class PiConsultationIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String METHODE = "Qualité technique, expérience et proposition financière";

    @Autowired private cnm.prs.service.ChampFicheMarcheService champService;
    @Autowired private cnm.prs.repository.CompteCandidatRepository candidats;
    @Autowired private cnm.prs.repository.NotificationRepository notificationRepository;
    @Autowired private cnm.prs.repository.CeremonieClesRepository ceremonieRepository;
    @Autowired private cnm.prs.service.ParametreService parametres;

    private final LocalDate aujourdhui = LocalDate.now();
    private String jetonA;
    private String jetonB;

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
        natureRepository.save(new Nature(94, "Prestations intellectuelles", null, "PRESTATIONS_INTELLECTUELLES"));
        Marche l = marche(9901, 9900, 9900);
        l.setIdMode(92);
        l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        l.setIdNature(94);
        l.setDesignationMarche("Étude de faisabilité du schéma directeur");
        marcheRepository.save(l);
        Marche f = marcheDao(9902, 9900, 9900);
        f.setIdMode(92);
        f.setDesignationMarche("Fourniture de mobilier de bureau");
        marcheRepository.save(f);
        champService.importerCsv(new ClassPathResource(
                "fiche-marche/referentiel-champs-fiche-dao-prestations-intellectuelles.csv").getFile().toPath());
        candidats.save(new CompteCandidat("C900000071", "a@pi.mg", "034 71 711 71", "Rabe", "Paul", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        candidats.save(new CompteCandidat("C900000072", "b@pi.mg", "034 72 722 72", "Rasoa", "Lova", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        jetonA = bearer("a@pi.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000071", null);
        jetonB = bearer("b@pi.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000072", null);
    }

    @Test
    @DisplayName("Sous-critères : PI seule, lignes contrôlées, somme par critère au bilan (bloquant), copiés à la révision ; la méthode du "
            + "budget prédéterminé exige le budget disponible")
    void sousCriteresEtBudget() throws Exception {
        Long idDmc = creerDmc(9901);
        Long fournitures = creerDmc(9902);
        String url = "/api/fiches-marche/" + idDmc + "/sous-criteres";
        mvc.perform(put("/api/fiches-marche/" + fournitures + "/sous-criteres").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"sousCriteres\":[]}")).andExpect(status().isConflict());
        mvc.perform(put(url).header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"sousCriteres\":[{\"critere\":\"B06-TP-09\",\"libelle\":\"x\",\"points\":5},{\"critere\":\"B06-TP-03\",\"libelle\":\" \",\"points\":0}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erreurs[*].champ").value(org.hamcrest.Matchers.hasItems(
                        "sousCriteres[0].critere", "sousCriteres[1].libelle", "sousCriteres[1].points")));
        mvc.perform(put(url).header("Authorization", tokenPrmp).contentType(JSON).content("{\"sousCriteres\":["
                + "{\"critere\":\"B06-TP-03\",\"libelle\":\"Approche technique et méthodologie\",\"points\":10},"
                + "{\"critere\":\"B06-TP-03\",\"libelle\":\"Plan de travail\",\"points\":10},"
                + "{\"critere\":\"B06-TP-03\",\"libelle\":\"Organisation et personnel\",\"points\":5},"
                + "{\"critere\":\"B06-TP-04\",\"libelle\":\"Chef de mission\",\"points\":20},"
                + "{\"critere\":\"B06-TP-04\",\"libelle\":\"Expert en planification urbaine\",\"points\":10}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5)).andExpect(jsonPath("$[4].ordre").value(5));
        Map<String, String> donnees = new LinkedHashMap<>();
        donnees.put("B02-MS-01", METHODE);
        donnees.put("B06-TP-03", "25");
        donnees.put("B06-TP-04", "35");
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES", donnees);
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='SOUS_CRITERES_POINTS')].champs[0]"))
                .containsExactly("B06-TP-04");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.ok[?(@.regle=='SOUS_CRITERES_POINTS')].champs[0]")).containsExactly("B06-TP-03");
        donnees.put("B02-MS-01", "Budget prédéterminé dont le candidat propose la meilleure utilisation");
        donnees.put("B06-TP-04", "30");
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES", donnees);
        fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(fiche, "$.bilanControles.bloquants[?(@.regle=='SOUS_CRITERES_POINTS')]")).isEmpty();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[*].regle")).contains("BUDGET_DISPONIBLE_ABSENT");
        donnees.put("B05-PF-13", "80000000");
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES", donnees);
        // Validée, la fiche ne s'écrit plus ; la révision recopie les sous-critères.
        mvc.perform(put(url).header("Authorization", tokenPrmp).contentType(JSON).content("{\"sousCriteres\":[]}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[3].libelle").value("Chef de mission"));
    }

    @Test
    @DisplayName("Consultation restreinte : fiche PI en ligne lancée par ses lettres ; invités écrits (adresse électronique), lettre, "
            + "notification ; hors de la liste publique, visible et retirable par les seuls invités")
    void consultationRestreinte() throws Exception {
        Long idDmc = ficheEnLigne();
        ceremonieRepository.save(new CeremonieCles(idDmc, CeremonieCles.CLOSE, LocalDateTime.now(), false, LocalDateTime.now(), null, null, null));
        int idDossier = JsonPath.read(mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.idDossier");
        receptionRepository.save(reception(9950, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(9950, 9950, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9950, 9950, "CTRMEM"));
        seedPvSigne(9950, 9950);
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc).header("Authorization", jetonA)).andExpect(status().isNotFound());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/lettres-invitation").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateEnvoi\":\"" + aujourdhui + "\",\"lieu\":\"Antananarivo\",\"candidats\":[{\"nom\":\"Cabinet A\",\"adresse\":\"Lot A\","
                        + "\"email\":\"A@pi.mg\"},{\"nom\":\"Bureau Z\",\"adresse\":\"Lot Z\",\"email\":\"pas-une-adresse\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/lettres-invitation").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateEnvoi\":\"" + aujourdhui + "\",\"lieu\":\"Antananarivo\",\"candidats\":[{\"nom\":\"Cabinet A\",\"adresse\":\"Lot A\","
                        + "\"email\":\"A@pi.mg\"},{\"nom\":\"Bureau Z\",\"adresse\":\"Lot Z\",\"email\":\"z@pi.mg\"}]}"))
                .andExpect(status().isCreated());
        assertThat(notificationRepository.findAll()).filteredOn(n -> "LETTRE_INVITATION".equals(n.getTypeNotif()))
                .extracting(Notification::getDestinataireEmail).containsExactlyInAnyOrder("A@pi.mg", "z@pi.mg");
        mvc.perform(get("/api/candidat/invitations").header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idDmc").value(idDmc)).andExpect(jsonPath("$[0].rang").value(1))
                .andExpect(jsonPath("$[0].source").value("SAISIE")).andExpect(jsonPath("$[0].lettreDisponible").value(true));
        mvc.perform(get("/api/candidat/invitations").header("Authorization", jetonB)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/candidat/invitations/" + idDmc + "/lettre").header("Authorization", jetonA)).andExpect(status().isOk());
        mvc.perform(get("/api/candidat/invitations/" + idDmc + "/lettre").header("Authorization", jetonB)).andExpect(status().isNotFound());
        // La procédure : hors de la liste publique ; visible de l'invité seul ; ses documents retirés par lui seul.
        mvc.perform(get("/api/procedures-en-ligne")).andExpect(jsonPath("$[?(@.idDmc == " + idDmc + ")]").isEmpty());
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc)).andExpect(status().isNotFound());
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc).header("Authorization", jetonB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc).header("Authorization", jetonA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value("PRESTATIONS_INTELLECTUELLES"));
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/documents").header("Authorization", jetonB)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NON_INVITE"));
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/documents").header("Authorization", jetonA)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("⚠️ PI-b : deux enveloppes (TECHNIQUE, FINANCIERE) exigées et uniques par proposition, invité seul, même numéro, accusé "
            + "portant l'autre empreinte, retrait conjoint, registre qui compte la proposition une fois")
    void deuxEnveloppes() throws Exception {
        Long idDmc = ficheEnLigne();
        String internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes")
                .header("Authorization", bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT")))
                .andReturn().getResponse().getContentAsString();
        List<String> membres = JsonPath.read(internes, "$.membresCommission[*].im");
        int i = 0;
        for (String im : membres) {
            cleRepository.save(cle(idDmc, cnm.prs.entity.CleDetenteur.MEMBRE, im, i++));
        }
        cleRepository.save(cle(idDmc, cnm.prs.entity.CleDetenteur.SECOURS, null, i));
        ceremonieRepository.save(new CeremonieCles(idDmc, CeremonieCles.CLOSE, LocalDateTime.now(), false, LocalDateTime.now(), null, null, null));
        int idDossier = JsonPath.read(mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.idDossier");
        receptionRepository.save(reception(9950, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(9950, 9950, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9950, 9950, "CTRMEM"));
        seedPvSigne(9950, 9950);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/lettres-invitation").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateEnvoi\":\"" + aujourdhui + "\",\"lieu\":\"Antananarivo\",\"candidats\":[{\"nom\":\"Cabinet A\",\"adresse\":\"Lot A\","
                        + "\"email\":\"a@pi.mg\"}]}")).andExpect(status().isCreated());
        changer(idDmc, "B04-SE-03", aujourdhui.minusDays(1) + "T08:00");
        for (String[] e : new String[][] { { jetonA, "1111000111", "Cabinet A" }, { jetonB, "2222000222", "Bureau B" } }) {
            mvc.perform(put("/api/candidat/entreprise").header("Authorization", e[0]).contentType(JSON).content("{\"raisonSociale\":\"" + e[2]
                    + "\",\"nif\":\"" + e[1] + "\",\"adresse\":\"Lot\",\"representant\":{\"nom\":\"Rakoto\",\"prenom\":\"Jean\"}}")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/cles")).andExpect(status().isNotFound());   // restreinte : invités seuls
        List<String> cles = JsonPath.read(mvc.perform(get("/api/procedures-en-ligne/" + idDmc + "/cles").header("Authorization", jetonA)).andReturn().getResponse().getContentAsString(),
                "$.detenteurs[*].empreinte");
        String t1 = java.util.UUID.randomUUID().toString();
        creer(jetonA, idDmc, enTete(idDmc, t1, cles), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ENVELOPPE_OBLIGATOIRE"));
        creer(jetonA, idDmc, enTete(idDmc, t1, cles), "AUTRE").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ENVELOPPE_INVALIDE"));
        creer(jetonB, idDmc, enTete(idDmc, t1, cles), "TECHNIQUE").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NON_INVITE"));
        String accuseT = deposer(jetonA, idDmc, t1, cles, "TECHNIQUE");
        assertThat(JsonPath.<Integer>read(accuseT, "$.offre.numero")).isEqualTo(1);
        assertThat(JsonPath.<Object>read(accuseT, "$.jumelle")).isNull();
        creer(jetonA, idDmc, enTete(idDmc, java.util.UUID.randomUUID().toString(), cles), "TECHNIQUE").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFRE_EXISTANTE"));
        String f1 = java.util.UUID.randomUUID().toString();
        String accuseF = deposer(jetonA, idDmc, f1, cles, "FINANCIERE");
        assertThat(JsonPath.<Integer>read(accuseF, "$.offre.numero")).isEqualTo(1);
        assertThat(JsonPath.<String>read(accuseF, "$.offre.enveloppe")).isEqualTo("FINANCIERE");
        assertThat(JsonPath.<String>read(accuseF, "$.jumelle.empreinte")).isEqualTo(JsonPath.<String>read(accuseT, "$.offre.empreinte"));
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/depots").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.nombre").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/candidat/offres/" + t1)
                .header("Authorization", jetonA)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("RETIREE"));
        assertThat(offreRepository.findById(f1).orElseThrow().getEtat()).isEqualTo("RETIREE");
        changer(idDmc, "B04-LH-02", aujourdhui.minusDays(1) + "T10:00");
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/depots").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.nombre").value(0))
                .andExpect(jsonPath("$.depots[*].enveloppe").value(org.hamcrest.Matchers.containsInAnyOrder("TECHNIQUE", "FINANCIERE")));
    }

    @Autowired private cnm.prs.repository.CleDetenteurRepository cleRepository;
    @Autowired private cnm.prs.repository.OffreRepository offreRepository;
    @Autowired private cnm.prs.repository.FicheMarcheValeurRepository valeurRepository;

    /** Une enveloppe déposée et scellée (contenu chiffré simulé, un morceau) : l'accusé. */
    private String deposer(String jeton, Long idDmc, String idOffre, List<String> cles, String enveloppe) throws Exception {
        String enTete = enTete(idDmc, idOffre, cles);
        creer(jeton, idDmc, enTete, enveloppe).andExpect(status().isCreated());
        byte[] morceau = new byte[100 + 28];
        new java.util.Random(7).nextBytes(morceau);
        mvc.perform(put("/api/candidat/offres/" + idOffre + "/morceaux/0").header("Authorization", jeton).header("X-Empreinte", sha(morceau))
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(morceau)).andExpect(status().isOk());
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
        sha.update(enTete.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        sha.update(morceau);
        return mvc.perform(post("/api/candidat/offres/" + idOffre + "/sceller").header("Authorization", jeton).contentType(JSON)
                .content("{\"empreinte\":\"" + java.util.HexFormat.of().formatHex(sha.digest()) + "\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions creer(String jeton, Long idDmc, String enTete, String enveloppe) throws Exception {
        return mvc.perform(post("/api/candidat/offres").header("Authorization", jeton).contentType(JSON).content("{\"idDmc\":" + idDmc
                + ",\"lot\":null,\"enTete\":\"" + enTete.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" + (enveloppe == null ? "" : ",\"enveloppe\":\""
                        + enveloppe + "\"") + "}"));
    }

    private static String enTete(Long idDmc, String idOffre, List<String> cles) {
        StringBuilder parts = new StringBuilder();
        for (String e : cles) {
            parts.append(parts.length() == 0 ? "" : ",").append("{\"empreinte\":\"").append(e).append("\",\"part\":\"")
                    .append(java.util.Base64.getEncoder().encodeToString(new byte[384])).append("\"}");
        }
        return "{\"version\":1,\"idOffre\":\"" + idOffre + "\",\"idDmc\":" + idDmc + ",\"lot\":null,\"algorithmes\":[\"AES-256-GCM\","
                + "\"RSA-OAEP-3072-SHA256\",\"SHAMIR-GF256\"],\"tailleMorceau\":" + (4 * 1024 * 1024) + ",\"nombreMorceaux\":1"
                + ",\"tailleContenu\":100,\"quorum\":2,\"n\":3,\"parts\":[" + parts + "]}";
    }

    private static String sha(byte[] b) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
    }

    private static cnm.prs.entity.CleDetenteur cle(Long idDmc, String role, String im, int i) {
        cnm.prs.entity.CleDetenteur c = new cnm.prs.entity.CleDetenteur();
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
        c.setEtatPart(cnm.prs.entity.CleDetenteur.PUBLIEE);
        c.setDatePublication(LocalDateTime.now());
        c.setRemplacements(0);
        return c;
    }

    private void changer(Long idDmc, String code, String valeur) throws Exception {
        int idFiche = JsonPath.read(mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString(), "$.idFiche");
        cnm.prs.entity.FicheMarcheValeur v = valeurRepository.findByIdFiche(idFiche).stream().filter(x -> x.getCodeChamp().equals(code))
                .findFirst().orElseThrow();
        v.setValeur(valeur);
        valeurRepository.save(v);
    }

    /** Une fiche PI en remise électronique, validée : responsable, CAO de deux membres, paramètres internes. */
    private Long ficheEnLigne() throws Exception {
        cnm.prs.service.RemiseElectronique.Parametres p = parametres.remiseElectronique();
        parametres.fixerRemiseElectronique(new cnm.prs.service.RemiseElectronique.Parametres(p.plateformeUrl(), p.fuseau(), "Simple",
                p.tailleMaxPlateformeMo(), p.delaiMinRemiseJours(), p.assistance(), p.quorumDefaut(), p.verificationPartJours()));
        String corps = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long idDmc = ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"modeRemise\":\"ELECTRONIQUE\",\"groupement\":\"NON\",\"avance\":\"NON\",\"prixRevisable\":\"NON\"}}"))
                .andExpect(status().isOk());
        Map<String, String> donnees = new LinkedHashMap<>();
        donnees.put("B02-MS-01", METHODE);
        donnees.put("B04-LH-02", ouvrable(aujourdhui.plusDays(40)) + "T10:00");
        donnees.put("B04-SE-02", "https://depot.cnm.mg");
        donnees.put("B04-SE-03", aujourdhui.plusDays(10) + "T08:00");
        donnees.put("B04-SE-05", "Simple");
        donnees.put("B04-SE-06", "À définir par l'Administrateur (liste officielle des prestataires de certification)");
        donnees.put("B04-SE-10", "OUI");
        donnees.put("B04-SE-17", aujourdhui.plusDays(10) + "T08:00");
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES", donnees);
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isCreated());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenPrmp).contentType(JSON)
                .content(CaoIntegrationTest.corpsCao("m1@pi.mg", "m2@pi.mg"))).andExpect(status().isOk());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"quorum\":2,\"dateCeremonie\":\"" + aujourdhui.plusDays(9) + "T09:00\",\"depositaire\":{\"nom\":\"Rakoto Jean\","
                        + "\"email\":\"rakoto.depositaire@secours.mg\"}}"))
                .andExpect(status().isOk());
        String v = mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp)).andReturn().getResponse()
                .getContentAsString();
        assertThat(v).as("validation de la fiche PI en ligne : %s", v).contains("\"statut\":\"VALIDEE\"");
        return idDmc;
    }

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }
}
