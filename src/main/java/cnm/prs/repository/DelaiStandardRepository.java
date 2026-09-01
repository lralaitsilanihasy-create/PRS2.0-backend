package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import cnm.prs.entity.DelaiStandard;

/** Referentiel administrable des delais standards par etape — chronometrage des delais, 2026-09-01. */
public interface DelaiStandardRepository extends JpaRepository<DelaiStandard, String> {

    /**
     * ⚠️ Projection SCALAIRE, et non {@code findAll()} : la date previsionnelle est calculee pour CHAQUE
     * dossier d'une liste, et charger les entites du referentiel a chaque fois gonflait le compteur
     * d'entites de Hibernate au point de faire tomber le contrat de pagination (lot D §3). Une paire
     * (etape, delai) ne charge rien.
     */
    @Query("select d.etape, d.delaiJours from DelaiStandard d")
    List<Object[]> tousLesDelais();
}
