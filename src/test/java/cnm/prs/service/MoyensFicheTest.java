package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.MaterielExigeDto;
import cnm.prs.dto.PersonnelExigeDto;
import cnm.prs.exception.ChampsInvalidesException;

/**
 * ⚠️ V60 (demande front du 2026-10-03, matériel et personnel des travaux, §B1 et §B2) — la validation des deux listes et
 * le texte des jetons {@code {{MOYENS.materiel}}} / {@code {{MOYENS.personnel}}}, sur les exemples du MTP et du MEN.
 */
class MoyensFicheTest {

    private static MaterielExigeDto engin(String designation, String carac, int nombre, Integer minimum, boolean parLot) {
        return new MaterielExigeDto(null, null, designation, carac, nombre, minimum, parLot);
    }

    @Test
    @DisplayName("Matériel : avec un minimum en propre, tout en propre, sans minimum (ou 0), par lot")
    void materiel() {
        Map<String, String> j = MoyensFiche.jetons(List.of(engin("Camions bennes", "≥ 10 000 kg", 6, 4, false),
                engin("Niveleuse", null, 1, 1, false), engin("Bétonnière", "≥ 350 l", 1, null, false),
                engin("Pervibrateur", null, 1, 0, false), engin("Voiture de liaison 4×4", null, 1, null, true)), List.of());
        assertThat(j).containsOnlyKeys("MOYENS.materiel");
        assertThat(j.get("MOYENS.materiel")).isEqualTo("""
                - Camions bennes ≥ 10 000 kg : 6, dont au moins 4 en propre
                - Niveleuse : 1, en propre
                - Bétonnière ≥ 350 l : 1
                - Pervibrateur : 1
                - Voiture de liaison 4×4 : 1 par lot""");
    }

    @Test
    @DisplayName("Personnel : diplôme en minuscule (sigle gardé), expérience et domaine, justificatifs ; morceaux absents "
            + "omis avec leur séparateur ; par lot")
    void personnel() {
        Map<String, String> j = MoyensFiche.jetons(List.of(), List.of(
                new PersonnelExigeDto(null, null, "Conducteur de travaux", 1, "Ingénieur BTP ou génie civil", 5,
                        "travaux routiers", "CV et diplôme certifié", false),
                new PersonnelExigeDto(null, null, "Chef de chantier", null, "BTS en génie civil", 3, null,
                        "CV avec photo, diplôme certifié", true),
                new PersonnelExigeDto(null, null, "Topographe", 2, null, 1, null, null, false),
                new PersonnelExigeDto(null, null, "Gardien", 1, null, null, null, null, false)));
        assertThat(j.get("MOYENS.personnel")).isEqualTo("""
                - Conducteur de travaux (1) : ingénieur BTP ou génie civil ; au moins 5 ans d'expérience en travaux routiers ; justificatifs : CV et diplôme certifié
                - Chef de chantier (1 par lot) : BTS en génie civil ; au moins 3 ans d'expérience ; justificatifs : CV avec photo, diplôme certifié
                - Topographe (2) : au moins 1 an d'expérience
                - Gardien (1)""");
        assertThat(MoyensFiche.jetons(List.of(), List.of())).isEmpty();   // listes vides : pointillés
    }

    @Test
    @DisplayName("2026-10-03 (réponse du front) — les conditions lisent les listes : MATERIEL-LISTE, PERSONNEL-LISTE, "
            + "PERSONNEL-CLE ; une fiche qui décrit son matériel en texte seul n'imprime pas de pointillés au-dessus")
    void conditionsSurLesListes() {
        String us = "\u001F";
        cnm.prs.service.FichierCommande.Modele modele = cnm.prs.service.FichierCommande.lireModele(String.join("\n",
                "CONDITION\tMATERIEL-LISTE" + us + "MOYENS.materiel renseigne",
                "CONDITION\tMATERIEL-TEXTE" + us + "B03-QT-09 renseigne",
                "CONDITION\tPERSONNEL-LISTE" + us + "MOYENS.personnel renseigne",
                "CONDITION\tPERSONNEL-CLE" + us + "MOYENS.personnel renseigne ou B03-QT-13 renseigne",
                "PARA\t(c) gros matériels :", "PARA\t{{SI:MATERIEL-LISTE}}", "PARA\t{{MOYENS.materiel}}", "PARA\t{{FINSI:MATERIEL-LISTE}}",
                "PARA\t{{SI:MATERIEL-TEXTE}}", "PARA\t{{B03-QT-09}}", "PARA\t{{FINSI:MATERIEL-TEXTE}}",
                "PARA\t{{SI:PERSONNEL-CLE}}", "PARA\t(e) proposer le personnel clé suivant :", "PARA\t{{SI:PERSONNEL-LISTE}}",
                "PARA\t{{MOYENS.personnel}}", "PARA\t{{FINSI:PERSONNEL-LISTE}}", "PARA\t{{FINSI:PERSONNEL-CLE}}", ""));
        cnm.prs.dto.FicheMarcheDto f = new cnm.prs.dto.FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche("QUANTITE_FIXE");
        f.setCategorie("TRAVAUX");
        f.setCadrage(new java.util.LinkedHashMap<>());
        f.setValeurs(new java.util.HashMap<>(Map.of("B03-QT-09", "Bétonnière, camion")));
        f.setValeursPpm(new java.util.HashMap<>());
        String texteSeul = FormulairesCandidat.rendreModele("DPAO", null, f, Map.of(), modele, null, Map.of()).texte();
        assertThat(texteSeul).contains("Bétonnière, camion").doesNotContain(FormulairesCandidat.POINTILLES, "(e) proposer");

        Map<String, String> jetons = MoyensFiche.jetons(List.of(engin("Niveleuse", null, 1, 1, false)),
                List.of(new PersonnelExigeDto(null, null, "Chef de chantier", 1, null, 3, null, null, false)));
        String listes = FormulairesCandidat.rendreModele("DPAO", null, f, Map.of(), modele, null, jetons).texte();
        assertThat(listes).contains("- Niveleuse : 1, en propre", "Bétonnière, camion", "(e) proposer le personnel clé suivant :",
                "- Chef de chantier (1) : au moins 3 ans d'expérience");
        assertThat(ConditionsModele.lisible("MOYENS.personnel renseigne ou B03-QT-13 renseigne")).isTrue();
    }

    @Test
    @DisplayName("400 nominatifs : désignation manquante, nombre < 1, minimum supérieur au nombre ; poste manquant, "
            + "expérience négative")
    void validation() {
        assertThatThrownBy(() -> MoyensFiche.validerMateriel(List.of(engin("Citerne à eau", null, 2, 3, false))))
                .isInstanceOf(ChampsInvalidesException.class)
                .satisfies(e -> assertThat(((ChampsInvalidesException) e).getErreurs()).extracting(x -> x.champ())
                        .containsExactly("materiel[0].minimumEnPropre"));
        assertThatThrownBy(() -> MoyensFiche.validerMateriel(List.of(engin(" ", null, 0, null, false))))
                .satisfies(e -> assertThat(((ChampsInvalidesException) e).getErreurs()).extracting(x -> x.champ())
                        .containsExactly("materiel[0].designation", "materiel[0].nombre"));
        assertThatThrownBy(() -> MoyensFiche.validerPersonnel(List.of(new PersonnelExigeDto(null, null, null, null, null, -1,
                null, null, false)))).satisfies(e -> assertThat(((ChampsInvalidesException) e).getErreurs())
                        .extracting(x -> x.champ()).containsExactly("personnel[0].poste", "personnel[0].experienceAnnees"));
        MoyensFiche.validerMateriel(List.of(engin("Citerne à eau", "≥ 5 000 l", 2, 1, false)));
    }
}
