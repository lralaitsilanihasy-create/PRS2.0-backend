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

    static final String MESSAGE_FORMAT = "Seul un fichier Word (.docx) peut être importé pour l'instant.";
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
        List<String> paragraphes = paragraphes(nomFichier, contenu);
        return lire(fiche, nomFichier, empreinte(contenu), paragraphes, couvertures);
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
     * Le texte d'un {@code .docx} dans l'ordre du document — paragraphes et tableaux entrelacés, une ligne de tableau par
     * ligne, cellules séparées par une tabulation, les paragraphes d'une cellule joints par une espace — puis découpé en
     * unités ({@link LectureDao#unitesDocument}). Même extraction que le {@code LireDocx} du front : run par run, le trait
     * d'union insécable rendu, l'appel de note {@code [note:n]} et la note sur la ligne suivante.
     */
    public static List<String> paragraphes(String nomFichier, byte[] contenu) {
        if (nomFichier == null || !nomFichier.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw ImportRefuseException.format(MESSAGE_FORMAT);
        }
        if (contenu == null || contenu.length == 0) {
            throw ImportRefuseException.format(MESSAGE_FORMAT + " Le fichier reçu est vide.");
        }
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
                                        cellule.append(' ');
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
        return LectureDao.unitesDocument(lignes);
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
                    case "tab" -> sb.append('\t');
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
            List<ModelesDao.Couverture> couvertures) {
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

        for (ModelesDao.Couverture c : couvertures) {
            LectureDao.Resultat r = LectureDao.lire(c.sigle(), modeles.modele(c.sigle()), paragraphes, code -> {
                ChampFicheMarche ch = champs.get(code);
                return ch == null ? null : new LectureDao.InfoChamp(ch.getType(), ch.getSource(), ch.getCleCadrage());
            });
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
            r.propositions().forEach(p -> attendus.add(p.code()));
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

        // Le cadrage : chaque réponse validée comme par PUT …/cadrage ; refusée, elle devient un avertissement.
        Map<String, Object> cadrageFiche = fiche.getCadrage() == null ? Map.of() : fiche.getCadrage();
        Map<String, Object> cadrageLu = new LinkedHashMap<>(cadrageFiche);
        List<ImportDaoResult.Cadrage> cadrage = new ArrayList<>();
        for (LectureDao.Reponse rc : reponses.values()) {
            if (enConflit.contains(rc.cle())) {
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
        List<ImportDaoResult.Divergence> divergences = new ArrayList<>();
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
            ChampFicheMarche c = champs.get(p.code());
            if (c == null || !Boolean.TRUE.equals(c.getActif())) {
                continue;
            }
            if (SourceChampFiche.PPM.name().equals(c.getSource())) {
                String plan = valeursPlan.get(p.code());
                if (plan == null || !LectureDao.norm(plan).equalsIgnoreCase(LectureDao.norm(p.valeur()))) {
                    divergences.add(new ImportDaoResult.Divergence(p.code(), p.brut(), plan));
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
            if (alloti && Boolean.TRUE.equals(c.getParLot())) {
                anomalies.add("« " + c.getLibelle() + " » se saisit par lot : l'import ne lit pas encore le lot.");
            }
            sortie.add(new ImportDaoResult.Proposition(p.code(), null, normalisee != null ? normalisee : p.valeur(), p.brut(),
                    p.confiance().libelle(), p.extrait(), valeursFiche.get(p.code()), anomalies));
        }
        Set<String> proposes = new LinkedHashSet<>();
        sortie.forEach(p -> proposes.add(p.code()));
        List<String> nonTrouves = proposables.stream().filter(c -> !proposes.contains(c) && !enConflit.contains(c)).toList();
        List<ImportDaoResult.Ambigu> ambigusSortie = ambigus.stream()
                .map(a -> new ImportDaoResult.Ambigu(a.candidats(), a.texte())).toList();
        return new ImportDaoResult(nomFichier, empreinte, lus, cadrage, sortie, ambigusSortie, divergences, conflits,
                nonTrouves, avertissements);
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
