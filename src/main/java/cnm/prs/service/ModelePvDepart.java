package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cnm.prs.dto.ModelePvDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * ⚠️ 2026-10-21 (demande front « modele-pv-depart ») — le <strong>modèle de départ</strong> d'un PV, assemblé par l'application à
 * partir du PV officiel du plan avec réserves ({@code PV_AFSR_PPM_*}) : l'application <strong>assemble</strong>, elle ne rédige pas.
 * Les phrases de chaque famille sont écrites ici une fois (Q1), à faire valider par la CNM ; le texte juridique de la base (formule
 * « L'an … », signatures, annexe) est repris tel quel.
 *
 * <p>⚠️ 2026-10-21 (demande front « modele-pv-page », §B5) — le départ est désormais une <strong>page</strong> ({@link ModelePvPage}),
 * plus un Word : la page officielle est reconstruite ici (en-tête et emblème, identification, « L'an … », présents, avis, signatures,
 * annexe), avec le même contenu que le Word du matin. Les marges de la base (7,5 mm à gauche, aucune à droite) et ses retraits de
 * paragraphe (17,5 mm, 25 mm) se résument à des marges de page de 25 mm.</p>
 *
 * <p>Ce que fait l'assemblage : titre et intitulé de la famille, « L'an » en lettres, avis en trois blocs (« AVIS FAVORABLE » en
 * gras), observation générale saisie, annexe sous {@code AVEC_RESERVES} (saut de page compris) avec sa ligne répétée
 * {@code OBSERVATIONS} ; pour {@code les-deux}, les écarts entre les PV centrale et régionale en blocs {@code CENTRALE} /
 * {@code REGIONALE} (en ligne quand l'écart est un mot) ; pour le dossier de marché, le tableau des titulaires proposés et l'écart à
 * l'estimation. Pur : aucune base.</p>
 *
 * <p>⚠️ 2026-10-22 (demande front « modele-pv-plan-depart », B1) — le <strong>plan</strong> (famille DDP) a son départ, recopié
 * des 12 fichiers intégrés {@code PV_{AF|AFSR|ANF}_{PPM|PPMAGPM}_{CENTRALE|REGIONALE}} : leurs textes ne diffèrent que par la phrase
 * d'avis (et l'annexe des réserves), la mention de l'AGPM (intitulé et avis, bloc {@code AVEC_AGPM}) et la commission. Le texte est
 * repris tel quel, même là où il s'écarte des autres départs (titre « PROCES-VERBAL DE LA COMMISSION … », avis défavorable sans motif,
 * pas d'observation générale) : la comparaison avec l'intégré (lot D2) doit rester honnête. Comme le générateur intégré, les lignes
 * Président, Chef de la Commission et Membre ne s'impriment que sous un nom ({@code PV.PRESIDENT renseigne}…), et « INITIAL » est la
 * nature du plan ({@code DOSSIER.NATURE_PLAN}).</p>
 */
public final class ModelePvDepart {

    public static final String CENTRALE = "centrale";
    public static final String REGIONALE = "regionale";
    public static final String LES_DEUX = "les-deux";
    public static final List<String> PORTEES = List.of(CENTRALE, REGIONALE, LES_DEUX);

    static final String MOTIF = "Modèle de départ généré par l'application — texte à faire valider par la CNM";
    private static final String BASE_CENTRALE = "PV_AFSR_PPM_CENTRALE.docx";
    private static final String BASE_REGIONALE = "PV_AFSR_PPM_REGIONALE.docx";
    private static final String LETTRE_CENTRALE = "LR_CENTRALE.docx";
    private static final String LETTRE_REGIONALE = "LR_REGIONALE.docx";
    private static final String POINTILLES = "………………………………..";

    /** Les phrases d'une famille : complément du titre, intitulé du dossier, ce sur quoi porte l'avis, titulaires proposés. */
    public record Famille(String code, String titre, String intitule, String cible, boolean titulaires) {
    }

    /** La page assemblée, les fichiers officiels qu'elle reconstruit, et ce qui se déclare d'office avec elle (§B4). */
    public record Depart(JsonNode document, List<String> bases, List<ModelePvDto.Condition> conditions, List<ModelePvDto.Champ> champs) {
    }

    /**
     * §B3 — propositions du front, articles ajoutés au titre (« DE L'ACTE », « DE LA DÉCLARATION »). ⚠️ 2026-10-22 — le plan (DDP) :
     * ses phrases sont celles des fichiers intégrés, la commission et l'AGPM s'y écrivent en blocs (voir {@link #assembler}).
     */
    public static final Map<String, Famille> FAMILLES = Map.of(
            CatalogueModelesPv.PLAN, new Famille(CatalogueModelesPv.PLAN, "DE LA COMMISSION",
                    "Projet de Plan de Passation des Marchés {{DOSSIER.NATURE_PLAN}}{{SI:AVEC_AGPM}} et d’Avis Général de Passation des "
                            + "Marchés {{DOSSIER.NATURE_PLAN}}{{FINSI:AVEC_AGPM}} pour l’année {{DOSSIER.EXERCICE}}",
                    "à l’affichage du PPM{{SI:AVEC_AGPM}} et à la publication de l’AGPM{{FINSI:AVEC_AGPM}}", false),
            "DMC", new Famille("DMC", "DU DOSSIER DE MISE EN CONCURRENCE",
                    "{{DOSSIER.SOUS_TYPE}} n° {{DOSSIER.REFERENCE}} — {{PROCEDURE.OBJET}} (procédure {{PROCEDURE.NUMERO}}, "
                            + "{{PROCEDURE.MODE}}, lot {{PROCEDURE.LOT}})",
                    "au dossier de mise en concurrence", false),
            "DDM", new Famille("DDM", "DU DOSSIER DE MARCHÉ",
                    "{{DOSSIER.SOUS_TYPE}} n° {{DOSSIER.REFERENCE}} — {{PROCEDURE.OBJET}} (procédure {{PROCEDURE.NUMERO}}, "
                            + "lot {{PROCEDURE.LOT}}), issu du dossier de mise en concurrence {{MARCHE.DOSSIER_MEC}}",
                    "à l’attribution du marché", true),
            "DGC", new Famille("DGC", "DE L’ACTE DE GESTION CONTRACTUELLE", "{{DOSSIER.SOUS_TYPE}} n° {{DOSSIER.REFERENCE}}",
                    "à l’acte de gestion", false),
            "DSS", new Famille("DSS", "DE LA DÉCLARATION SANS SUITE", "{{DOSSIER.SOUS_TYPE}} n° {{DOSSIER.REFERENCE}}",
                    "à la déclaration sans suite", false));

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private ModelePvDepart() {
    }

    /**
     * Assemble la page de départ d'une famille ({@link #FAMILLES}) pour une portée ({@link #PORTEES}) ; {@code idEmbleme} est
     * l'image de l'emblème (fournie par l'application).
     */
    public static Depart assembler(Famille f, String portee, long idEmbleme) {
        boolean deux = LES_DEUX.equals(portee);
        boolean regionale = REGIONALE.equals(portee);
        boolean plan = CatalogueModelesPv.PLAN.equals(f.code());
        String commission = deux ? "{{SI:CENTRALE}}Centrale{{FINSI:CENTRALE}}{{SI:REGIONALE}}Régionale{{FINSI:REGIONALE}}"
                : regionale ? "Régionale" : "Centrale";
        ArrayNode c = F.arrayNode();

        // En-tête officiel : l'emblème, le ministère, la commission (centrale ou régionale, avec sa localité).
        ObjectNode emb = ModelePvPage.noeud("image");
        emb.putObject("attrs").put("idImage", idEmbleme).put("largeur", 51.6).put("alignement", "center");
        c.add(emb);
        c.add(p("MINISTERE DE L’ECONOMIE ET DES FINANCES", attrs("center", null, 60.0), null));
        c.add(p(POINTILLES, attrs(null, 12.0, null), null));
        c.add(p("COMMISSION NATIONALE DES MARCHES", attrs("center", null, 60.0), null));
        c.add(p(POINTILLES, attrs(null, 12.0, null), null));
        c.add(p(deux ? "COMMISSION {{SI:CENTRALE}}CENTRALE DES MARCHES….{{FINSI:CENTRALE}}{{SI:REGIONALE}}REGIONALE DES MARCHES"
                + "{{FINSI:REGIONALE}}" : regionale ? "COMMISSION REGIONALE DES MARCHES" : "COMMISSION CENTRALE DES MARCHES….", null, null));
        c.add(p(POINTILLES, attrs(null, 12.0, null), null));
        if (deux || regionale) {
            List<ObjectNode> localite = List.of(p("{{COMMISSION.LOCALITE}}", attrs(null, 12.0, null), null),
                    p(POINTILLES, attrs(null, 12.0, null), null));
            if (deux) {
                c.add(bloc("REGIONALE", localite));
            } else {
                localite.forEach(c::add);
            }
        }
        c.add(vide());

        // Titre et identification.
        // ⚠️ 2026-10-22 — le titre du plan nomme la commission, comme ses fichiers intégrés.
        String titre = !plan ? "PROCÈS-VERBAL D’EXAMEN " + f.titre()
                : "PROCES-VERBAL " + f.titre() + (deux ? " {{SI:CENTRALE}}CENTRALE…{{FINSI:CENTRALE}}{{SI:REGIONALE}}REGIONALE{{FINSI:REGIONALE}}"
                        : regionale ? " REGIONALE" : " CENTRALE…");
        if (plan) {
            // ⚠️ 2026-10-22 — la mise en forme des fichiers du plan : titre en Arial 12 gras, « Séance du » gras souligné, libellés
            // de l'identification en gras et valeurs en maigre.
            ArrayNode titreArial = gras();
            ObjectNode arial = ModelePvPage.noeud("textStyle");
            arial.putObject("attrs").put("fontFamily", "Arial").put("fontSize", "12pt");
            titreArial.add(arial);
            ArrayNode seance = gras();
            seance.add(ModelePvPage.noeud("underline"));
            c.add(p(titre, attrs("center", null, null), titreArial));
            c.add(p("Séance du {{PV.DATE_EXAMEN}}", attrs("center", null, null), seance));
            c.add(vide());
            c.add(libelle("PV N° : ", "{{PV.REFERENCE}}"));
            c.add(libelle("DOSSIER DEPOSE LE : ", "{{DOSSIER.DATE_RECEPTION}}"));
            c.add(libelle("ORGANISME CONCERNE : ", "{{DOSSIER.ENTITE}}"));
            c.add(libelle("NATURE ET INTITULE DU DOSSIER : ", f.intitule()));
        } else {
            c.add(p(titre, attrs("center", null, null), marques(true, 12)));
            c.add(p("Séance du {{PV.DATE_EXAMEN}}", attrs("center", null, null), marques(false, 12)));
            c.add(vide());
            c.add(p("PV N° : {{PV.REFERENCE}}", null, gras()));
            c.add(p("DOSSIER DEPOSE LE : {{DOSSIER.DATE_RECEPTION}}", null, gras()));
            c.add(p("ORGANISME CONCERNE : {{DOSSIER.ENTITE}}", null, gras()));
            c.add(p("NATURE ET INTITULE DU DOSSIER : " + f.intitule(), null, gras()));
        }
        c.add(vide());
        c.add(p("L’an {{PV.DATE_EXAMEN|lettres}}, la Commission " + commission + " des Marchés, instituée par la loi N°2016-055 du "
                + "25 janvier 2017 portant Code des Marchés Publics et le décret N°219-093 du 13 février 2019, fixant les attributions "
                + "du Ministre de l’Economie et des Finances ainsi que l’organisation générale de son ministère, a statué sur le dossier "
                + "soumis à son contrôle a priori.", attrs("justify", null, null), null));
        c.add(vide());

        // Présents.
        c.add(p("Etaient présents :", null, gras()));
        ObjectNode president = p("Président de la Commission Nationale des Marchés : {{PV.PRESIDENT}}", null, null);
        ObjectNode chef = p("Chef de la Commission : {{PV.CHEF_COMMISSION}}", null, null);
        ObjectNode membre = p("Membre de la Commission : {{PV.MEMBRE}}", null, null);
        if (plan) {
            // ⚠️ 2026-10-22 — comme le générateur intégré : une ligne ne s'imprime que sous un nom (qui a signé).
            c.add(bloc("PRESIDENT", List.of(president)));
            c.add(bloc("CHEF_COMMISSION", List.of(chef)));
            c.add(bloc("MEMBRE", List.of(membre)));
        } else {
            c.add(president);
            c.add(chef);
            c.add(membre);
        }
        c.add(vide());

        // L'avis, en trois blocs ; l'observation générale ; les titulaires du marché.
        c.add(p("AVIS DE LA COMMISSION :", null, gras()));
        c.add(vide());
        String emet = "La Commission " + commission + " des Marchés émet un ";
        if (plan) {
            // ⚠️ 2026-10-22 — les phrases des fichiers intégrés, telles quelles : pas de motif au défavorable, pas d'observation générale.
            c.add(bloc("FAVORABLE", List.of(avis(emet, "AVIS FAVORABLE", " " + f.cible() + "."))));
            c.add(bloc("AVEC_RESERVES", List.of(avis(emet, "AVIS FAVORABLE", " " + f.cible()
                    + " sous réserve qu’il soit tenu compte des observations portées en annexe."))));
            c.add(bloc("DEFAVORABLE", List.of(avis(emet, "AVIS NON FAVORABLE", " " + f.cible() + "."))));
        } else {
            c.add(bloc("FAVORABLE", List.of(avis(emet, "AVIS FAVORABLE", " " + f.cible() + "."))));
            c.add(bloc("AVEC_RESERVES", List.of(avis(emet, "AVIS FAVORABLE", " " + f.cible()
                    + ", sous réserve qu’il soit tenu compte des observations portées en annexe."))));
            c.add(bloc("DEFAVORABLE", List.of(avis(emet, "AVIS NON FAVORABLE", " " + f.cible()
                    + ", pour le motif suivant : {{SAISI:motif_defavorable}}."))));
            c.add(bloc("OBS_GENERALE", List.of(p("Observations générales de la Commission : {{SAISI:observation_generale}}",
                    attrs("justify", null, null), null))));
        }
        if (f.titulaires()) {
            c.add(vide());
            c.add(p("Titulaire(s) proposé(s) :", null, null));
            c.add(table(List.of("RANG", "CANDIDAT PROPOSÉ", "MONTANT HT (Ar)", "DÉLAI D’EXÉCUTION"), "TITULAIRES",
                    List.of("{{TIT.RANG}}", "{{TIT.CANDIDAT}}", "{{TIT.MONTANT_HT}}", "{{TIT.DELAI}}"), List.of(55, 215, 125, 125), null));
            c.add(p("Écart à l’estimation : {{MARCHE.ECART}}", null, null));
        }
        c.add(vide());
        c.add(vide());

        // Signatures, sans bordures.
        ObjectNode sign = ModelePvPage.noeud("table");
        sign.putObject("attrs").put("bordures", false);
        ArrayNode lignes = sign.putArray("content");
        lignes.add(ligne(null, cellule(270, p("VISA DU SUPERIEUR HIERARCHIQUE", null, gras()),
                p("(Nom, prénoms, cachet et signature du supérieur hiérarchique)", null, null)),
                cellule(270, p("A {{COMMISSION.CHEF_LIEU}}, le {{PV.DATE_DU_JOUR}}", null, gras()),
                        p("(Nom, prénoms, cachet et signature du membre en charge du dossier)", null, null))));
        lignes.add(ligne(null, cellule(270, vide(), vide(), vide(), p("{{PV.VISEUR}}", null, marques(false, 11))),
                cellule(270, vide(), vide(), vide(), p("{{PV.MEMBRE}}", null, marques(false, 11)))));
        c.add(sign);

        // L'annexe : sa propre page, sous les réserves seulement.
        List<ObjectNode> annexe = new ArrayList<>();
        annexe.add(ModelePvPage.noeud("pageBreak"));
        annexe.add(p("ANNEXE", attrs("center", null, null), gras()));
        annexe.add(vide());
        ObjectNode obs = table(List.of("REFERENCES", "OBSERVATIONS", "CORRECTIONS"), "OBSERVATIONS", List.of(), List.of(150, 185, 185),
                marques(false, 12));
        ArrayNode ligneObs = (ArrayNode) obs.path("content").path(1).path("content");
        ligneObs.removeAll();
        ligneObs.add(cellule(150, p("{{OBS.POINT}}", null, marques(false, 12))));
        ligneObs.add(cellule(185, p("Au lieu de :", null, marques(true, 12)), p("{{OBS.AU_LIEU_DE}}", null, marques(false, 12))));
        ligneObs.add(cellule(185, p("Lire :", null, marques(true, 12)), p("{{OBS.LIRE}}", null, marques(true, 12))));
        annexe.add(obs);
        c.add(bloc("AVEC_RESERVES", annexe));

        ObjectNode doc = F.objectNode();
        ObjectNode page = doc.putObject("page").put("format", "A4").put("orientation", "portrait").put("police", "Arial").put("taille", 10);
        page.putObject("marges").put("haut", 25).put("bas", 17).put("gauche", 25).put("droite", 25);
        doc.putObject("corps").put("type", "doc").set("content", c);
        ArrayNode pied = F.arrayNode();
        ObjectNode numero = ModelePvPage.noeud("paragraph");
        numero.putObject("attrs").put("textAlign", "center");
        numero.putArray("content").add(ModelePvPage.noeud("numeroPage"));
        pied.add(numero);
        doc.putObject("piedDePage").put("type", "doc").set("content", pied);

        List<ModelePvDto.Condition> conditions = new ArrayList<>(List.of(
                new ModelePvDto.Condition("FAVORABLE", "avis = FAV", "Avis favorable"),
                new ModelePvDto.Condition("AVEC_RESERVES", "avis = FAVR", "Avis favorable avec réserves"),
                new ModelePvDto.Condition("DEFAVORABLE", "avis = DEF", "Avis défavorable")));
        if (plan) {
            conditions.add(new ModelePvDto.Condition("AVEC_AGPM", "agpm = oui", "Le plan porte un AGPM (sous-type PPM-AGPM)"));
            conditions.add(new ModelePvDto.Condition("PRESIDENT", "PV.PRESIDENT renseigne", "Le Président a signé"));
            conditions.add(new ModelePvDto.Condition("CHEF_COMMISSION", "PV.CHEF_COMMISSION renseigne", "Le Chef de la Commission a signé"));
            conditions.add(new ModelePvDto.Condition("MEMBRE", "PV.MEMBRE renseigne", "Un Membre a signé"));
        } else {
            conditions.add(new ModelePvDto.Condition("OBS_GENERALE", "saisi.observation_generale renseigne",
                    "Une observation générale est saisie"));
        }
        if (deux) {
            conditions.add(new ModelePvDto.Condition("CENTRALE", "portee = centrale", "Commission centrale"));
            conditions.add(new ModelePvDto.Condition("REGIONALE", "portee = regionale", "Commission régionale"));
        }
        if (plan) {
            List<String> bases = new ArrayList<>();
            for (String sens : List.of("AF", "AFSR", "ANF")) {
                for (String agpm : List.of("PPM", "PPMAGPM")) {
                    for (String lieu : deux ? List.of("CENTRALE", "REGIONALE") : List.of(regionale ? "REGIONALE" : "CENTRALE")) {
                        bases.add("PV_" + sens + "_" + agpm + "_" + lieu + ".docx");
                    }
                }
            }
            return new Depart(doc, bases, conditions, List.of());
        }
        List<ModelePvDto.Champ> champs = List.of(
                new ModelePvDto.Champ("motif_defavorable", "Motif de l'avis défavorable", "TEXTE_LONG", List.of(), true,
                        "Obligatoire quand l'avis est défavorable.", "DEFAVORABLE"),
                new ModelePvDto.Champ("observation_generale", "Observation générale de la Commission", "TEXTE_LONG", List.of(), false,
                        "Facultative : un constat d'ensemble sur le dossier.", null));
        List<String> bases = deux ? List.of(BASE_CENTRALE, BASE_REGIONALE) : List.of(regionale ? BASE_REGIONALE : BASE_CENTRALE);
        return new Depart(doc, bases, conditions, champs);
    }

    /**
     * ⚠️ 2026-10-21 (demande front « modele-lettre-renvoi », B2) — le départ de la <strong>lettre de renvoi</strong>, reconstruit
     * d'après {@code LR_CENTRALE.docx} / {@code LR_REGIONALE.docx} : en-tête officiel (l'emblème des PV, qui porte déjà « REPOBLIKAN'I
     * MADAGASIKARA » et la devise, écrites une seconde fois sous l'image du Word), date, signataire, destinataire, objet, référence,
     * formule, corps, nom du signataire. Les deux fichiers ne diffèrent que par la ligne de commission et le titre du signataire :
     * blocs {@code CENTRALE} / {@code REGIONALE} pour {@code les-deux}. Le texte est repris tel quel, régional compris (« Antananarivo,
     * le », « la Commission Nationale des Marchés renvoie ») : la comparaison avec le fichier intégré (lot L2) doit rester honnête.
     * Les retraits du Word (marge gauche de 7,5 mm, aucune à droite) sont ramenés à des marges de page de 25 et 23 mm.
     */
    public static Depart lettre(String portee, long idEmbleme) {
        boolean deux = LES_DEUX.equals(portee);
        boolean regionale = REGIONALE.equals(portee);
        ArrayNode c = F.arrayNode();
        ArrayNode arial = F.arrayNode();
        ObjectNode style = ModelePvPage.noeud("textStyle");
        style.putObject("attrs").put("fontFamily", "Arial").put("fontSize", "10pt");
        arial.add(style);

        ObjectNode emb = ModelePvPage.noeud("image");
        emb.putObject("attrs").put("idImage", idEmbleme).put("largeur", 45.0).put("alignement", "center");
        c.add(emb);
        c.add(p("MINISTERE DE L’ECONOMIE ET DES FINANCES", attrs("center", null, 60.0), arial));
        c.add(p(POINTILLES, attrs("center", null, 60.0), arial));
        c.add(p("COMMISSION NATIONALE DES MARCHES", attrs("center", null, 60.0), arial));
        c.add(p(POINTILLES, attrs("center", null, 60.0), arial));
        String regionaleLigne = "REGIONALE DES MARCHES {{LETTRE.LOCALITE|majuscules}}";
        c.add(p("COMMISSION " + (deux ? "{{SI:CENTRALE}}CENTRALE{{FINSI:CENTRALE}}{{SI:REGIONALE}}" + regionaleLigne + "{{FINSI:REGIONALE}}"
                : regionale ? regionaleLigne : "CENTRALE"), attrs("center", null, 60.0), arial));
        c.add(p(POINTILLES, attrs("center", null, 60.0), arial));
        c.add(vide());
        c.add(vide());

        c.add(p("Antananarivo, le {{LETTRE.DATE}}", attrs("center", null, null), null));
        c.add(vide());
        String president = "Le Président de la Commission Nationale des Marchés";
        String chef = "Le Chef de la Commission Régionale des Marchés";
        c.add(p(deux ? "{{SI:CENTRALE}}" + president + "{{FINSI:CENTRALE}}{{SI:REGIONALE}}" + chef + "{{FINSI:REGIONALE}}"
                : regionale ? chef : president, attrs("center", 53.0, 3.0), null));
        c.add(vide());
        c.add(vide());
        c.add(p("à", attrs("center", 50.0, null), null));
        c.add(vide());
        c.add(vide());
        c.add(p("Madame/Monsieur la Personne Responsable des Marchés Publics de {{DOSSIER.ENTITE}}", attrs("center", 57.0, 7.0), null));
        for (int i = 0; i < 4; i++) {
            c.add(vide());
        }

        ArrayNode souligne = gras();
        souligne.add(ModelePvPage.noeud("underline"));
        ObjectNode objet = ModelePvPage.noeud("paragraph");
        objet.putArray("content").addAll(ModelePvPage.enLigne("Objet", souligne)).addAll(ModelePvPage.enLigne(" : lettre de renvoi", null));
        c.add(objet);
        ObjectNode ref = ModelePvPage.noeud("paragraph");
        ref.putObject("attrs").put("espaceAvant", 12);
        ref.putArray("content").addAll(ModelePvPage.enLigne("Réf", souligne)).addAll(ModelePvPage.enLigne(" :   {{DOSSIER.REFERENCE}}", null));
        c.add(ref);
        c.add(vide());
        c.add(vide());
        c.add(p("Madame/Monsieur,", attrs(null, 25.0, null), null));
        ObjectNode corps = ModelePvPage.noeud("paragraph");
        corps.putObject("attrs").put("espaceAvant", 12).put("interligne", 1.15);
        corps.putArray("content").addAll(ModelePvPage.enLigne("Faisant suite à la séance d’instruction en date du {{LETTRE.DATE_EXAMEN}}, "
                + "la Commission Nationale des Marchés renvoie l’examen du dossier à une séance ultérieure en demandant au service de :", null))
                .add(ModelePvPage.noeud("hardBreak")).addAll(ModelePvPage.enLigne("{{LETTRE.CORPS}}", null));
        c.add(corps);
        for (int i = 0; i < 7; i++) {
            c.add(vide());
        }
        c.add(p("{{LETTRE.SIGNATAIRE}}", attrs("right", null, 16.0), null));

        ObjectNode doc = F.objectNode();
        ObjectNode page = doc.putObject("page").put("format", "A4").put("orientation", "portrait").put("police", "Times New Roman")
                .put("taille", 12);
        page.putObject("marges").put("haut", 23).put("bas", 17).put("gauche", 25).put("droite", 23);
        doc.putObject("corps").put("type", "doc").set("content", c);
        List<ModelePvDto.Condition> conditions = deux ? List.of(
                new ModelePvDto.Condition("CENTRALE", "portee = centrale", "Commission centrale"),
                new ModelePvDto.Condition("REGIONALE", "portee = regionale", "Commission régionale")) : List.of();
        List<String> bases = deux ? List.of(LETTRE_CENTRALE, LETTRE_REGIONALE) : List.of(regionale ? LETTRE_REGIONALE : LETTRE_CENTRALE);
        return new Depart(doc, bases, conditions, List.of());
    }

    // ───────────────────────────────────────────── outils ─────────────────────────────────────────────

    /** Un paragraphe écrit avec les marqueurs du Word ({@link ModelePvPage#enLigne}). */
    private static ObjectNode p(String texte, ObjectNode attrs, ArrayNode marques) {
        ObjectNode n = ModelePvPage.noeud("paragraph");
        if (attrs != null) {
            n.set("attrs", attrs);
        }
        n.set("content", ModelePvPage.enLigne(texte, marques));
        return n;
    }

    private static ObjectNode vide() {
        return ModelePvPage.noeud("paragraph");
    }

    /** « La Commission … émet un <b>AVIS …</b> cible. », justifié. */
    private static ObjectNode avis(String debut, String avis, String fin) {
        ObjectNode n = ModelePvPage.noeud("paragraph");
        n.set("attrs", attrs("justify", null, null));
        ArrayNode content = n.putArray("content");
        content.addAll(ModelePvPage.enLigne(debut, null));
        content.addAll(ModelePvPage.enLigne(avis, gras()));
        content.addAll(ModelePvPage.enLigne(fin, null));
        return n;
    }

    /** « <b>LIBELLÉ :</b> valeur » (l'identification du plan). */
    private static ObjectNode libelle(String libelle, String valeur) {
        ObjectNode n = ModelePvPage.noeud("paragraph");
        ArrayNode content = n.putArray("content");
        content.addAll(ModelePvPage.enLigne(libelle, gras()));
        content.addAll(ModelePvPage.enLigne(valeur, null));
        return n;
    }

    private static ObjectNode bloc(String condition, List<ObjectNode> contenu) {
        ObjectNode b = ModelePvPage.noeud("bloc");
        b.putObject("attrs").put("condition", condition);
        ArrayNode a = b.putArray("content");
        contenu.forEach(a::add);
        return b;
    }

    /** Un tableau à bordures : un en-tête en gras, puis une ligne répétée par {@code liste} portant {@code valeurs}. */
    private static ObjectNode table(List<String> titres, String liste, List<String> valeurs, List<Integer> largeurs, ArrayNode marques) {
        ObjectNode t = ModelePvPage.noeud("table");
        t.putObject("attrs").put("bordures", true);
        ArrayNode lignes = t.putArray("content");
        List<ObjectNode> entete = new ArrayList<>();
        List<ObjectNode> repetee = new ArrayList<>();
        ArrayNode grasTitre = marques == null ? gras() : marques(true, 12);
        for (int i = 0; i < titres.size(); i++) {
            entete.add(cellule(largeurs.get(i), p(titres.get(i), "REFERENCES".equals(titres.get(i)) ? attrs("center", null, null) : null,
                    grasTitre)));
            repetee.add(cellule(largeurs.get(i), p(i < valeurs.size() ? valeurs.get(i) : "", null, marques)));
        }
        lignes.add(ligne(null, entete.toArray(ObjectNode[]::new)));
        lignes.add(ligne(liste, repetee.toArray(ObjectNode[]::new)));
        return t;
    }

    private static ObjectNode ligne(String liste, ObjectNode... cellules) {
        ObjectNode l = ModelePvPage.noeud("tableRow");
        if (liste != null) {
            l.putObject("attrs").put("liste", liste);
        }
        ArrayNode a = l.putArray("content");
        for (ObjectNode c : cellules) {
            a.add(c);
        }
        return l;
    }

    private static ObjectNode cellule(int largeur, ObjectNode... paragraphes) {
        ObjectNode c = ModelePvPage.noeud("tableCell");
        c.putObject("attrs").putArray("colwidth").add(largeur);
        ArrayNode a = c.putArray("content");
        for (ObjectNode p : paragraphes) {
            a.add(p);
        }
        return c;
    }

    private static ObjectNode attrs(String alignement, Double retrait, Double retraitDroite) {
        ObjectNode a = F.objectNode();
        if (alignement != null) {
            a.put("textAlign", alignement);
        }
        if (retrait != null) {
            a.put("retrait", retrait);
        }
        if (retraitDroite != null) {
            a.put("retraitDroite", retraitDroite);
        }
        return a;
    }

    private static ArrayNode gras() {
        ArrayNode m = F.arrayNode();
        m.add(ModelePvPage.noeud("bold"));
        return m;
    }

    /** Gras ou non, en Times New Roman à la taille donnée (le titre, l'annexe et les noms de la base). */
    private static ArrayNode marques(boolean gras, int taille) {
        ArrayNode m = F.arrayNode();
        if (gras) {
            m.add(ModelePvPage.noeud("bold"));
        }
        ObjectNode style = ModelePvPage.noeud("textStyle");
        style.putObject("attrs").put("fontFamily", "Times New Roman").put("fontSize", taille + "pt");
        m.add(style);
        return m;
    }
}
