package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Capm;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDmc;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.ChampFicheMarcheRepository;

/**
 * ⚠️ <strong>Fiche marché d'un appel d'offres, lot 1</strong> (demande front du 2026-09-22) — les douze cas de la
 * recette : référentiel, création du DMC par la PRMP sous H4, éligibles, cadrage et rubriques fermées, 400 nominatifs,
 * bilan et validation, révision, périmètre, filiation à travers les versions du plan, ordre des gardes, semis du
 * type DAO, montants en lettres.
 *
 * <p>Jeu : dossier 9900 (PRMP001, ANT, CLOTURE, PV signé FAV) avec la ligne 9901 (mode 92 « Appel d'offres ouvert »
 * → DAO), la ligne 9902 (mode 90 → BC) et la ligne 9904 (mode 93, non rattaché) ; dossier 9905 (EXAMINE, sans PV)
 * avec la ligne 9903. Champs de recette semés dans B04 et B05, liés aux règles par leur {@code controle}.</p>
 */
class FicheMarcheDaoIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private ChampFicheMarcheRepository champRepository;

    private String tokenUgpm;
    private String tokenPrmp2;
    private String tokenPrmp3;

    @BeforeEach
    void jeu() {
        // Types et modes : DAO semé par V35 ; BC créé ; 92 → DAO, 90 → BC, 93 → rien.
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        TypeDmc bc = typeDmcRepository.save(new TypeDmc(null, "BC", "Bon de Commande", true));
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m92);
        ModePassation m90 = new ModePassation(90, "Achat direct", null, null, null, null);
        m90.setIdTypeDmc(bc.getIdTypeDmc());
        modePassationRepository.save(m90);
        modePassationRepository.save(new ModePassation(93, "Gré à gré", null, null, null, null));

        // Plan signé (FAV) de PRMP001, clôturé.
        planSigne(9900, "PRMP001", "CLOTURE", "FAV");
        Marche l = marcheDao(9901, 9900, 9900);
        l.setIdMode(92);
        l.setMontEstim(new BigDecimal("8400000"));
        l.setFinancement("RPI");
        marcheRepository.save(l);
        Marche bcLigne = marcheDao(9902, 9900, 9900);
        bcLigne.setIdMode(90);
        marcheRepository.save(bcLigne);
        Marche nonRattachee = marcheDao(9904, 9900, 9900);
        nonRattachee.setIdMode(93);
        marcheRepository.save(nonRattachee);
        // Dates prévisionnelles de la ligne 9901 : lancement 2026-03-02, attribution 2026-06-15.
        capmRepository.save(new Capm(9901, "Lancement de l'appel d'offres", 1, 92, null));
        capmRepository.save(new Capm(9902, "Attribution du marché", 2, 92, null));
        marchePrevisionRepository.save(new MarchePrevision(9901, 9901, 9901, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), null, null));
        marchePrevisionRepository.save(new MarchePrevision(9902, 9901, 9902, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15), null, null));

        // Plan non signé de PRMP001.
        dossierRepository.save(dossierLoc(9905, "EXAMINE", "ANT", "PRMP001"));
        ppmRepository.save(ppm(9905, 9905, "PRMP001"));
        Marche sansPv = marcheDao(9903, 9905, 9905);
        sansPv.setIdMode(92);
        marcheRepository.save(sansPv);

        // Acteurs : UGPM de PRMP001, PRMP étrangère, PRMP sans mandat actif.
        ugpmRepository.save(ugpm("UGPM001", "PRMP001", "RAKOTO", "Hery"));
        tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", "ANT");
        prmpRepository.save(prmp("PRMP002", "TMS"));
        tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "TMS");
        Prmp sansMandat = prmp("PRMP003", "ANT");
        sansMandat.setDateNomin(LocalDate.of(2019, 1, 15));   // mandat implicite expiré depuis 2022
        prmpRepository.save(sansMandat);
        tokenPrmp3 = bearer("PRMP003", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP003", "ANT");

        // Champs de recette (saisie), liés aux règles par leur contrôle.
        champ("B04-LR-02", "Date limite de remise des offres", "DATE", true, null, "DATES_ORDRE:REMISE");
        champ("B04-OP-02", "Date d'ouverture des plis", "DATE", false, null, "DATES_ORDRE:OUVERTURE");
        champ("B04-VO-01", "Validité des offres (jours)", "NOMBRE", true, null, "VALIDITE_GARANTIE_SUP_OFFRE:OFFRE");
        champ("B05-GS-02", "Montant de la garantie de soumission (Ariary)", "MONTANT", true, "garantieSoumission = OUI", null);
        champ("B05-GS-03", "Validité de la garantie de soumission (jours)", "NOMBRE", true, "garantieSoumission = OUI",
                "VALIDITE_GARANTIE_SUP_OFFRE:GARANTIE");
        champ("B02-AU-01", "Autorité contractante (précisions)", "TEXTE", false, null, null);
    }

    // ------------------------------------------------------------------ 1. référentiel

    @Test
    @DisplayName("1 — Référentiel : QUANTITE_FIXE sert 9 blocs (B01-B06, B08-B10) avec leurs rubriques et les champs "
            + "chargés ; CONTRAT_CADRE ajoute B07 ; type inconnu → 400")
    void referentiel() throws Exception {
        String corps = mvc.perform(get("/api/champs-fiche-marche").param("typeMarche", "QUANTITE_FIXE").param("categorie", "FOURNITURES_SERVICES")
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocs", hasSize(9)))
                .andExpect(jsonPath("$.blocs[0].code").value("B01"))
                .andExpect(jsonPath("$.blocs[6].code").value("B08"))
                .andExpect(jsonPath("$.blocs[4].rubriques[?(@.code=='B05-GS')].nbAttendu").value(6))
                .andReturn().getResponse().getContentAsString();
        List<String> codes = JsonPath.read(corps, "$.champs[*].code");
        assertThat(codes).contains("B01-AC-01", "B01-AC-18", "B02-LV-05", "B05-GS-02", "B04-LR-02");
        assertThat(codes).doesNotContain("B07-XX-01");
        assertThat(JsonPath.<List<String>>read(corps, "$.champs[?(@.code=='B01-AC-14')].source")).containsExactly("PPM");
        assertThat(JsonPath.<List<String>>read(corps, "$.champs[?(@.code=='B02-LV-05')].condition")).containsExactly("alloti = OUI");

        mvc.perform(get("/api/champs-fiche-marche").param("typeMarche", "CONTRAT_CADRE").param("categorie", "FOURNITURES_SERVICES").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocs", hasSize(10)))
                .andExpect(jsonPath("$.blocs[6].code").value("B07"));
        mvc.perform(get("/api/champs-fiche-marche").param("typeMarche", "INCONNU").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("typeMarche"));

        // Administration : POST (Admin) crée un champ, PUT le modifie, la PRMP est refusée.
        String nouveau = "{\"code\":\"B05-GS-07\",\"libelle\":\"Banque émettrice\",\"type\":\"TEXTE\",\"source\":\"SAISIE\","
                + "\"documentMaitre\":\"DPAO\",\"condition\":\"garantieSoumission = OUI\"}";
        mvc.perform(post("/api/champs-fiche-marche").header("Authorization", tokenPrmp).contentType(JSON).content(nouveau))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/champs-fiche-marche").header("Authorization", tokenAdmin).contentType(JSON).content(nouveau))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rubrique").value("B05-GS"))
                .andExpect(jsonPath("$.bloc").value("B05"))
                .andExpect(jsonPath("$.rang").value(7));
        mvc.perform(put("/api/champs-fiche-marche/B05-GS-07").header("Authorization", tokenAdmin).contentType(JSON)
                .content(nouveau.replace("Banque émettrice", "Banque émettrice de la garantie").replace("\"condition\":\"garantieSoumission = OUI\"", "\"condition\":\"garantieSoumission OUI\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("condition"));
        mvc.perform(post("/api/champs-fiche-marche").header("Authorization", tokenAdmin).contentType(JSON)
                .content(nouveau.replace("B05-GS-07", "B99-ZZ-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("code"));
    }

    // ------------------------------------------------------------------ 2. création par la PRMP

    @Test
    @DisplayName("2 — PRMP crée le DMC d'une ligne éligible → 201 avec 22 valeursPpm ; PPM non signé → 409 PV_NON_SIGNE ; "
            + "mode BC → 409 MODE_NON_DAO ; seconde création → 409 DAO_EXISTANT ; UGPM → 201 ; autre PRMP → 403")
    void creationParLaPrmp() throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeDmcCode").value("DAO"))
                .andExpect(jsonPath("$.versionPpm").value(0))
                .andExpect(jsonPath("$.valeursPpm.B01-AC-14").value("8 400 000"))
                .andExpect(jsonPath("$.valeursPpm.B01-AC-13").value("Appel d'offres ouvert"))
                .andExpect(jsonPath("$.valeursPpm.B01-AC-05").value("NOM Prenoms"))
                .andExpect(jsonPath("$.valeursPpm.B01-AC-11").value("DOS-9900"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.Map<String, Object>>read(corps, "$.valeursPpm")).hasSize(23);   // lot 1c : + B01-AC-19 « Forme du marché »

        mvc.perform(post("/api/dmcs/par-marche/9903").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PV_NON_SIGNE"));
        mvc.perform(post("/api/dmcs/par-marche/9902").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODE_NON_DAO"))
                .andExpect(jsonPath("$.message", containsString("n'est pas un appel d'offres")));
        mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAO_EXISTANT"));
        mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dmcs/par-marche/999999").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());

        // UGPM de la PRMP, sur une seconde ligne éligible.
        Marche autre = marcheDao(9906, 9900, 9900);
        autre.setIdMode(92);
        marcheRepository.save(autre);
        mvc.perform(post("/api/dmcs/par-marche/9906").header("Authorization", tokenUgpm))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.typeDmcCode").value("DAO"));
    }

    // ------------------------------------------------------------------ 3. éligibles

    @Test
    @DisplayName("3 — eligibles : seules les lignes H4 du périmètre, dejaDao juste ; un contrôleur n'en a aucune")
    void eligibles() throws Exception {
        mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDetail").value(9901))
                .andExpect(jsonPath("$[0].refeDossier").value("DOS-9900"))
                .andExpect(jsonPath("$[0].libelleMode").value("Appel d'offres ouvert"))
                .andExpect(jsonPath("$[0].dejaDao").value(false))
                .andExpect(jsonPath("$[0].idDmc").doesNotExist());
        Long idDmc = creerDmc(tokenPrmp, 9901);
        mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].dejaDao").value(true))
                .andExpect(jsonPath("$[0].idDmc").value(idDmc));
        mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.idDetail==9901)]", hasSize(1)));
    }

    // ------------------------------------------------------------------ 4. cadrage et rubriques fermées

    @Test
    @DisplayName("4 — Cadrage garantieSoumission = NON : les champs GS sont ignorés au PUT et absents du bilan ; = OUI : "
            + "obligatoires bloquants ; fiche virtuelle avant le premier PUT ; clé inconnue → 400, typeMarche ignoré (lot 1c)")
    void cadrageEtRubriquesFermees() throws Exception {
        Long idDmc = creerDmc(tokenPrmp, 9901);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idFiche").doesNotExist())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.idDetailCourant").value(9901))
                .andExpect(jsonPath("$.ligneSupprimee").value(false))
                .andExpect(jsonPath("$.valeursPpm.B01-AC-14").value("8 400 000"))
                .andExpect(jsonPath("$.valeursPpm.B02-OB-01").value("Marche 9901"));

        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"typeMarche\":\"A_COMMANDE\"}}"))
                .andExpect(status().isOk())   // lot 1c : la clé est ignorée, le type vient du plan
                .andExpect(jsonPath("$.typeMarche").value("QUANTITE_FIXE"))
                .andExpect(jsonPath("$.cadrage.typeMarche").doesNotExist());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"inconnue\":\"OUI\",\"alloti\":\"PEUT-ETRE\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='inconnue')]", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[?(@.champ=='alloti')]", hasSize(1)));

        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"typeMarche\":\"QUANTITE_FIXE\",\"garantieSoumission\":\"NON\",\"alloti\":\"OUI\",\"nbLots\":3}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idFiche").isNumber())
                .andExpect(jsonPath("$.cadrage.garantieSoumission").value("NON"))
                .andExpect(jsonPath("$.cadrage.nbLots").value(3))
                .andExpect(jsonPath("$.valeursCadrage.B05-GS-01").value("NON"))
                .andExpect(jsonPath("$.valeursCadrage.B02-LV-05").value("3"))
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.champs[0]=='B05-GS-02')]", hasSize(0)));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B05-GS-02\":8400000}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeurs.B05-GS-02").doesNotExist())
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.champs[0]=='B05-GS-02')]", hasSize(0)));

        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"OUI\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cadrage.nbLots").doesNotExist())
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.regle=='OBLIGATOIRE' && @.champs[0]=='B05-GS-02')]", hasSize(1)))
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.regle=='OBLIGATOIRE' && @.champs[0]=='B05-GS-03')]", hasSize(1)))
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.regle=='OBLIGATOIRE' && @.champs[0]=='B02-AU-01')]", hasSize(0)));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B05-GS-02\":8400000,\"B05-GS-03\":120}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeurs.B05-GS-02").value("8400000"))
                .andExpect(jsonPath("$.enLettres.B05-GS-02").value("huit millions quatre cent mille ariary"))
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.champs[0]=='B05-GS-02')]", hasSize(0)))
                .andExpect(jsonPath("$.bilanControles.nbSaisis").value(2));
    }

    // ------------------------------------------------------------------ 5. 400 nominatifs

    @Test
    @DisplayName("5 — PUT blocs/B05 : MONTANT négatif → 400 { champ: B05-GS-02 } ; DATE mal formée → 400 ; champ PPM "
            + "→ 400 ; champ d'un autre bloc → 400 ; bloc inconnu → 404 ; contrôleur → 403")
    void quatreCentsNominatifs() throws Exception {
        Long idDmc = creerDmc(tokenPrmp, 9901);
        cadrage(idDmc, "{\"garantieSoumission\":\"OUI\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B05-GS-02\":-5,\"B05-GS-03\":\"cent\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs", hasSize(2)))
                .andExpect(jsonPath("$.erreurs[?(@.champ=='B05-GS-02')].message").value(hasItem(containsString("négatif"))))
                .andExpect(jsonPath("$.erreurs[?(@.champ=='B05-GS-03')].message").value(hasItem(containsString("nombre"))));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B04").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B04-LR-02\":\"2026-13-45\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("B04-LR-02"));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B01").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B01-AC-14\":1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].message", containsString("repris du PPM")));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B04").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B05-GS-02\":1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].message", containsString("n'appartient pas au bloc B04")));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B42").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{}}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenCc).contentType(JSON)
                .content("{\"valeurs\":{\"B05-GS-02\":1}}"))
                .andExpect(status().isForbidden());
        // Rien de tout cela n'a écrit.
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bilanControles.nbSaisis").value(0));
    }

    // ------------------------------------------------------------------ 6. bilan et validation

    @Test
    @DisplayName("6 — Bilan : VALIDITE_GARANTIE_SUP_OFFRE et DATES_ORDRE détectés ; valider avec un bloquant → 409 "
            + "CONTROLES_BLOQUANTS ; sans → 200 VALIDEE version 1, journal +1 FICHE_MARCHE_VALIDEE ; UGPM → 403")
    void bilanEtValidation() throws Exception {
        Long idDmc = creerDmc(tokenPrmp, 9901);
        cadrage(idDmc, "{\"garantieSoumission\":\"OUI\"}");
        bloc(idDmc, "B05", "{\"B05-GS-02\":8400000,\"B05-GS-03\":60}");
        // Remise des offres AVANT le lancement du PPM (2026-03-02) ; validité des offres 90 > garantie 60.
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B04").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B04-LR-02\":\"2026-02-15\",\"B04-OP-02\":\"2026-04-20\",\"B04-VO-01\":90}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.regle=='VALIDITE_GARANTIE_SUP_OFFRE')]", hasSize(1)))
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.regle=='DATES_ORDRE')]", hasSize(1)))
                .andExpect(jsonPath("$.bilanControles.bloquants[?(@.regle=='DATES_ORDRE')].message").value(hasItem(containsString("remise des offres"))))
                .andExpect(jsonPath("$.bilanControles.ok[?(@.regle=='MONTANT_POSITIF')]", hasSize(1)));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/controler").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bloquants", hasSize(2)));
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTROLES_BLOQUANTS"));

        bloc(idDmc, "B04", "{\"B04-LR-02\":\"2026-04-10\",\"B04-OP-02\":\"2026-04-20\",\"B04-VO-01\":90}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B05-GS-02\":8400000,\"B05-GS-03\":120}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bilanControles.bloquants", hasSize(0)))
                .andExpect(jsonPath("$.bilanControles.ok[?(@.regle=='DATES_ORDRE')].message").value(hasItem(containsString("lancement < remise des offres < ouverture des plis < attribution"))))
                .andExpect(jsonPath("$.bilanControles.ok[?(@.regle=='VALIDITE_GARANTIE_SUP_OFFRE')]", hasSize(1)));

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenUgpm))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.validePar").value("PRMP001"))
                .andExpect(jsonPath("$.dateValidation").isNotEmpty());
        String journal = mvc.perform(get("/api/dossiers/9900/journal").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(journal, "$[?(@.typeAction=='FICHE_MARCHE_VALIDEE')].detail"))
                .containsExactly("DAO, version 1, 5 information(s)");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));
    }

    // ------------------------------------------------------------------ 7. révision

    @Test
    @DisplayName("7 — reviser → version 2 BROUILLON (copie) ; version 1 lisible dans versions ; PUT sur VALIDEE → 409 ; "
            + "reviser sans validation → 409 ; valider une fiche jamais écrite → 409 FICHE_VIDE")
    void revision() throws Exception {
        Long idDmc = creerDmc(tokenPrmp, 9901);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_VIDE"));
        cadrage(idDmc, "{\"garantieSoumission\":\"NON\"}");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BROUILLON_EN_COURS"));
        bloc(idDmc, "B04", "{\"B04-LR-02\":\"2026-04-10\",\"B04-VO-01\":90}");
        bloc(idDmc, "B02", "{\"B02-AU-01\":\"Direction des achats\"}");
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));

        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B02").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B02-AU-01\":\"Autre\"}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"OUI\"}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FICHE_VALIDEE"));

        mvc.perform(post("/api/fiches-marche/" + idDmc + "/reviser").header("Authorization", tokenUgpm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.cadrage.garantieSoumission").value("NON"))
                .andExpect(jsonPath("$.valeurs.B02-AU-01").value("Direction des achats"));
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/versions").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].statut").value("VALIDEE"))
                .andExpect(jsonPath("$[0].nbValeurs").value(3));
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/versions/1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.valeurs.B02-AU-01").value("Direction des achats"));
        bloc(idDmc, "B02", "{\"B02-AU-01\":\"Autre\"}");
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/versions/1").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valeurs.B02-AU-01").value("Direction des achats"));
    }

    // ------------------------------------------------------------------ 8. périmètre

    @Test
    @DisplayName("8 — Contrôleur de la localité : GET 200 ; PRMP d'un autre périmètre : 403 ; DMC inexistant : 404 ; "
            + "DMC d'un autre type : 409 DMC_NON_DAO ; anonyme : 401")
    void perimetre() throws Exception {
        Long idDmc = creerDmc(tokenPrmp, 9901);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenCc)).andExpect(status().isOk());
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPresident)).andExpect(status().isOk());
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenUgpm)).andExpect(status().isOk());
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp2)).andExpect(status().isForbidden());
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/versions").header("Authorization", tokenPrmp2)).andExpect(status().isForbidden());
        mvc.perform(get("/api/fiches-marche/999999").header("Authorization", tokenPrmp)).andExpect(status().isNotFound());
        mvc.perform(get("/api/fiches-marche/" + idDmc)).andExpect(status().isUnauthorized());

        Long idBc = creerDmc(tokenAdmin, 9902);
        mvc.perform(get("/api/fiches-marche/" + idBc).header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DMC_NON_DAO"));
    }

    // ------------------------------------------------------------------ 9. filiation

    @Test
    @DisplayName("9 — Filiation : DMC sur L (v1) ; plan signé en v2 où L' change de montant → GET sert versionPpm 2, "
            + "idDetailCourant L', le nouveau montant ; L' dejaDao dans eligibles ; POST sur L → 409 VERSION_DEPASSEE ; "
            + "L' supprimée → ligneSupprimee true, GET 200")
    void filiation() throws Exception {
        Long idDmc = creerDmc(tokenPrmp, 9901);
        // Version 2 signée : dossier 9910 enfant de 9900, qui passe REMPLACE ; L' = 9911 d'origine 9901.
        planSigne(9910, "PRMP001", "CLOTURE", "FAV");
        Dossier v2 = dossierRepository.findById(9910).orElseThrow();
        v2.setIdDossierParent(9900);
        dossierRepository.save(v2);
        Ppm ppm2 = ppmRepository.findById(9910).orElseThrow();
        ppm2.setNumMaj(2);
        ppmRepository.save(ppm2);
        Dossier v1 = dossierRepository.findById(9900).orElseThrow();
        v1.setStatut("REMPLACE");
        dossierRepository.save(v1);
        Marche lPrime = marcheDao(9911, 9910, 9910);
        lPrime.setIdMode(92);
        lPrime.setIdLigneOrigine(9901);
        lPrime.setMontEstim(new BigDecimal("9100000"));
        marcheRepository.save(lPrime);

        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDetail").value(9901))
                .andExpect(jsonPath("$.idDetailCourant").value(9911))
                .andExpect(jsonPath("$.ligneSupprimee").value(false))
                .andExpect(jsonPath("$.versionPpm").value(2))
                .andExpect(jsonPath("$.refeDossier").value("DOS-9910"))
                .andExpect(jsonPath("$.valeursPpm.B01-AC-14").value("9 100 000"))
                .andExpect(jsonPath("$.valeursPpm.B01-AC-10").value("2"));
        mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idDetail").value(9911))
                .andExpect(jsonPath("$[0].dejaDao").value(true))
                .andExpect(jsonPath("$[0].idDmc").value(idDmc));
        mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_DEPASSEE"));
        mvc.perform(post("/api/dmcs/par-marche/9911").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAO_EXISTANT"));

        lPrime.setSupprimee(true);
        marcheRepository.save(lPrime);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDetailCourant").value(9911))
                .andExpect(jsonPath("$.ligneSupprimee").value(true));
    }

    // ------------------------------------------------------------------ 10. ordre des gardes

    @Test
    @DisplayName("10 — PRMP étrangère sur une ligne qui porte déjà un DAO → 403 (jamais 409) ; PRMP propriétaire sans "
            + "mandat actif → 409 VACANCE_PRMP sur POST par-marche et PUT blocs/B02")
    void ordreDesGardes() throws Exception {
        creerDmc(tokenPrmp, 9901);
        mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());

        planSigne(9920, "PRMP003", "CLOTURE", "FAV");
        Marche l = marcheDao(9921, 9920, 9920);
        l.setIdMode(92);
        marcheRepository.save(l);
        mvc.perform(post("/api/dmcs/par-marche/9921").header("Authorization", tokenPrmp3))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VACANCE_PRMP"));
        DossierMec dmc = new DossierMec();
        dmc.setIdDetail(9921);
        dmc.setIdTypeDmc(typeDmcRepository.findByCode("DAO").orElseThrow().getIdTypeDmc());
        dmc.setStatut(StatutDmc.A_PREPARER);
        dmc.setDateCreation(java.time.LocalDateTime.of(2026, 9, 1, 8, 0));
        Long idDmc = dossierMecRepository.save(dmc).getIdDmc();
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B02").header("Authorization", tokenPrmp3).contentType(JSON)
                .content("{\"valeurs\":{\"B02-AU-01\":\"x\"}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VACANCE_PRMP"));
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp3)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 11. base vierge

    @Test
    @DisplayName("11 — Base migrée : t_type_dmc contient DAO ; mode rattaché à aucun type → 409 nominatif qui nomme "
            + "l'Administrateur")
    void semisDuTypeDao() throws Exception {
        assertThat(typeDmcRepository.findByCode("DAO")).isPresent().get()
                .extracting(TypeDmc::getLibelle).isEqualTo("Dossier d'Appel d'Offres");
        mvc.perform(post("/api/dmcs/par-marche/9904").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODE_NON_DAO"))
                .andExpect(jsonPath("$.message", containsString("Gré à gré")))
                .andExpect(jsonPath("$.message", containsString("l'Administrateur")));
    }

    // ------------------------------------------------------------------ 12. enLettres (paliers : MontantEnLettresTest)

    @Test
    @DisplayName("12 — enLettres : 1 200 000 000 → « un milliard deux cents millions ariary » sur la fiche")
    void enLettres() throws Exception {
        Long idDmc = creerDmc(tokenPrmp, 9901);
        cadrage(idDmc, "{\"garantieSoumission\":\"OUI\"}");
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/B05").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":{\"B05-GS-02\":1200000000}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enLettres.B05-GS-02").value("un milliard deux cents millions ariary"));
    }

    // ------------------------------------------------------------------ outils

    /** Dossier de planification au PV signé de l'avis donné, avec son PPM, sa réception, son dispatch et son examen. */
    private void planSigne(int id, String idPrmp, String statut, String avis) {
        dossierRepository.save(dossierLoc(id, statut, "ANT", idPrmp));
        Dossier d = dossierRepository.findById(id).orElseThrow();
        d.setIdEntiteContract(1);
        dossierRepository.save(d);
        ppmRepository.save(ppm(id, id, idPrmp));
        receptionRepository.save(reception(id, id, "CTRCC1", true));
        dispatchRepository.save(dispatch(id, id, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(id, id, "CTRMEM"));
        seedPvSigne(id, id);
        if (!"FAV".equals(avis)) {
            PvExamen pv = pvExamenRepository.findById(id).orElseThrow();
            pv.setIdAvis(avis);
            pvExamenRepository.save(pv);
        }
    }

    private void champ(String code, String libelle, String type, boolean obligatoire, String condition, String controle) {
        ChampFicheMarche c = new ChampFicheMarche();
        c.setCode(code);
        c.setCodeRubrique(code.substring(0, code.lastIndexOf('-')));
        c.setRang(Integer.parseInt(code.substring(code.lastIndexOf('-') + 1)));
        c.setLibelle(libelle);
        c.setType(type);
        c.setSource("SAISIE");
        c.setDocumentMaitre("DPAO");
        c.setTypesMarche("QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE");
        c.setObligatoire(obligatoire);
        c.setCondition(condition);
        c.setControle(controle);
        c.setActif(true);
        champRepository.save(c);
    }

    private Long creerDmc(String token, int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void cadrage(Long idDmc, String cadrage) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":" + cadrage + "}")).andExpect(status().isOk());
    }

    private void bloc(Long idDmc, String bloc, String valeurs) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/blocs/" + bloc).header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"valeurs\":" + valeurs + "}")).andExpect(status().isOk());
    }
}
