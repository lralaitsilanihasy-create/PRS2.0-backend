package cnm.prs.dto;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ⚠️ Audit 2026-08-27 (lot E) — politique de mot de passe. Jusqu'ici, la seule contrainte était
 * {@code @Size(min = 8, max = 72)} : « 12345678 » ou « aaaaaaaa » passaient, sur des comptes qui
 * donnent accès au circuit de contrôle des marchés publics.
 *
 * <p><b>Règle retenue</b> — volontairement <em>modérée</em> : au moins 8 caractères, dont au moins
 * une lettre et un chiffre. Pas de majuscule ni de caractère spécial imposés : ces exigences
 * poussent les utilisateurs vers les substitutions prévisibles (« Motdepasse1! ») sans gain réel,
 * et le NIST les déconseille explicitement depuis 2017. La borne haute de 72 est celle de
 * <strong>BCrypt</strong>, qui ignore silencieusement les octets au-delà — mieux vaut refuser que
 * tronquer sans le dire.</p>
 *
 * <p><b>Où l'appliquer</b> : uniquement sur les champs « <strong>nouveau</strong> mot de passe »
 * (changement, réinitialisation par l'Administrateur, inscriptions). Jamais sur un mot de passe
 * <em>présenté</em> — l'ancien d'un changement, celui d'une connexion — sous peine de rendre
 * inutilisables les comptes créés avant cette règle.</p>
 *
 * <p><b>Composition</b> sans {@code @ReportAsSingleViolation} : longueur et complexité remontent
 * chacune leur propre message, l'utilisateur sait donc laquelle des deux il a manquée.</p>
 */
@Documented
@Constraint(validatedBy = {})
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
@Size(min = 8, max = 72, message = "Le mot de passe doit comporter entre 8 et 72 caractères.")
@Pattern(regexp = "^(?=.*\\p{L})(?=.*\\p{N}).*$",
        message = "Le mot de passe doit contenir au moins une lettre et un chiffre.")
public @interface MotDePasseValide {

    String message() default "Mot de passe trop simple.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
