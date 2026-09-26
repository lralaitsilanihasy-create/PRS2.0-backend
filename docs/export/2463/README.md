# Jeu de données du dossier réel 2463

Constitué le 2026-09-26 sur DBPRS20, **par l'API** (aucun `INSERT`), depuis la seule source
`frontendprs2/docs/jeu-donnees-2463-faits.md`. Une étape = un commit.

| Fichier | Rôle |
|---|---|
| `jeu-2463.json` | **entrée** (transcription de la fiche des faits, étiquettes [R]/[D]/[H]) **et résultat** (ce que l'API a répondu à chaque étape, puis l'export complet `resultat.export`) |
| `rejouer-2463.mjs` | rejoue le jeu par l'API, étape par étape ; s'arrête (code 2) sur toute valeur manquante |
| `agpm-2026.json`, `agpm-2026.html` | projet d'AGPM dérivé du plan (données API) et sa reconstitution dans la mise en page du front |
| `documents/` | les documents produits à la validation de la fiche (DPAO, CCAP, AE par lot, LF, BP/TC par lot, A1-A4, C1/C2 par lot) |
| `verification.md` | pour chaque fait : où il est stocké, sa valeur en base, sa nature [R]/[D]/[H] |
| `rapport-2463.md` | écarts du modèle, anomalies du dossier (§9) et leur traitement, tampon de la base avant/après |
| `../jeu-2463-hypotheses-backend.md` | les [H] que le circuit ou le modèle ont exigées |

## Rejouer sur une base vide

Prérequis : backend démarré sur `http://localhost:8080`, base remise à zéro (référentiels et comptes de contrôle
conservés : `ADMIN01`, `SECANT1`, `PRES001`, `MEMANT1`, `MEMANT2` ; entité contractante 11 présente), Node ≥ 20.

```powershell
cd docs\export\2463
$env:PRS_MDP = "<mot de passe commun des comptes>"      # défaut : Test@1234
node rejouer-2463.mjs --etapes 0-5                        # ou --etape n, une étape à la fois
```

Le script est idempotent étape par étape (une PRMP déjà créée, un dossier déjà soumis ou une fiche déjà validée
sont relus, pas recréés) et consigne les identifiants obtenus dans `jeu-2463.json › resultat`. Pour rejouer à neuf,
vider `resultat` (`{}`) après la remise à zéro de la base.
