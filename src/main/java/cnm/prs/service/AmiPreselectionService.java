package cnm.prs.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AmiDto;
import cnm.prs.dto.PreselectionDto;
import cnm.prs.entity.Ami;
import cnm.prs.entity.AmiDeclaration;
import cnm.prs.entity.AmiExpression;
import cnm.prs.entity.AmiListe;
import cnm.prs.entity.AmiNote;
import cnm.prs.entity.AmiSignature;
import cnm.prs.entity.CaoMembre;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Entreprise;
import cnm.prs.entity.EvaluationJournal;
import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.AccesReserveException;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AmiDeclarationRepository;
import cnm.prs.repository.AmiExpressionRepository;
import cnm.prs.repository.AmiListeRepository;
import cnm.prs.repository.AmiNoteRepository;
import cnm.prs.repository.AmiRepository;
import cnm.prs.repository.AmiSignatureRepository;
import cnm.prs.repository.CaoMembreRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.EntrepriseRepository;
import cnm.prs.repository.EvaluationJournalRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>L'AMI en ligne, tranche AMI-b</strong> (demande front du 2026-10-07, {@code demande-backend-2026-10-07-ami-pi.md}, §B3,
 * §B4 ; V83 ; art. 42-II de la loi n° 2016-055) — la présélection, après la date limite :
 * <ul>
 *   <li>chaque membre de la commission signe sa <strong>déclaration préalable</strong> ; un membre en conflit ne décide rien ;</li>
 *   <li>les membres déclarés <strong>notent</strong> chaque expression sur les critères publiés (de 0 au poids du critère, motif
 *   exigé), ou l'<strong>écartent</strong> avec un motif ; le serveur totalise et classe ; est <strong>qualifiée</strong> une expression
 *   complète, non écartée, qui atteint la note minimale ;</li>
 *   <li>le <strong>président arrête la liste</strong> : les six premières qualifiées (le nombre de l'AMI) ; moins de six, avec un motif
 *   (Q3) ; une égalité au seuil se départage par un ordre motivé ; le <strong>rapport de présélection</strong> est produit, et signé des
 *   membres hors conflit ;</li>
 *   <li>à la dernière signature, la liste est <strong>définitive</strong> : publiée sur la page publique de l'AMI et notifiée à chaque
 *   candidat, les non retenus avec leur motif (Q5) ; elle est jointe d'office, avec le rapport, au dossier de la demande de propositions
 *   (Q4), et les lettres d'invitation la prennent avec les comptes des candidats (§B4) ;</li>
 *   <li>la PRMP peut <strong>relancer</strong> l'AMI avant l'arrêt de la liste (nouvelle date limite), ou le déclarer
 *   <strong>infructueux</strong> s'il n'a aucun candidat qualifié (art. 56-II).</li>
 * </ul>
 */
@Service
@Transactional
public class AmiPreselectionService {

    public static final String EN_ATTENTE = "EN_ATTENTE";
    public static final String NOTATION = "NOTATION";
    public static final String LISTE_ARRETEE = "LISTE_ARRETEE";
    public static final String DEFINITIVE = "DEFINITIVE";
    public static final String CODE_PIECE = "RAPPORT_PRESELECTION";
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");

    private final AmiRepository amis;
    private final AmiService ami;
    private final AmiExpressionRepository expressions;
    private final AmiDeclarationRepository declarations;
    private final AmiNoteRepository notes;
    private final AmiListeRepository listes;
    private final AmiSignatureRepository signatures;
    private final CaoMembreRepository caoMembres;
    private final ParametresInternesService internes;
    private final FicheMarcheService fiches;
    private final GenerateurDocumentsFiche generateur;
    private final CompteCandidatRepository candidats;
    private final EntrepriseRepository entreprises;
    private final NotificationService notifications;
    private final PieceJointeDossierRepository pieces;
    private final TypePieceJointeRepository typesPiece;
    private final EvaluationJournalRepository journal;
    private final Clock horloge;

    public AmiPreselectionService(AmiRepository amis, AmiService ami, AmiExpressionRepository expressions, AmiDeclarationRepository declarations,
            AmiNoteRepository notes, AmiListeRepository listes, AmiSignatureRepository signatures, CaoMembreRepository caoMembres,
            ParametresInternesService internes, FicheMarcheService fiches, GenerateurDocumentsFiche generateur, CompteCandidatRepository candidats,
            EntrepriseRepository entreprises, NotificationService notifications, PieceJointeDossierRepository pieces,
            TypePieceJointeRepository typesPiece, EvaluationJournalRepository journal, Clock horloge) {
        this.amis = amis;
        this.ami = ami;
        this.expressions = expressions;
        this.declarations = declarations;
        this.notes = notes;
        this.listes = listes;
        this.signatures = signatures;
        this.caoMembres = caoMembres;
        this.internes = internes;
        this.fiches = fiches;
        this.generateur = generateur;
        this.candidats = candidats;
        this.entreprises = entreprises;
        this.notifications = notifications;
        this.pieces = pieces;
        this.typesPiece = typesPiece;
        this.journal = journal;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ lecture

    /** La présélection : PRMP, UGPM, membres de la commission ; 404 sans AMI publié. */
    @Transactional(readOnly = true)
    public PreselectionDto lire(Long idDmc) {
        exigerLecteur(idDmc);
        return dto(exigerPublieOuClos(idDmc));
    }

    /** Le rapport de présélection (PDF, ou Word) : mêmes lecteurs ; 404 tant qu'il n'est pas produit. */
    @Transactional(readOnly = true)
    public byte[] rapport(Long idDmc, boolean docx) {
        exigerLecteur(idDmc);
        Ami a = exigerPublieOuClos(idDmc);
        byte[] b = docx ? a.getRapportDocx() : a.getRapportPdf();
        if (b == null) {
            throw new ResourceNotFoundException("Le rapport de présélection n'est pas produit.");
        }
        return b;
    }

    // ------------------------------------------------------------------ la commission

    /** La déclaration préalable du membre appelant ; 409 {@code DEJA_DECLARE}. */
    public PreselectionDto declarer(Long idDmc, PreselectionDto.DeclarationRequest r) {
        String k = membreAppelant(idDmc);
        if (k == null) {
            throw new AccessDeniedException("La déclaration se signe par les membres de la commission d'appel d'offres.");
        }
        Ami a = exigerPublieOuClos(idDmc);
        if (declarations.findByIdDmcAndIm(idDmc, k).isPresent()) {
            throw new BusinessRuleException("Vous avez déjà signé votre déclaration pour cet AMI.", "DEJA_DECLARE");
        }
        boolean conflit = r != null && Boolean.TRUE.equals(r.conflit());
        declarations.save(new AmiDeclaration(null, idDmc, k, maintenant(), conflit, r == null ? null : nettoyer(r.precision())));
        tracer(idDmc, "AMI_DECLARATION", internes.nomMembre(k) + (conflit ? " déclare un conflit d'intérêts" : " déclare l'absence de conflit d'intérêts"));
        return dto(a);
    }

    /**
     * Les notes d'une expression : membre déclaré sans conflit ; chaque critère publié (400 {@code CRITERE_INCONNU}), de 0 au poids du
     * critère (400 {@code NOTE_HORS_BAREME}), motivé (400 {@code MOTIF_OBLIGATOIRE}) ; 409 {@code LECTURE_FERMEE}, {@code LISTE_ARRETEE},
     * {@code EXPRESSION_ECARTEE}, {@code DECLARATION_MANQUANTE} ; 403 {@code MEMBRE_EN_CONFLIT}. Une note saisie remplace la précédente.
     */
    public PreselectionDto noter(Long idDmc, String idExpression, PreselectionDto.NotesRequest r) {
        String k = exigerDecideur(idDmc);
        Ami a = exigerEnNotation(idDmc);
        AmiExpression x = expression(idDmc, idExpression);
        if (x.getEcarteeLe() != null) {
            throw new BusinessRuleException("Cette expression est écartée : rétablissez-la avant de la noter.", "EXPRESSION_ECARTEE");
        }
        Map<String, AmiDto.Critere> criteres = new LinkedHashMap<>();
        ami.lireCriteres(a).forEach(c -> criteres.put(c.code(), c));
        List<PreselectionDto.NoteSaisie> saisies = r == null || r.notes() == null ? List.of() : r.notes().stream().filter(Objects::nonNull).toList();
        if (saisies.isEmpty()) {
            throw new BadRequestException("Aucune note saisie.", "NOTE_HORS_BAREME");
        }
        for (PreselectionDto.NoteSaisie s : saisies) {
            AmiDto.Critere c = criteres.get(s.code());
            if (c == null) {
                throw new BadRequestException("Critère inconnu de l'AMI : " + s.code() + ".", "CRITERE_INCONNU");
            }
            if (s.note() == null || s.note().signum() < 0 || s.note().compareTo(c.poids()) > 0) {
                throw new BadRequestException("La note de « " + c.libelle() + " » se situe entre 0 et " + c.poids().stripTrailingZeros().toPlainString()
                        + ".", "NOTE_HORS_BAREME");
            }
            if (nettoyer(s.motif()) == null) {
                throw new BadRequestException("La note de « " + c.libelle() + " » exige son motif.", "MOTIF_OBLIGATOIRE");
            }
        }
        LocalDateTime le = maintenant();
        for (PreselectionDto.NoteSaisie s : saisies) {
            AmiNote n = notes.findByIdExpressionAndCodeCritere(idExpression, s.code())
                    .orElseGet(() -> new AmiNote(null, idDmc, idExpression, s.code(), null, null, null, null));
            n.setNote(s.note());
            n.setMotif(nettoyer(s.motif()));
            n.setPar(k);
            n.setLe(le);
            notes.save(n);
        }
        tracer(idDmc, "AMI_NOTE", "Expression n° " + x.getNumero() + " (" + x.getRaisonSociale() + ") : " + String.join(", ",
                saisies.stream().map(s -> s.code() + " = " + s.note().stripTrailingZeros().toPlainString()).toList()) + " — " + internes.nomMembre(k));
        return dto(a);
    }

    /** Écarte une expression (motif exigé : 400 {@code MOTIF_OBLIGATOIRE}), ou la rétablit ({@code ecartee} faux) ; mêmes gardes que la notation. */
    public PreselectionDto ecarter(Long idDmc, String idExpression, PreselectionDto.EcartementRequest r) {
        String k = exigerDecideur(idDmc);
        Ami a = exigerEnNotation(idDmc);
        AmiExpression x = expression(idDmc, idExpression);
        boolean ecarter = r == null || r.ecartee() == null || r.ecartee();
        if (ecarter) {
            String motif = r == null ? null : nettoyer(r.motif());
            if (motif == null) {
                throw new BadRequestException("L'écartement d'une expression exige son motif.", "MOTIF_OBLIGATOIRE");
            }
            x.setMotifEcartement(motif);
            x.setEcarteeLe(maintenant());
            x.setEcarteePar(k);
        } else {
            x.setMotifEcartement(null);
            x.setEcarteeLe(null);
            x.setEcarteePar(null);
        }
        expressions.save(x);
        tracer(idDmc, "AMI_ECARTEMENT", "Expression n° " + x.getNumero() + " (" + x.getRaisonSociale() + ") " + (ecarter ? "écartée : "
                + x.getMotifEcartement() : "rétablie") + " — " + internes.nomMembre(k));
        return dto(a);
    }

    /**
     * Le président arrête la liste restreinte : 409 {@code NOTATION_INCOMPLETE} (détails : les numéros), {@code AUCUN_QUALIFIE},
     * {@code EGALITE_A_DEPARTAGER} (détails : les expressions à égalité au seuil) ; 400 {@code MOTIF_NOMBRE_OBLIGATOIRE} (moins de
     * qualifiées que le nombre à retenir, Q3), {@code ORDRE_INVALIDE}. Le rapport est produit ; sans signataire, la liste est définitive.
     */
    public PreselectionDto arreter(Long idDmc, PreselectionDto.ArretRequest r) {
        String k = exigerPresident(idDmc);
        Ami a = exigerEnNotation(idDmc);
        Classement cl = classer(a);
        List<Integer> incompletes = cl.lignes().stream().filter(l -> !l.ecartee() && !l.complete()).map(l -> l.x().getNumero()).toList();
        if (!incompletes.isEmpty()) {
            throw new BusinessRuleException("Des expressions ne sont pas notées sur tous les critères : n° " + incompletes + ".",
                    "NOTATION_INCOMPLETE", null, Map.of("expressions", incompletes));
        }
        List<Ligne> qualifiees = cl.lignes().stream().filter(Ligne::qualifiee).toList();
        if (qualifiees.isEmpty()) {
            throw new BusinessRuleException("Aucune expression n'est qualifiée : la PRMP peut relancer l'AMI ou le déclarer infructueux.",
                    "AUCUN_QUALIFIE");
        }
        int n = a.getNombreRetenus();
        List<Ligne> ordonnees = new ArrayList<>(qualifiees);
        if (qualifiees.size() > n) {
            BigDecimal seuil = qualifiees.get(n - 1).total();
            List<Ligne> aEgalite = qualifiees.stream().filter(l -> l.total().compareTo(seuil) == 0).toList();
            boolean coupee = qualifiees.get(n).total().compareTo(seuil) == 0;
            if (coupee) {
                List<String> ordre = r == null || r.ordre() == null ? List.of() : r.ordre();
                List<String> ids = aEgalite.stream().map(l -> l.x().getId()).toList();
                if (ordre.isEmpty()) {
                    throw new BusinessRuleException("Plusieurs expressions sont à égalité au seuil de la liste : départagez-les.", "EGALITE_A_DEPARTAGER",
                            null, Map.of("expressions", ids));
                }
                if (ordre.size() != ids.size() || !ordre.containsAll(ids)) {
                    throw new BadRequestException("L'ordre de départage doit citer une fois chaque expression à égalité au seuil.", "ORDRE_INVALIDE");
                }
                ordonnees.removeAll(aEgalite);
                int debut = (int) qualifiees.stream().filter(l -> l.total().compareTo(seuil) > 0).count();
                List<Ligne> departagees = ordre.stream().map(id -> aEgalite.stream().filter(l -> l.x().getId().equals(id)).findFirst().orElseThrow())
                        .toList();
                ordonnees.addAll(debut, departagees);
            }
        }
        List<Ligne> retenues = ordonnees.subList(0, Math.min(n, ordonnees.size()));
        String motifNombre = r == null ? null : nettoyer(r.motifNombre());
        if (retenues.size() < n && motifNombre == null) {
            throw new BadRequestException("Seules " + retenues.size() + " expression(s) sont qualifiées pour " + n + " places : motivez le nombre "
                    + "(art. 42-II), ou relancez l'AMI.", "MOTIF_NOMBRE_OBLIGATOIRE");
        }
        listes.deleteByIdDmc(idDmc);
        listes.flush();
        int rang = 0;
        for (Ligne l : retenues) {
            rang++;
            listes.save(new AmiListe(idDmc, rang, l.x().getId(), l.x().getIdCandidat(), l.x().getNif(), l.x().getRaisonSociale(), l.total()));
        }
        LocalDateTime le = maintenant();
        a.setListeArreteeLe(le);
        a.setListeArreteePar(k);
        a.setMotifNombre(retenues.size() < n ? motifNombre : null);
        a.setObservations(r == null ? null : nettoyer(r.observations()));
        List<String> signataires = signataires(idDmc);
        a.setSignataires(String.join(",", signataires));
        produireRapport(a);
        amis.save(a);
        tracer(idDmc, "AMI_LISTE_ARRETEE", "Liste restreinte arrêtée : " + retenues.size() + " candidat(s) — " + String.join(", ",
                retenues.stream().map(l -> l.x().getRaisonSociale()).toList()));
        if (signataires.isEmpty()) {
            finaliser(a);
        }
        return dto(a);
    }

    /** La signature du rapport par un membre appelé (observation facultative) : 403 {@code NON_SIGNATAIRE} ; 409 {@code RAPPORT_NON_PRODUIT}, {@code DEJA_SIGNE}. */
    public PreselectionDto signer(Long idDmc, PreselectionDto.SignatureRequest r) {
        String k = membreAppelant(idDmc);
        Ami a = exigerRapportASigner(idDmc);
        if (k == null || !liste(a.getSignataires()).contains(k)) {
            throw new AccesReserveException("Le rapport se signe par les membres de la commission appelés à le signer.", "NON_SIGNATAIRE");
        }
        if (signatures.existsByIdDmcAndIm(idDmc, k)) {
            throw new BusinessRuleException("Vous avez déjà signé ce rapport.", "DEJA_SIGNE");
        }
        signatures.save(new AmiSignature(null, idDmc, k, maintenant(), false, null, null, r == null ? null : nettoyer(r.observation())));
        tracer(idDmc, "AMI_SIGNATURE", internes.nomMembre(k) + " a signé le rapport de présélection");
        apresSignature(a);
        return dto(a);
    }

    /** L'empêchement d'un signataire, constaté par le président : 400 {@code MOTIF_ABSENT}, {@code NON_SIGNATAIRE} ; 409 {@code DEJA_SIGNE}. */
    public PreselectionDto empechement(Long idDmc, PreselectionDto.EmpechementRequest r) {
        String k = exigerPresident(idDmc);
        Ami a = exigerRapportASigner(idDmc);
        if (r == null || nettoyer(r.motif()) == null) {
            throw new BadRequestException("L'empêchement exige son motif.", "MOTIF_ABSENT");
        }
        if (r.im() == null || !liste(a.getSignataires()).contains(r.im())) {
            throw new BadRequestException("Ce membre n'est pas appelé à signer le rapport.", "NON_SIGNATAIRE");
        }
        if (signatures.existsByIdDmcAndIm(idDmc, r.im())) {
            throw new BusinessRuleException("Ce membre a déjà signé.", "DEJA_SIGNE");
        }
        signatures.save(new AmiSignature(null, idDmc, r.im(), maintenant(), true, nettoyer(r.motif()), internes.nomMembre(k), null));
        tracer(idDmc, "AMI_EMPECHEMENT", internes.nomMembre(r.im()) + " empêché de signer : " + r.motif().trim());
        apresSignature(a);
        return dto(a);
    }

    // ------------------------------------------------------------------ la PRMP : relance, infructuosité

    /**
     * Relance l'AMI avant l'arrêt de la liste (Q3) : nouvelle date limite à venir (400 {@code DATE_LIMITE_INVALIDE}), motif (400
     * {@code MOTIF_OBLIGATOIRE}) ; 409 {@code LISTE_ARRETEE}, {@code DATE_LIMITE_NON_ATTEINTE}. Les expressions et les notes restent.
     */
    public PreselectionDto relancer(Long idDmc, PreselectionDto.RelanceRequest r) {
        exigerPrmp(idDmc);
        Ami a = exigerEnNotation(idDmc);
        if (r == null || nettoyer(r.motif()) == null) {
            throw new BadRequestException("La relance exige son motif.", "MOTIF_OBLIGATOIRE");
        }
        if (r.dateLimite() == null || !r.dateLimite().isAfter(maintenant())) {
            throw new BadRequestException("La nouvelle date limite doit être à venir.", "DATE_LIMITE_INVALIDE");
        }
        a.setDateLimite(r.dateLimite());
        a.setNombreRelances(a.getNombreRelances() + 1);
        amis.save(a);
        tracer(idDmc, "AMI_RELANCE", "AMI relancé (" + r.motif().trim() + ") ; nouvelle date limite le " + r.dateLimite().format(HORODATAGE));
        return dto(a);
    }

    /** Déclare l'AMI infructueux (art. 56-II) : motif (400) ; 409 {@code QUALIFIES_PRESENTS} s'il reste une expression qualifiée, {@code LISTE_ARRETEE}. */
    public PreselectionDto infructueux(Long idDmc, PreselectionDto.InfructueuxRequest r) {
        exigerPrmp(idDmc);
        Ami a = exigerEnNotation(idDmc);
        if (r == null || nettoyer(r.motif()) == null) {
            throw new BadRequestException("L'infructuosité exige son motif.", "MOTIF_OBLIGATOIRE");
        }
        if (classer(a).lignes().stream().anyMatch(Ligne::qualifiee)) {
            throw new BusinessRuleException("Au moins une expression est qualifiée : l'AMI n'est pas infructueux.", "QUALIFIES_PRESENTS");
        }
        a.setEtat(Ami.INFRUCTUEUX);
        a.setMotifInfructueux(r.motif().trim());
        amis.save(a);
        tracer(idDmc, "AMI_INFRUCTUEUX", "AMI déclaré infructueux (art. 56-II) : " + a.getMotifInfructueux());
        return dto(a);
    }

    // ------------------------------------------------------------------ Q4 et §B4 : la liste, au dossier et aux invitations

    /** La liste restreinte définitive de la procédure, si l'AMI en a une. */
    @Transactional(readOnly = true)
    public Optional<List<AmiListe>> listeDefinitive(Long idDmc) {
        return amis.findById(idDmc).filter(a -> a.getListeDefinitiveLe() != null).map(a -> listes.findByIdDmcOrderByRangAsc(idDmc));
    }

    /** L'adresse d'un candidat de la liste, telle que son entreprise la déclare. */
    @Transactional(readOnly = true)
    public String adresse(String idCandidat) {
        return entreprises.findByIdCandidat(idCandidat).map(Entreprise::getAdresse).filter(s -> s != null && !s.isBlank()).orElse("—");
    }

    /**
     * Q4 — avant de créer le dossier de la demande de propositions : un AMI publié doit avoir sa liste définitive (409
     * {@code LISTE_NON_ARRETEE}). Sans AMI, ou dispensé, rien n'est exigé.
     */
    @Transactional(readOnly = true)
    public void exigerListePourDossier(Long idDmc) {
        amis.findById(idDmc).filter(a -> Ami.PUBLIE.equals(a.getEtat()) && a.getListeDefinitiveLe() == null).ifPresent(a -> {
            throw new BusinessRuleException("La liste restreinte de l'AMI n'est pas définitive : le dossier de la demande de propositions "
                    + "se crée une fois le rapport de présélection signé.", "LISTE_NON_ARRETEE");
        });
    }

    /** Q4 — joint d'office le rapport de présélection signé au dossier de la demande de propositions (code {@code RAPPORT_PRESELECTION}). */
    public void joindre(Integer idDossier, Long idDmc) {
        Ami a = amis.findById(idDmc).filter(x -> x.getListeDefinitiveLe() != null && x.getRapportPdf() != null).orElse(null);
        TypePieceJointe type = typesPiece.findFirstByCode(CODE_PIECE).orElse(null);
        if (a == null || type == null) {
            return;
        }
        PieceJointeDossier p = new PieceJointeDossier();
        p.setIdDossier(idDossier);
        p.setIdTypePiece(type.getIdTypePiece());
        p.setNomFichier("rapport-preselection-ami_" + idDmc + ".pdf");
        p.setContenu(a.getRapportPdf());
        p.setFormat("PDF");
        p.setTaille((long) a.getRapportPdf().length);
        p.setDateUpload(LocalDateTime.now(horloge));
        p.setApresLettreRenvoi(false);
        pieces.save(p);
        tracer(idDmc, "AMI_RAPPORT_JOINT", "Rapport de présélection joint au dossier " + idDossier);
    }

    /** Q4 — au détachement de la fiche, le rapport de présélection joint d'office part avec elle. */
    public void detacher(Integer idDossier) {
        typesPiece.findFirstByCode(CODE_PIECE).ifPresent(t -> pieces.findByIdDossier(idDossier).stream()
                .filter(p -> t.getIdTypePiece().equals(p.getIdTypePiece())).forEach(pieces::delete));
    }

    // ------------------------------------------------------------------ outils : classement, rapport, finalisation

    private record Ligne(AmiExpression x, Map<String, AmiNote> notes, BigDecimal total, boolean complete, boolean ecartee, boolean qualifiee) {
    }

    private record Classement(List<Ligne> lignes, Map<String, Integer> rangs, Map<String, Boolean> exAequo) {
    }

    /** Les expressions déposées, notées et classées : les qualifiées par total décroissant (puis par numéro), les autres ensuite. */
    private Classement classer(Ami a) {
        List<AmiDto.Critere> criteres = ami.lireCriteres(a);
        Map<String, Map<String, AmiNote>> parExpression = new HashMap<>();
        notes.findByIdDmcOrderByIdAsc(a.getIdDmc()).forEach(n -> parExpression.computeIfAbsent(n.getIdExpression(), x -> new HashMap<>())
                .put(n.getCodeCritere(), n));
        List<Ligne> lignes = new ArrayList<>();
        for (AmiExpression x : expressions.findByIdDmcAndEtatOrderByNumeroAsc(a.getIdDmc(), AmiExpression.DEPOSEE)) {
            Map<String, AmiNote> m = parExpression.getOrDefault(x.getId(), Map.of());
            BigDecimal total = criteres.stream().map(c -> m.get(c.code())).filter(Objects::nonNull).map(AmiNote::getNote)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean complete = criteres.stream().allMatch(c -> m.containsKey(c.code()));
            boolean ecartee = x.getEcarteeLe() != null;
            boolean qualifiee = complete && !ecartee && (a.getNoteMinimale() == null || total.compareTo(a.getNoteMinimale()) >= 0);
            lignes.add(new Ligne(x, m, total, complete, ecartee, qualifiee));
        }
        lignes.sort(Comparator.comparing((Ligne l) -> !l.qualifiee()).thenComparing(Ligne::total, Comparator.reverseOrder())
                .thenComparing(l -> l.x().getNumero()));
        Map<String, Integer> rangs = new HashMap<>();
        Map<String, Boolean> exAequo = new HashMap<>();
        int rang = 0;
        BigDecimal precedent = null;
        int position = 0;
        for (Ligne l : lignes) {
            if (!l.qualifiee()) {
                continue;
            }
            position++;
            if (precedent == null || l.total().compareTo(precedent) != 0) {
                rang = position;
            }
            precedent = l.total();
            rangs.put(l.x().getId(), rang);
        }
        for (Ligne l : lignes) {
            if (l.qualifiee()) {
                exAequo.put(l.x().getId(), lignes.stream().filter(o -> o != l && o.qualifiee() && o.total().compareTo(l.total()) == 0).count() > 0);
            }
        }
        return new Classement(lignes, rangs, exAequo);
    }

    private void apresSignature(Ami a) {
        List<String> attendues = liste(a.getSignataires());
        boolean toutes = attendues.stream().allMatch(im -> signatures.existsByIdDmcAndIm(a.getIdDmc(), im));
        if (toutes) {
            finaliser(a);
        }
    }

    /** La liste devient définitive : le rapport est reproduit avec ses signatures, la liste publiée et notifiée (Q5). */
    private void finaliser(Ami a) {
        a.setListeDefinitiveLe(maintenant());
        produireRapport(a);
        amis.save(a);
        List<AmiListe> liste = listes.findByIdDmcOrderByRangAsc(a.getIdDmc());
        Map<String, AmiListe> retenus = new HashMap<>();
        liste.forEach(l -> retenus.put(l.getIdExpression(), l));
        Classement cl = classer(a);
        for (Ligne l : cl.lignes()) {
            AmiListe r = retenus.get(l.x().getId());
            String corps = r != null ? "Votre candidature est retenue sur la liste restreinte (rang " + r.getRang() + ") de « " + a.getObjet()
                    + " » ; vous recevrez la lettre d'invitation à remettre une proposition."
                    : "Votre candidature n'est pas retenue sur la liste restreinte de « " + a.getObjet() + " » : " + motifNonRetenu(a, l, cl) + ".";
            CompteCandidat c = candidats.findById(l.x().getIdCandidat()).orElse(null);
            notifications.emettreCandidat(TypeNotification.AMI_RESULTAT, l.x().getIdCandidat(), c == null ? null : c.getEmail(),
                    a.getIdDmc().intValue(), TypeObjet.PROCEDURE, "Résultat de l'appel à manifestation d'intérêt", corps);
        }
        tracer(a.getIdDmc(), "AMI_LISTE_DEFINITIVE", "Rapport signé : liste restreinte définitive, publiée et notifiée à " + cl.lignes().size()
                + " candidat(s)");
    }

    private static String motifNonRetenu(Ami a, Ligne l, Classement cl) {
        if (l.ecartee()) {
            return "expression écartée — " + l.x().getMotifEcartement();
        }
        if (!l.qualifiee()) {
            return "note de " + l.total().stripTrailingZeros().toPlainString() + " sur 100, sous la note minimale de "
                    + a.getNoteMinimale().stripTrailingZeros().toPlainString();
        }
        return "classée au rang " + cl.rangs().get(l.x().getId()) + " avec " + l.total().stripTrailingZeros().toPlainString()
                + " sur 100, au-delà des " + a.getNombreRetenus() + " places de la liste";
    }

    /** Le rapport de présélection (PDF, Word) : AMI, commission, notes par critère, écartements, classement, liste, signatures. */
    private void produireRapport(Ami a) {
        List<GenerateurDocumentsFiche.Fichier> f = generateur.generer(document(a));
        f.stream().filter(x -> "pdf".equals(x.extension())).findFirst().ifPresent(x -> a.setRapportPdf(x.contenu()));
        f.stream().filter(x -> "docx".equals(x.extension())).findFirst().ifPresent(x -> a.setRapportDocx(x.contenu()));
    }

    private DocumentLibre document(Ami a) {
        List<AmiDto.Critere> criteres = ami.lireCriteres(a);
        Classement cl = classer(a);
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "RAPPORT DE PRÉSÉLECTION"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "Appel à manifestation d'intérêt — " + Objects.toString(a.getObjet(), "—")));
        sous(el, "1. L'appel à manifestation d'intérêt");
        para(el, "Publié le " + (a.getPublieLe() == null ? "—" : a.getPublieLe().format(HORODATAGE)) + " ; date limite de dépôt : "
                + a.getDateLimite().format(HORODATAGE) + (a.getNombreRelances() > 0 ? " (après " + a.getNombreRelances() + " relance(s))" : "")
                + ". Nombre de candidats à retenir : " + a.getNombreRetenus() + (a.getNoteMinimale() == null ? "" : " ; note minimale : "
                        + a.getNoteMinimale().stripTrailingZeros().toPlainString() + " sur 100") + ".");
        para(el, "Critères : " + String.join(" ; ", criteres.stream().map(c -> c.code() + " " + c.libelle() + " (" + c.poids().stripTrailingZeros()
                .toPlainString() + " points)").toList()) + ".");
        sous(el, "2. La commission");
        for (AmiDeclaration d : declarations.findByIdDmcOrderBySigneeLeAscIdAsc(a.getIdDmc())) {
            para(el, internes.nomMembre(d.getIm()) + " : déclaration signée le " + d.getSigneeLe().format(HORODATAGE)
                    + (Boolean.TRUE.equals(d.getConflit()) ? ", conflit d'intérêts déclaré : n'a pris part à aucune décision" : ", absence de conflit d'intérêts"));
        }
        sous(el, "3. Les expressions d'intérêt reçues et leur notation");
        List<String> entete = new ArrayList<>(List.of("N°", "Candidat"));
        criteres.forEach(c -> entete.add(c.code()));
        entete.add("Total");
        entete.add("Rang");
        List<List<List<String>>> lignes = new ArrayList<>();
        lignes.add(entete.stream().map(List::of).toList());
        for (Ligne l : cl.lignes()) {
            List<List<String>> ligne = new ArrayList<>();
            ligne.add(List.of(String.valueOf(l.x().getNumero())));
            ligne.add(List.of(l.x().getRaisonSociale()));
            criteres.forEach(c -> ligne.add(List.of(l.notes().containsKey(c.code()) ? l.notes().get(c.code()).getNote().stripTrailingZeros().toPlainString() : "—")));
            ligne.add(List.of(l.total().stripTrailingZeros().toPlainString()));
            ligne.add(List.of(l.ecartee() ? "écartée" : !l.qualifiee() ? "non qualifiée" : String.valueOf(cl.rangs().get(l.x().getId()))));
            lignes.add(ligne);
        }
        el.add(new DocumentLibre.Tableau(entete.size(), lignes));
        for (Ligne l : cl.lignes()) {
            if (l.ecartee()) {
                para(el, "Expression n° " + l.x().getNumero() + " (" + l.x().getRaisonSociale() + ") écartée : " + l.x().getMotifEcartement() + ".");
            }
            for (AmiDto.Critere c : criteres) {
                AmiNote n = l.notes().get(c.code());
                if (n != null) {
                    para(el, "N° " + l.x().getNumero() + ", " + c.code() + " : " + n.getNote().stripTrailingZeros().toPlainString() + " — " + n.getMotif());
                }
            }
        }
        sous(el, "4. La liste restreinte");
        for (AmiListe r : listes.findByIdDmcOrderByRangAsc(a.getIdDmc())) {
            para(el, r.getRang() + ". " + r.getRaisonSociale() + " (NIF " + r.getNif() + ") — " + r.getNote().stripTrailingZeros().toPlainString() + " sur 100");
        }
        if (a.getMotifNombre() != null) {
            para(el, "Moins de " + a.getNombreRetenus() + " candidats qualifiés : " + a.getMotifNombre());
        }
        if (a.getObservations() != null) {
            sous(el, "Observations");
            para(el, a.getObservations());
        }
        sous(el, "5. Signatures des membres de la commission");
        Map<String, AmiSignature> signees = new HashMap<>();
        signatures.findByIdDmcOrderByDateAscIdAsc(a.getIdDmc()).forEach(s -> signees.put(s.getIm(), s));
        for (String im : liste(a.getSignataires())) {
            AmiSignature s = signees.get(im);
            String qui = internes.nomMembre(im);
            para(el, s == null ? qui + " — signature attendue" : Boolean.TRUE.equals(s.getEmpechement()) ? qui + " — empêché de signer : " + s.getMotif()
                    : qui + " — signé électroniquement sur la plateforme le " + s.getDate().format(HORODATAGE)
                            + (s.getObservation() == null ? "" : ". Observation : " + s.getObservation()));
        }
        return new DocumentLibre("RAPPORT_PRESELECTION", null, el, "Procédure " + a.getIdDmc() + " — rapport de présélection (AMI)");
    }

    private PreselectionDto dto(Ami a) {
        Long idDmc = a.getIdDmc();
        Map<String, CaoMembre> membres = new LinkedHashMap<>();
        caoMembres.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).stream().filter(m -> m.estMembre() && m.getIdCompte() != null)
                .forEach(m -> membres.put(m.getIdCompte(), m));
        Map<String, AmiDeclaration> signees = new HashMap<>();
        declarations.findByIdDmcOrderBySigneeLeAscIdAsc(idDmc).forEach(d -> signees.put(d.getIm(), d));
        List<PreselectionDto.Declaration> decl = new ArrayList<>();
        membres.forEach((k, m) -> {
            AmiDeclaration d = signees.get(k);
            decl.add(new PreselectionDto.Declaration(k, internes.nomMembre(k), Boolean.TRUE.equals(m.getPresident()), d == null ? null : d.getSigneeLe(),
                    d == null ? null : d.getConflit(), d == null ? null : d.getPrecision()));
        });
        List<AmiDto.Critere> criteres = ami.lireCriteres(a);
        boolean lisible = !a.getDateLimite().isAfter(maintenant());
        List<PreselectionDto.Expression> exprs = new ArrayList<>();
        if (lisible) {
            Classement cl = classer(a);
            for (Ligne l : cl.lignes()) {
                List<PreselectionDto.Note> ns = criteres.stream().map(c -> {
                    AmiNote n = l.notes().get(c.code());
                    return new PreselectionDto.Note(c.code(), c.libelle(), c.poids(), n == null ? null : n.getNote(), n == null ? null : n.getMotif(),
                            n == null ? null : n.getPar(), n == null ? null : internes.nomMembre(n.getPar()), n == null ? null : n.getLe());
                }).toList();
                exprs.add(new PreselectionDto.Expression(l.x().getId(), l.x().getNumero(), l.x().getNif(), l.x().getRaisonSociale(), ns, l.total(),
                        l.complete(), l.qualifiee(), l.ecartee(), l.x().getMotifEcartement(), cl.rangs().get(l.x().getId()),
                        Boolean.TRUE.equals(cl.exAequo().get(l.x().getId()))));
            }
        }
        List<PreselectionDto.Retenu> liste = listes.findByIdDmcOrderByRangAsc(idDmc).stream()
                .map(r -> new PreselectionDto.Retenu(r.getRang(), r.getIdExpression(), r.getIdCandidat(), r.getNif(), r.getRaisonSociale(), r.getNote()))
                .toList();
        PreselectionDto.Rapport rapport = null;
        if (a.getListeArreteeLe() != null) {
            List<PreselectionDto.Signature> sig = signatures.findByIdDmcOrderByDateAscIdAsc(idDmc).stream()
                    .map(s -> new PreselectionDto.Signature(s.getIm(), internes.nomMembre(s.getIm()), s.getDate(), Boolean.TRUE.equals(s.getEmpechement()),
                            s.getMotif(), s.getConstatePar(), s.getObservation()))
                    .toList();
            List<PreselectionDto.Attendue> att = liste(a.getSignataires()).stream().filter(im -> !signatures.existsByIdDmcAndIm(idDmc, im))
                    .map(im -> new PreselectionDto.Attendue(im, internes.nomMembre(im))).toList();
            rapport = new PreselectionDto.Rapport(a.getListeArreteeLe(), a.getListeDefinitiveLe() != null, a.getListeDefinitiveLe(), sig, att);
        }
        String etat = Ami.INFRUCTUEUX.equals(a.getEtat()) ? Ami.INFRUCTUEUX : a.getListeDefinitiveLe() != null ? DEFINITIVE
                : a.getListeArreteeLe() != null ? LISTE_ARRETEE : lisible ? NOTATION : EN_ATTENTE;
        return new PreselectionDto(idDmc, etat, decl, exprs, liste, a.getNombreRetenus(), a.getNoteMinimale(), a.getMotifNombre(), a.getObservations(),
                rapport, a.getNombreRelances(), a.getMotifInfructueux());
    }

    /** Les membres appelés à signer : ceux de la commission, hors ceux qui ont déclaré un conflit. */
    private List<String> signataires(Long idDmc) {
        return internes.membresCao(idDmc).stream()
                .filter(im -> declarations.findByIdDmcAndIm(idDmc, im).map(d -> !Boolean.TRUE.equals(d.getConflit())).orElse(true)).toList();
    }

    private static List<String> liste(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(","));
    }

    private AmiExpression expression(Long idDmc, String idExpression) {
        return expressions.findById(idExpression).filter(x -> x.getIdDmc().equals(idDmc) && AmiExpression.DEPOSEE.equals(x.getEtat()))
                .orElseThrow(() -> new ResourceNotFoundException("Expression d'intérêt introuvable : " + idExpression + "."));
    }

    private Ami exigerPublieOuClos(Long idDmc) {
        return amis.findById(idDmc).filter(a -> Ami.PUBLIE.equals(a.getEtat()) || Ami.INFRUCTUEUX.equals(a.getEtat()))
                .orElseThrow(() -> new ResourceNotFoundException("Aucun appel à manifestation d'intérêt publié pour cette procédure."));
    }

    /** L'AMI publié, date limite passée, liste pas encore arrêtée : 409 {@code LECTURE_FERMEE}, {@code LISTE_ARRETEE}, {@code AMI_INFRUCTUEUX}. */
    private Ami exigerEnNotation(Long idDmc) {
        Ami a = exigerPublieOuClos(idDmc);
        if (Ami.INFRUCTUEUX.equals(a.getEtat())) {
            throw new BusinessRuleException("L'AMI est déclaré infructueux.", "AMI_INFRUCTUEUX");
        }
        if (a.getListeArreteeLe() != null) {
            throw new BusinessRuleException("La liste restreinte est arrêtée.", LISTE_ARRETEE);
        }
        if (a.getDateLimite().isAfter(maintenant())) {
            throw new BusinessRuleException("Les expressions d'intérêt se lisent après la date limite (" + a.getDateLimite().format(HORODATAGE) + ").",
                    "LECTURE_FERMEE");
        }
        return a;
    }

    private Ami exigerRapportASigner(Long idDmc) {
        Ami a = exigerPublieOuClos(idDmc);
        if (a.getListeArreteeLe() == null) {
            throw new BusinessRuleException("Le rapport de présélection n'est pas produit.", "RAPPORT_NON_PRODUIT");
        }
        if (a.getListeDefinitiveLe() != null) {
            throw new BusinessRuleException("Le rapport de présélection est déjà signé.", "DEJA_SIGNE");
        }
        return a;
    }

    private String membreAppelant(Long idDmc) {
        String ref = CurrentUser.ref().orElse(null);
        return ref != null && TypeActeur.MEMBRE_CAO.name().equals(CurrentUser.acteurType().orElse(null)) && internes.membresCao(idDmc).contains(ref)
                ? ref : null;
    }

    private String exigerDecideur(Long idDmc) {
        String k = membreAppelant(idDmc);
        if (k == null) {
            throw new AccessDeniedException("La présélection se fait par les membres de la commission d'appel d'offres.");
        }
        AmiDeclaration d = declarations.findByIdDmcAndIm(idDmc, k).orElseThrow(() -> new BusinessRuleException(
                "Signez d'abord votre déclaration d'absence de conflit d'intérêts et de confidentialité.", "DECLARATION_MANQUANTE"));
        if (Boolean.TRUE.equals(d.getConflit())) {
            throw new AccesReserveException("Vous avez déclaré un conflit d'intérêts : vous ne décidez rien sur cet AMI.", "MEMBRE_EN_CONFLIT");
        }
        return k;
    }

    private String exigerPresident(Long idDmc) {
        String k = exigerDecideur(idDmc);
        boolean president = caoMembres.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).stream()
                .anyMatch(m -> k.equals(m.getIdCompte()) && Boolean.TRUE.equals(m.getPresident()));
        if (!president) {
            throw new AccessDeniedException("La liste restreinte s'arrête par le président de la commission.");
        }
        return k;
    }

    private void exigerPrmp(Long idDmc) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Ce geste de l'AMI est réservé à la PRMP de la fiche.");
        }
        fiches.controlerLecture(idDmc);
    }

    private void exigerLecteur(Long idDmc) {
        if (membreAppelant(idDmc) != null) {
            return;
        }
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p == ProfilUtilisateur.PRMP || p == ProfilUtilisateur.UGPM) {
            fiches.controlerLecture(idDmc);
            return;
        }
        throw new AccessDeniedException("La présélection se lit par la PRMP, l'UGPM et la commission d'appel d'offres.");
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private static void sous(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, t));
    }

    private void tracer(Long idDmc, String action, String detail) {
        journal.save(new EvaluationJournal(null, idDmc, maintenant(), CurrentUser.ref().or(CurrentUser::login).orElse(null), action, detail));
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
