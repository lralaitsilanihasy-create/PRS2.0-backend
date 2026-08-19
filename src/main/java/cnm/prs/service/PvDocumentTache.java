package cnm.prs.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cnm.prs.entity.PvExamen;
import cnm.prs.repository.PvExamenRepository;

/**
 * Production du PDF du PV <strong>en arrière-plan</strong> (spec du 2026-08-19) — la conversion
 * .docx → PDF pilote Word localement (plusieurs secondes, incompressibles) et n'a plus rien à
 * faire dans la transaction de la signature.
 *
 * <p>Déclencheurs : l'événement {@link PvSigneEvent} <strong>après commit</strong> de la
 * signature finale (le PV {@code SIGNE} est déjà visible de tous ; {@code CHEMIN_DOCUMENT} est
 * renseigné quand le document est prêt), et le rattrapage {@link #genererEnArrierePlan(Integer)}
 * pour les PV signés avant ce correctif (legacy sans fichier). Un registre en mémoire évite les
 * doubles générations concurrentes du même PV ; l'échec est journalisé sans casser quoi que ce
 * soit — le téléchargement conserve sa régénération paresseuse en filet.</p>
 */
@Service
public class PvDocumentTache {

    private static final Logger log = LoggerFactory.getLogger(PvDocumentTache.class);

    private final PvExamenRepository repository;
    private final PvDocumentService documentService;

    /** PV dont la génération est en cours (dédoublonnage + signal « en préparation » au téléchargement). */
    private final Set<Integer> enCours = ConcurrentHashMap.newKeySet();

    public PvDocumentTache(PvExamenRepository repository, PvDocumentService documentService) {
        this.repository = repository;
        this.documentService = documentService;
    }

    /** Signature finale : la génération part APRÈS COMMIT — la réponse de signature n'attend jamais Word. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surPvSigne(PvSigneEvent evenement) {
        genererEtEnregistrer(evenement.idPv());
    }

    /** Rattrapage hors événement (PV signés avant le correctif, sans fichier) — mêmes garanties. */
    @Async
    public void genererEnArrierePlan(Integer idPv) {
        genererEtEnregistrer(idPv);
    }

    /** Vrai si la génération du document de ce PV est en cours (fenêtre post-signature). */
    public boolean estEnCours(Integer idPv) {
        return enCours.contains(idPv);
    }

    private void genererEtEnregistrer(Integer idPv) {
        if (idPv == null || !enCours.add(idPv)) {
            return;
        }
        try {
            PvExamen pv = repository.findById(idPv).orElse(null);
            if (pv == null || (pv.getCheminDocument() != null && !pv.getCheminDocument().isBlank())) {
                return;   // introuvable (ou déjà doté d'un document) : rien à produire
            }
            documentService.genererSiEligible(pv).ifPresent(chemin -> {
                pv.setCheminDocument(chemin);
                repository.save(pv);
                log.info("Document du PV {} produit en arrière-plan : {}", idPv, chemin);
            });
        } catch (RuntimeException e) {
            // Jamais bloquant : le téléchargement garde sa régénération paresseuse en filet.
            log.warn("Génération en arrière-plan du document du PV {} impossible : {}", idPv, e.getMessage());
        } finally {
            enCours.remove(idPv);
        }
    }
}
