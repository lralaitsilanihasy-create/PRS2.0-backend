package cnm.prs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Dossier;
import java.util.List;
import cnm.prs.entity.Examen;
import cnm.prs.entity.ExamenDetail;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Prmp;
import cnm.prs.entity.Ugpm;
import cnm.prs.entity.PrmpEntiteDemande;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Authentification, comptes et habilitations : login (resolution du role, de la localite et du nom
 * d'affichage), mots de passe, inscription des PRMP, cookie de session HttpOnly, en-tetes de
 * securite, journal d'audit, gestion des comptes (module 10), autorisations par profil et
 * delegation ascendante (les neuf paires officielles).
 */
class AuthentificationHabilitationIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Réinitialisation Admin : l'Admin force un nouveau mot de passe")
    void adminReinitialiseMotDePasse() throws Exception {
        String body = "{\"nouveauMotDePasse\":\"Reinit#2026\"}";
        // Un non-admin ne peut pas réinitialiser → 403.
        mvc.perform(post("/api/comptes-auth/CTRMEM/reinitialiser-mot-de-passe").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // L'Admin réinitialise le mot de passe de CTRMEM.
        mvc.perform(post("/api/comptes-auth/CTRMEM/reinitialiser-mot-de-passe").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        // CTRMEM se connecte avec le nouveau mot de passe.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRMEM\",\"motDePasse\":\"Reinit#2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBRE"));
        // Compte inexistant → 404.
        mvc.perform(post("/api/comptes-auth/INCONNU/reinitialiser-mot-de-passe").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Changer son mot de passe : nouveau mdp actif, ancien rejeté, garde du mdp actuel")
    void changerMotDePasse() throws Exception {
        // Changement réussi (le mot de passe seedé est « pw »).
        mvc.perform(post("/api/mon-compte/changer-mot-de-passe").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ancienMotDePasse\":\"pw\",\"nouveauMotDePasse\":\"Nouveau#2026\"}"))
                .andExpect(status().isOk());

        // Connexion avec le nouveau mot de passe → OK.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRMEM\",\"motDePasse\":\"Nouveau#2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MEMBRE"));

        // Connexion avec l'ancien mot de passe → refusée.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRMEM\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isUnauthorized());

        // Mauvais mot de passe actuel → 400.
        mvc.perform(post("/api/mon-compte/changer-mot-de-passe").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ancienMotDePasse\":\"FAUX\",\"nouveauMotDePasse\":\"Encore#2026\"}"))
                .andExpect(status().isBadRequest());

        // Sans jeton → 401.
        mvc.perform(post("/api/mon-compte/changer-mot-de-passe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ancienMotDePasse\":\"pw\",\"nouveauMotDePasse\":\"Autre#2026\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Changement de mot de passe du Membre, avec l'ancien « pw » du seed. */
    private org.springframework.test.web.servlet.ResultActions changerPour(String nouveau) throws Exception {
        return mvc.perform(post("/api/mon-compte/changer-mot-de-passe").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ancienMotDePasse\":\"pw\",\"nouveauMotDePasse\":\"" + nouveau + "\"}"));
    }

    @Test
    @DisplayName("⚠️ Audit lot E — politique de mot de passe : 8 caractères dont une lettre ET un chiffre, "
            + "sur les NOUVEAUX mots de passe seulement (changement, réinitialisation Admin, inscription) ; "
            + "l'ancien mot de passe n'y est jamais soumis")
    void politiqueMotDePasse_nouveauxSeulement() throws Exception {
        // Sans chiffre → 400, et le message dit ce qui manque, sur le bon champ.
        changerPour("motdepassesanschiffre")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='nouveauMotDePasse')].message",
                        hasItem(containsString("au moins une lettre et un chiffre"))));
        // Sans lettre → 400.
        changerPour("12345678").andExpect(status().isBadRequest());
        // Trop court, meme avec lettre ET chiffre → 400 (la borne de 8 tient toujours).
        changerPour("Abc123").andExpect(status().isBadRequest());

        // ⚠️ Le cœur de la regle : l'ANCIEN mot de passe n'est PAS soumis a la politique.
        // « pw » (2 caracteres, seede avant cette regle) reste accepte comme preuve d'identite —
        // sinon plus aucun compte existant ne pourrait changer son mot de passe.
        changerPour("Conforme2026").andExpect(status().isOk());

        // Reinitialisation par l'Administrateur : meme regle.
        mvc.perform(post("/api/comptes-auth/CTRMEM/reinitialiser-mot-de-passe").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nouveauMotDePasse\":\"sanschiffre\"}"))
                .andExpect(status().isBadRequest());

        // Inscription publique : meme regle, avant toute creation de fiche.
        mvc.perform(post("/api/auth/register/prmp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.faible\",\"motDePasse\":\"sanschiffre\",\"idPrmp\":\"PRMP960\","
                        + "\"nomPrmp\":\"Rakoto\",\"prenomsPrmp\":\"Faible\","
                        + "\"arreteNomin\":\"ARR-2026-960\",\"dateNomin\":\"2026-01-01\",\"cin\":\"960960960960\","
                        + "\"dateCin\":\"2010-01-01\",\"lieuCin\":\"Antananarivo\","
                        + "\"emailPrmp\":\"faible@prmp.mg\",\"telPrmp\":\"0340000960\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(compteAuthRepository.findByLogin("prmp.faible").isEmpty(),
                "aucun compte ne doit etre cree par une inscription au mot de passe refuse");

        // ⚠️ Le login n'est PAS contraint : les comptes anterieurs a la politique doivent
        // continuer a se connecter avec leur mot de passe faible.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRCC1\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Auto-inscription PRMP : compte inactif → activation Admin → connexion")
    void autoInscriptionPrmp_validationAdmin() throws Exception {
        String inscription = "{"
                + "\"login\":\"prmp.new\",\"motDePasse\":\"Passw0rd!\",\"idPrmp\":\"PRMP777\","
                + "\"nomPrmp\":\"Rakoto\",\"prenomsPrmp\":\"Nouvelle\","
                + "\"arreteNomin\":\"ARR-2026-777\",\"dateNomin\":\"2026-01-01\",\"cin\":\"101010101010\","
                + "\"dateCin\":\"2010-01-01\",\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"new@prmp.mg\","
                + "\"telPrmp\":\"0340000000\"}";

        // Inscription publique (sans jeton) → 201, compte inactif.
        mvc.perform(post("/api/auth/register/prmp").contentType(MediaType.APPLICATION_JSON).content(inscription))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actif").value(false))
                .andExpect(jsonPath("$.typeActeur").value("PRMP"));

        // L'Administrateur est notifié de l'inscription en attente.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVELLE_INSCRIPTION')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVELLE_INSCRIPTION')].destinataireIm", hasItem("CTRADM")));

        // Connexion refusée tant que le compte n'est pas validé → 401.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.new\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isUnauthorized());

        // Un non-administrateur ne peut pas activer → 403.
        mvc.perform(post("/api/comptes-auth/prmp.new/activer").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // L'Administrateur valide le compte.
        mvc.perform(post("/api/comptes-auth/prmp.new/activer").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true));

        // La connexion fonctionne désormais, avec le rôle PRMP.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.new\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PRMP"));

        // Réinscription avec le même login → 409.
        mvc.perform(post("/api/auth/register/prmp").contentType(MediaType.APPLICATION_JSON).content(inscription))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Login : le rôle et la localité sont déduits du profil")
    void login_resoutRoleEtLocalite() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRCC1\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CHEF_COMMISSION"))
                .andExpect(jsonPath("$.localite").value("ANT"))
                // ⚠️ Phase 3 du plan cookie : le jeton ne sort plus dans le corps — cookie seul.
                .andExpect(jsonPath("$.token").value(nullValue()));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRPRE\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRESIDENT"))
                .andExpect(jsonPath("$.localite").doesNotExist());

        // La PRMP n'a plus de localité propre : la claim localite est absente de sa réponse de connexion.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"PRMP001\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRMP"))
                .andExpect(jsonPath("$.localite").doesNotExist());
    }

    @Test
    @DisplayName("Login : nomAffichage « Nom Prénoms » résolu serveur pour les 3 types d'acteur — dont l'UGPM, "
            + "dont le « ref » désigne la tutelle et la fiche est fermée en lecture")
    void login_nomAffichage_tousTypesActeur() throws Exception {
        // Contrôleur (vaut pour tous les rôles CNM, Administrateur compris).
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRCC1\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomAffichage").value("NomCTRCC1 Prenoms"));

        // PRMP.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"PRMP001\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomAffichage").value("Nom Prenoms"));

        // UGPM : « ref » porte la PRMP de tutelle (périmètre), le nom vient de nomAffichage.
        Ugpm u = new Ugpm();
        u.setIdUgpm("UGPM002");
        u.setLibelle("UGPM du ministère");
        u.setIdPrmpTutelle("PRMP001");
        u.setNomUgpm("Rakoto");
        u.setPrenomsUgpm("Jean Claude");
        u.setCin("202022223333");
        u.setDateCin(LocalDate.of(2011, 6, 6));
        u.setLieuCin("Antananarivo");
        u.setEmailUgpm("ugpm002@min.mg");
        u.setTelUgpm("0330000002");
        ugpmRepository.save(u);
        compteAuthRepository.save(new CompteAuth("UGPM002", passwordEncoder.encode("pw"), "UGPM", "UGPM002", true));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"UGPM002\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("UGPM"))
                .andExpect(jsonPath("$.nomAffichage").value("Rakoto Jean Claude"))
                .andExpect(jsonPath("$.ref").value("PRMP001"));   // périmètre = tutelle, inchangé
    }

    @Test
    @DisplayName("Login : fiche sans nom → nomAffichage retombe sur le login (jamais vide côté front)")
    void login_nomAffichage_repliSurLogin() throws Exception {
        Prmp sansNom = prmp("PRMP950", "ANT");
        sansNom.setNomPrmp("");
        sansNom.setPrenomsPrmp("");
        prmpRepository.save(sansNom);
        compteAuthRepository.save(new CompteAuth("prmp.anon", passwordEncoder.encode("pw"), "PRMP", "PRMP950", true));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.anon\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomAffichage").value("prmp.anon"));
    }

    @Test
    @DisplayName("Login : mauvais mot de passe → 401")
    void login_mauvaisMotDePasse() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"CTRCC1\",\"motDePasse\":\"faux\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Endpoint protégé sans token → 401")
    void endpointProtege_sansToken() throws Exception {
        mvc.perform(get("/api/dossiers")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Référentiel public d'entités : accessible SANS jeton (écran d'inscription)")
    void entitesPubliques_sansToken() throws Exception {
        mvc.perform(get("/api/auth/entites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idEntiteContract==1)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idEntiteContract==1)].idLocalite", hasItem("ANT")));
    }

    @Test
    @DisplayName("Inscription PRMP v2 (multipart) : compte EN_ATTENTE + déclarations + pièces ; ≥1 entité requise")
    void inscriptionV2_multipart() throws Exception {
        String data = "{\"login\":\"prmp.v2\",\"motDePasse\":\"Passw0rd!\",\"idPrmp\":\"PRMP900\","
                + "\"nomPrmp\":\"Rakoto\",\"prenomsPrmp\":\"V2\","
                + "\"arreteNomin\":\"ARR-2026-900\",\"dateNomin\":\"2026-01-01\",\"cin\":\"909090909090\","
                + "\"dateCin\":\"2010-01-01\",\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"v2@prmp.mg\","
                + "\"telPrmp\":\"0340000900\",\"idEntites\":[1],"
                + "\"entitesNonListees\":[{\"libelle\":\"Nouvelle Autorite\",\"adresse\":\"Adr\",\"idLocalite\":\"ANT\"}]}";
        MockMultipartFile dataPart = new MockMultipartFile("data", "", "application/json",
                data.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile arrete = new MockMultipartFile("arrete", "arrete.pdf", "application/pdf",
                "%PDF-1.4 arrete".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile cin = new MockMultipartFile("cin", "cin.png", "image/png",
                new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 });

        // Inscription multipart → 201, compte EN_ATTENTE.
        mvc.perform(multipart("/api/auth/register/prmp").file(dataPart).file(arrete).file(cin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.actif").value(false));

        // 2 déclarations (1 existante + 1 proposée) et 2 pièces (arrêté + CIN) enregistrées.
        assertTrue(prmpEntiteDemandeRepository.findByLogin("prmp.v2").size() == 2, "2 déclarations d'entités");
        assertTrue(pieceJointeRepository.findByLogin("prmp.v2").size() == 2, "2 pièces (arrêté + CIN)");

        // L'Administrateur est notifié de l'inscription.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='NOUVELLE_INSCRIPTION')]", hasSize(1)));

        // Connexion refusée tant que non validée → 401.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.v2\",\"motDePasse\":\"Passw0rd!\"}"))
                .andExpect(status().isUnauthorized());

        // Aucune entité déclarée (ni existante ni proposée) → 400.
        String sansEntite = "{\"login\":\"prmp.v3\",\"motDePasse\":\"Passw0rd!\",\"idPrmp\":\"PRMP901\","
                + "\"nomPrmp\":\"Rakoto\",\"prenomsPrmp\":\"V3\","
                + "\"arreteNomin\":\"ARR-2026-901\",\"dateNomin\":\"2026-01-01\",\"cin\":\"901901901901\","
                + "\"dateCin\":\"2010-01-01\",\"lieuCin\":\"Antananarivo\",\"emailPrmp\":\"v3@prmp.mg\","
                + "\"telPrmp\":\"0340000901\",\"idEntites\":[],\"entitesNonListees\":[]}";
        MockMultipartFile dataSansEntite = new MockMultipartFile("data", "", "application/json",
                sansEntite.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/auth/register/prmp").file(dataSansEntite).file(arrete).file(cin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Validation inscription : partielle (entité libre activée, conflit signalé, proposée créée) → ACTIF + login")
    void inscription_validationPartielle() throws Exception {
        entiteContractRepository.save(entite(5, 1, "ANT")); // entité libre
        prmpRepository.save(prmp("PRMP900", "ANT"));
        compteAuthRepository.save(new CompteAuth("prmp.val", passwordEncoder.encode("pw"), "PRMP", "PRMP900", false));
        // Déclarations en attente : existante libre (5), existante déjà prise (1 = PRMP001 dans le seed), proposée.
        prmpEntiteDemandeRepository.save(demande(9001, "prmp.val", 5, null));
        prmpEntiteDemandeRepository.save(demande(9002, "prmp.val", 1, null));
        prmpEntiteDemandeRepository.save(demande(9003, "prmp.val", null, "Nouvelle Autorite"));

        // Lecture réservée à l'Admin.
        mvc.perform(get("/api/inscriptions/en-attente").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/inscriptions/en-attente").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.login=='prmp.val')]", hasSize(1)));

        // Validation : on accepte l'entité proposée (9003) avec un organigramme existant (1).
        String body = "{\"entitesProposees\":[{\"idDemande\":9003,\"accepter\":true,\"idOrganigramme\":1}]}";
        mvc.perform(post("/api/inscriptions/prmp.val/valider").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutCompte").value("ACTIF"))
                .andExpect(jsonPath("$.validees.length()").value(2))    // entité 5 + entité proposée créée
                .andExpect(jsonPath("$.conflits.length()").value(1));   // entité 1 déjà rattachée

        // Compte activé → login OK (rôle PRMP, sans localité).
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.val\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRMP"))
                .andExpect(jsonPath("$.localite").doesNotExist());

        // 2 affectations actives pour PRMP900 (entité 5 + entité proposée).
        assertTrue(prmpEntiteRepository.findByIdPrmpAndActifTrue("PRMP900").size() == 2, "2 affectations actives");
    }

    @Test
    @DisplayName("Refus inscription : REFUSE + motif, login refusé, réservé Admin")
    void inscription_refus() throws Exception {
        prmpRepository.save(prmp("PRMP901", "ANT"));
        compteAuthRepository.save(new CompteAuth("prmp.ref", passwordEncoder.encode("pw"), "PRMP", "PRMP901", false));
        prmpEntiteDemandeRepository.save(demande(9100, "prmp.ref", 1, null));
        String body = "{\"motif\":\"Arrêté de nomination non conforme\"}";

        // Refus réservé à l'Admin.
        mvc.perform(post("/api/inscriptions/prmp.ref/refuser").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // L'Admin refuse → 204.
        mvc.perform(post("/api/inscriptions/prmp.ref/refuser").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        // Login toujours refusé (compte non activé).
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"prmp.ref\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Positionne le contexte de sécurité sur un JWT du profil donné (test direct de la garde centrale). */
    private void authentifierProfil(ProfilUtilisateur profil) {
        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
                .withTokenValue("test").header("alg", "HS256").subject("test")
                .claim("role", profil.name()).build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt));
    }

    @Test
    @DisplayName("Délégation ascendante — matrice complète : titulaire + les 9 paires de la table, rien d'autre")
    void delegation_matricePaires() {
        java.util.List<ProfilUtilisateur> hierarchie = java.util.List.of(
                ProfilUtilisateur.PRESIDENT, ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.CHEF_COMMISSION,
                ProfilUtilisateur.MEMBRE, ProfilUtilisateur.VERIFICATEUR, ProfilUtilisateur.ASSISTANT_CONTROLEUR);
        java.util.Map<ProfilUtilisateur, java.util.Set<ProfilUtilisateur>> paires = java.util.Map.of(
                ProfilUtilisateur.PRESIDENT, java.util.Set.of(
                        ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.CHEF_COMMISSION, ProfilUtilisateur.MEMBRE,
                        ProfilUtilisateur.VERIFICATEUR, ProfilUtilisateur.ASSISTANT_CONTROLEUR),
                ProfilUtilisateur.CHEF_COMMISSION, java.util.Set.of(
                        ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.MEMBRE,
                        ProfilUtilisateur.VERIFICATEUR, ProfilUtilisateur.ASSISTANT_CONTROLEUR));
        java.util.List<ProfilUtilisateur> tousProfils = java.util.List.of(
                ProfilUtilisateur.PRESIDENT, ProfilUtilisateur.SECRETAIRE, ProfilUtilisateur.CHEF_COMMISSION,
                ProfilUtilisateur.MEMBRE, ProfilUtilisateur.VERIFICATEUR, ProfilUtilisateur.ASSISTANT_CONTROLEUR,
                ProfilUtilisateur.PRMP, ProfilUtilisateur.CHARGE_PUBLICATION, ProfilUtilisateur.ADMINISTRATEUR);
        try {
            // Chaque profil exerce SES tâches ; Président les 5 subordonnés ; CC les 4 ; PERSONNE d'autre
            // (négatifs : Secrétaire, Membre, Vérificateur, Assistant, PRMP, Chargé de publication,
            // Administrateur n'exercent la tâche d'aucun autre) — aucune paire hors table.
            for (ProfilUtilisateur courant : tousProfils) {
                authentifierProfil(courant);
                for (ProfilUtilisateur cible : hierarchie) {
                    boolean attendu = courant == cible
                            || paires.getOrDefault(courant, java.util.Set.of()).contains(cible);
                    org.junit.jupiter.api.Assertions.assertEquals(attendu,
                            permissionService.peutExercer(cible.name()), courant + " -> " + cible);
                }
            }
            // ⚠️ Anti-régression : LE cas qu'un modèle de rang casserait — le CC est SOUS le Secrétaire
            // dans la hiérarchie (Président > Secrétaire > CC > ...) mais hérite de ses droits parce que
            // la paire CC → Secrétaire est LISTÉE dans la table. Jamais de comparaison de rangs.
            authentifierProfil(ProfilUtilisateur.CHEF_COMMISSION);
            assertTrue(permissionService.peutExercer("SECRETAIRE"));
            // Non transitif et pas de réciprocité de rang : le Secrétaire (au-dessus du CC) n'exerce rien d'autre.
            authentifierProfil(ProfilUtilisateur.SECRETAIRE);
            assertFalse(permissionService.peutExercer("CHEF_COMMISSION"));
            assertFalse(permissionService.peutExercer("MEMBRE"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Délégation — recette : désactiver une paire (actif=false) retire l'habilitation sans changement "
            + "de code ; la réactiver la rend")
    void delegation_recette_toggleActif() throws Exception {
        // Président crée une lettre de renvoi — tâche du CC, exercée via la paire 7 (Président → CC).
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isCreated());
        // L'Admin DÉSACTIVE la paire → l'habilitation disparaît (403), sans aucun changement de code.
        mvc.perform(put("/api/delegation-profils/7").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":7,\"idProfileDelegant\":2,\"idProfileDelegue\":3,\"actif\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isForbidden());
        // Le CC titulaire reste habilité (la désactivation ne touche que la paire Président → CC).
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isCreated());
        // RÉACTIVATION → l'habilitation revient.
        mvc.perform(put("/api/delegation-profils/7").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":7,\"idProfileDelegant\":2,\"idProfileDelegue\":3,\"actif\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/lettre-renvois").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idExamen\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Délégation — unicité : une seule ligne par paire (délégant, délégué), doublon rejeté")
    void delegation_unicitePaire() {
        // La paire Président (2) → Secrétaire (4) existe déjà (id 1) : un doublon viole UQ_DELEGATION_PAIRE.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> delegationProfilRepository.saveAndFlush(delegation(90, 2, 4)));
    }

    @Test
    @DisplayName("Délégation ascendante — auto-attribution du Président (circuit court) : dispatch à soi-même (201) "
            + "et examen par lui-même, mais signature IMPOSSIBLE seul (⚠️ arbitrage du pilote 2026-08-28 : "
            + "l'auto-co-signature est abolie) — il désigne un Membre de la localité, qui co-signe")
    void delegation_autoAttributionPresident_circuitCourt() throws Exception {
        dossierRepository.save(dossier(4601, "PRET_DISPATCH"));
        receptionRepository.save(reception(5601, 4601, "CTRSEC", true)); // ANT
        // Le Président se dispatche le dossier à LUI-MÊME : attributaire couvert par la paire
        // Président → Membre (active) → 201. Le CC d'ANT est associé automatiquement.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5601,\"idReception\":5601,\"imCtrlMembre\":\"CTRPRE\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRPRE"));
        // Il examine lui-même (examen réservé à l'attributaire — c'est lui, §2.4) puis soumet.
        mvc.perform(post("/api/examens").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":5601,\"idDispatch\":5601,\"imCtrlMembre\":\"CTRPRE\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/examens/5601/soumettre").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idAvis\":\"FAV\"}"))
                .andExpect(status().isCreated());
        // Projet de PV : l'attributaire est DÉRIVÉ du dispatch (= CTRPRE), navette classique via le CC.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":5601,\"idExamen\":5601,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRPRE\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRPRE"));
        mvc.perform(post("/api/pv-examens/5601/soumettre").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        // ⚠️ VISA (2026-08-31) — c'est le Président qui a dispatché ce dossier (à lui-même) : lui seul
        // vise. Le CC, qui clôturait la navette jusqu'ici, n'y a plus accès — contrainte d'identité.
        viser(5601, tokenCc, "CTRCC1", "FAV", "CTRVER", "CTRMEM").andExpect(status().isBadRequest());
        // Il ne peut pas se désigner lui-même : l'auto-co-signature reste abolie, sans exception.
        viser(5601, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRPRE").andExpect(status().isConflict());
        // Ni désigner un contrôleur qui n'est pas Membre de la localité du dossier (§3.3).
        viser(5601, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRVER").andExpect(status().isConflict());
        // La part MEMBRE n'est ouverte pour personne avant le visa, pas même pour l'attributaire (ordre B).
        mvc.perform(post("/api/pv-examens/5601/signer").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRPRE\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isConflict());
        // Il vise : avis, secrétaire, désignation d'un Membre d'ANT et sa propre part, en un geste.
        viser(5601, tokenPresident, "CTRPRE", "FAV", "CTRVER", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("PROJET_ACCEPTE"))
                .andExpect(jsonPath("$.imCtrlPresident").value("CTRPRE"))
                .andExpect(jsonPath("$.imDispatcheur").value("CTRPRE"))
                .andExpect(jsonPath("$.imMembreCoSignataire").value("CTRMEM"));
        // Le Membre désigné co-signe → SIGNE. ⚠️ IM_CTRL_MEMBRE reste CTRPRE : c'est lui qui a EXAMINÉ
        // le dossier, et c'est ce nom que le PV officiel imprime. La co-signature ne l'écrase pas.
        String tokenMembreAnt = bearer("CTRMEM", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM", "ANT");
        mvc.perform(post("/api/pv-examens/5601/signer").header("Authorization", tokenMembreAnt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRPRE"))
                .andExpect(jsonPath("$.imMembreCoSignataire").value("CTRMEM"));
    }

    @Test
    @DisplayName("INTERIM_DISPATCH : titulaire dans sa localité (false), intérim hors localité (true)")
    void interimDispatch_conditionnel() throws Exception {
        // Les gardes du dispatch exigent un dossier PRET_DISPATCH et une réception sans dispatch.
        // On prépare des dossiers PRET_DISPATCH avec une réception dédiée chacun (ANT et TMS).
        dossierRepository.save(dossier(10, "PRET_DISPATCH"));
        dossierRepository.save(dossier(11, "PRET_DISPATCH"));
        dossierRepository.save(dossier(12, "PRET_DISPATCH"));
        dossierRepository.save(dossier(13, "PRET_DISPATCH"));
        receptionRepository.save(reception(20, 10, "CTRSEC", true)); // ANT (CTRSEC = localité ANT)
        receptionRepository.save(reception(21, 11, "CTRSEC", true)); // ANT
        receptionRepository.save(reception(22, 12, "CTRCC2", true)); // TMS (CTRCC2 = localité TMS)
        receptionRepository.save(reception(23, 13, "CTRCC2", true)); // TMS

        // Cas conformes (CC d'ANT) — réceptions fraîches.
        // Dossier d'ANT en titulaire.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":30,\"idReception\":20,\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        // Dossier de TMS en intérim.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":31,\"idReception\":22,\"interimDispatch\":true}"))
                .andExpect(status().isCreated());

        // Cas non conformes (409) — la précondition passe (réceptions fraîches PRET_DISPATCH),
        // c'est la règle d'intérim qui bloque.
        // CC dans sa localité mais marqué intérim.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":32,\"idReception\":21,\"interimDispatch\":true}"))
                .andExpect(status().isConflict());
        // CC hors de sa localité mais marqué titulaire.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":33,\"idReception\":23,\"interimDispatch\":false}"))
                .andExpect(status().isConflict());
        // Le Président dispatche toujours en titulaire.
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenPresident).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":34,\"idReception\":21,\"interimDispatch\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Audit automatique : une écriture API est tracée dans t_audit_log (§3.8)")
    void audit_traceLesEcritures() throws Exception {
        // ⚠️ LOT 3b (2026-08-26) — même correction que referentiel_ecritureAdminSeulement : « TMS »
        // existe déjà dans les fixtures, sa création répond maintenant 409. Ce qui est tracé ici,
        // c'est une écriture RÉUSSIE : elle doit donc porter sur une localité réellement nouvelle.
        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLocalite\":\"FIA\",\"libelleLocalite\":\"Fianarantsoa\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nomTable").value("localites"))
                .andExpect(jsonPath("$[0].typeAction").value("CREATE"))
                .andExpect(jsonPath("$[0].imActeur").value("CTRADM"));
    }

    @Test
    @DisplayName("⚠️ Recette 2026-08-27 — la sous-action tracée est LISIBLE : « CHANGER-MOT-DE-PASSE », plus « CHANGER-MO »")
    void audit_sousActionNonTronqueeA10() throws Exception {
        // L'intercepteur tronquait TYPE_ACTION à 10 caractères en dur alors que la colonne est
        // passée à varchar(30) : le journal ne disait plus QUELLE action avait eu lieu.
        mvc.perform(post("/api/mon-compte/changer-mot-de-passe").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ancienMotDePasse\":\"pw\",\"nouveauMotDePasse\":\"Nouveau#2026\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nomTable").value("mon-compte"))
                .andExpect(jsonPath("$[0].typeAction").value("CHANGER-MOT-DE-PASSE"))
                .andExpect(jsonPath("$[0].imActeur").value("CTRMEM"));
    }

    @Test
    @DisplayName("Journal d'audit §3.8 — ajout seul : POST (entrée forgée), PUT (réécriture) et DELETE répondent 409 même à l'Administrateur ; la trace reste intacte")
    void audit_journalImmuable_troisVerbesRefuses() throws Exception {
        // Seule voie d'alimentation légitime : l'intercepteur, après une écriture API réussie.
        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLocalite\":\"FIA\",\"libelleLocalite\":\"Fianarantsoa\"}"))
                .andExpect(status().isCreated());
        String json = mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        long idLog = ((Number) com.jayway.jsonpath.JsonPath.parse(json).read("$[0].idLog")).longValue();

        // POST — entrée FORGÉE (avant le correctif : 201, on écrivait ce qu'on voulait dans la preuve).
        mvc.perform(post("/api/audit-logs").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dateAction\":\"2020-01-01T00:00:00\",\"imActeur\":\"CTRADM\","
                        + "\"nomTable\":\"dossiers\",\"idEnregistrement\":\"1\",\"typeAction\":\"DELETE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("immuable")));

        // PUT — RÉÉCRITURE de la trace existante : date, acteur, table, IP (avant le correctif : 200).
        mvc.perform(put("/api/audit-logs/" + idLog).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idLog\":" + idLog + ",\"dateAction\":\"2020-01-01T00:00:00\",\"imActeur\":\"AUTRE\","
                        + "\"nomTable\":\"autre\",\"typeAction\":\"READ\",\"ipAdresse\":\"1.2.3.4\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("immuable")));

        // DELETE — comportement d'origine, conservé.
        mvc.perform(delete("/api/audit-logs/" + idLog).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());

        // Rien n'a été ajouté, rien n'a été réécrit : le journal est exactement ce qu'il était.
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nomTable").value("localites"))
                .andExpect(jsonPath("$[0].typeAction").value("CREATE"))
                .andExpect(jsonPath("$[0].imActeur").value("CTRADM"));
    }

    @Test
    @DisplayName("Module 10 : écriture comptes/hiérarchie réservée Admin, lecture ouverte, sessions Admin-only")
    void module10_gestionComptes() throws Exception {
        // Création d'un contrôleur interdite au Membre (403), avant même la validation.
        mvc.perform(post("/api/controleurs").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imControleur\":\"CTRX\",\"idProfile\":5,\"transversal\":false}"))
                .andExpect(status().isForbidden());
        // Lecture des contrôleurs ouverte (l'UI affiche les noms).
        mvc.perform(get("/api/controleurs").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
        // Sessions utilisateur : réservées à l'Administrateur (lecture comprise).
        mvc.perform(get("/api/session-utilisateurs").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/session-utilisateurs").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Délégation : tâche déléguée exécutable par le titulaire ou un profil délégué, sinon 403")
    void delegation_tachesDelegables() throws Exception {
        String body = "{\"idDossier\":1,\"numPassage\":1,\"typePassage\":\"INITIAL\","
                + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":false}";
        // Secrétaire titulaire : autorisé.
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        mvc.perform(put("/api/receptions/1").header("Authorization", tokenSec)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        // CC délégué (délégation Secrétaire → CC active) : autorisé.
        mvc.perform(put("/api/receptions/1").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        // Membre sans délégation pour la réception : interdit.
        mvc.perform(put("/api/receptions/1").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Délégation limitée à la localité : agir sur un dossier d'une autre localité → 403")
    void delegation_contrainteLocalite() throws Exception {
        // Le Président (toutes localités) peut agir sur le dossier 2 (TMS).
        mvc.perform(post("/api/receptions").header("Authorization", tokenPresident)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":2,\"numPassage\":2,\"typePassage\":\"RETOUR\","
                        + "\"imCtrlRecept\":\"CTRPRE\",\"complet\":false}"))
                .andExpect(status().isCreated());
        // Le CC d'ANT, même délégué, ne peut pas agir sur un dossier de TMS → 403.
        mvc.perform(post("/api/receptions").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":2,\"numPassage\":2,\"typePassage\":\"RETOUR\","
                        + "\"imCtrlRecept\":\"CTRCC1\",\"complet\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Circuit court CC : auto-désignation Secrétaire de séance (paire active ; désactivée → 409) ; "
            + "⚠️ 2026-08-28 — le CC ne signe PLUS seul : part Membre refusée avant désignation (409), sa part CC "
            + "reste gouvernée par la paire → Membre (désactivée → 403), puis le Membre désigné co-signe → SIGNE ; "
            + "passage statué sur SES PROPRES observations")
    void circuitCourtCc_secretaireSeanceParDelegation_etPassageParAttributaire() throws Exception {
        // Dossier ANT auto-attribué par le CC (garde attributaire : paire CC → Membre active).
        // Enrichi PPM + ligne de marché : support du diff de rectification (2026-08-15).
        Dossier d4610 = dossierLoc(4610, "PRET_DISPATCH", "ANT", "PRMP001");
        d4610.setIdTypeDossier("DDP");
        dossierRepository.save(d4610);
        ppmRepository.save(ppm(4610, 4610, "PRMP001"));
        natureRepository.save(new Nature(1, "Travaux", null));
        Marche m4610 = marche(46100, 4610, 4610);
        m4610.setIdLigneOrigine(46100);
        m4610.setMontEstim(new java.math.BigDecimal("500000000"));
        m4610.setIdNature(1);
        m4610.setFormeMarche(cnm.prs.enums.FormeMarche.QUANTITE_FIXE);
        marcheRepository.save(m4610);
        receptionRepository.save(reception(5610, 4610, "CTRSEC", true));
        mvc.perform(post("/api/dispatchs").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDispatch\":5610,\"idReception\":5610,\"imCtrlMembre\":\"CTRCC1\",\"interimDispatch\":false}"))
                .andExpect(status().isCreated());
        // Examen par le CC attributaire, avec un point NON CONFORME (source des observations du PV FAVR).
        mvc.perform(post("/api/examens").header("Authorization", tokenCc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":5610,\"idDispatch\":5610,\"imCtrlMembre\":\"CTRCC1\"}"))
                .andExpect(status().isCreated());
        if (!pointsCtrlRepository.existsById(990)) {
            PointsCtrl pc = new PointsCtrl();
            pc.setIdPointCtrl(990); pc.setLibelPointCtrl("Contrôle test"); pc.setObligatoire(true);
            pc.setIdTypeDossier("DDP");
            pointsCtrlRepository.save(pc);
        }
        ExamenDetail nonConforme = new ExamenDetail();
        nonConforme.setIdDetailExamen(991); nonConforme.setIdExamen(5610); nonConforme.setIdPtControle(990);
        nonConforme.setIdDetail(46100);   // point évalué SUR la ligne de marché (complétude par marché)
        nonConforme.setConforme(false);
        examenDetailRepository.save(nonConforme);
        String pvBody = mvc.perform(post("/api/examens/5610/soumettre").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idAvis\":\"FAVR\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int idPv = com.jayway.jsonpath.JsonPath.read(pvBody, "$.idPv");
        // Navette : le CC soumet SON projet puis l'accepte lui-même (accepteur = auteur — circuit court réel).
        mvc.perform(post("/api/pv-examens/" + idPv + "/soumettre").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        // ⚠️ VISA (2026-08-31) — les gardes du Secrétaire de séance sont reconduites telles quelles, mais
        // portées par « viser » au lieu de « accepter ». Ici le CC EST le dispatcheur (il s'est dispatché
        // le dossier), la contrainte d'identité est donc satisfaite et ce sont bien les gardes §3.3 que
        // ces cas éprouvent.
        // Négatifs : un Secrétaire (aucune paire → Vérificateur) reste refusé…
        viser(idPv, tokenCc, "CTRCC1", "FAVR", "CTRSEC", "CTRMEM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("délégation active vers Vérificateur")));
        // … tout comme un CC d'une AUTRE localité (paire active mais hors périmètre : CTRCC2 = TMS, dossier ANT).
        viser(idPv, tokenCc, "CTRCC1", "FAVR", "CTRCC2", "CTRMEM").andExpect(status().isConflict());
        // Data-driven : paire 6 (CC → Vérificateur) DÉSACTIVÉE → l'auto-désignation du CC est refusée.
        mvc.perform(put("/api/delegation-profils/6").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":6,\"idProfileDelegant\":3,\"idProfileDelegue\":6,\"actif\":false}"))
                .andExpect(status().isOk());
        viser(idPv, tokenCc, "CTRCC1", "FAVR", "CTRCC1", "CTRMEM").andExpect(status().isConflict());
        // RÉACTIVÉE → le CC SE DÉSIGNE LUI-MÊME Secrétaire de séance (décision produit : « moi-même ⤴ »),
        // et le visa passe : il désigne CTRMEM pour co-signer, sa propre part étant posée du même geste.
        mvc.perform(put("/api/delegation-profils/6").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDelegation\":6,\"idProfileDelegant\":3,\"idProfileDelegue\":6,\"actif\":true}"))
                .andExpect(status().isOk());
        viser(idPv, tokenCc, "CTRCC1", "FAVR", "CTRCC1", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSecretaireSeance").value("CTRCC1"))
                .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"))
                .andExpect(jsonPath("$.imDispatcheur").value("CTRCC1"))
                .andExpect(jsonPath("$.imMembreCoSignataire").value("CTRMEM"));
        // ⚠️ 2026-08-31 — la part CC est sortie de « signer » : elle appartient au visa, déjà posé. 409.
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imActeur\":\"CTRCC1\",\"role\":\"CC\",\"imMembreCoSignataire\":\"CTRMEM\"}"))
                .andExpect(status().isConflict());
        // ⚠️ 2026-08-28 — le CC attributaire ne prend pas non plus la part Membre : il a examiné, mais le
        // désigné est CTRMEM. 403, l'étape étant ouverte et lui n'étant pas le désigné.
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRCC1\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isForbidden());
        // Le Membre désigné co-signe → SIGNE. IM_CTRL_MEMBRE reste CTRCC1, qui a mené l'examen.
        String tokenMembreAnt2 = bearer("CTRMEM", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "CTRMEM", "ANT");
        mvc.perform(post("/api/pv-examens/" + idPv + "/signer").header("Authorization", tokenMembreAnt2)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"role\":\"MEMBRE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutPv").value("SIGNE"))
                .andExpect(jsonPath("$.imCtrlCc").value("CTRCC1"))
                .andExpect(jsonPath("$.imCtrlMembre").value("CTRCC1"));
        mvc.perform(get("/api/dossiers/4610").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("EN_VERIFICATION"));
        // Q1a : le MÊME CC (attributaire, auteur des observations) statue le passage via la paire CC → Vérificateur
        // — tâche de PROFIL, aucune restriction au Secrétaire de séance ni garde de séparation.
        // ⚠️ Décision produit 2026-08-15 : au PREMIER passage la levée est impossible (les observations
        // du PV sont réputées avec objet) — leveePossible=false au front, LEVEE → 409, tout MAINTENUE.
        String obs = mvc.perform(get("/api/observations-pv").header("Authorization", tokenCc).param("dossier", "4610"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leveePossible").value(false))
                .andReturn().getResponse().getContentAsString();
        int idObs = com.jayway.jsonpath.JsonPath.read(obs, "$[0].idObservationPv");
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":4610,\"decisions\":[{\"idObservationPv\":" + idObs + ",\"decision\":\"LEVEE\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("première rectification")));
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":4610,\"decisions\":[{\"idObservationPv\":" + idObs
                        + ",\"decision\":\"MAINTENUE\",\"precision\":\"a rectifier\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/4610").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_DECISION_PRMP"));
        // Diff de rectification (2026-08-15) — avant toute correction : aucun instantané → 409.
        mvc.perform(get("/api/dossiers/4610/diff-rectification").header("Authorization", tokenCc))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Aucune rectification")));
        // La PRMP corrige EN PLACE (structure figée, mise à jour par idDetail). Le PREMIER PUT du cycle
        // fige l'état pré-correction ; le second ne re-fige pas (le diff compare toujours à l'AVANT).
        String entete = "{\"exercice\":2026,\"signataire\":\"PRMP Test\",\"dateSignature\":\"2026-06-01\","
                + "\"reference\":\"PPM-4610\",\"marches\":[{\"idDetail\":46100,\"formeMarche\":\"QUANTITE_FIXE\","
                + "\"montEstim\":500000000,\"idNature\":1,\"statut\":\"PREVU\",\"designationMarche\":\"";
        mvc.perform(put("/api/saisies/ppm/4610").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(entete + "Marche 46100 rectifie\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/saisies/ppm/4610").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(entete + "Marche 46100 rectifie v2\"}]}"))
                .andExpect(status().isOk());
        // Le CC (vérificateur par délégation) voit CE QUE LA PRMP A CHANGÉ : ligne MODIFIEE,
        // designation avant → après (comparée à l'état d'AVANT la première correction), cycle non clos.
        mvc.perform(get("/api/dossiers/4610/diff-rectification").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fige").value(false))
                .andExpect(jsonPath("$.recap.modifiees").value(1))
                .andExpect(jsonPath("$.lignes[0].type").value("MODIFIEE"))
                .andExpect(jsonPath("$.lignes[0].champs[?(@.champ=='designationMarche')].avant",
                        hasItem("Marche 46100")))
                .andExpect(jsonPath("$.lignes[0].champs[?(@.champ=='designationMarche')].apres",
                        hasItem("Marche 46100 rectifie v2")));
        // La PRMP rectifie et resoumet → la levée devient possible (leveePossible=true au front).
        mvc.perform(post("/api/dossiers/4610/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"corrige\"}"))
                .andExpect(status().isOk());
        // Après resoumission, le diff du cycle CLOS reste servi (fige=true, motif de la rectification).
        mvc.perform(get("/api/dossiers/4610/diff-rectification").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fige").value(true))
                .andExpect(jsonPath("$.motifMaj").value("corrige"));
        mvc.perform(get("/api/observations-pv").header("Authorization", tokenCc).param("dossier", "4610"))
                .andExpect(jsonPath("$[0].leveePossible").value(true));
        mvc.perform(post("/api/observations-pv/passage").header("Authorization", tokenCc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":4610,\"decisions\":[{\"idObservationPv\":" + idObs + ",\"decision\":\"LEVEE\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/4610").header("Authorization", tokenCc))
                .andExpect(jsonPath("$.statut").value("OBSERVATIONS_LEVEES"));
    }

    @Test
    @DisplayName("Cookie de session HttpOnly (plan phases 1-3, 2026-08-17) : login pose PRS_SESSION SANS jeton "
            + "dans le corps (phase 3) ; l'API accepte le cookie seul ; mutation cookie-seul sans XSRF → 403, "
            + "avec XSRF → garde CSRF passée (409 métier) ; Bearer inchangé ; logout vide le cookie")
    void cookieSession_phase3() throws Exception {
        // 1) Login PRMP : cookie de session posé (HttpOnly, SameSite=Strict) ; ⚠️ phase 3 — le corps
        // ne porte PLUS le jeton (token: null), le profil d'affichage reste servi.
        org.springframework.mock.web.MockHttpServletResponse login = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"PRMP001\",\"motDePasse\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(nullValue()))
                .andExpect(jsonPath("$.role").value("PRMP"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andReturn().getResponse();
        String poseCookie = login.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("PRS_SESSION=")).findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(poseCookie, "le login doit poser PRS_SESSION");
        assertTrue(poseCookie.contains("HttpOnly"), "cookie HttpOnly attendu");
        assertTrue(poseCookie.contains("SameSite=Strict"), "SameSite=Strict attendu");
        // Le JWT ne circule plus que dans le cookie : on l'extrait du Set-Cookie.
        String jeton = poseCookie.substring("PRS_SESSION=".length(), poseCookie.indexOf(';'));
        assertTrue(!jeton.isBlank(), "le cookie doit porter le JWT");
        jakarta.servlet.http.Cookie session = new jakarta.servlet.http.Cookie("PRS_SESSION", jeton);

        // 2) Lecture authentifiée par COOKIE SEUL (aucun en-tête Authorization) + XSRF-TOKEN posé.
        org.springframework.mock.web.MockHttpServletResponse lecture =
                mvc.perform(get("/api/kpis/badges").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profil").value("PRMP"))
                .andReturn().getResponse();
        jakarta.servlet.http.Cookie xsrf = lecture.getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(xsrf,
                "le cookie XSRF-TOKEN doit être posé dès la première réponse (chargement immédiat)");

        // 3) Mutation cookie-seul SANS X-XSRF-TOKEN → 403 (garde CSRF, ciblée sur le canal cookie).
        // ⚠️ Recette 2026-08-27 — sur HTTP RÉEL, le client voit un 401 au corps vide, pas ce 403 : le
        // sendError de la garde déclenche un ré-aiguillage ERROR du conteneur vers /error, où le filtre
        // d'authentification (une-fois-par-requête) ne rejoue pas — le point d'entrée écrase le 403 par
        // un 401. MockMvc ne rejoue pas ce ré-aiguillage, d'où le 403 nu observé ici. C'est bien la
        // garde qui est éprouvée dans les deux cas ; le contrat rendu au client est documenté dans
        // docs/api-endpoints.md (§ Authentification, encadré « à lire avant de scripter au curl »).
        mvc.perform(post("/api/dossiers/1/resoumettre").cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"x\"}"))
                .andExpect(status().isForbidden());
        // 4) La même avec le jeton XSRF → la garde CSRF passe : échec MÉTIER (409, dossier non EN_ATTENTE).
        mvc.perform(post("/api/dossiers/1/resoumettre")
                .cookie(session, new jakarta.servlet.http.Cookie("XSRF-TOKEN", xsrf.getValue()))
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"x\"}"))
                .andExpect(status().isConflict());
        // 5) Bearer : exempté de CSRF, comportement inchangé (même 409 métier, sans jeton XSRF).
        mvc.perform(post("/api/dossiers/1/resoumettre").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRectification\":\"x\"}"))
                .andExpect(status().isConflict());
        // 6) Logout (route publique) : le cookie de session est vidé (Max-Age=0).
        org.springframework.mock.web.MockHttpServletResponse logout = mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        String suppression = logout.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("PRS_SESSION=")).findFirst().orElse(null);
        org.junit.jupiter.api.Assertions.assertNotNull(suppression, "le logout doit vider PRS_SESSION");
        assertTrue(suppression.contains("Max-Age=0"), "suppression du cookie (Max-Age=0) attendue");
    }

    @Test
    @DisplayName("En-têtes de sécurité (audit front 2026-08-16) : nosniff + CSP posés sur les réponses de l'API")
    void securite_headers() throws Exception {
        mvc.perform(get("/api/dossiers/1").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; object-src 'none'; frame-ancestors 'self'"));
    }

    @Test
    @DisplayName("Secrétaire de séance par délégation : le Président (sans localité, paire Président → Vérificateur "
            + "active) est désignable sur un dossier de n'importe quelle localité")
    void secretaireSeance_presidentParDelegation() throws Exception {
        // PV sur l'examen 1 (dossier 1, ANT, attributaire CTRMEM), soumis par le Membre.
        mvc.perform(post("/api/pv-examens").header("Authorization", tokenMembre).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPv\":5611,\"idExamen\":1,\"idAvis\":\"FAV\",\"imCtrlMembre\":\"CTRMEM\","
                        + "\"statutPv\":\"BROUILLON\",\"nbNavettes\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pv-examens/5611/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"imActeur\":\"CTRMEM\",\"commentaire\":\"go\"}"))
                .andExpect(status().isOk());
        // ⚠️ 2026-08-31 — le visa désigne le PRÉSIDENT Secrétaire de séance : couvert par la paire
        // Président → Vérificateur active, et sans localité → désignable partout. La garde §3.3 est
        // inchangée ; seul l'acteur l'est, le dispatcheur (CTRPRE) ayant remplacé le CC.
        viser(5611, tokenPresident, "CTRPRE", "FAV", "CTRPRE", "CTRMEM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSecretaireSeance").value("CTRPRE"));
    }

    @Test
    @DisplayName("Audit : un acteur dont la ref fait 8 à 10 caractères (« PRMP00001 ») est journalisé — IM_ACTEUR non null "
            + "(la colonne est en varchar(10) depuis la migration 2026-06-19)")
    void audit_refLongue_imActeurNonNull() throws Exception {
        // ⚠️ Correctif 2026-08-26 — le filtre de l'intercepteur était resté à 7 caractères alors que
        // IM_ACTEUR a été élargie à varchar(10) précisément pour porter un id PRMP (t_prmp.ID_PRMP).
        prmpRepository.save(prmp("PRMP00001", "ANT"));
        categorieEntiteRepository.save(new cnm.prs.entity.CategorieEntite("DIRECTION", 4));
        String tokenPrmpLong = bearer("PRMP00001", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP00001", "ANT");

        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenPrmpLong)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":300,\"libelleEntite\":\"Direction Longue\",\"adresse\":\"Rue Z\","
                        + "\"categorieEntite\":\"DIRECTION\",\"idOrganigramme\":1}"))
                .andExpect(status().isCreated());

        List<cnm.prs.entity.AuditLog> traces = auditLogRepository.findAll().stream()
                .filter(l -> "entite-contracts".equals(l.getNomTable())).toList();
        org.junit.jupiter.api.Assertions.assertEquals(1, traces.size());
        org.junit.jupiter.api.Assertions.assertEquals("PRMP00001", traces.get(0).getImActeur());
    }

    @Test
    @DisplayName("Jeton au rôle inconnu : CurrentUser.profil() honore son contrat (vide) — les lectures scopées "
            + "répondent 200 avec une liste vide, jamais 500")
    void currentUser_roleInconnu_aucuneErreurServeur() throws Exception {
        // ⚠️ Correctif 2026-08-26 — ProfilUtilisateur.valueOf() était appelé nu : un rôle inconnu levait
        // IllegalArgumentException, rendue en 500 par le handler générique, là où le contrat promet « vide ».
        String tokenInconnu = "Bearer " + tokenService.generer("inconnu", "INCONNU", TypeActeur.CONTROLEUR, "CTRX", null);
        for (String url : List.of("/api/dossiers", "/api/ppms", "/api/marches")) {
            mvc.perform(get(url).header("Authorization", tokenInconnu))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    private PrmpEntiteDemande demande(int id, String login, Integer idEntite, String libellePropose) {
        PrmpEntiteDemande d = new PrmpEntiteDemande();
        d.setIdDemande(id);
        d.setLogin(login);
        d.setIdEntiteContract(idEntite);
        if (libellePropose != null) {
            d.setLibellePropose(libellePropose);
            d.setAdressePropose("Adresse proposée");
            d.setIdLocalitePropose("ANT");
        }
        d.setStatutDemande("EN_ATTENTE");
        d.setDateDeclaration(LocalDate.of(2026, 1, 1));
        return d;
    }
}
