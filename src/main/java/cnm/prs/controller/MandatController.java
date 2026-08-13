package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import cnm.prs.dto.AbrogerMandatRequest;
import cnm.prs.dto.CreerMandatRequest;
import cnm.prs.dto.MandatDto;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.MandatService;

/**
 * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — ressource {@code mandats} (table {@code t_mandat}).
 *
 * <p><strong>Écriture réservée à l'Administrateur</strong> : un mandat matérialise un arrêté de nomination,
 * il ne se déclare pas soi-même. La <strong>lecture est ouverte</strong> aux utilisateurs authentifiés, mais
 * une PRMP / UGPM reste cantonnée à son propre périmètre (§3.1).</p>
 *
 * <p>Il n'existe volontairement <strong>ni PUT ni DELETE</strong> : un mandat ne se prolonge pas et ne
 * s'efface pas. On le termine ({@code /abroger}) ou on en crée un nouveau (reconduction).</p>
 */
@RestController
@RequestMapping("/api/mandats")
public class MandatController {

    private final MandatService service;

    public MandatController(MandatService service) {
        this.service = service;
    }

    /**
     * Historique chronologique (statut inclus, calculé à la date du jour).
     * {@code ?ugpm=} est résolu vers la PRMP de tutelle ; {@code ?prmp=} cible directement une PRMP ;
     * sans filtre, une PRMP / UGPM obtient son propre historique, un profil CNM obtient tout.
     */
    @GetMapping
    public List<MandatDto> historique(@RequestParam(required = false) String ugpm,
            @RequestParam(required = false) String prmp) {
        return service.historique(null, porteeAutorisee(ugpm, prmp));
    }

    /**
     * État de vacance du périmètre : <strong>200</strong> avec le mandat en cours, <strong>404</strong>
     * si personne n'est en fonction (« en attente de nomination de la nouvelle PRMP »). C'est le signal que
     * le front interroge pour désactiver les actions de traitement avant même de les tenter.
     */
    @GetMapping("/actif")
    public MandatDto actif(@RequestParam(required = false) String ugpm,
            @RequestParam(required = false) String prmp) {
        String cible = porteeAutorisee(ugpm, prmp);
        if (cible == null) {
            // Sans périmètre, un 404 se lirait à tort comme une vacance : on demande le filtre.
            throw new BadRequestException("Précisez le périmètre : ?ugpm=… ou ?prmp=…");
        }
        return service.mandatActif(null, cible)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun mandat actif : en attente de nomination de la nouvelle PRMP."));
    }

    @GetMapping("/{id}")
    public MandatDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** Nomination ou reconduction (mandat distinct). Un 3ᵉ mandat pour la même personne → 409. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping
    public ResponseEntity<MandatDto> creer(@Valid @RequestBody CreerMandatRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    /** Fin de mandat avant terme. Ouvre la vacance ; ne réattribue aucun dossier. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping("/{id}/abroger")
    public MandatDto abroger(@PathVariable Integer id, @Valid @RequestBody AbrogerMandatRequest req) {
        return service.abroger(id, req);
    }

    /**
     * PRMP cible d'une lecture, une fois les filtres résolus ({@code null} = pas de filtre, réservé aux
     * profils CNM). Une PRMP / UGPM ne lit que son propre périmètre : sans filtre on le lui applique
     * d'office, et tout filtre qui pointe ailleurs — {@code ?prmp=} comme {@code ?ugpm=} — est refusé (403).
     */
    private String porteeAutorisee(String ugpm, String prmp) {
        String cible = service.resoudrePrmp(ugpm, prmp);
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil != ProfilUtilisateur.PRMP && profil != ProfilUtilisateur.UGPM) {
            return cible;
        }
        String sien = CurrentUser.ref().filter(s -> !s.isBlank())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur PRMP non identifié."));
        if (cible == null) {
            return sien;
        }
        if (!sien.equals(cible)) {
            throw new AccessDeniedException("Mandats hors de votre périmètre (§3.1).");
        }
        return cible;
    }
}
