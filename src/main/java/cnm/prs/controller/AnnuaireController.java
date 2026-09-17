package cnm.prs.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cnm.prs.dto.AnnuairePersonneDto;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutCompteAnnuaire;
import cnm.prs.enums.TypeActeur;
import cnm.prs.service.AnnuaireService;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B2) — annuaire unifié des
 * personnes : un administrateur cherche « quelqu'un », pas « un contrôleur ».
 *
 * <p><strong>Accès : {@code ADMINISTRATEUR} et personne d'autre.</strong> C'est un écran
 * d'administration, et la réponse réunit ce qu'aucun autre profil n'a à connaître d'un seul tenant :
 * le référentiel complet des personnes, leur login et l'état de leur compte. Anonyme → 401 ; tout
 * autre profil → 403. Les trois listes d'origine ({@code /api/controleurs}, {@code /api/prmps},
 * {@code /api/ugpms}) gardent leurs règles, cette route ne les remplace pas.</p>
 *
 * <p><strong>Lecture seule.</strong> Aucun {@code POST}/{@code PUT}/{@code DELETE} : les gestes
 * d'administration de compte restent sur {@code /api/comptes-auth/**} et les fiches sur leur
 * ressource d'origine.</p>
 */
@RestController
@RequestMapping("/api/annuaire")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AnnuaireController {

    private final AnnuaireService service;

    public AnnuaireController(AnnuaireService service) {
        this.service = service;
    }

    /**
     * {@code GET /api/annuaire?q=&type=&profil=&localite=&statut=&page=&size=} — page de personnes
     * triées nom, prénoms, référence. Tous les critères sont facultatifs et se cumulent ; une valeur
     * inconnue d'un critère énuméré part en 400 (et non en liste vide, qui se lirait « personne » au
     * lieu de « critère erroné »).
     *
     * @param q        texte libre, sans la casse ni les accents : nom, prénoms, référence, login, entité
     * @param type     {@code CONTROLEUR} · {@code PRMP} · {@code UGPM}
     * @param profil   profil du contrôleur (ex. {@code MEMBRE}) ; exclut PRMP et UGPM, qui n'en portent pas
     * @param localite code de localité ({@code ID_LOCALITE}) ; ne retient que des contrôleurs
     * @param statut   {@code ACTIF} · {@code DESACTIVE} · {@code EN_ATTENTE} · {@code SANS_COMPTE}
     */
    @GetMapping
    public Page<AnnuairePersonneDto> rechercher(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TypeActeur type,
            @RequestParam(required = false) ProfilUtilisateur profil,
            @RequestParam(required = false) String localite,
            @RequestParam(required = false) StatutCompteAnnuaire statut,
            Pageable pageable) {
        return service.rechercher(q, type, profil, localite, statut, pageable);
    }
}
