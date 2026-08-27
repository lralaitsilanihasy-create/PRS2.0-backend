package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.TransmissionSigmp;

public interface TransmissionSigmpRepository extends JpaRepository<TransmissionSigmp, Integer> {

    List<TransmissionSigmp> findByIdDossier(Integer idDossier);

    /**
     * ⚠️ Audit 2026-08-27 (§3.1 du rapport) — transmissions d'un ensemble de dossiers. L'ensemble est
     * fourni par {@code DossierRepository.findIdsVisiblesParLocalite} : le périmètre des transmissions
     * reste ainsi défini au même endroit que celui de la liste des dossiers, jamais redéfini ici.
     */
    List<TransmissionSigmp> findByIdDossierIn(List<Integer> idsDossiers);
}
