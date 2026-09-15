package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ <strong>Une observation d'examen vise une CELLULE du document</strong> (demande front du 2026-09-14,
 * V30) — {@code champ}, {@code idMarcheCible}, {@code idBenefCible} sur la ligne « Au lieu de / Lire », et
 * leur recopie dans l'instantané FAVR.
 *
 * <p>Ce que ces tests protègent, dans l'ordre de la demande : la relecture à l'identique et la cible forcée
 * d'un point LIGNE (1) ; la rétrocompatibilité d'un corps sans ces champs (2) ; les refus, les mêmes par les
 * deux portes, puisqu'un seul validateur les sert (3, 4) ; la recopie à la signature sans toucher au libellé
 * (5), servie à la PRMP (6) ; et la migration elle-même, CHECK compris (7).</p>
 *
 * <p>Fixture : le dossier 1 (EXAMINE, examen 1 de CTRMEM) reçoit deux lignes de marché, chacune avec un
 * bénéficiaire, et une grille d'un point par portée ; le dossier 2 reçoit une ligne, « d'un autre
 * dossier ».</p>
 */
class ObservationCelluleCibleIntegrationTest extends CnmIntegrationTestSupport {

    private static final int PT_LIGNE = 8401;
    private static final int PT_DOSSIER = 8402;
    private static final int PT_FICHE = 8403;
    private static final int PT_AGPM = 8404;
    private static final int PT_SUPPRESSION = 8405;

    /** Lignes de marché du dossier 1, et une ligne du dossier 2. */
    private static final int LIGNE_A = 8411;
    private static final int LIGNE_B = 8412;
    private static final int LIGNE_AUTRE_DOSSIER = 8421;

    /** Bénéficiaire de la ligne A, et bénéficiaire de la ligne B. */
    private static final int BENEF_A = 8431;
    private static final int BENEF_B = 8432;

    /** Résultats d'examen déjà posés (pour la porte /api/observation-controles). */
    private static final int RES_LIGNE = 8441;
    private static final int RES_DOSSIER = 8442;
    private static final int RES_FICHE = 8443;
    private static final int RES_AGPM = 8444;
    private static final int RES_SUPPRESSION = 8445;

    @BeforeEach
    void grilleLignesEtBeneficiaires() {
        point(PT_LIGNE, "Mode de passation conforme", PorteePointCtrl.LIGNE);
        point(PT_DOSSIER, "Pas de fractionnement", PorteePointCtrl.DOSSIER);
        point(PT_FICHE, "Listes de la fiche cohérentes", PorteePointCtrl.FICHE);
        point(PT_AGPM, "AGPM cohérent avec le PPM", PorteePointCtrl.AGPM);
        point(PT_SUPPRESSION, "Constat de retrait", PorteePointCtrl.SUPPRESSION);

        marcheRepository.save(marche(LIGNE_A, 1, 1));
        marcheRepository.save(marche(LIGNE_B, 1, 1));
        ppmRepository.save(ppm(8420, 2, "PRMP001"));
        marcheRepository.save(marche(LIGNE_AUTRE_DOSSIER, 2, 8420));
        serviceBeneficiaireRepository.save(beneficiaire(BENEF_A, LIGNE_A));
        serviceBeneficiaireRepository.save(beneficiaire(BENEF_B, LIGNE_B));
        // Les résultats d'examen ne sont PAS posés ici : ils occuperaient le triplet d'unicité que les tests de
        // /api/examen-details réclament. resultatDe() les crée à la demande, pour la seule porte fille.
    }

    // ------------------------------------------------------------------ 1 & 2 — relecture, rétrocompatibilité

    @Test
    @DisplayName("1 — PUT examen-details avec champ « mode » sur un point LIGNE : relu à l'identique, idMarcheCible "
            + "forcé à la ligne du résultat")
    void pointLigne_champRelu_cibleForcee() throws Exception {
        int id = creerResultatHttp(PT_LIGNE, LIGNE_A, "{\"ordre\":1,\"auLieuDe\":\"AOO\",\"lire\":\"GAG\"}");

        mvc.perform(put("/api/examen-details/" + id).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsResultat(PT_LIGNE, LIGNE_A,
                        "{\"ordre\":1,\"auLieuDe\":\"AOO\",\"lire\":\"GAG\",\"champ\":\"mode\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observations[0].champ").value("mode"))
                .andExpect(jsonPath("$.observations[0].idMarcheCible").value(LIGNE_A))
                .andExpect(jsonPath("$.observations[0].idBenefCible").doesNotExist());

        // Relu par les deux lectures : le résultat et la ressource fille.
        mvc.perform(get("/api/examen-details/" + id).header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observations[0].champ").value("mode"))
                .andExpect(jsonPath("$.observations[0].idMarcheCible").value(LIGNE_A));
        mvc.perform(get("/api/observation-controles").param("detail", String.valueOf(id))
                .header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].champ").value("mode"))
                .andExpect(jsonPath("$[0].idMarcheCible").value(LIGNE_A));
    }

    @Test
    @DisplayName("1 bis — une colonne par bénéficiaire garde son bénéficiaire ; la ligne est forcée")
    void pointLigne_beneficiaireDeLaLigne_accepte() throws Exception {
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsResultat(PT_LIGNE, LIGNE_A,
                        "{\"ordre\":1,\"auLieuDe\":\"10\",\"lire\":\"12\",\"champ\":\"montBenef\",\"idBenefCible\":"
                                + BENEF_A + "}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observations[0].champ").value("montBenef"))
                .andExpect(jsonPath("$.observations[0].idMarcheCible").value(LIGNE_A))
                .andExpect(jsonPath("$.observations[0].idBenefCible").value(BENEF_A));
    }

    @Test
    @DisplayName("1 ter — fiche et AGPM : la ligne visée est portée par le corps, et relue")
    void ficheEtAgpm_ligneFournie_acceptee() throws Exception {
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsResultat(PT_FICHE, null, "{\"ordre\":1,\"auLieuDe\":\"x\",\"lire\":\"y\","
                        + "\"champ\":\"derogatoires.montEstim\",\"idMarcheCible\":" + LIGNE_B + "}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observations[0].champ").value("derogatoires.montEstim"))
                .andExpect(jsonPath("$.observations[0].idMarcheCible").value(LIGNE_B));
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsResultat(PT_AGPM, null, "{\"ordre\":1,\"auLieuDe\":\"x\",\"lire\":\"y\","
                        + "\"champ\":\"agpm.dateDao\",\"idMarcheCible\":" + LIGNE_A + "}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observations[0].champ").value("agpm.dateDao"));
    }

    @Test
    @DisplayName("2 — corps sans champ de cible : valeurs nulles, rien ne change")
    void corpsSansCible_valeursNulles() throws Exception {
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsResultat(PT_DOSSIER, null, "{\"ordre\":1,\"auLieuDe\":\"3 marchés\",\"lire\":\"1\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observations[0].auLieuDe").value("3 marchés"))
                .andExpect(jsonPath("$.observations[0].champ").doesNotExist())
                .andExpect(jsonPath("$.observations[0].idMarcheCible").doesNotExist())
                .andExpect(jsonPath("$.observations[0].idBenefCible").doesNotExist());
    }

    // ------------------------------------------------------------------ 3 & 4 — les refus, par les deux portes

    /** (libellé, point, ligne du résultat, fragment de cible, champ fautif attendu). */
    static Stream<Arguments> refus() {
        return Stream.of(
                Arguments.of("code inconnu", PT_LIGNE, LIGNE_A, "\"champ\":\"bidule\"", "champ"),
                Arguments.of("code PPM sur un point FICHE", PT_FICHE, null,
                        "\"champ\":\"mode\",\"idMarcheCible\":" + LIGNE_A, "champ"),
                Arguments.of("code de fiche sur un point LIGNE", PT_LIGNE, LIGNE_A,
                        "\"champ\":\"derogatoires.objet\"", "champ"),
                Arguments.of("point FICHE sans ligne", PT_FICHE, null, "\"champ\":\"derogatoires.objet\"",
                        "idMarcheCible"),
                Arguments.of("ligne d'un autre dossier", PT_DOSSIER, null,
                        "\"champ\":\"objet\",\"idMarcheCible\":" + LIGNE_AUTRE_DOSSIER, "idMarcheCible"),
                Arguments.of("ligne différente de celle du résultat LIGNE", PT_LIGNE, LIGNE_A,
                        "\"champ\":\"objet\",\"idMarcheCible\":" + LIGNE_B, "idMarcheCible"),
                Arguments.of("bénéficiaire étranger à la ligne", PT_LIGNE, LIGNE_A,
                        "\"champ\":\"montBenef\",\"idBenefCible\":" + BENEF_B, "idBenefCible"),
                Arguments.of("bénéficiaire avec « objet »", PT_LIGNE, LIGNE_A,
                        "\"champ\":\"objet\",\"idBenefCible\":" + BENEF_A, "idBenefCible"),
                Arguments.of("champ sur SUPPRESSION", PT_SUPPRESSION, LIGNE_B, "\"champ\":\"objet\"", "champ"),
                Arguments.of("cible sans champ", PT_DOSSIER, null, "\"idMarcheCible\":" + LIGNE_A, "idMarcheCible"));
    }

    @ParameterizedTest(name = "3 — examen-details : 400 sur {0}")
    @MethodSource("refus")
    void examenDetails_refus(String cas, int point, Integer ligne, String cible, String champFautif) throws Exception {
        // La ligne fautive est la SECONDE : le chemin doit porter son indice.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsResultat(point, ligne, "{\"ordre\":1,\"auLieuDe\":\"a\",\"lire\":\"b\"},"
                        + "{\"ordre\":2,\"auLieuDe\":\"c\",\"lire\":\"d\"," + cible + "}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("observations[1]." + champFautif));
    }

    @ParameterizedTest(name = "4 — observation-controles (POST) : 400 sur {0}")
    @MethodSource("refus")
    void observationControles_post_refus(String cas, int point, Integer ligne, String cible, String champFautif)
            throws Exception {
        mvc.perform(post("/api/observation-controles").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":" + resultatDe(point) + ",\"ordre\":1,\"auLieuDe\":\"a\",\"lire\":\"b\","
                        + cible + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value(champFautif));
    }

    @ParameterizedTest(name = "4 — observation-controles (PUT) : 400 sur {0}")
    @MethodSource("refus")
    void observationControles_put_refus(String cas, int point, Integer ligne, String cible, String champFautif)
            throws Exception {
        int idObservation = observation(resultatDe(point));
        mvc.perform(put("/api/observation-controles/" + idObservation).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":" + resultatDe(point) + ",\"ordre\":1,\"auLieuDe\":\"a\",\"lire\":\"b\","
                        + cible + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value(champFautif));
    }

    @Test
    @DisplayName("4 bis — observation-controles : la cible valide passe, la ligne d'un point LIGNE est forcée, "
            + "et un PUT sans cible l'efface")
    void observationControles_cibleValide_puisEffacee() throws Exception {
        resultatDe(PT_LIGNE);
        String cree = mvc.perform(post("/api/observation-controles").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":" + RES_LIGNE + ",\"ordre\":1,\"auLieuDe\":\"a\",\"lire\":\"b\","
                        + "\"champ\":\"nouvMontBenef\",\"idBenefCible\":" + BENEF_A + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.champ").value("nouvMontBenef"))
                .andExpect(jsonPath("$.idMarcheCible").value(LIGNE_A))
                .andExpect(jsonPath("$.idBenefCible").value(BENEF_A))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(cree, "$.idObservation");

        mvc.perform(put("/api/observation-controles/" + id).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDetail\":" + RES_LIGNE + ",\"ordre\":1,\"auLieuDe\":\"a\",\"lire\":\"b\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.champ").doesNotExist())
                .andExpect(jsonPath("$.idMarcheCible").doesNotExist())
                .andExpect(jsonPath("$.idBenefCible").doesNotExist());
    }

    @Test
    @DisplayName("4 ter — observation-controles : les gardes d'ExamenGarde répondent AVANT la cellule — autre Membre "
            + "403, résultat introuvable 403 (Membre) ou 409 (CC délégué), PV signé 409 ; rien n'est écrit")
    void observationControles_gardesAvantCellule() throws Exception {
        // ⚠️ Fusion de main (2026-09-15) — la porte fille applique d'abord les gardes de /api/examen-details
        // (a0b1172), puis le validateur de cellule (V30) : une cible invalide ne renseigne pas un tiers.
        int res = resultatDe(PT_LIGNE);
        int idObservation = observation(res);
        long lignes = observationControleRepository.count();
        String cibleInvalide = "{\"idDetail\":" + res + ",\"ordre\":1,\"auLieuDe\":\"a\",\"lire\":\"b\","
                + "\"champ\":\"bidule\"}";

        String autreMembre = bearer("CTRMEM2", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM2", "ANT");
        mvc.perform(post("/api/observation-controles").header("Authorization", autreMembre)
                .contentType(MediaType.APPLICATION_JSON).content(cibleInvalide))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/observation-controles/" + idObservation).header("Authorization", autreMembre)
                .contentType(MediaType.APPLICATION_JSON).content(cibleInvalide))
                .andExpect(status().isForbidden());

        // Résultat introuvable : examen inconnu, traité comme sur /api/examen-details. Le 400 « idDetail » du
        // validateur n'est plus atteint par cette porte.
        String introuvable = "{\"idDetail\":8499,\"ordre\":1,\"auLieuDe\":\"a\",\"lire\":\"b\",\"champ\":\"objet\"}";
        mvc.perform(post("/api/observation-controles").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(introuvable))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/observation-controles").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(introuvable))
                .andExpect(status().isConflict());

        cnm.prs.entity.Dossier dossier = dossierRepository.findById(1).orElseThrow();
        dossier.setStatut("PV_SIGNE");
        dossierRepository.save(dossier);
        mvc.perform(post("/api/observation-controles").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(cibleInvalide))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/observation-controles/" + idObservation).header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(cibleInvalide))
                .andExpect(status().isConflict());

        assertThat(observationControleRepository.count()).as("aucune ligne écrite par un refus").isEqualTo(lignes);
        assertThat(observationControleRepository.findById(idObservation).orElseThrow().getChampCible()).isNull();
    }

    // ------------------------------------------------------------------ 5 & 6 — instantané FAVR, PRMP

    @Test
    @DisplayName("5 & 6 — à la signature FAVR, observations-pv sert la cible et documentCible, à la PRMP comme à "
            + "l'UGPM ; le libellé reste identique à l'octet près")
    void signatureFavr_cibleRecopiee_libelleInchange() throws Exception {
        int id = creerResultatHttp(PT_LIGNE, LIGNE_A, "{\"ordre\":1,\"auLieuDe\":\"AOO\",\"lire\":\"GAG\","
                + "\"champ\":\"mode\"}");
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsResultat(PT_FICHE, null, "{\"ordre\":1,\"auLieuDe\":\"12\",\"lire\":\"15\","
                        + "\"champ\":\"delaisAmenages.delaiRemise\",\"idMarcheCible\":" + LIGNE_B + "}")))
                .andExpect(status().isCreated());
        assertThat(id).isPositive();

        signerPvAvecAvis(9301, "FAVR");

        String tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);
        for (String token : new String[] { tokenPrmp, tokenUgpm }) {
            String corps = mvc.perform(get("/api/observations-pv").header("Authorization", token)
                    .param("dossier", "1"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // Le libellé est celui d'avant la V30, recomposé ici à la main : aucune trace de la cible n'y entre.
            String libelleLigne = "Ligne « Marche " + LIGNE_A + " » — Mode de passation conforme : "
                    + "au lieu de « AOO », lire « GAG »";
            List<Object> ligne = JsonPath.read(corps, "$[?(@.champ=='mode')]");
            assertThat(ligne).hasSize(1);
            assertThat(JsonPath.<List<String>>read(corps, "$[?(@.champ=='mode')].libelle")).containsExactly(libelleLigne);
            assertThat(JsonPath.<List<Integer>>read(corps, "$[?(@.champ=='mode')].idMarcheCible"))
                    .containsExactly(LIGNE_A);
            assertThat(JsonPath.<List<String>>read(corps, "$[?(@.champ=='mode')].documentCible"))
                    .containsExactly("PPM");

            String libelleFiche = "Listes de la fiche cohérentes : au lieu de « 12 », lire « 15 »";
            assertThat(JsonPath.<List<String>>read(corps, "$[?(@.champ=='delaisAmenages.delaiRemise')].libelle"))
                    .containsExactly(libelleFiche);
            assertThat(JsonPath.<List<String>>read(corps,
                    "$[?(@.champ=='delaisAmenages.delaiRemise')].documentCible")).containsExactly("FICHE");
            assertThat(JsonPath.<List<Integer>>read(corps,
                    "$[?(@.champ=='delaisAmenages.delaiRemise')].idMarcheCible")).containsExactly(LIGNE_B);

            // Le point sans ligne détaillée (posé par la fixture FAVR) n'a pas de cible.
            String sansLigne = "$[?(@.libelle == 'Contrôle test : non conforme')]";
            assertThat(JsonPath.<List<Object>>read(corps, sansLigne)).hasSize(1);
            assertThat(JsonPath.<List<Object>>read(corps, sansLigne + ".champ")).allMatch(java.util.Objects::isNull);
            assertThat(JsonPath.<List<Object>>read(corps, sansLigne + ".idMarcheCible"))
                    .allMatch(java.util.Objects::isNull);
            assertThat(JsonPath.<List<Object>>read(corps, sansLigne + ".documentCible"))
                    .allMatch(java.util.Objects::isNull);
        }
    }

    // ------------------------------------------------------------------ 7 — la migration

    @Test
    @DisplayName("7 — V30 est appliquée (colonnes des deux tables) et se rejoue sans erreur")
    void migrationV30_appliqueeEtIdempotente() {
        for (String table : new String[] { "t_observation_controle", "t_observation_pv" }) {
            List<String> colonnes = jdbcTemplate.queryForList("select column_name from information_schema.columns "
                    + "where table_schema = 'public' and table_name = ? and column_name in "
                    + "('CHAMP_CIBLE', 'ID_MARCHE_CIBLE', 'ID_BENEF_CIBLE')", String.class, table);
            assertThat(colonnes).containsExactlyInAnyOrder("CHAMP_CIBLE", "ID_MARCHE_CIBLE", "ID_BENEF_CIBLE");
            Integer longueur = jdbcTemplate.queryForObject("select character_maximum_length from "
                    + "information_schema.columns where table_schema = 'public' and table_name = ? "
                    + "and column_name = 'CHAMP_CIBLE'", Integer.class, table);
            assertThat(longueur).isEqualTo(cnm.prs.enums.ChampCible.LONGUEUR_MAX);
        }
        resultatDe(PT_LIGNE);   // la ligne d'observation garde sa clé étrangère vers le résultat
        executerMigrationFlyway("V30__observation_cellule_cible.sql");
        // Une cible complète passe le CHECK.
        jdbcTemplate.update("insert into public.t_observation_controle (\"ID_DETAIL\", \"ORDRE\", \"CHAMP_CIBLE\", "
                + "\"ID_MARCHE_CIBLE\") values (?, 1, 'mode', ?)", RES_LIGNE, LIGNE_A);
    }

    @Test
    @DisplayName("7 — le CHECK de t_observation_controle refuse une cible sans champ")
    void check_observationControle_refuseCibleSansChamp() {
        resultatDe(PT_LIGNE);   // le refus doit venir du CHECK, pas de la clé étrangère
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("insert into public.t_observation_controle (\"ID_DETAIL\", "
                + "\"ORDRE\", \"ID_MARCHE_CIBLE\") values (?, 1, ?)", RES_LIGNE, LIGNE_A))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("t_observation_controle_CIBLE_check");
    }

    @Test
    @DisplayName("7 — le CHECK de t_observation_pv refuse un bénéficiaire sans champ")
    void check_observationPv_refuseCibleSansChamp() {
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("insert into public.t_observation_pv (\"ID_DOSSIER\", "
                + "\"ID_PV\", \"SOURCE\", \"LIBELLE\", \"ID_BENEF_CIBLE\") values (1, 1, 'POINT', 'x', ?)", BENEF_A))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("t_observation_pv_CIBLE_check");
    }

    // ------------------------------------------------------------------ fixture

    private void point(int id, String libelle, PorteePointCtrl portee) {
        PointsCtrl p = new PointsCtrl();
        p.setIdPointCtrl(id);
        p.setLibelPointCtrl(libelle);
        p.setObligatoire(true);
        p.setIdTypeDossier("DDP");
        p.setIdSousType(portee == PorteePointCtrl.AGPM ? "PPM-AGPM" : null);
        p.setPortee(portee);
        p.setOrdrePointCtrl(id);
        pointsCtrlRepository.save(p);
    }

    private ServiceBeneficiaire beneficiaire(int idBenef, int idDetail) {
        ServiceBeneficiaire s = new ServiceBeneficiaire();
        s.setIdBenef(idBenef);
        s.setIdDetail(idDetail);
        return s;
    }

    private void resultat(int id, int point, Integer ligne) {
        ExamenDetail d = new ExamenDetail();
        d.setIdDetailExamen(id);
        d.setIdExamen(1);
        d.setIdPtControle(point);
        d.setIdDetail(ligne);
        d.setConforme(false);
        examenDetailRepository.save(d);
    }

    /** Résultat d'examen non conforme du point, créé à la première demande ; renvoie son identifiant. */
    private int resultatDe(int point) {
        int id;
        Integer ligne;
        switch (point) {
            case PT_LIGNE -> { id = RES_LIGNE; ligne = LIGNE_A; }
            case PT_DOSSIER -> { id = RES_DOSSIER; ligne = null; }
            case PT_FICHE -> { id = RES_FICHE; ligne = null; }
            case PT_AGPM -> { id = RES_AGPM; ligne = null; }
            default -> { id = RES_SUPPRESSION; ligne = LIGNE_B; }
        }
        if (!examenDetailRepository.existsById(id)) {
            resultat(id, point, ligne);
        }
        return id;
    }

    /** Une ligne d'observation sans cible sur un résultat existant ; renvoie son identifiant. */
    private int observation(int idResultat) {
        cnm.prs.entity.ObservationControle oc = new cnm.prs.entity.ObservationControle();
        oc.setIdDetail(idResultat);
        oc.setAuLieuDe("a");
        oc.setLire("b");
        oc.setOrdre(1);
        return observationControleRepository.save(oc).getIdObservation();
    }

    /** Corps d'un résultat non conforme sur l'examen 1. */
    private String corpsResultat(int point, Integer ligne, String observations) {
        return "{\"idExamen\":1,\"idPtControle\":" + point + (ligne == null ? "" : ",\"idDetail\":" + ligne)
                + ",\"conforme\":false,\"observations\":[" + observations + "]}";
    }

    /** Crée un résultat par HTTP ; renvoie son identifiant. */
    private int creerResultatHttp(int point, Integer ligne, String observations) throws Exception {
        String corps = mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(corpsResultat(point, ligne, observations)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(corps, "$.idDetailExamen");
    }
}
