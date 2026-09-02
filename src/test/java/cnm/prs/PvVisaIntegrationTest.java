package cnm.prs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ Réforme « Visa unique » (arbitrage du pilote, 2026-08-31) — {@code POST /api/pv-examens/{id}/viser}.
 *
 * <p>Le visa fusionne l'ancienne clôture de navette ({@code accepter}, retirée en 410) et la signature
 * du Président / Chef de commission : avis éventuellement modifié, Membre
 * co-signataire et part du rôle, en une transaction. La règle vient du pilote : « le Membre qui fait
 * l'examen émet son avis à la fin de l'examen ; cet avis peut être modifié à la fin de la navette, qui
 * finit par le visa du Président ou du CC QUI A FAIT LE DISPATCH ».</p>
 *
 * <p>Cette classe couvre ce que le visa AJOUTE : la contrainte d'identité (§4), les 400 de validation,
 * la conservation ou le remplacement de l'avis, la transition des PV acceptés sous l'ancien contrat
 * (§6) et le retrait de l’ancien contrat, plus le RETRAIT DU SECRÉTAIRE DE SÉANCE (2026-09-02).
 * Les gardes RECONDUITES —
 * co-signataire (2026-08-28), CC auto-attributaire — restent éprouvées là où elles vivaient déjà,
 * dans {@code AuthentificationHabilitationIntegrationTest} et {@code PvWorkflowIntegrationTest}.</p>
 */
class PvVisaIntegrationTest extends CnmIntegrationTestSupport {

    /** Crée un projet de PV sur l'examen 1 (dossier 1, ANT, dispatcheur CTRPRE) et le soumet. */
    private void projetSoumis(int idPv, String avis) throws Exception {
        String corpsAvis = avis == null ? "" : ",\"idAvis\":\"" + avis + "\"";
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":" + idPv + ",\"idExamen\":1" + corpsAvis + ",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Visa — un autre Président ne vise pas SANS NOTE : 400 depuis l'intérim du 2026-09-01 "
            + "(c'était 403 tant que l'intérim n'existait pas)")
    void visa_reserveAuDispatcheur() throws Exception {
        projetSoumis(9401, "FAV");
        // ⚠️ Changement de contrat du 2026-09-01 : un P/CC non dispatcheur n'est plus interdit de visa,
        // il lui manque une pièce. Le refus passe donc de 403 à 400 — le droit existe, la justification
        // manque. Le 403 ne subsiste que pour ce qui reste structurellement impossible : profil hors
        // P/CC, ou CC d'une autre localité.
        String tokenAutrePresident = bearer("CTRPRE2", ProfilUtilisateur.PRESIDENT, TypeActeur.CONTROLEUR,
                "CTRPRE2", null);
        viser(9401, tokenAutrePresident, "CTRPRE2", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Note d'intérim requise")));
        viser(9401, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Visa — le corps n'usurpe pas l'identité : imActeur falsifié ignoré, le jeton fait foi")
    void visa_identiteDepuisLeJeton() throws Exception {
        projetSoumis(9402, "FAV");
        // Le CC se déclare « CTRPRE » dans le corps : le serveur lit son jeton (CTRCC1), le voit non
        // dispatcheur, et réclame la note d'intérim. Le corps ne fabrique pas une identité.
        viser(9402, tokenCc, "CTRPRE", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Note d'intérim requise")));
    }

    @Test
    @DisplayName("Visa — seul le co-signataire est obligatoire : 400 de validation, pas 409")
    void visa_champsObligatoires_400() throws Exception {
        projetSoumis(9403, "FAV");
        viser(9403, tokenPresident, "CTRPRE", "FAV", "CTRVER", null).andExpect(status().isBadRequest());
        mvc.perform(get("/api/pv-examens/9403").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statutPv").value("PROJET_SOUMIS"));
    }

    @Test
    @DisplayName("Visa SANS Secrétaire de séance — accepté : la notion est retirée du cycle du PV (2026-09-02)")
    void visa_sansSecretaireSeance_accepte() throws Exception {
        projetSoumis(9420, "FAV");
        viser(9420, tokenPresident, "CTRPRE", "FAV", null, "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"))
                .andExpect(jsonPath("$.idSecretaireSeance").doesNotExist());
    }

    @Test
    @DisplayName("Visa AVEC le champ retiré — toléré et IGNORÉ : le PV ne porte aucun secrétaire")
    void visa_avecSecretaireSeance_ignore() throws Exception {
        projetSoumis(9421, "FAV");
        // Un client non a jour continue d'envoyer le champ : il ne doit pas etre refuse...
        viser(9421, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"))
                // ...mais la valeur n'est pas ecrite pour autant.
                .andExpect(jsonPath("$.idSecretaireSeance").doesNotExist());
        org.junit.jupiter.api.Assertions.assertNull(
                pvExamenRepository.findById(9421).orElseThrow().getIdSecretaireSeance(),
                "le champ envoye est ignore, jamais persiste");
    }

    @Test
    @DisplayName("Visa — un matricule FANTAISISTE au champ retiré ne declenche plus aucune garde")
    void visa_secretaireInexistant_neGardePlus() throws Exception {
        projetSoumis(9422, "FAV");
        // Avant le 2026-09-02 : 409 « doit etre un Verificateur de la localite ». La garde a ete
        // retiree avec la notion — un champ ignore ne peut pas etre invalide.
        viser(9422, tokenPresident, "CTRPRE", "FAV", "INCONNU", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSecretaireSeance").doesNotExist());
    }

    @Test
    @DisplayName("PV HISTORIQUE — un secrétaire deja en base reste lu au DTO (aucune purge)")
    void pvHistorique_secretaireConserveAuDto() throws Exception {
        projetSoumis(9423, "FAV");
        // Trace d'un PV vise AVANT la regle : la colonne porte encore un secretaire.
        cnm.prs.entity.PvExamen pv = pvExamenRepository.findById(9423).orElseThrow();
        pv.setIdSecretaireSeance("CTRVER");
        pvExamenRepository.save(pv);

        mvc.perform(get("/api/pv-examens/9423").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSecretaireSeance").value("CTRVER"))
                .andExpect(jsonPath("$.nomSecretaireSeance").exists());
    }

    @Test
    @DisplayName("CRUD générique — le champ retiré n'est plus INSCRIPTIBLE : POST /api/pv-examens l'ignore")
    void crudGenerique_secretaireSeanceNonInscriptible() throws Exception {
        // Derniere porte d'ecriture fermee le 2026-09-02 : le mapper ne copie plus le champ vers
        // l'entite. Un client qui le poste encore ne le persiste plus — sans quoi la notion serait
        // reapparue un jour par ce canal, sans que personne comprenne d'ou.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":9430,\"idExamen\":1,\"imCtrlMembre\":\"CTRMEM\",\"idAvis\":\"FAV\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0,\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idSecretaireSeance").doesNotExist());

        org.junit.jupiter.api.Assertions.assertNull(
                pvExamenRepository.findAll().stream()
                        .filter(p -> Integer.valueOf(1).equals(p.getIdExamen()))
                        .findFirst().orElseThrow().getIdSecretaireSeance(),
                "le champ poste ne doit pas etre persiste");
    }

    @Test
    @DisplayName("Mise à jour — un PV HISTORIQUE modifié conserve son secrétaire (update n'y touche pas)")
    void miseAJour_pvHistorique_conserveSonSecretaire() throws Exception {
        // PV en BROUILLON (seul etat modifiable par le Membre) portant un secretaire, comme un PV
        // redige avant le 2026-09-02.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":9431,\"idExamen\":1,\"imCtrlMembre\":\"CTRMEM\",\"idAvis\":\"FAV\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        cnm.prs.entity.PvExamen pv = pvExamenRepository.findById(9431).orElseThrow();
        pv.setIdSecretaireSeance("CTRVER");
        pvExamenRepository.save(pv);

        // La fermeture de la porte d'ecriture ne doit RIEN effacer : update reaffecte ses champs un
        // par un et ne touche jamais celui-ci. Un PV redige hier garde sa trace.
        mvc.perform(put("/api/pv-examens/9431").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":9431,\"idExamen\":1,\"imCtrlMembre\":\"CTRMEM\",\"idAvis\":\"FAV\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0,"
                        + "\"syntheseObservations\":\"synthese revue\"}"))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals("CTRVER",
                pvExamenRepository.findById(9431).orElseThrow().getIdSecretaireSeance(),
                "un PV anterieur ne doit pas perdre son secretaire a la mise a jour");
    }

    @Test
    @DisplayName("Visa — avis ABSENT du corps : celui émis par le Membre à la soumission est conservé")
    void visa_avisAbsent_conserveCeluiDuMembre() throws Exception {
        projetSoumis(9404, "DEF");
        viser(9404, tokenPresident, "CTRPRE", null, "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAvis").value("DEF"))
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
    }

    @Test
    @DisplayName("Visa — avis FOURNI : il remplace celui du Membre (le P/CC garde le dernier mot)")
    void visa_avisFourni_remplace() throws Exception {
        projetSoumis(9405, "DEF");
        viser(9405, tokenPresident, "CTRPRE", "NSP", "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAvis").value("NSP"));
    }

    @Test
    @DisplayName("Visa — PV sans aucun avis (navette ouverte avant la réforme, §6) : le visa doit en fournir un (409)")
    void visa_pvSansAvis_exigeUnAvis() throws Exception {
        projetSoumis(9406, null);   // avis NULL : les PV en vol au moment du déploiement
        viser(9406, tokenPresident, "CTRPRE", null, "CTRVER", "CTRMEM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("aucun avis")));
        viser(9406, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Visa — cohérence avis ↔ observations revalidée : ≥ 1 observation ⇒ FAV refusé (409)")
    void visa_avisIncoherent_refuse() throws Exception {
        ajouterObservationExamen1();
        projetSoumis(9407, "FAVR");
        viser(9407, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM").andExpect(status().isConflict());
        viser(9407, tokenPresident, "CTRPRE", "FAVR", "CTRVER", "CTRMEM").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Visa — transition (§6) : rejouable tant que la part du rôle n'est pas posée, refusé ensuite")
    void visa_surProjetAccepteNonSigne() throws Exception {
        projetSoumis(9408, "FAV");
        viser(9408, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"));
        // Rejouer : la part Président est posée → 409, le verrou « une signature par rôle » tient.
        viser(9408, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Visa — le PV expose imDispatcheur et nomDispatcheur : le front conditionne son bouton sans "
            + "charger le dispatch")
    void visa_dtoExposeLeDispatcheur() throws Exception {
        projetSoumis(9409, "FAV");
        mvc.perform(get("/api/pv-examens/9409").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imDispatcheur").value("CTRPRE"))
                .andExpect(jsonPath("$.nomDispatcheur").exists());
    }

    @Test
    @DisplayName("Visa — l'ancien contrat est retiré : « accepter » en 410, « signer(PRESIDENT) » en 409")
    void visa_ancienContratRetire() throws Exception {
        projetSoumis(9410, "FAV");
        mvc.perform(post("/api/pv-examens/9410/accepter").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRPRE\",\"idAvis\":\"FAV\",\"idSecretaireSeance\":\"CTRVER\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message", containsString("viser")));
        viser(9410, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM").andExpect(status().isOk());
        mvc.perform(post("/api/pv-examens/9410/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRPRE\",\"role\":\"PRESIDENT\",\"imMembreCoSignataire\":\"CTRMEM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("viser")));
    }

    @Test
    @DisplayName("Visa — parcours complet : soumission avec avis, visa, co-signature du Membre désigné → SIGNE")
    void visa_parcoursComplet() throws Exception {
        projetSoumis(9411, "FAV");
        viser(9411, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"))
                .andExpect(jsonPath("$.dateAcceptation").isNotEmpty())
                .andExpect(jsonPath("$.imCtrlPresident").value("CTRPRE"))
                .andExpect(jsonPath("$.imMembreCoSignataire").value("CTRMEM"))
                .andExpect(jsonPath("$.nomMembreCoSignataire").exists());
        mvc.perform(post("/api/pv-examens/9411/signer").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"));
        // Le Membre désigné a bien reçu PV_A_COSIGNER (notification du 2026-08-28, inchangée).
        mvc.perform(get("/api/notifications/mes").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.typeNotif=='PV_A_COSIGNER')].idObjet", hasItem(9411)));
    }
}
