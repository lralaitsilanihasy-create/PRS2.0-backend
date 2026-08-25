package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DeclarationEntiteDto;
import cnm.prs.dto.InscriptionEnAttenteDto;
import cnm.prs.dto.PieceJointeMetaDto;
import cnm.prs.dto.ValidationInscriptionRequest;
import cnm.prs.dto.ValidationInscriptionRequest.DecisionEntiteProposee;
import cnm.prs.dto.ValidationInscriptionResponse;
import cnm.prs.dto.ValidationInscriptionResponse.Conflit;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.PieceJointe;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.PrmpEntite;
import cnm.prs.entity.PrmpEntiteDemande;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.StatutCompte;
import cnm.prs.enums.StatutDemandeEntite;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypePieceJointe;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.PieceJointeMapper;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.PieceJointeRepository;
import cnm.prs.repository.PrmpEntiteDemandeRepository;
import cnm.prs.repository.PrmpEntiteRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.UgpmRepository;
import cnm.prs.security.CurrentUser;

/**
 * Instruction des inscriptions PRMP par l'Administrateur (§3.1) : consultation des inscriptions
 * en attente, <strong>validation</strong> (vérification humaine de l'arrêté) ou <strong>refus</strong>
 * motivé, et téléchargement des pièces.
 *
 * <p>La validation est <strong>partielle</strong> : chaque entité déclarée disponible est activée
 * (création d'une affectation {@code t_prmp_entite}) ; les entités déjà rattachées à une autre PRMP
 * active sont signalées en conflit ; les entités proposées acceptées sont créées dans le référentiel.
 * Le compte n'est activé que si <strong>au moins une</strong> entité a été activée ; sinon il reste
 * {@code EN_ATTENTE} avec le récapitulatif des conflits.</p>
 */
@Service
@Transactional
public class InscriptionService {

    private final CompteAuthRepository compteRepository;
    private final PrmpRepository prmpRepository;
    private final PrmpEntiteDemandeRepository demandeRepository;
    private final PrmpEntiteRepository prmpEntiteRepository;
    private final EntiteContractRepository entiteContractRepository;
    private final PieceJointeRepository pieceJointeRepository;
    private final PieceJointeService pieceJointeService;
    private final NotificationService notificationService;
    private final UgpmRepository ugpmRepository;

    public InscriptionService(CompteAuthRepository compteRepository, PrmpRepository prmpRepository,
            PrmpEntiteDemandeRepository demandeRepository, PrmpEntiteRepository prmpEntiteRepository,
            EntiteContractRepository entiteContractRepository, PieceJointeRepository pieceJointeRepository,
            PieceJointeService pieceJointeService, NotificationService notificationService,
            UgpmRepository ugpmRepository) {
        this.compteRepository = compteRepository;
        this.prmpRepository = prmpRepository;
        this.demandeRepository = demandeRepository;
        this.prmpEntiteRepository = prmpEntiteRepository;
        this.entiteContractRepository = entiteContractRepository;
        this.pieceJointeRepository = pieceJointeRepository;
        this.pieceJointeService = pieceJointeService;
        this.notificationService = notificationService;
        this.ugpmRepository = ugpmRepository;
    }

    /**
     * Inscriptions <strong>PRMP et UGPM</strong> en attente de validation, enrichies (entités
     * déclarées + pièces pour la PRMP ; identité + tutelle + pièces pour l'UGPM). Union des deux types.
     */
    @Transactional(readOnly = true)
    public List<InscriptionEnAttenteDto> enAttente() {
        List<InscriptionEnAttenteDto> resultat = new ArrayList<>(
                compteRepository.findByStatutAndTypeActeur(StatutCompte.EN_ATTENTE.name(), TypeActeur.PRMP.name())
                        .stream().map(this::toInscriptionDto).toList());
        resultat.addAll(
                compteRepository.findByStatutAndTypeActeur(StatutCompte.EN_ATTENTE.name(), TypeActeur.UGPM.name())
                        .stream().map(this::toInscriptionUgpmDto).toList());
        return resultat;
    }

    private InscriptionEnAttenteDto toInscriptionDto(CompteAuth compte) {
        Prmp prmp = prmpRepository.findById(compte.getRefActeur()).orElse(null);
        List<DeclarationEntiteDto> declarations = demandeRepository.findByLogin(compte.getLogin())
                .stream().map(this::toDeclarationDto).toList();
        List<PieceJointeMetaDto> pieces = pieceJointeRepository.findByLogin(compte.getLogin())
                .stream().map(PieceJointeMapper::toDto).toList();
        return new InscriptionEnAttenteDto(TypeActeur.PRMP.name(), compte.getLogin(), compte.getRefActeur(),
                prmp != null ? prmp.getNomPrmp() : null,
                prmp != null ? prmp.getPrenomsPrmp() : null,
                prmp != null ? prmp.getEmailPrmp() : null,
                null, declarations, pieces);
    }

    private InscriptionEnAttenteDto toInscriptionUgpmDto(CompteAuth compte) {
        Ugpm ugpm = ugpmRepository.findById(compte.getRefActeur()).orElse(null);
        List<PieceJointeMetaDto> pieces = pieceJointeRepository.findByLogin(compte.getLogin())
                .stream().map(PieceJointeMapper::toDto).toList();
        return new InscriptionEnAttenteDto(TypeActeur.UGPM.name(), compte.getLogin(), compte.getRefActeur(),
                ugpm != null ? ugpm.getNomUgpm() : null,
                ugpm != null ? ugpm.getPrenomsUgpm() : null,
                ugpm != null ? ugpm.getEmailUgpm() : null,
                ugpm != null ? ugpm.getIdPrmpTutelle() : null,
                List.of(), pieces);
    }

    private DeclarationEntiteDto toDeclarationDto(PrmpEntiteDemande d) {
        Boolean disponible = null;
        if (d.getIdEntiteContract() != null) {
            disponible = prmpEntiteRepository.findByIdEntiteContractAndActifTrue(d.getIdEntiteContract()).isEmpty();
        }
        return new DeclarationEntiteDto(d.getIdDemande(), d.getIdEntiteContract(), d.getLibellePropose(),
                d.getAdressePropose(), d.getIdLocalitePropose(), d.getCategoriePropose(),
                d.getStatutDemande(), d.getMotif(), disponible);
    }

    /**
     * Valide une inscription (partiellement) : active les entités disponibles, crée les entités
     * proposées acceptées, signale les conflits. Active le compte si ≥ 1 entité activée.
     */
    public ValidationInscriptionResponse valider(String login, ValidationInscriptionRequest req) {
        CompteAuth compte = chargerEnAttente(login);

        // UGPM : pas d'entités à instruire → activation directe du compte.
        if (TypeActeur.UGPM.name().equals(compte.getTypeActeur())) {
            compte.setStatut(StatutCompte.ACTIF.name());
            compte.setActif(true);
            compte.setDateDecision(LocalDateTime.now());
            compte.setImValidateur(CurrentUser.ref().orElse(null));
            compteRepository.save(compte);
            // Unifie les pièces d'inscription (clé login) sur la clé id acteur (comme les pièces Admin).
            pieceJointeService.reAffecter(login, compte.getRefActeur());
            notifierUgpm(compte.getRefActeur(), TypeNotification.INSCRIPTION_VALIDEE, "Inscription validée",
                    "Votre compte UGPM a été activé. Vous pouvez désormais vous connecter.");
            return new ValidationInscriptionResponse(List.of("compte UGPM activé"), List.of(),
                    StatutCompte.ACTIF.name());
        }

        String idPrmp = compte.getRefActeur();
        Map<Integer, DecisionEntiteProposee> decisions = indexerDecisions(req);

        List<PrmpEntiteDemande> demandes = demandeRepository
                .findByLoginAndStatutDemande(login, StatutDemandeEntite.EN_ATTENTE.name());
        // PK de rattachement et d'entité : allouées ligne par ligne sur seq_prmp_entite /
        // seq_entite_contract. Les deux compteurs locaux qui les tenaient auparavant (max+1 puis ++)
        // laissaient la séquence en retard sur les lignes écrites — la validation suivante aurait
        // réattribué les mêmes ids et écrasé ces rattachements (save() sur PK assignée = merge).
        List<String> validees = new ArrayList<>();
        List<Conflit> conflits = new ArrayList<>();

        for (PrmpEntiteDemande d : demandes) {
            if (d.getIdEntiteContract() != null) {
                // Entité existante : activable si elle n'est pas déjà prise par une PRMP active.
                Optional<PrmpEntite> active = prmpEntiteRepository
                        .findByIdEntiteContractAndActifTrue(d.getIdEntiteContract());
                if (active.isPresent()) {
                    d.setStatutDemande(StatutDemandeEntite.REFUSEE.name());
                    d.setMotif("Entité déjà rattachée à la PRMP " + active.get().getIdPrmp() + ".");
                    conflits.add(new Conflit(d.getIdEntiteContract(), null, d.getMotif()));
                } else {
                    creerAffectation(idPrmp, d.getIdEntiteContract());
                    d.setStatutDemande(StatutDemandeEntite.VALIDEE.name());
                    validees.add("entité " + d.getIdEntiteContract());
                }
            } else {
                // Entité proposée : créée seulement si l'Administrateur l'accepte (avec un organigramme).
                DecisionEntiteProposee dec = decisions.get(d.getIdDemande());
                if (dec != null && dec.accepter() && dec.idOrganigramme() != null) {
                    EntiteContract e = new EntiteContract();
                    e.setIdEntiteContract(entiteContractRepository.nextIdEntiteContract().intValue());
                    e.setLibelleEntite(d.getLibellePropose());
                    e.setAdresse(d.getAdressePropose());
                    e.setCategorieEntite(d.getCategoriePropose());
                    e.setIdOrganigramme(dec.idOrganigramme());
                    e.setIdLocalite(d.getIdLocalitePropose());
                    entiteContractRepository.save(e);
                    creerAffectation(idPrmp, e.getIdEntiteContract());
                    d.setIdEntiteContract(e.getIdEntiteContract());
                    d.setStatutDemande(StatutDemandeEntite.VALIDEE.name());
                    validees.add("entité proposée « " + d.getLibellePropose() + " » (créée id "
                            + e.getIdEntiteContract() + ")");
                } else {
                    d.setStatutDemande(StatutDemandeEntite.REFUSEE.name());
                    d.setMotif("Entité proposée non retenue.");
                    conflits.add(new Conflit(null, d.getLibellePropose(), d.getMotif()));
                }
            }
            demandeRepository.save(d);
        }

        String statutFinal;
        if (!validees.isEmpty()) {
            compte.setStatut(StatutCompte.ACTIF.name());
            compte.setActif(true);
            compte.setDateDecision(LocalDateTime.now());
            compte.setImValidateur(CurrentUser.ref().orElse(null));
            compteRepository.save(compte);
            // Unifie les pièces d'inscription (clé login) sur la clé idPrmp (comme les pièces Admin).
            pieceJointeService.reAffecter(login, idPrmp);
            notifierPrmp(idPrmp, TypeNotification.INSCRIPTION_VALIDEE, "Inscription validée",
                    "Votre compte a été activé. Entités rattachées : " + validees
                            + (conflits.isEmpty() ? "." : " ; non retenues : " + conflits.size() + "."));
            statutFinal = StatutCompte.ACTIF.name();
        } else {
            // Aucune entité activée : le compte reste en attente (l'Administrateur corrige ou refuse).
            statutFinal = compte.getStatut();
        }
        return new ValidationInscriptionResponse(validees, conflits, statutFinal);
    }

    /** Refuse une inscription (motif communiqué à la PRMP). Le compte reste non connectable. */
    public void refuser(String login, String motif) {
        CompteAuth compte = chargerEnAttente(login);
        compte.setStatut(StatutCompte.REFUSE.name());
        compte.setActif(false);
        compte.setMotifRefus(motif);
        compte.setDateDecision(LocalDateTime.now());
        compte.setImValidateur(CurrentUser.ref().orElse(null));
        compteRepository.save(compte);

        // Demandes d'entités (PRMP uniquement ; l'UGPM n'en a pas → boucle vide).
        for (PrmpEntiteDemande d : demandeRepository.findByLoginAndStatutDemande(login,
                StatutDemandeEntite.EN_ATTENTE.name())) {
            d.setStatutDemande(StatutDemandeEntite.REFUSEE.name());
            d.setMotif("Inscription refusée.");
            demandeRepository.save(d);
        }
        String corps = "Votre inscription a été refusée. Motif : " + motif;
        if (TypeActeur.UGPM.name().equals(compte.getTypeActeur())) {
            notifierUgpm(compte.getRefActeur(), TypeNotification.INSCRIPTION_REFUSEE, "Inscription refusée", corps);
        } else {
            notifierPrmp(compte.getRefActeur(), TypeNotification.INSCRIPTION_REFUSEE, "Inscription refusée", corps);
        }
    }

    /** Récupère une pièce d'une inscription pour téléchargement (contenu + format). */
    @Transactional(readOnly = true)
    public PieceJointe telecharger(String login, TypePieceJointe type) {
        return pieceJointeService.telecharger(login, type);
    }

    /** Rattache l'entité à la PRMP. La PK vient de {@code seq_prmp_entite}, consommée à chaque appel. */
    private void creerAffectation(String idPrmp, Integer idEntite) {
        PrmpEntite aff = new PrmpEntite();
        aff.setIdPrmpEntite(prmpEntiteRepository.nextIdPrmpEntite().intValue());
        aff.setIdPrmp(idPrmp);
        aff.setIdEntiteContract(idEntite);
        aff.setDateAffectation(LocalDate.now());
        aff.setActif(Boolean.TRUE);
        prmpEntiteRepository.save(aff);
    }

    private Map<Integer, DecisionEntiteProposee> indexerDecisions(ValidationInscriptionRequest req) {
        Map<Integer, DecisionEntiteProposee> map = new HashMap<>();
        if (req != null && req.entitesProposees() != null) {
            for (DecisionEntiteProposee d : req.entitesProposees()) {
                if (d != null && d.idDemande() != null) {
                    map.put(d.idDemande(), d);
                }
            }
        }
        return map;
    }

    private CompteAuth chargerEnAttente(String login) {
        CompteAuth compte = compteRepository.findByLogin(login)
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable : " + login));
        if (!TypeActeur.PRMP.name().equals(compte.getTypeActeur())
                && !TypeActeur.UGPM.name().equals(compte.getTypeActeur())) {
            throw new BusinessRuleException("Ce compte n'est pas une inscription PRMP ou UGPM.");
        }
        if (!StatutCompte.EN_ATTENTE.name().equals(compte.getStatut())) {
            throw new BusinessRuleException("L'inscription n'est pas en attente (statut « "
                    + compte.getStatut() + " »).");
        }
        return compte;
    }

    private void notifierPrmp(String idPrmp, TypeNotification type, String titre, String corps) {
        String email = prmpRepository.findById(idPrmp).map(Prmp::getEmailPrmp).orElse(null);
        notificationService.emettre(null, type, null, email, titre, corps);
    }

    private void notifierUgpm(String idUgpm, TypeNotification type, String titre, String corps) {
        String email = ugpmRepository.findById(idUgpm).map(Ugpm::getEmailUgpm).orElse(null);
        notificationService.emettre(null, type, null, email, titre, corps);
    }
}
