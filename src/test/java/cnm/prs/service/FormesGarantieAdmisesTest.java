package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.ChampFicheMarche;

/**
 * ⚠️ 2026-09-26 (demande front « forme de la garantie de soumission : plusieurs formes admises », §B2) — la tournure des
 * formes admises dans les documents produits, et son déclenchement : {@code B05-GS-02} en {@code LISTE_MULTIPLE} à
 * plusieurs formes seulement.
 */
class FormesGarantieAdmisesTest {

    @Test
    @DisplayName("Plusieurs formes → « l'une des formes suivantes : », puis « – soit … » par forme, une par ligne, dans "
            + "l'ordre reçu, avec l'article des formes du CMP ; une option hors des quatre s'imprime telle quelle")
    void tournure() {
        assertThat(SelectionDocumentsFiche.formesAdmises(List.of(
                "Dépôt en numéraire au Trésor", "Caution personnelle et solidaire d'un organisme agréé par le MEF",
                "Garantie bancaire", "Chèque de banque", "Lettre de crédit")))
                .isEqualTo("Une garantie de soumission doit être fournie dans l'une des formes suivantes :\n"
                        + "– soit un dépôt en numéraire au Trésor\n"
                        + "– soit une caution personnelle et solidaire d'un organisme agréé par le MEF\n"
                        + "– soit une garantie bancaire\n"
                        + "– soit un chèque de banque\n"
                        + "– soit Lettre de crédit");
        assertThat(SelectionDocumentsFiche.formesAdmises(List.of("garantie bancaire", "Chèque de banque")))
                .as("la casse de la valeur n'importe pas")
                .endsWith("– soit une garantie bancaire\n– soit un chèque de banque");
    }

    @Test
    @DisplayName("Une seule forme → elle-même ; aucune → null")
    void singulier() {
        assertThat(SelectionDocumentsFiche.formesAdmises(List.of("Garantie bancaire"))).isEqualTo("Garantie bancaire");
        assertThat(SelectionDocumentsFiche.formesAdmises(List.of())).isNull();
    }

    @Test
    @DisplayName("valeurDocument : la tournure pour B05-GS-02 à plusieurs formes ; la valeur affichée (options jointes "
            + "par « , ») pour une seule forme, pour un autre champ à choix multiples, ou pour une clé de lot")
    void declenchement() {
        ChampFicheMarche forme = champ("B05-GS-02", "LISTE_MULTIPLE");
        ChampFicheMarche fiches = champ("B04-CD-01", "LISTE_MULTIPLE");
        FicheMarcheDto fiche = new FicheMarcheDto();
        fiche.setValeurs(Map.of(
                "B05-GS-02", "Garantie bancaire,Chèque de banque",
                "B05-GS-02#2", "Garantie bancaire,Chèque de banque",
                "B04-CD-01", "A1,A2"));
        assertThat(SelectionDocumentsFiche.valeurDocument(forme, "B05-GS-02", fiche))
                .startsWith("Une garantie de soumission doit être fournie").contains("\n– soit une garantie bancaire");
        assertThat(SelectionDocumentsFiche.valeurDocument(forme, "B05-GS-02#2", fiche))
                .as("clé de lot : même tournure").contains("\n– soit un chèque de banque");
        assertThat(SelectionDocumentsFiche.valeurDocument(fiches, "B04-CD-01", fiche))
                .as("les fiches exigées ne sont pas des alternatives").isEqualTo("A1, A2");
        assertThat(SelectionDocumentsFiche.valeurAffichee(forme, "B05-GS-02", fiche))
                .as("la valeur affichée (observations d'examen) reste la liste jointe")
                .isEqualTo("Garantie bancaire, Chèque de banque");

        fiche.setValeurs(Map.of("B05-GS-02", "Garantie bancaire"));
        assertThat(SelectionDocumentsFiche.valeurDocument(forme, "B05-GS-02", fiche)).isEqualTo("Garantie bancaire");
        fiche.setValeurs(Map.of());
        assertThat(SelectionDocumentsFiche.valeurDocument(forme, "B05-GS-02", fiche)).isNull();
    }

    private static ChampFicheMarche champ(String code, String type) {
        ChampFicheMarche c = new ChampFicheMarche();
        c.setCode(code);
        c.setType(type);
        c.setLibelle(code);
        return c;
    }
}
