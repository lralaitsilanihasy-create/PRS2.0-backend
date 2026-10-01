package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;

/**
 * ⚠️ 2026-10-01 (lot AV-4.1, demande front « lettres d'invitation », §B1, §B2, §B6) — le modèle LETTRE-PI rendu : en-tête
 * commun avec l'avis (emblème en premier), destinataire et liste restreinte sur plusieurs lignes, une rédaction du mode
 * de sélection parmi quatre. La fidélité au modèle (43/43) se mesure par le comparateur du front sur le rendu brut
 * ({@code ModelesDaoTest#renduBrut}). Pur : ni base, ni Spring.
 */
class ModelesLettreTest {

    private static final char SEP = FormulairesCandidat.SEPARATEUR_LIGNES;
    private static final List<String> REDACTIONS = List.of(
            "de la qualité technique de la proposition, de l’expérience du candidat",
            "d’un budget prédéterminé dont le candidat doit proposer la meilleure utilisation possible,",
            "de la meilleure proposition financière soumise par les candidats ayant obtenu une notation technique minimum.",
            "exclusivement de la qualité technique");

    private final ModelesDao dao = new ModelesDao();

    @Test
    @DisplayName("Le modèle est chargé hors des couvertures (ni produit à la validation, ni lu à l'import), pour les seules "
            + "prestations intellectuelles, avec ses quatre conditions")
    void registre() {
        assertThat(ModelesDao.sigleLettre("PRESTATIONS_INTELLECTUELLES")).isEqualTo("LETTRE-PI");
        assertThat(ModelesDao.sigleLettre("FOURNITURES_SERVICES")).isNull();
        assertThat(ModelesDao.sigleLettre("TRAVAUX")).isNull();
        assertThat(ModelesDao.COUVERTURES).noneMatch(c -> c.sigle().startsWith("LETTRE"));
        assertThat(dao.modele("LETTRE-PI").conditions()).containsOnlyKeys("SFQC", "BUDGET", "MOINDRE-COUT", "QUALITE-SEULE");
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = ';', value = {
            "Qualité technique, expérience et proposition financière;0",
            "Budget prédéterminé dont le candidat propose la meilleure utilisation;1",
            "Meilleure proposition financière parmi les candidats ayant obtenu la note technique minimale;2",
            "Qualité technique exclusivement;3"})
    @DisplayName("Une lettre : emblème en premier, référence « numéro — objet », lieu et date, destinataire nom puis adresse "
            + "(une ligne chacun), liste restreinte « - Nom » une ligne par candidat, la seule rédaction du mode de sélection, "
            + "l'adresse de la PRMP, la signature gardée ensemble")
    void lettre(String mode, int attendue) {
        FicheMarcheDto f = fiche(mode);
        Map<String, String> jetons = new HashMap<>();
        jetons.put("LETTRE.lieu", "Antananarivo");
        jetons.put("LETTRE.date", "05/10/2026");
        jetons.put("LETTRE.destinataire", "Cabinet A" + SEP + "Lot II A 12" + SEP + "Antananarivo");
        jetons.put("LETTRE.candidats", "- Cabinet A" + SEP + "- Bureau B");
        DocumentLibre d = FormulairesCandidat.rendreModele("LETTRE_INVITATION", null, f, Map.of(), dao.modele("LETTRE-PI"), null,
                jetons).finGardeeEnsemble(3);

        assertThat(d.elements().get(0)).isInstanceOfSatisfying(DocumentLibre.Image.class, im -> assertThat(im.nom()).isEqualTo("embleme"));
        List<String> paras = d.elements().stream().filter(e -> e instanceof DocumentLibre.Paragraphe)
                .map(e -> ((DocumentLibre.Paragraphe) e).texte()).toList();
        assertThat(paras).containsSubsequence("Ministère X", "LETTRE D’INVITATION", "Référence: 14-26/AOI/PI — Étude de faisabilité",
                "Antananarivo, 05/10/2026", "Cabinet A", "Lot II A 12", "Antananarivo", "Madame, Monsieur",
                "- Cabinet A", "- Bureau B", "Antananarivo (prmp@ministere.mg):", "La Personne Responsable des Marchés Publics",
                "RAKOTO Jean");
        String texte = d.texte();
        assertThat(texte).doesNotContain("{{", "………");
        for (int i = 0; i < REDACTIONS.size(); i++) {
            if (i == attendue) {
                assertThat(texte).as("rédaction retenue").contains(REDACTIONS.get(i));
            } else {
                assertThat(texte).as("rédaction " + i).doesNotContain(REDACTIONS.get(i));
            }
        }
        List<DocumentLibre.Paragraphe> fin = d.elements().stream().filter(e -> e instanceof DocumentLibre.Paragraphe)
                .map(e -> (DocumentLibre.Paragraphe) e).toList();
        assertThat(fin.subList(fin.size() - 3, fin.size())).extracting(DocumentLibre.Paragraphe::solidaireDuSuivant)
                .containsExactly(true, true, false);
    }

    private static FicheMarcheDto fiche(String mode) {
        FicheMarcheDto f = new FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche("QUANTITE_FIXE");
        f.setCategorie("PRESTATIONS_INTELLECTUELLES");
        f.setCadrage(new LinkedHashMap<>());
        f.setValeurs(new HashMap<>(Map.of("B02-MS-01", mode)));
        f.setValeursPpm(new HashMap<>(Map.of("B01-AC-01", "Ministère X", "B02-OB-01", "Étude de faisabilité",
                "B02-OB-03", "14-26/AOI/PI", "B01-AC-05", "RAKOTO Jean", "B01-AC-02", "Antananarivo",
                "B01-AC-06", "prmp@ministere.mg")));
        return f;
    }
}
