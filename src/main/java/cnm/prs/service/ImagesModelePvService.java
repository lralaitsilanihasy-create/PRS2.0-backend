package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.dto.ModelePvDto;
import cnm.prs.entity.ImageModelePv;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ImageModelePvRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-21 (demande front « modele-pv-page », §B3 ; V139) — les images des pages de modèles de PV : PNG ou JPEG de 2 Mo au plus,
 * reconnus par leur contenu (signature et lecture de l'image), jamais par le type déclaré ; servies avec le type contrôlé.
 * L'emblème des PV officiels ({@code word/media/image1.png} de {@code PV_AFSR_PPM_CENTRALE.docx}) est fourni par l'application,
 * créé au premier besoin sous le code {@code EMBLEME}.
 */
@Service
@Transactional
public class ImagesModelePvService {

    static final long TAILLE_MAX = 2L * 1024 * 1024;
    private static final String BASE_EMBLEME = "/templates/PV_AFSR_PPM_CENTRALE.docx";
    private static final String MEDIA_EMBLEME = "word/media/image1.png";

    private final ImageModelePvRepository images;

    public ImagesModelePvService(ImageModelePvRepository images) {
        this.images = images;
    }

    public ModelePvDto.ImageDeposee deposer(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Aucune image déposée.", "IMAGE_INVALIDE");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new BadRequestException("L'image dépasse 2 Mo.", "IMAGE_INVALIDE");
        }
        try {
            return dto(enregistrer(fichier.getBytes(), null));
        } catch (IOException e) {
            throw new BadRequestException("Image illisible : " + e.getMessage(), "IMAGE_INVALIDE");
        }
    }

    @Transactional(readOnly = true)
    public ImageModelePv lire(Long id) {
        return images.findById(id).orElseThrow(() -> new ResourceNotFoundException("Image introuvable : " + id));
    }

    /** L'emblème des PV officiels, créé au premier besoin. */
    public ModelePvDto.ImageDeposee embleme() {
        return dto(images.findByCode(ImageModelePv.EMBLEME).orElseGet(() -> {
            try (InputStream in = getClass().getResourceAsStream(BASE_EMBLEME); ZipInputStream zip = new ZipInputStream(in)) {
                for (ZipEntry e; (e = zip.getNextEntry()) != null;) {
                    if (MEDIA_EMBLEME.equals(e.getName())) {
                        return enregistrer(zip.readAllBytes(), ImageModelePv.EMBLEME);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Emblème illisible dans " + BASE_EMBLEME + " : " + e.getMessage(), e);
            }
            throw new IllegalStateException("Emblème absent de " + BASE_EMBLEME);
        }));
    }

    @Transactional(readOnly = true)
    public boolean existe(long id) {
        return images.existsById(id);
    }

    /** L'image prête au rendu ({@code null} si elle n'existe plus). */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public ModelePvPage.Image pourRendu(long id) {
        return images.findById(id).map(i -> new ModelePvPage.Image(i.getType(), i.getContenu(), i.getLargeur(), i.getHauteur()))
                .orElse(null);
    }

    private ImageModelePv enregistrer(byte[] contenu, String code) {
        String type = type(contenu);
        int[] dimensions = dimensions(contenu);
        ImageModelePv i = new ImageModelePv();
        i.setCode(code);
        i.setType(type);
        i.setContenu(contenu);
        i.setLargeur(dimensions[0]);
        i.setHauteur(dimensions[1]);
        i.setTaille((long) contenu.length);
        i.setEmpreinte(sha256(contenu));
        i.setDeposeeLe(LocalDateTime.now(HorlogeRecette.HORLOGE));
        i.setDeposeePar(CurrentUser.login().orElse(null));
        return images.saveAndFlush(i);
    }

    /** Le type par la signature du contenu : PNG ou JPEG, rien d'autre. */
    static String type(byte[] c) {
        if (c.length > 8 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G' && c[4] == 0x0D && c[5] == 0x0A
                && c[6] == 0x1A && c[7] == 0x0A) {
            return "image/png";
        }
        if (c.length > 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        throw new BadRequestException("L'image n'est ni un PNG ni un JPEG (contenu contrôlé, pas le type déclaré).", "IMAGE_INVALIDE");
    }

    /** Largeur et hauteur lues par le décodeur : une image qui ne se lit pas est refusée. */
    private static int[] dimensions(byte[] contenu) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(contenu))) {
            var lecteurs = ImageIO.getImageReaders(in);
            if (in != null && lecteurs.hasNext()) {
                ImageReader r = lecteurs.next();
                try {
                    r.setInput(in);
                    int l = r.getWidth(0);
                    int h = r.getHeight(0);
                    if (l > 0 && h > 0 && l <= 20000 && h <= 20000) {
                        return new int[] { l, h };
                    }
                } finally {
                    r.dispose();
                }
            }
        } catch (IOException | RuntimeException e) {
            // refusée ci-dessous
        }
        throw new BadRequestException("L'image ne se lit pas.", "IMAGE_INVALIDE");
    }

    private static ModelePvDto.ImageDeposee dto(ImageModelePv i) {
        return new ModelePvDto.ImageDeposee(i.getIdImage(), i.getLargeur(), i.getHauteur(), i.getType(), i.getCode());
    }

    private static String sha256(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
