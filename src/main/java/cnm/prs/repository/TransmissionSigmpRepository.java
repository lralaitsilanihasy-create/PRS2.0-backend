package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.TransmissionSigmp;

public interface TransmissionSigmpRepository extends JpaRepository<TransmissionSigmp, Integer> {

    List<TransmissionSigmp> findByIdDossier(Integer idDossier);
}
