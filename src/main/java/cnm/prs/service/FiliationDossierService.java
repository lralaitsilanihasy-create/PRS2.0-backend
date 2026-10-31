package cnm.prs.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.FiliationDossierDto;
import cnm.prs.dto.FiliationDossierDto.Ligne;
import cnm.prs.dto.FiliationDossierDto.Noeud;
import cnm.prs.entity.ActeGestion;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Marche;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ActeGestionRepository;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Visibilite;

/**
 * ⚠️ 2026-10-31 (demande front « filiation-dossier », §B1 ; demande du pilote) — la <strong>filiation</strong> d'un dossier : l'arbre
 * complet auquel il appartient, quel que soit le point d'entrée, la racine en tête.
 * <ul>
 *   <li>{@code VERSION_SUIVANTE} : la mise à jour d'un dossier ({@code ID_DOSSIER_PARENT}) ;</li>
 *   <li>{@code MISE_EN_CONCURRENCE} : le dossier né de la fiche d'une ligne du plan ({@code ID_DMC} → ligne → plan), sous la version
 *       du plan qui porte la ligne ;</li>
 *   <li>{@code MARCHE} : le dossier de marché d'un lot ({@code t_attribution}), sous le dossier de mise en concurrence, à défaut sous
 *       le plan (procédure sans dossier de mise en concurrence) ;</li>
 *   <li>{@code MODIFICATION} : le dossier d'acte de gestion d'un marché ({@code t_acte_gestion.ID_DOSSIER_MARCHE}).</li>
 * </ul>
 * Les marchés subséquents et les bons de commande n'ont pas de numéro de dossier, et un réexamen garde le numéro de son dossier :
 * ils ne sont pas des nœuds (ils se lisent dans l'historique du dossier). Un nœud hors du périmètre du connecté est rendu masqué. Lu
 * par niveau, une requête par nature et par niveau (pas de lecture par nœud).
 */
@Service
@Transactional(readOnly = true)
public class FiliationDossierService {

    private static final int PROFONDEUR_MAX = 30;

    private final DossierRepository dossiers;
    private final DossierMecRepository dmcs;
    private final MarcheRepository marches;
    private final AttributionRepository attributions;
    private final ActeGestionRepository actes;
    private final DossierService dossierService;

    public FiliationDossierService(DossierRepository dossiers, DossierMecRepository dmcs, MarcheRepository marches,
            AttributionRepository attributions, ActeGestionRepository actes, DossierService dossierService) {
        this.dossiers = dossiers;
        this.dmcs = dmcs;
        this.marches = marches;
        this.attributions = attributions;
        this.actes = actes;
        this.dossierService = dossierService;
    }

    /** {@code GET /api/dossiers/{idDossier}/filiation} : 404 inconnu ; 403 si le dossier demandé est hors périmètre. */
    public FiliationDossierDto filiation(Integer idDossier) {
        Dossier demande = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        dossierService.controlerVisibilite(idDossier);
        Dossier racine = racine(demande);
        Construction c = new Construction();
        c.lire(List.of(racine));
        c.lien.put(racine.getIdDossier(), FiliationDossierDto.RACINE);
        // Descente par niveaux : chaque niveau lit les enfants de tous ses nœuds en une requête par nature.
        List<Dossier> niveau = List.of(racine);
        for (int i = 0; i < PROFONDEUR_MAX && !niveau.isEmpty(); i++) {
            niveau = c.enfantsDe(niveau);
        }
        Set<Integer> visibles = visibles(c.parId.keySet());
        return new FiliationDossierDto(idDossier, c.noeud(racine.getIdDossier(), visibles));
    }

    // ------------------------------------------------------------------ la racine

    /** Remonte au dossier sans parent : acte de gestion → marché → dossier de mise en concurrence → plan → première version. */
    private Dossier racine(Dossier d) {
        Dossier courant = d;
        Set<Integer> vus = new HashSet<>();
        while (courant != null && vus.add(courant.getIdDossier()) && vus.size() < PROFONDEUR_MAX) {
            Dossier parent = parent(courant);
            if (parent == null) {
                return courant;
            }
            courant = parent;
        }
        return courant == null ? d : courant;
    }

    private Dossier parent(Dossier d) {
        Integer id = d.getIdDossier();
        // Un acte de gestion : le marché qu'il modifie.
        Integer marche = actes.findByIdDossier(id).map(ActeGestion::getIdDossierMarche).orElse(null);
        if (marche != null) {
            return dossiers.findById(marche).orElse(null);
        }
        // Un dossier de marché : le dossier de mise en concurrence de sa procédure, à défaut le plan de sa ligne.
        Attribution a = attributions.findFirstByIdDossier(id).orElse(null);
        if (a != null) {
            List<Dossier> mec = dossiers.findByIdDmcIn(List.of(a.getIdDmc()));
            return !mec.isEmpty() ? mec.get(0) : planDeLaProcedure(a.getIdDmc());
        }
        // Un dossier de mise en concurrence : le plan qui porte sa ligne.
        if (d.getIdDmc() != null) {
            return planDeLaProcedure(d.getIdDmc());
        }
        return d.getIdDossierParent() == null ? null : dossiers.findById(d.getIdDossierParent()).orElse(null);
    }

    private Dossier planDeLaProcedure(Long idDmc) {
        return dmcs.findById(idDmc).map(DossierMec::getIdDetail).flatMap(marches::findById).map(Marche::getIdDossier)
                .flatMap(dossiers::findById).orElse(null);
    }

    // ------------------------------------------------------------------ la descente

    /** L'arbre en cours de construction : les dossiers lus, le lien, le lot et la ligne de chacun, ses enfants. */
    private final class Construction {
        final Map<Integer, Dossier> parId = new LinkedHashMap<>();
        final Map<Integer, String> lien = new HashMap<>();
        final Map<Integer, Integer> lot = new HashMap<>();
        final Map<Integer, Ligne> ligne = new HashMap<>();
        final Map<Integer, List<Integer>> enfants = new HashMap<>();

        void lire(Collection<Dossier> ds) {
            ds.forEach(d -> parId.putIfAbsent(d.getIdDossier(), d));
        }

        /** Rattache un enfant (une seule fois : le premier lien trouvé l'emporte) ; vrai s'il est nouveau. */
        boolean rattacher(Integer parent, Dossier enfant, String nature) {
            if (enfant == null || parId.containsKey(enfant.getIdDossier())) {
                return false;
            }
            parId.put(enfant.getIdDossier(), enfant);
            lien.put(enfant.getIdDossier(), nature);
            enfants.computeIfAbsent(parent, k -> new ArrayList<>()).add(enfant.getIdDossier());
            return true;
        }

        /** Les enfants de tous les dossiers d'un niveau ; rend le niveau suivant. */
        List<Dossier> enfantsDe(List<Dossier> niveau) {
            List<Dossier> suivant = new ArrayList<>();
            List<Integer> ids = niveau.stream().map(Dossier::getIdDossier).toList();
            // Les versions suivantes.
            for (Dossier v : dossiers.findByIdDossierParentIn(ids)) {
                if (rattacher(v.getIdDossierParent(), v, FiliationDossierDto.VERSION_SUIVANTE)) {
                    suivant.add(v);
                }
            }
            // Les dossiers de mise en concurrence nés des lignes des plans du niveau (et les marchés d'une procédure sans eux).
            List<Integer> plans = niveau.stream().filter(d -> PublicationAgpmService.FAMILLE_DDP.equals(d.getIdTypeDossier()))
                    .map(Dossier::getIdDossier).toList();
            if (!plans.isEmpty()) {
                Map<Integer, Marche> lignes = new HashMap<>();
                marches.findByIdDossierIn(plans).forEach(m -> lignes.put(m.getIdDetail(), m));
                List<DossierMec> procedures = lignes.isEmpty() ? List.of() : dmcs.findByIdDetailIn(lignes.keySet());
                Map<Long, DossierMec> parDmc = new HashMap<>();
                procedures.forEach(p -> parDmc.put(p.getIdDmc(), p));
                Set<Long> avecDossier = new HashSet<>();
                if (!parDmc.isEmpty()) {
                    for (Dossier mec : dossiers.findByIdDmcIn(parDmc.keySet())) {
                        Marche l = lignes.get(parDmc.get(mec.getIdDmc()).getIdDetail());
                        avecDossier.add(mec.getIdDmc());
                        if (rattacher(l.getIdDossier(), mec, FiliationDossierDto.MISE_EN_CONCURRENCE)) {
                            ligne.put(mec.getIdDossier(), new Ligne(l.getIdDetail(), l.getDesignationMarche()));
                            suivant.add(mec);
                        }
                    }
                    // Une procédure sans dossier de mise en concurrence (remise papier ancienne) : ses marchés sous le plan.
                    List<Long> sans = parDmc.keySet().stream().filter(x -> !avecDossier.contains(x)).toList();
                    if (!sans.isEmpty()) {
                        suivant.addAll(marchesDe(sans, x -> lignes.get(parDmc.get(x).getIdDetail())));
                    }
                }
            }
            // Les dossiers de marché des procédures du niveau.
            List<Long> procedures = niveau.stream().map(Dossier::getIdDmc).filter(Objects::nonNull).distinct().toList();
            if (!procedures.isEmpty()) {
                Map<Long, Integer> mecParDmc = new HashMap<>();
                niveau.stream().filter(d -> d.getIdDmc() != null).forEach(d -> mecParDmc.putIfAbsent(d.getIdDmc(), d.getIdDossier()));
                suivant.addAll(marchesDe(procedures, null, mecParDmc));
            }
            // Les actes de gestion des marchés du niveau.
            List<ActeGestion> as = actes.findByIdDossierMarcheInOrderByIdActeAsc(ids);
            if (!as.isEmpty()) {
                Map<Integer, Dossier> ds = new HashMap<>();
                dossiers.findAllById(as.stream().map(ActeGestion::getIdDossier).filter(Objects::nonNull).toList())
                        .forEach(d -> ds.put(d.getIdDossier(), d));
                for (ActeGestion a : as) {
                    Dossier d = ds.get(a.getIdDossier());
                    if (rattacher(a.getIdDossierMarche(), d, FiliationDossierDto.MODIFICATION)) {
                        suivant.add(d);
                    }
                }
            }
            return suivant;
        }

        /** Les marchés d'une procédure sans dossier de mise en concurrence, sous le plan de sa ligne. */
        private List<Dossier> marchesDe(List<Long> procedures, java.util.function.Function<Long, Marche> ligneDe) {
            Map<Long, Integer> parent = new HashMap<>();
            Map<Long, Marche> l = new HashMap<>();
            for (Long p : procedures) {
                Marche m = ligneDe.apply(p);
                if (m != null) {
                    parent.put(p, m.getIdDossier());
                    l.put(p, m);
                }
            }
            List<Dossier> ajoutes = marchesDe(procedures, l, parent);
            return ajoutes;
        }

        /** Les dossiers de marché de ces procédures, rattachés au dossier {@code parent} de chacune ({@code lignes} : leur ligne). */
        private List<Dossier> marchesDe(List<Long> procedures, Map<Long, Marche> lignes, Map<Long, Integer> parent) {
            List<Dossier> ajoutes = new ArrayList<>();
            List<Attribution> as = attributions.findByIdDmcInOrderByIdDmcAscLotAsc(procedures).stream()
                    .filter(a -> a.getIdDossier() != null).toList();
            if (as.isEmpty()) {
                return ajoutes;
            }
            Map<Integer, Dossier> ds = new HashMap<>();
            dossiers.findAllById(as.stream().map(Attribution::getIdDossier).distinct().toList()).forEach(d -> ds.put(d.getIdDossier(), d));
            for (Attribution a : as) {
                Integer p = parent.get(a.getIdDmc());
                Dossier d = ds.get(a.getIdDossier());
                if (p != null && rattacher(p, d, FiliationDossierDto.MARCHE)) {
                    lot.put(d.getIdDossier(), a.getLot());
                    Marche m = lignes == null ? null : lignes.get(a.getIdDmc());
                    if (m != null) {
                        ligne.put(d.getIdDossier(), new Ligne(m.getIdDetail(), m.getDesignationMarche()));
                    }
                    ajoutes.add(d);
                }
            }
            return ajoutes;
        }

        Noeud noeud(Integer id, Set<Integer> visibles) {
            Dossier d = parId.get(id);
            List<Noeud> fils = enfants.getOrDefault(id, List.of()).stream()
                    .sorted(Comparator.comparing((Integer x) -> ordre(lien.get(x))).thenComparing(x -> x))
                    .map(x -> noeud(x, visibles)).toList();
            if (!visibles.contains(id)) {
                return new Noeud(null, null, null, null, null, null, lien.get(id), null, null, true, fils);
            }
            return new Noeud(d.getIdDossier(), d.getRefeDossier(), d.getIdTypeDossier(), d.getIdSousType(), d.getStatut(), d.getDateRef(),
                    lien.get(id), lot.get(id), ligne.get(id), false, fils);
        }
    }

    /** Ordre des enfants : versions, puis mises en concurrence, marchés, modifications (puis identifiant). */
    private static int ordre(String lien) {
        return switch (lien == null ? "" : lien) {
            case FiliationDossierDto.VERSION_SUIVANTE -> 0;
            case FiliationDossierDto.MISE_EN_CONCURRENCE -> 1;
            case FiliationDossierDto.MARCHE -> 2;
            default -> 3;
        };
    }

    // ------------------------------------------------------------------ le périmètre

    /** Les dossiers de l'arbre que le connecté voit (périmètre de la liste des dossiers), en une requête. */
    private Set<Integer> visibles(Collection<Integer> ids) {
        if (Visibilite.voitTout()) {
            return new HashSet<>(ids);
        }
        if (Visibilite.estPrmp()) {
            String idPrmp = CurrentUser.ref().filter(s -> !s.isBlank()).orElse(null);
            return idPrmp == null ? Set.of() : new HashSet<>(dossiers.idsVisiblesPourPrmp(ids, idPrmp));
        }
        String localite = CurrentUser.localite().filter(s -> !s.isBlank()).orElse(null);
        return localite == null ? Set.of() : new HashSet<>(dossiers.idsDansLocalite(ids, localite));
    }
}
