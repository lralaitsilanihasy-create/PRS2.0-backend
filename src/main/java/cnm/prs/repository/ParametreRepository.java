package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.Parametre;

/**
 * Accès aux paramètres système ({@code t_parametre}, clé/valeur).
 */
public interface ParametreRepository extends JpaRepository<Parametre, String> {
}
