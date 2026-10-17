package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ 2026-10-17 (demande front « cadrage-depuis-dao », §B1.1) — les règles de la lecture par clause sur des textes construits à la
 * main ; la mesure sur les DAO réels est {@link CadrageParClauseRecetteTest}.
 */
class CadrageParClauseTest {

    private static final Set<String> TOUTES = Set.copyOf(CadrageParClause.CLES);

    private static String valeur(CadrageParClause.Resultat r, String cle) {
        return r.reponses().stream().filter(x -> cle.equals(x.cle())).map(CadrageParClause.Reponse::valeur).findFirst().orElse(null);
    }

    @Test
    @DisplayName("Seules comptent les données particulières, l'acte d'engagement et le CCAP ; jamais les IC, le CCAG ni ce qui précède")
    void parties() {
        CadrageParClause.Resultat r = CadrageParClause.lire(List.of(
                "Les prix sont fermes et non révisables.",
                "1.1. - Instructions aux candidats",
                "Les variantes sont autorisées pour ce lot.",
                "1.2. - Données Particulières de l'Appel d'Offres (DPAO)",
                "Variantes : Les variantes ne sont pas prises en considération.",
                "Garantie de soumission : néant.",
                "2.3. - Cahier des Clauses Administratives Générales",
                "Les prix sont révisables selon la formule de révision.",
                "2.2. - Cahier des Clauses Administratives Particulières (CCAP)",
                "Les prix des marchés sont fermes."), TOUTES);
        assertThat(valeur(r, "variantes")).isEqualTo("NON");
        assertThat(valeur(r, "garantieSoumission")).isEqualTo("NON");
        assertThat(valeur(r, "prixRevisable")).isEqualTo("NON");
        assertThat(r.conflits()).isEmpty();
        assertThat(r.reponses()).allMatch(x -> x.partie() != null && !x.partie().contains("Instructions"));
    }

    @Test
    @DisplayName("Une phrase au conditionnel, qui renvoie aux DPAO, un trou de modèle ou une alternative à choisir ne compte pas")
    void phrasesGenerales() {
        CadrageParClause.Resultat r = CadrageParClause.lire(List.of(
                "Données particulières",
                "Les DPAO indiquent si les prix sont fermes ou révisables.",
                "Si elle est requise, une garantie de soumission d'un montant de 5 000 000 Ariary est fournie.",
                "Variantes : <autorisées / non autorisées>",
                "Avance : OUI / NON",
                "Le cas échéant, le groupement sera solidaire."), TOUTES);
        assertThat(r.reponses()).isEmpty();
    }

    @Test
    @DisplayName("Une négation l'emporte ; deux tournures contraires font un conflit ; un libellé seul se lit avec le paragraphe suivant")
    void negationEtConflit() {
        CadrageParClause.Resultat r = CadrageParClause.lire(List.of(
                "DONNEES PARTICULIERES D'APPEL A CONCURRENCE",
                "Les variantes ne sont pas autorisées.",
                "Aucune avance ne sera consentie au titulaire.",
                "Garantie de soumission :",
                "Une garantie de soumission d'un montant de Cinq millions Ariary (AR 5 000 000) doit être fournie.",
                "Le groupement devra être solidaire.",
                "Incoterm : CIP Antananarivo",
                "Acte d'engagement",
                "Les variantes sont autorisées."), TOUTES);
        assertThat(r.conflits()).containsKey("variantes");
        assertThat(valeur(r, "variantes")).isNull();
        assertThat(valeur(r, "avance")).isEqualTo("NON");
        assertThat(valeur(r, "garantieSoumission")).isEqualTo("OUI");
        assertThat(valeur(r, "formeGroupement")).isEqualTo("SOLIDAIRE_OBLIGATOIRE");
        assertThat(valeur(r, "provenance")).isEqualTo("IMPORTEES");
    }

    @Test
    @DisplayName("§B2 : le contrat-cadre ne cherche que la garantie de soumission et l'avance")
    void contratCadre() {
        assertThat(CadrageParClause.clesPour("CONTRAT_CADRE", "FOURNITURES_SERVICES")).containsExactlyInAnyOrder("garantieSoumission", "avance");
        CadrageParClause.Resultat r = CadrageParClause.lire(List.of("Contrat-cadre valant acte d'engagement et CCAP",
                "Les prix des marchés sont fermes."), CadrageParClause.clesPour("CONTRAT_CADRE", "FOURNITURES_SERVICES"));
        assertThat(r.reponses()).isEmpty();
        assertThat(CadrageParClause.clesPour("QUANTITE_FIXE", "FOURNITURES_SERVICES")).hasSize(6);
        assertThat(CadrageParClause.clesPour("QUANTITE_FIXE", "PRESTATIONS_INTELLECTUELLES")).doesNotContain("provenance");
    }
}
