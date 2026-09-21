package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Controleur;
import cnm.prs.entity.DemandeRetrait;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.PvNavette;
import cnm.prs.entity.SuspensionDossier;
import cnm.prs.entity.TacheDossier;
import cnm.prs.entity.Verification;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.SuspensionDossierRepository;
import cnm.prs.repository.TacheDossierRepository;

/**
 * ⚠️ <strong>Page dossier</strong> — {@code GET /api/dossiers/{id}/gestes} (refonte ergonomique, lot L4-B1, plan du
 * 2026-09-15 §6 ; tests attendus 1 à 6).
 *
 * <p>Même <strong>horloge figée</strong> qu'{@code AFaireIntegrationTest} (lundi 2026-09-14 15:00) : la parité avec
 * l'accueil compare des délais en heures, qu'une horloge système pourrait faire basculer entre deux appels. Contexte
 * Spring distinct du socle (bean {@code Clock} remplacé). Les fabriques de décor reprennent celles de l'accueil, qui
 * y sont privées : le test épinglé de l'accueil n'est pas modifié.</p>
 */
class GestesDossierIntegrationTest extends CnmIntegrationTestSupport {

    private static final LocalDateTime MAINTENANT = LocalDate.of(2026, 9, 14).atTime(15, 0);
    private static final LocalDateTime JEUDI_10H = LocalDate.of(2026, 9, 10).atTime(10, 5);
    private static final LocalDateTime VENDREDI_14H = LocalDate.of(2026, 9, 11).atTime(14, 0);
    private static final Set<String> URGENCES_CHRONOMETREES = Set.of("EN_RETARD", "BIENTOT", "DANS_LES_DELAIS");

    @TestConfiguration
    static class HorlogeLundi {
        // Nom de bean différent de `clock` (ClockConfig) : @Primary départage l'injection par type.
        @Bean
        @Primary
        Clock horlogeDeLaPageDossier() {
            return new HorlogeMutable(MAINTENANT.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired private TacheDossierRepository tacheRepository;
    @Autowired private SuspensionDossierRepository suspensionRepository;

    private String tokenSec;
    private String tokenSecTms;
    private String tokenCcTms;
    private String tokenCc3;
    private String tokenMembre2;
    private String tokenMembreTms;
    private String tokenVer;
    private String tokenVer2;
    private String tokenVerTms;
    private String tokenAss;
    private String tokenAssTms;
    private String tokenUgpm;
    private String tokenPrmp2;

    @BeforeEach
    void acteurs() {
        controleurRepository.save(controleur("CTRSECT", 4, "TMS"));   // Secrétaire de TMS
        controleurRepository.save(controleur("CTRCC3", 3, "ANT"));    // second CC de la centrale
        controleurRepository.save(controleur("CTRMEM2", 5, "ANT"));   // Membre pair
        controleurRepository.save(controleur("CTRMEMT", 5, "TMS"));   // Membre de TMS
        controleurRepository.save(controleur("CTRVER2", 6, "ANT"));   // Vérificateur collègue
        controleurRepository.save(controleur("CTRVERT", 6, "TMS"));   // Vérificateur de TMS
        controleurRepository.save(controleur("CTRASST", 9, "TMS"));   // Assistant de TMS
        tokenSec = jeton("CTRSEC", ProfilUtilisateur.SECRETAIRE, "ANT");
        tokenSecTms = jeton("CTRSECT", ProfilUtilisateur.SECRETAIRE, "TMS");
        tokenCcTms = jeton("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, "TMS");
        tokenCc3 = jeton("CTRCC3", ProfilUtilisateur.CHEF_COMMISSION, "ANT");
        tokenMembre2 = jeton("CTRMEM2", ProfilUtilisateur.MEMBRE, "ANT");
        tokenMembreTms = jeton("CTRMEMT", ProfilUtilisateur.MEMBRE, "TMS");
        tokenVer = jeton("CTRVER", ProfilUtilisateur.VERIFICATEUR, "ANT");
        tokenVer2 = jeton("CTRVER2", ProfilUtilisateur.VERIFICATEUR, "ANT");
        tokenVerTms = jeton("CTRVERT", ProfilUtilisateur.VERIFICATEUR, "TMS");
        tokenAss = jeton("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, "ANT");
        tokenAssTms = jeton("CTRASST", ProfilUtilisateur.ASSISTANT_CONTROLEUR, "TMS");
        ugpmRepository.save(ugpm("UGPM714", "PRMP001", "RAKOTO", "Hery"));
        tokenUgpm = bearer("ugpm.hery", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);
        cnm.prs.entity.Prmp autrePrmp = prmp("PRMP002", null);
        autrePrmp.setCin("202022223333");
        autrePrmp.setEmailPrmp("prmp2@min.mg");
        prmpRepository.save(autrePrmp);
        tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", null);
    }

    // ================================================================== 1 — parité avec l'accueil

    @Test
    @DisplayName("1 — Parité : pour chaque profil et chaque dossier du décor, les lignes de /gestes sont celles de "
            + "/a-faire?delegations=true pour ce dossier (titulaires puis bloc délégation, dans l'ordre, rang excepté) ; "
            + "403 exactement là où la consultation refuse")
    void parite_aFaire() throws Exception {
        List<Integer> ids = decorParite();
        Map<String, String> jetons = new LinkedHashMap<>();
        jetons.put("Président", tokenPresident);
        jetons.put("CC ANT", tokenCc);
        jetons.put("CC ANT bis", tokenCc3);
        jetons.put("CC TMS", tokenCcTms);
        jetons.put("Secrétaire ANT", tokenSec);
        jetons.put("Secrétaire TMS", tokenSecTms);
        jetons.put("Membre", tokenMembre);
        jetons.put("Membre pair", tokenMembre2);
        jetons.put("Membre TMS", tokenMembreTms);
        jetons.put("Vérificateur", tokenVer);
        jetons.put("Vérificateur cible", tokenVer2);
        jetons.put("Vérificateur TMS", tokenVerTms);
        jetons.put("Assistant", tokenAss);
        jetons.put("Assistant TMS", tokenAssTms);
        jetons.put("PRMP", tokenPrmp);
        jetons.put("Autre PRMP", tokenPrmp2);
        jetons.put("UGPM", tokenUgpm);

        int lignesComparees = 0;
        int refus = 0;
        Set<String> modes = new HashSet<>();
        Set<String> sections = new HashSet<>();
        for (Map.Entry<String, String> profil : jetons.entrySet()) {
            String accueil = aFaireAvecDelegations(profil.getValue());
            int dossiersVus = 0;
            for (int id : ids) {
                List<Map<String, Object>> attendues = new ArrayList<>(
                        JsonPath.<List<Map<String, Object>>>read(accueil, "$.taches" + filtreDossier(id)));
                attendues.addAll(JsonPath.<List<Map<String, Object>>>read(accueil,
                        "$.delegations.taches" + filtreDossier(id)));

                MvcResult reponse = mvc.perform(get("/api/dossiers/" + id + "/gestes")
                        .header("Authorization", profil.getValue())).andReturn();
                int code = reponse.getResponse().getStatus();
                if (code == 403) {
                    assertThat(attendues).as("%s, dossier %d refusé : aucune ligne à l'accueil", profil.getKey(), id)
                            .isEmpty();
                    // La garde est celle de la consultation : même refus.
                    mvc.perform(get("/api/dossiers/" + id).header("Authorization", profil.getValue()))
                            .andExpect(status().isForbidden());
                    refus++;
                    continue;
                }
                assertThat(code).as("%s, dossier %d", profil.getKey(), id).isEqualTo(200);
                dossiersVus++;
                String corps = reponse.getResponse().getContentAsString();
                List<Map<String, Object>> servies = JsonPath.read(corps, "$.taches");
                assertThat(sansRang(servies)).as("%s, dossier %d", profil.getKey(), id).isEqualTo(sansRang(attendues));
                List<Integer> rangs = JsonPath.read(corps, "$.taches[*].rang");
                for (int i = 0; i < rangs.size(); i++) {
                    assertThat(rangs.get(i)).isEqualTo(i + 1);
                }
                lignesComparees += servies.size();
                servies.forEach(t -> {
                    modes.add((String) t.get("mode"));
                    sections.add((String) t.get("section"));
                });
            }
            assertThat(dossiersVus).as("dossiers du périmètre de %s", profil.getKey()).isPositive();
        }
        // Le décor exerce bien tous les titres et la plupart des sections, et des refus.
        assertThat(modes).containsExactlyInAnyOrder("TITULAIRE", "DELEGATION", "INTERIM", "COLLEGUE", "SUPPLEANCE");
        assertThat(sections).contains("A_RECEPTIONNER", "A_DISPATCHER", "A_EXAMINER", "A_REEXAMINER", "PV_A_SOUMETTRE",
                "PV_A_REPRENDRE", "PV_A_ACCEPTER", "PV_A_VISER", "PV_A_SIGNER", "LETTRES_A_SIGNER",
                "RETRAITS_A_DECIDER", "A_VERIFIER", "A_TRANSMETTRE_SIGMP", "A_ARCHIVER", "LETTRES_A_ARCHIVER",
                "EN_ATTENTE_PRMP", "BROUILLONS", "PIECES_DEPOT_A_COMPLETER", "COMPLEMENTS_A_TRANSMETTRE", "A_RECTIFIER",
                "EN_COURS_CNM");
        assertThat(lignesComparees).isGreaterThan(60);
        assertThat(refus).isPositive();
    }

    // ================================================================== 2 — codes d'erreur

    @Test
    @DisplayName("2 — 404 dossier inexistant ; 403 hors périmètre (CC d'une autre localité, PRMP d'une autre tutelle), "
            + "avec les messages de la consultation ; 403 Administrateur et Chargé de publication ; 401 anonyme")
    void codesErreur() throws Exception {
        circuitDispatche(7201, "DISPATCHE", "ANT", "CTRPRE", "CTRMEM");

        for (String jeton : List.of(tokenPresident, tokenCc, tokenPrmp)) {
            String corps = mvc.perform(get("/api/dossiers/999999/gestes").header("Authorization", jeton))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            assertThat(JsonPath.<String>read(corps, "$.message")).isEqualTo("Dossier introuvable : 999999")
                    .isEqualTo(messageConsultation(999999, jeton, 404));
        }
        for (String jeton : List.of(tokenCcTms, tokenMembreTms, tokenPrmp2)) {
            String corps = mvc.perform(get("/api/dossiers/7201/gestes").header("Authorization", jeton))
                    .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
            assertThat(JsonPath.<String>read(corps, "$.message"))
                    .isEqualTo("Dossier hors de votre périmètre de visibilité (§1).")
                    .isEqualTo(messageConsultation(7201, jeton, 403));
        }
        mvc.perform(get("/api/dossiers/7201/gestes").header("Authorization", tokenAdmin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/dossiers/7201/gestes").header("Authorization", tokenPublication))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dossiers/7201/gestes")).andExpect(status().isUnauthorized());
        // Témoins : les profils du périmètre sont servis.
        mvc.perform(get("/api/dossiers/7201/gestes").header("Authorization", tokenCc)).andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/7201/gestes").header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/7201/gestes").header("Authorization", tokenUgpm)).andExpect(status().isOk());
    }

    // ================================================================== 3 — statuts terminaux et pause

    @Test
    @DisplayName("3 — Statuts terminaux (lettre et retrait en cours compris) : taches = [] et etapeCourante = null ; "
            + "brouillon : null pour le Président, HORS_DELAI pour la PRMP ; EN_ATTENTE_DECISION_PRMP : EN_PAUSE avec "
            + "pauseDepuis")
    void terminauxEtPause() throws Exception {
        List<String> terminaux = List.of("CLOTURE", "RETIRE", "REMPLACE", "PV_SIGNE");
        for (int i = 0; i < terminaux.size(); i++) {
            int id = 7310 + i;
            lettre(circuitDispatche(id, terminaux.get(i), "ANT", "CTRPRE", "CTRMEM"), "SOUMIS");
            retrait(id);
        }
        for (String jeton : List.of(tokenPresident, tokenCc, tokenVer, tokenPrmp, tokenUgpm)) {
            for (int id = 7310; id < 7314; id++) {
                String corps = gestes(jeton, id);
                assertThat(JsonPath.<Map<String, Object>>read(corps, "$")).containsKey("etapeCourante");
                assertThat(JsonPath.<Object>read(corps, "$.etapeCourante")).as("dossier %d", id).isNull();
                assertThat(JsonPath.<List<Object>>read(corps, "$.taches")).as("dossier %d", id).isEmpty();
            }
        }

        dossier(7320, "BROUILLON", "ANT");
        String president = gestes(tokenPresident, 7320);
        assertThat(JsonPath.<Object>read(president, "$.etapeCourante")).isNull();
        assertThat(JsonPath.<List<Object>>read(president, "$.taches")).isEmpty();
        String prmpBrouillon = gestes(tokenPrmp, 7320);
        assertThat(JsonPath.<String>read(prmpBrouillon, "$.etapeCourante.urgence")).isEqualTo("HORS_DELAI");
        assertThat(JsonPath.<List<String>>read(prmpBrouillon, "$.taches[*].geste")).containsExactly("SOUMETTRE");
        assertThat(JsonPath.<List<String>>read(gestes(tokenUgpm, 7320), "$.taches[*].geste"))
                .containsExactly("COMPLETER_BROUILLON");

        pvSigne(circuitDispatche(7330, "EN_ATTENTE_DECISION_PRMP", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        verification(7330);
        passage(7330, "VERIFICATION", VENDREDI_14H.minusHours(4), "CTRVER");
        attente(7330, VENDREDI_14H.minusHours(4));

        String prmp = gestes(tokenPrmp, 7330);
        assertThat(JsonPath.<String>read(prmp, "$.etapeCourante.urgence")).isEqualTo("EN_PAUSE");
        assertThat(JsonPath.<String>read(prmp, "$.etapeCourante.delai.pauseDepuis")).isEqualTo("2026-09-11T10:00:00");
        assertThat(JsonPath.<Integer>read(prmp, "$.etapeCourante.delai.pauseHeures")).isEqualTo(13);
        assertThat(JsonPath.<List<String>>read(prmp, "$.taches[*].section")).containsExactly("A_RECTIFIER");
        assertThat(JsonPath.<Map<String, Object>>read(prmp, "$.taches[0].delai"))
                .isEqualTo(JsonPath.<Map<String, Object>>read(prmp, "$.etapeCourante.delai"));
        // L'UGPM n'a pas le geste (réservé à la PRMP), mais voit la pause ; le Vérificateur suit l'attente.
        String ugpm = gestes(tokenUgpm, 7330);
        assertThat(JsonPath.<List<Object>>read(ugpm, "$.taches")).isEmpty();
        assertThat(JsonPath.<String>read(ugpm, "$.etapeCourante.urgence")).isEqualTo("EN_PAUSE");
        String ver = gestes(tokenVer, 7330);
        assertThat(JsonPath.<List<String>>read(ver, "$.taches[*].geste")).containsExactly("VOIR");
        assertThat(JsonPath.<String>read(ver, "$.etapeCourante.urgence")).isEqualTo("EN_PAUSE");
    }

    // ================================================================== 4 — Membre non attributaire

    @Test
    @DisplayName("4 — Membre non attributaire : taches = [], etapeCourante servi, identique à celui de l'attributaire "
            + "et au délai de sa ligne ; enveloppe et clés toujours présentes")
    void membreNonAttributaire() throws Exception {
        circuitDispatche(7401, "DISPATCHE", "ANT", "CTRPRE", "CTRMEM");
        passage(7401, "RECEPTION", JEUDI_10H.plusHours(2), "CTRSEC");
        passage(7401, "DISPATCH", VENDREDI_14H, "CTRPRE");

        String pair = gestes(tokenMembre2, 7401);
        assertThat(JsonPath.<List<Object>>read(pair, "$.taches")).isEmpty();
        assertThat(JsonPath.<Map<String, Object>>read(pair, "$")).containsOnlyKeys("idDossier", "profil", "genereLe",
                "etapeCourante", "taches");
        assertThat(JsonPath.<Integer>read(pair, "$.idDossier")).isEqualTo(7401);
        assertThat(JsonPath.<String>read(pair, "$.profil")).isEqualTo("MEMBRE");
        assertThat(JsonPath.<String>read(pair, "$.genereLe")).isEqualTo("2026-09-14T15:00:00");
        assertThat(JsonPath.<String>read(pair, "$.etapeCourante.delai.etape")).isEqualTo("EXAMEN");
        assertThat(JsonPath.<String>read(pair, "$.etapeCourante.delai.entree")).isEqualTo("2026-09-11T14:00:00");
        assertThat(JsonPath.<Integer>read(pair, "$.etapeCourante.delai.ecouleHeures")).isEqualTo(9);
        assertThat(JsonPath.<String>read(pair, "$.etapeCourante.urgence")).isIn(URGENCES_CHRONOMETREES);
        assertThat(JsonPath.<Map<String, Object>>read(pair, "$.etapeCourante.delai")).containsOnlyKeys("etape", "entree",
                "standardHeures", "ecouleHeures", "restantHeures", "echeance", "pauseDepuis", "pauseHeures",
                "datePrevisionnelleFin");
        assertThat(JsonPath.<Object>read(pair, "$.etapeCourante.delai.echeance")).isNotNull();

        String titulaire = gestes(tokenMembre, 7401);
        assertThat(JsonPath.<List<String>>read(titulaire, "$.taches[*].geste")).containsExactly("EXAMINER");
        assertThat(JsonPath.<Map<String, Object>>read(titulaire, "$.etapeCourante"))
                .isEqualTo(JsonPath.<Map<String, Object>>read(pair, "$.etapeCourante"));
        assertThat(JsonPath.<Map<String, Object>>read(titulaire, "$.taches[0].delai"))
                .isEqualTo(JsonPath.<Map<String, Object>>read(titulaire, "$.etapeCourante.delai"));
        assertThat(JsonPath.<String>read(titulaire, "$.taches[0].urgence"))
                .isEqualTo(JsonPath.<String>read(titulaire, "$.etapeCourante.urgence"));
        assertThat(JsonPath.<Integer>read(titulaire, "$.taches[0].rang")).isEqualTo(1);

        // La PRMP en suivi : SUIVI sur la ligne, jamais sur l'étape.
        String prmp = gestes(tokenPrmp, 7401);
        assertThat(JsonPath.<List<String>>read(prmp, "$.taches[*].urgence")).containsExactly("SUIVI");
        assertThat(JsonPath.<String>read(prmp, "$.etapeCourante.urgence"))
                .isEqualTo(JsonPath.<String>read(pair, "$.etapeCourante.urgence"));
    }

    // ================================================================== 5 — règle C2

    @Test
    @DisplayName("5 — Règle C2 : le corps brut servi à la PRMP et à l'UGPM ne contient ni nom ni matricule de "
            + "contrôleur, ni consigne ni retour de navette ; champs internes à null ; annuaire des contrôleurs non chargé")
    void c2_partieControlee() throws Exception {
        Dossier consigne = circuitDispatche(7501, "DISPATCHE", "ANT", "CTRPRE", "CTRMEM");
        var dispatch = dispatchRepository.findById(7501).orElseThrow();
        dispatch.setInstructions("Consigne interne X9");
        dispatchRepository.save(dispatch);
        passage(7501, "RECEPTION", VENDREDI_14H, "CTRSEC");
        passage(7501, "DISPATCH", VENDREDI_14H.plusHours(1), "CTRPRE");
        PvExamen accepte = pv(circuitDispatche(7502, "EXAMINE", "ANT", "CTRCC1", "CTRMEM"), "PROJET_ACCEPTE", "CTRMEM");
        accepte.setDateSignaturePresident(LocalDate.of(2026, 9, 11));
        accepte.setImMembreCoSignataire("CTRMEM2");
        accepte.setImCcCoSignataire("CTRCC1");
        pvExamenRepository.save(accepte);
        navette(7502, "RETOUR_CC", "Retour interne Y7");
        passage(7502, "EXAMEN", VENDREDI_14H, "CTRMEM");
        pvSigne(circuitDispatche(7503, "EN_ATTENTE_DECISION_PRMP", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        assertThat(consigne.getIdPrmp()).isEqualTo("PRMP001");

        // Témoins : les contrôleurs concernés reçoivent bien ces informations internes.
        assertThat(gestes(tokenMembre, 7501)).contains("Consigne interne X9").contains("NomCTR");
        assertThat(gestes(tokenCc, 7502)).contains("Retour interne Y7").contains("CTR");
        assertThat(controleursCharges(tokenCc, 7502)).isPositive();

        for (String jeton : List.of(tokenPrmp, tokenUgpm)) {
            for (int id : new int[] {7501, 7502, 7503}) {
                String corps = gestes(jeton, id);
                assertThat(corps).as("dossier %d", id).doesNotContain("CTR").doesNotContain("NomCTR")
                        .doesNotContain("Consigne interne X9").doesNotContain("Retour interne Y7");
                for (String champ : List.of("dossier.acteursEtapes", "dossier.niveauNavette", "faits.consigneDispatch",
                        "faits.dernierRetourNavette", "faits.partsAttendues", "refs.idDispatch")) {
                    assertThat(JsonPath.<List<Object>>read(corps, "$.taches[*]." + champ)).as("%s, dossier %d", champ, id)
                            .allMatch(java.util.Objects::isNull);
                }
                assertThat(controleursCharges(jeton, id)).as("annuaire chargé pour le dossier %d", id).isZero();
            }
            // Les lignes existent bien (le corps n'est pas vide par accident), et les dates de la frise restent.
            String suivi = gestes(jeton, 7501);
            assertThat(JsonPath.<List<String>>read(suivi, "$.taches[*].geste")).containsExactly("SUIVRE");
            assertThat(JsonPath.<String>read(suivi, "$.taches[0].dossier.datesEtapes.RECEPTION"))
                    .isEqualTo("2026-09-11T14:00:00");
            assertThat(JsonPath.<Object>read(suivi, "$.etapeCourante")).isNotNull();
        }
    }

    // ================================================================== 6 — requêtes

    @Test
    @DisplayName("6 — Ordres SQL : au plus 15, garde comprise, et constants d'un dossier à l'autre pour chaque profil")
    void sql_constant() throws Exception {
        pv(circuitDispatche(7601, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "PROJET_SOUMIS", "CTRMEM");
        passage(7601, "EXAMEN", VENDREDI_14H, "CTRMEM");
        pvSigne(circuitDispatche(7602, "DECISION_TRANSMISE_SIGMP", "ANT", "CTRPRE", "CTRMEM"), "FAV");
        lettre(7602, "SIGNE");
        pvSigne(circuitDispatche(7603, "EN_ATTENTE_DECISION_PRMP", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        attente(7603, VENDREDI_14H);
        circuitDispatche(7604, "DISPATCHE", "ANT", "CTRPRE", "CTRMEM");
        retrait(7604);
        dossier(7605, "SOUMIS", "ANT");
        lettre(circuitDispatche(7606, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "SOUMIS");
        int[] actifs = {7601, 7602, 7603, 7604, 7605, 7606};

        Map<String, String> jetons = new LinkedHashMap<>();
        jetons.put("PRESIDENT", tokenPresident);
        jetons.put("CHEF_COMMISSION", tokenCc);
        jetons.put("SECRETAIRE", tokenSec);
        jetons.put("MEMBRE", tokenMembre);
        jetons.put("VERIFICATEUR", tokenVer);
        jetons.put("ASSISTANT_CONTROLEUR", tokenAss);
        jetons.put("PRMP", tokenPrmp);
        jetons.put("UGPM", tokenUgpm);
        Map<String, Long> mesures = new LinkedHashMap<>();
        for (Map.Entry<String, String> profil : jetons.entrySet()) {
            long premier = ordresSql(profil.getValue(), actifs[0]);
            for (int id : actifs) {
                assertThat(ordresSql(profil.getValue(), id)).as("%s, dossier %d", profil.getKey(), id)
                        .isEqualTo(premier).isLessThanOrEqualTo(15);
            }
            mesures.put(profil.getKey(), premier);
        }
        // Valeurs mesurées (2026-09-15) : celles de l'accueil (12 CNM, 13 Vérificateur, 14 Assistant, 7 PRMP), plus
        // la garde de visibilité (aucune requête pour le Président) ; la ligne du dossier remplace la lecture du
        // périmètre et sert de test d'existence. Une hausse n'est pas une faute en soi, mais elle doit être voulue.
        // ⚠️ 2026-09-21 (intérim désigné) : +1 pour le Chef de commission et le Membre, les deux seuls profils qui
        // peuvent être intérimaires — leurs suppléances actives sont lues une fois par requête (contexte d'intérim).
        assertThat(mesures).containsExactly(Map.entry("PRESIDENT", 12L), Map.entry("CHEF_COMMISSION", 14L),
                Map.entry("SECRETAIRE", 13L), Map.entry("MEMBRE", 14L), Map.entry("VERIFICATEUR", 14L),
                Map.entry("ASSISTANT_CONTROLEUR", 15L), Map.entry("PRMP", 8L), Map.entry("UGPM", 8L));

        // Et le calcul a bien porté sur des dossiers qui servent des lignes.
        assertThat(JsonPath.<List<String>>read(gestes(tokenPresident, 7601), "$.taches[*].geste")).containsExactly("VISER");
        assertThat(JsonPath.<List<String>>read(gestes(tokenAss, 7602), "$.taches[*].geste"))
                .containsExactly("ARCHIVER_PV", "ARCHIVER_LETTRE");

        // Statut terminal : rien n'est calculé — la ligne du dossier et la garde de visibilité (aucune pour le Président),
        // plus le contexte d'intérim pour le Chef de commission et le Membre (2026-09-21) : trois au plus.
        circuitDispatche(7610, "CLOTURE", "ANT", "CTRPRE", "CTRMEM");
        assertThat(ordresSql(tokenPresident, 7610)).isEqualTo(1);
        for (String jeton : jetons.values()) {
            assertThat(ordresSql(jeton, 7610)).isLessThanOrEqualTo(3);
        }
    }

    // ================================================================== décor de la parité

    /** Tous les cas de l'accueil, dans les deux localités et pour deux tutelles ; renvoie les dossiers à parcourir. */
    private List<Integer> decorParite() {
        // Le Membre CTRMEM est rattaché au Vérificateur CTRVER2 : ses dossiers vérifiés ont une cible.
        Controleur membre = controleurRepository.findById("CTRMEM").orElseThrow();
        membre.setImRattache("CTRVER2");
        controleurRepository.save(membre);

        dossier(7101, "SOUMIS", "ANT");
        circuitAvecReception(7102, "PRET_DISPATCH", "TMS", "CTRSECT");
        passage(7102, "RECEPTION", VENDREDI_14H, "CTRSECT");
        circuitDispatche(7103, "DISPATCHE", "ANT", "CTRPRE", "CTRMEM");
        passage(7103, "DISPATCH", VENDREDI_14H, "CTRPRE");
        circuitDispatche(7104, "A_REEXAMINER", "ANT", "CTRPRE", "CTRMEM");
        pv(circuitDispatche(7105, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "BROUILLON", "CTRMEM");
        pv(circuitDispatche(7106, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "EN_RECTIFICATION", "CTRMEM");
        PvExamen accepter = pv(circuitDispatche(7107, "EXAMINE", "ANT", "CTRCC1", "CTRMEM"), "PROJET_SOUMIS", "CTRMEM");
        accepter.setNiveauNavette("CC");
        PvExamen viserHaut = pv(circuitDispatche(7108, "EXAMINE", "ANT", "CTRCC1", "CTRMEM"), "PROJET_SOUMIS", "CTRMEM");
        viserHaut.setNiveauNavette("PRESIDENT");
        pv(circuitDispatche(7109, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "PROJET_SOUMIS", "CTRMEM");
        passage(7109, "EXAMEN", VENDREDI_14H, "CTRMEM");
        PvExamen signer = pv(circuitDispatche(7110, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "PROJET_ACCEPTE", "CTRMEM");
        signer.setDateSignaturePresident(LocalDate.of(2026, 9, 11));
        signer.setImMembreCoSignataire("CTRMEM2");
        signer.setImCcCoSignataire("CTRCC1");
        pvExamenRepository.saveAll(List.of(accepter, viserHaut, signer));
        navette(7110, "RETOUR_CC", "Retour interne Y7");
        lettre(circuitDispatche(7111, "EXAMINE", "TMS", "CTRCC2", "CTRMEMT"), "SOUMIS");
        lettre(circuitDispatche(7112, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "SOUMIS");
        circuitAvecReception(7113, "PRET_DISPATCH", "TMS", "CTRSECT");
        retrait(7113);
        pvSigne(circuitDispatche(7114, "EN_VERIFICATION", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        pvSigne(circuitDispatche(7115, "OBSERVATIONS_LEVEES", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        pvSigne(circuitDispatche(7116, "DECISION_TRANSMISE_SIGMP", "ANT", "CTRPRE", "CTRMEM"), "FAV");
        lettre(7116, "SIGNE");
        lettre(circuitDispatche(7117, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "SIGNE");
        circuitAvecReception(7118, "EN_ATTENTE_COMPLEMENTS_DEPOT", "ANT", "CTRSEC");
        attente(7118, VENDREDI_14H);
        circuitDispatche(7119, "EN_ATTENTE_PIECES", "ANT", "CTRPRE", "CTRMEM");
        dossier(7120, "BROUILLON", "ANT");
        pvSigne(circuitDispatche(7121, "EN_ATTENTE_DECISION_PRMP", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        verification(7121);
        attente(7121, VENDREDI_14H.minusHours(4));
        circuitAvecReception(7122, "PRET_DISPATCH", "ANT", "CTRSEC");
        circuitAvecReception(7123, "DISPATCHE", "ANT", "CTRSEC");            // CC attributaire central : réattribuer
        dispatchRepository.save(dispatch(7123, 7123, null, "CTRCC1", "CTRPRE"));
        pv(circuitDispatche(7124, "EXAMINE", "ANT", "CTRPRE", "CTRCC1"), "PROJET_SOUMIS", "CTRCC1");   // intérim
        pvSigne(circuitDispatche(7125, "EN_VERIFICATION", "ANT", "CTRPRE", "CTRMEM2"), "FAV");       // sans cible
        pvSigne(circuitDispatche(7126, "EN_VERIFICATION", "TMS", "CTRCC2", "CTRMEMT"), "FAVR");
        pvSigne(circuitDispatche(7127, "DECISION_TRANSMISE_SIGMP", "TMS", "CTRCC2", "CTRMEMT"), "FAV");
        List<String> terminaux = List.of("CLOTURE", "RETIRE", "REMPLACE", "PV_SIGNE");
        for (int i = 0; i < terminaux.size(); i++) {
            lettre(circuitDispatche(7130 + i, terminaux.get(i), "ANT", "CTRPRE", "CTRMEM"), "SOUMIS");
            retrait(7130 + i);
        }
        Dossier etranger = circuitDispatche(7140, "DISPATCHE", "ANT", "CTRPRE", "CTRMEM");
        etranger.setIdPrmp("PRMP002");
        dossierRepository.save(etranger);
        Dossier consigne = circuitDispatche(7141, "DISPATCHE", "ANT", "CTRPRE", "CTRMEM2");
        var dispatch = dispatchRepository.findById(consigne.getIdDossier()).orElseThrow();
        dispatch.setInstructions("Consigne interne X9");
        dispatchRepository.save(dispatch);

        List<Integer> ids = new ArrayList<>(List.of(1, 2));
        for (int id = 7101; id <= 7127; id++) {
            ids.add(id);
        }
        ids.addAll(List.of(7130, 7131, 7132, 7133, 7140, 7141));
        return ids;
    }

    // ================================================================== outils

    private String jeton(String im, ProfilUtilisateur profil, String localite) {
        return bearer(im, profil, TypeActeur.CONTROLEUR, im, localite);
    }

    private String gestes(String jeton, int idDossier) throws Exception {
        return mvc.perform(get("/api/dossiers/" + idDossier + "/gestes").header("Authorization", jeton))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String aFaireAvecDelegations(String jeton) throws Exception {
        return mvc.perform(get("/api/dossiers/a-faire").param("delegations", "true").header("Authorization", jeton))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String messageConsultation(int idDossier, String jeton, int codeAttendu) throws Exception {
        MvcResult r = mvc.perform(get("/api/dossiers/" + idDossier).header("Authorization", jeton)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(codeAttendu);
        return JsonPath.read(r.getResponse().getContentAsString(), "$.message");
    }

    private static String filtreDossier(int idDossier) {
        return "[?(@.dossier.idDossier == " + idDossier + ")]";
    }

    /** Lignes sans leur rang : l'accueil numérote chaque liste à part, la page numérote toute la liste. */
    private static List<Map<String, Object>> sansRang(List<Map<String, Object>> taches) {
        List<Map<String, Object>> copies = new ArrayList<>(taches.size());
        for (Map<String, Object> t : taches) {
            Map<String, Object> copie = new LinkedHashMap<>(t);
            assertThat(copie).containsKey("rang");
            copie.remove("rang");
            copies.add(copie);
        }
        return copies;
    }

    private org.hibernate.stat.Statistics statistiquesRemisesAZero() {
        org.hibernate.stat.Statistics stats = entityManager.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        entityManager.flush();
        entityManager.clear();
        stats.clear();
        return stats;
    }

    /** Ordres SQL préparés pendant un appel, compteur Hibernate remis à zéro juste avant. */
    private long ordresSql(String jeton, int idDossier) throws Exception {
        org.hibernate.stat.Statistics stats = statistiquesRemisesAZero();
        gestes(jeton, idDossier);
        return stats.getPrepareStatementCount();
    }

    /** Contrôleurs chargés pendant un appel (l'annuaire en charge tous). */
    private long controleursCharges(String jeton, int idDossier) throws Exception {
        org.hibernate.stat.Statistics stats = statistiquesRemisesAZero();
        gestes(jeton, idDossier);
        return stats.getEntityStatistics(Controleur.class.getName()).getLoadCount();
    }

    private Dossier dossier(int id, String statut, String localite) {
        Dossier d = dossierLoc(id, statut, localite, "PRMP001");
        d.setIdEntiteContract(1);
        d.setDateSoumission("BROUILLON".equals(statut) ? null : JEUDI_10H);
        return dossierRepository.save(d);
    }

    /** Dossier, et sa réception par {@code imRecept} (qui fixe la localité du circuit). */
    private Dossier circuitAvecReception(int id, String statut, String localite, String imRecept) {
        Dossier d = dossier(id, statut, localite);
        receptionRepository.save(reception(id, id, imRecept, true));
        return d;
    }

    /** Dossier, réception (par un Secrétaire de la localité), dispatch et examen ({@code id} partout). */
    private Dossier circuitDispatche(int id, String statut, String localite, String dispatcheur, String attributaire) {
        Dossier d = circuitAvecReception(id, statut, localite, "ANT".equals(localite) ? "CTRSEC" : "CTRSECT");
        dispatchRepository.save(dispatch(id, id, null, attributaire, dispatcheur));
        examenRepository.save(examen(id, id, attributaire));
        return d;
    }

    private PvExamen pv(Dossier d, String statut, String examinateur) {
        PvExamen pv = new PvExamen();
        pv.setIdPv(d.getIdDossier());
        pv.setIdExamen(d.getIdDossier());
        pv.setIdAvis("FAV");
        pv.setImCtrlMembre(examinateur);
        pv.setStatutPv(statut);
        pv.setNbNavettes(1);
        return pvExamenRepository.save(pv);
    }

    private PvExamen pvSigne(Dossier d, String avis) {
        PvExamen pv = new PvExamen();
        pv.setIdPv(d.getIdDossier());
        pv.setIdExamen(d.getIdDossier());
        pv.setIdAvis(avis);
        pv.setImCtrlMembre("CTRMEM");
        pv.setStatutPv("SIGNE");
        pv.setImMembreCoSignataire("CTRMEM");
        pv.setDateSignatureMembre(LocalDate.of(2026, 9, 8));
        pv.setDateSignaturePresident(LocalDate.of(2026, 9, 8));
        pv.setNbNavettes(1);
        return pvExamenRepository.save(pv);
    }

    private int lettre(Dossier d, String statut) {
        return lettre(d.getIdDossier(), statut);
    }

    private int lettre(int idDossier, String statut) {
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(idDossier);
        l.setIdDossier(idDossier);
        l.setObjetLettre("Renvoi " + idDossier);
        l.setDateLettre(LocalDate.of(2026, 9, 9));
        l.setStatut(statut);
        return lettreRenvoiRepository.save(l).getIdLettre();
    }

    private void retrait(int idDossier) {
        DemandeRetrait dr = new DemandeRetrait();
        dr.setIdDossier(idDossier);
        dr.setIdPrmp("PRMP001");
        dr.setMotifRetrait("Motif " + idDossier);
        dr.setDateDemande(LocalDate.of(2026, 9, 7).atTime(9, 0));
        dr.setStatut("EN_ATTENTE");
        demandeRetraitRepository.save(dr);
    }

    private void navette(int idPv, String sens, String commentaire) {
        PvNavette n = new PvNavette();
        n.setIdNavette(pvNavetteRepository.nextIdNavette().intValue());
        n.setIdPv(idPv);
        n.setNumNavette(1);
        n.setSens(sens);
        n.setImActeur("CTRPRE");
        n.setDateAction(VENDREDI_14H);
        n.setCommentaire(commentaire);
        pvNavetteRepository.save(n);
    }

    private void passage(int idDossier, String etape, LocalDateTime fin, String acteur) {
        TacheDossier t = new TacheDossier();
        t.setIdTache(tacheRepository.nextId());
        t.setIdDossier(idDossier);
        t.setEtape(etape);
        t.setOccurrence(1);
        t.setImActeur(acteur);
        t.setProfil("SECRETAIRE");
        t.setDateFin(fin);
        tacheRepository.save(t);
    }

    /** Passage de vérification (réception et PV d'identifiant {@code idDossier}), observations non levées. */
    private void verification(int idDossier) {
        Verification passage = new Verification();
        passage.setIdReception(idDossier);
        passage.setIdPv(idDossier);
        passage.setImCtrlVerif("CTRVER");
        passage.setDateVerif(LocalDate.of(2026, 9, 11));
        passage.setObsLevees(false);
        verificationRepository.save(passage);
    }

    /** Fenêtre d'attente PRMP ouverte depuis {@code debut}, sur le statut courant du dossier. */
    private void attente(int idDossier, LocalDateTime debut) {
        SuspensionDossier attente = new SuspensionDossier();
        attente.setIdSuspension(suspensionRepository.nextId());
        attente.setIdDossier(idDossier);
        attente.setStatut(dossierRepository.findById(idDossier).orElseThrow().getStatut());
        attente.setDebut(debut);
        suspensionRepository.save(attente);
    }
}
