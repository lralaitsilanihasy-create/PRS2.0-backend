package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.entity.SeuilMarche;
import cnm.prs.enums.BaremeSeuil;
import cnm.prs.enums.CategorieSeuil;
import cnm.prs.enums.TypeSeuil;
import cnm.prs.exception.BadRequestException;
import cnm.prs.repository.SeuilMarcheRepository;
import cnm.prs.security.CurrentUser;

/**
 * ⚠️ Pré-contrôle du PPM (2026-09-20, assistant IA lot 3, migration V32) — le <strong>référentiel de
 * seuils</strong> : lecture pour les règles, saisie pour l'administration.
 *
 * <p>Les valeurs de l'arrêté n° 13 156/2019-MEF sont semées par V32 ; aucun montant n'est écrit dans le
 * code. Ce service ne détermine ni ne bloque rien — il sert le barème à qui doit
 * <strong>signaler</strong>.</p>
 */
@Service
@Transactional
public class SeuilMarcheService {

    private final SeuilMarcheRepository repository;

    public SeuilMarcheService(SeuilMarcheRepository repository) {
        this.repository = repository;
    }

    /**
     * Photographie du barème en vigueur à une date — à prendre <strong>une fois</strong> par exécution du
     * pré-contrôle, puis à interroger en mémoire.
     *
     * <p>La date est celle du plan examiné, pas celle du jour : le pré-contrôle d'un plan d'un exercice
     * passé doit se lire avec le barème de son temps.</p>
     */
    @Transactional(readOnly = true)
    public SeuilsEnVigueur chargerEnVigueurLe(LocalDate date) {
        LocalDate effective = date == null ? LocalDate.now() : date;
        return new SeuilsEnVigueur(effective, repository.findEnVigueurLe(effective));
    }

    /**
     * Barème applicable à une entité, déduit de son organisme de contrôle : CNM → central, CRM →
     * déconcentré (arbitrage du pilote du 2026-09-18). Aucun champ n'a été créé pour cela — la
     * « localité » de PRS <strong>est</strong> l'organisme de contrôle.
     */
    public BaremeSeuil baremeDe(String idLocalite) {
        return BaremeSeuil.pourLocalite(idLocalite);
    }

    /** L'histoire d'un barème, la valeur la plus récente d'abord (écran d'administration). */
    @Transactional(readOnly = true)
    public List<SeuilMarche> historique(TypeSeuil type, BaremeSeuil bareme) {
        return repository.findByTypeSeuilAndBaremeOrderByDateEffetDesc(type, bareme);
    }

    /** Les valeurs d'un barème en vigueur à une date (écran d'administration). */
    @Transactional(readOnly = true)
    public List<SeuilMarche> enVigueur(TypeSeuil type, BaremeSeuil bareme, LocalDate date) {
        return repository.findEnVigueurPourBareme(type, bareme, date == null ? LocalDate.now() : date);
    }

    /**
     * Pose une <strong>nouvelle valeur</strong> sur une case du barème, à partir d'une date d'effet.
     *
     * <p>Une valeur ne se corrige pas en place : la précédente est <strong>bornée</strong> à la date
     * d'effet de la nouvelle (exclue), et reste lisible. C'est ce qui permet au pré-contrôle d'un plan
     * ancien de rester juste après un changement de texte — et à un contrôleur de comprendre, un an plus
     * tard, pourquoi un signalement citait tel montant.</p>
     *
     * <p>Refuse une date d'effet déjà prise sur la même case (l'unicité est aussi en base) : deux valeurs
     * qui commencent le même jour ne se départagent pas.</p>
     */
    public SeuilMarche fixer(TypeSeuil type, CategorieSeuil categorie, BaremeSeuil bareme,
            BigDecimal montant, Integer delaiMinJours, LocalDate dateEffet, String baseLegale) {
        if (type == null || categorie == null || bareme == null) {
            throw new BadRequestException("Le type, la catégorie et le barème du seuil sont obligatoires.");
        }
        if (montant == null || montant.signum() < 0) {
            throw new BadRequestException("Le montant du seuil doit être un montant hors taxes positif ou nul.");
        }
        if (dateEffet == null) {
            throw new BadRequestException("La date d'effet du seuil est obligatoire : un seuil sans date "
                    + "d'effet rendrait illisible le pré-contrôle des plans antérieurs.");
        }
        List<SeuilMarche> histoire = repository.findByTypeSeuilAndBaremeOrderByDateEffetDesc(type, bareme);
        for (SeuilMarche s : histoire) {
            if (s.getCategorieSeuil() != categorie) {
                continue;
            }
            if (dateEffet.equals(s.getDateEffet())) {
                throw new BadRequestException("Un seuil de cette case porte déjà la date d'effet du "
                        + dateEffet + " : modifiez-le, ou choisissez une autre date d'effet.");
            }
            // Borner la valeur qui précède : elle vaut jusqu'à la veille de la nouvelle.
            if (s.getDateEffet().isBefore(dateEffet) && (s.getDateFin() == null || s.getDateFin().isAfter(dateEffet))) {
                s.setDateFin(dateEffet);
                repository.save(s);
            }
        }
        SeuilMarche neuf = new SeuilMarche();
        neuf.setTypeSeuil(type);
        neuf.setCategorieSeuil(categorie);
        neuf.setBareme(bareme);
        neuf.setMontant(montant);
        neuf.setDelaiMinJours(delaiMinJours);
        neuf.setDateEffet(dateEffet);
        neuf.setBaseLegale(baseLegale);
        neuf.setDateMaj(LocalDateTime.now());
        neuf.setImActeur(acteurCourant());
        return repository.save(neuf);
    }

    /**
     * Référence de l'acteur qui saisit, dans les 10 caractères de la colonne : l'{@code IM_CONTROLEUR} (7)
     * ou l'{@code ID_PRMP} (10) du jeton, à défaut le login <strong>tronqué</strong> — un login fait
     * jusqu'à 100 caractères et ferait échouer l'écriture en 22001 (c'est le défaut C3 de l'audit du
     * 2026-09-14, qu'on ne réintroduit pas ici pour une colonne de traçabilité).
     */
    private static String acteurCourant() {
        return CurrentUser.ref()
                .or(CurrentUser::login)
                .map(r -> r.length() <= 10 ? r : r.substring(0, 10))
                .orElse(null);
    }
}
