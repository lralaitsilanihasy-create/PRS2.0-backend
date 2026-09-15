package cnm.prs.service;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ObservationControleDto;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.ObservationControle;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.ObservationControleMapper;
import cnm.prs.repository.ExamenDetailRepository;
import cnm.prs.repository.ObservationControleRepository;
import cnm.prs.security.Visibilite;

/**
 * Logique métier pour {@link ObservationControle} (lignes « AU LIEU DE / LIRE » des points de contrôle).
 *
 * <p>⚠️ Audit 2026-08-27, constat C2 — §1/§3.1 : {@code findByDetail} servait les observations
 * <strong>internes</strong> de la commission à tout authentifié, toutes localités confondues et
 * pendant la navette. La lecture est désormais bornée par {@link Visibilite}, sur la même chaîne que
 * le détail d'examen parent — hors localité (et pour la PRMP/UGPM) : liste vide.</p>
 *
 * <p>⚠️ Revue du 2026-09-14 — l'<strong>écriture</strong> (POST/PUT/DELETE) n'avait, elle, aucune garde
 * au-delà du profil : tout Membre, de n'importe quelle localité, écrivait sur le résultat d'examen d'un
 * autre, y compris après la signature du PV. Une ligne d'observation est un morceau du résultat
 * ({@code t_examen_detail}) auquel elle appartient : elle obéit désormais aux gardes de
 * {@code /api/examen-details}, portées par {@link ExamenGarde} (source unique, aucune règle recopiée).</p>
 */
@Service
@Transactional
public class ObservationControleService {

    private final ObservationControleRepository repository;
    /** ⚠️ Revue 2026-09-14 — résout l'examen du résultat auquel appartient la ligne. */
    private final ExamenDetailRepository examenDetailRepository;
    /** ⚠️ Revue 2026-09-14 — gardes d'écriture partagées avec {@code ExamenDetailService}. */
    private final ExamenGarde garde;
    /** ⚠️ V30 (2026-09-14) — cellule visée : le même validateur que {@code /api/examen-details}. */
    private final ObservationCibleValidateur cibleValidateur;

    public ObservationControleService(ObservationControleRepository repository,
            ExamenDetailRepository examenDetailRepository, ExamenGarde garde,
            ObservationCibleValidateur cibleValidateur) {
        this.repository = repository;
        this.examenDetailRepository = examenDetailRepository;
        this.garde = garde;
        this.cibleValidateur = cibleValidateur;
    }

    /** ⚠️ C2 — lignes du point de contrôle, bornées au périmètre (§1) : vide hors localité / pour la PRMP. */
    @Transactional(readOnly = true)
    public List<ObservationControleDto> findByDetail(Integer idDetail) {
        return Visibilite.filtrer(
                        () -> repository.findByIdDetailOrderByOrdreAsc(idDetail),
                        loc -> repository.findByIdDetailEtLocalite(idDetail, loc))
                .stream().map(ObservationControleMapper::toDto).toList();
    }

    public ObservationControleDto create(ObservationControleDto dto) {
        exigerEcritureDesResultats(dto.getIdDetail());   // ⚠️ revue 2026-09-14 — 403/409 avant tout 400
        cibleValidateur.validerLigne(dto);               // ⚠️ V30 — cellule visée, après les gardes
        ObservationControle entity = ObservationControleMapper.toEntity(dto);
        entity.setIdObservation(null);   // PK auto (IDENTITY) ; tout id fourni est ignoré
        return ObservationControleMapper.toDto(repository.save(entity));
    }

    public ObservationControleDto update(Integer id, ObservationControleDto dto) {
        ObservationControle existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Observation introuvable : " + id));
        // ⚠️ Revue 2026-09-14 — comme le PUT d'un détail d'examen : garde sur le résultat EN PLACE et sur
        // le résultat VISÉ par le corps (un PUT peut déplacer la ligne vers un autre point de contrôle).
        exigerEcritureDesResultats(existing.getIdDetail(), dto.getIdDetail());
        if (!java.util.Objects.equals(existing.getIdDetail(), dto.getIdDetail())) {
            exigerUneLigneRestanteApresRetrait(existing.getIdDetail());   // ⚠️ revue 2026-09-14
        }
        // ⚠️ V30 — cellule visée, après les gardes : même ordre que PUT /api/examen-details (identité, verrou,
        // règle « non conforme », puis validateur de cellule).
        cibleValidateur.validerLigne(dto);
        existing.setIdDetail(dto.getIdDetail());
        existing.setAuLieuDe(dto.getAuLieuDe());
        existing.setLire(dto.getLire());
        existing.setOrdre(dto.getOrdre());
        // ⚠️ V30 — un PUT porte l'état complet de la ligne : une cible absente du corps est effacée.
        existing.setChampCible(dto.getChamp());
        existing.setIdMarcheCible(dto.getIdMarcheCible());
        existing.setIdBenefCible(dto.getIdBenefCible());
        return ObservationControleMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        ObservationControle existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Observation introuvable : " + id));
        exigerEcritureDesResultats(existing.getIdDetail());   // ⚠️ revue 2026-09-14
        exigerUneLigneRestanteApresRetrait(existing.getIdDetail());
        repository.delete(existing);
    }

    /**
     * ⚠️ Revue 2026-09-14 — retirer une ligne (DELETE, ou PUT qui la déplace) ne doit pas laisser un point
     * <strong>non conforme</strong> sans observation : même règle, même 400 et même message que
     * {@code /api/examen-details} ({@link ExamenGarde#exigerObservationSiNonConforme}), vérifiés après les
     * gardes d'identité et de verrou, comme là-bas.
     *
     * <p>L'état {@code conforme} est lu en base au moment de la requête. Un point repassé conforme par
     * {@code /api/examen-details} peut donc perdre sa dernière ligne ensuite ; et ce PUT, qui remplace les
     * lignes du point, n'a de toute façon pas besoin de cette route pour les retirer.</p>
     */
    private void exigerUneLigneRestanteApresRetrait(Integer idDetail) {
        Boolean conforme = examenDetailRepository.findById(idDetail).map(ExamenDetail::getConforme).orElse(null);
        garde.exigerObservationSiNonConforme(conforme, repository.countByIdDetail(idDetail) <= 1);
    }

    /**
     * ⚠️ Revue 2026-09-14 — gardes d'écriture d'un résultat d'examen, appliquées au(x) résultat(s)
     * ({@code t_examen_detail}) auquel la ligne appartient : <strong>exactement</strong> celles de
     * {@code ExamenDetailService} (création et mise à jour), dans le même ordre, par les mêmes méthodes de
     * {@link ExamenGarde} —
     * <ol>
     *   <li>{@link ExamenGarde#exigerAttributaire} : localité du circuit, puis Membre attributaire du dispatch
     *       (CC/Président par délégation admis dans leur localité) → 403 ;</li>
     *   <li>{@link ExamenGarde#exigerExamenModifiable} : dossier {@code DISPATCHE}, {@code EXAMINE} ou
     *       {@code A_REEXAMINER}, écriture refusée dès {@code PV_SIGNE} → 409.</li>
     * </ol>
     * Toutes les identités sont vérifiées avant tout verrou, comme au PUT d'un détail d'examen. Un résultat
     * introuvable donne un examen {@code null}, que les gardes traitent comme pour un {@code idExamen}
     * inconnu sur {@code /api/examen-details}.
     */
    private void exigerEcritureDesResultats(Integer... idsDetail) {
        List<Integer> examens = Stream.of(idsDetail).map(this::examenDuResultat).distinct().toList();
        examens.forEach(garde::exigerAttributaire);
        examens.forEach(garde::exigerExamenModifiable);
    }

    private Integer examenDuResultat(Integer idDetail) {
        return idDetail == null ? null
                : examenDetailRepository.findById(idDetail).map(ExamenDetail::getIdExamen).orElse(null);
    }
}
