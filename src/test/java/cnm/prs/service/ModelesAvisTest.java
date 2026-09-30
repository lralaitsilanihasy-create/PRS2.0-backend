package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;

/**
 * ⚠️ <strong>Avis spécifique d'appel d'offres</strong> (demande front du 2026-09-30, §B1, §B2, §B6) — AVIS-F et AVIS-T rendus
 * sur une fiche : les informations de publication ({@code {{AVIS.*}}}), les rédactions par forme et par type de prix,
 * l'adresse e-mail de consultation sous condition, la garantie. Pur.
 */
class ModelesAvisTest {

    private static final Map<String, String> PUBLICATION = Map.of("date-publication", "05/10/2026", "jmp-numero", "123",
            "jmp-date", "15/01/2026", "supports", "le quotidien Midi Madagasikara du 06/10/2026");

    private final ModelesDao dao = new ModelesDao();

    @Test
    @DisplayName("Travaux à quantité fixe, prix mixte, garantie, e-mail de consultation : AVIS-T, publication rendue, "
            + "numéro B02-OB-03, remise à B01-AC-02 le B04-OV-02")
    void travauxQuantiteFixeMixte() {
        FicheMarcheDto f = fiche("TRAVAUX", "QUANTITE_FIXE", Map.of("typePrix", "MIXTE", "alloti", "NON", "garantieSoumission", "OUI"));
        f.getValeurs().putAll(Map.of("B02-OB-03", "AOO n° 12/2026", "B04-OV-02", "2026-11-16T10:00", "B04-DS-11",
                "marches@exemple.mg", "B05-GQ-03", "2000000", "B04-DS-05", "100000"));
        String t = rendre("AVIS-T", f, PUBLICATION);
        assertThat(t).contains("05/10/2026", "Journal des Marchés Publics n°123 en date du 15/01/2026",
                "le quotidien Midi Madagasikara du 06/10/2026", "AOO n° 12/2026 — Réhabilitation du réseau d'eau",
                "pour exécuter les travaux suivants : Réhabilitation du réseau d'eau", "lot unique indivisible",
                "marches@exemple.mg", "deux millions")
                .doesNotContain("{{", "Les titulaires du contrat-cadre", "La soumission des offres par voie électronique sera autorisée.");
        f.getValeurs().remove("B04-DS-11");
        assertThat(rendre("AVIS-T", f, PUBLICATION)).doesNotContain("marches@exemple.mg");
    }

    @Test
    @DisplayName("Contrat-cadre de fournitures : AVIS-F, numéro B02-OE-01, remise à B04-RQ-03 le B04-CP-02, invitation des "
            + "titulaires aux marchés subséquents ; sans garantie ; informations de publication absentes → pointillés")
    void contratCadreFournitures() {
        FicheMarcheDto f = fiche("FOURNITURES_SERVICES", "CONTRAT_CADRE", Map.of("typePrix", "UNITAIRES", "alloti", "NON",
                "garantieSoumission", "NON"));
        f.getValeurs().putAll(Map.of("B02-OE-01", "CC n° 3/2026", "B04-RQ-03", "Bureau des marchés, porte 12"));
        String t = rendre("AVIS-F", f, PUBLICATION);
        assertThat(t).contains("CC n° 3/2026 — Réhabilitation du réseau d'eau", "Bureau des marchés, porte 12",
                "Les titulaires du contrat-cadre issu du présent appel d’offres")
                .doesNotContain("{{", "Chaque offre doit être accompagnée d’une garantie de soumission");
        assertThat(rendre("AVIS-F", f, Map.of())).contains("n°" + FormulairesCandidat.POINTILLES);
    }

    private String rendre(String sigle, FicheMarcheDto f, Map<String, String> publication) {
        return FormulairesCandidat.rendreModele("AVIS", null, f, Map.of(), dao.modele(sigle), null, publication).texte()
                .replace(' ', ' ').replace(' ', ' ');
    }

    private static FicheMarcheDto fiche(String categorie, String typeMarche, Map<String, String> cadrage) {
        FicheMarcheDto f = new FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche(typeMarche);
        f.setCategorie(categorie);
        f.setCadrage(new LinkedHashMap<>(cadrage));
        f.setValeurs(new HashMap<>());
        f.setValeursPpm(new HashMap<>(Map.of("B01-AC-01", "Ministère X", "B02-OB-01", "Réhabilitation du réseau d'eau",
                "B01-AC-05", "RAKOTO Jean", "B01-AC-02", "Antananarivo")));
        return f;
    }
}
