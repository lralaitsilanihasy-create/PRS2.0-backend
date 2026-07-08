package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.CreerPrmpRequest;
import cnm.prs.dto.PieceJointeMetaDto;
import cnm.prs.dto.PrmpDto;
import cnm.prs.dto.SuppressionLotPrmpResult;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.PieceJointe;
import cnm.prs.entity.Prmp;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PrmpMapper;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.DemandeRetraitRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.IndicateurPrmpRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpEntiteRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.UgpmRepository;

/**
 * Logique métier pour {@link Prmp}.
 */
@Service
@Transactional
public class PrmpService {

    private final PrmpRepository repository;
    private final CompteAuthRepository compteRepository;
    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final PrmpEntiteRepository prmpEntiteRepository;
    private final DemandeRetraitRepository demandeRetraitRepository;
    private final IndicateurPrmpRepository indicateurPrmpRepository;
    private final UgpmRepository ugpmRepository;
    private final PieceJointeService pieceJointeService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public PrmpService(PrmpRepository repository, CompteAuthRepository compteRepository,
            DossierRepository dossierRepository, PpmRepository ppmRepository,
            PrmpEntiteRepository prmpEntiteRepository, DemandeRetraitRepository demandeRetraitRepository,
            IndicateurPrmpRepository indicateurPrmpRepository, UgpmRepository ugpmRepository,
            PieceJointeService pieceJointeService,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.compteRepository = compteRepository;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.prmpEntiteRepository = prmpEntiteRepository;
        this.pieceJointeService = pieceJointeService;
        this.demandeRetraitRepository = demandeRetraitRepository;
        this.indicateurPrmpRepository = indicateurPrmpRepository;
        this.ugpmRepository = ugpmRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<PrmpDto> findAll() {
        return repository.findAll().stream().map(PrmpMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PrmpDto findById(String id) {
        Prmp entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prmp introuvable : " + id));
        return PrmpMapper.toDto(entity);
    }

    /**
     * PRMP rattachées à une localité <strong>via leurs entités contractantes actives</strong> (la PRMP n'a pas
     * de localité propre). Liste distincte, éventuellement vide.
     */
    @Transactional(readOnly = true)
    public List<PrmpDto> findByLocalite(String idLocalite) {
        return repository.findByLocaliteViaEntitesActives(idLocalite).stream().map(PrmpMapper::toDto).toList();
    }

    /**
     * PRMP rattachée à une entité contractante via son affectation active (0 ou 1, invariant une seule PRMP
     * active par entité). Liste, vide si aucune.
     */
    @Transactional(readOnly = true)
    public List<PrmpDto> findByEntite(Integer idEntiteContract) {
        return repository.findByEntiteViaAffectationActive(idEntiteContract).stream().map(PrmpMapper::toDto).toList();
    }

    /** Recherche partielle par nom (contient, insensible à la casse). Liste, vide si aucun résultat. */
    @Transactional(readOnly = true)
    public List<PrmpDto> findByNom(String nom) {
        return repository.findByNomPrmpContainingIgnoreCase(nom).stream().map(PrmpMapper::toDto).toList();
    }

    /**
     * Création admin de la fiche PRMP, avec <strong>compte optionnel</strong> : si {@code login}/{@code motDePasse}
     * sont fournis (ensemble), un compte PRMP <strong>actif</strong> ({@code TYPE_ACTEUR=PRMP}, {@code refActeur=idPrmp})
     * est créé et utilisable immédiatement (parité avec {@code POST /api/ugpms}) ; sinon fiche seule (rétro-compat).
     * <strong>400</strong> si l'un des deux credentials manque ; <strong>409</strong> si l'idPrmp ou le login est déjà pris.
     */
    public PrmpDto create(CreerPrmpRequest req) {
        boolean hasLogin = req.login() != null && !req.login().isBlank();
        boolean hasMdp = req.motDePasse() != null && !req.motDePasse().isBlank();
        if (hasLogin != hasMdp) {
            throw new BadRequestException("login et motDePasse doivent être fournis ensemble (ou tous deux absents).");
        }
        if (repository.existsById(req.idPrmp())) {
            throw new BusinessRuleException("Cette PRMP (id " + req.idPrmp() + ") existe déjà.");
        }
        if (hasLogin && compteRepository.findByLogin(req.login()).isPresent()) {
            throw new BusinessRuleException("Ce login est déjà utilisé.");
        }
        Prmp entity = new Prmp();
        entity.setIdPrmp(req.idPrmp());
        entity.setNomPrmp(req.nomPrmp());
        entity.setPrenomsPrmp(req.prenomsPrmp());
        entity.setArreteNomin(req.arreteNomin());
        entity.setDateNomin(req.dateNomin());
        entity.setCin(req.cin());
        entity.setDateCin(req.dateCin());
        entity.setLieuCin(req.lieuCin());
        entity.setEmailPrmp(req.emailPrmp());
        entity.setTelPrmp(req.telPrmp());
        repository.save(entity);
        if (hasLogin) {
            // Compte actif immédiatement (créé par l'Administrateur), pas de workflow de validation.
            compteRepository.save(new CompteAuth(req.login(), passwordEncoder.encode(req.motDePasse()),
                    TypeActeur.PRMP.name(), req.idPrmp(), true));
        }
        return PrmpMapper.toDto(entity);
    }

    /**
     * Création admin <strong>avec pièces optionnelles</strong> (arrêté, CIN, photo) — miroir de l'inscription.
     * Les pièces présentes sont validées (type réel PDF/JPEG/PNG + taille) et stockées sous la clé {@code idPrmp} ;
     * un fichier invalide → 400 et la création est annulée (transaction). Le compte est créé si credentials fournis.
     */
    public PrmpDto createAvecPieces(CreerPrmpRequest req, MultipartFile arrete, MultipartFile cin, MultipartFile photo) {
        PrmpDto cree = create(req);
        String id = cree.getIdPrmp();
        stockerSiPresente(id, TypePieceJointe.ARRETE_NOMIN, arrete);
        stockerSiPresente(id, TypePieceJointe.CIN, cin);
        stockerSiPresente(id, TypePieceJointe.PHOTO, photo);
        return cree;
    }

    private void stockerSiPresente(String idPrmp, TypePieceJointe type, MultipartFile fichier) {
        if (fichier != null && !fichier.isEmpty()) {
            pieceJointeService.stocker(idPrmp, type, fichier);
        }
    }

    /** Dépose (ou remplace) une pièce d'une PRMP existante (clé = idPrmp). <strong>404</strong> si PRMP inconnue. */
    public PieceJointeMetaDto deposerPiece(String idPrmp, TypePieceJointe type, MultipartFile fichier) {
        if (!repository.existsById(idPrmp)) {
            throw new ResourceNotFoundException("Prmp introuvable : " + idPrmp);
        }
        return pieceJointeService.stocker(idPrmp, type, fichier);
    }

    /** Télécharge une pièce d'une PRMP (clé = idPrmp). <strong>404</strong> si la pièce est absente. */
    @Transactional(readOnly = true)
    public PieceJointe telechargerPiece(String idPrmp, TypePieceJointe type) {
        return pieceJointeService.telecharger(idPrmp, type);
    }

    public PrmpDto update(String id, PrmpDto dto) {
        Prmp existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prmp introuvable : " + id));
        existing.setNomPrmp(dto.getNomPrmp());
        existing.setPrenomsPrmp(dto.getPrenomsPrmp());
        existing.setArreteNomin(dto.getArreteNomin());
        existing.setDateNomin(dto.getDateNomin());
        existing.setCin(dto.getCin());
        existing.setDateCin(dto.getDateCin());
        existing.setLieuCin(dto.getLieuCin());
        existing.setEmailPrmp(dto.getEmailPrmp());
        existing.setTelPrmp(dto.getTelPrmp());
        return PrmpMapper.toDto(repository.save(existing));
    }

    /**
     * Supprime une PRMP et son compte d'authentification. <strong>Garde</strong> : refuse (409) tant que la PRMP
     * porte des données liées (dossiers, PPM, entités rattachées, demandes de retrait, indicateurs, UGPM de
     * tutelle) — pour éviter une perte massive et les violations de FK.
     */
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Prmp introuvable : " + id);
        }
        if (aDesDonneesLiees(id)) {
            throw new BusinessRuleException("Suppression impossible : la PRMP « " + id + " » a des données liées "
                    + "(dossiers, PPM, entités rattachées, demandes de retrait, indicateurs ou UGPM). "
                    + "Retirez d'abord ces éléments.");
        }
        supprimerUne(id);
    }

    /**
     * Suppression <strong>en lot</strong> par matricule, <strong>tolérante</strong> : supprime chaque PRMP
     * existante <em>sans données liées</em> (avec son compte) ; les absents vont dans {@code introuvables}, les
     * PRMP à données liées dans {@code bloques} (comme le 409 unitaire). Jamais d'échec global. Doublons ignorés.
     */
    public SuppressionLotPrmpResult supprimerLot(List<String> matricules) {
        List<String> supprimes = new ArrayList<>();
        List<String> introuvables = new ArrayList<>();
        List<String> bloques = new ArrayList<>();
        for (String id : matricules.stream().distinct().toList()) {
            if (!repository.existsById(id)) {
                introuvables.add(id);
            } else if (aDesDonneesLiees(id)) {
                bloques.add(id);
            } else {
                supprimerUne(id);
                supprimes.add(id);
            }
        }
        return new SuppressionLotPrmpResult(supprimes, introuvables, bloques);
    }

    /** Vrai si la PRMP porte des données liées (garde de suppression). */
    private boolean aDesDonneesLiees(String id) {
        return dossierRepository.existsByIdPrmp(id)
                || ppmRepository.countByIdPrmp(id) > 0
                || !prmpEntiteRepository.findByIdPrmp(id).isEmpty()
                || !demandeRetraitRepository.findByIdPrmp(id).isEmpty()
                || indicateurPrmpRepository.existsByIdPrmp(id)
                || !ugpmRepository.findByIdPrmpTutelle(id).isEmpty();
    }

    /** Supprime une PRMP et son compte associé (sans contrôle — appelé après vérification existence + garde). */
    private void supprimerUne(String id) {
        compteRepository.deleteAll(compteRepository.findByRefActeurAndTypeActeur(id, TypeActeur.PRMP.name()));
        repository.deleteById(id);
    }
}
