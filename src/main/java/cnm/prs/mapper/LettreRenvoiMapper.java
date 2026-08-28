package cnm.prs.mapper;

import cnm.prs.dto.LettreRenvoiDto;
import cnm.prs.entity.LettreRenvoi;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link LettreRenvoi}.
 */
public final class LettreRenvoiMapper {

    private LettreRenvoiMapper() {
    }

    public static LettreRenvoiDto toDto(LettreRenvoi entity) {
        if (entity == null) {
            return null;
        }
        LettreRenvoiDto dto = new LettreRenvoiDto();
        dto.setIdLettre(entity.getIdLettre());
        dto.setIdExamen(entity.getIdExamen());
        dto.setIdDossier(entity.getIdDossier());
        dto.setRefLettre(entity.getRefLettre());
        dto.setCorpsLettre(entity.getCorpsLettre());
        dto.setDateExamen(entity.getDateExamen());
        dto.setDateLettre(entity.getDateLettre());
        dto.setStatut(entity.getStatut());
        dto.setImSignataire(entity.getImSignataire());
        dto.setDateArchivage(entity.getDateArchivage());
        dto.setImArchiveur(entity.getImArchiveur());
        dto.setVersion(entity.getVersion());   // ⚠️ verrou optimiste (docs/plan-conflit-version.md)
        // ⚠️ 2026-08-28 — le PDF est produit APRÈS COMMIT de la signature : entre les deux, la lettre est
        // SIGNE sans document. Le front doit pouvoir distinguer « pas encore prêt » de « pas de document »,
        // sinon il propose un téléchargement qui part en 404. Dérivé, aucune requête supplémentaire.
        dto.setDocumentDisponible(
                (entity.getCheminDocument() != null && !entity.getCheminDocument().isBlank())
                        || (entity.getDocumentPdf() != null && entity.getDocumentPdf().length > 0));
        return dto;
    }
}
