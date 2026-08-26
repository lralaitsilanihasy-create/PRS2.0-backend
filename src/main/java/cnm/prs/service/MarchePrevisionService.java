package cnm.prs.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.MarchePrevisionDto;
import cnm.prs.entity.Capm;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.MarchePrevisionMapper;
import cnm.prs.repository.CapmRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.security.PerimetreDossier;

/**
 * Logique métier pour {@link MarchePrevision} (dates prévisionnelles des marchés).
 *
 * <p>⚠️ LOT 3a (2026-08-26) — §1/§3.1 : CRUD auparavant sans aucune garde. Les lectures sont bornées
 * au périmètre du dossier parent ({@code ID_DETAIL → t_marche.ID_DOSSIER}).</p>
 *
 * <p><strong>Où est posée la garde d'écriture, et pourquoi ici plutôt que dans {@link #create}</strong> :
 * {@code create(...)} est aussi appelée <strong>en interne</strong> par {@link SaisieService} (saisie
 * et mise à jour d'un PPM, qui a déjà passé ses propres gardes et travaille sur un dossier dont le
 * statut n'est pas nécessairement celui attendu d'une édition unitaire). Y poser la garde ferait
 * échouer le flux de saisie. Les points d'entrée <strong>publics</strong> du contrôleur sont donc des
 * méthodes distinctes — {@link #creerAvecGarde}, {@link #modifierAvecGarde},
 * {@link #supprimerAvecGarde} — que seul {@code MarchePrevisionController} appelle.</p>
 */
@Service
@Transactional
public class MarchePrevisionService {

    private final MarchePrevisionRepository repository;
    private final CapmRepository capmRepository;
    private final PerimetreDossier perimetre;
    private final EnfantDossierGarde garde;

    public MarchePrevisionService(MarchePrevisionRepository repository, CapmRepository capmRepository,
            PerimetreDossier perimetre, EnfantDossierGarde garde) {
        this.repository = repository;
        this.capmRepository = capmRepository;
        this.perimetre = perimetre;
        this.garde = garde;
    }

    /** Prévisions du périmètre de l'appelant (Président/Admin : toutes ; CC : sa localité ; PRMP : ses dossiers). */
    @Transactional(readOnly = true)
    public List<MarchePrevisionDto> findAll() {
        return perimetre.filtrer(repository::findAll, repository::findParDossiers)
                .stream().map(MarchePrevisionMapper::toDto).map(this::peuplerOrdre).toList();
    }

    @Transactional(readOnly = true)
    public List<MarchePrevisionDto> findByMarche(Integer idDetail) {
        // Triées par l'ordre du processus (t_capm.ORDRE) ASC.
        List<MarchePrevision> lignes = repository.findByMarcheOrdonne(idDetail);
        if (lignes.isEmpty()) {
            return List.of();
        }
        perimetre.controler(repository.findIdDossier(lignes.get(0).getIdPrevision()).orElse(null));
        return lignes.stream().map(MarchePrevisionMapper::toDto).map(this::peuplerOrdre).toList();
    }

    @Transactional(readOnly = true)
    public MarchePrevisionDto findById(Integer id) {
        MarchePrevision entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Date prévisionnelle introuvable : " + id));
        perimetre.controler(repository.findIdDossier(id).orElse(null));
        return peuplerOrdre(MarchePrevisionMapper.toDto(entity));
    }

    /**
     * Création <strong>interne</strong> (façade de saisie) : aucune garde de périmètre — l'appelant a
     * déjà appliqué la sienne. Ne pas exposer directement à un contrôleur.
     */
    public MarchePrevisionDto create(MarchePrevisionDto dto) {
        validerChronologie(dto, null);
        MarchePrevision entity = MarchePrevisionMapper.toEntity(dto);
        entity.setIdPrevision(prochaineCle(dto.getIdPrevision()));
        return peuplerOrdre(MarchePrevisionMapper.toDto(repository.save(entity)));
    }

    /** ⚠️ LOT 3a — création par l'API : garde d'écriture (403 hors périmètre / 409 hors brouillon). */
    public MarchePrevisionDto creerAvecGarde(MarchePrevisionDto dto) {
        garde.exigerEcritureSurMarche(dto.getIdDetail());
        return create(dto);
    }

    /** ⚠️ LOT 3a — mise à jour par l'API : garde sur le marché actuel ET sur le marché cible. */
    public MarchePrevisionDto modifierAvecGarde(Integer id, MarchePrevisionDto dto) {
        MarchePrevision existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Date prévisionnelle introuvable : " + id));
        garde.exigerEcritureSurMarche(existing.getIdDetail());
        garde.exigerEcritureSurMarche(dto.getIdDetail());
        return update(id, dto);
    }

    /** ⚠️ LOT 3a — suppression par l'API : garde d'écriture sur le marché porteur. */
    public void supprimerAvecGarde(Integer id) {
        MarchePrevision existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Date prévisionnelle introuvable : " + id));
        garde.exigerEcritureSurMarche(existing.getIdDetail());
        delete(id);
    }

    /** Renseigne l'{@code ordre} (lecture seule) depuis le référentiel {@code t_capm}. */
    private MarchePrevisionDto peuplerOrdre(MarchePrevisionDto dto) {
        if (dto != null && dto.getIdCapm() != null) {
            capmRepository.findById(dto.getIdCapm()).ifPresent(c -> dto.setOrdre(c.getOrdre()));
        }
        return dto;
    }

    /**
     * ⚠️ LOT 3a (2026-08-26) — PK anti-collision, même motif que {@code LotService} : la liste rendue
     * au front est désormais scopée, son {@code max} n'est plus le max global. PK proposée conservée
     * si libre, sinon réallouée par le serveur plutôt que d'écraser la ligne d'autrui.
     *
     * <p>⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant. La branche
     * « réallouer » passait par {@code max + 1}, qui n'est pas atomique : deux créations concurrentes
     * y lisaient le même maximum. Elle passe désormais par la séquence {@code seq_marche_prevision}
     * (migration {@code V5}), via {@link ClePrimaire#reallouer}.</p>
     */
    private Integer prochaineCle(Integer idPropose) {
        return ClePrimaire.reallouer(idPropose, repository::existsByIdPrevision,
                repository::nextIdPrevision);
    }

    /**
     * Mise à jour <strong>interne</strong> (sans garde de périmètre) — voir {@link #modifierAvecGarde}
     * pour le point d'entrée de l'API.
     */
    public MarchePrevisionDto update(Integer id, MarchePrevisionDto dto) {
        MarchePrevision existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Date prévisionnelle introuvable : " + id));
        validerChronologie(dto, id);   // la ligne éditée remplace l'existante dans la séquence
        existing.setIdDetail(dto.getIdDetail());
        existing.setIdCapm(dto.getIdCapm());
        existing.setDateDebut(dto.getDateDebut());
        existing.setDateFin(dto.getDateFin());
        return peuplerOrdre(MarchePrevisionMapper.toDto(repository.save(existing)));
    }

    /**
     * ⚠️ Règle ajoutée — valide la cohérence chronologique des processus du marché après ajout/édition
     * de cette ligne : la prévision (dto) + les autres lignes du marché (hors {@code idAExclure}),
     * triées par ordre CAPM. Violation → 400 (champ {@code dateDebut}/{@code dateFin}).
     */
    private void validerChronologie(MarchePrevisionDto dto, Integer idAExclure) {
        List<ProcessusChronologie.Proc> procs = new ArrayList<>();
        procs.add(procDe(dto.getIdCapm(), dto.getDateDebut(), dto.getDateFin()));
        for (MarchePrevision sib : repository.findByIdDetail(dto.getIdDetail())) {
            if (idAExclure != null && idAExclure.equals(sib.getIdPrevision())) {
                continue;
            }
            procs.add(procDe(sib.getIdCapm(), sib.getDateDebut(), sib.getDateFin()));
        }
        ErrorResponse.FieldError violation = ProcessusChronologie.premiereViolation(procs);
        if (violation != null) {
            throw new ChampsInvalidesException(List.of(violation));
        }
    }

    /** Construit un {@code Proc} (chemin = nom de champ seul) en résolvant ordre/libellé via {@code t_capm}. */
    private ProcessusChronologie.Proc procDe(Integer idCapm, LocalDate dateDebut, LocalDate dateFin) {
        Capm c = idCapm == null ? null : capmRepository.findById(idCapm).orElse(null);
        int ordre = (c == null || c.getOrdre() == null) ? 0 : c.getOrdre();
        String libelle = c == null ? String.valueOf(idCapm) : c.getLibelleProcessus();
        return new ProcessusChronologie.Proc("", ordre, libelle, dateDebut, dateFin);
    }

    /**
     * Suppression <strong>interne</strong> (sans garde de périmètre) — voir {@link #supprimerAvecGarde}
     * pour le point d'entrée de l'API.
     */
    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Date prévisionnelle introuvable : " + id);
        }
        repository.deleteById(id);
    }
}
