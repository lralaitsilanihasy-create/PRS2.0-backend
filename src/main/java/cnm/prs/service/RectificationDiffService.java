package cnm.prs.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.DiffDossierDto;
import cnm.prs.entity.Dossier;
import cnm.prs.entity.Marche;
import cnm.prs.entity.SnapshotRectifLigne;
import cnm.prs.entity.Verification;
import cnm.prs.entity.VersionDossier;
import cnm.prs.enums.TypeChangementLigne;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ResourceNotFoundException;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.MarcheRepository;
import cnm.prs.repository.VerificationRepository;

/**
 * ⚠️ Règle ajoutée (2026-08-15, visibilité des rectifications) — rend les changements d'une
 * <strong>rectification</strong> visibles au circuit (vérificateur en tête) : la rectification modifie
 * la version courante <em>en place</em> (structure figée, mise à jour par {@code idDetail}), donc l'état
 * <strong>pré-correction</strong> des lignes est figé au <strong>premier</strong>
 * {@code PUT /api/saisies/ppm/{id}} de chaque cycle, et le diff du <strong>dernier cycle</strong> est
 * servi via {@code GET /api/dossiers/{id}/diff-rectification} ({@link #diffRectification}) dans le
 * <strong>même DTO</strong> que le diff des mises à jour ({@link DiffDossierDto}) — le front réutilise
 * tel quel son tableau (surlignage MODIFIEE + légende).
 *
 * <p>⚠️ <strong>Versions archivées (2026-09-06, demande pilote)</strong> — le gel n'est plus ici : il est
 * devenu l'<em>archivage</em> d'une version ({@link VersionDossierService#archiverAvantPremiereCorrection}),
 * et plus aucune série n'est effacée. Le contrat de ce diff est <strong>inchangé</strong> : il compare
 * toujours la <strong>dernière</strong> version archivée par rectification (l'état d'AVANT la première
 * correction du dernier cycle) aux lignes courantes — le vérificateur juge le dernier état.</p>
 *
 * <p>Un <strong>cycle</strong> = de la transmission des observations ({@code EN_ATTENTE_DECISION_PRMP})
 * à la resoumission. Appariement direct par {@code idDetail} : lignes {@code INCHANGEE} / {@code MODIFIEE},
 * et — depuis la règle pilote du 2026-09-06 (écart de structure toléré, ≤ 3 par sens) — {@code NOUVELLE}
 * pour une ligne ajoutée par la rectification, {@code SUPPRIMEE} pour une ligne retirée (rendue avec
 * {@code idDetail} nul, son libellé archivé et son {@code idLigneOrigine}). Ce sont les types du diff des
 * mises à jour : le front les connaît déjà.</p>
 *
 * <p>⚠️ Les empreintes de collections et la normalisation des valeurs ({@link EmpreintesLigne})
 * reprennent la même sémantique que {@code MiseAJourPpmService} (champs {@code CHAMPS_COMPARES}) — à
 * garder synchrones.</p>
 */
@Service
@Transactional
public class RectificationDiffService {

    private final MarcheRepository marcheRepository;
    private final DossierRepository dossierRepository;
    private final VerificationRepository verificationRepository;
    private final VersionDossierService versions;
    private final EmpreintesLigne empreintes;

    public RectificationDiffService(MarcheRepository marcheRepository, DossierRepository dossierRepository,
            VerificationRepository verificationRepository, VersionDossierService versions,
            EmpreintesLigne empreintes) {
        this.marcheRepository = marcheRepository;
        this.dossierRepository = dossierRepository;
        this.verificationRepository = verificationRepository;
        this.versions = versions;
        this.empreintes = empreintes;
    }

    // ------------------------------------------------------------------
    // Diff du dernier cycle (lecture circuit)
    // ------------------------------------------------------------------

    /**
     * Diff du dernier cycle de rectification : dernière version archivée vs lignes courantes, dans le
     * même {@link DiffDossierDto} que le diff des versions ({@code idDossierPrecedent}/{@code numMaj}
     * nuls — ce n'est pas une comparaison de versions ; {@code motifMaj} = motif de la resoumission qui
     * a clos le cycle ; {@code fige} = cycle clos par une resoumission). 409 si aucune rectification
     * n'a été enregistrée. Lecture : tout-voyant, PRMP propriétaire, ou contrôleur de la localité du
     * dossier (vérificateur titulaire ou délégué compris — même périmètre que la consultation).
     */
    @Transactional(readOnly = true)
    public DiffDossierDto diffRectification(Integer idDossier) {
        Dossier dossier = dossierRepository.findById(idDossier)
                .orElseThrow(() -> new ResourceNotFoundException("Dossier introuvable : " + idDossier));
        versions.controlerAccesLecture(dossier);
        VersionDossier derniere = versions.derniereVersionRectification(idDossier)
                .orElseThrow(() -> new BusinessRuleException("Aucune rectification enregistrée pour ce dossier : "
                        + "aucun instantané pré-correction (la PRMP n'a pas encore corrigé)."));
        List<SnapshotRectifLigne> avant = versions.lignes(derniere);
        int cycle = derniere.getCycle() == null ? 1 : derniere.getCycle();
        boolean fige = versions.nbResoumissions(idDossier) >= cycle;
        String motif = fige ? verificationRepository.findPassagesDuDossier(idDossier).stream()
                .findFirst().map(Verification::getMotifRectif).orElse(null) : null;

        Map<Integer, Marche> courantes = marcheRepository.findByIdDossier(idDossier).stream()
                .collect(Collectors.toMap(Marche::getIdDetail, m -> m, (a, b) -> a, LinkedHashMap::new));
        List<DiffDossierDto.LigneDiff> lignes = new ArrayList<>();
        for (SnapshotRectifLigne s : avant) {
            Marche m = courantes.remove(s.getIdDetail());
            if (m == null) {
                // Ligne RETIRÉE par la rectification (règle pilote 2026-09-06, ≤ 3 par sens) : elle n'existe
                // plus dans le plan courant — idDetail nul, libellé et identité repris de la version archivée.
                lignes.add(new DiffDossierDto.LigneDiff(null, s.getIdLigneOrigine(), s.getDesignationMarche(),
                        TypeChangementLigne.SUPPRIMEE.name(), "ORIGINE", List.of()));
                continue;
            }
            List<DiffDossierDto.ChampDiff> ecarts = comparer(s, m);
            lignes.add(new DiffDossierDto.LigneDiff(m.getIdDetail(), m.getIdLigneOrigine(),
                    m.getDesignationMarche(),
                    (ecarts.isEmpty() ? TypeChangementLigne.INCHANGEE : TypeChangementLigne.MODIFIEE).name(),
                    "ORIGINE", ecarts));
        }
        // Lignes AJOUTÉES par la rectification (règle pilote 2026-09-06) : absentes de la version archivée.
        for (Marche m : courantes.values()) {
            lignes.add(new DiffDossierDto.LigneDiff(m.getIdDetail(), m.getIdLigneOrigine(),
                    m.getDesignationMarche(), TypeChangementLigne.NOUVELLE.name(), "ORIGINE", List.of()));
        }
        return new DiffDossierDto(idDossier, null, null, motif, fige, recap(lignes), lignes);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Mêmes champs comparés que le diff des versions ({@code MiseAJourPpmService.CHAMPS_COMPARES}). */
    private List<DiffDossierDto.ChampDiff> comparer(SnapshotRectifLigne avant, Marche apres) {
        List<DiffDossierDto.ChampDiff> ecarts = new ArrayList<>();
        ajouterEcart(ecarts, "designationMarche", EmpreintesLigne.texte(avant.getDesignationMarche()),
                EmpreintesLigne.texte(apres.getDesignationMarche()));
        ajouterEcart(ecarts, "montEstim", EmpreintesLigne.montant(avant.getMontEstim()),
                EmpreintesLigne.montant(apres.getMontEstim()));
        ajouterEcart(ecarts, "nouvMontEstim", EmpreintesLigne.montant(avant.getNouvMontEstim()),
                EmpreintesLigne.montant(apres.getNouvMontEstim()));
        ajouterEcart(ecarts, "numCompte", EmpreintesLigne.texte(avant.getNumCompte()),
                EmpreintesLigne.texte(apres.getNumCompte()));
        ajouterEcart(ecarts, "financement", EmpreintesLigne.texte(avant.getFinancement()),
                EmpreintesLigne.texte(apres.getFinancement()));
        ajouterEcart(ecarts, "statut", EmpreintesLigne.texte(avant.getStatut()),
                EmpreintesLigne.texte(apres.getStatut()));
        ajouterEcart(ecarts, "idNature", EmpreintesLigne.nombre(avant.getIdNature()),
                EmpreintesLigne.nombre(apres.getIdNature()));
        ajouterEcart(ecarts, "idMode", EmpreintesLigne.nombre(avant.getIdMode()),
                EmpreintesLigne.nombre(apres.getIdMode()));
        ajouterEcart(ecarts, "formeMarche", avant.getFormeMarche(),
                apres.getFormeMarche() == null ? null : apres.getFormeMarche().name());
        ajouterEcart(ecarts, "beneficiaires", avant.getEmpBeneficiaires(), empreintes.beneficiaires(apres.getIdDetail()));
        ajouterEcart(ecarts, "lots", avant.getEmpLots(), empreintes.lots(apres.getIdDetail()));
        ajouterEcart(ecarts, "processus", avant.getEmpProcessus(), empreintes.previsions(apres.getIdDetail()));
        return ecarts;
    }

    private void ajouterEcart(List<DiffDossierDto.ChampDiff> ecarts, String champ, String a, String b) {
        if (!Objects.equals(a, b)) {
            ecarts.add(new DiffDossierDto.ChampDiff(champ, a, b));
        }
    }

    private DiffDossierDto.RecapDiff recap(List<DiffDossierDto.LigneDiff> lignes) {
        Map<String, Integer> n = new LinkedHashMap<>();
        for (DiffDossierDto.LigneDiff l : lignes) {
            n.merge(l.type(), 1, Integer::sum);
        }
        return new DiffDossierDto.RecapDiff(
                n.getOrDefault(TypeChangementLigne.INCHANGEE.name(), 0),
                n.getOrDefault(TypeChangementLigne.MODIFIEE.name(), 0),
                n.getOrDefault(TypeChangementLigne.NOUVELLE.name(), 0),
                n.getOrDefault(TypeChangementLigne.SUPPRIMEE.name(), 0),
                n.getOrDefault(TypeChangementLigne.RESTAUREE.name(), 0),
                lignes.size());
    }
}
