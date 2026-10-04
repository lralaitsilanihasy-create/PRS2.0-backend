package cnm.prs.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.EntrepriseCandidatDto;
import cnm.prs.entity.ExclusionArmp;
import cnm.prs.entity.ExclusionArmpJournal;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ExclusionArmpJournalRepository;
import cnm.prs.repository.ExclusionArmpRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1b, §B5) — le <strong>répertoire des entreprises exclues
 * par l'ARMP</strong>, tenu par l'Administrateur. Une exclusion ne se supprime pas : pour corriger une erreur, on la
 * modifie ; pour la lever avant terme, on avance sa date de fin. Chaque création et chaque modification sont au journal,
 * avec les anciennes et les nouvelles valeurs. Le rapprochement se fait par le NIF normalisé, sur le répertoire du jour.
 */
@Service
@Transactional
public class ExclusionArmpService {

    private final ExclusionArmpRepository repository;
    private final ExclusionArmpJournalRepository journal;
    private final ObjectMapper json;
    private final Clock horloge;

    public ExclusionArmpService(ExclusionArmpRepository repository, ExclusionArmpJournalRepository journal, ObjectMapper json,
            Clock horloge) {
        this.repository = repository;
        this.journal = journal;
        this.json = json;
        this.horloge = horloge;
    }

    @Transactional(readOnly = true)
    public List<EntrepriseCandidatDto.Exclusion> lister() {
        return repository.findAllByOrderByDateDebutDescIdExclusionDesc().stream().map(this::dto).toList();
    }

    public EntrepriseCandidatDto.Exclusion creer(EntrepriseCandidatDto.SaisieExclusion s) {
        valider(s);
        ExclusionArmp e = new ExclusionArmp(null, NormalisationCandidat.identifiant(s.nif()), s.raisonSociale().trim(),
                s.motif().trim(), s.referenceDecision().trim(), s.dateDebut(), s.dateFin(), LocalDateTime.now(horloge));
        e = repository.save(e);
        tracer(e.getIdExclusion(), null, valeurs(e));
        return dto(e);
    }

    public EntrepriseCandidatDto.Exclusion modifier(Integer id, EntrepriseCandidatDto.SaisieExclusion s) {
        ExclusionArmp e = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exclusion " + id + " introuvable."));
        valider(s);
        Map<String, Object> anciennes = valeurs(e);
        e.setNif(NormalisationCandidat.identifiant(s.nif()));
        e.setRaisonSociale(s.raisonSociale().trim());
        e.setMotif(s.motif().trim());
        e.setReferenceDecision(s.referenceDecision().trim());
        e.setDateDebut(s.dateDebut());
        e.setDateFin(s.dateFin());
        e = repository.save(e);
        Map<String, Object> nouvelles = valeurs(e);
        if (!nouvelles.equals(anciennes)) {
            tracer(e.getIdExclusion(), anciennes, nouvelles);
        }
        return dto(e);
    }

    /** L'exclusion en cours pour ce NIF au jour {@code jour} (la plus récente), s'il y en a une. */
    @Transactional(readOnly = true)
    public Optional<ExclusionArmp> enCours(String nif, LocalDate jour) {
        String n = NormalisationCandidat.identifiant(nif);
        if (n == null) {
            return Optional.empty();
        }
        return repository.findByNifOrderByDateDebutDesc(n).stream().filter(e -> estEnCours(e, jour)).findFirst();
    }

    /** L'exclusion en cours pour ce NIF aujourd'hui, en DTO (sans journal), ou {@code null}. */
    @Transactional(readOnly = true)
    public EntrepriseCandidatDto.Exclusion enCoursAujourdhui(String nif) {
        return enCours(nif, LocalDate.now(horloge)).map(e -> new EntrepriseCandidatDto.Exclusion(e.getIdExclusion(), e.getNif(),
                e.getRaisonSociale(), e.getMotif(), e.getReferenceDecision(), e.getDateDebut(), e.getDateFin(), true, List.of()))
                .orElse(null);
    }

    static boolean estEnCours(ExclusionArmp e, LocalDate jour) {
        return !e.getDateDebut().isAfter(jour) && (e.getDateFin() == null || !e.getDateFin().isBefore(jour));
    }

    private void valider(EntrepriseCandidatDto.SaisieExclusion s) {
        if (s.dateFin() != null && s.dateFin().isBefore(s.dateDebut())) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError("dateFin",
                    "La date de fin ne précède pas la date de début.")));
        }
    }

    private Map<String, Object> valeurs(ExclusionArmp e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nif", e.getNif());
        m.put("raisonSociale", e.getRaisonSociale());
        m.put("motif", e.getMotif());
        m.put("referenceDecision", e.getReferenceDecision());
        m.put("dateDebut", e.getDateDebut().toString());
        m.put("dateFin", e.getDateFin() == null ? null : e.getDateFin().toString());
        return m;
    }

    private void tracer(Integer id, Map<String, Object> anciennes, Map<String, Object> nouvelles) {
        journal.save(new ExclusionArmpJournal(null, id, LocalDateTime.now(horloge),
                CurrentUser.ref().or(CurrentUser::login).orElse(null),
                anciennes == null ? null : json.writeValueAsString(anciennes), json.writeValueAsString(nouvelles)));
    }

    private EntrepriseCandidatDto.Exclusion dto(ExclusionArmp e) {
        List<EntrepriseCandidatDto.LigneJournal> lignes = journal.findByIdExclusionOrderByDateActionAscIdJournalAsc(e.getIdExclusion())
                .stream().map(j -> new EntrepriseCandidatDto.LigneJournal(j.getDateAction(), j.getActeur(),
                        j.getAnciennes() == null ? null : lire(j.getAnciennes()), lire(j.getNouvelles()))).toList();
        return new EntrepriseCandidatDto.Exclusion(e.getIdExclusion(), e.getNif(), e.getRaisonSociale(), e.getMotif(),
                e.getReferenceDecision(), e.getDateDebut(), e.getDateFin(), estEnCours(e, LocalDate.now(horloge)), lignes);
    }

    private Map<String, Object> lire(String s) {
        return json.readValue(s, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }
}
