package cnm.prs.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.EntrepriseCandidatDto;
import cnm.prs.entity.Entreprise;
import cnm.prs.entity.EntrepriseJournal;
import cnm.prs.entity.PieceEntreprise;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.EntrepriseJournalRepository;
import cnm.prs.repository.EntrepriseRepository;
import cnm.prs.repository.PieceEntrepriseRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1b, §B3 et §B4) — l'<strong>entreprise</strong> d'un
 * candidat : un compte = une entreprise ; NIF, STAT et RCS uniques sur la plateforme (409 sans nommer l'autre compte) ;
 * ses pièces ; la <strong>vérification du NIF</strong> par deux voies, choisies par {@code CANDIDAT_VERIFICATION_NIF} :
 *
 * <ul>
 *   <li>{@code AUTOMATIQUE} : le {@link RaccordementDgi} ({@code VERIFIE_DGI}, {@code INCONNU_DGI}) ; injoignable ou
 *       illisible, la vérification retombe sur la voie sur pièces ;</li>
 *   <li>{@code SUR_PIECES} : l'Administrateur décide ({@code VERIFIE_SUR_PIECES}, {@code REFUSE_SUR_PIECES}, motif
 *       obligatoire pour un refus).</li>
 * </ul>
 * La vérification ne bloque jamais le dépôt. Tout changement de statut est journalisé avec ses valeurs. L'exclusion de
 * l'ARMP en cours pour le NIF est signalée ({@link ExclusionArmpService}), sans refus ici.
 */
@Service
@Transactional
public class EntrepriseCandidatService {

    public static final String NON_VERIFIE = "NON_VERIFIE";
    static final Set<String> TYPES_PIECE = Set.of("CARTE_FISCALE", "STATUTS", "POUVOIR", "AUTRE");
    static final Set<String> DECISIONS = Set.of("VERIFIE_SUR_PIECES", "REFUSE_SUR_PIECES");

    private final EntrepriseRepository entreprises;
    private final PieceEntrepriseRepository pieces;
    private final EntrepriseJournalRepository journal;
    private final ExclusionArmpService exclusions;
    private final RapprochementCandidatService rapprochements;
    private final RaccordementDgi dgi;
    private final ParametreService parametres;
    private final Clock horloge;

    public EntrepriseCandidatService(EntrepriseRepository entreprises, PieceEntrepriseRepository pieces,
            EntrepriseJournalRepository journal, ExclusionArmpService exclusions, RapprochementCandidatService rapprochements,
            RaccordementDgi dgi, ParametreService parametres, Clock horloge) {
        this.entreprises = entreprises;
        this.pieces = pieces;
        this.journal = journal;
        this.exclusions = exclusions;
        this.rapprochements = rapprochements;
        this.dgi = dgi;
        this.parametres = parametres;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ côté candidat

    /** L'entreprise du candidat connecté ; 404 si elle n'est pas encore déclarée. */
    @Transactional(readOnly = true)
    public EntrepriseCandidatDto.Entreprise lire(String idCandidat) {
        return dto(entreprises.findByIdCandidat(idCandidat)
                .orElseThrow(() -> new ResourceNotFoundException("Votre entreprise n'est pas encore déclarée.")));
    }

    /**
     * Déclare ou met à jour l'entreprise du candidat : 409 {@code NIF_EXISTANT} / {@code STAT_EXISTANT} /
     * {@code RCS_EXISTANT} si le numéro est déjà déclaré par une autre entreprise. Un NIF nouveau ou changé remet la
     * vérification à {@code NON_VERIFIE}, puis tente la voie automatique si le paramètre la demande.
     */
    public EntrepriseCandidatDto.Entreprise enregistrer(String idCandidat, EntrepriseCandidatDto.Saisie s) {
        String nif = NormalisationCandidat.identifiant(s.nif());
        String stat = NormalisationCandidat.identifiant(s.stat());
        String rcs = NormalisationCandidat.identifiant(s.rcs());
        Entreprise e = entreprises.findByIdCandidat(idCandidat).orElse(null);
        Integer moi = e == null ? null : e.getIdEntreprise();
        unique(entreprises.findByNif(nif).orElse(null), moi, "NIF", "NIF_EXISTANT");
        if (stat != null) {
            unique(entreprises.findByStat(stat).orElse(null), moi, "STAT", "STAT_EXISTANT");
        }
        if (rcs != null) {
            unique(entreprises.findByRcs(rcs).orElse(null), moi, "RCS", "RCS_EXISTANT");
        }
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        boolean nifChange = e == null || !nif.equals(e.getNif());
        if (e == null) {
            e = new Entreprise();
            e.setIdCandidat(idCandidat);
            e.setDateCreation(maintenant);
            e.setVerifStatut(NON_VERIFIE);
        }
        e.setRaisonSociale(s.raisonSociale().trim());
        e.setNif(nif);
        e.setStat(stat);
        e.setRcs(rcs);
        e.setAdresse(s.adresse().trim());
        e.setAdresseNormalisee(NormalisationCandidat.texte(s.adresse()));
        e.setRepNom(s.representant().nom().trim());
        e.setRepPrenom(s.representant().prenom().trim());
        e.setRepFonction(vide(s.representant().fonction()));
        e.setDateMaj(maintenant);
        e = entreprises.save(e);
        if (nifChange) {
            changerStatut(e, NON_VERIFIE, null, null, null);
            verifierAutomatiquement(e);
        }
        rapprochements.recalculer(idCandidat);
        return dto(e);
    }

    /** Ajoute une pièce : PDF, JPEG ou PNG (type réel), au plus {@code CANDIDAT_TAILLE_MAX_PIECE_MO} (413). */
    public EntrepriseCandidatDto.Piece ajouterPiece(String idCandidat, String type, MultipartFile fichier) {
        Entreprise e = entreprises.findByIdCandidat(idCandidat)
                .orElseThrow(() -> new BusinessRuleException("Déclarez d'abord votre entreprise.", "ENTREPRISE_ABSENTE"));
        if (type == null || !TYPES_PIECE.contains(type)) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError("type",
                    "Type de pièce : CARTE_FISCALE, STATUTS, POUVOIR ou AUTRE.")));
        }
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Fichier manquant ou vide.");
        }
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Lecture du fichier impossible.");
        }
        long max = parametres.candidats().tailleMaxPieceMo() * 1024L * 1024L;
        if (contenu.length > max) {
            throw new PayloadTropVolumineuxException("Le fichier dépasse " + parametres.candidats().tailleMaxPieceMo() + " Mo.");
        }
        String format = format(contenu);
        if (format == null) {
            throw new BadRequestException("Seuls les PDF, JPEG et PNG sont acceptés.");
        }
        PieceEntreprise p = pieces.save(new PieceEntreprise(null, e.getIdEntreprise(), type, fichier.getOriginalFilename(), format,
                (long) contenu.length, sha256(contenu), contenu, LocalDateTime.now(horloge)));
        return piece(p);
    }

    public void supprimerPiece(String idCandidat, Integer idPiece) {
        Entreprise e = entreprises.findByIdCandidat(idCandidat).orElse(null);
        PieceEntreprise p = pieces.findById(idPiece).orElse(null);
        if (e == null || p == null || !p.getIdEntreprise().equals(e.getIdEntreprise())) {
            throw new ResourceNotFoundException("Pièce " + idPiece + " introuvable.");
        }
        pieces.delete(p);
    }

    // ------------------------------------------------------------------ côté Administrateur

    /** Les entreprises d'un statut de vérification, les plus anciennes d'abord. */
    @Transactional(readOnly = true)
    public List<EntrepriseCandidatDto.Entreprise> parVerification(String statut) {
        return entreprises.findByVerifStatutOrderByDateCreationAscIdEntrepriseAsc(statut == null ? NON_VERIFIE : statut).stream()
                .map(this::dto).toList();
    }

    /** La décision de l'Administrateur sur pièces ; motif obligatoire pour un refus (400). */
    public EntrepriseCandidatDto.Verification decider(Integer idEntreprise, EntrepriseCandidatDto.Decision d) {
        Entreprise e = entreprises.findById(idEntreprise)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise " + idEntreprise + " introuvable."));
        if (!DECISIONS.contains(d.statut())) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError("statut",
                    "VERIFIE_SUR_PIECES ou REFUSE_SUR_PIECES.")));
        }
        if ("REFUSE_SUR_PIECES".equals(d.statut()) && (d.motif() == null || d.motif().isBlank())) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError("motif", "Le motif d'un refus est obligatoire.")));
        }
        changerStatut(e, d.statut(), "SUR_PIECES", CurrentUser.ref().or(CurrentUser::login).orElse(null), vide(d.motif()));
        entreprises.save(e);
        return verification(e);
    }

    /** Le fichier d'une pièce, pour l'Administrateur ; 404 si la pièce n'est pas de cette entreprise. */
    @Transactional(readOnly = true)
    public PieceEntreprise fichier(Integer idEntreprise, Integer idPiece) {
        PieceEntreprise p = pieces.findById(idPiece).orElse(null);
        if (p == null || !p.getIdEntreprise().equals(idEntreprise)) {
            throw new ResourceNotFoundException("Pièce " + idPiece + " introuvable pour l'entreprise " + idEntreprise + ".");
        }
        return p;
    }

    // ------------------------------------------------------------------ mécanique

    private void verifierAutomatiquement(Entreprise e) {
        if (!"AUTOMATIQUE".equals(parametres.candidats().verificationNif())) {
            return;
        }
        RaccordementDgi.Reponse r;
        try {
            r = dgi.verifier(e.getNif());
        } catch (RuntimeException ex) {
            r = RaccordementDgi.Reponse.INDISPONIBLE;   // injoignable : la voie sur pièces prend le relais
        }
        if (r == RaccordementDgi.Reponse.CONNU) {
            changerStatut(e, "VERIFIE_DGI", "DGI", "DGI", null);
        } else if (r == RaccordementDgi.Reponse.INCONNU) {
            changerStatut(e, "INCONNU_DGI", "DGI", "DGI", "Le NIF n'existe pas pour la DGI.");
        }
        entreprises.save(e);
    }

    private void changerStatut(Entreprise e, String statut, String source, String acteur, String motif) {
        String ancien = e.getVerifStatut();
        e.setVerifStatut(statut);
        e.setVerifSource(source);
        e.setVerifActeur(acteur);
        e.setVerifMotif(motif);
        e.setVerifDate(NON_VERIFIE.equals(statut) ? null : LocalDateTime.now(horloge));
        if (!Objects.equals(ancien, statut) || !NON_VERIFIE.equals(statut)) {
            journal.save(new EntrepriseJournal(null, e.getIdEntreprise(), LocalDateTime.now(horloge),
                    acteur != null ? acteur : CurrentUser.ref().or(CurrentUser::login).orElse(null), "VERIF_STATUT", ancien,
                    statut + (motif == null ? "" : " — " + motif)));
        }
    }

    private static void unique(Entreprise trouvee, Integer moi, String nom, String code) {
        if (trouvee != null && !trouvee.getIdEntreprise().equals(moi)) {
            throw new BusinessRuleException("Ce " + nom + " est déjà déclaré par une entreprise inscrite. Si c'est la vôtre, "
                    + "connectez-vous avec le compte qui l'a déclarée.", code);
        }
    }

    private EntrepriseCandidatDto.Entreprise dto(Entreprise e) {
        return new EntrepriseCandidatDto.Entreprise(e.getIdEntreprise(), e.getRaisonSociale(), e.getNif(), e.getStat(), e.getRcs(),
                e.getAdresse(), new EntrepriseCandidatDto.Representant(e.getRepNom(), e.getRepPrenom(), e.getRepFonction()),
                pieces.findByIdEntrepriseOrderByIdPieceAsc(e.getIdEntreprise()).stream().map(EntrepriseCandidatService::piece).toList(),
                verification(e), exclusions.enCoursAujourdhui(e.getNif()));
    }

    private static EntrepriseCandidatDto.Verification verification(Entreprise e) {
        return new EntrepriseCandidatDto.Verification(e.getVerifStatut(), e.getVerifSource(), e.getVerifDate(), e.getVerifActeur(),
                e.getVerifMotif());
    }

    private static EntrepriseCandidatDto.Piece piece(PieceEntreprise p) {
        return new EntrepriseCandidatDto.Piece(p.getIdPiece(), p.getType(), p.getNomFichier(), p.getFormat(), p.getTaille(),
                p.getDateDepot());
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Le type réel d'après les premiers octets : PDF, JPEG, PNG ; {@code null} sinon. */
    static String format(byte[] d) {
        if (d.length >= 4 && d[0] == 0x25 && d[1] == 0x50 && d[2] == 0x44 && d[3] == 0x46) {
            return "application/pdf";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47 && d[4] == 0x0D
                && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        return null;
    }

    private static String sha256(byte[] d) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(d));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
