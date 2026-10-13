package cnm.prs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ⚠️ R7 (manuel révisé pour l'application, lot 2, §B2 ; V109, 2026-10-13) — les questions des candidats avant la date limite
 * (art. 35-II) et les réponses de la PRMP.
 */
public final class QuestionCandidatDto {

    private QuestionCandidatDto() {
    }

    /**
     * Une question : {@code etat} ∈ {@code EN_ATTENTE} · {@code REPONDUE} ; {@code auteur} (raison sociale, à défaut le compte) et
     * {@code auteurCompte} servis aux lecteurs internes seulement — <strong>anonymisée</strong> sur la page publique ; {@code mienne}
     * pour le candidat connecté.
     */
    public record Question(Long id, Long idDmc, String question, LocalDateTime poseeLe, String auteur, String auteurCompte, boolean mienne,
            String etat, String reponse, String reponseNom, Long reponseTaille, LocalDateTime repondueLe, LocalDateTime publieeLe) {
    }

    /**
     * La fenêtre des questions et la liste : {@code dateLimite} (remise des offres, ISO minute), {@code clotureLe} = le dernier jour où une
     * question est recevable (date limite − {@code joursAvantLimite}, délai légal n° 3), {@code closes} : ce jour est passé (ou la date
     * limite est inconnue).
     */
    public record Questions(String dateLimite, LocalDate clotureLe, boolean closes, int joursAvantLimite, List<Question> questions) {
    }

    /** {@code POST /api/procedures-en-ligne/{idDmc}/questions} : la question du candidat. */
    public record QuestionRequest(String question) {
    }
}
