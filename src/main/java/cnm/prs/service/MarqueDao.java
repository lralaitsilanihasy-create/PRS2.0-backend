package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.openxml4j.opc.OPCPackage;

/**
 * ⚠️ 2026-10-17 (demande front « identite-dao-importe », §B4) — la marque d'un DAO complet produit par l'application : la ligne du plan,
 * la fiche et sa version, posées dans les mots-clés du document (propriétés du {@code .docx}, informations du PDF) après l'assemblage
 * par Word. Réimporté, le DAO est jugé d'emblée : conforme sur sa ligne, étranger ailleurs. La marque ne remplace pas la recherche
 * ({@link IdentiteDao}) : la plupart des DAO importés viennent d'ailleurs.
 */
public final class MarqueDao {

    public static final String PREFIXE = "PRS20-DAO";
    private static final Pattern MARQUE = Pattern.compile(PREFIXE + " ligne=(\\d+) fiche=(\\d+) version=(\\d+)");

    public record Marque(int idDetail, int idFiche, int version) {

        String texte() {
            return PREFIXE + " ligne=" + idDetail + " fiche=" + idFiche + " version=" + version;
        }
    }

    private MarqueDao() {
    }

    /** Le {@code .docx} marqué : seule la partie des propriétés change (paquet OPC, sans réécrire le document). */
    public static byte[] marquerDocx(byte[] docx, Marque marque) throws IOException {
        // Un paquet ouvert sur un flux ne se ferme pas (close() voudrait le réécrire) : il s'abandonne par revert().
        try {
            OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(docx));
            try {
                pkg.getPackageProperties().setKeywordsProperty(marque.texte());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                pkg.save(out);
                return out.toByteArray();
            } finally {
                pkg.revert();
            }
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException(e);
        }
    }

    public static byte[] marquerPdf(byte[] pdf, Marque marque) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.getDocumentInformation().setKeywords(marque.texte());
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** La marque du fichier, s'il en porte une ; un fichier illisible n'en porte pas. */
    public static Optional<Marque> lire(String nomFichier, byte[] contenu) {
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase(Locale.ROOT);
        String mots = null;
        try {
            if (nom.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(contenu)) {
                    mots = doc.getDocumentInformation().getKeywords();
                }
            } else if (nom.endsWith(".docx")) {
                OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(contenu));
                try {
                    mots = pkg.getPackageProperties().getKeywordsProperty().orElse(null);
                } finally {
                    pkg.revert();
                }
            }
        } catch (Exception | LinkageError e) {
            return Optional.empty();
        }
        Matcher m = MARQUE.matcher(mots == null ? "" : mots);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Marque(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
