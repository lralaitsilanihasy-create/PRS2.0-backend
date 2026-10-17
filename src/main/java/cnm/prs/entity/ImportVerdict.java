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

/**
 * ⚠️ V128 (demande front « identite-dao-importe », §B2) — le verdict d'identité d'un DAO lu à l'import, par fiche, empreinte et
 * utilisateur : l'application de l'import le relit (refus d'un DAO étranger, attestation d'un DAO non prouvé, lecture expirée).
 */
@Entity
@Table(name = "t_import_verdict")
@Getter
@Setter
@NoArgsConstructor
public class ImportVerdict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "EMPREINTE", nullable = false, length = 64)
    private String empreinte;

    @Column(name = "UTILISATEUR", nullable = false, length = 100)
    private String utilisateur;

    @Column(name = "VERDICT", nullable = false, length = 12)
    private String verdict;

    @Column(name = "MOTIF", length = 2000)
    private String motif;

    @Column(name = "LU_LE", nullable = false)
    private LocalDateTime luLe;
}
