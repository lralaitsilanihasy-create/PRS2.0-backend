package cnm.prs.seed;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import cnm.prs.service.ChampFicheMarcheService;

/**
 * ⚠️ Fiche marché DAO (demande front du 2026-09-22, §B1) — <strong>l'outil d'import du fichier de correspondance</strong>,
 * hors API : au démarrage, si {@code app.fiche-marche.import-csv=<chemin>} est posé, le CSV est chargé dans
 * {@code tr_champ_fiche_marche} (créations et mises à jour par code, lignes fautives rejetées avec leur raison, le
 * reste passe). Le bilan est écrit dans le journal applicatif. Sans la propriété, rien ne se passe.
 *
 * <p>Format : voir {@link ChampFicheMarcheService#importerCsv(Path)}. Usage :
 * {@code java -jar prs.jar --app.fiche-marche.import-csv=C:/chemin/correspondance.csv}. Plusieurs fichiers : séparés par « ; ».</p>
 */
@Component
@ConditionalOnProperty(name = "app.fiche-marche.import-csv")
public class ChampsFicheMarcheImport implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ChampsFicheMarcheImport.class);

    private final ChampFicheMarcheService service;
    private final String chemin;

    public ChampsFicheMarcheImport(ChampFicheMarcheService service,
            @Value("${app.fiche-marche.import-csv}") String chemin) {
        this.service = service;
        this.chemin = chemin;
    }

    @Override
    public void run(String... args) throws Exception {
        if (chemin == null || chemin.isBlank()) {
            return;
        }
        // ⚠️ 2026-09-24 (travaux) — plusieurs fichiers, séparés par « ; », chargés dans l'ordre.
        for (String un : chemin.split(";")) {
            if (un.isBlank()) {
                continue;
            }
            ChampFicheMarcheService.BilanImport bilan = service.importerCsv(Path.of(un.trim()));
            log.info("Import du fichier de correspondance {} : {} champ(s) créé(s), {} mis à jour, {} rejet(s).", un.trim(),
                    bilan.crees().size(), bilan.misAJour().size(), bilan.rejets().size());
            bilan.rejets().forEach(r -> log.warn("  rejeté — {}", r));
        }
    }
}
