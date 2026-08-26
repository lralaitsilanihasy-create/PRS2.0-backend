package cnm.prs.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DocumentPublicDto;
import cnm.prs.dto.VerificationIntegriteResult;
import cnm.prs.entity.DocumentPublic;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.DocumentPublicMapper;
import cnm.prs.repository.DocumentPublicRepository;

/**
 * Logique métier pour {@link DocumentPublic}.
 */
@Service
@Transactional
public class DocumentPublicService {

    private final DocumentPublicRepository repository;

    public DocumentPublicService(DocumentPublicRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DocumentPublicDto> findAll() {
        return repository.findAll().stream().map(DocumentPublicMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public DocumentPublicDto findById(Integer id) {
        DocumentPublic entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentPublic introuvable : " + id));
        return DocumentPublicMapper.toDto(entity);
    }

    public DocumentPublicDto create(DocumentPublicDto dto) {
        // ⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
        ClePrimaire.exigerLibre(dto.getIdDocPublic(), repository::existsById, "document public");
        DocumentPublic entity = DocumentPublicMapper.toEntity(dto);
        return DocumentPublicMapper.toDto(repository.save(entity));
    }

    public DocumentPublicDto update(Integer id, DocumentPublicDto dto) {
        DocumentPublic existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentPublic introuvable : " + id));
        existing.setIdPublication(dto.getIdPublication());
        existing.setTypeDoc(dto.getTypeDoc());
        existing.setLibelleDoc(dto.getLibelleDoc());
        existing.setCheminFichier(dto.getCheminFichier());
        existing.setFormat(dto.getFormat());
        existing.setTailleOctets(dto.getTailleOctets());
        existing.setDateDepot(dto.getDateDepot());
        existing.setHashSha256(dto.getHashSha256());
        return DocumentPublicMapper.toDto(repository.save(existing));
    }

    public void delete(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("DocumentPublic introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /**
     * Enregistre l'empreinte SHA-256 d'un document à partir de son contenu (Base64), et la
     * taille (§3.7 — dépôt avec vérification d'intégrité).
     */
    public DocumentPublicDto enregistrerEmpreinte(Integer id, String contenuBase64) {
        DocumentPublic doc = load(id);
        byte[] contenu = decoder(contenuBase64);
        doc.setHashSha256(sha256Hex(contenu));
        doc.setTailleOctets((long) contenu.length);
        if (doc.getDateDepot() == null) {
            doc.setDateDepot(LocalDateTime.now());
        }
        return DocumentPublicMapper.toDto(repository.save(doc));
    }

    /**
     * Vérifie l'intégrité d'un document : recalcule l'empreinte SHA-256 du contenu fourni et
     * la compare à celle enregistrée (§3.7).
     */
    @Transactional(readOnly = true)
    public VerificationIntegriteResult verifierIntegrite(Integer id, String contenuBase64) {
        DocumentPublic doc = load(id);
        String calcule = sha256Hex(decoder(contenuBase64));
        boolean conforme = doc.getHashSha256() != null && calcule.equalsIgnoreCase(doc.getHashSha256());
        return new VerificationIntegriteResult(conforme, doc.getHashSha256(), calcule);
    }

    private DocumentPublic load(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentPublic introuvable : " + id));
    }

    private byte[] decoder(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Contenu Base64 invalide.");
        }
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
