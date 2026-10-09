package cnm.prs.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.AlerteExamen;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.SuspensionDossier;
import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.repository.AlerteExamenRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.DossierRepository;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M5b, §B6 ; V99 ; arbitrage du pilote : alerte au Membre, au Chef de commission et
 * au Président) — « le délai de traitement maximum d'un dossier est de 5 jours ouvrés » (manuel, ch. 1, V) : un dossier en examen
 * depuis plus de {@code app.examen.alerte-heures-ouvrees} heures ouvrées (40 par défaut, soit 5 jours de 8 h) — mesurées comme le
 * chronométrage, de l'entrée dans l'étape à maintenant — déclenche {@code EXAMEN_EN_DEPASSEMENT} vers le Membre examinateur, le Chef de
 * commission du dispatch et le(s) Président(s). Une seule alerte par passage en examen ({@code t_alerte_examen}) : un réexamen, qui
 * rouvre un passage, peut en déclencher une nouvelle. Suivi horaire ({@code app.examen.cron-alertes}).
 */
@Service
public class AlerteExamenService {

    private static final Logger log = LoggerFactory.getLogger(AlerteExamenService.class);

    static final List<String> STATUTS_EN_EXAMEN = List.of(StatutDossier.DISPATCHE.name(), StatutDossier.A_REEXAMINER.name());

    private final DossierRepository dossiers;
    private final DispatchRepository dispatchs;
    private final ControleurRepository controleurs;
    private final ControleurDirectory annuaire;
    private final AlerteExamenRepository alertes;
    private final ChronometrageService chronometrage;
    private final DelaiStandardService delais;
    private final NotificationService notifications;
    private final Clock clock;
    private final int seuilHeures;

    public AlerteExamenService(DossierRepository dossiers, DispatchRepository dispatchs, ControleurRepository controleurs,
            ControleurDirectory annuaire, AlerteExamenRepository alertes, ChronometrageService chronometrage, DelaiStandardService delais,
            NotificationService notifications, Clock clock, @Value("${app.examen.alerte-heures-ouvrees:40}") int seuilHeures) {
        this.dossiers = dossiers;
        this.dispatchs = dispatchs;
        this.controleurs = controleurs;
        this.annuaire = annuaire;
        this.alertes = alertes;
        this.chronometrage = chronometrage;
        this.delais = delais;
        this.notifications = notifications;
        this.clock = clock;
        this.seuilHeures = seuilHeures;
    }

    @Scheduled(cron = "${app.examen.cron-alertes:0 5 * * * *}")
    public void planifie() {
        int n = verifier();
        if (n > 0) {
            log.info("[ALERTE] examens en dépassement signalés : {}", n);
        }
    }

    /** Signale les examens en dépassement pas encore signalés pour leur passage ; rend leur nombre. */
    @Transactional
    public int verifier() {
        List<Dossier> enExamen = dossiers.findByStatutIn(STATUTS_EN_EXAMEN);
        if (enExamen.isEmpty()) {
            return 0;
        }
        List<Integer> ids = enExamen.stream().map(Dossier::getIdDossier).toList();
        Map<Integer, List<TacheDossier>> taches = chronometrage.tachesParDossier(ids);
        Map<Integer, List<SuspensionDossier>> suspensions = chronometrage.suspensionsParDossier(ids);
        DelaiStandardService.Referentiel referentiel = delais.referentiel();
        LocalDateTime maintenant = LocalDateTime.now(clock);
        int signales = 0;
        for (Dossier d : enExamen) {
            ChronometrageService.DelaiCourant c = chronometrage.delaiCourant(d.getStatut(), null,
                    taches.getOrDefault(d.getIdDossier(), List.of()), suspensions.getOrDefault(d.getIdDossier(), List.of()),
                    d.getDateSoumission(), maintenant, referentiel.pour(d.getIdSousType()));
            if (c.etape() != EtapeCircuit.EXAMEN || c.entree() == null || c.ecouleHeures() == null || c.ecouleHeures() <= seuilHeures
                    || alertes.existsByIdDossierAndEntree(d.getIdDossier(), c.entree())) {
                continue;
            }
            AlerteExamen a = new AlerteExamen();
            a.setIdDossier(d.getIdDossier());
            a.setEntree(c.entree());
            a.setEcouleHeures(c.ecouleHeures().intValue());
            a.setAlerteLe(maintenant);
            alertes.save(a);
            notifier(d, c.ecouleHeures());
            signales++;
        }
        return signales;
    }

    private void notifier(Dossier d, long ecoule) {
        Set<String> destinataires = new LinkedHashSet<>();
        List<Object[]> acteurs = dispatchs.findActeursParDossier(d.getIdDossier());
        if (!acteurs.isEmpty()) {
            Object[] dernier = acteurs.get(0);
            if (dernier[0] != null) {
                destinataires.add((String) dernier[0]);
            }
            if (dernier[1] != null) {
                destinataires.add((String) dernier[1]);
            }
        }
        annuaire.presidents().forEach(p -> destinataires.add(p.getImControleur()));
        String reference = d.getRefeDossier() != null ? d.getRefeDossier() : "n° " + d.getIdDossier();
        String titre = "Examen en dépassement";
        String corps = "Le dossier " + reference + " est en examen depuis " + ecoule + " heures ouvrées, au-delà des 5 jours ouvrés du "
                + "manuel de contrôle (" + seuilHeures + " heures ouvrées).";
        for (String im : destinataires) {
            String email = controleurs.findById(im).map(Controleur::getEmailCont).orElse(null);
            notifications.emettreControleur(TypeNotification.EXAMEN_EN_DEPASSEMENT, im, email, d.getIdDossier(), TypeObjet.DOSSIER,
                    d.getIdDossier(), titre, corps);
        }
    }
}
