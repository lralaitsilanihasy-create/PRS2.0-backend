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
    /** ⚠️ 2026-10-16 (charte, §B3) — le besoin de la version validée et le taux de TVA, pour les tableaux imprimés. */
    private final BesoinFiche besoin;
    private final ParametreService parametres;
    /** ⚠️ 2026-10-17 (charte travaux, T6) — le personnel et le matériel exigés, qui pré-remplissent les formulaires du candidat. */
    private final MoyensFiche moyens;

    public DaoCompletService(DaoCompletWord word, DocumentsFicheMarcheService documents, DocumentFicheMarcheRepository documentRepository,
            SpecificationsFicheRepository specificationsRepository, ValeursPpmService valeursPpm) {
        this(word, documents, documentRepository, specificationsRepository, valeursPpm, null, null);
    }

    public DaoCompletService(DaoCompletWord word, DocumentsFicheMarcheService documents, DocumentFicheMarcheRepository documentRepository,
            SpecificationsFicheRepository specificationsRepository, ValeursPpmService valeursPpm, BesoinFiche besoin, ParametreService parametres) {
        this(word, documents, documentRepository, specificationsRepository, valeursPpm, besoin, parametres, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DaoCompletService(DaoCompletWord word, DocumentsFicheMarcheService documents, DocumentFicheMarcheRepository documentRepository,
            SpecificationsFicheRepository specificationsRepository, ValeursPpmService valeursPpm, BesoinFiche besoin, ParametreService parametres,
            MoyensFiche moyens) {
        this.moyens = moyens;
        this.word = word;
        this.documents = documents;
        this.documentRepository = documentRepository;
        this.specificationsRepository = specificationsRepository;
        this.valeursPpm = valeursPpm;
        this.besoin = besoin;
        this.parametres = parametres;
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
            DaoCompletWord.Resultat r = word.assembler(charte(etat, plan, fiche.getNumeroVersion(),
                    fiche.getDateValidation() != null ? fiche.getDateValidation() : etat.getDateValidation()), parties(etat, fiche.getIdFiche(), docs));
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
                .filter(d -> !DocumentsFicheMarcheService.TYPES_PUBLICATION.contains(d.getType()) && !TYPE.equals(d.getType()))
                .filter(d -> !DocumentsFicheMarcheService.TYPES_PROJET.contains(d.getType()))   // ⚠️ R3b — le projet d'avis n'entre pas au DAO
                .filter(d -> !DocumentsFicheMarcheService.TYPES_ESTIMATIF.contains(d.getType()))   // ⚠️ V117 — l'estimatif n'entre jamais au DAO
                .toList();
        Plan p = new Plan();

        // Première partie
        p.titre(pi ? "PREMIÈRE PARTIE : PROCÉDURE DE CONSULTATION" : "PREMIÈRE PARTIE : PROCÉDURE D'APPEL D'OFFRES", 1);
        if (pi) {
            p.titre("1.1. - Lettre d'invitation (adressée à chaque candidat, document à part)", 2);
            p.titre("1.2. - Instructions aux candidats (IC)", 2);
        } else {
            p.titre("1.1. - Instructions aux candidats", 2);
        }
        p.document(CharteDao.fixe(fixe(categorie, "IC")));
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
                    p.document(CharteDao.partie(d.getContenu()));
                }
            }
            if (!garanties.isEmpty()) {
                if (!travaux) {
                    p.titre("B. - Modèle d'attestation du fabricant - Non utilisé", 3);
                }
                p.titre(travaux ? "B. - Modèles de garantie de soumission" : "C. - Modèles de garantie de soumission", 3);
                for (DocumentFicheMarche d : garanties) {
                    p.titre((travaux ? GARANTIES_TRAVAUX : GARANTIES_FOURNITURES).getOrDefault(d.getType(), d.getType()), 4);
                    p.document(CharteDao.partie(d.getContenu()));
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
            p.document(CharteDao.partie(d.getContenu()));
        }
        // ⚠️ 2026-10-16 (charte, §B3) — les tableaux du besoin : le bordereau en annexe 1 de l'acte d'engagement ; en travaux, le
        // bordereau des prix unitaires puis le détail quantitatif et estimatif.
        Tableaux t = tableaux(etat, idFiche);
        if (travaux) {
            if (t.bpu() != null) {
                p.titre("Annexe : Bordereau des prix unitaires", 3);
                p.document(t.bpu());
            }
            if (t.dqe() != null) {
                p.titre(forfaitaire(etat) ? "Annexe : Décomposition du prix global et forfaitaire" : "Annexe : Détail quantitatif et estimatif", 3);
                p.document(t.dqe());
            }
            // ⚠️ 2026-10-17 (charte travaux, T5) — le coefficient K1 et le cadre du sous-détail des prix, annexes de l'acte d'engagement.
            p.titre("Annexe : Mode de calcul du coefficient de majoration des déboursés K1", 3);
            p.document(FormulairesTravaux.k1(parametres == null ? null : parametres.tauxTva()));
            p.titre("Annexe : Cadre du sous-détail des prix", 3);
            p.document(FormulairesTravaux.sousDetail(contexte(etat), articles(idFiche)));
        } else if (t.bordereau() != null) {
            p.titre("Annexe 1 : Cadre du bordereau des prix", 3);
            p.document(t.bordereau());
        }
        p.titre("2.2. - Cahier des Prescriptions Spéciales", 2);
        List<DocumentFicheMarche> ccap = choisir(word, List.of("CCAP"));
        if (!ccap.isEmpty()) {
            p.titre(pi ? "Cahier des Clauses Administratives Particulières et ses annexes"
                    : "Cahier des Clauses Administratives Particulières (CCAP) et ses annexes", 3);
            p.documents(ccap);
        }
        byte[] specifications = specificationsRepository.findById(idFiche).map(s -> s.getContenu()).orElse(null);
        // ⚠️ 2026-10-16 (charte, §B3) — avec un besoin, la liste des fournitures est le tableau généré (le document LF, produit à part,
        // n'entre plus au DAO) ; le Word joint par la PRMP suit le tableau des spécifications, il ne le remplace pas.
        List<DocumentFicheMarche> lf = t.avecBesoin() ? List.of() : choisir(word, List.of("LF"));
        if (specifications != null || !lf.isEmpty() || t.specifications() != null || t.liste() != null || travaux) {
            p.titre(pi ? "Termes de références" : "Spécifications techniques", 3);
            if (t.specifications() != null) {
                p.document(t.specifications());
            }
            if (specifications != null) {
                p.document(specifications);   // ⚠️ charte (§B1) : le Word joint par la PRMP garde son contenu
            }
            if (t.liste() != null) {
                p.titre("Annexe : Liste des fournitures et calendrier de livraison", 4);
                p.document(t.liste());
            }
            for (DocumentFicheMarche d : lf) {
                p.titre("Annexe : Liste des fournitures et calendrier de livraison", 4);
                p.document(CharteDao.partie(d.getContenu()));
            }
            if (travaux) {
                formulairesTravaux(p, etat, idFiche);
            }
        }
        p.titre("2.3. - Cahier des Clauses Administratives Générales" + (pi ? " applicable aux marchés publics de prestations intellectuelles"
                : travaux ? " applicable aux marchés publics de travaux"
                        : " applicable aux marchés publics de fournitures et de prestations de services courantes"), 2);
        p.document(CharteDao.fixe(fixe(categorie, "CCAG")));
        return p.fin();
    }

    /** ⚠️ 2026-10-16 (charte, §B3) — les tableaux du besoin produits pour la version, chacun {@code null} s'il ne l'est pas. */
    record Tableaux(boolean avecBesoin, byte[] bordereau, byte[] specifications, byte[] liste, byte[] bpu, byte[] dqe) {
        static final Tableaux AUCUN = new Tableaux(false, null, null, null, null, null);
    }

    /**
     * Les tableaux du besoin de la version validée ({@code GET …/articles}), un tableau par lot dans l'ordre des lots du plan. Rien
     * sans besoin (l'avertissement du bloc B12 suffit) ni en prestations intellectuelles. La liste des fournitures et le calendrier :
     * appel d'offres de fournitures (quantité fixe ou à commandes) ; pour un contrat-cadre, seulement à marchés subséquents uniques
     * non fractionnés dont le délai est fixé par l'autorité (condition {@code DELAI-AC} de l'AE-CC).
     */
    Tableaux tableaux(FicheMarcheDto etat, Integer idFiche) {
        String categorie = categorie(etat);
        if (besoin == null || idFiche == null || CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie)) {
            return Tableaux.AUCUN;
        }
        List<cnm.prs.dto.ArticleBesoinDto> articles = besoin.lister(idFiche);
        if (articles.isEmpty()) {
            return Tableaux.AUCUN;
        }
        boolean cc = contratCadre(etat);
        Map<String, String> valeurs = etat.getValeurs() == null ? Map.of() : etat.getValeurs();
        TableauxBesoin.Contexte c = new TableauxBesoin.Contexte(TypeMarcheDao.A_COMMANDE.name().equals(etat.getTypeMarche()), cc, objet(etat),
                lots(etat), forfaitaire(etat));
        if (CategorieDao.TRAVAUX.name().equals(categorie)) {
            return new Tableaux(true, null, null, null, TableauxBesoin.bpu(c, articles),
                    TableauxBesoin.dqe(c, articles, parametres == null ? null : parametres.tauxTva()));
        }
        boolean liste = !cc || "Marchés uniques non fractionnés".equals(valeurs.get("B07-FS-01"))
                && "Fixés par l'autorité contractante".equals(valeurs.get("B07-DE-01"));
        return new Tableaux(true, TableauxBesoin.bordereau(c, articles), TableauxBesoin.specifications(c, articles),
                liste ? TableauxBesoin.liste(c, articles, valeurs) : null, null, null);
    }

    /**
     * ⚠️ 2026-10-17 (charte travaux, T6-T7) — les annexes des spécifications techniques des travaux : la liste des plans
     * ({@code B04-CD-03}), le personnel et le matériel affectés (pré-remplis depuis la version validée), l'engagement de location,
     * les plannings (délai d'exécution {@code B09-DL-01} de chaque lot, délai de garantie {@code B09-GT-01}) et le plan de charge.
     */
    private void formulairesTravaux(Plan p, FicheMarcheDto etat, Integer idFiche) {
        List<cnm.prs.dto.PersonnelExigeDto> personnel = moyens == null || idFiche == null ? List.of() : moyens.personnel(idFiche);
        List<cnm.prs.dto.MaterielExigeDto> materiel = moyens == null || idFiche == null ? List.of() : moyens.materiel(idFiche);
        TableauxBesoin.Contexte c = contexte(etat);
        Map<Integer, String> delais = new java.util.HashMap<>();
        for (TableauxBesoin.Lot lot : c.lots()) {
            delais.put(lot.numero(), valeurLot(etat, "B09-DL-01", lot.numero()));
        }
        Integer garantie = null;
        try {
            String g = valeurLot(etat, "B09-GT-01", null);
            garantie = g == null ? null : Integer.valueOf(g.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            // un délai de garantie illisible : douze mois
        }
        p.titre("Annexe 1 : Liste des plans", 4);
        p.document(FormulairesTravaux.listePlans(valeurLot(etat, "B04-CD-03", null)));
        p.titre("Annexe 2 : Liste du personnel affecté aux lots et modèle de curriculum vitae", 4);
        p.document(FormulairesTravaux.personnel(c.lots(), personnel));
        p.titre("Annexe 3 : Liste du matériel affecté aux lots", 4);
        p.document(FormulairesTravaux.materiel(materiel));
        p.titre("Annexe 4 : Modèle d'engagement de location de matériel", 4);
        p.document(FormulairesTravaux.location());
        p.titre("Annexe 5 : Modèles de plannings d'exécution", 4);
        p.document(FormulairesTravaux.plannings(c, articles(idFiche), personnel, materiel, delais, garantie));
        p.titre("Annexe 6 : Modèle de plan de charge", 4);
        p.document(FormulairesTravaux.planDeCharge());
    }

    /** Le contexte d'impression des tableaux et des formulaires : la forme, l'objet, les lots du plan. */
    private TableauxBesoin.Contexte contexte(FicheMarcheDto etat) {
        return new TableauxBesoin.Contexte(TypeMarcheDao.A_COMMANDE.name().equals(etat.getTypeMarche()), contratCadre(etat), objet(etat),
                lots(etat), forfaitaire(etat));
    }

    /** Le besoin de la version validée, vide sans besoin. */
    private List<cnm.prs.dto.ArticleBesoinDto> articles(Integer idFiche) {
        return besoin == null || idFiche == null ? List.of() : besoin.lister(idFiche);
    }

    /** ⚠️ 17/10 (T4) — un prix global et forfaitaire (cadrage {@code typePrix}) : son détail est une décomposition du prix. */
    static boolean forfaitaire(FicheMarcheDto etat) {
        return etat.getCadrage() != null && "FORFAITAIRE".equals(String.valueOf(etat.getCadrage().get("typePrix")));
    }

    /** Les lots du plan, avec leur intitulé ; une ligne non allotie n'en a qu'un, sans numéro. */
    private List<TableauxBesoin.Lot> lots(FicheMarcheDto etat) {
        int nb = etat.getNbLots() == null ? 1 : etat.getNbLots();
        if (!LotsFiche.alloti(nb)) {
            return List.of(new TableauxBesoin.Lot(null, null));
        }
        String designations = plan(etat).get("LOTS_DESIGNATION");
        String[] parts = designations == null ? new String[0] : designations.split(" ; ");
        List<TableauxBesoin.Lot> out = new ArrayList<>();
        for (int i = 1; i <= nb; i++) {
            String d = i <= parts.length ? parts[i - 1].trim().replaceFirst("(?i)^lot\\s*\\d+\\s*:\\s*", "") : "";
            out.add(new TableauxBesoin.Lot(i, d));
        }
        return out;
    }

    /** Les parties en construction : les titres s'accumulent jusqu'au document qu'ils précèdent. */
    private static final class Plan {
        private final List<DaoCompletWord.Partie> parties = new ArrayList<>();
        private List<DaoCompletWord.Titre> titres = new ArrayList<>();

        /** ⚠️ 2026-10-16 (charte, §B2) — la partie du DAO en cours : rappelée sur la page de séparation d'une section qui ne l'ouvre pas. */
        private String partieCourante;

        void titre(String texte, int niveau) {
            titres.add(new DaoCompletWord.Titre(texte, niveau));
            if (niveau == 1) {
                partieCourante = texte;
            }
        }

        void document(byte[] docx) {
            parties.add(partie(docx));
            titres = new ArrayList<>();
        }

        private DaoCompletWord.Partie partie(byte[] docx) {
            boolean separateur = titres.stream().anyMatch(t -> t.niveau() == 2);
            boolean ouvre = titres.stream().anyMatch(t -> t.niveau() == 1);
            // La dernière section ouverte par la partie : des titres restés sans document la précèdent parfois (une DPAO absente).
            String section = titres.stream().filter(t -> t.niveau() == 2).map(DaoCompletWord.Titre::texte).reduce((a, b) -> b).orElse(null);
            return new DaoCompletWord.Partie(titres, docx, separateur, separateur && !ouvre ? partieCourante : null,
                    section == null ? null : intituleCourt(section));
        }

        void documents(List<DocumentFicheMarche> docs) {
            docs.forEach(d -> document(CharteDao.partie(d.getContenu())));
        }

        /** Des titres restés sans document (une section vide) forment une partie à eux seuls. */
        List<DaoCompletWord.Partie> fin() {
            if (!titres.isEmpty()) {
                parties.add(partie(null));
            }
            return parties;
        }
    }

    /**
     * ⚠️ 2026-10-17 (charte travaux, T2) — l'intitulé court d'une section du sommaire, à gauche de l'en-tête courant : sans son
     * numéro, son sigle ni son champ d'application, en minuscules sauf l'initiale (« 1.2. - Données Particulières de l'Appel
     * d'Offres (DPAO) » → « Données particulières de l'appel d'offres »), ce qui le distingue aussi du titre du séparateur (règle 6
     * de B4) ; l'acte d'engagement des fournitures et des travaux : « Cadre d'acte d'engagement ».
     */
    static String intituleCourt(String section) {
        String s = section.replaceFirst("^\\d+(\\.\\d+)*\\.?\\s*-\\s*", "").replaceAll("\\s*\\([A-Z]{2,6}\\)", "")
                .replaceFirst("(?i)\\s+applicable aux march.*$", "").replaceFirst("(?i)\\s*\\(adress.*$", "").trim();
        if (s.equalsIgnoreCase("Acte d'Engagement")) {
            return "Cadre d'acte d'engagement";
        }
        if (s.toLowerCase(Locale.FRENCH).startsWith("contrat-cadre valant")) {
            return "Contrat-cadre valant acte d'engagement et CCAP";
        }
        String bas = s.toLowerCase(Locale.FRENCH);
        return bas.isEmpty() ? bas : Character.toUpperCase(bas.charAt(0)) + bas.substring(1);
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
        return garde(etat, plan, null);
    }

    /**
     * ⚠️ 2026-10-16 (charte, §B2) — la page de garde des DAO réels, de haut en bas : le bloc de l'autorité centré, en capitales et
     * séparé par des tirets (ministère, entité, PRMP) ; « DOSSIER D'APPEL D'OFFRES {mode} », « N° … », « Lancé le … » (la date de
     * lancement {@code B04-CP-01} quand elle est saisie, à défaut celle de la validation) ; l'objet suivi des lots ; puis, à gauche,
     * « FINANCEMENT », « IMPUTATION BUDGETAIRE » (⚠️ 17/10, T1 : les lignes d'imputation du plan, {@code B01-AC-16}, sans leurs
     * montants) et « COMPTE » ({@code B01-AC-17}) ; en travaux, « MARCHES PUBLICS DE TRAVAUX » en bas de garde.
     */
    List<DaoCompletWord.LigneGarde> garde(FicheMarcheDto etat, Map<String, String> plan, LocalDateTime validation) {
        Map<String, String> p = plan == null ? Map.of() : plan;
        List<DaoCompletWord.LigneGarde> l = new ArrayList<>(autorite(etat, p, validation, true));
        vide(l);
        ligne(l, p.get("FINANCEMENT") == null ? null : "FINANCEMENT : " + p.get("FINANCEMENT"), 11, true, true);
        // ⚠️ 2026-10-17 (charte travaux, T1, correctif toutes catégories) — l'imputation est B01-AC-16, le compte B01-AC-17.
        String imputation = p.get("BENEFICIAIRES") == null ? null : p.get("BENEFICIAIRES").replaceAll("\\s*\\([^)]*\\)", "").trim();
        ligne(l, imputation == null || imputation.isEmpty() ? null : "IMPUTATION BUDGETAIRE : " + imputation, 11, true, true);
        ligne(l, p.get("COMPTES") == null ? null : "COMPTE : " + p.get("COMPTES"), 11, true, true);
        if (CategorieDao.TRAVAUX.name().equals(categorie(etat))) {
            vide(l);
            ligne(l, "MARCHES PUBLICS DE TRAVAUX", 13, true);
        }
        return l;
    }

    /**
     * Le bloc commun à la garde et aux pages de séparation (§B2) : autorité, intitulé du dossier, n°, « Lancé le », objet ; la garde
     * y ajoute les lots.
     */
    private List<DaoCompletWord.LigneGarde> autorite(FicheMarcheDto etat, Map<String, String> p, LocalDateTime validation, boolean garde) {
        boolean pi = CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie(etat));
        List<DaoCompletWord.LigneGarde> l = new ArrayList<>();
        ligne(l, majuscules(p.get("MINISTERE")), 10, true);
        tirets(l, p.get("MINISTERE") != null);
        // ⚠️ D1 (contre-recette du 06/10) — l'entité n'est imprimée que si elle diffère du ministère hors accents, casse et blancs.
        if (p.get("ENTITE") != null && !comparable(p.get("ENTITE")).equals(comparable(p.get("MINISTERE")))) {
            ligne(l, majuscules(p.get("ENTITE")), 10, true);
            tirets(l, true);
        }
        ligne(l, "PERSONNE RESPONSABLE DES MARCHÉS PUBLICS", 10, true);
        tirets(l, true);
        vide(l);
        vide(l);
        String mode = p.get("MODE");
        String intitule = intitule(mode, pi);
        ligne(l, intitule, garde ? 15 : 12, true);
        if (mode != null && !intitule.contains(majuscules(mode))) {
            ligne(l, mode, 12, false);
        }
        ligne(l, numero(etat) == null ? null : "N° " + numero(etat), 12, true);
        ligne(l, "Lancé le " + lance(etat, validation), 12, true);
        vide(l);
        String lots = p.get("LOTS_DESIGNATION");
        boolean alloti = lots != null && lots.contains(" ; ");
        String objet = objet(etat);
        ligne(l, objet == null ? null : "Objet : " + objet + (garde && !alloti ? ", en un lot unique" : ""), 12, true, true);
        if (garde && !alloti) {
            localisation(l, etat, null);
        }
        if (garde && alloti) {
            String[] parts = lots.split(" ; ");
            for (int i = 0; i < parts.length; i++) {
                String d = parts[i].trim();
                ligne(l, d.toLowerCase(Locale.FRENCH).startsWith("lot") ? d : "Lot " + (i + 1) + " : " + d, 11, false, true);
                localisation(l, etat, i + 1);
            }
        }
        return l;
    }

    /**
     * ⚠️ 2026-10-17 (charte travaux, T1, Q1) — sous l'objet ou sous la ligne du lot, en travaux : « {lieu}, {PK} » ({@code B02-OT-03},
     * {@code B02-OT-04}) puis « Coordonnées GPS : début … — fin … » ({@code B02-OT-05}, {@code B02-OT-06}) ; rien sans valeur.
     */
    private static void localisation(List<DaoCompletWord.LigneGarde> l, FicheMarcheDto etat, Integer lot) {
        if (!CategorieDao.TRAVAUX.name().equals(categorie(etat))) {
            return;
        }
        java.util.function.Function<String, String> v = code -> valeurLot(etat, code, lot);
        String lieu = java.util.stream.Stream.of(v.apply("B02-OT-03"), v.apply("B02-OT-04")).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(", "));
        ligne(l, lieu.isEmpty() ? null : lieu, 10, false, true);
        String debut = v.apply("B02-OT-05");
        String fin = v.apply("B02-OT-06");
        if (debut != null || fin != null) {
            ligne(l, "Coordonnées GPS : " + (debut == null ? "" : "début " + debut) + (debut != null && fin != null ? " — " : "")
                    + (fin == null ? "" : "fin " + fin), 9, false, true);
        }
    }

    /** La valeur d'un champ pour un lot ({@code CODE#k}, à défaut le code nu), {@code null} si vide. */
    static String valeurLot(FicheMarcheDto etat, String code, Integer lot) {
        Map<String, String> vs = etat.getValeurs() == null ? Map.of() : etat.getValeurs();
        String x = lot == null ? null : vs.get(LotsFiche.cle(code, lot));
        if (x == null || x.isBlank()) {
            x = vs.get(code);
        }
        return x == null || x.isBlank() ? null : x.trim();
    }

    /** « Lancé le » : {@code B04-CP-01} (date de lancement) quand elle est saisie, à défaut la date de validation. */
    static String lance(FicheMarcheDto etat, LocalDateTime validation) {
        String saisie = etat.getValeurs() == null ? null : etat.getValeurs().get("B04-CP-01");
        if (saisie != null && !saisie.isBlank()) {
            try {
                return java.time.LocalDate.parse(saisie.trim().substring(0, Math.min(10, saisie.trim().length()))).format(JOUR);
            } catch (RuntimeException e) {
                return saisie.trim();
            }
        }
        return validation == null ? "…………………………" : validation.format(JOUR);
    }

    /**
     * ⚠️ 2026-10-16 (charte, §B1-B2) — la présentation : la garde, le bloc des pages de séparation, l'en-tête courant (« DAO n° … » à
     * gauche, l'objet à droite) et le pied courant (l'autorité et la PRMP ; « Fiche v{n} validée le {date} » ; « page n / N »).
     */
    DaoCompletWord.Charte charte(FicheMarcheDto etat, Map<String, String> plan, Integer version, LocalDateTime validation) {
        Map<String, String> p = plan == null ? Map.of() : plan;
        boolean pi = CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie(etat));
        String autorite = p.get("ENTITE") != null ? p.get("ENTITE") : p.get("MINISTERE");
        // ⚠️ 2026-10-17 (T2) — l'en-tête : la partie en cours à gauche (ici le sommaire ; chaque section pose la sienne), le n° à droite.
        return new DaoCompletWord.Charte(garde(etat, p, validation), autorite(etat, p, validation, false), "SOMMAIRE GÉNÉRAL", "Sommaire général",
                (pi ? "Dossier de consultation" : "DAO") + (numero(etat) == null ? "" : " n° " + numero(etat)),
                (autorite == null ? "Autorité contractante" : autorite.trim()) + " — PRMP",
                "Fiche v" + (version == null ? "?" : version) + (validation == null ? "" : " validée le " + validation.format(JOUR)));
    }

    private static final java.time.format.DateTimeFormatter JOUR = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static void tirets(List<DaoCompletWord.LigneGarde> l, boolean si) {
        if (si) {
            l.add(new DaoCompletWord.LigneGarde("-------------", 10, true));
        }
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

    /**
     * Le numéro du DAO : {@code B02-OB-03}, à défaut la référence du plan. ⚠️ 2026-10-16 (recette de la charte, R1) — un contrat-cadre
     * porte le sien en {@code B02-OE-01} (« NUMÉRO » du DPAC) : il passe d'abord.
     */
    static String numero(FicheMarcheDto etat) {
        Map<String, String> v = etat.getValeurs() == null ? Map.of() : etat.getValeurs();
        for (String code : contratCadre(etat) ? List.of("B02-OE-01", DocumentsFicheMarcheService.CHAMP_NUMERO_DAO)
                : List.of(DocumentsFicheMarcheService.CHAMP_NUMERO_DAO)) {
            String n = v.get(code);
            if (n != null && !n.isBlank()) {
                return n.trim();
            }
        }
        return etat.getRefeDossier();
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
        ligne(l, texte, taille, gras, false);
    }

    private static void ligne(List<DaoCompletWord.LigneGarde> l, String texte, int taille, boolean gras, boolean gauche) {
        if (texte != null && !texte.isBlank()) {
            l.add(new DaoCompletWord.LigneGarde(texte.trim(), taille, gras, gauche));
        }
    }

    private static void vide(List<DaoCompletWord.LigneGarde> l) {
        l.add(new DaoCompletWord.LigneGarde("", 11, false));
    }


    static boolean contratCadre(FicheMarcheDto etat) {
        return Objects.equals(TypeMarcheDao.CONTRAT_CADRE.name(), etat.getTypeMarche());
    }
}
