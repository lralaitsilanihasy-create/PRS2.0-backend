package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.config.HorlogeRecette;
import cnm.prs.dto.DelaiParametreDto;
import cnm.prs.entity.DelaiParametre;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DelaiParametreRepository;
import cnm.prs.security.CurrentUser;

/** ⚠️ R6a (manuel révisé, lot 2, §B1 ; V106) — les délais de publicité administrables : lecture, réglage par l'Administrateur. */
@Service
public class DelaiParametreService {

    static final Set<String> UNITES = Set.of(DelaiParametre.JOURS, DelaiParametre.JOURS_FRANCS, DelaiParametre.JOURS_OUVRABLES, DelaiParametre.MOIS);

    private final DelaiParametreRepository repository;

    public DelaiParametreService(DelaiParametreRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DelaiParametreDto> tableau() {
        return repository.findAllByOrderByCodeAsc().stream().map(DelaiParametreService::dto).toList();
    }

    /** Règle un délai (Administrateur) : un champ absent garde sa valeur ; 404 code inconnu ; 400 nominatif (valeur négative, unité). */
    @Transactional
    public DelaiParametreDto definir(String code, DelaiParametreDto.Request r) {
        DelaiParametre p = repository.findById(code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Délai paramétrable inconnu : " + code + "."));
        DelaiParametreDto.Request c = r == null ? new DelaiParametreDto.Request(null, null, null, null) : r;
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        if (c.valeur() != null && c.valeur() < 0) {
            erreurs.add(new ErrorResponse.FieldError("valeur", "Un délai est un nombre positif ou nul."));
        }
        String unite = c.unite() == null || c.unite().isBlank() ? null : c.unite().trim().toUpperCase(Locale.ROOT);
        if (unite != null && !UNITES.contains(unite)) {
            erreurs.add(new ErrorResponse.FieldError("unite", "L'unité est JOURS, JOURS_FRANCS, JOURS_OUVRABLES ou MOIS."));
        }
        if (c.texte() != null && c.texte().length() > 300) {
            erreurs.add(new ErrorResponse.FieldError("texte", "Le texte tient en 300 caractères."));
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        if (c.valeur() != null) {
            p.setValeur(c.valeur());
        }
        if (unite != null) {
            p.setUnite(unite);
        }
        if (c.texte() != null) {
            p.setTexte(c.texte().isBlank() ? null : c.texte().trim());
        }
        if (c.actif() != null) {
            p.setActif(c.actif());
        }
        p.setDateMaj(LocalDateTime.now(HorlogeRecette.HORLOGE));
        p.setImActeur(CurrentUser.ref().or(CurrentUser::login).orElse(null));
        return dto(repository.save(p));
    }

    static DelaiParametreDto dto(DelaiParametre p) {
        return new DelaiParametreDto(p.getCode(), p.getValeur(), p.getUnite(), p.getTexte(), p.isActif(), p.getDateMaj(), p.getImActeur());
    }
}
