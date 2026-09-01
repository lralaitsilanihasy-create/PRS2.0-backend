package cnm.prs.mapper;

import cnm.prs.dto.PvExamenDto;
import cnm.prs.entity.PvExamen;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link PvExamen}.
 */
public final class PvExamenMapper {

    private PvExamenMapper() {
    }

    public static PvExamenDto toDto(PvExamen entity) {
        if (entity == null) {
            return null;
        }
        PvExamenDto dto = new PvExamenDto();
        dto.setIdPv(entity.getIdPv());
        dto.setIdExamen(entity.getIdExamen());
        dto.setIdAvis(entity.getIdAvis());
        dto.setImCtrlPresident(entity.getImCtrlPresident());
        dto.setImCtrlCc(entity.getImCtrlCc());
        dto.setImCtrlMembre(entity.getImCtrlMembre());
        dto.setSyntheseObservations(entity.getSyntheseObservations());
        dto.setStatutPv(entity.getStatutPv());
        dto.setNbNavettes(entity.getNbNavettes());
        dto.setDateSoumissionInitiale(entity.getDateSoumissionInitiale());
        dto.setDateAcceptation(entity.getDateAcceptation());
        dto.setDateSignaturePresident(entity.getDateSignaturePresident());
        dto.setDateSignatureCc(entity.getDateSignatureCc());
        dto.setDateSignatureMembre(entity.getDateSignatureMembre());
        dto.setDatePv(entity.getDatePv());
        dto.setReferencePv(entity.getReferencePv());
        dto.setRefePv(entity.getRefePv());
        dto.setIdSecretaireSeance(entity.getIdSecretaireSeance());
        dto.setImMembreCoSignataire(entity.getImMembreCoSignataire());
        // ⚠️ Visa par intérim (2026-09-01) — dérivés, aucune requête supplémentaire. Le contenu de la
        // note n'est JAMAIS mis dans le DTO : elle se télécharge par son endpoint dédié, dont l'accès
        // est plus étroit que celui du PV (la PRMP en est exclue, décision du 2026-09-01).
        dto.setViseParInterim(Boolean.TRUE.equals(entity.getViseParInterim()));
        dto.setNoteInterimNom(entity.getNoteInterimNom());
        dto.setNoteInterimDisponible(entity.getNoteInterim() != null && entity.getNoteInterim().length > 0);
        dto.setDateArchivage(entity.getDateArchivage());
        dto.setImArchiveur(entity.getImArchiveur());
        dto.setVersion(entity.getVersion());   // ⚠️ verrou optimiste (docs/plan-conflit-version.md)
        return dto;
    }

    public static PvExamen toEntity(PvExamenDto dto) {
        if (dto == null) {
            return null;
        }
        PvExamen entity = new PvExamen();
        entity.setIdPv(dto.getIdPv());
        entity.setIdExamen(dto.getIdExamen());
        entity.setIdAvis(dto.getIdAvis());
        entity.setImCtrlPresident(dto.getImCtrlPresident());
        entity.setImCtrlCc(dto.getImCtrlCc());
        entity.setImCtrlMembre(dto.getImCtrlMembre());
        entity.setSyntheseObservations(dto.getSyntheseObservations());
        entity.setStatutPv(dto.getStatutPv());
        entity.setNbNavettes(dto.getNbNavettes());
        entity.setDateSoumissionInitiale(dto.getDateSoumissionInitiale());
        entity.setDateAcceptation(dto.getDateAcceptation());
        entity.setDateSignaturePresident(dto.getDateSignaturePresident());
        entity.setDateSignatureCc(dto.getDateSignatureCc());
        entity.setDateSignatureMembre(dto.getDateSignatureMembre());
        entity.setDatePv(dto.getDatePv());
        entity.setReferencePv(dto.getReferencePv());
        entity.setIdSecretaireSeance(dto.getIdSecretaireSeance());
        return entity;
    }
}
