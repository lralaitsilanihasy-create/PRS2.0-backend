package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.SuspensionDossier;
import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.service.ChronometrageService;
import cnm.prs.service.ChronometrageService.DelaiCourant;
import cnm.prs.service.HeuresOuvrees;
import cnm.prs.service.JoursOuvres;

/**
 * ⚠️ <strong>Délai de l'étape en cours</strong> (demande front du 2026-09-14, accueil « À faire », §4) — test
 * unitaire pur de {@code ChronometrageService.delaiCourant} : le calcul est en mémoire, aucun repository n'est
 * touché, le service est donc construit sans eux.
 *
 * <p>Ce que ces tests protègent : l'exemple chiffré de la demande ; les trois bornes d'entrée (fin précédente,
 * dépôt, sortie d'attente — sauf pour la rectification) ; la pause ; et surtout la <strong>non-régression de
 * {@code datePrevisionnelleFin}</strong>, réécrite sur {@code delaiCourant} : un ORACLE, copie littérale de la
 * version d'avant (commit 486f63f), la compare à la nouvelle sur des milliers de dossiers tirés au sort.</p>
 */
class DelaiCourantTest {

    private static final LocalDate JEUDI = LocalDate.of(2026, 9, 10);
    private static final LocalDate VENDREDI = LocalDate.of(2026, 9, 11);
    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 14);

    private final ChronometrageService service = new ChronometrageService(null, null, null, null, null, null, null,
            Clock.system(ZoneId.systemDefault()));

    /** Le référentiel du seed (×8 depuis le 2026-09-02), repli compris pour les étapes hors table. */
    private static Map<EtapeCircuit, Integer> delais() {
        Map<EtapeCircuit, Integer> d = new EnumMap<>(EtapeCircuit.class);
        d.put(EtapeCircuit.RECEPTION, 8);
        d.put(EtapeCircuit.DISPATCH, 8);
        d.put(EtapeCircuit.EXAMEN, 40);
        d.put(EtapeCircuit.VISA, 16);
        d.put(EtapeCircuit.COSIGNATURE, 8);
        d.put(EtapeCircuit.RECTIFICATION_PRMP, 8);
        d.put(EtapeCircuit.VERIFICATION, 24);
        d.put(EtapeCircuit.TRANSMISSION_SIGMP, 8);
        d.put(EtapeCircuit.ARCHIVAGE, 16);
        return d;
    }

    // ------------------------------------------------------------------ l'exemple de la demande

    @Test
    @DisplayName("Exemple de la demande — PRET_DISPATCH entré vendredi 14:00, lundi 15:00 : 9 h écoulées sur 8, "
            + "reste −1, échéance lundi 14:00")
    void exempleDeLaDemande() {
        List<TacheDossier> taches = List.of(passage(1, "RECEPTION", VENDREDI.atTime(14, 0)));
        DelaiCourant d = service.delaiCourant("PRET_DISPATCH", null, taches, List.of(), JEUDI.atTime(10, 5),
                LUNDI.atTime(15, 0), delais());
        assertEquals(EtapeCircuit.DISPATCH, d.etape());
        assertEquals(VENDREDI.atTime(14, 0), d.entree());
        assertEquals(9L, d.ecouleHeures());
        assertEquals(8, d.standardHeures());
        assertEquals(-1L, d.restantHeures());
        assertEquals(LUNDI.atTime(14, 0), d.echeance());
        assertNull(d.pauseDepuis());
        assertNull(d.pauseHeures());
    }

    @Test
    @DisplayName("Visa (PV PROJET_SOUMIS) entré vendredi 12:00, standard 16 h : échéance mardi 12:00, reste 5 h lundi 15:00")
    void visaDeLaDemande() {
        List<TacheDossier> taches = List.of(passage(1, "RECEPTION", JEUDI.atTime(11, 0)),
                passage(2, "DISPATCH", JEUDI.atTime(15, 0)), passage(3, "EXAMEN", VENDREDI.atTime(12, 0)));
        DelaiCourant d = service.delaiCourant("EXAMINE", "PROJET_SOUMIS", taches, List.of(), JEUDI.atTime(9, 0),
                LUNDI.atTime(15, 0), delais());
        assertEquals(EtapeCircuit.VISA, d.etape());
        assertEquals(11L, d.ecouleHeures());
        assertEquals(5L, d.restantHeures());
        assertEquals(LocalDate.of(2026, 9, 15).atTime(12, 0), d.echeance());
    }

    // ------------------------------------------------------------------ les bornes d'entrée

    @Test
    @DisplayName("Sans passage, l'entrée est le DÉPÔT ; sans dépôt ni passage, aucune entrée : écoulé 0, pas d'échéance")
    void entree_depotPuisAucune() {
        DelaiCourant depose = service.delaiCourant("SOUMIS", null, List.of(), List.of(), VENDREDI.atTime(15, 0),
                LUNDI.atTime(9, 0), delais());
        assertEquals(EtapeCircuit.RECEPTION, depose.etape());
        assertEquals(VENDREDI.atTime(15, 0), depose.entree());
        assertEquals(2L, depose.ecouleHeures());
        assertEquals(6L, depose.restantHeures());

        DelaiCourant inconnu = service.delaiCourant("SOUMIS", null, List.of(), List.of(), null, LUNDI.atTime(9, 0),
                delais());
        assertEquals(EtapeCircuit.RECEPTION, inconnu.etape());
        assertNull(inconnu.entree());
        assertEquals(0L, inconnu.ecouleHeures(), "durée nulle plutôt qu'inventée, comme le passage en cours");
        assertEquals(8L, inconnu.restantHeures());
        assertNull(inconnu.echeance());
    }

    @Test
    @DisplayName("La sortie d'attente PRMP fait entrer l'étape qui reprend — pas la rectification, qui EST l'attente")
    void entree_sortieDAttente() {
        List<TacheDossier> taches = List.of(passage(1, "EXAMEN", JEUDI.atTime(9, 0)));
        List<SuspensionDossier> suspensions = List.of(suspension("EN_ATTENTE_PIECES", JEUDI.atTime(9, 0),
                VENDREDI.atTime(15, 0)));
        DelaiCourant reexamen = service.delaiCourant("A_REEXAMINER", null, taches, suspensions, null,
                LUNDI.atTime(9, 0), delais());
        assertEquals(EtapeCircuit.EXAMEN, reexamen.etape());
        assertEquals(VENDREDI.atTime(15, 0), reexamen.entree());
        assertEquals(2L, reexamen.ecouleHeures());

        DelaiCourant rectification = service.delaiCourant("EN_ATTENTE_DECISION_PRMP", "SIGNE", taches, suspensions,
                null, LUNDI.atTime(9, 0), delais());
        assertEquals(EtapeCircuit.RECTIFICATION_PRMP, rectification.etape());
        assertEquals(JEUDI.atTime(9, 0), rectification.entree(), "la reprise n'est pas une borne de la rectification");
    }

    // ------------------------------------------------------------------ la pause

    @Test
    @DisplayName("Pause — statut suspensif : début de la fenêtre OUVERTE et heures ouvrées écoulées ; étape nulle "
            + "hors rectification, étape de reprise lue dans REPRISE_APRES_ATTENTE")
    void pause() {
        List<SuspensionDossier> suspensions = List.of(
                suspension("EN_ATTENTE_PIECES", JEUDI.atTime(8, 0), JEUDI.atTime(10, 0)),
                suspension("EN_ATTENTE_PIECES", VENDREDI.atTime(10, 0), null));
        DelaiCourant pieces = service.delaiCourant("EN_ATTENTE_PIECES", null, List.of(), suspensions, null,
                LUNDI.atTime(15, 0), delais());
        assertNull(pieces.etape());
        assertNull(pieces.entree());
        assertNull(pieces.ecouleHeures());
        assertNull(pieces.standardHeures());
        assertNull(pieces.restantHeures());
        assertNull(pieces.echeance());
        assertEquals(VENDREDI.atTime(10, 0), pieces.pauseDepuis());
        assertEquals(13L, pieces.pauseHeures());   // vendredi 10-16 (6 h) + lundi 8-15 (7 h)
        assertEquals(EtapeCircuit.EXAMEN, ChronometrageService.etapeDeReprise("EN_ATTENTE_PIECES"));
        assertEquals(EtapeCircuit.EXAMEN, ChronometrageService.REPRISE_APRES_ATTENTE.get("EN_ATTENTE_PIECES"));

        DelaiCourant rectification = service.delaiCourant("EN_ATTENTE_DECISION_PRMP", "SIGNE",
                List.of(passage(1, "VERIFICATION", VENDREDI.atTime(10, 0))), suspensions, null,
                LUNDI.atTime(15, 0), delais());
        assertEquals(EtapeCircuit.RECTIFICATION_PRMP, rectification.etape());
        assertEquals(VENDREDI.atTime(10, 0), rectification.pauseDepuis());
        assertEquals(13L, rectification.ecouleHeures());

        DelaiCourant sansFenetre = service.delaiCourant("EN_ATTENTE_COMPLEMENTS_DEPOT", null, List.of(), List.of(),
                null, LUNDI.atTime(15, 0), delais());
        assertNull(sansFenetre.pauseDepuis(), "pas de fenêtre ouverte : pas de début de pause");
        assertNull(sansFenetre.pauseHeures());

        DelaiCourant horsAttente = service.delaiCourant("DISPATCHE", null, List.of(), suspensions, null,
                LUNDI.atTime(15, 0), delais());
        assertNull(horsAttente.pauseDepuis(), "hors statut suspensif, la pause ne se lit pas");
    }

    @Test
    @DisplayName("Hors circuit — brouillon, clôturé : tout est nul")
    void horsCircuit() {
        for (String statut : new String[] { "BROUILLON", "CLOTURE", "RETIRE", "PV_SIGNE" }) {
            DelaiCourant d = service.delaiCourant(statut, null, List.of(), List.of(), null, LUNDI.atTime(9, 0),
                    delais());
            assertNull(d.etape(), statut);
            assertNull(d.ecouleHeures(), statut);
            assertNull(d.pauseDepuis(), statut);
            assertNull(ChronometrageService.etapeDeReprise(statut), statut);
        }
        assertNull(ChronometrageService.etapeDeReprise(null));
    }

    // ------------------------------------------------------------------ non-régression de datePrevisionnelleFin

    @Test
    @DisplayName("⚠️ NON-RÉGRESSION — datePrevisionnelleFin réécrite sur delaiCourant rend la MÊME date que la "
            + "version d'avant sur 20 000 dossiers tirés au sort")
    void datePrevisionnelleFin_identiqueALOracle() {
        String[] statuts = { "BROUILLON", "SOUMIS", "PRET_DISPATCH", "DISPATCHE", "A_REEXAMINER", "EXAMINE",
                "PV_SIGNE", "EN_VERIFICATION", "EN_ATTENTE_DECISION_PRMP", "OBSERVATIONS_LEVEES",
                "DECISION_TRANSMISE_SIGMP", "EN_ATTENTE_PIECES", "EN_ATTENTE_COMPLEMENTS_DEPOT", "CLOTURE", "RETIRE" };
        String[] statutsPv = { null, "BROUILLON", "EN_RECTIFICATION", "PROJET_SOUMIS", "PROJET_ACCEPTE", "SIGNE" };
        EtapeCircuit[] etapes = EtapeCircuit.values();
        Random hasard = new Random(20260914L);
        LocalDateTime origine = LocalDate.of(2026, 8, 3).atStartOfDay();
        for (int i = 0; i < 20_000; i++) {
            LocalDateTime maintenant = origine.plusMinutes(hasard.nextInt(60 * 24 * 42));
            List<TacheDossier> taches = new ArrayList<>();
            int nbPassages = hasard.nextInt(6);
            for (int p = 0; p < nbPassages; p++) {
                // Des fins jusqu'à 3 jours APRÈS maintenant : la borne doit les écarter, dans les deux versions.
                taches.add(passage(p, etapes[hasard.nextInt(etapes.length)].name(),
                        maintenant.minusMinutes(hasard.nextInt(60 * 24 * 30) - 60 * 24 * 3)));
            }
            taches.sort(java.util.Comparator.comparing(TacheDossier::getDateFin));
            List<SuspensionDossier> suspensions = new ArrayList<>();
            int nbAttentes = hasard.nextInt(3);
            for (int s = 0; s < nbAttentes; s++) {
                LocalDateTime debut = maintenant.minusMinutes(hasard.nextInt(60 * 24 * 20));
                suspensions.add(suspension("EN_ATTENTE_PIECES", debut,
                        hasard.nextBoolean() ? null : debut.plusMinutes(hasard.nextInt(60 * 24 * 10))));
            }
            LocalDateTime depot = hasard.nextInt(4) == 0 ? null : maintenant.minusMinutes(hasard.nextInt(60 * 24 * 35));
            Map<EtapeCircuit, Integer> delais = delais();
            if (hasard.nextBoolean()) {
                delais.remove(etapes[hasard.nextInt(etapes.length)]);   // éprouve le repli de 8 h
            }
            String statut = statuts[hasard.nextInt(statuts.length)];
            String statutPv = statutsPv[hasard.nextInt(statutsPv.length)];

            assertEquals(Oracle.datePrevisionnelleFin(service, statut, statutPv, taches, suspensions, depot,
                            maintenant, delais),
                    service.datePrevisionnelleFin(statut, statutPv, taches, suspensions, depot, maintenant, delais),
                    "tirage " + i + " : statut=" + statut + " pv=" + statutPv + " maintenant=" + maintenant);
        }
    }

    /**
     * ORACLE — copie LITTÉRALE de {@code datePrevisionnelleFin} et de ses trois auxiliaires privés tels qu'ils
     * étaient avant la réécriture sur {@code delaiCourant} (commit 486f63f). Ne pas « corriger » : il est la
     * mémoire de l'ancien comportement.
     */
    private static final class Oracle {

        private static final Map<String, EtapeCircuit> REPRISE_APRES_ATTENTE = Map.of(
                "EN_ATTENTE_COMPLEMENTS_DEPOT", EtapeCircuit.RECEPTION,
                "EN_ATTENTE_PIECES", EtapeCircuit.EXAMEN,
                "EN_ATTENTE_DECISION_PRMP", EtapeCircuit.VERIFICATION);

        static LocalDate datePrevisionnelleFin(ChronometrageService service, String statut, String statutPv,
                List<TacheDossier> taches, List<SuspensionDossier> suspensions, LocalDateTime depot,
                LocalDateTime maintenant, Map<EtapeCircuit, Integer> delais) {
            EtapeCircuit courante = service.etapeCourante(statut, statutPv);
            EtapeCircuit reference = courante != null ? courante : REPRISE_APRES_ATTENTE.get(statut);
            if (reference == null) {
                return null;
            }
            LocalDateTime entreeCourante = courante == null ? null
                    : entree(derniereFin(taches), maintenant, depot, reprises(suspensions), courante);
            long totalHeures = 0L;
            for (EtapeCircuit etape : EtapeCircuit.etapesDuCompteur()) {
                if (etape.ordinal() < reference.ordinal()) {
                    continue;
                }
                int standard = delais.getOrDefault(etape, HeuresOuvrees.HEURES_PAR_JOUR);
                if (etape == courante && entreeCourante != null) {
                    totalHeures += Math.max(0L, standard - HeuresOuvrees.ecoulees(entreeCourante, maintenant));
                } else {
                    totalHeures += standard;
                }
            }
            return JoursOuvres.ajouter(maintenant.toLocalDate(), HeuresOuvrees.enJoursArrondiSuperieur(totalHeures));
        }

        private static LocalDateTime entree(LocalDateTime finPrecedente, LocalDateTime borne,
                LocalDateTime depot, List<LocalDateTime> reprises, EtapeCircuit etape) {
            LocalDateTime retenue = candidate(null, finPrecedente, borne);
            retenue = candidate(retenue, depot, borne);
            if (etape != EtapeCircuit.RECTIFICATION_PRMP) {
                for (LocalDateTime reprise : reprises) {
                    retenue = candidate(retenue, reprise, borne);
                }
            }
            return retenue;
        }

        private static LocalDateTime candidate(LocalDateTime meilleure, LocalDateTime candidate, LocalDateTime borne) {
            if (candidate == null || (borne != null && candidate.isAfter(borne))) {
                return meilleure;
            }
            return meilleure == null || candidate.isAfter(meilleure) ? candidate : meilleure;
        }

        private static List<LocalDateTime> reprises(List<SuspensionDossier> suspensions) {
            if (suspensions == null) {
                return List.of();
            }
            return suspensions.stream().map(SuspensionDossier::getFin).filter(java.util.Objects::nonNull).toList();
        }

        private static LocalDateTime derniereFin(List<TacheDossier> taches) {
            return taches == null ? null : taches.stream().map(TacheDossier::getDateFin)
                    .filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        }
    }

    // ------------------------------------------------------------------ fabrique

    private static TacheDossier passage(int id, String etape, LocalDateTime fin) {
        TacheDossier t = new TacheDossier();
        t.setIdTache(id);
        t.setIdDossier(500);
        t.setEtape(etape);
        t.setOccurrence(1);
        t.setDateFin(fin);
        return t;
    }

    private static SuspensionDossier suspension(String statut, LocalDateTime debut, LocalDateTime fin) {
        SuspensionDossier s = new SuspensionDossier();
        s.setIdDossier(500);
        s.setStatut(statut);
        s.setDebut(debut);
        s.setFin(fin);
        return s;
    }
}
