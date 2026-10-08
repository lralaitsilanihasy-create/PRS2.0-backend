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
    private String tokenUgpm;

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
        tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", "ANT");
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

    @Test
    @DisplayName("⚠️ PI-c : grille de la fiche (sous-critères, critères globaux), notation par membre déclaré (bornée, motivée), "
            + "moyennes et écart signalé, arrêt par le président une fois chaque grille complète, élimination sous le score minimum, rang, "
            + "réouverture motivée")
    void notationTechnique() throws Exception {
        Long idDmc = ficheEnLigne();
        String internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes")
                .header("Authorization", bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT")))
                .andReturn().getResponse().getContentAsString();
        List<String> ims = JsonPath.read(internes, "$.membresCommission[*].im");
        String m1 = bearer("m1@pi.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, ims.get(0), null);
        String m2 = bearer("m2@pi.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, ims.get(1), null);
        for (String[] e : new String[][] { { jetonA, "1111000111", "Cabinet A" }, { jetonB, "2222000222", "Bureau B" } }) {
            mvc.perform(put("/api/candidat/entreprise").header("Authorization", e[0]).contentType(JSON).content("{\"raisonSociale\":\"" + e[2]
                    + "\",\"nif\":\"" + e[1] + "\",\"adresse\":\"Lot\",\"representant\":{\"nom\":\"Rakoto\",\"prenom\":\"Jean\"}}")).andExpect(status().isOk());
        }
        String a = propositionOuverte(idDmc, "C900000071", "1111000111", "Cabinet A", 1);
        String b = propositionOuverte(idDmc, "C900000072", "2222000222", "Bureau B", 2);
        cnm.prs.entity.Seance s = new cnm.prs.entity.Seance();
        s.setIdDmc(idDmc);
        s.setEtat(cnm.prs.entity.Seance.CLOSE);
        s.setOuverteLe(LocalDateTime.now().minusDays(1));
        s.setCloseLe(LocalDateTime.now().minusHours(20));
        s.setPvSigneLe(LocalDateTime.now().minusHours(20));
        seanceRepository.save(s);
        String ev = "/api/fiches-marche/" + idDmc + "/evaluation";
        mvc.perform(post(ev + "/ouvrir").header("Authorization", bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT")))
                .andExpect(status().isCreated());
        String tech = ev + "/technique";
        String vue = mvc.perform(get(tech).header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(vue, "$.elements[*].code"))
                .containsExactly("B06-TP-02", "B06-TP-03#1", "B06-TP-03#2", "B06-TP-04", "B06-TP-05", "B06-TP-06");
        assertThat(JsonPath.<Number>read(vue, "$.scoreMinimum").intValue()).isEqualTo(70);
        assertThat(JsonPath.<Number>read(vue, "$.seuilEcartPourcent").intValue()).isEqualTo(20);
        notes(m1, tech, a, 18, 14, 14, 35, 4, 4).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DECLARATION_MANQUANTE"));
        mvc.perform(post(ev + "/declaration").header("Authorization", m1).contentType(JSON).content("{\"conflit\":false}")).andExpect(status().isOk());
        mvc.perform(post(ev + "/declaration").header("Authorization", m2).contentType(JSON).content("{\"conflit\":false}")).andExpect(status().isOk());
        notes(m1, tech, a, 18, 14, 14, 35, 4, 4).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFORMITE_NON_ARRETEE"));
        for (String o : List.of(a, b)) {
            mvc.perform(put(ev + "/offres/" + o + "/conformite").header("Authorization", m1).contentType(JSON).content("{\"decision\":\"CONFORME\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(ev + "/lots/1/etapes/CONFORMITE/arreter").header("Authorization", m1).contentType(JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(put(tech + "/offres/" + a + "/notes").header("Authorization", m1).contentType(JSON)
                .content("{\"notes\":[{\"element\":\"B06-TP-03#1\",\"note\":16,\"motif\":\"x\"}]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTE_HORS_BAREME"));
        mvc.perform(put(tech + "/offres/" + a + "/notes").header("Authorization", m1).contentType(JSON)
                .content("{\"notes\":[{\"element\":\"B06-TP-09\",\"note\":1,\"motif\":\"x\"}]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ELEMENT_INCONNU"));
        mvc.perform(put(tech + "/offres/" + a + "/notes").header("Authorization", m1).contentType(JSON)
                .content("{\"notes\":[{\"element\":\"B06-TP-02\",\"note\":10,\"motif\":\" \"}]}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        // A : 18+14+14+35+4+4 = 89 (m1) et 14+14+14+35+4+4 = 85 (m2) → 87 ; écart de 4 sur 20 au critère (i) : 20 % — non signalé.
        notes(m1, tech, a, 18, 14, 14, 35, 4, 4).andExpect(status().isOk());
        notes(m2, tech, a, 14, 14, 14, 35, 4, 4).andExpect(status().isOk());
        // B : (i) 18 et 10 : écart de 8 sur 20 (40 %) — signalé ; total 50+56... = sous 70 → éliminée.
        notes(m1, tech, b, 18, 8, 8, 20, 2, 2).andExpect(status().isOk());
        mvc.perform(post(tech + "/lots/1/arreter").header("Authorization", m2).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(tech + "/lots/1/arreter").header("Authorization", m1).contentType(JSON).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTATION_INCOMPLETE")).andExpect(jsonPath("$.details.offres[0]").value(2));
        String avant = notes(m2, tech, b, 10, 8, 8, 20, 2, 2).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(avant, "$.lots[0].offres[?(@.idOffre=='" + b + "')].moyennes[0].ecart")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(avant, "$.lots[0].offres[?(@.idOffre=='" + a + "')].moyennes[0].ecart")).containsExactly(false);
        String arrete = mvc.perform(post(tech + "/lots/1/arreter").header("Authorization", m1).contentType(JSON).content("{\"observation\":\"RAS\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Number>>read(arrete, "$.lots[0].offres[?(@.idOffre=='" + a + "')].total").get(0).doubleValue()).isEqualTo(87.0);
        assertThat(JsonPath.<List<String>>read(arrete, "$.lots[0].offres[?(@.idOffre=='" + a + "')].statut")).containsExactly("QUALIFIEE");
        assertThat(JsonPath.<List<Integer>>read(arrete, "$.lots[0].offres[?(@.idOffre=='" + a + "')].rang")).containsExactly(1);
        assertThat(JsonPath.<List<String>>read(arrete, "$.lots[0].offres[?(@.idOffre=='" + b + "')].statut")).containsExactly("ELIMINEE");
        assertThat(JsonPath.<List<String>>read(arrete, "$.lots[0].offres[?(@.idOffre=='" + b + "')].motifElimination").get(0))
                .contains("sous le score minimum de 70 points");
        notes(m1, tech, a, 18, 14, 14, 35, 4, 4).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TECHNIQUE_ARRETEE"));
        mvc.perform(post(tech + "/lots/1/rouvrir").header("Authorization", m1).contentType(JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(tech + "/lots/1/rouvrir").header("Authorization", m1).contentType(JSON).content("{\"motif\":\"Erreur de saisie\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lots[0].offres[0].statut").value("EN_COURS"));

        // ⚠️ PI-d1 — la seconde séance : pas avant l'arrêt ; seule l'enveloppe financière de la qualifiée s'ouvre, celle de l'éliminée
        // reste scellée et nommée ; la qualifiée est invitée ; l'évaluation technique ne se rouvre plus.
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String fa = propositionFinanciere(a);
        propositionFinanciere(b);
        String sf = "/api/fiches-marche/" + idDmc + "/seance/financiere";
        mvc.perform(post(sf + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TECHNIQUE_NON_ARRETEE")).andExpect(jsonPath("$.details.lots[0]").value(1));
        mvc.perform(post(tech + "/lots/1/arreter").header("Authorization", m1).contentType(JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(get(sf).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        mvc.perform(post(sf + "/ouvrir").header("Authorization", m1)).andExpect(status().isForbidden());
        String ouverte = mvc.perform(post(sf + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(ouverte, "$.etat")).isEqualTo("OUVERTE");
        assertThat(JsonPath.<String>read(ouverte, "$.methode")).isEqualTo(METHODE);
        assertThat(JsonPath.<List<String>>read(ouverte, "$.aOuvrir[*].idOffre")).containsExactly(fa);
        assertThat(JsonPath.<Number>read(ouverte, "$.aOuvrir[0].noteTechnique").doubleValue()).isEqualTo(87.0);
        assertThat(JsonPath.<Integer>read(ouverte, "$.aOuvrir[0].rangTechnique")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(ouverte, "$.nonOuvertes[0].numero")).isEqualTo(2);
        assertThat(JsonPath.<String>read(ouverte, "$.nonOuvertes[0].motif")).startsWith("éliminée à l'évaluation technique");
        assertThat(notificationRepository.findPourRefEtType("C900000071", "CANDIDAT")).extracting(Notification::getTypeNotif)
                .contains("SEANCE_FINANCIERE");
        assertThat(notificationRepository.findPourRefEtType("C900000072", "CANDIDAT")).extracting(Notification::getTypeNotif)
                .doesNotContain("SEANCE_FINANCIERE");
        mvc.perform(post(sf + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEANCE_FINANCIERE_OUVERTE"));
        mvc.perform(post(tech + "/lots/1/rouvrir").header("Authorization", m1).contentType(JSON).content("{\"motif\":\"Erreur\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEANCE_FINANCIERE_OUVERTE"));
    }

    @Test
    @DisplayName("⚠️ PI-d2a : qualité-coût — saisie (corrections, dépenses remboursables), Sf = 100 × Fm / F, S = T × wT + Sf × wF, rang, "
            + "arrêt par le président ; négociation par la PRMP avec le premier, échec motivé, le suivant, réussite et PV")
    void qualiteCoutEtNegociation() throws Exception {
        Long idDmc = ficheEnLigne();
        Pi pi = deuxQualifiees(idDmc);
        String fa = propositionFinanciere(pi.a());
        String fb = propositionFinanciere(pi.b());
        seconde(idDmc, Map.of(fa, "1000000", fb, "800000"));
        String ev = "/api/fiches-marche/" + idDmc + "/evaluation";
        String vue = mvc.perform(get(ev + "/financiere").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(vue, "$.codeMethode")).isEqualTo("QUALITE_COUT");
        assertThat(JsonPath.<Number>read(vue, "$.poidsTechnique").doubleValue()).isEqualTo(0.8);
        assertThat(JsonPath.<List<String>>read(vue, "$.lots[0].propositions[*].statut")).containsOnly("A_EVALUER");
        mvc.perform(post(ev + "/financiere/lots/1/arreter").header("Authorization", pi.m1()).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVALUATION_FINANCIERE_INCOMPLETE"));
        mvc.perform(put(ev + "/financiere/offres/" + fa).header("Authorization", pi.m1()).contentType(JSON).content("{\"remboursables\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REMBOURSABLES_INVALIDES"));
        // A : 1 000 000 lu, corrigé à 1 050 000, dont 50 000 de dépenses remboursables → F = 1 000 000 ; B : 800 000 → Fm.
        mvc.perform(put(ev + "/financiere/offres/" + fa).header("Authorization", pi.m1()).contentType(JSON).content("{\"corrections\":[{\"ligne\":1,"
                + "\"libelle\":\"Honoraires\",\"avant\":100000,\"apres\":150000,\"regle\":\"PU_PREVAUT\",\"retenue\":true}],\"remboursables\":50000,"
                + "\"motifRemboursables\":\"Frais de mission\"}")).andExpect(status().isOk());
        String classe = mvc.perform(put(ev + "/financiere/offres/" + fb).header("Authorization", pi.m2()).contentType(JSON).content("{}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // S(A) = 87 × 0,8 + 80 × 0,2 = 85,60 ; S(B) = 84 × 0,8 + 100 × 0,2 = 87,20 → B premier.
        assertThat(JsonPath.<List<Number>>read(classe, "$.lots[0].propositions[?(@.idOffre=='" + pi.a() + "')].scoreFinancier").get(0).doubleValue())
                .isEqualTo(80.0);
        assertThat(JsonPath.<List<Number>>read(classe, "$.lots[0].propositions[?(@.idOffre=='" + pi.a() + "')].scoreCombine").get(0).doubleValue())
                .isEqualTo(85.6);
        assertThat(JsonPath.<List<Number>>read(classe, "$.lots[0].propositions[?(@.idOffre=='" + pi.b() + "')].scoreCombine").get(0).doubleValue())
                .isEqualTo(87.2);
        assertThat(JsonPath.<List<Integer>>read(classe, "$.lots[0].propositions[?(@.idOffre=='" + pi.b() + "')].rang")).containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(classe, "$.lots[0].propositions[?(@.idOffre=='" + pi.a() + "')].rang")).containsExactly(2);
        mvc.perform(post(ev + "/financiere/lots/1/arreter").header("Authorization", pi.m2()).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(ev + "/negociation/lots/1/ouvrir").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLASSEMENT_NON_ARRETE"));
        mvc.perform(post(ev + "/financiere/lots/1/arreter").header("Authorization", pi.m1()).contentType(JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(put(ev + "/financiere/offres/" + fb).header("Authorization", pi.m2()).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLASSEMENT_ARRETE"));

        // La négociation : la PRMP, avec le seul premier.
        String neg = ev + "/negociation";
        mvc.perform(post(neg + "/lots/1/ouvrir").header("Authorization", pi.m1()).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        String n1 = mvc.perform(post(neg + "/lots/1/ouvrir").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"prevueLe\":\"" + aujourdhui.plusDays(3) + "T10:00\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(n1, "$.lots[0].negociations[0].idOffre")).isEqualTo(pi.b());
        assertThat(JsonPath.<String>read(n1, "$.lots[0].negociations[0].lieu")).isNotBlank();
        assertThat(JsonPath.<Object>read(n1, "$.lots[0].prochain")).isNull();
        assertThat(notificationRepository.findPourRefEtType("C900000072", "CANDIDAT")).extracting(Notification::getTypeNotif).contains("NEGOCIATION");
        mvc.perform(post(neg + "/lots/1/ouvrir").header("Authorization", tokenPrmp).contentType(JSON).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEGOCIATION_EN_COURS"));
        mvc.perform(post(ev + "/financiere/lots/1/rouvrir").header("Authorization", pi.m1()).contentType(JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NEGOCIATION_ENGAGEE"));
        long id1 = ((Number) JsonPath.read(n1, "$.lots[0].negociations[0].id")).longValue();
        mvc.perform(post(neg + "/" + id1 + "/conclure").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"resultat\":\"ECHOUEE\",\"dateNegociation\":\"" + aujourdhui + "\",\"texte\":\"Désaccord sur le chef de mission\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        String apresEchec = mvc.perform(post(neg + "/" + id1 + "/conclure").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"resultat\":\"ECHOUEE\",\"dateNegociation\":\"" + aujourdhui + "\",\"texte\":\"Désaccord sur le chef de mission\","
                        + "\"motif\":\"Le candidat remplace le chef de mission\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(apresEchec, "$.lots[0].prochain.idOffre")).isEqualTo(pi.a());
        String n2 = mvc.perform(post(neg + "/lots/1/ouvrir").header("Authorization", tokenPrmp).contentType(JSON).content("{\"lieu\":\"Salle B\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id2 = ((Number) JsonPath.read(n2, "$.lots[0].negociations[1].id")).longValue();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(neg + "/" + id2 + "/piece")
                .file(new org.springframework.mock.web.MockMultipartFile("fichier", "compte-rendu.pdf", "application/pdf", new byte[] { 1, 2, 3 }))
                .with(r -> { r.setMethod("PUT"); return r; }).header("Authorization", tokenPrmp)).andExpect(status().isOk());
        String fin = mvc.perform(post(neg + "/" + id2 + "/conclure").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"resultat\":\"REUSSIE\",\"dateNegociation\":\"" + aujourdhui + "\",\"texte\":\"Planning précisé, personnel confirmé\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(fin, "$.lots[0].conclue")).isTrue();
        String pv = texteDuPdf(mvc.perform(get(neg + "/" + id2 + "/pv").header("Authorization", pi.m1())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(pv).contains("PROCÈS-VERBAL DE NÉGOCIATION", "Cabinet A", "Salle B", "La négociation a abouti", "compte-rendu.pdf");
        mvc.perform(get(neg + "/" + id2 + "/piece").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(post(neg + "/lots/1/ouvrir").header("Authorization", tokenPrmp).contentType(JSON).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEGOCIATION_CONCLUE"));

        // ⚠️ PI-d2b — la proposition (la négociation réussie), le rapport adapté et signé, le dossier de marché MPI.
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String vueEv = mvc.perform(get(ev).header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(vueEv, "$.lots[0].proposition.idOffre")).isEqualTo(pi.a());
        assertThat(JsonPath.<String>read(vueEv, "$.lots[0].proposition.idOffreFinanciere")).isEqualTo(fa);
        assertThat(JsonPath.<Number>read(vueEv, "$.lots[0].proposition.montant").intValue()).isEqualTo(1050000);
        assertThat(JsonPath.<Boolean>read(vueEv, "$.lots[0].proposition.infructueux")).isFalse();
        mvc.perform(post(ev + "/rapport").header("Authorization", tokenVer).contentType(JSON).content("{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("RAPPORT_A_SIGNER"));
        mvc.perform(put(ev + "/financiere/offres/" + fa).header("Authorization", pi.m1()).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVALUATION_CLOSE"));
        for (String m : List.of(pi.m1(), pi.m2())) {
            mvc.perform(post(ev + "/rapport/signer").header("Authorization", m).contentType(JSON).content("{}")).andExpect(status().isOk());
        }
        String rapport = texteDuPdf(mvc.perform(get(ev + "/rapport").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(rapport).contains("RAPPORT D'ÉVALUATION DES PROPOSITIONS", "4. Évaluation technique", "note technique 87 points",
                "score combiné 85.6", "6. Classement", "Rang 1 — proposition n° 2", "n'a pas abouti — Le candidat remplace le chef de mission",
                "La commission propose d'attribuer le marché à Cabinet A (proposition n° 1)", "(propositions remises en ligne)",
                "Grilles individuelles de notation technique").doesNotContain("Post-qualification");
        typeDossierRepository.save(new cnm.prs.entity.TypeDossier("DDM", "Dossier de Marché"));
        sousTypeDossierRepository.save(new cnm.prs.entity.SousTypeDossier("MPI", "Marché de Prestations Intellectuelles", "DDM"));
        String att = "/api/fiches-marche/" + idDmc + "/attribution";
        String cree = mvc.perform(post(att + "/lots/1/dossier").header("Authorization", tokenPrmp)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.lots[0].dossierMarche.sousType").value("MPI"))
                .andExpect(jsonPath("$.lots[0].proposition.idOffre").value(pi.a())).andReturn().getResponse().getContentAsString();

        // ⚠️ 2d-1 (Q3) — l'avis défavorable de la Commission : la PRMP reprend l'évaluation (rapport archivé), ou déclare l'infructuosité.
        mvc.perform(post(att + "/lots/1/reprendre").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AVIS_NON_DEFAVORABLE"));
        int idDossier = JsonPath.read(cree, "$.lots[0].dossierMarche.idDossier");
        receptionRepository.save(reception(idDossier, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(idDossier, idDossier, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(idDossier, idDossier, "CTRMEM"));
        seedPvSigne(idDossier, idDossier);
        cnm.prs.entity.PvExamen pvDef = pvExamenRepository.findById(idDossier).orElseThrow();
        pvDef.setIdAvis("DEF");
        pvExamenRepository.save(pvDef);
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].dossierMarche.avis").value("DEF"));
        mvc.perform(post(att + "/lots/1/reprendre").header("Authorization", tokenUgpm).contentType(JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(att + "/lots/1/reprendre").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        String repris = mvc.perform(post(att + "/lots/1/reprendre").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"La Commission relève une erreur de classement\"}")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(repris, "$.lots[0].etat")).isEqualTo("EN_EVALUATION");
        assertThat(JsonPath.<Integer>read(repris, "$.lots[0].reprises[0].idDossier")).isEqualTo(idDossier);
        assertThat(JsonPath.<Boolean>read(repris, "$.lots[0].reprises[0].rapportDisponible")).isTrue();
        long idReprise = ((Number) JsonPath.read(repris, "$.lots[0].reprises[0].id")).longValue();
        mvc.perform(get(att + "/reprises/" + idReprise + "/rapport").header("Authorization", pi.m1())).andExpect(status().isOk());
        mvc.perform(get(ev).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.etat").value("EN_COURS"));
        mvc.perform(get(ev + "/rapport").header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        assertThat(notificationRepository.findPourRefEtType(JsonPath.<List<String>>read(vueEv, "$.declarations[*].membre").get(0), "MEMBRE_CAO"))
                .extracting(Notification::getTypeNotif).contains("EVALUATION_REPRISE");
        // Le nouveau rapport, signé, rend le lot au dossier de marché.
        mvc.perform(post(ev + "/rapport").header("Authorization", tokenVer).contentType(JSON).content("{}")).andExpect(status().isOk());
        for (String m : List.of(pi.m1(), pi.m2())) {
            mvc.perform(post(ev + "/rapport/signer").header("Authorization", m).contentType(JSON).content("{}")).andExpect(status().isOk());
        }
        mvc.perform(get(att).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.lots[0].etat").value("PROPOSE"))
                .andExpect(jsonPath("$.lots[0].reprises.length()").value(1));
    }

    @Autowired private cnm.prs.repository.PvExamenRepository pvExamenRepository;

    @Test
    @DisplayName("⚠️ PI-d2b : art. 56-II — une seule proposition conforme : le rapport propose d'office l'infructuosité, sans évaluation "
            + "technique ni financière ; tant que l'évaluation technique n'est pas arrêtée, le rapport attend")
    void uneSeuleConformeInfructueux() throws Exception {
        Long idDmc = ficheEnLigne();
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        List<String> ims = JsonPath.read(internes, "$.membresCommission[*].im");
        String m1 = bearer("m1@pi.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, ims.get(0), null);
        String m2 = bearer("m2@pi.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, ims.get(1), null);
        for (String[] e : new String[][] { { jetonA, "1111000111", "Cabinet A" }, { jetonB, "2222000222", "Bureau B" } }) {
            mvc.perform(put("/api/candidat/entreprise").header("Authorization", e[0]).contentType(JSON).content("{\"raisonSociale\":\"" + e[2]
                    + "\",\"nif\":\"" + e[1] + "\",\"adresse\":\"Lot\",\"representant\":{\"nom\":\"Rakoto\",\"prenom\":\"Jean\"}}")).andExpect(status().isOk());
        }
        String a = propositionOuverte(idDmc, "C900000071", "1111000111", "Cabinet A", 1);
        String b = propositionOuverte(idDmc, "C900000072", "2222000222", "Bureau B", 2);
        cnm.prs.entity.Seance s = new cnm.prs.entity.Seance();
        s.setIdDmc(idDmc);
        s.setEtat(cnm.prs.entity.Seance.CLOSE);
        s.setOuverteLe(LocalDateTime.now().minusDays(1));
        s.setCloseLe(LocalDateTime.now().minusHours(20));
        s.setPvSigneLe(LocalDateTime.now().minusHours(20));
        seanceRepository.save(s);
        String ev = "/api/fiches-marche/" + idDmc + "/evaluation";
        mvc.perform(post(ev + "/ouvrir").header("Authorization", tokenVer)).andExpect(status().isCreated());
        for (String m : List.of(m1, m2)) {
            mvc.perform(post(ev + "/declaration").header("Authorization", m).contentType(JSON).content("{\"conflit\":false}")).andExpect(status().isOk());
        }
        mvc.perform(put(ev + "/offres/" + a + "/conformite").header("Authorization", m1).contentType(JSON).content("{\"decision\":\"CONFORME\"}"))
                .andExpect(status().isOk());
        mvc.perform(put(ev + "/offres/" + b + "/conformite").header("Authorization", m1).contentType(JSON).content("{\"decision\":\"CONFORME\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(ev + "/lots/1/etapes/CONFORMITE/arreter").header("Authorization", m1).contentType(JSON).content("{}")).andExpect(status().isOk());
        // Deux conformes : le rapport attend l'évaluation technique.
        mvc.perform(post(ev + "/rapport").header("Authorization", tokenVer).contentType(JSON).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ETAPES_INCOMPLETES")).andExpect(jsonPath("$.details.lots[0]").value(1));
        mvc.perform(post(ev + "/lots/1/etapes/CONFORMITE/rouvrir").header("Authorization", m1).contentType(JSON).content("{\"motif\":\"Revoir B\"}"))
                .andExpect(status().isOk());
        mvc.perform(put(ev + "/offres/" + b + "/conformite").header("Authorization", m1).contentType(JSON).content("{\"decision\":\"ECARTEE\","
                + "\"qualification\":\"NON_CONFORME\",\"motif\":\"Personnel clé absent\",\"clause\":\"IC 5.1\"}")).andExpect(status().isOk());
        mvc.perform(post(ev + "/lots/1/etapes/CONFORMITE/arreter").header("Authorization", m1).contentType(JSON).content("{}")).andExpect(status().isOk());
        String vue = mvc.perform(get(ev).header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(vue, "$.lots[0].proposition.infructueux")).isTrue();
        assertThat(JsonPath.<String>read(vue, "$.lots[0].proposition.motifInfructuosite")).contains("Une seule proposition est conforme (art. 56-II)");
        mvc.perform(post(ev + "/rapport").header("Authorization", tokenVer).contentType(JSON).content("{}")).andExpect(status().isOk());
        for (String m : List.of(m1, m2)) {
            mvc.perform(post(ev + "/rapport/signer").header("Authorization", m).contentType(JSON).content("{}")).andExpect(status().isOk());
        }
        String rapport = texteDuPdf(mvc.perform(get(ev + "/rapport").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(rapport).contains("L'évaluation technique n'a pas été conduite", "La commission propose de déclarer le lot infructueux : Une "
                + "seule proposition est conforme (art. 56-II).");
        String att = "/api/fiches-marche/" + idDmc + "/attribution";
        mvc.perform(post(att + "/lots/1/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOT_INFRUCTUEUX"));

        // ⚠️ 2d-1 (§B6) — la PRMP déclare le lot infructueux, sur sa décision : notifié aux candidats, affiché.
        String corps = "{\"motif\":\"Une seule proposition conforme\",\"decision\":{\"reference\":\"DEC-12/2026\",\"date\":\"" + aujourdhui + "\"},"
                + "\"suite\":\"RELANCE\"}";
        mvc.perform(post(att + "/lots/1/infructueux").header("Authorization", tokenUgpm).contentType(JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(post(att + "/lots/1/infructueux").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DECISION_OBLIGATOIRE"));
        mvc.perform(post(att + "/lots/1/infructueux").header("Authorization", tokenPrmp).contentType(JSON).content(corps.replace("RELANCE", "AUTRE")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SUITE_INVALIDE"));
        mvc.perform(post(att + "/lots/1/infructueux").header("Authorization", tokenPrmp).contentType(JSON)
                .content(corps.replace("\"date\":\"" + aujourdhui, "\"date\":\"" + aujourdhui.plusDays(1)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DECISION_DATE_INVALIDE"));
        mvc.perform(post(att + "/lots/1/infructueux").header("Authorization", tokenPrmp).contentType(JSON).content(corps)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].etat").value("INFRUCTUEUX")).andExpect(jsonPath("$.lots[0].infructuosite.suite").value("RELANCE"))
                .andExpect(jsonPath("$.lots[0].infructuosite.decisionReference").value("DEC-12/2026"));
        mvc.perform(post(att + "/lots/1/infructueux").header("Authorization", tokenPrmp).contentType(JSON).content(corps))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEJA_INFRUCTUEUX"));
        for (String c : List.of("C900000071", "C900000072")) {
            assertThat(notificationRepository.findPourRefEtType(c, "CANDIDAT")).extracting(Notification::getTypeNotif)
                    .contains("PROCEDURE_INFRUCTUEUSE");
        }
    }

    @Test
    @DisplayName("⚠️ PI-d2a : qualité technique exclusivement — classement par la note technique, négociation avec le premier ; après son "
            + "échec, la financière du suivant s'ouvre en séance complémentaire (ronde 2) avant de négocier")
    void qualiteTechniqueEtSeanceComplementaire() throws Exception {
        Long idDmc = ficheEnLigne();
        changer(idDmc, "B02-MS-01", "Qualité technique exclusivement");
        Pi pi = deuxQualifiees(idDmc);
        String fa = propositionFinanciere(pi.a());
        String fb = propositionFinanciere(pi.b());
        seconde(idDmc, Map.of(fa, "1000000"));
        String ev = "/api/fiches-marche/" + idDmc + "/evaluation";
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        String vue = mvc.perform(get(ev + "/financiere").header("Authorization", tokenPrmp)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(vue, "$.codeMethode")).isEqualTo("QUALITE_TECHNIQUE");
        assertThat(JsonPath.<List<String>>read(vue, "$.lots[0].propositions[?(@.idOffre=='" + pi.b() + "')].statut")).containsExactly("NON_OUVERTE");
        assertThat(JsonPath.<List<Integer>>read(vue, "$.lots[0].propositions[?(@.idOffre=='" + pi.b() + "')].rang")).containsExactly(2);
        mvc.perform(post(ev + "/financiere/lots/1/arreter").header("Authorization", pi.m1()).contentType(JSON).content("{}")).andExpect(status().isOk());
        String neg = ev + "/negociation";
        String n1 = mvc.perform(post(neg + "/lots/1/ouvrir").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id1 = ((Number) JsonPath.read(n1, "$.lots[0].negociations[0].id")).longValue();
        // La réussite exige la saisie de la financière (permise après l'arrêt, dans ces méthodes).
        mvc.perform(post(neg + "/" + id1 + "/conclure").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"resultat\":\"REUSSIE\",\"dateNegociation\":\"" + aujourdhui + "\",\"texte\":\"x\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MONTANT_NON_EVALUE"));
        mvc.perform(put(ev + "/financiere/offres/" + fa).header("Authorization", pi.m1()).contentType(JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(post(neg + "/" + id1 + "/conclure").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"resultat\":\"ECHOUEE\",\"dateNegociation\":\"" + aujourdhui + "\",\"texte\":\"x\",\"motif\":\"Prix hors de portée\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lots[0].prochain.financiereOuverte").value(false));
        mvc.perform(post(neg + "/lots/1/ouvrir").header("Authorization", tokenPrmp).contentType(JSON).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCIERE_NON_OUVERTE")).andExpect(jsonPath("$.details.numero").value(2));
        String sf = "/api/fiches-marche/" + idDmc + "/seance/financiere";
        mvc.perform(post(sf + "/complementaire").header("Authorization", tokenVer).contentType(JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        String compl = mvc.perform(post(sf + "/complementaire").header("Authorization", tokenVer).contentType(JSON)
                .content("{\"motif\":\"Échec de la négociation avec le premier classé\"}")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<Integer>read(compl, "$.ronde")).isEqualTo(2);
        assertThat(JsonPath.<List<String>>read(compl, "$.aOuvrir[*].idOffre")).containsExactly(fb);
        assertThat(JsonPath.<List<Integer>>read(compl, "$.rondes[*].ronde")).containsExactly(1, 2);
        mvc.perform(post(sf + "/complementaire").header("Authorization", tokenVer).contentType(JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEANCE_FINANCIERE_EN_COURS"));
        // Le déchiffrement réel est éprouvé par SeanceIntegrationTest : ici, l'enveloppe est posée ouverte et la ronde close.
        ouvrirEnBase(fb, "900000");
        cnm.prs.entity.SeanceFinanciere r2 = seanceFinanciereRepository.findFirstByIdDmcOrderByRondeDesc(idDmc).orElseThrow();
        r2.setEtat(cnm.prs.entity.SeanceFinanciere.CLOSE);
        seanceFinanciereRepository.save(r2);
        mvc.perform(get(sf).header("Authorization", tokenPrmp)).andExpect(status().isOk()).andExpect(jsonPath("$.nonOuvertes.length()").value(0));
        mvc.perform(post(neg + "/lots/1/ouvrir").header("Authorization", tokenPrmp).contentType(JSON).content("{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[0].negociations[1].idOffre").value(pi.b()));
    }

    @Autowired private cnm.prs.repository.SeanceFinanciereRepository seanceFinanciereRepository;

    private record Pi(String a, String b, String m1, String m2) {
    }

    /** Deux propositions qualifiées : A (87 points, rang 1) et B (84 points, rang 2) ; m1 est le président. */
    private Pi deuxQualifiees(Long idDmc) throws Exception {
        String internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes")
                .header("Authorization", bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT")))
                .andReturn().getResponse().getContentAsString();
        List<String> ims = JsonPath.read(internes, "$.membresCommission[*].im");
        String m1 = bearer("m1@pi.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, ims.get(0), null);
        String m2 = bearer("m2@pi.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, ims.get(1), null);
        for (String[] e : new String[][] { { jetonA, "1111000111", "Cabinet A" }, { jetonB, "2222000222", "Bureau B" } }) {
            mvc.perform(put("/api/candidat/entreprise").header("Authorization", e[0]).contentType(JSON).content("{\"raisonSociale\":\"" + e[2]
                    + "\",\"nif\":\"" + e[1] + "\",\"adresse\":\"Lot\",\"representant\":{\"nom\":\"Rakoto\",\"prenom\":\"Jean\"}}")).andExpect(status().isOk());
        }
        String a = propositionOuverte(idDmc, "C900000071", "1111000111", "Cabinet A", 1);
        String b = propositionOuverte(idDmc, "C900000072", "2222000222", "Bureau B", 2);
        cnm.prs.entity.Seance s = new cnm.prs.entity.Seance();
        s.setIdDmc(idDmc);
        s.setEtat(cnm.prs.entity.Seance.CLOSE);
        s.setOuverteLe(LocalDateTime.now().minusDays(1));
        s.setCloseLe(LocalDateTime.now().minusHours(20));
        s.setPvSigneLe(LocalDateTime.now().minusHours(20));
        seanceRepository.save(s);
        String ev = "/api/fiches-marche/" + idDmc + "/evaluation";
        mvc.perform(post(ev + "/ouvrir").header("Authorization", bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT")))
                .andExpect(status().isCreated());
        for (String m : List.of(m1, m2)) {
            mvc.perform(post(ev + "/declaration").header("Authorization", m).contentType(JSON).content("{\"conflit\":false}")).andExpect(status().isOk());
        }
        for (String o : List.of(a, b)) {
            mvc.perform(put(ev + "/offres/" + o + "/conformite").header("Authorization", m1).contentType(JSON).content("{\"decision\":\"CONFORME\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post(ev + "/lots/1/etapes/CONFORMITE/arreter").header("Authorization", m1).contentType(JSON).content("{}")).andExpect(status().isOk());
        String tech = ev + "/technique";
        notes(m1, tech, a, 18, 14, 14, 35, 4, 4).andExpect(status().isOk());
        notes(m2, tech, a, 14, 14, 14, 35, 4, 4).andExpect(status().isOk());
        notes(m1, tech, b, 18, 14, 14, 30, 4, 4).andExpect(status().isOk());
        notes(m2, tech, b, 18, 14, 14, 30, 4, 4).andExpect(status().isOk());
        mvc.perform(post(tech + "/lots/1/arreter").header("Authorization", m1).contentType(JSON).content("{}")).andExpect(status().isOk());
        return new Pi(a, b, m1, m2);
    }

    /** La seconde séance (ronde 1) close, ses enveloppes posées ouvertes avec leur montant HT (le déchiffrement est éprouvé ailleurs). */
    private void seconde(Long idDmc, Map<String, String> montants) throws Exception {
        montants.forEach(this::ouvrirEnBase);
        cnm.prs.entity.SeanceFinanciere s = new cnm.prs.entity.SeanceFinanciere();
        s.setIdDmc(idDmc);
        s.setRonde(1);
        s.setEtat(cnm.prs.entity.SeanceFinanciere.CLOSE);
        s.setMethode(JsonPath.read(mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp)).andReturn().getResponse()
                .getContentAsString(), "$.valeurs['B02-MS-01']"));
        s.setAOuvrir(String.join(",", montants.keySet()));
        s.setOuverteLe(LocalDateTime.now().minusHours(2));
        s.setCloseLe(LocalDateTime.now().minusHours(1));
        s.setSecoursEmploye(false);
        seanceFinanciereRepository.save(s);
    }

    private void ouvrirEnBase(String idFinanciere, String montantHt) {
        cnm.prs.entity.Offre o = offreRepository.findById(idFinanciere).orElseThrow();
        o.setOuverteLe(LocalDateTime.now().minusHours(1));
        o.setIntegrite("INTACTE");
        o.setLecture("{\"acteEngagement\":{\"montantHt\":\"" + montantHt + "\",\"montantTtc\":\"" + montantHt + "\",\"monnaie\":\"MGA\"},\"pieces\":[]}");
        offreRepository.save(o);
    }

    /** L'enveloppe financière jumelle d'une proposition technique : déposée, scellée (jamais ouverte ici). */
    private String propositionFinanciere(String idTechnique) {
        cnm.prs.entity.Offre t = offreRepository.findById(idTechnique).orElseThrow();
        cnm.prs.entity.Offre o = new cnm.prs.entity.Offre();
        o.setIdOffre(java.util.UUID.randomUUID().toString());
        o.setIdDmc(t.getIdDmc());
        o.setIdCandidat(t.getIdCandidat());
        o.setIdEntreprise(t.getIdEntreprise());
        o.setNif(t.getNif());
        o.setRaisonSociale(t.getRaisonSociale());
        o.setEtat(cnm.prs.entity.Offre.DEPOSEE);
        o.setEnveloppe(cnm.prs.entity.Offre.FINANCIERE);
        o.setDateCreation(t.getDateCreation());
        o.setDateDepot(t.getDateDepot());
        o.setNumero(t.getNumero());
        o.setEnTete("{}");
        o.setNombreMorceaux(1);
        o.setTailleMorceau(1);
        o.setQuorum(2);
        o.setN(3);
        o.setEmpreintesDetenteurs("x");
        offreRepository.save(o);
        return o.getIdOffre();
    }

    @Autowired private cnm.prs.repository.SeanceRepository seanceRepository;

    /** Une proposition technique ouverte en séance (lecture sans montant). */
    private String propositionOuverte(Long idDmc, String idCandidat, String nif, String raison, int numero) {
        cnm.prs.entity.Offre o = new cnm.prs.entity.Offre();
        o.setIdOffre(java.util.UUID.randomUUID().toString());
        o.setIdDmc(idDmc);
        o.setIdCandidat(idCandidat);
        o.setIdEntreprise(entrepriseRepository.findByIdCandidat(idCandidat).orElseThrow().getIdEntreprise());
        o.setNif(nif);
        o.setRaisonSociale(raison);
        o.setEtat(cnm.prs.entity.Offre.DEPOSEE);
        o.setEnveloppe(cnm.prs.entity.Offre.TECHNIQUE);
        o.setDateCreation(LocalDateTime.now().minusDays(2));
        o.setDateDepot(LocalDateTime.now().minusDays(2));
        o.setNumero(numero);
        o.setEnTete("{}");
        o.setNombreMorceaux(1);
        o.setTailleMorceau(1);
        o.setQuorum(2);
        o.setN(3);
        o.setEmpreintesDetenteurs("x");
        o.setIntegrite("INTACTE");
        o.setOuverteLe(LocalDateTime.now().minusDays(1));
        o.setLecture("{\"pieces\":[]}");
        offreRepository.save(o);
        return o.getIdOffre();
    }

    @Autowired private cnm.prs.repository.EntrepriseRepository entrepriseRepository;

    /** La grille entière d'un membre : (i), (ii)a, (ii)b, (iii), (iv), (v). */
    private org.springframework.test.web.servlet.ResultActions notes(String jeton, String tech, String idOffre, int... n) throws Exception {
        String[] codes = { "B06-TP-02", "B06-TP-03#1", "B06-TP-03#2", "B06-TP-04", "B06-TP-05", "B06-TP-06" };
        StringBuilder b = new StringBuilder("{\"notes\":[");
        for (int i = 0; i < codes.length; i++) {
            b.append(i == 0 ? "" : ",").append("{\"element\":\"").append(codes[i]).append("\",\"note\":").append(n[i]).append(",\"motif\":\"Motif ")
                    .append(i).append("\"}");
        }
        return mvc.perform(put(tech + "/offres/" + idOffre + "/notes").header("Authorization", jeton).contentType(JSON).content(b + "]}"));
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
        // ⚠️ PI-c — la grille technique : cinq critères (100 points), le critère (ii) détaillé en deux sous-critères, minimum 70.
        donnees.put("B06-TP-02", "20");
        donnees.put("B06-TP-03", "30");
        donnees.put("B06-TP-04", "40");
        donnees.put("B06-TP-05", "5");
        donnees.put("B06-TP-06", "5");
        donnees.put("B06-TP-07", "70");
        // ⚠️ PI-d2a — les poids du score combiné (qualité-coût).
        donnees.put("B06-CS-02", "0.8");
        donnees.put("B06-CS-03", "0.2");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/sous-criteres").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"sousCriteres\":[{\"critere\":\"B06-TP-03\",\"libelle\":\"Approche et méthodologie\",\"points\":15},"
                        + "{\"critere\":\"B06-TP-03\",\"libelle\":\"Plan de travail\",\"points\":15}]}")).andExpect(status().isOk());
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
