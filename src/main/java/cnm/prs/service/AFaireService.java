package cnm.prs.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AFaireDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.DemandeRetrait;
import cnm.prs.entity.Profile;
import cnm.prs.entity.SuspensionDossier;
import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.ModeTache;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.SectionAFaire;
import cnm.prs.enums.StatutPv;
import cnm.prs.enums.StatutRetrait;
import cnm.prs.enums.UrgenceTache;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.LettreRenvoiRepository;
import cnm.prs.repository.ProfileRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.PvNavetteRepository;
import cnm.prs.repository.TransmissionSigmpRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.PermissionService;
import cnm.prs.service.ReglesAFaire.Acteur;
import cnm.prs.service.ReglesAFaire.EtatDossier;
import cnm.prs.service.ReglesAFaire.LettreEnCours;
import cnm.prs.service.ReglesAFaire.Ligne;
import cnm.prs.service.ReglesAFaire.PvEnCours;

/**
 * ⚠️ <strong>Accueil « À faire »</strong> (demande front du 2026-09-14, {@code demande-backend-2026-09-14-accueil-
 * a-faire.md} ; arbitrages du 2026-09-15) — les gestes attendus du connecté, calculés à la volée, en lecture
 * seule, sans table ni migration.
 *
 * <h2>Ce que fait ce service, et ce qu'il ne fait pas</h2>
 *
 * <p>Il <strong>charge en lot</strong> ce que les gardes du circuit lisent, puis pose pour chaque dossier la
 * question « qu'est-ce que ce connecté peut faire ici, et à quel titre ? » à {@link ReglesAFaire}, qui ne fait
 * qu'assembler les prédicats des gardes. Le délai vient de {@link ChronometrageService#delaiCourant}, la date
 * prévisionnelle de {@link ChronometrageService#datePrevisionnelleFin}, la frise de
 * {@link ChronometrageService#frise} : <strong>aucune règle n'est recopiée ici</strong>.</p>
 *
 * <h2>Nombre de requêtes constant</h2>
 *
 * <p>Au plus quatorze ordres SQL, quelle que soit la taille du périmètre (§6, cible ≤ 15, vérifiée au compteur
 * Hibernate) : profils et paires de délégation actives, annuaire des contrôleurs, dossiers avec libellés et
 * agrégats, réceptions et circuits, états des PV, passages, attentes, délais standards, lettres, retraits,
 * retours de navette ; examinateurs et transmissions pour le seul Vérificateur ou Assistant, dont le titre
 * dépend du rattachement. La PRMP et l'UGPM n'en déclenchent que sept : rien d'interne à la CNM n'est lu
 * pour elles.</p>
 *
 * <h2>Règle C2</h2>
 *
 * <p>Pour la PRMP et l'UGPM, les champs internes à la CNM sont servis à {@code null} ({@code acteursEtapes},
 * {@code niveauNavette}, {@code consigneDispatch}, {@code dernierRetourNavette}, {@code partsAttendues},
 * {@code idDispatch}) : même principe que {@code DossierService.masquerActeursInternesPourPrmp}. L'annuaire des
 * contrôleurs n'est même pas chargé.</p>
 */
@Service
@Transactional(readOnly = true)
public class AFaireService {

    /** Profils qui ont un accueil « À faire » (§1) ; l'Administrateur et le Chargé de publication gardent le leur. */
    public static final Set<ProfilUtilisateur> PROFILS_CONCERNES = EnumSet.of(ProfilUtilisateur.PRESIDENT,
            ProfilUtilisateur.CHEF_COMMISSION, ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.MEMBRE,
            ProfilUtilisateur.VERIFICATEUR, ProfilUtilisateur.ASSISTANT_CONTROLEUR, ProfilUtilisateur.PRMP,
            ProfilUtilisateur.UGPM);

    private final DossierRepository dossierRepository;
    private final DispatchRepository dispatchRepository;
    private final PvExamenRepository pvExamenRepository;
    private final LettreRenvoiRepository lettreRenvoiRepository;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final PvNavetteRepository pvNavetteRepository;
    private final ControleurRepository controleurRepository;
    private final ProfileRepository profileRepository;
    private final ExamenRepository examenRepository;
    private final TransmissionSigmpRepository transmissionSigmpRepository;
    private final ChronometrageService chronometrage;
    private final DelaiStandardService delaiStandardService;
    private final PermissionService permissionService;
    private final Clock clock;

    public AFaireService(DossierRepository dossierRepository, DispatchRepository dispatchRepository,
            PvExamenRepository pvExamenRepository, LettreRenvoiRepository lettreRenvoiRepository,
            DemandeRetraitRepository demandeRetraitRepository, PvNavetteRepository pvNavetteRepository,
            ControleurRepository controleurRepository, ProfileRepository profileRepository,
            ExamenRepository examenRepository, TransmissionSigmpRepository transmissionSigmpRepository,
            ChronometrageService chronometrage, DelaiStandardService delaiStandardService,
            PermissionService permissionService, Clock clock) {
        this.dossierRepository = dossierRepository;
        this.dispatchRepository = dispatchRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.lettreRenvoiRepository = lettreRenvoiRepository;
        this.demandeRetraitRepository = demandeRetraitRepository;
        this.pvNavetteRepository = pvNavetteRepository;
        this.controleurRepository = controleurRepository;
        this.profileRepository = profileRepository;
        this.examenRepository = examenRepository;
        this.transmissionSigmpRepository = transmissionSigmpRepository;
        this.chronometrage = chronometrage;
        this.delaiStandardService = delaiStandardService;
        this.permissionService = permissionService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ points d'entrée

    /**
     * L'accueil du connecté. Le profil est garanti par le {@code @PreAuthorize} du contrôleur ; un profil hors
     * {@link #PROFILS_CONCERNES} reçoit par prudence un accueil vide.
     *
     * @param delegations {@code true} pour servir les lignes du bloc délégation ; ses totaux sont toujours servis
     */
    public AFaireDto aFaire(boolean delegations) {
        return calculer(acteurCourant(), delegations);
    }

    /**
     * ⚠️ Badge de menu ({@code BadgesDto.aFaire}) — le même calcul, dont on ne garde que {@code compteurs.aFaire} :
     * le badge et l'écran ne peuvent pas diverger. {@code null} pour un profil sans accueil « À faire »
     * (Administrateur, Chargé de publication, profil non reconnu).
     */
    public Integer compterAFaire() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == null || !PROFILS_CONCERNES.contains(profil)) {
            return null;
        }
        return calculer(acteurCourant(), false).compteurs().aFaire();
    }

    private Acteur acteurCourant() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        String im = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        boolean partieControlee = profil == ProfilUtilisateur.PRMP || profil == ProfilUtilisateur.UGPM;
        Set<ProfilUtilisateur> exercables = profil == null ? Set.of()
                : partieControlee ? EnumSet.of(profil) : permissionService.profilsExercables(profil);
        return new Acteur(profil, im, localite, exercables);
    }

    // ------------------------------------------------------------------ calcul

    AFaireDto calculer(Acteur acteur, boolean servirDelegations) {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        String profil = acteur.profil() == null ? null : acteur.profil().name();
        List<Object[]> lignesDossiers = acteur.profil() == null || !PROFILS_CONCERNES.contains(acteur.profil())
                ? List.of() : perimetre(acteur);
        if (lignesDossiers.isEmpty()) {
            return new AFaireDto(profil, maintenant, new AFaireDto.Compteurs(0, 0, 0, 0, 0, 0, 0), List.of(), List.of(),
                    new AFaireDto.Delegations(0, List.of(), List.of()));
        }
        boolean cnm = !acteur.partieControlee();
        Lot lot = charger(acteur, cnm, lignesDossiers);

        List<Candidat> candidats = new ArrayList<>();
        for (Object[] d : lignesDossiers) {
            Integer idDossier = (Integer) d[0];
            EtatDossier etat = etat(lot, d);
            List<Ligne> lignes = ReglesAFaire.lignes(acteur, etat);
            if (lignes.isEmpty()) {
                continue;
            }
            candidats.addAll(taches(lot, cnm, d, etat, lignes, maintenant, idDossier));
        }

        Comparator<Candidat> ordre = Comparator.comparingInt((Candidat c) -> c.urgence().ordinal())
                .thenComparing(Candidat::cleIntraUrgence, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Candidat::idDossier)
                .thenComparingInt(c -> c.section().ordinal());
        candidats.sort(ordre);

        List<Candidat> titulaires = candidats.stream().filter(c -> c.mode() == ModeTache.TITULAIRE).toList();
        List<Candidat> delegues = candidats.stream().filter(c -> c.mode() != ModeTache.TITULAIRE).toList();

        return new AFaireDto(profil, maintenant, compteurs(titulaires), sections(titulaires, lot.delais()),
                classer(titulaires),
                new AFaireDto.Delegations(delegues.size(), totauxParSection(delegues),
                        servirDelegations ? classer(delegues) : List.of()));
    }

    /** Dossiers du périmètre aux statuts actifs, avec libellés et agrégats — une requête. */
    private List<Object[]> perimetre(Acteur acteur) {
        if (acteur.partieControlee()) {
            return acteur.im() == null ? List.of()
                    : dossierRepository.findAFairePourPrmp(acteur.im(), ReglesAFaire.STATUTS_ACTIFS_PARTIE_CONTROLEE);
        }
        if (acteur.profil() == ProfilUtilisateur.PRESIDENT) {
            return dossierRepository.findAFaireTous(ReglesAFaire.STATUTS_ACTIFS_CNM);
        }
        return acteur.localite() == null ? List.of()
                : dossierRepository.findAFaireParLocalite(acteur.localite(), ReglesAFaire.STATUTS_ACTIFS_CNM);
    }

    // ------------------------------------------------------------------ chargement en lot

    /** Tout ce que les gardes lisent, pour tous les dossiers du périmètre : un nombre de requêtes fixe. */
    private record Lot(
            Map<String, Controleur> annuaire,
            Map<Integer, ProfilUtilisateur> profilsParId,
            Map<Integer, Circuits> circuits,
            Map<Integer, Object[]> pvs,
            Map<Integer, List<TacheDossier>> taches,
            Map<Integer, List<SuspensionDossier>> suspensions,
            Map<EtapeCircuit, Integer> delais,
            Map<Integer, LettreEnCours> lettresASigner,
            Map<Integer, LettreEnCours> lettresAArchiver,
            Map<Integer, DemandeRetrait> retraits,
            Map<Integer, Map<Integer, String>> retoursParPv,
            Map<Integer, String> examinateurs,
            Map<Integer, String> valideurs) {
    }

    /** Réceptions et dispatchs d'un dossier. */
    private static final class Circuits {
        Integer idReception;
        String localiteReception;
        Integer idDispatch;
        CircuitDossierService.Circuit courant;
        String instructions;
        Integer idExamen;
        /** Attributaire courant non vide — la même lecture que {@code ChronometrageService.attributairesParDossier}. */
        String attributaireFrise;
        final Map<Integer, CircuitDossierService.Circuit> parDispatch = new HashMap<>();
    }

    private Lot charger(Acteur acteur, boolean cnm, List<Object[]> lignesDossiers) {
        List<Integer> ids = lignesDossiers.stream().map(d -> (Integer) d[0]).toList();

        Map<String, Controleur> annuaire = new HashMap<>();
        Map<Integer, ProfilUtilisateur> profilsParId = new HashMap<>();
        if (cnm) {
            for (Profile p : profileRepository.findAll()) {
                profilsParId.put(p.getIdProfile(), ProfilUtilisateur.resolve(p.getProfile()));
            }
            for (Controleur c : controleurRepository.findAll()) {
                annuaire.put(c.getImControleur(), c);
            }
        }

        Map<Integer, Circuits> circuits = new HashMap<>();
        for (Object[] r : dispatchRepository.findReceptionsEtCircuitsParDossiers(ids)) {
            Circuits c = circuits.computeIfAbsent((Integer) r[0], k -> new Circuits());
            c.idReception = (Integer) r[1];
            if (r[2] != null) {
                c.localiteReception = (String) r[2];
            }
            Integer idDispatch = (Integer) r[3];
            if (idDispatch == null) {
                continue;
            }
            CircuitDossierService.Circuit circuit =
                    new CircuitDossierService.Circuit((String) r[2], (String) r[4], (String) r[5]);
            c.parDispatch.put(idDispatch, circuit);
            if (c.idDispatch == null || idDispatch > c.idDispatch) {
                c.idDispatch = idDispatch;
                c.courant = circuit;
                c.instructions = (String) r[6];
                c.idExamen = (Integer) r[7];
            }
            if (r[5] != null && !((String) r[5]).isBlank()) {
                c.attributaireFrise = (String) r[5];
            }
        }

        // Le plus récent gagne : la requête est croissante par PV.
        Map<Integer, Object[]> pvs = new HashMap<>();
        for (Object[] l : pvExamenRepository.etatsPvParDossiers(ids)) {
            if (l.length >= 16 && l[0] != null && l[1] != null) {
                pvs.put((Integer) l[0], l);
            }
        }

        Map<Integer, List<TacheDossier>> taches = chronometrage.tachesParDossier(ids);
        Map<Integer, List<SuspensionDossier>> suspensions = chronometrage.suspensionsParDossier(ids);
        Map<EtapeCircuit, Integer> delais = delaiStandardService.delais();

        Map<Integer, LettreEnCours> lettresASigner = new HashMap<>();
        Map<Integer, LettreEnCours> lettresAArchiver = new HashMap<>();
        if (cnm) {
            for (Object[] l : lettreRenvoiRepository.findATraiterParDossiers(ids)) {
                LettreEnCours lettre = new LettreEnCours((Integer) l[1], (LocalDate) l[3], (String) l[4]);
                if ("SOUMIS".equals(l[2])) {
                    lettresASigner.put((Integer) l[0], lettre);            // la plus récente
                } else {
                    lettresAArchiver.putIfAbsent((Integer) l[0], lettre);  // la plus ancienne d'abord
                }
            }
        }

        Map<Integer, DemandeRetrait> retraits = new HashMap<>();
        for (DemandeRetrait dr : demandeRetraitRepository
                .findByIdDossierInAndStatutOrderByIdDemandeRetraitAsc(ids, StatutRetrait.EN_ATTENTE.name())) {
            retraits.put(dr.getIdDossier(), dr);
        }

        Map<Integer, Map<Integer, String>> retoursParPv = new HashMap<>();
        if (cnm) {
            for (Object[] n : pvNavetteRepository.findRetoursParDossiers(ids)) {
                retoursParPv.computeIfAbsent((Integer) n[0], k -> new HashMap<>()).put((Integer) n[1], (String) n[2]);
            }
        }

        // Le titre d'un Vérificateur ou d'un Assistant dépend du rattachement : cibles lues comme les listes de
        // dossiers les lisent (RattachementService.ciblesDans), et pour eux seuls.
        Map<Integer, String> examinateurs = Map.of();
        Map<Integer, String> valideurs = Map.of();
        if (acteur.profil() == ProfilUtilisateur.VERIFICATEUR
                || acteur.profil() == ProfilUtilisateur.ASSISTANT_CONTROLEUR) {
            examinateurs = RattachementService.examinateursParDossier(examenRepository.findMembresParDossiers(ids));
            if (acteur.profil() == ProfilUtilisateur.ASSISTANT_CONTROLEUR) {
                valideurs = RattachementService.valideursParDossier(transmissionSigmpRepository.findByIdDossierIn(ids));
            }
        }

        return new Lot(annuaire, profilsParId, circuits, pvs, taches, suspensions, delais, lettresASigner,
                lettresAArchiver, retraits, retoursParPv, examinateurs, valideurs);
    }

    /** Ce que les gardes lisent du dossier {@code d} (ligne de {@code SELECT_A_FAIRE}). */
    private EtatDossier etat(Lot lot, Object[] d) {
        Integer idDossier = (Integer) d[0];
        Circuits c = lot.circuits().get(idDossier);
        Object[] pvLigne = lot.pvs().get(idDossier);
        PvEnCours pv = pvLigne == null ? null : pvEnCours(pvLigne);
        CircuitDossierService.Circuit circuitPv = pvLigne == null || c == null ? null
                : c.parDispatch.get((Integer) pvLigne[15]);
        ProfilUtilisateur profilDispatcheurPv = circuitPv == null ? null
                : profilDe(lot, circuitPv.dispatcheur());
        RattachementService.Cibles cibles = RattachementService.ciblesDans(lot.annuaire(),
                lot.examinateurs().get(idDossier), lot.valideurs().get(idDossier));
        long verifications = d[15] == null ? 0L : ((Number) d[15]).longValue();
        return new EtatDossier(idDossier, (String) d[9], (String) d[7], c == null ? null : c.localiteReception,
                c != null && c.idReception != null, c == null ? null : c.courant, c != null && c.idExamen != null,
                pv, circuitPv, profilDispatcheurPv, cibles.verificateur(), cibles.assistant(), verifications > 0,
                lot.lettresASigner().get(idDossier), lot.lettresAArchiver().get(idDossier),
                lot.retraits().containsKey(idDossier));
    }

    /** Colonnes de {@code etatsPvParDossiers} : 0-8 historiques, 9-15 ajoutées le 2026-09-15. */
    private static PvEnCours pvEnCours(Object[] l) {
        return new PvEnCours((Integer) l[9], (String) l[1], (String) l[10], (String) l[2], (String) l[3],
                (LocalDate) l[4], (String) l[11], (LocalDate) l[6], (LocalDate) l[8], (String) l[12], (LocalDate) l[13]);
    }

    /** Profil d'un contrôleur par sa FK scalaire, comme {@code ControleurDirectory.profilDe}, dans l'annuaire chargé. */
    private static ProfilUtilisateur profilDe(Lot lot, String im) {
        Controleur c = im == null ? null : lot.annuaire().get(im);
        return c == null || c.getIdProfile() == null ? null : lot.profilsParId().get(c.getIdProfile());
    }

    // ------------------------------------------------------------------ assemblage d'une ligne

    /** Une ligne avant classement : la tâche (rang 0) et ses clés de tri. */
    private record Candidat(AFaireDto.Tache tache, UrgenceTache urgence, SectionAFaire section, ModeTache mode,
            Integer idDossier, Double cleIntraUrgence) {
    }

    private List<Candidat> taches(Lot lot, boolean cnm, Object[] d, EtatDossier etat,
            List<Ligne> lignes, LocalDateTime maintenant, Integer idDossier) {
        String statut = (String) d[9];
        LocalDateTime depot = (LocalDateTime) d[2];
        Object[] pvLigne = lot.pvs().get(idDossier);
        String statutPv = etat.pv() == null ? null : etat.pv().statut();
        List<TacheDossier> taches = lot.taches().getOrDefault(idDossier, List.of());
        List<SuspensionDossier> suspensions = lot.suspensions().getOrDefault(idDossier, List.of());

        // Le délai, la date annoncée et la frise : ceux du chronométrage, sur les données déjà chargées.
        ChronometrageService.DelaiCourant courant = chronometrage.delaiCourant(statut, statutPv, taches, suspensions,
                depot, maintenant, lot.delais());
        LocalDate finPrevue = chronometrage.datePrevisionnelleFin(statut, statutPv, taches, suspensions, depot,
                maintenant, lot.delais());
        Circuits c = lot.circuits().get(idDossier);
        ChronometrageService.Frise frise = chronometrage.frise(statut, statutPv, taches,
                c == null ? null : c.attributaireFrise, pvLigne == null ? null : ChronometrageService.etatPvDe(pvLigne),
                lot.annuaire());

        AFaireDto.Dossier dossier = new AFaireDto.Dossier(idDossier, (String) d[1], depot, (String) d[3],
                (String) d[4], (Integer) d[5], (String) d[6], (String) d[7], (String) d[8], statut, statutPv,
                cnm && etat.pv() != null ? etat.pv().niveauNavette() : null, frise.dates(),
                cnm ? frise.acteurs() : null);
        AFaireDto.Faits faits = faits(lot, cnm, d, etat, c);
        DemandeRetrait retrait = lot.retraits().get(idDossier);

        List<Candidat> candidats = new ArrayList<>();
        for (Ligne ligne : lignes) {
            SectionAFaire section = ligne.section();
            LettreEnCours lettre = section == SectionAFaire.LETTRES_A_SIGNER ? etat.lettreASigner()
                    : section == SectionAFaire.LETTRES_A_ARCHIVER ? etat.lettreAArchiver() : null;
            AFaireDto.Delai delai = delai(section, courant, finPrevue, lettre, retrait);
            UrgenceTache urgence = ReglesAFaire.urgence(section, courant);
            AFaireDto.Refs refs = new AFaireDto.Refs(c == null ? null : c.idReception,
                    cnm && c != null ? c.idDispatch : null,
                    etat.pv() != null && pvLigne[14] != null ? (Integer) pvLigne[14] : c == null ? null : c.idExamen,
                    etat.pv() == null ? null : etat.pv().idPv(), lettre == null ? null : lettre.idLettre(),
                    retrait == null ? null : retrait.getIdDemandeRetrait());
            AFaireDto.Tache tache = new AFaireDto.Tache(section.name(), ligne.geste().name(),
                    ligne.gestesSecondaires().stream().map(Enum::name).toList(), ligne.mode().name(), urgence.name(),
                    0, dossier, delai, faits, refs);
            candidats.add(new Candidat(tache, urgence, section, ligne.mode(), idDossier,
                    cleIntraUrgence(urgence, delai, d)));
        }
        return candidats;
    }

    /**
     * Délai d'une ligne : celui de {@code delaiCourant}. Lettres et retraits n'ont pas d'étape chronométrée : leur
     * entrée est la date de la lettre ou de la demande, et les champs du chronomètre restent nuls ; l'étape reste
     * l'étape courante du dossier.
     */
    private static AFaireDto.Delai delai(SectionAFaire section, ChronometrageService.DelaiCourant courant,
            LocalDate finPrevue, LettreEnCours lettre, DemandeRetrait retrait) {
        String etape = courant.etape() == null ? null : courant.etape().name();
        if (section.natureDelai() == SectionAFaire.NatureDelai.SANS_DELAI) {
            LocalDateTime entree = section == SectionAFaire.RETRAITS_A_DECIDER
                    ? (retrait == null ? null : retrait.getDateDemande())
                    : (lettre == null || lettre.dateLettre() == null ? null : lettre.dateLettre().atStartOfDay());
            return new AFaireDto.Delai(etape, entree, null, null, null, null, courant.pauseDepuis(),
                    courant.pauseHeures(), finPrevue);
        }
        return new AFaireDto.Delai(etape, courant.entree(), courant.standardHeures(), courant.ecouleHeures(),
                courant.restantHeures(), courant.echeance(), courant.pauseDepuis(), courant.pauseHeures(), finPrevue);
    }

    /**
     * Second critère de tri (§5) : reste croissant pour les urgences chronométrées ; date la plus ancienne pour
     * {@code SANS_DELAI}, {@code HORS_DELAI} et {@code EN_PAUSE} (début de pause d'abord pour une pause, sinon
     * entrée, puis dépôt, puis date de référence) ; date prévisionnelle croissante pour {@code SUIVI}.
     */
    private static Double cleIntraUrgence(UrgenceTache urgence, AFaireDto.Delai delai, Object[] d) {
        // Une seule urgence par comparaison (critère précédent) : la clé n'a à être homogène qu'au sein d'une urgence.
        switch (urgence) {
            case EN_RETARD, BIENTOT, DANS_LES_DELAIS:
                return delai.restantHeures() == null ? null : (double) delai.restantHeures();
            case SUIVI:
                return delai.datePrevisionnelleFin() == null ? null
                        : (double) delai.datePrevisionnelleFin().toEpochDay();
            default:
                LocalDateTime date = urgence == UrgenceTache.EN_PAUSE
                        ? premiere(delai.pauseDepuis(), delai.entree(), (LocalDateTime) d[2], (LocalDate) d[10])
                        : premiere(delai.entree(), delai.pauseDepuis(), (LocalDateTime) d[2], (LocalDate) d[10]);
                return date == null ? null : (double) date.toEpochSecond(java.time.ZoneOffset.UTC);
        }
    }

    private static LocalDateTime premiere(LocalDateTime a, LocalDateTime b, LocalDateTime c, LocalDate reference) {
        if (a != null) {
            return a;
        }
        if (b != null) {
            return b;
        }
        if (c != null) {
            return c;
        }
        return reference == null ? null : reference.atStartOfDay();
    }

    /** Faits de l'aperçu ; les faits internes à la CNM sont nuls pour la partie contrôlée (règle C2). */
    private static AFaireDto.Faits faits(Lot lot, boolean cnm, Object[] d, EtatDossier etat, Circuits c) {
        Integer idDossier = etat.idDossier();
        long nbLignes = d[11] == null ? 0L : ((Number) d[11]).longValue();
        long nbObservations = d[14] == null ? 0L : ((Number) d[14]).longValue();
        PvEnCours pv = etat.pv();
        DemandeRetrait retrait = lot.retraits().get(idDossier);

        String dernierRetour = null;
        List<String> partsAttendues = null;
        String consigne = null;
        if (cnm) {
            Map<Integer, String> retours = lot.retoursParPv().get(idDossier);
            dernierRetour = pv == null || retours == null ? null : retours.get(pv.idPv());
            consigne = c == null || c.instructions == null || c.instructions.isBlank() ? null : c.instructions;
            if (pv != null && StatutPv.PROJET_ACCEPTE.name().equals(pv.statut())) {
                partsAttendues = new ArrayList<>();
                if (PredicatsIdentite.designationFaite(pv.membreCoSignataire()) && pv.dateSignatureMembre() == null) {
                    partsAttendues.add("MEMBRE");
                }
                if (PredicatsIdentite.designationFaite(pv.ccCoSignataire()) && pv.dateSignatureCc() == null) {
                    partsAttendues.add("CC");
                }
            }
        }
        return new AFaireDto.Faits(
                nbLignes == 0 ? null : (int) nbLignes,
                nbLignes == 0 ? null : (BigDecimal) d[12],
                d[13] == null ? 0 : ((Number) d[13]).intValue(),
                pv == null ? null : pv.idAvis(),
                nbObservations == 0 ? null : (int) nbObservations,
                consigne,
                dernierRetour,
                retrait == null ? null : retrait.getMotifRetrait(),
                c == null || c.idDispatch == null ? null : c.idExamen != null,
                partsAttendues);
    }

    // ------------------------------------------------------------------ classement, sections, compteurs

    /** Rangs à partir de 1, dans l'ordre déjà trié. */
    private static List<AFaireDto.Tache> classer(List<Candidat> candidats) {
        List<AFaireDto.Tache> classees = new ArrayList<>(candidats.size());
        int rang = 1;
        for (Candidat c : candidats) {
            AFaireDto.Tache t = c.tache();
            classees.add(new AFaireDto.Tache(t.section(), t.geste(), t.gestesSecondaires(), t.mode(), t.urgence(),
                    rang++, t.dossier(), t.delai(), t.faits(), t.refs()));
        }
        return classees;
    }

    /** Compteurs des lignes titulaires (§5) : {@code aFaire} exclut les sections de suivi. */
    private static AFaireDto.Compteurs compteurs(List<Candidat> titulaires) {
        int aFaire = 0;
        Map<UrgenceTache, Integer> parUrgence = new EnumMap<>(UrgenceTache.class);
        for (Candidat c : titulaires) {
            parUrgence.merge(c.urgence(), 1, Integer::sum);
            if (!c.section().horsAFaire()) {
                aFaire++;
            }
        }
        return new AFaireDto.Compteurs(aFaire, parUrgence.getOrDefault(UrgenceTache.EN_RETARD, 0),
                parUrgence.getOrDefault(UrgenceTache.BIENTOT, 0),
                parUrgence.getOrDefault(UrgenceTache.DANS_LES_DELAIS, 0),
                parUrgence.getOrDefault(UrgenceTache.SANS_DELAI, 0) + parUrgence.getOrDefault(UrgenceTache.HORS_DELAI, 0),
                parUrgence.getOrDefault(UrgenceTache.EN_PAUSE, 0), parUrgence.getOrDefault(UrgenceTache.SUIVI, 0));
    }

    /** Sections non vides, dans l'ordre du circuit, avec le délai standard de leur étape. */
    private static List<AFaireDto.Section> sections(List<Candidat> titulaires, Map<EtapeCircuit, Integer> delais) {
        Map<SectionAFaire, Integer> totaux = totaux(titulaires);
        List<AFaireDto.Section> sections = new ArrayList<>();
        totaux.forEach((section, total) -> sections.add(new AFaireDto.Section(section.name(), total,
                section.etapeStandard() == null ? null
                        : delais.getOrDefault(section.etapeStandard(), HeuresOuvrees.HEURES_PAR_JOUR))));
        return sections;
    }

    private static List<AFaireDto.SectionTotal> totauxParSection(List<Candidat> candidats) {
        List<AFaireDto.SectionTotal> parSection = new ArrayList<>();
        totaux(candidats).forEach((section, total) -> parSection.add(new AFaireDto.SectionTotal(section.name(), total)));
        return parSection;
    }

    /** Totaux par section, dans l'ordre de déclaration de {@link SectionAFaire}. */
    private static Map<SectionAFaire, Integer> totaux(Collection<Candidat> candidats) {
        Map<SectionAFaire, Integer> totaux = new EnumMap<>(SectionAFaire.class);
        for (Candidat c : candidats) {
            totaux.merge(c.section(), 1, Integer::sum);
        }
        return new LinkedHashMap<>(totaux);
    }
}
