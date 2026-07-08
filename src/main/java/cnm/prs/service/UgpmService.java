package cnm.prs.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.CreerUgpmRequest;
import cnm.prs.dto.ModifierUgpmRequest;
import cnm.prs.dto.UgpmDto;
import cnm.prs.entity.CompteAuth;
import cnm.prs.entity.Ugpm;
import cnm.prs.enums.TypeActeur;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
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
        Ugpm ugpm = new Ugpm();
        ugpm.setIdUgpm(req.idUgpm());
        ugpm.setLibelle(req.libelle());
        ugpm.setIdPrmpTutelle(req.idPrmpTutelle());
        ugpm.setNomUgpm(req.nomUgpm());
        ugpm.setPrenomsUgpm(req.prenomsUgpm());
        ugpm.setCin(req.cin());
        ugpm.setDateCin(req.dateCin());
        ugpm.setLieuCin(req.lieuCin());
        ugpm.setEmailUgpm(req.emailUgpm());
        ugpm.setTelUgpm(req.telUgpm());
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

    @Transactional(readOnly = true)
    public UgpmDto findById(String idUgpm) {
        return ugpmRepository.findById(idUgpm).map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("UGPM introuvable : " + idUgpm + "."));
    }

    /**
     * Modifie les champs métier d'une UGPM (identité, libellé, PRMP de tutelle). L'identifiant (matricule)
     * et le compte d'authentification ne sont pas touchés. La nouvelle PRMP de tutelle doit exister.
     */
    public UgpmDto modifier(String idUgpm, ModifierUgpmRequest req) {
        Ugpm ugpm = ugpmRepository.findById(idUgpm)
                .orElseThrow(() -> new ResourceNotFoundException("UGPM introuvable : " + idUgpm + "."));
        if (!prmpRepository.existsById(req.idPrmpTutelle())) {
            throw new BusinessRuleException("PRMP de tutelle inconnue : " + req.idPrmpTutelle() + ".");
        }
        ugpm.setLibelle(req.libelle());
        ugpm.setIdPrmpTutelle(req.idPrmpTutelle());
        ugpm.setNomUgpm(req.nomUgpm());
        ugpm.setPrenomsUgpm(req.prenomsUgpm());
        ugpm.setCin(req.cin());
        ugpm.setDateCin(req.dateCin());
        ugpm.setLieuCin(req.lieuCin());
        ugpm.setEmailUgpm(req.emailUgpm());
        ugpm.setTelUgpm(req.telUgpm());
        return toDto(ugpmRepository.save(ugpm));
    }

    /**
     * Supprime une UGPM et son compte d'authentification (créés ensemble à {@link #creer}). Les dossiers
     * qu'elle a créés restent la propriété de la PRMP de tutelle (leur {@code CREE_PAR} est une simple trace,
     * pas une FK vers t_ugpm).
     */
    public void delete(String idUgpm) {
        if (!ugpmRepository.existsById(idUgpm)) {
            throw new ResourceNotFoundException("UGPM introuvable : " + idUgpm + ".");
        }
        compteRepository.deleteAll(
                compteRepository.findByRefActeurAndTypeActeur(idUgpm, TypeActeur.UGPM.name()));
        ugpmRepository.deleteById(idUgpm);
    }

    private UgpmDto toDto(Ugpm u) {
        // Login du compte associé (REF_ACTEUR = idUgpm) — lecture seule, pour la réinitialisation du mot de passe.
        String login = compteRepository.findByRefActeurAndTypeActeur(u.getIdUgpm(), TypeActeur.UGPM.name())
                .stream().map(CompteAuth::getLogin).findFirst().orElse(null);
        return new UgpmDto(u.getIdUgpm(), u.getLibelle(), u.getIdPrmpTutelle(), u.getNomUgpm(),
                u.getPrenomsUgpm(), u.getCin(), u.getDateCin(), u.getLieuCin(),
                u.getEmailUgpm(), u.getTelUgpm(), login);
    }
}
