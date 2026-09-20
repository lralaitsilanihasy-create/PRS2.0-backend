package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import cnm.prs.entity.SeuilMarche;
import cnm.prs.enums.BaremeSeuil;
import cnm.prs.enums.CategorieSeuil;
import cnm.prs.enums.ProcedureAttendue;
import cnm.prs.enums.TypeSeuil;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3) — <strong>photographie du référentiel de
 * seuils</strong> à une date, prise une fois et interrogée en mémoire.
 *
 * <p>Pourquoi une photographie : un PPM porte des dizaines à des centaines de lignes, et chaque ligne
 * interroge plusieurs seuils (procédure, contrôle a priori, publicité). Les relire ligne par ligne
 * ferait des centaines d'allers-retours en base pour une trentaine de valeurs qui ne changent pas
 * pendant l'exécution. Le pré-contrôle charge donc le barème, puis raisonne ici.</p>
 *
 * <p>Cette classe <strong>ne décide rien</strong> : elle lit le référentiel et dit ce que les textes
 * appellent. Le mode de passation reste saisi par la PRMP ; c'est une règle, ailleurs, qui comparera et
 * signalera — en citant le seuil et sa base légale.</p>
 *
 * <p><strong>Un barème vide ne se devine pas.</strong> Si le référentiel ne porte aucune valeur pour une
 * case (catégorie × barème × type), les lectures rendent {@link Optional#empty()} et l'appelant
 * s'abstient de signaler. Aucun seuil de repli n'est écrit dans le code : mieux vaut ne rien dire que
 * d'opposer à une PRMP un montant qui ne vient d'aucun texte.</p>
 */
public final class SeuilsEnVigueur {

    /** Une case du barème : le triplet qui identifie une valeur. */
    private record Cle(TypeSeuil type, CategorieSeuil categorie, BaremeSeuil bareme) {
    }

    private final LocalDate date;
    private final Map<Cle, SeuilMarche> parCle;

    /**
     * Retient, pour chaque case, la valeur dont la date d'effet est la plus récente parmi celles en
     * vigueur à {@code date} — deux valeurs concurrentes sur une même case ne peuvent normalement pas
     * coexister (contrainte d'unicité sur la date d'effet, et la précédente est bornée), mais si
     * l'administration en laissait deux, c'est la plus récente qui vaut.
     */
    SeuilsEnVigueur(LocalDate date, Collection<SeuilMarche> valeurs) {
        this.date = date;
        Map<Cle, SeuilMarche> index = new HashMap<>();
        for (SeuilMarche s : valeurs) {
            if (!s.enVigueurLe(date)) {
                continue;
            }
            Cle cle = new Cle(s.getTypeSeuil(), s.getCategorieSeuil(), s.getBareme());
            SeuilMarche dejaLa = index.get(cle);
            if (dejaLa == null || dejaLa.getDateEffet().isBefore(s.getDateEffet())) {
                index.put(cle, s);
            }
        }
        this.parCle = Map.copyOf(index);
    }

    /** Date à laquelle le barème a été photographié. */
    public LocalDate date() {
        return date;
    }

    /** Vrai si le référentiel ne porte aucune valeur à cette date : il n'y a alors rien à signaler. */
    public boolean vide() {
        return parCle.isEmpty();
    }

    /** La valeur d'une case, si le référentiel la porte. */
    public Optional<SeuilMarche> seuil(TypeSeuil type, CategorieSeuil categorie, BaremeSeuil bareme) {
        if (type == null || categorie == null || bareme == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(parCle.get(new Cle(type, categorie, bareme)));
    }

    /**
     * Procédure que les seuils appellent pour ce montant hors taxes, et le seuil qui la fonde.
     *
     * <p>Rend {@link Optional#empty()} quand la question n'a pas de réponse dans les textes : montant
     * inconnu, ou catégorie sans seuil de procédure — c'est le cas des <strong>prestations
     * intellectuelles</strong>, pour lesquelles l'arrêté ne fixe que le seuil de contrôle. Leur publicité
     * se lit par {@link #publiciteManifestationInteret}.</p>
     */
    public Optional<VerdictProcedure> procedureAttendue(CategorieSeuil categorie, BaremeSeuil bareme,
            BigDecimal montantHt) {
        if (montantHt == null) {
            return Optional.empty();
        }
        Optional<SeuilMarche> appelOffres = seuil(TypeSeuil.APPEL_OFFRES_OUVERT, categorie, bareme);
        Optional<SeuilMarche> consultation = seuil(TypeSeuil.CONSULTATION, categorie, bareme);
        if (appelOffres.isEmpty() && consultation.isEmpty()) {
            return Optional.empty();
        }
        if (appelOffres.filter(s -> s.atteintPar(montantHt)).isPresent()) {
            return Optional.of(new VerdictProcedure(ProcedureAttendue.APPEL_OFFRES_OUVERT, appelOffres.get()));
        }
        if (consultation.filter(s -> s.atteintPar(montantHt)).isPresent()) {
            return Optional.of(new VerdictProcedure(ProcedureAttendue.CONSULTATION, consultation.get()));
        }
        // Sous le seuil de consultation : bon de commande. Le seuil cité est celui qui n'est pas atteint,
        // car c'est lui qui explique le verdict ; il manque si le référentiel ne porte pas la case.
        return Optional.of(new VerdictProcedure(ProcedureAttendue.ACHAT_DIRECT, consultation.orElse(null)));
    }

    /** Seuil de contrôle a priori de cette case, si le référentiel le porte. */
    public Optional<SeuilMarche> seuilControleAPriori(CategorieSeuil categorie, BaremeSeuil bareme) {
        return seuil(TypeSeuil.CONTROLE_A_PRIORI, categorie, bareme);
    }

    /**
     * Le montant atteint-il le seuil de contrôle a priori ? {@code false} quand le référentiel ne porte
     * pas la case — on n'affirme pas qu'un marché échappe au contrôle sur un barème qu'on n'a pas.
     */
    public boolean soumisAuControleAPriori(CategorieSeuil categorie, BaremeSeuil bareme, BigDecimal montantHt) {
        return seuilControleAPriori(categorie, bareme).filter(s -> s.atteintPar(montantHt)).isPresent();
    }

    /**
     * Forme de publicité de l'appel à manifestation d'intérêt d'une prestation intellectuelle pour ce
     * montant : voie de presse au-delà du seuil, affichage en dessous. Le seuil rendu porte son
     * {@code DELAI_MIN_JOURS} — c'est de là que vient le délai minimal, jamais du code.
     */
    public Optional<SeuilMarche> publiciteManifestationInteret(BaremeSeuil bareme, BigDecimal montantHt) {
        if (montantHt == null) {
            return Optional.empty();
        }
        Optional<SeuilMarche> presse = seuil(TypeSeuil.MANIFESTATION_INTERET_PRESSE,
                CategorieSeuil.PRESTATIONS_INTELLECTUELLES, bareme);
        if (presse.filter(s -> s.atteintPar(montantHt)).isPresent()) {
            return presse;
        }
        return seuil(TypeSeuil.MANIFESTATION_INTERET_AFFICHAGE,
                CategorieSeuil.PRESTATIONS_INTELLECTUELLES, bareme)
                .filter(s -> s.atteintPar(montantHt));
    }

    /**
     * Ce que les seuils appellent, et le seuil qui le fonde — celui qui est atteint, ou celui qui ne
     * l'est pas quand le verdict est l'achat direct. {@code seuilCite} peut être {@code null} : le
     * référentiel ne porte pas toujours toutes les cases.
     */
    public record VerdictProcedure(ProcedureAttendue procedure, SeuilMarche seuilCite) {
    }
}
