package cnm.prs.service;

import java.text.Normalizer;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.repository.SequenceReferenceRepository;

/**
 * Génère la référence officielle d'un dossier au format
 * {@code xxxxx/sous_type/code_localite/annee_exercice} (ex. {@code 00013/PPM/CRM-ANT/2026}). Le segment
 * central est le <strong>sous-type</strong> du dossier (PPM, PPM-AGPM, DAO, DAOR…) ; le compteur xxxxx
 * reste <strong>global par (famille, exercice)</strong> — strictement unique tous dossiers d'une même
 * famille confondus, indépendamment de l'entité ou de la localité ; il redémarre à 1 chaque année
 * (l'année figure dans la référence).
 */
@Service
public class ReferenceService {

    /** Clé {@code CODE_LOCALITE} du compteur de dossiers : valeur fixe → compteur global par (type, année). */
    private static final String COMPTEUR_GLOBAL = "DOSSIER";

    private final SequenceReferenceRepository repository;

    public ReferenceService(SequenceReferenceRepository repository) {
        this.repository = repository;
    }

    /**
     * ⚠️ Règle modifiée (2026-07-20) — le <strong>segment affiché</strong> ({@code segmentType}) est
     * désormais le <strong>sous-type</strong> du dossier (PPM, PPM-AGPM, DAO, DAOR…), tandis que la
     * <strong>clé du compteur</strong> ({@code cleCompteur}) reste la <strong>famille</strong> (DDP, DMC,
     * DDM…) : la numérotation continue est <strong>inchangée</strong> (une seule séquence par famille et
     * par année), seul le libellé du segment change. Découpler les deux évite qu'un PPM et un PPM-AGPM
     * repartent chacun à 1.
     *
     * @param segmentType   segment « type » affiché dans la référence — le sous-type du dossier
     * @param cleCompteur   clé du compteur (famille du dossier) — porte la continuité de la numérotation
     * @param localite      localité du dossier (utilisée seulement si non centrale)
     * @param estCentrale   true -> segment localité = "CNM" ; false -> "CRM-" + localite
     * @param anneeExercice exercice budgétaire
     */
    @Transactional
    public String generer(String segmentType, String cleCompteur, String localite, boolean estCentrale,
            int anneeExercice) {
        String codeLocalite = estCentrale ? "CNM" : "CRM-" + localite;   // segment affiché dans la référence
        // ⚠️ Règle ajoutée — compteur GLOBAL par (famille, année) : clé localité fixe « DOSSIER » (jamais la vraie
        // localité), pour que deux dossiers d'entités/localités différentes la même année aient des numéros distincts.
        if (repository.incrementerExistant(cleCompteur, COMPTEUR_GLOBAL, anneeExercice) == 0) {
            repository.creer(cleCompteur, COMPTEUR_GLOBAL, anneeExercice);
        }
        long valeur = repository.valeurCourante(cleCompteur, COMPTEUR_GLOBAL, anneeExercice);
        return String.format("%05d/%s/%s/%d", valeur, segmentType, codeLocalite, anneeExercice);
    }

    /** Clés du compteur GLOBAL dédié aux lettres de renvoi (par année), indépendant du dossier/localité. */
    private static final String TYPE_LETTRE = "LR";
    private static final String COMPTEUR_LETTRE = "LETTRE";

    /**
     * ⚠️ Règle ajoutée — numéro de séquence <strong>global</strong> des lettres de renvoi par année :
     * strictement unique et continu sur l'ensemble des lettres, indépendamment du dossier, de l'entité ou
     * de la localité. Redémarre chaque année. Compteur dédié (clé {@code (LR, LETTRE, année)} dans
     * {@code t_sequence_reference}), distinct de celui des dossiers.
     */
    @Transactional
    public long sequenceLettreRenvoi(int anneeExercice) {
        if (repository.incrementerExistant(TYPE_LETTRE, COMPTEUR_LETTRE, anneeExercice) == 0) {
            repository.creer(TYPE_LETTRE, COMPTEUR_LETTRE, anneeExercice);
        }
        return repository.valeurCourante(TYPE_LETTRE, COMPTEUR_LETTRE, anneeExercice);
    }

    private static final Set<String> MOTS_VIDES =
            Set.of("de", "du", "des", "la", "le", "les", "l", "d", "et", "a", "aux", "en", "pour", "sur", "par");

    /**
     * ⚠️ Règle ajoutée — référence PPM dérivée du libellé de l'entité :
     * {@code <seq>/<acronyme>/PPM/<année>}. Compteur par (acronyme entité, année) via la table générique
     * {@code t_sequence_reference} (clé {@code (PPM_REF, acronyme, année)} — distincte des réf. de réception).
     */
    @Transactional
    public String genererPpm(String libelleEntite, int annee) {
        String code = acronymeEntite(libelleEntite);
        if (repository.incrementerExistant("PPM_REF", code, annee) == 0) {
            repository.creer("PPM_REF", code, annee);
        }
        long valeur = repository.valeurCourante("PPM_REF", code, annee);
        return String.format("%05d/%s/PPM/%d", valeur, code, annee);
    }

    /** Acronyme = initiales (sans accent) des mots significatifs du libellé. « Direction Générale du Budget » → « DGB ». */
    private String acronymeEntite(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            return "ENT";
        }
        StringBuilder sb = new StringBuilder();
        for (String mot : libelle.trim().split("\\s+")) {
            String lettres = Normalizer.normalize(mot, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "").replaceAll("[^A-Za-z]", "");
            if (lettres.isEmpty() || MOTS_VIDES.contains(lettres.toLowerCase())) {
                continue;
            }
            sb.append(Character.toUpperCase(lettres.charAt(0)));
        }
        return sb.length() == 0 ? "ENT" : sb.toString();
    }
}
