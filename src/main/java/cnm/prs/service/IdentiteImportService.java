package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.dto.ImportDaoResult;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.ImportVerdict;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.ImportVerdictRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.MinistereRepository;
import cnm.prs.repository.OrganigrammeRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-17 (demande front « identite-dao-importe », §B1, §B2, §B4) — le verdict d'identité d'un DAO importé et sa garde.
 *
 * <ul>
 *   <li><strong>Juger</strong> : un DAO marqué par l'application ({@link MarqueDao}) l'est d'emblée, conforme sur sa ligne (même filiation),
 *       étranger ailleurs ; sinon {@link IdentiteDao} cherche les valeurs du plan de la ligne en vigueur, et compare l'objet aux lignes
 *       des plans de la même autorité, pour l'exercice et le précédent (hors la filiation de la ligne, une ligne par filiation).</li>
 *   <li><strong>Garder</strong> (H1) : le verdict de chaque lecture, par fiche, empreinte et utilisateur, quatre heures
 *       ({@link #DUREE_HEURES}) ; purgé au bout d'un jour.</li>
 *   <li><strong>Exiger</strong> (B2) : à l'application, 409 {@code IMPORT_EXPIRE} sans lecture valable, {@code DAO_ETRANGER} pour un DAO
 *       étranger, {@code ATTESTATION_REQUISE} pour un DAO non prouvé que la PRMP n'atteste pas.</li>
 * </ul>
 */
@Service
public class IdentiteImportService {

    /** Durée de validité d'une lecture : le temps de la revue. */
    static final int DUREE_HEURES = 4;

    private final ValeursPpmService valeursPpm;
    private final MarcheRepository marcheRepository;
    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final EntiteContractRepository entiteRepository;
    private final OrganigrammeRepository organigrammeRepository;
    private final MinistereRepository ministereRepository;
    private final LotRepository lotRepository;
    private final ImportVerdictRepository verdictRepository;
    private final cnm.prs.repository.DocumentFicheMarcheRepository documentRepository;

    public IdentiteImportService(ValeursPpmService valeursPpm, MarcheRepository marcheRepository, DossierRepository dossierRepository,
            PpmRepository ppmRepository, EntiteContractRepository entiteRepository, OrganigrammeRepository organigrammeRepository,
            MinistereRepository ministereRepository, LotRepository lotRepository, ImportVerdictRepository verdictRepository,
            cnm.prs.repository.DocumentFicheMarcheRepository documentRepository) {
        this.documentRepository = documentRepository;
        this.valeursPpm = valeursPpm;
        this.marcheRepository = marcheRepository;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.entiteRepository = entiteRepository;
        this.organigrammeRepository = organigrammeRepository;
        this.ministereRepository = ministereRepository;
        this.lotRepository = lotRepository;
        this.verdictRepository = verdictRepository;
    }

    // ------------------------------------------------------------------ juger (B1, B4)

    @Transactional(readOnly = true)
    public ImportDaoResult.Identite juger(FicheMarcheDto fiche, String nomFichier, byte[] contenu, IdentiteDao.Texte texte,
            Set<String> divergences) {
        ValeursPpmService.ValeursPpm ppm = valeursPpm.lire(fiche.getIdDetail());
        Marche ligne = ppm.ligne();
        Map<String, String> v = ppm.valeurs();
        if (ligne == null) {
            return new ImportDaoResult.Identite(IdentiteDao.NON_PROUVE, List.of(), null,
                    "La ligne du plan de la fiche est introuvable : rien ne prouve que ce DAO la concerne.");
        }
        // B4 — la marque ne fait foi que sur le fichier tel que l'application l'a produit : un DAO repris dans Word pour une autre ligne
        // garde ses propriétés, il est alors jugé comme tout autre document.
        Optional<MarqueDao.Marque> marque = MarqueDao.lire(nomFichier, contenu)
                .filter(m -> documentRepository.existsByEmpreinteAndType(ImportDaoService.empreinte(contenu), DaoCompletService.TYPE));
        if (marque.isPresent()) {
            Marche marquee = marcheRepository.findById(marque.get().idDetail()).orElse(null);
            if (marquee != null) {
                if (Objects.equals(marquee.getIdLigneOrigine(), ligne.getIdLigneOrigine())) {
                    return new ImportDaoResult.Identite(IdentiteDao.CONFORME, List.of(), null, "Ce DAO a été produit par l'application pour "
                            + "cette ligne du plan (fiche " + marque.get().idFiche() + ", version " + marque.get().version() + ") : il est "
                            + "reconnu à sa marque.");
                }
                return new ImportDaoResult.Identite(IdentiteDao.ETRANGER, List.of(),
                        new ImportDaoResult.Voisine(marquee.getIdDetail(), marquee.getDesignationMarche()),
                        "Ce DAO n'est pas celui de la ligne « " + ligne.getDesignationMarche() + " » : l'application l'a produit pour la ligne "
                                + marquee.getIdDetail() + " « " + marquee.getDesignationMarche() + " » (marque du document).");
            }
        }
        Dossier dossier = ligne.getIdDossier() == null ? null : dossierRepository.findById(ligne.getIdDossier()).orElse(null);
        EntiteContract entite = dossier == null || dossier.getIdEntiteContract() == null ? null
                : entiteRepository.findById(dossier.getIdEntiteContract()).orElse(null);
        List<String> autorites = new ArrayList<>();
        List<String> sigles = new ArrayList<>();
        ajouter(autorites, v.get("ENTITE"));
        ajouter(autorites, v.get("MINISTERE"));
        ajouter(sigles, entite == null ? null : entite.getSigle());
        ajouter(sigles, entite == null || entite.getIdOrganigramme() == null ? null : organigrammeRepository.findById(entite.getIdOrganigramme())
                .filter(o -> o.getIdMinistere() != null).flatMap(o -> ministereRepository.findById(o.getIdMinistere()))
                .map(m -> m.getSigle()).orElse(null));
        int nbLots = LotsFiche.nbLots(v);
        boolean alloti = LotsFiche.alloti(nbLots);
        List<String> lots = alloti ? designations(ligne.getIdDetail()) : List.of();
        String forme = "CONTRAT_CADRE".equals(fiche.getTypeMarche()) ? v.get("FORME_MARCHE") : null;
        IdentiteDao.Plan plan = new IdentiteDao.Plan(ligne.getDesignationMarche(), autorites, sigles, v.get("MODE"), forme, nbLots, lots);

        List<IdentiteDao.Voisine> voisines = new ArrayList<>();
        Integer exercice = ligne.getIdPpm() == null ? null : ppmRepository.findById(ligne.getIdPpm()).map(p -> p.getExercice()).orElse(null);
        if (entite != null && exercice != null) {
            Map<Integer, Marche> parFiliation = new LinkedHashMap<>();
            marcheRepository.findLignesDeLAutorite(entite.getIdEntiteContract(), List.of(exercice, exercice - 1)).stream()
                    .filter(m -> !Boolean.TRUE.equals(m.getSupprimee()) && m.getDesignationMarche() != null)
                    .filter(m -> !Objects.equals(m.getIdLigneOrigine(), ligne.getIdLigneOrigine()))
                    .sorted(Comparator.comparing(Marche::getIdDetail))
                    .forEach(m -> parFiliation.put(m.getIdLigneOrigine(), m));
            for (Marche m : parFiliation.values()) {
                voisines.add(new IdentiteDao.Voisine(m.getIdDetail(), m.getDesignationMarche(), alloti ? designations(m.getIdDetail()) : List.of()));
            }
        }
        List<String> autres = new ArrayList<>();
        for (EntiteContract e : entiteRepository.findAll()) {
            if (entite != null && Objects.equals(e.getIdEntiteContract(), entite.getIdEntiteContract())) {
                continue;
            }
            ajouter(autres, e.getLibelleEntite());
            ajouter(autres, e.getSigle());
        }
        autres.removeIf(a -> autorites.stream().anyMatch(x -> IdentiteDao.cle(x).equals(IdentiteDao.cle(a)))
                || sigles.stream().anyMatch(x -> IdentiteDao.cle(x).equals(IdentiteDao.cle(a))));
        return IdentiteDao.juger(texte, plan, voisines, autres, divergences);
    }

    private List<String> designations(Integer idDetail) {
        return lotRepository.findByIdDetail(idDetail).stream().sorted(Comparator.comparing(Lot::getIdLot))
                .map(l -> l.getDesignationLot() == null ? "" : l.getDesignationLot()).toList();
    }

    private static void ajouter(List<String> liste, String valeur) {
        if (valeur != null && !valeur.isBlank() && liste.stream().noneMatch(x -> IdentiteDao.cle(x).equals(IdentiteDao.cle(valeur)))) {
            liste.add(valeur.trim());
        }
    }

    // ------------------------------------------------------------------ garder (B2, H1)

    @Transactional
    public void enregistrer(Long idDmc, String empreinte, ImportDaoResult.Identite identite) {
        LocalDateTime maintenant = LocalDateTime.now(HorlogeRecette.HORLOGE);
        verdictRepository.purger(maintenant.minusDays(1));
        String utilisateur = utilisateur();
        ImportVerdict v = verdictRepository.findByIdDmcAndEmpreinteAndUtilisateur(idDmc, empreinte, utilisateur).orElseGet(ImportVerdict::new);
        v.setIdDmc(idDmc);
        v.setEmpreinte(empreinte);
        v.setUtilisateur(utilisateur);
        v.setVerdict(identite.verdict());
        v.setMotif(identite.motif() == null || identite.motif().length() <= 2000 ? identite.motif() : identite.motif().substring(0, 2000));
        v.setLuLe(maintenant);
        verdictRepository.save(v);
    }

    /**
     * B2 — la lecture de ce fichier par cet utilisateur, pour cette fiche, autorise-t-elle l'application ? Rend la mention du journal
     * (« identité : CONFORME », « identité : NON_PROUVE, attestée par … ») ; 409 sinon.
     */
    @Transactional(readOnly = true)
    public String exiger(Long idDmc, String empreinte, boolean attestation) {
        String utilisateur = utilisateur();
        ImportVerdict v = verdictRepository.findByIdDmcAndEmpreinteAndUtilisateur(idDmc, empreinte, utilisateur)
                .filter(x -> x.getLuLe() != null && x.getLuLe().isAfter(LocalDateTime.now(HorlogeRecette.HORLOGE).minusHours(DUREE_HEURES))).orElse(null);
        if (v == null) {
            throw new BusinessRuleException("La lecture de ce fichier est introuvable ou date de plus de " + DUREE_HEURES
                    + " heures : importez de nouveau le DAO avant de l'appliquer.", "IMPORT_EXPIRE");
        }
        if (IdentiteDao.ETRANGER.equals(v.getVerdict())) {
            throw new BusinessRuleException(v.getMotif() == null ? "Ce DAO n'est pas celui de la ligne de la fiche." : v.getMotif(), "DAO_ETRANGER");
        }
        if (IdentiteDao.NON_PROUVE.equals(v.getVerdict())) {
            // L'attestation est celle de la PRMP : celle d'une UGPM ne vaut pas.
            if (!attestation || CurrentUser.profil().orElse(null) != cnm.prs.enums.ProfilUtilisateur.PRMP) {
                throw new BusinessRuleException("Rien ne prouve que ce DAO soit celui de la ligne de la fiche : la PRMP l'atteste "
                        + "(attestation) avant de l'appliquer." + (v.getMotif() == null ? "" : " " + v.getMotif()), "ATTESTATION_REQUISE");
            }
            return "identité : NON_PROUVE, attestée par " + utilisateur;
        }
        return "identité : " + v.getVerdict();
    }

    private static String utilisateur() {
        return CurrentUser.login().orElse("inconnu");
    }
}
