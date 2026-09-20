package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import cnm.prs.entity.ActionDossier;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Ppm;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.repository.ActionDossierRepository;
import cnm.prs.service.OutilsDossierIa;
import cnm.prs.service.OutilsDossierIa.Faits;

/**
 * ⚠️ Assistant IA, lot 2 (2026-09-20, étape 1) — <strong>l'étanchéité de la liste blanche</strong>.
 *
 * <p>C'est le premier lot où l'assistant touche une donnée métier, et « le risque réel n'est pas le
 * modèle mais l'étanchéité des habilitations » : l'audit du 2026-09-14 avait classé <strong>critique</strong>
 * une fuite de données PRMP. Ces tests sont la contrepartie de la règle du §2 du plan — ils prouvent
 * qu'elle tient, <strong>sans une ligne de code de sécurité</strong> dans l'assistant lui-même.</p>
 *
 * <p>Ils appellent {@link OutilsDossierIa} <strong>directement</strong>, contexte de sécurité posé à la
 * main : c'est exactement ainsi qu'il s'exécutera en production — dans le fil de la requête, sous
 * l'identité de l'utilisateur. Un appel depuis un pool de calcul n'aurait pas d'utilisateur, donc pas de
 * garde ; c'est le piège que cette étape écarte.</p>
 */
class AssistantIaOutilsIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER_ANT = 820;
    private static final int PPM_ANT = 820;
    private static final int DOSSIER_TOA = 821;

    @Autowired private OutilsDossierIa outils;
    @Autowired private ActionDossierRepository actionRepository;

    @BeforeEach
    void deuxDossiersDansDeuxCommissions() {
        localiteRepository.save(localite("TOA", "Toamasina"));
        prmpRepository.save(prmp("PRMP002", "TOA"));

        dossierSoumis(DOSSIER_ANT, "PRMP001", "ANT");
        dossierSoumis(DOSSIER_TOA, "PRMP002", "TOA");

        Ppm p = ppm(PPM_ANT, DOSSIER_ANT, "PRMP001");
        p.setReference("00820/MTP/PPM/2026");
        p.setIdLocalite("ANT");
        p.setSignataire("Hery Fanomezana Rasoanaivo");
        p.setDateSignature(LocalDate.of(2026, 1, 10));
        ppmRepository.save(p);

        action(DOSSIER_ANT, "CREATION", LocalDateTime.of(2026, 6, 1, 9, 0));
        action(DOSSIER_ANT, "SOUMISSION", LocalDateTime.of(2026, 6, 2, 10, 30));
        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void nettoyerContexte() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ fixture

    private void dossierSoumis(int id, String idPrmp, String localite) {
        Dossier d = dossier(id, "SOUMIS");
        d.setIdTypeDossier("DDP");
        d.setIdSousType("PPM");
        d.setIdPrmp(idPrmp);
        d.setIdLocalite(localite);
        d.setDateSoumission(LocalDateTime.of(2026, 6, 2, 10, 30));
        dossierRepository.save(d);
    }

    private void action(int idDossier, String type, LocalDateTime quand) {
        ActionDossier a = new ActionDossier();
        a.setIdDossier(idDossier);
        a.setTypeAction(type);
        a.setDateAction(quand);
        a.setNomOperateur("Rasoanaivo Hery Fanomezana");
        a.setAuteur("PRMP001");
        actionRepository.save(a);
    }

    /**
     * Pose le contexte de sécurité comme le ferait une requête authentifiée : la claim {@code role}
     * devient l'autorité {@code ROLE_<profil>}, sans quoi aucun {@code @PreAuthorize} ne passerait.
     */
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

    private List<String> titres(Faits faits) {
        return faits.sections().stream().map(OutilsDossierIa.Section::titre).toList();
    }

    // ------------------------------------------------------------------ 1. la porte

    @Test
    @DisplayName("Un contrôleur d'une AUTRE commission n'obtient rien : la lecture du dossier est la porte, "
            + "et son refus interrompt la synthèse avant toute autre lecture")
    void controleurDUneAutreCommission_refuse() {
        authentifier("MEMTOA1", ProfilUtilisateur.MEMBRE, "MEMTOA1", "TOA");

        assertThatThrownBy(() -> outils.lire(DOSSIER_ANT)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Une PRMP n'obtient rien sur le dossier d'une AUTRE PRMP — la fuite que l'audit du "
            + "2026-09-14 avait classée critique ne peut pas se rejouer par l'assistant")
    void autrePrmp_refusee() {
        authentifier("PRMP002", ProfilUtilisateur.PRMP, "PRMP002", null);

        assertThatThrownBy(() -> outils.lire(DOSSIER_ANT)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Un dossier qui n'existe pas ne rend pas une synthèse vide : il rend l'erreur du serveur")
    void dossierInexistant_remonteLErreur() {
        authentifier("MEMANT1", ProfilUtilisateur.MEMBRE, "MEMANT1", "ANT");

        assertThatThrownBy(() -> outils.lire(999_999)).isInstanceOf(RuntimeException.class);
    }

    // ------------------------------------------------------------------ 2. ce que chaque profil obtient

    @Test
    @DisplayName("⚠️ La synthèse d'une PRMP ne porte JAMAIS le journal du circuit : c'est une vue interne "
            + "à la CNM (audit 2026-09-14, C2), et la liste blanche le constate sans le savoir")
    void prmp_naPasLeJournalInterne() {
        authentifier("PRMP001", ProfilUtilisateur.PRMP, "PRMP001", null);

        Faits faits = outils.lire(DOSSIER_ANT);

        assertThat(titres(faits)).contains("Le dossier").doesNotContain("Journal du circuit");
        // La lecture refusée est NOMMÉE : elle part au journal d'audit, elle ne disparaît pas en silence.
        assertThat(faits.outilsRefuses()).contains("journal du circuit");
        assertThat(faits.materiau()).doesNotContain("SOUMISSION", "CREATION");
    }

    @Test
    @DisplayName("Le contrôleur de la commission, lui, obtient le journal du circuit avec ses gestes datés")
    void controleurDeLaCommission_aLeJournal() {
        authentifier("MEMANT1", ProfilUtilisateur.MEMBRE, "MEMANT1", "ANT");

        Faits faits = outils.lire(DOSSIER_ANT);

        assertThat(titres(faits)).contains("Journal du circuit");
        assertThat(faits.materiau()).contains("SOUMISSION", "02/06/2026");
        assertThat(faits.outilsLus()).contains("dossier", "journal du circuit");
    }

    @Test
    @DisplayName("La PRMP propriétaire obtient son dossier et son plan de passation, avec la référence et "
            + "le signataire — et le statut y est dit en français, pas en code")
    void prmpProprietaire_aSonDossierEtSonPlan() {
        authentifier("PRMP001", ProfilUtilisateur.PRMP, "PRMP001", null);

        Faits faits = outils.lire(DOSSIER_ANT);

        assertThat(faits.reference()).isEqualTo("DOS-" + DOSSIER_ANT);
        assertThat(titres(faits)).contains("Le dossier", "Plan de passation");
        assertThat(faits.materiau())
                .contains("initial — soumis, en attente de réception")
                .contains("00820/MTP/PPM/2026")
                .contains("Hery Fanomezana Rasoanaivo")
                .doesNotContain("SOUMIS,");   // le code brut du statut ne doit pas fuiter dans le texte
    }

    // ------------------------------------------------------------------ 3. le matériau du modèle

    @Test
    @DisplayName("⚠️ Le matériau remis au modèle ne contient RIEN d'autre que les sections servies à "
            + "l'écran : c'est ce qui permet de montrer à l'utilisateur ce que l'assistant a lu")
    void materiau_estExactementCeQueLEcranMontre() {
        authentifier("MEMANT1", ProfilUtilisateur.MEMBRE, "MEMANT1", "ANT");

        Faits faits = outils.lire(DOSSIER_ANT);
        String materiau = faits.materiau();

        assertThat(faits.vide()).isFalse();
        for (OutilsDossierIa.Section section : faits.sections()) {
            assertThat(materiau).contains(section.titre());
            for (String ligne : section.lignes()) {
                assertThat(materiau).contains(ligne);
            }
        }
        // Et l'inverse : pas une ligne du matériau qui ne vienne d'une section.
        long lignesDeSections = faits.sections().stream().mapToLong(s -> s.lignes().size()).sum();
        long lignesDuMateriau = materiau.lines().filter(l -> l.startsWith("- ")).count();
        assertThat(lignesDuMateriau).isEqualTo(lignesDeSections);
    }

    @Test
    @DisplayName("Un journal de vingt gestes est borné à douze, et le reste est COMPTÉ : un modèle noyé "
            + "ne dit pas qu'il n'a pas tout lu, il répond à côté (leçon du lot 3)")
    void journalLong_borneEtCompte() {
        for (int i = 0; i < 20; i++) {
            action(DOSSIER_ANT, "MISE_A_JOUR", LocalDateTime.of(2026, 6, 3, 8, 0).plusHours(i));
        }
        entityManager.flush();
        entityManager.clear();
        authentifier("MEMANT1", ProfilUtilisateur.MEMBRE, "MEMANT1", "ANT");

        Faits faits = outils.lire(DOSSIER_ANT);

        List<String> journal = faits.sections().stream()
                .filter(s -> "Journal du circuit".equals(s.titre()))
                .findFirst().orElseThrow().lignes();
        assertThat(journal).hasSize(13);
        assertThat(journal.get(12)).contains("actions plus anciennes");
    }
}
