package cnm.prs.config;

import jakarta.persistence.PrePersist;

/**
 * ⚠️ 2026-10-24 (demande front « horloge-recette », R1 : « le journal garde la vérité ») — sous horloge de recette décalée, une
 * entrée de journal enregistrée porte l'heure réelle ({@link HorlogeRecette#mention()}) dans son détail. Écouteur JPA posé sur les
 * journaux (journal général, journal du dossier, journaux des procédures en ligne, journal des modèles) ; sans décalage, rien.
 */
public class MentionHorlogeRecette {

    /** Une entrée de journal qui sait où écrire la mention (son détail, tenu dans la longueur de sa colonne). */
    public interface Journal {
        void mentionnerHeureReelle();
    }

    @PrePersist
    void mentionner(Object entree) {
        if (HorlogeRecette.decalee() && entree instanceof Journal j) {
            j.mentionnerHeureReelle();
        }
    }
}
