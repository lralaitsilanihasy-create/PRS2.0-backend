package cnm.prs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ Spec navette (2026-08-01) — transmission du <strong>sens de la décision de la Commission</strong>
 * vers SIGMP (application tierce, interop PRS 2.0 ↔ SIGMP). En l'absence de contrat d'API SIGMP réel,
 * la transmission est <strong>enregistrée côté PRS</strong> ({@code STATUT_ENVOI = ENREGISTREE}) et sera
 * ré-émise le jour où l'interop réelle sera branchée — aucun endpoint tiers inventé.
 */
@Entity
@Table(name = "t_transmission_sigmp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransmissionSigmp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_TRANSMISSION", nullable = false)
    private Integer idTransmission;

    @Column(name = "ID_DOSSIER", nullable = false)
    private Integer idDossier;

    @Column(name = "ID_PV", nullable = false)
    private Integer idPv;

    /** Sens transmis : {@code APPROUVE} (FAV, ou FAVR après levée) / {@code NON_APPROUVE} (DEF, NSP). */
    @Column(name = "SENS", nullable = false, length = 20)
    private String sens;

    /** Vrai si la transmission porte AUSSI la levée des observations (cas 2, fin de boucle FAVR). */
    @Column(name = "LEVEE_OBSERVATIONS", nullable = false)
    private Boolean leveeObservations;

    @Column(name = "DATE_TRANSMISSION", nullable = false)
    private LocalDateTime dateTransmission;

    /** Vérificateur auteur de la transmission (matricule, identité JWT). */
    @Column(name = "IM_VERIFICATEUR", length = 7)
    private String imVerificateur;

    /** État d'envoi vers SIGMP : {@code ENREGISTREE} (interop réelle à brancher plus tard). */
    @Column(name = "STATUT_ENVOI", nullable = false, length = 20)
    private String statutEnvoi;
}
