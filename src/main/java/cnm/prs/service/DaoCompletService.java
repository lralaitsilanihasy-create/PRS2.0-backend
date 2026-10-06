package cnm.prs.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.FicheMarche;
import cnm.prs.enums.CategorieDao;
import cnm.prs.enums.TypeMarcheDao;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.SpecificationsFicheRepository;

/**
 * ⚠️ <strong>Le DAO complet</strong> (demande front du 2026-10-06 ; arbitrages : un seul document, Word sur le serveur ; V73) :
 * un {@code .docx} et un {@code .pdf} par version validée. Il <strong>assemble</strong> les documents déjà produits, sans rien
 * réécrire (H1). Les classeurs ({@code xlsx} : bordereau des prix, DQE, tableau de conformité) restent à part.
 * <p>
 * ⚠️ Recette du 06/10 (§C) : la page de garde des DAO réels (C1 : emblème, ministère, PRMP, UGPM, intitulé portant le mode, lots,
 * « Lancé le », financement, imputation, compte) ; le <strong>plan de l'ARMP</strong> (C3), tel que le sommaire général des
 * documents types le numérote : première partie (1.1 instructions, 1.2 données particulières, 1.3 formulaires et leurs
 * sous-parties), deuxième partie (2.1 acte d'engagement, 2.2 cahier des prescriptions spéciales avec les spécifications techniques,
 * 2.3 CCAG) ; le nom du fichier sur le numéro du DAO (C5).
 */
@Service
public class DaoCompletService {

    private static final Logger log = LoggerFactory.getLogger(DaoCompletService.class);
    public static final String TYPE = "DAO_COMPLET";

    /** Les intitulés des formulaires, tels que le sommaire général de l'ARMP les écrit (C3). */
    private static final Map<String, String> FORMULAIRES = Map.of(
            "A1", "A1 - Identification du Candidat",
            "A2", "A2 - Capacités techniques",
            "A3", "A3 - Capacités financières",
            "A4", "A4 - Antécédents du Candidat pour des marchés de même nature");
    private static final Map<String, String> GARANTIES_FOURNITURES = Map.of(
            "C1", "C1 - Modèle de garantie bancaire",
            "C2", "C2 - Modèle de caution personnelle et solidaire");
    private static final Map<String, String> GARANTIES_TRAVAUX = Map.of(
            "C1", "B1 - Garantie bancaire",
            "C2", "B2 - Caution de soumission");

    private final DaoCompletWord word;
    private final DocumentsFicheMarcheService documents;
    private final DocumentFicheMarcheRepository documentRepository;
    private final SpecificationsFicheRepository specificationsRepository;
    private final ValeursPpmService valeursPpm;

    public DaoCompletService(DaoCompletWord word, DocumentsFicheMarcheService documents, DocumentFicheMarcheRepository documentRepository,
            SpecificationsFicheRepository specificationsRepository, ValeursPpmService valeursPpm) {
        this.word = word;
        this.documents = documents;
        this.documentRepository = documentRepository;
        this.specificationsRepository = specificationsRepository;
        this.valeursPpm = valeursPpm;
    }

    public boolean actif() {
        return word.actif();
    }

    /**
     * Produit le DAO complet d'une version validée s'il n'existe pas encore ; {@code true} s'il existe à l'issue. Un échec (Word
     * absent, délai dépassé) est journalisé et rend {@code false} : les documents séparés restent servis, rien n'est bloqué.
     */
    @Transactional
    public boolean assurer(FicheMarche fiche, FicheMarcheDto etat) {
        if (fiche == null || fiche.getIdFiche() == null) {
            return false;
        }
        List<DocumentFicheMarche> docs = documentRepository.findByIdFicheOrderByIdDocumentAsc(fiche.getIdFiche());
        if (docs.stream().anyMatch(d -> TYPE.equals(d.getType()))) {
            return true;
        }
        if (!word.actif()) {
            return false;
        }
        try {
            Map<String, String> plan = plan(etat);
            DaoCompletWord.Resultat r = word.assembler(garde(etat, plan), "SOMMAIRE GÉNÉRAL", entete(etat), parties(etat, fiche.getIdFiche(), docs));
            LocalDateTime maintenant = LocalDateTime.now();
            Integer idDetail = etat.getIdDetailCourant() != null ? etat.getIdDetailCourant() : etat.getIdDetail();
            // ⚠️ C5 (recette du 06/10) — le nom porte le numéro du DAO (B02-OB-03), à défaut la référence du plan.
            String refe = numero(etat);
            documents.enregistrer(fiche.getIdFiche(), List.of(
                    new DocumentsFicheMarcheService.Produit(TYPE, "docx",
                            DocumentsFicheMarcheService.nomFichier(TYPE, refe, idDetail, fiche.getNumeroVersion(), "docx"), r.docx(), null),
                    new DocumentsFicheMarcheService.Produit(TYPE, "pdf",
                            DocumentsFicheMarcheService.nomFichier(TYPE, refe, idDetail, fiche.getNumeroVersion(), "pdf"), r.pdf(), null)),
                    maintenant);
            log.info("[DAO_COMPLET] DMC {} version {} : {} Ko (docx), {} Ko (pdf)", fiche.getIdDmc(), fiche.getNumeroVersion(),
                    r.docx().length / 1024, r.pdf().length / 1024);
            return true;
        } catch (RuntimeException e) {
            log.warn("[DAO_COMPLET] DMC {} version {} non assemblé : {}", fiche.getIdDmc(), fiche.getNumeroVersion(), e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ le plan de l'ARMP (C3)

    List<DaoCompletWord.Partie> parties(FicheMarcheDto etat, Integer idFiche, List<DocumentFicheMarche> docs) {
        String categorie = categorie(etat);
        boolean pi = CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie);
        boolean travaux = CategorieDao.TRAVAUX.name().equals(categorie);
        boolean cc = contratCadre(etat);
        List<DocumentFicheMarche> word = docs.stream().filter(d -> "docx".equals(d.getExtension()))
                .filter(d -> !DocumentsFicheMarcheService.TYPES_PUBLICATION.contains(d.getType()) && !TYPE.equals(d.getType())).toList();
        Plan p = new Plan();

        // Première partie
        p.titre(pi ? "PREMIÈRE PARTIE : PROCÉDURE DE CONSULTATION" : "PREMIÈRE PARTIE : PROCÉDURE D'APPEL D'OFFRES", 1);
        if (pi) {
            p.titre("1.1. - Lettre d'invitation (adressée à chaque candidat, document à part)", 2);
            p.titre("1.2. - Instructions aux candidats (IC)", 2);
        } else {
            p.titre("1.1. - Instructions aux candidats", 2);
        }
        p.document(fixe(categorie, "IC"));
        p.titre(pi ? "1.3. - Données Particulières des Instructions aux Candidats (DPIC)"
                : cc ? "1.2. - Données Particulières d'Appel à Concurrence (DPAC)"
                        : "1.2. - Données Particulières de l'Appel d'Offres (DPAO)", 2);
        p.documents(choisir(word, List.of("DPAO", "DPAC", "DPIC")));
        List<DocumentFicheMarche> fiches = choisir(word, List.of("A1", "A2", "A3", "A4"));
        List<DocumentFicheMarche> garanties = choisir(word, List.of("C1", "C2"));
        if (!fiches.isEmpty() || !garanties.isEmpty()) {
            p.titre(pi ? "1.4. - Formulaires-types de soumission" : "1.3. - Formulaires de soumission", 2);
            if (!fiches.isEmpty()) {
                p.titre("A. - Modèles de fiches de renseignements", 3);
                for (DocumentFicheMarche d : fiches) {
                    p.titre(FORMULAIRES.getOrDefault(d.getType(), d.getType()), 4);
                    p.document(d.getContenu());
                }
            }
            if (!garanties.isEmpty()) {
                if (!travaux) {
                    p.titre("B. - Modèle d'attestation du fabricant - Non utilisé", 3);
                }
                p.titre(travaux ? "B. - Modèles de garantie de soumission" : "C. - Modèles de garantie de soumission", 3);
                for (DocumentFicheMarche d : garanties) {
                    p.titre((travaux ? GARANTIES_TRAVAUX : GARANTIES_FOURNITURES).getOrDefault(d.getType(), d.getType()), 4);
                    p.document(d.getContenu());
                }
            }
        }

        // Deuxième partie
        p.titre("DEUXIÈME PARTIE : MARCHÉ", 1);
        List<DocumentFicheMarche> ae = choisir(word, List.of("AE"));
        p.titre(cc ? "2.1. - Contrat-cadre valant Acte d'Engagement et Cahier des Clauses Administratives Particulières"
                : pi ? "2.1. - Modèle d'Acte d'Engagement" : "2.1. - Acte d'Engagement", 2);
        boolean parLot = ae.size() > 1 || ae.stream().anyMatch(d -> d.getLot() != null);
        for (DocumentFicheMarche d : ae) {
            if (parLot && d.getLot() != null) {
                p.titre("Lot " + d.getLot(), 3);
            }
            p.document(d.getContenu());
        }
        p.titre("2.2. - Cahier des Prescriptions Spéciales", 2);
        List<DocumentFicheMarche> ccap = choisir(word, List.of("CCAP"));
        if (!ccap.isEmpty()) {
            p.titre(pi ? "Cahier des Clauses Administratives Particulières et ses annexes"
                    : "Cahier des Clauses Administratives Particulières (CCAP) et ses annexes", 3);
            p.documents(ccap);
        }
        byte[] specifications = specificationsRepository.findById(idFiche).map(s -> s.getContenu()).orElse(null);
        List<DocumentFicheMarche> lf = choisir(word, List.of("LF"));
        if (specifications != null || !lf.isEmpty()) {
            p.titre(pi ? "Termes de références" : "Spécifications techniques", 3);
            if (specifications != null) {
                p.document(specifications);
            }
            for (DocumentFicheMarche d : lf) {
                p.titre("Annexe : Liste des fournitures et calendrier de livraison", 4);
                p.document(d.getContenu());
            }
        }
        p.titre("2.3. - Cahier des Clauses Administratives Générales" + (pi ? " applicable aux marchés publics de prestations intellectuelles"
                : travaux ? " applicable aux marchés publics de travaux"
                        : " applicable aux marchés publics de fournitures et de prestations de services courantes"), 2);
        p.document(fixe(categorie, "CCAG"));
        return p.fin();
    }

    /** Les parties en construction : les titres s'accumulent jusqu'au document qu'ils précèdent. */
    private static final class Plan {
        private final List<DaoCompletWord.Partie> parties = new ArrayList<>();
        private List<DaoCompletWord.Titre> titres = new ArrayList<>();

        void titre(String texte, int niveau) {
            titres.add(new DaoCompletWord.Titre(texte, niveau));
        }

        void document(byte[] docx) {
            parties.add(new DaoCompletWord.Partie(titres, docx));
            titres = new ArrayList<>();
        }

        void documents(List<DocumentFicheMarche> docs) {
            docs.forEach(d -> document(d.getContenu()));
        }

        /** Des titres restés sans document (une section vide) forment une partie à eux seuls. */
        List<DaoCompletWord.Partie> fin() {
            if (!titres.isEmpty()) {
                parties.add(new DaoCompletWord.Partie(titres, null));
            }
            return parties;
        }
    }

    /** Les documents de ces types, dans l'ordre des types puis des lots. */
    private static List<DocumentFicheMarche> choisir(List<DocumentFicheMarche> docs, List<String> types) {
        return docs.stream().filter(d -> types.contains(d.getType()))
                .sorted(Comparator.comparing((DocumentFicheMarche d) -> types.indexOf(d.getType()))
                        .thenComparing(d -> d.getLot() == null ? 0 : d.getLot()))
                .toList();
    }

    /** Le texte fixe de l'ARMP de la catégorie ({@code modeles/dao-fixes/<CATEGORIE>-<IC|CCAG>.docx}, rogné de sa couverture). */
    static byte[] fixe(String categorie, String partie) {
        String chemin = "/modeles/dao-fixes/" + categorie + "-" + partie + ".docx";
        try (InputStream in = DaoCompletService.class.getResourceAsStream(chemin)) {
            if (in == null) {
                throw new IllegalStateException("Texte fixe introuvable : " + chemin);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Texte fixe illisible : " + chemin, e);
        }
    }

    private static String categorie(FicheMarcheDto etat) {
        return etat.getCategorie() == null ? CategorieDao.FOURNITURES_SERVICES.name() : etat.getCategorie();
    }

    // ------------------------------------------------------------------ page de garde et en-tête (C1)

    /** Les valeurs brutes du plan (ministère, entité, mode, lots, financement, bénéficiaires, comptes) ; vide si illisibles. */
    private Map<String, String> plan(FicheMarcheDto etat) {
        try {
            return etat.getIdDetail() == null ? Map.of() : valeursPpm.lire(etat.getIdDetail()).valeurs();
        } catch (RuntimeException e) {
            log.warn("[DAO_COMPLET] valeurs du plan illisibles pour la ligne {} : {}", etat.getIdDetail(), e.getMessage());
            return Map.of();
        }
    }

    /**
     * La page de garde, de haut en bas comme celle des DAO réels (l'emblème est posé par le script) : ministère, autorité
     * contractante, PRMP, UGPM ; l'intitulé qui porte le mode ; le numéro, l'objet, les lots ; « Lancé le » ; financement,
     * imputation administrative, compte. Une valeur absente omet sa ligne ; « Lancé le » reste à compléter.
     */
    List<DaoCompletWord.LigneGarde> garde(FicheMarcheDto etat, Map<String, String> plan) {
        Map<String, String> p = plan == null ? Map.of() : plan;
        boolean pi = CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie(etat));
        List<DaoCompletWord.LigneGarde> l = new ArrayList<>();
        ligne(l, majuscules(p.get("MINISTERE")), 12, true);
        // ⚠️ D1 (contre-recette du 06/10) — l'entité n'est imprimée que si elle diffère du ministère hors accents, casse et blancs.
        if (p.get("ENTITE") != null && !comparable(p.get("ENTITE")).equals(comparable(p.get("MINISTERE")))) {
            ligne(l, majuscules(p.get("ENTITE")), 11, true);
        }
        ligne(l, "PERSONNE RESPONSABLE DES MARCHÉS PUBLICS", 11, true);
        ligne(l, "UNITÉ DE GESTION DE LA PASSATION DES MARCHÉS", 11, true);
        vide(l);
        String mode = p.get("MODE");
        String intitule = intitule(mode, pi);
        ligne(l, intitule, 20, true);
        if (mode != null && !intitule.contains(majuscules(mode))) {
            ligne(l, mode, 12, false);
        }
        ligne(l, numero(etat) == null ? null : "N° " + numero(etat), 13, true);
        vide(l);
        ligne(l, objet(etat), 13, true);
        String lots = p.get("LOTS_DESIGNATION");
        if (lots != null && lots.contains(" ; ")) {
            String[] parts = lots.split(" ; ");
            for (int i = 0; i < parts.length; i++) {
                String d = parts[i].trim();
                ligne(l, d.toLowerCase(Locale.FRENCH).startsWith("lot") ? d : "Lot " + (i + 1) + " : " + d, 11, false);
            }
        }
        vide(l);
        ligne(l, "Lancé le …………………………", 11, false);
        vide(l);
        ligne(l, p.get("FINANCEMENT") == null ? null : "Financement : " + p.get("FINANCEMENT"), 11, false);
        String imputation = p.get("BENEFICIAIRES") == null ? null : p.get("BENEFICIAIRES").replaceAll("\\s*\\([^)]*\\)", "").trim();
        ligne(l, imputation == null || imputation.isEmpty() ? null : "Imputation administrative : " + imputation, 11, false);
        ligne(l, p.get("COMPTES") == null ? null : "Compte : " + p.get("COMPTES"), 11, false);
        return l;
    }

    /** « DOSSIER D'APPEL D'OFFRES OUVERT » : le mode dans l'intitulé quand il est un appel d'offres ; sinon l'intitulé seul. */
    static String intitule(String mode, boolean pi) {
        if (pi) {
            return "DOSSIER DE CONSULTATION";
        }
        String m = mode == null ? "" : mode.trim().replace('’', '\'');
        return m.toLowerCase(Locale.FRENCH).startsWith("appel d'offres") ? "DOSSIER D'" + majuscules(m) : "DOSSIER D'APPEL D'OFFRES";
    }

    /** « DAO n° {B02-OB-03} — {objet} » (objet abrégé pour tenir sur la ligne). */
    String entete(FicheMarcheDto etat) {
        String o = objet(etat);
        if (o != null && o.length() > 90) {
            o = o.substring(0, 87) + "…";
        }
        boolean pi = CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie(etat));
        return (pi ? "Dossier de consultation" : "DAO") + (numero(etat) == null ? "" : " n° " + numero(etat)) + (o == null ? "" : " — " + o);
    }

    private static String numero(FicheMarcheDto etat) {
        String n = etat.getValeurs() == null ? null : etat.getValeurs().get(DocumentsFicheMarcheService.CHAMP_NUMERO_DAO);
        return n == null || n.isBlank() ? etat.getRefeDossier() : n.trim();
    }

    /** L'objet : la valeur figée de la fiche (champ PPM), à défaut la désignation de la ligne. */
    private static String objet(FicheMarcheDto etat) {
        String o = etat.getDesignationMarche();
        return o == null || o.isBlank() ? null : o.trim();
    }

    /** Sans accents, sans casse, blancs réduits : « Ministère des Travaux publics » = « MINISTERE DES TRAVAUX  PUBLICS ». */
    static String comparable(String s) {
        return s == null ? "" : java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('’', '\'').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String majuscules(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.FRENCH);
    }

    private static void ligne(List<DaoCompletWord.LigneGarde> l, String texte, int taille, boolean gras) {
        if (texte != null && !texte.isBlank()) {
            l.add(new DaoCompletWord.LigneGarde(texte.trim(), taille, gras));
        }
    }

    private static void vide(List<DaoCompletWord.LigneGarde> l) {
        l.add(new DaoCompletWord.LigneGarde("", 11, false));
    }


    static boolean contratCadre(FicheMarcheDto etat) {
        return Objects.equals(TypeMarcheDao.CONTRAT_CADRE.name(), etat.getTypeMarche());
    }
}
