package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import cnm.prs.entity.Anomalie;
import cnm.prs.entity.Capm;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Marche;
import cnm.prs.entity.MarchePrevision;
import cnm.prs.entity.ModePassation;
import cnm.prs.entity.Nature;
import cnm.prs.entity.PointsCtrl;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.entity.ServiceBeneficiaire;
import cnm.prs.entity.SoaBeneficiaire;
import cnm.prs.enums.CategorieModePassation;
import cnm.prs.enums.CategorieSeuil;
import cnm.prs.enums.FormeMarche;
import cnm.prs.enums.GraviteSignalement;
import cnm.prs.enums.PorteePointCtrl;
import cnm.prs.enums.ProcedureAttendue;
import cnm.prs.enums.SourceSignalement;
import cnm.prs.enums.StatutSignalement;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeSignalement;
import cnm.prs.repository.AnomalieLigneRepository;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.CompteRepository;
import cnm.prs.repository.RegleAnomalieRepository;
import cnm.prs.seed.ReglesPreControleSeeder;
import cnm.prs.service.PreControlePpmService;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 2) — <strong>les règles du manuel de
 * contrôle a priori</strong>, et le rapprochement d'une exécution à l'autre.
 *
 * <p>Ce que ces tests protègent, règle par règle : le <strong>fractionnement</strong> (avertissement quand
 * des lignes d'un même compte se ressemblent, <strong>prioritaire</strong> quand leur cumul change la
 * procédure ou franchit le seuil de contrôle), le <strong>mode en deçà du seuil</strong> — et le silence
 * sur un mode dérogatoire, dont la justification est examinée ailleurs —, la <strong>catégorie de seuil</strong>
 * demandée <em>seulement</em> quand elle change la réponse, la <strong>somme des lots</strong>, la
 * <strong>mention « délai réduit »</strong> et les <strong>dates prévisionnelles</strong>.</p>
 *
 * <p>Et ce qui compte autant que les règles : <strong>un écartement motivé survit</strong> à une nouvelle
 * vérification, un signalement qui ne ressort plus est <strong>levé, jamais effacé</strong>, une règle
 * éteinte depuis l'administration ne lève rien, et deux exécutions de suite ne créent aucun doublon.</p>
 */
class PreControleReglesIntegrationTest extends CnmIntegrationTestSupport {

    private static final int DOSSIER = 740;
    private static final int PPM = 740;
    private static final int MODE_AOO = 1;
    private static final int MODE_CONSULTATION = 2;
    private static final int MODE_ENTENTE = 3;
    private static final int NATURE_TRAVAUX = 1;
    private static final int NATURE_FOURNITURES = 2;

    @Autowired private PreControlePpmService preControle;
    @Autowired private ReglesPreControleSeeder seeder;
    @Autowired private AnomalieRepository anomalieRepository;
    @Autowired private AnomalieLigneRepository anomalieLigneRepository;
    @Autowired private RegleAnomalieRepository regleAnomalieRepository;
    @Autowired private CompteRepository compteRepository;

    @BeforeEach
    void referentielsEtPlan() {
        natureRepository.save(new Nature(NATURE_TRAVAUX, "Travaux", "Marches de travaux"));
        natureRepository.save(new Nature(NATURE_FOURNITURES, "Fournitures", "Marches de fournitures"));
        natureRepository.save(new Nature(3, "Services", "Prestations de services"));
        modePassationRepository.save(mode(MODE_AOO, "Appel d'offres ouvert", CategorieModePassation.NORMAL));
        modePassationRepository.save(mode(MODE_CONSULTATION, "Consultation de prix",
                CategorieModePassation.NORMAL));
        modePassationRepository.save(mode(MODE_ENTENTE, "Entente directe",
                CategorieModePassation.DEROGATOIRE));
        pointDeGrille(801, "Mode de passation conforme", PorteePointCtrl.LIGNE);
        pointDeGrille(802, "Cohérence du montant estimatif", PorteePointCtrl.LIGNE);
        pointDeGrille(803, "Conformité de la désignation", PorteePointCtrl.LIGNE);
        soaBeneficiaireRepository.save(soa("SOA-740"));

        Dossier d = dossier(DOSSIER, "BROUILLON");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        cnm.prs.entity.Ppm p = ppm(PPM, DOSSIER, "PRMP001");
        p.setIdLocalite("ANT");
        p.setDateSignature(LocalDate.of(2026, 1, 10));
        ppmRepository.save(p);

        // Les règles, le point « Fractionnement illicite » et le classement des modes au barème : le
        // seeder les pose au démarrage de l'application ; en test, les référentiels dont il dépend
        // n'existent qu'ici, donc on l'appelle nous-mêmes (patron de SequencePkSansEcrasementIntegrationTest).
        seeder.run();
        entityManager.flush();
        entityManager.clear();
    }

    // ------------------------------------------------------------------ fixture

    private ModePassation mode(int id, String libelle, CategorieModePassation categorie) {
        ModePassation m = new ModePassation();
        m.setIdMode(id);
        m.setLibelle(libelle);
        m.setCategorie(categorie);
        m.setDelaiMinJours(30);
        return m;
    }

    private void pointDeGrille(int id, String libelle, PorteePointCtrl portee) {
        PointsCtrl pc = new PointsCtrl();
        pc.setIdPointCtrl(id);
        pc.setLibelPointCtrl(libelle);
        pc.setObligatoire(Boolean.TRUE);
        pc.setIdTypeDossier("DDP");
        pc.setPortee(portee);
        pc.setOrdrePointCtrl(id - 800);
        pointsCtrlRepository.save(pc);
    }

    private SoaBeneficiaire soa(String code) {
        SoaBeneficiaire s = new SoaBeneficiaire();
        s.setSoaCode(code);
        s.setLibelle("Service beneficiaire de test");
        return s;
    }

    /** Une ligne du plan, avec sa nature, son mode et son montant en vigueur. */
    private Marche ligne(int idDetail, int nature, int mode, String montant, String designation) {
        Marche m = marche(idDetail, DOSSIER, PPM);
        m.setIdNature(nature);
        m.setIdMode(mode);
        m.setMontEstim(new BigDecimal(montant));
        m.setDesignationMarche(designation);
        m.setFinancement("RPI");
        m.setFormeMarche(FormeMarche.QUANTITE_FIXE);
        return marcheRepository.save(m);
    }

    /** Une imputation budgétaire sur une ligne — c'est le compte qui fonde l'appréciation du manuel. */
    private void compte(int idBenef, int idDetail, String numCompte) {
        if (!compteRepository.existsById(numCompte)) {
            cnm.prs.entity.Compte c = new cnm.prs.entity.Compte();
            c.setNumCompte(numCompte);
            c.setLibelle("Compte " + numCompte);
            compteRepository.save(c);
        }
        ServiceBeneficiaire b = new ServiceBeneficiaire();
        b.setIdBenef(idBenef);
        b.setIdDetail(idDetail);
        b.setSoaCode("SOA-740");
        b.setNumCompte(numCompte);
        serviceBeneficiaireRepository.save(b);
    }

    private List<Anomalie> signalements(TypeSignalement type) {
        return anomalieRepository.findByIdPpmOrderByIdAnomalie(PPM).stream()
                .filter(a -> type.name().equals(a.getTypeAnomalie())).toList();
    }

    // ------------------------------------------------------------------ 1. fractionnement

    @Test
    @DisplayName("Trois lignes d'un même compte, même financement et même forme : avertissement — le "
            + "manuel demande de les fusionner, il n'en fait pas un motif de refus")
    void fractionnement_lignesHomogenes_avertissement() {
        ligne(7401, NATURE_FOURNITURES, MODE_CONSULTATION, "3000000", "Achat de ramettes de papier");
        ligne(7402, NATURE_FOURNITURES, MODE_CONSULTATION, "3000000", "Achat de papier A4");
        ligne(7403, NATURE_FOURNITURES, MODE_CONSULTATION, "3000000", "Fourniture de papier");
        compte(74011, 7401, "61121");
        compte(74021, 7402, "61121");
        compte(74031, 7403, "61121");
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        List<Anomalie> constats = signalements(TypeSignalement.FRACTIONNEMENT_COMPTE);
        assertThat(constats).hasSize(1);
        Anomalie a = constats.get(0);
        assertThat(a.getGravite()).isEqualTo(GraviteSignalement.A_VERIFIER.name());
        assertThat(a.getSource()).isEqualTo(SourceSignalement.REGLE.name());
        assertThat(a.getStatut()).isEqualTo(StatutSignalement.OUVERT.name());
        assertThat(a.getIdDetail()).isNull();   // constat inter-lignes : aucune ligne unique
        assertThat(a.getDescription()).contains("3 lignes du compte 61121", "9 000 000 Ar HT", "p. 15");
        assertThat(a.getSuggestion()).contains("Au lieu de :", "Lire :");
        assertThat(anomalieLigneRepository.findByIdAnomalie(a.getIdAnomalie())).hasSize(3);
        // Rattaché au point « Fractionnement illicite », que le seeder a ajouté à la grille.
        assertThat(a.getIdPointCtrl()).isNotNull();
        assertThat(pointsCtrlRepository.findById(a.getIdPointCtrl()).orElseThrow().getPortee())
                .isEqualTo(PorteePointCtrl.DOSSIER);
    }

    @Test
    @DisplayName("Le cumul qui fait basculer la procédure rend le signalement prioritaire, et la phrase "
            + "cite le seuil qu'aucune ligne n'appelle seule")
    void fractionnement_cumulChangeLaProcedure_prioritaire() {
        ligne(7411, NATURE_FOURNITURES, MODE_CONSULTATION, "60000000", "Fourniture de mobilier de bureau");
        ligne(7412, NATURE_FOURNITURES, MODE_CONSULTATION, "60000000", "Achat de mobilier");
        ligne(7413, NATURE_FOURNITURES, MODE_CONSULTATION, "60000000", "Mobilier de bureau, lot 3");
        compte(74111, 7411, "61123");
        compte(74121, 7412, "61123");
        compte(74131, 7413, "61123");
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        Anomalie a = signalements(TypeSignalement.FRACTIONNEMENT_COMPTE).get(0);
        assertThat(a.getGravite()).isEqualTo(GraviteSignalement.PRIORITAIRE.name());
        assertThat(a.getDescription()).contains("180 000 000 Ar HT", "appel d'offres ouvert",
                "150 000 000 Ar HT", "13 156/2019-MEF");
    }

    @Test
    @DisplayName("Le cumul qui franchit le seuil de contrôle a priori rend le signalement prioritaire, "
            + "même quand la procédure ne change pas : c'est le cas des articles 27 et 28")
    void fractionnement_cumulFranchitLeSeuilDeControle_prioritaire() {
        ligne(7421, NATURE_FOURNITURES, MODE_AOO, "160000000", "Acquisition de véhicules de service");
        ligne(7422, NATURE_FOURNITURES, MODE_AOO, "160000000", "Acquisition de véhicules");
        compte(74211, 7421, "61124");
        compte(74221, 7422, "61124");
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        Anomalie a = signalements(TypeSignalement.FRACTIONNEMENT_COMPTE).get(0);
        assertThat(a.getGravite()).isEqualTo(GraviteSignalement.PRIORITAIRE.name());
        assertThat(a.getDescription()).contains("seuil de contrôle a priori", "300 000 000 Ar HT");
    }

    @Test
    @DisplayName("Deux lignes de comptes différents, ou d'un même compte mais de financements "
            + "différents, ne sont pas un fractionnement — le manuel distingue la source de financement")
    void fractionnement_comptesOuFinancementsDifferents_aucunSignalement() {
        ligne(7431, NATURE_FOURNITURES, MODE_CONSULTATION, "3000000", "Achat de papier");
        ligne(7432, NATURE_FOURNITURES, MODE_CONSULTATION, "3000000", "Achat de toner");
        Marche autreFinancement = ligne(7433, NATURE_FOURNITURES, MODE_CONSULTATION, "3000000",
                "Achat de papier sur financement extérieur");
        autreFinancement.setFinancement("Bailleur");
        marcheRepository.save(autreFinancement);
        compte(74311, 7431, "61121");
        compte(74321, 7432, "61122");
        compte(74331, 7433, "61121");
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        assertThat(signalements(TypeSignalement.FRACTIONNEMENT_COMPTE)).isEmpty();
    }

    // ------------------------------------------------------------------ 2. mode et seuils

    @Test
    @DisplayName("Une consultation à 200 millions de fournitures est signalée — l'appel d'offres ouvert "
            + "est obligatoire dès 150 millions — et la suggestion est rédigée comme l'annexe d'un PV")
    void mode_enDecaDuSeuil_signale() {
        ligne(7441, NATURE_FOURNITURES, MODE_CONSULTATION, "200000000", "Acquisition de matériel informatique");
        compte(74411, 7441, "61125");
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        List<Anomalie> constats = signalements(TypeSignalement.MODE_SOUS_LE_SEUIL);
        assertThat(constats).hasSize(1);
        assertThat(constats.get(0).getIdDetail()).isEqualTo(7441);
        assertThat(constats.get(0).getDescription()).contains("Consultation de prix",
                "un appel d'offres ouvert", "150 000 000 Ar HT", "p. 14");
        assertThat(constats.get(0).getSuggestion())
                .contains("Au lieu de : « Consultation de prix »", "Lire : « Appel d'offres ouvert »");
        assertThat(constats.get(0).getIdPointCtrl()).isEqualTo(801);
    }

    @Test
    @DisplayName("Un mode DÉROGATOIRE n'est pas signalé, même très au-dessus du seuil : sa justification "
            + "est un point de la fiche de présentation, pas un constat de seuil")
    void mode_derogatoire_jamaisSignale() {
        ligne(7451, NATURE_FOURNITURES, MODE_ENTENTE, "900000000", "Acquisition de carburant en urgence");
        compte(74511, 7451, "61126");
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        assertThat(signalements(TypeSignalement.MODE_SOUS_LE_SEUIL)).isEmpty();
    }

    @Test
    @DisplayName("Un appel d'offres ouvert là où une consultation suffirait n'est pas signalé : la règle "
            + "ne joue que dans le sens de la concurrence insuffisante")
    void mode_plusOuvertQueNecessaire_aucunSignalement() {
        ligne(7461, NATURE_FOURNITURES, MODE_AOO, "10000000", "Achat de petit outillage");
        compte(74611, 7461, "61127");
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        assertThat(signalements(TypeSignalement.MODE_SOUS_LE_SEUIL)).isEmpty();
    }

    // ------------------------------------------------------------------ 3. catégorie de seuil

    @Test
    @DisplayName("La catégorie de seuil n'est demandée que lorsqu'elle change la réponse : à 600 millions "
            + "de travaux elle la change, et la règle du mode se tait alors au lieu de deviner")
    void categorieSeuil_demandeeSeulementSiElleChangeLaReponse() {
        ligne(7471, NATURE_TRAVAUX, MODE_CONSULTATION, "600000000", "Réfection de la cour du bâtiment B");
        compte(74711, 7471, "23110");
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        List<Anomalie> constats = signalements(TypeSignalement.CATEGORIE_SEUIL_A_PRECISER);
        assertThat(constats).hasSize(1);
        assertThat(constats.get(0).getDescription()).contains("n'est pas précisée", "600 000 000 Ar HT",
                "travaux non routiers", "entretien routier");
        // Tant que la catégorie est incertaine, la règle du mode s'abstient : elle ne devine pas.
        assertThat(signalements(TypeSignalement.MODE_SOUS_LE_SEUIL)).isEmpty();
    }

    @Test
    @DisplayName("Catégorie précisée : plus rien à demander, et la règle du mode peut conclure")
    void categorieSeuil_precisee_laRegleDuModeConclut() {
        Marche m = ligne(7481, NATURE_TRAVAUX, MODE_CONSULTATION, "600000000",
                "Construction d'un bâtiment administratif");
        m.setCategorieSeuil(CategorieSeuil.TRAVAUX_NON_ROUTIERS);
        marcheRepository.save(m);
        compte(74811, 7481, "23111");
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        assertThat(signalements(TypeSignalement.CATEGORIE_SEUIL_A_PRECISER)).isEmpty();
        assertThat(signalements(TypeSignalement.MODE_SOUS_LE_SEUIL)).hasSize(1);
    }

    @Test
    @DisplayName("Des fournitures n'ont qu'une catégorie possible : la question ne se pose jamais")
    void categorieSeuil_natureSansAmbiguite_aucuneQuestion() {
        ligne(7491, NATURE_FOURNITURES, MODE_AOO, "600000000", "Acquisition de groupes électrogènes");
        compte(74911, 7491, "61128");
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        assertThat(signalements(TypeSignalement.CATEGORIE_SEUIL_A_PRECISER)).isEmpty();
    }

    // ------------------------------------------------------------------ 4. lots, mentions, dates

    @Test
    @DisplayName("La somme des lots doit faire le montant du marché : la procédure se détermine sur la "
            + "totalité des lots (article 6)")
    void lots_sommeDivergente_signalee() {
        ligne(7501, NATURE_FOURNITURES, MODE_AOO, "100000000", "Fourniture de matériel scolaire, 2 lots");
        compte(75011, 7501, "61129");
        lotRepository.save(lot(75011, 7501, "Lot 1 : cahiers", "60000000"));
        lotRepository.save(lot(75012, 7501, "Lot 2 : manuels", "60000000"));
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        List<Anomalie> constats = signalements(TypeSignalement.LOTS_SOMME_DIVERGENTE);
        assertThat(constats).hasSize(1);
        assertThat(constats.get(0).getDescription()).contains("120 000 000 Ar HT", "100 000 000 Ar HT",
                "article 6");
        assertThat(constats.get(0).getIdPointCtrl()).isEqualTo(802);
    }

    @Test
    @DisplayName("Lots dont la somme fait le montant : rien à signaler")
    void lots_sommeJuste_aucunSignalement() {
        ligne(7511, NATURE_FOURNITURES, MODE_AOO, "120000000", "Fourniture de matériel scolaire, 2 lots");
        compte(75111, 7511, "61130");
        lotRepository.save(lot(75111, 7511, "Lot 1 : cahiers", "60000000"));
        lotRepository.save(lot(75112, 7511, "Lot 2 : manuels", "60000000"));
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        assertThat(signalements(TypeSignalement.LOTS_SOMME_DIVERGENTE)).isEmpty();
    }

    @Test
    @DisplayName("Délai aménagé justifié mais objet sans la mention « délai réduit » : le manuel exige "
            + "la mention pour que les candidats connaissent les délais")
    void mention_delaiReduitManquante_signalee() {
        Marche m = ligne(7521, NATURE_FOURNITURES, MODE_AOO, "200000000", "Acquisition de vaccins");
        m.setJustifDelaiAmenage("Situation imprévisible : rupture de la chaîne du froid.");
        marcheRepository.save(m);
        Marche avecMention = ligne(7522, NATURE_FOURNITURES, MODE_AOO, "200000000",
                "Acquisition de réactifs — délai réduit");
        avecMention.setJustifDelaiAmenage("Situation imprévisible.");
        marcheRepository.save(avecMention);
        compte(75211, 7521, "61131");
        compte(75221, 7522, "61132");
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        List<Anomalie> constats = signalements(TypeSignalement.MENTION_DELAI_REDUIT);
        assertThat(constats).hasSize(1);
        assertThat(constats.get(0).getIdDetail()).isEqualTo(7521);
        assertThat(constats.get(0).getIdPointCtrl()).isEqualTo(803);
        assertThat(constats.get(0).getSuggestion()).contains("— délai réduit »");
    }

    @Test
    @DisplayName("Dates prévisionnelles : une fin avant son début et une date antérieure à l'exercice "
            + "sont signalées ensemble, en un seul constat par ligne")
    void dates_incoherentes_signaleesEnUnSeulConstat() {
        capmRepository.save(capm(9101, "Publication de l'avis"));
        capmRepository.save(capm(9102, "Ouverture des offres"));
        ligne(7531, NATURE_FOURNITURES, MODE_AOO, "200000000", "Acquisition de mobilier scolaire");
        compte(75311, 7531, "61133");
        marchePrevisionRepository.save(prevision(75311, 7531, 9101,
                LocalDate.of(2025, 11, 2), LocalDate.of(2025, 11, 30)));
        marchePrevisionRepository.save(prevision(75312, 7531, 9102,
                LocalDate.of(2026, 5, 20), LocalDate.of(2026, 5, 10)));
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        List<Anomalie> constats = signalements(TypeSignalement.DATES_PREVISION_INCOHERENTES);
        assertThat(constats).hasSize(1);
        assertThat(constats.get(0).getDescription())
                .contains("Publication de l'avis", "avant l'exercice 2026",
                        "Ouverture des offres", "avant son début");
    }

    @Test
    @DisplayName("Une date postérieure à l'exercice n'est pas signalée : un marché lancé en décembre "
            + "s'achève légitimement l'année suivante")
    void dates_apresLExercice_aucunSignalement() {
        capmRepository.save(capm(9103, "Notification du marché"));
        ligne(7541, NATURE_FOURNITURES, MODE_AOO, "200000000", "Acquisition de matériel de bureau");
        compte(75411, 7541, "61134");
        marchePrevisionRepository.save(prevision(75411, 7541, 9103,
                LocalDate.of(2026, 12, 10), LocalDate.of(2027, 2, 15)));
        entityManager.flush();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        assertThat(signalements(TypeSignalement.DATES_PREVISION_INCOHERENTES)).isEmpty();
    }

    // ------------------------------------------------------------------ 5. rapprochement

    @Test
    @DisplayName("Deux exécutions de suite sur un plan inchangé ne créent aucun doublon : le signalement "
            + "est retrouvé par sa clé")
    void deuxExecutions_aucunDoublon() {
        troisLignesDuMemeCompte();

        PreControlePpmService.ResultatPreControle premiere = preControle.executer(PPM);
        entityManager.flush();
        PreControlePpmService.ResultatPreControle seconde = preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        assertThat(premiere.nouveaux()).isEqualTo(1);
        assertThat(seconde.nouveaux()).isZero();
        assertThat(seconde.retrouves()).isEqualTo(1);
        assertThat(signalements(TypeSignalement.FRACTIONNEMENT_COMPTE)).hasSize(1);
    }

    @Test
    @DisplayName("Un signalement écarté avec son motif survit à une nouvelle vérification : il ne revient "
            + "jamais vierge — c'est toute la promesse faite à la PRMP")
    void ecartementMotive_survitAUneNouvelleVerification() {
        troisLignesDuMemeCompte();
        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        Anomalie a = signalements(TypeSignalement.FRACTIONNEMENT_COMPTE).get(0);
        a.setStatut(StatutSignalement.ECARTE.name());
        a.setImTraitement("PRMP001");
        a.setTypeActeurTraitement(TypeActeur.PRMP.name());
        a.setCommentaireTraitement("Trois sites distincts, livraisons séparées imposées par le magasin.");
        anomalieRepository.save(a);
        entityManager.flush();
        entityManager.clear();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        List<Anomalie> apres = signalements(TypeSignalement.FRACTIONNEMENT_COMPTE);
        assertThat(apres).hasSize(1);
        assertThat(apres.get(0).getIdAnomalie()).isEqualTo(a.getIdAnomalie());
        assertThat(apres.get(0).getStatut()).isEqualTo(StatutSignalement.ECARTE.name());
        assertThat(apres.get(0).getCommentaireTraitement()).contains("Trois sites distincts");
        assertThat(apres.get(0).getImTraitement()).isEqualTo("PRMP001");
    }

    @Test
    @DisplayName("Un signalement qui ne ressort plus après modification du plan est LEVÉ, pas effacé — et "
            + "il redevient ouvert si le défaut revient")
    void signalementQuiNeRessortPlus_leveEtNonEfface() {
        troisLignesDuMemeCompte();
        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();
        Integer idSignalement = signalements(TypeSignalement.FRACTIONNEMENT_COMPTE).get(0).getIdAnomalie();

        // La PRMP réimpute deux des trois lignes : le groupe n'a plus qu'une ligne, l'alarme se tait.
        compteDeSecours("61199");
        compteDeSecours("61198");
        serviceBeneficiaireRepository.findByIdDetail(7402).forEach(b -> {
            b.setNumCompte("61199");
            serviceBeneficiaireRepository.save(b);
        });
        serviceBeneficiaireRepository.findByIdDetail(7403).forEach(b -> {
            b.setNumCompte("61198");
            serviceBeneficiaireRepository.save(b);
        });
        entityManager.flush();
        entityManager.clear();

        PreControlePpmService.ResultatPreControle apresModification = preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        assertThat(apresModification.leves()).isEqualTo(1);
        Anomalie leve = anomalieRepository.findById(idSignalement).orElseThrow();
        assertThat(leve.getStatut()).isEqualTo(StatutSignalement.LEVE_MODIFICATION.name());
        assertThat(leve.getDateLevee()).isNotNull();
        assertThat(leve.getDetailLevee()).contains("ne ressort plus");

        // Le défaut revient : le signalement redevient ouvert, sans doublon ni trace de levée.
        serviceBeneficiaireRepository.findByIdDetail(7402).forEach(b -> {
            b.setNumCompte("61121");
            serviceBeneficiaireRepository.save(b);
        });
        serviceBeneficiaireRepository.findByIdDetail(7403).forEach(b -> {
            b.setNumCompte("61121");
            serviceBeneficiaireRepository.save(b);
        });
        entityManager.flush();
        entityManager.clear();

        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        Anomalie reouvert = anomalieRepository.findById(idSignalement).orElseThrow();
        assertThat(reouvert.getStatut()).isEqualTo(StatutSignalement.OUVERT.name());
        assertThat(reouvert.getDateLevee()).isNull();
        assertThat(signalements(TypeSignalement.FRACTIONNEMENT_COMPTE)).hasSize(1);
    }

    @Test
    @DisplayName("Une règle éteinte depuis l'administration ne produit plus rien — et ne lève pas ses "
            + "anciens signalements : leur silence ne prouverait pas que le plan a changé")
    void regleEteinte_neProduitRienEtNeLevePas() {
        troisLignesDuMemeCompte();
        preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        RegleAnomalie regle = regleAnomalieRepository
                .findByCodeRegle(TypeSignalement.FRACTIONNEMENT_COMPTE.name()).orElseThrow();
        regle.setActif(Boolean.FALSE);
        regleAnomalieRepository.save(regle);
        entityManager.flush();
        entityManager.clear();

        PreControlePpmService.ResultatPreControle apres = preControle.executer(PPM);
        entityManager.flush();
        entityManager.clear();

        assertThat(apres.leves()).isZero();
        assertThat(signalements(TypeSignalement.FRACTIONNEMENT_COMPTE)).hasSize(1);
        assertThat(signalements(TypeSignalement.FRACTIONNEMENT_COMPTE).get(0).getStatut())
                .isEqualTo(StatutSignalement.OUVERT.name());
    }

    @Test
    @DisplayName("Le seeder sème les six règles, le point « Fractionnement illicite » de portée DOSSIER "
            + "et classe les modes au barème de l'arrêté — sans jamais réécrire l'existant")
    void seeder_semeLesReglesLePointEtClasseLesModes() {
        for (TypeSignalement type : TypeSignalement.values()) {
            RegleAnomalie regle = regleAnomalieRepository.findByCodeRegle(type.name()).orElseThrow();
            assertThat(regle.getActif()).isTrue();
            assertThat(regle.getLibelle()).isEqualTo(type.libelle());
            assertThat(regle.getGraviteDefaut()).isEqualTo(type.graviteDefaut().name());
        }
        assertThat(pointsCtrlRepository.findAll()).anySatisfy(p -> {
            assertThat(p.getLibelPointCtrl()).isEqualTo("Fractionnement illicite");
            assertThat(p.getPortee()).isEqualTo(PorteePointCtrl.DOSSIER);
            assertThat(p.getIdTypeDossier()).isEqualTo("DDP");
        });
        assertThat(modePassationRepository.findById(MODE_AOO).orElseThrow().getProcedureSeuil())
                .isEqualTo(ProcedureAttendue.APPEL_OFFRES_OUVERT);
        assertThat(modePassationRepository.findById(MODE_CONSULTATION).orElseThrow().getProcedureSeuil())
                .isEqualTo(ProcedureAttendue.CONSULTATION);
        assertThat(modePassationRepository.findById(MODE_ENTENTE).orElseThrow().getProcedureSeuil())
                .isEqualTo(ProcedureAttendue.ACHAT_DIRECT);

        // Rejoué, il ne crée rien de plus et ne réécrit pas un libellé ajusté.
        long avant = regleAnomalieRepository.count();
        seeder.run();
        entityManager.flush();
        assertThat(regleAnomalieRepository.count()).isEqualTo(avant);
    }

    // ------------------------------------------------------------------ fixtures partagées

    /** Le cas d'école du fractionnement : trois lignes, un compte, un financement, une forme. */
    private void troisLignesDuMemeCompte() {
        ligne(7401, NATURE_FOURNITURES, MODE_CONSULTATION, "3000000", "Achat de ramettes de papier");
        ligne(7402, NATURE_FOURNITURES, MODE_CONSULTATION, "3000000", "Achat de papier A4");
        ligne(7403, NATURE_FOURNITURES, MODE_CONSULTATION, "3000000", "Fourniture de papier");
        compte(74011, 7401, "61121");
        compte(74021, 7402, "61121");
        compte(74031, 7403, "61121");
        entityManager.flush();
        entityManager.clear();
    }

    private void compteDeSecours(String numCompte) {
        if (!compteRepository.existsById(numCompte)) {
            cnm.prs.entity.Compte c = new cnm.prs.entity.Compte();
            c.setNumCompte(numCompte);
            c.setLibelle("Compte " + numCompte);
            compteRepository.save(c);
        }
    }

    private Lot lot(int idLot, int idDetail, String designation, String montant) {
        Lot l = new Lot();
        l.setIdLot(idLot);
        l.setIdDossier(DOSSIER);
        l.setIdDetail(idDetail);
        l.setDesignationLot(designation);
        l.setMontLot(new BigDecimal(montant));
        return l;
    }

    private Capm capm(int idCapm, String libelle) {
        Capm c = new Capm();
        c.setIdCapm(idCapm);
        c.setLibelleProcessus(libelle);
        c.setOrdre(idCapm - 9100);
        return c;
    }

    private MarchePrevision prevision(int id, int idDetail, int idCapm, LocalDate debut, LocalDate fin) {
        MarchePrevision p = new MarchePrevision();
        p.setIdPrevision(id);
        p.setIdDetail(idDetail);
        p.setIdCapm(idCapm);
        p.setDateDebut(debut);
        p.setDateFin(fin);
        return p;
    }
}
