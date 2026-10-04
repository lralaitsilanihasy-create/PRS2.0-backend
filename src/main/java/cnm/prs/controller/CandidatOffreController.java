package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.OffreDto;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.security.CurrentUser;
import cnm.prs.service.OffreService;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 3, §B4 ; ADR-0013) — le <strong>dépôt scellé d'une offre</strong>, dans
 * l'espace candidat ({@code /api/candidat/**}, profil {@code CANDIDAT}). Le candidat est celui du jeton : il ne voit et ne touche
 * que ses offres (403 sinon).
 */
@RestController
@RequestMapping("/api/candidat/offres")
@PreAuthorize("hasRole('CANDIDAT')")
public class CandidatOffreController {

    private final OffreService service;

    public CandidatOffreController(OffreService service) {
        this.service = service;
    }

    /** 201 {@code EN_COURS} ; 400 ; 409 {@code PROCEDURE_FERMEE} … {@code OFFRE_EXISTANTE}. */
    @PostMapping
    public ResponseEntity<OffreDto> creer(@RequestBody OffreDto.Creation corps) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(moi(), corps));
    }

    /** Un morceau ({@code application/octet-stream}), rang de 0 à n − 1, en-tête {@code X-Empreinte} (SHA-256 hexadécimal). */
    @PutMapping(value = "/{idOffre}/morceaux/{rang}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public OffreDto.Recu morceau(@PathVariable String idOffre, @PathVariable int rang,
            @RequestHeader(value = "X-Empreinte", required = false) String empreinte, @RequestBody byte[] octets) {
        return service.morceau(moi(), idOffre, rang, empreinte, octets);
    }

    @PostMapping("/{idOffre}/sceller")
    public OffreDto.Accuse sceller(@PathVariable String idOffre, @RequestBody OffreDto.Scellement corps) {
        return service.sceller(moi(), idOffre, corps);
    }

    @GetMapping
    public List<OffreDto> mesOffres() {
        return service.mesOffres(moi());
    }

    @GetMapping("/{idOffre}")
    public OffreDto lire(@PathVariable String idOffre) {
        return service.lire(moi(), idOffre);
    }

    /** Le PDF de l'accusé de réception ; 409 {@code OFFRE_NON_DEPOSEE}. */
    @GetMapping("/{idOffre}/accuse")
    public ResponseEntity<byte[]> accuse(@PathVariable String idOffre) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition("accuse-offre-" + idOffre + ".pdf"))
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.accusePdf(moi(), idOffre));
    }

    /** Retrait : 409 {@code DELAI_DEPASSE} / {@code REMPLACEMENT_INTERDIT} / {@code OFFRE_NON_DEPOSEE}. */
    @DeleteMapping("/{idOffre}")
    public OffreDto retirer(@PathVariable String idOffre) {
        return service.retirer(moi(), idOffre);
    }

    private static String moi() {
        return CurrentUser.ref().orElseThrow(() -> new ResourceNotFoundException("Compte candidat introuvable."));
    }
}
