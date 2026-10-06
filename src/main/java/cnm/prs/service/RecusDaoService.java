package cnm.prs.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.ProcedureEnLigneDto;
import cnm.prs.dto.RecuDto;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Entreprise;
import cnm.prs.entity.RecuDao;
import cnm.prs.entity.RecuJournal;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.EntrepriseRepository;
import cnm.prs.repository.RecuDaoRepository;
import cnm.prs.repository.RecuJournalRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>Le reçu du paiement des frais de dossier</strong> (demande front du 2026-10-06, « Voie B » du pilote ; V72). Le
 * candidat dépose le reçu de son entreprise (la clé est le NIF) ; la PRMP ou l'UGPM de la fiche le valide ou le refuse avec un
 * motif, sans retour ; un reçu validé ouvre le retrait du DAO (garde dans {@link ProceduresEnLigneService#retirer}). La plateforme
 * n'encaisse rien : la vérification est documentaire (H3).
 */
@Service
@Transactional
public class RecusDaoService {

    static final String DEPOSE = "RECU_DEPOSE";
    static final String VALIDE = "RECU_VALIDE";
    static final String REFUSE = "RECU_REFUSE";

    private final RecuDaoRepository recus;
    private final RecuJournalRepository journal;
    private final ProceduresEnLigneService procedures;
    private final EntrepriseRepository entreprises;
    private final CompteCandidatRepository candidats;
    private final ExclusionArmpService exclusions;
    private final ParametreService parametres;
    private final FicheMarcheService fiches;
    private final CeremonieService ceremonies;
    private final NotificationService notifications;
    private final Clock horloge;

    public RecusDaoService(RecuDaoRepository recus, RecuJournalRepository journal, ProceduresEnLigneService procedures,
            EntrepriseRepository entreprises, CompteCandidatRepository candidats, ExclusionArmpService exclusions, ParametreService parametres,
            FicheMarcheService fiches, CeremonieService ceremonies, NotificationService notifications, Clock horloge) {
        this.recus = recus;
        this.journal = journal;
        this.procedures = procedures;
        this.entreprises = entreprises;
        this.candidats = candidats;
        this.exclusions = exclusions;
        this.parametres = parametres;
        this.fiches = fiches;
        this.ceremonies = ceremonies;
        this.notifications = notifications;
        this.horloge = horloge;
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge);
    }

    // ------------------------------------------------------------------ §B2 le candidat

    /**
     * Dépose le reçu de l'entreprise du candidat (201). 404 procédure ; 409 {@code PROCEDURE_FERMEE} (date limite passée, ou dossier
     * gratuit : rien à payer), {@code ENTREPRISE_ABSENTE}, {@code ENTREPRISE_EXCLUE}, {@code RECU_EN_ATTENTE}, {@code RECU_DEJA_VALIDE}
     * (sauf pour des lots encore non couverts) ; 400 par champ, {@code FORMAT_INVALIDE} ; 413 au-delà de {@code tailleMaxPieceMo}.
     */
    public RecuDto deposer(Long idDmc, MultipartFile fichier, RecuDto.Corps corps) {
        String moi = candidat();
        ProceduresEnLigneService.Lue l = procedures.exiger(idDmc);
        if (ProceduresEnLigneService.CLOSE.equals(l.dto().etat())) {
            throw new BusinessRuleException("La date limite de remise des offres est passée : le reçu ne se dépose plus.", "PROCEDURE_FERMEE");
        }
        if (l.dto().fraisDossier() == null) {
            throw new BusinessRuleException("Le dossier de cette procédure est gratuit : il n'y a pas de reçu à déposer.", "PROCEDURE_FERMEE");
        }
        Entreprise e = entreprises.findByIdCandidat(moi)
                .orElseThrow(() -> new BusinessRuleException("Déclarez votre entreprise avant de déposer un reçu.", "ENTREPRISE_ABSENTE"));
        exclusions.enCours(e.getNif(), maintenant().toLocalDate()).ifPresent(x -> {
            throw new BusinessRuleException("Votre entreprise (NIF " + e.getNif() + ") est exclue des marchés publics par la décision de "
                    + "l'ARMP " + x.getReferenceDecision() + ".", "ENTREPRISE_EXCLUE");
        });
        RecuDto.Corps c = corps == null ? new RecuDto.Corps(null, null, null, null, null) : corps;
        Set<Integer> lotsProcedure = new LinkedHashSet<>();
        l.dto().lots().forEach(x -> lotsProcedure.add(x.numero()));
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        List<Integer> lots = c.lots() == null || c.lots().isEmpty() || lotsProcedure.size() <= 1 ? null
                : c.lots().stream().distinct().sorted().toList();
        if (lots != null && !lotsProcedure.containsAll(lots)) {
            erreurs.add(new ErrorResponse.FieldError("lots", "Lots inconnus de la procédure : " + lots + " (lots " + lotsProcedure + ")."));
        }
        if (c.montant() == null || c.montant().signum() <= 0) {
            erreurs.add(new ErrorResponse.FieldError("montant", "Le montant payé est attendu."));
        }
        if (c.referencePaiement() == null || c.referencePaiement().isBlank()) {
            erreurs.add(new ErrorResponse.FieldError("referencePaiement", "La référence du paiement est attendue."));
        }
        if (c.datePaiement() == null) {
            erreurs.add(new ErrorResponse.FieldError("datePaiement", "La date du paiement est attendue."));
        } else if (c.datePaiement().isAfter(maintenant().toLocalDate())) {
            erreurs.add(new ErrorResponse.FieldError("datePaiement", "La date du paiement ne peut pas être future."));
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        List<RecuDao> siens = recus.findByIdDmcAndNifOrderByDateDepotDescIdRecuDesc(idDmc, e.getNif());
        if (siens.stream().anyMatch(r -> RecuDao.EN_ATTENTE.equals(r.getEtat()))) {
            throw new BusinessRuleException("Un reçu de votre entreprise attend la décision de la PRMP.", "RECU_EN_ATTENTE");
        }
        List<RecuDao> valides = siens.stream().filter(r -> RecuDao.VALIDE.equals(r.getEtat())).toList();
        boolean toutCouvert = valides.stream().anyMatch(r -> r.getLots() == null);
        Set<String> couverts = new LinkedHashSet<>();
        valides.forEach(r -> couverts.addAll(ChampFicheMarche.liste(r.getLots())));
        if (!valides.isEmpty() && (toutCouvert || lots == null || lots.stream().allMatch(n -> couverts.contains(String.valueOf(n))))) {
            throw new BusinessRuleException("Un reçu validé couvre déjà " + (toutCouvert ? "tout le dossier" : "ces lots")
                    + " : un nouveau reçu ne sert que pour des lots encore non couverts.", "RECU_DEJA_VALIDE");
        }
        byte[] contenu = contenu(fichier);
        RecuDao r = new RecuDao();
        r.setIdDmc(idDmc);
        r.setIdEntreprise(e.getIdEntreprise());
        r.setNif(e.getNif());
        r.setRaisonSociale(e.getRaisonSociale());
        r.setIdCandidat(moi);
        r.setLots(lots == null ? null : String.join(",", lots.stream().map(String::valueOf).toList()));
        r.setMontant(c.montant());
        r.setReferencePaiement(c.referencePaiement().trim());
        r.setDatePaiement(c.datePaiement());
        r.setBanque(c.banque() == null || c.banque().isBlank() ? null : c.banque().trim());
        r.setNomFichier(fichier.getOriginalFilename());
        r.setFormat(EntrepriseCandidatService.format(contenu));
        r.setTailleOctets((long) contenu.length);
        r.setEmpreinte(sha256(contenu));
        r.setContenu(contenu);
        r.setDateDepot(maintenant());
        r.setEtat(RecuDao.EN_ATTENTE);
        r = recus.save(r);
        tracer(r, DEPOSE, e.getRaisonSociale() + " (NIF " + e.getNif() + "), " + lotsTexte(r) + ", référence " + r.getReferencePaiement());
        ceremonies.notifierPrmp(idDmc, TypeNotification.RECU_A_VALIDER, "Reçu de frais de dossier à valider — " + l.dto().reference(),
                e.getRaisonSociale() + " a déposé le reçu du paiement des frais de dossier (" + lotsTexte(r) + ") : validez-le ou refusez-le.");
        return dto(r, false, l.dto());
    }

    /** {@code GET …/recus/mien} : le reçu le plus récent de l'entreprise du candidat ; 404 sinon. */
    @Transactional(readOnly = true)
    public RecuDto mien(Long idDmc) {
        RecuDao r = dernier(idDmc);
        return dto(r, false, null);
    }

    /** {@code GET …/recus/mien/fichier} : le fichier du reçu le plus récent ; 404 sans reçu ou après la purge. */
    @Transactional(readOnly = true)
    public RecuDao fichierMien(Long idDmc) {
        return avecFichier(dernier(idDmc));
    }

    private RecuDao dernier(Long idDmc) {
        String nif = procedures.nifDe(candidat());
        return (nif == null ? List.<RecuDao>of() : recus.findByIdDmcAndNifOrderByDateDepotDescIdRecuDesc(idDmc, nif)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Aucun reçu déposé par votre entreprise pour la procédure " + idDmc + "."));
    }

    // ------------------------------------------------------------------ §B3 la PRMP et l'UGPM

    /** Les reçus de la procédure : les {@code EN_ATTENTE} d'abord, puis du plus récent au plus ancien. PRMP et UGPM de la fiche. */
    @Transactional(readOnly = true)
    public List<RecuDto> lister(Long idDmc) {
        exigerPrmp(idDmc);
        ProcedureEnLigneDto p = procedure(idDmc);
        return recus.findByIdDmcOrderByDateDepotDescIdRecuDesc(idDmc).stream()
                .sorted(Comparator.comparing((RecuDao r) -> !RecuDao.EN_ATTENTE.equals(r.getEtat())))
                .map(r -> dto(r, true, p)).toList();
    }

    @Transactional(readOnly = true)
    public RecuDao fichier(Long idDmc, Integer idRecu) {
        exigerPrmp(idDmc);
        return avecFichier(recu(idDmc, idRecu));
    }

    /** Valide un reçu {@code EN_ATTENTE} : 409 {@code RECU_DEJA_DECIDE} sinon ; le candidat est averti par courriel. */
    public RecuDto valider(Long idDmc, Integer idRecu) {
        exigerPrmp(idDmc);
        RecuDao r = enAttente(idDmc, idRecu);
        decider(r, RecuDao.VALIDE, null);
        tracer(r, VALIDE, r.getRaisonSociale() + ", " + lotsTexte(r));
        ProcedureEnLigneDto p = procedure(idDmc);
        avertir(r, TypeNotification.RECU_VALIDE, "Reçu de frais de dossier validé", "Votre reçu (" + r.getReferencePaiement()
                + ") pour la procédure « " + (p == null ? idDmc : p.reference()) + " » est validé : vous pouvez retirer le dossier d'appel "
                + "d'offres.");
        return dto(r, true, p);
    }

    /** Refuse un reçu {@code EN_ATTENTE}, motif obligatoire (400 {@code MOTIF_ABSENT}) ; 409 {@code RECU_DEJA_DECIDE}. */
    public RecuDto refuser(Long idDmc, Integer idRecu, RecuDto.Refus refus) {
        exigerPrmp(idDmc);
        if (refus == null || refus.motif() == null || refus.motif().isBlank()) {
            throw new BadRequestException("Le refus d'un reçu exige un motif, transmis au candidat.", "MOTIF_ABSENT");
        }
        RecuDao r = enAttente(idDmc, idRecu);
        decider(r, RecuDao.REFUSE, refus.motif().trim());
        tracer(r, REFUSE, r.getRaisonSociale() + " : " + r.getMotifRefus());
        ProcedureEnLigneDto p = procedure(idDmc);
        avertir(r, TypeNotification.RECU_REFUSE, "Reçu de frais de dossier refusé", "Votre reçu (" + r.getReferencePaiement()
                + ") pour la procédure « " + (p == null ? idDmc : p.reference()) + " » est refusé : " + r.getMotifRefus()
                + ". Déposez un nouveau reçu.");
        return dto(r, true, p);
    }

    // ------------------------------------------------------------------ outils

    private void decider(RecuDao r, String etat, String motif) {
        r.setEtat(etat);
        r.setMotifRefus(motif);
        r.setDateDecision(maintenant());
        r.setDecidePar(CurrentUser.profil().map(Enum::name).orElse(null));
        r.setLoginDecideur(CurrentUser.login().orElse(null));
        recus.save(r);
    }

    private RecuDao enAttente(Long idDmc, Integer idRecu) {
        RecuDao r = recu(idDmc, idRecu);
        if (!RecuDao.EN_ATTENTE.equals(r.getEtat())) {
            throw new BusinessRuleException("Ce reçu est déjà " + (RecuDao.VALIDE.equals(r.getEtat()) ? "validé" : "refusé")
                    + " : une décision ne se reprend pas ; une erreur se corrige par un nouveau dépôt du candidat.", "RECU_DEJA_DECIDE");
        }
        return r;
    }

    private RecuDao recu(Long idDmc, Integer idRecu) {
        return recus.findById(idRecu).filter(r -> idDmc.equals(r.getIdDmc()))
                .orElseThrow(() -> new ResourceNotFoundException("Reçu introuvable : " + idRecu + "."));
    }

    private static RecuDao avecFichier(RecuDao r) {
        if (r.getContenu() == null) {
            throw new ResourceNotFoundException(r.getPurgeLe() != null ? "Le fichier du reçu a été purgé au terme de sa conservation."
                    : "Le reçu n'a pas de fichier.");
        }
        return r;
    }

    private void avertir(RecuDao r, TypeNotification type, String titre, String corps) {
        CompteCandidat c = candidats.findById(r.getIdCandidat()).orElse(null);
        notifications.emettreCandidat(type, r.getIdCandidat(), c == null ? null : c.getEmail(), r.getIdDmc().intValue(), TypeObjet.PROCEDURE,
                titre, corps);
    }

    private void tracer(RecuDao r, String action, String detail) {
        journal.save(new RecuJournal(null, r.getIdRecu(), r.getIdDmc(), maintenant(), CurrentUser.ref().or(CurrentUser::login).orElse(null),
                action, detail));
    }

    private RecuDto dto(RecuDao r, boolean prmp, ProcedureEnLigneDto p) {
        List<Integer> lots = r.getLots() == null ? null : ChampFicheMarche.liste(r.getLots()).stream().map(Integer::valueOf).toList();
        BigDecimal attendus = null;
        if (prmp && p != null && p.fraisDossier() != null) {
            attendus = p.fraisDossier().stream().filter(f -> lots == null || f.lot() == null || lots.contains(f.lot()))
                    .map(ProcedureEnLigneDto.Frais::montant).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        String compte = prmp ? candidats.findById(r.getIdCandidat()).map(CompteCandidat::getEmail).orElse(r.getIdCandidat()) : null;
        return new RecuDto(r.getIdRecu(), lots, r.getMontant(), r.getReferencePaiement(), r.getDatePaiement(), r.getBanque(), r.getNomFichier(),
                r.getDateDepot(), r.getEtat(), r.getMotifRefus(), r.getDateDecision(), r.getDecidePar(),
                prmp ? new RecuDto.Entreprise(r.getRaisonSociale(), r.getNif()) : null, compte, attendus,
                prmp ? attendus != null && r.getMontant() != null && r.getMontant().compareTo(attendus) < 0 : null);
    }

    private ProcedureEnLigneDto procedure(Long idDmc) {
        return procedures.trouver(idDmc).map(ProceduresEnLigneService.Lue::dto).orElse(null);
    }

    private static String lotsTexte(RecuDao r) {
        return r.getLots() == null ? "tout le dossier" : "lot(s) " + r.getLots();
    }

    private byte[] contenu(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Le fichier du reçu est attendu.", "FICHIER_ABSENT");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Lecture du fichier impossible.", "FICHIER_ABSENT");
        }
        int mo = parametres.candidats().tailleMaxPieceMo();
        if (contenu.length > mo * 1024L * 1024L) {
            throw new PayloadTropVolumineuxException("Le reçu dépasse " + mo + " Mo.");
        }
        if (EntrepriseCandidatService.format(contenu) == null) {
            throw new BadRequestException("Le reçu doit être un PDF, un JPEG ou un PNG (type lu sur le contenu).", "FORMAT_INVALIDE");
        }
        return contenu;
    }

    private static String sha256(byte[] d) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(d));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String candidat() {
        String ref = CurrentUser.ref().orElse(null);
        if (ref == null || !TypeActeur.CANDIDAT.name().equals(CurrentUser.acteurType().orElse(null))) {
            throw new AccessDeniedException("Le reçu des frais de dossier se dépose par un candidat connecté.");
        }
        return ref;
    }

    /** La PRMP ou l'UGPM de la fiche (sur le modèle de la CAO, arbitrage du 04/10) ; pas l'Administrateur. */
    private void exigerPrmp(Long idDmc) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p != ProfilUtilisateur.PRMP && p != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("Les reçus des frais de dossier se valident par la PRMP de la fiche (ou son UGPM).");
        }
        fiches.controlerLecture(idDmc);
    }
}
