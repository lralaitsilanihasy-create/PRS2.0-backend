package cnm.prs.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Production du PDF de la lettre de renvoi <strong>en arrière-plan</strong> — jumelle de
 * {@link PvDocumentTache}, arbitrage du 2026-08-28 (option B).
 *
 * <p><strong>Ce qui change.</strong> {@code LettreRenvoiService#signer} produisait le PDF
 * <em>dans</em> la transaction de signature : la conversion .docx → PDF pilote Word localement,
 * plusieurs secondes, et son échec faisait refluer la signature. Le PV avait été sorti de ce
 * schéma le 2026-08-19 ; la lettre y était restée. Elle s'aligne : la signature commite seule,
 * le document part ensuite.</p>
 *
 * <p><strong>Pourquoi maintenant.</strong> La première CI qui a réellement exécuté les tests
 * (2026-08-28) échouait sur les runners GitHub, dépourvus de Word : tout test signant une lettre
 * mourait sur la construction du convertisseur. Ces tests n'étaient pas tagués {@code word} — et
 * les taguer aurait retiré de la CI tout le circuit de la lettre (statuts, transitions,
 * périmètres) pour un seul appel PDF. Sortir la génération du chemin de signature rend le circuit
 * testable sans Word, et rend une panne de Word non bloquante en production.</p>
 *
 * <p>Un registre en mémoire évite les doubles générations concurrentes de la même lettre ; l'échec
 * est journalisé sans rien casser — le téléchargement conserve une régénération paresseuse en
 * filet ({@code LettreRenvoiService#telechargerDocument}).</p>
 */
@Service
public class LettreRenvoiDocumentTache {

    private static final Logger log = LoggerFactory.getLogger(LettreRenvoiDocumentTache.class);

    private final LettreRenvoiService service;

    /** Lettres dont la génération est en cours (dédoublonnage + signal « en préparation »). */
    private final Set<Integer> enCours = ConcurrentHashMap.newKeySet();

    public LettreRenvoiDocumentTache(LettreRenvoiService service) {
        this.service = service;
    }

    /** Signature : la génération part APRÈS COMMIT — la réponse de signature n'attend jamais Word. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surLettreSignee(LettreRenvoiSigneeEvent evenement) {
        genererEtEnregistrer(evenement.idLettre());
    }

    /** Rattrapage hors événement (lettres signées avant ce correctif, sans fichier). */
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
            service.genererEtStockerDocument(idLettre);
        } catch (RuntimeException e) {
            // Jamais bloquant : la lettre reste SIGNE, le téléchargement retentera la génération.
            log.warn("Génération en arrière-plan du document de la lettre {} impossible : {}",
                    idLettre, e.getMessage());
        } finally {
            enCours.remove(idLettre);
        }
    }
}
