package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Nature;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.ChangementLigneRepository;
import cnm.prs.repository.SnapshotRectifLigneRepository;

/**
 * ⚠️ <strong>Demande pilote 2026-09-10</strong> — l'objet d'un marché plafonnait à <strong>500</strong>
 * caractères, en base comme à la validation : un objet réel (libellé administratif complet, lieu, tranche,
 * financement) était refusé. Il passe en <strong>texte libre</strong> (V27, {@code text}) avec une borne de
 * validation ramenée au seul rôle d'écarter l'aberrant.
 *
 * <p>⚠️ <strong>Ce test suit la désignation le long du circuit, pas seulement à la saisie.</strong> La
 * désignation est RECOPIÉE dans trois autres colonnes — l'archive d'une rectification, la trace figée d'une
 * mise à jour, et les deux versions d'une valeur qui change. N'en élargir qu'une aurait déplacé l'échec
 * plus loin : sur un dossier déjà accepté à la saisie, au moment de l'archiver ou de le soumettre.</p>
 *
 * <p>{@code t_lot.DESIGNATION_LOT} reste à 200 — autre champ, hors demande ; un test le vérifie.</p>
 */
class DesignationMarcheLongueIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired
    private SnapshotRectifLigneRepository snapshotRectifLigneRepository;
    @Autowired
    private ChangementLigneRepository changementLigneRepository;

    /** Objet de marché réaliste de la longueur demandée — ASCII pur (l'encodage du corps n'est pas le sujet). */
    private static String objet(int taille) {
        String motif = "Travaux de rehabilitation et d'extension du reseau d'adduction d'eau potable "
                + "de la commune rurale d'Ambohidratrimo, tranche ferme, lot unique, sur financement "
                + "interne de l'exercice budgetaire 2026, conformement au plan approuve. ";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < taille) {
            sb.append(motif);
        }
        return sb.substring(0, taille);
    }

    /** Nature, mode et processus attendus par la façade de saisie (le seed standard ne les porte pas). */
    private void decorSaisie() {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new cnm.prs.entity.ModePassation(2, "AOR", null, null, null, null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
    }

    private static String corpsSaisie(String designation, String reference) {
        return "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\","
                + "\"reference\":\"" + reference + "\",\"marches\":[{\"designationMarche\":\"" + designation + "\","
                + "\"montEstim\":1000000,\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
    }

    // ------------------------------------------------------------------ 1. saisie : POST puis PUT

    @Test
    @DisplayName("POST /api/saisies/ppm : un objet de 800 caractères est accepté (201) et persisté INTÉGRALEMENT")
    void saisie_objet800_accepteEtPersisteEntier() throws Exception {
        decorSaisie();
        String attendu = objet(800);
        var res = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsSaisie(attendu, "PPM-LONG-1")))
                .andReturn();
        Assertions.assertEquals(201, res.getResponse().getStatus(),
                "saisie refusee -- corps : " + res.getResponse().getContentAsString());
        int idDossier = (int) (Integer) JsonPath.read(res.getResponse().getContentAsString(), "$.idDossier");

        Marche ligne = marcheRepository.findByIdDossier(idDossier).get(0);
        // ⚠️ Pas seulement « ça passe » : la valeur relue doit être la valeur envoyée, au caractère près.
        // Une colonne trop courte n'aurait pas forcément levé — PostgreSQL tronque sur certains chemins.
        Assertions.assertEquals(800, ligne.getDesignationMarche().length(), "objet tronque en base");
        Assertions.assertEquals(attendu, ligne.getDesignationMarche());
    }

    @Test
    @DisplayName("PUT /api/saisies/ppm/{id} : l'édition d'un brouillon accepte 900 caractères et les conserve")
    void edition_objet900_accepteEtPersisteEntier() throws Exception {
        decorSaisie();
        var creation = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsSaisie(objet(600), "PPM-LONG-2")))
                .andExpect(status().isCreated()).andReturn();
        int idDossier = (int) (Integer) JsonPath.read(creation.getResponse().getContentAsString(), "$.idDossier");
        int idDetail = marcheRepository.findByIdDossier(idDossier).get(0).getIdDetail();

        String attendu = objet(900);
        String corps = "{\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\","
                + "\"reference\":\"PPM-LONG-2\",\"marches\":[{\"idDetail\":" + idDetail
                + ",\"designationMarche\":\"" + attendu + "\",\"montEstim\":1000000,\"idNature\":1,"
                + "\"idMode\":2,\"statut\":\"PREVU\"}]}";
        var res = mvc.perform(put("/api/saisies/ppm/" + idDossier).header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andReturn();
        Assertions.assertEquals(200, res.getResponse().getStatus(),
                "edition refusee -- corps : " + res.getResponse().getContentAsString());

        Assertions.assertEquals(attendu, marcheRepository.findById(idDetail).orElseThrow().getDesignationMarche());
    }

    // ------------------------------------------------------------------ 2. rectification : ligne + archive

    /** Dossier 700 posé en EN_ATTENTE_DECISION_PRMP, comme {@code VersionsRectificationIntegrationTest}. */
    private void dossierEnRectification(String designationInitiale) throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        Dossier d = dossierLoc(700, "EN_ATTENTE_DECISION_PRMP", "ANT", "PRMP001");
        d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        ppmRepository.save(ppm(700, 700, "PRMP001"));
        Marche m = marche(7001, 700, 700);
        m.setMontEstim(new BigDecimal("100"));
        m.setDesignationMarche(designationInitiale);
        marcheRepository.save(m);
    }

    @Test
    @DisplayName("Rectification : PUT avec un objet long → 200, et l'ARCHIVE de la version remplacée garde l'objet long d'origine")
    void rectification_objetLong_ligneEtArchive() throws Exception {
        String initiale = objet(700);
        dossierEnRectification(initiale);
        String attendu = objet(850);

        String corps = "{\"exercice\":2026,\"signataire\":\"PRMP Test\",\"dateSignature\":\"2026-06-01\","
                + "\"reference\":\"PPM-700\",\"marches\":[{\"idDetail\":7001,\"formeMarche\":\"QUANTITE_FIXE\","
                + "\"montEstim\":200,\"idNature\":1,\"statut\":\"PREVU\",\"designationMarche\":\"" + attendu + "\"}]}";
        var res = mvc.perform(put("/api/saisies/ppm/700").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andReturn();
        Assertions.assertEquals(200, res.getResponse().getStatus(),
                "rectification refusee -- corps : " + res.getResponse().getContentAsString());

        Assertions.assertEquals(attendu, marcheRepository.findById(7001).orElseThrow().getDesignationMarche());
        // ⚠️ Le cœur du volet : le PUT a ARCHIVÉ l'état d'avant. Si la colonne d'archive était restée à
        // 500, c'est ici que la rectification aurait échoué — après coup, sur une ligne déjà acceptée.
        var archivees = snapshotRectifLigneRepository.findAll().stream()
                .filter(l -> Integer.valueOf(7001).equals(l.getIdDetail())).toList();
        Assertions.assertFalse(archivees.isEmpty(), "aucune ligne archivee");
        Assertions.assertEquals(initiale, archivees.get(0).getDesignationMarche(),
                "l'archive doit garder l'objet long d'origine, entier");
    }

    @Test
    @DisplayName("PATCH /api/marches/{id}/rectifier : un objet long passe la validation du groupe rectification")
    void rectificationUnitaire_objetLong_accepte() throws Exception {
        dossierEnRectification(objet(600));
        String attendu = objet(800);
        var res = mvc.perform(patch("/api/marches/7001/rectifier").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designationMarche\":\"" + attendu + "\"}"))
                .andReturn();
        Assertions.assertEquals(200, res.getResponse().getStatus(),
                "rectification unitaire refusee -- corps : " + res.getResponse().getContentAsString());
        Assertions.assertEquals(attendu, marcheRepository.findById(7001).orElseThrow().getDesignationMarche());
    }

    // ------------------------------------------------------------------ 3. mise à jour : la trace figée

    // ⚠️ Tagué « word » (2026-09-12) — SEUL test de cette classe à soumettre une MISE À JOUR, donc à
    // passer par la pièce d'historique : elle régénère le PV du prédécesseur via MS Word (documents4j),
    // indisponible sur un runner Linux. Échec d'environnement, pas de règle ; exclu en CI
    // (-DexcludedGroups=word), exécuté en local. Voir SoumissionMiseAJourPiecesHistoriqueIntegrationTest
    // pour la contrainte de déploiement que ce tag laisse entière.
    @Test
    @org.junit.jupiter.api.Tag("word")
    @DisplayName("Mise à jour : la TRACE figée à la soumission garde l'objet long, avant ET après")
    void miseAJour_traceFigee_gardeLesDeuxObjetsLongs() throws Exception {
        String avant = objet(700);
        Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setIdPrmp("PRMP001");
        d.setIdEntiteContract(1);
        d.setIdTypeDossier("DDP");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        Marche source = marche(9800, 1, 1);
        source.setDesignationMarche(avant);
        marcheRepository.save(source);

        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        cloturerDossier1(9801, tokenVer);

        var ouverture = mvc.perform(post("/api/saisies/ppm/1/mise-a-jour").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Objet reformule\"}"))
                .andExpect(status().isCreated()).andReturn();
        int idMaj = (int) (Integer) JsonPath.read(ouverture.getResponse().getContentAsString(), "$.idDossier");

        // La copie a déjà transporté l'objet long : premier point de passage.
        Marche copie = marcheRepository.findByIdDossier(idMaj).get(0);
        Assertions.assertEquals(avant, copie.getDesignationMarche(), "la copie doit transporter l'objet entier");

        // On reformule l'objet, puis on soumet : c'est la soumission qui FIGE la trace.
        String apres = objet(950);
        copie.setDesignationMarche(apres);
        marcheRepository.save(copie);
        Assertions.assertEquals(apres, marcheRepository.findById(copie.getIdDetail()).orElseThrow()
                .getDesignationMarche());   // force le flush avant la soumission

        var soumission = mvc.perform(post("/api/dossiers/" + idMaj + "/soumettre").header("Authorization", tokenPrmp))
                .andReturn();
        Assertions.assertEquals(200, soumission.getResponse().getStatus(),
                "soumission refusee -- corps : " + soumission.getResponse().getContentAsString());

        var trace = changementLigneRepository.findByIdDossierOrderByIdChangementAsc(idMaj);
        Assertions.assertFalse(trace.isEmpty(), "aucune trace figee");
        Assertions.assertTrue(trace.stream().anyMatch(c -> apres.equals(c.getDesignation())),
                "la trace doit porter l'objet long comme designation de ligne");
        Assertions.assertTrue(
                trace.stream().anyMatch(c -> avant.equals(c.getValeurAvant()) && apres.equals(c.getValeurApres())),
                "la trace doit porter les DEUX objets longs, avant et apres, entiers");
    }

    // ------------------------------------------------------------------ 4. la borne voisine ne bouge pas

    @Test
    @DisplayName("t_lot.designationLot reste borné à 200 — autre champ, hors demande")
    void designationLot_resteBornee() throws Exception {
        decorSaisie();
        String corps = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\","
                + "\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-LOT\","
                + "\"marches\":[{\"designationMarche\":\"" + objet(800) + "\",\"montEstim\":1000000,"
                + "\"idNature\":1,\"idMode\":2,\"statut\":\"PREVU\","
                + "\"lots\":[{\"designationLot\":\"" + objet(300) + "\",\"montLot\":1000,\"qteLot\":1,\"uniteLot\":\"u\"}],"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest());
    }
}
