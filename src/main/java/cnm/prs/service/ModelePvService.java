package cnm.prs.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.ModelePvDto;
import cnm.prs.entity.ChampSaisiModelePv;
import cnm.prs.entity.ConditionModelePv;
import cnm.prs.entity.JournalModelePv;
import cnm.prs.entity.ModelePv;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.RattachementModelePv;
import cnm.prs.entity.SousTypeDossier;
import cnm.prs.entity.VersionModelePv;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.ChampSaisiModelePvRepository;
import cnm.prs.repository.ConditionModelePvRepository;
import cnm.prs.repository.JournalModelePvRepository;
import cnm.prs.repository.ModelePvRepository;
import cnm.prs.repository.RattachementModelePvRepository;
import cnm.prs.repository.SousTypeDossierRepository;
import cnm.prs.repository.VersionModelePvRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », lot G1 ; V138) — le <strong>générateur de modèles de PV</strong>, côté
 * Administrateur : créer un modèle, déposer une version {@code .docx} (contrôlée à chaque dépôt et à chaque déclaration), déclarer
 * ses conditions et ses champs saisis, l'essayer sur un dossier réel sans rien enregistrer, la mettre en vigueur avec un motif,
 * rattacher le modèle aux sous-types qu'il sert, le dupliquer, l'archiver. Tout est journalisé.
 *
 * <p>Ce lot ne produit encore aucun PV : la production par la version en vigueur (B6) est le lot G2. Les 14 modèles du plan restent
 * servis par {@link PvDocumentGenerator}.</p>
 *
 * <p>⚠️ 2026-10-21 (demande front « modele-pv-page », lots P1-P2 ; V139) — le support change : une version est une
 * <strong>page</strong> construite dans l'application ({@link ModelePvPage}), enregistrée en document JSON, contrôlée sur ses nœuds
 * et rendue en PDF. Le dépôt d'un Word est retiré ; les versions Word déjà déposées restent en consultation (fichier, aperçu) et ne
 * se mettent plus en vigueur ({@code SUPPORT_ABANDONNE}).</p>
 */
@Service
@Transactional
public class ModelePvService {

    static final Set<String> NATURES = Set.of("PV", "LETTRE_RENVOI");
    static final Set<String> TYPES_CHAMP = Set.of("TEXTE", "TEXTE_LONG", "LISTE", "CASE", "DATE", "NOMBRE", "PIECE");
    private static final Pattern NOM_CONDITION = Pattern.compile("[A-Za-z0-9_-]{1,60}");
    private static final Pattern CODE_CHAMP = Pattern.compile("[A-Za-z0-9_]{1,60}");
    private static final Pattern CODE_MODELE = Pattern.compile("[A-Za-z0-9_.-]{1,50}");
    private static final Pattern TERME = Pattern.compile("^\\s*(" + ConditionsModele.CLE + ")\\s*(?:!?=|contient\\s|renseigne|vide)");
    private static final Map<String, String> SECTIONS = Map.of("PV.REFERENCE", "en-tête et identification",
            "PV.MEMBRE", "signatures", "PV.AVIS", "avis", "DOSSIER.REFERENCE", "identification",
            "LETTRE.CORPS", "corps de la lettre", "LETTRE.SIGNATAIRE", "signature");

    private final ModelePvRepository modeles;
    private final VersionModelePvRepository versions;
    private final ConditionModelePvRepository conditions;
    private final ChampSaisiModelePvRepository champs;
    private final RattachementModelePvRepository rattachements;
    private final JournalModelePvRepository journal;
    private final SousTypeDossierRepository sousTypes;
    private final ContexteModelePvService contextes;
    private final PvDocumentGenerator convertisseur;
    private final ImagesModelePvService images;
    private final AuditLogService audit;
    private final ObjectMapper json;
    private final boolean essaiPdf;
    /** ⚠️ 2026-10-22 (« modeles-documents-marche », W1) — les natures dont la page se remet aussi en Word (complétée par un tiers). */
    static final Set<String> NATURES_WORD = Set.of("PROJET_MARCHE");

    public ModelePvService(ModelePvRepository modeles, VersionModelePvRepository versions, ConditionModelePvRepository conditions,
            ChampSaisiModelePvRepository champs, RattachementModelePvRepository rattachements, JournalModelePvRepository journal,
            SousTypeDossierRepository sousTypes, ContexteModelePvService contextes, PvDocumentGenerator convertisseur,
            ImagesModelePvService images, AuditLogService audit, ObjectMapper json, @Value("${app.modeles-pv.essai-pdf:true}") boolean essaiPdf) {
        this.modeles = modeles;
        this.versions = versions;
        this.conditions = conditions;
        this.champs = champs;
        this.rattachements = rattachements;
        this.journal = journal;
        this.sousTypes = sousTypes;
        this.contextes = contextes;
        this.convertisseur = convertisseur;
        this.images = images;
        this.audit = audit;
        this.json = json;
        this.essaiPdf = essaiPdf;
    }

    // ───────────────────────────────────────────── lectures ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ModelePvDto.Resume> lister() {
        List<ModelePvDto.Resume> out = new ArrayList<>();
        for (ModelePv m : modeles.findAllByOrderByLibelleAsc()) {
            List<VersionModelePv> vs = versions.findByIdModeleOrderByNumeroDesc(m.getIdModele());
            VersionModelePv enVigueur = vs.stream().filter(v -> VersionModelePv.EN_VIGUEUR.equals(v.getEtat())).findFirst().orElse(null);
            out.add(new ModelePvDto.Resume(m.getIdModele(), m.getCode(), m.getLibelle(), m.getNature(), m.getEtat(),
                    rattachements.findByIdModele(m.getIdModele()).stream().map(RattachementModelePv::getIdSousType).sorted().toList(),
                    courte(enVigueur), vs.isEmpty() ? null : courte(vs.get(0))));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ModelePvDto.Fiche lire(Long id) {
        return fiche(modele(id));
    }

    @Transactional(readOnly = true)
    public ModelePvDto.Version version(Long id, Integer numero) {
        return dto(version(modele(id), numero), true);
    }

    /** Le fichier d'une version Word ; une page n'en a pas (409 {@code VERSION_SANS_FICHIER}). */
    @Transactional(readOnly = true)
    public VersionModelePv fichier(Long id, Integer numero) {
        VersionModelePv v = version(modele(id), numero);
        if (VersionModelePv.PAGE.equals(v.getSupport())) {
            throw new BusinessRuleException("La version " + numero + " est une page : elle n'a pas de fichier. Son rendu se voit par "
                    + "l'aperçu PDF.", "VERSION_SANS_FICHIER");
        }
        return v;
    }

    /** ⚠️ 2026-10-21 (« modele-pv-page », §B1) — le schéma du document : nœuds, attributs, listes fermées, limites. */
    @Transactional(readOnly = true)
    public Map<String, Object> schema() {
        return ModelePvPage.schema();
    }

    /**
     * Le catalogue des variables (§B3), filtré par la famille du sous-type ({@code null} : tout) et, depuis « modele-lettre-renvoi »
     * (B1), par la nature : la lettre de renvoi a ses variables {@code LETTRE.*}, sans celles du PV ni ses listes.
     */
    @Transactional(readOnly = true)
    public ModelePvDto.Catalogue catalogue(String sousType, String nature) {
        String n = nature == null || nature.isBlank() ? null : nature.trim().toUpperCase(Locale.ROOT);
        if (n != null && !NATURES.contains(n)) {
            throw new BadRequestException("Nature inconnue : « " + nature + " » (PV, LETTRE_RENVOI).", "NATURE_INCONNUE");
        }
        String famille = null;
        if (sousType != null && !sousType.isBlank()) {
            famille = sousTypes.findById(sousType).map(SousTypeDossier::getIdTypeDossier)
                    .orElseThrow(() -> new BadRequestException("Sous-type inconnu : « " + sousType + " ».", "SOUS_TYPE_INCONNU"));
        }
        Map<String, List<ModelePvDto.Variable>> groupes = new LinkedHashMap<>();
        for (CatalogueModelesPv.Variable v : CatalogueModelesPv.pourFamille(famille).stream().filter(v -> CatalogueModelesPv.sert(v, n))
                .toList()) {
            groupes.computeIfAbsent(v.groupe(), g -> new ArrayList<>())
                    .add(new ModelePvDto.Variable(v.code(), v.libelle(), v.exemple(), v.liste()));
        }
        String f = famille;
        return new ModelePvDto.Catalogue(sousType, famille, n,
                groupes.entrySet().stream().map(e -> new ModelePvDto.Groupe(e.getKey(), e.getValue())).toList(),
                CatalogueModelesPv.LISTES.stream().filter(l -> !"LETTRE_RENVOI".equals(n))
                        .filter(l -> f == null || l.familles() == null || l.familles().contains(f))
                        .map(l -> new ModelePvDto.Liste(l.nom(), l.prefixe(), l.libelle())).toList(),
                CatalogueModelesPv.CLES.stream().map(c -> new ModelePvDto.Cle(c.cle(), c.libelle(), c.valeurs())).toList(),
                CatalogueModelesPv.FORMATS, CatalogueModelesPv.ALIAS);
    }

    /**
     * ⚠️ 2026-10-20 (demande front « modeles-pv-integres », §B1) — les modèles intégrés au JAR, lus là où le choix se fait
     * ({@link PvDocumentGenerator#modeles}, {@link LettreRenvoiDocumentGenerator#modeles},
     * {@link PvDocumentService#SOUS_TYPES_MODELES_INTEGRES}) ; seuls les fichiers présents sont servis. {@code enService} est vrai tant
     * qu'un sous-type au moins est encore produit par ces fichiers (⚠️ 2026-10-22, « releve-plan-lettre » : {@link #versionQuiProduit}).
     */
    @Transactional(readOnly = true)
    public List<ModelePvDto.Integre> integres() {
        return List.of(
                new ModelePvDto.Integre("PV-PLAN-INTEGRE", "PV d'examen du plan de passation", "PV",
                        PvDocumentService.SOUS_TYPES_MODELES_INTEGRES, presents(PvDocumentGenerator.modeles()),
                        "Un fichier par avis (FAV ; FAVR avec l'annexe des observations ; DEF), avec ou sans AGPM (sous-type PPM-AGPM), "
                                + "commission centrale ou régionale. Seul un dossier dont le PPM garde au moins une ligne reçoit un "
                                + "document ; l'avis NSP n'en a pas.",
                        PvDocumentService.SOUS_TYPES_MODELES_INTEGRES.stream().anyMatch(st -> versionQuiProduit(st, "PV").isEmpty())),
                new ModelePvDto.Integre("LETTRE-RENVOI-INTEGREE", "Lettre de renvoi", "LETTRE_RENVOI", null,
                        presents(LettreRenvoiDocumentGenerator.modeles()),
                        "Un fichier par commission, centrale ou régionale, pour toute lettre signée, quel que soit le sous-type.",
                        sousTypes.findAll().stream().anyMatch(s -> versionQuiProduit(s.getIdSousType(), "LETTRE_RENVOI").isEmpty())));
    }

    /**
     * ⚠️ 2026-10-22 (demande front « releve-plan-lettre », R1-R3) — <strong>la relève</strong> : la version qui produit le document
     * d'un sous-type pour une nature, à la place des fichiers intégrés. Il la faut rattachée (modèle actif), en vigueur, en page, et
     * dans le périmètre relevé : le PV du plan ({@link PvDocumentService#SOUS_TYPES_MODELES_INTEGRES}) et la lettre de renvoi de tout
     * sous-type. Lue à chaque production : un modèle archivé ou détaché rend la main aux fichiers intégrés (Q1), une mise en vigueur
     * la prend sans autre geste (Q2). Les autres PV n'ont toujours pas de document.
     */
    @Transactional(readOnly = true)
    public Optional<VersionModelePv> versionQuiProduit(String sousType, String nature) {
        if (sousType == null || !("LETTRE_RENVOI".equals(nature)
                || "PV".equals(nature) && PvDocumentService.SOUS_TYPES_MODELES_INTEGRES.contains(sousType))) {
            return Optional.empty();
        }
        return rattachements.findByIdSousTypeAndNature(sousType, nature)
                .flatMap(r -> modeles.findById(r.getIdModele()))
                .filter(m -> !ModelePv.ARCHIVE.equals(m.getEtat()))
                .flatMap(m -> versions.findByIdModeleAndEtat(m.getIdModele(), VersionModelePv.EN_VIGUEUR))
                .filter(v -> VersionModelePv.PAGE.equals(v.getSupport()));
    }

    /**
     * ⚠️ 2026-10-22 (« releve-plan-lettre », R1) — le PDF d'un PV produit par la page relevée, ou vide si le sous-type garde les
     * fichiers intégrés. Les règles d'éligibilité restent celles de {@link PvDocumentService#choisirModele} (l'appelant les a passées).
     * Une page qui ne se rend pas lève l'erreur : pas de repli silencieux sur l'intégré.
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> produirePv(PvExamen pv, String sousType) {
        return versionQuiProduit(sousType, "PV").map(v -> rendre(v.getDocument(), contextes.pourPv(pv), declarees(v)));
    }

    /** ⚠️ 2026-10-22 (« releve-plan-lettre », R2) — le PDF d'une lettre produit par la page relevée, ou vide (fichiers intégrés). */
    @Transactional(readOnly = true)
    public Optional<byte[]> produireLettre(Integer idDossier, Integer idLettre, String sousType) {
        return versionQuiProduit(sousType, "LETTRE_RENVOI")
                .map(v -> rendre(v.getDocument(), contextes.pourLettre(idDossier, idLettre, Map.of()), declarees(v)));
    }

    /** Les couples « sous-type|nature » relevés : comparés avant et après un changement pour le journal général (R3). */
    private Set<String> releves() {
        Set<String> out = new java.util.TreeSet<>();
        for (RattachementModelePv r : rattachements.findAll()) {
            if (versionQuiProduit(r.getIdSousType(), r.getNature()).isPresent()) {
                out.add(r.getIdSousType() + "|" + r.getNature());
            }
        }
        return out;
    }

    /**
     * ⚠️ 2026-10-22 (« releve-plan-lettre », R3) — une entrée au journal général par sous-type qui change de producteur :
     * {@code RELEVE_MODELE_INTEGRE} (la page prend la main) ou {@code RETOUR_MODELE_INTEGRE} (les fichiers intégrés la reprennent).
     */
    private void tracerReleve(ModelePv m, Set<String> avant, String cause) {
        Set<String> apres = releves();
        Set<String> tous = new java.util.TreeSet<>(avant);
        tous.addAll(apres);
        for (String cle : tous) {
            boolean a = avant.contains(cle);
            boolean b = apres.contains(cle);
            if (a == b) {
                continue;
            }
            String[] p = cle.split("\\|");
            String integre = "PV".equals(p[1]) ? "PV-PLAN-INTEGRE" : "LETTRE-RENVOI-INTEGREE";
            String detail = b
                    ? p[0] + " (" + p[1] + ") : produit désormais par " + m.getCode() + " v" + versionQuiProduit(p[0], p[1])
                            .map(VersionModelePv::getNumero).map(String::valueOf).orElse("?") + " à la place de " + integre
                    : p[0] + " (" + p[1] + ") : rendu aux fichiers intégrés " + integre + " (" + m.getCode() + ")";
            audit.enregistrerDetail(CurrentUser.ref().orElse(null), "modeles-pv", String.valueOf(m.getIdModele()),
                    b ? "RELEVE_MODELE_INTEGRE" : "RETOUR_MODELE_INTEGRE", p[0], detail + " — " + cause + " ; par "
                            + CurrentUser.login().orElse("?"), null);
        }
    }

    /**
     * ⚠️ 2026-10-20 (demande front « modeles-pv-integres », §B2) — pour chaque sous-type du référentiel et chaque nature, ce qui
     * produit son document. ⚠️ 2026-10-22 (« releve-plan-lettre », R3) — {@code GERE} quand une page en vigueur relève les fichiers
     * ({@link #versionQuiProduit}) ; un modèle rattaché hors du périmètre relevé (autres PV) ne produit rien et reste servi à part.
     */
    @Transactional(readOnly = true)
    public List<ModelePvDto.Couverture> couverture() {
        List<ModelePvDto.Integre> integres = integres();
        List<ModelePvDto.Couverture> out = new ArrayList<>();
        for (SousTypeDossier s : sousTypes.findAll(org.springframework.data.domain.Sort.by("idSousType"))) {
            for (String nature : List.of("PV", "LETTRE_RENVOI")) {
                ModelePvDto.Integre integre = integres.stream().filter(i -> i.nature().equals(nature)
                        && (i.sousTypes() == null || i.sousTypes().contains(s.getIdSousType()))).findFirst().orElse(null);
                ModelePvDto.ModeleRattache rattache = rattachements.findByIdSousTypeAndNature(s.getIdSousType(), nature)
                        .flatMap(r -> modeles.findById(r.getIdModele()))
                        .map(m -> new ModelePvDto.ModeleRattache(m.getIdModele(), m.getCode(),
                                versions.findByIdModeleAndEtat(m.getIdModele(), VersionModelePv.EN_VIGUEUR).map(VersionModelePv::getNumero)
                                        .orElse(null)))
                        .orElse(null);
                out.add(new ModelePvDto.Couverture(s.getIdSousType(), s.getLibelleSousType(), s.getIdTypeDossier(), nature,
                        versionQuiProduit(s.getIdSousType(), nature).isPresent() ? "GERE"
                                : integre != null && integre.enService() ? "INTEGRE" : "AUCUN",
                        integre == null ? null : integre.code(), rattache));
            }
        }
        return out;
    }

    private List<String> presents(List<String> chemins) {
        return chemins.stream().filter(c -> getClass().getResource(c) != null).map(c -> c.substring(c.lastIndexOf('/') + 1)).toList();
    }

    // ───────────────────────────────────────────── gestes ─────────────────────────────────────────────

    public ModelePvDto.Fiche creer(ModelePvDto.CreationRequest r) {
        String nature = r.nature().trim().toUpperCase(Locale.ROOT);
        if (!NATURES.contains(nature)) {
            throw new BadRequestException("Nature inconnue : « " + r.nature() + " » (PV, LETTRE_RENVOI).", "NATURE_INCONNUE");
        }
        ModelePv m = nouveau(r.code(), r.libelle(), nature);
        tracer(m, null, "CREE", m.getLibelle());
        return fiche(m);
    }

    /**
     * Copie le modèle et sa version en vigueur (à défaut sa dernière version), conditions et champs compris, en brouillon.
     * ⚠️ 2026-10-21 (« modele-pv-page », §B6) — une version Word ne se copie plus (elle ne pourrait pas être mise en vigueur) : la
     * copie part alors sans version, le journal le dit.
     */
    public ModelePvDto.Fiche dupliquer(Long id, ModelePvDto.DuplicationRequest r) {
        ModelePv source = modele(id);
        String code = r == null || r.code() == null || r.code().isBlank() ? codeLibre(source.getCode() + "-COPIE") : r.code();
        String libelle = r == null || r.libelle() == null || r.libelle().isBlank() ? source.getLibelle() + " (copie)" : r.libelle();
        ModelePv copie = nouveau(code, libelle, source.getNature());
        List<VersionModelePv> vs = versions.findByIdModeleOrderByNumeroDesc(id);
        VersionModelePv base = vs.stream().filter(v -> VersionModelePv.EN_VIGUEUR.equals(v.getEtat())).findFirst()
                .orElse(vs.isEmpty() ? null : vs.get(0));
        boolean word = base != null && !VersionModelePv.PAGE.equals(base.getSupport());
        if (word) {
            base = null;
        }
        if (base != null) {
            VersionModelePv v = enregistrerPage(copie, base.getDocument());
            int ordre = 0;
            for (ConditionModelePv c : conditions.findByIdVersionOrderByOrdreAsc(base.getIdVersion())) {
                conditions.save(condition(v, c.getNom(), c.getExpression(), c.getLibelle(), ordre++));
            }
            ordre = 0;
            for (ChampSaisiModelePv c : champs.findByIdVersionOrderByOrdreAsc(base.getIdVersion())) {
                ChampSaisiModelePv n = new ChampSaisiModelePv();
                n.setIdVersion(v.getIdVersion());
                n.setCode(c.getCode());
                n.setLibelle(c.getLibelle());
                n.setType(c.getType());
                n.setOptions(c.getOptions());
                n.setObligatoire(c.getObligatoire());
                n.setAide(c.getAide());
                n.setVisibleSi(c.getVisibleSi());
                n.setOrdre(ordre++);
                champs.save(n);
            }
            controlerEtEnregistrer(copie, v);
        }
        tracer(copie, null, "DUPLIQUE", "copie de " + source.getCode() + (base != null ? ", version " + base.getNumero()
                : word ? ", sans version (la version Word n'est pas copiée : support abandonné)" : ""));
        tracer(source, null, "DUPLIQUE", "copié en " + copie.getCode());
        return fiche(copie);
    }

    /**
     * ⚠️ 2026-10-21 (« modele-pv-page », §B2) — le dépôt d'un Word est retiré : l'Administrateur ne dépose plus de fichier, tout
     * modèle géré est une page (409 {@code SUPPORT_ABANDONNE}).
     */
    public ModelePvDto.Version deposer(Long id, MultipartFile fichier) {
        actif(id);
        throw new BusinessRuleException("Le dépôt d'un Word est retiré : un modèle de PV se construit désormais dans l'application "
                + "(POST …/versions avec le document de la page, ou POST …/versions/depart).", "SUPPORT_ABANDONNE");
    }

    /**
     * ⚠️ 2026-10-21 (« modele-pv-page », §B2) — enregistre une nouvelle version <strong>page</strong> : le document est validé (400
     * {@code DOCUMENT_INVALIDE}, {@code details} chemin → raison), puis contrôlé ; la version est un brouillon avec son rapport.
     */
    public ModelePvDto.Version enregistrerVersion(Long id, ModelePvDto.DocumentRequest r) {
        ModelePv m = actif(id);
        String document = documentValide(r);
        VersionModelePv v = enregistrerPage(m, document);
        if (r.motif() != null && !r.motif().isBlank()) {
            v.setMotif(r.motif().trim());
        }
        ModelePvDto.Rapport rapport = controlerEtEnregistrer(m, v);
        tracer(m, v.getNumero(), "ENREGISTREE", "page de " + v.getTaille() + " octets"
                + (rapport.bloquant() ? ", contrôle bloquant (" + rapport.nbBloquants() + ")" : ", contrôle sans blocage"));
        return dto(v, true);
    }

    /**
     * ⚠️ 2026-10-21 (« modele-pv-page », §B2, Q4) — remplace le document d'un <strong>brouillon</strong> (409 {@code VERSION_FIGEE}
     * sinon ; 409 {@code SUPPORT_ABANDONNE} sur une version Word) ; le rapport est recalculé. L'éditeur enregistre ainsi sans
     * multiplier les versions.
     */
    public ModelePvDto.Version modifierDocument(Long id, Integer numero, ModelePvDto.DocumentRequest r) {
        ModelePv m = actif(id);
        VersionModelePv v = brouillon(m, numero);
        if (!VersionModelePv.PAGE.equals(v.getSupport())) {
            throw new BusinessRuleException("La version " + numero + " est un Word, gardé en consultation : enregistrez une nouvelle "
                    + "version page.", "SUPPORT_ABANDONNE");
        }
        String document = documentValide(r);
        poserDocument(v, document);
        if (r.motif() != null && !r.motif().isBlank()) {
            v.setMotif(r.motif().trim());
        }
        ModelePvDto.Rapport rapport = controlerEtEnregistrer(m, v);
        tracer(m, numero, "DOCUMENT_MODIFIE", "page de " + v.getTaille() + " octets"
                + (rapport.bloquant() ? ", contrôle bloquant (" + rapport.nbBloquants() + ")" : ", contrôle sans blocage"));
        return dto(v, true);
    }

    /** Le document validé, sérialisé : 400 {@code DOCUMENT_INVALIDE} s'il manque, dépasse 2 Mo ou sort du schéma. */
    private String documentValide(ModelePvDto.DocumentRequest r) {
        if (r == null || r.document() == null || r.document().isNull() || r.document().isMissingNode()) {
            throw new BadRequestException("Le document de la page est obligatoire.", "DOCUMENT_INVALIDE",
                    Map.<String, Object>of("document", "document : obligatoire"));
        }
        String texte = json.writeValueAsString(r.document());
        if (texte.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > ModelePvPage.TAILLE_MAX) {
            throw new BadRequestException("Le document dépasse 2 Mo (les images sont à part).", "DOCUMENT_INVALIDE",
                    Map.<String, Object>of("document", "document : plus de 2 Mo"));
        }
        try {
            ModelePvPage.valider(r.document(), images::existe);
        } catch (ModelePvPage.Invalide e) {
            throw new BadRequestException("Document invalide : " + e.details().values().iterator().next()
                    + (e.details().size() > 1 ? " (et " + (e.details().size() - 1) + " autre(s))" : ""), "DOCUMENT_INVALIDE", e.details());
        }
        return texte;
    }

    public ModelePvDto.Version declarerConditions(Long id, Integer numero, List<ModelePvDto.Condition> liste) {
        ModelePv m = actif(id);
        VersionModelePv v = brouillon(m, numero);
        Map<String, Object> erreurs = new LinkedHashMap<>();
        Set<String> noms = new LinkedHashSet<>();
        for (ModelePvDto.Condition c : liste == null ? List.<ModelePvDto.Condition>of() : liste) {
            String nom = c.nom() == null ? "" : c.nom().trim();
            if (!NOM_CONDITION.matcher(nom).matches()) {
                erreurs.put(nom, "nom invalide (lettres, chiffres, _ ou -)");
            } else if (!noms.add(nom)) {
                erreurs.put(nom, "déclarée deux fois");
            } else if (!ConditionsModele.lisible(c.expression())) {
                erreurs.put(nom, "expression illisible : « " + c.expression() + " »");
            } else {
                List<String> inconnues = cles(c.expression()).stream().filter(k -> !CatalogueModelesPv.cleConnue(k)).toList();
                if (!inconnues.isEmpty()) {
                    erreurs.put(nom, "clé(s) inconnue(s) : " + String.join(", ", inconnues));
                }
            }
        }
        if (!erreurs.isEmpty()) {
            throw new BadRequestException("Conditions refusées : " + erreurs, "CONDITION_INVALIDE", erreurs);
        }
        enregistrerConditions(v, liste);
        controlerEtEnregistrer(m, v);
        tracer(m, numero, "CONDITIONS", noms.isEmpty() ? "aucune" : String.join(", ", noms));
        return dto(v);
    }

    public ModelePvDto.Version declarerChamps(Long id, Integer numero, List<ModelePvDto.Champ> liste) {
        ModelePv m = actif(id);
        VersionModelePv v = brouillon(m, numero);
        Set<String> declarees = new LinkedHashSet<>();
        conditions.findByIdVersionOrderByOrdreAsc(v.getIdVersion()).forEach(c -> declarees.add(c.getNom()));
        Map<String, Object> erreurs = new LinkedHashMap<>();
        Set<String> codes = new LinkedHashSet<>();
        for (ModelePvDto.Champ c : liste == null ? List.<ModelePvDto.Champ>of() : liste) {
            String code = c.code() == null ? "" : c.code().trim();
            String type = c.type() == null ? "" : c.type().trim().toUpperCase(Locale.ROOT);
            if (!CODE_CHAMP.matcher(code).matches()) {
                erreurs.put(code, "code invalide (lettres, chiffres ou _)");
            } else if (!codes.add(code)) {
                erreurs.put(code, "déclaré deux fois");
            } else if (!TYPES_CHAMP.contains(type)) {
                erreurs.put(code, "type inconnu : « " + c.type() + " » " + TYPES_CHAMP);
            } else if ("LISTE".equals(type) && (c.options() == null || c.options().stream().allMatch(o -> o == null || o.isBlank()))) {
                erreurs.put(code, "une liste a au moins un choix");
            } else if (c.visibleSi() != null && !c.visibleSi().isBlank() && !declarees.contains(c.visibleSi().trim())) {
                erreurs.put(code, "visibleSi : la condition « " + c.visibleSi() + " » n'est pas déclarée pour cette version");
            }
        }
        if (!erreurs.isEmpty()) {
            throw new BadRequestException("Champs refusés : " + erreurs, "CHAMP_INVALIDE", erreurs);
        }
        enregistrerChamps(v, liste);
        controlerEtEnregistrer(m, v);
        tracer(m, numero, "CHAMPS", codes.isEmpty() ? "aucun" : String.join(", ", codes));
        return dto(v);
    }

    /**
     * ⚠️ 2026-10-21 (demande front « modele-pv-depart ») — le <strong>modèle de départ</strong> : l'application assemble une version
     * en brouillon depuis le PV officiel du plan ({@link ModelePvDepart}), ses conditions et ses champs déclarés d'office ; rien
     * n'est mis en vigueur. La famille se lit sur les sous-types rattachés : 400 {@code SANS_SOUS_TYPE}, 409
     * {@code FAMILLES_MULTIPLES} (détail famille → sous-types), 409 {@code FAMILLE_SANS_DEPART} (famille sans départ connu ; ⚠️
     * 2026-10-22 : le plan a le sien, « modele-pv-plan-depart ») ; 400
     * {@code NATURE_SANS_DEPART} (lettre de renvoi), {@code PORTEE_INCONNUE} ; 409 {@code MODELE_ARCHIVE}. Permise même si le
     * modèle a déjà des versions : elle en ajoute une.
     */
    public ModelePvDto.Version depart(Long id, String portee) {
        ModelePv m = actif(id);
        String p = portee == null || portee.isBlank() ? ModelePvDepart.LES_DEUX : portee.trim().toLowerCase(Locale.ROOT);
        if (!ModelePvDepart.PORTEES.contains(p)) {
            throw new BadRequestException("Portée inconnue : « " + portee + " » " + ModelePvDepart.PORTEES + ".", "PORTEE_INCONNUE");
        }
        if ("LETTRE_RENVOI".equals(m.getNature())) {
            // ⚠️ 2026-10-21 (« modele-lettre-renvoi », B2) — une seule lettre pour tous les sous-types (Q1) : pas de famille à lire.
            return enregistrerDepart(m, ModelePvDepart.lettre(p, images.embleme().idImage()), "lettre de renvoi", p);
        }
        if (!"PV".equals(m.getNature())) {
            throw new BadRequestException("Pas de modèle de départ pour la nature " + m.getNature() + ".", "NATURE_SANS_DEPART");
        }
        Map<String, Object> parFamille = new java.util.TreeMap<>();
        rattachements.findByIdModele(m.getIdModele()).stream().map(RattachementModelePv::getIdSousType).sorted().forEach(st -> {
            String famille = sousTypes.findById(st).map(SousTypeDossier::getIdTypeDossier).orElse("?");
            @SuppressWarnings("unchecked")
            List<String> liste = (List<String>) parFamille.computeIfAbsent(famille, k -> new ArrayList<String>());
            liste.add(st);
        });
        if (parFamille.isEmpty()) {
            throw new BadRequestException("Le modèle « " + m.getCode() + " » ne sert aucun sous-type : rattachez-le d'abord, la famille "
                    + "à assembler s'en déduit.", "SANS_SOUS_TYPE");
        }
        if (parFamille.size() > 1) {
            throw new BusinessRuleException("Les sous-types du modèle relèvent de plusieurs familles (" + String.join(", ", parFamille.keySet())
                    + ") : un modèle de départ s'assemble pour une seule.", "FAMILLES_MULTIPLES", null, parFamille);
        }
        String famille = parFamille.keySet().iterator().next();
        ModelePvDepart.Famille f = ModelePvDepart.FAMILLES.get(famille);
        if (f == null) {
            throw new BusinessRuleException("Pas de modèle de départ pour la famille " + famille + ".", "FAMILLE_SANS_DEPART");
        }
        // ⚠️ 2026-10-21 (« modele-pv-page », §B5) — le départ est une page, reconstruite d'après la base et son emblème.
        return enregistrerDepart(m, ModelePvDepart.assembler(f, p, images.embleme().idImage()), "famille " + famille, p);
    }

    /** Le départ assemblé, enregistré en brouillon avec ses conditions et ses champs, contrôlé et journalisé. */
    private ModelePvDto.Version enregistrerDepart(ModelePv m, ModelePvDepart.Depart d, String quoi, String p) {
        VersionModelePv v = enregistrerPage(m, json.writeValueAsString(d.document()));
        v.setMotif(ModelePvDepart.MOTIF);
        enregistrerConditions(v, d.conditions());
        enregistrerChamps(v, d.champs());
        ModelePvDto.Rapport r = controlerEtEnregistrer(m, v);
        tracer(m, v.getNumero(), "DEPART_GENERE", quoi + ", page reconstruite d'après " + String.join(" + ", d.bases())
                + ", portée " + p + (r.bloquant() ? ", contrôle bloquant (" + r.nbBloquants() + ")" : ", contrôle sans blocage"));
        return dto(v, true);
    }

    private void enregistrerConditions(VersionModelePv v, List<ModelePvDto.Condition> liste) {
        conditions.deleteByIdVersion(v.getIdVersion());
        conditions.flush();
        int ordre = 0;
        for (ModelePvDto.Condition c : liste == null ? List.<ModelePvDto.Condition>of() : liste) {
            conditions.save(condition(v, c.nom().trim(), c.expression().trim(), c.libelle(), ordre++));
        }
    }

    private void enregistrerChamps(VersionModelePv v, List<ModelePvDto.Champ> liste) {
        champs.deleteByIdVersion(v.getIdVersion());
        champs.flush();
        int ordre = 0;
        for (ModelePvDto.Champ c : liste == null ? List.<ModelePvDto.Champ>of() : liste) {
            ChampSaisiModelePv n = new ChampSaisiModelePv();
            n.setIdVersion(v.getIdVersion());
            n.setCode(c.code().trim());
            n.setLibelle(c.libelle().trim());
            n.setType(c.type().trim().toUpperCase(Locale.ROOT));
            n.setOptions(c.options() == null ? null : String.join("\n", c.options().stream().filter(o -> o != null && !o.isBlank())
                    .map(String::trim).toList()));
            n.setObligatoire(Boolean.TRUE.equals(c.obligatoire()));
            n.setAide(c.aide());
            n.setVisibleSi(c.visibleSi() == null || c.visibleSi().isBlank() ? null : c.visibleSi().trim());
            n.setOrdre(ordre++);
            champs.save(n);
        }
    }

    /**
     * L'aperçu sur un dossier réel, <strong>sans rien enregistrer</strong> au dossier ni au modèle : le Word rempli, ou son PDF
     * (409 {@code CONVERSION_INDISPONIBLE} sans Word sur le serveur). Toute version s'essaie, brouillon compris.
     *
     * <p>⚠️ 2026-10-22 (demande front « apercu-exemple », E1) — ou sur le dossier d'exemple ({@code exemple},
     * {@link CatalogueModelesPv#exempleApercu}) : l'un ou l'autre (400 {@code DOSSIER_OU_EXEMPLE}). Le sous-type de l'exemple est le
     * premier rattaché au modèle ; une page d'exemple porte la mention « EXEMPLE — valeurs fictives » (Q1).</p>
     */
    @Transactional(readOnly = true)
    public byte[] apercu(Long id, Integer numero, Integer idDossier, boolean exemple, String avis, Integer idLettre, boolean pdf,
            Map<String, String> saisis) {
        if (exemple == (idDossier != null)) {
            throw new BadRequestException(exemple ? "L'aperçu se fait sur un dossier réel ou sur le dossier d'exemple, pas les deux."
                    : "L'aperçu demande un dossier (dossier=) ou le dossier d'exemple (exemple=true).", "DOSSIER_OU_EXEMPLE");
        }
        ModelePv m = modele(id);
        VersionModelePv v = version(m, numero);
        if (avis != null && !avis.isBlank() && !List.of("FAV", "FAVR", "DEF", "NSP").contains(avis)) {
            throw new BadRequestException("Avis inconnu : « " + avis + " » (FAV, FAVR, DEF, NSP).", "AVIS_INCONNU");
        }
        boolean page = VersionModelePv.PAGE.equals(v.getSupport());
        if (page && !pdf && !NATURES_WORD.contains(m.getNature())) {
            throw new BadRequestException("Une page de cette nature s'aperçoit en PDF seulement : le Word est réservé aux documents que le "
                    + "candidat ou la PRMP complètent.", "FORMAT_INDISPONIBLE");
        }
        // ⚠️ 2026-10-21 (« modele-lettre-renvoi », B1) — une lettre s'aperçoit sur une lettre du dossier (lettre=, à défaut la dernière).
        ModelePvWord.Contexte ctx;
        if (exemple) {
            String sousType = rattachements.findByIdModele(m.getIdModele()).stream().map(RattachementModelePv::getIdSousType).sorted()
                    .findFirst().orElse(null);
            ctx = CatalogueModelesPv.exempleApercu(avis, sousType,
                    sousType == null ? null : sousTypes.findById(sousType).map(SousTypeDossier::getLibelleSousType).orElse(null), saisis);
        } else {
            ctx = "LETTRE_RENVOI".equals(m.getNature()) ? contextes.pourLettre(idDossier, idLettre, saisis)
                    : contextes.pour(idDossier, avis == null || avis.isBlank() ? null : avis, saisis);
        }
        if (page) {
            // ⚠️ 2026-10-21 (« modele-pv-page », §B5) — le même rendu que l'essai du contrôle et que la production à venir (G2).
            try {
                return rendre(v.getDocument(), ctx, declarees(v), pdf, exemple ? CatalogueModelesPv.MENTION_EXEMPLE : null);
            } catch (RuntimeException e) {
                throw new BusinessRuleException("La page n'a pas pu être rendue : " + e.getMessage(), "APERCU_IMPOSSIBLE");
            }
        }
        byte[] docx;
        try {
            docx = ModelePvWord.remplir(v.getContenu(), ctx, declarees(v));
        } catch (IOException | RuntimeException e) {
            throw new BusinessRuleException("Le modèle n'a pas pu être rempli : " + e.getMessage(), "APERCU_IMPOSSIBLE");
        }
        if (!pdf) {
            return docx;
        }
        try {
            return convertisseur.convertirEnPdf(docx);
        } catch (RuntimeException e) {
            throw new BusinessRuleException("La conversion en PDF n'est pas disponible sur ce serveur (Word absent ou indisponible) : "
                    + "l'aperçu se demande en Word.", "CONVERSION_INDISPONIBLE");
        }
    }

    /** La version remplace la précédente pour les PV à venir (409 si son rapport est bloquant ; motif obligatoire). */
    public ModelePvDto.Fiche mettreEnVigueur(Long id, Integer numero, String motif) {
        ModelePv m = actif(id);
        VersionModelePv v = brouillon(m, numero);
        if (!VersionModelePv.PAGE.equals(v.getSupport())) {
            // ⚠️ 2026-10-21 (« modele-pv-page », §B6, Q5) — les versions Word restent en consultation seulement.
            throw new BusinessRuleException("La version " + numero + " est un Word : ce support est abandonné, elle reste en consultation. "
                    + "Générez un modèle de départ en page ou enregistrez une page.", "SUPPORT_ABANDONNE");
        }
        if (motif == null || motif.isBlank()) {
            throw new BadRequestException("La mise en vigueur demande un motif.", "MOTIF_REQUIS");
        }
        ModelePvDto.Rapport r = controlerEtEnregistrer(m, v);
        if (r.bloquant()) {
            throw new BusinessRuleException("Le rapport de contrôle de la version " + numero + " est bloquant (" + r.nbBloquants()
                    + ") : corrigez la page et enregistrez-la de nouveau.", "RAPPORT_BLOQUANT", null, Map.of("nbBloquants", r.nbBloquants()));
        }
        Set<String> avant = releves();
        LocalDateTime maintenant = LocalDateTime.now();
        versions.findByIdModeleAndEtat(m.getIdModele(), VersionModelePv.EN_VIGUEUR).ifPresent(p -> {
            p.setEtat(VersionModelePv.REMPLACEE);
            p.setRemplaceeLe(maintenant);
            versions.saveAndFlush(p);
        });
        v.setEtat(VersionModelePv.EN_VIGUEUR);
        v.setMotif(motif.trim());
        v.setEnVigueurLe(maintenant);
        v.setEnVigueurPar(CurrentUser.login().orElse(null));
        versions.saveAndFlush(v);
        tracer(m, numero, "MISE_EN_VIGUEUR", motif.trim());
        tracerReleve(m, avant, "mise en vigueur de la version " + numero);
        return fiche(m);
    }

    /**
     * Les sous-types servis par le modèle, remplacés en bloc. Un sous-type a au plus un modèle par nature : 409
     * {@code SOUS_TYPE_DEJA_SERVI} s'il est rattaché à un autre modèle (à détacher d'abord).
     */
    public ModelePvDto.Fiche rattacher(Long id, List<String> demandes) {
        ModelePv m = actif(id);
        Set<String> voulus = new LinkedHashSet<>();
        for (String s : demandes == null ? List.<String>of() : demandes) {
            if (s != null && !s.isBlank()) {
                voulus.add(s.trim());
            }
        }
        List<String> inconnus = voulus.stream().filter(s -> !sousTypes.existsById(s)).toList();
        if (!inconnus.isEmpty()) {
            throw new BadRequestException("Sous-type(s) inconnu(s) : " + String.join(", ", inconnus) + ".", "SOUS_TYPE_INCONNU");
        }
        Map<String, Object> pris = new LinkedHashMap<>();
        for (String s : voulus) {
            rattachements.findByIdSousTypeAndNature(s, m.getNature()).filter(r -> !r.getIdModele().equals(m.getIdModele()))
                    .ifPresent(r -> pris.put(s, modeles.findById(r.getIdModele()).map(ModelePv::getCode).orElse(String.valueOf(r.getIdModele()))));
        }
        if (!pris.isEmpty()) {
            throw new BusinessRuleException("Sous-type(s) déjà servi(s) par un autre modèle : " + pris + ". Un sous-type n'a qu'un modèle "
                    + "à la fois : détachez-le d'abord.", "SOUS_TYPE_DEJA_SERVI", null, pris);
        }
        Set<String> relevesAvant = releves();
        List<RattachementModelePv> actuels = rattachements.findByIdModele(m.getIdModele());
        Set<String> avant = new LinkedHashSet<>();
        for (RattachementModelePv r : actuels) {
            avant.add(r.getIdSousType());
            if (!voulus.contains(r.getIdSousType())) {
                rattachements.delete(r);
            }
        }
        for (String s : voulus) {
            if (!avant.contains(s)) {
                RattachementModelePv r = new RattachementModelePv();
                r.setIdSousType(s);
                r.setNature(m.getNature());
                r.setIdModele(m.getIdModele());
                r.setRattacheLe(LocalDateTime.now());
                r.setRattachePar(CurrentUser.login().orElse(null));
                rattachements.save(r);
            }
        }
        rattachements.flush();
        tracer(m, null, "RATTACHEMENTS", voulus.isEmpty() ? "aucun sous-type" : String.join(", ", voulus));
        tracerReleve(m, relevesAvant, "rattachements");
        return fiche(m);
    }

    /**
     * Retire le modèle. S'il sert un sous-type par une version en vigueur, 409 {@code MODELE_EN_SERVICE} sans motif ; avec un motif,
     * il est archivé et détaché : ses sous-types n'ont plus de modèle ; ⚠️ 2026-10-22 (« releve-plan-lettre », Q1) ceux du plan et les
     * lettres reviennent aux fichiers intégrés, au journal général ({@code RETOUR_MODELE_INTEGRE}).
     */
    public ModelePvDto.Fiche archiver(Long id, String motif) {
        ModelePv m = actif(id);
        List<RattachementModelePv> servis = rattachements.findByIdModele(m.getIdModele());
        boolean enVigueur = versions.findByIdModeleAndEtat(m.getIdModele(), VersionModelePv.EN_VIGUEUR).isPresent();
        if (enVigueur && !servis.isEmpty() && (motif == null || motif.isBlank())) {
            throw new BusinessRuleException("Ce modèle est en vigueur pour " + servis.stream().map(RattachementModelePv::getIdSousType).toList()
                    + " : ces sous-types n'auraient plus de modèle. Donnez un motif pour l'archiver quand même.", "MODELE_EN_SERVICE", null,
                    Map.of("sousTypes", servis.stream().map(RattachementModelePv::getIdSousType).toList()));
        }
        Set<String> relevesAvant = releves();
        rattachements.deleteAll(servis);
        rattachements.flush();
        m.setEtat(ModelePv.ARCHIVE);
        m.setArchiveLe(LocalDateTime.now());
        m.setMotifArchivage(motif == null || motif.isBlank() ? null : motif.trim());
        modeles.save(m);
        tracer(m, null, "ARCHIVE", (motif == null || motif.isBlank() ? "sans motif" : motif.trim())
                + (servis.isEmpty() ? "" : ", détaché de " + servis.stream().map(RattachementModelePv::getIdSousType).toList()));
        tracerReleve(m, relevesAvant, "archivage");
        return fiche(m);
    }

    /**
     * ⚠️ 2026-10-21 (« modele-pv-suppression », B1) — supprime un brouillon, avec ses conditions et ses champs ; les images de la
     * bibliothèque restent. Une version qui a été en vigueur ne se supprime pas (409 {@code VERSION_NON_SUPPRIMABLE}). Le numéro
     * n'est jamais réutilisé (Q1).
     */
    public ModelePvDto.Fiche supprimerVersion(Long id, Integer numero) {
        ModelePv m = actif(id);
        VersionModelePv v = version(m, numero);
        if (!VersionModelePv.BROUILLON.equals(v.getEtat())) {
            throw new BusinessRuleException("La version " + numero + " est " + (VersionModelePv.EN_VIGUEUR.equals(v.getEtat()) ? "en vigueur"
                    : "remplacée") + " : une version qui a été en vigueur ne se supprime pas.", "VERSION_NON_SUPPRIMABLE");
        }
        conditions.deleteByIdVersion(v.getIdVersion());
        champs.deleteByIdVersion(v.getIdVersion());
        versions.delete(v);
        versions.flush();
        tracer(m, numero, "VERSION_SUPPRIMEE", "version " + numero + ", " + (VersionModelePv.PAGE.equals(v.getSupport()) ? "page" : "Word"));
        return fiche(m);
    }

    /**
     * ⚠️ 2026-10-21 (« modele-pv-suppression », B2) — supprime un modèle dont aucun PV n'a pu dépendre : jamais en vigueur (ni
     * version {@code EN_VIGUEUR} / {@code REMPLACEE}, ni mise en vigueur au journal) et ne servant aucun sous-type. Ses versions,
     * conditions, champs, rattachements et journal partent avec lui ; la trace reste au journal général (Q2).
     */
    public void supprimer(Long id) {
        ModelePv m = modele(id);
        if (versions.existsByIdModeleAndEtatIn(m.getIdModele(), List.of(VersionModelePv.EN_VIGUEUR, VersionModelePv.REMPLACEE))
                || journal.existsByIdModeleAndAction(m.getIdModele(), "MISE_EN_VIGUEUR")) {
            throw new BusinessRuleException("Le modèle « " + m.getCode() + " » a été en vigueur : il ne se supprime pas, il reste pour la "
                    + "traçabilité.", "MODELE_NON_SUPPRIMABLE", null, Map.of("raison", "A_ETE_EN_VIGUEUR"));
        }
        List<RattachementModelePv> servis = rattachements.findByIdModele(m.getIdModele());
        if (!servis.isEmpty()) {
            List<String> st = servis.stream().map(RattachementModelePv::getIdSousType).sorted().toList();
            throw new BusinessRuleException("Le modèle « " + m.getCode() + " » sert encore des sous-types " + st
                    + " : détachez-les ou archivez le modèle.", "MODELE_NON_SUPPRIMABLE", null,
                    Map.of("raison", "SERT_DES_SOUS_TYPES", "sousTypes", st));
        }
        List<VersionModelePv> vs = versions.findByIdModeleOrderByNumeroDesc(m.getIdModele());
        for (VersionModelePv v : vs) {
            conditions.deleteByIdVersion(v.getIdVersion());
            champs.deleteByIdVersion(v.getIdVersion());
        }
        versions.deleteAll(vs);
        journal.deleteByIdModele(m.getIdModele());
        modeles.delete(m);
        modeles.flush();
        audit.enregistrerDetail(CurrentUser.ref().orElse(null), "modeles-pv", String.valueOf(m.getIdModele()), "MODELE_SUPPRIME",
                m.getCode(), m.getCode() + " — " + m.getLibelle() + " (" + m.getNature() + ", " + m.getEtat()
                        + (m.getMotifArchivage() == null ? "" : ", archivé : " + m.getMotifArchivage()) + ") ; " + vs.size()
                        + " version(s) en brouillon supprimée(s) ; par " + CurrentUser.login().orElse("?"), null);
    }

    // ───────────────────────────────────────────── contrôle (§B5) ─────────────────────────────────────────────

    /** Contrôle la version sur ses conditions et champs actuels, et enregistre le rapport. */
    private ModelePvDto.Rapport controlerEtEnregistrer(ModelePv m, VersionModelePv v) {
        Set<String> codesChamps = new LinkedHashSet<>();
        champs.findByIdVersionOrderByOrdreAsc(v.getIdVersion()).forEach(c -> codesChamps.add(c.getCode()));
        ModelePvDto.Rapport r = VersionModelePv.PAGE.equals(v.getSupport())
                ? controlerPage(v.getDocument(), m.getNature(), declarees(v), codesChamps)
                : controler(v.getContenu(), m.getNature(), declarees(v), codesChamps);
        v.setRapport(json.writeValueAsString(r));
        v.setBloquant(r.bloquant());
        versions.save(v);
        return r;
    }

    /** Le contrôle d'une version Word (gardée en consultation) : relevé du fichier, puis les points communs. */
    ModelePvDto.Rapport controler(byte[] contenu, String nature, Map<String, String> declarees, Set<String> codesChamps) {
        List<ModelePvDto.Point> pts = new ArrayList<>();
        ModelePvWord.Releve rel;
        try {
            rel = ModelePvWord.relever(contenu);
        } catch (IOException | RuntimeException e) {
            pts.add(point("BLOQUANT", "FICHIER_ILLISIBLE", "Le fichier n'est pas un Word (.docx) lisible : " + e.getMessage(), null, null));
            return rapport(pts, List.of(), List.of(), List.of());
        }
        pts.add(point("OK", "FICHIER_LISIBLE", "Fichier Word lisible", null, null));
        rel.defauts().forEach(d -> pts.add(point("BLOQUANT", d.code(), d.message(), d.ou(), null)));
        boolean blocsSains = rel.defauts().stream().noneMatch(d -> d.code().startsWith("BLOC_"));
        return points(pts, rel.marqueurs(), Map.of(), false, blocsSains, nature, declarees, codesChamps,
                () -> essai(contenu, declarees, pts));
    }

    /**
     * ⚠️ 2026-10-21 (« modele-pv-page », §B4) — le contrôle d'une page : les mêmes points, appliqués aux nœuds ; {@code ou} est le
     * lieu lisible du nœud, {@code chemin} ses rangs. Les défauts propres au Word (fichier illisible, marqueur coupé, bloc mal fermé,
     * ancien marqueur, image) n'ont plus lieu d'être : un nœud ne se coupe pas.
     */
    ModelePvDto.Rapport controlerPage(String document, String nature, Map<String, String> declarees, Set<String> codesChamps) {
        List<ModelePvDto.Point> pts = new ArrayList<>();
        JsonNode doc = json.readTree(document);
        ModelePvPage.Releve rel = ModelePvPage.relever(doc);
        pts.add(point("OK", "DOCUMENT_VALIDE", "Document de la page conforme au schéma", null, null));
        return points(pts, rel.marqueurs(), rel.chemins(), true, true, nature, declarees, codesChamps,
                () -> essaiPage(document, declarees, pts));
    }

    /** Les points communs aux deux supports, sur les marqueurs relevés ; {@code page} adapte les messages et les suggestions. */
    private ModelePvDto.Rapport points(List<ModelePvDto.Point> pts, List<ModelePvWord.Marqueur> marqueurs,
            Map<ModelePvWord.Marqueur, String> chemins, boolean page, boolean blocsSains, String nature, Map<String, String> declarees,
            Set<String> codesChamps, Runnable essai) {
        Set<String> variables = new LinkedHashSet<>();
        Set<String> listes = new LinkedHashSet<>();
        Set<String> conditionsUtilisees = new LinkedHashSet<>();
        Set<String> champsUtilises = new LinkedHashSet<>();
        int repetees = 0;
        for (ModelePvWord.Marqueur mk : marqueurs) {
            String ch = chemins.get(mk);
            switch (mk.type()) {
                case "VARIABLE", "ALIAS" -> {
                    CatalogueModelesPv.Variable var = CatalogueModelesPv.variable(mk.code()).orElse(null);
                    if (var == null) {
                        String s = CatalogueModelesPv.suggestion(mk.code()).orElse(null);
                        pts.add(point("BLOQUANT", "VARIABLE_INCONNUE", "Variable inconnue " + mk.brut()
                                + (s == null ? "" : " — vouliez-vous {{" + s + "}} ?"), mk.ou(),
                                s == null ? null : page ? s : "{{" + s + "}}", ch));
                        continue;
                    }
                    // ⚠️ 2026-10-21 (« modele-lettre-renvoi », B1) — une variable du PV dans une lettre, ou l'inverse.
                    String sienne = CatalogueModelesPv.nature(var.code());
                    if (sienne != null && !sienne.equals(nature)) {
                        pts.add(point("BLOQUANT", "VARIABLE_HORS_NATURE", mk.brut() + " ne vaut que pour "
                                + ("PV".equals(sienne) ? "un PV" : "une lettre de renvoi") + " : ce modèle est "
                                + ("PV".equals(nature) ? "un PV." : "une lettre de renvoi."), mk.ou(), null, ch));
                    }
                    if (mk.format() != null && !CatalogueModelesPv.FORMATS.contains(mk.format())) {
                        pts.add(point("BLOQUANT", "FORMAT_INCONNU", "Format inconnu « " + mk.format() + " » dans " + mk.brut() + " "
                                + CatalogueModelesPv.FORMATS, mk.ou(), null, ch));
                    }
                    if (var.liste() != null && !var.liste().equals(mk.liste())) {
                        pts.add(point("BLOQUANT", "VARIABLE_HORS_LISTE", mk.brut() + " ne vaut que sur une ligne de tableau répétée : "
                                + (page ? "placez-la dans une ligne répétée de la liste " + var.liste() + "."
                                        : "écrivez {{POUR:" + var.liste() + "}} dans la première cellule de la ligne."), mk.ou(), null, ch));
                    }
                    if ("ALIAS".equals(mk.type())) {
                        pts.add(point("AVERTISSEMENT", "ANCIEN_MARQUEUR", "Ancien marqueur " + mk.brut() + " lu comme {{" + var.code() + "}}",
                                mk.ou(), "{{" + var.code() + "}}", ch));
                    }
                    if (variables.add(var.code()) && var.liste() != null) {
                        repetees++;
                        listes.add(var.liste());
                    }
                }
                case "SAISI" -> {
                    champsUtilises.add(mk.code());
                    if (!codesChamps.contains(mk.code())) {
                        pts.add(point("BLOQUANT", "CHAMP_NON_DECLARE", mk.brut() + " : le champ « " + mk.code()
                                + " » n'est pas déclaré dans les champs saisis de la version.", mk.ou(), null, ch));
                    }
                }
                case "SI" -> {
                    conditionsUtilisees.add(mk.code());
                    if (!declarees.containsKey(mk.code())) {
                        blocsSains = false;
                        pts.add(point("BLOQUANT", "CONDITION_NON_DECLAREE", (page ? "Bloc « " + mk.code() + " »" : mk.brut())
                                + " : la condition « " + mk.code() + " » n'est pas déclarée pour cette version.", mk.ou(), null, ch));
                    }
                }
                case "POUR" -> {
                    if (CatalogueModelesPv.liste(mk.code()).isEmpty()) {
                        pts.add(point("BLOQUANT", "LISTE_INCONNUE", "Liste inconnue " + (page ? "« " + mk.code() + " »" : mk.brut()) + " "
                                + CatalogueModelesPv.LISTES.stream().map(CatalogueModelesPv.Liste::nom).toList(), mk.ou(), null, ch));
                    } else if (!mk.premiereCellule() || !mk.code().equals(mk.liste())) {
                        pts.add(point("BLOQUANT", "POUR_MAL_PLACE", page
                                ? "La ligne répétée « " + mk.code() + " » est dans une autre ligne répétée : deux listes ne s'imbriquent pas."
                                : mk.brut() + " s'écrit dans la première cellule de la ligne de tableau à répéter.", mk.ou(), null, ch));
                    }
                }
                case "IMAGE" -> pts.add(point("BLOQUANT", "IMAGE_NON_PRISE_EN_CHARGE", mk.brut() + " : insérez l'image directement "
                        + "dans le Word (l'emblème, le logo), elle sera reproduite telle quelle.", mk.ou(), null));
                default -> {
                    // FINSI : vérifié avec son SI au relevé
                }
            }
        }
        for (String nom : declarees.keySet()) {
            if (!conditionsUtilisees.contains(nom)) {
                pts.add(point("AVERTISSEMENT", "CONDITION_INUTILISEE", "La condition « " + nom + " » est déclarée mais aucun "
                        + (page ? "bloc" : "{{SI:" + nom + "}}") + " ne l'emploie.", null, null));
            }
        }
        for (String code : codesChamps) {
            if (!champsUtilises.contains(code)) {
                pts.add(point("AVERTISSEMENT", "CHAMP_INUTILISE", "Le champ « " + code + " » est déclaré mais " + (page
                        ? "la page ne l'emploie pas." : "{{SAISI:" + code + "}} n'apparaît pas dans le Word."), null, null));
            }
        }
        List<String> manquantes = new ArrayList<>();
        for (String requise : CatalogueModelesPv.REQUISES.getOrDefault(nature, List.of())) {
            if (!variables.contains(requise)) {
                manquantes.add(requise);
            }
        }
        boolean avisParCondition = conditionsUtilisees.stream().map(declarees::get)
                .anyMatch(e -> e != null && cles(e).contains("avis"));
        if ("PV".equals(nature) && !variables.contains("PV.AVIS") && !avisParCondition) {
            manquantes.add("PV.AVIS");
        }
        for (String code : manquantes) {
            pts.add(point("BLOQUANT", "SECTION_ABSENTE", "Section attendue absente (" + SECTIONS.getOrDefault(code, code) + ") : {{" + code
                    + "}}" + ("PV.AVIS".equals(code) ? " ou un bloc dont la condition lit l'avis" : "") + " n'apparaît pas.", null,
                    page ? code : "{{" + code + "}}"));
        }
        if (manquantes.isEmpty()) {
            pts.add(point("OK", "SECTIONS", "PV".equals(nature) ? "Sections attendues : en-tête, identification, avis, signatures"
                    : "Sections attendues : identification du dossier, corps de la lettre, signature", null, null));
        }
        pts.add(point("OK", "VARIABLES", variables.size() + " variable(s) reconnue(s)" + (repetees == 0 ? ""
                : ", dont " + repetees + " répétée(s) (" + String.join(", ", listes) + ")"), null, null));
        if (blocsSains) {
            pts.add(point("OK", "BLOCS", conditionsUtilisees.size() + " bloc(s) conditionnel(s), chacun " + (page ? "" : "fermé et ")
                    + "déclaré", null, null));
        }
        essai.run();
        return rapport(pts, new ArrayList<>(variables), new ArrayList<>(conditionsUtilisees), new ArrayList<>(champsUtilises));
    }

    /** ⚠️ 2026-10-21 (« modele-pv-page », §B5) — l'essai d'une page : son rendu PDF sur le dossier fictif du catalogue. */
    private void essaiPage(String document, Map<String, String> declarees, List<ModelePvDto.Point> pts) {
        try {
            rendre(document, CatalogueModelesPv.exemple(), declarees);
            pts.add(point("OK", "ESSAI", "Production d'essai sur un dossier fictif jusqu'au PDF", null, null));
        } catch (RuntimeException e) {
            pts.add(point("BLOQUANT", "ESSAI_ECHEC", "Le rendu PDF d'essai sur un dossier fictif a échoué : " + e.getMessage(), null, null));
        }
    }

    /** Le PDF d'une page remplie : le seul rendu, pour l'aperçu, l'essai et la production à venir (G2). */
    private byte[] rendre(String document, ModelePvWord.Contexte ctx, Map<String, String> declarees) {
        return rendre(document, ctx, declarees, true, null);
    }

    /**
     * ⚠️ 2026-10-22 (« modeles-documents-marche », W1) — le PDF ou le Word d'une page remplie, depuis le même document résolu ;
     * {@code mention} sur chaque page (aperçu d'exemple), {@code null} : aucune.
     */
    private byte[] rendre(String document, ModelePvWord.Contexte ctx, Map<String, String> declarees, boolean pdf, String mention) {
        JsonNode resolu = ModelePvPage.resoudre(json.readTree(document), ctx, declarees);
        return pdf ? ModelePvPage.pdf(ModelePvPage.xhtmlResolu(resolu, images::pourRendu, mention))
                : ModelePvDocx.docx(resolu, images::pourRendu, mention);
    }

    /** La production d'essai sur le dossier fictif du catalogue, jusqu'au PDF quand Word est là. */
    private void essai(byte[] contenu, Map<String, String> declarees, List<ModelePvDto.Point> pts) {
        byte[] docx;
        try {
            docx = ModelePvWord.remplir(contenu, CatalogueModelesPv.exemple(), declarees);
        } catch (IOException | RuntimeException e) {
            pts.add(point("BLOQUANT", "ESSAI_ECHEC", "La production d'essai sur un dossier fictif a échoué : " + e.getMessage(), null, null));
            return;
        }
        if (!essaiPdf) {
            pts.add(point("AVERTISSEMENT", "ESSAI_PDF_NON_VERIFIE", "Production d'essai réussie jusqu'au Word ; la conversion en PDF est "
                    + "désactivée sur ce serveur.", null, null));
            return;
        }
        try {
            convertisseur.convertirEnPdf(docx);
            pts.add(point("OK", "ESSAI", "Production d'essai sur un dossier fictif jusqu'au PDF", null, null));
        } catch (RuntimeException e) {
            pts.add(point("AVERTISSEMENT", "ESSAI_PDF_NON_VERIFIE", "Production d'essai réussie jusqu'au Word ; la conversion en PDF n'a "
                    + "pas pu être vérifiée (Word absent ou indisponible sur le serveur).", null, null));
        }
    }

    /** Les clés lues par une expression de condition. */
    static List<String> cles(String expression) {
        List<String> out = new ArrayList<>();
        for (String fragment : expression.split("\\s+(?:et|ou)\\s+")) {
            Matcher m = TERME.matcher(fragment);
            if (m.find()) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    private static ModelePvDto.Rapport rapport(List<ModelePvDto.Point> pts, List<String> variables, List<String> conditionsUtilisees,
            List<String> champsUtilises) {
        int b = (int) pts.stream().filter(p -> "BLOQUANT".equals(p.niveau())).count();
        int a = (int) pts.stream().filter(p -> "AVERTISSEMENT".equals(p.niveau())).count();
        return new ModelePvDto.Rapport(b > 0, b, a, pts, variables, conditionsUtilisees, champsUtilises, LocalDateTime.now());
    }

    private static ModelePvDto.Point point(String niveau, String code, String message, String ou, String suggestion) {
        return point(niveau, code, message, ou, suggestion, null);
    }

    private static ModelePvDto.Point point(String niveau, String code, String message, String ou, String suggestion, String chemin) {
        return new ModelePvDto.Point(niveau, code, message, ou, suggestion, chemin);
    }

    // ───────────────────────────────────────────── outils ─────────────────────────────────────────────

    private ModelePv nouveau(String code, String libelle, String nature) {
        String c = code.trim();
        if (!CODE_MODELE.matcher(c).matches()) {
            throw new BadRequestException("Code invalide : « " + code + " » (lettres, chiffres, _ . -).", "CODE_INVALIDE");
        }
        if (modeles.findByCode(c).isPresent()) {
            throw new BusinessRuleException("Le code « " + c + " » est déjà pris.", "CODE_DEJA_PRIS");
        }
        ModelePv m = new ModelePv();
        m.setCode(c);
        m.setLibelle(libelle.trim());
        m.setNature(nature);
        m.setEtat(ModelePv.ACTIF);
        m.setCreeLe(LocalDateTime.now());
        m.setCreePar(CurrentUser.login().orElse(null));
        return modeles.save(m);
    }

    private String codeLibre(String base) {
        String c = base.length() > 44 ? base.substring(0, 44) : base;
        String code = c;
        for (int i = 2; modeles.findByCode(code).isPresent(); i++) {
            code = c + "-" + i;
        }
        return code;
    }

    /** ⚠️ 2026-10-21 (« modele-pv-page ») — une nouvelle version page, en brouillon, au numéro suivant. */
    private VersionModelePv enregistrerPage(ModelePv m, String document) {
        List<VersionModelePv> vs = versions.findByIdModeleOrderByNumeroDesc(m.getIdModele());
        VersionModelePv v = new VersionModelePv();
        v.setIdModele(m.getIdModele());
        // ⚠️ 2026-10-21 (« modele-pv-suppression », Q1) — un numéro supprimé n'est jamais repris : le journal le cite encore.
        Integer cite = journal.numeroMax(m.getIdModele());
        v.setNumero(Math.max(vs.isEmpty() ? 0 : vs.get(0).getNumero(), cite == null ? 0 : cite) + 1);
        v.setSupport(VersionModelePv.PAGE);
        poserDocument(v, document);
        v.setEtat(VersionModelePv.BROUILLON);
        v.setBloquant(true);
        v.setDeposeeLe(LocalDateTime.now());
        v.setDeposeePar(CurrentUser.login().orElse(null));
        return versions.saveAndFlush(v);
    }

    /** Le document d'une page, son empreinte et sa taille (sur le JSON enregistré). */
    private static void poserDocument(VersionModelePv v, String document) {
        byte[] octets = document.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        v.setDocument(document);
        v.setEmpreinte(sha256(octets));
        v.setTaille((long) octets.length);
    }

    private static ConditionModelePv condition(VersionModelePv v, String nom, String expression, String libelle, int ordre) {
        ConditionModelePv c = new ConditionModelePv();
        c.setIdVersion(v.getIdVersion());
        c.setNom(nom);
        c.setExpression(expression);
        c.setLibelle(libelle);
        c.setOrdre(ordre);
        return c;
    }

    private Map<String, String> declarees(VersionModelePv v) {
        Map<String, String> out = new LinkedHashMap<>();
        conditions.findByIdVersionOrderByOrdreAsc(v.getIdVersion()).forEach(c -> out.put(c.getNom(), c.getExpression()));
        return out;
    }

    private ModelePv modele(Long id) {
        return modeles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Modèle de PV introuvable : " + id));
    }

    private ModelePv actif(Long id) {
        ModelePv m = modele(id);
        if (ModelePv.ARCHIVE.equals(m.getEtat())) {
            throw new BusinessRuleException("Le modèle « " + m.getCode() + " » est archivé.", "MODELE_ARCHIVE");
        }
        return m;
    }

    private VersionModelePv version(ModelePv m, Integer numero) {
        return versions.findByIdModeleAndNumero(m.getIdModele(), numero)
                .orElseThrow(() -> new ResourceNotFoundException("Version " + numero + " introuvable pour le modèle " + m.getCode() + "."));
    }

    private VersionModelePv brouillon(ModelePv m, Integer numero) {
        VersionModelePv v = version(m, numero);
        if (!VersionModelePv.BROUILLON.equals(v.getEtat())) {
            throw new BusinessRuleException("La version " + numero + " est " + (VersionModelePv.EN_VIGUEUR.equals(v.getEtat()) ? "en vigueur"
                    : "remplacée") + " : elle ne se modifie plus, déposez-en une nouvelle.", "VERSION_FIGEE");
        }
        return v;
    }

    private void tracer(ModelePv m, Integer numero, String action, String detail) {
        JournalModelePv j = new JournalModelePv();
        j.setIdModele(m.getIdModele());
        j.setNumeroVersion(numero);
        j.setAction(action);
        j.setDetail(detail == null || detail.length() <= 2000 ? detail : detail.substring(0, 2000));
        j.setPar(CurrentUser.login().orElse(null));
        j.setLe(LocalDateTime.now());
        journal.save(j);
    }

    private ModelePvDto.Fiche fiche(ModelePv m) {
        List<ModelePvDto.SousType> st = rattachements.findByIdModele(m.getIdModele()).stream()
                .map(r -> sousTypes.findById(r.getIdSousType())
                        .map(s -> new ModelePvDto.SousType(s.getIdSousType(), s.getLibelleSousType(), s.getIdTypeDossier()))
                        .orElse(new ModelePvDto.SousType(r.getIdSousType(), null, null)))
                .sorted(java.util.Comparator.comparing(ModelePvDto.SousType::id)).toList();
        return new ModelePvDto.Fiche(m.getIdModele(), m.getCode(), m.getLibelle(), m.getNature(), m.getEtat(), m.getCreeLe(),
                m.getCreePar(), m.getArchiveLe(), m.getMotifArchivage(), st,
                versions.findByIdModeleOrderByNumeroDesc(m.getIdModele()).stream().map(this::dto).toList(),
                journal.findByIdModeleOrderByLeDescIdJournalDesc(m.getIdModele()).stream()
                        .map(j -> new ModelePvDto.Evenement(j.getLe(), j.getAction(), j.getNumeroVersion(), j.getDetail(), j.getPar()))
                        .toList());
    }

    private ModelePvDto.Version dto(VersionModelePv v) {
        return dto(v, false);
    }

    /** {@code avecDocument} : le document de la page est servi (lecture d'une version, gestes qui la produisent). */
    private ModelePvDto.Version dto(VersionModelePv v, boolean avecDocument) {
        ModelePvDto.Rapport r = null;
        if (v.getRapport() != null) {
            try {
                r = json.readValue(v.getRapport(), ModelePvDto.Rapport.class);
            } catch (JacksonException e) {
                r = null;
            }
        }
        return new ModelePvDto.Version(v.getNumero(), v.getNomFichier(), v.getTaille(), v.getEmpreinte(), v.getEtat(),
                Boolean.TRUE.equals(v.getBloquant()), r, v.getMotif(), v.getDeposeeLe(), v.getDeposeePar(), v.getEnVigueurLe(),
                v.getEnVigueurPar(), v.getRemplaceeLe(),
                conditions.findByIdVersionOrderByOrdreAsc(v.getIdVersion()).stream()
                        .map(c -> new ModelePvDto.Condition(c.getNom(), c.getExpression(), c.getLibelle())).toList(),
                champs.findByIdVersionOrderByOrdreAsc(v.getIdVersion()).stream()
                        .map(c -> new ModelePvDto.Champ(c.getCode(), c.getLibelle(), c.getType(),
                                c.getOptions() == null || c.getOptions().isBlank() ? List.of() : List.of(c.getOptions().split("\n")),
                                Boolean.TRUE.equals(c.getObligatoire()), c.getAide(), c.getVisibleSi()))
                        .toList(),
                v.getSupport(), avecDocument && v.getDocument() != null ? json.readTree(v.getDocument()) : null);
    }

    private static ModelePvDto.VersionCourte courte(VersionModelePv v) {
        return v == null ? null : new ModelePvDto.VersionCourte(v.getNumero(), v.getEtat(), Boolean.TRUE.equals(v.getBloquant()),
                v.getDeposeeLe(), v.getEnVigueurLe());
    }

    private static String sha256(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
