package cnm.prs.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.dto.ReceptionDto;
import cnm.prs.entity.Reception;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Reception}.
 */
public final class ReceptionMapper {

    /** Format d'échange des dates/heures de réception : {@code yyyy-MM-dd HH:mm}. */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ReceptionMapper() {
    }

    /** {@link LocalDateTime} &rarr; {@code yyyy-MM-dd HH:mm} (ou {@code null}). */
    public static String format(LocalDateTime dt) {
        return dt == null ? null : dt.format(FMT);
    }

    /**
     * Texte &rarr; {@link LocalDateTime} ({@code null}/vide &rarr; {@code null}). Accepte une
     * <strong>date seule</strong> {@code yyyy-MM-dd} (envoyée par le formulaire de réception) en
     * <strong>complétant l'heure manquante</strong> avec l'heure courante du serveur, ou une date-heure
     * complète {@code yyyy-MM-dd HH:mm}. {@code t_reception.DATE_RECEPTION} est un TIMESTAMP, on conserve donc l'heure.
     */
    public static LocalDateTime toLocalDateTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        // Date seule (« yyyy-MM-dd », 10 caractères) → compléter avec l'heure courante (le formulaire n'envoie pas l'heure).
        if (t.length() <= 10) {
            return LocalDate.parse(t).atTime(LocalTime.now(HorlogeRecette.HORLOGE));
        }
        return LocalDateTime.parse(t, FMT);
    }

    public static ReceptionDto toDto(Reception entity) {
        if (entity == null) {
            return null;
        }
        ReceptionDto dto = new ReceptionDto();
        dto.setIdReception(entity.getIdReception());
        dto.setIdDossier(entity.getIdDossier());
        dto.setNumPassage(entity.getNumPassage());
        dto.setTypePassage(entity.getTypePassage());
        dto.setImCtrlRecept(entity.getImCtrlRecept());
        dto.setDateReception(format(entity.getDateReception()));
        dto.setObservation(entity.getObservation());
        dto.setComplet(entity.getComplet());
        dto.setIdReceptionPrec(entity.getIdReceptionPrec());
        dto.setReference(entity.getReference());   // snapshot immuable persisté (lecture seule)
        return dto;
    }

    public static Reception toEntity(ReceptionDto dto) {
        if (dto == null) {
            return null;
        }
        Reception entity = new Reception();
        entity.setIdReception(dto.getIdReception());
        entity.setIdDossier(dto.getIdDossier());
        entity.setNumPassage(dto.getNumPassage());
        entity.setTypePassage(dto.getTypePassage());
        entity.setImCtrlRecept(dto.getImCtrlRecept());
        entity.setDateReception(toLocalDateTime(dto.getDateReception()));
        entity.setObservation(dto.getObservation());
        entity.setComplet(dto.getComplet());
        entity.setIdReceptionPrec(dto.getIdReceptionPrec());
        return entity;
    }
}
