package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Examen;
import cnm.prs.entity.EntiteContract;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Generation des references : dossier (a la reception), lettre de renvoi, proces-verbal et PPM -
 * sequence globale, compteur indexe sur la famille, segment de sous-type verbatim, isolement par
 * contexte et absence de doublon en concurrence.
 */
class ReferenceGenerationIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Référence dossier — séquence globale unique (2 localités, même année → numéros distincts/consécutifs)")
    void reference_sequence_unique_globale() {
        // Segment affiché = sous-type (PPM) ; clé de compteur = famille (DDP).
        String rAnt = referenceService.generer("PPM", "ANT", false, 2099);
        String rTms = referenceService.generer("PPM", "TMS", false, 2099);
        // Numéros distincts ET consécutifs malgré des localités différentes (plus de « 00001 » partagé).
        org.junit.jupiter.api.Assertions.assertEquals("00001/PPM/CRM-ANT/2099", rAnt);
        org.junit.jupiter.api.Assertions.assertEquals("00002/PPM/CRM-TMS/2099", rTms);
    }

    @Test
    @DisplayName("⚠️ 2026-10-27 (« numero-dossier-unique », N1) — un seul compteur par exercice : PPM, PPM-AGPM et DAOO se suivent, "
            + "segment de sous-type verbatim")
    void reference_compteurUnique_famillesMeleees() {
        org.junit.jupiter.api.Assertions.assertEquals("00001/PPM/CRM-ANT/2097",
                referenceService.generer("PPM", "ANT", false, 2097));
        org.junit.jupiter.api.Assertions.assertEquals("00002/PPM-AGPM/CRM-ANT/2097",
                referenceService.generer("PPM-AGPM", "ANT", false, 2097));
        // Une autre famille (DMC) ne repart plus à 1 : même compteur.
        org.junit.jupiter.api.Assertions.assertEquals("00003/DAOO/CRM-ANT/2097",
                referenceService.generer("DAOO", "ANT", false, 2097));
    }

    @Test
    @DisplayName("⚠️ 2026-10-27 (« numero-dossier-unique », N2 ; V143) — le compteur unique reprend après le plus grand numéro des "
            + "compteurs par famille de l'exercice (DMC 4, DDP 1 → 00005) ; lettres et références PPM non comptées ; autre exercice "
            + "repris à part")
    void reference_compteurUnique_repriseV143() {
        jdbcTemplate.update("insert into t_sequence_reference values (2095, 'DOSSIER', 'DDP', 1), (2095, 'DOSSIER', 'DMC', 4), "
                + "(2095, 'DOSSIER', 'DDM', 1), (2095, 'LETTRE', 'LR', 9), (2095, 'MTP', 'PPM_REF', 12), (2094, 'DOSSIER', 'DDP', 2)");
        executerMigrationFlyway("V143__compteur_dossiers_unique.sql");
        org.junit.jupiter.api.Assertions.assertEquals("00005/MAOO-CC/CNM/2095", referenceService.generer("MAOO-CC", "ANT", true, 2095));
        org.junit.jupiter.api.Assertions.assertEquals("00006/PPM/CNM/2095", referenceService.generer("PPM", "ANT", true, 2095));
        org.junit.jupiter.api.Assertions.assertEquals("00003/DAOO/CNM/2094", referenceService.generer("DAOO", "ANT", true, 2094));
        org.junit.jupiter.api.Assertions.assertEquals(4L, jdbcTemplate.queryForObject("select \"DERNIERE_VALEUR\" from t_sequence_reference "
                + "where \"ANNEE_EXERCICE\" = 2095 and \"TYPE_DOSSIER\" = 'DMC'", Long.class));   // l'ancien compteur reste, figé
    }

    @Test
    @DisplayName("Référence dossier — incrément strictement croissant sans saut ni doublon (5 dossiers)")
    void reference_sequence_increment_correct() {
        for (int i = 1; i <= 5; i++) {
            org.junit.jupiter.api.Assertions.assertEquals(String.format("%05d/PPM/CRM-ANT/2098", i),
                    referenceService.generer("PPM", "ANT", false, 2098));
        }
    }

    /** Examen ANT (circuit via réception CTRSEC) sur un dossier à {@code refeDossier} structuré donné. */
    private int seedExamenAvecRefe(int base, String refeDossier) {
        Dossier d = dossier(base, "EXAMINE");
        d.setIdLocalite("ANT");
        d.setRefeDossier(refeDossier);
        dossierRepository.save(d);
        receptionRepository.save(reception(base, base, "CTRSEC", true));    // circuit ANT
        dispatchRepository.save(dispatch(base, base, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(base, base, "CTRMEM"));
        return base;   // idExamen
    }

    @Test
    @DisplayName("Réf. lettre — 2 lettres du MÊME dossier → numéros distincts (plus de répétition)")
    void lettre_reference_sequence_meme_dossier() throws Exception {
        seedExamenAvecRefe(340, "00007/DDP/CRM-ANT/2096");
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":340}"))
                .andExpect(jsonPath("$.refLettre").value("00001/DDP/CRM-ANT/LR/2096"));
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":340}"))
                .andExpect(jsonPath("$.refLettre").value("00002/DDP/CRM-ANT/LR/2096"));
    }

    @Test
    @DisplayName("Réf. lettre — séquence globale (2 dossiers/localités différents → numéros distincts/consécutifs)")
    void lettre_reference_sequence_unique_globale() throws Exception {
        seedExamenAvecRefe(341, "00001/DDP/CRM-ANT/2097");
        seedExamenAvecRefe(342, "00009/DDP/CRM-TMS/2097");   // dossier différent, localité TMS dans la réf
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":341}"))
                .andExpect(jsonPath("$.refLettre").value("00001/DDP/CRM-ANT/LR/2097"));
        // Numéro de séquence GLOBAL (00002) malgré une localité différente — pas un « 00001 » partagé.
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":342}"))
                .andExpect(jsonPath("$.refLettre").value("00002/DDP/CRM-TMS/LR/2097"));
    }

    @Test
    @DisplayName("Réf. lettre — incrément continu sans saut ni doublon (5 lettres)")
    void lettre_reference_increment_continu() throws Exception {
        seedExamenAvecRefe(343, "00001/DDP/CRM-ANT/2098");
        for (int i = 1; i <= 5; i++) {
            mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":343}"))
                    .andExpect(jsonPath("$.refLettre").value(String.format("%05d/DDP/CRM-ANT/LR/2098", i)));
        }
    }

    @Test
    @DisplayName("Réf. lettre — hérite du segment SOUS-TYPE du refeDossier, tiret compris (PPM-AGPM)")
    void lettre_refLettre_heriteSegmentSousType() throws Exception {
        // refeDossier au nouveau format sous-type (segment PPM-AGPM avec tiret) → la lettre le reprend tel quel.
        seedExamenAvecRefe(344, "00013/PPM-AGPM/CRM-ANT/2095");
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":344}"))
                .andExpect(jsonPath("$.refLettre").value("00001/PPM-AGPM/CRM-ANT/LR/2095"));
    }

    @Test
    @DisplayName("Référence réception : localité centrale (utilisateur transversal) -> 00001/PPM/CNM/2026")
    void reference_localite_centrale() throws Exception {
        Dossier d = dossier(300, "SOUMIS"); d.setIdTypeDossier("DDP"); d.setIdSousType("PPM"); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(300, 300, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":300,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRPRE\",\"complet\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("00001/PPM/CNM/2026"));
        // Persistée sur le dossier (REFE_DOSSIER écrasée).
        mvc.perform(get("/api/dossiers/300").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$.refeDossier").value("00001/PPM/CNM/2026"));
    }

    @Test
    @DisplayName("Référence réception (⚠️ règle corrigée 2026-08-04) : dossier régional TMS -> 00001/PPM/CRM-TMS/2026")
    void reference_localite_crm() throws Exception {
        String tokenCcTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        Dossier d = dossier(301, "SOUMIS"); d.setIdTypeDossier("DDP"); d.setIdSousType("PPM"); d.setIdLocalite("TMS");
        dossierRepository.save(d);
        ppmRepository.save(ppm(301, 301, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenCcTms)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":301,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC2\",\"complet\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("00001/PPM/CRM-TMS/2026"));
    }

    @Test
    @DisplayName("Référence réception : compteur auto-incrémenté par la BDD (00001 puis 00002, même contexte)")
    void reference_incrementee_automatiquement() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d1 = dossier(302, "SOUMIS"); d1.setIdTypeDossier("DDP"); d1.setIdSousType("PPM"); d1.setIdLocalite("ANT"); dossierRepository.save(d1);
        Dossier d2 = dossier(303, "SOUMIS"); d2.setIdTypeDossier("DDP"); d2.setIdSousType("PPM"); d2.setIdLocalite("ANT"); dossierRepository.save(d2);
        ppmRepository.save(ppm(302, 302, "PRMP001"));
        ppmRepository.save(ppm(303, 303, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":302,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00001/PPM/CNM/2026"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":303,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00002/PPM/CNM/2026"));
    }

    @Test
    @DisplayName("Référence réception : compteur GLOBAL par année (CNM, CRM-TMS, CNM → 00001, 00002, 00003)")
    void reference_isolee_par_contexte() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        String tokenSecTms = bearer("CTRCC2", ProfilUtilisateur.CHEF_COMMISSION, TypeActeur.CONTROLEUR, "CTRCC2", "TMS");
        Dossier ant = dossier(304, "SOUMIS"); ant.setIdTypeDossier("DDP"); ant.setIdSousType("PPM"); ant.setIdLocalite("ANT"); dossierRepository.save(ant);
        Dossier tms = dossier(305, "SOUMIS"); tms.setIdTypeDossier("DDP"); tms.setIdSousType("PPM"); tms.setIdLocalite("TMS"); dossierRepository.save(tms);
        Dossier cnm = dossier(306, "SOUMIS"); cnm.setIdTypeDossier("DDP"); cnm.setIdSousType("PPM"); cnm.setIdLocalite("ANT"); dossierRepository.save(cnm);
        ppmRepository.save(ppm(304, 304, "PRMP001"));
        ppmRepository.save(ppm(305, 305, "PRMP001"));
        ppmRepository.save(ppm(306, 306, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":304,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00001/PPM/CNM/2026"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenSecTms)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":305,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRCC2\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00002/PPM/CRM-TMS/2026"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":306,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRPRE\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00003/PPM/CNM/2026"));
    }

    @Test
    @DisplayName("Référence réception : segment = SOUS-TYPE verbatim (PPM-AGPM avec tiret) ; compteur continu indexé sur la famille DDP")
    void reference_segmentSousType_verbatim() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Deux dossiers de la MÊME famille DDP mais de sous-types différents (PPM et PPM-AGPM).
        Dossier ppm = dossier(307, "SOUMIS"); ppm.setIdTypeDossier("DDP"); ppm.setIdSousType("PPM"); ppm.setIdLocalite("ANT"); dossierRepository.save(ppm);
        Dossier agpm = dossier(308, "SOUMIS"); agpm.setIdTypeDossier("DDP"); agpm.setIdSousType("PPM-AGPM"); agpm.setIdLocalite("ANT"); dossierRepository.save(agpm);
        ppmRepository.save(ppm(307, 307, "PRMP001"));
        ppmRepository.save(ppm(308, 308, "PRMP001"));

        // 1re réception : segment = sous-type PPM, compteur famille DDP = 00001.
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":307,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00001/PPM/CNM/2026"));
        // 2e : segment PPM-AGPM VERBATIM (tiret conservé) ; le compteur DDP CONTINUE → 00002 (pas 00001).
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":308,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00002/PPM-AGPM/CNM/2026"));
    }

    @Test
    @DisplayName("Référence réception : dossier historique sans sous-type → repli sur la famille (DDP) dans le segment")
    void reference_sansSousType_repliFamille() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Dossier « historique » : famille présente, sous-type absent → segment = famille (repli défensif).
        Dossier d = dossier(309, "SOUMIS"); d.setIdTypeDossier("DDP"); d.setIdSousType(null); d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(309, 309, "PRMP001"));

        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":309,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference", org.hamcrest.Matchers.matchesPattern("\\d{5}/DDP/CNM/\\d{4}")));
    }

    @Test
    @DisplayName("Référence réception : pas de doublon sur 2 réceptions (unicité garantie par l'UPSERT BDD)")
    void reference_concurrence() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        Dossier d1 = dossier(307, "SOUMIS"); d1.setIdTypeDossier("DDP"); d1.setIdLocalite("ANT"); dossierRepository.save(d1);
        Dossier d2 = dossier(308, "SOUMIS"); d2.setIdTypeDossier("DDP"); d2.setIdLocalite("ANT"); dossierRepository.save(d2);
        ppmRepository.save(ppm(307, 307, "PRMP001"));
        ppmRepository.save(ppm(308, 308, "PRMP001"));

        // L'incrément est fait par la BDD (UPSERT atomique, verrou de ligne) : deux réceptions du même
        // contexte obtiennent des valeurs distinctes -> aucun doublon, même sous concurrence réelle.
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":307,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00001/DDP/CNM/2026"));
        mvc.perform(post("/api/receptions").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":308,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                        + "\"imCtrlRecept\":\"CTRSEC\",\"complet\":true}"))
                .andExpect(jsonPath("$.reference").value("00002/DDP/CNM/2026"));
    }

    @Test
    @DisplayName("PV refePv : derivee de refeDossier (.../YYYY -> .../PV/YYYY)")
    void pv_refePv_generee() throws Exception {
        Dossier d = dossier(500, "EXAMINE"); d.setRefeDossier("00003/DDP/CRM-ANT/2026"); dossierRepository.save(d);
        receptionRepository.save(reception(500, 500, "CTRSEC", true));
        dispatchRepository.save(dispatch(500, 500, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(500, 500, "CTRMEM"));

        mvc.perform(post("/api/pv-examens").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":201,\"idExamen\":500,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refePv").value("00003/DDP/CRM-ANT/PV/2026"));
    }

    @Test
    @DisplayName("PV refePv : hérite du segment SOUS-TYPE du refeDossier, tiret compris (PPM-AGPM)")
    void pv_refePv_heriteSegmentSousType() throws Exception {
        // refeDossier au nouveau format sous-type (PPM-AGPM) → refePv insère /PV/ en gardant le segment.
        Dossier d = dossier(502, "EXAMINE"); d.setRefeDossier("00013/PPM-AGPM/CRM-ANT/2026"); dossierRepository.save(d);
        receptionRepository.save(reception(502, 502, "CTRSEC", true));
        dispatchRepository.save(dispatch(502, 502, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(502, 502, "CTRMEM"));

        mvc.perform(post("/api/pv-examens").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":204,\"idExamen\":502,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refePv").value("00013/PPM-AGPM/CRM-ANT/PV/2026"));
    }

    @Test
    @DisplayName("PV refePv unique : deux PV sur le meme dossier -> 409")
    void pv_refePv_unique() throws Exception {
        Dossier d = dossier(501, "EXAMINE"); d.setRefeDossier("00007/DDP/CRM-ANT/2026"); dossierRepository.save(d);
        receptionRepository.save(reception(501, 501, "CTRSEC", true));
        dispatchRepository.save(dispatch(501, 501, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(501, 501, "CTRMEM"));

        mvc.perform(post("/api/pv-examens").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":202,\"idExamen\":501,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":203,\"idExamen\":501,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Saisie PPM — reference auto 00001/DGB/PPM/2026 (acronyme du libelle entite)")
    void ppm_reference_generee() throws Exception {
        EntiteContract e = entite(700, 1, "ANT"); e.setLibelleEntite("Direction Générale du Budget");
        entiteContractRepository.save(e);
        prmpEntiteRepository.save(prmpEntite(700, "PRMP001", 700, true));
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":700,\"exercice\":2026,\"dateSignature\":\"2026-01-10\",\"marches\":[]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        // Le brouillon n'apparaît plus dans la liste « Mes PPM & marchés » : on le lit par son id (propriétaire).
        int idPpm = ppmRepository.findByIdDossier(idDoss).get(0).getIdPpm();
        mvc.perform(get("/api/ppms/" + idPpm).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.reference").value("00001/DGB/PPM/2026"));
    }

    @Test
    @DisplayName("Saisie PPM — reference incrementee 00002 sur 2e PPM meme entite/annee")
    void ppm_reference_incrementee() throws Exception {
        EntiteContract e = entite(701, 1, "ANT"); e.setLibelleEntite("Direction Générale du Budget");
        entiteContractRepository.save(e);
        prmpEntiteRepository.save(prmpEntite(701, "PRMP001", 701, true));
        String body = "{\"idEntiteContract\":701,\"exercice\":2026,\"dateSignature\":\"2026-01-10\",\"marches\":[]}";
        int d1 = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString(), "$.idDossier");
        int d2 = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString(), "$.idDossier");
        int p1 = ppmRepository.findByIdDossier(d1).get(0).getIdPpm();
        int p2 = ppmRepository.findByIdDossier(d2).get(0).getIdPpm();
        mvc.perform(get("/api/ppms/" + p1).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.reference").value("00001/DGB/PPM/2026"));
        mvc.perform(get("/api/ppms/" + p2).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.reference").value("00002/DGB/PPM/2026"));
    }

    @Test
    @DisplayName("Saisie PPM — compteur isole par entite : DRT -> 00001/DRT/PPM/2026")
    void ppm_reference_isolee() throws Exception {
        EntiteContract e = entite(702, 1, "ANT"); e.setLibelleEntite("Direction Régionale des Travaux");
        entiteContractRepository.save(e);
        prmpEntiteRepository.save(prmpEntite(702, "PRMP001", 702, true));
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":702,\"exercice\":2026,\"dateSignature\":\"2026-01-10\",\"marches\":[]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int idPpm = ppmRepository.findByIdDossier(idDoss).get(0).getIdPpm();
        mvc.perform(get("/api/ppms/" + idPpm).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.reference").value("00001/DRT/PPM/2026"));
    }

    @Test
    @DisplayName("Lettre de renvoi — refLettre au format {seqLettreGlobal}/{type}/{code_localite}/LR/{annee}")
    void lettre_ref_format_ok() throws Exception {
        // refeDossier structuré → refLettre reprend type/localité/année mais avec le compteur GLOBAL des
        // lettres (00001 pour la 1ère), pas le numéro du dossier (00007).
        Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setRefeDossier("00007/DDP/CRM-ANT/2026");
        dossierRepository.save(d);
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1,\"objetLettre\":\"Renvoi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refLettre").value("00001/DDP/CRM-ANT/LR/2026"));
    }
}
