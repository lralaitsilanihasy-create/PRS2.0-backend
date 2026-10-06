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
                "3:Spécifications techniques",
                "4:Annexe : Liste des fournitures et calendrier de livraison",
                "2:2.3. - Cahier des Clauses Administratives Générales applicable aux marchés publics de fournitures et de "
                        + "prestations de services courantes");
        assertThat(p.subList(1, 10)).extracting(x -> (int) x.docx()[0]).containsExactly(6, 9, 5, 11, 4, 2, 3, 99, 1);
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
                "2:2.2. - Cahier des Prescriptions Spéciales|2:2.3. - Cahier des Clauses Administratives Générales applicable aux "
                        + "marchés publics de travaux");
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
    @DisplayName("Page de garde (C1) : ministère, entité, PRMP, UGPM, intitulé portant le mode, n°, objet, lots, « Lancé le », "
            + "financement, imputation (sans les montants), compte")
    void garde() {
        FicheMarcheDto etat = etat("FOURNITURES_SERVICES", "QUANTITE_FIXE");
        etat.setValeurs(Map.of("B02-OB-03", "001-DAOO/MEN/PRMP/Tvx-PI-2026"));
        etat.setDesignationMarche("Acquisition de matériels informatiques");
        List<String> l = new DaoCompletService(null, null, null, null, null).garde(etat, Map.of("MINISTERE", "Ministère de l'Éducation nationale",
                "ENTITE", "Direction des affaires financières", "MODE", "Appel d'offres ouvert", "LOTS_DESIGNATION", "Ordinateurs ; Imprimantes",
                "FINANCEMENT", "RPI", "BENEFICIAIRES", "00-71-0-110 – DAF (8 400 000 Ariary)", "COMPTES", "2321"))
                .stream().map(DaoCompletWord.LigneGarde::texte).filter(t -> !t.isEmpty()).toList();
        assertThat(l).containsExactly("MINISTÈRE DE L'ÉDUCATION NATIONALE", "DIRECTION DES AFFAIRES FINANCIÈRES",
                "PERSONNE RESPONSABLE DES MARCHÉS PUBLICS", "UNITÉ DE GESTION DE LA PASSATION DES MARCHÉS",
                "DOSSIER D'APPEL D'OFFRES OUVERT", "N° 001-DAOO/MEN/PRMP/Tvx-PI-2026", "Acquisition de matériels informatiques",
                "Lot 1 : Ordinateurs", "Lot 2 : Imprimantes", "Lancé le …………………………", "Financement : RPI",
                "Imputation administrative : 00-71-0-110 – DAF", "Compte : 2321");
        assertThat(DaoCompletService.intitule("Consultation de prix", false)).isEqualTo("DOSSIER D'APPEL D'OFFRES");
        assertThat(DocumentsFicheMarcheService.nomFichier("DAO_COMPLET", "001-DAOO/MEN/PRMP/Tvx-PI-2026", 303279, 1, "pdf"))
                .isEqualTo("DAO_COMPLET_001-DAOO-MEN-PRMP-Tvx-PI-2026_303279_v1.pdf");
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
