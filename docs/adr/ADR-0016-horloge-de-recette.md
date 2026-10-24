# ADR-0016 : Une horloge unique, décalable en recette

**Statut :** Adopté
**Date :** 2026-10-24
**Origine :** demande front `frontend/docs/demande-backend-2026-10-24-horloge-recette.md` (R1, R2, Q1, Q2) ; accord du pilote du
24/10 sur la proposition du backend : R1 seul, R2 écarté.

## Contexte

Le serveur vit à l'heure réelle. Une chaîne de recette complète (fiche DAO, avis, dépôts, séance d'ouverture, évaluation, dossier de
marché) s'étale pourtant sur plusieurs semaines de calendrier. Pour la jouer en un jour, le front retouchait en base les dates de la
fiche validée (ouverture des dépôts, heure limite). Les actes signés, des PDF figés à leur production, gardaient alors des dates qui
contredisent le calendrier de la fiche. Cela s'est produit deux fois : le 19/10 (produits d'entretien) et le 24/10 (entretien des
véhicules, ligne 303336).

Le code lisait l'heure de deux façons : une horloge injectée (`Clock`, `ClockConfig`, 2026-08-27) dans 36 fichiers, et **170 appels
directs** à `LocalDateTime.now()` / `LocalDate.now()` dans 63 autres, dont l'échéance de la remise (`FicheMarcheService`).

## Décision

1. **Une seule horloge.** `HorlogeRecette.HORLOGE` est l'heure système plus un décalage. Le décalage est **nul et figé hors recette**.
   - Le bean `Clock` vaut `HorlogeRecette.HORLOGE` quand `app.recette.horloge.active=true` (variable `APP_RECETTE_HORLOGE`, posée
     par le lanceur de recette), et `Clock.systemDefaultZone()` sinon, donc en production.
   - Les 170 appels directs lisent désormais `now(HorlogeRecette.HORLOGE)`.
   - `HorlogeUniqueTest` interdit tout nouveau `now()` sans horloge dans `src/main`.
2. **Quelques appels gardent l'heure réelle**, par construction : les jetons JWT (leur expiration est jugée par la bibliothèque à
   l'heure réelle), les sessions de connexion, l'horodatage des réponses d'erreur. Ils sont listés dans le test.
3. **Un réglage à chaud** par l'Administrateur : `GET` / `PUT` / `DELETE /api/admin/horloge-recette`, poser une date (`maintenant`)
   ou avancer d'une durée (`avancer`), avec motif.
   - La route n'existe qu'en recette (`@ConditionalOnProperty`) : 404 ailleurs.
   - Le décalage est écrit dans un fichier hors dépôt (`${user.home}/prs-horloge-recette.txt`) et relu au démarrage, pour qu'une
     reconstruction du JAR ne ramène pas une recette à l'heure réelle.
   - Le décalage se calcule entre instants : la date voulue est lue dans le fuseau du serveur, et un changement d'heure (heure d'été
     ou d'hiver) entre aujourd'hui et cette date ne décale pas d'une heure.
4. **Le journal garde la vérité.** Sous horloge décalée, un écouteur JPA (`MentionHorlogeRecette`) ajoute
   `[recette — heure réelle : …]` au détail des entrées de journal : journal général, journal du dossier, journaux de l'offre, du
   reçu, de la séance, de l'évaluation et des modèles. Le détail est raccourci, jamais la mention, quand la colonne est bornée.
   Chaque réglage est tracé (`HORLOGE_RECETTE`).
5. **Pas de régénération des actes figés (R2).** Un acte signé est une pièce : le refaire à partir des données, même en recette,
   ouvrirait une porte qu'on ne veut pas voir en production. Une chaîne mal datée se **rejoue** sur une autre ligne du plan (Q1). La
   fiche 11 et sa chaîne du 24/10 restent en l'état, comme chaîne abandonnée (Q2).

## Conséquences

- **Plus facile** : une chaîne de recette se joue aux dates de la fiche, étape par étape, sans toucher la base. Les tests peuvent
  toujours remplacer le bean `Clock`.
- **Plus difficile** : tout code nouveau doit lire l'heure par l'horloge, injectée ou statique ; le test le rappelle. Les colonnes
  remplies par la base (`DEFAULT now()`) ne suivent pas l'horloge. Une seule existe (`t_acte_gestion.CREE_LE`), et le code la renseigne
  lui-même.
- **Ce qui reste vrai** : l'heure des jetons et des sessions. Avancer l'horloge ne déconnecte personne et ne prolonge aucun jeton.
- **Les tâches planifiées** (`@Scheduled`) partent toujours à l'heure réelle, mais elles jugent à l'heure de l'horloge.

## Marche arrière

Retirer la variable `APP_RECETTE_HORLOGE` du lanceur suffit à revenir à l'heure système. Le code reste correct, avec un décalage nul.
