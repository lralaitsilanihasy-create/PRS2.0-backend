package cnm.prs.service;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.FinanciereDto;
import cnm.prs.dto.NegociationDto;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.EvaluationJournal;
import cnm.prs.entity.Negociation;
import cnm.prs.entity.Offre;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.EvaluationJournalRepository;
import cnm.prs.repository.NegociationRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>La négociation des prestations intellectuelles</strong> (art. 42-IV), lot 3, tranche PI-d2a (demande front du 2026-10-07,
 * §B6 ; V89 ; arbitrages du pilote du 2026-10-08) :
 * <ul>
 *   <li>conduite par la <strong>PRMP</strong> (ou son UGPM), le classement du lot arrêté, avec le <strong>seul</strong> candidat
 *   classé dont c'est le tour (409 {@code NEGOCIATION_EN_COURS} tant qu'une autre est ouverte) ; il est invité (date, lieu
 *   {@code B06-NG-01}) ;</li>
 *   <li>elle se conclut par un <strong>procès-verbal</strong> (date, lieu, texte, pièce jointe facultative ; PDF et Word) ; le serveur
 *   ne contrôle pas qu'elle reste non substantielle (termes de référence, conditions contractuelles, prix) ;</li>
 *   <li>l'<strong>échec</strong>, motivé, ouvre la voie au candidat classé suivant (Q7, en attente du juriste) ; quand seule la
 *   financière du premier a été ouverte (qualité technique exclusivement, qualification du consultant), celle du suivant s'ouvre
 *   d'abord en <strong>séance complémentaire</strong> (409 {@code FINANCIERE_NON_OUVERTE}).</li>
 * </ul>
 */
@Service
@Transactional
public class NegociationService {

    static final String LIEU = "B06-NG-01";
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EvaluationFinanciereService financiere;
    private final EvaluationTechniqueService technique;
    private final EvaluationService evaluation;
    private final NegociationRepository negociations;
    private final OffreRepository offres;
    private final FicheMarcheService fiches;
    private final ParametresInternesService internes;
    private final NotificationService notifications;
    private final CompteCandidatRepository candidats;
    private final GenerateurDocumentsFiche generateur;
    private final EvaluationJournalRepository journal;
    private final Clock horloge;

    public NegociationService(EvaluationFinanciereService financiere, EvaluationTechniqueService technique, EvaluationService evaluation,
            NegociationRepository negociations, OffreRepository offres, FicheMarcheService fiches, ParametresInternesService internes,
            NotificationService notifications, CompteCandidatRepository candidats, GenerateurDocumentsFiche generateur,
            EvaluationJournalRepository journal, Clock horloge) {
        this.financiere = financiere;
        this.technique = technique;
        this.evaluation = evaluation;
        this.negociations = negociations;
        this.offres = offres;
        this.fiches = fiches;
        this.internes = internes;
        this.notifications = notifications;
        this.candidats = candidats;
        this.generateur = generateur;
        this.journal = journal;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ lecture

    /** Les négociations de chaque lot et le prochain candidat : CAO, responsable, PRMP, UGPM. */
    @Transactional(readOnly = true)
    public NegociationDto lire(Long idDmc) {
        evaluation.controlerLecture(idDmc);
        return dto(idDmc);
    }

    @Transactional(readOnly = true)
    public byte[] pv(Long idDmc, Long id, boolean docx) {
        evaluation.controlerLecture(idDmc);
        Negociation n = exiger(idDmc, id);
        byte[] b = docx ? n.getPvDocx() : n.getPv();
        if (b == null) {
            throw new ResourceNotFoundException("Le procès-verbal de cette négociation n'est pas produit.");
        }
        return b;
    }

    @Transactional(readOnly = true)
    public Negociation piece(Long idDmc, Long id) {
        evaluation.controlerLecture(idDmc);
        Negociation n = exiger(idDmc, id);
        if (n.getPiece() == null) {
            throw new ResourceNotFoundException("Aucune pièce n'est jointe à cette négociation.");
        }
        return n;
    }

    // ------------------------------------------------------------------ l'ouverture

    /**
     * La PRMP ouvre la négociation d'un lot avec le candidat dont c'est le tour : 403 hors PRMP/UGPM ; 409 {@code CLASSEMENT_NON_ARRETE},
     * {@code NEGOCIATION_EN_COURS}, {@code NEGOCIATION_CONCLUE}, {@code AUCUN_CANDIDAT_A_NEGOCIER}, {@code FINANCIERE_NON_OUVERTE}
     * ({@code details} : {@code idOffre}, {@code numero} — une séance complémentaire l'ouvre), {@code EVALUATION_CLOSE}.
     */
    public NegociationDto ouvrir(Long idDmc, Integer lot, NegociationDto.OuvertureRequest r) {
        exigerPrmp(idDmc);
        technique.exigerEnCours(idDmc);
        EvaluationFinanciereService.lot(financiere.calculer(idDmc), lot);
        if (!financiere.arrete(idDmc, lot)) {
            throw new BusinessRuleException("Le classement de ce lot n'est pas arrêté.", "CLASSEMENT_NON_ARRETE");
        }
        List<Negociation> deja = negociations.findByIdDmcAndLotOrderByIdAsc(idDmc, lot);
        if (deja.stream().anyMatch(n -> Negociation.EN_COURS.equals(n.getEtat()))) {
            throw new BusinessRuleException("Une négociation est en cours sur ce lot : jamais avec deux candidats à la fois.", "NEGOCIATION_EN_COURS");
        }
        if (deja.stream().anyMatch(n -> Negociation.REUSSIE.equals(n.getEtat()))) {
            throw new BusinessRuleException("La négociation de ce lot a abouti.", "NEGOCIATION_CONCLUE");
        }
        FinanciereDto.Ligne p = suivant(idDmc, lot, deja);
        if (p == null) {
            throw new BusinessRuleException("Plus aucun candidat classé n'est à inviter à négocier sur ce lot.", "AUCUN_CANDIDAT_A_NEGOCIER");
        }
        if (!p.financiereOuverte()) {
            throw new BusinessRuleException("La proposition financière du candidat n° " + p.numero() + " n'est pas ouverte : le responsable de la "
                    + "procédure l'ouvre en séance complémentaire.", "FINANCIERE_NON_OUVERTE", null, Map.of("idOffre", p.idOffre(), "numero", p.numero()));
        }
        String lieu = r != null && r.lieu() != null && !r.lieu().isBlank() ? r.lieu().trim()
                : fiches.etatValide(idDmc).map(v -> v.valeur(LIEU)).orElse(null);
        Negociation n = new Negociation();
        n.setIdDmc(idDmc);
        n.setLot(lot);
        n.setIdOffre(p.idOffre());
        n.setIdFinanciere(p.idFinanciere());
        n.setNumero(p.numero());
        n.setRaisonSociale(p.raisonSociale());
        n.setRang(p.rang());
        n.setEtat(Negociation.EN_COURS);
        n.setOuverteLe(maintenant());
        n.setOuvertePar(acteur());
        n.setPrevueLe(r == null ? null : r.prevueLe());
        n.setLieu(lieu);
        negociations.saveAndFlush(n);
        tracer(idDmc, "NEGOCIATION_OUVERTE", "Lot " + lot + " : négociation avec le candidat classé " + p.rang() + " (n° " + p.numero() + ", "
                + p.raisonSociale() + ")" + (deja.isEmpty() ? "" : ", après " + deja.size() + " échec(s)"));
        Offre tech = offres.findById(p.idOffre()).orElseThrow();
        CompteCandidat c = candidats.findById(tech.getIdCandidat()).orElse(null);
        notifications.emettreCandidat(TypeNotification.NEGOCIATION, tech.getIdCandidat(), c == null ? null : c.getEmail(), idDmc.intValue(),
                TypeObjet.PROCEDURE, "Invitation à négocier", "Votre proposition n° " + p.numero() + " est classée au rang " + p.rang()
                        + " : vous êtes invité à négocier" + (n.getPrevueLe() == null ? "" : " le " + n.getPrevueLe().format(HORODATAGE))
                        + (lieu == null ? "" : ", " + lieu) + ".");
        return dto(idDmc);
    }

    /** La pièce jointe au procès-verbal (remplace la précédente) : PRMP/UGPM ; 400 {@code FICHIER_VIDE} ; 409 {@code NEGOCIATION_CLOSE}. */
    public NegociationDto joindre(Long idDmc, Long id, MultipartFile fichier) {
        exigerPrmp(idDmc);
        technique.exigerEnCours(idDmc);
        Negociation n = exigerEnCours(idDmc, id);
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("La pièce jointe est vide.", "FICHIER_VIDE");
        }
        try {
            n.setPiece(fichier.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("La pièce jointe est illisible.", "FICHIER_VIDE");
        }
        n.setPieceNom(fichier.getOriginalFilename());
        n.setPieceType(fichier.getContentType());
        negociations.save(n);
        tracer(idDmc, "NEGOCIATION_PIECE", "Lot " + n.getLot() + ", n° " + n.getNumero() + " : pièce jointe « " + n.getPieceNom() + " »");
        return dto(idDmc);
    }

    // ------------------------------------------------------------------ la conclusion

    /**
     * La PRMP conclut la négociation et produit son procès-verbal : 400 {@code RESULTAT_INVALIDE}, {@code DATE_OBLIGATOIRE},
     * {@code PV_OBLIGATOIRE}, {@code MOTIF_OBLIGATOIRE} (échec) ; 409 {@code NEGOCIATION_CLOSE}, {@code MONTANT_NON_EVALUE} (réussite
     * sans proposition financière évaluée).
     */
    public NegociationDto conclure(Long idDmc, Long id, NegociationDto.ConclusionRequest r) {
        exigerPrmp(idDmc);
        technique.exigerEnCours(idDmc);
        Negociation n = exigerEnCours(idDmc, id);
        String resultat = r == null || r.resultat() == null ? null : r.resultat().trim().toUpperCase(java.util.Locale.ROOT);
        if (!Set.of(Negociation.REUSSIE, Negociation.ECHOUEE).contains(resultat)) {
            throw new BadRequestException("Le résultat est REUSSIE ou ECHOUEE.", "RESULTAT_INVALIDE");
        }
        if (r.dateNegociation() == null) {
            throw new BadRequestException("La date de la négociation est attendue.", "DATE_OBLIGATOIRE");
        }
        String texte = nettoyer(r.texte());
        if (texte == null) {
            throw new BadRequestException("Le procès-verbal de négociation porte son texte.", "PV_OBLIGATOIRE");
        }
        String motif = nettoyer(r.motif());
        if (Negociation.ECHOUEE.equals(resultat) && motif == null) {
            throw new BadRequestException("L'échec de la négociation se motive.", "MOTIF_OBLIGATOIRE");
        }
        if (Negociation.REUSSIE.equals(resultat)) {
            FinanciereDto.Ligne p = EvaluationFinanciereService.lot(financiere.calculer(idDmc), n.getLot()).propositions().stream()
                    .filter(x -> x.idOffre().equals(n.getIdOffre())).findFirst().orElse(null);
            if (p == null || p.saisie() == null || p.saisie().refus() != null) {
                throw new BusinessRuleException("La proposition financière de ce candidat n'est pas évaluée : saisissez-la avant de conclure.",
                        "MONTANT_NON_EVALUE");
            }
        }
        n.setEtat(resultat);
        n.setDateNegociation(r.dateNegociation());
        if (nettoyer(r.lieu()) != null) {
            n.setLieu(r.lieu().trim());
        }
        n.setTexte(texte);
        n.setMotifEchec(Negociation.ECHOUEE.equals(resultat) ? motif : null);
        n.setConclueLe(maintenant());
        n.setConcluePar(acteur());
        for (GenerateurDocumentsFiche.Fichier f : generateur.generer(document(n))) {
            if ("pdf".equals(f.extension())) {
                n.setPv(f.contenu());
            } else if ("docx".equals(f.extension())) {
                n.setPvDocx(f.contenu());
            }
        }
        negociations.save(n);
        tracer(idDmc, Negociation.REUSSIE.equals(resultat) ? "NEGOCIATION_REUSSIE" : "NEGOCIATION_ECHOUEE", "Lot " + n.getLot() + ", n° "
                + n.getNumero() + " (" + n.getRaisonSociale() + ")" + (motif == null || Negociation.REUSSIE.equals(resultat) ? "" : " : " + motif));
        String titre = Negociation.REUSSIE.equals(resultat) ? "Négociation conclue" : "Négociation sans accord";
        for (String k : internes.membresCao(idDmc)) {
            internes.notifierMembre(idDmc, k, TypeNotification.NEGOCIATION, titre, "Procédure " + idDmc + ", lot " + n.getLot() + " : la négociation "
                    + "avec le candidat n° " + n.getNumero() + (Negociation.REUSSIE.equals(resultat) ? " a abouti." : " a échoué (" + motif + ")."));
        }
        return dto(idDmc);
    }

    private DocumentLibre document(Negociation n) {
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "PROCÈS-VERBAL DE NÉGOCIATION"));
        para(el, "Procédure " + n.getIdDmc() + ", lot " + n.getLot() + ". Négociation menée le " + n.getDateNegociation().format(DATE)
                + (n.getLieu() == null ? "" : ", " + n.getLieu()) + ", avec le candidat classé au rang " + n.getRang() + " : proposition n° "
                + n.getNumero() + " — " + n.getRaisonSociale() + ".");
        para(el, "La négociation ne modifie de manière substantielle ni la mission des termes de référence, ni les conditions contractuelles, "
                + "ni le prix (art. 42-IV).");
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Déroulement et conclusions"));
        para(el, n.getTexte());
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "Résultat"));
        para(el, Negociation.REUSSIE.equals(n.getEtat()) ? "La négociation a abouti." : "La négociation n'a pas abouti. Motif : " + n.getMotifEchec());
        if (n.getPieceNom() != null) {
            para(el, "Pièce jointe : " + n.getPieceNom() + ".");
        }
        para(el, "Établi le " + n.getConclueLe().format(HORODATAGE) + ".");
        return new DocumentLibre("PV_NEGOCIATION", null, el, "Procédure " + n.getIdDmc() + " — PV de négociation, lot " + n.getLot());
    }

    // ------------------------------------------------------------------ le suivant

    /** Le candidat avec qui négocier ensuite sur un lot ; nul si une négociation est en cours ou réussie, ou s'il n'en reste aucun. */
    @Transactional(readOnly = true)
    public FinanciereDto.Ligne prochain(Long idDmc, Integer lot) {
        List<Negociation> deja = negociations.findByIdDmcAndLotOrderByIdAsc(idDmc, lot);
        if (deja.stream().anyMatch(n -> !Negociation.ECHOUEE.equals(n.getEtat()))) {
            return null;
        }
        return suivant(idDmc, lot, deja);
    }

    private FinanciereDto.Ligne suivant(Long idDmc, Integer lot, List<Negociation> deja) {
        Set<String> echoues = new java.util.HashSet<>(deja.stream().map(Negociation::getIdOffre).toList());
        return financiere.classementArrete(idDmc, lot).stream().filter(p -> !echoues.contains(p.idOffre())).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ outils

    private NegociationDto dto(Long idDmc) {
        FinanciereDto f = financiere.calculer(idDmc);
        List<Negociation> toutes = negociations.findByIdDmcOrderByIdAsc(idDmc);
        List<NegociationDto.Lot> lots = new ArrayList<>();
        for (FinanciereDto.Lot l : f.lots()) {
            List<Negociation> duLot = toutes.stream().filter(n -> Objects.equals(n.getLot(), l.lot())).toList();
            boolean arrete = l.arret() != null && l.arret().le() != null;
            FinanciereDto.Ligne p = arrete ? prochain(idDmc, l.lot()) : null;
            lots.add(new NegociationDto.Lot(l.lot(), arrete, p == null ? null : new NegociationDto.Prochain(p.idOffre(), p.numero(), p.raisonSociale(),
                    p.rang(), p.financiereOuverte()), duLot.stream().anyMatch(n -> Negociation.REUSSIE.equals(n.getEtat())),
                    duLot.stream().map(NegociationService::item).toList()));
        }
        return new NegociationDto(idDmc, lots);
    }

    private static NegociationDto.Item item(Negociation n) {
        return new NegociationDto.Item(n.getId(), n.getIdOffre(), n.getIdFinanciere(), n.getNumero(), n.getRaisonSociale(), n.getRang(), n.getEtat(),
                n.getOuverteLe(), n.getOuvertePar(), n.getPrevueLe(), n.getLieu(), n.getConclueLe(), n.getConcluePar(), n.getDateNegociation(),
                n.getTexte(), n.getMotifEchec(), n.getPieceNom(), n.getPv() != null);
    }

    private Negociation exiger(Long idDmc, Long id) {
        return negociations.findById(id).filter(n -> n.getIdDmc().equals(idDmc))
                .orElseThrow(() -> new ResourceNotFoundException("Négociation introuvable : " + id + "."));
    }

    private Negociation exigerEnCours(Long idDmc, Long id) {
        Negociation n = exiger(idDmc, id);
        if (!Negociation.EN_COURS.equals(n.getEtat())) {
            throw new BusinessRuleException("Cette négociation est conclue.", "NEGOCIATION_CLOSE");
        }
        return n;
    }

    /** Arbitrage du pilote (08/10) : la négociation est conduite par la PRMP de la fiche, ou son UGPM. */
    private void exigerPrmp(Long idDmc) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p != ProfilUtilisateur.PRMP && p != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("La négociation est conduite par la PRMP de la fiche (ou son UGPM).");
        }
        fiches.controlerLecture(idDmc);
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void tracer(Long idDmc, String action, String detail) {
        journal.save(new EvaluationJournal(null, idDmc, maintenant(), acteur(), action, detail));
    }

    private static String acteur() {
        return CurrentUser.ref().or(CurrentUser::login).orElse(null);
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }
}
