package cnm.prs.entity;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ⚠️ Règle ajoutée (demande front du 2026-09-22, fiche marché DAO, lot 1) — une <strong>information</strong> du
 * fichier de correspondance ({@code tr_champ_fiche_marche}, V35) : le front <strong>dessine l'écran depuis ce
 * référentiel</strong>, comme la grille de contrôle depuis {@link PointsCtrl}. Ajouter un champ ou changer une
 * condition ne recompile rien.
 *
 * <p>{@code source} dit d'où vient la valeur ({@link cnm.prs.enums.SourceChampFiche}) ; {@code condition} est une
 * expression sur le cadrage (« garantieSoumission = OUI », « et », « ou ») — fausse, le champ est ignoré et sa
 * rubrique fermée ; {@code controle} nomme une règle du catalogue, suffixée d'un rôle quand la règle lit
 * plusieurs champs (« VALIDITE_GARANTIE_SUP_OFFRE:GARANTIE »). Les listes ({@code reprises}, {@code typesMarche},
 * {@code options}) sont stockées séparées par des virgules.</p>
 */
@Entity
@Table(name = "tr_champ_fiche_marche", indexes = {
        @Index(name = "idx_champ_fiche_marche_rubrique", columnList = "CODE_RUBRIQUE, RANG")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChampFicheMarche {

    /** Code métier « bloc-rubrique-rang » (« B05-GS-02 »). */
    @Id
    @Column(name = "CODE", nullable = false, length = 20)
    private String code;

    @Column(name = "CODE_RUBRIQUE", nullable = false, length = 20)
    private String codeRubrique;

    @Column(name = "RANG", nullable = false)
    private Integer rang;

    @Column(name = "LIBELLE", nullable = false, length = 200)
    private String libelle;

    /** {@link cnm.prs.enums.TypeChampFiche}. */
    @Column(name = "TYPE", nullable = false, length = 20)
    private String type;

    /** {@link cnm.prs.enums.SourceChampFiche}. */
    @Column(name = "SOURCE", nullable = false, length = 10)
    private String source;

    @Column(name = "DOCUMENT_MAITRE", nullable = false, length = 10)
    private String documentMaitre;

    @Column(name = "REPRISES", length = 40)
    private String reprises;

    @Column(name = "TYPES_MARCHE", nullable = false, length = 60)
    private String typesMarche;

    @Column(name = "CONDITION", length = 300)
    private String condition;

    @Column(name = "OBLIGATOIRE", nullable = false)
    private Boolean obligatoire = Boolean.FALSE;

    @Column(name = "TEXTE_TYPE", length = 1000)
    private String texteType;

    @Column(name = "CONTROLE", length = 60)
    private String controle;

    @Column(name = "OPTIONS", length = 500)
    private String options;

    /** Source CADRAGE : la clé de la réponse reflétée. */
    @Column(name = "CLE_CADRAGE", length = 40)
    private String cleCadrage;

    /** Source PPM : la clé de la dérivation ({@code ValeursPpmService}). */
    @Column(name = "CLE_PPM", length = 40)
    private String clePpm;

    @Column(name = "ACTIF", nullable = false)
    private Boolean actif = Boolean.TRUE;

    /** Le bloc, lu sur le code (« B05-GS-02 » → « B05 »). */
    public String codeBloc() {
        return code == null || code.length() < 3 ? null : code.substring(0, 3);
    }

    /** Une liste séparée par des virgules, nettoyée ; vide si nulle. */
    public static List<String> liste(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean pourTypeMarche(String typeMarche) {
        return typeMarche == null || liste(typesMarche).contains(typeMarche);
    }
}
