package cnm.prs.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.Entreprise;
import cnm.prs.entity.RapprochementCandidat;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.EntrepriseRepository;
import cnm.prs.repository.RapprochementCandidatRepository;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1b, §B6) — les <strong>rapprochements</strong> entre comptes
 * candidats, calculés et gardés ici, remis à la commission au lot 4 pour les offres d'une même procédure. Jamais un refus.
 *
 * <ul>
 *   <li>{@code TELEPHONE} : même numéro, chiffres seuls, indicatif +261 ramené au zéro ;</li>
 *   <li>{@code SIGNATAIRE} : même représentant de l'entreprise (nom et prénom normalisés) ; le signataire de l'offre
 *       s'y ajoutera au lot 3 ;</li>
 *   <li>{@code ADRESSE} : même adresse postale après normalisation (casse, blancs, accents) ;</li>
 *   <li>{@code EMAIL} : l'adresse électronique d'un compte est unique, deux comptes ne la partagent donc jamais ; le
 *       critère reste au catalogue pour les adresses saisies dans les offres (lot 3).</li>
 * </ul>
 * L'adresse IP n'est pas un critère (une IP partagée donnerait de fausses alertes).
 */
@Service
@Transactional
public class RapprochementCandidatService {

    private final RapprochementCandidatRepository repository;
    private final CompteCandidatRepository candidats;
    private final EntrepriseRepository entreprises;
    private final Clock horloge;

    public RapprochementCandidatService(RapprochementCandidatRepository repository, CompteCandidatRepository candidats,
            EntrepriseRepository entreprises, Clock horloge) {
        this.repository = repository;
        this.candidats = candidats;
        this.entreprises = entreprises;
        this.horloge = horloge;
    }

    /** Recalcule les rapprochements d'un candidat avec tous les autres (après son inscription ou sa déclaration). */
    public void recalculer(String idCandidat) {
        CompteCandidat moi = candidats.findById(idCandidat).orElse(null);
        if (moi == null) {
            return;
        }
        repository.supprimerPour(idCandidat);
        repository.flush();
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        String tel = NormalisationCandidat.telephone(moi.getTelephone());
        if (!tel.isEmpty()) {
            for (CompteCandidat autre : candidats.findAll()) {
                if (!autre.getIdCandidat().equals(idCandidat) && tel.equals(NormalisationCandidat.telephone(autre.getTelephone()))) {
                    ajouter(idCandidat, autre.getIdCandidat(), "TELEPHONE", tel, maintenant);
                }
            }
        }
        Entreprise mienne = entreprises.findByIdCandidat(idCandidat).orElse(null);
        if (mienne != null) {
            String signataire = NormalisationCandidat.personne(mienne.getRepNom(), mienne.getRepPrenom());
            for (Entreprise autre : entreprises.findByIdCandidatNot(idCandidat)) {
                if (signataire.equals(NormalisationCandidat.personne(autre.getRepNom(), autre.getRepPrenom()))) {
                    ajouter(idCandidat, autre.getIdCandidat(), "SIGNATAIRE", signataire, maintenant);
                }
            }
            for (Entreprise autre : entreprises.findByAdresseNormaliseeAndIdCandidatNot(mienne.getAdresseNormalisee(), idCandidat)) {
                ajouter(idCandidat, autre.getIdCandidat(), "ADRESSE", mienne.getAdresseNormalisee(), maintenant);
            }
        }
    }

    /** Les rapprochements d'un candidat (pour le lot 4 et les tests). */
    @Transactional(readOnly = true)
    public List<RapprochementCandidat> de(String idCandidat) {
        return repository.pour(idCandidat);
    }

    private void ajouter(String x, String y, String critere, String valeur, LocalDateTime maintenant) {
        String a = x.compareTo(y) < 0 ? x : y;
        String b = x.compareTo(y) < 0 ? y : x;
        String v = valeur.length() > 300 ? valeur.substring(0, 300) : valeur;
        repository.save(new RapprochementCandidat(null, a, b, critere, v, maintenant));
    }
}
