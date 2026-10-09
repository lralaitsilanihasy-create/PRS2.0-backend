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
    /** ⚠️ M5b (manuel de contrôle, §B6 ; V99) — délais surchargés par sous-type. */
    private final cnm.prs.repository.DelaiSousTypeRepository surcharges;
    private final cnm.prs.repository.SousTypeDossierRepository sousTypes;

    public DelaiStandardService(DelaiStandardRepository repository, cnm.prs.repository.DelaiSousTypeRepository surcharges,
            cnm.prs.repository.SousTypeDossierRepository sousTypes) {
        this.repository = repository;
        this.surcharges = surcharges;
        this.sousTypes = sousTypes;
    }

    /**
     * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M5b, §B6 ; V99) — le référentiel complet, lu <strong>une fois</strong> pour
     * une liste : les délais des étapes et leurs surcharges par sous-type. {@link #pour} rend la carte d'un dossier : celle des étapes,
     * corrigée des surcharges de son sous-type.
     */
    public record Referentiel(Map<EtapeCircuit, Integer> base, Map<String, Map<EtapeCircuit, Integer>> parSousType) {

        public Map<EtapeCircuit, Integer> pour(String sousType) {
            Map<EtapeCircuit, Integer> propres = sousType == null ? null : parSousType.get(sousType);
            if (propres == null || propres.isEmpty()) {
                return base;
            }
            Map<EtapeCircuit, Integer> fusion = new EnumMap<>(base);
            fusion.putAll(propres);
            return fusion;
        }
    }

    /** Le référentiel des étapes et des sous-types, en <strong>une</strong> requête scalaire (budget de requêtes des listes). */
    public Referentiel referentiel() {
        Map<EtapeCircuit, Integer> base = new EnumMap<>(EtapeCircuit.class);
        Map<String, Map<EtapeCircuit, Integer>> parSousType = new java.util.HashMap<>();
        for (Object[] l : surcharges.toutLeReferentiel()) {
            if (l.length < 3 || l[1] == null || l[2] == null) {
                continue;
            }
            try {
                EtapeCircuit etape = EtapeCircuit.valueOf((String) l[1]);
                int valeur = ((Number) l[2]).intValue();
                if (valeur <= 0) {
                    continue;
                }
                if (l[0] == null) {
                    base.put(etape, valeur);
                } else {
                    parSousType.computeIfAbsent((String) l[0], k -> new EnumMap<>(EtapeCircuit.class)).put(etape, valeur);
                }
            } catch (IllegalArgumentException ex) {
                // Étape retirée du code : ignorée, le repli s'applique.
            }
        }
        for (EtapeCircuit etape : EtapeCircuit.values()) {
            base.putIfAbsent(etape, DELAI_DE_REPLI);
        }
        return new Referentiel(base, parSousType);
    }

    /** Les délais d'un dossier de ce sous-type (surcharges comprises). */
    public Map<EtapeCircuit, Integer> delais(String sousType) {
        return referentiel().pour(sousType);
    }

    /**
     * Le tableau d'un sous-type pour l'Administrateur : chaque étape de la Commission avec son délai effectif, le délai standard de
     * l'étape et la marque de la surcharge. 404 si le sous-type est inconnu.
     */
    public List<cnm.prs.dto.DelaiSousTypeDto> tableauSousType(String sousType) {
        exigerSousType(sousType);
        Map<EtapeCircuit, Integer> base = delais();
        Map<EtapeCircuit, Integer> propres = referentiel().parSousType().getOrDefault(sousType, Map.of());
        List<cnm.prs.dto.DelaiSousTypeDto> lignes = new ArrayList<>();
        for (EtapeCircuit etape : EtapeCircuit.values()) {
            if (etape.porteur() == cnm.prs.enums.ProfilUtilisateur.PRMP) {
                continue;
            }
            Integer propre = propres.get(etape);
            lignes.add(new cnm.prs.dto.DelaiSousTypeDto(sousType, etape.name(), propre != null ? propre : base.get(etape), base.get(etape),
                    propre != null));
        }
        return lignes;
    }

    /** Surcharge le délai d'une étape pour un sous-type. 404 étape ou sous-type inconnus. */
    @Transactional
    public cnm.prs.dto.DelaiSousTypeDto definirSousType(String sousType, String etape, DelaiStandardDto dto) {
        exigerSousType(sousType);
        EtapeCircuit cible = etapeDeLaCommission(etape);
        surcharges.save(new cnm.prs.entity.DelaiSousType(sousType, cible.name(), dto.delaiHeures()));
        return new cnm.prs.dto.DelaiSousTypeDto(sousType, cible.name(), dto.delaiHeures(), delais().get(cible), true);
    }

    /** Retire la surcharge : l'étape reprend son délai standard pour ce sous-type. 404 étape ou sous-type inconnus. */
    @Transactional
    public void retirerSousType(String sousType, String etape) {
        exigerSousType(sousType);
        EtapeCircuit cible = etapeDeLaCommission(etape);
        surcharges.deleteById(new cnm.prs.entity.DelaiSousType.Cle(sousType, cible.name()));
    }

    private void exigerSousType(String sousType) {
        if (sousType == null || !sousTypes.existsById(sousType)) {
            throw new ResourceNotFoundException("Sous-type inconnu : " + sousType);
        }
    }

    private static EtapeCircuit etapeDeLaCommission(String etape) {
        EtapeCircuit cible;
        try {
            cible = EtapeCircuit.valueOf(etape);
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("Étape inconnue : " + etape);
        }
        if (cible.porteur() == cnm.prs.enums.ProfilUtilisateur.PRMP) {
            throw new ResourceNotFoundException("Étape inconnue : " + etape);
        }
        return cible;
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
        // ⚠️ 2026-09-07 — le référentiel des délais est celui des étapes de la CNM : l'étape portée par la
        // PRMP (rectification) n'y figure pas. Son délai n'est pas de la responsabilité de la Commission,
        // et son temps est de toute façon suspensif — l'Administrateur n'a rien à y régler.
        for (EtapeCircuit etape : EtapeCircuit.values()) {
            if (etape.porteur() == cnm.prs.enums.ProfilUtilisateur.PRMP) {
                continue;
            }
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
        // Ce qui ne figure pas au référentiel ne s'y règle pas : l'étape de la PRMP n'est pas un délai
        // de la Commission, et un réglage qui n'apparaît nulle part serait un piège pour l'Administrateur.
        if (cible.porteur() == cnm.prs.enums.ProfilUtilisateur.PRMP) {
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
