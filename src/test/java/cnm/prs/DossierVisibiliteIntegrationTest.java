package cnm.prs;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Dispatch;
import cnm.prs.entity.Dossier;
import java.util.List;
import cnm.prs.entity.Capm;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.Ppm;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Dossier : soumission, visibilite et scoping (par PPM, par ID_LOCALITE, par PRMP), filtres
 * serveur (statut, famille, sous-type), pagination, familles et sous-types de dossier, et
 * cloisonnement des endpoints bruts.
 */
class DossierVisibiliteIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Visibilité dossiers : CC (localité ANT) et Président voient le dossier ANT")
    void visibilite_dossiers() throws Exception {
        // Le CC d'ANT ne voit que le dossier d'ANT (1), pas celui de TMS (2).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // Le Président voit toutes les localités (2 dossiers).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        // La PRMP voit ses propres dossiers (lien PPM → dossier).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // Une PRMP sans dossier ne voit rien.
        String tokenAutrePrmp = bearer("PRMPXX", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPXX", "ANT");
        mvc.perform(get("/api/dossiers").header("Authorization", tokenAutrePrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Filtre localité étendu : réceptions/dispatch/examen limités à la localité")
    void filtreLocalite_etendu() throws Exception {
        // Réceptions : CC d'ANT n'en voit qu'une (ANT), le Président les deux.
        mvc.perform(get("/api/receptions").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/receptions").header("Authorization", tokenPresident))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        // Accès direct à une réception hors localité → 403 ; dans la localité → 200.
        mvc.perform(get("/api/receptions/2").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/receptions/1").header("Authorization", tokenCc))
                .andExpect(status().isOk());
        // Dispatch et examen aussi filtrés (seuls ceux d'ANT existent).
        mvc.perform(get("/api/dispatchs").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/examens").header("Authorization", tokenCc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // La PRMP n'accède pas aux ressources internes.
        mvc.perform(get("/api/receptions").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Scoping PPM/Marché : PRMP voit les siens, CC sa localité (hors brouillon), Président tout ; hors périmètre → 403")
    void scoping_ppmEtMarche() throws Exception {
        String tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");
        prmpRepository.save(prmp("PRMP002", "ANT")); // FK t_dossier/t_ppm.ID_PRMP

        // Dossiers SOUMIS (estampillés localité) avec PPM/marché de PRMP différentes / localités différentes.
        dossierRepository.save(dossierLoc(200, "SOUMIS", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(201, "SOUMIS", "ANT", "PRMP002"));
        dossierRepository.save(dossierLoc(202, "SOUMIS", "TMS", "PRMP001"));
        dossierRepository.save(dossierLoc(203, "BROUILLON", "ANT", "PRMP001")); // brouillon → invisible des contrôleurs
        ppmRepository.save(ppm(200, 200, "PRMP001"));
        ppmRepository.save(ppm(201, 201, "PRMP002"));
        ppmRepository.save(ppm(202, 202, "PRMP001"));
        ppmRepository.save(ppm(203, 203, "PRMP001"));
        marcheRepository.save(marche(800, 200, 200));
        marcheRepository.save(marche(801, 201, 201));
        marcheRepository.save(marche(802, 202, 202));

        // PRMP001 ne voit QUE ses PPM NON-brouillon (200, 202) : son brouillon (203) est exclu de
        // « Mes PPM & marchés » (écran « Mes brouillons » dédié) ; ceux de PRMP002 (201) restent invisibles.
        mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPpm==200)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==202)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==203)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idPpm==201)]", hasSize(0)));
        // PRMP002 ne voit que le sien (201).
        mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp2))
                .andExpect(jsonPath("$[?(@.idPpm==201)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==200)]", hasSize(0)));
        // CC ANT voit les PPM ANT non brouillon (200, 201), pas TMS (202) ni le brouillon (203).
        mvc.perform(get("/api/ppms").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.idPpm==200)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==201)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==202)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idPpm==203)]", hasSize(0)));
        // Président voit tout, y compris TMS (202).
        mvc.perform(get("/api/ppms").header("Authorization", tokenPresident))
                .andExpect(jsonPath("$[?(@.idPpm==202)]", hasSize(1)));

        // GET /{id} hors périmètre → 403 : PRMP001 sur le PPM de PRMP002 ; CC sur un brouillon.
        mvc.perform(get("/api/ppms/201").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ppms/203").header("Authorization", tokenCc))
                .andExpect(status().isForbidden());

        // Marchés : même scoping. PRMP001 voit 800/802 mais pas 801 ; CC ANT voit 800 mais pas 802 (TMS).
        mvc.perform(get("/api/marches").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDetail==800)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDetail==801)]", hasSize(0)));
        mvc.perform(get("/api/marches").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.idDetail==800)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDetail==802)]", hasSize(0)));
        // GET /{id} : marché de PRMP002 → 403 pour PRMP001 ; marché ANT visible au Membre d'ANT.
        mvc.perform(get("/api/marches/801").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/marches/800").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Mes PPM & marchés : GET /api/ppms (PRMP) exclut les BROUILLON ; « Mes brouillons » (GET /api/dossiers?statut=BROUILLON) inchangé")
    void mesPpm_exclutBrouillons_mesBrouillonsInchange() throws Exception {
        // PRMP001 : 3 dossiers non-brouillon (SOUMIS) + 2 brouillons, chacun avec son PPM.
        dossierRepository.save(dossierLoc(210, "SOUMIS", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(211, "SOUMIS", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(212, "EXAMINE", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(213, "BROUILLON", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(214, "BROUILLON", "ANT", "PRMP001"));
        for (int i = 210; i <= 214; i++) {
            ppmRepository.save(ppm(i, i, "PRMP001"));
        }

        // « Mes PPM & marchés » : les 3 non-brouillon présents, les 2 brouillons (213, 214) absents.
        mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPpm==210)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==211)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==212)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idPpm==213)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idPpm==214)]", hasSize(0)));

        // « Mes brouillons » : GET /api/dossiers?statut=BROUILLON renvoie toujours les 2 brouillons (non régressé).
        mvc.perform(get("/api/dossiers?statut=BROUILLON").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==213)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==214)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==210)]", hasSize(0)));

        // Badge du menu (KpiService.ppmMarches) : aligné sur la liste — même critère hors BROUILLON.
        String ppms = mvc.perform(get("/api/ppms").header("Authorization", tokenPrmp))
                .andReturn().getResponse().getContentAsString();
        int tailleListe = ((List<?>) com.jayway.jsonpath.JsonPath.read(ppms, "$")).size();
        mvc.perform(get("/api/kpis/mes-compteurs").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ppmMarches").value(tailleListe));
    }

    @Test
    @DisplayName("GET /api/dossiers/{id}/ppm : résout l'idPpm d'un brouillon (même sans marché) pour le propriétaire ; 403 hors périmètre ; 404 sans PPM")
    void dossierPpm_resolution_ok() throws Exception {
        // Brouillon PPM de PRMP001, SANS aucun marché → GET /api/marches ne peut pas fournir l'idPpm.
        dossierRepository.save(dossierLoc(220, "BROUILLON", "ANT", "PRMP001"));
        ppmRepository.save(ppm(220, 220, "PRMP001"));
        // Dossier BROUILLON de PRMP001 SANS PPM rattaché (cas 404).
        dossierRepository.save(dossierLoc(221, "BROUILLON", "ANT", "PRMP001"));

        // Propriétaire → 200 + PpmDto complet (idPpm résolu) même sans marché.
        mvc.perform(get("/api/dossiers/220/ppm").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPpm").value(220))
                .andExpect(jsonPath("$.idDossier").value(220));

        // Autre PRMP → hors périmètre → 403.
        String tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", "ANT");
        prmpRepository.save(prmp("PRMP002", "ANT"));
        mvc.perform(get("/api/dossiers/220/ppm").header("Authorization", tokenPrmp2))
                .andExpect(status().isForbidden());

        // Dossier sans PPM rattaché → 404.
        mvc.perform(get("/api/dossiers/221/ppm").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Filtre serveur ?statut sur /api/dossiers : scoping conservé, statut inconnu → 400")
    void dossiers_filtreStatut() throws Exception {
        dossierRepository.save(dossierLoc(210, "SOUMIS", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(211, "BROUILLON", "ANT", "PRMP001"));
        dossierRepository.save(dossierLoc(212, "CLOTURE", "ANT", "PRMP001"));

        // PRMP001 : ?statut=SOUMIS ne renvoie que 210 (pas 211 brouillon ni 212 clôturé).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "SOUMIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==210)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==211)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.idDossier==212)]", hasSize(0)));
        // ?statut=BROUILLON renvoie 211 (la PRMP voit ses brouillons).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "BROUILLON"))
                .andExpect(jsonPath("$[?(@.idDossier==211)]", hasSize(1)));
        // Sans filtre : 210, 211 et 212 présents.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==210)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==212)]", hasSize(1)));
        // Scoping conservé : le CC ANT avec ?statut=SOUMIS voit 210, jamais le brouillon 211.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenCc).param("statut", "SOUMIS"))
                .andExpect(jsonPath("$[?(@.idDossier==210)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==211)]", hasSize(0)));
        // Statut inconnu → 400.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp).param("statut", "NIMPORTEQUOI"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DossierDto — auteur de la saisie : creePar/soumisPar exposés + noms lisibles résolus serveur (UGPM et PRMP) ; login inconnu → nom null")
    void dossier_auteurSaisie_creeParEtNomsResolus() throws Exception {
        // UGPM rattachée à PRMP001, avec son compte : c'est elle qui a saisi le dossier.
        ugpmRepository.save(ugpm("UGPM010", "PRMP001", "Rasoa", "Hanta Miora"));
        compteAuthRepository.save(new CompteAuth("ugpm.hanta", passwordEncoder.encode("pw"), "UGPM", "UGPM010", true));

        Dossier d = dossierLoc(940, "SOUMIS", "ANT", "PRMP001");
        d.setCreePar("ugpm.hanta");     // login de l'agent UGPM (pas l'idUgpm : c'est tout l'objet de la résolution)
        d.setSoumisPar("PRMP001");      // soumission réservée à la PRMP
        dossierRepository.save(d);

        // Détail : logins bruts + noms lisibles, convention « Nom Prénoms » (celle du nomAffichage du login).
        mvc.perform(get("/api/dossiers/940").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creePar").value("ugpm.hanta"))
                .andExpect(jsonPath("$.creeParNom").value("Rasoa Hanta Miora"))
                .andExpect(jsonPath("$.soumisPar").value("PRMP001"))
                .andExpect(jsonPath("$.soumisParNom").value("Nom Prenoms"));

        // Les listes portent la même information (résolution en lot, pas de N+1).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==940)].creeParNom", hasItem("Rasoa Hanta Miora")));

        // Login sans compte (agent supprimé) → nom non résolu : le front garde le login brut.
        Dossier orphelin = dossierLoc(941, "SOUMIS", "ANT", "PRMP001");
        orphelin.setCreePar("compte.disparu");
        dossierRepository.save(orphelin);
        mvc.perform(get("/api/dossiers/941").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creePar").value("compte.disparu"))
                .andExpect(jsonPath("$.creeParNom").value(nullValue()))
                .andExpect(jsonPath("$.soumisParNom").value(nullValue()));

        // Champs en LECTURE SEULE : une valeur envoyée par le client est ignorée (traçabilité serveur).
        mvc.perform(put("/api/dossiers/940").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":940,\"statut\":\"SOUMIS\",\"idLocalite\":\"ANT\",\"idPrmp\":\"PRMP001\","
                        + "\"creePar\":\"usurpateur\",\"soumisPar\":\"usurpateur\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creePar").value("ugpm.hanta"))
                .andExpect(jsonPath("$.soumisPar").value("PRMP001"));
    }

    @Test
    @DisplayName("Pagination serveur (audit front 2026-08-16) : ?page=&size= sur dossiers/ppms/marches → enveloppe "
            + "Page ; sans page → liste plate (rétro-compatible)")
    void listes_paginationServeur() throws Exception {
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident)
                .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)));
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPresident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/ppms").header("Authorization", tokenPresident)
                .param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mvc.perform(get("/api/marches").header("Authorization", tokenPresident)
                .param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Diff de mise à jour — lecture ÉLARGIE (2026-08-15) : les contrôleurs du circuit lisent le diff "
            + "d'une version dans leur localité (plus de 403) ; la PRMP propriétaire inchangée")
    void diffMaj_lectureElargieAuCircuit() throws Exception {
        // Version v1 (parent) et v2 (successeur) seedées directement : même ligne (idLigneOrigine),
        // montant modifié — le diff à la volée doit la classer MODIFIEE.
        Dossier parent = dossierLoc(4620, "REMPLACE", "ANT", "PRMP001");
        parent.setIdTypeDossier("DDP");
        dossierRepository.save(parent);
        ppmRepository.save(ppm(4620, 4620, "PRMP001"));
        Marche v1 = marche(46200, 4620, 4620);
        v1.setIdLigneOrigine(46200);
        v1.setMontEstim(new java.math.BigDecimal("500000000"));
        v1.setFormeMarche(cnm.prs.enums.FormeMarche.QUANTITE_FIXE);
        v1.setDesignationMarche("Marche version");
        marcheRepository.save(v1);
        Dossier version = dossierLoc(4621, "SOUMIS", "ANT", "PRMP001");
        version.setIdTypeDossier("DDP");
        version.setIdDossierParent(4620);
        dossierRepository.save(version);
        Ppm p2 = ppm(4621, 4621, "PRMP001");
        p2.setNumMaj(1);
        p2.setMotifMaj("maj test");
        ppmRepository.save(p2);
        Marche v2 = marche(46210, 4621, 4621);
        v2.setIdLigneOrigine(46200);
        v2.setMontEstim(new java.math.BigDecimal("600000000"));
        v2.setFormeMarche(cnm.prs.enums.FormeMarche.QUANTITE_FIXE);
        v2.setDesignationMarche("Marche version");
        marcheRepository.save(v2);

        // Contrôleurs de la localité : 200 (hier 403 — le tableau partagé retrouve son surlignage).
        mvc.perform(get("/api/dossiers/4621/diff").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[0].type").value("MODIFIEE"))
                .andExpect(jsonPath("$.lignes[0].champs[?(@.champ=='montEstim')].avant", hasItem("500000000")));
        String tokenVer = bearer("CTRVER", ProfilUtilisateur.VERIFICATEUR, TypeActeur.CONTROLEUR, "CTRVER", "ANT");
        mvc.perform(get("/api/dossiers/4621/diff").header("Authorization", tokenVer))
                .andExpect(status().isOk());
        // La PRMP propriétaire lit toujours son diff.
        mvc.perform(get("/api/dossiers/4621/diff").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Soumission dossier (§3.1, Option C) : la PRMP soumet → SOUMIS (réf. générée à la réception, null avant) + Secrétaire/CC notifiés ; re-soumission → 409")
    void soumissionDossier_ok() throws Exception {
        // Brouillon PPM de la PRMP courante (PRMP001), localisé ANT, avec son PPM.
        Dossier d = dossier(3, "BROUILLON");
        d.setRefeDossier(null);
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        Ppm ppm = ppmLocalise(30, 3, "ANT");
        ppm.setIdPrmp("PRMP001");
        ppmRepository.save(ppm);
        marcheRepository.save(marche(31, 3, 30)); // un PPM doit comporter au moins un marché (règle ajoutée)

        // Soumission par la PRMP → 200, statut SOUMIS, refeDossier null (réf. posée à la réception).
        mvc.perform(post("/api/dossiers/3/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"))
                .andExpect(jsonPath("$.refeDossier").doesNotExist());

        // Le Secrétaire et le CC de la localité sont notifiés.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='DOSSIER_SOUMIS')].destinataireIm", hasItem("CTRSEC")))
                .andExpect(jsonPath("$[?(@.typeNotif=='DOSSIER_SOUMIS')].destinataireIm", hasItem("CTRCC1")));

        // Re-soumission → 409 (le dossier n'est plus BROUILLON).
        mvc.perform(post("/api/dossiers/3/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Soumission dossier (§3.1) refus : dossier d'une autre PRMP → 403, localité indéterminable → 400, non-PRMP → 403")
    void soumissionDossier_refus() throws Exception {
        // Jeton PRMP SANS localité (pour forcer l'échec de résolution de localité).
        String tokenPrmpSansLoc = bearer("PRMP001", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP001", null);
        // Une autre PRMP propriétaire.
        prmpRepository.save(prmp("PRMPXX", "ANT"));
        // (4) Brouillon DAO appartenant à une AUTRE PRMP.
        Dossier d4 = dossier(4, "BROUILLON");
        d4.setIdTypeDossier("DMC");
        d4.setIdPrmp("PRMPXX");
        dossierRepository.save(d4);
        // (5) Brouillon DAO de PRMP001, sans localité ni PPM.
        Dossier d5 = dossier(5, "BROUILLON");
        d5.setRefeDossier(null);
        d5.setIdTypeDossier("DMC");
        d5.setIdPrmp("PRMP001");
        dossierRepository.save(d5);

        // (a) Dossier d'une autre PRMP → 403.
        mvc.perform(post("/api/dossiers/4/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        // (b) Dossier nu + PRMP sans localité → aucune localité résoluble → 400.
        mvc.perform(post("/api/dossiers/5/soumettre").header("Authorization", tokenPrmpSansLoc))
                .andExpect(status().isBadRequest());
        // (c) Un non-PRMP ne peut pas soumettre → 403.
        mvc.perform(post("/api/dossiers/5/soumettre").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Soumission dossier SANS PPM (DAO/MAOO) : la localité du dossier (dérivée de l'entité à la saisie) → refeDossier null (réf. à la réception) + ID_LOCALITE estampillé + Secrétaire notifié et le voit")
    void soumissionDossier_sansPpm() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Brouillon DAO sans PPM, de PRMP001, dont la localité (ANT) a été dérivée de l'entité à la saisie.
        Dossier d = dossier(6, "BROUILLON");
        d.setRefeDossier(null);
        d.setIdTypeDossier("DMC");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);

        // PRMP001 soumet → localité = ANT (celle du dossier), SOUMIS, refeDossier null + ID_LOCALITE (plus de repli PRMP).
        mvc.perform(post("/api/dossiers/6/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"))
                .andExpect(jsonPath("$.refeDossier").doesNotExist())
                .andExpect(jsonPath("$.idLocalite").value("ANT"));
        // Le Secrétaire de la localité (ANT) est notifié.
        mvc.perform(get("/api/notifications").header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.typeNotif=='DOSSIER_SOUMIS')].destinataireIm", hasItem("CTRSEC")));
        // Et le dossier est désormais visible par le Secrétaire AVANT toute réception (via ID_LOCALITE).
        mvc.perform(get("/api/dossiers/6").header("Authorization", tokenSec))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Visibilité dossier via ID_LOCALITE : dossier localisé (sans PPM ni réception) visible par sa localité, pas une autre")
    void visibiliteDossierViaIdLocalite() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Dossiers estampillés, sans PPM ni réception.
        Dossier dAnt = dossier(7, "RECU");
        dAnt.setIdLocalite("ANT");
        dossierRepository.save(dAnt);
        Dossier dTms = dossier(8, "RECU");
        dTms.setIdLocalite("TMS");
        dossierRepository.save(dTms);

        mvc.perform(get("/api/dossiers").header("Authorization", tokenSec))
                .andExpect(jsonPath("$[?(@.idDossier==7)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==8)]", hasSize(0)));
        mvc.perform(get("/api/dossiers/7").header("Authorization", tokenSec)).andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/8").header("Authorization", tokenSec)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Visibilité dossier via PPM (Option A) : un dossier à PPM de la localité est visible/consultable sans réception")
    void visibiliteDossierViaPpm() throws Exception {
        String tokenSec = bearer("CTRSEC", ProfilUtilisateur.SECRETAIRE, TypeActeur.CONTROLEUR, "CTRSEC", "ANT");
        // Dossier soumis (PPM de localité ANT), AUCUNE réception.
        dossierRepository.save(dossier(3, "RECU"));
        ppmRepository.save(ppmLocalise(30, 3, "ANT"));
        // Dossier soumis (PPM de localité TMS), AUCUNE réception.
        dossierRepository.save(dossier(4, "RECU"));
        ppmRepository.save(ppmLocalise(40, 4, "TMS"));

        // Le Secrétaire d'ANT voit le dossier 3 (PPM de sa localité), pas le 4 (PPM TMS).
        mvc.perform(get("/api/dossiers").header("Authorization", tokenSec))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==3)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==4)]", hasSize(0)));

        // Accès direct : 200 sur le 3 (sa localité via PPM), 403 sur le 4 (autre localité).
        mvc.perform(get("/api/dossiers/3").header("Authorization", tokenSec))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dossiers/4").header("Authorization", tokenSec))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PRMP : ne peut pas soumettre un dossier d'une autre PRMP → 403 (hors périmètre)")
    void prmp_ne_soumet_pas_dossier_autre_prmp_403() throws Exception {
        // Dossier BROUILLON appartenant à une autre PRMP : le contrôle propriétaire (403) précède statut/contenu.
        Dossier autre = dossier(64, "BROUILLON");
        autre.setIdTypeDossier("DDP");
        autre.setIdPrmp("PRMP999");
        autre.setIdLocalite("ANT");
        dossierRepository.save(autre);
        mvc.perform(post("/api/dossiers/64/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Façade saisie DAO : dossier DAO BROUILLON ; type PPM refusé")
    void saisieDossier_dao() throws Exception {
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DAO\",\"idEntiteContract\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.idTypeDossier").value("DMC"))       // famille déduite du sous-type
                .andExpect(jsonPath("$.idSousType").value("DAO"))          // sous-type choisi (legacy idTypeDossier)
                .andExpect(jsonPath("$.idLocalite").value("ANT"))      // dérivée de l'entité 1
                .andExpect(jsonPath("$.idEntiteContract").value(1))
                .andExpect(jsonPath("$.idDossier").isNumber());        // PK attribuée par le serveur (séquence)
        // Le type PPM est refusé par cette façade (utiliser /api/saisies/ppm).
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"PPM\",\"idEntiteContract\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Intégrité type↔contenu : PPM sans t_ppm → soumission 409 ; PPM attaché à un dossier DAO → 409")
    void integrite_typeContenu() throws Exception {
        // Brouillon PPM sans aucun t_ppm rattaché → soumission refusée.
        Dossier dPpmVide = dossier(63, "BROUILLON");
        dPpmVide.setIdTypeDossier("DDP");
        dPpmVide.setIdPrmp("PRMP001");
        dPpmVide.setIdLocalite("ANT");
        dossierRepository.save(dPpmVide);
        mvc.perform(post("/api/dossiers/63/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict());

        // Brouillon DAO (sans propriétaire) ; y attacher un PPM via l'endpoint Admin → 409.
        Dossier dDao = dossier(64, "BROUILLON");
        dDao.setIdTypeDossier("DMC");
        dossierRepository.save(dDao);
        mvc.perform(post("/api/ppms").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPpm\":64,\"idDossier\":64,\"exercice\":2026,\"signataire\":\"X\","
                        + "\"dateSignature\":\"2026-01-10\",\"reference\":\"P64\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Soumission PPM : un PPM sans marché → 409 ; avec au moins un marché → OK (⚠️ règle ajoutée)")
    void soumission_ppmSansMarche() throws Exception {
        // Brouillon PPM de PRMP001 avec son t_ppm mais AUCUN marché → soumission refusée (409).
        Dossier d = dossier(90, "BROUILLON");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(90, 90, "PRMP001"));
        mvc.perform(post("/api/dossiers/90/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isConflict());

        // Ajout d'au moins une ligne de marché → la soumission passe (SOUMIS).
        marcheRepository.save(marche(900, 90, 90));
        mvc.perform(post("/api/dossiers/90/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("SOUMIS"));
    }

    @Test
    @DisplayName("Endpoints bruts restreints : POST /api/dossiers et /api/ppms réservés Admin ; façade réservée PRMP")
    void endpointsBruts_restreints() throws Exception {
        String dossierBody = "{\"idDossier\":65,\"statut\":\"BROUILLON\"}";
        // PRMP ne peut pas créer un dossier brut → 403 ; Admin → 201.
        mvc.perform(post("/api/dossiers").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(dossierBody))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dossiers").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(dossierBody))
                .andExpect(status().isCreated());
        // PRMP ne peut pas créer un PPM brut → 403.
        mvc.perform(post("/api/ppms").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPpm\":65,\"idDossier\":65,\"exercice\":2026,\"signataire\":\"X\","
                        + "\"dateSignature\":\"2026-01-10\",\"reference\":\"P65\"}"))
                .andExpect(status().isForbidden());
        // La façade de saisie est réservée PRMP : un Membre → 403.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idTypeDossier\":\"DAO\",\"idEntiteContract\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Reprise brouillon PRMP : la PRMP voit/rouvre son brouillon DAO (via t_dossier.idPrmp), pas une autre PRMP")
    void brouillonDao_visiblePourSaProprePrmp() throws Exception {
        String tokenAutrePrmp = bearer("PRMPYY", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMPYY", "ANT");
        // Brouillon DAO de PRMP001 (aucun PPM).
        Dossier d = dossier(80, "BROUILLON");
        d.setIdTypeDossier("DMC");
        d.setIdPrmp("PRMP001");
        dossierRepository.save(d);

        // PRMP001 voit son brouillon dans sa liste et peut l'ouvrir.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idDossier==80)]", hasSize(1)));
        mvc.perform(get("/api/dossiers/80").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("BROUILLON"));
        // Une autre PRMP ne le voit pas.
        mvc.perform(get("/api/dossiers").header("Authorization", tokenAutrePrmp))
                .andExpect(jsonPath("$[?(@.idDossier==80)]", hasSize(0)));
        mvc.perform(get("/api/dossiers/80").header("Authorization", tokenAutrePrmp))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Saisie : la localité vient de l'ENTITÉ choisie (même PRMP, 2 localités) ; entité hors PRMP → 403 ; entité sans localité → 400")
    void saisieLocalite_deLEntite() throws Exception {
        // Entité 9 existe mais non affectée à PRMP001 ; entité 10 affectée mais sans localité.
        entiteContractRepository.save(entite(9, 1, "ANT"));
        entiteContractRepository.save(entite(10, 1, null));
        prmpEntiteRepository.save(prmpEntite(10, "PRMP001", 10, true));

        // Même PRMP (PRMP001), 2 entités de localités différentes → 2 dossiers de localités différentes.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":82,\"idTypeDossier\":\"DAO\",\"idEntiteContract\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocalite").value("ANT"));
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":83,\"idTypeDossier\":\"DAO\",\"idEntiteContract\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocalite").value("TMS"));
        // Entité non rattachée à la PRMP → 403.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":84,\"idTypeDossier\":\"DAO\",\"idEntiteContract\":9}"))
                .andExpect(status().isForbidden());
        // Entité rattachée mais sans localité → 400.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":85,\"idTypeDossier\":\"DAO\",\"idEntiteContract\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Sous-types : lecture par famille ouverte ; écritures réservées ADMINISTRATEUR ; famille inconnue → 404")
    void sousTypes_referentiel() throws Exception {
        // Lecture par famille (remplit le dropdown de saisie) — ouverte à tout authentifié.
        mvc.perform(get("/api/sous-type-dossiers/par-famille/DDP").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idSousType=='PPM')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idSousType=='PPM-AGPM')]", hasSize(1)));
        // Famille inconnue → 404 explicite.
        mvc.perform(get("/api/sous-type-dossiers/par-famille/ZZZ").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
        // Liste ouverte : l'Admin ajoute un sous-type (ex. DAOX sous DMC) → 201 ; un Membre → 403.
        mvc.perform(post("/api/sous-type-dossiers").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"DAOX\",\"libelleSousType\":\"Variante DAO\",\"idTypeDossier\":\"DMC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDossier").value("DMC"));
        mvc.perform(post("/api/sous-type-dossiers").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"DAOY\",\"libelleSousType\":\"x\",\"idTypeDossier\":\"DMC\"}"))
                .andExpect(status().isForbidden());
        // Création sur une famille inconnue → 404.
        mvc.perform(post("/api/sous-type-dossiers").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"XYZ\",\"libelleSousType\":\"x\",\"idTypeDossier\":\"ZZZ\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sous-type DDP recalculé serveur : saisie mode ordinaire → PPM ; édition vers appel d'offres ouvert → PPM-AGPM ; retour → PPM")
    void sousType_ddp_recalcule() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);
        modePassationRepository.save(new ModePassation(4, "Cotation", null, null, null, null));

        // Saisie façade avec un marché en mode ordinaire → famille DDP, sous-type PPM.
        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"dateSignature\":\"2026-01-10\","
                + "\"marches\":[{\"designationMarche\":\"M1\",\"montEstim\":1000000,\"idNature\":1,\"idMode\":4,"
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\"}]}]}";
        String resp = mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDossier").value("DDP"))
                .andExpect(jsonPath("$.idSousType").value("PPM"))
                .andReturn().getResponse().getContentAsString();
        int idDoss = com.jayway.jsonpath.JsonPath.read(resp, "$.idDossier");
        int idDetail = marcheRepository.findByIdDossier(idDoss).get(0).getIdDetail();

        // Édition : la ligne passe en appel d'offres ouvert → le sous-type dérive vers PPM-AGPM.
        String v2 = "{\"exercice\":2026,\"signataire\":\"S\",\"dateSignature\":\"2026-01-10\",\"reference\":\"R\","
                + "\"marches\":[{\"idDetail\":" + idDetail + ",\"designationMarche\":\"M1\",\"montEstim\":1000000,"
                + "\"idNature\":1,\"idMode\":1}]}";
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(v2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSousType").value("PPM-AGPM"));

        // Retour au mode ordinaire → le sous-type redescend à PPM (source de vérité = les marchés).
        String v3 = v2.replace("\"idMode\":1}", "\"idMode\":4}");
        mvc.perform(put("/api/saisies/ppm/" + idDoss).header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(v3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSousType").value("PPM"));
    }

    @Test
    @DisplayName("GET /api/dossiers : filtres serveur ?type= (famille) et ?sousType= ; valeur inconnue → 400")
    void dossiers_filtres_typeEtSousType() throws Exception {
        Dossier a = dossier(9600, "SOUMIS"); a.setIdTypeDossier("DDP"); a.setIdSousType("PPM-AGPM");
        a.setIdPrmp("PRMP001"); a.setIdLocalite("ANT"); dossierRepository.save(a);
        Dossier b = dossier(9601, "SOUMIS"); b.setIdTypeDossier("DDP"); b.setIdSousType("PPM");
        b.setIdPrmp("PRMP001"); b.setIdLocalite("ANT"); dossierRepository.save(b);
        Dossier c = dossier(9602, "SOUMIS"); c.setIdTypeDossier("DMC"); c.setIdSousType("DAO");
        c.setIdPrmp("PRMP001"); c.setIdLocalite("ANT"); dossierRepository.save(c);

        mvc.perform(get("/api/dossiers?type=DDP").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9600)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9601)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9602)]", hasSize(0)));
        mvc.perform(get("/api/dossiers?sousType=PPM-AGPM").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9600)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.idDossier==9601)]", hasSize(0)));
        // Filtres combinables avec le statut, et valeur inconnue → 400.
        mvc.perform(get("/api/dossiers?statut=SOUMIS&type=DMC&sousType=DAO").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDossier==9602)]", hasSize(1)));
        mvc.perform(get("/api/dossiers?type=XXX").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dossiers?sousType=XXX").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Saisie dossier DMC/DDM : idSousType choisi (DAOR) → famille déduite ; sous-type DDP refusé ; sous-type inconnu → 400")
    void saisieDossier_sousTypeChoisi() throws Exception {
        // Nouveau contrat : idSousType → famille déduite du référentiel.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"DAOR\",\"idEntiteContract\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDossier").value("DMC"))
                .andExpect(jsonPath("$.idSousType").value("DAOR"));
        // Un sous-type de la famille DDP (planification) est refusé sur cette façade.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"PPM-AGPM\",\"idEntiteContract\":1}"))
                .andExpect(status().isConflict());
        // Sous-type inconnu → 400 ciblé.
        mvc.perform(post("/api/saisies/dossier").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSousType\":\"NIMPORTE\",\"idEntiteContract\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[?(@.champ=='idSousType')]", hasSize(1)));
    }
}
