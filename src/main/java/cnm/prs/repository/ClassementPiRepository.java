package cnm.prs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import cnm.prs.entity.ClassementPi;

public interface ClassementPiRepository extends JpaRepository<ClassementPi, ClassementPi.Cle> {
}
