package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AmiDeclaration;

public interface AmiDeclarationRepository extends JpaRepository<AmiDeclaration, Long> {
    List<AmiDeclaration> findByIdDmcOrderBySigneeLeAscIdAsc(Long idDmc);

    Optional<AmiDeclaration> findByIdDmcAndIm(Long idDmc, String im);
}
