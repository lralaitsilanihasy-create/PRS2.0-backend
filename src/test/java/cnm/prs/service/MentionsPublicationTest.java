package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.dto.MentionsDto;

/**
 * ⚠️ R3c (manuel révisé pour l'application, lot 1, §B3 ; arbitrage Q3 : « mentions complètes ») — les mentions du modèle type,
 * gabarit par gabarit : AVIS-F, AVIS-T et LETTRE-AOR portent toutes les leurs ; LETTRE-PI n'a ni coût ni paiement (aucun champ :
 * écart nommé) ; sur une fiche vide, les valeurs manquent (pointillés) ; sur une fiche remplie, le document est complet, les mentions
 * sans objet (lot unique, pas de visite) le disent.
 */
class MentionsPublicationTest {

    private final ModelesDao dao = new ModelesDao();

    @Test
    @DisplayName("Gabarits : AVIS-F, AVIS-T et LETTRE-AOR complets ; LETTRE-PI : COUT_DOSSIER et COMPTE_PAIEMENT absents, nommés")
    void gabarits() {
        for (String sigle : List.of("AVIS-F", "AVIS-T", ModelesDao.LETTRE_AOR)) {
            MentionsDto m = verifier(sigle, fiche("FOURNITURES_SERVICES"), Map.of());
            assertThat(m.gabarit()).isEqualTo(sigle);
            assertThat(m.gabaritComplet()).as(sigle + " : " + ecarts(m)).isTrue();
            assertThat(m.mentions()).extracting(MentionsDto.Mention::code).contains("OBJET", "DATE_HEURE_LIMITE", "HEURE_OUVERTURE",
                    "COUT_DOSSIER", "COMPTE_PAIEMENT", "FORMES_GARANTIE", "MONTANT_GARANTIE");
        }
        MentionsDto t = verifier("AVIS-T", fiche("TRAVAUX"), Map.of());
        assertThat(t.mentions()).extracting(MentionsDto.Mention::code).contains("VISITE_LIEUX", "REUNION_PREPARATOIRE", "LOTS_MAX");
        MentionsDto pi = verifier("LETTRE-PI", fiche("PRESTATIONS_INTELLECTUELLES"), Map.of());
        assertThat(pi.gabaritComplet()).isFalse();
        assertThat(ecarts(pi)).containsExactly("COUT_DOSSIER", "COMPTE_PAIEMENT");
        assertThat(pi.mentions().stream().filter(x -> "COUT_DOSSIER".equals(x.code())).findFirst().orElseThrow().detail())
                .contains("aucun champ");
        assertThat(pi.mentions()).extracting(MentionsDto.Mention::code).contains("LISTE_CANDIDATS", "MODE_SELECTION", "ADRESSE_REMISE",
                "DATE_HEURE_LIMITE", "HEURE_OUVERTURE", "ADRESSE_ACCUSE");
        assertThat(MentionsPublication.mentions("DPAO-F")).isEmpty();
    }

    @Test
    @DisplayName("Document : fiche vide → objet, adresses, date limite, coût, compte non renseignés ; fiche remplie, lot unique, sans "
            + "garantie → complet, LOTS_MAX sans objet, garantie « n'est pas requise » renseignée")
    void document() {
        FicheMarcheDto vide = fiche("FOURNITURES_SERVICES");
        MentionsDto m = verifier("AVIS-F", vide, Map.of());
        assertThat(m.documentComplet()).isFalse();
        assertThat(nonRenseignees(m)).contains("OBJET", "ADRESSE_CONSULTATION", "ADRESSE_RETRAIT", "DATE_HEURE_LIMITE", "COUT_DOSSIER",
                "COMPTE_PAIEMENT").doesNotContain("FORME_PRIX", "HEURE_OUVERTURE", "FORMES_GARANTIE", "MONTANT_GARANTIE");
        assertThat(m.mentions().stream().filter(x -> "LOTS_MAX".equals(x.code())).findFirst().orElseThrow().sansObjet()).isTrue();

        FicheMarcheDto pleine = fiche("FOURNITURES_SERVICES");
        pleine.getValeursPpm().putAll(Map.of("B02-OB-01", "Fourniture de mobilier", "B01-AC-01", "Région Analamanga"));
        pleine.getValeurs().putAll(Map.of("B04-DS-07", "RABE Paul", "B04-DS-08", "Chef UGPM", "B04-DS-09", "Lot II A 12", "B04-DS-10",
                "Antananarivo", "B04-DS-05", "100000", "B04-LR-02", "la salle des marchés", "B04-LR-03", "2026-11-03", "B04-LR-04", "10 h"));
        MentionsDto ok = verifier("AVIS-F", pleine, Map.of(FormulairesCandidat.JETON_COMPTE_DAO, "BNI, compte n° 1 au nom de ARMP"));
        assertThat(ok.documentComplet()).as(String.join(", ", nonRenseignees(ok))).isTrue();
        assertThat(ok.mentions()).allSatisfy(x -> assertThat(x.presenteAuGabarit()).isTrue());

        // Travaux, alloti sans maximum de lots, visite prévue mais réunion non : LOTS_MAX manque, VISITE renseignée, REUNION sans objet.
        FicheMarcheDto travaux = fiche("TRAVAUX");
        travaux.getCadrage().put("alloti", "OUI");
        travaux.getValeurs().putAll(Map.of("B04-VL-02", "OUI", "B04-RP-01", "NON"));
        MentionsDto tr = verifier("AVIS-T", travaux, Map.of());
        assertThat(nonRenseignees(tr)).contains("LOTS_MAX").doesNotContain("VISITE_LIEUX");
        assertThat(tr.mentions().stream().filter(x -> "REUNION_PREPARATOIRE".equals(x.code())).findFirst().orElseThrow().sansObjet()).isTrue();
        assertThat(tr.mentions().stream().filter(x -> "VISITE_LIEUX".equals(x.code())).findFirst().orElseThrow().renseignee()).isTrue();
    }

    private MentionsDto verifier(String sigle, FicheMarcheDto f, Map<String, String> jetons) {
        boolean lettre = sigle.startsWith("LETTRE");
        DocumentLibre rendu = FormulairesCandidat.rendreModele(lettre ? "LETTRE_INVITATION" : "AVIS", null, f, Map.of(), dao.modele(sigle),
                null, jetons);
        return MentionsPublication.verifier(sigle, dao.modele(sigle), rendu, f);
    }

    private static List<String> ecarts(MentionsDto m) {
        return m.mentions().stream().filter(x -> !x.presenteAuGabarit()).map(MentionsDto.Mention::code).toList();
    }

    private static List<String> nonRenseignees(MentionsDto m) {
        return m.mentions().stream().filter(x -> Boolean.FALSE.equals(x.renseignee())).map(MentionsDto.Mention::code).toList();
    }

    private static FicheMarcheDto fiche(String categorie) {
        FicheMarcheDto f = new FicheMarcheDto();
        f.setIdDetail(1);
        f.setVersion(1);
        f.setTypeMarche("QUANTITE_FIXE");
        f.setCategorie(categorie);
        Map<String, Object> cadrage = new LinkedHashMap<>();
        cadrage.put("typePrix", "UNITAIRES");
        cadrage.put("alloti", "NON");
        cadrage.put("garantieSoumission", "NON");
        f.setCadrage(cadrage);
        f.setValeurs(new HashMap<>());
        f.setValeursPpm(new HashMap<>());
        return f;
    }
}
