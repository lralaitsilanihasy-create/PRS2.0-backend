package cnm.prs.service;

import java.util.function.Predicate;
import java.util.function.Supplier;

import cnm.prs.exception.BusinessRuleException;

/**
 * Allocation et contrôle des clés primaires <strong>à la création</strong> (⚠️ LOT 3b — 2026-08-26).
 *
 * <p>Avant ce chantier, une cinquantaine de services faisaient {@code repository.save(toEntity(dto))}
 * avec la PK fournie par le client. Or {@code save()} sur une PK déjà présente n'insère pas : il
 * fait un <strong>MERGE</strong>. Un {@code POST /api/xxx} portant {@code {"id": 42, ...}} écrasait
 * donc <strong>silencieusement</strong> la ligne 42 — y compris celle d'une autre PRMP — et
 * répondait 201. Deux réponses, selon la nature de la clé :</p>
 *
 * <ul>
 *   <li>{@link #exigerLibre} — clé <strong>sémantique</strong>, choisie par l'utilisateur
 *       (référentiels à clé naturelle : {@code tr_localite}, {@code tr_type_dossier}… ; identifiant
 *       de publication saisi au clavier). Le conflit est une erreur de saisie : il doit être
 *       signalé → <strong>409</strong>.</li>
 *   <li>{@link #reallouer} — clé <strong>technique</strong>, calculée par le front
 *       ({@code max(id) + 1} en TypeScript : {@code crud-page.ts} pour les référentiels
 *       administrables, {@code dispatch-form.ts}, {@code examen-dossier.ts},
 *       {@code soumettre-dossier.ts}, {@code detail-ppm-modal.ts}). Refuser casserait un flux
 *       réel — et {@code examen-dossier.ts} réutilise localement l'identifiant qu'il a envoyé.
 *       La PK proposée est donc honorée si elle est libre, sinon une PK neuve est allouée à la
 *       séquence serveur. C'est la « Voie B » posée sur {@code LotService} et
 *       {@code MarchePrevisionService} au LOT 3a, ici généralisée et adossée aux séquences.</li>
 * </ul>
 *
 * <p>Le calcul {@code max(id) + 1} qu'utilisaient les services n'est pas atomique : deux créations
 * concurrentes lisent le même maximum et se disputent le même identifiant. Toute allocation passe
 * désormais par une séquence PostgreSQL (migration {@code V5}), seule primitive atomique.</p>
 */
public final class ClePrimaire {

    private ClePrimaire() {
    }

    /**
     * Garde de création sur une clé <strong>sémantique</strong>.
     *
     * <p>⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.</p>
     *
     * @param idPropose identifiant porté par le DTO ({@code null} accepté : rien à contrôler)
     * @param existe    test d'existence en base (typiquement {@code repository::existsById})
     * @param ressource libellé de la ressource, au singulier, pour le message d'erreur
     * @throws BusinessRuleException (HTTP 409) si l'identifiant est déjà pris
     */
    public static <T> void exigerLibre(T idPropose, Predicate<T> existe, String ressource) {
        if (idPropose != null && existe.test(idPropose)) {
            throw new BusinessRuleException(
                    "Un(e) " + ressource + " avec cet identifiant existe déjà.");
        }
    }

    /**
     * Allocation de création sur une clé <strong>technique</strong> calculée par le front
     * (« Voie B ») : la PK proposée est conservée si elle est libre, sinon réallouée.
     *
     * <p>⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.</p>
     */
    public static Integer reallouer(Integer idPropose, Predicate<Integer> existe,
            Supplier<Long> sequence) {
        if (idPropose != null && !existe.test(idPropose)) {
            return idPropose;
        }
        return allouerLibre(existe, sequence);
    }

    /**
     * Alloue une PK neuve à la séquence, en sautant les valeurs déjà prises.
     *
     * <p>⚠️ LOT 3b (2026-08-26) — un POST ne peut pas écraser un enregistrement existant.
     * Le saut est nécessaire sur les tables où coexistent une PK cliente honorée
     * ({@link #reallouer}) et une allocation serveur : une PK cliente acceptée <em>au-dessus</em>
     * du curseur de la séquence n'y est pas consommée, et un {@code nextval} ultérieur la
     * rendrait une seconde fois. La boucle termine : la séquence est strictement croissante et
     * la table finie.</p>
     */
    public static Integer allouerLibre(Predicate<Integer> existe, Supplier<Long> sequence) {
        Integer candidat;
        do {
            candidat = sequence.get().intValue();
        } while (existe.test(candidat));
        return candidat;
    }
}
