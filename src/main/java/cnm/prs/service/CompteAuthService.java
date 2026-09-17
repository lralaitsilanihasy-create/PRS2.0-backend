package cnm.prs.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CompteAuthResumeDto;
import cnm.prs.entity.CompteAuth;
import cnm.prs.enums.StatutCompte;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CompteAuthRepository;

/**
 * Gestion des comptes d'authentification par l'Administrateur (ouverture/fermeture d'un compte
 * déjà validé, réinitialisation des mots de passe).
 *
 * <p><strong>Ce service n'instruit pas les inscriptions</strong> : la validation et le refus d'une
 * inscription PRMP ou UGPM appartiennent à {@link InscriptionService} (écran « Demandes d'accès »,
 * {@code POST /api/inscriptions/{login}/valider|refuser}), qui seul rattache les entités, horodate
 * la décision, nomme le validateur et notifie le demandeur. Voir la garde d'{@link #activer}.</p>
 */
@Service
@Transactional
public class CompteAuthService {

    private final CompteAuthRepository repository;
    private final PasswordEncoder passwordEncoder;

    public CompteAuthService(CompteAuthRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Comptes en attente de validation (inactifs). */
    @Transactional(readOnly = true)
    public List<CompteAuthResumeDto> enAttente() {
        return repository.findByActif(false).stream().map(this::toDto).toList();
    }

    /**
     * Rouvre un compte <strong>déjà validé</strong> : le booléen {@code ACTIF} repasse à vrai et la
     * connexion redevient possible.
     *
     * <p>⚠️ <strong>Garde du 2026-09-17</strong> — refusée (409) sur une inscription
     * {@code EN_ATTENTE} ou {@code REFUSE}. Le geste n'ouvrait qu'{@code ACTIF} sans regarder
     * {@code STATUT} : un appel direct donnait l'accès à une inscription jamais acceptée, en
     * contournant l'instruction (entités rattachées, date de décision, validateur, notification)
     * que {@link InscriptionService#valider} est seul à faire. La règle vivait seulement dans
     * l'écran ({@code annuaire-admin.ts}, {@code gestesDeCompte}) et était déjà écrite dans
     * {@code docs/regles-gestion.md} (module 10) — c'est ce que referme cette garde.</p>
     *
     * <p><strong>Ce n'est pas la validation d'une inscription</strong> : celle-ci passe par
     * {@code POST /api/inscriptions/{login}/valider} et continue de fonctionner à l'identique.</p>
     *
     * <p>Le {@code STATUT} est reposé à {@link StatutCompte#ACTIF} : après la garde, il ne peut plus
     * valoir que {@code ACTIF} ou {@code null} (ligne antérieure à la colonne, vue
     * {@code SUSPENDU} par l'annuaire), et l'invariant annoncé par {@link StatutCompte}
     * ({@code ACTIF=true} ⟺ {@code STATUT=ACTIF}) est donc tenu de ce côté. {@link #desactiver}
     * continue, lui, de ne toucher que le booléen — c'est cet écart, volontaire, qui distingue un
     * compte <em>suspendu</em> d'une inscription <em>refusée</em> (cf. {@code StatutCompteAnnuaire}).</p>
     *
     * @throws BusinessRuleException inscription {@code EN_ATTENTE} ou {@code REFUSE} (409)
     */
    public CompteAuthResumeDto activer(String login) {
        CompteAuth compte = load(login);
        if (StatutCompte.EN_ATTENTE.name().equals(compte.getStatut())) {
            throw new BusinessRuleException("L'inscription « " + login + " » n'a pas encore été instruite :"
                    + " elle se valide dans « Demandes d'accès » (POST /api/inscriptions/" + login
                    + "/valider), qui rattache les entités et horodate la décision — pas en activant le compte.");
        }
        if (StatutCompte.REFUSE.name().equals(compte.getStatut())) {
            throw new BusinessRuleException("L'inscription « " + login + " » a été refusée :"
                    + " l'accès ne se rouvre pas en activant le compte, il demande une nouvelle décision.");
        }
        compte.setActif(true);
        compte.setStatut(StatutCompte.ACTIF.name());
        return toDto(repository.save(compte));
    }

    /**
     * Ferme un compte déjà validé : la connexion est bloquée ({@code ACTIF = false}),
     * <strong>{@code STATUT} reste inchangé</strong> — c'est ce qui distingue un compte suspendu
     * d'une inscription refusée (cf. {@code StatutCompteAnnuaire} et {@code AnnuaireService.statutDe}).
     *
     * <p>⚠️ <strong>Garde du 2026-09-17</strong> — refusée (409) sur une inscription
     * {@code EN_ATTENTE} ou {@code REFUSE}, par symétrie avec {@link #activer} : il n'y a là aucun
     * compte ouvert à fermer et le geste ne faisait rien (le booléen valait déjà faux) en rendant
     * 200, ce qui laissait croire à une suspension. Bloquer une inscription se dit
     * {@code POST /api/inscriptions/{login}/refuser}, avec son motif.</p>
     *
     * @throws BusinessRuleException inscription {@code EN_ATTENTE} ou {@code REFUSE} (409)
     */
    public CompteAuthResumeDto desactiver(String login) {
        CompteAuth compte = load(login);
        if (StatutCompte.EN_ATTENTE.name().equals(compte.getStatut())) {
            throw new BusinessRuleException("L'inscription « " + login + " » n'a pas encore été instruite :"
                    + " aucun compte n'est ouvert à suspendre — elle se refuse, avec motif, dans"
                    + " « Demandes d'accès » (POST /api/inscriptions/" + login + "/refuser).");
        }
        if (StatutCompte.REFUSE.name().equals(compte.getStatut())) {
            throw new BusinessRuleException("L'inscription « " + login + " » a été refusée :"
                    + " aucun compte n'a jamais été ouvert, il n'y a rien à suspendre.");
        }
        compte.setActif(false);
        return toDto(repository.save(compte));
    }

    /**
     * Réinitialise le mot de passe d'un compte (action Administrateur).
     *
     * <p>Volontairement <strong>sans garde de statut</strong>, à la différence d'{@link #activer} et
     * de {@link #desactiver} : un nouveau mot de passe n'ouvre aucun accès — le login ne consulte que
     * le booléen {@code ACTIF} — et le refuser sur une inscription en attente n'écarterait aucun
     * risque. L'écran, lui, ne propose pas le geste hors d'un compte ouvert.</p>
     */
    public CompteAuthResumeDto reinitialiserMotDePasse(String login, String nouveauMotDePasse) {
        CompteAuth compte = load(login);
        compte.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        return toDto(repository.save(compte));
    }

    private CompteAuth load(String login) {
        return repository.findByLogin(login)
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable : " + login));
    }

    private CompteAuthResumeDto toDto(CompteAuth c) {
        return new CompteAuthResumeDto(c.getLogin(), c.getTypeActeur(), c.getRefActeur(), c.getActif());
    }
}
