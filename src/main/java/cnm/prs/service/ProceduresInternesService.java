package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ProcedureEnLigneDto;
import cnm.prs.dto.ProcedureInterneDto;
import cnm.prs.dto.ResponsableProcedureDto;
import cnm.prs.entity.CeremonieCles;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Offre;
import cnm.prs.entity.Seance;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutFicheMarche;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.SeanceRepository;
import cnm.prs.security.CurrentUser;
import cnm.prs.security.Suppleance;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠️ <strong>La liste des procédures en ligne</strong>, pour qui les conduit (demande front du 2026-10-06, §B1) : l'Administrateur
 * voit toutes les fiches dont la dernière version porte {@code modeRemise = ELECTRONIQUE}, brouillons compris (il désigne le
 * responsable avant la validation) ; tout autre profil interne, celles dont il est le responsable titulaire, ou qu'il exerce par
 * intérim (ADR-0008), signalées. Un compte sans procédure reçoit une liste vide (le front y lit s'il affiche l'entrée de menu).
 * Une procédure close depuis plus que la durée de conservation des offres (V70) en sort (H1).
 */
@Service
@Transactional(readOnly = true)
public class ProceduresInternesService {

    private static final Set<String> SEANCE_EN_COURS = Set.of(Seance.OUVERTE, Seance.DECHIFFREE, Seance.PV_A_SIGNER);

    private final FicheMarcheRepository ficheRepository;
    private final FicheMarcheService fiches;
    private final ProceduresEnLigneService procedures;
    private final ParametresInternesService internes;
    private final CeremonieService ceremonies;
    private final SeanceRepository seances;
    private final OffreRepository offres;
    private final InterimService interims;
    private final ParametreService parametres;
    private final ObjectMapper mapper;

    public ProceduresInternesService(FicheMarcheRepository ficheRepository, FicheMarcheService fiches, ProceduresEnLigneService procedures,
            ParametresInternesService internes, CeremonieService ceremonies, SeanceRepository seances, OffreRepository offres,
            InterimService interims, ParametreService parametres, ObjectMapper mapper) {
        this.ficheRepository = ficheRepository;
        this.fiches = fiches;
        this.procedures = procedures;
        this.internes = internes;
        this.ceremonies = ceremonies;
        this.seances = seances;
        this.offres = offres;
        this.interims = interims;
        this.parametres = parametres;
        this.mapper = mapper;
    }

    /** {@code GET /api/fiches-marche/en-ligne} : à traiter d'abord, puis par date limite croissante ; 403 pour un compte externe. */
    public List<ProcedureInterneDto> lister() {
        String moi = CurrentUser.ref().orElse(null);
        ProfilUtilisateur profil = CurrentUser.profil().orElse(null);
        String type = CurrentUser.acteurType().orElse(null);
        if (moi == null || profil == null || TypeActeur.CANDIDAT.name().equals(type) || TypeActeur.MEMBRE_CAO.name().equals(type)
                || TypeActeur.DEPOSITAIRE.name().equals(type)) {
            throw new AccessDeniedException("La liste des procédures en ligne est réservée aux comptes internes.");
        }
        boolean admin = profil == ProfilUtilisateur.ADMINISTRATEUR;
        Set<String> titulaires = admin ? Set.of() : interims.suppleancesActivesDe(moi, profil).stream().map(Suppleance::imTitulaire)
                .collect(Collectors.toSet());
        Map<Long, List<FicheMarche>> parDmc = ficheRepository.findAll().stream().collect(Collectors.groupingBy(FicheMarche::getIdDmc));
        Integer conservation = parametres.offreConservationAnnees();
        LocalDateTime maintenant = LocalDateTime.now();
        List<ProcedureInterneDto> out = new ArrayList<>();
        for (FicheMarche derniere : ficheRepository.findDernieresVersions()) {
            if (!RemiseElectronique.electronique(cadrage(derniere))) {
                continue;
            }
            Long idDmc = derniere.getIdDmc();
            ResponsableProcedureDto responsable = internes.responsableDto(idDmc);
            boolean sienne = responsable != null && PredicatsIdentite.estResponsableProcedure(moi, responsable.im());
            boolean parInterim = !sienne && responsable != null && titulaires.stream()
                    .anyMatch(t -> PredicatsIdentite.estResponsableProcedure(t, responsable.im()));
            if (!admin && !sienne && !parInterim) {
                continue;
            }
            Seance s = seances.findById(idDmc).orElse(null);
            boolean close = s != null && (Seance.CLOSE.equals(s.getEtat()) || Seance.ILLISIBLE.equals(s.getEtat()));
            if (close && conservation != null && s.getCloseLe() != null && s.getCloseLe().plusYears(conservation).isBefore(maintenant)) {
                continue;   // H1 : au-delà de la conservation, la procédure sort de la liste
            }
            out.add(ligne(derniere, parDmc.getOrDefault(idDmc, List.of()), responsable, parInterim, s, maintenant));
        }
        out.sort(Comparator.comparing((ProcedureInterneDto p) -> !p.aTraiter())
                .thenComparing(p -> Seance.CLOSE.equals(p.etatSeance()) || Seance.ILLISIBLE.equals(p.etatSeance()))
                .thenComparing(ProcedureInterneDto::dateLimite, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProcedureInterneDto::idDmc));
        return out;
    }

    private ProcedureInterneDto ligne(FicheMarche derniere, List<FicheMarche> versions, ResponsableProcedureDto responsable,
            boolean parInterim, Seance s, LocalDateTime maintenant) {
        Long idDmc = derniere.getIdDmc();
        Optional<ProceduresEnLigneService.Lue> lancee = procedures.trouver(idDmc);
        ProcedureEnLigneDto dto = lancee.map(ProceduresEnLigneService.Lue::dto).orElseGet(() -> vue(idDmc));
        String statut = derniere.getStatut();
        if (StatutFicheMarche.BROUILLON.name().equals(statut)
                && versions.stream().anyMatch(v -> StatutFicheMarche.VALIDEE.name().equals(v.getStatut()))) {
            statut = "REVISION";
        }
        LocalDateTime heure = heureOuverture(idDmc);
        String etatSeance = s != null ? s.getEtat() : heure != null && !maintenant.isBefore(heure) ? Seance.A_VENIR : null;
        String etatCeremonie = ceremonies.etatPourFiche(idDmc, true);
        boolean seanceDuJour = heure != null && heure.toLocalDate().equals(LocalDate.now()) && (s == null || SEANCE_EN_COURS.contains(s.getEtat()));
        boolean aTraiter = responsable == null || !CeremonieCles.CLOSE.equals(etatCeremonie)
                || s != null && SEANCE_EN_COURS.contains(s.getEtat()) || seanceDuJour;
        long nbOffres = offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc).stream()
                .filter(o -> Offre.DEPOSEE.equals(o.getEtat()) || Offre.ECARTEE.equals(o.getEtat()))
                .filter(o -> !Offre.FINANCIERE.equals(o.getEnveloppe())).count();   // ⚠️ V86 (PI-b) — une proposition PI compte une fois
        return new ProcedureInterneDto(idDmc, dto == null ? derniere.getIdDmc().toString() : dto.reference(), dto == null ? null : dto.objet(),
                dto == null ? null : dto.autoriteContractante(), dto == null ? null : dto.categorie(), statut, responsable, parInterim,
                internes.etatCao(idDmc, true), etatCeremonie, lancee.isPresent() ? dto.datePublication() : null,
                dto == null ? null : dto.dateOuvertureDepots(), dto == null ? null : dto.dateLimite(), RemiseElectronique.isoMinute(heure),
                lancee.isPresent() && dto.etat() != null ? dto.etat() : "NON_LANCEE", etatSeance, nbOffres, aTraiter);
    }

    private ProcedureEnLigneDto vue(Long idDmc) {
        // ⚠️ 2026-10-10 — sans exception : le 404 rattrapé marquait la transaction de l'appelant pour l'annulation (500).
        return procedures.vueSiPresente(idDmc).orElse(null);
    }

    /** L'heure d'ouverture des plis ({@code B04-OP-02} + {@code B04-OP-03}) de la dernière version ; {@code null} sans elle. */
    private LocalDateTime heureOuverture(Long idDmc) {
        try {
            return fiches.etatCourant(idDmc).map(e -> {
                Map<String, String> v = e.etat().getValeurs();
                return v == null ? null : RemiseElectronique.echeance(v.get(RemiseElectronique.DATE_OUVERTURE_PLIS),
                        v.get(RemiseElectronique.HEURE_OUVERTURE_PLIS));
            }).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Map<String, Object> cadrage(FicheMarche f) {
        if (f.getCadrage() == null || f.getCadrage().isBlank() || !f.getCadrage().contains(RemiseElectronique.ELECTRONIQUE)) {
            return Map.of();
        }
        try {
            return mapper.readValue(f.getCadrage(), new TypeReference<Map<String, Object>>() {
            });
        } catch (RuntimeException e) {
            return Map.of();
        }
    }
}
