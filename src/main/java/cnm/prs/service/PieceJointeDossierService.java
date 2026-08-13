package cnm.prs.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.PieceJointeDossierDto;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PieceJointeDossierMapper;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.security.CurrentUser;

/**
 * Logique métier pour {@link PieceJointeDossier} : pièces jointes d'un dossier. Upload multipart
 * validé par magic-bytes (PDF/JPEG/PNG) ; {@code apresLettreRenvoi} distingue les pièces ajoutées
 * après réception d'une lettre de renvoi (dossier SOUMIS/PRET_DISPATCH + {@code idLettre}).
 */
@Service
@Transactional
public class PieceJointeDossierService {

    private final PieceJointeDossierRepository repository;
    private final TypePieceJointeRepository typePieceRepository;
    private final DossierRepository dossierRepository;
    private final DispatchRepository dispatchRepository;
    private final ControleurRepository controleurRepository;
    private final NotificationService notificationService;
    /** ⚠️ Spec « Mandats PRMP » — garde de propriété partagée (attribution OU PRMP en fonction) + vacance. */
    private final DossierIntegriteService dossierIntegrite;

    public PieceJointeDossierService(PieceJointeDossierRepository repository,
            TypePieceJointeRepository typePieceRepository, DossierRepository dossierRepository,
            DispatchRepository dispatchRepository, ControleurRepository controleurRepository,
            NotificationService notificationService, DossierIntegriteService dossierIntegrite) {
        this.dossierIntegrite = dossierIntegrite;
        this.repository = repository;
        this.typePieceRepository = typePieceRepository;
        this.dossierRepository = dossierRepository;
        this.dispatchRepository = dispatchRepository;
        this.controleurRepository = controleurRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<PieceJointeDossierDto> findByDossier(Integer idDossier) {
        return repository.findByIdDossier(idDossier).stream().map(this::toDtoAvecLibelle).toList();
    }

    @Transactional(readOnly = true)
    public PieceJointeDossierDto findById(Integer id) {
        return toDtoAvecLibelle(exigerExistante(id));
    }

    /** Pièce complète (contenu + format) pour téléchargement. */
    @Transactional(readOnly = true)
    public PieceJointeDossier telecharger(Integer id) {
        return exigerExistante(id);
    }

    /**
     * Upload d'une pièce par la PRMP propriétaire. {@code apresLettreRenvoi=true} si {@code idLettre}
     * est fourni et le dossier est SOUMIS/PRET_DISPATCH (flux historique) ou
     * {@code EN_ATTENTE_PIECES} (⚠️ spec navette 2026-08-02 — dossier suspendu par la lettre signée ;
     * la reprise reste une action EXPLICITE de la PRMP : {@code …/transmettre-complements}) ;
     * sinon pièce initiale (false).
     */
    public PieceJointeDossierDto store(PieceJointeDossierDto meta, MultipartFile fichier) {
        Dossier dossier = dossierRepository.findById(meta.getIdDossier())
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + meta.getIdDossier()));
        exigerProprietaire(dossier);
        boolean apres = false;
        Integer idLettre = null;
        if (meta.getIdLettre() != null
                && (StatutDossier.SOUMIS.name().equals(dossier.getStatut())
                        || StatutDossier.PRET_DISPATCH.name().equals(dossier.getStatut())
                        || StatutDossier.EN_ATTENTE_PIECES.name().equals(dossier.getStatut()))) {
            apres = true;
            idLettre = meta.getIdLettre();
        }
        PieceJointeDossier saved = enregistrer(meta.getIdDossier(), meta.getIdTypePiece(), fichier, apres, idLettre);
        // ⚠️ 2026-08-03 (demande user) — un dépôt pendant la RECTIFICATION (EN_ATTENTE_DECISION_PRMP)
        // est une VERSION CORRIGÉE : marquée pour être distinguée de l'originale dans toutes les listes.
        if (StatutDossier.EN_ATTENTE_DECISION_PRMP.name().equals(dossier.getStatut())) {
            saved.setVersionCorrigee(true);
            saved = repository.save(saved);
        }
        if (apres) {
            // No-op pour EN_ATTENTE_PIECES (garde PRET_DISPATCH interne) : la reprise passe par
            // POST /api/dossiers/{id}/transmettre-complements, pas par le dépôt.
            rouvrirExamenApresRenvoi(meta.getIdDossier());
        }
        return toDtoAvecLibelle(saved);
    }

    /**
     * (Règle ajoutée) Complétion d'un dossier après lettre de renvoi. Au <strong>premier</strong> dépôt
     * {@code apresLettreRenvoi} (dossier {@code PRET_DISPATCH}), on <strong>réutilise le dispatch existant</strong>
     * (le Membre y est déjà désigné) : simple avance {@code PRET_DISPATCH → DISPATCHE} — pas de nouveau dispatch,
     * donc aucun doublon — et le dossier réapparaît dans l'écran « à examiner » du Membre. Une <strong>unique</strong>
     * notification (regroupée) est émise vers ce Membre. Les dépôts suivants trouvent le dossier déjà {@code DISPATCHE}
     * (donc {@code apres=false}) : ni ré-avance ni notification en double.
     */
    private void rouvrirExamenApresRenvoi(Integer idDossier) {
        Dossier d = dossierRepository.findById(idDossier).orElse(null);
        if (d == null || !StatutDossier.PRET_DISPATCH.name().equals(d.getStatut())) {
            return;
        }
        String imMembre = dispatchRepository.findImCtrlMembreByDossier(idDossier).orElse(null);
        d.setStatut(StatutDossier.DISPATCHE.name());
        dossierRepository.save(d);
        if (imMembre != null && !imMembre.isBlank()) {
            String email = controleurRepository.findById(imMembre).map(Controleur::getEmailCont).orElse(null);
            String ref = d.getRefeDossier() != null && !d.getRefeDossier().isBlank()
                    ? d.getRefeDossier() : ("n° " + idDossier);
            notificationService.emettreControleur(TypeNotification.PIECE_AJOUTEE_APRES_RENVOI, imMembre, email,
                    idDossier, TypeObjet.DOSSIER, idDossier,
                    "Dossier complété après renvoi",
                    "Le dossier " + ref + " a été complété par la PRMP après lettre de renvoi ; il est à ré-examiner.");
        }
    }

    /** Enregistrement d'une pièce initiale (à la saisie) : {@code apresLettreRenvoi=false}. */
    public PieceJointeDossier enregistrerInitiale(Integer idDossier, Integer idTypePiece, MultipartFile fichier) {
        return enregistrer(idDossier, idTypePiece, fichier, false, null);
    }

    private PieceJointeDossier enregistrer(Integer idDossier, Integer idTypePiece, MultipartFile fichier,
            boolean apresLettreRenvoi, Integer idLettre) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Pièce jointe manquante ou vide.");
        }
        byte[] contenu = lire(fichier);
        String format = detecterFormat(contenu);
        if (format == null) {
            throw new BadRequestException("Type de fichier non autorisé : seuls PDF, JPEG et PNG sont acceptés.");
        }
        PieceJointeDossier p = new PieceJointeDossier();
        p.setIdDossier(idDossier);
        p.setIdTypePiece(idTypePiece);
        p.setNomFichier(fichier.getOriginalFilename());
        p.setContenu(contenu);
        p.setFormat(format);
        p.setTaille((long) contenu.length);
        p.setDateUpload(LocalDateTime.now());
        p.setApresLettreRenvoi(apresLettreRenvoi);
        p.setIdLettre(idLettre);
        return repository.save(p);
    }

    /** Suppression : Administrateur, ou PRMP propriétaire d'un dossier encore BROUILLON. */
    public void delete(Integer id) {
        PieceJointeDossier piece = exigerExistante(id);
        if (CurrentUser.profil().orElse(null) == ProfilUtilisateur.ADMINISTRATEUR) {
            repository.deleteById(id);
            return;
        }
        Dossier dossier = dossierRepository.findById(piece.getIdDossier())
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + piece.getIdDossier()));
        exigerProprietaire(dossier);
        if (!StatutDossier.BROUILLON.name().equals(dossier.getStatut())) {
            throw new BusinessRuleException("Suppression possible uniquement tant que le dossier est BROUILLON.");
        }
        repository.deleteById(id);
    }

    private PieceJointeDossier exigerExistante(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pièce jointe introuvable : " + id));
    }

    /**
     * Propriété : seule la PRMP propriétaire du dossier peut déposer/supprimer ses pièces.
     *
     * <p>⚠️ Spec « Mandats PRMP » — délégué à la garde partagée, qui accepte aussi la PRMP <em>en fonction</em>
     * (reprise du traitement après changement de titulaire) et refuse toute action pendant la vacance.
     * Un dossier sans PRMP propriétaire reste refusé ici : déposer une pièce suppose un périmètre connu.</p>
     */
    private void exigerProprietaire(Dossier dossier) {
        if (dossier.getIdPrmp() == null) {
            throw new AccessDeniedException("Pièce réservée à la PRMP propriétaire du dossier.");
        }
        dossierIntegrite.exigerOperateurHabilite(dossier);
    }

    private PieceJointeDossierDto toDtoAvecLibelle(PieceJointeDossier entity) {
        PieceJointeDossierDto dto = PieceJointeDossierMapper.toDto(entity);
        if (dto != null && dto.getIdTypePiece() != null) {
            typePieceRepository.findById(dto.getIdTypePiece())
                    .ifPresent(t -> dto.setLibellePiece(t.getLibellePiece()));
        }
        return dto;
    }

    private byte[] lire(MultipartFile fichier) {
        try {
            return fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Lecture du fichier impossible : " + e.getMessage());
        }
    }

    /** Magic-bytes : renvoie {@code PDF}/{@code JPEG}/{@code PNG} ou {@code null} si non reconnu. */
    private String detecterFormat(byte[] d) {
        if (d.length >= 4 && d[0] == 0x25 && d[1] == 0x50 && d[2] == 0x44 && d[3] == 0x46) {
            return "PDF"; // %PDF
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "JPEG";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "PNG";
        }
        return null;
    }
}
