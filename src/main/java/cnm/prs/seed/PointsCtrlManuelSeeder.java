package cnm.prs.seed;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.repository.PointsCtrlRepository;
import cnm.prs.repository.SousTypeDossierRepository;
import cnm.prs.repository.TypeDossierRepository;
import cnm.prs.service.ClePrimaire;

/**
 * ⚠️ 2026-10-08 (manuel de contrôle a priori, tranche M3, §B3 ; V96) — les points de contrôle du <em>Manuel de contrôle a priori</em>
 * (CNM, février 2026), sous-type par sous-type : le libellé court, la question du manuel en description, la portée, et la condition
 * (catégorie de la fiche, forme). Même patron que {@link PointsCtrlDossierMarcheSeeder} : un point absent (repéré par sa famille, son
 * sous-type et son libellé) est créé sous une PK libre ; un point existant n'est jamais touché ; rien pour une famille ou un sous-type
 * absent du référentiel. Désactivable avec {@code app.seed.points-ctrl-manuel.enabled=false}.
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "app.seed.points-ctrl-manuel.enabled", havingValue = "true", matchIfMissing = true)
public class PointsCtrlManuelSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PointsCtrlManuelSeeder.class);

    private static final String FS = "FOURNITURES_SERVICES";
    private static final String TRAVAUX = "TRAVAUX";
    private static final String CC = "CONTRAT_CADRE";

    /** Un point du manuel : famille, sous-type (nul : commun à la famille), portée, libellé, question, catégorie, forme. */
    record Graine(String famille, String sousType, PorteePointCtrl portee, String libelle, String question, String categorie, String forme) {
    }

    private static Graine d(String famille, String sousType, String libelle, String question) {
        return new Graine(famille, sousType, PorteePointCtrl.DOSSIER, libelle, question, null, null);
    }

    private static Graine c(String famille, String sousType, String libelle, String question, String categorie, String forme) {
        return new Graine(famille, sousType, PorteePointCtrl.DOSSIER, libelle, question, categorie, forme);
    }

    static final List<Graine> GRAINES = List.of(
            // ---------------------------------------------------------------- PPM (ch. 2-I) : communs à la famille DDP
            new Graine("DDP", null, PorteePointCtrl.FICHE, "Motifs de la mise à jour", "Les motifs de la mise à jour du PPM sont-ils conformes au CMP ?", null, null),
            new Graine("DDP", null, PorteePointCtrl.LIGNE, "Mode de passation", "Le mode respecte-t-il les textes ? Pour un contrat-cadre : prestations répétitives et indéterminées, durée, ou urgence ?", null, null),
            new Graine("DDP", null, PorteePointCtrl.LIGNE, "Dates prévisionnelles et délais aménagés", "Dates cohérentes avec le mode et les délais ? Délai aménagé appuyé (décision du Gouvernement, imprévisible, infructuosité, urgence, AGPM publié 3 mois avant, PI < 100 M Ar HT), objet portant « délai réduit » ?", null, null),
            new Graine("DDP", null, PorteePointCtrl.LIGNE, "Mentions de l'objet", "Objet explicite : type et quantité, site et consistance (travaux), domaine (PI), immatriculation (véhicules), lots et tranches, « relance », « délai réduit », « contrôle a priori » ?", null, null),
            d("DDP", null, "Fractionnement illicite (base d'appréciation)", "Base du fractionnement : compte PCOP/PCG, une seule RN ou un périmètre irrigué (travaux), TDR identiques (PI), en distinguant financement, forme, entretien et réhabilitation."),
            // ---------------------------------------------------------------- DAOO (ch. 2-II-A, B) — base de DAOOI, DAOOPREQUAL, DAOR
            d("DMC", "DAOO", "Fiche de présentation cohérente", "Objet, mode, allotissement, montant estimé cohérents avec l'AGPM et le DAO ? La disponibilité du DAO est-elle en retard sur l'AGPM ?"),
            d("DMC", "DAOO", "Délai de remise des offres", "Le délai minimum est-il respecté (30 jours ; 15 jours en délai aménagé, art. 3.a du décret 2019-1310) ?"),
            d("DMC", "DAOO", "AGPM contrôlé et publié", "L'AGPM a-t-il reçu l'avis de la CNM avant sa publication, et une copie du journal accompagne-t-elle le dossier ?"),
            d("DMC", "DAOO", "Avis spécifique conforme", "L'avis (SIGMP) suit-il le modèle : objet, lots, forme des prix, adresses, date et heure de remise et d'ouverture, coût du DAO et paiement, forme et montant de la garantie ?"),
            d("DMC", "DAOO", "Conformité aux documents types", "Le DAO est-il conforme, clause par clause, au document type et au modèle de DAO de la catégorie ?"),
            d("DMC", "DAOO", "Offres anormales et quantités", "Les seuils d'offre anormale sont-ils au plus 20 % (haute) et 10 % (basse), et la modification des quantités au plus 20 % ?"),
            d("DMC", "DAOO", "Remise et ouverture des plis", "Heure et lieu de remise cohérents avec l'avis ; ouverture à l'heure limite, au même lieu (ou très proche) ?"),
            d("DMC", "DAOO", "Formulaires de soumission", "Les formulaires existent-ils et sont-ils cohérents avec le DAO ; la déclaration des bénéficiaires effectifs est-elle jointe ?"),
            d("DMC", "DAOO", "Qualifications particulières proportionnées", "Qualifications pertinentes et proportionnées (indicatif : chiffre d'affaires 1 à 1,5 fois l'estimation, marché similaire du même ordre, liquidité ≤ 30 % de l'offre) ?"),
            c("DMC", "DAOO", "Quantités fixes ou à commande", "La rédaction distingue-t-elle quantités fixes et à commande (DPAO 1.2, AE art. 2 et 5, CCAP art. 6, 10, 12.1, 14, 21) ; à commande, proportion min / max identique d'un article à l'autre ?", FS, null),
            c("DMC", "DAOO", "Spécifications neutres", "Les spécifications sont-elles neutres et optimales : ni marque ni technologie précise, sans critère superflu ?", FS, null),
            c("DMC", "DAOO", "Visite des lieux", "Visite cohérente avec l'avis (obligatoire ou non, organisée ou non) ; si obligatoire et organisée, au moins une période ou deux dates ?", TRAVAUX, null),
            c("DMC", "DAOO", "Prix unitaires ou forfaitaires", "La forme de prix est-elle tenue partout (AE art. 2, BPU, DQE, coefficient K, sous-détails ; DQE seul au forfait ; CCAP art. 12.1 et 16) ?", TRAVAUX, null),
            c("DMC", "DAOO", "Garantie décennale", "Pour une construction neuve, la garantie décennale est-elle prévue (CCAP art. 8, partie C) ?", TRAVAUX, null),
            c("DMC", "DAOO", "Spécifications et DQE", "Chapitres II (matériaux) et III (exécution) cohérents avec le DQE ; le chapitre IV distingue-t-il mode d'évaluation (prix unitaires) et devis descriptif (forfait) ?", TRAVAUX, null),
            c("DMC", "DAOO", "Personnel et matériel exigés", "Le personnel exigé se limite-t-il à l'encadrement (conducteur des travaux, chef de chantier), le matériel aux engins essentiels ?", TRAVAUX, null),
            // Contrat-cadre (ch. 2-II-E)
            c("DMC", "DAOO", "Recours au contrat-cadre justifié", "Le besoin est-il répétitif et indéterminé (ou une urgence sur une période) ? Sinon, proposer le marché à commandes.", null, CC),
            c("DMC", "DAOO", "Critères d'élimination et d'attribution", "Pièces de candidature et critères cohérents ? Ni pièce administrative comme critère d'élimination ou d'attribution, ni spécification technique comme critère d'attribution.", null, CC),
            c("DMC", "DAOO", "Critères de conformité de l'offre", "Objectifs, réalistes, en rapport avec l'objet (attestation du fabricant, autorisation, certificat de normes, après-vente, garantie commerciale, garantie de soumission) ?", null, CC),
            c("DMC", "DAOO", "Pourcentages du règlement", "Pourcentages du règlement (9.1) et de l'AE / CCAP (art. 19) cohérents ? Travaux : prix d'installation et de repli de chantier supprimés ?", null, CC),
            c("DMC", "DAOO", "Délais de remise (contrat-cadre)", "Délais de remise du contrat-cadre et des marchés subséquents suffisants pour une concurrence saine ?", null, CC),
            // ---------------------------------------------------------------- variantes internationales (Q2)
            d("DMC", "DAOOI", "Publicité internationale", "L'avis est-il publié dans un journal national et dans un journal de portée internationale (décret 2019-1310, art. 2.1) ?"),
            d("DMC", "DAORI", "Publicité internationale", "La consultation restreinte internationale suit-elle la publicité exigée (décret 2019-1310, art. 2.1) ?"),
            // ---------------------------------------------------------------- DAOR (ch. 2-II-D) : grille DAOO, plus
            d("DMC", "DAOR", "Motif de l'appel d'offres restreint", "Le motif relève-t-il de l'un des quatre cas (urgence avérée, caractère confidentiel, prestataire défaillant, petit nombre de prestataires) et est-il pertinent ?"),
            d("DMC", "DAOR", "Constitution de la liste restreinte", "Critères neutres et liste conforme au motif (capacités ; moralité ; participants à l'AO ; organisme professionnel reconnu) ? Au moins trois candidats."),
            d("DMC", "DAOR", "PV de la CAO sur la liste", "Le PV de validation de la liste restreinte par la CAO est-il joint et cohérent ?"),
            d("DMC", "DAOR", "Lettre d'invitation", "La lettre d'invitation (SIGMP) est-elle conforme et cohérente avec le DAO ?"),
            d("DMC", "DAOR", "Délai de remise (restreint)", "Le délai respecte-t-il les articles 2 et 3 du décret 2019-1310 ?"),
            // ---------------------------------------------------------------- DPREQUAL (ch. 2-II-C) : grille propre
            d("DMC", "DPREQUAL", "Recours à la pré-qualification", "L'importance ou la complexité des prestations justifie-t-elle une pré-qualification ?"),
            d("DMC", "DPREQUAL", "Critères de qualification pertinents", "Moyens humains et matériels, capacité financière, références : proportionnés à l'objet et au montant (mêmes repères que le DAOO) ?"),
            d("DMC", "DPREQUAL", "Pièces d'appui", "AGPM publié, PPM contrôlé, avis spécifique du SIGMP joints ?"),
            // ---------------------------------------------------------------- DC (ch. 2-II-F) : grille propre
            d("DMC", "DC", "Fiche de présentation cohérente", "Objet, mode (clause 1 des DPIC), allotissement, montant estimé cohérents avec l'AGPM et le DC ; retard de disponibilité du DC ?"),
            d("DMC", "DC", "Délai de remise des propositions", "Le délai minimum est-il respecté ?"),
            d("DMC", "DC", "AGPM contrôlé et publié", "Avis de la CNM avant publication, journal joint ?"),
            d("DMC", "DC", "AMI : délai et ouverture", "Délai de remise des manifestations d'intérêt respecté (sinon : relancer l'AMI) ; date et heure d'ouverture celles de l'AMI ?"),
            d("DMC", "DC", "AMI : PV d'ouverture", "Quorum et qualité des signataires (président de la CAO ou son représentant, membres désignés, candidats présents) ?"),
            d("DMC", "DC", "AMI : rapport et PV de validation", "Évaluation selon les critères publiés (expérience générale et spécifique) ; PV de validation cohérent, quorum atteint ?"),
            d("DMC", "DC", "Décision autorisant la liste restreinte", "La liste est-elle celle des documents d'évaluation, les considérants pertinents ?"),
            d("DMC", "DC", "Lettre d'invitation", "Conforme au modèle (SIGMP) : objet, heure de remise et d'ouverture, liste des candidats, mode de sélection, adresse, coût du DC et paiement ?"),
            d("DMC", "DC", "Conformité aux documents types", "Le DC est-il conforme, clause par clause, au document type ?"),
            d("DMC", "DC", "Mode de sélection adapté", "Mode adapté à l'objet : qualité seule (grande complexité), qualité-coût (moyenne), budget déterminé, moindre coût (classiques), qualification du consultant ?"),
            d("DMC", "DC", "Forme de rémunération", "Prix unitaires (contrôle, surveillance, formation) ou forfait (audits, études) ; frais remboursables et frais divers pertinents ?"),
            d("DMC", "DC", "Critères et personnel clé", "Pondération des critères et sous-critères cohérente ; qualifications du personnel clé cohérentes avec les TDR et neutres ?"),
            d("DMC", "DC", "Remise et ouverture", "Dates, heures et lieu cohérents avec la lettre d'invitation ; ouverture des propositions techniques à l'heure limite, au même lieu ?"),
            d("DMC", "DC", "Acte d'engagement et délais", "Prix (unitaires / forfait, national / international) ; délais cohérents entre DPIC, AE, CCAP et TDR ; annexes conformes à la clause 7.2.2 ?"),
            d("DMC", "DC", "CCAP : modalités de règlement", "Cohérentes avec les livrables des TDR (nombre, périodicité) et la forme de rémunération ?"),
            d("DMC", "DC", "TDR cohérents", "Profil du consultant, livrables, délai : sans incohérence majeure avec le reste du dossier ?"),
            // ---------------------------------------------------------------- RJ, MGG (ch. 2-II-G) : grilles propres
            d("DMC", "RJ", "Motif de l'article 39-II", "Cas autorisé : prestations secrètes, urgence impérieuse (décret 2022-800), droit d'exclusivité, marché complémentaire, qualification unique / continuité (PI) ?"),
            d("DMC", "RJ", "Pièces à l'appui du motif", "Pièces du cas jointes : autorité autre que la PRMP (secret) ; comptes rendus, photos, décision (urgence) ; attestation d'exclusivité d'un organisme compétent ; marché initial (complémentaire) ?"),
            d("DMC", "RJ", "Marché complémentaire : conditions cumulatives", "Marché initial par AO ; prestations nouvelles nécessaires (circonstance imprévue), inséparables ; cumul ≤ 1/3 du marché principal, avenants non compris ?"),
            d("DMC", "RJ", "PPM et décision", "La prestation figure-t-elle au PPM contrôlé ; le projet de décision est-il joint ?"),
            d("DDM", "MGG", "Choix du titulaire justifié", "Capacités juridique (NIF, STAT, RCS), technique (marchés similaires, matériel, personnel clé) et financière (liquidité, chiffre d'affaires) justifiées ?"),
            d("DDM", "MGG", "Montant justifié", "Par un marché similaire passé par AO ouvert, l'analyse des sous-détails signés, ou la consultation d'au moins trois candidats ? Prix exorbitants : négociation consignée au PV."),
            d("DDM", "MGG", "PV de validation de la CAO", "Cohérent et pertinent ; quorum et qualité des signataires (président de la CAO ou son représentant, membres désignés) ?"),
            // ---------------------------------------------------------------- MAOO (ch. 2-III-C) : en plus des points communs DDM
            d("DDM", "MAOO", "Délai de remise des offres", "Décompté de la 1re publication de l'avis (journal ou site de l'ARMP) : suffisant ? Date de 1re publication clairement établie ?"),
            d("DDM", "MAOO", "PV d'ouverture : date et heure", "Cohérentes avec l'avis ? (Le PV ne se modifie jamais ; une erreur de transcription se prouve par le registre et une déclaration des membres.)"),
            d("DDM", "MAOO", "PV d'ouverture : quorum et signataires", "Président de la CAO ou son représentant, membres désignés, candidats présents ?"),
            d("DDM", "MAOO", "Conformité des documents essentiels", "AE daté et signé, validité de l'offre, délai, garantie de soumission (montant, forme, validité), attestation du fabricant (fournitures), certificat de visite (travaux) ?"),
            d("DDM", "MAOO", "Spécifications techniques", "Les spécifications proposées sont-elles conformes, sans réserve, divergence ni omission substantielle ?"),
            d("DDM", "MAOO", "Moralité des prix", "Le rapport mentionne-t-il expressément la vérification de la moralité des prix unitaires par la CAO (circulaire, point XII-2) ?"),
            d("DDM", "MAOO", "Qualification : activités et critères", "Activités du candidat (NIF, STAT) couvrant l'objet ; seuls critères du DAO validé appliqués (fiches de renseignement ignorées hors qualifications particulières) ?"),
            d("DDM", "MAOO", "PV de validation cohérent", "Les résultats du PV sont-ils ceux du rapport ; quorum et signataires ?"),
            d("DDM", "MAOO", "Validité de l'offre retenue", "L'offre de l'attributaire est-elle encore valide, ou sa prorogation obtenue avant expiration ?"),
            d("DDM", "MAOOI", "Publicité internationale de l'avis", "L'avis a-t-il été publié dans un journal national et dans un journal de portée internationale (décret 2019-1310, art. 2.1) ?"),
            // ---------------------------------------------------------------- MAOR (ch. 2-III-D) : grille MAOO, plus
            d("DDM", "MAOR", "Décision autorisant l'AO restreint", "Jointe et signée de l'Autorité contractante ?"),
            d("DDM", "MAOR", "Réception des lettres d'invitation", "Tous les candidats de la liste ont-ils reçu la lettre (accusés) ; le délai de remise, décompté de la dernière réception, est-il respecté ?"),
            // ---------------------------------------------------------------- MPI (ch. 2-III-E) : grille propre
            d("DDM", "MPI", "Lettres d'invitation", "Délai de remise décompté de la dernière réception : respecté ?"),
            d("DDM", "MPI", "PV d'ouverture technique", "Date et heure cohérentes avec la lettre ; quorum et signataires ?"),
            d("DDM", "MPI", "Documents essentiels", "Lettre de soumission, pouvoir (groupement), personnel clé avec CV, méthodologie, calendrier, programme de travail : présents ?"),
            d("DDM", "MPI", "Notation technique transparente", "Notation selon les critères, sous-critères et pondérations des DPIC, conforme aux TDR ; détail des notes de chaque membre au rapport ; rejet sous le score minimum ?"),
            d("DDM", "MPI", "PV de validation technique", "Cohérent avec le rapport ; quorum et signataires ?"),
            d("DDM", "MPI", "Notification des résultats techniques", "Lettres aux non retenus et information de l'ouverture financière aux retenus, avec accusé ?"),
            d("DDM", "MPI", "PV d'ouverture financière", "Quorum et signataires ?"),
            d("DDM", "MPI", "Évaluation finale", "Corrections selon les IC, rabais pris en compte, frais remboursables exclus, moralité des prix mentionnée, classement selon la méthode ?"),
            d("DDM", "MPI", "PV de validation finale", "Cohérent avec les rapports technique et financier ; quorum et signataires ?"),
            d("DDM", "MPI", "Proposition financière acceptable", "Montant au plus 20 % au-dessus de l'estimation, avec attestation de disponibilité des fonds (DAF ou ORDSEC) ; plus d'une proposition conforme ?"),
            d("DDM", "MPI", "Négociation", "Qualité technique seule : négociation avec le premier classé, personnel clé confirmé, sans changement substantiel ?"),
            // ---------------------------------------------------------------- AVN (ch. 2-IV) : grille propre
            d("DGC", "AVN", "Fiche de présentation cohérente", "Objet, historique du marché, motif et consistance de l'avenant cohérents avec l'accord des parties et le marché initial ?"),
            d("DGC", "AVN", "Avenant recevable", "Marché initial signé et approuvé ; avant la réception définitive (travaux) ou provisoire (fournitures, services) et le solde ; avance inchangée ; augmentation ≤ 1/3 du prix initial ?"),
            d("DGC", "AVN", "Élément déclencheur", "Circonstance indépendante de la volonté des parties, attestée par une autorité autre que la PRMP (relevés météo, attestation des TP…) — pas d'avenant de convenance ?"),
            d("DGC", "AVN", "Avenant nécessaire", "La modification dépasse-t-elle ce que le marché permet (variations du CPS / CCAP, calendrier, lieu, nature des prix, statut du titulaire) ?"),
            d("DGC", "AVN", "Accord des parties", "L'accord reflète-t-il toutes les modifications (délais, prix, montant) ?"),
            d("DGC", "AVN", "Nouveaux prix", "Prix appréciés par la CAO (sous-détails ou marchés similaires), pièces cohérentes ; quorum et signataires du PV ?"),
            d("DGC", "AVN", "Délai de présentation", "Projet présenté avant l'expiration du délai d'exécution, ou écart justifié ?"),
            // ---------------------------------------------------------------- DSS (ch. 2-V) : grille propre
            d("DSS", "DSS", "Fiche de présentation", "Historique de la mise en concurrence et motif cohérents avec les pièces ?"),
            d("DSS", "DSS", "Motif d'intérêt général", "Motif pertinent (intérêt public, commun, des finances publiques), appuyé de documents signés, pas de simple allégation ?"),
            d("DSS", "DSS", "Recevabilité", "Décision avant toute signature du marché ? (Avis à rendre sous cinq jours ; un refus oblige à reprendre la procédure.)"),
            // ---------------------------------------------------------------- actes de gestion contractuelle (ch. 3) : communs DGC, puis propres
            d("DGC", null, "Fiche de présentation cohérente", "Historique du marché et motif cohérents avec les pièces ?"),
            d("DGC", null, "Marché contrôlé a priori", "Le marché a-t-il été contrôlé a priori par la Commission (PV joint) ?"),
            d("DGC", null, "Pièces signées", "Les pièces sont-elles signées, sans simple allégation ?"),
            d("DGC", "INDEMN", "Cas d'indemnisation fondé", "Minimum de commande non atteint, résiliation aux torts de l'administration, ajournement, imprévision, diminution de la masse (4 % au plus) ?"),
            d("DGC", "INDEMN", "Calcul de l'indemnité", "Calcul de l'indemnité justifié et accepté par les deux parties ?"),
            d("DGC", "SURSIS", "Difficultés exceptionnelles", "Difficultés exceptionnelles, imprévisibles, non imputables au titulaire ?"),
            d("DGC", "SURSIS", "Demande dans les délais", "Demande dans les 10 jours de l'apparition des causes, avant l'expiration du délai contractuel ?"),
            d("DGC", "SURSIS", "Nombre de jours justifié", "Le nombre de jours de sursis est-il justifié ?"),
            d("DGC", "PENAL", "Accord formel de la PRMP", "Pièces justificatives et accord formel de la PRMP joints ?"),
            d("DGC", "PENAL", "Calcul des pénalités", "Calcul du nombre de jours et du montant des pénalités justifié ?"),
            d("DGC", "DR", "Motif de résiliation fondé", "Faute grave, carence, liquidation, intérêt général ; défaut de paiement > 6 mois, ajournement > 3 mois ; force majeure ; garantie non fournie ; manquement au code d'éthique ?"),
            d("DGC", "DR", "Mise en demeure motivée", "Mise en demeure motivée et information préalable du titulaire (résiliation à ses torts) ?"),
            d("DGC", "DR", "Indemnité prévue", "Indemnité prévue en cas de résiliation aux torts de l'administration ?"));

    private final PointsCtrlRepository pointsCtrlRepository;
    private final TypeDossierRepository typeDossierRepository;
    private final SousTypeDossierRepository sousTypeDossierRepository;

    public PointsCtrlManuelSeeder(PointsCtrlRepository pointsCtrlRepository, TypeDossierRepository typeDossierRepository,
            SousTypeDossierRepository sousTypeDossierRepository) {
        this.pointsCtrlRepository = pointsCtrlRepository;
        this.typeDossierRepository = typeDossierRepository;
        this.sousTypeDossierRepository = sousTypeDossierRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        semer();
    }

    /** Sème ce qui manque ; rend le nombre de points créés. */
    @Transactional
    public int semer() {
        List<PointsCtrl> existants = pointsCtrlRepository.findAll();
        int prochainOrdre = existants.stream().map(PointsCtrl::getOrdrePointCtrl).filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
        int crees = 0;
        for (Graine g : GRAINES) {
            if (!typeDossierRepository.existsById(g.famille()) || g.sousType() != null && !sousTypeDossierRepository.existsById(g.sousType())) {
                continue;
            }
            boolean deja = existants.stream().anyMatch(p -> g.famille().equals(p.getIdTypeDossier()) && Objects.equals(g.sousType(), p.getIdSousType())
                    && g.libelle().equalsIgnoreCase(p.getLibelPointCtrl() == null ? "" : p.getLibelPointCtrl().trim()));
            if (deja) {
                continue;
            }
            PointsCtrl point = new PointsCtrl();
            point.setIdPointCtrl(ClePrimaire.allouerLibre(pointsCtrlRepository::existsById, pointsCtrlRepository::nextIdPointCtrl));
            point.setLibelPointCtrl(g.libelle());
            point.setDecriptPointCtrl(g.question().length() > 255 ? g.question().substring(0, 254) + "…" : g.question());
            point.setOrdrePointCtrl(prochainOrdre++);
            point.setObligatoire(Boolean.TRUE);
            point.setIdTypeDossier(g.famille());
            point.setIdSousType(g.sousType());
            point.setPortee(g.portee());
            point.setCategorie(g.categorie());
            point.setForme(g.forme());
            pointsCtrlRepository.save(point);
            existants.add(point);
            crees++;
        }
        if (crees > 0) {
            log.info("[SEED] points de contrôle du manuel créés : {}", crees);
        }
        return crees;
    }
}
