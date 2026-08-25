package cnm.prs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.Localite;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.repository.ControleurRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.EntiteContractRepository;
import cnm.prs.repository.LettreRenvoiRepository;
import cnm.prs.repository.LocaliteRepository;

/**
 * Production du <strong>document PDF</strong> d'une lettre de renvoi : choix du modèle Word selon la
 * localité, remplissage des placeholders, conversion et stockage sur le FSX. Pendant de
 * {@link PvDocumentService} pour les PV.
 *
 * <p>⚠️ 2026-08-19 (motif repris du PV, commit {@code cd955e0}) — cette production est
 * <strong>sortie de la transaction de signature</strong> : elle pilote Microsoft Word (plusieurs
 * secondes, incompressibles) et n'a rien à faire dans le chemin d'un acte métier. Elle est
 * déclenchée par {@link LettreRenvoiDocumentTache} <strong>après commit</strong>, et en
 * régénération paresseuse au téléchargement. L'avoir isolée ici évite aussi le cycle de
 * dépendances {@code LettreRenvoiService} ⇄ {@code LettreRenvoiDocumentTache}.</p>
 */
@Service
public class LettreRenvoiDocumentService {

    private final LettreRenvoiRepository repository;
    private final DossierRepository dossierRepository;
    private final LocaliteRepository localiteRepository;
    private final EntiteContractRepository entiteContractRepository;
    private final ControleurRepository controleurRepository;
    private final LettreRenvoiDocumentGenerator generator;

    @Value("${storage.lettre-renvoi.path:${java.io.tmpdir}/prs-fsx/LR}")
    private String cheminStockageLr;

    public LettreRenvoiDocumentService(LettreRenvoiRepository repository, DossierRepository dossierRepository,
            LocaliteRepository localiteRepository, EntiteContractRepository entiteContractRepository,
            ControleurRepository controleurRepository, LettreRenvoiDocumentGenerator generator) {
        this.repository = repository;
        this.dossierRepository = dossierRepository;
        this.localiteRepository = localiteRepository;
        this.entiteContractRepository = entiteContractRepository;
        this.controleurRepository = controleurRepository;
        this.generator = generator;
    }

    /**
     * Localité de la lettre : celle du <strong>dossier</strong> ({@code idLocalite}), avec repli sur la
     * localité de <strong>réception</strong> si absente. Commande à la fois la règle de signature
     * (centrale → CC ou Président ; régionale → CC seul) et le modèle Word retenu.
     */
    public String localiteDeLaLettre(LettreRenvoi lettre) {
        if (lettre == null) {
            return null;
        }
        String localite = lettre.getIdDossier() == null ? null
                : dossierRepository.findById(lettre.getIdDossier()).map(Dossier::getIdLocalite).orElse(null);
        if (localite == null || localite.isBlank()) {
            localite = repository.findLocaliteByLettre(lettre.getIdLettre()).orElse(null);
        }
        return localite;
    }

    /**
     * Génère le PDF de la lettre (modèle centrale/régionale) et le stocke sur le FSX ; renvoie le
     * chemin obtenu. {@link Optional#empty()} seulement si la lettre est absente — une lettre a
     * toujours un modèle, contrairement au PV dont l'avis peut n'en avoir aucun.
     *
     * <p>Ne doit jamais être appelée depuis la transaction d'un acte métier (cf. javadoc de classe).
     * <strong>Volontairement sans {@code @Transactional}</strong>, comme
     * {@link PvDocumentService#genererSiEligible} : ouvrir une transaction ici retiendrait une
     * connexion du pool pendant toute la conversion Word — précisément le défaut corrigé. Chaque
     * lecture de référentiel ouvre sa propre transaction, courte.</p>
     */
    public Optional<String> genererEtStocker(LettreRenvoi lettre) {
        if (lettre == null) {
            return Optional.empty();
        }
        Dossier dossier = lettre.getIdDossier() == null ? null
                : dossierRepository.findById(lettre.getIdDossier()).orElse(null);
        String localite = localiteDeLaLettre(lettre);
        boolean centrale = Localite.estCentrale(localite);   // source unique (cf. références « CNM »)
        String localiteLibelle = localite == null ? "" : localiteRepository.findById(localite)
                .map(l -> l.getLibelleLocalite() == null ? "" : l.getLibelleLocalite()).orElse("");
        byte[] pdf = generator.genererPdf(centrale, construireRemplacements(lettre, dossier,
                nomComplet(lettre.getImSignataire()), centrale, localiteLibelle));
        return Optional.of(stockerSurFsx(lettre, pdf));
    }

    /**
     * Vrai si le PDF de la lettre est <strong>prêt à télécharger maintenant</strong> :
     * {@code CHEMIN_DOCUMENT} renseigné, ou contenu {@code DOCUMENT_PDF} en base (anciennes lettres).
     *
     * <p>⚠️ 2026-08-19 — même sens que le {@code documentDisponible} du PV signé : <strong>false
     * pendant la fenêtre de génération</strong> qui suit la signature. Une lettre non signée n'a,
     * elle, jamais de document : pas d'équivalent de l'éligibilité « un document sera produit » du
     * projet de PV, car {@code …/document} n'a jamais servi de prévisualisation de brouillon.</p>
     */
    @Transactional(readOnly = true)
    public boolean documentDisponible(LettreRenvoi lettre) {
        if (lettre == null) {
            return false;
        }
        if (lettre.getCheminDocument() != null && !lettre.getCheminDocument().isBlank()) {
            return true;
        }
        return lettre.getDocumentPdf() != null && lettre.getDocumentPdf().length > 0;
    }

    /** Écrit le PDF dans le répertoire FSX LR/ sous {@code {refLettre nettoyée}.pdf} ; renvoie le chemin. */
    private String stockerSurFsx(LettreRenvoi lettre, byte[] pdf) {
        String base = lettre.getRefLettre() != null && !lettre.getRefLettre().isBlank()
                ? lettre.getRefLettre() : ("lettre-" + lettre.getIdLettre());
        String nomFichier = base.replace('/', '_').replace('\\', '_') + ".pdf";
        try {
            Path dir = Path.of(cheminStockageLr);
            Files.createDirectories(dir);
            Path fichier = dir.resolve(nomFichier);
            Files.write(fichier, pdf);
            return fichier.toString();
        } catch (IOException e) {
            throw new BusinessRuleException("Stockage du document de la lettre impossible : " + e.getMessage());
        }
    }

    /**
     * Construit la table des remplacements de placeholders du modèle Word selon la localité.
     * Communs aux deux modèles ; le central a le placeholder « PRESIDENT OU CHEF DE COMMISSION », le
     * régional a « LOCALITE DOSSIER » et « CHEF DE COMMISSION ». Le nom du signataire remplace
     * <strong>uniquement</strong> le placeholder (aucun libellé de rôle ajouté).
     */
    private Map<String, String> construireRemplacements(LettreRenvoi lettre, Dossier dossier,
            String nomSignataire, boolean centrale, String localiteLibelle) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH);
        String dateLettre = lettre.getDateLettre() == null ? "" : lettre.getDateLettre().format(fmt);
        String dateExamen = lettre.getDateExamen() == null ? "" : lettre.getDateExamen().format(fmt);
        String reference = dossier == null || dossier.getRefeDossier() == null ? "" : dossier.getRefeDossier();
        String entite = dossier == null || dossier.getIdEntiteContract() == null ? ""
                : entiteContractRepository.findById(dossier.getIdEntiteContract())
                        .map(EntiteContract::getLibelleEntite).orElse("");
        String corps = lettre.getCorpsLettre() == null ? "" : lettre.getCorpsLettre();
        String nom = nomSignataire == null ? "" : nomSignataire;

        Map<String, String> m = new HashMap<>();
        m.put("<DATE_LETTRE>", dateLettre);
        m.put("<NOM_ENTITE_CONTRACT>", entite);
        m.put("<REFERENCE DOSSIER>", reference);
        m.put("<DATE EXAMEN>", dateExamen);
        m.put("<CORPS DE LA LETTRE>", corps);
        if (centrale) {
            m.put("<NOM ET PRENOMS DU PRESIDENT OU CHEF DE COMMISSION>", nom);
        } else {
            m.put("<LOCALITE DOSSIER>", localiteLibelle == null ? "" : localiteLibelle.toUpperCase(Locale.FRENCH));
            m.put("<NOM ET PRENOMS DU CHEF DE COMMISSION>", nom);
        }
        return m;
    }

    /** « Prénoms Nom » d'un contrôleur (signataire effectif), ou l'IM si introuvable. */
    private String nomComplet(String im) {
        if (im == null) {
            return "";
        }
        return controleurRepository.findById(im).map(c -> {
            String n = ((c.getPrenomsCont() == null ? "" : c.getPrenomsCont()) + " "
                    + (c.getNomCont() == null ? "" : c.getNomCont())).trim();
            return n.isBlank() ? im : n;
        }).orElse(im);
    }
}
