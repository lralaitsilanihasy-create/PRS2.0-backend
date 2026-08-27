package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Anomalie;

@Repository
public interface AnomalieRepository extends JpaRepository<Anomalie, Integer> {

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
