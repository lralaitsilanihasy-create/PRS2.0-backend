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

    /** ⚠️ R2 (manuel révisé, V102) — « en ligne » d'un acte de gestion (son marché) et d'une déclaration sans suite (sa demande). */
    private final cnm.prs.repository.ActeGestionRepository actes;
    private final cnm.prs.repository.SansSuiteRepository sansSuites;
    /** ⚠️ R3a (§B3) — la transition SIGMP : terminée, les pièces {@code FACULTATIVE_EN_LIGNE} sont retirées des procédures en ligne. */
    private final ParametreService parametres;

    public PiecesExigees(PieceSousTypeRepository associations, TypePieceJointeRepository types, AttributionRepository attributions,
            FicheMarcheService fiches, cnm.prs.repository.SousTypeDossierRepository sousTypes, cnm.prs.repository.ActeGestionRepository actes,
            cnm.prs.repository.SansSuiteRepository sansSuites, ParametreService parametres) {
        this.parametres = parametres;
        this.actes = actes;
        this.sansSuites = sansSuites;
        this.sousTypes = sousTypes;
        this.associations = associations;
        this.types = types;
        this.attributions = attributions;
        this.fiches = fiches;
    }

    /** Une pièce exigée : son type, son obligation pour ce dossier, sa condition (catégorie, forme, et ⚠️ R2 : {@code condition} de ligne). */
    public record Exigee(TypePieceJointe type, boolean obligatoire, int ordre, String categorie, String forme, String condition) {
    }

    /**
     * Les pièces exigées d'un dossier, dans l'ordre. ⚠️ R2 (manuel révisé, lot 1, §B4 ; V102) — pour une procédure <strong>en ligne</strong>,
     * une pièce {@code PAPIER} n'est pas servie et une pièce {@code FACULTATIVE_EN_LIGNE} perd son obligation ; hors ligne, rien ne change.
     */
    public List<Exigee> pour(Dossier d) {
        List<PieceSousType> liste = d.getIdSousType() == null ? List.of() : associations.findByIdSousTypeOrderByOrdreAscIdAsc(d.getIdSousType());
        if (liste.isEmpty()) {
            return types.findByIdTypeDossierOrderByOrdreAsc(d.getIdTypeDossier()).stream()
                    .map(t -> new Exigee(t, Boolean.TRUE.equals(t.getObligatoire()), t.getOrdre() == null ? 0 : t.getOrdre(), null, null, null))
                    .toList();
        }
        Contexte c = contexte(d);
        // ⚠️ R3a (§B3) — la transition SIGMP terminée, l'imprimé facultatif disparaît des procédures en ligne, comme une pièce PAPIER.
        boolean transitionTerminee = c.enLigne() && liste.stream().anyMatch(a -> PieceSousType.FACULTATIVE_EN_LIGNE.equals(a.getCondition()))
                && parametres.transitionSigmpTerminee();
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
            // ⚠️ R2 — en ligne : la pièce « papier » disparaît, la pièce facultative en ligne reste sans obligation.
            if (c.enLigne() && PieceSousType.PAPIER.equals(a.getCondition())) {
                continue;
            }
            boolean facultative = c.enLigne() && PieceSousType.FACULTATIVE_EN_LIGNE.equals(a.getCondition());
            if (facultative && transitionTerminee) {
                continue;
            }
            // Condition inconnue (dossier sans fiche) : la pièce est servie, sans obligation.
            out.add(new Exigee(t, Boolean.TRUE.equals(a.getObligatoire()) && vaut != null && !facultative, a.getOrdre(), a.getCategorie(),
                    a.getForme(), a.getCondition()));
        }
        return out;
    }

    /** ⚠️ R2 — la procédure du dossier est-elle conduite dans l'application ? */
    public boolean enLigne(Dossier d) {
        return contexte(d).enLigne();
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
                a.getOrdre(), a.getCategorie(), a.getForme(), a.getCondition())).toList();
    }

    /** Les pièces exigées d'un dossier, en DTO (obligation résolue pour ce dossier). */
    public List<TypePieceJointeDto> dtoPour(Dossier d) {
        return pour(d).stream().map(e -> dto(e.type(), e.obligatoire(), e.ordre(), e.categorie(), e.forme(), e.condition())).toList();
    }

    private static TypePieceJointeDto dto(TypePieceJointe t, Boolean obligatoire, Integer ordre, String categorie, String forme, String condition) {
        TypePieceJointeDto x = TypePieceJointeMapper.toDto(t);
        x.setObligatoire(obligatoire);
        x.setOrdre(ordre);
        x.setCategorie(categorie);
        x.setForme(forme);
        x.setCondition(condition);
        return x;
    }

    // ------------------------------------------------------------------ la condition

    /** La catégorie et la forme de la fiche (nuls sans fiche), et ⚠️ R2 : la procédure est-elle conduite dans l'application. */
    record Contexte(String categorie, String forme, boolean enLigne) {
    }

    /** Vrai : la pièce s'applique ; faux : elle ne s'applique pas ; nul : on ne sait pas (pas de fiche). */
    static Boolean vaut(PieceSousType a, Contexte c) {
        return vaut(a.getCategorie(), a.getForme(), c);
    }

    /** ⚠️ M3 — la même règle pour un point de contrôle : vrai, faux, ou nul (pas de fiche). */
    static Boolean vaut(String categorie, String forme, Contexte c) {
        if (categorie == null && forme == null) {
            return true;
        }
        if (categorie != null) {
            if (c.categorie() == null) {
                return null;
            }
            if (!categorie.equals(c.categorie())) {
                return false;
            }
        }
        if (forme != null) {
            if (c.forme() == null) {
                return null;
            }
            boolean cadre = PieceSousType.CONTRAT_CADRE.equals(c.forme());
            if (PieceSousType.CONTRAT_CADRE.equals(forme) != cadre) {
                return false;
            }
        }
        return true;
    }

    /**
     * La catégorie et la forme de la fiche du dossier : par son {@code ID_DMC}, ou par le lot dont il est le dossier de marché, ou — ⚠️ R2 —
     * par le marché de l'acte de gestion. « En ligne » : la fiche existe (DMC), le marché est attribué en ligne (DDM, acte de gestion), ou
     * la déclaration sans suite a été demandée depuis la procédure (DSS).
     */
    Contexte contexte(Dossier d) {
        Long idDmc = d.getIdDmc();
        boolean enLigne = idDmc != null;
        if (idDmc == null && d.getIdDossier() != null) {
            Integer marche = ActesGestionService.FAMILLE_DGC.equals(d.getIdTypeDossier())
                    ? actes.findByIdDossier(d.getIdDossier()).map(a -> a.getIdDossierMarche()).orElse(null) : d.getIdDossier();
            idDmc = marche == null ? null : attributions.findFirstByIdDossier(marche).map(a -> a.getIdDmc()).orElse(null);
            enLigne = idDmc != null || ("DSS".equals(d.getIdTypeDossier()) && sansSuites.existsByIdDossier(d.getIdDossier()));
        }
        if (idDmc == null) {
            return new Contexte(null, null, enLigne);
        }
        boolean ligne = enLigne;
        // ⚠️ R8 (manuel révisé, lot 2, §B3 ; V110 ; arbitrage Q1) — « en ligne » se lit au MODE de la fiche validée : remise
        // électronique. En PAPIER, les listes de pièces restent entières et les contrôles automatiques sont sans objet. Sans fiche
        // validée (dossier rattaché autrement), le rattachement vaut comme avant.
        return fiches.etatValide(idDmc).map(v -> new Contexte(v.categorie(), v.etat().getTypeMarche(),
                ligne && RemiseElectronique.electronique(v.etat().getCadrage()))).orElse(new Contexte(null, null, ligne));
    }
}
