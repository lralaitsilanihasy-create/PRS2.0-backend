package cnm.prs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cnm.prs.entity.FicheMarche;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.FicheMarcheRepository;

/**
 * ⚠️ 2026-10-06 (DAO complet, §B3) — les versions <strong>déjà validées</strong> reçoivent leur DAO complet sans attendre une
 * nouvelle validation : toutes les {@code app.dao-complet.rattrapage-ms} (défaut 10 min), la dernière version validée de chaque DMC
 * qui n'en a pas est assemblée (Word), une à la fois. Sans Word, rien. Le dossier déjà soumis à la Commission garde ses pièces.
 * <p>
 * ⚠️ 2026-10-28 (« dao-complet-en-file », Q2) — le rattrapage n'assemble plus lui-même : il inscrit en file les versions qui n'y ont
 * pas de place (ou dont le DAO complet a été retiré) et réveille l'ouvrier unique, qui reprend aussi les versions en échec dont l'heure
 * de reprise est passée.
 */
@Component
public class DaoCompletRattrapage {

    private static final Logger log = LoggerFactory.getLogger(DaoCompletRattrapage.class);

    private final DaoCompletFileService file;
    private final FicheMarcheRepository ficheRepository;
    private final DocumentFicheMarcheRepository documentRepository;

    public DaoCompletRattrapage(DaoCompletFileService file, FicheMarcheRepository ficheRepository,
            DocumentFicheMarcheRepository documentRepository) {
        this.file = file;
        this.ficheRepository = ficheRepository;
        this.documentRepository = documentRepository;
    }

    @Scheduled(initialDelayString = "${app.dao-complet.rattrapage-initial-ms:120000}", fixedDelayString = "${app.dao-complet.rattrapage-ms:600000}")
    public void rattraper() {
        if (!file.actif()) {
            return;
        }
        for (FicheMarche f : ficheRepository.findDernieresValidees()) {
            var docs = documentRepository.findByIdFicheOrderByIdDocumentAsc(f.getIdFiche());
            if (docs.isEmpty() || docs.stream().anyMatch(d -> DaoCompletService.TYPE.equals(d.getType()))) {
                continue;
            }
            try {
                file.inscrireOubliee(f);
            } catch (RuntimeException e) {
                log.warn("[DAO_COMPLET] rattrapage du DMC {} : {}", f.getIdDmc(), e.getMessage());
            }
        }
        file.reveiller();
    }
}
