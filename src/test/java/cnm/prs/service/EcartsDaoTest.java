package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * ⚠️ 2026-10-23 (demande front « ecarts-de-forme », B1) — la nature d'un écart : forme (orthographe, accords, ponctuation, espaces,
 * tirets, style) ou fond (mot ajouté ou retiré, mot remplacé, nombre, jeton, règle, mot court qui change de sens), sur les exemples de
 * la relecture des documents types.
 */
class EcartsDaoTest {

    private static String nature(String avant, String apres) {
        return EcartsDao.nature("PARA\t" + avant, "PARA\t" + apres);
    }

    @Test
    void formes() {
        assertThat(nature("la pré - qualification", "la pré-qualification")).isEqualTo("FORME");
        assertThat(nature("les taxes para fiscales", "les taxes parafiscales")).isEqualTo("FORME");
        assertThat(nature("Données Pariculières", "Données Particulières")).isEqualTo("FORME");
        assertThat(nature("si elles ne tombe pas", "si elles ne tombent pas")).isEqualTo("FORME");
        assertThat(nature("des GROUPEMENTS", "du GROUPEMENT")).isEqualTo("FOND");
        assertThat(nature("les bons de commande", "les bons de commandes")).isEqualTo("FORME");
        assertThat(nature("ci - après dans:", "ci-après dans :")).isEqualTo("FORME");
        assertThat(nature("la commission nationale", "la commission national")).isEqualTo("FORME");
        assertThat(nature("et le", "est la")).isEqualTo("FORME");
        assertThat(nature("le trentième (30ème) jour", "le trentième (30e) jour")).isEqualTo("FORME");
        assertThat(EcartsDao.nature("PARA\tTitre", "CENTRE\tTitre")).isEqualTo("FORME");
    }

    @Test
    void fonds() {
        assertThat(nature("s'il ne se conforme soit", "s'il ne se conforme pas soit")).isEqualTo("FOND");
        assertThat(nature("adopté par 2006-343", "adopté par décret n° 2006-343")).isEqualTo("FOND");
        assertThat(nature("Les rémunérations commissions", "Les rémunérations ou commissions")).isEqualTo("FOND");
        assertThat(nature("dans un délai de 30 jours", "dans un délai de 60 jours")).isEqualTo("FOND");
        assertThat(nature("le titulaire ou le sous-traitant", "le titulaire et le sous-traitant")).isEqualTo("FOND");
        assertThat(nature("le montant {{B05-GS-03}}", "le montant {{B05-GS-04}}")).isEqualTo("FOND");
        assertThat(nature("l'article", "la clause")).isEqualTo("FOND");
        assertThat(EcartsDao.nature("CONDITION\tCC\u001FtypeMarche = CONTRAT_CADRE", "CONDITION\tCC\u001FtypeMarche != CONTRAT_CADRE"))
                .isEqualTo("FOND");
    }

    @Test
    void ajoutsEtRetraitsSontDuFond() {
        assertThat(EcartsDao.ecarts("PARA\tun\nPARA\tdeux\n", "PARA\tun\n")).singleElement()
                .satisfies(e -> assertThat(e.type() + "/" + e.nature()).isEqualTo("RETIRE/FOND"));
        assertThat(EcartsDao.ecarts("PARA\tun\n", "PARA\tun\nPARA\ttrois\n")).singleElement()
                .satisfies(e -> assertThat(e.type() + "/" + e.nature()).isEqualTo("AJOUTE/FOND"));
        assertThat(EcartsDao.ecarts("PARA\tLes Pariculières\n", "PARA\tLes Particulières\n")).singleElement()
                .satisfies(e -> assertThat(e.type() + "/" + e.nature()).isEqualTo("MODIFIE/FORME"));
    }
}
