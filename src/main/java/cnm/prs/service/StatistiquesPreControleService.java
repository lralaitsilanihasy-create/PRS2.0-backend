package cnm.prs.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.StatistiquesReglesDto;
import cnm.prs.dto.StatistiquesReglesDto.LigneStatistiqueDto;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.enums.SourceSignalement;
import cnm.prs.enums.TypeSignalement;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.RegleAnomalieRepository;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 7) — <strong>le taux d'écartement par
 * règle</strong>, c'est-à-dire la mesure qui dit si l'outil reste utile.
 *
 * <p>C'est la contre-mesure à la fatigue d'alerte, et elle fait partie du lot (plan, 3.e) : « une règle
 * écartée dans 80 % des cas est une mauvaise règle, on la désactive ». Sans cette mesure, personne ne
 * saurait laquelle éteindre — on constaterait seulement, six semaines plus tard, que plus personne ne lit
 * les signalements.</p>
 *
 * <p><strong>Ne rend que des compteurs</strong> : aucun libellé de marché, aucun acteur, aucun plan. C'est
 * ce qui permet de l'ouvrir à l'Administrateur, dont le rôle est technique.</p>
 *
 * <p>⚠️ <strong>Un écartement et une levée ne se lisent pas de la même façon.</strong> Un signalement
 * <em>levé</em> est un succès : la PRMP a corrigé son plan. Un signalement <em>écarté</em> est un désaccord
 * motivé. Les deux sont comptés séparément, et seul le second alimente le taux — confondre les deux ferait
 * éteindre les règles qui marchent le mieux.</p>
 */
@Service
@Transactional(readOnly = true)
public class StatistiquesPreControleService {

    /**
     * Seuil d'alerte du plan : au-delà, la règle est à revoir. Il ne s'applique qu'à partir d'un nombre
     * significatif de signalements — trois signalements dont deux écartés ne disent rien.
     */
    static final double TAUX_SUSPECT = 0.8;
    static final int MINIMUM_SIGNIFICATIF = 5;

    private final AnomalieRepository anomalieRepository;
    private final RegleAnomalieRepository regleAnomalieRepository;

    public StatistiquesPreControleService(AnomalieRepository anomalieRepository,
            RegleAnomalieRepository regleAnomalieRepository) {
        this.anomalieRepository = anomalieRepository;
        this.regleAnomalieRepository = regleAnomalieRepository;
    }

    /**
     * Le tableau de bord, une ligne par règle, <strong>la plus écartée d'abord</strong> : c'est celle dont
     * il faut décider.
     *
     * <p>Les règles qui n'ont encore rien produit sont servies aussi, à zéro : leur absence donnerait à
     * penser qu'elles n'existent pas, alors qu'elles attendent peut-être simplement leur premier plan.</p>
     *
     * @param exercice pour observer un exercice en particulier, ou {@code null} pour tous
     */
    public StatistiquesReglesDto parRegle(Integer exercice) {
        Map<String, long[]> compteurs = new HashMap<>();
        for (Object[] ligne : anomalieRepository.compterParRegle(exercice)) {
            compteurs.put((String) ligne[0], new long[] {
                    nombre(ligne[1]), nombre(ligne[2]), nombre(ligne[3]), nombre(ligne[4]) });
        }

        List<LigneStatistiqueDto> lignes = new ArrayList<>();
        int total = 0;
        int ecartes = 0;
        for (RegleAnomalie regle : regleAnomalieRepository.findAll()) {
            long[] c = compteurs.getOrDefault(regle.getCodeRegle(), new long[4]);
            int t = (int) c[0];
            int ouverts = (int) c[1];
            int ecartesRegle = (int) c[2];
            int leves = (int) c[3];
            double taux = t == 0 ? 0 : (double) ecartesRegle / t;
            lignes.add(new LigneStatistiqueDto(regle.getCodeRegle(), regle.getLibelle(),
                    source(regle.getCodeRegle()), !Boolean.FALSE.equals(regle.getActif()),
                    t, ouverts, ecartesRegle, leves, taux,
                    t >= MINIMUM_SIGNIFICATIF && taux >= TAUX_SUSPECT));
            total += t;
            ecartes += ecartesRegle;
        }
        lignes.sort(Comparator.comparingDouble(LigneStatistiqueDto::taux).reversed()
                .thenComparing(Comparator.comparingInt(LigneStatistiqueDto::total).reversed())
                .thenComparing(LigneStatistiqueDto::code));

        return new StatistiquesReglesDto(exercice, total, ecartes,
                total == 0 ? 0 : (double) ecartes / total, lignes);
    }

    /**
     * Source d'une règle, déduite de son code : les trois types de l'assistant sont des pistes, les autres
     * des faits. Une règle inconnue de l'énumération (créée à la main par l'Administrateur) est comptée
     * comme un fait — c'est le défaut sûr : elle sera jugée avec la sévérité d'une règle.
     */
    private static String source(String code) {
        return TypeSignalement.typesDeLAssistant().stream().anyMatch(t -> t.name().equals(code))
                ? SourceSignalement.IA.name() : SourceSignalement.REGLE.name();
    }

    private static long nombre(Object valeur) {
        return valeur instanceof Number n ? n.longValue() : 0L;
    }
}
