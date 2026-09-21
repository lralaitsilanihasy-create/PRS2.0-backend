package cnm.prs.service;

import cnm.prs.entity.Localite;
import cnm.prs.enums.NiveauNavette;
import cnm.prs.enums.ProfilUtilisateur;

/**
 * ⚠️ <strong>Qui peut agir : les prédicats d'identité du circuit</strong>, extraits des gardes (demande front du
 * 2026-09-14, accueil « À faire », §6).
 *
 * <p><strong>Pourquoi cette classe.</strong> Les conditions qui disent <em>qui</em> reçoit, dispatche, examine,
 * vise, signe, vérifie ou archive vivaient en privé dans les gardes de chaque service, écrites directement sur
 * {@code CurrentUser} et sur des lectures en base. L'accueil « À faire » doit poser les mêmes questions pour une
 * liste de dossiers, en lot, sans jeton ni requête par ligne : sans extraction, il les aurait recopiées — et deux
 * copies d'une règle de circuit ne restent jamais d'accord longtemps (c'est le motif qui a déjà fait naître
 * {@link CircuitDossierService#deuxNiveaux}).</p>
 *
 * <p><strong>Contrat.</strong> Fonctions <em>pures</em> : des valeurs en entrée (acteur, profil, localités,
 * dispatcheur, examinateur, niveau…), un booléen en sortie, aucune lecture de {@code CurrentUser} ni de base,
 * aucun effet. Les gardes existantes les appellent avec les valeurs qu'elles lisaient déjà, et gardent pour
 * elles l'ordre des vérifications, les messages et les codes HTTP — strictement inchangés.</p>
 *
 * <p><strong>Ce qui n'est pas ici.</strong> Les tâches de <em>profil</em> ouvertes par délégation
 * ({@code PermissionService.peutExercer}) dépendent de la table {@code t_delegation_profil} : elles restent
 * au service de permissions, et entrent ici comme un booléen déjà résolu quand un prédicat en a besoin. La
 * garde de localité « Président exempté » est dans {@link cnm.prs.security.Visibilite#localiteAdmise}, à côté
 * de celle qui l'applique ; le discriminant de la navette à deux niveaux, dans
 * {@link CircuitDossierService#deuxNiveaux(CircuitDossierService.Circuit, ProfilUtilisateur)}.</p>
 */
public final class PredicatsIdentite {

    private PredicatsIdentite() {
    }

    // ------------------------------------------------------------------ identités nominatives

    /**
     * L'acteur est-il l'<strong>attributaire</strong> (le {@code imCtrlMembre} du dispatch) ? Faux sans acteur
     * ou sans attributaire. Garde de l'examen (« seul l'attributaire courant examine », 2026-09-03), du retrait
     * de dispatch et de la lettre de renvoi.
     */
    public static boolean estAttributaire(String acteur, String attributaire) {
        return memePersonne(acteur, attributaire);
    }

    /** L'acteur est-il le <strong>dispatcheur</strong> courant ({@code IM_CTRL_DISPATCH}) ? Faux sans l'un ou l'autre. */
    public static boolean estDispatcheur(String acteur, String dispatcheur) {
        return memePersonne(acteur, dispatcheur);
    }

    /** L'acteur est-il l'<strong>examinateur</strong> du projet de PV ({@code IM_CTRL_MEMBRE}) ? Soumission (2026-09-08). */
    public static boolean estExaminateur(String acteur, String examinateur) {
        return memePersonne(acteur, examinateur);
    }

    /**
     * L'acteur est-il le <strong>co-signataire désigné</strong> au visa ({@code IM_MEMBRE_COSIGNATAIRE} ou
     * {@code IM_CC_COSIGNATAIRE}) ? Seul le désigné signe sa part (2026-08-28, élargi au CC le 2026-09-04).
     */
    public static boolean estDesigne(String acteur, String designe) {
        return memePersonne(acteur, designe);
    }

    /** Une désignation est-elle posée (matricule non vide) ? Sans elle, la part n'est pas encore ouverte (409). */
    public static boolean designationFaite(String designe) {
        return designe != null && !designe.isBlank();
    }

    // ------------------------------------------------------------------ examen et projet de PV

    /**
     * L'écriture d'un morceau d'examen (point de contrôle, pièce) est-elle ouverte <strong>par délégation</strong>,
     * sans exiger l'attributaire ? Oui pour tout profil autre que Membre — le CC ou le Président qui instruit par
     * délégation, sa localité étant vérifiée à part ({@code ExamenGarde}, audit lot B). Un Membre titulaire,
     * lui, doit être l'attributaire.
     */
    public static boolean ecritureExamenParDelegation(ProfilUtilisateur profil) {
        return profil != ProfilUtilisateur.MEMBRE;
    }

    /**
     * Rédaction du projet de PV par un tiers : profil autre que Membre <strong>et</strong> paire de délégation
     * active vers Membre ({@code delegationVersMembre}, résolue par {@code PermissionService}). Le Membre
     * titulaire non examinateur n'y a jamais droit : la délégation ascendante ne joue pas entre pairs.
     */
    public static boolean redactionParDelegation(ProfilUtilisateur profil, boolean delegationVersMembre) {
        return profil != ProfilUtilisateur.MEMBRE && delegationVersMembre;
    }

    // ------------------------------------------------------------------ dispatch

    /**
     * ⚠️ Pré-dispatch de la <strong>centrale</strong> réservé au Président (règle du pilote, 2026-09-03) : un Chef
     * de commission ne dispatche pas un dossier central — sauf à réattribuer celui dont il est attributaire ou
     * dispatcheur ({@code reattributionParConcerne}). Tout autre profil, et tout dossier régional : admis.
     */
    public static boolean peutDispatcherSelonLocalite(ProfilUtilisateur profil, boolean reattributionParConcerne,
            String localiteDossier) {
        return profil != ProfilUtilisateur.CHEF_COMMISSION || reattributionParConcerne
                || !Localite.estCentrale(localiteDossier);
    }

    /**
     * Le retrait d'un dossier au Membre est-il soumis à la garde « dispatcheur, sans auto-retrait » (2026-09-03) ?
     * Seulement pour le Chef de commission ; le Président n'est pas restreint.
     */
    public static boolean retraitDispatchReserveAuDispatcheur(ProfilUtilisateur profil) {
        return profil == ProfilUtilisateur.CHEF_COMMISSION;
    }

    // ------------------------------------------------------------------ navette et visa

    /**
     * ⚠️ <strong>« Niveau nul = étage du CC »</strong> (audit 2026-09-14, E4) — sur un circuit à deux niveaux, un
     * PV sans {@code NIVEAU_NAVETTE} (soumis avant V17) est chez le CC : c'est le niveau {@link NiveauNavette#CC}.
     */
    public static NiveauNavette niveauEffectif(NiveauNavette niveau) {
        return niveau == null ? NiveauNavette.CC : niveau;
    }

    /** Le PV est-il à l'étage attendu, un niveau nul valant l'étage du CC ? */
    public static boolean estALEtage(NiveauNavette niveau, NiveauNavette attendu) {
        return niveauEffectif(niveau) == attendu;
    }

    /**
     * L'étage du bas d'une navette à deux niveaux (accepter, retourner au Membre) appartient au <strong>CC du
     * circuit</strong>, c'est-à-dire à son dispatcheur courant (le CC qui a réattribué).
     */
    public static boolean estCcDuCircuit(String acteur, CircuitDossierService.Circuit circuit) {
        return circuit != null && memePersonne(acteur, circuit.dispatcheur());
    }

    /** Le profil peut-il viser ? Président (§3.2) ou Chef de commission (§3.3). */
    public static boolean estProfilViseur(ProfilUtilisateur profil) {
        return profil == ProfilUtilisateur.PRESIDENT || profil == ProfilUtilisateur.CHEF_COMMISSION;
    }

    /**
     * Sur une navette à deux niveaux, le visa — et le retour du projet au CC — appartiennent au
     * <strong>Président</strong> (arbitrage 2 du pilote, 2026-09-04).
     */
    public static boolean estViseurDeuxNiveaux(ProfilUtilisateur profil) {
        return profil == ProfilUtilisateur.PRESIDENT;
    }

    /**
     * Le visa est-il <strong>par intérim</strong> ? Navette simple et acteur autre que le dispatcheur — il lui faut
     * alors la note d'intérim. Jamais sur deux niveaux : la règle d'identité y porte sur le profil et l'étage.
     */
    public static boolean visaParInterim(boolean deuxNiveaux, String acteur, String dispatcheur) {
        return !deuxNiveaux && !memePersonne(acteur, dispatcheur);
    }

    /**
     * ⚠️ <strong>L'examinateur ne vise pas son propre examen</strong> (arbitrage du pilote, 2026-09-08), intérim
     * compris — sauf s'il est aussi le dispatcheur, qui cumule alors légitimement. Vrai quand l'acteur peut viser
     * (ou retourner) au regard de cette seule règle : il n'est pas l'examinateur, ou il est aussi le dispatcheur.
     * Ne s'applique qu'à la navette simple (l'appelant en décide).
     */
    public static boolean viseurHorsExaminateur(String acteur, String examinateur, String dispatcheur) {
        if (acteur == null || examinateur == null || !examinateur.equals(acteur)) {
            return true;   // l'acteur n'est pas l'examinateur : la règle ne le concerne pas
        }
        return dispatcheur != null && dispatcheur.equals(acteur);   // exception : il s'est dispatché le dossier
    }

    // ------------------------------------------------------------------ localité stricte, lettres, retraits

    /**
     * ⚠️ <strong>Localité stricte</strong>, sans l'exemption du Président que porte
     * {@code Visibilite.localiteAdmise} — garde de l'<strong>archivage</strong> (PV et lettres), de la
     * <strong>transmission SIGMP</strong> et de la co-signature du CC : une ressource localisée n'est traitée que
     * par un acteur de la même localité ; un acteur sans localité (le Président) en est donc exclu. Une ressource
     * sans localité ne contraint rien.
     *
     * @param localiteActeur localité de l'acteur, {@code null} si absente ou vide
     */
    public static boolean localiteStricteAdmise(String localiteRessource, String localiteActeur) {
        return localiteRessource == null || localiteRessource.equals(localiteActeur);
    }

    /**
     * Signature d'une lettre de renvoi, au regard du profil : une lettre <strong>régionale</strong> ne se signe que
     * par un Chef de commission ; une lettre centrale reste ouverte au profil (Président ou CC, la localité étant
     * vérifiée à part).
     */
    public static boolean signatureLettreProfilAdmis(ProfilUtilisateur profil, String localiteLettre) {
        return Localite.estCentrale(localiteLettre) || profil == ProfilUtilisateur.CHEF_COMMISSION;
    }

    /**
     * Décision d'une demande de retrait : le <strong>Président</strong>, ou le <strong>Chef de commission de la
     * localité du dossier</strong> (§3.3).
     *
     * @param localiteActeur localité de l'acteur, {@code null} si absente ou vide
     */
    public static boolean peutDeciderRetrait(ProfilUtilisateur profil, String localiteActeur, String localiteDossier) {
        if (profil == ProfilUtilisateur.PRESIDENT) {
            return true;
        }
        return profil == ProfilUtilisateur.CHEF_COMMISSION && localiteDossier != null
                && localiteDossier.equals(localiteActeur);
    }

    // ------------------------------------------------------------------ identités étendues par intérim

    /**
     * ⚠️ <strong>Intérim désigné</strong> (lot 1, 2026-09-21, §B4.1) — les mêmes prédicats nominatifs, posés
     * sur les <strong>identités</strong> du connecté : la sienne, plus celle de chaque titulaire qu'il supplée
     * aujourd'hui ({@code InterimContexte.identites(moi)}). « Le dispatcheur » devient ainsi « le dispatcheur
     * ou son intérimaire actif » sans qu'aucune garde ne réécrive la règle ; ce qui n'est PAS étendu
     * (l'attributaire, l'examinateur, le Membre désigné — actes d'attributaire, hors lot) garde les surcharges
     * à un acteur. Toujours purs : l'ensemble est fourni par l'appelant.
     */
    public static boolean estDispatcheurParmi(java.util.Collection<String> identites, String dispatcheur) {
        return memePersonne(identites, dispatcheur);
    }

    /**
     * L'attributaire, ou son intérimaire actif — pour la seule <strong>réattribution</strong> par le CC attributaire
     * d'un dossier central (dérogation du 2026-09-03), qui est un acte de CC. L'examen, lui, reste à l'attributaire
     * seul ({@link #estAttributaire(String, String)}).
     */
    public static boolean estAttributaireParmi(java.util.Collection<String> identites, String attributaire) {
        return memePersonne(identites, attributaire);
    }

    /** Le CC désigné co-signataire, ou son intérimaire actif (la part CC se signe par intérim, §B4.4). */
    public static boolean estDesigneParmi(java.util.Collection<String> identites, String designe) {
        return memePersonne(identites, designe);
    }

    /** Le CC du circuit, ou son intérimaire actif (acceptation et retour au Membre sur deux niveaux). */
    public static boolean estCcDuCircuitParmi(java.util.Collection<String> identites, CircuitDossierService.Circuit circuit) {
        return circuit != null && memePersonne(identites, circuit.dispatcheur());
    }

    /**
     * L'acteur a-t-il déjà <strong>signé une part</strong> de ce PV, sous son propre nom ? « Une part par personne
     * et par PV » (§B4.4-5) : l'intérimaire qui a déjà signé une autre part ne signe pas celle du titulaire —
     * elle attend le titulaire ou un autre intérimaire. Chaque part est jugée sur sa date ET son signataire.
     */
    public static boolean aDejaSigne(String acteur, String imPresident, java.time.LocalDate datePresident,
            String imCc, java.time.LocalDate dateCc, String imMembreCoSignataire, java.time.LocalDate dateMembre) {
        if (acteur == null) {
            return false;
        }
        return (datePresident != null && acteur.equals(imPresident))
                || (dateCc != null && acteur.equals(imCc))
                || (dateMembre != null && acteur.equals(imMembreCoSignataire));
    }

    private static boolean memePersonne(java.util.Collection<String> identites, String titulaire) {
        return titulaire != null && identites != null && identites.contains(titulaire);
    }

    private static boolean memePersonne(String acteur, String titulaire) {
        return acteur != null && acteur.equals(titulaire);
    }
}
