package cnm.prs.mapper;

import cnm.prs.dto.MarcheDto;
import cnm.prs.entity.Marche;
import cnm.prs.enums.FormeMarche;

/**
 * Convertisseur entité &lt;-&gt; DTO pour {@link Marche}.
 */
public final class MarcheMapper {

    private MarcheMapper() {
    }

    public static MarcheDto toDto(Marche entity) {
        if (entity == null) {
            return null;
        }
        MarcheDto dto = new MarcheDto();
        dto.setIdDetail(entity.getIdDetail());
        dto.setIdLigneOrigine(entity.getIdLigneOrigine());   // getters coalescents → jamais null
        dto.setSupprimee(entity.getSupprimee());
        dto.setIdDossier(entity.getIdDossier());
        dto.setIdPpm(entity.getIdPpm());
        dto.setDesignationMarche(entity.getDesignationMarche());
        dto.setNumCompte(entity.getNumCompte());
        dto.setMontEstim(entity.getMontEstim());
        dto.setAncienMontEstim(entity.getAncienMontEstim());
        dto.setNouvMontEstim(entity.getNouvMontEstim());
        dto.setFinancement(entity.getFinancement());
        dto.setStatut(entity.getStatut());
        dto.setIdNature(entity.getIdNature());
        dto.setIdMode(entity.getIdMode());
        dto.setFormeMarche(entity.getFormeMarche().name());   // getter coalescent → jamais null
        dto.setJustifModeDerogatoire(entity.getJustifModeDerogatoire());   // ⚠️ fiche de présentation (2026-09-01)
        dto.setJustifDelaiAmenage(entity.getJustifDelaiAmenage());
        dto.setVersion(entity.getVersion());   // ⚠️ verrou optimiste (docs/plan-conflit-version.md)
        return dto;
    }

    public static Marche toEntity(MarcheDto dto) {
        if (dto == null) {
            return null;
        }
        Marche entity = new Marche();
        entity.setIdDetail(dto.getIdDetail());
        entity.setIdDossier(dto.getIdDossier());
        entity.setIdPpm(dto.getIdPpm());
        entity.setDesignationMarche(dto.getDesignationMarche());
        entity.setNumCompte(dto.getNumCompte());
        entity.setMontEstim(dto.getMontEstim());
        entity.setAncienMontEstim(dto.getAncienMontEstim());
        entity.setNouvMontEstim(dto.getNouvMontEstim());
        entity.setFinancement(dto.getFinancement());
        entity.setStatut(dto.getStatut());
        entity.setIdNature(dto.getIdNature());
        entity.setIdMode(dto.getIdMode());
        // Forme du marché : optionnelle (absent → QUANTITE_FIXE), code inconnu → 400 ciblé.
        entity.setFormeMarche(FormeMarche.depuisCodeOuDefaut(dto.getFormeMarche()));
        // ⚠️ Fiche de présentation (2026-09-01) — normalisées à la CRÉATION : un blanc vaut absence,
        // il ne doit pas se retrouver stocké comme une justification vide qui satisferait l'œil sans
        // rien justifier. La sémantique « null = inchangé » ne concerne que la mise à jour, portée
        // par MarcheService.update — ici l'entité naît, il n'y a rien à conserver.
        entity.setJustifModeDerogatoire(texteOuNull(dto.getJustifModeDerogatoire()));
        entity.setJustifDelaiAmenage(texteOuNull(dto.getJustifDelaiAmenage()));
        return entity;
    }

    /** Texte utile ou {@code null} : {@code trim}, et une chaîne blanche devient {@code null}. */
    public static String texteOuNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
