package cnm.prs.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.RattachementDto;
import cnm.prs.service.RattachementService;

/**
 * ⚠️ <strong>Rattachements Membre → Vérificateur → Assistant</strong> (arbitrage du pilote, 2026-09-01).
 *
 * <p><strong>Sous-ressource dédiée, et non le PUT générique du contrôleur.</strong>
 * {@code PUT /api/controleurs/{id}} est réservé à l'Administrateur par {@code GESTION_COMPTES_ID} dans
 * {@code SecurityConfig} ; l'ouvrir au Président et au Chef de commission pour qu'ils posent un
 * rattachement leur donnerait du même coup l'écriture sur le nom, l'email, le profil et la localité de
 * n'importe quel contrôleur. Un chemin séparé accorde exactement le droit voulu, et rien de plus.</p>
 *
 * <p>Le {@code @PreAuthorize} n'exprime que le socle (les trois profils) : la restriction fine — le CC
 * dans SA localité — porte sur la <em>localité du porteur</em>, donnée que l'expression ne connaît pas.
 * Elle est donc en service, comme partout ailleurs dans ce dépôt.</p>
 */
@RestController
@RequestMapping("/api/controleurs")
public class RattachementController {

    private final RattachementService service;

    public RattachementController(RattachementService service) {
        this.service = service;
    }

    /**
     * Tableau des rattachements du périmètre de l'acteur — Membres et Vérificateurs, avec leur rattaché
     * résolu. Un {@code imRattache} nul signale une <strong>chaîne incomplète</strong>, état normal et
     * non bloquant (le repli localité s'applique).
     */
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','PRESIDENT','CHEF_COMMISSION')")
    @GetMapping("/rattachements")
    public List<RattachementDto> rattachements() {
        return service.tableau();
    }

    /**
     * Pose ou retire le rattaché d'un contrôleur. Corps : {@code { "imRattache": "…" }} —
     * {@code null} ou absent pour <strong>détacher</strong>.
     *
     * <p>403 : profil hors Admin/Président/CC, ou CC hors de sa localité. 409 : profil du porteur sans
     * chaîne, profil du rattaché incorrect, rattachement inter-localités, auto-rattachement.</p>
     */
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','PRESIDENT','CHEF_COMMISSION')")
    @PutMapping("/{im}/rattachement")
    public RattachementDto definir(@PathVariable String im, @RequestBody(required = false) Corps corps) {
        service.definirRattachement(im, corps == null ? null : corps.imRattache());
        return service.tableau().stream()
                .filter(r -> r.imControleur().equals(im))
                .findFirst()
                .orElseThrow(() -> new cnm.prs.exception.ResourceNotFoundException(
                        "Contrôleur hors de votre périmètre après écriture : " + im));
    }

    /** Corps du PUT — {@code imRattache} nul ou vide détache. */
    public record Corps(String imRattache) {
    }
}
