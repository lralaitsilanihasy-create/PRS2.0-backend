package cnm.prs.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cnm.prs.entity.Dossier;

@Repository
public interface DossierRepository extends JpaRepository<Dossier, Integer> {

    /**
     * ⚠️ Règle ajoutée (2026-08-05, mise à jour des PPM) — versions issues d'un dossier donné. En régime
     * normal la liste contient au plus un élément soumis (la version suivante), plus éventuellement un
     * brouillon de mise à jour en cours. Sert la garde d'unicité et la descente de la chaîne des versions.
     */
    List<Dossier> findByIdDossierParent(Integer idDossierParent);

    /** Nombre de dossiers à un statut donné (compteurs du tableau de bord — vue globale). */
    long countByStatut(String statut);

    /** Nombre de dossiers à un statut donné dans une localité (compteurs scopés CC). */
    long countByStatutAndIdLocalite(String statut, String idLocalite);

    /** Nombre de dossiers d'une PRMP à un statut donné (compteurs du menu PRMP). */
    long countByStatutAndIdPrmp(String statut, String idPrmp);

    /** Existe-t-il au moins un dossier appartenant à cette PRMP ? (garde de suppression PRMP) */
    boolean existsByIdPrmp(String idPrmp);

    /** Nombre de dossiers d'une PRMP dans un ensemble de statuts (ex. vérifiés : PV_SIGNE/CLOTURE). */
    long countByStatutInAndIdPrmp(java.util.Collection<String> statuts, String idPrmp);

    /** Dossiers dont la date de référence est dans la période (pour les rapports périodiques). */
    List<Dossier> findByDateRefBetween(LocalDate debut, LocalDate fin);

    /** Dossiers d'une localité dont la date de référence est dans la période (rapport périodique du CC, §3.3). */
    List<Dossier> findByDateRefBetweenAndIdLocalite(LocalDate debut, LocalDate fin, String idLocalite);

    /** Dossiers d'une localité, sans bornage de période (rapport « tous les dossiers » du CC). */
    List<Dossier> findByIdLocalite(String idLocalite);

    /** Tous les dossiers, filtrés par statut si fourni (Président/Admin). {@code statut=null} → tous. */
    @Query("select d from Dossier d where (:statut is null or d.statut = :statut)")
    List<Dossier> findParStatut(@Param("statut") String statut);

    /**
     * ⚠️ Audit 2026-08-27 (lot D §3) — variante <strong>paginée en SQL</strong> de
     * {@link #findParStatut}, filtres famille et sous-type inclus (Président / Administrateur).
     */
    @Query("""
            select d from Dossier d where
                (:statut is null or d.statut = :statut)
            and (:type is null or d.idTypeDossier = :type)
            and (:sousType is null or d.idSousType = :sousType)
            """)
    Page<Dossier> findParStatutPagine(@Param("statut") String statut, @Param("type") String type,
            @Param("sousType") String sousType, Pageable pageable);

    /** Comptage des dossiers par statut (pipeline du tableau de bord, §3.2). [statut, nombre] */
    @Query("select d.statut, count(d) from Dossier d group by d.statut")
    List<Object[]> compterParStatut();

    /** Pipeline par statut filtré sur une localité (tableau de bord du CC, §3.3). */
    @Query("select d.statut, count(d) from Dossier d where d.idLocalite = :localite group by d.statut")
    List<Object[]> compterParStatutParLocalite(@Param("localite") String localite);

    /** Nombre de dossiers <strong>soumis</strong> (statut ≠ BROUILLON) — dénominateur du taux de conformité. */
    @Query("select count(d) from Dossier d where d.statut is null or d.statut <> 'BROUILLON'")
    long compterSoumis();

    /** Idem, pour une localité (tableau de bord du CC, §3.3). */
    @Query("""
            select count(d) from Dossier d
            where d.idLocalite = :localite and (d.statut is null or d.statut <> 'BROUILLON')
            """)
    long compterSoumisParLocalite(@Param("localite") String localite);

    /** Dossiers <strong>à réceptionner</strong> : soumis ({@code SOUMIS}) et sans réception (toutes localités). */
    @Query("""
            select d from Dossier d where d.statut = 'SOUMIS'
              and not exists (select 1 from Reception r where r.idDossier = d.idDossier)
            """)
    List<Dossier> findAReceptionner();

    /** Dossiers à réceptionner d'une localité (file du Secrétaire, §3.4). */
    @Query("""
            select d from Dossier d where d.statut = 'SOUMIS' and d.idLocalite = :localite
              and not exists (select 1 from Reception r where r.idDossier = d.idDossier)
            """)
    List<Dossier> findAReceptionnerParLocalite(@Param("localite") String localite);

    /** Compteur « à réceptionner » du Secrétaire (miroir de {@link #findAReceptionnerParLocalite}). */
    @Query("""
            select count(d) from Dossier d where d.statut = 'SOUMIS' and d.idLocalite = :localite
              and not exists (select 1 from Reception r where r.idDossier = d.idDossier)
            """)
    long countAReceptionnerParLocalite(@Param("localite") String localite);

    /** Statut du dossier rattaché à une réception (précondition du dispatch, §2.2/§2.3). */
    @Query("select d.statut from Dossier d, Reception r where r.idReception = :idReception and d.idDossier = r.idDossier")
    Optional<String> findStatutByReception(@Param("idReception") Integer idReception);

    /** Statut du dossier rattaché à un dispatch, via sa réception (précondition de l'examen, §2.4). */
    @Query("""
            select d.statut from Dossier d, Reception r, Dispatch di
            where di.idDispatch = :idDispatch and r.idReception = di.idReception and d.idDossier = r.idDossier
            """)
    Optional<String> findStatutByDispatch(@Param("idDispatch") Integer idDispatch);

    /** Identifiant du dossier rattaché à un dispatch, via sa réception (transition DISPATCHE→EXAMINE). */
    @Query("""
            select d.idDossier from Dossier d, Reception r, Dispatch di
            where di.idDispatch = :idDispatch and r.idReception = di.idReception and d.idDossier = r.idDossier
            """)
    Optional<Integer> findIdDossierByDispatch(@Param("idDispatch") Integer idDispatch);

    /**
     * Dossiers visibles d'une localité (§1) : un dossier appartient à la localité du contrôleur
     * qui l'a réceptionné ({@code Reception.imCtrlRecept → Controleur.idLocalite}) <strong>ou</strong>,
     * pour un dossier soumis pas encore réceptionné, de la localité de son PPM
     * ({@code Ppm.idLocalite}) — afin que le Secrétaire le voie avant la réception (Option A).
     */
    @Query("""
            select d from Dossier d where
                (d.statut is null or d.statut <> 'BROUILLON')
            and (
                d.idLocalite = :localite
             or exists (select 1 from Reception r
                        where r.idDossier = d.idDossier and r.ctrlRecept.idLocalite = :localite)
             or exists (select 1 from Ppm p
                        where p.idDossier = d.idDossier and p.idLocalite = :localite))
            """)
    List<Dossier> findVisiblesParLocalite(@Param("localite") String localite);

    /** Idem {@link #findVisiblesParLocalite}, en filtrant aussi par statut si fourni ({@code null} → tous). */
    @Query("""
            select d from Dossier d where
                (d.statut is null or d.statut <> 'BROUILLON')
            and (:statut is null or d.statut = :statut)
            and (
                d.idLocalite = :localite
             or exists (select 1 from Reception r
                        where r.idDossier = d.idDossier and r.ctrlRecept.idLocalite = :localite)
             or exists (select 1 from Ppm p
                        where p.idDossier = d.idDossier and p.idLocalite = :localite))
            """)
    List<Dossier> findVisiblesParLocaliteEtStatut(@Param("localite") String localite,
            @Param("statut") String statut);

    /**
     * ⚠️ Audit 2026-08-27 (lot D §3) — variante <strong>paginée en SQL</strong> de
     * {@link #findVisiblesParLocaliteEtStatut}, filtres famille et sous-type inclus. Périmètre
     * strictement identique à celui de la liste plate correspondante : c'est la même clause
     * {@code where}, à laquelle s'ajoutent les deux filtres que le service appliquait jusqu'ici en
     * mémoire, après avoir tout chargé.
     */
    @Query("""
            select d from Dossier d where
                (d.statut is null or d.statut <> 'BROUILLON')
            and (:statut is null or d.statut = :statut)
            and (:type is null or d.idTypeDossier = :type)
            and (:sousType is null or d.idSousType = :sousType)
            and (
                d.idLocalite = :localite
             or exists (select 1 from Reception r
                        where r.idDossier = d.idDossier and r.ctrlRecept.idLocalite = :localite)
             or exists (select 1 from Ppm p
                        where p.idDossier = d.idDossier and p.idLocalite = :localite))
            """)
    Page<Dossier> findVisiblesParLocalitePagine(@Param("localite") String localite,
            @Param("statut") String statut, @Param("type") String type,
            @Param("sousType") String sousType, Pageable pageable);

    /**
     * Vrai si le dossier est visible dans la localité donnée — via sa propre localité, sa réception
     * ou son PPM — et qu'il n'est pas un brouillon (les brouillons sont masqués aux contrôleurs).
     */
    @Query("""
            select (count(d) > 0) from Dossier d where d.idDossier = :idDossier
            and (d.statut is null or d.statut <> 'BROUILLON')
            and (
                d.idLocalite = :localite
             or exists (select 1 from Reception r
                        where r.idDossier = d.idDossier and r.ctrlRecept.idLocalite = :localite)
             or exists (select 1 from Ppm p
                        where p.idDossier = d.idDossier and p.idLocalite = :localite))
            """)
    boolean existsDansLocalite(@Param("idDossier") Integer idDossier, @Param("localite") String localite);

    /**
     * Dossiers d'une PRMP (§3.1) : ceux dont elle est <strong>propriétaire</strong>
     * ({@code t_dossier.ID_PRMP}, posé à la saisie — y compris les brouillons DAO/MAOO sans PPM),
     * ses PPM ({@code t_ppm.ID_PRMP}) et les marchés rattachés à ses PPM.
     */
    @Query("""
            select d from Dossier d where
               d.idPrmp = :idPrmp
               or exists (select 1 from Ppm p where p.idDossier = d.idDossier and p.idPrmp = :idPrmp)
               or exists (select 1 from Marche m, Ppm p2
                          where m.idDossier = d.idDossier and m.idPpm = p2.idPpm and p2.idPrmp = :idPrmp)
            """)
    List<Dossier> findVisiblesPourPrmp(@Param("idPrmp") String idPrmp);

    /** Idem {@link #findVisiblesPourPrmp}, en filtrant aussi par statut si fourni ({@code null} → tous). */
    @Query("""
            select d from Dossier d where
               (:statut is null or d.statut = :statut)
            and (
               d.idPrmp = :idPrmp
               or exists (select 1 from Ppm p where p.idDossier = d.idDossier and p.idPrmp = :idPrmp)
               or exists (select 1 from Marche m, Ppm p2
                          where m.idDossier = d.idDossier and m.idPpm = p2.idPpm and p2.idPrmp = :idPrmp))
            """)
    List<Dossier> findVisiblesPourPrmpEtStatut(@Param("idPrmp") String idPrmp,
            @Param("statut") String statut);

    /**
     * ⚠️ Audit 2026-08-27 (lot D §3) — variante <strong>paginée en SQL</strong> de
     * {@link #findVisiblesPourPrmpEtStatut}, filtres famille et sous-type inclus. Périmètre
     * strictement identique à celui de la liste plate correspondante.
     */
    @Query("""
            select d from Dossier d where
               (:statut is null or d.statut = :statut)
            and (:type is null or d.idTypeDossier = :type)
            and (:sousType is null or d.idSousType = :sousType)
            and (
               d.idPrmp = :idPrmp
               or exists (select 1 from Ppm p where p.idDossier = d.idDossier and p.idPrmp = :idPrmp)
               or exists (select 1 from Marche m, Ppm p2
                          where m.idDossier = d.idDossier and m.idPpm = p2.idPpm and p2.idPrmp = :idPrmp))
            """)
    Page<Dossier> findVisiblesPourPrmpPagine(@Param("idPrmp") String idPrmp, @Param("statut") String statut,
            @Param("type") String type, @Param("sousType") String sousType, Pageable pageable);

    /** Vrai si le dossier appartient à la PRMP (propriétaire {@code t_dossier.ID_PRMP}, ou via PPM/marché). */
    @Query("""
            select (count(d) > 0) from Dossier d where d.idDossier = :idDossier and (
               d.idPrmp = :idPrmp
               or exists (select 1 from Ppm p where p.idDossier = d.idDossier and p.idPrmp = :idPrmp)
               or exists (select 1 from Marche m, Ppm p2
                          where m.idDossier = d.idDossier and m.idPpm = p2.idPpm and p2.idPrmp = :idPrmp))
            """)
    boolean existsVisiblePourPrmp(@Param("idDossier") Integer idDossier, @Param("idPrmp") String idPrmp);

    /**
     * ⚠️ LOT 3a (2026-08-26) — §1 : identifiants des dossiers visibles d'une localité, projection de
     * {@link #findVisiblesParLocalite} sur la seule PK. Sert à scoper les ressources
     * <strong>enfants</strong> d'un dossier (lots, prévisions, échéances…) dont la table ne porte pas
     * la localité — voir {@link cnm.prs.security.PerimetreDossier}. Prédicat volontairement identique
     * à celui de la liste des dossiers : une seule définition de « dossier visible d'une localité ».
     */
    @Query("""
            select d.idDossier from Dossier d where
                (d.statut is null or d.statut <> 'BROUILLON')
            and (
                d.idLocalite = :localite
             or exists (select 1 from Reception r
                        where r.idDossier = d.idDossier and r.ctrlRecept.idLocalite = :localite)
             or exists (select 1 from Ppm p
                        where p.idDossier = d.idDossier and p.idLocalite = :localite))
            """)
    List<Integer> findIdsVisiblesParLocalite(@Param("localite") String localite);

    /**
     * ⚠️ LOT 3a (2026-08-26) — §3.1 : identifiants des dossiers d'une PRMP, projection de
     * {@link #findVisiblesPourPrmp} sur la seule PK (même usage que
     * {@link #findIdsVisiblesParLocalite}). Les <strong>brouillons</strong> y figurent : la PRMP
     * propriétaire lit et édite les enfants de son brouillon.
     */
    @Query("""
            select d.idDossier from Dossier d where
               d.idPrmp = :idPrmp
               or exists (select 1 from Ppm p where p.idDossier = d.idDossier and p.idPrmp = :idPrmp)
               or exists (select 1 from Marche m, Ppm p2
                          where m.idDossier = d.idDossier and m.idPpm = p2.idPpm and p2.idPrmp = :idPrmp)
            """)
    List<Integer> findIdsVisiblesPourPrmp(@Param("idPrmp") String idPrmp);

    /**
     * Dossiers d'un ensemble de statuts <strong>attribués à un Membre</strong> (via réception → dispatch
     * {@code imCtrlMembre}). Sert à la file « à examiner » du Membre attributaire (§2.4) : DISPATCHE
     * (premier examen) + A_REEXAMINER (⚠️ 2026-08-02 — réexamen après lettre de renvoi, pièces reçues).
     */
    @Query("""
            select d from Dossier d where d.statut in :statuts
              and exists (select 1 from Reception r, Dispatch di
                          where r.idDossier = d.idDossier and di.idReception = r.idReception
                            and di.imCtrlMembre = :im)
            """)
    List<Dossier> findAExaminerParMembre(@Param("statuts") List<String> statuts, @Param("im") String im);

    /** Compteur « à examiner » du Membre (miroir de {@link #findAExaminerParMembre}). */
    @Query("""
            select count(d) from Dossier d where d.statut in :statuts
              and exists (select 1 from Reception r, Dispatch di
                          where r.idDossier = d.idDossier and di.idReception = r.idReception
                            and di.imCtrlMembre = :im)
            """)
    long countAExaminerParMembre(@Param("statuts") List<String> statuts, @Param("im") String im);

    /**
     * Dossiers d'un ensemble de statuts attribués à un Membre, <strong>paginés</strong> — historique
     * « examinés » (EXAMINE + PV_SIGNE + CLOTURE) du Membre attributaire.
     */
    @Query("""
            select d from Dossier d where d.statut in :statuts
              and exists (select 1 from Reception r, Dispatch di
                          where r.idDossier = d.idDossier and di.idReception = r.idReception
                            and di.imCtrlMembre = :im)
            """)
    Page<Dossier> findExaminesParMembre(@Param("statuts") List<String> statuts, @Param("im") String im,
            Pageable pageable);

    /** Compteur « examinés » du Membre (miroir de {@link #findExaminesParMembre}). */
    @Query("""
            select count(d) from Dossier d where d.statut in :statuts
              and exists (select 1 from Reception r, Dispatch di
                          where r.idDossier = d.idDossier and di.idReception = r.idReception
                            and di.imCtrlMembre = :im)
            """)
    long countExaminesParMembre(@Param("statuts") List<String> statuts, @Param("im") String im);

    /**
     * File « à vérifier » du Vérificateur (§3.6, ⚠️ règle ajoutée) : dossiers de la localité sur
     * lesquels le vérificateur a encore une ACTION à mener — {@code EN_VERIFICATION} (vérifier OU
     * transmettre la décision selon l'avis), {@code EN_ATTENTE_DECISION_PRMP} (lecture seule, en
     * attente de la PRMP) et {@code OBSERVATIONS_LEVEES} (approbation + levée à transmettre à SIGMP).
     *
     * ⚠️ <strong>Règle MODIFIÉE (demande user 2026-08-04)</strong> : {@code DECISION_TRANSMISE_SIGMP}
     * ne figure PLUS ici. Le dossier quitte « à vérifier » dès la <strong>transmission de la décision à
     * SIGMP</strong> — le travail du vérificateur est terminé, l'archivage revient à l'Assistant — et
     * bascule aussitôt dans « vérifiés / clôturés » ({@link #findVerifiesParLocalite}).
     * Auparavant il y restait jusqu'au {@code CLOTURE}.
     */
    @Query("""
            select d from Dossier d where d.statut in
              ('EN_VERIFICATION', 'EN_ATTENTE_DECISION_PRMP', 'OBSERVATIONS_LEVEES')
              and exists (select 1 from Reception r
                          where r.idDossier = d.idDossier and r.ctrlRecept.idLocalite = :loc)
            """)
    List<Dossier> findAVerifierParLocalite(@Param("loc") String loc);

    /** Membres attributaires d'un dossier (dispatchs de ses réceptions) — reprise après lettre de renvoi. */
    @Query("""
            select distinct di.imCtrlMembre from Dispatch di where di.imCtrlMembre is not null
              and di.idReception in (select r.idReception from Reception r where r.idDossier = :idDossier)
            """)
    List<String> findMembresAttributaires(@Param("idDossier") Integer idDossier);

    /** Compteur « à vérifier » du Vérificateur (miroir EXACT de {@link #findAVerifierParLocalite} — badge du menu). */
    @Query("""
            select count(d) from Dossier d where d.statut in
              ('EN_VERIFICATION', 'EN_ATTENTE_DECISION_PRMP', 'OBSERVATIONS_LEVEES')
              and exists (select 1 from Reception r
                          where r.idDossier = d.idDossier and r.ctrlRecept.idLocalite = :loc)
            """)
    long countAVerifierParLocalite(@Param("loc") String loc);

    /**
     * Historique « vérifiés / clôturés » du Vérificateur (§3.6, ⚠️ règle ajoutée), paginé, lecture seule :
     * dossiers de la localité ayant un PV SIGNE — qu'ils aient été <strong>auto-clôturés</strong>
     * à la signature (FAV/DEF/NSP) ou clôturés après levée des observations (FAVR).
     *
     * ⚠️ <strong>Règle MODIFIÉE (demande user 2026-08-04)</strong> : la liste couvre désormais
     * {@code DECISION_TRANSMISE_SIGMP} <em>en plus</em> de {@code CLOTURE}. Un dossier dont la décision
     * est transmise à SIGMP est <strong>vérifié</strong> (le vérificateur n'a plus rien à y faire) même
     * si l'archivage par l'Assistant, qui le clôturera, n'a pas encore eu lieu : il apparaît ici dès la
     * transmission et y reste après la clôture. Complémentaire de {@link #findAVerifierParLocalite},
     * qui l'exclut au même instant — aucun dossier ne peut être dans les deux files ni dans aucune.
     */
    @Query("""
            select d from Dossier d where d.statut in ('DECISION_TRANSMISE_SIGMP', 'CLOTURE')
              and exists (select 1 from Reception r, Dispatch di, Examen e, PvExamen pv
                          where r.idDossier = d.idDossier and di.idReception = r.idReception
                            and e.idDispatch = di.idDispatch and pv.idExamen = e.idExamen
                            and pv.statutPv = 'SIGNE' and r.ctrlRecept.idLocalite = :loc)
            """)
    Page<Dossier> findVerifiesParLocalite(@Param("loc") String loc, Pageable pageable);

    /** Compteur « vérifiés / clôturés » du Vérificateur (miroir EXACT de {@link #findVerifiesParLocalite}). */
    @Query("""
            select count(d) from Dossier d where d.statut in ('DECISION_TRANSMISE_SIGMP', 'CLOTURE')
              and exists (select 1 from Reception r, Dispatch di, Examen e, PvExamen pv
                          where r.idDossier = d.idDossier and di.idReception = r.idReception
                            and e.idDispatch = di.idDispatch and pv.idExamen = e.idExamen
                            and pv.statutPv = 'SIGNE' and r.ctrlRecept.idLocalite = :loc)
            """)
    long countVerifiesParLocalite(@Param("loc") String loc);

    /**
     * Dossiers retirables de la PRMP dont elle est propriétaire — liste déroulante (⚠️ règle ajoutée §3.3).
     * Éligibles = statuts « avant PV signé » ({@code :statuts} = {@link cnm.prs.enums.StatutDossier#NOMS_AVANT_PV_SIGNE},
     * source unique partagée avec la garde du POST), et sans demande déjà {@code EN_ATTENTE}/{@code REFUSEE}.
     */
    @Query("""
            select d from Dossier d
            where d.idPrmp = :idPrmp
              and d.statut in :statuts
              and not exists (select 1 from DemandeRetrait dr
                              where dr.idDossier = d.idDossier
                                and dr.statut in ('EN_ATTENTE', 'REFUSEE'))
            """)
    List<Dossier> findRetirablesPourPrmp(@Param("idPrmp") String idPrmp, @Param("statuts") List<String> statuts);

    /** Prochaine PK dossier, allouée par la séquence serveur (Voie B — l'id client est ignoré). */
    @Query(value = "select nextval('seq_dossier')", nativeQuery = true)
    Long nextIdDossier();

    /**
     * File « en attente PRMP » du Vérificateur (⚠️ règle ajoutée), lecture seule : dossiers
     * {@code EN_ATTENTE_DECISION_PRMP} de la localité (observations non levées, en attente de décision PRMP).
     */
    @Query("""
            select d from Dossier d where d.statut = 'EN_ATTENTE_DECISION_PRMP'
              and exists (select 1 from Reception r
                          where r.idDossier = d.idDossier and r.ctrlRecept.idLocalite = :loc)
            """)
    List<Dossier> findEnAttentePrmpParLocalite(@Param("loc") String loc);

    /** Compteur « en attente PRMP » du Vérificateur (miroir de {@link #findEnAttentePrmpParLocalite}). */
    @Query("""
            select count(d) from Dossier d where d.statut = 'EN_ATTENTE_DECISION_PRMP'
              and exists (select 1 from Reception r
                          where r.idDossier = d.idDossier and r.ctrlRecept.idLocalite = :loc)
            """)
    long countEnAttentePrmpParLocalite(@Param("loc") String loc);
}
