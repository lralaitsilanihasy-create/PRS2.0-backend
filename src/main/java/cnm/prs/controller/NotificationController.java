package cnm.prs.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.NotificationDto;
import cnm.prs.service.NotificationService;

/**
 * Notifications (table {@code t_notification}).
 *
 * <p><strong>Mes notifications</strong> ({@code /mes}, {@code /mes/non-lues/count},
 * {@code /{id}/lu}, {@code /lire-tout}) sont scopées à l'utilisateur courant. La <strong>liste
 * globale</strong> et le CRUD sont réservés à l'<strong>Administrateur</strong> (supervision).</p>
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final cnm.prs.service.NotificationStreamRegistry streamRegistry;

    public NotificationController(NotificationService service,
            cnm.prs.service.NotificationStreamRegistry streamRegistry) {
        this.service = service;
        this.streamRegistry = streamRegistry;
    }

    // --- Mes notifications (scopées à l'utilisateur courant) ---

    @GetMapping("/mes")
    public List<NotificationDto> mes(@RequestParam(required = false) Boolean lu) {
        return service.mes(lu);
    }

    @GetMapping("/mes/non-lues/count")
    public Map<String, Long> compterNonLues() {
        return Map.of("nonLues", service.compterNonLues());
    }

    @PostMapping("/{id}/lu")
    public NotificationDto marquerLu(@PathVariable Integer id) {
        return service.marquerLu(id);
    }

    /** ⚠️ Spec notifications (2026-08-02) — marquage manuel NON LU (unitaire). */
    @PostMapping("/{id}/non-lu")
    public NotificationDto marquerNonLu(@PathVariable Integer id) {
        return service.marquerNonLu(id);
    }

    @PostMapping("/lire-tout")
    public Map<String, Integer> lireTout() {
        return Map.of("traitees", service.marquerToutLu());
    }

    /**
     * ⚠️ Spec notifications (2026-08-02) — flux SSE « mes notifications » : un événement {@code maj}
     * est poussé à chaque émission/lecture concernant l'utilisateur courant ; le front recharge alors
     * le compteur serveur. Repli automatique côté front : polling périodique.
     */
    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream() {
        String ref = cnm.prs.security.CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Utilisateur non identifié."));
        return streamRegistry.subscribe(ref);
    }

    // --- Supervision (Administrateur) ---

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public List<NotificationDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public NotificationDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<NotificationDto> create(@Valid @RequestBody NotificationDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public NotificationDto update(@PathVariable Integer id, @Valid @RequestBody NotificationDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
