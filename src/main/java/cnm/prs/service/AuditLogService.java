package cnm.prs.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AuditLogDto;
import cnm.prs.entity.AuditLog;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.mapper.AuditLogMapper;
import cnm.prs.repository.AuditLogRepository;

/**
 * Logique métier pour {@link AuditLog}.
 *
 * <p>⚠️ Audit 2026-08-27 (§3.2 du rapport) — le journal {@code t_audit_log} est en <strong>ajout
 * seul</strong>. Son unique auteur est {@link #enregistrer}, appelé par l'intercepteur HTTP après
 * chaque écriture réussie. Les trois verbes d'écriture du CRUD générique (POST, PUT, DELETE) sont
 * donc refusés en 409 : jusqu'ici seul DELETE l'était, si bien que {@code update} réécrivait la
 * totalité de la preuve (date, acteur, valeurs avant/après, IP, session) et que le POST permettait
 * d'insérer des entrées forgées.</p>
 */
@Service
@Transactional
public class AuditLogService {

    /** Préfixe commun aux trois refus d'écriture (⚠️ audit 2026-08-27) — un seul message d'immuabilité. */
    private static final String IMMUABLE = "Le journal d'audit est immuable :";

    /**
     * ⚠️ Audit 2026-08-27 (lot D §4) — <strong>plafond de la liste historique</strong> : 500 entrées.
     *
     * <p>{@code t_audit_log} reçoit une ligne à chaque écriture de l'application ; sa croissance est
     * monotone et sans fin. La liste plate en demandait la totalité, ce qui revient, après quelques
     * mois d'exploitation, à télécharger des années de journal pour en regarder les vingt dernières
     * lignes. Elle rend désormais les <strong>500 plus récentes</strong>, ce qui garde l'écran
     * existant fonctionnel sans le rendre dépendant de l'âge de la base. Au-delà, la variante paginée
     * ({@code ?page=&size=}, avec ses filtres) est le seul chemin d'accès complet.</p>
     */
    private static final int LIMITE_HISTORIQUE = 500;

    /** Ordre du journal : du plus récent au plus ancien — le seul utile pour une consultation. */
    private static final Sort PLUS_RECENT_DABORD = Sort.by(Sort.Direction.DESC, "dateAction");

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Liste historique du journal, <strong>bornée aux {@value #LIMITE_HISTORIQUE} entrées les plus
     * récentes</strong> (⚠️ audit 2026-08-27, lot D §4 — voir {@link #LIMITE_HISTORIQUE}). Pour
     * remonter au-delà, ou filtrer, utiliser {@link #rechercher}.
     */
    @Transactional(readOnly = true)
    public List<AuditLogDto> findAll() {
        return repository.rechercher(null, null, null, null,
                PageRequest.of(0, LIMITE_HISTORIQUE, PLUS_RECENT_DABORD))
                .map(AuditLogMapper::toDto).getContent();
    }

    /**
     * Recherche paginée et filtrée du journal (⚠️ audit 2026-08-27, lot D §4 — ajout de contrat).
     * Tous les filtres sont facultatifs ; la page est triée du plus récent au plus ancien, quel que
     * soit le tri demandé par le client (le journal n'a qu'un ordre de lecture sensé).
     *
     * @param nomTable table auditée ({@code t_audit_log.NOM_TABLE}), exacte ; {@code null}/vide = toutes
     * @param acteur   acteur de l'écriture ({@code IM_ACTEUR}), exact ; {@code null}/vide = tous
     * @param du       premier jour inclus ; {@code null} = pas de borne inférieure
     * @param au       dernier jour <strong>inclus</strong> (la journée entière) ; {@code null} = pas de borne
     */
    @Transactional(readOnly = true)
    public Page<AuditLogDto> rechercher(String nomTable, String acteur, LocalDate du, LocalDate au,
            Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), PLUS_RECENT_DABORD);
        return repository.rechercher(vide(nomTable), vide(acteur),
                du == null ? null : du.atStartOfDay(),
                // Dernier instant représentable du jour (t_audit_log."DATE_ACTION" est un timestamp(6)) :
                // la journée demandée est incluse en entier, sans risque d'arrondi sur le lendemain.
                au == null ? null : au.plusDays(1).atStartOfDay().minusNanos(1_000),
                page)
                .map(AuditLogMapper::toDto);
    }

    /** Un filtre vide vaut « pas de filtre » (le front envoie volontiers une chaîne vide). */
    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    @Transactional(readOnly = true)
    public AuditLogDto findById(Long id) {
        AuditLog entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog introuvable : " + id));
        return AuditLogMapper.toDto(entity);
    }

    /**
     * Création manuelle interdite (⚠️ audit 2026-08-27) : le journal n'est alimenté que par
     * {@link #enregistrer}, appelé par l'intercepteur HTTP après chaque écriture réussie. Un POST
     * ouvert permettait d'y <strong>forger</strong> des entrées — un journal où l'on peut écrire à la
     * main ne prouve plus rien. → HTTP 409, comme la suppression.
     */
    public AuditLogDto create(AuditLogDto dto) {
        throw new BusinessRuleException(IMMUABLE + " création interdite (§3.8).");
    }

    /**
     * Modification interdite (⚠️ audit 2026-08-27) : {@code update} réécrivait date, acteur, table,
     * valeurs avant/après, adresse IP et session — soit la totalité de la preuve. L'immuabilité
     * annoncée n'était vraie que pour DELETE. → HTTP 409.
     */
    public AuditLogDto update(Long id, AuditLogDto dto) {
        throw new BusinessRuleException(IMMUABLE + " modification interdite (§3.8).");
    }

    /**
     * Suppression interdite : le journal d'audit est immuable — toutes les actions y sont
     * tracées et conservées (§3.8). → HTTP 409.
     */
    public void delete(Long id) {
        throw new BusinessRuleException(IMMUABLE + " suppression interdite (§3.8).");
    }

    /**
     * Enregistre une entrée d'audit (§3.8). Appelé automatiquement par l'intercepteur HTTP
     * après chaque écriture réussie. {@code SESSION_ID} reste {@code null} (FK vers
     * {@code t_session_utilisateur}, pas de session réelle pour l'instant).
     */
    public void enregistrer(String imActeur, String nomTable, String idEnregistrement,
            String typeAction, String ipAdresse) {
        AuditLog log = new AuditLog();
        log.setIdLog(repository.nextIdAuditLog());   // PK serveur (sequence)
        log.setDateAction(LocalDateTime.now());
        log.setImActeur(imActeur);
        log.setNomTable(nomTable);
        log.setIdEnregistrement(idEnregistrement);
        log.setTypeAction(typeAction);
        log.setIpAdresse(ipAdresse);
        repository.save(log);
    }
}
