package cnm.prs.mapper;

import cnm.prs.dto.NotificationDto;
import cnm.prs.entity.Notification;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Notification}.
 */
public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationDto toDto(Notification entity) {
        if (entity == null) {
            return null;
        }
        NotificationDto dto = new NotificationDto();
        dto.setIdNotification(entity.getIdNotification());
        dto.setIdDossier(entity.getIdDossier());
        dto.setTypeNotif(entity.getTypeNotif());
        dto.setDestinataireIm(entity.getDestinataireIm());
        dto.setDestinataireEmail(entity.getDestinataireEmail());
        dto.setTitre(entity.getTitre());
        dto.setCorps(entity.getCorps());
        dto.setDateEnvoi(entity.getDateEnvoi());
        dto.setLu(entity.getLu());
        dto.setDateLecture(entity.getDateLecture());
        dto.setCanal(entity.getCanal());
        dto.setDestinataireRef(entity.getDestinataireRef());
        dto.setDestinataireType(entity.getDestinataireType());
        dto.setIdObjet(entity.getIdObjet());
        dto.setTypeObjet(entity.getTypeObjet());
        dto.setInterimDe(entity.getInterimDe());
        dto.setIdInterim(entity.getIdInterim());
        return dto;
    }

    public static Notification toEntity(NotificationDto dto) {
        if (dto == null) {
            return null;
        }
        Notification entity = new Notification();
        entity.setIdNotification(dto.getIdNotification());
        entity.setIdDossier(dto.getIdDossier());
        entity.setTypeNotif(dto.getTypeNotif());
        entity.setDestinataireIm(dto.getDestinataireIm());
        entity.setDestinataireEmail(dto.getDestinataireEmail());
        entity.setTitre(dto.getTitre());
        entity.setCorps(dto.getCorps());
        entity.setDateEnvoi(dto.getDateEnvoi());
        entity.setLu(dto.getLu());
        entity.setDateLecture(dto.getDateLecture());
        entity.setCanal(dto.getCanal());
        entity.setDestinataireRef(dto.getDestinataireRef());
        entity.setDestinataireType(dto.getDestinataireType());
        entity.setIdObjet(dto.getIdObjet());
        entity.setTypeObjet(dto.getTypeObjet());
        return entity;
    }
}
