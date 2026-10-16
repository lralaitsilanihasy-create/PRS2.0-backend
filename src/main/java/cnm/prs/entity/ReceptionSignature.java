package cnm.prs.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ⚠️ V127 (lot MS-4) — la signature du PV de réception par un membre de la commission (ou par la PRMP qui la préside). */
@Entity
@Table(name = "t_reception_signature")
@IdClass(ReceptionSignature.Cle.class)
@Getter
@Setter
@NoArgsConstructor
public class ReceptionSignature {

    @Id
    @Column(name = "ID_RECEPTION", nullable = false)
    private Long idReception;

    @Id
    @Column(name = "SIGNATAIRE", nullable = false, length = 100)
    private String signataire;

    @Column(name = "NOM", length = 220)
    private String nom;

    @Column(name = "PRESIDENT", nullable = false)
    private boolean president;

    @Column(name = "SIGNE_LE", nullable = false)
    private LocalDateTime signeLe;

    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Cle implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long idReception;
        private String signataire;
    }
}
