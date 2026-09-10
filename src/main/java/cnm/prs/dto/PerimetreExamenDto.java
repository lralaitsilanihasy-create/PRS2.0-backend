package cnm.prs.dto;

import java.util.List;

/**
 * ⚠️ <strong>Périmètre d'examen d'un dossier</strong> (demande pilote du 2026-09-10) — ce que
 * l'examinateur doit statuer, et rien de plus.
 *
 * <p>Sur une <strong>mise à jour</strong>, l'examen ne porte que sur ce qui a changé : les lignes
 * nouvelles, modifiées ou restaurées, plus un <em>constat</em> sur chaque ligne retirée. Les lignes
 * inchangées sortent de la complétude <strong>et</strong> du PV. Sur un dossier initial ou en
 * rectification, tout est à examiner et {@code miseAJour} vaut {@code false} : le front n'a pas à
 * distinguer les deux cas, il applique ce qu'on lui sert.</p>
 *
 * <p>⚠️ <strong>C'est la valeur que la garde applique</strong>, pas une dérivation voisine. La complétude
 * de la soumission d'examen s'appuie sur le même calcul : ce qui est annoncé ici est exactement ce qui
 * sera exigé, et le front n'a rien à re-déduire du diff. Les dériver séparément aurait permis d'exiger
 * une ligne annoncée hors périmètre — l'écart ne se voyant qu'au refus de soumettre.</p>
 *
 * @param idDossier        le dossier examiné
 * @param idDossierParent  la version précédente, ou {@code null} si le dossier n'est pas une mise à jour
 * @param miseAJour        vrai si le périmètre est RÉDUIT aux changements
 * @param ficheAExaminer   la fiche de présentation doit-elle être statuée ?
 * @param agpmAExaminer    le projet d'AGPM doit-il être statué ?
 * @param dossierAExaminer les points inter-lignes : <strong>toujours vrai</strong> — un point de portée
 *                         DOSSIER juge l'équilibre du plan entier, que la modification d'une seule ligne
 *                         peut rompre. Servi malgré tout, pour que le front lise un périmètre complet
 *                         plutôt que d'en supposer une partie.
 * @param lignes           le sort de chaque ligne de marché du dossier
 */
public record PerimetreExamenDto(
        Integer idDossier,
        Integer idDossierParent,
        boolean miseAJour,
        boolean ficheAExaminer,
        boolean agpmAExaminer,
        boolean dossierAExaminer,
        List<LignePerimetre> lignes) {

    /**
     * Le sort d'une ligne de marché.
     *
     * @param idDetail        la ligne
     * @param designation     son libellé, pour que le front n'ait pas à re-joindre une liste
     * @param typeChangement  {@code NOUVELLE}, {@code MODIFIEE}, {@code RESTAUREE}, {@code SUPPRIMEE},
     *                        {@code INCHANGEE} — ou {@code null} hors mise à jour, et sur une ligne que le
     *                        diff n'a pas su classer (elle est alors à examiner : on ne dispense jamais
     *                        d'examiner sur un doute)
     * @param aExaminer       tous les points de portée LIGNE sont exigés sur cette ligne
     * @param constatRequis   la ligne est RETIRÉE : seul le constat de suppression est exigé, porté par
     *                        cet {@code idDetail} — la ligne survit dans la version, la copie d'une mise à
     *                        jour conservant les lignes supprimées
     */
    public record LignePerimetre(
            Integer idDetail,
            String designation,
            String typeChangement,
            boolean aExaminer,
            boolean constatRequis) {
    }
}
