package cnm.prs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ArticleBesoinDto;
import cnm.prs.dto.BesoinEnLigneDto;
import cnm.prs.dto.MaterielExigeDto;
import cnm.prs.dto.PersonnelExigeDto;
import cnm.prs.dto.ProcedureEnLigneDto;
import cnm.prs.dto.SeanceDto;
import cnm.prs.entity.FicheMarche;
import cnm.prs.enums.CategorieDao;
import cnm.prs.enums.TypeMarcheDao;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.databind.JsonNode;

/**
 * ⚠️ <strong>L'offre saisie dans des formulaires</strong> (demande front du 2026-10-05, soumission en ligne, lot 5 ; manifeste
 * format 3). Trois choses :
 * <ul>
 *   <li>§B1 — le <strong>besoin servi au candidat</strong> ({@link #besoin}), lu sur la version validée en vigueur ;</li>
 *   <li>§B1.3 — la correspondance <strong>pièce attendue → formulaire</strong> ({@link #formulaire}) : une pièce marquée n'est plus
 *       exigée en fichier ;</li>
 *   <li>§B3 — à l'ouverture, l'<strong>analyse</strong> de la partie {@code formulaires} du manifeste ({@link #analyser}) : les totaux
 *       recalculés depuis le bordereau et les quantités de la fiche, et des <strong>alertes, jamais un refus</strong>. Le détail
 *       (les prix ligne à ligne) ne va ni en base ni au journal : il se relit dans le contenu déchiffré, et se purge avec lui
 *       (V70).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class FormulairesEnLigne {

    public static final String BORDEREAU = "BORDEREAU";
    public static final String CONFORMITE = "CONFORMITE";
    public static final String CALENDRIER = "CALENDRIER";
    public static final String DQE = "DQE";
    public static final String SOUS_DETAIL = "SOUS_DETAIL";
    public static final String K1 = "K1";
    public static final String CAPACITES = "CAPACITES";
    public static final String PERSONNEL = "PERSONNEL";
    public static final String MATERIEL = "MATERIEL";
    static final String MONNAIE = "MGA";
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final ProceduresEnLigneService procedures;
    private final FicheMarcheService fiches;
    private final FicheMarcheRepository ficheRepository;
    private final BesoinFiche besoin;
    private final MoyensFiche moyens;
    private final ParametreService parametres;
    private final GenerateurDocumentsFiche generateur;

    public FormulairesEnLigne(ProceduresEnLigneService procedures, FicheMarcheService fiches, FicheMarcheRepository ficheRepository,
            BesoinFiche besoin, MoyensFiche moyens, ParametreService parametres, GenerateurDocumentsFiche generateur) {
        this.generateur = generateur;
        this.procedures = procedures;
        this.fiches = fiches;
        this.ficheRepository = ficheRepository;
        this.besoin = besoin;
        this.moyens = moyens;
        this.parametres = parametres;
    }

    // ------------------------------------------------------------------ §B1 le besoin servi au candidat

    /** {@code GET /api/procedures-en-ligne/{idDmc}/besoin} : 404 hors des critères de la liste publique (même règle que la procédure). */
    public BesoinEnLigneDto besoin(Long idDmc) {
        ProceduresEnLigneService.Lue l = procedures.exiger(idDmc);
        // ⚠️ 2026-10-06 (dossier payant, « A ») — le besoin est une part du dossier : gardé comme les documents (un lot payé ouvre
        // tout le besoin ; sans entreprise déclarée, la même réponse).
        if (l.dto().retraitPayant() && procedures.recuValide(idDmc, procedures.nifDe(CurrentUser.ref().orElse(null)), null).isEmpty()) {
            throw new cnm.prs.exception.AccesReserveException("Le besoin du dossier se lit une fois le reçu du paiement des frais validé "
                    + "par la PRMP.", "FRAIS_NON_REGLES");
        }
        return construire(idDmc, l.etat(), l.fiche().getIdFiche(), l.dto());
    }

    /** Le besoin d'un lot de la version validée (pour la commission, §B3.4) ; vide si la fiche n'en a pas. */
    Optional<BesoinEnLigneDto.Lot> lot(Long idDmc, Integer lot) {
        return contexte(idDmc).map(c -> construire(idDmc, c.etat(), c.idFiche(), null)).flatMap(b -> b.lots().stream()
                .filter(x -> Objects.equals(x.numero(), lot) || b.lots().size() == 1).findFirst());
    }

    private BesoinEnLigneDto construire(Long idDmc, FicheMarcheService.EtatVersion etat, Integer idFiche, ProcedureEnLigneDto dto) {
        String categorie = etat.categorie() == null ? CategorieDao.FOURNITURES_SERVICES.name() : etat.categorie();
        boolean travaux = CategorieDao.TRAVAUX.name().equals(categorie);
        boolean pi = CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie);
        String type = etat.type() == null ? TypeMarcheDao.QUANTITE_FIXE.name() : etat.type();
        List<ArticleBesoinDto> articles = pi ? List.of() : besoin.lister(idFiche);
        boolean formulaires = !articles.isEmpty();
        Map<String, String> v = etat.etat().getValeurs() == null ? Map.of() : etat.etat().getValeurs();
        List<ProcedureEnLigneDto.Lot> lotsPlan = dto == null ? List.of() : dto.lots();
        List<Integer> numeros = new ArrayList<>();
        if (lotsPlan.size() > 1) {
            lotsPlan.forEach(x -> numeros.add(x.numero()));
        } else {
            articles.stream().map(ArticleBesoinDto::getLot).filter(Objects::nonNull).distinct().sorted().forEach(numeros::add);
        }
        if (numeros.isEmpty()) {
            numeros.add(null);
        }
        List<BesoinEnLigneDto.Lot> lots = new ArrayList<>();
        for (Integer n : numeros) {
            String intitule = lotsPlan.stream().filter(x -> n != null && x.numero() == n).map(ProcedureEnLigneDto.Lot::intitule).findFirst()
                    .orElse(dto == null ? null : dto.objet());
            List<BesoinEnLigneDto.Article> duLot = articles.stream().filter(a -> n == null || n.equals(a.getLot()))
                    .map(FormulairesEnLigne::article).toList();
            lots.add(new BesoinEnLigneDto.Lot(n, intitule, duLot, travaux ? null : lu(v, "B09-LL-01", n), delai(v, n, travaux, type),
                    montant(lu(v, travaux ? "B05-GQ-03" : "B05-GS-03", n)), travaux ? qualification(v, n) : null));
        }
        return new BesoinEnLigneDto(idDmc, categorie, type, formulaires, parametres.tauxTva(), MONNAIE, lots,
                travaux && formulaires ? moyens.materiel(idFiche) : List.of(), travaux && formulaires ? moyens.personnel(idFiche) : List.of());
    }

    private static BesoinEnLigneDto.Article article(ArticleBesoinDto a) {
        List<BesoinEnLigneDto.Caracteristique> c = a.getCaracteristiques() == null ? List.of() : a.getCaracteristiques().stream()
                .map(x -> new BesoinEnLigneDto.Caracteristique(x.getIdCaracteristique(), x.getOrdre(), x.getLibelle(), x.getExigence())).toList();
        return new BesoinEnLigneDto.Article(a.getIdArticle(), a.getOrdre(), a.getDesignation(), a.getUnite(), a.getQuantite(), a.getQuantiteMin(),
                a.getQuantiteMax(), c, a.getNumeroPrix(), a.getSerie(), a.getSerieLibelle(), a.getLibelleBordereau(),
                Boolean.TRUE.equals(a.getSousDetail()), a.getPlafond());
    }

    /** La valeur brute d'un champ pour un lot : {@code CODE#n}, à défaut {@code CODE} ; {@code null} si vide. */
    private static String lu(Map<String, String> v, String code, Integer lot) {
        String x = lot == null ? null : v.get(code + LotsFiche.SEPARATEUR + lot);
        if (x == null || x.isBlank()) {
            x = v.get(code);
        }
        return x == null || x.isBlank() ? null : x.trim();
    }

    private static BesoinEnLigneDto.Delai delai(Map<String, String> v, Integer lot, boolean travaux, String type) {
        if (travaux) {
            String texte = lu(v, "B09-DL-01", lot);
            return texte == null ? null : new BesoinEnLigneDto.Delai(entier(texte), entier(texte) == null ? null : "JOURS", texte);
        }
        String jours = TypeMarcheDao.A_COMMANDE.name().equals(type) ? lu(v, "B06-EO-12", lot) : lu(v, "B06-EO-11", lot);
        if (jours == null) {
            jours = lu(v, "B09-DX-01", lot);
        }
        Integer n = entier(jours);
        return n == null ? null : new BesoinEnLigneDto.Delai(n, "JOURS", null);
    }

    private static BesoinEnLigneDto.Qualification qualification(Map<String, String> v, Integer lot) {
        Integer nombre = entier(lu(v, "B03-QT-19", lot));
        return new BesoinEnLigneDto.Qualification(montant(lu(v, "B03-QT-14", lot)), montant(lu(v, "B03-QT-15", lot)),
                new BesoinEnLigneDto.ChiffreAffaires(montant(lu(v, "B03-QT-07", lot)), entier(lu(v, "B03-QT-17", lot)),
                        entier(lu(v, "B03-QT-16", lot)), lu(v, "B03-QT-18", lot)),
                new BesoinEnLigneDto.References(montant(lu(v, "B03-QT-20", lot)), nombre, entier(lu(v, "B03-QT-12", lot)),
                        nombre != null && nombre > 1));
    }

    // ------------------------------------------------------------------ §B1.3 pièce → formulaire

    /**
     * Le formulaire qui remplace une pièce attendue, d'après son libellé (les pièces de la fiche, bloc B14, sont libres) ;
     * {@code null} si elle reste à joindre. Fournitures : bordereau, conformité, calendrier ; travaux : DQE / bordereau des prix
     * unitaires, sous-détail, coefficient K1, capacités financières et références, personnel, matériel.
     */
    public static String formulaire(String libelle, boolean travaux) {
        if (libelle == null) {
            return null;
        }
        String l = java.text.Normalizer.normalize(libelle, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        // ⚠️ 2026-10-05 (recette du lot 5, §B6) — un planning, un plan, un échéancier (et, aux travaux, un calendrier) n'est jamais un
        // formulaire : « Planning de mobilisation du personnel et du matériel » n'est pas la liste du personnel.
        if (l.contains("planning") || l.contains("plan de ") || l.contains("echeancier") || travaux && l.contains("calendrier")) {
            return null;
        }
        if (l.contains("sous-detail") || l.contains("sous detail")) {
            return SOUS_DETAIL;
        }
        if (l.contains("detail quantitatif") || l.matches(".*\\bdqe\\b.*")) {
            return DQE;
        }
        if (l.contains("bordereau") || l.matches(".*\\bbpu?\\b.*")) {
            return travaux ? DQE : BORDEREAU;
        }
        if (l.contains("conformite") || l.contains("specifications techniques") || l.contains("fiche technique proposee")) {
            return travaux ? null : CONFORMITE;
        }
        if (l.contains("calendrier") || l.contains("delai de livraison")) {
            return travaux ? null : CALENDRIER;
        }
        if (!travaux) {
            return null;
        }
        if (l.contains("coefficient k") || l.matches(".*\\bk1\\b.*")) {
            return K1;
        }
        if (l.contains("chiffre d'affaires") || l.contains("chiffres d'affaires") || l.contains("capacite financiere")
                || l.contains("liquidite") || l.contains("ligne de credit") || l.contains("marches similaires")
                || l.contains("references")) {
            return CAPACITES;
        }
        if (l.contains("personnel")) {
            return PERSONNEL;
        }
        if (l.contains("materiel")) {
            return MATERIEL;
        }
        return null;
    }


    /**
     * ⚠️ 2026-10-05 (recette du lot 5, §B5) — les documents remplis qu'une offre porte : {@code BORDEREAU} (bordereau ou DQE),
     * {@code CONFORMITE}, {@code CAPACITES} — ceux que {@link #pdf} sait produire pour elle.
     */
    public static List<String> parties(JsonNode f) {
        List<String> out = new ArrayList<>();
        if (f.path("bordereau").isArray() && !f.path("bordereau").isEmpty()) {
            out.add(BORDEREAU);
        }
        if (f.path("conformite").isArray() && !f.path("conformite").isEmpty()) {
            out.add(CONFORMITE);
        }
        if (f.path("capacites").isObject() && !f.path("capacites").isEmpty()) {
            out.add(CAPACITES);
        }
        return out;
    }
    /** La fiche validée en vigueur a-t-elle un besoin (dépôt par formulaires) ? */
    boolean formulaires(Long idDmc, Integer idFiche, String categorie) {
        return !CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie) && !besoin.lister(idFiche).isEmpty();
    }

    // ------------------------------------------------------------------ §B3 l'analyse à l'ouverture

    /** Le résultat de l'analyse : les totaux recalculés ({@code ht}, {@code tva}, {@code ttc}, {@code htMin}, {@code ttcMin}, {@code parSerie}) et les alertes. */
    public record Analyse(Map<String, Object> totaux, List<SeanceDto.Alerte> alertes) {
    }

    /**
     * ⚠️ 2026-10-07 (évaluation des offres, §B3.1) — une correction arithmétique proposée : {@code ligne} (l'article, nul pour le
     * total), {@code avant} / {@code apres} en montants hors taxes, {@code regle} ∈ {@code LETTRES_PREVALENT} · {@code PU_PREVAUT}.
     */
    public record Correction(Integer ligne, String libelle, BigDecimal avant, BigDecimal apres, String regle) {
    }

    /**
     * ⚠️ 2026-10-07 (évaluation des offres, §B3.1) — les corrections que le bordereau scellé appelle, proposées à la CAO (elle les
     * retient ou non) : par article, le prix en lettres qui diffère des chiffres (les lettres font foi, montant de la ligne = lettres ×
     * quantité) ; puis le montant HT de l'acte d'engagement qui diffère de Σ prix unitaire × quantité (le prix unitaire prévaut).
     * Corrections cumulables : leur somme mène de l'acte d'engagement au total recalculé sur les lettres. Vide sans bordereau.
     */
    public List<Correction> corrections(Long idDmc, Integer lot, JsonNode f, JsonNode acteEngagement) {
        List<Correction> out = new ArrayList<>();
        Contexte c = contexte(idDmc).orElse(null);
        if (c == null || f == null || !f.isObject()) {
            return out;
        }
        BesoinEnLigneDto b = construire(idDmc, c.etat(), c.idFiche(), null);
        BesoinEnLigneDto.Lot l = b.lots().stream().filter(x -> Objects.equals(x.numero(), lot) || b.lots().size() == 1).findFirst().orElse(null);
        if (l == null) {
            return out;
        }
        boolean aCommande = TypeMarcheDao.A_COMMANDE.name().equals(b.typeMarche());
        Map<Integer, JsonNode> lignes = new HashMap<>();
        for (JsonNode x : f.path("bordereau")) {
            lignes.put(x.path("idArticle").asInt(), x);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BesoinEnLigneDto.Article a : l.articles()) {
            JsonNode x = lignes.get(a.idArticle());
            BigDecimal pu = x == null ? null : SeanceService.montant(valeur(x.path("prixUnitaireHt")));
            if (pu == null) {
                continue;
            }
            BigDecimal q = aCommande ? a.quantiteMax() : a.quantite();
            q = q == null ? BigDecimal.ZERO : q;
            total = total.add(pu.multiply(q));
            String enLettres = x.path("prixEnLettres").asString(null);
            BigDecimal lu = LettresEnNombre.lire(enLettres);
            if (enLettres != null && !enLettres.isBlank() && lu != null && lu.subtract(pu).abs().compareTo(BigDecimal.ONE) >= 0) {
                out.add(new Correction(a.idArticle(), nom(a) + " : prix en lettres « " + enLettres.trim() + " » (" + lisible(lu) + ") au lieu de "
                        + lisible(pu), arrondi(pu.multiply(q)), arrondi(lu.multiply(q)), "LETTRES_PREVALENT"));
            }
        }
        BigDecimal ae = acteEngagement == null ? null : SeanceService.montant(valeur(acteEngagement.path("montantHt")));
        BigDecimal tolerance = BigDecimal.valueOf(Math.max(1, l.articles().size()));
        if (ae != null && ae.subtract(total).abs().compareTo(tolerance) > 0) {
            out.add(new Correction(null, "Montant HT de l'acte d'engagement ramené à la somme des prix unitaires × quantités", arrondi(ae),
                    arrondi(total), "PU_PREVAUT"));
        }
        return out;
    }

    /**
     * Analyse la partie {@code formulaires} d'un manifeste (format 3) : totaux recalculés depuis le bordereau et les quantités de la
     * fiche, comparés aux totaux déclarés ({@code TOTAL_DIVERGENT}) et à l'acte d'engagement ({@code AE_DIVERGENT}) — un écart de plus
     * de 1 Ar par ligne ; puis {@code PRIX_MANQUANT}, {@code LETTRES_DIVERGENTES}, {@code PLAFOND_DEPASSE}, {@code NON_CONFORME},
     * {@code LIVRAISON_HORS_DELAI} et, si le manifeste les porte (lot 5b), {@code CA_INSUFFISANT}, {@code LIQUIDITE_INSUFFISANTE},
     * {@code REFERENCES_INSUFFISANTES}, {@code PERSONNEL_INCOMPLET}, {@code MATERIEL_INCOMPLET}, {@code SOUS_DETAIL_INCOHERENT}.
     *
     * @param ouverture la date de la séance : le délai de livraison court à partir d'elle (la notification n'est pas connue)
     */
    public Analyse analyser(Long idDmc, Integer lot, JsonNode f, JsonNode acteEngagement, LocalDate ouverture) {
        Contexte c = contexte(idDmc).orElse(null);
        List<SeanceDto.Alerte> alertes = new ArrayList<>();
        if (c == null) {
            return new Analyse(Map.of(), alertes);
        }
        BesoinEnLigneDto b = construire(idDmc, c.etat(), c.idFiche(), null);
        BesoinEnLigneDto.Lot l = b.lots().stream().filter(x -> Objects.equals(x.numero(), lot) || b.lots().size() == 1).findFirst().orElse(null);
        if (l == null) {
            return new Analyse(Map.of(), alertes);
        }
        boolean aCommande = TypeMarcheDao.A_COMMANDE.name().equals(b.typeMarche());
        boolean travaux = CategorieDao.TRAVAUX.name().equals(b.categorie());
        BigDecimal taux = b.tauxTva() == null ? BigDecimal.ZERO : b.tauxTva();
        Map<Integer, JsonNode> lignes = new HashMap<>();
        for (JsonNode x : f.path("bordereau")) {
            lignes.put(x.path("idArticle").asInt(), x);
        }
        BigDecimal ht = BigDecimal.ZERO;
        BigDecimal htMin = BigDecimal.ZERO;
        Map<String, BigDecimal> parSerie = new LinkedHashMap<>();
        Map<Integer, BigDecimal> montants = new HashMap<>();
        List<String> manquants = new ArrayList<>();
        List<String> lettres = new ArrayList<>();
        List<String> tardives = new ArrayList<>();
        Map<Integer, BigDecimal> prix = new HashMap<>();
        for (BesoinEnLigneDto.Article a : l.articles()) {
            JsonNode x = lignes.get(a.idArticle());
            BigDecimal pu = x == null ? null : SeanceService.montant(valeur(x.path("prixUnitaireHt")));
            if (pu == null) {
                manquants.add(nom(a));
                continue;
            }
            prix.put(a.idArticle(), pu);
            BigDecimal q = aCommande ? a.quantiteMax() : a.quantite();
            BigDecimal m = q == null ? BigDecimal.ZERO : pu.multiply(q);
            montants.put(a.idArticle(), m);
            ht = ht.add(m);
            if (aCommande && a.quantiteMin() != null) {
                htMin = htMin.add(pu.multiply(a.quantiteMin()));
            }
            if (travaux) {
                parSerie.merge(a.serie() == null ? "" : a.serie(), m, BigDecimal::add);
            }
            String enLettres = x.path("prixEnLettres").asString(null);
            BigDecimal lu = LettresEnNombre.lire(enLettres);
            if (enLettres != null && !enLettres.isBlank() && lu != null && lu.subtract(pu).abs().compareTo(BigDecimal.ONE) >= 0) {
                lettres.add(nom(a) + " (" + enLettres.trim() + " ≠ " + lisible(pu) + ")");
            }
            LocalDate livraison = date(x.path("dateLivraison").asString(null));
            if (!travaux && livraison != null && l.delaiExecution() != null && l.delaiExecution().valeur() != null && ouverture != null
                    && livraison.isAfter(ouverture.plusDays(l.delaiExecution().valeur()))) {
                tardives.add(nom(a));
            }
        }
        BigDecimal tva = ht.multiply(taux).divide(CENT, 0, RoundingMode.HALF_UP);
        BigDecimal ttc = ht.add(tva);
        Map<String, Object> totaux = new LinkedHashMap<>();
        totaux.put("ht", arrondi(ht));
        totaux.put("tva", tva);
        totaux.put("ttc", arrondi(ttc));
        if (aCommande) {
            BigDecimal tvaMin = htMin.multiply(taux).divide(CENT, 0, RoundingMode.HALF_UP);
            totaux.put("htMin", arrondi(htMin));
            totaux.put("ttcMin", arrondi(htMin.add(tvaMin)));
        }
        if (travaux) {
            List<Map<String, Object>> series = new ArrayList<>();
            parSerie.forEach((s, m) -> series.add(Map.of("serie", s, "ht", arrondi(m))));
            totaux.put("parSerie", series);
        }
        BigDecimal tolerance = BigDecimal.valueOf(Math.max(1, l.articles().size()));
        if (!manquants.isEmpty()) {
            alertes.add(new SeanceDto.Alerte("PRIX_MANQUANT", "Sans prix : " + String.join(", ", manquants) + "."));
        }
        JsonNode declares = f.path("totaux");
        List<String> ecarts = new ArrayList<>();
        ecart(ecarts, "HT", SeanceService.montant(valeur(declares.path("ht"))), ht, tolerance);
        ecart(ecarts, "TTC", SeanceService.montant(valeur(declares.path("ttc"))), ttc, tolerance);
        if (!ecarts.isEmpty()) {
            alertes.add(new SeanceDto.Alerte("TOTAL_DIVERGENT", "Totaux déclarés différents des totaux recalculés : " + String.join(" ; ", ecarts) + "."));
        }
        ecarts.clear();
        ecart(ecarts, "HT", SeanceService.montant(valeur(acteEngagement.path("montantHt"))), ht, tolerance);
        ecart(ecarts, "TTC", SeanceService.montant(valeur(acteEngagement.path("montantTtc"))), ttc, tolerance);
        if (!ecarts.isEmpty()) {
            alertes.add(new SeanceDto.Alerte("AE_DIVERGENT", "Montants de l'acte d'engagement différents du bordereau : " + String.join(" ; ", ecarts) + "."));
        }
        if (!lettres.isEmpty()) {
            alertes.add(new SeanceDto.Alerte("LETTRES_DIVERGENTES", "Prix en lettres différents des chiffres (les lettres font foi) : "
                    + String.join(", ", lettres) + "."));
        }
        List<String> plafonds = new ArrayList<>();
        for (BesoinEnLigneDto.Article a : l.articles()) {
            BigDecimal m = montants.get(a.idArticle());
            if (a.plafond() != null && m != null && ht.signum() > 0
                    && m.multiply(CENT).compareTo(ht.multiply(a.plafond())) > 0) {
                plafonds.add(nom(a) + " (" + m.multiply(CENT).divide(ht, 1, RoundingMode.HALF_UP) + " % pour " + a.plafond().stripTrailingZeros().toPlainString()
                        + " % au plus)");
            }
        }
        if (!plafonds.isEmpty()) {
            alertes.add(new SeanceDto.Alerte("PLAFOND_DEPASSE", "Plafond dépassé : " + String.join(", ", plafonds) + "."));
        }
        List<String> nonConformes = new ArrayList<>();
        for (JsonNode x : f.path("conformite")) {
            for (JsonNode k : x.path("caracteristiques")) {
                if (k.has("conforme") && !k.path("conforme").asBoolean(true)) {
                    int id = x.path("idArticle").asInt();
                    nonConformes.add(l.articles().stream().filter(a -> a.idArticle() != null && a.idArticle() == id).map(FormulairesEnLigne::nom)
                            .findFirst().orElse("article " + id));
                    break;
                }
            }
        }
        if (!nonConformes.isEmpty()) {
            alertes.add(new SeanceDto.Alerte("NON_CONFORME", "Caractéristique déclarée non conforme : " + String.join(", ", nonConformes) + "."));
        }
        if (!tardives.isEmpty()) {
            alertes.add(new SeanceDto.Alerte("LIVRAISON_HORS_DELAI", "Livraison au-delà du délai de " + l.delaiExecution().valeur()
                    + " jours de la fiche (compté depuis l'ouverture des plis) : " + String.join(", ", tardives) + "."));
        }
        if (travaux) {
            capacites(f, l.qualification(), ttc, alertes);
            moyens(f, b, alertes);
            sousDetails(f, prix, l, alertes);
        }
        return new Analyse(totaux, alertes);
    }

    static void capacites(JsonNode f, BesoinEnLigneDto.Qualification q, BigDecimal ttc, List<SeanceDto.Alerte> alertes) {
        JsonNode cap = f.path("capacites");
        if (cap.isMissingNode() || cap.isNull() || q == null) {
            return;
        }
        int annee = Year.now().getValue();
        BesoinEnLigneDto.ChiffreAffaires ca = q.chiffreAffaires();
        if (ca != null && ca.montant() != null && cap.has("chiffresAffaires")) {
            List<BigDecimal> valeurs = new ArrayList<>();
            for (JsonNode x : cap.path("chiffresAffaires")) {
                int a = x.path("annee").asInt(0);
                BigDecimal m = SeanceService.montant(valeur(x.path("montant")));
                if (m != null && (ca.annees() == null || a > annee - ca.annees() - 1)) {
                    valeurs.add(m);
                }
            }
            valeurs.sort(Comparator.reverseOrder());
            List<BigDecimal> retenues = ca.meilleures() == null ? valeurs : valeurs.subList(0, Math.min(ca.meilleures(), valeurs.size()));
            BigDecimal moyenne = retenues.isEmpty() ? BigDecimal.ZERO
                    : retenues.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(retenues.size()), 0, RoundingMode.HALF_UP);
            if (moyenne.compareTo(ca.montant()) < 0) {
                alertes.add(new SeanceDto.Alerte("CA_INSUFFISANT", "Chiffre d'affaires moyen de " + lisible(moyenne) + " pour un minimum de "
                        + lisible(ca.montant()) + "."));
            }
        }
        BigDecimal exigee = q.liquiditeMontant();
        if (q.liquiditePourcentage() != null && ttc.signum() > 0) {
            BigDecimal pourcentage = ttc.multiply(q.liquiditePourcentage()).divide(CENT, 0, RoundingMode.HALF_UP);
            exigee = exigee == null || pourcentage.compareTo(exigee) > 0 ? pourcentage : exigee;
        }
        if (exigee != null && cap.has("liquidite")) {
            BigDecimal m = SeanceService.montant(valeur(cap.path("liquidite").path("montant")));
            if (m == null || m.compareTo(exigee) < 0) {
                alertes.add(new SeanceDto.Alerte("LIQUIDITE_INSUFFISANTE", "Liquidité de " + (m == null ? "—" : lisible(m)) + " pour un minimum de "
                        + lisible(exigee) + "."));
            }
        }
        BesoinEnLigneDto.References r = q.references();
        if (r != null && r.montant() != null && cap.has("references")) {
            List<BigDecimal> valeurs = new ArrayList<>();
            for (JsonNode x : cap.path("references")) {
                int a = x.path("annee").asInt(0);
                BigDecimal m = SeanceService.montant(valeur(x.path("montant")));
                if (m != null && (r.annees() == null || a > annee - r.annees() - 1)) {
                    valeurs.add(m);
                }
            }
            valeurs.sort(Comparator.reverseOrder());
            int n = r.nombre() == null || r.nombre() < 1 ? 1 : r.nombre();
            BigDecimal cumul = valeurs.subList(0, Math.min(n, valeurs.size())).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (cumul.compareTo(r.montant()) < 0) {
                alertes.add(new SeanceDto.Alerte("REFERENCES_INSUFFISANTES", "Marchés de référence : " + lisible(cumul) + " (" + n
                        + " marché(s) au plus) pour un minimum de " + lisible(r.montant()) + "."));
            }
        }
    }

    static void moyens(JsonNode f, BesoinEnLigneDto b, List<SeanceDto.Alerte> alertes) {
        if (f.has("personnel")) {
            List<String> manque = new ArrayList<>();
            for (PersonnelExigeDto p : b.personnel()) {
                int n = 0;
                for (JsonNode x : f.path("personnel")) {
                    if (Objects.equals(p.getIdPersonnel(), x.path("idPersonnel").asInt(-1))
                            && (p.getExperienceAnnees() == null || x.path("experienceAnnees").asInt(0) >= p.getExperienceAnnees())) {
                        n++;
                    }
                }
                if (n < (p.getNombre() == null ? 1 : p.getNombre())) {
                    manque.add(p.getPoste());
                }
            }
            if (!manque.isEmpty()) {
                alertes.add(new SeanceDto.Alerte("PERSONNEL_INCOMPLET", "Personnel clé manquant ou sans l'expérience exigée : " + String.join(", ", manque) + "."));
            }
        }
        if (f.has("materiel")) {
            List<String> manque = new ArrayList<>();
            for (MaterielExigeDto m : b.materiel()) {
                int nombre = 0;
                int enPropre = 0;
                for (JsonNode x : f.path("materiel")) {
                    if (Objects.equals(m.getIdMateriel(), x.path("idMateriel").asInt(-1))) {
                        nombre += x.path("nombre").asInt(0);
                        enPropre += x.path("enPropre").asInt(0);
                    }
                }
                if (nombre < (m.getNombre() == null ? 1 : m.getNombre()) || m.getMinimumEnPropre() != null && enPropre < m.getMinimumEnPropre()) {
                    manque.add(m.getDesignation());
                }
            }
            if (!manque.isEmpty()) {
                alertes.add(new SeanceDto.Alerte("MATERIEL_INCOMPLET", "Matériel manquant ou en propre insuffisant : " + String.join(", ", manque) + "."));
            }
        }
    }

    static void sousDetails(JsonNode f, Map<Integer, BigDecimal> prix, BesoinEnLigneDto.Lot l, List<SeanceDto.Alerte> alertes) {
        List<String> ecarts = new ArrayList<>();
        for (JsonNode x : f.path("sousDetails")) {
            int id = x.path("idArticle").asInt(-1);
            BigDecimal calcule = SeanceService.montant(valeur(x.path("prixCalcule")));
            BigDecimal pu = prix.get(id);
            if (calcule != null && pu != null && pu.signum() > 0
                    && calcule.subtract(pu).abs().multiply(CENT).compareTo(pu) > 0) {
                ecarts.add(l.articles().stream().filter(a -> a.idArticle() != null && a.idArticle() == id).map(FormulairesEnLigne::nom)
                        .findFirst().orElse("article " + id) + " (" + lisible(calcule) + " pour " + lisible(pu) + ")");
            }
        }
        if (!ecarts.isEmpty()) {
            alertes.add(new SeanceDto.Alerte("SOUS_DETAIL_INCOHERENT", "Prix du sous-détail écarté de plus de 1 % du bordereau : "
                    + String.join(", ", ecarts) + "."));
        }
    }


    // ------------------------------------------------------------------ §B3.4 le formulaire rempli, en PDF

    /**
     * Le formulaire rempli que la commission imprime, produit à la volée (Q2) : {@code BORDEREAU} (ou {@code DQE} aux travaux : le même
     * document, regroupé par série), {@code CONFORMITE}, {@code CAPACITES}. 400 {@code FORMULAIRE_INCONNU} ; 404 si l'offre ne porte
     * pas ce formulaire.
     */
    public byte[] pdf(String type, BesoinEnLigneDto.Lot lot, JsonNode f, String entete, Long idDmc) {
        String t = type == null ? "" : type.toUpperCase(Locale.ROOT).replace(".PDF", "");
        List<DocumentLibre.Element> el = new ArrayList<>();
        List<BesoinEnLigneDto.Article> articles = lot == null ? List.of() : lot.articles();
        Map<Integer, BesoinEnLigneDto.Article> parId = new HashMap<>();
        articles.forEach(a -> parId.put(a.idArticle(), a));
        switch (t) {
            case BORDEREAU, DQE -> {
                exigerPartie(f, "bordereau");
                el.add(titre(DQE.equals(t) ? "DÉTAIL QUANTITATIF ET ESTIMATIF — BORDEREAU DES PRIX" : "BORDEREAU DES PRIX", entete));
                List<List<List<String>>> lignes = new ArrayList<>();
                lignes.add(cellules("N°", "Désignation", "Unité", "Quantité", "Prix unitaire HT", "Prix en lettres", "Montant HT"));
                BigDecimal total = BigDecimal.ZERO;
                for (JsonNode x : f.path("bordereau")) {
                    BesoinEnLigneDto.Article a = parId.get(x.path("idArticle").asInt(-1));
                    BigDecimal pu = SeanceService.montant(valeur(x.path("prixUnitaireHt")));
                    BigDecimal q = a == null ? null : a.quantite() != null ? a.quantite() : a.quantiteMax();
                    BigDecimal m = pu == null || q == null ? null : pu.multiply(q);
                    total = m == null ? total : total.add(m);
                    lignes.add(cellules(a == null ? "?" : a.numeroPrix() != null ? a.numeroPrix() : String.valueOf(a.ordre()),
                            a == null ? "article " + x.path("idArticle").asInt() : a.designation(), a == null ? "" : Objects.toString(a.unite(), ""),
                            a == null ? "" : quantite(a), pu == null ? "—" : lisible(pu), x.path("prixEnLettres").asString(""),
                            m == null ? "—" : lisible(m)));
                }
                lignes.add(cellules("", "Total HT", "", "", "", "", lisible(total)));
                el.add(new DocumentLibre.Tableau(7, lignes));
            }
            case CONFORMITE -> {
                exigerPartie(f, "conformite");
                el.add(titre("TABLEAU DE CONFORMITÉ TECHNIQUE", entete));
                for (JsonNode x : f.path("conformite")) {
                    BesoinEnLigneDto.Article a = parId.get(x.path("idArticle").asInt(-1));
                    el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, (a == null ? "Article " + x.path("idArticle").asInt()
                            : a.ordre() + ". " + a.designation()) + " — marque : " + x.path("marque").asString("—") + ", modèle : "
                            + x.path("modele").asString("—")));
                    List<List<List<String>>> lignes = new ArrayList<>();
                    lignes.add(cellules("Caractéristique", "Exigée", "Proposée", "Conforme"));
                    for (JsonNode k : x.path("caracteristiques")) {
                        int id = k.path("idCaracteristique").asInt(-1);
                        BesoinEnLigneDto.Caracteristique c = a == null ? null : a.caracteristiques().stream()
                                .filter(y -> y.idCaracteristique() != null && y.idCaracteristique() == id).findFirst().orElse(null);
                        lignes.add(cellules(c == null ? "caractéristique " + id : c.libelle(), c == null ? "" : Objects.toString(c.exigence(), ""),
                                k.path("proposee").asString(""), k.has("conforme") ? (k.path("conforme").asBoolean() ? "oui" : "NON") : "—"));
                    }
                    el.add(new DocumentLibre.Tableau(4, lignes));
                }
            }
            case CAPACITES -> {
                exigerPartie(f, "capacites");
                JsonNode c = f.path("capacites");
                el.add(titre("CAPACITÉS FINANCIÈRES ET RÉFÉRENCES", entete));
                List<List<List<String>>> ca = new ArrayList<>();
                ca.add(cellules("Année", "Chiffre d'affaires"));
                for (JsonNode x : c.path("chiffresAffaires")) {
                    ca.add(cellules(x.path("annee").asString(""), montantTexte(x.path("montant"))));
                }
                el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Chiffres d'affaires"));
                el.add(new DocumentLibre.Tableau(2, ca));
                JsonNode l = c.path("liquidite");
                el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Liquidité"));
                el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, montantTexte(l.path("montant")) + " — " + l.path("nature").asString("—")
                        + " — " + l.path("emetteur").asString("—")));
                List<List<List<String>>> refs = new ArrayList<>();
                refs.add(cellules("Objet", "Maître d'ouvrage", "Année", "Montant"));
                for (JsonNode x : c.path("references")) {
                    refs.add(cellules(x.path("objet").asString(""), x.path("maitreOuvrage").asString(""), x.path("annee").asString(""),
                            montantTexte(x.path("montant"))));
                }
                el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Marchés de référence"));
                el.add(new DocumentLibre.Tableau(4, refs));
            }
            default -> throw new cnm.prs.exception.BadRequestException("Formulaire inconnu : " + type + " (BORDEREAU, DQE, CONFORMITE ou CAPACITES).",
                    "FORMULAIRE_INCONNU");
        }
        return generateur.generer(new DocumentLibre("FORMULAIRE_" + t, null, el, "Procédure " + idDmc)).stream()
                .filter(x -> "pdf".equals(x.extension())).findFirst().orElseThrow().contenu();
    }

    private static void exigerPartie(JsonNode f, String partie) {
        if (!f.path(partie).isArray() && !f.path(partie).isObject() || f.path(partie).isEmpty()) {
            throw new cnm.prs.exception.ResourceNotFoundException("L'offre ne porte pas ce formulaire (" + partie + ").");
        }
    }

    private static DocumentLibre.Paragraphe titre(String titre, String entete) {
        return new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, titre + "\n" + entete);
    }

    private static List<List<String>> cellules(String... textes) {
        List<List<String>> out = new ArrayList<>();
        for (String t : textes) {
            out.add(List.of(t == null ? "" : t));
        }
        return out;
    }

    private static String quantite(BesoinEnLigneDto.Article a) {
        if (a.quantite() != null) {
            return lisible(a.quantite());
        }
        return (a.quantiteMin() == null ? "—" : lisible(a.quantiteMin())) + " à " + (a.quantiteMax() == null ? "—" : lisible(a.quantiteMax()));
    }

    private static String montantTexte(JsonNode n) {
        BigDecimal m = SeanceService.montant(valeur(n));
        return m == null ? "—" : lisible(m);
    }
    // ------------------------------------------------------------------ outils

    /** La version validée en vigueur et sa fiche. */
    record Contexte(FicheMarcheService.EtatVersion etat, Integer idFiche) {
    }

    Optional<Contexte> contexte(Long idDmc) {
        try {
            return fiches.etatValide(idDmc).flatMap(e -> ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                    .filter(x -> e.version().equals(x.getNumeroVersion())).map(FicheMarche::getIdFiche).findFirst().map(id -> new Contexte(e, id)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static void ecart(List<String> ecarts, String libelle, BigDecimal declare, BigDecimal calcule, BigDecimal tolerance) {
        if (declare != null && declare.subtract(calcule).abs().compareTo(tolerance) > 0) {
            ecarts.add(libelle + " " + lisible(declare) + " pour " + lisible(calcule));
        }
    }

    private static Object valeur(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        return n.isNumber() ? n.decimalValue() : n.asString();
    }

    private static String nom(BesoinEnLigneDto.Article a) {
        return (a.numeroPrix() != null ? "prix n° " + a.numeroPrix() : "article " + a.ordre()) + " « " + a.designation() + " »";
    }

    private static BigDecimal arrondi(BigDecimal m) {
        return m.setScale(m.stripTrailingZeros().scale() > 0 ? 2 : 0, RoundingMode.HALF_UP);
    }

    static String lisible(BigDecimal m) {
        java.text.DecimalFormatSymbols sym = new java.text.DecimalFormatSymbols(Locale.FRANCE);
        sym.setGroupingSeparator(' ');
        return new java.text.DecimalFormat("#,##0.##", sym).format(m);
    }

    private static LocalDate date(String s) {
        try {
            return s == null || s.isBlank() ? null : LocalDate.parse(s.trim().substring(0, Math.min(10, s.trim().length())));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BigDecimal montant(String s) {
        return SeanceService.montant(s);
    }

    private static Integer entier(String s) {
        if (s == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d+)").matcher(s);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }
}
