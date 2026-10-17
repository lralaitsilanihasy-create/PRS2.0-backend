package cnm.prs.service;

import static cnm.prs.service.TableauxBesoin.POINTILLES;
import static cnm.prs.service.TableauxBesoin.texte;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import cnm.prs.dto.ArticleBesoinDto;
import cnm.prs.dto.MaterielExigeDto;
import cnm.prs.dto.PersonnelExigeDto;

/**
 * ⚠️ 2026-10-17 (demande front « charte-dao-travaux », T5 à T7 ; arbitrage 4 du pilote) — les <strong>formulaires techniques du
 * candidat</strong> intégrés au DAO complet des travaux. Le CCAP du document type les cite comme pièces du marché sans en donner le
 * modèle : leur texte est repris des deux DAO de travaux réels (routes Ankily – Mahasoa, MTP ; bâtiments scolaires, MEN), sans
 * clause, seulement des consignes au candidat, et ce qui y désignait un service ou une commune nommés renvoie à l'autorité
 * contractante.
 *
 * <ul>
 *   <li>annexes du cadre d'acte d'engagement : le mode de calcul du coefficient K1 et le cadre du sous-détail des prix, qui cite les
 *       prix à sous-détailler de chaque lot (installation de chantier, prix marqués d'un astérisque) ;</li>
 *   <li>annexes des spécifications techniques : la liste des plans ({@code B04-CD-03}, « Néant » sans plan), le personnel et le
 *       matériel affectés, <strong>pré-remplis</strong> depuis le personnel et le matériel exigés de la version validée (les colonnes
 *       du candidat restent vides), le curriculum vitae, l'engagement de location, les cinq plannings (la grille du planning général
 *       compte autant de colonnes que le délai d'exécution du lot, ses lignes sont les séries de son détail quantitatif) et le plan
 *       de charge.</li>
 * </ul>
 * Mise en forme de la charte, par les outils de {@link TableauxBesoin}.
 */
public final class FormulairesTravaux {

    private FormulairesTravaux() {
    }

    private static final String CERTIFIE = "Je certifie sincères et véritables les indications portées ci-dessus.";

    // ------------------------------------------------------------------ T5 : K1 et sous-détail des prix

    /** Le mode de calcul du coefficient de majoration des déboursés K1 (DAO routier, p. 45) ; la TVA est le paramètre de la fiche. */
    public static byte[] k1(BigDecimal tauxTva) {
        return document(doc -> {
            corps(doc, "Prix du règlement = Déboursés × « K1 »");
            corps(doc, "« K1 » est le coefficient de majoration des déboursés. La formule à utiliser pour son calcul est :");
            centre(doc, "K1 = (1 + A1/100) × (1 + A2/100) / (1 − [A3/100 × (1 + T/100)])");
            corps(doc, "où T est la taxe sur la valeur ajoutée, qui est actuellement de "
                    + (tauxTva == null ? "……" : FormulairesEnLigne.lisible(tauxTva)) + " % pour les marchés de travaux. Les autres "
                    + "paramètres de cette formule (A1, A2 et A3) sont définis dans le tableau ci-dessous. K1 sera arrondi à la deuxième "
                    + "décimale par défaut.");
            List<List<String>> l = List.of(
                    List.of("Frais généraux proportionnels aux déboursés", "Frais d'agence et de patente", "a.1", "A1"),
                    List.of("", "Frais de chantier", "a.2", ""),
                    List.of("", "Frais d'études et de laboratoire", "a.3", ""),
                    List.of("", "Assurances", "a.4", ""),
                    List.of("Bénéfice brut et frais financiers proportionnels au prix de revient", "Bénéfice et impôts sur le bénéfice", "a.5",
                            "A2"),
                    List.of("", "Aléas techniques", "a.6", ""),
                    List.of("", "Aléas de révision de prix", "a.7", ""),
                    List.of("", "Frais financiers", "a.8", ""),
                    List.of("Frais proportionnels au prix de règlement", "Frais de siège", "a.9", "A3"));
            TableauxBesoin.table(doc, List.of("Origine des frais", "Décomposition à l'intérieur de chaque catégorie de frais", "Indice",
                    "Indice de composition de catégorie"), new int[] { 3000, 3700, 1000, 1712 }, l);
            corps(doc, "A1 = a.1 + a.2 + a.3 + a.4 ; A2 = a.5 + a.6 + a.7 + a.8 ; A3 = a.9.").setSpacingBefore(120);
            corps(doc, "Nota : A3 est nul dans le cas d'une entreprise ayant son siège social à Madagascar. La formule devient dans ce cas :");
            centre(doc, "« K1 » = (1 + A1/100) × (1 + A2/100)");
            TableauxBesoin.signature(doc);
        });
    }

    /**
     * Le cadre du sous-détail des prix (DAO routier, p. 46) : les consignes, les prix à sous-détailler de chaque lot (⚠️ Q2 : ceux de
     * l'installation de chantier et ceux que le besoin marque {@code sousDetail}, imprimés avec un astérisque), puis le cadre.
     */
    public static byte[] sousDetail(TableauxBesoin.Contexte c, List<ArticleBesoinDto> articles) {
        return document(doc -> {
            corps(doc, "1 / Les candidats devront fournir le sous-détail des seuls prix suivants : tous les prix d'installation de chantier "
                    + "figurant au bordereau des prix et au détail quantitatif et estimatif ; tous les prix de sous-traitance de laboratoire "
                    + "(si ces prix existent au bordereau des prix et au détail quantitatif et estimatif du lot) ; les prix marqués d'un "
                    + "astérisque au détail quantitatif et estimatif, qui correspondent en principe aux prix les plus importants du marché "
                    + "de par leur montant.");
            for (Map.Entry<TableauxBesoin.Lot, List<String>> e : prixParLot(c, articles).entrySet()) {
                String lot = e.getKey().numero() == null ? "" : "lot " + e.getKey().numero() + " : ";
                corps(doc, "Prix à sous-détailler, " + lot + "n° " + String.join(", ", e.getValue()) + ".").setIndentationLeft(400);
            }
            corps(doc, "2 / Le sous-détail sera présenté conformément au tableau ci-dessous.");
            corps(doc, "3 / Le calcul du prix sec sera effectué sur la base d'une production journalière. Ainsi, le rendement « R » à "
                    + "prendre en considération doit être la production journalière de l'atelier (ensemble de moyens humains et matériels) "
                    + "chargé de réaliser les travaux rémunérés par le prix unitaire considéré.");
            corps(doc, "4 / La grille de calcul sous Excel peut être fournie par l'administration selon la demande des candidats.");
            gras(doc, "Entreprise : " + POINTILLES + " — Lot : ……… — Sous-détail du prix n° ……… — K1 = ………").setSpacingBefore(160);
            TableauxBesoin.table(doc, List.of("Désignation", "Unité", "Quantité à réaliser", "Rendement journalier", "Durée de la tâche (jours)"),
                    new int[] { 3600, 1000, 1600, 1600, 1612 }, List.of(List.of("", "", "", "", "")));
            corps(doc, "");   // deux tableaux contigus, Word les fusionnerait
            List<List<String>> l = new ArrayList<>();
            List<Integer> genres = new ArrayList<>();
            composante(l, genres, "Personnel", List.of("Conducteur de travaux", "Chef d'équipe", "Ouvrier spécialisé", "Manœuvre"));
            composante(l, genres, "Matériel", List.of("Pelle", "Camion", "Niveleuse", "Compacteur", "Citerne"));
            composante(l, genres, "Matériaux", List.of("Redevance carrière"));
            composante(l, genres, "Outillage", List.of("Petit matériel"));
            l.add(List.of("Total déboursés secs = « D »", "", "", "", "", ""));
            genres.add(TableauxBesoin.SERIE);
            XWPFTable t = TableauxBesoin.table(doc, List.of("Composante du prix", "Nombre", "Unité ou jour", "Quantité totale", "Prix unitaire",
                    "Total"), new int[] { 3200, 1000, 1200, 1300, 1300, 1412 }, l);
            TableauxBesoin.griser(t, genres);
            gras(doc, "Prix de vente à l'unité = (« D » × « K1 ») / « R » = " + POINTILLES).setSpacingBefore(120);
            TableauxBesoin.signature(doc);
        });
    }

    private static void composante(List<List<String>> l, List<Integer> genres, String nom, List<String> exemples) {
        l.add(List.of(nom, "", "", "", "", ""));
        genres.add(TableauxBesoin.SERIE);
        for (String x : exemples) {
            l.add(List.of("   " + x, "", "", "", "", ""));
            genres.add(TableauxBesoin.ARTICLE);
        }
        l.add(List.of("Total partiel — " + nom.toLowerCase(Locale.FRENCH), "", "", "", "", ""));
        genres.add(TableauxBesoin.SOUS_TOTAL);
    }

    /** Les prix à sous-détailler de chaque lot qui en a, dans l'ordre des lots. */
    static Map<TableauxBesoin.Lot, List<String>> prixParLot(TableauxBesoin.Contexte c, List<ArticleBesoinDto> articles) {
        Map<TableauxBesoin.Lot, List<String>> out = new LinkedHashMap<>();
        for (TableauxBesoin.Lot lot : lots(c)) {
            List<String> prix = TableauxBesoin.prixASousDetailler(articlesDuLot(articles, lot));
            if (!prix.isEmpty()) {
                out.put(lot, prix);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ T7 : liste des plans ; T6 : personnel et matériel

    /** ⚠️ Q3 — la liste des plans : les titres de {@code B04-CD-03}, un par ligne ; « Néant » sans plan. */
    public static byte[] listePlans(String plans) {
        return listePlans(plans, false);
    }

    /**
     * ⚠️ 2026-10-17 (demande front « specifications-donnees-et-plans-pdf », §B2) — et, quand le devis descriptif et les plans sont
     * déposés en PDF ({@code annexe}), la phrase qui renvoie au document annexé : le PDF n'est jamais fondu dans le DAO (Word ne
     * l'insère pas, ses pages en A1 casseraient la charte).
     */
    public static byte[] listePlans(String plans, boolean annexe) {
        List<String> titres = plans == null ? List.of()
                : plans.lines().map(s -> s.replaceFirst("^\\s*(?:[-•*]|\\d+[.)/-])\\s*", "").trim()).filter(s -> !s.isEmpty()).toList();
        return document(doc -> {
            if (annexe) {
                corps(doc, "Le devis descriptif et les plans font l'objet du document annexé « " + DescriptifPlans.INTITULE
                        + " », remis avec le présent dossier.");
            }
            if (titres.isEmpty()) {
                if (!annexe) {
                    corps(doc, "Néant.");   // avec l'annexe, la phrase de renvoi suffit
                }
                return;
            }
            List<List<String>> l = new ArrayList<>();
            for (int i = 0; i < titres.size(); i++) {
                l.add(List.of(String.valueOf(i + 1), titres.get(i)));
            }
            TableauxBesoin.table(doc, List.of("N°", "Titre du plan"), new int[] { 900, 8512 }, l);
        });
    }

    /**
     * La liste du personnel affecté aux lots (DAO routier, p. 95-96), pré-remplie : une ligne par poste exigé, avec son nombre, le
     * diplôme et l'expérience ; une colonne « Nom proposé » par lot quand un poste est exigé par lot sur une ligne allotie, une seule
     * sinon. Puis le modèle de curriculum vitae.
     */
    public static byte[] personnel(List<TableauxBesoin.Lot> lots, List<PersonnelExigeDto> personnel) {
        List<PersonnelExigeDto> ps = personnel == null ? List.of() : personnel;
        List<TableauxBesoin.Lot> numerotes = lots == null ? List.of() : lots.stream().filter(x -> x.numero() != null).toList();
        boolean parLot = numerotes.size() > 1 && ps.stream().anyMatch(x -> Boolean.TRUE.equals(x.getParLot()));
        return document(doc -> {
            List<String> entete = new ArrayList<>(List.of("Poste", "Nombre exigé", "Diplôme et expérience exigés"));
            int[] base = { 2400, 900, 3100 };
            int reste = TableauxBesoin.LARGEUR - 6400;
            int n = parLot ? numerotes.size() : 1;
            int[] largeurs = new int[3 + n];
            System.arraycopy(base, 0, largeurs, 0, 3);
            for (int i = 0; i < n; i++) {
                largeurs[3 + i] = i == n - 1 ? reste - (reste / n) * (n - 1) : reste / n;
                entete.add(parLot ? "Nom proposé — lot " + numerotes.get(i).numero() : "Nom proposé");
            }
            List<List<String>> l = new ArrayList<>();
            for (PersonnelExigeDto p : ps) {
                List<String> ligne = new ArrayList<>(List.of(texte(p.getPoste()), p.getNombre() == null ? "" : String.valueOf(p.getNombre()),
                        exigences(p)));
                for (int i = 0; i < n; i++) {
                    ligne.add("");
                }
                l.add(ligne);
            }
            vides(l, ps.isEmpty() ? 4 : 0, entete.size());
            TableauxBesoin.table(doc, entete, largeurs, l);
            corps(doc, "Pour chaque nom proposé : l'identité (nom, prénoms) ; P/NP = permanent de l'entreprise ou non permanent ; le "
                    + "meilleur diplôme obtenu ; le nombre d'années d'expérience dans la fonction, dans l'entreprise et hors de cette "
                    + "entreprise. Joindre à l'offre son curriculum vitae signé et la copie légalisée de ses diplômes.").setSpacingBefore(120);
            certifie(doc, "Signature et cachet du candidat");

            titre(doc, "MODÈLE DE CURRICULUM VITAE", true);
            for (String champ : List.of("FONCTION PROPOSÉE", "NOM", "PRÉNOMS", "ÂGE", "ANCIENNETÉ DANS L'ENTREPRISE")) {
                corps(doc, champ + " : " + POINTILLES + POINTILLES);
            }
            corps(doc, "(Photo d'identité)").setAlignment(ParagraphAlignment.RIGHT);
            gras(doc, "FORMATIONS ET DIPLÔMES");
            List<List<String>> f = new ArrayList<>();
            vides(f, 4, 3);
            TableauxBesoin.table(doc, List.of("Année", "Formation et/ou diplôme obtenu", "Diplôme joint"), new int[] { 1400, 6012, 2000 }, f);
            gras(doc, "EXPÉRIENCES PROFESSIONNELLES");
            List<List<String>> x = new ArrayList<>();
            vides(x, 5, 3);
            TableauxBesoin.table(doc, List.of("Année", "Entreprise / Société", "Fonction / Expérience"), new int[] { 1400, 3500, 4512 }, x);
            corps(doc, "Je soussigné " + POINTILLES + " certifie exactes les déclarations ci-dessus.");
            TableauxBesoin.paragraphe(doc, "Fait à " + POINTILLES + ", le " + POINTILLES, false, ParagraphAlignment.LEFT).setSpacingBefore(160);
            TableauxBesoin.paragraphe(doc, "Le déclarant", true, ParagraphAlignment.RIGHT);
        });
    }

    /** « Ingénieur des travaux publics ; 10 ans d'expérience (travaux routiers) ». */
    static String exigences(PersonnelExigeDto p) {
        List<String> l = new ArrayList<>();
        if (!texte(p.getDiplome()).isEmpty()) {
            l.add(texte(p.getDiplome()));
        }
        if (p.getExperienceAnnees() != null && p.getExperienceAnnees() > 0) {
            l.add(p.getExperienceAnnees() + (p.getExperienceAnnees() > 1 ? " ans" : " an") + " d'expérience"
                    + (texte(p.getDomaineExperience()).isEmpty() ? "" : " (" + texte(p.getDomaineExperience()) + ")"));
        } else if (!texte(p.getDomaineExperience()).isEmpty()) {
            l.add("Expérience : " + texte(p.getDomaineExperience()));
        }
        return String.join(" ; ", l);
    }

    /**
     * La liste du matériel affecté aux lots (DAO routier, p. 97), pré-remplie : une ligne par engin exigé, sa caractéristique, son
     * nombre (« par lot » s'il est exigé par lot) et le minimum en propre (« — » s'il est nul) ; le statut et la localisation sont au
     * candidat.
     */
    public static byte[] materiel(List<MaterielExigeDto> materiel) {
        List<MaterielExigeDto> ms = materiel == null ? List.of() : materiel;
        return document(doc -> {
            corps(doc, "Nota 1 / Les candidats doivent localiser dans leur offre les lieux de parcage où ces matériels peuvent être visités.");
            corps(doc, "Nota 2 / Doivent être obligatoirement indiqués ici tous les matériels exigés par le dossier d'appel d'offres, et pour "
                    + "chaque lot sur lequel le candidat prévoit de remettre une offre. Dans le cas où le candidat prévoit de remettre "
                    + "plusieurs offres et ne prétend à être attributaire que d'un seul lot, les matériels pourront être identiques pour les "
                    + "lots.");
            corps(doc, "Nota 3 / Joindre les contrats ou engagements de location et les copies légalisées des cartes grises.");
            List<List<String>> l = new ArrayList<>();
            for (MaterielExigeDto m : ms) {
                String nombre = m.getNombre() == null ? "" : m.getNombre() + (Boolean.TRUE.equals(m.getParLot()) ? " par lot" : "");
                String propre = m.getMinimumEnPropre() == null || m.getMinimumEnPropre() == 0 ? "—" : String.valueOf(m.getMinimumEnPropre());
                l.add(List.of(texte(m.getDesignation()), texte(m.getCaracteristique()), nombre, propre, "", ""));
            }
            vides(l, ms.isEmpty() ? 5 : 0, 6);
            TableauxBesoin.table(doc, List.of("Matériel exigé", "Caractéristique", "Nombre", "Dont en propre",
                    "Statut proposé (propriété, location longue durée, à louer)", "Localisation"),
                    new int[] { 2200, 1700, 900, 900, 2100, 1612 }, l);
            certifie(doc, "Signature et cachet du candidat");
        });
    }

    /** Le modèle d'engagement de location de matériel (DAO routier, p. 98). */
    public static byte[] location() {
        return document(doc -> {
            for (String s : List.of("Logo de l'entreprise", "Raison sociale de l'entreprise", "Adresse", "N° de téléphone")) {
                corps(doc, s);
            }
            titre(doc, "ENGAGEMENT DE LOCATION", false);
            corps(doc, "Je soussigné " + POINTILLES + ", représentant légal de la société " + POINTILLES + ", demeurant à " + POINTILLES
                    + ", numéro de téléphone " + POINTILLES);
            corps(doc, "A / Déclare être propriétaire du matériel suivant (1) :");
            for (int i = 1; i <= 3; i++) {
                corps(doc, i + ". Modèle, marque, type, année, immatriculation ou numéro de série").setIndentationLeft(400);
            }
            corps(doc, "4. etc.").setIndentationLeft(400);
            corps(doc, "B / M'engage, dans le cas où l'entreprise " + POINTILLES + " serait attributaire du lot ……, à donner en location ce "
                    + "matériel à cette entreprise (ce groupement), pour les durées suivantes à compter du " + POINTILLES + " :");
            for (int i = 1; i <= 3; i++) {
                corps(doc, i + ". Modèle, marque, type = …… mois").setIndentationLeft(400);
            }
            corps(doc, "4. etc.").setIndentationLeft(400);
            corps(doc, "C / Reconnais que le présent document engage l'entreprise " + POINTILLES + " vis-à-vis de l'autorité contractante et "
                    + "l'expose à d'éventuelles sanctions, dans le cas où la location partielle ou totale de ce matériel ne pourrait avoir "
                    + "lieu.");
            TableauxBesoin.paragraphe(doc, "Fait à " + POINTILLES + ", le " + POINTILLES, false, ParagraphAlignment.LEFT).setSpacingBefore(160);
            TableauxBesoin.paragraphe(doc, "Signature et cachet du loueur", true, ParagraphAlignment.RIGHT);
            corps(doc, "(1) Une ligne par machine.");
        });
    }

    // ------------------------------------------------------------------ T7 : plannings et plan de charge

    /** Une durée lue dans un texte de délai, en mois ({@code null} : illisible). */
    private static final Pattern DUREE = Pattern.compile("(\\d+)\\s*\\)?\\s*(mois|semaines?|jours?|ans?|années?)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** « six (06) mois » → 6 ; « 120 jours » → 4 ; « 2 ans » → 24 ; {@code null} sans durée chiffrée. */
    static Integer mois(String delai) {
        Matcher m = DUREE.matcher(delai == null ? "" : delai);
        if (!m.find()) {
            return null;
        }
        int n = Integer.parseInt(m.group(1));
        String u = m.group(2).toLowerCase(Locale.FRENCH);
        int mois = u.startsWith("mois") ? n : u.startsWith("semaine") ? (int) Math.ceil(n * 7 / 30.0)
                : u.startsWith("jour") ? (int) Math.ceil(n / 30.0) : n * 12;
        return Math.max(1, mois);
    }

    /**
     * La grille d'un planning : une colonne par mois jusqu'à douze mois, par trimestre jusqu'à trente-six, par semestre au-delà ; douze
     * colonnes mensuelles si le délai n'est pas chiffré.
     */
    record Grille(int colonnes, String unite) {

        static Grille de(Integer mois) {
            int m = mois == null ? 12 : mois;
            if (m <= 12) {
                return new Grille(m, "mois");
            }
            return m <= 36 ? new Grille((int) Math.ceil(m / 3.0), "trimestre") : new Grille((int) Math.ceil(m / 6.0), "semestre");
        }
    }

    /**
     * Les cinq plannings (DAO routier, p. 99-100) : les consignes, puis le planning général d'exécution de chaque lot (lignes : les
     * séries de son détail quantitatif ; colonnes : son délai d'exécution {@code B09-DL-01}), la mobilisation du personnel et du
     * matériel (pré-remplie depuis les moyens exigés), l'approvisionnement des matériaux, l'échéancier de paiement et l'intervention
     * pendant le délai de garantie ({@code B09-GT-01}, douze mois à défaut).
     *
     * @param delais le texte du délai d'exécution de chaque lot (clé {@code null} : ligne non allotie)
     */
    public static byte[] plannings(TableauxBesoin.Contexte c, List<ArticleBesoinDto> articles, List<PersonnelExigeDto> personnel,
            List<MaterielExigeDto> materiel, Map<Integer, String> delais, Integer garantieMois) {
        Integer plusLong = delais.values().stream().map(FormulairesTravaux::mois).filter(Objects::nonNull).max(Integer::compare).orElse(null);
        Grille generale = Grille.de(plusLong);
        // Une ligne non allotie : son délai dans l'en-tête des modèles communs ; plusieurs lots : le candidat le reporte.
        String unique = delais.size() == 1 && delais.values().iterator().next() != null ? delais.values().iterator().next().trim() : null;
        return document(doc -> {
            gras(doc, "1. Modèles");
            corps(doc, "Les candidats doivent remettre avec leur offre cinq plannings, qui devront être obligatoirement conformes aux modèles "
                    + "donnés ci-après et complétés selon les modalités suivantes. Ces plannings sont :");
            for (String s : List.of("8-a : Planning général d'exécution des travaux ;", "8-b : Planning de mobilisation du personnel et du matériel ;",
                    "8-c : Planning d'approvisionnement des matériaux ;", "8-d : Échéancier de paiement ;",
                    "8-e : Planning d'intervention pendant le délai de garantie.")) {
                corps(doc, s).setIndentationLeft(400);
            }
            gras(doc, "2. Modalités d'utilisation");
            corps(doc, "Attention : la qualité de présentation de ces documents et leur corrélation avec les autres pièces techniques et "
                    + "financières de l'offre sont des éléments permettant entre autres à la commission d'apprécier l'expérience et le "
                    + "professionnalisme de l'entreprise. Les opérations pour préparer ces plannings sont les suivantes.");
            gras(doc, "2-1 Planning général d'exécution");
            corps(doc, "Compléter : le nom de l'entreprise soumissionnaire, le numéro et la désignation du lot concerné, le délai maximal prévu "
                    + "dans le DAO ; enlever de la colonne « Série / tâche » les codes, numéros et désignations qui ne concernent pas le lot "
                    + "objet de la soumission ; rajouter, si besoin est, par lignes, de nouvelles rubriques en fonction du lot considéré et "
                    + "des travaux prévus ; compléter dans la colonne « Q », à partir du bordereau des prix et du détail quantitatif et "
                    + "estimatif du DAO, les quantités les plus importantes et les plus significatives ; indiquer dans la colonne "
                    + "« Rendement », sur la base de l'expérience de l'entreprise et des indications portées aux sous-détails de prix, les "
                    + "rendements qu'elle estime pouvoir appliquer aux travaux, et calculer par ligne les délais correspondant aux "
                    + "quantités à réaliser ; tracer un trait (ou griser les cases), pour chaque ligne, matérialisant les délais (1 case = 1 "
                    + generale.unite() + "), du début à la fin de l'opération, en prenant en compte les quantités à réaliser, le rendement "
                    + "du poste de travail et l'organisation logique du travail par phase d'intervention. Le trait correspondant au temps "
                    + "d'exécution des travaux de chaque poste peut être continu ou par intermittence. Le délai global représenté par "
                    + "l'enveloppe des traits du planning ne peut en aucun cas être supérieur à celui prévu dans le DAO. Signer et dater le "
                    + "planning, qui est une pièce contractuelle.");
            gras(doc, "2-2 Planning de mobilisation du personnel et du matériel");
            corps(doc, "Compléter : le nom de l'entreprise soumissionnaire, le numéro et la désignation du lot concerné, le délai maximal prévu "
                    + "dans le DAO ; enlever de la colonne « Désignation » les postes et matériels que l'entreprise ne juge pas devoir "
                    + "mobiliser pour la réalisation des travaux ; rajouter éventuellement des lignes pour le personnel ou le matériel que "
                    + "l'entreprise considère devoir mobiliser ; sur la base du planning d'exécution, et pour chaque ligne, remplir chaque "
                    + "case d'un chiffre correspondant au nombre d'unités prévu, sur la durée et la périodicité de mobilisation. Signer et "
                    + "dater le planning.");
            gras(doc, "2-3 Planning d'approvisionnement des matériaux");
            corps(doc, "Compléter : le nom de l'entreprise soumissionnaire, le numéro et la désignation du lot concerné, le délai maximal prévu "
                    + "dans le DAO ; enlever de la colonne « Désignation des matériaux » ceux qui ne concernent pas le lot (liste non "
                    + "exhaustive portée à titre indicatif) ; rajouter si nécessaire de nouvelles désignations de matériaux ; calculer, en "
                    + "fonction des volumes de travaux à réaliser, les quantités correspondantes de ciment, gravier, moellons, planches, fer "
                    + "à béton, etc., et les reporter dans la colonne « Quantité prévisionnelle » ; tracer un trait (ou griser les cases) "
                    + "correspondant au délai entre le démarrage de l'acheminement sur chantier et la fin de livraison des matériaux. "
                    + "Signer et dater le planning.");
            gras(doc, "2-4 Planning d'intervention pendant le délai de garantie");
            corps(doc, "Compléter : le nom de l'entreprise soumissionnaire, le numéro et la désignation du lot concerné, le délai de garantie "
                    + "prévu dans le DAO ; rajouter, par lignes, les activités ou tâches que le candidat prévoit d'effectuer ; compléter les "
                    + "colonnes « Unité » et « Qté » ; tracer un trait, pour chaque ligne, matérialisant les délais, du début à la fin de "
                    + "l'opération. Signer et dater le planning, qui est une pièce contractuelle.");

            // 8-a : un planning général par lot.
            for (TableauxBesoin.Lot lot : lots(c)) {
                List<ArticleBesoinDto> as = articlesDuLot(articles, lot);
                if (lot.numero() != null && as.isEmpty() && !articles.isEmpty()) {
                    continue;
                }
                String delai = delais.get(lot.numero());
                Grille g = Grille.de(mois(delai));
                titre(doc, "MODÈLE 8-a : PLANNING GÉNÉRAL D'EXÉCUTION DES TRAVAUX", true);
                corps(doc, "Entreprise : " + POINTILLES + " — Lot n° " + (lot.numero() == null ? "……" : lot.numero()
                        + (texte(lot.intitule()).isEmpty() ? "" : " : " + texte(lot.intitule()))) + " — Délai maximal prévu au DAO : "
                        + (delai == null || delai.isBlank() ? POINTILLES : delai.trim()) + " — une colonne par " + g.unite() + ".");
                List<List<String>> l = new ArrayList<>();
                String vue = null;
                for (ArticleBesoinDto a : as) {
                    String s = texte(a.getSerie());
                    if (!s.isEmpty() && !s.equals(vue)) {
                        l.add(ligne(s + " " + texte(a.getSerieLibelle()), 2 + g.colonnes()));
                        vue = s;
                    }
                }
                if (l.isEmpty()) {
                    vides(l, 6, 3 + g.colonnes());
                } else {
                    l.add(ligne("Repli de chantier", 2 + g.colonnes()));
                }
                grille(doc, List.of("Série / tâche", "Q", "Rendement"), new int[] { 3000, 800, 1100 }, g, l);
                TableauxBesoin.signature(doc);
            }

            // 8-b : la mobilisation, pré-remplie depuis les moyens exigés.
            titre(doc, "MODÈLE 8-b : PLANNING DE MOBILISATION DU PERSONNEL ET DU MATÉRIEL", true);
            enTeteModele(doc, generale, unique);
            List<List<String>> l = new ArrayList<>();
            List<Integer> genres = new ArrayList<>();
            l.add(ligne("Personnel", 1 + generale.colonnes()));
            genres.add(TableauxBesoin.SERIE);
            for (PersonnelExigeDto p : personnel == null ? List.<PersonnelExigeDto>of() : personnel) {
                l.add(ligne("   " + texte(p.getPoste()), 1 + generale.colonnes()));
                genres.add(TableauxBesoin.ARTICLE);
            }
            l.add(ligne("Matériel et véhicules", 1 + generale.colonnes()));
            genres.add(TableauxBesoin.SERIE);
            for (MaterielExigeDto m : materiel == null ? List.<MaterielExigeDto>of() : materiel) {
                l.add(ligne("   " + texte(m.getDesignation()) + (texte(m.getCaracteristique()).isEmpty() ? "" : " (" + texte(m.getCaracteristique())
                        + ")"), 1 + generale.colonnes()));
                genres.add(TableauxBesoin.ARTICLE);
            }
            for (int i = 0; i < 2; i++) {
                l.add(ligne("", 1 + generale.colonnes()));
                genres.add(TableauxBesoin.ARTICLE);
            }
            TableauxBesoin.griser(grille(doc, List.of("Désignation", "Nombre"), new int[] { 3900, 1000 }, generale, l), genres);
            TableauxBesoin.signature(doc);

            // 8-c : l'approvisionnement, liste indicative.
            titre(doc, "MODÈLE 8-c : PLANNING D'APPROVISIONNEMENT DES MATÉRIAUX", true);
            enTeteModele(doc, generale, unique);
            List<List<String>> mat = new ArrayList<>();
            for (String s : List.of("Ciment", "Gravier", "Sable", "Moellons", "Fer à béton", "Bois de coffrage", "")) {
                mat.add(ligne(s, 2 + generale.colonnes()));
            }
            grille(doc, List.of("Désignation des matériaux", "Unité", "Quantité prévisionnelle"), new int[] { 2700, 800, 1400 }, generale, mat);
            TableauxBesoin.signature(doc);

            // 8-d : l'échéancier de paiement, une ligne par période.
            titre(doc, "MODÈLE 8-d : ÉCHÉANCIER DE PAIEMENT", true);
            enTeteModele(doc, generale, unique);
            List<List<String>> ech = new ArrayList<>();
            for (int i = 1; i <= generale.colonnes(); i++) {
                ech.add(List.of(capitale(generale.unite()) + " " + i, "", "", ""));
            }
            XWPFTable e = TableauxBesoin.table(doc, List.of("Période", "Montant prévisionnel des décomptes (Ariary HT)", "Montant cumulé (Ariary HT)",
                    "Pourcentage cumulé du montant du marché"), new int[] { 1800, 2700, 2600, 2312 }, ech);
            TableauxBesoin.ligneTotale(e, 4, "Total", false);
            TableauxBesoin.signature(doc);

            // 8-e : l'intervention pendant le délai de garantie.
            int garantie = garantieMois == null || garantieMois <= 0 ? 12 : garantieMois;
            Grille gg = Grille.de(garantie);
            titre(doc, "MODÈLE 8-e : PLANNING D'INTERVENTION PENDANT LE DÉLAI DE GARANTIE", true);
            corps(doc, "Entreprise : " + POINTILLES + " — Lot n° …… — Délai de garantie prévu au DAO : " + garantie + " mois — une colonne par "
                    + gg.unite() + ".");
            List<List<String>> gar = new ArrayList<>();
            vides(gar, 5, 3 + gg.colonnes());
            grille(doc, List.of("Activités / tâches", "Unité", "Qté"), new int[] { 3000, 800, 1100 }, gg, gar);
            TableauxBesoin.signature(doc);
        });
    }

    /** « Entreprise : … — Lot n° … — Délai maximal prévu au DAO : … — une colonne par mois. » */
    private static void enTeteModele(XWPFDocument doc, Grille g, String delai) {
        corps(doc, "Entreprise : " + POINTILLES + " — Lot n° …… — Délai maximal prévu au DAO : " + (delai == null ? POINTILLES : delai)
                + " — une colonne par " + g.unite() + ".");
    }

    /** Un planning : les colonnes fixes, puis une colonne par période numérotée de 1 à n (marges intérieures réduites). */
    private static XWPFTable grille(XWPFDocument doc, List<String> fixes, int[] largeursFixes, Grille g, List<List<String>> lignes) {
        List<String> entete = new ArrayList<>(fixes);
        int somme = java.util.Arrays.stream(largeursFixes).sum();
        int reste = TableauxBesoin.LARGEUR - somme;
        int[] largeurs = new int[fixes.size() + g.colonnes()];
        System.arraycopy(largeursFixes, 0, largeurs, 0, fixes.size());
        for (int i = 0; i < g.colonnes(); i++) {
            entete.add(String.valueOf(i + 1));
            largeurs[fixes.size() + i] = i == g.colonnes() - 1 ? reste - (reste / g.colonnes()) * (g.colonnes() - 1) : reste / g.colonnes();
        }
        XWPFTable t = TableauxBesoin.table(doc, entete, largeurs, lignes);
        t.setCellMargins(57, 30, 57, 30);
        return t;
    }

    /** Le modèle de plan de charge (DAO routier, p. 101) : les marchés en cours de l'entreprise, ou « NEANT ». */
    public static byte[] planDeCharge() {
        return document(doc -> {
            gras(doc, "Travaux et/ou attributions en cours");
            List<List<String>> l = new ArrayList<>();
            vides(l, 5, 8);
            TableauxBesoin.table(doc, List.of("N°", "Désignation du marché ou du contrat", "Maître d'ouvrage", "Montant des travaux (millions "
                    + "d'Ariary)", "Avancement physique actuel", "Délai", "Début", "Fin"), new int[] { 500, 2300, 1300, 1300, 1200, 900, 956, 956 }, l);
            corps(doc, "Si l'entreprise n'a pas de travaux en cours, indiquer « NEANT ».").setSpacingBefore(120);
            certifie(doc, "Le candidat");
        });
    }

    // ------------------------------------------------------------------ outils

    private interface Contenu {
        void ecrire(XWPFDocument doc);
    }

    private static byte[] document(Contenu contenu) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CharteDao.normalJustifie(doc);   // ⚠️ R3 : l'alignement à gauche des cellules survit à l'insertion
            contenu.ecrire(doc);
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Formulaire des travaux impossible : " + e.getMessage(), e);
        }
    }

    private static List<TableauxBesoin.Lot> lots(TableauxBesoin.Contexte c) {
        return c == null || c.lots() == null || c.lots().isEmpty() ? List.of(new TableauxBesoin.Lot(null, null)) : c.lots();
    }

    private static List<ArticleBesoinDto> articlesDuLot(List<ArticleBesoinDto> articles, TableauxBesoin.Lot lot) {
        return (articles == null ? List.<ArticleBesoinDto>of() : articles).stream().filter(a -> Objects.equals(a.getLot(), lot.numero()))
                .sorted(java.util.Comparator.comparing((ArticleBesoinDto a) -> a.getOrdre() == null ? 0 : a.getOrdre())).toList();
    }

    /** Une ligne dont la première cellule porte le texte, les {@code autres} suivantes vides. */
    private static List<String> ligne(String premier, int autres) {
        List<String> l = new ArrayList<>();
        l.add(premier);
        for (int i = 0; i < autres; i++) {
            l.add("");
        }
        return l;
    }

    private static void vides(List<List<String>> l, int nombre, int colonnes) {
        for (int i = 0; i < nombre; i++) {
            l.add(ligne("", colonnes - 1));
        }
    }

    private static void certifie(XWPFDocument doc, String signataire) {
        corps(doc, CERTIFIE).setSpacingBefore(160);
        TableauxBesoin.paragraphe(doc, "Fait à " + POINTILLES + ", le " + POINTILLES, false, ParagraphAlignment.LEFT);
        TableauxBesoin.paragraphe(doc, signataire, true, ParagraphAlignment.RIGHT);
    }

    private static String capitale(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Un paragraphe du corps : 10,5 pt, justifié, sans lien avec le suivant. */
    private static XWPFParagraph corps(XWPFDocument doc, String s) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        p.setSpacingAfter(100);
        XWPFRun r = p.createRun();
        r.setFontFamily(CharteDao.POLICE);
        r.setFontSize(10.5);
        r.setText(s);
        return p;
    }

    private static XWPFParagraph gras(XWPFDocument doc, String s) {
        XWPFParagraph p = TableauxBesoin.paragraphe(doc, s, true, ParagraphAlignment.LEFT);
        p.getRuns().forEach(r -> r.setFontSize(10.5));
        return p;
    }

    private static void centre(XWPFDocument doc, String s) {
        TableauxBesoin.paragraphe(doc, s, true, ParagraphAlignment.CENTER).getRuns().forEach(r -> r.setFontSize(10.5));
    }

    /** Le titre d'un modèle, centré, en tête de page s'il le faut. */
    private static void titre(XWPFDocument doc, String s, boolean nouvellePage) {
        XWPFParagraph p = TableauxBesoin.paragraphe(doc, s, true, ParagraphAlignment.CENTER);
        p.setPageBreak(nouvellePage);
        p.setSpacingAfter(200);
    }
}
