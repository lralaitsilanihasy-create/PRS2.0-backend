package cnm.prs.seed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.ModePassation;
import cnm.prs.enums.CategorieModePassation;
import cnm.prs.repository.ModePassationRepository;

/**
 * ⚠️ Règle ajoutée (2026-08-13) — reprise des données : renseigne {@code tr_mode_passation.CATEGORIE}
 * (colonne ajoutée par {@code ddl-auto=update}, donc {@code NULL} sur l'existant) à partir de la seule
 * classification <strong>officielle et déterministe</strong> disponible : le Code des marchés publics
 * fait de l'appel d'offres ouvert le mode de droit commun, et le marqueur AOO administré est le drapeau
 * {@code DECLENCHE_AGPM} (jamais de détection par mot-clé de libellé) → ces modes passent en
 * {@link CategorieModePassation#NORMAL}. Les autres restent {@code null} (non classés) : l'Administrateur
 * les classe via l'écran référentiel.
 *
 * <p><strong>Ne remplit que les {@code NULL}</strong> (jamais d'écrasement d'un classement admin).
 * Activée par défaut ; désactivable avec {@code app.migration.categorie-mode.enabled=false}.</p>
 */
@Component
@ConditionalOnProperty(name = "app.migration.categorie-mode.enabled", havingValue = "true", matchIfMissing = true)
public class CategorieModePassationMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CategorieModePassationMigration.class);

    private final ModePassationRepository modePassationRepository;

    public CategorieModePassationMigration(ModePassationRepository modePassationRepository) {
        this.modePassationRepository = modePassationRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<ModePassation> aClasser = modePassationRepository.findByDeclencheAgpmTrueAndCategorieIsNull();
        if (aClasser.isEmpty()) {
            return;
        }
        for (ModePassation mode : aClasser) {
            mode.setCategorie(CategorieModePassation.NORMAL);
        }
        modePassationRepository.saveAll(aClasser);
        log.info("[MIGRATION] Catégorie NORMAL posée sur {} mode(s) de passation marqué(s) DECLENCHE_AGPM "
                + "(appel d'offres ouvert = mode de droit commun) ; les autres restent non classés.", aClasser.size());
    }
}
