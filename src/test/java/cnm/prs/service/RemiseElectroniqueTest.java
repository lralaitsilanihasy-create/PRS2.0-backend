package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.BilanControlesDto;
import cnm.prs.dto.BilanControlesDto.Controle;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.service.ControlesFicheMarche.RemiseElectroniqueBilan;
import cnm.prs.service.RemiseElectronique.Internes;
import cnm.prs.service.RemiseElectronique.Parametres;

/**
 * ⚠️ V50 (2026-09-27, remise électronique, §B3 et §B6) — les onze règles du bilan, un cas valide et un cas invalide
 * chacune, message exact ; les valeurs calculées (§B1.4, Q11) ; l'état des paramètres internes (§B4) ; plusieurs
 * contrôles par champ. Pur : ni base, ni Spring.
 */
class RemiseElectroniqueTest {

    private static final Map<String, Object> ELECTRONIQUE = Map.of("modeRemise", "ELECTRONIQUE", "garantieSoumission", "OUI");
    private static final Map<String, LocalDate> PPM = Map.of(ControlesFicheMarche.PPM_LANCEMENT, LocalDate.of(2026, 3, 2));
    private static final Parametres PARAMS = new Parametres(null, "Indian/Antananarivo", "Avancée", 500, 30, null, "3/5");
    private static final LocalDateTime CEREMONIE = LocalDateTime.of(2026, 3, 1, 9, 0);

    private static ChampFicheMarche champ(String code, String type, String controle) {
        ChampFicheMarche c = new ChampFicheMarche();
        c.setCode(code);
        c.setCodeRubrique(code.substring(0, 6));
        c.setType(type);
        c.setSource("SAISIE");
        c.setControle(controle);
        c.setActif(true);
        c.setObligatoire(false);
        c.setTypesMarche("QUANTITE_FIXE,A_COMMANDE,CONTRAT_CADRE");
        return c;
    }

    private static List<ChampFicheMarche> champs() {
        return List.of(
                champ("B04-LR-02", "TEXTE_LONG", "SE_ORIGINAL_GARANTIE:LIEU_REMISE"),
                champ("B04-LR-03", "DATE", "DATES_ORDRE:REMISE,SE_HEURE_LIMITE:DATE"),
                champ("B04-LR-04", "TEXTE", "SE_HEURE_LIMITE:HEURE"),
                champ("B04-SE-03", "DATE_HEURE", "SE_OUVERTURE_DEPOTS:DEPOTS"),
                champ("B04-SE-05", "LISTE", "SE_SIGNATURE_MIN:NIVEAU,SE_PRESTATAIRES:NIVEAU"),
                champ("B04-SE-06", "LISTE_MULTIPLE", "SE_PRESTATAIRES:PRESTATAIRES"),
                champ("B04-SE-08", "NOMBRE", "SE_TAILLES:FICHIER"),
                champ("B04-SE-09", "NOMBRE", "SE_TAILLES:OFFRE"),
                champ("B04-SE-15", "DATE_HEURE", null),
                champ("B04-SE-17", "DATE_HEURE", "SE_OUVERTURE_DEPOTS:PUBLICATION,SE_CEREMONIE:PUBLICATION"),
                champ("B05-GS-11", "OUI_NON", "SE_ORIGINAL_GARANTIE:EXIGE"),
                champ("B05-GS-12", "TEXTE_LONG", "SE_ORIGINAL_GARANTIE:LIEU"),
                champ("B05-GS-14", "DATE_HEURE", "SE_ORIGINAL_GARANTIE:LIMITE"),
                champ("B04-OP-12", "NOMBRE", "SE_OUVERTURE_PLIS:DELAI"),
                champ("B04-OP-02", "DATE", "DATES_ORDRE:OUVERTURE,SE_OUVERTURE_PLIS:DATE"),
                champ("B04-OP-03", "TEXTE", "SE_OUVERTURE_PLIS:HEURE"));
    }

    private static Map<String, String> valides() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("B04-LR-02", "Lot II M 85 Bis Antananarivo");
        v.put("B04-LR-03", "2026-04-10");   // un vendredi
        v.put("B04-LR-04", "10:00");
        v.put("B04-SE-03", "2026-03-02T00:00");
        v.put("B04-SE-17", "2026-03-02T00:00");
        v.put("B04-SE-05", "Avancée");
        v.put("B04-SE-06", "Prestataire A");
        v.put("B04-SE-08", "50");
        v.put("B04-SE-09", "500");
        v.put("B05-GS-11", "NON");
        v.put("B04-OP-12", "60");
        v.put("B04-OP-02", "2026-04-10");
        v.put("B04-OP-03", "11:00");
        return v;
    }

    private static Internes internes(Integer quorum, LocalDateTime ceremonie, String responsable, String... membres) {
        return new Internes(List.of(membres), quorum, ceremonie, responsable);
    }

    /** ⚠️ V66 — avec le dépositaire de la part de secours (règle 12). */
    private static Internes complets() {
        return new Internes(List.of("CTRMEM", "CTRCC1"), 2, CEREMONIE, "CTRVER",
                new cnm.prs.dto.CeremonieDto.Depositaire("Rakoto Jean", "ARMP", null, null, "depositaire@secours.mg", null, null));
    }

    private static BilanControlesDto bilan(Map<String, String> valeurs, Internes i, boolean responsable) {
        return ControlesFicheMarche.bilan(champs(), valeurs, ELECTRONIQUE, PPM, 0, null, null,
                new RemiseElectroniqueBilan(true, PARAMS, i, responsable));
    }

    private static List<String> messages(List<Controle> l, String regle) {
        return l.stream().filter(c -> regle.equals(c.regle())).map(Controle::message).toList();
    }

    private static List<String> regles(List<Controle> l) {
        return l.stream().map(Controle::regle).distinct().toList();
    }

    private static final List<String> REGLES_SE = List.of(ControlesFicheMarche.SE_HEURE_LIMITE,
            ControlesFicheMarche.SE_OUVERTURE_DEPOTS, ControlesFicheMarche.SE_TAILLES, ControlesFicheMarche.SE_SIGNATURE_MIN,
            ControlesFicheMarche.SE_ORIGINAL_GARANTIE, ControlesFicheMarche.SE_QUORUM, ControlesFicheMarche.SE_OUVERTURE_PLIS,
            ControlesFicheMarche.SE_CEREMONIE, ControlesFicheMarche.SE_PRESTATAIRES,
            ControlesFicheMarche.PARAMETRES_INTERNES_INCOMPLETS, ControlesFicheMarche.RESPONSABLE_NON_DESIGNE,
            ControlesFicheMarche.SE_DEPOSITAIRE);   // ⚠️ V66 : règle 12

    @Test
    @DisplayName("Tout valide en mode électronique : aucun bloquant, les onze règles en « ok » ; en mode papier, aucune n'est évaluée")
    void toutValideEtPapier() {
        BilanControlesDto b = bilan(valides(), complets(), true);
        assertThat(b.bloquants()).isEmpty();
        assertThat(regles(b.ok())).containsAll(REGLES_SE);

        BilanControlesDto papier = ControlesFicheMarche.bilan(champs(), valides(), Map.of("garantieSoumission", "OUI"), PPM, 0,
                null, null, new RemiseElectroniqueBilan(false, PARAMS, null, false));
        assertThat(regles(papier.ok())).doesNotContainAnyElementsOf(REGLES_SE);
        assertThat(regles(papier.bloquants())).doesNotContainAnyElementsOf(REGLES_SE);
        BilanControlesDto sansContexte = ControlesFicheMarche.bilan(champs(), valides(), ELECTRONIQUE, PPM, 0, null, null, null);
        assertThat(regles(sansContexte.bloquants())).doesNotContainAnyElementsOf(REGLES_SE);
    }

    @Test
    @DisplayName("Règle 1 — SE_HEURE_LIMITE : heure hors HH:MM ou date un samedi → bloquant, message exact")
    void regle1() {
        String attendu = "En remise électronique, la date limite doit porter une heure (HH:MM) et tomber un jour ouvrable.";
        Map<String, String> v = valides();
        v.put("B04-LR-04", "10h");
        assertThat(messages(bilan(v, complets(), true).bloquants(), ControlesFicheMarche.SE_HEURE_LIMITE)).containsExactly(attendu);
        v = valides();
        v.put("B04-LR-03", "2026-04-11");
        v.put("B04-OP-02", "2026-04-11");
        assertThat(messages(bilan(v, complets(), true).bloquants(), ControlesFicheMarche.SE_HEURE_LIMITE)).containsExactly(attendu);
    }

    @Test
    @DisplayName("Règle 2 — SE_OUVERTURE_DEPOTS : dépôts après la date limite, ou publication à moins de 30 jours → bloquant")
    void regle2() {
        String attendu = "L'ouverture des dépôts doit précéder la date limite, et la publication la précéder d'au moins 30 jours.";
        Map<String, String> v = valides();
        v.put("B04-SE-03", "2026-04-10T10:00");
        assertThat(messages(bilan(v, complets(), true).bloquants(), ControlesFicheMarche.SE_OUVERTURE_DEPOTS)).containsExactly(attendu);
        v = valides();
        v.put("B04-SE-17", "2026-04-01T00:00");
        v.put("B04-SE-03", "2026-04-01T00:00");
        assertThat(messages(bilan(v, complets(), true).bloquants(), ControlesFicheMarche.SE_OUVERTURE_DEPOTS)).containsExactly(attendu);
    }

    @Test
    @DisplayName("Règle 3 — SE_TAILLES : fichier > offre, ou offre > plateforme → bloquant")
    void regle3() {
        String attendu = "La taille par fichier doit être inférieure ou égale à la taille par offre, elle-même limitée à 500 Mo par la plateforme.";
        Map<String, String> v = valides();
        v.put("B04-SE-08", "600");
        assertThat(messages(bilan(v, complets(), true).bloquants(), ControlesFicheMarche.SE_TAILLES)).containsExactly(attendu);
        v = valides();
        v.put("B04-SE-09", "800");
        assertThat(messages(bilan(v, complets(), true).bloquants(), ControlesFicheMarche.SE_TAILLES)).containsExactly(attendu);
    }

    @Test
    @DisplayName("Règle 4 — SE_SIGNATURE_MIN : Simple sous un minimum Avancée → bloquant ; Qualifiée → ok")
    void regle4() {
        Map<String, String> v = valides();
        v.put("B04-SE-05", "Simple");
        assertThat(messages(bilan(v, complets(), true).bloquants(), ControlesFicheMarche.SE_SIGNATURE_MIN)).containsExactly(
                "Le niveau de signature exigé ne peut pas être inférieur au niveau minimal fixé par l'administrateur (Avancée).");
        v.put("B04-SE-05", "Qualifiée");
        assertThat(messages(bilan(v, complets(), true).bloquants(), ControlesFicheMarche.SE_SIGNATURE_MIN)).isEmpty();
    }

    @Test
    @DisplayName("Règle 5 — SE_ORIGINAL_GARANTIE : original exigé sans lieu ni date limite → bloquant ; renseignés → ok")
    void regle5() {
        Map<String, String> v = valides();
        v.put("B05-GS-11", "OUI");
        assertThat(messages(bilan(v, complets(), true).bloquants(), ControlesFicheMarche.SE_ORIGINAL_GARANTIE))
                .containsExactly("L'original papier étant exigé, indiquez le lieu et la date limite de son dépôt.");
        v.put("B05-GS-12", "Bureau 12");
        v.put("B05-GS-14", "2026-04-10T10:00");
        assertThat(regles(bilan(v, complets(), true).ok())).contains(ControlesFicheMarche.SE_ORIGINAL_GARANTIE);
    }

    @Test
    @DisplayName("Règle 6 — SE_QUORUM : quorum hors de [2, membres] ou responsable détenteur d'une part → bloquant")
    void regle6() {
        assertThat(messages(bilan(valides(), internes(1, CEREMONIE, "CTRVER", "CTRMEM", "CTRCC1"), true).bloquants(),
                ControlesFicheMarche.SE_QUORUM)).containsExactly(RemiseElectronique.MESSAGE_QUORUM);
        assertThat(messages(bilan(valides(), internes(3, CEREMONIE, "CTRVER", "CTRMEM", "CTRCC1"), true).bloquants(),
                ControlesFicheMarche.SE_QUORUM)).containsExactly(RemiseElectronique.MESSAGE_QUORUM);
        assertThat(messages(bilan(valides(), internes(2, CEREMONIE, "CTRMEM", "CTRMEM", "CTRCC1"), true).bloquants(),
                ControlesFicheMarche.SE_QUORUM)).containsExactly(RemiseElectronique.MESSAGE_QUORUM);
    }

    @Test
    @DisplayName("Règle 7 — SE_OUVERTURE_PLIS : ouverture ≠ date limite + 60 minutes → bloquant")
    void regle7() {
        Map<String, String> v = valides();
        v.put("B04-OP-03", "12:00");
        assertThat(messages(bilan(v, complets(), true).bloquants(), ControlesFicheMarche.SE_OUVERTURE_PLIS))
                .containsExactly("La date et l'heure d'ouverture des plis sont calculées : date limite plus 60 minutes.");
    }

    @Test
    @DisplayName("Règle 8 — SE_CEREMONIE : cérémonie après la publication → bloquant")
    void regle8() {
        assertThat(messages(bilan(valides(), internes(2, LocalDateTime.of(2026, 3, 5, 9, 0), "CTRVER", "CTRMEM", "CTRCC1"), true)
                .bloquants(), ControlesFicheMarche.SE_CEREMONIE)).containsExactly(RemiseElectronique.MESSAGE_CEREMONIE);
    }

    @Test
    @DisplayName("Règle 9 — SE_PRESTATAIRES : signature qualifiée sans prestataire → bloquant ; simple sans prestataire → ok")
    void regle9() {
        Map<String, String> v = valides();
        v.remove("B04-SE-06");
        v.put("B04-SE-05", "Qualifiée");
        assertThat(messages(bilan(v, complets(), true).bloquants(), ControlesFicheMarche.SE_PRESTATAIRES))
                .containsExactly("Pour une signature qualifiée ou avancée, indiquez au moins un prestataire de certification accepté.");
        v.put("B04-SE-05", "Simple");
        BilanControlesDto b = bilan(v, complets(), true);
        assertThat(messages(b.bloquants(), ControlesFicheMarche.SE_PRESTATAIRES)).isEmpty();
        assertThat(regles(b.ok())).contains(ControlesFicheMarche.SE_PRESTATAIRES);
    }

    @Test
    @DisplayName("Règle 10 — PARAMETRES_INTERNES_INCOMPLETS : absents ou incomplets → bloquant ; état ABSENTS / INCOMPLETS / COMPLETS")
    void regle10() {
        String attendu = "Les paramètres internes de la procédure sont incomplets : à compléter par le responsable de la procédure.";
        assertThat(messages(bilan(valides(), null, true).bloquants(), ControlesFicheMarche.PARAMETRES_INTERNES_INCOMPLETS))
                .containsExactly(attendu);
        Internes sansQuorum = internes(null, CEREMONIE, "CTRVER", "CTRMEM", "CTRCC1");
        assertThat(messages(bilan(valides(), sansQuorum, true).bloquants(), ControlesFicheMarche.PARAMETRES_INTERNES_INCOMPLETS))
                .containsExactly(attendu);
        assertThat(RemiseElectronique.etat(null, null)).isEqualTo(RemiseElectronique.Etat.ABSENTS);
        assertThat(RemiseElectronique.etat(sansQuorum, null)).isEqualTo(RemiseElectronique.Etat.INCOMPLETS);
        assertThat(RemiseElectronique.etat(complets(), LocalDateTime.of(2026, 3, 2, 0, 0))).isEqualTo(RemiseElectronique.Etat.COMPLETS);
        assertThat(RemiseElectronique.anomalies(internes(1, null, "CTRVER", "CTRMEM"), null)).extracting(RemiseElectronique.Anomalie::message)
                .containsExactly("Au moins deux membres détenteurs d'une part de clé sont attendus.",
                        "La date de la cérémonie des clés est à renseigner.", RemiseElectronique.MESSAGE_DEPOSITAIRE,   // ⚠️ V66
                        RemiseElectronique.MESSAGE_QUORUM);
    }

    @Test
    @DisplayName("Règle 11 — RESPONSABLE_NON_DESIGNE : sans responsable → bloquant")
    void regle11() {
        assertThat(messages(bilan(valides(), complets(), false).bloquants(), ControlesFicheMarche.RESPONSABLE_NON_DESIGNE))
                .containsExactly("Aucun responsable de la procédure n'est désigné : la fiche ne peut pas être validée en remise électronique.");
    }

    @Test
    @DisplayName("Valeurs calculées : publication = lancement du plan, dépôts = publication, assistance = échéance − 48 h, "
            + "original = échéance et adresse de remise, ouverture des plis = échéance + délai")
    void calculs() {
        Map<String, String> v = valides();
        v.remove("B04-SE-03");
        v.remove("B04-SE-17");
        v.remove("B04-OP-02");
        v.remove("B04-OP-03");
        Map<String, String> c = RemiseElectronique.calculs(champs(), v, PPM);
        assertThat(c).containsEntry("B04-SE-17", "2026-03-02T00:00").containsEntry("B04-SE-03", "2026-03-02T00:00")
                .containsEntry("B04-SE-15", "2026-04-08T10:00").containsEntry("B05-GS-14", "2026-04-10T10:00")
                .containsEntry("B05-GS-12", "Lot II M 85 Bis Antananarivo").containsEntry("B04-OP-02", "2026-04-10")
                .containsEntry("B04-OP-03", "11:00");
        // La publication saisie prime le plan pour les dépôts (la candidate du plan reste servie pour la publication elle-même :
        // l'appelant la compare à la saisie) ; sans échéance lisible, rien de dérivé de l'échéance.
        v.put("B04-SE-17", "2026-03-05T08:00");
        v.remove("B04-LR-04");
        c = RemiseElectronique.calculs(champs(), v, PPM);
        assertThat(c).containsEntry("B04-SE-03", "2026-03-05T08:00").containsEntry("B04-SE-17", "2026-03-02T00:00")
                .doesNotContainKeys("B04-SE-15", "B05-GS-14", "B04-OP-02", "B04-OP-03");
        assertThat(RemiseElectronique.calculs(champs(), v, Map.of())).doesNotContainKey("B04-SE-17");
        // Une cible fermée (absente des champs ouverts) n'est pas calculée.
        List<ChampFicheMarche> sansOriginal = new ArrayList<>(champs());
        sansOriginal.removeIf(x -> x.getCode().startsWith("B05-GS-1"));
        assertThat(RemiseElectronique.calculs(sansOriginal, valides(), PPM)).doesNotContainKeys("B05-GS-12", "B05-GS-14");
    }

    @Test
    @DisplayName("Plusieurs contrôles par champ : « DATES_ORDRE:REMISE,SE_HEURE_LIMITE:DATE » nourrit les deux règles")
    void controlesMultiples() {
        assertThat(ControlesFicheMarche.controles(champ("B04-LR-03", "DATE", "DATES_ORDRE:REMISE, se_heure_limite:date")))
                .extracting(r -> r[0] + "/" + r[1]).containsExactly("DATES_ORDRE/REMISE", "SE_HEURE_LIMITE/DATE");
        assertThat(ControlesFicheMarche.controles(champ("B05-GS-03", "MONTANT", "MONTANT_POSITIF")))
                .extracting(r -> r[0] + "/" + r[1]).containsExactly("MONTANT_POSITIF/");
        Map<String, String> v = valides();
        v.put("B04-LR-03", "2026-02-27");   // avant le lancement du plan (02/03) : DATES_ORDRE bloque, la règle 1 reste satisfaite
        v.put("B04-OP-02", "2026-02-27");
        BilanControlesDto b = bilan(v, complets(), true);
        assertThat(regles(b.bloquants())).contains(ControlesFicheMarche.DATES_ORDRE);
        assertThat(regles(b.ok())).contains(ControlesFicheMarche.SE_HEURE_LIMITE);
    }

    @Test
    @DisplayName("Formats : date-heure ISO à la minute, heure HH:MM, adresse http(s), libellé du mode, rang des niveaux")
    void formats() {
        assertThat(RemiseElectronique.dateHeure("2026-04-10T10:00")).isEqualTo(LocalDateTime.of(2026, 4, 10, 10, 0));
        assertThat(RemiseElectronique.dateHeure("2026-04-10 10:00")).isNull();
        assertThat(RemiseElectronique.isoMinute(LocalDateTime.of(2026, 4, 10, 10, 0, 30))).isEqualTo("2026-04-10T10:00");
        assertThat(RemiseElectronique.heure("09:30")).isEqualTo("09:30");
        assertThat(RemiseElectronique.heure("24:00")).isNull();
        assertThat(RemiseElectronique.urlValide("https://depot.cnm.mg/ao/12")).isTrue();
        assertThat(RemiseElectronique.urlValide("ftp://depot.cnm.mg")).isFalse();
        assertThat(RemiseElectronique.urlValide("https://" + "x".repeat(500))).isFalse();
        assertThat(RemiseElectronique.libelleMode("PAPIER")).isEqualTo("Papier");
        assertThat(RemiseElectronique.libelleMode("ELECTRONIQUE")).isEqualTo("Électronique");
        assertThat(RemiseElectronique.rangNiveau("qualifiée")).isEqualTo(2);
        assertThat(RemiseElectronique.rangNiveau("Forte")).isEqualTo(-1);
        assertThat(RemiseElectronique.electronique(Map.of("modeRemise", "electronique"))).isTrue();
        assertThat(RemiseElectronique.electronique(Map.of())).isFalse();
        assertThat(new Parametres(null, null, null, null, null, null, "3/5").quorumPropose()).isEqualTo(3);
    }
}
