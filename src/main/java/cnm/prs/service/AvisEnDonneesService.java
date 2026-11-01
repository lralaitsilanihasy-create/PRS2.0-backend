package cnm.prs.service;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AvisEnDonneesDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.dto.MentionsDto;
import cnm.prs.entity.Dossier;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DossierRepository;

/**
 * ⚠️ 2026-10-18 (demande front « avis-specifique-en-donnees », §B1 ; demande du pilote) — le projet d'avis spécifique (AVIS-F,
 * AVIS-T) ou de lettre d'invitation (LETTRE-PI, LETTRE-AOR) d'un dossier né d'une fiche validée, <strong>en données</strong> : les
 * mentions que le manuel fait contrôler ({@link MentionsPublication}), avec la valeur que le document rendu imprime.
 * <p>
 * La version lue est celle dont le PDF est joint sous {@code AVIS_SPECIFIQUE} (ou {@code LETTRE_INVITATION}) : la version soumise du
 * dossier, à défaut la dernière validée (Q1 : {@link DocumentsFicheMarcheService#joindre} remplace le projet à chaque validation tant
 * que le dossier est en constitution). Garde de lecture du dossier, comme la fiche de présentation et le canevas de rapport.
 */
@Service
@Transactional(readOnly = true)
public class AvisEnDonneesService {

    private final DossierRepository dossiers;
    private final FicheMarcheService fiches;
    private final ValeursPpmService valeursPpm;
    private final DocumentsFicheMarcheService documents;
    private final ObjectProvider<DossierService> dossierService;

    public AvisEnDonneesService(DossierRepository dossiers, FicheMarcheService fiches, ValeursPpmService valeursPpm,
            DocumentsFicheMarcheService documents, ObjectProvider<DossierService> dossierService) {
        this.dossiers = dossiers;
        this.fiches = fiches;
        this.valeursPpm = valeursPpm;
        this.documents = documents;
        this.dossierService = dossierService;
    }

    /**
     * Les données ; 403 hors périmètre, 404 ; 409 {@code AVIS_INDISPONIBLE}, raison {@code FICHE_NON_VALIDEE} (dossier qui n'est pas
     * né d'une fiche validée) ou {@code CATEGORIE_SANS_AVIS} (aucun modèle de publication pour la catégorie et le mode).
     */
    public AvisEnDonneesDto lire(Integer idDossier) {
        dossierService.getObject().controlerVisibilite(idDossier);
        Dossier d = dossiers.findById(idDossier).orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        FicheMarcheService.VersionPresentee vp = d.getIdDmc() == null ? null
                : fiches.versionPresentee(d.getIdDmc(), d.getVersionFicheSoumise()).orElse(null);
        if (vp == null) {
            throw new BusinessRuleException("Ce dossier n'est pas né d'une fiche validée : son avis spécifique n'est pas produit par "
                    + "l'application.", AvisSpecifiqueService.CODE_INDISPONIBLE, idDossier,
                    Map.of("raison", AvisSpecifiqueService.FICHE_NON_VALIDEE));
        }
        FicheMarcheService.EtatVersion ev = vp.etat();
        FicheMarcheDto etat = ev.etat();
        boolean restreint = etat.getIdDetail() != null && valeursPpm.modeRestreint(etat.getIdDetail());
        String avis = ModelesDao.sigleAvis(ev.categorie(), restreint);
        String sigle = avis != null ? avis : ModelesDao.sigleLettre(ev.categorie(), restreint);
        if (sigle == null) {
            throw new BusinessRuleException("Aucun modèle de publication pour cette catégorie et ce mode.", AvisSpecifiqueService.CODE_INDISPONIBLE,
                    idDossier, Map.of("raison", AvisSpecifiqueService.CATEGORIE_SANS_AVIS));
        }
        boolean lettre = avis == null;
        MentionsDto m = MentionsPublication.verifier(sigle, documents.modele(sigle), documents.rendrePublication(etat, sigle, lettre), etat);
        return new AvisEnDonneesDto(idDossier, d.getIdDmc(), ev.version(), sigle, lettre, valeur(ev, "B01-AC-01"), valeur(ev, "B02-OB-03"),
                m.documentComplet(), m.mentions());
    }

    /**
     * ⚠️ 2026-11-01 (demande front « avis-specifique-public-donnees », §B1) — les mentions de l'avis <strong>publié</strong>, lues sur la
     * version {@code version} de la fiche (celle de la dernière impression, fournie par l'appelant), pour l'espace candidat : même calcul
     * que {@link #lire} ({@link MentionsPublication#verifier}), sans les contrôles de la Commission ; le numéro imprimé à l'avis
     * ({@link DaoCompletService#numero}). Sans garde (l'appelant applique les critères publics) ; 404 sans version validée ou sans
     * modèle d'avis (consultation restreinte : des lettres d'invitation, pas d'avis).
     */
    public cnm.prs.dto.AvisPublicDto publique(Long idDmc, Integer version) {
        FicheMarcheService.EtatVersion ev = fiches.versionPresentee(idDmc, version).map(FicheMarcheService.VersionPresentee::etat)
                .orElseThrow(() -> new ResourceNotFoundException("Avis spécifique introuvable."));
        FicheMarcheDto etat = ev.etat();
        boolean restreint = etat.getIdDetail() != null && valeursPpm.modeRestreint(etat.getIdDetail());
        String sigle = ModelesDao.sigleAvis(ev.categorie(), restreint);
        if (sigle == null) {
            throw new ResourceNotFoundException("Avis spécifique introuvable.");
        }
        MentionsDto m = MentionsPublication.verifier(sigle, documents.modele(sigle), documents.rendrePublication(etat, sigle, false), etat);
        return new cnm.prs.dto.AvisPublicDto(idDmc, ev.version(), sigle, DaoCompletService.numero(etat), valeur(ev, "B01-AC-01"),
                m.mentions().stream().map(x -> new cnm.prs.dto.AvisPublicDto.Mention(x.code(), x.libelle(), x.sansObjet(),
                        x.valeur() == null || x.valeur().isBlank() ? null : x.valeur())).toList());
    }

    /** La valeur de la fiche, à défaut celle du plan (l'autorité contractante et la référence sont reprises du PPM). */
    private static String valeur(FicheMarcheService.EtatVersion ev, String code) {
        String v = FichePresentationService.premier(ev, code);
        if (v == null && ev.etat().getValeursPpm() != null) {
            v = ev.etat().getValeursPpm().get(code);
        }
        return v == null || v.isBlank() ? null : v;
    }
}
