package cnm.prs.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CompteursAdminDto;
import cnm.prs.dto.CompteursAssistantDto;
import cnm.prs.dto.CompteursDto;
import cnm.prs.dto.CompteursMembreDto;
import cnm.prs.dto.CompteursPrmpDto;
import cnm.prs.dto.CompteursPublicationDto;
import cnm.prs.dto.CompteursSecretaireDto;
import cnm.prs.dto.CompteursVerificateurDto;
import cnm.prs.dto.PointNonConformiteDto;
import cnm.prs.dto.TableauBordDto;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutCompte;
import cnm.prs.enums.StatutDemandeEntite;
import cnm.prs.enums.StatutLettreRenvoi;
import cnm.prs.enums.StatutPublication;
import cnm.prs.enums.StatutPv;
import cnm.prs.enums.StatutRetrait;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.AuditLogRepository;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.DemandeRetraitVueRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenDetailRepository;
import cnm.prs.repository.LettreRenvoiRepository;
import cnm.prs.repository.MandatRepository;
import cnm.prs.repository.PieceJointeRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpEntiteDemandeRepository;
import cnm.prs.repository.PublicationRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.VerificationRepository;
import cnm.prs.security.CurrentUser;

/**
 * Calcul des KPIs du tableau de bord (§3.2, §3.7, §3.8) à partir des tables opérationnelles :
 * pipeline par statut, taux de conformité, top non-conformité par point de contrôle.
 */
@Service
@Transactional(readOnly = true)
public class KpiService {

    /**
     * ⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B1) — fenêtre de surveillance des
     * mandats PRMP : le bloc « À surveiller » de l'accueil compte ceux dont le terme tombe dans les
     * {@value} prochains jours.
     */
    private static final int PREAVIS_MANDAT_JOURS = 30;

    private final DossierRepository dossierRepository;
    private final VerificationRepository verificationRepository;
    private final ExamenDetailRepository examenDetailRepository;
    private final PvExamenRepository pvExamenRepository;
    private final LettreRenvoiRepository lettreRenvoiRepository;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final PpmRepository ppmRepository;
    private final ReceptionRepository receptionRepository;
    private final PublicationRepository publicationRepository;
    private final CompteAuthRepository compteAuthRepository;
    private final AuditLogRepository auditLogRepository;
    private final DemandeRetraitVueRepository demandeRetraitVueRepository;
    /** ⚠️ Lot 6 (2026-09-17, §B1) — files et ancienneté de l'accueil de l'Administrateur. */
    private final PrmpEntiteDemandeRepository prmpEntiteDemandeRepository;
    private final PieceJointeRepository pieceJointeRepository;
    private final MandatRepository mandatRepository;
    /** ⚠️ 2026-09-15 — le badge « À faire » vient du calcul de l'accueil lui-même, jamais d'un comptage parallèle. */
    private final AFaireService aFaireService;

    public KpiService(DossierRepository dossierRepository, VerificationRepository verificationRepository,
            ExamenDetailRepository examenDetailRepository, PvExamenRepository pvExamenRepository,
            LettreRenvoiRepository lettreRenvoiRepository, DemandeRetraitRepository demandeRetraitRepository,
            PpmRepository ppmRepository, ReceptionRepository receptionRepository,
            PublicationRepository publicationRepository, CompteAuthRepository compteAuthRepository,
            AuditLogRepository auditLogRepository, DemandeRetraitVueRepository demandeRetraitVueRepository,
            PrmpEntiteDemandeRepository prmpEntiteDemandeRepository, PieceJointeRepository pieceJointeRepository,
            MandatRepository mandatRepository, AFaireService aFaireService) {
        this.aFaireService = aFaireService;
        this.demandeRetraitVueRepository = demandeRetraitVueRepository;
        this.prmpEntiteDemandeRepository = prmpEntiteDemandeRepository;
        this.pieceJointeRepository = pieceJointeRepository;
        this.mandatRepository = mandatRepository;
        this.dossierRepository = dossierRepository;
        this.verificationRepository = verificationRepository;
        this.examenDetailRepository = examenDetailRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.lettreRenvoiRepository = lettreRenvoiRepository;
        this.demandeRetraitRepository = demandeRetraitRepository;
        this.ppmRepository = ppmRepository;
        this.receptionRepository = receptionRepository;
        this.publicationRepository = publicationRepository;
        this.compteAuthRepository = compteAuthRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Tableau de bord du contrôleur courant : <strong>global</strong> pour le Président et
     * l'Administrateur ; <strong>filtré sur sa localité</strong> pour le Chef de commission (§3.3).
     */
    public TableauBordDto tableauBord() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == ProfilUtilisateur.CHEF_COMMISSION) {
            String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
            if (localite == null) {
                return new TableauBordDto(new LinkedHashMap<>(), 0, 0, 0.0, List.of(),
                        new CompteursDto(0, 0, 0, 0, 0, 0));
            }
            return calculer(localite);
        }
        return calculer(null); // Président / Administrateur : toutes localités
    }

    /**
     * ⚠️ Audit front (2026-08-16) — badges de menu agrégés : route sur le profil du connecté et renvoie
     * ses compteurs en UN appel (mêmes DTOs que les endpoints {@code mes-compteurs*} existants — le
     * front réutilise ses lecteurs). Président / Chef de commission : les compteurs du tableau de bord
     * (dont « prêts à dispatcher »), globaux pour le Président, filtrés localité pour le CC.
     */
    public cnm.prs.dto.BadgesDto badges() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == null) {
            return new cnm.prs.dto.BadgesDto(null, Map.of(), null);
        }
        Object compteurs = switch (profil) {
            case PRMP -> mesCompteursPrmp();
            case PRESIDENT -> compteurs(null);
            case CHEF_COMMISSION -> {
                String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
                yield localite == null ? new CompteursDto(0, 0, 0, 0, 0, 0) : compteurs(localite);
            }
            case SECRETAIRE -> mesCompteursSecretaire();
            case MEMBRE -> mesCompteursMembre();
            case VERIFICATEUR -> mesCompteursVerificateur();
            case ASSISTANT_CONTROLEUR -> mesCompteursAssistant();
            case CHARGE_PUBLICATION -> mesCompteursPublication();
            case ADMINISTRATEUR -> mesCompteursAdmin();
            default -> Map.of();
        };
        // ⚠️ 2026-09-15 — badge de l'accueil « À faire » : compteurs.aFaire du même calcul (lignes titulaires, hors
        // suivi, hors bloc délégation) ; null pour l'Administrateur et le Chargé de publication.
        return new cnm.prs.dto.BadgesDto(profil.name(), compteurs, aFaireService.compterAFaire());
    }

    /**
     * Compteurs de contenu du menu PRMP — tous filtrés sur la PRMP authentifiée (JWT) : brouillons,
     * PPM &amp; marchés, dossiers à rectifier non traités ({@code EN_ATTENTE_DECISION_PRMP}), dossiers
     * vérifiés ({@code PV_SIGNE}/{@code CLOTURE}), lettres de renvoi signées. PRMP non identifiée → zéros.
     */
    public CompteursPrmpDto mesCompteursPrmp() {
        String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (idPrmp == null) {
            return new CompteursPrmpDto(0, 0, 0, 0, 0, 0);
        }
        // ⚠️ Décision métier 2026-08-27 — les lettres non lues se comptent par AGENT (login, claim
        // « sub ») et non plus par tutelle : la lecture d'une UGPM ne décrémente plus le badge de sa
        // PRMP. Le périmètre des lettres, lui, reste la tutelle (idPrmp). Login absent → tout non lu.
        String login = CurrentUser.login().filter(s -> !s.isBlank()).orElse("");
        // Demandes décidées (ACCEPTEE/REFUSEE) depuis la dernière consultation de l'écran (sinon tout l'historique).
        java.time.LocalDateTime seuil = demandeRetraitVueRepository.findByIdPrmp(idPrmp)
                .map(cnm.prs.entity.DemandeRetraitVue::getDateDerniereVue)
                .orElse(java.time.LocalDateTime.of(1970, 1, 1, 0, 0));
        return new CompteursPrmpDto(
                dossierRepository.countByStatutAndIdPrmp(StatutDossier.BROUILLON.name(), idPrmp),
                ppmRepository.countVisiblesParPrmp(idPrmp),   // « Mes PPM & marchés » — hors BROUILLON (colle à la liste)
                dossierRepository.countByStatutAndIdPrmp(StatutDossier.EN_ATTENTE_DECISION_PRMP.name(), idPrmp),
                dossierRepository.countByStatutInAndIdPrmp(
                        List.of(StatutDossier.PV_SIGNE.name(), StatutDossier.CLOTURE.name()), idPrmp),
                lettreRenvoiRepository.countSigneesNonLuesPourPrmp(idPrmp, login),
                demandeRetraitRepository.countNouvellesDecisionsPourPrmp(idPrmp, seuil));
    }

    /**
     * Compteurs de contenu du menu Contrôleur vérificateur — filtrés sur sa localité, miroir de ses
     * trois worklists : à vérifier, vérifiés/clôturés, en attente de décision PRMP. Sans localité → zéros.
     */
    public CompteursVerificateurDto mesCompteursVerificateur() {
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localite == null) {
            return new CompteursVerificateurDto(0, 0, 0);
        }
        return new CompteursVerificateurDto(
                dossierRepository.countAVerifierParLocalite(localite),
                dossierRepository.countVerifiesParLocalite(localite),
                dossierRepository.countEnAttentePrmpParLocalite(localite));
    }

    /**
     * Compteurs de contenu du menu Secrétaire — filtrés sur sa localité : dossiers à réceptionner
     * ({@code SOUMIS} sans réception) et réceptions enregistrées dans sa localité. Sans localité → zéros.
     */
    public CompteursSecretaireDto mesCompteursSecretaire() {
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localite == null) {
            return new CompteursSecretaireDto(0, 0);
        }
        return new CompteursSecretaireDto(
                dossierRepository.countAReceptionnerParLocalite(localite),
                receptionRepository.countByLocalite(localite));
    }

    /**
     * Compteurs de contenu du menu Membre — filtrés sur le Membre attributaire (son IM) : dossiers à
     * examiner ({@code DISPATCHE}) et examinés ({@code EXAMINE}/{@code PV_SIGNE}/{@code EN_VERIFICATION}/
     * {@code CLOTURE}). Membre non identifié → zéros.
     */
    public CompteursMembreDto mesCompteursMembre() {
        String im = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (im == null) {
            return new CompteursMembreDto(0, 0);
        }
        List<String> examines = List.of(StatutDossier.EXAMINE.name(), StatutDossier.PV_SIGNE.name(),
                StatutDossier.EN_VERIFICATION.name(), StatutDossier.CLOTURE.name());
        // ⚠️ 2026-08-02 — A_REEXAMINER (réexamen après lettre de renvoi) compte dans « à examiner ».
        return new CompteursMembreDto(
                dossierRepository.countAExaminerParMembre(
                        List.of(StatutDossier.DISPATCHE.name(), StatutDossier.A_REEXAMINER.name()), im),
                dossierRepository.countExaminesParMembre(examines, im));
    }

    /**
     * Compteurs de contenu du menu Administrateur — comptes <strong>globaux</strong> (rôle transversal) :
     * inscriptions PRMP en attente de validation, total des comptes d'authentification, total des entrées
     * du journal d'audit.
     *
     * <p>⚠️ Lot 6 (2026-09-17, §B1) — enrichis pour l'accueil de l'Administrateur, <strong>sans migration
     * ni route neuve</strong> (c'est {@code GET /api/kpis/badges} qui porte le tout). Les trois compteurs
     * d'origine sont inchangés, y compris leur périmètre.</p>
     *
     * <ul>
     *   <li><strong>Ancienneté des files</strong> — ce qui dit s'il y a urgence n'est pas le volume mais
     *       la date de la plus vieille demande. Côté rattachements, c'est {@code DATE_DECLARATION}
     *       (une date : elle remonte donc à minuit). Côté inscriptions, {@code t_compte_auth} ne porte
     *       aucune date de dépôt — la migration étant exclue, l'ancienneté se lit sur la première pièce
     *       déposée, écrite dans la même transaction que l'inscription ; à défaut (variante JSON
     *       historique, sans pièce) sur la première déclaration d'entité.</li>
     *   <li><strong>Périmètre de l'ancienneté d'inscription</strong> : celui du compteur qu'elle
     *       accompagne — les inscriptions de type PRMP. Afficher « 3 en attente, la plus ancienne du
     *       12/08 » avec une date venue d'une file non comptée serait incohérent.</li>
     *   <li><strong>Actifs / suspendus</strong> : {@code ACTIF} fait foi, car c'est lui que le login
     *       consulte. {@link cnm.prs.enums.StatutCompte} n'a pas de valeur {@code DESACTIVE} — la
     *       désactivation ne touche que le booléen — d'où « non connectable hors attente » pour les
     *       suspendus (désactivés + inscriptions refusées). Même règle que le statut affiché par
     *       l'annuaire ({@code AnnuaireService}), pour que les deux écrans comptent pareil.</li>
     * </ul>
     */
    public CompteursAdminDto mesCompteursAdmin() {
        java.time.LocalDate aujourdhui = java.time.LocalDate.now();
        // Lue une seule fois : elle sert de doyenneté aux rattachements, et de repli aux inscriptions.
        java.time.LocalDate premiereDeclaration =
                prmpEntiteDemandeRepository.premiereDeclaration(StatutDemandeEntite.EN_ATTENTE.name());
        return new CompteursAdminDto(
                compteAuthRepository.countByStatutAndTypeActeur(StatutCompte.EN_ATTENTE.name(), TypeActeur.PRMP.name()),
                compteAuthRepository.count(),
                auditLogRepository.count(),
                prmpEntiteDemandeRepository.countByStatutDemande(StatutDemandeEntite.EN_ATTENTE.name()),
                inscriptionDoyenneLe(premiereDeclaration),
                premiereDeclaration == null ? null : premiereDeclaration.atStartOfDay(),
                compteAuthRepository.countByActifTrue(),
                compteAuthRepository.compterNonConnectablesHorsAttente(StatutCompte.EN_ATTENTE.name()),
                mandatRepository.compterExpirantEntre(aujourdhui, aujourdhui.plusDays(PREAVIS_MANDAT_JOURS)));
    }

    /**
     * Dépôt de la plus ancienne inscription PRMP encore en attente ({@code null} si la file est vide) :
     * la première pièce déposée. Voir {@link #mesCompteursAdmin()} pour le motif de cette dérivation.
     *
     * <p>Sans aucune pièce — l'inscription par la variante JSON historique n'en dépose pas —, le repli
     * est le jour de la première déclaration d'entité <strong>encore en attente</strong> : une telle
     * déclaration n'existe que portée par une inscription en attente, et elle naît avec elle. Les deux
     * doyennetés se confondent alors, ce qui est exact et non un recopiage.</p>
     */
    private java.time.LocalDateTime inscriptionDoyenneLe(java.time.LocalDate premiereDeclarationEnAttente) {
        java.time.LocalDateTime parPiece = pieceJointeRepository.premierDepotDesComptes(
                StatutCompte.EN_ATTENTE.name(), TypeActeur.PRMP.name());
        if (parPiece != null) {
            return parPiece;
        }
        return premiereDeclarationEnAttente == null ? null : premiereDeclarationEnAttente.atStartOfDay();
    }

    /**
     * Compteurs de contenu du menu Assistant contrôleur — filtrés sur sa localité : lettres de renvoi
     * signées et PV définitifs (signés) de sa localité, les documents qu'il distribue. Sans localité → zéros.
     */
    public CompteursAssistantDto mesCompteursAssistant() {
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        if (localite == null) {
            return new CompteursAssistantDto(0, 0);
        }
        return new CompteursAssistantDto(
                lettreRenvoiRepository.countByStatutEtLocalite(StatutLettreRenvoi.SIGNE.name(), localite),
                pvExamenRepository.countDefinitifsParLocalite(localite));
    }

    /**
     * Compteurs de contenu du menu Chargé de publication — comptes <strong>globaux</strong> du workflow
     * de publication (rôle transversal) : à publier ({@code EN_ATTENTE}), publiées ({@code PUBLIE}),
     * retirées ({@code RETIRE}).
     */
    public CompteursPublicationDto mesCompteursPublication() {
        return new CompteursPublicationDto(
                publicationRepository.countByStatutPubli(StatutPublication.EN_ATTENTE.name()),
                publicationRepository.countByStatutPubli(StatutPublication.PUBLIE.name()),
                publicationRepository.countByStatutPubli(StatutPublication.RETIRE.name()));
    }

    /** Calcule le tableau de bord, global si {@code localite == null}, sinon limité à cette localité. */
    private TableauBordDto calculer(String localite) {
        List<Object[]> statuts = localite == null
                ? dossierRepository.compterParStatut()
                : dossierRepository.compterParStatutParLocalite(localite);
        Map<String, Long> pipeline = new LinkedHashMap<>();
        for (Object[] ligne : statuts) {
            String statut = ligne[0] != null ? (String) ligne[0] : "(non défini)";
            pipeline.put(statut, ((Number) ligne[1]).longValue());
        }

        long nbSoumis = localite == null
                ? dossierRepository.compterSoumis()
                : dossierRepository.compterSoumisParLocalite(localite);
        long nbConformes = localite == null
                ? verificationRepository.compterDossiersConformes()
                : verificationRepository.compterDossiersConformesParLocalite(localite);
        double tauxConformite = nbSoumis == 0 ? 0.0 : arrondi(nbConformes * 100.0 / nbSoumis);

        List<Object[]> stats = localite == null
                ? examenDetailRepository.statsNonConformiteParPoint()
                : examenDetailRepository.statsNonConformiteParPointParLocalite(localite);
        List<PointNonConformiteDto> topNonConformite = stats.stream()
                .map(this::versPointNonConformite)
                .sorted(Comparator.comparingDouble(PointNonConformiteDto::tauxNonConformitePct).reversed())
                .limit(5)
                .toList();

        return new TableauBordDto(pipeline, nbSoumis, nbConformes, tauxConformite, topNonConformite,
                compteurs(localite));
    }

    /**
     * Compteurs de contenu par section du menu : <strong>globaux</strong> pour le Président/Administrateur
     * ({@code localite == null}), <strong>filtrés sur la localité</strong> pour le Chef de commission.
     * Sections : prêts à dispatcher, dispatchés, projets de PV, lettres de renvoi soumises, PV signés,
     * demandes de retrait en attente.
     */
    private CompteursDto compteurs(String localite) {
        String pretDispatch = StatutDossier.PRET_DISPATCH.name();
        String dispatche = StatutDossier.DISPATCHE.name();
        String signe = StatutPv.SIGNE.name();
        String lettreSoumise = StatutLettreRenvoi.SOUMIS.name();
        String retraitEnAttente = StatutRetrait.EN_ATTENTE.name();
        if (localite == null) {
            return new CompteursDto(
                    dossierRepository.countByStatut(pretDispatch),
                    dossierRepository.countByStatut(dispatche),
                    pvExamenRepository.countByStatutPvNot(signe),
                    lettreRenvoiRepository.countByStatut(lettreSoumise),
                    pvExamenRepository.countByStatutPv(signe),
                    demandeRetraitRepository.countByStatut(retraitEnAttente));
        }
        return new CompteursDto(
                dossierRepository.countByStatutAndIdLocalite(pretDispatch, localite),
                dossierRepository.countByStatutAndIdLocalite(dispatche, localite),
                pvExamenRepository.countProjetsParLocalite(localite),
                lettreRenvoiRepository.countByStatutEtLocalite(lettreSoumise, localite),
                pvExamenRepository.countDefinitifsParLocalite(localite),
                demandeRetraitRepository.countByStatutEtLocaliteDossier(retraitEnAttente, localite));
    }

    private PointNonConformiteDto versPointNonConformite(Object[] ligne) {
        long total = ((Number) ligne[2]).longValue();
        long nonConforme = ligne[3] != null ? ((Number) ligne[3]).longValue() : 0L;
        double taux = total == 0 ? 0.0 : arrondi(nonConforme * 100.0 / total);
        return new PointNonConformiteDto((Integer) ligne[0], (String) ligne[1], total, nonConforme, taux);
    }

    private double arrondi(double valeur) {
        return Math.round(valeur * 100.0) / 100.0;
    }
}
