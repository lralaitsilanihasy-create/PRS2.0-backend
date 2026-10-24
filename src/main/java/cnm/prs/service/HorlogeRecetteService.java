package cnm.prs.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.dto.HorlogeRecetteDto;
import cnm.prs.exception.BadRequestException;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-24 (demande front « horloge-recette », R1 ; ADR-0016) — régler l'horloge de recette à chaud : avancer entre deux
 * étapes d'un parcours, ou la poser sur une date de la fiche. N'existe qu'avec {@code app.recette.horloge.active=true} (la route
 * répond 404 ailleurs). Le décalage est écrit dans {@code app.recette.horloge.fichier}, relu au démarrage ; chaque réglage est
 * tracé au journal général ({@code HORLOGE_RECETTE}, avec son motif et l'heure réelle).
 */
@Service
@ConditionalOnProperty(name = "app.recette.horloge.active", havingValue = "true")
public class HorlogeRecetteService {

    private final AuditLogService audit;
    private final Path fichier;

    public HorlogeRecetteService(AuditLogService audit,
            @Value("${app.recette.horloge.fichier:${user.home}/prs-horloge-recette.txt}") String fichier) {
        this.audit = audit;
        this.fichier = Path.of(fichier);
    }

    public HorlogeRecetteDto.Etat etat() {
        Duration d = HorlogeRecette.decalage();
        return new HorlogeRecetteDto.Etat(LocalDateTime.now(HorlogeRecette.HORLOGE).truncatedTo(ChronoUnit.SECONDS),
                HorlogeRecette.heureReelle().truncatedTo(ChronoUnit.SECONDS), d.toString(), d.toMinutes(), HorlogeRecette.decalee());
    }

    /** 400 {@code MAINTENANT_OU_AVANCER} (les deux ou aucun), {@code DUREE_INVALIDE}. */
    public HorlogeRecetteDto.Etat regler(HorlogeRecetteDto.Reglage r) {
        boolean parDate = r.maintenant() != null;
        boolean parDuree = r.avancer() != null && !r.avancer().isBlank();
        if (parDate == parDuree) {
            throw new BadRequestException("Donner soit « maintenant » (l'heure voulue), soit « avancer » (une durée ISO-8601).",
                    "MAINTENANT_OU_AVANCER");
        }
        Duration avant = HorlogeRecette.decalage();
        Duration apres;
        if (parDate) {
            // Entre instants, pas entre heures locales : un changement d'heure (heure d'été / d'hiver) entre aujourd'hui et la date
            // voulue décalerait d'une heure.
            apres = Duration.between(Clock.systemDefaultZone().instant(), r.maintenant().atZone(HorlogeRecette.HORLOGE.getZone())
                    .toInstant()).truncatedTo(ChronoUnit.SECONDS);
        } else {
            try {
                apres = avant.plus(Duration.parse(r.avancer().trim()));
            } catch (DateTimeParseException e) {
                throw new BadRequestException("Durée illisible : « " + r.avancer() + " » (ISO-8601 attendu : PT2H, P3D, -PT30M).",
                        "DUREE_INVALIDE");
            }
        }
        return poser(avant, apres, r.motif().trim());
    }

    /** Retour à l'heure réelle. */
    public HorlogeRecetteDto.Etat reinitialiser(String motif) {
        return poser(HorlogeRecette.decalage(), Duration.ZERO, motif == null || motif.isBlank() ? "retour à l'heure réelle" : motif.trim());
    }

    private HorlogeRecetteDto.Etat poser(Duration avant, Duration apres, String motif) {
        HorlogeRecette.regler(apres);
        try {
            Files.writeString(fichier, apres.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            HorlogeRecette.regler(avant);
            throw new UncheckedIOException("Décalage de recette non enregistré dans " + fichier, e);
        }
        audit.enregistrerDetail(CurrentUser.ref().orElse(null), "horloge-recette", "1", "HORLOGE_RECETTE", "decalage",
                avant + " → " + apres + " ; heure du serveur " + LocalDateTime.now(HorlogeRecette.HORLOGE).truncatedTo(ChronoUnit.MINUTES)
                        + " ; motif : " + motif + " ; par " + CurrentUser.login().orElse("?"),
                null);
        return etat();
    }
}
