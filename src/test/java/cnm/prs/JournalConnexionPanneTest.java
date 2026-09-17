package cnm.prs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import cnm.prs.entity.SessionUtilisateur;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.SessionUtilisateurRepository;
import cnm.prs.service.JournalConnexionService;

/**
 * ⚠️ Lot 6 (2026-09-17, §B4) — <strong>une panne du journal des connexions ne casse jamais une
 * connexion</strong>. C'est le principe déjà retenu par {@code AuditInterceptor} (« l'audit ne doit
 * jamais casser la requête »), et il est plus impératif encore ici : si une écriture ratée remontait,
 * une base pleine, un index corrompu ou une contrainte imprévue rendraient l'application
 * <em>inconnectable</em>, pour tout le monde, d'un coup.
 *
 * <p><strong>Pourquoi un test unitaire et non d'intégration.</strong> Faire échouer l'écriture depuis un
 * test d'intégration demanderait de remplacer le repository par un bouchon, donc un contexte Spring
 * distinct de celui que partage toute la suite — cinq minutes de démarrage pour vérifier un
 * {@code catch}. La garantie porte sur le service, elle se prouve sur le service ; l'appel du
 * contrôleur, lui, est couvert par {@link JournalConnexionIntegrationTest}.</p>
 */
class JournalConnexionPanneTest {

    private final SessionUtilisateurRepository sessions = mock(SessionUtilisateurRepository.class);
    private final CompteAuthRepository comptes = mock(CompteAuthRepository.class);
    private final JournalConnexionService journal = new JournalConnexionService(sessions, comptes);

    private void baseEnPanne() {
        when(comptes.findByLogin(anyString())).thenReturn(Optional.empty());
        when(sessions.save(any(SessionUtilisateur.class)))
                .thenThrow(new DataIntegrityViolationException("disque plein"));
        when(sessions.fermer(anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("disque plein"));
    }

    @Test
    @DisplayName("Journal en panne : la connexion réussie n'en sait rien")
    void connexionReussie_avale() {
        baseEnPanne();
        assertDoesNotThrow(() -> journal.connexionReussie("CTRADM", "jeton", "127.0.0.1", "agent"));
    }

    @Test
    @DisplayName("Journal en panne : la tentative refusée n'en sait rien non plus")
    void connexionRefusee_avale() {
        baseEnPanne();
        assertDoesNotThrow(() -> journal.connexionRefusee("CTRADM", "127.0.0.1", "agent"));
    }

    @Test
    @DisplayName("Journal en panne : la déconnexion répond « rien fermé » au lieu de lever")
    void deconnexion_avale() {
        baseEnPanne();
        assertFalse(journal.deconnexion("jeton"));
    }

    @Test
    @DisplayName("Empreinte : déterministe, 64 hexadécimaux, et deux jetons différents ne se confondent pas")
    void empreinte_deterministe() {
        String a = JournalConnexionService.empreinte("jeton-a");
        org.junit.jupiter.api.Assertions.assertEquals(a, JournalConnexionService.empreinte("jeton-a"));
        org.junit.jupiter.api.Assertions.assertEquals(64, a.length());
        org.junit.jupiter.api.Assertions.assertTrue(a.matches("[0-9a-f]{64}"));
        org.junit.jupiter.api.Assertions.assertNotEquals(a, JournalConnexionService.empreinte("jeton-b"));
    }
}
