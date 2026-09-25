package cnm.prs.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ExamenDetailDto;
import cnm.prs.dto.ObservationControleDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.ObservationControle;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenDetailRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.PointsCtrlRepository;

/**
 * ⚠️ <strong>Une observation d'examen qui pointe une information de la fiche DAO</strong> (demande front du 2026-09-25,
 * V44) — le validateur UNIQUE de {@code idDmc} et {@code champFiche}, appelé par les deux portes qui écrivent une ligne
 * « Au lieu de / Lire » ({@code /api/examen-details} et {@code /api/observation-controles}), après les gardes
 * d'identité et de verrou de l'examen et après le validateur de cellule (V30). L'habilitation est celle de
 * l'observation : aucun rôle de plus.
 *
 * <ol>
 *   <li>{@code champFiche} sans {@code idDmc} → 400 {@code idDmc} ;</li>
 *   <li>{@code champFiche} avec une cellule du PPM ({@code champ}, V30), ou sur un point de portée
 *       {@code SUPPRESSION} → 400 {@code champFiche} : une ligne vise un seul endroit ;</li>
 *   <li>{@code idDmc} qui n'est pas la fiche du dossier examiné ({@code t_dossier.ID_DMC}) → 409
 *       {@code FICHE_HORS_DOSSIER} ;</li>
 *   <li>{@code champFiche} hors du référentiel de la fiche (forme et catégorie), ou rang de lot mal posé (mêmes règles
 *       qu'à la saisie) → 400 {@code champFiche} ({@link FicheMarcheService#ancrer}).</li>
 * </ol>
 *
 * <p>Le libellé et la valeur sont posés dans le DTO par ce validateur — jamais repris du client — puis
 * {@link #conserver} y remet ceux de la ligne existante quand l'ancrage n'a pas changé : l'observation garde la valeur
 * observée, même si la fiche a été révisée depuis.</p>
 */
@Component
@Transactional(readOnly = true)
public class ObservationFicheValidateur {

    private final ExamenDetailRepository examenDetailRepository;
    private final ExamenRepository examenRepository;
    private final DossierRepository dossierRepository;
    private final PointsCtrlRepository pointsCtrlRepository;
    private final FicheMarcheService ficheService;

    public ObservationFicheValidateur(ExamenDetailRepository examenDetailRepository, ExamenRepository examenRepository,
            DossierRepository dossierRepository, PointsCtrlRepository pointsCtrlRepository,
            FicheMarcheService ficheService) {
        this.examenDetailRepository = examenDetailRepository;
        this.examenRepository = examenRepository;
        this.dossierRepository = dossierRepository;
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.ficheService = ficheService;
    }

    /** Lignes portées par un résultat : erreurs ciblées sur {@code observations[i].<champ>}. */
    public void validerResultat(ExamenDetailDto resultat) {
        List<ObservationControleDto> lignes = resultat.getObservations();
        if (lignes == null) {
            return;
        }
        for (int i = 0; i < lignes.size(); i++) {
            if (lignes.get(i) != null) {
                valider(lignes.get(i), resultat.getIdExamen(), resultat.getIdPtControle(), "observations[" + i + "].");
            }
        }
    }

    /** Ligne seule, lue sur son résultat {@code idDetail}. */
    public void validerLigne(ObservationControleDto ligne) {
        ExamenDetail resultat = ligne == null || ligne.getIdDetail() == null ? null
                : examenDetailRepository.findById(ligne.getIdDetail()).orElse(null);
        if (ligne != null) {
            valider(ligne, resultat == null ? null : resultat.getIdExamen(),
                    resultat == null ? null : resultat.getIdPtControle(), "");
        }
    }

    /**
     * Remet dans {@code ligne} le libellé et la valeur figés d'{@code existante} quand elle vise la même information de
     * la même fiche ({@code existante} nulle : rien).
     */
    public static void conserver(ObservationControleDto ligne, ObservationControle existante) {
        if (existante != null && ligne.getChampFiche() != null
                && Objects.equals(existante.getChampFiche(), ligne.getChampFiche())
                && Objects.equals(existante.getIdDmcFiche(), ligne.getIdDmc())) {
            ligne.setLibelleChampFiche(existante.getLibelleChampFiche());
            ligne.setValeurChampFiche(existante.getValeurChampFiche());
        }
    }

    private void valider(ObservationControleDto ligne, Integer idExamen, Integer idPtControle, String prefixe) {
        ligne.setLibelleChampFiche(null);   // lecture seule : jamais repris du client
        ligne.setValeurChampFiche(null);
        ligne.setLot(null);
        String cle = ligne.getChampFiche() == null || ligne.getChampFiche().isBlank() ? null
                : ligne.getChampFiche().trim().toUpperCase();
        ligne.setChampFiche(cle);
        if (cle == null && ligne.getIdDmc() == null) {
            return;
        }
        if (cle != null && ligne.getIdDmc() == null) {
            throw erreur(prefixe + "idDmc", "Une information de la fiche (« " + cle + " ») se vise avec sa fiche : "
                    + "idDmc est obligatoire.");
        }
        if (cle != null && ligne.getChamp() != null) {
            throw erreur(prefixe + "champFiche", "Une observation vise une cellule du plan (« champ ») ou une "
                    + "information de la fiche (« champFiche »), pas les deux.");
        }
        PorteePointCtrl portee = idPtControle == null ? null
                : pointsCtrlRepository.findById(idPtControle).map(p -> p.getPortee()).orElse(null);
        if (cle != null && portee == PorteePointCtrl.SUPPRESSION) {
            throw erreur(prefixe + "champFiche", "Un point de portée SUPPRESSION constate une ligne retirée : il "
                    + "n'accepte aucune information ciblée.");
        }
        Long ficheDuDossier = idExamen == null ? null : examenRepository.findIdDossierByExamen(idExamen)
                .flatMap(dossierRepository::findById).map(Dossier::getIdDmc).orElse(null);
        if (!ligne.getIdDmc().equals(ficheDuDossier)) {
            throw new BusinessRuleException("La fiche " + ligne.getIdDmc() + " n'est pas celle du dossier examiné"
                    + (ficheDuDossier == null ? " (le dossier ne porte aucune fiche marché)" : " (fiche " + ficheDuDossier + ")")
                    + " : on n'observe pas la fiche d'un autre dossier.", "FICHE_HORS_DOSSIER");
        }
        if (cle == null) {
            return;
        }
        FicheMarcheService.AncrageChamp a = ficheService.ancrer(ligne.getIdDmc(), cle, prefixe + "champFiche");
        ligne.setChampFiche(a.cle());
        ligne.setLibelleChampFiche(a.libelle());
        ligne.setValeurChampFiche(a.valeur() != null && a.valeur().length() > 4000 ? a.valeur().substring(0, 4000)
                : a.valeur());
        ligne.setLot(LotsFiche.lotDe(a.cle()));
    }

    private static ChampsInvalidesException erreur(String champ, String message) {
        return new ChampsInvalidesException(List.of(new ErrorResponse.FieldError(champ, message)));
    }
}
