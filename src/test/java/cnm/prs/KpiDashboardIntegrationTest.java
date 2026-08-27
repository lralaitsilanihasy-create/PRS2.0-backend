package cnm.prs;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.DemandeRetrait;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Examen;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Tableaux de bord et indicateurs : compteurs de menu par profil, badges agreges, KPIs du
 * pipeline, KPIs par localite et rapports exportes (PDF, Excel).
 */
class KpiDashboardIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("KPIs : tableau de bord (pipeline + taux de conformité) réservé Président/Admin")
    void kpis_tableauBord() throws Exception {
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbDossiersSoumis").value(2))
                .andExpect(jsonPath("$.nbDossiersConformes").value(0))
                .andExpect(jsonPath("$.tauxConformitePct").value(0.0))
                .andExpect(jsonPath("$.pipelineParStatut.EXAMINE").value(2))
                .andExpect(jsonPath("$.topNonConformite").isArray());
        // Réservé : un Membre n'accède pas aux KPIs globaux.
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Rapport PDF : généré pour le Président, interdit au Membre")
    void rapport_dossiersPdf() throws Exception {
        byte[] pdf = mvc.perform(get("/api/rapports/dossiers").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 100, "le PDF doit être non vide");
        assertTrue(new String(pdf, 0, 4, StandardCharsets.US_ASCII).equals("%PDF"), "en-tête PDF attendu");

        // Un Membre ne peut pas générer le rapport (réservé Président / Admin).
        mvc.perform(get("/api/rapports/dossiers").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Export Excel : .xlsx généré pour le Président, interdit au Membre")
    void rapport_dossiersExcel() throws Exception {
        byte[] xlsx = mvc.perform(get("/api/rapports/dossiers/excel").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(xlsx.length > 100, "le classeur doit être non vide");
        assertTrue(xlsx[0] == 'P' && xlsx[1] == 'K', "signature ZIP/xlsx attendue (PK)");

        mvc.perform(get("/api/rapports/dossiers/excel").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Rapport par localité : CC autorisé (forcé sur sa localité), Président peut cibler une localité")
    void rapport_parLocalite() throws Exception {
        // Le Chef de commission peut désormais générer le rapport : il est forcé sur sa propre localité (ANT).
        byte[] pdfCc = mvc.perform(get("/api/rapports/dossiers").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(new String(pdfCc, 0, 4, StandardCharsets.US_ASCII).equals("%PDF"), "en-tête PDF attendu");

        // Idem en Excel.
        byte[] xlsxCc = mvc.perform(get("/api/rapports/dossiers/excel").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(xlsxCc[0] == 'P' && xlsxCc[1] == 'K', "signature ZIP/xlsx attendue (PK)");

        // Le Président peut cibler explicitement une localité via ?localite=.
        mvc.perform(get("/api/rapports/dossiers").header("Authorization", tokenPresident).param("localite", "ANT"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("Tableau de bord Président : compteurs de contenu présents (6 sections, valeurs ≥ 0)")
    void dashboard_compteurs_president_ok() throws Exception {
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compteurs").exists())
                .andExpect(jsonPath("$.compteurs.predispatch").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.compteurs.dispatch").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.compteurs.projetsPV").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.compteurs.lettresRenvoi").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.compteurs.pvDefinitifs").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.compteurs.demandesRetrait").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Tableau de bord CC : compteurs filtrés sur sa localité (Président voit le global)")
    void dashboard_compteurs_cc_localite_ok() throws Exception {
        // Un dossier PRET_DISPATCH en ANT, un autre en TMS.
        Dossier ant = dossier(170, "PRET_DISPATCH"); ant.setIdLocalite("ANT"); dossierRepository.save(ant);
        Dossier tms = dossier(171, "PRET_DISPATCH"); tms.setIdLocalite("TMS"); dossierRepository.save(tms);

        // CC d'ANT : ne compte que le dossier de sa localité.
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compteurs.predispatch").value(1));

        // Président : vue globale → compte les deux localités.
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compteurs.predispatch").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("Menu PRMP : compteurs présents (5 sections, valeurs ≥ 0), filtrés sur la PRMP du JWT")
    void dashboard_compteurs_prmp_ok() throws Exception {
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brouillons").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.ppmMarches").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.dossiersARectifier").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.dossiersVerifies").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.lettresRenvoi").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu PRMP : 2 brouillons de la PRMP → brouillons = 2")
    void dashboard_brouillons_ok() throws Exception {
        Dossier b1 = dossier(180, "BROUILLON"); b1.setIdPrmp("PRMP001"); dossierRepository.save(b1);
        Dossier b2 = dossier(181, "BROUILLON"); b2.setIdPrmp("PRMP001"); dossierRepository.save(b2);

        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brouillons").value(2));
    }

    @Test
    @DisplayName("Menu PRMP : dossier EN_ATTENTE_DECISION_PRMP → dossiersARectifier = 1")
    void dashboard_rectifier_ok() throws Exception {
        Dossier d = dossier(182, "EN_ATTENTE_DECISION_PRMP"); d.setIdPrmp("PRMP001"); dossierRepository.save(d);

        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dossiersARectifier").value(1));
    }

    @Test
    @DisplayName("Menu PRMP : lettre SIGNÉE d'un dossier de la PRMP → lettresRenvoi ≥ 1")
    void dashboard_lettres_ok() throws Exception {
        // Le dossier 1 a un PPM de PRMP001 (seed) ; on lui attache une lettre SIGNÉE.
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1); l.setIdDossier(1); l.setObjetLettre("Renvoi"); l.setStatut("SIGNE");
        lettreRenvoiRepository.save(l);

        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Menu vérificateur : compteurs présents (3 sections, valeurs ≥ 0), filtrés sur sa localité")
    void dashboard_compteurs_verificateur_ok() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(get("/api/kpis/mes-compteurs-verificateur").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aVerifier").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.verifies").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.enAttentePrmp").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu vérificateur : dossier EN_VERIFICATION de sa localité → aVerifier = 1")
    void dashboard_verif_aVerifier_ok() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        Dossier d = dossier(190, "EN_VERIFICATION"); dossierRepository.save(d);
        receptionRepository.save(reception(190, 190, "CTRCC1", true)); // réception ANT (CTRCC1)

        mvc.perform(get("/api/kpis/mes-compteurs-verificateur").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aVerifier").value(1))
                .andExpect(jsonPath("$.enAttentePrmp").value(0));
    }

    @Test
    @DisplayName("Menu vérificateur : dossier EN_ATTENTE_DECISION_PRMP → enAttentePrmp = 1 (compté aussi dans aVerifier)")
    void dashboard_verif_enAttentePrmp_ok() throws Exception {
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        Dossier d = dossier(191, "EN_ATTENTE_DECISION_PRMP"); dossierRepository.save(d);
        receptionRepository.save(reception(191, 191, "CTRCC1", true)); // réception ANT

        mvc.perform(get("/api/kpis/mes-compteurs-verificateur").header("Authorization", tokenVer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enAttentePrmp").value(1))
                .andExpect(jsonPath("$.aVerifier").value(1));
    }

    @Test
    @DisplayName("Menu secrétaire : compteurs présents (2 sections, valeurs ≥ 0), filtrés sur sa localité")
    void dashboard_compteurs_secretaire_ok() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        mvc.perform(get("/api/kpis/mes-compteurs-secretaire").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aReceptionner").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.receptions").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu secrétaire : dossier SOUMIS sans réception de sa localité → aReceptionner = 1")
    void dashboard_sec_aReceptionner_ok() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d = dossier(200, "SOUMIS"); d.setIdLocalite("ANT"); dossierRepository.save(d); // pas de réception

        mvc.perform(get("/api/kpis/mes-compteurs-secretaire").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aReceptionner").value(1));
    }

    @Test
    @DisplayName("Menu secrétaire : réceptions de sa localité comptées (réception ANT seedée) → receptions ≥ 1")
    void dashboard_sec_receptions_ok() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // La réception 1 (CTRCC1, localité ANT) est seedée dans @BeforeEach.
        mvc.perform(get("/api/kpis/mes-compteurs-secretaire").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receptions").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Menu membre : compteurs présents (2 sections, valeurs ≥ 0), filtrés sur le Membre attributaire")
    void dashboard_compteurs_membre_ok() throws Exception {
        mvc.perform(get("/api/kpis/mes-compteurs-membre").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aExaminer").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.examines").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu membre : dossier DISPATCHE qui lui est attribué → aExaminer = 1")
    void dashboard_membre_aExaminer_ok() throws Exception {
        // Dossier DISPATCHE + réception + dispatch attribué à CTRMEM (le Membre du token).
        Dossier d = dossier(210, "DISPATCHE"); dossierRepository.save(d);
        receptionRepository.save(reception(210, 210, "CTRCC1", true));
        dispatchRepository.save(dispatch(210, 210, "CTRCC1", "CTRMEM"));

        mvc.perform(get("/api/kpis/mes-compteurs-membre").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aExaminer").value(1));
    }

    @Test
    @DisplayName("Menu membre : dossier EXAMINE attribué (seed) → examines ≥ 1")
    void dashboard_membre_examines_ok() throws Exception {
        // Le dossier 1 (EXAMINE) est dispatché à CTRMEM dans @BeforeEach.
        mvc.perform(get("/api/kpis/mes-compteurs-membre").header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examines").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Menu chargé de publication : compteurs présents (3 sections, valeurs ≥ 0)")
    void dashboard_compteurs_publication_ok() throws Exception {
        mvc.perform(get("/api/kpis/mes-compteurs-publication").header("Authorization", tokenPublication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aPublier").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.publiees").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.retirees").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu chargé de publication : publication EN_ATTENTE → aPublier = 1")
    void dashboard_pub_aPublier_ok() throws Exception {
        seedPublication(300, "EN_ATTENTE");
        mvc.perform(get("/api/kpis/mes-compteurs-publication").header("Authorization", tokenPublication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aPublier").value(1));
    }

    @Test
    @DisplayName("Menu chargé de publication : publication PUBLIE → publiees = 1")
    void dashboard_pub_publiees_ok() throws Exception {
        seedPublication(301, "PUBLIE");
        mvc.perform(get("/api/kpis/mes-compteurs-publication").header("Authorization", tokenPublication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publiees").value(1));
    }

    @Test
    @DisplayName("Menu assistant contrôleur : compteurs présents (2 sections, valeurs ≥ 0), filtrés sur sa localité")
    void dashboard_compteurs_assistant_ok() throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        mvc.perform(get("/api/kpis/mes-compteurs-assistant").header("Authorization", tokenAss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.pvDefinitifs").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu assistant contrôleur : lettre SIGNÉE de sa localité → lettresRenvoi ≥ 1")
    void dashboard_assist_lettres_ok() throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        // Examen 1 → dispatch 1 → réception 1 (CTRCC1, ANT) : une lettre SIGNÉE sur cet examen.
        LettreRenvoi l = new LettreRenvoi();
        l.setIdExamen(1); l.setIdDossier(1); l.setObjetLettre("Renvoi"); l.setStatut("SIGNE");
        lettreRenvoiRepository.save(l);

        mvc.perform(get("/api/kpis/mes-compteurs-assistant").header("Authorization", tokenAss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Menu assistant contrôleur : PV signé de sa localité → pvDefinitifs ≥ 1")
    void dashboard_assist_pvDefinitifs_ok() throws Exception {
        String tokenAss = bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT");
        seedPvSigne(400, 1); // PV signé sur l'examen 1 (localité ANT via réception 1)

        mvc.perform(get("/api/kpis/mes-compteurs-assistant").header("Authorization", tokenAss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pvDefinitifs").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Menu administrateur : compteurs présents (3 sections, valeurs ≥ 0)")
    void dashboard_compteurs_admin_ok() throws Exception {
        mvc.perform(get("/api/kpis/mes-compteurs-admin").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inscriptionsEnAttente").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.comptes").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.journalAudit").value(greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Menu administrateur : inscription PRMP EN_ATTENTE → inscriptionsEnAttente = 1")
    void dashboard_admin_inscriptions_ok() throws Exception {
        CompteAuth c = new CompteAuth("prmp.att", "x", "PRMP", "prmp.att", false);
        c.setStatut("EN_ATTENTE");
        compteAuthRepository.save(c);

        mvc.perform(get("/api/kpis/mes-compteurs-admin").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inscriptionsEnAttente").value(1));
    }

    @Test
    @DisplayName("Menu administrateur : comptes seedés comptés → comptes ≥ 1")
    void dashboard_admin_comptes_ok() throws Exception {
        // @BeforeEach crée plusieurs comptes d'authentification.
        mvc.perform(get("/api/kpis/mes-compteurs-admin").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comptes").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Compteur PRMP : lettre SIGNÉE non lue → lettresRenvoi = 1")
    void compteur_lettre_non_lue() throws Exception {
        seedLettreSignee(); // non lue
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(1));
    }

    @Test
    @DisplayName("Compteur PRMP : lettre SIGNÉE lue (consultée) → exclue, lettresRenvoi = 0")
    void compteur_lettre_lue_exclu() throws Exception {
        int id = seedLettreSignee();
        mvc.perform(get("/api/lettre-renvois/" + id).header("Authorization", tokenPrmp)).andExpect(status().isOk());
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lettresRenvoi").value(0));
    }

    @Test
    @DisplayName("Compteur PRMP : demande décidée (ACCEPTEE) après la dernière vue → demandesRetraitNouvelles = 1")
    void compteur_demandes_nouvelles_ok() throws Exception {
        // Aucune consultation préalable → seuil = époque → la décision récente est comptée.
        seedDemandeDecision("ACCEPTEE", LocalDateTime.of(2026, 6, 20, 9, 0));
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandesRetraitNouvelles").value(1));
    }

    @Test
    @DisplayName("Compteur PRMP : demande décidée AVANT la dernière vue → exclue, demandesRetraitNouvelles = 0")
    void compteur_demandes_anciennes_exclu() throws Exception {
        seedDemandeDecision("ACCEPTEE", LocalDateTime.of(2026, 1, 1, 0, 0)); // décision ancienne
        demandeRetraitVueRepository.save(
                new cnm.prs.entity.DemandeRetraitVue(null, "PRMP001", LocalDateTime.of(2026, 6, 1, 0, 0)));
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandesRetraitNouvelles").value(0));
    }

    @Test
    @DisplayName("Compteur PRMP : demande EN_ATTENTE → jamais comptée, demandesRetraitNouvelles = 0")
    void compteur_demandes_en_attente_exclu() throws Exception {
        seedDemandeDecision("EN_ATTENTE", null); // pas de décision
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandesRetraitNouvelles").value(0));
    }

    /** Crée une demande de retrait de PRMP001 (dossier 1) au statut/date de décision donnés. */
    private void seedDemandeDecision(String statut, LocalDateTime dateDecision) {
        DemandeRetrait d = demandeRetrait(0, 1, "PRMP001");
        d.setStatut(statut);
        d.setDateDecision(dateDecision);
        demandeRetraitRepository.save(d);
    }

    /** Crée une publication H2 au statut donné (PK manuelle). */
    private void seedPublication(int id, String statut) {
        cnm.prs.entity.Publication p = new cnm.prs.entity.Publication();
        p.setIdPublication(id);
        p.setTypeObjet("PV");
        p.setIdObjet(1);
        p.setStatutPubli(statut);
        publicationRepository.save(p);
    }

    @Test
    @DisplayName("Badges de menu agrégés (audit front 2026-08-16) : GET /api/kpis/badges route sur le profil du "
            + "connecté et renvoie ses compteurs en un appel (mêmes DTOs que les mes-compteurs*)")
    void kpis_badgesAgreges_parProfil() throws Exception {
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profil").value("PRMP"))
                .andExpect(jsonPath("$.compteurs.brouillons").isNumber())
                .andExpect(jsonPath("$.compteurs.demandesRetraitNouvelles").isNumber());
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.profil").value("CHEF_COMMISSION"))
                .andExpect(jsonPath("$.compteurs.predispatch").isNumber());
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.profil").value("PRESIDENT"))
                .andExpect(jsonPath("$.compteurs.predispatch").isNumber());
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        mvc.perform(get("/api/kpis/badges").header("Authorization", tokenSec))
                .andExpect(jsonPath("$.profil").value("SECRETAIRE"))
                .andExpect(jsonPath("$.compteurs.aReceptionner").isNumber());
    }

    @Test
    @DisplayName("KPIs par localité : le CC ne voit que sa localité ; le Président voit tout")
    void kpisParLocalite() throws Exception {
        Dossier a = dossier(140, "SOUMIS"); a.setIdLocalite("ANT"); dossierRepository.save(a);
        Dossier b = dossier(141, "PRET_DISPATCH"); b.setIdLocalite("ANT"); dossierRepository.save(b);
        Dossier c = dossier(142, "SOUMIS"); c.setIdLocalite("TMS"); dossierRepository.save(c);
        Dossier e = dossier(143, "BROUILLON"); e.setIdLocalite("ANT"); dossierRepository.save(e);

        // CC d'ANT : pipeline ANT (SOUMIS, PRET_DISPATCH, BROUILLON), mais nbDossiersSoumis exclut le BROUILLON.
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbDossiersSoumis").value(2))   // 140, 141 ; le BROUILLON 143 exclu
                .andExpect(jsonPath("$.pipelineParStatut.SOUMIS").value(1))
                .andExpect(jsonPath("$.pipelineParStatut.PRET_DISPATCH").value(1))
                .andExpect(jsonPath("$.pipelineParStatut.BROUILLON").value(1));
        // Président : global → SOUMIS = 2 (140 ANT + 142 TMS).
        mvc.perform(get("/api/kpis/tableau-bord").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineParStatut.SOUMIS").value(2));
    }
}
