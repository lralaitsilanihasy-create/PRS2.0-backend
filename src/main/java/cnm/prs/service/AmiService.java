package cnm.prs.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.AmiDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.Ami;
import cnm.prs.entity.AmiExpression;
import cnm.prs.entity.AmiExpressionPiece;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Entreprise;
import cnm.prs.entity.EvaluationJournal;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AmiExpressionPieceRepository;
import cnm.prs.repository.AmiExpressionRepository;
import cnm.prs.repository.AmiRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.EntrepriseRepository;
import cnm.prs.repository.EvaluationJournalRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.security.CurrentUser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ <strong>L'appel à manifestation d'intérêt en ligne</strong>, tranche AMI-a (demande front du 2026-10-07,
 * {@code demande-backend-2026-10-07-ami-pi.md}, §B1, §B2 ; V82 ; loi n° 2016-055, art. 32 et 42-II) — pour une procédure de
 * prestations intellectuelles :
 * <ul>
 *   <li>la PRMP (ou son UGPM) prépare l'AMI : critères de sélection pondérés (sur 100), pièces attendues, note minimale de
 *   qualification, nombre de candidats à retenir (six), date limite ; l'avis se prévisualise ;</li>
 *   <li>la PRMP le <strong>publie</strong> en déclarant ses supports (art. 32-III) : l'AMI est figé, l'avis produit et signé, et il paraît
 *   dans la liste publique des AMI ; ou elle déclare la <strong>dispense de publicité</strong>, avec son motif (Q1 : le seuil reste au
 *   juriste), et la liste restreinte se saisit comme avant ;</li>
 *   <li>les candidats déposent, remplacent ou retirent leur <strong>expression d'intérêt</strong> avant la date limite : en clair, sans
 *   scellement, mais <strong>illisible de l'administration avant la date limite</strong> (arbitrage Q2) ; l'accusé porte une empreinte.</li>
 * </ul>
 * L'évaluation par la commission, la liste restreinte, le rapport et les invitations suivent en tranche AMI-b. Le journal de l'AMI est
 * celui de la procédure (registre de l'évaluation).
 */
@Service
@Transactional
public class AmiService {

    public static final int NOMBRE_RETENUS = 6;
    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private final AmiRepository amis;
    private final AmiExpressionRepository expressions;
    private final AmiExpressionPieceRepository piecesRepository;
    private final FicheMarcheService fiches;
    private final DossierMecRepository dmcRepository;
    private final ValeursPpmService valeursPpm;
    private final GenerateurDocumentsFiche generateur;
    private final EntrepriseRepository entreprises;
    private final CompteCandidatRepository candidats;
    private final PrmpRepository prmpRepository;
    private final NotificationService notifications;
    private final ParametreService parametres;
    private final ParametresInternesService internes;
    private final EvaluationJournalRepository journal;
    private final ObjectMapper mapper;
    private final Clock horloge;

    public AmiService(AmiRepository amis, AmiExpressionRepository expressions, AmiExpressionPieceRepository piecesRepository,
            FicheMarcheService fiches, DossierMecRepository dmcRepository, ValeursPpmService valeursPpm, GenerateurDocumentsFiche generateur,
            EntrepriseRepository entreprises, CompteCandidatRepository candidats, PrmpRepository prmpRepository,
            NotificationService notifications, ParametreService parametres, ParametresInternesService internes,
            EvaluationJournalRepository journal, ObjectMapper mapper, Clock horloge) {
        this.amis = amis;
        this.expressions = expressions;
        this.piecesRepository = piecesRepository;
        this.fiches = fiches;
        this.dmcRepository = dmcRepository;
        this.valeursPpm = valeursPpm;
        this.generateur = generateur;
        this.entreprises = entreprises;
        this.candidats = candidats;
        this.prmpRepository = prmpRepository;
        this.notifications = notifications;
        this.parametres = parametres;
        this.internes = internes;
        this.journal = journal;
        this.mapper = mapper;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ §B1 l'AMI, côté administration

    /** L'AMI de la procédure : PRMP, UGPM, membres de la commission ; 404 tant qu'il n'est pas préparé. */
    @Transactional(readOnly = true)
    public AmiDto lire(Long idDmc) {
        exigerLecteur(idDmc);
        return dto(exiger(idDmc));
    }

    /**
     * Prépare (crée ou modifie) l'AMI : PRMP ou UGPM ; 400 {@code CRITERES_OBLIGATOIRES}, {@code PONDERATION_INVALIDE} (poids &gt; 0,
     * somme = 100), {@code NOTE_MINIMALE_INVALIDE} (0 à 100), {@code DATE_LIMITE_INVALIDE} (passée), {@code NOMBRE_RETENUS_INVALIDE} ;
     * 409 {@code CATEGORIE_SANS_AMI} (fiche hors prestations intellectuelles), {@code AMI_PUBLIE}, {@code AMI_DISPENSE}.
     */
    public AmiDto preparer(Long idDmc, AmiDto.AmiRequest r) {
        FicheMarcheDto fiche = exigerPreparateur(idDmc);
        Ami a = amis.findById(idDmc).orElse(null);
        if (a != null) {
            exigerBrouillon(a);
        }
        if (r == null) {
            throw new BadRequestException("Le corps de la préparation est vide.", "CRITERES_OBLIGATOIRES");
        }
        List<AmiDto.Critere> criteres = criteres(r.criteres());
        if (r.noteMinimale() != null && (r.noteMinimale().signum() < 0 || r.noteMinimale().compareTo(CENT) > 0)) {
            throw new BadRequestException("La note minimale de qualification se situe entre 0 et 100.", "NOTE_MINIMALE_INVALIDE");
        }
        if (r.nombreRetenus() != null && (r.nombreRetenus() < 1 || r.nombreRetenus() > 20)) {
            throw new BadRequestException("Le nombre de candidats à retenir se situe entre 1 et 20 (six, art. 42-II).", "NOMBRE_RETENUS_INVALIDE");
        }
        if (r.dateLimite() != null && !r.dateLimite().isAfter(maintenant())) {
            throw new BadRequestException("La date limite de dépôt des expressions d'intérêt doit être à venir.", "DATE_LIMITE_INVALIDE");
        }
        LocalDateTime maintenant = maintenant();
        if (a == null) {
            a = new Ami();
            a.setIdDmc(idDmc);
            a.setEtat(Ami.BROUILLON);
            a.setCreeLe(maintenant);
            a.setCreePar(acteur());
        }
        a.setObjet(fiche.getDesignationMarche());
        a.setDateLimite(r.dateLimite());
        a.setCriteres(mapper.writeValueAsString(criteres));
        a.setPieces(mapper.writeValueAsString(nettoyees(r.pieces())));
        a.setNoteMinimale(r.noteMinimale());
        a.setNombreRetenus(r.nombreRetenus() == null ? NOMBRE_RETENUS : r.nombreRetenus());
        a.setModifieLe(maintenant);
        amis.save(a);
        tracer(idDmc, "AMI_PREPARE", criteres.size() + " critère(s), " + nettoyees(r.pieces()).size() + " pièce(s) attendue(s)"
                + (r.dateLimite() == null ? "" : ", date limite le " + r.dateLimite().format(HORODATAGE)));
        return dto(a);
    }

    /**
     * L'avis à manifestation d'intérêt, en PDF (ou Word) : publié, l'avis signé ; en préparation, un <strong>projet</strong> produit à la
     * demande. PRMP, UGPM, commission ; 404 sans AMI ; 409 {@code AMI_DISPENSE}.
     */
    @Transactional(readOnly = true)
    public byte[] avis(Long idDmc, boolean docx) {
        exigerLecteur(idDmc);
        Ami a = exiger(idDmc);
        if (Ami.DISPENSE.equals(a.getEtat())) {
            throw new BusinessRuleException("L'AMI est dispensé de publicité : il n'a pas d'avis.", "AMI_DISPENSE");
        }
        if (Ami.PUBLIE.equals(a.getEtat())) {
            return docx ? a.getAvisDocx() : a.getAvisPdf();
        }
        return fichier(generateur.generer(document(a, null)), docx);
    }

    /**
     * Publie l'AMI : PRMP seule ; les supports déclarés (au moins un, avec sa date ; 400 {@code PUBLICATION_OBLIGATOIRE}), les critères
     * et la date limite à venir exigés (400 {@code CRITERES_OBLIGATOIRES}, {@code DATE_LIMITE_INVALIDE}) ; 409 {@code AMI_PUBLIE},
     * {@code AMI_DISPENSE}. L'AMI est figé, l'avis produit et signé électroniquement par la PRMP.
     */
    public AmiDto publier(Long idDmc, AmiDto.PublicationRequest r) {
        exigerPrmp(idDmc);
        Ami a = exiger(idDmc);
        exigerBrouillon(a);
        List<AmiDto.Publication> pubs = r == null || r.publications() == null ? List.of()
                : r.publications().stream().filter(p -> p != null && nettoyer(p.support()) != null && p.date() != null)
                        .map(p -> new AmiDto.Publication(nettoyer(p.support()), p.date(), nettoyer(p.reference()))).toList();
        if (pubs.isEmpty()) {
            throw new BadRequestException("Déclarez au moins un support de publication, avec sa date (art. 32-III).", "PUBLICATION_OBLIGATOIRE");
        }
        if (lireCriteres(a).isEmpty()) {
            throw new BadRequestException("L'AMI se publie avec ses critères de sélection.", "CRITERES_OBLIGATOIRES");
        }
        if (a.getDateLimite() == null || !a.getDateLimite().isAfter(maintenant())) {
            throw new BadRequestException("L'AMI se publie avec une date limite à venir.", "DATE_LIMITE_INVALIDE");
        }
        a.setPublications(mapper.writeValueAsString(pubs));
        a.setPublieLe(maintenant());
        a.setPubliePar(acteur());
        a.setEtat(Ami.PUBLIE);
        List<GenerateurDocumentsFiche.Fichier> f = generateur.generer(document(a, nomPrmp()));
        a.setAvisPdf(fichier(f, false));
        a.setAvisDocx(fichier(f, true));
        a.setAvisProduitLe(a.getPublieLe());
        amis.save(a);
        tracer(idDmc, "AMI_PUBLIE", "Avis publié (" + String.join(", ", pubs.stream().map(AmiDto.Publication::support).toList())
                + ") ; date limite le " + a.getDateLimite().format(HORODATAGE));
        return dto(a);
    }

    /**
     * Déclare la dispense de publicité (art. 42-II, sous le seuil réglementaire ; Q1 au juriste) : PRMP seule ; motif obligatoire (400
     * {@code MOTIF_OBLIGATOIRE}) ; 409 {@code CATEGORIE_SANS_AMI}, {@code AMI_PUBLIE}, {@code AMI_DISPENSE}. La liste restreinte se saisit
     * alors comme avant, aux lettres d'invitation.
     */
    public AmiDto dispenser(Long idDmc, AmiDto.DispenseRequest r) {
        exigerPrmp(idDmc);
        exigerCategorie(fiches.lire(idDmc));
        String motif = r == null ? null : nettoyer(r.motif());
        if (motif == null) {
            throw new BadRequestException("La dispense de publicité exige son motif.", "MOTIF_OBLIGATOIRE");
        }
        Ami a = amis.findById(idDmc).orElse(null);
        if (a != null) {
            exigerBrouillon(a);
        } else {
            a = new Ami();
            a.setIdDmc(idDmc);
            a.setCreeLe(maintenant());
            a.setCreePar(acteur());
        }
        a.setEtat(Ami.DISPENSE);
        a.setMotifDispense(motif);
        a.setModifieLe(maintenant());
        amis.save(a);
        tracer(idDmc, "AMI_DISPENSE", "Dispense de publicité : " + motif);
        return dto(a);
    }

    /** Les expressions d'intérêt déposées : lecteurs de l'AMI, <strong>après la date limite</strong> (Q2) ; 409 {@code LECTURE_FERMEE}. */
    @Transactional(readOnly = true)
    public List<AmiDto.Expression> expressionsDeposees(Long idDmc) {
        exigerLecteur(idDmc);
        Ami a = exigerLisible(idDmc);
        return expressions.findByIdDmcAndEtatOrderByNumeroAsc(a.getIdDmc(), AmiExpression.DEPOSEE).stream().map(this::expressionDto).toList();
    }

    /** Une pièce d'une expression : lecteurs de l'AMI, après la date limite ; 404. */
    @Transactional(readOnly = true)
    public AmiExpressionPiece piece(Long idDmc, String idExpression, Long idPiece) {
        exigerLecteur(idDmc);
        exigerLisible(idDmc);
        expressions.findById(idExpression).filter(e -> e.getIdDmc().equals(idDmc))
                .orElseThrow(() -> new ResourceNotFoundException("Expression d'intérêt introuvable : " + idExpression + "."));
        return piecesRepository.findById(idPiece).filter(p -> p.getIdExpression().equals(idExpression))
                .orElseThrow(() -> new ResourceNotFoundException("Pièce introuvable : " + idPiece + "."));
    }

    // ------------------------------------------------------------------ §B1 le public

    /** Les AMI publiés et encore ouverts, la date limite la plus proche d'abord (sans session). */
    @Transactional(readOnly = true)
    public List<AmiDto.AmiPublic> publics() {
        return amis.findByEtatOrderByDateLimiteAsc(Ami.PUBLIE).stream().filter(a -> a.getDateLimite().isAfter(maintenant()))
                .map(this::publicDto).toList();
    }

    /** Un AMI publié (ouvert ou clos), sans session ; 404 sinon. */
    @Transactional(readOnly = true)
    public AmiDto.AmiPublic publicDe(Long idDmc) {
        return publicDto(exigerPublie(idDmc));
    }

    /** L'avis publié, sans session ; 404 sinon. */
    @Transactional(readOnly = true)
    public byte[] avisPublic(Long idDmc, boolean docx) {
        Ami a = exigerPublie(idDmc);
        return docx ? a.getAvisDocx() : a.getAvisPdf();
    }

    // ------------------------------------------------------------------ §B2 le dépôt des expressions d'intérêt

    /**
     * Dépose (ou remplace) l'expression d'intérêt du candidat : multipart {@code expression} (JSON) et {@code fichiers} ; avant la date
     * limite ; chaque pièce attendue portée par un fichier (400 {@code PIECES_MANQUANTES}, avec {@code details.pieces}), PDF, JPEG ou
     * PNG ({@code FORMAT_INVALIDE}, 413) ; 400 {@code LETTRE_OBLIGATOIRE}, {@code FICHIER_INCONNU}, {@code EXPRESSION_ILLISIBLE} ; 404
     * AMI non publié ; 409 {@code DATE_LIMITE_DEPASSEE}, {@code ENTREPRISE_NON_DECLAREE}. Notification {@code AMI_EXPRESSION_DEPOSEE}
     * (l'accusé, par courriel).
     */
    public AmiDto.Expression deposer(String idCandidat, Long idDmc, String json, List<MultipartFile> fichiers) {
        Ami a = exigerPublie(idDmc);
        exigerOuvert(a);
        Entreprise e = entreprises.findByIdCandidat(idCandidat).orElseThrow(() -> new BusinessRuleException(
                "Déclarez d'abord votre entreprise (ou votre cabinet) dans votre espace.", "ENTREPRISE_NON_DECLAREE"));
        AmiDto.ExpressionRequest r;
        try {
            r = json == null || json.isBlank() ? null : mapper.readValue(json, AmiDto.ExpressionRequest.class);
        } catch (RuntimeException ex) {
            throw new BadRequestException("L'expression d'intérêt est illisible (JSON attendu dans la partie « expression »).", "EXPRESSION_ILLISIBLE");
        }
        if (r == null || nettoyer(r.lettre()) == null) {
            throw new BadRequestException("L'expression d'intérêt exige sa lettre de manifestation d'intérêt.", "LETTRE_OBLIGATOIRE");
        }
        Map<String, MultipartFile> parNom = new HashMap<>();
        if (fichiers != null) {
            fichiers.stream().filter(f -> f != null && !f.isEmpty()).forEach(f -> parNom.put(Objects.toString(f.getOriginalFilename(), ""), f));
        }
        Map<String, String> declarees = new LinkedHashMap<>();
        if (r.pieces() != null) {
            for (AmiDto.PieceDeclaree p : r.pieces()) {
                if (p != null && nettoyer(p.libelle()) != null && nettoyer(p.fichier()) != null) {
                    if (!parNom.containsKey(p.fichier().trim())) {
                        throw new BadRequestException("Aucun fichier envoyé ne s'appelle « " + p.fichier() + " ».", "FICHIER_INCONNU");
                    }
                    declarees.put(p.libelle().trim(), p.fichier().trim());
                }
            }
        }
        List<String> manquantes = lirePieces(a).stream().filter(l -> !declarees.containsKey(l)).toList();
        if (!manquantes.isEmpty()) {
            throw new BadRequestException("Pièce(s) attendue(s) manquante(s) : " + String.join(" ; ", manquantes) + ".", "PIECES_MANQUANTES",
                    Map.of("pieces", manquantes));
        }
        // Les fichiers se contrôlent avant toute écriture.
        int mo = parametres.candidats().tailleMaxPieceMo();
        List<AmiExpressionPiece> lues = new ArrayList<>();
        for (Map.Entry<String, String> d : declarees.entrySet()) {
            MultipartFile f = parNom.get(d.getValue());
            byte[] contenu;
            try {
                contenu = f.getBytes();
            } catch (IOException ex) {
                throw new BadRequestException("Lecture du fichier impossible : " + d.getValue() + ".", "FICHIER_ABSENT");
            }
            if (contenu.length > mo * 1024L * 1024L) {
                throw new PayloadTropVolumineuxException("Le fichier « " + d.getValue() + " » dépasse " + mo + " Mo.");
            }
            String format = EntrepriseCandidatService.format(contenu);
            if (format == null) {
                throw new BadRequestException("« " + d.getValue() + " » doit être un PDF, un JPEG ou un PNG (type lu sur le contenu).", "FORMAT_INVALIDE");
            }
            AmiExpressionPiece p = new AmiExpressionPiece();
            p.setLibelle(d.getKey().length() > 255 ? d.getKey().substring(0, 255) : d.getKey());
            String nom = d.getValue().replaceAll("[\\\\/]", "_");
            p.setNom(nom.length() > 255 ? nom.substring(nom.length() - 255) : nom);
            p.setFormat(format);
            p.setTaille((long) contenu.length);
            p.setEmpreinte(sha256(contenu));
            p.setContenu(contenu);
            lues.add(p);
        }
        Map<String, Object> contenu = new LinkedHashMap<>();
        contenu.put("lettre", r.lettre().trim());
        contenu.put("qualifications", nettoyer(r.qualifications()));
        contenu.put("references", r.references() == null ? List.of() : r.references());
        contenu.put("groupement", r.groupement() == null ? List.of() : r.groupement());
        String texte = mapper.writeValueAsString(contenu);
        StringBuilder aEmpreindre = new StringBuilder(texte);
        lues.forEach(p -> aEmpreindre.append('|').append(p.getLibelle()).append(':').append(p.getEmpreinte()));
        LocalDateTime maintenant = maintenant();
        AmiExpression precedente = expressions.findFirstByIdDmcAndIdCandidatAndEtat(idDmc, idCandidat, AmiExpression.DEPOSEE).orElse(null);
        AmiExpression x = new AmiExpression();
        x.setId(UUID.randomUUID().toString());
        if (precedente != null) {
            precedente.setEtat(AmiExpression.REMPLACEE);
            precedente.setRemplaceePar(x.getId());
            expressions.saveAndFlush(precedente);
        }
        x.setIdDmc(idDmc);
        x.setIdCandidat(idCandidat);
        x.setNif(e.getNif());
        x.setRaisonSociale(e.getRaisonSociale());
        x.setNumero((int) expressions.countByIdDmc(idDmc) + 1);
        x.setEtat(AmiExpression.DEPOSEE);
        x.setContenu(texte);
        x.setEmpreinte(sha256(aEmpreindre.toString().getBytes(StandardCharsets.UTF_8)));
        x.setDeposeeLe(maintenant);
        expressions.save(x);
        lues.forEach(p -> {
            p.setIdExpression(x.getId());
            piecesRepository.save(p);
        });
        tracer(idDmc, "AMI_EXPRESSION", "Expression d'intérêt n° " + x.getNumero() + " déposée par " + e.getRaisonSociale()
                + (precedente == null ? "" : " (remplace la n° " + precedente.getNumero() + ")"));
        CompteCandidat c = candidats.findById(idCandidat).orElse(null);
        notifications.emettreCandidat(TypeNotification.AMI_EXPRESSION_DEPOSEE, idCandidat, c == null ? null : c.getEmail(), idDmc.intValue(),
                TypeObjet.PROCEDURE, "Accusé de dépôt de votre expression d'intérêt", "Votre expression d'intérêt n° " + x.getNumero()
                        + " pour « " + Objects.toString(a.getObjet(), "l'AMI " + idDmc) + " » est déposée le " + maintenant.format(HORODATAGE)
                        + " ; empreinte " + x.getEmpreinte() + ". Vous pouvez la remplacer ou la retirer jusqu'au " + a.getDateLimite().format(HORODATAGE)
                        + ".");
        return expressionDto(x);
    }

    /** L'expression d'intérêt du candidat (la déposée) ; 404 sans dépôt. */
    @Transactional(readOnly = true)
    public AmiDto.Expression sienne(String idCandidat, Long idDmc) {
        return expressionDto(expressions.findFirstByIdDmcAndIdCandidatAndEtat(idDmc, idCandidat, AmiExpression.DEPOSEE)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune expression d'intérêt déposée pour cet AMI.")));
    }

    /** Une pièce de son expression, pour le candidat ; 404. */
    @Transactional(readOnly = true)
    public AmiExpressionPiece pieceDuCandidat(String idCandidat, Long idDmc, Long idPiece) {
        AmiExpression x = expressions.findFirstByIdDmcAndIdCandidatAndEtat(idDmc, idCandidat, AmiExpression.DEPOSEE)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune expression d'intérêt déposée pour cet AMI."));
        return piecesRepository.findById(idPiece).filter(p -> p.getIdExpression().equals(x.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Pièce introuvable : " + idPiece + "."));
    }

    /** Retire l'expression d'intérêt avant la date limite : 409 {@code DATE_LIMITE_DEPASSEE} ; 404 sans dépôt. */
    public void retirer(String idCandidat, Long idDmc) {
        Ami a = exigerPublie(idDmc);
        exigerOuvert(a);
        AmiExpression x = expressions.findFirstByIdDmcAndIdCandidatAndEtat(idDmc, idCandidat, AmiExpression.DEPOSEE)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune expression d'intérêt déposée pour cet AMI."));
        x.setEtat(AmiExpression.RETIREE);
        x.setRetireeLe(maintenant());
        expressions.save(x);
        tracer(idDmc, "AMI_RETRAIT", "Expression d'intérêt n° " + x.getNumero() + " retirée par " + x.getRaisonSociale());
    }

    // ------------------------------------------------------------------ l'avis (modèle provisoire)

    /**
     * L'avis à manifestation d'intérêt (modèle provisoire, sur celui de l'avis spécifique) : objet, autorité contractante, critères et
     * pondération, pièces attendues, liste restreinte de six, dépôt en ligne, date limite ; {@code signataire} nul : un projet.
     */
    private DocumentLibre document(Ami a, String signataire) {
        DossierMec dmc = dmcRepository.findById(a.getIdDmc()).orElseThrow();
        Map<String, String> plan = valeursPpm.lire(dmc.getIdDetail()).valeurs();
        String autorite = Objects.toString(plan.get("ENTITE"), "L'Autorité contractante");
        String reference = reference(a.getIdDmc());
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, plan.get("MINISTERE") == null ? autorite : plan.get("MINISTERE") + " — " + autorite));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, signataire == null ? "PROJET D'AVIS À MANIFESTATION D'INTÉRÊT"
                : "AVIS À MANIFESTATION D'INTÉRÊT"));
        if (reference != null) {
            el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "N° " + reference));
        }
        para(el, autorite + " invite les consultants qualifiés à manifester leur intérêt pour la mission suivante : "
                + Objects.toString(a.getObjet(), "—") + ".");
        para(el, "Une liste restreinte de " + a.getNombreRetenus() + " candidats sera établie à l'issue de l'évaluation des expressions "
                + "d'intérêt par la commission d'appel d'offres (art. 42-II de la loi n° 2016-055) ; les candidats retenus seront invités à "
                + "remettre une proposition technique et financière.");
        sous(el, "Critères de sélection");
        List<List<List<String>>> lignes = new ArrayList<>();
        lignes.add(List.of(List.of("Critère"), List.of("Points")));
        for (AmiDto.Critere c : lireCriteres(a)) {
            lignes.add(List.of(List.of(c.libelle() + (c.description() == null ? "" : " — " + c.description())),
                    List.of(c.poids().stripTrailingZeros().toPlainString())));
        }
        el.add(new DocumentLibre.Tableau(2, lignes));
        if (a.getNoteMinimale() != null) {
            para(el, "Note minimale de qualification : " + a.getNoteMinimale().stripTrailingZeros().toPlainString() + " points sur 100.");
        }
        List<String> pieces = lirePieces(a);
        if (!pieces.isEmpty()) {
            sous(el, "Pièces à joindre à l'expression d'intérêt");
            pieces.forEach(p -> para(el, "– " + p));
        }
        sous(el, "Dépôt");
        RemiseElectronique.Parametres re = parametres.remiseElectronique();
        para(el, "Les expressions d'intérêt sont déposées en ligne" + (re == null || re.plateformeUrl() == null ? "" : ", sur " + re.plateformeUrl())
                + ", au plus tard le " + (a.getDateLimite() == null ? "……" : a.getDateLimite().format(HORODATAGE)) + ". Elles ne sont lues "
                + "qu'après cette date ; un candidat peut remplacer ou retirer la sienne jusque-là.");
        if (signataire != null) {
            el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
            para(el, "La Personne responsable des marchés publics, " + signataire + " — signé électroniquement sur la plateforme le "
                    + a.getPublieLe().format(HORODATAGE) + ".");
        }
        return new DocumentLibre("AVIS_AMI", null, el, "Procédure " + a.getIdDmc() + " — avis à manifestation d'intérêt (modèle provisoire)");
    }

    // ------------------------------------------------------------------ outils

    private AmiDto dto(Ami a) {
        DossierMec dmc = dmcRepository.findById(a.getIdDmc()).orElse(null);
        String autorite = dmc == null ? null : valeursPpm.lire(dmc.getIdDetail()).valeurs().get("ENTITE");
        return new AmiDto(a.getIdDmc(), a.getEtat(), a.getObjet(), autorite, reference(a.getIdDmc()), a.getDateLimite(), lireCriteres(a),
                lirePieces(a), a.getNoteMinimale(), a.getNombreRetenus(), a.getMotifDispense(), lirePublications(a), !Ami.DISPENSE.equals(a.getEtat()),
                a.getPublieLe(), a.getPubliePar(), lisible(a), expressions.countByIdDmcAndEtat(a.getIdDmc(), AmiExpression.DEPOSEE));
    }

    private AmiDto.AmiPublic publicDto(Ami a) {
        DossierMec dmc = dmcRepository.findById(a.getIdDmc()).orElse(null);
        String autorite = dmc == null ? null : valeursPpm.lire(dmc.getIdDetail()).valeurs().get("ENTITE");
        return new AmiDto.AmiPublic(a.getIdDmc(), reference(a.getIdDmc()), a.getObjet(), autorite, a.getDateLimite(), lireCriteres(a),
                lirePieces(a), a.getNombreRetenus(), lirePublications(a), a.getPublieLe(), a.getDateLimite().isAfter(maintenant()));
    }

    private AmiDto.Expression expressionDto(AmiExpression x) {
        Map<String, Object> c = mapper.readValue(x.getContenu(), new TypeReference<Map<String, Object>>() {
        });
        List<AmiDto.Reference> refs = mapper.convertValue(c.getOrDefault("references", List.of()), new TypeReference<List<AmiDto.Reference>>() {
        });
        List<AmiDto.Membre> grp = mapper.convertValue(c.getOrDefault("groupement", List.of()), new TypeReference<List<AmiDto.Membre>>() {
        });
        List<AmiDto.PieceDeposee> pieces = piecesRepository.findByIdExpressionOrderByIdAsc(x.getId()).stream()
                .map(p -> new AmiDto.PieceDeposee(p.getId(), p.getLibelle(), p.getNom(), p.getFormat(), p.getTaille(), p.getEmpreinte())).toList();
        return new AmiDto.Expression(x.getId(), x.getNumero(), x.getNif(), x.getRaisonSociale(), x.getEtat(), x.getDeposeeLe(), x.getEmpreinte(),
                (String) c.get("lettre"), (String) c.get("qualifications"), refs, grp, pieces);
    }

    /** Les critères saisis : au moins un, libellés uniques, poids &gt; 0 et somme = 100 ; un code C1, C2… s'il manque. */
    private static List<AmiDto.Critere> criteres(List<AmiDto.Critere> saisis) {
        List<AmiDto.Critere> l = saisis == null ? List.of() : saisis.stream().filter(c -> c != null && nettoyer(c.libelle()) != null).toList();
        if (l.isEmpty()) {
            throw new BadRequestException("L'AMI exige au moins un critère de sélection (aptitude, références, expérience).", "CRITERES_OBLIGATOIRES");
        }
        BigDecimal somme = BigDecimal.ZERO;
        Set<String> codes = new HashSet<>();
        List<AmiDto.Critere> out = new ArrayList<>();
        int i = 0;
        for (AmiDto.Critere c : l) {
            i++;
            if (c.poids() == null || c.poids().signum() <= 0) {
                throw new BadRequestException("Chaque critère porte un poids positif : « " + c.libelle() + " ».", "PONDERATION_INVALIDE");
            }
            String code = nettoyer(c.code()) == null ? "C" + i : c.code().trim();
            if (!codes.add(code)) {
                throw new BadRequestException("Deux critères portent le code « " + code + " ».", "PONDERATION_INVALIDE");
            }
            somme = somme.add(c.poids());
            out.add(new AmiDto.Critere(code, c.libelle().trim(), c.poids(), nettoyer(c.description())));
        }
        if (somme.compareTo(CENT) != 0) {
            throw new BadRequestException("Les poids des critères totalisent " + somme.stripTrailingZeros().toPlainString() + " : ils doivent faire 100.",
                    "PONDERATION_INVALIDE");
        }
        return out;
    }

    private static List<String> nettoyees(List<String> l) {
        return l == null ? List.of() : l.stream().map(AmiService::nettoyer).filter(Objects::nonNull).distinct().toList();
    }

    List<AmiDto.Critere> lireCriteres(Ami a) {
        return a.getCriteres() == null ? List.of() : mapper.readValue(a.getCriteres(), new TypeReference<List<AmiDto.Critere>>() {
        });
    }

    private List<String> lirePieces(Ami a) {
        return a.getPieces() == null ? List.of() : mapper.readValue(a.getPieces(), new TypeReference<List<String>>() {
        });
    }

    private List<AmiDto.Publication> lirePublications(Ami a) {
        return a.getPublications() == null ? List.of() : mapper.readValue(a.getPublications(), new TypeReference<List<AmiDto.Publication>>() {
        });
    }

    /** La référence de la procédure ({@code B02-OB-03} de la fiche), nulle sans elle. */
    private String reference(Long idDmc) {
        try {
            Map<String, String> v = fiches.lire(idDmc).getValeurs();
            return v == null ? null : nettoyer(v.get("B02-OB-03"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean lisible(Ami a) {
        return Ami.PUBLIE.equals(a.getEtat()) && a.getDateLimite() != null && !a.getDateLimite().isAfter(maintenant());
    }

    private Ami exiger(Long idDmc) {
        return amis.findById(idDmc).orElseThrow(() -> new ResourceNotFoundException("Aucun appel à manifestation d'intérêt pour cette procédure."));
    }

    private Ami exigerPublie(Long idDmc) {
        return amis.findById(idDmc).filter(a -> Ami.PUBLIE.equals(a.getEtat()))
                .orElseThrow(() -> new ResourceNotFoundException("Aucun appel à manifestation d'intérêt publié pour cette procédure."));
    }

    private Ami exigerLisible(Long idDmc) {
        Ami a = exigerPublie(idDmc);
        if (!lisible(a)) {
            throw new BusinessRuleException("Les expressions d'intérêt se lisent après la date limite (" + a.getDateLimite().format(HORODATAGE) + ").",
                    "LECTURE_FERMEE");
        }
        return a;
    }

    private void exigerOuvert(Ami a) {
        if (!a.getDateLimite().isAfter(maintenant())) {
            throw new BusinessRuleException("La date limite de dépôt est passée (" + a.getDateLimite().format(HORODATAGE) + ").", "DATE_LIMITE_DEPASSEE");
        }
    }

    private static void exigerBrouillon(Ami a) {
        if (Ami.PUBLIE.equals(a.getEtat())) {
            throw new BusinessRuleException("L'AMI est publié : il ne se modifie plus.", "AMI_PUBLIE");
        }
        if (Ami.DISPENSE.equals(a.getEtat())) {
            throw new BusinessRuleException("L'AMI est dispensé de publicité.", "AMI_DISPENSE");
        }
    }

    private static void exigerCategorie(FicheMarcheDto f) {
        if (ModelesDao.sigleLettre(f.getCategorie()) == null) {
            throw new BusinessRuleException("L'appel à manifestation d'intérêt est propre aux prestations intellectuelles.", "CATEGORIE_SANS_AMI");
        }
    }

    /** La PRMP ou son UGPM, au périmètre de la fiche, et une fiche de prestations intellectuelles. */
    private FicheMarcheDto exigerPreparateur(Long idDmc) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p != ProfilUtilisateur.PRMP && p != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("L'AMI se prépare par la PRMP de la fiche (ou son UGPM).");
        }
        FicheMarcheDto f = fiches.lire(idDmc);
        exigerCategorie(f);
        return f;
    }

    private void exigerPrmp(Long idDmc) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Ce geste de l'AMI est réservé à la PRMP de la fiche.");
        }
        fiches.controlerLecture(idDmc);
    }

    /** PRMP et UGPM de la fiche ; les membres de la commission d'appel d'offres de la procédure. */
    private void exigerLecteur(Long idDmc) {
        String ref = CurrentUser.ref().orElse(null);
        if (ref != null && TypeActeur.MEMBRE_CAO.name().equals(CurrentUser.acteurType().orElse(null)) && internes.membresCao(idDmc).contains(ref)) {
            return;
        }
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p == ProfilUtilisateur.PRMP || p == ProfilUtilisateur.UGPM) {
            fiches.controlerLecture(idDmc);
            return;
        }
        throw new AccessDeniedException("L'AMI se lit par la PRMP, l'UGPM et la commission d'appel d'offres.");
    }

    private String nomPrmp() {
        String ref = CurrentUser.ref().orElse(null);
        return ref == null ? Objects.toString(acteur(), "—") : prmpRepository.findById(ref)
                .map(p -> (Objects.toString(p.getNomPrmp(), "") + " " + Objects.toString(p.getPrenomsPrmp(), "")).trim())
                .filter(s -> !s.isBlank()).orElse(ref);
    }

    private static byte[] fichier(List<GenerateurDocumentsFiche.Fichier> f, boolean docx) {
        return f.stream().filter(x -> (docx ? "docx" : "pdf").equals(x.extension())).map(GenerateurDocumentsFiche.Fichier::contenu).findFirst()
                .orElse(null);
    }

    static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private static void sous(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, t));
    }

    private void tracer(Long idDmc, String action, String detail) {
        journal.save(new EvaluationJournal(null, idDmc, maintenant(), acteur(), action, detail));
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }

    private static String acteur() {
        return CurrentUser.ref().or(CurrentUser::login).orElse(null);
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Pour les journaux et messages : un jour lisible. */
    static String jour(LocalDateTime d) {
        return d == null ? "—" : d.format(JOUR);
    }
}
