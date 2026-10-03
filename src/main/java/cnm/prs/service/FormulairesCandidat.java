package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.enums.CategorieDao;
import cnm.prs.enums.TypeChampFiche;

/**
 * ⚠️ <strong>Les formulaires du candidat sur les modèles officiels</strong> (demande front du 2026-09-25, §B8 ; arbitrages du
 * pilote du 26/09, R1-R12) — fiches de renseignements A1 à A4 (une fois pour le dossier) et garanties de soumission C1 /
 * C2 (une par lot), toutes catégories, rendus depuis les <strong>fichiers de commande du décalque</strong>
 * ({@code modeles/candidat/<sigle>.txt}, copiés tels quels du dépôt front — le texte du dossier 2463 au caractère près).
 *
 * <p><strong>Le contrat des jetons</strong>, tous de la forme {@code {{…}}}, traités <em>avant</em> le rendu :</p>
 * <ul>
 *   <li>{@code {{CODE}}} : la valeur de la fiche (saisie, reprise du plan ou reflet), pour le lot du document si le champ
 *       est par lot — montant « 1 600 000 Ariary », date « JJ/MM/AAAA », liste à choix multiples jointe par des virgules ;</li>
 *   <li>{@code {{CODE.lettres}}} : un montant en toutes lettres ({@code MontantEnLettres.ariary}), un nombre en lettres
 *       ({@code NombreEnLettres.cardinal}) ;</li>
 *   <li>{@code {{CODE.doublet}}} : l'ordinal et son abrégé (« cent cinquième (105ème) ») ;</li>
 *   <li>{@code {{DERIVE.delai-garantie.doublet}}} : idem sur {@code B05-GS-04 − B04-VO-01} (« trentième (30ème) »),
 *       protégé par le contrôle {@code VALIDITE_GARANTIE_SUP_OFFRE} ; {@code {{DERIVE.fin-validite-offre}}} :
 *       {@code B04-LR-03 + B04-VO-01} jours, en date (date de remise à défaut : B04-CP-02, puis B04-OV-02) — calculés,
 *       jamais stockés ;</li>
 *   <li>{@code {{A1B.mention}}} : « (non applicable) » quand le cadrage n'autorise pas le groupement, rien sinon — et le
 *       paragraphe est retiré (R9) ;</li>
 *   <li>marqueurs {@code {{SI:A1B}}}…{@code {{FINSI:A1B}}} : les paragraphes entre les deux sont omis sans groupement ;
 *       {@code {{SI:A3B-NATURES}}}…{@code {{FINSI:A3B-NATURES}}} : les lignes de tableau de la plage (de la ligne qui
 *       contient SI à celle qui contient FINSI, R7) sont régénérées, une par nature du marché, déduites de la catégorie
 *       (R8) ; les marqueurs sont toujours retirés ;</li>
 *   <li>un jeton dont la valeur manque s'imprime en <strong>pointillés</strong> « ……… » (R2), pour que le papier reste
 *       remplissable ; un jeton inconnu du contrat est laissé tel quel.</li>
 * </ul>
 *
 * <p>Classe pure : la fiche figée, le référentiel des champs et les modèles lus en entrée, des {@link DocumentLibre} en
 * sortie. Le rendu sans substitution ({@link #brut}) sert au comparateur de fidélité du front.</p>
 */
public final class FormulairesCandidat {

    public static final List<String> FICHES = List.of("A1", "A2", "A3", "A4");
    public static final List<String> GARANTIES = List.of("C1", "C2");
    /**
     * ⚠️ 2026-10-02 (§B5.2) — le modèle d'une garantie selon la catégorie : pour une fiche de travaux, B1 / B2 du dossier
     * type des travaux (renvois aux clauses 6.7 et 10.4 des IC des travaux) ; le type du document reste C1 / C2.
     */
    static final Map<String, String> GARANTIES_TRAVAUX = Map.of("C1", "B1", "C2", "B2");
    public static final String POINTILLES = "………";

    /** Les champs qui commandent les formulaires ou alimentent les dérivés (§B8). */
    static final String FICHES_EXIGEES = "B04-CD-01";
    static final String FORME_GARANTIE = "B04-CD-02";
    static final String VALIDITE_GARANTIE = "B05-GS-04";
    static final String REMISE_OFFRES = "B04-LR-03";
    static final String VALIDITE_OFFRES = "B04-VO-01";
    /** ⚠️ Lot D (2026-09-28) — la date limite de remise du contrat-cadre (date-heure), à défaut de {@link #REMISE_OFFRES}. */
    static final String REMISE_OFFRES_CONTRAT_CADRE = "B04-CP-02";
    /** ⚠️ Lot D4 (2026-09-30, §B6.1) — la date limite de remise des travaux (date-heure), quantité fixe et à commande. */
    static final String REMISE_OFFRES_TRAVAUX = "B04-OV-02";
    /** ⚠️ Lot D (2026-09-28, §B2) — le numéro du lot du document ({@code {{LOT}}}). */
    static final String JETON_LOT = "LOT";
    /** ⚠️ Lot D2 (2026-09-29, §B1) — clés lisibles par les conditions : la forme et la catégorie de la fiche. */
    static final String CLE_TYPE_MARCHE = "typeMarche";
    /** ⚠️ 2026-10-01 (avis spécifique, §B7.5) — clé de condition : les autres supports de publication saisis à l'impression. */
    static final String CLE_SUPPORTS_PUBLICATION = "supportsPublication";
    static final String CLE_CATEGORIE = "categorie";
    /** ⚠️ Lot D2 (§B1) — suffixe d'un champ par lot énuméré dans un document commun : « Lot n° 1 : v1 ; Lot n° 2 : v2 ». */
    static final String SUFFIXE_PAR_LOT = "parLot";
    /** ⚠️ 2026-10-01 (avis spécifique, §B7.3) — un montant par lot, une ligne (un paragraphe) par lot. */
    static final String SUFFIXE_LIGNES_PAR_LOT = "lignesParLot";

    /** ⚠️ 2026-10-02 — l'heure seule d'une date-heure : « 09 h 30 » ; illisible : la valeur telle quelle. */
    static String heure(String brut) {
        LocalDateTime d = RemiseElectronique.dateHeureLue(brut);
        return d == null ? brut : d.format(DateTimeFormatter.ofPattern("HH 'h' mm"));
    }

    /** ⚠️ 2026-10-02 — « le quinzième jour précédant la date limite fixée pour la remise des offres » (AE-T 3). */
    static final int JOURS_AVANT_REMISE_DATE_PRIX = 15;
    /** ⚠️ 2026-10-02 — la garantie de soumission des travaux vaut 30 jours au-delà de la validité des offres (B1 / B2). */
    static final int JOURS_GARANTIE_APRES_OFFRES = 30;

    /** ⚠️ §B7.3 — une date-heure « 12/10/2026 à 09 h 00 (heure locale) » ; illisible : telle quelle. */
    static String heureLocale(String brut) {
        LocalDateTime d = RemiseElectronique.dateHeureLue(brut);
        return d == null ? brut : d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH 'h' mm")) + " (heure locale)";
    }

    /** Les noms de section historiques des formulaires du candidat, admis sans déclaration. */
    public static final java.util.Set<String> SECTIONS_HISTORIQUES = java.util.Set.of("A1B", "A3B-NATURES",
            RemiseElectronique.SECTION);

    /**
     * ⚠️ Lot D (2026-09-28, §B3) — un document type rempli depuis un fichier de commande à <strong>conditions déclarées</strong>
     * ({@link FichierCommande.Modele}) : sections retenues selon la fiche (et le lot), jetons substitués, marqueurs retirés.
     *
     * @param type  type du document ({@code DPAC}, {@code AE})
     * @param lot   rang du lot d'un document établi par lot ; {@code null} : une fois pour le dossier
     */
    public static DocumentLibre rendreModele(String type, Integer lot, FicheMarcheDto fiche, Map<String, ChampFicheMarche> champs,
            FichierCommande.Modele modele, LocalDateTime validation) {
        return rendreModele(type, lot, fiche, champs, modele, validation, Map.of());
    }

    /**
     * ⚠️ Avis spécifique d'appel d'offres (demande front du 2026-09-30, §B2) — avec les <strong>informations de
     * publication</strong> saisies à l'impression, qui ne sont pas des données de la fiche : les jetons
     * {@code {{AVIS.date-publication}}}, {@code {{AVIS.jmp-numero}}}, {@code {{AVIS.jmp-date}}}, {@code {{AVIS.supports}}}
     * se lisent dans {@code publication} (clés sans le préfixe, valeurs déjà mises en forme) ; absents : pointillés.
     */
    public static DocumentLibre rendreModele(String type, Integer lot, FicheMarcheDto fiche, Map<String, ChampFicheMarche> champs,
            FichierCommande.Modele modele, LocalDateTime validation, Map<String, String> publication) {
        Contexte ctx = new Contexte(fiche, champs, modele.conditions(), publication == null ? Map.of() : publication, validation);
        return new DocumentLibre(type, lot, finaliser(ctx.rendre(modele.elements(), lot)), pied(fiche, validation));
    }

    /** ⚠️ 2026-10-01 (avis spécifique, §B7.2) — le repère de numérotation des paragraphes principaux. */
    static final String JETON_NUM = "{{NUM}}";
    /** ⚠️ §B7.8 — un paragraphe qui n'est qu'une image : {@code {{IMAGE:<nom>}}}, lue dans {@code modeles/images/<nom>.png}. */
    private static final Pattern IMAGE = Pattern.compile("^\\{\\{IMAGE:([a-z0-9-]+)}}$");
    /** ⚠️ §B7.8 — largeur d'une image en tête d'un document (l'emblème de l'avis réel : environ 5 cm). */
    static final int LARGEUR_IMAGE_MM = 50;
    /** ⚠️ §B7.3 — séparateur interne d'un jeton qui rend plusieurs lignes ({@code .lignesParLot}) : un paragraphe par ligne. */
    static final char SEPARATEUR_LIGNES = '\u2029';

    /**
     * ⚠️ 2026-10-01 (avis spécifique, §B7.2, §B7.3, §B7.8) — la passe finale d'un document rendu : un paragraphe qui n'est
     * qu'une image devient l'image (repère inconnu : paragraphe omis, jamais imprimé tel quel) ; un paragraphe à plusieurs
     * lignes devient un paragraphe par ligne ; {@code {{NUM}}} est remplacé par « 1. », « 2. »… dans l'ordre des
     * paragraphes <strong>imprimés</strong> (une section omise ne laisse pas de trou).
     */
    static List<DocumentLibre.Element> finaliser(List<DocumentLibre.Element> elements) {
        List<DocumentLibre.Element> out = new ArrayList<>();
        int numero = 0;
        for (DocumentLibre.Element e : elements) {
            if (!(e instanceof DocumentLibre.Paragraphe p)) {
                out.add(e);
                continue;
            }
            Matcher im = IMAGE.matcher(p.texte().trim());
            if (im.matches()) {
                byte[] contenu = image(im.group(1));
                if (contenu != null) {
                    out.add(new DocumentLibre.Image(im.group(1), contenu, LARGEUR_IMAGE_MM));
                }
                continue;
            }
            for (String ligne : p.texte().split(String.valueOf(SEPARATEUR_LIGNES), -1)) {
                String texte = ligne;
                if (texte.startsWith(JETON_NUM)) {
                    texte = (++numero) + "." + texte.substring(JETON_NUM.length());
                }
                out.add(new DocumentLibre.Paragraphe(p.style(), texte));
            }
        }
        return out;
    }

    private static final Map<String, byte[]> IMAGES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Une image de {@code classpath:modeles/images/<nom>.png}, lue une fois ; {@code null} si elle n'existe pas. */
    static byte[] image(String nom) {
        byte[] lu = IMAGES.computeIfAbsent(nom, n -> {
            try (java.io.InputStream in = FormulairesCandidat.class.getResourceAsStream("/modeles/images/" + n + ".png")) {
                return in == null ? new byte[0] : in.readAllBytes();
            } catch (java.io.IOException e) {
                return new byte[0];
            }
        });
        return lu.length == 0 ? null : lu;
    }

    /** ⚠️ Avis spécifique (§B2) — le préfixe des jetons d'information de publication. */
    public static final String PREFIXE_AVIS = "AVIS.";
    /** ⚠️ 2026-10-01 (avis spécifique, §B8.3) — le préfixe des jetons de paramètres de l'application. */
    public static final String PREFIXE_PARAM = "PARAM.";
    /** Le jeton du compte bancaire unique de l'ARMP (§B8). */
    public static final String JETON_COMPTE_DAO = "PARAM.compte-dao";
    /**
     * ⚠️ 2026-10-01 (lot AV-4.1, §B2) — le préfixe des jetons de la lettre d'invitation ({@code LETTRE.lieu},
     * {@code LETTRE.date}, {@code LETTRE.destinataire}, {@code LETTRE.candidats}) : saisis à l'impression, jamais écrits dans
     * la fiche, rendus par l'appelant (clé complète). Plusieurs lignes : séparées par {@link #SEPARATEUR_LIGNES}.
     */
    public static final String PREFIXE_LETTRE = "LETTRE.";
    /**
     * ⚠️ V59 (2026-10-02, DQE des travaux, §B1.5) — le préfixe des jetons tirés du besoin : {@code {{BESOIN.series}}}, une
     * ligne par série du DQE (« 500 — Ouvrages : ……… % »), rendue par l'appelant ({@link #seriesDuBesoin}). Un document
     * établi par lot lit les séries de son lot ({@code BESOIN.series#n}) ; un document commun, celles de la ligne.
     */
    public static final String PREFIXE_BESOIN = "BESOIN.";
    public static final String JETON_SERIES = "BESOIN.series";

    /**
     * Les valeurs du jeton {@code {{BESOIN.series}}} : par lot ({@code BESOIN.series#n}) et pour la ligne
     * ({@code BESOIN.series}). Ligne allotie : la liste commune si tous les lots ont les mêmes séries (le MEN répète le même
     * DQE), sinon une liste par lot, chacune sous « Lot n : ». Sans article de travaux (pas de série) : rien, le jeton
     * s'imprime en pointillés.
     */
    public static Map<String, String> seriesDuBesoin(List<BesoinFiche.Article> articles, int nbLots) {
        Map<String, String> m = new LinkedHashMap<>();
        Map<Integer, List<String>> parLot = new java.util.TreeMap<>(java.util.Comparator.nullsFirst(Integer::compare));
        Map<Integer, Map<String, String>> series = new LinkedHashMap<>();
        for (BesoinFiche.Article a : articles == null ? List.<BesoinFiche.Article>of() : articles) {
            if (a.serie() == null || a.serie().isBlank()) {
                continue;
            }
            Map<String, String> duLot = series.computeIfAbsent(a.lot(), k -> new LinkedHashMap<>());
            String libelle = a.serieLibelle() == null ? "" : a.serieLibelle();
            duLot.merge(a.serie(), libelle, (x, y) -> x.isEmpty() ? y : x);
        }
        series.forEach((lot, duLot) -> parLot.put(lot, duLot.entrySet().stream()
                .map(e -> e.getKey() + (e.getValue().isEmpty() ? "" : " — " + e.getValue()) + " : " + POINTILLES + " %")
                .toList()));
        if (parLot.isEmpty()) {
            return m;
        }
        parLot.forEach((lot, lignes) -> {
            if (lot != null) {
                m.put(JETON_SERIES + "#" + lot, String.join("\n", lignes));
            }
        });
        boolean identiques = parLot.values().stream().distinct().count() == 1;
        if (!LotsFiche.alloti(nbLots) || identiques) {
            m.put(JETON_SERIES, String.join("\n", parLot.values().iterator().next()));
        } else {
            List<String> blocs = new ArrayList<>();
            parLot.forEach((lot, lignes) -> blocs.add("Lot " + lot + " :\n" + String.join("\n", lignes)));
            m.put(JETON_SERIES, String.join("\n", blocs));
        }
        return m;
    }

    private static final Pattern JETON = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Pattern MARQUEUR = Pattern.compile("\\{\\{(SI|FINSI):([A-Z0-9-]+)}}");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Map<String, String> TITRES = Map.of(
            "A1", "Fiche de renseignements A1",
            "A2", "Fiche de renseignements A2",
            "A3", "Fiche de renseignements A3",
            "A4", "Fiche de renseignements A4",
            "C1", "Garantie bancaire de soumission (C1)",
            "C2", "Caution personnelle et solidaire de soumission (C2)");

    private FormulairesCandidat() {
    }

    public static String titre(String type) {
        return TITRES.get(type);
    }

    /**
     * @param fiche      l'état figé de la version
     * @param champs     le référentiel des champs, par code (types, par lot)
     * @param modeles    les éléments de chaque modèle, par sigle ({@link ModelesCandidat})
     * @param validation date de validation (pied de page)
     */
    public static List<DocumentLibre> generer(FicheMarcheDto fiche, Map<String, ChampFicheMarche> champs,
            Map<String, List<DocumentLibre.Element>> modeles, LocalDateTime validation) {
        List<DocumentLibre> documents = new ArrayList<>();
        String pied = pied(fiche, validation);
        Contexte ctx = new Contexte(fiche, champs);
        for (String a : ChampFicheMarche.liste(valeur(fiche, FICHES_EXIGEES, null))) {
            if (FICHES.contains(a) && modeles.containsKey(a)) {
                documents.add(new DocumentLibre(a, null, ctx.rendre(modeles.get(a), null), pied));   // R11 : une fois pour le dossier
            }
        }
        String forme = valeur(fiche, FORME_GARANTIE, null);
        List<String> garanties = forme == null ? List.of()
                : forme.contains("C1") && forme.contains("C2") ? GARANTIES : List.of(forme.trim().toUpperCase());
        int nbLots = Boolean.TRUE.equals(fiche.getSaisieParLot()) && fiche.getNbLots() != null ? fiche.getNbLots() : 0;
        boolean travaux = CategorieDao.TRAVAUX.name().equals(fiche.getCategorie());
        for (String c : garanties) {
            String sigle = travaux && modeles.containsKey(GARANTIES_TRAVAUX.get(c)) ? GARANTIES_TRAVAUX.get(c) : c;
            if (!GARANTIES.contains(c) || !modeles.containsKey(sigle)) {
                continue;
            }
            if (LotsFiche.alloti(nbLots)) {
                for (int lot = 1; lot <= nbLots; lot++) {
                    documents.add(new DocumentLibre(c, lot, ctx.rendre(modeles.get(sigle), lot), pied));
                }
            } else {
                documents.add(new DocumentLibre(c, null, ctx.rendre(modeles.get(sigle), null), pied));
            }
        }
        return documents;
    }

    /** Le modèle tel quel, jetons non substitués : ce que le comparateur de fidélité du front relit. */
    public static DocumentLibre brut(String sigle, List<DocumentLibre.Element> modele) {
        return new DocumentLibre(sigle, null, modele, "Modèle " + sigle + " — rendu brut, jetons non substitués");
    }

    private static String pied(FicheMarcheDto fiche, LocalDateTime validation) {
        return "Plan " + (fiche.getRefeDossier() == null ? "—" : fiche.getRefeDossier()) + " · ligne "
                + fiche.getIdDetail() + " · fiche marché version " + fiche.getVersion()
                + (validation == null ? "" : " validée le " + validation.toLocalDate().format(JOUR));
    }

    // ------------------------------------------------------------------ substitution et marqueurs

    /** Le contexte d'une fiche : ce que valent les conditions, les répétitions et les jetons. */
    /** @param validation ⚠️ 2026-10-02 — la validation de la version rendue ({@code DERIVE.date-dao}) ; {@code null} : rendu brut */
    private record Contexte(FicheMarcheDto fiche, Map<String, ChampFicheMarche> champs, Map<String, String> conditions,
            Map<String, String> publication, LocalDateTime validation) {

        Contexte(FicheMarcheDto fiche, Map<String, ChampFicheMarche> champs) {
            this(fiche, champs, Map.of(), Map.of(), null);
        }

        /**
         * ⚠️ Lot D (2026-09-28, §B1) — la valeur d'une clé de condition : un code de champ (valeur de la fiche figée, celle du
         * lot pour un document de lot) ou une clé de cadrage, lue avec sa réponse par défaut ({@code modeRemise} absent =
         * {@code PAPIER}).
         */
        String lire(String cle, Integer lot) {
            if (cle.matches("B\\d{2}-[A-Z0-9]{1,6}-\\d{2}")) {
                String v = valeur(fiche, cle, lot);
                ChampFicheMarche c = champs.get(cle);
                int nbLots = Boolean.TRUE.equals(fiche.getSaisieParLot()) && fiche.getNbLots() != null ? fiche.getNbLots() : 0;
                if (lot == null && (v == null || v.isBlank()) && c != null && LotsFiche.parLot(c, nbLots)) {
                    // ⚠️ 2026-10-01 (DAO de travaux du MEN) — dans un document commun d'une ligne allotie, un champ saisi par
                    // lot vaut, pour une condition, ses valeurs par lot réunies : « B03-QT-14 renseigne » est vrai dès qu'un
                    // lot porte une liquidité (sans quoi le paragraphe (f) du DPAO-T ne s'imprimait jamais).
                    List<String> parLot = new ArrayList<>();
                    for (int n = 1; n <= nbLots; n++) {
                        String w = valeur(fiche, cle, n);
                        if (w != null && !w.isBlank()) {
                            parLot.add(w);
                        }
                    }
                    return parLot.isEmpty() ? v : String.join(" ; ", parLot);
                }
                return v;
            }
            // ⚠️ Lot D2 (2026-09-29, §B1) — la forme et la catégorie de la fiche se lisent comme des clés de cadrage.
            if (CLE_SUPPORTS_PUBLICATION.equals(cle)) {
                String v = publication.get("supports");   // ⚠️ §B7.5 — « et dans … » seulement s'il y a des supports
                return v == null || v.isBlank() ? null : v;
            }
            if (CLE_TYPE_MARCHE.equals(cle)) {
                return fiche.getTypeMarche();
            }
            if (CLE_CATEGORIE.equals(cle)) {
                return fiche.getCategorie() != null ? fiche.getCategorie() : CategorieDao.FOURNITURES_SERVICES.name();
            }
            Object v = fiche.getCadrage() == null ? null : fiche.getCadrage().get(cle);
            if (v == null && RemiseElectronique.CLE_CADRAGE.equals(cle)) {
                return RemiseElectronique.PAPIER;
            }
            return v == null ? null : String.valueOf(v);
        }

        /**
         * La date limite de remise des offres : B04-LR-03 (fournitures) ; à défaut ⚠️ lot D B04-CP-02 (contrat-cadre,
         * date-heure : sa date) ; à défaut ⚠️ lot D4 B04-OV-02 (travaux, date-heure : sa date).
         */
        LocalDate dateLimiteRemise() {
            LocalDate remise = ControlesFicheMarche.date(valeur(fiche, REMISE_OFFRES, null));
            if (remise == null) {
                remise = ControlesFicheMarche.date(valeur(fiche, REMISE_OFFRES_CONTRAT_CADRE, null));
            }
            if (remise == null) {
                remise = ControlesFicheMarche.date(valeur(fiche, REMISE_OFFRES_TRAVAUX, null));
            }
            return remise;
        }

        boolean groupement() {
            return fiche.getCadrage() != null && "OUI".equalsIgnoreCase(String.valueOf(fiche.getCadrage().get("groupement")));
        }

        /** ⚠️ V50 (2026-09-27) — le cadrage dit que les offres sont remises par voie électronique ({@code modeRemise}). */
        boolean remiseElectronique() {
            return RemiseElectronique.electronique(fiche.getCadrage());
        }

        /**
         * Une condition de section par son nom ; inconnue : vraie (le texte est gardé, les marqueurs retirés).
         * {@code A1B} : le groupement est autorisé ; ⚠️ V50 {@code B04-SE} : la remise est électronique (la clause de C1 / C2).
         */
        boolean condition(String nom, Integer lot) {
            // ⚠️ Lot D (2026-09-28, §B1) — une condition déclarée dans le fichier l'emporte : son expression est évaluée.
            if (conditions.containsKey(nom)) {
                return ConditionsModele.vraie(conditions.get(nom), cle -> lire(cle, lot));
            }
            if ("A1B".equals(nom)) {
                return groupement();
            }
            if (RemiseElectronique.SECTION.equals(nom)) {
                return remiseElectronique();
            }
            return true;
        }

        /** Les valeurs d'une plage régénérée par son nom ; {@code null} si ce n'est pas une répétition. */
        List<String> repetition(String nom) {
            if (!"A3B-NATURES".equals(nom)) {
                return null;
            }
            String categorie = fiche.getCategorie();
            if (CategorieDao.TRAVAUX.name().equals(categorie)) {
                return List.of("Travaux");
            }
            if (CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie)) {
                return List.of("Prestations intellectuelles");
            }
            return List.of("Fournitures", "Services");
        }

        List<DocumentLibre.Element> rendre(List<DocumentLibre.Element> modele, Integer lot) {
            List<DocumentLibre.Element> out = new ArrayList<>();
            // ⚠️ Lot D (2026-09-28, §B1) — une PILE de sections : une section fausse omet tout jusqu'à son FINSI, sections
            // internes comprises (elles ne sont pas même évaluées).
            java.util.Deque<Boolean> pile = new java.util.ArrayDeque<>();
            int omises = 0;
            for (DocumentLibre.Element e : modele) {
                if (e instanceof DocumentLibre.Paragraphe p) {
                    Matcher m = MARQUEUR.matcher(p.texte().trim());
                    if (m.matches() && m.group(0).equals(p.texte().trim())) {
                        String nom = m.group(2);
                        if ("SI".equals(m.group(1))) {
                            boolean vraie = omises == 0 && condition(nom, lot);
                            pile.push(vraie);
                            omises += vraie ? 0 : 1;
                        } else if (!pile.isEmpty()) {
                            omises -= pile.pop() ? 0 : 1;
                        }
                        continue;   // un marqueur n'est jamais imprimé
                    }
                    if (omises > 0) {
                        continue;
                    }
                    String texte = substituer(p.texte(), lot);
                    if (texte == null) {
                        continue;   // R9 : un paragraphe fait d'une seule mention vide est retiré
                    }
                    out.add(new DocumentLibre.Paragraphe(p.style(), texte));
                } else if (e instanceof DocumentLibre.Tableau t) {
                    if (omises > 0) {
                        continue;
                    }
                    out.add(new DocumentLibre.Tableau(t.colonnes(), lignes(t, lot)));
                }
            }
            return out;
        }

        /** Les lignes d'un tableau : plages régénérées ou conditionnelles, puis substitution cellule par cellule. */
        private List<List<List<String>>> lignes(DocumentLibre.Tableau t, Integer lot) {
            List<List<List<String>>> out = new ArrayList<>();
            // ⚠️ Lot D2 (2026-09-29, §B1.2) — une rangée-marqueur (première cellule exactement {{SI:X}} / {{FINSI:X}}, les
            // autres vides) ouvre ou ferme une section de RANGÉES ; elle ne s'imprime jamais. Même pile que les paragraphes :
            // une section fausse omet ses rangées, sections internes comprises.
            java.util.Deque<Boolean> pile = new java.util.ArrayDeque<>();
            int omises = 0;
            int i = 0;
            while (i < t.lignes().size()) {
                List<List<String>> ligne = t.lignes().get(i);
                Matcher rangee = marqueurDeRangee(ligne);
                if (rangee != null) {
                    if ("SI".equals(rangee.group(1))) {
                        boolean vraie = omises == 0 && condition(rangee.group(2), lot);
                        pile.push(vraie);
                        omises += vraie ? 0 : 1;
                    } else if (!pile.isEmpty()) {
                        omises -= pile.pop() ? 0 : 1;
                    }
                    i++;
                    continue;
                }
                if (omises > 0) {
                    i++;
                    continue;
                }
                String nom = marqueur(ligne, "SI");
                if (nom == null) {
                    out.add(substituer(ligne, lot));
                    i++;
                    continue;
                }
                int fin = i;
                while (fin < t.lignes().size() && !nom.equals(marqueur(t.lignes().get(fin), "FINSI"))) {
                    fin++;
                }
                fin = Math.min(fin, t.lignes().size() - 1);
                List<String> valeurs = repetition(nom);
                if (valeurs != null) {
                    List<List<String>> gabarit = sansMarqueurs(t.lignes().get(i));
                    for (String v : valeurs) {
                        List<List<String>> copie = new ArrayList<>();
                        for (int c = 0; c < gabarit.size(); c++) {
                            copie.add(new ArrayList<>(gabarit.get(c)));
                        }
                        if (!copie.isEmpty() && !copie.get(0).isEmpty()) {
                            copie.get(0).set(0, v);
                        }
                        out.add(substituer(copie, lot));
                    }
                } else if (condition(nom, lot)) {
                    for (int k = i; k <= fin; k++) {
                        out.add(substituer(sansMarqueurs(t.lignes().get(k)), lot));
                    }
                }
                i = fin + 1;
            }
            return out;
        }

        /** ⚠️ Lot D2 — la rangée est-elle un marqueur de rangée ? Le marqueur, ou {@code null}. */
        static Matcher marqueurDeRangee(List<List<String>> ligne) {
            if (ligne.isEmpty() || ligne.get(0).size() != 1) {
                return null;
            }
            Matcher m = MARQUEUR.matcher(ligne.get(0).get(0).trim());
            if (!m.matches()) {
                return null;
            }
            for (int c = 1; c < ligne.size(); c++) {
                for (String p : ligne.get(c)) {
                    if (!p.isBlank()) {
                        return null;
                    }
                }
            }
            return m;
        }

        /**
         * Un marqueur de plage historique ({@code A3B-NATURES}) : collé au texte d'une cellule. ⚠️ Lot D2 — un paragraphe de
         * cellule qui n'est QUE le marqueur est une section interne à la cellule ({@link #substituer(List, Integer)}), pas
         * une plage de rangées.
         */
        private static String marqueur(List<List<String>> ligne, String sorte) {
            for (List<String> cellule : ligne) {
                for (String paragraphe : cellule) {
                    if (MARQUEUR.matcher(paragraphe.trim()).matches()) {
                        continue;
                    }
                    Matcher m = MARQUEUR.matcher(paragraphe);
                    while (m.find()) {
                        if (sorte.equals(m.group(1))) {
                            return m.group(2);
                        }
                    }
                }
            }
            return null;
        }

        private static List<List<String>> sansMarqueurs(List<List<String>> ligne) {
            List<List<String>> out = new ArrayList<>();
            for (List<String> cellule : ligne) {
                List<String> c = new ArrayList<>();
                for (String paragraphe : cellule) {
                    c.add(MARQUEUR.matcher(paragraphe).replaceAll(""));
                }
                out.add(c);
            }
            return out;
        }

        private List<List<String>> substituer(List<List<String>> ligne, Integer lot) {
            List<List<String>> out = new ArrayList<>();
            for (List<String> cellule : ligne) {
                List<String> c = new ArrayList<>();
                // ⚠️ Lot D2 (2026-09-29, §B1.1) — un paragraphe de cellule qui n'est QUE {{SI:X}} / {{FINSI:X}} ouvre ou ferme
                // une section interne à la cellule : même évaluation, jamais imprimé.
                java.util.Deque<Boolean> pile = new java.util.ArrayDeque<>();
                int omises = 0;
                boolean marques = false;
                for (String paragraphe : cellule) {
                    Matcher m = MARQUEUR.matcher(paragraphe.trim());
                    if (m.matches()) {
                        marques = true;
                        if ("SI".equals(m.group(1))) {
                            boolean vraie = omises == 0 && condition(m.group(2), lot);
                            pile.push(vraie);
                            omises += vraie ? 0 : 1;
                        } else if (!pile.isEmpty()) {
                            omises -= pile.pop() ? 0 : 1;
                        }
                        continue;
                    }
                    if (omises > 0) {
                        continue;
                    }
                    String s = substituer(paragraphe, lot);
                    c.add(s == null ? "" : s);
                }
                if (c.isEmpty() && marques) {
                    c.add("");   // la cellule garde sa place dans la rangée
                }
                out.add(c);
            }
            return out;
        }

        /** Le texte substitué ; {@code null} si le texte n'était qu'un jeton qui vaut la chaîne vide (mention). */
        private String substituer(String texte, Integer lot) {
            Matcher m = JETON.matcher(texte);
            StringBuilder sb = new StringBuilder();
            boolean seul = m.matches();
            m.reset();
            while (m.find()) {
                String v = jeton(m.group(1).trim(), lot);
                if (seul && v != null && v.isEmpty()) {
                    return null;
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group(0) : v));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        /** La valeur d'un jeton ; {@code null} : jeton inconnu du contrat (laissé tel quel). */
        private String jeton(String nom, Integer lot) {
            if (nom.startsWith(RemiseElectronique.PREFIXE_JETON_INTERNE)) {
                return null;   // ⚠️ V50 (§B2.2) — un paramètre interne de la procédure n'entre dans aucun document : laissé tel quel
            }
            if (nom.startsWith(PREFIXE_PARAM) || nom.startsWith(PREFIXE_LETTRE)) {
                // ⚠️ 2026-10-01 (§B8.3) — un paramètre de l'application rendu par l'appelant ({@code PARAM.compte-dao}).
                String v = publication.get(nom);
                return v == null || v.isBlank() ? POINTILLES : v;
            }
            if (nom.startsWith(PREFIXE_BESOIN)) {
                // ⚠️ V59 (§B1.5) — tiré du besoin par l'appelant : celui du lot pour un document par lot, sinon de la ligne.
                String v = lot == null ? null : publication.get(nom + "#" + lot);
                v = v != null ? v : publication.get(nom);
                return v == null || v.isBlank() ? POINTILLES : v;
            }
            if (nom.startsWith(PREFIXE_AVIS)) {
                // ⚠️ Avis spécifique (§B2) — une information de publication, saisie à l'impression, jamais stockée dans la fiche.
                String v = publication.get(nom.substring(PREFIXE_AVIS.length()));
                return v == null || v.isBlank() ? POINTILLES : v;
            }
            if (JETON_LOT.equals(nom)) {
                return lot == null ? "" : String.valueOf(lot);   // ⚠️ Lot D (2026-09-28, §B2) — le numéro du lot, vide hors lot
            }
            if ("A1B.mention".equals(nom)) {
                return groupement() ? "" : "(non applicable)";
            }
            if ("DERIVE.delai-garantie.doublet".equals(nom)) {
                BigDecimal g = ControlesFicheMarche.nombre(valeur(fiche, VALIDITE_GARANTIE, null));
                BigDecimal o = ControlesFicheMarche.nombre(valeur(fiche, VALIDITE_OFFRES, null));
                return g == null || o == null || g.subtract(o).signum() <= 0 ? POINTILLES
                        : NombreEnLettres.doublet(g.subtract(o).longValue(), false);
            }
            if ("DERIVE.fin-validite-offre".equals(nom)) {
                LocalDate remise = dateLimiteRemise();
                BigDecimal jours = ControlesFicheMarche.nombre(valeur(fiche, VALIDITE_OFFRES, null));
                return remise == null || jours == null ? POINTILLES : remise.plusDays(jours.longValue()).format(JOUR);
            }
            if ("DERIVE.validite-garantie".equals(nom)) {
                // ⚠️ 2026-10-02 (travaux routiers, §B5, B1 / B2) — « [durée de validité des offres + 30 jours] » : les travaux
                // n'ont pas de champ de validité de la garantie ; un nombre de jours (« soit jusqu'au 150 ème jour »).
                BigDecimal jours = ControlesFicheMarche.nombre(valeur(fiche, VALIDITE_OFFRES, null));
                return jours == null ? POINTILLES : String.valueOf(jours.longValue() + JOURS_GARANTIE_APRES_OFFRES);
            }
            if ("DERIVE.date-prix".equals(nom)) {
                // ⚠️ 2026-10-02 (recette du DAO du MEN, §B3.1) — « le quinzième jour précédant la date limite fixée pour la
                // remise des offres, soit le … » (AE-T 3).
                LocalDate remise = dateLimiteRemise();
                return remise == null ? POINTILLES : remise.minusDays(JOURS_AVANT_REMISE_DATE_PRIX).format(JOUR);
            }
            if ("DERIVE.date-dao".equals(nom)) {
                // ⚠️ 2026-10-02 (§B3.1) — « Dossier d'Appel d'Offres N° … du <date> » : la date de la validation de la
                // version, celle où le DAO est établi et figé ; rendu brut (sans validation) : pointillés.
                return validation == null ? POINTILLES : validation.toLocalDate().format(JOUR);
            }
            if (nom.startsWith("DERIVE.") || nom.startsWith("SI:") || nom.startsWith("FINSI:")) {
                return null;
            }
            int point = nom.indexOf('.');
            String code = point < 0 ? nom : nom.substring(0, point);
            String suffixe = point < 0 ? "" : nom.substring(point + 1);
            ChampFicheMarche c = champs.get(code);
            if (c == null && !code.matches("B\\d{2}-[A-Z0-9]{1,6}-\\d{2}")) {
                return null;
            }
            if (SUFFIXE_PAR_LOT.equals(suffixe)) {
                return parLot(code, c, lot);
            }
            if (SUFFIXE_LIGNES_PAR_LOT.equals(suffixe)) {
                return lignesParLot(code, c, lot);
            }
            String brut = valeur(fiche, code, c != null && Boolean.TRUE.equals(c.getParLot()) ? lot : null);
            if (brut == null && c != null && c.getCleCadrage() != null && !c.getCleCadrage().isBlank()) {
                // ⚠️ Lot D4 (2026-09-29, §B3) — un reflet que le modèle cite hors de sa catégorie (le contrat-cadre de travaux
                // cite B02-LV-05, reflet des fournitures) : sa valeur est celle de la clé de cadrage qu'il reflète.
                brut = lire(c.getCleCadrage(), lot);
            }
            if (brut == null) {
                return POINTILLES;
            }
            String type = c == null ? TypeChampFiche.TEXTE.name() : c.getType();
            if (suffixe.isEmpty() && (RemiseElectronique.CHAMP_MODE.equals(code)
                    || c != null && RemiseElectronique.CLE_CADRAGE.equals(c.getCleCadrage()))) {
                return RemiseElectronique.libelleMode(brut);   // ⚠️ V50 (§B2.3) — « Papier » / « Électronique »
            }
            BigDecimal n = ControlesFicheMarche.nombre(brut);
            return switch (suffixe) {
                case "lettres" -> TypeChampFiche.MONTANT.name().equals(type) && n != null ? MontantEnLettres.ariary(n)
                        : n != null && n.stripTrailingZeros().scale() <= 0 ? NombreEnLettres.cardinal(n.longValue()) : brut;
                case "doublet" -> n != null && n.signum() > 0 ? NombreEnLettres.doublet(n.longValue(), false) : POINTILLES;
                // ⚠️ 2026-09-27 (documents types ARMP) — le nombre en chiffres SANS l'unité, pour un gabarit qui écrit lui-même
                // « Ariary » après le montant (« pour la somme de … ({{B05-GS-03.chiffres}} Ariary) »).
                case "chiffres" -> n != null ? ValeursPpmService.montant(n) : brut;
                case "heureLocale" -> heureLocale(brut);   // ⚠️ 2026-10-01 (§B7.3)
                case "heure" -> heure(brut);   // ⚠️ 2026-10-02 (recette du DAO du MEN, §B3.1) — « 09 h 30 »
                case "" -> affichage(type, brut, n);
                default -> null;
            };
        }

        /**
         * ⚠️ 2026-10-01 (avis spécifique, §B7.3) — un montant par lot, une ligne par lot : « - Lot 1 : cent mille ariary
         * (Ar 100 000) » ; hors allotissement (ou dans un document de lot), la ligne seule, sans le lot.
         */
        private String lignesParLot(String code, ChampFicheMarche c, Integer lot) {
            int nbLots = Boolean.TRUE.equals(fiche.getSaisieParLot()) && fiche.getNbLots() != null ? fiche.getNbLots() : 0;
            if (lot != null || c == null || !LotsFiche.parLot(c, nbLots)) {
                return "- " + jeton(code + ".lettres", lot) + " (Ar " + jeton(code + ".chiffres", lot) + ")";
            }
            List<String> lignes = new ArrayList<>();
            for (int n = 1; n <= nbLots; n++) {
                lignes.add("- Lot " + n + " : " + jeton(code + ".lettres", n) + " (Ar " + jeton(code + ".chiffres", n) + ")");
            }
            return String.join(String.valueOf(SEPARATEUR_LIGNES), lignes);
        }

        /**
         * ⚠️ Lot D2 (2026-09-29, §B1.3) — {@code {{CODE.parLot}}} : dans un document COMMUN d'une ligne allotie, la valeur
         * de chaque lot formatée comme {@code {{CODE}}}, énumérée « Lot n° 1 : v1 ; Lot n° 2 : v2 » (pointillés pour un lot
         * sans valeur) ; sur une ligne non allotie, un champ qui n'est pas par lot, ou dans un document de lot : la valeur
         * seule, comme {@code {{CODE}}}.
         */
        private String parLot(String code, ChampFicheMarche c, Integer lot) {
            int nbLots = Boolean.TRUE.equals(fiche.getSaisieParLot()) && fiche.getNbLots() != null ? fiche.getNbLots() : 0;
            if (lot != null || c == null || !LotsFiche.parLot(c, nbLots)) {
                return jeton(code, lot);
            }
            List<String> parts = new ArrayList<>();
            for (int n = 1; n <= nbLots; n++) {
                parts.add("Lot n° " + n + " : " + jeton(code, n));
            }
            return String.join(" ; ", parts);
        }

        /** La valeur d'un champ, selon son type : montant en chiffres avec l'unité, date JJ/MM/AAAA, Oui/Non, listes. */
        private static String affichage(String type, String brut, BigDecimal n) {
            if (TypeChampFiche.MONTANT.name().equals(type) && n != null) {
                return ValeursPpmService.montant(n) + " Ariary";
            }
            if (TypeChampFiche.DATE.name().equals(type)) {
                LocalDate d = ControlesFicheMarche.date(brut);
                return d == null ? brut : d.format(JOUR);
            }
            if (TypeChampFiche.DATE_HEURE.name().equals(type)) {   // ⚠️ V50 (§B2.3) — JJ/MM/AAAA HH:MM
                LocalDateTime d = RemiseElectronique.dateHeureLue(brut);
                return d == null ? brut : d.format(RemiseElectronique.AFFICHAGE);
            }
            if (TypeChampFiche.OUI_NON.name().equals(type)) {
                return "OUI".equalsIgnoreCase(brut) ? "Oui" : "NON".equalsIgnoreCase(brut) ? "Non" : brut;
            }
            if (TypeChampFiche.LISTE_MULTIPLE.name().equals(type)) {
                return String.join(", ", ChampFicheMarche.liste(brut));
            }
            if (TypeChampFiche.POURCENTAGE.name().equals(type)) {
                return brut.endsWith("%") ? brut : brut + " %";
            }
            // ⚠️ Lot D3 (2026-09-29, §B2.1) — un nombre décimal s'imprime avec la virgule (« 0,8 », poids T et F).
            if (TypeChampFiche.NOMBRE.name().equals(type) && brut.matches("-?\\d+\\.\\d+")) {
                return brut.replace('.', ',');
            }
            return brut;
        }
    }

    /** La valeur d'un champ (saisie, reprise du plan, reflet) ; pour un lot, {@code CODE#n} puis le code nu. */
    static String valeur(FicheMarcheDto fiche, String code, Integer lot) {
        for (Map<String, String> m : java.util.Arrays.asList(fiche.getValeurs(), fiche.getValeursPpm(), fiche.getValeursCadrage())) {
            if (m == null) {
                continue;
            }
            String v = lot == null ? null : m.get(LotsFiche.cle(code, lot));
            v = v != null ? v : m.get(code);
            if (v != null && !v.isBlank() && !"null".equalsIgnoreCase(v.trim())) {
                return v.trim();
            }
        }
        return null;
    }

    /** Fabrique de résolveurs pour les tests : inutilisée en production. */
    static Function<String, String> resolveur(FicheMarcheDto fiche, Map<String, ChampFicheMarche> champs, Integer lot) {
        Contexte ctx = new Contexte(fiche, champs);
        return nom -> ctx.jeton(nom, lot);
    }
}
