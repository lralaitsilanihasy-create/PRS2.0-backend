# ADR-0013 : Le scellement des offres déposées en ligne

**Statut :** Proposé (à adopter avant toute ligne de code des lots 2 à 4)
**Date :** 2026-10-04
**Origine :** demande front `frontend/docs/demande-backend-2026-10-04-soumission-en-ligne.md`, §B1 (lot 0) ; plan
`docs/plan-2026-10-04-soumission-en-ligne.md`, Q4 arbitrée par le pilote le 04/10 : chiffrement dans le navigateur du
candidat, ouverture au quorum des membres désignés (paramètres internes de V50, ADR-0010), procédure de secours S1 à S5.

## Contexte

La remise électronique (V50, ADR-0010) prépare la fiche, mais rien ne reçoit encore une offre. Les lots 2 (cérémonie
des clés), 3 (dépôt) et 4 (ouverture) reposent sur un même choix : **comment une offre est scellée**, de sorte que :

1. avant l'heure d'ouverture, personne ne puisse la lire : ni le serveur, ni un administrateur de la base ou du
   serveur, ni un membre seul, ni moins de `quorum` membres ensemble ;
2. toutes les offres d'une procédure s'ouvrent ensemble, en séance ;
3. le serveur refuse toute contribution à l'ouverture avant `B04-OP-02` / `B04-OP-03` ;
4. l'intégrité soit prouvable (empreinte SHA-256 du contenu chiffré dans l'accusé de réception, vérifiée à l'ouverture) ;
5. aucun secret en clair (clé d'offre, clé privée, phrase secrète) ne transite ni ne se stocke avant l'ouverture ;
6. la procédure de secours S1 à S5 soit réalisable ;
7. aucune cryptographie ne soit écrite à la main : WebCrypto dans le navigateur, JCA/JCE au serveur, et une
   bibliothèque auditée pour le partage de secret.

L'option « clé gardée par le serveur » est écartée par le pilote.

## Décision

### 1. Un partage par offre, jamais une clé de procédure

La proposition du front (§B1.2) est **retenue**. La clé d'une offre n'existe entière que chez le candidat, qui a déjà son
offre en clair. Il n'y a pas de clé de procédure, ni de poste de cérémonie qui la verrait passer.

- **Cérémonie (lot 2).**
  - Chaque membre désigné (`membresCommission`) et le dépositaire de secours génèrent **dans leur navigateur** une
    paire de clés.
  - La clé privée est enveloppée par une clé dérivée de la **phrase secrète** du détenteur (§3). Elle est remise au
    serveur sous cette forme (copie qu'il ne peut pas lire), et gardée hors ligne par le détenteur : un fichier pour un
    membre, une impression sous pli scellé pour le dépositaire.
  - Seules les **clés publiques** et leurs **empreintes** (SHA-256 de la forme SPKI, en hexadécimal) sont publiées par
    la procédure. Chaque détenteur voit sa propre empreinte à la cérémonie, et la retrouve dans la liste publiée.
- **Dépôt (lot 3), dans le navigateur du candidat.**
  1. Une clé `K` est tirée pour cette offre : AES-256-GCM, `crypto.getRandomValues`.
  2. Le contenu est chiffré **par morceaux**. Chaque morceau a son vecteur d'initialisation, et ses données
     authentifiées portent l'identifiant de l'offre, la version du conteneur, son rang et un drapeau « dernier
     morceau » : on ne peut ni retirer, ni permuter, ni tronquer un morceau (§4).
  3. `K` est **partagée** (Shamir, §2) en `n` parts, seuil `quorum` : `n` = nombre de membres + 1 (la part de secours).
  4. Chaque part est chiffrée pour un seul détenteur, avec sa clé publique.
  5. Le navigateur envoie le conteneur. `K` et les parts en clair sont oubliées.
- **Ouverture (lot 4).**
  - Chaque membre présent déverrouille sa clé privée dans son navigateur, déchiffre **sa** part de chaque offre et
    envoie ses parts en une fois.
  - Le serveur **refuse** tout envoi avant l'heure d'ouverture (409, code à fixer au lot 4), sur son horloge de
    référence. Une part déposée après cette heure ne protège plus rien qu'il faille encore protéger.
  - Le serveur ne reconstitue rien tant que le quorum n'est pas atteint **pour toutes les offres** de la procédure.
    Il reconstitue alors chaque `K`, vérifie l'empreinte, déchiffre, et oublie `K` et les parts, **toutes les offres
    dans le même geste** (exigence 2).

### 2. Le partage de secret : `shamir-secret-sharing`, et la reconstitution au serveur sous condition

- **Navigateur** : `shamir-secret-sharing` (Privy, licence Apache-2.0), GF(2^8), dérivée de l'implémentation de
  HashiCorp Vault. Le dépôt de la bibliothèque annonce **deux audits indépendants, Cure53 et Zellic**, dont les rapports
  sont publiés. Le front ajoute cette dépendance npm, à une version figée.
- **Reconstitution de `K` : au serveur**, comme le front le propose. Le serveur produit le PV, et il ne reconstitue
  qu'après l'heure d'ouverture. Il le fait avec **BouncyCastle** (`bcprov`, ≥ 1.80), qui fournit le partage de Shamir
  (`org.bouncycastle.crypto.threshold`, algorithmes OASIS, corps GF(2^8) au polynôme d'AES). C'est une bibliothèque
  reconnue ; son paquet de partage est récent.
- **Condition, à lever au début du lot 4** : un test d'**interopérabilité** passe en CI. Des parts produites par
  `shamir-secret-sharing` (vecteurs générés par le front, avec l'abscisse portée par le dernier octet de chaque part)
  doivent être recombinées par BouncyCastle en la même clé. Les deux bibliothèques travaillent dans GF(2^8), mais leur
  identité de corps et de format **n'est pas documentée** : seul ce test la prouve.
- **Repli, si le test échoue** : la recombinaison se fait dans le navigateur du responsable de séance, avec la même
  bibliothèque que le dépôt. Il reçoit les parts déchiffrées, déjà refusées avant l'heure par le serveur, et renvoie `K`
  au serveur, qui déchiffre et vérifie. Aucune recombinaison n'est **jamais** écrite à la main en Java.
- **Ajout de dépendance** (`pom.xml`) : `org.bouncycastle:bcprov-jdk18on`, au lot 4 seulement. L'ADR seule n'en ajoute
  aucune.

> ⚠️ **2026-10-04, lot 4 (V69) — la condition est levée.** `DechiffrementOffreTest` (CI) recombine par BouncyCastle
> **`bcprov-jdk18on` 1.86** (`org.bouncycastle.crypto.threshold`, GF(256) au polynôme de l'AES) toute paire de parts produites par
> **`shamir-secret-sharing` 0.0.4** (vecteurs du front, `src/test/resources/scellement/`), puis déchiffre le conteneur du front de bout en
> bout (parts RSA-OAEP SHA-256 / MGF1-SHA-256, `K`, morceau AES-256-GCM et ses données authentifiées, empreintes). La reconstitution se
> fait donc **au serveur** ; le repli (navigateur du responsable) n'est pas construit. Précision constatée sur les vecteurs : la part fait
> 33 octets, l'**abscisse en dernier octet**, et les abscisses sont **tirées au hasard** (permutation de 1 à 255), non « rang + 1 ». Si ce
> test casse un jour (montée de version d'une des deux bibliothèques), la reconstitution au serveur perd son droit d'exister.

### 3. Les clés des détenteurs : RSA-OAEP 3072, SHA-256

- **RSA-OAEP, module de 3072 bits, SHA-256 avec MGF1-SHA-256.**
  - Il est natif dans WebCrypto (génération, chiffrement d'une part, déchiffrement), et dans la JCA de Java 21 :
    `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`, avec un `OAEPParameterSpec` explicite qui fixe MGF1 à SHA-256, sans quoi
    Java prend SHA-1 pour MGF1 et ne s'accorde plus avec WebCrypto.
  - Une part (32 octets + 1) tient en un seul bloc.
- **ECDH P-256 + HKDF + AES-KW est écarté** : Java 21 n'a pas de HKDF dans la JCA (l'API de dérivation n'y entre qu'en
  Java 25). Il faudrait une bibliothèque de plus, ou écrire HKDF à la main, ce qui est exclu.
- **Enveloppe de la clé privée** : `wrapKey('pkcs8')` sous AES-256-GCM. La clé d'enveloppe est dérivée de la phrase
  secrète par **PBKDF2-SHA-256, 600 000 itérations**, avec un sel aléatoire de 16 octets propre à chaque détenteur. Le
  sel, le nombre d'itérations et le vecteur d'initialisation sont stockés avec l'enveloppe, pour permettre de relever
  le nombre d'itérations plus tard.
- **Phrase secrète** : **12 caractères au moins** ; l'écran recommande une phrase de quatre mots ou plus. Elle ne quitte
  jamais le navigateur.

### 4. Le conteneur d'une offre (version 1)

- **En-tête** (JSON, en clair, authentifié par les données authentifiées de chaque morceau via son empreinte) :
  - `version` : 1 ;
  - `idOffre`, `idDmc`, `lot` ;
  - `algorithmes` : `AES-256-GCM`, `RSA-OAEP-3072-SHA256`, `SHAMIR-GF256` ;
  - `tailleMorceau` (4 Mio, le dernier plus court), `nombreMorceaux` ;
  - `quorum`, `n` ;
  - `parts` : pour chaque détenteur, l'empreinte de sa clé publique et la part chiffrée (base64).
- **Morceaux** : pour chacun, vecteur d'initialisation (12 octets), chiffré, étiquette de 16 octets. Données
  authentifiées : `idOffre | version | rang | dernier (0/1) | SHA-256(en-tête)`.
- **Empreinte de l'accusé de réception** : SHA-256 de l'en-tête et des morceaux, dans l'ordre. Le navigateur la calcule
  avant l'envoi, le serveur la recalcule à réception, et les deux doivent coïncider. Elle figure dans l'accusé, et elle
  est recalculée à l'ouverture : une différence est signalée au PV (exigence 4).
- **Taille** : jusqu'à 500 Mo (`B04-SE-09`), à travers l'envoi par morceaux du lot 3.

### 5. La procédure de secours S1 à S5

- **S1 — La marge du quorum.** La règle 6 de V50 (2 ≤ quorum ≤ membres) reçoit un **avertissement**, non bloquant, quand
  le quorum égale le nombre de membres : « Le quorum est égal au nombre de membres : la perte d'une seule part rendrait
  les offres illisibles. » La part de secours ne compte pas dans ce calcul.
- **S2 — La vérification d'une part, sans rien révéler.** C'est un défi :
  - le serveur tire 32 octets aléatoires et les chiffre avec la clé publique du détenteur ;
  - le détenteur les déchiffre dans son navigateur, avec sa phrase secrète, et les renvoie ;
  - le serveur compare en temps constant.

  Rien n'est révélé : le défi n'a aucun lien avec les offres. Quand le nombre de membres vérifiés tombe au quorum, le
  responsable est alerté.
- **S3 — La part de secours.**
  - Le dépositaire a une paire de clés comme un membre. Chaque offre lui chiffre une part.
  - Sa clé privée enveloppée est imprimée et mise sous pli scellé.
  - Il ne compte que pour **une** part : il n'atteint jamais seul le quorum, puisque quorum ≥ 2.
  - Son emploi est consigné au PV, avec le motif.
- **S4 — Remplacer une clé.**
  - Avant le premier dépôt : on remplace la clé du seul membre concerné, ou on refait la cérémonie, sans rien perdre.
  - Après le premier dépôt : seules les offres déjà scellées pour l'ancienne clé en dépendent, et S1 et S3 les couvrent.
    Les offres suivantes sont scellées pour la nouvelle clé publiée.
  - Chaque offre garde dans son en-tête l'empreinte des clés pour lesquelles elle a été scellée.
- **S5 — Le dernier recours.** Si le quorum ne peut plus être atteint, part de secours comprise, la séance constate au PV
  que les offres sont illisibles, et la procédure est relancée. Les suites juridiques, notamment le sort des garanties,
  sont à écrire par le juriste.

### 6. Le sort des paramètres de V50

- `membresCommission` et `quorum` sont les entrées du partage : `n` = membres + 1, seuil = quorum.
- `dateCeremonie` reste la date de publication des clés publiques. La règle 8 (la cérémonie précède la publication) est
  inchangée.
- **La part de secours ne compte pas dans `nombreParts`**, qui reste le nombre de membres. Elle a son champ propre dans
  `ParametresInternesDto`, `partDeSecours: { depositaire, etat }`, comme le front le propose. Le dépositaire est **à
  désigner** (question au juriste ; l'ARMP est une piste). Tant qu'il ne l'est pas, les lots 2 à 4 ne s'ouvrent pas.

> ⚠️ **2026-10-04, lot 2 (V66).** La structure de la part de secours est fixée sans attendre le choix de l'organisme :
> `depositaire { nom, organisme, fonction, contact }` sur les paramètres internes, règle 12 `SE_DEPOSITAIRE` bloquante en
> remise électronique, `partDeSecours.etat` ∈ `A_DESIGNER` · `DESIGNE` · `PUBLIEE` · `VERIFIEE` · `PERDUE`. Seule la
> **valeur** attend le juriste (question 1). La cérémonie, S1 à S4 et la garde de l'avis (`CEREMONIE_NON_CLOSE`) sont livrées
> dans `docs/api-endpoints.md`, § *La cérémonie des clés et la procédure de secours S1 à S4 — V66*.
>
> **Q11 (décision du pilote du 2026-10-04)** : les détenteurs de parts sont les membres de la **commission d'appel
> d'offres** désignés par la PRMP, pas les contrôleurs de la CNM de V50 / ADR-0010. Le §6 ci-dessus (« `membresCommission`
> et `quorum` sont les entrées du partage ») reste vrai, mais `membresCommission` sera **dérivé de la CAO** par le lot 2a
> (`demande-backend-2026-10-04-commission-appel-offres.md`), qui amendera l'ADR-0010.

### 7. Le stockage des offres chiffrées (question 6)

- Les conteneurs vont sur **disque**, hors de la base, dans un répertoire configurable (`app.offres.repertoire`). Un
  fichier par offre est nommé par son identifiant. La base ne garde que l'en-tête, l'empreinte et le chemin.
- **Sauvegarde** : les conteneurs sont sauvegardés tels quels, chiffrés. Une sauvegarde volée n'est pas lisible sans le
  quorum.
- **Durée de conservation** : celle des pièces du dossier de marché, à fixer avec le juriste. Les contenus déchiffrés à
  l'ouverture suivent le régime des pièces du dossier.

## Conséquences

- **Plus facile.**
  - Aucune clé de procédure n'existe : il n'y a ni poste de cérémonie à protéger, ni clé à faire circuler.
  - La perte d'une part avant le premier dépôt se répare pour un seul membre (S4).
  - La vérification d'une part (S2) ne révèle rien.
  - Le serveur, sa base et ses sauvegardes ne détiennent rien qui permette de lire une offre avant l'ouverture.
- **Plus difficile.**
  - Le dépôt fait tout le travail cryptographique dans le navigateur : chiffrement par morceaux de 500 Mo, partage,
    `n` chiffrements RSA.
  - L'ouverture demande que chaque membre déverrouille sa clé en séance.
  - Deux bibliothèques de partage doivent s'accorder (§2) ; c'est le premier test du lot 4.
- **Limites, à dire franchement.**
  - Le code qui chiffre dans le navigateur est **servi par le serveur**. Un attaquant qui modifierait ce code au
    déploiement pourrait détourner les clés avant le chiffrement. Le scellement protège contre la lecture de ce qui est
    **stocké** (base, disque, sauvegardes, administrateur), pas contre un code servi compromis. La défense est celle de
    tout déploiement : intégrité de la chaîne de construction, empreintes des fichiers servis et journal des
    déploiements.
  - De même, le serveur publie les clés publiques : un serveur malveillant pourrait y glisser la sienne. C'est pourquoi
    chaque détenteur contrôle son empreinte dans la liste publiée, et pourquoi l'en-tête de chaque offre les garde.
  - `quorum` membres qui s'entendent **avant** l'ouverture peuvent lire les offres. C'est la nature d'un seuil, comme
    une commission physique ; le refus du serveur avant l'heure ne s'applique qu'aux parts qu'on lui envoie.
- **Marche arrière.** Le conteneur porte sa version : un changement d'algorithme (ML-KEM, X25519 quand il sera partout)
  ouvre une version 2, sans toucher aux offres scellées en version 1.

## Questions qui restent ouvertes

| # | Question | À qui |
|---|---|---|
| 1 | Le dépositaire de la part de secours ; le sort des garanties en S5 | juriste, par le pilote — ⚠️ 2026-10-04 : dépositaire **libre**, par procédure (arbitrage du pilote, V70 §B5) ; le sort des garanties en S5 reste ouvert |
| 2 | ~~La durée de conservation des conteneurs et des contenus déchiffrés~~ — ⚠️ 2026-10-04 : **fixée par l'Administrateur** (`OFFRE_CONSERVATION_ANNEES`, nulle = sans limite), depuis la clôture de la séance ; purge par un geste de l'Administrateur, journalisée (V70 §B4.2) | pilote |
| 3 | ~~L'interopérabilité `shamir-secret-sharing` / BouncyCastle~~ — ✅ levée le 2026-10-04 (`DechiffrementOffreTest`, BouncyCastle 1.86) | backend |
