package cnm.prs.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.CaoDto;
import cnm.prs.service.CaoService;
import cnm.prs.service.CompteCaoService;
import cnm.prs.service.QuorumCao;
import jakarta.validation.Valid;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 2a, §B2) — l'<strong>espace des membres de CAO</strong>
 * ({@code /api/cao/**}, profil {@code MEMBRE_CAO}, hors coquille interne) : l'activation du compte (publique, règle de
 * {@code SecurityConfig}), mes procédures, la vue d'une procédure. La cérémonie et la clé vivent sous
 * {@code /api/fiches-marche/{idDmc}/ceremonie/**}, ouvertes à ce profil par identité.
 */
@RestController
@RequestMapping("/api/cao")
public class EspaceCaoController {

    private final CaoService service;
    private final CompteCaoService comptes;
    private final QuorumCao quorum;

    public EspaceCaoController(CaoService service, CompteCaoService comptes, QuorumCao quorum) {
        this.service = service;
        this.comptes = comptes;
        this.quorum = quorum;
    }

    /**
     * ⚠️ R1 (manuel révisé, lot 1, §B2 ; arbitrage : désignation par séance) — le président désigne (ou retire, {@code im} nul) son
     * représentant pour un contexte : {@code { contexte: SEANCE | SEANCE_FINANCIERE | RAPPORT | AMI, im }} ; 403 hors président ; 400
     * {@code CONTEXTE_INVALIDE}, {@code REPRESENTANT_INVALIDE}.
     */
    @PostMapping("/procedures/{idDmc}/representant")
    @PreAuthorize("hasRole('MEMBRE_CAO')")
    public QuorumCao.Representant representant(@PathVariable Long idDmc, @RequestBody(required = false) QuorumCao.RepresentantRequest corps) {
        return quorum.designerRepresentant(idDmc, corps);
    }

    /** Public : 200 {@code { etat: 'ACTIF' }} ; 400 {@code CODE_INVALIDE} / {@code CODE_EXPIRE} ; 404 ; 429. */
    @PostMapping("/activation")
    public CaoDto.EtatCompte activer(@Valid @RequestBody CaoDto.Activation corps) {
        return comptes.activer(corps);
    }

    @GetMapping("/mes-procedures")
    @PreAuthorize("hasRole('MEMBRE_CAO')")
    public List<CaoDto.MaProcedure> mesProcedures() {
        return service.mesProcedures();
    }

    /** 403 si le membre n'y siège pas ; 404 DMC inconnu. */
    @GetMapping("/procedures/{idDmc}")
    @PreAuthorize("hasRole('MEMBRE_CAO')")
    public CaoDto.VueMembre procedure(@PathVariable Long idDmc) {
        return service.vueMembre(idDmc);
    }
}
