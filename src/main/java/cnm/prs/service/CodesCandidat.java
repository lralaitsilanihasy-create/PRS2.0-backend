package cnm.prs.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.CodeCandidat;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.repository.CodeCandidatRepository;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1a, §B2) — les <strong>codes de confirmation</strong> d'un
 * compte candidat : six chiffres tirés par {@link SecureRandom}, à usage unique, valables {@value #MINUTES} minutes,
 * {@value #ESSAIS_MAX} essais au plus, stockés hachés (BCrypt). Émettre un code invalide les précédents du même canal.
 *
 * <p>Les opérations rejoignent la transaction de l'appelant ; c'est lui qui la garde malgré son refus (un essai manqué
 * compté sous un 400, un code émis à une connexion refusée sous un 409) par {@code noRollbackFor}.</p>
 */
@Component
public class CodesCandidat {

    static final int MINUTES = 15;
    static final int ESSAIS_MAX = 5;

    /** Le résultat d'une vérification. */
    public enum Verdict { BON, FAUX, EXPIRE, EPUISE }

    private static final SecureRandom HASARD = new SecureRandom();

    private final CodeCandidatRepository repository;
    private final PasswordEncoder encodeur;
    private final EmailService email;
    private final PasserelleSms sms;
    private final Clock horloge;

    public CodesCandidat(CodeCandidatRepository repository, PasswordEncoder encodeur, EmailService email, PasserelleSms sms,
            Clock horloge) {
        this.repository = repository;
        this.encodeur = encodeur;
        this.email = email;
        this.sms = sms;
        this.horloge = horloge;
    }

    /**
     * Émet un code par courriel et, si {@code telephone}, un code par SMS ; les codes précédents non utilisés sont
     * invalidés.
     */
    @Transactional
    public void emettre(CompteCandidat compte, boolean telephone) {
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        repository.findByIdCandidatAndUtiliseFalse(compte.getIdCandidat()).forEach(c -> {
            c.setUtilise(true);
            repository.save(c);
        });
        String codeEmail = tirer();
        repository.save(new CodeCandidat(null, compte.getIdCandidat(), CodeCandidat.EMAIL, encodeur.encode(codeEmail), maintenant,
                maintenant.plusMinutes(MINUTES), 0, false));
        email.envoyer(compte.getEmail(), "PRS — votre code de confirmation",
                "Bonjour " + compte.getPrenom() + ",\n\nVotre code de confirmation est : " + codeEmail
                        + "\nIl est valable " + MINUTES + " minutes et ne sert qu'une fois.\n\n"
                        + "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.");
        if (telephone) {
            String codeTel = tirer();
            repository.save(new CodeCandidat(null, compte.getIdCandidat(), CodeCandidat.TELEPHONE, encodeur.encode(codeTel),
                    maintenant, maintenant.plusMinutes(MINUTES), 0, false));
            sms.envoyer(compte.getTelephone(), "PRS : votre code de confirmation est " + codeTel + " (" + MINUTES + " min).");
        }
    }

    /** Vérifie le dernier code du canal ; un essai faux est compté, un code bon est consommé. */
    @Transactional
    public Verdict verifier(String idCandidat, String canal, String saisi) {
        CodeCandidat code = repository.findFirstByIdCandidatAndCanalAndUtiliseFalseOrderByDateEmissionDescIdCodeDesc(idCandidat, canal)
                .orElse(null);
        if (code == null || code.getExpiration().isBefore(LocalDateTime.now(horloge))) {
            return Verdict.EXPIRE;
        }
        if (code.getEssais() >= ESSAIS_MAX) {
            return Verdict.EPUISE;
        }
        if (saisi == null || !saisi.trim().matches("\\d{6}") || !encodeur.matches(saisi.trim(), code.getHashCode())) {
            code.setEssais(code.getEssais() + 1);
            repository.save(code);
            return code.getEssais() >= ESSAIS_MAX ? Verdict.EPUISE : Verdict.FAUX;
        }
        return Verdict.BON;
    }

    /** Consomme les codes en cours d'un compte (après une confirmation réussie). */
    @Transactional
    public void consommer(String idCandidat) {
        repository.findByIdCandidatAndUtiliseFalse(idCandidat).forEach(c -> {
            c.setUtilise(true);
            repository.save(c);
        });
    }

    private static String tirer() {
        return String.format("%06d", HASARD.nextInt(1_000_000));
    }
}
