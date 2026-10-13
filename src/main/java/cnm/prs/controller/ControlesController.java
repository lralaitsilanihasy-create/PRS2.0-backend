package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.ControleResultatDto;
import cnm.prs.service.ControlesService;

/**
 * ⚠️ R5 (manuel révisé pour l'application, lot 1, §B6 ; V105, 2026-10-13) — les résultats des contrôles d'un dossier (automatiques
 * à la soumission, B du Membre), leur synthèse PDF.
 */
@RestController
@RequestMapping("/api/dossiers/{idDossier}/controles")
public class ControlesController {

    private final ControlesService service;

    public ControlesController(ControlesService service) {
        this.service = service;
    }

    /** Les résultats de la soumission {@code ?soumission=n}, ou de la dernière ; même garde que {@code GET /api/dossiers/{id}}. */
    @GetMapping
    public List<ControleResultatDto> lister(@PathVariable Integer idDossier, @RequestParam(required = false) Integer soumission) {
        return service.lister(idDossier, soumission);
    }

    /**
     * Un contrôle B du Membre attributaire (point de grille ou règle, résultat, observation) : 201 ; 400 nominatif ; 403 ; 409. Même
     * habilitation que {@code POST /api/examen-details} (Membre, ou délégation vers Membre).
     */
    @org.springframework.security.access.prepost.PreAuthorize("@perm.peutExercer('MEMBRE')")
    @PostMapping
    public ResponseEntity<ControleResultatDto> ajouter(@PathVariable Integer idDossier, @RequestBody(required = false) ControleResultatDto.Request corps) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ajouter(idDossier, corps));
    }

    /** La synthèse PDF des contrôles d'une soumission (annexe du PV). */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Integer idDossier, @RequestParam(required = false) Integer soumission) {
        return Telechargements.fichier("CONTROLES_" + idDossier + (soumission == null ? "" : "_s" + soumission) + ".pdf", "pdf",
                service.pdf(idDossier, soumission));
    }
}
