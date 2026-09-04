package cnm.prs.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ProcessusMarche;
import cnm.prs.dto.SaisieMarcheLigne;
import cnm.prs.entity.Capm;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.enums.CategorieModePassation;
import cnm.prs.enums.FormeMarche;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.mapper.MarcheMapper;
import cnm.prs.repository.CapmRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.PpmRepository;

/**
 * ⚠️ <strong>Justifications de la fiche de présentation</strong> (arbitrage du pilote, 2026-09-01).
 *
 * <p>La « Fiche de présentation » du dossier de planification énumère trois catégories de marchés qui
 * appellent une justification : ① mode <strong>dérogatoire</strong>, ② <strong>délai aménagé</strong>,
 * ③ <strong>contrat-cadre</strong>. Le pilote a tranché : ces justifications se saisissent à la
 * création du dossier et sont <strong>bloquantes</strong>.</p>
 *
 * <p><strong>Le classement est refait ici, jamais lu dans la requête.</strong> Le front calcule les
 * mêmes listes pour son affichage, mais un client qui se tromperait — ou qui mentirait — obtiendrait
 * sinon la création d'un dossier dépourvu des justifications réglementaires. La catégorie du mode, son
 * plancher de délai, la forme du marché et les dates CAPM sont donc relus depuis les référentiels du
 * serveur. À l'inverse, une justification envoyée sur une ligne que le serveur ne classe pas est
 * simplement stockée : on ne fabrique pas d'erreur là où il n'y a pas de règle.</p>
 *
 * <p><strong>Où la garde s'applique.</strong> Aux deux entrées nommées par l'arbitrage — la création
 * ({@code POST /api/saisies/ppm}) et l'édition ({@code PUT /api/saisies/ppm/{idDossier}}). La mise à
 * jour d'un PPM <em>pilotée par import PDF</em> ({@link MiseAJourPpmService}) traverse le même code de
 * persistance mais en est <strong>exemptée</strong> : un PDF ne peut structurellement pas porter de
 * justification, et l'y soumettre rendrait toute mise à jour par import définitivement impossible dès
 * qu'une ligne dérogatoire y apparaît. Conséquence assumée et signalée au pilote : une version créée
 * par import peut contenir un marché dérogatoire non justifié, que la fiche affichera « À compléter »
 * jusqu'à édition.</p>
 */
@Service
public class FicheJustificationsService {

    /** Mot-clé du processus CAPM ouvrant le délai de publicité. */
    private static final String ETAPE_LANCEMENT = "LANCEMENT";

    /** Mot-clé du processus CAPM fermant le délai de publicité. */
    private static final String ETAPE_OUVERTURE = "OUVERTURE";

    private final ModePassationRepository modePassationRepository;
    private final CapmRepository capmRepository;
    private final MarcheRepository marcheRepository;
    private final MarchePrevisionRepository marchePrevisionRepository;
    private final PpmRepository ppmRepository;

    public FicheJustificationsService(ModePassationRepository modePassationRepository,
            CapmRepository capmRepository, MarcheRepository marcheRepository,
            MarchePrevisionRepository marchePrevisionRepository, PpmRepository ppmRepository) {
        this.modePassationRepository = modePassationRepository;
        this.capmRepository = capmRepository;
        this.marcheRepository = marcheRepository;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.ppmRepository = ppmRepository;
    }

    /**
     * Vérifie les justifications d'une saisie complète et lève un 400 <strong>unique</strong> portant
     * TOUTES les erreurs. Une erreur par manque, et non la première rencontrée : le front affiche un
     * panneau « justifications manquantes » et doit pouvoir montrer d'un coup toutes les lignes à
     * compléter, comme il le fait déjà pour les pièces obligatoires.
     *
     * @param lignes             lignes de la requête, dans leur ordre d'envoi (l'index nourrit le
     *                           chemin de champ {@code marches[i].…})
     * @param justificationFiche justification globale envoyée ({@code null} = non fournie)
     * @param idPpmExistant      PPM en cours d'édition, pour lire la globale déjà stockée ;
     *                           {@code null} à la création
     */
    public void exigerJustifications(List<SaisieMarcheLigne> lignes, String justificationFiche,
            Integer idPpmExistant) {
        List<SaisieMarcheLigne> effectives = lignes == null ? List.of() : lignes;
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        boolean uneListeNonVide = false;

        for (int i = 0; i < effectives.size(); i++) {
            SaisieMarcheLigne ligne = effectives.get(i);
            Marche stockee = ligneStockee(ligne);
            Classement classement = classer(ligne, stockee);
            uneListeNonVide |= classement.concerneLaFiche();

            if (classement.derogatoire() && vide(valeurEffective(ligne.justifModeDerogatoire(),
                    stockee == null ? null : stockee.getJustifModeDerogatoire()))) {
                erreurs.add(new ErrorResponse.FieldError("marches[" + i + "].justifModeDerogatoire",
                        "Ce marché est passé selon un mode dérogatoire : sa justification est obligatoire."));
            }
            if (classement.delaiAmenage() && vide(valeurEffective(ligne.justifDelaiAmenage(),
                    stockee == null ? null : stockee.getJustifDelaiAmenage()))) {
                erreurs.add(new ErrorResponse.FieldError("marches[" + i + "].justifDelaiAmenage",
                        "Le délai entre le lancement et l'ouverture des plis est inférieur au minimum "
                                + "réglementaire du mode : sa justification est obligatoire."));
            }
        }

        // La globale ne se réclame que si la fiche a matière à justifier : un plan entièrement conforme
        // n'en a pas besoin, et le formulaire officiel laisse alors la rubrique vide.
        if (uneListeNonVide && vide(valeurEffective(justificationFiche, globaleStockee(idPpmExistant)))) {
            erreurs.add(new ErrorResponse.FieldError("justificationFiche",
                    "La fiche de présentation comporte au moins un marché à justifier (mode dérogatoire, "
                            + "délai aménagé ou contrat-cadre) : la justification globale est obligatoire."));
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
    }

    /**
     * ⚠️ <strong>La fiche de présentation est-elle VIDE ?</strong> (règle du pilote, 2026-09-04 — « s'il
     * n'y a pas de contenu dans un onglet, sauter le contrôle : on ne contrôle pas le vide »).
     *
     * <p>Vide au sens du document : parmi les marchés <strong>non supprimés</strong> du dossier, aucun
     * n'alimente les trois listes de la fiche — ni mode dérogatoire, ni délai aménagé, ni contrat-cadre.
     * Le document existe alors mais ne porte aucune ligne : exiger qu'on statue des points de contrôle
     * dessus reviendrait à demander un avis sur une page blanche.</p>
     *
     * <p><strong>La dérivation vit ici, et nulle part ailleurs.</strong> C'est déjà cette classe qui
     * décide, à la saisie, si une ligne « concerne la fiche » — la refaire dans
     * {@code ExamenService} aurait créé deux définitions du même document, libres de diverger au
     * premier ajustement de règle. Le classement est donc le même que celui des justifications
     * obligatoires, appliqué cette fois aux lignes <em>telles qu'elles sont en base</em> : ni requête
     * ni saisie en cours, rien que le plan tel qu'il est examiné.</p>
     */
    @Transactional(readOnly = true)
    public boolean ficheVide(Integer idDossier) {
        if (idDossier == null) {
            return true;
        }
        return marcheRepository.findByIdDossier(idDossier).stream()
                .filter(m -> !Boolean.TRUE.equals(m.getSupprimee()))
                .noneMatch(this::concerneLaFiche);
    }

    /**
     * Classement d'une ligne <strong>déjà persistée</strong> au regard des trois listes de la fiche —
     * pendant de {@link #classer} pour la dérivation, où il n'y a pas de requête à faire primer.
     */
    private boolean concerneLaFiche(Marche stockee) {
        ModePassation mode = stockee.getIdMode() == null ? null
                : modePassationRepository.findById(stockee.getIdMode()).orElse(null);
        if (mode != null && CategorieModePassation.DEROGATOIRE.equals(mode.getCategorie())) {
            return true;
        }
        if (FormeMarche.CONTRAT_CADRE.equals(stockee.getFormeMarche())) {
            return true;
        }
        // Le délai aménagé est le seul des trois qui se calcule : il exige le plancher du mode et les
        // deux dates du processus. Sans eux, pas de classement — une donnée manquante ne se devine pas,
        // exactement comme à la saisie.
        return mode != null && mode.getDelaiMinJours() != null
                && sousLePlancher(datesStockees(stockee), mode);
    }

    /**
     * Classement d'une ligne selon les référentiels du serveur.
     *
     * @param stockee ligne déjà persistée s'il s'agit d'une mise à jour, {@code null} sinon — elle
     *                fournit le mode, la forme et les dates que la requête ne renvoie pas
     */
    private Classement classer(SaisieMarcheLigne ligne, Marche stockee) {
        ModePassation mode = modeDe(ligne, stockee);
        boolean derogatoire = mode != null && CategorieModePassation.DEROGATOIRE.equals(mode.getCategorie());
        boolean contratCadre = FormeMarche.CONTRAT_CADRE
                .equals(FormeMarche.depuisCodeOuDefaut(formeDe(ligne, stockee)));
        return new Classement(derogatoire, delaiAmenage(ligne, stockee, mode), contratCadre);
    }

    /**
     * Délai aménagé : {@code ouverture − lancement} en jours <strong>calendaires</strong>,
     * <strong>strictement inférieur</strong> au plancher du mode. Pas de classement sans les deux dates
     * ni sans plancher — l'égalité est conforme, et une donnée manquante ne se devine pas.
     */
    private boolean delaiAmenage(SaisieMarcheLigne ligne, Marche stockee, ModePassation mode) {
        if (mode == null || mode.getDelaiMinJours() == null) {
            return false;
        }
        Map<String, LocalDate> dates = datesDuDelai(ligne, stockee);
        LocalDate lancement = dates.get(ETAPE_LANCEMENT);
        LocalDate ouverture = dates.get(ETAPE_OUVERTURE);
        return sousLePlancher(dates, mode);
    }

    /** Cœur du classement « délai aménagé », partagé par la saisie et la dérivation depuis la base. */
    private boolean sousLePlancher(Map<String, LocalDate> dates, ModePassation mode) {
        LocalDate lancement = dates.get(ETAPE_LANCEMENT);
        LocalDate ouverture = dates.get(ETAPE_OUVERTURE);
        if (lancement == null || ouverture == null) {
            return false;
        }
        return ChronoUnit.DAYS.between(lancement, ouverture) < mode.getDelaiMinJours().longValue();
    }

    /**
     * Dates de lancement et d'ouverture d'une ligne. La liste {@code processus} <strong>fournie</strong>
     * fait foi — elle remplacera les prévisions existantes ; <strong>absente</strong>, les prévisions
     * déjà stockées sont lues. C'est le contrat de la façade d'édition, où une liste nulle conserve les
     * enfants : le classement doit porter sur ce que le dossier vaudra APRÈS écriture, pas sur ce que
     * la requête montre.
     */
    private Map<String, LocalDate> datesDuDelai(SaisieMarcheLigne ligne, Marche stockee) {
        Map<String, LocalDate> dates = new HashMap<>();
        if (ligne.processus() != null) {
            for (ProcessusMarche p : ligne.processus()) {
                if (p == null || p.idCapm() == null || p.dateDebut() == null) {
                    continue;
                }
                capmRepository.findById(p.idCapm()).ifPresent(capm -> retenirSiEtape(dates, capm, p.dateDebut()));
            }
        } else if (stockee != null) {
            dates.putAll(datesStockees(stockee));
        }
        return dates;
    }

    /** Dates de lancement et d'ouverture LUES EN BASE — le chemin de la dérivation, sans requête. */
    private Map<String, LocalDate> datesStockees(Marche stockee) {
        Map<String, LocalDate> dates = new HashMap<>();
        for (MarchePrevision prev : marchePrevisionRepository.findByIdDetail(stockee.getIdDetail())) {
            if (prev.getDateDebut() == null || prev.getIdCapm() == null) {
                continue;
            }
            capmRepository.findById(prev.getIdCapm())
                    .ifPresent(capm -> retenirSiEtape(dates, capm, prev.getDateDebut()));
        }
        return dates;
    }

    /**
     * Range une date sous {@code LANCEMENT} ou {@code OUVERTURE} si le libellé du processus contient le
     * mot-clé, en réutilisant la normalisation des libellés du dépôt (accents, casse, séparateurs,
     * pluriels) : « 2 - Lancement de l'appel d'offres » et « LANCEMENT » se rejoignent. Si plusieurs
     * étapes portent le même mot-clé, la <strong>plus précoce</strong> est retenue.
     */
    private void retenirSiEtape(Map<String, LocalDate> dates, Capm capm, LocalDate date) {
        String libelle = LibelleNormalisation.normaliser(capm.getLibelleProcessus());
        for (String etape : List.of(ETAPE_LANCEMENT, ETAPE_OUVERTURE)) {
            if (libelle.contains(etape)) {
                LocalDate connue = dates.get(etape);
                if (connue == null || date.isBefore(connue)) {
                    dates.put(etape, date);
                }
            }
        }
    }

    /**
     * Mode de la ligne, résolu depuis les référentiels du serveur. L'{@code idMode} de la requête prime ;
     * à défaut, celui de la ligne stockée ; à défaut encore, le libellé est cherché dans
     * {@code tr_mode_passation}. Un mode donné par <strong>libellé seul et inconnu</strong> sera créé à
     * la volée par la façade, sans catégorie ni plancher : il n'est donc ni dérogatoire ni contraint en
     * délai, et rien ne sera exigé. C'est la conséquence directe et voulue du principe « le serveur
     * classe depuis SES référentiels » — on n'exige pas une justification au nom d'une règle qu'on ne
     * connaît pas encore.
     */
    private ModePassation modeDe(SaisieMarcheLigne ligne, Marche stockee) {
        Integer idMode = ligne.idMode() != null ? ligne.idMode() : (stockee == null ? null : stockee.getIdMode());
        if (idMode != null) {
            return modePassationRepository.findById(idMode).orElse(null);
        }
        if (ligne.modeLibelle() == null || ligne.modeLibelle().isBlank()) {
            return null;
        }
        String cible = LibelleNormalisation.normaliser(ligne.modeLibelle());
        return modePassationRepository.findAll().stream()
                .filter(m -> LibelleNormalisation.normaliser(m.getLibelle()).equals(cible))
                .findFirst().orElse(null);
    }

    /** Forme du marché : celle de la requête si fournie, sinon celle déjà stockée. */
    private String formeDe(SaisieMarcheLigne ligne, Marche stockee) {
        if (ligne.formeMarche() != null && !ligne.formeMarche().isBlank()) {
            return ligne.formeMarche();
        }
        return stockee == null ? null : stockee.getFormeMarche().name();
    }

    /** Ligne déjà persistée portant cet {@code idDetail}, ou {@code null} si la ligne est neuve. */
    private Marche ligneStockee(SaisieMarcheLigne ligne) {
        return ligne.idDetail() == null ? null : marcheRepository.findById(ligne.idDetail()).orElse(null);
    }

    /** Justification globale déjà stockée sur le PPM en cours d'édition ; {@code null} à la création. */
    private String globaleStockee(Integer idPpm) {
        if (idPpm == null) {
            return null;
        }
        return ppmRepository.findById(idPpm).map(p -> p.getJustificationFiche()).orElse(null);
    }

    /**
     * Valeur retenue pour la garde : celle de la requête si elle est <strong>fournie</strong> (une
     * chaîne blanche est fournie — et vaut effacement, donc absence), sinon celle déjà stockée. Exact
     * miroir de la sémantique d'écriture « {@code null} = inchangé ».
     */
    private static String valeurEffective(String envoyee, String stockee) {
        return envoyee != null ? envoyee : stockee;
    }

    /** Absente au sens de la règle : {@code null} ou uniquement des blancs. */
    private static boolean vide(String s) {
        return MarcheMapper.texteOuNull(s) == null;
    }

    /**
     * Classement d'une ligne au regard des trois listes de la fiche.
     *
     * @param derogatoire  liste ① — mode de catégorie {@code DEROGATOIRE}
     * @param delaiAmenage liste ② — délai de publicité sous le plancher du mode
     * @param contratCadre liste ③ — forme {@code CONTRAT_CADRE}. Sans justification par ligne : la
     *                     globale la couvre, comme sur le formulaire papier
     */
    private record Classement(boolean derogatoire, boolean delaiAmenage, boolean contratCadre) {

        /** Vrai si la ligne figure dans une des trois listes — ce qui rend la globale obligatoire. */
        boolean concerneLaFiche() {
            return derogatoire || delaiAmenage || contratCadre;
        }
    }
}
