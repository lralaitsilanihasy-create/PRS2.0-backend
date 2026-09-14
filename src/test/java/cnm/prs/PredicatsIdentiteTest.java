package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.Localite;
import cnm.prs.enums.NiveauNavette;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.security.Visibilite;
import cnm.prs.service.CircuitDossierService;
import cnm.prs.service.CircuitDossierService.Circuit;
import cnm.prs.service.PredicatsIdentite;

/**
 * ⚠️ <strong>Prédicats d'identité du circuit</strong> (demande front du 2026-09-14, accueil « À faire », §6) —
 * test unitaire pur : aucune base, aucun jeton, aucun contexte Spring. Les gardes qui appellent ces prédicats
 * sont éprouvées de bout en bout par les tests d'intégration existants, restés inchangés ; ceux-ci verrouillent
 * chaque condition isolément, cas nuls compris, pour que l'accueil puisse les appeler en lot sans surprise.
 */
class PredicatsIdentiteTest {

    private static final ProfilUtilisateur P = ProfilUtilisateur.PRESIDENT;
    private static final ProfilUtilisateur CC = ProfilUtilisateur.CHEF_COMMISSION;
    private static final ProfilUtilisateur MEMBRE = ProfilUtilisateur.MEMBRE;
    private static final String CENTRALE = Localite.ID_CENTRALE;
    private static final String REGIONALE = "TMS";

    // ------------------------------------------------------------------ identités nominatives

    @Test
    @DisplayName("Même personne — attributaire, dispatcheur, examinateur, désigné : égalité stricte, jamais sur un nul")
    void identitesNominatives() {
        assertTrue(PredicatsIdentite.estAttributaire("CTRMEM", "CTRMEM"));
        assertFalse(PredicatsIdentite.estAttributaire("CTRCC1", "CTRMEM"));
        assertFalse(PredicatsIdentite.estAttributaire(null, "CTRMEM"));
        assertFalse(PredicatsIdentite.estAttributaire("CTRMEM", null));
        assertFalse(PredicatsIdentite.estAttributaire(null, null), "deux inconnus ne sont pas la même personne");

        assertTrue(PredicatsIdentite.estDispatcheur("CTRPRE", "CTRPRE"));
        assertFalse(PredicatsIdentite.estDispatcheur(null, null));
        assertTrue(PredicatsIdentite.estExaminateur("CTRMEM", "CTRMEM"));
        assertFalse(PredicatsIdentite.estExaminateur("CTRPRE", "CTRMEM"));
        assertTrue(PredicatsIdentite.estDesigne("CTRMEM", "CTRMEM"));
        assertFalse(PredicatsIdentite.estDesigne("CTRMEM", null));
    }

    @Test
    @DisplayName("Désignation faite — un matricule non vide ; nul, vide ou blanc : la part n'est pas ouverte")
    void designationFaite() {
        assertTrue(PredicatsIdentite.designationFaite("CTRMEM"));
        assertFalse(PredicatsIdentite.designationFaite(null));
        assertFalse(PredicatsIdentite.designationFaite(""));
        assertFalse(PredicatsIdentite.designationFaite("   "));
    }

    // ------------------------------------------------------------------ examen, projet de PV

    @Test
    @DisplayName("Écriture d'examen par délégation — tout profil sauf Membre ; rédaction du projet : paire active, jamais un Membre")
    void examenEtRedaction() {
        assertFalse(PredicatsIdentite.ecritureExamenParDelegation(MEMBRE));
        assertTrue(PredicatsIdentite.ecritureExamenParDelegation(CC));
        assertTrue(PredicatsIdentite.ecritureExamenParDelegation(P));
        assertTrue(PredicatsIdentite.ecritureExamenParDelegation(null));

        assertFalse(PredicatsIdentite.redactionParDelegation(MEMBRE, true), "pas de délégation entre pairs");
        assertTrue(PredicatsIdentite.redactionParDelegation(CC, true));
        assertFalse(PredicatsIdentite.redactionParDelegation(CC, false));
        assertFalse(PredicatsIdentite.redactionParDelegation(null, false));
    }

    // ------------------------------------------------------------------ dispatch

    @Test
    @DisplayName("Pré-dispatch — le CC ne dispatche pas la centrale, sauf à réattribuer ce qui le concerne ; le régional et le Président passent")
    void preDispatch() {
        assertFalse(PredicatsIdentite.peutDispatcherSelonLocalite(CC, false, CENTRALE));
        assertTrue(PredicatsIdentite.peutDispatcherSelonLocalite(CC, true, CENTRALE));
        assertTrue(PredicatsIdentite.peutDispatcherSelonLocalite(CC, false, REGIONALE));
        assertTrue(PredicatsIdentite.peutDispatcherSelonLocalite(CC, false, null), "localité indéterminée : pas centrale");
        assertTrue(PredicatsIdentite.peutDispatcherSelonLocalite(P, false, CENTRALE));

        assertTrue(PredicatsIdentite.retraitDispatchReserveAuDispatcheur(CC));
        assertFalse(PredicatsIdentite.retraitDispatchReserveAuDispatcheur(P));
    }

    // ------------------------------------------------------------------ navette et visa

    @Test
    @DisplayName("⚠️ Niveau nul = étage du CC — et l'étage PRESIDENT n'est atteint que posé")
    void niveauNulEtageDuCc() {
        assertEquals(NiveauNavette.CC, PredicatsIdentite.niveauEffectif(null));
        assertEquals(NiveauNavette.CC, PredicatsIdentite.niveauEffectif(NiveauNavette.CC));
        assertEquals(NiveauNavette.PRESIDENT, PredicatsIdentite.niveauEffectif(NiveauNavette.PRESIDENT));
        assertTrue(PredicatsIdentite.estALEtage(null, NiveauNavette.CC));
        assertFalse(PredicatsIdentite.estALEtage(null, NiveauNavette.PRESIDENT));
        assertTrue(PredicatsIdentite.estALEtage(NiveauNavette.PRESIDENT, NiveauNavette.PRESIDENT));
        assertFalse(PredicatsIdentite.estALEtage(NiveauNavette.PRESIDENT, NiveauNavette.CC));
    }

    @Test
    @DisplayName("CC du circuit — le dispatcheur courant, et lui seul")
    void ccDuCircuit() {
        Circuit circuit = new Circuit(CENTRALE, "CTRCC1", "CTRMEM");
        assertTrue(PredicatsIdentite.estCcDuCircuit("CTRCC1", circuit));
        assertFalse(PredicatsIdentite.estCcDuCircuit("CTRPRE", circuit));
        assertFalse(PredicatsIdentite.estCcDuCircuit(null, circuit));
        assertFalse(PredicatsIdentite.estCcDuCircuit("CTRCC1", null));
        assertFalse(PredicatsIdentite.estCcDuCircuit(null, new Circuit(null, null, null)));
    }

    @Test
    @DisplayName("Visa — profils P/CC ; deux niveaux : le Président ; intérim : navette simple et non-dispatcheur")
    void visa() {
        assertTrue(PredicatsIdentite.estProfilViseur(P));
        assertTrue(PredicatsIdentite.estProfilViseur(CC));
        assertFalse(PredicatsIdentite.estProfilViseur(MEMBRE));
        assertFalse(PredicatsIdentite.estProfilViseur(ProfilUtilisateur.ADMINISTRATEUR));
        assertFalse(PredicatsIdentite.estProfilViseur(null));

        assertTrue(PredicatsIdentite.estViseurDeuxNiveaux(P));
        assertFalse(PredicatsIdentite.estViseurDeuxNiveaux(CC));

        assertFalse(PredicatsIdentite.visaParInterim(false, "CTRPRE", "CTRPRE"), "le dispatcheur vise en titulaire");
        assertTrue(PredicatsIdentite.visaParInterim(false, "CTRCC1", "CTRPRE"));
        assertFalse(PredicatsIdentite.visaParInterim(true, "CTRPRE", "CTRCC1"), "jamais d'intérim sur deux niveaux");
    }

    @Test
    @DisplayName("⚠️ L'examinateur ne vise pas son examen — sauf s'il en est aussi le dispatcheur ; les autres ne sont pas concernés")
    void viseurHorsExaminateur() {
        assertTrue(PredicatsIdentite.viseurHorsExaminateur("CTRPRE", "CTRCC1", "CTRPRE"), "non-examinateur");
        assertTrue(PredicatsIdentite.viseurHorsExaminateur("CTRCC2", "CTRCC1", "CTRPRE"), "suppléant non-examinateur");
        assertFalse(PredicatsIdentite.viseurHorsExaminateur("CTRCC1", "CTRCC1", "CTRPRE"), "examinateur non dispatcheur");
        assertFalse(PredicatsIdentite.viseurHorsExaminateur("CTRCC1", "CTRCC1", null));
        assertTrue(PredicatsIdentite.viseurHorsExaminateur("CTRCC1", "CTRCC1", "CTRCC1"), "circuit court : il cumule");
        assertTrue(PredicatsIdentite.viseurHorsExaminateur(null, "CTRCC1", "CTRPRE"));
        assertTrue(PredicatsIdentite.viseurHorsExaminateur("CTRCC1", null, "CTRPRE"));
    }

    // ------------------------------------------------------------------ localités, lettres, retraits

    @Test
    @DisplayName("Localité stricte (archivage, SIGMP) — même localité ; ressource sans localité libre ; acteur sans localité exclu")
    void localiteStricte() {
        assertTrue(PredicatsIdentite.localiteStricteAdmise(REGIONALE, REGIONALE));
        assertFalse(PredicatsIdentite.localiteStricteAdmise(REGIONALE, CENTRALE));
        assertFalse(PredicatsIdentite.localiteStricteAdmise(REGIONALE, null), "le Président n'archive pas");
        assertTrue(PredicatsIdentite.localiteStricteAdmise(null, null));
    }

    @Test
    @DisplayName("Lettre de renvoi — régionale : CC seulement ; centrale : profil libre (localité vérifiée à part)")
    void signatureLettre() {
        assertTrue(PredicatsIdentite.signatureLettreProfilAdmis(CC, REGIONALE));
        assertFalse(PredicatsIdentite.signatureLettreProfilAdmis(P, REGIONALE));
        assertTrue(PredicatsIdentite.signatureLettreProfilAdmis(P, CENTRALE));
        assertTrue(PredicatsIdentite.signatureLettreProfilAdmis(CC, CENTRALE));
        assertFalse(PredicatsIdentite.signatureLettreProfilAdmis(P, null), "sans localité : régionale par défaut");
    }

    @Test
    @DisplayName("Décision de retrait — le Président partout, le CC dans la localité du dossier")
    void decisionRetrait() {
        assertTrue(PredicatsIdentite.peutDeciderRetrait(P, null, REGIONALE));
        assertTrue(PredicatsIdentite.peutDeciderRetrait(P, null, null));
        assertTrue(PredicatsIdentite.peutDeciderRetrait(CC, REGIONALE, REGIONALE));
        assertFalse(PredicatsIdentite.peutDeciderRetrait(CC, CENTRALE, REGIONALE));
        assertFalse(PredicatsIdentite.peutDeciderRetrait(CC, REGIONALE, null));
        assertFalse(PredicatsIdentite.peutDeciderRetrait(MEMBRE, REGIONALE, REGIONALE));
        assertFalse(PredicatsIdentite.peutDeciderRetrait(null, REGIONALE, REGIONALE));
    }

    @Test
    @DisplayName("Visibilite.localiteAdmise — Président et Administrateur partout ; ressource sans localité libre ; sinon même localité")
    void localiteAdmise() {
        assertTrue(Visibilite.voitTout(P));
        assertTrue(Visibilite.voitTout(ProfilUtilisateur.ADMINISTRATEUR));
        assertFalse(Visibilite.voitTout(CC));
        assertFalse(Visibilite.voitTout(null));

        assertTrue(Visibilite.localiteAdmise(P, null, REGIONALE));
        assertTrue(Visibilite.localiteAdmise(CC, null, null));
        assertTrue(Visibilite.localiteAdmise(CC, REGIONALE, REGIONALE));
        assertFalse(Visibilite.localiteAdmise(CC, CENTRALE, REGIONALE));
        assertFalse(Visibilite.localiteAdmise(CC, null, REGIONALE));
        assertFalse(Visibilite.localiteAdmise(CC, "  ", REGIONALE));
        assertFalse(Visibilite.localiteAdmise(ProfilUtilisateur.PRMP, REGIONALE, CENTRALE));
    }

    @Test
    @DisplayName("Deux niveaux (prédicat pur) — centrale, dispatcheur CC, distinct de l'attributaire ; tout le reste à un niveau")
    void deuxNiveaux() {
        Circuit reattribue = new Circuit(CENTRALE, "CTRCC1", "CTRMEM");
        assertTrue(CircuitDossierService.deuxNiveaux(reattribue, CC));
        assertFalse(CircuitDossierService.deuxNiveaux(reattribue, P), "dispatch direct du Président");
        assertFalse(CircuitDossierService.deuxNiveaux(new Circuit(REGIONALE, "CTRCC2", "CTRMEM"), CC), "régional");
        assertFalse(CircuitDossierService.deuxNiveaux(new Circuit(CENTRALE, "CTRCC1", "CTRCC1"), CC),
                "le CC qui examine lui-même reste à un niveau");
        assertFalse(CircuitDossierService.deuxNiveaux(new Circuit(CENTRALE, null, "CTRMEM"), CC), "dispatch incomplet");
        assertFalse(CircuitDossierService.deuxNiveaux(new Circuit(CENTRALE, "CTRCC1", null), CC), "sans attributaire");
        assertFalse(CircuitDossierService.deuxNiveaux(null, CC));
    }
}
