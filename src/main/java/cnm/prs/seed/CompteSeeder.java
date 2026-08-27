package cnm.prs.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Prmp;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.PrmpRepository;

/**
 * Utilitaire de développement : crée un compte d'authentification ({@code t_compte_auth})
 * pour chaque contrôleur et chaque PRMP existant, afin de pouvoir tester l'API sécurisée.
 *
 * <p><strong>Désactivé par défaut.</strong> Activer avec {@code app.seed.comptes.enabled=true}.
 * Le login est le matricule du contrôleur ({@code IM_CONTROLEUR}) ou l'identifiant PRMP
 * ({@code ID_PRMP}) ; tous reçoivent le même mot de passe par défaut
 * ({@code app.seed.comptes.password}, BCrypt). Idempotent : les logins déjà présents sont
 * ignorés. À n'utiliser qu'en environnement de développement.</p>
 *
 * <p>⚠️ <strong>Ce seed ne crée que des COMPTES, jamais les fiches qui les portent</strong>
 * (constat de recette du 2026-08-27). Il <em>parcourt</em> {@code tr_controleur} et {@code t_prmp}
 * et ouvre un compte pour chaque fiche trouvée : sur une base <strong>vierge</strong> — aucun
 * contrôleur, aucune PRMP — il parcourt deux tables vides et crée <strong>0 compte</strong>, en
 * journalisant « 0 compte(s) contrôleur + 0 compte(s) PRMP créé(s) » sans la moindre erreur. Le
 * symptôme est alors trompeur : l'application démarre, le seed dit avoir tourné, et
 * <strong>personne ne peut se connecter</strong>.</p>
 *
 * <p>Les <strong>données de référence minimales doivent donc exister d'abord</strong> : profils
 * ({@code tr_profile}), puis fiches contrôleur et/ou PRMP. Elles ne sont livrées ni par Flyway (V1
 * crée le schéma, pas le contenu métier) ni par ce seed. Voir {@code docs/deploiement.md}, §11.</p>
 */
@Component
@ConditionalOnProperty(name = "app.seed.comptes.enabled", havingValue = "true")
public class CompteSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CompteSeeder.class);

    private final ControleurRepository controleurRepository;
    private final PrmpRepository prmpRepository;
    private final CompteAuthRepository compteRepository;
    private final PasswordEncoder passwordEncoder;
    private final String motDePasseParDefaut;

    public CompteSeeder(ControleurRepository controleurRepository, PrmpRepository prmpRepository,
            CompteAuthRepository compteRepository, PasswordEncoder passwordEncoder,
            @Value("${app.seed.comptes.password:Test@1234}") String motDePasseParDefaut) {
        this.controleurRepository = controleurRepository;
        this.prmpRepository = prmpRepository;
        this.compteRepository = compteRepository;
        this.passwordEncoder = passwordEncoder;
        this.motDePasseParDefaut = motDePasseParDefaut;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String hash = passwordEncoder.encode(motDePasseParDefaut);
        int controleurs = seedControleurs(hash);
        int prmps = seedPrmps(hash);
        log.warn("[SEED] {} compte(s) contrôleur + {} compte(s) PRMP créé(s). "
                + "Login = matricule/ID, mot de passe commun = « {} ». "
                + "À désactiver hors développement (app.seed.comptes.enabled=false).",
                controleurs, prmps, motDePasseParDefaut);
    }

    private int seedControleurs(String hash) {
        int n = 0;
        for (Controleur c : controleurRepository.findAll()) {
            String login = c.getImControleur();
            if (login == null || compteRepository.existsById(login)) {
                continue;
            }
            compteRepository.save(new CompteAuth(login, hash, TypeActeur.CONTROLEUR.name(), login, true));
            n++;
        }
        return n;
    }

    private int seedPrmps(String hash) {
        int n = 0;
        for (Prmp p : prmpRepository.findAll()) {
            String login = p.getIdPrmp();
            if (login == null || compteRepository.existsById(login)) {
                continue;
            }
            compteRepository.save(new CompteAuth(login, hash, TypeActeur.PRMP.name(), login, true));
            n++;
        }
        return n;
    }
}
