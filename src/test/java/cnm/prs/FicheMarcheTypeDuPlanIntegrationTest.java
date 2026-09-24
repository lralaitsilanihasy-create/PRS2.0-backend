package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.TypeDmc;
import cnm.prs.enums.FormeMarche;

/**
 * ⚠️ <strong>Le type de marché se déduit du plan, lot 1c</strong> (demande front du 2026-09-23) — les six cas de la
 * recette : type dérivé et clé de cadrage ignorée, forme non outillée (création refusée, ligne listée désactivée), ligne
 * sans forme, 23e information reprise, filiation qui change la forme ({@code typeChange}), fiches validées inchangées.
 *
 * <p>Jeu : plan 9900 (PRMP001, ANT, CLOTURE, PV signé FAV), lignes en appel d'offres ouvert (mode 92 → DAO) : 9901 à
 * quantité fixe, 9902 contrat-cadre, 9903 à commande ; plan 9905 non signé avec la ligne 9904 contrat-cadre.</p>
 */
class FicheMarcheTypeDuPlanIntegrationTest extends CnmIntegrationTestSupport {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @BeforeEach
    void jeu() {
        TypeDmc dao = typeDmcRepository.findByCode("DAO").orElseThrow();
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(dao.getIdTypeDmc());
        modePassationRepository.save(m92);

        planSigne(9900, "PRMP001");
        ligne(9901, 9900, FormeMarche.QUANTITE_FIXE);
        ligne(9902, 9900, FormeMarche.CONTRAT_CADRE);
        ligne(9903, 9900, FormeMarche.A_COMMANDE);

        dossierRepository.save(dossierLoc(9905, "EXAMINE", "ANT", "PRMP001"));
        ppmRepository.save(ppm(9905, 9905, "PRMP001"));
        ligne(9904, 9905, FormeMarche.CONTRAT_CADRE);
    }

    // ------------------------------------------------------------------ 1. type dérivé, cadrage sans typeMarche

    @Test
    @DisplayName("1 — Ligne QUANTITE_FIXE : typeMarche dérivé ; un PUT cadrage qui envoie typeMarche passe (clé ignorée) ; "
            + "un cadrage enregistré avant le lot 1c est servi sans sa clé typeMarche")
    void typeDeriveEtCleIgnoree() throws Exception {
        Long idDmc = creerDmc(9901);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeMarche").value("QUANTITE_FIXE"))
                .andExpect(jsonPath("$.typeChange").value(false));

        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"typeMarche\":\"CONTRAT_CADRE\",\"garantieSoumission\":\"NON\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeMarche").value("QUANTITE_FIXE"))
                .andExpect(jsonPath("$.typeChange").value(false))
                .andExpect(jsonPath("$.cadrage.garantieSoumission").value("NON"))
                .andExpect(jsonPath("$.cadrage.typeMarche").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("select \"CADRAGE\" from t_fiche_marche where \"ID_DMC\" = ?",
                String.class, idDmc)).doesNotContain("typeMarche");

        // Cadrage d'avant le lot 1c : la clé est retirée à la lecture.
        entityManager.flush();
        jdbcTemplate.update("update t_fiche_marche set \"CADRAGE\" = ? where \"ID_DMC\" = ?",
                "{\"typeMarche\":\"QUANTITE_FIXE\",\"garantieSoumission\":\"NON\"}", idDmc);
        entityManager.clear();
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cadrage.typeMarche").doesNotExist())
                .andExpect(jsonPath("$.cadrage.garantieSoumission").value("NON"));
    }

    // ------------------------------------------------------------------ 2. forme non outillée

    @Test
    @DisplayName("2 — ⚠️ Lots 3 et 4 : les trois formes sont outillées — contrat-cadre et à commande → 201 ; eligibles les "
            + "liste avec leur forme et formeOutillee = true ; plan non signé : PV_NON_SIGNE, plus FORME_NON_OUTILLEE")
    void formesOuvertes() throws Exception {
        mvc.perform(post("/api/dmcs/par-marche/9902").header("Authorization", tokenPrmp)).andExpect(status().isCreated());
        mvc.perform(post("/api/dmcs/par-marche/9903").header("Authorization", tokenPrmp)).andExpect(status().isCreated());
        mvc.perform(post("/api/dmcs/par-marche/9904").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PV_NON_SIGNE"));

        String corps = mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(corps, "$[*].idDetail")).containsExactlyInAnyOrder(9901, 9902, 9903);
        assertThat(JsonPath.<List<String>>read(corps, "$[?(@.idDetail==9902)].formeMarche")).containsExactly("CONTRAT_CADRE");
        assertThat(JsonPath.<List<Boolean>>read(corps, "$[*].formeOutillee")).containsOnly(true);
    }

    // ------------------------------------------------------------------ 3. ligne sans forme

    @Test
    @DisplayName("3 — Ligne sans FORME_MARCHE : typeMarche nul, fiche lisible, écriture refusée ; POST par-marche refusé en "
            + "nommant le champ du plan ; eligibles : formeMarche nul, non outillée")
    void ligneSansForme() throws Exception {
        Long idDmc = creerDmc(9901);
        sansForme(9901);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeMarche").doesNotExist())
                .andExpect(jsonPath("$.valeursPpm.B01-AC-19").doesNotExist());
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FORME_NON_OUTILLEE"))
                .andExpect(jsonPath("$.message", containsString("Forme du marché")));

        Marche autre = ligne(9906, 9900, FormeMarche.QUANTITE_FIXE);
        sansForme(autre.getIdDetail());
        mvc.perform(post("/api/dmcs/par-marche/9906").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FORME_NON_OUTILLEE"))
                .andExpect(jsonPath("$.message", containsString("« Forme du marché »")));
        String corps = mvc.perform(get("/api/dmcs/eligibles").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(corps, "$[?(@.idDetail==9906)].formeMarche")).containsExactly((Object) null);
        assertThat(JsonPath.<List<Boolean>>read(corps, "$[?(@.idDetail==9906)].formeOutillee")).containsExactly(false);
    }

    // ------------------------------------------------------------------ 4. référentiel

    @Test
    @DisplayName("4 — Référentiel : B01-AC-19 « Forme du marché » (PPM, FORME_MARCHE) ; 23 informations reprises ; "
            + "rubrique Acheteur à 19")
    void referentiel() throws Exception {
        String ref = mvc.perform(get("/api/champs-fiche-marche").param("typeMarche", "QUANTITE_FIXE")
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B01-AC-19')].libelle")).containsExactly("Forme du marché");
        assertThat(JsonPath.<List<String>>read(ref, "$.champs[?(@.code=='B01-AC-19')].clePpm")).containsExactly("FORME_MARCHE");
        assertThat(JsonPath.<List<Integer>>read(ref, "$.blocs[0].rubriques[?(@.code=='B01-AC')].nbAttendu")).containsExactly(19);

        String dmc = mvc.perform(post("/api/dmcs/par-marche/9901").header("Authorization", tokenPrmp))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valeursPpm.B01-AC-19").value("À quantité fixe"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, Object>>read(dmc, "$.valeursPpm")).hasSize(23);
        Long idDmc = ((Number) JsonPath.read(dmc, "$.idDmc")).longValue();
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valeursPpm.B01-AC-19").value("À quantité fixe"));
    }

    // ------------------------------------------------------------------ 5. filiation

    @Test
    @DisplayName("5 — Mise à jour du plan qui passe la ligne en contrat-cadre : typeMarche = CONTRAT_CADRE, typeChange sur la "
            + "fiche brouillon ; ⚠️ lot 4 : écriture permise, et reprendre le cadrage éteint typeChange")
    void filiation() throws Exception {
        Long idDmc = creerDmc(9901);
        cadrage(idDmc);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.typeChange").value(false));

        planSigne(9910, "PRMP001");
        Dossier v2 = dossierRepository.findById(9910).orElseThrow();
        v2.setIdDossierParent(9900);
        dossierRepository.save(v2);
        Ppm ppm2 = ppmRepository.findById(9910).orElseThrow();
        ppm2.setNumMaj(2);
        ppmRepository.save(ppm2);
        Dossier v1 = dossierRepository.findById(9900).orElseThrow();
        v1.setStatut("REMPLACE");
        dossierRepository.save(v1);
        Marche lPrime = ligne(9911, 9910, FormeMarche.CONTRAT_CADRE);
        lPrime.setIdLigneOrigine(9901);
        marcheRepository.save(lPrime);

        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDetailCourant").value(9911))
                .andExpect(jsonPath("$.typeMarche").value("CONTRAT_CADRE"))
                .andExpect(jsonPath("$.typeChange").value(true))
                .andExpect(jsonPath("$.valeursPpm.B01-AC-19").value("Contrat cadre"));
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"OUI\",\"attributaires\":\"MULTI\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeMarche").value("CONTRAT_CADRE"))
                .andExpect(jsonPath("$.typeChange").value(false))
                .andExpect(jsonPath("$.typeOutille").value(true))
                .andExpect(jsonPath("$.cadrage.attributaires").value("MULTI"));
    }

    // ------------------------------------------------------------------ 6. fiches validées

    @Test
    @DisplayName("6 — Une fiche validée reste lisible, même version, même contenu, typeChange faux, si la forme du plan change "
            + "ensuite ; l'historique garde le type sous lequel elle a été saisie")
    void fichesValidees() throws Exception {
        Long idDmc = creerDmc(9901);
        cadrage(idDmc);
        mvc.perform(post("/api/fiches-marche/" + idDmc + "/valider").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));

        Marche l = marcheRepository.findById(9901).orElseThrow();
        l.setFormeMarche(FormeMarche.CONTRAT_CADRE);
        marcheRepository.saveAndFlush(l);
        mvc.perform(get("/api/fiches-marche/" + idDmc).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.typeChange").value(false))
                .andExpect(jsonPath("$.cadrage.garantieSoumission").value("NON"));
        mvc.perform(get("/api/fiches-marche/" + idDmc + "/versions").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$[0].typeMarche").value("QUANTITE_FIXE"));
    }

    // ------------------------------------------------------------------ outils

    private void planSigne(int id, String idPrmp) {
        dossierRepository.save(dossierLoc(id, "CLOTURE", "ANT", idPrmp));
        Dossier d = dossierRepository.findById(id).orElseThrow();
        d.setIdEntiteContract(1);
        dossierRepository.save(d);
        ppmRepository.save(ppm(id, id, idPrmp));
        receptionRepository.save(reception(id, id, "CTRCC1", true));
        dispatchRepository.save(dispatch(id, id, "CTRCC1", "CTRMEM", "CTRPRE"));
        examenRepository.save(examen(id, id, "CTRMEM"));
        seedPvSigne(id, id);
    }

    private Marche ligne(int idDetail, int idDossier, FormeMarche forme) {
        Marche l = marcheDao(idDetail, idDossier, idDossier);
        l.setIdMode(92);
        l.setFormeMarche(forme);
        return marcheRepository.save(l);
    }

    /** Efface la forme en base (plans anciens) : le getter la masquerait, la colonne la garde nulle. */
    private void sansForme(int idDetail) {
        Marche l = marcheRepository.findById(idDetail).orElseThrow();
        l.setFormeMarche(null);
        marcheRepository.saveAndFlush(l);
        assertThat(jdbcTemplate.queryForObject("select \"FORME_MARCHE\" from t_marche where \"ID_DETAIL\" = ?",
                String.class, idDetail)).isNull();
    }

    private Long creerDmc(int idDetail) throws Exception {
        String corps = mvc.perform(post("/api/dmcs/par-marche/" + idDetail).header("Authorization", tokenPrmp))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corps, "$.idDmc")).longValue();
    }

    private void cadrage(Long idDmc) throws Exception {
        mvc.perform(put("/api/fiches-marche/" + idDmc + "/cadrage").header("Authorization", tokenPrmp).contentType(JSON)
                .content("{\"cadrage\":{\"garantieSoumission\":\"NON\"}}")).andExpect(status().isOk());
    }
}
