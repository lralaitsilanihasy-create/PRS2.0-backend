package cnm.prs.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entité JPA mappée sur la table {@code t_delegation_profil}.
 * Générée à partir du MLD (db_ppm110626.pgerd).
 *
 * <p>⚠️ Règle ajoutée (2026-08-14) — <strong>une ligne par paire</strong> : la paire
 * (délégant, délégué) est unique (contrainte {@code UQ_DELEGATION_PAIRE}, migration
 * {@code docs/migrations/2026-08-14_delegation_unicite_paires.sql}). L'habilitation se
 * pilote par {@code ACTIF}, jamais par des doublons.</p>
 */
@Entity
@Table(name = "t_delegation_profil", uniqueConstraints = @UniqueConstraint(
        name = "UQ_DELEGATION_PAIRE", columnNames = { "ID_PROFILE_DELEGANT", "ID_PROFILE_DELEGUE" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DelegationProfil {

    @Id
    @Column(name = "ID_DELEGATION", nullable = false)
    private Integer idDelegation;

    @Column(name = "ID_PROFILE_DELEGANT", nullable = false)
    private Integer idProfileDelegant;

    @Column(name = "ID_PROFILE_DELEGUE", nullable = false)
    private Integer idProfileDelegue;

    @Column(name = "ACTIF", nullable = false)
    private Boolean actif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PROFILE_DELEGANT", insertable = false, updatable = false)
    @JsonIgnore
    private Profile profileDelegant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PROFILE_DELEGUE", insertable = false, updatable = false)
    @JsonIgnore
    private Profile profileDelegue;
}
