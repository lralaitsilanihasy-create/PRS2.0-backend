package cnm.prs.dto;

/**
 * ⚠️ 2026-11-04 (demande front « avancement-plans », B1) — l'avancement des lignes d'un plan : chaque ligne non retirée comptée dans
 * une seule étape, la première qui s'applique — {@code marche} (un dossier de marché), puis selon le dossier de mise en concurrence
 * {@code lancee} (clos ou décision transmise), {@code commission} (au circuit), {@code preparation} (brouillon), puis {@code lancer}
 * (une procédure sans dossier, ou un gré à gré), sinon {@code aucune}.
 */
public record AvancementLignesDto(int lignes, int marche, int commission, int lancee, int preparation, int lancer, int aucune) {
}
