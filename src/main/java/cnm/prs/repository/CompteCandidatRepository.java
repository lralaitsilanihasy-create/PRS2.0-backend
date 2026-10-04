package cnm.prs.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cnm.prs.entity.CompteCandidat;

/** ⚠️ V63 (2026-10-04) — les comptes candidats. */
public interface CompteCandidatRepository extends JpaRepository<CompteCandidat, String> {

    Optional<CompteCandidat> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Les comptes jamais confirmés, inscrits avant {@code limite} (ménage, §B7). */
    List<CompteCandidat> findByEtatAndDateInscriptionBefore(String etat, LocalDateTime limite);

    /** Les comptes confirmés sans connexion depuis {@code limite} (à défaut de connexion : depuis leur confirmation). */
    @Query("select c from CompteCandidat c where c.etat = 'CONFIRME' "
            + "and coalesce(c.derniereConnexion, c.dateConfirmation, c.dateInscription) < :limite")
    List<CompteCandidat> inactifsDepuis(@Param("limite") LocalDateTime limite);

    @Query(value = "select nextval('public.seq_compte_candidat')", nativeQuery = true)
    long prochainNumero();
}
