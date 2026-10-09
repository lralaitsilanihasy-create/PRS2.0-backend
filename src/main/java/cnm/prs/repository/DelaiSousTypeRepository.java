package cnm.prs.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.DelaiSousType;

/** ⚠️ M5b (manuel de contrôle, §B6 ; V99) — délais surchargés par sous-type. */
@Repository
public interface DelaiSousTypeRepository extends JpaRepository<DelaiSousType, DelaiSousType.Cle> {

    /**
     * Le référentiel complet en <strong>une</strong> requête : (null, étape, délai) pour les étapes, (sous-type, étape, délai) pour les
     * surcharges — l'accueil « À faire » et la liste des dossiers ont un budget de requêtes constant à tenir.
     */
    @Query(value = "select cast(null as varchar) as st, \"ETAPE\", \"DELAI_HEURES\" from public.tr_delai_standard "
            + "union all select \"ID_SOUS_TYPE\", \"ETAPE\", \"DELAI_HEURES\" from public.tr_delai_sous_type", nativeQuery = true)
    List<Object[]> toutLeReferentiel();
}
