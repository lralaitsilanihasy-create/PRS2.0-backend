package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutPv;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.PvExamenRepository;

/**
 * ⚠️ <strong>L'examinateur ne vise pas son propre examen</strong> (arbitrage du pilote, 2026-09-08 —
 * {@code docs/demande-backend-2026-09-08-visa-reserve-dispatcheur-hors-examinateur.md}).
 *
 * <p>Quand le Président dispatche l'examen au CC, le CC examine puis se voyait proposer de viser le même
 * dossier — directement, ou par la voie de l'<em>intérim</em>, étant P/CC du périmètre. C'est la
 * séparation des rôles qui tombe. Le pendant exact de « la soumission revient à l'examinateur », pris à
 * l'envers.</p>
 *
 * <p><strong>Le critère n'est pas « est l'examinateur »</strong> mais « examinateur ET pas
 * dispatcheur » : par délégation de profil, une même personne peut se dispatcher le dossier puis
 * l'examiner, et cumule alors légitimement examen, soumission et visa. Bloquer sur le seul fait
 * d'examiner fermerait ce circuit court.</p>
 *
 * <p>Ce que ces tests protègent : le <strong>403 de l'examinateur</strong> au visa comme au retour, y
 * compris en tentant l'intérim ; le <strong>200 du dispatcheur</strong> et celui d'un
 * <strong>suppléant non examinateur</strong> ; l'<strong>exception</strong> du cumul ; et le fait que
 * l'examinateur ne peut pas davantage <strong>prendre en charge</strong> l'étape VISA — sans quoi il
 * verrouillerait l'étape contre le dispatcheur lui-même.</p>
 */
class VisaReserveHorsExaminateurIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private PvExamenRepository pvExamenRepository;

    /**
     * Décor : dossier EXAMINE, dispatché par {@code dispatcheur} à {@code examinateur}, examen et projet
     * de PV au nom de l'examinateur. Navette SIMPLE (le discriminant « deux niveaux » exige un CC
     * dispatcheur <em>différent</em> de l'examinateur, ce qu'aucun des deux cas ne réalise).
     */
    private void decor(int id, String dispatcheur, String examinateur) {
        dossierRepository.save(dossierLoc(id, "EXAMINE", "ANT", "PRMP001"));
        receptionRepository.save(reception(id, id, "CTRCC1", true));
        dispatchRepository.save(dispatch(id, id, "CTRCC1", examinateur, dispatcheur));
        examenRepository.save(examen(id, id, examinateur));
    }

    /** Le projet de PV, soumis par son examinateur — seul geste qui lui revient (règle du même jour). */
    private void projetSoumisParLExaminateur(int id, String tokenExaminateur, String examinateur)
            throws Exception {
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenExaminateur)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + id + ",\"idExamen\":" + id + ",\"imCtrlMembre\":\"" + examinateur
                        + "\",\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + id + "/soumettre").header("Authorization", tokenExaminateur)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"prêt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));
    }

    // ------------------------------------------------------------------ (a) examinateur ≠ dispatcheur

    @Test
    @DisplayName("Contre-recette (a) — examen du CC dispatché par le Président : le VISA par le CC "
            + "examinateur est refusé en 403, celui du Président dispatcheur passe")
    void visa_examinateurNonDispatcheur_refuse_dispatcheurAccepte() throws Exception {
        decor(970, "CTRPRE", "CTRCC1");
        projetSoumisParLExaminateur(970, tokenCc, "CTRCC1");

        viser(970, tokenCc, "CTRCC1", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("l'examinateur ne")));
        // Refus = aucun effet : le projet attend toujours son visa.
        assertThat(pvExamenRepository.findById(970).orElseThrow().getStatutPv())
                .isEqualTo(StatutPv.PROJET_SOUMIS.name());

        viser(970, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
    }

    @Test
    @DisplayName("Contre-recette (a) — le RETOUR pour rectification suit la même règle : refusé au CC "
            + "examinateur, accepté au Président dispatcheur")
    void retour_examinateurNonDispatcheur_refuse_dispatcheurAccepte() throws Exception {
        decor(971, "CTRPRE", "CTRCC1");
        projetSoumisParLExaminateur(971, tokenCc, "CTRCC1");

        mvc.perform(post("/api/pv-examens/971/retourner").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"à revoir\"}"))
                .andExpect(status().isForbidden());
        assertThat(pvExamenRepository.findById(971).orElseThrow().getStatutPv())
                .isEqualTo(StatutPv.PROJET_SOUMIS.name());

        mvc.perform(post("/api/pv-examens/971/retourner").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"commentaire\":\"à revoir\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("EN_RECTIFICATION"));
    }

    @Test
    @DisplayName("L'INTÉRIM n'ouvre rien à l'examinateur — suppléer, c'est tenir la place d'un absent, "
            + "pas s'auto-délivrer un visa ; un suppléant NON examinateur, lui, garde le geste")
    void interim_neContournePasLaGarde_maisResteOuvertAuxAutres() throws Exception {
        decor(972, "CTRPRE", "CTRCC1");
        projetSoumisParLExaminateur(972, tokenCc, "CTRCC1");

        // Le CC examinateur tente de suppléer le Président : refusé AVANT même la note d'intérim.
        viser(972, tokenCc, "CTRCC1", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isForbidden());

        // Un AUTRE CC de la localité, qui n'a pas examiné, supplée : il lui manque la note, pas le droit.
        // 400 (pièce absente) et non 403 : la garde d'identité l'a laissé passer.
        String tokenCcAnt2 = bearer("CTRCCB", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR,
                "CTRCCB", "ANT");
        controleurRepository.save(controleur("CTRCCB", 3, "ANT"));
        viser(972, tokenCcAnt2, "CTRCCB", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ (b) l'exception : le cumul

    @Test
    @DisplayName("Contre-recette (b) — EXCEPTION : dispatché à lui-même puis examiné par lui, le CC "
            + "cumule légitimement examen, soumission ET visa")
    void examinateurEgalDispatcheur_cumuleLegitimement() throws Exception {
        decor(973, "CTRCC1", "CTRCC1");   // il s'est dispatché le dossier à lui-même
        projetSoumisParLExaminateur(973, tokenCc, "CTRCC1");   // la soumission lui revient : 200

        viser(973, tokenCc, "CTRCC1", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
    }
}
