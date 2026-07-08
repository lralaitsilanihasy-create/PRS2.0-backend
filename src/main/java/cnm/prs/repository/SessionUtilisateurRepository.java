package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.SessionUtilisateur;

@Repository
public interface SessionUtilisateurRepository extends JpaRepository<SessionUtilisateur, String> {

    /** Supprime les sessions d'un contrôleur (nettoyage à la suppression du contrôleur). */
    long deleteByImControleur(String imControleur);
}
