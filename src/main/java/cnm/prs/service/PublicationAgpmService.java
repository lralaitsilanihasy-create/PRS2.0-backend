package cnm.prs.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.PublicationAgpmDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Marche;
import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.entity.PublicationAgpm;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutDossier;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.PublicationAgpmRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ V118 (2026-10-15, demande front « pièces de l'AMI et publication de l'AGPM », §B2 ; propositions du front validées par le pilote le
 * 15/10 ; arbitrages de l'utilisateur au backend : H1 numéro et date du journal lus sur le plan, figés ; H2 filiation) — la
 * <strong>publication de l'AGPM</strong> se dépose une fois, sur le plan de passation contrôlé, et vaut pour tous les dossiers dont la
 * ligne relève du plan :
 * <ul>
 *   <li>dépôt par la PRMP propriétaire ou son UGPM, plan au PV signé favorable (ou déjà remplacé par une mise à jour) ; une nouvelle
 *       publication remplace la précédente ;</li>
 *   <li>la publication <strong>en vigueur</strong> d'un plan est la sienne, à défaut celle de sa version précédente, et ainsi de suite
 *       (H2) ;</li>
 *   <li>un dossier conduit dans l'application (produit par une fiche, ou dossier de marché de son attribution) la reprend d'office sous la
 *       pièce {@code PUBLICATION_AGPM} à sa création et à sa soumission, s'il ne la porte pas déjà ;</li>
 *   <li>l'avis spécifique lit le numéro et la date du journal sur elle (H1).</li>
 * </ul>
 */
@Service
@Transactional
public class PublicationAgpmService {

    public static final String CODE_PIECE = "PUBLICATION_AGPM";
    public static final String CODE_ABSENTE = "PUBLICATION_AGPM_ABSENTE";
    static final String FAMILLE_DDP = "DDP";
    private static final int MAX_VERSIONS = 50;

    private final PublicationAgpmRepository publications;
    private final DossierRepository dossiers;
    private final DossierMecRepository dmcs;
    private final MarcheRepository marches;
    private final AttributionRepository attributions;
    private final PieceJointeDossierRepository pieces;
    private final TypePieceJointeRepository typesPiece;
    private final ValeursPpmService valeursPpm;
    private final PiecesExigees piecesExigees;
    private final JournalDossierService journal;
    private final ObjectProvider<DossierService> dossierService;

    public PublicationAgpmService(PublicationAgpmRepository publications, DossierRepository dossiers, DossierMecRepository dmcs,
            MarcheRepository marches, AttributionRepository attributions, PieceJointeDossierRepository pieces,
            TypePieceJointeRepository typesPiece, ValeursPpmService valeursPpm, PiecesExigees piecesExigees, JournalDossierService journal,
            ObjectProvider<DossierService> dossierService) {
        this.publications = publications;
        this.dossiers = dossiers;
        this.dmcs = dmcs;
        this.marches = marches;
        this.attributions = attributions;
        this.pieces = pieces;
        this.typesPiece = typesPiece;
        this.valeursPpm = valeursPpm;
        this.piecesExigees = piecesExigees;
        this.journal = journal;
        this.dossierService = dossierService;
    }

    // ------------------------------------------------------------------ dépôt

    /**
     * Dépose la publication sur le plan {@code idDossier} : PRMP propriétaire ou son UGPM (403 sinon) ; 409 {@code PAS_UN_PLAN} hors
     * dossier de planification, {@code PLAN_NON_CONTROLE} avant le PV signé favorable ; 400 nominatif ({@code fichier} PDF, {@code journal},
     * {@code dateParution}, {@code referenceAgpm}, {@code numero}). Journal du plan : {@code PUBLICATION_AGPM_DEPOSEE}.
     */
    public PublicationAgpmDto deposer(Integer idDossier, MultipartFile fichier, String journalNom, String numero, String dateParution,
            String referenceAgpm) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil != ProfilUtilisateur.PRMP && profil != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("La publication de l'AGPM se dépose par la PRMP du plan ou son UGPM.");
        }
        dossierService.getObject().controlerVisibilite(idDossier);
        Dossier plan = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        if (!FAMILLE_DDP.equals(plan.getIdTypeDossier())) {
            throw new BusinessRuleException("La publication de l'AGPM se dépose sur le plan de passation, pas sur ce dossier.", "PAS_UN_PLAN",
                    idDossier);
        }
        if (!StatutDossier.REMPLACE.name().equals(plan.getStatut()) && !valeursPpm.pvSigneFavorable(plan)) {
            throw new BusinessRuleException("Le plan n'est pas encore contrôlé : la publication de l'AGPM se dépose une fois le PV d'examen "
                    + "signé favorable.", "PLAN_NON_CONTROLE", idDossier);
        }
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        byte[] contenu = null;
        if (fichier == null || fichier.isEmpty()) {
            erreurs.add(new ErrorResponse.FieldError("fichier", "Le scan du journal est obligatoire."));
        } else {
            try {
                contenu = fichier.getBytes();
            } catch (IOException e) {
                throw new IllegalStateException("Lecture du fichier impossible.", e);
            }
            if (!pdf(contenu)) {
                erreurs.add(new ErrorResponse.FieldError("fichier", "Le scan du journal est un PDF."));
            }
        }
        String j = texte(journalNom, "journal", "Le nom du journal", 200, true, erreurs);
        String n = texte(numero, "numero", "Le numéro du journal", 50, false, erreurs);
        String r = texte(referenceAgpm, "referenceAgpm", "La référence de l'AGPM", 100, true, erreurs);
        LocalDate d = null;
        if (dateParution == null || dateParution.isBlank()) {
            erreurs.add(new ErrorResponse.FieldError("dateParution", "La date de parution est obligatoire."));
        } else {
            try {
                d = LocalDate.parse(dateParution.trim());
                if (d.isAfter(LocalDate.now())) {
                    erreurs.add(new ErrorResponse.FieldError("dateParution", "La date de parution n'est pas dans le futur."));
                }
            } catch (DateTimeParseException e) {
                erreurs.add(new ErrorResponse.FieldError("dateParution", "La date de parution est au format AAAA-MM-JJ."));
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        LocalDateTime maintenant = LocalDateTime.now();
        publications.findFirstByIdDossierAndRemplaceeLeIsNull(idDossier).ifPresent(p -> {
            p.setRemplaceeLe(maintenant);
            publications.saveAndFlush(p);
        });
        PublicationAgpm p = new PublicationAgpm();
        p.setIdDossier(idDossier);
        p.setJournal(j);
        p.setNumero(n);
        p.setDateParution(d);
        p.setReferenceAgpm(r);
        String original = fichier.getOriginalFilename();
        p.setNomFichier(original == null || original.isBlank() ? "publication-agpm_" + idDossier + ".pdf" : original.trim());
        p.setContenu(contenu);
        p.setTaille((long) contenu.length);
        p.setDeposePar(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        p.setDeposeLe(maintenant);
        p = publications.save(p);
        journal.tracer(idDossier, "PUBLICATION_AGPM_DEPOSEE", "Publication de l'AGPM déposée : " + j + (n == null ? "" : " n° " + n)
                + " du " + d + ", AGPM " + r);
        return dto(p, false);
    }

    // ------------------------------------------------------------------ lecture

    /** La publication en vigueur pour ce dossier (plan, ou dossier dont la ligne relève d'un plan) ; 404 {@code PUBLICATION_AGPM_ABSENTE}. */
    @Transactional(readOnly = true)
    public PublicationAgpmDto lire(Integer idDossier) {
        dossierService.getObject().controlerVisibilite(idDossier);
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        Integer plan = planDuDossier(d);
        PublicationAgpm p = plan == null ? null : enVigueurDuPlan(plan).orElse(null);
        if (p == null) {
            throw new ResourceNotFoundException("Aucune publication de l'AGPM n'est déposée sur le plan de ce dossier.");
        }
        return dto(p, !p.getIdDossier().equals(plan));
    }

    /** Le scan en vigueur, même garde que {@link #lire(Integer)}. */
    @Transactional(readOnly = true)
    public PublicationAgpm contenu(Integer idDossier) {
        PublicationAgpmDto dto = lire(idDossier);
        return publications.findById(dto.idPublication()).orElseThrow();
    }

    /** H2 — la publication en vigueur du plan : la sienne, à défaut celle de sa version précédente, et ainsi de suite. */
    @Transactional(readOnly = true)
    public Optional<PublicationAgpm> enVigueurDuPlan(Integer idPlan) {
        Integer courant = idPlan;
        for (int i = 0; courant != null && i < MAX_VERSIONS; i++) {
            Optional<PublicationAgpm> p = publications.findFirstByIdDossierAndRemplaceeLeIsNull(courant);
            if (p.isPresent()) {
                return p;
            }
            courant = dossiers.findById(courant).map(Dossier::getIdDossierParent).orElse(null);
        }
        return Optional.empty();
    }

    /** La publication en vigueur pour la procédure d'une fiche marché (plan de la ligne en vigueur de son DMC). */
    @Transactional(readOnly = true)
    public Optional<PublicationAgpm> enVigueurPourDmc(Long idDmc) {
        Integer plan = planDuDmc(idDmc);
        return plan == null ? Optional.empty() : enVigueurDuPlan(plan);
    }

    /**
     * Le plan dont relève un dossier : lui-même (planification) ; le plan de la ligne en vigueur de sa fiche ({@code ID_DMC}) ; pour un
     * dossier de marché, celui de la fiche de son attribution ; {@code null} sinon.
     */
    @Transactional(readOnly = true)
    public Integer planDuDossier(Dossier d) {
        if (FAMILLE_DDP.equals(d.getIdTypeDossier())) {
            return d.getIdDossier();
        }
        Long idDmc = d.getIdDmc() != null ? d.getIdDmc()
                : attributions.findFirstByIdDossier(d.getIdDossier()).map(a -> a.getIdDmc()).orElse(null);
        return idDmc == null ? null : planDuDmc(idDmc);
    }

    private Integer planDuDmc(Long idDmc) {
        return dmcs.findById(idDmc).map(DossierMec::getIdDetail).flatMap(marches::findById).map(valeursPpm::ligneEnVigueur)
                .map(Marche::getIdDossier).orElse(null);
    }

    // ------------------------------------------------------------------ reprise d'office

    /**
     * Joint d'office au dossier la publication en vigueur de son plan, sous la pièce {@code PUBLICATION_AGPM}, quand le sous-type
     * l'exige et que le dossier ne la porte pas encore. Sans effet sinon (pas de plan, pas de publication, pièce déjà jointe).
     *
     * @return vrai si la pièce a été jointe
     */
    public boolean joindreSiAbsente(Dossier d) {
        if (d == null || FAMILLE_DDP.equals(d.getIdTypeDossier())) {
            return false;
        }
        TypePieceJointe type = piecesExigees.obligatoires(d).stream().filter(t -> CODE_PIECE.equals(t.getCode())).findFirst().orElse(null);
        if (type == null || pieces.existsByIdDossierAndIdTypePiece(d.getIdDossier(), type.getIdTypePiece())) {
            return false;
        }
        Integer plan = planDuDossier(d);
        PublicationAgpm p = plan == null ? null : enVigueurDuPlan(plan).orElse(null);
        if (p == null) {
            return false;
        }
        PieceJointeDossier piece = new PieceJointeDossier();
        piece.setIdDossier(d.getIdDossier());
        piece.setIdTypePiece(type.getIdTypePiece());
        piece.setNomFichier("PUBLICATION_AGPM_" + p.getReferenceAgpm().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf");
        piece.setContenu(p.getContenu());
        piece.setFormat("PDF");
        piece.setTaille(p.getTaille());
        piece.setDateUpload(LocalDateTime.now());
        piece.setApresLettreRenvoi(false);
        pieces.save(piece);
        journal.tracer(d.getIdDossier(), "PUBLICATION_AGPM_REPRISE", "Publication de l'AGPM reprise d'office du plan " + p.getIdDossier()
                + " (" + p.getJournal() + " du " + p.getDateParution() + ")");
        return true;
    }

    /** La pièce manquante se dépose sur le plan : le complément du message de la soumission. */
    public static String indication(TypePieceJointe t) {
        return CODE_PIECE.equals(t.getCode()) ? " Elle se dépose une fois sur le plan de passation contrôlé, et chaque dossier du plan la "
                + "reprend d'office." : "";
    }

    // ------------------------------------------------------------------ outils

    private static PublicationAgpmDto dto(PublicationAgpm p, boolean heritee) {
        return new PublicationAgpmDto(p.getIdPublication(), p.getIdDossier(), p.getJournal(), p.getNumero(), p.getDateParution(),
                p.getReferenceAgpm(), p.getNomFichier(), p.getTaille(), p.getDeposePar(), p.getDeposeLe(), p.getRemplaceeLe(),
                p.getRemplaceeLe() == null, heritee);
    }

    private static boolean pdf(byte[] c) {
        return c != null && c.length > 4 && c[0] == 0x25 && c[1] == 0x50 && c[2] == 0x44 && c[3] == 0x46;   // %PDF
    }

    private static String texte(String v, String champ, String nom, int max, boolean obligatoire, List<ErrorResponse.FieldError> erreurs) {
        if (v == null || v.isBlank()) {
            if (obligatoire) {
                erreurs.add(new ErrorResponse.FieldError(champ, nom + " est obligatoire."));
            }
            return null;
        }
        if (v.trim().length() > max) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " : " + max + " caractères au plus."));
            return null;
        }
        return v.trim();
    }
}
