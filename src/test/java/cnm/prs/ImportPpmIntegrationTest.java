package cnm.prs;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.EntiteContract;
import cnm.prs.entity.Ministere;

/**
 * Import d'un PPM depuis un fichier : parsing PDF (formats SIGMP et MIDSP, multi-pages, lots,
 * fragments recolles, anomalies structurees) et import tableur xlsx (colonnes explicites,
 * gabarit telechargeable).
 */
class ImportPpmIntegrationTest extends CnmIntegrationTestSupport {

    @Test
    @DisplayName("Import PPM PDF (read-only) : parse l'en-tête + résout l'entité + avertissements ; non-PDF → 400")
    void importPpm_pdf_prefill_ok() throws Exception {
        EntiteContract e = entite(900, 1, "ANT"); e.setLibelleEntite("MINISTERE ECONOMIE");
        entiteContractRepository.save(e);
        natureRepository.save(new Nature(1, "Fournitures et services", null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES - Exercice 2026",
                "Autorite Contractante : MINISTERE ECONOMIE",
                "Documentation et abonnement Fournitures et services 1 005 000",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercice").value(2026))
                .andExpect(jsonPath("$.idEntiteContract").value(900))
                .andExpect(jsonPath("$.autoriteContractante").value("MINISTERE ECONOMIE"))
                .andExpect(jsonPath("$.dateSignature").value("2026-04-14"))
                .andExpect(jsonPath("$.avertissements").isArray())
                .andExpect(jsonPath("$.marches").isArray());

        // Robustesse : fichier non-PDF → 400 (pas de données partielles silencieuses).
        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "x.txt", "text/plain", "ceci n'est pas un pdf".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Import PPM PDF — parsing calibré du tableau : OBJET multi-lignes recomposé, montants, bénéficiaire, 3 prévisions")
    void importPpm_tableauCalibre_ok() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));

        // Structure du PPM officiel : en-tête doc, en-tête colonnes (+ sous-colonnes ignorées), 1 ligne de données
        // (NATURE + OBJET sur 3 lignes, puis ligne montants), puis « Fait à … ». Sans accents (police Helvetica).
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "Date d'etablissement du Document initial: 14/04/2026",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "SERVICE BENEFICIAIRE COMPTE MONTANT ESTIMATIF PAR BENEFICIAIRE",
                "Travaux Travaux de fabrication et",
                "installation des etageres",
                "metalliques fixes",
                "5 550 000.00 Achat Direct RPI 00-21-0-J00-00000 6211 5 550 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le _ _ /_ _ /_ _ _ _");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercice").value(2026))
                .andExpect(jsonPath("$.dateSignature").value("2026-04-14"))
                .andExpect(jsonPath("$.marches", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].designationMarche").value("Travaux de fabrication et installation des etageres metalliques fixes"))
                .andExpect(jsonPath("$.marches[0].natureLibelle").value("Travaux"))
                .andExpect(jsonPath("$.marches[0].idNature").value(1))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("Achat Direct"))
                .andExpect(jsonPath("$.marches[0].idMode").value(nullValue()))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-21-0-J00-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("6211"))
                .andExpect(jsonPath("$.marches[0].previsions[0].processus").value("LANCEMENT"))
                .andExpect(jsonPath("$.marches[0].previsions[0].dateDebut").value("2026-06-01"))
                .andExpect(jsonPath("$.marches[0].previsions[1].dateDebut").value("2026-06-02"))
                .andExpect(jsonPath("$.marches[0].previsions[2].dateDebut").value("2026-06-12"))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*Achat Direct.*/)]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — lots extraits de la désignation (« repartis en 04 Lots : Lot 01 : … ») : 4 lots + désignation INTÉGRALE conservée")
    void importPpm_lotsExtraitsDeLaDesignation() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        // Cas réel observé (sans accents — police Helvetica du fixture) : allotissement décrit dans l'OBJET.
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "Date d'etablissement du Document initial: 14/04/2026",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Travaux de traitement des points noirs sur la route reliant Fenomanana et",
                "Analamarina dans le district de Faratsiho - Vakinakaratra repartis en 04 Lots :",
                "Lot 01 : traitement de breche et chaussee a Ampakandrano; Lot 02 : traitement de",
                "breche et de la chaussee a Ambatofotsikely; Lot 03 : traitement de la chaussee a",
                "Ambohiborona ; Lot 04 : traitement de la digue vers Analamarina.",
                "1 150 000 000.00 Appel d'offres ouvert RPI 00-21-0-J00-00000 6211 1 150 000 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                // Désignation INTÉGRALE conservée, énumération des lots comprise (décision revisitée
                // 2026-07-18 : le doublon texte/lots[] est accepté et voulu).
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Travaux de traitement des points noirs sur la route reliant Fenomanana et "
                                + "Analamarina dans le district de Faratsiho - Vakinakaratra repartis en 04 Lots : "
                                + "Lot 01 : traitement de breche et chaussee a Ampakandrano; Lot 02 : traitement de "
                                + "breche et de la chaussee a Ambatofotsikely; Lot 03 : traitement de la chaussee a "
                                + "Ambohiborona ; Lot 04 : traitement de la digue vers Analamarina."))
                .andExpect(jsonPath("$.marches[0].lots", hasSize(4)))
                .andExpect(jsonPath("$.marches[0].lots[0].designationLot").value("traitement de breche et chaussee a Ampakandrano"))
                .andExpect(jsonPath("$.marches[0].lots[1].designationLot").value("traitement de breche et de la chaussee a Ambatofotsikely"))
                .andExpect(jsonPath("$.marches[0].lots[2].designationLot").value("traitement de la chaussee a Ambohiborona"))
                .andExpect(jsonPath("$.marches[0].lots[3].designationLot").value("traitement de la digue vers Analamarina"))
                // Champs descriptifs non portés par le texte → null (aucun contrôle de somme, règle actée).
                .andExpect(jsonPath("$.marches[0].lots[0].montLot").value(nullValue()))
                // Pas d'avertissement d'allotissement quand l'extraction réussit.
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*Allotissement.*/)]", hasSize(0)));
    }

    @Test
    @DisplayName("Import PPM — lots (variantes SIGMP) : « repartie en trois 3 lots: lot n1: … - lot n2: … », « deux 2 lots », et « LOT N°01: … LOT N°02: … » sans annonce")
    void importPpm_lotsVariantes() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                // L14 : annonce lettres + chiffres, séparateur « - », 3 lots.
                "Travaux Rehabilitation de routes repartie en trois 3 lots: lot n1: piste Est - lot n2: piste Ouest - lot n3: piste Nord",
                "500 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 500 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                // L15 : annonce « deux 2 lots », 2 lots.
                "Travaux Entretien de pistes repartie en deux 2 lots: lot n1: piste A - lot n2: piste B",
                "400 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 400 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                // L18 : SANS annonce, marqueurs « LOT N°01: » / « LOT N°02: ».
                "Travaux Construction de batiments LOT N°01: batiment A LOT N°02: batiment B",
                "300 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 300 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(3)))
                // L14 : 3 lots (séparateur « - » retiré du texte de lot).
                .andExpect(jsonPath("$.marches[0].lots", hasSize(3)))
                .andExpect(jsonPath("$.marches[0].lots[0].designationLot").value("piste Est"))
                .andExpect(jsonPath("$.marches[0].lots[1].designationLot").value("piste Ouest"))
                .andExpect(jsonPath("$.marches[0].lots[2].designationLot").value("piste Nord"))
                .andExpect(jsonPath("$.marches[0].anomalies[?(@.champ=='lot')]", hasSize(0)))
                // L15 : 2 lots.
                .andExpect(jsonPath("$.marches[1].lots", hasSize(2)))
                .andExpect(jsonPath("$.marches[1].lots[0].designationLot").value("piste A"))
                .andExpect(jsonPath("$.marches[1].lots[1].designationLot").value("piste B"))
                // L18 : 2 lots SANS annonce (≥2 marqueurs suffisent).
                .andExpect(jsonPath("$.marches[2].lots", hasSize(2)))
                .andExpect(jsonPath("$.marches[2].lots[0].designationLot").value("batiment A"))
                .andExpect(jsonPath("$.marches[2].lots[1].designationLot").value("batiment B"));
    }

    @Test
    @DisplayName("Import PPM — lots : objet COMMENÇANT par le marqueur (« LOT N°01:… LOT N°02:… » en position 0, séparés par un espace) → 2 lots")
    void importPpm_lotsObjetCommenceParMarqueur() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        // Cas réel 188-SIGMP L18 : la désignation COMMENCE par « LOT N°01: » (aucun préambule), lots séparés
        // par un simple espace avant « LOT N°02: ». (Sans accents — police du fixture.)
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux LOT N°01:Travaux de rehabilitation du batiment A au siege de la direction generale LOT N°02:Travaux de rehabilitation du logement de fonction du DG a Fort-Duchesne.",
                "300 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 300 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].lots", hasSize(2)))
                .andExpect(jsonPath("$.marches[0].lots[0].designationLot").value(
                        "Travaux de rehabilitation du batiment A au siege de la direction generale"))
                .andExpect(jsonPath("$.marches[0].lots[1].designationLot").value(
                        "Travaux de rehabilitation du logement de fonction du DG a Fort-Duchesne"))
                // Extraction réussie → pas d'anomalie « lot ». Désignation intégrale (le marqueur y reste).
                .andExpect(jsonPath("$.marches[0].anomalies[?(@.champ=='lot')]", hasSize(0)))
                .andExpect(jsonPath("$.marches[0].designationMarche", org.hamcrest.Matchers.startsWith("LOT N°01:")));
    }

    @Test
    @DisplayName("Import PPM — lots incohérents (« trois 3 lots » annoncés, 2 marqueurs) : lots vides + anomalie champ:lot LOT_INCOHERENT")
    void importPpm_lotsIncoherent_anomalie() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Rehabilitation repartie en trois 3 lots: lot n1: piste A - lot n2: piste B",
                "500 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 500 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                // 3 annoncés mais 2 marqueurs → rien d'extrait + anomalie « lot » pour la revue front.
                .andExpect(jsonPath("$.marches[0].lots", hasSize(0)))
                .andExpect(jsonPath("$.marches[0].anomalies[?(@.champ=='lot' && @.type=='LOT_INCOHERENT' && @.gravite=='A_VERIFIER')]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — allotissement incohérent (« 03 Lots » annoncés, 2 segments) : lots vides, désignation intégrale + avertissement")
    void importPpm_lotsCompteIncoherent() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Rehabilitation de pistes repartis en 03 Lots : Lot 01 : piste A; Lot 02 : piste B",
                "5 550 000.00 Achat Direct RPI 00-21-0-J00-00000 6211 5 550 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                // Dans le doute : rien d'extrait, désignation INTÉGRALE (comportement antérieur conservé).
                .andExpect(jsonPath("$.marches[0].lots", hasSize(0)))
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Rehabilitation de pistes repartis en 03 Lots : Lot 01 : piste A; Lot 02 : piste B"))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*Allotissement.*3 lot.*2 segment.*/)]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — MODE|FINANCEMENT|SERVICE aplatis (cellules multi-lignes SIGMP, RPI) : mode net, financement=RPI, service→soaLibelle (dernier mot isolé conservé)")
    void importPpm_modeFinancementServiceMultiligne() throws Exception {
        // 460 SIGMP : mode + RPI + « Service Administratif et Financier », « Financier » seul sur sa ligne
        // physique (piège BRUIT_PAGE : « Financier » ⊃ « finan » ne doit PAS être filtré).
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "FOURNITURES Fourniture de test 6 000 000.00 CONSULTATION DE PRIX OUVERTE RPI Service Administratif et",
                "Financier",
                "6111 6 000 000.00 21/05/2026 01/06/2026 08/06/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].designationMarche").value("Fourniture de test"))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("CONSULTATION DE PRIX OUVERTE"))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].beneficiaires", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value(nullValue()))
                // « Financier » conservé (BRUIT_PAGE) + service capté après le financement (découpage).
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaLibelle").value("Service Administratif et Financier"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("6111"));
    }

    @Test
    @DisplayName("Import PPM — financement « FR » + service textuel « TOUT SERVICE » (163 SIGMP) : mode net, financement=FR, service→soaLibelle")
    void importPpm_financementFrServiceTextuel() throws Exception {
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "FOURNITURES Fourniture eau 2 480 000.00 ACHAT DIRECT FR TOUT SERVICE 6471 2 480 000.00 23/03/2026 24/03/2026 25/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("ACHAT DIRECT"))
                .andExpect(jsonPath("$.marches[0].financement").value("FR"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaLibelle").value("TOUT SERVICE"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value(nullValue()))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("6471"));
    }

    @Test
    @DisplayName("Import PPM — mode variante « … PIP » collé au financement RPI (379) : le mode CONSERVE son PIP (idMode=8), financement=RPI, SOA codé")
    void importPpm_modeVariantePipFinancementRpi() throws Exception {
        modePassationRepository.save(new ModePassation(8, "CONSULTATION DE PRIX OUVERTE PIP", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "FOURNITURES Fourniture test 75 000 000.00 CONSULTATION DE PRIX OUVERTE PIP RPI 00-61-0-D10-00000 2317 75 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches[0].idMode").value(8))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("CONSULTATION DE PRIX OUVERTE PIP"))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-61-0-D10-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaLibelle").value(nullValue()));
    }

    @Test
    @DisplayName("Import PPM xlsx — colonnes explicites : 2 marchés (dont multi-bénéficiaire + lots), référentiels résolus, 0 anomalie")
    void importXlsx_ok() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        String[] entetes = { "objet", "forme", "nature", "montant estimatif", "mode", "financement",
                "soa", "compte", "montant beneficiaire", "date lancement", "date ouverture", "date attribution",
                "lots", "exercice" };
        Object[][] lignes = {
                { "Travaux de rehabilitation de la RN 13", "CONTRAT_CADRE", "Travaux", 500000000L,
                        "Consultation de prix ouverte", "RPI", "00-61-0-D10-00000", "2441", 500000000L,
                        "06/03/2026", "16/03/2026", "27/03/2026", "Lot A | Lot B", 2026L },
                // Marché à 2 bénéficiaires : 2e ligne = objet vide (continuation).
                { "Fourniture de materiel", "", "Travaux", 3000000L, "Consultation de prix ouverte", "RPI",
                        "00-61-0-D10-00000", "2441", 1000000L, "06/03/2026", "16/03/2026", "27/03/2026", "", "" },
                { "", "", "", "", "", "", "00-61-0-D10-00000", "2441", 2000000L, "", "", "", "", "" } };
        byte[] xlsx = xlsxAvecLignes(entetes, lignes);

        mvc.perform(multipart("/api/saisies/ppm/import-xlsx")
                .file(new MockMultipartFile("fichier", "ppm.xlsx", "application/vnd.ms-excel", xlsx))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(2)))
                .andExpect(jsonPath("$.exercice").value(2026))
                .andExpect(jsonPath("$.nbAVerifier").value(0))
                // Marché 0 : transcription exacte + référentiels résolus + lots + forme.
                .andExpect(jsonPath("$.marches[0].designationMarche").value("Travaux de rehabilitation de la RN 13"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(500000000.0))
                .andExpect(jsonPath("$.marches[0].formeMarche").value("CONTRAT_CADRE"))
                .andExpect(jsonPath("$.marches[0].idNature").value(1))
                .andExpect(jsonPath("$.marches[0].idMode").value(1))
                .andExpect(jsonPath("$.marches[0].beneficiaires", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-61-0-D10-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(500000000.0))
                .andExpect(jsonPath("$.marches[0].lots", hasSize(2)))
                .andExpect(jsonPath("$.marches[0].lots[0].designationLot").value("Lot A"))
                .andExpect(jsonPath("$.marches[0].previsions[0].dateDebut").value("2026-03-06"))
                .andExpect(jsonPath("$.marches[0].anomalies", hasSize(0)))
                // Marché 1 : 2 bénéficiaires (Σ = montant), 0 anomalie.
                .andExpect(jsonPath("$.marches[1].beneficiaires", hasSize(2)))
                .andExpect(jsonPath("$.marches[1].montEstim").value(3000000.0))
                .andExpect(jsonPath("$.marches[1].anomalies", hasSize(0)));
    }

    @Test
    @DisplayName("Import PPM xlsx — montant ≠ Σ bénéficiaires → anomalie MONTANT_INCOHERENT BLOQUANT (pas d'auto-correction en tableur)")
    void importXlsx_montantIncoherent() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        String[] entetes = { "objet", "nature", "montant estimatif", "mode", "financement", "soa", "compte",
                "montant beneficiaire", "date lancement", "date ouverture", "date attribution" };
        Object[][] lignes = { { "Travaux divers", "Travaux", 500000000L, "Consultation de prix ouverte", "RPI",
                "00-61-0-D10-00000", "2441", 400000000L, "06/03/2026", "16/03/2026", "27/03/2026" } };

        mvc.perform(multipart("/api/saisies/ppm/import-xlsx")
                .file(new MockMultipartFile("fichier", "ppm.xlsx", "application/vnd.ms-excel",
                        xlsxAvecLignes(entetes, lignes)))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbAVerifier").value(1))
                .andExpect(jsonPath("$.marches[0].anomalies[?(@.champ=='montEstim' && @.type=='MONTANT_INCOHERENT' && @.gravite=='BLOQUANT' && @.corrige==false)]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM xlsx — gabarit téléchargeable (.xlsx, en-têtes + notice)")
    void importXlsx_gabarit() throws Exception {
        byte[] gabarit = mvc.perform(get("/api/saisies/ppm/import-xlsx/gabarit").header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        // Magic bytes d'un .xlsx (archive ZIP « PK »).
        org.junit.jupiter.api.Assertions.assertTrue(gabarit.length > 100
                && gabarit[0] == 'P' && gabarit[1] == 'K');
        // Le gabarit se ré-importe (les lignes d'exemple sont exploitables) → ≥1 marché.
        mvc.perform(multipart("/api/saisies/ppm/import-xlsx")
                .file(new MockMultipartFile("fichier", "gabarit.xlsx", "application/vnd.ms-excel", gabarit))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(2)));
    }

    /** Génère en mémoire un .xlsx (POI) : ligne 0 = en-têtes, puis les lignes de données — pour tester l'import tableur. */
    private byte[] xlsxAvecLignes(String[] entetes, Object[][] lignes) throws Exception {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Marchés");
            org.apache.poi.ss.usermodel.Row h = sheet.createRow(0);
            for (int i = 0; i < entetes.length; i++) {
                h.createCell(i).setCellValue(entetes[i]);
            }
            for (int r = 0; r < lignes.length; r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
                for (int c = 0; c < lignes[r].length; c++) {
                    Object v = lignes[r][c];
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                    if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(v));
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("Import PPM — normalisation étendue : « APPEL D'OFFRE OUVERT » (singulier) résout idMode=1, modeLibelle CANONIQUE, sans avertissement")
    void importPpm_modeSingulierResolu() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Rehabilitation de la route reliant Tsiatosika",
                "23 100 000 000.00 APPEL D'OFFRE OUVERT RPI 00-21-0-J00-00000 6211 23 100 000 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].idMode").value(1))
                // Libellé CANONIQUE du référentiel (pas le texte brut du PDF) — le badge AGPM du front s'aligne.
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("Appel d'offres ouvert"))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*Mode de passation.*/)]", hasSize(0)));
    }

    @Test
    @DisplayName("Import PPM — régression 161-PPM MTP : fragment d'objet (n° de route) collé au montant ou isolé sur sa ligne → recollé à l'objet, montant réaligné")
    void importPpm_fragmentObjetCollageMontant_regression() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));

        // Deux formes réelles du bug (161-PPM MTP.pdf) :
        //  A) le n° de route « 33 » est collé au montant sur la même ligne physique (contamination) ;
        //  B) le n° de route « 44 » est SEUL sur sa propre ligne physique (fragment autrefois filtré).
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Travaux de reparation de la breche au",
                "PK 38+800 de la RNT 33 590 000 000.00 CONSULTATION DE PRIX OUVERTE RPI 00-61-0-D10-00000 2441 590 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Travaux Travaux d'urgence pour la construction",
                "d'un dalot au PK 194+450 sur la RNS",
                "44",
                "140 000 000.00 CONSULTATION DE PRIX OUVERTE RPI 00-61-0-D10-00000 2441 140 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(2)))
                // A) « 33 » recollé, montant réaligné sur 590 000 000 (plus de 33 590 000 000).
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Travaux de reparation de la breche au PK 38+800 de la RNT 33"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(590000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(590000000.00))
                // B) « 44 » (fragment isolé) conservé et recollé, montant correct 140 000 000.
                .andExpect(jsonPath("$.marches[1].designationMarche").value(
                        "Travaux d'urgence pour la construction d'un dalot au PK 194+450 sur la RNS 44"))
                .andExpect(jsonPath("$.marches[1].montEstim").value(140000000.00))
                .andExpect(jsonPath("$.marches[1].beneficiaires[0].ancMontBenef").value(140000000.00))
                // La correction est tracée (non silencieuse) — une par marché.
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*recoll.*objet.*/)]", hasSize(2)));
    }

    @Test
    @DisplayName("Import PPM — invariant SYMÉTRIQUE : fragment collé au MONTANT BÉNÉFICIAIRE (« 3 125 000 000 » pour un estimatif 125M) → recollé à l'objet, bénéficiaire réaligné")
    void importPpm_fragmentMontantBeneficiaire_regression() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        // Cas réel 379.PPM : montEstim correct (125M) mais le montant bénéficiaire est contaminé par un « 3 ».
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Fourniture d'habillement pour le personnel",
                "125 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 3 125 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                // « 3 » recollé à l'objet ; montant bénéficiaire ramené à 125M (== estimatif).
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Fourniture d'habillement pour le personnel 3"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(125000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(125000000.00))
                // Correction tracée + anomalie montant auto-corrigée (à confirmer).
                .andExpect(jsonPath("$.marches[0].anomalies[?(@.champ=='montEstim' && @.type=='MONTANT_INCOHERENT' && @.corrige==true)]", hasSize(1)))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*recoll.*objet.*/)]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — invariant symétrique sur la colonne NOUVEAU : fragment collé au nouveau montant bénéficiaire → recollé, réaligné")
    void importPpm_fragmentNouveauMontantBeneficiaire_regression() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Appel d'offres ouvert", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte", null, null));

        // montEstim 500M + nouvMontEstim 600M ; colonne « ancien » cohérente, mais le NOUVEAU montant
        // bénéficiaire est contaminé par un « 4 » (« 4 600 000 000 » au lieu de 600 000 000).
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Rehabilitation de la route nationale",
                "500 000 000.00 600 000 000.00 Appel d'offres ouvert RPI 00-61-0-D10-00000 2441 500 000 000.00 4 600 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Rehabilitation de la route nationale 4"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(500000000.00))
                .andExpect(jsonPath("$.marches[0].nouvMontEstim").value(600000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(500000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].nouvMontBenef").value(600000000.00))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*nouveau montant.*/)]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — anomalies structurées : ligne propre (0), montant auto-corrigé, objet tronqué, référentiel inconnu ; nbAVerifier")
    void importPpm_anomaliesStructurees() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Consultation de prix ouverte", null, null, null, null));
        soaBeneficiaireRepository.save(new cnm.prs.entity.SoaBeneficiaire("00-61-0-D10-00000", "SOA test"));
        compteRepository.save(new cnm.prs.entity.Compte("2441", "Compte test", null, null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                // [0] PROPRE : tout résout, montant == Σ, objet fini par un mot → 0 anomalie.
                "Travaux Rehabilitation de la route principale du secteur",
                "500 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 500 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                // [1] fragment « 33 » collé au montant → MONTANT_INCOHERENT auto-corrigé.
                "Travaux Reparation de la breche au PK 38+800 de la RNT",
                "33 590 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 590 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                // [2] objet tronqué (finit par RNS sans numéro), montant cohérent → OBJET_TRONQUE_PROBABLE.
                "Travaux Travaux de reparation du pont sur la RNS",
                "400 000 000.00 Consultation de prix ouverte RPI 00-61-0-D10-00000 2441 400 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                // [3] mode absent du référentiel → REFERENTIEL_INCONNU / mode (objet fini par un mot).
                "Travaux Rehabilitation de la route secondaire du district",
                "300 000 000.00 Achat Direct RPI 00-61-0-D10-00000 2441 300 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(4)))
                // 3 marchés sur 4 portent ≥1 anomalie ([0] est propre).
                .andExpect(jsonPath("$.nbAVerifier").value(3))
                .andExpect(jsonPath("$.marches[0].anomalies", hasSize(0)))
                // [1] montant auto-corrigé (champ + type + corrige exacts) ; objet et montant réalignés.
                .andExpect(jsonPath("$.marches[1].anomalies[?(@.champ=='montEstim' && @.type=='MONTANT_INCOHERENT' && @.gravite=='A_VERIFIER' && @.corrige==true)]", hasSize(1)))
                .andExpect(jsonPath("$.marches[1].montEstim").value(590000000.00))
                .andExpect(jsonPath("$.marches[1].designationMarche").value("Reparation de la breche au PK 38+800 de la RNT 33"))
                // [2] objet tronqué probable.
                .andExpect(jsonPath("$.marches[2].anomalies[?(@.champ=='objet' && @.type=='OBJET_TRONQUE_PROBABLE' && @.gravite=='A_VERIFIER')]", hasSize(1)))
                // [3] mode inconnu au référentiel.
                .andExpect(jsonPath("$.marches[3].anomalies[?(@.champ=='mode' && @.type=='REFERENTIEL_INCONNU')]", hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — mode non résolu mais proche : avertissement « vouliez-vous dire … ? » (pas d'auto-résolution fuzzy)")
    void importPpm_modeSuggestion() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        modePassationRepository.save(new ModePassation(1, "Appel d'offres ouvert", null, null, null, null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Rehabilitation de piste",
                "5 550 000.00 Apel d'offres ouvert RPI 00-21-0-J00-00000 6211 5 550 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches[0].idMode").value(nullValue()))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("Apel d'offres ouvert"))
                .andExpect(jsonPath(
                        "$.avertissements[?(@ =~ /.*Apel d'offres ouvert.*vouliez-vous dire.*Appel d'offres ouvert.*/)]",
                        hasSize(1)));
    }

    @Test
    @DisplayName("Import PPM — régression 161-PPM MTP : « 11,700 Km » dans la désignation ne casse plus le découpage des colonnes")
    void importPpm_nombreDansDesignation_regression() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        ModePassation aoo = new ModePassation(1, "Appel d'offres ouvert", null, null, null, null);
        aoo.setDeclencheAgpm(true);
        modePassationRepository.save(aoo);

        // Ligne réelle de 161-PPM MTP.pdf : kilométrage « 11,700 Km (contrat cadre) » DANS la désignation.
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Travaux d'amenagement de la voie rapide dans la Commune Urbaine de Sambava,",
                "croisement Menagisy vers Ambodisatrana de longueur 11,700 Km (contrat cadre)",
                "15 000 000 000.00 APPEL D'OFFRE OUVERT RPI 00-61-0-D10-00000 2441 15 000 000 000.00 06/03/2026 20/03/2026 31/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                // Désignation INTÉGRALE, kilométrage compris — plus de troncature sur « 11,70 ».
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Travaux d'amenagement de la voie rapide dans la Commune Urbaine de Sambava, "
                                + "croisement Menagisy vers Ambodisatrana de longueur 11,700 Km (contrat cadre)"))
                // Forme du marché relevée dans l'objet : « (contrat cadre) » → CONTRAT_CADRE.
                .andExpect(jsonPath("$.marches[0].formeMarche").value("CONTRAT_CADRE"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(15000000000.00))
                // Mode résolu canonique (règle pluriel) — plus d'avertissement « Km contrat ».
                .andExpect(jsonPath("$.marches[0].idMode").value(1))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("Appel d'offres ouvert"))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-61-0-D10-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("2441"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(15000000000.00))
                .andExpect(jsonPath("$.marches[0].previsions[0].dateDebut").value("2026-03-06"))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*Km.*/)]", hasSize(0)));
    }

    @Test
    @DisplayName("Import PPM — régression 161-PPM MTP : dimensions « 2,00 x 2,00 m » + point kilométrique « PK 208+000 » dans la désignation")
    void importPpm_dimensionsEtPkDansDesignation_regression() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        ModePassation cpo = new ModePassation(1, "Consultation de prix ouverte", null, null, null, null);
        modePassationRepository.save(cpo);

        // Ligne réelle de 161-PPM MTP.pdf : dimensions d'un dalot DANS la désignation, et « 208 » du
        // point kilométrique qui, sur une mauvaise ancre, se fait passer pour un numéro de compte.
        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Travaux d'urgence sur la construction d'un dalot 2,00 x 2,00 m et reparation",
                "du chaussee sur la RNS 5A PK 208+000 Fokontany Angalovanga District Vohemar",
                "200 000 000.00 CONSULTATION DE PRIX OUVERTE RPI 00-61-0-D10-00000 2441 200 000 000.00 06/03/2026 16/03/2026 27/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(1)))
                // Désignation INTÉGRALE, dimensions et PK compris — plus d'ancrage sur « 2,00 ».
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Travaux d'urgence sur la construction d'un dalot 2,00 x 2,00 m et reparation "
                                + "du chaussee sur la RNS 5A PK 208+000 Fokontany Angalovanga District Vohemar"))
                // Aucune forme mentionnée dans l'objet → défaut QUANTITE_FIXE.
                .andExpect(jsonPath("$.marches[0].formeMarche").value("QUANTITE_FIXE"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(200000000.00))
                .andExpect(jsonPath("$.marches[0].idMode").value(1))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("Consultation de prix ouverte"))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-61-0-D10-00000"))
                // Le vrai compte 2441 — plus le « 208 » du point kilométrique.
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("2441"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(200000000.00))
                .andExpect(jsonPath("$.marches[0].previsions[0].dateDebut").value("2026-03-06"))
                .andExpect(jsonPath("$.avertissements[?(@ =~ /.*reparation.*/)]", hasSize(0)));
    }

    @Test
    @DisplayName("Import PPM — forme du marché relevée dans l'objet : « (Contrat Cadre) » → CONTRAT_CADRE, « marches a commandes » → A_COMMANDE, désignations intégrales")
    void importPpm_formeMarcheDetectee() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));

        byte[] pdf = pdfAvecTexte(
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "Travaux Travaux d'entretien periodique de la RN 13 (Contrat Cadre)",
                "15 000 000.00 APPEL D'OFFRE OUVERT RPI 00-61-0-D10-00000 2441 15 000 000.00 06/03/2026 20/03/2026 31/03/2026",
                "Travaux Fourniture de carburants, marches a commandes, pour le parc automobile",
                "5 000 000.00 ACHAT DIRECT RPI 00-61-0-D10-00000 2441 5 000 000.00 06/03/2026 20/03/2026 31/03/2026",
                "Fait a Antananarivo le 14 avril 2026");

        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marches", hasSize(2)))
                // On relève, on ne retire pas : la mention reste dans la désignation.
                .andExpect(jsonPath("$.marches[0].designationMarche").value(
                        "Travaux d'entretien periodique de la RN 13 (Contrat Cadre)"))
                .andExpect(jsonPath("$.marches[0].formeMarche").value("CONTRAT_CADRE"))
                // Pluriels tolérés (« marches a commandes ») ; frontières de mots respectées.
                .andExpect(jsonPath("$.marches[1].designationMarche").value(
                        "Fourniture de carburants, marches a commandes, pour le parc automobile"))
                .andExpect(jsonPath("$.marches[1].formeMarche").value("A_COMMANDE"));
    }

    @Test
    @DisplayName("Import PPM PDF multi-pages : les 2 pages sont lues (en-tête/pied répétés ignorés, borne sur le dernier « Fait à … »)")
    void importPpm_multiPages_ok() throws Exception {
        natureRepository.save(new Nature(1, "Travaux", null));
        natureRepository.save(new Nature(2, "Fournitures", null));

        // Page 1 : en-tête doc + tableau (1 marché), puis pied de page RÉPÉTÉ (« Fait à … » + n° de page).
        String[] page1 = {
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE TEST",
                "Date d'etablissement du Document initial: 14/04/2026",
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "SERVICE BENEFICIAIRE COMPTE MONTANT ESTIMATIF PAR BENEFICIAIRE",
                "Travaux Travaux de fabrication et",
                "installation des etageres",
                "5 550 000.00 Achat Direct RPI 00-21-0-J00-00000 6211 5 550 000.00 01/06/2026 02/06/2026 12/06/2026",
                "Fait a Antananarivo le 14 avril 2026",   // pied répété : NE doit PAS clore le tableau
                "Page 1 sur 2" };
        // Page 2 : en-tête de colonnes REJOUÉ, 2e marché, puis pied final (dernier « Fait à … » = vraie fin).
        String[] page2 = {
                "NATURE OBJET MONTANT ESTIMATIF INITIAL",
                "SERVICE BENEFICIAIRE COMPTE MONTANT ESTIMATIF PAR BENEFICIAIRE",
                "Fournitures Fournitures de bureau",
                "diverses",
                "1 200 000.00 Appel d'Offres RPI 00-21-0-J00-00001 6212 1 200 000.00 03/07/2026 04/07/2026 14/07/2026",
                "Fait a Antananarivo le 14 avril 2026",
                "Page 2 sur 2" };

        byte[] pdf = pdfMultiPages(page1, page2);
        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                // Les DEUX marchés (page 1 et page 2) sont lus.
                .andExpect(jsonPath("$.marches", hasSize(2)))
                .andExpect(jsonPath("$.marches[0].designationMarche").value("Travaux de fabrication et installation des etageres"))
                .andExpect(jsonPath("$.marches[0].natureLibelle").value("Travaux"))
                .andExpect(jsonPath("$.marches[1].designationMarche").value("Fournitures de bureau diverses"))
                .andExpect(jsonPath("$.marches[1].natureLibelle").value("Fournitures"))
                .andExpect(jsonPath("$.marches[1].modeLibelle").value("Appel d'Offres"))
                .andExpect(jsonPath("$.marches[1].previsions[0].dateDebut").value("2026-07-03"));
    }

    @Test
    @DisplayName("Import PPM — format MIDSP : en-tête multi-lignes ignoré, NATURE MAJ (dont 2 lignes), mode multi-lignes, multi-bénéficiaires, nouvMontEstim, PIP")
    void importPpm_formatMidsp_ok() throws Exception {
        natureRepository.save(new Nature(1, "FOURNITURES", null));
        natureRepository.save(new Nature(2, "TRAVAUX", null));
        natureRepository.save(new Nature(3, "PRESTATIONS DE SERVICE", null));

        // Page 1 : en-tête doc + en-tête de colonnes ÉCLATÉ sur plusieurs lignes + 2 marchés.
        String[] page1 = {
                "PPM_26-488-0078 page 1/2 18/06/2026 05:55",
                "PLAN DE PASSATION DES MARCHES POUR L'ANNEE 2026",
                "Autorite Contractante: MINISTERE DE L'INDUSTRIALISATION ET DU DEVELOPPEMENT DU",
                "SECTEUR PRIVE",
                "Nom de la PRMP: RAHERIVELO Fanja - MIDSP",
                "Date d'etablissement du Document initial: 03/02/2026",
                "NATURE OBJET", "MONTANT", "ESTIMATIF", "INITIAL", "NOUVEAU", "MONTANT", "ESTIMATIF",
                "MODE DE PASSATION FINAN-", "CEMENT", "Informations sur le Beneficiaire DATE",
                "SERVICE", "BENEFICIAIRE COMPTE",
                // Marché 1 : NATURE MAJ seule, OBJET multi-lignes, 2 bénéficiaires (anc + nouv), nouvMontEstim.
                "FOURNITURES",
                "Fourniture de cartes recharges",
                "telephoniques pour le Ministere",
                "645 442 000.00 645 442 000.00 ACHAT DIRECT RPI",
                "00-34-0-A00-00000",
                "00-34-0-B00-00000",
                "6263",
                "35 000 000.00",
                "25 000 000.00",
                "35 000 000.00",
                "25 000 000.00",
                "22/06/2026 29/06/2026 30/06/2026",
                // Marché 2 : mode sur 2 lignes (CONSULTATION DE / PRIX OUVERTE), 1 bénéficiaire (anc + nouv).
                "TRAVAUX",
                "Travaux d'entretien de batiments",
                "53 200 000.00 107 000 000.00 CONSULTATION DE",
                "PRIX OUVERTE",
                "RPI 00-34-0-B10-00000 6211 53 200 000.00 107 000 000.00 29/06/2026 10/07/2026 22/07/2026",
                "Fait a Antananarivo le _ _/_ _/_ _ _ _" };
        // Page 2 : NATURE sur 2 lignes, financement PIP, 1 bénéficiaire SANS nouveau montant, pas de nouvMontEstim.
        String[] page2 = {
                "PPM_26-488-0078 page 2/2 18/06/2026 05:55",
                "PRESTATIONS DE",
                "SERVICE",
                "Frais de colloque pour le Ministere",
                "20 000 000.00 CONSULTATION DE PRIX OUVERTE PIP 00-34-0-D00-00000 6225 20 000 000.00 22/06/2026 02/07/2026 13/07/2026",
                "Fait a Antananarivo le 03 fevrier 2026",
                "LA PERSONNE RESPONSABLE DES MARCHES PUBLICS" };

        byte[] pdf = pdfMultiPages(page1, page2);
        mvc.perform(multipart("/api/saisies/ppm/import")
                .file(new MockMultipartFile("fichier", "ppm.pdf", "application/pdf", pdf))
                .header("Authorization", tokenPrmp))
                .andExpect(status().isOk())
                // Autorité contractante recomposée sur 2 lignes.
                .andExpect(jsonPath("$.autoriteContractante").value("MINISTERE DE L'INDUSTRIALISATION ET DU DEVELOPPEMENT DU SECTEUR PRIVE"))
                .andExpect(jsonPath("$.marches", hasSize(3)))
                // Marché 1 : nature MAJ, nouvMontEstim capté, 2 bénéficiaires alignés (anc + nouv), compte partagé.
                .andExpect(jsonPath("$.marches[0].natureLibelle").value("FOURNITURES"))
                .andExpect(jsonPath("$.marches[0].idNature").value(1))
                .andExpect(jsonPath("$.marches[0].modeLibelle").value("ACHAT DIRECT"))
                .andExpect(jsonPath("$.marches[0].financement").value("RPI"))
                .andExpect(jsonPath("$.marches[0].montEstim").value(645442000.00))
                .andExpect(jsonPath("$.marches[0].nouvMontEstim").value(645442000.00))
                .andExpect(jsonPath("$.marches[0].designationMarche").value("Fourniture de cartes recharges telephoniques pour le Ministere"))
                .andExpect(jsonPath("$.marches[0].beneficiaires", hasSize(2)))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].soaCode").value("00-34-0-A00-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].numCompte").value("6263"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].ancMontBenef").value(35000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[0].nouvMontBenef").value(35000000.00))
                .andExpect(jsonPath("$.marches[0].beneficiaires[1].soaCode").value("00-34-0-B00-00000"))
                .andExpect(jsonPath("$.marches[0].beneficiaires[1].ancMontBenef").value(25000000.00))
                // Marché 2 : mode recomposé sur 2 lignes + nouvMontEstim.
                .andExpect(jsonPath("$.marches[1].natureLibelle").value("TRAVAUX"))
                .andExpect(jsonPath("$.marches[1].modeLibelle").value("CONSULTATION DE PRIX OUVERTE"))
                .andExpect(jsonPath("$.marches[1].nouvMontEstim").value(107000000.00))
                .andExpect(jsonPath("$.marches[1].beneficiaires", hasSize(1)))
                // Marché 3 : NATURE sur 2 lignes, financement PIP, 1 bénéficiaire SANS nouveau montant.
                .andExpect(jsonPath("$.marches[2].natureLibelle").value("PRESTATIONS DE SERVICE"))
                .andExpect(jsonPath("$.marches[2].idNature").value(3))
                .andExpect(jsonPath("$.marches[2].financement").value("PIP"))
                .andExpect(jsonPath("$.marches[2].nouvMontEstim").value(nullValue()))
                .andExpect(jsonPath("$.marches[2].beneficiaires", hasSize(1)))
                .andExpect(jsonPath("$.marches[2].beneficiaires[0].ancMontBenef").value(20000000.00))
                .andExpect(jsonPath("$.marches[2].beneficiaires[0].nouvMontBenef").value(nullValue()));
    }

    /** PDF multi-pages (PDFBox) : une page physique par tableau de lignes — pour tester le parsing multi-pages. */
    private byte[] pdfMultiPages(String[]... pages) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            for (String[] lignes : pages) {
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
                doc.addPage(page);
                try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                        new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 11);
                    cs.setLeading(16f);
                    cs.newLineAtOffset(50, 750);
                    for (String l : lignes) {
                        cs.showText(l);
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Génère en mémoire un PDF (PDFBox) contenant les lignes de texte fournies — pour tester le parsing d'import. */
    private byte[] pdfAvecTexte(String... lignes) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                    new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 11);
                cs.setLeading(16f);
                cs.newLineAtOffset(50, 750);
                for (String l : lignes) {
                    cs.showText(l);
                    cs.newLine();
                }
                cs.endText();
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
