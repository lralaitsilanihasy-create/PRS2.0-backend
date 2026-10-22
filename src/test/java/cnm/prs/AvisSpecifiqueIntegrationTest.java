package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.TypeDmc;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.repository.FicheMarcheValeurRepository;

/**
 * ⚠️ <strong>Avis spécifique d'appel d'offres</strong> (demande front du 2026-09-30, §B3, §B4, §B6) — la garde (raisons de
 * {@code AVIS_INDISPONIBLE}, dont un PV FAVR avant puis après la levée des réserves), l'impression sur la dernière version
 * validée, deux impressions successives, une révision ouverte, les informations de publication hors de la fiche, l'avis
 * jamais joint au dossier comme pièce du DAO.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), ligne 9901 de fournitures à quantité fixe (mode 92 → DAO),
 * ligne 9902 de prestations intellectuelles (nature 93). Le dossier DAO de la ligne 9901 est produit par la fiche ; sa
 * chaîne réception → dispatch → examen → PV (9950) est posée à la main, avec l'avis et le statut voulus.</p>
 */
class AvisSpecifiqueIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String CORPS = "{\"datePublication\":\"2026-10-05\",\"jmpNumero\":\"123\",\"jmpDate\":\"2026-01-15\","
            + "\"supports\":\"le quotidien Midi Madagasikara du 06/10/2026\"}";

    @Autowired private FicheMarcheValeurRepository valeurRepository;
    @Autowired private cnm.prs.service.ChampFicheMarcheService champService;
    @Autowired private cnm.prs.repository.NotificationRepository notificationRepository;
    @Autowired private cnm.prs.service.FicheMarcheService ficheMarcheService;
    @Autowired private cnm.prs.service.DocumentsFicheMarcheService documentsFicheMarcheService;

    private Long idDmc;

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
        publicationAgpmDeTest(9900);   // ⚠️ V118 (H1) : l'avis lit le journal de l'avis général sur le plan

        Marche l = marcheDao(9901, 9900, 9900);
        l.setIdMode(92);
        l.setDesignationMarche("Fourniture de mobilier de bureau");
        marcheRepository.save(l);
        natureRepository.save(new cnm.prs.entity.Nature(93, "Prestations intellectuelles", null, "PRESTATIONS_INTELLECTUELLES"));
        Marche pi = marcheDao(9902, 9900, 9900);
        pi.setIdMode(92);
        pi.setIdNature(93);
        pi.setDesignationMarche("Étude de faisabilité");
        marcheRepository.save(pi);

        TypePieceJointe t = typePieceJointeRepository.findById(seedTypePiece("Dossier d'appel d'offres complet", true, "DMC", 1))
                .orElseThrow();
        t.setCode("DAO_COMPLET");
        typePieceJointeRepository.save(t);

        idDmc = creerDmc(9901);
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\",\"alloti\":\"NON\",\"typePrix\":\"UNITAIRES\"}}")).andExpect(status().isOk());   // alloti : imposé par le plan à l'écran
        valider();
    }

    @Test
    @DisplayName("Garde : sans dossier, PV non signé, PV FAVR avant la levée (409 AVIS_INDISPONIBLE, raison dans details) "
            + "puis après (OBSERVATIONS_LEVEES, DECISION_TRANSMISE_SIGMP, CLOTURE), DEF, prestations intellectuelles")
    void garde() throws Exception {
        disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(false)).andExpect(jsonPath("$.raison").value("SANS_DOSSIER"));
        imprimer(idDmc).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AVIS_INDISPONIBLE"))
                .andExpect(jsonPath("$.details.raison").value("SANS_DOSSIER"));

        int idDossier = creerDossier();
        disponibilite(idDmc).andExpect(jsonPath("$.raison").value("PV_NON_SIGNE"))
                .andExpect(jsonPath("$.idDossierSoumis").value(idDossier));

        pvSigne(idDossier, "FAVR", "EN_VERIFICATION");
        disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.raison").value("RESERVES_NON_LEVEES")).andExpect(jsonPath("$.idAvis").value("FAVR"))
                .andExpect(jsonPath("$.statutPv").value("SIGNE")).andExpect(jsonPath("$.statutDossier").value("EN_VERIFICATION"));
        imprimer(idDmc).andExpect(status().isConflict()).andExpect(jsonPath("$.details.raison").value("RESERVES_NON_LEVEES"));
        for (String levee : List.of("OBSERVATIONS_LEVEES", "DECISION_TRANSMISE_SIGMP", "CLOTURE")) {
            statut(idDossier, levee);
            disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(true)).andExpect(jsonPath("$.raison").isEmpty())
                    .andExpect(jsonPath("$.daoComplet").value("SANS_ASSEMBLAGE"));   // ⚠️ R9a : pas d'assemblage en test
        }
        statut(idDossier, "EN_ATTENTE_DECISION_PRMP");
        disponibilite(idDmc).andExpect(jsonPath("$.raison").value("RESERVES_NON_LEVEES"));

        avis(idDossier, "FAV");
        disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(true));   // FAV : dès le PV signé
        // ⚠️ V118 (2026-10-15, §B2, H1) — sans publication de l'AGPM sur le plan, l'avis ne s'imprime pas : 409 nommé.
        publicationAgpmRepository.deleteAll();
        disponibilite(idDmc).andExpect(jsonPath("$.raison").value("PUBLICATION_AGPM_ABSENTE"));
        imprimer(idDmc).andExpect(status().isConflict()).andExpect(jsonPath("$.details.raison").value("PUBLICATION_AGPM_ABSENTE"));
        publicationAgpmDeTest(9900);
        avis(idDossier, "DEF");
        disponibilite(idDmc).andExpect(jsonPath("$.raison").value("AVIS_NON_FAVORABLE"));

        Long pi = creerDmc(9902);
        disponibilite(pi).andExpect(jsonPath("$.raison").value("CATEGORIE_SANS_AVIS"));

        mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/disponibilite").header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenMembre)
                .contentType(JSON).content(CORPS)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("« avis-a-imprimer » : impressionAvis sur la liste (PRMP ; absent pour le Président) ; AVIS_A_IMPRIMER au passage "
            + "(dépôt de la publication de l'AGPM), vers la PRMP et son UGPM, une seule fois ; déjà imprimé après l'impression")
    void avisAImprimer() throws Exception {
        ugpmRepository.save(ugpm("UG9901", "PRMP001", "RABE", "Hery"));
        compteAuthRepository.save(new cnm.prs.entity.CompteAuth("ugpm.avis", "x", "UGPM", "UG9901", true));
        String tokenUgpm = bearer("ugpm.avis", cnm.prs.enums.ProfilUtilisateur.UGPM, cnm.prs.enums.TypeActeur.UGPM, "PRMP001", "ANT");
        Dossier plan = dossierRepository.findById(9900).orElseThrow();
        plan.setIdTypeDossier("DDP");   // la publication de l'AGPM se dépose sur un plan
        plan.setIdSousType("PPM");
        dossierRepository.save(plan);
        int idDossier = creerDossier();
        publicationAgpmRepository.deleteAll();
        pvSigne(idDossier, "FAV", "EN_VERIFICATION");   // posé à la main : aucune notification (l'avis attend la publication)

        String liste = mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ici = "$[?(@.idDossier==" + idDossier + ")].impressionAvis";
        assertThat(JsonPath.<List<String>>read(liste, ici + ".document")).containsExactly("AVIS_SPECIFIQUE");
        assertThat(JsonPath.<List<Boolean>>read(liste, ici + ".disponible")).containsExactly(false);
        assertThat(JsonPath.<List<Boolean>>read(liste, ici + ".dejaImprime")).containsExactly(false);
        String president = mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(president, "$[?(@.idDossier==" + idDossier + ")].impressionAvis")).isEmpty();

        // Le dépôt de la publication de l'AGPM rend l'avis imprimable : la PRMP et son UGPM sont prévenues.
        deposerPublication();
        List<cnm.prs.entity.Notification> notes = notificationRepository.findAll().stream()
                .filter(n -> "AVIS_A_IMPRIMER".equals(n.getTypeNotif())).toList();
        assertThat(notes).extracting(n -> n.getDestinataireType() + ":" + n.getDestinataireRef())
                .containsExactlyInAnyOrder("PRMP:PRMP001", "UGPM:UG9901");
        assertThat(notes).allMatch(n -> "DOSSIER".equals(n.getTypeObjet()) && Integer.valueOf(idDossier).equals(n.getIdObjet())
                && n.getCorps().contains("Fourniture de mobilier de bureau") && n.getCorps().contains("peut être imprimé"));
        String boite = mvc.perform(get("/api/notifications/mes").header("Authorization", tokenUgpm)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(boite, "$[*].typeNotif")).contains("AVIS_A_IMPRIMER");

        // Toujours imprimable : un nouveau dépôt ne renotifie pas.
        deposerPublication();
        assertThat(notificationRepository.findAll()).filteredOn(n -> "AVIS_A_IMPRIMER".equals(n.getTypeNotif())).hasSize(2);

        imprimer(idDmc).andExpect(status().isCreated());
        liste = mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(liste, ici + ".disponible")).containsExactly(true);
        assertThat(JsonPath.<List<Boolean>>read(liste, ici + ".dejaImprime")).containsExactly(true);
    }

    @Test
    @DisplayName("« avis-a-imprimer » §B3 — accueil « À faire » : une ligne AVIS_A_IMPRIMER / IMPRIMER_AVIS (document, SANS_DELAI) sur "
            + "un dossier clôturé, comptée dans aFaire et la pastille, servie aussi par …/gestes ; l'UGPM la voit, le Président non ; "
            + "elle disparaît à la première impression")
    void aFaireAvisAImprimer() throws Exception {
        ugpmRepository.save(ugpm("UG9901", "PRMP001", "RABE", "Hery"));
        compteAuthRepository.save(new cnm.prs.entity.CompteAuth("ugpm.afaire", "x", "UGPM", "UG9901", true));
        String tokenUgpm = bearer("ugpm.afaire", cnm.prs.enums.ProfilUtilisateur.UGPM, cnm.prs.enums.TypeActeur.UGPM, "PRMP001", "ANT");
        int idDossier = creerDossier();
        String ligne = "$.taches[?(@.section=='AVIS_A_IMPRIMER' && @.dossier.idDossier==" + idDossier + ")]";
        assertThat(JsonPath.<List<Object>>read(aFaire(tokenPrmp), ligne)).isEmpty();   // PV non signé : rien à imprimer

        pvSigne(idDossier, "FAV", "CLOTURE");   // clôturé : hors des statuts actifs de l'accueil
        for (String jeton : List.of(tokenPrmp, tokenUgpm)) {
            String corps = aFaire(jeton);
            assertThat(JsonPath.<List<String>>read(corps, ligne + ".geste")).containsExactly("IMPRIMER_AVIS");
            assertThat(JsonPath.<List<String>>read(corps, ligne + ".document")).containsExactly("AVIS_SPECIFIQUE");
            assertThat(JsonPath.<List<String>>read(corps, ligne + ".urgence")).containsExactly("SANS_DELAI");
            assertThat(JsonPath.<List<String>>read(corps, ligne + ".mode")).containsExactly("TITULAIRE");
            assertThat(JsonPath.<List<String>>read(corps, "$.sections[?(@.code=='AVIS_A_IMPRIMER')].code")).hasSize(1);
            assertThat(JsonPath.<List<Object>>read(corps, "$.taches[?(@.section!='AVIS_A_IMPRIMER')].document")).isEmpty();
        }
        String prmp = aFaire(tokenPrmp);
        int aFaire = JsonPath.read(prmp, "$.compteurs.aFaire");
        assertThat(aFaire).isEqualTo(JsonPath.<List<Object>>read(prmp,
                "$.taches[?(@.section!='EN_COURS_CNM' && @.section!='EN_ATTENTE_PRMP')]").size()).isPositive();
        String badges = mvc.perform(get("/api/kpis/badges").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(badges, "$.aFaire")).isEqualTo(aFaire);
        String gestes = mvc.perform(get("/api/dossiers/" + idDossier + "/gestes").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(gestes, "$.taches[*].geste")).containsExactly("IMPRIMER_AVIS");
        assertThat(JsonPath.<List<Object>>read(aFaire(tokenPresident), "$.taches[?(@.section=='AVIS_A_IMPRIMER')]")).isEmpty();

        imprimer(idDmc).andExpect(status().isCreated());   // la première impression retire la ligne
        assertThat(JsonPath.<List<Object>>read(aFaire(tokenPrmp), ligne)).isEmpty();
        gestes = mvc.perform(get("/api/dossiers/" + idDossier + "/gestes").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(gestes, "$.taches")).isEmpty();
    }

    private String aFaire(String jeton) throws Exception {
        return mvc.perform(get("/api/dossiers/a-faire").header("Authorization", jeton)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void deposerPublication() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/dossiers/9900/publication-agpm")
                .file(new org.springframework.mock.web.MockMultipartFile("fichier", "jmp.pdf", "application/pdf",
                        "%PDF-1.4\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                .param("journal", "Journal des marchés publics").param("dateParution", "2026-01-15").param("referenceAgpm", "AGPM-9900")
                .header("Authorization", tokenPrmp)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Impression : 400 nominatif si une information manque ou si une date est illisible ; 201, paire .docx / .pdf "
            + "de type AVIS avec les informations de publication rendues JJ/MM/AAAA et gardées en trace, absentes de la fiche ; "
            + "une seconde impression ajoute une paire ; l'avis n'est pas joint au dossier")
    void impression() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"datePublication\":\"05/10/2026\",\"jmpNumero\":\"123\",\"jmpDate\":\"2026-01-15\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='datePublication')]").isNotEmpty())
                // ⚠️ 2026-10-01 (§B7.5) — le numéro du JMP et les supports sont facultatifs : seules les deux dates sont exigées.
                .andExpect(jsonPath("$.erreurs[?(@.champ=='supports')]").isEmpty())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='jmpNumero')]").isEmpty());

        String premier = imprimer(idDmc).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(premier, "$[*].type")).containsExactly("AVIS", "AVIS");
        assertThat(JsonPath.<List<String>>read(premier, "$[*].extension")).containsExactly("docx", "pdf");
        assertThat(JsonPath.<List<String>>read(premier, "$[*].libelle")).containsOnly("Avis spécifique d'appel d'offres");
        assertThat(JsonPath.<List<Integer>>read(premier, "$[*].version")).containsOnly(1);
        assertThat(JsonPath.<List<String>>read(premier, "$[*].publication.datePublication")).containsOnly("2026-10-05");
        assertThat(JsonPath.<List<String>>read(premier, "$[*].nomFichier")).allMatch(n -> n.startsWith("AVIS_") && n.contains("_v1_"));
        int idDocx = JsonPath.<List<Integer>>read(premier, "$[?(@.extension=='docx')].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + idDocx + "/contenu")
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(doc.getAllPictures()).as("l'emblème en tête (§B7.8)").hasSize(1);
            // ⚠️ 2026-10-01 — le bloc de signature gardé ensemble : « à …, le … » et la qualité solidaires du suivant (le nom)
            List<org.apache.poi.xwpf.usermodel.XWPFParagraph> ps = doc.getParagraphs();
            assertThat(ps.subList(ps.size() - 3, ps.size())).extracting(p -> p.getCTP().isSetPPr() && p.getCTP().getPPr().isSetKeepNext())
                    .containsExactly(true, true, false);
            assertThat(ps.get(ps.size() - 2).getText()).isEqualTo("La Personne Responsable des Marchés Publics");
        }
        String texte = texteDuDocx(docx);
        assertThat(texte).contains("05/10/2026", "Journal des Marchés Publics n°123 en date du 15/01/2026",
                "le quotidien Midi Madagasikara du 06/10/2026", "Fourniture de mobilier de bureau")
                .doesNotContain("{{", "2026-10-05");
        // Les informations de publication n'entrent pas dans la fiche.
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idFiche = JsonPath.read(fiche, "$.idFiche");
        assertThat(valeurRepository.findByIdFiche(idFiche)).noneMatch(v -> v.getCodeChamp().startsWith("AVIS")
                || String.valueOf(v.getValeur()).contains("Midi Madagasikara"));

        imprimer(idDmc).andExpect(status().isCreated());
        String liste = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> avis = JsonPath.read(liste, "$[?(@.type=='AVIS')].idDocument");
        assertThat(avis).hasSize(4).isSortedAccordingTo(java.util.Comparator.reverseOrder());   // du plus récent au plus ancien
        assertThat(JsonPath.<List<String>>read(liste, "$[?(@.type!='AVIS')].type")).contains("DPAO", "CCAP", "AE");

        // Rattachement de secours du dossier à la fiche : les PDF du DAO sont joints, jamais l'avis.
        statut(idDossier, "BROUILLON");
        mvc.perform(delete("/api/dossiers/" + idDossier + "/fiche-marche").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        mvc.perform(put("/api/dossiers/" + idDossier + "/fiche-marche").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"idDmc\":" + idDmc + "}")).andExpect(status().isOk());
        String pieces = mvc.perform(get("/api/piece-jointe-dossiers").param("dossier", String.valueOf(idDossier))
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(pieces, "$[*].nomFichier")).isNotEmpty()
                .noneMatch(n -> n.startsWith("AVIS_") && !n.startsWith("AVIS_PROJET_"));   // ⚠️ R3b : le projet, lui, peut l'être
    }

    @Test
    @DisplayName("⚠️ R3b (manuel révisé, §B3) — le projet d'avis dès la fiche : rendu à la volée (pdf, ?format=docx) sur la dernière "
            + "version validée, PROJET en filigrane, publication en pointillés, pied d'équivalence, sans « Lancé » ; produit à la validation "
            + "et joint d'office au dossier sous AVIS_SPECIFIQUE, remplacé à la version suivante, absent de la liste des documents ; "
            + "PI → CATEGORIE_SANS_AVIS et, sans version validée, FICHE_NON_VALIDEE ; Membre → 403")
    void projetDesLaFiche() throws Exception {
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/projet").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("AVIS_PROJET_")));
        byte[] docx = mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/projet").param("format", "docx")
                .header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("_v1.docx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertThat(doc.getHeaderList()).as("le filigrane PROJET (en-tête Word)").isNotEmpty();
            assertThat(doc.getHeaderList().get(0)._getHdrFtr().xmlText()).contains("PROJET");
            assertThat(doc.getFooterList().get(0).getText()).contains("Projet — équivalence SIGMP en attente");
        }
        String texte = texteDuDocx(docx).replace(' ', ' ').replace(' ', ' ');
        assertThat(texte).contains("Fourniture de mobilier de bureau", "n°………").doesNotContain("{{");
        // Un projet n'est pas une publication : la ligne reste PREVU, rien au journal.
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.statut").value("PREVU"));
        assertThat(lancements()).isEmpty();

        // Produit à la validation, joint d'office au dossier sous le type AVIS_SPECIFIQUE ; pas dans la liste des documents de la version.
        // Le type de pièce de code AVIS_SPECIFIQUE : celui du référentiel (V95) s'il existe, sinon semé (le code est lu par findFirstByCode).
        TypePieceJointe ta = typePieceJointeRepository.findFirstByCode("AVIS_SPECIFIQUE").orElseGet(() -> {
            TypePieceJointe t = typePieceJointeRepository.findById(seedTypePiece("Projet d'avis spécifique (imprimé du SIGMP)", false, "DMC", 2))
                    .orElseThrow();
            t.setCode("AVIS_SPECIFIQUE");
            return typePieceJointeRepository.save(t);
        });
        int idDossier = creerDossier();
        assertThat(projetsJoints(idDossier, ta.getIdTypePiece())).hasSize(1)
                .allMatch(n -> n.startsWith("AVIS_PROJET_") && n.endsWith("_v1.pdf"));
        String liste = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(liste, "$[*].type")).doesNotContain("AVIS_PROJET").contains("DPAO");
        // La version suivante remplace le projet (dossier brouillon).
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        valider();
        assertThat(projetsJoints(idDossier, ta.getIdTypePiece())).hasSize(1).allMatch(n -> n.endsWith("_v2.pdf"));

        Long pi = creerDmc(9902);
        mvc.perform(get("/api/fiches-marche/" + pi + "/avis-specifique/projet").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AVIS_INDISPONIBLE"))
                .andExpect(jsonPath("$.details.raison").value("CATEGORIE_SANS_AVIS"));
        mvc.perform(get("/api/fiches-marche/" + pi + "/lettres-invitation/projet").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LETTRE_INDISPONIBLE"))
                .andExpect(jsonPath("$.details.raison").value("FICHE_NON_VALIDEE"));
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/projet").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("⚠️ R3c (manuel révisé, §B3) — les mentions du modèle type : AVIS-F complet au gabarit ; sur la fiche, le compte de "
            + "l'ARMP manque tant qu'il n'est pas réglé, LOTS_MAX sans objet (lot unique) ; PI → CATEGORIE_SANS_AVIS ; Membre → 403")
    void mentions() throws Exception {
        String m = mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/mentions").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gabarit").value("AVIS-F")).andExpect(jsonPath("$.gabaritComplet").value(true))
                .andExpect(jsonPath("$.documentComplet").value(false)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(m, "$.mentions[?(@.code=='COMPTE_PAIEMENT')].renseignee")).containsExactly(false);
        assertThat(JsonPath.<List<Boolean>>read(m, "$.mentions[?(@.code=='LOTS_MAX')].sansObjet")).containsExactly(true);
        assertThat(JsonPath.<List<String>>read(m, "$.mentions[?(@.code=='OBJET')].detail")).containsExactly((String) null);   // l'objet vient du plan

        mvc.perform(put("/api/parametres/compte-dao").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"banque\":\"BNI Madagascar\",\"titulaire\":\"ARMP\",\"numeroCompte\":\"00005 00001 12345678901 23\"}"))
                .andExpect(status().isOk());
        m = mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/mentions").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Boolean>>read(m, "$.mentions[?(@.code=='COMPTE_PAIEMENT')].renseignee")).containsExactly(true);

        Long pi = creerDmc(9902);
        mvc.perform(get("/api/fiches-marche/" + pi + "/avis-specifique/mentions").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.details.raison").value("CATEGORIE_SANS_AVIS"));
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/avis-specifique/mentions").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("⚠️ R6a (manuel révisé, lot 2, §B1 ; arbitrage Q6) — à l'impression, la date de publication saisie respecte le délai de "
            + "publicité de la loi : appel d'offres ouvert, date limite 15 jours après → 409 DELAI_PUBLICITE (requis 30, constaté 15, "
            + "départ, limite) ; 49 jours avant → imprimé")
    void delaiPublicite() throws Exception {
        // Le référentiel des fournitures (B04-LR-03), puis une révision validée avec la date limite voulue (sans prévision de lancement
        // au plan : la validation ne juge pas le délai, l'impression le fera contre la date saisie).
        assertThat(champService.importerCsv(new org.springframework.core.io.ClassPathResource(
                "fiche-marche/referentiel-champs-fiche-marche-fournitures.csv").getFile().toPath()).rejets()).isEmpty();
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        remplirObligatoiresEtValider(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", Map.of("B04-LR-03", "2026-04-20"));   // entre le lancement et l'ouverture générés (DATES_ORDRE)
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"datePublication\":\"2026-04-05\",\"jmpDate\":\"2026-01-15\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELAI_PUBLICITE")).andExpect(jsonPath("$.details.code").value("PUBLICITE_AOO_NATIONAL"))
                .andExpect(jsonPath("$.details.requis").value(30)).andExpect(jsonPath("$.details.constate").value(15))
                .andExpect(jsonPath("$.details.depart").value("2026-04-05")).andExpect(jsonPath("$.details.limite").value("2026-04-20"));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"datePublication\":\"2026-03-01\",\"jmpDate\":\"2026-01-15\"}")).andExpect(status().isCreated());
    }

    /** Les noms des pièces du dossier jointes sous ce type de pièce. */
    private List<String> projetsJoints(int idDossier, Integer idTypePiece) throws Exception {
        String pieces = mvc.perform(get("/api/piece-jointe-dossiers").param("dossier", String.valueOf(idDossier))
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(pieces, "$[?(@.idTypePiece==" + idTypePiece + ")].nomFichier");
    }

    @Test
    @DisplayName("Fiche révisée pendant la rectification (EN_ATTENTE_DECISION_PRMP) puis revalidée : après la levée des "
            + "réserves, l'avis se lit sur la dernière version VALIDÉE (v2) ; une révision n'est possible que dossier rendu "
            + "à la PRMP, jamais une fois les réserves levées")
    void ficheRevisee() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAVR", "EN_ATTENTE_DECISION_PRMP");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B02").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{}}")).andExpect(status().isOk());
        valider();
        statut(idDossier, "OBSERVATIONS_LEVEES");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DOSSIER_EN_EXAMEN"));

        String produit = imprimer(idDmc).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(produit, "$[*].version")).containsOnly(2);
        assertThat(JsonPath.<List<String>>read(produit, "$[*].nomFichier")).allMatch(n -> n.contains("_v2_"));
        String liste = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Map<String, String>>>read(liste, "$[?(@.type=='AVIS')].publication"))
                .hasSize(2).allMatch(p -> "123".equals(p.get("jmpNumero")));
        // La version 1 n'a pas d'avis.
        String v1 = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").param("version", "1")
                .header("Authorization", tokenPrmp)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(v1, "$[*].type")).doesNotContain("AVIS");
    }

    @Test
    @DisplayName("Statut « Lancé » (décision du 30/09, §B2) — la fiche laisse la ligne PREVU ; la première impression la passe "
            + "LANCE (journal LIGNE_LANCEE, avisImprimeLe) ; une réimpression ne change rien et n'écrit rien")
    void premiereImpressionLanceLaLigne() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PREVU")).andExpect(jsonPath("$.avisImprimeLe").isEmpty());

        imprimer(idDmc).andExpect(status().isCreated());
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("LANCE"))
                .andExpect(jsonPath("$.avisImprimeLe").value(java.time.LocalDate.now().toString()));
        assertThat(lancements()).containsExactly(
                "Ligne 9901 : avis spécifique imprimé (publication du 05/10/2026), statut PREVU → LANCE");

        imprimer(idDmc).andExpect(status().isCreated());
        assertThat(lancements()).hasSize(1);
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.statut").value("LANCE"));
    }

    @Test
    @DisplayName("Statut « Lancé » (§B2) — un statut manuel (CHDP) n'est pas écrasé ; l'impression reste possible, le journal "
            + "le dit")
    void statutManuelConserve() throws Exception {
        Marche l = marcheRepository.findById(9901).orElseThrow();
        l.setStatut("CHDP");
        marcheRepository.saveAndFlush(l);
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        imprimer(idDmc).andExpect(status().isCreated());
        mvc.perform(get("/api/marches/9901").header("Authorization", tokenPrmp)).andExpect(jsonPath("$.statut").value("CHDP"));
        assertThat(lancements()).containsExactly(
                "Ligne 9901 : avis spécifique imprimé (publication du 05/10/2026), statut CHDP conservé (statut manuel)");
    }

    @Test
    @DisplayName("⚠️ 18/10 (« dao-electronique », Q4) — dossier payant, compte de l'ARMP non réglé : indisponible, 409 "
            + "COMPTE_ARMP_ABSENT nommé ; le compte réglé, l'avis s'imprime ; un dossier gratuit ne l'exige pas")
    void compteArmpExigeSiPayant() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(true));   // gratuit : le compte n'est pas exigé
        String fiche = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int idFiche = JsonPath.read(fiche, "$.idFiche");
        valeurRepository.save(new cnm.prs.entity.FicheMarcheValeur(null, idFiche, "B04-DS-05", "150000"));
        disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(false)).andExpect(jsonPath("$.raison").value("COMPTE_ARMP_ABSENT"));
        imprimer(idDmc).andExpect(status().isConflict()).andExpect(jsonPath("$.details.raison").value("COMPTE_ARMP_ABSENT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("compte bancaire de l'ARMP")));
        long avant = notificationRepository.findAll().stream().filter(n -> "AVIS_A_IMPRIMER".equals(n.getTypeNotif())).count();
        mvc.perform(put("/api/parametres/compte-dao").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"banque\":\"BNI Madagascar\",\"titulaire\":\"ARMP\",\"numeroCompte\":\"00005 00001 12345678901 23\"}"))
                .andExpect(status().isOk());
        disponibilite(idDmc).andExpect(jsonPath("$.disponible").value(true));
        // Le réglage du compte rend l'avis imprimable : la PRMP en est prévenue (relevé avant/après, « avis-a-imprimer »).
        assertThat(notificationRepository.findAll()).filteredOn(n -> "AVIS_A_IMPRIMER".equals(n.getTypeNotif())
                && Integer.valueOf(idDossier).equals(n.getIdObjet())).isNotEmpty();
        assertThat(notificationRepository.findAll().stream().filter(n -> "AVIS_A_IMPRIMER".equals(n.getTypeNotif())).count())
                .isGreaterThan(avant);
        imprimer(idDmc).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("§B7.5 / §B8 (2026-10-01) — impression sans numéro de JMP ni supports : 201, pas de « et dans », pointillés ; "
            + "le compte bancaire de l'ARMP réglé par l'Administrateur est imprimé, des pointillés tant qu'il ne l'est pas")
    void publicationMinimaleEtCompte() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        // ⚠️ V118 (H1) — le numéro du journal se lit sur la publication du plan : sans numéro, des pointillés (celui du corps est ignoré).
        cnm.prs.entity.PublicationAgpm agpm = publicationAgpmRepository.findFirstByIdDossierAndRemplaceeLeIsNull(9900).orElseThrow();
        agpm.setNumero(null);
        publicationAgpmRepository.save(agpm);
        String minimal = "{\"datePublication\":\"2026-10-05\",\"jmpDate\":\"2026-01-15\"}";
        String r = mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenPrmp)
                .contentType(JSON).content(minimal)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String sansCompte = docxTexte(r);
        assertThat(sansCompte).contains("n°………", "compte bancaire de l’ARMP : ………").doesNotContain("et dans", "{{");

        mvc.perform(put("/api/parametres/compte-dao").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"banque\":\"BNI Madagascar\",\"titulaire\":\"ARMP\",\"numeroCompte\":\"00005 00001 12345678901 23\"}"))
                .andExpect(status().isOk());
        r = mvc.perform(post("/api/fiches-marche/" + idDmc + "/avis-specifique").header("Authorization", tokenPrmp)
                .contentType(JSON).content(minimal)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(docxTexte(r)).contains("BNI Madagascar, compte n° 00005 00001 12345678901 23 au nom de ARMP");
    }

    @Test
    @DisplayName("⚠️ R3a (manuel révisé, §B3) — le pied de chaque page porte l'équivalence SIGMP : « Projet — équivalence SIGMP en "
            + "attente » tant que la décision n'est pas réglée, puis « … en application de la décision n° … du … »")
    void piedSigmp() throws Exception {
        int idDossier = creerDossier();
        pvSigne(idDossier, "FAV", "PV_SIGNE");
        String r = imprimer(idDmc).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(piedDuDocx(r)).contains("fiche marché version 1", "Projet — équivalence SIGMP en attente");

        mvc.perform(put("/api/parametres/sigmp-transition").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"decisionReference\":\"12/CNM/2026\",\"decisionDate\":\"2026-10-01\"}")).andExpect(status().isOk());
        r = imprimer(idDmc).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(piedDuDocx(r)).contains("Document produit par la plateforme PRS 2.0 en application de la décision n° 12/CNM/2026 du 01/10/2026")
                .doesNotContain("en attente");
        // Le corps de l'avis ne change pas : le pied est hors du texte relu par le comparateur de fidélité.
        assertThat(docxTexte(r)).doesNotContain("équivalence SIGMP");
    }

    private String piedDuDocx(String produits) throws Exception {
        int id = JsonPath.<List<Integer>>read(produits, "$[?(@.extension=='docx')].idDocument").get(0);
        byte[] docx = mvc.perform(get("/api/fiches-marche/documents/" + id + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            return doc.getFooterList().stream().map(org.apache.poi.xwpf.usermodel.XWPFFooter::getText).reduce("", String::concat);
        }
    }

    private String docxTexte(String produits) throws Exception {
        int id = JsonPath.<List<Integer>>read(produits, "$[?(@.extension=='docx')].idDocument").get(0);
        return texteDuDocx(mvc.perform(get("/api/fiches-marche/documents/" + id + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).replace(' ', ' ').replace(' ', ' ');
    }

    // ------------------------------------------------------------------ outils

    /** Les détails du journal LIGNE_LANCEE du plan 9900. */
    private List<String> lancements() throws Exception {
        String journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(journal, "$[?(@.typeAction=='LIGNE_LANCEE')].detail");
    }

    private org.springframework.test.web.servlet.ResultActions disponibilite(Long dmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + dmc + "/avis-specifique/disponibilite").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions imprimer(Long dmc) throws Exception {
        return mvc.perform(post("/api/fiches-marche/" + dmc + "/avis-specifique").header("Authorization", tokenPrmp)
                .contentType(JSON).content(CORPS));
    }

    /**
     * ⚠️ 2026-10-22 (demande front « modeles-dao », D0 et relève) — sur une procédure à fiche validée : l'aperçu du fichier intégré AVIS-F,
     * puis un modèle de l'application rattaché à AVIS-F, au titre modifié, mis en vigueur avec la validation du juriste : l'aperçu de
     * sa version et l'avis rendu par la production (relecture des mentions) portent le nouveau titre ; l'archivage rend l'intégré.
     */
    @Test
    void releveDeLAvisParUnModeleDeDocument() throws Exception {
        byte[] integre = mvc.perform(post("/api/modeles-pv/integres/dao/AVIS-F/apercu").param("procedure", String.valueOf(idDmc))
                .header("Authorization", tokenAdmin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(texteDuPdf(integre)).contains("Avis d’Appel d’Offres Ouvert", "Fourniture de mobilier de bureau")
                .doesNotContain("{{", "modèle de l’application");

        String cree = mvc.perform(post("/api/modeles-pv").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"AVIS-F-APP\",\"libelle\":\"Avis\",\"nature\":\"DOCUMENT_DAO\"}")).andReturn().getResponse()
                .getContentAsString();
        long id = ((Number) JsonPath.read(cree, "$.id")).longValue();
        mvc.perform(put("/api/modeles-pv/" + id + "/rattachements").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sigles\":[\"AVIS-F\"]}")).andExpect(status().isOk());
        String v1 = mvc.perform(post("/api/modeles-pv/" + id + "/versions/depart").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String doc = new tools.jackson.databind.ObjectMapper().readTree(v1).path("document").toString()
                .replace("\"Avis d’Appel d’Offres Ouvert\"", "\"Avis d’Appel d’Offres Ouvert — modèle de l’application\"");
        mvc.perform(put("/api/modeles-pv/" + id + "/versions/1/document").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"document\":" + doc + "}")).andExpect(status().isOk());
        byte[] apercu = mvc.perform(post("/api/modeles-pv/" + id + "/versions/1/apercu").param("procedure", String.valueOf(idDmc))
                .header("Authorization", tokenAdmin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(texteDuPdf(apercu)).contains("Avis d’Appel d’Offres Ouvert — modèle de l’application", "Fourniture de mobilier de bureau");
        mvc.perform(post("/api/modeles-pv/" + id + "/versions/1/mettre-en-vigueur").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"titre\",\"juriste\":{\"nom\":\"RABE\",\"qualite\":\"Juriste\","
                        + "\"date\":\"2026-10-22\"}}")).andExpect(status().isOk());

        cnm.prs.dto.FicheMarcheDto etat = ficheMarcheService.etatValide(idDmc).orElseThrow().etat();
        String publie = documentsFicheMarcheService.rendrePublication(etat, "AVIS-F", false).elements().toString();
        assertThat(publie).contains("Avis d’Appel d’Offres Ouvert — modèle de l’application");
        mvc.perform(post("/api/modeles-pv/" + id + "/archiver").header("Authorization", tokenAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"retour\"}")).andExpect(status().isOk());
        assertThat(documentsFicheMarcheService.rendrePublication(etat, "AVIS-F", false).elements().toString())
                .doesNotContain("modèle de l’application").contains("Avis d’Appel d’Offres Ouvert");
    }

    private void valider() throws Exception {
        besoinDeTest(idDmc);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp)).andExpect(status().isOk());
    }

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private int creerDossier() throws Exception {
        String corps = mvc.perform(post("/api/fiches-marche/" + idDmc + "/dossier").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corps, "$.idDossier");
    }

    /** La chaîne réception → dispatch → examen → PV signé du dossier DAO, avec l'avis et le statut du dossier voulus. */
    private void pvSigne(int idDossier, String avis, String statutDossier) {
        receptionRepository.save(reception(9950, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(9950, 9950, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(9950, 9950, "CTRMEM"));
        seedPvSigne(9950, 9950);
        avis(idDossier, avis);
        statut(idDossier, statutDossier);
    }

    private void avis(int idDossier, String avis) {
        PvExamen pv = pvExamenRepository.findSignesParDossierRows(idDossier).get(0);
        pv.setIdAvis(avis);
        pvExamenRepository.save(pv);
    }

    private void statut(int idDossier, String statut) {
        Dossier d = dossierRepository.findById(idDossier).orElseThrow();
        d.setStatut(statut);
        dossierRepository.saveAndFlush(d);
    }

    private static String texteDuDocx(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }
}
