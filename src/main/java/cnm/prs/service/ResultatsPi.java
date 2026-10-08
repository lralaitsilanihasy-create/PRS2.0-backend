package cnm.prs.service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.FinanciereDto;
import cnm.prs.dto.TechniqueDto;
import cnm.prs.entity.Negociation;
import cnm.prs.entity.Offre;
import cnm.prs.repository.NegociationRepository;
import cnm.prs.repository.OffreRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ <strong>Les résultats d'une consultation de prestations intellectuelles</strong>, lot 3, tranche PI-d2b (demande front du
 * 2026-10-07, §B7 ; arbitrages du pilote du 2026-10-08 : sous-type MPI ; une seule proposition conforme → infructuosité proposée
 * d'office) — ce que le rapport d'évaluation et l'attribution (lot 2) lisent pour une fiche PI, à la place des étapes 3 à 5 du lot 1 :
 * <ul>
 *   <li>l'<strong>état</strong> d'un lot : prêt pour le rapport ou non (et pourquoi), avec sa <strong>proposition</strong> — la
 *   proposition dont la négociation a abouti, ou l'infructuosité et son motif (art. 56-II : toutes écartées, une seule conforme, aucune
 *   au score technique minimum, aucune proposition financière recevable ; ou l'échec des négociations avec tous les classés) ;</li>
 *   <li>les <strong>sections du rapport</strong> : évaluation technique, évaluation financière, classement, négociation ; et l'annexe des
 *   grilles individuelles (arbitrage Q4) ;</li>
 *   <li>le <strong>motif du rejet</strong> d'une proposition, pour les lettres d'information (note technique et classement).</li>
 * </ul>
 * Lu par {@link EvaluationService} à travers un {@code ObjectProvider} (les services PI relisent l'évaluation par
 * {@link EvaluationService#vueSansPropositionPi}).
 */
@Service
@Transactional(readOnly = true)
public class ResultatsPi {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EvaluationTechniqueService technique;
    private final EvaluationFinanciereService financiere;
    private final NegociationService negociation;
    private final NegociationRepository negociations;
    private final OffreRepository offres;
    private final ObjectMapper mapper;

    public ResultatsPi(EvaluationTechniqueService technique, EvaluationFinanciereService financiere, NegociationService negociation,
            NegociationRepository negociations, OffreRepository offres, ObjectMapper mapper) {
        this.technique = technique;
        this.financiere = financiere;
        this.negociation = negociation;
        this.negociations = negociations;
        this.offres = offres;
        this.mapper = mapper;
    }

    /** L'état d'un lot : {@code pret} pour le rapport, sinon ce qui manque ({@code attente}) ; la proposition quand il est prêt. */
    public record Etat(boolean pret, String attente, EvaluationDto.Proposition proposition) {
    }

    public Etat etat(Long idDmc, Integer lot) {
        TechniqueDto.Lot t = technique.resultats(idDmc).lots().stream().filter(x -> Objects.equals(x.lot(), lot)).findFirst().orElse(null);
        if (t == null || !t.conformiteArretee()) {
            return new Etat(false, "l'examen préliminaire n'est pas arrêté", null);
        }
        if (t.offres().isEmpty()) {
            return infructueux("Toutes les propositions sont écartées à l'examen préliminaire (art. 56-II).");
        }
        if (t.offres().size() == 1) {
            return infructueux("Une seule proposition est conforme (art. 56-II).");
        }
        if (t.arret() == null || t.arret().le() == null) {
            return new Etat(false, "l'évaluation technique n'est pas arrêtée", null);
        }
        if (t.offres().stream().noneMatch(o -> EvaluationTechniqueService.QUALIFIEE.equals(o.statut()))) {
            return infructueux("Aucune proposition n'atteint la note technique minimale (art. 56-II).");
        }
        FinanciereDto.Lot f = EvaluationFinanciereService.lot(financiere.calculer(idDmc), lot);
        boolean arrete = f.arret() != null && f.arret().le() != null;
        if (!arrete) {
            boolean aucuneClassee = f.propositions().stream().noneMatch(p -> p.rang() != null);
            boolean aucuneAttendue = f.propositions().stream().noneMatch(p -> EvaluationFinanciereService.A_EVALUER.equals(p.statut()));
            if (aucuneClassee && aucuneAttendue && f.propositions().stream().anyMatch(p -> p.financiereOuverte())) {
                return infructueux("Aucune proposition financière n'est recevable : écartées, ou au-delà du budget disponible (art. 56-II).");
            }
            return new Etat(false, "le classement n'est pas arrêté", null);
        }
        List<Negociation> negos = negociations.findByIdDmcAndLotOrderByIdAsc(idDmc, lot);
        Negociation reussie = negos.stream().filter(n -> Negociation.REUSSIE.equals(n.getEtat())).findFirst().orElse(null);
        if (reussie != null) {
            FinanciereDto.Ligne p = f.propositions().stream().filter(x -> x.idOffre().equals(reussie.getIdOffre())).findFirst().orElse(null);
            if (p == null || p.saisie() == null) {
                return new Etat(false, "la proposition financière négociée n'est pas évaluée", null);
            }
            Offre fin = p.idFinanciere() == null ? null : offres.findById(p.idFinanciere()).orElse(null);
            return new Etat(true, null, new EvaluationDto.Proposition(p.idOffre(), p.numero(), p.raisonSociale(), p.saisie().prixCorrige(),
                    p.saisie().prixLuTtc(), EvaluationService.delaiLu(acteEngagement(fin)), false, p.idFinanciere(), null));
        }
        if (negos.stream().anyMatch(n -> Negociation.EN_COURS.equals(n.getEtat()))) {
            return new Etat(false, "une négociation est en cours", null);
        }
        if (negociation.prochain(idDmc, lot) == null) {
            // ⚠️ 2d-2 — après le retrait d'un marché attribué, l'infructuosité est exclue (art. 56-VI) : seule une déclaration sans suite.
            if (negos.stream().anyMatch(n -> Negociation.RETIREE.equals(n.getEtat()))) {
                return infructueux("Aucun candidat suivant n'est éligible après le retrait du marché ; l'infructuosité étant exclue après "
                        + "l'attribution (art. 56-VI), la procédure ne peut se clore que par une déclaration sans suite.");
            }
            return infructueux("Les négociations n'ont abouti avec aucun des candidats classés.");
        }
        return new Etat(false, negos.isEmpty() ? "la négociation n'est pas menée" : "la négociation avec le candidat suivant n'est pas menée", null);
    }

    /** La proposition du lot, ou nulle tant qu'il n'est pas prêt. */
    public EvaluationDto.Proposition proposition(Long idDmc, Integer lot) {
        return etat(idDmc, lot).proposition();
    }

    private static Etat infructueux(String motif) {
        return new Etat(true, null, new EvaluationDto.Proposition(null, null, null, null, null, null, true, null, motif));
    }

    // ------------------------------------------------------------------ le rapport

    /** Les sections 4 à 7 du rapport pour un lot PI : évaluation technique, évaluation financière, classement, négociation. */
    public List<DocumentLibre.Element> sections(Long idDmc, Integer lot, String suffixe) {
        List<DocumentLibre.Element> el = new ArrayList<>();
        TechniqueDto tech = technique.resultats(idDmc);
        TechniqueDto.Lot t = tech.lots().stream().filter(x -> Objects.equals(x.lot(), lot)).findFirst().orElse(null);
        sous(el, "4. Évaluation technique" + suffixe);
        if (t == null || t.arret() == null || t.arret().le() == null) {
            para(el, "L'évaluation technique n'a pas été conduite (moins de deux propositions conformes).");
            return el;
        }
        para(el, "Grille de la demande de propositions" + (tech.scoreMinimum() == null ? "" : ", score technique minimum : "
                + lisible(tech.scoreMinimum()) + " points") + ". Chaque membre a noté chaque proposition ; la note retenue est la moyenne des "
                + "membres (le détail des grilles figure en annexe).");
        for (TechniqueDto.Offre o : t.offres()) {
            List<String> parElement = new ArrayList<>();
            for (TechniqueDto.Moyenne m : o.moyennes()) {
                TechniqueDto.Element e = tech.elements().stream().filter(x -> x.code().equals(m.element())).findFirst().orElse(null);
                parElement.add((e == null ? m.element() : e.libelle()) + " " + lisible(m.moyenne()) + "/" + (e == null ? "—" : lisible(e.max()))
                        + (m.ecart() ? " (écart entre membres signalé)" : ""));
            }
            para(el, "Proposition n° " + o.numero() + " — " + o.raisonSociale() + " : note technique " + lisible(o.total()) + " points ("
                    + String.join(" ; ", parElement) + ") — " + (EvaluationTechniqueService.ELIMINEE.equals(o.statut())
                            ? "éliminée : " + o.motifElimination() : "qualifiée, rang technique " + o.rang()) + ".");
        }
        FinanciereDto f = financiere.calculer(idDmc);
        FinanciereDto.Lot fl = EvaluationFinanciereService.lot(f, lot);
        sous(el, "5. Évaluation financière (hors taxes)" + suffixe);
        para(el, "Méthode de sélection : " + Objects.toString(f.methode(), "—") + (f.poidsTechnique() == null ? "" : " ; poids technique "
                + lisible(f.poidsTechnique()) + ", poids financier " + lisible(f.poidsFinancier())) + (f.budget() == null ? "" : " ; budget disponible "
                + lisible(f.budget()) + " Ariary HT") + ".");
        for (FinanciereDto.Ligne p : fl.propositions()) {
            FinanciereDto.Saisie s = p.saisie();
            StringBuilder b = new StringBuilder("Proposition n° " + p.numero() + " — " + p.raisonSociale() + " : ");
            if (s == null) {
                b.append(Objects.toString(p.motif(), "non évaluée"));
            } else {
                b.append("prix lu ").append(lisible(s.prixLu()));
                List<EvaluationDto.Correction> retenues = s.corrections() == null ? List.of()
                        : s.corrections().stream().filter(c -> Boolean.TRUE.equals(c.retenue())).toList();
                for (EvaluationDto.Correction c : retenues) {
                    b.append(", correction « ").append(c.libelle()).append(" » de ").append(lisible(c.avant())).append(" à ").append(lisible(c.apres()))
                            .append(" (").append(c.regle()).append(")");
                }
                b.append(", prix corrigé ").append(lisible(s.prixCorrige())).append(", dont dépenses remboursables ").append(lisible(s.remboursables()));
                if (p.montantCompare() != null) {
                    b.append(", montant comparé ").append(lisible(p.montantCompare()));
                }
                if (p.scoreFinancier() != null) {
                    b.append(", score financier ").append(lisible(p.scoreFinancier()));
                }
                if (p.scoreCombine() != null) {
                    b.append(", score combiné ").append(lisible(p.scoreCombine()));
                }
                if (p.motif() != null) {
                    b.append(" — ").append(p.motif());
                }
            }
            para(el, b.append(".").toString());
        }
        sous(el, "6. Classement" + suffixe);
        List<FinanciereDto.Ligne> classees = fl.propositions().stream().filter(p -> p.rang() != null).sorted(Comparator.comparing(FinanciereDto.Ligne::rang))
                .toList();
        if (classees.isEmpty()) {
            para(el, "Aucune proposition n'est classée.");
        }
        for (FinanciereDto.Ligne p : classees) {
            para(el, "Rang " + p.rang() + " — proposition n° " + p.numero() + " (" + p.raisonSociale() + ")");
        }
        if (fl.departage() != null) {
            para(el, "Égalité départagée par la commission : " + fl.departage().motif() + ".");
        }
        sous(el, "7. Négociation" + suffixe);
        List<Negociation> negos = negociations.findByIdDmcAndLotOrderByIdAsc(idDmc, lot);
        if (negos.isEmpty()) {
            para(el, "Aucune négociation n'a été menée.");
        }
        for (Negociation n : negos) {
            para(el, "Avec le candidat classé " + n.getRang() + " (proposition n° " + n.getNumero() + ", " + n.getRaisonSociale() + ")"
                    + (n.getDateNegociation() == null ? "" : ", le " + n.getDateNegociation().format(DATE)) + (n.getLieu() == null ? "" : ", " + n.getLieu())
                    + " : " + (Negociation.REUSSIE.equals(n.getEtat()) ? "a abouti" : Negociation.RETIREE.equals(n.getEtat())
                            ? "a abouti, puis le marché a été retiré — " + n.getMotifEchec() : Negociation.ECHOUEE.equals(n.getEtat())
                            ? "n'a pas abouti — " + n.getMotifEchec() : "en cours") + ". Le procès-verbal de négociation est joint.");
        }
        return el;
    }

    /** L'annexe des grilles individuelles de notation technique (arbitrage Q4 : jointes au rapport). */
    public List<DocumentLibre.Element> annexeGrilles(Long idDmc) {
        List<DocumentLibre.Element> el = new ArrayList<>();
        TechniqueDto tech = technique.resultats(idDmc);
        sous(el, "Annexe — Grilles individuelles de notation technique");
        boolean aucune = true;
        for (TechniqueDto.Lot l : tech.lots()) {
            for (TechniqueDto.Offre o : l.offres()) {
                for (TechniqueDto.Grille g : o.grilles()) {
                    aucune = false;
                    List<String> notes = g.notes().stream().map(n -> {
                        TechniqueDto.Element e = tech.elements().stream().filter(x -> x.code().equals(n.element())).findFirst().orElse(null);
                        return (e == null ? n.element() : e.libelle()) + " " + lisible(n.note()) + " (" + n.motif() + ")";
                    }).toList();
                    para(el, (tech.lots().size() > 1 ? "Lot " + l.lot() + ", p" : "P") + "roposition n° " + o.numero() + " — " + g.nom() + " : "
                            + String.join(" ; ", notes) + ".");
                }
            }
        }
        if (aucune) {
            para(el, "Aucune grille n'a été saisie.");
        }
        return el;
    }

    // ------------------------------------------------------------------ les lettres

    /**
     * Le motif du rejet d'une proposition retenue à l'examen préliminaire (les autres gardent le motif du lot 1) : éliminée à
     * l'évaluation technique, écartée à l'évaluation financière, ou classée — sa note technique et son rang.
     */
    public String motifRejet(Long idDmc, Integer lot, String idOffre) {
        TechniqueDto.Lot t = technique.resultats(idDmc).lots().stream().filter(x -> Objects.equals(x.lot(), lot)).findFirst().orElse(null);
        TechniqueDto.Offre o = t == null ? null : t.offres().stream().filter(x -> x.idOffre().equals(idOffre)).findFirst().orElse(null);
        if (o == null || t.arret() == null || t.arret().le() == null) {
            return "Proposition non retenue : la consultation ne s'est pas poursuivie au-delà de l'examen préliminaire.";
        }
        if (EvaluationTechniqueService.ELIMINEE.equals(o.statut())) {
            return "Proposition éliminée à l'évaluation technique : " + o.motifElimination() + ".";
        }
        FinanciereDto.Ligne p = EvaluationFinanciereService.lot(financiere.calculer(idDmc), lot).propositions().stream()
                .filter(x -> x.idOffre().equals(idOffre)).findFirst().orElse(null);
        if (p != null && (EvaluationFinanciereService.ECARTEE.equals(p.statut()) || EvaluationFinanciereService.HORS_BUDGET.equals(p.statut()))) {
            return "Proposition écartée à l'évaluation financière : " + p.motif();
        }
        String echec = negociations.findByIdDmcAndLotOrderByIdAsc(idDmc, lot).stream()
                .filter(n -> n.getIdOffre().equals(idOffre) && (Negociation.ECHOUEE.equals(n.getEtat()) || Negociation.RETIREE.equals(n.getEtat())))
                .map(Negociation::getMotifEchec).findFirst()
                .orElse(null);
        return "Proposition qualifiée, note technique de " + lisible(o.total()) + " points" + (p == null || p.scoreCombine() == null ? ""
                : ", score combiné de " + lisible(p.scoreCombine())) + (p == null || p.rang() == null ? "" : ", classée au rang " + p.rang())
                + (echec == null ? "" : " ; la négociation n'a pas abouti : " + echec) + ". Le marché est attribué au candidat avec qui la "
                + "négociation a abouti, dans l'ordre du classement.";
    }

    // ------------------------------------------------------------------ outils

    @SuppressWarnings("unchecked")
    private Map<String, Object> acteEngagement(Offre o) {
        if (o == null || o.getLecture() == null) {
            return null;
        }
        Map<String, Object> l = mapper.readValue(o.getLecture(), new TypeReference<Map<String, Object>>() {
        });
        return (Map<String, Object>) l.get("acteEngagement");
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private static void sous(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, t));
    }

    private static String lisible(BigDecimal b) {
        return b == null ? "—" : b.stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ ⚠️ lot 2, tranche 2d-2 : la réattribution (Q8)

    /** Le classé suivant après le retrait du marché (la négociation réussie mise à part) ; nul s'il n'en reste aucun. */
    public FinanciereDto.Ligne suivantApresRetrait(Long idDmc, Integer lot) {
        return negociation.suivantApresRetrait(idDmc, lot);
    }

    /** La négociation réussie de la proposition retirée passe {@code RETIREE}. */
    @Transactional
    public void retirerNegociation(Long idDmc, Integer lot, String idOffre, String motif) {
        negociation.retirer(idDmc, lot, idOffre, motif);
    }
}
