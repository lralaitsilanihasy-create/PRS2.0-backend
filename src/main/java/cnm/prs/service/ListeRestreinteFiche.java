package cnm.prs.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cnm.prs.dto.ListeRestreinteDto;
import cnm.prs.entity.FicheCandidatRestreint;
import cnm.prs.entity.FicheListeRestreinte;
import cnm.prs.exception.ChampsInvalidesException;
import cnm.prs.exception.ErrorResponse;
import cnm.prs.repository.CompteCandidatRepository;
import cnm.prs.repository.FicheCandidatRestreintRepository;
import cnm.prs.repository.FicheListeRestreinteRepository;

/**
 * ⚠️ V156 (2026-11-02, demande front « appel-offres-restreint », §B3) — la <strong>liste restreinte saisie</strong> d'une version de
 * fiche d'appel d'offres restreint (décret n° 2019-1310, art. 11-2 et 13 ; loi n° 2016-055, art. 38-II) : sa source et ses candidats,
 * trois au moins. Comme le motif du recours ({@link MotifRestreintFiche}) : remplacée en bloc sur le brouillon, figée à la validation,
 * copiée à la révision ; exigée au bilan ({@code LISTE_RESTREINTE}). La lettre {@code LETTRE-AOR} la reprend à l'impression.
 */
@Component
@Transactional
public class ListeRestreinteFiche {

    /** Les sources de la liste, avec leur libellé. */
    public static final Map<String, String> SOURCES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("APPEL_CANDIDATURES", "Appel public à candidatures");
        m.put("BASE_AUTORITE", "Base de données de l'autorité contractante");
        m.put("PREMIER_APPEL_OFFRES", "Candidats qualifiés du premier appel d'offres");
        m.put("REGISTRE_ARMP", "Registre de l'ARMP");
        m.put("ORGANISME_PROFESSIONNEL", "Organisme professionnel");
        SOURCES = java.util.Collections.unmodifiableMap(m);
    }

    /** La source que pose l'appel à candidatures en ligne (§B4), pas la saisie. */
    public static final String APPEL_CANDIDATURES = "APPEL_CANDIDATURES";
    /** Le nombre minimal de candidats (loi, art. 38-II ; décret, art. 13). */
    public static final int MINIMUM = 3;

    private final FicheListeRestreinteRepository listes;
    private final FicheCandidatRestreintRepository candidats;
    private final CompteCandidatRepository comptes;

    public ListeRestreinteFiche(FicheListeRestreinteRepository listes, FicheCandidatRestreintRepository candidats,
            CompteCandidatRepository comptes) {
        this.listes = listes;
        this.candidats = candidats;
        this.comptes = comptes;
    }

    @Transactional(readOnly = true)
    public Optional<ListeRestreinteDto> lire(Integer idFiche) {
        if (idFiche == null) {
            return Optional.empty();
        }
        return listes.findById(idFiche).map(l -> new ListeRestreinteDto(l.getSource(), SOURCES.get(l.getSource()),
                candidats.findByIdFicheOrderByRangAsc(idFiche).stream().map(c -> new ListeRestreinteDto.Candidat(c.getRang(), c.getNom(),
                        c.getAdresse(), c.getEmail(), c.getIdCandidat(), c.getNif())).toList(), l.getSaisiLe()));
    }

    /**
     * Source connue (la saisie n'écrit pas {@code APPEL_CANDIDATURES}, que pose l'appel à candidatures), trois candidats au moins ;
     * par candidat : nom (≤ 255) et adresse (≤ 1 000) obligatoires, adresse électronique valide, compte candidat existant, NIF ≤ 30 ;
     * pas de doublon de nom, d'adresse électronique ou de compte. 400 nominatif ({@code source}, {@code candidats},
     * {@code candidats[i].…}).
     */
    ListeRestreinteDto valider(ListeRestreinteDto recue) {
        List<ErrorResponse.FieldError> erreurs = new ArrayList<>();
        String source = recue == null || recue.source() == null ? null : recue.source().trim().toUpperCase(Locale.ROOT);
        if (source == null || source.isEmpty()) {
            erreurs.add(new ErrorResponse.FieldError("source", "La source de la liste restreinte est obligatoire."));
        } else if (!SOURCES.containsKey(source)) {
            erreurs.add(new ErrorResponse.FieldError("source", "Source inconnue : « " + recue.source() + " » (" + String.join(", ", SOURCES.keySet())
                    + ")."));
        } else if (APPEL_CANDIDATURES.equals(source)) {
            erreurs.add(new ErrorResponse.FieldError("source", "La liste issue d'un appel à candidatures est celle qu'arrête la commission "
                    + "au terme de l'appel en ligne : elle ne se saisit pas."));
        }
        List<ListeRestreinteDto.Candidat> lues = recue == null || recue.candidats() == null ? List.of() : recue.candidats();
        if (lues.size() < MINIMUM) {
            erreurs.add(new ErrorResponse.FieldError("candidats", "La liste restreinte compte au moins " + MINIMUM + " candidats ("
                    + lues.size() + " saisi(s))."));
        }
        List<ListeRestreinteDto.Candidat> propres = new ArrayList<>();
        Set<String> noms = new HashSet<>();
        Set<String> emails = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < lues.size(); i++) {
            ListeRestreinteDto.Candidat c = lues.get(i);
            String q = "candidats[" + i + "].";
            int n = i + 1;
            String nom = texte(c == null ? null : c.nom());
            String adresse = texte(c == null ? null : c.adresse());
            String email = texte(c == null ? null : c.email());
            String idCandidat = texte(c == null ? null : c.idCandidat());
            String nif = texte(c == null ? null : c.nif());
            if (nom == null) {
                erreurs.add(new ErrorResponse.FieldError(q + "nom", "Le nom du candidat n° " + n + " est obligatoire."));
            } else if (nom.length() > 255) {
                erreurs.add(new ErrorResponse.FieldError(q + "nom", "Le nom du candidat n° " + n + " : 255 caractères au plus."));
            } else if (!noms.add(nom.toLowerCase(Locale.ROOT))) {
                erreurs.add(new ErrorResponse.FieldError(q + "nom", "Le candidat n° " + n + " figure déjà dans la liste."));
            }
            if (adresse == null) {
                erreurs.add(new ErrorResponse.FieldError(q + "adresse", "L'adresse du candidat n° " + n + " est obligatoire."));
            } else if (adresse.length() > 1000) {
                erreurs.add(new ErrorResponse.FieldError(q + "adresse", "L'adresse du candidat n° " + n + " : 1 000 caractères au plus."));
            }
            if (email != null && (!email.contains("@") || email.length() > 150)) {
                erreurs.add(new ErrorResponse.FieldError(q + "email", "L'adresse électronique du candidat n° " + n + " est invalide."));
            } else if (email != null && !emails.add(email.toLowerCase(Locale.ROOT))) {
                erreurs.add(new ErrorResponse.FieldError(q + "email", "L'adresse électronique du candidat n° " + n + " figure déjà dans la liste."));
            }
            if (idCandidat != null && !comptes.existsById(idCandidat)) {
                erreurs.add(new ErrorResponse.FieldError(q + "idCandidat", "Compte candidat inconnu : " + idCandidat + "."));
            } else if (idCandidat != null && !ids.add(idCandidat)) {
                erreurs.add(new ErrorResponse.FieldError(q + "idCandidat", "Le compte du candidat n° " + n + " figure déjà dans la liste."));
            }
            if (nif != null && nif.length() > 30) {
                erreurs.add(new ErrorResponse.FieldError(q + "nif", "Le NIF du candidat n° " + n + " : 30 caractères au plus."));
            }
            propres.add(new ListeRestreinteDto.Candidat(n, nom, adresse, email, idCandidat, nif));
        }
        if (!erreurs.isEmpty()) {
            throw new ChampsInvalidesException(erreurs);
        }
        return new ListeRestreinteDto(source, SOURCES.get(source), propres, null);
    }

    private static String texte(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public ListeRestreinteDto remplacer(Integer idFiche, ListeRestreinteDto valide, LocalDateTime le, String par) {
        candidats.deleteByIdFiche(idFiche);
        candidats.flush();
        listes.save(new FicheListeRestreinte(idFiche, valide.source(), le, par));
        listes.flush();
        for (ListeRestreinteDto.Candidat c : valide.candidats()) {
            candidats.save(new FicheCandidatRestreint(null, idFiche, c.rang(), c.nom(), c.adresse(), c.email(), c.idCandidat(), c.nif()));
        }
        return lire(idFiche).orElseThrow();
    }

    public void copier(Integer source, Integer cible) {
        listes.findById(source).ifPresent(l -> {
            listes.save(new FicheListeRestreinte(cible, l.getSource(), l.getSaisiLe(), l.getSaisiPar()));
            listes.flush();
            candidats.findByIdFicheOrderByRangAsc(source).forEach(c -> candidats.save(new FicheCandidatRestreint(null, cible, c.getRang(),
                    c.getNom(), c.getAdresse(), c.getEmail(), c.getIdCandidat(), c.getNif())));
        });
    }
}
