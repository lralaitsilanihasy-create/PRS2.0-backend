package cnm.prs.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ExamenDto;
import cnm.prs.dto.ExamenSoumissionRequest;
import cnm.prs.dto.PvExamenDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Examen;
import cnm.prs.entity.Marche;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ExamenMapper;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenDetailRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link Examen}.
 */
@Service
@Transactional
public class ExamenService {

    /** Journal des transitions du circuit (⚠️ LOT 4 — 2026-08-26), format {@code [CIRCUIT] …}. */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExamenService.class);

    private final ExamenRepository repository;
    private final DispatchRepository dispatchRepository;
    private final DossierRepository dossierRepository;
    private final PvExamenService pvExamenService;
    private final ControleurDirectory controleurDirectory;
    private final PointsCtrlRepository pointsCtrlRepository;
    private final MarcheRepository marcheRepository;
    private final ExamenDetailRepository examenDetailRepository;
    /** ⚠️ Audit 2026-08-27 (lot B) — verrou d'état partagé avec les détails et les pièces d'examen. */
    private final ExamenGarde examenGarde;

    public ExamenService(ExamenRepository repository, DispatchRepository dispatchRepository,
            DossierRepository dossierRepository, PvExamenService pvExamenService,
            ControleurDirectory controleurDirectory, PointsCtrlRepository pointsCtrlRepository,
            MarcheRepository marcheRepository, ExamenDetailRepository examenDetailRepository,
            ExamenGarde examenGarde) {
        this.examenGarde = examenGarde;
        this.repository = repository;
        this.dispatchRepository = dispatchRepository;
        this.dossierRepository = dossierRepository;
        this.pvExamenService = pvExamenService;
        this.controleurDirectory = controleurDirectory;
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.marcheRepository = marcheRepository;
        this.examenDetailRepository = examenDetailRepository;
    }

    /**
     * ⚠️ Règle ajoutée — soumission de l'examen : produit le <strong>Projet de PV</strong>
     * (via {@link PvExamenService}, {@code idPv} alloué serveur) avec l'avis fourni
     * ({@code idAvis}, obligatoire sur le PV). Le <strong>Secrétaire de séance</strong>
     * ({@code idSecretaireSeance}) doit être un VERIFICATEUR de la localité du dossier ou un contrôleur
     * couvert par une paire « → Vérificateur » active (⚠️ règle élargie 2026-08-15 ; sinon 400
     * {@code erreurs:[{champ:idSecretaireSeance}]}). La lettre de renvoi est une action séparée.
     */
    public PvExamenDto soumettre(Integer idExamen, ExamenSoumissionRequest req) {
        Examen examen = repository.findById(idExamen)
                .orElseThrow(() -> new ResourceNotFoundException("Examen introuvable : " + idExamen));
        // ⚠️ Audit 2026-08-27 (lot B) — la garde attributaire n'était posée qu'à la CRÉATION : la
        // soumission, qui engage l'examen et produit le projet de PV, n'en avait aucune.
        Visibilite.exigerLocalite(dispatchRepository.findLocaliteById(examen.getIdDispatch()));
        exigerMembreAttributaire(examen.getIdDispatch());
        validerCompletude(idExamen);
        // ⚠️ Règle INVERSÉE (2026-08-31, réforme « Visa unique ») — l'AVIS revient au Membre : « le
        // Membre qui fait l'examen émet son avis à la fin de l'examen ; cet avis peut être modifié à la
        // fin de la navette » (pilote). La règle du 2026-08-01, qui le confiait au P/CC à l'acceptation,
        // est abandonnée. Le Secrétaire de séance, lui, RESTE posé au visa (arbitrage 3).
        //
        // ⚠️ LOT 1 : l'avis n'est pas encore OBLIGATOIRE ici. Le front n'ayant pas encore livré, exiger
        // le champ tout de suite casserait sa soumission pendant l'intervalle. Une soumission sans avis
        // crée donc encore un PV à avis NULL — cas déjà prévu, le visa devra alors le fournir (409). Le
        // caractère obligatoire (400) arrive en LOT 2, après la livraison du front.
        //
        // ⚠️ Ce que ce lot ferme DÉJÀ : jusqu'ici l'avis fourni à la soumission était posé SANS aucun
        // contrôle — validerCoherenceAvis n'existait que dans « accepter ». Un Membre pouvait donc
        // soumettre FAV avec des observations relevées, et rien ne l'arrêtait avant la clôture. Ce
        // n'était pas un simple déplacement de garde : c'était un trou.
        String idAvis = req.idAvis() == null || req.idAvis().isBlank() ? null : req.idAvis().trim();
        if (idAvis != null) {
            pvExamenService.validerCoherenceAvisPublic(idExamen, idAvis);
        }
        String idSecretaire = req.idSecretaireSeance() == null || req.idSecretaireSeance().isBlank()
                ? null
                : validerSecretaireSeance(idExamen, req.idSecretaireSeance());
        PvExamenDto pv = pvExamenService.creerProjet(idExamen, idAvis, idSecretaire);
        // ⚠️ Règle DÉPLACÉE (2026-08-01) — le dossier n'avance DISPATCHE → EXAMINE qu'à la SOUMISSION :
        // la création d'un examen est désormais un BROUILLON de progression (le dossier reste « à
        // examiner » et le Membre peut reprendre plus tard). Même transaction que le projet de PV.
        avancerDossierVersExamine(examen.getIdDispatch());
        return pv;
    }

    /**
     * ⚠️ Règle ajoutée (2026-07-21) — <strong>complétude</strong> de l'examen avant soumission (garantit
     * « toutes les lignes traitées ») : chaque point de <strong>portée LIGNE</strong> de la grille effective
     * du dossier doit être évalué <strong>pour chaque marché</strong>, et chaque point de <strong>portée
     * DOSSIER</strong> évalué <strong>une fois</strong> ({@code idDetail} nul). Sinon 400 ciblé {@code grille}.
     *
     * <p>Vacant (aucune exigence) si le dossier n'a pas de grille (famille/sous-type sans points) : les
     * examens historiques et non-PPM ne sont pas contraints.</p>
     */
    private void validerCompletude(Integer idExamen) {
        Integer idDossier = repository.findIdDossierByExamen(idExamen).orElse(null);
        Dossier dossier = idDossier == null ? null : dossierRepository.findById(idDossier).orElse(null);
        if (dossier == null) {
            return;
        }
        List<PointsCtrl> grille = pointsCtrlRepository.findGrilleEffective(
                dossier.getIdTypeDossier(), dossier.getIdSousType());
        if (grille.isEmpty()) {
            return;   // pas de grille pour ce (famille, sous-type) → rien à exiger
        }
        // ⚠️ 2026-08-05 (versionnement des PPM) — les lignes SUPPRIMÉES d'une version sont conservées en
        // base (restaurables, jamais effacées) mais ne font plus partie du plan : exiger leur évaluation
        // rendrait l'examen impossible à terminer.
        List<Marche> marches = marcheRepository.findByIdDossier(idDossier).stream()
                .filter(m -> !m.getSupprimee())
                .toList();
        Set<String> evalues = new HashSet<>();
        for (Object[] couple : examenDetailRepository.couplesEvalues(idExamen)) {
            evalues.add(cleCouple((Integer) couple[0], (Integer) couple[1]));
        }
        List<String> manquants = new ArrayList<>();
        for (PointsCtrl p : grille) {
            if (p.getPortee() == PorteePointCtrl.DOSSIER) {
                if (!evalues.contains(cleCouple(null, p.getIdPointCtrl()))) {
                    manquants.add("« " + p.getLibelPointCtrl() + " » (niveau dossier)");
                }
            } else {
                for (Marche m : marches) {
                    if (!evalues.contains(cleCouple(m.getIdDetail(), p.getIdPointCtrl()))) {
                        String marche = m.getDesignationMarche() == null || m.getDesignationMarche().isBlank()
                                ? "n°" + m.getIdDetail() : m.getDesignationMarche();
                        manquants.add("« " + p.getLibelPointCtrl() + " » — marché « " + marche + " »");
                    }
                }
            }
        }
        if (!manquants.isEmpty()) {
            String apercu = manquants.stream().limit(5).collect(Collectors.joining(" ; "));
            String reste = manquants.size() > 5 ? " … (+" + (manquants.size() - 5) + ")" : "";
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError("grille",
                    "Examen incomplet : " + manquants.size() + " évaluation(s) manquante(s) — chaque point LIGNE "
                            + "doit être évalué pour chaque marché et chaque point DOSSIER une fois. À évaluer : "
                            + apercu + reste + ".")));
        }
    }

    /** Clé d'un couple évalué ({@code idDetail} nul → « null ») pour la comparaison de complétude. */
    private static String cleCouple(Integer idDetail, Integer idPtControle) {
        return (idDetail == null ? "null" : idDetail) + "|" + idPtControle;
    }

    /**
     * Valide le Secrétaire de séance : Vérificateur TITULAIRE de la localité du dossier, OU
     * (⚠️ règle élargie 2026-08-15) contrôleur couvert par une paire « → Vérificateur » ACTIVE
     * de t_delegation_profil — même garde qu'à l'acceptation du PV.
     * @return le matricule validé (trimé)
     * @throws ChampsInvalidesException (→ 400) si absent ou non couvert
     */
    private String validerSecretaireSeance(Integer idExamen, String idSecretaire) {
        if (idSecretaire == null || idSecretaire.isBlank()) {
            throw champInvalide("Le secrétaire de séance (vérificateur) est obligatoire.");
        }
        // Localité du circuit (réception : examen→dispatch→réception→contrôleur récepteur), cf. lettres de renvoi.
        String localite = repository.findLocaliteByExamen(idExamen).orElse(null);
        if (!controleurDirectory.peutEtreSecretaireSeance(idSecretaire, localite)) {
            throw champInvalide("Le secrétaire de séance doit être un vérificateur de la localité du dossier, "
                    + "ou un contrôleur couvert par une délégation active vers Vérificateur.");
        }
        return idSecretaire.trim();
    }

    private ChampsInvalidesException champInvalide(String message) {
        return new ChampsInvalidesException(List.of(new ErrorResponse.FieldError("idSecretaireSeance", message)));
    }

    @Transactional(readOnly = true)
    public List<ExamenDto> findAll() {
        return Visibilite.filtrer(repository::findAll, repository::findVisiblesParLocalite)
                .stream().map(ExamenMapper::toDto).toList();
    }

    /** Code d'avis suggéré (référentiel tr_avis) : défavorable dès une non-conformité, sinon favorable. */
    private static final String AVIS_DEFAVORABLE = "DEF";
    private static final String AVIS_FAVORABLE = "FAV";

    @Transactional(readOnly = true)
    public ExamenDto findById(Integer id) {
        Examen entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examen introuvable : " + id));
        Visibilite.controler(loc -> repository.existsDansLocalite(id, loc));
        ExamenDto dto = ExamenMapper.toDto(entity);
        dto.setAvisSuggere(avisSuggere(id));
        return dto;
    }

    /**
     * ⚠️ Règle ajoutée (2026-07-21) — avis suggéré, non contraignant : {@code DEF} si ≥1 point non conforme,
     * sinon {@code FAV} ; {@code null} tant qu'aucun point n'est évalué (rien à suggérer).
     */
    private String avisSuggere(Integer idExamen) {
        List<cnm.prs.entity.ExamenDetail> details = examenDetailRepository.findByIdExamen(idExamen);
        if (details.isEmpty()) {
            return null;
        }
        boolean auMoinsUneNonConformite = details.stream().anyMatch(d -> Boolean.FALSE.equals(d.getConforme()));
        return auMoinsUneNonConformite ? AVIS_DEFAVORABLE : AVIS_FAVORABLE;
    }

    public ExamenDto create(ExamenDto dto) {
        Visibilite.exigerLocalite(dispatchRepository.findLocaliteById(dto.getIdDispatch()));
        exigerMembreAttributaire(dto.getIdDispatch());
        exigerDossierDispatche(dto.getIdDispatch());
        Examen entity = ExamenMapper.toEntity(dto);
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        entity.setIdExamen(ClePrimaire.reallouer(dto.getIdExamen(), repository::existsById, repository::nextIdExamen));
        Examen saved = repository.save(entity);
        // ⚠️ Règle DÉPLACÉE (2026-08-01) — la création N'AVANCE PLUS le statut du dossier : l'examen
        // créé est un BROUILLON de progression (sauvegarde à chaque étape côté front) ; le dossier ne
        // passe DISPATCHE → EXAMINE qu'à la soumission ({@link #soumettre}).
        return ExamenMapper.toDto(saved);
    }

    /**
     * [Auto] À la SOUMISSION de l'examen, le dossier passe de {@link StatutDossier#DISPATCHE} à
     * {@link StatutDossier#EXAMINE} (même transaction). Idempotent : on ne réécrit que si le dossier
     * est bien {@code DISPATCHE} (jamais un dossier déjà examiné/signé/clôturé).
     */
    private void avancerDossierVersExamine(Integer idDispatch) {
        Integer idDossier = idDispatch == null ? null
                : dossierRepository.findIdDossierByDispatch(idDispatch).orElse(null);
        if (idDossier == null) {
            return;
        }
        dossierRepository.findById(idDossier).ifPresent(d -> {
            if (StatutDossier.DISPATCHE.name().equals(d.getStatut())) {
                d.setStatut(StatutDossier.EXAMINE.name());
                dossierRepository.save(d);
                log.info("[CIRCUIT] examen clos dossier={} acteur={} dispatch={} statut={}",
                        idDossier, CurrentUser.login().orElse(null), idDispatch,
                        StatutDossier.EXAMINE.name());
            }
        });
    }

    /**
     * Autorisation (§2.4, §3.5) : un Membre <strong>titulaire</strong> n'examine que les dossiers qui
     * lui sont <strong>attribués</strong> ({@code Dispatch.imCtrlMembre}). Un CC / Président instruisant
     * <strong>par délégation</strong> (profil ≠ MEMBRE, déjà contrôlé en localité) reste autorisé.
     *
     * @throws AccessDeniedException (→ 403) si un Membre tente d'examiner le dossier d'un autre Membre
     */
    private void exigerMembreAttributaire(Integer idDispatch) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.MEMBRE) {
            return; // délégation (CC/Président) : autorisé, localité déjà vérifiée
        }
        String attributaire = idDispatch == null ? null
                : dispatchRepository.findImCtrlMembreById(idDispatch).orElse(null);
        String moi = CurrentUser.ref().orElse(null);
        if (attributaire == null || !attributaire.equals(moi)) {
            throw new AccessDeniedException(
                    "Examen réservé au Membre attributaire du dispatch (§2.4) : vous n'êtes pas l'attributaire.");
        }
    }

    /**
     * Précondition du circuit (§2.3 → §2.4) : on n'examine qu'un dossier <strong>déjà dispatché</strong>
     * (statut {@link StatutDossier#DISPATCHE}). Le dispatch précède l'examen et fait passer le dossier
     * de PRET_DISPATCH à DISPATCHE ; un dossier non dispatché (ou clôturé/retiré) est refusé.
     */
    private void exigerDossierDispatche(Integer idDispatch) {
        String statut = idDispatch == null ? null
                : dossierRepository.findStatutByDispatch(idDispatch).orElse(null);
        if (!StatutDossier.DISPATCHE.name().equals(statut)) {
            throw new BusinessRuleException(
                    "Examen impossible : le dossier doit avoir été dispatché (statut DISPATCHE) (§2.4), "
                            + "statut actuel « " + statut + " ».");
        }
    }

    /**
     * ⚠️ Audit 2026-08-27 (lot B) — deux trous fermés sur ce {@code PUT} générique :
     * <ul>
     *   <li>la <strong>garde attributaire</strong> n'était appelée qu'à la création : n'importe quel
     *       Membre de la localité réécrivait l'examen d'un autre. Elle est jouée sur le dispatch
     *       <em>en place</em> comme sur celui <em>visé</em> par le corps (sinon un attributaire
     *       pourrait déplacer un examen qui ne lui appartient pas vers son propre dispatch) ;</li>
     *   <li>{@code imCtrlMembre} était <strong>recopié du corps</strong> : l'attributaire est une donnée
     *       du dispatch (§2.4), pas une déclaration du client. La valeur existante est conservée —
     *       même principe qu'à la création du projet de PV, où elle est dérivée du dispatch.</li>
     * </ul>
     */
    public ExamenDto update(Integer id, ExamenDto dto) {
        Examen existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examen introuvable : " + id));
        Visibilite.exigerLocalite(dispatchRepository.findLocaliteById(existing.getIdDispatch()));
        exigerMembreAttributaire(existing.getIdDispatch());
        Visibilite.exigerLocalite(dispatchRepository.findLocaliteById(dto.getIdDispatch()));
        exigerMembreAttributaire(dto.getIdDispatch());
        exigerExamenModifiable(id);
        existing.setIdDispatch(dto.getIdDispatch());
        existing.setDateExamen(dto.getDateExamen());
        return ExamenMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Examen introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /**
     * Verrou d'édition de l'examen (§2.6) : DISPATCHE (brouillon), EXAMINE ou A_REEXAMINER ; dès la
     * signature du PV l'examen est <strong>définitif</strong> → 409.
     *
     * <p>⚠️ Audit 2026-08-27 (lot B) — règle unique, portée par {@link ExamenGarde} : elle était
     * recopiée ici et dans {@code ExamenDetailService}, et manquait aux pièces d'examen.</p>
     */
    private void exigerExamenModifiable(Integer idExamen) {
        examenGarde.exigerExamenModifiable(idExamen);
    }
}
