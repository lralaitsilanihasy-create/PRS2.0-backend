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

    /**
     * ⚠️ Lot 6 (2026-09-17, §B1) — inscriptions à un statut donné pour <strong>plusieurs</strong> types
     * d'acteur.
     *
     * <p>Remplace le comptage sur le seul type PRMP : le badge du menu doit compter ce que l'écran
     * liste, or {@code InscriptionService.enAttente()} rend les inscriptions PRMP <strong>et</strong>
     * UGPM. Un badge qui annonce 5 au-dessus d'une liste de 7 est un défaut, pas un contrat.</p>
     */
    long countByStatutAndTypeActeurIn(String statut, Collection<String> typesActeur);

    /**
     * ⚠️ Lot 6 (2026-09-17, §B1) — comptes <strong>connectables</strong>. Le login s'appuie sur le
     * booléen {@code ACTIF} : c'est lui, et non {@code STATUT}, qui dit ce qui est ouvert.
     */
    long countByActifTrue();

    /**
     * ⚠️ Lot 6 (2026-09-17, §B1) — comptes <strong>suspendus</strong> : validés puis fermés par
     * l'Administrateur, c'est-à-dire {@code ACTIF = false} alors que {@code STATUT} vaut toujours
     * {@code ACTIF}.
     *
     * <p><strong>Les inscriptions refusées ({@code STATUT = REFUSE}) en sont exclues</strong>
     * (correction du 2026-09-17) : une inscription qu'on n'a jamais ouverte n'est pas un compte qu'on a
     * fermé, et la tuile « comptes suspendus » de l'accueil est une mesure de sécurité — les y verser
     * la gonflerait. Les deux restent distinguables bien que {@link cnm.prs.enums.StatutCompte} n'ait
     * pas de valeur {@code DESACTIVE}, parce que {@code CompteAuthService.desactiver} ne touche que le
     * booléen. Le {@code statut is null} couvre les lignes antérieures à l'introduction de la colonne,
     * qu'une égalité seule exclurait (comparaison à {@code null} = inconnu) ; c'est le même périmètre
     * que {@code SUSPENDU} dans l'annuaire, pour que l'accueil et l'annuaire comptent pareil.</p>
     */
    @Query("""
            select count(c) from CompteAuth c
            where c.actif = false and (c.statut is null or c.statut = :statutActif)
            """)
    long compterSuspendus(@Param("statutActif") String statutActif);
}
