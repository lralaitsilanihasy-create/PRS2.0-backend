package cnm.prs.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.enums.BaremeSeuil;
import cnm.prs.enums.CategorieSeuil;
import cnm.prs.enums.ProcedureAttendue;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 2) — <strong>tout ce que les règles ont
 * besoin de savoir</strong> sur un plan, chargé une fois.
 *
 * <p>Pourquoi un contexte : un PPM porte des dizaines à des centaines de lignes, et six règles les
 * parcourent. Si chacune relisait les bénéficiaires, les lots, les prévisions, les natures, les modes et
 * le barème, une vérification coûterait des milliers de requêtes. Le service charge, les règles lisent.</p>
 *
 * <p>Le contexte porte aussi les <strong>lectures dérivées</strong> que plusieurs règles partagent — le
 * montant en vigueur d'une ligne, ses comptes budgétaires, les catégories de seuil qu'on peut lui prêter,
 * la procédure que son montant appelle. Elles sont ici, et non recopiées dans chaque règle, parce qu'une
 * divergence entre deux règles sur « le montant d'une ligne » produirait des signalements
 * contradictoires.</p>
 */
public final class ContextePreControle {

    private final Ppm ppm;
    private final String idLocalite;
    private final BaremeSeuil bareme;
    private final SeuilsEnVigueur seuils;
    private final List<Marche> lignes;
    private final Map<Integer, List<ServiceBeneficiaire>> beneficiairesParLigne;
    private final Map<Integer, List<Lot>> lotsParLigne;
    private final Map<Integer, List<MarchePrevision>> previsionsParLigne;
    private final Map<Integer, Nature> natures;
    private final Map<Integer, ModePassation> modes;
    private final Map<Integer, String> libellesProcessus;

    /**
     * @param ppm                    le plan examiné
     * @param idLocalite             organisme de contrôle de l'entité — il choisit le barème
     * @param seuils                 photographie du barème à la date du plan
     * @param lignes                 les lignes <strong>vivantes</strong> du plan (les lignes supprimées
     *                               logiquement en sont exclues : les documents officiels les excluent, et
     *                               un signalement sur une ligne retirée n'a pas de sens)
     * @param beneficiairesParLigne  services bénéficiaires par {@code ID_DETAIL} — ce sont eux qui portent
     *                               les comptes budgétaires, un par SOA
     * @param lotsParLigne           lots par {@code ID_DETAIL}
     * @param previsionsParLigne     dates prévisionnelles par {@code ID_DETAIL}
     * @param natures                référentiel des natures, par identifiant
     * @param modes                  référentiel des modes de passation, par identifiant
     * @param libellesProcessus      libellé d'un processus CAPM par identifiant, pour nommer une date dans
     *                               un message
     */
    ContextePreControle(Ppm ppm, String idLocalite, SeuilsEnVigueur seuils, List<Marche> lignes,
            Map<Integer, List<ServiceBeneficiaire>> beneficiairesParLigne,
            Map<Integer, List<Lot>> lotsParLigne,
            Map<Integer, List<MarchePrevision>> previsionsParLigne,
            Map<Integer, Nature> natures, Map<Integer, ModePassation> modes,
            Map<Integer, String> libellesProcessus) {
        this.ppm = ppm;
        this.idLocalite = idLocalite;
        this.bareme = BaremeSeuil.pourLocalite(idLocalite);
        this.seuils = seuils;
        this.lignes = List.copyOf(lignes);
        this.beneficiairesParLigne = Map.copyOf(beneficiairesParLigne);
        this.lotsParLigne = Map.copyOf(lotsParLigne);
        this.previsionsParLigne = Map.copyOf(previsionsParLigne);
        this.natures = Map.copyOf(natures);
        this.modes = Map.copyOf(modes);
        this.libellesProcessus = Map.copyOf(libellesProcessus);
    }

    public Ppm ppm() {
        return ppm;
    }

    /** Organisme de contrôle de l'entité (la « localité » de PRS), d'où le barème est déduit. */
    public String idLocalite() {
        return idLocalite;
    }

    public BaremeSeuil bareme() {
        return bareme;
    }

    public SeuilsEnVigueur seuils() {
        return seuils;
    }

    /** Les lignes vivantes du plan. */
    public List<Marche> lignes() {
        return lignes;
    }

    /** Exercice du plan — sert à juger la cohérence des dates prévisionnelles. */
    public Integer exercice() {
        return ppm.getExercice();
    }

    public List<ServiceBeneficiaire> beneficiaires(Marche ligne) {
        return beneficiairesParLigne.getOrDefault(ligne.getIdDetail(), List.of());
    }

    public List<Lot> lots(Marche ligne) {
        return lotsParLigne.getOrDefault(ligne.getIdDetail(), List.of());
    }

    public List<MarchePrevision> previsions(Marche ligne) {
        return previsionsParLigne.getOrDefault(ligne.getIdDetail(), List.of());
    }

    public Optional<Nature> nature(Marche ligne) {
        return ligne.getIdNature() == null ? Optional.empty()
                : Optional.ofNullable(natures.get(ligne.getIdNature()));
    }

    public Optional<ModePassation> mode(Marche ligne) {
        return ligne.getIdMode() == null ? Optional.empty()
                : Optional.ofNullable(modes.get(ligne.getIdMode()));
    }

    /** Libellé d'un processus CAPM, ou « processus n° X » à défaut — pour nommer une date dans un message. */
    public String libelleProcessus(Integer idCapm) {
        String libelle = libellesProcessus.get(idCapm);
        return libelle == null || libelle.isBlank() ? "processus n° " + idCapm : libelle.trim();
    }

    /**
     * <strong>Montant en vigueur</strong> d'une ligne, hors taxes : le nouveau montant estimatif s'il y en
     * a un, sinon le montant initial. C'est la valeur que le plan porte aujourd'hui, donc la seule à
     * comparer à un seuil. {@code null} si la ligne n'a aucun montant.
     */
    public BigDecimal montant(Marche ligne) {
        BigDecimal nouveau = ligne.getNouvMontEstim();
        if (nouveau != null && nouveau.signum() > 0) {
            return nouveau;
        }
        return ligne.getMontEstim();
    }

    /**
     * <strong>Comptes budgétaires</strong> d'une ligne. Une ligne en porte <strong>un ou plusieurs</strong>,
     * un par service bénéficiaire (confirmé par le pilote le 2026-09-18) : c'est {@code
     * t_service_beneficiaire.NUM_COMPTE}, capté par les imports PDF et XLSX. {@code t_marche.NUM_COMPTE}
     * n'est qu'un compte unique hérité, utilisé en dernier recours.
     *
     * <p>Conséquence pour le fractionnement : une ligne à plusieurs comptes appartient à plusieurs
     * groupes, et deux lignes sont candidates à la fusion dès qu'elles <strong>partagent un compte</strong>.</p>
     */
    public List<String> comptes(Marche ligne) {
        LinkedHashSet<String> comptes = new LinkedHashSet<>();
        for (ServiceBeneficiaire b : beneficiaires(ligne)) {
            if (b.getNumCompte() != null && !b.getNumCompte().isBlank()) {
                comptes.add(b.getNumCompte().trim());
            }
        }
        if (comptes.isEmpty() && ligne.getNumCompte() != null && !ligne.getNumCompte().isBlank()) {
            comptes.add(ligne.getNumCompte().trim());
        }
        return new ArrayList<>(comptes);
    }

    /**
     * Catégories de seuil qu'on peut prêter à une ligne : celle qu'elle porte si elle en porte une, sinon
     * les catégories plausibles de sa nature. Jamais vide.
     */
    public List<CategorieSeuil> categoriesPlausibles(Marche ligne) {
        if (ligne.getCategorieSeuil() != null) {
            return List.of(ligne.getCategorieSeuil());
        }
        return CategorieSeuil.plausiblesPourNature(nature(ligne).map(Nature::getLibelle).orElse(null));
    }

    /**
     * Catégorie <strong>certaine</strong> d'une ligne : celle qu'elle porte, ou la seule plausible de sa
     * nature. Vide quand plusieurs catégories restent possibles — c'est alors à la règle de décider si la
     * question se pose (elle ne se pose que si les catégories donnent des réponses différentes).
     */
    public Optional<CategorieSeuil> categorieCertaine(Marche ligne) {
        List<CategorieSeuil> plausibles = categoriesPlausibles(ligne);
        return plausibles.size() == 1 ? Optional.of(plausibles.get(0)) : Optional.empty();
    }

    /** Procédure que le montant appelle pour une catégorie donnée, d'après le barème en vigueur. */
    public Optional<SeuilsEnVigueur.VerdictProcedure> procedureAttendue(CategorieSeuil categorie,
            BigDecimal montant) {
        return seuils.procedureAttendue(categorie, bareme, montant);
    }

    /** Le montant atteint-il le seuil de contrôle a priori de cette catégorie ? */
    public boolean soumisAuControleAPriori(CategorieSeuil categorie, BigDecimal montant) {
        return seuils.soumisAuControleAPriori(categorie, bareme, montant);
    }

    /** Palier de l'arrêté auquel appartient le mode <strong>saisi</strong> d'une ligne, s'il est classé. */
    public Optional<ProcedureAttendue> procedureDuModeSaisi(Marche ligne) {
        return mode(ligne).map(ModePassation::getProcedureSeuil);
    }

    /** Désignation de la ligne, ramenée à une longueur lisible dans un message. */
    public String designation(Marche ligne) {
        String d = ligne.getDesignationMarche();
        if (d == null || d.isBlank()) {
            return "ligne n° " + ligne.getIdDetail();
        }
        String propre = d.trim().replaceAll("\\s+", " ");
        return propre.length() <= 120 ? propre : propre.substring(0, 117) + "…";
    }

    /** Minuscules sans accents — pour comparer un libellé saisi ou importé à un mot attendu. */
    public static String normaliser(String texte) {
        return texte == null ? "" : Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").trim().toLowerCase(Locale.FRENCH);
    }

    /**
     * Montant en ariary, groupé par milliers avec des espaces ordinaires : « 155 000 000 ». Volontairement
     * pas {@code NumberFormat}, dont l'espace insécable étroit du français s'affiche mal selon les polices
     * et se cherche mal dans un journal.
     */
    public static String formaterMontant(BigDecimal montant) {
        if (montant == null) {
            return "montant inconnu";
        }
        String entier = montant.stripTrailingZeros().toBigInteger().toString();
        StringBuilder sb = new StringBuilder();
        int compte = 0;
        for (int i = entier.length() - 1; i >= 0; i--) {
            sb.append(entier.charAt(i));
            if (++compte % 3 == 0 && i > 0 && Character.isDigit(entier.charAt(i - 1))) {
                sb.append(' ');
            }
        }
        return sb.reverse().toString();
    }

    /** Premier jour de l'exercice du plan, ou {@code null} si le plan n'a pas d'exercice. */
    public LocalDate debutExercice() {
        Integer exercice = exercice();
        return exercice == null ? null : LocalDate.of(exercice, 1, 1);
    }
}
