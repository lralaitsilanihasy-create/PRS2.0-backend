package cnm.prs.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ Règle ajoutée (demande front du 2026-09-21, « Gestion de l'INTÉRIM », lot 1) — un <strong>intérim
 * désigné</strong> : « X (titulaire, Président ou Chef de commission) est absent du D1 au D2 ; Y
 * (intérimaire) agit à sa place, dans SON périmètre, sous sa PROPRE identité ». Table {@code t_interim}
 * (V34).
 *
 * <p>Calqué sur le {@link Mandat} : période, référence, pièce, <strong>jamais modifié ni effacé</strong>
 * (ni PUT ni DELETE) — une prolongation est un nouvel intérim, une fin avant terme une révocation
 * ({@code dateRevocation}). Le statut ({@link cnm.prs.enums.StatutInterim}) se <strong>déduit</strong> des
 * dates à la date du jour et n'est pas stocké.</p>
 *
 * <p>Ce qui distingue l'intérim de la délégation ascendante ({@code t_delegation_profil}) : la délégation
 * ouvre une <em>tâche de profil</em> à un supérieur, jamais une identité ; l'intérim ouvre les
 * <em>actes d'identité</em> du titulaire (dispatch, visa, réattribution, retrait, part de signature) à
 * une personne nommée, pour une période. Il descend même : un <strong>Membre</strong> peut suppléer un
 * CC (arbitrage Q1 du pilote).</p>
 *
 * <p>La pièce PDF, obligatoire, vit dans {@link InterimPiece} : cette table est lue à chaque requête d'un
 * CC ou d'un Membre, elle ne doit jamais charger le binaire.</p>
 */
@Entity
@Table(name = "t_interim", indexes = {
        @Index(name = "idx_interim_titulaire", columnList = "IM_TITULAIRE, DATE_DEBUT"),
        @Index(name = "idx_interim_interimaire", columnList = "IM_INTERIMAIRE, DATE_DEBUT")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Interim {

    /** Longueur d'une référence d'acteur (matricule), alignée sur V29/V31. */
    public static final int LONGUEUR_IM = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_INTERIM", nullable = false)
    private Integer idInterim;

    /** Le contrôleur absent. */
    @Column(name = "IM_TITULAIRE", nullable = false, length = LONGUEUR_IM)
    private String imTitulaire;

    /** Nom du titulaire <strong>figé</strong> à la désignation (« NOM Prénoms »). */
    @Column(name = "NOM_TITULAIRE", nullable = false, length = 200)
    private String nomTitulaire;

    /** Profil du titulaire à la désignation ({@code PRESIDENT} ou {@code CHEF_COMMISSION}) : celui que l'intérimaire exerce. */
    @Column(name = "PROFIL_TITULAIRE", nullable = false, length = 30)
    private String profilTitulaire;

    /** Localité du titulaire à la désignation ; {@code null} pour le Président (toutes localités). */
    @Column(name = "ID_LOCALITE_TITULAIRE", length = 5)
    private String idLocaliteTitulaire;

    /** Le désigné (≠ titulaire, contrainte {@code ck_interim_personnes}). */
    @Column(name = "IM_INTERIMAIRE", nullable = false, length = LONGUEUR_IM)
    private String imInterimaire;

    @Column(name = "NOM_INTERIMAIRE", nullable = false, length = 200)
    private String nomInterimaire;

    @Column(name = "PROFIL_INTERIMAIRE", nullable = false, length = 30)
    private String profilInterimaire;

    @Column(name = "ID_LOCALITE_INTERIMAIRE", length = 5)
    private String idLocaliteInterimaire;

    /** Période <strong>inclusive</strong>. */
    @Column(name = "DATE_DEBUT", nullable = false)
    private LocalDate dateDebut;

    /** Borne incluse ; nulle seulement pour {@code VACANCE_POSTE}. */
    @Column(name = "DATE_FIN")
    private LocalDate dateFin;

    /** Voir {@link cnm.prs.enums.MotifInterim}. */
    @Column(name = "MOTIF", nullable = false, length = 20)
    private String motif;

    /** Note de service ou décision de désignation. */
    @Column(name = "REFERENCE", nullable = false, length = 100)
    private String reference;

    /** Nom du fichier de la pièce (le contenu est dans {@link InterimPiece}). */
    @Column(name = "PIECE_NOM", length = 255)
    private String pieceNom;

    @Column(name = "PIECE_TAILLE")
    private Long pieceTaille;

    /** Qui a créé : le titulaire lui-même, ou l'Administrateur (repli). */
    @Column(name = "DESIGNE_PAR", nullable = false, length = LONGUEUR_IM)
    private String designePar;

    @Column(name = "NOM_DESIGNE_PAR", length = 200)
    private String nomDesignePar;

    @Column(name = "DATE_DESIGNATION", nullable = false)
    private LocalDateTime dateDesignation;

    /** Fin avant terme, à effet à cette date ; {@code null} sinon. */
    @Column(name = "DATE_REVOCATION")
    private LocalDate dateRevocation;

    @Column(name = "MOTIF_REVOCATION", length = 255)
    private String motifRevocation;

    @Column(name = "REVOQUE_PAR", length = LONGUEUR_IM)
    private String revoquePar;
}
