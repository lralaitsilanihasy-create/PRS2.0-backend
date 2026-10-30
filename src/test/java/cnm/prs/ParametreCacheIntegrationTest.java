package cnm.prs;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import cnm.prs.repository.MemoLecture;
import cnm.prs.service.ParametreService;

/**
 * ⚠️ 2026-10-30 (demande front « performance », §B1, §B3) — le cache des paramètres et la mémoire de lecture des listes.
 */
class ParametreCacheIntegrationTest extends CnmIntegrationTestSupport {

    @Autowired private ParametreService parametres;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private org.hibernate.stat.Statistics stats() {
        org.hibernate.stat.Statistics s = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        s.setStatisticsEnabled(true);
        return s;
    }

    @Test
    @DisplayName("Un paramètre lu une fois n'est plus relu en base ; une écriture se lit aussitôt dans sa transaction (cache contourné "
            + "jusqu'à la fin de celle-ci)")
    void cacheDesParametres() {
        BigDecimal avant = parametres.seuilAgpmMontant();
        org.hibernate.stat.Statistics s = stats();
        s.clear();
        assertThat(parametres.seuilAgpmMontant()).isEqualByComparingTo(avant);
        assertThat(s.getPrepareStatementCount()).as("seconde lecture servie par le cache").isZero();
        parametres.fixerSeuilAgpmMontant(new BigDecimal("123456"));
        assertThat(parametres.seuilAgpmMontant()).isEqualByComparingTo("123456");
        parametres.fixerSeuilAgpmMontant(new BigDecimal("654321"));
        assertThat(parametres.seuilAgpmMontant()).isEqualByComparingTo("654321");
    }

    @Test
    @DisplayName("La mémoire de lecture ne sert qu'ouverte, se referme avec l'appel le plus extérieur et ne retient rien après")
    void memoireDeLecture() {
        int[] lectures = { 0 };
        assertThat(MemoLecture.ouverte()).isFalse();
        assertThat(MemoLecture.<Integer>lire("cle", () -> ++lectures[0])).isEqualTo(1);
        assertThat(MemoLecture.<Integer>lire("cle", () -> ++lectures[0])).isEqualTo(2);   // fermée : chaque appel lit
        try (MemoLecture m = MemoLecture.ouvrir()) {
            assertThat(MemoLecture.<Integer>lire("cle", () -> ++lectures[0])).isEqualTo(3);
            try (MemoLecture interieure = MemoLecture.ouvrir()) {
                assertThat(MemoLecture.<Integer>lire("cle", () -> ++lectures[0])).isEqualTo(3);
            }
            assertThat(MemoLecture.ouverte()).as("l'appel intérieur ne referme pas la mémoire de l'appelant").isTrue();
            MemoLecture.poser("autre", "préchargé");
            assertThat(MemoLecture.<String>lire("autre", () -> "lu")).isEqualTo("préchargé");
        }
        assertThat(MemoLecture.ouverte()).isFalse();
        assertThat(MemoLecture.<Integer>lire("cle", () -> ++lectures[0])).isEqualTo(4);
    }
}
