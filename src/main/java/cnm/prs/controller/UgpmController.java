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

import cnm.prs.dto.CreerUgpmRequest;
import cnm.prs.dto.ModifierUgpmRequest;
import cnm.prs.dto.PieceJointeMetaDto;
import cnm.prs.dto.SuppressionLotResult;
import cnm.prs.dto.SuppressionLotUgpmRequest;
import cnm.prs.dto.UgpmDto;
import cnm.prs.entity.PieceJointe;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.service.UgpmService;
import jakarta.validation.Valid;

/**
 * UGPM (Administrateur) : création d'une UGPM + compte, et liste. Réservé au profil {@code ADMINISTRATEUR}.
 */
@RestController
@RequestMapping("/api/ugpms")
public class UgpmController {

    private final UgpmService service;

    public UgpmController(UgpmService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UgpmDto> creer(@Valid @RequestBody CreerUgpmRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(req));
    }

    /**
     * Création <strong>multipart</strong> avec pièces <strong>optionnelles</strong> (miroir PRMP, sans arrêté) :
     * part {@code data} (JSON = {@code CreerUgpmRequest}) + parts {@code cin}/{@code photo}. Contraintes fichiers :
     * PDF/JPEG/PNG (magic-bytes), ≤ 5 Mo ; la photo doit être une image (JPEG/PNG). Sinon 400.
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UgpmDto> creerAvecPieces(
            @Valid @RequestPart("data") CreerUgpmRequest req,
            @RequestPart(value = "cin", required = false) MultipartFile cin,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creerAvecPieces(req, cin, photo));
    }

    /**
     * Dépose (ou remplace) une pièce d'une UGPM. {@code type} ∈ {@code CIN}/{@code PHOTO}
     * ({@code ARRETE_NOMIN} → 400). <strong>404</strong> si l'UGPM est inconnue.
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping(value = "/{id}/pieces/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PieceJointeMetaDto deposerPiece(@PathVariable String id, @PathVariable TypePieceJointe type,
            @RequestPart("fichier") MultipartFile fichier) {
        return service.deposerPiece(id, type, fichier);
    }

    /** Téléchargement d'une pièce d'une UGPM. {@code ARRETE_NOMIN} → 400 ; pièce absente → 404. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping("/{id}/pieces/{type}")
    public ResponseEntity<byte[]> telechargerPiece(@PathVariable String id, @PathVariable TypePieceJointe type) {
        PieceJointe piece = service.telechargerPiece(id, type);
        // ⚠️ Audit front (2026-08-16) — type de sortie sur LISTE BLANCHE + nom d'en-tête assaini.
        String nom = piece.getLibelle() != null ? piece.getLibelle() : id + "_" + type;
        return ResponseEntity.ok()
                .contentType(Telechargements.typeAutorise(piece.getFormat()))
                .header(HttpHeaders.CONTENT_DISPOSITION, Telechargements.disposition(nom))
                .body(piece.getContenu());
    }

    /**
     * Supprime une pièce d'une UGPM (sans supprimer l'UGPM). Réservé {@code ADMINISTRATEUR} (sous-chemin non
     * couvert par SecurityConfig). {@code type} ∈ {@code CIN}/{@code PHOTO} ({@code ARRETE_NOMIN} → 400) ;
     * **404** si l'UGPM ou la pièce est inconnue.
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}/pieces/{type}")
    public ResponseEntity<Void> supprimerPiece(@PathVariable String id, @PathVariable TypePieceJointe type) {
        service.supprimerPiece(id, type);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping
    public List<UgpmDto> findAll() {
        return service.findAll();
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping("/{id}")
    public UgpmDto findById(@PathVariable String id) {
        return service.findById(id);
    }

    /**
     * UGPM rattachées à une PRMP de tutelle (liste, vide si aucune).
     *
     * <p>⚠️ Ouvert à la <strong>PRMP concernée</strong> (2026-08-19) : elle consulte ses propres
     * unités rattachées — l'onglet « Entité contractante » du front les affiche. Le service vérifie
     * que le {@code idPrmp} demandé est bien le sien (sinon 403) ; l'Administrateur voit tout.</p>
     */
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','PRMP','UGPM')")
    @GetMapping("/par-tutelle/{idPrmp}")
    public List<UgpmDto> findByTutelle(@PathVariable String idPrmp) {
        return service.findByTutelle(idPrmp);
    }

    /** UGPM d'une localité, via leur PRMP de tutelle (entités actives). Liste, vide si aucune ; pas de 404. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping("/par-localite/{idLocalite}")
    public List<UgpmDto> findByLocalite(@PathVariable String idLocalite) {
        return service.findByLocalite(idLocalite);
    }

    /** Recherche partielle par nom (contient, insensible à la casse ; liste, vide si aucun résultat). */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @GetMapping("/par-nom/{nom}")
    public List<UgpmDto> findByNom(@PathVariable String nom) {
        return service.findByNom(nom);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public UgpmDto modifier(@PathVariable String id, @Valid @RequestBody ModifierUgpmRequest req) {
        return service.modifier(id, req);
    }

    /**
     * Modification <strong>multipart</strong> avec pièces (miroir du POST multipart) : part {@code data} (JSON
     * {@code ModifierUgpmRequest}) + parts {@code cin}/{@code photo} <strong>optionnelles</strong> (fournie →
     * remplace ; absente → inchangée). Mêmes contraintes fichiers : {@code CIN}/{@code PHOTO}, photo = image,
     * ≤ 5 Mo (sinon 400). <strong>404</strong> si l'UGPM est inconnue.
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UgpmDto modifierAvecPieces(@PathVariable String id,
            @Valid @RequestPart("data") ModifierUgpmRequest req,
            @RequestPart(value = "cin", required = false) MultipartFile cin,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        return service.modifierAvecPieces(id, req, cin, photo);
    }

    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Suppression en lot (tolérante) : bilan {@code {supprimes[], introuvables[]}}. */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping("/suppression-lot")
    public SuppressionLotResult supprimerLot(@Valid @RequestBody SuppressionLotUgpmRequest req) {
        return service.supprimerLot(req.matricules());
    }
}
