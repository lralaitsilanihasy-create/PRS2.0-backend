package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ Audit 2026-08-27 (lot D §1) — état du schéma après la migration {@code V8}.
 *
 * <p>PostgreSQL indexe la colonne <em>référencée</em> d'une clé étrangère, jamais la colonne
 * <em>référençante</em> : le schéma portait 92 FK pour 15 index. Ce test fixe le résultat de la V8 —
 * les colonnes FK réellement filtrées sont indexées, la contrainte d'unicité dupliquée de
 * {@code t_delegation_profil} a disparu, et {@code t_piece_jointe_dossier.ID_DOSSIER} porte enfin
 * une vraie clé étrangère.</p>
 *
 * <p>Il lit le catalogue de PostgreSQL, pas un fichier : c'est le schéma <strong>réellement
 * appliqué</strong> par Flyway sur le conteneur qui est vérifié.</p>
 */
class SchemaIndexFkIntegrationTest extends CnmIntegrationTestSupport {

    /** Un index attendu, décrit par sa table et la (ou les) colonne(s) qu'il doit couvrir en tête. */
    private record IndexAttendu(String table, String colonnes) {
    }

    /**
     * Les colonnes FK que la V8 doit avoir indexées. Le nom de l'index n'est pas testé (il peut
     * évoluer) : ce qui compte est qu'un index EXISTE sur la table et commence par ces colonnes.
     */
    private static final List<IndexAttendu> INDEX_ATTENDUS = List.of(
            // Chaîne de visibilité par localité.
            new IndexAttendu("t_reception", "\"IM_CTRL_RECEPT\""),
            new IndexAttendu("tr_controleur", "\"ID_LOCALITE\""),
            new IndexAttendu("t_dispatch", "\"ID_RECEPTION\""),
            new IndexAttendu("t_examen", "\"ID_DISPATCH\""),
            // Aval de l'examen.
            new IndexAttendu("t_examen_detail", "\"ID_EXAMEN\""),
            new IndexAttendu("t_examen_piece", "\"ID_EXAMEN\""),
            new IndexAttendu("t_pv_examen", "\"ID_EXAMEN\""),
            new IndexAttendu("t_verification", "\"ID_PV\""),
            new IndexAttendu("t_verification", "\"ID_RECEPTION\""),
            // Fan-out par dossier.
            new IndexAttendu("t_lettre_renvoi", "\"ID_DOSSIER\""),
            new IndexAttendu("t_demande_retrait", "\"ID_DOSSIER\""),
            new IndexAttendu("t_lot", "\"ID_DOSSIER\""),
            new IndexAttendu("t_piece_jointe_dossier", "\"ID_DOSSIER\""),
            // Fan-out par ligne de marché.
            new IndexAttendu("t_lot", "\"ID_DETAIL\""),
            new IndexAttendu("t_echeance", "\"ID_DETAIL\""),
            new IndexAttendu("t_marche_prevision", "\"ID_DETAIL\""),
            new IndexAttendu("t_service_beneficiaire", "\"ID_DETAIL\""),
            // Destinataires.
            new IndexAttendu("t_notification", "\"DESTINATAIRE_REF\", \"DESTINATAIRE_TYPE\""),
            new IndexAttendu("t_message", "\"DESTINATAIRE_IM\""));

    @Test
    @DisplayName("V8 — chaque colonne FK réellement filtrée porte un index")
    void colonnesFk_indexees() {
        for (IndexAttendu attendu : INDEX_ATTENDUS) {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' AND tablename = ? "
                            + "AND indexdef LIKE ?",
                    Integer.class, attendu.table(), "%(" + attendu.colonnes() + "%");
            assertThat(n).as("index sur %s (%s)", attendu.table(), attendu.colonnes()).isNotNull().isPositive();
        }
    }

    @Test
    @DisplayName("V8 — t_delegation_profil ne porte plus QU'UNE contrainte d'unicité sur la paire "
            + "(délégant, délégué) : celle que déclare l'entité")
    void contrainteUnicite_dupliquee_retiree() {
        List<String> noms = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid "
                        + "WHERE t.relname = 't_delegation_profil' AND c.contype = 'u'",
                String.class);
        // Avant la V8 : « UQ_DELEGATION_PAIRE » (citée) ET uq_delegation_paire (repliée en
        // minuscules) coexistaient — deux index uniques maintenus pour une seule règle.
        assertThat(noms).containsExactly("UQ_DELEGATION_PAIRE");
    }

    @Test
    @DisplayName("V8 — t_piece_jointe_dossier.ID_DOSSIER porte une clé étrangère vers t_dossier")
    void fkPieceJointeDossier_posee() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_constraint c "
                        + "JOIN pg_class enfant ON enfant.oid = c.conrelid "
                        + "JOIN pg_class parent ON parent.oid = c.confrelid "
                        + "WHERE c.contype = 'f' AND enfant.relname = 't_piece_jointe_dossier' "
                        + "AND parent.relname = 't_dossier'",
                Integer.class);
        assertThat(n).as("FK t_piece_jointe_dossier.ID_DOSSIER -> t_dossier").isNotNull().isPositive();
    }

    @Test
    @DisplayName("V8 — la FK est active : une pièce jointe sur un dossier inexistant est refusée")
    void fkPieceJointeDossier_refuseUnDossierInexistant() {
        // ⚠️ Dernière instruction du test : l'échec avorte la transaction, que le socle annule ensuite.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO public.t_piece_jointe_dossier "
                        + "(\"ID_DOSSIER\", \"ID_TYPE_PIECE\", \"APRES_LETTRE_RENVOI\", \"NOM_FICHIER\") "
                        + "VALUES (?, 1, false, 'orpheline.pdf')",
                999_999))
                .hasMessageContaining("fk_piece_jointe_dossier_dossier");
    }
}
