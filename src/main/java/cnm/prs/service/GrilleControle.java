package cnm.prs.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.repository.SousTypeDossierRepository;

/**
 * ⚠️ 2026-10-08 (manuel de contrôle a priori, tranche M3, §B3 ; V96 ; arbitrages du pilote : 5 points ajoutés aux plans, point
 * conditionné servi à examiner sans fiche) — la grille de contrôle d'un sous-type, et celle d'un dossier :
 * <ul>
 *   <li>les points <strong>communs</strong> de la famille, sauf si le sous-type a une <strong>grille propre</strong> (MPI, MGG, DC, AVN…) ;</li>
 *   <li>les points de sa <strong>grille de base</strong>, de proche en proche (DAORI ← DAOR ← DAOO), puis les siens ;</li>
 *   <li>pour un dossier, les points conditionnés par la catégorie ou la forme de sa fiche qui ne s'appliquent pas sont retirés ; sans
 *   fiche, ils restent servis (le Membre les examine).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GrilleControle {

    private final PointsCtrlRepository points;
    private final SousTypeDossierRepository sousTypes;
    private final PiecesExigees pieces;

    public GrilleControle(PointsCtrlRepository points, SousTypeDossierRepository sousTypes, PiecesExigees pieces) {
        this.points = points;
        this.sousTypes = sousTypes;
        this.pieces = pieces;
    }

    /** La grille d'un sous-type (conditions non résolues), triée par ordre. */
    public List<PointsCtrl> duSousType(String famille, String sousType) {
        SousTypeDossier st = sousType == null ? null : sousTypes.findById(sousType).orElse(null);
        Map<Integer, PointsCtrl> grille = new LinkedHashMap<>();
        boolean propre = st != null && Boolean.TRUE.equals(st.getGrillePropre());
        if (!propre) {
            points.findByIdTypeDossierOrderByOrdrePointCtrlAsc(famille).stream().filter(p -> p.getIdSousType() == null)
                    .forEach(p -> grille.putIfAbsent(p.getIdPointCtrl(), p));
        }
        Set<String> vus = new HashSet<>();
        List<String> chaine = new ArrayList<>();
        for (SousTypeDossier x = st; x != null && vus.add(x.getIdSousType()); x = x.getGrilleBase() == null ? null
                : sousTypes.findById(x.getGrilleBase()).orElse(null)) {
            chaine.add(0, x.getIdSousType());   // la base d'abord
        }
        if (st == null && sousType != null) {
            chaine.add(sousType);
        }
        for (String s : chaine) {
            points.findGrilleEffective(famille, s).stream().filter(p -> s.equals(p.getIdSousType()))
                    .forEach(p -> grille.putIfAbsent(p.getIdPointCtrl(), p));
        }
        return grille.values().stream().sorted(Comparator.comparing((PointsCtrl p) -> p.getOrdrePointCtrl() == null ? Integer.MAX_VALUE
                : p.getOrdrePointCtrl()).thenComparing(PointsCtrl::getIdPointCtrl)).toList();
    }

    /** La grille d'un dossier : celle de son sous-type, sans les points conditionnés qui ne s'appliquent pas à sa fiche. */
    public List<PointsCtrl> pour(Dossier d) {
        PiecesExigees.Contexte c = pieces.contexte(d);
        return duSousType(d.getIdTypeDossier(), d.getIdSousType()).stream()
                .filter(p -> !Boolean.FALSE.equals(PiecesExigees.vaut(p.getCategorie(), p.getForme(), c))).toList();
    }
}
