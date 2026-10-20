package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », lot G1 ; V138) — un champ saisi par le membre à l'examen (lot G3),
 * repris dans le Word par {@code {{SAISI:code}}}. Les options d'une liste sont séparées par des retours à la ligne.
 */
@Entity
@Table(name = "t_champ_saisi_modele_pv")
@Getter
@Setter
@NoArgsConstructor
public class ChampSaisiModelePv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CHAMP", nullable = false)
    private Long idChamp;

    @Column(name = "ID_VERSION", nullable = false)
    private Long idVersion;

    @Column(name = "CODE", nullable = false, length = 60)
    private String code;

    @Column(name = "LIBELLE", nullable = false)
    private String libelle;

    /** {@code TEXTE}, {@code TEXTE_LONG}, {@code LISTE}, {@code CASE}, {@code DATE}, {@code NOMBRE}, {@code PIECE}. */
    @Column(name = "TYPE", nullable = false, length = 20)
    private String type;

    @Column(name = "OPTIONS", columnDefinition = "text")
    private String options;

    @Column(name = "OBLIGATOIRE", nullable = false)
    private Boolean obligatoire;

    @Column(name = "AIDE")
    private String aide;

    /** Nom d'une condition déclarée de la version : le champ n'est demandé que si elle est vraie ; nul = toujours. */
    @Column(name = "VISIBLE_SI", length = 60)
    private String visibleSi;

    @Column(name = "ORDRE", nullable = false)
    private Integer ordre;
}
