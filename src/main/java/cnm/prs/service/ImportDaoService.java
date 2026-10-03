package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.dto.ImportDaoResult;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.enums.SourceChampFiche;
import cnm.prs.enums.TypeChampFiche;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ImportRefuseException;
import cnm.prs.repository.ChampFicheMarcheRepository;

/**
 * ⚠️ <strong>Import du DAO</strong> (demande front du 2026-09-28, §B1 et §B3 ; ADR-0012) — lit un DAO Word et
 * <strong>propose</strong> de quoi pré-remplir la fiche ; rien n'est écrit (l'écriture est {@code PUT …/import/appliquer},
 * {@link FicheMarcheService#appliquerImport}). La fiche reste le formulaire et la seule source de vérité.
 *
 * <ul>
 *   <li><strong>Le fichier</strong> : {@code .docx} seulement, sans macros ({@code .docm} refusé), lu en mémoire par POI
 *       — qui n'exécute rien et garde contre les archives piégées (ratio de décompression) — puis oublié : ni stocké, ni
 *       journalisé à la lecture. Autre chose → 415 {@code FORMAT_NON_SUPPORTE}.</li>
 *   <li><strong>Les modèles</strong> : ceux du lot D que la fiche produirait ({@link ModelesDao#couvertures}) ; aucun →
 *       422 {@code MODELE_ABSENT}. Chacun cherche sa partie dans le même fichier (avis, DPAC, AE à la suite).</li>
 *   <li><strong>La lecture</strong> : {@link LectureDao}, portage de la spécification du front.</li>
 *   <li><strong>Le filtre</strong> : jamais proposés — un champ repris du plan (une valeur lue qui en diffère devient une
 *       <em>divergence</em>, le plan fait foi), un champ dérivé du cadrage (un reflet devient une réponse de cadrage), un
 *       champ calculé (remise électronique), une pièce. Chaque valeur passe par la validation de la saisie
 *       ({@code normaliser}) et par la condition d'affichage du champ, évaluée sur le cadrage de la fiche complété des
 *       réponses déduites : un refus devient une <em>anomalie</em> de la proposition.</li>
 * </ul>
 */
@Service
public class ImportDaoService {

    static final String MESSAGE_FORMAT = "Seul un fichier Word (.docx) ou PDF (.pdf) peut être importé.";
    /** Seuil de l'avertissement « hors gabarit » : part des unités d'un modèle reconnues dans le document. */
    static final int SEUIL_GABARIT = 30;
    private static final Pattern VBA = Pattern.compile("(?i)/word/vbaProject\\.bin");

    private final FicheMarcheService fiches;
    private final ModelesDao modeles;
    private final ChampFicheMarcheRepository champRepository;

    public ImportDaoService(FicheMarcheService fiches, ModelesDao modeles, ChampFicheMarcheRepository champRepository) {
        this.fiches = fiches;
        this.modeles = modeles;
        this.champRepository = champRepository;
    }

    /** §B1 — lit le fichier pour la fiche {@code idDmc}, gardes de la fiche d'abord, puis le format. */
    public ImportDaoResult lire(Long idDmc, String nomFichier, byte[] contenu) {
        FicheMarcheDto fiche = fiches.etatImportable(idDmc);
        List<ModelesDao.Couverture> couvertures = ModelesDao.couvertures(fiche.getTypeMarche(), fiche.getCategorie());
        if (couvertures.isEmpty()) {
            throw ImportRefuseException.modeleAbsent();
        }
        // ⚠️ Lot D4 (2026-09-30, §B6.3) — les paragraphes tels qu'écrits ; la lecture travaille sur leur forme normalisée.
        List<String> origines = paragraphesDOrigine(nomFichier, contenu);
        List<String> paragraphes = origines.stream().map(LectureDao::norm).toList();
        return lire(fiche, nomFichier, empreinte(contenu), paragraphes, origines, couvertures);
    }

    // ------------------------------------------------------------------ le fichier

    /** SHA-256 en hexadécimal minuscule. */
    static String empreinte(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Les unités de lecture d'un {@code .docx} ou, ⚠️ depuis le lot D2 (2026-09-29, §B5 règle 4), d'un {@code .pdf}
     * « texte » ({@link LecturePdf}). Un PDF sans texte (scanné) → 422 {@code DOCUMENT_SANS_TEXTE}.
     */
    public static List<String> paragraphes(String nomFichier, byte[] contenu) {
        return paragraphesDOrigine(nomFichier, contenu).stream().map(LectureDao::norm).toList();
    }

    /** ⚠️ Lot D4 (2026-09-30, §B6.3) — les mêmes unités TELLES QU'ÉCRITES (non normalisées), dans le même ordre. */
    public static List<String> paragraphesDOrigine(String nomFichier, byte[] contenu) {
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase(Locale.ROOT);
        if (!nom.endsWith(".docx") && !nom.endsWith(".pdf")) {
            throw ImportRefuseException.format(MESSAGE_FORMAT);
        }
        if (contenu == null || contenu.length == 0) {
            throw ImportRefuseException.format(MESSAGE_FORMAT + " Le fichier reçu est vide.");
        }
        if (nom.endsWith(".pdf")) {
            List<String> pdf;
            try {
                pdf = LecturePdf.paragraphesDOrigine(contenu);
            } catch (Exception | LinkageError e) {
                // pas un PDF, PDF chiffré ou endommagé
                throw ImportRefuseException.format(MESSAGE_FORMAT + " Ce fichier ne se lit pas comme un document PDF.");
            }
            if (pdf.isEmpty()) {
                throw ImportRefuseException.sansTexte();
            }
            return pdf;
        }
        return paragraphesDocxOrigine(contenu);
    }

    /**
     * Le texte d'un {@code .docx} dans l'ordre du document — paragraphes et tableaux entrelacés, une ligne de tableau par
     * ligne, cellules séparées par une tabulation, ⚠️ lot D2 : les paragraphes d'une cellule séparés (RS), une tabulation
     * dans un paragraphe rendue par une espace — puis découpé en unités ({@link LectureDao#unitesDocument}). Même
     * extraction que {@code LireDocx --paragraphes} du front : run par run, le trait d'union insécable rendu, l'appel de
     * note {@code [note:n]} et la note sur la ligne suivante.
     */
    static List<String> paragraphesDocx(byte[] contenu) {
        return paragraphesDocxOrigine(contenu).stream().map(LectureDao::norm).toList();
    }

    /** ⚠️ Lot D4 (§B6.3) — les unités du .docx telles qu'écrites. */
    static List<String> paragraphesDocxOrigine(byte[] contenu) {
        List<String> lignes = new ArrayList<>();
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(contenu))) {
            if (!pkg.getPartsByName(VBA).isEmpty()) {
                throw ImportRefuseException.format(MESSAGE_FORMAT + " Un document à macros n'est pas admis.");
            }
            try (XWPFDocument doc = new XWPFDocument(pkg)) {
                if (!XWPFRelation.DOCUMENT.getContentType().equals(doc.getPackagePart().getContentType())) {
                    throw ImportRefuseException.format(MESSAGE_FORMAT + " Un document à macros ou un modèle n'est pas admis.");
                }
                List<String> notes = new ArrayList<>();
                for (IBodyElement e : doc.getBodyElements()) {
                    if (e instanceof XWPFParagraph p) {
                        lignes.add(texte(doc, p, notes));
                        lignes.addAll(notes);
                        notes.clear();
                    } else if (e instanceof XWPFTable t) {
                        for (XWPFTableRow r : t.getRows()) {
                            StringBuilder sb = new StringBuilder();
                            int rang = 0;
                            for (XWPFTableCell c : r.getTableCells()) {
                                if (rang++ > 0) {
                                    sb.append('\t');
                                }
                                StringBuilder cellule = new StringBuilder();
                                for (XWPFParagraph p : c.getParagraphs()) {
                                    if (cellule.length() > 0) {
                                        cellule.append('\u001E');   // lot D2 : un paragraphe de cellule est une unité
                                    }
                                    cellule.append(texte(doc, p, notes));
                                }
                                sb.append(cellule);
                            }
                            lignes.add(sb.toString());
                            lignes.addAll(notes);
                            notes.clear();
                        }
                    }
                }
            }
        } catch (ImportRefuseException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            // archive illisible, piégée (ratio de décompression de POI), pas un paquet Office…
            throw ImportRefuseException.format(MESSAGE_FORMAT + " Ce fichier ne se lit pas comme un document Word.");
        }
        return LectureDao.unitesDocumentOrigine(lignes);
    }

    private static String texte(XWPFDocument doc, XWPFParagraph p, List<String> notes) {
        StringBuilder sb = new StringBuilder();
        for (IRunElement element : p.getIRuns()) {
            if (!(element instanceof XWPFRun r)) {
                continue;
            }
            boolean capitales = r.isCapitalized() || r.isSmallCaps();
            NodeList enfants = r.getCTR().getDomNode().getChildNodes();
            for (int i = 0; i < enfants.getLength(); i++) {
                Node n = enfants.item(i);
                String nom = n.getLocalName() == null ? "" : n.getLocalName();
                switch (nom) {
                    case "t" -> sb.append(capitales ? texteDe(n).toUpperCase(Locale.FRENCH) : texteDe(n));
                    case "tab" -> sb.append(' ');   // lot D2 : une tabulation dans un paragraphe n'y coupe rien
                    case "br", "cr" -> sb.append('\n');
                    case "noBreakHyphen" -> sb.append('‑');
                    case "footnoteReference" -> {
                        String id = attribut(n, "id");
                        sb.append("[note:").append(id).append(']');
                        XWPFFootnote note = id.matches("-?\\d+") ? doc.getFootnoteByID(Integer.parseInt(id)) : null;
                        if (note != null) {
                            StringBuilder t = new StringBuilder();
                            for (XWPFParagraph q : note.getParagraphs()) {
                                if (t.length() > 0) {
                                    t.append(' ');
                                }
                                t.append(texte(doc, q, new ArrayList<>()));
                            }
                            notes.add("[note " + id + "] " + t.toString().trim());
                        }
                    }
                    default -> {
                        // rPr, lastRenderedPageBreak, softHyphen… : rien à lire
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String texteDe(Node n) {
        StringBuilder sb = new StringBuilder();
        NodeList enfants = n.getChildNodes();
        for (int i = 0; i < enfants.getLength(); i++) {
            Node e = enfants.item(i);
            if (e.getNodeType() == Node.TEXT_NODE || e.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(e.getNodeValue());
            }
        }
        return sb.toString();
    }

    private static String attribut(Node n, String local) {
        NamedNodeMap attributs = n.getAttributes();
        for (int i = 0; attributs != null && i < attributs.getLength(); i++) {
            Node a = attributs.item(i);
            if (local.equals(a.getLocalName())) {
                return a.getNodeValue();
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ la lecture et le filtre

    private ImportDaoResult lire(FicheMarcheDto fiche, String nomFichier, String empreinte, List<String> paragraphes,
            List<String> origines, List<ModelesDao.Couverture> couvertures) {
        Map<String, ChampFicheMarche> champs = new LinkedHashMap<>();
        champRepository.findAllByOrderByCodeRubriqueAscRangAsc().forEach(c -> champs.putIfAbsent(c.getCode(), c));
        String typeMarche = fiche.getTypeMarche();
        String categorie = fiche.getCategorie() != null ? fiche.getCategorie() : "FOURNITURES_SERVICES";

        List<ImportDaoResult.Modele> lus = new ArrayList<>();
        List<String> avertissements = new ArrayList<>();
        List<LectureDao.Ambigu> ambigus = new ArrayList<>();
        List<ImportDaoResult.Conflit> conflits = new ArrayList<>();
        Map<String, LectureDao.Proposition> propositions = new LinkedHashMap<>();
        Map<String, LectureDao.Reponse> reponses = new LinkedHashMap<>();
        Set<String> attendus = new LinkedHashSet<>();
        Set<String> enConflit = new LinkedHashSet<>();

        java.util.function.Function<String, LectureDao.InfoChamp> infos = code -> {
            ChampFicheMarche ch = champs.get(code);
            return ch == null ? null : new LectureDao.InfoChamp(ch.getType(), ch.getSource(), ch.getCleCadrage(),
                    cnm.prs.entity.ChampFicheMarche.liste(ch.getOptions()));   // ⚠️ 2026-10-01 : réponses d'un terme « contient »
        };
        // ⚠️ 2026-10-03 (lecture par clause) — les valeurs de la passe par clause, mises de côté : elles ne s'ajoutent qu'après
        // la lecture de TOUS les modèles, pour un champ qu'aucun n'a proposé (jamais à la place d'une valeur lue dans un
        // modèle, ni en conflit avec elle) ; et les passages de listes.
        List<LectureDao.Proposition> parClause = new ArrayList<>();
        List<ImportDaoResult.Passage> passages = new ArrayList<>();
        for (ModelesDao.Couverture c : couvertures) {
            LectureDao.Resultat lu = LectureDao.lire(c.sigle(), modeles.modele(c.sigle()), paragraphes, infos, origines);
            LectureDao.Resultat r = LectureDao.completerParClause(lu, paragraphes, c.sigle(), infos);
            r.propositions().stream().filter(p -> LectureDao.SOURCE_CLAUSE.equals(p.source())).forEach(parClause::add);
            r.passages().forEach(p -> passages.add(new ImportDaoResult.Passage(p.liste(), p.texte(), p.paragraphe())));
            r = lu;
            lus.add(new ImportDaoResult.Modele(r.sigle(), r.unites(), r.reconnues()));
            int part = r.unites() == 0 ? 0 : (int) Math.round(100.0 * r.reconnues() / r.unites());
            if (part < SEUIL_GABARIT) {
                avertissements.add(r.sigle() + " : peu de texte du modèle reconnu (" + part
                        + " %) : ce document ne suit pas le document type");
            }
            ambigus.addAll(r.ambigus());
            r.conflits().forEach(k -> {
                conflits.add(new ImportDaoResult.Conflit(k.code(), k.valeurs()));
                enConflit.add(k.code());
            });
            attendus.addAll(r.nonTrouves());
            r.propositions().forEach(p -> attendus.add(p.code().split("#", -1)[0]));
            // un même champ lu par deux modèles : la même valeur garde la meilleure confiance, deux valeurs sont un conflit
            for (LectureDao.Proposition p : r.propositions()) {
                fusionner(propositions, p, conflits, enConflit);
            }
            for (LectureDao.Reponse rc : r.reponsesChamps()) {
                fusionner(propositions, new LectureDao.Proposition(rc.cle(), rc.valeur(), rc.valeur(),
                        LectureDao.Confiance.MOYENNE, "rédaction retenue (section " + rc.section() + " de " + r.sigle() + ")"),
                        conflits, enConflit);
            }
            for (LectureDao.Reponse rc : r.cadrage()) {
                LectureDao.Reponse deja = reponses.get(rc.cle());
                if (deja == null) {
                    reponses.put(rc.cle(), rc);
                } else if (!deja.valeur().equals(rc.valeur())) {
                    conflits.add(new ImportDaoResult.Conflit(rc.cle(), List.of(deja.valeur(), rc.valeur())));
                    enConflit.add(rc.cle());
                }
            }
        }

        Set<String> lusParModele = new LinkedHashSet<>();
        propositions.values().forEach(p -> lusParModele.add(p.code().split("#", -1)[0]));
        for (LectureDao.Proposition p : parClause) {
            String nu = p.code().split("#", -1)[0];
            if (!lusParModele.contains(nu) && !enConflit.contains(nu)) {
                fusionner(propositions, p, conflits, enConflit);
                attendus.add(nu);
            }
        }

        // Le cadrage : chaque réponse validée comme par PUT …/cadrage ; refusée, elle devient un avertissement.
        Map<String, Object> cadrageFiche = fiche.getCadrage() == null ? Map.of() : fiche.getCadrage();
        Map<String, Object> cadrageLu = new LinkedHashMap<>(cadrageFiche);
        List<ImportDaoResult.Cadrage> cadrage = new ArrayList<>();
        List<ImportDaoResult.Divergence> divergences = new ArrayList<>();
        for (LectureDao.Reponse rc : reponses.values()) {
            if (enConflit.contains(rc.cle())) {
                continue;
            }
            // ⚠️ Lot D2 (2026-09-29) — la forme et la catégorie se lisent au plan, pas au cadrage : une rédaction qui les dit
            // autrement est une divergence (le plan fait foi), jamais une réponse.
            if (FormulairesCandidat.CLE_TYPE_MARCHE.equals(rc.cle()) || FormulairesCandidat.CLE_CATEGORIE.equals(rc.cle())) {
                String plan = FormulairesCandidat.CLE_TYPE_MARCHE.equals(rc.cle()) ? typeMarche : categorie;
                if (!rc.valeur().equalsIgnoreCase(plan)) {
                    divergences.add(new ImportDaoResult.Divergence(rc.cle(), rc.valeur(), plan));
                }
                continue;
            }
            try {
                Map<String, Object> propre = fiches.cadrageValide(Map.of(rc.cle(), rc.valeur()), fiche.getCategorie(), typeMarche);
                Object valeur = propre.get(rc.cle());
                if (valeur == null) {
                    continue;
                }
                cadrage.add(new ImportDaoResult.Cadrage(rc.cle(), valeur, rc.section(), cadrageFiche.get(rc.cle())));
                cadrageLu.put(rc.cle(), valeur);
            } catch (ChampsInvalidesException e) {
                avertissements.add("réponse de cadrage « " + rc.cle() + " = " + rc.valeur() + " » écartée : "
                        + e.getErreurs().get(0).message());
            }
        }

        // Les valeurs : filtre du référentiel, puis validation de la saisie.
        Map<String, String> valeursFiche = fiche.getValeurs() == null ? Map.of() : fiche.getValeurs();
        Map<String, String> valeursPlan = fiche.getValeursPpm() == null ? Map.of() : fiche.getValeursPpm();
        boolean alloti = fiche.getNbLots() != null && LotsFiche.alloti(fiche.getNbLots());
        List<ImportDaoResult.Proposition> sortie = new ArrayList<>();
        Set<String> proposables = new LinkedHashSet<>();
        for (String code : attendus) {
            ChampFicheMarche c = champs.get(code);
            if (proposable(c, typeMarche, categorie)) {
                proposables.add(code);
            }
        }
        for (LectureDao.Proposition p : propositions.values()) {
            if (enConflit.contains(p.code())) {
                continue;
            }
            // ⚠️ Lot D2 (2026-09-29) — une valeur énumérée par lot ({{CODE.parLot}}) arrive sous CODE#n : le champ est CODE,
            // le lot n.
            int diese = p.code().indexOf(LotsFiche.SEPARATEUR);
            String code = diese < 0 ? p.code() : p.code().substring(0, diese);
            Integer lot = null;
            if (diese >= 0) {
                try {
                    lot = Integer.valueOf(p.code().substring(diese + 1));
                } catch (NumberFormatException e) {
                    continue;   // « Lot n° 99999999999 » : pas un lot
                }
            }
            ChampFicheMarche c = champs.get(code);
            if (c == null || !Boolean.TRUE.equals(c.getActif())) {
                continue;
            }
            if (SourceChampFiche.PPM.name().equals(c.getSource())) {
                String plan = valeursPlan.get(code);
                if (plan == null || !LectureDao.norm(plan).equalsIgnoreCase(LectureDao.norm(p.valeur()))) {
                    divergences.add(new ImportDaoResult.Divergence(code, p.brut(), plan));
                }
                continue;
            }
            if (!proposable(c, typeMarche, categorie)) {
                continue;
            }
            List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
            String normalisee = FicheMarcheService.normaliser(c, p.valeur(), erreurs, p.code());
            List<String> anomalies = new ArrayList<>(erreurs.stream().map(ErrorResponse.FieldError::message).toList());
            if (c.getCondition() != null && !c.getCondition().isBlank() && !ConditionCadrage.vraie(c.getCondition(), cadrageLu)) {
                anomalies.add("« " + c.getLibelle() + " » ne s'applique pas avec ce cadrage (condition : " + c.getCondition() + ").");
            }
            boolean parLot = alloti && Boolean.TRUE.equals(c.getParLot());
            if (parLot && lot == null) {
                anomalies.add("« " + c.getLibelle() + " » se saisit par lot : le document ne dit pas de quel lot il s'agit.");
            } else if (lot != null && !parLot) {
                anomalies.add("« " + c.getLibelle() + " » est lu pour le lot " + lot + ", mais la ligne ne se saisit pas par lot "
                        + "pour ce champ.");
            } else if (lot != null && lot > fiche.getNbLots()) {
                anomalies.add("« " + c.getLibelle() + " » est lu pour le lot " + lot + ", hors du plan (" + fiche.getNbLots()
                        + " lots).");
            }
            sortie.add(new ImportDaoResult.Proposition(code, lot, normalisee != null ? normalisee : p.valeur(), p.brut(),
                    p.confiance().libelle(), p.extrait(), valeursFiche.get(p.code()), anomalies, p.source()));
        }
        Set<String> proposes = new LinkedHashSet<>();
        sortie.forEach(p -> proposes.add(p.code()));
        List<String> nonTrouves = proposables.stream().filter(c -> !proposes.contains(c) && !enConflit.contains(c)).toList();
        List<ImportDaoResult.Ambigu> ambigusSortie = ambigus.stream()
                .map(a -> new ImportDaoResult.Ambigu(a.candidats(), a.texte())).toList();
        return new ImportDaoResult(nomFichier, empreinte, lus, cadrage, sortie, ambigusSortie, divergences, conflits,
                nonTrouves, avertissements, passages);
    }

    /** Un champ que l'import peut proposer : saisi, actif, de la forme et de la catégorie, ni pièce, ni calculé. */
    private static boolean proposable(ChampFicheMarche c, String typeMarche, String categorie) {
        return c != null && Boolean.TRUE.equals(c.getActif()) && SourceChampFiche.SAISIE.name().equals(c.getSource())
                && !TypeChampFiche.PIECE.name().equals(c.getType())
                && !RemiseElectronique.TOUJOURS_CALCULES.contains(c.getCode())
                && !RemiseElectronique.CALCULES_SI_VIDES.contains(c.getCode())
                && c.pourTypeMarche(typeMarche) && c.pourCategorie(categorie);
    }

    private static void fusionner(Map<String, LectureDao.Proposition> propositions, LectureDao.Proposition p,
            List<ImportDaoResult.Conflit> conflits, Set<String> enConflit) {
        LectureDao.Proposition deja = propositions.get(p.code());
        if (deja == null) {
            propositions.put(p.code(), p);
        } else if (!Objects.equals(deja.valeur(), p.valeur())) {
            if (enConflit.add(p.code())) {
                conflits.add(new ImportDaoResult.Conflit(p.code(), List.of(deja.valeur(), p.valeur())));
            }
        } else if (p.confiance().rang > deja.confiance().rang) {
            propositions.put(p.code(), p);
        }
    }
}
