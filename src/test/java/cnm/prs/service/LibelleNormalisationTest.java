package cnm.prs.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.entity.ModePassation;

/**
 * Tests unitaires de la résolution de mode <strong>tolérante au suffixe de source de financement</strong>
 * (RPI/PIP) — {@link LibelleNormalisation#resoudreMode} / {@link LibelleNormalisation#separerSource}.
 *
 * <p>Le référentiel est l'image fidèle de {@code tr_mode_passation} en base, y compris ses deux variantes
 * suffixées : idMode=8 « … PIP » (variante PIP de idMode=4) et idMode=6 « … PPP » (PPP <em>n'est pas</em>
 * une source de financement : mode à part entière, conservé).</p>
 */
class LibelleNormalisationTest {

    /** Référentiel des modes, calqué sur la base réelle (déclencheur AGPM sur idMode=1). */
    private static List<ModePassation> referentiel() {
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        return List.of(
                aoo,
                new ModePassation(2, "Appel d'offres restreint", null, null, null, null),
                new ModePassation(3, "Gré à gré", null, null, null, null),
                new ModePassation(4, "Consultation des Prix Ouverte", null, null, null, null),
                new ModePassation(5, "Achat Direct", null, null, null, null),
                new ModePassation(6, "MARCHE DE GRE A GRE PPP", null, null, null, null),
                new ModePassation(7, "MARCHE DE GRE A GRE", null, null, null, null),
                new ModePassation(8, "CONSULTATION DE PRIX OUVERTE PIP", null, null, null, null));
    }

    private static ModePassation resoudre(String libelle) {
        return LibelleNormalisation.resoudreMode(referentiel(), ModePassation::getLibelle, libelle);
    }

    private static void assertMode(int idAttendu, String libelle) {
        ModePassation m = resoudre(libelle);
        assertNotNull(m, () -> "attendu idMode=" + idAttendu + " pour « " + libelle + " »");
        assertEquals(idAttendu, m.getIdMode().intValue(), () -> "mauvais mode pour « " + libelle + " »");
    }

    @Test
    @DisplayName("Suffixe RPI retiré → mode base ; jamais RPI→PIP")
    void suffixeRpiVersBase() {
        assertMode(5, "ACHAT DIRECT RPI");                    // → Achat Direct
        assertMode(4, "CONSULTATION DE PRIX OUVERTE RPI");    // → base (PAS idMode=8 « … PIP »)
        assertMode(7, "MARCHE DE GRE A GRE RPI");             // → base (PPP non concerné)
    }

    @Test
    @DisplayName("APPEL D'OFFRE OUVERT RPI → idMode=1 (AGPM), résolu et non créé à la volée")
    void aooRpiDeclencheAgpm() {
        ModePassation m = resoudre("APPEL D'OFFRE OUVERT RPI");   // coquille singulier + suffixe RPI
        assertNotNull(m);
        assertEquals(1, m.getIdMode().intValue());
        assertTrue(Boolean.TRUE.equals(m.getDeclencheAgpm()), "le drapeau AGPM doit être préservé");
    }

    @Test
    @DisplayName("Source PIP exacte → variante distincte idMode=8")
    void sourcePipExacteVersVariante() {
        assertMode(8, "CONSULTATION DE PRIX OUVERTE PIP");
    }

    @Test
    @DisplayName("Sans suffixe → mode base ; PPP reste un mode distinct")
    void sansSuffixe() {
        assertMode(4, "Consultation des Prix Ouverte");
        assertMode(5, "Achat Direct");
        assertMode(6, "MARCHE DE GRE A GRE PPP");   // PPP conservé (mode à part entière)
        assertMode(7, "MARCHE DE GRE A GRE");
    }

    @Test
    @DisplayName("Aucun noyau correspondant → null (pas de résolution floue)")
    void inconnuRendNull() {
        assertNull(resoudre("Procédure inexistante RPI"));
        assertNull(resoudre("RPI"));        // seulement une source → pas un mode
        assertNull(resoudre(""));
        assertNull(resoudre(null));
    }

    @Test
    @DisplayName("separerSource : noyau normalisé + source (RPI/PIP) ; PPP n'est pas une source")
    void separationSource() {
        assertEquals("ACHATDIRECT", LibelleNormalisation.separerSource("ACHAT DIRECT RPI")[0]);
        assertEquals("RPI", LibelleNormalisation.separerSource("ACHAT DIRECT RPI")[1]);
        assertNull(LibelleNormalisation.separerSource("Achat Direct")[1]);
        assertEquals("PIP", LibelleNormalisation.separerSource("CONSULTATION DE PRIX OUVERTE PIP")[1]);
        assertNull(LibelleNormalisation.separerSource("MARCHE DE GRE A GRE PPP")[1]);   // PPP ≠ source
        assertEquals("MARCHEDEGREAGREPPP", LibelleNormalisation.separerSource("MARCHE DE GRE A GRE PPP")[0]);
    }
}
