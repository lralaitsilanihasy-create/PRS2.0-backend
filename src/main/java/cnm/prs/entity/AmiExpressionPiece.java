package cnm.prs.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ⚠️ V82 (AMI en ligne, §B2) — une pièce d'une expression d'intérêt : son libellé (une des pièces attendues), le fichier, son empreinte. */
@Entity
@Table(name = "t_ami_expression_piece")
@Getter
@Setter
@NoArgsConstructor
public class AmiExpressionPiece {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "ID_EXPRESSION", nullable = false, length = 36)
    private String idExpression;

    @Column(name = "LIBELLE", nullable = false, length = 255)
    private String libelle;

    @Column(name = "NOM", nullable = false, length = 255)
    private String nom;

    @Column(name = "FORMAT", nullable = false, length = 50)
    private String format;

    @Column(name = "TAILLE", nullable = false)
    private Long taille;

    @Column(name = "EMPREINTE", nullable = false, length = 64)
    private String empreinte;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "CONTENU", nullable = false)
    private byte[] contenu;
}
