package cnm.prs.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import cnm.prs.dto.BilanControlesDto;
import cnm.prs.dto.DelaiLegalDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.DelaiParametre;
import cnm.prs.enums.CategorieDao;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.repository.DelaiParametreRepository;

/**
 * ⚠️ R6a (manuel révisé pour l'application, lot 2, §B1, règles 1-2 et 4 ; arbitrage Q6 du pilote ; 2026-10-13) — le <strong>délai de
 * publicité</strong> : entre la date de publication de l'avis et la date limite de remise des offres, au moins 30 jours (appel
 * d'offres ouvert national, art. 35-II), 45 (international) ou 10 (consultation ouverte, art. 41-II) — <strong>bloquant</strong> à la
 * validation de la fiche (contre la date de publication prévue : {@code B04-SE-17}, à défaut la prévision LANCEMENT du plan) et à
 * l'impression de l'avis (contre la date de publication saisie). Les autres modes (restreint, prestations intellectuelles, AMI,
 * pré-qualification, contrat-cadre) et le délai aménagé justifié au plan lisent un paramètre administrable : <strong>signalé</strong>,
 * jamais bloquant. Convention : le jour de départ n'est pas compté ({@code limite − départ ≥ requis}).
 */
@Service
public class DelaisPublicite {

    public static final String DELAI_PUBLICITE = "DELAI_PUBLICITE";
    /** Les champs de la date limite de remise, par catégorie et forme (fournitures, travaux, contrat-cadre, prestations intellectuelles). */
    static final List<String> CHAMPS_LIMITE = List.of("B04-LR-03", "B04-OV-02", "B04-CP-02", "B04-LH-02");
    /** La date de publication prévue de l'avis (remise électronique). */
    static final String PUBLICATION_PREVUE = "B04-SE-17";
    static final String PPM_LANCEMENT = "LANCEMENT";
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DelaiParametreRepository parametres;

    public DelaisPublicite(DelaiParametreRepository parametres) {
        this.parametres = parametres;
    }

    /**
     * Ce qu'exige le mode : la valeur requise, son origine (loi ou paramètre), son caractère bloquant.
     *
     * @param legal   un des neuf délais de la loi (bloquant) ; faux : un paramètre administrable (signalé)
     */
    public record Exigence(String code, int requis, String unite, boolean legal, boolean bloquant, String libelle, String base) {
    }

    /** Le constat sur deux dates : jours écoulés entre le départ (non compté) et la limite, conforme si ≥ requis. */
    public record Constat(Exigence exigence, LocalDate depart, LocalDate limite, long constate, boolean conforme) {
    }

    /**
     * L'exigence qui s'applique à une procédure, ou {@code null} (gré à gré, achat direct, mode inconnu).
     *
     * @param modeLibelle            le libellé du mode du plan
     * @param delaiAmenageJustifie   la ligne du plan porte une justification de délai aménagé
     * @param categorie              la catégorie de la fiche ({@code PRESTATIONS_INTELLECTUELLES}…)
     * @param typeMarche             la forme ({@code CONTRAT_CADRE}…)
     */
    public Exigence exigence(String modeLibelle, boolean delaiAmenageJustifie, String categorie, String typeMarche) {
        String m = SousTypesDossier.normaliser(modeLibelle);
        if (m.contains("gre a gre") || m.contains("achat direct")) {
            return null;
        }
        if (CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie)) {
            return parametre("PUBLICITE_DC_PI");
        }
        if ("CONTRAT_CADRE".equals(typeMarche)) {
            return parametre("PUBLICITE_CONTRAT_CADRE");
        }
        if (m.contains("manifestation")) {
            return parametre("PUBLICITE_AMI");
        }
        if (m.contains("restreint")) {
            return parametre("PUBLICITE_AOR");
        }
        if (m.contains("qualification")) {
            return parametre("PUBLICITE_PREQUALIFICATION");
        }
        if (delaiAmenageJustifie) {
            return parametre("DELAI_AMENAGE");
        }
        if (m.contains("international")) {
            return legal(DelaisLegaux.PUBLICITE_AOO_INTERNATIONAL);
        }
        if (m.contains("consultation")) {
            return legal(DelaisLegaux.PUBLICITE_CONSULTATION);
        }
        if (m.contains("ouvert") || m.contains("appel d'offre")) {
            return legal(DelaisLegaux.PUBLICITE_AOO_NATIONAL);
        }
        return null;
    }

    private static Exigence legal(String code) {
        DelaiLegalDto d = DelaisLegaux.par(code).orElseThrow();
        return new Exigence(code, d.valeur(), d.unite(), true, true, d.texte(), d.article());
    }

    private Exigence parametre(String code) {
        return parametres.findById(code).filter(DelaiParametre::isActif)
                .map(p -> new Exigence(code, p.getValeur(), p.getUnite(), false, false, p.getTexte() == null ? code : p.getTexte(), "paramètre"))
                .orElse(null);
    }

    /** Le constat ; {@code null} sans l'une des deux dates (non évaluable). */
    public static Constat constater(Exigence e, LocalDate depart, LocalDate limite) {
        if (e == null || depart == null || limite == null) {
            return null;
        }
        long constate = ChronoUnit.DAYS.between(depart, limite);
        return new Constat(e, depart, limite, constate, constate >= e.requis());
    }

    /** La date limite de remise lue sur la fiche (le premier champ renseigné parmi {@link #CHAMPS_LIMITE}). */
    public static LocalDate dateLimite(Map<String, String> valeurs) {
        String champ = champLimite(valeurs);
        return champ == null ? null : ControlesFicheMarche.date(valeurs.get(champ));
    }

    static String champLimite(Map<String, String> valeurs) {
        if (valeurs == null) {
            return null;
        }
        for (String c : CHAMPS_LIMITE) {
            if (ControlesFicheMarche.date(valeurs.get(c)) != null) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ à la validation de la fiche

    /**
     * Au bilan de la fiche : la date de publication prévue ({@code B04-SE-17}, à défaut la prévision LANCEMENT du plan) et la date limite
     * de remise. Non évaluable (une date manque) : rien n'est ajouté, l'impression de l'avis tranchera.
     */
    public void auBilan(Map<String, String> valeurs, ValeursPpmService.ValeursPpm ppm, String categorie, String typeMarche,
            BilanControlesDto bilan) {
        String champ = champLimite(valeurs);
        LocalDate limite = champ == null ? null : ControlesFicheMarche.date(valeurs.get(champ));
        LocalDate prevue = ControlesFicheMarche.date(valeurs.get(PUBLICATION_PREVUE));
        LocalDate depart = prevue != null ? prevue : ppm == null || ppm.dates() == null ? null : ppm.dates().get(PPM_LANCEMENT);
        Exigence e = exigence(ppm == null || ppm.valeurs() == null ? null : ppm.valeurs().get("MODE"), delaiAmenageJustifie(ppm), categorie,
                typeMarche);
        Constat c = constater(e, depart, limite);
        if (c == null) {
            return;
        }
        List<String> champs = new ArrayList<>();
        champs.add(champ);
        if (prevue != null) {
            champs.add(PUBLICATION_PREVUE);
        }
        String origine = prevue != null ? "la date de publication prévue" : "la prévision de lancement du plan";
        BilanControlesDto.Controle controle = new BilanControlesDto.Controle(DELAI_PUBLICITE, champs, "B04", message(c, origine));
        if (c.conforme()) {
            bilan.ok().add(controle);
        } else if (e.bloquant()) {
            bilan.bloquants().add(controle);
        } else {
            bilan.avertissements().add(controle);
        }
    }

    // ------------------------------------------------------------------ à l'impression de l'avis

    /**
     * À l'impression de l'avis : contre la date de publication saisie. 409 {@code DELAI_PUBLICITE} ({@code details} : {@code code},
     * {@code requis}, {@code constate}, {@code depart}, {@code limite}) quand un délai de la loi n'est pas respecté ; un paramètre
     * administrable ne bloque pas.
     */
    public void exigerAImpression(FicheMarcheDto etat, ValeursPpmService.ValeursPpm ppm, LocalDate datePublication) {
        LocalDate limite = dateLimite(etat.getValeurs());
        Exigence e = exigence(ppm == null || ppm.valeurs() == null ? null : ppm.valeurs().get("MODE"), delaiAmenageJustifie(ppm),
                etat.getCategorie(), etat.getTypeMarche());
        Constat c = constater(e, datePublication, limite);
        if (c == null || c.conforme() || !e.bloquant()) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", e.code());
        details.put("requis", e.requis());
        details.put("constate", c.constate());
        details.put("depart", datePublication.toString());
        details.put("limite", limite.toString());
        throw new BusinessRuleException(message(c, "la date de publication"), DELAI_PUBLICITE, etat.getIdDossierSoumis(), details);
    }

    static boolean delaiAmenageJustifie(ValeursPpmService.ValeursPpm ppm) {
        return ppm != null && ppm.ligne() != null && ppm.ligne().getJustifDelaiAmenage() != null && !ppm.ligne().getJustifDelaiAmenage().isBlank();
    }

    static String message(Constat c, String origine) {
        Exigence e = c.exigence();
        return "Délai de publicité — " + e.libelle() + " : " + c.constate() + " jour(s) entre " + origine + " (" + c.depart().format(JOUR)
                + ") et la date limite de remise (" + c.limite().format(JOUR) + ") ; " + e.requis() + " jour(s) requis (" + e.base() + ")"
                + (c.conforme() ? "." : e.bloquant() ? " : date limite trop proche." : " : à vérifier (délai à confirmer par le pilote).");
    }
}
