package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.EcheanceDto;
import cnm.prs.entity.Echeance;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.EcheanceMapper;
import cnm.prs.repository.EcheanceRepository;
import cnm.prs.security.PerimetreDossier;

/**
 * Logique métier pour {@link Echeance}.
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1 (Module 04 « Calendrier des jalons [Lecture] ») : CRUD
 * auparavant sans aucune garde. La <strong>lecture</strong> est bornée au périmètre du dossier parent
 * ({@code ID_DETAIL → t_marche.ID_DOSSIER}) — la PRMP consulte le calendrier de ses propres marchés,
 * les contrôleurs celui de leur localité. L'<strong>écriture</strong> générique est réservée à
 * l'Administrateur ({@code @PreAuthorize} sur {@code EcheanceController}) : les jalons naissent des
 * flux internes (alertes J-7 / J-1), pas d'une saisie d'utilisateur.</p>
 */
@Service
@Transactional
public class EcheanceService {

    private final EcheanceRepository repository;
    private final PerimetreDossier perimetre;

    public EcheanceService(EcheanceRepository repository, PerimetreDossier perimetre) {
        this.repository = repository;
        this.perimetre = perimetre;
    }

    @Transactional(readOnly = true)
    public List<EcheanceDto> findAll() {
        return perimetre.filtrer(repository::findAll, repository::findParDossiers)
                .stream().map(EcheanceMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public EcheanceDto findById(Integer id) {
        Echeance entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Echeance introuvable : " + id));
        perimetre.controler(repository.findIdDossier(id).orElse(null));
        return EcheanceMapper.toDto(entity);
    }

    public EcheanceDto create(EcheanceDto dto) {
        Echeance entity = EcheanceMapper.toEntity(dto);
        return EcheanceMapper.toDto(repository.save(entity));
    }

    public EcheanceDto update(Integer id, EcheanceDto dto) {
        Echeance existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Echeance introuvable : " + id));
        existing.setIdDetail(dto.getIdDetail());
        existing.setTypeJalon(dto.getTypeJalon());
        existing.setDatePrevue(dto.getDatePrevue());
        existing.setDateReelle(dto.getDateReelle());
        existing.setStatutJalon(dto.getStatutJalon());
        existing.setEcartJours(dto.getEcartJours());
        existing.setAlerteEnvoyee(dto.getAlerteEnvoyee());
        return EcheanceMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Echeance introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
