package cnm.prs;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import cnm.prs.entity.Dossier;
import java.util.List;
import cnm.prs.entity.Capm;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.PrmpEntite;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.TypeActeur;

/**
 * Referentiels et administration : ecriture reservee a l'Administrateur, entites contractantes
 * et rattachements PRMP-entite, categories d'entite, CAPM, modes de passation et types de DMC,
 * actualites et publication.
 */
class ReferentielAdminIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Référentiel : écriture interdite au Membre (403), permise à l'Admin (201)")
    void referentiel_ecritureAdminSeulement() throws Exception {
        // ⚠️ LOT 3b (2026-08-26) — le test créait « TMS », que le jeu de fixtures pose déjà : il
        // vérifiait donc, sans le savoir, l'ÉCRASEMENT silencieux d'un référentiel existant (save()
        // sur une PK présente = MERGE, réponse 201). Cette création répond désormais 409. Le test
        // porte sur l'autorisation, pas sur la collision : il crée une localité réellement nouvelle.
        String body = "{\"idLocalite\":\"FIA\",\"libelleLocalite\":\"Fianarantsoa\"}";

        mvc.perform(post("/api/localites").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Référentiel : lecture ouverte à tout utilisateur authentifié (200)")
    void referentiel_lectureOuverte() throws Exception {
        mvc.perform(get("/api/localites").header("Authorization", tokenMembre))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Robustesse PK : création sans identifiant assigné → 400 (au lieu de 500)")
    void pk_idManquant_renvoie400() throws Exception {
        mvc.perform(post("/api/localites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"libelleLocalite\":\"X\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Actualités — CRUD admin : INACTIF forcé à la création, validations 400 (profils/HTML/dates), visibilité par profil ciblé")
    void actualites_cycleAdmin_visibiliteParProfil() throws Exception {
        // Réservé à l'Administrateur.
        mvc.perform(post("/api/actualites").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"x\",\"profilsCibles\":[\"MEMBRE\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/actualites").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // Validations 400 : profils vides / inconnu, HTML dans le markdown, expiration avant publication.
        mvc.perform(post("/api/actualites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"x\",\"profilsCibles\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/actualites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"x\",\"profilsCibles\":[\"PILOTE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("PILOTE")));
        mvc.perform(post("/api/actualites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"<script>alert(1)</script>\",\"profilsCibles\":[\"MEMBRE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Markdown")));
        mvc.perform(post("/api/actualites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"x\",\"profilsCibles\":[\"MEMBRE\"],"
                        + "\"datePublication\":\"2026-09-01\",\"dateExpiration\":\"2026-08-01\"}"))
                .andExpect(status().isBadRequest());

        // Création OK — markdown avec autolien et « a < b » (le garde HTML ne bloque pas le markdown légitime).
        int id = creerActualite("Nouvelle procedure", "## Bonjour\\n\\nVoir <https://cnm.mg> - seuil : a < b.",
                "\"MEMBRE\",\"PRMP\"");
        mvc.perform(get("/api/actualites/" + id).header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statut").value("INACTIF"))
                .andExpect(jsonPath("$.imAuteur").value("CTRADM"))
                .andExpect(jsonPath("$.profilsCibles", containsInAnyOrder("MEMBRE", "PRMP")));

        // INACTIF : personne ne la voit, même ciblé.
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idActualite==" + id + ")]", hasSize(0)));

        // Activation (PUT) → visible pour les profils ciblés uniquement, filtrage serveur.
        activerActualite(id, "Nouvelle procedure", "## Bonjour", "\"MEMBRE\",\"PRMP\"");
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idActualite==" + id + ")].titre", hasItem("Nouvelle procedure")));
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idActualite==" + id + ")]", hasSize(1)));
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenCc))
                .andExpect(jsonPath("$[?(@.idActualite==" + id + ")]", hasSize(0)));
    }

    @Test
    @DisplayName("Actualités — interrupteur global, fenêtre de dates, expiration→ARCHIVE automatique, DELETE=archivage, tri")
    void actualites_interrupteur_datesEtArchivage() throws Exception {
        int id1 = creerActualite("Annonce recente", "corps", "\"MEMBRE\"");
        activerActualite(id1, "Annonce recente", "corps", "\"MEMBRE\"");

        // Interrupteur global : coupe le modal pour tous, d'un coup ; bascule réservée à l'Admin.
        mvc.perform(get("/api/parametres/actualites-actives").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(true));
        mvc.perform(put("/api/parametres/actualites-actives").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"actif\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/parametres/actualites-actives").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"actif\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(put("/api/parametres/actualites-actives").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"actif\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idActualite==" + id1 + ")]", hasSize(1)));

        // Publication future → pas encore visible.
        LocalDate demain = LocalDate.now().plusDays(1);
        mvc.perform(put("/api/actualites/" + id1).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Annonce recente\",\"contenuMd\":\"corps\",\"profilsCibles\":[\"MEMBRE\"],"
                        + "\"statut\":\"ACTIF\",\"datePublication\":\"" + demain + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idActualite==" + id1 + ")]", hasSize(0)));

        // Expiration atteinte → bascule automatique en ARCHIVE à la lecture (archiveur système = null).
        LocalDate avantHier = LocalDate.now().minusDays(2);
        LocalDate hier = LocalDate.now().minusDays(1);
        mvc.perform(put("/api/actualites/" + id1).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"Annonce recente\",\"contenuMd\":\"corps\",\"profilsCibles\":[\"MEMBRE\"],"
                        + "\"statut\":\"ACTIF\",\"datePublication\":\"" + avantHier + "\",\"dateExpiration\":\"" + hier + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[?(@.idActualite==" + id1 + ")]", hasSize(0)));
        mvc.perform(get("/api/actualites/" + id1).header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statut").value("ARCHIVE"))
                .andExpect(jsonPath("$.dateArchivage").isNotEmpty())
                .andExpect(jsonPath("$.imArchiveur").value(nullValue()));
        // Archivée = historique : plus modifiable (409), re-DELETE refusé (409).
        mvc.perform(put("/api/actualites/" + id1).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"x\",\"contenuMd\":\"x\",\"profilsCibles\":[\"MEMBRE\"]}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/actualites/" + id1).header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());

        // DELETE = archivage manuel (traçé) — jamais de suppression physique : reste listée côté admin.
        int id2 = creerActualite("A archiver", "corps", "\"MEMBRE\"");
        mvc.perform(delete("/api/actualites/" + id2).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/actualites/" + id2).header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.statut").value("ARCHIVE"))
                .andExpect(jsonPath("$.imArchiveur").value("CTRADM"));

        // Tri : publication effective décroissante (la plus récente d'abord).
        int idAncienne = creerActualite("Ancienne", "corps", "\"MEMBRE\"");
        activerActualite(idAncienne, "Ancienne", "corps", "\"MEMBRE\"", LocalDate.now().minusDays(5));
        int idRecente = creerActualite("Recente", "corps", "\"MEMBRE\"");
        activerActualite(idRecente, "Recente", "corps", "\"MEMBRE\"", LocalDate.now().minusDays(1));
        mvc.perform(get("/api/actualites/mes-actualites").header("Authorization", tokenMembre))
                .andExpect(jsonPath("$[0].idActualite").value(idRecente))
                .andExpect(jsonPath("$[1].idActualite").value(idAncienne));
    }

    @Test
    @DisplayName("Actualités — images : JPEG seul (magic-bytes) → 400, > 10 Mo → 413, redimensionnement 1600 px, lecture authentifiée, ordre")
    void actualites_images_jpegRedimensionne() throws Exception {
        int id = creerActualite("Avec images", "corps", "\"MEMBRE\"");

        // Non-JPEG (PNG déguisé) → 400 ; JPEG > 10 Mo → 413 ; réservé à l'Admin.
        mvc.perform(multipart("/api/actualites/" + id + "/images")
                .file(new MockMultipartFile("fichier", "logo.jpg", "image/jpeg",
                        new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A }))
                .header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
        byte[] trosGros = new byte[10 * 1024 * 1024 + 1];
        trosGros[0] = (byte) 0xFF; trosGros[1] = (byte) 0xD8; trosGros[2] = (byte) 0xFF;
        mvc.perform(multipart("/api/actualites/" + id + "/images")
                .file(new MockMultipartFile("fichier", "photo.jpg", "image/jpeg", trosGros))
                .header("Authorization", tokenAdmin))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/actualites/" + id + "/images")
                .file(new MockMultipartFile("fichier", "p.jpg", "image/jpeg", jpegDeTest(40, 20)))
                .header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());

        // JPEG petit : stocké tel quel, ordre 1 ; le suivant prend l'ordre 2.
        mvc.perform(multipart("/api/actualites/" + id + "/images")
                .file(new MockMultipartFile("fichier", "banniere.jpg", "image/jpeg", jpegDeTest(40, 20)))
                .header("Authorization", tokenAdmin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordre").value(1))
                .andExpect(jsonPath("$.nomFichier").value("banniere.jpg"));
        // JPEG trop large (3200 px) : redimensionné au serveur à 1600 px (proportionnel).
        String repImage = mvc.perform(multipart("/api/actualites/" + id + "/images")
                .file(new MockMultipartFile("fichier", "panorama.jpg", "image/jpeg", jpegDeTest(3200, 100)))
                .header("Authorization", tokenAdmin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ordre").value(2))
                .andReturn().getResponse().getContentAsString();
        int idImage = Integer.parseInt(repImage.replaceAll(".*\"idImage\":(\\d+).*", "$1"));

        // Lecture par un utilisateur authentifié (le modal du Membre) : image/jpeg, largeur réduite à 1600.
        byte[] servie = mvc.perform(get("/api/actualites/" + id + "/images/" + idImage)
                .header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        java.awt.image.BufferedImage relue = javax.imageio.ImageIO
                .read(new java.io.ByteArrayInputStream(servie));
        org.junit.jupiter.api.Assertions.assertEquals(1600, relue.getWidth(), "largeur plafonnée");
        org.junit.jupiter.api.Assertions.assertEquals(50, relue.getHeight(), "hauteur proportionnelle");

        // Métadonnées dans le DTO (jamais le binaire) ; mauvaise actualité → 404 ; suppression → 204 puis 404.
        mvc.perform(get("/api/actualites/" + id).header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$.images", hasSize(2)))
                .andExpect(jsonPath("$.images[1].idImage").value(idImage));
        mvc.perform(get("/api/actualites/999999/images/" + idImage).header("Authorization", tokenMembre))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/actualites/" + id + "/images/" + idImage).header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/actualites/" + id + "/images/" + idImage).header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/actualites/" + id + "/images/" + idImage).header("Authorization", tokenMembre))
                .andExpect(status().isNotFound());
    }

    /** POST admin d'une actualité (statut forcé INACTIF) — {@code profilsJson} : liste JSON sans crochets. */
    private int creerActualite(String titre, String contenuMd, String profilsJson) throws Exception {
        String rep = mvc.perform(post("/api/actualites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"" + titre + "\",\"contenuMd\":\"" + contenuMd
                        + "\",\"profilsCibles\":[" + profilsJson + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("INACTIF"))
                .andReturn().getResponse().getContentAsString();
        return Integer.parseInt(rep.replaceAll(".*\"idActualite\":(\\d+).*", "$1"));
    }

    private void activerActualite(int id, String titre, String contenuMd, String profilsJson) throws Exception {
        activerActualite(id, titre, contenuMd, profilsJson, null);
    }

    /** PUT admin : passe l'actualité ACTIF (avec date de publication optionnelle). */
    private void activerActualite(int id, String titre, String contenuMd, String profilsJson,
            LocalDate datePublication) throws Exception {
        String dates = datePublication == null ? "" : ",\"datePublication\":\"" + datePublication + "\"";
        mvc.perform(put("/api/actualites/" + id).header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titre\":\"" + titre + "\",\"contenuMd\":\"" + contenuMd
                        + "\",\"profilsCibles\":[" + profilsJson + "],\"statut\":\"ACTIF\"" + dates + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIF"));
    }

    /** JPEG réel généré en mémoire (aplat), aux dimensions demandées. */
    private static byte[] jpegDeTest(int largeur, int hauteur) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(largeur, hauteur,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.ORANGE);
        g.fillRect(0, 0, largeur, hauteur);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("Affectations PRMP↔entité (§3.1) : lecture scopée, unicité une PRMP active par entité (409), écriture Admin only")
    void prmpEntites_scopeUniciteEtAutorisation() throws Exception {
        // Lecture scopée : l'Administrateur voit toutes les affectations (les 2 seedées de PRMP001).
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')]", hasSize(2)));
        // La PRMP ne voit que les siennes.
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.idPrmp=='PRMP001')]", hasSize(2)));
        // Une autre PRMP (sans affectation) ne voit rien.
        String tokenPrmp2 = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", null);
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenPrmp2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // Un contrôleur (ni Admin ni PRMP) → liste vide.
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        // Unicité : l'entité 1 est déjà rattachée à PRMP001 → tentative pour une autre PRMP → 409.
        prmpRepository.save(prmp("PRMP002", "ANT"));
        mvc.perform(post("/api/prmp-entites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"PRMP002\",\"idEntiteContract\":1,\"actif\":true}"))
                .andExpect(status().isConflict());

        // Écriture réservée à l'Admin : une PRMP ne peut pas créer d'affectation → 403.
        entiteContractRepository.save(entite(3, 1, "ANT"));
        mvc.perform(post("/api/prmp-entites").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"PRMP001\",\"idEntiteContract\":3,\"actif\":true}"))
                .andExpect(status().isForbidden());

        // L'Admin affecte une entité libre (3) à PRMP001 → 201, active.
        mvc.perform(post("/api/prmp-entites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPrmp\":\"PRMP001\",\"idEntiteContract\":3,\"actif\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEntiteContract").value(3))
                .andExpect(jsonPath("$.actif").value(true));
    }

    @Test
    @DisplayName("Référentiel catégorie-entites — CRUD : lecture ouverte, écriture ADMINISTRATEUR (403 sinon), {id}=libellé")
    void categorieEntites_crud() throws Exception {
        // Lecture ouverte à tout authentifié.
        mvc.perform(get("/api/categorie-entites").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        String body = "{\"libelle\":\"SERVICE\",\"niveauHierarchique\":5}";
        // POST réservé ADMINISTRATEUR (Membre → 403).
        mvc.perform(post("/api/categorie-entites").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/categorie-entites").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelle").value("SERVICE"))
                .andExpect(jsonPath("$.niveauHierarchique").value(5));
        // GET {id} = libellé.
        mvc.perform(get("/api/categorie-entites/SERVICE").header("Authorization", tokenPrmp))
                .andExpect(status().isOk()).andExpect(jsonPath("$.niveauHierarchique").value(5));
        // PUT (niveau modifié) — ADMINISTRATEUR.
        mvc.perform(put("/api/categorie-entites/SERVICE").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"libelle\":\"SERVICE\",\"niveauHierarchique\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.niveauHierarchique").value(7));
        // DELETE — ADMINISTRATEUR.
        mvc.perform(delete("/api/categorie-entites/SERVICE").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/categorie-entites/SERVICE").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Entité contractante — niveauHierarchique DÉRIVÉ de categorieEntite (source unique, valeur client ignorée) ; catégorie inconnue → 400")
    void entiteContract_niveauDeriveDeLaCategorie() throws Exception {
        categorieEntiteRepository.save(new cnm.prs.entity.CategorieEntite("MINISTERE", 1));
        categorieEntiteRepository.save(new cnm.prs.entity.CategorieEntite("DIRECTION", 4));

        // POST : catégorie DIRECTION ; le client tente niveau=99 → ignoré, dérivé à 4 (organigramme 1 seedé au setup).
        String post = "{\"idEntiteContract\":100,\"libelleEntite\":\"Direction X\",\"adresse\":\"Rue Y\","
                + "\"categorieEntite\":\"DIRECTION\",\"idOrganigramme\":1,\"niveauHierarchique\":99}";
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(post))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categorieEntite").value("DIRECTION"))
                .andExpect(jsonPath("$.niveauHierarchique").value(4));

        // PUT : catégorie → MINISTERE, niveau re-dérivé à 1 (le 99 fourni est ignoré).
        String put = "{\"idEntiteContract\":100,\"libelleEntite\":\"Direction X\",\"adresse\":\"Rue Y\","
                + "\"categorieEntite\":\"MINISTERE\",\"idOrganigramme\":1,\"niveauHierarchique\":99}";
        mvc.perform(put("/api/entite-contracts/100").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(put))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.niveauHierarchique").value(1));

        // Catégorie hors référentiel → 400.
        String bad = "{\"idEntiteContract\":101,\"libelleEntite\":\"Z\",\"adresse\":\"W\","
                + "\"categorieEntite\":\"INCONNU\",\"idOrganigramme\":1}";
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Entité contractante — création par la PRMP (import PPM) : 201 + niveau dérivé + rattachement EN ATTENTE (actif=false), idEntiteParent null accepté")
    void entiteContract_creationParPrmp_autoRattachementEnAttente() throws Exception {
        categorieEntiteRepository.save(new cnm.prs.entity.CategorieEntite("DIRECTION", 4));
        // PRMP001 crée une entité (autorité hors périmètre) : PK assignée client, idEntiteParent absent.
        String post = "{\"idEntiteContract\":200,\"libelleEntite\":\"Nouvelle Direction\",\"adresse\":\"Rue Z\","
                + "\"categorieEntite\":\"DIRECTION\",\"idOrganigramme\":1}";
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(post))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEntiteContract").value(200))
                .andExpect(jsonPath("$.niveauHierarchique").value(4))          // dérivé de DIRECTION
                .andExpect(jsonPath("$.idEntiteParent").value(nullValue()));   // null accepté
        // Rattachement auto EN ATTENTE créé pour la PRMP courante.
        List<cnm.prs.entity.PrmpEntite> liens = prmpEntiteRepository.findByIdPrmp("PRMP001").stream()
                .filter(l -> l.getIdEntiteContract() != null && l.getIdEntiteContract().intValue() == 200).toList();
        org.junit.jupiter.api.Assertions.assertEquals(1, liens.size());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, liens.get(0).getActif());
        // Visible dans le GET scopé PRMP (le front filtrera actif=true pour la sélection).
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idEntiteContract==200)].actif", hasItem(false)));
    }

    @Test
    @DisplayName("Entité contractante — création par l'ADMIN : aucun rattachement prmp-entites auto (l'Admin n'est pas une PRMP enregistrée)")
    void entiteContract_creationParAdmin_sansRattachement() throws Exception {
        long liensAvant = prmpEntiteRepository.count();
        String post = "{\"idEntiteContract\":201,\"libelleEntite\":\"Entite Admin\",\"adresse\":\"Rue A\",\"idOrganigramme\":1}";
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(post))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(liensAvant, prmpEntiteRepository.count());
    }

    @Test
    @DisplayName("Rattachement prmp-entites — approbation ADMIN d'un lien EN ATTENTE : PUT {actif:true} l'active (visible scopé PRMP) ; unicité 409 à l'activation si conflit")
    void prmpEntite_approbationAdmin_activeEtUnicite() throws Exception {
        entiteContractRepository.save(entite(300, 1, "ANT"));                 // entité cible
        prmpEntiteRepository.save(prmpEntite(50, "PRMP001", 300, false));     // lien EN ATTENTE (comme auto-créé)
        // ADMIN approuve → actif=true.
        String put = "{\"idPrmpEntite\":50,\"idPrmp\":\"PRMP001\",\"idEntiteContract\":300,\"actif\":true}";
        mvc.perform(put("/api/prmp-entites/50").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(put))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(true));
        // Devient sélectionnable par la PRMP (GET scopé, actif=true).
        mvc.perform(get("/api/prmp-entites").header("Authorization", tokenPrmp))
                .andExpect(jsonPath("$[?(@.idEntiteContract==300)].actif", hasItem(true)));
        // Unicité à l'activation : un 2e lien EN ATTENTE (PRMP002 ↔ même entité) ne peut PAS être activé → 409.
        prmpRepository.save(prmp("PRMP002", "ANT"));
        prmpEntiteRepository.save(prmpEntite(51, "PRMP002", 300, false));
        String put2 = "{\"idPrmpEntite\":51,\"idPrmp\":\"PRMP002\",\"idEntiteContract\":300,\"actif\":true}";
        mvc.perform(put("/api/prmp-entites/51").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(put2))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Entité contractante — PUT persiste idLocalite (régression : le PUT l'ignorait, désormais aligné sur le POST)")
    void entiteContract_putPersisteIdLocalite() throws Exception {
        // POST avec idLocalite=ANT (persiste déjà), puis PUT vers TMS → doit persister aussi.
        String post = "{\"idEntiteContract\":250,\"libelleEntite\":\"E\",\"adresse\":\"A\",\"idOrganigramme\":1,\"idLocalite\":\"ANT\"}";
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(post))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idLocalite").value("ANT"));
        String put = "{\"idEntiteContract\":250,\"libelleEntite\":\"E\",\"adresse\":\"A\",\"idOrganigramme\":1,\"idLocalite\":\"TMS\"}";
        mvc.perform(put("/api/entite-contracts/250").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(put))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLocalite").value("TMS"));      // réponse du PUT
        // Relecture (GET) : idLocalite bien persisté.
        mvc.perform(get("/api/entite-contracts/250").header("Authorization", tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idLocalite").value("TMS"));
    }

    @Test
    @DisplayName("Publication : workflow EN_ATTENTE → PUBLIE → RETIRE + compteur de consultations")
    void publication_workflow() throws Exception {
        // Création : statut/consultations envoyés ignorés → EN_ATTENTE / 0.
        mvc.perform(post("/api/publications").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPublication\":1,\"typeObjet\":\"PPM\",\"idObjet\":1,"
                        + "\"statutPubli\":\"PUBLIE\",\"nbConsultations\":99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statutPubli").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.nbConsultations").value(0));
        // Publication.
        mvc.perform(post("/api/publications/1/publier").header("Authorization", tokenPublication))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPubli").value("PUBLIE"));
        // Consultation (ouverte à tout authentifié) → compteur incrémenté.
        mvc.perform(post("/api/publications/1/consulter").header("Authorization", tokenMembre))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nbConsultations").value(1));
        // Retrait documenté.
        mvc.perform(post("/api/publications/1/retirer").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motifRetrait\":\"Erreur de publication\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statutPubli").value("RETIRE"));
        // Un Membre ne peut pas publier.
        mvc.perform(post("/api/publications/1/publier").header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Document public : intégrité SHA-256 (empreinte + vérification)")
    void documentPublic_integriteSha256() throws Exception {
        mvc.perform(post("/api/publications").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idPublication\":1,\"typeObjet\":\"PPM\",\"idObjet\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/document-publics").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDocPublic\":1,\"idPublication\":1,\"libelleDoc\":\"PV\"}"))
                .andExpect(status().isCreated());

        String contenu = Base64.getEncoder().encodeToString("contenu du document".getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/api/document-publics/1/empreinte").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON).content("{\"contenuBase64\":\"" + contenu + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hashSha256").isNotEmpty());

        // Même contenu → conforme.
        mvc.perform(post("/api/document-publics/1/verifier-integrite").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON).content("{\"contenuBase64\":\"" + contenu + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conforme").value(true));

        // Contenu altéré → non conforme.
        String altere = Base64.getEncoder().encodeToString("contenu altéré".getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/api/document-publics/1/verifier-integrite").header("Authorization", tokenPublication)
                .contentType(MediaType.APPLICATION_JSON).content("{\"contenuBase64\":\"" + altere + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conforme").value(false));
    }

    @Test
    @DisplayName("CAPM — CRUD Administrateur : POST/PUT/DELETE → 201/200/204")
    void capm_crud_admin() throws Exception {
        mvc.perform(post("/api/capm").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCapm\":10,\"libelleProcessus\":\"NEGOCIATION\",\"ordre\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idCapm").value(10))
                .andExpect(jsonPath("$.ordre").value(5));
        mvc.perform(put("/api/capm/10").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCapm\":10,\"libelleProcessus\":\"NEGOCIATION MAJ\",\"ordre\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelleProcessus").value("NEGOCIATION MAJ"))
                .andExpect(jsonPath("$.ordre").value(6));
        mvc.perform(delete("/api/capm/10").header("Authorization", tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("CAPM — écriture interdite hors Administrateur → 403")
    void capm_crud_non_admin() throws Exception {
        mvc.perform(post("/api/capm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCapm\":11,\"libelleProcessus\":\"X\",\"ordre\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Champs élargis : libelleEntite jusqu'à 150 accepté (intitulé de ministère long, 69 car.) ; >150 → 400")
    void entite_libelleLong_accepte() throws Exception {
        String ministere = "MINISTERE DE L'INDUSTRIALISATION ET DU DEVELOPPEMENT DU SECTEUR PRIVE"; // 68 car.
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":950,\"libelleEntite\":\"" + ministere
                        + "\",\"adresse\":\"Anosy\",\"idOrganigramme\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.libelleEntite").value(ministere));

        // Au-delà de 150 → 400 (borne).
        mvc.perform(post("/api/entite-contracts").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idEntiteContract\":951,\"libelleEntite\":\"" + "X".repeat(151)
                        + "\",\"adresse\":\"Anosy\",\"idOrganigramme\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DMC : le type est dérivé du mode de passation (Achat Direct → BC)")
    void dmc_type_derive_du_mode() throws Exception {
        Long idBc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "BC", "Bon de Commande", true))
                .getIdTypeDmc();
        ModePassation mode = new ModePassation(90, "Achat Direct", null, null, null, null);
        mode.setIdTypeDmc(idBc);
        modePassationRepository.save(mode);
        Marche m = marche(9700, 1, 1); m.setIdMode(90); marcheRepository.save(m);

        mvc.perform(post("/api/dmcs/par-marche/9700").header("Authorization", tokenAdmin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idDetail").value(9700))
                .andExpect(jsonPath("$.typeDmcCode").value("BC"))
                .andExpect(jsonPath("$.statut").value("A_PREPARER"));
    }

    @Test
    @DisplayName("DMC : mode non mappé → erreur explicite de configuration, aucun DMC créé")
    void dmc_mode_non_mappe_erreur_explicite() throws Exception {
        modePassationRepository.save(new ModePassation(91, "Gré à gré", null, null, null, null)); // ID_TYPE_DMC null
        Marche m = marche(9701, 1, 1); m.setIdMode(91); marcheRepository.save(m);

        mvc.perform(post("/api/dmcs/par-marche/9701").header("Authorization", tokenAdmin))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertTrue(dossierMecRepository.findByIdDetail(9701).isEmpty());
    }

    @Test
    @DisplayName("DMC : unicité 1-1 par marché (2e création → 409)")
    void dmc_unique_par_marche() throws Exception {
        Long idBc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "BC", "Bon de Commande", true))
                .getIdTypeDmc();
        ModePassation mode = new ModePassation(90, "Achat Direct", null, null, null, null);
        mode.setIdTypeDmc(idBc);
        modePassationRepository.save(mode);
        Marche m = marche(9702, 1, 1); m.setIdMode(90); marcheRepository.save(m);

        mvc.perform(post("/api/dmcs/par-marche/9702").header("Authorization", tokenAdmin))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/dmcs/par-marche/9702").header("Authorization", tokenAdmin))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DMC : changement de mode re-dérive le type si le DMC est A_PREPARER")
    void changement_mode_redérive_type_si_a_preparer() throws Exception {
        Long idBc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "BC", "Bon de Commande", true))
                .getIdTypeDmc();
        Long idDao = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "DAO", "Dossier d'Appel d'Offres", true))
                .getIdTypeDmc();
        ModePassation m90 = new ModePassation(90, "Achat Direct", null, null, null, null);
        m90.setIdTypeDmc(idBc); modePassationRepository.save(m90);
        ModePassation m92 = new ModePassation(92, "Appel d'offres ouvert", null, null, null, null);
        m92.setIdTypeDmc(idDao); modePassationRepository.save(m92);
        // Dossier BROUILLON de PRMP001 (autorise la modification du marché).
        Dossier d = dossier(9710, "BROUILLON");
        d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT"); d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        Marche m = marche(9703, 9710, 1); m.setIdMode(90); marcheRepository.save(m);

        // DMC créé → BC.
        mvc.perform(post("/api/dmcs/par-marche/9703").header("Authorization", tokenAdmin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.typeDmcCode").value("BC"));
        // Changement de mode du marché → 92 (Appel d'offres ouvert = DAO).
        mvc.perform(put("/api/marches/9703").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idDossier\":9710,\"idPpm\":1,\"designationMarche\":\"M\",\"statut\":\"PREVU\",\"idMode\":92}"))
                .andExpect(status().isOk());
        // DMC re-dérivé → DAO.
        mvc.perform(get("/api/dmcs/par-marche/9703").header("Authorization", tokenAdmin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.typeDmcCode").value("DAO"));
    }

    @Test
    @DisplayName("DMC : la suppression du marché supprime son DMC (cascade)")
    void suppression_marche_cascade_dmc() throws Exception {
        Long idBc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "BC", "Bon de Commande", true))
                .getIdTypeDmc();
        ModePassation mode = new ModePassation(90, "Achat Direct", null, null, null, null);
        mode.setIdTypeDmc(idBc); modePassationRepository.save(mode);
        Dossier d = dossier(9711, "BROUILLON");
        d.setIdPrmp("PRMP001"); d.setIdLocalite("ANT"); d.setIdTypeDossier("DDP");
        dossierRepository.save(d);
        Marche m = marche(9705, 9711, 1); m.setIdMode(90); marcheRepository.save(m);

        mvc.perform(post("/api/dmcs/par-marche/9705").header("Authorization", tokenAdmin))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertTrue(dossierMecRepository.existsByIdDetail(9705));
        // Suppression du marché (brouillon, propriétaire PRMP001) → DMC supprimé.
        mvc.perform(delete("/api/marches/9705").header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(dossierMecRepository.existsByIdDetail(9705));
    }

    @Test
    @DisplayName("DMC : POST /api/mode-passations dérive automatiquement le type de DMC du libellé (sinon fourni conservé / sinon null)")
    void mode_create_autoMap_typeDmc() throws Exception {
        Long dao = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "DAO", "Dossier d'Appel d'Offres", true))
                .getIdTypeDmc();
        Long dc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "DC", "Dossier de Consultation", true))
                .getIdTypeDmc();

        // (a) « Appel d'offres ouvert » sans idTypeDmc → DAO (dérivé).
        mvc.perform(post("/api/mode-passations").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":95,\"libelle\":\"Appel d'offres ouvert\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDmc").value(dao.intValue()));
        // (b) « Demande de cotation » → DC (mot-clé « cotation »).
        mvc.perform(post("/api/mode-passations").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":96,\"libelle\":\"Demande de cotation\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDmc").value(dc.intValue()));
        // (c) libellé sans mot-clé → null (à mapper en admin).
        mvc.perform(post("/api/mode-passations").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":97,\"libelle\":\"Régie\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDmc").value(org.hamcrest.Matchers.nullValue()));
        // (d) idTypeDmc explicite fourni → conservé (pas écrasé par l'heuristique).
        mvc.perform(post("/api/mode-passations").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":98,\"libelle\":\"Appel d'offres ouvert\",\"idTypeDmc\":" + dc + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTypeDmc").value(dc.intValue()));
    }

    @Test
    @DisplayName("Modes : catégorie NORMAL/DEROGATOIRE — GET l'expose (null = non classé), PUT la persiste, valeur inconnue → 400 (champ categorie)")
    void mode_categorie_declaratif() throws Exception {
        modePassationRepository.save(new ModePassation(70, "Gré à gré", null, null, null, null));

        // GET : le champ est servi, null tant que l'admin n'a pas classé.
        mvc.perform(get("/api/mode-passations/70").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value(nullValue()));

        // PUT (admin) : categorie DEROGATOIRE persiste et se relit.
        mvc.perform(put("/api/mode-passations/70").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":70,\"libelle\":\"Gré à gré\",\"categorie\":\"DEROGATOIRE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value("DEROGATOIRE"));
        mvc.perform(get("/api/mode-passations/70").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorie").value("DEROGATOIRE"));

        // PUT : valeur hors enum → 400 ciblant le champ categorie (handler Jackson global).
        mvc.perform(put("/api/mode-passations/70").header("Authorization", tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idMode\":70,\"libelle\":\"Gré à gré\",\"categorie\":\"EXCEPTIONNEL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[0].champ").value("categorie"));
    }

    @Test
    @DisplayName("Reprise Flyway V2 — CATEGORIE : NORMAL sur les modes DECLENCHE_AGPM non classés, sans écraser un classement admin")
    void mode_categorie_migration() throws Exception {
        ModePassation aoo = new ModePassation(71, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);
        modePassationRepository.save(new ModePassation(72, "Gré à gré", null, null, null, null));   // non marqué → reste non classé
        ModePassation dejaClasse = new ModePassation(73, "Consultation des Prix Ouverte", null, null, null, null);
        dejaClasse.setDeclencheAgpm(true);
        dejaClasse.setCategorie(cnm.prs.enums.CategorieModePassation.DEROGATOIRE);   // classement admin : intouchable
        modePassationRepository.save(dejaClasse);

        executerMigrationFlyway("V2__reprise_categorie_mode_passation.sql");

        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.CategorieModePassation.NORMAL,
                modePassationRepository.findById(71).orElseThrow().getCategorie());
        org.junit.jupiter.api.Assertions.assertNull(modePassationRepository.findById(72).orElseThrow().getCategorie());
        org.junit.jupiter.api.Assertions.assertEquals(cnm.prs.enums.CategorieModePassation.DEROGATOIRE,
                modePassationRepository.findById(73).orElseThrow().getCategorie());
    }

    @Test
    @DisplayName("DMC : un mode créé À LA VOLÉE (saisie PPM) reçoit aussi le type de DMC dérivé du libellé")
    void mode_alaVolee_autoMap_typeDmc() throws Exception {
        Long bc = typeDmcRepository.save(new cnm.prs.entity.TypeDmc(null, "BC", "Bon de Commande", true))
                .getIdTypeDmc();
        natureRepository.save(new Nature(1, "Travaux", null));
        capmRepository.save(new Capm(1, "LANCEMENT", 1, null, null));

        String body = "{\"idEntiteContract\":1,\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\",\"reference\":\"PPM-DMC\","
                + "\"marches\":[{\"designationMarche\":\"A\",\"montEstim\":1000000,\"natureLibelle\":\"Travaux\",\"modeLibelle\":\"Achat Direct\",\"statut\":\"PREVU\","
                + "\"processus\":[{\"idCapm\":1,\"dateDebut\":\"2026-02-01\",\"dateFin\":\"2026-06-30\"}]}]}";
        mvc.perform(post("/api/saisies/ppm").header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // Le mode « Achat Direct » créé à la volée porte idTypeDmc = BC (dérivé).
        ModePassation cree = modePassationRepository.findAll().stream()
                .filter(m -> "Achat Direct".equals(m.getLibelle())).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(bc, cree.getIdTypeDmc());
    }
}
