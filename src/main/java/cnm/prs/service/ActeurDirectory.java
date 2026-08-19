package cnm.prs.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Controleur;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.UgpmRepository;

/**
 * Annuaire des acteurs par <strong>login</strong> : traduit un login de compte
 * ({@code t_compte_auth.LOGIN}, tel que stocké dans {@code CREE_PAR} / {@code SOUMIS_PAR})
 * en <strong>nom lisible</strong> « Prénoms Nom » de la personne (PRMP, UGPM ou contrôleur).
 *
 * <p>⚠️ Demande front du 2026-08-19 : le front ne peut pas faire cette jointure lui-même — le
 * login n'est pas l'identifiant de l'acteur, et le répertoire des UGPM n'est pas ouvert à tous.
 * La résolution est donc <strong>serveur</strong>, et se fait <strong>en lot</strong>
 * ({@link #nomsParLogin}) : trois requêtes au plus quelle que soit la taille de la liste.</p>
 */
@Service
public class ActeurDirectory {

    private final CompteAuthRepository compteAuthRepository;
    private final PrmpRepository prmpRepository;
    private final UgpmRepository ugpmRepository;
    private final ControleurRepository controleurRepository;

    public ActeurDirectory(CompteAuthRepository compteAuthRepository, PrmpRepository prmpRepository,
            UgpmRepository ugpmRepository, ControleurRepository controleurRepository) {
        this.compteAuthRepository = compteAuthRepository;
        this.prmpRepository = prmpRepository;
        this.ugpmRepository = ugpmRepository;
        this.controleurRepository = controleurRepository;
    }

    /**
     * Noms lisibles des acteurs correspondant aux logins fournis. Les logins inconnus (compte
     * supprimé, acteur introuvable) sont <strong>absents</strong> de la table : l'appelant garde
     * alors le login brut comme repli.
     */
    @Transactional(readOnly = true)
    public Map<String, String> nomsParLogin(Collection<String> logins) {
        Set<String> demandes = logins == null ? Set.of()
                : logins.stream().filter(l -> l != null && !l.isBlank()).collect(Collectors.toSet());
        if (demandes.isEmpty()) {
            return Map.of();
        }
        List<CompteAuth> comptes = compteAuthRepository.findByLoginIn(demandes);
        Set<String> refsPrmp = refs(comptes, TypeActeur.PRMP);
        Set<String> refsUgpm = refs(comptes, TypeActeur.UGPM);
        Set<String> refsControleur = refs(comptes, TypeActeur.CONTROLEUR);

        Map<String, String> nomsPrmp = refsPrmp.isEmpty() ? Map.of()
                : prmpRepository.findAllById(refsPrmp).stream()
                        .collect(Collectors.toMap(Prmp::getIdPrmp,
                                p -> assembler(p.getPrenomsPrmp(), p.getNomPrmp())));
        Map<String, String> nomsUgpm = refsUgpm.isEmpty() ? Map.of()
                : ugpmRepository.findAllById(refsUgpm).stream()
                        .collect(Collectors.toMap(Ugpm::getIdUgpm,
                                u -> assembler(u.getPrenomsUgpm(), u.getNomUgpm())));
        Map<String, String> nomsControleur = refsControleur.isEmpty() ? Map.of()
                : controleurRepository.findAllById(refsControleur).stream()
                        .collect(Collectors.toMap(Controleur::getImControleur,
                                c -> assembler(c.getPrenomsCont(), c.getNomCont())));

        Map<String, String> resultat = new HashMap<>();
        for (CompteAuth compte : comptes) {
            TypeActeur type = typeDe(compte);
            Map<String, String> source = Map.of();
            if (type == TypeActeur.PRMP) {
                source = nomsPrmp;
            } else if (type == TypeActeur.UGPM) {
                source = nomsUgpm;
            } else if (type == TypeActeur.CONTROLEUR) {
                source = nomsControleur;
            }
            String nom = source.get(compte.getRefActeur());
            if (nom != null && !nom.isBlank()) {
                resultat.put(compte.getLogin(), nom);
            }
        }
        return resultat;
    }

    /** Nom lisible d'un seul login, ou {@code null} s'il n'est pas résolvable. */
    @Transactional(readOnly = true)
    public String nom(String login) {
        return nomsParLogin(login == null ? Set.of() : Set.of(login)).get(login);
    }

    private static Set<String> refs(List<CompteAuth> comptes, TypeActeur type) {
        Set<String> refs = new HashSet<>();
        for (CompteAuth c : comptes) {
            if (typeDe(c) == type && c.getRefActeur() != null) {
                refs.add(c.getRefActeur());
            }
        }
        return refs;
    }

    private static TypeActeur typeDe(CompteAuth compte) {
        try {
            return TypeActeur.valueOf(compte.getTypeActeur());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;   // type inconnu en base : pas de résolution, le login brut fait foi
        }
    }

    /**
     * Nom d'affichage « Nom Prénoms » — <strong>même convention que le {@code nomAffichage} du
     * login</strong> ({@code AuthService}), pour que la même personne s'écrive partout pareil.
     */
    private static String assembler(String prenoms, String nom) {
        return ((nom == null ? "" : nom.trim()) + " " + (prenoms == null ? "" : prenoms.trim())).trim();
    }
}
