package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ciblage d'une actualité par profil (table {@code t_actualite_profil}) — spec du 2026-08-18.
 *
 * <p>Une actualité vise un ou plusieurs profils ; aucun profil ⇒ visible de personne (le ciblage
 * est un acte délibéré, jamais « tous » implicitement). ⚠️ Le profil est stocké par son
 * <strong>nom d'enum</strong> {@link cnm.prs.enums.ProfilUtilisateur} (colonne {@code PROFIL}) et
 * non par {@code ID_PROFILE} numérique : la correspondance numérique de {@code tr_profile} n'est
 * pas spécifiée par les règles, et le nom d'enum est déjà l'autorité de sécurité du projet
 * ({@code ROLE_<nom>}) — même rapprochement que partout ailleurs.</p>
 */
@Entity
@Table(name = "t_actualite_profil",
        uniqueConstraints = @UniqueConstraint(columnNames = { "ID_ACTUALITE", "PROFIL" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActualiteProfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ACTUALITE_PROFIL", nullable = false)
    private Integer idActualiteProfil;

    @Column(name = "ID_ACTUALITE", nullable = false)
    private Integer idActualite;

    /** Nom d'enum {@link cnm.prs.enums.ProfilUtilisateur} (ex. {@code MEMBRE}, {@code PRMP}). */
    @Column(name = "PROFIL", nullable = false, length = 30)
    private String profil;
}
