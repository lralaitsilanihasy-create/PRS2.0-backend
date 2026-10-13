package cnm.prs.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.InstantaneDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierInstantane;
import cnm.prs.entity.FicheMarche;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.MarcheMapper;
import cnm.prs.repository.ActeGestionRepository;
import cnm.prs.repository.AmiRepository;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.DossierInstantaneRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EvaluationRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.SansSuiteRepository;
import cnm.prs.repository.SeanceRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ R4 (manuel révisé pour l'application, lot 1, §B5 ; V104, 2026-10-13) — <strong>ce que le contrôleur voit</strong> : à la
 * soumission d'un dossier (et à chaque resoumission), le serveur fige, écran par écran, le JSON du DTO de lecture tel qu'il est
 * servi à cet instant, avec son empreinte SHA-256 ; les profils CNM (Membre, Chef de commission, Président, Vérificateur,
 * Assistant) et les lecteurs actuels (PRMP, UGPM) le relisent ensuite, figé, à la place des pièces retirées (R2).
 * <ul>
 *   <li>dossier de mise en concurrence ({@code DMC}) : {@code PLAN}, {@code AVIS} (le projet), {@code AMI} (consultation), {@code CAO} ;</li>
 *   <li>dossier de marché ({@code DDM}) : {@code SEANCE}, {@code EVALUATION}, {@code ATTRIBUTION}, {@code CAO}, {@code AVIS}, {@code PLAN} ;</li>
 *   <li>acte de gestion ({@code DGC}) : {@code ATTRIBUTION}, {@code ACTE} ; déclaration sans suite ({@code DSS}) : {@code DSS}, {@code ATTRIBUTION}.</li>
 * </ul>
 * Un écran dont la donnée n'existe pas encore (séance non ouverte, AMI non préparé…) n'est pas figé : son existence est vérifiée
 * avant toute lecture, pour qu'aucune exception ne traverse la transaction de la soumission. Les lectures sont celles des écrans
 * vivants (mêmes services, mêmes gardes : la PRMP qui soumet en est lectrice).
 */
@Service
public class InstantaneService {

    private static final Logger log = LoggerFactory.getLogger(InstantaneService.class);

    public static final String SEANCE = "SEANCE";
    public static final String EVALUATION = "EVALUATION";
    public static final String AMI = "AMI";
    public static final String ATTRIBUTION = "ATTRIBUTION";
    public static final String CAO = "CAO";
    public static final String AVIS = "AVIS";
    public static final String PLAN = "PLAN";
    public static final String ACTE = "ACTE";
    public static final String DSS = "DSS";
    public static final Set<String> ECRANS = Set.of(SEANCE, EVALUATION, AMI, ATTRIBUTION, CAO, AVIS, PLAN, ACTE, DSS);

    static final String FAMILLE_DMC = "DMC";
    static final String FAMILLE_DDM = "DDM";
    static final String FAMILLE_DSS = "DSS";

    private static final DateTimeFormatter JOUR_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final DossierInstantaneRepository repository;
    private final DossierRepository dossiers;
    private final DossierMecRepository dmcs;
    private final AttributionRepository attributions;
    private final ActeGestionRepository actes;
    private final SansSuiteRepository sansSuites;
    private final FicheMarcheRepository fiches;
    private final SeanceRepository seances;
    private final EvaluationRepository evaluations;
    private final AmiRepository amis;
    private final MarcheRepository marches;
    private final ValeursPpmService valeursPpm;
    private final ObjectMapper mapper;
    private final GenerateurDocumentsFiche generateur;
    // Les écrans vivants : fournis à la demande (ils dépendent, directement ou non, du service du dossier qui appelle le figement).
    private final ObjectProvider<DossierService> dossierService;
    private final ObjectProvider<SeanceService> seance;
    private final ObjectProvider<EvaluationService> evaluation;
    private final ObjectProvider<AmiService> ami;
    private final ObjectProvider<AmiPreselectionService> preselection;
    private final ObjectProvider<AttributionService> attribution;
    private final ObjectProvider<CaoService> cao;
    private final ObjectProvider<AvisSpecifiqueService> avis;
    private final ObjectProvider<LettreInvitationService> lettres;
    private final ObjectProvider<DocumentsFicheMarcheService> documents;
    private final ObjectProvider<SansSuiteService> sansSuite;
    private final ObjectProvider<ActesGestionService> actesGestion;

    public InstantaneService(DossierInstantaneRepository repository, DossierRepository dossiers, DossierMecRepository dmcs,
            AttributionRepository attributions, ActeGestionRepository actes, SansSuiteRepository sansSuites, FicheMarcheRepository fiches,
            SeanceRepository seances, EvaluationRepository evaluations, AmiRepository amis, MarcheRepository marches,
            ValeursPpmService valeursPpm, ObjectMapper mapper, GenerateurDocumentsFiche generateur,
            ObjectProvider<DossierService> dossierService, ObjectProvider<SeanceService> seance, ObjectProvider<EvaluationService> evaluation,
            ObjectProvider<AmiService> ami, ObjectProvider<AmiPreselectionService> preselection, ObjectProvider<AttributionService> attribution,
            ObjectProvider<CaoService> cao, ObjectProvider<AvisSpecifiqueService> avis, ObjectProvider<LettreInvitationService> lettres,
            ObjectProvider<DocumentsFicheMarcheService> documents, ObjectProvider<SansSuiteService> sansSuite,
            ObjectProvider<ActesGestionService> actesGestion) {
        this.repository = repository;
        this.dossiers = dossiers;
        this.dmcs = dmcs;
        this.attributions = attributions;
        this.actes = actes;
        this.sansSuites = sansSuites;
        this.fiches = fiches;
        this.seances = seances;
        this.evaluations = evaluations;
        this.amis = amis;
        this.marches = marches;
        this.valeursPpm = valeursPpm;
        this.mapper = mapper;
        this.generateur = generateur;
        this.dossierService = dossierService;
        this.seance = seance;
        this.evaluation = evaluation;
        this.ami = ami;
        this.preselection = preselection;
        this.attribution = attribution;
        this.cao = cao;
        this.avis = avis;
        this.lettres = lettres;
        this.documents = documents;
        this.sansSuite = sansSuite;
        this.actesGestion = actesGestion;
    }

    // ------------------------------------------------------------------ figement

    /**
     * Fige les écrans du dossier sous le numéro de soumission suivant (1, puis 2…). À appeler dans la transaction de la soumission,
     * une fois le statut posé. Un écran sans donnée n'est pas écrit.
     *
     * @return les instantanés écrits
     */
    @Transactional
    public List<DossierInstantane> figer(Dossier dossier) {
        Integer precedent = repository.maxNumero(dossier.getIdDossier());
        int numero = (precedent == null ? 0 : precedent) + 1;
        LocalDateTime maintenant = LocalDateTime.now();
        String par = CurrentUser.ref().or(CurrentUser::login).orElse(null);
        Long idDmc = idDmc(dossier);
        List<DossierInstantane> out = new ArrayList<>();
        for (String ecran : ecrans(dossier.getIdTypeDossier())) {
            Object contenu = contenu(ecran, dossier, idDmc);
            if (contenu == null) {
                continue;
            }
            String json = mapper.writeValueAsString(contenu);
            out.add(repository.save(new DossierInstantane(null, dossier.getIdDossier(), numero, ecran, json, empreinte(json), maintenant, par)));
        }
        log.info("[INSTANTANE] dossier={} soumission n° {} : {} écran(s) figé(s) ({})", dossier.getIdDossier(), numero, out.size(),
                out.stream().map(DossierInstantane::getEcran).toList());
        return out;
    }

    /** Les écrans d'une famille de dossier, dans l'ordre de lecture. */
    static List<String> ecrans(String famille) {
        if (famille == null) {
            return List.of();
        }
        return switch (famille) {
            case FAMILLE_DMC -> List.of(PLAN, AVIS, AMI, CAO);
            case FAMILLE_DDM -> List.of(SEANCE, EVALUATION, ATTRIBUTION, CAO, AVIS, PLAN);
            case ActesGestionService.FAMILLE_DGC -> List.of(ATTRIBUTION, ACTE);
            case FAMILLE_DSS -> List.of(DSS, ATTRIBUTION);
            default -> List.of();
        };
    }

    /** La procédure du dossier : la fiche (DMC), l'attribution du marché (DDM, acte de gestion), la demande de sans suite (DSS). */
    Long idDmc(Dossier d) {
        if (d.getIdDmc() != null) {
            return d.getIdDmc();
        }
        Integer id = d.getIdDossier();
        if (id == null || d.getIdTypeDossier() == null) {
            return null;
        }
        return switch (d.getIdTypeDossier()) {
            case FAMILLE_DDM -> attributions.findFirstByIdDossier(id).map(a -> a.getIdDmc()).orElse(null);
            case ActesGestionService.FAMILLE_DGC -> actes.findByIdDossier(id).map(a -> a.getIdDossierMarche())
                    .flatMap(m -> attributions.findFirstByIdDossier(m)).map(a -> a.getIdDmc()).orElse(null);
            case FAMILLE_DSS -> sansSuites.findFirstByIdDossier(id).map(s -> s.getIdDmc()).orElse(null);
            default -> null;
        };
    }

    /** Le DTO de lecture d'un écran, ou {@code null} si sa donnée n'existe pas (rien n'est figé). */
    Object contenu(String ecran, Dossier d, Long idDmc) {
        boolean fiche = idDmc != null && fiches.findFirstByIdDmcOrderByNumeroVersionDesc(idDmc).isPresent();
        return switch (ecran) {
            case PLAN -> idDmc == null ? null : plan(idDmc);
            case AVIS -> !fiche ? null : avis(idDmc);
            case CAO -> !fiche ? null : cao.getObject().lire(idDmc);
            case AMI -> idDmc == null ? null : amis.findById(idDmc).map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ami", ami.getObject().lire(idDmc));
                m.put("preselection", cnm.prs.entity.Ami.PUBLIE.equals(a.getEtat()) || cnm.prs.entity.Ami.INFRUCTUEUX.equals(a.getEtat())
                        ? preselection.getObject().lire(idDmc) : null);
                return (Object) m;
            }).orElse(null);
            case SEANCE -> idDmc != null && seances.existsById(idDmc) ? seance.getObject().lire(idDmc) : null;
            case EVALUATION -> idDmc != null && evaluations.existsById(idDmc) ? evaluation.getObject().lire(idDmc) : null;
            case ATTRIBUTION -> idDmc != null && evaluations.existsById(idDmc) ? attribution.getObject().lire(idDmc) : null;
            case ACTE -> actes.findByIdDossier(d.getIdDossier()).map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("acte", actesGestion.getObject().acte(d.getIdDossier()));
                m.put("marche", actesGestion.getObject().marche(a.getIdDossierMarche()));
                return (Object) m;
            }).orElse(null);
            case DSS -> !fiche ? null : sansSuite.getObject().lire(idDmc);
            default -> null;
        };
    }

    /** La ligne du plan et ses valeurs (celles que la fiche et les documents reprennent). */
    private Map<String, Object> plan(Long idDmc) {
        Integer idDetail = dmcs.findById(idDmc).map(x -> x.getIdDetail()).orElse(null);
        if (idDetail == null) {
            return null;
        }
        ValeursPpmService.ValeursPpm v = valeursPpm.lire(idDetail);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idDmc", idDmc);
        m.put("ligne", marches.findById(idDetail).map(MarcheMapper::toDto).orElse(null));
        m.put("versionPpm", v.versionPpm());
        m.put("valeurs", v.valeurs());
        m.put("dates", v.dates());
        return m;
    }

    /** L'écran de l'avis et des lettres : disponibilités, publications imprimées, projets (R3b). */
    private Map<String, Object> avis(Long idDmc) {
        List<FicheMarche> versions = fiches.findByIdDmcOrderByNumeroVersionAsc(idDmc);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("avis", avis.getObject().disponibilite(idDmc));
        m.put("lettres", lettres.getObject().disponibilite(idDmc));
        m.put("documents", documents.getObject().listerAvis(versions));
        m.put("projets", documents.getObject().listerProjets(versions));
        return m;
    }

    // ------------------------------------------------------------------ lecture

    /** Les instantanés du dossier (même garde de lecture que le dossier). */
    @Transactional(readOnly = true)
    public List<InstantaneDto> lister(Integer idDossier) {
        exigerDossierVisible(idDossier);
        return repository.findByIdDossierOrderByNumeroSoumissionAscEcranAsc(idDossier).stream().map(InstantaneService::dto).toList();
    }

    /**
     * Le JSON figé d'un écran : de la soumission {@code soumission}, ou de la dernière. 400 {@code ECRAN_INCONNU} ; 404 sans instantané.
     */
    @Transactional(readOnly = true)
    public JsonNode contenu(Integer idDossier, String ecran, Integer soumission) {
        return mapper.readTree(instantane(idDossier, ecran, soumission).getContenu());
    }

    /** L'export PDF d'un écran figé, pour l'archivage avec le PV. */
    @Transactional(readOnly = true)
    public byte[] pdf(Integer idDossier, String ecran, Integer soumission) {
        DossierInstantane i = instantane(idDossier, ecran, soumission);
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        String reference = d.getRefeDossier() == null || d.getRefeDossier().isBlank() ? "dossier " + idDossier : d.getRefeDossier();
        DocumentLibre doc = InstantaneDocument.rendre("Instantané — " + i.getEcran(), reference + " — figé à la soumission n° "
                + i.getNumeroSoumission() + " du " + i.getFigeLe().format(JOUR_HEURE), mapper.readTree(i.getContenu()),
                "Figé à la soumission n° " + i.getNumeroSoumission() + " du " + i.getFigeLe().format(JOUR_HEURE) + " — empreinte SHA-256 "
                        + i.getEmpreinte());
        return generateur.generer(doc).stream().filter(f -> "pdf".equals(f.extension())).findFirst()
                .orElseThrow(() -> new IllegalStateException("L'instantané n'a pas été rendu en pdf.")).contenu();
    }

    /** Le nom du fichier d'export : {@code INSTANTANE_<ecran>_<dossier>_s<n>.pdf}. */
    public String nomPdf(Integer idDossier, String ecran, Integer soumission) {
        DossierInstantane i = instantane(idDossier, ecran, soumission);
        return "INSTANTANE_" + i.getEcran() + "_" + idDossier + "_s" + i.getNumeroSoumission() + ".pdf";
    }

    private DossierInstantane instantane(Integer idDossier, String ecran, Integer soumission) {
        exigerDossierVisible(idDossier);
        String e = ecran == null ? "" : ecran.trim().toUpperCase(java.util.Locale.ROOT);
        if (!ECRANS.contains(e)) {
            throw new BadRequestException("Écran inconnu : « " + ecran + " » (attendu : " + String.join(", ", ECRANS.stream().sorted().toList())
                    + ").", "ECRAN_INCONNU");
        }
        return (soumission == null ? repository.findFirstByIdDossierAndEcranOrderByNumeroSoumissionDesc(idDossier, e)
                : repository.findFirstByIdDossierAndEcranAndNumeroSoumission(idDossier, e, soumission))
                .orElseThrow(() -> new ResourceNotFoundException("Aucun instantané " + e + " pour le dossier " + idDossier
                        + (soumission == null ? "" : " à la soumission n° " + soumission) + "."));
    }

    private void exigerDossierVisible(Integer idDossier) {
        if (!dossiers.existsById(idDossier)) {
            throw new ResourceNotFoundException("Dossier introuvable : " + idDossier);
        }
        dossierService.getObject().controlerVisibilite(idDossier);
    }

    static InstantaneDto dto(DossierInstantane i) {
        return new InstantaneDto(i.getIdInstantane(), i.getNumeroSoumission(), i.getEcran(), i.getFigeLe(), i.getFigePar(), i.getEmpreinte(),
                i.getContenu() == null ? 0 : i.getContenu().length());
    }

    public static String empreinte(String json) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
