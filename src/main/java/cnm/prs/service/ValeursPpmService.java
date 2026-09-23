package cnm.prs.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.enums.StatutDossier;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.LocaliteRepository;
import cnm.prs.repository.CapmRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.MinistereRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.NatureRepository;
import cnm.prs.repository.OrganigrammeRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.ServiceBeneficiaireRepository;

/**
 * ⚠️ Fiche marché DAO (demande front du 2026-09-22, §B2/§B3, H7) — les <strong>22 informations reprises du PPM</strong>,
 * <strong>relues à chaque lecture</strong> et jamais stockées dans la fiche. Une clé par champ de source {@code PPM}
 * ({@code tr_champ_fiche_marche.CLE_PPM}) ; la valeur est un texte prêt à afficher.
 *
 * <p><strong>La ligne à travers les versions du plan.</strong> Le DMC est ancré sur un {@code ID_DETAIL}, qui
 * n'identifie la ligne que dans UN dossier : une mise à jour du PPM recopie les lignes dans un nouveau dossier
 * (nouvelles PK, {@code idLigneOrigine} commun) et bascule le prédécesseur en {@code REMPLACE}. Pour que la fiche
 * suive le plan en vigueur (H7, test 9 de la recette), la ligne lue est la <strong>ligne homologue de la dernière
 * version effective</strong> : depuis la ligne du DMC, tant que son dossier est {@code REMPLACE}, on passe à
 * l'enfant effectif (non brouillon) et à la ligne de même origine. Une mise à jour encore en brouillon n'est pas
 * suivie (elle peut être abandonnée) ; une ligne retirée dans la nouvelle version reste lue (marquée
 * {@code supprimee}). L'ancrage du DMC ne change pas — décision backend, à confirmer par le pilote.</p>
 */
@Service
@Transactional(readOnly = true)
public class ValeursPpmService {

    /** Bornes du parcours des versions : un plan n'a jamais autant de versions, c'est une garde contre un cycle. */
    private static final int MAX_VERSIONS = 100;

    /** Statuts d'un plan au PV signé (le PV, une fois signé, ne se rouvre pas). */
    public static final java.util.Set<String> STATUTS_PV_SIGNE = java.util.Set.of(StatutDossier.PV_SIGNE.name(),
            StatutDossier.EN_VERIFICATION.name(), StatutDossier.OBSERVATIONS_LEVEES.name(),
            StatutDossier.DECISION_TRANSMISE_SIGMP.name(), StatutDossier.CLOTURE.name());

    /** Statuts où les réserves d'un PV FAVR sont levées. */
    private static final java.util.Set<String> STATUTS_RESERVES_LEVEES = java.util.Set.of(
            StatutDossier.OBSERVATIONS_LEVEES.name(), StatutDossier.DECISION_TRANSMISE_SIGMP.name(),
            StatutDossier.CLOTURE.name());

    private final MarcheRepository marcheRepository;
    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final EntiteContractRepository entiteContractRepository;
    private final OrganigrammeRepository organigrammeRepository;
    private final MinistereRepository ministereRepository;
    private final LocaliteRepository localiteRepository;
    private final PrmpRepository prmpRepository;
    private final NatureRepository natureRepository;
    private final ModePassationRepository modePassationRepository;
    private final ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    private final MarchePrevisionRepository marchePrevisionRepository;
    private final LotRepository lotRepository;
    private final CapmRepository capmRepository;
    private final PvExamenRepository pvExamenRepository;

    public ValeursPpmService(MarcheRepository marcheRepository, DossierRepository dossierRepository,
            PpmRepository ppmRepository, EntiteContractRepository entiteContractRepository,
            OrganigrammeRepository organigrammeRepository, MinistereRepository ministereRepository,
            LocaliteRepository localiteRepository, PrmpRepository prmpRepository, NatureRepository natureRepository,
            ModePassationRepository modePassationRepository,
            ServiceBeneficiaireRepository serviceBeneficiaireRepository,
            MarchePrevisionRepository marchePrevisionRepository, LotRepository lotRepository,
            CapmRepository capmRepository, PvExamenRepository pvExamenRepository) {
        this.marcheRepository = marcheRepository;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.entiteContractRepository = entiteContractRepository;
        this.organigrammeRepository = organigrammeRepository;
        this.ministereRepository = ministereRepository;
        this.localiteRepository = localiteRepository;
        this.prmpRepository = prmpRepository;
        this.natureRepository = natureRepository;
        this.modePassationRepository = modePassationRepository;
        this.serviceBeneficiaireRepository = serviceBeneficiaireRepository;
        this.marchePrevisionRepository = marchePrevisionRepository;
        this.lotRepository = lotRepository;
        this.capmRepository = capmRepository;
        this.pvExamenRepository = pvExamenRepository;
    }

    /**
     * Ce que la fiche relit du plan pour une ligne.
     *
     * @param ligne      la ligne homologue en vigueur (peut différer de la ligne du DMC)
     * @param versionPpm {@code t_ppm.NUM_MAJ} du plan lu, 0 pour un plan initial
     * @param valeurs    les 22 informations, par clé
     * @param dates      dates prévisionnelles du plan par étape ({@code LANCEMENT}, {@code ATTRIBUTION}…), pour les contrôles
     */
    public record ValeursPpm(Marche ligne, int versionPpm, Map<String, String> valeurs, Map<String, LocalDate> dates) {
    }

    public ValeursPpm lire(Integer idDetailDmc) {
        Marche origine = marcheRepository.findById(idDetailDmc).orElse(null);
        if (origine == null) {
            return new ValeursPpm(null, 0, Map.of(), Map.of());
        }
        Marche ligne = ligneEnVigueur(origine);
        Dossier dossier = ligne.getIdDossier() == null ? null : dossierRepository.findById(ligne.getIdDossier()).orElse(null);
        Ppm ppm = ligne.getIdPpm() == null ? null : ppmRepository.findById(ligne.getIdPpm()).orElse(null);
        Map<String, LocalDate> dates = new LinkedHashMap<>();
        Map<String, String> v = new LinkedHashMap<>();

        EntiteContract entite = dossier == null || dossier.getIdEntiteContract() == null ? null
                : entiteContractRepository.findById(dossier.getIdEntiteContract()).orElse(null);
        v.put("ENTITE", entite == null ? null : entite.getLibelleEntite());
        v.put("ADRESSE", entite == null ? null : entite.getAdresse());
        v.put("MINISTERE", ministere(entite));
        String idLocalite = localiteDe(dossier, ppm);
        v.put("LOCALITE", idLocalite == null ? null
                : localiteRepository.findById(idLocalite).map(l -> l.getLibelleLocalite()).orElse(idLocalite));
        String idPrmp = prmpDe(dossier, ppm);
        Prmp prmp = idPrmp == null ? null : prmpRepository.findById(idPrmp).orElse(null);
        v.put("PRMP", prmp == null ? idPrmp : nomComplet(prmp.getNomPrmp(), prmp.getPrenomsPrmp()));
        v.put("PRMP_EMAIL", prmp == null ? null : prmp.getEmailPrmp());
        v.put("PRMP_TEL", prmp == null ? null : prmp.getTelPrmp());
        v.put("PPM_REFERENCE", ppm == null ? null : ppm.getReference());
        v.put("PPM_EXERCICE", ppm == null || ppm.getExercice() == null ? null : String.valueOf(ppm.getExercice()));
        int versionPpm = ppm == null || ppm.getNumMaj() == null ? 0 : ppm.getNumMaj();
        v.put("PPM_VERSION", String.valueOf(versionPpm));
        v.put("DOSSIER_REFERENCE", dossier == null ? null : dossier.getRefeDossier());
        v.put("NATURE", ligne.getIdNature() == null ? null
                : natureRepository.findById(ligne.getIdNature()).map(n -> n.getLibelle()).orElse(null));
        v.put("MODE", ligne.getIdMode() == null ? null
                : modePassationRepository.findById(ligne.getIdMode()).map(m -> m.getLibelle()).orElse(null));
        v.put("MONTANT_ESTIMATIF", montant(ligne.getMontEstim()));
        v.put("FINANCEMENT", ligne.getFinancement());
        List<ServiceBeneficiaire> benefs = serviceBeneficiaireRepository.findByIdDetail(ligne.getIdDetail());
        v.put("BENEFICIAIRES", beneficiaires(benefs));
        v.put("COMPTES", comptes(ligne, benefs));
        v.put("DATES_PREVISIONNELLES", datesPrevisionnelles(ligne, dates));
        v.put("OBJET", ligne.getDesignationMarche());
        // ⚠️ Lot 1c (2026-09-23) — la forme telle que saisie au plan (jamais le défaut du getter) : d'elle se déduit le
        // type de marché de la fiche. Servie en libellé ; le code est FicheMarcheDto.typeMarche.
        v.put("FORME_MARCHE", ligne.formeMarcheSaisie() == null ? null : ligne.formeMarcheSaisie().libelle());
        List<Lot> lots = lotRepository.findByIdDetail(ligne.getIdDetail());
        v.put("NB_LOTS_PPM", String.valueOf(lots.size()));
        v.put("LOTS_DESIGNATION", lots.isEmpty() ? null : String.join(" ; ", lots.stream()
                .map(l -> (l.getDesignationLot() == null ? "Lot " + l.getIdLot() : l.getDesignationLot())).toList()));
        v.put("LOTS_MONTANTS", lots.isEmpty() ? null : String.join(" ; ", lots.stream()
                .map(l -> (l.getDesignationLot() == null ? "Lot " + l.getIdLot() : l.getDesignationLot())
                        + " : " + montant(l.getMontLot())).toList()));
        return new ValeursPpm(ligne, versionPpm, v, dates);
    }

    /**
     * ⚠️ Fiche marché, lot 1b (2026-09-23, §B2) — l'en-tête d'un dossier produit depuis la fiche, <strong>en
     * identifiants</strong> : entité contractante et localité de la ligne en vigueur, dérivées par les mêmes règles
     * que {@code ENTITE} et {@code LOCALITE} de {@link #lire} (une seule définition). La PRMP n'y est pas : le
     * dossier appartient à la PRMP qui le crée, comme toute saisie (mandat d'attribution figé à la création).
     */
    public record EnTete(Integer idEntiteContract, String idLocalite) {
    }

    public EnTete enTete(Integer idDetailDmc) {
        Marche origine = marcheRepository.findById(idDetailDmc).orElse(null);
        if (origine == null) {
            return new EnTete(null, null);
        }
        Marche ligne = ligneEnVigueur(origine);
        Dossier dossier = ligne.getIdDossier() == null ? null : dossierRepository.findById(ligne.getIdDossier()).orElse(null);
        Ppm ppm = ligne.getIdPpm() == null ? null : ppmRepository.findById(ligne.getIdPpm()).orElse(null);
        return new EnTete(dossier == null ? null : dossier.getIdEntiteContract(), localiteDe(dossier, ppm));
    }

    /** Localité de la ligne : celle de son dossier, à défaut celle du PPM. */
    private static String localiteDe(Dossier dossier, Ppm ppm) {
        return dossier != null && dossier.getIdLocalite() != null ? dossier.getIdLocalite()
                : ppm != null ? ppm.getIdLocalite() : null;
    }

    /** PRMP de la ligne : celle du PPM, à défaut celle du dossier. */
    private static String prmpDe(Dossier dossier, Ppm ppm) {
        return ppm != null && ppm.getIdPrmp() != null ? ppm.getIdPrmp() : dossier == null ? null : dossier.getIdPrmp();
    }

    /** La ligne homologue de la dernière version effective du plan (voir l'en-tête). */
    public Marche ligneEnVigueur(Marche origine) {
        Marche courante = origine;
        for (int i = 0; i < MAX_VERSIONS; i++) {
            Dossier d = courante.getIdDossier() == null ? null : dossierRepository.findById(courante.getIdDossier()).orElse(null);
            if (d == null || !StatutDossier.REMPLACE.name().equals(d.getStatut())) {
                return courante;
            }
            Integer origineLigne = courante.getIdLigneOrigine();
            Marche suivante = null;
            List<Dossier> enfants = new ArrayList<>(dossierRepository.findByIdDossierParent(d.getIdDossier()));
            enfants.sort(java.util.Comparator.comparing(Dossier::getIdDossier));
            for (Dossier enfant : enfants) {
                // Seule une version SIGNÉE (même prédicat que la garde H4 : PV signé, avis FAV ou FAVR réserves
                // levées) — ou elle-même déjà remplacée, donc signée avant de l'être — est « la version courante » :
                // une mise à jour encore en instruction arrête la marche sans devenir courante.
                if (!StatutDossier.REMPLACE.name().equals(enfant.getStatut()) && !pvSigneFavorable(enfant)) {
                    continue;
                }
                suivante = marcheRepository.findByIdDossier(enfant.getIdDossier()).stream()
                        .filter(m -> Objects.equals(m.getIdLigneOrigine(), origineLigne)).findFirst().orElse(null);
                if (suivante != null) {
                    break;
                }
            }
            if (suivante == null) {
                return courante;
            }
            courante = suivante;
        }
        return courante;
    }

    /**
     * H4 — le plan est-il au <strong>PV signé favorable</strong> : statut d'un plan signé, et le plus récent des PV
     * signés du dossier d'avis {@code FAV}, ou {@code FAVR} une fois les réserves levées. Partagé par la garde de
     * création du DMC, la liste des éligibles et le parcours des versions.
     */
    public boolean pvSigneFavorable(Dossier dossier) {
        if (dossier == null || !STATUTS_PV_SIGNE.contains(dossier.getStatut())) {
            return false;
        }
        List<PvExamen> pvs = pvExamenRepository.findSignesParDossierRows(dossier.getIdDossier());
        if (pvs.isEmpty()) {
            return false;
        }
        String avis = pvs.get(0).getIdAvis();
        if ("FAV".equals(avis)) {
            return true;
        }
        return "FAVR".equals(avis) && STATUTS_RESERVES_LEVEES.contains(dossier.getStatut());
    }

    // ------------------------------------------------------------------ mises en forme

    private String ministere(EntiteContract entite) {
        if (entite == null || entite.getIdOrganigramme() == null) {
            return null;
        }
        return organigrammeRepository.findById(entite.getIdOrganigramme())
                .filter(o -> o.getIdMinistere() != null)
                .flatMap(o -> ministereRepository.findById(o.getIdMinistere()))
                .map(m -> m.getLibelleMinistere()).orElse(null);
    }

    private static String nomComplet(String nom, String prenoms) {
        String n = nom == null ? "" : nom.trim().toUpperCase();
        String p = prenoms == null ? "" : prenoms.trim();
        return (n + " " + p).trim();
    }

    /** « 8 400 000 » : groupement par espaces, décimales seulement si présentes. */
    public static String montant(BigDecimal m) {
        if (m == null) {
            return null;
        }
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.FRANCE);
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator(',');
        DecimalFormat f = new DecimalFormat("#,##0.##", s);
        return f.format(m).replace(' ', ' ');
    }

    private static String beneficiaires(List<ServiceBeneficiaire> benefs) {
        if (benefs.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (ServiceBeneficiaire b : benefs) {
            String lib = b.getSoa() != null && b.getSoa().getLibelle() != null ? b.getSoa().getLibelle() : null;
            String code = b.getSoaCode();
            String montant = b.getNouvMontBenef() != null ? montant(b.getNouvMontBenef())
                    : montant(b.getAncMontBenef());
            String t = lib != null && code != null ? code + " – " + lib : lib != null ? lib : code;
            parts.add(t == null ? "?" : montant == null ? t : t + " (" + montant + ")");
        }
        return String.join(" ; ", parts);
    }

    private static String comptes(Marche ligne, List<ServiceBeneficiaire> benefs) {
        List<String> comptes = new ArrayList<>();
        if (ligne.getNumCompte() != null) {
            comptes.add(ligne.getNumCompte());
        }
        for (ServiceBeneficiaire b : benefs) {
            if (b.getNumCompte() != null && !comptes.contains(b.getNumCompte())) {
                comptes.add(b.getNumCompte());
            }
        }
        return comptes.isEmpty() ? null : String.join(", ", comptes);
    }

    /**
     * « Lancement : 2026-03-02 ; Ouverture des plis : 2026-04-15 … » dans l'ordre du processus, et alimente
     * {@code dates} par mot-clé d'étape (LANCEMENT, REMISE, OUVERTURE, ATTRIBUTION) — la plus précoce par étape.
     */
    private String datesPrevisionnelles(Marche ligne, Map<String, LocalDate> dates) {
        List<MarchePrevision> prevs = marchePrevisionRepository.findByMarcheOrdonne(ligne.getIdDetail());
        if (prevs.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (MarchePrevision p : prevs) {
            // L'association peut manquer sur une entité encore en cache de session : la colonne fait foi.
            Capm capm = p.getCapm() != null ? p.getCapm()
                    : p.getIdCapm() == null ? null : capmRepository.findById(p.getIdCapm()).orElse(null);
            String libelle = capm == null ? "Étape " + p.getIdCapm() : capm.getLibelleProcessus();
            String periode = p.getDateDebut() == null ? "—"
                    : p.getDateFin() == null || p.getDateFin().equals(p.getDateDebut()) ? p.getDateDebut().toString()
                    : p.getDateDebut() + " → " + p.getDateFin();
            parts.add(libelle + " : " + periode);
            if (p.getDateDebut() != null && capm != null) {
                String norm = LibelleNormalisation.normaliser(capm.getLibelleProcessus());
                for (String etape : List.of("LANCEMENT", "REMISE", "OUVERTURE", "ATTRIBUTION")) {
                    if (norm.contains(etape)) {
                        LocalDate connue = dates.get(etape);
                        if (connue == null || p.getDateDebut().isBefore(connue)) {
                            dates.put(etape, p.getDateDebut());
                        }
                    }
                }
            }
        }
        return String.join(" ; ", parts);
    }
}
