package cnm.prs.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.PerimetreExamenDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.enums.TypeChangementLigne;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ChangementLigneRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarcheRepository;

/**
 * ⚠️ <strong>Périmètre d'examen d'une mise à jour</strong> (demande pilote du 2026-09-10) — « n'examiner
 * que les lignes changées ».
 *
 * <p><strong>Le constat.</strong> L'examen d'une mise à jour de PPM réclamait, comme celui d'un plan
 * initial, l'évaluation de <em>chaque</em> point de portée LIGNE sur <em>chaque</em> marché. Sur une
 * version qui ne corrige que trois lignes d'un plan qui en compte soixante, la Commission réexaminait
 * l'intégralité d'un plan qu'elle avait déjà validé — et le PV rendait compte de tout, noyant les
 * corrections dans l'inchangé.</p>
 *
 * <p><strong>Ce que le périmètre retient</strong>, pour un dossier qui a un prédécesseur :</p>
 * <ul>
 *   <li>les lignes <strong>nouvelles, modifiées ou restaurées</strong> : tous les points LIGNE ;</li>
 *   <li>les lignes <strong>retirées</strong> : le seul <em>constat de suppression</em> (portée
 *       {@code SUPPRESSION}), posé sur l'{@code idDetail} de la ligne retirée elle-même — elle survit
 *       dans la version, la copie conservant les lignes supprimées ;</li>
 *   <li>les lignes <strong>inchangées</strong> : rien. Elles sortent de la complétude <em>et</em> du PV ;</li>
 *   <li>la <strong>FICHE</strong> et l'<strong>AGPM</strong> : seulement si une ligne <em>qui les
 *       concerne</em> a changé — sinon le document dérivé est celui qu'on a déjà examiné ;</li>
 *   <li>le <strong>DOSSIER</strong> : toujours. Un point inter-lignes (le fractionnement, par exemple)
 *       porte sur l'équilibre du plan entier, que la modification d'une seule ligne peut rompre.</li>
 * </ul>
 *
 * <p><strong>Un dossier initial, ou en rectification, n'est pas concerné</strong> : sans prédécesseur, il
 * n'y a rien de « déjà examiné », et le périmètre rendu déclare tout à examiner. La règle ne se
 * remarque donc que là où elle s'applique.</p>
 *
 * <p><strong>Une seule dérivation, trois usages</strong> : la complétude s'en sert pour exiger, l'écran
 * d'examen pour afficher, le PV pour n'imprimer que ce qui relève du périmètre. Les calculer séparément
 * aurait permis à l'examinateur de statuer sur une ligne que la garde ignore, ou l'inverse — l'écart ne
 * se voyant qu'en recette.</p>
 */
@Service
@Transactional(readOnly = true)
public class PerimetreExamenService {

    /** Changements qui remettent une ligne à l'examen : elle entre, revient, ou n'est plus la même. */
    private static final Set<TypeChangementLigne> A_REEXAMINER = Set.of(
            TypeChangementLigne.NOUVELLE, TypeChangementLigne.MODIFIEE, TypeChangementLigne.RESTAUREE);

    private final DossierRepository dossierRepository;
    private final MarcheRepository marcheRepository;
    private final ChangementLigneRepository changementLigneRepository;
    private final FicheJustificationsService ficheJustifications;
    private final AgpmService agpmService;

    public PerimetreExamenService(DossierRepository dossierRepository, MarcheRepository marcheRepository,
            ChangementLigneRepository changementLigneRepository, FicheJustificationsService ficheJustifications,
            AgpmService agpmService) {
        this.dossierRepository = dossierRepository;
        this.marcheRepository = marcheRepository;
        this.changementLigneRepository = changementLigneRepository;
        this.ficheJustifications = ficheJustifications;
        this.agpmService = agpmService;
    }

    /**
     * Le périmètre du dossier, tel que la complétude l'exigera et que le PV le restituera.
     *
     * @throws ResourceNotFoundException si le dossier n'existe pas
     */
    public PerimetreExamenDto perimetre(Integer idDossier) {
        Dossier dossier = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        boolean miseAJour = dossier.getIdDossierParent() != null;
        Map<Integer, TypeChangementLigne> changements = miseAJour
                ? typesParOrigine(idDossier) : Map.of();

        List<PerimetreExamenDto.LignePerimetre> lignes = new ArrayList<>();
        boolean uneLigneDeFicheAChange = false;
        boolean uneLigneDAgpmAChange = false;
        Set<Integer> lignesDeLaFiche = miseAJour ? ficheJustifications.lignesDeLaFiche(idDossier) : Set.of();
        Set<Integer> lignesDeLAgpm = miseAJour ? agpmService.lignesDeclenchantAgpm(idDossier) : Set.of();

        for (Marche m : marcheRepository.findByIdDossier(idDossier)) {
            boolean retiree = Boolean.TRUE.equals(m.getSupprimee());
            // La trace est indexée par l'IDENTITÉ INTER-VERSIONS de la ligne, pas par sa clé technique :
            // c'est elle qui relie une ligne à son ancêtre d'une version à l'autre.
            TypeChangementLigne type = changements.get(m.getIdLigneOrigine());
            // Hors mise à jour : tout le plan est à examiner, et rien n'est « retiré » au sens d'une
            // version — une ligne supprimée d'un brouillon est effacée, pas conservée.
            boolean aExaminer = !miseAJour ? !retiree : (!retiree && (type == null || A_REEXAMINER.contains(type)));
            boolean constatRequis = miseAJour && retiree;
            lignes.add(new PerimetreExamenDto.LignePerimetre(m.getIdDetail(), m.getDesignationMarche(),
                    type == null ? null : type.name(), aExaminer, constatRequis));
            if (aExaminer || constatRequis) {
                uneLigneDeFicheAChange |= lignesDeLaFiche.contains(m.getIdDetail());
                uneLigneDAgpmAChange |= lignesDeLAgpm.contains(m.getIdDetail());
            }
        }

        // « On ne contrôle pas le vide » (2026-09-04) reste la première borne : un document sans contenu
        // n'est jamais exigé, mise à jour ou pas. Le périmètre ne fait que RÉDUIRE, jamais élargir.
        boolean ficheAExaminer = !ficheJustifications.ficheVide(idDossier)
                && (!miseAJour || uneLigneDeFicheAChange);
        boolean agpmAExaminer = agpmService.requisPourDossier(idDossier)
                && (!miseAJour || uneLigneDAgpmAChange);

        return new PerimetreExamenDto(idDossier, dossier.getIdDossierParent(), miseAJour,
                ficheAExaminer, agpmAExaminer, true, lignes);
    }

    /**
     * ⚠️ <strong>Le type de changement de chaque ligne, lu sur la TRACE FIGÉE</strong> —
     * {@code idLigneOrigine → type}.
     *
     * <p><strong>Pourquoi la trace et non un recalcul.</strong> Le diff d'une version est
     * <em>recalculé</em> tant qu'elle est un brouillon, puis <strong>figé</strong> à sa soumission, et
     * c'est la trace qui fait foi ensuite. Or l'examen vient <em>après</em> la soumission : au moment où
     * ce périmètre est consulté, la trace existe toujours. La lire plutôt que refaire la comparaison
     * évite d'introduire une <strong>seconde définition</strong> de « cette ligne a changé » — et évite
     * qu'un périmètre calculé aujourd'hui contredise le diff figé hier, que le front affiche.</p>
     *
     * <p><strong>Sans trace, on n'allège rien</strong> : la table est vide, aucune ligne n'est classée,
     * et tout le plan reste à examiner. C'est le cas d'une version encore en brouillon — qui n'est de
     * toute façon pas examinée — et le bon repli : on ne dispense jamais d'examiner sur un silence.</p>
     */
    private Map<Integer, TypeChangementLigne> typesParOrigine(Integer idDossier) {
        Map<Integer, TypeChangementLigne> types = new LinkedHashMap<>();
        for (cnm.prs.entity.ChangementLigne trace
                : changementLigneRepository.findByIdDossierOrderByIdChangementAsc(idDossier)) {
            if (trace.getIdLigneOrigine() == null || trace.getTypeChangement() == null) {
                continue;
            }
            try {
                types.putIfAbsent(trace.getIdLigneOrigine(),
                        TypeChangementLigne.valueOf(trace.getTypeChangement()));
            } catch (IllegalArgumentException ex) {
                // Type inconnu (trace d'une version antérieure au vocabulaire courant) : la ligne reste
                // hors classement, donc examinée comme le reste.
                continue;
            }
        }
        return types;
    }

    /** Index {@code idDetail → ligne du périmètre}, pour les appelants qui interrogent ligne à ligne. */
    public Map<Integer, PerimetreExamenDto.LignePerimetre> parLigne(Integer idDossier) {
        Map<Integer, PerimetreExamenDto.LignePerimetre> index = new LinkedHashMap<>();
        for (PerimetreExamenDto.LignePerimetre l : perimetre(idDossier).lignes()) {
            index.put(l.idDetail(), l);
        }
        return index;
    }
}
