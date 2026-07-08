package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.DemandeRetrait;

@Repository
public interface DemandeRetraitRepository extends JpaRepository<DemandeRetrait, Integer> {

    /** Nombre de demandes de retrait à un statut donné (compteur du tableau de bord — vue globale). */
    long countByStatut(String statut);

    /** Nombre de demandes à un statut donné dont le DOSSIER est dans la localité (scope CC, §3.3). */
    @Query("""
            select count(dr) from DemandeRetrait dr where dr.statut = :statut
              and exists (select 1 from Dossier d where d.idDossier = dr.idDossier and d.idLocalite = :loc)
            """)
    long countByStatutEtLocaliteDossier(@Param("statut") String statut, @Param("loc") String loc);

    /**
     * Nombre de demandes d'une PRMP <strong>décidées</strong> ({@code ACCEPTEE}/{@code REFUSEE})
     * après un seuil (dernière consultation de l'écran) — compteur « nouveautés » du menu PRMP.
     * {@code DATE_DECISION} est posée au passage ACCEPTEE/REFUSEE (null pour EN_ATTENTE, donc exclu).
     */
    @Query("""
            select count(dr) from DemandeRetrait dr
            where dr.idPrmp = :idPrmp and dr.statut in ('ACCEPTEE', 'REFUSEE')
              and dr.dateDecision > :seuil
            """)
    long countNouvellesDecisionsPourPrmp(@Param("idPrmp") String idPrmp,
            @Param("seuil") java.time.LocalDateTime seuil);

    /** Demandes d'une PRMP (suivi de ses propres demandes, §3.1). */
    List<DemandeRetrait> findByIdPrmp(String idPrmp);

    /** Ce contrôleur (CC) a-t-il décidé au moins une demande de retrait ? (garde de suppression) */
    boolean existsByImCtrlCc(String imCtrlCc);

    /** Vrai s'il existe déjà une demande à ce statut pour ce dossier (anti-doublon EN_ATTENTE). */
    boolean existsByIdDossierAndStatut(Integer idDossier, String statut);

    /** Vrai si le dossier porte au moins une demande de retrait (trace de circuit — empêche le hard delete). */
    boolean existsByIdDossier(Integer idDossier);

    /** Supprime les demandes de retrait d'un dossier (cascade à la suppression du dossier brouillon). */
    void deleteByIdDossier(Integer idDossier);

    /** Demandes à un (ou plusieurs) statut(s) — Président, toutes localités. */
    List<DemandeRetrait> findByStatutIn(List<String> statuts);

    /** Demandes d'un ou plusieurs statuts dont le DOSSIER est dans la localité (scope CC, §3.3). */
    @Query("""
            select dr from DemandeRetrait dr where dr.statut in :statuts
              and exists (select 1 from Dossier d where d.idDossier = dr.idDossier and d.idLocalite = :loc)
            """)
    List<DemandeRetrait> findByStatutsEtLocaliteDossier(@Param("statuts") List<String> statuts, @Param("loc") String loc);

    @Query("""
            select dr from DemandeRetrait dr where exists (
                select 1 from Reception r where r.idDossier = dr.idDossier and r.ctrlRecept.idLocalite = :loc)
            """)
    List<DemandeRetrait> findVisiblesParLocalite(@Param("loc") String loc);

    @Query("""
            select (count(dr) > 0) from DemandeRetrait dr where dr.idDemandeRetrait = :id and exists (
                select 1 from Reception r where r.idDossier = dr.idDossier and r.ctrlRecept.idLocalite = :loc)
            """)
    boolean existsDansLocalite(@Param("id") Integer id, @Param("loc") String loc);
}
