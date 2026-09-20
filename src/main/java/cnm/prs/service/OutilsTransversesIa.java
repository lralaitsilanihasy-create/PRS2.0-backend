package cnm.prs.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import cnm.prs.controller.AnnuaireController;
import cnm.prs.controller.DossierController;
import cnm.prs.controller.KpiController;
import cnm.prs.dto.AFaireDto;
import cnm.prs.dto.AnnuairePersonneDto;
import cnm.prs.dto.BadgesDto;
import cnm.prs.dto.RechercheDossierDto;
import cnm.prs.dto.TableauBordDto;
import cnm.prs.enums.IntentionAssistant;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.service.AiguillageAssistantIa.Aiguillage;
import cnm.prs.service.OutilsDossierIa.Faits;
import cnm.prs.service.OutilsDossierIa.Section;

/**
 * ⚠️ Assistant IA, lot 4 (2026-09-20, étape 2) — <strong>les lectures transverses</strong> du chatbot
 * ({@code docs/plan-assistant-ia.md} §4, lot 4, 4.c).
 *
 * <p>Mêmes règles qu'au lot 2, et pour les mêmes raisons : aucune base, aucun service, uniquement des
 * méthodes de <strong>contrôleurs</strong> appelées bean à bean <strong>dans le fil de la requête</strong>
 * — donc à travers les {@code @PreAuthorize} et les filtres de visibilité qui existent déjà.</p>
 *
 * <h2>Ce qui change par rapport au lot 2 : il y a un paramètre</h2>
 * <p>Un terme de recherche peut venir d'une question libre. La garantie n'est donc plus « le modèle ne
 * choisit rien », mais celle-ci : <strong>un paramètre ne peut qu'élargir à l'intérieur de ce que
 * l'utilisateur voit déjà</strong>. {@code rechercher} filtre sur le périmètre de l'appelant,
 * l'annuaire est réservé à l'Administrateur. Le pire qu'une injection obtienne est une liste que
 * l'utilisateur pouvait déjà afficher d'un clic — et <strong>aucun paramètre ne désigne jamais</strong>
 * un profil, une localité, une PRMP ou un identifiant d'acteur.</p>
 *
 * <p>Le résultat prend la forme des {@link Faits} du lot 2 : l'écran sait déjà les montrer, et le
 * matériau remis au modèle reste exactement ce que l'utilisateur voit.</p>
 */
@Component
public class OutilsTransversesIa {

    private static final Logger log = LoggerFactory.getLogger(OutilsTransversesIa.class);

    /** Au-delà, une liste noie la réponse — et la fenêtre du modèle avec elle. */
    private static final int RESULTATS_MAX = 8;
    private static final int TACHES_MAX = 6;

    /**
     * Libellés des files d'attente — repris de {@code SECTIONS} du frontend
     * ({@code features/home/a-faire/…}). Même raison qu'au lot 2 : le matériau est en français, sinon on
     * reprocherait au modèle de recopier le vocabulaire technique qu'on lui a donné.
     */
    private static final Map<String, String> SECTIONS = Map.ofEntries(
            Map.entry("A_RECEPTIONNER", "à réceptionner et numéroter"),
            Map.entry("A_DISPATCHER", "à dispatcher"),
            Map.entry("A_EXAMINER", "à examiner"),
            Map.entry("A_REEXAMINER", "à réexaminer"),
            Map.entry("PV_A_SOUMETTRE", "projets de PV à soumettre"),
            Map.entry("PV_A_REPRENDRE", "projets de PV à reprendre"),
            Map.entry("PV_A_ACCEPTER", "projets de PV à accepter"),
            Map.entry("PV_A_VISER", "projets de PV à viser"),
            Map.entry("PV_A_SIGNER", "PV à signer"),
            Map.entry("LETTRES_A_SIGNER", "lettres de renvoi à signer"),
            Map.entry("RETRAITS_A_DECIDER", "demandes de retrait"),
            Map.entry("A_VERIFIER", "à vérifier"),
            Map.entry("A_TRANSMETTRE_SIGMP", "observations levées, à transmettre à SIGMP"),
            Map.entry("A_ARCHIVER", "PV à archiver"),
            Map.entry("LETTRES_A_ARCHIVER", "lettres de renvoi à archiver"),
            Map.entry("EN_ATTENTE_PRMP", "en attente de la PRMP"),
            Map.entry("BROUILLONS", "brouillons à soumettre"),
            Map.entry("PIECES_DEPOT_A_COMPLETER", "pièces du dépôt à compléter"),
            Map.entry("COMPLEMENTS_A_TRANSMETTRE", "pièces complémentaires à transmettre"),
            Map.entry("A_RECTIFIER", "à rectifier"),
            Map.entry("EN_COURS_CNM", "en cours à la CNM"));

    /**
     * Libellés des classes d'urgence — miroir de {@code LIBELLES_URGENCES} du frontend
     * ({@code features/home/a-faire/a-faire-libelles.ts}). Sans eux, la recette du 2026-09-20 montrait
     * « (bientot) » et « (hors_delai) » dans les faits affichés.
     */
    private static final Map<String, String> URGENCES = Map.of(
            "EN_RETARD", "en retard",
            "BIENTOT", "bientôt à échéance",
            "DANS_LES_DELAIS", "dans les délais",
            "SANS_DELAI", "sans délai",
            "HORS_DELAI", "hors délai CNM",
            "EN_PAUSE", "chez la PRMP",
            "SUIVI", "en suivi");

    private final DossierController dossiers;
    private final KpiController kpis;
    private final AnnuaireController annuaire;
    private final OutilsDossierIa outilsDossier;

    public OutilsTransversesIa(DossierController dossiers, KpiController kpis, AnnuaireController annuaire,
            OutilsDossierIa outilsDossier) {
        this.dossiers = dossiers;
        this.kpis = kpis;
        this.annuaire = annuaire;
        this.outilsDossier = outilsDossier;
    }

    /**
     * Les intentions qu'on propose au modèle pour ce profil.
     *
     * <p>⚠️ Cette liste est une <strong>courtoisie</strong>, pas une garde : elle évite de proposer au
     * modèle une porte que le serveur refuserait de toute façon. L'autorité reste le {@code @PreAuthorize}
     * de chaque contrôleur — si les deux divergent un jour, c'est la garde qui gagne, et l'assistant
     * répond simplement qu'il n'a pas pu lire.</p>
     */
    public List<IntentionAssistant> ouvertes(ProfilUtilisateur profil) {
        List<IntentionAssistant> ouvertes = new ArrayList<>();
        ouvertes.add(IntentionAssistant.REGLE);
        if (profil == null) {
            return List.copyOf(ouvertes);
        }
        ouvertes.add(IntentionAssistant.MES_CHIFFRES);
        ouvertes.add(IntentionAssistant.TROUVER_DOSSIER);
        ouvertes.add(IntentionAssistant.ETAT_DOSSIER);
        // L'accueil « À faire » n'existe ni pour l'Administrateur ni pour le Chargé de publication.
        if (profil != ProfilUtilisateur.ADMINISTRATEUR && profil != ProfilUtilisateur.CHARGE_PUBLICATION) {
            ouvertes.add(IntentionAssistant.MES_TACHES);
        }
        if (profil == ProfilUtilisateur.PRESIDENT || profil == ProfilUtilisateur.ADMINISTRATEUR
                || profil == ProfilUtilisateur.CHEF_COMMISSION) {
            ouvertes.add(IntentionAssistant.TABLEAU_DE_BORD);
        }
        if (profil == ProfilUtilisateur.ADMINISTRATEUR) {
            ouvertes.add(IntentionAssistant.ANNUAIRE);
        }
        return List.copyOf(ouvertes);
    }

    /**
     * Fait la lecture que l'aiguillage a décidée. À appeler <strong>dans le fil de la requête</strong>.
     *
     * @return les faits lus, ou {@code null} si l'intention ne lit rien ({@code REGLE}) ou si la lecture
     *         a été refusée — la réponse retombe alors sur le corpus documentaire
     */
    public Faits lire(Aiguillage aiguillage) {
        IntentionAssistant intention = aiguillage.intention();
        if (!intention.litDesDonnees()) {
            return null;
        }
        try {
            return switch (intention) {
                case MES_TACHES -> faits("Ce que vous avez à traiter", lignesTaches(dossiers.aFaire(false)));
                case MES_CHIFFRES -> faits("Vos compteurs", lignesCompteurs(kpis.badges()));
                case TABLEAU_DE_BORD -> faits("La Commission en chiffres", lignesTableauBord(kpis.tableauBord()));
                case TROUVER_DOSSIER -> faits("Dossiers trouvés",
                        lignesRecherche(dossiers.rechercher(aiguillage.parametre())));
                case ETAT_DOSSIER -> etatDuDossier(aiguillage.parametre());
                case ANNUAIRE -> faits("Annuaire", lignesAnnuaire(aiguillage.parametre()));
                default -> null;
            };
        } catch (RuntimeException e) {
            // ⚠️ Un refus est un cas NORMAL : la garde a parlé, et l'assistant n'a pas à la commenter. La
            // réponse retombe sur le corpus documentaire, qui ne lit aucune donnée.
            log.debug("Assistant IA : lecture « {} » écartée ({})", intention, e.getClass().getSimpleName());
            return null;
        }
    }

    // ------------------------------------------------------------------ les lectures

    private Faits faits(String titre, List<String> lignes) {
        if (lignes.isEmpty()) {
            return null;
        }
        return new Faits(0, titre, List.of(new Section(titre, lignes)), List.of(titre), List.of());
    }

    private List<String> lignesTaches(AFaireDto aFaire) {
        if (aFaire == null) {
            return List.of();
        }
        List<String> lignes = new ArrayList<>();
        AFaireDto.Compteurs c = aFaire.compteurs();
        if (c != null) {
            lignes.add(c.aFaire() + " dossier(s) à traiter, dont " + c.enRetard() + " en retard et "
                    + c.bientot() + " bientôt à échéance");
            if (c.enPause() > 0) {
                lignes.add(c.enPause() + " dossier(s) en pause : le compteur de la CNM ne court pas");
            }
        }
        if (aFaire.sections() != null) {
            for (AFaireDto.Section s : aFaire.sections()) {
                if (s.total() > 0) {
                    lignes.add(s.total() + " " + section(s.code()));
                }
            }
        }
        if (aFaire.taches() != null) {
            List<AFaireDto.Tache> taches = aFaire.taches().stream().limit(TACHES_MAX).toList();
            for (AFaireDto.Tache t : taches) {
                String reference = t.dossier() == null ? null : t.dossier().refeDossier();
                lignes.add("À traiter : " + (reference == null ? "dossier sans référence" : reference)
                        + " — " + section(t.section())
                        + (t.urgence() == null ? "" : " (" + urgence(t.urgence()) + ")"));
            }
            if (aFaire.taches().size() > TACHES_MAX) {
                lignes.add("… et " + (aFaire.taches().size() - TACHES_MAX) + " autres tâches.");
            }
        }
        return lignes;
    }

    /**
     * Les compteurs du rôle appelant. ⚠️ {@code BadgesDto.compteurs} est volontairement un
     * {@code Object} — chaque profil a les siens. On n'essaie pas de les typer ici : on rend la
     * <strong>phrase</strong> que l'utilisateur lit dans son menu, et le total « à faire ».
     */
    private List<String> lignesCompteurs(BadgesDto badges) {
        if (badges == null) {
            return List.of();
        }
        List<String> lignes = new ArrayList<>();
        if (badges.aFaire() != null) {
            lignes.add(badges.aFaire() + " dossier(s) dans votre file « À faire »");
        }
        if (badges.compteurs() instanceof Map<?, ?> compteurs) {
            for (Map.Entry<?, ?> e : compteurs.entrySet()) {
                if (e.getValue() instanceof Number n && n.intValue() > 0) {
                    lignes.add(n + " " + section(String.valueOf(e.getKey())));
                }
            }
        }
        return lignes;
    }

    private List<String> lignesTableauBord(TableauBordDto tb) {
        if (tb == null) {
            return List.of();
        }
        List<String> lignes = new ArrayList<>();
        lignes.add(tb.nbDossiersSoumis() + " dossier(s) soumis, " + tb.nbDossiersConformes()
                + " conforme(s) — taux de conformité " + Math.round(tb.tauxConformitePct()) + " %");
        if (tb.pipelineParStatut() != null) {
            for (Map.Entry<String, Long> e : tb.pipelineParStatut().entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    lignes.add(e.getValue() + " au statut « " + e.getKey().toLowerCase(java.util.Locale.FRENCH)
                            .replace('_', ' ') + " »");
                }
            }
        }
        return lignes;
    }

    private List<String> lignesRecherche(List<RechercheDossierDto> trouves) {
        if (trouves == null || trouves.isEmpty()) {
            return List.of();
        }
        List<String> lignes = new ArrayList<>();
        for (RechercheDossierDto d : trouves.stream().limit(RESULTATS_MAX).toList()) {
            lignes.add(valeur(d.refeDossier()) + (d.reference() == null ? "" : " — " + d.reference())
                    + " (" + valeur(d.statut()).toLowerCase(java.util.Locale.FRENCH).replace('_', ' ') + ")");
        }
        if (trouves.size() > RESULTATS_MAX) {
            lignes.add("… et " + (trouves.size() - RESULTATS_MAX) + " autres dossiers.");
        }
        return lignes;
    }

    /**
     * L'état d'UN dossier : la référence est d'abord <strong>résolue par la recherche</strong>, qui est
     * filtrée sur le périmètre de l'appelant — un identifiant hors périmètre ne donne donc rien à
     * résoudre. Puis c'est la lecture factuelle du lot 2, avec ses sept gardes.
     */
    private Faits etatDuDossier(String designation) {
        if (designation == null || designation.isBlank()) {
            return null;
        }
        List<RechercheDossierDto> trouves = dossiers.rechercher(designation);
        if (trouves == null || trouves.isEmpty()) {
            return null;
        }
        if (trouves.size() > 1) {
            // Plusieurs dossiers répondent : on ne CHOISIT pas à la place de l'utilisateur, on les liste.
            return faits("Dossiers trouvés", lignesRecherche(trouves));
        }
        return outilsDossier.lire(trouves.get(0).idDossier());
    }

    private List<String> lignesAnnuaire(String recherche) {
        if (recherche == null || recherche.isBlank()) {
            return List.of();
        }
        var page = annuaire.rechercher(recherche, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, RESULTATS_MAX));
        if (page == null || page.isEmpty()) {
            return List.of();
        }
        List<String> lignes = new ArrayList<>();
        for (AnnuairePersonneDto p : page.getContent()) {
            lignes.add(valeur(p.nom()) + " " + valeur(p.prenoms()) + " — " + valeur(p.profil())
                    + (p.entite() == null ? "" : ", " + p.entite())
                    + (p.localite() == null ? "" : " (" + p.localite() + ")"));
        }
        return lignes;
    }

    // ------------------------------------------------------------------ formatage

    private static String section(String code) {
        return code == null ? "dossier(s)"
                : SECTIONS.getOrDefault(code, code.toLowerCase(java.util.Locale.FRENCH).replace('_', ' '));
    }

    /** Le libellé d.une classe d.urgence ; le code brut si le référentiel a bougé sans nous. */
    private static String urgence(String code) {
        return code == null ? "" : URGENCES.getOrDefault(code, code.toLowerCase(java.util.Locale.FRENCH)
                .replace('_', ' '));
    }

    private static String valeur(String texte) {
        return texte == null || texte.isBlank() ? "non renseigné" : texte.strip();
    }
}
