package cnm.prs.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CompteCandidatDto;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.enums.StatutCompte;
import cnm.prs.enums.TypeActeur;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.exception.TropDeRequetesException;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.security.LoginRateLimiter;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1a, §B2 et §B7) — les <strong>comptes candidats</strong> :
 * inscription publique, confirmation par codes, contrôle à la connexion, ménage.
 *
 * <ul>
 *   <li>Le compte de connexion est un {@link CompteAuth} ordinaire : login = adresse électronique en minuscules, type
 *       {@code CANDIDAT}, {@code REF_ACTEUR} = l'identifiant court du candidat ({@code C} + 9 chiffres), mot de passe
 *       BCrypt sous la politique des comptes internes. Il n'est actif qu'une fois confirmé.</li>
 *   <li>La confirmation exige le code reçu par courriel et, si {@code CANDIDAT_CONFIRMATION_TELEPHONE} vaut {@code OUI},
 *       le code reçu par SMS ({@link PasserelleSms} : aucune passerelle raccordée pour l'instant).</li>
 *   <li>Un compte archivé se réactive par un nouveau code envoyé par courriel à la connexion.</li>
 * </ul>
 */
@Service
@Transactional
public class CandidatService {

    private final CompteCandidatRepository candidats;
    private final CompteAuthRepository comptes;
    private final CodesCandidat codes;
    private final ParametreService parametres;
    private final LoginRateLimiter limiteur;
    private final PasswordEncoder encodeur;
    private final Clock horloge;

    public CandidatService(CompteCandidatRepository candidats, CompteAuthRepository comptes, CodesCandidat codes,
            ParametreService parametres, LoginRateLimiter limiteur, PasswordEncoder encodeur, Clock horloge) {
        this.candidats = candidats;
        this.comptes = comptes;
        this.codes = codes;
        this.parametres = parametres;
        this.limiteur = limiteur;
        this.encodeur = encodeur;
        this.horloge = horloge;
    }

    /** L'adresse électronique telle qu'elle est stockée et cherchée : sans blancs de bord, en minuscules. */
    public static String normaliserEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * {@code POST /api/candidats/inscription} — quota par adresse IP ({@code CANDIDAT_INSCRIPTIONS_PAR_JOUR}, 429), puis
     * 409 {@code EMAIL_EXISTANT} si l'adresse est déjà celle d'un compte (candidat ou interne). Les codes partent aussitôt.
     */
    public CompteCandidatDto.Inscrit inscrire(CompteCandidatDto.Inscription demande, String ip) {
        ParametreService.ParametresCandidats p = parametres.candidats();
        limiteur.consommerInscriptionCandidat(ip, p.inscriptionsParJour());
        String email = normaliserEmail(demande.email());
        if (candidats.existsByEmail(email) || comptes.findByLogin(email).isPresent()) {
            throw new BusinessRuleException("Cette adresse électronique est déjà celle d'un compte. Connectez-vous, ou "
                    + "demandez un nouveau code si votre inscription n'est pas confirmée.", "EMAIL_EXISTANT");
        }
        String id = String.format("C%09d", candidats.prochainNumero());
        CompteCandidat compte = candidats.save(new CompteCandidat(id, email, demande.telephone().trim(), demande.nom().trim(),
                demande.prenom().trim(), CompteCandidat.A_CONFIRMER, false, LocalDateTime.now(horloge), null, null, null));
        comptes.save(new CompteAuth(email, encodeur.encode(demande.motDePasse()), TypeActeur.CANDIDAT.name(), id, false));
        codes.emettre(compte, Boolean.TRUE.equals(p.confirmationTelephone()));
        return new CompteCandidatDto.Inscrit(id, compte.getEtat());
    }

    /**
     * {@code POST /api/candidats/confirmation} — 404 adresse inconnue ; 400 {@code CODE_INVALIDE} / {@code CODE_EXPIRE} ;
     * 429 après {@value CodesCandidat#ESSAIS_MAX} essais manqués. Confirme une inscription, ou réactive un compte
     * archivé (courriel seul). Un compte déjà confirmé répond {@code CONFIRME}.
     */
    @Transactional(noRollbackFor = { BadRequestException.class, TropDeRequetesException.class })
    public CompteCandidatDto.Etat confirmer(CompteCandidatDto.Confirmation demande) {
        CompteCandidat compte = candidats.findByEmail(normaliserEmail(demande.email()))
                .orElseThrow(() -> new ResourceNotFoundException("Aucun compte candidat pour cette adresse électronique."));
        if (CompteCandidat.CONFIRME.equals(compte.getEtat())) {
            return new CompteCandidatDto.Etat(compte.getEtat());
        }
        exiger(codes.verifier(compte.getIdCandidat(), cnm.prs.entity.CodeCandidat.EMAIL, demande.codeEmail()), "courriel");
        boolean telephone = CompteCandidat.A_CONFIRMER.equals(compte.getEtat())
                && Boolean.TRUE.equals(parametres.candidats().confirmationTelephone());
        if (telephone) {
            exiger(codes.verifier(compte.getIdCandidat(), cnm.prs.entity.CodeCandidat.TELEPHONE, demande.codeTelephone()), "SMS");
            compte.setTelephoneConfirme(true);
        }
        codes.consommer(compte.getIdCandidat());
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        if (compte.getDateConfirmation() == null) {
            compte.setDateConfirmation(maintenant);
        }
        compte.setEtat(CompteCandidat.CONFIRME);
        compte.setDateArchivage(null);
        candidats.save(compte);
        comptes.findByLogin(compte.getEmail()).ifPresent(a -> {
            a.setActif(true);
            a.setStatut(StatutCompte.ACTIF.name());
            comptes.save(a);
        });
        return new CompteCandidatDto.Etat(compte.getEtat());
    }

    /**
     * {@code POST /api/candidats/codes} — renvoi des codes ({@value LoginRateLimiter#RENVOIS_MAX} par heure et par
     * adresse, 429). Toujours 204 : une adresse inconnue ou un compte déjà confirmé ne reçoit rien, sans le dire.
     */
    public void renvoyerCodes(CompteCandidatDto.RenvoiCodes demande) {
        String email = normaliserEmail(demande.email());
        limiteur.consommerRenvoiCodes(email);
        candidats.findByEmail(email).filter(c -> !CompteCandidat.CONFIRME.equals(c.getEtat())).ifPresent(c -> codes.emettre(c,
                CompteCandidat.A_CONFIRMER.equals(c.getEtat()) && Boolean.TRUE.equals(parametres.candidats().confirmationTelephone())));
    }

    /**
     * À la connexion d'un compte {@code CANDIDAT}, une fois le mot de passe vérifié : 409 {@code COMPTE_A_CONFIRMER} ;
     * 409 {@code COMPTE_ARCHIVE}, après l'envoi d'un nouveau code par courriel ; compte désactivé : identifiants refusés.
     * Sinon, la date de dernière connexion est posée, et le nom à afficher rendu. La transaction de la connexion
     * ({@code AuthService.login}) est gardée malgré le 409, pour que le code émis survive.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public String controlerConnexion(CompteAuth compte) {
        CompteCandidat candidat = candidats.findById(compte.getRefActeur())
                .orElseThrow(() -> new BadCredentialsException("Candidat introuvable pour ce compte."));
        if (CompteCandidat.A_CONFIRMER.equals(candidat.getEtat())) {
            throw new BusinessRuleException("Votre inscription n'est pas confirmée : saisissez le code reçu par courriel, ou "
                    + "demandez-en un nouveau.", "COMPTE_A_CONFIRMER");
        }
        if (CompteCandidat.ARCHIVE.equals(candidat.getEtat())) {
            codes.emettre(candidat, false);
            throw new BusinessRuleException("Votre compte a été archivé faute de connexion. Un code vient de vous être envoyé "
                    + "par courriel : saisissez-le pour le réactiver.", "COMPTE_ARCHIVE");
        }
        if (!Boolean.TRUE.equals(compte.getActif())) {
            throw new BadCredentialsException("Compte désactivé.");
        }
        candidat.setDerniereConnexion(LocalDateTime.now(horloge));
        candidats.save(candidat);
        return candidat.getNom().toUpperCase(Locale.ROOT) + " " + candidat.getPrenom();
    }

    /**
     * ⚠️ §B7 — le ménage : supprime les comptes jamais confirmés au-delà de {@code CANDIDAT_DELAI_CONFIRMATION_JOURS},
     * archive les comptes sans connexion depuis {@code CANDIDAT_DELAI_INACTIVITE_MOIS} (jamais supprimés). Rend
     * {@code [supprimés, archivés]}.
     */
    public int[] menage() {
        ParametreService.ParametresCandidats p = parametres.candidats();
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        int supprimes = 0;
        for (CompteCandidat c : candidats.findByEtatAndDateInscriptionBefore(CompteCandidat.A_CONFIRMER,
                maintenant.minusDays(p.delaiConfirmationJours()))) {
            comptes.findByLogin(c.getEmail()).filter(a -> TypeActeur.CANDIDAT.name().equals(a.getTypeActeur()))
                    .ifPresent(comptes::delete);
            candidats.delete(c);
            supprimes++;
        }
        int archives = 0;
        for (CompteCandidat c : candidats.inactifsDepuis(maintenant.minusMonths(p.delaiInactiviteMois()))) {
            c.setEtat(CompteCandidat.ARCHIVE);
            c.setDateArchivage(maintenant);
            candidats.save(c);
            archives++;
        }
        return new int[] { supprimes, archives };
    }

    private static void exiger(CodesCandidat.Verdict verdict, String canal) {
        switch (verdict) {
            case BON -> {
                return;
            }
            case FAUX -> throw new BadRequestException("Le code reçu par " + canal + " est faux.", "CODE_INVALIDE");
            case EXPIRE -> throw new BadRequestException("Le code reçu par " + canal + " a expiré : demandez-en un nouveau.",
                    "CODE_EXPIRE");
            case EPUISE -> throw new TropDeRequetesException("Trop d'essais pour le code reçu par " + canal
                    + " : demandez-en un nouveau.", 60);
            default -> throw new IllegalStateException(String.valueOf(verdict));
        }
    }
}
