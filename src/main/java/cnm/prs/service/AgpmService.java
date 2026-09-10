package cnm.prs.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.repository.MarcheRepository;

/**
 * ⚠️ <strong>« Ce plan requiert-il un AGPM ? »</strong> — la question posée par trois écrans à la fois : le
 * <strong>sous-type</strong> du dossier et sa référence ({@code DossierIntegriteService}), la grille
 * d'examen ({@code ExamenService} — « on ne contrôle pas le vide ») et le drapeau {@code agpmRequis} du PPM
 * ({@code PpmService}). Une seule réponse, donc un seul endroit : trois copies de la règle auraient
 * divergé au premier ajustement du pilote.
 *
 * <h3>La règle</h3>
 * <p>Un plan requiert l'AGPM dès qu'<strong>au moins un</strong> de ses marchés est déclencheur. Un marché
 * l'est de deux façons :</p>
 * <ul>
 *   <li><strong>inconditionnellement</strong>, si son mode porte {@code declencheAgpm} — les
 *       <strong>appels d'offres</strong>, toutes variantes (règle du 2026-09-07, V21) ;</li>
 *   <li><strong>sous condition de montant</strong>, si son mode porte {@code agpmSiSeuil} et que son
 *       montant estimé atteint le <strong>seuil</strong> — l'<strong>appel à manifestation d'intérêt</strong>
 *       (arbitrage du 2026-09-07 « suite », V22).</li>
 * </ul>
 *
 * <p><strong>Le seuil est administrable</strong> : il vit dans {@code t_parametre}
 * ({@link ParametreService#AGPM_SEUIL_MONTANT}), saisi et ajusté par le pilote depuis l'administration,
 * sans redéploiement. Aucune valeur numérique n'est écrite dans ce code.</p>
 *
 * <p><strong>La comparaison est par MARCHÉ</strong>, jamais sur le total du dossier : c'est la maille de
 * la dérivation depuis toujours (un seul marché déclencheur suffit), et le pilote l'a confirmée.</p>
 *
 * <p><strong>Le montant retenu</strong> est celui <em>en vigueur</em> : le nouveau montant estimatif s'il
 * a été posé, le montant estimatif initial sinon. Un marché sans aucun montant ne déclenche rien — on ne
 * suppose pas qu'un montant absent franchit un seuil.</p>
 */
@Service
public class AgpmService {

    private final MarcheRepository marcheRepository;
    private final ParametreService parametreService;

    public AgpmService(MarcheRepository marcheRepository, ParametreService parametreService) {
        this.marcheRepository = marcheRepository;
        this.parametreService = parametreService;
    }

    /** Le dossier comporte-t-il au moins un marché déclencheur d'AGPM ? */
    @Transactional(readOnly = true)
    public boolean requisPourDossier(Integer idDossier) {
        return idDossier != null
                && marcheRepository.existsMarcheDeclencheurAgpmByDossier(idDossier, seuil());
    }

    /** Idem pour un PPM — même règle, autre clé d'entrée (drapeau {@code agpmRequis} du DTO). */
    @Transactional(readOnly = true)
    public boolean requisPourPpm(Integer idPpm) {
        return idPpm != null && marcheRepository.existsMarcheDeclencheurAgpmByPpm(idPpm, seuil());
    }

    /**
     * ⚠️ 2026-09-10 — les lignes qui DÉCLENCHENT l'AGPM, pour le périmètre d'examen d'une mise à jour :
     * le projet d'AGPM n'est réexaminé que si l'une d'elles a changé. Même prédicat que
     * {@link #requisPourDossier} — « existe-t-il » et « lesquelles » ne peuvent pas se contredire.
     */
    @Transactional(readOnly = true)
    public java.util.Set<Integer> lignesDeclenchantAgpm(Integer idDossier) {
        return idDossier == null ? java.util.Set.of()
                : new java.util.LinkedHashSet<>(marcheRepository.idsMarchesDeclencheursAgpm(idDossier, seuil()));
    }

    /** Seuil courant des modes à déclenchement conditionnel, tel que l'administration le porte. */
    @Transactional(readOnly = true)
    public BigDecimal seuil() {
        return parametreService.seuilAgpmMontant();
    }
}
