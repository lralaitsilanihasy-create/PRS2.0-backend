package cnm.prs.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.SaisiePpmImportResult;
import cnm.prs.dto.SaisiePpmImportResult.MarcheImport;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.exception.BadRequestException;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.NatureRepository;

/**
 * Parsing <strong>read-only</strong> d'un PPM PDF pour pré-remplir le formulaire de saisie
 * ({@code POST /api/saisies/ppm/import}). N'écrit rien en base : extrait le texte via PDFBox, résout au
 * mieux l'en-tête et les référentiels (sinon libellé + avertissement), et renvoie un
 * {@link SaisiePpmImportResult}. Un PDF illisible → {@link BadRequestException} (400).
 *
 * <p>⚠️ Le parsing du <strong>tableau des marchés</strong> est un premier jet <em>best-effort</em> : il doit
 * être calibré sur un exemplaire officiel (positions de colonnes / cellules multi-lignes). Tant qu'il ne l'est
 * pas, les lignes extraites sont signalées dans {@code avertissements} et restent à valider par la PRMP.
 */
@Service
public class SaisiePpmImportService {

    private static final Pattern EXERCICE = Pattern.compile("(?i)(?:exercice|gestion|ann[eé]e)\\D{0,15}(20\\d{2})");
    private static final Pattern ANNEE = Pattern.compile("(20\\d{2})");
    private static final Pattern FAIT_LE = Pattern.compile(
            "(?i)fait\\s+[aàâä]\\s+[^,\\n]+?\\s+le\\s+(\\d{1,2})\\s+([a-zàâäéèêëîïôöùûüç]+)\\s+(20\\d{2})");
    private static final Pattern AUTORITE = Pattern.compile("(?i)autorit[eé]\\s+contractante\\s*:?\\s*(.+)");
    private static final Pattern MONTANT = Pattern.compile("(\\d[\\d\\s\\u00a0]{2,}(?:[.,]\\d+)?)");

    private static final Map<String, Integer> MOIS = Map.ofEntries(
            Map.entry("janvier", 1), Map.entry("fevrier", 2), Map.entry("mars", 3), Map.entry("avril", 4),
            Map.entry("mai", 5), Map.entry("juin", 6), Map.entry("juillet", 7), Map.entry("aout", 8),
            Map.entry("septembre", 9), Map.entry("octobre", 10), Map.entry("novembre", 11), Map.entry("decembre", 12));

    private final EntiteContractRepository entiteRepository;
    private final NatureRepository natureRepository;
    private final ModePassationRepository modePassationRepository;

    public SaisiePpmImportService(EntiteContractRepository entiteRepository, NatureRepository natureRepository,
            ModePassationRepository modePassationRepository) {
        this.entiteRepository = entiteRepository;
        this.natureRepository = natureRepository;
        this.modePassationRepository = modePassationRepository;
    }

    public SaisiePpmImportResult importer(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Fichier PDF manquant ou vide.");
        }
        String texte = extraireTexte(fichier);
        List<String> avert = new ArrayList<>();

        Integer exercice = extraireExercice(texte);
        if (exercice == null) {
            avert.add("Exercice non détecté dans le PDF — à saisir.");
        }
        String dateSignature = extraireDateSignature(texte);
        if (dateSignature == null) {
            avert.add("Date de signature non détectée (« Fait à… le… ») — à confirmer.");
        }
        String autorite = extraireAutorite(texte);
        Integer idEntite = resoudreEntite(autorite, avert);

        List<MarcheImport> marches = parserMarches(texte, avert);
        return new SaisiePpmImportResult(exercice, dateSignature, autorite, idEntite, marches, avert);
    }

    /** Extraction texte via PDFBox. Fichier non-PDF / illisible → 400 (pas de données partielles silencieuses). */
    private String extraireTexte(MultipartFile fichier) {
        byte[] bytes;
        try {
            bytes = fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Lecture du fichier impossible : " + e.getMessage());
        }
        if (bytes.length < 5 || !(bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F')) {
            throw new BadRequestException("Le fichier fourni n'est pas un PDF valide.");
        }
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String t = new PDFTextStripper().getText(doc);
            if (t == null || t.isBlank()) {
                throw new BadRequestException("PDF sans texte extractible (document scanné/image ?).");
            }
            return t;
        } catch (IOException e) {
            throw new BadRequestException("PDF illisible ou invalide.");
        }
    }

    private Integer extraireExercice(String texte) {
        Matcher m = EXERCICE.matcher(texte);
        if (m.find()) {
            return Integer.valueOf(m.group(1));
        }
        Matcher a = ANNEE.matcher(texte);
        return a.find() ? Integer.valueOf(a.group(1)) : null;
    }

    /** « Fait à … le 14 avril 2026 » → {@code 2026-04-14} (best-effort, {@code null} si absent/illisible). */
    private String extraireDateSignature(String texte) {
        Matcher m = FAIT_LE.matcher(texte);
        if (!m.find()) {
            return null;
        }
        Integer mois = MOIS.get(sansAccents(m.group(2)).toLowerCase(Locale.FRENCH));
        if (mois == null) {
            return null;
        }
        int jour = Integer.parseInt(m.group(1));
        int annee = Integer.parseInt(m.group(3));
        return String.format("%04d-%02d-%02d", annee, mois, jour);
    }

    private String extraireAutorite(String texte) {
        Matcher m = AUTORITE.matcher(texte);
        if (!m.find()) {
            return null;
        }
        String v = m.group(1).trim();
        int coupe = v.indexOf("  ");
        return coupe > 0 ? v.substring(0, coupe).trim() : v;
    }

    /** Résout l'entité depuis l'autorité contractante (comparaison normalisée « contient »), sinon {@code null} + avertissement. */
    private Integer resoudreEntite(String autorite, List<String> avert) {
        if (autorite == null || autorite.isBlank()) {
            avert.add("Autorité contractante non détectée — la PRMP choisit son entité.");
            return null;
        }
        String cible = normaliser(autorite);
        Integer id = entiteRepository.findAll().stream()
                .filter(e -> e.getLibelleEntite() != null && !e.getLibelleEntite().isBlank())
                .filter(e -> {
                    String lib = normaliser(e.getLibelleEntite());
                    return cible.contains(lib) || lib.contains(cible);
                })
                .map(EntiteContract::getIdEntiteContract)
                .findFirst().orElse(null);
        if (id == null) {
            avert.add("Entité contractante « " + autorite + " » non résolue — à sélectionner dans la liste.");
        }
        return id;
    }

    /**
     * Parsing best-effort des lignes de marché à partir du texte extrait. Non calibré sur le format
     * officiel : détecte les lignes portant un montant et en dérive une désignation. Toujours signalé.
     */
    private List<MarcheImport> parserMarches(String texte, List<String> avert) {
        List<MarcheImport> lignes = new ArrayList<>();
        for (String brute : texte.split("\\r?\\n")) {
            String ligne = brute.trim();
            if (ligne.length() < 8) {
                continue;
            }
            Matcher mm = MONTANT.matcher(ligne);
            if (!mm.find()) {
                continue;
            }
            String designation = ligne.substring(0, mm.start()).trim();
            if (designation.length() < 4 || !designation.matches(".*[A-Za-zÀ-ÿ]{3,}.*")) {
                continue;   // pas une vraie désignation → on évite d'inventer une ligne
            }
            BigDecimal montant = parseMontant(mm.group(1));
            Nature nature = resoudreNature(ligne).orElse(null);
            ModePassation mode = resoudreMode(ligne).orElse(null);
            lignes.add(new MarcheImport(designation, montant, null,
                    nature == null ? null : nature.getIdNature(), nature == null ? null : nature.getLibelle(),
                    mode == null ? null : mode.getIdMode(), mode == null ? null : mode.getLibelle(),
                    null, List.of(), List.of()));
        }
        if (lignes.isEmpty()) {
            avert.add("Aucune ligne de marché détectée automatiquement — parser à calibrer sur le format officiel.");
        } else {
            avert.add(lignes.size() + " ligne(s) de marché extraite(s) en mode best-effort — à valider "
                    + "(parser non encore calibré sur le format officiel du tableau ; bénéficiaires et prévisions non extraits).");
        }
        return lignes;
    }

    private java.util.Optional<Nature> resoudreNature(String ligne) {
        String n = normaliser(ligne);
        return natureRepository.findAll().stream()
                .filter(x -> x.getLibelle() != null && !x.getLibelle().isBlank() && n.contains(normaliser(x.getLibelle())))
                .findFirst();
    }

    private java.util.Optional<ModePassation> resoudreMode(String ligne) {
        String n = normaliser(ligne);
        return modePassationRepository.findAll().stream()
                .filter(x -> x.getLibelle() != null && !x.getLibelle().isBlank() && n.contains(normaliser(x.getLibelle())))
                .findFirst();
    }

    private static BigDecimal parseMontant(String brut) {
        String net = brut.replaceAll("[\\s\\u00a0]", "").replace(',', '.');
        try {
            return new BigDecimal(net);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normaliser(String s) {
        return sansAccents(s).toUpperCase(Locale.FRENCH).replaceAll("[^A-Z0-9]", "");
    }

    private static String sansAccents(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
