package cnm.prs.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.springframework.stereotype.Component;

import com.documents4j.api.DocumentType;
import com.documents4j.api.IConverter;
import com.documents4j.job.LocalConverter;

import cnm.prs.exception.BusinessRuleException;
import jakarta.annotation.PreDestroy;

/**
 * Génère le PDF du <strong>Projet de PV pour avis favorable sous réserve</strong> à partir du modèle Word
 * {@code resources/templates/PV_AFSR_PPMAGPM_CENTRALE.docx} : copie du {@code .docx}, remplacement des
 * placeholders (au niveau du paragraphe, gère les runs scindés), puis conversion en PDF via Microsoft Word
 * (documents4j local), comme la lettre de renvoi. La mise en forme et l'emblème du modèle sont conservés.
 *
 * <p>Particularités du modèle PV :</p>
 * <ul>
 *   <li>{@code <DATE EXAMEN>} apparaît 2 fois : formatée « jj mois aaaa » dans « Séance du », et
 *       <strong>en toutes lettres</strong> dans le paragraphe « L'an … » (détecté par « instituée »).</li>
 *   <li>Bloc « Étaient présents » : les lignes Président / Chef de commission ne sont conservées que si
 *       le nom est fourni (rôle ayant signé) ; Membre et Secrétaire de séance sont toujours présents.</li>
 *   <li>ANNEXE : la ligne modèle du tableau est dupliquée pour chaque observation (point / au lieu de / lire).</li>
 * </ul>
 */
@Component
public class PvDocumentGenerator {

    /**
     * Modèles officiels disponibles (localité <strong>centrale</strong> ; aucune variante régionale
     * fournie). Le choix appartient au métier ({@code PvDocumentService}), jamais au générateur.
     *
     * <p>⚠️ 2026-08-03 — ajout des deux modèles « <strong>avis favorable sans réserve</strong> »
     * (fournis par le user) : sans annexe, et avec/sans la mention AGPM selon le sous-type du dossier.</p>
     */
    /** Sens de l'avis dans le nom du modèle (⚠️ 2026-08-04 : {@code ANF} ajouté). */
    public enum SensAvis {
        /** Favorable AVEC réserves : clause « sous réserve … » + ANNEXE des observations. */
        AFSR,
        /** Favorable sans réserve : ni clause ni annexe. */
        AF,
        /** NON favorable : « émet un AVIS NON FAVORABLE … », ni clause ni annexe. */
        ANF
    }

    /**
     * Modèle officiel du PV selon les <strong>trois axes</strong> — 12 fichiers
     * {@code /templates/PV_{AFSR|AF|ANF}_{PPMAGPM|PPM}_{CENTRALE|REGIONALE}.docx} :
     * <ul>
     *   <li><strong>avis</strong> : cf. {@link SensAvis} ;</li>
     *   <li><strong>sous-type</strong> : {@code PPMAGPM} porte la mention « … et à la publication de
     *       l'AGPM » (intitulé et phrase d'avis) ; {@code PPM} ne la porte pas ;</li>
     *   <li><strong>portée</strong> : {@code REGIONALE} ajoute la localité à l'en-tête et dit
     *       « Commission Régionale » (titre, paragraphe « L'an … » et avis).</li>
     * </ul>
     *
     * @param sens     sens de l'avis (AFSR / AF / ANF)
     * @param avecAgpm dossier de sous-type {@code PPM-AGPM}
     * @param centrale dossier de la localité centrale (sinon régionale)
     */
    public static String modele(SensAvis sens, boolean avecAgpm, boolean centrale) {
        return "/templates/PV_" + sens.name()
                + (avecAgpm ? "_PPMAGPM_" : "_PPM_")
                + (centrale ? "CENTRALE" : "REGIONALE") + ".docx";
    }

    private static final String DATE_EXAMEN = "<DATE EXAMEN>";
    private static final String REFERENCE_PV = "<REFERENCE PV >";
    private static final String DATE_RECEPTION = "<DATE RECEPTION DU DOSSIER>";
    private static final String ENTITE = "<ENTITE CONTRACTANTE>";
    private static final String ANNEE = "<ANNEE EXERCICE>";
    private static final String PRESIDENT = "<NOM ET PRENOMS DU PRESIDENT>";
    private static final String CHEF_COMMISSION = "<NOM ET PRENOMS DU CHEF DE COMMISSION>";
    private static final String MEMBRE = "<NOM ET PRENOMS DU MEMBRE>";
    /**
     * ⚠️ <strong>Ligne retirée</strong> (règle du pilote, 2026-09-02) — le Secrétaire de séance a
     * disparu du cycle du PV. Le placeholder est retiré des 12 modèles, mais la constante survit pour
     * servir de <strong>garde</strong> : tout paragraphe qui le porterait encore est supprimé, de sorte
     * qu'un modèle mal re-dérivé n'imprime jamais un marqueur brut à la place d'un nom.
     */
    private static final String SECRETAIRE_SEANCE_RETIRE = "<NOM ET PRENOMS DU VERIFICATEUR>";

    /**
     * ⚠️ Refonte du bloc VISA (2026-09-01) — ligne du viseur, ajoutée aux 12 modèles PV dans la table
     * VISA (cellule de droite, côté Commission). Le P/CC n'y avait aucun emplacement : le bloc ne
     * portait que le supérieur hiérarchique de l'entité et le membre en charge du dossier.
     */
    private static final String VISEUR = "<VISEUR>";
    private static final String LOCALITE = "<LOCALITE>";
    /**
     * ⚠️ 2026-08-04 — lieu d'établissement du document (« A &lt;chef-lieu&gt;, le … ») : la ville où
     * siège la Commission, distincte de la LOCALITÉ (région) affichée dans l'en-tête régional.
     * Alimenté par {@code tr_localite.CHEF_LIEU}, repli sur le libellé de la localité.
     */
    private static final String CHEF_LIEU = "<CHEF LIEU>";
    /**
     * Graphies du marqueur rencontrées dans les modèles officiels fournis par le métier : trait d'union
     * ou espace, avec ou sans espace avant le chevron fermant (cf. {@code <REFERENCE PV >}). Toutes sont
     * acceptées, sinon un modèle installé tel quel laisserait le marqueur brut sur un PV signé.
     */
    private static final String[] CHEF_LIEU_GRAPHIES = {
        CHEF_LIEU, "<CHEF LIEU >", "<CHEF-LIEU>", "<CHEF-LIEU >"
    };
    private static final String DATE_AUJOURDHUI = "<DATE AUJOURD’HUI>";
    private static final String POINT = "<POINT DE CONTROLE>";
    private static final String AU_LIEU_DE = "<AU LIEU DE>";
    private static final String LIRE = "<LIRE>";

    /** Marqueur du paragraphe « L'an … » (date en toutes lettres). */
    private static final String MARQUEUR_LAN = "instituée";

    /**
     * ⚠️ 2026-08-05 (versionnement des PPM) — nature du plan. Les modèles écrivent « INITIAL » en dur
     * (deux fois sur les variantes AGPM : le PPM et l'AGPM) ; ce mot <strong>tient lieu de marqueur</strong>
     * et devient « MODIFICATIF N°n » quand le dossier est une version. Aucun modèle n'a été retouché :
     * la substitution est portée par le générateur, et reste circonscrite au paragraphe NATURE.
     */
    private static final String MARQUEUR_NATURE = "NATURE ET INTITULE DU DOSSIER";
    private static final String NATURE_INITIALE = "INITIAL";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH);

    /** Convertisseur Word partagé, initialisé paresseusement (cf. lettre de renvoi). */
    private volatile IConverter convertisseur;

    /** @return le PDF du Projet de PV (copie du modèle, placeholders remplis). */
    public byte[] genererPdf(PvDocumentContexte ctx) {
        // repli historique : favorable avec réserves / PPM-AGPM / centrale
        return genererPdf(ctx, modele(SensAvis.AFSR, true, true));
    }

    /**
     * @param modele ressource du modèle Word à utiliser (cf. constantes {@code MODELE_*}) — choisi par
     *               le métier selon l'avis et le sous-type du dossier. Le remplissage de l'ANNEXE est
     *               sans effet sur les modèles qui n'en comportent pas (avis favorable sans réserve).
     * @return le PDF du Projet de PV (copie du modèle, placeholders remplis).
     */
    public byte[] genererPdf(PvDocumentContexte ctx, String modele) {
        try (InputStream in = getClass().getResourceAsStream(modele)) {
            if (in == null) {
                throw new BusinessRuleException("Modèle de PV introuvable : " + modele);
            }
            XWPFDocument doc = new XWPFDocument(in);
            remplirCorps(doc, ctx);
            remplirTablesHorsAnnexe(doc, baseMap(ctx));
            remplirAnnexe(doc, ctx);
            ByteArrayOutputStream docxOut = new ByteArrayOutputStream();
            doc.write(docxOut);
            doc.close();

            return convertirEnPdf(docxOut.toByteArray());
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("Génération du document du PV impossible : " + e.getMessage());
        }
    }

    /** Placeholders constants (hors {@code <DATE EXAMEN>} et lignes conditionnelles président/CC). */
    private Map<String, String> baseMap(PvDocumentContexte ctx) {
        Map<String, String> m = new HashMap<>();
        m.put(REFERENCE_PV, nz(ctx.refPv()));
        m.put(DATE_RECEPTION, fmt(ctx.dateReception()));
        m.put(ENTITE, nz(ctx.entiteContractante()));
        m.put(ANNEE, ctx.anneeExercice() == null ? "" : String.valueOf(ctx.anneeExercice()));
        m.put(MEMBRE, nz(ctx.nomMembre()));
        m.put(VISEUR, nz(ctx.ligneViseur()));
        m.put(LOCALITE, nz(ctx.localite()));
        // Lieu d'établissement : chef-lieu si renseigné, sinon libellé de la localité (repli).
        String lieu = nonVide(ctx.chefLieu()) ? ctx.chefLieu() : nz(ctx.localite());
        for (String graphie : CHEF_LIEU_GRAPHIES) {
            m.put(graphie, lieu);
        }
        m.put(DATE_AUJOURDHUI, fmt(LocalDate.now()));
        return m;
    }

    /** Corps (paragraphes hors tableaux) : double date, lignes présents conditionnelles, autres placeholders. */
    private void remplirCorps(XWPFDocument doc, PvDocumentContexte ctx) {
        Map<String, String> base = baseMap(ctx);
        boolean president = nonVide(ctx.nomPresident());
        boolean chef = nonVide(ctx.nomChefCommission());
        // ⚠️ Co-signature élargie (2026-09-04, §4) — la ligne « Membre » devient CONDITIONNELLE, comme
        // celles du Président et du CC. Un PV visé P + CC seul n'a aucun Membre signataire : imprimer
        // sa ligne ferait porter au document un nom sous une signature absente.
        boolean membre = nonVide(ctx.nomMembre());
        List<XWPFParagraph> aSupprimer = new ArrayList<>();
        for (XWPFParagraph p : doc.getParagraphs()) {
            String texte = texteConcatene(p);
            if (texte.isEmpty()) {
                continue;
            }
            // ⚠️ Secrétaire de séance retiré du PV (règle du pilote, 2026-09-02) : sa ligne est
            // supprimée du document. Les 12 modèles ont été re-dérivés sans elle ; cette garde couvre
            // le cas d'un modèle qui la porterait encore, pour qu'un marqueur brut ne s'imprime jamais.
            if (texte.contains(SECRETAIRE_SEANCE_RETIRE)) {
                aSupprimer.add(p);
                continue;
            }
            if (texte.contains(PRESIDENT) && !president) {
                aSupprimer.add(p);
                continue;
            }
            if (texte.contains(CHEF_COMMISSION) && !chef) {
                aSupprimer.add(p);
                continue;
            }
            if (texte.contains(MEMBRE) && !membre) {
                aSupprimer.add(p);
                continue;
            }
            Map<String, String> m = new HashMap<>(base);
            if (president) {
                m.put(PRESIDENT, ctx.nomPresident());
            }
            if (chef) {
                m.put(CHEF_COMMISSION, ctx.nomChefCommission());
            }
            if (texte.contains(DATE_EXAMEN)) {
                // « L'an … » (paragraphe juridique, marqueur « instituée ») → année + « et le » + jour mois ;
                // « Séance du … » → format chiffres « jj mois aaaa ».
                m.put(DATE_EXAMEN, texte.contains(MARQUEUR_LAN)
                        ? NombreEnLettres.dateExamenPourLAn(ctx.dateExamen()) : fmt(ctx.dateExamen()));
            }
            // ⚠️ 2026-08-05 (versionnement) — nature du plan annoncée à la ligne NATURE. Le mot INITIAL
            // des modèles TIENT LIEU DE MARQUEUR : sur une version, il devient « MODIFICATIF N°n ».
            // Restreint à ce paragraphe pour ne jamais toucher un libellé de marché contenant ce mot.
            if (texte.startsWith(MARQUEUR_NATURE) && estUneMiseAJour(ctx)) {
                m.put(NATURE_INITIALE, "MODIFICATIF N°" + ctx.numMaj());
            }
            remplacerDansParagraphe(p, m);
        }
        for (XWPFParagraph p : aSupprimer) {
            int pos = doc.getBodyElements().indexOf(p);
            if (pos >= 0) {
                doc.removeBodyElement(pos);
            }
        }
    }

    /** Vrai si le PV porte sur une VERSION du PPM (mise à jour n°1, 2, …) et non sur le plan initial. */
    private boolean estUneMiseAJour(PvDocumentContexte ctx) {
        return ctx.numMaj() != null && ctx.numMaj() > 0;
    }

    /** Tableaux hors ANNEXE (bloc de signature : {@code <CHEF LIEU>}, {@code <DATE AUJOURD'HUI>}). */
    private void remplirTablesHorsAnnexe(XWPFDocument doc, Map<String, String> base) {
        for (XWPFTable table : doc.getTables()) {
            if (table.getText() != null && table.getText().contains(POINT)) {
                continue;   // ANNEXE traitée à part
            }
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    cell.getParagraphs().forEach(p -> remplacerDansParagraphe(p, base));
                }
            }
        }
    }

    /** ANNEXE : duplique la ligne modèle pour chaque observation, puis retire la ligne modèle. */
    private void remplirAnnexe(XWPFDocument doc, PvDocumentContexte ctx) {
        XWPFTable annexe = null;
        for (XWPFTable t : doc.getTables()) {
            if (t.getText() != null && t.getText().contains(POINT)) {
                annexe = t;
                break;
            }
        }
        if (annexe == null) {
            return;
        }
        int idxModele = -1;
        for (int i = 0; i < annexe.getRows().size(); i++) {
            if (annexe.getRow(i).getCtRow().toString().contains(POINT) || ligneContient(annexe.getRow(i), POINT)) {
                idxModele = i;
                break;
            }
        }
        if (idxModele < 0) {
            return;
        }
        XWPFTableRow modele = annexe.getRow(idxModele);
        List<PvDocumentContexte.Observation> obs = ctx.observations() == null ? List.of() : ctx.observations();
        int insert = idxModele;
        for (PvDocumentContexte.Observation o : obs) {
            CTRow ct = (CTRow) modele.getCtRow().copy();
            XWPFTableRow ligne = new XWPFTableRow(ct, annexe);
            Map<String, String> m = new HashMap<>();
            m.put(POINT, nz(o.pointControle()));
            m.put(AU_LIEU_DE, nz(o.auLieuDe()));
            m.put(LIRE, nz(o.lire()));
            // ⚠️ Pièce jointe non conforme (2026-08-01) : observation en texte libre — les libellés
            // « Au lieu de : / Lire : » de la ligne modèle n'ont pas de sens, on les efface.
            if (o.piece()) {
                m.put("Au lieu de :", "");
                m.put("Lire :", "");
            }
            for (XWPFTableCell cell : ligne.getTableCells()) {
                cell.getParagraphs().forEach(p -> remplacerDansParagraphe(p, m));
            }
            annexe.addRow(ligne, insert++);
        }
        annexe.removeRow(insert);   // retire la ligne modèle (décalée en `insert` après les insertions)
    }

    private boolean ligneContient(XWPFTableRow row, String token) {
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph p : cell.getParagraphs()) {
                if (texteConcatene(p).contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Remplace les placeholders d'un paragraphe en raisonnant sur le texte concaténé des runs. */
    private void remplacerDansParagraphe(XWPFParagraph paragraphe, Map<String, String> rempl) {
        List<XWPFRun> runs = paragraphe.getRuns();
        if (runs == null || runs.isEmpty()) {
            return;
        }
        String texte = texteConcatene(paragraphe);
        if (texte.isEmpty() || !contientUnPlaceholder(texte, rempl)) {
            return;
        }
        String remplace = texte;
        for (Map.Entry<String, String> e : rempl.entrySet()) {
            remplace = remplace.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        runs.get(0).setText(remplace, 0);
        for (int i = runs.size() - 1; i >= 1; i--) {
            paragraphe.removeRun(i);
        }
    }

    private String texteConcatene(XWPFParagraph paragraphe) {
        List<XWPFRun> runs = paragraphe.getRuns();
        if (runs == null || runs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : runs) {
            String t = run.getText(0);
            if (t != null) {
                sb.append(t);
            }
        }
        return sb.toString();
    }

    private boolean contientUnPlaceholder(String texte, Map<String, String> rempl) {
        for (String cle : rempl.keySet()) {
            if (texte.contains(cle)) {
                return true;
            }
        }
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean nonVide(String s) {
        return s != null && !s.isBlank();
    }

    private static String fmt(LocalDate d) {
        return d == null ? "" : d.format(FMT);
    }

    /**
     * ⚠️ Robustesse (2026-08-04) — Word (documents4j) peut <strong>s'arrêter entre deux conversions</strong> ;
     * le convertisseur en cache devenait alors définitivement inutilisable (« The converter seems to be
     * shut down ») et TOUTES les générations suivantes échouaient jusqu'au redémarrage du serveur.
     * On retente donc UNE fois avec un convertisseur neuf.
     */
    private byte[] convertirEnPdf(byte[] docx) {
        try {
            return convertir(docx);
        } catch (RuntimeException premiereTentative) {
            invaliderConvertisseur();
            return convertir(docx);
        }
    }

    private byte[] convertir(byte[] docx) {
        ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
        convertisseur().convert(new ByteArrayInputStream(docx))
                .as(DocumentType.DOCX).to(pdfOut).as(DocumentType.PDF).execute();
        return pdfOut.toByteArray();
    }

    /**
     * ⚠️ 2026-08-19 — préchauffage au démarrage ({@link PvDocumentPrechauffage}) : démarre le pont
     * Word pour que la première génération ne paie pas le lancement de Word.
     */
    public void prechauffer() {
        convertisseur();
    }

    /** Convertisseur partagé, (re)créé s'il est absent ou arrêté. */
    private IConverter convertisseur() {
        IConverter c = convertisseur;
        if (c == null || !c.isOperational()) {
            synchronized (this) {
                c = convertisseur;
                if (c == null || !c.isOperational()) {
                    c = LocalConverter.builder().build();
                    convertisseur = c;
                }
            }
        }
        return c;
    }

    /** Oublie (et tente de fermer) le convertisseur courant : le suivant sera recréé. */
    private void invaliderConvertisseur() {
        synchronized (this) {
            IConverter c = convertisseur;
            convertisseur = null;
            if (c != null) {
                try {
                    c.shutDown();
                } catch (RuntimeException dejaArrete) {
                    // convertisseur déjà mort : rien à fermer
                }
            }
        }
    }

    /**
     * ⚠️ CI Linux (2026-08-28) — délègue à {@link #invaliderConvertisseur()}, dont le {@code shutDown()}
     * est GARDÉ. Sur une machine sans Word (runners GitHub), {@code LocalConverter.build()} rend un
     * convertisseur dont l'{@code executorService} est nul ; {@code shutDown()} y lève alors
     * « Cannot invoke ExecutorService.shutdown() because this.executorService is null ». Ce PreDestroy
     * s'exécutant à la fermeture de CHAQUE contexte Spring de test, l'exception faisait échouer la
     * construction. La garde existait déjà à l'invalidation et manquait ici : deux chemins vers la même
     * fermeture, un seul protégé. Le symptôme est invisible en local, où Word est présent.
     */
    @PreDestroy
    void fermerConvertisseur() {
        invaliderConvertisseur();
    }
}
