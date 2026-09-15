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

    /**
     * ⚠️ Revue du 2026-09-14 — <strong>rattachement figé</strong> d'une ligne d'examen (résultat de point de
     * contrôle, résultat de pièce) : un {@code PUT} ne la change pas d'examen.
     *
     * <p>Le PUT vérifiait l'attributaire de l'examen visé par le corps, mais le verrou d'état seulement sur
     * l'examen en place : l'attributaire de deux examens déplaçait un résultat vers celui dont le PV était
     * signé (200). Plutôt que d'étendre le verrou à l'examen visé, le déplacement est refusé : un résultat
     * est un morceau de la grille de <em>son</em> examen (unicité, complétude à la soumission, instantané des
     * observations du PV), et le déplacer, même entre deux examens ouverts, fausse les deux grilles. Aucun
     * appelant ne le fait : le front ne met à jour que les résultats de l'examen qu'il enregistre.</p>
     *
     * @throws cnm.prs.exception.ChampsInvalidesException (→ 400, champ {@code idExamen}) si l'examen change
     */
    public void exigerRattachementInchange(Integer idExamenEnPlace, Integer idExamenDuCorps) {
        if (!java.util.Objects.equals(idExamenEnPlace, idExamenDuCorps)) {
            throw new cnm.prs.exception.ChampsInvalidesException(java.util.List.of(
                    new cnm.prs.exception.ErrorResponse.FieldError("idExamen",
                            "Un résultat d'examen ne change pas d'examen de rattachement (examen " + idExamenEnPlace
                                    + ", reçu " + idExamenDuCorps + ") : le créer sur l'examen visé.")));
        }
    }

    /** Message de la règle « point non conforme ⇒ au moins une ligne d'observation », à une seule place. */
    static final String OBSERVATION_OBLIGATOIRE_SI_NON_CONFORME =
            "Au moins une ligne d'observation est obligatoire si le point est non conforme.";

    /**
     * ⚠️ Règle ajoutée — un point de contrôle <strong>non conforme</strong> ({@code conforme=false}) porte au
     * moins une ligne d'observation, sinon 400 (champ {@code observations}).
     *
     * <p>⚠️ Revue du 2026-09-14 — la règle n'était tenue que par {@code /api/examen-details}, sur le corps
     * reçu ; {@code PUT}/{@code DELETE /api/observation-controles} retiraient la dernière ligne d'un point non
     * conforme sans rien vérifier. Elle vit ici pour que les deux portes disent la même chose.</p>
     *
     * @param conforme  état du point ({@code null} = non renseigné : aucune exigence)
     * @param sansLigne vrai si le point n'aura plus aucune ligne une fois l'écriture faite
     */
    public void exigerObservationSiNonConforme(Boolean conforme, boolean sansLigne) {
        if (Boolean.FALSE.equals(conforme) && sansLigne) {
            throw new cnm.prs.exception.ChampsInvalidesException(java.util.List.of(
                    new cnm.prs.exception.ErrorResponse.FieldError("observations",
                            OBSERVATION_OBLIGATOIRE_SI_NON_CONFORME)));
        }
    }
}
