package cnm.prs.service;

import static cnm.prs.service.FichePresentationService.ligne;
import static cnm.prs.service.FichePresentationService.lignes;
import static cnm.prs.service.FichePresentationService.premier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CanevasRapportDto;
import cnm.prs.dto.CanevasRapportDto.Section;
import cnm.prs.dto.CanevasRapportDto.Tableau;
import cnm.prs.dto.FichePresentationDto.Ligne;
import cnm.prs.dto.SousCritereDto;
import cnm.prs.entity.Dossier;
import cnm.prs.enums.CategorieDao;
import cnm.prs.enums.TypeMarcheDao;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DossierRepository;

/**
 * ⚠️ 2026-10-17 (demande front « canevas-rapport-evaluation », §B1 ; demande du pilote) — le <strong>canevas de rapport
 * d'évaluation</strong> d'un dossier né d'une fiche validée, produit par l'application : il remplace la pièce « Canevas de rapport
 * d'évaluation » ({@link PiecesFournies}), comme V115 l'annonçait en attendant les modèles du guide de la CAO.
 * <p>
 * Il suit le plan du rapport que l'évaluation produira ({@link EvaluationService}, guide de la CAO) et porte ce que la fiche sait
 * <strong>avant les offres</strong> : références, exigences de l'examen préliminaire (la grille de ses vérifications, à coter),
 * conformité (les articles et leurs caractéristiques, par lot), méthode d'évaluation, critères de qualification ; les tableaux que la
 * commission remplira restent vides. Prestations intellectuelles (Q2) : un seul canevas en deux parties, l'évaluation technique puis
 * l'évaluation finale. La version présentée est la version soumise du dossier, à défaut la dernière validée. Le contrat-cadre n'a pas
 * de canevas (manuel, page 32).
 */
@Service
@Transactional(readOnly = true)
public class CanevasRapportService {

    public static final String CODE_INDISPONIBLE = "CANEVAS_INDISPONIBLE";
    public static final String PARTIE_TECHNIQUE = "TECHNIQUE";
    public static final String PARTIE_FINALE = "FINALE";
    private static final DateTimeFormatter JOUR_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");
    private static final List<String> SIGNATAIRES = List.of("Président", "Membre", "Membre", "Membre", "Membre");

    private final DossierRepository dossiers;
    private final FicheMarcheService fiches;
    private final ValeursPpmService valeursPpm;
    private final BesoinFiche besoin;
    private final SousCriteresFiche sousCriteres;
    private final GenerateurDocumentsFiche generateur;
    private final ObjectProvider<DossierService> dossierService;

    public CanevasRapportService(DossierRepository dossiers, FicheMarcheService fiches, ValeursPpmService valeursPpm, BesoinFiche besoin,
            SousCriteresFiche sousCriteres, GenerateurDocumentsFiche generateur, ObjectProvider<DossierService> dossierService) {
        this.dossiers = dossiers;
        this.fiches = fiches;
        this.valeursPpm = valeursPpm;
        this.besoin = besoin;
        this.sousCriteres = sousCriteres;
        this.generateur = generateur;
        this.dossierService = dossierService;
    }

    /** Les données, sous la garde de lecture du dossier ; 409 {@code CANEVAS_INDISPONIBLE} sans fiche validée ou au contrat-cadre. */
    public CanevasRapportDto lire(Integer idDossier) {
        dossierService.getObject().controlerVisibilite(idDossier);
        return contenu(idDossier);
    }

    /** Le PDF, sous la même garde. */
    public byte[] pdf(Integer idDossier) {
        dossierService.getObject().controlerVisibilite(idDossier);
        CanevasRapportDto c = contenu(idDossier);
        return generateur.generer(document(c, LocalDateTime.now())).stream().filter(x -> "pdf".equals(x.extension())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Le canevas n'a pas été rendu en pdf.")).contenu();
    }

    /** {@code CANEVAS_RAPPORT_<référence ou id du dossier>.pdf}. */
    public String nomPdf(Integer idDossier) {
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        String ref = d.getRefeDossier() == null || d.getRefeDossier().isBlank() ? String.valueOf(idDossier) : d.getRefeDossier();
        return "CANEVAS_RAPPORT_" + ref.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
    }

    /** Le canevas vaut-il pour cette version (hors contrat-cadre) ? */
    static boolean prevu(FicheMarcheService.EtatVersion ev) {
        return !TypeMarcheDao.CONTRAT_CADRE.name().equals(ev.type());
    }

    CanevasRapportDto contenu(Integer idDossier) {
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        FicheMarcheService.VersionPresentee vp = d.getIdDmc() == null ? null
                : fiches.versionPresentee(d.getIdDmc(), d.getVersionFicheSoumise()).orElse(null);
        if (vp == null) {
            throw new BusinessRuleException("Ce dossier n'est pas né d'une fiche validée : son canevas de rapport d'évaluation n'est pas "
                    + "produit par l'application.", CODE_INDISPONIBLE, idDossier);
        }
        FicheMarcheService.EtatVersion ev = vp.etat();
        if (!prevu(ev)) {
            throw new BusinessRuleException("Un contrat-cadre n'a pas de canevas de rapport d'évaluation (manuel de contrôle, page 32).",
                    CODE_INDISPONIBLE, idDossier);
        }
        Map<String, String> p = ev.etat().getIdDetail() == null ? Map.of() : valeursPpm.lire(ev.etat().getIdDetail()).valeurs();
        boolean pi = CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(ev.categorie());
        List<Section> sections = pi ? prestationsIntellectuelles(ev, p, vp.idFiche()) : offres(ev, p, vp.idFiche());
        return new CanevasRapportDto(idDossier, d.getIdDmc(), ev.version(), ev.categorie(),
                sections.stream().filter(s -> !s.lignes().isEmpty() || s.tableau() != null).toList());
    }

    // ------------------------------------------------------------------ fournitures et travaux

    private List<Section> offres(FicheMarcheService.EtatVersion ev, Map<String, String> p, Integer idFiche) {
        boolean travaux = CategorieDao.TRAVAUX.name().equals(ev.categorie());
        List<BesoinFiche.Article> articles = besoin.articles(idFiche);
        List<Section> s = new ArrayList<>();
        s.add(section("REFERENCES", "1. Références du marché", null, references(ev, p), null));
        s.add(section("PLIS_RECUS", "2. Plis reçus", null, List.of(), new Tableau(
                List.of("N°", "Soumissionnaire", "Date et heure de remise", "Montant lu à l'ouverture", "Observations"), List.of(), 5)));
        List<String> verifications = new ArrayList<>(EvaluationService.VERIFICATIONS.values());
        if (!travaux) {
            verifications.removeIf(v -> v.startsWith("Certificat de visite des lieux"));
        }
        s.add(section("EXAMEN_PRELIMINAIRE", "3. Examen préliminaire", null, lignes(
                ligne("GARANTIE", "Garantie de soumission exigée", premier(ev, "B05-GS-01", "B05-GQ-01")),
                ligne("GARANTIE_FORME", "Forme de la garantie de soumission", premier(ev, "B05-GS-02", "B05-GQ-02")),
                ligne("GARANTIE_MONTANT", "Montant de la garantie de soumission (Ariary)", premier(ev, "B05-GS-03", "B05-GQ-03")),
                ligne("GARANTIE_VALIDITE", "Validité de la garantie de soumission (jours)", premier(ev, "B05-GS-04")),
                ligne("VALIDITE_OFFRES", "Délai de validité des offres (jours)", premier(ev, "B04-VO-01", "B04-VT-01", "B04-DV-01")),
                ligne("PIECES_ADMINISTRATIVES", "Pièces administratives exigées", premier(ev, "B03-CQ-01", "B04-PI-01")),
                ligne("VISITE_LIEUX", "Visite des lieux obligatoire", premier(ev, "B04-VL-02"))),
                new Tableau(List.of("Vérification", "Cote (C / SC / NC / SO)", "Observation"), verifications, 0)));
        s.add(section("CONFORMITE", "4. Conformité pour l'essentiel", null, conformite(ev, articles),
                new Tableau(List.of("Article et caractéristique exigée", "Proposition du soumissionnaire", "Cote (C / SC / NC)"),
                        caracteristiques(articles), articles.isEmpty() ? 5 : 0)));
        s.add(section("EVALUATION", "5. Évaluation des offres conformes", null, lignes(
                ligne("METHODE", "Méthode", "Attribution à l'offre évaluée la moins disante, conforme pour l'essentiel, du soumissionnaire "
                        + "qualifié : montant lu, corrections arithmétiques, rabais et marge de préférence éventuels."),
                ligne("PLUSIEURS_LOTS", "Offres portant sur plusieurs lots", premier(ev, "B06-EO-01", "B06-EV-01")),
                ligne("CRITERES_ADDITIONNELS", "Critères additionnels d'évaluation", premier(ev, "B06-EO-02")),
                ligne("MARGE_PREFERENCE", "Marge de préférence nationale (%)", premier(ev, "B06-EO-09", "B06-PN-02")),
                ligne("VARIANTES", "Variantes", premier(ev, "B02-VA-01", "B02-LV-06")),
                ligne("OFFRES_ANORMALES", "Offres anormalement hautes ou basses", premier(ev, "B06-EO-07")),
                ligne("VARIATION_QUANTITES", "Variation maximale des quantités à l'attribution (%)", premier(ev, "B06-EO-10"))),
                new Tableau(List.of("Soumissionnaire", "Montant lu", "Corrections arithmétiques", "Rabais", "Marge de préférence",
                        "Montant évalué"), List.of(), 5)));
        s.add(section("CLASSEMENT", "6. Classement des offres", null, List.of(),
                new Tableau(List.of("Rang", "Soumissionnaire", "Montant évalué", "Observations"), List.of(), 5)));
        List<Ligne> qualification = champsDe(ev, "B03-CQ-", "B03-QT-", "B03-GT-03");
        s.add(section("POST_QUALIFICATION", "7. Vérification de la qualification du soumissionnaire le moins disant", null, qualification,
                new Tableau(List.of("Critère de qualification", "Constat", "Cote (C / NC)"),
                        qualification.stream().map(Ligne::libelle).toList(), qualification.isEmpty() ? 5 : 0)));
        s.add(section("ATTRIBUTION", "8. Proposition d'attribution", null, List.of(),
                new Tableau(List.of("Lot", "Attributaire proposé", "Montant (Ariary)", "Délai"), lots(ev, articles), 0)));
        s.add(signatures("9. Signatures des membres de la commission", null));
        return s;
    }

    private static List<Ligne> conformite(FicheMarcheService.EtatVersion ev, List<BesoinFiche.Article> articles) {
        List<Ligne> l = new ArrayList<>(lignes(
                ligne("DELAI", "Délai de livraison ou d'exécution (jours)", premier(ev, "B06-EO-11", "B06-EO-12"))));
        Map<Integer, List<BesoinFiche.Article>> parLot = new TreeMap<>();
        articles.forEach(a -> parLot.computeIfAbsent(a.lot() == null ? 0 : a.lot(), k -> new ArrayList<>()).add(a));
        parLot.forEach((lot, as) -> l.add(new Ligne(lot == 0 ? "ARTICLES" : "ARTICLES#" + lot,
                lot == 0 ? "Articles exigés" : "Articles exigés, lot " + lot,
                String.join("\n", as.stream().map(a -> a.designation() + (a.quantite() == null ? ""
                        : " — " + a.quantite().stripTrailingZeros().toPlainString() + (a.unite() == null ? "" : " " + a.unite()))).toList()))));
        return l;
    }

    /** Une rangée par caractéristique exigée (« article — caractéristique : exigence »), à défaut par article. */
    private static List<String> caracteristiques(List<BesoinFiche.Article> articles) {
        List<String> r = new ArrayList<>();
        for (BesoinFiche.Article a : articles) {
            String prefixe = (a.lot() == null ? "" : "Lot " + a.lot() + " — ") + a.designation();
            if (a.caracteristiques() == null || a.caracteristiques().isEmpty()) {
                r.add(prefixe);
            } else {
                a.caracteristiques().forEach(c -> r.add(prefixe + " — " + Objects.toString(c.getLibelle(), "")
                        + (c.getExigence() == null || c.getExigence().isBlank() ? "" : " : " + c.getExigence())));
            }
        }
        return r;
    }

    private static List<String> lots(FicheMarcheService.EtatVersion ev, List<BesoinFiche.Article> articles) {
        Integer nb = ev.etat().getNbLots();
        if (nb == null || nb <= 1) {
            return List.of("Marché");
        }
        List<String> r = new ArrayList<>();
        for (int i = 1; i <= nb; i++) {
            r.add("Lot " + i);
        }
        return r;
    }

    // ------------------------------------------------------------------ prestations intellectuelles (Q2)

    private List<Section> prestationsIntellectuelles(FicheMarcheService.EtatVersion ev, Map<String, String> p, Integer idFiche) {
        List<Section> s = new ArrayList<>();
        String t = PARTIE_TECHNIQUE;
        s.add(section("REFERENCES", "1. Références de la consultation", t, references(ev, p), null));
        s.add(section("PROPOSITIONS_RECUES", "2. Propositions reçues", t, List.of(),
                new Tableau(List.of("N°", "Consultant", "Date et heure de remise", "Observations"), List.of(), 5)));
        s.add(section("EXAMEN_PRELIMINAIRE", "3. Examen préliminaire", t, lignes(
                ligne("VALIDITE_PROPOSITIONS", "Délai de validité des propositions (jours)", premier(ev, "B04-DP-01"))),
                new Tableau(List.of("Vérification", "Cote (C / NC)", "Observation"), List.of(
                        "Proposition remise dans le délai, à l'adresse indiquée",
                        "Propositions technique et financière sous plis séparés",
                        "Signataire habilité, pouvoirs joints",
                        "Pièces exigées par la demande de propositions"), 0)));
        List<String> rangees = new ArrayList<>();
        List<SousCritereDto> sc = sousCriteres.lister(idFiche);
        for (String code : SousCriteresFiche.CRITERES) {
            String points = ev.valeur(code);
            if (points == null || points.isBlank()) {
                continue;
            }
            rangees.add(ev.libelle(code).replaceFirst("^Points : ", "") + " (" + points + " points)");
            sc.stream().filter(x -> code.equals(x.critere())).forEach(x -> rangees.add("    " + x.libelle()
                    + (x.points() == null ? "" : " (" + x.points().stripTrailingZeros().toPlainString() + " points)")));
        }
        s.add(section("EVALUATION_TECHNIQUE", "4. Évaluation des propositions techniques", t, lignes(
                ligne("SCORE_MINIMUM", "Score technique minimum (points)", premier(ev, "B06-TP-07"))),
                new Tableau(List.of("Critère et sous-critère", "Consultant 1", "Consultant 2", "Consultant 3", "Consultant 4"),
                        rangees, rangees.isEmpty() ? 5 : 0)));
        s.add(signatures("5. Signatures (rapport d'évaluation technique)", t));
        String f = PARTIE_FINALE;
        s.add(section("OUVERTURE_FINANCIERE", "6. Ouverture des propositions financières", f, List.of(),
                new Tableau(List.of("Consultant", "Note technique", "Montant de la proposition financière", "Observations"), List.of(), 4)));
        s.add(section("EVALUATION_FINALE", "7. Évaluation finale (combinée)", f, lignes(
                ligne("POIDS_TECHNIQUE", "Poids de la proposition technique (T)", premier(ev, "B06-CS-02")),
                ligne("POIDS_FINANCIER", "Poids de la proposition financière (F)", premier(ev, "B06-CS-03"))),
                new Tableau(List.of("Rang", "Consultant", "Note technique", "Note financière", "Note combinée"), List.of(), 4)));
        s.add(section("NEGOCIATIONS", "8. Négociations et proposition d'attribution", f, lignes(
                ligne("ADRESSE_NEGOCIATIONS", "Adresse des négociations", premier(ev, "B06-NG-01"))),
                new Tableau(List.of("Consultant proposé", "Montant (Ariary)", "Observations"), List.of(), 1)));
        s.add(signatures("9. Signatures (rapport d'évaluation finale)", f));
        return s;
    }

    // ------------------------------------------------------------------ commun

    private static List<Ligne> references(FicheMarcheService.EtatVersion ev, Map<String, String> p) {
        return lignes(
                ligne("NUMERO_DAO", "Numéro du dossier d'appel d'offres", premier(ev, "B02-OB-03")),
                ligne("OBJET", "Objet", p.getOrDefault("OBJET", ev.etat().getDesignationMarche())),
                ligne("AUTORITE", "Autorité contractante", p.get("ENTITE")),
                ligne("MODE", "Mode de passation", p.get("MODE")),
                ligne("CATEGORIE", "Catégorie", FichePresentationService.categorie(ev.categorie())),
                ligne("LOTS", "Lots (montant au plan)", p.get("LOTS_MONTANTS") != null ? p.get("LOTS_MONTANTS") : p.get("LOTS_DESIGNATION")),
                ligne("MONTANT_ESTIMATIF", "Montant estimatif du plan", p.get("MONTANT_ESTIMATIF")),
                ligne("VERSION_FICHE", "Version de la fiche", String.valueOf(ev.version())));
    }

    /** Les champs qui valent pour la fiche et portent une valeur, dont le code commence par l'un des préfixes (ordre du référentiel). */
    private static List<Ligne> champsDe(FicheMarcheService.EtatVersion ev, String... prefixes) {
        List<Ligne> l = new ArrayList<>();
        for (String code : ev.champs().keySet()) {
            for (String pre : prefixes) {
                if (code.startsWith(pre)) {
                    String v = ev.valeur(code);
                    if (v != null && !v.isBlank()) {
                        l.add(new Ligne(code, ev.libelle(code), v));
                    }
                    break;
                }
            }
        }
        return l;
    }

    private static Section signatures(String titre, String partie) {
        return section(PARTIE_FINALE.equals(partie) ? "SIGNATURES_FINALE" : "SIGNATURES", titre, partie, List.of(),
                new Tableau(List.of("Qualité", "Nom et prénoms", "Signature"), SIGNATAIRES, 0));
    }

    private static Section section(String code, String titre, String partie, List<Ligne> lignes, Tableau tableau) {
        return new Section(code, titre, partie, lignes.stream().filter(Objects::nonNull).toList(), tableau);
    }

    DocumentLibre document(CanevasRapportDto c, LocalDateTime le) {
        boolean pi = CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(c.categorie());
        List<DocumentLibre.Element> els = new ArrayList<>();
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "CANEVAS DE RAPPORT D'ÉVALUATION"
                + (pi ? " DES PROPOSITIONS" : " DES OFFRES")));
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "Guide de la Commission d'Appel d'Offres"));
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        String partie = null;
        for (Section s : c.sections()) {
            if (s.partie() != null && !s.partie().equals(partie)) {
                partie = s.partie();
                els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, PARTIE_TECHNIQUE.equals(partie)
                        ? "PREMIÈRE PARTIE — ÉVALUATION DES PROPOSITIONS TECHNIQUES" : "DEUXIÈME PARTIE — ÉVALUATION FINALE", true));
            }
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, s.titre(), true));
            if (!s.lignes().isEmpty()) {
                List<List<List<String>>> rangs = new ArrayList<>();
                s.lignes().forEach(l -> rangs.add(List.of(List.of(l.libelle()), List.of(l.valeur().split("\n", -1)))));
                els.add(new DocumentLibre.Tableau(2, rangs));
            }
            Tableau t = s.tableau();
            if (t != null) {
                int n = t.colonnes().size();
                List<List<List<String>>> rangs = new ArrayList<>();
                rangs.add(t.colonnes().stream().map(List::of).toList());
                for (String r : t.rangees()) {
                    List<List<String>> rang = new ArrayList<>();
                    rang.add(List.of(r));
                    rang.addAll(Collections.nCopies(n - 1, List.of("")));
                    rangs.add(rang);
                }
                for (int i = 0; i < t.rangeesVides(); i++) {
                    rangs.add(Collections.nCopies(n, List.of("")));
                }
                els.add(new DocumentLibre.Tableau(n, rangs));
            }
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        }
        String pied = "Canevas produit par l'application le " + le.format(JOUR_HEURE) + " à partir de la version " + c.versionFiche()
                + " de la fiche ; le rapport d'évaluation reprendra les critères de cette version.";
        return new DocumentLibre("CANEVAS_RAPPORT", null, els, pied);
    }
}
