package cnm.prs.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.PrmpEntiteRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.ProfileRepository;
import cnm.prs.repository.UgpmRepository;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B2) — <strong>annuaire unifié des
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

    public AnnuaireService(ControleurRepository controleurRepository, ProfileRepository profileRepository,
            PrmpRepository prmpRepository, UgpmRepository ugpmRepository,
            CompteAuthRepository compteAuthRepository, PrmpEntiteRepository prmpEntiteRepository,
            EntiteContractRepository entiteContractRepository) {
        this.controleurRepository = controleurRepository;
        this.profileRepository = profileRepository;
        this.prmpRepository = prmpRepository;
        this.ugpmRepository = ugpmRepository;
        this.compteAuthRepository = compteAuthRepository;
        this.prmpEntiteRepository = prmpEntiteRepository;
        this.entiteContractRepository = entiteContractRepository;
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
        Map<String, CompteAuth> comptes = comptesParActeur();
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

    private List<AnnuairePersonneDto> controleurs(Map<String, CompteAuth> comptes) {
        Map<Integer, ProfilUtilisateur> profils = new HashMap<>();
        for (Profile p : profileRepository.findAll()) {
            // Résolution par le LIBELLÉ, comme l'authentification : ID_PROFILE n'a pas de sémantique fixée.
            profils.put(p.getIdProfile(), ProfilUtilisateur.resolve(p.getProfile()));
        }
        List<AnnuairePersonneDto> lignes = new ArrayList<>();
        for (Controleur c : controleurRepository.findAll()) {
            ProfilUtilisateur profil = c.getIdProfile() == null ? null : profils.get(c.getIdProfile());
            CompteAuth compte = comptes.get(cle(TypeActeur.CONTROLEUR, c.getImControleur()));
            lignes.add(new AnnuairePersonneDto(c.getImControleur(), TypeActeur.CONTROLEUR.name(),
                    c.getNomCont(), c.getPrenomsCont(), profil == null ? null : profil.name(),
                    c.getIdLocalite(), null, login(compte), statutDe(compte).name()));
        }
        return lignes;
    }

    private List<AnnuairePersonneDto> prmps(Map<String, CompteAuth> comptes, Map<String, String> entites) {
        List<AnnuairePersonneDto> lignes = new ArrayList<>();
        for (Prmp p : prmpRepository.findAll()) {
            CompteAuth compte = comptes.get(cle(TypeActeur.PRMP, p.getIdPrmp()));
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
    private List<AnnuairePersonneDto> ugpms(Map<String, CompteAuth> comptes, Map<String, String> entites) {
        List<AnnuairePersonneDto> lignes = new ArrayList<>();
        for (Ugpm u : ugpmRepository.findAll()) {
            CompteAuth compte = comptes.get(cle(TypeActeur.UGPM, u.getIdUgpm()));
            lignes.add(new AnnuairePersonneDto(u.getIdUgpm(), TypeActeur.UGPM.name(),
                    u.getNomUgpm(), u.getPrenomsUgpm(), null, null, entites.get(u.getIdPrmpTutelle()),
                    login(compte), statutDe(compte).name()));
        }
        return lignes;
    }

    // ------------------------------------------------------------------
    // Jointures en lot
    // ------------------------------------------------------------------

    /**
     * Comptes indexés par (type d'acteur, référence) — la jointure que le front ne peut pas faire :
     * le login n'est pas l'identifiant de la personne.
     *
     * <p>Rien n'interdit en base plusieurs comptes pour une même personne (la PK est le login) ; on
     * garde alors le <strong>moins fermé</strong>, car c'est l'accès dont elle dispose réellement.</p>
     */
    private Map<String, CompteAuth> comptesParActeur() {
        Map<String, CompteAuth> parActeur = new HashMap<>();
        for (CompteAuth compte : compteAuthRepository.findAll()) {
            if (compte.getTypeActeur() == null || compte.getRefActeur() == null) {
                continue;
            }
            parActeur.merge(compte.getTypeActeur() + " " + compte.getRefActeur(), compte,
                    AnnuaireService::leMoinsFerme);
        }
        return parActeur;
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
     * État d'accès d'une personne. {@link StatutCompte} ne comporte pas de valeur {@code DESACTIVE} —
     * {@code CompteAuthService.desactiver} ne touche que le booléen {@code ACTIF}, celui que le login
     * consulte — d'où la dernière ligne, qui réunit les comptes suspendus et les inscriptions
     * refusées. Même règle que {@code comptesSuspendus} de {@code KpiService}, pour que l'accueil et
     * l'annuaire comptent pareil.
     */
    static StatutCompteAnnuaire statutDe(CompteAuth compte) {
        if (compte == null) {
            return StatutCompteAnnuaire.SANS_COMPTE;
        }
        if (StatutCompte.EN_ATTENTE.name().equals(compte.getStatut())) {
            return StatutCompteAnnuaire.EN_ATTENTE;
        }
        return Boolean.TRUE.equals(compte.getActif())
                ? StatutCompteAnnuaire.ACTIF
                : StatutCompteAnnuaire.DESACTIVE;
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

    private static String cle(TypeActeur type, String ref) {
        return type.name() + " " + ref;
    }

    private static String login(CompteAuth compte) {
        return compte == null ? null : compte.getLogin();
    }

    private static CompteAuth leMoinsFerme(CompteAuth existant, CompteAuth candidat) {
        return rang(statutDe(candidat)) < rang(statutDe(existant)) ? candidat : existant;
    }

    private static int rang(StatutCompteAnnuaire statut) {
        return switch (statut) {
            case ACTIF -> 0;
            case EN_ATTENTE -> 1;
            case DESACTIVE -> 2;
            case SANS_COMPTE -> 3;
        };
    }
}
