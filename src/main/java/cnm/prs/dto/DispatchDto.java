package cnm.prs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de transfert pour {@link cnm.prs.entity.Dispatch}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispatchDto {

    private Integer idDispatch;

    @NotNull
    private Integer idReception;

    @Size(max = 7)
    private String imCtrlDispatch;

    @Size(max = 7)
    private String imCtrlCc;

    @Size(max = 7)
    private String imCtrlMembre;

    /** Date et heure du dispatch, formatée {@code yyyy-MM-dd HH:mm}. */
    private String dateDispatch;

    /** Date et heure de pré-dispatch = réception du dossier par le secrétaire
     *  ({@code t_reception.DATE_RECEPTION} la plus récente du dossier), {@code yyyy-MM-dd HH:mm} ;
     *  lecture seule, {@code null} si aucune réception. */
    private String datePredispatch;

    private LocalDate dateCtrlAssigne;

    @Size(max = 500)
    private String instructions;

    @NotNull
    private Boolean interimDispatch;

    /**
     * ⚠️ Intérim désigné (2026-09-21, §B5) — lecture seule : identifiant de l'intérim quand le dispatch a été
     * posé par un intérimaire désigné, et matricule du titulaire suppléé ({@code interimDe}, égal à
     * {@code imCtrlDispatch} : le dispatcheur enregistré est le titulaire, ADR-0008). {@code null} sinon.
     * {@code interimDispatch} reste le repli ponctuel, inchangé.
     */
    private Integer idInterim;

    private String interimDe;
}
