package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cnm.prs.config.AssistantIaProperties;
import cnm.prs.config.AssistantIaProperties.DocumentCorpus;
import cnm.prs.service.CorpusIaService.Passage;

/** Corpus de l'assistant IA : découpage des PDF par page, des Markdown par titre, chargement tolérant. */
class CorpusIaServiceTest {

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Markdown : un passage par section, référence = chemin des titres sous le niveau 1, sous-titre en gras compris")
    void markdown_sectionsEtReferences() {
        String md = """
                # Règles de gestion — Application CNM

                ## 1. Hiérarchie des contrôleurs

                Le Président voit toutes les localités ; le Chef de commission voit sa seule localité.

                ### 3.1. PRMP

                **Module 02 — Saisie & gestion PPM**

                - Création et mise à jour du PPM : en-tête, exercice, signataire, marchés, lots et tranches.

                ## Légende
                OK
                """;

        List<Passage> passages = CorpusIaService.decouperMarkdown(md, "Règles de gestion de PRS", "d1");

        assertThat(passages).extracting(Passage::reference)
                .containsExactly("1. Hiérarchie des contrôleurs", "1. Hiérarchie des contrôleurs › 3.1. PRMP › Module 02 — Saisie & gestion PPM");
        assertThat(passages.get(1).texte()).startsWith("- Création et mise à jour du PPM");
        assertThat(passages).allSatisfy(p -> assertThat(p.document()).isEqualTo("Règles de gestion de PRS"));
        assertThat(passages).extracting(Passage::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Markdown : une section trop longue est recoupée sur un début de liste, jamais au-delà d'une fois et demie la taille visée")
    void markdown_sectionLongueRecoupee() {
        StringBuilder md = new StringBuilder("## 3.1. PRMP\n\n");
        for (int i = 0; i < 60; i++) {
            md.append("- Règle numéro ").append(i).append(" : le montant estimatif en vigueur est le nouveau montant s'il a été posé.\n");
        }

        List<Passage> passages = CorpusIaService.decouperMarkdown(md.toString(), "Règles", "d1");

        assertThat(passages).hasSizeGreaterThan(1);
        assertThat(passages).allSatisfy(p -> {
            assertThat(p.texte().length()).isLessThanOrEqualTo(CorpusIaService.TAILLE_MAX_PASSAGE * 3 / 2 + 200);
            assertThat(p.texte()).startsWith("- Règle numéro");
            assertThat(p.reference()).isEqualTo("3.1. PRMP");
        });
    }

    @Test
    @DisplayName("Page : le numéro de page est retiré en fin comme en tête (l'ordre du flux PDF le sort en premier)")
    void nettoyerPage_numeroDePageRetire() {
        assertThat(CorpusIaService.nettoyerPage("15\n  Dates   prévisionnelles  \n\nVérifier les dates.\n"))
                .isEqualTo("Dates prévisionnelles\nVérifier les dates.");
        assertThat(CorpusIaService.nettoyerPage("Vérifier les dates.\n16\n")).isEqualTo("Vérifier les dates.");
    }

    @Test
    @DisplayName("Titres : balisage Markdown et pictogrammes retirés de la référence")
    void nettoyerTitre() {
        assertThat(CorpusIaService.nettoyerTitre("La navette du PV à **DEUX NIVEAUX** ⚠️"))
                .isEqualTo("La navette du PV à DEUX NIVEAUX");
    }

    @Test
    @DisplayName("PDF : un passage par page citée « p. N », numéro de page final retiré, page trop courte ignorée")
    void pdf_unPassageParPage() throws IOException {
        Path pdf = dossier.resolve("manuel.pdf");
        creerPdf(pdf, List.of(
                List.of("Examen des documents de planification", "La PRMP établit son plan de passation au plus tard le 31 octobre.",
                        "Vérifier les motifs de la mise à jour du PPM et la cohérence de la fiche de présentation.", "12"),
                List.of("3"),
                List.of("Cas de fractionnement illicite : plusieurs prestations identiques d'un même compte.",
                        "Exiger de les fusionner et éventuellement de les allotir, selon le manuel.", "15")));

        List<Passage> passages = CorpusIaService.decouperPdf(pdf, "Manuel", "d0");

        assertThat(passages).extracting(Passage::reference).containsExactly("p. 1", "p. 3");
        assertThat(passages.get(0).texte()).contains("31 octobre").doesNotEndWith("12");
        assertThat(passages.get(1).id()).isEqualTo("d0-p3");
    }

    @Test
    @DisplayName("PDF : chaque page emporte le début de la suivante, pour qu'une liste coupée par un saut de page reste entière")
    void pdf_pageSuivieDuDebutDeLaSuivante() throws IOException {
        Path pdf = dossier.resolve("liste.pdf");
        creerPdf(pdf, List.of(
                List.of("Le recours aux marchés complémentaires n'est possible qu'aux conditions cumulatives suivantes :",
                        "le marché initial a été passé selon la procédure d'appel d'offres ;"),
                List.of("le montant cumulé des marchés complémentaires ne dépasse pas un tiers du marché principal.",
                        "Les prestations ne peuvent être séparées du marché principal.")));

        List<Passage> passages = CorpusIaService.decouperPdf(pdf, "Manuel", "d0");

        assertThat(passages.get(0).reference()).isEqualTo("p. 1");
        assertThat(passages.get(0).texte()).contains("[… suite en page 2 :]").contains("un tiers du marché principal");
        assertThat(passages.get(1).texte()).doesNotContain("suite en page");
    }

    @Test
    @DisplayName("Chargement : un document introuvable est ignoré, les autres sont indexés et interrogeables")
    void chargement_tolerantEtInterrogeable() throws IOException {
        Path md = dossier.resolve("regles.md");
        Files.writeString(md, "## Délais\n\nLe délai de traitement d'un dossier est de 48 heures, cinq jours ouvrés au plus.\n");
        AssistantIaProperties props = new AssistantIaProperties(true, null, null, 0, 0, 0, List.of(
                new DocumentCorpus("Absent", dossier.resolve("absent.pdf").toString()),
                new DocumentCorpus("Règles", md.toString()),
                new DocumentCorpus("Vide", "")));
        CorpusIaService corpus = new CorpusIaService(props);

        assertThat(corpus.documents()).extracting(CorpusIaService.DocumentCharge::libelle).containsExactly("Règles");
        assertThat(corpus.rechercher("Quel est le délai de traitement ?", 3))
                .extracting(Passage::reference).containsExactly("Délais");
        assertThat(corpus.rechercher("xylophone", 3)).isEmpty();
    }

    private static void creerPdf(Path fichier, List<List<String>> pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font police = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<String> lignes : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream flux = new PDPageContentStream(doc, page)) {
                    flux.beginText();
                    flux.setFont(police, 11);
                    flux.newLineAtOffset(50, 700);
                    for (String ligne : lignes) {
                        flux.showText(ligne);
                        flux.newLineAtOffset(0, -16);
                    }
                    flux.endText();
                }
            }
            doc.save(fichier.toFile());
        }
    }
}
