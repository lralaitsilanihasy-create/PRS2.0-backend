package cnm.prs.seed;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Marche;
import cnm.prs.enums.FormeMarche;
import cnm.prs.repository.MarcheRepository;

/**
 * ⚠️ Règle ajoutée (2026-07-18) — reprise des données : renseigne {@code t_marche.FORME_MARCHE} sur les
 * lignes historiques (colonne ajoutée par {@code ddl-auto=update}, donc {@code NULL} sur l'existant).
 * La forme est <strong>dérivée de la désignation</strong> avec les mêmes motifs que l'import PPM
 * ({@link FormeMarche#detecterDansDesignation}) : « contrat cadre » → CONTRAT_CADRE, « à commande » →
 * A_COMMANDE, sinon défaut QUANTITE_FIXE.
 *
 * <p><strong>Idempotente</strong> : ne touche que les lignes dont la colonne est {@code NULL} — les
 * exécutions suivantes ne trouvent plus rien. Activée par défaut ; désactivable avec
 * {@code app.migration.forme-marche.enabled=false}.</p>
 */
@Component
@ConditionalOnProperty(name = "app.migration.forme-marche.enabled", havingValue = "true", matchIfMissing = true)
public class FormeMarcheMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FormeMarcheMigration.class);

    private final MarcheRepository marcheRepository;

    public FormeMarcheMigration(MarcheRepository marcheRepository) {
        this.marcheRepository = marcheRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Marche> aReprendre = marcheRepository.findByFormeMarcheIsNull();
        if (aReprendre.isEmpty()) {
            return;
        }
        for (Marche m : aReprendre) {
            m.setFormeMarche(FormeMarche.detecterDansDesignation(m.getDesignationMarche()));
        }
        marcheRepository.saveAll(aReprendre);
        log.info("[MIGRATION] Forme du marché reprise sur {} ligne(s) historique(s) de t_marche "
                + "(dérivée de la désignation, défaut QUANTITE_FIXE).", aReprendre.size());
    }
}
