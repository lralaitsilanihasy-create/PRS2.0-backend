package cnm.prs.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * ⚠️ Lot 6 (2026-09-17, §B1) — comptes <strong>connectables</strong>. Le login s'appuie sur le
     * booléen {@code ACTIF} : c'est lui, et non {@code STATUT}, qui dit ce qui est ouvert.
     */
    long countByActifTrue();

    /**
     * ⚠️ Lot 6 (2026-09-17, §B1) — comptes existants <strong>non connectables</strong>, hors
     * inscriptions encore en attente : désactivés par l'Administrateur ({@code ACTIF = false} alors que
     * {@code STATUT} vaut toujours {@code ACTIF}) ou refusés.
     *
     * <p>{@link cnm.prs.enums.StatutCompte} ne comporte <strong>pas</strong> de valeur
     * {@code DESACTIVE} — la désactivation ne touche que le booléen ({@code CompteAuthService.desactiver}) —,
     * d'où un prédicat sur {@code ACTIF} et non sur le statut. Le {@code statut is null} couvre les
     * lignes antérieures à l'introduction de la colonne : {@code <>} seul les exclurait (comparaison à
     * {@code null} = inconnu).</p>
     */
    @Query("""
            select count(c) from CompteAuth c
            where c.actif = false and (c.statut is null or c.statut <> :statutEnAttente)
            """)
    long compterNonConnectablesHorsAttente(@Param("statutEnAttente") String statutEnAttente);
}
