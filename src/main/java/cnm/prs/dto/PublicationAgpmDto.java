package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ⚠️ V118 (2026-10-15, demande front « pièces de l'AMI et publication de l'AGPM », §B2) — une publication de l'AGPM déposée sur un plan.
 * {@code idDossierPlan} : la version du plan qui la porte ; {@code heritee} : vrai quand elle est lue depuis une version plus récente du
 * plan (H2, filiation) ; {@code enVigueur} : faux pour une publication remplacée.
 */
public record PublicationAgpmDto(Long idPublication, Integer idDossierPlan, String journal, String numero, LocalDate dateParution,
        String referenceAgpm, String nomFichier, Long taille, String deposePar, LocalDateTime deposeLe, LocalDateTime remplaceeLe,
        boolean enVigueur, boolean heritee) {
}
