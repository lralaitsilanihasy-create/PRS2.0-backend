package cnm.prs.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Offre;

/** ⚠️ V68 (2026-10-04, soumission en ligne, lot 3) — les offres déposées en ligne. */
@Repository
public interface OffreRepository extends JpaRepository<Offre, String> {

    List<Offre> findByIdCandidatOrderByDateCreationDesc(String idCandidat);

    List<Offre> findByIdDmcOrderByNumeroAscDateCreationAsc(Long idDmc);

    Optional<Offre> findFirstByIdDmcAndIdEntrepriseAndLotAndEtat(Long idDmc, Integer idEntreprise, Integer lot, String etat);

    Optional<Offre> findFirstByIdDmcAndIdEntrepriseAndLotIsNullAndEtat(Long idDmc, Integer idEntreprise, String etat);

    long countByIdDmcAndEtat(Long idDmc, String etat);

    long countByIdDmcAndEtatNot(Long idDmc, String etat);

    /** ⚠️ 2026-10-06 (compteurs, §B2) — les offres déposées (ou écartées) par DMC, en une requête : {@code [idDmc, nombre]}. */
    @org.springframework.data.jpa.repository.Query("select o.idDmc, count(o) from Offre o where o.etat in ('DEPOSEE', 'ECARTEE') group by o.idDmc")
    java.util.List<Object[]> compterDeposeesParDmc();

    List<Offre> findByEtat(String etat);

    List<Offre> findByEtatAndDateCreationBefore(String etat, LocalDateTime limite);
}
