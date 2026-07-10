package cnm.prs.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.DossierMec} (dossier de mise en concurrence).
 * {@code typeDmcCode}/{@code typeDmcLibelle} sont dérivés du type (lecture seule).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DmcDto {

    private Long idDmc;
    private Integer idDetail;
    private Long idTypeDmc;
    private String typeDmcCode;
    private String typeDmcLibelle;
    private String reference;
    private String statut;
    private LocalDateTime dateCreation;
}
