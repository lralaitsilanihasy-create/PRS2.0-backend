package cnm.prs.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ModelePvDto;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.PvExamen;
import cnm.prs.exception.BadRequestException;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.ExamenRepository;
import cnm.prs.repository.LettreRenvoiRepository;
import cnm.prs.repository.PvExamenRepository;

/**
 * ⚠️ 2026-10-20 (demande front « modeles-pv-integres-consultation ») — consulter les modèles intégrés au JAR depuis l'écran de
 * l'Administrateur : télécharger un fichier tel que livré (§B1), et l'essayer sur un dossier réel (§B2) par la règle de choix et le
 * remplissage <strong>mêmes</strong> que la production ({@link PvDocumentService#choisirModele}, {@link PvDocumentService#apercuDocx},
 * {@link LettreRenvoiService#apercu}), sans rien enregistrer. C'est l'outil de la comparaison qui précédera le retrait des fichiers.
 */
@Service
@Transactional(readOnly = true)
public class ModelesIntegresService {

    static final String PV_PLAN = "PV-PLAN-INTEGRE";
    static final String LETTRE_RENVOI = "LETTRE-RENVOI-INTEGREE";
    private static final List<String> AVIS = List.of("FAV", "FAVR", "DEF", "NSP");

    /** Un aperçu : le fichier qui a servi et le document. */
    public record Apercu(String fichier, byte[] contenu) {
    }

    private final ModelePvService modeles;
    private final PvDocumentService pvDocuments;
    private final PvDocumentGenerator convertisseur;
    private final LettreRenvoiService lettres;
    private final DossierRepository dossiers;
    private final ExamenRepository examens;
    private final PvExamenRepository pvs;
    private final LettreRenvoiRepository lettresRepository;

    public ModelesIntegresService(ModelePvService modeles, PvDocumentService pvDocuments, PvDocumentGenerator convertisseur,
            LettreRenvoiService lettres, DossierRepository dossiers, ExamenRepository examens, PvExamenRepository pvs,
            LettreRenvoiRepository lettresRepository) {
        this.modeles = modeles;
        this.pvDocuments = pvDocuments;
        this.convertisseur = convertisseur;
        this.lettres = lettres;
        this.dossiers = dossiers;
        this.examens = examens;
        this.pvs = pvs;
        this.lettresRepository = lettresRepository;
    }

    /** §B1 — le {@code .docx} tel que livré ; 404 pour un code ou un nom absent de la liste servie (jamais un chemin libre). */
    public byte[] fichier(String code, String nom) {
        ModelePvDto.Integre integre = integre(code);
        if (!integre.fichiers().contains(nom)) {
            throw new ResourceNotFoundException("Le modèle intégré " + code + " n'a pas de fichier « " + nom + " ».");
        }
        String chemin = sources(code).stream().filter(c -> c.endsWith("/" + nom)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Fichier intégré introuvable : " + nom));
        try (InputStream in = getClass().getResourceAsStream(chemin)) {
            if (in == null) {
                throw new ResourceNotFoundException("Fichier intégré introuvable : " + nom);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Lecture du fichier intégré " + nom + " impossible : " + e.getMessage(), e);
        }
    }

    /**
     * §B2 — l'aperçu sur un dossier réel. PV du plan : le PV {@code idPv}, à défaut le plus récent du dossier (Q1), l'avis forcé sur
     * une copie ; lettre de renvoi : la lettre {@code idLettre}, à défaut la dernière du dossier (Q2). 409 {@code PV_ABSENT},
     * {@code LETTRE_ABSENTE}, {@code AUCUN_DOCUMENT} (la raison de la règle), {@code CONVERSION_INDISPONIBLE}.
     */
    public Apercu apercu(String code, Integer idDossier, String avis, Integer idPv, Integer idLettre, boolean pdf) {
        integre(code);
        if (!dossiers.existsById(idDossier)) {
            throw new ResourceNotFoundException("Dossier introuvable : " + idDossier);
        }
        Apercu docx = PV_PLAN.equals(code) ? pv(idDossier, avis, idPv) : lettre(idDossier, idLettre);
        if (!pdf) {
            return docx;
        }
        try {
            return new Apercu(docx.fichier(), convertisseur.convertirEnPdf(docx.contenu()));
        } catch (RuntimeException e) {
            throw new BusinessRuleException("La conversion en PDF n'est pas disponible sur ce serveur (Word absent ou indisponible) : "
                    + "l'aperçu se demande en Word.", "CONVERSION_INDISPONIBLE");
        }
    }

    private Apercu pv(Integer idDossier, String avis, Integer idPv) {
        if (avis != null && !avis.isBlank() && !AVIS.contains(avis)) {
            throw new BadRequestException("Avis inconnu : « " + avis + " » (FAV, FAVR, DEF, NSP).", "AVIS_INCONNU");
        }
        PvExamen pv;
        if (idPv != null) {
            pv = pvs.findById(idPv).filter(p -> idDossier.equals(examens.findIdDossierByExamen(p.getIdExamen()).orElse(null)))
                    .orElseThrow(() -> new ResourceNotFoundException("Le PV " + idPv + " n'est pas un PV du dossier " + idDossier + "."));
        } else {
            List<PvExamen> tous = pvs.findTousParDossier(idDossier);
            if (tous.isEmpty()) {
                throw new BusinessRuleException("Le dossier " + idDossier + " n'a pas de PV : le document se remplit sur un PV (date "
                        + "d'examen, présents, observations).", "PV_ABSENT", idDossier);
            }
            pv = tous.get(tous.size() - 1);
        }
        // L'avis forcé se pose sur une copie : le PV en base n'est jamais touché.
        PvExamen copie = new PvExamen();
        BeanUtils.copyProperties(pv, copie);
        if (avis != null && !avis.isBlank()) {
            copie.setIdAvis(avis);
        }
        PvDocumentService.ChoixModele choix = pvDocuments.choisirModele(copie);
        if (choix.modele() == null) {
            throw new BusinessRuleException(choix.raison(), "AUCUN_DOCUMENT", idDossier);
        }
        return new Apercu(nom(choix.modele()), pvDocuments.apercuDocx(copie, choix.modele()));
    }

    private Apercu lettre(Integer idDossier, Integer idLettre) {
        LettreRenvoi lettre;
        if (idLettre != null) {
            lettre = lettresRepository.findById(idLettre).filter(l -> idDossier.equals(l.getIdDossier()))
                    .orElseThrow(() -> new ResourceNotFoundException("La lettre " + idLettre + " n'est pas une lettre du dossier "
                            + idDossier + "."));
        } else {
            lettre = lettresRepository.findFirstByIdDossierOrderByIdLettreDesc(idDossier)
                    .orElseThrow(() -> new BusinessRuleException("Le dossier " + idDossier + " n'a pas de lettre de renvoi.",
                            "LETTRE_ABSENTE", idDossier));
        }
        LettreRenvoiService.ApercuLettre a = lettres.apercu(lettre.getIdLettre());
        return new Apercu(nom(a.modele()), a.docx());
    }

    private ModelePvDto.Integre integre(String code) {
        return modeles.integres().stream().filter(i -> i.code().equals(code)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Modèle intégré inconnu : " + code));
    }

    private static List<String> sources(String code) {
        return PV_PLAN.equals(code) ? PvDocumentGenerator.modeles() : LettreRenvoiDocumentGenerator.modeles();
    }

    private static String nom(String chemin) {
        return chemin.substring(chemin.lastIndexOf('/') + 1);
    }
}
