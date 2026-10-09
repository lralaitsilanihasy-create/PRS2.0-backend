package cnm.prs.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.MotifType;
import cnm.prs.repository.MotifTypeRepository;
import cnm.prs.repository.SousTypeDossierRepository;
import cnm.prs.repository.TypeDossierRepository;

/**
 * ⚠️ 2026-10-09 (manuel de contrôle a priori, tranche M4, §B4 ; V97) — les motifs-types du <em>Manuel de contrôle a priori</em> (CNM,
 * février 2026) : « formulation de la conclusion » de chaque type — motifs de demande de compléments (RENVOI) et motifs pouvant conduire
 * à ne pas donner un avis favorable (AVIS_DEFAVORABLE). Même patron que {@link PointsCtrlManuelSeeder} : un motif absent (repéré par sa
 * famille, son sous-type, sa nature et son libellé) est créé ; un motif existant n'est jamais touché, même désactivé ou modifié par
 * l'Administrateur ; rien pour une famille ou un sous-type absent du référentiel. Les plans (ch. 2-I) n'ont pas de motifs au manuel.
 * Désactivable avec {@code app.seed.motifs-types-manuel.enabled=false}.
 */
@Component
@Order(21)
@ConditionalOnProperty(name = "app.seed.motifs-types-manuel.enabled", havingValue = "true", matchIfMissing = true)
public class MotifsTypesManuelSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MotifsTypesManuelSeeder.class);

    private static final String R = MotifType.RENVOI;
    private static final String D = MotifType.AVIS_DEFAVORABLE;
    private static final String FS = "FOURNITURES_SERVICES";
    private static final String TRAVAUX = "TRAVAUX";

    /** Un motif du manuel : famille, sous-type (nul : commun à la famille), nature, libellé, texte, catégorie. */
    record Graine(String famille, String sousType, String nature, String libelle, String texte, String categorie) {
    }

    private static Graine g(String famille, String sousType, String nature, String libelle, String texte) {
        return new Graine(famille, sousType, nature, libelle, texte, null);
    }

    private static final String JOINDRE = "Joindre ou authentifier des documents";
    private static final String JOINDRE_TEXTE = "Joindre les documents suivants, ou en produire l'original signé par le service compétent : …";
    private static final String PV_OUVERTURE = "PV d'ouverture des plis non valide";
    private static final String NON_QUALIFIE = "Candidat non qualifié";
    private static final String PIECES_DGC = "Pièces absentes, non authentifiées ou non signées";
    private static final String PIECES_DGC_TEXTE = "Un ou plusieurs documents sont absents, non authentifiés ou non signés : …";

    static final List<Graine> GRAINES = List.of(
            // ---------------------------------------------------------------- DAOO (ch. 2-II-A, B) — hérités par DAOOI, DAOOPREQUAL, DAOR, DAORI
            g("DMC", "DAOO", R, "Objet incohérent entre l'AGPM et le DAO",
                    "L'objet porté dans l'AGPM est incohérent avec celui du DAO : mettre à jour le PPM / AGPM, ou modifier le DAO."),
            new Graine("DMC", "DAOO", R, "Confusion entre quantités fixes et marché à commandes",
                    "Incohérence dans la rédaction du DAO : confusion entre marché à commandes et marché à quantités fixes (…).", FS),
            new Graine("DMC", "DAOO", R, "Confusion entre prix unitaires et prix global forfaitaire",
                    "Incohérence dans la rédaction du DAO : confusion entre marché à prix unitaires et marché à prix global et forfaitaire (…).", TRAVAUX),
            g("DMC", "DAOO", R, "Partie du DAO absente",
                    "Une ou plusieurs parties des documents composant le DAO sont absentes : …"),
            new Graine("DMC", "DAOO", R, "Spécifications techniques orientées ou incomplètes",
                    "Les spécifications techniques sont orientées ou incomplètes : …", FS),
            new Graine("DMC", "DAOO", R, "Spécifications techniques incomplètes ou incohérentes",
                    "Les spécifications techniques sont incomplètes ou incohérentes : …", TRAVAUX),
            // ---------------------------------------------------------------- DC (ch. 2-II-F)
            g("DMC", "DC", R, "Délai de remise des manifestations d'intérêt non respecté",
                    "Le délai de remise des manifestations d'intérêt fixé par les textes n'a pas été respecté : relancer l'AMI."),
            g("DMC", "DC", R, "Critères d'évaluation de l'AMI non neutres",
                    "Les critères d'évaluation de l'AMI doivent être neutres et objectifs, portant principalement sur l'expérience générale et spécifique des candidats : …"),
            g("DMC", "DC", R, "Objet incohérent entre l'AGPM et le Dossier de Consultation",
                    "L'objet porté dans l'AGPM est incohérent avec celui du Dossier de Consultation : mettre à jour le PPM / AGPM, ou modifier le Dossier de Consultation."),
            g("DMC", "DC", R, "Documents d'évaluation de l'AMI incohérents",
                    "Les documents d'évaluation de l'AMI sont incohérents : …"),
            g("DMC", "DC", R, "Mode de sélection ou rémunération incohérents avec les TDR",
                    "La rédaction du dossier est incohérente avec les TDR, notamment le mode de sélection et le type de rémunération : …"),
            g("DMC", "DC", R, "TDR incomplets ou incohérents",
                    "Les TDR sont incomplets ou incohérents avec le reste du Dossier de Consultation : …"),
            // ---------------------------------------------------------------- RJ (ch. 2-II-G, rapport justificatif)
            g("DMC", "RJ", R, "Justifications insuffisantes",
                    "Les justifications apportées par le service sont insuffisantes : joindre les documents nécessaires (…)."),
            g("DMC", "RJ", R, "Document constitutif absent",
                    "Un ou des documents constitutifs du dossier sont absents (PPM signé par la PRMP, projet de décision…) : …"),
            // ---------------------------------------------------------------- MGG (ch. 2-II-G, projet de marché)
            g("DDM", "MGG", R, "Reprendre la validation du choix ou du montant",
                    "Les justifications apportées sont insuffisantes ou manquent de pertinence : reprendre le processus de validation du choix du titulaire et/ou du montant du marché (…)."),
            g("DDM", "MGG", R, "Prix exorbitants : négociation",
                    "Certains prix proposés par l'attributaire paraissent exorbitants (…) : procéder à une négociation, consignée dans un procès-verbal."),
            g("DDM", "MGG", R, JOINDRE, JOINDRE_TEXTE),
            // ---------------------------------------------------------------- MAOO (ch. 2-III-C) — hérités par MAOOI, MAOOPREQUAL, MAOR, MAORI
            g("DDM", "MAOO", D, "Délai de remise des offres insuffisant",
                    "Le délai de remise des offres, décompté de la 1re publication de l'avis d'appel d'offres dans les quotidiens ou sur le site de l'ARMP, est insuffisant."),
            g("DDM", "MAOO", D, "Date et heure d'ouverture des plis non respectées",
                    "Les date et heure d'ouverture des plis prévues n'ont pas été respectées."),
            g("DDM", "MAOO", D, PV_OUVERTURE,
                    "Le PV d'ouverture des plis n'est pas valide : quorum de la CAO non atteint / signataires non habilités."),
            g("DDM", "MAOO", D, "Acte d'engagement non signé",
                    "L'acte d'engagement du candidat attributaire n'est pas signé."),
            g("DDM", "MAOO", D, "Offre partielle",
                    "L'offre est partielle : prix du bordereau des prix ou du DQE non complétés / bordereau des prix ou DQE modifiés (…)."),
            g("DDM", "MAOO", D, "Validité des offres insuffisante ou non précisée",
                    "La durée de validité de l'offre est insuffisante ou non précisée."),
            g("DDM", "MAOO", D, "Délai de livraison ou d'exécution excessif",
                    "Le délai de livraison / d'exécution proposé est supérieur à celui demandé."),
            g("DDM", "MAOO", D, "Documents essentiels absents ou non valides",
                    "Des documents essentiels requis sont absents ou non valides (signataire non habilité, authenticité non vérifiée) : attestation de fabricant, catalogue, certificat de visite des lieux… (…)."),
            g("DDM", "MAOO", D, "Spécifications techniques non conformes",
                    "Les spécifications techniques proposées présentent une réserve, une omission ou une divergence substantielle par rapport à celles demandées : …"),
            g("DDM", "MAOO", D, "Offre de l'attributaire inacceptable",
                    "L'offre du candidat attributaire est inacceptable au sens de l'article 1 du Code des marchés publics : …"),
            g("DDM", "MAOO", D, NON_QUALIFIE,
                    "Le candidat ne remplit pas les critères de qualification exigés : …"),
            g("DDM", "MAOO", R, "Reprendre l'évaluation des offres",
                    "Reprendre l'évaluation des offres sur les points suivants : …"),
            g("DDM", "MAOO", R, JOINDRE, JOINDRE_TEXTE),
            g("DDM", "MAOO", R, "Prorogation de la validité de l'offre",
                    "Joindre les correspondances prorogeant la validité de l'offre du candidat attributaire avant son expiration."),
            // ---------------------------------------------------------------- MPI (ch. 2-III-E)
            g("DDM", "MPI", D, "Délai de remise des propositions insuffisant",
                    "Le délai de remise des propositions, décompté de la dernière date de réception des lettres d'invitation par les candidats, est insuffisant."),
            g("DDM", "MPI", D, "Date et heure d'ouverture des propositions non respectées",
                    "Les date et heure d'ouverture des propositions prévues n'ont pas été respectées."),
            g("DDM", "MPI", D, PV_OUVERTURE,
                    "Le PV d'ouverture des plis n'est pas valide : quorum de la CAO non atteint / signataires non habilités."),
            g("DDM", "MPI", D, "Proposition financière inacceptable",
                    "La proposition financière du candidat attributaire dépasse largement le montant estimatif du marché (un dépassement de moins de 20 % n'est acceptable qu'avec un document du DAF ou de l'ORDSEC justifiant la disponibilité des fonds)."),
            g("DDM", "MPI", D, NON_QUALIFIE,
                    "Le candidat ne remplit pas les critères de qualification exigés : …"),
            g("DDM", "MPI", D, "Une seule proposition conforme",
                    "Une seule proposition conforme a été remise."),
            g("DDM", "MPI", R, "Reprendre l'évaluation des propositions",
                    "Reprendre l'évaluation des propositions sur les points suivants : …"),
            g("DDM", "MPI", R, JOINDRE, JOINDRE_TEXTE),
            g("DDM", "MPI", R, "Prorogation de la validité de l'offre",
                    "Joindre les correspondances prorogeant la validité de l'offre du candidat attributaire avant son expiration."),
            // ---------------------------------------------------------------- AVN (ch. 2-IV)
            g("DGC", "AVN", D, "Motif de l'avenant non pertinent ou non autorisé",
                    "Le motif de l'avenant n'est pas pertinent (par exemple augmentation de délai ne découlant pas d'une circonstance extérieure et imprévisible) ou n'est pas autorisé (par exemple introduction ou modification d'une clause sur l'avance)."),
            g("DGC", "AVN", D, "Marché initial non valide",
                    "Le marché initial n'est pas valide : il n'a pas été signé ni approuvé par toutes les autorités compétentes."),
            new Graine("DGC", "AVN", D, "Avenant postérieur à la réception définitive",
                    "L'avenant est conclu postérieurement à la signature du procès-verbal prononçant la réception définitive des travaux.", TRAVAUX),
            new Graine("DGC", "AVN", D, "Avenant postérieur à la réception provisoire",
                    "L'avenant est conclu postérieurement à la signature du procès-verbal prononçant la réception provisoire des fournitures ou services.", FS),
            g("DGC", "AVN", D, "Avenant postérieur au solde",
                    "L'avenant est conclu après le règlement du solde du marché."),
            g("DGC", "AVN", R, "Reprendre l'accord des parties",
                    "L'accord des deux parties est absent ou ne reflète pas de manière exhaustive toutes les modifications (délai, bordereau des prix, montant de l'avenant…) : reprendre la négociation avec le titulaire sur les points suivants : …"),
            g("DGC", "AVN", R, "Justifier le délai de présentation",
                    "Le projet d'avenant est présenté au-delà du délai d'exécution ou de livraison : justifier l'écart entre la date de présentation de l'avenant et la date prévue d'exécution de la prestation."),
            g("DGC", "AVN", R, "Élément déclencheur non justifié",
                    "Joindre les documents justifiant la passation de l'avenant, notamment l'élément déclencheur : …"),
            g("DGC", "AVN", R, JOINDRE, JOINDRE_TEXTE),
            // ---------------------------------------------------------------- DSS (ch. 2-V)
            g("DSS", "DSS", D, "Motifs d'intérêt général non documentés",
                    "Les motifs d'intérêt général ne sont pas appuyés par une documentation précise et authentifiée."),
            g("DSS", "DSS", D, "Motifs contraires à l'intérêt général",
                    "Les motifs présentés sont fallacieux et contraires à l'intérêt général : …"),
            g("DSS", "DSS", R, PIECES_DGC, PIECES_DGC_TEXTE),
            // ---------------------------------------------------------------- Actes de gestion (ch. 3) : communs DGC (AVN a sa grille propre)
            g("DGC", null, D, "Motifs non documentés ou non authentifiés",
                    "Les motifs de la décision ne sont pas appuyés par une documentation précise et authentifiée."),
            g("DGC", null, D, "Motifs non justifiés",
                    "Les motifs présentés à l'appui de la décision ne sont pas justifiés : …"),
            g("DGC", null, R, PIECES_DGC, PIECES_DGC_TEXTE),
            g("DGC", "INDEMN", R, "Calcul ou acceptation de l'indemnité",
                    "Justifier les modalités de calcul du montant de l'indemnité et joindre son acceptation formelle par les deux parties."),
            g("DGC", "SURSIS", R, "Nombre de jours non justifié",
                    "Justifier le calcul du nombre de jours de sursis : …"),
            g("DGC", "PENAL", R, "Calcul des pénalités non justifié",
                    "Le calcul du nombre de jours ou du montant des pénalités n'est pas justifié : …"),
            g("DGC", "DR", R, "Information et mise en demeure préalables",
                    "La procédure d'information du prestataire sur la sanction envisagée et de mise en demeure préalable (résiliation aux torts du titulaire) n'a pas été respectée : …"));

    private final MotifTypeRepository motifs;
    private final TypeDossierRepository typeDossiers;
    private final SousTypeDossierRepository sousTypes;

    public MotifsTypesManuelSeeder(MotifTypeRepository motifs, TypeDossierRepository typeDossiers, SousTypeDossierRepository sousTypes) {
        this.motifs = motifs;
        this.typeDossiers = typeDossiers;
        this.sousTypes = sousTypes;
    }

    @Override
    @Transactional
    public void run(String... args) {
        semer();
    }

    /** Sème ce qui manque ; rend le nombre de motifs créés. L'ordre suit le manuel, de 10 en 10. */
    @Transactional
    public int semer() {
        List<MotifType> existants = new ArrayList<>(motifs.findAll());
        int crees = 0;
        int rang = 0;
        for (Graine g : GRAINES) {
            rang += 10;
            if (!typeDossiers.existsById(g.famille()) || g.sousType() != null && !sousTypes.existsById(g.sousType())) {
                continue;
            }
            boolean deja = existants.stream().anyMatch(m -> g.famille().equals(m.getIdTypeDossier())
                    && Objects.equals(g.sousType(), m.getIdSousType()) && g.nature().equals(m.getNature())
                    && g.libelle().equalsIgnoreCase(m.getLibelle().trim()));
            if (deja) {
                continue;
            }
            MotifType m = new MotifType();
            m.setIdTypeDossier(g.famille());
            m.setIdSousType(g.sousType());
            m.setNature(g.nature());
            m.setLibelle(g.libelle());
            m.setTexte(g.texte());
            m.setOrdre(rang);
            m.setCategorie(g.categorie());
            m.setActif(Boolean.TRUE);
            existants.add(motifs.save(m));
            crees++;
        }
        if (crees > 0) {
            log.info("[SEED] motifs-types du manuel créés : {}", crees);
        }
        return crees;
    }
}
