# Dériver un modèle `.docx` — pièges et méthode

Les 14 modèles Word du dépôt (`src/main/resources/templates/`) ne sont pas écrits à la main :
ils sont **dérivés par script** à partir du seul `.docx` officiel fourni par le métier,
`PV_AFSR_PPMAGPM_CENTRALE.docx`. Toucher un modèle veut donc dire rejouer une dérivation.

Ce document consigne ce qui a été appris en le faisant (chantier du 2026-08-04, retours de
l'équipe front du 2026-08-28). Sans lui, ces pièges se re-découvrent un par un, et chacun
produit un fichier que Word refuse d'ouvrir sans dire pourquoi.

## Les 14 modèles

- **12 PV** : `PV_{AFSR|AF|ANF}_{PPMAGPM|PPM}_{CENTRALE|REGIONALE}.docx` — trois axes (avis,
  type de plan, ressort). Matrice détaillée dans `api-endpoints.md`.
- **2 lettres de renvoi** : `LR_CENTRALE.docx`, `LR_REGIONALE.docx`.

## Méthode : chirurgie au niveau des runs

On part du `.docx` officiel et l'on opère **sur les offsets du texte décodé**, run par run —
pas sur le XML brut. Gras, italiques, emblème et mise en page sont ainsi préservés : ce sont
des propriétés de run, et on ne recrée jamais un run.

Repères stables du corps, valables sur tous les modèles :

| Repère | Contenu |
|---|---|
| §5  | en-tête commission |
| §6  | pointillés |
| §7  | titre |
| §12 | NATURE ET INTITULÉ DU DOSSIER |
| §13 | « L'an … » (paragraphe juridique) |
| §22 | avis de la commission |
| éléments 26-29 | bloc ANNEXE |
| table 25 | VISA |

## Les quatre pièges

**1. `xml:space="preserve"` sur tout `<w:t>` réécrit.** Sans cet attribut, Word supprime les
espaces de bord. Symptôme observé : « à l'affichage du PPMsous réserve » — deux mots collés,
un espace avalé.

**2. Écrire le `.docx` avec POI, jamais avec le zip .NET.** `OPCPackage` +
`part.getOutputStream()`. Un `[System.IO.Compression.ZipFile]` produit une archive
techniquement valide que Word refuse pourtant d'ouvrir (« No valid entries… »). Le zip n'est
pas en cause : c'est l'ordre et les métadonnées OPC des parties.

**3. Exclure les `<w:t/>` auto-fermants de la regex des runs.** Il y en a 25 dans le modèle
officiel, dont 8 dans la seule table VISA. Une regex naïve avale le XML jusqu'au `</w:t>`
suivant, et le fichier casse à l'ouverture sur « w:tbl must be terminated ».

**4. À `debut` égal, appliquer le patch le plus large d'abord.** Sinon une insertion placée au
début d'un autre patch est écrasée **silencieusement** — pas d'erreur, juste du texte manquant.

## Trois pièges voisins

**Espaces insécables (U+00A0).** Les modèles officiels suivent la typographie française :
« Secrétaire de séance␣: » avec une insécable avant le deux-points. Invisible au PDF, mais
toute égalité de chaînes échoue. Normaliser avant comparaison. Le métier écrit aussi parfois
une espace avant le chevron fermant (`<REFERENCE PV >`) — d'où les graphies multiples
tolérées par `PvDocumentGenerator.CHEF_LIEU_GRAPHIES`.

**Conversion Word fragile.** documents4j peut s'arrêter entre deux conversions (« The converter
seems to be shut down ») ; toutes les générations suivantes partent alors en 409 jusqu'au
redémarrage du serveur. Le correctif — `isOperational()`, recréation, retry unique — est en
place **des deux côtés** : `PvDocumentGenerator` (~ligne 397) et `LettreRenvoiDocumentGenerator`
(~ligne 97). Symptôme e2e typique : le premier PDF passe, les suivants échouent.

**Régénération paresseuse.** Le PDF n'est reproduit que si `CHEMIN_DOCUMENT` est nul. Pour
re-tester après un changement de modèle, **vider la colonne** — sinon on relit l'ancien fichier
et le nouveau modèle semble sans effet.

## Ce que les modèles contiennent — et ne contiennent pas

Vérifié le 2026-08-28 sur les 12 PV : `<NOM ET PRENOMS DU MEMBRE>` apparaît **exactement une
fois** par modèle, sous « Étaient présents », jamais ailleurs :

```
Etaient présents :
   Président de la Commission Nationale des Marchés : <NOM ET PRENOMS DU PRESIDENT>
   Chef de la Commission : <NOM ET PRENOMS DU CHEF DE COMMISSION>
   Membre de la Commission : <NOM ET PRENOMS DU MEMBRE>
   Secrétaire de séance : <NOM ET PRENOMS DU VERIFICATEUR>
```

**Le bloc de signature ne porte aucun nom.** Il ne reçoit que `<CHEF LIEU>` et
`<DATE AUJOURD'HUI>` ; les signatures y sont manuscrites sous des intitulés de rôle. Aucun
signataire — ni Président, ni CC, ni Membre — n'y est imprimé.

Conséquence pour la co-signature (règle du 2026-08-28) : le document nomme celui qui a
**instruit** le dossier, ce qui est exact, et ne nomme aucun signataire. Le co-signataire
(`IM_MEMBRE_COSIGNATAIRE`) n'apparaît nulle part sur le PV — c'est une absence, pas une erreur.
Réaffecter le placeholder existant au signataire le ferait figurer parmi les **présents** d'une
séance à laquelle il n'a pas assisté : le défaut serait introduit, pas corrigé.
