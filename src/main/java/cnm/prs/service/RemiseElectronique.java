package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import cnm.prs.entity.ChampFicheMarche;

/**
 * ⚠️ <strong>La remise électronique des offres</strong> (demande front du 2026-09-27, cahier des charges du pilote du
 * 27/09, ADR-0010) — ce que la fiche DAO sait du mode de remise, <strong>pur</strong> : le mode lu dans le cadrage
 * ({@code modeRemise}, {@code PAPIER} à défaut — une fiche d'avant V50 est papier), les formats de date-heure et d'heure,
 * les niveaux de signature, les <strong>valeurs calculées</strong> posées à l'enregistrement d'un bloc (§B1.4, Q11) et
 * l'<strong>état des paramètres internes</strong> de la procédure (§B4 : règles 6 et 8, complétude).
 *
 * <p><strong>Comment les calculs trouvent leurs entrées.</strong> Comme les règles du bilan : par le <em>rôle</em> que
 * porte le champ ({@code SE_HEURE_LIMITE:DATE} / {@code :HEURE} pour l'échéance de remise, {@code SE_OUVERTURE_PLIS:DELAI}
 * pour le délai d'ouverture, {@code SE_ORIGINAL_GARANTIE:LIEU_REMISE} pour l'adresse de remise) — jamais par un code,
 * pour que les trois catégories y trouvent leurs propres champs. Les <em>cibles</em>, elles, sont nommées par le contrat
 * (§B1.3 : {@code B04-SE-03}, {@code B04-SE-15}, {@code B04-SE-17}, {@code B05-GS-12}, {@code B05-GS-14} si vides ;
 * {@code B04-OP-02} / {@code B04-OP-03} toujours, Q11).</p>
 */
public final class RemiseElectronique {

    /** La clé de cadrage « Comment les offres sont-elles remises ? », reflétée par {@code B04-SE-01}. */
    public static final String CLE_CADRAGE = "modeRemise";
    public static final String PAPIER = "PAPIER";
    public static final String ELECTRONIQUE = "ELECTRONIQUE";
    /** Le reflet du mode dans le référentiel (LISTE de source CADRAGE) : imprimé « Papier » / « Électronique ». */
    public static final String CHAMP_MODE = "B04-SE-01";
    /** La section conditionnelle des formulaires du candidat ({@code {{SI:B04-SE}}} … {@code {{FINSI:B04-SE}}}, §B2.1). */
    public static final String SECTION = "B04-SE";
    /** Préfixe des jetons qui viseraient un paramètre interne ({@code {{INT-SE-03}}}) : refusés au chargement (§B2.2). */
    public static final String PREFIXE_JETON_INTERNE = "INT-";
    /** Un tel jeton dans un fichier de commande, avec son nom. */
    public static final Pattern JETON_INTERNE = Pattern.compile("\\{\\{\\s*(" + PREFIXE_JETON_INTERNE + "[^{}]*)}}");

    /** Les cibles des calculs (§B1.4 et Q11). */
    public static final String OUVERTURE_DEPOTS = "B04-SE-03";
    public static final String LIMITE_ASSISTANCE = "B04-SE-15";
    public static final String PUBLICATION_AVIS = "B04-SE-17";
    public static final String LIEU_ORIGINAL = "B05-GS-12";
    public static final String LIMITE_ORIGINAL = "B05-GS-14";
    public static final String DATE_OUVERTURE_PLIS = "B04-OP-02";
    public static final String HEURE_OUVERTURE_PLIS = "B04-OP-03";
    /** Q11 — calculés à chaque enregistrement du bloc en mode électronique, même si une valeur a été envoyée. */
    public static final Set<String> TOUJOURS_CALCULES = Set.of(DATE_OUVERTURE_PLIS, HEURE_OUVERTURE_PLIS);
    /** Les cibles « si vide » (§B1.4). */
    public static final Set<String> CALCULES_SI_VIDES = Set.of(OUVERTURE_DEPOTS, LIMITE_ASSISTANCE, PUBLICATION_AVIS,
            LIEU_ORIGINAL, LIMITE_ORIGINAL);

    /** Rôles lus par les calculs et les règles (posés par le référentiel sur les champs de chaque catégorie). */
    public static final String ROLE_DATE = "DATE";
    public static final String ROLE_HEURE = "HEURE";
    public static final String ROLE_DELAI = "DELAI";
    public static final String ROLE_LIEU_REMISE = "LIEU_REMISE";
    public static final String ROLE_PUBLICATION = "PUBLICATION";

    /** Stockage d'un {@code DATE_HEURE} : ISO local à la minute, la valeur d'un {@code datetime-local}. */
    public static final DateTimeFormatter ISO_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    /** Impression d'un {@code DATE_HEURE} (§B2.3). */
    public static final DateTimeFormatter AFFICHAGE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Pattern HEURE = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    public static final int URL_LONGUEUR_MAX = 500;

    /** Niveaux de signature électronique, du plus faible au plus fort (règle 4). */
    public static final List<String> NIVEAUX = List.of("Simple", "Avancée", "Qualifiée");

    /** Heures entre la date limite de remise et la date limite des demandes d'assistance ({@code B04-SE-15}, §B1.3). */
    public static final long HEURES_AVANT_ASSISTANCE = 48;

    private RemiseElectronique() {
    }

    // ------------------------------------------------------------------ mode

    /** Le mode est électronique ({@code modeRemise = ELECTRONIQUE}) ; toute autre réponse, ou aucune, vaut papier. */
    public static boolean electronique(Map<String, ?> cadrage) {
        return cadrage != null && ELECTRONIQUE.equalsIgnoreCase(String.valueOf(cadrage.get(CLE_CADRAGE)));
    }

    /** « Papier » / « Électronique » pour les codes du cadrage ; toute autre valeur telle quelle. */
    public static String libelleMode(String code) {
        if (code == null) {
            return null;
        }
        return PAPIER.equalsIgnoreCase(code.trim()) ? "Papier"
                : ELECTRONIQUE.equalsIgnoreCase(code.trim()) ? "Électronique" : code;
    }

    // ------------------------------------------------------------------ formats

    /** Une date-heure ISO locale ({@code AAAA-MM-JJTHH:MM}, secondes tolérées) ; {@code null} si absente ou illisible. */
    public static LocalDateTime dateHeure(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(v.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * ⚠️ 2026-09-28 (contrat-cadre, §B1) — la <strong>lecture</strong> d'une valeur d'un champ {@code DATE_HEURE} : une
     * date-heure, ou une date seule ({@code AAAA-MM-JJ}, saisie quand le champ était encore {@code DATE}) lue comme
     * {@code AAAA-MM-JJT00:00}. L'écriture, elle, reste stricte ({@link #dateHeure}) : 400 si la date seule est renvoyée.
     */
    public static LocalDateTime dateHeureLue(String v) {
        LocalDateTime d = dateHeure(v);
        if (d != null || v == null || v.isBlank()) {
            return d;
        }
        LocalDate jour = ControlesFicheMarche.date(v);
        return jour == null ? null : jour.atStartOfDay();
    }

    /** La valeur enregistrée d'un {@code DATE_HEURE}. */
    public static String isoMinute(LocalDateTime d) {
        return d == null ? null : d.format(ISO_MINUTE);
    }

    /** Une heure {@code HH:MM} (24 h) ; {@code null} si absente ou d'une autre forme. */
    public static String heure(String v) {
        if (v == null) {
            return null;
        }
        String h = v.trim();
        return HEURE.matcher(h).matches() ? h : null;
    }

    /** Une adresse absolue {@code http} / {@code https}, 500 caractères au plus. */
    public static boolean urlValide(String v) {
        return v != null && v.length() <= URL_LONGUEUR_MAX && URL.matcher(v.trim()).matches();
    }

    /** L'échéance d'une date ISO et d'une heure {@code HH:MM} ; {@code null} si l'une manque ou ne se lit pas. */
    public static LocalDateTime echeance(String dateIso, String heure) {
        LocalDate d = ControlesFicheMarche.date(dateIso);
        String h = heure(heure);
        return d == null || h == null ? null : d.atTime(Integer.parseInt(h.substring(0, 2)), Integer.parseInt(h.substring(3)));
    }

    /** Rang d'un niveau de signature (0 Simple, 1 Avancée, 2 Qualifiée) ; −1 inconnu. */
    public static int rangNiveau(String niveau) {
        if (niveau == null) {
            return -1;
        }
        for (int i = 0; i < NIVEAUX.size(); i++) {
            if (NIVEAUX.get(i).equalsIgnoreCase(niveau.trim())) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ paramètres administrables et internes

    /**
     * Les paramètres administrables de la remise électronique ({@code t_parametre}, §B1.4) : {@code plateformeUrl}
     * ({@code FICHE_SE_PLATEFORME_URL}), {@code fuseau}, {@code signatureMin}, {@code tailleMaxPlateformeMo},
     * {@code delaiMinRemiseJours}, {@code assistance}, {@code quorumDefaut} (« 3/5 »).
     */
    public record Parametres(String plateformeUrl, String fuseau, String signatureMin, Integer tailleMaxPlateformeMo,
            Integer delaiMinRemiseJours, String assistance, String quorumDefaut) {

        /** Le numérateur de {@code quorumDefaut} (« 3/5 » → 3) ; {@code null} s'il ne se lit pas. */
        public Integer quorumPropose() {
            if (quorumDefaut == null) {
                return null;
            }
            String n = quorumDefaut.trim().split("/", 2)[0].trim();
            try {
                return Integer.valueOf(n);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * Les paramètres internes d'une procédure (§B4) : {@code membres} (INT-SE-01, matricules), {@code quorum}
     * (INT-SE-03), {@code dateCeremonie} (INT-SE-04), {@code responsable} (INT-SE-05, le titulaire du rôle, ou
     * {@code null}). {@code null} tout entier : jamais enregistrés ({@link Etat#ABSENTS}).
     */
    public record Internes(List<String> membres, Integer quorum, LocalDateTime dateCeremonie, String responsable) {
        public Internes {
            membres = membres == null ? List.of() : List.copyOf(membres);
        }
    }

    /** L'état des paramètres internes, servi sur la fiche à tous ceux qui la lisent (§B5.1). */
    public enum Etat {
        COMPLETS, INCOMPLETS, ABSENTS
    }

    public record Anomalie(String regle, String message) {
    }

    /** Message de la règle 6 (§B3), tel quel. */
    public static final String MESSAGE_QUORUM = "Le quorum de déchiffrement doit être compris entre 2 et le nombre de membres, "
            + "et le responsable ne peut pas détenir une part de clé.";
    /** Message de la règle 8 (§B3), tel quel. */
    public static final String MESSAGE_CEREMONIE = "La cérémonie des clés doit précéder la publication de l'avis.";

    /** La règle 6 est-elle violée ? (quorum hors de [2, nombre de membres], ou le responsable détient une part). */
    public static boolean quorumInvalide(Internes i) {
        if (i == null) {
            return false;
        }
        boolean quorumHors = i.quorum() != null && (i.quorum() < 2 || i.quorum() > i.membres().size());
        boolean responsableMembre = i.responsable() != null && i.membres().stream().anyMatch(m -> m.equalsIgnoreCase(i.responsable()));
        return quorumHors || responsableMembre;
    }

    /** La règle 8 est-elle violée ? (cérémonie non antérieure à la publication ; non évaluée si l'une manque). */
    public static boolean ceremonieInvalide(Internes i, LocalDateTime publication) {
        return i != null && i.dateCeremonie() != null && publication != null && !i.dateCeremonie().isBefore(publication);
    }

    /**
     * Les anomalies de l'écran des paramètres internes : ce qui manque (au moins deux membres, le quorum, la date), la
     * règle 6 et la règle 8. Vide pour des paramètres complets et valides.
     */
    public static List<Anomalie> anomalies(Internes i, LocalDateTime publication) {
        List<Anomalie> out = new ArrayList<>();
        if (i == null) {
            out.add(new Anomalie(ControlesFicheMarche.PARAMETRES_INTERNES_INCOMPLETS,
                    "Les paramètres internes de la procédure n'ont pas encore été enregistrés."));
            return out;
        }
        if (i.membres().size() < 2) {
            out.add(new Anomalie(ControlesFicheMarche.PARAMETRES_INTERNES_INCOMPLETS,
                    "Au moins deux membres détenteurs d'une part de clé sont attendus."));
        }
        if (i.quorum() == null) {
            out.add(new Anomalie(ControlesFicheMarche.PARAMETRES_INTERNES_INCOMPLETS, "Le quorum de déchiffrement est à renseigner."));
        }
        if (i.dateCeremonie() == null) {
            out.add(new Anomalie(ControlesFicheMarche.PARAMETRES_INTERNES_INCOMPLETS, "La date de la cérémonie des clés est à renseigner."));
        }
        if (quorumInvalide(i)) {
            out.add(new Anomalie(ControlesFicheMarche.SE_QUORUM, MESSAGE_QUORUM));
        }
        if (ceremonieInvalide(i, publication)) {
            out.add(new Anomalie(ControlesFicheMarche.SE_CEREMONIE, MESSAGE_CEREMONIE));
        }
        return out;
    }

    /** {@code COMPLETS} : membres ≥ 2, quorum et date renseignés, règles 6 et 8 satisfaites ; {@code ABSENTS} sans ligne. */
    public static Etat etat(Internes i, LocalDateTime publication) {
        if (i == null) {
            return Etat.ABSENTS;
        }
        return anomalies(i, publication).isEmpty() ? Etat.COMPLETS : Etat.INCOMPLETS;
    }

    // ------------------------------------------------------------------ valeurs calculées

    /** Les champs d'un rôle d'une règle, parmi les champs ouverts ({@code controle = REGLE:ROLE}, plusieurs par champ). */
    static Map<String, Map<String, ChampFicheMarche>> roles(List<ChampFicheMarche> champsOuverts) {
        Map<String, Map<String, ChampFicheMarche>> roles = new LinkedHashMap<>();
        for (ChampFicheMarche c : champsOuverts) {
            for (String[] rr : ControlesFicheMarche.controles(c)) {
                roles.computeIfAbsent(rr[0], k -> new LinkedHashMap<>()).put(rr[1], c);
            }
        }
        return roles;
    }

    private static String valeurDuRole(Map<String, Map<String, ChampFicheMarche>> roles, String regle, String role,
            Map<String, String> valeurs) {
        Map<String, ChampFicheMarche> r = roles.get(regle);
        ChampFicheMarche c = r == null ? null : r.get(role);
        return c == null ? null : valeurs.get(c.getCode());
    }

    /** L'échéance de remise des offres lue par ses rôles ({@code SE_HEURE_LIMITE:DATE} et {@code :HEURE}). */
    public static LocalDateTime echeanceRemise(Map<String, Map<String, ChampFicheMarche>> roles, Map<String, String> valeurs) {
        return echeance(valeurDuRole(roles, ControlesFicheMarche.SE_HEURE_LIMITE, ROLE_DATE, valeurs),
                valeurDuRole(roles, ControlesFicheMarche.SE_HEURE_LIMITE, ROLE_HEURE, valeurs));
    }

    /**
     * Les valeurs calculées (§B1.4, Q11), par code cible, pour les cibles <strong>ouvertes</strong> dont les entrées se
     * lisent : {@code B04-SE-17} = date prévisionnelle de lancement du plan (à 00:00) ; {@code B04-SE-03} =
     * {@code B04-SE-17} ; {@code B04-SE-15} = échéance − 48 h ; {@code B05-GS-12} = adresse de remise ; {@code B05-GS-14} =
     * échéance ; {@code B04-OP-02} / {@code B04-OP-03} = échéance + délai d'ouverture (minutes). L'appelant décide, cible par
     * cible, si la valeur se pose (vide, ou toujours pour Q11).
     *
     * @param champsOuverts champs ouverts de la fiche (les cibles fermées ne sont pas calculées)
     * @param valeurs       toutes les valeurs de la fiche, celles du bloc enregistré comprises
     * @param datesPpm      dates prévisionnelles du plan ({@link ControlesFicheMarche#PPM_LANCEMENT})
     */
    public static Map<String, String> calculs(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs,
            Map<String, LocalDate> datesPpm) {
        Set<String> ouverts = new java.util.HashSet<>();
        champsOuverts.forEach(c -> ouverts.add(c.getCode()));
        Map<String, Map<String, ChampFicheMarche>> roles = roles(champsOuverts);
        Map<String, String> out = new LinkedHashMap<>();

        // La candidate d'une cible « si vide » se calcule SANS lire la cible elle-même : l'appelant compare à ce qu'il a reçu
        // (vide → posée ; identique → reste « calculée » ; différente → saisie).
        LocalDateTime publicationPlan = datesPpm == null || datesPpm.get(ControlesFicheMarche.PPM_LANCEMENT) == null ? null
                : datesPpm.get(ControlesFicheMarche.PPM_LANCEMENT).atStartOfDay();
        if (ouverts.contains(PUBLICATION_AVIS) && publicationPlan != null) {
            out.put(PUBLICATION_AVIS, isoMinute(publicationPlan));
        }
        LocalDateTime publicationSaisie = dateHeure(valeurs.get(PUBLICATION_AVIS));
        LocalDateTime publication = publicationSaisie != null ? publicationSaisie : publicationPlan;
        if (ouverts.contains(OUVERTURE_DEPOTS) && publication != null) {
            out.put(OUVERTURE_DEPOTS, isoMinute(publication));
        }
        LocalDateTime echeance = echeanceRemise(roles, valeurs);
        if (echeance != null) {
            if (ouverts.contains(LIMITE_ASSISTANCE)) {
                out.put(LIMITE_ASSISTANCE, isoMinute(echeance.minusHours(HEURES_AVANT_ASSISTANCE)));
            }
            if (ouverts.contains(LIMITE_ORIGINAL)) {
                out.put(LIMITE_ORIGINAL, isoMinute(echeance));
            }
            BigDecimal delai = ControlesFicheMarche.nombre(
                    valeurDuRole(roles, ControlesFicheMarche.SE_OUVERTURE_PLIS, ROLE_DELAI, valeurs));
            if (delai != null && delai.signum() >= 0) {
                LocalDateTime ouverture = echeance.plusMinutes(delai.longValue());
                if (ouverts.contains(DATE_OUVERTURE_PLIS)) {
                    out.put(DATE_OUVERTURE_PLIS, ouverture.toLocalDate().toString());
                }
                if (ouverts.contains(HEURE_OUVERTURE_PLIS)) {
                    out.put(HEURE_OUVERTURE_PLIS, ouverture.format(DateTimeFormatter.ofPattern("HH:mm")));
                }
            }
        }
        String lieu = valeurDuRole(roles, ControlesFicheMarche.SE_ORIGINAL_GARANTIE, ROLE_LIEU_REMISE, valeurs);
        if (ouverts.contains(LIEU_ORIGINAL) && lieu != null && !lieu.isBlank()) {
            out.put(LIEU_ORIGINAL, lieu);
        }
        return out;
    }
}
