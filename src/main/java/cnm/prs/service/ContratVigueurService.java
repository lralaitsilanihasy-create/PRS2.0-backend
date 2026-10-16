package cnm.prs.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ContratVigueurDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.AttributionTitulaire;
import cnm.prs.entity.ContratReconduction;
import cnm.prs.entity.ContratVigueur;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Offre;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.AttributionTitulaireRepository;
import cnm.prs.repository.ContratReconductionRepository;
import cnm.prs.repository.ContratVigueurRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-16 (demande front « marchés subséquents », lot MS-1 ; V124) — le contrat-cadre ou le marché à commandes <strong>en
 * vigueur</strong>, un par lot notifié.
 *
 * <ul>
 *   <li><strong>Création</strong> à la notification du lot (AttributionExecutionService.notifier), pour une fiche de contrat-cadre ou de
 *       marché à commandes : la période de validité court de la date de notification ; durée maximale, reconductions, préavis, montants,
 *       forme et rythme des marchés subséquents sont figés depuis la dernière version validée de la fiche.</li>
 *   <li><strong>Titulaires</strong> : ceux de l'attribution (les titulaires non retirés d'un contrat-cadre multi-attributaire, sinon
 *       l'attributaire), liste fermée (décret n° 2019-1064, art. 18).</li>
 *   <li><strong>Reconduction</strong> : décision écrite de la PRMP (AE-CC, art. 7.3), dans la limite du nombre de reconductions et de la
 *       durée maximale (deux ans sauf justification, art. 19 ; trois ans pour le marché à commandes, loi art. 30).</li>
 *   <li><strong>Échéance</strong> : passé {@code dateFin}, le contrat est échu ; plus aucune remise en concurrence ni commande
 *       ({@link #exigerEnVigueur}, pour MS-2 et MS-3). Pas de minimum pour le contrat-cadre (art. 34).</li>
 * </ul>
 */
@Service
public class ContratVigueurService {

    public static final String EN_VIGUEUR = "EN_VIGUEUR";
    public static final String ECHU = "ECHU";
    /** Les alertes servies au contrat et comptées pour la PRMP. */
    public static final String RECONDUCTION_A_DECIDER = "RECONDUCTION_A_DECIDER";
    public static final String ECHEANCE_PROCHE = "ECHEANCE_PROCHE";
    /** L'échéance est « proche » à moins de ce nombre de jours, sans reconduction décidée. */
    static final int JOURS_ECHEANCE_PROCHE = 30;
    /** Loi n° 2016-055, art. 30 : un marché à commandes ne dépasse pas trois ans. */
    static final int MOIS_MAX_A_COMMANDE = 36;

    private static final Set<ProfilUtilisateur> EXTERNES = Set.of(ProfilUtilisateur.CANDIDAT, ProfilUtilisateur.MEMBRE_CAO,
            ProfilUtilisateur.DEPOSITAIRE);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContratVigueurRepository contrats;
    private final ContratReconductionRepository reconductions;
    private final AttributionRepository attributions;
    private final AttributionTitulaireRepository titulaires;
    private final OffreRepository offres;
    private final DossierMecRepository dmcRepository;
    private final FicheMarcheService fiches;
    private final ValeursPpmService valeursPpm;
    private final EvaluationService evaluation;
    private final Clock horloge;

    public ContratVigueurService(ContratVigueurRepository contrats, ContratReconductionRepository reconductions,
            AttributionRepository attributions, AttributionTitulaireRepository titulaires, OffreRepository offres,
            DossierMecRepository dmcRepository, FicheMarcheService fiches, ValeursPpmService valeursPpm, EvaluationService evaluation,
            Clock horloge) {
        this.contrats = contrats;
        this.reconductions = reconductions;
        this.attributions = attributions;
        this.titulaires = titulaires;
        this.offres = offres;
        this.dmcRepository = dmcRepository;
        this.fiches = fiches;
        this.valeursPpm = valeursPpm;
        this.evaluation = evaluation;
        this.horloge = horloge;
    }

    // ------------------------------------------------------------------ création à la notification

    /**
     * Le contrat en vigueur du lot qui vient d'être notifié, s'il s'agit d'un contrat-cadre ou d'un marché à commandes (rien sinon, ni
     * s'il existe déjà). Appelé par la notification, dans sa transaction.
     */
    void creerALaNotification(Attribution a) {
        FicheMarcheService.EtatVersion v = fiches.etatValide(a.getIdDmc()).orElse(null);
        String type = v == null ? null : v.type();
        if (!ContratVigueur.CONTRAT_CADRE.equals(type) && !ContratVigueur.A_COMMANDE.equals(type)
                || contrats.findByIdDmcAndLot(a.getIdDmc(), a.getLot()).isPresent() || a.getDateNotification() == null) {
            return;
        }
        Integer lot = a.getLot();
        ContratVigueur c = new ContratVigueur();
        c.setIdDmc(a.getIdDmc());
        c.setLot(lot);
        c.setTypeMarche(type);
        c.setDateEffet(a.getDateNotification());
        if (ContratVigueur.CONTRAT_CADRE.equals(type)) {
            Integer periode = entier(v, "B02-DC-02", lot);
            Integer maxMois = entier(v, "B02-DC-01", lot);
            Integer maxAns = entier(v, "B07-DU-05", lot);
            c.setPeriodeMois(periode);
            c.setDureeMaxMois(maxMois != null ? maxMois : maxAns != null ? maxAns * 12 : periode);
            c.setReconductible("OUI".equalsIgnoreCase(valeur(v, "B02-DC-03", lot)));
            c.setReconductionsMax(c.isReconductible() ? entier(v, "B07-DU-04", lot) : Integer.valueOf(0));
            c.setPreavisMois(c.isReconductible() ? entier(v, "B07-DU-07", lot) : null);
            c.setMontantEstime(nombre(v, "B05-MT-01", lot));
            c.setMontantMinimum(nombre(v, "B05-MT-03", lot));
            c.setFormeSubsequents(valeur(v, "B07-FS-01", lot));
            c.setRythme(valeur(v, "B02-PC-02", lot));
            c.setCalendrier(valeur(v, "B02-PC-03", lot));
        } else {
            Integer periode = entier(v, "B02-AU-04", lot);
            c.setPeriodeMois(periode == null ? null : Math.min(periode, MOIS_MAX_A_COMMANDE));   // loi, art. 30 : trois ans au plus
            c.setDureeMaxMois(periode == null ? MOIS_MAX_A_COMMANDE : Math.min(periode, MOIS_MAX_A_COMMANDE));
            c.setReconductible(false);
            c.setReconductionsMax(0);
            c.setMontantMinimum(nombre(v, "B05-TP-02", lot));
            c.setMontantMaximum(nombre(v, "B05-TP-03", lot));
            c.setMontantEstime(c.getMontantMaximum());
            c.setRythme(valeur(v, "B09-DX-03", lot));
        }
        c.setDateFin(finDePeriode(c.getDateEffet(), c.getPeriodeMois() == null ? c.getDureeMaxMois() : c.getPeriodeMois()));
        c.setCreeLe(maintenant());
        contrats.save(c);
        evaluation.tracerAttribution(a.getIdDmc(), "CONTRAT_EN_VIGUEUR", "Lot " + lot + " : " + (ContratVigueur.CONTRAT_CADRE.equals(type)
                ? "contrat-cadre" : "marché à commandes") + " en vigueur du " + c.getDateEffet().format(JOUR) + " au " + c.getDateFin().format(JOUR)
                + (c.isReconductible() ? ", reconductible " + Objects.toString(c.getReconductionsMax(), "—") + " fois" : ""));
    }

    /** La fin d'une période : le dernier jour avant la date anniversaire ({@code effet + mois − 1 jour}) ; sans durée, l'effet. */
    static LocalDate finDePeriode(LocalDate debut, Integer mois) {
        return mois == null || mois <= 0 ? debut : debut.plusMonths(mois).minusDays(1);
    }

    // ------------------------------------------------------------------ lecture

    /**
     * Les contrats lisibles par le connecté, les plus proches de l'échéance d'abord : PRMP et UGPM, ceux de ses procédures ; Commission et
     * Administrateur, tous ; 403 aux comptes externes.
     */
    @Transactional(readOnly = true)
    public List<ContratVigueurDto> lister() {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == null || EXTERNES.contains(profil)) {
            throw new AccessDeniedException("Les contrats en vigueur se lisent dans l'espace de l'autorité contractante et de la Commission.");
        }
        List<ContratVigueur> tous = contrats.findAllByOrderByDateFinAscIdAsc();
        if (profil == ProfilUtilisateur.PRMP || profil == ProfilUtilisateur.UGPM) {
            String idPrmp = CurrentUser.ref().orElse(null);
            Set<String> miens = idPrmp == null ? Set.of() : attributions.pourPrmp(idPrmp).stream().map(x -> x.getIdDmc() + "#" + x.getLot())
                    .collect(java.util.stream.Collectors.toSet());
            tous = tous.stream().filter(c -> miens.contains(c.getIdDmc() + "#" + c.getLot())).toList();
        }
        return tous.stream().map(this::dto).toList();
    }

    /** Un contrat : mêmes lecteurs ; la PRMP et l'UGPM au périmètre de la fiche ; 404 inconnu. */
    @Transactional(readOnly = true)
    public ContratVigueurDto lire(Long id) {
        ContratVigueur c = exiger(id);
        controlerLecture(c);
        return dto(c);
    }

    // ------------------------------------------------------------------ reconduction

    /**
     * La décision écrite de reconduire ou non (AE-CC, art. 7.3), par la PRMP de la fiche : {@code decision} ∈ {@code RECONDUIRE},
     * {@code NE_PAS_RECONDUIRE} (400 {@code DECISION_INVALIDE}), {@code reference} et {@code dateDecision} au plus aujourd'hui (400
     * {@code DECISION_OBLIGATOIRE}, {@code DATE_INVALIDE}) ; 409 {@code CONTRAT_ECHU}, {@code NON_RECONDUCTIBLE},
     * {@code RECONDUCTIONS_EPUISEES}, {@code DUREE_MAX_ATTEINTE} ({@code details.finMaximale}), {@code DEJA_DECIDE} (une décision pour la
     * période en cours). Reconduit, la période suivante commence à la date anniversaire ; prise moins de {@code preavisMois} avant la fin,
     * la décision est marquée {@code horsPreavis}.
     */
    @Transactional
    public ContratVigueurDto reconduire(Long id, ContratVigueurDto.ReconductionRequest r) {
        ContratVigueur c = exiger(id);
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("La reconduction se décide par la PRMP de la fiche.");
        }
        fiches.controlerLecture(c.getIdDmc());
        String decision = r == null || r.decision() == null ? null : r.decision().trim().toUpperCase(Locale.ROOT);
        if (!ContratReconduction.RECONDUIRE.equals(decision) && !ContratReconduction.NE_PAS_RECONDUIRE.equals(decision)) {
            throw new BadRequestException("La décision est RECONDUIRE ou NE_PAS_RECONDUIRE.", "DECISION_INVALIDE");
        }
        String reference = r.reference() == null || r.reference().isBlank() ? null : r.reference().trim();
        if (reference == null || r.dateDecision() == null) {
            throw new BadRequestException("La décision écrite (référence et date) est attendue.", "DECISION_OBLIGATOIRE");
        }
        LocalDate aujourdhui = aujourdhui();
        if (r.dateDecision().isAfter(aujourdhui) || r.dateDecision().isBefore(c.getDateEffet())) {
            throw new BadRequestException("La date de la décision se situe entre l'entrée en vigueur et aujourd'hui.", "DATE_INVALIDE");
        }
        if (aujourdhui.isAfter(c.getDateFin())) {
            throw new BusinessRuleException("Le contrat est échu depuis le " + c.getDateFin().format(JOUR) + " : il ne se reconduit plus.",
                    "CONTRAT_ECHU");
        }
        if (decisionPeriodeEnCours(c) != null) {
            throw new BusinessRuleException("La décision de reconduire ou non est déjà prise pour la période en cours.", "DEJA_DECIDE");
        }
        LocalDate nouvelleFin = null;
        if (ContratReconduction.RECONDUIRE.equals(decision)) {
            if (!c.isReconductible()) {
                throw new BusinessRuleException("Le contrat n'est pas reconductible.", "NON_RECONDUCTIBLE");
            }
            if (c.getReconductionsMax() != null && c.getReconductions() >= c.getReconductionsMax()) {
                throw new BusinessRuleException("Les " + c.getReconductionsMax() + " reconduction(s) prévue(s) sont faites.", "RECONDUCTIONS_EPUISEES");
            }
            nouvelleFin = finDePeriode(c.getDateFin().plusDays(1), c.getPeriodeMois());
            LocalDate finMaximale = finMaximale(c);
            if (finMaximale != null && nouvelleFin.isAfter(finMaximale)) {
                throw new BusinessRuleException("La reconduction porterait le contrat au-delà de sa durée maximale (" + c.getDureeMaxMois()
                        + " mois, jusqu'au " + finMaximale.format(JOUR) + ").", "DUREE_MAX_ATTEINTE", null,
                        java.util.Map.of("finMaximale", finMaximale.toString()));
            }
        }
        LocalDate limite = dateLimiteDecision(c);
        ContratReconduction x = new ContratReconduction();
        x.setIdContrat(c.getId());
        x.setDecision(decision);
        x.setReference(reference);
        x.setDateDecision(r.dateDecision());
        x.setFinAvant(c.getDateFin());
        x.setNouvelleFin(nouvelleFin);
        x.setHorsPreavis(limite != null && r.dateDecision().isAfter(limite));
        x.setLe(maintenant());
        x.setPar(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        reconductions.save(x);
        if (nouvelleFin != null) {
            c.setDateFin(nouvelleFin);
            c.setReconductions(c.getReconductions() + 1);
            contrats.save(c);
        }
        evaluation.tracerAttribution(c.getIdDmc(), "RECONDUCTION", "Lot " + c.getLot() + " : "
                + (nouvelleFin != null ? "contrat reconduit jusqu'au " + nouvelleFin.format(JOUR) + " (reconduction " + c.getReconductions()
                        + (c.getReconductionsMax() == null ? "" : "/" + c.getReconductionsMax()) + ")"
                        : "contrat non reconduit, échéance le " + c.getDateFin().format(JOUR))
                + ", décision " + reference + " du " + r.dateDecision().format(JOUR) + (x.isHorsPreavis() ? " (hors préavis)" : ""));
        return dto(c);
    }

    // ------------------------------------------------------------------ pour les lots suivants (MS-2, MS-3) et les compteurs

    /** Le contrat en vigueur du lot ; 404 sans contrat ; 409 {@code CONTRAT_ECHU} une fois l'échéance passée. */
    @Transactional(readOnly = true)
    public ContratVigueur exigerEnVigueur(Long idDmc, Integer lot) {
        ContratVigueur c = contrats.findByIdDmcAndLot(idDmc, lot)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun contrat en vigueur pour ce lot."));
        if (aujourdhui().isAfter(c.getDateFin())) {
            throw new BusinessRuleException("Le contrat est échu depuis le " + c.getDateFin().format(JOUR)
                    + " : plus aucune remise en concurrence ni commande.", "CONTRAT_ECHU");
        }
        return c;
    }

    /** Les compteurs de la PRMP : {@code reconductionsADecider}, {@code contratsAEcheance} (échéance à moins de 30 jours, sans reconduction). */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> compteursPrmp(String idPrmp) {
        long aDecider = 0;
        long aEcheance = 0;
        if (idPrmp != null && !idPrmp.isBlank()) {
            Set<String> miens = attributions.pourPrmp(idPrmp).stream().map(x -> x.getIdDmc() + "#" + x.getLot())
                    .collect(java.util.stream.Collectors.toSet());
            for (ContratVigueur c : contrats.findAllByOrderByDateFinAscIdAsc()) {
                if (!miens.contains(c.getIdDmc() + "#" + c.getLot())) {
                    continue;
                }
                List<String> al = alertes(c);
                aDecider += al.contains(RECONDUCTION_A_DECIDER) ? 1 : 0;
                aEcheance += al.contains(ECHEANCE_PROCHE) ? 1 : 0;
            }
        }
        return java.util.Map.of("reconductionsADecider", aDecider, "contratsAEcheance", aEcheance);
    }

    // ------------------------------------------------------------------ dérivations

    static LocalDate finMaximale(ContratVigueur c) {
        return c.getDureeMaxMois() == null ? null : finDePeriode(c.getDateEffet(), c.getDureeMaxMois());
    }

    /** La date avant laquelle la décision se prend : {@code dateFin} − préavis ; nulle sans reconduction possible. */
    static LocalDate dateLimiteDecision(ContratVigueur c) {
        return !c.isReconductible() || c.getPreavisMois() == null ? null : c.getDateFin().minusMonths(c.getPreavisMois());
    }

    /** La décision prise pour la période en cours (celle dont {@code finAvant} est la fin actuelle), ou {@code null}. */
    private ContratReconduction decisionPeriodeEnCours(ContratVigueur c) {
        return reconductions.findByIdContratOrderByLeAscIdAsc(c.getId()).stream()
                .filter(x -> c.getDateFin().equals(x.getFinAvant()))   // reconduit, la période suivante n'a pas encore de décision
                .reduce((x, y) -> y).orElse(null);
    }

    /** Une reconduction est encore possible : reconductible, pas épuisée, dans la durée maximale. */
    static boolean reconductionPossible(ContratVigueur c) {
        if (!c.isReconductible() || c.getReconductionsMax() != null && c.getReconductions() >= c.getReconductionsMax()) {
            return false;
        }
        LocalDate fin = finMaximale(c);
        return fin == null || !finDePeriode(c.getDateFin().plusDays(1), c.getPeriodeMois()).isAfter(fin);
    }

    List<String> alertes(ContratVigueur c) {
        LocalDate aujourdhui = aujourdhui();
        List<String> al = new ArrayList<>();
        if (aujourdhui.isAfter(c.getDateFin())) {
            al.add(ECHU);
            return al;
        }
        boolean decide = decisionPeriodeEnCours(c) != null;
        LocalDate limite = dateLimiteDecision(c);
        if (!decide && reconductionPossible(c) && limite != null && !aujourdhui.isBefore(limite.minusDays(JOURS_ECHEANCE_PROCHE))) {
            al.add(RECONDUCTION_A_DECIDER);
        }
        // Une reconduction décidée a déjà reporté dateFin : à moins de 30 jours de la fin en cours, l'échéance est toujours proche.
        if (ChronoUnit.DAYS.between(aujourdhui, c.getDateFin()) <= JOURS_ECHEANCE_PROCHE) {
            al.add(ECHEANCE_PROCHE);
        }
        return al;
    }

    private ContratVigueurDto dto(ContratVigueur c) {
        List<ContratVigueurDto.Decision> decisions = reconductions.findByIdContratOrderByLeAscIdAsc(c.getId()).stream()
                .map(x -> new ContratVigueurDto.Decision(x.getId(), x.getDecision(), x.getReference(), x.getDateDecision(), x.getFinAvant(),
                        x.getNouvelleFin(), x.isHorsPreavis(), x.getLe(), x.getPar())).toList();
        FicheMarcheService.EtatVersion v = fiches.etatValide(c.getIdDmc()).orElse(null);
        String objet = v == null ? null : v.etat().getDesignationMarche();
        DossierMec dmc = dmcRepository.findById(c.getIdDmc()).orElse(null);
        String acheteur = dmc == null || dmc.getIdDetail() == null ? null : valeursPpm.lire(dmc.getIdDetail()).valeurs().get("ENTITE");
        return new ContratVigueurDto(c.getId(), c.getIdDmc(), c.getLot(), c.getTypeMarche(), objet, acheteur,
                aujourdhui().isAfter(c.getDateFin()) ? ECHU : EN_VIGUEUR, c.getDateEffet(), c.getDateFin(), c.getPeriodeMois(),
                c.getDureeMaxMois(), finMaximale(c), c.isReconductible(), c.getReconductionsMax(), c.getReconductions(), c.getPreavisMois(),
                dateLimiteDecision(c), c.getMontantEstime(), c.getMontantMinimum(), c.getMontantMaximum(), c.getFormeSubsequents(), c.getRythme(),
                c.getCalendrier(), titulairesDto(c), decisions, alertes(c));
    }

    /** Les titulaires : ceux de l'attribution non retirés, sinon l'attributaire du lot. */
    List<ContratVigueurDto.Titulaire> titulairesDto(ContratVigueur c) {
        List<AttributionTitulaire> tit = titulaires.findByIdDmcAndLotOrderByRangAscIdOffreAsc(c.getIdDmc(), c.getLot()).stream()
                .filter(t -> t.getRetireLe() == null).toList();
        if (!tit.isEmpty()) {
            return tit.stream().map(t -> {
                Offre o = offres.findById(t.getIdOffre()).orElse(null);
                return new ContratVigueurDto.Titulaire(t.getIdOffre(), o == null ? null : o.getNumero(), o == null ? null : o.getRaisonSociale(),
                        o == null ? null : o.getNif(), t.getMontant(), t.getDelai(), t.getRang());
            }).toList();
        }
        Attribution a = attributions.findById(new Attribution.Cle(c.getIdDmc(), c.getLot())).orElse(null);
        if (a == null || a.getIdOffreAttribuee() == null) {
            return List.of();
        }
        Offre o = offres.findById(a.getIdOffreAttribuee()).orElse(null);
        return List.of(new ContratVigueurDto.Titulaire(a.getIdOffreAttribuee(), o == null ? null : o.getNumero(),
                o == null ? null : o.getRaisonSociale(), o == null ? null : o.getNif(), a.getMontant(), a.getDelai(), 1));
    }

    // ------------------------------------------------------------------ outils

    private ContratVigueur exiger(Long id) {
        return contrats.findById(id).orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + id + "."));
    }

    private void controlerLecture(ContratVigueur c) {
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        if (profil == null || EXTERNES.contains(profil)) {
            throw new AccessDeniedException("Les contrats en vigueur se lisent dans l'espace de l'autorité contractante et de la Commission.");
        }
        if (profil == ProfilUtilisateur.PRMP || profil == ProfilUtilisateur.UGPM) {
            fiches.controlerLecture(c.getIdDmc());
        }
    }

    /** La valeur brute (normalisée, non mise en forme) de la version validée : celle du lot ({@code CODE#n}) d'abord. */
    static String valeur(FicheMarcheService.EtatVersion v, String code, Integer lot) {
        java.util.Map<String, String> valeurs = v == null || v.etat() == null ? null : v.etat().getValeurs();
        if (valeurs == null) {
            return null;
        }
        String x = lot == null ? null : valeurs.get(code + LotsFiche.SEPARATEUR + lot);
        if (x == null || x.isBlank()) {
            x = valeurs.get(code);
        }
        return x == null || x.isBlank() ? null : x.trim();
    }

    private static BigDecimal nombre(FicheMarcheService.EtatVersion v, String code, Integer lot) {
        return ControlesFicheMarche.nombre(valeur(v, code, lot));
    }

    private static Integer entier(FicheMarcheService.EtatVersion v, String code, Integer lot) {
        BigDecimal n = nombre(v, code, lot);
        return n == null || n.signum() <= 0 ? null : n.intValue();
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(horloge);
    }

    private LocalDateTime maintenant() {
        return LocalDateTime.now(horloge).withNano(0);
    }
}
