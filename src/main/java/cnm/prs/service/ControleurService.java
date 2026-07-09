package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.ControleurDto;
import cnm.prs.dto.PieceJointeMetaDto;
import cnm.prs.dto.SuppressionLotControleurResult;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.PieceJointe;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ControleurMapper;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.IndicateurCtrlRepository;
import cnm.prs.repository.LettreRenvoiRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.SessionUtilisateurRepository;
import cnm.prs.repository.VerificationRepository;

/**
 * Logique métier pour {@link Controleur}.
 */
@Service
@Transactional
public class ControleurService {

    private final ControleurRepository repository;
    private final CompteAuthRepository compteRepository;
    private final ExamenRepository examenRepository;
    private final PvExamenRepository pvExamenRepository;
    private final VerificationRepository verificationRepository;
    private final DispatchRepository dispatchRepository;
    private final ReceptionRepository receptionRepository;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final LettreRenvoiRepository lettreRenvoiRepository;
    private final SessionUtilisateurRepository sessionRepository;
    private final IndicateurCtrlRepository indicateurCtrlRepository;
    private final PieceJointeService pieceJointeService;

    public ControleurService(ControleurRepository repository, CompteAuthRepository compteRepository,
            ExamenRepository examenRepository, PvExamenRepository pvExamenRepository,
            VerificationRepository verificationRepository, DispatchRepository dispatchRepository,
            ReceptionRepository receptionRepository, DemandeRetraitRepository demandeRetraitRepository,
            LettreRenvoiRepository lettreRenvoiRepository, SessionUtilisateurRepository sessionRepository,
            IndicateurCtrlRepository indicateurCtrlRepository, PieceJointeService pieceJointeService) {
        this.repository = repository;
        this.compteRepository = compteRepository;
        this.examenRepository = examenRepository;
        this.pvExamenRepository = pvExamenRepository;
        this.verificationRepository = verificationRepository;
        this.dispatchRepository = dispatchRepository;
        this.receptionRepository = receptionRepository;
        this.demandeRetraitRepository = demandeRetraitRepository;
        this.lettreRenvoiRepository = lettreRenvoiRepository;
        this.sessionRepository = sessionRepository;
        this.indicateurCtrlRepository = indicateurCtrlRepository;
        this.pieceJointeService = pieceJointeService;
    }

    @Transactional(readOnly = true)
    public List<ControleurDto> findAll() {
        return repository.findAll().stream().map(ControleurMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ControleurDto findById(String id) {
        Controleur entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Controleur introuvable : " + id));
        return ControleurMapper.toDto(entity);
    }

    /** Contrôleurs affectés à une localité ({@code idLocalite} = X). Liste, vide si aucun ; transversaux exclus. */
    @Transactional(readOnly = true)
    public List<ControleurDto> findByLocalite(String idLocalite) {
        return repository.findByIdLocalite(idLocalite).stream().map(ControleurMapper::toDto).toList();
    }

    /** Contrôleurs d'un profil (rôle) donné ({@code idProfile} = X). Liste, vide si aucun. */
    @Transactional(readOnly = true)
    public List<ControleurDto> findByProfil(Integer idProfile) {
        return repository.findByIdProfile(idProfile).stream().map(ControleurMapper::toDto).toList();
    }

    /** Subordonnés directs d'un contrôleur ({@code idSuperieur} = imSuperieur). Liste, vide si aucun. */
    @Transactional(readOnly = true)
    public List<ControleurDto> findBySuperieur(String imSuperieur) {
        return repository.findByIdSuperieur(imSuperieur).stream().map(ControleurMapper::toDto).toList();
    }

    /** Recherche partielle par nom (contient, insensible à la casse). Liste, vide si aucun résultat. */
    @Transactional(readOnly = true)
    public List<ControleurDto> findByNom(String nom) {
        return repository.findByNomContContainingIgnoreCase(nom).stream().map(ControleurMapper::toDto).toList();
    }

    public ControleurDto create(ControleurDto dto) {
        Controleur entity = ControleurMapper.toEntity(dto);
        return ControleurMapper.toDto(repository.save(entity));
    }

    /**
     * Création avec photo <strong>optionnelle</strong> (miroir PRMP/UGPM, photo seule) : crée le contrôleur puis
     * stocke la {@code PHOTO} sous la clé {@code imControleur}. Transactionnel : un fichier invalide annule la
     * création (400).
     */
    public ControleurDto createAvecPhoto(ControleurDto dto, MultipartFile photo) {
        ControleurDto cree = create(dto);
        if (photo != null && !photo.isEmpty()) {
            stockerPhoto(cree.getImControleur(), TypePieceJointe.PHOTO, photo);
        }
        return cree;
    }

    /**
     * Dépose (ou remplace) la photo d'un contrôleur. {@code type} limité à {@code PHOTO} (tout autre → 400,
     * le contrôleur n'a pas d'autre pièce). <strong>404</strong> si le contrôleur est inconnu.
     */
    public PieceJointeMetaDto deposerPhoto(String imControleur, TypePieceJointe type, MultipartFile fichier) {
        exigerPhoto(type);
        if (!repository.existsById(imControleur)) {
            throw new ResourceNotFoundException("Controleur introuvable : " + imControleur);
        }
        return stockerPhoto(imControleur, type, fichier);
    }

    /** Récupère la photo d'un contrôleur. {@code type} ≠ {@code PHOTO} → 400 ; photo absente → 404. */
    @Transactional(readOnly = true)
    public PieceJointe telechargerPhoto(String imControleur, TypePieceJointe type) {
        exigerPhoto(type);
        return pieceJointeService.telecharger(imControleur, type);
    }

    /**
     * Supprime la photo d'un contrôleur (sans supprimer le contrôleur). {@code type} ≠ {@code PHOTO} → 400 ;
     * <strong>404</strong> si le contrôleur est inconnu ou si la photo est absente.
     */
    public void supprimerPhoto(String imControleur, TypePieceJointe type) {
        exigerPhoto(type);
        if (!repository.existsById(imControleur)) {
            throw new ResourceNotFoundException("Controleur introuvable : " + imControleur);
        }
        pieceJointeService.supprimer(imControleur, type);
    }

    /** Stocke la photo puis refuse un fichier qui n'est pas une image (JPEG/PNG) → 400 (rollback). */
    private PieceJointeMetaDto stockerPhoto(String imControleur, TypePieceJointe type, MultipartFile fichier) {
        PieceJointeMetaDto meta = pieceJointeService.stocker(imControleur, type, fichier);
        if ("application/pdf".equals(meta.format())) {
            throw new BadRequestException("La photo doit être une image (JPEG ou PNG), pas un PDF.");
        }
        return meta;
    }

    /** Le contrôleur n'a ni CIN ni arrêté : seule la pièce PHOTO est autorisée. */
    private void exigerPhoto(TypePieceJointe type) {
        if (type != TypePieceJointe.PHOTO) {
            throw new BadRequestException("Seule la pièce PHOTO est autorisée pour un contrôleur.");
        }
    }

    public ControleurDto update(String id, ControleurDto dto) {
        Controleur existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Controleur introuvable : " + id));
        existing.setNomCont(dto.getNomCont());
        existing.setPrenomsCont(dto.getPrenomsCont());
        existing.setEmailCont(dto.getEmailCont());
        existing.setTelCont(dto.getTelCont());
        existing.setIdProfile(dto.getIdProfile());
        existing.setIdLocalite(dto.getIdLocalite());
        existing.setIdSuperieur(dto.getIdSuperieur());
        existing.setTransversal(dto.getTransversal());
        return ControleurMapper.toDto(repository.save(existing));
    }

    /**
     * Modifie un contrôleur <strong>et remplace sa photo</strong> (miroir de {@link #createAvecPhoto}) : met à
     * jour la fiche via {@link #update} (404 si inconnu), puis remplace la {@code PHOTO} si elle est fournie.
     * Photo <strong>absente = inchangée</strong>. Transactionnel : un fichier invalide annule la modification (400).
     */
    public ControleurDto updateAvecPhoto(String id, ControleurDto dto, MultipartFile photo) {
        ControleurDto maj = update(id, dto);
        if (photo != null && !photo.isEmpty()) {
            stockerPhoto(id, TypePieceJointe.PHOTO, photo);
        }
        return maj;
    }

    /**
     * Supprime un contrôleur et son compte d'authentification. <strong>Garde métier</strong> : refuse (409) tant
     * qu'il a une participation métier (supérieur d'un autre contrôleur, ou présent sur un examen / PV /
     * vérification / dispatch / réception / demande de retrait / lettre signée). Sinon, nettoie ses données
     * <strong>dérivées</strong> (sessions, indicateurs) et son compte, puis supprime le contrôleur.
     */
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Controleur introuvable : " + id);
        }
        if (aUneActiviteMetier(id)) {
            throw new BusinessRuleException("Suppression impossible : le contrôleur « " + id + " » a une activité "
                    + "métier (subordonnés, examens, PV, vérifications, dispatchs, réceptions, demandes de retrait "
                    + "ou lettres signées). Retirez d'abord ces éléments.");
        }
        supprimerUn(id);
    }

    /**
     * Suppression <strong>en lot</strong> par matricule, <strong>tolérante</strong> : supprime chaque contrôleur
     * existant <em>sans activité métier</em> (données dérivées + compte) ; absents → {@code introuvables},
     * contrôleurs avec activité → {@code bloques} (comme le 409 unitaire). Jamais d'échec global. Doublons ignorés.
     */
    public SuppressionLotControleurResult supprimerLot(List<String> matricules) {
        List<String> supprimes = new ArrayList<>();
        List<String> introuvables = new ArrayList<>();
        List<String> bloques = new ArrayList<>();
        for (String id : matricules.stream().distinct().toList()) {
            if (!repository.existsById(id)) {
                introuvables.add(id);
            } else if (aUneActiviteMetier(id)) {
                bloques.add(id);
            } else {
                supprimerUn(id);
                supprimes.add(id);
            }
        }
        return new SuppressionLotControleurResult(supprimes, introuvables, bloques);
    }

    /** Vrai si le contrôleur a une participation métier (garde de suppression). */
    private boolean aUneActiviteMetier(String id) {
        return repository.existsByIdSuperieur(id)
                || examenRepository.existsByImCtrlMembre(id)
                || pvExamenRepository.existsAvecControleur(id)
                || verificationRepository.existsByImCtrlVerif(id)
                || dispatchRepository.existsAvecControleur(id)
                || receptionRepository.existsByImCtrlRecept(id)
                || demandeRetraitRepository.existsByImCtrlCc(id)
                || lettreRenvoiRepository.existsByImSignataire(id);
    }

    /** Supprime un contrôleur : données dérivées (sessions, indicateurs) + compte + le contrôleur. */
    private void supprimerUn(String id) {
        sessionRepository.deleteByImControleur(id);
        indicateurCtrlRepository.deleteByImControleur(id);
        pieceJointeService.purger(id);   // purge la photo (t_piece_jointe, clé imControleur) — pas d'orphelin
        compteRepository.deleteAll(compteRepository.findByRefActeurAndTypeActeur(id, TypeActeur.CONTROLEUR.name()));
        repository.deleteById(id);
    }
}
