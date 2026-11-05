package cnm.prs.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;

/**
 * ⚠️ 2026-11-05 (demande front « points-par-element », B1, B2 ; pilote) — les <strong>éléments du dossier</strong> qu'un point de
 * contrôle vérifie, dans l'ordre, le premier étant l'élément principal : les onglets de l'examen d'un dossier de marché. Stockés en texte
 * à virgules ({@code tr_points_ctrl.ELEMENTS}), servis en tableau. Le champ ne sert qu'au rangement de la grille à l'écran.
 */
public final class ElementsDossier {

    /**
     * Les codes admis : les onglets de l'examen d'un dossier de marché, puis (⚠️ B4, V168) ceux d'un dossier né d'une fiche
     * ({@code PLAN}, {@code AVIS}, {@code CANEVAS}).
     */
    public static final List<String> CODES = List.of("PRESENTATION", "PV_CNM", "DAO", "SEANCE", "EVALUATION", "ATTRIBUTION", "BORDEREAU",
            "PIECES", "PLAN", "AVIS", "CANEVAS");

    /**
     * B2, B3 — le rattachement des points de la grille du dossier de marché validé par le pilote le 05/11, par libellé, pour tous les
     * sous-types DDM. B3 : seuls les éléments NOUVEAUX du dossier de marché portent des points ({@code PV_CNM}, {@code DAO} et
     * {@code BORDEREAU}, examinés au dossier de mise en concurrence, n'en portent aucun). Posé par les semeurs à la création d'un point,
     * et par les migrations V166-V167 sur une base existante.
     */
    public static final Map<String, String> RATTACHEMENT_DDM = Map.ofEntries(
            Map.entry("CAO désignée, membres déclarés", "SEANCE,PIECES"),
            Map.entry("PV d'ouverture signé et publié", "SEANCE"),
            Map.entry("PV d'ouverture : date et heure", "SEANCE"),
            Map.entry("PV d'ouverture : quorum et signataires", "SEANCE"),
            Map.entry("Délai de remise des offres", "SEANCE"),
            Map.entry("Conformité des documents essentiels", "SEANCE,EVALUATION"),
            Map.entry("Rejets motivés par le DAO", "EVALUATION"),
            Map.entry("Montant évalué conforme au DAO", "EVALUATION"),
            Map.entry("Offres anormales : justification demandée", "EVALUATION"),
            Map.entry("Offres anormales : processus et seuils", "EVALUATION"),
            Map.entry("Classement et attributaire proposé", "EVALUATION,ATTRIBUTION"),
            Map.entry("Post-qualification sur les seuls critères du DAO", "EVALUATION"),
            Map.entry("Qualification : activités et critères", "EVALUATION"),
            Map.entry("Spécifications techniques", "EVALUATION"),
            Map.entry("Moralité des prix", "EVALUATION"),
            Map.entry("Rapport signé de tous les membres", "EVALUATION"),
            Map.entry("PV de validation cohérent", "EVALUATION"),
            Map.entry("Projet de marché conforme à l'offre retenue", "ATTRIBUTION"),
            Map.entry("Validité de l'offre retenue", "ATTRIBUTION"),
            Map.entry("Offre retenue acceptable", "ATTRIBUTION,PRESENTATION"),
            Map.entry("Publicité effective de l'avis", "PIECES"));

    /**
     * ⚠️ B4 (pilote, 05/11 ; V168) — le rattachement de la grille d'un dossier né d'une fiche DAOO (famille DMC, sous-types DAOO et
     * DAOO-CC, variantes contrat-cadre comprises), par libellé. La ligne du plan est une référence déjà examinée : aucun point sur
     * {@code PLAN}. Les points sans rattachement propre vont sur {@code DAO}.
     */
    public static final Map<String, String> RATTACHEMENT_DAOO = Map.ofEntries(
            Map.entry("Fiche de présentation cohérente", "PRESENTATION,DAO"),
            Map.entry("Avis spécifique conforme", "AVIS"),
            Map.entry("Délai de remise des offres", "AVIS,DAO"),
            Map.entry("Remise et ouverture des plis", "AVIS,DAO"),
            Map.entry("Visite des lieux", "AVIS,DAO"),
            Map.entry("AGPM contrôlé et publié", "PIECES"),
            Map.entry("Évaluation et attribution", "DAO,CANEVAS"),
            Map.entry("Prix unitaires ou forfaitaires", "DAO,BORDEREAU"),
            Map.entry("Spécifications et DQE", "DAO,BORDEREAU"),
            Map.entry("Conformité au plan de passation", "DAO"),
            Map.entry("Allotissement", "DAO"),
            Map.entry("Montants et estimation", "DAO"),
            Map.entry("Garantie de soumission", "DAO"),
            Map.entry("Délais de la consultation", "DAO"),
            Map.entry("Candidats : pièces et capacités", "DAO"),
            Map.entry("Paiements, avance et garanties financières", "DAO"),
            Map.entry("Pénalités, garantie et dérogations", "DAO"),
            Map.entry("Exécution et livraison", "DAO"),
            Map.entry("Spécifications techniques", "DAO"),
            Map.entry("Cohérence des mentions du dossier", "DAO"),
            Map.entry("Conformité aux documents types", "DAO"),
            Map.entry("Offres anormales et quantités", "DAO"),
            Map.entry("Formulaires de soumission", "DAO"),
            Map.entry("Qualifications particulières proportionnées", "DAO"),
            Map.entry("Quantités fixes ou à commande", "DAO"),
            Map.entry("Spécifications neutres", "DAO"),
            Map.entry("Garantie décennale", "DAO"),
            Map.entry("Personnel et matériel exigés", "DAO"),
            Map.entry("Recours au contrat-cadre justifié", "DAO"),
            Map.entry("Critères d'élimination et d'attribution", "DAO"),
            Map.entry("Critères de conformité de l'offre", "DAO"),
            Map.entry("Pourcentages du règlement", "DAO"),
            Map.entry("Délais de remise (contrat-cadre)", "DAO"));

    /**
     * Le rattachement de référence d'un point, PAR FAMILLE (⚠️ B4 : un même libellé, « Délai de remise des offres », n'a pas les mêmes
     * éléments au dossier de marché et au DAO) ; {@code null} hors des grilles validées par le pilote.
     */
    public static String rattachement(String famille, String sousType, String libelle) {
        if ("DDM".equals(famille)) {
            return RATTACHEMENT_DDM.get(libelle);
        }
        if ("DMC".equals(famille) && ("DAOO".equals(sousType) || "DAOO-CC".equals(sousType))) {
            return RATTACHEMENT_DAOO.get(libelle);
        }
        return null;
    }

    private ElementsDossier() {
    }

    /** Le texte stocké, en liste (vide sans élément). */
    public static List<String> lire(String stocke) {
        List<String> out = new ArrayList<>();
        if (stocke != null) {
            for (String s : stocke.split(",")) {
                if (!s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        return out;
    }

    /**
     * La liste reçue, contrôlée et mise en forme pour le stockage : codes connus (casse ignorée), sans doublon, dans l'ordre reçu ;
     * {@code null} si elle est vide. 400 nominatif {@code elements} sur un code inconnu.
     */
    public static String ecrire(List<String> recus) {
        if (recus == null) {
            return null;
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        List<String> inconnus = new ArrayList<>();
        for (String r : recus) {
            String c = r == null ? "" : r.trim().toUpperCase(Locale.ROOT);
            if (c.isEmpty()) {
                continue;
            }
            if (CODES.contains(c)) {
                codes.add(c);
            } else {
                inconnus.add(r);
            }
        }
        if (!inconnus.isEmpty()) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError("elements", "Élément(s) inconnu(s) : "
                    + String.join(", ", inconnus) + " (" + String.join(", ", CODES) + ").")));
        }
        return codes.isEmpty() ? null : String.join(",", codes);
    }
}
