package cnm.prs.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.MotifTypeDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.MotifType;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MotifTypeRepository;
import cnm.prs.repository.SousTypeDossierRepository;
import cnm.prs.repository.TypeDossierRepository;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M4, §B4 ; V97 ; arbitrages du pilote : Q5 oui ; référentiel administrable semé du
 * manuel, texte inséré par le front, héritage des grilles) — les motifs-types de renvoi et d'avis défavorable.
 * <ul>
 *   <li>Les motifs d'un sous-type suivent ceux de sa grille de contrôle (M3) : les motifs <strong>communs</strong> de la famille, sauf
 *   grille propre, puis ceux de sa <strong>grille de base</strong> de proche en proche (DAOR ← DAOO), puis les siens ; seuls les actifs.</li>
 *   <li>Pour un dossier, un motif conditionné par la catégorie ou la forme qui ne s'applique pas à sa fiche est retiré ; sans fiche, il
 *   reste servi.</li>
 *   <li>Le serveur n'écrit rien dans le PV ni dans la lettre : le front y insère le texte choisi, que le Membre modifie.</li>
 * </ul>
 */
@Service
@Transactional
public class MotifsTypesService {

    static final Set<String> NATURES = Set.of(MotifType.RENVOI, MotifType.AVIS_DEFAVORABLE);
    static final Set<String> CATEGORIES = Set.of("FOURNITURES_SERVICES", "TRAVAUX", "PRESTATIONS_INTELLECTUELLES");
    static final Set<String> FORMES = Set.of("CONTRAT_CADRE", "AUTRE");

    private final MotifTypeRepository repository;
    private final TypeDossierRepository typeDossiers;
    private final SousTypeDossierRepository sousTypes;
    private final DossierRepository dossiers;
    private final GrilleControle grille;
    private final PiecesExigees pieces;
    private final ObjectProvider<DossierService> dossierService;

    public MotifsTypesService(MotifTypeRepository repository, TypeDossierRepository typeDossiers, SousTypeDossierRepository sousTypes,
            DossierRepository dossiers, GrilleControle grille, PiecesExigees pieces, ObjectProvider<DossierService> dossierService) {
        this.repository = repository;
        this.typeDossiers = typeDossiers;
        this.sousTypes = sousTypes;
        this.dossiers = dossiers;
        this.grille = grille;
        this.pieces = pieces;
        this.dossierService = dossierService;
    }

    /**
     * {@code ?sousType=} : les motifs actifs du sous-type (héritage, conditions servies non résolues) ; {@code ?typeDossier=} : tous les
     * motifs de la famille, inactifs compris (écran d'administration) ; sans paramètre : tout le référentiel. {@code ?nature=} filtre.
     */
    @Transactional(readOnly = true)
    public List<MotifTypeDto> findAll(String typeDossier, String sousType, String nature) {
        String n = natureOuNulle(nature);
        String famille = vide(typeDossier) ? null : typeDossier.trim();
        Stream<MotifType> motifs;
        if (!vide(sousType)) {
            SousTypeDossier st = sousTypes.findById(sousType.trim()).orElseThrow(() -> new BadRequestException(
                    "Sous-type de dossier inconnu : « " + sousType.trim() + " » (référentiel /api/sous-type-dossiers)."));
            if (famille != null && !famille.equals(st.getIdTypeDossier())) {
                throw new BadRequestException("Le sous-type « " + st.getIdSousType() + " » n'appartient pas à la famille « " + famille
                        + " » (famille : " + st.getIdTypeDossier() + ").");
            }
            motifs = duSousType(st.getIdTypeDossier(), st.getIdSousType()).stream();
        } else if (famille != null) {
            motifs = repository.findByIdTypeDossier(famille).stream().sorted(ORDRE);
        } else {
            motifs = repository.findAll().stream().sorted(ORDRE);
        }
        return motifs.filter(m -> n == null || n.equals(m.getNature())).map(MotifsTypesService::toDto).toList();
    }

    /** Les motifs d'un dossier : ceux de son sous-type, sans les motifs conditionnés qui ne s'appliquent pas à sa fiche. */
    @Transactional(readOnly = true)
    public List<MotifTypeDto> pourDossier(Integer idDossier, String nature) {
        String n = natureOuNulle(nature);
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        dossierService.getObject().controlerVisibilite(idDossier);
        PiecesExigees.Contexte c = pieces.contexte(d);
        return duSousType(d.getIdTypeDossier(), d.getIdSousType()).stream()
                .filter(m -> n == null || n.equals(m.getNature()))
                .filter(m -> !Boolean.FALSE.equals(PiecesExigees.vaut(m.getCategorie(), m.getForme(), c)))
                .map(MotifsTypesService::toDto).toList();
    }

    /** Les motifs actifs d'un sous-type : communs de la famille (sauf grille propre), puis la chaîne de base, puis les siens. */
    List<MotifType> duSousType(String famille, String sousType) {
        List<MotifType> famille_ = repository.findByIdTypeDossier(famille);
        Map<Integer, MotifType> retenus = new LinkedHashMap<>();
        List<String> niveaux = new ArrayList<>();
        if (!grille.grillePropre(sousType)) {
            niveaux.add(null);
        }
        niveaux.addAll(grille.chaine(sousType));
        for (String niveau : niveaux) {
            famille_.stream().filter(m -> Boolean.TRUE.equals(m.getActif()))
                    .filter(m -> niveau == null ? m.getIdSousType() == null : niveau.equals(m.getIdSousType()))
                    .sorted(ORDRE).forEach(m -> retenus.putIfAbsent(m.getIdMotif(), m));
        }
        return List.copyOf(retenus.values());
    }

    @Transactional(readOnly = true)
    public MotifTypeDto findById(Integer id) {
        return toDto(trouver(id));
    }

    public MotifTypeDto create(MotifTypeDto dto) {
        valider(dto);
        MotifType m = new MotifType();
        appliquer(m, dto);
        m.setActif(dto.getActif() == null || dto.getActif());
        return toDto(repository.save(m));
    }

    public MotifTypeDto update(Integer id, MotifTypeDto dto) {
        MotifType m = trouver(id);
        valider(dto);
        appliquer(m, dto);
        if (dto.getActif() != null) {
            m.setActif(dto.getActif());
        }
        return toDto(repository.save(m));
    }

    public void delete(Integer id) {
        repository.delete(trouver(id));
    }

    private MotifType trouver(Integer id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Motif-type introuvable : " + id));
    }

    private void valider(MotifTypeDto dto) {
        String famille = dto.getIdTypeDossier().trim();
        if (!typeDossiers.existsById(famille)) {
            throw new BadRequestException("Famille de dossier inconnue : « " + famille + " » (référentiel /api/type-dossiers).");
        }
        if (!vide(dto.getIdSousType())) {
            SousTypeDossier st = sousTypes.findById(dto.getIdSousType().trim()).orElseThrow(() -> new BadRequestException(
                    "Sous-type de dossier inconnu : « " + dto.getIdSousType().trim() + " » (référentiel /api/sous-type-dossiers)."));
            if (!famille.equals(st.getIdTypeDossier())) {
                throw new BadRequestException("Le sous-type « " + st.getIdSousType() + " » n'appartient pas à la famille « " + famille
                        + " » (famille : " + st.getIdTypeDossier() + ").");
            }
        }
        natureOuNulle(dto.getNature());
        if (!vide(dto.getCategorie()) && !CATEGORIES.contains(dto.getCategorie().trim())) {
            throw new BadRequestException("Catégorie inconnue : « " + dto.getCategorie().trim() + " » (attendu : " + CATEGORIES + ").");
        }
        if (!vide(dto.getForme()) && !FORMES.contains(dto.getForme().trim())) {
            throw new BadRequestException("Forme inconnue : « " + dto.getForme().trim() + " » (attendu : " + FORMES + ").");
        }
    }

    private static void appliquer(MotifType m, MotifTypeDto dto) {
        m.setIdTypeDossier(dto.getIdTypeDossier().trim());
        m.setIdSousType(vide(dto.getIdSousType()) ? null : dto.getIdSousType().trim());
        m.setNature(dto.getNature().trim());
        m.setLibelle(dto.getLibelle().trim());
        m.setTexte(dto.getTexte().trim());
        m.setOrdre(dto.getOrdre());
        m.setCategorie(vide(dto.getCategorie()) ? null : dto.getCategorie().trim());
        m.setForme(vide(dto.getForme()) ? null : dto.getForme().trim());
    }

    private static String natureOuNulle(String nature) {
        if (vide(nature)) {
            return null;
        }
        String n = nature.trim();
        if (!NATURES.contains(n)) {
            throw new BadRequestException("Nature de motif inconnue : « " + n + " » (attendu : RENVOI ou AVIS_DEFAVORABLE).");
        }
        return n;
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    private static final Comparator<MotifType> ORDRE = Comparator
            .comparing((MotifType m) -> m.getOrdre() == null ? Integer.MAX_VALUE : m.getOrdre()).thenComparing(MotifType::getIdMotif);

    static MotifTypeDto toDto(MotifType m) {
        return new MotifTypeDto(m.getIdMotif(), m.getIdTypeDossier(), m.getIdSousType(), m.getNature(), m.getLibelle(), m.getTexte(),
                m.getOrdre(), m.getCategorie(), m.getForme(), m.getActif());
    }
}
