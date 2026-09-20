package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Anomalie;

@Repository
public interface AnomalieRepository extends JpaRepository<Anomalie, Integer> {

    /**
     * ⚠️ Pré-contrôle du PPM (2026-09-20, lot 3, migration V32) — prochaine PK allouée par la séquence
     * serveur {@code seq_anomalie} (allocation atomique). V5 avait généralisé les séquences de PK mais
     * sauté {@code t_anomalie}, qu'aucun code n'alimentait alors.
     */
    @Query(value = "select nextval('seq_anomalie')", nativeQuery = true)
    Long nextIdAnomalie();

    /**
     * Les signalements d'un PPM, tous statuts confondus — un signalement écarté ou levé reste visible
     * (rien ne s'efface).
     */
    List<Anomalie> findByIdPpmOrderByIdAnomalie(Integer idPpm);

    /**
     * Le signalement de même identité dans ce PPM, s'il existe déjà : une nouvelle exécution du
     * pré-contrôle le <strong>retrouve</strong> — donc son écartement et son motif — au lieu d'en créer
     * un double.
     */
    Optional<Anomalie> findByIdPpmAndCleSignalement(Integer idPpm, String cleSignalement);

    /**
     * Purge des anomalies d'une ligne de marché supprimée (⚠️ audit 2026-08-27, lot D §2) —
     * {@code t_anomalie.ID_DETAIL} porte une FK vers {@code t_marche}. Le commentaire de
     * {@code MarcheService#supprimerSousLignes} tenait pour acquis qu'« un marché supprimable est
     * BROUILLON, jamais dispatché : ni anomalie ni échéance possibles » — l'hypothèse est périmée
     * depuis que le retrait accepté ramène en BROUILLON un dossier qui a bel et bien circulé.
     */
    long deleteByIdDetail(Integer idDetail);

    /**
     * Idem pour les anomalies rattachées au PPM ({@code t_anomalie.ID_PPM} → {@code t_ppm}), à purger
     * avant la suppression d'un PPM (cascade de {@code PpmService} et de {@code DossierService}).
     */
    long deleteByIdPpm(Integer idPpm);
}
