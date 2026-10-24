package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.dto.SpecificationsDto;
import cnm.prs.entity.SpecificationsFiche;
import cnm.prs.enums.CategorieDao;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.SpecificationsFicheRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>Les spécifications techniques</strong> d'une fiche DAO (demande front du 2026-10-06, DAO complet, §B2 ; V73) : un fichier
 * joint par la PRMP ou l'UGPM à la version de la fiche, comme le besoin — écrit sur le brouillon (409 {@code FICHE_VALIDEE} sur une
 * version validée), recopié à la révision ({@link #copier}).
 *
 * <p>⚠️ 2026-10-17 (demande front « specifications-donnees-et-plans-pdf » ; arbitrage du pilote) — selon la catégorie :</p>
 * <ul>
 *   <li><strong>fournitures et services</strong> : plus de fichier, les spécifications sont les données du besoin (B12) ; 409
 *       {@code SPECIFICATIONS_HORS_PERIMETRE} ;</li>
 *   <li><strong>travaux</strong> : le devis descriptif et les plans, en <strong>un seul PDF</strong> de {@value #TAILLE_MAX_PDF_MO} Mo au
 *       plus (plans en grand format) ; un Word est refusé (il s'exporte en PDF). Le PDF devient à la validation le document
 *       {@code DESCRIPTIF_PLANS} de la version, cité par le DAO complet, servi au retrait et joint d'office au dossier ;</li>
 *   <li><strong>prestations intellectuelles</strong> : un {@code .docx}, inséré dans le DAO complet, comme avant.</li>
 * </ul>
 */
@Service
@Transactional
public class SpecificationsService {

    /** 20 Mo pour un Word. */
    static final long TAILLE_MAX = 20L * 1024 * 1024;
    static final int TAILLE_MAX_PDF_MO = 50;
    /** 50 Mo pour le PDF des travaux (la limite multipart de l'application est réglée en conséquence). */
    static final long TAILLE_MAX_PDF = TAILLE_MAX_PDF_MO * 1024L * 1024;
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

    /**
     * Dépose ou remplace le fichier du brouillon : 409 {@code SPECIFICATIONS_HORS_PERIMETRE} en fournitures et services ; 400
     * {@code FICHIER_ABSENT} / {@code FORMAT_INVALIDE} (un PDF en travaux, un .docx sinon) ; 413 au-delà de la taille admise.
     */
    public SpecificationsDto deposer(Long idDmc, MultipartFile fichier) {
        String categorie = fiches.categorie(idDmc);
        if (CategorieDao.FOURNITURES_SERVICES.name().equals(categorie)) {
            throw new BusinessRuleException("En fournitures et services, les spécifications techniques sont les données du besoin (bloc "
                    + "B12, articles, caractéristiques et prescriptions générales) : aucun fichier ne s'y joint.", "SPECIFICATIONS_HORS_PERIMETRE");
        }
        boolean travaux = CategorieDao.TRAVAUX.name().equals(categorie);
        Integer idFiche = fiches.ficheEcrivable(idDmc);
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Le fichier des spécifications techniques est attendu.", "FICHIER_ABSENT");
        }
        if (fichier.getSize() > (travaux ? TAILLE_MAX_PDF : TAILLE_MAX)) {
            throw new PayloadTropVolumineuxException(travaux ? "Le devis descriptif et les plans dépassent " + TAILLE_MAX_PDF_MO + " Mo."
                    : "Les spécifications techniques dépassent 20 Mo.");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Lecture du fichier impossible.", "FICHIER_ABSENT");
        }
        if (travaux) {
            exigerPdf(contenu);
        } else {
            exigerDocx(contenu);
        }
        SpecificationsFiche s = repository.save(new SpecificationsFiche(idFiche, nom(fichier.getOriginalFilename(), travaux),
                (long) contenu.length, sha256(contenu), contenu, LocalDateTime.now(HorlogeRecette.HORLOGE), CurrentUser.login().orElse(null)));
        return new SpecificationsDto(s.getNomFichier(), s.getTailleOctets(), s.getDeposeLe(), s.getDeposePar());
    }

    /** Retire le fichier du brouillon ; 404 s'il n'y en a pas. */
    public void retirer(Long idDmc) {
        Integer idFiche = fiches.ficheEcrivable(idDmc);
        SpecificationsFiche s = repository.findById(idFiche)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune spécification technique jointe à cette fiche."));
        repository.delete(s);
    }

    /**
     * ⚠️ La révision : le fichier de la version source est recopié sur la nouvelle version. ⚠️ 2026-10-17 — sauf ce que la catégorie
     * n'admet plus : un Word en fournitures ou en travaux (une version validée avant le 17/10 garde le sien).
     */
    public void copier(Integer idFicheSource, Integer idFicheCible, String categorie) {
        repository.findById(idFicheSource).filter(s -> admis(s.getNomFichier(), categorie))
                .ifPresent(s -> repository.save(new SpecificationsFiche(idFicheCible, s.getNomFichier(),
                        s.getTailleOctets(), s.getEmpreinte(), s.getContenu(), s.getDeposeLe(), s.getDeposePar())));
    }

    /** Le fichier d'une version précise (DAO complet), vide sans fichier. */
    @Transactional(readOnly = true)
    public Optional<SpecificationsFiche> deVersion(Integer idFiche) {
        return idFiche == null ? Optional.empty() : repository.findById(idFiche);
    }

    /** Un fichier déposé est-il le PDF des travaux (et non un Word) ? Lu sur son nom, que le dépôt a fixé. */
    public static boolean estPdf(String nomFichier) {
        return nomFichier != null && nomFichier.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    static boolean admis(String nomFichier, String categorie) {
        if (CategorieDao.FOURNITURES_SERVICES.name().equals(categorie)) {
            return false;
        }
        return CategorieDao.TRAVAUX.name().equals(categorie) == estPdf(nomFichier);
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

    /**
     * ⚠️ 2026-10-17 (§B1) — un vrai PDF, lisible, non chiffré, d'une page au moins (400 {@code FORMAT_INVALIDE}). Un Word est refusé :
     * il s'exporte en PDF.
     */
    static void exigerPdf(byte[] contenu) {
        String message = "En travaux, le devis descriptif et les plans se déposent en un seul PDF (un Word s'exporte en PDF).";
        try (PDDocument doc = Loader.loadPDF(contenu)) {
            if (doc.isEncrypted() || doc.getNumberOfPages() < 1) {
                throw new BadRequestException(message, "FORMAT_INVALIDE");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            throw new BadRequestException(message, "FORMAT_INVALIDE");
        }
    }

    private static String nom(String original, boolean pdf) {
        String n = original == null || original.isBlank() ? (pdf ? "descriptif-plans.pdf" : "specifications.docx")
                : original.replaceAll("[\\\\/]", "_");
        if (pdf && !estPdf(n)) {
            n = n + ".pdf";
        }
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
