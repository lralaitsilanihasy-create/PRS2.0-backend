package cnm.prs.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.FicheArticle;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Marche;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.FicheArticleRepository;
import cnm.prs.repository.FicheCaracteristiqueRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.FicheMarcheValeurRepository;
import cnm.prs.repository.FichePieceRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.MemoLecture;
import cnm.prs.repository.ServiceBeneficiaireRepository;

/**
 * ⚠️ 2026-10-30 (demande front « performance », §B1, second chantier) — le <strong>préchargement de l'état des fiches</strong> d'une
 * liste de procédures. La reconstruction de l'état d'une fiche ({@code FicheMarcheService.toDto}) lit, pour chaque procédure : ses
 * versions, ses valeurs, son besoin (articles et caractéristiques), ses pièces, ses avis imprimés, son dossier, et sur le plan la
 * ligne, ses lots, ses bénéficiaires et ses prévisions. Ce service les lit <strong>en une requête par nature pour toute la liste</strong>
 * et les dépose dans la mémoire de lecture ouverte ({@link MemoLecture}), sous les clés que lisent ces points de lecture : le nombre
 * de requêtes de la liste ne dépend plus du nombre de procédures. Il y joint le responsable, la CAO et la cérémonie de chaque procédure.
 * Sans mémoire ouverte, rien n'est lu.
 * <p>
 * Une clé absente de la mémoire (ligne du plan remplacée dans la filiation, par exemple) est lue à la demande, comme avant : le
 * préchargement n'est qu'une économie, jamais une condition.
 */
@Service
@Transactional(readOnly = true)
public class PrechargementFiches {

    private final FicheMarcheRepository fiches;
    private final FicheMarcheValeurRepository valeurs;
    private final FicheArticleRepository articles;
    private final FicheCaracteristiqueRepository caracteristiques;
    private final FichePieceRepository pieces;
    private final DocumentFicheMarcheRepository documents;
    private final DossierRepository dossiers;
    private final DossierMecRepository dmcs;
    private final MarcheRepository marches;
    private final LotRepository lots;
    private final ServiceBeneficiaireRepository beneficiaires;
    private final MarchePrevisionRepository previsions;
    private final cnm.prs.repository.ResponsableProcedureRepository responsables;
    private final cnm.prs.repository.CaoMembreRepository membres;
    private final cnm.prs.repository.CeremonieClesRepository ceremonies;

    public PrechargementFiches(FicheMarcheRepository fiches, FicheMarcheValeurRepository valeurs, FicheArticleRepository articles,
            FicheCaracteristiqueRepository caracteristiques, FichePieceRepository pieces, DocumentFicheMarcheRepository documents,
            DossierRepository dossiers, DossierMecRepository dmcs, MarcheRepository marches, LotRepository lots,
            ServiceBeneficiaireRepository beneficiaires, MarchePrevisionRepository previsions,
            cnm.prs.repository.ResponsableProcedureRepository responsables, cnm.prs.repository.CaoMembreRepository membres,
            cnm.prs.repository.CeremonieClesRepository ceremonies) {
        this.fiches = fiches;
        this.valeurs = valeurs;
        this.articles = articles;
        this.caracteristiques = caracteristiques;
        this.pieces = pieces;
        this.documents = documents;
        this.dossiers = dossiers;
        this.dmcs = dmcs;
        this.marches = marches;
        this.lots = lots;
        this.beneficiaires = beneficiaires;
        this.previsions = previsions;
        this.responsables = responsables;
        this.membres = membres;
        this.ceremonies = ceremonies;
    }

    /**
     * Précharge l'état des fiches de ces DMC. {@code versionsParDmc} : les versions déjà lues par l'appelant (toutes, dans n'importe
     * quel ordre) ; {@code null} pour les lire ici.
     */
    public void precharger(Collection<Long> idDmcs, Map<Long, List<FicheMarche>> versionsParDmc) {
        if (!MemoLecture.ouverte() || idDmcs == null || idDmcs.isEmpty()) {
            return;
        }
        Map<Long, List<FicheMarche>> parDmc = versionsParDmc != null ? versionsParDmc
                : fiches.findByIdDmcIn(idDmcs).stream().collect(Collectors.groupingBy(FicheMarche::getIdDmc));
        // Les versions et la dernière de chaque DMC.
        List<Integer> idFiches = new ArrayList<>();
        Map<Long, List<FicheMarche>> versions = new HashMap<>();
        for (Long id : idDmcs) {
            List<FicheMarche> v = parDmc.getOrDefault(id, List.of()).stream().sorted(Comparator.comparing(FicheMarche::getNumeroVersion))
                    .toList();
            versions.put(id, v);
            MemoLecture.poser("versions:" + id, v);
            MemoLecture.poser("ficheDerniere:" + id, v.isEmpty() ? Optional.empty() : Optional.of(v.get(v.size() - 1)));
            v.stream().map(FicheMarche::getIdFiche).filter(Objects::nonNull).forEach(idFiches::add);
        }
        prechargerVersions(idFiches);
        prechargerAvis(versions, idFiches);
        // Le dossier né de chaque fiche.
        Map<Long, Integer> dossierParDmc = new HashMap<>();
        dossiers.findIdsDossierParDmc(idDmcs).forEach(r -> dossierParDmc.put((Long) r[0], (Integer) r[1]));
        idDmcs.forEach(id -> MemoLecture.poser("idDossierDmc:" + id, Optional.ofNullable(dossierParDmc.get(id))));
        // Le plan : la ligne de chaque DMC (gardée dans le contexte de persistance pour les lectures par identifiant), ses lots,
        // bénéficiaires et prévisions.
        List<Integer> idDetails = dmcs.findAllById(idDmcs).stream().map(DossierMec::getIdDetail).filter(Objects::nonNull).distinct().toList();
        prechargerPlan(idDetails);
        prechargerProcedure(idDmcs);
    }

    /** Le responsable, les membres de la CAO et la cérémonie de chaque DMC (clés de ParametresInternesService et CeremonieService). */
    private void prechargerProcedure(Collection<Long> idDmcs) {
        Map<Long, cnm.prs.entity.ResponsableProcedure> r = new HashMap<>();
        responsables.findByIdDmcInAndDateRetraitIsNullOrderByIdAsc(idDmcs).forEach(x -> r.putIfAbsent(x.getIdDmc(), x));
        Map<Long, List<cnm.prs.entity.CaoMembre>> m = membres.findByIdDmcInOrderByIdDmcAscRangAscIdMembreAsc(idDmcs).stream()
                .collect(Collectors.groupingBy(cnm.prs.entity.CaoMembre::getIdDmc));
        Map<Long, cnm.prs.entity.CeremonieCles> c = new HashMap<>();
        ceremonies.findAllById(idDmcs).forEach(x -> c.put(x.getIdDmc(), x));
        for (Long id : idDmcs) {
            MemoLecture.poser(ParametresInternesService.CLE_RESPONSABLE + id, Optional.ofNullable(r.get(id)));
            MemoLecture.poser(ParametresInternesService.CLE_MEMBRES_CAO + id, m.getOrDefault(id, List.of()));
            MemoLecture.poser("ceremonie:" + id, Optional.ofNullable(c.get(id)));
        }
    }

    private void prechargerVersions(List<Integer> idFiches) {
        if (idFiches.isEmpty()) {
            return;
        }
        Map<Integer, List<cnm.prs.entity.FicheMarcheValeur>> v = valeurs.findByIdFicheIn(idFiches).stream()
                .collect(Collectors.groupingBy(cnm.prs.entity.FicheMarcheValeur::getIdFiche));
        Map<Integer, List<FicheArticle>> a = articles.findParFiches(idFiches).stream()
                .collect(Collectors.groupingBy(FicheArticle::getIdFiche, LinkedHashMap::new, Collectors.toList()));
        Map<Integer, Integer> ficheDeLArticle = new HashMap<>();
        a.values().forEach(l -> l.forEach(x -> ficheDeLArticle.put(x.getIdArticle(), x.getIdFiche())));
        Map<Integer, List<cnm.prs.entity.FicheCaracteristique>> c = ficheDeLArticle.isEmpty() ? Map.of()
                : caracteristiques.findByIdArticleInOrderByIdArticleAscOrdreAsc(ficheDeLArticle.keySet()).stream()
                        .collect(Collectors.groupingBy(x -> ficheDeLArticle.get(x.getIdArticle())));
        Map<Integer, List<cnm.prs.entity.FichePiece>> p = pieces.findByIdFicheInOrderByIdFicheAscOrdreAsc(idFiches).stream()
                .collect(Collectors.groupingBy(cnm.prs.entity.FichePiece::getIdFiche));
        for (Integer id : idFiches) {
            MemoLecture.poser("valeurs:" + id, v.getOrDefault(id, List.of()));
            MemoLecture.poser("articles:" + id, a.getOrDefault(id, List.of()));
            // Même ordre que la lecture unitaire : par article (identifiant croissant), puis par ordre.
            MemoLecture.poser("caracteristiques:" + id, c.getOrDefault(id, List.of()));
            MemoLecture.poser("pieces:" + id, p.getOrDefault(id, List.of()));
        }
    }

    /**
     * Les avis et lettres imprimés de chaque DMC, sous la clé de {@code DocumentsFicheMarcheService.listerAvis} : les identifiants de
     * ses versions dans l'ordre croissant des numéros (celui de {@code FicheMarcheRepository.versions}).
     */
    private void prechargerAvis(Map<Long, List<FicheMarche>> versions, List<Integer> idFiches) {
        if (idFiches.isEmpty()) {
            return;
        }
        List<DocumentFicheMarche> tous = documents.findByIdFicheInAndTypeInOrderByIdDocumentDesc(idFiches,
                DocumentsFicheMarcheService.TYPES_PUBLICATION);
        versions.forEach((idDmc, v) -> {
            if (v.isEmpty()) {
                return;
            }
            Map<Integer, Integer> numeros = new LinkedHashMap<>();
            v.forEach(f -> numeros.put(f.getIdFiche(), f.getNumeroVersion()));
            MemoLecture.poser("avis:" + numeros.keySet(), tous.stream().filter(d -> numeros.containsKey(d.getIdFiche())).toList());
        });
    }

    private void prechargerPlan(List<Integer> idDetails) {
        if (idDetails.isEmpty()) {
            return;
        }
        Map<Integer, Integer> dossierDeLaLigne = new HashMap<>();
        for (Marche m : marches.findAllById(idDetails)) {
            dossierDeLaLigne.put(m.getIdDetail(), m.getIdDossier());
        }
        Map<Integer, List<cnm.prs.entity.Lot>> l = lots.findByIdDetailIn(idDetails).stream()
                .collect(Collectors.groupingBy(cnm.prs.entity.Lot::getIdDetail));
        Map<Integer, List<cnm.prs.entity.ServiceBeneficiaire>> b = beneficiaires.findByIdDetailIn(idDetails).stream()
                .collect(Collectors.groupingBy(cnm.prs.entity.ServiceBeneficiaire::getIdDetail));
        Map<Integer, List<cnm.prs.entity.MarchePrevision>> p = previsions.findByMarchesOrdonnes(idDetails).stream()
                .collect(Collectors.groupingBy(cnm.prs.entity.MarchePrevision::getIdDetail));
        for (Integer id : idDetails) {
            if (dossierDeLaLigne.containsKey(id)) {
                MemoLecture.poser("idDossierDetail:" + id, Optional.ofNullable(dossierDeLaLigne.get(id)));
            }
            MemoLecture.poser("lots:" + id, l.getOrDefault(id, List.of()));
            MemoLecture.poser("beneficiaires:" + id, b.getOrDefault(id, List.of()));
            MemoLecture.poser("previsions:" + id, p.getOrDefault(id, List.of()));
        }
    }
}
