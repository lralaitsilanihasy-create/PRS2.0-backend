package cnm.prs.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Parametre;
import cnm.prs.repository.ParametreRepository;
import cnm.prs.security.CurrentUser;

/**
 * Paramètres système ({@code t_parametre}, clé/valeur) — éditables sans redéploiement.
 */
@Service
@Transactional
public class ParametreService {

    /** Interrupteur global des actualités (spec 2026-08-18). */
    public static final String ACTUALITES_ACTIVES = "ACTUALITES_ACTIVES";

    private final ParametreRepository repository;

    public ParametreService(ParametreRepository repository) {
        this.repository = repository;
    }

    /**
     * État de l'interrupteur global des actualités. Ligne absente = <strong>actif</strong> :
     * c'est un coupe-circuit (chaque actualité naît de toute façon INACTIF — l'activation
     * reste un acte délibéré), pas une seconde activation à cocher.
     */
    @Transactional(readOnly = true)
    public boolean actualitesActives() {
        return repository.findById(ACTUALITES_ACTIVES)
                .map(p -> !"false".equalsIgnoreCase(p.getValeur()))
                .orElse(true);
    }

    /** Bascule l'interrupteur (Administrateur) — upsert horodaté avec l'identité JWT. */
    public boolean basculerActualites(boolean actif) {
        Parametre p = repository.findById(ACTUALITES_ACTIVES)
                .orElseGet(() -> new Parametre(ACTUALITES_ACTIVES, null, null, null));
        p.setValeur(Boolean.toString(actif));
        p.setDateMaj(LocalDateTime.now());
        p.setImActeur(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        repository.save(p);
        return actif;
    }
}
