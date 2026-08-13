package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ActionDossierDto;
import cnm.prs.entity.ActionDossier;
import cnm.prs.entity.Dossier;
import cnm.prs.repository.ActionDossierRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — écriture et lecture du <strong>journal des actions</strong>
 * d'un dossier ({@code t_action_dossier}).
 *
 * <p>Chaque action de traitement y est consignée avec l'<strong>opérateur courant</strong> : la PRMP en
 * fonction à la date de l'action et le mandat sous lequel elle agit. L'attribution du dossier, elle,
 * ne bouge pas — c'est précisément la séparation que ce journal rend visible.</p>
 */
@Service
@Transactional(readOnly = true)
public class JournalDossierService {

    /** Types d'action consignés — un vocabulaire fermé, pour que le front puisse les libeller. */
    public static final String CREATION = "CREATION";
    public static final String SOUMISSION = "SOUMISSION";
    public static final String RESOUMISSION = "RESOUMISSION";
    public static final String TRANSMISSION_COMPLEMENTS = "TRANSMISSION_COMPLEMENTS";
    public static final String TRANSMISSION_COMPLEMENTS_DEPOT = "TRANSMISSION_COMPLEMENTS_DEPOT";
    public static final String SUPPRESSION = "SUPPRESSION";
    public static final String MISE_A_JOUR = "MISE_A_JOUR";

    private final ActionDossierRepository repository;
    private final PrmpRepository prmpRepository;
    private final MandatService mandatService;

    public JournalDossierService(ActionDossierRepository repository, PrmpRepository prmpRepository,
            MandatService mandatService) {
        this.repository = repository;
        this.prmpRepository = prmpRepository;
        this.mandatService = mandatService;
    }

    /**
     * Consigne une action sur un dossier. L'opérateur est lu sur le jeton courant ({@code ref} = PRMP,
     * ou sa PRMP de tutelle pour un agent UGPM) ; l'auteur réel reste le login.
     *
     * <p>L'écriture rejoint la transaction de l'action qu'elle décrit — délibérément : un journal qui
     * survivrait au rollback de son action raconterait un événement qui n'a pas eu lieu.</p>
     */
    @Transactional
    public void tracer(Integer idDossier, String typeAction, String detail) {
        String operateur = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        ActionDossier action = new ActionDossier();
        action.setIdDossier(idDossier);
        action.setDateAction(LocalDateTime.now());
        action.setTypeAction(typeAction);
        action.setIdPrmpOperateur(operateur);
        action.setNomOperateur(nomOperateur(operateur));
        action.setAuteur(CurrentUser.login().orElse(operateur));
        action.setIdMandatOperateur(operateur == null ? null : mandatService.idMandatCourant(operateur));
        action.setDetail(tronquer(detail, 500));
        repository.save(action);
    }

    /** Variante prenant le dossier, pour les appels qui l'ont déjà chargé. */
    @Transactional
    public void tracer(Dossier dossier, String typeAction, String detail) {
        tracer(dossier.getIdDossier(), typeAction, detail);
    }

    /** Journal d'un dossier, chronologique. Le contrôle de visibilité est fait par l'appelant. */
    public List<ActionDossierDto> journal(Integer idDossier) {
        return repository.findByIdDossierOrderByDateActionAscIdActionAsc(idDossier).stream()
                .map(JournalDossierService::toDto).toList();
    }

    /** Supprime le journal d'un dossier (cascade de la suppression d'un brouillon). */
    @Transactional
    public void purger(Integer idDossier) {
        repository.deleteByIdDossier(idDossier);
    }

    private String nomOperateur(String idPrmp) {
        if (idPrmp == null) {
            return null;
        }
        return prmpRepository.findById(idPrmp).map(p -> {
            String nom = ((p.getPrenomsPrmp() == null ? "" : p.getPrenomsPrmp()) + " "
                    + (p.getNomPrmp() == null ? "" : p.getNomPrmp())).trim();
            return nom.isBlank() ? idPrmp : nom;
        }).orElse(idPrmp);
    }

    private static String tronquer(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static ActionDossierDto toDto(ActionDossier entity) {
        ActionDossierDto dto = new ActionDossierDto();
        dto.setIdAction(entity.getIdAction());
        dto.setIdDossier(entity.getIdDossier());
        dto.setDateAction(entity.getDateAction());
        dto.setTypeAction(entity.getTypeAction());
        dto.setIdPrmpOperateur(entity.getIdPrmpOperateur());
        dto.setNomOperateur(entity.getNomOperateur());
        dto.setAuteur(entity.getAuteur());
        dto.setIdMandatOperateur(entity.getIdMandatOperateur());
        dto.setDetail(entity.getDetail());
        return dto;
    }
}
