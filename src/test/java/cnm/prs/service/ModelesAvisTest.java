package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.ChampFicheMarche;

/**
 * ⚠️ <strong>Avis spécifique d'appel d'offres</strong> (demande front du 2026-09-30, §B1, §B2, §B6 ; aligné sur un avis réel
 * le 2026-10-01, §B7, §B8) — AVIS-F et AVIS-T rendus sur une fiche : l'emblème en tête, le numéro seul, la numérotation
 * continue des paragraphes imprimés, l'heure locale, le montant du DAO par lot, les supports facultatifs, le compte de
 * l'ARMP. Pur.
 */
class ModelesAvisTest {

    private static final String COMPTE = "BNI Madagascar, compte n° 00005 00001 12345678901 23 au nom de ARMP";

    private final ModelesDao dao = new ModelesDao();

    @Test
    @DisplayName("Travaux à quantité fixe, lot unique, garantie : emblème en tête, numéro seul « N° … », objet entre guillemets, "
            + "paragraphes numérotés 1. à n. sans trou, heure locale, compte de l'ARMP, lieu et date en bas")
    void travauxQuantiteFixe() {
        FicheMarcheDto f = fiche("TRAVAUX", "QUANTITE_FIXE", Map.of("typePrix", "MIXTE", "alloti", "NON",
                "garantieSoumission", "OUI"));
        f.getValeurs().putAll(Map.of("B02-OB-03", "14-26/AOO/REG", "B04-OV-02", "2026-10-12T09:00", "B05-GQ-03", "2000000",
                "B04-DS-05", "100000", "B04-DS-10", "Antananarivo"));
        DocumentLibre d = rendre("AVIS-T", f, publication(true));
        // L'emblème est en tête du corps, juste après le titre du modèle (ligne TITRE), avant l'autorité.
        int image = d.elements().stream().map(e -> e instanceof DocumentLibre.Image).toList().indexOf(true);
        assertThat(image).isBetween(0, 1);
        assertThat(((DocumentLibre.Paragraphe) d.elements().get(image + 1)).texte()).isEqualTo("Région Analamanga");
        String t = texte(d);
        assertThat(t).contains("N° 14-26/AOO/REG", "« Réhabilitation du réseau d'eau »", "le 12/10/2026 à 09 h 00 (heure locale)",
                "cent mille ariary (Ar 100 000)", COMPTE, "deux millions", "et dans le quotidien Midi Madagasikara du 06/10/2026",
                "à Antananarivo, le 05/10/2026")
                .doesNotContain("{{", "14-26/AOO/REG — ", "La garantie de soumission n’est pas requise.");
        assertNumerotationContinue(t);
    }

    @Test
    @DisplayName("Fournitures, contrat-cadre alloti, sans garantie, sans numéro de JMP ni supports : montant du DAO lot par "
            + "lot (une ligne par lot), « La garantie de soumission n’est pas requise. », pas de « et dans », pointillés pour "
            + "le JMP et pour un compte non réglé ; numérotation sans trou")
    void contratCadreAlloti() {
        FicheMarcheDto f = fiche("FOURNITURES_SERVICES", "CONTRAT_CADRE", Map.of("typePrix", "UNITAIRES", "alloti", "OUI",
                "garantieSoumission", "NON"));
        f.setSaisieParLot(true);
        f.setNbLots(2);
        f.getValeurs().putAll(Map.of("B02-OE-01", "CC n° 3/2026", "B04-DS-05#1", "100000", "B04-DS-05#2", "150000",
                "B04-CP-02", "2026-11-03T10:30"));
        Map<String, String> publication = publication(false);
        publication.remove(FormulairesCandidat.JETON_COMPTE_DAO);
        String t = texte(rendre("AVIS-F", f, publication));
        assertThat(t).contains("N° CC n° 3/2026", "- Lot 1 : cent mille ariary (Ar 100 000)",
                "- Lot 2 : cent cinquante mille ariary (Ar 150 000)", "La garantie de soumission n’est pas requise.",
                "n°" + FormulairesCandidat.POINTILLES, "le compte bancaire de l’ARMP : " + FormulairesCandidat.POINTILLES,
                "le 03/11/2026 à 10 h 30 (heure locale)", "Les titulaires du contrat-cadre")
                .doesNotContain("{{", "et dans");
        assertThat(t.lines().filter(l -> l.startsWith("- Lot ")).count()).isEqualTo(2);   // un paragraphe par lot
        assertNumerotationContinue(t);
    }

    @Test
    @DisplayName("Passe finale : un repère d'image inconnu est omis (jamais imprimé tel quel) ; {{NUM}} numérote dans l'ordre "
            + "des paragraphes imprimés")
    void passeFinale() {
        List<DocumentLibre.Element> e = FormulairesCandidat.finaliser(List.of(
                new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "{{IMAGE:inconnue}}"),
                new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "{{IMAGE:embleme}}"),
                new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "{{NUM}} premier"),
                new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "sans numéro"),
                new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "{{NUM}} second")));
        assertThat(e).hasSize(4);
        assertThat(e.get(0)).isInstanceOfSatisfying(DocumentLibre.Image.class, im -> {
            assertThat(im.nom()).isEqualTo("embleme");
            assertThat(im.contenu()).isNotEmpty();
        });
        assertThat(e.subList(1, 4)).extracting(x -> ((DocumentLibre.Paragraphe) x).texte())
                .containsExactly("1. premier", "sans numéro", "2. second");
        assertThat(FormulairesCandidat.heureLocale("2026-10-12T09:00")).isEqualTo("12/10/2026 à 09 h 00 (heure locale)");
    }

    /** Les paragraphes numérotés le sont 1., 2., 3.… sans trou ni doublon. */
    private static void assertNumerotationContinue(String texte) {
        List<Integer> numeros = texte.lines().map(l -> java.util.regex.Pattern.compile("^(\\d+)\\. ").matcher(l))
                .filter(java.util.regex.Matcher::find).map(m -> Integer.parseInt(m.group(1))).toList();
        assertThat(numeros).isNotEmpty();
        for (int i = 0; i < numeros.size(); i++) {
            assertThat(numeros.get(i)).as("numéro " + (i + 1)).isEqualTo(i + 1);
        }
    }

    private DocumentLibre rendre(String sigle, FicheMarcheDto f, Map<String, String> publication) {
        return FormulairesCandidat.rendreModele("AVIS", null, f, champs(), dao.modele(sigle), null, publication);
    }

    private static String texte(DocumentLibre d) {
        return d.texte().replace(' ', ' ').replace(' ', ' ');
    }

    private static Map<String, String> publication(boolean avecSupports) {
        Map<String, String> p = new HashMap<>(Map.of("date-publication", "05/10/2026", "jmp-date", "15/01/2026",
                FormulairesCandidat.JETON_COMPTE_DAO, COMPTE));
        if (avecSupports) {
            p.put("jmp-numero", "123");
            p.put("supports", "le quotidien Midi Madagasikara du 06/10/2026");
        }
        return p;
    }

    private static Map<String, ChampFicheMarche> champs() {
        ChampFicheMarche ds05 = new ChampFicheMarche();
        ds05.setCode("B04-DS-05");
        ds05.setType("MONTANT");
        ds05.setParLot(true);
        ds05.setActif(true);
        ChampFicheMarche gq03 = new ChampFicheMarche();
        gq03.setCode("B05-GQ-03");
        gq03.setType("MONTANT");
        gq03.setActif(true);
        ChampFicheMarche dh = new ChampFicheMarche();
        dh.setCode("B04-OV-02");
        dh.setType("DATE_HEURE");
        dh.setActif(true);
        return Map.of("B04-DS-05", ds05, "B05-GQ-03", gq03, "B04-OV-02", dh);
    }

    private static FicheMarcheDto fiche(String categorie, String typeMarche, Map<String, String> cadrage) {
        FicheMarcheDto f = new FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche(typeMarche);
        f.setCategorie(categorie);
        f.setCadrage(new LinkedHashMap<>(cadrage));
        f.setValeurs(new HashMap<>());
        f.setValeursPpm(new HashMap<>(Map.of("B01-AC-01", "Région Analamanga", "B02-OB-01", "Réhabilitation du réseau d'eau",
                "B01-AC-05", "RAKOTO Jean", "B01-AC-02", "Antananarivo")));
        return f;
    }
}
