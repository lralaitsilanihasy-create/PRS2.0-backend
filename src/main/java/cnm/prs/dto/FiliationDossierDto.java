package cnm.prs.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * ⚠️ 2026-10-31 (demande front « filiation-dossier », §B1) — l'arbre complet auquel appartient un dossier, la racine en tête.
 * {@code idDossierDemande} : le dossier demandé (repéré dans l'arbre par son identifiant).
 */
public record FiliationDossierDto(Integer idDossierDemande, Noeud racine) {

    /** Nature du lien d'un nœud à son parent. */
    public static final String RACINE = "RACINE";
    public static final String VERSION_SUIVANTE = "VERSION_SUIVANTE";
    public static final String MISE_EN_CONCURRENCE = "MISE_EN_CONCURRENCE";
    public static final String MARCHE = "MARCHE";
    public static final String MODIFICATION = "MODIFICATION";

    /**
     * Un dossier de l'arbre. Hors du périmètre du connecté, il est rendu <strong>masqué</strong> : {@code masque = true}, {@code lien}
     * et {@code enfants} seuls (tout le reste nul), pour que l'arbre garde sa forme. {@code lot} : pour un dossier de marché ;
     * {@code ligne} : la ligne du plan d'un dossier de mise en concurrence (ou d'un marché sans dossier de mise en concurrence).
     */
    public record Noeud(Integer idDossier, String refeDossier, String idTypeDossier, String idSousType, String statut, LocalDate dateRef,
            String lien, Integer lot, Ligne ligne, boolean masque, List<Noeud> enfants) {
    }

    /** La ligne du plan : son identifiant et son objet. */
    public record Ligne(Integer idDetail, String objet) {
    }
}
