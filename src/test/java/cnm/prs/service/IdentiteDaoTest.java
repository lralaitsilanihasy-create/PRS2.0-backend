package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.ImportDaoResult;

/**
 * ⚠️ 2026-10-17 (demande front « identite-dao-importe ») — le verdict d'identité sur des textes construits à la main (la mesure sur les
 * DAO réels est {@link IdentiteDaoRecetteTest}), la marque de l'application (B4) et l'avertissement du DAO périmé (B3).
 */
class IdentiteDaoTest {

    private static final String MEF = "MINISTERE DE L'ECONOMIE ET DES FINANCES";
    private static final IdentiteDao.Plan BUREAU = new IdentiteDao.Plan("Fourniture d’articles de bureau (CONTRAT - CADRE)", List.of(MEF),
            List.of(), "Appel d'offres ouvert", "Contrat cadre", 1, List.of());
    private static final List<IdentiteDao.Voisine> VOISINES = List.of(
            new IdentiteDao.Voisine(303339, "Fourniture de consomptibles informatiques (CONTRAT - CADRE)", List.of()),
            new IdentiteDao.Voisine(303338, "Fourniture de produits d’entretien et des petits matériels (Contrat cadre)", List.of()));

    private static IdentiteDao.Texte garde(String... paragraphes) {
        return new IdentiteDao.Texte(List.of(paragraphes), null);
    }

    private static String etat(ImportDaoResult.Identite v, String critere) {
        return v.criteres().stream().filter(c -> critere.equals(c.critere())).findFirst().orElseThrow().etat();
    }

    @Test
    @DisplayName("Sa propre ligne : objet (sans la mention entre parenthèses), autorité, mode, forme retrouvés → CONFORME")
    void conforme() {
        ImportDaoResult.Identite v = IdentiteDao.juger(garde("MINISTÈRE DE L'ÉCONOMIE ET DES FINANCES", "DOSSIER D'APPEL D'OFFRES OUVERT",
                "Objet : Fourniture d'articles de bureau, en un lot unique", "Le présent contrat-cadre porte sur la fourniture."),
                BUREAU, VOISINES, List.of(), Set.of());
        assertThat(v.verdict()).isEqualTo(IdentiteDao.CONFORME);
        assertThat(v.criteres()).extracting(ImportDaoResult.Critere::etat).containsOnly(IdentiteDao.RETROUVE);
        assertThat(v.voisine()).isNull();
    }

    @Test
    @DisplayName("B1.4 : l'objet de la garde ressemble nettement plus à une voisine → OBJET CONTREDIT, la voisine nommée, ETRANGER")
    void voisineNettementPlusProche() {
        ImportDaoResult.Identite v = IdentiteDao.juger(garde(MEF, "DOSSIER D'APPEL D'OFFRES OUVERT",
                "Objet : Fourniture de consomptibles informatiques (CONTRAT - CADRE), en un lot", "unique", "Contrat-cadre"),
                BUREAU, VOISINES, List.of(), Set.of());
        assertThat(v.verdict()).isEqualTo(IdentiteDao.ETRANGER);
        assertThat(etat(v, IdentiteDao.OBJET)).isEqualTo(IdentiteDao.CONTREDIT);
        assertThat(v.voisine().idDetail()).isEqualTo(303339);
        assertThat(v.motif()).contains("ligne 303339");
    }

    @Test
    @DisplayName("B1.4 : une voisine ressemble autant → OBJET INTROUVABLE, voisine nommée au motif, NON_PROUVE")
    void voisineAussiProche() {
        IdentiteDao.Plan plan = new IdentiteDao.Plan("Entretien et réparation des véhicules administratifs pour la DAF", List.of(MEF), List.of(),
                "Appel d'offres ouvert", null, 1, List.of());
        List<IdentiteDao.Voisine> voisines = List.of(new IdentiteDao.Voisine(303349,
                "Entretien et réparation des véhicules administratifs pour la DGI", List.of()));
        ImportDaoResult.Identite v = IdentiteDao.juger(garde(MEF, "Appel d'offres ouvert", "OBJET DU MARCHE : Entretien et réparation des "
                + "véhicules administratifs"), plan, voisines, List.of(), Set.of());
        assertThat(v.verdict()).isEqualTo(IdentiteDao.NON_PROUVE);
        assertThat(etat(v, IdentiteDao.OBJET)).isEqualTo(IdentiteDao.INTROUVABLE);
        assertThat(v.motif()).contains("303349", "lui ressemble autant");
    }

    @Test
    @DisplayName("B1.3 : l'objet de la garde ne ressemble pas à celui du plan, sans voisine → CONTREDIT ; introuvable sans indice → NON_PROUVE")
    void objetSansRapport() {
        ImportDaoResult.Identite v = IdentiteDao.juger(garde(MEF, "Appel d'offres ouvert", "Objet :", "Travaux de réhabilitation du stade "
                + "municipal"), BUREAU, List.of(), List.of(), Set.of());
        assertThat(etat(v, IdentiteDao.OBJET)).isEqualTo(IdentiteDao.CONTREDIT);
        assertThat(v.verdict()).isEqualTo(IdentiteDao.ETRANGER);

        ImportDaoResult.Identite muet = IdentiteDao.juger(garde(MEF, "Appel d'offres ouvert contrat-cadre", "Le présent dossier est "
                + "constitué des pièces suivantes."), BUREAU, List.of(), List.of(), Set.of());
        assertThat(etat(muet, IdentiteDao.OBJET)).isEqualTo(IdentiteDao.INTROUVABLE);
        assertThat(muet.verdict()).isEqualTo(IdentiteDao.NON_PROUVE);
    }

    @Test
    @DisplayName("B1.3 : une autre autorité nommée en tête, celle du plan nulle part → AUTORITE CONTREDIT ; le sigle vaut l'autorité")
    void autreAutorite() {
        IdentiteDao.Plan plan = new IdentiteDao.Plan("Fourniture d'articles de bureau", List.of(MEF), List.of("MEF"), "Appel d'offres ouvert",
                null, 1, List.of());
        ImportDaoResult.Identite v = IdentiteDao.juger(garde("MINISTERE DES TRAVAUX PUBLICS", "Appel d'offres ouvert",
                "Objet : Fourniture d'articles de bureau"), plan, List.of(), List.of("MINISTERE DES TRAVAUX PUBLICS", "JIRAMA"), Set.of());
        assertThat(etat(v, IdentiteDao.AUTORITE)).isEqualTo(IdentiteDao.CONTREDIT);
        assertThat(v.motif()).contains("MINISTERE DES TRAVAUX PUBLICS");

        ImportDaoResult.Identite sigle = IdentiteDao.juger(garde("MEF/SG/PRMP", "Appel d'offres ouvert", "Objet : Fourniture d'articles de bureau"),
                plan, List.of(), List.of("MINISTERE DES TRAVAUX PUBLICS"), Set.of());
        assertThat(etat(sigle, IdentiteDao.AUTORITE)).isEqualTo(IdentiteDao.RETROUVE);
    }

    @Test
    @DisplayName("Mode et forme : une divergence déjà lue contredit ; lots : plus de lots que le plan contredit, tous retrouvés → RETROUVE")
    void modeFormeLots() {
        ImportDaoResult.Identite mode = IdentiteDao.juger(garde(MEF, "Objet : Fourniture d'articles de bureau", "Contrat-cadre"), BUREAU,
                List.of(), List.of(), Set.of("B01-AC-13", FormulairesCandidat.CLE_TYPE_MARCHE));
        assertThat(etat(mode, IdentiteDao.MODE)).isEqualTo(IdentiteDao.CONTREDIT);
        assertThat(etat(mode, IdentiteDao.FORME)).isEqualTo(IdentiteDao.CONTREDIT);

        IdentiteDao.Plan alloti = new IdentiteDao.Plan("Fourniture de matériels informatiques", List.of(MEF), List.of(), "Appel d'offres ouvert",
                null, 2, List.of("Ordinateurs portables", "Imprimantes laser"));
        ImportDaoResult.Identite trop = IdentiteDao.juger(garde(MEF, "Appel d'offres ouvert", "Objet : Fourniture de matériels informatiques",
                "LOT N°1 : ordinateurs portables", "LOT N°2 : imprimantes laser", "LOT N°3 : scanners"), alloti, List.of(), List.of(), Set.of());
        assertThat(etat(trop, IdentiteDao.LOTS)).isEqualTo(IdentiteDao.CONTREDIT);
        assertThat(trop.motif()).contains("3 lots, le plan 2");

        ImportDaoResult.Identite juste = IdentiteDao.juger(garde(MEF, "Appel d'offres ouvert", "Objet : Fourniture de matériels informatiques",
                "LOT N°1 : ordinateurs portables", "LOT N°2 : imprimantes laser"), alloti, List.of(), List.of(), Set.of());
        assertThat(juste.verdict()).isEqualTo(IdentiteDao.CONFORME);
    }

    @Test
    @DisplayName("Les mots collés par l'extraction (« FOURNITUREET », « informatiquesrépartis ») et l'objet coupé sur deux paragraphes se "
            + "retrouvent")
    void extractionImparfaite() {
        IdentiteDao.Plan plan = new IdentiteDao.Plan("Fourniture et livraison des matériels informatiques", List.of(MEF), List.of(),
                "Appel d'offres ouvert", null, 1, List.of());
        ImportDaoResult.Identite v = IdentiteDao.juger(garde(MEF, "DOSSIER D'APPELD'OFFRES OUVERT", "Objet :",
                "FOURNITUREET LIVRAISON DES MATERIELS", "informatiquesrépartis en cinq lots"), plan, List.of(), List.of(), Set.of());
        assertThat(v.verdict()).isEqualTo(IdentiteDao.CONFORME);
        assertThat(IdentiteDao.egal("FOURNITURE", "FOURNITUREET")).isTrue();
        assertThat(IdentiteDao.egal("ADMINISTRATIF", "ADMINISTRATIVE")).isTrue();
        assertThat(IdentiteDao.egal("BUREAU", "BUREAUX")).isTrue();
        assertThat(IdentiteDao.egal("LOT", "LOTISSEMENT")).isFalse();
    }

    @Test
    @DisplayName("B4 : la marque posée dans le .docx et le PDF se relit ; un fichier sans marque n'en porte pas")
    void marque() throws Exception {
        byte[] docx;
        try (XWPFDocument doc = new XWPFDocument(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Objet : Fourniture d'articles de bureau");
            doc.write(out);
            docx = out.toByteArray();
        }
        MarqueDao.Marque m = new MarqueDao.Marque(303337, 12, 3);
        assertThat(MarqueDao.lire("DAO.docx", MarqueDao.marquerDocx(docx, m))).contains(m);
        assertThat(MarqueDao.lire("DAO.docx", docx)).isEmpty();
        byte[] pdf;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(out);
            pdf = out.toByteArray();
        }
        assertThat(MarqueDao.lire("DAO.pdf", MarqueDao.marquerPdf(pdf, m))).contains(m);
        assertThat(MarqueDao.lire("DAO.pdf", "pas un PDF".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    @DisplayName("B3 : DAO_PERIME si la date limite lue est passée ou antérieure à l'exercice ; rien sinon, ni sans date lue")
    void perime() {
        LocalDateTime maintenant = LocalDateTime.of(2026, 10, 17, 9, 0);
        List<ImportDaoResult.Proposition> passee = List.of(new ImportDaoResult.Proposition("B04-CP-02", null, "2026-03-26T13:00", "26 mars",
                "haute", "", null, List.of(), "modele"));
        assertThat(ImportDaoService.perime(passee, Map.of("B01-AC-09", "2026"), maintenant)).startsWith("DAO_PERIME : ").contains("26/03/2026");
        List<ImportDaoResult.Proposition> avantExercice = List.of(new ImportDaoResult.Proposition("B04-CP-02", null, "2027-01-10T10:00", "",
                "haute", "", null, List.of(), "modele"));
        assertThat(ImportDaoService.perime(avantExercice, Map.of("B01-AC-09", "2028"), maintenant)).contains("antérieure à l'exercice");
        List<ImportDaoResult.Proposition> aVenir = List.of(new ImportDaoResult.Proposition("B04-CP-02", null, "2027-02-01T10:00", "", "haute",
                "", null, List.of(), "modele"));
        assertThat(ImportDaoService.perime(aVenir, Map.of("B01-AC-09", "2027"), maintenant)).isNull();
        assertThat(ImportDaoService.perime(List.of(), Map.of(), maintenant)).isNull();
    }
}
