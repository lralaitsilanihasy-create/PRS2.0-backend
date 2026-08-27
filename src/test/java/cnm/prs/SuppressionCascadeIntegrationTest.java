package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import cnm.prs.entity.Anomalie;
import cnm.prs.entity.ChangementLigne;
import cnm.prs.entity.CopieDossier;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Echeance;
import cnm.prs.entity.LettreRenvoi;
import cnm.prs.entity.Lot;
import cnm.prs.entity.Message;
import cnm.prs.entity.PieceDemandeRetrait;
import cnm.prs.entity.PieceJointeDossier;
import cnm.prs.entity.Ppm;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.ChangementLigneRepository;
import cnm.prs.repository.EcheanceRepository;
import cnm.prs.repository.MessageRepository;
import cnm.prs.repository.PieceDemandeRetraitRepository;
import cnm.prs.repository.PieceJointeDossierRepository;
import cnm.prs.repository.RegleAnomalieRepository;

/**
 * ⚠️ Audit 2026-08-27 (lot D §2) — <strong>fermeture de la cascade de suppression d'un dossier</strong>.
 *
 * <p>La cascade de {@code DossierService#delete} reposait sur une hypothèse <strong>périmée</strong> :
 * « un brouillon n'a jamais dépassé {@code PRET_DISPATCH} ». Depuis que le <strong>retrait accepté</strong>
 * ramène en {@code BROUILLON} un dossier qui a bel et bien parcouru tout le circuit, un brouillon peut
 * porter des anomalies, des échéances, des messages, des pièces jointes, un diff de version et des
 * traces de circuit résiduelles. La conséquence était double :</p>
 * <ul>
 *   <li><strong>orphelins garantis</strong> — {@code t_piece_jointe_dossier} (avec son {@code CONTENU}
 *       binaire), {@code t_changement_ligne} et {@code t_piece_demande_retrait} n'étaient jamais purgés
 *       et survivaient, illisibles, à chaque suppression ;</li>
 *   <li><strong>409 « violation de clé étrangère »</strong> — {@code t_message.ID_DOSSIER},
 *       {@code t_anomalie.ID_DETAIL}/{@code ID_PPM} et {@code t_echeance.ID_DETAIL} portent de vraies FK
 *       jamais nettoyées : la suppression échouait purement et simplement.</li>
 * </ul>
 *
 * <p>Les deux tests couvrent les deux chemins : un brouillon revenu d'un circuit complet par retrait
 * accepté (§1), et un brouillon portant encore des traces de circuit non purgées (§2 — la réutilisation
 * de {@link cnm.prs.service.CircuitCascadeService#purgerCircuit} dans la suppression).</p>
 */
class SuppressionCascadeIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired private PieceJointeDossierRepository pieceJointeDossierRepository;
    @Autowired private ChangementLigneRepository changementLigneRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private AnomalieRepository anomalieRepository;
    @Autowired private EcheanceRepository echeanceRepository;
    @Autowired private RegleAnomalieRepository regleAnomalieRepository;
    @Autowired private PieceDemandeRetraitRepository pieceDemandeRetraitRepository;

    @Test
    @DisplayName("Lot D §2 — suppression d'un brouillon REVENU d'un circuit complet (retrait accepté) : "
            + "204 et zéro orphelin (pièces, diff, messages, anomalies, échéances, lots, PDF de retrait)")
    void suppressionBrouillonRevenuDeCircuit_204_etZeroOrphelin() throws Exception {
        final int idDossier = 910;

        // — Un dossier EXAMINE de PRMP001 qui a parcouru tout le circuit.
        Dossier d = dossierLoc(idDossier, "EXAMINE", "ANT", "PRMP001");
        d.setRefeDossier("00099/DDP/CRM-ANT/2026");
        dossierRepository.save(d);
        Ppm p = ppm(idDossier, idDossier, "PRMP001");
        p.setReference("00099/DGB/PPM/2026");
        ppmRepository.save(p);
        marcheRepository.save(marche(9100, idDossier, idDossier));
        receptionRepository.save(reception(idDossier, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(idDossier, idDossier, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(idDossier, idDossier, "CTRMEM"));
        LettreRenvoi lr = new LettreRenvoi();
        lr.setIdExamen(idDossier);
        lr.setIdDossier(idDossier);
        lr.setObjetLettre("Renvoi");
        lr.setStatut("SIGNE");
        lettreRenvoiRepository.save(lr);

        // — Les satellites que la cascade oubliait (les 3 orphelins + les 4 FK jamais purgées).
        creerSatellites(idDossier, 9100);

        // — Retrait accepté : le circuit est purgé, le dossier redevient un BROUILLON… chargé.
        int idDemande = demandeRetraitRepository.save(demandeRetrait(0, idDossier, "PRMP001")).getIdDemandeRetrait();
        PieceDemandeRetrait pdf = new PieceDemandeRetrait();
        pdf.setIdDemandeRetrait(idDemande);
        pdf.setNomFichier("lettre-retrait.pdf");
        pdf.setFormat("application/pdf");
        pdf.setTailleOctets(4L);
        pdf.setDateDepot(LocalDateTime.of(2026, 6, 5, 10, 0));
        pdf.setContenu("%PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        pieceDemandeRetraitRepository.save(pdf);

        mvc.perform(post("/api/demande-retraits/" + idDemande + "/accepter").header("Authorization", tokenCc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE"));
        assertEquals("BROUILLON", dossierRepository.findById(idDossier).orElseThrow().getStatut());

        // — La suppression par la PRMP propriétaire. Avant le lot D : 409 (FK t_message / t_anomalie /
        //   t_echeance) ; après : 204 et plus rien en base.
        mvc.perform(delete("/api/dossiers/" + idDossier).header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        assertZeroOrphelin(idDossier, 9100, idDemande);
    }

    @Test
    @DisplayName("Lot D §2 — suppression d'un brouillon portant encore des traces de circuit "
            + "(dispatch, examen, lettre, copie) : 204, l'aval du dispatch est purgé lui aussi")
    void suppressionBrouillon_avecTracesDeCircuitResiduelles_purgeFkSafe() throws Exception {
        final int idDossier = 920;
        Dossier d = dossierLoc(idDossier, "BROUILLON", "ANT", "PRMP001");
        dossierRepository.save(d);
        ppmRepository.save(ppm(idDossier, idDossier, "PRMP001"));
        // Traces de circuit résiduelles : l'ancienne cascade ne supprimait QUE les réceptions, si bien
        // que le dispatch (FK vers la réception) faisait échouer la suppression en 409.
        receptionRepository.save(reception(idDossier, idDossier, "CTRCC1", true));
        dispatchRepository.save(dispatch(idDossier, idDossier, "CTRCC1", "CTRMEM"));
        examenRepository.save(examen(idDossier, idDossier, "CTRMEM"));
        CopieDossier cop = new CopieDossier();
        cop.setIdCopie(9200);
        cop.setIdDispatch(idDossier);
        cop.setIdDossier(idDossier);
        cop.setImDestinataire("CTRMEM");
        cop.setTypeCopie("MEMBRE");
        cop.setDateTransmission(LocalDateTime.of(2026, 6, 5, 9, 0));
        cop.setAccuseReception(false);
        copieDossierRepository.save(cop);
        LettreRenvoi lr = new LettreRenvoi();
        lr.setIdExamen(idDossier);
        lr.setIdDossier(idDossier);
        lr.setObjetLettre("Renvoi");
        lr.setStatut("SIGNE");
        lettreRenvoiRepository.save(lr);

        mvc.perform(delete("/api/dossiers/" + idDossier).header("Authorization", tokenPrmp))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        assertEquals(0, compter("t_dossier", "ID_DOSSIER", idDossier), "dossier supprimé");
        assertEquals(0, compter("t_reception", "ID_DOSSIER", idDossier), "réceptions purgées");
        assertEquals(0, compter("t_copie_dossier", "ID_DOSSIER", idDossier), "copies purgées");
        assertEquals(0, compter("t_lettre_renvoi", "ID_DOSSIER", idDossier), "lettres de renvoi purgées");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.t_dispatch WHERE \"ID_DISPATCH\" = ?", Integer.class, idDossier),
                "dispatchs purgés");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.t_examen WHERE \"ID_EXAMEN\" = ?", Integer.class, idDossier),
                "examens purgés");
    }

    /** Les satellites d'un dossier ayant circulé : pièces, diff, message, anomalies, échéance, lot. */
    private void creerSatellites(int idDossier, int idDetail) {
        PieceJointeDossier piece = new PieceJointeDossier();
        piece.setIdDossier(idDossier);
        piece.setIdTypePiece(1);
        piece.setNomFichier("ppm-signe.pdf");
        piece.setFormat("pdf");
        piece.setTaille(4L);
        piece.setContenu("%PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        piece.setDateUpload(LocalDateTime.of(2026, 6, 1, 8, 0));
        piece.setApresLettreRenvoi(false);
        pieceJointeDossierRepository.save(piece);

        ChangementLigne chg = new ChangementLigne();
        chg.setIdChangement(changementLigneRepository.nextIdChangement().intValue());
        chg.setIdDossier(idDossier);
        chg.setIdLigneOrigine(idDetail);
        chg.setTypeChangement("MODIFIE");
        chg.setChamp("montEstim");
        chg.setValeurAvant("100");
        chg.setValeurApres("200");
        changementLigneRepository.save(chg);

        Message msg = new Message();
        msg.setIdMessage(messageRepository.nextIdMessage().intValue());
        msg.setIdDossier(idDossier);
        msg.setExpediteurIm("CTRCC1");
        msg.setDestinataireIm("CTRMEM");
        msg.setSujet("Dossier " + idDossier);
        msg.setCorps("A revoir.");
        msg.setDateEnvoi(LocalDateTime.of(2026, 6, 4, 11, 0));
        msg.setLu(false);
        messageRepository.save(msg);

        RegleAnomalie regle = new RegleAnomalie();
        regle.setIdRegleAnomalie(9001);
        regle.setCodeRegle("MONTANT_HORS_SEUIL");
        regle.setLibelle("Montant hors seuil");
        regle.setActif(true);
        regleAnomalieRepository.save(regle);
        Anomalie surLigne = new Anomalie();
        surLigne.setIdAnomalie(9101);
        surLigne.setIdDetail(idDetail);
        surLigne.setIdRegleAnomalie(9001);
        surLigne.setDateDetection(LocalDateTime.of(2026, 6, 4, 12, 0));
        anomalieRepository.save(surLigne);
        Anomalie surPpm = new Anomalie();
        surPpm.setIdAnomalie(9102);
        surPpm.setIdPpm(idDossier);
        surPpm.setIdRegleAnomalie(9001);
        surPpm.setDateDetection(LocalDateTime.of(2026, 6, 4, 12, 5));
        anomalieRepository.save(surPpm);

        Echeance ech = new Echeance();
        ech.setIdEcheance(9103);
        ech.setIdDetail(idDetail);
        ech.setTypeJalon("PUBLICATION_AVIS");
        ech.setDatePrevue(LocalDate.of(2026, 7, 1));
        echeanceRepository.save(ech);

        Lot lot = new Lot();
        lot.setIdLot(9104);
        lot.setIdDossier(idDossier);
        lot.setIdDetail(idDetail);
        lot.setDesignationLot("Lot unique");
        lot.setMontLot(new BigDecimal("1000"));
        lotRepository.save(lot);
    }

    /** Aucune ligne ne doit subsister dans les tables satellites — ni orpheline, ni bloquante. */
    private void assertZeroOrphelin(int idDossier, int idDetail, int idDemande) {
        assertEquals(0, compter("t_dossier", "ID_DOSSIER", idDossier), "dossier supprimé");
        assertEquals(0, compter("t_piece_jointe_dossier", "ID_DOSSIER", idDossier), "pièces jointes purgées");
        assertEquals(0, compter("t_changement_ligne", "ID_DOSSIER", idDossier), "diff de version purgé");
        assertEquals(0, compter("t_message", "ID_DOSSIER", idDossier), "messages purgés");
        assertEquals(0, compter("t_anomalie", "ID_DETAIL", idDetail), "anomalies de la ligne purgées");
        assertEquals(0, compter("t_anomalie", "ID_PPM", idDossier), "anomalies du PPM purgées");
        assertEquals(0, compter("t_echeance", "ID_DETAIL", idDetail), "échéances purgées");
        assertEquals(0, compter("t_lot", "ID_DOSSIER", idDossier), "lots purgés");
        assertEquals(0, compter("t_marche", "ID_DOSSIER", idDossier), "marchés purgés");
        assertEquals(0, compter("t_ppm", "ID_DOSSIER", idDossier), "PPM purgés");
        assertEquals(0, compter("t_demande_retrait", "ID_DOSSIER", idDossier), "demandes de retrait purgées");
        assertEquals(0, compter("t_piece_demande_retrait", "ID_DEMANDE_RETRAIT", idDemande),
                "PDF de la demande de retrait purgé");
        assertEquals(0, compter("t_notification", "ID_DOSSIER", idDossier), "notifications purgées");
    }

    private int compter(String table, String colonne, int valeur) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public." + table + " WHERE \"" + colonne + "\" = ?", Integer.class, valeur);
        return n == null ? 0 : n;
    }
}
