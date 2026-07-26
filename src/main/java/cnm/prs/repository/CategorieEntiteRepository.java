package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.CategorieEntite;

@Repository
public interface CategorieEntiteRepository extends JpaRepository<CategorieEntite, String> {
}
