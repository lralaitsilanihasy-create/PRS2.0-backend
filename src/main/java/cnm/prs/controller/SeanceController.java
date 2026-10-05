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

import cnm.prs.dto.SeanceDto;
import cnm.prs.service.SeanceService;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 4 ; ADR-0013) — la <strong>séance d'ouverture des plis</strong>. Les gardes
 * sont dans le service, par identité : le responsable de la procédure conduit, chaque membre de la CAO apporte ses parts, la PRMP et
 * l'UGPM lisent.
 */
@RestController
@RequestMapping("/api/fiches-marche/{idDmc}/seance")
public class SeanceController {

    private final SeanceService service;

    public SeanceController(SeanceService service) {
        this.service = service;
    }

    @GetMapping
    public SeanceDto lire(@PathVariable Long idDmc) {
        return service.lire(idDmc);
    }

    /** 409 {@code SEANCE_PREMATUREE} (l'heure dans {@code details.heureOuverture}), {@code DEPOTS_NON_CLOS}, {@code SEANCE_DEJA_OUVERTE}. */
    @PostMapping("/ouvrir")
    public SeanceDto ouvrir(@PathVariable Long idDmc) {
        return service.ouvrir(idDmc);
    }

    @PutMapping("/presences")
    public SeanceDto presences(@PathVariable Long idDmc, @RequestBody SeanceDto.Presences corps) {
        return service.presences(idDmc, corps);
    }

    /** Ses parts chiffrées (membre ; responsable avec {@code ?role=SECOURS}) ; 409 {@code SEANCE_NON_OUVERTE}. */
    @GetMapping("/mes-parts")
    public List<SeanceDto.PartChiffree> mesParts(@PathVariable Long idDmc, @RequestParam(required = false) String role) {
        return service.mesParts(idDmc, role);
    }

    /** Toutes ses parts claires en une fois ; au quorum, toutes les offres s'ouvrent. */
    @PostMapping("/parts")
    public SeanceDto parts(@PathVariable Long idDmc, @RequestParam(required = false) String role, @RequestBody SeanceDto.Apport corps) {
        return service.apporter(idDmc, role, corps);
    }

    /**
     * ⚠️ 2026-10-05 (dépositaire, §B3) — le responsable demande la part de secours au dépositaire : 400 {@code MOTIF_ABSENT} ; 409
     * {@code SEANCE_NON_OUVERTE} / {@code SECOURS_INUTILE} / {@code GESTE_DU_RESPONSABLE}.
     */
    @PostMapping("/secours")
    public SeanceDto demanderSecours(@PathVariable Long idDmc, @RequestBody(required = false) SeanceDto.DemandeSecours corps) {
        return service.demanderSecours(idDmc, corps);
    }

    /** 409 {@code SEANCE_NON_DECHIFFREE}. */
    @GetMapping("/lecture")
    public SeanceDto.Lecture lecture(@PathVariable Long idDmc) {
        return service.lecture(idDmc);
    }

    @GetMapping("/offres/{idOffre}/pieces/{nomFichier}")
    public ResponseEntity<byte[]> piece(@PathVariable Long idDmc, @PathVariable String idOffre, @PathVariable String nomFichier) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition(nomFichier))
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(service.piece(idDmc, idOffre, nomFichier));
    }

    @PostMapping("/pv")
    public SeanceDto produirePv(@PathVariable Long idDmc, @RequestBody(required = false) SeanceDto.Observations corps) {
        return service.produirePv(idDmc, corps);
    }

    @GetMapping("/pv")
    public ResponseEntity<byte[]> pv(@PathVariable Long idDmc) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition("pv-ouverture-" + idDmc + ".pdf"))
                .contentType(MediaType.APPLICATION_PDF).body(service.pv(idDmc));
    }

    /** ⚠️ Arbitrages du pilote (§B2) : la signature du PV par un membre présent ; 403 {@code NON_PRESENT} ; 409 {@code PV_NON_PRODUIT}, {@code DEJA_SIGNE}. */
    @PostMapping("/pv/signer")
    public SeanceDto signer(@PathVariable Long idDmc) {
        return service.signer(idDmc);
    }

    /** ⚠️ §B2, Q1 : l'empêchement d'un membre présent, constaté par le président ; 400 {@code MOTIF_ABSENT}, {@code NON_SIGNATAIRE}. */
    @PostMapping("/pv/empechement")
    public SeanceDto empechement(@PathVariable Long idDmc, @RequestBody SeanceDto.Empechement corps) {
        return service.empechement(idDmc, corps);
    }

    /** S5 : 400 sans motif ; 409 {@code QUORUM_POSSIBLE}, {@code SEANCE_NON_OUVERTE}. */
    @PostMapping("/constater-illisible")
    public SeanceDto constaterIllisible(@PathVariable Long idDmc, @RequestBody SeanceDto.Constat corps) {
        return service.constaterIllisible(idDmc, corps);
    }
}
