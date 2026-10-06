package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.RecuDto;
import cnm.prs.entity.RecuDao;
import cnm.prs.service.RecusDaoService;

/**
 * ⚠️ 2026-10-06 (demande front « le retrait du DAO après paiement des frais », §B3) — les <strong>reçus des frais de dossier</strong>
 * d'une procédure, vus et décidés par la PRMP et l'UGPM de la fiche (gardes dans le service).
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/recus")
public class RecusDaoController {

    private final RecusDaoService service;

    public RecusDaoController(RecusDaoService service) {
        this.service = service;
    }

    /** Les {@code EN_ATTENTE} d'abord, puis du plus récent au plus ancien. */
    @GetMapping
    public List<RecuDto> lister(@PathVariable Long idDmc) {
        return service.lister(idDmc);
    }

    @GetMapping("/{idRecu}/fichier")
    public ResponseEntity<byte[]> fichier(@PathVariable Long idDmc, @PathVariable Integer idRecu) {
        RecuDao r = service.fichier(idDmc, idRecu);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition(r.getNomFichier() == null ? "recu" : r.getNomFichier()))
                .contentType(MediaType.parseMediaType(r.getFormat())).body(r.getContenu());
    }

    /** 409 {@code RECU_DEJA_DECIDE}. */
    @PostMapping("/{idRecu}/valider")
    public RecuDto valider(@PathVariable Long idDmc, @PathVariable Integer idRecu) {
        return service.valider(idDmc, idRecu);
    }

    /** 400 {@code MOTIF_ABSENT} ; 409 {@code RECU_DEJA_DECIDE}. */
    @PostMapping("/{idRecu}/refuser")
    public RecuDto refuser(@PathVariable Long idDmc, @PathVariable Integer idRecu, @RequestBody(required = false) RecuDto.Refus corps) {
        return service.refuser(idDmc, idRecu, corps);
    }
}
