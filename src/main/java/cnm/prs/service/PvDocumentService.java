package cnm.prs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Controleur;
import cnm.prs.entity.Dossier;
import cnm.prs.enums.StatutPv;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.Examen;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.ExamenPiece;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Localite;
import cnm.prs.entity.ObservationControle;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.Reception;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.ExamenDetailRepository;
import cnm.prs.repository.ExamenPieceRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.LocaliteRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.ObservationControleRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.PpmRepository;
import cnm.prs.repository.ReceptionRepository;
import cnm.prs.repository.TypePieceJointeRepository;

/**
 * Génère et stocke le PDF du Projet de PV <strong>uniquement quand un modèle officiel existe</strong>
 * pour le cas (cf. {@link #modelePour(PvExamen)}) : avis <strong>FAVR</strong> (avec annexe des
 * observations), <strong>FAV</strong> ou <strong>DEF</strong> (sans annexe) — décliné PPM / PPM-AGPM et
 * centrale / régionale — et {@code PPM} comportant au moins une ligne de marché.
 * <strong>Indépendant du mode de passation</strong>. Hors de ces conditions, aucun document
 * n'est produit (le PV est créé normalement, sans {@code cheminDocument}) — rien n'est inventé.
 */
@Component
@Transactional(readOnly = true)
public class PvDocumentService {

    private static final String AVIS_FAVORABLE_RESERVE = "FAVR";
    /** ⚠️ 2026-08-03 — avis favorable SANS réserve : modèles officiels fournis (PPM / PPM-AGPM). */
    private static final String AVIS_FAVORABLE = "FAV";
    /** ⚠️ 2026-08-04 — avis défavorable : modèles « AVIS NON FAVORABLE » (ANF) fournis. */
    private static final String AVIS_DEFAVORABLE = "DEF";
    /** Localité centrale : source unique {@link Localite#ID_CENTRALE} (partagée avec les références). */
    private static final String LOCALITE_CENTRALE = Localite.ID_CENTRALE;

    private final PvDocumentGenerator generator;
    private final ExamenRepository examenRepository;
    private final DossierRepository dossierRepository;
    private final PpmRepository ppmRepository;
    private final MarcheRepository marcheRepository;
    private final ReceptionRepository receptionRepository;
    private final EntiteContractRepository entiteContractRepository;
    private final LocaliteRepository localiteRepository;
    private final ControleurRepository controleurRepository;
    private final ExamenDetailRepository examenDetailRepository;
    private final ObservationControleRepository observationControleRepository;
    private final ExamenPieceRepository examenPieceRepository;
    private final PieceJointeDossierRepository pieceJointeDossierRepository;
    private final TypePieceJointeRepository typePieceJointeRepository;
    private final ControleurDirectory controleurDirectory;

    @Value("${storage.pv-examen.path:${java.io.tmpdir}/prs-fsx/PV}")
    private String cheminStockagePv;

    public PvDocumentService(PvDocumentGenerator generator, ExamenRepository examenRepository,
            DossierRepository dossierRepository, PpmRepository ppmRepository, MarcheRepository marcheRepository,
            ReceptionRepository receptionRepository,
            EntiteContractRepository entiteContractRepository, LocaliteRepository localiteRepository,
            ControleurRepository controleurRepository, ExamenDetailRepository examenDetailRepository,
            ObservationControleRepository observationControleRepository,
            ExamenPieceRepository examenPieceRepository,
            PieceJointeDossierRepository pieceJointeDossierRepository,
            TypePieceJointeRepository typePieceJointeRepository,
            ControleurDirectory controleurDirectory) {
        this.generator = generator;
        this.examenRepository = examenRepository;
        this.dossierRepository = dossierRepository;
        this.ppmRepository = ppmRepository;
        this.marcheRepository = marcheRepository;
        this.receptionRepository = receptionRepository;
        this.entiteContractRepository = entiteContractRepository;
        this.localiteRepository = localiteRepository;
        this.controleurRepository = controleurRepository;
        this.examenDetailRepository = examenDetailRepository;
        this.observationControleRepository = observationControleRepository;
        this.examenPieceRepository = examenPieceRepository;
        this.pieceJointeDossierRepository = pieceJointeDossierRepository;
        this.typePieceJointeRepository = typePieceJointeRepository;
        this.controleurDirectory = controleurDirectory;
    }

    /**
     * Génère et stocke le PDF du Projet de PV si le PV est éligible ; renvoie le chemin du fichier, ou
     * {@link Optional#empty()} si non éligible (avis ≠ FAVR, localité non centrale, ou une ligne de
     * marché hors appel d'offres ouvert).
     */
    public Optional<String> genererSiEligible(PvExamen pv) {
        String modele = modelePour(pv).orElse(null);
        if (modele == null) {
            return Optional.empty();
        }
        Integer idExamen = pv.getIdExamen();
        Integer idDossier = examenRepository.findIdDossierByExamen(idExamen).orElse(null);
        Dossier dossier = dossierRepository.findById(idDossier).orElse(null);
        String localite = examenRepository.findLocaliteByExamen(idExamen).orElse(null);
        Ppm ppm = ppmRepository.findByIdDossier(idDossier).stream().findFirst().orElse(null);
        PvDocumentContexte ctx = construireContexte(pv, dossier, ppm, idExamen, localite);
        byte[] pdf = generator.genererPdf(ctx, modele);
        return Optional.of(stockerSurFsx(pv, pdf));
    }

    /**
     * Prédicat <strong>pur</strong> d'éligibilité à la génération du PDF (sans effet de bord) : avis
     * {@code FAVR} + dossier de localité <strong>centrale ANT</strong> + <strong>PPM</strong> comportant au
     * moins une ligne de marché. <strong>Indépendant du mode de passation</strong> : le gabarit AFSR/PPM/central
     * est identique quel que soit le mode. Sert au flag {@code documentDisponible}.
     */
    @Transactional(readOnly = true)
    public boolean estEligible(PvExamen pv) {
        return modelePour(pv).isPresent();
    }

    /**
     * Modèle Word officiel correspondant au PV, ou {@link Optional#empty()} si <strong>aucun modèle
     * n'existe</strong> pour ce cas (aucun document n'est alors produit — rien n'est inventé).
     *
     * <p>Condition commune : <strong>PPM comportant au moins une ligne de marché</strong> — indépendant
     * du mode de passation. Le modèle est choisi sur <strong>trois axes</strong> — avis, sous-type
     * (AGPM), localité <strong>centrale / régionale</strong> — cf. {@link PvDocumentGenerator#modele}
     * (⚠️ modèles fournis par le métier les 2026-08-03 et 2026-08-04) :</p>
     * <ul>
     *   <li>avis <strong>FAVR</strong> (favorable avec réserves) → modèle {@code AFSR},
     *       <strong>avec ANNEXE</strong> des observations ;</li>
     *   <li>avis <strong>FAV</strong> (favorable sans réserve) → modèle {@code AF}, sans annexe ;</li>
     *   <li>avis <strong>DEF</strong> (défavorable) → modèle {@code ANF} (« émet un AVIS NON
     *       FAVORABLE … »), sans annexe ;</li>
     * </ul>
     * <p>chacun décliné selon le <strong>sous-type</strong> du dossier (dérivé serveur des marchés) :
     * {@code PPM-AGPM} (« … à l'affichage du PPM <em>et à la publication de l'AGPM</em> ») ou
     * {@code PPM} (« … à l'affichage du PPM »), et selon la <strong>localité</strong>.</p>
     * <p>L'avis {@code NSP} (« ne se prononce pas ») n'a pas de modèle : aucun document.</p>
     */
    @Transactional(readOnly = true)
    public Optional<String> modelePour(PvExamen pv) {
        PvDocumentGenerator.SensAvis sens = sensDuModele(pv == null ? null : pv.getIdAvis()).orElse(null);
        if (sens == null) {
            return Optional.empty();
        }
        Integer idExamen = pv.getIdExamen();
        Integer idDossier = examenRepository.findIdDossierByExamen(idExamen).orElse(null);
        Dossier dossier = idDossier == null ? null : dossierRepository.findById(idDossier).orElse(null);
        // Localité du circuit (réception), comme les lettres de renvoi : commande la variante
        // centrale / régionale (⚠️ 2026-08-04 — les 4 modèles régionaux sont désormais fournis).
        String localite = examenRepository.findLocaliteByExamen(idExamen).orElse(null);
        if (dossier == null || localite == null) {
            return Optional.empty();
        }
        Ppm ppm = ppmRepository.findByIdDossier(idDossier).stream().findFirst().orElse(null);
        // L'éligibilité ne dépend PAS du mode de passation : on exige seulement que le PPM comporte au
        // moins une ligne de marché (PPM réel), quel que soit le mode (AOO, cotation, etc.).
        // ⚠️ 2026-08-05 — les lignes SUPPRIMÉES d'une version ne comptent pas : un plan dont toutes les
        // lignes ont été retirées n'est pas un PPM.
        if (ppm == null || marcheRepository.findByIdPpm(ppm.getIdPpm()).stream().allMatch(Marche::getSupprimee)) {
            return Optional.empty();
        }
        // Le sous-type (dérivé serveur des marchés déclencheurs) commande la mention AGPM du modèle.
        return Optional.of(PvDocumentGenerator.modele(sens,
                DossierIntegriteService.SOUS_TYPE_PPM_AGPM.equals(dossier.getIdSousType()),
                LOCALITE_CENTRALE.equals(localite)));
    }

    /**
     * Sens de modèle correspondant à l'avis du PV, ou {@link Optional#empty()} si <strong>aucun modèle
     * officiel n'existe</strong> pour cet avis (aujourd'hui : {@code NSP} « ne se prononce pas »).
     */
    private Optional<PvDocumentGenerator.SensAvis> sensDuModele(String idAvis) {
        if (AVIS_FAVORABLE_RESERVE.equals(idAvis)) {
            return Optional.of(PvDocumentGenerator.SensAvis.AFSR);
        }
        if (AVIS_FAVORABLE.equals(idAvis)) {
            return Optional.of(PvDocumentGenerator.SensAvis.AF);
        }
        if (AVIS_DEFAVORABLE.equals(idAvis)) {
            return Optional.of(PvDocumentGenerator.SensAvis.ANF);   // « AVIS NON FAVORABLE » au document
        }
        return Optional.empty();
    }

    /**
     * Vrai si un PDF officiel est <strong>réellement disponible</strong> pour ce PV.
     *
     * <p>⚠️ Contrat révisé (2026-08-19, génération post-commit) : pour un PV <strong>SIGNE</strong>,
     * le flag dit « le fichier est prêt à télécharger <em>maintenant</em> » — donc
     * {@code CHEMIN_DOCUMENT} non nul, et <strong>false pendant la fenêtre de génération</strong>
     * qui suit la signature (le front sait afficher un PV signé sans document). Pour un PV non
     * signé (projet), le sens historique est conservé : {@link #estEligible(PvExamen) éligibilité}
     * — « un document officiel sera produit à la signature » (matrice des 12 modèles).</p>
     */
    @Transactional(readOnly = true)
    public boolean documentDisponible(PvExamen pv) {
        if (pv != null && pv.getCheminDocument() != null && !pv.getCheminDocument().isBlank()) {
            return true;
        }
        if (pv != null && StatutPv.SIGNE.name().equals(pv.getStatutPv())) {
            return false;   // signé sans fichier : génération en cours (ou non éligible) — pas prêt
        }
        return estEligible(pv);
    }


    /**
     * ⚠️ 2026-09-01 — <strong>contexte du document d'un PV</strong>, tel qu'il sera imprimé, sans produire
     * le PDF.
     *
     * <p>Ajouté pour rendre vérifiable la mention « (par intérim) » de l'arbitrage 4 : la seule autre
     * façon de la constater serait de générer le PDF, ce qui pilote Word et exclut le test de la CI
     * Linux (groupe {@code word}). Une règle métier qui ne s'observe qu'avec Microsoft Word installé
     * n'est pas une règle testée. Ce n'est pas un accesseur de complaisance : il rend lisible ce que le
     * document dira, ce qu'aucune autre méthode publique n'offrait.</p>
     *
     * @return le contexte, ou {@code null} si le PV, son dossier ou son PPM sont introuvables
     */
    @Transactional(readOnly = true)
    public PvDocumentContexte contexte(PvExamen pv) {
        if (pv == null) {
            return null;
        }
        Integer idExamen = pv.getIdExamen();
        Integer idDossier = examenRepository.findIdDossierByExamen(idExamen).orElse(null);
        Dossier dossier = idDossier == null ? null : dossierRepository.findById(idDossier).orElse(null);
        Ppm ppm = idDossier == null ? null : ppmRepository.findByIdDossier(idDossier).stream().findFirst().orElse(null);
        if (dossier == null || ppm == null) {
            return null;
        }
        return construireContexte(pv, dossier, ppm, idExamen,
                examenRepository.findLocaliteByExamen(idExamen).orElse(dossier.getIdLocalite()));
    }

    private PvDocumentContexte construireContexte(PvExamen pv, Dossier dossier, Ppm ppm, Integer idExamen,
            String idLocalite) {
        LocalDate dateExamen = examenRepository.findById(idExamen).map(Examen::getDateExamen).orElse(null);
        String refPv = pv.getRefePv() != null ? pv.getRefePv() : pv.getReferencePv();
        LocalDate dateReception = receptionRepository.findByIdDossier(dossier.getIdDossier()).stream()
                .map(Reception::getDateReception).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).map(LocalDateTime::toLocalDate).orElse(null);
        String entite = dossier.getIdEntiteContract() == null ? "" : entiteContractRepository
                .findById(dossier.getIdEntiteContract()).map(EntiteContract::getLibelleEntite).orElse("");
        Localite loc = localiteRepository.findById(idLocalite).orElse(null);
        String localite = loc == null ? idLocalite : loc.getLibelleLocalite();
        // ⚠️ 2026-08-04 — lieu d'établissement = chef-lieu (ville de siège) ; repli sur le libellé.
        String chefLieu = loc == null || loc.getChefLieu() == null || loc.getChefLieu().isBlank()
                ? localite : loc.getChefLieu();
        return new PvDocumentContexte(dateExamen, refPv, dateReception, entite, ppm.getExercice(), localite, chefLieu,
                nomAvecMentionInterim(pv.getImCtrlPresident(), pv, idLocalite),
                nomAvecMentionInterim(pv.getImCtrlCc(), pv, idLocalite),
                nomMembreAttributaire(pv.getImCtrlMembre()), nomSecretaireSeance(pv.getIdSecretaireSeance()),
                // ⚠️ 2026-08-05 — nature du plan : INITIAL (null/0) ou MODIFICATIF N°n si le dossier est
                // une version. L'information est portée par le PPM lui-même (t_ppm.NUM_MAJ).
                ppm.getNumMaj(),
                construireObservations(idExamen));
    }

    /**
     * ⚠️ Règle élargie (2026-08-15) — nom du Secrétaire de séance au bloc Signataires : suffixé
     * « (par délégation) » quand le désigné n'est pas un Vérificateur titulaire (auto-désignation
     * du Président/CC via une paire « → Vérificateur » active), pour que le cumul des mentions
     * du circuit court reste lisible sur le document.
     */
    private String nomSecretaireSeance(String im) {
        return nomAvecMentionDelegation(im, cnm.prs.enums.ProfilUtilisateur.VERIFICATEUR);
    }

    /**
     * ⚠️ Décision produit (2026-08-15, circuit court) — ligne « Membre » du bloc Signataires :
     * suffixée « (par délégation) » quand l'attributaire n'est pas un Membre titulaire (Président/CC
     * auto-attributaire) — les deux lignes de signature pouvant alors porter le même nom, la mention
     * rend le cumul lisible sur le document ; la ligne de son propre rôle reste sans mention.
     */
    private String nomMembreAttributaire(String im) {
        return nomAvecMentionDelegation(im, cnm.prs.enums.ProfilUtilisateur.MEMBRE);
    }

    /**
     * ⚠️ Visa par intérim (arbitrage du pilote, 2026-09-01, RÉVISÉ le jour même) — nom du Président ou
     * du Chef de commission, suffixé <strong>« (par intérim) »</strong> quand le visa a été posé par un
     * P/CC autre que le dispatcheur, <strong>et seulement hors localité centrale</strong>.
     *
     * <p><strong>Où la mention apparaît, et pourquoi pas ailleurs.</strong> La spec demandait la mention
     * « sur la ligne de signature du P/CC », via les modèles régionaux. Vérification faite sur les 12
     * modèles : cette ligne <em>n'existe pas</em>. Le bloc de signature ne contient que des légendes
     * (« VISA DU SUPERIEUR HIERARCHIQUE », « (Nom, prénoms, cachet et signature du membre en charge du
     * dossier) ») — aucun placeholder de nom, et aucun emplacement pour le P/CC. Les modèles centraux et
     * régionaux y sont d'ailleurs identiques. Le seul endroit où le P/CC est imprimé est le bloc
     * « Étaient présents ». La mention s'y pose donc, par le mécanisme même que la spec cite en
     * référence : {@code nomAvecMentionDelegation} suffixe le NOM, et ce nom atterrit dans ce bloc.
     * Résultat : aucun {@code .docx} modifié, donc aucun des quatre pièges de dérivation.</p>
     *
     * <p><strong>La condition de localité est en Java, pas dans les modèles</strong> — pour la même
     * raison : il n'y a rien à différencier côté régional. {@code Localite.estCentrale} tranche.</p>
     */
    private String nomAvecMentionInterim(String im, cnm.prs.entity.PvExamen pv, String idLocalite) {
        String nom = nomControleur(im);
        if (nom == null || !Boolean.TRUE.equals(pv.getViseParInterim()) || Localite.estCentrale(idLocalite)) {
            return nom;
        }
        return nom + " (par intérim)";
    }

    /** Nom du contrôleur, suffixé « (par délégation) » si son profil n'est pas le profil titulaire du rôle. */
    private String nomAvecMentionDelegation(String im, cnm.prs.enums.ProfilUtilisateur profilTitulaire) {
        String nom = nomControleur(im);
        if (nom == null) {
            return null;
        }
        boolean titulaire = controleurDirectory.profilDe(im)
                .map(p -> p == profilTitulaire).orElse(false);
        return titulaire ? nom : nom + " (par délégation)";
    }

    /** « Prénoms Nom » d'un contrôleur, ou {@code null} si matricule absent (→ ligne « présents » retirée). */
    private String nomControleur(String im) {
        if (im == null || im.isBlank()) {
            return null;
        }
        return controleurRepository.findById(im)
                .map(c -> ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " "
                        + (c.getNomCont() == null ? "" : c.getNomCont())).trim())
                .filter(s -> !s.isBlank()).orElse(im);
    }

    /**
     * Toutes les observations des points de contrôle non conformes de l'examen, à plat (ordre stable).
     * ⚠️ Règle ajoutée (2026-07-21) — chaque observation est préfixée par la <strong>ligne de marché</strong>
     * concernée : « [Marché « désignation »] libellé du point » pour un résultat par ligne
     * ({@code idDetail} renseigné), « [Dossier] libellé du point » pour un point inter-lignes ou un examen
     * historique ({@code idDetail} nul). Aucune modification du gabarit Word : le préfixe entre dans la
     * colonne « point de contrôle » existante.
     * ⚠️ Règle ajoutée (2026-08-01) — les <strong>pièces jointes non conformes</strong> ({@code t_examen_piece})
     * sont ajoutées à la suite : RÉFÉRENCES = libellé de la pièce SEUL (sans préfixe « [Pièce « … »] » —
     * demande user), OBSERVATIONS = texte libre (les libellés « Au lieu de : / Lire : » sont retirés).
     */
    private List<PvDocumentContexte.Observation> construireObservations(Integer idExamen) {
        List<PvDocumentContexte.Observation> out = new ArrayList<>();
        java.util.Map<Integer, String> designationsParLigne = new java.util.HashMap<>();
        for (ExamenDetail ed : examenDetailRepository.findByIdExamen(idExamen)) {
            if (Boolean.FALSE.equals(ed.getConforme())) {
                String libelle = ed.getPtControle() == null ? null : ed.getPtControle().getLibelPointCtrl();
                String point = prefixerParLigne(ed.getIdDetail(), libelle, designationsParLigne);
                for (ObservationControle o : observationControleRepository
                        .findByIdDetailOrderByOrdreAsc(ed.getIdDetailExamen())) {
                    out.add(new PvDocumentContexte.Observation(point, o.getAuLieuDe(), o.getLire()));
                }
            }
        }
        for (ExamenPiece ep : examenPieceRepository.findByIdExamen(idExamen)) {
            if (Boolean.FALSE.equals(ep.getConforme())) {
                out.add(new PvDocumentContexte.Observation(
                        libellePiece(ep.getIdPiece()), ep.getObservation(), null, true));
            }
        }
        return out;
    }

    /** Libellé d'une pièce jointe (type de pièce, repli nom de fichier puis « n°<id> »). */
    private String libellePiece(Integer idPiece) {
        if (idPiece == null) {
            return "n°?";
        }
        return pieceJointeDossierRepository.findById(idPiece)
                .map(p -> {
                    String type = p.getIdTypePiece() == null ? null : typePieceJointeRepository
                            .findById(p.getIdTypePiece()).map(t -> t.getLibellePiece()).orElse(null);
                    return type != null && !type.isBlank() ? type
                            : p.getNomFichier() != null && !p.getNomFichier().isBlank() ? p.getNomFichier()
                            : "n°" + idPiece;
                })
                .orElse("n°" + idPiece);
    }

    /** Préfixe le libellé du point par la ligne de marché (désignation mise en cache) ou « [Dossier] ». */
    private String prefixerParLigne(Integer idDetail, String libelle, java.util.Map<Integer, String> cache) {
        String designation = idDetail == null ? null : cache.computeIfAbsent(idDetail, id ->
                marcheRepository.findById(id).map(m -> m.getDesignationMarche()).orElse(null));
        return prefixerLibelle(idDetail, libelle, designation);
    }

    /**
     * ⚠️ Règle ajoutée (2026-07-21) — formatage pur du libellé de l'ANNEXE (testable sans Word/BD) :
     * « [Marché « désignation »] point » si {@code idDetail} renseigné (repli « n°&lt;id&gt; » sans désignation),
     * « [Dossier] point » sinon.
     */
    public static String prefixerLibelle(Integer idDetail, String libelle, String designationMarche) {
        String base = libelle == null ? "" : libelle;
        if (idDetail == null) {
            return "[Dossier] " + base;
        }
        String marche = designationMarche == null || designationMarche.isBlank()
                ? "n°" + idDetail : designationMarche;
        return "[Marché « " + marche + " »] " + base;
    }

    /** Écrit le PDF dans le répertoire FSX PV/ sous {@code {refePv nettoyée}.pdf} ; renvoie le chemin. */
    private String stockerSurFsx(PvExamen pv, byte[] pdf) {
        String base = pv.getRefePv() != null && !pv.getRefePv().isBlank()
                ? pv.getRefePv() : ("pv-" + pv.getIdPv());
        String nomFichier = base.replace('/', '_').replace('\\', '_') + ".pdf";
        try {
            Path dir = Path.of(cheminStockagePv);
            Files.createDirectories(dir);
            Path fichier = dir.resolve(nomFichier);
            Files.write(fichier, pdf);
            return fichier.toString();
        } catch (IOException e) {
            throw new BusinessRuleException("Stockage du document du PV impossible : " + e.getMessage());
        }
    }
}
