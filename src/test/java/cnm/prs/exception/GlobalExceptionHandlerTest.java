package cnm.prs.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    @Test
    @DisplayName("500 : le corps ne contient aucun detail d'implementation (SQL, chemin serveur, nom de classe)")
    void erreurInterne_neFuitAucunDetail() {
        // Le corps renvoyait ex.getMessage() brut. Une exception porteuse d'un fragment SQL ou d'un chemin de
        // fichier publiait donc la structure du serveur a qui savait provoquer l'erreur — sur une API dont
        // certaines routes sont publiques. Ce test verifie l'absence de fuite plutot que la presence d'un
        // texte : c'est la fuite qui est le defaut, et elle doit rester impossible quelle que soit la phrase.
        String interne = "insert into t_marche (MONT_ESTIM) values (?) [/srv/prs/app.war] "
                + "cnm.prs.repository.MarcheRepositoryImpl";

        ResponseEntity<ErrorResponse> reponse =
                handler.handleGeneric(new IllegalStateException(interne), requete());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, reponse.getStatusCode());
        assertNotNull(reponse.getBody());
        String corps = reponse.getBody().message();
        assertFalse(corps.contains("t_marche"), "le corps expose un nom de table");
        assertFalse(corps.contains("insert into"), "le corps expose un fragment SQL");
        assertFalse(corps.contains("/srv/prs"), "le corps expose un chemin du serveur");
        assertFalse(corps.contains("cnm.prs."), "le corps expose un nom de classe interne");
        assertEquals(GlobalExceptionHandler.MESSAGE_ERREUR_INTERNE, corps);
        assertNull(reponse.getBody().erreurs());
    }

    @Test
    @DisplayName("JpaSystemException hors « PK manquante » : 500 generique lui aussi, pas le message Hibernate")
    void jpaSystemException_repliGenerique() {
        // Le repli de handleJpaSystem renvoyait lui aussi ex.getMessage() : meme fuite, autre porte. Le cas
        // metier reconnu (PK assignee manquante) doit rester un 400 explicite — c'est la seule exception.
        ResponseEntity<ErrorResponse> fuite = handler.handleJpaSystem(
                new JpaSystemException(new RuntimeException("could not extract ResultSet from t_dossier")), requete());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, fuite.getStatusCode());
        assertNotNull(fuite.getBody());
        assertFalse(fuite.getBody().message().contains("t_dossier"), "le repli expose un nom de table");

        ResponseEntity<ErrorResponse> pkManquante = handler.handleJpaSystem(
                new JpaSystemException(new RuntimeException("ids for this class must be manually assigned")), requete());
        assertEquals(HttpStatus.BAD_REQUEST, pkManquante.getStatusCode());
    }
    @Test
    @DisplayName("405 : Allow peuple depuis les verbes supportes, omis quand le mapping n'en fournit aucun")
    void methodeNonSupportee_allowFacultatif() {
        // getSupportedHttpMethods() peut etre null ou vide : un Allow vide serait un en-tete mensonger.
        // Le 405 doit rester rendu dans tous les cas — c'est le statut qui porte l'information utile.
        ResponseEntity<ErrorResponse> avecVerbes = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST")), requete());
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, avecVerbes.getStatusCode());
        assertEquals(List.of(HttpMethod.GET, HttpMethod.POST), avecVerbes.getHeaders().getAllow().stream().toList());

        ResponseEntity<ErrorResponse> sansVerbe =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("TRACE"), requete());
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, sansVerbe.getStatusCode());
        assertFalse(sansVerbe.getHeaders().containsHeader("Allow"), "Allow ne doit pas etre rendu vide");
    }

    /**
     * Le libellé du type attendu est la seule chose que l'appelant puisse exploiter pour corriger : un 400
     * qui dirait seulement « mauvais type » ne vaudrait guère mieux que le 500 qu'il remplace. Deux points
     * fragiles sont figés ici parce qu'ils ne sont pas atteignables depuis une requête HTTP réelle :
     * l'énumération, dont les valeurs admises doivent être listées (comme le font déjà les 400 métier),
     * et le type {@code null} — Spring ne renseigne pas toujours {@code getRequiredType()}, et un repli
     * manquant transformerait ce gestionnaire en {@code NullPointerException}, donc en 500, c'est-à-dire
     * exactement le défaut qu'il corrige.
     */
    @Test
    @DisplayName("400 type incompatible : les valeurs admises d'une enum sont listees, type null tolere")
    void typeIncompatible_libelleUtileEtTypeNullTolere() {
        ResponseEntity<ErrorResponse> enumeration = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("bleu", Couleur.class, "teinte", null, null), requete());
        assertEquals(HttpStatus.BAD_REQUEST, enumeration.getStatusCode());
        assertNotNull(enumeration.getBody());
        assertNotNull(enumeration.getBody().erreurs());
        assertEquals("teinte", enumeration.getBody().erreurs().get(0).champ());
        assertTrue(enumeration.getBody().erreurs().get(0).message().contains("ROUGE"),
                "les valeurs admises doivent figurer dans le message");

        ResponseEntity<ErrorResponse> sansType = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("x", null, "inconnu", null, null), requete());
        assertEquals(HttpStatus.BAD_REQUEST, sansType.getStatusCode());
        assertNotNull(sansType.getBody());
        assertEquals("inconnu", sansType.getBody().erreurs().get(0).champ());
    }

    /** Énumération de test — le projet n'expose aucun paramètre de requête typé enum aujourd'hui. */
    private enum Couleur {
        ROUGE, VERT
    }

}
