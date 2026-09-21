package cnm.prs.mapper;

import cnm.prs.dto.InterimDto;
import cnm.prs.entity.Interim;

/**
 * Convertisseur entité → DTO pour {@link Interim}. Le statut est <strong>calculé</strong> par le service
 * (dérivé des dates à la date du jour) et reçu en paramètre : il n'existe aucune colonne à recopier.
 * Pas de {@code toEntity} : la seule écriture est la désignation, construite par le service.
 */
public final class InterimMapper {

    private InterimMapper() {
    }

    public static InterimDto toDto(Interim entity, String statutEffectif) {
        if (entity == null) {
            return null;
        }
        InterimDto dto = new InterimDto();
        dto.setIdInterim(entity.getIdInterim());
        dto.setImTitulaire(entity.getImTitulaire());
        dto.setNomTitulaire(entity.getNomTitulaire());
        dto.setProfilTitulaire(entity.getProfilTitulaire());
        dto.setIdLocaliteTitulaire(entity.getIdLocaliteTitulaire());
        dto.setImInterimaire(entity.getImInterimaire());
        dto.setNomInterimaire(entity.getNomInterimaire());
        dto.setProfilInterimaire(entity.getProfilInterimaire());
        dto.setIdLocaliteInterimaire(entity.getIdLocaliteInterimaire());
        dto.setDateDebut(entity.getDateDebut());
        dto.setDateFin(entity.getDateFin());
        dto.setMotif(entity.getMotif());
        dto.setReference(entity.getReference());
        dto.setPieceNom(entity.getPieceNom());
        dto.setPieceDisponible(entity.getPieceTaille() != null && entity.getPieceTaille() > 0);
        dto.setDesignePar(entity.getDesignePar());
        dto.setNomDesignePar(entity.getNomDesignePar());
        dto.setDateDesignation(entity.getDateDesignation());
        dto.setStatut(statutEffectif);
        dto.setDateRevocation(entity.getDateRevocation());
        dto.setMotifRevocation(entity.getMotifRevocation());
        dto.setRevoquePar(entity.getRevoquePar());
        return dto;
    }
}
