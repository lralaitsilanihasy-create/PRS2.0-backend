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
 * ⚠️ V66 (demande front du 2026-10-04, soumission en ligne, lot 2, §B4 ; ADR-0013 S2) — un <strong>défi</strong> de
 * vérification d'une part : le serveur a tiré 32 octets, les a chiffrés avec la clé publique du détenteur, et ne garde que
 * le SHA-256 du clair ({@code empreinteClair}), cinq minutes, à usage unique. Rien n'est révélé : le défi n'a aucun lien
 * avec les offres.
 */
@Entity
@Table(name = "t_defi_cle")
@Getter
@Setter
@NoArgsConstructor
public class DefiCle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_DEFI", nullable = false)
    private Long idDefi;

    @Column(name = "ID_DMC", nullable = false)
    private Long idDmc;

    @Column(name = "ID_CLE", nullable = false)
    private Long idCle;

    @Column(name = "IM_APPELANT", length = 10)
    private String imAppelant;

    @Column(name = "EMPREINTE_CLAIR", nullable = false, length = 64)
    private String empreinteClair;

    @Column(name = "DATE_CREATION", nullable = false)
    private LocalDateTime dateCreation;

    @Column(name = "EXPIRE", nullable = false)
    private LocalDateTime expire;

    @Column(name = "CONSOMME", nullable = false)
    private Boolean consomme = Boolean.FALSE;
}
