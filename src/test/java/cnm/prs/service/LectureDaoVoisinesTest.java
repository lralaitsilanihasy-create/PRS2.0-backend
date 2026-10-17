package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠️ 2026-10-17 (demande front « lecture-valeurs-voisines ») — deux valeurs voisines d'un même paragraphe (B1, B2), la première
 * accroche confirmée (la cause de B02-SG-01 à 03 non relus), et les intervalles qui contiennent un titre de clause.
 */
class LectureDaoVoisinesTest {

    @Test
    @DisplayName("B1 — une valeur connue (plan) au bord du texte lu : l'autre est le reste, séparateur ôté ; absente du bord : rien")
    void valeurConnue() {
        assertThat(LectureDao.voisines("{{B02-OB-03}} — {{B02-OB-01}}", "001-DAOO/MEN/PRMP — Acquisition de matériels informatiques",
                Map.of("B02-OB-01", "Acquisition de matériels informatiques"), List.of(), c -> null))
                .containsExactly("001-DAOO/MEN/PRMP", "Acquisition de matériels informatiques");
        // Le numéro contient lui-même le séparateur : B2 ne couperait pas, B1 le peut.
        assertThat(LectureDao.voisines("{{B02-OB-03}} — {{B02-OB-01}}", "07 — CC/MEF — Fourniture de papier",
                Map.of("B02-OB-01", "Fourniture de papier"), List.of(), c -> null)).containsExactly("07 - CC/MEF", "Fourniture de papier");   // normalisé : la reprojection rend « — »
        assertThat(LectureDao.voisines("{{B02-OB-03}} — {{B02-OB-01}}", "07 — CC/MEF — Fourniture de papier",
                Map.of("B02-OB-01", "Autre objet"), List.of(), c -> null)).isNull();
    }

    @Test
    @DisplayName("B2 — un séparateur présent une seule fois ; « ( » seulement refermé par le modèle ; deux occurrences : rien ; un type à respecter")
    void separateurUnique() {
        assertThat(LectureDao.voisines("{{B02-SG-01}} ({{B02-SG-02}}).", "RAKOTO Jean (Décision n° 031-MEF/SG du 6 janvier 2027).", Map.of(),
                List.of(), c -> null)).containsExactly("RAKOTO Jean", "Décision n° 031-MEF/SG du 6 janvier 2027");
        assertThat(LectureDao.voisines("{{B02-LT-06}} ; {{B02-LT-07}}", "Lot 1 : voirie ; Lot 2 : assainissement", Map.of(), List.of(), c -> null))
                .containsExactly("Lot 1 : voirie", "Lot 2 : assainissement");
        assertThat(LectureDao.voisines("{{B02-SG-01}} ({{B02-SG-02}}).", "RAKOTO (Jean) (Décision n° 031)", Map.of(), List.of(), c -> null)).isNull();
        assertThat(LectureDao.voisines("{{A}} ; {{B}}", "un ; deux ; trois", Map.of(), List.of(), c -> null)).isNull();
        // Un champ numérique doit se lire comme un nombre.
        assertThat(LectureDao.voisines("{{B04-VO-01}} ; {{B02-LT-07}}", "cent vingt ; Lot 2", Map.of(), List.of(),
                c -> new LectureDao.InfoChamp("NOMBRE", "SAISIE", null, null))).isNull();
    }

    @Test
    @DisplayName("Un titre d'article (lettres espacées comprises) ou une clause numérotée n'est pas une valeur ; un trou du modèle non plus")
    void titresEtTrous() {
        assertThat(LectureDao.titreDeClause("AR TIC L E4.- GR O U P E ME N T S (CCAG AR TI C L E5)")).isTrue();
        assertThat(LectureDao.titreDeClause("ARTICLE PREMIER : OBJET")).isTrue();
        assertThat(LectureDao.titreDeClause("1.1-Lots, Lot : Lot unique et indivisible")).isTrue();
        assertThat(LectureDao.titreDeClause("Un (01) millième du montant du Marché")).isFalse();
        assertThat(LectureDao.titreDeClause("Articles de bureau et consommables")).isFalse();
        assertThat(LectureDao.valeurSaisie("<insérer l'adresse complète>", null, null)).isNull();
        assertThat(LectureDao.valeurSaisie("Analakely", null, null)).isEqualTo("Analakely");
    }

    @Test
    @DisplayName("Accroche confirmée — l'AE-CC qui suit un DPAC au même bloc d'autorité : son préambule est relu (B02-SG-01 à 03)")
    void accrocheConfirmee() {
        List<String> doc = new ArrayList<>();
        // Le DPAC : le même en-tête d'autorité que l'AE-CC, puis son propre texte.
        doc.addAll(List.of("MINISTERE DE L'ECONOMIE ET DES FINANCES", "Antananarivo", "Personne responsable des marchés publics : RAKOTO Jean",
                "Courriel : prmp@mef.mg – Téléphone : 0330000001", "MARCHE DE : Fournitures et services", "NUMÉRO : 07-CC/MEF"));
        for (int i = 0; i < 70; i++) {
            doc.add("Clause " + i + " du règlement de la consultation, sans rapport avec l'acte d'engagement.");
        }
        // L'AE-CC.
        doc.addAll(List.of("MINISTERE DE L'ECONOMIE ET DES FINANCES", "Antananarivo", "Personne responsable des marchés publics : RAKOTO Jean",
                "Courriel : prmp@mef.mg – Téléphone : 0330000001", "Fourniture de papier", "CONTRAT-CADRE Valant acte d'engagement et CCAP UNIQUE",
                "Code des marchés publics (Article 67)", "PREAMBULE – DISPOSITIONS GENERALES",
                "Personne Publique contractante : MINISTERE DE L'ECONOMIE ET DES FINANCES, Antananarivo",
                "Personne habilitée à signer le contrat-cadre : RAKOTO Jean.",
                "Personne responsable des marchés passés sur la base du présent contrat-cadre : RAKOTO Jean (Décision n° 031-MEF/SG du 6 janvier 2027).",
                "Procédure de passation du contrat-cadre :", "ARTICLE 1er – Contractants (1)", "Le contrat-cadre est conclu entre :",
                "- D'une part, MINISTERE DE L'ECONOMIE ET DES FINANCES, ci-après dénommée « l'autorité contractante »,",
                "Représentée par : RAKOTO Jean, nommée par Arrêté n° 0457/2027-MEF du 4 janvier 2027."));
        List<String> lu = doc.stream().map(LectureDao::norm).toList();
        var r = LectureDao.lire("AE-CC", new ModelesDao().modele("AE-CC"), lu, c -> null);
        assertThat(r.propositions()).extracting(LectureDao.Proposition::code).contains("B02-SG-01", "B02-SG-02", "B02-SG-03");
        assertThat(r.propositions()).filteredOn(p -> p.code().equals("B02-SG-03")).extracting(LectureDao.Proposition::valeur)
                .containsExactly("Arrêté n° 0457/2027-MEF du 4 janvier 2027");
    }
}
