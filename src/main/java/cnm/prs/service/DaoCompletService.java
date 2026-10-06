package cnm.prs.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * un {@code .docx} et un {@code .pdf} par version validée, qui suivent le plan des six documents types de l'ARMP — page de garde et
 * sommaire, I Instructions aux candidats (texte fixe), II Données particulières (DPAO, DPAC ou DPIC), III Formulaires de
 * soumission, IV Acte d'engagement (lot par lot), V CCAP ou CPS (et la liste des fournitures), spécifications techniques (le Word
 * joint par la PRMP), VI CCAG (texte fixe). Il <strong>assemble</strong> les documents déjà produits, sans rien réécrire (H1).
 * Les classeurs ({@code xlsx} : bordereau des prix, DQE, tableau de conformité) restent à part.
 */
@Service
public class DaoCompletService {

    private static final Logger log = LoggerFactory.getLogger(DaoCompletService.class);
    public static final String TYPE = "DAO_COMPLET";
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DaoCompletWord word;
    private final DocumentsFicheMarcheService documents;
    private final DocumentFicheMarcheRepository documentRepository;
    private final SpecificationsFicheRepository specificationsRepository;

    public DaoCompletService(DaoCompletWord word, DocumentsFicheMarcheService documents, DocumentFicheMarcheRepository documentRepository,
            SpecificationsFicheRepository specificationsRepository) {
        this.word = word;
        this.documents = documents;
        this.documentRepository = documentRepository;
        this.specificationsRepository = specificationsRepository;
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
            DaoCompletWord.Resultat r = word.assembler(garde(etat), "SOMMAIRE", entete(etat), parties(etat, fiche.getIdFiche(), docs));
            LocalDateTime maintenant = LocalDateTime.now();
            Integer idDetail = etat.getIdDetailCourant() != null ? etat.getIdDetailCourant() : etat.getIdDetail();
            String refe = etat.getValeursPpm() == null ? etat.getRefeDossier() : etat.getValeursPpm().get("DOSSIER_REFERENCE");
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

    // ------------------------------------------------------------------ le plan

    List<DaoCompletWord.Partie> parties(FicheMarcheDto etat, Integer idFiche, List<DocumentFicheMarche> docs) {
        String categorie = categorie(etat);
        boolean pi = CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie);
        List<DocumentFicheMarche> word = docs.stream().filter(d -> "docx".equals(d.getExtension()))
                .filter(d -> !DocumentsFicheMarcheService.TYPES_PUBLICATION.contains(d.getType()) && !TYPE.equals(d.getType())).toList();
        List<DaoCompletWord.Partie> out = new ArrayList<>();
        out.add(new DaoCompletWord.Partie("Section I — Instructions aux candidats", fixe(categorie, "IC")));
        ajouter(out, word, List.of("DPAO", "DPAC", "DPIC"), "Section II — " + (pi ? "Données particulières des instructions aux candidats"
                : "Données particulières de l'appel d'offres"));
        ajouter(out, word, List.of("A1", "A2", "A3", "A4", "C1", "C2"), "Section III — Formulaires de soumission");
        ajouter(out, word, List.of("AE"), "Section IV — Acte d'engagement");
        ajouter(out, word, List.of("CCAP", "LF"), "Section V — " + (pi ? "Cahier des prescriptions spéciales"
                : "Cahier des clauses administratives particulières"));
        specificationsRepository.findById(idFiche)
                .ifPresent(s -> out.add(new DaoCompletWord.Partie("Spécifications techniques", s.getContenu())));
        out.add(new DaoCompletWord.Partie("Section VI — Cahier des clauses administratives générales", fixe(categorie, "CCAG")));
        return out;
    }

    /** Les documents de ces types, dans l'ordre des types puis des lots ; le titre de la section sur le premier seulement. */
    private static void ajouter(List<DaoCompletWord.Partie> out, List<DocumentFicheMarche> docs, List<String> types, String titre) {
        List<DocumentFicheMarche> choisis = docs.stream().filter(d -> types.contains(d.getType()))
                .sorted(Comparator.comparing((DocumentFicheMarche d) -> types.indexOf(d.getType()))
                        .thenComparing(d -> d.getLot() == null ? 0 : d.getLot()))
                .toList();
        for (int i = 0; i < choisis.size(); i++) {
            out.add(new DaoCompletWord.Partie(i == 0 ? titre : null, choisis.get(i).getContenu()));
        }
    }

    /** Le texte fixe de l'ARMP de la catégorie ({@code modeles/dao-fixes/<CATEGORIE>-<IC|CCAG>.docx}, converti par Word). */
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

    // ------------------------------------------------------------------ page de garde et en-tête

    List<DaoCompletWord.LigneGarde> garde(FicheMarcheDto etat) {
        Map<String, String> p = etat.getValeursPpm() == null ? Map.of() : etat.getValeursPpm();
        boolean pi = CategorieDao.PRESTATIONS_INTELLECTUELLES.name().equals(categorie(etat));
        List<DaoCompletWord.LigneGarde> l = new ArrayList<>();
        ligne(l, p.get("MINISTERE"), 13, true);
        ligne(l, p.get("ENTITE"), 12, true);
        ligne(l, p.get("PRMP") == null ? null : "Personne Responsable des Marchés Publics : " + p.get("PRMP"), 11, false);
        l.add(new DaoCompletWord.LigneGarde("", 11, false));
        ligne(l, pi ? "DOSSIER DE CONSULTATION" : "DOSSIER D'APPEL D'OFFRES", 22, true);
        ligne(l, p.get("MODE"), 13, false);
        ligne(l, numero(etat) == null ? null : "N° " + numero(etat), 13, true);
        l.add(new DaoCompletWord.LigneGarde("", 11, false));
        ligne(l, objet(etat) == null ? null : "Objet : " + objet(etat), 13, true);
        ligne(l, p.get("LOTS_DESIGNATION") == null ? null : "Lots : " + p.get("LOTS_DESIGNATION"), 11, false);
        ligne(l, p.get("FINANCEMENT") == null ? null : "Source de financement : " + p.get("FINANCEMENT"), 11, false);
        l.add(new DaoCompletWord.LigneGarde("", 11, false));
        ligne(l, (etat.getDateValidation() == null ? LocalDateTime.now() : etat.getDateValidation()).format(JOUR), 11, false);
        return l;
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

    private static String objet(FicheMarcheDto etat) {
        String o = etat.getValeursPpm() == null ? null : etat.getValeursPpm().get("OBJET");
        return o == null || o.isBlank() ? etat.getDesignationMarche() : o.trim();
    }

    private static void ligne(List<DaoCompletWord.LigneGarde> l, String texte, int taille, boolean gras) {
        if (texte != null && !texte.isBlank()) {
            l.add(new DaoCompletWord.LigneGarde(texte.trim(), taille, gras));
        }
    }

    /** Le contrat-cadre place son DPAC au rang 2 (déjà le cas : DPAC entre dans la section II). */
    static boolean contratCadre(FicheMarcheDto etat) {
        return Objects.equals(TypeMarcheDao.CONTRAT_CADRE.name(), etat.getTypeMarche());
    }
}
