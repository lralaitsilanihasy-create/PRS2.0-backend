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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.SeanceDto;
import cnm.prs.dto.SeanceFinanciereDto;
import cnm.prs.service.SeanceFinanciereService;

/**
 * ⚠️ 2026-10-08 (lot 3 PI, tranche PI-d1, §B3 ; V88) — la seconde séance d'ouverture des prestations intellectuelles : les seules
 * enveloppes financières des propositions qualifiées, les parts des détenteurs, la lecture, la clôture et le PV. Sous le même accès
 * que la première séance ({@code /seance/**}) ; gardes dans le service.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/seance/financiere")
public class SeanceFinanciereController {

    private final SeanceFinanciereService service;

    public SeanceFinanciereController(SeanceFinanciereService service) {
        this.service = service;
    }

    @GetMapping
    public SeanceFinanciereDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** Responsable ; 409 {@code SEANCE_TECHNIQUE_NON_CLOSE}, {@code TECHNIQUE_NON_ARRETEE}, {@code AUCUNE_FINANCIERE_A_OUVRIR}. */
    @PostMapping("/ouvrir")
    public SeanceFinanciereDto ouvrir(@PathVariable Long idDmc) {
        return service.ouvrir(idDmc);
    }

    /** Ses parts chiffrées des enveloppes à ouvrir (membre ; responsable avec {@code ?role=SECOURS}). */
    @GetMapping("/mes-parts")
    public List<SeanceDto.PartChiffree> mesParts(@PathVariable Long idDmc, @RequestParam(required = false) String role) {
        return service.mesParts(idDmc, role);
    }

    /** Toutes ses parts claires en une fois ; au quorum, les enveloppes financières s'ouvrent ensemble. */
    @PostMapping("/parts")
    public SeanceFinanciereDto parts(@PathVariable Long idDmc, @RequestParam(required = false) String role, @RequestBody SeanceDto.Apport corps) {
        return service.apporter(idDmc, role, corps);
    }

    /** {@code { presents[], autres[{ nom, qualite }], observations }} ; responsable ; produit le PV. */
    @PostMapping("/cloturer")
    public SeanceFinanciereDto cloturer(@PathVariable Long idDmc, @RequestBody(required = false) SeanceFinanciereDto.Cloture corps) {
        return service.cloturer(idDmc, corps);
    }

    /** Le PV (PDF ; {@code ?format=docx} pour la version Word). */
    @GetMapping("/pv")
    public ResponseEntity<byte[]> pv(@PathVariable Long idDmc, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition("pv-ouverture-financiere-" + idDmc + (docx ? ".docx" : ".pdf")))
                .contentType(docx ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        : MediaType.APPLICATION_PDF)
                .body(service.pv(idDmc, docx));
    }
}
