package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ExamenDetailDto;
import cnm.prs.dto.ObservationControleDto;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.ChampCible;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.repository.ExamenDetailRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.repository.ServiceBeneficiaireRepository;

/**
 * ⚠️ <strong>Cellule visée par une observation d'examen</strong> (demande front du 2026-09-14, V30) — le
 * validateur UNIQUE des trois champs {@code champ}, {@code idMarcheCible} et {@code idBenefCible}, appelé par
 * les deux portes qui écrivent une ligne « Au lieu de / Lire » : {@code /api/examen-details} (les lignes
 * portées par le résultat) et {@code /api/observation-controles} (une ligne seule, POST et PUT).
 *
 * <p><strong>Les règles, dans l'ordre où elles répondent</strong> (400 ciblé, le premier défaut de chaque
 * ligne) :</p>
 * <ol>
 *   <li>{@code champ} nul impose {@code idMarcheCible} et {@code idBenefCible} nuls — le CHECK de V30 tient
 *       le même invariant en base ;</li>
 *   <li>code inconnu, ou étranger au document de la portée du point : refusé ; la portée
 *       {@code SUPPRESSION} n'accepte aucun champ ({@link ChampCible#admisSur}) ;</li>
 *   <li>portée {@code LIGNE} : {@code idMarcheCible} facultatif et <strong>forcé</strong> à la ligne du
 *       résultat ; fourni et différent, 400 ;</li>
 *   <li>portées {@code DOSSIER}, {@code FICHE}, {@code AGPM} : {@code idMarcheCible} obligatoire dès qu'un
 *       champ est posé, et ligne du dossier examiné. Le serveur ne vérifie pas que la ligne figure dans la
 *       fiche ou l'AGPM : ces documents sont calculés côté front, leur règle n'est pas recopiée ici ;</li>
 *   <li>{@code idBenefCible} n'est admis qu'avec un code par bénéficiaire, et doit être un bénéficiaire de
 *       {@code idMarcheCible}.</li>
 * </ol>
 *
 * <p><strong>Un résultat LIGNE sans ligne de marché</strong> (examen historique, que
 * {@code ExamenDetailService} tolère) n'a rien à quoi forcer la cible : il suit la règle 4, comme un point
 * évalué une fois. Le verrou d'édition (modifiable jusqu'à {@code PV_SIGNE}) reste celui des services
 * appelants, inchangé.</p>
 */
@Component
@Transactional(readOnly = true)
public class ObservationCibleValidateur {

    private final PointsCtrlRepository pointsCtrlRepository;
    private final ExamenRepository examenRepository;
    private final ExamenDetailRepository examenDetailRepository;
    private final MarcheRepository marcheRepository;
    private final ServiceBeneficiaireRepository serviceBeneficiaireRepository;

    public ObservationCibleValidateur(PointsCtrlRepository pointsCtrlRepository, ExamenRepository examenRepository,
            ExamenDetailRepository examenDetailRepository, MarcheRepository marcheRepository,
            ServiceBeneficiaireRepository serviceBeneficiaireRepository) {
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.examenRepository = examenRepository;
        this.examenDetailRepository = examenDetailRepository;
        this.marcheRepository = marcheRepository;
        this.serviceBeneficiaireRepository = serviceBeneficiaireRepository;
    }

    /**
     * Lignes d'observation portées par un résultat ({@code /api/examen-details}) : erreurs ciblées sur
     * {@code observations[i].<champ>}. Complète la cible forcée des points LIGNE, en place dans le DTO.
     */
    public void validerResultat(ExamenDetailDto resultat) {
        List<ObservationControleDto> lignes = resultat.getObservations();
        if (lignes != null) {
            lignes.forEach(ObservationCibleValidateur::normaliser);
        }
        if (lignes == null || lignes.stream().allMatch(ObservationCibleValidateur::sansCible)) {
            return;   // aucun champ de cible : rien à lire, comportement d'avant la V30
        }
        Contexte contexte = contexte(resultat.getIdPtControle(), resultat.getIdDetail(), resultat.getIdExamen());
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            ObservationControleDto ligne = lignes.get(i);
            if (ligne != null) {
                valider(ligne, contexte, "observations[" + i + "].", erreurs);
            }
        }
        lever(erreurs);
    }

    /**
     * Ligne d'observation seule ({@code /api/observation-controles}, POST et PUT) : erreurs ciblées sur
     * {@code champ}, {@code idMarcheCible}, {@code idBenefCible}. Le contexte est lu sur le résultat
     * {@code idDetail} en place.
     *
     * <p>⚠️ Fusion de {@code main} (2026-09-15) — {@code ObservationControleService} appelle ce validateur
     * <strong>après</strong> les gardes d'{@link ExamenGarde} : un résultat introuvable y est déjà refusé (403
     * ou 409). Le 400 {@code idDetail} ci-dessous reste une défense pour tout autre appelant.</p>
     */
    public void validerLigne(ObservationControleDto ligne) {
        normaliser(ligne);
        if (ligne == null || sansCible(ligne)) {
            return;
        }
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        if (ligne.getChamp() == null) {
            // Règle 1 d'abord : elle ne demande aucun contexte.
            valider(ligne, null, "", erreurs);
            lever(erreurs);
            return;
        }
        ExamenDetail resultat = ligne.getIdDetail() == null ? null
                : examenDetailRepository.findById(ligne.getIdDetail()).orElse(null);
        if (resultat == null) {
            lever(List.of(new ErrorResponse.FieldError("idDetail",
                    "Point de contrôle d'examen introuvable : impossible de valider la cellule visée.")));
            return;
        }
        valider(ligne, contexte(resultat.getIdPtControle(), resultat.getIdDetail(), resultat.getIdExamen()), "",
                erreurs);
        lever(erreurs);
    }

    /** Ce qu'il faut savoir du résultat pour juger une cible : portée du point, ligne du résultat, dossier. */
    private record Contexte(PorteePointCtrl portee, String libellePoint, Integer ligneDuResultat, Integer idDossier) {
    }

    private Contexte contexte(Integer idPtControle, Integer ligneDuResultat, Integer idExamen) {
        PointsCtrl point = idPtControle == null ? null : pointsCtrlRepository.findById(idPtControle).orElse(null);
        PorteePointCtrl portee = point == null ? null
                : (point.getPortee() == null ? PorteePointCtrl.LIGNE : point.getPortee());
        Integer idDossier = idExamen == null ? null : examenRepository.findIdDossierByExamen(idExamen).orElse(null);
        return new Contexte(portee, point == null ? null : point.getLibelPointCtrl(), ligneDuResultat, idDossier);
    }

    /** Une ligne, les cinq règles ; au plus une erreur par ligne (la première rencontrée). */
    private void valider(ObservationControleDto ligne, Contexte contexte, String prefixe,
            List<ErrorResponse.FieldError> erreurs) {
        // ① Pas de champ : pas de cible.
        if (ligne.getChamp() == null) {
            if (ligne.getIdMarcheCible() != null) {
                erreurs.add(erreur(prefixe, "idMarcheCible",
                        "Une ligne de marché ciblée exige un champ (« champ ») : sans cellule, pas de cible."));
            } else if (ligne.getIdBenefCible() != null) {
                erreurs.add(erreur(prefixe, "idBenefCible",
                        "Un bénéficiaire ciblé exige un champ (« champ ») : sans cellule, pas de cible."));
            }
            return;
        }

        // ② Code connu, et admis sur la portée du point.
        ChampCible champ = ChampCible.depuisCode(ligne.getChamp());
        if (champ == null) {
            erreurs.add(erreur(prefixe, "champ", "Champ inconnu : « " + ligne.getChamp()
                    + " ». Les codes admis sont ceux de la liste fermée (colonnes du PPM, « derogatoires. », "
                    + "« delaisAmenages. », « contratsCadres. » pour la fiche, « agpm. » pour l'AGPM)."));
            return;
        }
        PorteePointCtrl portee = contexte.portee();
        if (portee == PorteePointCtrl.SUPPRESSION) {
            erreurs.add(erreur(prefixe, "champ", "Le point « " + contexte.libellePoint() + " » constate une ligne "
                    + "retirée (portée SUPPRESSION) : il n'accepte aucune cellule ciblée."));
            return;
        }
        if (!champ.admisSur(portee)) {
            erreurs.add(erreur(prefixe, "champ", "Le champ « " + champ.getCode() + " » appartient au document "
                    + champ.document().name() + " : il ne peut pas être ciblé sur un point de portée "
                    + (portee == null ? "inconnue" : portee.name()) + "."));
            return;
        }

        // ③ / ④ La ligne de marché.
        if (portee == PorteePointCtrl.LIGNE && contexte.ligneDuResultat() != null) {
            if (ligne.getIdMarcheCible() == null) {
                ligne.setIdMarcheCible(contexte.ligneDuResultat());   // forcée : c'est la ligne du résultat
            } else if (!ligne.getIdMarcheCible().equals(contexte.ligneDuResultat())) {
                erreurs.add(erreur(prefixe, "idMarcheCible", "Le résultat porte sur la ligne de marché "
                        + contexte.ligneDuResultat() + " : l'observation ne peut pas viser la ligne "
                        + ligne.getIdMarcheCible() + "."));
                return;
            }
        } else {
            if (ligne.getIdMarcheCible() == null) {
                erreurs.add(erreur(prefixe, "idMarcheCible", "La ligne de marché visée est obligatoire pour "
                        + "cibler le champ « " + champ.getCode() + " » : ce point s'évalue une seule fois, sans "
                        + "ligne propre."));
                return;
            }
            boolean ligneDuDossier = contexte.idDossier() != null && marcheRepository.findById(ligne.getIdMarcheCible())
                    .map(m -> contexte.idDossier().equals(m.getIdDossier())).orElse(false);
            if (!ligneDuDossier) {
                erreurs.add(erreur(prefixe, "idMarcheCible", "La ligne de marché " + ligne.getIdMarcheCible()
                        + " n'appartient pas au dossier examiné."));
                return;
            }
        }

        // ⑤ Le bénéficiaire.
        if (ligne.getIdBenefCible() != null) {
            if (!champ.parBeneficiaire()) {
                erreurs.add(erreur(prefixe, "idBenefCible", "Le champ « " + champ.getCode() + " » n'est pas une "
                        + "colonne par bénéficiaire : aucun bénéficiaire ne peut y être ciblé."));
                return;
            }
            boolean benefDeLaLigne = serviceBeneficiaireRepository.findById(ligne.getIdBenefCible())
                    .map(b -> Objects.equals(b.getIdDetail(), ligne.getIdMarcheCible())).orElse(false);
            if (!benefDeLaLigne) {
                erreurs.add(erreur(prefixe, "idBenefCible", "Le bénéficiaire " + ligne.getIdBenefCible()
                        + " n'est pas un bénéficiaire de la ligne de marché " + ligne.getIdMarcheCible() + "."));
            }
        }
    }

    /** Un champ vide ou blanc vaut absent : le front qui efface la cellule ne doit pas écrire « » en base. */
    private static void normaliser(ObservationControleDto ligne) {
        if (ligne != null && ligne.getChamp() != null) {
            String code = ligne.getChamp().trim();
            ligne.setChamp(code.isEmpty() ? null : code);
        }
    }

    private static boolean sansCible(ObservationControleDto ligne) {
        return ligne == null
                || (ligne.getChamp() == null && ligne.getIdMarcheCible() == null && ligne.getIdBenefCible() == null);
    }

    private static ErrorResponse.FieldError erreur(String prefixe, String champ, String message) {
        return new ErrorResponse.FieldError(prefixe + champ, message);
    }

    private static void lever(List<ErrorResponse.FieldError> erreurs) {
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(List.copyOf(erreurs));
        }
    }
}
