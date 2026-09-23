package cnm.prs.exception;

/**
 * ⚠️ Fiche marché, lot 2a (demande front du 2026-09-23, §B1) — la production des documents d'une version a échoué :
 * la validation est <strong>annulée</strong> (la fiche reste en brouillon). Traduite en <strong>500</strong> nommé, code
 * {@code GENERATION_DOCUMENTS}, message qui dit quel document a échoué — mieux vaut une fiche restée en brouillon qu'une
 * version figée sans ses documents.
 */
public class GenerationDocumentsException extends RuntimeException {

    public GenerationDocumentsException(String message, Throwable cause) {
        super(message, cause);
    }
}
