package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ControleurDto;
import cnm.prs.dto.SuppressionLotControleurResult;
import cnm.prs.entity.Controleur;
import cnm.prs.enums.TypeActeur;
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

    public ControleurService(ControleurRepository repository, CompteAuthRepository compteRepository,
            ExamenRepository examenRepository, PvExamenRepository pvExamenRepository,
            VerificationRepository verificationRepository, DispatchRepository dispatchRepository,
            ReceptionRepository receptionRepository, DemandeRetraitRepository demandeRetraitRepository,
            LettreRenvoiRepository lettreRenvoiRepository, SessionUtilisateurRepository sessionRepository,
            IndicateurCtrlRepository indicateurCtrlRepository) {
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

    public ControleurDto create(ControleurDto dto) {
        Controleur entity = ControleurMapper.toEntity(dto);
        return ControleurMapper.toDto(repository.save(entity));
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
        compteRepository.deleteAll(compteRepository.findByRefActeurAndTypeActeur(id, TypeActeur.CONTROLEUR.name()));
        repository.deleteById(id);
    }
}
