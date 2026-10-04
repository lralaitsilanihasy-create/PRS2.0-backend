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
 * ⚠️ V63 (2026-10-04, soumission en ligne, lot 1a, §B2) — un <strong>code de confirmation</strong> d'un compte candidat :
 * six chiffres, à usage unique, valable 15 minutes, 5 essais au plus ; stocké haché (BCrypt), par canal ({@code EMAIL},
 * {@code TELEPHONE}).
 */
@Entity
@Table(name = "t_code_candidat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CodeCandidat {

    public static final String EMAIL = "EMAIL";
    public static final String TELEPHONE = "TELEPHONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CODE", nullable = false)
    private Integer idCode;

    @Column(name = "ID_CANDIDAT", nullable = false, length = 10)
    private String idCandidat;

    @Column(name = "CANAL", nullable = false, length = 10)
    private String canal;

    @Column(name = "HASH_CODE", nullable = false, length = 100)
    private String hashCode;

    @Column(name = "DATE_EMISSION", nullable = false)
    private LocalDateTime dateEmission;

    @Column(name = "EXPIRATION", nullable = false)
    private LocalDateTime expiration;

    @Column(name = "ESSAIS", nullable = false)
    private int essais;

    @Column(name = "UTILISE", nullable = false)
    private boolean utilise;
}
