package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.TypePieceJointeDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.PieceSousType;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.mapper.TypePieceJointeMapper;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.PieceSousTypeRepository;
import cnm.prs.repository.TypePieceJointeRepository;

/**
 * ⚠️ 2026-10-08 (manuel de contrôle a priori, tranche M2, §B2 ; V95 ; arbitrages du pilote : pièces « * » exigées dès M2, plans
 * inchangés, sans fiche les pièces conditionnées par la catégorie servies sans obligation) — les pièces exigées d'un dossier :
 * <ul>
 *   <li>un sous-type qui a sa <strong>liste</strong> ({@code t_piece_sous_type}) n'utilise qu'elle ; les autres gardent les pièces de
 *   leur <strong>famille</strong> (comportement d'avant M2) ;</li>
 *   <li>une pièce conditionnée par la <strong>catégorie</strong> de la fiche (bordereau en fournitures, DQE en travaux) ou par la
 *   <strong>forme</strong> (contrat-cadre) ne vaut que si elle s'applique ; la fiche se lit par le dossier ({@code ID_DMC}) ou, pour un
 *   dossier de marché, par son lot ; sans fiche, la condition est inconnue : la pièce est servie <strong>sans obligation</strong>.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PiecesExigees {

    private final PieceSousTypeRepository associations;
    private final TypePieceJointeRepository types;
    private final AttributionRepository attributions;
    private final FicheMarcheService fiches;
    private final cnm.prs.repository.SousTypeDossierRepository sousTypes;

    public PiecesExigees(PieceSousTypeRepository associations, TypePieceJointeRepository types, AttributionRepository attributions,
            FicheMarcheService fiches, cnm.prs.repository.SousTypeDossierRepository sousTypes) {
        this.sousTypes = sousTypes;
        this.associations = associations;
        this.types = types;
        this.attributions = attributions;
        this.fiches = fiches;
    }

    /** Une pièce exigée : son type, son obligation pour ce dossier, sa condition. */
    public record Exigee(TypePieceJointe type, boolean obligatoire, int ordre, String categorie, String forme) {
    }

    /** Les pièces exigées d'un dossier, dans l'ordre. */
    public List<Exigee> pour(Dossier d) {
        List<PieceSousType> liste = d.getIdSousType() == null ? List.of() : associations.findByIdSousTypeOrderByOrdreAscIdAsc(d.getIdSousType());
        if (liste.isEmpty()) {
            return types.findByIdTypeDossierOrderByOrdreAsc(d.getIdTypeDossier()).stream()
                    .map(t -> new Exigee(t, Boolean.TRUE.equals(t.getObligatoire()), t.getOrdre() == null ? 0 : t.getOrdre(), null, null)).toList();
        }
        Contexte c = contexte(d);
        Map<Integer, TypePieceJointe> parId = types.findAllById(liste.stream().map(PieceSousType::getIdTypePiece).distinct().toList()).stream()
                .collect(Collectors.toMap(TypePieceJointe::getIdTypePiece, Function.identity()));
        List<Exigee> out = new ArrayList<>();
        for (PieceSousType a : liste) {
            TypePieceJointe t = parId.get(a.getIdTypePiece());
            if (t == null) {
                continue;
            }
            Boolean vaut = vaut(a, c);
            if (Boolean.FALSE.equals(vaut)) {
                continue;
            }
            // Condition inconnue (dossier sans fiche) : la pièce est servie, sans obligation.
            out.add(new Exigee(t, Boolean.TRUE.equals(a.getObligatoire()) && vaut != null, a.getOrdre(), a.getCategorie(), a.getForme()));
        }
        return out;
    }

    /** Les pièces exigées obligatoires d'un dossier. */
    public List<TypePieceJointe> obligatoires(Dossier d) {
        return pour(d).stream().filter(Exigee::obligatoire).map(Exigee::type).toList();
    }

    /** La liste d'un sous-type, sans condition résolue (référentiel, écran de dépôt) ; à défaut de liste propre, les pièces de sa famille. */
    public List<TypePieceJointeDto> duSousType(String idSousType) {
        List<PieceSousType> liste = associations.findByIdSousTypeOrderByOrdreAscIdAsc(idSousType);
        if (liste.isEmpty()) {
            return sousTypes.findById(idSousType).map(s -> types.findByIdTypeDossierOrderByOrdreAsc(s.getIdTypeDossier()).stream()
                    .map(TypePieceJointeMapper::toDto).toList()).orElse(List.of());
        }
        Map<Integer, TypePieceJointe> parId = types.findAllById(liste.stream().map(PieceSousType::getIdTypePiece).distinct().toList()).stream()
                .collect(Collectors.toMap(TypePieceJointe::getIdTypePiece, Function.identity()));
        return liste.stream().filter(a -> parId.containsKey(a.getIdTypePiece())).map(a -> dto(parId.get(a.getIdTypePiece()), a.getObligatoire(),
                a.getOrdre(), a.getCategorie(), a.getForme())).toList();
    }

    /** Les pièces exigées d'un dossier, en DTO (obligation résolue pour ce dossier). */
    public List<TypePieceJointeDto> dtoPour(Dossier d) {
        return pour(d).stream().map(e -> dto(e.type(), e.obligatoire(), e.ordre(), e.categorie(), e.forme())).toList();
    }

    private static TypePieceJointeDto dto(TypePieceJointe t, Boolean obligatoire, Integer ordre, String categorie, String forme) {
        TypePieceJointeDto x = TypePieceJointeMapper.toDto(t);
        x.setObligatoire(obligatoire);
        x.setOrdre(ordre);
        x.setCategorie(categorie);
        x.setForme(forme);
        return x;
    }

    // ------------------------------------------------------------------ la condition

    record Contexte(String categorie, String forme) {
    }

    /** Vrai : la pièce s'applique ; faux : elle ne s'applique pas ; nul : on ne sait pas (pas de fiche). */
    static Boolean vaut(PieceSousType a, Contexte c) {
        if (a.getCategorie() == null && a.getForme() == null) {
            return true;
        }
        if (a.getCategorie() != null) {
            if (c.categorie() == null) {
                return null;
            }
            if (!a.getCategorie().equals(c.categorie())) {
                return false;
            }
        }
        if (a.getForme() != null) {
            if (c.forme() == null) {
                return null;
            }
            boolean cadre = PieceSousType.CONTRAT_CADRE.equals(c.forme());
            if (PieceSousType.CONTRAT_CADRE.equals(a.getForme()) != cadre) {
                return false;
            }
        }
        return true;
    }

    /** La catégorie et la forme de la fiche du dossier : par son {@code ID_DMC}, ou par le lot dont il est le dossier de marché. */
    private Contexte contexte(Dossier d) {
        Long idDmc = d.getIdDmc() != null ? d.getIdDmc()
                : d.getIdDossier() == null ? null : attributions.findFirstByIdDossier(d.getIdDossier()).map(a -> a.getIdDmc()).orElse(null);
        if (idDmc == null) {
            return new Contexte(null, null);
        }
        return fiches.etatValide(idDmc).map(v -> new Contexte(v.categorie(), v.etat().getTypeMarche())).orElse(new Contexte(null, null));
    }
}
