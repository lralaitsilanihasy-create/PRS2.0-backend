package cnm.prs.mapper;

import cnm.prs.dto.PrmpDto;
import cnm.prs.entity.Prmp;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Prmp}.
 */
public final class PrmpMapper {

    private PrmpMapper() {
    }

    public static PrmpDto toDto(Prmp entity) {
        if (entity == null) {
            return null;
        }
        PrmpDto dto = new PrmpDto();
        dto.setIdPrmp(entity.getIdPrmp());
        dto.setNomPrmp(entity.getNomPrmp());
        dto.setPrenomsPrmp(entity.getPrenomsPrmp());
        dto.setArreteNomin(entity.getArreteNomin());
        dto.setDateNomin(entity.getDateNomin());
        dto.setCin(entity.getCin());
        dto.setDateCin(entity.getDateCin());
        dto.setLieuCin(entity.getLieuCin());
        dto.setEmailPrmp(entity.getEmailPrmp());
        dto.setTelPrmp(entity.getTelPrmp());
        return dto;
    }

    /**
     * Vue <strong>réduite</strong> d'une PRMP : tout {@link PrmpDto} <em>sauf</em> le triptyque de la
     * pièce d'identité ({@code cin}, {@code dateCin}, {@code lieuCin}), laissé à {@code null}.
     *
     * <p>⚠️ Durcissement (2026-08-24) — le répertoire des PRMP est lisible par tous les profils du
     * circuit : la fiche du signataire d'un plan de passation fait partie de l'instruction du
     * dossier. Le <strong>numéro de carte d'identité</strong>, lui, est une donnée personnelle sans
     * usage métier hors gestion des comptes ; il est réservé à l'Administrateur et à la PRMP
     * elle-même (arbitrage porté par {@code PrmpService#vue}).</p>
     *
     * <p>L'arrêté de nomination, sa date, le courriel et le téléphone <strong>restent servis</strong> :
     * mentions d'un acte administratif public et coordonnées de fonction, que l'onglet « Entité
     * contractante » du détail d'un plan de passation affiche aux contrôleurs. Même découpage que la
     * vue restreinte des UGPM ({@code UgpmService#toDtoRestreint}) : ni CIN, mais courriel et
     * téléphone.</p>
     */
    public static PrmpDto toDtoRestreint(Prmp entity) {
        PrmpDto dto = toDto(entity);
        if (dto != null) {
            dto.setCin(null);
            dto.setDateCin(null);
            dto.setLieuCin(null);
        }
        return dto;
    }

    public static Prmp toEntity(PrmpDto dto) {
        if (dto == null) {
            return null;
        }
        Prmp entity = new Prmp();
        entity.setIdPrmp(dto.getIdPrmp());
        entity.setNomPrmp(dto.getNomPrmp());
        entity.setPrenomsPrmp(dto.getPrenomsPrmp());
        entity.setArreteNomin(dto.getArreteNomin());
        entity.setDateNomin(dto.getDateNomin());
        entity.setCin(dto.getCin());
        entity.setDateCin(dto.getDateCin());
        entity.setLieuCin(dto.getLieuCin());
        entity.setEmailPrmp(dto.getEmailPrmp());
        entity.setTelPrmp(dto.getTelPrmp());
        return entity;
    }
}
