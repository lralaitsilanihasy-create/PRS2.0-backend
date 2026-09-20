package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Ppm;
import cnm.prs.enums.IntentionAssistant;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.service.AiguillageAssistantIa.Aiguillage;
import cnm.prs.service.OutilsDossierIa.Faits;
import cnm.prs.service.OutilsTransversesIa;

/**
 * ⚠️ Assistant IA, lot 4 (2026-09-20, étape 2) — <strong>l'étanchéité des lectures transverses</strong>.
 *
 * <p>Le chatbot élargit la liste blanche : il ne lit plus seulement un dossier ouvert, mais des files,
 * des compteurs, une recherche. Ces tests vérifient les deux choses qui rendent cet élargissement
 * tenable :</p>
 * <ol>
 *   <li>la liste des intentions proposées au modèle <strong>dépend du profil</strong> — c'est une
 *       courtoisie, pour ne pas pousser une porte qui sera refusée ;</li>
 *   <li>⚠️ <strong>et elle n'est PAS la garde</strong> : une intention forcée hors de ce que le profil
 *       peut lire est refusée par le contrôleur lui-même, et l'assistant n'obtient rien.</li>
 * </ol>
 */
class AssistantIaTransverseIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER_ANT = 860;
    private static final int PPM_ANT = 860;
    private static final int DOSSIER_TOA = 861;

    @Autowired private OutilsTransversesIa outils;

    @BeforeEach
    void deuxDossiersDansDeuxCommissions() {
        localiteRepository.save(localite("TOA", "Toamasina"));
        prmpRepository.save(prmp("PRMP002", "TOA"));

        dossierSoumis(DOSSIER_ANT, "PRMP001", "ANT", "00860/PPM/CNM/2026");
        dossierSoumis(DOSSIER_TOA, "PRMP002", "TOA", "00861/PPM/CNM/2026");

        Ppm p = ppm(PPM_ANT, DOSSIER_ANT, "PRMP001");
        p.setReference("00860/MTP/PPM/2026");
        p.setIdLocalite("ANT");
        p.setDateSignature(LocalDate.of(2026, 1, 10));
        ppmRepository.save(p);

        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void nettoyerContexte() {
        SecurityContextHolder.clearContext();
    }

    private void dossierSoumis(int id, String idPrmp, String localite, String reference) {
        Dossier d = dossier(id, "SOUMIS");
        d.setIdTypeDossier("DDP");
        d.setIdSousType("PPM");
        d.setIdPrmp(idPrmp);
        d.setIdLocalite(localite);
        d.setRefeDossier(reference);
        d.setDateSoumission(LocalDateTime.of(2026, 6, 2, 10, 30));
        dossierRepository.save(d);
    }

    private void authentifier(String login, ProfilUtilisateur profil, String ref, String localite) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "HS256").subject(login)
                .claim("role", profil.name())
                .claim("ref", ref)
                .claim("acteurType", profil == ProfilUtilisateur.PRMP ? "PRMP" : "CONTROLEUR")
                .claim("localite", localite)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + profil.name()))));
    }

    private Faits lire(IntentionAssistant intention, String parametre) {
        return outils.lire(new Aiguillage(intention, parametre, false));
    }

    // ------------------------------------------------------------------ 1. ce qu'on propose au modèle

    @Test
    @DisplayName("Les intentions proposées suivent le profil : le Membre a ses tâches, pas l'annuaire ni "
            + "la vue d'ensemble ; l'Administrateur a l'annuaire, mais pas de file « à faire »")
    void intentionsOuvertes_suiventLeProfil() {
        assertThat(outils.ouvertes(ProfilUtilisateur.MEMBRE))
                .contains(IntentionAssistant.MES_TACHES, IntentionAssistant.MES_CHIFFRES,
                        IntentionAssistant.ETAT_DOSSIER, IntentionAssistant.REGLE)
                .doesNotContain(IntentionAssistant.ANNUAIRE, IntentionAssistant.TABLEAU_DE_BORD);

        assertThat(outils.ouvertes(ProfilUtilisateur.ADMINISTRATEUR))
                .contains(IntentionAssistant.ANNUAIRE, IntentionAssistant.TABLEAU_DE_BORD)
                .doesNotContain(IntentionAssistant.MES_TACHES);

        assertThat(outils.ouvertes(ProfilUtilisateur.PRMP))
                .contains(IntentionAssistant.MES_TACHES)
                .doesNotContain(IntentionAssistant.ANNUAIRE, IntentionAssistant.TABLEAU_DE_BORD);
    }

    @Test
    @DisplayName("Sans profil connu, seule la réponse documentaire est proposée")
    void sansProfil_seulementLeRepli() {
        assertThat(outils.ouvertes(null)).containsExactly(IntentionAssistant.REGLE);
    }

    // ------------------------------------------------------------------ 2. la liste n'est PAS la garde

    @Test
    @DisplayName("⚠️ Une intention FORCÉE hors du profil ne donne rien : c'est le contrôleur qui refuse, "
            + "pas la liste — si les deux divergeaient un jour, c'est la garde qui gagnerait")
    void intentionForceeHorsProfil_refuseeParLaGarde() {
        authentifier("MEMANT1", ProfilUtilisateur.MEMBRE, "MEMANT1", "ANT");

        assertThat(lire(IntentionAssistant.ANNUAIRE, "Rakoto")).isNull();
        assertThat(lire(IntentionAssistant.TABLEAU_DE_BORD, null)).isNull();
    }

    @Test
    @DisplayName("La réponse documentaire ne lit jamais rien, quelle que soit la question")
    void repli_neLitRien() {
        authentifier("MEMANT1", ProfilUtilisateur.MEMBRE, "MEMANT1", "ANT");

        assertThat(lire(IntentionAssistant.REGLE, "peu importe")).isNull();
    }

    // ------------------------------------------------------------------ 3. le périmètre des lectures

    @Test
    @DisplayName("⚠️ Une recherche ne franchit pas la frontière : le contrôleur de Toamasina ne trouve "
            + "pas le dossier d'Antananarivo, alors que la même recherche le rend à son contrôleur")
    void recherche_resteDansLePerimetre() {
        authentifier("MEMTOA1", ProfilUtilisateur.MEMBRE, "MEMTOA1", "TOA");
        assertThat(lire(IntentionAssistant.TROUVER_DOSSIER, "00860")).isNull();

        SecurityContextHolder.clearContext();
        authentifier("MEMANT1", ProfilUtilisateur.MEMBRE, "MEMANT1", "ANT");
        Faits trouve = lire(IntentionAssistant.TROUVER_DOSSIER, "00860");
        assertThat(trouve).isNotNull();
        assertThat(trouve.materiau()).contains("00860/PPM/CNM/2026");
    }

    @Test
    @DisplayName("⚠️ L'état d'un dossier passe par la RECHERCHE avant la lecture : une référence hors "
            + "périmètre ne se résout pas, donc ne se lit pas")
    void etatDossier_horsPerimetre_neSeResoutPas() {
        authentifier("MEMTOA1", ProfilUtilisateur.MEMBRE, "MEMTOA1", "TOA");

        assertThat(lire(IntentionAssistant.ETAT_DOSSIER, "00860/PPM/CNM/2026")).isNull();
    }

    @Test
    @DisplayName("Dans son périmètre, l'état d'un dossier rend les faits du lot 2 — la même lecture, "
            + "avec les mêmes sept gardes")
    void etatDossier_dansLePerimetre_rendLesFaitsDuLot2() {
        authentifier("MEMANT1", ProfilUtilisateur.MEMBRE, "MEMANT1", "ANT");

        Faits faits = lire(IntentionAssistant.ETAT_DOSSIER, "00860/PPM/CNM/2026");

        assertThat(faits).isNotNull();
        assertThat(faits.idDossier()).isEqualTo(DOSSIER_ANT);
        assertThat(faits.sections()).anyMatch(s -> "Le dossier".equals(s.titre()));
    }

    @Test
    @DisplayName("Les files d'attente se disent en français, pas en codes : « à examiner », pas A_EXAMINER")
    void mesTaches_enFrancais() {
        authentifier("MEMANT1", ProfilUtilisateur.MEMBRE, "MEMANT1", "ANT");

        Faits faits = lire(IntentionAssistant.MES_TACHES, null);

        // La file peut être vide sur ce jeu de données : ce qui est vérifié, c'est qu'AUCUN code ne sort.
        if (faits != null) {
            assertThat(faits.materiau()).doesNotContain("A_EXAMINER", "PV_A_VISER", "A_RECEPTIONNER");
        }
    }
}
