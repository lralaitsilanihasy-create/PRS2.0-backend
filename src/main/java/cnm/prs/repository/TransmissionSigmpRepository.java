package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.TransmissionSigmp;

public interface TransmissionSigmpRepository extends JpaRepository<TransmissionSigmp, Integer> {

    List<TransmissionSigmp> findByIdDossier(Integer idDossier);

    /**
     * ⚠️ Rattachements (2026-09-01) — matricule du Vérificateur ayant EFFECTIVEMENT transmis à SIGMP,
     * le plus récent d'abord. C'est lui, et non le vérificateur nominalement rattaché, qui détermine
     * l'Assistant cible pour l'archivage : si un suppléant a validé, c'est sa chaîne qui archive —
     * sans quoi on notifierait l'assistant d'un vérificateur qui n'a rien fait.
     */
    @org.springframework.data.jpa.repository.Query("""
            select t.imVerificateur from TransmissionSigmp t
            where t.idDossier = :idDossier and t.imVerificateur is not null
            order by t.idTransmission desc
            """)
    List<String> findImVerificateurParDossier(
            @org.springframework.data.repository.query.Param("idDossier") Integer idDossier);

    /**
     * ⚠️ Audit 2026-08-27 (§3.1 du rapport) — transmissions d'un ensemble de dossiers. L'ensemble est
     * fourni par {@code DossierRepository.findIdsVisiblesParLocalite} : le périmètre des transmissions
     * reste ainsi défini au même endroit que celui de la liste des dossiers, jamais redéfini ici.
     */
    List<TransmissionSigmp> findByIdDossierIn(List<Integer> idsDossiers);
}
