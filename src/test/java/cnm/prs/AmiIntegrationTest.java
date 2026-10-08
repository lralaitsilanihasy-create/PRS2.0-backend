package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

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
 * ⚠️ 2026-10-07 (AMI en ligne, tranche AMI-a, §B1, §B2 ; V82) — la préparation de l'AMI d'une fiche de prestations intellectuelles
 * (critères pondérés sur 100), le projet d'avis, la publication (supports déclarés, avis signé, liste publique), la dispense ; le dépôt,
 * le remplacement et le retrait des expressions d'intérêt avant la date limite, l'accusé, et leur lecture fermée jusqu'à la date limite
 * (arbitrage Q2). Jeu : celui des lettres d'invitation (ligne 9901 PI, ligne 9902 de fournitures).
 */
class AmiIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String CRITERES = "\"criteres\":[{\"libelle\":\"Aptitude\",\"poids\":30},{\"libelle\":\"Références de missions similaires\","
            + "\"poids\":40},{\"libelle\":\"Expérience du personnel\",\"poids\":30}]";

    @Autowired private cnm.prs.service.ChampFicheMarcheService champService;
    @Autowired private cnm.prs.repository.AmiRepository amiRepository;
    @Autowired private cnm.prs.repository.CompteCandidatRepository candidats;
    @Autowired private cnm.prs.repository.NotificationRepository notificationRepository;

    private Long idDmc;
    private Long fournitures;
    private String base;
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
        idDmc = creerDmc(9901);
        fournitures = creerDmc(9902);
        base = "/api/fiches-marche/" + idDmc + "/ami";
        tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", "ANT");
        candidats.save(new CompteCandidat("C900000061", "a@ami.mg", "034 61 611 61", "Rabe", "Paul", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        candidats.save(new CompteCandidat("C900000062", "b@ami.mg", "034 62 622 62", "Rasoa", "Lova", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        jetonA = bearer("a@ami.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000061", null);
        jetonB = bearer("b@ami.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000062", null);
        mvc.perform(put("/api/candidat/entreprise").header("Authorization", jetonA).contentType(JSON).content("{\"raisonSociale\":\"Cabinet Alpha\","
                + "\"nif\":\"1111000111\",\"adresse\":\"Lot A\",\"representant\":{\"nom\":\"Rabe\",\"prenom\":\"Paul\"}}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Préparation (PRMP ou UGPM, fiche PI seule, critères pondérés sur 100), projet d'avis, publication (supports, avis signé, "
            + "liste publique, figé) ; dispense sur une autre fiche impossible hors PI")
    void preparationEtPublication() throws Exception {
        mvc.perform(get(base).header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        mvc.perform(put("/api/fiches-marche/" + fournitures + "/ami").header("Authorization", tokenPrmp).contentType(JSON).content("{" + CRITERES + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORIE_SANS_AMI"));
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON).content("{\"criteres\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CRITERES_OBLIGATOIRES"));
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"criteres\":[{\"libelle\":\"Aptitude\",\"poids\":30},{\"libelle\":\"Références\",\"poids\":40}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PONDERATION_INVALIDE"));
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON)
                .content("{" + CRITERES + ",\"dateLimite\":\"" + LocalDateTime.now().minusDays(1).withNano(0) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DATE_LIMITE_INVALIDE"));
        mvc.perform(put(base).header("Authorization", tokenUgpm).contentType(JSON).content("{" + CRITERES + ",\"pieces\":[\"Lettre de "
                + "manifestation d'intérêt signée\",\"Références de missions similaires\"],\"noteMinimale\":60,\"dateLimite\":\""
                + LocalDateTime.now().plusDays(15).withNano(0) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("BROUILLON")).andExpect(jsonPath("$.criteres[1].code").value("C2"))
                .andExpect(jsonPath("$.nombreRetenus").value(6)).andExpect(jsonPath("$.objet").value("Étude de faisabilité du schéma directeur"))
                .andExpect(jsonPath("$.lectureOuverte").value(false));
        String projet = texteDuPdf(mvc.perform(get(base + "/avis").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(projet).contains("PROJET D'AVIS À MANIFESTATION D'INTÉRÊT", "Références de missions similaires", "liste restreinte de 6",
                "Note minimale de qualification : 60").doesNotContain("signé électroniquement");
        mvc.perform(get("/api/amis-en-ligne/" + idDmc)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/publier").header("Authorization", tokenUgpm).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/publier").header("Authorization", tokenPrmp).contentType(JSON).content("{\"publications\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUBLICATION_OBLIGATOIRE"));
        mvc.perform(post(base + "/publier").header("Authorization", tokenPrmp).contentType(JSON).content("{\"publications\":[{\"support\":"
                + "\"Journal des marchés publics de l'ARMP\",\"date\":\"" + LocalDate.now() + "\",\"reference\":\"JMP n° 412\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("PUBLIE")).andExpect(jsonPath("$.publications[0].reference").value("JMP n° 412"));
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON).content("{" + CRITERES + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AMI_PUBLIE"));
        mvc.perform(post(base + "/dispense").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AMI_PUBLIE"));
        mvc.perform(get("/api/amis-en-ligne")).andExpect(status().isOk()).andExpect(jsonPath("$[0].idDmc").value(idDmc))
                .andExpect(jsonPath("$[0].ouvert").value(true));
        String avis = texteDuPdf(mvc.perform(get("/api/amis-en-ligne/" + idDmc + "/avis")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(avis).contains("AVIS À MANIFESTATION D'INTÉRÊT", "signé électroniquement sur la plateforme", "modèle provisoire")
                .doesNotContain("PROJET D'AVIS");
        assertThat(journalRepository().findByIdDmcOrderByDateAscIdAsc(idDmc)).extracting(j -> j.getAction()).contains("AMI_PREPARE", "AMI_PUBLIE");
    }

    @Test
    @DisplayName("Dispense de publicité : motif exigé, PRMP seule, AMI figé ; pas d'avis")
    void dispense() throws Exception {
        mvc.perform(post(base + "/dispense").header("Authorization", tokenPrmp).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(post(base + "/dispense").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"Sous le seuil\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("DISPENSE")).andExpect(jsonPath("$.motifDispense").value("Sous le seuil"));
        mvc.perform(get(base + "/avis").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMI_DISPENSE"));
    }

    @Test
    @DisplayName("Dépôt : pièces attendues exigées, accusé et empreinte, remplacement, retrait ; illisible avant la date limite (Q2), "
            + "lisible après ; plus de dépôt passé la date limite")
    void depotDesExpressions() throws Exception {
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON).content("{" + CRITERES + ",\"pieces\":[\"Références\"],"
                + "\"dateLimite\":\"" + LocalDateTime.now().plusDays(15).withNano(0) + "\"}")).andExpect(status().isOk());
        String url = "/api/candidat/amis/" + idDmc + "/expression";
        MockMultipartFile refs = new MockMultipartFile("fichiers", "refs.pdf", "application/pdf", "%PDF-1.4 r".getBytes(StandardCharsets.ISO_8859_1));
        String corps = "{\"lettre\":\"Nous manifestons notre intérêt.\",\"qualifications\":\"Dix ans d'études\",\"references\":[{\"intitule\":"
                + "\"Schéma directeur de Toamasina\",\"client\":\"Commune\",\"annee\":2024}],\"pieces\":[{\"libelle\":\"Références\",\"fichier\":\"refs.pdf\"}]}";
        mvc.perform(multipart(url).file(refs).param("expression", corps).header("Authorization", jetonA)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/publier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"publications\":[{\"support\":\"Journal national\",\"date\":\"" + LocalDate.now() + "\"}]}")).andExpect(status().isOk());
        mvc.perform(multipart(url).file(refs).param("expression", corps).header("Authorization", jetonB)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENTREPRISE_NON_DECLAREE"));
        mvc.perform(multipart(url).param("expression", "{\"lettre\":\"x\"}").header("Authorization", jetonA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PIECES_MANQUANTES")).andExpect(jsonPath("$.details.pieces[0]").value("Références"));
        mvc.perform(multipart(url).file(refs).param("expression", "{\"pieces\":[]}").header("Authorization", jetonA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LETTRE_OBLIGATOIRE"));
        String premiere = mvc.perform(multipart(url).file(refs).param("expression", corps).header("Authorization", jetonA))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(1)).andExpect(jsonPath("$.empreinte").isNotEmpty())
                .andExpect(jsonPath("$.pieces[0].libelle").value("Références")).andReturn().getResponse().getContentAsString();
        assertThat(notificationRepository.findAll()).filteredOn(n -> "C900000061".equals(n.getDestinataireRef()))
                .extracting(Notification::getTypeNotif).contains("AMI_EXPRESSION_DEPOSEE");
        mvc.perform(multipart(url).file(refs).param("expression", corps.replace("Dix ans", "Douze ans")).header("Authorization", jetonA))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(2)).andExpect(jsonPath("$.qualifications").value("Douze ans d'études"));
        long idPiece = JsonPath.<Number>read(mvc.perform(get(url).header("Authorization", jetonA)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.pieces[0].id").longValue();
        mvc.perform(get(url + "/pieces/" + idPiece).header("Authorization", jetonA)).andExpect(status().isOk());
        assertThat(JsonPath.<String>read(premiere, "$.id")).isNotBlank();
        // Q2 : le nombre se lit, le contenu non, avant la date limite.
        mvc.perform(get(base).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.nombreExpressions").value(1));
        mvc.perform(get(base + "/expressions").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURE_FERMEE"));
        mvc.perform(delete(url).header("Authorization", jetonA)).andExpect(status().isNoContent());
        mvc.perform(get(base).header("Authorization", tokenPrmp)).andExpect(jsonPath("$.nombreExpressions").value(0));
        mvc.perform(multipart(url).file(refs).param("expression", corps).header("Authorization", jetonA)).andExpect(status().isCreated());
        // La date limite passée : lecture ouverte, plus de dépôt ni de retrait.
        cnm.prs.entity.Ami a = amiRepository.findById(idDmc).orElseThrow();
        a.setDateLimite(LocalDateTime.now().minusMinutes(1));
        amiRepository.save(a);
        mvc.perform(multipart(url).file(refs).param("expression", corps).header("Authorization", jetonA)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATE_LIMITE_DEPASSEE"));
        mvc.perform(delete(url).header("Authorization", jetonA)).andExpect(status().isConflict());
        String lues = mvc.perform(get(base + "/expressions").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(lues, "$[*].raisonSociale")).containsExactly("Cabinet Alpha");
        assertThat(JsonPath.<String>read(lues, "$[0].lettre")).isEqualTo("Nous manifestons notre intérêt.");
        String idExpression = JsonPath.read(lues, "$[0].id");
        long idP = JsonPath.<Number>read(lues, "$[0].pieces[0].id").longValue();
        mvc.perform(get(base + "/expressions/" + idExpression + "/pieces/" + idP).header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(get("/api/amis-en-ligne")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/amis-en-ligne/" + idDmc)).andExpect(status().isOk()).andExpect(jsonPath("$.ouvert").value(false));
    }

    @Test
    @DisplayName("AMI-b : déclarations, notation motivée et bornée, écartement, arrêt par le président (notation complète, moins de six "
            + "qualifiés motivé), rapport signé hors conflit, liste définitive publiée et notifiée, dossier de la DP (garde puis rapport "
            + "joint), lettres d'invitation tirées de la liste")
    void preselection() throws Exception {
        String[] m = commission();
        String jetonM1 = m[0];
        String jetonM2 = m[1];
        String jetonC = bearer("c@ami.mg", ProfilUtilisateur.CANDIDAT, TypeActeur.CANDIDAT, "C900000063", null);
        candidats.save(new CompteCandidat("C900000063", "c@ami.mg", "034 63 633 63", "Rakoto", "Fara", CompteCandidat.CONFIRME, false,
                LocalDateTime.now(), LocalDateTime.now(), null, null));
        declarer(jetonB, "2222000222", "Bureau Beta");
        declarer(jetonC, "3333000333", "Conseil Gamma");
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON).content("{" + CRITERES + ",\"noteMinimale\":50,"
                + "\"nombreRetenus\":3,\"dateLimite\":\"" + LocalDateTime.now().plusDays(15).withNano(0) + "\"}")).andExpect(status().isOk());
        mvc.perform(post(base + "/publier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"publications\":[{\"support\":\"Journal national\",\"date\":\"" + LocalDate.now() + "\"}]}")).andExpect(status().isOk());
        String a = deposer(jetonA);
        String b = deposer(jetonB);
        String c = deposer(jetonC);
        String pre = base + "/preselection";
        mvc.perform(get(pre).header("Authorization", jetonM1)).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.expressions.length()").value(0));
        cnm.prs.entity.Ami ami = amiRepository.findById(idDmc).orElseThrow();
        ami.setDateLimite(LocalDateTime.now().minusMinutes(1));
        amiRepository.save(ami);

        noter(jetonM1, a, "C1", 25, "x").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DECLARATION_MANQUANTE"));
        mvc.perform(post(pre + "/declaration").header("Authorization", jetonM1).contentType(JSON).content("{\"conflit\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("NOTATION")).andExpect(jsonPath("$.expressions.length()").value(3));
        mvc.perform(post(pre + "/declaration").header("Authorization", jetonM2).contentType(JSON)
                .content("{\"conflit\":true,\"precision\":\"Ancien associé de Bureau Beta\"}")).andExpect(status().isOk());
        noter(jetonM2, a, "C1", 25, "x").andExpect(status().isForbidden());
        noter(jetonM1, a, "C1", 31, "x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTE_HORS_BAREME"));
        noter(jetonM1, a, "C9", 1, "x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CRITERE_INCONNU"));
        noter(jetonM1, a, "C1", 25, " ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(put(base + "/expressions/" + a + "/notes").header("Authorization", jetonM1).contentType(JSON).content("{\"notes\":["
                + "{\"code\":\"C1\",\"note\":25,\"motif\":\"Cabinet agréé\"},{\"code\":\"C2\",\"note\":35,\"motif\":\"Quatre missions similaires\"},"
                + "{\"code\":\"C3\",\"note\":25,\"motif\":\"Experts seniors\"}]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.expressions[0].total").value(85)).andExpect(jsonPath("$.expressions[0].qualifiee").value(true));
        mvc.perform(post(pre + "/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTATION_INCOMPLETE"));
        mvc.perform(put(base + "/expressions/" + b + "/notes").header("Authorization", jetonM1).contentType(JSON).content("{\"notes\":["
                + "{\"code\":\"C1\",\"note\":20,\"motif\":\"a\"},{\"code\":\"C2\",\"note\":30,\"motif\":\"b\"},{\"code\":\"C3\",\"note\":20,\"motif\":\"c\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/expressions/" + c + "/ecartement").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_OBLIGATOIRE"));
        mvc.perform(post(base + "/expressions/" + c + "/ecartement").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"ecartee\":true,\"motif\":\"Lettre non signée\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.expressions[2].ecartee").value(true));
        mvc.perform(post(pre + "/arreter").header("Authorization", jetonM1).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MOTIF_NOMBRE_OBLIGATOIRE"));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTE_NON_ARRETEE"));
        mvc.perform(post(pre + "/arreter").header("Authorization", jetonM1).contentType(JSON)
                .content("{\"motifNombre\":\"Deux candidats seulement atteignent la note minimale.\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("LISTE_ARRETEE")).andExpect(jsonPath("$.liste.length()").value(2))
                .andExpect(jsonPath("$.liste[0].raisonSociale").value("Cabinet Alpha"))
                .andExpect(jsonPath("$.rapport.attendues.length()").value(1));
        noter(jetonM1, a, "C1", 20, "x").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LISTE_ARRETEE"));
        mvc.perform(post(base + "/rapport/signer").header("Authorization", jetonM2).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/rapport/signer").header("Authorization", jetonM1).contentType(JSON).content("{}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.etat").value("DEFINITIVE")).andExpect(jsonPath("$.rapport.signe").value(true));
        String rapport = texteDuPdf(mvc.perform(get(base + "/rapport").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).replaceAll("\\s+", " ");
        assertThat(rapport).contains("RAPPORT DE PRÉSÉLECTION", "Cabinet Alpha", "Lettre non signée", "Deux candidats seulement",
                "conflit d'intérêts déclaré", "signé électroniquement sur la plateforme");
        assertThat(notificationRepository.findAll()).filteredOn(n -> "AMI_RESULTAT".equals(n.getTypeNotif()))
                .extracting(Notification::getDestinataireRef).containsExactlyInAnyOrder("C900000061", "C900000062", "C900000063");
        assertThat(notificationRepository.findAll()).filteredOn(n -> "C900000063".equals(n.getDestinataireRef()) && "AMI_RESULTAT".equals(n.getTypeNotif()))
                .extracting(Notification::getCorps).allMatch(t -> t.contains("Lettre non signée"));
        mvc.perform(get("/api/amis-en-ligne/" + idDmc)).andExpect(jsonPath("$.liste.length()").value(2))
                .andExpect(jsonPath("$.liste[1].raisonSociale").value("Bureau Beta"));

        // Q4 : le dossier de la demande de propositions reçoit d'office le rapport signé ; §B4 : les lettres prennent la liste.
        cnm.prs.entity.TypePieceJointe tp = typePieceJointeRepository.findById(seedTypePiece("Rapport de présélection (AMI)", false, "DMC", 9))
                .orElseThrow();
        tp.setCode("RAPPORT_PRESELECTION");
        typePieceJointeRepository.save(tp);
        String dossier = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int idDossier = JsonPath.read(dossier, "$.idDossier");
        assertThat(pieceJointeDossierRepository.findByIdDossier(idDossier)).extracting(p -> p.getNomFichier()).contains("rapport-preselection-ami_" + idDmc + ".pdf");
        receptionRepository.save(reception(9950, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(9950, 9950, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9950, 9950, "CTRMEM"));
        seedPvSigne(9950, 9950);
        String lettres = mvc.perform(post("/api/fiches-marche/" + idDmc + "/lettres-invitation").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"dateEnvoi\":\"2026-10-08\",\"lieu\":\"Antananarivo\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(lettres, "$")).hasSize(4);
        assertThat(notificationRepository.findAll()).filteredOn(n -> "LETTRE_INVITATION".equals(n.getTypeNotif()))
                .extracting(Notification::getDestinataireRef).containsExactlyInAnyOrder("C900000061", "C900000062");
    }

    @Test
    @DisplayName("AMI-b : relance par la PRMP (nouvelle date limite, motif), puis infructuosité sans candidat qualifié (art. 56-II)")
    void relanceEtInfructuosite() throws Exception {
        String[] m = commission();
        mvc.perform(put(base).header("Authorization", tokenPrmp).contentType(JSON).content("{" + CRITERES + ",\"dateLimite\":\""
                + LocalDateTime.now().plusDays(15).withNano(0) + "\"}")).andExpect(status().isOk());
        mvc.perform(post(base + "/publier").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"publications\":[{\"support\":\"Journal national\",\"date\":\"" + LocalDate.now() + "\"}]}")).andExpect(status().isOk());
        String a = deposer(jetonA);
        cnm.prs.entity.Ami ami = amiRepository.findById(idDmc).orElseThrow();
        ami.setDateLimite(LocalDateTime.now().minusMinutes(1));
        amiRepository.save(ami);
        mvc.perform(post(base + "/relancer").header("Authorization", m[0]).contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(base + "/relancer").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"Une seule réponse\",\"dateLimite\":\"" + LocalDateTime.now().minusDays(1).withNano(0) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DATE_LIMITE_INVALIDE"));
        mvc.perform(post(base + "/relancer").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"Une seule réponse\",\"dateLimite\":\"" + LocalDateTime.now().plusDays(10).withNano(0) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("EN_ATTENTE")).andExpect(jsonPath("$.nombreRelances").value(1));
        mvc.perform(get("/api/amis-en-ligne")).andExpect(jsonPath("$[0].idDmc").value(idDmc));
        ami = amiRepository.findById(idDmc).orElseThrow();
        ami.setDateLimite(LocalDateTime.now().minusMinutes(1));
        amiRepository.save(ami);
        mvc.perform(post(base + "/preselection/declaration").header("Authorization", m[0]).contentType(JSON).content("{\"conflit\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/preselection/arreter").header("Authorization", m[0]).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTATION_INCOMPLETE"));
        mvc.perform(post(base + "/expressions/" + a + "/ecartement").header("Authorization", m[0]).contentType(JSON)
                .content("{\"motif\":\"Hors du domaine de la mission\"}")).andExpect(status().isOk());
        mvc.perform(post(base + "/preselection/arreter").header("Authorization", m[0]).contentType(JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUCUN_QUALIFIE"));
        mvc.perform(post(base + "/infructueux").header("Authorization", tokenPrmp).contentType(JSON).content("{\"motif\":\"Aucun candidat qualifié\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("INFRUCTUEUX"));
        mvc.perform(post(base + "/relancer").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"motif\":\"x\",\"dateLimite\":\"" + LocalDateTime.now().plusDays(10).withNano(0) + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AMI_INFRUCTUEUX"));
    }

    /** Une CAO de deux membres (le premier préside), un responsable ; les jetons des deux membres. */
    private String[] commission() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isCreated());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cao").header("Authorization", tokenPrmp).contentType(JSON)
                .content(CaoIntegrationTest.corpsCao("m1@ami.mg", "m2@ami.mg"))).andExpect(status().isOk());
        String internes = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andReturn().getResponse().getContentAsString();
        List<String> ims = JsonPath.read(internes, "$.membresCommission[*].im");
        return new String[] { bearer("m1@ami.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, ims.get(0), null),
                bearer("m2@ami.mg", ProfilUtilisateur.MEMBRE_CAO, TypeActeur.MEMBRE_CAO, ims.get(1), null) };
    }

    private void declarer(String jeton, String nif, String raison) throws Exception {
        mvc.perform(put("/api/candidat/entreprise").header("Authorization", jeton).contentType(JSON).content("{\"raisonSociale\":\"" + raison
                + "\",\"nif\":\"" + nif + "\",\"adresse\":\"Lot " + nif + "\",\"representant\":{\"nom\":\"Rakoto\",\"prenom\":\"Jean\"}}"))
                .andExpect(status().isOk());
    }

    private String deposer(String jeton) throws Exception {
        String r = mvc.perform(multipart("/api/candidat/amis/" + idDmc + "/expression").param("expression", "{\"lettre\":\"Intérêt confirmé.\"}")
                .header("Authorization", jeton)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(r, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions noter(String jeton, String idExpression, String code, int note, String motif)
            throws Exception {
        return mvc.perform(put(base + "/expressions/" + idExpression + "/notes").header("Authorization", jeton).contentType(JSON)
                .content("{\"notes\":[{\"code\":\"" + code + "\",\"note\":" + note + ",\"motif\":\"" + motif + "\"}]}"));
    }

    @Autowired private cnm.prs.repository.PieceJointeDossierRepository pieceJointeDossierRepository;

    private cnm.prs.repository.EvaluationJournalRepository journalRepository() {
        return journal;
    }

    @Autowired private cnm.prs.repository.EvaluationJournalRepository journal;

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
        if (idDetail == 9901) {
            remplirObligatoiresEtValider(id, "QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES",
                    Map.of("B02-MS-01", "Budget prédéterminé dont le candidat propose la meilleure utilisation"));
        }
        return id;
    }
}
