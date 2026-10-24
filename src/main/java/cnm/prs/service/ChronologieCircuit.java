package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.exception.BadRequestException;

/**
 * ⚠️ 2026-10-24 (demande front « heure-serveur », H2) — la date saisie d'une étape du circuit CNM suit l'étape précédente et ne
 * dépasse pas l'heure du serveur : réception après la soumission du dossier, dispatch après la réception, examen après le dispatch.
 * Comparaison à la minute (les formulaires saisissent « yyyy-MM-dd HH:mm »), au jour pour l'examen (une date seule). Une date
 * future est tolérée de {@link #MARGE_FUTUR_MINUTES} minutes (le poste de l'agent n'est pas à la seconde près). Pur, hormis
 * l'heure du serveur ({@link HorlogeRecette#HORLOGE}, celle qui complète une date sans heure).
 */
public final class ChronologieCircuit {

    static final int MARGE_FUTUR_MINUTES = 5;

    private ChronologieCircuit() {
    }

    /**
     * 400 {@code DATE_ANTERIEURE} si {@code date} précède {@code precedente} ; 400 {@code DATE_FUTURE} si elle dépasse l'heure du
     * serveur. Rien si {@code date} est nulle ; pas de garde d'antériorité si {@code precedente} est nulle (dossier ancien).
     */
    public static void exiger(String etape, LocalDateTime date, String etapePrecedente, LocalDateTime precedente) {
        if (date == null) {
            return;
        }
        LocalDateTime d = date.truncatedTo(ChronoUnit.MINUTES);
        if (precedente != null && d.isBefore(precedente.truncatedTo(ChronoUnit.MINUTES))) {
            throw anterieure(etape, date.toString(), etapePrecedente, precedente.truncatedTo(ChronoUnit.MINUTES).toString());
        }
        LocalDateTime maintenant = LocalDateTime.now(HorlogeRecette.HORLOGE).truncatedTo(ChronoUnit.MINUTES);
        if (d.isAfter(maintenant.plusMinutes(MARGE_FUTUR_MINUTES))) {
            throw future(etape, date.toString(), maintenant.toString());
        }
    }

    /** La même garde au jour (l'examen porte une date seule). */
    public static void exiger(String etape, LocalDate date, String etapePrecedente, LocalDateTime precedente) {
        if (date == null) {
            return;
        }
        if (precedente != null && date.isBefore(precedente.toLocalDate())) {
            throw anterieure(etape, date.toString(), etapePrecedente, precedente.truncatedTo(ChronoUnit.MINUTES).toString());
        }
        LocalDate aujourdhui = LocalDate.now(HorlogeRecette.HORLOGE);
        if (date.isAfter(aujourdhui)) {
            throw future(etape, date.toString(), LocalDateTime.now(HorlogeRecette.HORLOGE).truncatedTo(ChronoUnit.MINUTES).toString());
        }
    }

    /**
     * La date saisie ({@code saisie}, lue en {@code lue}) diffère-t-elle de l'enregistrée ? Une date seule ne compare que le jour : le
     * serveur en complète l'heure, qui changerait à chaque correction. Sert aux {@code PUT} : une date inchangée n'est pas regardée.
     */
    public static boolean changee(String saisie, LocalDateTime lue, LocalDateTime enregistree) {
        if (saisie == null || saisie.isBlank() || lue == null) {
            return false;
        }
        if (enregistree == null) {
            return true;
        }
        return saisie.trim().length() <= 10 ? !lue.toLocalDate().equals(enregistree.toLocalDate())
                : !lue.truncatedTo(ChronoUnit.MINUTES).equals(enregistree.truncatedTo(ChronoUnit.MINUTES));
    }

    private static BadRequestException anterieure(String etape, String date, String etapePrecedente, String precedente) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("etape", etape);
        details.put("date", date);
        details.put("etapePrecedente", etapePrecedente);
        details.put("datePrecedente", precedente);
        return new BadRequestException("La date de " + libelle(etape) + " (" + date + ") précède celle de " + libelle(etapePrecedente)
                + " (" + precedente + ").", "DATE_ANTERIEURE", details);
    }

    private static BadRequestException future(String etape, String date, String maintenant) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("etape", etape);
        details.put("date", date);
        details.put("maintenant", maintenant);
        return new BadRequestException("La date de " + libelle(etape) + " (" + date + ") est postérieure à l'heure du serveur ("
                + maintenant + ").", "DATE_FUTURE", details);
    }

    private static String libelle(String etape) {
        return switch (etape) {
            case "SOUMISSION" -> "soumission du dossier";
            case "RECEPTION" -> "réception";
            case "DISPATCH" -> "dispatch";
            case "EXAMEN" -> "l'examen";
            default -> etape;
        };
    }
}
