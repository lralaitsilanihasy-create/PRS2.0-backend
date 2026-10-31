package cnm.prs.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.FiliationDossierDto;
import cnm.prs.service.FiliationDossierService;

/**
 * ⚠️ 2026-10-31 (demande front « filiation-dossier », §B1) — la filiation d'un dossier : l'arbre complet auquel il appartient.
 */
@RestController
public class FiliationDossierController {

    private final FiliationDossierService service;
    private final cnm.prs.service.HistoriqueDossierService historique;

    public FiliationDossierController(FiliationDossierService service, cnm.prs.service.HistoriqueDossierService historique) {
        this.service = service;
        this.historique = historique;
    }

    /** ⚠️ 2026-10-31 (§B2) — l'historique d'un dossier : ses objets rattachés, triés par date ; 404 inconnu ; 403 hors périmètre. */
    @GetMapping("/api/dossiers/{idDossier}/historique")
    public java.util.List<cnm.prs.dto.HistoriqueDossierDto> historique(@PathVariable Integer idDossier) {
        return historique.historique(idDossier);
    }

    /** L'arbre, la racine en tête, le dossier demandé repéré par {@code idDossierDemande} ; 404 inconnu ; 403 hors périmètre. */
    @GetMapping("/api/dossiers/{idDossier}/filiation")
    public FiliationDossierDto filiation(@PathVariable Integer idDossier) {
        return service.filiation(idDossier);
    }
}
