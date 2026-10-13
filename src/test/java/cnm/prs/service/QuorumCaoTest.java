package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.exception.BadRequestException;

/**
 * ⚠️ 2026-10-10 (manuel révisé, lot 1, R1, §B2) — la règle de quorum de la CAO : lecture, nombre requis selon l'effectif désigné.
 */
class QuorumCaoTest {

    @Test
    @DisplayName("La règle : vide ou MAJORITE, TOUS, un nombre ; autre chose est refusé")
    void regle() {
        assertThat(QuorumCao.parse(null).code()).isEqualTo("MAJORITE");
        assertThat(QuorumCao.parse(" majorite ").code()).isEqualTo("MAJORITE");
        assertThat(QuorumCao.parse("tous").code()).isEqualTo("TOUS");
        assertThat(QuorumCao.parse("3").nombre()).isEqualTo(3);
        assertThat(QuorumCao.parse("3").texte()).isEqualTo("3");
        assertThatThrownBy(() -> QuorumCao.parse("0")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> QuorumCao.parse("la moitié")).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Le nombre requis : majorité = plus de la moitié des désignés ; tous ; un nombre borné par l'effectif")
    void requis() {
        QuorumCao.Regle majorite = QuorumCao.parse("MAJORITE");
        assertThat(QuorumCao.requis(majorite, 2)).isEqualTo(2);
        assertThat(QuorumCao.requis(majorite, 3)).isEqualTo(2);
        assertThat(QuorumCao.requis(majorite, 4)).isEqualTo(3);
        assertThat(QuorumCao.requis(majorite, 5)).isEqualTo(3);
        assertThat(QuorumCao.requis(QuorumCao.parse("TOUS"), 5)).isEqualTo(5);
        assertThat(QuorumCao.requis(QuorumCao.parse("2"), 5)).isEqualTo(2);
        assertThat(QuorumCao.requis(QuorumCao.parse("9"), 5)).isEqualTo(5);
        assertThat(QuorumCao.requis(majorite, 0)).isZero();
        assertThat(QuorumCao.parse("2").libelle()).isEqualTo("2 membre(s) au moins");
        assertThat(majorite.libelle()).isEqualTo("majorité des membres désignés");
    }
}
