package cnm.prs.dto;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * La fiche marché d'un DMC (demande du 2026-09-22, §B3) — une version : cadrage, valeurs saisies, reflets du
 * cadrage, les 22 informations du PPM relues, le bilan des contrôles.
 *
 * <p>{@code valeurs} ne porte que les champs de source {@code SAISIE} ; {@code enLettres} donne, pour chaque
 * {@code MONTANT} saisi, le montant en toutes lettres ; {@code valeursCadrage} les champs de source
 * {@code CADRAGE} dérivés des réponses ; {@code valeursPpm} les champs de source {@code PPM}, relus à chaque lecture
 * (jamais stockés, H7) avec {@code versionPpm} le numéro de version du plan lu. Avant le premier enregistrement,
 * {@code idFiche} est nul : la fiche est virtuelle, version 1, brouillon.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FicheMarcheDto {

    private Integer idFiche;

    private Long idDmc;

    /** La ligne à laquelle le DMC est lié (au moment de sa création). */
    private Integer idDetail;

    private Integer idDossier;

    /**
     * ⚠️ 22/09 (filiation) — la ligne <strong>courante</strong> de la même filiation dans la dernière version signée
     * du plan ({@code = idDetail} tant que le plan n'a pas été mis à jour) ; {@code valeursPpm} et {@code versionPpm}
     * sont ceux de cette ligne. {@code ligneSupprimee} : la filiation est retirée dans la version courante.
     */
    private Integer idDetailCourant;

    private Boolean ligneSupprimee;

    private String refeDossier;

    private String designationMarche;

    private Integer version;

    private String statut;

    private String typeMarche;

    private Map<String, Object> cadrage;

    private Map<String, String> valeurs;

    private Map<String, String> enLettres;

    private Map<String, String> valeursCadrage;

    private Map<String, String> valeursPpm;

    private Integer versionPpm;

    private BilanControlesDto bilanControles;

    private LocalDateTime dateCreation;

    private LocalDateTime dateMaj;

    private LocalDateTime dateValidation;

    private String validePar;
}
