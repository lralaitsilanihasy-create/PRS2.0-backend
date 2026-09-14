package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.enums.ChampCible;
import cnm.prs.enums.ChampCible.DocumentCible;
import cnm.prs.enums.PorteePointCtrl;

/**
 * ⚠️ Codes de cellule d'une observation d'examen (V30, 2026-09-14) — test unitaire pur de la liste fermée :
 * elle est la table du contrat avec le front, un code qui glisse casse l'encadrement sans rien dire.
 */
class ChampCibleTest {

    @Test
    @DisplayName("La liste est celle de la demande : 13 codes PPM (dont 4 par bénéficiaire), 13 de fiche, 7 d'AGPM")
    void listeFermee() {
        assertThat(Arrays.stream(ChampCible.values()).map(ChampCible::getCode)).containsExactlyInAnyOrder(
                "nature", "objet", "montEstim", "nouvMontEstim", "mode", "financement", "lancement", "ouverture",
                "attribution", "soa", "compte", "montBenef", "nouvMontBenef",
                "derogatoires.objet", "derogatoires.montEstim", "derogatoires.mode", "derogatoires.justification",
                "delaisAmenages.objet", "delaisAmenages.montEstim", "delaisAmenages.mode",
                "delaisAmenages.delaiRemise", "delaisAmenages.justification",
                "contratsCadres.objet", "contratsCadres.montEstim", "contratsCadres.mode", "contratsCadres.delaiRemise",
                "agpm.compte", "agpm.nature", "agpm.objet", "agpm.montEstim", "agpm.financement", "agpm.mode",
                "agpm.dateDao");
        assertThat(Arrays.stream(ChampCible.values()).filter(ChampCible::parBeneficiaire).map(ChampCible::getCode))
                .containsExactlyInAnyOrder("soa", "compte", "montBenef", "nouvMontBenef");
    }

    @Test
    @DisplayName("Tous les codes tiennent dans CHAMP_CIBLE varchar(40)")
    void longueur() {
        assertThat(Arrays.stream(ChampCible.values()).map(ChampCible::getCode))
                .allMatch(code -> code.length() <= ChampCible.LONGUEUR_MAX);
    }

    @Test
    @DisplayName("Le préfixe dit le document, et documentDuCode le retrouve du seul code")
    void documentDuPrefixe() {
        for (ChampCible champ : ChampCible.values()) {
            String code = champ.getCode();
            DocumentCible attendu = code.startsWith("agpm.") ? DocumentCible.AGPM
                    : code.contains(".") ? DocumentCible.FICHE : DocumentCible.PPM;
            assertThat(champ.document()).as(code).isEqualTo(attendu);
            assertThat(ChampCible.documentDuCode(code)).isEqualTo(attendu);
        }
        assertThat(ChampCible.documentDuCode(null)).isNull();
        assertThat(ChampCible.documentDuCode("bidule")).isNull();
        assertThat(ChampCible.depuisCode("MODE")).as("le code est sensible à la casse").isNull();
    }

    @Test
    @DisplayName("Portée : PPM sur LIGNE et DOSSIER, fiche sur FICHE, AGPM sur AGPM, rien sur SUPPRESSION ni sans portée")
    void admissionParPortee() {
        assertThat(ChampCible.MODE.admisSur(PorteePointCtrl.LIGNE)).isTrue();
        assertThat(ChampCible.MODE.admisSur(PorteePointCtrl.DOSSIER)).isTrue();
        assertThat(ChampCible.MODE.admisSur(PorteePointCtrl.FICHE)).isFalse();
        assertThat(ChampCible.MODE.admisSur(PorteePointCtrl.AGPM)).isFalse();
        assertThat(ChampCible.DEROGATOIRES_MODE.admisSur(PorteePointCtrl.FICHE)).isTrue();
        assertThat(ChampCible.DEROGATOIRES_MODE.admisSur(PorteePointCtrl.LIGNE)).isFalse();
        assertThat(ChampCible.AGPM_DATE_DAO.admisSur(PorteePointCtrl.AGPM)).isTrue();
        assertThat(ChampCible.AGPM_DATE_DAO.admisSur(PorteePointCtrl.FICHE)).isFalse();
        for (ChampCible champ : ChampCible.values()) {
            assertThat(champ.admisSur(PorteePointCtrl.SUPPRESSION)).as(champ.getCode()).isFalse();
            assertThat(champ.admisSur(null)).as(champ.getCode()).isFalse();
        }
    }
}
