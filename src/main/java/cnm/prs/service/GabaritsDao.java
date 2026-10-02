package cnm.prs.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import cnm.prs.dto.GabaritDto;

/**
 * ⚠️ 2026-10-02 (demande front « gabarits », question Q1 de la recette du DAO du MEN ; accord du pilote) — pour chaque
 * champ, les <strong>phrases des modèles qui le citent</strong> : un champ texte s'insère souvent au milieu d'une phrase,
 * et la PRMP qui ne la voit pas écrit une phrase complète, que le document produit double.
 *
 * <p>Relevé <strong>une fois</strong>, au premier appel, sur les modèles déjà chargés par {@link ModelesDao} : chaque
 * paragraphe (et chaque paragraphe d'une cellule de tableau) qui contient un jeton de champ {@code {{CODE}}} ou
 * {@code {{CODE.suffixe}}}. Règles du contrat :</p>
 * <ol>
 *   <li>périmètre : les modèles de la catégorie et de la forme demandées (couvertures, avis, lettre d'invitation) ;
 *       sans filtre, tous ;</li>
 *   <li>un paragraphe fait du seul jeton ({@code avant} et {@code apres} vides) n'est pas servi ;</li>
 *   <li>deux occurrences de mêmes document, avant, après et suffixe n'en font qu'une ;</li>
 *   <li>ordre : celui des documents (DPAO, DPAC, DPIC, CCAP, CPS, AE, AVIS, LETTRE), puis celui du modèle ;</li>
 *   <li>un paragraphe sous condition est servi comme les autres : le gabarit décrit le modèle, pas la fiche.</li>
 * </ol>
 */
@Component
public class GabaritsDao {

    /** L'ordre des documents dans la réponse. */
    static final List<String> ORDRE_DOCUMENTS = List.of("DPAO", "DPAC", "DPIC", "CCAP", "CPS", "AE", "AVIS", "LETTRE");
    private static final Pattern JETON = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Pattern CODE_CHAMP = Pattern.compile("B\\d{2}-[A-Z0-9]{1,6}-\\d{2}");
    private static final String TROU = "___";

    /** Une citation d'un champ dans un modèle. */
    record Citation(String code, GabaritDto gabarit) {
    }

    /** Un modèle du périmètre : son sigle, sa forme ({@code null} : toutes), sa catégorie. */
    record Portee(String sigle, String typeMarche, String categorie) {
    }

    private final ModelesDao modeles;
    private volatile Map<String, List<Citation>> parSigle;

    public GabaritsDao(ModelesDao modeles) {
        this.modeles = modeles;
    }

    /** Les gabarits d'un champ pour une forme et une catégorie ({@code null} : sans filtre) ; liste vide s'il n'est cité nulle part. */
    public List<GabaritDto> gabarits(String code, String typeMarche, String categorie) {
        Map<String, List<Citation>> index = index();
        Set<GabaritDto> vus = new LinkedHashSet<>();
        for (String sigle : sigles(typeMarche, categorie)) {
            for (Citation c : index.getOrDefault(sigle, List.of())) {
                if (c.code().equals(code)) {
                    vus.add(c.gabarit());
                }
            }
        }
        List<GabaritDto> out = new ArrayList<>(vus);
        out.sort(Comparator.comparingInt(g -> ORDRE_DOCUMENTS.indexOf(g.document())));   // tri stable : l'ordre du modèle reste
        return out;
    }

    /** Les sigles du périmètre, dans l'ordre des couvertures, puis l'avis et la lettre. */
    List<String> sigles(String typeMarche, String categorie) {
        List<Portee> portees = new ArrayList<>();
        ModelesDao.COUVERTURES.forEach(c -> portees.add(new Portee(c.sigle(), c.typeMarche(), c.categorie())));
        ModelesDao.AVIS.forEach((cat, sigle) -> portees.add(new Portee(sigle, null, cat)));
        ModelesDao.LETTRES.forEach((cat, sigle) -> portees.add(new Portee(sigle, null, cat)));
        Set<String> sigles = new LinkedHashSet<>();
        for (Portee p : portees) {
            if ((typeMarche == null || p.typeMarche() == null || p.typeMarche().equals(typeMarche))
                    && (categorie == null || p.categorie().equals(categorie))) {
                sigles.add(p.sigle());
            }
        }
        return new ArrayList<>(sigles);
    }

    private Map<String, List<Citation>> index() {
        Map<String, List<Citation>> i = parSigle;
        if (i == null) {
            synchronized (this) {
                if (parSigle == null) {
                    Map<String, List<Citation>> m = new LinkedHashMap<>();
                    modeles.modeles().forEach((sigle, modele) -> m.put(sigle, citations(sigle, modele)));
                    parSigle = m;
                }
                i = parSigle;
            }
        }
        return i;
    }

    /** Les citations d'un modèle, dans l'ordre de ses paragraphes (cellules de tableau comprises). */
    static List<Citation> citations(String sigle, FichierCommande.Modele modele) {
        String document = sigle.contains("-") ? sigle.substring(0, sigle.indexOf('-')) : sigle;
        List<Citation> out = new ArrayList<>();
        for (DocumentLibre.Element e : modele.elements()) {
            if (e instanceof DocumentLibre.Paragraphe p) {
                citer(document, p.texte(), out);
            } else if (e instanceof DocumentLibre.Tableau t) {
                for (List<List<String>> ligne : t.lignes()) {
                    for (List<String> cellule : ligne) {
                        for (String paragraphe : cellule) {
                            citer(document, paragraphe, out);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static void citer(String document, String texte, List<Citation> out) {
        if (texte == null || !texte.contains("{{")) {
            return;
        }
        List<int[]> positions = new ArrayList<>();
        List<String> noms = new ArrayList<>();
        Matcher m = JETON.matcher(texte);
        while (m.find()) {
            positions.add(new int[] { m.start(), m.end() });
            noms.add(m.group(1));
        }
        for (int k = 0; k < noms.size(); k++) {
            String nom = noms.get(k);
            int point = nom.indexOf('.');
            String code = point < 0 ? nom : nom.substring(0, point);
            if (!CODE_CHAMP.matcher(code).matches()) {
                continue;
            }
            String avant = nettoyer(texte, positions, noms, 0, positions.get(k)[0]);
            String apres = nettoyer(texte, positions, noms, positions.get(k)[1], texte.length());
            if (avant.isBlank() && apres.isBlank()) {
                continue;   // règle 2 : un paragraphe fait du seul jeton n'apprend rien
            }
            out.add(new Citation(code, new GabaritDto(document, avant, apres, point < 0 ? null : nom.substring(point + 1))));
        }
    }

    /** Le texte entre deux positions : les jetons remplacés par « ___ », les balises SI / FINSI retirées. */
    private static String nettoyer(String texte, List<int[]> positions, List<String> noms, int debut, int fin) {
        StringBuilder sb = new StringBuilder();
        int i = debut;
        for (int k = 0; k < positions.size(); k++) {
            int[] p = positions.get(k);
            if (p[1] <= debut || p[0] >= fin) {
                continue;
            }
            sb.append(texte, i, p[0]);
            String nom = noms.get(k);
            if (!nom.startsWith("SI:") && !nom.startsWith("FINSI:")) {
                sb.append(TROU);
            }
            i = p[1];
        }
        sb.append(texte, i, fin);
        return sb.toString();
    }
}
