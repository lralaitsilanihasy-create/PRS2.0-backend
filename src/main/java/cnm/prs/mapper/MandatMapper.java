package cnm.prs.mapper;

import cnm.prs.dto.MandatDto;
import cnm.prs.entity.Mandat;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Mandat}.
 *
 * <p>Le statut exposé est celui <strong>calculé</strong> par le service (dérivé des dates) : le mapper
 * le reçoit en paramètre plutôt que de recopier la colonne, qui n'est qu'un cache de la dernière écriture.</p>
 */
public final class MandatMapper {

    private MandatMapper() {
    }

    public static MandatDto toDto(Mandat entity, String statutEffectif) {
        if (entity == null) {
            return null;
        }
        MandatDto dto = new MandatDto();
        dto.setIdMandat(entity.getIdMandat());
        dto.setIdPrmp(entity.getIdPrmp());
        dto.setTitulaire(entity.getTitulaire());
        dto.setDateDebut(entity.getDateDebut());
        dto.setDateFin(entity.getDateFin());
        dto.setRefArrete(entity.getRefArrete());
        dto.setStatut(statutEffectif != null ? statutEffectif : entity.getStatut());
        dto.setNumeroMandat(entity.getNumeroMandat());
        dto.setDateAbrogation(entity.getDateAbrogation());
        dto.setMotifAbrogation(entity.getMotifAbrogation());
        dto.setImplicite(false);
        return dto;
    }
}
