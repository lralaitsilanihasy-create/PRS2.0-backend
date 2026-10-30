package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ⚠️ M1 (manuel de contrôle, §B1) — le sous-type des dossiers produits, déduit du mode du plan et de la catégorie. */
class SousTypesDossierTest {

    @Test
    @DisplayName("Le dossier de mise en concurrence et le dossier de marché suivent le mode du plan ; les PI donnent DC et MPI")
    void derivation() {
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("Appel d'offres ouvert", false)).isEqualTo("DAOO");
        assertThat(SousTypesDossier.dossierMiseEnConcurrence(null, false)).isEqualTo("DAOO");
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("Appel d'offres restreint", false)).isEqualTo("DAOR");
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("APPEL D'OFFRE OUVERT INTERNATIONAL", false)).isEqualTo("DAOOI");
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("Appel d'offres restreint international", false)).isEqualTo("DAORI");
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("APPEL D'OFFRE AVEC PRE-QUALIFICATION", false)).isEqualTo("DAOOPREQUAL");
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("Appel d'offres avec pré-qualification", false)).isEqualTo("DAOOPREQUAL");
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("Appel d'offres restreint", true)).isEqualTo("DC");
        // ⚠️ 2026-10-18 (« sous-type-contrat-cadre ») — l'appel d'offres ouvert en contrat-cadre seul ; Q3 : les autres modes gardent le leur.
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("Appel d'offres ouvert", false, true)).isEqualTo("DAOO-CC");
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("Appel d'offres ouvert", false, false)).isEqualTo("DAOO");
        // ⚠️ 2026-10-30 (« contrat-cadre-4b », §B2 ; décret 2019-1064, art. 7) — l'appel d'offres restreint en contrat-cadre : DAOR-CC, MAOR-CC.
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("Appel d'offres restreint", false, true)).isEqualTo("DAOR-CC");
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("APPEL D'OFFRE OUVERT INTERNATIONAL", false, true)).isEqualTo("DAOOI");
        assertThat(SousTypesDossier.dossierMiseEnConcurrence("Consultation", false, true)).isEqualTo("DAOO-CC");   // pas de sous-type propre
        assertThat(SousTypesDossier.PRODUITS_PAR_FICHE).contains("DAOO-CC", "DAOR-CC");
        assertThat(SousTypesDossier.PRODUITS_PAR_PROCEDURE).contains("MAOR-CC");
        assertThat(SousTypesDossier.dossierMarche("Appel d'offres restreint", false, "DAOR-CC")).isEqualTo("MAOR-CC");
        assertThat(SousTypesDossier.formeImposee("DAOR-CC")).isEqualTo("CONTRAT_CADRE");
        assertThat(SousTypesDossier.dossierMarche("Appel d'offres ouvert", false)).isEqualTo("MAOO");
        assertThat(SousTypesDossier.dossierMarche("Appel d'offres restreint", false)).isEqualTo("MAOR");
        assertThat(SousTypesDossier.dossierMarche("APPEL D'OFFRE OUVERT INTERNATIONAL", false)).isEqualTo("MAOOI");
        assertThat(SousTypesDossier.dossierMarche("APPEL D'OFFRE AVEC PRE-QUALIFICATION", false)).isEqualTo("MAOOPREQUAL");
        assertThat(SousTypesDossier.dossierMarche("Appel d'offres ouvert", true)).isEqualTo("MPI");
    }
}
