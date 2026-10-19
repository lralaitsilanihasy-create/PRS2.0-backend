package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CopieOffreDto;
import cnm.prs.entity.CopieOffre;
import cnm.prs.entity.Offre;
import cnm.prs.entity.OffreJournal;
import cnm.prs.entity.Seance;
import cnm.prs.entity.SeanceFinanciere;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.PayloadTropVolumineuxException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CopieOffreRepository;
import cnm.prs.repository.OffreJournalRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.SeanceFinanciereRepository;
import cnm.prs.repository.SeanceRepository;

/**
 * ⚠️ V136 (demande front du 2026-10-19, « copie-offre-candidat », §B1 ; arbitrage du pilote du 19/10) — la <strong>copie de son
 * offre</strong> que le candidat chiffre sur son poste avec sa phrase de relecture, gardée par le serveur pour lui seul, relisible
 * depuis n'importe quel poste. Le serveur ne la déchiffre jamais et n'en journalise que le geste (« déposée », « lue »,
 * « effacée »), jamais la phrase ni le contenu.
 *
 * <ul>
 *   <li><strong>Dépôt</strong> : l'offre est scellée (pas {@code EN_COURS}), pas retirée ; l'enveloppe passe le contrôle commun
 *       ({@link EnveloppeChiffree}) ; le chiffré tient dans {@link #TAILLE_MAX_OCTETS}. Un nouveau dépôt remplace le précédent.</li>
 *   <li><strong>Effacement</strong> : à la clôture de la séance qui ouvre l'offre — la séance d'ouverture (PV signé), ou, pour
 *       l'enveloppe financière d'une proposition de prestations intellectuelles, la séance financière. L'offre est alors connue de
 *       la CAO ; le candidat la relit telle que lue ({@code GET …/lecture}). <strong>Un constat d'illisibilité n'efface rien</strong> :
 *       la commission n'a rien lu, la copie reste la seule trace lisible de la saisie.</li>
 *   <li>Une offre remplacée ou retirée garde sa copie jusqu'à la clôture ; la purge de conservation supprime la ligne.</li>
 * </ul>
 */
@Service
@Transactional
public class CopieOffreService {

    /** 1 Mo de chiffré : le récapitulatif pèse quelques dizaines de Ko. */
    public static final int TAILLE_MAX_OCTETS = 1024 * 1024;
    public static final String COPIE_EFFACEE = "COPIE_EFFACEE";

    private final CopieOffreRepository copies;
    private final OffreRepository offres;
    private final OffreJournalRepository journal;
    private final SeanceRepository seances;
    private final SeanceFinanciereRepository seancesFinancieres;

    public CopieOffreService(CopieOffreRepository copies, OffreRepository offres, OffreJournalRepository journal, SeanceRepository seances,
            SeanceFinanciereRepository seancesFinancieres) {
        this.copies = copies;
        this.offres = offres;
        this.journal = journal;
        this.seances = seances;
        this.seancesFinancieres = seancesFinancieres;
    }

    /** {@code PUT …/copie} : 400 {@code ENVELOPPE_INVALIDE}, 413, 409 {@code OFFRE_NON_SCELLEE} / {@code OFFRE_RETIREE} / {@code SEANCE_CLOSE}. */
    public void deposer(String idCandidat, String idOffre, CopieOffreDto.Corps corps) {
        Offre o = sienne(idCandidat, idOffre);
        if (corps == null) {
            throw new BadRequestException("L'enveloppe de la copie est attendue.", EnveloppeChiffree.CODE_INVALIDE);
        }
        byte[] chiffre = EnveloppeChiffree.controler(corps.enveloppe());
        if (corps.version() == null || corps.version() < 1) {
            throw new BadRequestException("La version du récapitulatif (entier ≥ 1) est attendue.", EnveloppeChiffree.CODE_INVALIDE);
        }
        if (chiffre.length > TAILLE_MAX_OCTETS) {
            throw new PayloadTropVolumineuxException("La copie dépasse 1 Mo (" + chiffre.length + " octets) : le récapitulatif ne porte "
                    + "pas les fichiers eux-mêmes.");
        }
        if (Offre.EN_COURS.equals(o.getEtat()) || o.getDateDepot() == null) {
            throw new BusinessRuleException("L'offre n'est pas encore scellée : la copie se dépose après le scellement.", "OFFRE_NON_SCELLEE");
        }
        if (Offre.RETIREE.equals(o.getEtat())) {
            throw new BusinessRuleException("L'offre est retirée : sa copie ne se dépose plus.", "OFFRE_RETIREE");
        }
        if (seanceClose(o).isPresent()) {
            throw new BusinessRuleException("La séance qui ouvre cette offre est close : l'offre se relit telle que lue par la commission.",
                    "SEANCE_CLOSE");
        }
        CopieOffre c = copies.findById(idOffre).orElseGet(CopieOffre::new);
        boolean remplacement = c.getIdOffre() != null;
        c.setIdOffre(idOffre);
        c.setEnvChiffre(corps.chiffre().trim());
        c.setEnvIv(corps.iv().trim());
        c.setEnvSel(corps.sel().trim());
        c.setEnvIterations(corps.iterations());
        c.setEnvKdf(EnveloppeChiffree.KDF);
        c.setEnvAlgorithme(EnveloppeChiffree.ALGORITHME);
        c.setVersion(corps.version());
        c.setEnregistreeLe(LocalDateTime.now());
        c.setEffaceeLe(null);
        copies.save(c);
        tracer(o, "COPIE_DEPOSEE", (remplacement ? "copie remplacée" : "copie déposée") + ", " + chiffre.length + " octets chiffrés");
    }

    /** {@code GET …/copie} : 404 sans copie ; 404 {@code COPIE_EFFACEE} après la séance. */
    public CopieOffreDto lire(String idCandidat, String idOffre) {
        Offre o = sienne(idCandidat, idOffre);
        CopieOffre c = copies.findById(idOffre)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune copie de cette offre n'est enregistrée.", "COPIE_ABSENTE"));
        if (c.getEffaceeLe() != null) {
            throw new ResourceNotFoundException("La copie a été effacée le " + c.getEffaceeLe().toLocalDate() + ", à la clôture de la séance "
                    + "d'ouverture : l'offre se relit telle que lue par la commission.", COPIE_EFFACEE);
        }
        tracer(o, "COPIE_LUE", "copie rendue au candidat");
        return new CopieOffreDto(c.getEnvChiffre(), c.getEnvIv(), c.getEnvSel(), c.getEnvIterations(), c.getEnvKdf(), c.getEnvAlgorithme(),
                c.getVersion(), c.getEnregistreeLe());
    }

    /** L'état des copies des offres données, pour la liste (§B2). */
    @Transactional(readOnly = true)
    public Map<String, CopieOffre> etats(Collection<String> idOffres) {
        Map<String, CopieOffre> etats = new HashMap<>();
        if (!idOffres.isEmpty()) {
            copies.findByIdOffreIn(idOffres).forEach(c -> etats.put(c.getIdOffre(), c));
        }
        return etats;
    }

    /**
     * La séance qui ouvre les offres de la procédure est close : leurs copies sont effacées. {@code financieres} : les enveloppes
     * financières des prestations intellectuelles (séance financière) ; sinon toutes les autres (séance d'ouverture).
     */
    public void effacerALaCloture(Long idDmc, boolean financieres) {
        LocalDateTime maintenant = LocalDateTime.now();
        Map<String, Offre> parId = new HashMap<>();
        offres.findByIdDmcOrderByNumeroAscDateCreationAsc(idDmc).stream()
                .filter(o -> financieres == Offre.FINANCIERE.equals(o.getEnveloppe())).forEach(o -> parId.put(o.getIdOffre(), o));
        if (parId.isEmpty()) {
            return;
        }
        for (CopieOffre c : copies.lisibles(parId.keySet())) {
            c.effacer(maintenant);
            copies.save(c);
            tracer(parId.get(c.getIdOffre()), "COPIE_EFFACEE", financieres ? "clôture de la séance financière" : "clôture de la séance d'ouverture");
        }
    }

    /** La purge de conservation : les copies des offres purgées disparaissent avec leurs contenus. */
    public void purger(Collection<String> idOffres) {
        if (!idOffres.isEmpty()) {
            copies.deleteAll(copies.findByIdOffreIn(idOffres));
        }
    }

    /** La date de clôture de la séance qui ouvre l'offre, si elle est close (un constat d'illisibilité ne compte pas). */
    private Optional<LocalDateTime> seanceClose(Offre o) {
        if (Offre.FINANCIERE.equals(o.getEnveloppe())) {
            return seancesFinancieres.findByIdDmcOrderByRondeAsc(o.getIdDmc()).stream()
                    .filter(s -> SeanceFinanciere.CLOSE.equals(s.getEtat()) && s.getCloseLe() != null).map(SeanceFinanciere::getCloseLe)
                    .findFirst();
        }
        return seances.findById(o.getIdDmc()).filter(s -> Seance.CLOSE.equals(s.getEtat())).map(s -> s.getCloseLe() == null
                ? LocalDateTime.now() : s.getCloseLe());
    }

    private Offre sienne(String idCandidat, String idOffre) {
        Offre o = offres.findById(idOffre).orElseThrow(() -> new ResourceNotFoundException("Offre introuvable : " + idOffre + "."));
        if (!o.getIdCandidat().equals(idCandidat)) {
            throw new AccessDeniedException("Cette offre n'est pas la vôtre.");
        }
        return o;
    }

    private void tracer(Offre o, String action, String detail) {
        journal.save(new OffreJournal(null, o.getIdOffre(), o.getIdDmc(), o.getIdCandidat(), LocalDateTime.now(), action, detail));
    }
}
