package cnm.prs.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CapmDto;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;

/**
 * ⚠️ 2026-10-14 (demande front « q-piece », §B1 ; arbitrage du pilote du 14/10 sur Q-pièce : « la saisie du calendrier annuel de
 * passation est obligatoire lors de la création du plan de passation », toutes les étapes datées ; V114) — le calendrier complet
 * d'une ligne du plan : chaque processus de la grille CAPM effective de son mode ({@link CapmService#findAll(Integer)}) est daté
 * (date de début) ou marqué <strong>sans objet</strong> avec un motif (H2).
 * <p>
 * Le refus {@code CAPM_INCOMPLET} joue à la <strong>soumission</strong> du plan (création comme nouvelle version, H1 : toute
 * soumission future), pas à l'enregistrement de la ligne : l'import PDF et la mise à jour par import enregistrent des lignes
 * incomplètes (H3), un brouillon se complète en plusieurs fois. Une ligne dont le mode n'a aucune grille n'exige rien.
 */
@Service
@Transactional(readOnly = true)
public class CalendrierPlan {

    public static final String CODE = "CAPM_INCOMPLET";

    private final CapmService capmService;
    private final MarcheRepository marcheRepository;
    private final MarchePrevisionRepository previsionRepository;

    public CalendrierPlan(CapmService capmService, MarcheRepository marcheRepository,
            MarchePrevisionRepository previsionRepository) {
        this.capmService = capmService;
        this.marcheRepository = marcheRepository;
        this.previsionRepository = previsionRepository;
    }

    /**
     * La règle d'un processus saisi : sans objet → aucune date et un motif ; sinon une date de début. {@code null} si conforme,
     * sinon l'erreur sur {@code champ} (préfixe du chemin, vide pour le CRUD unitaire).
     */
    public static ErrorResponse.FieldError violation(String champ, boolean sansObjet, String motif, LocalDate dateDebut,
            LocalDate dateFin) {
        String prefixe = champ == null || champ.isEmpty() ? "" : champ + ".";
        if (sansObjet) {
            if (dateDebut != null || dateFin != null) {
                return new ErrorResponse.FieldError(prefixe + "sansObjet", "Un processus sans objet ne porte pas de date.");
            }
            if (motif == null || motif.isBlank()) {
                return new ErrorResponse.FieldError(prefixe + "motifSansObjet", "Le motif du processus sans objet est obligatoire.");
            }
            return null;
        }
        if (dateDebut == null) {
            return new ErrorResponse.FieldError(prefixe + "dateDebut", "La date de début est obligatoire.");
        }
        return null;
    }

    /** Les processus de la grille du mode qui ne sont ni datés ni sans objet sur la ligne, dans l'ordre de la grille. */
    public List<CapmDto> manquants(Marche ligne, List<MarchePrevision> previsions, Map<Integer, List<CapmDto>> grilles) {
        if (ligne.getIdMode() == null) {
            return List.of();
        }
        List<CapmDto> grille = grilles.computeIfAbsent(ligne.getIdMode(), capmService::findAll);
        Set<Integer> renseignes = previsions.stream().filter(p -> p.isSansObjet() || p.getDateDebut() != null)
                .map(MarchePrevision::getIdCapm).collect(Collectors.toSet());
        return grille.stream().filter(c -> !renseignes.contains(c.getIdCapm())).toList();
    }

    /**
     * Le refus à la soumission d'un plan : 409 {@code CAPM_INCOMPLET}, {@code details.lignes} = une entrée par ligne incomplète
     * ({@code idDetail}, {@code designation}, {@code processus} = les libellés manquants).
     */
    public void exigerComplet(Integer idDossier) {
        Map<Integer, List<CapmDto>> grilles = new HashMap<>();
        Map<Integer, List<MarchePrevision>> parLigne = previsionRepository.findParDossiers(List.of(idDossier)).stream()
                .collect(Collectors.groupingBy(MarchePrevision::getIdDetail));
        List<Map<String, Object>> lignes = new ArrayList<>();
        String premiere = null;
        for (Marche m : marcheRepository.findByIdDossier(idDossier)) {
            if (Boolean.TRUE.equals(m.getSupprimee())) {
                continue;
            }
            List<CapmDto> manquants = manquants(m, parLigne.getOrDefault(m.getIdDetail(), List.of()), grilles);
            if (!manquants.isEmpty()) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("idDetail", m.getIdDetail());
                l.put("designation", m.getDesignationMarche());
                l.put("processus", manquants.stream().map(CapmDto::getLibelleProcessus).toList());
                if (premiere == null) {
                    premiere = m.getDesignationMarche() + " : " + manquants.stream().map(CapmDto::getLibelleProcessus)
                            .collect(Collectors.joining(", "));
                }
                lignes.add(l);
            }
        }
        if (!lignes.isEmpty()) {
            throw new BusinessRuleException("Le calendrier de passation est incomplet sur " + lignes.size() + " ligne(s) : chaque "
                    + "processus du modèle doit être daté ou marqué sans objet (" + premiere + (lignes.size() > 1 ? " ; …" : "") + ").",
                    CODE, idDossier, Map.of("lignes", lignes));
        }
    }
}
