package cnm.prs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.TypePieceJointe;

@Repository
public interface TypePieceJointeRepository extends JpaRepository<TypePieceJointe, Integer> {

    /** Types de pièces d'un type de dossier, triés par ordre d'affichage. */
    List<TypePieceJointe> findByIdTypeDossierOrderByOrdreAsc(String idTypeDossier);

    /** Types de pièces obligatoires d'un type de dossier (contrôle à la soumission). */
    List<TypePieceJointe> findByIdTypeDossierAndObligatoireTrue(String idTypeDossier);

    /**
     * Type de pièce d'un type de dossier repéré par son code stable (ex. {@code AGPM}) — support de
     * l'obligation <strong>conditionnelle</strong> à la soumission. {@code Optional.empty()} si le
     * référentiel ne définit pas encore cette pièce (l'admin doit la créer).
     */
    Optional<TypePieceJointe> findFirstByIdTypeDossierAndCode(String idTypeDossier, String code);
}
