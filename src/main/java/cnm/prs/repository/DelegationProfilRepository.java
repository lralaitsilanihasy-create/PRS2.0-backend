package cnm.prs.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * ⚠️ 2026-09-15 — <strong>paires actives</strong>, par libellé de profil ({@code tr_profile.PROFILE}) :
     * {@code [libellé délégant, libellé délégué]}. Une requête pour toutes les paires, là où
     * {@link #existsByActifTrueAndIdProfileDelegantInAndIdProfileDelegueIn} en coûte une par couple testé —
     * l'accueil « À faire » pose la question pour plusieurs profils cibles sur toute une liste.
     */
    @Query("""
            select pd.profile, pe.profile from DelegationProfil d, Profile pd, Profile pe
            where d.actif = true and pd.idProfile = d.idProfileDelegant and pe.idProfile = d.idProfileDelegue
            """)
    java.util.List<Object[]> findPairesActivesParLibelle();

    /** Vrai si la paire (délégant, délégué) existe déjà — active ou non (unicité, seed idempotent). */
    boolean existsByIdProfileDelegantAndIdProfileDelegue(Integer delegant, Integer delegue);


    /** Prochaine PK allouee par la sequence serveur {@code seq_delegation_profil} (allocation atomique). */
    @Query(value = "select nextval('seq_delegation_profil')", nativeQuery = true)
    Long nextIdDelegation();
}
