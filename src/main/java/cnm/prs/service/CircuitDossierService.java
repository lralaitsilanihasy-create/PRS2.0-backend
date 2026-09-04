package cnm.prs.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Localite;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.PvExamenRepository;

/**
 * ⚠️ <strong>Le circuit d'un dossier</strong> — localité, dispatcheur courant, attributaire — et le
 * <strong>discriminant du « deux niveaux »</strong> (spec pilote du 2026-09-04).
 *
 * <p><strong>Pourquoi cette classe existe.</strong> Le discriminant vivait en privé dans
 * {@link PvExamenService}, où il décide qui accepte, qui retourne et qui vise. Le 2026-09-04, la garde
 * de <em>prise en charge</em> a eu besoin du même raisonnement : sans extraction, il aurait été récrit
 * une seconde fois dans {@link ChronometrageService}. Deux copies d'une règle de circuit ne restent
 * jamais d'accord longtemps — et leur désaccord ne se voit qu'en recette, sur les seuls dossiers
 * réattribués. C'est exactement le défaut que le constat du dossier 100286 a coûté à corriger en SQL.</p>
 *
 * <p>Aucune règle nouvelle ici : le contenu est celui qui servait déjà, déplacé sans changement.</p>
 */
@Service
public class CircuitDossierService {

    private final PvExamenRepository pvExamenRepository;
    private final DispatchRepository dispatchRepository;
    private final ControleurDirectory controleurDirectory;

    public CircuitDossierService(PvExamenRepository pvExamenRepository, DispatchRepository dispatchRepository,
            ControleurDirectory controleurDirectory) {
        this.pvExamenRepository = pvExamenRepository;
        this.dispatchRepository = dispatchRepository;
        this.controleurDirectory = controleurDirectory;
    }

    /** Le circuit d'un dossier : localité, dispatcheur COURANT, attributaire COURANT. */
    public record Circuit(String localite, String dispatcheur, String attributaire) {

        /** Vrai si le dossier a bien un dispatch exploitable — sinon, aucune règle de circuit ne s'applique. */
        public boolean complet() {
            return dispatcheur != null && !dispatcheur.isBlank() && attributaire != null;
        }
    }

    /** Circuit lu depuis le PV (chemin de la navette), en une requête. */
    @Transactional(readOnly = true)
    public Circuit parPv(Integer idPv) {
        return premier(idPv == null ? List.of() : pvExamenRepository.findCircuitByPv(idPv));
    }

    /** Circuit lu depuis le dossier (chemin du chronométrage), en une requête. */
    @Transactional(readOnly = true)
    public Circuit parDossier(Integer idDossier) {
        return premier(idDossier == null ? List.of() : dispatchRepository.findCircuitByDossier(idDossier));
    }

    private Circuit premier(List<Object[]> lignes) {
        return lignes.stream().findFirst()
                .map(r -> new Circuit((String) r[0], (String) r[1], (String) r[2]))
                .orElseGet(() -> new Circuit(null, null, null));
    }

    /**
     * ⚠️ <strong>Discriminant du « deux niveaux »</strong> (arbitrage 4 du pilote, 2026-09-04) — le
     * périmètre est le <strong>chemin réel</strong> du dossier, pas sa localité ni son type.
     *
     * <p>Trois conditions, sur le dispatch courant : le dossier est <strong>central</strong>, son
     * dispatcheur est un <strong>Chef de commission</strong>, et il n'est pas lui-même l'attributaire.
     * Le raisonnement tient à une propriété du circuit : en centrale, <em>seul le Président
     * dispatche</em> (garde du 2026-09-03). Si le dispatcheur courant est un CC, c'est donc
     * nécessairement qu'il a RÉATTRIBUÉ un dossier reçu du Président — le chemin P → CC → Membre est
     * prouvé sans avoir à relire l'historique des dispatchs.</p>
     *
     * <p><strong>Ce que la troisième condition exclut.</strong> Le CC qui examine lui-même le dossier
     * que le Président lui a confié reste à UN niveau : il soumettrait au CC, c'est-à-dire à lui-même.
     * Un dispatch direct — Président → Membre, CC régional → Membre, ou P/CC auto-attributaire — garde
     * donc la navette simple, sans qu'aucune de ces situations n'ait à être énumérée.</p>
     */
    public boolean deuxNiveaux(Circuit circuit) {
        if (circuit == null || !circuit.complet() || !Localite.estCentrale(circuit.localite())) {
            return false;
        }
        return !circuit.dispatcheur().equals(circuit.attributaire())
                && controleurDirectory.profilDe(circuit.dispatcheur()).orElse(null)
                        == ProfilUtilisateur.CHEF_COMMISSION;
    }
}
