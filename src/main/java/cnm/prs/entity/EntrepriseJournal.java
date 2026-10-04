package cnm.prs.entity;

import java.time.LocalDateTime;

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
 * ⚠️ V64 (demande front du 2026-10-04, soumission en ligne, lot 1b) — une ligne du <strong>journal</strong> d'une entreprise : un changement de statut de vérification
 * du NIF, avec l'ancienne et la nouvelle valeur.
 */
@Entity
@Table(name = "t_entreprise_journal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EntrepriseJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_JOURNAL", nullable = false)
    private Integer idJournal;

    @Column(name = "ID_ENTREPRISE", nullable = false)
    private Integer idEntreprise;

    @Column(name = "DATE_ACTION", nullable = false)
    private LocalDateTime dateAction;

    @Column(name = "ACTEUR", length = 100)
    private String acteur;

    @Column(name = "CHAMP", nullable = false, length = 50)
    private String champ;

    @Column(name = "ANCIENNE", length = 500)
    private String ancienne;

    @Column(name = "NOUVELLE", length = 500)
    private String nouvelle;
}
