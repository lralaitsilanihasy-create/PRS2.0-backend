package cnm.prs.dto;

import java.time.LocalDateTime;

/**
 * ⚠️ V136 (demande front du 2026-10-19, « copie-offre-candidat », §B1) — la copie chiffrée de son offre, telle que le candidat l'a
 * déposée : l'enveloppe ({@code chiffre}, {@code iv}, {@code sel} en base64 ; {@code iterations}, {@code kdf}, {@code algorithme}),
 * la {@code version} du récapitulatif et la date d'enregistrement. Le serveur ne la lit pas.
 */
public record CopieOffreDto(String chiffre, String iv, String sel, Integer iterations, String kdf, String algorithme, Integer version,
        LocalDateTime enregistreeLe) {

    /** Le corps de {@code PUT /api/candidat/offres/{idOffre}/copie}. */
    public record Corps(String chiffre, String iv, String sel, Integer iterations, String kdf, String algorithme, Integer version) {

        public CeremonieDto.Enveloppe enveloppe() {
            return new CeremonieDto.Enveloppe(chiffre, iv, sel, iterations, kdf, algorithme);
        }
    }
}
