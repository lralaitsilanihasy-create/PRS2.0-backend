package cnm.prs.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cnm.prs.entity.LettreRenvoi;
import cnm.prs.repository.LettreRenvoiRepository;

/**
 * Production du PDF de la lettre de renvoi <strong>en arrière-plan</strong> — même motif que
 * {@link PvDocumentTache} (spec du 2026-08-19) : la conversion .docx → PDF pilote Word localement
 * (plusieurs secondes, incompressibles) et n'a plus rien à faire dans la transaction de la signature.
 *
 * <p>Déclencheurs : l'événement {@link LettreRenvoiSigneeEvent} <strong>après commit</strong> de la
 * signature (la lettre {@code SIGNE} est déjà visible de tous ; {@code CHEMIN_DOCUMENT} est renseigné
 * quand le document est prêt), et le rattrapage {@link #genererEnArrierePlan(Integer)} pour les
 * lettres signées sans fichier (antérieures à ce correctif, ou dont la génération a échoué). Un
 * registre en mémoire évite les doubles générations concurrentes de la même lettre ; l'échec est
 * journalisé sans casser quoi que ce soit — le téléchargement conserve sa régénération paresseuse
 * en filet.</p>
 */
@Service
public class LettreRenvoiDocumentTache {

    private static final Logger log = LoggerFactory.getLogger(LettreRenvoiDocumentTache.class);

    private final LettreRenvoiRepository repository;
    private final LettreRenvoiDocumentService documentService;

    /** Lettres dont la génération est en cours (dédoublonnage + signal « en préparation »). */
    private final Set<Integer> enCours = ConcurrentHashMap.newKeySet();

    public LettreRenvoiDocumentTache(LettreRenvoiRepository repository,
            LettreRenvoiDocumentService documentService) {
        this.repository = repository;
        this.documentService = documentService;
    }

    /** Signature : la génération part APRÈS COMMIT — la réponse de signature n'attend jamais Word. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surLettreSignee(LettreRenvoiSigneeEvent evenement) {
        genererEtEnregistrer(evenement.idLettre());
    }

    /** Rattrapage hors événement (lettres signées avant le correctif, sans fichier) — mêmes garanties. */
    @Async
    public void genererEnArrierePlan(Integer idLettre) {
        genererEtEnregistrer(idLettre);
    }

    /** Vrai si la génération du document de cette lettre est en cours (fenêtre post-signature). */
    public boolean estEnCours(Integer idLettre) {
        return enCours.contains(idLettre);
    }

    private void genererEtEnregistrer(Integer idLettre) {
        if (idLettre == null || !enCours.add(idLettre)) {
            return;
        }
        try {
            LettreRenvoi lettre = repository.findById(idLettre).orElse(null);
            if (lettre == null || documentService.documentDisponible(lettre)) {
                return;   // introuvable (ou déjà dotée d'un document) : rien à produire
            }
            documentService.genererEtStocker(lettre).ifPresent(chemin -> {
                lettre.setCheminDocument(chemin);
                repository.save(lettre);
                log.info("Document de la lettre de renvoi {} produit en arrière-plan : {}", idLettre, chemin);
            });
        } catch (RuntimeException e) {
            // Jamais bloquant : la signature reste acquise, le téléchargement garde sa régénération
            // paresseuse en filet.
            log.warn("Génération en arrière-plan du document de la lettre {} impossible : {}",
                    idLettre, e.getMessage());
        } finally {
            enCours.remove(idLettre);
        }
    }
}
