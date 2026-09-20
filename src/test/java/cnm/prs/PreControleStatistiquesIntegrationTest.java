package cnm.prs;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import cnm.prs.entity.Anomalie;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.enums.GraviteSignalement;
import cnm.prs.enums.SourceSignalement;
import cnm.prs.enums.StatutSignalement;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeSignalement;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.RegleAnomalieRepository;
import cnm.prs.seed.ReglesPreControleSeeder;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 7) — <strong>le taux d'écartement par
 * règle</strong>, la mesure qui dit si l'outil reste utile.
 *
 * <p>Ce que ces tests protègent : une règle massivement écartée est <strong>signalée comme suspecte</strong>
 * (c'est le seuil de 80 % du plan), un signalement <strong>levé n'est pas compté comme écarté</strong> — le
 * confondre ferait éteindre les règles qui marchent le mieux —, le tableau ne porte <strong>que des
 * compteurs</strong>, et il n'est ouvert qu'à l'Administrateur et au Président.</p>
 */
class PreControleStatistiquesIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER = 790;
    private static final int PPM = 790;

    @Autowired private ReglesPreControleSeeder seeder;
    @Autowired private AnomalieRepository anomalieRepository;
    @Autowired private RegleAnomalieRepository regleAnomalieRepository;

    @BeforeEach
    void signalementsDeReference() {
        Dossier d = dossier(DOSSIER, "SOUMIS");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        Ppm p = ppm(PPM, DOSSIER, "PRMP001");
        p.setExercice(2026);
        ppmRepository.save(p);
        seeder.run();
        entityManager.flush();

        // Une règle massivement écartée : 5 signalements, 4 écartés → 80 %, le seuil du plan.
        for (int i = 0; i < 5; i++) {
            enregistrer(TypeSignalement.MENTION_DELAI_REDUIT, i < 4
                    ? StatutSignalement.ECARTE : StatutSignalement.OUVERT, "MENTION|" + i);
        }
        // Une règle qui MARCHE : 4 signalements, 3 levés parce que la PRMP a corrigé son plan.
        for (int i = 0; i < 4; i++) {
            enregistrer(TypeSignalement.MODE_SOUS_LE_SEUIL, i < 3
                    ? StatutSignalement.LEVE_MODIFICATION : StatutSignalement.OUVERT, "MODE|" + i);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void enregistrer(TypeSignalement type, StatutSignalement statut, String cle) {
        RegleAnomalie regle = regleAnomalieRepository.findByCodeRegle(type.name()).orElseThrow();
        Anomalie a = new Anomalie();
        a.setIdAnomalie(anomalieRepository.nextIdAnomalie().intValue());
        a.setIdPpm(PPM);
        a.setIdRegleAnomalie(regle.getIdRegleAnomalie());
        a.setTypeAnomalie(type.name());
        a.setGravite(GraviteSignalement.A_VERIFIER.name());
        a.setSource(SourceSignalement.REGLE.name());
        a.setStatut(statut.name());
        a.setCleSignalement(cle);
        a.setDateDetection(LocalDateTime.of(2026, 9, 20, 9, 0));
        if (statut == StatutSignalement.ECARTE) {
            a.setImTraitement("PRMP001");
            a.setTypeActeurTraitement(TypeActeur.PRMP.name());
            a.setCommentaireTraitement("Mention déjà portée sur la fiche de présentation.");
            a.setDateTraitement(LocalDateTime.of(2026, 9, 20, 10, 0));
        }
        anomalieRepository.save(a);
    }

    @Test
    @DisplayName("La règle écartée dans 80 % des cas ressort en tête et marquée suspecte : c'est celle "
            + "qu'il faut revoir, ou éteindre")
    void regleMassivementEcartee_marqueeSuspecteEtEnTete() throws Exception {
        mvc.perform(get("/api/pre-controle/statistiques").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(9))
                .andExpect(jsonPath("$.ecartes").value(4))
                .andExpect(jsonPath("$.regles[0].code").value(TypeSignalement.MENTION_DELAI_REDUIT.name()))
                .andExpect(jsonPath("$.regles[0].total").value(5))
                .andExpect(jsonPath("$.regles[0].ecartes").value(4))
                .andExpect(jsonPath("$.regles[0].taux", closeTo(0.8, 0.001)))
                .andExpect(jsonPath("$.regles[0].suspecte").value(true))
                .andExpect(jsonPath("$.regles[0].actif").value(true));
    }

    @Test
    @DisplayName("Un signalement LEVÉ n'est pas un écartement : la règle qui fait corriger les plans n'est "
            + "jamais marquée suspecte — les confondre ferait éteindre celles qui marchent")
    void signalementLeve_nEstPasUnEcartement() throws Exception {
        mvc.perform(get("/api/pre-controle/statistiques").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regles[?(@.code == '" + TypeSignalement.MODE_SOUS_LE_SEUIL.name()
                        + "')].leves").value(3))
                .andExpect(jsonPath("$.regles[?(@.code == '" + TypeSignalement.MODE_SOUS_LE_SEUIL.name()
                        + "')].ecartes").value(0))
                .andExpect(jsonPath("$.regles[?(@.code == '" + TypeSignalement.MODE_SOUS_LE_SEUIL.name()
                        + "')].suspecte").value(false));
    }

    @Test
    @DisplayName("Toutes les règles sont servies, y compris celles qui n'ont encore rien produit, et les "
            + "pistes de l'assistant sont distinguées des faits")
    void toutesLesRegles_serviesEtDistinguees() throws Exception {
        mvc.perform(get("/api/pre-controle/statistiques").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regles.length()")
                        .value(TypeSignalement.values().length))
                .andExpect(jsonPath("$.regles[?(@.code == '"
                        + TypeSignalement.FRACTIONNEMENT_DEGUISE.name() + "')].source").value("IA"))
                .andExpect(jsonPath("$.regles[?(@.code == '"
                        + TypeSignalement.FRACTIONNEMENT_COMPTE.name() + "')].source").value("REGLE"))
                .andExpect(jsonPath("$.regles[?(@.code == '"
                        + TypeSignalement.LOTS_SOMME_DIVERGENTE.name() + "')].total").value(0));
    }

    @Test
    @DisplayName("Le filtre par exercice ne compte que cet exercice : une règle peut être bonne une année "
            + "et mauvaise la suivante")
    void filtreParExercice() throws Exception {
        mvc.perform(get("/api/pre-controle/statistiques?exercice=2026").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercice").value(2026))
                .andExpect(jsonPath("$.total").value(9));
        mvc.perform(get("/api/pre-controle/statistiques?exercice=2025").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("Le tableau de bord est ouvert à l'Administrateur et au Président — et fermé à tous les "
            + "autres, PRMP comprise")
    void perimetre_administrateurEtPresidentSeulement() throws Exception {
        mvc.perform(get("/api/pre-controle/statistiques").header("Authorization", tokenPresident))
                .andExpect(status().isOk());
        mvc.perform(get("/api/pre-controle/statistiques").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pre-controle/statistiques").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pre-controle/statistiques").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
    }
}
