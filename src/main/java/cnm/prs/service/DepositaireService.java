package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CaoDto;
import cnm.prs.dto.DepositaireDto;
import cnm.prs.dto.ProcedureEnLigneDto;
import cnm.prs.entity.CeremonieCles;
import cnm.prs.entity.CleDetenteur;
import cnm.prs.entity.CompteDepositaire;
import cnm.prs.entity.ParametreInterneProcedure;
import cnm.prs.entity.Seance;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeNotification;
import cnm.prs.repository.CeremonieClesRepository;
import cnm.prs.repository.CleDetenteurRepository;
import cnm.prs.repository.ParametreInterneProcedureRepository;
import cnm.prs.repository.SeanceRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>L'espace du dépositaire</strong> (demande front du 2026-10-05, « le dépositaire génère lui-même la part de secours »,
 * §B1, §B2 ; V71) : l'activation de son compte (publique) — après laquelle chaque procédure dont la clé de secours reste à publier
 * lui envoie {@code CLE_A_PUBLIER} —, et ses procédures. Il ne voit ni les offres, ni leurs pièces, ni le PV : sa part, l'état de la
 * cérémonie et celui de la séance.
 */
@Service
@Transactional
public class DepositaireService {

    private final CompteDepositaireService comptes;
    private final ParametreInterneProcedureRepository internesRepository;
    private final ParametresInternesService internes;
    private final CleDetenteurRepository cles;
    private final CeremonieClesRepository ceremonies;
    private final SeanceRepository seances;
    private final ProceduresEnLigneService procedures;

    public DepositaireService(CompteDepositaireService comptes, ParametreInterneProcedureRepository internesRepository,
            ParametresInternesService internes, CleDetenteurRepository cles, CeremonieClesRepository ceremonies, SeanceRepository seances,
            ProceduresEnLigneService procedures) {
        this.comptes = comptes;
        this.internesRepository = internesRepository;
        this.internes = internes;
        this.cles = cles;
        this.ceremonies = ceremonies;
        this.seances = seances;
        this.procedures = procedures;
    }

    /** {@code POST /api/depositaire/activation} (public) ; à la première activation, {@code CLE_A_PUBLIER} pour chaque clé à publier. */
    public CaoDto.EtatCompte activer(CaoDto.Activation demande) {
        boolean dejaActif = comptes.parEmail(demande.email()).map(c -> CompteDepositaire.ACTIF.equals(c.getEtat())).orElse(false);
        CompteDepositaire compte = comptes.activer(demande);
        if (!dejaActif) {
            for (ParametreInterneProcedure p : internesRepository.findByIdCompteDepositaireOrderByIdDmcAsc(compte.getIdCompte())) {
                if (!saCle(p.getIdDmc(), compte.getIdCompte())) {
                    internes.notifierDepositaire(p.getIdDmc(), TypeNotification.CLE_A_PUBLIER, "Part de secours : votre clé est à publier",
                            "Votre compte est activé. Générez votre clé de secours pour la procédure " + p.getIdDmc() + " sur votre poste "
                                    + "et publiez-la : vous seul verrez votre phrase secrète.");
                }
            }
        }
        return new CaoDto.EtatCompte(compte.getEtat());
    }

    /** {@code GET /api/depositaire/procedures} : les procédures dont l'appelant est le dépositaire désigné. */
    @Transactional(readOnly = true)
    public List<DepositaireDto.Procedure> mesProcedures() {
        String moi = CurrentUser.ref().orElse(null);
        if (moi == null || !TypeActeur.DEPOSITAIRE.name().equals(CurrentUser.acteurType().orElse(null))) {
            throw new AccessDeniedException("Espace réservé aux dépositaires de parts de secours.");
        }
        List<DepositaireDto.Procedure> out = new ArrayList<>();
        for (ParametreInterneProcedure p : internesRepository.findByIdCompteDepositaireOrderByIdDmcAsc(moi)) {
            Long idDmc = p.getIdDmc();
            ProcedureEnLigneDto vue = vue(idDmc);
            CleDetenteur active = cles.findFirstByIdDmcAndRoleAndDateArchivageIsNull(idDmc, CleDetenteur.SECOURS).orElse(null);
            boolean sienne = active != null && CleDetenteur.PAR_DEPOSITAIRE.equals(active.getGenerePar()) && moi.equals(active.getIdDepositaire());
            String generePar = active == null ? null : active.getGenerePar() == null ? CleDetenteur.PAR_RESPONSABLE : active.getGenerePar();
            Seance s = seances.findById(idDmc).orElse(null);
            out.add(new DepositaireDto.Procedure(idDmc, vue == null ? null : vue.reference(), vue == null ? null : vue.objet(),
                    ceremonies.findById(idDmc).map(CeremonieCles::getEtat).orElse(CeremonieCles.A_VENIR),
                    sienne ? active.getEtatPart() : CeremonieService.ABSENTE, generePar, active != null && !sienne,
                    s == null ? Seance.A_VENIR : s.getEtat(),
                    s == null || s.getSecoursDemandeLe() == null ? null
                            : new DepositaireDto.SecoursDemande(s.getSecoursDemandeMotif(), s.getSecoursDemandeLe())));
        }
        return out;
    }

    private boolean saCle(Long idDmc, String compte) {
        return cles.findFirstByIdDmcAndRoleAndDateArchivageIsNull(idDmc, CleDetenteur.SECOURS)
                .filter(c -> CleDetenteur.PAR_DEPOSITAIRE.equals(c.getGenerePar()) && compte.equals(c.getIdDepositaire())).isPresent();
    }

    private ProcedureEnLigneDto vue(Long idDmc) {
        try {
            return procedures.vue(idDmc);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
