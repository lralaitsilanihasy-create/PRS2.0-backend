package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.AvisDisponibiliteDto;
import cnm.prs.dto.DocumentFicheDto;
import cnm.prs.dto.FicheMarcheDto;
import cnm.prs.dto.LettreInvitationRequest;
import cnm.prs.entity.FicheMarche;
import cnm.prs.exception.BusinessRuleException;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;

/**
 * ⚠️ <strong>Lettres d'invitation des prestations intellectuelles</strong> (lot AV-4.1, demande front du 2026-10-01,
 * {@code demande-backend-2026-10-01-lettre-invitation-pi.md} ; plan accepté par le pilote, Q1-Q5) — le pendant de
 * l'avis spécifique ({@link AvisSpecifiqueService}) : leur procédure n'a pas d'avis public, chaque candidat de la liste
 * restreinte reçoit une lettre.
 *
 * <ul>
 *   <li><strong>Garde</strong> : celle de l'avis (PV signé {@code FAV}, ou {@code FAVR} après la levée des réserves),
 *       {@code CATEGORIE_SANS_LETTRE} en tête pour les fournitures et les travaux ; 409 {@code LETTRE_INDISPONIBLE}.</li>
 *   <li><strong>Rendu</strong> (Q1) : sur la dernière version validée, une paire .docx / .pdf par candidat, chacune avec
 *       son destinataire et la même liste ; type {@code LETTRE_INVITATION}.</li>
 *   <li><strong>Trace</strong> : la saisie et le rang du candidat, avec chaque document ({@code publication}).</li>
 *   <li><strong>Statut</strong> (Q5) : la première publication de la filiation, avis ou lettres, la fait passer à
 *       « Lancé ».</li>
 * </ul>
 */
@Service
public class LettreInvitationService {

    public static final String CODE_INDISPONIBLE = "LETTRE_INDISPONIBLE";
    /** Journal du dossier DAO : une impression des lettres. */
    public static final String JOURNAL_LETTRES_IMPRIMEES = "LETTRES_INVITATION_IMPRIMEES";

    private final AvisSpecifiqueService avis;
    private final FicheMarcheService fiches;
    private final DocumentsFicheMarcheService documents;
    private final DossierIntegriteService dossierIntegrite;
    private final JournalDossierService journal;
    /** ⚠️ 2026-10-07 (AMI en ligne, AMI-b, §B4) — la liste restreinte arrêtée, avec les comptes des candidats. */
    private final AmiPreselectionService preselection;
    private final NotificationService notifications;
    private final cnm.prs.repository.CompteCandidatRepository candidatsRepository;
    /** ⚠️ 2026-10-08 (lot 3 PI, PI-a, §B1) — les invités de la consultation restreinte et leur lettre. */
    private final InvitationsService invitations;
    private final cnm.prs.repository.DocumentFicheMarcheRepository documentRepository;

    public LettreInvitationService(AvisSpecifiqueService avis, FicheMarcheService fiches, DocumentsFicheMarcheService documents,
            DossierIntegriteService dossierIntegrite, JournalDossierService journal, AmiPreselectionService preselection,
            NotificationService notifications, cnm.prs.repository.CompteCandidatRepository candidatsRepository, InvitationsService invitations,
            cnm.prs.repository.DocumentFicheMarcheRepository documentRepository) {
        this.invitations = invitations;
        this.documentRepository = documentRepository;
        this.preselection = preselection;
        this.notifications = notifications;
        this.candidatsRepository = candidatsRepository;
        this.avis = avis;
        this.fiches = fiches;
        this.documents = documents;
        this.dossierIntegrite = dossierIntegrite;
        this.journal = journal;
    }

    /** §B4 — les lettres peuvent-elles être imprimées ? Même forme que l'avis. PRMP et UGPM, au périmètre de la fiche. */
    @Transactional(readOnly = true)
    public AvisDisponibiliteDto disponibilite(Long idDmc) {
        AvisSpecifiqueService.exigerProfil();
        return avis.evaluer(idDmc, fiches.lire(idDmc), true);
    }

    /**
     * §B3 — imprime une lettre par candidat : 400 nominatif ; 409 {@code LETTRE_INDISPONIBLE} (raison dans
     * {@code details.raison}) ; sinon les documents produits, candidat par candidat (.docx puis .pdf).
     */
    @Transactional
    public List<DocumentFicheDto> produire(Long idDmc, LettreInvitationRequest corps) {
        AvisSpecifiqueService.exigerProfil();
        // ⚠️ AMI-b (§B4) — avec une liste restreinte définitive, les candidats sont les siens (comptes, adresses déclarées) : la saisie
        // des candidats est alors ignorée.
        java.util.Optional<List<cnm.prs.entity.AmiListe>> listeAmi = preselection.listeDefinitive(idDmc);
        Saisie saisie = saisie(corps, listeAmi.isPresent());
        List<LettreInvitationRequest.Candidat> invites = listeAmi.map(l -> l.stream()
                .map(x -> new LettreInvitationRequest.Candidat(x.getRaisonSociale(), preselection.adresse(x.getIdCandidat()))).toList())
                .orElse(saisie.candidats());
        FicheMarcheDto courante = fiches.lire(idDmc);
        AvisDisponibiliteDto dispo = avis.evaluer(idDmc, courante, true);
        if (!dispo.disponible()) {
            throw new BusinessRuleException(AvisSpecifiqueService.message(dispo.raison(), true), CODE_INDISPONIBLE,
                    dispo.idDossierSoumis(), Map.of("raison", dispo.raison()));
        }
        dossierIntegrite.exigerMandatActif();
        FicheMarche validee = avis.derniereValidee(idDmc, true);
        FicheMarcheDto etat = fiches.lireVersion(idDmc, validee.getNumeroVersion());
        LocalDateTime maintenant = LocalDateTime.now();
        cnm.prs.entity.Marche ligne = avis.ligne(etat);
        Integer origine = AvisSpecifiqueService.origine(ligne);
        boolean premiereImpression = avis.premiereImpression(origine);

        Map<String, String> commun = new LinkedHashMap<>();
        commun.put(FormulairesCandidat.PREFIXE_LETTRE + "lieu", saisie.lieu());
        commun.put(FormulairesCandidat.PREFIXE_LETTRE + "date", saisie.date());
        commun.put(FormulairesCandidat.PREFIXE_LETTRE + "candidats", String.join(String.valueOf(FormulairesCandidat.SEPARATEUR_LIGNES),
                invites.stream().map(c -> "- " + c.nom()).toList()));
        List<String> destinataires = invites.stream()
                .map(c -> c.nom() + FormulairesCandidat.SEPARATEUR_LIGNES + lignes(c.adresse())).toList();
        Map<Integer, List<DocumentsFicheMarcheService.Produit>> parRang = documents.produireLettres(etat, commun, destinataires,
                maintenant);

        List<Map<String, String>> liste = invites.stream()
                .map(c -> { Map<String, String> m = new LinkedHashMap<>(); m.put("nom", c.nom()); m.put("adresse", c.adresse()); return m; })
                .toList();
        Set<Integer> ids = new java.util.HashSet<>();
        Map<Integer, Integer> pdfParRang = new java.util.HashMap<>();
        for (Map.Entry<Integer, List<DocumentsFicheMarcheService.Produit>> e : parRang.entrySet()) {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("dateEnvoi", saisie.dateIso());
            trace.put("lieu", saisie.lieu());
            trace.put("candidats", liste);
            trace.put("rang", e.getKey());
            Set<Integer> idsRang = documents.enregistrerAvis(validee.getIdFiche(), e.getValue(), maintenant,
                    DocumentsFicheMarcheService.publicationJson(trace));
            ids.addAll(idsRang);
            documentRepository.findAllById(idsRang).stream().filter(d -> "pdf".equals(d.getExtension())).findFirst()
                    .ifPresent(d -> pdfParRang.put(e.getKey(), d.getIdDocument()));
        }
        // ⚠️ PI-a (§B1) — les invités de la consultation restreinte : la liste de l'AMI (comptes), ou la saisie (adresses électroniques).
        List<InvitationsService.Invite> aInviter = new ArrayList<>();
        for (int i = 0; i < invites.size(); i++) {
            int rang = i + 1;
            LettreInvitationRequest.Candidat c = invites.get(i);
            aInviter.add(listeAmi.isPresent()
                    ? new InvitationsService.Invite(cnm.prs.entity.Invitation.AMI, listeAmi.get().get(i).getIdCandidat(), null, c.nom(), rang,
                            pdfParRang.get(rang))
                    : new InvitationsService.Invite(cnm.prs.entity.Invitation.SAISIE, null, c.email(), c.nom(), rang, pdfParRang.get(rang)));
            if (listeAmi.isEmpty() && c.email() != null) {
                notifications.emettre(null, cnm.prs.enums.TypeNotification.LETTRE_INVITATION, null, c.email(), "Invitation à remettre une proposition",
                        "Vous êtes invité à remettre une proposition pour « " + etat.getDesignationMarche() + " » (lettre d'invitation du "
                                + saisie.date() + "). Créez votre compte sur la plateforme avec cette adresse électronique : la procédure et la "
                                + "lettre y seront disponibles.");
            }
        }
        invitations.enregistrer(idDmc, aInviter, maintenant);
        int n = invites.size();
        listeAmi.ifPresent(l -> l.forEach(x -> {
            cnm.prs.entity.CompteCandidat c = candidatsRepository.findById(x.getIdCandidat()).orElse(null);
            notifications.emettreCandidat(cnm.prs.enums.TypeNotification.LETTRE_INVITATION, x.getIdCandidat(), c == null ? null : c.getEmail(),
                    idDmc.intValue(), cnm.prs.enums.TypeObjet.PROCEDURE, "Invitation à remettre une proposition", "Vous êtes invité, au rang "
                            + x.getRang() + " de la liste restreinte, à remettre une proposition pour « " + etat.getDesignationMarche()
                            + " » ; la lettre d'invitation (envoi du " + saisie.date() + ") et la demande de propositions sont disponibles sur la plateforme.");
        }));
        avis.lancerLigne(ligne, origine, premiereImpression, n + " lettre(s) d'invitation imprimée(s) (envoi du " + saisie.date() + ")");
        journal.tracer(dispo.idDossierSoumis(), JOURNAL_LETTRES_IMPRIMEES, n + " lettre(s) d'invitation imprimée(s) pour la "
                + "liste restreinte (fiche marché version " + validee.getNumeroVersion() + ", envoi du " + saisie.date() + ")");
        List<DocumentFicheDto> produits = new ArrayList<>(documents.listerAvis(List.of(validee)).stream()
                .filter(d -> ids.contains(d.idDocument())).toList());
        produits.sort(java.util.Comparator.comparing(DocumentFicheDto::idDocument));
        return produits;
    }

    /** Une adresse saisie sur plusieurs lignes : une ligne de la lettre par ligne saisie, les vides retirées. */
    private static String lignes(String adresse) {
        return String.join(String.valueOf(FormulairesCandidat.SEPARATEUR_LIGNES),
                adresse.lines().map(String::trim).filter(l -> !l.isEmpty()).toList());
    }

    private record Saisie(String dateIso, String date, String lieu, List<LettreInvitationRequest.Candidat> candidats) {
    }

    /** La saisie, contrôlée (400 nominatif) et mise en forme. */
    private static Saisie saisie(LettreInvitationRequest corps, boolean listeAmi) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        LettreInvitationRequest c = corps == null ? new LettreInvitationRequest(null, null, null) : corps;
        String date = AvisSpecifiqueService.date(c.dateEnvoi(), "dateEnvoi", "La date d'envoi des lettres", erreurs);
        String lieu = AvisSpecifiqueService.texte(c.lieu(), "lieu", "Le lieu d'envoi des lettres", erreurs);
        List<LettreInvitationRequest.Candidat> candidats = new ArrayList<>();
        if (listeAmi) {
            // ⚠️ AMI-b — la liste arrêtée fait foi : la saisie des candidats est ignorée.
        } else if (c.candidats() == null || c.candidats().isEmpty()) {
            erreurs.add(new ErrorResponse.FieldError("candidats", "La liste restreinte doit compter au moins un candidat."));
        } else {
            for (int i = 0; i < c.candidats().size(); i++) {
                LettreInvitationRequest.Candidat k = c.candidats().get(i);
                String n = AvisSpecifiqueService.texte(k == null ? null : k.nom(), "candidats[" + i + "].nom",
                        "Le nom du candidat n° " + (i + 1), erreurs);
                String a = AvisSpecifiqueService.texte(k == null ? null : k.adresse(), "candidats[" + i + "].adresse",
                        "L'adresse du candidat n° " + (i + 1), erreurs);
                String m = k == null || k.email() == null || k.email().isBlank() ? null : k.email().trim();
                if (m != null && (!m.contains("@") || m.length() > 150)) {
                    erreurs.add(new ErrorResponse.FieldError("candidats[" + i + "].email", "L'adresse électronique du candidat n° " + (i + 1)
                            + " est invalide."));
                }
                candidats.add(new LettreInvitationRequest.Candidat(n, a, m));
            }
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        return new Saisie(c.dateEnvoi().trim(), date, lieu, candidats);
    }
}
