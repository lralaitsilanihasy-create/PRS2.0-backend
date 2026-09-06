package cnm.prs.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import cnm.prs.repository.LotRepository;
import cnm.prs.repository.MarchePrevisionRepository;
import cnm.prs.repository.ServiceBeneficiaireRepository;

/**
 * Empreintes normalisées des <strong>collections</strong> d'une ligne de marché (bénéficiaires, lots,
 * dates prévisionnelles) et normalisation des valeurs scalaires comparées — la « signature » d'une
 * ligne à un instant donné.
 *
 * <p>Extrait de {@code RectificationDiffService} (2026-09-06) quand l'archivage des versions a été
 * séparé du diff : les deux en ont besoin — l'archivage fige l'empreinte de chaque collection, le diff
 * la compare à l'empreinte de l'état courant. Une empreinte figée n'a de sens que si elle est calculée
 * <em>exactement</em> comme celle d'aujourd'hui, d'où un seul lieu.</p>
 *
 * <p>⚠️ Même sémantique que les empreintes de {@code MiseAJourPpmService} (diff des versions de mise à
 * jour) — à garder synchrones. Formats : bénéficiaires {@code soa:montant} (montant = nouveau s'il
 * existe, sinon ancien), lots {@code désignation normalisée:montant:quantité}, processus
 * {@code idCapm:dateDebut:dateFin} ; éléments triés, joints par des virgules.</p>
 */
@Component
public class EmpreintesLigne {

    private final ServiceBeneficiaireRepository serviceBeneficiaireRepository;
    private final LotRepository lotRepository;
    private final MarchePrevisionRepository marchePrevisionRepository;

    public EmpreintesLigne(ServiceBeneficiaireRepository serviceBeneficiaireRepository, LotRepository lotRepository,
            MarchePrevisionRepository marchePrevisionRepository) {
        this.serviceBeneficiaireRepository = serviceBeneficiaireRepository;
        this.lotRepository = lotRepository;
        this.marchePrevisionRepository = marchePrevisionRepository;
    }

    public String beneficiaires(Integer idDetail) {
        return serviceBeneficiaireRepository.findByIdDetail(idDetail).stream()
                .map(b -> texte(b.getSoaCode()) + ":" + montant(b.getNouvMontBenef() != null
                        ? b.getNouvMontBenef() : b.getAncMontBenef()))
                .sorted()
                .collect(Collectors.joining(","));
    }

    public String lots(Integer idDetail) {
        return lotRepository.findByIdDetail(idDetail).stream()
                .map(l -> normaliser(l.getDesignationLot()) + ":" + montant(l.getMontLot())
                        + ":" + (l.getQteLot() == null ? "" : l.getQteLot()))
                .sorted()
                .collect(Collectors.joining(","));
    }

    public String previsions(Integer idDetail) {
        return marchePrevisionRepository.findByIdDetail(idDetail).stream()
                .map(p -> p.getIdCapm() + ":" + Optional.ofNullable(p.getDateDebut()).map(Object::toString).orElse("")
                        + ":" + Optional.ofNullable(p.getDateFin()).map(Object::toString).orElse(""))
                .sorted()
                .collect(Collectors.joining(","));
    }

    // --- Normalisation des scalaires comparés ---

    /** Montant sans zéros de fin ni notation scientifique ({@code null} si absent). */
    public static String montant(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    /** Texte épuré ({@code null} si vide). */
    public static String texte(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    public static String nombre(Integer v) {
        return v == null ? null : String.valueOf(v);
    }

    /** Minuscules, espaces réduits — pour les désignations de lots. */
    public static String normaliser(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.FRENCH).replaceAll("\\s+", " ");
    }
}
