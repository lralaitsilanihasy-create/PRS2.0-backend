package cnm.prs.entity;

import cnm.prs.enums.BaremeSeuil;
import cnm.prs.enums.CategorieSeuil;
import cnm.prs.enums.TypeSeuil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, migration V32) — une valeur du
 * <strong>référentiel de seuils</strong> ({@code tr_seuil_marche}).
 *
 * <p>Les seuils par mode avaient été supprimés le 2026-07-04 ({@code c432e73}) avec la détermination
 * automatique du mode de passation : le PPM officiel ne porte pas de « situation » et le mode y est
 * <strong>saisi</strong>. Ce référentiel ne revient pas sur cette décision — il ne détermine ni ne bloque
 * rien, il sert <strong>uniquement à signaler</strong>. Sur le modèle du paramètre
 * {@code AGPM_SEUIL_MONTANT} : aucune valeur numérique n'est écrite dans le code, elles vivent toutes
 * ici, administrables.</p>
 *
 * <p><strong>Daté</strong>, parce qu'un arrêté est remplacé : une valeur vaut à partir de sa
 * {@link #dateEffet} et, une fois remplacée, jusqu'à sa {@link #dateFin} (exclue). Le pré-contrôle lit le
 * barème en vigueur à la date du plan, jamais « le dernier saisi » — sans quoi un plan ancien
 * deviendrait illisible le jour où les seuils changent.</p>
 *
 * <p>Les montants sont <strong>hors taxes</strong>, en ariary, comme les montants estimatifs de PRS
 * (arbitrage du pilote du 2026-09-18) : la comparaison est directe, sans conversion.</p>
 */
@Entity
@Table(name = "tr_seuil_marche")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SeuilMarche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_SEUIL", nullable = false)
    private Integer idSeuil;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE_SEUIL", nullable = false, length = 40)
    private TypeSeuil typeSeuil;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORIE_SEUIL", nullable = false, length = 40)
    private CategorieSeuil categorieSeuil;

    @Enumerated(EnumType.STRING)
    @Column(name = "BAREME", nullable = false, length = 20)
    private BaremeSeuil bareme;

    /** Montant hors taxes, en ariary, à partir duquel la conséquence du {@link #typeSeuil} s'applique. */
    @Column(name = "MONTANT", nullable = false)
    private BigDecimal montant;

    /**
     * Délai minimal de publicité attaché au franchissement, quand le texte en attache un (prestations
     * intellectuelles : 30 jours par voie de presse, 10 jours par affichage). {@code null} sinon.
     */
    @Column(name = "DELAI_MIN_JOURS")
    private Integer delaiMinJours;

    @Column(name = "DATE_EFFET", nullable = false)
    private LocalDate dateEffet;

    /** {@code null} = valeur en vigueur. Une valeur remplacée est bornée, jamais effacée. */
    @Column(name = "DATE_FIN")
    private LocalDate dateFin;

    /** Texte qui fixe la valeur — cité tel quel dans le signalement, pour qu'il soit opposable. */
    @Column(name = "BASE_LEGALE", length = 300)
    private String baseLegale;

    /** Acteur de la dernière saisie administrative ({@code null} pour les valeurs semées par V32). */
    @Column(name = "IM_ACTEUR", length = 10)
    private String imActeur;

    @Column(name = "DATE_MAJ")
    private LocalDateTime dateMaj;

    /** La valeur s'applique-t-elle à cette date ? {@code dateEffet} incluse, {@code dateFin} exclue. */
    public boolean enVigueurLe(LocalDate date) {
        return date != null && dateEffet != null && !date.isBefore(dateEffet)
                && (dateFin == null || date.isBefore(dateFin));
    }

    /** Le montant atteint-il ce seuil ? Comparaison « égal ou supérieur », comme l'écrit l'arrêté. */
    public boolean atteintPar(BigDecimal montantHt) {
        return montantHt != null && montant != null && montantHt.compareTo(montant) >= 0;
    }
}
