package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entite JPA mappee sur {@code t_suspension_dossier} — une fenetre ou « la balle est chez la PRMP ».
 *
 * <p>Sert UNIQUEMENT au compteur net CNM. Le drapeau d'attente expose a la PRMP est derive du statut
 * COURANT du dossier, pas de cette table : un enregistrement manque fausserait un cumul, jamais
 * l'affichage.</p>
 */
@Entity
@Table(name = "t_suspension_dossier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SuspensionDossier {

    @Id
    @Column(name = "ID_SUSPENSION", nullable = false)
    private Integer idSuspension;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    /** Statut suspensif ayant ouvert la fenetre. */
    @Column(name = "STATUT", nullable = false, length = 40)
    private String statut;

    @Column(name = "DEBUT", nullable = false)
    private LocalDateTime debut;

    /** Nul tant que la PRMP n'a pas rendu la main. */
    @Column(name = "FIN")
    private LocalDateTime fin;
}
