package cnm.prs.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Examen;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.repository.DispatchRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * ⚠️ Audit 2026-08-27, lot B — gardes d'<strong>écriture</strong> communes à l'examen et à ses tables
 * filles ({@code t_examen_detail}, {@code t_examen_piece}).
 *
 * <p>Le verrou d'état existait en trois exemplaires recopiés ({@code ExamenService},
 * {@code ExamenDetailService}, et manquait aux pièces) ; la garde d'identité, elle, n'existait qu'à la
 * <em>création</em> d'un examen. Les deux vivent désormais ici, à une seule place : un point de
 * contrôle et un résultat de pièce sont des morceaux de l'examen, ils obéissent aux mêmes règles que
 * lui.</p>
 */
@Component
@Transactional(readOnly = true)
public class ExamenGarde {

    private final ExamenRepository examenRepository;
    private final DispatchRepository dispatchRepository;

    public ExamenGarde(ExamenRepository examenRepository, DispatchRepository dispatchRepository) {
        this.examenRepository = examenRepository;
        this.dispatchRepository = dispatchRepository;
    }

    /**
     * Verrou d'édition (§2.6) : l'examen et ses lignes ne sont modifiables que tant que le dossier est
     * {@link StatutDossier#DISPATCHE} (brouillon de progression), {@link StatutDossier#EXAMINE} (navette
     * ouverte) ou {@link StatutDossier#A_REEXAMINER} (réexamen après lettre de renvoi). Dès la signature
     * du PV, l'examen est <strong>définitif</strong> → 409.
     */
    public void exigerExamenModifiable(Integer idExamen) {
        String statut = idExamen == null ? null
                : examenRepository.findStatutDossierByExamen(idExamen).orElse(null);
        boolean modifiable = StatutDossier.DISPATCHE.name().equals(statut)
                || StatutDossier.EXAMINE.name().equals(statut)
                || StatutDossier.A_REEXAMINER.name().equals(statut);
        if (!modifiable) {
            throw new BusinessRuleException(
                    "Examen verrouillé : modification possible uniquement tant que le dossier est DISPATCHE "
                            + "(brouillon), EXAMINE ou A_REEXAMINER (statut actuel « " + statut
                            + " », examen définitif après signature du PV, §2.6).");
        }
    }

    /**
     * Garde d'identité de l'écriture (§2.4, §3.3), identique à celle de la création d'un examen :
     * <ul>
     *   <li><strong>localité</strong> du circuit de l'examen (réception) — un contrôleur, même délégué,
     *       n'écrit que dans sa localité ; Président/Administrateur exemptés ;</li>
     *   <li><strong>Membre attributaire</strong> : un Membre <em>titulaire</em> n'écrit que sur les
     *       examens du dispatch qui lui est attribué. Un CC / Président instruisant par délégation
     *       (profil ≠ MEMBRE) reste autorisé, sa localité venant d'être vérifiée.</li>
     * </ul>
     *
     * @throws AccessDeniedException (→ 403) hors localité, ou Membre non attributaire
     */
    public void exigerAttributaire(Integer idExamen) {
        Visibilite.exigerLocalite(idExamen == null ? null
                : examenRepository.findLocaliteByExamen(idExamen).orElse(null));
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.MEMBRE) {
            return; // délégation (CC/Président/Admin) : autorisé, localité déjà vérifiée
        }
        String attributaire = idExamen == null ? null
                : examenRepository.findById(idExamen).map(Examen::getIdDispatch)
                        .flatMap(dispatchRepository::findImCtrlMembreById).orElse(null);
        String moi = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
        if (attributaire == null || !attributaire.equals(moi)) {
            throw new AccessDeniedException(
                    "Examen réservé au Membre attributaire du dispatch (§2.4) : vous n'êtes pas l'attributaire.");
        }
    }
}
