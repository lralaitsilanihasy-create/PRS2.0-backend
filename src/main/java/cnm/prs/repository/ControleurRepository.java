package cnm.prs.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Controleur;

@Repository
public interface ControleurRepository extends JpaRepository<Controleur, String> {

    List<Controleur> findByIdProfileIn(Collection<Integer> idProfiles);

    List<Controleur> findByIdProfileInAndIdLocalite(Collection<Integer> idProfiles, String idLocalite);

    /** Contrôleurs affectés à une localité (idLocalite = X ; exclut les transversaux à localité nulle). */
    List<Controleur> findByIdLocalite(String idLocalite);

    /** Contrôleurs d'un profil (rôle) donné — idProfile = X (tr_profile). */
    List<Controleur> findByIdProfile(Integer idProfile);

    /** Ce contrôleur est-il le supérieur hiérarchique d'un autre ? (garde de suppression) */
    boolean existsByIdSuperieur(String idSuperieur);

    /** Contrôleurs dont le supérieur hiérarchique est {@code imSuperieur} (ses subordonnés directs). */
    List<Controleur> findByIdSuperieur(String idSuperieur);

    /** Recherche partielle par nom (contient, insensible à la casse). */
    List<Controleur> findByNomContContainingIgnoreCase(String nom);
}
