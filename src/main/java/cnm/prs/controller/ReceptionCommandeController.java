package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.ReceptionCommandeDto;
import cnm.prs.service.ReceptionCommandeService;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-4 ; V127) — la réception des commandes : la commission de réception
 * (PRMP ou UGPM), la livraison déclarée, le PV établi par le président et signé par chaque membre (et la PRMP quand elle préside).
 */
@RestController
public class ReceptionCommandeController {

    private final ReceptionCommandeService service;

    public ReceptionCommandeController(ReceptionCommandeService service) {
        this.service = service;
    }

    @GetMapping("/api/contrats-en-vigueur/{idContrat}/commission-reception")
    public ReceptionCommandeDto.Commission commission(@PathVariable Long idContrat) {
        return service.commission(idContrat);
    }

    /** 400 champs invalides ; 409 {@code PV_EN_SIGNATURE}. */
    @PutMapping("/api/contrats-en-vigueur/{idContrat}/commission-reception")
    public ReceptionCommandeDto.Commission designer(@PathVariable Long idContrat, @RequestBody(required = false) ReceptionCommandeDto.CommissionCorps corps) {
        return service.designer(idContrat, corps);
    }

    /** Les réceptions où le membre connecté siège. */
    @GetMapping("/api/receptions-commandes/miennes")
    public List<ReceptionCommandeDto.Mienne> miennes() {
        return service.miennes();
    }

    @GetMapping("/api/receptions-commandes/bons/{idBon}")
    public ReceptionCommandeDto.DuBon duBon(@PathVariable Long idBon) {
        return service.duBon(idBon);
    }

    /** 201 ; 400 {@code DATE_INVALIDE} ; 409 {@code BON_ANNULE}, {@code RECEPTION_EN_COURS}, {@code DEJA_RECU}. */
    @PostMapping("/api/receptions-commandes/bons/{idBon}/livraison")
    public ResponseEntity<ReceptionCommandeDto.DuBon> livraison(@PathVariable Long idBon, @RequestBody(required = false) ReceptionCommandeDto.LivraisonRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.declarerLivraison(idBon, r));
    }

    /** 201 ; 409 {@code COMMISSION_NON_DESIGNEE}, {@code NON_RECU}, {@code GARANTIE_EN_COURS}, {@code SANS_GARANTIE}, {@code RECEPTION_EN_COURS}. */
    @PostMapping("/api/receptions-commandes/bons/{idBon}/definitive")
    public ResponseEntity<ReceptionCommandeDto.DuBon> definitive(@PathVariable Long idBon) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ouvrirDefinitive(idBon));
    }

    @GetMapping("/api/receptions-commandes/{id}")
    public ReceptionCommandeDto lire(@PathVariable Long id) {
        return service.lire(id);
    }

    /** Le PV, en PDF ou en Word ({@code ?format=docx}) ; 404 avant son établissement. */
    @GetMapping("/api/receptions-commandes/{id}/pv")
    public ResponseEntity<byte[]> pv(@PathVariable Long id, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return Telechargements.fichier("pv-reception-" + id + (docx ? ".docx" : ".pdf"), docx ? "docx" : "pdf", service.pv(id, docx));
    }

    /**
     * Le président : 400 {@code DECISION_INVALIDE}, {@code MOTIF_OBLIGATOIRE}, {@code REFACTION_INVALIDE}, {@code DATE_INVALIDE},
     * {@code TAUX_INVALIDE} ; 409 {@code COMMISSION_NON_DESIGNEE}, {@code PV_SIGNE}.
     */
    @PostMapping("/api/receptions-commandes/{id}/pv")
    public ReceptionCommandeDto etablir(@PathVariable Long id, @RequestBody(required = false) ReceptionCommandeDto.PvRequest r) {
        return service.etablir(id, r);
    }

    /** 409 {@code PV_NON_ETABLI}, {@code DEJA_SIGNE}. */
    @PostMapping("/api/receptions-commandes/{id}/signature")
    public ReceptionCommandeDto signer(@PathVariable Long id) {
        return service.signer(id);
    }
}
