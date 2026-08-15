package cnm.prs.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.repository.DispatchRepository;

/**
 * ⚠️ Règle modifiée (2026-08-15, spec dispatch) — reprise des données : avant cette règle, le CC de
 * la localité était associé ({@code IM_CTRL_CC}) à <strong>tout</strong> dispatch, y compris quand le
 * CC dispatchait lui-même ou s'auto-attribuait le dossier — d'où un doublon « Rôle Membre + Rôle CC »
 * pour la même personne dans les attributions (cas constaté sur 00002/PPM/CNM/2026).
 *
 * <p>Nettoyage idempotent : {@code IM_CTRL_CC} est effacé quand il désigne l'<strong>attributaire</strong>
 * ({@code IM_CTRL_CC = IM_CTRL_MEMBRE}) ou le <strong>dispatcheur</strong> lui-même
 * ({@code IM_CTRL_CC = IM_CTRL_DISPATCH}). Les associations légitimes (Président → Membre, CC tiers)
 * sont conservées. Activée par défaut ; désactivable avec
 * {@code app.migration.association-cc-dispatch.enabled=false}.</p>
 */
@Component
@ConditionalOnProperty(name = "app.migration.association-cc-dispatch.enabled",
        havingValue = "true", matchIfMissing = true)
public class AssociationCcDispatchMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AssociationCcDispatchMigration.class);

    private final DispatchRepository dispatchRepository;

    public AssociationCcDispatchMigration(DispatchRepository dispatchRepository) {
        this.dispatchRepository = dispatchRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int corriges = dispatchRepository.effacerAssociationCcInvalide();
        if (corriges > 0) {
            log.info("[MIGRATION] Association CC effacée sur {} dispatch(s) où IM_CTRL_CC désignait "
                    + "l'attributaire ou le dispatcheur lui-même (doublon Membre+CC).", corriges);
        }
    }
}
