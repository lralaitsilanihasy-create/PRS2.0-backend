package cnm.prs.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.BonCommandeDto;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.BonCommandeService;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-3 ; V126) — le titulaire : ses bons de commande notifiés ; la première
 * lecture d'un bon vaut accusé de réception.
 */
@RestController
@RequestMapping("/api/candidat/bons-commande")
@PreAuthorize("hasRole('CANDIDAT')")
public class CandidatBonsCommandeController {

    private final BonCommandeService service;

    public CandidatBonsCommandeController(BonCommandeService service) {
        this.service = service;
    }

    @GetMapping
    public List<BonCommandeDto> lister() {
        return service.mesBons(moi());
    }

    /** Le bon, en PDF ou en Word ({@code ?format=docx}) ; sa première lecture vaut accusé de réception ; 404 hors des siens. */
    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable Long id, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return Telechargements.fichier("bon-commande-" + id + (docx ? ".docx" : ".pdf"), docx ? "docx" : "pdf",
                service.documentDuTitulaire(moi(), id, docx));
    }

    private static String moi() {
        return CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
    }
}
