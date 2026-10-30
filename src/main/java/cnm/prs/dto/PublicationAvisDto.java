package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ⚠️ V154 (2026-10-30, demande front « pieces-ddm-donnees », §B2) — la publication de l'avis déposée sur une procédure. {@code nature} :
 * {@code JOURNAL} ou {@code LETTRES_INVITATION} (prestations intellectuelles) ; {@code dateParution} : date de parution du journal, ou
 * d'envoi des lettres.
 */
public record PublicationAvisDto(Long idPublication, Long idDmc, String nature, String journal, String numero, LocalDate dateParution,
        String nomFichier, Long taille, String deposePar, LocalDateTime deposeLe) {
}
