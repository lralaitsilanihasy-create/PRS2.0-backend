package cnm.prs.controller;

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

import cnm.prs.dto.PreselectionDto;
import cnm.prs.service.AmiPreselectionService;

/**
 * ⚠️ 2026-10-07 (AMI en ligne, tranche AMI-b, §B3 ; V83) — la présélection par la commission d'appel d'offres : déclarations, notes,
 * écartements, arrêt de la liste restreinte par le président, rapport et signatures ; relance et infructuosité par la PRMP. Chaque geste
 * répond la {@code PreselectionDto} à jour. Gardes dans le service.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/ami")
public class AmiPreselectionController {

    private final AmiPreselectionService service;

    public AmiPreselectionController(AmiPreselectionService service) {
        this.service = service;
    }

    @GetMapping("/preselection")
    public PreselectionDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** {@code { conflit, precision? }} ; 409 {@code DEJA_DECLARE}. */
    @PostMapping("/preselection/declaration")
    public PreselectionDto declarer(@PathVariable Long idDmc, @RequestBody(required = false) PreselectionDto.DeclarationRequest r) {
        return service.declarer(idDmc, r);
    }

    /** {@code { notes[{ code, note, motif }] }} ; 400 {@code CRITERE_INCONNU}, {@code NOTE_HORS_BAREME}, {@code MOTIF_OBLIGATOIRE}. */
    @PutMapping("/expressions/{idExpression}/notes")
    public PreselectionDto noter(@PathVariable Long idDmc, @PathVariable String idExpression,
            @RequestBody(required = false) PreselectionDto.NotesRequest r) {
        return service.noter(idDmc, idExpression, r);
    }

    /** {@code { ecartee, motif }} ; {@code ecartee} faux rétablit l'expression. */
    @PostMapping("/expressions/{idExpression}/ecartement")
    public PreselectionDto ecarter(@PathVariable Long idDmc, @PathVariable String idExpression,
            @RequestBody(required = false) PreselectionDto.EcartementRequest r) {
        return service.ecarter(idDmc, idExpression, r);
    }

    /** {@code { motifNombre?, observations?, ordre? }} ; 409 {@code NOTATION_INCOMPLETE}, {@code AUCUN_QUALIFIE}, {@code EGALITE_A_DEPARTAGER}. */
    @PostMapping("/preselection/arreter")
    public PreselectionDto arreter(@PathVariable Long idDmc, @RequestBody(required = false) PreselectionDto.ArretRequest r) {
        return service.arreter(idDmc, r);
    }

    /** Le rapport de présélection, en PDF ou en Word ({@code ?format=docx}). */
    @GetMapping("/rapport")
    public ResponseEntity<byte[]> rapport(@PathVariable Long idDmc, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition("rapport-preselection-" + idDmc + (docx ? ".docx" : ".pdf")))
                .contentType(docx ? Telechargements.DOCX : MediaType.APPLICATION_PDF).body(service.rapport(idDmc, docx));
    }

    @PostMapping("/rapport/signer")
    public PreselectionDto signer(@PathVariable Long idDmc, @RequestBody(required = false) PreselectionDto.SignatureRequest r) {
        return service.signer(idDmc, r);
    }

    @PostMapping("/rapport/empechement")
    public PreselectionDto empechement(@PathVariable Long idDmc, @RequestBody(required = false) PreselectionDto.EmpechementRequest r) {
        return service.empechement(idDmc, r);
    }

    /** {@code { dateLimite, motif }} ; PRMP ; 409 {@code LISTE_ARRETEE}, {@code LECTURE_FERMEE}. */
    @PostMapping("/relancer")
    public PreselectionDto relancer(@PathVariable Long idDmc, @RequestBody(required = false) PreselectionDto.RelanceRequest r) {
        return service.relancer(idDmc, r);
    }

    /** {@code { motif }} ; PRMP ; 409 {@code QUALIFIES_PRESENTS}. */
    @PostMapping("/infructueux")
    public PreselectionDto infructueux(@PathVariable Long idDmc, @RequestBody(required = false) PreselectionDto.InfructueuxRequest r) {
        return service.infructueux(idDmc, r);
    }
}
