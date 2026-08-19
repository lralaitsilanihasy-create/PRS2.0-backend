package cnm.prs.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.ActualiteDto;
import cnm.prs.dto.ActualiteImageDto;
import cnm.prs.entity.Actualite;
import cnm.prs.entity.ActualiteImage;
import cnm.prs.entity.ActualiteProfil;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutActualite;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ActualiteImageRepository;
import cnm.prs.repository.ActualiteProfilRepository;
import cnm.prs.repository.ActualiteRepository;
import cnm.prs.security.CurrentUser;

/**
 * Actualités affichées à l'ouverture de session — spec du 2026-08-18.
 *
 * <p>Filtrage de visibilité <strong>entièrement serveur</strong> (interrupteur global, statut
 * ACTIF, profil JWT ciblé, fenêtre de dates). Suppression = archivage logique ; l'expiration
 * bascule automatiquement en ARCHIVE au fil des lectures. Contenu markdown brut (HTML refusé) ;
 * images JPEG validées par magic-bytes et redimensionnées au serveur (largeur max 1600 px).</p>
 */
@Service
@Transactional
public class ActualiteService {

    /** Taille maximale d'une image à l'envoi (10 Mo) — au-delà : 413. */
    private static final int IMAGE_MAX_OCTETS = 10 * 1024 * 1024;
    /** Largeur maximale stockée : au-delà, redimensionnement proportionnel au serveur. */
    private static final int LARGEUR_MAX_PX = 1600;
    /**
     * Balise HTML (ouvrante, fermante ou commentaire). Ne matche PAS les usages markdown
     * légitimes de « < » : autolien {@code <https://…>} (suivi de « : »), comparaison « a < b »
     * (suivi d'une espace).
     */
    private static final Pattern BALISE_HTML = Pattern.compile("<(/?[a-zA-Z][a-zA-Z0-9-]*[\\s>/]|!--)");

    private final ActualiteRepository repository;
    private final ActualiteProfilRepository profilRepository;
    private final ActualiteImageRepository imageRepository;
    private final ParametreService parametreService;

    public ActualiteService(ActualiteRepository repository, ActualiteProfilRepository profilRepository,
            ActualiteImageRepository imageRepository, ParametreService parametreService) {
        this.repository = repository;
        this.profilRepository = profilRepository;
        this.imageRepository = imageRepository;
        this.parametreService = parametreService;
    }

    // ------------------------------------------------------------------ lecture utilisateur

    /**
     * Actualités du modal d'ouverture de session pour l'utilisateur authentifié. Liste vide si
     * l'interrupteur global est coupé. Le profil vient du JWT/cookie, jamais d'un paramètre
     * client. Tri : date de publication effective (publication, sinon création) décroissante.
     */
    public List<ActualiteDto> mesActualites() {
        if (!parametreService.actualitesActives()) {
            return List.of();
        }
        archiverExpirees();
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == null) {
            return List.of();
        }
        List<Actualite> visibles = repository.visiblesPourProfil(profil.name(), LocalDate.now());
        visibles.sort(Comparator
                .comparing((Actualite a) -> a.getDatePublication() != null
                        ? a.getDatePublication() : a.getDateCreation().toLocalDate())
                .thenComparing(Actualite::getDateCreation).reversed());
        return enrichir(visibles.stream().map(ActualiteService::toDto).toList());
    }

    // ------------------------------------------------------------------ CRUD Administrateur

    /** Vue Administrateur : toutes les actualités (Historique compris), plus récentes d'abord. */
    public List<ActualiteDto> findAll() {
        archiverExpirees();
        List<Actualite> tout = repository.findAll();
        tout.sort(Comparator.comparing(Actualite::getDateCreation).reversed());
        return enrichir(tout.stream().map(ActualiteService::toDto).toList());
    }

    /** Variante paginée (mêmes données, enveloppe {@code Page}) — optionnelle comme sur les grandes listes. */
    public Page<ActualiteDto> findAllPagine(Pageable pageable) {
        return Pagination.depuisListe(findAll(), pageable);
    }

    public ActualiteDto findById(Integer id) {
        archiverExpirees();
        return enrichir(List.of(toDto(charger(id)))).get(0);
    }

    /** Création (Administrateur) : statut forcé INACTIF — l'activation est un second acte délibéré. */
    public ActualiteDto create(ActualiteDto dto) {
        Set<String> profils = validerProfils(dto.getProfilsCibles());
        validerContenuEtDates(dto);
        Actualite entity = new Actualite();
        entity.setTitre(dto.getTitre());
        entity.setContenuMd(dto.getContenuMd());
        entity.setStatut(StatutActualite.INACTIF.name());
        entity.setDatePublication(dto.getDatePublication());
        entity.setDateExpiration(dto.getDateExpiration());
        entity.setDateCreation(LocalDateTime.now());
        entity.setImAuteur(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        Actualite saved = repository.save(entity);
        for (String p : profils) {
            profilRepository.save(new ActualiteProfil(null, saved.getIdActualite(), p));
        }
        return enrichir(List.of(toDto(saved))).get(0);
    }

    /**
     * Mise à jour (Administrateur). Une actualité ARCHIVE est de l'historique : non modifiable
     * (409). Le statut n'accepte que ACTIF/INACTIF ({@code null} = inchangé) — l'archivage passe
     * exclusivement par le DELETE.
     */
    public ActualiteDto update(Integer id, ActualiteDto dto) {
        Actualite entity = charger(id);
        if (StatutActualite.ARCHIVE.name().equals(entity.getStatut())) {
            throw new BusinessRuleException("Actualité archivée : consultable dans l'historique, non modifiable.");
        }
        Set<String> profils = validerProfils(dto.getProfilsCibles());
        validerContenuEtDates(dto);
        if (dto.getStatut() != null) {
            if (!StatutActualite.ACTIF.name().equals(dto.getStatut())
                    && !StatutActualite.INACTIF.name().equals(dto.getStatut())) {
                throw new BadRequestException(
                        "Statut invalide : seuls ACTIF et INACTIF sont acceptés par le PUT (l'archivage passe par DELETE).");
            }
            entity.setStatut(dto.getStatut());
        }
        entity.setTitre(dto.getTitre());
        entity.setContenuMd(dto.getContenuMd());
        entity.setDatePublication(dto.getDatePublication());
        entity.setDateExpiration(dto.getDateExpiration());
        Actualite saved = repository.save(entity);
        profilRepository.effacerParActualite(id);
        for (String p : profils) {
            profilRepository.save(new ActualiteProfil(null, id, p));
        }
        return enrichir(List.of(toDto(saved))).get(0);
    }

    /** DELETE = archivage logique (jamais de suppression physique) — onglet « Historique ». */
    public void archiver(Integer id) {
        Actualite entity = charger(id);
        if (StatutActualite.ARCHIVE.name().equals(entity.getStatut())) {
            throw new BusinessRuleException("Actualité déjà archivée.");
        }
        entity.setStatut(StatutActualite.ARCHIVE.name());
        entity.setDateArchivage(LocalDateTime.now());
        entity.setImArchiveur(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        repository.save(entity);
    }

    // ------------------------------------------------------------------ images

    /**
     * Ajout d'une image (Administrateur) : JPEG obligatoire (magic-bytes {@code FF D8 FF},
     * jamais le Content-Type déclaré) → 400 ; &gt; 10 Mo → 413 ; redimensionnée au serveur
     * (largeur max {@value #LARGEUR_MAX_PX} px) avant stockage. Position = fin de la mini-page.
     */
    public ActualiteImageDto ajouterImage(Integer idActualite, MultipartFile fichier) {
        Actualite actualite = charger(idActualite);
        if (StatutActualite.ARCHIVE.name().equals(actualite.getStatut())) {
            throw new BusinessRuleException("Actualité archivée : non modifiable.");
        }
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Image manquante : joignez un JPEG dans la partie « fichier ».");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Lecture du fichier impossible : " + e.getMessage());
        }
        boolean jpeg = contenu.length >= 3 && (contenu[0] & 0xFF) == 0xFF
                && (contenu[1] & 0xFF) == 0xD8 && (contenu[2] & 0xFF) == 0xFF;
        if (!jpeg) {
            throw new BadRequestException("Seul le JPEG est accepté pour les images d'actualité.");
        }
        if (contenu.length > IMAGE_MAX_OCTETS) {
            throw new PayloadTropVolumineuxException("Image trop volumineuse (" + contenu.length
                    + " octets ; max " + IMAGE_MAX_OCTETS + ").");
        }
        contenu = redimensionner(contenu);

        int ordre = imageRepository.findMetaByIdActualiteInOrderByOrdreAsc(List.of(idActualite)).stream()
                .mapToInt(ActualiteImageRepository.Meta::getOrdre).max().orElse(0) + 1;
        ActualiteImage image = new ActualiteImage();
        image.setIdActualite(idActualite);
        image.setNomFichier(fichier.getOriginalFilename());
        image.setFormat("image/jpeg");
        image.setTailleOctets((long) contenu.length);
        image.setSha256(sha256Hex(contenu));
        image.setOrdre(ordre);
        image.setDateDepot(LocalDateTime.now());
        image.setContenu(contenu);
        ActualiteImage saved = imageRepository.save(image);
        return new ActualiteImageDto(saved.getIdImage(), saved.getNomFichier(), saved.getTailleOctets(), saved.getOrdre());
    }

    /** Lecture d'une image (tout utilisateur authentifié) — 404 si elle n'appartient pas à l'actualité. */
    @Transactional(readOnly = true)
    public ActualiteImage image(Integer idActualite, Integer idImage) {
        return imageRepository.findByIdImageAndIdActualite(idImage, idActualite)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Image " + idImage + " introuvable pour l'actualité " + idActualite + "."));
    }

    public void supprimerImage(Integer idActualite, Integer idImage) {
        ActualiteImage img = image(idActualite, idImage);
        imageRepository.delete(img);
    }

    // ------------------------------------------------------------------ interne

    private Actualite charger(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Actualité introuvable : " + id));
    }

    /** Expiration = archivage automatique ({@code IM_ARCHIVEUR} null : système), au fil des lectures. */
    private void archiverExpirees() {
        repository.archiverExpirees(LocalDate.now(), LocalDateTime.now());
    }

    /** Profils cibles : au moins un, tous connus ({@link ProfilUtilisateur}) — sinon 400. Dédoublonnés, ordre conservé. */
    private Set<String> validerProfils(List<String> profilsCibles) {
        if (profilsCibles == null || profilsCibles.isEmpty()) {
            throw new BadRequestException(
                    "Au moins un profil cible est requis : le ciblage est un acte délibéré (jamais « tous » implicitement).");
        }
        Set<String> profils = new LinkedHashSet<>();
        for (String p : profilsCibles) {
            try {
                profils.add(ProfilUtilisateur.valueOf(p).name());
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new BadRequestException("Profil cible inconnu : « " + p + " ».");
            }
        }
        return profils;
    }

    private void validerContenuEtDates(ActualiteDto dto) {
        if (dto.getContenuMd() != null && BALISE_HTML.matcher(dto.getContenuMd()).find()) {
            throw new BadRequestException(
                    "Le contenu est du Markdown brut : aucune balise HTML n'est acceptée.");
        }
        if (dto.getDatePublication() != null && dto.getDateExpiration() != null
                && dto.getDateExpiration().isBefore(dto.getDatePublication())) {
            throw new BadRequestException("La date d'expiration ne peut pas précéder la date de publication.");
        }
    }

    /** Redimensionne le JPEG à {@value #LARGEUR_MAX_PX} px de large max (proportionnel) ; illisible → 400. */
    private byte[] redimensionner(byte[] contenu) {
        BufferedImage source;
        try {
            source = javax.imageio.ImageIO.read(new ByteArrayInputStream(contenu));
        } catch (IOException e) {
            source = null;
        }
        if (source == null) {
            throw new BadRequestException("Image JPEG illisible.");
        }
        if (source.getWidth() <= LARGEUR_MAX_PX) {
            return contenu;
        }
        int largeur = LARGEUR_MAX_PX;
        int hauteur = Math.max(1, Math.round((float) source.getHeight() * largeur / source.getWidth()));
        BufferedImage reduite = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = reduite.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, largeur, hauteur, null);
        g.dispose();
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(reduite, "jpg", sortie);
        } catch (IOException e) {
            throw new BadRequestException("Redimensionnement de l'image impossible : " + e.getMessage());
        }
        return sortie.toByteArray();
    }

    private static String sha256Hex(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static ActualiteDto toDto(Actualite entity) {
        ActualiteDto dto = new ActualiteDto();
        dto.setIdActualite(entity.getIdActualite());
        dto.setTitre(entity.getTitre());
        dto.setContenuMd(entity.getContenuMd());
        dto.setStatut(entity.getStatut());
        dto.setDatePublication(entity.getDatePublication());
        dto.setDateExpiration(entity.getDateExpiration());
        dto.setDateCreation(entity.getDateCreation());
        dto.setImAuteur(entity.getImAuteur());
        dto.setDateArchivage(entity.getDateArchivage());
        dto.setImArchiveur(entity.getImArchiveur());
        return dto;
    }

    /** Reporte profils cibles et métadonnées d'images (jamais le binaire) sur les DTO. */
    private List<ActualiteDto> enrichir(List<ActualiteDto> dtos) {
        List<Integer> ids = dtos.stream().map(ActualiteDto::getIdActualite).toList();
        if (ids.isEmpty()) {
            return dtos;
        }
        Map<Integer, List<String>> profils = profilRepository.findByIdActualiteIn(ids).stream()
                .collect(Collectors.groupingBy(ActualiteProfil::getIdActualite,
                        Collectors.mapping(ActualiteProfil::getProfil, Collectors.toList())));
        Map<Integer, List<ActualiteImageDto>> images = imageRepository.findMetaByIdActualiteInOrderByOrdreAsc(ids)
                .stream().collect(Collectors.groupingBy(ActualiteImageRepository.Meta::getIdActualite,
                        Collectors.mapping(m -> new ActualiteImageDto(m.getIdImage(), m.getNomFichier(),
                                m.getTailleOctets(), m.getOrdre()), Collectors.toList())));
        for (ActualiteDto dto : dtos) {
            dto.setProfilsCibles(profils.getOrDefault(dto.getIdActualite(), List.of()));
            dto.setImages(images.getOrDefault(dto.getIdActualite(), List.of()));
        }
        return dtos;
    }
}
