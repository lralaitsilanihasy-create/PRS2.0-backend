package cnm.prs.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Echeance;

@Repository
public interface EcheanceRepository extends JpaRepository<Echeance, Integer> {

    /**
     * Jalons à alerter (§3.1, Module 04) : non encore alertés, non réalisés
     * ({@code DATE_REELLE} nulle) et dont la date prévue tombe dans la fenêtre [debut, fin].
     */
    @Query("""
            select e from Echeance e
            where (e.alerteEnvoyee is null or e.alerteEnvoyee = false)
              and e.dateReelle is null
              and e.datePrevue between :debut and :fin
            """)
    List<Echeance> findJalonsAAlerter(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /**
     * ⚠️ LOT 3a (2026-08-26) — §1/§3.1 : échéances des dossiers du périmètre de l'appelant (liste
     * scopée), rattachées via la ligne de marché ({@code ID_DETAIL → t_marche.ID_DOSSIER}). Sert le
     * calendrier des marchés de la PRMP comme la vue des contrôleurs de la localité.
     */
    @Query("select e from Echeance e, Marche m where m.idDetail = e.idDetail and m.idDossier in :idsDossiers")
    List<Echeance> findParDossiers(@Param("idsDossiers") Collection<Integer> idsDossiers);

    /** Dossier rattaché à une échéance (via son marché) — contrôle de périmètre d'un accès unitaire. */
    @Query("select m.idDossier from Echeance e, Marche m where e.idEcheance = :id and m.idDetail = e.idDetail")
    Optional<Integer> findIdDossier(@Param("id") Integer id);
}
