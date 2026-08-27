package cnm.prs.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Outils de pagination des listes scopées.
 *
 * <p>⚠️ Audit 2026-08-27 (lot D §3) — {@link #depuisListe} <strong>n'est plus le mécanisme des
 * grandes listes</strong>. Elle chargeait la table scopée ENTIÈRE, la mappait en DTO, puis en
 * découpait une tranche : demander la page 3 de 20 lignes coûtait exactement le même travail serveur
 * que de tout télécharger — le gain n'existait qu'entre le serveur et le navigateur, et disparaissait
 * dès que la table grossissait. {@code /api/dossiers}, {@code /api/ppms} et {@code /api/marches}
 * paginent désormais <strong>en SQL</strong> ({@code LIMIT}/{@code OFFSET} + {@code count}), en
 * réutilisant les mêmes prédicats de périmètre que leur liste plate. {@link #depuisListe} ne sert
 * plus qu'aux listes intrinsèquement petites et bornées (actualités, réservées à l'Administrateur).</p>
 */
final class Pagination {

    private Pagination() {
    }

    /**
     * Pageable servi au repository : même page et même taille que la demande, mais tri
     * <strong>imposé par le serveur</strong> sur la propriété donnée.
     *
     * <p>Le tri porté par le {@code Pageable} du client n'est délibérément <strong>pas</strong>
     * appliqué — c'est le contrat annoncé depuis l'origine de ces endpoints, et l'accepter
     * reviendrait à laisser un paramètre d'URL nommer une propriété d'entité arbitraire. Le tri sur
     * la clé primaire est en revanche <strong>indispensable</strong> : sans {@code ORDER BY},
     * PostgreSQL ne garantit aucun ordre entre deux requêtes, et deux pages successives pourraient
     * se recouvrir ou omettre des lignes.</p>
     */
    static Pageable page(Pageable pageable, String proprieteTri) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(proprieteTri));
    }

    /**
     * Découpe {@code tous} selon {@code pageable} ({@code totalElements} = taille complète filtrée).
     * Réservée aux listes petites et bornées : pour tout le reste, paginer en SQL.
     */
    static <T> Page<T> depuisListe(List<T> tous, Pageable pageable) {
        int debut = (int) Math.min(pageable.getOffset(), tous.size());
        int fin = Math.min(debut + pageable.getPageSize(), tous.size());
        return new PageImpl<>(tous.subList(debut, fin), pageable, tous.size());
    }
}
