package cnm.prs.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import cnm.prs.dto.DispatchDto;
import cnm.prs.entity.Dispatch;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Dispatch}.
 */
public final class DispatchMapper {

    /** Format d'échange de la date/heure de dispatch : {@code yyyy-MM-dd HH:mm}. */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DispatchMapper() {
    }

    /** {@link LocalDateTime} &rarr; {@code yyyy-MM-dd HH:mm} (ou {@code null}). */
    public static String format(LocalDateTime dt) {
        return dt == null ? null : dt.format(FMT);
    }

    /**
     * Texte &rarr; {@link LocalDateTime} ({@code null}/vide &rarr; {@code null}). Accepte une
     * <strong>date seule</strong> {@code yyyy-MM-dd} (envoyée par un {@code <input type="date">}) en
     * <strong>complétant l'heure manquante</strong> avec l'heure courante du serveur, ou une date-heure
     * complète {@code yyyy-MM-dd HH:mm}. {@code t_dispatch.DATE_DISPATCH} est un TIMESTAMP, on conserve l'heure.
     */
    public static LocalDateTime toLocalDateTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        // Date seule (« yyyy-MM-dd », 10 caractères) → compléter avec l'heure courante (l'input HTML n'envoie pas l'heure).
        if (t.length() <= 10) {
            return LocalDate.parse(t).atTime(LocalTime.now());
        }
        return LocalDateTime.parse(t, FMT);
    }

    public static DispatchDto toDto(Dispatch entity) {
        if (entity == null) {
            return null;
        }
        DispatchDto dto = new DispatchDto();
        dto.setIdDispatch(entity.getIdDispatch());
        dto.setIdReception(entity.getIdReception());
        dto.setImCtrlDispatch(entity.getImCtrlDispatch());
        dto.setImCtrlCc(entity.getImCtrlCc());
        dto.setImCtrlMembre(entity.getImCtrlMembre());
        dto.setDateDispatch(format(entity.getDateDispatch()));
        dto.setDateCtrlAssigne(entity.getDateCtrlAssigne());
        dto.setInstructions(entity.getInstructions());
        dto.setInterimDispatch(entity.getInterimDispatch());
        // ⚠️ Intérim désigné (2026-09-21) — lecture seule : le titulaire suppléé EST le dispatcheur enregistré.
        dto.setIdInterim(entity.getIdInterim());
        dto.setInterimDe(entity.getIdInterim() == null ? null : entity.getImCtrlDispatch());
        return dto;
    }

    public static Dispatch toEntity(DispatchDto dto) {
        if (dto == null) {
            return null;
        }
        Dispatch entity = new Dispatch();
        entity.setIdDispatch(dto.getIdDispatch());
        entity.setIdReception(dto.getIdReception());
        entity.setImCtrlDispatch(dto.getImCtrlDispatch());
        entity.setImCtrlCc(dto.getImCtrlCc());
        entity.setImCtrlMembre(dto.getImCtrlMembre());
        entity.setDateDispatch(toLocalDateTime(dto.getDateDispatch()));
        entity.setDateCtrlAssigne(dto.getDateCtrlAssigne());
        entity.setInstructions(dto.getInstructions());
        entity.setInterimDispatch(dto.getInterimDispatch());
        return entity;
    }
}
