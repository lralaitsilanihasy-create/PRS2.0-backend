package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.ActionDossier;
import cnm.prs.entity.DemandeRetrait;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.TacheDossier;
import cnm.prs.enums.CategorieModePassation;
import cnm.prs.enums.EtapeCircuit;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.ActionDossierRepository;
import cnm.prs.repository.ModePassationRepository;
import cnm.prs.repository.TacheDossierRepository;

/**
 * ⚠️ <strong>Recensement des trous backend au 07/09</strong> (front, {@code docs/demande-backend-2026-09-07-recensement-trous.md})
 * — trois corrections serveur :
 * <ol>
 *   <li><strong>T1</strong> — le retrait d'un dossier n'apparaissait pas dans le journal (constat réel
 *       100299) : {@code DEMANDE_RETRAIT} / {@code RETRAIT_ACCEPTE} / {@code RETRAIT_REFUSE} sont dérivés
 *       de {@code t_demande_retrait}, rétroactifs ;</li>
 *   <li><strong>T2</strong> — la transmission SIGMP directe (avis FAV, sans passage de vérification) laissait
 *       l'occurrence VERIFICATION ouverte à jamais ;</li>
 *   <li><strong>T3</strong> — la mise à jour par import PDF échappait à la garde des justifications : le trou
 *       se ferme à la soumission, où tous les chemins convergent.</li>
 * </ol>
 */
class RecensementTrousIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private ActionDossierRepository actionDossierRepository;
    @Autowired
    private TacheDossierRepository tacheRepository;
    @Autowired
    private ModePassationRepository modePassationRepository;

    // ------------------------------------------------------------------ T1 — retrait au journal

    @Test
    @DisplayName("T1 — le retrait se lit au journal : DEMANDE_RETRAIT (PRMP, motif) puis RETRAIT_ACCEPTE (décideur, "
            + "état d'avant -> BROUILLON), intercalés à leurs dates ; une demande REFUSEE porte son observation")
    void retrait_deriveAuJournal() throws Exception {
        // Décor : soumission puis réception consignées (le retrait survient après la réception).
        actionDossierRepository.save(action(1, "SOUMISSION", LocalDateTime.of(2026, 9, 7, 7, 57)));
        actionDossierRepository.save(action(1, "RECEPTION", LocalDateTime.of(2026, 9, 7, 8, 12)));
        DemandeRetrait acceptee = demandeRetrait(0, 1, "PRMP001");
        acceptee.setMotifRetrait("Test");
        acceptee.setDateDemande(LocalDateTime.of(2026, 9, 7, 8, 23, 3));
        acceptee.setStatut("ACCEPTEE");
        acceptee.setImCtrlCc("CTRPRE");
        acceptee.setDateDecision(LocalDateTime.of(2026, 9, 7, 8, 23, 50));
        demandeRetraitRepository.save(acceptee);
        actionDossierRepository.save(action(1, "SOUMISSION", LocalDateTime.of(2026, 9, 7, 8, 32)));
        DemandeRetrait refusee = demandeRetrait(0, 1, "PRMP001");
        refusee.setDateDemande(LocalDateTime.of(2026, 9, 7, 9, 0));
        refusee.setStatut("REFUSEE");
        refusee.setImCtrlCc("CTRCC1");
        refusee.setDateDecision(LocalDateTime.of(2026, 9, 7, 9, 5));
        refusee.setObsDecision("Dossier déjà dispatché");
        demandeRetraitRepository.save(refusee);

        String journal = mvc.perform(get("/api/dossiers/1/journal").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(journal, "$[*].typeAction");
        List<String> details = JsonPath.read(journal, "$[*].detail");
        int reception = types.indexOf("RECEPTION");
        // La demande du décor (socle, juin) précède tout : viser CELLE du test, par son motif.
        int demande = -1;
        for (int i = 0; i < types.size(); i++) {
            if ("DEMANDE_RETRAIT".equals(types.get(i)) && details.get(i) != null && details.get(i).contains("motif : Test")) {
                demande = i;
            }
        }
        int accepte = types.indexOf("RETRAIT_ACCEPTE");
        int resoumission = types.lastIndexOf("SOUMISSION");
        assertThat(demande).isGreaterThan(reception);
        assertThat(accepte).isGreaterThan(demande);
        assertThat(resoumission).isGreaterThan(accepte);

        // La demande est un acte de la PRMP : opérateur = la PRMP (aucun marqueur « opérateur ≠ attributaire »).
        // (Le socle sème déjà une demande EN_ATTENTE sur le dossier 1 : trois demandes au journal.)
        List<String> auteursDemande = JsonPath.read(journal, "$[?(@.typeAction=='DEMANDE_RETRAIT')].auteur");
        assertThat(auteursDemande).hasSize(3).containsOnly("PRMP001");
        List<String> prmpDemande = JsonPath.read(journal, "$[?(@.typeAction=='DEMANDE_RETRAIT')].idPrmpOperateur");
        assertThat(prmpDemande).containsOnly("PRMP001");
        List<String> detailsDemande = JsonPath.read(journal, "$[?(@.typeAction=='DEMANDE_RETRAIT')].detail");
        assertThat(detailsDemande).contains("Demande de retrait — motif : Test");
        // La décision est celle du décideur, avec l'état de départ relu dans le journal.
        List<String> auteursAccepte = JsonPath.read(journal, "$[?(@.typeAction=='RETRAIT_ACCEPTE')].auteur");
        assertThat(auteursAccepte).containsExactly("CTRPRE");
        List<String> detailsAccepte = JsonPath.read(journal, "$[?(@.typeAction=='RETRAIT_ACCEPTE')].detail");
        assertThat(detailsAccepte.get(0)).isEqualTo("Retrait accepté — PRET_DISPATCH -> BROUILLON");
        List<String> datesAccepte = JsonPath.read(journal, "$[?(@.typeAction=='RETRAIT_ACCEPTE')].dateAction");
        assertThat(datesAccepte.get(0)).startsWith("2026-09-07T08:23:50");
        List<String> detailsRefus = JsonPath.read(journal, "$[?(@.typeAction=='RETRAIT_REFUSE')].detail");
        assertThat(detailsRefus).containsExactly("Retrait refusé — Dossier déjà dispatché");
    }

    // ------------------------------------------------------------------ T2 — SIGMP direct clôt VERIFICATION

    @Test
    @DisplayName("T2 — avis FAV : la transmission SIGMP DIRECTE clôt l'étape VERIFICATION, qu'aucun passage "
            + "de vérification n'avait close — sans quoi tout son temps se reporterait sur la transmission")
    void transmissionSigmpDirecte_clotLaVerificationEnCours() throws Exception {
        Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setStatut("EN_VERIFICATION");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        seedPvSigne(700, 1);   // PV SIGNE, avis FAV : pas de boucle FAVR, donc pas de passage de vérification
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");

        mvc.perform(post("/api/sigmp-transmissions").header("Authorization", tokenVer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idDossier\":1}"))
                .andExpect(status().isCreated());

        List<TacheDossier> verifications = tacheRepository.findParDossier(1).stream()
                .filter(t -> EtapeCircuit.VERIFICATION.name().equals(t.getEtape())).toList();
        assertThat(verifications).as("l'étape est close ici, faute d'autre geste pour le faire").hasSize(1);
        assertThat(verifications.get(0).getDateFin()).isNotNull();
        assertThat(verifications.get(0).getImActeur()).isEqualTo("CTRVER");
        mvc.perform(get("/api/dossiers/1/chronometrage").header("Authorization", tokenVer))
                .andExpect(jsonPath("$.etapes[?(@.etape=='VERIFICATION' && @.enCours==true)]", hasSize(0)))
                .andExpect(jsonPath("$.etapes[?(@.etape=='TRANSMISSION_SIGMP')]", hasSize(1)));
    }

    // ------------------------------------------------------------------ T3 — justifications à la soumission

    @Test
    @DisplayName("T3 — la soumission exige les justifications de la fiche quel que soit le chemin : un marché "
            + "dérogatoire sans justification → 400 par champ ; justifié → SOUMIS")
    void soumission_exigeLesJustificationsDeLaFiche() throws Exception {
        ModePassation derogatoire = new ModePassation();
        derogatoire.setIdMode(702);
        derogatoire.setLibelle("Gré à gré");
        derogatoire.setCategorie(CategorieModePassation.DEROGATOIRE);
        modePassationRepository.save(derogatoire);
        Dossier d = dossier(3, "BROUILLON");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        Ppm ppm = ppmLocalise(30, 3, "ANT");
        ppm.setIdPrmp("PRMP001");
        ppmRepository.save(ppm);
        Marche m = marche(31, 3, 30);
        m.setIdMode(702);   // dérogatoire, sans justification — comme le laisserait passer un import PDF
        marcheRepository.save(m);

        mvc.perform(post("/api/dossiers/3/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='marches[0].justifModeDerogatoire')]", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[?(@.champ=='justificationFiche')]", hasSize(1)));
        assertThat(dossierRepository.findById(3).orElseThrow().getStatut()).isEqualTo("BROUILLON");

        m.setJustifModeDerogatoire("Urgence impérieuse");
        marcheRepository.save(m);
        ppm.setJustificationFiche("Voir note de justification jointe");
        ppmRepository.save(ppm);
        mvc.perform(post("/api/dossiers/3/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));
    }

    // ------------------------------------------------------------------ helpers

    private static ActionDossier action(int idDossier, String type, LocalDateTime date) {
        ActionDossier a = new ActionDossier();
        a.setIdDossier(idDossier);
        a.setTypeAction(type);
        a.setDateAction(date);
        a.setAuteur("PRMP001");
        return a;
    }
}
