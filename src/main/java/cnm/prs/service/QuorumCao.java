package cnm.prs.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.QuorumDto;
import cnm.prs.entity.CaoMembre;
import cnm.prs.entity.CaoRepresentant;
import cnm.prs.enums.TypeActeur;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.repository.CaoMembreRepository;
import cnm.prs.repository.CaoRepresentantRepository;
import cnm.prs.repository.ParametreInterneProcedureRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-10 (manuel révisé pour l'application, lot 1, tranche R1, §B2 ; V101 ; arbitrages du pilote du 10/10 : Q5 quorum paramétrable,
 * défaut majorité, président ou représentant requis ; arbitrages au backend : représentant désigné par séance, règle globale surchargeable
 * par procédure, empêchement refusé s'il rend le quorum inatteignable, majorité des membres désignés) — le quorum de la commission
 * d'appel d'offres :
 * <ul>
 *   <li>la <strong>règle</strong> : {@code MAJORITE} (plus de la moitié des membres désignés de qualité {@code MEMBRE}), {@code TOUS}, ou un
 *   nombre entier minimal ; lue dans les paramètres internes de la procédure, à défaut dans {@code CAO_QUORUM} (t_parametre) ;</li>
 *   <li>le <strong>président</strong>, ou le représentant qu'il a désigné pour le contexte, est toujours compté en plus du nombre ;</li>
 *   <li>il <strong>bloque</strong> la clôture de la séance d'ouverture et de la seconde séance (présents), la production du rapport
 *   d'évaluation et l'arrêt de la liste de l'AMI (signataires appelés), et chaque <strong>empêchement</strong> qui le rendrait
 *   inatteignable : 409 {@code QUORUM_NON_ATTEINT} avec {@code requis}, {@code obtenus}, {@code presidentSigne}, {@code regle}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class QuorumCao {

    public static final String CAO_QUORUM = "CAO_QUORUM";
    public static final String MAJORITE = "MAJORITE";
    public static final String TOUS = "TOUS";

    public static final String SEANCE = "SEANCE";
    public static final String SEANCE_FINANCIERE = "SEANCE_FINANCIERE";
    public static final String RAPPORT = "RAPPORT";
    public static final String AMI = "AMI";
    static final Set<String> CONTEXTES = Set.of(SEANCE, SEANCE_FINANCIERE, RAPPORT, AMI);

    public static final String PRESIDENT = "PRESIDENT";
    public static final String REPRESENTANT = "REPRESENTANT";
    public static final String MEMBRE = "MEMBRE";

    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final ParametreService parametres;
    private final ParametreInterneProcedureRepository internes;
    private final CaoMembreRepository membres;
    private final CaoRepresentantRepository representants;
    private final ParametresInternesService noms;
    private final Clock horloge;

    public QuorumCao(ParametreService parametres, ParametreInterneProcedureRepository internes, CaoMembreRepository membres,
            CaoRepresentantRepository representants, ParametresInternesService noms, Clock horloge) {
        this.parametres = parametres;
        this.internes = internes;
        this.membres = membres;
        this.representants = representants;
        this.noms = noms;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ la règle

    /** La règle : {@code MAJORITE}, {@code TOUS}, ou un nombre ({@code nombre} non nul). */
    public record Regle(String code, Integer nombre) {

        public String libelle() {
            return nombre != null ? nombre + " membre(s) au moins" : MAJORITE.equals(code) ? "majorité des membres désignés" : "tous les membres désignés";
        }

        public String texte() {
            return nombre != null ? String.valueOf(nombre) : code;
        }
    }

    /** Lit une règle : vide = {@code MAJORITE} ; 400 {@code QUORUM_REGLE_INVALIDE} sinon. */
    public static Regle parse(String texte) {
        String t = texte == null ? "" : texte.trim().toUpperCase(java.util.Locale.ROOT);
        if (t.isEmpty() || MAJORITE.equals(t)) {
            return new Regle(MAJORITE, null);
        }
        if (TOUS.equals(t)) {
            return new Regle(TOUS, null);
        }
        if (t.matches("\\d{1,3}") && Integer.parseInt(t) >= 1) {
            return new Regle("NOMBRE", Integer.parseInt(t));
        }
        throw new BadRequestException("Règle de quorum inconnue : « " + texte + " » (attendu : MAJORITE, TOUS, ou un nombre de membres, 1 au moins).",
                "QUORUM_REGLE_INVALIDE");
    }

    /** La règle d'une procédure : sa surcharge, sinon la règle globale. */
    public Regle regle(Long idDmc) {
        String propre = idDmc == null ? null : internes.findById(idDmc).map(p -> p.getCaoQuorum()).filter(v -> v != null && !v.isBlank()).orElse(null);
        return parse(propre != null ? propre : parametres.texte(CAO_QUORUM));
    }

    /** Le nombre requis pour {@code designes} membres : majorité = plus de la moitié ; un nombre est borné par l'effectif. */
    public static int requis(Regle r, int designes) {
        if (designes <= 0) {
            return 0;
        }
        if (r.nombre() != null) {
            return Math.max(1, Math.min(r.nombre(), designes));
        }
        return TOUS.equals(r.code()) ? designes : designes / 2 + 1;
    }

    // ------------------------------------------------------------------ l'évaluation

    /**
     * Le quorum constaté pour un contexte, {@code comptes} étant les membres comptés (présents à la clôture, ou signataires non
     * empêchés).
     */
    public QuorumDto evaluer(Long idDmc, String contexte, Collection<String> comptes) {
        List<CaoMembre> designes = designes(idDmc);
        Regle r = regle(idDmc);
        int requis = requis(r, designes.size());
        Set<String> c = comptes == null ? Set.of() : new java.util.LinkedHashSet<>(comptes);
        String president = president(designes);
        String representant = representant(idDmc, contexte).orElse(null);
        boolean presidentRequis = president != null;
        boolean presidentSigne = !presidentRequis || c.contains(president) || (representant != null && c.contains(representant));
        int obtenus = (int) c.stream().filter(k -> designes.stream().anyMatch(m -> k.equals(m.getIdCompte()))).count();
        return new QuorumDto(r.texte(), requis, obtenus, obtenus >= requis && presidentSigne, presidentRequis, presidentSigne, representant,
                representant == null ? null : noms.nomMembre(representant));
    }

    /** 409 {@code QUORUM_NON_ATTEINT} si les membres comptés n'atteignent pas le quorum (ou sans le président ni son représentant). */
    public void exigerAtteignable(Long idDmc, String contexte, Collection<String> comptes) {
        QuorumDto q = evaluer(idDmc, contexte, comptes);
        if (!q.atteint()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("regle", q.regle());
            details.put("requis", q.requis());
            details.put("obtenus", q.obtenus());
            details.put("presidentSigne", q.presidentSigne());
            throw new BusinessRuleException("Le quorum de la commission n'est pas atteint : " + q.obtenus() + " membre(s) pour " + q.requis()
                    + " requis (" + regle(idDmc).libelle() + ")" + (q.presidentSigne() ? "" : " ; le président, ou son représentant désigné, manque")
                    + ".", "QUORUM_NON_ATTEINT", null, details);
        }
    }

    /** La qualité d'un signataire pour ce contexte : {@code PRESIDENT}, {@code REPRESENTANT}, {@code MEMBRE}. */
    public String qualite(Long idDmc, String contexte, String im) {
        if (im != null && im.equals(president(designes(idDmc)))) {
            return PRESIDENT;
        }
        return representant(idDmc, contexte).filter(r -> r.equals(im)).isPresent() ? REPRESENTANT : MEMBRE;
    }

    /** Le membre est-il le président de la CAO ? */
    public boolean estPresident(Long idDmc, String im) {
        return im != null && im.equals(president(designes(idDmc)));
    }

    public Optional<String> representant(Long idDmc, String contexte) {
        return idDmc == null || contexte == null ? Optional.empty() : representants.findByIdDmcAndContexte(idDmc, contexte).map(CaoRepresentant::getIm);
    }

    /** Une ligne pour un PV ou un rapport : « Quorum : … ». */
    public String ligne(QuorumDto q, Regle r) {
        return "Quorum de la commission : " + r.libelle() + " — " + q.requis() + " requis, " + q.obtenus() + " obtenu(s) : "
                + (q.atteint() ? "atteint" : "non atteint") + (!q.presidentRequis() ? "" : q.presidentSigne()
                        ? (q.representant() != null ? " ; président représenté par " + q.representantNom() : " ; président compté")
                        : " ; président ou représentant manquant") + ".";
    }

    // ------------------------------------------------------------------ le représentant du président

    public record RepresentantRequest(String contexte, String im) {
    }

    public record Representant(Long idDmc, String contexte, String im, String nom, LocalDateTime designeLe, String designePar) {
    }

    /**
     * Le président désigne (ou retire, {@code im} nul) son représentant pour un contexte : 403 hors président ; 400
     * {@code CONTEXTE_INVALIDE}, {@code REPRESENTANT_INVALIDE} (ni membre de la CAO, ou le président lui-même).
     */
    @Transactional
    public Representant designerRepresentant(Long idDmc, RepresentantRequest r) {
        String k = CurrentUser.ref().orElse(null);
        List<CaoMembre> designes = designes(idDmc);
        String president = president(designes);
        if (k == null || !TypeActeur.MEMBRE_CAO.name().equals(CurrentUser.acteurType().orElse(null)) || !k.equals(president)) {
            throw new AccessDeniedException("Le représentant du président se désigne par le président de la commission.");
        }
        String contexte = r == null || r.contexte() == null ? null : r.contexte().trim().toUpperCase(java.util.Locale.ROOT);
        if (contexte == null || !CONTEXTES.contains(contexte)) {
            throw new BadRequestException("Contexte inconnu : « " + (r == null ? null : r.contexte()) + " » (SEANCE, SEANCE_FINANCIERE, RAPPORT, AMI).",
                    "CONTEXTE_INVALIDE");
        }
        CaoRepresentant existant = representants.findByIdDmcAndContexte(idDmc, contexte).orElse(null);
        String im = r.im() == null || r.im().isBlank() ? null : r.im().trim();
        if (im == null) {
            if (existant != null) {
                representants.delete(existant);
            }
            return new Representant(idDmc, contexte, null, null, null, null);
        }
        if (im.equals(president) || designes.stream().noneMatch(m -> im.equals(m.getIdCompte()))) {
            throw new BadRequestException("Le représentant est un membre de la commission autre que le président.", "REPRESENTANT_INVALIDE");
        }
        CaoRepresentant x = existant != null ? existant : new CaoRepresentant();
        x.setIdDmc(idDmc);
        x.setContexte(contexte);
        x.setIm(im);
        x.setDesigneLe(LocalDateTime.now(horloge).withNano(0));
        x.setDesignePar(noms.nomMembre(k));
        representants.save(x);
        return new Representant(idDmc, contexte, im, noms.nomMembre(im), x.getDesigneLe(), x.getDesignePar());
    }

    // ------------------------------------------------------------------ la « séance de validation » (§B1)

    /** Une signature ou un empêchement, tel que le rapport le relate. */
    public record SignatureVue(String im, String nom, String qualite, LocalDateTime date, boolean empechement, String motif, String constatePar,
            String observation) {
    }

    /**
     * ⚠️ Q4 — la section « Séance de validation » d'un rapport (qui vaut PV de validation) : date et heure de la validation, membres
     * ayant validé, empêchements, exclus pour conflit d'intérêts, quorum appliqué, observations de désaccord.
     */
    public List<DocumentLibre.Element> sectionSeanceDeValidation(String titre, Long idDmc, QuorumDto q, LocalDateTime valideLe,
            List<SignatureVue> signatures, List<String> exclusConflit) {
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, titre));
        el.add(para(valideLe == null ? "Validation en cours : les signatures des membres appelés sont attendues."
                : "Rapport validé par la commission le " + valideLe.format(HORODATAGE) + " (dernière signature électronique) : il vaut procès-verbal "
                        + "de validation."));
        List<String> valides = signatures.stream().filter(s -> !s.empechement()).map(s -> s.nom() + " (" + qualiteLisible(s.qualite()) + "), le "
                + s.date().format(HORODATAGE)).toList();
        el.add(para("Membres ayant validé : " + (valides.isEmpty() ? "aucun pour l'instant" : String.join(" ; ", valides)) + "."));
        List<String> empeches = signatures.stream().filter(SignatureVue::empechement).map(s -> s.nom() + " — " + s.motif()
                + (s.constatePar() == null ? "" : " (constaté par " + s.constatePar() + " le " + s.date().format(HORODATAGE) + ")")).toList();
        if (!empeches.isEmpty()) {
            el.add(para("Empêchements constatés : " + String.join(" ; ", empeches) + "."));
        }
        if (exclusConflit != null && !exclusConflit.isEmpty()) {
            el.add(para("Membres exclus de la signature pour conflit d'intérêts déclaré : " + String.join(", ", exclusConflit) + "."));
        }
        el.add(para(ligne(q, regle(idDmc))));
        List<String> observations = signatures.stream().filter(s -> !s.empechement() && s.observation() != null && !s.observation().isBlank())
                .map(s -> s.nom() + " : " + s.observation().trim()).toList();
        el.add(para("Observations de désaccord portées à la signature : " + (observations.isEmpty() ? "aucune" : String.join(" ; ", observations)) + "."));
        return el;
    }

    public static String qualiteLisible(String qualite) {
        return PRESIDENT.equals(qualite) ? "Président" : REPRESENTANT.equals(qualite) ? "représentant du Président" : "Membre";
    }

    // ------------------------------------------------------------------ outils

    private List<CaoMembre> designes(Long idDmc) {
        return idDmc == null ? List.of() : membres.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).stream()
                .filter(m -> m.estMembre() && m.getIdCompte() != null).toList();
    }

    private static String president(List<CaoMembre> designes) {
        return designes.stream().filter(m -> Boolean.TRUE.equals(m.getPresident())).map(CaoMembre::getIdCompte).findFirst().orElse(null);
    }

    private static DocumentLibre.Paragraphe para(String t) {
        return new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t);
    }
}
