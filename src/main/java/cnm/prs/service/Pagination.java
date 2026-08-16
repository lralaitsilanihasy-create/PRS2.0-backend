package cnm.prs.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * ⚠️ Audit front (2026-08-16) — pagination APPLICATIVE des listes volumineuses ({@code /api/dossiers},
 * {@code /api/ppms}, {@code /api/marches}) : la liste est d'abord constituée avec les filtres de
 * périmètre habituels (visibilité par localité / PRMP propriétaire, filtres métier), puis découpée en
 * page. Le gain visé est le transfert et le parse côté front (plus de téléchargement de tables
 * entières) ; l'ordre servi est celui de la liste non paginée (le tri du {@code Pageable} n'est pas
 * appliqué). Enveloppe {@code Page} de Spring Data — même forme que {@code /api/dossiers/examines}.
 */
final class Pagination {

    private Pagination() {
    }

    /** Découpe {@code tous} selon {@code pageable} ({@code totalElements} = taille complète filtrée). */
    static <T> Page<T> depuisListe(List<T> tous, Pageable pageable) {
        int debut = (int) Math.min(pageable.getOffset(), tous.size());
        int fin = Math.min(debut + pageable.getPageSize(), tous.size());
        return new PageImpl<>(tous.subList(debut, fin), pageable, tous.size());
    }
}
