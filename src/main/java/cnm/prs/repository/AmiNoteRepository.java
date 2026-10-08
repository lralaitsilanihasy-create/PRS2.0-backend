package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AmiNote;

public interface AmiNoteRepository extends JpaRepository<AmiNote, Long> {
    List<AmiNote> findByIdDmcOrderByIdAsc(Long idDmc);

    Optional<AmiNote> findByIdExpressionAndCodeCritere(String idExpression, String codeCritere);
}
