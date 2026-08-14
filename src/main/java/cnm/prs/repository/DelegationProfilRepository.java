package cnm.prs.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.DelegationProfil;

@Repository
public interface DelegationProfilRepository extends JpaRepository<DelegationProfil, Integer> {

    /**
     * Vrai s'il existe une délégation active permettant à l'un des profils {@code delegues}
     * d'exercer les tâches de l'un des profils {@code delegants}.
     */
    boolean existsByActifTrueAndIdProfileDelegantInAndIdProfileDelegueIn(
            Collection<Integer> delegants, Collection<Integer> delegues);

    /** Vrai si la paire (délégant, délégué) existe déjà — active ou non (unicité, seed idempotent). */
    boolean existsByIdProfileDelegantAndIdProfileDelegue(Integer delegant, Integer delegue);
}
