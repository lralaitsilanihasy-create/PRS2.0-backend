package cnm.prs.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CreerUgpmRequest;
import cnm.prs.dto.UgpmDto;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.TypeActeur;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.repository.CompteAuthRepository;
import cnm.prs.repository.PrmpRepository;
import cnm.prs.repository.UgpmRepository;

/**
 * Gestion des UGPM (Administrateur) : création d'une UGPM rattachée à une PRMP de tutelle, avec son compte
 * d'authentification <strong>actif</strong> ({@code TYPE_ACTEUR=UGPM}, {@code REF_ACTEUR=ID_UGPM}).
 */
@Service
@Transactional
public class UgpmService {

    private final UgpmRepository ugpmRepository;
    private final PrmpRepository prmpRepository;
    private final CompteAuthRepository compteRepository;
    private final PasswordEncoder passwordEncoder;

    public UgpmService(UgpmRepository ugpmRepository, PrmpRepository prmpRepository,
            CompteAuthRepository compteRepository, PasswordEncoder passwordEncoder) {
        this.ugpmRepository = ugpmRepository;
        this.prmpRepository = prmpRepository;
        this.compteRepository = compteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UgpmDto creer(CreerUgpmRequest req) {
        if (!prmpRepository.existsById(req.idPrmpTutelle())) {
            throw new BusinessRuleException("PRMP de tutelle inconnue : " + req.idPrmpTutelle() + ".");
        }
        if (ugpmRepository.existsById(req.idUgpm())) {
            throw new BusinessRuleException("Une UGPM existe déjà avec l'identifiant : " + req.idUgpm() + ".");
        }
        if (compteRepository.findByLogin(req.login()).isPresent()) {
            throw new BusinessRuleException("Ce login est déjà utilisé.");
        }
        Ugpm ugpm = new Ugpm(req.idUgpm(), req.libelle(), req.idPrmpTutelle(), null);
        ugpmRepository.save(ugpm);
        // Compte actif immédiatement (créé par l'Administrateur), pas de workflow de validation.
        compteRepository.save(new CompteAuth(req.login(), passwordEncoder.encode(req.motDePasse()),
                TypeActeur.UGPM.name(), req.idUgpm(), true));
        return toDto(ugpm);
    }

    @Transactional(readOnly = true)
    public List<UgpmDto> findAll() {
        return ugpmRepository.findAll().stream().map(this::toDto).toList();
    }

    private UgpmDto toDto(Ugpm u) {
        return new UgpmDto(u.getIdUgpm(), u.getLibelle(), u.getIdPrmpTutelle());
    }
}
