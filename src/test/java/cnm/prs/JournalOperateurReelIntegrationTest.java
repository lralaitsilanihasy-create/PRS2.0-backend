package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.ActionDossier;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Dossier;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;
import cnm.prs.repository.ActionDossierRepository;
import cnm.prs.service.JournalDossierService;

/**
 * ⚠️ <strong>La CRÉATION revient à son auteur réel</strong> (signalement pilote du 2026-09-08, dossier
 * 00305 — {@code docs/demande-backend-2026-09-08-journal-creation-operateur-ugpm.md}).
 *
 * <p>Un dossier saisi par une <strong>UGPM</strong> puis soumis par sa PRMP affichait la CRÉATION au nom
 * de la <em>PRMP</em>. À l'écriture, l'opérateur d'une action est la PRMP en fonction — pour un agent
 * UGPM, sa PRMP de tutelle : c'est ce qui donne son sens au couple opérateur/mandat. Mais la création
 * n'est pas un acte de traitement sous mandat, c'est une saisie, et le dossier sait qui l'a faite
 * ({@code CREE_PAR}). Le journal contredisait donc le {@code DossierDto}, qui servait déjà le bon nom.</p>
 *
 * <p><strong>2ᵉ tour (arbitrages du pilote, même jour)</strong> : la règle vaut pour <strong>tous</strong>
 * les gestes — une soumission ou une resoumission faite par l'UGPM porte son nom, pas celui de sa
 * tutelle — et les noms s'écrivent dans <strong>une seule convention</strong>, « NOM Prénoms ». Les trois
 * annuaires en servaient deux : la même personne changeait d'ordre d'une ligne à l'autre du même tableau.</p>
 *
 * <p>Ce que ces tests protègent : le <strong>nom de l'UGPM</strong> sur la CRÉATION puis sur
 * <strong>tous</strong> ses gestes, la <strong>SOUMISSION laissée à la PRMP</strong> quand c'est elle qui
 * soumet, l'<strong>ordre uniforme</strong>, la <strong>rétroactivité</strong> (dérivé à la lecture, donc
 * les lignes déjà écrites se corrigent seules), et le <strong>repli</strong> qui ne remplace jamais un nom
 * connu par un identifiant brut.</p>
 */
class JournalOperateurReelIntegrationTest extends CnmIntegrationTestSupport {

    /** Nom lisible attendu pour l'UGPM créatrice : « NOM Prénoms », comme {@code creeParNom}. */
    private static final String NOM_UGPM = "RALAITSILANIHASY Lantonirina Annick";
    /** Nom de la PRMP dans la convention CANONIQUE « NOM Prénoms » — la seule du journal depuis le 08/09. */
    private static final String NOM_PRMP = "Nom Prenoms";
    /** Le MÊME nom dans l'ancien ordre « Prénoms Nom », tel que les lignes déjà écrites le portent. */
    private static final String NOM_PRMP_ANCIEN_ORDRE = "Prenoms Nom";

    @Autowired
    private ActionDossierRepository actionDossierRepository;

    /** Une UGPM sous la tutelle de PRMP001, avec son compte : c'est elle qui saisit. */
    @BeforeEach
    void ugpmSousTutelle() {
        ugpmRepository.save(ugpm("UGPM002", "PRMP001", "RALAITSILANIHASY", "Lantonirina Annick"));
        compteAuthRepository.save(
                new CompteAuth("ugpm.annick", passwordEncoder.encode("pw"), "UGPM", "UGPM002", true));
    }

    private String tokenUgpm() {
        // ⚠️ ref = PRMP001 : un agent UGPM agit sous la ref de sa PRMP de tutelle. C'est exactement ce
        // qui faisait nommer la PRMP dans le journal.
        return bearer("ugpm.annick", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "PRMP001", null);
    }

    private int dossierCreeParLUgpm() throws Exception {
        String reponse = mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenUgpm())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DAO\",\"idEntiteContract\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.idDossier");
    }

    // ------------------------------------------------------------------ 1. la création porte l'UGPM

    @Test
    @DisplayName("Recette — un dossier saisi par l'UGPM porte SON nom sur la ligne CREATION, pas celui de "
            + "sa PRMP de tutelle ; le journal dit alors exactement ce que dit creeParNom")
    void creationParUneUgpm_leJournalNommeLUgpm() throws Exception {
        int idDossier = dossierCreeParLUgpm();
        assertThat(dossierRepository.findById(idDossier).orElseThrow().getCreePar()).isEqualTo("ugpm.annick");

        mvc.perform(get("/api/dossiers/" + idDossier + "/journal").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].nomOperateur", hasItem(NOM_UGPM)))
                // L'auteur consigné est le login créateur : la ligne le sert désormais tel quel.
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].auteur", hasItem("ugpm.annick")))
                // ⚠️ idPrmpOperateur reste la PRMP de TUTELLE : l'UGPM a saisi sous son autorité. Y mettre
                // l'UGPM allumerait le marqueur « opérateur ≠ attributaire » du front — un contresens.
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].idPrmpOperateur", hasItem("PRMP001")));

        // Cohérence journal ↔ dossier : la même personne, écrite à l'identique des deux côtés.
        mvc.perform(get("/api/dossiers/" + idDossier).header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$.creePar").value("ugpm.annick"))
                .andExpect(jsonPath("$.creeParNom").value(NOM_UGPM));
    }

    // ------------------------------------------------------------------ 2. la soumission reste à la PRMP

    @Test
    @DisplayName("Recette — l'UGPM crée, la PRMP soumet : la CREATION porte l'UGPM et la SOUMISSION la "
            + "PRMP ; les deux lignes ne se confondent plus")
    void ugpmCree_prmpSoumet_deuxOperateursDistincts() throws Exception {
        int idDossier = dossierCreeParLUgpm();
        mvc.perform(post("/api/dossiers/" + idDossier + "/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        mvc.perform(get("/api/dossiers/" + idDossier + "/journal").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].nomOperateur", hasItem(NOM_UGPM)))
                .andExpect(jsonPath("$[?(@.typeAction=='SOUMISSION')].nomOperateur", hasItem(NOM_PRMP)))
                .andExpect(jsonPath("$[?(@.typeAction=='SOUMISSION')].auteur", hasItem("PRMP001")));
    }

    // ------------------------------------------------------------------ 3. rétroactif

    @Test
    @DisplayName("RÉTROACTIF — une ligne CREATION écrite AVANT le correctif (nom de la PRMP en base) se "
            + "corrige à la relecture : la dérivation ne demande aucune reprise de données")
    void ligneEcriteAvantLeCorrectif_seCorrigeALaRelecture() throws Exception {
        Dossier d = dossierLoc(910, "BROUILLON", "ANT", "PRMP001");
        d.setIdTypeDossier("DMC");
        d.setCreePar("ugpm.annick");
        dossierRepository.save(d);
        // Telle que la ligne a été écrite : opérateur = PRMP de tutelle, nom = celui de la PRMP.
        ActionDossier ancienne = new ActionDossier();
        ancienne.setIdDossier(910);
        ancienne.setDateAction(LocalDateTime.now().minusDays(3));
        ancienne.setTypeAction(JournalDossierService.CREATION);
        ancienne.setIdPrmpOperateur("PRMP001");
        ancienne.setNomOperateur(NOM_PRMP_ANCIEN_ORDRE);
        ancienne.setAuteur("ugpm.annick");
        actionDossierRepository.save(ancienne);

        mvc.perform(get("/api/dossiers/910/journal").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].nomOperateur", hasItem(NOM_UGPM)));
        // Rien n'a été réécrit en base : c'est la LECTURE qui corrige.
        assertThat(actionDossierRepository.findById(ancienne.getIdAction()).orElseThrow().getNomOperateur())
                .isEqualTo(NOM_PRMP_ANCIEN_ORDRE);
    }

    // ------------------------------------------------------------------ 4. replis

    @Test
    @DisplayName("REPLIS — sans CREE_PAR (dossier d'avant la colonne) le nom stocké est conservé, et un "
            + "créateur inconnu de l'annuaire ne remplace jamais un nom par un login brut")
    void sansCreateurConnu_leNomStockeEstConserve() throws Exception {
        Dossier d = dossierLoc(911, "BROUILLON", "ANT", "PRMP001");
        d.setIdTypeDossier("DMC");
        d.setCreePar(null);   // dossier antérieur à la traçabilité du créateur
        dossierRepository.save(d);
        ActionDossier sansCreateur = new ActionDossier();
        sansCreateur.setIdDossier(911);
        sansCreateur.setDateAction(LocalDateTime.now().minusDays(5));
        sansCreateur.setTypeAction(JournalDossierService.CREATION);
        sansCreateur.setIdPrmpOperateur("PRMP001");
        sansCreateur.setNomOperateur(NOM_PRMP_ANCIEN_ORDRE);
        sansCreateur.setAuteur(null);
        actionDossierRepository.save(sansCreateur);

        mvc.perform(get("/api/dossiers/911/journal").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].nomOperateur", hasItem(NOM_PRMP_ANCIEN_ORDRE)));

        // Créateur consigné mais absent de l'annuaire (compte supprimé) : le nom connu tient.
        Dossier e = dossierLoc(912, "BROUILLON", "ANT", "PRMP001");
        e.setIdTypeDossier("DMC");
        e.setCreePar("compte.efface");
        dossierRepository.save(e);
        ActionDossier inconnue = new ActionDossier();
        inconnue.setIdDossier(912);
        inconnue.setDateAction(LocalDateTime.now().minusDays(5));
        inconnue.setTypeAction(JournalDossierService.CREATION);
        inconnue.setIdPrmpOperateur("PRMP001");
        inconnue.setNomOperateur(NOM_PRMP_ANCIEN_ORDRE);
        inconnue.setAuteur("compte.efface");
        actionDossierRepository.save(inconnue);

        mvc.perform(get("/api/dossiers/912/journal").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].nomOperateur", hasItem(NOM_PRMP_ANCIEN_ORDRE)));
    }

    // ------------------------------------------------------------------ 5. 2ᵉ tour : TOUS les gestes

    @Test
    @DisplayName("Arbitrage ② — la règle ne vaut pas que pour la CRÉATION : toute ligne dont l'auteur est "
            + "une UGPM porte SON nom, quel que soit le type d'action (ici RESOUMISSION et MISE_A_JOUR)")
    void tousLesGestesDeLUgpm_portentSonNom() throws Exception {
        int idDossier = dossierCreeParLUgpm();
        // ⚠️ Constat de livraison (2026-09-08) : l'API ne permet PAS aujourd'hui à une UGPM de soumettre
        // ou resoumettre — /soumettre, /resoumettre et /transmettre-complements* exigent le rôle PRMP
        // (403). La création est son seul geste consigné accessible. La règle est donc éprouvée ici sur
        // la FORME des lignes, telles qu'elles existeraient (ou existent déjà) : c'est l'auteur consigné
        // qui décide du nom, pas le type de l'action.
        mvc.perform(post("/api/dossiers/" + idDossier + "/soumettre").header("Authorization", tokenUgpm()))
                .andExpect(status().isForbidden());

        for (String type : new String[] { JournalDossierService.RESOUMISSION,
                JournalDossierService.MISE_A_JOUR }) {
            ActionDossier ligne = new ActionDossier();
            ligne.setIdDossier(idDossier);
            ligne.setDateAction(LocalDateTime.now());
            ligne.setTypeAction(type);
            ligne.setIdPrmpOperateur("PRMP001");
            ligne.setNomOperateur(NOM_PRMP_ANCIEN_ORDRE);   // ce que l'ancien code écrivait
            ligne.setAuteur("ugpm.annick");                 // ce que l'auteur réel était déjà
            actionDossierRepository.save(ligne);
        }

        mvc.perform(get("/api/dossiers/" + idDossier + "/journal").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].nomOperateur", hasItem(NOM_UGPM)))
                .andExpect(jsonPath("$[?(@.typeAction=='RESOUMISSION')].nomOperateur", hasItem(NOM_UGPM)))
                .andExpect(jsonPath("$[?(@.typeAction=='MISE_A_JOUR')].nomOperateur", hasItem(NOM_UGPM)))
                // Le rattachement à la tutelle ne bouge pas : le marqueur « opérateur ≠ attributaire »
                // du front ne doit pas s'allumer parce qu'une UGPM a agi pour SA PRMP.
                .andExpect(jsonPath("$[?(@.typeAction=='RESOUMISSION')].idPrmpOperateur", hasItem("PRMP001")));
    }

    // ------------------------------------------------------------------ 6. 2ᵉ tour : ordre uniforme

    @Test
    @DisplayName("Arbitrage ① — une seule convention « NOM Prénoms » sur TOUTES les lignes : la même "
            + "personne ne change plus d'ordre d'une ligne à l'autre, et l'ancien ordre est corrigé à la relecture")
    void ordreDesNoms_uniformeSurToutesLesLignes() throws Exception {
        // Créé ET soumis par la PRMP : les deux lignes nomment la même personne — elles doivent
        // désormais l'écrire pareil. Avant : « Nom Prenoms » à la création, « Prenoms Nom » à la soumission.
        String reponse = mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DAO\",\"idEntiteContract\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idDossier = JsonPath.read(reponse, "$.idDossier");
        mvc.perform(post("/api/dossiers/" + idDossier + "/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());

        String journal = mvc.perform(get("/api/dossiers/" + idDossier + "/journal")
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].nomOperateur", hasItem(NOM_PRMP)))
                .andExpect(jsonPath("$[?(@.typeAction=='SOUMISSION')].nomOperateur", hasItem(NOM_PRMP)))
                .andReturn().getResponse().getContentAsString();
        // Aucune ligne ne porte plus l'ancien ordre.
        java.util.List<String> noms = JsonPath.read(journal, "$[*].nomOperateur");
        assertThat(noms).isNotEmpty().doesNotContain(NOM_PRMP_ANCIEN_ORDRE);

        // RÉTROACTIF sur l'ordre : une ligne écrite « Prénoms Nom » se relit « NOM Prénoms ».
        ActionDossier ancienOrdre = new ActionDossier();
        ancienOrdre.setIdDossier(idDossier);
        ancienOrdre.setDateAction(LocalDateTime.now());
        ancienOrdre.setTypeAction(JournalDossierService.TRANSMISSION_COMPLEMENTS);
        ancienOrdre.setIdPrmpOperateur("PRMP001");
        ancienOrdre.setNomOperateur(NOM_PRMP_ANCIEN_ORDRE);
        ancienOrdre.setAuteur("PRMP001");
        actionDossierRepository.save(ancienOrdre);

        mvc.perform(get("/api/dossiers/" + idDossier + "/journal").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.typeAction=='TRANSMISSION_COMPLEMENTS')].nomOperateur",
                        hasItem(NOM_PRMP)));
    }
}
