package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », §B4, lot G1) — les échanges de l'écran « Modèles de PV » de
 * l'Administrateur.
 */
public final class ModelePvDto {

    private ModelePvDto() {
    }

    /** Une ligne de la liste : le modèle, ses sous-types, sa version en vigueur et sa dernière version. */
    public record Resume(Long id, String code, String libelle, String nature, String etat, List<String> sousTypes,
            VersionCourte enVigueur, VersionCourte derniere) {
    }

    public record VersionCourte(Integer numero, String etat, boolean bloquant, LocalDateTime deposeeLe, LocalDateTime enVigueurLe) {
    }

    /** La fiche d'un modèle. */
    public record Fiche(Long id, String code, String libelle, String nature, String etat, LocalDateTime creeLe, String creePar,
            LocalDateTime archiveLe, String motifArchivage, List<SousType> sousTypes, List<Version> versions, List<Evenement> historique) {
    }

    public record SousType(String id, String libelle, String famille) {
    }

    /** Une version : son fichier, son état, son rapport de contrôle, ses conditions et ses champs saisis. */
    public record Version(Integer numero, String nomFichier, Long taille, String empreinte, String etat, boolean bloquant,
            Rapport rapport, String motif, LocalDateTime deposeeLe, String deposeePar, LocalDateTime enVigueurLe, String enVigueurPar,
            LocalDateTime remplaceeLe, List<Condition> conditions, List<Champ> champs) {
    }

    /** Le rapport de contrôle (§B5) : des points {@code OK}, {@code AVERTISSEMENT} ou {@code BLOQUANT}. */
    public record Rapport(boolean bloquant, int nbBloquants, int nbAvertissements, List<Point> points, List<String> variables,
            List<String> conditionsUtilisees, List<String> champsUtilises, LocalDateTime controleLe) {
    }

    /** Un point du rapport ; {@code ou} situe le marqueur (paragraphe, cellule, en-tête), {@code suggestion} propose un code connu. */
    public record Point(String niveau, String code, String message, String ou, String suggestion) {
    }

    public record Condition(@NotBlank @Size(max = 60) String nom, @NotBlank @Size(max = 1000) String expression,
            @Size(max = 255) String libelle) {
    }

    /** {@code options} : les choix d'une liste ; {@code visibleSi} : le nom d'une condition déclarée de la version. */
    public record Champ(@NotBlank @Size(max = 60) String code, @NotBlank @Size(max = 255) String libelle, @NotBlank String type,
            List<String> options, Boolean obligatoire, @Size(max = 1000) String aide, @Size(max = 60) String visibleSi) {
    }

    public record Evenement(LocalDateTime le, String action, Integer numeroVersion, String detail, String par) {
    }

    public record CreationRequest(@NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 255) String libelle,
            @NotBlank String nature) {
    }

    public record DuplicationRequest(@Size(max = 50) String code, @Size(max = 255) String libelle) {
    }

    public record MotifRequest(@Size(max = 1000) String motif) {
    }

    public record RattachementsRequest(List<String> sousTypes) {
    }

    public record ApercuRequest(Map<String, String> saisis) {
    }

    /** Le catalogue des variables (§B3), par groupe, filtré par la famille du sous-type demandé. */
    public record Catalogue(String sousType, String famille, List<Groupe> groupes, List<Liste> listes, List<Cle> cles,
            List<String> formats, Map<String, String> alias) {
    }

    public record Groupe(String nom, List<Variable> variables) {
    }

    public record Variable(String code, String libelle, String exemple, String liste) {
    }

    public record Liste(String nom, String prefixe, String libelle) {
    }

    public record Cle(String cle, String libelle, List<String> valeurs) {
    }
}
