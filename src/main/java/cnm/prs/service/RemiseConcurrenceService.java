package cnm.prs.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cnm.prs.dto.ContratVigueurDto;
import cnm.prs.dto.RemiseDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.ContratVigueur;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Offre;
import cnm.prs.entity.RemiseConcurrence;
import cnm.prs.entity.RemiseConsulte;
import cnm.prs.entity.RemiseReponse;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.CaoMembreRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.ContratVigueurRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.RemiseConcurrenceRepository;
import cnm.prs.repository.RemiseConsulteRepository;
import cnm.prs.repository.RemiseReponseRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-2 ; V125 ; décret n° 2019-1064, art. 20-33) — la remise en concurrence des
 * titulaires d'un contrat-cadre en vigueur et le marché subséquent.
 *
 * <ul>
 *   <li><strong>Lancement</strong> (PRMP ou UGPM, art. 24) : le contrat est en vigueur (409 {@code CONTRAT_ECHU}) ; <strong>tous</strong>
 *       les titulaires non retirés sont consultés (art. 21) ; la lettre d'invitation est produite (annexe 1 du document type ARMP :
 *       caractéristiques du besoin, critères, date et heure limites, art. 26) et notifiée à chacun. Un titulaire unique (mono-attributaire,
 *       art. 18) est invité à <strong>compléter</strong> son offre initiale ({@code COMPLETUDE}), dans le délai {@code B07-MA-01} à défaut
 *       d'échéance fixée.</li>
 *   <li><strong>Réponses</strong> (art. 27, loi art. 33) : devis, facture pro forma ou document convenu, en ligne par le titulaire ou
 *       enregistrées depuis le papier par la PRMP ou l'UGPM, jusqu'à l'échéance ; <strong>fermées</strong> jusqu'à l'échéance (montants et
 *       fichiers invisibles).</li>
 *   <li><strong>Évaluation</strong> (art. 28, 33) : par la CAO du contrat-cadre en collège restreint — ⚠️ hypothèse H1, à confirmer par le
 *       juriste : son <strong>président</strong> arrête l'évaluation et signe le rapport simplifié. Chaque réponse est déclarée conforme ou
 *       non ; le prix moyen du marché (règles du contrat, {@code B07-MA-07}) est saisi ; l'écart de chaque offre est calculé ; la
 *       proposition est l'offre conforme la moins-disante, à défaut celle que la commission désigne. Les absences de réponse sont
 *       constatées (art. 22).</li>
 *   <li><strong>Décision</strong> (art. 29-30) : la PRMP attribue le marché subséquent au seul titulaire proposé (art. 20 : pas de
 *       « saupoudrage »), ou déclare la remise infructueuse ; chaque titulaire consulté est informé.</li>
 * </ul>
 */
@Service
public class RemiseConcurrenceService {

    static final Set<String> NATURES = Set.of("DEVIS", "FACTURE_PRO_FORMA", "DOCUMENT_CONVENU");
    public static final String EXCLUSION_POSSIBLE = "EXCLUSION_POSSIBLE";
    public static final String DELIEMENT_POSSIBLE = "DELIEMENT_POSSIBLE";
    public static final String PLAFOND_DEPASSE = "PLAFOND_DEPASSE";
    /** DPAC-CC, art. 9 : en l'absence de trois réponses consécutives, le titulaire peut être exclu du contrat-cadre. */
    static final int ABSENCES_EXCLUSION = 3;
    /** Décret, art. 23 : des offres supérieures de 30 % au prix moyen du marché. */
    static final BigDecimal SEUIL_ECART = new BigDecimal("30");
    private static final Set<ProfilUtilisateur> EXTERNES = Set.of(ProfilUtilisateur.CANDIDAT, ProfilUtilisateur.DEPOSITAIRE);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    private final RemiseConcurrenceRepository remises;
    private final RemiseConsulteRepository consultes;
    private final RemiseReponseRepository reponses;
    private final ContratVigueurRepository contrats;
    private final ContratVigueurService contratsService;
    private final AttributionRepository attributions;
    private final OffreRepository offres;
    private final CompteCandidatRepository candidats;
    private final CaoMembreRepository caoMembres;
    private final DossierMecRepository dmcRepository;
    private final FicheMarcheService fiches;
    private final ValeursPpmService valeursPpm;
    private final ParametresInternesService internes;
    private final NotificationService notifications;
    private final CeremonieService ceremonies;
    private final EvaluationService evaluation;
    private final GenerateurDocumentsFiche generateur;
    private final ParametreService parametres;
    private final Clock horloge;

    public RemiseConcurrenceService(RemiseConcurrenceRepository remises, RemiseConsulteRepository consultes, RemiseReponseRepository reponses,
            ContratVigueurRepository contrats, ContratVigueurService contratsService, AttributionRepository attributions, OffreRepository offres,
            CompteCandidatRepository candidats, CaoMembreRepository caoMembres, DossierMecRepository dmcRepository, FicheMarcheService fiches,
            ValeursPpmService valeursPpm, ParametresInternesService internes, NotificationService notifications, CeremonieService ceremonies,
            EvaluationService evaluation, GenerateurDocumentsFiche generateur, ParametreService parametres, Clock horloge) {
        this.remises = remises;
        this.consultes = consultes;
        this.reponses = reponses;
        this.contrats = contrats;
        this.contratsService = contratsService;
        this.attributions = attributions;
        this.offres = offres;
        this.candidats = candidats;
        this.caoMembres = caoMembres;
        this.dmcRepository = dmcRepository;
        this.fiches = fiches;
        this.valeursPpm = valeursPpm;
        this.internes = internes;
        this.notifications = notifications;
        this.ceremonies = ceremonies;
        this.evaluation = evaluation;
        this.generateur = generateur;
        this.parametres = parametres;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ lancement

    /**
     * Lance une remise en concurrence : PRMP ou UGPM de la fiche. 400 {@code CHAMP_OBLIGATOIRE} (objet, spécifications, délai
     * d'exécution ; échéance hors complétude), {@code ECHEANCE_INVALIDE} (passée, ou après la fin du contrat) ; 409 {@code CONTRAT_ECHU},
     * {@code FORME_SANS_REMISE} (marché à commandes : MS-3), {@code SANS_TITULAIRE}, {@code REMISE_EN_COURS} (une remise non décidée).
     */
    @Transactional
    public RemiseDto lancer(Long idContrat, RemiseDto.RemiseRequest r) {
        ContratVigueur c = contrats.findById(idContrat).orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + idContrat + "."));
        exigerPrmpOuUgpm(c.getIdDmc());
        contratsService.exigerEnVigueur(c.getIdDmc(), c.getLot());
        if (!ContratVigueur.CONTRAT_CADRE.equals(c.getTypeMarche())) {
            throw new BusinessRuleException("Un marché à commandes s'exécute par bons de commande : il n'a pas de remise en concurrence.",
                    "FORME_SANS_REMISE");
        }
        List<ContratVigueurDto.Titulaire> titulaires = contratsService.titulairesDto(c);
        if (titulaires.isEmpty()) {
            throw new BusinessRuleException("Le contrat n'a plus de titulaire.", "SANS_TITULAIRE");
        }
        if (remises.findByIdContratOrderByNumeroAsc(idContrat).stream().anyMatch(x -> RemiseConcurrence.LANCEE.equals(x.getEtat())
                || RemiseConcurrence.EVALUEE.equals(x.getEtat()))) {
            throw new BusinessRuleException("Une remise en concurrence de ce contrat attend sa décision.", "REMISE_EN_COURS");
        }
        String objet = obligatoire(r == null ? null : r.objet(), "objet");
        String specifications = obligatoire(r.specifications(), "specifications");
        String delai = obligatoire(r.delaiExecution(), "delaiExecution");
        boolean completude = titulaires.size() == 1;
        LocalDateTime maintenant = maintenant();
        LocalDateTime echeance = r.echeance();
        if (echeance == null && completude) {
            Integer jours = joursCompletude(c);
            echeance = jours == null ? null : maintenant.plusDays(jours).withSecond(0);
        }
        if (echeance == null) {
            throw new BadRequestException("La date et l'heure limites de remise des offres sont à fixer.", "CHAMP_OBLIGATOIRE", Map.<String, Object>of("champ", "echeance"));
        }
        if (!echeance.isAfter(maintenant) || echeance.toLocalDate().isAfter(c.getDateFin())) {
            throw new BadRequestException("L'échéance se situe entre maintenant et la fin du contrat (" + c.getDateFin().format(JOUR) + ").",
                    "ECHEANCE_INVALIDE");
        }
        RemiseConcurrence x = new RemiseConcurrence();
        x.setIdContrat(idContrat);
        x.setNumero(remises.findFirstByIdContratOrderByNumeroDesc(idContrat).map(RemiseConcurrence::getNumero).orElse(0) + 1);
        x.setMode(completude ? RemiseConcurrence.COMPLETUDE : RemiseConcurrence.REMISE_EN_CONCURRENCE);
        x.setEtat(RemiseConcurrence.LANCEE);
        x.setObjet(objet);
        x.setSpecifications(specifications);
        x.setCriteres(nettoyer(r.criteres()));
        x.setLieuLivraison(nettoyer(r.lieuLivraison()));
        x.setDelaiExecution(delai);
        x.setValiditeJours(r.validiteJours() == null || r.validiteJours() <= 0 ? null : r.validiteJours());
        x.setVisite(nettoyer(r.visite()));
        x.setEcheance(echeance);
        x.setLanceeLe(maintenant);
        x.setLanceePar(acteur());
        remises.save(x);
        for (GenerateurDocumentsFiche.Fichier f : generateur.generer(lettre(c, x))) {
            if ("pdf".equals(f.extension())) {
                x.setLettrePdf(f.contenu());
            } else if ("docx".equals(f.extension())) {
                x.setLettreDocx(f.contenu());
            }
        }
        remises.save(x);
        String objetContrat = objetContrat(c);
        for (ContratVigueurDto.Titulaire t : titulaires) {
            RemiseConsulte k = new RemiseConsulte();
            k.setIdRemise(x.getId());
            k.setIdOffre(t.idOffre());
            k.setNotifieLe(maintenant);
            consultes.save(k);
            notifierTitulaire(t.idOffre(), c, TypeNotification.REMISE_CONCURRENCE, completude ? "Demande de complétude de votre offre"
                    : "Remise en concurrence", (completude ? "Vous êtes invité à compléter votre offre initiale" : "Vous êtes invité à remettre une offre")
                    + " pour le marché subséquent « " + objet + " » du contrat-cadre « " + objetContrat + " », au plus tard le "
                    + echeance.format(HEURE) + ". La lettre d'invitation est disponible dans votre espace (Mes invitations).");
        }
        evaluation.tracerAttribution(c.getIdDmc(), "REMISE_EN_CONCURRENCE", "Lot " + c.getLot() + " : " + (completude ? "complétude" : "remise en concurrence")
                + " n° " + x.getNumero() + " lancée — « " + objet + " », " + titulaires.size() + " titulaire(s) consulté(s), échéance le "
                + echeance.format(HEURE));
        return dto(x, c);
    }

    // ------------------------------------------------------------------ lecture

    /**
     * Les remises lisibles par le connecté : PRMP et UGPM, celles de leurs procédures ; membre de CAO, celles des procédures de sa CAO ;
     * Commission et Administrateur, toutes.
     */
    @Transactional(readOnly = true)
    public List<RemiseDto> lister() {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p == null || EXTERNES.contains(p)) {
            throw new AccessDeniedException("Les remises en concurrence se lisent dans l'espace de l'autorité contractante, de la CAO et de la Commission.");
        }
        List<RemiseDto> out = new ArrayList<>();
        for (RemiseConcurrence x : remises.findAll()) {
            ContratVigueur c = contrats.findById(x.getIdContrat()).orElse(null);
            if (c != null && lisible(c)) {
                out.add(dto(x, c));
            }
        }
        out.sort(Comparator.comparing(RemiseDto::echeance).reversed());
        return out;
    }

    /** Les remises d'un contrat, dans l'ordre. */
    @Transactional(readOnly = true)
    public List<RemiseDto> listerDuContrat(Long idContrat) {
        ContratVigueur c = contrats.findById(idContrat).orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + idContrat + "."));
        exigerLecteur(c);
        return remises.findByIdContratOrderByNumeroAsc(idContrat).stream().map(x -> dto(x, c)).toList();
    }

    @Transactional(readOnly = true)
    public RemiseDto lire(Long id) {
        RemiseConcurrence x = exiger(id);
        ContratVigueur c = contrats.findById(x.getIdContrat()).orElseThrow();
        exigerLecteur(c);
        return dto(x, c);
    }

    /** La lettre d'invitation (PDF, ou Word) : lecteurs de la remise. */
    @Transactional(readOnly = true)
    public byte[] lettre(Long id, boolean docx) {
        RemiseConcurrence x = exiger(id);
        exigerLecteur(contrats.findById(x.getIdContrat()).orElseThrow());
        return docx ? x.getLettreDocx() : x.getLettrePdf();
    }

    /** Le rapport simplifié (PDF, ou Word) : lecteurs ; 404 avant l'évaluation. */
    @Transactional(readOnly = true)
    public byte[] rapport(Long id, boolean docx) {
        RemiseConcurrence x = exiger(id);
        exigerLecteur(contrats.findById(x.getIdContrat()).orElseThrow());
        byte[] b = docx ? x.getRapportDocx() : x.getRapportPdf();
        if (b == null) {
            throw new ResourceNotFoundException("Le rapport simplifié n'est pas produit.");
        }
        return b;
    }

    /** Le fichier d'une réponse : lecteurs, une fois l'échéance passée (409 {@code REPONSES_FERMEES}). */
    @Transactional(readOnly = true)
    public RemiseReponse fichier(Long id, Long idReponse) {
        RemiseConcurrence x = exiger(id);
        exigerLecteur(contrats.findById(x.getIdContrat()).orElseThrow());
        if (!echeancePassee(x)) {
            throw new BusinessRuleException("Les réponses restent fermées jusqu'à l'échéance (" + x.getEcheance().format(HEURE) + ").", "REPONSES_FERMEES");
        }
        return reponses.findById(idReponse).filter(y -> y.getIdRemise().equals(id))
                .orElseThrow(() -> new ResourceNotFoundException("Réponse introuvable : " + idReponse + "."));
    }

    // ------------------------------------------------------------------ réponses

    /**
     * Le titulaire dépose (ou remplace) sa réponse en ligne : {@code nature} ∈ DEVIS, FACTURE_PRO_FORMA, DOCUMENT_CONVENU (400
     * {@code NATURE_INVALIDE}), {@code montantHt} positif (400 {@code MONTANT_INVALIDE}), le fichier (400 {@code FICHIER_OBLIGATOIRE},
     * {@code FORMAT_INVALIDE}) ; 404 si son offre n'est pas consultée ; 409 {@code ECHEANCE_PASSEE}, {@code REMISE_CLOSE}.
     */
    @Transactional
    public RemiseDto.PourTitulaire deposer(String idCandidat, Long id, String nature, BigDecimal montantHt, BigDecimal montantTtc, String delai,
            MultipartFile fichier) {
        RemiseConcurrence x = exiger(id);
        String idOffre = offreConsulteeDe(idCandidat, x).orElseThrow(() -> new ResourceNotFoundException("Remise introuvable : " + id + "."));
        exigerOuverte(x, maintenant());
        enregistrer(x, idOffre, nature, montantHt, montantTtc, delai, fichier, RemiseReponse.EN_LIGNE, maintenant());
        ContratVigueur c = contrats.findById(x.getIdContrat()).orElseThrow();
        Offre o = offres.findById(idOffre).orElse(null);
        ceremonies.notifierPrmp(c.getIdDmc(), TypeNotification.REMISE_REPONSE_RECUE, "Réponse à une remise en concurrence",
                "Le titulaire " + (o == null ? idOffre : o.getRaisonSociale()) + " a déposé sa réponse à la remise n° " + x.getNumero()
                        + " (lot " + c.getLot() + ", procédure " + c.getIdDmc() + ") ; elle s'ouvrira à l'échéance du " + x.getEcheance().format(HEURE) + ".");
        return pourTitulaire(x, c, idOffre);
    }

    /**
     * La PRMP ou l'UGPM enregistre une réponse reçue sur papier (loi, art. 33) : {@code idOffre} d'un titulaire consulté (404 sinon),
     * {@code dateReception} au plus tard à l'échéance (400 {@code DATE_INVALIDE}, défaut : maintenant), mêmes contrôles que le dépôt en
     * ligne ; 409 {@code REMISE_CLOSE}.
     */
    @Transactional
    public RemiseDto enregistrerPapier(Long id, String idOffre, String nature, BigDecimal montantHt, BigDecimal montantTtc, String delai,
            LocalDateTime dateReception, MultipartFile fichier) {
        RemiseConcurrence x = exiger(id);
        ContratVigueur c = contrats.findById(x.getIdContrat()).orElseThrow();
        exigerPrmpOuUgpm(c.getIdDmc());
        if (!RemiseConcurrence.LANCEE.equals(x.getEtat())) {
            throw new BusinessRuleException("La remise est évaluée : plus aucune réponse ne s'enregistre.", "REMISE_CLOSE");
        }
        if (consultes.findById(cle(id, idOffre)).isEmpty()) {
            throw new ResourceNotFoundException("Cette offre n'est pas consultée par la remise.");
        }
        LocalDateTime recue = dateReception == null ? maintenant() : dateReception;
        if (recue.isAfter(x.getEcheance()) || recue.isAfter(maintenant()) || recue.isBefore(x.getLanceeLe())) {
            throw new BadRequestException("La réponse papier est reçue entre le lancement et l'échéance (" + x.getEcheance().format(HEURE) + ").",
                    "DATE_INVALIDE");
        }
        enregistrer(x, idOffre, nature, montantHt, montantTtc, delai, fichier, RemiseReponse.PAPIER, recue);
        return dto(x, c);
    }

    private void enregistrer(RemiseConcurrence x, String idOffre, String nature, BigDecimal montantHt, BigDecimal montantTtc, String delai,
            MultipartFile fichier, String voie, LocalDateTime recue) {
        String n = nature == null ? null : nature.trim().toUpperCase(Locale.ROOT);
        if (n == null || !NATURES.contains(n)) {
            throw new BadRequestException("La réponse est un devis, une facture pro forma ou le document convenu au contrat-cadre (DEVIS, "
                    + "FACTURE_PRO_FORMA, DOCUMENT_CONVENU).", "NATURE_INVALIDE");
        }
        if (montantHt == null || montantHt.signum() <= 0 || montantTtc != null && montantTtc.signum() <= 0) {
            throw new BadRequestException("Le montant hors taxes de l'offre est un nombre positif.", "MONTANT_INVALIDE");
        }
        byte[] contenu = contenu(fichier);
        String format = EntrepriseCandidatService.format(contenu);
        if (format == null) {
            throw new BadRequestException("Le fichier doit être un PDF, un JPEG ou un PNG (type lu sur le contenu).", "FORMAT_INVALIDE");
        }
        RemiseReponse y = reponses.findByIdRemiseAndIdOffre(x.getId(), idOffre).orElseGet(RemiseReponse::new);
        y.setIdRemise(x.getId());
        y.setIdOffre(idOffre);
        y.setNature(n);
        y.setVoie(voie);
        y.setMontantHt(montantHt);
        y.setMontantTtc(montantTtc);
        y.setDelai(nettoyer(delai));
        String nom = fichier.getOriginalFilename() == null || fichier.getOriginalFilename().isBlank() ? "reponse" : fichier.getOriginalFilename()
                .replaceAll("[\\\\/]", "_");
        y.setNom(nom.length() > 255 ? nom.substring(nom.length() - 255) : nom);
        y.setFormat(format);
        y.setTaille((long) contenu.length);
        y.setContenu(contenu);
        y.setRecueLe(recue);
        y.setDeposeePar(acteur());
        reponses.save(y);
        Offre o = offres.findById(idOffre).orElse(null);
        ContratVigueur c = contrats.findById(x.getIdContrat()).orElseThrow();
        evaluation.tracerAttribution(c.getIdDmc(), "REMISE_REPONSE", "Lot " + c.getLot() + ", remise n° " + x.getNumero() + " : réponse "
                + (RemiseReponse.PAPIER.equals(voie) ? "papier enregistrée" : "déposée en ligne") + " par "
                + (o == null ? idOffre : "le titulaire offre n° " + o.getNumero() + " (" + o.getRaisonSociale() + ")") + ", reçue le " + recue.format(HEURE));
    }

    // ------------------------------------------------------------------ évaluation

    /**
     * L'évaluation de la CAO en collège restreint (art. 28, 33) — ⚠️ H1 : par son président, une fois l'échéance passée (409
     * {@code ECHEANCE_NON_PASSEE}, {@code DEJA_EVALUEE}). {@code prixMoyen} positif (400 {@code PRIX_MOYEN_INVALIDE}, facultatif) ; une
     * décision par réponse (400 {@code DECISIONS_INCOMPLETES}, {@code MOTIF_OBLIGATOIRE} pour une non-conformité) ; {@code idOffreProposee}
     * facultative, conforme (409 {@code OFFRE_NON_CONFORME}) : à défaut, la moins-disante conforme ; aucune conforme : l'infructuosité est
     * proposée. Le rapport simplifié est produit.
     */
    @Transactional
    public RemiseDto evaluer(Long id, RemiseDto.EvaluationRequest r) {
        RemiseConcurrence x = exiger(id);
        ContratVigueur c = contrats.findById(x.getIdContrat()).orElseThrow();
        String president = exigerPresidentCao(c.getIdDmc());
        if (!RemiseConcurrence.LANCEE.equals(x.getEtat())) {
            throw new BusinessRuleException("La remise est déjà évaluée.", "DEJA_EVALUEE");
        }
        if (!echeancePassee(x)) {
            throw new BusinessRuleException("L'évaluation suit l'échéance du " + x.getEcheance().format(HEURE) + ".", "ECHEANCE_NON_PASSEE");
        }
        BigDecimal prixMoyen = r == null ? null : r.prixMoyen();
        if (prixMoyen != null && prixMoyen.signum() <= 0) {
            throw new BadRequestException("Le prix moyen du marché est un montant positif.", "PRIX_MOYEN_INVALIDE");
        }
        List<RemiseReponse> rs = reponses.findByIdRemiseOrderByIdAsc(id);
        Map<String, RemiseDto.DecisionReponse> decisions = new HashMap<>();
        for (RemiseDto.DecisionReponse d : r == null || r.decisions() == null ? List.<RemiseDto.DecisionReponse>of() : r.decisions()) {
            if (d != null && d.idOffre() != null) {
                decisions.put(d.idOffre(), d);
            }
        }
        List<String> manquantes = rs.stream().filter(y -> decisions.get(y.getIdOffre()) == null || decisions.get(y.getIdOffre()).conforme() == null)
                .map(RemiseReponse::getIdOffre).toList();
        if (!manquantes.isEmpty()) {
            throw new BadRequestException("Chaque réponse se déclare conforme ou non.", "DECISIONS_INCOMPLETES", Map.<String, Object>of("offres", manquantes));
        }
        for (RemiseReponse y : rs) {
            RemiseDto.DecisionReponse d = decisions.get(y.getIdOffre());
            if (!d.conforme() && nettoyer(d.motif()) == null) {
                throw new BadRequestException("Une réponse non conforme se motive.", "MOTIF_OBLIGATOIRE", Map.<String, Object>of("offres", List.of(y.getIdOffre())));
            }
        }
        String designee = r == null ? null : nettoyer(r.idOffreProposee());
        List<RemiseReponse> conformes = rs.stream().filter(y -> decisions.get(y.getIdOffre()).conforme())
                .sorted(Comparator.comparing(RemiseReponse::getMontantHt)).toList();
        if (designee != null && conformes.stream().noneMatch(y -> y.getIdOffre().equals(designee))) {
            throw new BusinessRuleException("La commission propose une réponse conforme.", "OFFRE_NON_CONFORME");
        }
        String proposee = designee != null ? designee : conformes.isEmpty() ? null : conformes.get(0).getIdOffre();
        for (RemiseReponse y : rs) {
            RemiseDto.DecisionReponse d = decisions.get(y.getIdOffre());
            y.setConforme(d.conforme());
            y.setMotif(d.conforme() ? null : nettoyer(d.motif()));
            reponses.save(y);
        }
        Set<String> repondu = rs.stream().map(RemiseReponse::getIdOffre).collect(Collectors.toSet());
        for (RemiseConsulte k : consultes.findByIdRemiseOrderByIdOffreAsc(id)) {
            k.setSansReponse(!repondu.contains(k.getIdOffre()));
            consultes.save(k);
        }
        x.setPrixMoyen(prixMoyen);
        x.setIdOffreProposee(proposee);
        x.setObservations(r == null ? null : nettoyer(r.observations()));
        x.setEvalueeLe(maintenant());
        x.setEvalueePar(president);
        x.setEtat(RemiseConcurrence.EVALUEE);
        remises.save(x);
        RemiseDto vue = dto(x, c);
        for (GenerateurDocumentsFiche.Fichier f : generateur.generer(rapportDocument(c, x, vue))) {
            if ("pdf".equals(f.extension())) {
                x.setRapportPdf(f.contenu());
            } else if ("docx".equals(f.extension())) {
                x.setRapportDocx(f.contenu());
            }
        }
        remises.save(x);
        Offre o = proposee == null ? null : offres.findById(proposee).orElse(null);
        evaluation.tracerAttribution(c.getIdDmc(), "REMISE_EVALUEE", "Lot " + c.getLot() + ", remise n° " + x.getNumero() + " : " + rs.size()
                + " réponse(s), " + conformes.size() + " conforme(s) ; " + (o == null ? "infructuosité proposée" : "proposition : offre n° " + o.getNumero()
                        + " (" + o.getRaisonSociale() + ")") + (prixMoyen == null ? "" : ", prix moyen du marché " + FormulairesEnLigne.lisible(prixMoyen) + " Ariary HT"));
        ceremonies.notifierPrmp(c.getIdDmc(), TypeNotification.REMISE_RESULTAT, "Remise en concurrence évaluée", "La commission a évalué la remise n° "
                + x.getNumero() + " (lot " + c.getLot() + ", procédure " + c.getIdDmc() + ") : " + (o == null ? "elle propose de la déclarer infructueuse."
                        : "elle propose " + o.getRaisonSociale() + ". Le rapport simplifié est disponible."));
        return dto(x, c);
    }

    // ------------------------------------------------------------------ décision de la PRMP

    /**
     * La PRMP attribue le marché subséquent au titulaire proposé, et à lui seul (art. 29, art. 20) : 409 {@code NON_EVALUEE},
     * {@code AUCUNE_PROPOSITION} (la remise se déclare alors infructueuse). Chaque titulaire consulté est informé (art. 30).
     */
    @Transactional
    public RemiseDto attribuer(Long id, RemiseDto.DecisionRequest r) {
        RemiseConcurrence x = exiger(id);
        ContratVigueur c = contrats.findById(x.getIdContrat()).orElseThrow();
        exigerPrmp(c.getIdDmc());
        if (!RemiseConcurrence.EVALUEE.equals(x.getEtat())) {
            throw new BusinessRuleException("La remise n'est pas évaluée par la commission.", "NON_EVALUEE");
        }
        if (x.getIdOffreProposee() == null) {
            throw new BusinessRuleException("La commission ne propose aucun titulaire : la remise se déclare infructueuse.", "AUCUNE_PROPOSITION");
        }
        RemiseReponse retenue = reponses.findByIdRemiseAndIdOffre(id, x.getIdOffreProposee()).orElseThrow();
        x.setIdOffreRetenue(retenue.getIdOffre());
        x.setMontantRetenu(retenue.getMontantHt());
        x.setMotifDecision(r == null ? null : nettoyer(r.motif()));
        x.setDecideeLe(maintenant());
        x.setDecideePar(acteur());
        x.setEtat(RemiseConcurrence.ATTRIBUEE);
        remises.save(x);
        Offre o = offres.findById(retenue.getIdOffre()).orElse(null);
        informer(c, x);
        evaluation.tracerAttribution(c.getIdDmc(), "MARCHE_SUBSEQUENT", "Lot " + c.getLot() + ", remise n° " + x.getNumero()
                + " : marché subséquent attribué à " + (o == null ? retenue.getIdOffre() : "offre n° " + o.getNumero() + " (" + o.getRaisonSociale() + ")")
                + ", " + FormulairesEnLigne.lisible(retenue.getMontantHt()) + " Ariary HT" + (x.getMotifDecision() == null ? "" : " — " + x.getMotifDecision()));
        return dto(x, c);
    }

    /** La PRMP déclare la remise infructueuse : motif obligatoire (400 {@code MOTIF_OBLIGATOIRE}) ; 409 {@code NON_EVALUEE}. */
    @Transactional
    public RemiseDto declarerInfructueuse(Long id, RemiseDto.DecisionRequest r) {
        RemiseConcurrence x = exiger(id);
        ContratVigueur c = contrats.findById(x.getIdContrat()).orElseThrow();
        exigerPrmp(c.getIdDmc());
        if (!RemiseConcurrence.EVALUEE.equals(x.getEtat())) {
            throw new BusinessRuleException("La remise n'est pas évaluée par la commission.", "NON_EVALUEE");
        }
        String motif = r == null ? null : nettoyer(r.motif());
        if (motif == null) {
            throw new BadRequestException("L'infructuosité de la remise se motive.", "MOTIF_OBLIGATOIRE");
        }
        x.setMotifDecision(motif);
        x.setDecideeLe(maintenant());
        x.setDecideePar(acteur());
        x.setEtat(RemiseConcurrence.INFRUCTUEUSE);
        remises.save(x);
        informer(c, x);
        evaluation.tracerAttribution(c.getIdDmc(), "REMISE_INFRUCTUEUSE", "Lot " + c.getLot() + ", remise n° " + x.getNumero()
                + " : déclarée infructueuse — " + motif);
        return dto(x, c);
    }

    /** Art. 30 : le résultat à chaque titulaire consulté — retenu ; non retenu et son motif ; ou la remise infructueuse. */
    private void informer(ContratVigueur c, RemiseConcurrence x) {
        Map<String, RemiseReponse> parOffre = reponses.findByIdRemiseOrderByIdAsc(x.getId()).stream()
                .collect(Collectors.toMap(RemiseReponse::getIdOffre, y -> y));
        for (RemiseConsulte k : consultes.findByIdRemiseOrderByIdOffreAsc(x.getId())) {
            String resultat = resultat(x, k.getIdOffre());
            String motif = motif(x, k, parOffre.get(k.getIdOffre()));
            notifierTitulaire(k.getIdOffre(), c, TypeNotification.REMISE_RESULTAT, "Résultat de la remise en concurrence n° " + x.getNumero(),
                    switch (resultat) {
                        case "RETENU" -> "Votre offre est retenue : le marché subséquent « " + x.getObjet() + " » vous est attribué, pour "
                                + FormulairesEnLigne.lisible(x.getMontantRetenu()) + " Ariary hors taxes.";
                        case "INFRUCTUEUSE" -> "La remise en concurrence pour « " + x.getObjet() + " » est déclarée infructueuse : " + x.getMotifDecision() + ".";
                        default -> "Votre offre pour le marché subséquent « " + x.getObjet() + " » n'est pas retenue : " + motif;
                    });
        }
    }

    // ------------------------------------------------------------------ espace du titulaire

    /** Les remises où l'une de ses offres est consultée, les plus récentes d'abord. */
    @Transactional(readOnly = true)
    public List<RemiseDto.PourTitulaire> mesRemises(String idCandidat) {
        List<RemiseDto.PourTitulaire> out = new ArrayList<>();
        for (Offre o : offres.findAll().stream().filter(o -> idCandidat.equals(o.getIdCandidat())).toList()) {
            for (RemiseConsulte k : consultes.findByIdOffreOrderByIdRemiseDesc(o.getIdOffre())) {
                RemiseConcurrence x = remises.findById(k.getIdRemise()).orElse(null);
                ContratVigueur c = x == null ? null : contrats.findById(x.getIdContrat()).orElse(null);
                if (c != null) {
                    out.add(pourTitulaire(x, c, o.getIdOffre()));
                }
            }
        }
        out.sort(Comparator.comparing(RemiseDto.PourTitulaire::echeance).reversed());
        return out;
    }

    /** Sa lettre d'invitation (PDF, ou Word) ; 404 hors des remises qui le consultent. */
    @Transactional(readOnly = true)
    public byte[] lettreDuTitulaire(String idCandidat, Long id, boolean docx) {
        RemiseConcurrence x = exiger(id);
        offreConsulteeDe(idCandidat, x).orElseThrow(() -> new ResourceNotFoundException("Remise introuvable : " + id + "."));
        return docx ? x.getLettreDocx() : x.getLettrePdf();
    }

    private RemiseDto.PourTitulaire pourTitulaire(RemiseConcurrence x, ContratVigueur c, String idOffre) {
        RemiseReponse y = reponses.findByIdRemiseAndIdOffre(x.getId(), idOffre).orElse(null);
        RemiseConsulte k = consultes.findById(cle(x.getId(), idOffre)).orElse(null);
        Attribution a = attributions.findById(new Attribution.Cle(c.getIdDmc(), c.getLot())).orElse(null);
        return new RemiseDto.PourTitulaire(x.getId(), x.getNumero(), idOffre, x.getObjet(), objetContrat(c), x.getEcheance(), echeancePassee(x),
                x.getDelaiExecution(), x.getLieuLivraison(), y == null ? null : reponseDto(y, x, null, true, false), resultat(x, idOffre),
                k == null ? null : motif(x, k, y), a == null || a.getDateSignature() == null ? c.getDateEffet() : a.getDateSignature());
    }

    // ------------------------------------------------------------------ vue

    private RemiseDto dto(RemiseConcurrence x, ContratVigueur c) {
        boolean ouvertes = echeancePassee(x);
        List<RemiseReponse> rs = reponses.findByIdRemiseOrderByIdAsc(x.getId());
        Set<String> repondu = rs.stream().map(RemiseReponse::getIdOffre).collect(Collectors.toSet());
        List<RemiseConcurrence> duContrat = remises.findByIdContratOrderByNumeroAsc(x.getIdContrat());
        List<RemiseDto.Consulte> cs = consultes.findByIdRemiseOrderByIdOffreAsc(x.getId()).stream().map(k -> {
            Offre o = offres.findById(k.getIdOffre()).orElse(null);
            return new RemiseDto.Consulte(k.getIdOffre(), o == null ? null : o.getNumero(), o == null ? null : o.getRaisonSociale(), k.getNotifieLe(),
                    repondu.contains(k.getIdOffre()), k.isSansReponse(), absencesConsecutives(duContrat, x, k.getIdOffre()));
        }).toList();
        Map<String, Integer> rangs = new HashMap<>();
        List<RemiseReponse> conformes = rs.stream().filter(y -> Boolean.TRUE.equals(y.getConforme()))
                .sorted(Comparator.comparing(RemiseReponse::getMontantHt)).toList();
        for (int i = 0; i < conformes.size(); i++) {
            rangs.put(conformes.get(i).getIdOffre(), i + 1);
        }
        BigDecimal plafond = RemiseConcurrence.COMPLETUDE.equals(x.getMode()) ? plafondCompletude(c) : null;
        List<RemiseDto.Reponse> vues = rs.stream().map(y -> reponseDto(y, x, rangs.get(y.getIdOffre()), ouvertes, plafond != null
                && y.getMontantHt().compareTo(plafond) > 0)).toList();
        List<String> alertes = new ArrayList<>();
        if (cs.stream().anyMatch(k -> k.absencesConsecutives() >= ABSENCES_EXCLUSION)) {
            alertes.add(EXCLUSION_POSSIBLE);
        }
        if (auDela30(x) && duContrat.stream().filter(p -> p.getNumero() == x.getNumero() - 1).anyMatch(this::auDela30)) {
            alertes.add(DELIEMENT_POSSIBLE);
        }
        if (ouvertes && vues.stream().anyMatch(RemiseDto.Reponse::plafondDepasse)) {
            alertes.add(PLAFOND_DEPASSE);
        }
        return new RemiseDto(x.getId(), x.getIdContrat(), c.getIdDmc(), c.getLot(), x.getNumero(), x.getMode(), x.getEtat(), x.getObjet(),
                x.getSpecifications(), x.getCriteres(), x.getLieuLivraison(), x.getDelaiExecution(), x.getValiditeJours(), x.getVisite(),
                x.getEcheance(), ouvertes, x.getLanceeLe(), x.getLanceePar(), x.getLettrePdf() != null, cs, vues, x.getPrixMoyen(),
                x.getIdOffreProposee(), x.getObservations(), x.getEvalueeLe(), x.getEvalueePar(), x.getRapportPdf() != null, x.getIdOffreRetenue(),
                x.getMontantRetenu(), x.getMotifDecision(), x.getDecideeLe(), x.getDecideePar(), alertes);
    }

    private RemiseDto.Reponse reponseDto(RemiseReponse y, RemiseConcurrence x, Integer rang, boolean ouverte, boolean plafondDepasse) {
        Offre o = offres.findById(y.getIdOffre()).orElse(null);
        return new RemiseDto.Reponse(y.getId(), y.getIdOffre(), o == null ? null : o.getNumero(), o == null ? null : o.getRaisonSociale(),
                y.getNature(), y.getVoie(), ouverte ? y.getMontantHt() : null, ouverte ? y.getMontantTtc() : null, ouverte ? y.getDelai() : null,
                y.getRecueLe(), ouverte ? y.getNom() : null, y.getConforme(), y.getMotif(), ouverte ? ecart(y.getMontantHt(), x.getPrixMoyen()) : null, rang,
                ouverte && plafondDepasse);
    }

    /** L'écart au prix moyen, en pour cent au dixième ; nul sans prix moyen. */
    static BigDecimal ecart(BigDecimal montant, BigDecimal prixMoyen) {
        if (montant == null || prixMoyen == null || prixMoyen.signum() <= 0) {
            return null;
        }
        return montant.subtract(prixMoyen).multiply(BigDecimal.valueOf(100)).divide(prixMoyen, 1, RoundingMode.HALF_UP);
    }

    /** Une remise évaluée dont la meilleure offre conforme (ou, sans conforme, la meilleure reçue) dépasse de plus de 30 % le prix moyen. */
    private boolean auDela30(RemiseConcurrence x) {
        if (x.getEvalueeLe() == null || x.getPrixMoyen() == null) {
            return false;
        }
        List<RemiseReponse> rs = reponses.findByIdRemiseOrderByIdAsc(x.getId());
        List<RemiseReponse> base = rs.stream().anyMatch(y -> Boolean.TRUE.equals(y.getConforme()))
                ? rs.stream().filter(y -> Boolean.TRUE.equals(y.getConforme())).toList() : rs;
        return base.stream().map(RemiseReponse::getMontantHt).min(Comparator.naturalOrder())
                .map(m -> ecart(m, x.getPrixMoyen()).compareTo(SEUIL_ECART) > 0).orElse(false);
    }

    /** Les remises évaluées consécutives, jusqu'à celle-ci, où le titulaire n'a pas répondu. */
    static int absencesConsecutives(List<RemiseConcurrence> duContrat, RemiseConcurrence jusqua, RemiseConsulteRepository consultes, String idOffre) {
        int n = 0;
        for (int i = duContrat.size() - 1; i >= 0; i--) {
            RemiseConcurrence p = duContrat.get(i);
            if (p.getNumero() > jusqua.getNumero() || p.getEvalueeLe() == null) {
                continue;
            }
            RemiseConsulte k = consultes.findById(cle(p.getId(), idOffre)).orElse(null);
            if (k == null || !k.isSansReponse()) {
                break;
            }
            n++;
        }
        return n;
    }

    private int absencesConsecutives(List<RemiseConcurrence> duContrat, RemiseConcurrence jusqua, String idOffre) {
        return absencesConsecutives(duContrat, jusqua, consultes, idOffre);
    }

    /** Le montant au-delà duquel une complétude dépasse le plafond d'augmentation du contrat ({@code B05-PM-04}) ; nul sans plafond. */
    private BigDecimal plafondCompletude(ContratVigueur c) {
        BigDecimal pourcent = ControlesFicheMarche.nombre(ContratVigueurService.valeur(fiches.etatValide(c.getIdDmc()).orElse(null), "B05-PM-04",
                c.getLot()));
        BigDecimal initiale = contratsService.titulairesDto(c).stream().map(ContratVigueurDto.Titulaire::montant).filter(Objects::nonNull)
                .findFirst().orElse(null);
        return pourcent == null || initiale == null ? null
                : initiale.multiply(BigDecimal.ONE.add(pourcent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))).setScale(2, RoundingMode.HALF_UP);
    }

    private Integer joursCompletude(ContratVigueur c) {
        BigDecimal n = ControlesFicheMarche.nombre(ContratVigueurService.valeur(fiches.etatValide(c.getIdDmc()).orElse(null), "B07-MA-01", c.getLot()));
        return n == null || n.signum() <= 0 ? null : n.intValue();
    }

    /** Le résultat d'une offre consultée : {@code EN_COURS}, {@code RETENU}, {@code NON_RETENU} ou {@code INFRUCTUEUSE}. */
    static String resultat(RemiseConcurrence x, String idOffre) {
        return RemiseConcurrence.ATTRIBUEE.equals(x.getEtat()) ? (idOffre.equals(x.getIdOffreRetenue()) ? "RETENU" : "NON_RETENU")
                : RemiseConcurrence.INFRUCTUEUSE.equals(x.getEtat()) ? "INFRUCTUEUSE" : "EN_COURS";
    }

    /** Le motif servi à un titulaire non retenu ; nul tant que la remise n'est pas décidée, et pour le retenu. */
    private String motif(RemiseConcurrence x, RemiseConsulte k, RemiseReponse y) {
        String res = resultat(x, k.getIdOffre());
        if (!"NON_RETENU".equals(res)) {
            return "INFRUCTUEUSE".equals(res) ? x.getMotifDecision() : null;
        }
        if (y == null) {
            return "aucune réponse reçue à l'échéance.";
        }
        if (Boolean.FALSE.equals(y.getConforme())) {
            return "réponse non conforme — " + y.getMotif() + ".";
        }
        return "une offre conforme moins-disante est retenue.";
    }

    // ------------------------------------------------------------------ documents

    /** La lettre d'invitation pour le marché subséquent : l'annexe 1 du document type ARMP du contrat-cadre, et l'annexe 2 (spécifications). */
    private DocumentLibre lettre(ContratVigueur c, RemiseConcurrence x) {
        Map<String, String> plan = plan(c);
        Attribution a = attributions.findById(new Attribution.Cle(c.getIdDmc(), c.getLot())).orElse(null);
        String numeroContrat = a != null && a.getReferenceEnregistrement() != null ? a.getReferenceEnregistrement()
                : "procédure " + c.getIdDmc() + ", lot " + c.getLot();
        LocalDate dateContrat = a != null && a.getDateSignature() != null ? a.getDateSignature() : c.getDateEffet();
        String autorite = Objects.toString(plan.get("ENTITE"), "L'Autorité contractante");
        boolean completude = RemiseConcurrence.COMPLETUDE.equals(x.getMode());
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, plan.get("MINISTERE") == null ? autorite : plan.get("MINISTERE") + " — " + autorite));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "La Personne responsable des marchés publics"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "MARCHÉ SUBSÉQUENT AU CONTRAT-CADRE N° " + numeroContrat));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, completude ? "DEMANDE DE COMPLÉTUDE DE L'OFFRE" : "LETTRE D'INVITATION"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "« " + x.getObjet() + " »"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, autorite));
        para(el, "Remise en concurrence n° " + x.getNumero() + " du " + x.getLanceeLe().format(JOUR) + ".");
        if (completude) {
            para(el, "En application des dispositions du contrat-cadre n° " + numeroContrat + " en date du " + dateContrat.format(JOUR) + ", dont vous êtes "
                    + "le titulaire, nous vous invitons à compléter par écrit votre offre initiale, au plus tard le " + x.getEcheance().format(HEURE)
                    + ", en vue de la passation d'un marché subséquent (article 4 du contrat-cadre).");
        } else {
            para(el, "En application des dispositions du contrat-cadre n° " + numeroContrat + " en date du " + dateContrat.format(JOUR) + ", dont vous êtes "
                    + "titulaire, nous vous invitons à nous faire parvenir au plus tard le " + x.getEcheance().format(HEURE) + " vos offres, aussi bien "
                    + "technique que financière, en vue de la passation d'un marché subséquent.");
        }
        para(el, "A cet effet, vous trouverez en annexe la consistance exacte et précise des prestations demandées.");
        para(el, "Votre réponse prend la forme d'un devis, d'une facture pro forma ou du document convenu au contrat-cadre (décret n° 2019-1064, "
                + "art. 27). Ainsi, veuillez nous transmettre vos offres à l'adresse ci-après, ou les déposer en ligne depuis votre espace sur la "
                + "plateforme (Mes invitations) :");
        para(el, "A l'attention de : " + Objects.toString(plan.get("PRMP"), FormulairesCandidat.POINTILLES));
        para(el, "Adresse : " + Objects.toString(plan.get("ADRESSE"), FormulairesCandidat.POINTILLES));
        if (x.getVisite() != null) {
            para(el, "Une visite des lieux est organisée : " + x.getVisite() + " (art. 27).");
        }
        para(el, "La Personne Responsable des Marchés Publics,");
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "ANNEXE 1"));
        sous(el, "ARTICLE 1. OBJET DU MARCHÉ");
        para(el, "Le présent marché subséquent a pour objet « " + x.getObjet() + " ».");
        sous(el, "ARTICLE 2. RÉFÉRENCES AU CONTRAT-CADRE");
        para(el, "Le présent marché subséquent s'inscrit dans le cadre du contrat-cadre portant « " + objetContrat(c) + " ».");
        para(el, "Les dispositions générales relatives à l'exécution du présent marché subséquent sont celles contenues au CCAP du contrat-cadre.");
        para(el, "Les dispositions de la présente lettre de consultation valant cahier des charges précisent celles du CCAP du contrat-cadre.");
        sous(el, "ARTICLE 3. DÉLAI D'EXÉCUTION");
        para(el, "Le délai d'exécution est fixé à " + x.getDelaiExecution() + " à compter du lendemain de la date de notification de l'ordre de service "
                + "de commencer les prestations ou du bon de commande." + (x.getLieuLivraison() == null ? "" : " Lieu de livraison : " + x.getLieuLivraison() + "."));
        sous(el, "ARTICLE 4. RÉCEPTION");
        para(el, "Les conditions de la réception sont celles prévues au CCAP du contrat-cadre.");
        sous(el, "ARTICLE 5. CONDITIONS DE RÈGLEMENT");
        para(el, "Les dispositions générales relatives aux modalités de facturation et de paiement du présent marché subséquent sont celles indiquées "
                + "au CCAP et sont détaillées dans l'acte d'engagement.");
        sous(el, "ARTICLE 6. PIÈCES CONSTITUTIVES DU MARCHÉ SUBSÉQUENT");
        para(el, "Les pièces constitutives du présent marché subséquent sont les suivantes : la présente lettre d'invitation, valant cahier des charges "
                + "pour le présent marché subséquent ; l'acte d'engagement relatif au contrat-cadre ; l'annexe II du présent document présentant les "
                + "offres proposées, dont notamment le bordereau de prix unitaires (BPU) et le détail quantitatif et estimatif (DQE) ; le cahier des "
                + "clauses administratives générales applicables.");
        sous(el, "ARTICLE 7. PRIX DU MARCHÉ");
        para(el, "Les prix indiqués dans le marché incluent toutes les sujétions indiquées au CCAG.");
        sous(el, "ARTICLE 8. DÉLAI DE VALIDITÉ DES OFFRES");
        para(el, "Le délai de validité des offres sera de " + (x.getValiditeJours() == null ? FormulairesCandidat.POINTILLES : x.getValiditeJours())
                + " jours à compter de la date de remise des offres.");
        sous(el, "ARTICLE 9. ATTRIBUTION DU MARCHÉ");
        para(el, "1. Critère d'attribution");
        para(el, x.getCriteres() == null ? "Le présent marché sera attribué par rapport aux critères fixés aux données particulières de l'appel d'offres du "
                + "contrat-cadre." : "Le présent marché sera attribué selon les critères suivants, qui précisent ceux des données particulières de l'appel "
                        + "d'offres du contrat-cadre : " + x.getCriteres());
        para(el, "2. Évaluation des offres");
        para(el, "Les offres seront classées par ordre décroissant en fonction des critères fixés aux données particulières de l'appel d'offres du contrat-cadre.");
        para(el, "La Personne Responsable des Marchés Publics,");
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, "ANNEXE 2 — Clauses techniques particulières"));
        para(el, "Spécifications techniques détaillées de la (des) prestation(s) demandée(s) :");
        para(el, x.getSpecifications());
        return new DocumentLibre("LETTRE_INVITATION", null, el, "Procédure " + c.getIdDmc() + ", lot " + c.getLot() + " — remise en concurrence n° "
                + x.getNumero());
    }

    /** Le rapport d'évaluation simplifié de la CAO (art. 33). */
    private DocumentLibre rapportDocument(ContratVigueur c, RemiseConcurrence x, RemiseDto vue) {
        List<DocumentLibre.Element> el = new ArrayList<>();
        Map<String, String> plan = plan(c);
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, Objects.toString(plan.get("ENTITE"), "L'Autorité contractante")));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "RAPPORT D'ÉVALUATION SIMPLIFIÉ"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, "Marché subséquent « " + x.getObjet() + " » — remise en concurrence n° " + x.getNumero()));
        para(el, "Contrat-cadre : « " + objetContrat(c) + " », lot " + c.getLot() + ". " + (RemiseConcurrence.COMPLETUDE.equals(x.getMode())
                ? "Complétude de l'offre du titulaire unique." : "Remise en concurrence de tous les titulaires (décret n° 2019-1064, art. 21)."));
        para(el, "Lettre d'invitation du " + x.getLanceeLe().format(JOUR) + " ; date et heure limites : " + x.getEcheance().format(HEURE) + ".");
        sous(el, "1. Titulaires consultés");
        for (RemiseDto.Consulte k : vue.consultes()) {
            para(el, "– " + k.candidat() + " (offre n° " + k.numero() + ") : " + (k.reponseRecue() ? "réponse reçue" : "aucune réponse à l'échéance")
                    + (k.absencesConsecutives() >= ABSENCES_EXCLUSION ? " — " + k.absencesConsecutives() + " absences consécutives de réponse (exclusion "
                            + "possible, décision de la PRMP)" : "") + ".");
        }
        sous(el, "2. Réponses et décisions");
        List<List<List<String>>> lignes = new ArrayList<>();
        lignes.add(List.of(List.of("Titulaire"), List.of("Nature"), List.of("Montant HT"), List.of("Écart au prix moyen"), List.of("Décision")));
        for (RemiseDto.Reponse y : vue.reponses()) {
            lignes.add(List.of(List.of(y.candidat() + " (offre n° " + y.numero() + ")"), List.of(y.nature().toLowerCase(Locale.FRENCH).replace('_', ' ')),
                    List.of(FormulairesEnLigne.lisible(y.montantHt()) + " Ariary"), List.of(y.ecartPrixMoyen() == null ? "—" : y.ecartPrixMoyen() + " %"),
                    List.of(Boolean.TRUE.equals(y.conforme()) ? "conforme, rang " + y.rang() : "non conforme — " + y.motif())));
        }
        el.add(new DocumentLibre.Tableau(5, lignes));
        if (x.getPrixMoyen() != null) {
            para(el, "Prix moyen du marché, déterminé selon les règles du contrat-cadre : " + FormulairesEnLigne.lisible(x.getPrixMoyen()) + " Ariary hors taxes.");
        }
        if (vue.alertes().contains(DELIEMENT_POSSIBLE)) {
            para(el, "Pour la deuxième remise consécutive, la meilleure offre dépasse de plus de 30 % le prix moyen du marché : l'acheteur public est, "
                    + "pour cet achat, délié de l'exclusivité contractuelle et peut y procéder par une mise en concurrence en dehors du contrat-cadre "
                    + "(décret n° 2019-1064, art. 23).");
        }
        if (vue.alertes().contains(PLAFOND_DEPASSE)) {
            para(el, "La complétude dépasse le plafond d'augmentation des prix fixé au contrat-cadre.");
        }
        sous(el, "3. Proposition de la commission");
        Offre o = x.getIdOffreProposee() == null ? null : offres.findById(x.getIdOffreProposee()).orElse(null);
        para(el, o == null ? "Aucune réponse n'est conforme : la commission propose de déclarer la remise infructueuse."
                : "La commission propose d'attribuer le marché subséquent à " + o.getRaisonSociale() + " (offre n° " + o.getNumero() + "), pour "
                        + vue.reponses().stream().filter(y -> y.idOffre().equals(o.getIdOffre())).map(y -> FormulairesEnLigne.lisible(y.montantHt()))
                                .findFirst().orElse("—") + " Ariary hors taxes, à ce seul titulaire (décret n° 2019-1064, art. 20 et 29).");
        if (x.getObservations() != null) {
            sous(el, "Observations");
            para(el, x.getObservations());
        }
        para(el, "Pour la commission d'appel d'offres réunie en collège restreint, le président : " + internes.nomMembre(x.getEvalueePar())
                + " — signé électroniquement sur la plateforme le " + x.getEvalueeLe().format(HEURE) + ".");
        return new DocumentLibre("RAPPORT_SIMPLIFIE", null, el, "Procédure " + c.getIdDmc() + ", lot " + c.getLot() + " — rapport simplifié de la remise n° "
                + x.getNumero());
    }

    // ------------------------------------------------------------------ droits

    private boolean lisible(ContratVigueur c) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p == null || EXTERNES.contains(p)) {
            return false;
        }
        if (p == ProfilUtilisateur.MEMBRE_CAO) {
            return membreCao(c.getIdDmc());
        }
        if (p == ProfilUtilisateur.PRMP || p == ProfilUtilisateur.UGPM) {
            String idPrmp = CurrentUser.ref().orElse(null);
            return idPrmp != null && attributions.pourPrmp(idPrmp).stream().anyMatch(a -> a.getIdDmc().equals(c.getIdDmc()));
        }
        return true;
    }

    private void exigerLecteur(ContratVigueur c) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p == null || EXTERNES.contains(p) || p == ProfilUtilisateur.MEMBRE_CAO && !membreCao(c.getIdDmc())) {
            throw new AccessDeniedException("Les remises en concurrence se lisent dans l'espace de l'autorité contractante, de la CAO et de la Commission.");
        }
        if (p == ProfilUtilisateur.PRMP || p == ProfilUtilisateur.UGPM) {
            fiches.controlerLecture(c.getIdDmc());
        }
    }

    private boolean membreCao(Long idDmc) {
        String ref = CurrentUser.ref().orElse(null);
        return ref != null && TypeActeur.MEMBRE_CAO.name().equals(CurrentUser.acteurType().orElse(null)) && internes.membresCao(idDmc).contains(ref);
    }

    /** ⚠️ H1 — le président de la CAO de la procédure (collège restreint : à confirmer par le juriste). */
    private String exigerPresidentCao(Long idDmc) {
        String ref = CurrentUser.ref().orElse(null);
        boolean president = membreCao(idDmc) && caoMembres.findByIdDmcOrderByRangAscIdMembreAsc(idDmc).stream()
                .anyMatch(m -> ref.equals(m.getIdCompte()) && Boolean.TRUE.equals(m.getPresident()));
        if (!president) {
            throw new AccessDeniedException("La remise en concurrence s'évalue par la commission d'appel d'offres en collège restreint, sous son président.");
        }
        return ref;
    }

    private void exigerPrmpOuUgpm(Long idDmc) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p != ProfilUtilisateur.PRMP && p != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("La remise en concurrence se conduit par la PRMP ou son UGPM (décret n° 2019-1064, art. 24).");
        }
        fiches.controlerLecture(idDmc);
    }

    private void exigerPrmp(Long idDmc) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Le marché subséquent s'attribue par la PRMP, sur proposition de la commission (décret n° 2019-1064, art. 29).");
        }
        fiches.controlerLecture(idDmc);
    }

    // ------------------------------------------------------------------ outils

    private java.util.Optional<String> offreConsulteeDe(String idCandidat, RemiseConcurrence x) {
        return consultes.findByIdRemiseOrderByIdOffreAsc(x.getId()).stream().map(RemiseConsulte::getIdOffre)
                .filter(id -> offres.findById(id).map(o -> idCandidat.equals(o.getIdCandidat())).orElse(false)).findFirst();
    }

    private void exigerOuverte(RemiseConcurrence x, LocalDateTime le) {
        if (!RemiseConcurrence.LANCEE.equals(x.getEtat())) {
            throw new BusinessRuleException("La remise est évaluée : plus aucune réponse ne se dépose.", "REMISE_CLOSE");
        }
        if (le.isAfter(x.getEcheance())) {
            throw new BusinessRuleException("L'échéance de la remise est passée (" + x.getEcheance().format(HEURE) + ").", "ECHEANCE_PASSEE");
        }
    }

    private boolean echeancePassee(RemiseConcurrence x) {
        return maintenant().isAfter(x.getEcheance());
    }

    private void notifierTitulaire(String idOffre, ContratVigueur c, TypeNotification type, String titre, String corps) {
        Offre o = offres.findById(idOffre).orElse(null);
        if (o == null) {
            return;
        }
        CompteCandidat cc = candidats.findById(o.getIdCandidat()).orElse(null);
        notifications.emettreCandidat(type, o.getIdCandidat(), cc == null ? null : cc.getEmail(), c.getIdDmc().intValue(), TypeObjet.PROCEDURE, titre, corps);
    }

    private Map<String, String> plan(ContratVigueur c) {
        DossierMec dmc = dmcRepository.findById(c.getIdDmc()).orElse(null);
        return dmc == null || dmc.getIdDetail() == null ? Map.of() : valeursPpm.lire(dmc.getIdDetail()).valeurs();
    }

    private String objetContrat(ContratVigueur c) {
        return fiches.etatValide(c.getIdDmc()).map(v -> v.etat().getDesignationMarche()).filter(s -> s != null && !s.isBlank())
                .orElse("procédure " + c.getIdDmc());
    }

    private byte[] contenu(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new BadRequestException("Le fichier de la réponse est obligatoire.", "FICHIER_OBLIGATOIRE");
        }
        byte[] b;
        try {
            b = fichier.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Lecture du fichier impossible.", "FICHIER_ABSENT");
        }
        int mo = parametres.candidats().tailleMaxPieceMo();
        if (b.length > mo * 1024L * 1024L) {
            throw new PayloadTropVolumineuxException("Le fichier dépasse " + mo + " Mo.");
        }
        return b;
    }

    private RemiseConcurrence exiger(Long id) {
        return remises.findById(id).orElseThrow(() -> new ResourceNotFoundException("Remise introuvable : " + id + "."));
    }

    static RemiseConsulte.Cle cle(Long idRemise, String idOffre) {
        RemiseConsulte.Cle k = new RemiseConsulte.Cle();
        k.setIdRemise(idRemise);
        k.setIdOffre(idOffre);
        return k;
    }

    private static String obligatoire(String s, String champ) {
        String v = nettoyer(s);
        if (v == null) {
            throw new BadRequestException("« " + champ + " » est obligatoire.", "CHAMP_OBLIGATOIRE", Map.<String, Object>of("champ", champ));
        }
        return v;
    }

    private static String nettoyer(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private static void sous(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, t));
    }

    private static String acteur() {
        return CurrentUser.ref().or(CurrentUser::login).orElse(null);
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }
}
