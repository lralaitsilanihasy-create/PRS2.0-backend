package cnm.prs.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cnm.prs.dto.BilanControlesDto;
import cnm.prs.dto.BilanControlesDto.Controle;
import cnm.prs.entity.ChampFicheMarche;
import cnm.prs.enums.SourceChampFiche;
import cnm.prs.enums.TypeChampFiche;

/**
 * ⚠️ <strong>Le catalogue des contrôles de la fiche marché</strong> (demande front du 2026-09-22, §B4 ; règles de
 * l'esquisse du pilote, quantité fixe).
 *
 * <p><strong>Comment une règle trouve ses champs.</strong> La liste nominative des champs n'est pas connue : une
 * règle ne peut donc pas nommer un code. C'est le <em>champ</em> qui nomme la règle, dans son attribut
 * {@code controle} — {@code REGLE} quand elle ne lit que lui, {@code REGLE:ROLE} quand elle en lit plusieurs
 * ({@code VALIDITE_GARANTIE_SUP_OFFRE:GARANTIE} et {@code :OFFRE}). Deux règles n'ont pas besoin de champ :
 * {@code OBLIGATOIRE} (tout champ obligatoire d'une rubrique ouverte) et {@code MONTANT_POSITIF} (tout
 * {@code MONTANT}). Une règle dont un rôle manque n'est pas évaluée — ni bloquante, ni « ok » : elle attend son
 * champ. Ce qui vient du cadrage (taux d'avance, type de prix) est lu dans le cadrage.</p>
 *
 * <p>Pur : des valeurs en entrée, un bilan en sortie, aucune lecture de base.</p>
 *
 * <table>
 * <tr><th>Règle</th><th>Rôles</th><th>Gravité</th></tr>
 * <tr><td>OBLIGATOIRE</td><td>—</td><td>bloquant</td></tr>
 * <tr><td>MONTANT_POSITIF</td><td>—</td><td>bloquant</td></tr>
 * <tr><td>DATES_ORDRE</td><td>LANCEMENT (à défaut : PPM), REMISE, OUVERTURE, ATTRIBUTION (à défaut : PPM)</td><td>bloquant</td></tr>
 * <tr><td>VALIDITE_GARANTIE_SUP_OFFRE</td><td>GARANTIE, OFFRE (jours)</td><td>bloquant</td></tr>
 * <tr><td>AVANCE_MAX_20</td><td>TAUX (à défaut : cadrage {@code tauxAvance})</td><td>bloquant</td></tr>
 * <tr><td>AVANCE_SUP_5_GARANTIE</td><td>TAUX (idem), GARANTIE (garantie de restitution)</td><td>bloquant</td></tr>
 * <tr><td>FORFAIT_60_40</td><td>RECEPTION (%), PV (%) — si cadrage {@code typePrix = FORFAITAIRE}</td><td>bloquant</td></tr>
 * <tr><td>PENALITES_PLAFOND_15</td><td>TAUX (%), DEROGATION (texte)</td><td>avertissement</td></tr>
 * <tr><td>INTERETS_MORATOIRES_TAUX</td><td>TAUX (%), BANQUE (%)</td><td>avertissement</td></tr>
 * <tr><td>DELAI_PAIEMENT_75</td><td>DELAI (jours)</td><td>avertissement</td></tr>
 * </table>
 */
public final class ControlesFicheMarche {

    public static final String OBLIGATOIRE = "OBLIGATOIRE";
    public static final String MONTANT_POSITIF = "MONTANT_POSITIF";
    public static final String DATES_ORDRE = "DATES_ORDRE";
    public static final String VALIDITE_GARANTIE_SUP_OFFRE = "VALIDITE_GARANTIE_SUP_OFFRE";
    public static final String AVANCE_MAX_20 = "AVANCE_MAX_20";
    public static final String AVANCE_SUP_5_GARANTIE = "AVANCE_SUP_5_GARANTIE";
    public static final String FORFAIT_60_40 = "FORFAIT_60_40";
    public static final String PENALITES_PLAFOND_15 = "PENALITES_PLAFOND_15";
    public static final String INTERETS_MORATOIRES_TAUX = "INTERETS_MORATOIRES_TAUX";
    public static final String DELAI_PAIEMENT_75 = "DELAI_PAIEMENT_75";
    /** ⚠️ V45 (2026-09-25) — le besoin et les garanties générées. */
    public static final String BESOIN_INCOMPLET = "BESOIN_INCOMPLET";
    public static final String QUANTITES_ORDRE = "QUANTITES_ORDRE";
    public static final String GARANTIE_MANQUANTE = "GARANTIE_MANQUANTE";
    public static final String GARANTIE_TAUX = "GARANTIE_TAUX";
    private static final String BLOC_BESOIN = "B12";

    /** Libellés CAPM du plan dont les dates entrent dans {@code DATES_ORDRE} à défaut de champ. */
    public static final String PPM_LANCEMENT = "LANCEMENT";
    public static final String PPM_ATTRIBUTION = "ATTRIBUTION";

    private ControlesFicheMarche() {
    }

    /**
     * @param champsOuverts champs de la fiche dont la condition de cadrage est vraie, pour le type de marché
     * @param valeurs       valeurs saisies, par code (normalisées : nombres en chiffres, dates ISO)
     * @param cadrage       réponses du cadrage
     * @param datesPpm      dates prévisionnelles du plan par libellé CAPM ({@link #PPM_LANCEMENT}, {@link #PPM_ATTRIBUTION})
     */
    public static BilanControlesDto bilan(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs,
            Map<String, ?> cadrage, Map<String, LocalDate> datesPpm) {
        return bilan(champsOuverts, valeurs, cadrage, datesPpm, 0);
    }

    /**
     * ⚠️ 2026-09-25 (§B2) — {@code nbLots} : lots de la ligne au plan. Sur une ligne allotie, un champ {@code parLot} est
     * attendu (et, s'il est obligatoire, exigé) <strong>pour chaque lot</strong>, sous {@code CODE#n} : une ligne du
     * bilan par lot manquant, le rang dans le message ({@link LotsFiche}).
     */
    public static BilanControlesDto bilan(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs,
            Map<String, ?> cadrage, Map<String, LocalDate> datesPpm, int nbLots) {
        return bilan(champsOuverts, valeurs, cadrage, datesPpm, nbLots, null, null);
    }

    /**
     * ⚠️ V45 (2026-09-25, formulaires du candidat, §B4) — le besoin d'une fiche de fournitures ({@code articles}), et si
     * le marché est à commande (quantités minimum et maximum).
     */
    public record Besoin(List<BesoinFiche.Article> articles, boolean aCommande) {
    }

    /**
     * ⚠️ V45 — {@code besoin} : celui d'une fiche de fournitures, {@code null} hors du périmètre du besoin (pas de
     * contrôle {@code BESOIN_INCOMPLET} / {@code QUANTITES_ORDRE}) ; {@code taux} : paramètres administrables du contrôle
     * {@code GARANTIE_TAUX} ({@code null} : non évalué).
     */
    public static BilanControlesDto bilan(List<ChampFicheMarche> champsOuverts, Map<String, String> valeurs,
            Map<String, ?> cadrage, Map<String, LocalDate> datesPpm, int nbLots, Besoin besoin,
            ParametreService.TauxGarantie taux) {
        List<Controle> bloquants = new ArrayList<>();
        List<Controle> avertissements = new ArrayList<>();
        List<Controle> ok = new ArrayList<>();
        Map<String, Map<String, ChampFicheMarche>> roles = new LinkedHashMap<>();
        int nbSaisis = 0;
        int nbAttendus = 0;

        for (ChampFicheMarche c : champsOuverts) {
            // ⚠️ Lot 5 (2026-09-24) — un champ PIECE (formulaire à remplir, joint au dossier) ne se saisit pas dans la fiche :
            // ni attendu, ni obligatoire au bilan — sinon il bloquerait la validation et fausserait le compte.
            boolean saisie = SourceChampFiche.SAISIE.name().equals(c.getSource())
                    && !TypeChampFiche.PIECE.name().equals(c.getType());
            if (saisie) {
                boolean parLot = LotsFiche.parLot(c, nbLots);
                List<String> cles = LotsFiche.cles(c, nbLots);
                for (int i = 0; i < cles.size(); i++) {
                    String cle = cles.get(i);
                    String lot = parLot ? " (lot " + (i + 1) + ")" : "";
                    String v = valeurs.get(cle);
                    boolean vide = v == null || v.isBlank();
                    nbAttendus++;
                    if (!vide) {
                        nbSaisis++;
                    }
                    if (Boolean.TRUE.equals(c.getObligatoire()) && vide) {
                        bloquants.add(new Controle(OBLIGATOIRE, List.of(cle), c.codeBloc(),
                                "« " + c.getLibelle() + " »" + lot + " est obligatoire."));
                    }
                    if (TypeChampFiche.MONTANT.name().equals(c.getType()) && !vide) {
                        BigDecimal montant = nombre(v);
                        if (montant != null && montant.signum() <= 0) {
                            bloquants.add(new Controle(MONTANT_POSITIF, List.of(cle), c.codeBloc(),
                                    "« " + c.getLibelle() + " »" + lot + " doit être un montant strictement positif."));
                        } else if (montant != null) {
                            ok.add(new Controle(MONTANT_POSITIF, List.of(cle), c.codeBloc(),
                                    "« " + c.getLibelle() + " »" + lot + " : montant positif."));
                        }
                    }
                }
            }
            if (c.getControle() != null && !c.getControle().isBlank()) {
                String[] parts = c.getControle().trim().split(":", 2);
                String regle = parts[0].trim().toUpperCase();
                String role = parts.length > 1 ? parts[1].trim().toUpperCase() : "";
                roles.computeIfAbsent(regle, k -> new LinkedHashMap<>()).put(role, c);
            }
        }

        datesOrdre(roles.get(DATES_ORDRE), valeurs, datesPpm, bloquants, ok);
        validiteGarantie(roles.get(VALIDITE_GARANTIE_SUP_OFFRE), valeurs, bloquants, ok);
        avance(roles.get(AVANCE_MAX_20), roles.get(AVANCE_SUP_5_GARANTIE), valeurs, cadrage, bloquants, ok);
        forfait(roles.get(FORFAIT_60_40), valeurs, cadrage, bloquants, ok);
        penalites(roles.get(PENALITES_PLAFOND_15), valeurs, avertissements, ok);
        interetsMoratoires(roles.get(INTERETS_MORATOIRES_TAUX), valeurs, avertissements, ok);
        delaiPaiement(roles.get(DELAI_PAIEMENT_75), valeurs, avertissements, ok);
        // ⚠️ V45 (2026-09-25, §B4) — le besoin, la garantie générée et son taux.
        besoin(besoin, nbLots, bloquants, ok);
        garantieManquante(roles.get(GARANTIE_MANQUANTE), valeurs, cadrage, bloquants, ok);
        garantieTaux(roles.get(GARANTIE_TAUX), valeurs, nbLots, taux, avertissements, ok);

        return new BilanControlesDto(bloquants, avertissements, ok, nbSaisis, nbAttendus);
    }

    // ------------------------------------------------------------------ règles V45

    /**
     * {@code BESOIN_INCOMPLET} : chaque lot (le lot unique d'une ligne non allotie) a au moins un article, chaque article
     * au moins une caractéristique ; {@code QUANTITES_ORDRE} : à commande, quantité minimum ≤ maximum. Bloquants.
     */
    private static void besoin(Besoin besoin, int nbLots, List<Controle> bloquants, List<Controle> ok) {
        if (besoin == null) {
            return;
        }
        List<Integer> lots = new ArrayList<>();
        if (LotsFiche.alloti(nbLots)) {
            for (int n = 1; n <= nbLots; n++) {
                lots.add(n);
            }
        } else {
            lots.add(null);
        }
        boolean complet = true;
        for (Integer lot : lots) {
            List<BesoinFiche.Article> duLot = besoin.articles().stream()
                    .filter(a -> java.util.Objects.equals(a.lot(), lot)).toList();
            String nomLot = lot == null ? "Le besoin" : "Le lot " + lot;
            if (duLot.isEmpty()) {
                complet = false;
                bloquants.add(new Controle(BESOIN_INCOMPLET, List.of(), BLOC_BESOIN, nomLot + " n'a aucun article."));
            }
            for (BesoinFiche.Article a : duLot) {
                String nom = "L'article " + a.ordre() + (lot == null ? "" : " du lot " + lot) + " (« " + a.designation() + " »)";
                if (a.caracteristiques() == null || a.caracteristiques().isEmpty()) {
                    complet = false;
                    bloquants.add(new Controle(BESOIN_INCOMPLET, List.of(), BLOC_BESOIN,
                            nom + " n'a aucune caractéristique exigée."));
                }
                if (besoin.aCommande() && a.quantiteMin() != null && a.quantiteMax() != null
                        && a.quantiteMin() > a.quantiteMax()) {
                    bloquants.add(new Controle(QUANTITES_ORDRE, List.of(), BLOC_BESOIN, nom + " : la quantité minimum ("
                            + a.quantiteMin() + ") dépasse la quantité maximum (" + a.quantiteMax() + ")."));
                }
            }
        }
        if (complet) {
            ok.add(new Controle(BESOIN_INCOMPLET, List.of(), BLOC_BESOIN,
                    "Besoin complet : chaque lot a ses articles, chaque article ses caractéristiques."));
        }
    }

    /**
     * {@code GARANTIE_MANQUANTE} (rôle {@code FORME}, le champ qui dit quel modèle de garantie est joint) : une garantie
     * de soumission exigée ({@code garantieSoumission = OUI}) se génère par lot, au montant du lot — il faut donc sa
     * forme (C1, C2). Bloquant. Les montants par lot sont, eux, exigés par leur caractère obligatoire.
     */
    private static void garantieManquante(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, Map<String, ?> cadrage,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche forme = r == null ? null : r.get("FORME");
        if (forme == null || cadrage == null || !"OUI".equalsIgnoreCase(String.valueOf(cadrage.get("garantieSoumission")))) {
            return;
        }
        String v = valeurs.get(forme.getCode());
        if (v == null || v.isBlank()) {
            bloquants.add(new Controle(GARANTIE_MANQUANTE, List.of(forme.getCode()), forme.codeBloc(),
                    "Une garantie de soumission est exigée : choisissez le modèle joint (« " + forme.getLibelle()
                            + " »), qui sera produit pour chaque lot à son montant."));
        } else {
            ok.add(new Controle(GARANTIE_MANQUANTE, List.of(forme.getCode()), forme.codeBloc(),
                    "Modèle de garantie de soumission retenu : " + v + "."));
        }
    }

    /**
     * {@code GARANTIE_TAUX} (rôles {@code GARANTIE} et {@code MAXIMUM}, par lot) : la garantie rapportée au montant
     * maximum du lot, comparée aux bornes administrables ({@link ParametreService.TauxGarantie}). <strong>Avertissement,
     * jamais bloquant</strong> ; sans borne, le taux est seulement constaté.
     */
    private static void garantieTaux(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, int nbLots,
            ParametreService.TauxGarantie taux, List<Controle> avertissements, List<Controle> ok) {
        ChampFicheMarche garantie = r == null ? null : r.get("GARANTIE");
        ChampFicheMarche maximum = r == null ? null : r.get("MAXIMUM");
        if (garantie == null || maximum == null || taux == null) {
            return;
        }
        List<String> clesG = LotsFiche.cles(garantie, nbLots);
        List<String> clesM = LotsFiche.cles(maximum, nbLots);
        for (int i = 0; i < Math.min(clesG.size(), clesM.size()); i++) {
            BigDecimal g = nombre(valeurs.get(clesG.get(i)));
            BigDecimal m = nombre(valeurs.get(clesM.get(i)));
            if (g == null || m == null || m.signum() <= 0) {
                continue;
            }
            BigDecimal t = g.multiply(new BigDecimal("100")).divide(m, 2, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
            String lot = LotsFiche.alloti(nbLots) ? " du lot " + (i + 1) : "";
            String reference = taux.reference() == null ? "" : " (référence " + taux.reference().toPlainString() + " %)";
            List<String> champs = List.of(clesG.get(i), clesM.get(i));
            boolean horsBornes = (taux.borneBasse() != null && t.compareTo(taux.borneBasse()) < 0)
                    || (taux.borneHaute() != null && t.compareTo(taux.borneHaute()) > 0);
            String constat = "Garantie de soumission" + lot + " : " + t.toPlainString() + " % du montant maximum" + reference;
            if (horsBornes) {
                avertissements.add(new Controle(GARANTIE_TAUX, champs, garantie.codeBloc(), constat + ", hors des bornes "
                        + (taux.borneBasse() == null ? "—" : taux.borneBasse().toPlainString() + " %") + " à "
                        + (taux.borneHaute() == null ? "—" : taux.borneHaute().toPlainString() + " %") + "."));
            } else {
                ok.add(new Controle(GARANTIE_TAUX, champs, garantie.codeBloc(), constat + "."));
            }
        }
    }

    // ------------------------------------------------------------------ règles

    private static void datesOrdre(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            Map<String, LocalDate> datesPpm, List<Controle> bloquants, List<Controle> ok) {
        if (r == null) {
            return;
        }
        // ⚠️ Lot 4 (2026-09-23) — la notification, cinquième date du calendrier du contrat-cadre (rôle NOTIFICATION).
        List<String> etapes = List.of("LANCEMENT", "REMISE", "OUVERTURE", "ATTRIBUTION", "NOTIFICATION");
        List<String> libelles = List.of("lancement", "remise des offres", "ouverture des plis", "attribution", "notification");
        List<LocalDate> dates = new ArrayList<>();
        List<String> noms = new ArrayList<>();
        List<String> champs = new ArrayList<>();
        String bloc = null;
        for (int i = 0; i < etapes.size(); i++) {
            ChampFicheMarche c = r.get(etapes.get(i));
            LocalDate d = null;
            if (c != null) {
                d = date(valeurs.get(c.getCode()));
                if (d != null) {
                    champs.add(c.getCode());
                    bloc = bloc == null ? c.codeBloc() : bloc;
                }
            } else if (datesPpm != null && (i == 0 || i == 3)) {
                d = datesPpm.get(i == 0 ? PPM_LANCEMENT : PPM_ATTRIBUTION);
            }
            if (d != null) {
                dates.add(d);
                noms.add(libelles.get(i));
            }
        }
        if (dates.size() < 2) {
            return;
        }
        for (int i = 1; i < dates.size(); i++) {
            if (dates.get(i).isBefore(dates.get(i - 1))) {
                bloquants.add(new Controle(DATES_ORDRE, champs, bloc, "Dates dans l'ordre : " + noms.get(i) + " ("
                        + dates.get(i) + ") précède " + noms.get(i - 1) + " (" + dates.get(i - 1)
                        + ") — lancement < remise des offres < ouverture < attribution < notification."));
                return;
            }
        }
        ok.add(new Controle(DATES_ORDRE, champs, bloc, "Dates dans l'ordre (" + String.join(" < ", noms) + ")."));
    }

    private static void validiteGarantie(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            List<Controle> bloquants, List<Controle> ok) {
        ChampFicheMarche garantie = r == null ? null : r.get("GARANTIE");
        ChampFicheMarche offre = r == null ? null : r.get("OFFRE");
        BigDecimal g = garantie == null ? null : nombre(valeurs.get(garantie.getCode()));
        BigDecimal o = offre == null ? null : nombre(valeurs.get(offre.getCode()));
        if (g == null || o == null) {
            return;
        }
        List<String> champs = List.of(garantie.getCode(), offre.getCode());
        if (g.compareTo(o) <= 0) {
            bloquants.add(new Controle(VALIDITE_GARANTIE_SUP_OFFRE, champs, garantie.codeBloc(),
                    "La validité de la garantie de soumission (" + g.stripTrailingZeros().toPlainString()
                            + " jours) doit dépasser la validité des offres (" + o.stripTrailingZeros().toPlainString() + " jours)."));
        } else {
            ok.add(new Controle(VALIDITE_GARANTIE_SUP_OFFRE, champs, garantie.codeBloc(),
                    "Validité de la garantie supérieure à celle des offres."));
        }
    }

    private static void avance(Map<String, ChampFicheMarche> max20, Map<String, ChampFicheMarche> sup5,
            Map<String, String> valeurs, Map<String, ?> cadrage, List<Controle> bloquants, List<Controle> ok) {
        if (cadrage == null || !"OUI".equalsIgnoreCase(String.valueOf(cadrage.get("avance")))) {
            return;
        }
        ChampFicheMarche champTaux = max20 != null && max20.get("TAUX") != null ? max20.get("TAUX")
                : sup5 != null ? sup5.get("TAUX") : null;
        BigDecimal taux = champTaux != null ? nombre(valeurs.get(champTaux.getCode())) : nombre(cadrage.get("tauxAvance"));
        if (taux == null) {
            return;
        }
        List<String> champs = champTaux == null ? List.of() : List.of(champTaux.getCode());
        String bloc = champTaux == null ? "B08" : champTaux.codeBloc();
        if (taux.compareTo(new BigDecimal("20")) > 0) {
            bloquants.add(new Controle(AVANCE_MAX_20, champs, bloc, "Avance de " + taux.stripTrailingZeros().toPlainString()
                    + " % : le plafond réglementaire est de 20 % du montant TTC."));
        } else {
            ok.add(new Controle(AVANCE_MAX_20, champs, bloc, "Avance ≤ 20 %."));
        }
        ChampFicheMarche garantie = sup5 == null ? null : sup5.get("GARANTIE");
        if (garantie != null && taux.compareTo(new BigDecimal("5")) > 0) {
            String v = valeurs.get(garantie.getCode());
            if (v == null || v.isBlank()) {
                bloquants.add(new Controle(AVANCE_SUP_5_GARANTIE, List.of(garantie.getCode()), garantie.codeBloc(),
                        "Avance de " + taux.stripTrailingZeros().toPlainString() + " % : au-delà de 5 %, la garantie de "
                                + "restitution d'avance (« " + garantie.getLibelle() + " ») doit être renseignée."));
            } else {
                ok.add(new Controle(AVANCE_SUP_5_GARANTIE, List.of(garantie.getCode()), garantie.codeBloc(),
                        "Garantie de restitution d'avance renseignée."));
            }
        }
    }

    private static void forfait(Map<String, ChampFicheMarche> r, Map<String, String> valeurs, Map<String, ?> cadrage,
            List<Controle> bloquants, List<Controle> ok) {
        if (r == null || cadrage == null || !"FORFAITAIRE".equalsIgnoreCase(String.valueOf(cadrage.get("typePrix")))) {
            return;
        }
        ChampFicheMarche reception = r.get("RECEPTION");
        ChampFicheMarche pv = r.get("PV");
        BigDecimal rec = reception == null ? null : nombre(valeurs.get(reception.getCode()));
        BigDecimal surPv = pv == null ? null : nombre(valeurs.get(pv.getCode()));
        if (rec == null && surPv == null) {
            return;
        }
        List<String> champs = new ArrayList<>();
        if (reception != null) {
            champs.add(reception.getCode());
        }
        if (pv != null) {
            champs.add(pv.getCode());
        }
        String bloc = reception != null ? reception.codeBloc() : pv.codeBloc();
        boolean recOk = rec == null || rec.compareTo(new BigDecimal("60")) >= 0;
        boolean pvOk = surPv == null || surPv.compareTo(new BigDecimal("40")) <= 0;
        if (recOk && pvOk) {
            ok.add(new Controle(FORFAIT_60_40, champs, bloc, "Prix global forfaitaire : au moins 60 % à réception, au plus 40 % sur PV."));
        } else {
            bloquants.add(new Controle(FORFAIT_60_40, champs, bloc, "Prix global forfaitaire : au moins 60 % à la "
                    + "réception et au plus 40 % sur procès-verbal (saisi : " + (rec == null ? "—" : rec.stripTrailingZeros().toPlainString() + " %")
                    + " à réception, " + (surPv == null ? "—" : surPv.stripTrailingZeros().toPlainString() + " %") + " sur PV)."));
        }
    }

    private static void penalites(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            List<Controle> avertissements, List<Controle> ok) {
        ChampFicheMarche taux = r == null ? null : r.get("TAUX");
        BigDecimal t = taux == null ? null : nombre(valeurs.get(taux.getCode()));
        if (t == null) {
            return;
        }
        ChampFicheMarche derogation = r.get("DEROGATION");
        String d = derogation == null ? null : valeurs.get(derogation.getCode());
        if (t.compareTo(new BigDecimal("15")) > 0 && (d == null || d.isBlank())) {
            avertissements.add(new Controle(PENALITES_PLAFOND_15, List.of(taux.getCode()), taux.codeBloc(),
                    "Pénalités de " + t.stripTrailingZeros().toPlainString() + " % : au-delà du plafond de 15 % du CCAG, "
                            + "la dérogation doit être précisée."));
        } else {
            ok.add(new Controle(PENALITES_PLAFOND_15, List.of(taux.getCode()), taux.codeBloc(),
                    t.compareTo(new BigDecimal("15")) > 0 ? "Pénalités au-delà de 15 %, dérogation précisée." : "Pénalités dans le plafond du CCAG."));
        }
    }

    private static void interetsMoratoires(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            List<Controle> avertissements, List<Controle> ok) {
        ChampFicheMarche taux = r == null ? null : r.get("TAUX");
        ChampFicheMarche banque = r == null ? null : r.get("BANQUE");
        BigDecimal t = taux == null ? null : nombre(valeurs.get(taux.getCode()));
        BigDecimal b = banque == null ? null : nombre(valeurs.get(banque.getCode()));
        if (t == null || b == null) {
            return;
        }
        List<String> champs = List.of(taux.getCode(), banque.getCode());
        if (t.compareTo(b.add(BigDecimal.ONE)) < 0) {
            avertissements.add(new Controle(INTERETS_MORATOIRES_TAUX, champs, taux.codeBloc(),
                    "Intérêts moratoires de " + t.stripTrailingZeros().toPlainString() + " % : le taux attendu est au moins "
                            + "celui de la Banque centrale (" + b.stripTrailingZeros().toPlainString() + " %) majoré d'un point."));
        } else {
            ok.add(new Controle(INTERETS_MORATOIRES_TAUX, champs, taux.codeBloc(), "Intérêts moratoires ≥ taux Banque centrale + 1 point."));
        }
    }

    private static void delaiPaiement(Map<String, ChampFicheMarche> r, Map<String, String> valeurs,
            List<Controle> avertissements, List<Controle> ok) {
        ChampFicheMarche delai = r == null ? null : r.get("DELAI");
        BigDecimal d = delai == null ? null : nombre(valeurs.get(delai.getCode()));
        if (d == null) {
            return;
        }
        if (d.compareTo(new BigDecimal("75")) > 0) {
            avertissements.add(new Controle(DELAI_PAIEMENT_75, List.of(delai.getCode()), delai.codeBloc(),
                    "Délai de paiement de " + d.stripTrailingZeros().toPlainString() + " jours : le délai réglementaire est de 75 jours au plus."));
        } else {
            ok.add(new Controle(DELAI_PAIEMENT_75, List.of(delai.getCode()), delai.codeBloc(), "Délai de paiement ≤ 75 jours."));
        }
    }

    // ------------------------------------------------------------------ lectures

    /** Un nombre depuis une valeur normalisée ; {@code null} si absent ou illisible. */
    public static BigDecimal nombre(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim().replace(" ", "").replace(',', '.');
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Une date ISO ({@code yyyy-MM-dd}) ; {@code null} si absente ou illisible. */
    public static LocalDate date(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(v.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
