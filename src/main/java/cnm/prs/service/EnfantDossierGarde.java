package cnm.prs.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.PerimetreDossier;

/**
 * ⚠️ LOT 3a (2026-08-26) — §3.1 (règle « édition/suppression uniquement si le dossier rattaché est en
 * BROUILLON et propriété de la PRMP ») : garde d'<strong>écriture</strong> commune aux ressources
 * enfants de la saisie PPM (lots, tranches, bénéficiaires, dates prévisionnelles).
 *
 * <p>Avant ce lot, ces CRUD génériques n'avaient aucune garde : n'importe quel authentifié pouvait
 * créer, modifier ou supprimer un lot d'un dossier d'autrui, y compris après soumission. La garde
 * rejoue la même règle que la façade de saisie, en deux temps :</p>
 * <ol>
 *   <li><strong>Propriété</strong> ({@link PerimetreDossier}) — le dossier parent doit être dans le
 *       périmètre de l'appelant, sinon <strong>403</strong> ;</li>
 *   <li><strong>Éditabilité</strong> ({@link DossierIntegriteService#exigerBrouillonModifiable}) — le
 *       dossier doit être un {@code BROUILLON} et l'opérateur habilité (mandat actif), sinon
 *       <strong>409</strong>.</li>
 * </ol>
 *
 * <p>L'<strong>Administrateur</strong> n'est soumis à aucune de ces deux contraintes (reprise de
 * données, correction). Le filtrage par <em>rôle</em> (PRMP / UGPM / Administrateur) reste porté par
 * les {@code @PreAuthorize} des contrôleurs : cette garde ne traite que le périmètre et l'état.</p>
 */
@Service
@Transactional(readOnly = true)
public class EnfantDossierGarde {

    private final PerimetreDossier perimetre;
    private final DossierIntegriteService dossierIntegrite;
    private final MarcheRepository marcheRepository;

    public EnfantDossierGarde(PerimetreDossier perimetre, DossierIntegriteService dossierIntegrite,
            MarcheRepository marcheRepository) {
        this.perimetre = perimetre;
        this.dossierIntegrite = dossierIntegrite;
        this.marcheRepository = marcheRepository;
    }

    /**
     * Dossier <strong>faisant autorité</strong> d'une ligne de marché — jamais l'{@code idDossier}
     * envoyé par le client, qui permettrait de déclarer un dossier à soi tout en rattachant l'enfant
     * au marché d'autrui.
     *
     * @throws ResourceNotFoundException si la ligne de marché n'existe pas (→ 404)
     */
    public Integer dossierDuMarche(Integer idDetail) {
        if (idDetail == null) {
            throw new ResourceNotFoundException("Ligne de marché non renseignée : rattachement impossible.");
        }
        return marcheRepository.findIdDossierByIdDetail(idDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Marché introuvable : " + idDetail));
    }

    /** Garde d'écriture sur un enfant rattaché à une <strong>ligne de marché</strong>. */
    public void exigerEcritureSurMarche(Integer idDetail) {
        exigerEcritureSurDossier(dossierDuMarche(idDetail));
    }

    /**
     * Garde d'écriture sur un enfant rattaché à un <strong>dossier</strong> : propriété (403) puis
     * brouillon + habilitation (409). Sans effet pour l'Administrateur.
     */
    public void exigerEcritureSurDossier(Integer idDossier) {
        if (estAdministrateur()) {
            return;
        }
        perimetre.controler(idDossier);
        dossierIntegrite.exigerBrouillonModifiable(idDossier);
    }

    /** Vrai si l'appelant est Administrateur (aucune restriction de périmètre ni d'état). */
    public boolean estAdministrateur() {
        return CurrentUser.profil().orElse(null) == ProfilUtilisateur.ADMINISTRATEUR;
    }
}
