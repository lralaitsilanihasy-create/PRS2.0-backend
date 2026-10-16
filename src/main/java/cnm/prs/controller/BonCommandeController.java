package cnm.prs.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.BonCommandeDto;
import cnm.prs.service.BonCommandeService;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-3 ; V126) — les bons de commande d'un contrat en vigueur : l'émission
 * et l'annulation (PRMP de la fiche), la lecture et la consommation (PRMP et UGPM de la fiche, Commission, Administrateur).
 */
@RestController
public class BonCommandeController {

    private final BonCommandeService service;

    public BonCommandeController(BonCommandeService service) {
        this.service = service;
    }

    /**
     * 201 ; 400 {@code CHAMP_OBLIGATOIRE}, {@code REMISE_OBLIGATOIRE}, {@code LIGNES_OBLIGATOIRES}, {@code QUANTITE_INVALIDE},
     * {@code PRIX_OBLIGATOIRE}, {@code ARTICLE_INCONNU}, {@code PRIX_HORS_BORDEREAU} ; 409 {@code CONTRAT_ECHU},
     * {@code REMISE_NON_ATTRIBUEE}, {@code SANS_TITULAIRE}, {@code MONTANT_SUBSEQUENT_DEPASSE}, {@code MAXIMUM_DEPASSE}.
     */
    @PostMapping("/api/contrats-en-vigueur/{idContrat}/bons-commande")
    public ResponseEntity<BonCommandeDto> emettre(@PathVariable Long idContrat, @RequestBody(required = false) BonCommandeDto.BonRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.emettre(idContrat, r));
    }

    /** Les bons du contrat et sa consommation (cumuls, minimum et maximum, marchés subséquents, alertes). */
    @GetMapping("/api/contrats-en-vigueur/{idContrat}/bons-commande")
    public BonCommandeDto.Consommation consommation(@PathVariable Long idContrat) {
        return service.consommation(idContrat);
    }

    @GetMapping("/api/bons-commande/{id}")
    public BonCommandeDto lire(@PathVariable Long id) {
        return service.lire(id);
    }

    /** Le bon, en PDF ou en Word ({@code ?format=docx}). */
    @GetMapping("/api/bons-commande/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable Long id, @RequestParam(required = false) String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        return Telechargements.fichier("bon-commande-" + id + (docx ? ".docx" : ".pdf"), docx ? "docx" : "pdf", service.document(id, docx));
    }

    /** 400 {@code MOTIF_OBLIGATOIRE} ; 409 {@code DEJA_ANNULE}. */
    @PostMapping("/api/bons-commande/{id}/annulation")
    public BonCommandeDto annuler(@PathVariable Long id, @RequestBody(required = false) BonCommandeDto.AnnulationRequest r) {
        return service.annuler(id, r);
    }
}
