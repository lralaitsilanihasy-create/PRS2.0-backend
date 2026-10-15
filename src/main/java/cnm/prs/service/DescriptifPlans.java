package cnm.prs.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.TypePieceJointeRepository;

/**
 * ⚠️ V119 (2026-10-15, demande front « ccag-descriptif-travaux », §B3, H2 ; arbitrage du 15/10 : pièce du dossier, servie aux candidats) —
 * le <strong>descriptif technique et les plans</strong> d'un marché de travaux : pièce {@code DESCRIPTIF_PLANS} du dossier produit par la
 * fiche (plusieurs fichiers), exigée à la soumission par la liste du sous-type, et servie aux candidats avec les documents du DAO — un
 * candidat ne chiffre pas des travaux sans eux. En rectification, ce sont les fichiers de la version corrigée qui sont servis.
 */
@Component
@Transactional(readOnly = true)
public class DescriptifPlans {

    public static final String CODE_PIECE = "DESCRIPTIF_PLANS";
    public static final String INTITULE = "Descriptif technique et plans des travaux";

    private final DossierRepository dossiers;
    private final PieceJointeDossierRepository pieces;
    private final TypePieceJointeRepository types;

    public DescriptifPlans(DossierRepository dossiers, PieceJointeDossierRepository pieces, TypePieceJointeRepository types) {
        this.dossiers = dossiers;
        this.pieces = pieces;
        this.types = types;
    }

    /** Les fichiers servis pour la procédure de cette fiche ; vide sans dossier, sans type de pièce ou sans fichier joint. */
    public List<PieceJointeDossier> fichiers(Long idDmc) {
        Integer idDossier = dossiers.findIdDossierByIdDmc(idDmc).orElse(null);
        Integer idType = types.findFirstByCode(CODE_PIECE).map(t -> t.getIdTypePiece()).orElse(null);
        if (idDossier == null || idType == null) {
            return List.of();
        }
        List<PieceJointeDossier> tous = pieces.findByIdDossier(idDossier).stream().filter(p -> idType.equals(p.getIdTypePiece()))
                .sorted(Comparator.comparing(PieceJointeDossier::getIdPiece)).toList();
        List<PieceJointeDossier> corriges = tous.stream().filter(p -> Boolean.TRUE.equals(p.getVersionCorrigee())).toList();
        return corriges.isEmpty() ? tous : corriges;
    }
}
