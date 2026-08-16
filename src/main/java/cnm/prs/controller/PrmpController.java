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

import cnm.prs.dto.CreerPrmpRequest;
import cnm.prs.dto.PieceJointeMetaDto;
import cnm.prs.dto.PrmpDto;
import cnm.prs.dto.SuppressionLotPrmpRequest;
import cnm.prs.dto.SuppressionLotPrmpResult;
import cnm.prs.entity.PieceJointe;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.service.PrmpService;

/**
 * Contrôleur REST pour la ressource {@code prmps} (table {@code t_prmp}).
 */
@RestController
@RequestMapping("/api/prmps")
public class PrmpController {

    private final PrmpService service;

    public PrmpController(PrmpService service) {
        this.service = service;
    }

    @GetMapping
    public List<PrmpDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public PrmpDto findById(@PathVariable String id) {
        return service.findById(id);
    }

    /** PRMP rattachées à une localité via leurs entités contractantes actives (liste, vide si aucune). */
    @GetMapping("/par-localite/{idLocalite}")
    public List<PrmpDto> findByLocalite(@PathVariable String idLocalite) {
        return service.findByLocalite(idLocalite);
    }

    /** PRMP rattachée à une entité contractante (affectation active) : 0 ou 1, en liste (vide si aucune). */
    @GetMapping("/par-entite/{idEntiteContract}")
    public List<PrmpDto> findByEntite(@PathVariable Integer idEntiteContract) {
        return service.findByEntite(idEntiteContract);
    }

    /** Recherche partielle par nom (contient, insensible à la casse ; liste, vide si aucun résultat). */
    @GetMapping("/par-nom/{nom}")
    public List<PrmpDto> findByNom(@PathVariable String nom) {
        return service.findByNom(nom);
    }

    /**
     * Création JSON (rétro-compatible, sans pièces). {@code CreerPrmpRequest} = identité + {@code login}/{@code
     * motDePasse} <strong>optionnels</strong> : fournis → crée aussi le compte PRMP actif ; absents → fiche seule.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PrmpDto> create(@Valid @RequestBody CreerPrmpRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    /**
     * Création <strong>multipart</strong> avec pièces <strong>optionnelles</strong> (miroir de l'inscription) :
     * part {@code data} (JSON = {@code CreerPrmpRequest}, credentials inclus) + parts {@code arrete}/{@code cin}/
     * {@code photo}. Contraintes fichiers : PDF/JPEG/PNG (magic-bytes), arrêté ≤ 10 Mo, CIN/photo ≤ 5 Mo (sinon 400).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PrmpDto> createAvecPieces(
            @Valid @RequestPart("data") CreerPrmpRequest req,
            @RequestPart(value = "arrete", required = false) MultipartFile arrete,
            @RequestPart(value = "cin", required = false) MultipartFile cin,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAvecPieces(req, arrete, cin, photo));
    }

    @PutMapping("/{id}")
    public PrmpDto update(@PathVariable String id, @Valid @RequestBody PrmpDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Suppression en lot (tolérante) : bilan {@code {supprimes[], introuvables[], bloques[]}}. Réservé
     * {@code ADMINISTRATEUR} (ce sous-chemin n'est pas couvert par les règles URL de SecurityConfig).
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping("/suppression-lot")
    public SuppressionLotPrmpResult supprimerLot(@Valid @RequestBody SuppressionLotPrmpRequest req) {
        return service.supprimerLot(req.matricules());
    }

    /**
     * Dépose (ou remplace) une pièce d'une PRMP. Réservé {@code ADMINISTRATEUR} (sous-chemin non couvert par
     * SecurityConfig). Mêmes contraintes que la création. {@code type} ∈ {@code ARRETE_NOMIN}/{@code CIN}/{@code PHOTO}.
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @PostMapping(value = "/{id}/pieces/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PieceJointeMetaDto deposerPiece(@PathVariable String id, @PathVariable TypePieceJointe type,
            @RequestPart("fichier") MultipartFile fichier) {
        return service.deposerPiece(id, type, fichier);
    }

    /** Téléchargement d'une pièce d'une PRMP. Réservé {@code ADMINISTRATEUR}. **404** si la pièce est absente. */
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
     * Supprime une pièce d'une PRMP (sans supprimer la PRMP). Réservé {@code ADMINISTRATEUR} (sous-chemin non
     * couvert par SecurityConfig). {@code type} ∈ {@code ARRETE_NOMIN}/{@code CIN}/{@code PHOTO} ; **404** si la
     * PRMP ou la pièce est inconnue.
     */
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @DeleteMapping("/{id}/pieces/{type}")
    public ResponseEntity<Void> supprimerPiece(@PathVariable String id, @PathVariable TypePieceJointe type) {
        service.supprimerPiece(id, type);
        return ResponseEntity.noContent().build();
    }
}
