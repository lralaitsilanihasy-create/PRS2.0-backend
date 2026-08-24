package cnm.prs.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.IndicateurCtrlDto;
import cnm.prs.entity.IndicateurCtrl;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.IndicateurCtrlMapper;
import cnm.prs.repository.IndicateurCtrlRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;
import cnm.prs.enums.TypeActeur;

/**
 * Logique métier pour {@link IndicateurCtrl}.
 *
 * <p>⚠️ Correction de périmètre — la performance individuelle d'un contrôleur (nb examens, nb conformes,
 * délai moyen, nb observations) est une donnée d'<strong>évaluation</strong>. Le §3.8 (Module 09) la
 * destine au Président : « performance mensuelle de chaque membre de toutes les commissions ». Le
 * périmètre est donc <strong>nominatif</strong>, pas géographique : Président/Administrateur voient tout,
 * un contrôleur ne voit que <strong>les siens</strong> ({@code IM_CONTROLEUR} = son propre matricule),
 * la PRMP ne voit rien. Auparavant ce service faisait {@code repository.findAll()} nu : n'importe quel
 * porteur de jeton — PRMP comprise — lisait les notes de tous les contrôleurs du CNM.</p>
 */
@Service
@Transactional
public class IndicateurCtrlService {

    private final IndicateurCtrlRepository repository;

    public IndicateurCtrlService(IndicateurCtrlRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<IndicateurCtrlDto> findAll() {
        if (Visibilite.voitTout()) {
            return repository.findAll().stream().map(IndicateurCtrlMapper::toDto).toList();
        }
        return matriculeAppelant()
                .map(im -> repository.findByImControleur(im).stream().map(IndicateurCtrlMapper::toDto).toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public IndicateurCtrlDto findById(Integer id) {
        IndicateurCtrl entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IndicateurCtrl introuvable : " + id));
        if (!Visibilite.voitTout()
                && !matriculeAppelant().map(im -> im.equals(entity.getImControleur())).orElse(false)) {
            throw new AccessDeniedException(
                    "Indicateur d'un autre contrôleur : hors de votre périmètre de visibilité (§1).");
        }
        return IndicateurCtrlMapper.toDto(entity);
    }

    public IndicateurCtrlDto create(IndicateurCtrlDto dto) {
        IndicateurCtrl entity = IndicateurCtrlMapper.toEntity(dto);
        return IndicateurCtrlMapper.toDto(repository.save(entity));
    }

    public IndicateurCtrlDto update(Integer id, IndicateurCtrlDto dto) {
        IndicateurCtrl existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IndicateurCtrl introuvable : " + id));
        existing.setImControleur(dto.getImControleur());
        existing.setPeriode(dto.getPeriode());
        existing.setNbExamens(dto.getNbExamens());
        existing.setNbConformes(dto.getNbConformes());
        existing.setDelaiMoyenExamen(dto.getDelaiMoyenExamen());
        existing.setNbObsEmises(dto.getNbObsEmises());
        return IndicateurCtrlMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("IndicateurCtrl introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /**
     * Matricule du contrôleur authentifié (claim {@code ref}), ou vide si l'appelant n'est pas un
     * contrôleur — la PRMP porte elle aussi un {@code ref}, mais c'est un ID_PRMP : sans le contrôle
     * sur {@code acteurType}, une PRMP dont l'identifiant coïnciderait avec un matricule tomberait
     * sur les indicateurs d'un contrôleur homonyme.
     */
    private java.util.Optional<String> matriculeAppelant() {
        if (CurrentUser.acteurType().filter(TypeActeur.CONTROLEUR.name()::equals).isEmpty()) {
            return java.util.Optional.empty();
        }
        return CurrentUser.ref().filter(s -> !s.isBlank());
    }
}
