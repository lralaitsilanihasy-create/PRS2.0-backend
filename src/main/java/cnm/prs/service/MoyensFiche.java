package cnm.prs.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.MaterielExigeDto;
import cnm.prs.dto.PersonnelExigeDto;
import cnm.prs.entity.FicheMateriel;
import cnm.prs.entity.FichePersonnel;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.repository.FicheMaterielRepository;
import cnm.prs.repository.FichePersonnelRepository;

/**
 * ⚠️ V60 (demande front du 2026-10-03, matériel et personnel des travaux) — les deux listes « moyens » d'une version de
 * fiche DAO de travaux : le <strong>matériel</strong> et le <strong>personnel clé</strong> exigés à la clause 6.3 du
 * DPAO-T. Comme le besoin ({@link BesoinFiche}) : remplacées en bloc (l'ordre est la position), figées à la validation,
 * copiées à la révision. Une seule liste par fiche ; {@code parLot} dit qu'une ligne vaut pour chaque lot. Les gardes
 * (profil, fiche validée, catégorie) sont celles de {@link FicheMarcheService}.
 *
 * <p>Impression : {@code {{MOYENS.materiel}}} et {@code {{MOYENS.personnel}}}, une ligne par entrée ({@link #jetons}).</p>
 */
@Component
@Transactional
public class MoyensFiche {

    /** Les jetons rendus par l'appelant dans la map {@code publication} des modèles. */
    public static final String JETON_MATERIEL = "MOYENS.materiel";
    public static final String JETON_PERSONNEL = "MOYENS.personnel";

    private final FicheMaterielRepository materielRepository;
    private final FichePersonnelRepository personnelRepository;

    public MoyensFiche(FicheMaterielRepository materielRepository, FichePersonnelRepository personnelRepository) {
        this.materielRepository = materielRepository;
        this.personnelRepository = personnelRepository;
    }

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public List<MaterielExigeDto> materiel(Integer idFiche) {
        if (idFiche == null) {
            return List.of();
        }
        return materielRepository.findByIdFicheOrderByOrdreAsc(idFiche).stream().map(m -> new MaterielExigeDto(
                m.getIdMateriel(), m.getOrdre(), m.getDesignation(), m.getCaracteristique(), m.getNombre(),
                m.getMinimumEnPropre(), m.isParLot())).toList();
    }

    @Transactional(readOnly = true)
    public List<PersonnelExigeDto> personnel(Integer idFiche) {
        if (idFiche == null) {
            return List.of();
        }
        return personnelRepository.findByIdFicheOrderByOrdreAsc(idFiche).stream().map(p -> new PersonnelExigeDto(
                p.getIdPersonnel(), p.getOrdre(), p.getPoste(), p.getNombre(), p.getDiplome(), p.getExperienceAnnees(),
                p.getDomaineExperience(), p.getJustificatifs(), p.isParLot())).toList();
    }

    // ------------------------------------------------------------------ validation (400 nominatifs)

    /** §B1.2 — désignation obligatoire (≤ 200), caractéristique ≤ 200, nombre ≥ 1, minimum en propre de 0 au nombre. */
    public static void validerMateriel(List<MaterielExigeDto> lignes) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            MaterielExigeDto m = lignes.get(i);
            String p = "materiel[" + i + "].";
            if (m == null) {
                erreurs.add(new ErrorResponse.FieldError("materiel[" + i + "]", "Ligne vide."));
                continue;
            }
            obligatoire(erreurs, p + "designation", m.getDesignation(), 200, "La désignation");
            facultatif(erreurs, p + "caracteristique", m.getCaracteristique(), 200, "La caractéristique");
            if (m.getNombre() == null || m.getNombre() < 1) {
                erreurs.add(new ErrorResponse.FieldError(p + "nombre", "Le nombre est obligatoire, au moins 1."));
            } else if (m.getMinimumEnPropre() != null && (m.getMinimumEnPropre() < 0 || m.getMinimumEnPropre() > m.getNombre())) {
                erreurs.add(new ErrorResponse.FieldError(p + "minimumEnPropre", "Le minimum en propre va de 0 au nombre exigé ("
                        + m.getNombre() + ")."));
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
    }

    /** §B1.3 — poste obligatoire (≤ 200), nombre ≥ 1 (1 s'il est absent), expérience ≥ 0, textes bornés. */
    public static void validerPersonnel(List<PersonnelExigeDto> lignes) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            PersonnelExigeDto x = lignes.get(i);
            String p = "personnel[" + i + "].";
            if (x == null) {
                erreurs.add(new ErrorResponse.FieldError("personnel[" + i + "]", "Ligne vide."));
                continue;
            }
            obligatoire(erreurs, p + "poste", x.getPoste(), 200, "Le poste");
            if (x.getNombre() != null && x.getNombre() < 1) {
                erreurs.add(new ErrorResponse.FieldError(p + "nombre", "Le nombre est d'au moins 1."));
            }
            facultatif(erreurs, p + "diplome", x.getDiplome(), 500, "Le diplôme");
            if (x.getExperienceAnnees() != null && x.getExperienceAnnees() < 0) {
                erreurs.add(new ErrorResponse.FieldError(p + "experienceAnnees", "L'expérience n'est pas négative."));
            }
            facultatif(erreurs, p + "domaineExperience", x.getDomaineExperience(), 200, "Le domaine d'expérience");
            facultatif(erreurs, p + "justificatifs", x.getJustificatifs(), 500, "Les justificatifs");
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
    }

    // ------------------------------------------------------------------ écriture

    public void remplacerMateriel(Integer idFiche, List<MaterielExigeDto> lignes) {
        materielRepository.deleteByIdFiche(idFiche);
        materielRepository.flush();
        int ordre = 0;
        for (MaterielExigeDto m : lignes) {
            materielRepository.save(new FicheMateriel(null, idFiche, ++ordre, m.getDesignation().trim(),
                    vide(m.getCaracteristique()), m.getNombre(), m.getMinimumEnPropre(), Boolean.TRUE.equals(m.getParLot())));
        }
    }

    public void remplacerPersonnel(Integer idFiche, List<PersonnelExigeDto> lignes) {
        personnelRepository.deleteByIdFiche(idFiche);
        personnelRepository.flush();
        int ordre = 0;
        for (PersonnelExigeDto p : lignes) {
            personnelRepository.save(new FichePersonnel(null, idFiche, ++ordre, p.getPoste().trim(),
                    p.getNombre() == null ? 1 : p.getNombre(), vide(p.getDiplome()), p.getExperienceAnnees(),
                    vide(p.getDomaineExperience()), vide(p.getJustificatifs()), Boolean.TRUE.equals(p.getParLot())));
        }
    }

    /** La révision copie les deux listes de la version précédente, comme ses valeurs et son besoin. */
    public void copier(Integer idFicheSource, Integer idFicheCible) {
        for (MaterielExigeDto m : materiel(idFicheSource)) {
            materielRepository.save(new FicheMateriel(null, idFicheCible, m.getOrdre(), m.getDesignation(),
                    m.getCaracteristique(), m.getNombre(), m.getMinimumEnPropre(), Boolean.TRUE.equals(m.getParLot())));
        }
        for (PersonnelExigeDto p : personnel(idFicheSource)) {
            personnelRepository.save(new FichePersonnel(null, idFicheCible, p.getOrdre(), p.getPoste(), p.getNombre(),
                    p.getDiplome(), p.getExperienceAnnees(), p.getDomaineExperience(), p.getJustificatifs(),
                    Boolean.TRUE.equals(p.getParLot())));
        }
    }

    // ------------------------------------------------------------------ impression (§B2)

    /**
     * Les valeurs des jetons {@code {{MOYENS.materiel}}} et {@code {{MOYENS.personnel}}}, une ligne par entrée (vrais sauts
     * de ligne) ; une liste vide n'en donne pas (le jeton s'imprime en pointillés).
     */
    public static Map<String, String> jetons(List<MaterielExigeDto> materiel, List<PersonnelExigeDto> personnel) {
        Map<String, String> m = new LinkedHashMap<>();
        if (materiel != null && !materiel.isEmpty()) {
            m.put(JETON_MATERIEL, String.join("\n", materiel.stream().map(MoyensFiche::ligne).toList()));
        }
        if (personnel != null && !personnel.isEmpty()) {
            m.put(JETON_PERSONNEL, String.join("\n", personnel.stream().map(MoyensFiche::ligne).toList()));
        }
        return m;
    }

    /**
     * « - Camions bennes ≥ 10 000 kg : 6, dont au moins 4 en propre » ; tout en propre : « : 1, en propre » ; sans minimum
     * (ou minimum 0) : « : 1 » ; par lot : « : 1 par lot ».
     */
    static String ligne(MaterielExigeDto m) {
        StringBuilder sb = new StringBuilder("- ").append(m.getDesignation());
        if (m.getCaracteristique() != null && !m.getCaracteristique().isBlank()) {
            sb.append(' ').append(m.getCaracteristique().trim());
        }
        sb.append(" : ").append(m.getNombre()).append(Boolean.TRUE.equals(m.getParLot()) ? " par lot" : "");
        Integer min = m.getMinimumEnPropre();
        if (min != null && min > 0) {
            sb.append(min.equals(m.getNombre()) ? ", en propre" : ", dont au moins " + min + " en propre");
        }
        return sb.toString();
    }

    /**
     * « - Conducteur de travaux (1) : ingénieur BTP ou génie civil ; au moins 5 ans d'expérience en travaux routiers ;
     * justificatifs : CV et diplôme certifié ». Les morceaux absents disparaissent avec leur séparateur ; par lot :
     * « (1 par lot) ». Le diplôme prend une minuscule initiale, sauf un sigle (« BTS… »).
     */
    static String ligne(PersonnelExigeDto p) {
        int nombre = p.getNombre() == null ? 1 : p.getNombre();
        List<String> morceaux = new ArrayList<>();
        if (p.getDiplome() != null && !p.getDiplome().isBlank()) {
            morceaux.add(minuscule(p.getDiplome().trim()));
        }
        if (p.getExperienceAnnees() != null) {
            int n = p.getExperienceAnnees();
            morceaux.add("au moins " + n + (n > 1 ? " ans" : " an") + " d'expérience"
                    + (p.getDomaineExperience() == null || p.getDomaineExperience().isBlank() ? ""
                            : " en " + p.getDomaineExperience().trim()));
        } else if (p.getDomaineExperience() != null && !p.getDomaineExperience().isBlank()) {
            morceaux.add("expérience en " + p.getDomaineExperience().trim());
        }
        if (p.getJustificatifs() != null && !p.getJustificatifs().isBlank()) {
            morceaux.add("justificatifs : " + p.getJustificatifs().trim());
        }
        return "- " + p.getPoste() + " (" + nombre + (Boolean.TRUE.equals(p.getParLot()) ? " par lot" : "") + ")"
                + (morceaux.isEmpty() ? "" : " : " + String.join(" ; ", morceaux));
    }

    private static String minuscule(String s) {
        if (s.length() > 1 && Character.isUpperCase(s.charAt(1))) {
            return s;   // un sigle garde ses capitales
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static void obligatoire(List<ErrorResponse.FieldError> erreurs, String champ, String v, int max, String nom) {
        if (v == null || v.isBlank()) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " est obligatoire."));
        } else if (v.trim().length() > max) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " : " + max + " caractères au plus."));
        }
    }

    private static void facultatif(List<ErrorResponse.FieldError> erreurs, String champ, String v, int max, String nom) {
        if (v != null && v.trim().length() > max) {
            erreurs.add(new ErrorResponse.FieldError(champ, nom + " : " + max + " caractères au plus."));
        }
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
