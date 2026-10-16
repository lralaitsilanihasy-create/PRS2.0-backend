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
    private final cnm.prs.service.ReceptionCommandeService receptions;

    public CandidatBonsCommandeController(BonCommandeService service, cnm.prs.service.ReceptionCommandeService receptions) {
        this.service = service;
        this.receptions = receptions;
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

    /** ⚠️ MS-4 (V127) — le titulaire déclare sa livraison : 201 ; 400 {@code DATE_INVALIDE} ; 409 {@code BON_ANNULE}, {@code RECEPTION_EN_COURS}, {@code DEJA_RECU}. */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/livraison")
    public ResponseEntity<cnm.prs.dto.ReceptionCommandeDto.DuBon> livraison(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody(required = false) cnm.prs.dto.ReceptionCommandeDto.LivraisonRequest r) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(receptions.declarerLivraisonTitulaire(moi(), id, r));
    }

    /** ⚠️ MS-4 — les réceptions de son bon. */
    @GetMapping("/{id}/receptions")
    public cnm.prs.dto.ReceptionCommandeDto.DuBon receptions(@PathVariable Long id) {
        return receptions.duBonDuTitulaire(moi(), id);
    }

    /** ⚠️ MS-4 — le PV signé (PDF, ou Word) ; 404 avant la signature. */
    @GetMapping("/{id}/receptions/{idReception}/pv")
    public ResponseEntity<byte[]> pv(@PathVariable Long id, @PathVariable Long idReception, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return Telechargements.fichier("pv-reception-" + idReception + (docx ? ".docx" : ".pdf"), docx ? "docx" : "pdf",
                receptions.pvDuTitulaire(moi(), id, idReception, docx));
    }

    private static String moi() {
        return CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
    }
}
