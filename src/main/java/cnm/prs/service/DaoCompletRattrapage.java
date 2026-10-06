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
 */
@Component
public class DaoCompletRattrapage {

    private static final Logger log = LoggerFactory.getLogger(DaoCompletRattrapage.class);

    private final DaoCompletService daoComplet;
    private final FicheMarcheRepository ficheRepository;
    private final DocumentFicheMarcheRepository documentRepository;
    private final FicheMarcheService fiches;

    public DaoCompletRattrapage(DaoCompletService daoComplet, FicheMarcheRepository ficheRepository,
            DocumentFicheMarcheRepository documentRepository, FicheMarcheService fiches) {
        this.daoComplet = daoComplet;
        this.ficheRepository = ficheRepository;
        this.documentRepository = documentRepository;
        this.fiches = fiches;
    }

    @Scheduled(initialDelayString = "${app.dao-complet.rattrapage-initial-ms:120000}", fixedDelayString = "${app.dao-complet.rattrapage-ms:600000}")
    public void rattraper() {
        if (!daoComplet.actif()) {
            return;
        }
        for (FicheMarche f : ficheRepository.findDernieresValidees()) {
            boolean aDesDocuments = !documentRepository.findByIdFicheOrderByIdDocumentAsc(f.getIdFiche()).isEmpty();
            boolean complet = documentRepository.findByIdFicheOrderByIdDocumentAsc(f.getIdFiche()).stream()
                    .anyMatch(d -> DaoCompletService.TYPE.equals(d.getType()));
            if (!aDesDocuments || complet) {
                continue;
            }
            try {
                fiches.etatValide(f.getIdDmc()).ifPresent(e -> daoComplet.assurer(f, e.etat()));
            } catch (RuntimeException e) {
                log.warn("[DAO_COMPLET] rattrapage du DMC {} : {}", f.getIdDmc(), e.getMessage());
            }
        }
    }
}
