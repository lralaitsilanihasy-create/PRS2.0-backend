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
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ 2026-10-20 (demande front « generateur-modeles-pv », lot G1 ; V138) — le <strong>générateur de modèles de PV</strong>, côté
 * Administrateur : créer un modèle, déposer une version {@code .docx} (contrôlée à chaque dépôt et à chaque déclaration), déclarer
 * ses conditions et ses champs saisis, l'essayer sur un dossier réel sans rien enregistrer, la mettre en vigueur avec un motif,
 * rattacher le modèle aux sous-types qu'il sert, le dupliquer, l'archiver. Tout est journalisé.
 *
 * <p>Ce lot ne produit encore aucun PV : la production par la version en vigueur (B6) est le lot G2. Les 14 modèles du plan restent
 * servis par {@link PvDocumentGenerator}.</p>
 */
@Service
@Transactional
public class ModelePvService {

    static final Set<String> NATURES = Set.of("PV", "LETTRE_RENVOI");
    static final Set<String> TYPES_CHAMP = Set.of("TEXTE", "TEXTE_LONG", "LISTE", "CASE", "DATE", "NOMBRE", "PIECE");
    private static final long TAILLE_MAX = 10L * 1024 * 1024;
    private static final Pattern NOM_CONDITION = Pattern.compile("[A-Za-z0-9_-]{1,60}");
    private static final Pattern CODE_CHAMP = Pattern.compile("[A-Za-z0-9_]{1,60}");
    private static final Pattern CODE_MODELE = Pattern.compile("[A-Za-z0-9_.-]{1,50}");
    private static final Pattern TERME = Pattern.compile("^\\s*(" + ConditionsModele.CLE + ")\\s*(?:!?=|contient\\s|renseigne|vide)");
    private static final Map<String, String> SECTIONS = Map.of("PV.REFERENCE", "en-tête et identification",
            "PV.MEMBRE", "signatures", "PV.AVIS", "avis", "DOSSIER.REFERENCE", "identification");

    private final ModelePvRepository modeles;
    private final VersionModelePvRepository versions;
    private final ConditionModelePvRepository conditions;
    private final ChampSaisiModelePvRepository champs;
    private final RattachementModelePvRepository rattachements;
    private final JournalModelePvRepository journal;
    private final SousTypeDossierRepository sousTypes;
    private final ContexteModelePvService contextes;
    private final PvDocumentGenerator convertisseur;
    private final ObjectMapper json;
    private final boolean essaiPdf;

    public ModelePvService(ModelePvRepository modeles, VersionModelePvRepository versions, ConditionModelePvRepository conditions,
            ChampSaisiModelePvRepository champs, RattachementModelePvRepository rattachements, JournalModelePvRepository journal,
            SousTypeDossierRepository sousTypes, ContexteModelePvService contextes, PvDocumentGenerator convertisseur,
            ObjectMapper json, @Value("${app.modeles-pv.essai-pdf:true}") boolean essaiPdf) {
        this.modeles = modeles;
        this.versions = versions;
        this.conditions = conditions;
        this.champs = champs;
        this.rattachements = rattachements;
        this.journal = journal;
        this.sousTypes = sousTypes;
        this.contextes = contextes;
        this.convertisseur = convertisseur;
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
        return dto(version(modele(id), numero));
    }

    @Transactional(readOnly = true)
    public VersionModelePv fichier(Long id, Integer numero) {
        return version(modele(id), numero);
    }

    /** Le catalogue des variables (§B3), filtré par la famille du sous-type ({@code null} : tout). */
    @Transactional(readOnly = true)
    public ModelePvDto.Catalogue catalogue(String sousType) {
        String famille = null;
        if (sousType != null && !sousType.isBlank()) {
            famille = sousTypes.findById(sousType).map(SousTypeDossier::getIdTypeDossier)
                    .orElseThrow(() -> new BadRequestException("Sous-type inconnu : « " + sousType + " ».", "SOUS_TYPE_INCONNU"));
        }
        Map<String, List<ModelePvDto.Variable>> groupes = new LinkedHashMap<>();
        for (CatalogueModelesPv.Variable v : CatalogueModelesPv.pourFamille(famille)) {
            groupes.computeIfAbsent(v.groupe(), g -> new ArrayList<>())
                    .add(new ModelePvDto.Variable(v.code(), v.libelle(), v.exemple(), v.liste()));
        }
        String f = famille;
        return new ModelePvDto.Catalogue(sousType, famille,
                groupes.entrySet().stream().map(e -> new ModelePvDto.Groupe(e.getKey(), e.getValue())).toList(),
                CatalogueModelesPv.LISTES.stream().filter(l -> f == null || l.familles() == null || l.familles().contains(f))
                        .map(l -> new ModelePvDto.Liste(l.nom(), l.prefixe(), l.libelle())).toList(),
                CatalogueModelesPv.CLES.stream().map(c -> new ModelePvDto.Cle(c.cle(), c.libelle(), c.valeurs())).toList(),
                CatalogueModelesPv.FORMATS, CatalogueModelesPv.ALIAS);
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

    /** Copie le modèle et sa version en vigueur (à défaut sa dernière version), conditions et champs compris, en brouillon. */
    public ModelePvDto.Fiche dupliquer(Long id, ModelePvDto.DuplicationRequest r) {
        ModelePv source = modele(id);
        String code = r == null || r.code() == null || r.code().isBlank() ? codeLibre(source.getCode() + "-COPIE") : r.code();
        String libelle = r == null || r.libelle() == null || r.libelle().isBlank() ? source.getLibelle() + " (copie)" : r.libelle();
        ModelePv copie = nouveau(code, libelle, source.getNature());
        List<VersionModelePv> vs = versions.findByIdModeleOrderByNumeroDesc(id);
        VersionModelePv base = vs.stream().filter(v -> VersionModelePv.EN_VIGUEUR.equals(v.getEtat())).findFirst()
                .orElse(vs.isEmpty() ? null : vs.get(0));
        if (base != null) {
            VersionModelePv v = enregistrer(copie, base.getNomFichier(), base.getContenu());
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
        tracer(copie, null, "DUPLIQUE", "copie de " + source.getCode() + (base == null ? "" : ", version " + base.getNumero()));
        tracer(source, null, "DUPLIQUE", "copié en " + copie.getCode());
        return fiche(copie);
    }

    /** Dépose une version : contrôlée tout de suite, en brouillon avec son rapport (§B5). */
    public ModelePvDto.Version deposer(Long id, MultipartFile fichier) {
        ModelePv m = actif(id);
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Aucun fichier déposé.", "FICHIER_INVALIDE");
        }
        String nom = fichier.getOriginalFilename() == null ? "modele.docx" : fichier.getOriginalFilename();
        if (!nom.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new BadRequestException("Le modèle se dépose en Word (.docx) : « " + nom + " ».", "FICHIER_INVALIDE");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new BadRequestException("Le fichier dépasse 10 Mo.", "FICHIER_INVALIDE");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Fichier illisible : " + e.getMessage(), "FICHIER_INVALIDE");
        }
        VersionModelePv v = enregistrer(m, nom, contenu);
        ModelePvDto.Rapport r = controlerEtEnregistrer(m, v);
        tracer(m, v.getNumero(), "DEPOSEE", nom + (r.bloquant() ? ", contrôle bloquant (" + r.nbBloquants() + ")" : ", contrôle sans blocage"));
        return dto(v);
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
        conditions.deleteByIdVersion(v.getIdVersion());
        conditions.flush();
        int ordre = 0;
        for (ModelePvDto.Condition c : liste == null ? List.<ModelePvDto.Condition>of() : liste) {
            conditions.save(condition(v, c.nom().trim(), c.expression().trim(), c.libelle(), ordre++));
        }
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
        controlerEtEnregistrer(m, v);
        tracer(m, numero, "CHAMPS", codes.isEmpty() ? "aucun" : String.join(", ", codes));
        return dto(v);
    }

    /**
     * L'aperçu sur un dossier réel, <strong>sans rien enregistrer</strong> au dossier ni au modèle : le Word rempli, ou son PDF
     * (409 {@code CONVERSION_INDISPONIBLE} sans Word sur le serveur). Toute version s'essaie, brouillon compris.
     */
    @Transactional(readOnly = true)
    public byte[] apercu(Long id, Integer numero, Integer idDossier, String avis, boolean pdf, Map<String, String> saisis) {
        VersionModelePv v = version(modele(id), numero);
        if (avis != null && !avis.isBlank() && !List.of("FAV", "FAVR", "DEF", "NSP").contains(avis)) {
            throw new BadRequestException("Avis inconnu : « " + avis + " » (FAV, FAVR, DEF, NSP).", "AVIS_INCONNU");
        }
        ModelePvWord.Contexte ctx = contextes.pour(idDossier, avis == null || avis.isBlank() ? null : avis, saisis);
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
        if (motif == null || motif.isBlank()) {
            throw new BadRequestException("La mise en vigueur demande un motif.", "MOTIF_REQUIS");
        }
        ModelePvDto.Rapport r = controlerEtEnregistrer(m, v);
        if (r.bloquant()) {
            throw new BusinessRuleException("Le rapport de contrôle de la version " + numero + " est bloquant (" + r.nbBloquants()
                    + ") : corrigez le Word et déposez-le de nouveau.", "RAPPORT_BLOQUANT", null, Map.of("nbBloquants", r.nbBloquants()));
        }
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
        versions.save(v);
        tracer(m, numero, "MISE_EN_VIGUEUR", motif.trim());
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
        return fiche(m);
    }

    /**
     * Retire le modèle. S'il sert un sous-type par une version en vigueur, 409 {@code MODELE_EN_SERVICE} sans motif ; avec un motif,
     * il est archivé et détaché : ses sous-types n'ont plus de modèle (leurs PV, plus de document).
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
        rattachements.deleteAll(servis);
        m.setEtat(ModelePv.ARCHIVE);
        m.setArchiveLe(LocalDateTime.now());
        m.setMotifArchivage(motif == null || motif.isBlank() ? null : motif.trim());
        modeles.save(m);
        tracer(m, null, "ARCHIVE", (motif == null || motif.isBlank() ? "sans motif" : motif.trim())
                + (servis.isEmpty() ? "" : ", détaché de " + servis.stream().map(RattachementModelePv::getIdSousType).toList()));
        return fiche(m);
    }

    // ───────────────────────────────────────────── contrôle (§B5) ─────────────────────────────────────────────

    /** Contrôle la version sur ses conditions et champs actuels, et enregistre le rapport. */
    private ModelePvDto.Rapport controlerEtEnregistrer(ModelePv m, VersionModelePv v) {
        Set<String> codesChamps = new LinkedHashSet<>();
        champs.findByIdVersionOrderByOrdreAsc(v.getIdVersion()).forEach(c -> codesChamps.add(c.getCode()));
        ModelePvDto.Rapport r = controler(v.getContenu(), m.getNature(), declarees(v), codesChamps);
        v.setRapport(json.writeValueAsString(r));
        v.setBloquant(r.bloquant());
        versions.save(v);
        return r;
    }

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
        Set<String> variables = new LinkedHashSet<>();
        Set<String> listes = new LinkedHashSet<>();
        Set<String> conditionsUtilisees = new LinkedHashSet<>();
        Set<String> champsUtilises = new LinkedHashSet<>();
        int repetees = 0;
        boolean blocsSains = rel.defauts().stream().noneMatch(d -> d.code().startsWith("BLOC_"));
        for (ModelePvWord.Marqueur mk : rel.marqueurs()) {
            switch (mk.type()) {
                case "VARIABLE", "ALIAS" -> {
                    CatalogueModelesPv.Variable var = CatalogueModelesPv.variable(mk.code()).orElse(null);
                    if (var == null) {
                        String s = CatalogueModelesPv.suggestion(mk.code()).orElse(null);
                        pts.add(point("BLOQUANT", "VARIABLE_INCONNUE", "Variable inconnue " + mk.brut()
                                + (s == null ? "" : " — vouliez-vous {{" + s + "}} ?"), mk.ou(), s == null ? null : "{{" + s + "}}"));
                        continue;
                    }
                    if (mk.format() != null && !CatalogueModelesPv.FORMATS.contains(mk.format())) {
                        pts.add(point("BLOQUANT", "FORMAT_INCONNU", "Format inconnu « " + mk.format() + " » dans " + mk.brut() + " "
                                + CatalogueModelesPv.FORMATS, mk.ou(), null));
                    }
                    if (var.liste() != null && !var.liste().equals(mk.liste())) {
                        pts.add(point("BLOQUANT", "VARIABLE_HORS_LISTE", mk.brut() + " ne vaut que sur une ligne de tableau répétée : "
                                + "écrivez {{POUR:" + var.liste() + "}} dans la première cellule de la ligne.", mk.ou(), null));
                    }
                    if ("ALIAS".equals(mk.type())) {
                        pts.add(point("AVERTISSEMENT", "ANCIEN_MARQUEUR", "Ancien marqueur " + mk.brut() + " lu comme {{" + var.code() + "}}",
                                mk.ou(), "{{" + var.code() + "}}"));
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
                                + " » n'est pas déclaré dans les champs saisis de la version.", mk.ou(), null));
                    }
                }
                case "SI" -> {
                    conditionsUtilisees.add(mk.code());
                    if (!declarees.containsKey(mk.code())) {
                        blocsSains = false;
                        pts.add(point("BLOQUANT", "CONDITION_NON_DECLAREE", mk.brut() + " : la condition « " + mk.code()
                                + " » n'est pas déclarée pour cette version.", mk.ou(), null));
                    }
                }
                case "POUR" -> {
                    if (CatalogueModelesPv.liste(mk.code()).isEmpty()) {
                        pts.add(point("BLOQUANT", "LISTE_INCONNUE", "Liste inconnue " + mk.brut() + " "
                                + CatalogueModelesPv.LISTES.stream().map(CatalogueModelesPv.Liste::nom).toList(), mk.ou(), null));
                    } else if (!mk.premiereCellule() || !mk.code().equals(mk.liste())) {
                        pts.add(point("BLOQUANT", "POUR_MAL_PLACE", mk.brut() + " s'écrit dans la première cellule de la ligne de tableau "
                                + "à répéter.", mk.ou(), null));
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
                        + "{{SI:" + nom + "}} ne l'emploie.", null, null));
            }
        }
        for (String code : codesChamps) {
            if (!champsUtilises.contains(code)) {
                pts.add(point("AVERTISSEMENT", "CHAMP_INUTILISE", "Le champ « " + code + " » est déclaré mais {{SAISI:" + code
                        + "}} n'apparaît pas dans le Word.", null, null));
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
                    "{{" + code + "}}"));
        }
        if (manquantes.isEmpty()) {
            pts.add(point("OK", "SECTIONS", "PV".equals(nature) ? "Sections attendues : en-tête, identification, avis, signatures"
                    : "Section attendue : identification du dossier", null, null));
        }
        pts.add(point("OK", "VARIABLES", variables.size() + " variable(s) reconnue(s)" + (repetees == 0 ? ""
                : ", dont " + repetees + " répétée(s) (" + String.join(", ", listes) + ")"), null, null));
        if (blocsSains) {
            pts.add(point("OK", "BLOCS", conditionsUtilisees.size() + " bloc(s) conditionnel(s), chacun fermé et déclaré", null, null));
        }
        essai(contenu, declarees, pts);
        return rapport(pts, new ArrayList<>(variables), new ArrayList<>(conditionsUtilisees), new ArrayList<>(champsUtilises));
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
        return new ModelePvDto.Point(niveau, code, message, ou, suggestion);
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

    private VersionModelePv enregistrer(ModelePv m, String nom, byte[] contenu) {
        List<VersionModelePv> vs = versions.findByIdModeleOrderByNumeroDesc(m.getIdModele());
        VersionModelePv v = new VersionModelePv();
        v.setIdModele(m.getIdModele());
        v.setNumero(vs.isEmpty() ? 1 : vs.get(0).getNumero() + 1);
        v.setNomFichier(nom);
        v.setContenu(contenu);
        v.setEmpreinte(sha256(contenu));
        v.setTaille((long) contenu.length);
        v.setEtat(VersionModelePv.BROUILLON);
        v.setBloquant(true);
        v.setDeposeeLe(LocalDateTime.now());
        v.setDeposeePar(CurrentUser.login().orElse(null));
        return versions.saveAndFlush(v);
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
                        .toList());
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
