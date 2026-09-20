package cnm.prs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import cnm.prs.entity.Anomalie;
import cnm.prs.entity.Compte;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.entity.SoaBeneficiaire;
import cnm.prs.enums.CategorieModePassation;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.enums.StatutSignalement;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeSignalement;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.CompteRepository;
import cnm.prs.seed.ReglesPreControleSeeder;
import cnm.prs.service.PreControlePpmService;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 3) — <strong>l'API, ses gardes et
 * l'écartement motivé</strong>.
 *
 * <p>C'est la moitié sécurité du lot, et elle compte autant que les règles : « le risque réel n'est pas le
 * modèle mais l'étanchéité des habilitations » — l'audit du 2026-09-14 avait classé critique une fuite de
 * données PRMP. Ces tests vérifient donc <strong>profil par profil</strong> qui voit quoi : une PRMP ne
 * voit que ses plans, un contrôleur que ceux de sa commission, le Président tous, et l'Administrateur
 * aucun.</p>
 *
 * <p>Ils vérifient aussi les quatre conditions de la dissuasion (plan, 3.f) : le <strong>motif
 * obligatoire</strong> et d'une longueur minimale, l'<strong>avertissement de visibilité</strong> exigé par
 * le serveur lui-même, le <strong>contrôleur qui lit l'écartement de la PRMP avec son motif</strong>, et
 * le <strong>figeage à la soumission</strong> — après quoi rien ne se défait.</p>
 */
class PreControleApiIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER_ANT = 760;
    private static final int PPM_ANT = 760;
    private static final int DOSSIER_TOA = 761;
    private static final int PPM_TOA = 761;
    private static final String MOTIF =
            "Trois sites distincts, livraisons séparées imposées par la capacité du magasin.";

    @Autowired private ReglesPreControleSeeder seeder;
    @Autowired private PreControlePpmService preControle;
    @Autowired private AnomalieRepository anomalieRepository;
    @Autowired private CompteRepository compteRepository;

    private String tokenMembreToa;
    private String tokenPrmpAutre;
    private String tokenAssistant;

    @BeforeEach
    void deuxPlansDansDeuxCommissions() {
        localiteRepository.save(localite("TOA", "Toamasina"));
        natureRepository.save(new Nature(1, "Travaux", "Marches de travaux"));
        natureRepository.save(new Nature(2, "Fournitures", "Marches de fournitures"));
        modePassationRepository.save(mode(1, "Appel d'offres ouvert"));
        modePassationRepository.save(mode(2, "Consultation de prix"));
        pointDeGrille(811, "Mode de passation conforme");
        soaBeneficiaireRepository.save(soa("SOA-760"));
        prmpRepository.save(prmp("PRMP002", "TOA"));

        plan(DOSSIER_ANT, PPM_ANT, "PRMP001", "ANT");
        plan(DOSSIER_TOA, PPM_TOA, "PRMP002", "TOA");

        // Le cas d'école du fractionnement, sur le plan de la PRMP courante.
        ligne(7601, DOSSIER_ANT, PPM_ANT, "3000000", "Achat de ramettes de papier");
        ligne(7602, DOSSIER_ANT, PPM_ANT, "3000000", "Achat de papier A4");
        ligne(7603, DOSSIER_ANT, PPM_ANT, "3000000", "Fourniture de papier");
        compte(76011, 7601, "61121");
        compte(76021, 7602, "61121");
        compte(76031, 7603, "61121");
        // Une ligne dont le mode est en deçà du seuil : de quoi avoir deux signalements distincts.
        ligne(7604, DOSSIER_ANT, PPM_ANT, "200000000", "Acquisition de matériel informatique");
        compte(76041, 7604, "61125");
        marcheRepository.findById(7604).ifPresent(m -> {
            m.setIdMode(2);
            marcheRepository.save(m);
        });

        tokenMembreToa = bearer("MEMTOA1", ProfilUtilisateur.MEMBRE, TypeActeur.CONTROLEUR, "MEMTOA1", "TOA");
        tokenPrmpAutre = bearer("PRMP002", ProfilUtilisateur.PRMP, TypeActeur.PRMP, "PRMP002", null);
        tokenAssistant = bearer("ASSANT1", ProfilUtilisateur.ASSISTANT_CONTROLEUR, TypeActeur.CONTROLEUR,
                "ASSANT1", "ANT");

        seeder.run();
        entityManager.flush();
        entityManager.clear();
    }

    // ------------------------------------------------------------------ fixture

    private ModePassation mode(int id, String libelle) {
        ModePassation m = new ModePassation();
        m.setIdMode(id);
        m.setLibelle(libelle);
        m.setCategorie(CategorieModePassation.NORMAL);
        m.setDelaiMinJours(30);
        return m;
    }

    private void pointDeGrille(int id, String libelle) {
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(id);
        pc.setLibelPointCtrl(libelle);
        pc.setObligatoire(Boolean.TRUE);
        pc.setIdTypeDossier("DDP");
        pc.setPortee(PorteePointCtrl.LIGNE);
        pc.setOrdrePointCtrl(1);
        pointsCtrlRepository.save(pc);
    }

    private SoaBeneficiaire soa(String code) {
        SoaBeneficiaire s = new SoaBeneficiaire();
        s.setSoaCode(code);
        s.setLibelle("Service beneficiaire de test");
        return s;
    }

    private void plan(int idDossier, int idPpm, String idPrmp, String localite) {
        Dossier d = dossier(idDossier, "BROUILLON");
        d.setIdTypeDossier("DDP");
        d.setIdSousType("PPM");
        d.setIdPrmp(idPrmp);
        d.setIdLocalite(localite);
        dossierRepository.save(d);
        Ppm p = ppm(idPpm, idDossier, idPrmp);
        p.setIdLocalite(localite);
        p.setDateSignature(LocalDate.of(2026, 1, 10));
        ppmRepository.save(p);
    }

    private void ligne(int idDetail, int idDossier, int idPpm, String montant, String designation) {
        Marche m = marche(idDetail, idDossier, idPpm);
        m.setIdNature(2);
        m.setIdMode(1);
        m.setMontEstim(new BigDecimal(montant));
        m.setDesignationMarche(designation);
        m.setFinancement("RPI");
        m.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        marcheRepository.save(m);
    }

    private void compte(int idBenef, int idDetail, String numCompte) {
        if (!compteRepository.existsById(numCompte)) {
            Compte c = new Compte();
            c.setNumCompte(numCompte);
            c.setLibelle("Compte " + numCompte);
            compteRepository.save(c);
        }
        ServiceBeneficiaire b = new ServiceBeneficiaire();
        b.setIdBenef(idBenef);
        b.setIdDetail(idDetail);
        b.setSoaCode("SOA-760");
        b.setNumCompte(numCompte);
        serviceBeneficiaireRepository.save(b);
    }

    /** Lance la vérification côté service, quand le test n'a pas besoin de passer par l'API. */
    private void verifier() {
        preControle.executer(PPM_ANT);
        entityManager.flush();
        entityManager.clear();
    }

    private Anomalie signalementFractionnement() {
        return anomalieRepository.findByIdPpmOrderByIdAnomalie(PPM_ANT).stream()
                .filter(a -> TypeSignalement.FRACTIONNEMENT_COMPTE.name().equals(a.getTypeAnomalie()))
                .findFirst().orElseThrow();
    }

    private String corpsEcartement(String motif, Boolean avertissementLu) {
        return "{\"motif\":\"" + motif + "\",\"avertissementLu\":" + avertissementLu + "}";
    }

    // ------------------------------------------------------------------ 1. périmètres

    @Test
    @DisplayName("La PRMP vérifie son plan et reçoit ses signalements, prioritaires en tête ; le compte "
            + "rendu est servi par le serveur, pas recompté par l'écran")
    void prmp_verifieSonPlan() throws Exception {
        mvc.perform(post("/api/pre-controle/ppm/" + PPM_ANT + "/verifier")
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPpm").value(PPM_ANT))
                .andExpect(jsonPath("$.exercice").value(2026))
                .andExpect(jsonPath("$.nbOuverts").value(2))
                .andExpect(jsonPath("$.nbEcartes").value(0))
                .andExpect(jsonPath("$.signalements", hasSize(2)))
                .andExpect(jsonPath("$.signalements[0].source").value("REGLE"))
                .andExpect(jsonPath("$.signalements[0].statut").value("OUVERT"));
    }

    @Test
    @DisplayName("Une PRMP n'accède pas au plan d'une autre PRMP — c'est exactement la fuite que l'audit "
            + "du 2026-09-14 avait classée critique")
    void prmp_neVoitPasLePlanDUneAutrePrmp() throws Exception {
        mvc.perform(get("/api/pre-controle/ppm/" + PPM_TOA).header("Authorization", tokenPrmp))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pre-controle/ppm/" + PPM_ANT).header("Authorization", tokenPrmpAutre))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un contrôleur voit les plans de sa commission, pas ceux d'une autre ; le Président voit "
            + "tout ; l'Administrateur n'entre pas")
    void controleurs_perimetreParLocalite() throws Exception {
        verifier();

        mvc.perform(get("/api/pre-controle/ppm/" + PPM_ANT).header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signalements", hasSize(2)));
        mvc.perform(get("/api/pre-controle/ppm/" + PPM_ANT).header("Authorization", tokenMembreToa))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/pre-controle/ppm/" + PPM_ANT).header("Authorization", tokenPresident))
                .andExpect(status().isOk());
        mvc.perform(get("/api/pre-controle/ppm/" + PPM_ANT).header("Authorization", tokenAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("L'Assistant contrôleur lit les signalements mais n'en écarte aucun : il prépare le "
            + "travail de l'examinateur, il ne tranche pas")
    void assistant_litMaisNEcartePas() throws Exception {
        verifier();
        int id = signalementFractionnement().getIdAnomalie();

        mvc.perform(get("/api/pre-controle/ppm/" + PPM_ANT).header("Authorization", tokenAssistant))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenAssistant)
                .contentType(MediaType.APPLICATION_JSON).content(corpsEcartement(MOTIF, true)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 2. écartement motivé

    @Test
    @DisplayName("Un écartement sans motif, ou motivé « RAS », est refusé : il ne dirait rien au "
            + "contrôleur")
    void ecartement_motifObligatoireEtSuffisant() throws Exception {
        verifier();
        int id = signalementFractionnement().getIdAnomalie();

        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corpsEcartement("", true)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corpsEcartement("RAS", true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Le serveur exige la confirmation que l'avertissement de visibilité a été montré — sans "
            + "quoi l'écartement serait un piège, et la dissuasion n'aurait pas lieu")
    void ecartement_avertissementExigeParLeServeur() throws Exception {
        verifier();
        int id = signalementFractionnement().getIdAnomalie();

        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corpsEcartement(MOTIF, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erreurs[*].message",
                        hasItem(containsString("visibles de l'autre côté du circuit"))));
        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"" + MOTIF + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("La PRMP écarte avec son motif, et le contrôleur le lit — avec le motif : c'est le cœur "
            + "de la décision du pilote du 2026-09-18")
    void ecartementDeLaPrmp_luParLeControleurAvecSonMotif() throws Exception {
        verifier();
        int id = signalementFractionnement().getIdAnomalie();

        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corpsEcartement(MOTIF, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ECARTE"))
                .andExpect(jsonPath("$.ecartement.typeActeur").value("PRMP"))
                .andExpect(jsonPath("$.ecartement.refActeur").value("PRMP001"))
                .andExpect(jsonPath("$.ecartement.motif", containsString("Trois sites distincts")));

        mvc.perform(get("/api/pre-controle/ppm/" + PPM_ANT).header("Authorization", tokenMembre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbEcartes").value(1))
                .andExpect(jsonPath("$.signalements[?(@.id == " + id + ")].ecartement.typeActeur")
                        .value("PRMP"))
                .andExpect(jsonPath("$.signalements[?(@.id == " + id + ")].ecartement.motif",
                        hasItem(containsString("Trois sites distincts"))));
    }

    @Test
    @DisplayName("Un contrôleur ne ré-écarte pas ce que la PRMP a déjà écarté : cela effacerait son motif. "
            + "Le refus lui rappelle ce motif et l'oriente vers une observation d'examen")
    void controleur_neReEcartePasParDessusLaPrmp() throws Exception {
        verifier();
        int id = signalementFractionnement().getIdAnomalie();
        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corpsEcartement(MOTIF, true)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsEcartement("Je ne suis pas convaincu par ce motif de la PRMP.", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("déjà été écarté")))
                .andExpect(jsonPath("$.message", containsString("observation")));
    }

    @Test
    @DisplayName("L'écartement d'un contrôleur n'est pas servi à la PRMP : c'est une appréciation interne "
            + "au contrôle, qui se dit dans le PV")
    void ecartementDuControleur_masqueALaPrmp() throws Exception {
        verifier();
        int id = signalementFractionnement().getIdAnomalie();

        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsEcartement("Lignes sur trois sites, appréciation admise par la Commission.",
                        true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ecartement.typeActeur").value("CONTROLEUR"));

        mvc.perform(get("/api/pre-controle/ppm/" + PPM_ANT).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbEcartes").value(0))
                .andExpect(jsonPath("$.signalements[?(@.id == " + id + ")].statut").value("OUVERT"))
                .andExpect(jsonPath("$.signalements[?(@.id == " + id + ")].ecartement",
                        everyItem(nullValue())));
    }

    @Test
    @DisplayName("La PRMP reprend son propre écartement tant que le plan n'est pas soumis ; un autre "
            + "acteur ne le reprend pas")
    void reprise_parSonAuteurSeulement() throws Exception {
        verifier();
        int id = signalementFractionnement().getIdAnomalie();
        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corpsEcartement(MOTIF, true)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/pre-controle/signalements/" + id + "/reprendre")
                .header("Authorization", tokenMembre))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pre-controle/signalements/" + id + "/reprendre")
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("OUVERT"))
                .andExpect(jsonPath("$.ecartement").isEmpty());
    }

    // ------------------------------------------------------------------ 3. figeage à la soumission

    @Test
    @DisplayName("La soumission relance les règles et FIGE les écartements : après elle, la PRMP ne peut "
            + "plus ni écarter ni reprendre — c'est ce qui donne sa valeur à son motif")
    void soumission_relanceLesReglesEtFigeLesEcartements() throws Exception {
        verifier();
        int id = signalementFractionnement().getIdAnomalie();
        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corpsEcartement(MOTIF, true)))
                .andExpect(status().isOk());
        entityManager.flush();

        mvc.perform(post("/api/dossiers/" + DOSSIER_ANT + "/soumettre").header("Authorization", tokenPrmp))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/api/pre-controle/ppm/" + PPM_ANT).header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signalements[?(@.id == " + id + ")].fige").value(true));

        mvc.perform(post("/api/pre-controle/signalements/" + id + "/reprendre")
                .header("Authorization", tokenPrmp))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("figé")));

        // Un autre signalement, toujours ouvert mais figé : la PRMP ne peut plus l'écarter non plus.
        Anomalie autre = anomalieRepository.findByIdPpmOrderByIdAnomalie(PPM_ANT).stream()
                .filter(a -> StatutSignalement.OUVERT.name().equals(a.getStatut())).findFirst()
                .orElseThrow();
        mvc.perform(post("/api/pre-controle/signalements/" + autre.getIdAnomalie() + "/ecarter")
                .header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corpsEcartement(MOTIF, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("figés")));
    }

    @Test
    @DisplayName("Un contrôleur, lui, écarte encore après la soumission : son examen commence là où le "
            + "travail de la PRMP s'arrête")
    void controleur_ecarteApresLaSoumission() throws Exception {
        verifier();
        preControle.figer(PPM_ANT);
        entityManager.flush();
        entityManager.clear();
        int id = signalementFractionnement().getIdAnomalie();

        mvc.perform(post("/api/pre-controle/signalements/" + id + "/ecarter")
                .header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpsEcartement("Appréciation de la Commission : trois sites distincts.", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ECARTE"))
                .andExpect(jsonPath("$.ecartement.typeActeur").value("CONTROLEUR"));
    }

    @Test
    @DisplayName("Un plan introuvable donne 404, et un signalement introuvable aussi — jamais un 500")
    void introuvables_donnent404() throws Exception {
        mvc.perform(get("/api/pre-controle/ppm/999999").header("Authorization", tokenPrmp))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/pre-controle/signalements/999999/ecarter")
                .header("Authorization", tokenPrmp)
                .contentType(MediaType.APPLICATION_JSON).content(corpsEcartement(MOTIF, true)))
                .andExpect(status().isNotFound());
    }
}
