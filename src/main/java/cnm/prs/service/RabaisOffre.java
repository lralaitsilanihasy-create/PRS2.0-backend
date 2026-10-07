package cnm.prs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cnm.prs.dto.SeanceDto;

/**
 * ⚠️ 2026-10-07 (demande front « le rabais structuré au dépôt », arbitrage Q4 du pilote) — le rabais de l'acte d'engagement.
 * Manifeste format 4 : un objet {@code { nature: POURCENTAGE | MONTANT, valeur, condition: AUCUNE | LOTS, lots, libelle }} ; formats
 * 2 et 3 : un texte libre, lu tel quel. Le serveur ne lit le manifeste qu'à l'ouverture (ADR-0013) : ses contrôles sont des
 * <strong>alertes de séance</strong>, jamais un refus de dépôt.
 */
final class RabaisOffre {

    static final String POURCENTAGE = "POURCENTAGE";
    static final String MONTANT = "MONTANT";
    static final String AUCUNE = "AUCUNE";
    static final String LOTS = "LOTS";

    private RabaisOffre() {
    }

    /** Le rabais lu dans l'acte d'engagement, chiffré sur le HT lu (inconditionnel seulement) ; nul sans rabais. */
    static SeanceDto.RabaisLu lire(Map<String, Object> acteEngagement) {
        Object r = acteEngagement == null ? null : acteEngagement.get("rabais");
        if (r == null || r instanceof String s && s.isBlank()) {
            return null;
        }
        if (!(r instanceof Map<?, ?> m)) {
            String texte = String.valueOf(r).trim();
            return new SeanceDto.RabaisLu(null, null, null, null, texte, null, texte);
        }
        String nature = texte(m.get("nature"));
        BigDecimal valeur = SeanceService.montant(m.get("valeur"));
        String condition = texte(m.get("condition")) == null ? AUCUNE : texte(m.get("condition"));
        List<Integer> lots = new ArrayList<>();
        if (m.get("lots") instanceof List<?> l) {
            for (Object x : l) {
                BigDecimal n = SeanceService.montant(x);
                if (n != null) {
                    lots.add(n.intValue());
                }
            }
        }
        String libelle = texte(m.get("libelle"));
        BigDecimal ht = SeanceService.montant(acteEngagement.get("montantHt"));
        BigDecimal montant = AUCUNE.equals(condition) ? montant(nature, valeur, ht) : null;
        return new SeanceDto.RabaisLu(nature, valeur, condition, LOTS.equals(condition) ? lots : null, libelle, montant,
                phrase(nature, valeur, condition, lots, montant));
    }

    /** Le montant hors taxes du rabais sur une base : pourcentage × base (à l'ariary), ou le montant déclaré ; nul sans base. */
    static BigDecimal montant(String nature, BigDecimal valeur, BigDecimal base) {
        if (valeur == null) {
            return null;
        }
        if (MONTANT.equals(nature)) {
            return valeur;
        }
        if (POURCENTAGE.equals(nature) && base != null) {
            return base.multiply(valeur).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        }
        return null;
    }

    /** La phrase lue en séance : « 2 % du montant hors taxes, soit 250 000 Ariary », « 2 % si les lots 1 et 2 sont attribués ». */
    static String phrase(String nature, BigDecimal valeur, String condition, List<Integer> lots, BigDecimal montant) {
        if (valeur == null || nature == null) {
            return "Rabais déclaré, sans valeur lisible";
        }
        String quoi = POURCENTAGE.equals(nature) ? valeur.stripTrailingZeros().toPlainString().replace('.', ',') + " % du montant hors taxes"
                : FormulairesEnLigne.lisible(valeur) + " Ariary hors taxes";
        if (LOTS.equals(condition)) {
            return quoi + ", si les lots " + lots.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("?")
                    + " sont attribués au candidat";
        }
        return quoi + (POURCENTAGE.equals(nature) && montant != null ? ", soit " + FormulairesEnLigne.lisible(montant) + " Ariary" : "");
    }

    /**
     * Les contrôles du rabais structuré, en alertes de séance : {@code RABAIS_INVALIDE} (nature ou condition inconnue, valeur ≤ 0,
     * pourcentage ≥ 100, montant supérieur au HT de l'acte), {@code RABAIS_LOTS} (condition {@code LOTS} sans au moins deux lots, sans
     * le lot de l'offre, ou avec un lot inconnu de la fiche).
     */
    static List<SeanceDto.Alerte> controler(SeanceDto.RabaisLu r, BigDecimal ht, Integer lotOffre, int nbLots) {
        List<SeanceDto.Alerte> out = new ArrayList<>();
        if (r == null || r.lecture() != null && r.nature() == null && r.condition() == null) {
            return out;   // pas de rabais, ou un texte libre (formats 2 et 3)
        }
        List<String> fautes = new ArrayList<>();
        if (!POURCENTAGE.equals(r.nature()) && !MONTANT.equals(r.nature())) {
            fautes.add("nature inconnue (" + r.nature() + ")");
        }
        if (!AUCUNE.equals(r.condition()) && !LOTS.equals(r.condition())) {
            fautes.add("condition inconnue (" + r.condition() + ")");
        }
        if (r.valeur() == null || r.valeur().signum() <= 0) {
            fautes.add("valeur nulle ou négative");
        } else if (POURCENTAGE.equals(r.nature()) && r.valeur().compareTo(BigDecimal.valueOf(100)) >= 0) {
            fautes.add("pourcentage de 100 ou plus");
        } else if (MONTANT.equals(r.nature()) && ht != null && r.valeur().compareTo(ht) > 0) {
            fautes.add("montant supérieur au montant hors taxes de l'acte d'engagement");
        }
        if (!fautes.isEmpty()) {
            out.add(new SeanceDto.Alerte("RABAIS_INVALIDE", "Rabais déclaré invalide : " + String.join(", ", fautes) + "."));
        }
        if (LOTS.equals(r.condition())) {
            List<Integer> lots = r.lots() == null ? List.of() : r.lots();
            int lot = lotOffre == null ? 1 : lotOffre;
            List<String> f = new ArrayList<>();
            if (lots.stream().distinct().count() < 2) {
                f.add("moins de deux lots");
            }
            if (!lots.contains(lot)) {
                f.add("sans le lot de l'offre (" + lot + ")");
            }
            if (lots.stream().anyMatch(n -> n < 1 || n > Math.max(1, nbLots))) {
                f.add("un lot inconnu de la fiche");
            }
            if (!f.isEmpty()) {
                out.add(new SeanceDto.Alerte("RABAIS_LOTS", "Rabais conditionnel mal formé : " + String.join(", ", f) + "."));
            }
        }
        return out;
    }

    private static String texte(Object o) {
        return o == null || String.valueOf(o).isBlank() ? null : String.valueOf(o).trim();
    }
}
