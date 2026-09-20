package cnm.prs.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.config.AssistantIaProperties;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.enums.TypeSignalement;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.RegleAnomalieRepository;
import cnm.prs.service.PreControlePpmService.ResultatPreControle;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 6) — <strong>ce que l'assistant ajoute
 * par-dessus les règles</strong>.
 *
 * <h2>Le partage du travail, et pourquoi il est dans ce sens</h2>
 *
 * <p>Les règles détectent, l'assistant <strong>explique et propose</strong> (plan, 3.a). Il n'est
 * <strong>jamais</strong> appelé sur ce qu'une règle sait faire : le fractionnement par compte, la
 * conformité du mode au seuil, la somme des lots sont des faits opposables, reproductibles, cités avec
 * leur base légale — un modèle de langage n'y ajouterait que de l'incertitude. Et il n'est pas appelé
 * ligne par ligne : un PPM porte des centaines de lignes et le GPU est partagé par tous les
 * utilisateurs.</p>
 *
 * <p>Il intervient sur les trois points que le manuel demande de vérifier et qu'<strong>aucune règle ne
 * peut établir</strong>, parce que l'information n'existe que dans une phrase libre : le
 * <strong>fractionnement déguisé</strong> (même besoin sous des comptes ou des libellés différents — le
 * procédé même d'évasion que la règle du compte ne voit pas), l'<strong>objet imprécis</strong> au regard
 * des mentions attendues (manuel, p. 14), et la <strong>nature incohérente</strong> avec l'objet ou le
 * compte.</p>
 *
 * <h2>Les garde-fous, qui sont l'essentiel</h2>
 * <ul>
 *   <li><strong>Ses constats naissent en pistes</strong> ({@code SOURCE = IA}, porté par le type) :
 *       l'écran ne les présente jamais comme des faits, et une PRMP qui écarte à raison une intuition
 *       fausse du modèle n'en porte pas la marque.</li>
 *   <li><strong>Rien de ce que le modèle rend n'est cru sur parole</strong> — le filtrage est dans
 *       {@link DialogueAnalyseIa}, isolé pour être mesurable.</li>
 *   <li><strong>Chaque type de piste a son interrupteur</strong> ({@code t_regle_anomalie.ACTIF}) : la
 *       couche IA est la plus susceptible d'être bruyante, c'est celle qu'on doit pouvoir éteindre la
 *       première, sans redéploiement.</li>
 *   <li><strong>Aucune donnée personnelle ne sort</strong> : le modèle reçoit des lignes de plan — objet,
 *       nature, compte, financement, montant —, pas d'acteur, pas de PRMP, pas de dossier.</li>
 *   <li><strong>Chaque analyse est journalisée</strong> comme tout échange avec l'assistant (le plan
 *       examiné, le modèle, le nombre de pistes, la durée), pour qu'on sache toujours ce que l'assistant a
 *       dit et sur quoi.</li>
 *   <li><strong>Une panne du modèle ne prive personne du pré-contrôle</strong> : les constats des règles
 *       sont servis par les autres endpoints, et l'analyse rend un 503 explicite.</li>
 * </ul>
 */
@Service
@Transactional
public class AnalysePreControleIaService {

    private static final Logger log = LoggerFactory.getLogger(AnalysePreControleIaService.class);

    /** Journal d'audit : même table que les échanges de l'assistant (lot 1), action distincte. */
    static final String TABLE_AUDIT = "assistant_ia";
    static final String ACTION_AUDIT = "ANALYSE_PRE_CONTROLE";

    private final AssistantIaProperties props;
    private final ClientModeleIa client;
    private final DialogueAnalyseIa dialogue;
    private final PreControlePpmService preControle;
    private final RegleAnomalieRepository regleAnomalieRepository;
    private final AuditLogService audit;

    public AnalysePreControleIaService(AssistantIaProperties props, ClientModeleIa client,
            DialogueAnalyseIa dialogue, PreControlePpmService preControle,
            RegleAnomalieRepository regleAnomalieRepository, AuditLogService audit) {
        this.props = props;
        this.client = client;
        this.dialogue = dialogue;
        this.preControle = preControle;
        this.regleAnomalieRepository = regleAnomalieRepository;
        this.audit = audit;
    }

    /**
     * Ce qu'une analyse rend.
     *
     * @param synthese        la phrase de hiérarchisation — <strong>non enregistrée</strong>, c'est une aide
     *                        à la lecture recalculée à la demande
     * @param lignesAnalysees combien de lignes l'assistant a réellement <strong>lues</strong>
     * @param lignesDuPlan    combien le plan en porte. Les deux sont rendus, et l'écran le dit : une
     *                        couverture partielle <strong>annoncée</strong> vaut mieux qu'une couverture
     *                        totale supposée — c'est le défaut que la recette du 2026-09-20 a révélé
     * @param resultat        le compte rendu du rapprochement
     */
    public record Analyse(String synthese, int lignesAnalysees, int lignesDuPlan,
            ResultatPreControle resultat) {
    }

    /**
     * Analyse un plan avec l'assistant et enregistre ses pistes.
     *
     * @param contexte  le plan chargé (les mêmes données que les règles, chargées une fois)
     * @param refActeur référence de l'acteur qui demande, pour le journal
     * @throws ResourceNotFoundException si l'assistant n'est pas activé — même contrat que le lot 1
     * @throws ClientModeleIa.ModeleIndisponibleException si le serveur d'inférence ne répond pas (→ 503)
     */
    public Analyse analyser(ContextePreControle contexte, String refActeur) {
        if (!props.actif()) {
            throw new ResourceNotFoundException("L'assistant IA n'est pas activé.");
        }
        Ppm ppm = contexte.ppm();
        Set<String> typesActifs = typesActifs();
        if (typesActifs.isEmpty()) {
            log.info("[PRE-CONTROLE IA] aucune piste active : analyse inutile sur le PPM {}", ppm.getIdPpm());
            return new Analyse(null, 0, contexte.lignes().size(),
                    preControle.enregistrerConstatsIa(ppm.getIdPpm(), List.of(), Set.of()));
        }

        List<List<Marche>> lots = dialogue.lots(contexte);
        int lignesAnalysees = lots.stream().mapToInt(List::size).sum();
        long debut = System.nanoTime();

        // ⚠️ UNE PASSE PAR TYPE, et non une question portant les trois. La batterie de référence a tranché :
        // une consigne unique avec ses exceptions et son ordre de priorité fait se contredire un modèle de
        // 9 milliards de paramètres. Trois questions courtes coûtent plus de calcul et rendent bien mieux —
        // et l'analyse est un geste explicite et rare, pas une frappe au clavier.
        //
        // ⚠️ ET PAR LOTS DE LIGNES (recette du 2026-09-20) : un plan réel de 130 lignes faisait un
        // inventaire de 37 000 caractères, très au-delà de la fenêtre de contexte d'un modèle local. Le
        // prompt arrivait tronqué, la réponse était illisible, et l'analyse annonçait « rien à signaler »
        // sur un plan qu'elle n'avait pas lu.
        List<SignalementDetecte> pistes = new ArrayList<>();
        Set<String> clesVues = new LinkedHashSet<>();
        for (TypeSignalement type : TypeSignalement.typesDeLAssistant()) {
            if (!typesActifs.contains(type.name())) {
                continue;
            }
            for (List<Marche> lot : lots) {
                if (pistes.size() >= DialogueAnalyseIa.PISTES_MAX) {
                    break;
                }
                String reponse;
                try {
                    reponse = client.generer(
                            List.of(new ClientModeleIa.Message("system", dialogue.consigne(type)),
                                    new ClientModeleIa.Message("user", dialogue.inventaire(contexte, lot))),
                            fragment -> { /* pas de flux : une passe rend un JSON complet ou rien */ },
                            () -> false, DialogueAnalyseIa.JETONS_PAR_PASSE);
                } catch (ClientModeleIa.ModeleIndisponibleException e) {
                    // Le serveur d'inférence est tombé : on ne garde rien de ce qui précède plutôt que de
                    // livrer une analyse tronquée qui passerait pour complète.
                    journaliser(refActeur, ppm, 0, lignesAnalysees, millisecondes(debut), e.getMessage());
                    throw e;
                }
                for (SignalementDetecte piste : dialogue.lire(reponse, type, contexte, lot, clesVues).pistes()) {
                    if (pistes.size() < DialogueAnalyseIa.PISTES_MAX) {
                        pistes.add(piste);
                    }
                }
            }
        }

        ResultatPreControle resultat = preControle.enregistrerConstatsIa(ppm.getIdPpm(), pistes, typesActifs);
        journaliser(refActeur, ppm, pistes.size(), lignesAnalysees, millisecondes(debut), null);
        return new Analyse(dialogue.synthese(pistes), lignesAnalysees, contexte.lignes().size(), resultat);
    }

    /** Les types de l'assistant dont la ligne de {@code t_regle_anomalie} est active. */
    private Set<String> typesActifs() {
        Set<String> actifs = new LinkedHashSet<>();
        for (TypeSignalement type : TypeSignalement.typesDeLAssistant()) {
            Optional<RegleAnomalie> regle = regleAnomalieRepository.findByCodeRegle(type.name());
            if (regle.isPresent() && !Boolean.FALSE.equals(regle.get().getActif())) {
                actifs.add(type.name());
            }
        }
        return actifs;
    }

    // ------------------------------------------------------------------ journal

    private void journaliser(String refActeur, Ppm ppm, int pistes, int lignesAnalysees, long dureeMs,
            String erreur) {
        String trace = "Analyse du pré-contrôle — PPM " + ppm.getIdPpm()
                + " (exercice " + ppm.getExercice() + ")"
                + "\nLignes examinées : " + lignesAnalysees
                + "\nPistes retenues : " + pistes
                + "\nDurée : " + dureeMs + " ms"
                + (erreur == null ? "" : "\nErreur : " + erreur);
        try {
            audit.enregistrerDetail(refActeur, TABLE_AUDIT, String.valueOf(ppm.getIdPpm()), ACTION_AUDIT,
                    props.modele(), trace, null);
        } catch (RuntimeException e) {
            // Le journal ne doit jamais casser une analyse — même règle que l'intercepteur d'audit.
            log.warn("[PRE-CONTROLE IA] échec de la journalisation de l'analyse ({})", e.getMessage());
        }
    }

    private static long millisecondes(long debutNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - debutNanos);
    }
}
