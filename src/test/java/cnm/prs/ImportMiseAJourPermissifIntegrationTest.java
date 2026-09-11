package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import cnm.prs.dto.DiffDossierDto;
import cnm.prs.dto.SaisiePpmImportResult;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.Nature;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.entity.SoaBeneficiaire;
import cnm.prs.enums.GraviteAnomalie;
import cnm.prs.enums.ProfilUtilisateur;
import cnm.prs.repository.ServiceBeneficiaireRepository;
import cnm.prs.repository.SoaBeneficiaireRepository;
import cnm.prs.service.MiseAJourPpmService;

/**
 * ⚠️ <strong>Demande pilote 2026-09-11</strong> — l'import d'une mise à jour <strong>rejetait en bloc</strong>
 * (400) dès qu'une ligne était incohérente : sur le PPM JIRAMA, une seule ligne sur une soixantaine
 * condamnait l'import entier, et l'écran ne pouvait même pas afficher le diff pour la corriger. L'import de
 * CRÉATION, lui, charge, signale et auto-corrige. C'est cette asymétrie qui est levée ici.
 *
 * <p>⚠️ <strong>Ces tests attaquent {@code appliquerImport} directement</strong>, là où le contrôleur le
 * branche ({@code service.appliquerImport(id, importService.importer(fichier))}). Le parsing du PDF est un
 * autre sujet, couvert par {@code ImportPpmIntegrationTest} ; ce qui est éprouvé ici, c'est ce que le
 * backend fait du résultat parsé — et un fixture PDF au format « rapprochement de mise à jour » n'existe
 * pas dans le dépôt.</p>
 */
class ImportMiseAJourPermissifIntegrationTest extends CnmIntegrationTestSupport {

    private static final int PARENT = 801;
    private static final int VERSION = 800;
    private static final int PPM = 800;
    private static final int LIGNE = 8001;
    private static final String OBJET = "Travaux de renforcement du reseau";

    @Autowired
    private MiseAJourPpmService miseAJourService;
    @Autowired
    private SoaBeneficiaireRepository soaBeneficiaireRepository;
    @Autowired
    private ServiceBeneficiaireRepository serviceBeneficiaireRepository;

    /**
     * Une VERSION (dossier rattaché à un prédécesseur) portant une ligne déjà connue, que l'import va
     * rapprocher par son objet. Posée directement : ce qui est éprouvé ici est le comportement de
     * l'import, pas le circuit qui mène à la version (couvert ailleurs).
     */
    @BeforeEach
    void versionBrouillon() {
        natureRepository.save(new Nature(1, "Travaux", null));
        soaBeneficiaireRepository.save(new SoaBeneficiaire("SOA-A", "Service A"));
        soaBeneficiaireRepository.save(new SoaBeneficiaire("SOA-B", "Service B"));

        Dossier parent = dossierLoc(PARENT, "CLOTURE", "ANT", "PRMP001");
        parent.setIdTypeDossier("DDP");
        parent.setIdEntiteContract(1);
        dossierRepository.save(parent);

        Dossier version = dossierLoc(VERSION, "BROUILLON", "ANT", "PRMP001");
        version.setIdTypeDossier("DDP");
        version.setIdEntiteContract(1);
        version.setIdDossierParent(PARENT);
        dossierRepository.save(version);
        ppmRepository.save(ppm(PPM, VERSION, "PRMP001"));

        Marche m = marche(LIGNE, VERSION, PPM);
        m.setDesignationMarche(OBJET);
        m.setMontEstim(new BigDecimal("1000"));
        m.setIdNature(1);
        marcheRepository.save(m);

        authentifierPrmp();
    }

    @AfterEach
    void nettoyerContexte() {
        SecurityContextHolder.clearContext();
    }

    /** {@code appliquerImport} est appelé hors requête HTTP : le contexte de sécurité est posé à la main. */
    private void authentifierPrmp() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "HS256").subject("PRMP001")
                .claim("role", ProfilUtilisateur.PRMP.name())
                .claim("ref", "PRMP001")
                .claim("acteurType", "PRMP")
                .claim("localite", "ANT")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static SaisiePpmImportResult.BeneficiaireImport benef(String soa, BigDecimal anc, BigDecimal nouv) {
        return new SaisiePpmImportResult.BeneficiaireImport(soa, null, null, anc, nouv);
    }

    /** Un résultat d'import d'une seule ligne, rapprochée de {@link #LIGNE} par son objet. */
    private static SaisiePpmImportResult importAvec(BigDecimal montEstim, BigDecimal nouvMontEstim,
            List<SaisiePpmImportResult.BeneficiaireImport> benefs) {
        var ligne = new SaisiePpmImportResult.MarcheImport(OBJET, "QUANTITE_FIXE", montEstim, nouvMontEstim,
                1, "Travaux", null, null, null, benefs, null, null, List.of());
        return new SaisiePpmImportResult(2026, null, null, null, List.of(ligne), List.of(), 0);
    }

    private static DiffDossierDto.LigneDiff ligneDe(DiffDossierDto diff) {
        return diff.lignes().stream().filter(l -> Integer.valueOf(LIGNE).equals(l.idDetail())).findFirst()
                .orElseThrow(() -> new AssertionError("ligne " + LIGNE + " absente du diff"));
    }

    // ------------------------------------------------------------------ 1. le cas JIRAMA : auto-corrigé

    @Test
    @DisplayName("Un seul bénéficiaire, nouvMontBenef vide : auto-corrigé à nouvMontEstim, anomalie corrige=true, PAS de 400")
    void monoBeneficiaire_autoCorrige() {
        // Le cas exact du PPM JIRAMA : nouveau montant au niveau du marché, colonne bénéficiaire vide.
        BigDecimal nouveau = new BigDecimal("468794467");
        DiffDossierDto diff = miseAJourService.appliquerImport(VERSION,
                importAvec(new BigDecimal("1000"), nouveau, List.of(benef("SOA-A", new BigDecimal("1000"), null))));

        // ① La valeur a été posée en base — c'est le montant du marché, il n'y avait rien à deviner.
        List<ServiceBeneficiaire> benefs = serviceBeneficiaireRepository.findByIdDetail(LIGNE);
        Assertions.assertEquals(1, benefs.size());
        Assertions.assertEquals(0, nouveau.compareTo(benefs.get(0).getNouvMontBenef()),
                "le nouveau montant du benef doit etre repris du marche");

        // ② Et elle est SIGNALÉE : auto-corrigée, à confirmer — pas avalée en silence.
        var anomalies = ligneDe(diff).anomalies();
        Assertions.assertEquals(1, anomalies.size(), "une anomalie d'auto-correction attendue");
        Assertions.assertEquals(Boolean.TRUE, anomalies.get(0).corrige());
        Assertions.assertEquals(GraviteAnomalie.A_VERIFIER, anomalies.get(0).gravite());
        Assertions.assertEquals(1, diff.nbAVerifier());
    }

    // ------------------------------------------------------------------ 2. le cas ambigu : signalé, pas rejeté

    @Test
    @DisplayName("Deux bénéficiaires, un seul nouvMontBenef : l'import passe (200) avec une anomalie BLOQUANTE — rien n'est inventé")
    void ambigu_signaleSansRejet() {
        DiffDossierDto diff = miseAJourService.appliquerImport(VERSION,
                importAvec(new BigDecimal("1000"), new BigDecimal("2000"),
                        List.of(benef("SOA-A", new BigDecimal("600"), new BigDecimal("1200")),
                                benef("SOA-B", new BigDecimal("400"), null))));

        var anomalies = ligneDe(diff).anomalies();
        Assertions.assertEquals(1, anomalies.size(), "une anomalie attendue sur la ligne ambigue");
        Assertions.assertEquals(GraviteAnomalie.BLOQUANT, anomalies.get(0).gravite());
        Assertions.assertNull(anomalies.get(0).corrige(), "rien n'a ete corrige : la repartition n'est pas deductible");
        // ⚠️ À deux bénéficiaires, aucune valeur n'est inventée : le montant manquant le reste.
        Assertions.assertTrue(serviceBeneficiaireRepository.findByIdDetail(LIGNE).stream()
                .anyMatch(b -> b.getNouvMontBenef() == null), "aucun montant ne doit etre invente");
    }

    @Test
    @DisplayName("La règle n'est pas levée, elle est déplacée : l'enregistrement de la grille refuse toujours en 400")
    void incoherence_refuseeAuEnregistrement() throws Exception {
        // L'import passe...
        miseAJourService.appliquerImport(VERSION,
                importAvec(new BigDecimal("1000"), new BigDecimal("2000"),
                        List.of(benef("SOA-A", new BigDecimal("600"), new BigDecimal("1200")),
                                benef("SOA-B", new BigDecimal("400"), null))));
        SecurityContextHolder.clearContext();

        // ... mais le PUT de la grille, lui, applique la validation stricte.
        String corps = "{\"exercice\":2026,\"signataire\":\"RABE\",\"dateSignature\":\"2026-01-10\","
                + "\"reference\":\"PPM-800\",\"marches\":[{\"idDetail\":" + LIGNE + ",\"designationMarche\":\""
                + OBJET + "\",\"montEstim\":1000,\"nouvMontEstim\":2000,\"idNature\":1,\"statut\":\"PREVU\","
                + "\"beneficiaires\":[{\"soaCode\":\"SOA-A\",\"ancMontBenef\":600,\"nouvMontBenef\":1200},"
                + "{\"soaCode\":\"SOA-B\",\"ancMontBenef\":400}]}]}";
        var res = mvc.perform(put("/api/saisies/ppm/" + VERSION).header("Authorization", tokenPrmp)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andReturn();
        Assertions.assertEquals(400, res.getResponse().getStatus(),
                "l'enregistrement doit refuser -- corps : " + res.getResponse().getContentAsString());
        Assertions.assertTrue(res.getResponse().getContentAsString().contains("nouvMontBenef"),
                "le refus doit cibler le champ, comme avant");
    }

    // ------------------------------------------------------------------ 3. non-régression

    @Test
    @DisplayName("Import cohérent : aucune anomalie, nbAVerifier = 0 — l'ouverture ne bruite pas le cas normal")
    void importCoherent_aucuneAnomalie() {
        DiffDossierDto diff = miseAJourService.appliquerImport(VERSION,
                importAvec(new BigDecimal("1000"), new BigDecimal("2000"),
                        List.of(benef("SOA-A", new BigDecimal("1000"), new BigDecimal("2000")))));

        Assertions.assertTrue(ligneDe(diff).anomalies().isEmpty(), "aucune anomalie attendue");
        Assertions.assertEquals(0, diff.nbAVerifier());
    }
}
