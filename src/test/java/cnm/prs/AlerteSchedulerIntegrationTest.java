package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import cnm.prs.dto.CreerMandatRequest;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Mandat;
import cnm.prs.enums.TypeNotification;
import cnm.prs.repository.MandatRepository;
import cnm.prs.repository.NotificationRepository;
import cnm.prs.scheduler.AlerteScheduler;
import cnm.prs.service.MandatService;

/**
 * ⚠️ Audit 2026-08-27 (lot C, constat C4) — {@link AlerteScheduler} n'avait <strong>aucun test</strong>,
 * y compris {@code expirerComptesPrmp} (désactivation d'un compte en fin de mandat, un automatisme de
 * sécurité). Le job est appelé <strong>directement</strong> (pas d'attente de cron) sous une horloge
 * maîtrisée ({@link ClockTestConfig}), qui remplace le bean par défaut de {@code ClockConfig}
 * (horloge système réelle) pour piloter « aujourd'hui » sans dépendre de la date de la machine.
 *
 * <p>Contexte Spring distinct de {@code CnmIntegrationTestSupport} (bean {@code Clock} différent) —
 * seule cette classe paie le coût d'un second contexte, en échange d'un contrôle total de la date.</p>
 */
class AlerteSchedulerIntegrationTest extends CnmIntegrationTestSupport {

    @TestConfiguration
    static class ClockTestConfig {
        // ⚠️ Nom de bean différent de `clock` (ClockConfig) : Spring Boot refuse par défaut le
        // remplacement d'une définition de bean existante (BeanDefinitionOverrideException) ;
        // @Primary suffit à départager les deux beans Clock pour l'injection par type.
        @Bean
        @Primary
        Clock clockDeTest() {
            return new HorlogeMutable(Instant.now(), ZoneId.systemDefault());
        }
    }

    /** Horloge de test : instant figé, avançable à la demande ({@link #avancerA}). */
    static final class HorlogeMutable extends Clock {
        private volatile Instant instant;
        private final ZoneId zone;

        HorlogeMutable(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new HorlogeMutable(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void avancerA(LocalDate date) {
            this.instant = date.atStartOfDay(zone).toInstant();
        }
    }

    @Autowired private AlerteScheduler scheduler;
    @Autowired private MandatRepository mandatRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private MandatService mandatService;
    @Autowired private Clock clock;

    private HorlogeMutable horloge;

    @BeforeEach
    void horlogeDeTest() {
        horloge = (HorlogeMutable) clock;
    }

    /** Mandat déclaré de PRMP001 dont la fin est fixée à {@code jours} jours après « aujourd'hui ». */
    private Mandat seedMandat(String idPrmp, String refArrete, int jours) {
        LocalDate today = LocalDate.now(horloge);
        Mandat m = new Mandat();
        m.setIdPrmp(idPrmp);
        m.setTitulaire("Titulaire test");
        m.setDateDebut(today.minusYears(1));
        m.setDateFin(today.plusDays(jours));
        m.setRefArrete(refArrete);
        m.setStatut("ACTIF");
        m.setNumeroMandat(1);
        return mandatRepository.save(m);
    }

    private long nbNotificationsFinMandat(String idPrmp) {
        return notificationRepository.findAll().stream()
                .filter(n -> "PRMP".equals(n.getDestinataireType()) && idPrmp.equals(n.getDestinataireRef())
                        && TypeNotification.FIN_MANDAT.name().equals(n.getTypeNotif()))
                .count();
    }

    private boolean compteActif(String idPrmp) {
        return compteAuthRepository.findByRefActeurAndTypeActeur(idPrmp, "PRMP").stream()
                .findFirst().map(CompteAuth::getActif).orElseThrow();
    }

    // ------------------------------------------------------------------
    // Paliers J-90 / J-30 / J-7 (mandat déclaré)
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "palier J-{0} : une alerte FIN_MANDAT est émise")
    @ValueSource(longs = { 90, 30, 7 })
    @DisplayName("Paliers J-90/J-30/J-7 du mandat déclaré : alerte émise")
    void palier_mandatDeclare_emetUneAlerte(long jours) {
        seedMandat("PRMP001", "ARR-PALIER-" + jours, (int) jours);

        scheduler.alerterFinMandat();

        assertThat(nbNotificationsFinMandat("PRMP001")).isEqualTo(1);
        assertThat(compteActif("PRMP001")).isTrue();
    }

    @Test
    @DisplayName("Hors palier (J-6) : aucune alerte, compte inchangé")
    void horsPalier_nAlerteRien() {
        seedMandat("PRMP001", "ARR-HORS-PALIER", 6);

        scheduler.alerterFinMandat();

        assertThat(nbNotificationsFinMandat("PRMP001")).isZero();
        assertThat(compteActif("PRMP001")).isTrue();
    }

    // ------------------------------------------------------------------
    // Priorité du mandat déclaré sur le repli DATE_NOMIN + 3 ans
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Mandat déclaré : sa date de fin fait autorité, pas DATE_NOMIN + 3 ans (PRMP001 nommée 2024-01-15)")
    void mandatDeclare_prevautSurLeRepli() {
        // DATE_NOMIN (2024-01-15) + 3 ans = 2027-01-15 : si le repli était utilisé, le calcul des jours
        // restants tomberait sur une toute autre valeur que celle du mandat déclaré ci-dessous.
        seedMandat("PRMP001", "ARR-DECLARE-PRIME", 7);
        LocalDate finDeclaree = LocalDate.now(horloge).plusDays(7);
        LocalDate finRepliNaif = LocalDate.of(2024, 1, 15).plusYears(3);
        assertThat(finDeclaree).isNotEqualTo(finRepliNaif); // pré-requis du test : les deux dates divergent

        scheduler.alerterFinMandat();

        assertThat(nbNotificationsFinMandat("PRMP001")).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Repli DATE_NOMIN + 3 ans (aucun mandat déclaré)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Sans mandat déclaré : repli sur DATE_NOMIN + 3 ans")
    void sansMandatDeclare_replieSurDateNominPlus3Ans() {
        // PRMP002 sans aucune ligne t_mandat ; DATE_NOMIN choisie pour que DATE_NOMIN + 3 ans == J-7.
        LocalDate today = LocalDate.now(horloge);
        LocalDate dateNomin = today.plusDays(7).minusYears(3);
        cnm.prs.entity.Prmp prmp = prmp("PRMP002", "ANT");
        prmp.setDateNomin(dateNomin);
        prmpRepository.save(prmp);
        compteAuthRepository.save(new CompteAuth("PRMP002", passwordEncoder.encode("pw"), "PRMP", "PRMP002", true));

        scheduler.alerterFinMandat();

        assertThat(nbNotificationsFinMandat("PRMP002")).isEqualTo(1);
        assertThat(compteActif("PRMP002")).isTrue();
    }

    // ------------------------------------------------------------------
    // Expiration (J-0 / dépassé) : désactivation du compte
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Mandat expiré (J-0) : le compte PRMP est désactivé")
    void mandatExpireJ0_desactiveLeCompte() {
        seedMandat("PRMP001", "ARR-EXPIRE-J0", 0);
        assertThat(compteActif("PRMP001")).isTrue();

        scheduler.alerterFinMandat();

        assertThat(compteActif("PRMP001")).isFalse();
        assertThat(nbNotificationsFinMandat("PRMP001")).isZero(); // J-0 n'est pas un palier d'alerte
    }

    @Test
    @DisplayName("Mandat expiré depuis plusieurs jours : le compte PRMP est désactivé")
    void mandatDejaExpire_desactiveLeCompte() {
        seedMandat("PRMP001", "ARR-EXPIRE-PASSE", -5);

        scheduler.alerterFinMandat();

        assertThat(compteActif("PRMP001")).isFalse();
    }

    @Test
    @DisplayName("Compte déjà inactif à l'expiration : ré-appliquer le job n'échoue pas (idempotent)")
    void compteDejaInactif_rejouerLeJobNePlantePas() {
        seedMandat("PRMP001", "ARR-DEJA-INACTIF", -1);

        scheduler.alerterFinMandat();
        assertThat(compteActif("PRMP001")).isFalse();

        scheduler.alerterFinMandat(); // rejoue sur un compte déjà désactivé : ne doit pas planter

        assertThat(compteActif("PRMP001")).isFalse();
    }

    // ------------------------------------------------------------------
    // Pas de re-déclenchement le jour suivant (le palier ne dure qu'un jour)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Le jour suivant le palier J-7, le job ne redéclenche pas d'alerte (joursRestants=6)")
    void lendemainDuPalier_neRedeclenchePas() {
        seedMandat("PRMP001", "ARR-LENDEMAIN", 7);

        scheduler.alerterFinMandat();
        assertThat(nbNotificationsFinMandat("PRMP001")).isEqualTo(1);

        horloge.avancerA(LocalDate.now(horloge).plusDays(1));
        scheduler.alerterFinMandat();

        assertThat(nbNotificationsFinMandat("PRMP001")).isEqualTo(1); // toujours 1 : pas de doublon
    }

    /**
     * ⚠️ Trouvaille (pas un correctif — hors périmètre « Clock uniquement ») : contrairement à
     * {@code alerterJalons} (qui pose {@code alerteEnvoyee=true}), {@code alerterFinMandat} n'a
     * <strong>aucune protection contre un second appel le même jour</strong> : la garantie « une seule
     * alerte par palier » ne tient que parce que le cron ne s'exécute qu'une fois par jour (cf. javadoc
     * de la classe), pas par idempotence du code. Un rejeu manuel (ex. relance du process le même jour)
     * duplique l'alerte — vérifié ci-dessous en l'assumant explicitement.
     */
    @Test
    @DisplayName("TROUVAILLE : un second appel le MÊME jour duplique l'alerte (aucune garde d'idempotence)")
    void secondAppelMemeJour_dupliqueLAlerte_trouvaille() {
        seedMandat("PRMP001", "ARR-DOUBLON", 7);

        scheduler.alerterFinMandat();
        scheduler.alerterFinMandat();

        assertThat(nbNotificationsFinMandat("PRMP001")).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Reconduction (lot B, MandatService.creer) : réactivation après expiration
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Après expiration par le job, une reconduction (MandatService.creer) rouvre le compte")
    void reconduction_rouvreLeCompteApresExpirationParLeJob() {
        // 1) Mandat expiré (au sens de l'horloge du scheduler) -> le job désactive le compte.
        LocalDate finExpire = LocalDate.now(horloge).minusDays(10);
        Mandat premier = seedMandat("PRMP001", "ARR-PREMIER", -10);
        premier.setDateFin(finExpire);
        mandatRepository.save(premier);

        scheduler.alerterFinMandat();
        assertThat(compteActif("PRMP001")).isFalse();

        // 2) Reconduction réelle (MandatService.creer juge l'activité au jour RÉEL du système, non
        //    à l'horloge du scheduler) : bornes couvrant LocalDate.now() (réel).
        LocalDate debutReconduction = finExpire.plusDays(1);
        CreerMandatRequest req = new CreerMandatRequest("PRMP001", "ARR-RECONDUCTION", debutReconduction, null, null);
        mandatService.creer(req);

        // 3) Le compte est rouvert par le déblocage automatique de la reconduction (lot B).
        assertThat(compteActif("PRMP001")).isTrue();
    }
}
