package cnm.prs.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Attribution;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.PerimetreDossier;
import cnm.prs.security.Visibilite;

/**
 * ⚠️ 2026-10-20 (demande front « dossier-marche-donnees », §B1 ; demande du pilote) — la Commission lit, <strong>en lecture seule</strong>,
 * la procédure (séance, évaluation, attribution, journal) d'un dossier de marché qu'elle contrôle : un compte de la Commission
 * ({@link #COMMISSION}), dès qu'un dossier de marché né de l'attribution d'un lot ({@code t_attribution.ID_DOSSIER}) est dans son
 * périmètre de lecture ({@link PerimetreDossier} : non brouillon de sa localité, donc dès la soumission — Q1 ; tous les contrôleurs du périmètre,
 * pas seulement ceux du circuit — Q2). Sans dossier de marché, rien ne change. Les gestes gardent leurs gardes ; les pièces des offres
 * restent aux membres de la CAO (403 {@code PIECE_RESERVEE_CAO}).
 */
@Component
@Transactional(readOnly = true)
public class ControleDuMarche {

    /** Les profils de la Commission (et l'Administrateur) ; ni PRMP, ni UGPM, ni membre de la CAO, ni candidat. */
    static final java.util.Set<ProfilUtilisateur> COMMISSION = java.util.EnumSet.of(ProfilUtilisateur.PRESIDENT,
            ProfilUtilisateur.CHEF_COMMISSION, ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.MEMBRE, ProfilUtilisateur.VERIFICATEUR,
            ProfilUtilisateur.ASSISTANT_CONTROLEUR, ProfilUtilisateur.CHARGE_PUBLICATION, ProfilUtilisateur.ADMINISTRATEUR);

    private final AttributionRepository attributions;
    private final PerimetreDossier perimetre;

    public ControleDuMarche(AttributionRepository attributions, PerimetreDossier perimetre) {
        this.attributions = attributions;
        this.perimetre = perimetre;
    }

    /** Vrai si l'appelant contrôle un dossier de marché né de cette procédure. */
    public boolean litLaProcedure(Long idDmc) {
        if (!CurrentUser.profil().map(COMMISSION::contains).orElse(false) || Visibilite.estPrmp()) {
            return false;
        }
        return attributions.findByIdDmcOrderByLotAsc(idDmc).stream().map(Attribution::getIdDossier)
                .anyMatch(id -> id != null && perimetre.estVisible(id));
    }
}
