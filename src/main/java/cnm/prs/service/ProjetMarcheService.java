package cnm.prs.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.EntrepriseCandidatDto;
import cnm.prs.dto.EvaluationDto;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Offre;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.LotRepository;

/**
 * ⚠️ 2026-10-10 (demande front « projet-de-marche », relecture du pilote sur le lot 1 de la procédure 40 ; arbitrages du 10/10 : un lot =
 * un marché, document produit sans saisie nouvelle, renvoi aux cahiers des charges avec les valeurs courtes de la fiche) — le projet de
 * marché d'un lot, conforme à la loi n° 2016-055 :
 * <ul>
 *   <li><strong>art. 28</strong> — l'objet est celui de l'appel d'offres, puis du <strong>seul lot</strong> du marché (« Lot n°01 :
 *   désignation » du plan), sans l'énumération des autres lots ;</li>
 *   <li><strong>art. 60</strong> — toutes les mentions obligatoires, remplies quand l'application connaît la donnée, sinon laissées
 *   « …… » et nommées, pour que la PRMP les complète dans le Word avant la signature.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ProjetMarcheService {

    static final String LOI = "la loi n° 2016-055 du 25 janvier 2017 portant Code des marchés publics";
    static final String A_COMPLETER = "……";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Codes de la fiche lus par le projet (valeurs courtes ; « #lot » d'abord pour un champ par lot). */
    static final String NUMERO_AO = "B02-OB-03";
    static final String COMPTABLE_FS = "B03-NA-03";
    static final String COMPTABLE_TRAVAUX = "B03-NT-01";
    static final String IMPUTATION = "B02-MW-03";
    static final String DOMICILIATION = "B08-PA-01";
    static final String DELAI_JOURS = "B09-DX-01";
    static final String PENALITE_TAUX = "B09-PE-02";
    static final String PENALITE_PLAFOND_TRAVAUX = "B09-PE-03";
    static final String PENALITE_PLAFOND = "B09-PR-02";
    static final String RECEPTION_PAR_TRANCHES = "B09-RP-01";
    static final String INDEMNITE_RESILIATION = "B10-IR-03";
    static final String INDEMNITE_RESILIATION_PI = "B10-IN-01";

    private final FicheMarcheService fiches;
    private final DossierMecRepository dmcs;
    private final LotRepository lots;
    private final MandatService mandats;
    private final EntrepriseCandidatService entreprises;

    public ProjetMarcheService(FicheMarcheService fiches, DossierMecRepository dmcs, LotRepository lots, MandatService mandats,
            EntrepriseCandidatService entreprises) {
        this.fiches = fiches;
        this.dmcs = dmcs;
        this.lots = lots;
        this.mandats = mandats;
        this.entreprises = entreprises;
    }

    /** Ce que le projet lit hors de la fiche : la proposition, l'offre retenue, les valeurs du plan, la PRMP du marché, la notification. */
    public record Entree(Long idDmc, Integer lot, EvaluationDto.Proposition proposition, Offre offre, Map<String, String> plan, boolean allotie,
            boolean pi, String idPrmp, LocalDate dateNotification) {
    }

    public DocumentLibre document(Entree in) {
        FicheMarcheService.EtatVersion v = fiches.etatValide(in.idDmc()).orElse(null);
        Valeurs val = new Valeurs(v, in.lot());
        String offreMot = in.pi() ? "la proposition" : "l'offre";
        String objet = objetDuMarche(v == null ? in.plan().get("OBJET") : v.etat().getDesignationMarche(), in.allotie() ? lotLibelle(in) : null);
        String numero = val.get(NUMERO_AO);
        EntrepriseCandidatDto.Entreprise e = entreprise(in.offre());
        EvaluationDto.Proposition p = in.proposition();

        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "PROJET DE MARCHÉ"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, objet));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        para(el, "Entre :");
        para(el, Objects.toString(in.plan().get("ENTITE"), "l'Autorité contractante")
                + (in.plan().get("MINISTERE") == null ? "" : " (" + in.plan().get("MINISTERE") + ")")
                + ", représentée par sa Personne responsable des marchés publics, " + signataire(in) + ", ci-après « l'Autorité contractante »,");
        para(el, "et :");
        para(el, in.offre().getRaisonSociale() + ", NIF " + in.offre().getNif() + (e == null || e.adresse() == null ? "" : ", " + e.adresse())
                + (e == null || e.representant() == null ? ", représentée par " + A_COMPLETER + " (nom et qualité du signataire)"
                        : ", représentée par " + e.representant().prenom() + " " + e.representant().nom()
                                + (e.representant().fonction() == null ? "" : ", " + e.representant().fonction()))
                + ", ci-après « le Titulaire ».");

        sous(el, "Article 1 — Objet");
        para(el, "Le présent marché a pour objet : " + objet + ", issu de l'appel d'offres" + (numero == null ? "" : " n° " + numero) + ".");

        sous(el, "Article 2 — Base légale");
        para(el, baseLegale(in.plan().get("MODE"), in.pi()));

        sous(el, "Article 3 — Pièces constitutives");
        para(el, "Le marché est constitué, par ordre de priorité : l'acte d'engagement du Titulaire ; le cahier des clauses administratives "
                + "particulières (ou le cahier des prescriptions spéciales) et ses annexes, les spécifications techniques ou les termes de "
                + "référence ; " + offreMot + " du Titulaire (n° " + in.offre().getNumero() + "), dont le bordereau des prix ; le cahier des "
                + "clauses administratives générales — tels qu'ils figurent au dossier et à " + offreMot + " retenue, sans modification "
                + "substantielle.");

        sous(el, "Article 4 — Prix");
        para(el, "Le montant du marché est fixé à " + (p.montant() == null ? A_COMPLETER + " (montant hors taxes)"
                : FormulairesEnLigne.lisible(p.montant()) + " Ariary hors taxes (" + NombreEnLettres.cardinal(p.montant().longValue()) + " ariary)")
                + (p.montantTtc() == null ? "" : ", soit " + FormulairesEnLigne.lisible(p.montantTtc()) + " Ariary toutes taxes comprises")
                + ", tel qu'il résulte de l'évaluation (prix corrigé, rabais déduit), selon les prix et les modalités de leur détermination "
                + "fixés au bordereau des prix et au cahier des clauses administratives particulières.");

        sous(el, "Article 5 — Délai d'exécution");
        para(el, "Le délai d'exécution est de " + delai(p.delai(), val) + ", à compter de la date fixée par l'ordre de service de commencer "
                + "les prestations. " + sanction(val));

        sous(el, "Article 6 — Réception");
        para(el, "La réception des prestations" + ("OUI".equalsIgnoreCase(val.get(RECEPTION_PAR_TRANCHES))
                ? ", et leur réception partielle par tranches," : " et, le cas échéant, leur réception partielle,")
                + " sont prononcées dans les conditions du cahier des clauses administratives particulières (ou du cahier des prescriptions "
                + "spéciales) et du cahier des clauses administratives générales.");

        sous(el, "Article 7 — Règlement");
        para(el, "Les sommes dues au Titulaire sont réglées, après constatation du service fait et selon les modalités de réception, dans les "
                + "conditions du cahier des clauses administratives particulières (ou du cahier des prescriptions spéciales) et du cahier des "
                + "clauses administratives générales, dans le délai de paiement de l'article 74 de " + LOI + ".");
        para(el, "Comptable public assignataire chargé du paiement : " + Objects.toString(val.premiere(COMPTABLE_FS, COMPTABLE_TRAVAUX), A_COMPLETER) + ".");
        para(el, "Imputation budgétaire : " + Objects.toString(val.premiere(IMPUTATION), Objects.toString(in.plan().get("COMPTES"), A_COMPLETER)) + ".");
        para(el, "Domiciliation bancaire des paiements : " + Objects.toString(val.premiere(DOMICILIATION), A_COMPLETER)
                + " (banque, code banque, code guichet, numéro de compte, clé)" + ".");

        sous(el, "Article 8 — Résiliation");
        String indemnite = val.premiere(INDEMNITE_RESILIATION, INDEMNITE_RESILIATION_PI);
        para(el, "Le marché peut être résilié dans les conditions de l'article 76 de " + LOI + ", du cahier des clauses administratives "
                + "générales et du cahier des clauses administratives particulières (ou du cahier des prescriptions spéciales)"
                + (indemnite == null ? "" : " ; l'indemnité de résiliation due au Titulaire est de " + pourcent(indemnite)) + ".");

        sous(el, "Article 9 — Notification et entrée en vigueur");
        para(el, "Date de notification du marché : " + (in.dateNotification() == null ? A_COMPLETER : DATE.format(in.dateNotification())) + ".");
        para(el, "Le marché prend effet à sa notification au Titulaire, après son approbation et l'avis de l'organe de contrôle.");

        if (international(in.plan().get("MODE"))) {
            sous(el, "Article 10 — Droit applicable");
            para(el, "Le présent marché est régi par le droit de la République de Madagascar.");
        }

        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        para(el, "Pour le Titulaire : ……………………………………  (nom, qualité, date et signature)");
        para(el, "Pour l'Autorité contractante, la Personne responsable des marchés publics : ……………………………………  (date et signature)");
        return new DocumentLibre("PROJET_MARCHE", in.allotie() ? in.lot() : null, el,
                "Procédure " + in.idDmc() + " — projet de marché" + (in.allotie() ? ", lot " + in.lot() : ""));
    }

    // ------------------------------------------------------------------ art. 28 : l'objet du lot

    /** « répartis en cinq (05) lots : Lot n°01 … » : l'énumération des lots qui suit l'objet de l'appel d'offres. */
    private static final Pattern ENUMERATION = Pattern.compile(
            "\\s*[,;:]?\\s*(?:(?:r[ée]partie?s?|divis[ée]e?s?|scind[ée]e?s?|r[ée]parti[es]*)\\s+)?en\\s+\\S+(?:\\s*\\(\\s*\\d+\\s*\\))?\\s+lots?\\b.*$"
                    + "|\\s*[,;:]?\\s*\\(?\\s*lot\\s*n?°?\\s*0?1\\b.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    /** L'objet du marché : celui de l'appel d'offres, suivi du seul lot s'il y a lieu. */
    static String objetDuMarche(String objetAppel, String lot) {
        String o = objetAppel == null ? "" : objetAppel.trim();
        if (lot == null) {
            return o.isEmpty() ? A_COMPLETER : o;
        }
        Matcher m = ENUMERATION.matcher(o);
        String tete = m.find() ? o.substring(0, m.start()).trim() : o;
        return (tete.isEmpty() ? A_COMPLETER : tete) + " — " + lot;
    }

    /** « Lot n°01 : désignation » depuis les lots du plan (dans leur ordre), sinon « Lot n°01 ». */
    private String lotLibelle(Entree in) {
        String numero = String.format(Locale.ROOT, "Lot n°%02d", in.lot());
        Integer idDetail = dmcs.findById(in.idDmc()).map(d -> d.getIdDetail()).orElse(null);
        if (idDetail == null || in.lot() == null || in.lot() < 1) {
            return numero;
        }
        List<Lot> duPlan = lots.findByIdDetail(idDetail).stream().sorted(Comparator.comparing(Lot::getIdLot)).toList();
        if (in.lot() > duPlan.size() || duPlan.get(in.lot() - 1).getDesignationLot() == null
                || duPlan.get(in.lot() - 1).getDesignationLot().isBlank()) {
            return numero;
        }
        return numero + " : " + duPlan.get(in.lot() - 1).getDesignationLot().trim();
    }

    // ------------------------------------------------------------------ art. 60 : les mentions

    /** La PRMP « nommée par <acte> » depuis son mandat en vigueur ; sinon l'acte reste à compléter. */
    private String signataire(Entree in) {
        String nom = Objects.toString(in.plan().get("PRMP"), A_COMPLETER);
        String acte = in.idPrmp() == null ? null : mandats.mandatActif(null, in.idPrmp())
                .filter(m -> !m.isImplicite() && m.getRefArrete() != null && !m.getRefArrete().isBlank())
                .map(m -> m.getRefArrete().trim() + (m.getDateDebut() == null ? "" : " du " + DATE.format(m.getDateDebut()))).orElse(null);
        return nom + ", nommée par " + (acte == null ? A_COMPLETER + " (acte de nomination)" : acte);
    }

    /** Les articles de la loi en vertu desquels le marché est passé, selon le mode de la ligne du plan. */
    static String baseLegale(String mode, boolean pi) {
        String m = SousTypesDossier.normaliser(mode);
        String article;
        String libelle;
        if (pi) {
            article = "42";
            libelle = "consultation restreinte de prestations intellectuelles";
        } else if (m.contains("gre a gre")) {
            article = "39";
            libelle = "marché de gré à gré";
        } else if (m.contains("consultation")) {
            article = "41";
            libelle = "consultation";
        } else if (m.contains("deux etapes")) {
            article = "37";
            libelle = "appel d'offres en deux étapes";
        } else if (m.contains("pre-qualification") || m.contains("prequalification") || m.contains("pre qualification")) {
            article = "36";
            libelle = "appel d'offres ouvert avec pré-qualification";
        } else if (m.contains("restreint")) {
            article = "38";
            libelle = "appel d'offres restreint";
        } else if (m.contains("ouvert") || m.contains("appel d'offres")) {
            article = "35";
            libelle = "appel d'offres ouvert";
        } else {
            return "Le présent marché est passé en application des articles " + A_COMPLETER + " et 60 de " + LOI + ".";
        }
        return "Le présent marché est passé par " + libelle + (international(mode) ? " international" : "") + ", en application des articles "
                + article + " et 60 de " + LOI + ".";
    }

    static boolean international(String mode) {
        return SousTypesDossier.normaliser(mode).contains("international");
    }

    /** Le délai avec son unité : celle de l'acte d'engagement, à défaut les jours de la fiche, à défaut à compléter. */
    private static String delai(String delai, Valeurs val) {
        if (delai == null || delai.isBlank()) {
            String jours = val.get(DELAI_JOURS);
            return jours == null ? A_COMPLETER + " (durée et unité)" : jours + " jours";
        }
        String d = delai.trim().replaceAll("[.\\s]+$", "");
        if (d.matches(".*\\p{L}.*")) {
            return d;
        }
        return d + (val.get(DELAI_JOURS) != null ? " jours" : " " + A_COMPLETER + " (unité : jours, mois…)");
    }

    /** La sanction du dépassement : les pénalités de retard du CCAP / des données particulières, taux et plafond s'ils sont saisis. */
    private static String sanction(Valeurs val) {
        String taux = val.get(PENALITE_TAUX);
        String plafond = val.premiere(PENALITE_PLAFOND_TRAVAUX, PENALITE_PLAFOND);
        return "Tout dépassement de ce délai expose le Titulaire aux pénalités de retard prévues au cahier des clauses administratives "
                + "particulières (ou au cahier des prescriptions spéciales) et au cahier des clauses administratives générales"
                + (taux == null ? "" : ", au taux de " + tauxPenalite(taux) + " du montant du marché par jour de retard")
                + (plafond == null ? "" : ", dans la limite de " + pourcent(plafond) + " du montant du marché") + ".";
    }

    /**
     * ⚠️ 2026-10-10 (recette du front, fiche 40 : « 1/2000 ») — le champ {@code B09-PE-02} est libre : un nombre entier de millièmes
     * (« 2 » → « 2 millième(s) »), ou une fraction du montant (« 1/2000 ») reprise telle quelle.
     */
    static String tauxPenalite(String v) {
        String t = v.trim();
        return t.matches("\\d+") ? t + " millième(s)" : t;
    }

    private static String pourcent(String v) {
        String t = v.trim();
        return t.endsWith("%") ? t : t + " %";
    }

    private EntrepriseCandidatDto.Entreprise entreprise(Offre offre) {
        try {
            return entreprises.lire(offre.getIdCandidat());
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    /** Les valeurs de la fiche validée : le champ du lot ({@code CODE#lot}) d'abord, puis le champ commun ; vide = nul. */
    private record Valeurs(FicheMarcheService.EtatVersion v, Integer lot) {

        String get(String code) {
            if (v == null) {
                return null;
            }
            String parLot = lot == null ? null : vide(v.valeur(code + LotsFiche.SEPARATEUR + lot));
            return parLot != null ? parLot : vide(v.valeur(code));
        }

        String premiere(String... codes) {
            for (String c : codes) {
                String x = get(c);
                if (x != null) {
                    return x;
                }
            }
            return null;
        }

        private static String vide(String s) {
            return s == null || s.isBlank() ? null : s.trim();
        }
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private static void sous(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, t));
    }
}
