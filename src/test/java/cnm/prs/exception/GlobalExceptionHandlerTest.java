package cnm.prs.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * Tests unitaires du mappage exception → réponse HTTP de {@link GlobalExceptionHandler}.
 *
 * <p>Pourquoi ici et non dans le test d'intégration : la suite d'intégration est {@code @Transactional},
 * donc l'INSERT n'est pas vidé en base avant l'assertion — une violation de contrainte SQL ne peut pas y
 * être provoquée de façon fiable. Le mappage se teste donc à sa source, sur l'instance du gestionnaire,
 * en lui présentant l'exception telle que le pilote la remonte.</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static WebRequest requete() {
        return new ServletWebRequest(new MockHttpServletRequest("POST", "/api/aviss"));
    }

    /** Violation d'intégrité telle que Spring la remonte : le SQLSTATE est porté par une cause SQLException. */
    private static DataIntegrityViolationException violation(String sqlState, String message) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException(message, sqlState));
    }

    @Test
    @DisplayName("SQLSTATE 22001 : depassement de longueur -> 400 nommant le champ, jamais 409")
    void depassementLongueur_400EtChampNomme() {
        // Un dépassement de longueur tombait dans le `default` du switch : 409 « Violation d'une contrainte
        // de données », sans dire lequel — alors que la MÊME valeur arrêtée en amont par @Size donne un 400
        // pointant le champ. Deux réponses incomparables pour une seule faute de saisie : le front ne pouvait
        // pas traiter le cas uniformément. Ce test fige l'équivalence des deux chemins.
        ResponseEntity<ErrorResponse> reponse = handler.handleDataIntegrity(
                violation("22001", "Value too long for column \"ID_AVIS CHARACTER VARYING(10)\": \"'AVIS-TROP-LONG' (15)\""),
                requete());

        assertEquals(HttpStatus.BAD_REQUEST, reponse.getStatusCode());
        assertNotNull(reponse.getBody());
        assertNotNull(reponse.getBody().erreurs());
        assertEquals(1, reponse.getBody().erreurs().size());
        // Colonne SNAKE_MAJUSCULE convertie en nom de propriété : c'est ce que le front reçoit et affiche.
        assertEquals("idAvis", reponse.getBody().erreurs().get(0).champ());
    }

    @Test
    @DisplayName("SQLSTATE 22001 sans colonne citee : 400 quand meme, sans tableau erreurs")
    void depassementLongueur_sansColonne_400SansErreurs() {
        // PostgreSQL, contrairement à H2, ne nomme pas la colonne sur un 22001. Le nommage est donc un bonus,
        // pas une condition : le code de statut ne doit pas dépendre de la verbosité du pilote.
        ResponseEntity<ErrorResponse> reponse = handler.handleDataIntegrity(
                violation("22001", "ERROR: value too long for type character varying(10)"), requete());

        assertEquals(HttpStatus.BAD_REQUEST, reponse.getStatusCode());
        assertNotNull(reponse.getBody());
        assertNull(reponse.getBody().erreurs());
    }

    @Test
    @DisplayName("Les autres SQLSTATE gardent leur mappage (23505 doublon -> 409, inconnu -> 409)")
    void autresSqlstate_inchanges() {
        // Non-régression : l'ajout du cas 22001 ne doit pas déplacer les branches existantes ni le repli.
        assertEquals(HttpStatus.CONFLICT,
                handler.handleDataIntegrity(violation("23505", "duplicate key"), requete()).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,
                handler.handleDataIntegrity(violation("23503", "foreign key"), requete()).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                handler.handleDataIntegrity(violation("23502", "null value"), requete()).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,
                handler.handleDataIntegrity(violation("40001", "serialization failure"), requete()).getStatusCode());
    }
}
