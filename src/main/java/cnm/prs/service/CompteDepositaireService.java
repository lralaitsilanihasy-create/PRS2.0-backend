package cnm.prs.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CaoDto;
import cnm.prs.entity.CodeCandidat;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.CompteDepositaire;
import cnm.prs.enums.StatutCompte;
import cnm.prs.enums.TypeActeur;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.exception.TropDeRequetesException;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.CompteDepositaireRepository;

/**
 * ⚠️ V71 (demande front du 2026-10-05, dépositaire de la part de secours, §B1, Q1) — les <strong>comptes des dépositaires</strong>
 * (profil {@code DEPOSITAIRE}), sur le modèle des membres de CAO ({@link CompteCaoService}) : créés à la désignation
 * ({@code A_ACTIVER}, login = l'adresse en minuscules, identifiant court {@code D} + 9 chiffres), invités par courriel (code à six
 * chiffres, 72 heures, {@link CodesCandidat}), activés en public ({@code POST /api/depositaire/activation}). Un dépositaire garde son
 * compte d'une procédure à l'autre.
 */
@Service
@Transactional
public class CompteDepositaireService {

    static final int HEURES_INVITATION = 72;
    private static final SecureRandom HASARD = new SecureRandom();

    private final CompteDepositaireRepository comptes;
    private final CompteAuthRepository auth;
    private final CodesCandidat codes;
    private final PasswordEncoder encodeur;
    private final Clock horloge;
    private final String lienActivation;

    public CompteDepositaireService(CompteDepositaireRepository comptes, CompteAuthRepository auth, CodesCandidat codes,
            PasswordEncoder encodeur, Clock horloge,
            @Value("${app.depositaire.lien-activation:http://localhost:4200/depositaire/activation}") String lienActivation) {
        this.comptes = comptes;
        this.auth = auth;
        this.codes = codes;
        this.encodeur = encodeur;
        this.horloge = horloge;
        this.lienActivation = lienActivation;
    }

    @Transactional(readOnly = true)
    public Optional<CompteDepositaire> parEmail(String email) {
        return comptes.findByEmail(CandidatService.normaliserEmail(email));
    }

    /**
     * Le compte d'une adresse : retrouvé s'il existe (le nom et le téléphone mis à jour), sinon créé {@code A_ACTIVER} avec un compte
     * d'authentification inactif dont le mot de passe, aléatoire, ne sert jamais (l'activation le remplace).
     */
    public CompteDepositaire creerOuRetrouver(String email, String nom, String telephone) {
        String adresse = CandidatService.normaliserEmail(email);
        CompteDepositaire existant = comptes.findByEmail(adresse).orElse(null);
        if (existant != null) {
            existant.setNom(nom.trim());
            existant.setTelephone(telephone);
            return comptes.save(existant);
        }
        byte[] aleatoire = new byte[32];
        HASARD.nextBytes(aleatoire);
        String id = String.format("D%09d", comptes.prochainNumero());
        CompteDepositaire compte = comptes.save(new CompteDepositaire(id, adresse, nom.trim(), telephone, CompteDepositaire.A_ACTIVER,
                LocalDateTime.now(horloge), null, null, null));
        auth.save(new CompteAuth(adresse, encodeur.encode(Base64.getEncoder().encodeToString(aleatoire)), TypeActeur.DEPOSITAIRE.name(),
                id, false));
        return compte;
    }

    /** Une invitation (ou son renvoi) : 409 {@code DEJA_ACTIF} si le compte est déjà activé ; le code précédent ne vaut plus. */
    public void inviter(CompteDepositaire compte, String procedure, String autorite) {
        if (CompteDepositaire.ACTIF.equals(compte.getEtat())) {
            throw new BusinessRuleException("Le compte du dépositaire " + compte.getNom() + " est déjà activé : il se connecte avec son "
                    + "mot de passe.", "DEJA_ACTIF");
        }
        codes.emettreInvitation(compte.getIdCompte(), compte.getEmail(), HEURES_INVITATION,
                "PRS — vous êtes désigné dépositaire de la part de secours d'une procédure",
                code -> "Bonjour " + compte.getNom() + ",\n\nVous êtes désigné dépositaire de la part de secours de la procédure « "
                        + procedure + " » (" + autorite + "). Vous générerez vous-même, sur votre poste, la clé de secours : "
                        + "personne d'autre ne verra votre phrase secrète.\n\nPour activer votre compte, rendez-vous sur " + lienActivation
                        + " et saisissez ce code d'activation : " + code + "\nIl est valable " + HEURES_INVITATION
                        + " heures et ne sert qu'une fois.\n\nSi vous n'êtes pas concerné, ignorez ce message.");
        compte.setDateInvitation(LocalDateTime.now(horloge));
        comptes.save(compte);
    }

    /**
     * {@code POST /api/depositaire/activation} — 404 adresse inconnue ; 400 {@code CODE_INVALIDE} / {@code CODE_EXPIRE} ; 429 après
     * {@value CodesCandidat#ESSAIS_MAX} essais. Pose le mot de passe, active le compte. Un compte déjà actif répond {@code ACTIF}.
     *
     * @return le compte (son état)
     */
    @Transactional(noRollbackFor = { BadRequestException.class, TropDeRequetesException.class })
    public CompteDepositaire activer(CaoDto.Activation demande) {
        CompteDepositaire compte = comptes.findByEmail(CandidatService.normaliserEmail(demande.email()))
                .orElseThrow(() -> new ResourceNotFoundException("Aucun compte de dépositaire pour cette adresse électronique."));
        if (CompteDepositaire.ACTIF.equals(compte.getEtat())) {
            return compte;
        }
        switch (codes.verifier(compte.getIdCompte(), CodeCandidat.EMAIL, demande.code())) {
            case BON -> {
            }
            case FAUX -> throw new BadRequestException("Le code d'activation est faux.", "CODE_INVALIDE");
            case EXPIRE -> throw new BadRequestException("Le code d'activation a expiré : demandez au responsable de la procédure de "
                    + "renvoyer l'invitation.", "CODE_EXPIRE");
            case EPUISE -> throw new TropDeRequetesException("Trop d'essais : demandez au responsable de la procédure de renvoyer "
                    + "l'invitation.", 60);
        }
        codes.consommer(compte.getIdCompte());
        CompteAuth a = auth.findByLogin(compte.getEmail()).orElseThrow(() -> new IllegalStateException("Compte d'authentification absent : "
                + compte.getIdCompte()));
        a.setMotDePasse(encodeur.encode(demande.motDePasse()));
        a.setActif(true);
        a.setStatut(StatutCompte.ACTIF.name());
        auth.save(a);
        compte.setEtat(CompteDepositaire.ACTIF);
        compte.setDateActivation(LocalDateTime.now(horloge));
        return comptes.save(compte);
    }

    /** Le compte n'est pas encore activé : la connexion répond 409 {@code COMPTE_A_ACTIVER}. */
    @Transactional(readOnly = true)
    public boolean compteAActiver(CompteAuth compte) {
        return comptes.findById(compte.getRefActeur()).map(c -> CompteDepositaire.A_ACTIVER.equals(c.getEtat())).orElse(false);
    }

    /** À la connexion, le mot de passe vérifié : compte archivé ou désactivé refusé ; la date de connexion posée ; le nom. */
    public String controlerConnexion(CompteAuth compte) {
        CompteDepositaire c = comptes.findById(compte.getRefActeur())
                .orElseThrow(() -> new BadCredentialsException("Dépositaire introuvable pour ce compte."));
        if (CompteDepositaire.ARCHIVE.equals(c.getEtat()) || !Boolean.TRUE.equals(compte.getActif())) {
            throw new BadCredentialsException("Compte désactivé.");
        }
        c.setDerniereConnexion(LocalDateTime.now(horloge));
        comptes.save(c);
        return c.getNom();
    }

    /** L'état servi : {@code A_INVITER} · {@code INVITE} · {@code ACTIF} · {@code ARCHIVE} (comme les membres de CAO). */
    static String etat(CompteDepositaire c) {
        if (c == null) {
            return "A_INVITER";
        }
        return CompteDepositaire.ACTIF.equals(c.getEtat()) ? "ACTIF" : CompteDepositaire.ARCHIVE.equals(c.getEtat()) ? "ARCHIVE"
                : c.getDateInvitation() == null ? "A_INVITER" : "INVITE";
    }
}
