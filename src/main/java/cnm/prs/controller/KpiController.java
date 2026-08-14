package cnm.prs.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.CompteursAdminDto;
import cnm.prs.dto.CompteursAssistantDto;
import cnm.prs.dto.CompteursMembreDto;
import cnm.prs.dto.CompteursPrmpDto;
import cnm.prs.dto.CompteursPublicationDto;
import cnm.prs.dto.CompteursSecretaireDto;
import cnm.prs.dto.CompteursVerificateurDto;
import cnm.prs.dto.TableauBordDto;
import cnm.prs.service.KpiService;

/**
 * Tableau de bord & KPIs (§3.2, §3.8) : vue globale pour le Président/Administrateur,
 * vue filtrée sur sa localité pour le Chef de commission (§3.3).
 */
@RestController
@RequestMapping("/api/kpis")
public class KpiController {

    private final KpiService kpiService;

    public KpiController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    @PreAuthorize("hasAnyRole('PRESIDENT','ADMINISTRATEUR','CHEF_COMMISSION')")
    @GetMapping("/tableau-bord")
    public TableauBordDto tableauBord() {
        return kpiService.tableauBord();
    }

    /** Compteurs de contenu du menu PRMP — filtrés sur la PRMP authentifiée (§3.1). */
    @PreAuthorize("hasRole('PRMP')")
    @GetMapping("/mes-compteurs")
    public CompteursPrmpDto mesCompteurs() {
        return kpiService.mesCompteursPrmp();
    }

    /** Compteurs de contenu du menu Contrôleur vérificateur — filtrés sur sa localité (§3.6). */
    @PreAuthorize("@perm.peutExercer('VERIFICATEUR') or hasRole('ADMINISTRATEUR')")
    @GetMapping("/mes-compteurs-verificateur")
    public CompteursVerificateurDto mesCompteursVerificateur() {
        return kpiService.mesCompteursVerificateur();
    }

    /** Compteurs de contenu du menu Secrétaire — filtrés sur sa localité (§3.4). */
    @PreAuthorize("@perm.peutExercer('SECRETAIRE') or hasRole('ADMINISTRATEUR')")
    @GetMapping("/mes-compteurs-secretaire")
    public CompteursSecretaireDto mesCompteursSecretaire() {
        return kpiService.mesCompteursSecretaire();
    }

    /** Compteurs de contenu du menu Membre — filtrés sur le Membre attributaire (§2.4). */
    @PreAuthorize("@perm.peutExercer('MEMBRE') or hasRole('ADMINISTRATEUR')")
    @GetMapping("/mes-compteurs-membre")
    public CompteursMembreDto mesCompteursMembre() {
        return kpiService.mesCompteursMembre();
    }

    /** Compteurs de contenu du menu Chargé de publication — workflow de publication, global (§3.7). */
    @PreAuthorize("hasAnyRole('CHARGE_PUBLICATION','ADMINISTRATEUR')")
    @GetMapping("/mes-compteurs-publication")
    public CompteursPublicationDto mesCompteursPublication() {
        return kpiService.mesCompteursPublication();
    }

    /** Compteurs de contenu du menu Assistant contrôleur — filtrés sur sa localité (§3.7). */
    @PreAuthorize("@perm.peutExercer('ASSISTANT_CONTROLEUR') or hasRole('ADMINISTRATEUR')")
    @GetMapping("/mes-compteurs-assistant")
    public CompteursAssistantDto mesCompteursAssistant() {
        return kpiService.mesCompteursAssistant();
    }

    /** Compteurs de contenu du menu Administrateur — global (inscriptions, comptes, audit §3.8). */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping("/mes-compteurs-admin")
    public CompteursAdminDto mesCompteursAdmin() {
        return kpiService.mesCompteursAdmin();
    }
}
