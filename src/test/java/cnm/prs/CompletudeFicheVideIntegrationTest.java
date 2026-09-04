package cnm.prs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.enums.CategorieModePassation;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.PorteePointCtrl;

/**
 * ⚠️ <strong>« On ne contrôle pas le vide »</strong> (règle du pilote, 2026-09-04) — les points de
 * contrôle de la <strong>fiche de présentation</strong> et de l'<strong>AGPM</strong> ne sont exigés
 * que si le document dérivé a du contenu.
 *
 * <p>Le front avait déjà sauté l'étape quand la fiche est vide ; la garde serveur, elle, exigeait
 * toujours une évaluation de chaque point non-LIGNE. Un examen de dossier à fiche vide partait donc en
 * <strong>400 « grille »</strong> alors qu'il n'y avait rien à contrôler — soumission impossible.</p>
 *
 * <p>Ce que ces tests fixent surtout, c'est la <strong>frontière</strong> : seule l'EXIGENCE tombe. Un
 * seul marché dérogatoire, et la fiche redevient contrôlable — c'est l'anti-régression qui empêche
 * cette règle de dégénérer en « les points de fiche ne sont jamais exigés ».</p>
 */
class CompletudeFicheVideIntegrationTest extends CnmIntegrationTestSupport {

    private static final int FICHE_1 = 40;
    private static final int AGPM_1 = 43;
    private static final int MODE_NORMAL_AGPM = 701;
    private static final int MODE_DEROGATOIRE = 702;

    @BeforeEach
    void grilleEtReferentiels() {
        creerPoint(FICHE_1, "Listes de la fiche cohérentes avec le plan", PorteePointCtrl.FICHE, null);
        creerPoint(AGPM_1, "AGPM cohérent avec le PPM", PorteePointCtrl.AGPM, "PPM-AGPM");
        // Deux modes : l'un déclenche l'AGPM (appel d'offres ouvert, catégorie de droit commun),
        // l'autre est dérogatoire — c'est lui qui remplit la première liste de la fiche.
        modePassationRepository.save(mode(MODE_NORMAL_AGPM, "Appel d'offres ouvert",
                CategorieModePassation.NORMAL, true));
        modePassationRepository.save(mode(MODE_DEROGATOIRE, "Gré à gré",
                CategorieModePassation.DEROGATOIRE, false));
    }

    private void creerPoint(int id, String libelle, PorteePointCtrl portee, String sousType) {
        PointsCtrl p = new PointsCtrl();
        p.setIdPointCtrl(id);
        p.setLibelPointCtrl(libelle);
        p.setObligatoire(true);
        p.setIdTypeDossier("DDP");
        p.setIdSousType(sousType);
        p.setPortee(portee);
        p.setOrdrePointCtrl(id);
        pointsCtrlRepository.save(p);
    }

    private ModePassation mode(int id, String libelle, CategorieModePassation categorie, boolean agpm) {
        ModePassation m = new ModePassation();
        m.setIdMode(id);
        m.setLibelle(libelle);
        m.setCategorie(categorie);
        m.setDeclencheAgpm(agpm);
        return m;
    }

    /** Place le dossier 1 dans la famille DDP au sous-type voulu — c'est lui que porte l'examen 1. */
    private void dossier1(String sousType) {
        Dossier d = dossierRepository.findById(1).orElseThrow();
        d.setIdTypeDossier("DDP");
        d.setIdSousType(sousType);
        dossierRepository.save(d);
    }

    /** Ajoute une ligne de marché au dossier 1 ; {@code idMode} nul = ligne ordinaire, hors fiche. */
    private void ligne(int idDetail, Integer idMode, FormeMarche forme) {
        Marche m = marche(idDetail, 1, 1);
        m.setIdMode(idMode);
        m.setFormeMarche(forme);
        marcheRepository.save(m);
    }

    /** Soumission de l'examen 1 SANS aucune évaluation : la complétude est le seul juge. */
    private String soumettre(org.springframework.test.web.servlet.ResultMatcher attendu) throws Exception {
        return mvc.perform(post("/api/examens/1/soumettre").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON).content("{\"idAvis\":\"FAV\"}"))
                .andExpect(attendu)
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("1 — Fiche VIDE (ni dérogatoire, ni délai aménagé, ni contrat-cadre) : la soumission passe, "
            + "sans évaluation de fiche")
    void ficheVide_soumissionAcceptee() throws Exception {
        dossier1("PPM");
        // Une ligne ordinaire : mode de droit commun, forme à quantité fixe. Elle n'alimente aucune
        // des trois listes de la fiche — le document existe, mais il est blanc.
        ligne(4001, MODE_NORMAL_AGPM, FormeMarche.QUANTITE_FIXE);

        soumettre(status().isCreated());
    }

    @Test
    @DisplayName("2 — Un SEUL marché dérogatoire, et la fiche redevient exigée : 400 « grille » (anti-régression)")
    void ficheAvecContenu_exigeeCommeAvant() throws Exception {
        dossier1("PPM");
        ligne(4002, MODE_DEROGATOIRE, FormeMarche.QUANTITE_FIXE);

        String erreur = soumettre(status().isBadRequest());
        // ⚠️ C'est cette assertion qui empêche la règle de dégénérer en « la fiche n'est jamais
        // exigée » : le vide est une exception, pas la nouvelle norme.
        Assertions.assertTrue(erreur.contains("fiche de présentation"),
                "la fiche a du contenu : son point doit être réclamé — " + erreur);
    }

    @Test
    @DisplayName("2 bis — Le contrat-cadre suffit à remplir la fiche, à lui seul")
    void contratCadre_remplitLaFiche() throws Exception {
        dossier1("PPM");
        // Mode de droit commun, mais forme CONTRAT_CADRE : la troisième liste de la fiche. Chacune des
        // trois suffit — les dériver ensemble, c'est ce que fait déjà la saisie.
        ligne(4003, MODE_NORMAL_AGPM, FormeMarche.CONTRAT_CADRE);

        Assertions.assertTrue(soumettre(status().isBadRequest()).contains("fiche de présentation"));
    }

    @Test
    @DisplayName("3 — Un PPM-AGPM garde ses points AGPM exigés : l'AGPM a du contenu par construction")
    void agpm_toujoursExige() throws Exception {
        dossier1("PPM-AGPM");
        // La ligne en appel d'offres ouvert est ce qui FAIT le sous-type PPM-AGPM : le prédicat de
        // l'AGPM vide est le même que celui qui dérive ce sous-type, les deux ne peuvent pas diverger.
        ligne(4004, MODE_NORMAL_AGPM, FormeMarche.QUANTITE_FIXE);

        String erreur = soumettre(status().isBadRequest());
        Assertions.assertTrue(erreur.contains("projet d'AGPM"),
                "un PPM-AGPM porte des lignes d'AGPM : ses points restent exigés — " + erreur);
        // La fiche, elle, est vide sur ce dossier : elle ne doit PAS être réclamée. Les deux documents
        // sont jugés séparément — c'est bien « par onglet » que le pilote a formulé la règle.
        Assertions.assertFalse(erreur.contains("fiche de présentation"),
                "fiche vide : ses points ne sont pas exigés, même sur un PPM-AGPM — " + erreur);
    }

    @Test
    @DisplayName("4 — Une évaluation de fiche EXCÉDENTAIRE reste acceptée : seule l'exigence tombe")
    void evaluationExcedentaire_conservee() throws Exception {
        dossier1("PPM");
        ligne(4005, MODE_NORMAL_AGPM, FormeMarche.QUANTITE_FIXE);   // fiche vide

        // Le Membre a statué le point de fiche avant qu'une mise à jour ne vide la fiche (ou depuis un
        // brouillon antérieur). Rien ne doit le rejeter : la complétude compte ce qui MANQUE.
        mvc.perform(post("/api/examen-details").header("Authorization", tokenMembre)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idExamen\":1,\"idPtControle\":" + FICHE_1 + ",\"conforme\":true}"))
                .andExpect(status().isCreated());

        soumettre(status().isCreated());
        Assertions.assertEquals(1, examenDetailRepository.findByIdExamen(1).stream()
                .filter(x -> FICHE_1 == x.getIdPtControle()).count(),
                "l'évaluation excédentaire est conservée, pas effacée");
    }
}
