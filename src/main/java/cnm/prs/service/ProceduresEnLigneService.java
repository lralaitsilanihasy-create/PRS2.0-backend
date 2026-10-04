package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DocumentFicheDto;
import cnm.prs.dto.ProcedureEnLigneDto;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.entity.CompteCandidat;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.Entreprise;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Lot;
import cnm.prs.entity.RetraitDao;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.EntrepriseRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.LotRepository;
import cnm.prs.repository.RetraitDaoRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ 2026-10-04 (demande front « soumission en ligne », lot 1c, §B8) — les <strong>procédures ouvertes en ligne</strong>
 * et le <strong>retrait du DAO</strong>.
 * <p>
 * Une procédure figure dans la liste publique si sa fiche a une version <strong>validée</strong> (la dernière), en mode
 * {@code ELECTRONIQUE} (cadrage {@code modeRemise}), <strong>lancée</strong> (un avis spécifique imprimé sur l'une de ses
 * versions : les lettres d'invitation des prestations intellectuelles, liste restreinte, n'ouvrent rien au public), dont
 * la date limite de remise n'est pas passée, et qui n'exige que la signature <strong>Simple</strong> (Q5 : la plateforme
 * ne sait pas encore faire mieux ; {@code B04-SE-05} vide vaut Simple). Lue par son identifiant, une procédure close
 * reste visible ({@code etat = CLOSE}) ; hors de ces critères, 404 — sans dire pourquoi.
 * <p>
 * Les champs sont lus sur la fiche validée, tels que les documents les impriment (champ fermé ou d'une autre forme :
 * {@code null}) : aucun paramètre interne (V50, ADR-0010). Le retrait est libre pour tout candidat connecté et journalisé
 * à chaque téléchargement ({@code t_retrait_dao}, V65) ; la PRMP de la fiche lit ce registre.
 */
@Service
public class ProceduresEnLigneService {

    static final String CHAMP_AUTORITE = "B01-AC-01";
    static final String CHAMP_HEURE_REFERENCE = "B04-SE-04";
    static final String CHAMP_SIGNATURE = "B04-SE-05";
    static final String CHAMP_FORMATS = "B04-SE-07";
    static final String CHAMP_TAILLE_FICHIER = "B04-SE-08";
    static final String CHAMP_TAILLE_OFFRE = "B04-SE-09";
    static final String CHAMP_ASSISTANCE = "B04-SE-14";

    public static final String A_VENIR = "A_VENIR";
    public static final String OUVERTE = "OUVERTE";
    public static final String CLOSE = "CLOSE";

    private final FicheMarcheRepository ficheRepository;
    private final FicheMarcheService fiches;
    private final DocumentsFicheMarcheService documents;
    private final DocumentFicheMarcheRepository documentRepository;
    private final LotRepository lotRepository;
    private final RetraitDaoRepository retraitRepository;
    private final CompteCandidatRepository compteRepository;
    private final EntrepriseRepository entrepriseRepository;

    public ProceduresEnLigneService(FicheMarcheRepository ficheRepository, FicheMarcheService fiches,
            DocumentsFicheMarcheService documents, DocumentFicheMarcheRepository documentRepository, LotRepository lotRepository,
            RetraitDaoRepository retraitRepository, CompteCandidatRepository compteRepository,
            EntrepriseRepository entrepriseRepository) {
        this.ficheRepository = ficheRepository;
        this.fiches = fiches;
        this.documents = documents;
        this.documentRepository = documentRepository;
        this.lotRepository = lotRepository;
        this.retraitRepository = retraitRepository;
        this.compteRepository = compteRepository;
        this.entrepriseRepository = entrepriseRepository;
    }

    /** La procédure lue, et sa fiche validée. */
    private record Lue(ProcedureEnLigneDto dto, FicheMarche fiche, FicheMarcheService.EtatVersion etat) {
    }

    // ------------------------------------------------------------------ public

    /** La liste publique : procédures à venir ou ouvertes, la date limite la plus proche d'abord. */
    @Transactional(readOnly = true)
    public List<ProcedureEnLigneDto> lister() {
        LocalDateTime maintenant = LocalDateTime.now();
        List<ProcedureEnLigneDto> out = new ArrayList<>();
        for (FicheMarche f : ficheRepository.findDernieresValidees()) {
            // tri grossier avant la lecture complète de la fiche : le cadrage enregistré doit dire « électronique »
            if (f.getCadrage() == null || !f.getCadrage().contains(RemiseElectronique.ELECTRONIQUE)) {
                continue;
            }
            lire(f.getIdDmc(), maintenant).map(Lue::dto).filter(d -> !CLOSE.equals(d.etat())).ifPresent(out::add);
        }
        out.sort(Comparator.comparing(ProcedureEnLigneDto::dateLimite, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProcedureEnLigneDto::idDmc));
        return out;
    }

    /** Une procédure par son identifiant : 404 hors des critères de la liste (close : lisible). */
    @Transactional(readOnly = true)
    public ProcedureEnLigneDto procedure(Long idDmc) {
        return exiger(idDmc).dto();
    }

    // ------------------------------------------------------------------ candidat

    /**
     * Les documents du DAO à retirer : ceux de la dernière version validée, sans l'avis ni les lettres d'invitation.
     * {@code code} = le nom du fichier (unique dans une version).
     */
    @Transactional(readOnly = true)
    public List<ProcedureEnLigneDto.Document> documents(Long idDmc) {
        Lue l = exiger(idDmc);
        return documents.lister(l.fiche(), l.etat().categorie()).stream()
                .map(d -> new ProcedureEnLigneDto.Document(d.nomFichier(), d.libelle(), d.version(),
                        d.tailleOctets() == null ? 0 : d.tailleOctets()))
                .toList();
    }

    /** Un document du DAO, retiré par le candidat {@code idCandidat} : le retrait est journalisé. 404 inconnu. */
    @Transactional
    public DocumentFicheMarche retirer(Long idDmc, String code, String idCandidat) {
        Lue l = exiger(idDmc);
        DocumentFicheMarche d = documentRepository.findByIdFicheOrderByIdDocumentAsc(l.fiche().getIdFiche()).stream()
                .filter(x -> !DocumentsFicheMarcheService.TYPES_PUBLICATION.contains(x.getType()))
                .filter(x -> x.getNomFichier() != null && x.getNomFichier().equals(code))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable : " + code + "."));
        Integer idEntreprise = entrepriseRepository.findByIdCandidat(idCandidat).map(Entreprise::getIdEntreprise).orElse(null);
        retraitRepository.save(new RetraitDao(null, idDmc, idCandidat, idEntreprise, d.getNomFichier(),
                l.fiche().getNumeroVersion(), LocalDateTime.now()));
        return d;
    }

    // ------------------------------------------------------------------ PRMP

    /** Le registre des retraits, du plus ancien au plus récent : PRMP de la fiche seule (403), 404 DMC inconnu. */
    @Transactional(readOnly = true)
    public List<ProcedureEnLigneDto.Retrait> retraits(Long idDmc) {
        if (CurrentUser.profil().orElse(null) != ProfilUtilisateur.PRMP) {
            throw new AccessDeniedException("Le registre des retraits du DAO se lit par la PRMP de la fiche.");
        }
        fiches.controlerLecture(idDmc);
        List<ProcedureEnLigneDto.Retrait> out = new ArrayList<>();
        for (RetraitDao r : retraitRepository.findByIdDmcOrderByDateRetraitAscIdRetraitAsc(idDmc)) {
            CompteCandidat c = compteRepository.findById(r.getIdCandidat()).orElse(null);
            Entreprise e = r.getIdEntreprise() == null ? null : entrepriseRepository.findById(r.getIdEntreprise()).orElse(null);
            out.add(new ProcedureEnLigneDto.Retrait(r.getDateRetrait(), c == null ? r.getIdCandidat() : c.getEmail(),
                    e == null ? null : e.getRaisonSociale(), e == null ? null : e.getNif(), r.getCodeDocument(),
                    r.getVersionFiche()));
        }
        return out;
    }

    // ------------------------------------------------------------------ lecture

    private Lue exiger(Long idDmc) {
        return lire(idDmc, LocalDateTime.now())
                .orElseThrow(() -> new ResourceNotFoundException("Procédure en ligne introuvable : " + idDmc + "."));
    }

    /** La procédure si elle remplit les critères (close comprise), vide sinon. */
    private Optional<Lue> lire(Long idDmc, LocalDateTime maintenant) {
        FicheMarcheService.EtatVersion etat;
        try {
            etat = fiches.etatValide(idDmc).orElse(null);
        } catch (ResourceNotFoundException | cnm.prs.exception.BusinessRuleException e) {
            return Optional.empty();   // DMC inconnu ou qui n'est pas un DAO
        }
        if (etat == null || !RemiseElectronique.electronique(etat.etat().getCadrage())) {
            return Optional.empty();
        }
        if (RemiseElectronique.rangNiveau(etat.valeur(CHAMP_SIGNATURE)) > 0) {
            return Optional.empty();   // Q5 : Avancée ou Qualifiée, pas encore
        }
        List<FicheMarche> versions = ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc);
        List<DocumentFicheDto> avis = documents.listerAvis(versions).stream()
                .filter(d -> DocumentsFicheMarcheService.TYPE_AVIS.equals(d.type())).toList();
        if (avis.isEmpty()) {
            return Optional.empty();   // pas lancée
        }
        FicheMarche fiche = versions.stream().filter(v -> v.getNumeroVersion().equals(etat.version())).findFirst().orElse(null);
        if (fiche == null) {
            return Optional.empty();
        }
        LocalDateTime limite = dateLimite(etat);
        LocalDateTime ouvertureDepots = RemiseElectronique.dateHeureLue(brute(etat, RemiseElectronique.OUVERTURE_DEPOTS));
        String etatProcedure = limite != null && !maintenant.isBefore(limite) ? CLOSE
                : ouvertureDepots != null && maintenant.isBefore(ouvertureDepots) ? A_VENIR : OUVERTE;

        String numeroDao = brute(etat, DocumentsFicheMarcheService.CHAMP_NUMERO_DAO);
        ProcedureEnLigneDto dto = new ProcedureEnLigneDto(idDmc,
                numeroDao != null ? numeroDao : etat.etat().getRefeDossier(),
                etat.etat().getDesignationMarche(),
                etat.valeur(CHAMP_AUTORITE),
                etat.categorie(),
                lots(etat),
                datePublication(avis, etat),
                RemiseElectronique.isoMinute(ouvertureDepots),
                RemiseElectronique.isoMinute(limite),
                etat.valeur(CHAMP_HEURE_REFERENCE),
                Optional.ofNullable(etat.valeur(CHAMP_SIGNATURE)).orElse(RemiseElectronique.NIVEAUX.get(0)),
                ChampFicheMarche.liste(brute(etat, CHAMP_FORMATS)),
                entier(brute(etat, CHAMP_TAILLE_FICHIER)),
                entier(brute(etat, CHAMP_TAILLE_OFFRE)),
                etat.valeur(CHAMP_ASSISTANCE),
                etatProcedure);
        return Optional.of(new Lue(dto, fiche, etat));
    }

    /**
     * La date limite de remise : {@code B04-LR-03} + {@code B04-LR-04} (fournitures, l'heure manquante vaut 00:00) ; à défaut
     * {@code B04-CP-02} (contrat-cadre) ; à défaut {@code B04-OV-02} (travaux) — même ordre que les formulaires du candidat.
     */
    static LocalDateTime dateLimite(FicheMarcheService.EtatVersion etat) {
        String jour = brute(etat, FormulairesCandidat.REMISE_OFFRES);
        if (jour != null) {
            LocalDateTime avecHeure = RemiseElectronique.echeance(jour, brute(etat, "B04-LR-04"));
            return avecHeure != null ? avecHeure : RemiseElectronique.dateHeureLue(jour);
        }
        LocalDateTime cc = RemiseElectronique.dateHeureLue(brute(etat, FormulairesCandidat.REMISE_OFFRES_CONTRAT_CADRE));
        return cc != null ? cc : RemiseElectronique.dateHeureLue(brute(etat, FormulairesCandidat.REMISE_OFFRES_TRAVAUX));
    }

    /** La date de la première publication saisie à l'impression de l'avis ; à défaut {@code B04-SE-17}. */
    private static String datePublication(List<DocumentFicheDto> avis, FicheMarcheService.EtatVersion etat) {
        DocumentFicheDto premier = avis.get(avis.size() - 1);   // listerAvis : du plus récent au plus ancien
        Object saisie = premier.publication() == null ? null : premier.publication().get("datePublication");
        if (saisie != null && !String.valueOf(saisie).isBlank()) {
            return String.valueOf(saisie);
        }
        return RemiseElectronique.isoMinute(RemiseElectronique.dateHeureLue(brute(etat, RemiseElectronique.PUBLICATION_AVIS)));
    }

    /** Les lots du plan (ligne courante), numérotés dans l'ordre ; vide pour un marché non alloti. */
    private List<ProcedureEnLigneDto.Lot> lots(FicheMarcheService.EtatVersion etat) {
        Integer idDetail = etat.etat().getIdDetailCourant() != null ? etat.etat().getIdDetailCourant() : etat.etat().getIdDetail();
        if (idDetail == null) {
            return List.of();
        }
        List<Lot> lots = new ArrayList<>(lotRepository.findByIdDetail(idDetail));
        lots.sort(Comparator.comparing(Lot::getIdLot));
        List<ProcedureEnLigneDto.Lot> out = new ArrayList<>();
        for (int i = 0; i < lots.size(); i++) {
            Lot l = lots.get(i);
            out.add(new ProcedureEnLigneDto.Lot(i + 1, l.getDesignationLot() == null ? "Lot " + (i + 1) : l.getDesignationLot()));
        }
        return out;
    }

    /** La valeur enregistrée d'un champ, si la version l'imprime ({@link FicheMarcheService.EtatVersion#valeur}) ; sinon {@code null}. */
    private static String brute(FicheMarcheService.EtatVersion etat, String code) {
        if (etat.valeur(code) == null) {
            return null;
        }
        Map<String, String> v = etat.etat().getValeurs();
        String s = v == null ? null : v.get(code);
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Integer entier(String v) {
        if (v == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(v.replace(',', '.').replace(" ", "")).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
