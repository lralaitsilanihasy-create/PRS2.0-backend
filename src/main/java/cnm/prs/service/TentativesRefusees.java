package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.OffreDto;
import cnm.prs.entity.Entreprise;
import cnm.prs.entity.Offre;
import cnm.prs.entity.OffreTentative;
import cnm.prs.repository.OffreTentativeRepository;

/**
 * ⚠️ R9b (manuel révisé pour l'application, lot 2, §B4 n° 4 ; art. 44 ; V112) — le registre des tentatives de dépôt refusées hors
 * délai. L'écriture a sa <strong>propre transaction</strong> ({@code REQUIRES_NEW}) : le refus qui suit annule le geste du candidat,
 * pas sa trace ; une erreur d'écriture est journalisée et n'empêche jamais le refus d'être rendu.
 */
@Service
public class TentativesRefusees {

    private static final Logger log = LoggerFactory.getLogger(TentativesRefusees.class);

    private final OffreTentativeRepository repository;

    public TentativesRefusees(OffreTentativeRepository repository) {
        this.repository = repository;
    }

    /**
     * Une création refusée : l'offre n'existe pas encore ; {@code entreprise} est celle du compte du candidat, lue par l'appelant dans sa
     * transaction (nulle s'il n'en a pas déclaré).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void creation(Long idDmc, String idCandidat, Entreprise entreprise, Integer lot, String enveloppe, LocalDateTime horodatage,
            LocalDateTime limite) {
        try {
            OffreTentative t = base(idDmc, idCandidat, OffreTentative.CREATION, horodatage, limite);
            t.setLot(lot);
            t.setEnveloppe(enveloppe);
            if (entreprise != null) {
                entreprise(t, entreprise);
            }
            repository.save(t);
        } catch (RuntimeException e) {
            log.warn("[TENTATIVE] procédure {} candidat {} : trace non écrite ({})", idDmc, idCandidat, e.getMessage());
        }
    }

    /** Un morceau ou un scellement refusé sur une offre en cours ; un morceau n'est tracé qu'une fois par offre. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void surOffre(Offre o, String geste, LocalDateTime horodatage, LocalDateTime limite) {
        try {
            if (OffreTentative.MORCEAU.equals(geste) && repository.existsByIdOffreAndGeste(o.getIdOffre(), geste)) {
                return;
            }
            OffreTentative t = base(o.getIdDmc(), o.getIdCandidat(), geste, horodatage, limite);
            t.setIdEntreprise(o.getIdEntreprise());
            t.setNif(o.getNif());
            t.setRaisonSociale(o.getRaisonSociale());
            t.setLot(o.getLot());
            t.setEnveloppe(o.getEnveloppe());
            t.setIdOffre(o.getIdOffre());
            repository.save(t);
        } catch (RuntimeException e) {
            log.warn("[TENTATIVE] offre {} : trace non écrite ({})", o.getIdOffre(), e.getMessage());
        }
    }

    /** Le registre d'une procédure, dans l'ordre des horodatages. */
    @Transactional(readOnly = true)
    public List<OffreDto.Tentative> lister(Long idDmc) {
        return repository.findByIdDmcOrderByHorodatageAscIdAsc(idDmc).stream()
                .map(t -> new OffreDto.Tentative(t.getHorodatage(), t.getGeste(), t.getMotif(), t.getRaisonSociale(), t.getNif(), t.getLot(),
                        t.getEnveloppe(), t.getIdOffre(), t.getDateLimite()))
                .toList();
    }

    private static OffreTentative base(Long idDmc, String idCandidat, String geste, LocalDateTime horodatage, LocalDateTime limite) {
        OffreTentative t = new OffreTentative();
        t.setIdDmc(idDmc);
        t.setIdCandidat(idCandidat);
        t.setGeste(geste);
        t.setMotif(OffreTentative.DELAI_DEPASSE);
        t.setHorodatage(horodatage);
        t.setDateLimite(limite);
        return t;
    }

    private static void entreprise(OffreTentative t, Entreprise e) {
        t.setIdEntreprise(e.getIdEntreprise());
        t.setNif(e.getNif());
        t.setRaisonSociale(e.getRaisonSociale());
    }
}
