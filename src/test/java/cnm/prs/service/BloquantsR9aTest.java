package cnm.prs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.entity.FicheMarche;
import cnm.prs.entity.Parametre;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.repository.ParametreRepository;

/**
 * ⚠️ R9a (manuel révisé pour l'application, lot 2, §B4 n° 2 et n° 13) — le DAO complet exigé avant la publication, les recours
 * suspensifs lus au paramètre.
 */
class BloquantsR9aTest {

    private static AvisSpecifiqueService avis(DaoCompletService daoComplet) {
        return avis(daoComplet, mock(DaoCompletFileService.class));
    }

    @SuppressWarnings("unchecked")
    private static AvisSpecifiqueService avis(DaoCompletService daoComplet, DaoCompletFileService file) {
        org.springframework.beans.factory.ObjectProvider<DaoCompletFileService> p = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(p.getObject()).thenReturn(file);
        return new AvisSpecifiqueService(null, null, null, null, null, null, null, null, null, null, null, null, null, daoComplet, null, null, p, null);
    }

    private static FicheMarche version(int n) {
        FicheMarche f = new FicheMarche();
        f.setIdFiche(7);
        f.setNumeroVersion(n);
        return f;
    }

    @Test
    @DisplayName("n° 2 — sans assemblage actif sur le serveur, la règle ne s'applique pas")
    void daoCompletInactif() {
        DaoCompletService dao = mock(DaoCompletService.class);
        when(dao.actif()).thenReturn(false);
        DaoCompletFileService file = mock(DaoCompletFileService.class);
        assertThatCode(() -> avis(dao, file).exigerDaoComplet(version(2), new FicheMarcheDto(), 10)).doesNotThrowAnyException();
        verify(file, never()).mettreEnTete(any());
    }

    @Test
    @DisplayName("n° 2 — le DAO complet présent laisse publier, sans toucher à la file")
    void daoCompletPresent() {
        DaoCompletService dao = mock(DaoCompletService.class);
        when(dao.actif()).thenReturn(true);
        when(dao.existe(7)).thenReturn(true);
        DaoCompletFileService file = mock(DaoCompletFileService.class);
        assertThatCode(() -> avis(dao, file).exigerDaoComplet(version(2), new FicheMarcheDto(), 10)).doesNotThrowAnyException();
        verify(file, never()).mettreEnTete(any());
    }

    @Test
    @DisplayName("n° 2 (⚠️ 28/10, « dao-complet-en-file », Q2) — absent : la version passe en tête de file, 409 DAO_COMPLET_ABSENT avec la version et le rang, sans assembler")
    void daoCompletAbsent() {
        DaoCompletService dao = mock(DaoCompletService.class);
        when(dao.actif()).thenReturn(true);
        when(dao.existe(7)).thenReturn(false);
        DaoCompletFileService file = mock(DaoCompletFileService.class);
        FicheMarche v3 = version(3);
        when(file.mettreEnTete(v3)).thenReturn(1);
        BusinessRuleException e = catchThrowableOfType(BusinessRuleException.class,
                () -> avis(dao, file).exigerDaoComplet(v3, new FicheMarcheDto(), 10));
        assertThat(e.getCode()).isEqualTo(AvisSpecifiqueService.DAO_COMPLET_ABSENT);
        assertThat(e.getIdDossier()).isEqualTo(10);
        assertThat(e.getDetails()).containsEntry("version", 3).containsEntry("rang", 1);
        assertThat(e.getMessage()).contains("en préparation").contains("n° 1 dans la file");
        verify(dao, never()).assurer(any(), any());
        verify(dao, never()).assembler(any());
    }

    @Test
    @DisplayName("n° 2 — la disponibilité annonce l'état du DAO complet : produit, à assembler, sans assemblage, rien sans version validée")
    void etatDaoComplet() {
        cnm.prs.repository.FicheMarcheRepository fiches = mock(cnm.prs.repository.FicheMarcheRepository.class);
        cnm.prs.repository.DocumentFicheMarcheRepository documents = mock(cnm.prs.repository.DocumentFicheMarcheRepository.class);
        DaoCompletService dao = mock(DaoCompletService.class);
        AvisSpecifiqueService s = new AvisSpecifiqueService(null, fiches, null, null, null, null, null, null, null, documents, null, null, null, dao, null, null, null, null);
        when(fiches.findByIdDmcOrderByNumeroVersionAsc(5L)).thenReturn(java.util.List.of());
        assertThat(s.etatDaoComplet(5L)).isNull();
        FicheMarche v = version(2);
        v.setStatut(cnm.prs.enums.StatutFicheMarche.VALIDEE.name());
        when(fiches.findByIdDmcOrderByNumeroVersionAsc(5L)).thenReturn(java.util.List.of(v));
        when(documents.findByIdFicheOrderByIdDocumentAsc(7)).thenReturn(java.util.List.of());
        when(dao.actif()).thenReturn(false);
        assertThat(s.etatDaoComplet(5L)).isEqualTo("SANS_ASSEMBLAGE");
        when(dao.actif()).thenReturn(true);
        assertThat(s.etatDaoComplet(5L)).isEqualTo("A_ASSEMBLER");
        cnm.prs.entity.DocumentFicheMarche complet = new cnm.prs.entity.DocumentFicheMarche();
        complet.setType(DaoCompletService.TYPE);
        when(documents.findByIdFicheOrderByIdDocumentAsc(7)).thenReturn(java.util.List.of(complet));
        assertThat(s.etatDaoComplet(5L)).isEqualTo("PRODUIT");
    }

    @Test
    @DisplayName("n° 13 — paramètre absent : révision ARMP et référé ; « AUCUN » : aucun ; valeur inconnue ignorée")
    void recoursSuspensifs() {
        ParametreRepository repo = mock(ParametreRepository.class);
        ParametreService p = new ParametreService(repo);
        when(repo.findById(ParametreService.RECOURS_SUSPENSIFS)).thenReturn(Optional.empty());
        assertThat(p.recoursSuspensifs()).containsExactlyInAnyOrder("REVISION_ARMP", "REFERE");
        when(repo.findById(ParametreService.RECOURS_SUSPENSIFS)).thenReturn(Optional.of(new Parametre(ParametreService.RECOURS_SUSPENSIFS, "AUCUN", null, null)));
        assertThat(p.recoursSuspensifs()).isEmpty();
        when(repo.findById(ParametreService.RECOURS_SUSPENSIFS)).thenReturn(Optional.of(new Parametre(ParametreService.RECOURS_SUSPENSIFS, "reexamen, FOO", null, null)));
        assertThat(p.recoursSuspensifs()).containsExactly("REEXAMEN");
        assertThat(p.lireRecoursSuspensifs().connus()).containsExactly("REEXAMEN", "REVISION_ARMP", "REFERE");
    }
}
