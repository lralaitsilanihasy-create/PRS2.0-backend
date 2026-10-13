package cnm.prs.service;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import cnm.prs.dto.AttributionDto;
import cnm.prs.entity.Attribution;
import cnm.prs.entity.DelaiParametre;
import cnm.prs.repository.DelaiParametreRepository;

/**
 * ⚠️ R6c (manuel révisé pour l'application, lot 2, §B1 règle 6, QB1 ; V108, 2026-10-13) — <strong>le délai d'attente</strong> de
 * {@link AttributionService#JOURS_FRANCS dix jours francs} avant la signature (art. 52-I et 58 de la loi n° 2016-055, art. 78) part de la
 * <strong>réception de l'information par les candidats non retenus</strong>, et non plus de son envoi. Arbitrage au backend du 13/10 :
 * la réception est <strong>présumée pour tous</strong> à l'envoi + {@code n} jours — l'envoi des lettres est l'information elle-même
 * (courriel et mise à disposition sur la plateforme au même instant) — sans lire l'accusé de lecture de chacun ; {@code n} est le
 * paramètre administrable {@code RECEPTION_LETTRE} (3 jours proposés), inactif ou nul : l'envoi vaut réception. L'affichage au siège,
 * s'il est plus tardif, reste le point de départ.
 */
@Component
public class DelaiAttenteCalcul {

    public static final String RECEPTION_LETTRE = "RECEPTION_LETTRE";

    private final DelaiParametreRepository parametres;

    public DelaiAttenteCalcul(DelaiParametreRepository parametres) {
        this.parametres = parametres;
    }

    /** Les jours de réception présumée (paramètre {@code RECEPTION_LETTRE}, actif), sinon 0. */
    public int joursReception() {
        return parametres.findById(RECEPTION_LETTRE).filter(DelaiParametre::isActif).map(DelaiParametre::getValeur).filter(v -> v > 0).orElse(0);
    }

    /** Le délai d'attente du lot à une date donnée ; nul avant l'information. */
    public AttributionDto.DelaiAttente calculer(Attribution a, LocalDate aujourdhui) {
        if (a == null || a.getInformeLe() == null) {
            return null;
        }
        int n = joursReception();
        LocalDate reception = a.getInformeLe().toLocalDate().plusDays(n);
        LocalDate debut = reception;
        if (a.getDateAffichage() != null && a.getDateAffichage().isAfter(debut)) {
            debut = a.getDateAffichage();
        }
        // Jours francs : ni le jour du point de départ, ni celui de l'échéance ne comptent — la signature vient le lendemain du dixième.
        LocalDate fin = debut.plusDays(AttributionService.JOURS_FRANCS);
        LocalDate signable = fin.plusDays(1);
        return new AttributionDto.DelaiAttente(debut, fin, signable, AttributionService.JOURS_FRANCS, !aujourdhui.isBefore(signable), reception, n);
    }
}
