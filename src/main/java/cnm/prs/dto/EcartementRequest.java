package cnm.prs.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, étape 3) — corps d'un
 * <strong>écartement</strong> de signalement ({@code POST /api/pre-controle/signalements/{id}/ecarter}).
 *
 * <p>L'identité de l'auteur vient du jeton, jamais du corps.</p>
 *
 * @param motif            <strong>obligatoire</strong>, et d'une longueur minimale : « un écartement sans
 *                         motif, ou motivé "RAS", ne dit rien au contrôleur » (plan, §4, lot 3, 3.f,
 *                         condition 2). C'est ce motif que lira l'autre côté du circuit.
 * @param avertissementLu  l'auteur a <strong>vu</strong> que son écartement et son motif seront lus de
 *                         l'autre côté. C'est la première des quatre conditions de la dissuasion : « sans
 *                         cela il n'y a pas de dissuasion — seulement un piège, contestable et déloyal ».
 *                         Le serveur l'exige pour que la promesse ne dépende pas du seul écran.
 */
public record EcartementRequest(
        @NotBlank(message = "Le motif de l'écartement est obligatoire.")
        @Size(min = EcartementRequest.MOTIF_MIN, max = 2000,
                message = "Le motif doit faire au moins " + EcartementRequest.MOTIF_MIN
                        + " caractères : il sera lu de l'autre côté du circuit, « RAS » n'y dit rien.")
        String motif,

        // ⚠️ @NotNull en plus de @AssertTrue : Bean Validation considère qu'un booléen ABSENT satisfait
        // @AssertTrue (null = pas d'avis). Sans cette seconde annotation, omettre le champ suffisait à
        // écarter sans avertissement — c'est-à-dire à contourner la condition même de la dissuasion.
        @NotNull(message = EcartementRequest.AVERTISSEMENT)
        @AssertTrue(message = EcartementRequest.AVERTISSEMENT)
        Boolean avertissementLu) {

    /** Longueur minimale d'un motif recevable. */
    public static final int MOTIF_MIN = 20;

    /**
     * La phrase que l'écran doit avoir montrée, et que le serveur renvoie en clair quand l'appelant ne
     * confirme pas l'avoir lue. Elle est ici, dans le contrat, pour que le front et le serveur disent
     * exactement la même chose.
     */
    public static final String AVERTISSEMENT =
            "Cet écartement et votre motif seront visibles de l'autre côté du circuit : du contrôleur de "
                    + "la CNM si vous êtes la PRMP, de votre hiérarchie si vous êtes contrôleur. "
                    + "L'écran doit vous l'avoir dit avant que vous n'écartiez.";
}
