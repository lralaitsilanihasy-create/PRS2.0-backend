package cnm.prs.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DelaiStandardDto;
import cnm.prs.entity.DelaiStandard;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DelaiStandardRepository;

/**
 * ⚠️ <strong>Référentiel administrable des délais standards</strong> (arbitrage ②, 2026-09-01).
 *
 * <p>Il fournit la prévision des étapes <strong>pas encore prises en charge</strong>, ce qui permet
 * d'annoncer une date à la PRMP <strong>dès la soumission</strong> — avant que quiconque à la CNM ait
 * touché le dossier. Chaque prise en charge le remplace, pour son étape, par la prévision réellement
 * saisie.</p>
 *
 * <p><strong>Le référentiel ne peut pas être vide.</strong> La lecture rend toujours les huit étapes,
 * même si la table en manque : une étape sans délai standard ferait disparaître un terme de la somme et
 * la date annoncée serait silencieusement trop optimiste. Un défaut de repli vaut mieux qu'un trou.</p>
 *
 * <p><strong>Lu en projection scalaire.</strong> La date prévisionnelle est calculée pour chaque dossier
 * d'une liste ; charger les entités du référentiel à chaque étape et chaque dossier gonflait le compteur
 * d'entités de Hibernate au point de faire tomber le contrat de pagination (lot D §3). D'où
 * {@link #delais()}, à appeler <strong>une fois</strong> et à passer au calcul.</p>
 */
@Service
@Transactional(readOnly = true)
public class DelaiStandardService {

    /**
     * Repli si le référentiel est muet sur une étape — jamais 0, qui ferait mentir la date annoncée.
     * ⚠️ Passé de 1 jour à <strong>8 heures</strong> le 2026-09-02 : même durée, nouvelle unité.
     */
    private static final int DELAI_DE_REPLI = HeuresOuvrees.HEURES_PAR_JOUR;

    private final DelaiStandardRepository repository;

    public DelaiStandardService(DelaiStandardRepository repository) {
        this.repository = repository;
    }

    /**
     * Délais de <strong>toutes</strong> les étapes, en une requête scalaire. Les étapes absentes de la
     * table prennent le délai de repli : la carte rendue est toujours complète.
     */
    public Map<EtapeCircuit, Integer> delais() {
        Map<EtapeCircuit, Integer> parEtape = new EnumMap<>(EtapeCircuit.class);
        for (Object[] ligne : repository.tousLesDelais()) {
            if (ligne.length < 2 || ligne[0] == null || ligne[1] == null) {
                continue;
            }
            try {
                int valeur = ((Number) ligne[1]).intValue();
                if (valeur > 0) {
                    parEtape.put(EtapeCircuit.valueOf((String) ligne[0]), valeur);
                }
            } catch (IllegalArgumentException ex) {
                // Ligne orpheline (étape retirée du code) : ignorée, le repli s'applique.
            }
        }
        for (EtapeCircuit etape : EtapeCircuit.values()) {
            parEtape.putIfAbsent(etape, DELAI_DE_REPLI);
        }
        return parEtape;
    }

    /** Délai standard d’une étape, en <strong>heures ouvrées</strong>. Jamais nul, jamais zéro. */
    public int delai(EtapeCircuit etape) {
        return etape == null ? DELAI_DE_REPLI : delais().get(etape);
    }

    /** Le référentiel complet, dans l'ordre du circuit — toutes les étapes, y compris celles absentes en base. */
    public List<DelaiStandardDto> tableau() {
        Map<String, DelaiStandard> stockes = new java.util.HashMap<>();
        for (DelaiStandard d : repository.findAll()) {
            stockes.put(d.getEtape(), d);
        }
        List<DelaiStandardDto> lignes = new ArrayList<>();
        for (EtapeCircuit etape : EtapeCircuit.values()) {
            DelaiStandard stocke = stockes.get(etape.name());
            lignes.add(new DelaiStandardDto(etape.name(),
                    stocke == null || stocke.getDelaiHeures() == null ? DELAI_DE_REPLI : stocke.getDelaiHeures(),
                    stocke == null ? null : stocke.getLibelle()));
        }
        return lignes;
    }

    /**
     * Réglage administratif du délai d'une étape. L'étape doit exister dans {@link EtapeCircuit} — un
     * référentiel qu'on peut peupler de clés inventées cesserait d'être un référentiel.
     */
    @Transactional
    public DelaiStandardDto definir(String etape, DelaiStandardDto dto) {
        EtapeCircuit cible;
        try {
            cible = EtapeCircuit.valueOf(etape);
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("Étape inconnue : " + etape);
        }
        DelaiStandard entite = repository.findById(cible.name()).orElseGet(() -> {
            DelaiStandard neuf = new DelaiStandard();
            neuf.setEtape(cible.name());
            return neuf;
        });
        entite.setDelaiHeures(dto.delaiHeures());
        if (dto.libelle() != null && !dto.libelle().isBlank()) {
            entite.setLibelle(dto.libelle().trim());
        }
        DelaiStandard sauve = repository.save(entite);
        return new DelaiStandardDto(sauve.getEtape(), sauve.getDelaiHeures(), sauve.getLibelle());
    }
}
