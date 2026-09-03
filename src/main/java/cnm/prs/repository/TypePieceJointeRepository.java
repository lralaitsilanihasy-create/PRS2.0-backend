package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.TypePieceJointe;

@Repository
public interface TypePieceJointeRepository extends JpaRepository<TypePieceJointe, Integer> {

    /** Types de pièces d'un type de dossier, triés par ordre d'affichage. */
    List<TypePieceJointe> findByIdTypeDossierOrderByOrdreAsc(String idTypeDossier);

    /** Types de pièces obligatoires d'un type de dossier (contrôle à la soumission). */
    List<TypePieceJointe> findByIdTypeDossierAndObligatoireTrue(String idTypeDossier);
}
