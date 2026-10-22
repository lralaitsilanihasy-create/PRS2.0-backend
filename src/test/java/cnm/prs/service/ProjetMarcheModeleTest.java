package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import cnm.prs.dto.EntrepriseCandidatDto;
import cnm.prs.dto.EvaluationDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.dto.MandatDto;
import cnm.prs.entity.DossierMec;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Offre;
import cnm.prs.repository.DossierMecRepository;
import cnm.prs.repository.LotRepository;
import tools.jackson.databind.JsonNode;

/**
 * ⚠️ 2026-10-22 (demande front « modeles-documents-marche », M1) — le modèle de départ du projet de marché, rendu avec les valeurs de
 * {@link ProjetMarcheService#contexte}, redonne <strong>mot pour mot</strong> le texte du code ({@link ProjetMarcheService#document}) :
 * marché complet (fiche, entreprise, mandat, international, alloti, tranches, original de l'AE), marché de prestations intellectuelles
 * sans données (« …… » et fragments omis), contrat-cadre à deux titulaires dont l'un sans entreprise ni montant. Pied de page compris.
 */
class ProjetMarcheModeleTest {

    private final FicheMarcheService fiches = mock(FicheMarcheService.class);
    private final DossierMecRepository dmcs = mock(DossierMecRepository.class);
    private final LotRepository lots = mock(LotRepository.class);
    private final MandatService mandats = mock(MandatService.class);
    private final EntrepriseCandidatService entreprises = mock(EntrepriseCandidatService.class);
    private final ProjetMarcheService service = new ProjetMarcheService(fiches, dmcs, lots, mandats, entreprises);

    private static Offre offre(String id, String raison, String nif, int numero, String candidat) {
        Offre o = new Offre();
        o.setIdOffre(id);
        o.setRaisonSociale(raison);
        o.setNif(nif);
        o.setNumero(numero);
        o.setIdCandidat(candidat);
        return o;
    }

    private void fiche(Map<String, String> valeurs, String designation) {
        FicheMarcheService.EtatVersion v = mock(FicheMarcheService.EtatVersion.class);
        FicheMarcheDto etat = mock(FicheMarcheDto.class);
        when(etat.getDesignationMarche()).thenReturn(designation);
        when(v.etat()).thenReturn(etat);
        when(v.valeur(anyString())).thenAnswer(i -> valeurs.get(i.getArgument(0, String.class)));
        when(fiches.etatValide(any())).thenReturn(Optional.of(v));
    }

    @Test
    void marcheCompletMotPourMot() {
        fiche(Map.of(ProjetMarcheService.NUMERO_AO, "13-CC/MEF/SG/PRMP.2026", ProjetMarcheService.COMPTABLE_FS + "#1", "Payeur général",
                ProjetMarcheService.IMPUTATION, "6011", ProjetMarcheService.DOMICILIATION, "BNI 00005", ProjetMarcheService.PENALITE_TAUX, "2",
                ProjetMarcheService.PENALITE_PLAFOND, "10", ProjetMarcheService.RECEPTION_PAR_TRANCHES, "OUI",
                ProjetMarcheService.INDEMNITE_RESILIATION, "2"), "Fournitures de bureau en deux lots");
        DossierMec dmc = new DossierMec();
        dmc.setIdDetail(77);
        when(dmcs.findById(40L)).thenReturn(Optional.of(dmc));
        Lot l1 = new Lot();
        l1.setIdLot(1);
        l1.setDesignationLot("Papier");
        when(lots.findByIdDetail(77)).thenReturn(List.of(l1));
        MandatDto mandat = mock(MandatDto.class);
        when(mandat.isImplicite()).thenReturn(false);
        when(mandat.getRefArrete()).thenReturn("arrêté n° 1234/2026");
        when(mandat.getDateDebut()).thenReturn(LocalDate.of(2026, 1, 15));
        when(mandats.mandatActif(null, "PRMP1")).thenReturn(Optional.of(mandat));
        when(entreprises.lire("C1")).thenReturn(new EntrepriseCandidatDto.Entreprise(1, "Papeterie SA", "300", null, null, "Antanimena",
                new EntrepriseCandidatDto.Representant("RAKOTO", "Hery", "Gérant"), List.of(), null, null));
        EvaluationDto.Proposition p = new EvaluationDto.Proposition("O1", 3, "Papeterie SA", new BigDecimal("179570562"),
                new BigDecimal("215484674"), "12 mois", false, null, null);
        ProjetMarcheService.Entree in = new ProjetMarcheService.Entree(40L, 1, p, offre("O1", "Papeterie SA", "300", 3, "C1"),
                Map.of("ENTITE", "DAAF", "MINISTERE", "MEF", "PRMP", "RANDRIA Lala", "MODE", "Appel d'offres ouvert international"), true, false,
                "PRMP1", LocalDate.of(2026, 11, 5), true, LocalDate.of(2026, 10, 30));
        comparer(in, "FOURNITURES_SERVICES");
    }

    @Test
    void prestationsIntellectuellesSansDonnees() {
        when(fiches.etatValide(any())).thenReturn(Optional.empty());
        when(entreprises.lire(anyString())).thenThrow(new IllegalStateException("inconnue"));
        EvaluationDto.Proposition p = new EvaluationDto.Proposition("O2", 1, "Cabinet", null, null, null, false, null, null);
        ProjetMarcheService.Entree in = new ProjetMarcheService.Entree(41L, 1, p, offre("O2", "Cabinet Conseil", "400", 1, "C2"),
                Map.of("OBJET", "Étude de faisabilité"), false, true, null, null, false, null);
        comparer(in, "PRESTATIONS_INTELLECTUELLES");
    }

    @Test
    void contratCadreADeuxTitulaires() {
        when(fiches.etatValide(any())).thenReturn(Optional.empty());
        when(entreprises.lire("C1")).thenReturn(new EntrepriseCandidatDto.Entreprise(1, "Hygiène SA", "500", null, null, "Ankorondrano",
                new EntrepriseCandidatDto.Representant("RABE", "Soa", null), List.of(), null, null));
        when(entreprises.lire("C2")).thenThrow(new IllegalStateException("inconnue"));
        EvaluationDto.Proposition p = new EvaluationDto.Proposition("O1", 1, "Hygiène SA", null, null, null, false, null, null);
        List<ProjetMarcheService.Titulaire> titulaires = List.of(
                new ProjetMarcheService.Titulaire(offre("O1", "Hygiène SA", "500", 1, "C1"), new BigDecimal("1000000"), null, "12 mois"),
                new ProjetMarcheService.Titulaire(offre("O2", "Nettoyage SARL", "600", 2, "C2"), null, null, null));
        ProjetMarcheService.Entree in = new ProjetMarcheService.Entree(42L, 1, p, offre("O1", "Hygiène SA", "500", 1, "C1"),
                Map.of("ENTITE", "DAAF", "OBJET", "Produits d'entretien", "MODE", "Appel d'offres ouvert"), false, false, null, null, false, null,
                titulaires);
        comparer(in, "FOURNITURES_SERVICES");
    }

    private void comparer(ProjetMarcheService.Entree in, String categorie) {
        DocumentLibre attendu = service.document(in);
        List<String> code = new ArrayList<>();
        for (DocumentLibre.Element e : attendu.elements()) {
            if (e instanceof DocumentLibre.Paragraphe p && !p.texte().isEmpty()) {
                code.add(p.texte());
            }
        }
        ModelePvDepart.Depart d = ModelePvDepart.projetMarche(categorie);
        ModelePvPage.valider(d.document(), id -> false);
        Map<String, String> cond = new LinkedHashMap<>();
        d.conditions().forEach(c -> cond.put(c.nom(), c.expression()));
        JsonNode r = ModelePvPage.resoudre(d.document(), service.contexte(in), cond);
        assertThat(code).hasSizeGreaterThan(15);
        assertThat(textes(r.path("corps"))).containsExactlyElementsOf(code);
        assertThat(textes(r.path("piedDePage"))).containsExactly(attendu.piedDePage());
        dernier = String.join("\n", code);
    }

    private String dernier;

    @Test
    void lesCasVerifiesPortentBienLeursVariantes() {
        marcheCompletMotPourMot();
        assertThat(dernier).contains("Article 10 — Droit applicable", "cent soixante-dix-neuf millions", "arrêté n° 1234/2026 du 15/01/2026",
                ", et leur réception partielle par tranches,", "(original signé remis le 30/10/2026", "Hery RAKOTO, Gérant", "05/11/2026");
        org.mockito.Mockito.reset(fiches, dmcs, lots, mandats, entreprises);
        prestationsIntellectuellesSansDonnees();
        assertThat(dernier).contains("la proposition du Titulaire", "…… (montant hors taxes)", "…… (nom et qualité du signataire)",
                "issu de l'appel d'offres.").doesNotContain("Article 10");
        org.mockito.Mockito.reset(fiches, dmcs, lots, mandats, entreprises);
        contratCadreADeuxTitulaires();
        assertThat(dernier).contains("PROJET DE CONTRAT-CADRE", "– Hygiène SA, NIF 500, Ankorondrano, représentée par Soa RABE (offre n° 1) ;",
                "– Nettoyage SARL : offre indicative de …… (montant hors taxes).", "Pour Nettoyage SARL :");
    }

    private static List<String> textes(JsonNode zone) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : zone.path("content")) {
            StringBuilder s = new StringBuilder();
            for (JsonNode t : n.path("content")) {
                s.append("hardBreak".equals(t.path("type").asString()) ? "\n" : t.path("text").asString(""));
            }
            if (!s.isEmpty()) {
                out.add(s.toString());
            }
        }
        return out;
    }
}
