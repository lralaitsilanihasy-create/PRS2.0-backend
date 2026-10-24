package cnm.prs.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.dto.ControleResultatDto;
import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.SeanceDto;
import cnm.prs.entity.ControleResultat;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Examen;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ControleResultatRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

/**
 * ⚠️ R5 (manuel révisé pour l'application, lot 1, §B6 ; V105, 2026-10-13) — le <strong>socle des contrôles automatiques</strong> et
 * de leurs résultats :
 * <ul>
 *   <li>à la soumission (et à chaque resoumission), chaque règle ({@link ControleAutomatique}) qui concerne le dossier est évaluée ;
 *   une règle <strong>bloquante</strong> non conforme refuse la soumission (400 {@code CONTROLES_BLOQUANTS}, la liste et les valeurs
 *   dans {@code details.controles}) ; sinon tous les résultats sont enregistrés sous le numéro de soumission ;</li>
 *   <li>les profils CNM et la PRMP lisent les résultats ; le Membre attributaire pose ses contrôles B (point de grille ou règle,
 *   résultat, observation obligatoire si non conforme), même garde que {@code t_examen_detail} ;</li>
 *   <li>une non-conformité <strong>non levée</strong> (le dernier résultat posé sur la règle, serveur ou contrôleur, reste non conforme)
 *   interdit l'avis « Favorable » sans réserve et figure à l'annexe du PV ;</li>
 *   <li>la synthèse PDF d'une soumission, pour l'archivage avec le PV.</li>
 * </ul>
 * Les règles lisent les écrans vivants (séance, évaluation) après vérification de leur existence, comme les instantanés (R4) : aucune
 * exception ne traverse la transaction de la soumission.
 */
@Service
public class ControlesService {

    private static final Logger log = LoggerFactory.getLogger(ControlesService.class);

    public static final String CODE_BLOQUANTS = "CONTROLES_BLOQUANTS";
    static final Set<String> RESULTATS = Set.of(ControleResultat.CONFORME, ControleResultat.NON_CONFORME, ControleResultat.NON_APPLICABLE);
    private static final DateTimeFormatter JOUR_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ControleResultatRepository repository;
    private final DossierRepository dossiers;
    private final ExamenRepository examens;
    private final ExamenGarde garde;
    private final PointsCtrlRepository points;
    private final PiecesExigees pieces;
    private final ObjectMapper mapper;
    private final GenerateurDocumentsFiche generateur;
    private final List<ControleAutomatique> regles;
    private final ObjectProvider<InstantaneService> instantanes;
    private final ObjectProvider<DossierService> dossierService;

    public ControlesService(ControleResultatRepository repository, DossierRepository dossiers, ExamenRepository examens, ExamenGarde garde,
            PointsCtrlRepository points, PiecesExigees pieces, ObjectMapper mapper, GenerateurDocumentsFiche generateur,
            List<ControleAutomatique> regles, ObjectProvider<InstantaneService> instantanes, ObjectProvider<DossierService> dossierService) {
        this.repository = repository;
        this.dossiers = dossiers;
        this.examens = examens;
        this.garde = garde;
        this.points = points;
        this.pieces = pieces;
        this.mapper = mapper;
        this.generateur = generateur;
        this.regles = regles;
        this.instantanes = instantanes;
        this.dossierService = dossierService;
    }

    /** Une règle évaluée sur le dossier. */
    public record Evalue(ControleAutomatique regle, ControleAutomatique.Resultat resultat) {
    }

    // ------------------------------------------------------------------ à la soumission

    /** Évalue les règles qui concernent le dossier. */
    public List<Evalue> evaluer(Dossier d) {
        InstantaneService is = instantanes.getObject();
        Long idDmc = is.idDmc(d);
        ControlesContexte ctx = new ControlesContexte(d, idDmc, pieces.enLigne(d),
                () -> (SeanceDto) is.contenu(InstantaneService.SEANCE, d, idDmc),
                () -> (EvaluationDto) is.contenu(InstantaneService.EVALUATION, d, idDmc));
        List<Evalue> out = new ArrayList<>();
        for (ControleAutomatique r : regles) {
            if (!r.concerne(d)) {
                continue;
            }
            ControleAutomatique.Resultat res = r.evaluer(d, ctx);
            if (res != null) {
                out.add(new Evalue(r, res));
            }
        }
        return out;
    }

    /**
     * Avant la soumission : évalue, et refuse (400 {@code CONTROLES_BLOQUANTS}) si une règle bloquante est non conforme ; les
     * non-conformités, leurs références et leurs valeurs sont dans {@code details.controles} : la PRMP sait quoi corriger.
     */
    public List<Evalue> controler(Dossier d) {
        List<Evalue> evalues = evaluer(d);
        List<Map<String, Object>> bloquants = new ArrayList<>();
        for (Evalue e : evalues) {
            if (e.regle().bloquant() && ControleResultat.NON_CONFORME.equals(e.resultat().resultat())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", e.regle().code());
                m.put("reference", e.regle().reference());
                m.put("libelle", e.regle().libelle());
                m.put("valeurs", e.resultat().valeurs());
                bloquants.add(m);
            }
        }
        if (!bloquants.isEmpty()) {
            throw new BadRequestException("Soumission refusée : " + bloquants.size() + " contrôle(s) bloquant(s) non conforme(s) — "
                    + String.join(", ", bloquants.stream().map(m -> m.get("reference") + " " + m.get("code")).toList()) + ".",
                    CODE_BLOQUANTS, Map.of("controles", bloquants));
        }
        return evalues;
    }

    /** Après la soumission : enregistre les évaluations sous le numéro de soumission suivant (1, puis 2…). */
    @Transactional
    public List<ControleResultat> enregistrer(Dossier d, List<Evalue> evalues) {
        if (evalues.isEmpty()) {
            return List.of();
        }
        Integer precedent = repository.maxNumero(d.getIdDossier());
        int numero = (precedent == null ? 0 : precedent) + 1;
        LocalDateTime maintenant = LocalDateTime.now(HorlogeRecette.HORLOGE);
        List<ControleResultat> out = new ArrayList<>();
        for (Evalue e : evalues) {
            out.add(repository.save(new ControleResultat(null, d.getIdDossier(), numero, e.regle().reference(), null, e.regle().code(),
                    e.regle().libelle(), json(e.resultat().valeurs()), e.resultat().resultat(), e.regle().bloquant(), ControleResultat.SERVEUR,
                    maintenant, null)));
        }
        log.info("[CONTROLES] dossier={} soumission n° {} : {} contrôle(s) automatique(s) enregistré(s) ({})", d.getIdDossier(), numero,
                out.size(), out.stream().map(c -> c.getRegle() + "=" + c.getResultat()).toList());
        return out;
    }

    // ------------------------------------------------------------------ lecture et contrôles B

    /** Les résultats d'une soumission (la dernière par défaut) ; même garde de lecture que le dossier. */
    @Transactional(readOnly = true)
    public List<ControleResultatDto> lister(Integer idDossier, Integer soumission) {
        exigerVisible(idDossier);
        Integer n = soumission != null ? soumission : repository.maxNumero(idDossier);
        if (n == null) {
            return List.of();
        }
        return repository.findByIdDossierAndNumeroSoumissionOrderByIdControleAsc(idDossier, n).stream().map(this::dto).toList();
    }

    /**
     * Un contrôle B du Membre attributaire de l'examen en cours (même garde que {@code t_examen_detail} : attributaire, examen
     * modifiable) : un point de grille ou une règle, un résultat, l'observation obligatoire s'il n'est pas conforme. 400 nominatif ;
     * 409 {@code EXAMEN_ABSENT} sans examen.
     */
    @Transactional
    public ControleResultatDto ajouter(Integer idDossier, ControleResultatDto.Request r) {
        if (!dossiers.existsById(idDossier)) {
            throw new ResourceNotFoundException("Dossier introuvable : " + idDossier);
        }
        Examen examen = examens.findParDossierRecents(idDossier).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("Aucun examen en cours sur le dossier " + idDossier + ".", "EXAMEN_ABSENT"));
        garde.exigerAttributaire(examen.getIdExamen());
        garde.exigerExamenModifiable(examen.getIdExamen());
        ControleResultatDto.Request c = r == null ? new ControleResultatDto.Request(null, null, null, null, null, null, null) : r;
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        String resultat = c.resultat() == null ? null : c.resultat().trim().toUpperCase(Locale.ROOT);
        if (resultat == null || !RESULTATS.contains(resultat)) {
            erreurs.add(new ErrorResponse.FieldError("resultat", "Le résultat est CONFORME, NON_CONFORME ou NON_APPLICABLE."));
        }
        PointsCtrl point = c.idPointCtrl() == null ? null : points.findById(c.idPointCtrl()).orElse(null);
        if (c.idPointCtrl() != null && point == null) {
            erreurs.add(new ErrorResponse.FieldError("idPointCtrl", "Point de contrôle inconnu : " + c.idPointCtrl() + "."));
        }
        String regle = c.regle() != null && !c.regle().isBlank() ? c.regle().trim() : point == null ? null : "POINT_" + point.getIdPointCtrl();
        if (regle == null) {
            erreurs.add(new ErrorResponse.FieldError("regle", "Un point de grille (idPointCtrl) ou une règle (regle) est attendu."));
        }
        String observation = c.observation() == null || c.observation().isBlank() ? null : c.observation().trim();
        if (ControleResultat.NON_CONFORME.equals(resultat) && observation == null) {
            erreurs.add(new ErrorResponse.FieldError("observation", "L'observation est obligatoire quand le contrôle n'est pas conforme."));
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        Integer precedent = repository.maxNumero(idDossier);
        String libelle = c.libelle() != null && !c.libelle().isBlank() ? c.libelle().trim()
                : point != null && point.getLibelPointCtrl() != null ? point.getLibelPointCtrl() : regle;
        ControleResultat saved = repository.save(new ControleResultat(null, idDossier, precedent == null ? 1 : precedent,
                c.reference() == null || c.reference().isBlank() ? null : c.reference().trim(), c.idPointCtrl(), regle, libelle, null, resultat,
                Boolean.TRUE.equals(c.bloquant()), CurrentUser.ref().or(CurrentUser::login).orElse("?"), LocalDateTime.now(HorlogeRecette.HORLOGE), observation));
        return dto(saved);
    }

    /**
     * Les non-conformités non levées de la dernière soumission : par règle, le dernier résultat posé (serveur ou contrôleur) est
     * encore {@code NON_CONFORME}. Un contrôleur lève une non-conformité automatique en posant CONFORME ou NON_APPLICABLE sur la même
     * règle, avec son observation.
     */
    @Transactional(readOnly = true)
    public List<ControleResultat> nonConformesNonLeves(Integer idDossier) {
        Integer n = idDossier == null ? null : repository.maxNumero(idDossier);
        if (n == null) {
            return List.of();
        }
        Map<String, ControleResultat> dernier = new LinkedHashMap<>();
        for (ControleResultat c : repository.findByIdDossierAndNumeroSoumissionOrderByIdControleAsc(idDossier, n)) {
            dernier.put(c.getRegle(), c);
        }
        return dernier.values().stream().filter(c -> ControleResultat.NON_CONFORME.equals(c.getResultat())).toList();
    }

    // ------------------------------------------------------------------ synthèse

    /** La synthèse PDF des contrôles d'une soumission (la dernière par défaut), pour l'archivage avec le PV. */
    @Transactional(readOnly = true)
    public byte[] pdf(Integer idDossier, Integer soumission) {
        exigerVisible(idDossier);
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        Integer n = soumission != null ? soumission : repository.maxNumero(idDossier);
        List<ControleResultat> liste = n == null ? List.of() : repository.findByIdDossierAndNumeroSoumissionOrderByIdControleAsc(idDossier, n);
        String reference = d.getRefeDossier() == null || d.getRefeDossier().isBlank() ? "dossier " + idDossier : d.getRefeDossier();
        List<DocumentLibre.Element> els = new ArrayList<>();
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "Synthèse des contrôles"));
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, reference + (n == null ? "" : " — soumission n° " + n)));
        els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        if (liste.isEmpty()) {
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, "Aucun contrôle enregistré."));
        } else {
            List<List<List<String>>> lignes = new ArrayList<>();
            lignes.add(List.of(List.of("Référence"), List.of("Contrôle"), List.of("Résultat"), List.of("Valeurs constatées / observation"),
                    List.of("Auteur"), List.of("Date")));
            for (ControleResultat c : liste) {
                String constat = c.getObservation() != null ? c.getObservation() : valeursTexte(c.getValeurs());
                lignes.add(List.of(List.of(c.getReference() == null ? "" : c.getReference()), List.of(c.getLibelle() == null ? c.getRegle() : c.getLibelle()),
                        List.of(c.getResultat() + (c.isBloquant() ? " (bloquant)" : "")), List.of(constat == null ? "" : constat),
                        List.of(ControleResultat.SERVEUR.equals(c.getAuteur()) ? "contrôle automatique" : c.getAuteur()),
                        List.of(c.getDateControle() == null ? "" : c.getDateControle().format(JOUR_HEURE))));
            }
            els.add(new DocumentLibre.Tableau(6, lignes));
            long nonConformes = liste.stream().filter(c -> ControleResultat.NON_CONFORME.equals(c.getResultat())).count();
            els.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, liste.size() + " contrôle(s), " + nonConformes + " non conforme(s)."));
        }
        DocumentLibre doc = new DocumentLibre("CONTROLES", null, els, "Synthèse des contrôles — " + reference
                + (n == null ? "" : " — soumission n° " + n));
        return generateur.generer(doc).stream().filter(f -> "pdf".equals(f.extension())).findFirst()
                .orElseThrow(() -> new IllegalStateException("La synthèse n'a pas été rendue en pdf.")).contenu();
    }

    /** Les valeurs constatées en une ligne (« clé : valeur ; … »), pour l'annexe du PV et la synthèse. */
    public String valeursTexte(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> m = mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
            return String.join(" ; ", m.entrySet().stream().map(e -> e.getKey() + " : " + e.getValue()).toList());
        } catch (RuntimeException e) {
            return json;
        }
    }

    // ------------------------------------------------------------------ outils

    private void exigerVisible(Integer idDossier) {
        if (!dossiers.existsById(idDossier)) {
            throw new ResourceNotFoundException("Dossier introuvable : " + idDossier);
        }
        dossierService.getObject().controlerVisibilite(idDossier);
    }

    ControleResultatDto dto(ControleResultat c) {
        Map<String, Object> valeurs = null;
        if (c.getValeurs() != null && !c.getValeurs().isBlank()) {
            try {
                valeurs = mapper.readValue(c.getValeurs(), new TypeReference<LinkedHashMap<String, Object>>() { });
            } catch (RuntimeException e) {
                valeurs = Map.of("brut", c.getValeurs());
            }
        }
        return new ControleResultatDto(c.getIdControle(), c.getNumeroSoumission(), c.getReference(), c.getIdPointCtrl(), c.getRegle(), c.getLibelle(),
                valeurs, c.getResultat(), c.isBloquant(), c.getAuteur(), c.getDateControle(), c.getObservation());
    }

    private String json(Map<String, Object> valeurs) {
        return valeurs == null ? null : mapper.writeValueAsString(valeurs);
    }
}
