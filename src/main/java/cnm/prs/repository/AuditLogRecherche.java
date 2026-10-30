package cnm.prs.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import cnm.prs.entity.AuditLog;

import jakarta.persistence.criteria.Predicate;

/**
 * ⚠️ 2026-10-30 (demande front « performance », §B4) — la recherche du journal d'audit, construite selon les filtres présents :
 * un filtre absent ne figure pas dans la requête, si bien que PostgreSQL peut s'appuyer sur les index de V153
 * ({@code DATE_ACTION} ; {@code NOM_TABLE, DATE_ACTION}). Mêmes règles qu'avant : tables en OU (liste vide = toutes), acteur exact,
 * bornes de date incluses.
 */
public final class AuditLogRecherche {

    private AuditLogRecherche() {
    }

    public static Specification<AuditLog> filtres(Collection<String> nomTables, String acteur, LocalDateTime debut, LocalDateTime fin) {
        return (racine, requete, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (nomTables != null && !nomTables.isEmpty()) {
                conditions.add(nomTables.size() == 1 ? cb.equal(racine.get("nomTable"), nomTables.iterator().next())
                        : racine.get("nomTable").in(nomTables));
            }
            if (acteur != null) {
                conditions.add(cb.equal(racine.get("imActeur"), acteur));
            }
            if (debut != null) {
                conditions.add(cb.greaterThanOrEqualTo(racine.get("dateAction"), debut));
            }
            if (fin != null) {
                conditions.add(cb.lessThanOrEqualTo(racine.get("dateAction"), fin));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }
}
