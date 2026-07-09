package cnm.prs.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.PieceJointeMetaDto;
import cnm.prs.entity.PieceJointe;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PieceJointeMapper;
import cnm.prs.repository.PieceJointeRepository;

/**
 * Stockage et récupération des pièces jointes d'inscription PRMP (arrêté, CIN, photo).
 *
 * <p>Le contenu est conservé en base ({@code bytea}). À chaque dépôt, le type réel est vérifié
 * par les <strong>magic-bytes</strong> (et non par l'extension/Content-Type déclaré), la taille
 * est contrôlée selon le type, et l'empreinte SHA-256 est calculée. Une seule pièce
 * <strong>active</strong> par couple ({@code login}, {@code type}) : un nouveau dépôt
 * <strong>remplace</strong> le précédent (la trace des dépôts est dans le journal d'audit).</p>
 */
@Service
@Transactional
public class PieceJointeService {

    private final PieceJointeRepository repository;

    public PieceJointeService(PieceJointeRepository repository) {
        this.repository = repository;
    }

    /**
     * Stocke (ou remplace) la pièce {@code type} du compte {@code login} à partir du fichier
     * téléversé. Valide la présence, le type réel (PDF/JPEG/PNG) et la taille.
     *
     * @throws BadRequestException si le fichier est absent, d'un type non autorisé ou trop volumineux
     */
    public PieceJointeMetaDto stocker(String login, TypePieceJointe type, MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Pièce « " + type + " » manquante ou vide.");
        }
        byte[] contenu = lire(fichier);
        String mime = detecterType(contenu);
        if (mime == null) {
            throw new BadRequestException("Type de fichier non autorisé pour « " + type
                    + " » : seuls PDF, JPEG et PNG sont acceptés.");
        }
        if (contenu.length > type.maxOctets()) {
            throw new BadRequestException("Fichier « " + type + " » trop volumineux ("
                    + contenu.length + " octets ; max " + type.maxOctets() + ").");
        }

        PieceJointe piece = repository.findByLoginAndTypePiece(login, type.name())
                .orElseGet(() -> {
                    PieceJointe p = new PieceJointe();
                    p.setIdPiece(repository.findMaxId() + 1);
                    p.setLogin(login);
                    p.setTypePiece(type.name());
                    return p;
                });
        piece.setLibelle(fichier.getOriginalFilename());
        piece.setFormat(mime);
        piece.setTailleOctets((long) contenu.length);
        piece.setDateDepot(LocalDateTime.now());
        piece.setHashSha256(sha256Hex(contenu));
        piece.setContenu(contenu);
        return PieceJointeMapper.toDto(repository.save(piece));
    }

    /** Récupère une pièce (contenu + format) pour téléchargement. */
    @Transactional(readOnly = true)
    public PieceJointe telecharger(String login, TypePieceJointe type) {
        return repository.findByLoginAndTypePiece(login, type.name())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pièce « " + type + " » introuvable pour " + login + "."));
    }

    /** Purge toutes les pièces d'une clé acteur (appelé à la suppression de l'acteur, évite les orphelins). */
    public void purger(String cle) {
        repository.deleteByLogin(cle);
    }

    /** Supprime la pièce {@code type} d'une clé acteur. <strong>404</strong> si elle est absente. */
    public void supprimer(String cle, TypePieceJointe type) {
        PieceJointe piece = repository.findByLoginAndTypePiece(cle, type.name())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pièce « " + type + " » introuvable pour " + cle + "."));
        repository.delete(piece);
    }

    private byte[] lire(MultipartFile fichier) {
        try {
            return fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Lecture du fichier impossible : " + e.getMessage());
        }
    }

    /**
     * Détecte le type réel à partir des premiers octets (magic-bytes). Renvoie le MIME autorisé
     * ({@code application/pdf}, {@code image/jpeg}, {@code image/png}) ou {@code null} si non reconnu.
     */
    private String detecterType(byte[] d) {
        if (d.length >= 4 && d[0] == 0x25 && d[1] == 0x50 && d[2] == 0x44 && d[3] == 0x46) {
            return "application/pdf"; // %PDF
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        return null;
    }

    private String sha256Hex(byte[] data) {
        try {
            byte[] empreinte = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(empreinte.length * 2);
            for (byte b : empreinte) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithme SHA-256 indisponible", e);
        }
    }
}
