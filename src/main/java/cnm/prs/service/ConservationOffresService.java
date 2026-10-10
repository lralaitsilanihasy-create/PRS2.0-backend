package cnm.prs.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ConservationOffresDto;
import cnm.prs.dto.ProcedureEnLigneDto;
import cnm.prs.entity.Offre;
import cnm.prs.entity.OffreJournal;
import cnm.prs.entity.Seance;
import cnm.prs.entity.SeanceJournal;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.OffreJournalRepository;
import cnm.prs.repository.OffreMorceauRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.SeanceJournalRepository;
import cnm.prs.repository.SeanceRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>La conservation des offres</strong> (arbitrages du pilote après le lot 4, §B4.2, Q3 ; V70). Le paramètre
 * {@code OFFRE_CONSERVATION_ANNEES} ({@code GET / PUT /api/parametres/candidats}) fixe la durée, comptée depuis la clôture de la
 * séance ({@code closeLe} : PV signé, ou constat d'illisibilité) ; nul = conservation sans limite. Au terme, la purge est un
 * <strong>geste de l'Administrateur</strong> (proposé, retenu par le pilote) : l'écran liste les procédures échues, il les purge une à
 * une. La purge supprime les conteneurs, les morceaux et les contenus déchiffrés de toutes les offres de la procédure (remplacées et
 * retirées comprises) ; la ligne de l'offre, son empreinte, sa lecture, les journaux et le PV restent. Chaque purge est journalisée
 * (journal des offres et de la séance).
 */
@Service
@Transactional
public class ConservationOffresService {

    static final String PURGE = "PURGE_CONSERVATION";

    private final ParametreService parametres;
    private final SeanceRepository seances;
    private final OffreRepository offres;
    private final OffreMorceauRepository morceaux;
    private final OffreJournalRepository journalOffres;
    private final SeanceJournalRepository journalSeance;
    private final StockageOffres stockage;
    private final ProceduresEnLigneService procedures;
    /** ⚠️ 2026-10-06 (retrait après paiement, H4) — les fichiers des reçus de frais de dossier suivent la même conservation. */
    private final cnm.prs.repository.RecuDaoRepository recus;
    private final Clock horloge;

    public ConservationOffresService(ParametreService parametres, SeanceRepository seances, OffreRepository offres,
            OffreMorceauRepository morceaux, OffreJournalRepository journalOffres, SeanceJournalRepository journalSeance,
            StockageOffres stockage, ProceduresEnLigneService procedures, Clock horloge, cnm.prs.repository.RecuDaoRepository recus) {
        this.recus = recus;
        this.parametres = parametres;
        this.seances = seances;
        this.offres = offres;
        this.morceaux = morceaux;
        this.journalOffres = journalOffres;
        this.journalSeance = journalSeance;
        this.stockage = stockage;
        this.procedures = procedures;
        this.horloge = horloge;
    }

    /** Les procédures dont la conservation est échue et qui ont encore des offres à purger (les plus anciennes d'abord). */
    @Transactional(readOnly = true)
    public ConservationOffresDto echues() {
        Integer annees = parametres.offreConservationAnnees();
        List<ConservationOffresDto.Echue> echues = new ArrayList<>();
        if (annees != null) {
            LocalDateTime maintenant = LocalDateTime.now(horloge);
            for (Seance s : seances.findAll()) {
                LocalDateTime echeance = echeance(s, annees);
                if (echeance == null || echeance.isAfter(maintenant)) {
                    continue;
                }
                long aPurger = aPurger(s.getIdDmc()).size();
                if (aPurger > 0) {
                    ProcedureEnLigneDto p = procedure(s.getIdDmc());
                    echues.add(new ConservationOffresDto.Echue(s.getIdDmc(), p == null ? null : p.reference(), p == null ? null : p.objet(),
                            s.getEtat(), s.getCloseLe(), echeance, aPurger));
                }
            }
            echues.sort(Comparator.comparing(ConservationOffresDto.Echue::echeance));
        }
        return new ConservationOffresDto(annees, echues);
    }

    /**
     * Purge les offres d'une procédure échue : 404 sans séance ; 409 {@code CONSERVATION_NON_FIXEE} (paramètre nul),
     * {@code CONSERVATION_EN_COURS} (séance non close ou échéance à venir, dans {@code details.echeance}).
     */
    public ConservationOffresDto.Purge purger(Long idDmc) {
        Integer annees = parametres.offreConservationAnnees();
        if (annees == null) {
            throw new BusinessRuleException("Aucune durée de conservation n'est fixée : les offres se conservent sans limite.",
                    "CONSERVATION_NON_FIXEE");
        }
        Seance s = seances.findById(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune séance d'ouverture pour la procédure " + idDmc + "."));
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        LocalDateTime echeance = echeance(s, annees);
        if (echeance == null || echeance.isAfter(maintenant)) {
            throw new BusinessRuleException(echeance == null ? "La séance n'est pas close : la conservation n'a pas commencé."
                    : "La conservation des offres court jusqu'au " + echeance.toLocalDate() + ".", "CONSERVATION_EN_COURS", null,
                    echeance == null ? Map.of() : Map.of("echeance", echeance.toString()));
        }
        String acteur = CurrentUser.ref().or(CurrentUser::login).orElse(null);
        List<Offre> cibles = aPurger(idDmc);
        for (Offre o : cibles) {
            stockage.purgerTout(o.getIdOffre());
            morceaux.deleteAll(morceaux.findByIdOffreOrderByRangAsc(o.getIdOffre()));
            o.setChemin(null);
            o.setPurgeeLe(maintenant);
            offres.save(o);
            journalOffres.save(new OffreJournal(null, o.getIdOffre(), idDmc, o.getIdCandidat(), maintenant, PURGE,
                    "conteneur et contenu supprimés au terme de " + annees + " an(s) de conservation"));
        }
        int recusPurges = 0;
        for (cnm.prs.entity.RecuDao r : recus.findByIdDmcOrderByDateDepotDescIdRecuDesc(idDmc)) {
            if (r.getPurgeLe() == null) {
                r.setContenu(null);
                r.setPurgeLe(maintenant);
                recus.save(r);
                recusPurges++;
            }
        }
        journalSeance.save(new SeanceJournal(null, idDmc, maintenant, acteur, PURGE,
                cibles.size() + " offre(s) et " + recusPurges + " reçu(s) de frais de dossier purgés au terme de " + annees
                        + " an(s) de conservation"));
        return new ConservationOffresDto.Purge(idDmc, cibles.size(), maintenant);
    }

    private static LocalDateTime echeance(Seance s, int annees) {
        boolean close = Seance.CLOSE.equals(s.getEtat()) || Seance.ILLISIBLE.equals(s.getEtat()) && (s.getSignataires() == null || s.getPvSigneLe() != null);
        return close && s.getCloseLe() != null ? s.getCloseLe().plusYears(annees) : null;
    }

    private List<Offre> aPurger(Long idDmc) {
        return offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc).stream().filter(o -> o.getPurgeeLe() == null).toList();
    }

    private ProcedureEnLigneDto procedure(Long idDmc) {
        // ⚠️ 2026-10-10 — sans exception : le 404 rattrapé marquait la transaction de l'appelant pour l'annulation (500).
        return procedures.vueSiPresente(idDmc).orElse(null);
    }
}
