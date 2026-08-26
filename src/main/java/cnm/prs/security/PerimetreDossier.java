package cnm.prs.security;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.repository.DossierRepository;

/**
 * ⚠️ LOT 3a (2026-08-26) — §1 / §3.1 : périmètre de visibilité appliqué aux ressources
 * <strong>enfants d'un dossier</strong> (lots, tranches, bénéficiaires, prévisions, échéances,
 * copies…), dont la propre table ne porte pas la localité.
 *
 * <p>Prolonge {@link Visibilite} : là où {@code Visibilite} choisit entre une requête « tout » et
 * une requête « par localité » sur une table qui porte elle-même le critère, cette classe résout
 * d'abord l'<strong>ensemble des dossiers visibles</strong> (source unique :
 * {@link DossierRepository}, mêmes prédicats que la liste des dossiers) puis laisse l'appelant
 * filtrer ses enfants sur ces identifiants.</p>
 *
 * <p>Règle appliquée, identique à celle des dossiers :</p>
 * <ul>
 *   <li>Président / Administrateur : tout ;</li>
 *   <li>PRMP <em>ou</em> UGPM ({@link Visibilite#estPrmp()}, le {@code ref} du jeton portant
 *       l'ID_PRMP de tutelle) : les dossiers dont elle est propriétaire ;</li>
 *   <li>autres contrôleurs : les dossiers <strong>non brouillons</strong> de leur localité ;</li>
 *   <li>sans localité ni propriété : rien.</li>
 * </ul>
 */
@Component("perimetre")
@Transactional(readOnly = true)
public class PerimetreDossier {

    private final DossierRepository dossierRepository;

    public PerimetreDossier(DossierRepository dossierRepository) {
        this.dossierRepository = dossierRepository;
    }

    /**
     * Liste filtrée d'enfants de dossier : {@code tout} pour le Président/Admin, sinon
     * {@code parDossiers} appliqué aux identifiants des dossiers visibles (liste vide → aucun accès,
     * l'appelant n'exécute même pas la requête).
     *
     * @param tout        requête non filtrée (Président / Administrateur)
     * @param parDossiers requête filtrée sur un ensemble d'{@code ID_DOSSIER}
     */
    public <T> List<T> filtrer(Supplier<List<T>> tout, Function<List<Integer>, List<T>> parDossiers) {
        if (Visibilite.voitTout()) {
            return tout.get();
        }
        List<Integer> ids = idsDossiersVisibles();
        return ids.isEmpty() ? List.of() : parDossiers.apply(ids);
    }

    /** Identifiants des dossiers du périmètre de lecture de l'utilisateur courant (hors Président/Admin). */
    public List<Integer> idsDossiersVisibles() {
        if (Visibilite.estPrmp()) {
            return CurrentUser.ref().filter(s -> !s.isBlank())
                    .map(dossierRepository::findIdsVisiblesPourPrmp).orElseGet(List::of);
        }
        return Visibilite.localite().map(dossierRepository::findIdsVisiblesParLocalite).orElseGet(List::of);
    }

    /** Vrai si le dossier est dans le périmètre de <strong>lecture</strong> de l'utilisateur courant. */
    public boolean estVisible(Integer idDossier) {
        if (Visibilite.voitTout()) {
            return true;
        }
        if (idDossier == null) {
            return false;
        }
        if (Visibilite.estPrmp()) {
            return CurrentUser.ref().filter(s -> !s.isBlank())
                    .map(ref -> dossierRepository.existsVisiblePourPrmp(idDossier, ref)).orElse(false);
        }
        return Visibilite.localite()
                .map(loc -> dossierRepository.existsDansLocalite(idDossier, loc)).orElse(false);
    }

    /**
     * Lève {@link AccessDeniedException} (→ 403) si le dossier parent est hors du périmètre de lecture.
     * Le parent doit avoir été résolu au préalable ; un parent introuvable ({@code null}) est refusé.
     */
    public void controler(Integer idDossier) {
        if (!estVisible(idDossier)) {
            throw new AccessDeniedException(
                    "Ressource hors de votre périmètre de visibilité (§1) : dossier rattaché " + idDossier + ".");
        }
    }
}
