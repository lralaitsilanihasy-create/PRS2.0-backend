package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import cnm.prs.dto.CreerInterimRequest;
import cnm.prs.dto.InterimDto;
import cnm.prs.dto.MesInterimsDto;
import cnm.prs.dto.RevoquerInterimRequest;
import cnm.prs.service.InterimService;

/**
 * ⚠️ Règle ajoutée (demande front du 2026-09-21, « Gestion de l'INTÉRIM », lot 1) — ressource
 * {@code interims} (table {@code t_interim}).
 *
 * <p><strong>Le titulaire désigne lui-même</strong> son intérimaire (Président ou Chef de commission) ;
 * l'Administrateur peut le faire en repli. La <strong>lecture est ouverte aux contrôleurs et à
 * l'Administrateur</strong> ; la PRMP et l'UGPM en sont exclues (vue interne, contrôlé en service, 403).</p>
 *
 * <p>Il n'existe volontairement <strong>ni PUT ni DELETE</strong> : un intérim est un acte daté, l'historique
 * reste. On le clôt avant terme ({@code /revoquer}) ou on en crée un nouveau (prolongation).</p>
 */
@RestController
@RequestMapping("/api/interims")
public class InterimController {

    private static final String LECTURE = "hasAnyRole('PRESIDENT','CHEF_COMMISSION','SECRETAIRE','MEMBRE',"
            + "'VERIFICATEUR','ASSISTANT_CONTROLEUR','CHARGE_PUBLICATION','ADMINISTRATEUR')";
    private static final String ECRITURE = "hasAnyRole('PRESIDENT','CHEF_COMMISSION','ADMINISTRATEUR')";

    private final InterimService service;

    public InterimController(InterimService service) {
        this.service = service;
    }

    /** Historique chronologique ; {@code ?actifs=true} garde les ACTIFS et A_VENIR. */
    @PreAuthorize(LECTURE)
    @GetMapping
    public List<InterimDto> lister(@RequestParam(required = false) String titulaire,
            @RequestParam(required = false) String interimaire,
            @RequestParam(defaultValue = "false") boolean actifs) {
        return service.lister(titulaire, interimaire, actifs);
    }

    /** Le signal du front : ce que le connecté exerce, ce qu'il subit, ce qui vient. */
    @PreAuthorize(LECTURE)
    @GetMapping("/mes")
    public MesInterimsDto mes() {
        return service.mes();
    }

    @PreAuthorize(LECTURE)
    @GetMapping("/{id}")
    public InterimDto findById(@PathVariable Integer id) {
        return service.findById(id);
    }

    /** La pièce PDF de la désignation — même garde que la note d'intérim au visa (403 PRMP/UGPM). */
    @PreAuthorize(LECTURE)
    @GetMapping("/{id}/piece")
    public ResponseEntity<byte[]> piece(@PathVariable Integer id) {
        byte[] pdf = service.piece(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"interim-" + id + ".pdf\"")
                .body(pdf);
    }

    /**
     * Désignation : parties {@code data} ({@link CreerInterimRequest}, JSON) et {@code piece} (PDF, obligatoire).
     * La partie fichier est déclarée facultative pour que son absence produise un <strong>400 métier</strong>
     * lisible plutôt qu'un 400 opaque de Spring — même convention que le visa par intérim.
     */
    @PreAuthorize(ECRITURE)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InterimDto> creer(@Valid @RequestPart("data") CreerInterimRequest req,
            @RequestPart(value = "piece", required = false) MultipartFile piece) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req, piece));
    }

    /** Fin avant terme, à effet immédiat (au plus tôt aujourd'hui). */
    @PreAuthorize(ECRITURE)
    @PostMapping("/{id}/revoquer")
    public InterimDto revoquer(@PathVariable Integer id, @Valid @RequestBody RevoquerInterimRequest req) {
        return service.revoquer(id, req);
    }
}
