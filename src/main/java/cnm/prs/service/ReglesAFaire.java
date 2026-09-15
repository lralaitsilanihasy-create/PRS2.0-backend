package cnm.prs.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import cnm.prs.entity.Localite;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.GesteAFaire;
import cnm.prs.enums.ModeTache;
import cnm.prs.enums.NiveauNavette;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.SectionAFaire;
import cnm.prs.enums.StatutDossier;
import cnm.prs.enums.StatutPv;
import cnm.prs.enums.UrgenceTache;
import cnm.prs.security.Visibilite;

/**
 * ⚠️ <strong>Qui voit quoi dans l'accueil « À faire »</strong> (demande front du 2026-09-14, §3 à §5 ; arbitrages
 * du 2026-09-15) — pour un connecté et un dossier déjà chargé, les lignes que le serveur lui propose.
 *
 * <p><strong>Aucune règle de circuit n'est écrite ici.</strong> Chaque condition est un appel aux prédicats que
 * les gardes appellent elles-mêmes : {@link PredicatsIdentite} (identités, étages, localité stricte, lettres,
 * retraits), {@link Visibilite#localiteAdmise} (localité, Président exempté),
 * {@link CircuitDossierService#deuxNiveaux(CircuitDossierService.Circuit, ProfilUtilisateur)} (navette à deux
 * niveaux), {@link RattachementService#ciblesDans} (cibles, résolues par l'appelant) et
 * {@link ChronometrageService#etapeDeReprise} (porteur d'une attente). Ce qui est propre à ce fichier est
 * l'<strong>assemblage</strong> : quel prédicat pour quelle section, et à quel titre ({@link ModeTache}).</p>
 *
 * <p><strong>La garde fait foi.</strong> Un geste n'est proposé que si l'endpoint qui l'exécute l'accepterait pour
 * ce connecté, y compris son {@code @PreAuthorize} ({@code @perm.peutExercer(...)}) : la paire de délégation
 * {@code Président → Chef de commission} ouvre au Président le dispatch, le visa et les lettres, et la désactiver
 * les lui retire ici comme là. Les refus qui ne portent que sur le corps ou l'état ponctuel de la requête (note
 * d'intérim manquante, co-signataire à désigner, mandat PRMP vacant) ne retirent pas la ligne.</p>
 *
 * <p>Fonctions <strong>pures</strong> : aucune lecture de jeton ni de base, aucun effet.</p>
 */
public final class ReglesAFaire {

    /** Seuil « bientôt », plancher : un reste de 2 h ouvrées ou moins est toujours proche (arbitrage 4). */
    public static final int SEUIL_BIENTOT_MINIMUM_HEURES = 2;

    /**
     * Seuil « bientôt », part du délai standard : 35 %, arrondie à l'heure supérieure (arbitrage 4, seuil de la
     * maquette validée). Pour un standard de 8 h, le seuil vaut 3 h ; pour 16 h, 6 h.
     */
    public static final int SEUIL_BIENTOT_PART_DU_STANDARD_PCT = 35;

    private static final String AVIS_FAVR = "FAVR";

    private ReglesAFaire() {
    }

    // ------------------------------------------------------------------ entrées

    /**
     * Le connecté : profil, matricule (ou ID_PRMP de tutelle), localité ({@code null} si absente), et les profils
     * qu'il peut exercer ({@code PermissionService.profilsExercables} : titulaire et paires actives).
     */
    public record Acteur(ProfilUtilisateur profil, String im, String localite, Set<ProfilUtilisateur> profilsExercables) {

        /** Même réponse que {@code PermissionService.peutExercer(profil, cible)}. */
        public boolean exerce(ProfilUtilisateur cible) {
            return profilsExercables != null && profilsExercables.contains(cible);
        }

        /** PRMP ou UGPM : la partie contrôlée (même périmètre, {@code Visibilite.estPrmp()}). */
        public boolean partieControlee() {
            return profil == ProfilUtilisateur.PRMP || profil == ProfilUtilisateur.UGPM;
        }
    }

    /** Le PV le plus récent du dossier, tel que ses gardes le lisent. */
    public record PvEnCours(Integer idPv, String statut, String niveauNavette, String examinateur,
            String membreCoSignataire, LocalDate dateSignatureMembre, String ccCoSignataire, LocalDate dateSignatureCc,
            LocalDate dateSignaturePresident, String idAvis, LocalDate dateArchivage) {

        /** Niveau de navette lu tel quel ({@code null} si absent ou inconnu). */
        NiveauNavette niveau() {
            if (niveauNavette == null || niveauNavette.isBlank()) {
                return null;
            }
            try {
                return NiveauNavette.valueOf(niveauNavette);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /** La part du viseur (Président ou CC) est-elle posée ? Même lecture que {@code partsCompletes}. */
        boolean partViseurPosee() {
            return dateSignaturePresident != null || dateSignatureCc != null;
        }
    }

    /** Une lettre de renvoi à traiter : sa date et la localité de sa réception ({@code findLocaliteByLettre}). */
    public record LettreEnCours(Integer idLettre, LocalDate dateLettre, String localiteReception) {
    }

    /**
     * Ce que les gardes lisent d'un dossier, chargé en lot par l'appelant.
     *
     * @param idLocalite        {@code t_dossier.ID_LOCALITE} brute (garde des retraits, du passage de vérification)
     * @param localiteReception localité du contrôleur de réception (gardes du dispatch, de l'examen, du PV)
     * @param receptionne       une réception existe
     * @param circuit           dispatch courant (le plus récent) ; {@code null} sans dispatch
     * @param examenEntame      un examen existe sur le dispatch courant
     * @param pv                PV le plus récent ; {@code null} sans PV
     * @param circuitPv         circuit du dispatch de ce PV ({@code CircuitDossierService.parPv}) ; {@code null} sans PV
     * @param profilDispatcheurPv profil du dispatcheur de ce circuit, lu dans l'annuaire
     * @param verificateurCible {@link RattachementService#ciblesDans} ; {@code null} = repli localité
     * @param assistantCible    idem, pour l'archivage
     * @param verifie           au moins un passage de vérification (condition de {@code /a-verifier} en attente PRMP)
     * @param lettreASigner     lettre {@code SOUMIS} la plus récente ; {@code null} sinon
     * @param lettreAArchiver   lettre {@code SIGNE} non archivée la plus ancienne ; {@code null} sinon
     * @param retraitEnAttente  une demande de retrait est {@code EN_ATTENTE}
     */
    public record EtatDossier(Integer idDossier, String statut, String idLocalite, String localiteReception,
            boolean receptionne, CircuitDossierService.Circuit circuit, boolean examenEntame, PvEnCours pv,
            CircuitDossierService.Circuit circuitPv, ProfilUtilisateur profilDispatcheurPv, String verificateurCible,
            String assistantCible, boolean verifie, LettreEnCours lettreASigner, LettreEnCours lettreAArchiver,
            boolean retraitEnAttente) {

        /**
         * Localité « du dossier » des gardes qui la lisent sur le dossier (réception, lettres) : la sienne, à
         * défaut celle de sa réception.
         */
        String localiteDossier() {
            return idLocalite != null && !idLocalite.isBlank() ? idLocalite : localiteReception;
        }
    }

    /** Une ligne proposée : section, geste principal, gestes secondaires, titre. */
    public record Ligne(SectionAFaire section, GesteAFaire geste, List<GesteAFaire> gestesSecondaires, ModeTache mode) {
    }

    // ------------------------------------------------------------------ lignes

    /** Lignes proposées au connecté pour un dossier, titulaires et bloc délégation confondus. */
    public static List<Ligne> lignes(Acteur a, EtatDossier d) {
        List<Ligne> lignes = new ArrayList<>();
        if (a == null || a.profil() == null || d == null || d.statut() == null) {
            return lignes;
        }
        if (a.partieControlee()) {
            partieControlee(a, d, lignes);
            return lignes;
        }
        String statut = d.statut();
        if (StatutDossier.SOUMIS.name().equals(statut) && !d.receptionne()) {
            aReceptionner(a, d, lignes);
        } else if (StatutDossier.PRET_DISPATCH.name().equals(statut)) {
            aDispatcher(a, d, lignes);
        } else if (StatutDossier.DISPATCHE.name().equals(statut)) {
            aExaminer(a, d, lignes);
        } else if (StatutDossier.A_REEXAMINER.name().equals(statut)) {
            aReexaminer(a, d, lignes);
        } else if (StatutDossier.EXAMINE.name().equals(statut)) {
            navette(a, d, lignes);
        } else if (StatutDossier.EN_VERIFICATION.name().equals(statut)) {
            aVerifier(a, d, lignes);
        } else if (StatutDossier.OBSERVATIONS_LEVEES.name().equals(statut)) {
            aTransmettre(a, d, lignes);
        } else if (StatutDossier.DECISION_TRANSMISE_SIGMP.name().equals(statut)) {
            aArchiver(a, d, lignes);
        } else if (ChronometrageService.estEnAttentePrmp(statut)) {
            enAttentePrmp(a, d, lignes);
        }
        if (d.lettreASigner() != null) {
            lettreASigner(a, d, lignes);
        }
        if (d.retraitEnAttente()) {
            retraitADecider(a, d, lignes);
        }
        if (d.lettreAArchiver() != null) {
            lettreAArchiver(a, d, lignes);
        }
        return lignes;
    }

    /** Réception : Secrétaire de la localité ; délégation par paire active vers Secrétaire (P/CC). */
    private static void aReceptionner(Acteur a, EtatDossier d, List<Ligne> lignes) {
        // Garde : @perm.peutExercer('SECRETAIRE') + Visibilite.exigerLocalite(localité du dossier).
        if (!a.exerce(ProfilUtilisateur.SECRETAIRE)
                || !Visibilite.localiteAdmise(a.profil(), a.localite(), d.localiteDossier())) {
            return;
        }
        ModeTache mode = a.profil() == ProfilUtilisateur.SECRETAIRE ? ModeTache.TITULAIRE : ModeTache.DELEGATION;
        lignes.add(ligne(SectionAFaire.A_RECEPTIONNER, GesteAFaire.NUMEROTER, mode));
    }

    /**
     * Pré-dispatch : central, Président ; régional, CC de la localité, et le Président en suppléance
     * (arbitrage 2). La localité est celle de la réception, comme {@code DispatchService.resoudreLocaliteDossier}.
     */
    private static void aDispatcher(Acteur a, EtatDossier d, List<Ligne> lignes) {
        // Garde : @perm.peutExercer('CHEF_COMMISSION') + pré-dispatch central réservé au Président.
        if (!a.exerce(ProfilUtilisateur.CHEF_COMMISSION)) {
            return;
        }
        String localite = d.localiteReception() != null ? d.localiteReception() : d.localiteDossier();
        if (a.profil() == ProfilUtilisateur.PRESIDENT) {
            ModeTache mode = Localite.estCentrale(localite) ? ModeTache.TITULAIRE : ModeTache.SUPPLEANCE;
            lignes.add(ligne(SectionAFaire.A_DISPATCHER, GesteAFaire.DISPATCHER, mode));
        } else if (a.profil() == ProfilUtilisateur.CHEF_COMMISSION
                && PredicatsIdentite.peutDispatcherSelonLocalite(a.profil(), false, localite)
                && localite != null && localite.equals(a.localite())) {
            lignes.add(ligne(SectionAFaire.A_DISPATCHER, GesteAFaire.DISPATCHER, ModeTache.TITULAIRE));
        }
    }

    /**
     * Examen : l'attributaire courant, quel que soit son profil. Le CC attributaire d'un dossier central dont
     * l'examen n'est pas entamé a pour geste principal la réattribution (arbitrage 3), l'examen en second.
     */
    private static void aExaminer(Acteur a, EtatDossier d, List<Ligne> lignes) {
        CircuitDossierService.Circuit c = d.circuit();
        if (c == null || !PredicatsIdentite.estAttributaire(a.im(), c.attributaire())) {
            return;
        }
        // Garde de l'examen : @perm.peutExercer('MEMBRE') + localité du circuit + attributaire.
        boolean examiner = a.exerce(ProfilUtilisateur.MEMBRE)
                && Visibilite.localiteAdmise(a.profil(), a.localite(), c.localite());
        // Garde du PUT de réattribution : CC attributaire (dérogation centrale), localité, examen non entamé (409).
        boolean reattribuer = a.profil() == ProfilUtilisateur.CHEF_COMMISSION
                && Localite.estCentrale(c.localite()) && !d.examenEntame()
                && PredicatsIdentite.peutDispatcherSelonLocalite(a.profil(), true, c.localite())
                && Visibilite.localiteAdmise(a.profil(), a.localite(), c.localite());
        if (reattribuer) {
            lignes.add(new Ligne(SectionAFaire.A_EXAMINER, GesteAFaire.REATTRIBUER,
                    examiner ? List.of(GesteAFaire.EXAMINER) : List.of(), ModeTache.TITULAIRE));
        } else if (examiner) {
            lignes.add(ligne(SectionAFaire.A_EXAMINER, GesteAFaire.EXAMINER, ModeTache.TITULAIRE));
        }
    }

    /** Réexamen après lettre de renvoi : l'attributaire, qui re-soumettra le projet de PV. */
    private static void aReexaminer(Acteur a, EtatDossier d, List<Ligne> lignes) {
        CircuitDossierService.Circuit c = d.circuit();
        if (c != null && PredicatsIdentite.estAttributaire(a.im(), c.attributaire())
                && a.exerce(ProfilUtilisateur.MEMBRE)
                && Visibilite.localiteAdmise(a.profil(), a.localite(), c.localite())) {
            lignes.add(ligne(SectionAFaire.A_REEXAMINER, GesteAFaire.REEXAMINER, ModeTache.TITULAIRE));
        }
    }

    /** Dossier EXAMINE : le statut du PV le plus récent dit qui a la main. */
    private static void navette(Acteur a, EtatDossier d, List<Ligne> lignes) {
        PvEnCours pv = d.pv();
        if (pv == null || pv.statut() == null) {
            return;
        }
        String statutPv = pv.statut();
        if (StatutPv.BROUILLON.name().equals(statutPv) || StatutPv.EN_RECTIFICATION.name().equals(statutPv)) {
            // Garde de la soumission : @perm.peutExercer('MEMBRE') + examinateur, même par délégation.
            if (PredicatsIdentite.estExaminateur(a.im(), pv.examinateur()) && a.exerce(ProfilUtilisateur.MEMBRE)) {
                boolean brouillon = StatutPv.BROUILLON.name().equals(statutPv);
                lignes.add(ligne(brouillon ? SectionAFaire.PV_A_SOUMETTRE : SectionAFaire.PV_A_REPRENDRE,
                        brouillon ? GesteAFaire.SOUMETTRE_PV : GesteAFaire.REPRENDRE_EXAMEN, ModeTache.TITULAIRE));
            }
            return;
        }
        CircuitDossierService.Circuit circuit = d.circuitPv();
        boolean deuxNiveaux = CircuitDossierService.deuxNiveaux(circuit, d.profilDispatcheurPv());
        if (StatutPv.PROJET_SOUMIS.name().equals(statutPv)) {
            if (deuxNiveaux) {
                navetteDeuxNiveaux(a, pv, circuit, lignes);
            } else {
                visaNavetteSimple(a, pv, circuit, true, lignes);
            }
        } else if (StatutPv.PROJET_ACCEPTE.name().equals(statutPv)) {
            // Un PV accepté sans part de viseur (contrat d'avant le visa unique) se vise encore — navette simple
            // seulement : à deux niveaux, l'étage d'un PV accepté est nul, le visa y répond 409.
            if (!deuxNiveaux && !pv.partViseurPosee()) {
                visaNavetteSimple(a, pv, circuit, false, lignes);
            }
            aSigner(a, pv, lignes);
        }
    }

    /**
     * Deux niveaux : étage du CC (niveau nul compris) → le CC du circuit accepte ; étage du Président → il vise.
     * Pas d'intérim sur ce circuit.
     */
    private static void navetteDeuxNiveaux(Acteur a, PvEnCours pv, CircuitDossierService.Circuit circuit,
            List<Ligne> lignes) {
        if (!a.exerce(ProfilUtilisateur.CHEF_COMMISSION)) {
            return;   // @perm.peutExercer('CHEF_COMMISSION') sur accepter, viser et retourner
        }
        boolean retourLocal = Visibilite.localiteAdmise(a.profil(), a.localite(), circuit.localite());
        if (PredicatsIdentite.estALEtage(pv.niveau(), NiveauNavette.CC)) {
            if (PredicatsIdentite.estCcDuCircuit(a.im(), circuit)) {
                lignes.add(new Ligne(SectionAFaire.PV_A_ACCEPTER, GesteAFaire.ACCEPTER,
                        retourLocal ? List.of(GesteAFaire.RETOURNER) : List.of(), ModeTache.TITULAIRE));
            }
        } else if (PredicatsIdentite.estViseurDeuxNiveaux(a.profil())) {
            lignes.add(new Ligne(SectionAFaire.PV_A_VISER, GesteAFaire.VISER,
                    retourLocal ? List.of(GesteAFaire.RETOURNER) : List.of(), ModeTache.TITULAIRE));
        }
    }

    /**
     * Navette simple : le dispatcheur vise en titulaire ; un autre P/CC admis par la localité vise par intérim ;
     * l'examinateur non dispatcheur ne vise jamais, pas même par intérim.
     */
    private static void visaNavetteSimple(Acteur a, PvEnCours pv, CircuitDossierService.Circuit circuit,
            boolean projetSoumis, List<Ligne> lignes) {
        String dispatcheur = circuit == null ? null : circuit.dispatcheur();
        if (dispatcheur == null || dispatcheur.isBlank()) {
            return;   // « aucun dispatcheur enregistré » : 409 pour tous
        }
        if (!a.exerce(ProfilUtilisateur.CHEF_COMMISSION) || !PredicatsIdentite.estProfilViseur(a.profil())
                || !PredicatsIdentite.viseurHorsExaminateur(a.im(), pv.examinateur(), dispatcheur)
                || !Visibilite.localiteAdmise(a.profil(), a.localite(), circuit.localite())) {
            return;
        }
        ModeTache mode = PredicatsIdentite.visaParInterim(false, a.im(), dispatcheur)
                ? ModeTache.INTERIM : ModeTache.TITULAIRE;
        // Le retour n'est ouvert que sur un projet soumis.
        lignes.add(new Ligne(SectionAFaire.PV_A_VISER, GesteAFaire.VISER,
                projetSoumis ? List.of(GesteAFaire.RETOURNER) : List.of(), mode));
    }

    /** Co-signature : le désigné (Membre ou CC) dont la part n'est pas datée, sur un PV qui porte un avis. */
    private static void aSigner(Acteur a, PvEnCours pv, List<Ligne> lignes) {
        if (!a.exerce(ProfilUtilisateur.MEMBRE) || pv.idAvis() == null || pv.idAvis().isBlank()) {
            return;   // @perm.peutExercer('MEMBRE') ; « avis global manquant » : 409 pour tous
        }
        boolean partMembre = PredicatsIdentite.designationFaite(pv.membreCoSignataire())
                && PredicatsIdentite.estDesigne(a.im(), pv.membreCoSignataire()) && pv.dateSignatureMembre() == null;
        boolean partCc = PredicatsIdentite.designationFaite(pv.ccCoSignataire())
                && PredicatsIdentite.estDesigne(a.im(), pv.ccCoSignataire()) && pv.dateSignatureCc() == null;
        if (partMembre || partCc) {
            lignes.add(ligne(SectionAFaire.PV_A_SIGNER, GesteAFaire.SIGNER, ModeTache.TITULAIRE));
        }
    }

    /**
     * Vérification : avis FAVR → statuer les observations (localité du dossier, Président exempté) ; autre avis →
     * transmettre la décision (localité stricte : le Président en est exclu).
     */
    private static void aVerifier(Acteur a, EtatDossier d, List<Ligne> lignes) {
        PvEnCours pv = d.pv();
        if (pv == null || !StatutPv.SIGNE.name().equals(pv.statut())) {
            return;
        }
        boolean favr = AVIS_FAVR.equals(pv.idAvis());
        boolean localiteOk = favr
                ? Visibilite.localiteAdmise(a.profil(), a.localite(), d.idLocalite())
                : PredicatsIdentite.localiteStricteAdmise(localitePv(d), a.localite());
        verification(a, d, localiteOk, SectionAFaire.A_VERIFIER,
                favr ? GesteAFaire.VERIFIER : GesteAFaire.TRANSMETTRE_DECISION, lignes);
    }

    /** Transmission SIGMP après levée des observations : même garde que la transmission directe. */
    private static void aTransmettre(Acteur a, EtatDossier d, List<Ligne> lignes) {
        PvEnCours pv = d.pv();
        if (pv == null || !StatutPv.SIGNE.name().equals(pv.statut())) {
            return;
        }
        verification(a, d, PredicatsIdentite.localiteStricteAdmise(localitePv(d), a.localite()),
                SectionAFaire.A_TRANSMETTRE_SIGMP, GesteAFaire.TRANSMETTRE_SIGMP, lignes);
    }

    /**
     * Vérificateur de la localité de réception (comme {@code /a-verifier}) : titulaire s'il est la cible ou sans
     * cible, collègue sinon ; tout autre profil couvert par une paire active vers Vérificateur : délégation.
     */
    private static void verification(Acteur a, EtatDossier d, boolean localiteGarde, SectionAFaire section,
            GesteAFaire geste, List<Ligne> lignes) {
        if (!a.exerce(ProfilUtilisateur.VERIFICATEUR) || !localiteGarde) {
            return;
        }
        if (a.profil() == ProfilUtilisateur.VERIFICATEUR) {
            if (a.localite() == null || !a.localite().equals(d.localiteReception())) {
                return;
            }
            lignes.add(ligne(section, geste, modeCible(a, d.verificateurCible())));
        } else {
            lignes.add(ligne(section, geste, ModeTache.DELEGATION));
        }
    }

    /**
     * Archivage du PV : Assistant cible, sans cible les Assistants de la localité ; CC de la localité par
     * délégation ; jamais le Président (arbitrage 5 — la localité stricte l'exclut déjà).
     */
    private static void aArchiver(Acteur a, EtatDossier d, List<Ligne> lignes) {
        PvEnCours pv = d.pv();
        if (pv == null || !StatutPv.SIGNE.name().equals(pv.statut()) || pv.dateArchivage() != null
                || a.profil() == ProfilUtilisateur.PRESIDENT || !a.exerce(ProfilUtilisateur.ASSISTANT_CONTROLEUR)
                || !PredicatsIdentite.localiteStricteAdmise(localitePv(d), a.localite())) {
            return;
        }
        ModeTache mode = a.profil() == ProfilUtilisateur.ASSISTANT_CONTROLEUR
                ? modeCible(a, d.assistantCible()) : ModeTache.DELEGATION;
        lignes.add(ligne(SectionAFaire.A_ARCHIVER, GesteAFaire.ARCHIVER_PV, mode));
    }

    /**
     * En attente de la PRMP : le porteur de l'étape de reprise suit le dossier (hors {@code aFaire}) — Secrétaire
     * de la localité au dépôt, attributaire à l'examen, Vérificateur (cible ou de la localité) après vérification.
     */
    private static void enAttentePrmp(Acteur a, EtatDossier d, List<Ligne> lignes) {
        EtapeCircuit reprise = ChronometrageService.etapeDeReprise(d.statut());
        boolean porteur = false;
        if (reprise == EtapeCircuit.RECEPTION) {
            porteur = a.profil() == ProfilUtilisateur.SECRETAIRE
                    && Visibilite.localiteAdmise(a.profil(), a.localite(), d.localiteDossier());
        } else if (reprise == EtapeCircuit.EXAMEN) {
            porteur = d.circuit() != null && PredicatsIdentite.estAttributaire(a.im(), d.circuit().attributaire());
        } else if (reprise == EtapeCircuit.VERIFICATION) {
            porteur = d.verifie() && a.profil() == ProfilUtilisateur.VERIFICATEUR && a.localite() != null
                    && a.localite().equals(d.localiteReception())
                    && modeCible(a, d.verificateurCible()) == ModeTache.TITULAIRE;
        }
        if (porteur) {
            lignes.add(ligne(SectionAFaire.EN_ATTENTE_PRMP, GesteAFaire.VOIR, ModeTache.TITULAIRE));
        }
    }

    /** Signature de lettre : régional, CC de la localité ; central, CC de la localité et Président. */
    private static void lettreASigner(Acteur a, EtatDossier d, List<Ligne> lignes) {
        // Garde : @perm.peutExercer('CHEF_COMMISSION') + profil admis selon la localité + localité (P exempté).
        String localite = d.idLocalite() != null && !d.idLocalite().isBlank()
                ? d.idLocalite() : d.lettreASigner().localiteReception();
        if (a.exerce(ProfilUtilisateur.CHEF_COMMISSION)
                && PredicatsIdentite.signatureLettreProfilAdmis(a.profil(), localite)
                && Visibilite.localiteAdmise(a.profil(), a.localite(), localite)) {
            lignes.add(ligne(SectionAFaire.LETTRES_A_SIGNER, GesteAFaire.SIGNER_LETTRE, ModeTache.TITULAIRE));
        }
    }

    /** Décision de retrait : CC de la localité du dossier et Président (le refus reste ouvert si le dossier a avancé). */
    private static void retraitADecider(Acteur a, EtatDossier d, List<Ligne> lignes) {
        if (a.exerce(ProfilUtilisateur.CHEF_COMMISSION)
                && PredicatsIdentite.peutDeciderRetrait(a.profil(), a.localite(), d.idLocalite())) {
            lignes.add(ligne(SectionAFaire.RETRAITS_A_DECIDER, GesteAFaire.DECIDER_RETRAIT, ModeTache.TITULAIRE));
        }
    }

    /** Archivage de lettre : Assistants de la localité ; CC par délégation ; jamais le Président (arbitrage 5). */
    private static void lettreAArchiver(Acteur a, EtatDossier d, List<Ligne> lignes) {
        if (a.profil() == ProfilUtilisateur.PRESIDENT || !a.exerce(ProfilUtilisateur.ASSISTANT_CONTROLEUR)
                || !PredicatsIdentite.localiteStricteAdmise(d.lettreAArchiver().localiteReception(), a.localite())) {
            return;
        }
        ModeTache mode = a.profil() == ProfilUtilisateur.ASSISTANT_CONTROLEUR ? ModeTache.TITULAIRE : ModeTache.DELEGATION;
        lignes.add(ligne(SectionAFaire.LETTRES_A_ARCHIVER, GesteAFaire.ARCHIVER_LETTRE, mode));
    }

    /**
     * PRMP et UGPM (arbitrage 6) : brouillons (la PRMP soumet, l'UGPM complète), attentes de la PRMP (gestes
     * réservés à la PRMP), suivi des dossiers à la CNM. Le périmètre (propriété) est appliqué au chargement.
     */
    private static void partieControlee(Acteur a, EtatDossier d, List<Ligne> lignes) {
        boolean prmp = a.profil() == ProfilUtilisateur.PRMP;
        String statut = d.statut();
        if (StatutDossier.BROUILLON.name().equals(statut)) {
            lignes.add(ligne(SectionAFaire.BROUILLONS,
                    prmp ? GesteAFaire.SOUMETTRE : GesteAFaire.COMPLETER_BROUILLON, ModeTache.TITULAIRE));
        } else if (ChronometrageService.estEnAttentePrmp(statut)) {
            if (!prmp) {
                return;   // transmettre et resoumettre : hasRole('PRMP')
            }
            if (StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT.name().equals(statut)) {
                lignes.add(ligne(SectionAFaire.PIECES_DEPOT_A_COMPLETER, GesteAFaire.COMPLETER_PIECES_DEPOT,
                        ModeTache.TITULAIRE));
            } else if (StatutDossier.EN_ATTENTE_PIECES.name().equals(statut)) {
                lignes.add(ligne(SectionAFaire.COMPLEMENTS_A_TRANSMETTRE, GesteAFaire.TRANSMETTRE_COMPLEMENTS,
                        ModeTache.TITULAIRE));
            } else {
                lignes.add(ligne(SectionAFaire.A_RECTIFIER, GesteAFaire.RECTIFIER, ModeTache.TITULAIRE));
            }
        } else if (STATUTS_EN_COURS_CNM.contains(statut)) {
            lignes.add(ligne(SectionAFaire.EN_COURS_CNM, GesteAFaire.SUIVRE, ModeTache.TITULAIRE));
        }
    }

    /** De SOUMIS à DECISION_TRANSMISE_SIGMP, hors attente PRMP et hors PV_SIGNE (transitoire, §7). */
    static final Set<String> STATUTS_EN_COURS_CNM = Set.of(
            StatutDossier.SOUMIS.name(), StatutDossier.PRET_DISPATCH.name(), StatutDossier.DISPATCHE.name(),
            StatutDossier.EXAMINE.name(), StatutDossier.A_REEXAMINER.name(), StatutDossier.EN_VERIFICATION.name(),
            StatutDossier.OBSERVATIONS_LEVEES.name(), StatutDossier.DECISION_TRANSMISE_SIGMP.name());

    /**
     * Statuts chargés pour la CNM : ceux où un geste ou un suivi existe. Exclus (§7) : BROUILLON (invisible des
     * contrôleurs), PV_SIGNE, CLOTURE, RETIRE, REMPLACE.
     */
    public static final Set<String> STATUTS_ACTIFS_CNM = Set.of(
            StatutDossier.SOUMIS.name(), StatutDossier.PRET_DISPATCH.name(), StatutDossier.DISPATCHE.name(),
            StatutDossier.EXAMINE.name(), StatutDossier.A_REEXAMINER.name(), StatutDossier.EN_VERIFICATION.name(),
            StatutDossier.OBSERVATIONS_LEVEES.name(), StatutDossier.DECISION_TRANSMISE_SIGMP.name(),
            StatutDossier.EN_ATTENTE_COMPLEMENTS_DEPOT.name(), StatutDossier.EN_ATTENTE_PIECES.name(),
            StatutDossier.EN_ATTENTE_DECISION_PRMP.name());

    /** Statuts chargés pour la PRMP et l'UGPM : ceux de la CNM, plus leurs brouillons. */
    public static final Set<String> STATUTS_ACTIFS_PARTIE_CONTROLEE;

    static {
        java.util.Set<String> statuts = new java.util.HashSet<>(STATUTS_ACTIFS_CNM);
        statuts.add(StatutDossier.BROUILLON.name());
        STATUTS_ACTIFS_PARTIE_CONTROLEE = Set.copyOf(statuts);
    }

    // ------------------------------------------------------------------ urgence

    /**
     * Seuil « bientôt » d'un délai standard, en heures ouvrées : {@code max(2, ⌈35 % × standard⌉)} (arbitrage 4).
     */
    public static long seuilBientotHeures(int standardHeures) {
        long part = (SEUIL_BIENTOT_PART_DU_STANDARD_PCT * (long) standardHeures + 99L) / 100L;
        return Math.max(SEUIL_BIENTOT_MINIMUM_HEURES, part);
    }

    /**
     * Urgence d'une ligne. Section chronométrée : reste &lt; 0 → {@code EN_RETARD} ; 0 ≤ reste ≤ seuil →
     * {@code BIENTOT} ; au-delà → {@code DANS_LES_DELAIS}. <strong>Entrée inconnue</strong> ({@code delaiCourant}
     * sans étape ou sans entrée : écoulé 0, pas d'échéance) → {@code SANS_DELAI}, plutôt qu'un « dans les délais »
     * inventé. Les autres sections ont une urgence fixe ({@link SectionAFaire.NatureDelai}).
     */
    public static UrgenceTache urgence(SectionAFaire section, ChronometrageService.DelaiCourant delai) {
        return urgence(section.natureDelai(), delai);
    }

    /**
     * ⚠️ <strong>Page dossier</strong> (2026-09-15, lot L4-B1, {@code GET /api/dossiers/{id}/gestes}) — urgence de
     * l'<strong>étape en cours</strong> d'un dossier, hors de toute ligne. Mêmes seuils que les lignes
     * ({@link #urgence(SectionAFaire, ChronometrageService.DelaiCourant)}) ; seule la nature du délai est lue sur le
     * statut au lieu de la section : {@code EN_PAUSE} sur un statut suspensif, {@code HORS_DELAI} pour un brouillon,
     * sinon chronométrée ({@code SANS_DELAI} si l'entrée est inconnue). <strong>Jamais {@code SUIVI}</strong> : le
     * suivi est le titre d'une ligne de la PRMP, pas un état de l'étape.
     */
    public static UrgenceTache urgenceEtape(String statut, ChronometrageService.DelaiCourant delai) {
        SectionAFaire.NatureDelai nature = ChronometrageService.estEnAttentePrmp(statut) ? SectionAFaire.NatureDelai.PAUSE
                : StatutDossier.BROUILLON.name().equals(statut) ? SectionAFaire.NatureDelai.HORS_DELAI
                : SectionAFaire.NatureDelai.CHRONOMETRE;
        return urgence(nature, delai);
    }

    private static UrgenceTache urgence(SectionAFaire.NatureDelai nature, ChronometrageService.DelaiCourant delai) {
        switch (nature) {
            case SUIVI:
                return UrgenceTache.SUIVI;
            case PAUSE:
                return UrgenceTache.EN_PAUSE;
            case HORS_DELAI:
                return UrgenceTache.HORS_DELAI;
            case SANS_DELAI:
                return UrgenceTache.SANS_DELAI;
            default:
                break;
        }
        if (delai == null || delai.etape() == null || delai.entree() == null || delai.restantHeures() == null
                || delai.standardHeures() == null) {
            return UrgenceTache.SANS_DELAI;
        }
        long reste = delai.restantHeures();
        if (reste < 0) {
            return UrgenceTache.EN_RETARD;
        }
        return reste <= seuilBientotHeures(delai.standardHeures()) ? UrgenceTache.BIENTOT : UrgenceTache.DANS_LES_DELAIS;
    }

    // ------------------------------------------------------------------ outils

    private static Ligne ligne(SectionAFaire section, GesteAFaire geste, ModeTache mode) {
        return new Ligne(section, geste, List.of(), mode);
    }

    /** Titulaire si la cible est le connecté ou s'il n'y a pas de cible (repli localité) ; collègue sinon. */
    private static ModeTache modeCible(Acteur a, String cible) {
        return cible == null || cible.equals(a.im()) ? ModeTache.TITULAIRE : ModeTache.COLLEGUE;
    }

    /** Localité du PV ({@code findLocaliteByPv}) : celle du circuit de son dispatch, à défaut de la réception. */
    private static String localitePv(EtatDossier d) {
        return d.circuitPv() != null && d.circuitPv().localite() != null
                ? d.circuitPv().localite() : d.localiteReception();
    }
}
