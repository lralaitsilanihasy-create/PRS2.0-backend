package cnm.prs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.CompteAuth;

@Repository
public interface CompteAuthRepository extends JpaRepository<CompteAuth, String> {

    Optional<CompteAuth> findByLogin(String login);

    /** Résolution en lot des comptes d'un ensemble de logins (annuaire des acteurs, sans N+1). */
    List<CompteAuth> findByLoginIn(Collection<String> logins);

    List<CompteAuth> findByRefActeurAndTypeActeur(String refActeur, String typeActeur);

    /** Comptes selon leur état d'activation (ex. {@code false} = en attente de validation). */
    List<CompteAuth> findByActif(Boolean actif);

    /** Comptes par statut et type d'acteur (ex. inscriptions PRMP EN_ATTENTE). */
    List<CompteAuth> findByStatutAndTypeActeur(String statut, String typeActeur);

    /** Nombre de comptes à un statut donné pour un type d'acteur (compteur du menu Administrateur). */
    long countByStatutAndTypeActeur(String statut, String typeActeur);
}
