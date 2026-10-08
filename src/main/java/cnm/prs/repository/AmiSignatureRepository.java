package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.AmiSignature;

public interface AmiSignatureRepository extends JpaRepository<AmiSignature, Long> {
    List<AmiSignature> findByIdDmcOrderByDateAscIdAsc(Long idDmc);

    boolean existsByIdDmcAndIm(Long idDmc, String im);
}
