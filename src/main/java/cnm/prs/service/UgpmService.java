package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.CreerUgpmRequest;
import cnm.prs.dto.ModifierUgpmRequest;
import cnm.prs.dto.PieceJointeMetaDto;
import cnm.prs.dto.SuppressionLotResult;
import cnm.prs.dto.UgpmDto;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.PieceJointe;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.UgpmRepository;

/**
 * Gestion des UGPM (Administrateur) : création d'une UGPM rattachée à une PRMP de tutelle, avec son compte
 * d'authentification <strong>actif</strong> ({@code TYPE_ACTEUR=UGPM}, {@code REF_ACTEUR=ID_UGPM}).
 */
@Service
@Transactional
public class UgpmService {

    private final UgpmRepository ugpmRepository;
    private final PrmpRepository prmpRepository;
    private final CompteAuthRepository compteRepository;
    private final PasswordEncoder passwordEncoder;
    private final PieceJointeService pieceJointeService;

    public UgpmService(UgpmRepository ugpmRepository, PrmpRepository prmpRepository,
            CompteAuthRepository compteRepository, PasswordEncoder passwordEncoder,
            PieceJointeService pieceJointeService) {
        this.ugpmRepository = ugpmRepository;
        this.prmpRepository = prmpRepository;
        this.compteRepository = compteRepository;
        this.passwordEncoder = passwordEncoder;
        this.pieceJointeService = pieceJointeService;
    }

    public UgpmDto creer(CreerUgpmRequest req) {
        if (!prmpRepository.existsById(req.idPrmpTutelle())) {
            throw new BusinessRuleException("PRMP de tutelle inconnue : " + req.idPrmpTutelle() + ".");
        }
        if (ugpmRepository.existsById(req.idUgpm())) {
            throw new BusinessRuleException("Une UGPM existe déjà avec l'identifiant : " + req.idUgpm() + ".");
        }
        if (compteRepository.findByLogin(req.login()).isPresent()) {
            throw new BusinessRuleException("Ce login est déjà utilisé.");
        }
        Ugpm ugpm = new Ugpm();
        ugpm.setIdUgpm(req.idUgpm());
        ugpm.setLibelle(req.libelle());
        ugpm.setIdPrmpTutelle(req.idPrmpTutelle());
        ugpm.setNomUgpm(req.nomUgpm());
        ugpm.setPrenomsUgpm(req.prenomsUgpm());
        ugpm.setCin(req.cin());
        ugpm.setDateCin(req.dateCin());
        ugpm.setLieuCin(req.lieuCin());
        ugpm.setEmailUgpm(req.emailUgpm());
        ugpm.setTelUgpm(req.telUgpm());
        ugpmRepository.save(ugpm);
        // Compte actif immédiatement (créé par l'Administrateur), pas de workflow de validation.
        compteRepository.save(new CompteAuth(req.login(), passwordEncoder.encode(req.motDePasse()),
                TypeActeur.UGPM.name(), req.idUgpm(), true));
        return toDto(ugpm);
    }

    /**
     * Création avec pièces <strong>optionnelles</strong> (miroir PRMP, sans arrêté) : crée l'UGPM + son compte,
     * puis stocke la {@code CIN} et/ou la {@code PHOTO} sous la clé {@code idUgpm}. Transactionnel : un fichier
     * invalide (type/taille) annule la création (400).
     */
    public UgpmDto creerAvecPieces(CreerUgpmRequest req, MultipartFile cin, MultipartFile photo) {
        UgpmDto cree = creer(req);
        stockerSiPresente(req.idUgpm(), TypePieceJointe.CIN, cin);
        stockerSiPresente(req.idUgpm(), TypePieceJointe.PHOTO, photo);
        return cree;
    }

    /**
     * Dépose (ou remplace) une pièce d'une UGPM. {@code type} limité à {@code CIN}/{@code PHOTO}
     * ({@code ARRETE_NOMIN} → 400, l'UGPM n'a pas d'arrêté). <strong>404</strong> si l'UGPM est inconnue.
     */
    public PieceJointeMetaDto deposerPiece(String idUgpm, TypePieceJointe type, MultipartFile fichier) {
        exigerTypeUgpm(type);
        if (!ugpmRepository.existsById(idUgpm)) {
            throw new ResourceNotFoundException("UGPM introuvable : " + idUgpm + ".");
        }
        return stockerPiece(idUgpm, type, fichier);
    }

    /** Récupère une pièce d'une UGPM pour téléchargement. {@code ARRETE_NOMIN} → 400 ; pièce absente → 404. */
    @Transactional(readOnly = true)
    public PieceJointe telechargerPiece(String idUgpm, TypePieceJointe type) {
        exigerTypeUgpm(type);
        return pieceJointeService.telecharger(idUgpm, type);
    }

    private void stockerSiPresente(String idUgpm, TypePieceJointe type, MultipartFile fichier) {
        if (fichier != null && !fichier.isEmpty()) {
            stockerPiece(idUgpm, type, fichier);
        }
    }

    /** Stocke la pièce puis refuse une PHOTO qui n'est pas une image (JPEG/PNG) → 400 (rollback). */
    private PieceJointeMetaDto stockerPiece(String idUgpm, TypePieceJointe type, MultipartFile fichier) {
        PieceJointeMetaDto meta = pieceJointeService.stocker(idUgpm, type, fichier);
        if (type == TypePieceJointe.PHOTO && "application/pdf".equals(meta.format())) {
            throw new BadRequestException("La photo doit être une image (JPEG ou PNG), pas un PDF.");
        }
        return meta;
    }

    /** L'UGPM n'a pas d'arrêté de nomination : seules les pièces CIN et PHOTO sont autorisées. */
    private void exigerTypeUgpm(TypePieceJointe type) {
        if (type == TypePieceJointe.ARRETE_NOMIN) {
            throw new BadRequestException(
                    "L'UGPM n'a pas d'arrêté de nomination ; pièces autorisées : CIN, PHOTO.");
        }
    }

    @Transactional(readOnly = true)
    public List<UgpmDto> findAll() {
        return ugpmRepository.findAll().stream().map(this::toDto).toList();
    }

    /** UGPM rattachées à une PRMP de tutelle (liste, éventuellement vide si aucune ou PRMP inconnue). */
    @Transactional(readOnly = true)
    public List<UgpmDto> findByTutelle(String idPrmp) {
        return ugpmRepository.findByIdPrmpTutelle(idPrmp).stream().map(this::toDto).toList();
    }

    /**
     * UGPM rattachées à une localité <strong>via leur PRMP de tutelle</strong> : l'UGPM n'a pas de localité
     * propre, elle hérite du périmètre de sa PRMP (rattachée à la localité par ses entités contractantes
     * actives). Liste, éventuellement vide (aucune PRMP dans la localité, ou aucune UGPM) — pas de 404.
     */
    @Transactional(readOnly = true)
    public List<UgpmDto> findByLocalite(String idLocalite) {
        List<String> idsPrmp = prmpRepository.findByLocaliteViaEntitesActives(idLocalite).stream()
                .map(Prmp::getIdPrmp).toList();
        if (idsPrmp.isEmpty()) {
            return List.of();
        }
        return ugpmRepository.findByIdPrmpTutelleIn(idsPrmp).stream().map(this::toDto).toList();
    }

    /** Recherche partielle par nom (contient, insensible à la casse ; liste, vide si aucun résultat). */
    @Transactional(readOnly = true)
    public List<UgpmDto> findByNom(String nom) {
        return ugpmRepository.findByNomUgpmContainingIgnoreCase(nom).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public UgpmDto findById(String idUgpm) {
        return ugpmRepository.findById(idUgpm).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("UGPM introuvable : " + idUgpm + "."));
    }

    /**
     * Modifie les champs métier d'une UGPM (identité, libellé, PRMP de tutelle). L'identifiant (matricule)
     * et le compte d'authentification ne sont pas touchés. La nouvelle PRMP de tutelle doit exister.
     */
    public UgpmDto modifier(String idUgpm, ModifierUgpmRequest req) {
        Ugpm ugpm = ugpmRepository.findById(idUgpm)
                .orElseThrow(() -> new ResourceNotFoundException("UGPM introuvable : " + idUgpm + "."));
        if (!prmpRepository.existsById(req.idPrmpTutelle())) {
            throw new BusinessRuleException("PRMP de tutelle inconnue : " + req.idPrmpTutelle() + ".");
        }
        ugpm.setLibelle(req.libelle());
        ugpm.setIdPrmpTutelle(req.idPrmpTutelle());
        ugpm.setNomUgpm(req.nomUgpm());
        ugpm.setPrenomsUgpm(req.prenomsUgpm());
        ugpm.setCin(req.cin());
        ugpm.setDateCin(req.dateCin());
        ugpm.setLieuCin(req.lieuCin());
        ugpm.setEmailUgpm(req.emailUgpm());
        ugpm.setTelUgpm(req.telUgpm());
        return toDto(ugpmRepository.save(ugpm));
    }

    /**
     * Supprime une UGPM et son compte d'authentification (créés ensemble à {@link #creer}). Les dossiers
     * qu'elle a créés restent la propriété de la PRMP de tutelle (leur {@code CREE_PAR} est une simple trace,
     * pas une FK vers t_ugpm).
     */
    public void delete(String idUgpm) {
        if (!ugpmRepository.existsById(idUgpm)) {
            throw new ResourceNotFoundException("UGPM introuvable : " + idUgpm + ".");
        }
        supprimerUn(idUgpm);
    }

    /**
     * Suppression <strong>en lot</strong> par matricule, <strong>tolérante</strong> : supprime chaque UGPM
     * existante (et son compte), liste les matricules absents ; jamais d'échec global. Doublons ignorés.
     */
    public SuppressionLotResult supprimerLot(List<String> matricules) {
        List<String> supprimes = new ArrayList<>();
        List<String> introuvables = new ArrayList<>();
        for (String id : matricules.stream().distinct().toList()) {
            if (ugpmRepository.existsById(id)) {
                supprimerUn(id);
                supprimes.add(id);
            } else {
                introuvables.add(id);
            }
        }
        return new SuppressionLotResult(supprimes, introuvables);
    }

    /** Supprime une UGPM et son compte associé (sans contrôle d'existence — appelé après vérification). */
    private void supprimerUn(String idUgpm) {
        compteRepository.deleteAll(
                compteRepository.findByRefActeurAndTypeActeur(idUgpm, TypeActeur.UGPM.name()));
        ugpmRepository.deleteById(idUgpm);
    }

    private UgpmDto toDto(Ugpm u) {
        // Login du compte associé (REF_ACTEUR = idUgpm) — lecture seule, pour la réinitialisation du mot de passe.
        String login = compteRepository.findByRefActeurAndTypeActeur(u.getIdUgpm(), TypeActeur.UGPM.name())
                .stream().map(CompteAuth::getLogin).findFirst().orElse(null);
        return new UgpmDto(u.getIdUgpm(), u.getLibelle(), u.getIdPrmpTutelle(), u.getNomUgpm(),
                u.getPrenomsUgpm(), u.getCin(), u.getDateCin(), u.getLieuCin(),
                u.getEmailUgpm(), u.getTelUgpm(), login);
    }
}
