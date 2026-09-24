package cnm.prs.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ChampFicheMarcheDto;
import cnm.prs.dto.ReferentielFicheMarcheDto;
import cnm.prs.entity.BlocFicheMarche;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.RubriqueFicheMarche;
import cnm.prs.enums.SourceChampFiche;
import cnm.prs.enums.TypeChampFiche;
import cnm.prs.enums.CategorieDao;
import cnm.prs.enums.TypeMarcheDao;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.BlocFicheMarcheRepository;
import cnm.prs.repository.ChampFicheMarcheRepository;
import cnm.prs.repository.RubriqueFicheMarcheRepository;

/**
 * ⚠️ Fiche marché DAO (demande front du 2026-09-22, §B1) — le référentiel {@code champs-fiche-marche} : la structure
 * (blocs, rubriques, figés par migration) et les champs (administrables : POST/PUT Administrateur, pas de DELETE —
 * un champ se <em>désactive</em>, les valeurs saisies sous un ancien code restent lisibles).
 *
 * <p>L'<strong>outil d'import</strong> du fichier de correspondance ({@link #importerCsv(Path)}) est hors API : il
 * se lance au démarrage sur {@code app.fiche-marche.import-csv=<chemin>} ({@code seed.ChampsFicheMarcheImport}).
 * Idempotent par code : un code connu est mis à jour, un code neuf créé, une ligne fautive rejetée avec sa raison
 * sans arrêter le reste.</p>
 */
@Service
@Transactional
public class ChampFicheMarcheService {

    private static final List<String> DOCUMENTS = List.of("DPAO", "DPAC", "DPIC", "AE", "CCAP", "AUCUN");   // DPIC : V42

    private final BlocFicheMarcheRepository blocRepository;
    private final RubriqueFicheMarcheRepository rubriqueRepository;
    private final ChampFicheMarcheRepository champRepository;

    public ChampFicheMarcheService(BlocFicheMarcheRepository blocRepository,
            RubriqueFicheMarcheRepository rubriqueRepository, ChampFicheMarcheRepository champRepository) {
        this.blocRepository = blocRepository;
        this.rubriqueRepository = rubriqueRepository;
        this.champRepository = champRepository;
    }

    /**
     * Structure et champs actifs d'un type de marché et d'une catégorie ({@code null} = pas de filtre sur cet axe ; les
     * deux nuls = tout, y compris les champs inactifs — la vue d'administration).
     *
     * <p>⚠️ Lots 4 et 5 — une rubrique (un bloc) est servie si elle relève du type et de la catégorie demandés ET si au
     * moins un de ses champs ACTIFS vaut pour les deux ; une rubrique qui n'a encore AUCUN champ (référentiel à compléter)
     * reste servie, c'est ainsi que l'écran le signale.</p>
     */
    @Transactional(readOnly = true)
    public ReferentielFicheMarcheDto referentiel(String typeMarche, String categorie) {
        String type = typeMarche == null || typeMarche.isBlank() ? null : typeMarche.trim().toUpperCase();
        if (type != null && Arrays.stream(TypeMarcheDao.values()).noneMatch(t -> t.name().equals(type))) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError("typeMarche",
                    "Type de marché inconnu : " + typeMarche + " (QUANTITE_FIXE, A_COMMANDE, CONTRAT_CADRE).")));
        }
        String cat = categorie == null || categorie.isBlank() ? null : categorie.trim().toUpperCase();
        if (cat != null && Arrays.stream(CategorieDao.values()).noneMatch(t -> t.name().equals(cat))) {
            throw new ChampsInvalidesException(List.of(new ErrorResponse.FieldError("categorie",
                    "Catégorie inconnue : " + categorie + " (" + CategorieDao.toutes().replace(",", ", ") + ").")));
        }
        boolean filtre = type != null || cat != null;
        java.util.Set<String> avecChamps = new java.util.HashSet<>();
        java.util.Set<String> avecChampsVoulus = new java.util.HashSet<>();
        if (filtre) {
            for (ChampFicheMarche c : champRepository.findAllByOrderByCodeRubriqueAscRangAsc()) {
                avecChamps.add(c.getCodeRubrique());
                // ⚠️ 2026-09-24 — un champ inactif ne fait pas servir sa rubrique : une rubrique dont tous les champs sont
                // inactifs n'affichait qu'un titre (B09-FC des prestations intellectuelles) ; elle revient si l'un est réactivé.
                if (Boolean.TRUE.equals(c.getActif()) && c.pourTypeMarche(type) && c.pourCategorie(cat)) {
                    avecChampsVoulus.add(c.getCodeRubrique());
                }
            }
        }
        Map<String, List<RubriqueFicheMarche>> rubriquesParBloc = rubriqueRepository.findAllByOrderByCodeBlocAscRangAsc()
                .stream().filter(r -> !filtre || (admet(r.getTypesMarche(), type) && admet(r.getCategories(), cat)
                        && (avecChampsVoulus.contains(r.getCode()) || !avecChamps.contains(r.getCode()))))
                .collect(Collectors.groupingBy(RubriqueFicheMarche::getCodeBloc));
        List<ReferentielFicheMarcheDto.BlocDto> blocs = new ArrayList<>();
        for (BlocFicheMarche b : blocRepository.findAllByOrderByRangAsc()) {
            if (!admet(b.getTypesMarche(), type) || !admet(b.getCategories(), cat)) {
                continue;
            }
            List<ReferentielFicheMarcheDto.RubriqueDto> rubriques = rubriquesParBloc.getOrDefault(b.getCode(), List.of())
                    .stream().map(r -> new ReferentielFicheMarcheDto.RubriqueDto(r.getCode(), r.getLibelle(), r.getRang(),
                            r.getDocumentMaitre(), r.getNbAttendu())).toList();
            blocs.add(new ReferentielFicheMarcheDto.BlocDto(b.getCode(), b.getLibelle(), b.getRang(), rubriques));
        }
        List<ChampFicheMarche> champs = !filtre ? champRepository.findAllByOrderByCodeRubriqueAscRangAsc()
                : champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc().stream()
                        .filter(c -> c.pourTypeMarche(type) && c.pourCategorie(cat)).toList();
        return new ReferentielFicheMarcheDto(blocs, champs.stream().map(ChampFicheMarcheService::toDto).toList());
    }

    /** La liste {@code csv} contient la valeur ({@code null} : pas de filtre). */
    private static boolean admet(String csv, String valeur) {
        return valeur == null || ChampFicheMarche.liste(csv).contains(valeur);
    }

    /** Champs actifs d'un type de marché, dans l'ordre d'affichage — ce que la fiche évalue. */
    @Transactional(readOnly = true)
    public List<ChampFicheMarche> champsActifs(String typeMarche) {
        return champRepository.findByActifTrueOrderByCodeRubriqueAscRangAsc().stream()
                .filter(c -> c.pourTypeMarche(typeMarche)).toList();
    }

    public ChampFicheMarcheDto creer(ChampFicheMarcheDto dto) {
        String code = dto.getCode() == null ? null : dto.getCode().trim().toUpperCase();
        if (code != null && champRepository.existsById(code)) {
            throw new BusinessRuleException("Le champ " + code + " existe déjà : modifiez-le (PUT).", "CHAMP_EXISTANT");
        }
        ChampFicheMarche c = new ChampFicheMarche();
        c.setCode(code);
        appliquer(c, dto);
        return toDto(champRepository.save(c));
    }

    public ChampFicheMarcheDto modifier(String code, ChampFicheMarcheDto dto) {
        ChampFicheMarche c = champRepository.findById(code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Champ introuvable : " + code));
        dto.setCode(c.getCode());
        appliquer(c, dto);
        return toDto(champRepository.save(c));
    }

    /**
     * Copie et valide le corps sur l'entité ; 400 nominatif par attribut fautif. La rubrique est lue sur le code
     * (« B05-GS-02 » → « B05-GS ») et doit exister ; la condition doit se lire (chaque terme {@code cle = VALEUR}).
     */
    private void appliquer(ChampFicheMarche c, ChampFicheMarcheDto dto) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        String code = c.getCode();
        String rubrique = code == null || code.lastIndexOf('-') < 0 ? null : code.substring(0, code.lastIndexOf('-'));
        Optional<RubriqueFicheMarche> r = rubrique == null ? Optional.empty() : rubriqueRepository.findById(rubrique);
        if (r.isEmpty()) {
            erreurs.add(new ErrorResponse.FieldError("code", "Aucune rubrique « " + rubrique + " » : le code doit "
                    + "commencer par le code d'une rubrique existante (B05-GS-02 → B05-GS)."));
        }
        String type = dto.getType() == null ? null : dto.getType().trim().toUpperCase();
        if (type == null || Arrays.stream(TypeChampFiche.values()).noneMatch(t -> t.name().equals(type))) {
            erreurs.add(new ErrorResponse.FieldError("type", "Type inconnu : " + dto.getType() + " ("
                    + Arrays.stream(TypeChampFiche.values()).map(Enum::name).collect(Collectors.joining(", ")) + ")."));
        }
        String source = dto.getSource() == null ? null : dto.getSource().trim().toUpperCase();
        if (source == null || Arrays.stream(SourceChampFiche.values()).noneMatch(s -> s.name().equals(source))) {
            erreurs.add(new ErrorResponse.FieldError("source", "Source inconnue : " + dto.getSource() + " (PPM, SAISIE, CADRAGE)."));
        }
        if (SourceChampFiche.PPM.name().equals(source) && (dto.getClePpm() == null || dto.getClePpm().isBlank())) {
            erreurs.add(new ErrorResponse.FieldError("clePpm", "Un champ de source PPM nomme la clé reprise du plan."));
        }
        if (SourceChampFiche.CADRAGE.name().equals(source) && (dto.getCleCadrage() == null || dto.getCleCadrage().isBlank())) {
            erreurs.add(new ErrorResponse.FieldError("cleCadrage", "Un champ de source CADRAGE nomme la clé de cadrage reflétée."));
        }
        String doc = dto.getDocumentMaitre() == null || dto.getDocumentMaitre().isBlank() ? "AUCUN"
                : dto.getDocumentMaitre().trim().toUpperCase();
        if (!DOCUMENTS.contains(doc)) {
            erreurs.add(new ErrorResponse.FieldError("documentMaitre", "Document inconnu : " + dto.getDocumentMaitre()
                    + " (" + String.join(", ", DOCUMENTS) + ")."));
        }
        List<String> types = dto.getTypesMarche() == null || dto.getTypesMarche().isEmpty()
                ? Arrays.stream(TypeMarcheDao.values()).map(Enum::name).toList()
                : dto.getTypesMarche().stream().map(s -> s.trim().toUpperCase()).toList();
        for (String t : types) {
            if (Arrays.stream(TypeMarcheDao.values()).noneMatch(x -> x.name().equals(t))) {
                erreurs.add(new ErrorResponse.FieldError("typesMarche", "Type de marché inconnu : " + t + "."));
            }
        }
        // ⚠️ Lot 5 (2026-09-24) — catégories : absentes = défaut de l'entité (FOURNITURES_SERVICES à la création, inchangées
        // à la modification) ; présentes = validées.
        List<String> categories = dto.getCategories() == null ? List.of()
                : dto.getCategories().stream().map(x -> x.trim().toUpperCase()).filter(x -> !x.isEmpty()).toList();
        for (String x : categories) {
            if (Arrays.stream(CategorieDao.values()).noneMatch(v -> v.name().equals(x))) {
                erreurs.add(new ErrorResponse.FieldError("categories", "Catégorie inconnue : " + x + " ("
                        + CategorieDao.toutes().replace(",", ", ") + ")."));
            }
        }
        if (!ConditionCadrage.lisible(dto.getCondition())) {
            erreurs.add(new ErrorResponse.FieldError("condition", "Condition illisible : attendu « cle = VALEUR » ou "
                    + "« cle != VALEUR » (valeur en lettres, chiffres ou _), combinés par « et » / « ou » "
                    + "(ex. « garantieSoumission = OUI et avance = OUI »)."));
        }
        if (dto.getControle() != null && !dto.getControle().isBlank()
                && !dto.getControle().trim().toUpperCase().matches("[A-Z0-9_]+(:[A-Z0-9_]+)?")) {
            erreurs.add(new ErrorResponse.FieldError("controle", "Contrôle attendu sous la forme REGLE ou REGLE:ROLE."));
        }
        // ⚠️ Lot 4 (2026-09-23) — une LISTE de source CADRAGE est un reflet : ses options sont celles de la question de
        // cadrage (le fichier du contrat-cadre en charge trois ainsi, comme les reflets semés par V35).
        if (TypeChampFiche.LISTE.name().equals(type) && !SourceChampFiche.CADRAGE.name().equals(source)
                && (dto.getOptions() == null || dto.getOptions().isEmpty())) {
            erreurs.add(new ErrorResponse.FieldError("options", "Un champ LISTE déclare ses options."));
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        c.setCodeRubrique(r.get().getCode());
        c.setRang(dto.getRang() != null ? dto.getRang() : rangDuCode(code));
        c.setLibelle(dto.getLibelle().trim());
        c.setType(type);
        c.setSource(source);
        c.setDocumentMaitre(doc);
        c.setReprises(csv(dto.getReprises()));
        c.setTypesMarche(String.join(",", types));
        if (!categories.isEmpty()) {
            c.setCategories(String.join(",", categories));
        }
        c.setCondition(vide(dto.getCondition()) ? null : dto.getCondition().trim());
        c.setObligatoire(Boolean.TRUE.equals(dto.getObligatoire()));
        c.setTexteType(vide(dto.getTexteType()) ? null : dto.getTexteType().trim());
        c.setControle(vide(dto.getControle()) ? null : dto.getControle().trim().toUpperCase());
        c.setOptions(csv(dto.getOptions()));
        c.setCleCadrage(vide(dto.getCleCadrage()) ? null : dto.getCleCadrage().trim());
        c.setClePpm(vide(dto.getClePpm()) ? null : dto.getClePpm().trim().toUpperCase());
        c.setActif(dto.getActif() == null || dto.getActif());
    }

    private static int rangDuCode(String code) {
        try {
            return Integer.parseInt(code.substring(code.lastIndexOf('-') + 1));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }

    private static String csv(List<String> l) {
        if (l == null || l.isEmpty()) {
            return null;
        }
        String s = l.stream().map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.joining(","));
        return s.isEmpty() ? null : s;
    }

    public static ChampFicheMarcheDto toDto(ChampFicheMarche c) {
        return new ChampFicheMarcheDto(c.getCode(), c.codeBloc(), c.getCodeRubrique(), c.getRang(), c.getLibelle(),
                c.getType(), c.getSource(), c.getDocumentMaitre(), ChampFicheMarche.liste(c.getReprises()),
                ChampFicheMarche.liste(c.getTypesMarche()), c.getCondition(), c.getObligatoire(), c.getTexteType(),
                c.getControle(), ChampFicheMarche.liste(c.getOptions()), c.getCleCadrage(), c.getClePpm(), c.getActif(),
                ChampFicheMarche.liste(c.getCategories()));
    }

    // ------------------------------------------------------------------ import CSV (hors API)

    /** Bilan d'un import : codes créés, mis à jour, et lignes rejetées avec leur raison. */
    public record BilanImport(List<String> crees, List<String> misAJour, List<String> rejets) {
    }

    /**
     * Fichier de correspondance en CSV (UTF-8, séparateur « ; », une ligne d'en-tête). Colonnes reconnues par leur
     * nom d'en-tête, dans n'importe quel ordre : {@code code}, {@code libelle}, {@code type}, {@code source},
     * {@code documentMaitre}, {@code reprises}, {@code typesMarche}, {@code condition}, {@code obligatoire}
     * ({@code oui}/{@code non}), {@code texteType}, {@code controle}, {@code options}, {@code cleCadrage},
     * {@code clePpm}, {@code rang}, {@code actif}, ⚠️ lot 5 {@code categories} (absente : {@code FOURNITURES_SERVICES}
     * pour un champ créé, inchangée pour un champ mis à jour). Les listes sont séparées par des virgules dans la cellule.
     */
    public BilanImport importerCsv(Path fichier) throws IOException {
        List<String> lignes = Files.readAllLines(fichier, StandardCharsets.UTF_8);
        List<String> crees = new ArrayList<>();
        List<String> misAJour = new ArrayList<>();
        List<String> rejets = new ArrayList<>();
        if (lignes.isEmpty()) {
            return new BilanImport(crees, misAJour, rejets);
        }
        List<String> entetes = Arrays.stream(lignes.get(0).replace("﻿", "").split(";")).map(String::trim).toList();
        for (int i = 1; i < lignes.size(); i++) {
            String brute = lignes.get(i);
            if (brute.isBlank()) {
                continue;
            }
            String[] cellules = brute.split(";", -1);
            Map<String, String> l = new java.util.HashMap<>();
            for (int k = 0; k < entetes.size() && k < cellules.length; k++) {
                l.put(entetes.get(k), cellules[k].trim());
            }
            String code = l.getOrDefault("code", "").toUpperCase();
            try {
                ChampFicheMarcheDto dto = new ChampFicheMarcheDto();
                dto.setCode(code);
                dto.setLibelle(l.get("libelle"));
                dto.setType(l.get("type"));
                dto.setSource(l.getOrDefault("source", "SAISIE"));
                dto.setDocumentMaitre(l.get("documentMaitre"));
                dto.setReprises(ChampFicheMarche.liste(l.get("reprises")));
                dto.setTypesMarche(ChampFicheMarche.liste(l.get("typesMarche")));
                dto.setCondition(l.get("condition"));
                dto.setObligatoire(ouiNon(l.get("obligatoire")));
                dto.setTexteType(l.get("texteType"));
                dto.setControle(l.get("controle"));
                dto.setOptions(ChampFicheMarche.liste(l.get("options")));
                dto.setCleCadrage(l.get("cleCadrage"));
                dto.setClePpm(l.get("clePpm"));
                dto.setCategories(ChampFicheMarche.liste(l.get("categories")));   // lot 5 : absente = défaut
                dto.setRang(vide(l.get("rang")) ? null : Integer.valueOf(l.get("rang")));
                dto.setActif(vide(l.get("actif")) ? Boolean.TRUE : ouiNon(l.get("actif")));
                if (vide(code) || !code.matches("B\\d{2}-[A-Z0-9]{1,6}-\\d{2}")) {
                    throw new IllegalArgumentException("code attendu de la forme B05-GS-02");
                }
                if (vide(dto.getLibelle())) {
                    throw new IllegalArgumentException("libellé vide");
                }
                if (champRepository.existsById(code)) {
                    modifier(code, dto);
                    misAJour.add(code);
                } else {
                    creer(dto);
                    crees.add(code);
                }
            } catch (ChampsInvalidesException e) {
                rejets.add("ligne " + (i + 1) + " (" + code + ") : " + e.getErreurs().stream()
                        .map(f -> f.champ() + " — " + f.message()).collect(Collectors.joining(" ; ")));
            } catch (RuntimeException e) {
                rejets.add("ligne " + (i + 1) + " (" + code + ") : " + e.getMessage());
            }
        }
        return new BilanImport(crees, misAJour, rejets);
    }

    private static Boolean ouiNon(String s) {
        if (s == null) {
            return Boolean.FALSE;
        }
        String v = s.trim().toLowerCase();
        return v.equals("oui") || v.equals("o") || v.equals("true") || v.equals("1") || v.equals("x");
    }
}
