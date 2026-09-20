package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.SeuilMarche;
import cnm.prs.enums.BaremeSeuil;
import cnm.prs.enums.CategorieSeuil;
import cnm.prs.enums.ProcedureAttendue;
import cnm.prs.enums.TypeSeuil;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3) — lecture du barème de seuils.
 *
 * <p>Ce que ces tests protègent : la <strong>comparaison « égal ou supérieur »</strong> qu'écrit
 * l'arrêté (un montant exactement au seuil l'atteint), le fait que le <strong>barème choisi change le
 * verdict</strong> — l'exemple même du plan : 100 millions de fournitures passent en consultation pour un
 * organisme central et exigent un appel d'offres ouvert pour une structure déconcentrée —, la
 * <strong>datation</strong> (une valeur bornée ne vaut plus, la plus récente gagne), et le
 * <strong>silence</strong> quand le référentiel ne porte pas la case : on ne devine jamais un seuil.</p>
 */
class SeuilsEnVigueurTest {

    private static final LocalDate EFFET = LocalDate.of(2019, 7, 4);
    private static final LocalDate AUJOURD_HUI = LocalDate.of(2026, 9, 20);

    private static SeuilMarche seuil(TypeSeuil type, CategorieSeuil categorie, BaremeSeuil bareme,
            String montant) {
        return seuil(type, categorie, bareme, montant, EFFET, null);
    }

    private static SeuilMarche seuil(TypeSeuil type, CategorieSeuil categorie, BaremeSeuil bareme,
            String montant, LocalDate dateEffet, LocalDate dateFin) {
        SeuilMarche s = new SeuilMarche();
        s.setTypeSeuil(type);
        s.setCategorieSeuil(categorie);
        s.setBareme(bareme);
        s.setMontant(new BigDecimal(montant));
        s.setDateEffet(dateEffet);
        s.setDateFin(dateFin);
        return s;
    }

    /** Les quatre valeurs de l'arrêté qui encadrent les fournitures et services, les deux barèmes. */
    private static SeuilsEnVigueur fournituresEtServices() {
        return new SeuilsEnVigueur(AUJOURD_HUI, List.of(
                seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.FOURNITURES_SERVICES,
                        BaremeSeuil.CENTRAL, "150000000"),
                seuil(TypeSeuil.CONSULTATION, CategorieSeuil.FOURNITURES_SERVICES,
                        BaremeSeuil.CENTRAL, "2500000"),
                seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.FOURNITURES_SERVICES,
                        BaremeSeuil.DECONCENTRE, "75000000"),
                seuil(TypeSeuil.CONSULTATION, CategorieSeuil.FOURNITURES_SERVICES,
                        BaremeSeuil.DECONCENTRE, "1500000")));
    }

    @Test
    @DisplayName("100 millions de fournitures : consultation pour un organisme central, appel d'offres "
            + "ouvert pour une structure déconcentrée — le barème change le verdict")
    void memeMontant_baremeDifferent_verdictDifferent() {
        SeuilsEnVigueur seuils = fournituresEtServices();
        BigDecimal centMillions = new BigDecimal("100000000");

        SeuilsEnVigueur.VerdictProcedure central = seuils
                .procedureAttendue(CategorieSeuil.FOURNITURES_SERVICES, BaremeSeuil.CENTRAL, centMillions)
                .orElseThrow();
        assertThat(central.procedure()).isEqualTo(ProcedureAttendue.CONSULTATION);
        assertThat(central.seuilCite().getMontant()).isEqualByComparingTo("2500000");

        SeuilsEnVigueur.VerdictProcedure deconcentre = seuils
                .procedureAttendue(CategorieSeuil.FOURNITURES_SERVICES, BaremeSeuil.DECONCENTRE, centMillions)
                .orElseThrow();
        assertThat(deconcentre.procedure()).isEqualTo(ProcedureAttendue.APPEL_OFFRES_OUVERT);
        assertThat(deconcentre.seuilCite().getMontant()).isEqualByComparingTo("75000000");
    }

    @Test
    @DisplayName("Un montant exactement au seuil l'atteint : l'arrêté dit « égal ou supérieur »")
    void montantExactementAuSeuil_atteintLeSeuil() {
        SeuilsEnVigueur seuils = fournituresEtServices();

        assertThat(seuils.procedureAttendue(CategorieSeuil.FOURNITURES_SERVICES, BaremeSeuil.CENTRAL,
                new BigDecimal("150000000")).orElseThrow().procedure())
                .isEqualTo(ProcedureAttendue.APPEL_OFFRES_OUVERT);
        assertThat(seuils.procedureAttendue(CategorieSeuil.FOURNITURES_SERVICES, BaremeSeuil.CENTRAL,
                new BigDecimal("149999999")).orElseThrow().procedure())
                .isEqualTo(ProcedureAttendue.CONSULTATION);
    }

    @Test
    @DisplayName("Sous le seuil de consultation : achat direct, et c'est le seuil non atteint qui est cité")
    void sousLeSeuilDeConsultation_achatDirect() {
        SeuilsEnVigueur.VerdictProcedure verdict = fournituresEtServices()
                .procedureAttendue(CategorieSeuil.FOURNITURES_SERVICES, BaremeSeuil.CENTRAL,
                        new BigDecimal("2499999"))
                .orElseThrow();

        assertThat(verdict.procedure()).isEqualTo(ProcedureAttendue.ACHAT_DIRECT);
        assertThat(verdict.seuilCite().getMontant()).isEqualByComparingTo("2500000");
    }

    @Test
    @DisplayName("Référentiel muet sur la case : aucune procédure attendue, donc rien à signaler — "
            + "aucun seuil de repli n'est écrit dans le code")
    void caseAbsente_aucunVerdict() {
        SeuilsEnVigueur vide = new SeuilsEnVigueur(AUJOURD_HUI, List.of());

        assertThat(vide.vide()).isTrue();
        assertThat(vide.procedureAttendue(CategorieSeuil.ROUTES_CONSTRUCTION, BaremeSeuil.CENTRAL,
                new BigDecimal("9000000000"))).isEmpty();
        assertThat(vide.soumisAuControleAPriori(CategorieSeuil.ROUTES_CONSTRUCTION, BaremeSeuil.CENTRAL,
                new BigDecimal("9000000000"))).isFalse();
    }

    @Test
    @DisplayName("Les prestations intellectuelles n'ont pas de seuil de procédure dans l'arrêté : "
            + "aucun verdict de procédure, mais une forme de publicité et son délai")
    void prestationsIntellectuelles_publiciteEtDelai() {
        SeuilMarche presse = seuil(TypeSeuil.MANIFESTATION_INTERET_PRESSE,
                CategorieSeuil.PRESTATIONS_INTELLECTUELLES, BaremeSeuil.CENTRAL, "100000000");
        presse.setDelaiMinJours(30);
        SeuilMarche affichage = seuil(TypeSeuil.MANIFESTATION_INTERET_AFFICHAGE,
                CategorieSeuil.PRESTATIONS_INTELLECTUELLES, BaremeSeuil.CENTRAL, "0");
        affichage.setDelaiMinJours(10);
        SeuilMarche controle = seuil(TypeSeuil.CONTROLE_A_PRIORI,
                CategorieSeuil.PRESTATIONS_INTELLECTUELLES, BaremeSeuil.CENTRAL, "300000000");
        SeuilsEnVigueur seuils = new SeuilsEnVigueur(AUJOURD_HUI, List.of(presse, affichage, controle));

        assertThat(seuils.procedureAttendue(CategorieSeuil.PRESTATIONS_INTELLECTUELLES, BaremeSeuil.CENTRAL,
                new BigDecimal("120000000"))).isEmpty();
        assertThat(seuils.publiciteManifestationInteret(BaremeSeuil.CENTRAL, new BigDecimal("100000000"))
                .orElseThrow().getDelaiMinJours()).isEqualTo(30);
        assertThat(seuils.publiciteManifestationInteret(BaremeSeuil.CENTRAL, new BigDecimal("99999999"))
                .orElseThrow().getDelaiMinJours()).isEqualTo(10);
        assertThat(seuils.soumisAuControleAPriori(CategorieSeuil.PRESTATIONS_INTELLECTUELLES,
                BaremeSeuil.CENTRAL, new BigDecimal("300000000"))).isTrue();
        assertThat(seuils.soumisAuControleAPriori(CategorieSeuil.PRESTATIONS_INTELLECTUELLES,
                BaremeSeuil.CENTRAL, new BigDecimal("299999999"))).isFalse();
    }

    @Test
    @DisplayName("Une valeur bornée ne vaut plus à la date lue, et de deux valeurs en vigueur c'est la "
            + "plus récente qui gagne : le pré-contrôle d'un plan ancien garde le barème de son temps")
    void datation_valeurBorneeEtValeurLaPlusRecente() {
        SeuilMarche ancienne = seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.FOURNITURES_SERVICES,
                BaremeSeuil.CENTRAL, "100000000", LocalDate.of(2016, 3, 31), LocalDate.of(2019, 7, 4));
        SeuilMarche courante = seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.FOURNITURES_SERVICES,
                BaremeSeuil.CENTRAL, "150000000", LocalDate.of(2019, 7, 4), null);

        SeuilsEnVigueur avant = new SeuilsEnVigueur(LocalDate.of(2018, 1, 1), List.of(ancienne, courante));
        assertThat(avant.seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.FOURNITURES_SERVICES,
                BaremeSeuil.CENTRAL).orElseThrow().getMontant()).isEqualByComparingTo("100000000");

        SeuilsEnVigueur apres = new SeuilsEnVigueur(AUJOURD_HUI, List.of(ancienne, courante));
        assertThat(apres.seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.FOURNITURES_SERVICES,
                BaremeSeuil.CENTRAL).orElseThrow().getMontant()).isEqualByComparingTo("150000000");
    }

    @Test
    @DisplayName("La nature seule ne départage pas les trois catégories de travaux — c'est pour cela que "
            + "la catégorie de seuil existe")
    void categoriesPlausibles_selonLaNature() {
        assertThat(CategorieSeuil.plausiblesPourNature("Travaux")).containsExactly(
                CategorieSeuil.ROUTES_CONSTRUCTION, CategorieSeuil.ENTRETIEN_ROUTIER,
                CategorieSeuil.TRAVAUX_NON_ROUTIERS);
        assertThat(CategorieSeuil.plausiblesPourNature("Fournitures"))
                .containsExactly(CategorieSeuil.FOURNITURES_SERVICES);
        assertThat(CategorieSeuil.plausiblesPourNature("Services")).containsExactly(
                CategorieSeuil.FOURNITURES_SERVICES, CategorieSeuil.PRESTATIONS_INTELLECTUELLES);
        assertThat(CategorieSeuil.plausiblesPourNature(null))
                .containsExactly(CategorieSeuil.values());
    }

    @Test
    @DisplayName("Le barème se déduit de l'organisme de contrôle : la CNM donne le barème central, une "
            + "CRM le barème déconcentré — et une localité inconnue penche du côté qui signale le plus")
    void bareme_deduitDeLOrganismeDeControle() {
        assertThat(BaremeSeuil.pourLocalite("ANT")).isEqualTo(BaremeSeuil.CENTRAL);
        assertThat(BaremeSeuil.pourLocalite("TOA")).isEqualTo(BaremeSeuil.DECONCENTRE);
        assertThat(BaremeSeuil.pourLocalite(null)).isEqualTo(BaremeSeuil.DECONCENTRE);
    }
}
