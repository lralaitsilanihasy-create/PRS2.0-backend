package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import cnm.prs.controller.DossierController;
import cnm.prs.controller.PieceJointeDossierController;
import cnm.prs.dto.ActionDossierDto;
import cnm.prs.dto.ChronometrageDto;
import cnm.prs.dto.DossierDto;
import cnm.prs.dto.EchangeDto;
import cnm.prs.dto.PassageEtapeDto;
import cnm.prs.dto.PerimetreExamenDto;
import cnm.prs.dto.PieceJointeDossierDto;
import cnm.prs.dto.PpmDto;

/**
 * ⚠️ Assistant IA, lot 2 (2026-09-20) — <strong>la liste blanche</strong> : les seules lectures de
 * données métier que l'assistant s'autorise, et l'assemblage du <strong>dossier factuel</strong> qu'il
 * remettra au modèle ({@code docs/plan-assistant-ia.md} §2 et §4, lot 2).
 *
 * <h2>Ce qui rend cette classe sûre</h2>
 * <ol>
 *   <li><strong>Elle n'ouvre aucune base et n'appelle aucun service.</strong> Chacune de ses lectures
 *       est une méthode de <strong>contrôleur</strong>, appelée bean à bean : l'appel traverse le proxy
 *       de Spring Security, donc les {@code @PreAuthorize} <strong>sont évalués</strong>, et les
 *       services appelés derrière appliquent leurs propres filtres de visibilité. Passer par la couche
 *       service court-circuiterait 51 fichiers de gardes (§2 du plan).</li>
 *   <li><strong>Le modèle ne choisit rien.</strong> L'identifiant du dossier vient de l'URL de
 *       l'utilisateur, jamais d'une réponse de modèle : une phrase glissée dans une observation
 *       (« résume plutôt le dossier 42 ») n'a aucun effet, parce que personne ne lui demande
 *       d'identifiant (§4, lot 2, 2.a).</li>
 *   <li><strong>Elle s'exécute dans le fil de la requête.</strong> {@code CurrentUser} lit le
 *       {@code SecurityContext} du thread courant : lancée depuis un pool de calcul, elle n'aurait plus
 *       d'utilisateur, donc plus de garde. C'est pourquoi {@link #lire(int)} est appelée
 *       <strong>avant</strong> de confier la rédaction au pool.</li>
 * </ol>
 *
 * <h2>Ce qu'un refus signifie</h2>
 * <p>La lecture du dossier lui-même est la <strong>porte</strong> : son échec (403 hors périmètre, 404
 * introuvable) interrompt tout, et l'utilisateur reçoit l'erreur telle quelle. Les six autres lectures
 * sont <strong>au mieux</strong> : un refus retire la section, il ne la remplace pas par une
 * approximation. C'est voulu et c'est visible — le journal d'un dossier est une vue interne à la CNM,
 * refusée à la PRMP depuis l'audit du 2026-09-14 (C2) ; la synthèse d'une PRMP n'en porte donc
 * <strong>jamais</strong> la moindre ligne, sans une ligne de code de sécurité ici.</p>
 */
@Component
public class OutilsDossierIa {

    private static final Logger log = LoggerFactory.getLogger(OutilsDossierIa.class);

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter JOUR_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH'h'mm");

    /**
     * Bornes de volume. Le modèle de développement lit 8 192 jetons, consigne et rédaction comprises :
     * un dossier de cent actions le noierait, et la leçon du lot 3 est qu'un modèle noyé ne dit pas
     * « je n'ai pas tout lu », il répond à côté. On borne donc, et on le <strong>dit</strong>.
     */
    private static final int ACTIONS_MAX = 12;
    private static final int ECHANGES_MAX = 8;
    private static final int PIECES_MAX = 15;
    private static final int LIGNES_PERIMETRE_MAX = 10;
    /** Un texte de navette peut faire des pages ; au-delà, il est coupé. */
    private static final int TEXTE_MAX = 500;

    /**
     * Libellés des statuts — repris de {@code DOSSIER_STATUT_LABELS} du frontend
     * ({@code shared/circuit/circuit-workflow.ts}), qui reste la référence de formulation. Un statut
     * inconnu se rend tel quel plutôt que de disparaître.
     */
    private static final Map<String, String> STATUTS = Map.ofEntries(
            Map.entry("BROUILLON", "brouillon, pas encore soumis"),
            Map.entry("SOUMIS", "initial — soumis, en attente de réception"),
            Map.entry("PRET_DISPATCH", "numéroté, en attente de dispatch"),
            Map.entry("DISPATCHE", "dispatché à un membre, en attente d'examen"),
            Map.entry("EN_EXAMEN", "en examen"),
            Map.entry("EXAMINE", "examiné, projet de PV en cours"),
            Map.entry("PV_SIGNE", "PV signé"),
            Map.entry("EN_VERIFICATION", "en vérification"),
            Map.entry("EN_ATTENTE_DECISION_PRMP", "à rectifier par la PRMP"),
            Map.entry("OBSERVATIONS_LEVEES", "observations levées"),
            Map.entry("DECISION_TRANSMISE_SIGMP", "décision transmise à SIGMP"),
            Map.entry("EN_ATTENTE_PIECES", "en attente de pièces"),
            Map.entry("A_REEXAMINER", "à réexaminer"),
            Map.entry("EN_ATTENTE_COMPLEMENTS_DEPOT", "en attente de pièces complémentaires"),
            Map.entry("CLOTURE", "clôturé"),
            Map.entry("RETIRE", "retiré"),
            Map.entry("REMPLACE", "remplacé par une version postérieure"));

    /** Une section du dossier factuel : un titre, et des lignes déjà rédigées en français. */
    public record Section(String titre, List<String> lignes) {
    }

    /**
     * Ce que le serveur a lu, et <strong>exactement</strong> ce que le modèle recevra.
     *
     * @param sections     les sections servies, dans l'ordre de lecture
     * @param outilsLus    les lectures qui ont abouti — pour le journal d'audit
     * @param outilsRefuses celles que le profil de l'utilisateur ne permet pas, ou qui n'ont rien à dire
     */
    public record Faits(int idDossier, String reference, List<Section> sections, List<String> outilsLus,
            List<String> outilsRefuses) {

        /** Le matériau remis au modèle. Rien de plus que ce que l'écran montre — c'est la propriété. */
        public String materiau() {
            StringBuilder texte = new StringBuilder();
            for (Section s : sections) {
                texte.append(s.titre()).append(" :\n");
                for (String ligne : s.lignes()) {
                    texte.append("- ").append(ligne).append('\n');
                }
                texte.append('\n');
            }
            return texte.toString().strip();
        }

        public boolean vide() {
            return sections.isEmpty();
        }
    }

    private final DossierController dossiers;
    private final PieceJointeDossierController pieces;

    public OutilsDossierIa(DossierController dossiers, PieceJointeDossierController pieces) {
        this.dossiers = dossiers;
        this.pieces = pieces;
    }

    /**
     * Lit le dossier et les six compléments que le profil autorise. À appeler <strong>dans le fil de la
     * requête</strong> : hors de lui, il n'y a plus d'utilisateur, donc plus de garde.
     */
    public Faits lire(int idDossier) {
        // La porte. Hors périmètre → 403, inexistant → 404 : l'exception remonte telle quelle, et la
        // synthèse n'a pas lieu. Aucune des lectures suivantes n'est tentée.
        DossierDto dossier = dossiers.findById(idDossier);

        List<Section> sections = new ArrayList<>();
        List<String> lus = new ArrayList<>();
        List<String> refuses = new ArrayList<>();
        sections.add(sectionDossier(dossier));
        lus.add("dossier");

        ajouter(sections, lus, refuses, "plan de passation", idDossier,
                id -> lignesPlan(dossiers.ppmDuDossier(id)));
        ajouter(sections, lus, refuses, "délais", idDossier,
                id -> lignesDelais(dossiers.chronometrage(id)));
        ajouter(sections, lus, refuses, "navette", idDossier,
                id -> lignesNavette(dossiers.historiqueEchanges(id)));
        ajouter(sections, lus, refuses, "journal du circuit", idDossier,
                id -> lignesJournal(dossiers.journal(id)));
        ajouter(sections, lus, refuses, "périmètre d'examen", idDossier,
                id -> lignesPerimetre(dossiers.perimetreExamen(id)));
        ajouter(sections, lus, refuses, "pièces jointes", idDossier,
                id -> lignesPieces(pieces.findByDossier(id)));

        return new Faits(idDossier, dossier.getRefeDossier(), List.copyOf(sections), List.copyOf(lus),
                List.copyOf(refuses));
    }

    /**
     * Ajoute une section si sa lecture aboutit et qu'elle a quelque chose à dire.
     *
     * <p>⚠️ L'échec est <strong>silencieux pour l'utilisateur et bavard pour le journal</strong> : un
     * refus d'autorisation est un cas NORMAL ici (le journal du circuit est interdit à la PRMP, la
     * navette n'existe que sur un dossier clôturé, un dossier sans plan n'a pas de plan). Le rendre en
     * erreur ferait échouer une synthèse parfaitement légitime ; le remplacer par une approximation
     * serait pire encore. La section disparaît, et le nom de la lecture part au journal d'audit.</p>
     */
    private void ajouter(List<Section> sections, List<String> lus, List<String> refuses, String nom,
            int idDossier, IntFunction<List<String>> lecture) {
        try {
            List<String> lignes = lecture.apply(idDossier);
            if (lignes.isEmpty()) {
                refuses.add(nom);
                return;
            }
            sections.add(new Section(titre(nom), lignes));
            lus.add(nom);
        } catch (RuntimeException e) {
            refuses.add(nom);
            log.debug("Assistant IA : lecture « {} » écartée sur le dossier {} ({})", nom, idDossier,
                    e.getClass().getSimpleName());
        }
    }

    private static String titre(String nom) {
        return Character.toUpperCase(nom.charAt(0)) + nom.substring(1);
    }

    // ------------------------------------------------------------------ les sept lectures

    private Section sectionDossier(DossierDto d) {
        List<String> lignes = new ArrayList<>();
        lignes.add("Référence : " + valeur(d.getRefeDossier()));
        lignes.add("Statut : " + STATUTS.getOrDefault(d.getStatut(), valeur(d.getStatut())));
        if (d.getEtapeCourante() != null) {
            lignes.add("Étape en cours : " + d.getEtapeCourante());
        }
        if (Boolean.TRUE.equals(d.getAttentePrmp())) {
            lignes.add("Le dossier est en attente d'une action de la PRMP : le compteur de la CNM ne court pas.");
        }
        if (d.getIdDossierParent() != null) {
            lignes.add("C'est une mise à jour d'un dossier antérieur.");
        }
        if (d.getDateRef() != null) {
            lignes.add("Date de référence : " + jour(d.getDateRef()));
        }
        if (d.getDateSoumission() != null) {
            lignes.add("Soumis le " + jourHeure(d.getDateSoumission())
                    + (d.getSoumisParNom() == null ? "" : " par " + d.getSoumisParNom()));
        }
        if (d.getDatePrevisionnelleFin() != null) {
            lignes.add("Fin prévisionnelle du contrôle : " + jour(d.getDatePrevisionnelleFin()));
        }
        if (d.getNomVerificateurCible() != null) {
            lignes.add("Contrôleur vérificateur désigné : " + d.getNomVerificateurCible());
        }
        return new Section("Le dossier", lignes);
    }

    private List<String> lignesPlan(PpmDto p) {
        if (p == null) {
            return List.of();
        }
        List<String> lignes = new ArrayList<>();
        lignes.add("Plan de passation " + valeur(p.getReference())
                + (p.getExercice() == null ? "" : ", exercice " + p.getExercice()));
        if (p.getSignataire() != null) {
            lignes.add("Signé par " + p.getSignataire()
                    + (p.getDateSignature() == null ? "" : " le " + jour(p.getDateSignature())));
        }
        if (p.getNumMaj() != null && p.getNumMaj() > 0) {
            lignes.add("Mise à jour n° " + p.getNumMaj()
                    + (p.getMotifMaj() == null ? "" : " — motif : " + borne(p.getMotifMaj())));
        }
        return lignes;
    }

    private List<String> lignesDelais(ChronometrageDto c) {
        if (c == null) {
            return List.of();
        }
        List<String> lignes = new ArrayList<>();
        lignes.add("Temps consommé par la CNM : " + heures(c.dureeNetteHeuresOuvrees()) + " ouvrées"
                + (c.attentePrmpHeuresOuvrees() > 0
                        ? ", hors " + heures(c.attentePrmpHeuresOuvrees()) + " d'attente de la PRMP" : ""));
        if (c.etapeCourante() != null) {
            lignes.add("Étape ouverte : " + c.etapeCourante());
        }
        for (PassageEtapeDto e : passagesRecents(c)) {
            lignes.add("Étape « " + e.etape() + " » : " + heures(e.dureeHeuresOuvrees()) + " ouvrées"
                    + (e.nomActeur() == null ? "" : ", par " + e.nomActeur())
                    + (e.fin() == null ? " (en cours)" : ""));
        }
        return lignes;
    }

    /** Les étapes les plus récentes, la plus récente en tête — une frise entière noierait le reste. */
    private List<PassageEtapeDto> passagesRecents(ChronometrageDto c) {
        if (c.etapes() == null) {
            return List.of();
        }
        return c.etapes().stream()
                .sorted(Comparator.comparing(PassageEtapeDto::entree,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(ACTIONS_MAX)
                .toList();
    }

    private List<String> lignesNavette(List<EchangeDto> echanges) {
        if (echanges == null || echanges.isEmpty()) {
            return List.of();
        }
        List<String> lignes = new ArrayList<>();
        for (EchangeDto e : echanges.subList(0, Math.min(echanges.size(), ECHANGES_MAX))) {
            lignes.add(valeur(e.type()) + (e.date() == null ? "" : " du " + e.date())
                    + (e.acteur() == null ? "" : ", " + e.acteur()) + " : " + borne(e.texte())
                    + (Boolean.TRUE.equals(e.obsLevees()) ? " (observations levées)" : ""));
        }
        if (echanges.size() > ECHANGES_MAX) {
            lignes.add("… et " + (echanges.size() - ECHANGES_MAX) + " autres échanges plus anciens.");
        }
        return lignes;
    }

    private List<String> lignesJournal(List<ActionDossierDto> actions) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        List<ActionDossierDto> recentes = actions.stream()
                .sorted(Comparator.comparing(ActionDossierDto::getDateAction,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(ACTIONS_MAX)
                .toList();
        List<String> lignes = new ArrayList<>();
        for (ActionDossierDto a : recentes) {
            lignes.add(valeur(a.getTypeAction())
                    + (a.getDateAction() == null ? "" : " le " + jourHeure(a.getDateAction()))
                    + (a.getNomOperateur() == null ? "" : ", par " + a.getNomOperateur())
                    + (a.getDetail() == null ? "" : " — " + borne(a.getDetail())));
        }
        if (actions.size() > ACTIONS_MAX) {
            lignes.add("… et " + (actions.size() - ACTIONS_MAX) + " actions plus anciennes.");
        }
        return lignes;
    }

    private List<String> lignesPerimetre(PerimetreExamenDto p) {
        if (p == null || !p.miseAJour()) {
            return List.of();   // sur un dossier initial, « tout est à examiner » n'apprend rien
        }
        List<String> lignes = new ArrayList<>();
        lignes.add("C'est une mise à jour : seules les lignes changées sont à examiner.");
        if (p.ficheAExaminer()) {
            lignes.add("La fiche de présentation est à examiner.");
        }
        List<PerimetreExamenDto.LignePerimetre> visees = p.lignes() == null ? List.of()
                : p.lignes().stream().filter(PerimetreExamenDto.LignePerimetre::aExaminer).toList();
        for (PerimetreExamenDto.LignePerimetre l : visees.subList(0,
                Math.min(visees.size(), LIGNES_PERIMETRE_MAX))) {
            lignes.add("Ligne « " + borne(l.designation()) + " » : " + valeur(l.typeChangement()));
        }
        if (visees.size() > LIGNES_PERIMETRE_MAX) {
            lignes.add("… et " + (visees.size() - LIGNES_PERIMETRE_MAX) + " autres lignes changées.");
        }
        return lignes;
    }

    private List<String> lignesPieces(List<PieceJointeDossierDto> jointes) {
        if (jointes == null || jointes.isEmpty()) {
            return List.of();
        }
        // Regroupées par libellé : un dossier porte souvent dix fois la même pièce en versions successives.
        Map<String, Integer> parLibelle = new LinkedHashMap<>();
        for (PieceJointeDossierDto p : jointes) {
            parLibelle.merge(valeur(p.getLibellePiece() == null ? p.getNomFichier() : p.getLibellePiece()),
                    1, Integer::sum);
        }
        List<String> lignes = new ArrayList<>();
        for (Map.Entry<String, Integer> e : parLibelle.entrySet()) {
            if (lignes.size() >= PIECES_MAX) {
                lignes.add("… et d'autres pièces.");
                break;
            }
            lignes.add(e.getKey() + (e.getValue() > 1 ? " (" + e.getValue() + " versions)" : ""));
        }
        return lignes;
    }

    // ------------------------------------------------------------------ formatage

    private static String valeur(String texte) {
        return texte == null || texte.isBlank() ? "non renseigné" : texte.strip();
    }

    private static String borne(String texte) {
        if (texte == null || texte.isBlank()) {
            return "non renseigné";
        }
        String propre = texte.strip().replaceAll("\\s+", " ");
        return propre.length() <= TEXTE_MAX ? propre : propre.substring(0, TEXTE_MAX - 1) + "…";
    }

    private static String jour(LocalDate date) {
        return date.format(JOUR);
    }

    private static String jourHeure(LocalDateTime date) {
        return date.format(JOUR_HEURE);
    }

    /** « 37 heures » plutôt que « 37 » : le modèle reprend l'unité qu'on lui donne. */
    private static String heures(long heures) {
        return heures + (heures > 1 ? " heures" : " heure");
    }
}
