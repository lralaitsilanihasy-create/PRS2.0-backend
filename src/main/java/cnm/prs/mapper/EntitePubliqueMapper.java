package cnm.prs.mapper;

import cnm.prs.dto.EntitePubliqueDto;
import cnm.prs.entity.EntiteContract;

/**
 * Projette une {@link EntiteContract} sur sa vue publique réduite {@link EntitePubliqueDto}.
 */
public final class EntitePubliqueMapper {

    private EntitePubliqueMapper() {
    }

    public static EntitePubliqueDto toDto(EntiteContract e) {
        if (e == null) {
            return null;
        }
        return new EntitePubliqueDto(e.getIdEntiteContract(), e.getLibelleEntite(),
                e.getAdresse(), e.getCategorieEntite(), e.getIdLocalite(), e.getSigle());
    }
}
