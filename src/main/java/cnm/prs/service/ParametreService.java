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

    /**
     * ⚠️ Arbitrage pilote (2026-09-07, suite) — <strong>seuil de montant</strong> au-delà duquel un marché
     * passé selon un mode à déclenchement <em>conditionnel</em> (l'appel à manifestation d'intérêt) rend
     * l'AGPM requis. Administrable : le pilote le saisit et l'ajuste depuis l'administration, sans
     * redéploiement — aucune valeur numérique n'est écrite dans le code.
     */
    public static final String AGPM_SEUIL_MONTANT = "AGPM_SEUIL_MONTANT";

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

    /**
     * Seuil courant du déclenchement AGPM conditionnel (cf. {@link #AGPM_SEUIL_MONTANT}).
     *
     * <p><strong>Ligne absente ou illisible = zéro</strong>, c'est-à-dire « tout marché du mode concerné
     * déclenche l'AGPM ». Le défaut penche du côté de la publicité : manquer un AGPM dû est un manquement
     * réglementaire, en produire un de trop ne l'est pas. Le pilote resserre ensuite d'une saisie.</p>
     */
    @Transactional(readOnly = true)
    public java.math.BigDecimal seuilAgpmMontant() {
        return repository.findById(AGPM_SEUIL_MONTANT)
                .map(Parametre::getValeur)
                .map(v -> {
                    try {
                        return new java.math.BigDecimal(v.trim());
                    } catch (RuntimeException e) {
                        return java.math.BigDecimal.ZERO;
                    }
                })
                .orElse(java.math.BigDecimal.ZERO);
    }

    /** Fixe le seuil (Administrateur) — upsert horodaté avec l'identité JWT. Refuse une valeur négative. */
    public java.math.BigDecimal fixerSeuilAgpmMontant(java.math.BigDecimal seuil) {
        if (seuil == null || seuil.signum() < 0) {
            throw new cnm.prs.exception.BadRequestException(
                    "Le seuil de déclenchement de l'AGPM doit être un montant positif ou nul.");
        }
        Parametre p = repository.findById(AGPM_SEUIL_MONTANT)
                .orElseGet(() -> new Parametre(AGPM_SEUIL_MONTANT, null, null, null));
        p.setValeur(seuil.toPlainString());
        p.setDateMaj(LocalDateTime.now());
        p.setImActeur(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        repository.save(p);
        return seuil;
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
