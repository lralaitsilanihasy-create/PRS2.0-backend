package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.Evaluation;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    /** ⚠️ 2026-10-20 (« liste-dossier-marche », §B2) — les procédures dont l'évaluation est ouverte. */
    @org.springframework.data.jpa.repository.Query("select e.idDmc from Evaluation e")
    java.util.List<Long> findIdsDmc();
}
