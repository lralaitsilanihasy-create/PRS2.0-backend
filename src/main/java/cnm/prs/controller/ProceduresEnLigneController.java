package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.ProcedureEnLigneDto;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.ProceduresEnLigneService;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1c, §B8) — les <strong>procédures ouvertes en ligne</strong> :
 * liste et détail publics (sans session, règle de {@code SecurityConfig}) ; documents du DAO réservés au profil
 * {@code CANDIDAT}, chaque téléchargement inscrit au registre des retraits.
 */
@RestController
@RequestMapping("/api/procedures-en-ligne")
public class ProceduresEnLigneController {

    private final ProceduresEnLigneService service;

    public ProceduresEnLigneController(ProceduresEnLigneService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProcedureEnLigneDto> lister() {
        return service.lister();
    }

    /** 404 hors des critères de la liste ; une procédure close reste lisible ({@code etat = CLOSE}). */
    @GetMapping("/{idDmc}")
    public ProcedureEnLigneDto procedure(@PathVariable Long idDmc) {
        return service.procedure(idDmc);
    }

    @GetMapping("/{idDmc}/documents")
    @PreAuthorize("hasRole('CANDIDAT')")
    public List<ProcedureEnLigneDto.Document> documents(@PathVariable Long idDmc) {
        return service.documents(idDmc);
    }

    /** Le document ({@code code} = son nom de fichier) ; le retrait est journalisé. 404 procédure ou document inconnu. */
    @GetMapping("/{idDmc}/documents/{code}")
    @PreAuthorize("hasRole('CANDIDAT')")
    public ResponseEntity<byte[]> document(@PathVariable Long idDmc, @PathVariable String code) {
        String moi = CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
        DocumentFicheMarche d = service.retirer(idDmc, code, moi);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition(d.getNomFichier()))
                .contentType(Telechargements.typeAutorise(d.getExtension()))
                .body(d.getContenu());
    }
}
