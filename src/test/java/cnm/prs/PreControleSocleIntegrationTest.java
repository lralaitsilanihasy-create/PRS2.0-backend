package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import cnm.prs.entity.Anomalie;
import cnm.prs.entity.AnomalieLigne;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.RegleAnomalie;
import cnm.prs.entity.SeuilMarche;
import cnm.prs.enums.BaremeSeuil;
import cnm.prs.enums.CategorieSeuil;
import cnm.prs.enums.GraviteSignalement;
import cnm.prs.enums.SourceSignalement;
import cnm.prs.enums.StatutSignalement;
import cnm.prs.enums.TypeActeur;
import cnm.prs.enums.TypeSeuil;
import cnm.prs.repository.AnomalieLigneRepository;
import cnm.prs.repository.AnomalieRepository;
import cnm.prs.repository.RegleAnomalieRepository;
import cnm.prs.repository.SeuilMarcheRepository;
import cnm.prs.service.SeuilMarcheService;
import cnm.prs.service.SeuilsEnVigueur;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, migration V32) — le <strong>socle de
 * données</strong> : le référentiel de seuils et le signalement écartable.
 *
 * <p>Ce que ces tests protègent :</p>
 * <ul>
 *   <li>le <strong>barème de l'arrêté n° 13 156/2019-MEF</strong> est bien en base, à ses valeurs
 *       exactes — une faute de frappe dans le semis se lirait un jour comme un signalement injuste ;</li>
 *   <li>une <strong>PRMP peut être enregistrée</strong> comme ayant écarté un signalement : c'était
 *       impossible avant V32 (colonne de 7 caractères jointe à {@code tr_controleur}, alors qu'un
 *       {@code ID_PRMP} en fait 10). C'est le défaut C3 de l'audit du 2026-09-14, et ce test est là pour
 *       qu'il ne revienne pas une quatrième fois ;</li>
 *   <li>un <strong>signalement ne se dédouble pas</strong> d'une exécution à l'autre : sa clé est unique
 *       dans son PPM, sans quoi un écartement motivé serait remplacé par un doublon vierge ;</li>
 *   <li>le <strong>vocabulaire est fermé en base</strong> (statut, source, gravité, type d'acteur) ;</li>
 *   <li>un signalement <strong>inter-lignes</strong> liste ses lignes, et sa suppression les emporte ;</li>
 *   <li>une nouvelle valeur de seuil <strong>borne</strong> celle qu'elle remplace, au lieu de l'écraser.</li>
 * </ul>
 */
class PreControleSocleIntegrationTest extends CnmIntegrationTestSupport {

    private static final int PPM = 730;
    private static final int DOSSIER = 730;

    @Autowired private SeuilMarcheRepository seuilMarcheRepository;
    @Autowired private SeuilMarcheService seuilMarcheService;
    @Autowired private AnomalieRepository anomalieRepository;
    @Autowired private AnomalieLigneRepository anomalieLigneRepository;
    @Autowired private RegleAnomalieRepository regleAnomalieRepository;

    /** Un PPM de la PRMP, deux lignes : le minimum pour accrocher des signalements. */
    @BeforeEach
    void planDeLaPrmp() {
        Dossier d = dossier(DOSSIER, "BROUILLON");
        d.setIdTypeDossier("DDP");
        d.setIdPrmp("PRMP001");
        d.setIdLocalite("ANT");
        dossierRepository.save(d);
        ppmRepository.save(ppm(PPM, DOSSIER, "PRMP001"));
        marcheRepository.save(marche(7301, DOSSIER, PPM));
        marcheRepository.save(marche(7302, DOSSIER, PPM));
        regleAnomalieRepository.save(new RegleAnomalie(1, "ESSAI_SOCLE", "Règle d'essai du socle",
                null, null, Boolean.TRUE, GraviteSignalement.A_VERIFIER.name()));
    }

    /** Signalement minimal, prêt à être enregistré. */
    private Anomalie signalement(String cle) {
        Anomalie a = new Anomalie();
        a.setIdAnomalie(anomalieRepository.nextIdAnomalie().intValue());
        a.setIdPpm(PPM);
        a.setIdRegleAnomalie(1);
        a.setTypeAnomalie("ESSAI_SOCLE");
        a.setGravite(GraviteSignalement.A_VERIFIER.name());
        a.setSource(SourceSignalement.REGLE.name());
        a.setStatut(StatutSignalement.OUVERT.name());
        a.setDescription("Signalement d'essai");
        a.setDateDetection(LocalDateTime.of(2026, 9, 20, 9, 0));
        a.setCleSignalement(cle);
        return a;
    }

    // ------------------------------------------------------------------ 1. le référentiel de seuils

    @Test
    @DisplayName("Le barème de l'arrêté n° 13 156/2019-MEF est semé par V32, aux valeurs du texte : "
            + "30 valeurs en vigueur, dont les quatre que le plan cite en exemple")
    void bareme_semeParLaMigration_auxValeursDuTexte() {
        SeuilsEnVigueur seuils = seuilMarcheService.chargerEnVigueurLe(LocalDate.of(2026, 9, 20));

        assertThat(seuilMarcheRepository.findEnVigueurLe(LocalDate.of(2026, 9, 20))).hasSize(30);
        assertThat(seuils.vide()).isFalse();

        assertThat(seuils.seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.FOURNITURES_SERVICES,
                BaremeSeuil.CENTRAL).orElseThrow().getMontant()).isEqualByComparingTo("150000000");
        assertThat(seuils.seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.FOURNITURES_SERVICES,
                BaremeSeuil.DECONCENTRE).orElseThrow().getMontant()).isEqualByComparingTo("75000000");
        assertThat(seuils.seuil(TypeSeuil.CONTROLE_A_PRIORI, CategorieSeuil.ROUTES_CONSTRUCTION,
                BaremeSeuil.CENTRAL).orElseThrow().getMontant()).isEqualByComparingTo("10000000000");
        // ⚠️ Le chiffre de l'arrêté (2.500.000.000) et non ses lettres (« deux milliards cinq cent mille »).
        assertThat(seuils.seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.ROUTES_CONSTRUCTION,
                BaremeSeuil.DECONCENTRE).orElseThrow().getMontant()).isEqualByComparingTo("2500000000");
    }

    @Test
    @DisplayName("Toute valeur semée porte sa base légale : un signalement doit pouvoir citer le texte "
            + "qui le fonde, sinon il n'est pas opposable")
    void toutSeuil_porteSaBaseLegale() {
        assertThat(seuilMarcheRepository.findEnVigueurLe(LocalDate.of(2026, 9, 20)))
                .allSatisfy(s -> assertThat(s.getBaseLegale()).isNotBlank());
    }

    @Test
    @DisplayName("Les prestations intellectuelles : seuil de contrôle des fournitures et services, "
            + "aucun seuil de procédure, et une publicité datée du décret n° 2019-1310")
    void prestationsIntellectuelles_semeesCommeLeTexteLesFixe() {
        SeuilsEnVigueur seuils = seuilMarcheService.chargerEnVigueurLe(LocalDate.of(2026, 9, 20));

        assertThat(seuils.seuil(TypeSeuil.CONTROLE_A_PRIORI, CategorieSeuil.PRESTATIONS_INTELLECTUELLES,
                BaremeSeuil.CENTRAL).orElseThrow().getMontant()).isEqualByComparingTo("300000000");
        assertThat(seuils.seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.PRESTATIONS_INTELLECTUELLES,
                BaremeSeuil.CENTRAL)).isEmpty();

        SeuilMarche presse = seuils.publiciteManifestationInteret(BaremeSeuil.CENTRAL,
                new BigDecimal("100000000")).orElseThrow();
        assertThat(presse.getTypeSeuil()).isEqualTo(TypeSeuil.MANIFESTATION_INTERET_PRESSE);
        assertThat(presse.getDelaiMinJours()).isEqualTo(30);
        assertThat(seuils.publiciteManifestationInteret(BaremeSeuil.CENTRAL, new BigDecimal("50000000"))
                .orElseThrow().getDelaiMinJours()).isEqualTo(10);
    }

    @Test
    @DisplayName("Une nouvelle valeur borne celle qu'elle remplace au lieu de l'écraser : le "
            + "pré-contrôle d'un plan ancien garde le barème de son temps")
    void fixerUnSeuil_borneLaValeurPrecedente() {
        LocalDate effet = LocalDate.of(2027, 1, 1);

        seuilMarcheService.fixer(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.FOURNITURES_SERVICES,
                BaremeSeuil.CENTRAL, new BigDecimal("200000000"), null, effet, "Arrêté à venir");

        List<SeuilMarche> histoire = seuilMarcheService.historique(TypeSeuil.APPEL_OFFRES_OUVERT,
                BaremeSeuil.CENTRAL).stream()
                .filter(s -> s.getCategorieSeuil() == CategorieSeuil.FOURNITURES_SERVICES).toList();
        assertThat(histoire).hasSize(2);
        assertThat(histoire.get(0).getMontant()).isEqualByComparingTo("200000000");
        assertThat(histoire.get(0).getDateFin()).isNull();
        assertThat(histoire.get(1).getMontant()).isEqualByComparingTo("150000000");
        assertThat(histoire.get(1).getDateFin()).isEqualTo(effet);

        // Avant la date d'effet, c'est encore l'ancienne valeur qui vaut.
        assertThat(seuilMarcheService.chargerEnVigueurLe(LocalDate.of(2026, 12, 31))
                .seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.FOURNITURES_SERVICES,
                        BaremeSeuil.CENTRAL).orElseThrow().getMontant())
                .isEqualByComparingTo("150000000");
        assertThat(seuilMarcheService.chargerEnVigueurLe(effet)
                .seuil(TypeSeuil.APPEL_OFFRES_OUVERT, CategorieSeuil.FOURNITURES_SERVICES,
                        BaremeSeuil.CENTRAL).orElseThrow().getMontant())
                .isEqualByComparingTo("200000000");
    }

    // ------------------------------------------------------------------ 2. le signalement écartable

    @Test
    @DisplayName("⚠️ Défaut C3 de l'audit du 2026-09-14 — une PRMP de 10 caractères s'enregistre comme "
            + "ayant écarté un signalement, avec son motif : c'était impossible avant V32")
    void ecartementParUnePrmp_sEnregistre() {
        Anomalie a = signalement("ESSAI_SOCLE|7301");
        a.setStatut(StatutSignalement.ECARTE.name());
        a.setImTraitement("PRMP123456"); // 10 caractères, la longueur maximale d'un ID_PRMP
        a.setTypeActeurTraitement(TypeActeur.PRMP.name());
        a.setDateTraitement(LocalDateTime.of(2026, 9, 20, 10, 0));
        a.setCommentaireTraitement("Deux besoins distincts, sur deux sites éloignés.");
        anomalieRepository.save(a);
        entityManager.flush();
        entityManager.clear();

        Anomalie relu = anomalieRepository.findById(a.getIdAnomalie()).orElseThrow();
        assertThat(relu.getImTraitement()).isEqualTo("PRMP123456");
        assertThat(relu.getTypeActeurTraitement()).isEqualTo("PRMP");
        assertThat(relu.getStatut()).isEqualTo(StatutSignalement.ECARTE.name());
        assertThat(relu.getCommentaireTraitement()).contains("deux sites éloignés");
        assertThat(relu.getFige()).isFalse();
    }

    @Test
    @DisplayName("Deux signalements de même clé dans un PPM sont refusés : une nouvelle exécution "
            + "retrouve le signalement écarté au lieu d'en créer un double vierge")
    void memeCleDansUnPpm_refusee() {
        anomalieRepository.save(signalement("FRACTIONNEMENT|6111"));
        entityManager.flush();

        assertThatThrownBy(() -> insererSignalement(990001, "FRACTIONNEMENT|6111",
                StatutSignalement.OUVERT.name()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_anomalie_cle");
    }

    @Test
    @DisplayName("Le vocabulaire du signalement est fermé en base : un statut inventé est refusé")
    void statutHorsVocabulaire_refuse() {
        entityManager.flush();

        assertThatThrownBy(() -> insererSignalement(990002, "ESSAI_SOCLE|7302", "IGNORE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_anomalie_statut");
    }

    /**
     * Insertion en SQL direct : c'est la <strong>base</strong> qu'on met à l'épreuve ici, et la traduction
     * d'exception de Spring nomme la contrainte qui a refusé — ce que le flush d'Hibernate ne fait pas.
     */
    private void insererSignalement(int id, String cle, String statut) {
        jdbcTemplate.update("insert into public.t_anomalie (\"ID_ANOMALIE\", \"ID_PPM\", "
                + "\"ID_REGLE_ANOMALIE\", \"CLE_SIGNALEMENT\", \"STATUT\") values (?, ?, 1, ?, ?)",
                id, PPM, cle, statut);
    }

    @Test
    @DisplayName("Un signalement inter-lignes liste les lignes qu'il vise, avec leur montant du moment ; "
            + "sa suppression les emporte")
    void signalementInterLignes_listeSesLignesEtLesEmporte() {
        Anomalie a = signalement("FRACTIONNEMENT|2310");
        a.setGravite(GraviteSignalement.PRIORITAIRE.name());
        anomalieRepository.save(a);
        anomalieLigneRepository.save(new AnomalieLigne(a.getIdAnomalie(), 7301,
                new BigDecimal("60000000"), null));
        anomalieLigneRepository.save(new AnomalieLigne(a.getIdAnomalie(), 7302,
                new BigDecimal("55000000"), null));
        entityManager.flush();
        entityManager.clear();

        assertThat(anomalieLigneRepository.findByIdAnomalie(a.getIdAnomalie())).hasSize(2)
                .extracting(AnomalieLigne::getIdDetail).containsExactlyInAnyOrder(7301, 7302);

        anomalieRepository.deleteById(a.getIdAnomalie());
        entityManager.flush();
        entityManager.clear();
        assertThat(anomalieLigneRepository.findByIdAnomalie(a.getIdAnomalie())).isEmpty();
    }

    @Test
    @DisplayName("La catégorie de seuil se pose sur la ligne, reste facultative et ne change rien "
            + "au circuit")
    void categorieDeSeuil_facultativeSurLaLigne() {
        cnm.prs.entity.Marche sansCategorie = marcheRepository.findById(7301).orElseThrow();
        assertThat(sansCategorie.getCategorieSeuil()).isNull();

        cnm.prs.entity.Marche m = marcheRepository.findById(7302).orElseThrow();
        m.setCategorieSeuil(CategorieSeuil.ENTRETIEN_ROUTIER);
        marcheRepository.save(m);
        entityManager.flush();
        entityManager.clear();

        assertThat(marcheRepository.findById(7302).orElseThrow().getCategorieSeuil())
                .isEqualTo(CategorieSeuil.ENTRETIEN_ROUTIER);
    }
}
