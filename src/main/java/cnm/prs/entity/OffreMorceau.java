package cnm.prs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ⚠️ V68 (lot 3, §B4) — un morceau reçu d'une offre en cours de dépôt : taille et empreinte ; le contenu est sur disque. */
@Entity
@Table(name = "t_offre_morceau")
@Getter
@Setter
@NoArgsConstructor
public class OffreMorceau {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_MORCEAU", nullable = false)
    private Long idMorceau;

    @Column(name = "ID_OFFRE", nullable = false, length = 36)
    private String idOffre;

    @Column(name = "RANG", nullable = false)
    private Integer rang;

    @Column(name = "TAILLE", nullable = false)
    private Integer taille;

    @Column(name = "EMPREINTE", nullable = false, length = 64)
    private String empreinte;

    @Column(name = "DATE_RECU", nullable = false)
    private LocalDateTime dateRecu;
}
