package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.DocumentFicheMarche;
import cnm.prs.entity.SpecificationsFiche;
import cnm.prs.repository.SpecificationsFicheRepository;

/**
 * ⚠️ 2026-10-06 (DAO complet ; recette §C, C1 et C3) — le plan de l'ARMP : deux parties numérotées 1.1 à 1.3 et 2.1 à 2.3, les
 * sous-parties des formulaires, l'acte d'engagement lot par lot, les spécifications techniques dans la 2.2 ; la page de garde.
 */
class DaoCompletPartiesTest {

    @Test
    @DisplayName("Fournitures : première partie (IC, DPAO, formulaires A et C), deuxième partie (AE par lot, CCAP, spécifications "
            + "et liste des fournitures, CCAG) ; avis et classeurs exclus")
    void fournitures() {
        SpecificationsFicheRepository specs = mock(SpecificationsFicheRepository.class);
        SpecificationsFiche s = new SpecificationsFiche();
        s.setContenu(new byte[] { 99 });
        when(specs.findById(7)).thenReturn(Optional.of(s));
        FicheMarcheDto etat = etat("FOURNITURES_SERVICES", "QUANTITE_FIXE");
        List<DocumentFicheMarche> docs = List.of(doc("LF", "docx", null, 1), doc("AE", "docx", 2, 2), doc("CCAP", "docx", null, 3),
                doc("AE", "docx", 1, 4), doc("A2", "docx", null, 5), doc("DPAO", "docx", null, 6), doc("DPAO", "pdf", null, 7),
                doc("BP", "xlsx", null, 8), doc("A1", "docx", null, 9), doc("AVIS", "docx", null, 10), doc("C1", "docx", null, 11));
        List<DaoCompletWord.Partie> p = new DaoCompletService(null, null, null, specs, null).parties(etat, 7, docs);
        assertThat(p).extracting(DaoCompletPartiesTest::titres).containsExactly(
                "1:PREMIÈRE PARTIE : PROCÉDURE D'APPEL D'OFFRES|2:1.1. - Instructions aux candidats",
                "2:1.2. - Données Particulières de l'Appel d'Offres (DPAO)",
                "2:1.3. - Formulaires de soumission|3:A. - Modèles de fiches de renseignements|4:A1 - Identification du Candidat",
                "4:A2 - Capacités techniques",
                "3:B. - Modèle d'attestation du fabricant - Non utilisé|3:C. - Modèles de garantie de soumission"
                        + "|4:C1 - Modèle de garantie bancaire",
                "1:DEUXIÈME PARTIE : MARCHÉ|2:2.1. - Acte d'Engagement|3:Lot 1",
                "3:Lot 2",
                "2:2.2. - Cahier des Prescriptions Spéciales|3:Cahier des Clauses Administratives Particulières (CCAP) et ses annexes",
                // ⚠️ 17/10 (« specifications-donnees-et-plans-pdf », A2) — un Word de fournitures n'entre plus : le titre des
                // spécifications rejoint la liste des fournitures.
                "3:Spécifications techniques|4:Annexe : Liste des fournitures et calendrier de livraison",
                "2:2.3. - Cahier des Clauses Administratives Générales applicable aux marchés publics de fournitures et de "
                        + "prestations de services courantes");
        assertThat(p.subList(1, 9)).extracting(x -> (int) x.docx()[0]).containsExactly(6, 9, 5, 11, 4, 2, 3, 1);
    }

    @Test
    @DisplayName("⚠️ 16/10 (charte, §B3) — avec un besoin : le bordereau en annexe 1 de l'AE ; en 2.2 les spécifications, le Word de la "
            + "PRMP après, la liste générée (le document LF n'entre plus) ; travaux : bordereau des prix unitaires puis DQE")
    void tableauxDuBesoin() {
        SpecificationsFicheRepository specs = mock(SpecificationsFicheRepository.class);
        SpecificationsFiche s = new SpecificationsFiche();
        s.setContenu(new byte[] { 99 });
        when(specs.findById(7)).thenReturn(Optional.of(s));
        BesoinFiche besoin = mock(BesoinFiche.class);
        cnm.prs.dto.ArticleBesoinDto a = new cnm.prs.dto.ArticleBesoinDto();
        a.setOrdre(1);
        a.setDesignation("Rame de papier");
        a.setUnite("Rame");
        a.setQuantite(java.math.BigDecimal.TEN);
        a.setNumeroPrix("001");
        a.setCaracteristiques(List.of());
        when(besoin.lister(7)).thenReturn(List.of(a));
        DaoCompletService service = new DaoCompletService(null, null, null, specs, null, besoin, null);
        List<DaoCompletWord.Partie> p = service.parties(etat("FOURNITURES_SERVICES", "QUANTITE_FIXE"), 7, List.of(doc("AE", "docx", null, 1),
                doc("CCAP", "docx", null, 2), doc("LF", "docx", null, 3)));
        String plan = p.stream().map(DaoCompletPartiesTest::titres).collect(Collectors.joining(" / "));
        assertThat(plan).contains("2:2.1. - Acte d'Engagement / 3:Annexe 1 : Cadre du bordereau des prix / 2:2.2.",
                "3:Spécifications techniques / 4:Annexe : Liste des fournitures et calendrier de livraison");
        // ⚠️ 17/10 (« specifications-donnees-et-plans-pdf », A2) — en fournitures, un Word n'entre plus : le besoin suffit.
        assertThat(p).noneMatch(x -> x.docx() != null && x.docx().length == 1 && x.docx()[0] == 99);
        assertThat(p).noneMatch(x -> x.docx() != null && x.docx().length == 1 && x.docx()[0] == 3);   // le document LF n'entre plus
        assertThat(p.stream().filter(x -> x.titres().stream().anyMatch(t -> t.niveau() == 2)).allMatch(DaoCompletWord.Partie::separateur)).isTrue();
        String travaux = service.parties(etat("TRAVAUX", "QUANTITE_FIXE"), 7, List.of(doc("AE", "docx", null, 1))).stream()
                .map(DaoCompletPartiesTest::titres).collect(Collectors.joining(" / "));
        assertThat(travaux).contains("3:Annexe : Bordereau des prix unitaires / 3:Annexe : Détail quantitatif et estimatif").doesNotContain("Cadre du bordereau");
    }

    @Test
    @DisplayName("Travaux : garanties B1 / B2 sous « B. », pas d'attestation du fabricant ; titres restés sans document reportés")
    void travaux() {
        SpecificationsFicheRepository specs = mock(SpecificationsFicheRepository.class);
        when(specs.findById(7)).thenReturn(Optional.empty());
        List<DaoCompletWord.Partie> p = new DaoCompletService(null, null, null, specs, null).parties(etat("TRAVAUX", "QUANTITE_FIXE"), 7,
                List.of(doc("DPAO", "docx", null, 1), doc("C2", "docx", null, 2), doc("AE", "docx", null, 3)));
        assertThat(p).extracting(DaoCompletPartiesTest::titres).containsSubsequence(
                "2:1.3. - Formulaires de soumission|3:B. - Modèles de garantie de soumission|4:B2 - Caution de soumission",
                "1:DEUXIÈME PARTIE : MARCHÉ|2:2.1. - Acte d'Engagement",
                // ⚠️ 17/10 (charte travaux, T5 à T7) — K1 et sous-détail en annexe de l'AE, formulaires du candidat en annexe des
                // spécifications techniques.
                "3:Annexe : Mode de calcul du coefficient de majoration des déboursés K1", "3:Annexe : Cadre du sous-détail des prix",
                "2:2.2. - Cahier des Prescriptions Spéciales|3:Spécifications techniques|4:Annexe 1 : Liste des plans",
                "4:Annexe 2 : Liste du personnel affecté aux lots et modèle de curriculum vitae", "4:Annexe 3 : Liste du matériel affecté aux lots",
                "4:Annexe 4 : Modèle d'engagement de location de matériel", "4:Annexe 5 : Modèles de plannings d'exécution",
                "4:Annexe 6 : Modèle de plan de charge",
                "2:2.3. - Cahier des Clauses Administratives Générales applicable aux marchés publics de travaux");
        // La fourniture n'en a aucun.
        assertThat(new DaoCompletService(null, null, null, specs, null).parties(etat("FOURNITURES_SERVICES", "QUANTITE_FIXE"), 7,
                List.of(doc("AE", "docx", null, 3))).stream().map(DaoCompletPartiesTest::titres).collect(Collectors.joining(" / ")))
                .doesNotContain("K1", "Liste des plans", "plannings");
    }

    @Test
    @DisplayName("Prestations intellectuelles : procédure de consultation, 1.1 lettre d'invitation à part, 1.2 IC, 1.3 DPIC ; contrat-cadre : "
            + "DPAC en 1.2 et contrat-cadre en 2.1")
    void prestationsEtContratCadre() {
        SpecificationsFicheRepository specs = mock(SpecificationsFicheRepository.class);
        when(specs.findById(7)).thenReturn(Optional.empty());
        DaoCompletService service = new DaoCompletService(null, null, null, specs, null);
        assertThat(service.parties(etat("PRESTATIONS_INTELLECTUELLES", "QUANTITE_FIXE"), 7, List.of(doc("DPIC", "docx", null, 1))))
                .extracting(DaoCompletPartiesTest::titres).startsWith("1:PREMIÈRE PARTIE : PROCÉDURE DE CONSULTATION"
                        + "|2:1.1. - Lettre d'invitation (adressée à chaque candidat, document à part)|2:1.2. - Instructions aux candidats (IC)",
                        "2:1.3. - Données Particulières des Instructions aux Candidats (DPIC)");
        assertThat(service.parties(etat("FOURNITURES_SERVICES", "CONTRAT_CADRE"), 7, List.of(doc("DPAC", "docx", null, 1),
                doc("AE", "docx", null, 2)))).extracting(DaoCompletPartiesTest::titres).containsSubsequence(
                        "2:1.2. - Données Particulières d'Appel à Concurrence (DPAC)",
                        "1:DEUXIÈME PARTIE : MARCHÉ|2:2.1. - Contrat-cadre valant Acte d'Engagement et Cahier des Clauses "
                                + "Administratives Particulières");
    }

    @Test
    @DisplayName("Page de garde (C1 ; ⚠️ charte du 16/10, §B2) : ministère, entité, PRMP séparés par des tirets, intitulé portant le mode, n°, « Lancé le », objet, lots, "
            + "financement, imputation (sans les montants), compte")
    void garde() {
        FicheMarcheDto etat = etat("FOURNITURES_SERVICES", "QUANTITE_FIXE");
        etat.setValeurs(Map.of("B02-OB-03", "001-DAOO/MEN/PRMP/Tvx-PI-2026"));
        etat.setDesignationMarche("Acquisition de matériels informatiques");
        List<String> l = new DaoCompletService(null, null, null, null, null).garde(etat, Map.of("MINISTERE", "Ministère de l'Éducation nationale",
                "ENTITE", "Direction des affaires financières", "MODE", "Appel d'offres ouvert", "LOTS_DESIGNATION", "Ordinateurs ; Imprimantes",
                "FINANCEMENT", "RPI", "BENEFICIAIRES", "00-71-0-110 – DAF (8 400 000 Ariary)", "COMPTES", "2321"))
                .stream().map(DaoCompletWord.LigneGarde::texte).filter(t -> !t.isEmpty()).toList();
        // ⚠️ 16/10 (charte, §B2) — le bloc de l'autorité séparé par des tirets, « Lancé le », l'objet, puis financement et imputation
        // budgétaire (les comptes du plan, B01-AC-17).
        assertThat(l).containsExactly("MINISTÈRE DE L'ÉDUCATION NATIONALE", "-------------", "DIRECTION DES AFFAIRES FINANCIÈRES", "-------------",
                "PERSONNE RESPONSABLE DES MARCHÉS PUBLICS", "-------------", "DOSSIER D'APPEL D'OFFRES OUVERT", "N° 001-DAOO/MEN/PRMP/Tvx-PI-2026",
                "Lancé le …………………………", "Objet : Acquisition de matériels informatiques", "Lot 1 : Ordinateurs", "Lot 2 : Imprimantes",
                "FINANCEMENT : RPI", "IMPUTATION BUDGETAIRE : 00-71-0-110 – DAF", "COMPTE : 2321");   // ⚠️ 17/10 (T1) : B01-AC-16 puis B01-AC-17
        // La date de lancement saisie (B04-CP-01), à défaut celle de la validation ; une ligne non allotie : « en un lot unique ».
        FicheMarcheDto cc = etat("FOURNITURES_SERVICES", "CONTRAT_CADRE");
        cc.setValeurs(Map.of("B02-OB-03", "07-CC/MEF", "B04-CP-01", "2027-01-18"));
        cc.setDesignationMarche("Fourniture d'articles de bureau");
        DaoCompletWord.Charte charte = new DaoCompletService(null, null, null, null, null).charte(cc, Map.of("MINISTERE",
                "Ministère de l'Économie et des Finances", "ENTITE", "Secrétariat général"), 3, java.time.LocalDateTime.of(2027, 1, 12, 9, 0));
        assertThat(charte.garde()).extracting(DaoCompletWord.LigneGarde::texte).contains("Lancé le 18/01/2027",
                "Objet : Fourniture d'articles de bureau, en un lot unique");
        assertThat(charte.separateur()).extracting(DaoCompletWord.LigneGarde::texte).contains("SECRÉTARIAT GÉNÉRAL", "N° 07-CC/MEF",
                "Objet : Fourniture d'articles de bureau").doesNotContain("FINANCEMENT : RPI");
        assertThat(charte.enteteGauche()).isEqualTo("Sommaire général");   // ⚠️ 17/10 (T2) : la partie en cours à gauche, le n° à droite
        assertThat(charte.enteteDroite()).isEqualTo("DAO n° 07-CC/MEF");
        assertThat(DaoCompletService.intituleCourt("1.2. - Données Particulières de l'Appel d'Offres (DPAO)")).isEqualTo("Données particulières de l'appel d'offres");
        assertThat(DaoCompletService.intituleCourt("2.1. - Acte d'Engagement")).isEqualTo("Cadre d'acte d'engagement");
        assertThat(DaoCompletService.intituleCourt("2.3. - Cahier des Clauses Administratives Générales applicable aux marchés publics de travaux"))
                .isEqualTo("Cahier des clauses administratives générales");
        assertThat(DaoCompletService.intituleCourt("1.3. - Formulaires de soumission")).isEqualTo("Formulaires de soumission");
        assertThat(charte.piedGauche()).isEqualTo("Secrétariat général — PRMP");
        assertThat(charte.piedCentre()).isEqualTo("Fiche v3 validée le 12/01/2027");
        assertThat(DaoCompletService.lance(cc, null)).isEqualTo("18/01/2027");
        // ⚠️ R1 (recette du 16/10) — un contrat-cadre porte son numéro en B02-OE-01 (« NUMÉRO » du DPAC), avant B02-OB-03 et le plan.
        cc.setValeurs(Map.of("B02-OB-03", "07-CC/MEF", "B02-OE-01", "07-CC/MEF/SG/PRMP.2027"));
        assertThat(DaoCompletService.numero(cc)).isEqualTo("07-CC/MEF/SG/PRMP.2027");
        cc.setValeurs(Map.of());
        cc.setRefeDossier("00001/PPM-AGPM/CNM/2026");
        assertThat(DaoCompletService.numero(cc)).isEqualTo("00001/PPM-AGPM/CNM/2026");
        FicheMarcheDto qf = etat("FOURNITURES_SERVICES", "QUANTITE_FIXE");
        qf.setValeurs(Map.of("B02-OE-01", "ignoré", "B02-OB-03", "001-DAOO"));
        assertThat(DaoCompletService.numero(qf)).isEqualTo("001-DAOO");
        assertThat(DaoCompletService.intitule("Consultation de prix", false)).isEqualTo("DOSSIER D'APPEL D'OFFRES");
        assertThat(DocumentsFicheMarcheService.nomFichier("DAO_COMPLET", "001-DAOO/MEN/PRMP/Tvx-PI-2026", 303279, 1, "pdf"))
                .isEqualTo("DAO_COMPLET_001-DAOO-MEN-PRMP-Tvx-PI-2026_303279_v1.pdf");
        // ⚠️ Contre-recette du 06/10 — accents translittérés (é → e) ; D1 : l'entité qui ne diffère que par l'accent n'est pas répétée.
        assertThat(DocumentsFicheMarcheService.nomFichier("DAO_COMPLET", "Recette D4 Numéro du dossier d’appel", 303121, 7, "pdf"))
                .isEqualTo("DAO_COMPLET_Recette-D4-Numero-du-dossier-d-appel_303121_v7.pdf");
        assertThat(new DaoCompletService(null, null, null, null, null).garde(etat, Map.of("MINISTERE", "MINISTÈRE DES TRAVAUX PUBLICS",
                "ENTITE", "Ministere des  travaux publics")).stream().map(DaoCompletWord.LigneGarde::texte)
                .filter(t -> t.contains("TRAVAUX"))).containsExactly("MINISTÈRE DES TRAVAUX PUBLICS");
        // ⚠️ 17/10 (charte travaux, T1, Q1) — la localisation sous chaque lot des travaux, rien sans valeur.
        FicheMarcheDto tx = etat("TRAVAUX", "QUANTITE_FIXE");
        tx.setDesignationMarche("Entretien des routes");
        tx.setValeurs(Map.of("B02-OT-03#1", "Région Ihorombe", "B02-OT-04#1", "PK 0 à PK 32", "B02-OT-05#1", "22°11'58\"S 45°18'04\"E",
                "B02-OT-06#1", "22°20'37\"S 45°45'03\"E", "B02-OT-03#2", "Région Ihorombe"));
        List<String> lignes = new DaoCompletService(null, null, null, null, null).garde(tx, Map.of("LOTS_DESIGNATION",
                "Lot 1 : Ankily – Mahasoa ; Lot 2 : Mahasoa – Ihosy")).stream().map(DaoCompletWord.LigneGarde::texte).toList();
        assertThat(lignes).containsSubsequence("Lot 1 : Ankily – Mahasoa", "Région Ihorombe, PK 0 à PK 32",
                "Coordonnées GPS : début 22°11'58\"S 45°18'04\"E — fin 22°20'37\"S 45°45'03\"E", "Lot 2 : Mahasoa – Ihosy",
                "Région Ihorombe", "MARCHES PUBLICS DE TRAVAUX");
        assertThat(lignes.stream().filter(x -> x.startsWith("Coordonnées GPS"))).hasSize(1);
    }

    private static String titres(DaoCompletWord.Partie p) {
        return p.titres().stream().map(t -> t.niveau() + ":" + t.texte()).collect(Collectors.joining("|"));
    }

    private static FicheMarcheDto etat(String categorie, String type) {
        FicheMarcheDto etat = new FicheMarcheDto();
        etat.setCategorie(categorie);
        etat.setTypeMarche(type);
        return etat;
    }

    private static DocumentFicheMarche doc(String type, String ext, Integer lot, int marque) {
        return new DocumentFicheMarche(marque, 7, type, ext, type + "." + ext, 1L, "", LocalDateTime.now(), new byte[] { (byte) marque }, lot,
                null);
    }
}
