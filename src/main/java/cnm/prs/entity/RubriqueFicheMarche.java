package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rubrique d'un bloc de la fiche marché ({@code tr_rubrique_fiche_marche}, V35) — « une rubrique = une carte à
 * l'écran ». {@code nbAttendu} est le nombre d'informations que le fichier de correspondance annonce pour la
 * rubrique : tant que ses champs ne sont pas chargés, le front affiche « n informations attendues ».
 */
@Entity
@Table(name = "tr_rubrique_fiche_marche")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RubriqueFicheMarche {

    /** Code complet, {@code bloc-rubrique} (« B05-GS »). */
    @Id
    @Column(name = "CODE", nullable = false, length = 20)
    private String code;

    @Column(name = "CODE_BLOC", nullable = false, length = 3)
    private String codeBloc;

    /** Code court dans le bloc (« GS »). */
    @Column(name = "CODE_COURT", nullable = false, length = 10)
    private String codeCourt;

    @Column(name = "LIBELLE", nullable = false, length = 150)
    private String libelle;

    @Column(name = "RANG", nullable = false)
    private Integer rang;

    /** Document maître de la rubrique ({@code DPAO}, {@code DPAC}, {@code AE}, {@code CCAP}, {@code AUCUN}) ; nul si mixte. */
    @Column(name = "DOCUMENT_MAITRE", length = 10)
    private String documentMaitre;

    @Column(name = "NB_ATTENDU", nullable = false)
    private Integer nbAttendu;

    @Column(name = "TYPES_MARCHE", nullable = false, length = 60)
    private String typesMarche;
}
