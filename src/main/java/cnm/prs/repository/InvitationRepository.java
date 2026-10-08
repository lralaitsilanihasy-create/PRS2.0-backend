package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.Invitation;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    List<Invitation> findByIdDmcOrderByRangAsc(Long idDmc);

    void deleteByIdDmc(Long idDmc);

    boolean existsByIdDmc(Long idDmc);

    List<Invitation> findByIdCandidatOrEmailIgnoreCase(String idCandidat, String email);
}
