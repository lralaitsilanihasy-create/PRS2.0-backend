package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ReceptionSignature;

/** ⚠️ V127 (lot MS-4) — les signatures d'un PV de réception. */
public interface ReceptionSignatureRepository extends JpaRepository<ReceptionSignature, ReceptionSignature.Cle> {

    List<ReceptionSignature> findByIdReceptionOrderBySigneLeAsc(Long idReception);

    void deleteByIdReception(Long idReception);
}
