package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.service.ChampFicheMarcheService;
import cnm.prs.service.RemiseElectronique;

/**
 * ⚠️ <strong>La remise électronique des offres</strong> (demande front du 2026-09-27, V50, ADR-0010) — §B1 : types
 * {@code DATE_HEURE} / {@code URL}, cadrage {@code modeRemise}, défauts et valeurs calculées ({@code champsCalcules}) ;
 * §B2 : Données particulières et C1 en mode papier et électronique ; §B3 : règles 10 et 11 à la validation ; §B4 :
 * paramètres internes réservés au titulaire ; §B5 : rôle « Responsable de la procédure » ; §B1.4 : paramètres
 * administrables.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), lignes 9901 et 9902 à quantité fixe (fournitures), lancement
 * prévisionnel le 2026-03-02 ; référentiel des fournitures importé du CSV du front.</p>
 */
class RemiseElectroniqueIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheService champService;

    private String tokenVer;
    private String tokenAss;
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
        ligne(9901);
        ligne(9902);
        capmRepository.save(new Capm(9901, "Lancement de l'appel d'offres", 1, 92, null));
        marchePrevisionRepository.save(new MarchePrevision(9901, 9901, 9901, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), null, null));
        marchePrevisionRepository.save(new MarchePrevision(9902, 9902, 9901, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), null, null));
        // Les fournitures, puis les travaux (B04-CD y est défini pour les trois catégories), comme FicheBesoinIntegrationTest.
        for (String f : List.of("referentiel-champs-fiche-marche-fournitures.csv", "referentiel-champs-fiche-dao-travaux.csv")) {
            assertThat(champService.importerCsv(new ClassPathResource("fiche-marche/" + f).getFile().toPath()).rejets()).isEmpty();
        }
        tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", "ANT");
    }

    // ------------------------------------------------------------------ 1. types, cadrage, calculs, défauts

    @Test
    @DisplayName("1 — Référentiel V50 : B04-SE servi, B04-VE inactif ; défauts constants et « = paramètre » recopiés à la création ; "
            + "DATE_HEURE / URL → 400 nominatifs ; en mode électronique le serveur pose les valeurs calculées (champsCalcules), "
            + "en mode papier les champs B04-SE sont fermés et le reflet vaut Papier")
    void typesCadrageEtCalculs() throws Exception {
        String ref = mvc.perform(get("/api/champs-fiche-marche").param("typeMarche", "QUANTITE_FIXE")
                .param("categorie", "FOURNITURES_SERVICES").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(ref, "$.blocs[?(@.code=='B04')].rubriques[*].code")).contains("B04-SE", "B04-CD");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[*].code")).contains("B04-SE-01", "B04-SE-17", "B05-GS-14", "B04-OP-13")
                .doesNotContain("B04-VE-01", "B04-VE-02");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B04-SE-02')].type")).containsExactly("URL");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B04-LR-03')].controle"))
                .containsExactly("DATES_ORDRE:REMISE,SE_HEURE_LIMITE:DATE");

        mvc.perform(put("/api/parametres/fiche-remise-electronique").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"plateformeUrl\":\"https://depot.cnm.mg\",\"fuseau\":\"Indian/Antananarivo\",\"signatureMin\":\"Avancée\","
                        + "\"tailleMaxPlateformeMo\":500,\"delaiMinRemiseJours\":30,\"assistance\":\"Cellule e-procurement, 8h-16h\","
                        + "\"quorumDefaut\":\"3/5\"}")).andExpect(status().isOk());
        Long idDmc = creerDmc(9901);
        String fiche = fiche(idDmc);
        assertThat(JsonPath.<String>read(fiche, "$.parametresInternes")).isEqualTo("ABSENTS");
        assertThat(JsonPath.<Object>read(fiche, "$.responsableProcedure")).isNull();
        assertThat(JsonPath.<Boolean>read(fiche, "$.peutModifierParametresInternes")).isFalse();
        assertThat(JsonPath.<List<String>>read(fiche, "$.champsCalcules")).isEmpty();

        // Mode papier (aucune clé modeRemise) : le reflet vaut PAPIER, les champs B04-SE sont fermés (ignorés au PUT, hors bilan).
        cadrage(idDmc, "");
        fiche = fiche(idDmc);
        assertThat(JsonPath.<String>read(fiche, "$.valeursCadrage['B04-SE-01']")).isEqualTo("PAPIER");
        assertThat(JsonPath.<String>read(fiche, "$.valeurs['B04-SE-08']")).isEqualTo("50");   // défaut constant recopié à la création
        assertThat(JsonPath.<String>read(fiche, "$.valeurs['B04-SE-05']")).isEqualTo("Avancée");   // = paramètre FICHE_SE_SIGNATURE_MIN
        assertThat(JsonPath.<String>read(fiche, "$.valeurs['B04-SE-02']")).isEqualTo("https://depot.cnm.mg");   // = paramètre
        assertThat(JsonPath.<String>read(fiche, "$.valeurs['B04-SE-14']")).isEqualTo("Cellule e-procurement, 8h-16h");
        String papier = bloc(idDmc, "B04", "{\"B04-LR-03\":\"2026-04-10\",\"B04-LR-04\":\"10:00\",\"B04-SE-03\":\"n'importe quoi\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(papier, "$.bilanControles.bloquants[*].regle")).doesNotContain("SE_HEURE_LIMITE",
                "RESPONSABLE_NON_DESIGNE", "PARAMETRES_INTERNES_INCOMPLETS");
        assertThat(JsonPath.<List<String>>read(papier, "$.bilanControles.bloquants[?(@.regle=='OBLIGATOIRE')].champs[0]"))
                .doesNotContain("B04-SE-17", "B04-SE-14");
        assertThat(JsonPath.<List<String>>read(papier, "$.champsCalcules")).isEmpty();

        // Mode électronique : 400 nominatifs sur les nouveaux types.
        cadrage(idDmc, "\"modeRemise\":\"ELECTRONIQUE\",\"garantieSoumission\":\"OUI\"");
        bloc(idDmc, "B04", "{\"B04-SE-02\":\"ftp://depot.cnm.mg\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("B04-SE-02"))
                .andExpect(jsonPath("$.erreurs[0].message").value("« Adresse de la plateforme de dépôt » attend une adresse http ou https."));
        bloc(idDmc, "B04", "{\"B04-SE-03\":\"2026-13-01T10:00\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("B04-SE-03"))
                .andExpect(jsonPath("$.erreurs[0].message").value("« Date d'ouverture des dépôts » attend une date et une heure AAAA-MM-JJTHH:MM."));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"modeRemise\":\"MIXTE\"}}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("modeRemise"));

        // Le serveur pose les valeurs calculées du bloc B04 : publication (lancement du plan), dépôts, assistance, ouverture des plis.
        // (Le PUT remplace le bloc : les défauts recopiés à la création sont renvoyés, comme le fait l'écran.)
        String b04 = bloc(idDmc, "B04", "{\"B04-LR-02\":\"Lot II M 85 Bis\",\"B04-LR-03\":\"2026-04-10\",\"B04-LR-04\":\"10:00\","
                + "\"B04-OP-12\":60,\"B04-SE-02\":\"https://depot.cnm.mg\",\"B04-OP-02\":\"2026-04-20\",\"B04-OP-03\":\"09:00\","
                + "\"B04-SE-05\":\"Avancée\",\"B04-SE-08\":50,\"B04-SE-09\":500}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(b04, "$.valeursCadrage['B04-SE-01']")).isEqualTo("ELECTRONIQUE");
        assertThat(JsonPath.<List<String>>read(b04, "$.champsCalcules"))
                .containsExactly("B04-OP-02", "B04-OP-03", "B04-SE-03", "B04-SE-15", "B04-SE-17");
        assertThat(JsonPath.<String>read(b04, "$.valeurs['B04-SE-17']")).isEqualTo("2026-03-02T00:00");
        assertThat(JsonPath.<String>read(b04, "$.valeurs['B04-SE-03']")).isEqualTo("2026-03-02T00:00");
        assertThat(JsonPath.<String>read(b04, "$.valeurs['B04-SE-15']")).isEqualTo("2026-04-08T10:00");
        assertThat(JsonPath.<String>read(b04, "$.valeurs['B04-OP-02']")).isEqualTo("2026-04-10");   // Q11 : toujours recalculé
        assertThat(JsonPath.<String>read(b04, "$.valeurs['B04-OP-03']")).isEqualTo("11:00");
        assertThat(JsonPath.<List<String>>read(b04, "$.bilanControles.ok[*].regle")).contains("SE_HEURE_LIMITE", "SE_OUVERTURE_DEPOTS",
                "SE_OUVERTURE_PLIS", "SE_TAILLES", "SE_SIGNATURE_MIN");
        assertThat(JsonPath.<List<String>>read(b04, "$.bilanControles.bloquants[*].regle")).contains("RESPONSABLE_NON_DESIGNE",
                "PARAMETRES_INTERNES_INCOMPLETS", "SE_PRESTATAIRES");
        // Une valeur saisie différente de la valeur calculée reste une saisie ; renvoyée telle quelle, elle reste « calculée ».
        String b04bis = bloc(idDmc, "B04", "{\"B04-LR-02\":\"Lot II M 85 Bis\",\"B04-LR-03\":\"2026-04-10\",\"B04-LR-04\":\"10:00\","
                + "\"B04-OP-12\":60,\"B04-SE-02\":\"https://depot.cnm.mg\",\"B04-SE-17\":\"2026-03-02T00:00\",\"B04-SE-03\":\"2026-03-03T08:00\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(b04bis, "$.champsCalcules")).containsExactly("B04-OP-02", "B04-OP-03", "B04-SE-15", "B04-SE-17");
        assertThat(JsonPath.<String>read(b04bis, "$.valeurs['B04-SE-03']")).isEqualTo("2026-03-03T08:00");
        // Bloc B05 : le lieu et la date limite du dépôt de l'original se déduisent de la remise.
        String b05 = bloc(idDmc, "B05", "{\"B05-GS-11\":\"OUI\",\"B05-GS-03\":1600000,\"B05-GS-04\":105}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(b05, "$.champsCalcules")).contains("B05-GS-12", "B05-GS-14");
        assertThat(JsonPath.<String>read(b05, "$.valeurs['B05-GS-12']")).isEqualTo("Lot II M 85 Bis");
        assertThat(JsonPath.<String>read(b05, "$.valeurs['B05-GS-14']")).isEqualTo("2026-04-10T10:00");
        assertThat(JsonPath.<List<String>>read(b05, "$.bilanControles.ok[*].regle")).contains("SE_ORIGINAL_GARANTIE");
    }

    // ------------------------------------------------------------------ 2. validation et documents

    @Test
    @DisplayName("2 — Électronique : la validation est refusée sans responsable ni paramètres internes (messages exacts), puis "
            + "acceptée ; les Données particulières impriment « Électronique » et la plateforme, C1 porte la clause balisée ; "
            + "après validation les paramètres internes sont en lecture seule (FICHE_VALIDEE). Papier : validée sans rien, "
            + "la clause 7.3 du DPAO dit que la voie électronique n'est pas possible, pas de clause")
    void validationEtDocuments() throws Exception {
        Long idDmc = creerDmc(9901);
        cadrage(idDmc, "\"modeRemise\":\"ELECTRONIQUE\",\"garantieSoumission\":\"OUI\"");
        besoinDeTest(idDmc);
        Map<String, String> donnees = new LinkedHashMap<>();
        donnees.put("B02-OB-03", "AOO 0001/MESupReS/2026");
        donnees.put("B04-CD-02", "C1");
        donnees.put("B04-LR-03", "2026-04-10");
        donnees.put("B04-LR-04", "10:00");
        donnees.put("B04-SE-02", "https://depot.cnm.mg");
        donnees.put("B04-SE-05", "Avancée");
        donnees.put("B04-SE-06", "À définir par l'Administrateur (liste officielle des prestataires de certification)");
        donnees.put("B04-SE-17", "2026-03-02T08:00");
        donnees.put("B05-GS-03", "1600000");
        donnees.put("B05-GS-04", "105");
        donnees.put("B04-VO-01", "75");
        remplirObligatoires(idDmc, "QUANTITE_FIXE", "FOURNITURES_SERVICES", donnees);
        String fiche = fiche(idDmc);
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[*].regle"))
                .containsExactlyInAnyOrder("PARAMETRES_INTERNES_INCOMPLETS", "RESPONSABLE_NON_DESIGNE", "SE_DEPOSITAIRE");   // ⚠️ V66 : règle 12
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[*].message")).containsExactlyInAnyOrder(
                "Les paramètres internes de la procédure sont incomplets : à compléter par le responsable de la procédure.",
                "Aucun responsable de la procédure n'est désigné : la fiche ne peut pas être validée en remise électronique.",
                RemiseElectronique.MESSAGE_DEPOSITAIRE);
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[*].bloc")).containsOnly("B04");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTROLES_BLOQUANTS"));

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.im").value("CTRVER")).andExpect(jsonPath("$.nom").value("NomCTRVER Prenoms"));
        fiche = fiche(idDmc);
        assertThat(JsonPath.<String>read(fiche, "$.responsableProcedure.im")).isEqualTo("CTRVER");
        assertThat(JsonPath.<String>read(fiche, "$.parametresInternes")).isEqualTo("ABSENTS");
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.bloquants[*].regle")).containsExactlyInAnyOrder("PARAMETRES_INTERNES_INCOMPLETS", "SE_DEPOSITAIRE");
        String vue = mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenVer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Boolean>read(vue, "$.peutModifierParametresInternes")).isTrue();

        String internes = internes(idDmc, tokenVer, "{\"membresCommission\":[\"CTRMEM\",\"CTRCC1\"],\"quorum\":2,\"depositaire\":{\"nom\":\"Rakoto Jean\",\"organisme\":\"ARMP\"},"
                + "\"dateCeremonie\":\"2026-03-01T09:00\"}").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(internes, "$.etat")).isEqualTo("COMPLETS");
        assertThat(JsonPath.<Integer>read(internes, "$.nombreParts")).isEqualTo(2);
        assertThat(JsonPath.<List<String>>read(internes, "$.membresCommission[*].im")).containsExactly("CTRMEM", "CTRCC1");
        assertThat(JsonPath.<List<String>>read(internes, "$.membresCommission[*].profil")).containsExactly("MEMBRE", "CHEF_COMMISSION");
        assertThat(JsonPath.<String>read(internes, "$.responsable.im")).isEqualTo("CTRVER");
        assertThat(JsonPath.<List<Object>>read(internes, "$.anomalies")).isEmpty();
        assertThat(JsonPath.<List<String>>read(internes, "$.journal[*].champ"))
                .containsExactly("responsable", "membresCommission", "quorum", "dateCeremonie", "depositaire");   // ⚠️ V66
        assertThat(JsonPath.<List<String>>read(internes, "$.journal[*].nouvelleValeur"))
                .containsExactly("CTRVER", "CTRMEM,CTRCC1", "2", "2026-03-01T09:00", "Rakoto Jean ; ARMP");
        fiche = fiche(idDmc);
        assertThat(JsonPath.<String>read(fiche, "$.parametresInternes")).isEqualTo("COMPLETS");
        assertThat(JsonPath.<List<Object>>read(fiche, "$.bilanControles.bloquants")).isEmpty();
        assertThat(JsonPath.<List<String>>read(fiche, "$.bilanControles.ok[*].regle")).contains("SE_QUORUM", "SE_CEREMONIE",
                "PARAMETRES_INTERNES_INCOMPLETS", "RESPONSABLE_NON_DESIGNE", "SE_PRESTATAIRES");

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("VALIDEE"));
        String documents = mvc.perform(get("/api/fiches-marche/" + idDmc + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String dpao = texteDocx(contenu(documents, "DPAO", "docx"));
        // ⚠️ Lot D2 (2026-09-29) — le DPAO des fournitures est le document type rempli : la clause 7.3 porte la remise
        // électronique (clause du juriste et ses valeurs), plus la liste « libellé : valeur » du lot 2a.
        assertThat(dpao).contains("7.3. Remise des offres par voie électronique",
                "CLAUSE À FOURNIR PAR LE JURISTE : conditions et modalités de la remise électronique — plateforme (https://depot.cnm.mg)")
                .doesNotContain("n'est pas possible dans le cadre du présent Appel d'Offres", "INT-SE", "CTRMEM", "quorum", "{{");
        String c1 = texteDocx(contenu(documents, "C1", "docx"));
        assertThat(c1).contains("CLAUSE À FOURNIR PAR LE JURISTE : remise électronique",
                "Téléversement avec code de vérification (voie B)").doesNotContain("{{");
        internes(idDmc, tokenVer, "{\"membresCommission\":[\"CTRMEM\",\"CTRCC1\"],\"quorum\":2,\"dateCeremonie\":\"2026-03-01T09:00\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));

        // Papier : rien d'exigé, « Papier » imprimé, pas de clause dans C1.
        Long papier = creerDmc(9902);
        cadrage(papier, "\"garantieSoumission\":\"OUI\"");
        besoinDeTest(papier);
        Map<String, String> d2 = new LinkedHashMap<>();
        d2.put("B04-CD-02", "C1");
        d2.put("B04-LR-03", "2026-04-10");   // après le lancement prévisionnel du plan (02/03) : DATES_ORDRE
        d2.put("B04-OP-02", "2026-04-10");
        d2.put("B05-GS-03", "1600000");
        d2.put("B05-GS-04", "105");
        d2.put("B04-VO-01", "75");
        remplirObligatoiresEtValider(papier, "QUANTITE_FIXE", "FOURNITURES_SERVICES", d2);
        String docsPapier = mvc.perform(get("/api/fiches-marche/" + papier + "/documents").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(texteDocx(contenu(docsPapier, "DPAO", "docx"))).contains("Le mode de remise des offres par voie électronique n'est pas possible dans le cadre du présent Appel d'Offres.")   // lot D2
                .doesNotContain("plateforme", "CLAUSE À FOURNIR");
        assertThat(texteDocx(contenu(docsPapier, "C1", "docx"))).doesNotContain("CLAUSE À FOURNIR", "{{");
    }

    // ------------------------------------------------------------------ 3. droits

    @Test
    @DisplayName("3 — Paramètres internes : 403 pour PRMP, UGPM, Membre, Président, Chef de commission, Administrateur non titulaire "
            + "et responsable d'une autre procédure ; 200 pour le titulaire (GET, PUT, candidats) ; 404 DMC inconnu")
    void droits() throws Exception {
        Long idDmc = creerDmc(9901);
        Long autre = creerDmc(9902);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/fiches-marche/" + autre + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRASS\"}")).andExpect(status().isCreated());
        String corps = "{\"membresCommission\":[\"CTRMEM\"],\"quorum\":2,\"dateCeremonie\":\"2026-03-01T09:00\"}";
        for (String token : List.of(tokenPrmp, tokenUgpm, tokenMembre, tokenPresident, tokenCc, tokenAdmin, tokenAss)) {
            mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", token))
                    .andExpect(status().isForbidden());
            internes(idDmc, token, corps).andExpect(status().isForbidden());
            mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes/candidats").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("ABSENTS"))
                .andExpect(jsonPath("$.quorum").value(3))   // proposé depuis FICHE_SE_QUORUM_DEFAUT (3/5)
                .andExpect(jsonPath("$.anomalies", hasSize(2)));   // ⚠️ V66 : + le dépositaire (règle 12)
        internes(idDmc, tokenVer, corps).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("INCOMPLETS"))
                .andExpect(jsonPath("$.anomalies[*].message").value(hasItem("Au moins deux membres détenteurs d'une part de clé sont attendus.")));
        String candidats = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes/candidats").header("Authorization", tokenVer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(candidats, "$[*].im")).contains("CTRPRE", "CTRCC1", "CTRMEM")
                .doesNotContain("CTRVER", "CTRADM", "CTRSEC", "CTRCC2");
        mvc.perform(get("/api/fiches-marche/" + autre + "/parametres-internes").header("Authorization", tokenVer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/fiches-marche/999999/parametres-internes").header("Authorization", tokenVer))
                .andExpect(status().isNotFound());
        // 400 nominatifs du corps.
        internes(idDmc, tokenVer, "{\"membresCommission\":[\"INCONNU\"],\"quorum\":0,\"dateCeremonie\":\"hier\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[*].champ").value(org.hamcrest.Matchers.containsInAnyOrder("membresCommission", "quorum", "dateCeremonie")));
    }

    // ------------------------------------------------------------------ 4. rôle

    @Test
    @DisplayName("4 — Responsable : PRMP → 403 ; compte inconnu → 404 ; désignation 201 ; second → 409 RESPONSABLE_EXISTANT ; "
            + "titulaire dans la commission → 409 MEMBRE_COMMISSION dans les deux sens ; retrait 204 puis 404 ; candidats hors "
            + "membres ; journal dédié avec valeurs, journal global sans valeurs")
    void role() throws Exception {
        Long idDmc = creerDmc(9901);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"INCONNU\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRVER\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRPRE\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESPONSABLE_EXISTANT"));
        internes(idDmc, tokenVer, "{\"membresCommission\":[\"CTRVER\",\"CTRMEM\"],\"quorum\":2,\"dateCeremonie\":\"2026-03-01T09:00\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBRE_COMMISSION"));
        internes(idDmc, tokenVer, "{\"membresCommission\":[\"CTRMEM\",\"CTRCC1\"],\"quorum\":2,\"dateCeremonie\":\"2026-03-01T09:00\","
                + "\"depositaire\":{\"nom\":\"Rakoto Jean\"}}")   // ⚠️ V66 : le dépositaire, exigé par COMPLETS (règle 12)
                .andExpect(status().isOk());
        String journal = mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$.journal[*].champ"))
                .containsExactly("responsable", "membresCommission", "quorum", "dateCeremonie", "depositaire");
        assertThat(JsonPath.<String>read(journal, "$.journal[0].acteur")).isEqualTo("CTRADM");
        assertThat(JsonPath.<String>read(journal, "$.journal[1].acteur")).isEqualTo("CTRVER");
        assertThat(JsonPath.<String>read(journal, "$.journal[1].nouvelleValeur")).isEqualTo("CTRMEM,CTRCC1");

        String candidats = mvc.perform(get("/api/fiches-marche/" + idDmc + "/responsable/candidats").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(candidats, "$[*].im")).contains("CTRVER", "CTRPRE", "CTRADM", "CTRSEC")
                .doesNotContain("CTRMEM", "CTRCC1", "CTRCC2");
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/responsable/candidats").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", tokenVer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{\"im\":\"CTRMEM\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBRE_COMMISSION"));
        mvc.perform(delete("/api/fiches-marche/" + idDmc + "/responsable").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        String fiche = fiche(idDmc);
        assertThat(JsonPath.<Object>read(fiche, "$.responsableProcedure")).isNull();
        assertThat(JsonPath.<String>read(fiche, "$.parametresInternes")).isEqualTo("COMPLETS");

        // Journal global : la route et l'acteur, jamais les valeurs.
        List<cnm.prs.entity.AuditLog> audits = auditLogRepository.findAll().stream()
                .filter(a -> "fiches-marche".equals(a.getNomTable()) && String.valueOf(idDmc).equals(a.getIdEnregistrement())).toList();
        assertThat(audits).extracting(cnm.prs.entity.AuditLog::getTypeAction).contains("RESPONSABLE", "PARAMETRES-INTERNES");
        assertThat(audits).allSatisfy(a -> {
            assertThat(a.getNouvelleValeur()).isNull();
            assertThat(a.getAncienneValeur()).isNull();
        });
    }

    // ------------------------------------------------------------------ 5. paramètres administrables

    @Test
    @DisplayName("5 — GET /api/parametres/fiche-remise-electronique : défauts de V50 ; PUT réservé à l'Administrateur, 400 nominatif, "
            + "état complet, null efface")
    void parametres() throws Exception {
        mvc.perform(get("/api/parametres/fiche-remise-electronique").header("Authorization", tokenPrmp)).andExpect(status().isOk())
                .andExpect(jsonPath("$.fuseau").value("Indian/Antananarivo"))
                .andExpect(jsonPath("$.signatureMin").value("Avancée"))
                .andExpect(jsonPath("$.tailleMaxPlateformeMo").value(500))
                .andExpect(jsonPath("$.delaiMinRemiseJours").value(30))
                .andExpect(jsonPath("$.quorumDefaut").value("3/5"))
                .andExpect(jsonPath("$.plateformeUrl").isEmpty());
        String corps = "{\"plateformeUrl\":\"https://depot.cnm.mg\",\"fuseau\":\"Indian/Antananarivo\",\"signatureMin\":\"Simple\","
                + "\"tailleMaxPlateformeMo\":800,\"delaiMinRemiseJours\":21,\"assistance\":null,\"quorumDefaut\":\"2/3\"}";
        mvc.perform(put("/api/parametres/fiche-remise-electronique").header("Authorization", tokenPrmp).contentType(JSON).content(corps))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/parametres/fiche-remise-electronique").header("Authorization", tokenAdmin).contentType(JSON)
                .content(corps.replace("Simple", "Forte").replace("https://depot.cnm.mg", "depot.cnm.mg")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[*].champ").value(org.hamcrest.Matchers.containsInAnyOrder("plateformeUrl", "signatureMin")));
        mvc.perform(put("/api/parametres/fiche-remise-electronique").header("Authorization", tokenAdmin).contentType(JSON).content(corps))
                .andExpect(status().isOk()).andExpect(jsonPath("$.signatureMin").value("Simple"))
                .andExpect(jsonPath("$.tailleMaxPlateformeMo").value(800)).andExpect(jsonPath("$.delaiMinRemiseJours").value(21))
                .andExpect(jsonPath("$.plateformeUrl").value("https://depot.cnm.mg")).andExpect(jsonPath("$.quorumDefaut").value("2/3"));
        mvc.perform(put("/api/parametres/fiche-remise-electronique").header("Authorization", tokenAdmin).contentType(JSON)
                .content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.signatureMin").isEmpty())
                .andExpect(jsonPath("$.tailleMaxPlateformeMo").isEmpty());
    }

    // ------------------------------------------------------------------ outils

    private String fiche(Long idDmc) throws Exception {
        return mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions internes(Long idDmc, String token, String corps) throws Exception {
        return mvc.perform(put("/api/fiches-marche/" + idDmc + "/parametres-internes").header("Authorization", token)
                .contentType(JSON).content(corps));
    }

    private ResultActions bloc(Long idDmc, String bloc, String valeurs) throws Exception {
        return mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/" + bloc).header("Authorization", tokenPrmp)
                .contentType(JSON).content("{\"valeurs\":" + valeurs + "}"));
    }

    /** Le cadrage : les réponses données, plus le tronc commun. */
    private void cadrage(Long idDmc, String reponses) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{" + (reponses.isEmpty() ? "" : reponses + ",") + "\"alloti\":\"NON\",\"variantes\":\"NON\","
                        + "\"groupement\":\"NON\",\"provenance\":\"NATIONAL\",\"typePrix\":\"UNITAIRES\",\"prixRevisable\":\"NON\","
                        + "\"avance\":\"NON\",\"penalites\":\"CCAG\"}}"))
                .andExpect(status().isOk());
    }

    private byte[] contenu(String documents, String type, String extension) throws Exception {
        int id = JsonPath.<List<Integer>>read(documents, "$[?(@.type=='" + type + "' && @.extension=='" + extension + "')].idDocument").get(0);
        return mvc.perform(get("/api/fiches-marche/documents/" + id + "/contenu").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private static String texteDocx(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            return ex.getText();
        }
    }

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void ligne(int idDetail) {
        Marche l = marche(idDetail, 9900, 9900);
        l.setIdMode(92);
        l.setIdNature(natureFournitures());
        l.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        l.setDesignationMarche("Acquisition de matériels informatiques " + idDetail);
        marcheRepository.save(l);
    }
}
