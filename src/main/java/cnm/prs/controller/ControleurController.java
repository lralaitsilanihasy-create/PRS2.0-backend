package cnm.prs.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import cnm.prs.dto.ControleurDto;
import cnm.prs.dto.PieceJointeMetaDto;
import cnm.prs.dto.SuppressionLotControleurRequest;
import cnm.prs.dto.SuppressionLotControleurResult;
import cnm.prs.entity.PieceJointe;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.service.ControleurService;

/**
 * Contrôleur REST pour la ressource {@code controleurs} (table {@code tr_controleur}).
 */
@RestController
@RequestMapping("/api/controleurs")
public class ControleurController {

    private final ControleurService service;

    public ControleurController(ControleurService service) {
        this.service = service;
    }

    @GetMapping
    public List<ControleurDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ControleurDto findById(@PathVariable String id) {
        return service.findById(id);
    }

    /** Contrôleurs affectés à une localité (liste, vide si aucun ; transversaux à localité nulle exclus). */
    @GetMapping("/par-localite/{idLocalite}")
    public List<ControleurDto> findByLocalite(@PathVariable String idLocalite) {
        return service.findByLocalite(idLocalite);
    }

    /** Contrôleurs d'un profil (rôle, tr_profile) donné (liste, vide si aucun). */
    @GetMapping("/par-profil/{idProfile}")
    public List<ControleurDto> findByProfil(@PathVariable Integer idProfile) {
        return service.findByProfil(idProfile);
    }

    /** Subordonnés directs d'un contrôleur (ceux dont le supérieur = imSuperieur ; liste, vide si aucun). */
    @GetMapping("/par-superieur/{imSuperieur}")
    public List<ControleurDto> findBySuperieur(@PathVariable String imSuperieur) {
        return service.findBySuperieur(imSuperieur);
    }

    /** Recherche partielle par nom (contient, insensible à la casse ; liste, vide si aucun résultat). */
    @GetMapping("/par-nom/{nom}")
    public List<ControleurDto> findByNom(@PathVariable String nom) {
        return service.findByNom(nom);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ControleurDto> create(@Valid @RequestBody ControleurDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    /**
     * Création <strong>multipart</strong> avec photo <strong>optionnelle</strong> (miroir PRMP/UGPM, photo seule) :
     * part {@code data} (JSON = {@code ControleurDto}) + part {@code photo}. La photo doit être une image
     * (JPEG/PNG, magic-bytes), ≤ 5 Mo (sinon 400).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ControleurDto> createAvecPhoto(
            @Valid @RequestPart("data") ControleurDto dto,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAvecPhoto(dto, photo));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ControleurDto update(@PathVariable String id, @Valid @RequestBody ControleurDto dto) {
        return service.update(id, dto);
    }

    /**
     * Modification <strong>multipart</strong> avec photo (miroir du POST multipart) : part {@code data} (JSON
     * {@code ControleurDto}) + part {@code photo} <strong>optionnelle</strong> (fournie → remplace ; absente →
     * inchangée). La photo doit être une image (JPEG/PNG), ≤ 5 Mo (sinon 400). <strong>404</strong> si inconnu.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ControleurDto updateAvecPhoto(@PathVariable String id,
            @Valid @RequestPart("data") ControleurDto dto,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        return service.updateAvecPhoto(id, dto, photo);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Dépose (ou remplace) la photo d'un contrôleur ({@code id} = imControleur). Réservé {@code ADMINISTRATEUR}
     * (sous-chemin non couvert par SecurityConfig). {@code type} ∈ {@code PHOTO} uniquement (autre → 400).
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping(value = "/{id}/pieces/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PieceJointeMetaDto deposerPhoto(@PathVariable String id, @PathVariable TypePieceJointe type,
            @RequestPart("fichier") MultipartFile fichier) {
        return service.deposerPhoto(id, type, fichier);
    }

    /**
     * Téléchargement de la photo d'un contrôleur. Lecture ouverte à tout AUTHENTIFIÉ (comme la fiche
     * contrôleur — affichage des trombinoscopes/statistiques, ex. « Dispatchs par contrôleur ») ;
     * le dépôt et la suppression restent réservés {@code ADMINISTRATEUR}. **404** si la photo est absente.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/pieces/{type}")
    public ResponseEntity<byte[]> telechargerPhoto(@PathVariable String id, @PathVariable TypePieceJointe type) {
        PieceJointe piece = service.telechargerPhoto(id, type);
        String format = piece.getFormat() != null ? piece.getFormat() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String nom = piece.getLibelle() != null ? piece.getLibelle() : id + "_" + type;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .body(piece.getContenu());
    }

    /**
     * Supprime la photo d'un contrôleur (sans supprimer le contrôleur). Réservé {@code ADMINISTRATEUR}
     * (sous-chemin non couvert par SecurityConfig). {@code type} ∈ {@code PHOTO} (autre → 400) ; **404** si le
     * contrôleur ou la photo est inconnu(e).
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}/pieces/{type}")
    public ResponseEntity<Void> supprimerPhoto(@PathVariable String id, @PathVariable TypePieceJointe type) {
        service.supprimerPhoto(id, type);
        return ResponseEntity.noContent().build();
    }

    /**
     * Suppression en lot (tolérante) : bilan {@code {supprimes[], introuvables[], bloques[]}}. Réservé
     * {@code ADMINISTRATEUR} (ce sous-chemin n'est pas couvert par les règles URL de SecurityConfig).
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping("/suppression-lot")
    public SuppressionLotControleurResult supprimerLot(@Valid @RequestBody SuppressionLotControleurRequest req) {
        return service.supprimerLot(req.matricules());
    }
}
