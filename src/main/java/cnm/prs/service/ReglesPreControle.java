package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.SeuilMarche;
import cnm.prs.enums.CategorieModePassation;
import cnm.prs.enums.CategorieSeuil;
import cnm.prs.enums.GraviteSignalement;
import cnm.prs.enums.ProcedureAttendue;
import cnm.prs.enums.TypeSignalement;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 2) — <strong>les règles</strong>, c'est-à-dire
 * les points de vérification du manuel de contrôle a priori de la CNM (février 2026, chapitre 2, § I,
 * p. 12 à 17) mis en code.
 *
 * <p>Elles sont réunies dans un seul fichier parce qu'elles forment <strong>une grille</strong>, celle du
 * manuel : les lire à la suite, c'est lire ce que le contrôleur vérifie. Chacune est une classe imbriquée,
 * indépendante, sans état, et porte en tête le texte dont elle découle.</p>
 *
 * <p><strong>Ce qu'aucune de ces règles ne fait</strong> : bloquer, refuser, déterminer un mode. Elles
 * signalent, et un signalement s'écarte avec un motif. Les exceptions des articles 38 et 39 du code des
 * marchés publics, les modes dérogatoires justifiés dans la fiche de présentation, les cas d'espèce que le
 * texte n'écrit pas : tout cela existe, et c'est exactement pourquoi un signalement n'est pas une
 * sanction.</p>
 *
 * <p><strong>Et ce qu'elles ne font pas non plus</strong> : deviner. Montant absent, mode non classé,
 * catégorie de seuil que le barème ne couvre pas, référentiel muet — la règle s'abstient. Un signalement
 * faux coûte plus cher qu'un signalement manquant : il apprend à la PRMP à tout écarter sans lire, et la
 * fonctionnalité meurt en six semaines (plan, 3.e).</p>
 */
public final class ReglesPreControle {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Nombre de lignes citées dans le texte d'un constat inter-lignes. Au-delà, on compte : un groupe de
     * sept lignes aux libellés voisins produisait un paragraphe illisible, alors que les lignes sont
     * listées juste en dessous, avec leur montant (recette du 2026-09-20).
     */
    private static final int LIGNES_CITEES = 3;

    private ReglesPreControle() {
    }

    /**
     * Les règles livrées, dans l'ordre où le manuel les présente. Le service n'exécute que celles dont la
     * ligne de {@code t_regle_anomalie} est active.
     */
    public static List<ReglePreControle> toutes() {
        return List.of(
                new Fractionnement(),
                new ModeSousLeSeuil(),
                new CategorieSeuilAPreciser(),
                new SommeDesLots(),
                new MentionDelaiReduit(),
                new DatesPrevisionnelles());
    }

    // =============================================================================================
    // 1. Fractionnement illicite — manuel p. 15, articles 27 et 28 du code des marchés publics
    // =============================================================================================

    /**
     * « Vérifier l'existence de plusieurs prestations identiques d'un même compte : dans ce cas, exiger de
     * les fusionner et éventuellement de les allotir » (manuel, p. 15).
     *
     * <p>L'appréciation du manuel repose sur <strong>le compte PCOP/PCG</strong>, en distinguant la
     * <strong>source de financement</strong> et la <strong>forme du marché</strong> (quantités fixes,
     * commandes, contrat-cadre) : deux lignes d'un même compte mais de financements différents ne sont pas
     * un fractionnement, et le tableau du manuel le dit pour chaque nature de prestations.</p>
     *
     * <p>Une ligne porte <strong>un ou plusieurs comptes</strong>, un par service bénéficiaire : elle
     * appartient donc à plusieurs groupes, et deux lignes sont candidates à la fusion dès qu'elles
     * <strong>partagent un compte</strong>. Le montant comparé au seuil est la <strong>valeur du marché
     * fusionné</strong>, c'est-à-dire le total des lignes du groupe.</p>
     *
     * <p><strong>Avertissement par défaut</strong> — le manuel en fait une demande, pas un motif de refus.
     * Le signalement devient <strong>prioritaire</strong> dans les deux seuls cas que visent les articles
     * 27 et 28 : le cumul <strong>change la procédure</strong> applicable, ou il fait passer le marché
     * au-dessus du <strong>seuil de contrôle a priori</strong> qu'aucune ligne n'atteint seule. C'est là
     * que le fractionnement « échappe aux règles de mise en concurrence ou se soustrait aux contrôles ».</p>
     *
     * <p>⚠️ La variante <strong>déguisée</strong> — même besoin réparti sur des comptes ou des libellés
     * différents, même route nationale, même périmètre irrigué — n'est pas de son ressort : l'information
     * n'est que dans la désignation, et c'est à la couche IA de la proposer comme piste (étape 6).</p>
     */
    static final class Fractionnement implements ReglePreControle {

        /** Un groupe homogène au sens du manuel : même compte, même financement, même forme de marché. */
        private record Groupe(String compte, String financement, String forme) {
        }

        @Override
        public TypeSignalement type() {
            return TypeSignalement.FRACTIONNEMENT_COMPTE;
        }

        @Override
        public List<SignalementDetecte> examiner(ContextePreControle ctx) {
            Map<Groupe, List<Marche>> groupes = new LinkedHashMap<>();
            for (Marche ligne : ctx.lignes()) {
                String financement = ContextePreControle.normaliser(ligne.getFinancement());
                String forme = ligne.getFormeMarche().name();
                for (String compte : ctx.comptes(ligne)) {
                    groupes.computeIfAbsent(new Groupe(compte, financement, forme), g -> new ArrayList<>())
                            .add(ligne);
                }
            }

            List<SignalementDetecte> constats = new ArrayList<>();
            for (Map.Entry<Groupe, List<Marche>> entree : groupes.entrySet()) {
                List<Marche> lignes = entree.getValue().stream()
                        .sorted(Comparator.comparing(Marche::getIdDetail)).toList();
                if (lignes.size() < 2) {
                    continue;
                }
                constats.add(constat(ctx, entree.getKey(), lignes));
            }
            return constats;
        }

        private SignalementDetecte constat(ContextePreControle ctx, Groupe groupe, List<Marche> lignes) {
            BigDecimal cumul = lignes.stream().map(ctx::montant).filter(m -> m != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Optional<CategorieSeuil> categorie = categorieCommune(ctx, lignes);
            Optional<String> aggravation = categorie.flatMap(cat -> aggravation(ctx, cat, lignes, cumul));

            StringBuilder texte = new StringBuilder()
                    .append(lignes.size()).append(" lignes du compte ").append(groupe.compte());
            if (!groupe.financement().isBlank()) {
                texte.append(", même source de financement (").append(groupe.financement()).append(')');
            }
            texte.append(", même forme de marché (").append(libelleForme(groupe.forme()))
                    .append("), totalisent ").append(ContextePreControle.formaterMontant(cumul))
                    .append(" Ar HT : ");
            // ⚠️ Trois lignes citées au plus, et le reste compté (recette du 2026-09-20). Sur un groupe de
            // sept lignes aux libellés voisins, la phrase devenait un mur illisible — et les lignes sont
            // de toute façon listées juste en dessous, avec leur montant.
            texte.append(lignes.stream().limit(LIGNES_CITEES)
                    .map(l -> "« " + ctx.designation(l) + " » ("
                            + ContextePreControle.formaterMontant(ctx.montant(l)) + " Ar HT)")
                    .collect(Collectors.joining(", ")));
            if (lignes.size() > LIGNES_CITEES) {
                texte.append(", et ").append(lignes.size() - LIGNES_CITEES)
                        .append(lignes.size() - LIGNES_CITEES == 1 ? " autre ligne" : " autres lignes")
                        .append(" (voir le détail ci-dessous)");
            }
            texte.append(". Le manuel de contrôle a priori (p. 15) demande, pour plusieurs prestations "
                    + "identiques d'un même compte, d'exiger de les fusionner et éventuellement de les "
                    + "allotir (articles 27 et 28 du code des marchés publics).");
            aggravation.ifPresent(texte::append);

            String suggestion = "Au lieu de : " + lignes.size() + " lignes distinctes sur le compte "
                    + groupe.compte() + ".\nLire : un seul marché de "
                    + ContextePreControle.formaterMontant(cumul)
                    + " Ar HT, éventuellement alloti en " + lignes.size() + " lots.";

            List<SignalementDetecte.LigneVisee> visees = lignes.stream()
                    .map(l -> new SignalementDetecte.LigneVisee(l.getIdDetail(), ctx.montant(l))).toList();

            return SignalementDetecte.surPlusieursLignes(type(),
                    aggravation.isPresent() ? GraviteSignalement.PRIORITAIRE : GraviteSignalement.A_VERIFIER,
                    type().name() + "|" + groupe.compte() + "|" + groupe.financement() + "|" + groupe.forme(),
                    visees, texte.toString(), suggestion);
        }

        /** Catégorie de seuil commune aux lignes du groupe, quand elles la donnent toutes et s'accordent. */
        private static Optional<CategorieSeuil> categorieCommune(ContextePreControle ctx, List<Marche> lignes) {
            LinkedHashSet<CategorieSeuil> categories = new LinkedHashSet<>();
            for (Marche ligne : lignes) {
                Optional<CategorieSeuil> certaine = ctx.categorieCertaine(ligne);
                if (certaine.isEmpty()) {
                    return Optional.empty();
                }
                categories.add(certaine.get());
            }
            return categories.size() == 1 ? categories.stream().findFirst() : Optional.empty();
        }

        /**
         * La phrase qui aggrave le constat, s'il y a lieu : le cumul change la procédure, ou il franchit le
         * seuil de contrôle a priori qu'aucune ligne n'atteint seule. Vide sinon — et le constat reste un
         * avertissement.
         */
        private static Optional<String> aggravation(ContextePreControle ctx, CategorieSeuil categorie,
                List<Marche> lignes, BigDecimal cumul) {
            Optional<SeuilsEnVigueur.VerdictProcedure> surCumul = ctx.procedureAttendue(categorie, cumul);
            int niveauMaxSeul = lignes.stream()
                    .map(l -> ctx.procedureAttendue(categorie, ctx.montant(l))
                            .map(v -> v.procedure().niveau()).orElse(0))
                    .max(Integer::compareTo).orElse(0);
            if (surCumul.isPresent() && surCumul.get().procedure().niveau() > niveauMaxSeul) {
                SeuilMarche seuil = surCumul.get().seuilCite();
                return Optional.of(" ⚠️ Le cumul appelle "
                        + avecArticle(surCumul.get().procedure())
                        + (seuil == null ? ""
                                : " à partir de " + ContextePreControle.formaterMontant(seuil.getMontant())
                                        + " Ar HT (" + seuil.getBaseLegale() + ")")
                        + ", qu'aucune de ces lignes n'appelle seule.");
            }
            boolean cumulSousControle = ctx.soumisAuControleAPriori(categorie, cumul);
            boolean uneLigneSousControle = lignes.stream()
                    .anyMatch(l -> ctx.soumisAuControleAPriori(categorie, ctx.montant(l)));
            if (cumulSousControle && !uneLigneSousControle) {
                return ctx.seuils().seuilControleAPriori(categorie, ctx.bareme())
                        .map(s -> " ⚠️ Le cumul atteint le seuil de contrôle a priori ("
                                + ContextePreControle.formaterMontant(s.getMontant()) + " Ar HT — "
                                + s.getBaseLegale() + "), qu'aucune de ces lignes n'atteint seule.");
            }
            return Optional.empty();
        }
    }

    // =============================================================================================
    // 2. Mode de passation conforme aux seuils — manuel p. 14, arrêté n° 13 156/2019-MEF art. 2, 2°
    // =============================================================================================

    /**
     * « Vérifier si le mode de passation respecte les dispositions des textes de référence » (manuel,
     * p. 14) : le mode saisi est-il au moins aussi ouvert à la concurrence que le montant ne l'exige ?
     *
     * <p>La règle ne se déclenche que dans un sens : un mode <strong>moins</strong> ouvert que le seuil
     * ne l'appelle. Passer un appel d'offres ouvert là où une consultation suffirait n'enfreint rien.</p>
     *
     * <p><strong>Elle laisse les modes dérogatoires tranquilles.</strong> Un marché passé en entente
     * directe à 5 milliards est exactement ce que les articles 38 et 39 autorisent sous condition, et sa
     * justification est déjà un point de la fiche de présentation, que le contrôleur examine à part
     * (« Justifications par marché renseignées et recevables »). Le signaler ici ne dirait rien de neuf et
     * remplirait l'écran de la PRMP de constats qu'elle a déjà justifiés.</p>
     *
     * <p>Muette si le mode n'est pas classé au barème ({@code PROCEDURE_SEUIL} nul), si la catégorie de
     * seuil reste incertaine — c'est alors {@link CategorieSeuilAPreciser} qui parle — ou si le
     * référentiel ne porte pas la case.</p>
     */
    static final class ModeSousLeSeuil implements ReglePreControle {

        @Override
        public TypeSignalement type() {
            return TypeSignalement.MODE_SOUS_LE_SEUIL;
        }

        @Override
        public List<SignalementDetecte> examiner(ContextePreControle ctx) {
            List<SignalementDetecte> constats = new ArrayList<>();
            for (Marche ligne : ctx.lignes()) {
                Optional<ModePassation> mode = ctx.mode(ligne);
                if (mode.isEmpty() || mode.get().getProcedureSeuil() == null
                        || mode.get().getCategorie() == CategorieModePassation.DEROGATOIRE) {
                    continue;
                }
                Optional<CategorieSeuil> categorie = ctx.categorieCertaine(ligne);
                BigDecimal montant = ctx.montant(ligne);
                if (categorie.isEmpty() || montant == null || montant.signum() <= 0) {
                    continue;
                }
                Optional<SeuilsEnVigueur.VerdictProcedure> attendue =
                        ctx.procedureAttendue(categorie.get(), montant);
                if (attendue.isEmpty()
                        || mode.get().getProcedureSeuil().niveau() >= attendue.get().procedure().niveau()) {
                    continue;
                }
                constats.add(constat(ctx, ligne, mode.get(), categorie.get(), montant, attendue.get()));
            }
            return constats;
        }

        private SignalementDetecte constat(ContextePreControle ctx, Marche ligne, ModePassation mode,
                CategorieSeuil categorie, BigDecimal montant,
                SeuilsEnVigueur.VerdictProcedure attendue) {
            SeuilMarche seuil = attendue.seuilCite();
            String description = "« " + ctx.designation(ligne) + " » ("
                    + libelle(categorie) + ", " + ContextePreControle.formaterMontant(montant)
                    + " Ar HT) est prévue en « " + mode.getLibelle() + " », alors que ce montant appelle "
                    + avecArticle(attendue.procedure())
                    + (seuil == null ? "" : " à partir de "
                            + ContextePreControle.formaterMontant(seuil.getMontant()) + " Ar HT ("
                            + seuil.getBaseLegale() + ")")
                    + ". Manuel de contrôle a priori, p. 14.";
            String suggestion = "Au lieu de : « " + mode.getLibelle() + " ».\nLire : « "
                    + libelle(attendue.procedure()) + " » — ou la justification du mode dérogatoire dans la "
                    + "fiche de présentation, si le marché relève des articles 38 ou 39 du code des marchés "
                    + "publics.";
            return SignalementDetecte.surLigne(type(), type().graviteDefaut(),
                    type().name() + "|" + ligne.getIdLigneOrigine(), ligne.getIdDetail(),
                    description, suggestion);
        }
    }

    // =============================================================================================
    // 3. Catégorie de seuil à préciser — conséquence de l'arrêté, pas du manuel
    // =============================================================================================

    /**
     * L'arrêté fixe ses seuils par catégorie de prestations ; PRS n'a que trois natures. Quand la ligne ne
     * précise pas sa catégorie, le pré-contrôle essaie les catégories plausibles de sa nature — et
     * <strong>ne demande une précision que si elles ne donnent pas la même réponse</strong>.
     *
     * <p>C'est la contre-mesure à la fatigue d'alerte appliquée à la lettre : sur la grande majorité des
     * lignes, les lectures concordent et la PRMP n'a rien à faire. Elle n'est sollicitée que là où la
     * réponse dépend vraiment de la catégorie — un marché de travaux de 240 millions, par exemple, qui
     * reste en consultation en entretien routier mais exige un appel d'offres ouvert en travaux non
     * routiers.</p>
     */
    static final class CategorieSeuilAPreciser implements ReglePreControle {

        @Override
        public TypeSignalement type() {
            return TypeSignalement.CATEGORIE_SEUIL_A_PRECISER;
        }

        @Override
        public List<SignalementDetecte> examiner(ContextePreControle ctx) {
            List<SignalementDetecte> constats = new ArrayList<>();
            for (Marche ligne : ctx.lignes()) {
                if (ligne.getCategorieSeuil() != null) {
                    continue;
                }
                List<CategorieSeuil> plausibles = ctx.categoriesPlausibles(ligne);
                BigDecimal montant = ctx.montant(ligne);
                if (plausibles.size() < 2 || montant == null || montant.signum() <= 0) {
                    continue;
                }
                Map<CategorieSeuil, String> reponses = new LinkedHashMap<>();
                for (CategorieSeuil categorie : plausibles) {
                    ctx.procedureAttendue(categorie, montant)
                            .ifPresent(v -> reponses.put(categorie, libelle(v.procedure())));
                }
                if (reponses.size() < 2 || new LinkedHashSet<>(reponses.values()).size() < 2) {
                    continue;   // toutes les lectures concordent : rien à demander
                }
                constats.add(constat(ctx, ligne, montant, reponses));
            }
            return constats;
        }

        private SignalementDetecte constat(ContextePreControle ctx, Marche ligne, BigDecimal montant,
                Map<CategorieSeuil, String> reponses) {
            String divergences = reponses.entrySet().stream()
                    .map(e -> libelle(e.getKey()) + " → " + e.getValue())
                    .collect(Collectors.joining(" ; "));
            String description = "La catégorie de seuil de « " + ctx.designation(ligne)
                    + " » n'est pas précisée, et pour " + ContextePreControle.formaterMontant(montant)
                    + " Ar HT la procédure applicable en dépend : " + divergences
                    + ". Précisez la catégorie (arrêté n° 13 156/2019-MEF, art. 2) pour que la conformité "
                    + "du mode puisse être vérifiée.";
            String suggestion = "Au lieu de : catégorie de seuil non précisée.\nLire : "
                    + reponses.keySet().stream().map(c -> "« " + libelle(c) + " »")
                            .collect(Collectors.joining(" ou "))
                    + ", selon l'objet du marché.";
            return SignalementDetecte.surLigne(type(), type().graviteDefaut(),
                    type().name() + "|" + ligne.getIdLigneOrigine(), ligne.getIdDetail(),
                    description, suggestion);
        }
    }

    // =============================================================================================
    // 4. Marchés allotis : la procédure se détermine sur la totalité des lots — article 6 du CMP
    // =============================================================================================

    /**
     * « Pour les marchés allotis, la procédure se détermine sur la totalité des lots » (article 6 du code
     * des marchés publics). Si la somme des lots ne fait pas le montant de la ligne, la procédure a pu
     * être choisie sur une base fausse — dans un sens comme dans l'autre.
     *
     * <p>Muette sur une ligne sans lot, ou dont aucun lot ne porte de montant : il n'y a alors rien à
     * comparer.</p>
     */
    static final class SommeDesLots implements ReglePreControle {

        @Override
        public TypeSignalement type() {
            return TypeSignalement.LOTS_SOMME_DIVERGENTE;
        }

        @Override
        public List<SignalementDetecte> examiner(ContextePreControle ctx) {
            List<SignalementDetecte> constats = new ArrayList<>();
            for (Marche ligne : ctx.lignes()) {
                List<Lot> lots = ctx.lots(ligne);
                BigDecimal montant = ctx.montant(ligne);
                if (lots.isEmpty() || montant == null) {
                    continue;
                }
                BigDecimal totalLots = lots.stream().map(Lot::getMontLot).filter(m -> m != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalLots.signum() <= 0 || totalLots.compareTo(montant) == 0) {
                    continue;
                }
                String description = "Les " + lots.size() + " lots de « " + ctx.designation(ligne)
                        + " » totalisent " + ContextePreControle.formaterMontant(totalLots)
                        + " Ar HT, alors que la ligne porte "
                        + ContextePreControle.formaterMontant(montant)
                        + " Ar HT. Pour les marchés allotis, la procédure se détermine sur la "
                        + "totalité des lots (article 6 du code des marchés publics) : l'écart doit être "
                        + "corrigé pour que le mode puisse être apprécié.";
                String suggestion = "Au lieu de : montant estimatif "
                        + ContextePreControle.formaterMontant(montant) + " Ar HT pour "
                        + lots.size() + " lots à " + ContextePreControle.formaterMontant(totalLots)
                        + " Ar HT.\nLire : un montant estimatif égal au total des lots, ou des montants de "
                        + "lots corrigés.";
                constats.add(SignalementDetecte.surLigne(type(), type().graviteDefaut(),
                        type().name() + "|" + ligne.getIdLigneOrigine(), ligne.getIdDetail(),
                        description, suggestion));
            }
            return constats;
        }
    }

    // =============================================================================================
    // 5. Mentions obligatoires dans l'objet : « délai réduit » — manuel p. 14 et p. 16
    // =============================================================================================

    /**
     * « Si la PRMP souhaite recourir à un aménagement de délai […], il est nécessaire de mettre à jour le
     * PPM/AGPM et de préciser dans l'objet "délai réduit" pour que les éventuels candidats puissent être
     * informés sur les différents délais » (manuel, p. 16 ; la liste des mentions attendues est p. 14).
     *
     * <p>Le constat est entièrement déterministe : la ligne porte une justification de délai aménagé
     * ({@code JUSTIF_DELAI_AMENAGE}, exigée par la fiche de présentation depuis le 2026-09-01), et sa
     * désignation ne contient pas la mention. Aucune autre mention du manuel n'est vérifiable de la même
     * façon : « relance » et « contrôle a priori » ne se déduisent d'aucune donnée du plan — elles
     * resteront l'affaire de l'œil du contrôleur, et éventuellement d'une piste de l'assistant.</p>
     */
    static final class MentionDelaiReduit implements ReglePreControle {

        @Override
        public TypeSignalement type() {
            return TypeSignalement.MENTION_DELAI_REDUIT;
        }

        @Override
        public List<SignalementDetecte> examiner(ContextePreControle ctx) {
            List<SignalementDetecte> constats = new ArrayList<>();
            for (Marche ligne : ctx.lignes()) {
                String justification = ligne.getJustifDelaiAmenage();
                if (justification == null || justification.isBlank()) {
                    continue;
                }
                if (ContextePreControle.normaliser(ligne.getDesignationMarche()).contains("delai reduit")) {
                    continue;
                }
                String description = "« " + ctx.designation(ligne) + " » porte une justification de délai "
                        + "aménagé, mais son objet ne contient pas la mention « délai réduit ». Le manuel "
                        + "de contrôle a priori (p. 16) demande de la préciser dans l'objet, pour que les "
                        + "éventuels candidats soient informés des délais.";
                String suggestion = "Au lieu de : « " + ctx.designation(ligne) + " ».\nLire : « "
                        + ctx.designation(ligne) + " — délai réduit ».";
                constats.add(SignalementDetecte.surLigne(type(), type().graviteDefaut(),
                        type().name() + "|" + ligne.getIdLigneOrigine(), ligne.getIdDetail(),
                        description, suggestion));
            }
            return constats;
        }
    }

    // =============================================================================================
    // 6. Dates prévisionnelles — manuel p. 15
    // =============================================================================================

    /**
     * « Vérifier si les dates proposées sont pertinentes et cohérentes avec le mode de passation et les
     * différents délais » (manuel, p. 15).
     *
     * <p>La règle s'en tient à ce qu'elle peut établir <strong>sans se tromper</strong> : une fin avant son
     * début, et une date <strong>antérieure à l'exercice</strong> du plan. Elle ne signale pas une date
     * postérieure à l'exercice — un marché lancé en décembre s'achève légitimement l'année suivante — et
     * elle ne compare pas encore les durées aux délais minimaux des modes : cela demande de savoir lequel
     * des processus CAPM porte la publicité, ce que le référentiel ne dit pas aujourd'hui. Une règle qui
     * se tromperait sur ce point apprendrait à la PRMP à écarter sans lire.</p>
     *
     * <p>Un seul signalement par ligne, qui liste tous ses problèmes de dates : un par date noierait
     * l'écran.</p>
     */
    static final class DatesPrevisionnelles implements ReglePreControle {

        @Override
        public TypeSignalement type() {
            return TypeSignalement.DATES_PREVISION_INCOHERENTES;
        }

        @Override
        public List<SignalementDetecte> examiner(ContextePreControle ctx) {
            LocalDate debutExercice = ctx.debutExercice();
            List<SignalementDetecte> constats = new ArrayList<>();
            for (Marche ligne : ctx.lignes()) {
                List<String> problemes = new ArrayList<>();
                for (MarchePrevision p : ctx.previsions(ligne)) {
                    String processus = ctx.libelleProcessus(p.getIdCapm());
                    if (p.getDateDebut() != null && p.getDateFin() != null
                            && p.getDateFin().isBefore(p.getDateDebut())) {
                        problemes.add("« " + processus + " » se termine le " + JOUR.format(p.getDateFin())
                                + ", avant son début le " + JOUR.format(p.getDateDebut()));
                    }
                    if (debutExercice != null) {
                        if (p.getDateDebut() != null && p.getDateDebut().isBefore(debutExercice)) {
                            problemes.add("« " + processus + " » commence le "
                                    + JOUR.format(p.getDateDebut()) + ", avant l'exercice "
                                    + ctx.exercice() + " du plan");
                        } else if (p.getDateFin() != null && p.getDateFin().isBefore(debutExercice)) {
                            problemes.add("« " + processus + " » se termine le "
                                    + JOUR.format(p.getDateFin()) + ", avant l'exercice "
                                    + ctx.exercice() + " du plan");
                        }
                    }
                }
                if (problemes.isEmpty()) {
                    continue;
                }
                String description = "Dates prévisionnelles à revoir sur « " + ctx.designation(ligne)
                        + " » : " + String.join(" ; ", problemes)
                        + ". Le manuel de contrôle a priori (p. 15) demande de vérifier que les dates "
                        + "proposées sont pertinentes et cohérentes avec le mode de passation et les "
                        + "différents délais.";
                String suggestion = "Au lieu de : " + problemes.get(0) + ".\nLire : des dates comprises "
                        + "dans l'exercice " + ctx.exercice() + ", chaque fin après son début.";
                constats.add(SignalementDetecte.surLigne(type(), type().graviteDefaut(),
                        type().name() + "|" + ligne.getIdLigneOrigine(), ligne.getIdDetail(),
                        description, suggestion));
            }
            return constats;
        }
    }

    // =============================================================================================
    // Libellés d'affichage — ils vivent ici, avec les phrases qu'ils composent
    // =============================================================================================

    /** « un appel d'offres ouvert », « une consultation… » — pour écrire une phrase correcte. */
    private static String avecArticle(ProcedureAttendue procedure) {
        return switch (procedure) {
            case APPEL_OFFRES_OUVERT -> "un appel d'offres ouvert";
            case CONSULTATION -> "une consultation d'entrepreneurs, de fournisseurs ou de prestataires de services";
            case ACHAT_DIRECT -> "une exécution directe par bon de commande";
        };
    }

    private static String libelle(ProcedureAttendue procedure) {
        return switch (procedure) {
            case APPEL_OFFRES_OUVERT -> "Appel d'offres ouvert";
            case CONSULTATION -> "Consultation";
            case ACHAT_DIRECT -> "Achat direct par bon de commande";
        };
    }

    private static String libelle(CategorieSeuil categorie) {
        return switch (categorie) {
            case ROUTES_CONSTRUCTION -> "construction ou réhabilitation de routes";
            case ENTRETIEN_ROUTIER -> "entretien routier";
            case TRAVAUX_NON_ROUTIERS -> "travaux non routiers";
            case FOURNITURES_SERVICES -> "fournitures et services";
            case PRESTATIONS_INTELLECTUELLES -> "prestations intellectuelles";
        };
    }

    /** Forme du marché, telle que le tableau du manuel la nomme. */
    private static String libelleForme(String forme) {
        return switch (forme) {
            case "QUANTITE_FIXE" -> "quantités fixes";
            case "A_COMMANDE" -> "marché à commandes";
            case "CONTRAT_CADRE" -> "contrat-cadre";
            default -> forme.toLowerCase(java.util.Locale.FRENCH).replace('_', ' ');
        };
    }
}
