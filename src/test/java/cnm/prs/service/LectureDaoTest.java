package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ⚠️ Import du DAO (2026-09-28, ADR-0012) — la lecture par modèle inversé, sans base ni Spring. */
class LectureDaoTest {

    @Test
    @DisplayName("norm : apostrophes, tirets, guillemets, espaces insécables et blancs ramenés à une forme")
    void normalisation() {
        assertThat(LectureDao.norm("  L’autorité contractante – « prix »\n\tfin ")).isEqualTo("L'autorité contractante - \" prix \" fin");
    }

    @Test
    @DisplayName("valeurSaisie : dates JJ/MM/AAAA → ISO, date-heure, montants et pourcentages → nombre, pointillés → rien")
    void valeursDeSaisie() {
        assertThat(LectureDao.valeurSaisie("20/11/2026", "DATE", null)).isEqualTo("2026-11-20");
        assertThat(LectureDao.valeurSaisie("20/11/2026 10:00", "DATE_HEURE", null)).isEqualTo("2026-11-20T10:00");
        assertThat(LectureDao.valeurSaisie("250 000 000 Ariary", "MONTANT", null)).isEqualTo("250000000");
        assertThat(LectureDao.valeurSaisie("12,5 %", "POURCENTAGE", null)).isEqualTo("12.5");
        assertThat(LectureDao.valeurSaisie("1 500", null, "chiffres")).isEqualTo("1500");
        assertThat(LectureDao.valeurSaisie("………", "TEXTE", null)).isNull();
        assertThat(LectureDao.valeurSaisie("trente jours", "NOMBRE", null)).isNull();
        assertThat(LectureDao.valeurSaisie("Texte libre", null, null)).isEqualTo("Texte libre");
    }

    @Test
    @DisplayName("implications : les termes « = » d'une conjonction ; rien pour « ou » ; « != », contient, renseigne n'impliquent rien")
    void implications() {
        assertThat(ConditionsModele.implications("attributaires = MULTI et B02-PC-02 = Au fur et à mesure des besoins"))
                .containsExactly(Map.entry("attributaires", "MULTI"), Map.entry("B02-PC-02", "Au fur et à mesure des besoins"));
        assertThat(ConditionsModele.implications("attributaires = MONO ou B05-PM-02 = OUI")).isEmpty();
        assertThat(ConditionsModele.implications("attributaires = MULTI et B05-PM-05 != OUI"))
                .containsExactly(Map.entry("attributaires", "MULTI"));
        assertThat(ConditionsModele.implications("B05-UM-01 renseigne")).isEmpty();
    }

    @Test
    @DisplayName("lecture : texte fixe reconnu (haute si borné ou typé), paragraphe collé coupé (moyenne), jeton seul entre voisins, "
            + "section attestée → réponse de cadrage, deux jetons seuls dans un intervalle → ambigu")
    void lecture() {
        FichierCommande.Modele modele = FichierCommande.lireModele(String.join("\n",
                "CONDITION\tMONO\u001Fattributaires = MONO",
                "CONDITION\tMULTI\u001Fattributaires = MULTI",
                "TITRE\tDOCUMENT",
                "PARA\tDate limite : {{B04-CP-01}} à dix heures.",
                "PARA\tNom du Responsable : {{B04-DS-07}}",
                "PARA\tFonction : {{B04-DS-08}}",
                "SOUS_TITRE\tArticle 2 : Délais d'exécution",
                "PARA\t{{B07-DE-02}}",
                "PARA\t{{B07-DE-03}}",
                "SOUS_TITRE\tArticle 3 : Attribution",
                "PARA\t{{SI:MULTI}}",
                "PARA\tLe contrat-cadre est conclu avec plusieurs titulaires.",
                "PARA\t{{FINSI:MULTI}}",
                "PARA\tMontant : {{B05-MT-01}}"));
        List<String> doc = LectureDao.unitesDocument(List.of("DOCUMENT", "Date limite : 20/11/2026 à dix heures.",
                "Nom du Responsable : RAKOTO Jean Fonction : PRMP", "Article 2 : Délais d’exécution",
                "Trente jours.", "Article 3 : Attribution", "Le contrat-cadre est conclu avec plusieurs titulaires.",
                "Montant : 1 000 Ariary"));
        Map<String, LectureDao.InfoChamp> champs = Map.of("B04-CP-01", new LectureDao.InfoChamp("DATE", "SAISIE", null),
                "B05-MT-01", new LectureDao.InfoChamp("MONTANT", "SAISIE", null));
        LectureDao.Resultat r = LectureDao.lire("T", modele, doc, champs::get);

        // B5 règle 2 (2026-09-29) : « Montant : » n'a que 7 lettres de texte fixe — jamais haute, même typée.
        assertThat(r.propositions()).extracting(p -> p.code() + "=" + p.valeur() + ":" + p.confiance().libelle())
                .containsExactly("B04-CP-01=2026-11-20:haute", "B04-DS-07=RAKOTO Jean:moyenne", "B04-DS-08=PRMP:moyenne",
                        "B05-MT-01=1000:moyenne");
        assertThat(r.cadrage()).extracting(c -> c.cle() + "=" + c.valeur() + "@" + c.section()).containsExactly("attributaires=MULTI@MULTI");
        assertThat(r.ambigus()).singleElement().satisfies(a -> {
            assertThat(a.candidats()).containsExactly("B07-DE-02", "B07-DE-03");
            assertThat(a.texte()).isEqualTo("Trente jours.");
        });
        assertThat(r.nonTrouves()).containsExactly("B07-DE-02", "B07-DE-03");
    }

    @Test
    @DisplayName("Lot D2 (B5, 2026-09-29) : « {{CODE}}. » est un jeton seul, jamais un motif ; un libellé présent dans deux "
            + "rédactions n'atteste aucune section ; marqueurs de cellule et de rangée ; {{CODE.parLot}} → CODE#1, CODE#2")
    void reglesDuLotD2() {
        FichierCommande.Modele modele = FichierCommande.lireModele(String.join("\n",
                "CONDITION\tFERME\u001FprixRevisable = NON",
                "CONDITION\tREVISABLE\u001FprixRevisable = OUI",
                "CONDITION\tIMPORTEES\u001Fprovenance = IMPORTEES",
                "TABLE\t2",
                "LIGNE\tClause des instructions aux candidats\u001FDonnées particulières de l'appel",
                "LIGNE\t6.6.3. Caractère ferme ou révisable des prix\u001F{{SI:FERME}}\u001ELes prix sont fermes et non révisables"
                        + " pendant toute la durée\u001E{{FINSI:FERME}}\u001E{{SI:REVISABLE}}\u001ELes prix sont révisables selon la"
                        + " formule du CCAP\u001E{{FINSI:REVISABLE}}",
                "LIGNE\t{{SI:IMPORTEES}}\u001F",
                "LIGNE\t6.7. Monnaie des fournitures importées\u001FDevise des fournitures importées",
                "LIGNE\t{{FINSI:IMPORTEES}}\u001F",
                "LIGNE\t6.8. Garantie de soumission\u001FLe montant de la garantie de soumission est de :\u001E{{B05-GS-03.parLot}}.",
                "LIGNE\t6.9. Devise\u001F{{B05-MO-02}}.",
                "LIGNE\t7.1. Forme des plis\u001FOutre l'original de l'offre, les plis comprennent des copies",
                "FIN_TABLE",
                "PARA\t{{SI:FERME}}",
                "PARA\tFerme",
                "PARA\t{{FINSI:FERME}}",
                "PARA\t{{SI:REVISABLE}}",
                "PARA\tFerme",
                "PARA\t{{FINSI:REVISABLE}}"));
        List<String> doc = LectureDao.unitesDocument(List.of(
                "Clause des instructions aux candidats\tDonnées particulières de l'appel",
                "6.6.3. Caractère ferme ou révisable des prix\tLes prix sont fermes et non révisables pendant toute la durée",
                "6.8. Garantie de soumission\tLe montant de la garantie de soumission est de :\u001ELot n° 1 : 1 000 000 Ariary ; Lot n° 2 : 500 000 Ariary.",
                "6.9. Devise\tEuro.",
                "7.1. Forme des plis\tOutre l'original de l'offre, les plis comprennent des copies",
                "Ferme"));
        Map<String, LectureDao.InfoChamp> champs = Map.of("B05-GS-03", new LectureDao.InfoChamp("MONTANT", "SAISIE", null));
        LectureDao.Resultat r = LectureDao.lire("T", modele, doc, champs::get);

        // la rédaction « fermes » (36 lettres, dans FERME seule) atteste FERME ; « Ferme », présent dans FERME et REVISABLE,
        // n'atteste rien ; la rangée IMPORTEES, absente du document, n'est pas attestée
        assertThat(r.cadrage()).extracting(c -> c.cle() + "=" + c.valeur()).containsExactly("prixRevisable=NON");
        assertThat(r.propositions()).extracting(p -> p.code() + "=" + p.valeur())
                .contains("B05-GS-03#1=1000000", "B05-GS-03#2=500000", "B05-MO-02=Euro");   // le point du modèle n'est pas la valeur
        // « {{B05-MO-02}}. » : jeton seul, lu entre ses voisins (jamais haute)
        assertThat(r.propositions()).filteredOn(p -> p.code().equals("B05-MO-02")).singleElement()
                .satisfies(p -> assertThat(p.confiance()).isNotEqualTo(LectureDao.Confiance.HAUTE));
        assertThat(r.nonTrouves()).doesNotContain("B05-GS-03");
    }
}
