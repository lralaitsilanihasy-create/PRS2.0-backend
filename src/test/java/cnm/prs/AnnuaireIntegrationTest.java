package cnm.prs;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Controleur;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * ⚠️ Lot 6 (2026-09-17, demande front « espace d'administration » §B2 et §B3) — l'annuaire des
 * personnes : {@code GET /api/annuaire}, recherche unifiée (contrôleurs, PRMP, UGPM), et
 * {@code GET /api/annuaire/{type}/{ref}}, la fiche de la maquette C. Tout est réservé à
 * l'Administrateur.
 *
 * <p>Jeu de départ du socle : 9 contrôleurs (dont 6 en localité ANT, 1 en TMS, 2 sans localité),
 * 1 PRMP (PRMP001, deux entités actives), aucune UGPM, et des comptes pour CTRPRE, CTRCC1, CTRMEM,
 * CTRADM et PRMP001 seulement — les autres contrôleurs sont donc {@code SANS_COMPTE}, ce qui fait du
 * socle le cas limite que la demande réclame de couvrir.</p>
 */
class AnnuaireIntegrationTest extends CnmIntegrationTestSupport {

    // ------------------------------------------------------------------
    // Accès : écran d'administration, rien de moins
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Annuaire : 401 anonyme, 403 pour tout profil autre qu'Administrateur, 200 pour lui")
    void annuaire_reserveALAdministrateur() throws Exception {
        mvc.perform(get("/api/annuaire")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/annuaire").header("Authorization", tokenPresident))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization", tokenPublication))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization",
                        bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization",
                        bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization",
                        bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire").header("Authorization",
                        bearer("UGPM001", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "UGPM001", "ANT")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Contenu
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Annuaire : les trois populations dans une seule page, chacune avec son type")
    void annuaire_troisPopulations() throws Exception {
        ugpmRepository.save(ugpm("UGPM001", "PRMP001", "Randria", "Hanta"));

        String page = mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("page", "0").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(11))   // 9 contrôleurs + 1 PRMP + 1 UGPM
                .andReturn().getResponse().getContentAsString();

        List<String> types = JsonPath.read(page, "$.content[*].type");
        Assertions.assertEquals(9, types.stream().filter("CONTROLEUR"::equals).count());
        Assertions.assertEquals(1, types.stream().filter("PRMP"::equals).count());
        Assertions.assertEquals(1, types.stream().filter("UGPM"::equals).count());
    }

    @Test
    @DisplayName("Annuaire : une personne sans compte sort avec login null et statutCompte SANS_COMPTE")
    void annuaire_personneSansCompte() throws Exception {
        // CTRVER n'a aucun compte dans le socle ; CTRADM en a un, actif.
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "CTRVER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRVER"))
                .andExpect(jsonPath("$.content[0].login").doesNotExist())
                .andExpect(jsonPath("$.content[0].statutCompte").value("SANS_COMPTE"))
                .andExpect(jsonPath("$.content[0].profil").value("VERIFICATEUR"))
                .andExpect(jsonPath("$.content[0].localite").value("ANT"));

        String sansCompte = mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("statut", "SANS_COMPTE").param("size", "50"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> refs = JsonPath.read(sansCompte, "$.content[*].ref");
        Assertions.assertTrue(refs.contains("CTRVER"), "le contrôleur sans compte est dans la file SANS_COMPTE");
        Assertions.assertFalse(refs.contains("CTRADM"), "un contrôleur pourvu d'un compte n'y est pas");
    }

    @Test
    @DisplayName("Annuaire : les cinq états — actif, suspendu, refusé, en attente, sans compte")
    void annuaire_cinqStatuts() throws Exception {
        CompteAuth suspendu = compteAuthRepository.findByLogin("CTRMEM").orElseThrow();
        suspendu.setActif(false);         // suspension Administrateur : le STATUT reste ACTIF
        compteAuthRepository.save(suspendu);
        ugpmRepository.save(ugpm("UGPM001", "PRMP001", "Randria", "Hanta"));
        CompteAuth enAttente = new CompteAuth("ugpm.att", "x", "UGPM", "UGPM001", false);
        enAttente.setStatut("EN_ATTENTE");
        compteAuthRepository.save(enAttente);
        // Inscription refusée : STATUT = REFUSE et son motif — ce n'est PAS un compte suspendu.
        CompteAuth refuse = new CompteAuth("ctrver.ref", "x", "CONTROLEUR", "CTRVER", false);
        refuse.setStatut("REFUSE");
        refuse.setMotifRefus("Arrêté non conforme.");
        compteAuthRepository.save(refuse);

        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "CTRADM"))
                .andExpect(jsonPath("$.content[0].statutCompte").value("ACTIF"))
                .andExpect(jsonPath("$.content[0].login").value("CTRADM"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "CTRMEM"))
                .andExpect(jsonPath("$.content[0].statutCompte").value("SUSPENDU"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "CTRVER"))
                .andExpect(jsonPath("$.content[0].statutCompte").value("REFUSE"))
                .andExpect(jsonPath("$.content[0].login").value("ctrver.ref"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "UGPM001"))
                .andExpect(jsonPath("$.content[0].statutCompte").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.content[0].login").value("ugpm.att"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "CTRSEC"))
                .andExpect(jsonPath("$.content[0].statutCompte").value("SANS_COMPTE"));

        // ⚠️ Les deux filtres ne se recouvrent jamais : un refusé n'est pas un suspendu.
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("statut", "SUSPENDU"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRMEM"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("statut", "REFUSE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRVER"));
        // L'ancienne valeur fourre-tout n'existe plus : elle part en 400, pas en liste vide.
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("statut", "DESACTIVE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Annuaire : plusieurs comptes pour une personne → le moins fermé l'emporte")
    void annuaire_plusieursComptes() throws Exception {
        // CTRSEC n'a aucun compte dans le socle : on lui en donne deux, un refusé et un actif.
        CompteAuth refuse = new CompteAuth("ctrsec.ref", "x", "CONTROLEUR", "CTRSEC", false);
        refuse.setStatut("REFUSE");
        compteAuthRepository.save(refuse);
        compteAuthRepository.save(new CompteAuth("ctrsec.ok", "x", "CONTROLEUR", "CTRSEC", true));

        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "CTRSEC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].statutCompte").value("ACTIF"))
                .andExpect(jsonPath("$.content[0].login").value("ctrsec.ok"));
    }

    @Test
    @DisplayName("Annuaire : q ignore la casse et les accents, et cherche aussi la référence et le login")
    void annuaire_recherche_sansCasseNiAccents() throws Exception {
        Controleur accentue = controleur("CTRACC", 5, "ANT");
        accentue.setNomCont("RANDRIANARISOA");
        accentue.setPrenomsCont("Hérivélo");
        controleurRepository.save(accentue);
        // Login volontairement éloigné du matricule : c'est la question « qui est m.rakotomalala ? ».
        compteAuthRepository.save(new CompteAuth("m.rakotomalala", "x", "CONTROLEUR", "CTRSEC", true));

        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "herivelo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRACC"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "HÉRIVÉLO"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "randrianarisoa"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRACC"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "rakotomalala"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRSEC"))
                .andExpect(jsonPath("$.content[0].login").value("m.rakotomalala"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "ctracc"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("q", "introuvable"))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("Annuaire : entité de rattachement pour la PRMP, celle de la tutelle pour l'UGPM")
    void annuaire_entiteDeRattachement() throws Exception {
        ugpmRepository.save(ugpm("UGPM001", "PRMP001", "Randria", "Hanta"));

        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("type", "PRMP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("PRMP001"))
                .andExpect(jsonPath("$.content[0].entite").value("Entite 1 · Entite 2"))
                .andExpect(jsonPath("$.content[0].profil").doesNotExist())
                .andExpect(jsonPath("$.content[0].localite").doesNotExist());

        // Une recherche sur le nom de l'entité rend la PRMP ET l'UGPM qu'elle chapeaute.
        String parEntite = mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("q", "entite 1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> refs = JsonPath.read(parEntite, "$.content[*].ref");
        Assertions.assertEquals(List.of("PRMP001", "UGPM001"), refs.stream().sorted().toList());
    }

    @Test
    @DisplayName("Annuaire : filtres type, profil et localité (code insensible à la casse)")
    void annuaire_filtres() throws Exception {
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("profil", "MEMBRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRMEM"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("localite", "ANT"))
                .andExpect(jsonPath("$.totalElements").value(6));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("localite", "ant"))
                .andExpect(jsonPath("$.totalElements").value(6));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("localite", "TMS"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRCC2"));
        // Les filtres se cumulent : un Chef de commission en ANT, pas celui de TMS.
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("profil", "CHEF_COMMISSION").param("localite", "ANT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].ref").value("CTRCC1"));
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("type", "CONTROLEUR"))
                .andExpect(jsonPath("$.totalElements").value(9));
    }

    @Test
    @DisplayName("Annuaire : forme Page, tri nom/prénoms/référence, 2ᵉ page sans recouvrement")
    void annuaire_pagination() throws Exception {
        String p0 = mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("q", "nomctr").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.totalElements").value(9))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andReturn().getResponse().getContentAsString();
        String p1 = mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("q", "nomctr").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.number").value(1))
                .andReturn().getResponse().getContentAsString();

        List<String> refs = new java.util.ArrayList<>(JsonPath.<List<String>>read(p0, "$.content[*].ref"));
        refs.addAll(JsonPath.<List<String>>read(p1, "$.content[*].ref"));
        Assertions.assertEquals(9, java.util.Set.copyOf(refs).size(), "aucun doublon entre les deux pages");
        // Les noms du socle sont « Nom<matricule> » : l'ordre de l'annuaire est donc celui des matricules.
        Assertions.assertEquals(
                List.of("CTRADM", "CTRASS", "CTRCC1", "CTRCC2", "CTRMEM", "CTRPRE", "CTRPUB", "CTRSEC", "CTRVER"),
                refs);
    }

    @Test
    @DisplayName("Annuaire : une valeur inconnue d'un critère énuméré est refusée (400), pas ignorée")
    void annuaire_critereInconnu() throws Exception {
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("statut", "FERME"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("type", "AGENT"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin).param("profil", "STAGIAIRE"))
                .andExpect(status().isBadRequest());
        // Un critère VIDE, lui, n'est pas une erreur : c'est « pas de filtre » (le front sert « tous »).
        mvc.perform(get("/api/annuaire").header("Authorization", tokenAdmin)
                        .param("statut", "").param("type", "").param("profil", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(10));   // 9 contrôleurs + PRMP001
    }

    // ------------------------------------------------------------------
    // Fiche (§B3) — GET /api/annuaire/{type}/{ref}
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Fiche : 401 anonyme, 403 pour tout profil autre qu'Administrateur, 200 pour lui")
    void fiche_reserveALAdministrateur() throws Exception {
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization", tokenPresident))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization", tokenPublication))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization",
                        bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization",
                        bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization",
                        bearer("CTRASS", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR, "CTRASS", "ANT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization",
                        bearer("UGPM001", ProfilUtilisateur.UGPM, TypeActeur.UGPM, "UGPM001", "ANT")))
                .andExpect(status().isForbidden());

        // ⚠️ Même la personne concernée ne lit pas sa propre fiche : c'est un écran d'administration.
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization", tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Fiche : 404 sur une référence inconnue, 400 sur un type qui n'existe pas")
    void fiche_referenceInconnueEtTypeInconnu() throws Exception {
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRXXX").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/annuaire/PRMP/PRMP999").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/annuaire/UGPM/UGPM999").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // La bonne personne dans la mauvaise population n'existe pas non plus.
        mvc.perform(get("/api/annuaire/PRMP/CTRMEM").header("Authorization", tokenAdmin))
                .andExpect(status().isNotFound());
        // Un type hors des trois valeurs est une faute de client, pas une personne absente.
        mvc.perform(get("/api/annuaire/AGENT/CTRMEM").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Fiche d'un contrôleur : identité, compte, supérieur, transversal, chaîne et délégations")
    void fiche_controleur() throws Exception {
        Controleur membre = controleurRepository.findById("CTRMEM").orElseThrow();
        membre.setIdSuperieur("CTRCC1");
        membre.setTransversal(true);
        membre.setImRattache("CTRVER");
        controleurRepository.save(membre);
        Controleur verificateur = controleurRepository.findById("CTRVER").orElseThrow();
        verificateur.setImRattache("CTRASS");
        controleurRepository.save(verificateur);
        CompteAuth compte = compteAuthRepository.findByLogin("CTRMEM").orElseThrow();
        compte.setDateDecision(LocalDateTime.of(2026, 3, 14, 9, 30));
        compteAuthRepository.save(compte);

        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                // Identité : les mêmes champs, sous les mêmes noms, que la ligne de liste.
                .andExpect(jsonPath("$.ref").value("CTRMEM"))
                .andExpect(jsonPath("$.type").value("CONTROLEUR"))
                .andExpect(jsonPath("$.nom").value("NomCTRMEM"))
                .andExpect(jsonPath("$.profil").value("MEMBRE"))
                .andExpect(jsonPath("$.localite").value("ANT"))
                .andExpect(jsonPath("$.entite").value(nullValue()))
                // Compte : login, statut et « actif depuis le … ».
                .andExpect(jsonPath("$.login").value("CTRMEM"))
                .andExpect(jsonPath("$.statutCompte").value("ACTIF"))
                .andExpect(jsonPath("$.dateActivation").value("2026-03-14T09:30:00"))
                // Organisation.
                .andExpect(jsonPath("$.superieur.ref").value("CTRCC1"))
                .andExpect(jsonPath("$.superieur.profil").value("CHEF_COMMISSION"))
                .andExpect(jsonPath("$.superieur.localite").value("ANT"))
                .andExpect(jsonPath("$.transversal").value(true))
                .andExpect(jsonPath("$.chaineControle", hasSize(3)))
                .andExpect(jsonPath("$.chaineControle[0].ref").value("CTRMEM"))
                .andExpect(jsonPath("$.chaineControle[0].lui").value(true))
                .andExpect(jsonPath("$.chaineControle[1].ref").value("CTRVER"))
                .andExpect(jsonPath("$.chaineControle[1].profil").value("VERIFICATEUR"))
                .andExpect(jsonPath("$.chaineControle[1].lui").value(false))
                .andExpect(jsonPath("$.chaineControle[2].ref").value("CTRASS"))
                .andExpect(jsonPath("$.chaineControle[2].profil").value("ASSISTANT_CONTROLEUR"))
                // Les tâches d'un Membre sont exerçables par le Président et par le Chef de commission.
                .andExpect(jsonPath("$.delegations", hasSize(2)))
                .andExpect(jsonPath("$.delegations[0].sens").value("EXERCEE_PAR"))
                .andExpect(jsonPath("$.delegations[0].profil").value("CHEF_COMMISSION"))
                .andExpect(jsonPath("$.delegations[1].profil").value("PRESIDENT"))
                // Un contrôleur n'a jamais de mandat : le mandat est l'acte de nomination d'une PRMP.
                .andExpect(jsonPath("$.mandat").value(nullValue()));

        // Le Chef de commission, lui, exerce quatre profils et voit le sien exercé par le Président.
        String cc = mvc.perform(get("/api/annuaire/CONTROLEUR/CTRCC1").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delegations", hasSize(5)))
                .andReturn().getResponse().getContentAsString();
        Assertions.assertEquals(List.of("EXERCE", "EXERCE", "EXERCE", "EXERCE", "EXERCEE_PAR"),
                JsonPath.read(cc, "$.delegations[*].sens"),
                "les délégations reçues d'abord, la délégation consentie ensuite");
    }

    @Test
    @DisplayName("Fiche : chaîne incomplète à un seul maillon, et rattachement qui boucle sans tourner en rond")
    void fiche_chaineIncompleteEtBouclee() throws Exception {
        // Aucun rattaché : la chaîne se réduit à la personne — état normal, pas une erreur.
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRSEC").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaineControle", hasSize(1)))
                .andExpect(jsonPath("$.chaineControle[0].ref").value("CTRSEC"))
                .andExpect(jsonPath("$.chaineControle[0].lui").value(true))
                .andExpect(jsonPath("$.superieur").value(nullValue()))
                .andExpect(jsonPath("$.transversal").value(false));

        // Rien n'interdit en base que deux contrôleurs se rattachent l'un à l'autre.
        Controleur publication = controleurRepository.findById("CTRPUB").orElseThrow();
        publication.setImRattache("CTRPRE");
        controleurRepository.save(publication);
        Controleur president = controleurRepository.findById("CTRPRE").orElseThrow();
        president.setImRattache("CTRPUB");
        controleurRepository.save(president);

        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRPUB").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaineControle", hasSize(2)))
                .andExpect(jsonPath("$.chaineControle[0].ref").value("CTRPUB"))
                .andExpect(jsonPath("$.chaineControle[1].ref").value("CTRPRE"));

        // Un supérieur qui ne désigne plus personne ne cite personne (référentiel purgé).
        Controleur orphelin = controleurRepository.findById("CTRASS").orElseThrow();
        orphelin.setIdSuperieur("CTRZZZ");
        controleurRepository.save(orphelin);
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRASS").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superieur").value(nullValue()));
    }

    @Test
    @DisplayName("Fiche : une personne sans compte — login et date d'activation nuls, statut SANS_COMPTE")
    void fiche_personneSansCompte() throws Exception {
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRVER").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ref").value("CTRVER"))
                .andExpect(jsonPath("$.profil").value("VERIFICATEUR"))
                .andExpect(jsonPath("$.login").value(nullValue()))
                .andExpect(jsonPath("$.statutCompte").value("SANS_COMPTE"))
                .andExpect(jsonPath("$.dateActivation").value(nullValue()))
                .andExpect(jsonPath("$.actionsJournal30j").value(0));

        // Inscription refusée : DATE_DECISION date le REFUS, ce n'est pas une date d'activation.
        CompteAuth refuse = new CompteAuth("ctrver.ref", "x", "CONTROLEUR", "CTRVER", false);
        refuse.setStatut("REFUSE");
        refuse.setDateDecision(LocalDateTime.of(2026, 4, 2, 11, 0));
        compteAuthRepository.save(refuse);
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRVER").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statutCompte").value("REFUSE"))
                .andExpect(jsonPath("$.login").value("ctrver.ref"))
                .andExpect(jsonPath("$.dateActivation").value(nullValue()));
    }

    @Test
    @DisplayName("Fiche d'une PRMP et d'une UGPM : entité, mandat en fonction, et rien qu'elles ne portent pas")
    void fiche_prmpEtUgpm() throws Exception {
        ugpmRepository.save(ugpm("UGPM001", "PRMP001", "Randria", "Hanta"));

        mvc.perform(get("/api/annuaire/PRMP/PRMP001").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PRMP"))
                .andExpect(jsonPath("$.entite").value("Entite 1 · Entite 2"))
                .andExpect(jsonPath("$.login").value("PRMP001"))
                .andExpect(jsonPath("$.statutCompte").value("ACTIF"))
                // Ni profil de contrôle, ni localité, ni supérieur, ni chaîne, ni délégation.
                .andExpect(jsonPath("$.profil").value(nullValue()))
                .andExpect(jsonPath("$.localite").value(nullValue()))
                .andExpect(jsonPath("$.superieur").value(nullValue()))
                .andExpect(jsonPath("$.transversal").value(nullValue()))
                .andExpect(jsonPath("$.chaineControle", hasSize(0)))
                .andExpect(jsonPath("$.delegations", hasSize(0)))
                // Mandat en fonction ce jour : ici celui reconstitué depuis t_prmp, faute de mandat déclaré.
                .andExpect(jsonPath("$.mandat.refArrete").value("ARR-001"))
                .andExpect(jsonPath("$.mandat.dateDebut").value("2024-01-15"))
                .andExpect(jsonPath("$.mandat.statut").value("ACTIF"))
                .andExpect(jsonPath("$.mandat.implicite").value(true));

        // L'UGPM est située par les entités de sa tutelle, mais ne porte pas son mandat.
        mvc.perform(get("/api/annuaire/UGPM/UGPM001").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("UGPM"))
                .andExpect(jsonPath("$.nom").value("Randria"))
                .andExpect(jsonPath("$.entite").value("Entite 1 · Entite 2"))
                .andExpect(jsonPath("$.statutCompte").value("SANS_COMPTE"))
                .andExpect(jsonPath("$.mandat").value(nullValue()))
                .andExpect(jsonPath("$.chaineControle", hasSize(0)));
    }

    @Test
    @DisplayName("Fiche : actions au journal sur 30 jours glissants, les plus anciennes exclues")
    void fiche_activiteJournal30j() throws Exception {
        semerAudit("CTRMEM", LocalDateTime.now().minusDays(1), 3);
        semerAudit("CTRMEM", LocalDateTime.now().minusDays(40), 2);   // hors fenêtre
        semerAudit("CTRCC1", LocalDateTime.now().minusDays(2), 4);    // un autre acteur
        semerAudit("PRMP001", LocalDateTime.now().minusHours(3), 5);  // une PRMP agit aussi

        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRMEM").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionsJournal30j").value(3));
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRCC1").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.actionsJournal30j").value(4));
        mvc.perform(get("/api/annuaire/PRMP/PRMP001").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.actionsJournal30j").value(5));
    }

    @Test
    @DisplayName("Fiche §B4 : sans aucune trace au journal, la dernière connexion est nulle et les échecs à zéro")
    void fiche_connexionsSansTrace() throws Exception {
        // La forme n'a pas changé le jour où B4 a été livré : seule la valeur change. Une personne qui
        // ne s'est jamais connectée DEPUIS QUE LE JOURNAL EXISTE n'a pas de dernière connexion — et le
        // front ne l'affiche pas (plan §6).
        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRADM").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.derniereConnexion").value(nullValue()))
                .andExpect(jsonPath("$.echecs30j").value(0));
    }

    @Test
    @DisplayName("Fiche §B4 : la dernière connexion RÉUSSIE et les échecs de 30 jours remontent sur la fiche")
    void fiche_accesDepuisLeJournal() throws Exception {
        semerSession("S-1", "CTRADM", LocalDateTime.of(2026, 9, 10, 8, 0), true);
        semerSession("S-2", "CTRADM", LocalDateTime.of(2026, 9, 12, 9, 0), true);
        // Un échec ne fait pas une « dernière connexion », même s'il est plus récent.
        semerSession("S-3", "CTRADM", LocalDateTime.now().minusDays(1), false);
        semerSession("S-4", "CTRADM", LocalDateTime.now().minusDays(2), false);
        // Hors fenêtre de 30 jours : ne compte pas.
        semerSession("S-5", "CTRADM", LocalDateTime.now().minusDays(45), false);
        // Échec d'un autre acteur : sa fiche, pas celle-ci.
        semerSession("S-6", "CTRMEM", LocalDateTime.now().minusHours(3), false);

        mvc.perform(get("/api/annuaire/CONTROLEUR/CTRADM").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.derniereConnexion").value("2026-09-12T09:00:00"))
                .andExpect(jsonPath("$.echecs30j").value(2));
    }

    /** Une ligne du journal des connexions (§B4). */
    private void semerSession(String id, String acteur, LocalDateTime quand, boolean succes) {
        cnm.prs.entity.SessionUtilisateur s = new cnm.prs.entity.SessionUtilisateur();
        s.setIdSession(id);
        s.setImControleur(acteur);
        s.setLogin(acteur);
        s.setDateConnexion(quand);
        s.setSucces(succes);
        sessionUtilisateurRepository.save(s);
    }

    /** Insère {@code combien} écritures d'audit au nom d'un acteur, espacées d'une minute. */
    private void semerAudit(String acteur, LocalDateTime depart, int combien) {
        for (int i = 0; i < combien; i++) {
            jdbcTemplate.update(
                    "INSERT INTO public.t_audit_log (\"ID_LOG\", \"DATE_ACTION\", \"IM_ACTEUR\", \"NOM_TABLE\","
                            + " \"TYPE_ACTION\") VALUES (nextval('seq_audit_log'), ?, ?, ?, ?)",
                    java.sql.Timestamp.valueOf(depart.plusMinutes(i)), acteur, "t_dossier", "MODIFICATION");
        }
    }
}
