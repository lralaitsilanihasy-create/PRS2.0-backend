package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.VerificationPieceDepotDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.entity.VerificationPieceDepot;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.repository.VerificationPieceDepotRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ Spec recevabilité au dépôt (2026-08-02) — contrôle de complétude des pièces par le SECRÉTAIRE
 * (pièce par pièce, avant enregistrement de la réception). Historisation append-only ; l'état courant
 * d'une pièce attendue = dernière décision. Fournit aussi la liste des DÉFAUTS courants (non conformes
 * + obligatoires manquantes), consommée par la garde de réception et par le signalement PRMP.
 */
@Service
@Transactional
public class VerificationPieceDepotService {

    public static final String DECISION_CONFORME = "CONFORME";
    public static final String DECISION_NON_CONFORME = "NON_CONFORME";
    public static final String DECISION_MANQUANTE = "MANQUANTE";
    private static final Set<String> DECISIONS = Set.of(DECISION_CONFORME, DECISION_NON_CONFORME, DECISION_MANQUANTE);

    private final VerificationPieceDepotRepository repository;
    private final DossierRepository dossierRepository;
    private final TypePieceJointeRepository typePieceJointeRepository;
    private final PieceJointeDossierRepository pieceJointeDossierRepository;

    public VerificationPieceDepotService(VerificationPieceDepotRepository repository,
            DossierRepository dossierRepository, TypePieceJointeRepository typePieceJointeRepository,
            PieceJointeDossierRepository pieceJointeDossierRepository) {
        this.repository = repository;
        this.dossierRepository = dossierRepository;
        this.typePieceJointeRepository = typePieceJointeRepository;
        this.pieceJointeDossierRepository = pieceJointeDossierRepository;
    }

    /** Historique complet des vérifications du dossier (ASC — traçabilité §6). */
    @Transactional(readOnly = true)
    public List<VerificationPieceDepotDto> historique(Integer idDossier) {
        return repository.findByIdDossierOrderByDateVerifAscIdVerifPieceAsc(idDossier).stream()
                .map(this::toDto).toList();
    }

    /** Enregistre une décision (append-only) — SECRÉTAIRE, dossier SOUMIS ou EN_ATTENTE_COMPLEMENTS_DEPOT. */
    public VerificationPieceDepotDto enregistrer(VerificationPieceDepotDto dto) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.SECRETAIRE) {
            throw new AccessDeniedException("Le contrôle de complétude des pièces est réservé au Secrétaire.");
        }
        if (!DECISIONS.contains(dto.getDecision())) {
            throw new BusinessRuleException("Décision invalide : « " + dto.getDecision()
                    + " » (attendu CONFORME, NON_CONFORME ou MANQUANTE).");
        }
        Dossier dossier = dossierRepository.findById(dto.getIdDossier())
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + dto.getIdDossier()));
        if (!StatutDossier.SOUMIS.name().equals(dossier.getStatut())
                && !StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException("Contrôle de complétude impossible : le dossier n'est pas au dépôt (statut « "
                    + dossier.getStatut() + " »).");
        }
        VerificationPieceDepot v = new VerificationPieceDepot();
        v.setIdDossier(dto.getIdDossier());
        v.setIdTypePiece(dto.getIdTypePiece());
        v.setIdPiece(dto.getIdPiece());
        v.setDecision(dto.getDecision());
        v.setObservation(dto.getObservation());
        v.setImSecretaire(CurrentUser.ref().orElse(null));
        v.setDateVerif(LocalDateTime.now());
        return toDto(repository.save(v));
    }

    /** État courant par type de pièce attendu : dernière décision (ordre chronologique). */
    @Transactional(readOnly = true)
    public Map<Integer, VerificationPieceDepot> etatCourant(Integer idDossier) {
        Map<Integer, VerificationPieceDepot> etat = new LinkedHashMap<>();
        for (VerificationPieceDepot v : repository.findByIdDossierOrderByDateVerifAscIdVerifPieceAsc(idDossier)) {
            etat.put(v.getIdTypePiece(), v);
        }
        return etat;
    }

    /**
     * DÉFAUTS courants du dossier : types attendus dont la dernière décision est NON_CONFORME ou
     * MANQUANTE, plus les OBLIGATOIRES sans pièce déposée et sans décision (manquantes de fait).
     * Renvoie « libellé — observation » (pour la garde 409 et la notification PRMP).
     */
    @Transactional(readOnly = true)
    public List<String> defautsCourants(Integer idDossier) {
        Dossier dossier = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        List<TypePieceJointe> attendus = typePieceJointeRepository.findAll().stream()
                .filter(t -> t.getIdTypeDossier() != null && t.getIdTypeDossier().equals(dossier.getIdTypeDossier()))
                .toList();
        Set<Integer> deposes = pieceJointeDossierRepository.findAll().stream()
                .filter(p -> idDossier.equals(p.getIdDossier()) && p.getIdTypePiece() != null)
                .map(p -> p.getIdTypePiece())
                .collect(java.util.stream.Collectors.toSet());
        Map<Integer, VerificationPieceDepot> etat = etatCourant(idDossier);

        List<String> defauts = new java.util.ArrayList<>();
        for (TypePieceJointe t : attendus) {
            VerificationPieceDepot v = etat.get(t.getIdTypePiece());
            if (v != null && !DECISION_CONFORME.equals(v.getDecision())) {
                defauts.add(t.getLibellePiece()
                        + (v.getObservation() != null && !v.getObservation().isBlank() ? " — " + v.getObservation() : "")
                        + (DECISION_MANQUANTE.equals(v.getDecision()) ? " (manquante)" : " (non conforme)"));
            } else if (v == null && Boolean.TRUE.equals(t.getObligatoire()) && !deposes.contains(t.getIdTypePiece())) {
                defauts.add(t.getLibellePiece() + " (manquante)");
            }
        }
        return defauts;
    }

    /**
     * Pièces OBLIGATOIRES du type non encore déclarées CONFORMES (garde d'enregistrement de la réception).
     * Vide = réception autorisée.
     */
    @Transactional(readOnly = true)
    public List<String> obligatoiresNonConformes(Integer idDossier) {
        Dossier dossier = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        Map<Integer, VerificationPieceDepot> etat = etatCourant(idDossier);
        return typePieceJointeRepository.findAll().stream()
                .filter(t -> t.getIdTypeDossier() != null && t.getIdTypeDossier().equals(dossier.getIdTypeDossier())
                        && Boolean.TRUE.equals(t.getObligatoire()))
                .filter(t -> {
                    VerificationPieceDepot v = etat.get(t.getIdTypePiece());
                    return v == null || !DECISION_CONFORME.equals(v.getDecision());
                })
                .map(TypePieceJointe::getLibellePiece)
                .toList();
    }

    private VerificationPieceDepotDto toDto(VerificationPieceDepot v) {
        return new VerificationPieceDepotDto(v.getIdVerifPiece(), v.getIdDossier(), v.getIdTypePiece(),
                v.getIdPiece(), v.getDecision(), v.getObservation(), v.getImSecretaire(), v.getDateVerif());
    }
}
