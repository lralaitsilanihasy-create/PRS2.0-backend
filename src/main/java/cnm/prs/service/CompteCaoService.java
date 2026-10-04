package cnm.prs.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CaoDto;
import cnm.prs.entity.CodeCandidat;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.CompteCao;
import cnm.prs.enums.StatutCompte;
import cnm.prs.enums.TypeActeur;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.exception.TropDeRequetesException;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.CompteCaoRepository;

/**
 * ⚠️ V67 (demande front du 2026-10-04, soumission en ligne, lot 2a, §B2) — les <strong>comptes des membres de CAO</strong>
 * (profil {@code MEMBRE_CAO}) : créés à la désignation ({@code A_ACTIVER}, login = l'adresse en minuscules, identifiant court
 * {@code K} + 9 chiffres), invités par courriel (code à six chiffres, 72 heures, {@link CodesCandidat}), activés en public
 * ({@code POST /api/cao/activation}) avec le mot de passe choisi. Une personne garde son compte d'une CAO à l'autre ; pas de
 * ménage automatique. Lu par {@code AuthService} à la connexion.
 */
@Service
@Transactional
public class CompteCaoService {

    static final int HEURES_INVITATION = 72;
    private static final SecureRandom HASARD = new SecureRandom();

    private final CompteCaoRepository comptes;
    private final CompteAuthRepository auth;
    private final CodesCandidat codes;
    private final PasswordEncoder encodeur;
    private final Clock horloge;
    private final String lienActivation;

    public CompteCaoService(CompteCaoRepository comptes, CompteAuthRepository auth, CodesCandidat codes, PasswordEncoder encodeur,
            Clock horloge, @Value("${app.cao.lien-activation:http://localhost:4200/cao/activation}") String lienActivation) {
        this.comptes = comptes;
        this.auth = auth;
        this.codes = codes;
        this.encodeur = encodeur;
        this.horloge = horloge;
        this.lienActivation = lienActivation;
    }

    @Transactional(readOnly = true)
    public Optional<CompteCao> parEmail(String email) {
        return comptes.findByEmail(CandidatService.normaliserEmail(email));
    }

    /**
     * Le compte d'une adresse : retrouvé s'il existe, sinon créé {@code A_ACTIVER} avec un compte d'authentification inactif
     * dont le mot de passe, aléatoire, ne sert jamais (l'activation le remplace).
     */
    public CompteCao creerOuRetrouver(String email, String nom, String prenom) {
        String adresse = CandidatService.normaliserEmail(email);
        CompteCao existant = comptes.findByEmail(adresse).orElse(null);
        if (existant != null) {
            return existant;
        }
        byte[] aleatoire = new byte[32];
        HASARD.nextBytes(aleatoire);
        String id = String.format("K%09d", comptes.prochainNumero());
        CompteCao compte = comptes.save(new CompteCao(id, adresse, nom.trim(), prenom.trim(), CompteCao.A_ACTIVER,
                LocalDateTime.now(horloge), null, null, null));
        auth.save(new CompteAuth(adresse, encodeur.encode(Base64.getEncoder().encodeToString(aleatoire)), TypeActeur.MEMBRE_CAO.name(),
                id, false));
        return compte;
    }

    /** Une invitation (ou son renvoi) : 409 {@code COMPTE_ACTIF} si le compte est déjà activé ; le code précédent ne vaut plus. */
    public void inviter(CompteCao compte, String procedure, String autorite) {
        if (CompteCao.ACTIF.equals(compte.getEtat())) {
            throw new BusinessRuleException("Le compte de " + compte.getPrenom() + " " + compte.getNom() + " est déjà activé : il se "
                    + "connecte avec son mot de passe.", "COMPTE_ACTIF");
        }
        codes.emettreInvitation(compte.getIdCompte(), compte.getEmail(), compte.getPrenom(), procedure, autorite, lienActivation,
                HEURES_INVITATION);
        compte.setDateInvitation(LocalDateTime.now(horloge));
        comptes.save(compte);
    }

    /**
     * {@code POST /api/cao/activation} — 404 adresse inconnue ; 400 {@code CODE_INVALIDE} / {@code CODE_EXPIRE} ; 429 après
     * {@value CodesCandidat#ESSAIS_MAX} essais. Pose le mot de passe (politique des comptes internes, contrôlée en entrée),
     * active le compte. Un compte déjà actif répond {@code ACTIF} sans rien changer.
     */
    @Transactional(noRollbackFor = { BadRequestException.class, TropDeRequetesException.class })
    public CaoDto.EtatCompte activer(CaoDto.Activation demande) {
        CompteCao compte = comptes.findByEmail(CandidatService.normaliserEmail(demande.email()))
                .orElseThrow(() -> new ResourceNotFoundException("Aucun compte de membre de commission pour cette adresse électronique."));
        if (CompteCao.ACTIF.equals(compte.getEtat())) {
            return new CaoDto.EtatCompte(compte.getEtat());
        }
        switch (codes.verifier(compte.getIdCompte(), CodeCandidat.EMAIL, demande.code())) {
            case BON -> {
            }
            case FAUX -> throw new BadRequestException("Le code d'activation est faux.", "CODE_INVALIDE");
            case EXPIRE -> throw new BadRequestException("Le code d'activation a expiré : demandez à la PRMP de renvoyer l'invitation.",
                    "CODE_EXPIRE");
            case EPUISE -> throw new TropDeRequetesException("Trop d'essais : demandez à la PRMP de renvoyer l'invitation.", 60);
        }
        codes.consommer(compte.getIdCompte());
        CompteAuth a = auth.findByLogin(compte.getEmail()).orElseThrow(() -> new IllegalStateException("Compte d'authentification absent : "
                + compte.getIdCompte()));
        a.setMotDePasse(encodeur.encode(demande.motDePasse()));
        a.setActif(true);
        a.setStatut(StatutCompte.ACTIF.name());
        auth.save(a);
        compte.setEtat(CompteCao.ACTIF);
        compte.setDateActivation(LocalDateTime.now(horloge));
        comptes.save(compte);
        return new CaoDto.EtatCompte(compte.getEtat());
    }

    /** Le compte n'est pas encore activé (il n'a pas de mot de passe) : la connexion répond 409 {@code COMPTE_A_ACTIVER}. */
    @Transactional(readOnly = true)
    public boolean compteAActiver(CompteAuth compte) {
        return comptes.findById(compte.getRefActeur()).map(c -> CompteCao.A_ACTIVER.equals(c.getEtat())).orElse(false);
    }

    /** À la connexion, le mot de passe vérifié : compte archivé ou désactivé refusé ; la date de connexion posée ; « NOM Prénom ». */
    public String controlerConnexion(CompteAuth compte) {
        CompteCao c = comptes.findById(compte.getRefActeur())
                .orElseThrow(() -> new BadCredentialsException("Membre de commission introuvable pour ce compte."));
        if (CompteCao.ARCHIVE.equals(c.getEtat()) || !Boolean.TRUE.equals(compte.getActif())) {
            throw new BadCredentialsException("Compte désactivé.");
        }
        c.setDerniereConnexion(LocalDateTime.now(horloge));
        comptes.save(c);
        return nom(c);
    }

    /** « NOM Prénom » d'un membre. */
    static String nom(CompteCao c) {
        return c.getNom().toUpperCase(Locale.ROOT) + " " + c.getPrenom();
    }
}
