package cnm.prs.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.TypeNotification;
import cnm.prs.enums.TypeObjet;
import cnm.prs.repository.DocumentFicheMarcheRepository;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.PvExamenRepository;
import cnm.prs.repository.UgpmRepository;

/**
 * ⚠️ 2026-10-18 (demande front « avis-a-imprimer », demande du pilote) — l'avis spécifique (ou les lettres d'invitation) qu'une
 * fiche imprime, et quand il le devient.
 * <ul>
 *   <li><strong>§B1</strong> — chaque écriture qui peut le rendre imprimable (signature d'un PV, observations levées, transmission au
 *   SIGMP, archivage, dépôt de la publication de l'AGPM, clôture de la cérémonie des clés) relève les fiches imprimables
 *   <strong>avant</strong> ({@link #disponibles}) et notifie celles qui le sont devenues <strong>après</strong>
 *   ({@link #notifierDevenues}) : une fois par passage, une réimpression ne renotifie pas, une nouvelle disponibilité après un retour
 *   à l'indisponibilité (réexamen, nouvelle version) renotifie, rien n'est rétroactif (Q1). Vers la PRMP du plan et ses UGPM.</li>
 *   <li><strong>§B2</strong> — l'état servi sur la liste des dossiers ({@link #etats}) : la règle de
 *   {@code …/avis-specifique/disponibilite} et {@code …/lettres-invitation/disponibilite}, sans la raison.</li>
 * </ul>
 * La règle est celle d'{@link AvisSpecifiqueService#evaluer}, appliquée à l'essentiel de la fiche lu sans contrôle de l'appelant
 * ({@link FicheMarcheService#essentiel}). Rien ici ne lève : la transaction de l'écriture qui l'appelle ne doit pas en pâtir.
 */
@Service
@Transactional
public class AvisAImprimer {

    public static final String AVIS_SPECIFIQUE = "AVIS_SPECIFIQUE";
    public static final String LETTRES_INVITATION = "LETTRES_INVITATION";

    /** Le document que la fiche imprime, et s'il est imprimable. */
    public record Etat(String document, boolean disponible) {
    }

    private final FicheMarcheService fiches;
    private final AvisSpecifiqueService avis;
    private final DossierRepository dossierRepository;
    private final MarcheRepository marcheRepository;
    private final PrmpRepository prmpRepository;
    private final UgpmRepository ugpmRepository;
    private final PvExamenRepository pvRepository;
    private final DocumentFicheMarcheRepository documentRepository;
    private final NotificationService notifications;

    public AvisAImprimer(FicheMarcheService fiches, AvisSpecifiqueService avis, DossierRepository dossierRepository,
            MarcheRepository marcheRepository, PrmpRepository prmpRepository, UgpmRepository ugpmRepository,
            PvExamenRepository pvRepository, DocumentFicheMarcheRepository documentRepository, NotificationService notifications) {
        this.fiches = fiches;
        this.avis = avis;
        this.dossierRepository = dossierRepository;
        this.marcheRepository = marcheRepository;
        this.prmpRepository = prmpRepository;
        this.ugpmRepository = ugpmRepository;
        this.pvRepository = pvRepository;
        this.documentRepository = documentRepository;
        this.notifications = notifications;
    }

    /** Le document de la fiche et sa disponibilité ; vide pour un DMC sans fiche DAO ou une fiche sans avis ni lettre. */
    Optional<Etat> etat(Long idDmc) {
        return fiches.essentiel(idDmc).flatMap(f -> etat(idDmc, f));
    }

    private Optional<Etat> etat(Long idDmc, FicheMarcheDto f) {
        if (avis.sansModele(f, false) == null) {
            return Optional.of(new Etat(AVIS_SPECIFIQUE, avis.evaluer(idDmc, f, false).disponible()));
        }
        if (avis.sansModele(f, true) == null) {
            return Optional.of(new Etat(LETTRES_INVITATION, avis.evaluer(idDmc, f, true).disponible()));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ §B1 — la notification au passage

    /** Les fiches, parmi celles données, dont le document est imprimable maintenant. */
    public Set<Long> disponibles(Collection<Long> idDmcs) {
        Set<Long> d = new HashSet<>();
        for (Long id : idDmcs) {
            if (id != null && etat(id).map(Etat::disponible).orElse(false)) {
                d.add(id);
            }
        }
        return d;
    }

    /** La fiche du dossier, s'il en est né une. */
    public Optional<Long> dmcDuDossier(Integer idDossier) {
        return idDossier == null ? Optional.empty() : dossierRepository.findById(idDossier).map(Dossier::getIdDmc);
    }

    /** Les fiches nées des dossiers de la PRMP (un dépôt de publication de l'AGPM peut toutes les concerner). */
    public List<Long> dmcsDeLaPrmp(String idPrmp) {
        return idPrmp == null ? List.of() : dossierRepository.findIdDmcsParPrmp(idPrmp);
    }

    /**
     * ⚠️ 2026-10-18 (« dao-electronique », Q4) — les fiches dont le dossier a un PV favorable signé, toutes PRMP confondues : celles
     * qu'un réglage global (le compte bancaire de l'ARMP) peut rendre imprimables.
     */
    public List<Long> dmcsAPvFavorable() {
        Map<Integer, Long> dmcParDossier = new HashMap<>();
        for (Object[] r : dossierRepository.findDossiersNesDeFiche()) {
            dmcParDossier.put((Integer) r[0], (Long) r[1]);
        }
        return dmcParDossier.isEmpty() ? List.of()
                : pvRepository.dossiersAvecPvFavorableSigne(dmcParDossier.keySet()).stream().map(dmcParDossier::get).distinct().toList();
    }

    /** Notifie les fiches devenues imprimables depuis le relevé {@code avant}. */
    public void notifierDevenues(Collection<Long> idDmcs, Set<Long> avant) {
        notifierDevenues(idDmcs, avant, null);
    }

    /**
     * ⚠️ 2026-10-28 (demande front « decision-sigmp-prmp », H2) — idem, sauf pour la fiche dont le dossier soumis est
     * {@code saufDossier} : la notification {@code DECISION_TRANSMISE_SIGMP} de ce dossier dit déjà que l'avis s'imprime.
     */
    public void notifierDevenues(Collection<Long> idDmcs, Set<Long> avant, Integer saufDossier) {
        for (Long id : idDmcs) {
            if (id == null || avant.contains(id)) {
                continue;
            }
            fiches.essentiel(id).filter(f -> saufDossier == null || !saufDossier.equals(f.getIdDossierSoumis()))
                    .ifPresent(f -> etat(id, f).filter(Etat::disponible).ifPresent(e -> notifier(f, e.document())));
        }
    }

    /**
     * ⚠️ 2026-10-28 (« decision-sigmp-prmp », §B2) — la suite d'un dossier DAO né d'une fiche, en une phrase : le document
     * (avis spécifique ou lettres d'invitation) peut être imprimé, ou pourquoi pas encore (la phrase de
     * {@code …/disponibilite}). Vide pour un autre dossier, une fiche sans document, ou un avis non favorable (la décision le
     * dit déjà).
     */
    public Optional<String> suite(Long idDmc, Integer idDossier) {
        if (idDmc == null || idDossier == null) {
            return Optional.empty();
        }
        return fiches.essentiel(idDmc).filter(f -> idDossier.equals(f.getIdDossierSoumis())).flatMap(f -> {
            boolean lettre;
            if (avis.sansModele(f, false) == null) {
                lettre = false;
            } else if (avis.sansModele(f, true) == null) {
                lettre = true;
            } else {
                return Optional.empty();
            }
            cnm.prs.dto.AvisDisponibiliteDto d = avis.evaluer(idDmc, f, lettre);
            if (d.disponible()) {
                return Optional.of(lettre ? "Les lettres d'invitation peuvent être imprimées." : "L'avis spécifique peut être imprimé.");
            }
            return AvisSpecifiqueService.AVIS_NON_FAVORABLE.equals(d.raison()) ? Optional.empty()
                    : Optional.of(AvisSpecifiqueService.message(d.raison(), lettre));
        });
    }

    /** ⚠️ 2026-10-28 (« decision-sigmp-prmp », §B2) — l'objet de la fiche du dossier, s'il en est né une. */
    public Optional<String> objet(Long idDmc, Integer idDossier) {
        return idDmc == null || idDossier == null ? Optional.empty()
                : fiches.essentiel(idDmc).filter(f -> idDossier.equals(f.getIdDossierSoumis()))
                        .map(FicheMarcheDto::getDesignationMarche).filter(s -> !s.isBlank());
    }

    private void notifier(FicheMarcheDto f, String document) {
        Integer idDossier = f.getIdDossierSoumis();
        String idPrmp = marcheRepository.findIdDossierByIdDetail(f.getIdDetail()).flatMap(dossierRepository::findById)
                .map(Dossier::getIdPrmp).orElse(null);
        if (idDossier == null || idPrmp == null) {
            return;
        }
        String reference = dossierRepository.findById(idDossier).map(Dossier::getRefeDossier).orElse("n° " + idDossier);
        String objet = f.getDesignationMarche() == null ? "" : " (" + f.getDesignationMarche() + ")";
        boolean lettres = LETTRES_INVITATION.equals(document);
        String titre = lettres ? "Lettres d'invitation à imprimer" : "Avis spécifique à imprimer";
        String corps = lettres ? "Les lettres d'invitation du dossier " + reference + objet + " peuvent être imprimées."
                : "L'avis spécifique du dossier " + reference + objet + " peut être imprimé.";
        String email = prmpRepository.findById(idPrmp).map(Prmp::getEmailPrmp).orElse(null);
        notifications.emettrePrmp(TypeNotification.AVIS_A_IMPRIMER, idPrmp, email, idDossier, TypeObjet.DOSSIER, idDossier, titre, corps);
        for (Ugpm u : ugpmRepository.findByIdPrmpTutelle(idPrmp)) {
            notifications.emettreUgpm(TypeNotification.AVIS_A_IMPRIMER, u.getIdUgpm(), idPrmp, u.getEmailUgpm(), idDossier,
                    TypeObjet.DOSSIER, idDossier, titre, corps);
        }
    }

    // ------------------------------------------------------------------ §B2 — l'état sur la liste des dossiers

    /** Ce que la liste sert : le document, s'il est imprimable, s'il a déjà été produit. */
    public record Impression(String document, boolean disponible, boolean dejaImprime) {
    }

    /**
     * L'état d'impression des dossiers nés d'une fiche ({@code idDossier → idDmc}), en lot : une requête pour les dossiers dont un
     * PV favorable est signé (les seuls où le document peut être imprimable), une pour les documents déjà produits ; la règle
     * entière ne s'évalue que pour les premiers. Un dossier dont la fiche n'imprime ni avis ni lettre est absent.
     */
    @Transactional(readOnly = true)
    public Map<Integer, Impression> etats(Map<Integer, Long> dmcParDossier) {
        Map<Integer, Impression> etats = new HashMap<>();
        if (dmcParDossier.isEmpty()) {
            return etats;
        }
        Set<Integer> favorables = new HashSet<>(pvRepository.dossiersAvecPvFavorableSigne(dmcParDossier.keySet()));
        Set<Long> imprimes = new HashSet<>(documentRepository.dmcsImprimes(dmcParDossier.values()));
        dmcParDossier.forEach((idDossier, idDmc) -> fiches.essentiel(idDmc).ifPresent(f -> {
            Optional<Etat> e = favorables.contains(idDossier) ? etat(idDmc, f) : documentSeul(f);
            e.ifPresent(x -> etats.put(idDossier, new Impression(x.document(), x.disponible(), imprimes.contains(idDmc))));
        }));
        return etats;
    }

    /**
     * ⚠️ 2026-10-18 (« avis-a-imprimer », §B3) — l'accueil « À faire » : parmi les dossiers donnés ({@code idDossier → idDmc}), ceux
     * dont le document est imprimable et n'a jamais été produit, avec ce document. Deux requêtes en lot ; la règle ne s'évalue que
     * pour les dossiers à PV favorable signé et sans document produit (la pastille du menu refait ce calcul à chaque page).
     */
    @Transactional(readOnly = true)
    public Map<Integer, String> aImprimer(Map<Integer, Long> dmcParDossier) {
        Map<Integer, String> aImprimer = new java.util.LinkedHashMap<>();
        if (dmcParDossier.isEmpty()) {
            return aImprimer;
        }
        Set<Integer> favorables = new HashSet<>(pvRepository.dossiersAvecPvFavorableSigne(dmcParDossier.keySet()));
        Set<Long> imprimes = new HashSet<>(documentRepository.dmcsImprimes(dmcParDossier.values()));
        dmcParDossier.forEach((idDossier, idDmc) -> {
            if (favorables.contains(idDossier) && !imprimes.contains(idDmc)) {
                etat(idDmc).filter(Etat::disponible).ifPresent(e -> aImprimer.put(idDossier, e.document()));
            }
        });
        return aImprimer;
    }

    /** Sans PV favorable signé : le document de la fiche, indisponible (sans évaluer la suite de la règle). */
    private Optional<Etat> documentSeul(FicheMarcheDto f) {
        if (avis.sansModele(f, false) == null) {
            return Optional.of(new Etat(AVIS_SPECIFIQUE, false));
        }
        return avis.sansModele(f, true) == null ? Optional.of(new Etat(LETTRES_INVITATION, false)) : Optional.empty();
    }
}
