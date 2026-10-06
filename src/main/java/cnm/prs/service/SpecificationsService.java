package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.SpecificationsDto;
import cnm.prs.entity.SpecificationsFiche;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.SpecificationsFicheRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>Les spécifications techniques</strong> d'une fiche DAO (demande front du 2026-10-06, DAO complet, §B2 ; V73) : un
 * {@code .docx} joint par la PRMP ou l'UGPM à la version de la fiche, comme le besoin — écrit sur le brouillon (409
 * {@code FICHE_VALIDEE} sur une version validée), recopié à la révision ({@link #copier}), inséré au rang 5 bis du DAO complet.
 */
@Service
@Transactional
public class SpecificationsService {

    /** 20 Mo (la limite multipart de l'application est réglée en conséquence). */
    static final long TAILLE_MAX = 20L * 1024 * 1024;
    private static final Pattern VBA = Pattern.compile("(?i)/word/vbaProject\\.bin");

    private final SpecificationsFicheRepository repository;
    private final FicheMarcheService fiches;

    public SpecificationsService(SpecificationsFicheRepository repository, FicheMarcheService fiches) {
        this.repository = repository;
        this.fiches = fiches;
    }

    /** La version courante (lecture de la fiche) ; 404 sans fichier. */
    @Transactional(readOnly = true)
    public SpecificationsDto lire(Long idDmc) {
        SpecificationsFiche s = courant(idDmc);
        return new SpecificationsDto(s.getNomFichier(), s.getTailleOctets(), s.getDeposeLe(), s.getDeposePar());
    }

    @Transactional(readOnly = true)
    public SpecificationsFiche fichier(Long idDmc) {
        return courant(idDmc);
    }

    /** Dépose ou remplace le fichier du brouillon : 400 {@code FICHIER_ABSENT} / {@code FORMAT_INVALIDE} (pas un .docx) ; 413 au-delà de 20 Mo. */
    public SpecificationsDto deposer(Long idDmc, MultipartFile fichier) {
        Integer idFiche = fiches.ficheEcrivable(idDmc);
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Le fichier des spécifications techniques est attendu.", "FICHIER_ABSENT");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new PayloadTropVolumineuxException("Les spécifications techniques dépassent 20 Mo.");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Lecture du fichier impossible.", "FICHIER_ABSENT");
        }
        exigerDocx(contenu);
        SpecificationsFiche s = repository.save(new SpecificationsFiche(idFiche, nom(fichier.getOriginalFilename()), (long) contenu.length,
                sha256(contenu), contenu, LocalDateTime.now(), CurrentUser.login().orElse(null)));
        return new SpecificationsDto(s.getNomFichier(), s.getTailleOctets(), s.getDeposeLe(), s.getDeposePar());
    }

    /** Retire le fichier du brouillon ; 404 s'il n'y en a pas. */
    public void retirer(Long idDmc) {
        Integer idFiche = fiches.ficheEcrivable(idDmc);
        SpecificationsFiche s = repository.findById(idFiche)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune spécification technique jointe à cette fiche."));
        repository.delete(s);
    }

    /** ⚠️ La révision : le fichier de la version source est recopié sur la nouvelle version. */
    public void copier(Integer idFicheSource, Integer idFicheCible) {
        repository.findById(idFicheSource).ifPresent(s -> repository.save(new SpecificationsFiche(idFicheCible, s.getNomFichier(),
                s.getTailleOctets(), s.getEmpreinte(), s.getContenu(), s.getDeposeLe(), s.getDeposePar())));
    }

    /** Le fichier d'une version précise (DAO complet), vide sans fichier. */
    @Transactional(readOnly = true)
    public Optional<SpecificationsFiche> deVersion(Integer idFiche) {
        return idFiche == null ? Optional.empty() : repository.findById(idFiche);
    }

    private SpecificationsFiche courant(Long idDmc) {
        Integer idFiche = fiches.ficheCourante(idDmc);
        return deVersion(idFiche).orElseThrow(() -> new ResourceNotFoundException("Aucune spécification technique jointe à cette fiche."));
    }

    /** Un vrai document Word : paquet OOXML, partie principale « document », sans macros (400 {@code FORMAT_INVALIDE}). */
    static void exigerDocx(byte[] contenu) {
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(contenu))) {
            if (!pkg.getPartsByName(VBA).isEmpty()) {
                throw new BadRequestException("Un document à macros n'est pas admis : enregistrez-le en .docx.", "FORMAT_INVALIDE");
            }
            try (XWPFDocument doc = new XWPFDocument(pkg)) {
                if (!XWPFRelation.DOCUMENT.getContentType().equals(doc.getPackagePart().getContentType())) {
                    throw new BadRequestException("Seul un document Word (.docx) est admis.", "FORMAT_INVALIDE");
                }
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Seul un document Word (.docx) est admis.", "FORMAT_INVALIDE");
        }
    }

    private static String nom(String original) {
        String n = original == null || original.isBlank() ? "specifications.docx" : original.replaceAll("[\\\\/]", "_");
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    private static String sha256(byte[] d) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(d));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
