package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AttributionDto;
import cnm.prs.dto.EntrepriseCandidatDto;
import cnm.prs.dto.EvaluationDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Evaluation;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Offre;
import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.entity.PvExamen;
import cnm.prs.entity.TypePieceJointe;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutFicheMarche;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.AttributionRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.OffreRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.TypePieceJointeRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ <strong>L'évaluation des offres, lot 2, tranche 2a</strong> (demande front du 2026-10-07 « de la proposition d'attribution à la
 * notification », §B1, §B2 ; V79). Après le rapport d'évaluation signé, chaque lot a son état ; pour un lot attribuable, la PRMP crée
 * le <strong>dossier de marché</strong> (famille {@code DDM}, un par lot : arbitrage Q2 du pilote ; chaque marché en ligne : Q11), qui
 * suit le circuit de la Commission sans changement. Les pièces 14 à 18 y sont jointes d'office, dont le <strong>projet de marché
 * produit par le serveur</strong> (arbitrage Q1). L'avis de la Commission remonte au lot.
 */
@Service
@Transactional
public class AttributionService {

    private static final Logger log = LoggerFactory.getLogger(AttributionService.class);

    public static final String EN_EVALUATION = "EN_EVALUATION";
    public static final String PROPOSE = "PROPOSE";
    public static final String AVIS_RENDU = "AVIS_RENDU";

    private final AttributionRepository attributions;
    private final EvaluationService evaluation;
    private final SeanceService seance;
    private final SaisieService saisie;
    private final ValeursPpmService valeursPpm;
    private final FicheMarcheService fiches;
    private final EntrepriseCandidatService entreprises;
    private final GenerateurDocumentsFiche generateur;
    private final DossierMecRepository dmcRepository;
    private final DossierRepository dossierRepository;
    private final OffreRepository offres;
    private final PvExamenRepository pvRepository;
    private final PieceJointeDossierRepository pieces;
    private final TypePieceJointeRepository typesPiece;
    private final FicheMarcheRepository ficheRepository;
    private final DocumentFicheMarcheRepository documentRepository;

    public AttributionService(AttributionRepository attributions, EvaluationService evaluation, SeanceService seance, SaisieService saisie,
            ValeursPpmService valeursPpm, FicheMarcheService fiches, EntrepriseCandidatService entreprises, GenerateurDocumentsFiche generateur,
            DossierMecRepository dmcRepository, DossierRepository dossierRepository, OffreRepository offres, PvExamenRepository pvRepository,
            PieceJointeDossierRepository pieces, TypePieceJointeRepository typesPiece, FicheMarcheRepository ficheRepository,
            DocumentFicheMarcheRepository documentRepository) {
        this.attributions = attributions;
        this.evaluation = evaluation;
        this.seance = seance;
        this.saisie = saisie;
        this.valeursPpm = valeursPpm;
        this.fiches = fiches;
        this.entreprises = entreprises;
        this.generateur = generateur;
        this.dmcRepository = dmcRepository;
        this.dossierRepository = dossierRepository;
        this.offres = offres;
        this.pvRepository = pvRepository;
        this.pieces = pieces;
        this.typesPiece = typesPiece;
        this.ficheRepository = ficheRepository;
        this.documentRepository = documentRepository;
    }

    // ------------------------------------------------------------------ §B1 le lot et son état

    /** L'attribution de la procédure, lot par lot : CAO, responsable, PRMP, UGPM ; 404 tant que l'évaluation n'est pas ouverte. */
    @Transactional(readOnly = true)
    public AttributionDto lire(Long idDmc) {
        evaluation.controlerLecture(idDmc);
        EvaluationDto ev = evaluation.vueSansGarde(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("L'évaluation de cette procédure n'est pas ouverte."));
        List<AttributionDto.LotAttribution> lots = new ArrayList<>();
        for (EvaluationDto.Lot l : ev.lots()) {
            if (!Evaluation.CLOSE.equals(ev.etat())) {
                lots.add(new AttributionDto.LotAttribution(l.lot(), EN_EVALUATION, null, null, false));
                continue;
            }
            Attribution a = attributions.findById(new Attribution.Cle(idDmc, l.lot())).orElse(null);
            AttributionDto.DossierMarche dm = a == null || a.getIdDossier() == null ? null : dossierMarche(a);
            String etat = a == null ? PROPOSE : dm != null && dm.avis() != null ? AVIS_RENDU : a.getEtat();
            lots.add(new AttributionDto.LotAttribution(l.lot(), etat, l.proposition(), dm, a != null && a.getProjetPdf() != null));
        }
        return new AttributionDto(idDmc, lots);
    }

    // ------------------------------------------------------------------ §B2 le dossier de marché

    /**
     * Crée le dossier de marché d'un lot attribuable (PRMP ou son UGPM) et y joint d'office le projet de marché, le cahier des charges
     * (DAO complet), le devis estimatif (bordereau de l'offre proposée), le PV d'ouverture et le rapport d'évaluation : 409
     * {@code EVALUATION_NON_CLOSE}, {@code LOT_INFRUCTUEUX}, {@code DOSSIER_EXISTANT} (avec {@code idDossier}) ; 404 lot inconnu.
     */
    public AttributionDto creerDossier(Long idDmc, Integer lot) {
        exigerPrmp(idDmc);
        EvaluationDto ev = evaluation.vueSansGarde(idDmc)
                .orElseThrow(() -> new ResourceNotFoundException("L'évaluation de cette procédure n'est pas ouverte."));
        if (!Evaluation.CLOSE.equals(ev.etat())) {
            throw new BusinessRuleException("Le dossier de marché se crée une fois le rapport d'évaluation signé.", "EVALUATION_NON_CLOSE");
        }
        EvaluationDto.Lot l = ev.lots().stream().filter(x -> Objects.equals(x.lot(), lot)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable dans l'évaluation : " + lot + "."));
        Attribution existante = attributions.findById(new Attribution.Cle(idDmc, lot)).orElse(null);
        if (existante != null && existante.getIdDossier() != null) {
            throw new BusinessRuleException("Le dossier de marché de ce lot existe déjà : " + existante.getIdDossier() + ".", "DOSSIER_EXISTANT",
                    existante.getIdDossier());
        }
        EvaluationDto.Proposition p = l.proposition();
        if (p == null || p.infructueux() || p.idOffre() == null) {
            throw new BusinessRuleException("Le rapport propose de déclarer ce lot infructueux : pas de dossier de marché.", "LOT_INFRUCTUEUX");
        }
        DossierMec dmc = dmcRepository.findById(idDmc).orElseThrow(() -> new ResourceNotFoundException("DMC introuvable : " + idDmc));
        Map<String, String> plan = valeursPpm.lire(dmc.getIdDetail()).valeurs();
        String sousType = Objects.toString(plan.get("MODE"), "").toLowerCase(Locale.FRENCH).contains("restreint") ? "MAOR" : "MAOO";
        ValeursPpmService.EnTete enTete = valeursPpm.enTete(dmc.getIdDetail());
        Dossier dossier = saisie.creerDossierMarche(sousType, enTete.idLocalite(), enTete.idEntiteContract());
        LocalDateTime maintenant = LocalDateTime.now();
        Attribution a = existante != null ? existante : new Attribution();
        a.setIdDmc(idDmc);
        a.setLot(lot);
        a.setEtat(Attribution.AU_CONTROLE);
        a.setIdOffreProposee(p.idOffre());
        a.setIdDossier(dossier.getIdDossier());
        a.setDossierCreeLe(maintenant);
        a.setDossierCreePar(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        // Le projet de marché (Q1) : produit par le serveur, Word et PDF.
        Offre offre = offres.findById(p.idOffre()).orElseThrow();
        for (GenerateurDocumentsFiche.Fichier f : generateur.generer(projet(idDmc, lot, p, offre, plan, ev.lots().size() > 1))) {
            if ("pdf".equals(f.extension())) {
                a.setProjetPdf(f.contenu());
            } else if ("docx".equals(f.extension())) {
                a.setProjetDocx(f.contenu());
            }
        }
        attributions.save(a);
        String suffixe = "_" + idDmc + "_lot" + lot + ".pdf";
        int jointes = 0;
        jointes += joindre(dossier, "PROJET_MARCHE", "projet-de-marche" + suffixe, a.getProjetPdf());
        jointes += joindreCahierDesCharges(dossier, idDmc);
        jointes += joindre(dossier, "DEVIS_ESTIMATIF", "bordereau-offre-" + offre.getNumero() + suffixe,
                seance.bordereauPdf(p.idOffre()).orElse(null));
        jointes += joindre(dossier, "PV_OUVERTURE", "pv-ouverture_" + idDmc + ".pdf", seance.pvSigne(idDmc));
        jointes += joindre(dossier, "RAPPORT_ANALYSE", "rapport-evaluation_" + idDmc + ".pdf", evaluation.rapportSigne(idDmc));
        evaluation.tracerAttribution(idDmc, "DOSSIER_MARCHE", "Lot " + lot + " : dossier de marché " + dossier.getIdDossier() + " (" + sousType
                + ") créé pour l'offre n° " + p.numero() + " (" + p.candidat() + "), " + jointes + " pièce(s) jointe(s) d'office");
        return lire(idDmc);
    }

    /** Le projet de marché du lot (PDF, ou Word avec {@code docx}) : mêmes lecteurs ; 404 tant qu'il n'est pas produit. */
    @Transactional(readOnly = true)
    public byte[] projet(Long idDmc, Integer lot, boolean docx) {
        evaluation.controlerLecture(idDmc);
        Attribution a = attributions.findById(new Attribution.Cle(idDmc, lot))
                .orElseThrow(() -> new ResourceNotFoundException("Le projet de marché de ce lot n'est pas produit."));
        byte[] b = docx ? a.getProjetDocx() : a.getProjetPdf();
        if (b == null) {
            throw new ResourceNotFoundException("Le projet de marché de ce lot n'est pas produit.");
        }
        return b;
    }

    private AttributionDto.DossierMarche dossierMarche(Attribution a) {
        Dossier d = dossierRepository.findById(a.getIdDossier()).orElse(null);
        if (d == null) {
            return null;
        }
        String avis = pvRepository.findSignesParDossier(d.getIdDossier()).stream().reduce((x, y) -> y).map(PvExamen::getIdAvis).orElse(null);
        return new AttributionDto.DossierMarche(d.getIdDossier(), d.getIdSousType(), d.getStatut(), avis, a.getDossierCreeLe(), a.getDossierCreePar());
    }

    /** Joint une pièce produite au dossier ; 0 sans contenu ou sans type de pièce de ce code au référentiel (journal applicatif). */
    private int joindre(Dossier d, String code, String nom, byte[] contenu) {
        if (contenu == null) {
            return 0;
        }
        TypePieceJointe type = typesPiece.findFirstByCode(code).orElse(null);
        if (type == null) {
            log.warn("[ATTRIBUTION] aucun type de pièce de code {} : pièce non jointe au dossier {}", code, d.getIdDossier());
            return 0;
        }
        PieceJointeDossier p = new PieceJointeDossier();
        p.setIdDossier(d.getIdDossier());
        p.setIdTypePiece(type.getIdTypePiece());
        p.setNomFichier(nom);
        p.setContenu(contenu);
        p.setFormat("PDF");
        p.setTaille((long) contenu.length);
        p.setDateUpload(LocalDateTime.now());
        p.setApresLettreRenvoi(false);
        pieces.save(p);
        return 1;
    }

    /** Le cahier des charges : le DAO complet de la dernière version validée, à défaut ses documents séparés (PDF). */
    private int joindreCahierDesCharges(Dossier d, Long idDmc) {
        FicheMarche validee = ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc).stream()
                .filter(f -> StatutFicheMarche.VALIDEE.name().equals(f.getStatut())).reduce((x, y) -> y).orElse(null);
        if (validee == null) {
            return 0;
        }
        List<DocumentFicheMarche> pdfs = documentRepository.findByIdFicheOrderByIdDocumentAsc(validee.getIdFiche()).stream()
                .filter(x -> "pdf".equals(x.getExtension()) && !DocumentsFicheMarcheService.TYPES_PUBLICATION.contains(x.getType())).toList();
        if (pdfs.stream().anyMatch(x -> DaoCompletService.TYPE.equals(x.getType()))) {
            pdfs = pdfs.stream().filter(x -> DaoCompletService.TYPE.equals(x.getType())).toList();
        }
        int n = 0;
        for (DocumentFicheMarche x : pdfs) {
            n += joindre(d, "CAHIER_CHARGES", x.getNomFichier(), x.getContenu());
        }
        return n;
    }

    /**
     * Le projet de marché (Q1 : produit par le serveur) : les parties, l'objet, les pièces constitutives (le DAO, l'offre retenue,
     * le CCAG), le montant hors taxes (prix corrigé − rabais) et en lettres, le délai de l'acte d'engagement, les signatures.
     */
    private DocumentLibre projet(Long idDmc, Integer lot, EvaluationDto.Proposition p, Offre offre, Map<String, String> plan, boolean allotie) {
        FicheMarcheService.EtatVersion v = fiches.etatValide(idDmc).orElse(null);
        String numero = v == null || v.etat().getValeurs() == null ? null : v.etat().getValeurs().get("B02-OB-03");
        String objet = v == null ? null : v.etat().getDesignationMarche();
        EntrepriseCandidatDto.Entreprise e = null;
        try {
            e = entreprises.lire(offre.getIdCandidat());
        } catch (RuntimeException ignore) {
            e = null;
        }
        List<DocumentLibre.Element> el = new ArrayList<>();
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.TITRE, "PROJET DE MARCHÉ"));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.CENTRE, (objet == null ? "" : objet) + (allotie ? " — lot " + lot : "")));
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        para(el, "Entre :");
        para(el, Objects.toString(plan.get("ENTITE"), "l'Autorité contractante") + (plan.get("MINISTERE") == null ? "" : " (" + plan.get("MINISTERE") + ")")
                + ", représentée par sa Personne responsable des marchés publics" + (plan.get("PRMP") == null ? "" : ", " + plan.get("PRMP"))
                + ", ci-après « l'Autorité contractante »,");
        para(el, "et :");
        para(el, offre.getRaisonSociale() + ", NIF " + offre.getNif() + (e == null || e.adresse() == null ? "" : ", " + e.adresse())
                + (e == null || e.representant() == null ? "" : ", représentée par " + e.representant().prenom() + " " + e.representant().nom()
                        + (e.representant().fonction() == null ? "" : ", " + e.representant().fonction()))
                + ", ci-après « le Titulaire ».");
        sous(el, "Article 1 — Objet");
        para(el, "Le présent marché a pour objet : " + Objects.toString(objet, "—") + (allotie ? ", lot " + lot : "") + ", issu de l'appel d'offres"
                + (numero == null ? "" : " n° " + numero) + ".");
        sous(el, "Article 2 — Pièces constitutives");
        para(el, "Le marché est constitué, par ordre de priorité : l'acte d'engagement du Titulaire ; le cahier des clauses administratives "
                + "particulières (ou le cahier des prescriptions spéciales) et ses annexes, les spécifications techniques ; l'offre du Titulaire "
                + "(offre n° " + offre.getNumero() + "), dont le bordereau des prix ; le cahier des clauses administratives générales — tels "
                + "qu'ils figurent au dossier d'appel d'offres et à l'offre retenue, sans modification substantielle.");
        sous(el, "Article 3 — Montant");
        para(el, "Le montant du marché est fixé à " + (p.montant() == null ? "……" : FormulairesEnLigne.lisible(p.montant()) + " Ariary hors taxes ("
                + NombreEnLettres.cardinal(p.montant().longValue()) + " ariary)") + ", tel qu'il résulte de l'évaluation des offres (prix "
                + "corrigé, rabais déduit).");
        sous(el, "Article 4 — Délai d'exécution");
        para(el, "Le délai d'exécution est celui de l'acte d'engagement : " + Objects.toString(p.delai(), "……") + ".");
        sous(el, "Article 5 — Entrée en vigueur");
        para(el, "Le marché prend effet à sa notification au Titulaire, après son approbation et l'avis de l'organe de contrôle.");
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.VIDE, ""));
        para(el, "Pour le Titulaire : ……………………………………  (nom, qualité, date et signature)");
        para(el, "Pour l'Autorité contractante, la Personne responsable des marchés publics : ……………………………………  (date et signature)");
        return new DocumentLibre("PROJET_MARCHE", allotie ? lot : null, el, "Procédure " + idDmc + " — projet de marché" + (allotie ? ", lot " + lot : ""));
    }

    private static void para(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.PARA, t));
    }

    private static void sous(List<DocumentLibre.Element> el, String t) {
        el.add(new DocumentLibre.Paragraphe(DocumentLibre.Style.SOUS_TITRE, t));
    }

    /** La PRMP de la fiche, ou son UGPM (Q : la création d'un dossier leur est ouverte, comme le dossier DAO). */
    private void exigerPrmp(Long idDmc) {
        ProfilUtilisateur p = CurrentUser.profil().orElse(null);
        if (p != ProfilUtilisateur.PRMP && p != ProfilUtilisateur.UGPM) {
            throw new AccessDeniedException("Le dossier de marché se crée par la PRMP de la fiche (ou son UGPM).");
        }
        fiches.controlerLecture(idDmc);
    }
}
