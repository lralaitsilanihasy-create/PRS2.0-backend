package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Valeur saisie d'un champ d'une version de fiche marché ({@code t_fiche_marche_valeur}, V35) — une ligne par
 * champ de source {@code SAISIE} renseigné. Les valeurs PPM et CADRAGE ne sont jamais écrites ici.
 */
@Entity
@Table(name = "t_fiche_marche_valeur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FicheMarcheValeur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_VALEUR", nullable = false)
    private Integer idValeur;

    @Column(name = "ID_FICHE", nullable = false)
    private Integer idFiche;

    /** {@code tr_champ_fiche_marche.CODE}, sans clé étrangère (un code ancien ne bloque pas l'import du référentiel). */
    @Column(name = "CODE_CHAMP", nullable = false, length = 20)
    private String codeChamp;

    /** Valeur telle que normalisée à l'enregistrement (nombre en chiffres, date ISO, code de liste, OUI/NON, texte). */
    @Column(name = "VALEUR", length = 4000)
    private String valeur;
}
