package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.AmiDto;
import cnm.prs.entity.AmiExpressionPiece;
import cnm.prs.service.AmiService;

/**
 * ⚠️ 2026-10-07 (AMI en ligne, tranche AMI-a, §B1, §B2 ; V82) — l'appel à manifestation d'intérêt d'une procédure de prestations
 * intellectuelles, côté administration : préparation, avis, publication ou dispense, lecture des expressions après la date limite.
 * Gardes dans le service.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/ami")
public class AmiController {

    private final AmiService service;

    public AmiController(AmiService service) {
        this.service = service;
    }

    @GetMapping
    public AmiDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** Crée ou modifie l'AMI en préparation ; 409 {@code CATEGORIE_SANS_AMI}, {@code AMI_PUBLIE}, {@code AMI_DISPENSE}. */
    @PutMapping
    public AmiDto preparer(@PathVariable Long idDmc, @RequestBody(required = false) AmiDto.AmiRequest r) {
        return service.preparer(idDmc, r);
    }

    /** L'avis (le projet tant que l'AMI n'est pas publié), en PDF ou en Word ({@code ?format=docx}). */
    @GetMapping("/avis")
    public ResponseEntity<byte[]> avis(@PathVariable Long idDmc, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return reponse(idDmc, docx, service.avis(idDmc, docx));
    }

    /** {@code { publications[{ support, date, reference? }] }} ; 400 {@code PUBLICATION_OBLIGATOIRE} ; 409 {@code AMI_PUBLIE}. */
    @PostMapping("/publier")
    public AmiDto publier(@PathVariable Long idDmc, @RequestBody(required = false) AmiDto.PublicationRequest r) {
        return service.publier(idDmc, r);
    }

    /** {@code { motif }} ; 409 {@code AMI_PUBLIE}. */
    @PostMapping("/dispense")
    public AmiDto dispenser(@PathVariable Long idDmc, @RequestBody(required = false) AmiDto.DispenseRequest r) {
        return service.dispenser(idDmc, r);
    }

    /** Les expressions d'intérêt déposées ; 409 {@code LECTURE_FERMEE} avant la date limite. */
    @GetMapping("/expressions")
    public List<AmiDto.Expression> expressions(@PathVariable Long idDmc) {
        return service.expressionsDeposees(idDmc);
    }

    @GetMapping("/expressions/{idExpression}/pieces/{idPiece}")
    public ResponseEntity<byte[]> piece(@PathVariable Long idDmc, @PathVariable String idExpression, @PathVariable Long idPiece) {
        AmiExpressionPiece p = service.piece(idDmc, idExpression, idPiece);
        return Telechargements.fichier(p.getNom(), p.getFormat(), p.getContenu());
    }

    static ResponseEntity<byte[]> reponse(Long idDmc, boolean docx, byte[] contenu) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition("avis-ami-" + idDmc + (docx ? ".docx" : ".pdf")))
                .contentType(docx ? Telechargements.DOCX : MediaType.APPLICATION_PDF).body(contenu);
    }
}
