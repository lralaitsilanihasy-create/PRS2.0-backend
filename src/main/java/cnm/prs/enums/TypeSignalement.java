package cnm.prs.enums;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 2) — les <strong>règles</strong> du
 * pré-contrôle, telles qu'elles s'écrivent dans {@code t_anomalie.TYPE_ANOMALIE} et dans
 * {@code t_regle_anomalie.CODE_REGLE}.
 *
 * <p><strong>Ces règles ne sont pas inventées : ce sont les points de vérification du manuel de contrôle
 * a priori de la CNM</strong> (version février 2026, chapitre 2, § I, p. 12 à 17), que le pilote a remis
 * le 2026-09-18. Chaque valeur porte la phrase du manuel ou l'article du code des marchés publics dont
 * elle découle — c'est ce qui rend le signalement <strong>opposable</strong> : « l'article X impose le
 * mode Y au-delà du seuil Z » se défend devant un contrôleur, « le modèle estime que » ne se défend
 * pas.</p>
 *
 * <p>À ne pas confondre avec {@link TypeAnomalie}, qui qualifie les anomalies de <em>transcription</em>
 * d'un import de PPM (objet tronqué, encodage douteux, référentiel inconnu…) : celles-là contrôlent le
 * fichier, celles-ci le métier.</p>
 *
 * <p><strong>Une règle s'active et se désactive sans redéploiement</strong> : le code est la clé d'une
 * ligne de {@code t_regle_anomalie}, dont la colonne {@code ACTIF} commande. C'est la contre-mesure à la
 * fatigue d'alerte (plan, §4, lot 3, 3.e) — une règle écartée dans 80 % des cas est une mauvaise règle,
 * on l'éteint.</p>
 *
 * <p>Les codes tiennent en <strong>30 caractères</strong> ({@code t_regle_anomalie.CODE_REGLE}).</p>
 */
public enum TypeSignalement {

    /**
     * Plusieurs lignes homogènes sur un <strong>même compte</strong> : « Vérifier l'existence de plusieurs
     * prestations identiques d'un même compte : dans ce cas, exiger de les fusionner et éventuellement de
     * les allotir » (manuel, p. 15). L'appréciation distingue la source de financement et la forme du
     * marché (quantités fixes, commandes, contrat-cadre), comme le tableau du manuel le demande.
     *
     * <p>Avertissement par défaut — le manuel en fait une demande, pas un motif de refus. Devient
     * <strong>prioritaire</strong> quand le cumul change la procédure applicable ou fait passer le marché
     * au-dessus du seuil de contrôle a priori : c'est le cas que visent les articles 27 et 28, fractionner
     * « dans le seul but d'échapper aux règles de mise en concurrence ou de se soustraire aux
     * contrôles ».</p>
     */
    FRACTIONNEMENT_COMPTE("Lignes homogènes sur un même compte — à fusionner, éventuellement à allotir",
            GraviteSignalement.A_VERIFIER, PointDeGrille.FRACTIONNEMENT),

    /**
     * Le mode de passation saisi est <strong>en deçà</strong> de ce que le montant appelle : « Vérifier si
     * le mode de passation respecte les dispositions des textes de référence » (manuel, p. 14), au regard
     * des seuils de l'arrêté n° 13 156/2019-MEF (art. 2, 2°).
     *
     * <p>Signalement, jamais refus : les articles 38 et 39 du code des marchés publics prévoient des
     * exceptions, et un mode dérogatoire justifié dans la fiche de présentation est précisément ce qui
     * s'écarte.</p>
     */
    MODE_SOUS_LE_SEUIL("Mode de passation en deçà du seuil applicable au montant",
            GraviteSignalement.A_VERIFIER, PointDeGrille.MODE),

    /**
     * La somme des lots diverge du montant de la ligne. « Pour les marchés allotis, la procédure se
     * détermine sur la totalité des lots » (art. 6 du code des marchés publics) : si la somme des lots ne
     * fait pas le montant du marché, la procédure a pu être choisie sur une base fausse.
     */
    LOTS_SOMME_DIVERGENTE("Somme des lots différente du montant estimatif du marché",
            GraviteSignalement.A_VERIFIER, PointDeGrille.MONTANT),

    /**
     * La <strong>catégorie de seuil</strong> de la ligne n'est pas précisée, et les catégories plausibles
     * de sa nature <strong>ne donnent pas la même réponse</strong> : impossible de dire quel seuil
     * s'applique. Signalé seulement dans ce cas — tant que toutes les lectures concordent, il n'y a rien
     * à demander à la PRMP (fatigue d'alerte, 3.e).
     */
    CATEGORIE_SEUIL_A_PRECISER("Catégorie de seuil à préciser — le seuil applicable en dépend",
            GraviteSignalement.A_VERIFIER, PointDeGrille.MODE),

    /**
     * Délai aménagé justifié, mais l'objet ne porte pas la mention « délai réduit ». Le manuel l'exige
     * (p. 14 et p. 16) : « il est nécessaire de mettre à jour le PPM/AGPM et de préciser dans l'objet
     * "délai réduit" pour que les éventuels candidats puissent être informés sur les différents délais ».
     */
    MENTION_DELAI_REDUIT("Délai aménagé sans la mention « délai réduit » dans l'objet",
            GraviteSignalement.A_VERIFIER, PointDeGrille.DESIGNATION),

    /**
     * Dates prévisionnelles incohérentes : « Vérifier si les dates proposées sont pertinentes et
     * cohérentes avec le mode de passation et les différents délais » (manuel, p. 15). Ce que la règle
     * établit sans risque d'erreur : une fin avant son début, ou une date hors de l'exercice du plan.
     */
    DATES_PREVISION_INCOHERENTES("Dates prévisionnelles incohérentes (fin avant début, ou hors exercice)",
            GraviteSignalement.A_VERIFIER, PointDeGrille.MONTANT),

    // =============================================================================================
    // ⚠️ Étape 6 (2026-09-20) — ce que l'ASSISTANT propose, et qu'aucune règle ne peut établir.
    // Ces trois types portent SOURCE = IA : ce sont des PISTES, à ne jamais présenter comme des faits.
    // Ils ont eux aussi leur ligne dans t_regle_anomalie, donc leur interrupteur : la couche IA est la
    // plus susceptible d'être bruyante, c'est celle qu'on doit pouvoir éteindre la première.
    // =============================================================================================

    /**
     * <strong>Fractionnement déguisé</strong> — même besoin réparti sur des comptes ou des libellés
     * différents : même route nationale, même périmètre irrigué, même bâtiment. La règle du compte ne
     * peut pas le voir (les comptes diffèrent, c'est tout le procédé) ; l'information n'est que dans la
     * désignation, et le manuel le dit explicitement pour les travaux routiers (« une seule opération
     * […] pour une seule Route Nationale ») et hydro-agricoles (« pour un périmètre irrigué »).
     */
    FRACTIONNEMENT_DEGUISE("Même besoin réparti sur plusieurs lignes, comptes ou libellés différents",
            GraviteSignalement.A_VERIFIER, PointDeGrille.FRACTIONNEMENT, SourceSignalement.IA),

    /**
     * <strong>Objet imprécis</strong> — le manuel énumère ce que l'objet doit contenir (p. 14) : type et
     * quantité des fournitures, site et consistance des travaux, domaine d'intervention des prestations
     * intellectuelles, immatriculation du véhicule pour un entretien, numéro et objet des lots et des
     * tranches. Aucune règle ne sait lire cela dans une phrase libre.
     */
    OBJET_IMPRECIS("Objet peu explicite au regard des mentions attendues par le manuel",
            GraviteSignalement.A_VERIFIER, PointDeGrille.DESIGNATION, SourceSignalement.IA),

    /**
     * <strong>Nature incohérente</strong> avec l'objet ou le compte (manuel, p. 14 : « vérifier si la
     * nature des prestations est cohérente avec l'objet », « si l'objet est cohérent avec la nature et le
     * compte PCOP/PCG »). C'est une appréciation de sens, hors de portée d'une règle.
     */
    NATURE_INCOHERENTE("Nature déclarée peu cohérente avec l'objet ou le compte",
            GraviteSignalement.A_VERIFIER, PointDeGrille.DESIGNATION, SourceSignalement.IA);

    /**
     * Point de la grille de contrôle du PPM ({@code tr_points_ctrl}) que le signalement éclaire — le
     * contrôleur le trouve ainsi là où il travaille déjà. Le rapprochement se fait sur le
     * <strong>libellé normalisé</strong>, parce que la grille est un référentiel
     * <strong>administrable</strong> : l'Administrateur peut avoir ajusté un libellé, et la grille de
     * recette porte les siens sans accents. Aucun point trouvé = signalement sans rattachement, ce qui
     * n'empêche rien.
     */
    public enum PointDeGrille {

        /** « Fractionnement illicite » — de portée DOSSIER, semé par le pré-contrôle : la grille n'en avait aucun. */
        FRACTIONNEMENT("Fractionnement illicite"),

        /** « Mode de passation conforme » — point 3 de la grille livrée. */
        MODE("Mode de passation conforme"),

        /** « Cohérence du montant estimatif » — point 2 de la grille livrée. */
        MONTANT("Cohérence du montant estimatif"),

        /** « Conformité de la désignation » — point 1 de la grille livrée. */
        DESIGNATION("Conformité de la désignation");

        private final String libelle;

        PointDeGrille(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    private final String libelle;
    private final GraviteSignalement graviteDefaut;
    private final PointDeGrille pointDeGrille;
    private final SourceSignalement source;

    /** Type de règle : ce qu'une règle établit est un fait ({@link SourceSignalement#REGLE}). */
    TypeSignalement(String libelle, GraviteSignalement graviteDefaut, PointDeGrille pointDeGrille) {
        this(libelle, graviteDefaut, pointDeGrille, SourceSignalement.REGLE);
    }

    TypeSignalement(String libelle, GraviteSignalement graviteDefaut, PointDeGrille pointDeGrille,
            SourceSignalement source) {
        this.libelle = libelle;
        this.graviteDefaut = graviteDefaut;
        this.pointDeGrille = pointDeGrille;
        this.source = source;
    }

    /**
     * D'où vient ce type de signalement : une <strong>règle</strong> (fait opposable, avec sa base
     * légale) ou l'<strong>assistant</strong> (piste). C'est porté par le type, et non laissé au bon
     * vouloir de l'appelant, pour qu'une piste ne puisse jamais être enregistrée comme un fait.
     */
    public SourceSignalement source() {
        return source;
    }

    /** Les types que l'assistant peut proposer — les seuls qu'une analyse IA est autorisée à produire. */
    public static java.util.List<TypeSignalement> typesDeLAssistant() {
        return java.util.Arrays.stream(values()).filter(t -> t.source == SourceSignalement.IA).toList();
    }

    /** Libellé de la règle, tel qu'il est semé dans {@code t_regle_anomalie.LIBELLE} (200 caractères). */
    public String libelle() {
        return libelle;
    }

    /** Gravité que la règle pose par défaut ; une règle peut la relever au cas par cas. */
    public GraviteSignalement graviteDefaut() {
        return graviteDefaut;
    }

    /** Point de la grille de contrôle auquel le signalement se rattache. */
    public PointDeGrille pointDeGrille() {
        return pointDeGrille;
    }
}
