package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.dto.DaoCompletEtatDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.DaoCompletFile;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.StatutFicheMarche;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.repository.DaoCompletFileRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.UgpmRepository;
import jakarta.annotation.PreDestroy;

/**
 * ⚠️ <strong>Le DAO complet produit en file</strong> (demande front du 2026-10-28, « dao-complet-en-file », Q1-Q5 ; V144). Word mettait
 * 20 s (fournitures) à 1 min 23 s (travaux) à assembler un DAO, sous un verrou unique, pendant que la validation gardait sa transaction
 * — et sa connexion — ouverte : dix validations simultanées prenaient les dix connexions du pool.
 * <ul>
 *   <li><strong>Q1</strong> — la validation inscrit la version en file ({@link #mettreEnFile}, dans sa transaction : une validation
 *       annulée n'y entre pas) et réveille l'ouvrier après la validation de la transaction ;</li>
 *   <li><strong>Q2</strong> — un <strong>ouvrier unique</strong> (un fil) prend les versions dans l'ordre d'arrivée, la tête de file
 *       d'abord ({@link #mettreEnTete}, l'avis spécifique) : lecture en transaction courte, Word <strong>hors transaction</strong>,
 *       enregistrement en transaction courte. Un échec remet la version en file avec son motif, reprise après
 *       {@code app.dao-complet.reprise-ms} ; au {@code app.dao-complet.essais}<sup>e</sup>, {@code ECHEC} ;</li>
 *   <li><strong>Q3</strong> — {@link #etat} ; <strong>Q4</strong> — la PRMP du plan et ses UGPM sont prévenues du DAO prêt,
 *       l'Administrateur de l'abandon ; <strong>Q5</strong> — {@link #exigerPret} à la soumission.</li>
 * </ul>
 * Le DAO complet prêt rejoint le dossier de la fiche s'il existe ({@link DocumentsFicheMarcheService#joindre} : brouillon ou
 * rectification seulement). Le rattrapage périodique ({@link DaoCompletRattrapage}) inscrit les versions oubliées et réveille l'ouvrier.
 */
@Service
public class DaoCompletFileService {

    private static final Logger log = LoggerFactory.getLogger(DaoCompletFileService.class);

    /** Q5 — la soumission attend le DAO complet en préparation. */
    public static final String DAO_COMPLET_EN_PREPARATION = "DAO_COMPLET_EN_PREPARATION";
    /** Q3 — serveur sans assemblage. */
    public static final String SANS_WORD = "SANS_WORD";

    private final DaoCompletFileRepository repository;
    private final DaoCompletService daoComplet;
    private final FicheMarcheRepository ficheRepository;
    private final ObjectProvider<FicheMarcheService> fiches;
    private final DocumentsFicheMarcheService documents;
    private final DossierRepository dossierRepository;
    private final MarcheRepository marcheRepository;
    private final PrmpRepository prmpRepository;
    private final UgpmRepository ugpmRepository;
    private final ControleurDirectory controleurs;
    private final NotificationService notifications;
    private final TransactionTemplate courte;
    private final TransactionTemplate apres;
    private final int essaisMax;
    private final long repriseMs;
    /** Faux en test : l'ouvrier ne part pas tout seul, le test appelle {@link #traiterTout}. */
    private final boolean auto;

    private final ExecutorService ouvrier = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dao-complet");
        t.setDaemon(true);
        return t;
    });
    /** Un seul passage en attente : réveiller dix fois de suite ne programme qu'un passage de plus. */
    private final AtomicBoolean reveilProgramme = new AtomicBoolean();

    public DaoCompletFileService(DaoCompletFileRepository repository, DaoCompletService daoComplet, FicheMarcheRepository ficheRepository,
            ObjectProvider<FicheMarcheService> fiches, DocumentsFicheMarcheService documents, DossierRepository dossierRepository,
            MarcheRepository marcheRepository, PrmpRepository prmpRepository, UgpmRepository ugpmRepository, ControleurDirectory controleurs,
            NotificationService notifications, PlatformTransactionManager transactions,
            @Value("${app.dao-complet.essais:3}") int essaisMax, @Value("${app.dao-complet.reprise-ms:600000}") long repriseMs,
            @Value("${app.dao-complet.file-auto:true}") boolean auto) {
        this.repository = repository;
        this.daoComplet = daoComplet;
        this.ficheRepository = ficheRepository;
        this.fiches = fiches;
        this.documents = documents;
        this.dossierRepository = dossierRepository;
        this.marcheRepository = marcheRepository;
        this.prmpRepository = prmpRepository;
        this.ugpmRepository = ugpmRepository;
        this.controleurs = controleurs;
        this.notifications = notifications;
        // L'ouvrier tourne sur son propre fil, hors de toute transaction : chaque étape en ouvre une, courte, et la referme.
        this.courte = new TransactionTemplate(transactions);
        this.courte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        // Le rejeu de la mise en tête, depuis afterCompletion : la transaction d'origine est refermée mais encore attachée au fil.
        this.apres = new TransactionTemplate(transactions);
        this.apres.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.essaisMax = Math.max(1, essaisMax);
        this.repriseMs = repriseMs;
        this.auto = auto;
    }

    public boolean actif() {
        return daoComplet.actif();
    }

    // ------------------------------------------------------------------ la file

    /**
     * Q1 — inscrit une version qui vient d'être validée, dans la transaction de la validation ; l'ouvrier est réveillé après sa
     * validation. Sans assemblage sur ce serveur : rien (les documents séparés font le DAO).
     */
    public void mettreEnFile(FicheMarche fiche) {
        if (!actif() || fiche == null || fiche.getIdFiche() == null) {
            return;
        }
        LocalDateTime maintenant = LocalDateTime.now(HorlogeRecette.HORLOGE);
        DaoCompletFile f = repository.findById(fiche.getIdFiche()).orElse(null);
        if (f == null) {
            repository.save(new DaoCompletFile(fiche.getIdFiche(), fiche.getIdDmc(), maintenant));
        } else if (!DaoCompletFile.EN_COURS.equals(f.getEtat())) {
            relancer(f, maintenant, f.isPrioritaire());
            repository.save(f);
        }
        reveillerApresValidation();
    }

    /**
     * Q2 — l'avis spécifique (ou les lettres) attend ce DAO complet : la version passe en tête de file. Une version abandonnée
     * ({@code ECHEC}) repart pour une nouvelle série d'essais. Rend le rang (1 = le prochain), {@code null} si Word l'assemble déjà.
     * <p>
     * L'impression est refusée en 409 juste après, ce qui annule la transaction de la requête : la mise en tête est alors
     * <strong>rejouée</strong> dans une transaction à elle, une fois la première refermée (une transaction séparée ouverte pendant la
     * première attendrait les verrous de celle-ci).
     */
    public Integer mettreEnTete(FicheMarche fiche) {
        if (!actif() || fiche == null || fiche.getIdFiche() == null) {
            return null;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            Integer rang = courte.execute(s -> enTete(fiche));
            reveiller();
            return rang;
        }
        Integer rang = enTete(fiche);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int statut) {
                if (statut != STATUS_COMMITTED) {
                    try {
                        apres.execute(s -> enTete(fiche));
                    } catch (RuntimeException e) {
                        log.warn("[DAO_COMPLET] fiche {} : mise en tête de file non enregistrée ({})", fiche.getIdFiche(), e.getMessage());
                        return;
                    }
                }
                reveiller();
            }
        });
        return rang;
    }

    private Integer enTete(FicheMarche fiche) {
        LocalDateTime maintenant = LocalDateTime.now(HorlogeRecette.HORLOGE);
        DaoCompletFile f = repository.findById(fiche.getIdFiche()).orElseGet(() -> new DaoCompletFile(fiche.getIdFiche(), fiche.getIdDmc(), maintenant));
        if (!DaoCompletFile.EN_COURS.equals(f.getEtat())) {
            relancer(f, maintenant, true);
            f.setRepriseApres(null);
        }
        repository.save(f);
        return rang(f.getIdFiche());
    }

    /** Remet une ligne en file : nouvelle série d'essais si elle était abandonnée ou prête (DAO complet effacé depuis). */
    private static void relancer(DaoCompletFile f, LocalDateTime maintenant, boolean prioritaire) {
        if (!DaoCompletFile.EN_FILE.equals(f.getEtat())) {
            f.setDepuis(maintenant);
            f.setEssais(0);
            f.setRepriseApres(null);
        }
        f.setEtat(DaoCompletFile.EN_FILE);
        f.setPrioritaire(prioritaire);
    }

    /** Le rattrapage : la dernière version validée qui a ses documents mais pas de DAO complet, ni de place dans la file. */
    void inscrireOubliee(FicheMarche fiche) {
        courte.executeWithoutResult(s -> {
            DaoCompletFile f = repository.findById(fiche.getIdFiche()).orElse(null);
            LocalDateTime maintenant = LocalDateTime.now(HorlogeRecette.HORLOGE);
            if (f == null) {
                repository.save(new DaoCompletFile(fiche.getIdFiche(), fiche.getIdDmc(), maintenant));
                log.info("[DAO_COMPLET] DMC {} version {} : inscrite en file par le rattrapage", fiche.getIdDmc(), fiche.getNumeroVersion());
            } else if (DaoCompletFile.PRET.equals(f.getEtat())) {
                relancer(f, maintenant, false);   // le DAO complet a été retiré (migration de gabarit) : il se reproduit
                repository.save(f);
            }
        });
    }

    // ------------------------------------------------------------------ Q3, Q5

    /**
     * Q3 — l'état du DAO complet de la dernière version validée ; {@code null} sans version validée, ou pour une version qui n'a ni
     * DAO complet ni place dans la file (validée avant la production des documents).
     */
    public DaoCompletEtatDto etat(Long idDmc) {
        FicheMarche validee = idDmc == null ? null : derniereValidee(idDmc);
        if (validee == null) {
            return null;
        }
        DaoCompletFile f = repository.findById(validee.getIdFiche()).orElse(null);
        if (daoComplet.existe(validee.getIdFiche())) {
            return new DaoCompletEtatDto(DaoCompletFile.PRET, null, f != null && DaoCompletFile.PRET.equals(f.getEtat()) ? f.getDepuis() : null, null);
        }
        if (!actif()) {
            return new DaoCompletEtatDto(SANS_WORD, null, null, null);
        }
        if (f == null) {
            return null;
        }
        boolean enFile = DaoCompletFile.EN_FILE.equals(f.getEtat());
        // Une ligne PRET sans document : le DAO complet a été retiré, le rattrapage la remet en file.
        String etat = DaoCompletFile.PRET.equals(f.getEtat()) ? DaoCompletFile.EN_FILE : f.getEtat();
        return new DaoCompletEtatDto(etat, enFile ? rang(f.getIdFiche()) : null, f.getDepuis(),
                enFile || DaoCompletFile.ECHEC.equals(f.getEtat()) ? f.getMotif() : null);
    }

    /**
     * Q5 (H1) — la Commission examine le DAO tel que les candidats le recevront : la soumission (et la resoumission) d'un dossier né
     * d'une fiche est refusée tant que son DAO complet est {@code EN_FILE} ou {@code EN_COURS} — 409
     * {@code DAO_COMPLET_EN_PREPARATION} {@code { rang }}. En {@code SANS_WORD}, {@code ECHEC} ou sans place dans la file, la règle
     * d'avant s'applique (les documents séparés).
     */
    public void exigerPret(Long idDmc, Integer idDossier) {
        DaoCompletEtatDto e = etat(idDmc);
        if (e == null || !DaoCompletFile.EN_FILE.equals(e.etat()) && !DaoCompletFile.EN_COURS.equals(e.etat())) {
            return;
        }
        String ou = e.rang() != null ? " (n° " + e.rang() + " dans la file)" : " (assemblage en cours)";
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("rang", e.rang());
        throw new BusinessRuleException("Le DAO complet est en préparation" + ou + " : la Commission examine le DAO tel que les "
                + "candidats le recevront. Réessayez quand il sera prêt ; vous serez prévenu.", DAO_COMPLET_EN_PREPARATION, idDossier, details);
    }

    private Integer rang(Integer idFiche) {
        List<DaoCompletFile> file = repository.enFile();
        for (int i = 0; i < file.size(); i++) {
            if (file.get(i).getIdFiche().equals(idFiche)) {
                return i + 1;
            }
        }
        return null;
    }

    private FicheMarche derniereValidee(Long idDmc) {
        return ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                .filter(x -> StatutFicheMarche.VALIDEE.name().equals(x.getStatut()))
                .reduce((a, b) -> b).orElse(null);
    }

    // ------------------------------------------------------------------ Q2 — l'ouvrier

    /** Au démarrage : un assemblage interrompu par l'arrêt retourne en file, puis l'ouvrier reprend la file. */
    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrage() {
        if (!actif()) {
            return;
        }
        Integer remises = courte.execute(s -> repository.remettreEnFile(LocalDateTime.now(HorlogeRecette.HORLOGE)));
        if (remises != null && remises > 0) {
            log.info("[DAO_COMPLET] {} assemblage(s) interrompu(s) par l'arrêt remis en file", remises);
        }
        reveiller();
    }

    /** Réveille l'ouvrier après la validation de la transaction courante (tout de suite s'il n'y en a pas). */
    private void reveillerApresValidation() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reveiller();
                }
            });
        } else {
            reveiller();
        }
    }

    /** Programme un passage de l'ouvrier (un seul en attente à la fois). */
    public void reveiller() {
        if (!auto || !actif() || !reveilProgramme.compareAndSet(false, true)) {
            return;
        }
        ouvrier.submit(() -> {
            reveilProgramme.set(false);
            try {
                traiterTout();
            } catch (RuntimeException e) {
                log.warn("[DAO_COMPLET] ouvrier : {}", e.getMessage(), e);
            }
        });
    }

    /** Vide la file : une version à la fois, jusqu'à ce qu'aucune ne soit prenable. Rend le nombre de versions traitées. */
    public int traiterTout() {
        int n = 0;
        Integer idFiche;
        while ((idFiche = prochaine()) != null) {
            traiter(idFiche);
            n++;
        }
        return n;
    }

    private Integer prochaine() {
        return courte.execute(s -> repository.prenables(LocalDateTime.now(HorlogeRecette.HORLOGE)).stream()
                .map(DaoCompletFile::getIdFiche).findFirst().orElse(null));
    }

    /** Une version : lire (transaction courte), assembler (hors transaction), enregistrer (transaction courte). */
    void traiter(Integer idFiche) {
        DaoCompletService.Preparation p;
        try {
            p = courte.execute(s -> preparer(idFiche));
        } catch (RuntimeException e) {
            echouer(idFiche, e);
            return;
        }
        if (p == null) {
            return;
        }
        DaoCompletWord.Resultat r;
        try {
            r = daoComplet.assembler(p);
        } catch (RuntimeException e) {
            echouer(idFiche, e);
            return;
        }
        try {
            courte.executeWithoutResult(s -> enregistrer(p, r));
        } catch (RuntimeException e) {
            echouer(idFiche, e);
            return;
        }
        // Le DAO complet est enregistré : joindre et prévenir ne peuvent plus le défaire (chacun dans sa transaction).
        Integer idDossier = null;
        try {
            idDossier = courte.execute(s -> joindre(p.fiche().getIdDmc()));
        } catch (RuntimeException e) {
            log.warn("[DAO_COMPLET] DMC {} : DAO complet non joint au dossier ({})", p.fiche().getIdDmc(), e.getMessage());
        }
        Integer dossier = idDossier;
        try {
            courte.executeWithoutResult(s -> notifierPret(p, dossier));
        } catch (RuntimeException e) {
            log.warn("[DAO_COMPLET] DMC {} : notification du DAO complet prêt non émise ({})", p.fiche().getIdDmc(), e.getMessage());
        }
    }

    /** {@code null} : rien à assembler (plus en file, version remplacée par une plus récente, DAO complet déjà là). */
    private DaoCompletService.Preparation preparer(Integer idFiche) {
        DaoCompletFile f = repository.findById(idFiche).orElse(null);
        if (f == null || !DaoCompletFile.EN_FILE.equals(f.getEtat())) {
            return null;
        }
        LocalDateTime maintenant = LocalDateTime.now(HorlogeRecette.HORLOGE);
        FicheMarche fiche = ficheRepository.findById(idFiche).orElse(null);
        Optional<FicheMarcheService.EtatVersion> ev = fiche == null ? Optional.empty() : fiches.getObject().etatValide(fiche.getIdDmc());
        if (ev.isEmpty() || !java.util.Objects.equals(ev.get().version(), fiche.getNumeroVersion())) {
            repository.delete(f);   // une révision plus récente a été validée : c'est elle qui compte
            log.info("[DAO_COMPLET] fiche {} : version remplacée, retirée de la file", idFiche);
            return null;
        }
        if (daoComplet.existe(idFiche)) {
            f.setEtat(DaoCompletFile.PRET);
            f.setDepuis(maintenant);
            repository.save(f);
            return null;
        }
        f.setEtat(DaoCompletFile.EN_COURS);
        f.setDepuis(maintenant);
        repository.save(f);
        return daoComplet.preparer(fiche, ev.get().etat());
    }

    /** Enregistre les deux fichiers et marque la ligne prête. */
    private void enregistrer(DaoCompletService.Preparation p, DaoCompletWord.Resultat r) {
        daoComplet.enregistrer(p, r);
        repository.findById(p.fiche().getIdFiche()).ifPresent(f -> {
            f.setEtat(DaoCompletFile.PRET);
            f.setDepuis(LocalDateTime.now(HorlogeRecette.HORLOGE));
            f.setMotif(null);
            f.setRepriseApres(null);
            repository.save(f);
        });
    }

    /** Joint le DAO complet au dossier de la fiche s'il existe (brouillon ou rectification : il remplace les séparés) ; rend ce dossier. */
    private Integer joindre(Long idDmc) {
        Integer idDossier = dossierRepository.findIdDossierByIdDmc(idDmc).orElse(null);
        if (idDossier != null) {
            documents.joindre(idDossier, idDmc);
        }
        return idDossier;
    }

    /** Un échec : la version reste en file, reprise plus tard ; au dernier essai, {@code ECHEC} et l'Administrateur est prévenu. */
    private void echouer(Integer idFiche, RuntimeException e) {
        String motif = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (motif.length() > 1000) {
            motif = motif.substring(0, 997) + "...";
        }
        String m = motif;
        DaoCompletFile abandon = courte.execute(s -> {
            DaoCompletFile f = repository.findById(idFiche).orElse(null);
            if (f == null) {
                return null;
            }
            LocalDateTime maintenant = LocalDateTime.now(HorlogeRecette.HORLOGE);
            f.setEssais(f.getEssais() + 1);
            f.setMotif(m);
            f.setDepuis(maintenant);
            boolean fin = f.getEssais() >= essaisMax;
            f.setEtat(fin ? DaoCompletFile.ECHEC : DaoCompletFile.EN_FILE);
            f.setRepriseApres(fin ? null : maintenant.plus(java.time.Duration.ofMillis(repriseMs)));
            repository.save(f);
            return fin ? f : null;
        });
        log.warn("[DAO_COMPLET] fiche {} non assemblée{} : {}", idFiche, abandon != null ? " (abandon)" : " (reprise plus tard)", motif);
        if (abandon != null) {
            try {
                courte.executeWithoutResult(s -> notifierEchec(abandon));
            } catch (RuntimeException ex) {
                log.warn("[DAO_COMPLET] fiche {} : notification de l'échec non émise ({})", idFiche, ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ Q4 — les notifications

    private void notifierPret(DaoCompletService.Preparation p, Integer idDossier) {
        FicheMarcheDto etat = p.etat();
        FicheMarche fiche = p.fiche();
        String idPrmp = etat.getIdDetail() == null ? null : marcheRepository.findIdDossierByIdDetail(etat.getIdDetail())
                .flatMap(dossierRepository::findById).map(Dossier::getIdPrmp).orElse(null);
        if (idPrmp == null) {
            return;
        }
        String objet = etat.getDesignationMarche() == null || etat.getDesignationMarche().isBlank() ? "la fiche n° " + fiche.getIdDmc()
                : etat.getDesignationMarche();
        String titre = "DAO complet prêt";
        String corps = "Le DAO complet de " + objet + " (version " + fiche.getNumeroVersion() + ") est prêt.";
        Integer idObjet = fiche.getIdDmc().intValue();
        String email = prmpRepository.findById(idPrmp).map(Prmp::getEmailPrmp).orElse(null);
        notifications.emettrePrmp(TypeNotification.DAO_COMPLET_PRET, idPrmp, email, idObjet, TypeObjet.PROCEDURE, idDossier, titre, corps);
        for (Ugpm u : ugpmRepository.findByIdPrmpTutelle(idPrmp)) {
            notifications.emettreUgpm(TypeNotification.DAO_COMPLET_PRET, u.getIdUgpm(), idPrmp, u.getEmailUgpm(), idObjet, TypeObjet.PROCEDURE,
                    idDossier, titre, corps);
        }
    }

    private void notifierEchec(DaoCompletFile f) {
        FicheMarche fiche = ficheRepository.findById(f.getIdFiche()).orElse(null);
        String version = fiche == null ? "" : " (version " + fiche.getNumeroVersion() + ")";
        String titre = "DAO complet non assemblé";
        String corps = "Le DAO complet de la fiche n° " + f.getIdDmc() + version + " n'a pas pu être assemblé après " + f.getEssais()
                + " essai(s) : " + f.getMotif() + ". Les documents séparés restent servis ; l'impression de l'avis le remet en file.";
        for (Controleur admin : controleurs.administrateurs()) {
            notifications.emettreControleur(TypeNotification.DAO_COMPLET_ECHEC, admin.getImControleur(), admin.getEmailCont(),
                    f.getIdDmc().intValue(), TypeObjet.PROCEDURE, null, titre, corps);
        }
    }

    @PreDestroy
    void arreter() {
        ouvrier.shutdownNow();
    }

}
