package cnm.prs.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.SpecificationsFicheRepository;
import cnm.prs.repository.TypePieceJointeRepository;

/**
 * ⚠️ 2026-10-07 (constat E2 de la recette du DAO complet) — les pièces obligatoires qu'un dossier n'a plus à porter à part, parce que le
 * <strong>DAO complet</strong> joint depuis la fiche les contient : le CCAG (code {@code CCAG}, partie 2.3, toujours recopiée du document
 * type), et le CCTP (code {@code CCTP}) quand la fiche a ses spécifications techniques (partie 2.2). Sans spécifications, le CCTP reste
 * exigé. Les codes sont posés par V80 sur les types existants ; un type sans code n'est jamais exempté. Lu à la soumission du dossier et
 * au contrôle des pièces par le Secrétaire.
 */
@Component
@Transactional(readOnly = true)
public class PiecesExemptees {

    public static final String CCAG = "CCAG";
    public static final String CCTP = "CCTP";

    private final PieceJointeDossierRepository pieces;
    private final DocumentFicheMarcheRepository documents;
    private final SpecificationsFicheRepository specifications;
    private final TypePieceJointeRepository types;

    public PiecesExemptees(PieceJointeDossierRepository pieces, DocumentFicheMarcheRepository documents,
            SpecificationsFicheRepository specifications, TypePieceJointeRepository types) {
        this.pieces = pieces;
        this.documents = documents;
        this.specifications = specifications;
        this.types = types;
    }

    /** Les identifiants des types de pièces exemptés pour ce dossier ; vide sans DAO complet joint. */
    public Set<Integer> de(Integer idDossier) {
        List<DocumentFicheMarche> daoComplets = pieces.findByIdDossierAndIdDocumentFicheIsNotNull(idDossier).stream()
                .map(p -> documents.findById(p.getIdDocumentFiche()).orElse(null))
                .filter(d -> d != null && DaoCompletService.TYPE.equals(d.getType())).toList();
        Set<Integer> exemptes = new HashSet<>();
        if (daoComplets.isEmpty()) {
            return exemptes;
        }
        types.findFirstByCode(CCAG).map(TypePieceJointe::getIdTypePiece).ifPresent(exemptes::add);
        if (daoComplets.stream().anyMatch(d -> specifications.existsById(d.getIdFiche()))) {
            types.findFirstByCode(CCTP).map(TypePieceJointe::getIdTypePiece).ifPresent(exemptes::add);
        }
        return exemptes;
    }
}
