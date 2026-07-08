package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Ugpm;

@Repository
public interface UgpmRepository extends JpaRepository<Ugpm, String> {

    /** UGPM rattachées à une PRMP de tutelle (une PRMP chapeaute plusieurs UGPM). */
    List<Ugpm> findByIdPrmpTutelle(String idPrmpTutelle);

    /** UGPM rattachées à l'une des PRMP de tutelle fournies (utilisé pour le filtre par localité). */
    List<Ugpm> findByIdPrmpTutelleIn(Collection<String> idsPrmpTutelle);

    /** Recherche partielle par nom (contient, insensible à la casse). */
    List<Ugpm> findByNomUgpmContainingIgnoreCase(String nom);
}
