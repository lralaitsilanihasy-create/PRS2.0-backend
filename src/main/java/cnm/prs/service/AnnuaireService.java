package cnm.prs.service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AnnuaireFicheDto;
import cnm.prs.dto.AnnuairePersonneDto;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.PrmpEntite;
import cnm.prs.entity.Profile;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutCompte;
import cnm.prs.enums.StatutCompteAnnuaire;
import cnm.prs.enums.TypeActeur;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AuditLogRepository;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DelegationProfilRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.PrmpEntiteRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.ProfileRepository;
import cnm.prs.repository.SessionUtilisateurRepository;
import cnm.prs.repository.UgpmRepository;

/**
 * Lot 6 (2026-09-17, demande front « espace d'administration » §B2) — <strong>annuaire unifié des
 * personnes</strong> : contrôleurs, PRMP et UGPM dans une seule liste cherchable et paginée.
 *
 * <p><strong>Pourquoi côté serveur.</strong> Les trois populations vivent dans trois tables, exposées
 * par trois endpoints ({@code /api/controleurs}, {@code /api/prmps}, {@code /api/ugpms}), avec des
 * identifiants de longueurs différentes et des champs qui ne se recouvrent pas. Recoller trois appels
 * à l'affichage rendrait la pagination et le tri faux dès que les populations se mélangent, et le
 * filtre par statut de compte imposerait un quatrième appel : le statut ne vit pas dans les tables de
 * personnes mais dans {@code t_compte_auth}.</p>
 *
 * <p><strong>Pourquoi en mémoire, et pas en SQL.</strong> Les trois tables sont de petites tables de
 * référentiel qui bougent peu — quelques dizaines de contrôleurs, quelques centaines de PRMP et
 * d'UGPM — et l'écran les balaie toutes : une union paginée en SQL exigerait soit une vue (un objet
 * de plus à maintenir, et une migration, toutes deux écartées par la demande), soit une requête
 * native à trois branches avec un {@code unaccent} qui n'est pas installé sur la base. Le tri et la
 * recherche sans accents se font donc ici, sur des listes bornées, comme {@code Pagination.depuisListe}
 * le prévoit pour les listes « intrinsèquement petites et bornées ». La borne est celle du
 * référentiel des personnes : si elle venait à sauter, c'est la stratégie entière qu'il faudrait
 * revoir, pas un paramètre à ajuster.</p>
 */
@Service
@Transactional(readOnly = true)
public class AnnuaireService {

    /** Séparateur des entités multiples d'une même PRMP (une PRMP peut en chapeauter plusieurs). */
    private static final String SEPARATEUR_ENTITES = " · ";

    /** Fenêtre d'activité de la fiche (§B3) : « actions au journal sur 30 jours ». */
    private static final int JOURS_ACTIVITE = 30;

    /** Délégation reçue : la personne exerce les tâches du profil cité (elle est <em>délégante</em>). */
    private static final String SENS_EXERCE = "EXERCE";

    /** Délégation consentie : les tâches de son profil sont exerçables par le profil cité (elle est <em>déléguée</em>). */
    private static final String SENS_EXERCEE_PAR = "EXERCEE_PAR";

    /**
     * Ordre servi : nom, puis prénoms, puis référence — l'ordre d'un annuaire, insensible aux accents
     * comme la recherche. La référence tranche les homonymes et rend l'ordre
     * <strong>total</strong> : sans elle, deux pages successives pourraient se recouvrir.
     */
    private static final Comparator<AnnuairePersonneDto> ORDRE_ANNUAIRE =
            Comparator.comparing((AnnuairePersonneDto p) -> normaliser(p.nom()))
                    .thenComparing(p -> normaliser(p.prenoms()))
                    .thenComparing(p -> normaliser(p.ref()));

    private final ControleurRepository controleurRepository;
    private final ProfileRepository profileRepository;
    private final PrmpRepository prmpRepository;
    private final UgpmRepository ugpmRepository;
    private final CompteAuthRepository compteAuthRepository;
    private final PrmpEntiteRepository prmpEntiteRepository;
    private final EntiteContractRepository entiteContractRepository;
    private final DelegationProfilRepository delegationProfilRepository;
    private final AuditLogRepository auditLogRepository;
    /** ⚠️ Lot 6 (2026-09-17, §B4) — bloc « Accès » de la fiche : dernière connexion et échecs sur 30 jours. */
    private final SessionUtilisateurRepository sessionRepository;
    private final MandatService mandatService;

    public AnnuaireService(ControleurRepository controleurRepository, ProfileRepository profileRepository,
            PrmpRepository prmpRepository, UgpmRepository ugpmRepository,
            CompteAuthRepository compteAuthRepository, PrmpEntiteRepository prmpEntiteRepository,
            EntiteContractRepository entiteContractRepository,
            DelegationProfilRepository delegationProfilRepository, AuditLogRepository auditLogRepository,
            SessionUtilisateurRepository sessionRepository, MandatService mandatService) {
        this.controleurRepository = controleurRepository;
        this.profileRepository = profileRepository;
        this.prmpRepository = prmpRepository;
        this.ugpmRepository = ugpmRepository;
        this.compteAuthRepository = compteAuthRepository;
        this.prmpEntiteRepository = prmpEntiteRepository;
        this.entiteContractRepository = entiteContractRepository;
        this.delegationProfilRepository = delegationProfilRepository;
        this.auditLogRepository = auditLogRepository;
        this.sessionRepository = sessionRepository;
        this.mandatService = mandatService;
    }

    /**
     * Recherche unifiée. Tous les critères sont facultatifs et se cumulent ; aucun ne restreint la
     * <em>visibilité</em> (l'annuaire est réservé à l'Administrateur, qui voit tout le référentiel).
     *
     * @param q        texte libre, <strong>sans tenir compte de la casse ni des accents</strong>, cherché
     *                 dans le nom, les prénoms, la référence, le login et l'entité de rattachement
     * @param type     population ({@code CONTROLEUR}, {@code PRMP}, {@code UGPM}) ; {@code null} = les trois
     * @param profil   profil du contrôleur ; {@code null} = tous. Une PRMP et une UGPM n'en portant pas,
     *                 ce filtre les exclut de fait — c'est {@code type} qui sert à les isoler
     * @param localite code de localité ({@code ID_LOCALITE}), comparé sans la casse ; ne retient que des
     *                 contrôleurs, seuls porteurs d'une localité
     * @param statut   état d'accès ({@link StatutCompteAnnuaire}), {@code SANS_COMPTE} compris
     */
    public Page<AnnuairePersonneDto> rechercher(String q, TypeActeur type, ProfilUtilisateur profil,
            String localite, StatutCompteAnnuaire statut, Pageable pageable) {
        Map<CleActeur, CompteAuth> comptes = comptesParActeur();
        List<AnnuairePersonneDto> personnes = new ArrayList<>();
        if (type == null || type == TypeActeur.CONTROLEUR) {
            personnes.addAll(controleurs(comptes));
        }
        if (type == null || type == TypeActeur.PRMP || type == TypeActeur.UGPM) {
            Map<String, String> entites = entitesParPrmp();
            if (type == null || type == TypeActeur.PRMP) {
                personnes.addAll(prmps(comptes, entites));
            }
            if (type == null || type == TypeActeur.UGPM) {
                personnes.addAll(ugpms(comptes, entites));
            }
        }

        String recherche = normaliser(q);
        String codeLocalite = localite == null || localite.isBlank() ? null : localite.trim();
        List<AnnuairePersonneDto> retenues = personnes.stream()
                .filter(p -> profil == null || profil.name().equals(p.profil()))
                .filter(p -> codeLocalite == null || codeLocalite.equalsIgnoreCase(p.localite()))
                .filter(p -> statut == null || statut.name().equals(p.statutCompte()))
                .filter(p -> recherche.isEmpty() || correspond(p, recherche))
                .sorted(ORDRE_ANNUAIRE)
                .toList();
        return Pagination.depuisListe(retenues, pageable);
    }

    // ------------------------------------------------------------------
    // Les trois populations
    // ------------------------------------------------------------------

    private List<AnnuairePersonneDto> controleurs(Map<CleActeur, CompteAuth> comptes) {
        Map<Integer, ProfilUtilisateur> profils = profilsParId();
        List<AnnuairePersonneDto> lignes = new ArrayList<>();
        for (Controleur c : controleurRepository.findAll()) {
            CompteAuth compte = comptes.get(new CleActeur(TypeActeur.CONTROLEUR.name(), c.getImControleur()));
            lignes.add(new AnnuairePersonneDto(c.getImControleur(), TypeActeur.CONTROLEUR.name(),
                    c.getNomCont(), c.getPrenomsCont(), nom(profilDe(c, profils)),
                    c.getIdLocalite(), null, login(compte), statutDe(compte).name()));
        }
        return lignes;
    }

    private List<AnnuairePersonneDto> prmps(Map<CleActeur, CompteAuth> comptes, Map<String, String> entites) {
        List<AnnuairePersonneDto> lignes = new ArrayList<>();
        for (Prmp p : prmpRepository.findAll()) {
            CompteAuth compte = comptes.get(new CleActeur(TypeActeur.PRMP.name(), p.getIdPrmp()));
            lignes.add(new AnnuairePersonneDto(p.getIdPrmp(), TypeActeur.PRMP.name(),
                    p.getNomPrmp(), p.getPrenomsPrmp(), null, null, entites.get(p.getIdPrmp()),
                    login(compte), statutDe(compte).name()));
        }
        return lignes;
    }

    /**
     * Une UGPM n'a pas d'entité à elle : elle travaille sous le périmètre de sa PRMP de tutelle. Ce
     * sont donc les entités de la tutelle qui la situent — c'est ce qui fait qu'une recherche sur
     * « DGCF » rend la PRMP de la DGCF <em>et</em> ses UGPM, la question même que l'écran doit savoir
     * poser.
     */
    private List<AnnuairePersonneDto> ugpms(Map<CleActeur, CompteAuth> comptes, Map<String, String> entites) {
        List<AnnuairePersonneDto> lignes = new ArrayList<>();
        for (Ugpm u : ugpmRepository.findAll()) {
            CompteAuth compte = comptes.get(new CleActeur(TypeActeur.UGPM.name(), u.getIdUgpm()));
            lignes.add(new AnnuairePersonneDto(u.getIdUgpm(), TypeActeur.UGPM.name(),
                    u.getNomUgpm(), u.getPrenomsUgpm(), null, null, entites.get(u.getIdPrmpTutelle()),
                    login(compte), statutDe(compte).name()));
        }
        return lignes;
    }

    // ------------------------------------------------------------------
    // Fiche d'une personne (§B3)
    // ------------------------------------------------------------------

    /**
     * ⚠️ Lot 6 (2026-09-17, demande front §B3) — fiche d'une personne : ce que la maquette C affiche
     * quand on la sélectionne dans la liste, et rien de plus.
     *
     * <p><strong>La même personne, dite pareil.</strong> L'identité, le login et l'état du compte
     * sortent des mêmes fonctions que {@link #rechercher} — {@link #statutDe}, {@link #leMoinsFerme},
     * {@link #entitesParPrmp}, le rapprochement du compte par le couple (type d'acteur, référence) —
     * de sorte que la ligne de liste et la fiche ne peuvent pas se contredire d'un clic à l'autre.</p>
     *
     * <p>⚠️ <strong>2026-09-17, §B4</strong> — {@code derniereConnexion} et {@code echecs30j} sont
     * désormais <strong>servis</strong> : ils étaient nuls faute de journal des connexions, que la
     * migration {@code V31} et {@link JournalConnexionService} viennent de livrer. Le bloc « Accès » de
     * la maquette C peut s'afficher. {@code derniereConnexion} reste nulle pour qui ne s'est jamais
     * connecté <em>depuis que le journal existe</em> — c'est-à-dire, au début, pour tout le monde.</p>
     *
     * @param type population de la personne ; le contrôleur lie l'énuméré, une valeur inconnue part en 400
     * @param ref  identifiant dans cette population
     * @throws ResourceNotFoundException référence inconnue dans cette population → 404
     */
    public AnnuaireFicheDto fiche(TypeActeur type, String ref) {
        String cle = ref == null ? "" : ref.trim();
        return switch (type) {
            case CONTROLEUR -> ficheControleur(cle);
            case PRMP -> fichePrmp(cle);
            case UGPM -> ficheUgpm(cle);
        };
    }

    private AnnuaireFicheDto ficheControleur(String im) {
        Controleur c = controleurRepository.findById(im)
                .orElseThrow(() -> introuvable(TypeActeur.CONTROLEUR, im));
        Map<Integer, ProfilUtilisateur> profils = profilsParId();
        ProfilUtilisateur profil = profilDe(c, profils);
        CompteAuth compte = compteDe(TypeActeur.CONTROLEUR, im);
        return new AnnuaireFicheDto(c.getImControleur(), TypeActeur.CONTROLEUR.name(),
                c.getNomCont(), c.getPrenomsCont(), nom(profil), c.getIdLocalite(), null,
                login(compte), statutDe(compte).name(), dateActivation(compte),
                derniereConnexion(im), echecs30j(im),
                superieur(c, profils), c.getTransversal(), chaineControle(c, profils),
                delegations(profil), null, actionsJournal30j(im));
    }

    private AnnuaireFicheDto fichePrmp(String id) {
        Prmp p = prmpRepository.findById(id).orElseThrow(() -> introuvable(TypeActeur.PRMP, id));
        CompteAuth compte = compteDe(TypeActeur.PRMP, id);
        return new AnnuaireFicheDto(p.getIdPrmp(), TypeActeur.PRMP.name(), p.getNomPrmp(), p.getPrenomsPrmp(),
                null, null, entitesParPrmp().get(p.getIdPrmp()),
                login(compte), statutDe(compte).name(), dateActivation(compte),
                derniereConnexion(id), echecs30j(id),
                null, null, List.of(), List.of(),
                // Le mandat en fonction ce jour, servi par le service qui fait déjà foi partout ailleurs
                // (mandat déclaré, ou reconstitué depuis t_prmp quand aucun ne l'a encore été).
                mandatService.mandatActif(null, p.getIdPrmp()).orElse(null), actionsJournal30j(id));
    }

    /**
     * Une UGPM n'a ni profil de contrôle, ni entité en propre, ni mandat : elle travaille sous celui
     * de sa PRMP de tutelle. La fiche ne le lui attribue pas — ce serait lui prêter une habilitation
     * qui n'est pas la sienne — mais la situe par les entités de sa tutelle, comme la liste.
     */
    private AnnuaireFicheDto ficheUgpm(String id) {
        Ugpm u = ugpmRepository.findById(id).orElseThrow(() -> introuvable(TypeActeur.UGPM, id));
        CompteAuth compte = compteDe(TypeActeur.UGPM, id);
        return new AnnuaireFicheDto(u.getIdUgpm(), TypeActeur.UGPM.name(), u.getNomUgpm(), u.getPrenomsUgpm(),
                null, null, entitesParPrmp().get(u.getIdPrmpTutelle()),
                login(compte), statutDe(compte).name(), dateActivation(compte),
                derniereConnexion(id), echecs30j(id),
                null, null, List.of(), List.of(), null, actionsJournal30j(id));
    }

    private static ResourceNotFoundException introuvable(TypeActeur type, String ref) {
        return new ResourceNotFoundException("Personne introuvable à l'annuaire : " + type.name() + " " + ref);
    }

    /** Compte de la personne, le <strong>moins fermé</strong> si elle en a plusieurs — règle de la liste. */
    private CompteAuth compteDe(TypeActeur type, String ref) {
        return compteAuthRepository.findByRefActeurAndTypeActeur(ref, type.name()).stream()
                .reduce(AnnuaireService::leMoinsFerme).orElse(null);
    }

    /**
     * « Actif depuis le … » de la maquette : la date de la décision d'<strong>ouverture</strong> du
     * compte. {@code t_compte_auth} ne porte qu'une date, {@code DATE_DECISION}, écrite quand
     * l'Administrateur tranche — validation comme refus. Elle n'est donc servie que pour un compte
     * effectivement ouvert (actif ou suspendu depuis) : sur un refus, elle date le refus, et la
     * publier sous le nom « date d'activation » dirait le contraire de ce qui s'est passé. Une
     * inscription en attente n'en a aucune (défaut relevé à la livraison de B1, à corriger avec B4).
     */
    private static LocalDateTime dateActivation(CompteAuth compte) {
        if (compte == null) {
            return null;
        }
        StatutCompteAnnuaire statut = statutDe(compte);
        return statut == StatutCompteAnnuaire.ACTIF || statut == StatutCompteAnnuaire.SUSPENDU
                ? compte.getDateDecision()
                : null;
    }

    /** Supérieur hiérarchique résolu ; un matricule qui ne désigne plus personne ne cite personne. */
    private AnnuaireFicheDto.Personne superieur(Controleur c, Map<Integer, ProfilUtilisateur> profils) {
        String im = c.getIdSuperieur();
        if (im == null || im.isBlank()) {
            return null;
        }
        return controleurRepository.findById(im.trim())
                .map(s -> new AnnuaireFicheDto.Personne(s.getImControleur(), s.getNomCont(), s.getPrenomsCont(),
                        nom(profilDe(s, profils)), s.getIdLocalite()))
                .orElse(null);
    }

    /**
     * Chaîne de rattachement Membre → Vérificateur → Assistant ({@code IM_RATTACHE}), la personne en
     * tête. Un seul maillon = <strong>chaîne incomplète</strong> : état normal, pas une erreur, le
     * repli localité s'applique (arbitrage du pilote du 2026-09-01).
     *
     * <p>Le parcours s'arrête sur un matricule déjà vu : rien n'interdit en base qu'un rattachement
     * boucle (A rattaché à B, B rattaché à A), et une fiche ne doit pas tourner indéfiniment pour
     * autant.</p>
     */
    private List<AnnuaireFicheDto.Maillon> chaineControle(Controleur depart, Map<Integer, ProfilUtilisateur> profils) {
        List<AnnuaireFicheDto.Maillon> chaine = new ArrayList<>();
        Set<String> vus = new HashSet<>();
        Controleur courant = depart;
        while (courant != null && vus.add(courant.getImControleur())) {
            chaine.add(new AnnuaireFicheDto.Maillon(courant.getImControleur(), courant.getNomCont(),
                    courant.getPrenomsCont(), nom(profilDe(courant, profils)), chaine.isEmpty()));
            String suivant = courant.getImRattache();
            courant = suivant == null || suivant.isBlank() ? null
                    : controleurRepository.findById(suivant.trim()).orElse(null);
        }
        return chaine;
    }

    /**
     * Délégations de profil actives qui concernent ce profil, dans les deux sens (cf.
     * {@link cnm.prs.dto.AnnuaireFicheDto.Delegation}). La convention de {@code t_delegation_profil}
     * est celle de {@code PermissionService} : le <em>délégant</em> exerce, le <em>délégué</em> est
     * celui dont la tâche est exercée.
     *
     * <p>Les paires sont lues par <strong>libellé</strong> de profil, comme l'habilitation elle-même ;
     * plusieurs lignes de {@code tr_profile} pouvant porter le même libellé, deux paires peuvent se
     * ramener à la même délégation — elles ne sont alors citées qu'une fois.</p>
     */
    private List<AnnuaireFicheDto.Delegation> delegations(ProfilUtilisateur profil) {
        if (profil == null) {
            return List.of();
        }
        List<AnnuaireFicheDto.Delegation> lignes = new ArrayList<>();
        for (Object[] paire : delegationProfilRepository.findPairesActivesParLibelle()) {
            ProfilUtilisateur delegant = ProfilUtilisateur.resolve((String) paire[0]);
            ProfilUtilisateur delegue = ProfilUtilisateur.resolve((String) paire[1]);
            if (delegant == null || delegue == null || delegant == delegue) {
                continue;
            }
            if (delegant == profil) {
                lignes.add(new AnnuaireFicheDto.Delegation(SENS_EXERCE, delegue.name()));
            } else if (delegue == profil) {
                lignes.add(new AnnuaireFicheDto.Delegation(SENS_EXERCEE_PAR, delegant.name()));
            }
        }
        return lignes.stream()
                .distinct()
                .sorted(Comparator.comparing(AnnuaireFicheDto.Delegation::sens)
                        .thenComparing(AnnuaireFicheDto.Delegation::profil))
                .toList();
    }

    /** Écritures portées au nom de la personne sur 30 jours glissants ({@code t_audit_log.IM_ACTEUR}). */
    private long actionsJournal30j(String ref) {
        return auditLogRepository.compterActionsDepuis(ref, LocalDateTime.now().minusDays(JOURS_ACTIVITE));
    }

    /**
     * ⚠️ Lot 6 (2026-09-17, §B4) — « dernière connexion » du bloc « Accès ». Nulle pour qui ne s'est
     * jamais connecté <strong>depuis que le journal existe</strong> (V31) : au début, c'est tout le
     * monde, et c'est exact — le journal ne remonte pas le temps.
     */
    private LocalDateTime derniereConnexion(String ref) {
        return sessionRepository.derniereConnexionDe(ref);
    }

    /**
     * ⚠️ Lot 6 (2026-09-17, §B4) — tentatives refusées attribuées à cette personne sur la même fenêtre
     * de {@value #JOURS_ACTIVITE} jours que les actions au journal, pour que les deux chiffres du bloc
     * se lisent sur la même période.
     */
    private long echecs30j(String ref) {
        return sessionRepository.compterEchecsDe(ref, LocalDateTime.now().minusDays(JOURS_ACTIVITE));
    }

    // ------------------------------------------------------------------
    // Jointures en lot
    // ------------------------------------------------------------------

    /** Un compte appartient à un couple (type d'acteur, référence) : {@code REF_ACTEUR} seul ne suffit pas. */
    private record CleActeur(String type, String ref) {
    }

    /**
     * Comptes indexés par (type d'acteur, référence) : la jointure que le front ne peut pas faire, le
     * login n'étant pas l'identifiant de la personne.
     *
     * <p>Rien n'interdit en base plusieurs comptes pour une même personne (la PK est le login) ; on
     * garde alors le <strong>moins fermé</strong>, car c'est l'accès dont elle dispose réellement.</p>
     */
    private Map<CleActeur, CompteAuth> comptesParActeur() {
        Map<CleActeur, CompteAuth> parActeur = new HashMap<>();
        for (CompteAuth compte : compteAuthRepository.findAll()) {
            if (compte.getTypeActeur() == null || compte.getRefActeur() == null) {
                continue;
            }
            parActeur.merge(new CleActeur(compte.getTypeActeur(), compte.getRefActeur()), compte,
                    AnnuaireService::leMoinsFerme);
        }
        return parActeur;
    }

    /**
     * Profils du référentiel, par {@code ID_PROFILE}, résolus par le <strong>LIBELLÉ</strong> comme
     * l'authentification : {@code ID_PROFILE} n'a pas de sémantique fixée. Partagé par la liste et la
     * fiche — les deux nomment le profil d'une personne de la même façon.
     */
    private Map<Integer, ProfilUtilisateur> profilsParId() {
        Map<Integer, ProfilUtilisateur> profils = new HashMap<>();
        for (Profile p : profileRepository.findAll()) {
            profils.put(p.getIdProfile(), ProfilUtilisateur.resolve(p.getProfile()));
        }
        return profils;
    }

    /** Entités contractantes <strong>actives</strong> de chaque PRMP, assemblées en un libellé lisible. */
    private Map<String, String> entitesParPrmp() {
        Map<Integer, String> libelles = new HashMap<>();
        for (EntiteContract e : entiteContractRepository.findAll()) {
            libelles.put(e.getIdEntiteContract(), e.getLibelleEntite());
        }
        Map<String, List<String>> parPrmp = new HashMap<>();
        for (PrmpEntite affectation : prmpEntiteRepository.findAll()) {
            if (!Boolean.TRUE.equals(affectation.getActif())) {
                continue;   // une affectation retirée ne situe plus personne
            }
            String libelle = libelles.get(affectation.getIdEntiteContract());
            if (libelle == null || libelle.isBlank()) {
                continue;
            }
            parPrmp.computeIfAbsent(affectation.getIdPrmp(), id -> new ArrayList<>()).add(libelle);
        }
        Map<String, String> resultat = new HashMap<>();
        parPrmp.forEach((idPrmp, noms) -> {
            noms.sort(Comparator.comparing(AnnuaireService::normaliser));
            resultat.put(idPrmp, String.join(SEPARATEUR_ENTITES, noms));
        });
        return resultat;
    }

    // ------------------------------------------------------------------
    // Règles pures
    // ------------------------------------------------------------------

    /**
     * État d'accès d'une personne, dans l'ordre où les cas se tranchent.
     *
     * <p><strong>{@code ACTIF} prime sur {@code STATUT}</strong> : le login ne consulte que ce booléen,
     * donc quelqu'un qui peut se connecter est actif, quoi que dise le statut. Ensuite seulement
     * viennent l'inscription en attente, l'inscription refusée, et enfin la suspension.</p>
     *
     * <p><strong>⚠️ Suspendu et refusé ne sont pas le même état</strong> (correction du 2026-09-17) :
     * un compte fermé après coup, et une inscription qui n'a jamais été ouverte. Les confondre
     * gonflerait la tuile « comptes suspendus » de l'accueil, qui est une mesure de sécurité. Les deux
     * restent distinguables bien que {@link StatutCompte} n'ait pas de valeur {@code DESACTIVE} :
     * {@code CompteAuthService.desactiver} pose {@code ACTIF = false} en laissant {@code STATUT} à
     * {@code ACTIF}, là où un refus écrit {@code STATUT = REFUSE}.</p>
     *
     * <p><strong>⚠️ Invariant rompu, documenté ici faute de l'être ailleurs.</strong> Le javadoc de
     * {@link StatutCompte} annonce {@code ACTIF=true ⟺ STATUT=ACTIF} ; ni
     * {@code CompteAuthService.desactiver} ni {@code CompteAuthService.activer} ne tiennent cette
     * promesse — tous deux n'écrivent que le booléen. C'est précisément cet écart qui rend la
     * distinction possible ici. Il n'est <strong>pas</strong> corrigé : le login s'appuie sur
     * {@code ACTIF}, et y toucher dépasse le périmètre de ce lot.</p>
     */
    static StatutCompteAnnuaire statutDe(CompteAuth compte) {
        if (compte == null) {
            return StatutCompteAnnuaire.SANS_COMPTE;
        }
        if (Boolean.TRUE.equals(compte.getActif())) {
            return StatutCompteAnnuaire.ACTIF;
        }
        if (StatutCompte.EN_ATTENTE.name().equals(compte.getStatut())) {
            return StatutCompteAnnuaire.EN_ATTENTE;
        }
        if (StatutCompte.REFUSE.name().equals(compte.getStatut())) {
            return StatutCompteAnnuaire.REFUSE;
        }
        // STATUT = ACTIF (ou nul sur une ligne héritée) avec ACTIF = false : fermé par l'Administrateur.
        return StatutCompteAnnuaire.SUSPENDU;
    }

    /** Vrai si l'un des champs cherchables contient le texte demandé (tout est déjà normalisé). */
    private static boolean correspond(AnnuairePersonneDto p, String recherche) {
        return normaliser(p.nom()).contains(recherche)
                || normaliser(p.prenoms()).contains(recherche)
                || normaliser(p.ref()).contains(recherche)
                || normaliser(p.login()).contains(recherche)
                || normaliser(p.entite()).contains(recherche);
    }

    /**
     * Forme de comparaison : casse et accents neutralisés, rien de plus. Volontairement plus simple
     * que {@link LibelleNormalisation}, qui sert à <em>identifier</em> un libellé de référentiel (elle
     * supprime les séparateurs et les pluriels) : ici on cherche une sous-chaîne dans un nom propre,
     * où « RAKOTO » doit trouver « Rakotomalala ».
     */
    private static String normaliser(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.FRENCH);
    }

    private static String login(CompteAuth compte) {
        return compte == null ? null : compte.getLogin();
    }

    /** Profil d'un contrôleur dans un référentiel déjà chargé ; nul s'il n'en porte pas. */
    private static ProfilUtilisateur profilDe(Controleur c, Map<Integer, ProfilUtilisateur> profils) {
        return c.getIdProfile() == null ? null : profils.get(c.getIdProfile());
    }

    /** Nom d'un profil pour l'API, ou {@code null} : l'API expose des codes, jamais des libellés. */
    private static String nom(ProfilUtilisateur profil) {
        return profil == null ? null : profil.name();
    }

    private static CompteAuth leMoinsFerme(CompteAuth existant, CompteAuth candidat) {
        return rang(statutDe(candidat)) < rang(statutDe(existant)) ? candidat : existant;
    }

    /** Du plus ouvert au plus fermé — sert à départager plusieurs comptes d'une même personne. */
    private static int rang(StatutCompteAnnuaire statut) {
        return switch (statut) {
            case ACTIF -> 0;
            case EN_ATTENTE -> 1;
            case SUSPENDU -> 2;
            case REFUSE -> 3;
            case SANS_COMPTE -> 4;
        };
    }
}
