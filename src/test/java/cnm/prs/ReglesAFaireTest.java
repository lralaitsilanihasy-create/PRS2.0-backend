package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.GesteAFaire;
import cnm.prs.enums.ModeTache;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.SectionAFaire;
import cnm.prs.enums.UrgenceTache;
import cnm.prs.service.ChronometrageService.DelaiCourant;
import cnm.prs.service.CircuitDossierService.Circuit;
import cnm.prs.service.ReglesAFaire;
import cnm.prs.service.ReglesAFaire.Acteur;
import cnm.prs.service.ReglesAFaire.EtatDossier;
import cnm.prs.service.ReglesAFaire.Ligne;
import cnm.prs.service.ReglesAFaire.PvEnCours;

/**
 * ⚠️ <strong>Règles de l'accueil « À faire »</strong>, en unitaire pur (demande front du 2026-09-14 ; arbitrages du
 * 2026-09-15) : seuil « bientôt », urgence d'une entrée inconnue, geste principal du CC attributaire d'un dossier
 * central, et quelques frontières que l'intégration ne balaie pas toutes.
 */
class ReglesAFaireTest {

    private static final Set<ProfilUtilisateur> CC_EXERCE = EnumSet.of(ProfilUtilisateur.CHEF_COMMISSION,
            ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.MEMBRE, ProfilUtilisateur.VERIFICATEUR,
            ProfilUtilisateur.ASSISTANT_CONTROLEUR);

    @Test
    @DisplayName("Seuil « bientôt » = max(2 h, ⌈35 % du standard⌉)")
    void seuilBientot() {
        assertThat(ReglesAFaire.seuilBientotHeures(0)).isEqualTo(2);
        assertThat(ReglesAFaire.seuilBientotHeures(1)).isEqualTo(2);
        assertThat(ReglesAFaire.seuilBientotHeures(5)).isEqualTo(2);    // 1,75 → 2
        assertThat(ReglesAFaire.seuilBientotHeures(8)).isEqualTo(3);    // 2,8 → 3 (exemple du contrat)
        assertThat(ReglesAFaire.seuilBientotHeures(16)).isEqualTo(6);   // 5,6 → 6 (exemple du contrat)
        assertThat(ReglesAFaire.seuilBientotHeures(20)).isEqualTo(7);   // 7,0 → 7, pas 8
        assertThat(ReglesAFaire.seuilBientotHeures(40)).isEqualTo(14);
    }

    @Test
    @DisplayName("Urgence d'une section chronométrée : bornes, et SANS_DELAI quand l'entrée est inconnue")
    void urgence_chronometree() {
        assertThat(urgence(8, -1)).isEqualTo(UrgenceTache.EN_RETARD);
        assertThat(urgence(8, 0)).isEqualTo(UrgenceTache.BIENTOT);
        assertThat(urgence(8, 3)).isEqualTo(UrgenceTache.BIENTOT);
        assertThat(urgence(8, 4)).isEqualTo(UrgenceTache.DANS_LES_DELAIS);
        // Entrée inconnue : delaiCourant rend un écoulé nul et pas d'échéance — l'urgence ne s'invente pas.
        DelaiCourant inconnue = new DelaiCourant(EtapeCircuit.DISPATCH, null, 0L, 8, 8L, null, null, null);
        assertThat(ReglesAFaire.urgence(SectionAFaire.A_DISPATCHER, inconnue)).isEqualTo(UrgenceTache.SANS_DELAI);
        DelaiCourant horsCircuit = new DelaiCourant(null, null, null, null, null, null, null, null);
        assertThat(ReglesAFaire.urgence(SectionAFaire.A_EXAMINER, horsCircuit)).isEqualTo(UrgenceTache.SANS_DELAI);
    }

    @Test
    @DisplayName("Urgence fixe des sections non chronométrées, quel que soit le délai")
    void urgence_fixe() {
        DelaiCourant enRetard = delai(8, -5);
        assertThat(ReglesAFaire.urgence(SectionAFaire.LETTRES_A_SIGNER, enRetard)).isEqualTo(UrgenceTache.SANS_DELAI);
        assertThat(ReglesAFaire.urgence(SectionAFaire.RETRAITS_A_DECIDER, enRetard)).isEqualTo(UrgenceTache.SANS_DELAI);
        assertThat(ReglesAFaire.urgence(SectionAFaire.BROUILLONS, enRetard)).isEqualTo(UrgenceTache.HORS_DELAI);
        assertThat(ReglesAFaire.urgence(SectionAFaire.A_RECTIFIER, enRetard)).isEqualTo(UrgenceTache.EN_PAUSE);
        assertThat(ReglesAFaire.urgence(SectionAFaire.EN_ATTENTE_PRMP, enRetard)).isEqualTo(UrgenceTache.EN_PAUSE);
        assertThat(ReglesAFaire.urgence(SectionAFaire.EN_COURS_CNM, enRetard)).isEqualTo(UrgenceTache.SUIVI);
        assertThat(SectionAFaire.EN_ATTENTE_PRMP.horsAFaire()).isTrue();
        assertThat(SectionAFaire.EN_COURS_CNM.horsAFaire()).isTrue();
        assertThat(SectionAFaire.A_RECTIFIER.horsAFaire()).isFalse();
    }

    @Test
    @DisplayName("Arbitrage 3 — CC attributaire d'un dossier central sans examen : REATTRIBUER, EXAMINER en second ; "
            + "examen entamé : EXAMINER ; dossier régional ou Membre : EXAMINER")
    void ccAttributaireCentral_reattribuer() {
        Acteur cc = new Acteur(ProfilUtilisateur.CHEF_COMMISSION, "CTRCC1", "ANT", CC_EXERCE);
        Circuit central = new Circuit("ANT", "CTRPRE", "CTRCC1");

        List<Ligne> sansExamen = ReglesAFaire.lignes(cc, dispatche(central, false));
        assertThat(sansExamen).containsExactly(new Ligne(SectionAFaire.A_EXAMINER, GesteAFaire.REATTRIBUER,
                List.of(GesteAFaire.EXAMINER), ModeTache.TITULAIRE));

        assertThat(ReglesAFaire.lignes(cc, dispatche(central, true))).containsExactly(new Ligne(SectionAFaire.A_EXAMINER,
                GesteAFaire.EXAMINER, List.of(), ModeTache.TITULAIRE));

        // Paire CC → Membre désactivée : la réattribution reste, l'examen n'est plus proposé.
        Acteur ccSansMembre = new Acteur(ProfilUtilisateur.CHEF_COMMISSION, "CTRCC1", "ANT",
                EnumSet.of(ProfilUtilisateur.CHEF_COMMISSION));
        assertThat(ReglesAFaire.lignes(ccSansMembre, dispatche(central, false))).containsExactly(new Ligne(
                SectionAFaire.A_EXAMINER, GesteAFaire.REATTRIBUER, List.of(), ModeTache.TITULAIRE));

        Acteur ccTms = new Acteur(ProfilUtilisateur.CHEF_COMMISSION, "CTRCC2", "TMS", CC_EXERCE);
        assertThat(ReglesAFaire.lignes(ccTms, dispatche(new Circuit("TMS", "CTRPRE", "CTRCC2"), false)))
                .extracting(Ligne::geste).containsExactly(GesteAFaire.EXAMINER);

        Acteur membre = new Acteur(ProfilUtilisateur.MEMBRE, "CTRMEM", "ANT", EnumSet.of(ProfilUtilisateur.MEMBRE));
        assertThat(ReglesAFaire.lignes(membre, dispatche(new Circuit("ANT", "CTRPRE", "CTRMEM"), false)))
                .extracting(Ligne::geste).containsExactly(GesteAFaire.EXAMINER);
    }

    @Test
    @DisplayName("Paire Président → CC désactivée : le Président ne dispatche plus et ne vise plus (la garde fait foi)")
    void presidentSansPaireCc() {
        Acteur president = new Acteur(ProfilUtilisateur.PRESIDENT, "CTRPRE", null,
                EnumSet.of(ProfilUtilisateur.PRESIDENT, ProfilUtilisateur.SECRETAIRE));
        EtatDossier pret = new EtatDossier(1, "PRET_DISPATCH", "ANT", "ANT", true, null, false, null, null, null, null,
                null, false, null, null, false);
        assertThat(ReglesAFaire.lignes(president, pret)).isEmpty();

        PvEnCours soumis = new PvEnCours(1, "PROJET_SOUMIS", null, "CTRMEM", null, null, null, null, null, "FAV", null);
        Circuit simple = new Circuit("ANT", "CTRPRE", "CTRMEM");
        EtatDossier aViser = new EtatDossier(1, "EXAMINE", "ANT", "ANT", true, simple, true, soumis, simple,
                ProfilUtilisateur.PRESIDENT, null, null, false, null, null, false);
        assertThat(ReglesAFaire.lignes(president, aViser)).isEmpty();
    }

    @Test
    @DisplayName("PV accepté sans part de viseur (contrat d'avant le visa unique) : encore à viser en navette simple")
    void pvAccepteSansPartViseur() {
        Acteur president = new Acteur(ProfilUtilisateur.PRESIDENT, "CTRPRE", null,
                EnumSet.of(ProfilUtilisateur.PRESIDENT, ProfilUtilisateur.CHEF_COMMISSION));
        Circuit simple = new Circuit("ANT", "CTRPRE", "CTRMEM");
        PvEnCours accepteSansPart = new PvEnCours(1, "PROJET_ACCEPTE", null, "CTRMEM", null, null, null, null, null,
                "FAV", null);
        EtatDossier etat = new EtatDossier(1, "EXAMINE", "ANT", "ANT", true, simple, true, accepteSansPart, simple,
                ProfilUtilisateur.PRESIDENT, null, null, false, null, null, false);
        assertThat(ReglesAFaire.lignes(president, etat)).containsExactly(
                new Ligne(SectionAFaire.PV_A_VISER, GesteAFaire.VISER, List.of(), ModeTache.TITULAIRE));

        PvEnCours accepteVise = new PvEnCours(1, "PROJET_ACCEPTE", null, "CTRMEM", "CTRMEM2", null, null, null,
                LocalDate.of(2026, 9, 11), "FAV", null);
        EtatDossier vise = new EtatDossier(1, "EXAMINE", "ANT", "ANT", true, simple, true, accepteVise, simple,
                ProfilUtilisateur.PRESIDENT, null, null, false, null, null, false);
        assertThat(ReglesAFaire.lignes(president, vise)).isEmpty();
    }

    // ------------------------------------------------------------------ outils

    private static UrgenceTache urgence(int standard, long reste) {
        return ReglesAFaire.urgence(SectionAFaire.A_DISPATCHER, delai(standard, reste));
    }

    private static DelaiCourant delai(int standard, long reste) {
        LocalDateTime entree = LocalDate.of(2026, 9, 14).atTime(8, 0);
        return new DelaiCourant(EtapeCircuit.DISPATCH, entree, standard - reste, standard, reste, entree, null, null);
    }

    private static EtatDossier dispatche(Circuit circuit, boolean examenEntame) {
        return new EtatDossier(1, "DISPATCHE", circuit.localite(), circuit.localite(), true, circuit, examenEntame,
                null, null, null, null, null, false, null, null, false);
    }
}
