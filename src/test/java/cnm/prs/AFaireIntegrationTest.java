package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

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
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.SuspensionDossierRepository;
import cnm.prs.repository.TacheDossierRepository;
import cnm.prs.service.DelaiStandardService;
import cnm.prs.service.HeuresOuvrees;
import cnm.prs.service.ReglesAFaire;

/**
 * ⚠️ <strong>Accueil « À faire »</strong> — {@code GET /api/dossiers/a-faire} (demande front du 2026-09-14,
 * « Tests attendus » 1 à 11 ; arbitrages du 2026-09-15).
 *
 * <p><strong>Horloge figée</strong> au lundi 2026-09-14 15:00, l'instant de l'exemple du contrat : l'écoulé se compte
 * en heures pleines et les bornes « bientôt » / « en retard » se testent à l'heure près. Contexte Spring distinct du
 * socle (bean {@code Clock} remplacé), comme {@code DelaiCourantConcordanceIntegrationTest}.</p>
 *
 * <p>Le socle porte déjà deux dossiers et une demande de retrait : les assertions portent sur les dossiers créés
 * ici (identifiants 7000 et au-delà), jamais sur des totaux absolus — sauf les invariants, vrais quel que soit le
 * décor.</p>
 */
class AFaireIntegrationTest extends CnmIntegrationTestSupport {

    private static final LocalDateTime MAINTENANT = LocalDate.of(2026, 9, 14).atTime(15, 0);
    private static final LocalDateTime JEUDI_10H = LocalDate.of(2026, 9, 10).atTime(10, 5);
    private static final LocalDateTime VENDREDI_14H = LocalDate.of(2026, 9, 11).atTime(14, 0);

    @TestConfiguration
    static class HorlogeLundi {
        // Nom de bean différent de `clock` (ClockConfig) : @Primary départage l'injection par type.
        @Bean
        @Primary
        Clock horlogeDeLAccueilAFaire() {
            return new HorlogeMutable(MAINTENANT.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired private TacheDossierRepository tacheRepository;
    @Autowired private SuspensionDossierRepository suspensionRepository;
    @Autowired private DelaiStandardService delaiStandardService;

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
        tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", null);
    }

    // ================================================================== 1 — chaque ligne du tableau §3

    @Test
    @DisplayName("1a — Réception, dispatch régional, examen, réexamen : présents chez le titulaire, absents chez un pair")
    void titulaires_receptionDispatchExamen() throws Exception {
        dossier(7101, "SOUMIS", "ANT");
        circuitAvecReception(7102, "PRET_DISPATCH", "TMS", "CTRSECT");
        circuitDispatche(7103, "DISPATCHE", "ANT", "CTRPRE", "CTRMEM");
        circuitDispatche(7104, "A_REEXAMINER", "ANT", "CTRPRE", "CTRMEM");

        assertLigne(aFaire(tokenSec), 7101, "A_RECEPTIONNER", "NUMEROTER");
        assertAucuneLigne(aFaire(tokenSecTms), 7101);
        assertLigne(aFaire(tokenCcTms), 7102, "A_DISPATCHER", "DISPATCHER");
        assertAucuneLigne(aFaire(tokenCc), 7102);
        String membre = aFaire(tokenMembre);
        assertLigne(membre, 7103, "A_EXAMINER", "EXAMINER");
        assertLigne(membre, 7104, "A_REEXAMINER", "REEXAMINER");
        String pair = aFaire(tokenMembre2);
        assertAucuneLigne(pair, 7103);
        assertAucuneLigne(pair, 7104);

        // Arbitrage 3 : le CC attributaire d'un dossier central dont l'examen n'est pas entamé réattribue d'abord.
        circuitAvecReception(7190, "DISPATCHE", "ANT", "CTRSEC");
        dispatchRepository.save(dispatch(7190, 7190, null, "CTRCC1", "CTRPRE"));
        String cc = aFaire(tokenCc);
        assertLigne(cc, 7190, "A_EXAMINER", "REATTRIBUER");
        assertThat(JsonPath.<List<List<String>>>read(cc, filtre(7190) + ".gestesSecondaires").get(0))
                .containsExactly("EXAMINER");
        assertThat(JsonPath.<List<Boolean>>read(cc, filtre(7190) + ".faits.examenEntame")).containsExactly(false);
    }

    @Test
    @DisplayName("1b — Projet de PV à soumettre, à reprendre, à accepter, à viser, à signer : titulaire seul")
    void titulaires_navetteEtSignature() throws Exception {
        pv(circuitDispatche(7105, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "BROUILLON", "CTRMEM");
        pv(circuitDispatche(7106, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "EN_RECTIFICATION", "CTRMEM");
        PvExamen accepter = pv(circuitDispatche(7107, "EXAMINE", "ANT", "CTRCC1", "CTRMEM"), "PROJET_SOUMIS", "CTRMEM");
        accepter.setNiveauNavette("CC");
        PvExamen viserHaut = pv(circuitDispatche(7108, "EXAMINE", "ANT", "CTRCC1", "CTRMEM"), "PROJET_SOUMIS", "CTRMEM");
        viserHaut.setNiveauNavette("PRESIDENT");
        pv(circuitDispatche(7109, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "PROJET_SOUMIS", "CTRMEM");
        PvExamen signer = pv(circuitDispatche(7110, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "PROJET_ACCEPTE", "CTRMEM");
        signer.setDateSignaturePresident(LocalDate.of(2026, 9, 11));
        signer.setImMembreCoSignataire("CTRMEM2");
        pvExamenRepository.saveAll(List.of(accepter, viserHaut, signer));

        String membre = aFaire(tokenMembre);
        assertLigne(membre, 7105, "PV_A_SOUMETTRE", "SOUMETTRE_PV");
        assertLigne(membre, 7106, "PV_A_REPRENDRE", "REPRENDRE_EXAMEN");
        assertAucuneLigne(membre, 7110);                     // examinateur non désigné : rien à signer
        String pair = aFaire(tokenMembre2);
        assertAucuneLigne(pair, 7105);
        assertAucuneLigne(pair, 7106);
        assertLigne(pair, 7110, "PV_A_SIGNER", "SIGNER");    // le désigné signe

        String cc = aFaire(tokenCc);
        assertLigne(cc, 7107, "PV_A_ACCEPTER", "ACCEPTER");
        assertThat(JsonPath.<List<List<String>>>read(cc, filtre(7107) + ".gestesSecondaires").get(0))
                .containsExactly("RETOURNER");
        assertAucuneLigne(cc, 7108);
        assertAucuneLigne(aFaire(tokenCc3), 7107);           // autre CC de la centrale : l'étage n'est pas le sien
        assertAucuneLigne(aFaire(tokenCcTms), 7107);

        String president = aFaire(tokenPresident);
        assertLigne(president, 7108, "PV_A_VISER", "VISER");
        assertLigne(president, 7109, "PV_A_VISER", "VISER");
        assertAucuneLigne(president, 7107);
        assertAucuneLigne(aFaire(tokenCcTms), 7109);
    }

    @Test
    @DisplayName("1c — Lettres à signer (régional : CC de la localité ; central : CC et Président) et retraits à décider")
    void titulaires_lettresEtRetraits() throws Exception {
        int regionale = lettre(circuitDispatche(7111, "EXAMINE", "TMS", "CTRCC2", "CTRMEMT"), "SOUMIS");
        int centrale = lettre(circuitDispatche(7112, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "SOUMIS");
        circuitAvecReception(7113, "PRET_DISPATCH", "TMS", "CTRSECT");
        retrait(7113);

        String ccTms = aFaire(tokenCcTms);
        assertLigne(ccTms, 7111, "LETTRES_A_SIGNER", "SIGNER_LETTRE");
        assertThat(JsonPath.<List<Integer>>read(ccTms, filtre(7111) + ".refs.idLettre")).containsExactly(regionale);
        assertThat(JsonPath.<List<String>>read(ccTms, filtre(7111) + ".urgence")).containsExactly("SANS_DELAI");
        assertAucuneLigne(ccTms, 7112);
        assertLigne(ccTms, 7113, "RETRAITS_A_DECIDER", "DECIDER_RETRAIT");

        String cc = aFaire(tokenCc);
        assertAucuneLigne(cc, 7111);
        assertLigne(cc, 7112, "LETTRES_A_SIGNER", "SIGNER_LETTRE");
        assertAucuneLigne(cc, 7113);

        String president = aFaire(tokenPresident);
        assertAucuneSection(president, 7111, "LETTRES_A_SIGNER");
        assertLigne(president, 7112, "LETTRES_A_SIGNER", "SIGNER_LETTRE");
        assertThat(JsonPath.<List<Integer>>read(president, filtre(7112) + ".refs.idLettre")).containsExactly(centrale);
        assertLigne(president, 7113, "RETRAITS_A_DECIDER", "DECIDER_RETRAIT");
    }

    @Test
    @DisplayName("1d — Vérification, transmission SIGMP, archivage du PV et de la lettre, attentes PRMP : titulaire seul")
    void titulaires_verificationArchivageAttentes() throws Exception {
        pvSigne(circuitDispatche(7114, "EN_VERIFICATION", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        pvSigne(circuitDispatche(7115, "OBSERVATIONS_LEVEES", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        pvSigne(circuitDispatche(7116, "DECISION_TRANSMISE_SIGMP", "ANT", "CTRPRE", "CTRMEM"), "FAV");
        lettre(circuitDispatche(7117, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "SIGNE");
        circuitAvecReception(7118, "EN_ATTENTE_COMPLEMENTS_DEPOT", "ANT", "CTRSEC");
        circuitDispatche(7119, "EN_ATTENTE_PIECES", "ANT", "CTRPRE", "CTRMEM");

        String ver = aFaire(tokenVer);
        assertLigne(ver, 7114, "A_VERIFIER", "VERIFIER");
        assertLigne(ver, 7115, "A_TRANSMETTRE_SIGMP", "TRANSMETTRE_SIGMP");
        String verTms = aFaire(tokenVerTms);
        assertAucuneLigne(verTms, 7114);
        assertAucuneLigne(verTms, 7115);

        String ass = aFaire(tokenAss);
        assertLigne(ass, 7116, "A_ARCHIVER", "ARCHIVER_PV");
        assertLigne(ass, 7117, "LETTRES_A_ARCHIVER", "ARCHIVER_LETTRE");
        String assTms = aFaire(tokenAssTms);
        assertAucuneLigne(assTms, 7116);
        assertAucuneLigne(assTms, 7117);

        String sec = aFaire(tokenSec);
        assertLigne(sec, 7118, "EN_ATTENTE_PRMP", "VOIR");
        assertThat(JsonPath.<List<String>>read(sec, filtre(7118) + ".urgence")).containsExactly("EN_PAUSE");
        assertAucuneLigne(aFaire(tokenSecTms), 7118);
        assertLigne(aFaire(tokenMembre), 7119, "EN_ATTENTE_PRMP", "VOIR");
        assertAucuneLigne(aFaire(tokenMembre2), 7119);
    }

    @Test
    @DisplayName("1e — PRMP et UGPM : brouillons (soumettre / compléter), pièces, compléments, rectification, suivi ; "
            + "rien chez une autre PRMP")
    void titulaires_partieControlee() throws Exception {
        dossier(7120, "BROUILLON", "ANT");
        circuitAvecReception(7118, "EN_ATTENTE_COMPLEMENTS_DEPOT", "ANT", "CTRSEC");
        circuitDispatche(7119, "EN_ATTENTE_PIECES", "ANT", "CTRPRE", "CTRMEM");
        pvSigne(circuitDispatche(7121, "EN_ATTENTE_DECISION_PRMP", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        circuitDispatche(7103, "DISPATCHE", "ANT", "CTRPRE", "CTRMEM");

        String prmp = aFaire(tokenPrmp);
        assertLigne(prmp, 7120, "BROUILLONS", "SOUMETTRE");
        assertLigne(prmp, 7118, "PIECES_DEPOT_A_COMPLETER", "COMPLETER_PIECES_DEPOT");
        assertLigne(prmp, 7119, "COMPLEMENTS_A_TRANSMETTRE", "TRANSMETTRE_COMPLEMENTS");
        assertLigne(prmp, 7121, "A_RECTIFIER", "RECTIFIER");
        assertLigne(prmp, 7103, "EN_COURS_CNM", "SUIVRE");
        assertThat(JsonPath.<List<String>>read(prmp, filtre(7120) + ".urgence")).containsExactly("HORS_DELAI");
        assertThat(JsonPath.<List<String>>read(prmp, filtre(7103) + ".urgence")).containsExactly("SUIVI");

        String ugpm = aFaire(tokenUgpm);
        assertLigne(ugpm, 7120, "BROUILLONS", "COMPLETER_BROUILLON");
        assertLigne(ugpm, 7103, "EN_COURS_CNM", "SUIVRE");
        assertAucuneLigne(ugpm, 7121);   // rectifier et transmettre restent à la PRMP

        String autre = aFaire(tokenPrmp2);
        for (int id : new int[] {7118, 7119, 7120, 7121, 7103}) {
            assertAucuneLigne(autre, id);
        }
    }

    // ================================================================== 2 — pré-dispatch central / régional

    @Test
    @DisplayName("2 — PRET_DISPATCH central : absent chez le CC central, titulaire chez le Président ; régional : "
            + "titulaire chez son CC, suppléance chez le Président")
    void preDispatch_centralEtRegional() throws Exception {
        circuitAvecReception(7201, "PRET_DISPATCH", "ANT", "CTRSEC");
        circuitAvecReception(7202, "PRET_DISPATCH", "TMS", "CTRSECT");

        String ccCentral = aFaireAvecDelegations(tokenCc);
        assertAucuneLigne(ccCentral, 7201);
        assertAucuneDelegation(ccCentral, 7201);

        String president = aFaireAvecDelegations(tokenPresident);
        assertLigne(president, 7201, "A_DISPATCHER", "DISPATCHER");
        assertAucuneLigne(president, 7202);
        assertDelegation(president, 7202, "A_DISPATCHER", "SUPPLEANCE");

        assertLigne(aFaire(tokenCcTms), 7202, "A_DISPATCHER", "DISPATCHER");
    }

    // ================================================================== 3 — navette simple et deux niveaux

    @Test
    @DisplayName("3 — Navette simple : le dispatcheur vise, l'examinateur non dispatcheur n'a rien (même en intérim), "
            + "un autre CC de la localité est en INTERIM ; deux niveaux : CC accepte au niveau nul, Président vise en haut")
    void navette_simpleEtDeuxNiveaux() throws Exception {
        // Navette simple : le Président dispatche au CC, qui examine.
        pv(circuitDispatche(7301, "EXAMINE", "ANT", "CTRPRE", "CTRCC1"), "PROJET_SOUMIS", "CTRCC1");
        // Deux niveaux, niveau nul (PV soumis avant V17).
        pv(circuitDispatche(7302, "EXAMINE", "ANT", "CTRCC1", "CTRMEM"), "PROJET_SOUMIS", "CTRMEM");
        // Deux niveaux, étage du Président.
        PvExamen haut = pv(circuitDispatche(7303, "EXAMINE", "ANT", "CTRCC1", "CTRMEM"), "PROJET_SOUMIS", "CTRMEM");
        haut.setNiveauNavette("PRESIDENT");
        pvExamenRepository.save(haut);

        String president = aFaireAvecDelegations(tokenPresident);
        assertLigne(president, 7301, "PV_A_VISER", "VISER");
        assertThat(JsonPath.<List<String>>read(president, filtre(7301) + ".mode")).containsExactly("TITULAIRE");
        assertAucuneLigne(president, 7302);
        assertAucuneDelegation(president, 7302);
        assertLigne(president, 7303, "PV_A_VISER", "VISER");

        String examinateur = aFaireAvecDelegations(tokenCc);
        assertAucuneSection(examinateur, 7301, "PV_A_VISER");
        assertAucuneDelegation(examinateur, 7301);
        assertLigne(examinateur, 7302, "PV_A_ACCEPTER", "ACCEPTER");
        assertAucuneLigne(examinateur, 7303);
        assertAucuneDelegation(examinateur, 7303);

        String autreCc = aFaireAvecDelegations(tokenCc3);
        assertAucuneLigne(autreCc, 7301);
        assertDelegation(autreCc, 7301, "PV_A_VISER", "INTERIM");
        assertAucuneDelegation(autreCc, 7303);   // pas d'intérim à deux niveaux
    }

    // ================================================================== 4 — paire de délégation

    @Test
    @DisplayName("4 — Désactiver la paire PRESIDENT → SECRETAIRE retire les réceptions du bloc délégation ; le bloc ne "
            + "compte jamais dans les compteurs")
    void delegation_paireDesactivee() throws Exception {
        dossier(7401, "SOUMIS", "ANT");

        String avant = aFaireAvecDelegations(tokenPresident);
        assertAucuneLigne(avant, 7401);
        assertDelegation(avant, 7401, "A_RECEPTIONNER", "DELEGATION");
        assertThat(JsonPath.<List<String>>read(avant, "$.delegations.parSection[*].code")).contains("A_RECEPTIONNER");
        Map<String, Object> compteursAvant = JsonPath.read(avant, "$.compteurs");
        assertThat(JsonPath.<Integer>read(avant, "$.delegations.total")).isPositive();

        var paire = delegationProfilRepository.findById(1).orElseThrow();   // Président (2) → Secrétaire (4)
        assertThat(paire.getIdProfileDelegant()).isEqualTo(2);
        assertThat(paire.getIdProfileDelegue()).isEqualTo(4);
        paire.setActif(false);
        delegationProfilRepository.saveAndFlush(paire);

        String apres = aFaireAvecDelegations(tokenPresident);
        assertAucuneDelegation(apres, 7401);
        assertAucuneLigne(apres, 7401);
        assertThat(JsonPath.<List<String>>read(apres, "$.delegations.parSection[*].code")).doesNotContain("A_RECEPTIONNER");
        assertThat(JsonPath.<Map<String, Object>>read(apres, "$.compteurs")).isEqualTo(compteursAvant);

        // Sans ?delegations, seuls les totaux du bloc sont servis.
        String replie = aFaire(tokenCc);
        assertThat(JsonPath.<Integer>read(replie, "$.delegations.total")).isPositive();
        assertThat(JsonPath.<List<Object>>read(replie, "$.delegations.taches")).isEmpty();
    }

    // ================================================================== 5 — rattachement du Vérificateur

    @Test
    @DisplayName("5 — Vérificateur cible en TITULAIRE, collègue en COLLEGUE ; sans rattachement tous TITULAIRE ; "
            + "avis FAV → TRANSMETTRE_DECISION")
    void verification_cibleCollegueEtAvis() throws Exception {
        Controleur membre = controleurRepository.findById("CTRMEM").orElseThrow();
        membre.setImRattache("CTRVER2");
        controleurRepository.save(membre);
        pvSigne(circuitDispatche(7501, "EN_VERIFICATION", "ANT", "CTRPRE", "CTRMEM"), "FAVR");   // cible CTRVER2
        pvSigne(circuitDispatche(7502, "EN_VERIFICATION", "ANT", "CTRPRE", "CTRMEM2"), "FAVR");  // sans rattachement
        pvSigne(circuitDispatche(7503, "EN_VERIFICATION", "ANT", "CTRPRE", "CTRMEM2"), "FAV");

        String cible = aFaireAvecDelegations(tokenVer2);
        assertLigne(cible, 7501, "A_VERIFIER", "VERIFIER");
        assertLigne(cible, 7502, "A_VERIFIER", "VERIFIER");

        String collegue = aFaireAvecDelegations(tokenVer);
        assertAucuneLigne(collegue, 7501);
        assertDelegation(collegue, 7501, "A_VERIFIER", "COLLEGUE");
        assertLigne(collegue, 7502, "A_VERIFIER", "VERIFIER");
        assertLigne(collegue, 7503, "A_VERIFIER", "TRANSMETTRE_DECISION");

        String cc = aFaireAvecDelegations(tokenCc);
        assertDelegation(cc, 7501, "A_VERIFIER", "DELEGATION");
        assertDelegation(cc, 7503, "A_VERIFIER", "DELEGATION");
        // Le Président statue les observations par délégation, mais la transmission exige la localité stricte.
        String president = aFaireAvecDelegations(tokenPresident);
        assertDelegation(president, 7501, "A_VERIFIER", "DELEGATION");
        assertAucuneDelegation(president, 7503);
    }

    // ================================================================== 6 — archivage et Président

    @Test
    @DisplayName("6 — Le Président n'a jamais A_ARCHIVER ni LETTRES_A_ARCHIVER, pas même en délégation ; le CC les a "
            + "en délégation")
    void archivage_jamaisLePresident() throws Exception {
        pvSigne(circuitDispatche(7601, "DECISION_TRANSMISE_SIGMP", "ANT", "CTRPRE", "CTRMEM"), "FAV");
        lettre(7601, "SIGNE");

        String president = aFaireAvecDelegations(tokenPresident);
        assertThat(JsonPath.<List<String>>read(president, "$.taches[*].section"))
                .doesNotContain("A_ARCHIVER", "LETTRES_A_ARCHIVER");
        assertThat(JsonPath.<List<String>>read(president, "$.delegations.taches[*].section"))
                .doesNotContain("A_ARCHIVER", "LETTRES_A_ARCHIVER");

        String cc = aFaireAvecDelegations(tokenCc);
        assertDelegation(cc, 7601, "A_ARCHIVER", "DELEGATION");
        assertDelegation(cc, 7601, "LETTRES_A_ARCHIVER", "DELEGATION");
        String ass = aFaire(tokenAss);
        assertLigne(ass, 7601, "A_ARCHIVER", "ARCHIVER_PV");
        assertLigne(ass, 7601, "LETTRES_A_ARCHIVER", "ARCHIVER_LETTRE");
    }

    // ================================================================== 7 — règle C2

    @Test
    @DisplayName("7 — Règle C2 : le corps brut servi à la PRMP et à l'UGPM ne contient ni nom ni matricule de "
            + "contrôleur, ni consigne ni retour de navette, et seulement leurs dossiers")
    void c2_partieControlee() throws Exception {
        Dossier consigne = circuitDispatche(7701, "DISPATCHE", "ANT", "CTRPRE", "CTRMEM");
        var dispatch = dispatchRepository.findById(7701).orElseThrow();
        dispatch.setInstructions("Consigne interne X9");
        dispatchRepository.save(dispatch);
        PvExamen accepte = pv(circuitDispatche(7702, "EXAMINE", "ANT", "CTRCC1", "CTRMEM"), "PROJET_ACCEPTE", "CTRMEM");
        accepte.setDateSignaturePresident(LocalDate.of(2026, 9, 11));
        accepte.setImMembreCoSignataire("CTRMEM2");
        accepte.setImCcCoSignataire("CTRCC1");
        pvExamenRepository.save(accepte);
        navette(7702, "RETOUR_CC", "Retour interne Y7");
        pvSigne(circuitDispatche(7703, "EN_ATTENTE_DECISION_PRMP", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        passage(7701, "RECEPTION", VENDREDI_14H, "CTRSEC");
        passage(7701, "DISPATCH", VENDREDI_14H.plusHours(1), "CTRPRE");
        // Un dossier d'une autre PRMP, dans les mêmes statuts.
        cnm.prs.entity.Prmp autrePrmp = prmp("PRMP002", null);
        autrePrmp.setCin("202022223333");
        autrePrmp.setEmailPrmp("prmp2@min.mg");
        prmpRepository.save(autrePrmp);
        Dossier etranger = circuitDispatche(7704, "DISPATCHE", "ANT", "CTRPRE", "CTRMEM");
        etranger.setIdPrmp("PRMP002");
        dossierRepository.save(etranger);
        assertThat(consigne.getIdPrmp()).isEqualTo("PRMP001");

        // Témoin : le CC reçoit bien ces informations internes.
        String cc = aFaire(tokenCc);
        assertThat(cc).contains("Retour interne Y7");

        for (String jeton : List.of(tokenPrmp, tokenUgpm)) {
            String corps = aFaire(jeton);
            assertThat(corps).doesNotContain("CTR").doesNotContain("Consigne interne X9")
                    .doesNotContain("Retour interne Y7").doesNotContain("NomCTR");
            assertThat(JsonPath.<List<Integer>>read(corps, "$.taches[*].dossier.idDossier"))
                    .contains(7701, 7702).doesNotContain(7704);
            assertThat(JsonPath.<List<Object>>read(corps, "$.taches[*].dossier.acteursEtapes")).containsOnlyNulls();
            assertThat(JsonPath.<List<Object>>read(corps, "$.taches[*].dossier.niveauNavette")).containsOnlyNulls();
            assertThat(JsonPath.<List<Object>>read(corps, "$.taches[*].faits.consigneDispatch")).containsOnlyNulls();
            assertThat(JsonPath.<List<Object>>read(corps, "$.taches[*].faits.dernierRetourNavette")).containsOnlyNulls();
            assertThat(JsonPath.<List<Object>>read(corps, "$.taches[*].faits.partsAttendues")).containsOnlyNulls();
            assertThat(JsonPath.<List<Object>>read(corps, "$.taches[*].refs.idDispatch")).containsOnlyNulls();
            // Les dates de la frise restent servies.
            assertThat(JsonPath.<List<String>>read(corps, filtre(7701) + ".dossier.datesEtapes.RECEPTION"))
                    .containsExactly("2026-09-11T14:00:00");
        }
    }

    // ================================================================== 8 — pause

    @Test
    @DisplayName("8 — EN_ATTENTE_DECISION_PRMP : A_RECTIFIER en pause chez la PRMP depuis le début de la suspension ; "
            + "EN_ATTENTE_PRMP chez le Vérificateur, hors aFaire")
    void pause_rectificationEtVerificateur() throws Exception {
        pvSigne(circuitDispatche(7801, "EN_ATTENTE_DECISION_PRMP", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        Verification passage = new Verification();
        passage.setIdReception(7801);
        passage.setIdPv(7801);
        passage.setImCtrlVerif("CTRVER");
        passage.setDateVerif(LocalDate.of(2026, 9, 11));
        passage.setObsLevees(false);
        verificationRepository.save(passage);
        passage(7801, "VERIFICATION", VENDREDI_14H.minusHours(4), "CTRVER");
        SuspensionDossier attente = new SuspensionDossier();
        attente.setIdSuspension(suspensionRepository.nextId());
        attente.setIdDossier(7801);
        attente.setStatut("EN_ATTENTE_DECISION_PRMP");
        attente.setDebut(VENDREDI_14H.minusHours(4));
        suspensionRepository.save(attente);

        String prmp = aFaire(tokenPrmp);
        assertLigne(prmp, 7801, "A_RECTIFIER", "RECTIFIER");
        assertThat(JsonPath.<List<String>>read(prmp, filtre(7801) + ".urgence")).containsExactly("EN_PAUSE");
        assertThat(JsonPath.<List<String>>read(prmp, filtre(7801) + ".delai.pauseDepuis"))
                .containsExactly("2026-09-11T10:00:00");
        assertThat(JsonPath.<List<Integer>>read(prmp, filtre(7801) + ".delai.pauseHeures")).containsExactly(13);
        assertInvariantPartieControlee(prmp);

        String ver = aFaire(tokenVer);
        assertLigne(ver, 7801, "EN_ATTENTE_PRMP", "VOIR");
        assertThat(JsonPath.<List<String>>read(ver, filtre(7801) + ".urgence")).containsExactly("EN_PAUSE");
        assertInvariantCnm(ver);
        int enPause = JsonPath.read(ver, "$.compteurs.enPause");
        assertThat(enPause).isPositive();
    }

    // ================================================================== 9 — délai et bornes

    @Test
    @DisplayName("9 — ecouleHeures = dureeHeuresOuvrees du passage enCours ; bornes EN_RETARD / BIENTOT / "
            + "DANS_LES_DELAIS à horloge figée, et tri par reste croissant")
    void delai_concordanceEtBornes() throws Exception {
        Dossier d = circuitAvecReception(7901, "PRET_DISPATCH", "ANT", "CTRSEC");
        d.setDateSoumission(JEUDI_10H);
        dossierRepository.save(d);
        passage(7901, "RECEPTION", VENDREDI_14H, "CTRSEC");

        String chrono = mvc.perform(get("/api/dossiers/7901/chronometrage").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String president = aFaire(tokenPresident);
        int ecoule = JsonPath.<List<Integer>>read(president, filtre(7901) + ".delai.ecouleHeures").get(0);
        assertThat(ecoule).isEqualTo(9).isEqualTo(JsonPath.<Integer>read(chrono, "$.etapes[-1].dureeHeuresOuvrees"));
        assertThat(JsonPath.<List<String>>read(president, filtre(7901) + ".delai.entree"))
                .containsExactly(JsonPath.<String>read(chrono, "$.etapes[-1].entree"));
        assertThat(JsonPath.<List<String>>read(president, filtre(7901) + ".delai.etape")).containsExactly("DISPATCH");

        // Bornes : délai standard du DISPATCH porté à 16 h pour la durée du test (seuil = max(2, ⌈5,6⌉) = 6 h).
        jdbcTemplate.update("update tr_delai_standard set \"DELAI_HEURES\" = 16 where \"ETAPE\" = 'DISPATCH'");
        assertThat(delaiStandardService.delais().get(EtapeCircuit.DISPATCH)).isEqualTo(16);
        assertThat(ReglesAFaire.seuilBientotHeures(16)).isEqualTo(6);
        borne(7911, 17);   // reste −1 → EN_RETARD
        borne(7912, 16);   // reste 0 → BIENTOT
        borne(7913, 10);   // reste 6 = seuil → BIENTOT
        borne(7914, 9);    // reste 7 → DANS_LES_DELAIS

        String bornes = aFaire(tokenPresident);
        assertThat(urgenceEtReste(bornes, 7911)).containsExactly("EN_RETARD", -1);
        assertThat(urgenceEtReste(bornes, 7912)).containsExactly("BIENTOT", 0);
        assertThat(urgenceEtReste(bornes, 7913)).containsExactly("BIENTOT", 6);
        assertThat(urgenceEtReste(bornes, 7914)).containsExactly("DANS_LES_DELAIS", 7);
        assertThat(JsonPath.<List<String>>read(bornes, filtre(7911) + ".delai.echeance")).allMatch(e -> e != null);
        List<Integer> ordre = JsonPath.read(bornes, "$.taches[?(@.section == 'A_DISPATCHER')].dossier.idDossier");
        assertThat(ordre).containsSubsequence(7911, 7912, 7913, 7914);
        assertThat(JsonPath.<List<Integer>>read(bornes, "$.sections[?(@.code == 'A_DISPATCHER')].standardHeures"))
                .containsExactly(16);
    }

    // ================================================================== 10 — tri, statuts terminaux, accès

    @Test
    @DisplayName("10 — Tri déterministe et rangs consécutifs ; statuts terminaux absents ; Administrateur et Chargé de "
            + "publication 403 ; anonyme 401")
    void triTerminauxEtAcces() throws Exception {
        dossier(7001, "SOUMIS", "ANT");
        circuitAvecReception(7002, "PRET_DISPATCH", "ANT", "CTRSEC");
        pv(circuitDispatche(7003, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "PROJET_SOUMIS", "CTRMEM");
        for (int i = 0; i < 4; i++) {
            String statut = List.of("CLOTURE", "RETIRE", "REMPLACE", "PV_SIGNE").get(i);
            int id = 7010 + i;
            lettre(circuitDispatche(id, statut, "ANT", "CTRPRE", "CTRMEM"), "SOUMIS");
            retrait(id);
        }

        String premier = aFaireAvecDelegations(tokenPresident);
        String second = aFaireAvecDelegations(tokenPresident);
        assertThat(second).isEqualTo(premier);
        List<Integer> rangs = JsonPath.read(premier, "$.taches[*].rang");
        for (int i = 0; i < rangs.size(); i++) {
            assertThat(rangs.get(i)).isEqualTo(i + 1);
        }
        List<String> urgences = JsonPath.read(premier, "$.taches[*].urgence");
        List<String> ordreEnum = java.util.Arrays.stream(cnm.prs.enums.UrgenceTache.values()).map(Enum::name).toList();
        for (int i = 1; i < urgences.size(); i++) {
            assertThat(ordreEnum.indexOf(urgences.get(i))).isGreaterThanOrEqualTo(ordreEnum.indexOf(urgences.get(i - 1)));
        }
        List<String> sections = JsonPath.read(premier, "$.sections[*].code");
        List<String> ordreSections = java.util.Arrays.stream(cnm.prs.enums.SectionAFaire.values()).map(Enum::name).toList();
        for (int i = 1; i < sections.size(); i++) {
            assertThat(ordreSections.indexOf(sections.get(i))).isGreaterThan(ordreSections.indexOf(sections.get(i - 1)));
        }
        for (String jeton : List.of(tokenPresident, tokenCc)) {
            String corps = aFaireAvecDelegations(jeton);
            for (int id = 7010; id < 7014; id++) {
                assertAucuneLigne(corps, id);
                assertAucuneDelegation(corps, id);
            }
        }

        mvc.perform(get("/api/dossiers/a-faire").header("Authorization", tokenAdmin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/dossiers/a-faire").header("Authorization", tokenPublication))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dossiers/a-faire")).andExpect(status().isUnauthorized());
    }

    // ================================================================== 11 — requêtes et badge

    @Test
    @DisplayName("11a — Nombre d'ordres SQL constant, de 1 à 40 dossiers, et au plus 15 (Président, Assistant, PRMP)")
    void sql_constant() throws Exception {
        decorMixte(7100);
        long presidentUn = ordresSql(tokenPresident);
        long assistantUn = ordresSql(tokenAss);
        long prmpUn = ordresSql(tokenPrmp);
        for (int i = 1; i < 40; i++) {
            decorMixte(7100 + i * 10);
        }
        long presidentQuarante = ordresSql(tokenPresident);
        long assistantQuarante = ordresSql(tokenAss);
        long prmpQuarante = ordresSql(tokenPrmp);

        assertThat(presidentQuarante).isEqualTo(presidentUn).isLessThanOrEqualTo(15);
        assertThat(assistantQuarante).isEqualTo(assistantUn).isLessThanOrEqualTo(15);
        assertThat(prmpQuarante).isEqualTo(prmpUn).isLessThanOrEqualTo(15);
        // Valeurs mesurées (2026-09-15) : 12 pour la CNM, 14 pour l'Assistant (rattachements), 7 pour la PRMP. Une
        // hausse n'est pas une faute en soi, mais elle doit être voulue : ce test la rend visible.
        assertThat(List.of(presidentUn, assistantUn, prmpUn)).containsExactly(12L, 14L, 7L);
        // Et le calcul a bien porté sur les quarante décors.
        assertThat(JsonPath.<List<Object>>read(aFaire(tokenPresident),
                "$.taches[?(@.section == 'PV_A_VISER')]")).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    @DisplayName("Paires actives : profilsExercables(courant) concorde avec peutExercer(courant, cible) pour tout couple")
    void profilsExercables_concordeAvecPeutExercer() {
        var paire = delegationProfilRepository.findById(5).orElseThrow();   // CC (3) → Membre (5)
        paire.setActif(false);
        delegationProfilRepository.saveAndFlush(paire);
        for (ProfilUtilisateur courant : ProfilUtilisateur.values()) {
            var exercables = permissionService.profilsExercables(courant);
            for (ProfilUtilisateur cible : ProfilUtilisateur.values()) {
                assertThat(exercables.contains(cible)).as("%s → %s", courant, cible)
                        .isEqualTo(permissionService.peutExercer(courant, cible));
            }
        }
        assertThat(permissionService.profilsExercables(ProfilUtilisateur.CHEF_COMMISSION))
                .doesNotContain(ProfilUtilisateur.MEMBRE).contains(ProfilUtilisateur.SECRETAIRE);
    }

    @Test
    @DisplayName("Faits de l'aperçu : lignes, montant, pièces, avis, examen entamé, parts attendues ; clés toujours "
            + "présentes")
    void faits_etClesPresentes() throws Exception {
        Dossier d = circuitDispatche(7950, "EXAMINE", "ANT", "CTRCC1", "CTRMEM");
        ppmRepository.save(ppm(7950, 7950, "PRMP001"));
        cnm.prs.entity.Marche ligne = marche(7950, 7950, 7950);
        ligne.setMontEstim(new BigDecimal("1500000"));
        marcheRepository.save(ligne);
        PvExamen accepte = pv(d, "PROJET_ACCEPTE", "CTRMEM");
        accepte.setDateSignaturePresident(LocalDate.of(2026, 9, 11));
        accepte.setImMembreCoSignataire("CTRMEM2");
        accepte.setImCcCoSignataire("CTRCC1");
        pvExamenRepository.save(accepte);

        String cc = aFaire(tokenCc);
        String f = filtre(7950);
        assertLigne(cc, 7950, "PV_A_SIGNER", "SIGNER");
        assertThat(JsonPath.<List<Integer>>read(cc, f + ".faits.nbLignes")).containsExactly(1);
        assertThat(JsonPath.<List<Number>>read(cc, f + ".faits.montantTotal").get(0).longValue()).isEqualTo(1_500_000L);
        assertThat(JsonPath.<List<String>>read(cc, f + ".faits.idAvis")).containsExactly("FAV");
        assertThat(JsonPath.<List<Boolean>>read(cc, f + ".faits.examenEntame")).containsExactly(true);
        assertThat(JsonPath.<List<List<String>>>read(cc, f + ".faits.partsAttendues").get(0))
                .containsExactly("MEMBRE", "CC");
        assertThat(JsonPath.<List<Integer>>read(cc, f + ".refs.idPv")).containsExactly(7950);
        assertThat(JsonPath.<List<Integer>>read(cc, f + ".refs.idDispatch")).containsExactly(7950);
        assertThat(JsonPath.<List<String>>read(cc, f + ".dossier.niveauNavette")).containsOnlyNulls();
        // Toutes les clés du contrat sont présentes, nulles comprises.
        Map<String, Object> tache = JsonPath.<List<Map<String, Object>>>read(cc, f).get(0);
        assertThat(tache).containsKeys("section", "geste", "gestesSecondaires", "mode", "urgence", "rang", "dossier",
                "delai", "faits", "refs");
        assertThat((Map<String, Object>) tache.get("faits")).containsKeys("nbLignes", "montantTotal", "nbPieces",
                "idAvis", "nbObservations", "consigneDispatch", "dernierRetourNavette", "motifRetrait", "examenEntame",
                "partsAttendues");
        assertThat((Map<String, Object>) tache.get("delai")).containsKeys("etape", "entree", "standardHeures",
                "ecouleHeures", "restantHeures", "echeance", "pauseDepuis", "pauseHeures", "datePrevisionnelleFin");
        assertThat((Map<String, Object>) tache.get("refs")).containsKeys("idReception", "idDispatch", "idExamen",
                "idPv", "idLettre", "idDemandeRetrait");
        assertThat(JsonPath.<Map<String, Object>>read(cc, "$.compteurs")).containsKeys("aFaire", "enRetard",
                "bientot", "dansLesDelais", "sansDelai", "enPause", "suivi");
    }

    // ================================================================== outils

    private String jeton(String im, ProfilUtilisateur profil, String localite) {
        return bearer(im, profil, TypeActeur.CONTROLEUR, im, localite);
    }

    private String aFaire(String jeton) throws Exception {
        return mvc.perform(get("/api/dossiers/a-faire").header("Authorization", jeton))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String aFaireAvecDelegations(String jeton) throws Exception {
        return mvc.perform(get("/api/dossiers/a-faire").param("delegations", "true").header("Authorization", jeton))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String filtre(int idDossier) {
        return "$.taches[?(@.dossier.idDossier == " + idDossier + ")]";
    }

    private static void assertLigne(String corps, int idDossier, String section, String geste) {
        List<String> gestes = JsonPath.read(corps,
                "$.taches[?(@.dossier.idDossier == " + idDossier + " && @.section == '" + section + "')].geste");
        assertThat(gestes).as("ligne %s du dossier %d", section, idDossier).containsExactly(geste);
        List<String> modes = JsonPath.read(corps,
                "$.taches[?(@.dossier.idDossier == " + idDossier + " && @.section == '" + section + "')].mode");
        assertThat(modes).containsOnly("TITULAIRE");
    }

    private static void assertAucuneLigne(String corps, int idDossier) {
        assertThat(JsonPath.<List<Object>>read(corps, filtre(idDossier))).as("aucune ligne pour %d", idDossier).isEmpty();
    }

    private static void assertAucuneSection(String corps, int idDossier, String section) {
        assertThat(JsonPath.<List<Object>>read(corps,
                "$.taches[?(@.dossier.idDossier == " + idDossier + " && @.section == '" + section + "')]")).isEmpty();
    }

    private static void assertDelegation(String corps, int idDossier, String section, String mode) {
        List<String> modes = JsonPath.read(corps, "$.delegations.taches[?(@.dossier.idDossier == " + idDossier
                + " && @.section == '" + section + "')].mode");
        assertThat(modes).as("délégation %s du dossier %d", section, idDossier).containsExactly(mode);
    }

    private static void assertAucuneDelegation(String corps, int idDossier) {
        assertThat(JsonPath.<List<Object>>read(corps,
                "$.delegations.taches[?(@.dossier.idDossier == " + idDossier + ")]")).isEmpty();
    }

    private static List<Object> urgenceEtReste(String corps, int idDossier) {
        return List.of(JsonPath.<List<String>>read(corps, filtre(idDossier) + ".urgence").get(0),
                JsonPath.<List<Integer>>read(corps, filtre(idDossier) + ".delai.restantHeures").get(0));
    }

    private static void assertInvariantCnm(String corps) {
        int aFaire = JsonPath.read(corps, "$.compteurs.aFaire");
        int somme = JsonPath.<Integer>read(corps, "$.compteurs.enRetard") + JsonPath.<Integer>read(corps, "$.compteurs.bientot")
                + JsonPath.<Integer>read(corps, "$.compteurs.dansLesDelais")
                + JsonPath.<Integer>read(corps, "$.compteurs.sansDelai");
        assertThat(aFaire).isEqualTo(somme);
    }

    private static void assertInvariantPartieControlee(String corps) {
        int aFaire = JsonPath.read(corps, "$.compteurs.aFaire");
        assertThat(aFaire).isEqualTo(JsonPath.<Integer>read(corps, "$.compteurs.enPause")
                + JsonPath.<Integer>read(corps, "$.compteurs.sansDelai"));
    }

    /** Ordres SQL préparés pendant un appel, compteur Hibernate remis à zéro juste avant. */
    private long ordresSql(String jeton) throws Exception {
        org.hibernate.stat.Statistics stats = entityManager.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        entityManager.flush();
        entityManager.clear();
        stats.clear();
        aFaire(jeton);
        return stats.getPrepareStatementCount();
    }

    /** Un dossier en navette simple (PV à viser), un dossier à archiver et un dossier à rectifier. */
    private void decorMixte(int base) {
        pv(circuitDispatche(base + 1, "EXAMINE", "ANT", "CTRPRE", "CTRMEM"), "PROJET_SOUMIS", "CTRMEM");
        pvSigne(circuitDispatche(base + 2, "DECISION_TRANSMISE_SIGMP", "ANT", "CTRPRE", "CTRMEM"), "FAV");
        pvSigne(circuitDispatche(base + 3, "EN_ATTENTE_DECISION_PRMP", "ANT", "CTRPRE", "CTRMEM"), "FAVR");
        passage(base + 1, "EXAMEN", VENDREDI_14H, "CTRMEM");
    }

    private void borne(int id, int ecouleVoulu) {
        circuitAvecReception(id, "PRET_DISPATCH", "ANT", "CTRSEC");
        LocalDateTime entree = MAINTENANT;
        while (HeuresOuvrees.ecoulees(entree, MAINTENANT) < ecouleVoulu) {
            entree = entree.minusHours(1);
        }
        assertThat(HeuresOuvrees.ecoulees(entree, MAINTENANT)).isEqualTo(ecouleVoulu);
        passage(id, "RECEPTION", entree, "CTRSEC");
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
}
