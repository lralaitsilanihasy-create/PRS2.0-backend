package cnm.prs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import cnm.prs.security.CurrentUser;
import cnm.prs.service.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Journalise automatiquement dans {@code t_audit_log} (§3.8) chaque écriture réussie de
 * l'API (POST / PUT / PATCH / DELETE renvoyant un code &lt; 400). Trace : utilisateur,
 * table (ressource), enregistrement, type d'action, IP. L'échec de l'audit n'interrompt
 * jamais la requête.
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);

    /**
     * Longueurs des colonnes de {@code t_audit_log} que cet intercepteur alimente (baseline Flyway
     * V1, reprises dans {@code cnm.prs.entity.AuditLog}) : une valeur plus longue serait refusée par
     * PostgreSQL, l'audit est donc tronqué ICI, au plus près de la colonne.
     */
    private static final int LONGUEUR_NOM_TABLE = 50;
    private static final int LONGUEUR_ID_ENREGISTREMENT = 20;

    /**
     * ⚠️ Recette 2026-08-27 — {@code TYPE_ACTION} était tronqué à <strong>10</strong> en dur, valeur
     * périmée depuis que la colonne est passée à {@code varchar(30)}
     * ({@code docs/migrations/2026-06-19_audit_log_type_action_len30.sql}). Le journal d'audit
     * enregistrait donc « CHANGER-MO » pour {@code POST /api/comptes/changer-mot-de-passe} : illisible,
     * et ambigu dès que deux sous-actions partagent leurs dix premiers caractères. La limite suit
     * désormais la colonne — 30 caractères couvrent la plus longue sous-action du contrat actuel
     * ({@code transmettre-complements-depot}, 29).
     */
    private static final int LONGUEUR_TYPE_ACTION = 30;

    private final AuditLogService auditLogService;

    public AuditInterceptor(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        try {
            if (ex != null || response.getStatus() >= 400 || !estEcriture(request.getMethod())) {
                return;
            }
            String[] parts = cheminApi(request.getRequestURI());
            if (parts.length == 0) {
                return;
            }
            String nomTable = tronquer(parts[0], LONGUEUR_NOM_TABLE);
            String idEnregistrement = null;
            String sousAction = null;
            if (parts.length >= 2) {
                if (parts[1].matches("\\d+")) {
                    idEnregistrement = tronquer(parts[1], LONGUEUR_ID_ENREGISTREMENT);
                    if (parts.length >= 3) {
                        sousAction = parts[2];
                    }
                } else {
                    sousAction = parts[1]; // action sur la collection (ex. suggestion-mode)
                }
            }
            String typeAction = sousAction != null
                    ? tronquer(sousAction.toUpperCase(), LONGUEUR_TYPE_ACTION)
                    : typeParMethode(request.getMethod());

            // ⚠️ Correctif 2026-08-26 — la limite suit la colonne : t_audit_log.IM_ACTEUR est passée
            // à varchar(10) (docs/migrations/2026-06-19_audit_log_im_acteur_len10.sql) précisément
            // pour porter un id PRMP (t_prmp.ID_PRMP = varchar 10). Le filtre était resté à 7 : tout
            // acteur dont la ref fait 8 à 10 caractères était journalisé avec IM_ACTEUR null.
            String ref = CurrentUser.ref().orElse(null);
            String imActeur = (ref != null && ref.length() <= 10) ? ref : null;

            auditLogService.enregistrer(imActeur, nomTable, idEnregistrement, typeAction, request.getRemoteAddr());
        } catch (Exception e) {
            // L'audit ne doit jamais casser la requête métier.
            log.warn("Échec de la journalisation d'audit : {}", e.getMessage());
        }
    }

    private boolean estEcriture(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private String typeParMethode(String method) {
        return switch (method) {
            case "POST" -> "CREATE";
            case "DELETE" -> "DELETE";
            default -> "UPDATE";
        };
    }

    /** Segments du chemin après {@code /api/} (ex. "/api/pv-examens/1/signer" → [pv-examens, 1, signer]). */
    private String[] cheminApi(String uri) {
        int i = uri.indexOf("/api/");
        if (i < 0) {
            return new String[0];
        }
        String reste = uri.substring(i + "/api/".length());
        if (reste.isBlank()) {
            return new String[0];
        }
        return reste.split("/");
    }

    private String tronquer(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
