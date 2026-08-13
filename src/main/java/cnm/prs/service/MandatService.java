package cnm.prs.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AbrogerMandatRequest;
import cnm.prs.dto.CreerMandatRequest;
import cnm.prs.dto.MandatDto;
import cnm.prs.entity.Mandat;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.StatutMandat;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.exception.VacancePrmpException;
import cnm.prs.mapper.MandatMapper;
import cnm.prs.repository.MandatRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.UgpmRepository;

/**
 * ⚠️ Règle ajoutée (spec « Mandats PRMP ») — cycle de vie des mandats et <strong>habilitation à traiter</strong>.
 *
 * <p>Trois invariants portés ici :</p>
 * <ul>
 *   <li><strong>Durée 3 ans, reconduction = mandat distinct</strong> : jamais de prolongation. Une
 *       reconduction se crée comme une nomination (nouvel arrêté, nouvelles dates) et porte
 *       {@code numeroMandat = 2}.</li>
 *   <li><strong>Renouvellement unique</strong> : un 3ᵉ mandat pour la même personne est refusé (409).</li>
 *   <li><strong>Standby de transition</strong> : sans mandat actif à la date de l'action, le traitement
 *       est bloqué ({@link VacancePrmpException}) — sans intérim, et débloqué automatiquement dès
 *       qu'un mandat redevient actif.</li>
 * </ul>
 *
 * <p><strong>Reprise de l'existant</strong> : les PRMP en place avant l'introduction de {@code t_mandat}
 * n'ont aucun mandat déclaré. Pour elles, le mandat est <em>reconstitué</em> depuis {@code t_prmp}
 * ({@code ARRETE_NOMIN}, {@code DATE_NOMIN} + 3 ans) — la règle « expiration = DATE_NOMIN + 3 ans »
 * (§3.1) devient ainsi opposable sans reprise de données. Dès qu'un mandat est déclaré pour une PRMP,
 * {@code t_mandat} fait seul autorité.</p>
 */
@Service
@Transactional(readOnly = true)
public class MandatService {

    /** Durée légale d'un mandat de PRMP (§3.1). */
    public static final int DUREE_ANNEES = 3;

    /** Nombre maximal de mandats successifs pour une même personne (renouvellement unique). */
    public static final int MANDATS_MAX = 2;

    private final MandatRepository repository;
    private final PrmpRepository prmpRepository;
    private final UgpmRepository ugpmRepository;

    public MandatService(MandatRepository repository, PrmpRepository prmpRepository,
            UgpmRepository ugpmRepository) {
        this.repository = repository;
        this.prmpRepository = prmpRepository;
        this.ugpmRepository = ugpmRepository;
    }

    // ------------------------------------------------------------------ lecture

    /**
     * Historique chronologique des mandats, avec leur statut à la date du jour.
     *
     * @param idUgpm filtre par UGPM — résolu vers sa PRMP de tutelle ({@code t_ugpm.ID_PRMP_TUTELLE})
     * @param idPrmp filtre direct par PRMP ; prioritaire sur {@code idUgpm}
     */
    public List<MandatDto> historique(String idUgpm, String idPrmp) {
        String cible = resoudrePrmp(idUgpm, idPrmp);
        if (cible == null) {
            return repository.findAllByOrderByDateDebutAscIdMandatAsc().stream().map(this::versDto).toList();
        }
        List<Mandat> mandats = repository.findByIdPrmpOrderByDateDebutAscIdMandatAsc(cible);
        if (!mandats.isEmpty()) {
            return mandats.stream().map(this::versDto).toList();
        }
        // Aucun mandat déclaré : on expose le mandat reconstitué depuis t_prmp, pour que l'historique
        // ne soit jamais vide face à une PRMP réellement en fonction.
        return mandatImplicite(cible).map(List::of).orElseGet(List::of);
    }

    /** Un mandat par son identifiant, statut recalculé. */
    public MandatDto findById(Integer id) {
        return versDto(charger(id));
    }

    /**
     * Mandat actif à la date du jour pour le périmètre demandé, s'il en existe un.
     * C'est le signal de <strong>vacance</strong> du front : présent = traitement possible,
     * absent = « en attente de nomination de la nouvelle PRMP ».
     */
    public Optional<MandatDto> mandatActif(String idUgpm, String idPrmp) {
        String cible = resoudrePrmp(idUgpm, idPrmp);
        if (cible == null) {
            return Optional.empty();
        }
        return mandatActifDto(cible, LocalDate.now());
    }

    /**
     * Date de fin de mandat effective d'une PRMP : celle de son <strong>dernier mandat déclaré</strong>
     * (avancée à la date d'abrogation le cas échéant), ou vide si elle n'en a aucun — l'appelant retombe
     * alors sur {@code DATE_NOMIN + 3 ans}.
     *
     * <p>Sans cela, une PRMP <em>valablement reconduite</em> resterait jugée sur sa première nomination :
     * alertes de fin de mandat parasites, et comptes expirés à tort.</p>
     */
    public Optional<LocalDate> finDeMandatDeclare(String idPrmp) {
        return repository.findFirstByIdPrmpOrderByDateDebutDescIdMandatDesc(idPrmp)
                .map(m -> m.getDateAbrogation() != null ? m.getDateAbrogation() : m.getDateFin());
    }

    /** Mandat actif d'une PRMP à une date donnée (entité déclarée uniquement, sans repli implicite). */
    public Optional<Mandat> mandatEnVigueur(String idPrmp, LocalDate date) {
        if (idPrmp == null || idPrmp.isBlank()) {
            return Optional.empty();
        }
        return repository.findEnVigueur(idPrmp, date).stream().findFirst();
    }

    /**
     * Identifiant du mandat sous lequel une action est faite (opérateur courant), ou {@code null}
     * si la PRMP n'a pas de mandat déclaré (mandat implicite : pas de ligne à référencer).
     */
    public Integer idMandatCourant(String idPrmp) {
        return mandatEnVigueur(idPrmp, LocalDate.now()).map(Mandat::getIdMandat).orElse(null);
    }

    // ------------------------------------------------------------------ garde de vacance

    /**
     * Exige qu'une PRMP soit en fonction à la date du jour, sinon <strong>bloque le traitement</strong>.
     *
     * @throws VacancePrmpException 409 {@code VACANCE_PRMP} — aucun mandat actif (achevé, abrogé, ou
     *                              successeur pas encore nommé)
     */
    public void exigerMandatActif(String idPrmp) {
        if (idPrmp == null || idPrmp.isBlank()) {
            return;   // acteur hors périmètre PRMP : la garde de vacance ne le concerne pas
        }
        if (mandatActifDto(idPrmp, LocalDate.now()).isEmpty()) {
            throw new VacancePrmpException();
        }
    }

    /** Vrai si la PRMP est en fonction aujourd'hui (lecture non bloquante, pour exposer un état). */
    public boolean estEnFonction(String idPrmp) {
        return idPrmp != null && !idPrmp.isBlank()
                && mandatActifDto(idPrmp, LocalDate.now()).isPresent();
    }

    // ------------------------------------------------------------------ écriture

    /**
     * Nomination ou reconduction. Une reconduction est un mandat <strong>distinct</strong> : nouvel arrêté,
     * nouvelles dates, {@code numeroMandat = 2}. Le numéro est calculé par le serveur, jamais reçu du client.
     *
     * @throws ResourceNotFoundException PRMP inconnue (404)
     * @throws BadRequestException       dates incohérentes (400)
     * @throws BusinessRuleException     3ᵉ mandat, arrêté réutilisé, chevauchement ou prolongation déguisée (409)
     */
    @Transactional
    public MandatDto creer(CreerMandatRequest req) {
        Prmp prmp = prmpRepository.findById(req.idPrmp().trim())
                .orElseThrow(() -> new ResourceNotFoundException("PRMP introuvable : " + req.idPrmp()));

        long dejaPortes = repository.countByIdPrmp(prmp.getIdPrmp());
        if (dejaPortes >= MANDATS_MAX) {
            throw new BusinessRuleException(
                    "Renouvellement unique : " + nomComplet(prmp) + " (" + prmp.getIdPrmp() + ") a déjà porté "
                            + dejaPortes + " mandats. Un 3ᵉ mandat est impossible — un mandat de "
                            + DUREE_ANNEES + " ans n'est reconductible qu'une seule fois.");
        }

        String refArrete = req.refArrete().trim();
        if (repository.existsByRefArreteIgnoreCase(refArrete)) {
            throw new BusinessRuleException(
                    "Arrêté déjà utilisé par un autre mandat : « " + refArrete
                            + " ». Une reconduction exige un nouvel arrêté (jamais une prolongation).");
        }

        LocalDate debut = req.dateDebut();
        LocalDate fin = req.dateFin() != null ? req.dateFin() : finParDefaut(debut);
        validerPeriode(debut, fin);

        int numero = (int) dejaPortes + 1;
        if (numero == MANDATS_MAX) {
            // Reconduction : elle ne peut partir que d'un 1er mandat, et doit succéder à celui-ci.
            Mandat precedent = repository.findFirstByIdPrmpOrderByDateDebutDescIdMandatDesc(prmp.getIdPrmp())
                    .orElseThrow(() -> new BusinessRuleException("Mandat précédent introuvable : reconduction impossible."));
            validerReconduction(precedent, debut, refArrete);
        }
        if (!repository.findChevauchants(prmp.getIdPrmp(), debut, fin).isEmpty()) {
            throw new BusinessRuleException(
                    "La période " + debut + " → " + fin + " chevauche un mandat existant de cette PRMP.");
        }

        Mandat mandat = new Mandat();
        mandat.setIdPrmp(prmp.getIdPrmp());
        mandat.setTitulaire(req.titulaire() != null && !req.titulaire().isBlank()
                ? req.titulaire().trim() : nomComplet(prmp));
        mandat.setDateDebut(debut);
        mandat.setDateFin(fin);
        mandat.setRefArrete(refArrete);
        mandat.setNumeroMandat(numero);
        mandat.setStatut(statutEffectif(mandat, LocalDate.now()).name());
        return versDto(repository.save(mandat));
    }

    /**
     * Abrogation avant terme. N'entraîne <strong>aucune réattribution</strong> : les dossiers gardent leur
     * mandat d'attribution ; seule la vacance s'ouvre, jusqu'à la nomination du successeur.
     *
     * @throws BusinessRuleException mandat déjà abrogé, ou date d'abrogation hors période (409)
     */
    @Transactional
    public MandatDto abroger(Integer id, AbrogerMandatRequest req) {
        Mandat mandat = charger(id);
        if (mandat.getDateAbrogation() != null) {
            throw new BusinessRuleException("Mandat déjà abrogé le " + mandat.getDateAbrogation() + ".");
        }
        LocalDate date = req.dateAbrogation() != null ? req.dateAbrogation() : LocalDate.now();
        if (date.isBefore(mandat.getDateDebut()) || date.isAfter(mandat.getDateFin())) {
            throw new BusinessRuleException("Date d'abrogation hors de la période du mandat ("
                    + mandat.getDateDebut() + " → " + mandat.getDateFin() + ").");
        }
        mandat.setDateAbrogation(date);
        mandat.setMotifAbrogation(req.motif().trim());
        mandat.setStatut(StatutMandat.ABROGE.name());
        return versDto(repository.save(mandat));
    }

    // ------------------------------------------------------------------ interne

    /**
     * Statut dérivé à une date donnée. {@code ABROGE} prime (acte explicite) ; sinon la période décide :
     * avant → {@code EN_TRANSITION}, pendant → {@code ACTIF}, après → {@code ACHEVE}.
     */
    public StatutMandat statutEffectif(Mandat mandat, LocalDate date) {
        if (mandat.getDateAbrogation() != null && !date.isBefore(mandat.getDateAbrogation())) {
            return StatutMandat.ABROGE;
        }
        if (date.isBefore(mandat.getDateDebut())) {
            return StatutMandat.EN_TRANSITION;
        }
        if (date.isAfter(mandat.getDateFin())) {
            return StatutMandat.ACHEVE;
        }
        return StatutMandat.ACTIF;
    }

    /** Mandat actif (déclaré, sinon reconstitué depuis {@code t_prmp}) d'une PRMP à une date donnée. */
    private Optional<MandatDto> mandatActifDto(String idPrmp, LocalDate date) {
        Optional<Mandat> declare = repository.findEnVigueur(idPrmp, date).stream().findFirst();
        if (declare.isPresent()) {
            return declare.map(this::versDto);
        }
        if (repository.countByIdPrmp(idPrmp) > 0) {
            return Optional.empty();   // des mandats existent mais aucun n'est en vigueur → vacance
        }
        return mandatImplicite(idPrmp)
                .filter(m -> StatutMandat.ACTIF.name().equals(m.getStatut()));
    }

    /**
     * Mandat reconstitué depuis {@code t_prmp} pour une PRMP sans mandat déclaré :
     * {@code DATE_NOMIN} → {@code DATE_NOMIN + 3 ans}, arrêté = {@code ARRETE_NOMIN}, sans identifiant.
     */
    private Optional<MandatDto> mandatImplicite(String idPrmp) {
        return prmpRepository.findById(idPrmp)
                .filter(p -> p.getDateNomin() != null)
                .map(p -> {
                    Mandat virtuel = new Mandat();
                    virtuel.setIdPrmp(p.getIdPrmp());
                    virtuel.setTitulaire(nomComplet(p));
                    virtuel.setDateDebut(p.getDateNomin());
                    virtuel.setDateFin(finParDefaut(p.getDateNomin()));
                    virtuel.setRefArrete(p.getArreteNomin());
                    virtuel.setNumeroMandat(1);
                    MandatDto dto = MandatMapper.toDto(virtuel, statutEffectif(virtuel, LocalDate.now()).name());
                    dto.setImplicite(true);
                    return dto;
                });
    }

    /**
     * Résout les filtres de lecture en une PRMP cible ({@code null} = aucun filtre).
     * {@code ?prmp=} l'emporte sur {@code ?ugpm=} ; une UGPM est résolue vers sa PRMP de tutelle.
     * Exposé pour que le contrôleur puisse contrôler le périmètre <em>après</em> résolution.
     */
    public String resoudrePrmp(String idUgpm, String idPrmp) {
        if (idPrmp != null && !idPrmp.isBlank()) {
            return idPrmp.trim();
        }
        if (idUgpm == null || idUgpm.isBlank()) {
            return null;
        }
        Ugpm ugpm = ugpmRepository.findById(idUgpm.trim())
                .orElseThrow(() -> new ResourceNotFoundException("UGPM introuvable : " + idUgpm));
        return ugpm.getIdPrmpTutelle();
    }

    /** Fin de mandat par défaut : 3 ans révolus à compter de la prise de fonction. */
    private LocalDate finParDefaut(LocalDate debut) {
        return debut.plusYears(DUREE_ANNEES).minusDays(1);
    }

    private void validerPeriode(LocalDate debut, LocalDate fin) {
        if (!fin.isAfter(debut)) {
            throw new BadRequestException("La fin de mandat doit être postérieure à la prise de fonction.");
        }
        if (fin.isAfter(finParDefaut(debut))) {
            throw new BusinessRuleException("Un mandat ne peut excéder " + DUREE_ANNEES
                    + " ans : fin maximale " + finParDefaut(debut) + " pour une prise de fonction au " + debut + ".");
        }
    }

    /**
     * Une reconduction part d'un 1ᵉʳ mandat et lui <strong>succède</strong> : elle commence après sa fin
     * (ou son abrogation) et porte une référence d'arrêté différente. Toute date qui recouvrirait le mandat
     * précédent serait une prolongation déguisée.
     */
    private void validerReconduction(Mandat precedent, LocalDate debut, String refArrete) {
        if (precedent.getNumeroMandat() != null && precedent.getNumeroMandat() != 1) {
            throw new BusinessRuleException(
                    "Une reconduction ne peut suivre qu'un 1ᵉʳ mandat (précédent : n°" + precedent.getNumeroMandat() + ").");
        }
        LocalDate finPrecedent = precedent.getDateAbrogation() != null
                ? precedent.getDateAbrogation() : precedent.getDateFin();
        if (!debut.isAfter(finPrecedent)) {
            throw new BusinessRuleException("Une reconduction est un mandat distinct, pas une prolongation : "
                    + "la prise de fonction doit être postérieure au " + finPrecedent
                    + " (fin du mandat précédent).");
        }
        if (refArrete.equalsIgnoreCase(precedent.getRefArrete())) {
            throw new BusinessRuleException(
                    "Une reconduction exige un nouvel arrêté : « " + refArrete + " » est celui du mandat précédent.");
        }
    }

    private Mandat charger(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mandat introuvable : " + id));
    }

    private MandatDto versDto(Mandat mandat) {
        return MandatMapper.toDto(mandat, statutEffectif(mandat, LocalDate.now()).name());
    }

    private String nomComplet(Prmp prmp) {
        String nom = ((prmp.getPrenomsPrmp() == null ? "" : prmp.getPrenomsPrmp()) + " "
                + (prmp.getNomPrmp() == null ? "" : prmp.getNomPrmp())).trim();
        return nom.isBlank() ? prmp.getIdPrmp() : nom;
    }
}
