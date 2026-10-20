package cnm.prs.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import cnm.prs.dto.DocumentFicheDto;
import cnm.prs.entity.PvExamen;
import cnm.prs.repository.DossierRepository;
import cnm.prs.repository.FicheMarcheRepository;
import cnm.prs.repository.PvExamenRepository;

/**
 * ⚠️ 2026-10-15 (demande front « modeles-guide-cao », §B1.1 ; Guide de la Commission d'Appel d'Offres, annexes 1 et 2) — les
 * références de l'en-tête des PV de la commission que l'application connaît : l'avis de la Commission sur le dossier d'appel d'offres
 * (son PV signé) et la première publication de l'avis spécifique (date et supports). Lectures sans exception : une référence inconnue
 * est simplement absente du PV.
 */
@Component
public class ReferencesCommission {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DossierRepository dossiers;
    private final PvExamenRepository pvs;
    private final FicheMarcheRepository ficheRepository;
    private final DocumentsFicheMarcheService documents;
    private final ProceduresEnLigneService procedures;
    private final cnm.prs.repository.AmiRepository amis;

    public ReferencesCommission(DossierRepository dossiers, PvExamenRepository pvs, FicheMarcheRepository ficheRepository,
            DocumentsFicheMarcheService documents, ProceduresEnLigneService procedures, cnm.prs.repository.AmiRepository amis) {
        this.procedures = procedures;
        this.amis = amis;
        this.dossiers = dossiers;
        this.pvs = pvs;
        this.ficheRepository = ficheRepository;
        this.documents = documents;
    }

    /**
     * La ligne de l'avis de la Commission sur le dossier d'appel d'offres : le plus récent PV signé du dossier que porte la procédure
     * (« … PV n° …, du …, avis favorable ») ; vide sans dossier ou sans PV signé.
     */
    public Optional<String> avisCommission(Long idDmc) {
        return pvCommission(idDmc).map(pv -> "Avis de la Commission nationale des marchés sur le dossier d'appel d'offres : "
                + lignePv(pv) + ".");
    }

    /** ⚠️ 2026-10-20 (« fiche-presentation-marche », §B1) — le plus récent PV signé du dossier que porte la procédure ; vide sinon. */
    public Optional<PvExamen> pvCommission(Long idDmc) {
        Integer idDossier = idDmc == null ? null : dossiers.findIdDossierByIdDmc(idDmc).orElse(null);
        if (idDossier == null) {
            return Optional.empty();
        }
        List<PvExamen> signes = pvs.findSignesParDossierRows(idDossier);
        return signes.isEmpty() ? Optional.empty() : Optional.of(signes.get(0));
    }

    /** « procès-verbal n° …, du …, avis favorable » (les éléments connus seulement). */
    public static String lignePv(PvExamen pv) {
        java.time.LocalDate date = pv.getDatePv() != null ? pv.getDatePv() : pv.getDateSignaturePresident();
        String avis = "FAV".equals(pv.getIdAvis()) ? "favorable" : "FAVR".equals(pv.getIdAvis()) ? "favorable avec réserves"
                : pv.getIdAvis() == null ? null : pv.getIdAvis().toLowerCase(java.util.Locale.FRENCH);
        return "procès-verbal" + (pv.getReferencePv() == null ? "" : " n° " + pv.getReferencePv()) + (date == null ? "" : " du " + date.format(DATE))
                + (avis == null ? "" : ", avis " + avis);
    }

    /** La première publication de l'avis spécifique : {@code datePublication} (aaaa-mm-jj) et {@code supports}, tels que saisis à l'impression ; vide sans avis. */
    public Optional<Publication> publication(Long idDmc) {
        if (idDmc == null) {
            return Optional.empty();
        }
        List<DocumentFicheDto> avis = documents.listerAvis(ficheRepository.findByIdDmcOrderByNumeroVersionAsc(idDmc)).stream()
                .filter(d -> DocumentsFicheMarcheService.TYPE_AVIS.equals(d.type()) && d.publication() != null).toList();
        if (avis.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> p = avis.get(avis.size() - 1).publication();   // listerAvis : du plus récent au plus ancien
        String date = texte(p.get("datePublication"));
        String supports = texte(p.get("supports"));
        return date == null && supports == null ? Optional.empty() : Optional.of(new Publication(date, supports));
    }

    /** La date de lancement (première publication) et les journaux et supports, lisibles pour un en-tête. */
    public Optional<String> lignePublication(Long idDmc) {
        return publication(idDmc).map(p -> "Date de lancement (première publication de l'avis d'appel d'offres) : "
                + (p.datePublication() == null ? "—" : lisible(p.datePublication())) + (p.supports() == null ? "" : " ; journaux et supports : "
                        + p.supports()) + ".");
    }

    public record Publication(String datePublication, String supports) {
    }

    /**
     * ⚠️ §B2.1 — la désignation de la procédure : « référence — objet », puis l'autorité contractante ; vide sans fiche (aucune exception :
     * {@link ProceduresEnLigneService#vueSiPresente}).
     */
    public List<String> designation(Long idDmc) {
        cnm.prs.dto.ProcedureEnLigneDto p = idDmc == null ? null : procedures.vueSiPresente(idDmc).orElse(null);
        if (p == null) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>();
        out.add("Procédure : " + Objects.toString(p.reference(), String.valueOf(idDmc)) + (p.objet() == null ? "" : " — " + p.objet()));
        if (p.autoriteContractante() != null) {
            out.add("Autorité contractante : " + p.autoriteContractante());
        }
        return out;
    }

    /**
     * ⚠️ §B2.1 — ce qui a arrêté la liste restreinte (prestations intellectuelles) : le rapport de présélection signé de l'appel à
     * manifestation d'intérêt (liste définitive), ou sa dispense motivée ; l'application ne porte pas de décision numérotée.
     */
    public Optional<String> listeRestreinte(Long idDmc) {
        cnm.prs.entity.Ami a = idDmc == null ? null : amis.findById(idDmc).orElse(null);
        if (a == null) {
            return Optional.empty();
        }
        if (cnm.prs.entity.Ami.DISPENSE.equals(a.getEtat())) {
            return Optional.of("Liste restreinte établie sans appel à manifestation d'intérêt" + (a.getMotifDispense() == null ? "" : " : "
                    + a.getMotifDispense()) + ".");
        }
        java.time.LocalDateTime le = a.getListeDefinitiveLe() != null ? a.getListeDefinitiveLe() : a.getListeArreteeLe();
        return le == null ? Optional.empty() : Optional.of("Liste restreinte arrêtée à l'issue de l'appel à manifestation d'intérêt" + (a.getObjet() == null
                ? "" : " « " + a.getObjet() + " »") + ", par le rapport de présélection " + (a.getListeDefinitiveLe() != null ? "signé le " : "produit le ")
                + le.format(DATE) + ".");
    }

    private static String texte(Object o) {
        String s = o == null ? null : Objects.toString(o).trim();
        return s == null || s.isEmpty() ? null : s;
    }

    private static String lisible(String iso) {
        try {
            return java.time.LocalDate.parse(iso.length() > 10 ? iso.substring(0, 10) : iso).format(DATE);
        } catch (RuntimeException e) {
            return iso;
        }
    }
}
