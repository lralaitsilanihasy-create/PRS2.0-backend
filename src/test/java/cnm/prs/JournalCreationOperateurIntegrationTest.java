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
 * <p>Ce que ces tests protègent : le <strong>nom de l'UGPM</strong> sur la CRÉATION, la
 * <strong>SOUMISSION laissée à la PRMP</strong>, la <strong>rétroactivité</strong> (dérivé à la lecture,
 * donc les dossiers déjà créés se corrigent seuls), et le <strong>repli</strong> qui ne remplace jamais
 * un nom connu par un login brut.</p>
 */
class JournalCreationOperateurIntegrationTest extends CnmIntegrationTestSupport {

    /** Nom lisible attendu pour l'UGPM créatrice : « NOM Prénoms », comme {@code creeParNom}. */
    private static final String NOM_UGPM = "RALAITSILANIHASY Lantonirina Annick";
    /** Nom de la PRMP tel que le journal le compose depuis l'annuaire des PRMP (« Prénoms Nom »). */
    private static final String NOM_PRMP = "Prenoms Nom";

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
        ancienne.setNomOperateur(NOM_PRMP);
        ancienne.setAuteur("ugpm.annick");
        actionDossierRepository.save(ancienne);

        mvc.perform(get("/api/dossiers/910/journal").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].nomOperateur", hasItem(NOM_UGPM)));
        // Rien n'a été réécrit en base : c'est la LECTURE qui corrige.
        assertThat(actionDossierRepository.findById(ancienne.getIdAction()).orElseThrow().getNomOperateur())
                .isEqualTo(NOM_PRMP);
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
        sansCreateur.setNomOperateur(NOM_PRMP);
        sansCreateur.setAuteur(null);
        actionDossierRepository.save(sansCreateur);

        mvc.perform(get("/api/dossiers/911/journal").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].nomOperateur", hasItem(NOM_PRMP)));

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
        inconnue.setNomOperateur(NOM_PRMP);
        inconnue.setAuteur("compte.efface");
        actionDossierRepository.save(inconnue);

        mvc.perform(get("/api/dossiers/912/journal").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.typeAction=='CREATION')].nomOperateur", hasItem(NOM_PRMP)));
    }
}
