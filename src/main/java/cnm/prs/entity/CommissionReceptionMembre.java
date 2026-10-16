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

/** ⚠️ V127 (lot MS-4) — un membre de la commission de réception, avec son compte (K…) pour signer le PV. */
@Entity
@Table(name = "t_commission_reception_membre")
@Getter
@Setter
@NoArgsConstructor
public class CommissionReceptionMembre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_CONTRAT", nullable = false)
    private Long idContrat;

    @Column(name = "RANG", nullable = false)
    private int rang;

    @Column(name = "NOM", nullable = false, length = 100)
    private String nom;

    @Column(name = "PRENOM", nullable = false, length = 100)
    private String prenom;

    @Column(name = "EMAIL", nullable = false, length = 255)
    private String email;

    @Column(name = "FONCTION", length = 200)
    private String fonction;

    @Column(name = "PRESIDENT", nullable = false)
    private boolean president;

    @Column(name = "ID_COMPTE", nullable = false, length = 20)
    private String idCompte;
}
