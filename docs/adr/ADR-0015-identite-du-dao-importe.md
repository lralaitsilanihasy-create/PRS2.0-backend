# ADR-0015 : L'identité du DAO importé est jugée et gardée par le serveur

**Statut :** Adopté
**Date :** 2026-10-17
**Origine :** demande front `frontend/docs/demande-backend-2026-10-17-identite-dao-importe.md` (§B1-B4) ; arbitrages du pilote du
17/10 : le contrôle repose sur quelques informations reprises de la ligne du plan, il ajoute un test de discrimination contre les
autres lignes de la même autorité, et un DAO dont la date limite est périmée est signalé.

## Contexte

L'import d'un DAO (`POST …/import`, ADR-0012) **propose** des valeurs, que la PRMP applique (`PUT …/import/appliquer`,
`PUT …/articles`). Rien ne vérifiait que le document était celui du marché de la fiche. Le DAO des articles de bureau (ligne 303337),
importé dans la fiche des consomptibles informatiques (ligne 303339), laissait appliquer une quarantaine de valeurs d'un autre marché.
Une valeur du plan que le DAO dit autrement devenait une divergence listée en bas de la revue, sans rien bloquer.

Deux contraintes :
1. Les DAO importés **viennent souvent d'autres applications**. Le contrôle ne peut donc pas reposer sur une marque propre à
   l'application, ni sur la reconnaissance de nos modèles.
2. Le verdict doit tenir **même si on contourne l'écran**. Or l'application de l'import ne renvoie pas le fichier : elle n'en
   renvoie que le nom et l'empreinte.

## Décision

1. **Juger en cherchant le plan dans le document** (`IdentiteDao`, pur et testable). Le contrôle porte sur cinq critères :
   - l'objet, l'autorité, le mode ;
   - la forme, pour un contrat-cadre seulement ;
   - les lots, pour une ligne allotie seulement.

   Chaque valeur du plan est cherchée dans tout le texte : ses mots significatifs dans l'ordre, dans un même passage, à un mot près
   pour une valeur de quatre mots ou plus. Un critère n'est **contredit** que sur un indice positif. Le verdict vaut `ETRANGER` dès
   un critère contredit, `NON_PROUVE` dès un critère introuvable, `CONFORME` sinon.
2. **Discriminer par les lignes voisines.** L'objet de la garde est comparé à toutes les lignes des plans de la même autorité, pour
   l'exercice et le précédent, par le coefficient de Dice des mots significatifs :
   - une voisine plus proche de 0,2 au moins (et à 0,5 au moins) **contredit** l'objet ;
   - une voisine aussi proche le rend **introuvable**.

   Les seuils sont ceux de la mesure.
3. **Garder le verdict sur le serveur** (V128, `t_import_verdict`) : par fiche, empreinte et utilisateur, valable **quatre heures**,
   purgé au bout d'un jour. L'application de l'import le relit (`IdentiteImportService.exiger`) :
   - sans lecture valable, 409 `IMPORT_EXPIRE` ;
   - un DAO étranger, 409 `DAO_ETRANGER` ;
   - un DAO non prouvé que la PRMP n'atteste pas (`attestation: true`, la PRMP seule), 409 `ATTESTATION_REQUISE`.

   Le journal `FICHE_IMPORTEE` note le verdict et l'auteur de l'attestation.
4. **Marquer les DAO produits par l'application** (§B4). La ligne, la fiche et la version sont posées dans les mots-clés du `.docx`
   (paquet OPC) et du PDF (PDFBox), après l'assemblage par Word. La marque ne fait foi que si le fichier est **resté tel que
   l'application l'a produit**, c'est-à-dire si son empreinte est celle d'un DAO complet en base. Un DAO repris dans Word pour une
   autre ligne garde ses propriétés : il est alors jugé comme tout autre document.

## Conséquences

- **Plus facile** : le DAO d'une autre ligne ne s'applique plus, même par un appel direct de l'API. Un DAO dont rien ne prouve
  l'identité s'applique sous la responsabilité nominative de la PRMP, tracée au journal.
- **Plus difficile** :
  - l'application d'un import exige désormais sa lecture par le même utilisateur dans les quatre heures. Un client qui
    appliquait sans lire, ou avec une empreinte forgée, est refusé ;
  - le verdict dépend de la qualité du plan. Un objet de ligne très abrégé, ou très différent de celui du DAO, donne
    `NON_PROUVE`, donc une attestation.
- **Marche arrière** : `exiger` est le seul point de blocage ; le neutraliser rend l'import à son comportement d'avant, le verdict
  restant affiché. La table se supprime sans effet sur le reste du schéma.

## Mesure (17/10)

- **Sur sa propre ligne**, jamais `ETRANGER` :
  - 2463, 297, Ankily-Mahasoa, 6114 (ligne 303338) et les DAO complets de 303337 et 303339 sont `CONFORME` ;
  - 6213 (ligne 303336) est `NON_PROUVE` : la garde dit « Entretien des véhicules administratifs », le plan « Entretien et
    réparation des véhicules administratifs pour la Direction Administrative et Financière », et la ligne 303355 lui ressemble
    autant.
- **Sur une autre des lignes 303336 à 303339** (même autorité, même mode, contrats-cadres) : 12 cas sur 12 sont `ETRANGER`, chacun
  avec sa propre ligne nommée comme voisine.

## Questions qui restent ouvertes

- Le DAO des bâtiments scolaires du MEN et le DAO routier n'étaient plus sur le poste lors de la mesure.
- Une PRMP qui importe le DAO d'une ligne d'un autre plan de son autorité, d'un exercice plus ancien que le précédent, n'a pas de
  voisine pour la discriminer : le jugement repose alors sur les seuls critères.
