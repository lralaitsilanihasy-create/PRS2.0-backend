# Règles de gestion — Application CNM (Contrôle des Marchés Publics)

> Document de référence des règles métier, extrait fidèlement de la brochure
> *CNM — Fonctionnalités par profil* (v2.x). À utiliser pour la conception du
> backend (Spring Boot) **et** du frontend (Angular). Le backend reste l'autorité
> qui applique réellement ces règles ; le frontend les reproduit pour l'UX.

Périmètre : 8 profils, hiérarchie des contrôleurs, délégations et visibilité par localité.

---

## 1. Hiérarchie des contrôleurs et visibilité

La hiérarchie et le périmètre de visibilité (rattachement à une localité) conditionnent
ce que chaque utilisateur peut voir et faire.

1.1. **Président** — `ID_LOCALITE = NULL` → voit **toutes** les localités. Peut exercer les tâches de Secrétaire, Membre et Vérificateur.
1.2. **Chef de commission (CC)** — rattaché à une localité ; voit **uniquement** les dossiers de sa localité ; dispose de son Secrétaire et de ses Membres ; peut exercer les tâches de Secrétaire, Membre et Vérificateur.
1.3. **Secrétaire** — subordonné du CC ; même localité que son CC ; en charge de la réception des dossiers.
1.4. **Membre** — subordonné du CC ; voit tous les dossiers de sa localité.
1.5. **Contrôleur vérificateur** — subordonné du Membre ; même localité.
1.6. **Assistant contrôleur** — subordonné du Vérificateur ; même localité.

1.7. ⚠️ **Rattachement nominatif (2026-09-01)** — indépendamment de la hiérarchie ci-dessus, chaque
**Membre** peut désigner **son** Vérificateur, et chaque **Vérificateur** **son** Assistant
(`t_controleur.IM_RATTACHE`, une seule colonne pour les deux liens). C'est ce lien — et non
`ID_SUPERIEUR` — qui détermine **qui est notifié** et **quel dossier apparaît comme « le mien »**.

> **Ne pas confondre `ID_SUPERIEUR` et `IM_RATTACHE`.** Le premier est la ligne hiérarchique
> (organigramme, qui rend compte à qui) ; le second est l'**aiguillage opérationnel** d'un dossier
> le long du circuit. Ils peuvent désigner des personnes différentes, et rien n'impose qu'ils
> coïncident.
>
> Le rattachement se pose par `PUT /api/controleurs/{im}/rattachement`, réservé à l'**Administrateur**,
> au **Président** et au **Chef de commission** (ce dernier **dans sa seule localité**). Le rattaché doit
> avoir le profil attendu (Vérificateur pour un Membre, Assistant pour un Vérificateur), être de la
> **même localité**, et ne pas être le porteur lui-même — sinon **409**.
>
> ⚠️ **Un rattachement cible, il ne verrouille pas.** Une chaîne **incomplète** (`IM_RATTACHE` nul) est
> un état **normal et non bloquant** : le **repli localité** historique s'applique alors — tous les
> Vérificateurs de la localité sont notifiés et peuvent agir. Aucune garde d'autorisation n'a été
> ajoutée par cette règle : même rattaché, un dossier reste actionnable par tout Vérificateur de sa
> localité. Le rattachement sert la **notification** et l'**affichage**, pas l'exclusivité.


> Règle transversale : la visibilité des dossiers **des contrôleurs** est filtrée par `ID_LOCALITE`,
> sauf pour le Président (`ID_LOCALITE = NULL`) qui voit tout. La **PRMP** (acteur externe) n'est
> **pas** scopée par localité : elle ne voit que **ses propres** dossiers (propriété `t_dossier.ID_PRMP`).

---

## 2. Circuit de contrôle (workflow)

Flux complet d'un dossier, avec navette du projet de PV :

1. **Réception** — acteurs : Secrétaire / CC / Président
2. **PRET_DISPATCH** — déclenchement automatique (trigger auto, dès `COMPLET = true`)
3. **Dispatch** — acteurs : Président / CC
4. **Examen** — acteurs : Membre / CC / Président
5. **Projet de PV** — rédigé par le Membre ; navette (aller-retour) possible
6. **PV accepté & signé** — co-signature Président/CC + Membre
7. **Vérification** — acteur : **Contrôleur vérificateur** (strict, ⚠️ règle ajoutée) — **pour tout avis** (⚠️ corrigé 2026-08-27, voir encadré ci-dessous — pas seulement `FAVR`)
8. **Transmission SIGMP** — acteur : **Contrôleur vérificateur** — transmet le sens de la décision à SIGMP
9. **Archivage & clôture** — acteur : **Assistant contrôleur** — archive le PV, ce qui clôt le dossier (⚠️ corrigé 2026-08-27 — la clôture n'est plus automatique à la signature, voir encadré ci-dessous)

> Statuts de navette du PV : `PROJET_PV_SOUMIS`, `PROJET_PV_RETOUR`, `PROJET_PV_ACCEPTE`, puis `SIGNE`.

> ## ⚠️ RÉFORME « VISA UNIQUE » (arbitrage du pilote, 2026-08-31) — fait autorité sur la navette
>
> **Énoncé du pilote** : « Le Membre qui fait l'examen du dossier émet son avis à la fin de l'examen.
> Cet avis peut être modifié à la fin de la navette, qui finit par le visa du Président ou du CC **qui
> a fait le dispatch**. Le visa consiste à choisir le co-signataire et à faire sa part de signature. »
>
> Cette réforme **inverse la règle du 2026-08-01** (l'avis était posé par le P/CC à l'acceptation, le PV
> naissait sans avis) et **fusionne** l'acceptation et la signature du P/CC en un geste unique.
>
> | | Avant (01/08 + co-signature du 28/08) | Depuis le 31/08 |
> |---|---|---|
> | Soumission de l'examen (Membre) | synthèse seule, avis `NULL` | synthèse **+ avis du Membre** |
> | Navette (`retourner` / re-soumettre) | inchangée | inchangée — l'avis peut être ajusté à chaque cycle |
> | Clôture (P/CC) | `accepter` puis `signer(role=PRESIDENT|CC)` | **`viser`** : avis + co-signataire + part du rôle |
> | Part Membre | `signer(role=MEMBRE)` par le désigné | inchangée → `SIGNE` |
>
> **Le cycle d'états ne change pas.** `PROJET_ACCEPTE` devient « visé, en attente de la co-signature du
> Membre désigné » ; `DATE_ACCEPTATION` est la date du visa.
>
> **⚠️ Contrainte d'IDENTITÉ, pas de profil.** Seul le **dispatcheur** (`IM_CTRL_DISPATCH` du dispatch de
> l'examen) vise — **403 sinon, y compris couvert par une paire de délégation active**. C'est la ligne de
> l'invariant du 2026-08-15 : la délégation ascendante autorise à exercer une **tâche de profil**, jamais
> à endosser l'**identité** d'un autre. Viser, comme signer, atteste.
> Conséquence assumée : dispatcheur indisponible ⇒ PV non visable ; le déblocage est un **re-dispatch**,
> qui met `IM_CTRL_DISPATCH` à jour sur la même ligne (le `PUT` le repose depuis le JWT, comme le `POST`).
>
> **`retourner` reste une tâche de RÔLE** (P/CC de la localité), délégable. Asymétrie voulue : un visa
> bloqué gèle la clôture d'un PV, un retour bloqué gèlerait **la navette entière** — le Membre ne pourrait
> plus récupérer son projet pour le corriger. Retourner instruit ; viser atteste.
>
> **Contrat.** `POST /api/pv-examens/{id}/viser` — `imMembreCoSignataire` obligatoire (**400**) ;
> `idAvis` optionnel (absent = avis du Membre conservé ; fourni = il le remplace, cohérence revalidée).
> `POST /{id}/accepter` est **retiré (410 Gone)** ; `signer` ne porte plus que le rôle `MEMBRE`
> (`PRESIDENT`/`CC` → **409** orientant vers `viser`). `PV_A_VALIDER` ne cible plus que le dispatcheur.
>
> **Garde reconduite sans changement** : Membre co-signataire (2026-08-28 : Membre titulaire de la
> localité, ≠ acteur). ⚠️ Le **Secrétaire de séance a été retiré du visa** le 2026-09-02 (§ dédié).
>
> **Transition.** Un PV `PROJET_ACCEPTE` dont la part du rôle n'est pas signée (accepté sous l'ancien
> contrat) reste **visable** ; un PV en navette sans avis exige que le visa en fournisse un (**409**).
>
> **Trou fermé au passage** : jusqu'au 31/08, un avis fourni à la soumission était posé **sans aucun
> contrôle** — `validerCoherenceAvis` n'existait que dans `accepter`. Un Membre pouvait soumettre `FAV`
> avec des observations relevées. La garde s'applique désormais à la soumission, au **PUT** du PV (canal
> par lequel le Membre change d'avis en rectification) **et** au visa. ⚠️ **2026-09-01** : `idAvis` est
> devenu **obligatoire** à `POST /api/examens/{id}/soumettre` (400 sinon) — la fenêtre de compatibilité
> du lot 1 est refermée.
>
> ### ⚠️ VISA PAR INTÉRIM (arbitrage du pilote, 2026-09-01)
>
> **Énoncé** : « Le P/CC non dispatcheur peut effectuer le visa en cas d'absence du dispatcheur. Cette
> absence est justifiée par une note d'intérim. »
>
> La contrainte d'identité reste la règle ; l'intérim en est **l'exception justifiée**. C'est le pendant,
> à la clôture, de l'`INTERIM_DISPATCH` du dispatch — mais avec **pièce justificative**, et **sans** levée
> de la garde de localité : un CC ne supplée que dans SA localité, seul le Président supplée partout.
>
> **La note est téléversée** (PDF, multipart sur `viser`, parties `data` + `noteInterim`), auto-déclarée
> par l'intérimaire **au moment du visa** : aucun écran préalable, l'absence du dispatcheur ne bloque
> jamais le circuit. Le type est reconnu sur les **octets d'en-tête**, jamais sur le nom du fichier.
>
> **⚠️ Le refus d'un P/CC non dispatcheur passe de 403 à 400.** Il n'est plus interdit de viser : il lui
> manque une pièce. Le 403 ne subsiste que pour ce qui reste structurellement impossible.
>
> | Acteur | Code |
> |---|---|
> | P/CC non dispatcheur, bonne localité, **sans** note | **400** « note d'intérim requise » |
> | P/CC non dispatcheur, bonne localité, **avec** note PDF | **200** — visa posé, `viseParInterim = true` |
> | CC d'une **autre** localité | **403** — aucune note ne l'autoriserait |
> | Profil hors P/CC | **403** — la note ne crée pas l'habilitation |
>
> **L'ordre des gardes porte cette distinction** : identité → profil → **périmètre** → note. Le périmètre
> passe avant la note, pour ne pas réclamer à un CC hors localité une pièce qui ne débloquerait rien.
>
> **Aucune vérification de l'absence réelle du dispatcheur.** Le serveur ne peut pas la constater, et une
> garde invérifiable donne l'illusion du contrôle. La note EST la justification, sous la responsabilité du
> signataire — tracée, horodatée, versée au journal d'audit. Un dispatcheur présent peut donc recevoir un
> visa d'intérim ; sa note est alors simplement ignorée, le visa est normal.
>
> **Trace** : `VISE_PAR_INTERIM` + la note sur `t_pv_examen` (V11). Le signataire et la date ne sont pas
> redits : ils sont déjà portés par `IM_CTRL_PRESIDENT`/`IM_CTRL_CC` et `DATE_ACCEPTATION`.
>
> **Consultation de la note** : `GET /api/pv-examens/{id}/note-interim`, **fermée à la PRMP** (403).
> C'est un document d'organisation interne, pas un élément de la décision qui lui est notifiée — l'ouvrir
> rétablirait par une autre porte ce que la règle du document retire ci-dessous.
>
> **Mention sur le document — ⚠️ RÉVISÉ le 2026-09-01 (refonte du bloc VISA).** La première livraison
> avait posé la mention sous « Étaient présents », faute d'emplacement : le bloc VISA des 12 modèles ne
> portait que des légendes et **aucune ligne pour le P/CC**. Le pilote a tranché de **créer
> l'emplacement manquant**. Trois règles :
>
> | | Règle |
> |---|---|
> | **R1** | Le bloc VISA nomme le viseur sur **TOUS** les PV : « Visé par : NOM Prénoms, qualité » — visa normal comme intérim, Centrale comme régional. Un seul gabarit, pas deux rendus du même formulaire. |
> | **R2** | En **Centrale**, la ligne est là mais **jamais** la mention, intérim compris. L'arbitrage 4 tient : rien n'y révèle l'intérim. |
> | **R3** | La mention a **DÉMÉNAGÉ** : retirée de « Étaient présents », qui redevient une simple liste de présence. Un seul endroit fait foi, celui de l'acte. |
>
> La qualité est reprise **mot pour mot** du bloc « Étaient présents » du même document (« Président de
> la Commission Nationale des Marchés », « Chef de la Commission ») : aucun vocabulaire nouveau
> introduit dans un formulaire officiel.
>
> Les 12 modèles PV ont été dérivés en conséquence (les 2 lettres de renvoi ne sont pas concernées) —
> voir `docs/derivation-modeles-docx.md`.
>
> ⚠️ **Où elle apparaît, et pourquoi pas ailleurs.** L'arbitrage la voulait « sur la ligne de signature du
> P/CC », via les modèles régionaux. Vérification faite sur les 12 modèles : **cette ligne n'existe pas**.
> Le bloc de signature ne porte que des légendes (« VISA DU SUPERIEUR HIERARCHIQUE », « (Nom, prénoms,
> cachet et signature du membre en charge du dossier) ») — aucun placeholder de nom, aucun emplacement
> pour le P/CC, et **les modèles centraux et régionaux y sont identiques**. Le seul endroit où le P/CC est
> imprimé est le bloc « Étaient présents » ; la mention s'y pose, par le mécanisme même que citait
> l’arbitrage — celui de « (par délégation) » du Secrétaire de séance, qui atterrissait au même endroit
> (⚠️ ce mécanisme a disparu le 2026-09-02 avec la notion elle-même ; le paragraphe reste comme trace de
> la décision d’alors).
> Conséquence : **aucun `.docx` n'a été modifié**, et la condition de localité est en Java.

> ⚠️ **Règle CORRIGÉE (2026-08-27, audit — la clôture n'est PLUS automatique à la signature du PV, quel
> que soit l'avis).** Ce paragraphe a longtemps décrit une clôture automatique à la signature pour
> `FAV`/`DEF`/`NSP` ; ce n'est **plus le comportement réel depuis la spec navette du 2026-08-01**
> (`PvExamenService.brancherSelonAvis`) — l'audit du 27/08 l'a jugé « matériellement périmé » sur ce
> point précis. Le circuit réel, à la bascule `SIGNE` :
>
> 1. **Tous les avis** (`FAV`, `FAVR`, `DEF`, `NSP`) font passer le dossier en **`EN_VERIFICATION`** —
>    plus aucun avis ne clôture directement. Le PV est dans tous les cas **transmis à la PRMP**
>    (notification `PV_SIGNE`) et le **Contrôleur vérificateur** de la localité est notifié :
>    `PV_A_VERIFIER` (avis `FAVR`, boucle de rectification à ouvrir) ou **`DECISION_A_TRANSMETTRE`**
>    (`FAV`/`DEF`/`NSP` — le vérificateur n'a plus qu'à transmettre le sens de la décision à SIGMP).
> 2. Le **vérificateur transmet le sens de la décision à SIGMP** (`POST /api/sigmp-transmissions`,
>    dérivé serveur de l'avis : `FAV` → `APPROUVE`, `DEF`/`NSP` → `NON_APPROUVE` ; pour un `FAVR`, ce
>    n'est possible qu'après la boucle de vérification — observations toutes **levées**, statut
>    `OBSERVATIONS_LEVEES`) → le dossier passe **`DECISION_TRANSMISE_SIGMP`**, notification
>    `PV_A_ARCHIVER` aux Assistants contrôleurs de la localité.
> 3. **L'Assistant contrôleur archive le PV** (`POST /api/pv-examens/{id}/archiver`, réservé à sa
>    localité) : seul ce geste pose la **clôture** (`CLOTURE`) et déclenche `CLOTURE_ELIGIBLE`
>    (Chargé de publication).
>
> Le statut `PV_SIGNE` n'est donc **jamais un état de repos** du dossier (transitoire, immédiatement
> réécrit en `EN_VERIFICATION` dans la même transaction que la signature) : la clôture n'arrive
> qu'à l'**archivage**, plusieurs étapes et plusieurs acteurs après la signature. Statuts intermédiaires
> du circuit post-signature, absents des versions antérieures de ce document : **`OBSERVATIONS_LEVEES`**
> (avis `FAVR`, observations toutes levées, en attente de transmission SIGMP), **`DECISION_TRANSMISE_SIGMP`**
> (décision transmise, PV chez l'Assistant pour archivage). Voir aussi `EN_ATTENTE_PIECES` et
> `A_REEXAMINER` (cas 3, lettre de renvoi) et `EN_ATTENTE_COMPLEMENTS_DEPOT` (contrôle de complétude
> au dépôt) au Module 03.

> ⚠️ **Règle ajoutée (2026-08-19) — le PDF du PV est produit HORS du chemin de la signature.** La conversion
> .docx → PDF pilote Word localement (plusieurs secondes, incompressibles) : la signature finale marque le
> PV `SIGNE` et **répond immédiatement** ; le document officiel est généré **après commit, en tâche de
> fond**, qui renseigne `CHEMIN_DOCUMENT` quand il est prêt. Entre-temps `documentDisponible` est
> **false** (le front sait afficher un PV signé sans document) ; un échec de génération est journalisé et
> **ne fait jamais échouer la signature** — le téléchargement garde sa régénération paresseuse en filet, et
> la consultation d'un PV signé sans fichier (antérieur au correctif) relance la production en arrière-plan.
> Le convertisseur Word est **préchauffé au démarrage** (première génération sans coût de lancement de Word).

> ⚠️ **Règle ajoutée (non issue de la brochure d'origine) — statut `DISPATCHE`.** La brochure ne nomme
> aucun statut de dossier entre `PRET_DISPATCH` et `CLOTURE`. Pour matérialiser l'étape **Dispatch (3)**
> dans le pipeline, le backend ajoute le statut **`DISPATCHE`** (« dispatché, en attente d'examen ») :
> à la **création d'un dispatch**, le dossier passe **`PRET_DISPATCH` → `DISPATCHE`** (transactionnel).
> L'**examen (4)** exige désormais que le dossier soit **`DISPATCHE`** (et non plus `PRET_DISPATCH`).
> Portée : étape Dispatch → Examen uniquement. Le frontend doit s'aligner sur ce statut.

> ⚠️ **Règle ajoutée — statuts `EXAMINE` et `PV_SIGNE`.** Même principe que `DISPATCHE`, pour matérialiser
> **Examen (4)** et **PV signé (6)** : à la **signature du PV**, il passe **`EXAMINE` → `PV_SIGNE`**
> (transitoire, cf. encadré ci-dessus). Cycle complet : `… DISPATCHE → EXAMINE → PV_SIGNE →
> EN_VERIFICATION → … → DECISION_TRANSMISE_SIGMP → CLOTURE` (à l'archivage). Transitions
> transactionnelles et idempotentes.
>
> ⚠️ **Règle DÉPLACÉE (2026-08-01, corrigée dans ce document le 2026-08-27).** La transition
> **`DISPATCHE` → `EXAMINE`** ne se produit **plus à la création de l'examen** mais à sa **SOUMISSION**
> (`POST /api/examens/{id}/soumettre`, même transaction que la production du Projet de PV) — ce
> document l'a longtemps décrite à la création, ce qui ne correspond plus au code depuis cette date.
> La **création** d'un examen est un **brouillon de progression** : le dossier reste `DISPATCHE`, le
> Membre peut sauvegarder ses résultats point par point sans faire avancer le dossier, et reprendre plus
> tard. Le verrou d'écriture de l'examen et de ses détails accepte donc `DISPATCHE` (brouillon) **ou**
> `EXAMINE` (navette ouverte) — refus (409) dès `PV_SIGNE`, inchangé.
>
> - **Verrou de l'examen** : l'examen et ses détails (`t_examen_detail`) sont **modifiables** tant que le
>   dossier est `EXAMINE` (navette ouverte) ; toute modification est **refusée (409)** dès `PV_SIGNE`
>   (l'examen devient **définitif** à la signature).
> - **Attributaire** : un **Membre titulaire** n'examine que les dossiers **qui lui sont attribués**
>   (`Dispatch.imCtrlMembre`) — sinon **403** ; un CC/Président instruisant **par délégation** (§3.5)
>   reste autorisé.
> - **Deux listes exclusives** (scopées au Membre attributaire) : « **à examiner** » = ses dossiers
>   `DISPATCHE` ; « **examinés** » = ses dossiers `EXAMINE` / `PV_SIGNE` / `CLOTURE` (historique, paginé).

### Notifications (transversal au circuit)

À **chaque transmission**, le système émet une **notification** au(x) responsable(s) de l'étape suivante,
**dans la même transaction** que l'événement :

- **soumission** du dossier → Secrétaire / CC de la localité (`DOSSIER_SOUMIS`) ;
- **dossier complet** → Président + CC de la localité (`PRET_DISPATCH`) ;
- **dispatch** → **Membre assigné** (`EXAMEN_A_FAIRE`) ;
- **projet de PV soumis** → CC + Président de la localité (`PV_A_VALIDER`) ;
- **navette retour (commentaire) / acceptation** → **Membre auteur** (`PV_A_RECTIFIER` / `PV_ACCEPTE`) ;
- **PV signé** → PRMP (`PV_SIGNE`) ; **clôture éligible** → Chargé de publication (`CLOTURE_ELIGIBLE`) ;
- **message** de la messagerie interne → son destinataire (`NOUVEAU_MESSAGE`).

Le destinataire est déterminé par **rôle + localité** du dossier (ou par **assignation explicite**, ex. le
Membre du dispatch). Chaque utilisateur ne consulte que **ses** notifications (`/api/notifications/mes`,
comptage des non-lues, marquer lu) ; la **liste globale** est réservée à l'**Administrateur** (supervision).

> ⚠️ **Ciblage nominatif des notifications (2026-09-01).** Deux destinataires, jusqu'ici diffusés à
> **tous** les porteurs du rôle dans la localité, sont désormais **adressés** quand la chaîne de
> rattachement (§1.7) est complète :
>
> - **PV signé → à vérifier** (`PV_A_VERIFIER`) : le **rattaché du Membre qui a EXAMINÉ** le dossier.
>   Explicitement **pas** le co-signataire du PV — co-signer est un acte de PV, examiner est l'acte qui
>   attribue le dossier.
> - **Archivage** : le **rattaché du Vérificateur ayant EFFECTIVEMENT transmis** à SIGMP — pas le
>   Vérificateur nominalement cible. Si le remplaçant a fait le travail, c'est **son** Assistant qui
>   archive. À défaut de transmission identifiable, on retombe sur le rattaché du Vérificateur cible.
>
> **Repli sans exception** : chaîne incomplète, examinateur introuvable, rattaché supprimé → diffusion à
> **tous** les porteurs du rôle dans la localité, exactement comme avant. Un rattachement manquant ne
> fait **jamais** disparaître une notification.

### Le Secrétaire de séance est retiré du cycle du PV (règle du pilote, 2026-09-02)

⚠️ **La notion disparaît, désignation comprise.** Depuis les **rattachements Membre → Vérificateur →
Assistant** (§1.7, 2026-09-01), la boucle de vérification est routée par les **chaînes nominatives** : le
Secrétaire de séance n'avait plus qu'un rôle **documentaire** — une ligne sous « Étaient présents ». Le
pilote retire cette ligne et la désignation qui la nourrissait.

**Les deux points de désignation tombent** — c'est le point à retenir, la notion vivait à deux endroits :

- **au visa** (`POST /api/pv-examens/{id}/viser`) : le champ était obligatoire, la garde 400 est retirée ;
- **à la soumission de l'examen** (`POST /api/examens/{id}/soumettre`) : le champ y était optionnel mais
  **validé** ; il est désormais ignoré.

Les **gardes d'éligibilité** associées (Vérificateur titulaire de la localité, ou paire
« → Vérificateur » active — règle élargie du 2026-08-15) sont **retirées avec la notion**. Une garde sans
objet qu'on laisse en place devient une règle qui dérive en silence.

**Tolérance.** Un client non à jour qui envoie encore le champ n'est **pas refusé** : la valeur est
**ignorée**, jamais écrite. Un matricule fantaisiste ne déclenche plus aucun refus — un champ ignoré ne
peut pas être invalide.

**Documents.** La ligne « Secrétaire de séance : … » ne s'imprime plus, mention « (par délégation) »
comprise. Les **12 modèles PV** ont été re-dérivés sans elle, et le générateur supprime tout paragraphe
qui la porterait encore — un modèle mal re-dérivé n'imprimera jamais un marqueur brut à la place d'un nom.

**Ce qui reste, et pourquoi.** La colonne `SECRETAIRE_SEANCE` n'est **pas purgée** et le DTO continue de
l'exposer **en lecture** : les PV visés **avant** la règle gardent leur secrétaire. Un PV est un acte
officiel — on ne réécrit pas son contenu a posteriori. **Aucune migration.** Un PV visé après le
déploiement porte `null`.

⚠️ **Un PV antérieur RÉGÉNÉRÉ n'imprime plus la ligne**, alors qu'il porte encore un secrétaire en base.
Décision assumée : le PDF déjà archivé fait foi, et un document réédité reflète la règle en vigueur.

### Chronométrage et prévision des délais (transversal au circuit)

⚠️ **Règle du pilote (2026-09-01)** — la PRMP doit connaître la **date prévisionnelle d'achèvement** du
traitement de son dossier. Chaque tâche affectée à un profil est chronométrée ; le **compteur global**
court de l'**enregistrement** du dossier à la **validation sur SIGMP**.

**Huit étapes**, chacune avec un porteur, un statut d'éligibilité et un **geste métier de clôture qui
existe déjà** — la fin d'une tâche n'est jamais saisie, elle se déduit de l'acte que le porteur pose de
toute façon : `RECEPTION` (Secrétaire), `DISPATCH` (P/CC), `EXAMEN` (Membre), `VISA` (dispatcheur ou son
intérimaire), `COSIGNATURE` (Membre), `VERIFICATION` (Vérificateur), `TRANSMISSION_SIGMP` (Vérificateur),
`ARCHIVAGE` (Assistant — chronométré mais **hors compteur global**).

- **Prise en charge = geste EXPLICITE.** Le porteur ouvre sa tâche et saisit sa prévision en jours
  ouvrés. Le temps d'attente **avant** la prise en charge est ainsi mesuré lui aussi. La prévision reste
  corrigeable tant que la tâche est ouverte.
- ⚠️ **Le chronométrage n'empêche JAMAIS le métier.** Un geste de clôture posé sans prise en charge
  préalable n'est pas bloqué : l'occurrence est créée avec une durée nulle et la prévision standard. Un
  chronomètre qui bloquerait un dossier serait pire que pas de chronomètre.
- **Étapes rejouables** : réexamen, nouvelle navette de visa, passage supplémentaire du Vérificateur dans
  la boucle FAVR — chaque occurrence est un enregistrement **distinct, append-only**, et la prévision se
  ressaisit à chaque fois. C'est ce qui rend visible le nombre d'aller-retours.
- **Jours ouvrés** : samedi et dimanche exclus, **jours fériés hors périmètre v1**. Le chronométrage est
  horodaté **à la seconde** ; seule la restitution convertit en jours ouvrés, pour que le jour où les
  fériés entreront dans le périmètre tout reste recalculable.

**Deux compteurs.** Le **brut** (enregistrement → SIGMP, à la lettre) et le **net CNM**, où les périodes
« balle chez la PRMP » sont **suspendues** — c'est le net qui juge la CNM. Statuts suspensifs, trois et
trois seulement : `EN_ATTENTE_COMPLEMENTS_DEPOT`, `EN_ATTENTE_PIECES`, `EN_ATTENTE_DECISION_PRMP`. La
« rectification des documents témoins » n'en est pas un quatrième : c'est exactement
`EN_ATTENTE_DECISION_PRMP`, pendant laquelle la PRMP corrige puis resoumet.

⚠️ **La vérification et la transmission SIGMP sont DEUX étapes.** Quand les observations ne sont pas
levées, le dossier passe à `EN_ATTENTE_DECISION_PRMP` **entre** les deux actes. Une tâche unique
enjamberait cette attente et ferait porter au Vérificateur le temps de la PRMP, alors que la règle veut
précisément qu'aucune tâche CNM ne coure pendant ces fenêtres.

**La date annoncée** = `aujourd'hui + reste(étape en cours) + Σ prévisions des étapes restantes`, en
jours ouvrés, **calculée entièrement serveur**. Les étapes non encore prises en charge comptent pour leur
**délai standard** (référentiel administrable), d'où une date disponible **dès la soumission**. Une étape
en dépassement compte **0** : la date **glisse** au lieu de promettre un rattrapage qui n'aura pas lieu.
Pendant une attente PRMP la date reste calculée, accompagnée du drapeau `attentePrmp`.

> **Transition** : la base ayant été réinitialisée le 01/09, aucune reprise d'historique. Les dossiers
> créés après le déploiement sont chronométrés dès leur soumission.

### Actualités à l'ouverture de session (transversal)

⚠️ **Règle ajoutée (2026-08-19, spec du 2026-08-18)** — un **modal d'actualités** (mini-page markdown +
images) s'affiche à **chaque** ouverture de session des utilisateurs **ciblés par leur profil**, éditable
par l'Administrateur **sans redéploiement** (`t_actualite`, `t_actualite_profil`, `t_actualite_image`).
Une actualité est servie par `GET /api/actualites/mes-actualites` si **toutes** les conditions tiennent
(filtrage **entièrement serveur**, profil issu du JWT/cookie) : interrupteur global `ACTUALITES_ACTIVES`
à `true` (`t_parametre` — ligne absente = actif, coupe-circuit) ; statut `ACTIF` ; profil de l'utilisateur
parmi les **profils cibles** (au moins un, jamais « tous » implicitement) ; `datePublication` nulle ou
passée **et** `dateExpiration` nulle ou non atteinte (jour J compris = atteinte). Tri : **date de
publication effective décroissante** (publication, sinon création). Contenu **markdown brut** — toute
balise HTML est **refusée (400)** dès la saisie (la surface XSS soldée par l'audit du 16-17/08 ne se
rouvre pas). Images : **JPEG uniquement** (magic-bytes), **≤ 10 Mo (413 au-delà)**,
**redimensionnées au serveur** (largeur max 1600 px). Cycle de vie : création **INACTIF** forcé
(l'activation est un second acte délibéré) → `ACTIF`/`INACTIF` par le PUT → **archivage logique** par le
DELETE (jamais de suppression physique, onglet « Historique ») ; l'**expiration bascule automatiquement
en `ARCHIVE`** au fil des lectures (archiveur système = null) ; une actualité archivée n'est **plus
modifiable (409)**. Créations, activations, archivages et bascules de l'interrupteur sont **journalisés**
dans `t_audit_log` par l'intercepteur d'audit (comme toute écriture API).

---

## 3. Fonctionnalités et règles par profil

### 3.1. PRMP

- **Rôle** : Personne Responsable des Marchés Publics
- **Visibilité** : Acteur externe — aucun périmètre CNM

Acteur externe qui soumet ses PPM et marchés à la CNM. Suit l'avancement jusqu'au PV d'examen, peut demander le retrait motivé d'un dossier (soumis à validation du CC), et consulte ses indicateurs de performance par exercice. Son mandat est de 3 ans à compter de DATE_NOMIN — des alertes automatiques lui sont envoyées à J-90, J-30 et J-7 avant expiration.

**Rattachement aux entités contractantes**

- Une PRMP **gère plusieurs entités contractantes** (autorités contractantes), via la table
  `t_prmp_entite`. Chaque entité n'est rattachée qu'à **une seule PRMP active** à la fois
  (invariant d'unicité). Les affectations sont **créées et gérées par l'Administrateur** et restent
  **stables** (une entité reste rattachée à sa PRMP ; il n'y a pas de transfert d'une PRMP à une autre).
- La PRMP **n'a pas de localité propre**. La **localité d'un dossier** est déterminée par l'**entité
  contractante choisie à la saisie** (`tr_entite_contract.ID_LOCALITE`), jamais par la PRMP. À la
  saisie, la PRMP choisit une entité **parmi ses entités actives**.
- Le périmètre de visibilité de la PRMP est donc la **propriété** de ses dossiers
  (`t_dossier.ID_PRMP`), pas une localité.

**Mandats de la PRMP (⚠️ règle ajoutée — spec « Mandats PRMP »)**

Le mandat d'une PRMP est matérialisé par la table **`t_mandat`** (`/api/mandats`) :
`{idMandat, idPrmp, titulaire, dateDebut, dateFin, refArrete, statut, numeroMandat}`. Il est
**déclaré par l'Administrateur** — un arrêté de nomination ne se déclare pas soi-même.

- **Durée 3 ans.** `dateFin` vaut par défaut `dateDebut + 3 ans − 1 jour` et ne peut jamais excéder cette
  borne.
- **Une reconduction est un mandat distinct, jamais une prolongation.** Elle exige un **nouvel arrêté**
  (`refArrete` inédit) et des **dates nouvelles** qui **succèdent** au mandat précédent ; elle porte
  `numeroMandat = 2`. Il n'existe volontairement aucun moyen d'allonger un mandat existant : ni `PUT`, ni
  endpoint de prolongation.
- **Renouvellement unique.** Une même personne ne peut porter plus de **2** mandats : un 3ᵉ est refusé
  (**409**, message explicite). Une reconduction ne peut partir que d'un **1ᵉʳ** mandat.
- **Statuts** : `ACTIF` (en cours), `EN_TRANSITION` (nommé, pas encore en fonction), `ACHEVE` (terme
  atteint), `ABROGE` (fin avant terme, acte explicite qui prime sur les dates). Le statut est **dérivé des
  dates** à chaque lecture — un mandat périme dans le temps, pas au gré d'une écriture en base.
- **Reprise de l'existant** : une PRMP sans mandat déclaré se voit reconstituer un mandat **implicite**
  depuis `t_prmp` (`DATE_NOMIN` → `+ 3 ans`, arrêté = `ARRETE_NOMIN`). Aucune reprise de données n'est
  requise, et la règle d'expiration ci-dessus (§ « Mandat de 3 ans ») devient opposable telle quelle.
- ⚠️ **Réactivation automatique du compte à la reconduction (2026-08-27, audit lot B) — corrige un écart
  avec la promesse de « déblocage automatique » ci-dessous.** `AlerteScheduler.expirerComptesPrmp`
  **désactive** le compte d'authentification de la PRMP à l'échéance de son mandat (`t_compte_auth.ACTIF
  = false`) ; jusqu'à ce chantier, **rien ne le réactivait** à la reconduction — une PRMP valablement
  reconduite redevenait en fonction (la garde de vacance la laissait passer) mais **ne pouvait pas se
  connecter** sans intervention manuelle de l'Administrateur. Désormais, la **création d'un nouveau
  mandat** réactive le(s) compte(s) PRMP du titulaire, à condition que ce nouveau mandat soit **`ACTIF`
  à la date du jour** (une nomination à effet futur, `EN_TRANSITION`, ne rouvre rien) et que le compte
  soit déjà **validé** par l'Administrateur (statut d'inscription `ACTIF` — une inscription `EN_ATTENTE`
  ou `REFUSE` n'est jamais activée par ce mécanisme : une nomination ne vaut pas validation d'inscription).
  Un blocage manuel de l'Administrateur sur une PRMP que l'on reconduit est ainsi levé du même geste
  (le modèle ne distingue pas une désactivation d'expiration d'une désactivation manuelle — assumé).

**Standby de transition — vacance de PRMP (⚠️ règle ajoutée)**

- **Aucune obligation d'intérim.** S'il n'existe **aucun mandat actif à la date de l'action**, **toute
  action de traitement** côté PRMP / UGPM — création, édition, transmission de compléments, demande de
  retrait, et la **soumission** (acte de signature de la PRMP) — est **bloquée** : **409** avec le code
  dédié **`VACANCE_PRMP`** et le message « En attente de nomination de la nouvelle PRMP ». Le dossier
  attend, il ne bascule sur personne.
- **Déblocage automatique** dès qu'un mandat redevient actif : rien à rejouer, rien à débloquer à la main.
  L'action en attente est alors faite par le **nouveau titulaire en tant qu'opérateur** — l'attribution des
  dossiers reste **inchangée**.
- Le circuit interne CNM (réception, dispatch, examen, navette, signature du PV) n'est **jamais** suspendu
  par la vacance d'une PRMP.

**Attribution figée vs opérateur courant (⚠️ règle ajoutée)**

- Le dossier **fige son mandat d'attribution à la création** (`t_dossier.ID_MANDAT_ATTRIB`, aux côtés de
  `ID_PRMP`) et ne le **recalcule jamais**. Un changement de PRMP **ne réattribue rien** rétroactivement ;
  une mise à jour de PPM hérite de l'attribution de sa lignée.
- Chaque action porte au contraire l'**opérateur courant** — la PRMP **en fonction à la date de l'action** —
  consigné horodaté et par auteur dans **`t_action_dossier`**, lisible via
  `GET /api/dossiers/{id}/journal` par les profils concernés (périmètre de visibilité du dossier, §1). Ce
  journal métier est distinct de `t_audit_log` (trace technique, réservée à l'Administrateur).
- La **garde de propriété** accepte donc **deux titres** : la PRMP d'attribution **et** la PRMP en fonction
  sur le périmètre du dossier — celle qui a *à la fois* un mandat actif *et* une affectation active
  (`t_prmp_entite.ACTIF`) sur l'entité contractante du dossier. C'est ce second titre qui autorise la
  **reprise du traitement** des dossiers de l'UGPM par le successeur, sans changer l'attribution. Une PRMP
  en fonction **ailleurs** reste refusée (**403**).
- ⚠️ **Règle ajoutée (2026-08-19) — auteur de la saisie visible.** `t_dossier.CREE_PAR` (login de l'acteur
  ayant créé le dossier : PRMP **ou** UGPM de tutelle) et `SOUMIS_PAR` (PRMP seule) sont désormais **exposés**
  dans `DossierDto`, accompagnés de leur **nom lisible** résolu serveur (`creeParNom` / `soumisParNom`,
  « Nom Prénoms ») — le login n'étant pas l'identifiant de l'acteur, seul le serveur peut faire la jointure.
  Champs en **lecture seule** : posés à la création/soumission, toute valeur envoyée par le client est ignorée.
  Corollaire d'accès : `GET /api/ugpms/par-tutelle/{idPrmp}` est ouvert à la **PRMP concernée** (ses propres
  unités rattachées) ; une autre tutelle reste refusée (**403**).
- ⚠️ **Règle ajoutée (2026-08-20) — les contrôleurs voient l'unité de gestion qui a saisi le dossier.**
  `GET /api/ugpms/par-tutelle/{idPrmp}` est aussi ouvert aux profils **Président, Chef de commission,
  Secrétaire, Membre, Vérificateur, Assistant contrôleur**, pour **toute** tutelle et **sans filtre de
  localité** : ce sont eux qui instruisent le dossier et doivent savoir quelle unité l'a établi. Le filtre
  par localité est volontairement écarté — le répertoire des **PRMP** est déjà national pour tout
  authentifié, et l'UGPM **n'a pas de localité propre** (elle hérite de celles des entités contractantes
  actives de sa tutelle, éventuellement réparties sur plusieurs localités) : filtrer masquerait justement
  l'unité qu'un contrôleur d'une autre localité doit identifier. **Étendue** : hors Administrateur, la
  réponse est une **vue restreinte** (identité, matricule, libellé, courriel, téléphone) — **ni pièce
  d'identité (`cin`, `dateCin`, `lieuCin`) ni `login`**, réservés à l'Administrateur. Le Chargé de
  publication, hors instruction, n'est pas concerné (403), et le reste de la ressource UGPM demeure
  réservé à l'Administrateur.
- ⚠️ **Correctif (2026-08-26) — l'UGPM lit le périmètre de sa tutelle.** Une UGPM connectée voit en
  **lecture** ce que voit sa PRMP de tutelle : dossiers, PPM, marchés et lettres de renvoi signées
  (le `ref` de son jeton porte l'ID_PRMP de tutelle — quatre services testaient `profil == PRMP` à la
  main au lieu de la garde centrale `Visibilite.estPrmp()`, excluant l'UGPM de tout). Les **actions**
  réservées à la PRMP (soumission, resoumission, transmission de compléments, retraits) restent
  interdites à l'UGPM (403).
- ⚠️ **Décision (2026-08-27) — la lecture d'une lettre de renvoi est un suivi par agent, pas par
  tutelle.** Le correctif ci-dessus avait un effet de bord non voulu : la consultation d'une lettre
  par une UGPM marquait « lue » **pour la tutelle entière**, éteignant à tort le badge de sa PRMP
  alors que celle-ci n'avait rien consulté. Décision métier du PO, tranchée le 2026-08-27 : le
  marquage « lue » (`t_lettre_renvoi_lue`) devient **individuel**, identifié par le **login** du
  compte (colonne `LOGIN_AGENT`, migration `V7`) plutôt que par le seul `ID_PRMP` — la lecture d'une
  UGPM ne vaut plus lecture pour sa PRMP de tutelle, et réciproquement. `ID_PRMP` reste porté sur la
  trace comme **périmètre de tutelle** (la purge par dossier, qui passe par `ID_LETTRE`, est
  inchangée) mais n'entre plus dans l'unicité de la trace. Le champ `lue` du DTO et le compteur
  « Mes lettres de renvoi » du menu PRMP reflètent désormais les lectures **de l'agent connecté**,
  pas celles de toute la tutelle.

**Rectification en attente de décision PRMP (⚠️ règle ajoutée)**

- Sur un dossier au statut **`EN_ATTENTE_DECISION_PRMP`** (observations de vérification non levées), la PRMP
  propriétaire peut **corriger le contenu sans repasser par le brouillon**, via une **édition restreinte** :
  - `PATCH /api/ppms/{id}/rectifier` — en-tête du PPM ;
  - `PATCH /api/marches/{id}/rectifier` — ligne de marché (⚠️ **corrigé 2026-08-27** — le mode de
    passation n'est **plus revalidé** : `idMode` est simplement conservé tel quel, comme au POST/PUT,
    depuis le retrait de la détermination automatique, voir Module 02 ci-dessous).
- Le **statut reste `EN_ATTENTE_DECISION_PRMP`** jusqu'à la **resoumission** (`POST /api/dossiers/{id}/resoumettre`
  → `EN_VERIFICATION`). Hors `EN_ATTENTE_DECISION_PRMP` → **409**. Profil **PRMP strict** (Admin/vérificateur → 403).
- **Identité figée** (PPM : idDossier/idPrmp/idLocalite ; Marché : idDossier/idPpm) et **édition en place**
  (pas d'ajout/suppression de lignes — ces opérations restent réservées au `BROUILLON`). Tracé `t_audit_log`
  (`MODIFICATION_RECTIFICATION`). DAO/MAOO : sans contenu éditable, non concernés.

**Inscription et validation du compte**

- **Auto-inscription** (route publique, `multipart/form-data`) : la PRMP renseigne son identité,
  **déclare ses entités contractantes** — choisies dans le référentiel public et/ou **proposées**
  si « non listées » — et joint son **arrêté de nomination** et sa **CIN** (obligatoires) plus une
  **photo** (optionnelle). Le compte est créé au statut **`EN_ATTENTE`** et ne peut pas se connecter.
- **Vérification humaine** : l'**Administrateur** consulte l'inscription, **télécharge et vérifie
  l'arrêté de nomination**, puis décide (la vérification n'est pas automatique).
- **Validation (partielle)** : chaque entité déclarée **disponible** est rattachée (affectation
  active `t_prmp_entite`) ; une entité déjà rattachée à une autre PRMP active est **signalée en
  conflit** (non bloquant) ; une entité **proposée** acceptée est **créée** dans le référentiel par
  l'Administrateur. Le compte passe **`ACTIF`** dès qu'**au moins une** entité est rattachée ; si
  aucune ne l'est (tous conflits), il **reste `EN_ATTENTE`** avec le récapitulatif.
- **Refus** : l'Administrateur refuse avec un **motif** ; le compte passe **`REFUSE`** (non
  connectable) et la PRMP est **notifiée** du motif.
- **Pièces jointes** : stockées en base (`t_piece_jointe`), une pièce active par type (re-dépôt =
  remplacement) ; téléchargement réservé à l'**Administrateur ou au propriétaire** de l'inscription.
- **Prérequis** : le référentiel `tr_entite_contract` doit être **pré-alimenté** par l'Administrateur
  pour que les PRMP y choisissent leurs entités ; à défaut, elles passent par le canal « entité non
  listée » (proposition créée à la validation).

**Module 02 — Saisie & gestion PPM**

- Création et mise à jour du PPM [Écriture]
  - En-tête, exercice, signataire, marchés, lots, tranches, SOA bénéficiaires.
- Mode de passation — **purement saisi** [Écriture]
  - ⚠️ **Règle RETIRÉE (corrigé 2026-08-27)**. Ce paragraphe décrivait une détermination automatique du
    mode (référentiels `t_situation`/`t_regle_passation`/`t_seuil`, validation serveur avec 409 « hors
    ensemble », endpoint d'aperçu `POST /api/regle-passations/suggestion-mode`) : c'est un **écart
    d'origine** entre la doc et le code — ce socle a été **retiré** dès le commit `c432e73`
    (2026-07-04), avant même le chantier d'audit d'août. Le mode de passation (`idMode` de
    `MarcheDto`/`PpmDto`) est **purement saisi** par la PRMP (ou repris de l'import PPM) et **conservé
    tel quel** à la création, à la mise à jour et à la rectification — aucune détermination, aucune
    validation par situation/seuil, plus de notification `MODE_NON_DETERMINE`. Seule la **clé
    étrangère** vers `tr_mode` garantit que le mode existe.
- Justifications de la fiche de présentation [Écriture] ⚠️ **Règle ajoutée (arbitrage du pilote, 2026-09-01)**
  - La **fiche de présentation** du dossier de planification énumère trois catégories de marchés qui
    appellent une justification : ① marchés passés selon un **mode dérogatoire**
    (`tr_mode_passation.CATEGORIE = DEROGATOIRE`), ② marchés à **délai aménagé** (ouverture des plis −
    lancement, en jours calendaires, **strictement inférieur** au `delaiMinJours` du mode), ③ **contrats-cadres**
    (`formeMarche = CONTRAT_CADRE`).
  - Ces justifications sont des **données saisies à la création**, et elles sont **bloquantes** :
    `POST /api/saisies/ppm` et `PUT /api/saisies/ppm/{idDossier}` refusent en **400** un marché dérogatoire
    sans sa justification, un marché à délai aménagé sans la sienne, et une **justification globale** absente
    dès qu'une des trois listes est non vide. Les erreurs sont rendues **toutes ensemble**, une par champ.
  - **Deux justifications par ligne, une pour la fiche.** Un marché peut cumuler mode dérogatoire et délai
    aménagé — deux questions distinctes, deux champs (`justifModeDerogatoire`, `justifDelaiAmenage`). Les
    **contrats-cadres n'ont pas de champ par ligne** : la justification globale portée par le plan
    (`justificationFiche` sur `t_ppm`, la « Justification : » du bas du formulaire officiel) les couvre,
    comme sur le document papier.
  - ⚠️ **Le serveur re-classe les marchés lui-même**, depuis ses propres référentiels (catégorie et plancher
    du mode, forme du marché, dates CAPM appariées par mot-clé LANCEMENT / OUVERTURE). Le classement envoyé
    par le client n'est jamais cru : s'y fier permettrait de créer un dossier sans les justifications
    réglementaires en se trompant — ou en mentant — sur la catégorie d'un marché.
  - **Ce qui n'est pas exigé** compte autant : pas de délai calculable sans les **deux** dates, pas de règle
    sans plancher au référentiel, et l'**égalité au plancher est conforme** (la comparaison est un `<` strict).
    Une justification envoyée sur une ligne que le serveur ne classe pas est acceptée et conservée.
  - **Transition** : aucune reprise de données. Les plans antérieurs rendent `null` et la fiche affiche
    « À compléter » ; la règle ne porte que sur les écritures faites par la façade après le déploiement.
  - ⚠️ **Une entrée reste hors garde** : la mise à jour d'un PPM **pilotée par import PDF**
    (`POST /api/saisies/ppm/{id}/mise-a-jour/import`). Un PDF ne porte aucune justification, et l'y soumettre
    interdirait définitivement toute mise à jour comportant une ligne dérogatoire. **Conséquence assumée** :
    une version créée par import peut contenir un marché dérogatoire non justifié, à compléter ensuite par
    la façade d'édition.
- Identifiants attribués par le serveur [Auto]
  - ⚠️ **Règle ajoutée** : les PK dossier / PPM / marché sont **allouées par une séquence serveur** (`seq_dossier`/`seq_ppm`/`seq_marche`) ; tout id envoyé par le client est **ignoré** (plus de « identifiant en doublon »). Le formulaire ne saisit plus d'id. **Dette documentée** : séquence applicative (et non `IDENTITY`) pour éviter une refonte massive des fixtures de test sur ces 3 tables centrales ; bascule `IDENTITY` possible plus tard.
- Suppression d'un marché / d'un PPM [Écriture]
  - ⚠️ **Règle ajoutée** : possible **uniquement** si le **dossier rattaché est en BROUILLON** et **propriété** de la PRMP (sinon **403** « Vous n'êtes pas le propriétaire… » / **409** « Opération impossible : le dossier n'est pas un brouillon »). Supprimer un **marché** efface **en cascade** ses **dates prévisionnelles** (`t_marche_prevision`) ; supprimer un **PPM** efface **en cascade** ses **marchés** et leurs prévisions — le tout dans la **même transaction** (la cascade ne touche **que** les enfants de la cible). *(Côté SGBD, un filet de sécurité distingue désormais les violations FK / doublon / valeur obligatoire.)*

- Date prévisionnelle de fin de traitement [Lecture] ⚠️ **Règle ajoutée (2026-09-01)**
  - Chaque dossier porte sa **date prévisionnelle d'achèvement** à la CNM (`datePrevisionnelleFin` sur
    `DossierDto`, présent sur la lecture unitaire **et** sur les listes), en jours ouvrés et **calculée
    entièrement serveur**. Elle est disponible **dès la soumission**, avant que quiconque à la CNM ait
    touché le dossier.
  - Le drapeau **`attentePrmp`** l'accompagne : quand la balle est chez la PRMP
    (`EN_ATTENTE_COMPLEMENTS_DEPOT`, `EN_ATTENTE_PIECES`, `EN_ATTENTE_DECISION_PRMP`), la date reste
    calculée mais **glisse tant que la PRMP n'a pas rendu la main** — c'est le net CNM qui juge la
    Commission, pas le temps que la PRMP prend à rectifier.
  - `GET /api/dossiers/{id}/chronometrage` détaille les étapes franchies, leurs acteurs et les deux
    compteurs. Détail de la règle : section « Chronométrage et prévision des délais » en §2.

**Module 03 — Soumission & retours**

- ⚠️ **Règle ajoutée (2026-07-17) — FAMILLES et SOUS-TYPES de dossier.** Le référentiel `tr_type_dossier`
  porte désormais les **familles** — codes **renommés** : `PPM`→**`DDP`** « Dossier de Planification »,
  `DAO`→**`DMC`** « Dossier de Mise en Concurrence », `MAOO`→**`DDM`** « Dossier de Marché ». Chaque
  dossier porte un **sous-type** (`t_dossier.ID_SOUS_TYPE`, référentiel **ouvert et administrable**
  `tr_sous_type_dossier`, rattaché à une famille) : **DDP** ⊃ `PPM`, `PPM-AGPM` ; **DMC** ⊃ `DAO`,
  `DAOR`, … ; **DDM** ⊃ `MAOO`, `MAOR`, … La famille se **déduit** du sous-type. Famille **DDP** :
  sous-type **dérivé serveur** (`PPM-AGPM` ssi ≥1 marché en appel d'offres ouvert — même source de
  vérité que la règle AGPM ci-dessous), jamais choisi. Familles **DMC/DDM** : sous-type **choisi à la
  saisie** (liste par famille). Les **pièces attendues** (`t_type_piece_jointe`) restent rattachées à la
  **famille**. Les **points de contrôle** (`tr_points_ctrl`) sont rattachés à la famille et
  **affinables par sous-type** (⚠️ règle ajoutée 2026-07-17) : `ID_SOUS_TYPE` facultatif — null = point
  commun à la famille, renseigné = point spécifique au sous-type. **Grille effective d'un dossier =
  communs de sa famille + spécifiques de son sous-type** (`GET /api/points-ctrls?sousType=X`) : la
  grille d'un `PPM` (7 points) ≠ celle d'un `PPM-AGPM` (8 points, dont « AGPM joint et conforme » —
  seed `2026-07-17_points_ctrl_sous_type.sql`). Les **nouvelles références**
  portent le segment **famille** (ex. `00012/DDP/CRM-ANT/2026`, numérotation continue) ; les
  références déjà émises sont conservées ; la référence initiale PPM (`xxxxx/<acronyme>/PPM/<année>`)
  garde son segment `PPM` (nom du document). Migration : `2026-07-17_familles_sous_types.sql`.

- Soumission du dossier [Action]
  - Envoi officiel avec génération de la référence unique.
  - ⚠️ **Règle ajoutée (non issue de la brochure d'origine)** : **un PPM doit comporter au moins
    un marché avant soumission**. La soumission d'un dossier de la famille **DDP** (planification) sans
    aucune ligne de marché est **refusée (HTTP 409)**. Ne s'applique **qu'à la famille DDP** — les
    dossiers **DMC/DDM** ne sont pas concernés. Justification : un PPM est un plan de passation de
    marchés ; un PPM vide n'a rien à soumettre au contrôle. Le frontend doit s'aligner sur cette précondition.
  - ⚠️ **Règle ajoutée (non issue de la brochure d'origine) — AGPM conditionnel** : un PPM comportant
    **au moins un marché en « appel d'offres ouvert »** doit être accompagné de la pièce **AGPM**
    (Avis Général de Passation de Marché). À la soumission, si l'AGPM manque → **refus (HTTP 400,
    champ `piecesJointes`)**, au même titre que les pièces obligatoires statiques. Le déclenchement est
    **data-driven, jamais par mot-clé de libellé** : l'administrateur coche le drapeau `DECLENCHE_AGPM`
    sur le(s) mode(s) de passation concerné(s) (`tr_mode_passation`), et la pièce AGPM est repérée dans
    le référentiel `t_type_piece_jointe` par son **code stable `AGPM`** (son drapeau `OBLIGATOIRE`
    statique reste **false** — l'obligation est conditionnelle). Le PPM lu expose un dérivé serveur
    **`agpmRequis`** (true ssi ≥1 marché déclencheur) que le front **affiche** sans le recalculer.
    Prérequis de paramétrage (admin) : cocher `DECLENCHE_AGPM` sur le mode « appel d'offres ouvert » et
    créer la pièce de code `AGPM` sur la famille de dossier **DDP**. Un dossier DDP dans ce cas porte le
    sous-type dérivé **`PPM-AGPM`** (cf. règle familles/sous-types ci-dessus).
- Suivi de l'état de réception [Lecture]
  - Accès à réception, date, secrétaire — en temps réel.
- Consultation du PV d'examen [Lecture]
  - Accès en lecture au PV signé : référence, avis, synthèse des observations non conformes.
  - ⚠️ **Détail des points de contrôle réservé au circuit interne (2026-08-27, audit constat C2/lot A —
    effet assumé).** La PRMP n'a **jamais** accès à la grille point par point (`examen-details`,
    `examen-pieces`, `observation-controles`) — y compris via « PV définitifs » (`GET
    /api/pv-examens/definitifs`, `/{id}`, `/{id}/document`) : elle ne reçoit que la **synthèse** du PV
    (avis, synthèse des observations, document PDF officiel), jamais la reconstruction du détail
    interne à la commission (réservée aux contrôleurs de la localité). Avant ce chantier, les lectures
    de détail n'appliquaient **aucun filtre** : la PRMP — et tout authentifié — lisait la grille de
    n'importe quel examen. **À confirmer côté métier** : c'est un cloisonnement plus strict que
    l'ancien comportement de fait, décidé par l'audit, pas encore validé explicitement par le PO comme
    un choix produit définitif (voir `docs/plan-audit-2026-08.md`, décisions en attente).
- Soumission du dossier corrigé [Action]
  - Dépôt en retour avec corrections basées sur les observations du PV.
- Dépôt de pièce jointe — borné aux phases de dépôt (⚠️ règle ajoutée 2026-08-27, audit lot B) [Écriture]
  - Avant ce chantier, le dépôt d'une **pièce initiale** (`POST /api/piece-jointe-dossiers`, sans
    lettre de renvoi) n'avait **aucune garde d'état** : la PRMP pouvait verser une pièce à n'importe
    quel moment du circuit, y compris après l'examen, après la signature du PV ou sur un dossier
    clôturé. Deux listes blanches de statuts, sinon **409** :
    - **dépôt initial** (`idLettre` absent), toujours ouvert : `BROUILLON`, `SOUMIS`,
      `EN_ATTENTE_COMPLEMENTS_DEPOT`, `EN_ATTENTE_PIECES`, `EN_ATTENTE_DECISION_PRMP` ;
    - **dépôt après lettre de renvoi** (`idLettre` fourni), ouvert **en plus** sur les statuts de
      reprise d'examen : `PRET_DISPATCH`, `DISPATCHE`, `A_REEXAMINER` — le **premier** complément
      rouvre l'examen (`…/transmettre-complements`), les suivants arrivent sur un dossier déjà reparti.

**Module 11 — Retrait de dossier**

- Demande de retrait motivée [Action]
  - Demande de retrait d'un dossier déjà enregistré. Motif obligatoire (MOTIF_RETRAIT NOT NULL dans t_demande_retrait).
  - ⚠️ **Règle ajoutée (2026-08-17) — lettre de demande de retrait obligatoire** : la PRMP joint, **au moment de la demande**, sa **lettre de demande de retrait datée et signée** (PDF uniquement, ≤ 10 Mo — validation par magic-bytes ; sinon **400**, la demande n'est pas créée). `POST /api/demande-retraits` passe en **multipart** (`data` JSON + `fichier` PDF). La lettre est stockée **avec la demande** (`t_piece_demande_retrait`, hors pièces du dossier) car elle **justifie la décision** : elle **survit à la purge du circuit** à l'acceptation. Lecture `GET /api/demande-retraits/{id}/document` : PRMP demanderesse + décideur (CC de la localité / Président ; Admin). **Rétro-compat** : les demandes antérieures sans lettre restent valides (`nomFichier` null, document → 404).
  - ⚠️ **Règle ajoutée** : la PRMP demandeuse est **l'utilisateur authentifié** (JWT), jamais le corps ; l'`ID_DEMANDE_RETRAIT` est **auto-généré**. Gardes (sinon 403/409) : **être propriétaire** du dossier, dossier **« avant PV signé »**, et **pas de demande déjà `EN_ATTENTE`** pour ce dossier. Liste déroulante des dossiers retirables : `GET /api/dossiers/retirables`.
  - ⚠️ **Règle ajoutée (§3.3) — retrait possible jusqu'au PV signé** : la demande est recevable **à toute étape du circuit tant que le PV n'est pas signé** (plus seulement « avant dispatch »). Statuts retirables **exacts** : **`SOUMIS`, `PRET_DISPATCH`, `DISPATCHE`, `EXAMINE`** ; refus (409) à partir de **`PV_SIGNE`** et au-delà (`EN_VERIFICATION`, `EN_ATTENTE_DECISION_PRMP`, `RETIRE`, `CLOTURE`). `BROUILLON` exclu (pré-circuit). La liste `GET /api/dossiers/retirables` et la garde du POST partagent **le même ensemble** (source unique serveur), donc ne divergent jamais.
  - ⚠️ **Re-contrôle d'état à la décision (2026-08-27, audit constat C3, corrigé lot A).** La garde
    ci-dessus ne s'appliquait **qu'à la création** de la demande : rien ne suspendant le circuit pendant
    qu'une demande reste `EN_ATTENTE` (règle §3.1 — pas d'obligation d'intérim, pas de suspension), le PV
    pouvait être signé **entre la demande et la décision**. `POST /{id}/accepter` **relit désormais le
    statut du dossier en base** au moment de la décision, sur le même ensemble source : dossier ayant
    progressé au-delà de `NOMS_AVANT_PV_SIGNE` → **409 « la demande de retrait est caduque »**. La
    demande reste `EN_ATTENTE` et peut toujours être **refusée** (le refus ne touche jamais au circuit).
    Avant ce correctif, l'acceptation d'une demande caduque **purgeait silencieusement** un PV déjà
    signé, ses navettes, vérifications et lettres — en violation de l'immuabilité du PV signé (§3.5).
- Suivi de la demande [Lecture]
  - Consultation du statut : **EN_ATTENTE / ACCEPTEE / REFUSEE** (⚠️ règle ajoutée). Ses demandes : `GET /api/demande-retraits`.
- Notification décision [Lecture]
  - Reçoit **RETRAIT_ACCEPTE** ou **RETRAIT_REFUSE**. ⚠️ **Règle ajoutée** : si **accepté**, le dossier **repasse en `BROUILLON`** (et non `RETIRE`) ; si refusé, dossier inchangé (motif de refus optionnel).
  - ⚠️ **Règle ajoutée (§3.3) — purge du circuit à l'acceptation** : un dossier retiré à un stade avancé (`DISPATCHE`/`EXAMINE`) porte un enchaînement réception → dispatch → examen → projet de PV → navettes (+ copies, lettres de renvoi, observations). L'acceptation **supprime tout cet historique** en une transaction, dans l'ordre FK-safe, pour que le dossier redevienne un `BROUILLON` propre (re-soumissible → re-réception `INITIAL`). Le journal d'audit (`t_audit_log`, sans FK) est conservé.

**Module 04 — Calendrier & notifications**

- Calendrier des jalons [Lecture]
  - Lancement, ouverture, attribution — ⚠️ **comportement réel corrigé (2026-08-27)** : ce document
    promettait des alertes **J-7 ET J-1** ; le code (`AlerteScheduler.alerterJalons`) n'émet en réalité
    qu'**une seule alerte par jalon**, dès son entrée dans la **fenêtre J-7** (job quotidien qui
    sélectionne les échéances entre aujourd'hui et J+7). Un **drapeau** (`Echeance.alerteEnvoyee`) est
    posé au premier envoi et empêche tout renvoi ultérieur — il n'y a **pas** de second passage à J-1.
    **Écart d'origine**, non introduit par ce chantier : signalé par l'audit du 27/08, documenté ici
    tel quel plutôt que corrigé (un J-1 réel demanderait de revoir la condition du job, hors périmètre
    de ce chantier de documentation).
- Notification PV accepté puis signé [Lecture]
  - Reçoit PV_SIGNE dès que le PV atteint le statut SIGNE (après navette et signature). Ne reçoit pas les notifications de la navette interne (PROJET_PV_SOUMIS / PROJET_PV_RETOUR / PROJET_PV_ACCEPTE — réservées aux contrôleurs).
- Alerte fin de mandat [Lecture] [Auto]
  - Le système calcule la date d'expiration du mandat = DATE_NOMIN + 3 ans (t_prmp). Des alertes automatiques sont envoyées à J-90, J-30 et J-7 avant expiration. TYPE_NOTIF = FIN_MANDAT. À J=0, le compte est marqué comme expiré.

**Module 01 — Tableau de bord**

- Synthèse de ses PPM [Lecture]
  - Statuts en cours, clôturés, en observation — par exercice.
- Pipeline de ses dossiers [Lecture]
  - Soumis → Reçu → Examen → PV → Retour / Clôture.
- Mes indicateurs de performance [Lecture]
  - Taux de conformité, nb dossiers soumis / conformes / retours / retraits, délai moyen de correction — depuis v_performance_prmp.
- Alertes personnelles [Lecture]
  - Observations en attente, échéances dépassées, PV signé en attente de correction.

**Restrictions / contraintes :**

- Ne voit que la synthèse et l'avis du PV — pas le détail des points de contrôle internes
- Retrait soumis à validation obligatoire du Chef de commission
- Ne peut pas modifier un dossier après soumission, sauf retour officiel ou retrait approuvé
- Mandat de 3 ans non renouvelable automatiquement — expiration = DATE_NOMIN + 3 ans (t_prmp), ou la
  `dateFin` du mandat déclaré (`t_mandat`) qui prime dès qu'il en existe un. **Reconductible une seule
  fois**, par un mandat distinct ; sans mandat actif, tout traitement est suspendu (409 `VACANCE_PRMP`).
- Aucun accès au journal d'audit, aux anomalies ni aux statistiques CNM globales

---

### 3.2. Président

- **Rôle** : Pilotage de la commission — sommet de la hiérarchie
- **Visibilité** : Toutes localités (ID_LOCALITE = NULL)
- **Subordonnés** : Tous les Chefs de commission
- **Délégations possibles** : Secrétaire, Membre, Vérificateur

Sommet de la hiérarchie CNM. Supervise tous les Chefs de commission. Voit tous les dossiers toutes localités confondues. Peut exercer par délégation les tâches de Secrétaire, Membre et Vérificateur. Dispatche les dossiers et co-signe les PV.

**Module 02 — Circuit de contrôle**

- Notification PRET_DISPATCH [Lecture]
  - Alerte automatique dès COMPLET = true — notifié en temps réel pour toutes les localités.
- File d'attente dispatch [Lecture]
  - Vue v_file_attente_dispatch : tous les dossiers complets sans dispatch existant, toutes localités.
- Dispatch vers un membre [Action]
  - Affectation avec instructions et date limite. INTERIM_DISPATCH = false en fonctionnement normal.
  - ⚠️ **Règle ajoutée (2026-08-15) — cohérence de l'attributaire (garde au POST/PUT `/api/dispatchs`)** : `IM_CTRL_MEMBRE` doit désigner un contrôleur **capable d'exercer la tâche du Membre** — titulaire (profil MEMBRE) **ou** couvert par une paire (profil → Membre) **active** de `t_delegation_profil`. Sinon le dossier serait inexaminable (l'examen est réservé à l'attributaire, §2.4) : **409**, message explicite ; matricule inconnu → **409** aussi. Data-driven : désactiver/réactiver la paire en base change la réponse **sans changement de code**.
  - ⚠️ **Règle ajoutée (2026-08-15) — auto-attribution (circuit court)** : la garde ci-dessus autorise le dispatcheur à **s'attribuer le dossier à lui-même** (Président via la paire Président → Membre ; CC via CC → Membre lorsqu'elle est active) : il dispatche, examine et **signe la part Membre** (acte d'identité de l'attributaire). ⚠️ **Décision produit (2026-08-15, annule la séparation des signataires du même jour)** : le signataire couvert par une paire « → Membre » **active** peut **aussi porter sa part de rôle** (Président ou CC) — **toute la signature du PV par une seule personne**, en **deux actions successives** ; la complétude « Membre + (Président OU CC) » est inchangée. Data-driven : paire désactivée → le verrou « une signature par personne » se referme (403 à l'endpoint, 409 sinon) sans changement de code.
  - ⚠️ **Règle MODIFIÉE (2026-08-15) — association CC du dispatch** : l'association/copie CC ne vaut que **quand le Président dispatche à un Membre** (le CC suit alors les dossiers de sa commission). Dispatcheur **CC** → aucune association (un `imCtrlCc` envoyé par le client est **ignoré**, forcé à null) ; Président **auto-attributaire** → pas d'association non plus ; l'association ne désigne **jamais l'attributaire lui-même** — plus de doublon « Rôle Membre + Rôle CC » dans les attributions. **Reprise au démarrage** (`AssociationCcDispatchMigration`) : `IM_CTRL_CC` effacé sur l'existant quand il désigne l'attributaire ou le dispatcheur.
- Réception d'un dossier (délégation) [Action]
  - Peut enregistrer et valider la complétude d'un dossier à la place du Secrétaire (t_delegation_profil).
- Examen point par point (délégation) [Action]
  - Peut instruire l'examen d'un dossier à la place du Membre (t_delegation_profil).
- Vérification de levée (délégation) [Action]
  - Peut vérifier la levée des observations à la place du Vérificateur (t_delegation_profil).
- Révision et retour du projet de PV [Action]
  - À réception du projet soumis par le Membre (PROJET_SOUMIS), peut demander des corrections : passage en EN_RECTIFICATION + insertion dans t_pv_navette (SENS = RETOUR_RECTIF) + notification PROJET_PV_RETOUR vers le Membre. Le commentaire de rectification est obligatoire.
- Acceptation du projet de PV [Action]
  - Valide le projet corrigé : passage en PROJET_ACCEPTE + insertion dans t_pv_navette (SENS = ACCEPTATION) + notification PROJET_PV_ACCEPTE vers le Membre. Le PV devient signable.
- Co-signature définitive du PV [Écriture]
  - Une fois le projet accepté, un Président réel co-signe en renseignant DATE_SIGNATURE_PRESIDENT **et IM_CTRL_PRESIDENT (= son matricule)** dans t_pv_examen. Le service authentifie le signataire : profil PRESIDENT requis (403 sinon), et le co-signataire doit être **différent du Membre signataire** (auto-co-signature interdite) — **sauf** (⚠️ décision produit 2026-08-15, circuit court) s'il est couvert par une paire « → Membre » **active** : le Président attributaire signe alors **les deux parts** lui-même. Facultatif si c'est le CC qui co-signe — contrainte t_pv_examen_cosignataire_check garantit qu'au moins l'un des deux signe.
- Suivi de tous les dossiers [Lecture]
  - Vue d'ensemble de tous les dossiers, toutes localités et toutes commissions.

**Module 01 — Tableau de bord & KPIs**

- Pipeline de tous les dossiers [Lecture]
  - Vue de l'avancement de tous les dossiers, toutes commissions et localités confondues — depuis v_file_attente_dispatch et t_dossier.STATUT.
- Alertes et notifications [Lecture]
  - Toutes les alertes PRET_DISPATCH, PV_SIGNE, ALERTE_DELAI — toutes localités, en temps réel.
- KPIs en temps réel [Lecture]
  - Dossiers reçus, taux de conformité, délai moyen de traitement, montant total contrôlé — agrégés toutes localités depuis t_snapshot_stats.
- Taux de conformité [Lecture]
  - Rapport NB_DOSSIERS_CONFORMES / NB_DOSSIERS_SOUMIS × 100 (t_snapshot_stats.TAUX_CONFORMITE). Un dossier est conforme quand le vérificateur enregistre OBS_LEVEES = true au dernier passage (t_verification). Un dossier non conforme a reçu un avis DEFAVORABLE ou FAVORABLE_RESERVES non levé. Le taux mesure donc la qualité initiale des soumissions PRMP : 100 % signifie zéro retour corrigé.
- Flux de traitement visuel [Lecture]
  - Pipeline par étape (Réception → Dispatch → Examen → PV → Vérification → Clôture) avec comptage par statut — source t_snapshot_stats.
- Indicateurs par contrôleur [Lecture]
  - Performance mensuelle (t_indicateur_ctrl) de chaque membre de toutes les commissions : nb examens, nb conformes, délai moyen, nb observations.
- Performance des PRMP [Lecture]
  - Consultation de v_performance_prmp : taux de conformité par PRMP, nb retours, nb retraits approuvés, délai moyen de correction — par exercice budgétaire.

**Module 06 — Calendrier & jalons**

- Calendrier des jalons [Lecture]
  - Vue de tous les marchés toutes localités avec retards (ECART_JOURS > 0).
- Export PDF / iCal [Action]
  - Export des échéances pour les outils de planning.

**Module 10 — Rapports périodiques**

- Mensuel / trimestriel / annuel [Action]
  - Génération et export PDF/Excel des dossiers traités — toutes commissions.

**Module 07 — Statistiques non-conformité**

- Taux de non-conformité global [Lecture]
  - Pour chaque point de contrôle (tr_points_ctrl), le taux = nb fois où t_examen_detail.CONFORME = false / nb total d'occurrences du point dans tous les examens × 100. C'est un taux par point de référentiel, pas par dossier. Source : v_stats_non_conformite — champ taux_non_conformite_pct. Toutes localités.
- Top 5 des points de contrôle [Lecture]
  - Les 5 points de contrôle ayant le taux de non-conformité le plus élevé (taux_non_conformite_pct DESC), toutes localités confondues. Calculé depuis v_stats_non_conformite : rang_frequence ≤ 5, partitionné par localité × exercice. Identifiés par LIBEL_POINT_CTRL et ID_TYPE_DOSSIER.
- Évolution mensuelle [Lecture]
  - Courbe d'évolution du nombre de lignes t_examen_detail.CONFORME = false par mois — permet de détecter une dégradation ou amélioration de la qualité des soumissions dans le temps.
- Répartition par type de dossier [Lecture]
  - Distribution des occurrences CONFORME = false par ID_TYPE_DOSSIER (tr_points_ctrl.ID_TYPE_DOSSIER) — identifie les familles de dossiers les plus problématiques.

**Restrictions / contraintes :**

- Ne rédige pas le PV — c'est le Membre qui rédige le projet
- Peut retourner le projet autant de fois que nécessaire avant acceptation
- Ne peut co-signer (SIGNE) qu'après que le projet soit au statut PROJET_ACCEPTE
- Pas d'accès aux référentiels et au paramétrage (réservé à l'Administrateur)

---

### 3.3. Chef de commission

- **Rôle** : Pilotage de sa commission — localité définie
- **Visibilité** : Sa localité uniquement
- **Supérieur** : Président
- **Subordonnés** : Secrétaire, Membres de sa localité
- **Délégations possibles** : Secrétaire, Membre, Vérificateur

Subordonné du Président. Rattaché à une localité définie — ne voit que les dossiers de sa localité et ne trouve que ses propres membres. A son propre Secrétaire. Dispatche en titulaire (CRM) ou en intérim. Peut exercer les tâches de Secrétaire, Membre et Vérificateur pour sa localité.

**Module 02 — Circuit de contrôle**

- Notification PRET_DISPATCH (copie) [Lecture]
  - Reçoit en copie la notification PRET_DISPATCH — uniquement pour les dossiers de sa localité.
- Dispatch titulaire (localité CRM) [Action]
  - Pour la localité CRM, le CC dispatche en tant que titulaire — INTERIM_DISPATCH = false.
- Dispatch en intérim (autres localités) [Action]
  - En l'absence du Président — INTERIM_DISPATCH = true tracé dans t_dispatch.
  - ⚠️ La **garde de cohérence de l'attributaire** et l'**auto-attribution** (§3.2, Dispatch vers un membre) s'appliquent à l'identique au dispatch du CC — l'auto-attribution du CC n'est possible que si la paire CC → Membre est **active**.
- Réception copie du dossier [Lecture]
  - Copie formelle via t_copie_dossier (TYPE_COPIE = DISPATCH_CC) + notification DISPATCH_CC.
  - ⚠️ **Règle MODIFIÉE (2026-08-15)** : la copie/association CC ne vaut que pour les dispatchs du **Président vers un Membre**. Quand le CC dispatche lui-même (titulaire ou intérim, Membre ou auto-attribution), **aucune association CC** n'est posée — il est l'acteur du dispatch (voir §3.2, « Dispatch vers un membre »).
- Réception d'un dossier (délégation) [Action]
  - Peut enregistrer et valider la complétude d'un dossier à la place de son Secrétaire.
- Examen point par point (délégation) [Action]
  - Peut instruire l'examen à la place d'un de ses Membres — pour sa localité uniquement.
- Vérification de levée (délégation) [Action]
  - Peut vérifier la levée des observations à la place d'un Vérificateur de sa localité.
- Lecture des résultats d'examen [Lecture]
  - Accès aux points de contrôle de ses Membres (t_examen_detail) — sa localité uniquement.
- Révision et retour du projet de PV [Action]
  - À réception du projet soumis par le Membre (PROJET_SOUMIS), peut demander des corrections : passage en EN_RECTIFICATION + insertion dans t_pv_navette (SENS = RETOUR_RECTIF) + notification PROJET_PV_RETOUR vers le Membre. Le commentaire est obligatoire.
- Acceptation du projet de PV [Action]
  - Valide le projet : passage en PROJET_ACCEPTE + insertion dans t_pv_navette (SENS = ACCEPTATION) + notification PROJET_PV_ACCEPTE vers le Membre. Le PV devient signable.
- Co-signature définitive du PV [Écriture]
  - Une fois le projet accepté, le CC **de la localité du dossier** co-signe en renseignant DATE_SIGNATURE_CC **et IM_CTRL_CC (= son matricule)**. Le service authentifie le signataire : profil CHEF_COMMISSION **et localité du dossier** requis (403 sinon), co-signataire **différent du Membre** (auto-co-signature interdite) — **sauf** (⚠️ décision produit 2026-08-15, circuit court) s'il est couvert par la paire CC → Membre **active** : le CC attributaire signe alors **les deux parts** lui-même. Facultatif si c'est le Président qui co-signe — contrainte cosignataire garantit qu'au moins l'un des deux signe.

**Module 11 — Gestion des retraits PRMP**

- Notification demande de retrait [Lecture]
  - Reçoit DEMANDE_RETRAIT_A_VALIDER dès qu'une PRMP de sa localité soumet une demande motivée (le **Président** est également notifié). File à valider : `GET /api/demande-retraits/a-valider` (scopée à la localité du dossier) ; historique : `…/historique`.
- Validation ou rejet du retrait [Action]
  - ⚠️ **Règle ajoutée** : décision via **`POST /{id}/accepter`** ou **`POST /{id}/refuser`** (le `PUT` générique est supprimé). **Seuls le CC de la localité du dossier ou le Président** peuvent statuer (contrôle rôle↔localité **dans le service**, sinon 403) ; le décideur réel (CC **ou** Président) est enregistré dans `IM_CTRL_CC` depuis le **JWT**. **Accepter → dossier `BROUILLON`** ; refuser → dossier inchangé + motif (optionnel). Demande déjà traitée → 409.
- Notification décision à la PRMP [Auto]
  - **RETRAIT_ACCEPTE** ou **RETRAIT_REFUSE** envoyé automatiquement à la PRMP.

> ⚠️ **Statut `RETIRE` (t_dossier) — non produit.** Depuis cette règle, un retrait accepté ramène le dossier en `BROUILLON` ; **aucune transition ne pose plus `RETIRE`** (valeur conservée dans l'enum, référencée défensivement par la réception, mais état mort).

**Module 07 — Statistiques non-conformité**

- Taux de non-conformité [Lecture]
  - Pour chaque point de contrôle (tr_points_ctrl) de sa localité : nb fois où t_examen_detail.CONFORME = false / nb total d'occurrences du point dans les examens de sa localité × 100. Source : v_stats_non_conformite filtrée sur ID_LOCALITE du CC. Mesure la difficulté de chaque point de référentiel, pas le taux de dossiers rejetés.
- Top 5 des points de contrôle [Lecture]
  - Les 5 points de contrôle de sa localité ayant le taux de non-conformité le plus élevé (taux_non_conformite_pct DESC), filtrés sur ID_LOCALITE du CC. Source : v_stats_non_conformite, rang_frequence ≤ 5, partitionné par localité × exercice.
- Détail par membre [Lecture]
  - Répartition des occurrences t_examen_detail.CONFORME = false par IM_CTRL_MEMBRE — permet de comparer la sévérité d'instruction entre les membres de sa commission.
- Suivi après PV [Lecture]
  - Taux de levée des observations (OBS_LEVEES = true dans t_verification) sur les dossiers ayant reçu un avis DEFAVORABLE ou FAVORABLE_RESERVES — mesure l'efficacité du circuit de correction pour sa localité.

**Module 04 — Messagerie**

- Notifications reçues [Lecture]
  - PRET_DISPATCH, DISPATCH_CC, DEMANDE_RETRAIT_A_VALIDER et autres alertes de sa localité.
- Messagerie interne [Action]
  - Échange avec le Président, ses Membres et ses Vérificateurs.

**Module 01 — Tableau de bord**

- Pipeline de ses dossiers [Lecture]
  - Vue des dossiers de sa localité uniquement : en dispatch, en examen, PV, vérification, clôturés.
- Alertes de sa localité [Lecture]
  - Toutes les alertes PRET_DISPATCH, PV_SIGNE, ALERTE_DELAI filtrées sur sa localité.

**Restrictions / contraintes :**

- Visibilité strictement limitée à sa localité — ne voit pas les dossiers des autres CC
- Ne trouve que ses propres membres (ID_SUPERIEUR = IM_CC via v_hierarchie_controleurs)
- Dispatch en intérim pour les autres localités (INTERIM_DISPATCH = true obligatoire)
- Délégations de tâches limitées à sa localité — pas de débordement inter-localités

---

### 3.4. Secrétaire

- **Rôle** : Réception des dossiers — subordonné du CC
- **Visibilité** : Sa localité uniquement (même localité que son CC)
- **Supérieur** : Chef de commission
- **Subordonnés** : —

Subordonné direct du Chef de commission. Partage sa localité avec son CC. Réceptionne et enregistre les dossiers. Actif uniquement au passage INITIAL (NUM_PASSAGE = 1).

**Module 02 — Circuit de contrôle**

- Enregistrement du dossier [Action]
  - Création de la réception avec référence, date, NUM_PASSAGE = 1 et TYPE_PASSAGE = INITIAL.
  - ⚠️ **Règle ajoutée — référence officielle générée à la réception.** Au `POST /api/receptions`, le serveur génère une référence au format **`xxxxx/type_dossier/code_localite/annee_exercice`** :
    - `xxxxx` : compteur 5 chiffres **incrémenté par la base** par combinaison (`type_dossier`, `code_localite`, `annee_exercice`) — table `t_sequence_reference` (PK composite ; `UPDATE +1` atomique sinon `INSERT` à 1), sans `SELECT FOR UPDATE` ni compteur applicatif ; la PK garantit l'unicité.
    - `type_dossier` : `ID_TYPE_DOSSIER` du dossier (PPM, DAO, MAOO…). *Dossier sans type → pas de référence structurée, la réception reste valide.*
    - `code_localite` : **`CNM`** si réception **centrale** (utilisateur transversal, sans localité — ex. Président) ; sinon **`CRM-<code_localite>`** (ex. `CRM-ANT`, `CRM-TMS`).
    - `annee_exercice` : exercice du PPM du dossier, sinon année courante.
    - La référence est **persistée sur le dossier** (`REFE_DOSSIER`, elle remplace la référence provisoire de soumission) et **retournée** dans `ReceptionDto.reference`.
    - Compteurs **isolés par contexte** : `CRM-ANT`, `CRM-TMS` et `CNM` ont chacun leur propre suite. Exemples : `00001/PPM/CNM/2026`, `00001/PPM/CRM-ANT/2026`, `00002/PPM/CRM-ANT/2026`, `00001/PPM/CRM-TMS/2026`.
  - ⚠️ **Règle ajoutée — plus de N° de réception saisi.** L'identifiant technique `idReception` (PK de `t_reception`) n'est **plus saisi** par le secrétaire : il est **alloué par le serveur** (séquence `seq_reception`, Voie B — tout id client est ignoré), comme les PK dossier/PPM/marché. Il reste retourné dans la réponse (référencé par le dispatch). Le contrôle de doublon de PK sur ce champ devient sans objet.
- Vérification de complétude [Écriture]
  - COMPLET = true/false avec consignation des observations initiales.
- Déclenchement PRET_DISPATCH [Auto]
  - Quand COMPLET = true, le trigger notifie automatiquement le Président et le CC.
- Suivi des réceptions [Lecture]
  - Liste des dossiers reçus de sa localité, statuts, historique des passages.

**Module 04 — Messagerie & notifications**

- Notifications reçues [Lecture]
  - Alertes de retour de dossier et de clôture — sa localité.
- Messagerie interne [Action]
  - Échange avec son CC et les autres agents de sa localité.

**Module 01 — Tableau de bord**

- Pipeline de ses dossiers [Lecture]
  - Vue de l'avancement des dossiers reçus de sa localité : Reçu → Dispatch → Examen → PV → Vérification → Clôture.

**Restrictions / contraintes :**

- Visibilité limitée à sa localité (même localité que son CC)
- Actif uniquement au passage INITIAL (NUM_PASSAGE = 1) — pas sur les retours
- Pas d'accès au dispatch, à l'examen ni au PV
- Pas d'accès aux KPIs ni au journal d'audit

---

### 3.5. Membre

- **Rôle** : Instructeur de l'examen — subordonné du CC
- **Visibilité** : Tous les dossiers de sa localité
- **Supérieur** : Chef de commission
- **Subordonnés** : Contrôleurs vérificateurs

Subordonné direct du Chef de commission. Voit tous les dossiers de sa localité — pas seulement ceux qui lui sont dispatché. Instruit les dossiers point par point, rédige le projet de PV et anime la navette avec le Président ou le CC jusqu'à acceptation, puis co-signe le PV définitif.

**Module 02 — Circuit de contrôle**

- Consultation de tous les dossiers [Lecture]
  - Accès en lecture à tous les dossiers de sa localité — pas uniquement ceux qui lui sont dispatché.
- Examen point par point [Écriture]
  - Renseigne chaque point de tr_points_ctrl : conforme / non conforme + observation (t_examen_detail).
- Rédaction du projet de PV [Écriture]
  - Le Membre rédige le projet de PV dans t_pv_examen (STATUT_PV = BROUILLON) : synthèse des observations non conformes de t_examen_detail.OBS_SI_NON_CONFORME, avis ID_AVIS. Le projet est modifiable librement tant qu'il n'a pas été soumis.
  - ⚠️ **Règle ajoutée** : l'attributaire `IM_CTRL_MEMBRE` du PV est **dérivé de l'attribution** (Examen→Dispatch.imCtrlMembre), **jamais saisi** dans le corps — c'est la source de vérité de la signature Membre. Un examen sans attributaire → création/MAJ refusée (409).
  - ⚠️ **Garde attributaire étendue au PUT et à la soumission de l'examen, et à ses tables filles
    (2026-08-27, audit lot B).** Elle n'était jouée **qu'à la création** : `PUT /api/examens/{id}` et
    `POST /{id}/soumettre` n'avaient **aucune** garde d'identité, rejouée désormais sur le dispatch en
    place **et** sur celui visé par le corps (sinon 403). Le `PUT` recopiait en outre `imCtrlMembre`
    depuis le corps du client — l'attributaire est une donnée du **dispatch**, jamais une déclaration
    du client : la valeur existante est conservée. Les écritures d'`ExamenDetailService` et
    d'`ExamenPieceService` (points de contrôle, pièces d'examen) n'avaient elles-mêmes **aucune** garde
    d'identité propre : localité et attributaire sont désormais exigés à la création, la modification
    et la suppression de chacune. **Verrou d'examen étendu aux pièces** : le verrou d'état
    (`DISPATCHE`/`EXAMINE`/`A_REEXAMINER` modifiable, refus dès `PV_SIGNE`) existait déjà pour les
    points de contrôle mais **pas pour les pièces d'examen**, qui restaient modifiables **après la
    signature du PV** — même garde désormais partagée par les deux (source unique, `ExamenGarde`).
- Soumission du projet au Président/CC [Action]
  - Passage en PROJET_SOUMIS → insertion dans t_pv_navette (SENS = SOUMISSION, NUM_NAVETTE incrémenté) → notification PROJET_PV_SOUMIS envoyée au Président/CC destinataire.
- Rectification sur retour [Écriture]
  - Si le Président/CC retourne le projet (EN_RECTIFICATION, SENS = RETOUR_RECTIF dans t_pv_navette), le Membre corrige la synthèse et/ou l'avis puis resoumet. Le cycle peut se répéter — NB_NAVETTES incrémenté à chaque retour.
- Signature définitive du PV [Écriture]
  - Quand le projet est accepté (PROJET_ACCEPTE), **le Membre attributaire du PV** (IM_CTRL_MEMBRE) signe en renseignant DATE_SIGNATURE_MEMBRE dans t_pv_examen. Cette signature **n'est pas déléguable** : le service refuse (403) tout autre signataire que le Membre attributaire. Le PV passe à SIGNE quand DATE_SIGNATURE_MEMBRE ET (DATE_SIGNATURE_PRESIDENT ou DATE_SIGNATURE_CC) sont renseignées — le co-signataire devant être **une personne différente** du Membre (auto-co-signature interdite), **sauf** (⚠️ décision produit 2026-08-15, circuit court) un Président/CC attributaire couvert par une paire « → Membre » **active**, qui porte alors les deux parts lui-même (le **Membre titulaire** reste exclu de la co-signature : les rôles PRESIDENT/CC exigent leur profil). Sur le **document PV**, la ligne Membre est suffixée « **(par délégation)** » quand l'attributaire n'est pas un Membre titulaire.
  - **Identité du signataire** : pour chaque signature, le service enregistre l'identité de l'**utilisateur authentifié** (CurrentUser, principal JWT) dans IM_CTRL_MEMBRE / IM_CTRL_PRESIDENT / IM_CTRL_CC ; le champ `imActeur` du corps de requête n'est **pas** utilisé pour l'identité (non falsifiable).
  - ⚠️ **Principe étendu aux chemins secondaires (2026-08-27, audit lot B) — identité toujours issue du
    JWT.** Le principe ci-dessus (signature) ne couvrait pas encore le **reste de la navette** ni le
    **dispatch**. `PvExamenService.ajouterNavette` pose désormais l'`IM_ACTEUR` de chaque navette depuis
    `CurrentUser.ref()`, jamais depuis le corps de requête (`PvActionRequest.imActeur` reste **accepté**
    pour compatibilité mais **n'a plus aucun effet** — sa contrainte `@NotBlank` a été retirée). Même
    principe côté dispatch : `IM_CTRL_DISPATCH` (POST **et** PUT) vient de l'utilisateur authentifié,
    jamais du corps — une trace de circuit déclarée par le client n'en est pas une. En plus de l'identité,
    la **localité** de l'acteur est désormais vérifiée à `POST /{id}/retourner` et `/{id}/accepter`
    (clôture de navette) : un CC d'une autre localité que celle du dossier recevait auparavant 200, il
    reçoit désormais 403 (le Président, sans localité, reste compétent partout).

**Module 04 — Messagerie**

- Notifications reçues [Lecture]
  - Alertes de dispatch et de retour de dossier de sa localité.
- Messagerie interne [Action]
  - Échange avec son CC, le Président et ses Vérificateurs.

**Module 01 — Tableau de bord**

- Pipeline de tous les dossiers [Lecture]
  - Vue de l'avancement de tous les dossiers de sa localité : affectés et non affectés.

**Restrictions / contraintes :**

- Visibilité limitée à sa localité
- Ne trouve que les Vérificateurs qui lui sont subordonnés (ID_SUPERIEUR = IM_MEMBRE)
- Pas de dispatch ni d'accès à la file d'attente
- Ne peut pas signer le PV définitif tant que le projet n'est pas au statut PROJET_ACCEPTE
- Chaque soumission et rectification est tracée dans t_pv_navette — aucune navette ne peut être supprimée
- Pas d'accès aux KPIs, rapports ni au module anomalie

---

### 3.6. Contrôleur vérificateur

- **Rôle** : Vérification de la levée — subordonné du Membre
- **Visibilité** : Sa localité uniquement
- **Supérieur** : Membre
- **Subordonnés** : Assistants contrôleurs

Subordonné direct du Membre. Travaille sur la base du PV signé (STATUT_PV = SIGNE). Valide ou rejette la levée des observations et déclenche la clôture ou un nouveau retour.

**Module 02 — Circuit de contrôle**

- Lecture du PV signé [Lecture]
  - Accès au PV définitif (STATUT_PV = SIGNE) avant vérification : référence, avis, SYNTHESE_OBSERVATIONS issue de la navette acceptée — t_verification.ID_PV requis. Peut aussi consulter l'historique de la navette (t_pv_navette) pour comprendre les rectifications apportées.
- Vérification de levée des observations [Action]
  - ⚠️ **Décision produit (2026-08-15) — pas de levée avant la première rectification de la PRMP** : les observations arrêtées au PV sont **réputées avec objet** (déjà validées par toute la chaîne — examen, acceptation P/CC, co-signature) ; le cas « levée sans objet » au premier passage n'existe pas. Le **premier passage** du vérificateur = **émission du rappel** : toutes les observations sont **MAINTENUES** ; la décision `LEVEE` est **refusée (409)** tant qu'aucune **resoumission** de la PRMP (`POST /api/dossiers/{id}/resoumettre`, action `RESOUMISSION` du journal `t_action_dossier`) n'est **postérieure à la signature du PV**. Après la première resoumission, levée/maintenue **libres** à chaque passage (boucle inchangée jusqu'à tout levé). Signal front : champ serveur **`leveePossible`** sur `GET /api/observations-pv?dossier=` (le front grise « Levée » en miroir, sans heuristique).
  - ⚠️ **Corrigé (2026-08-27) — OBS_LEVEES = true ne clôture PLUS directement.** Le dossier passe en
    **`OBSERVATIONS_LEVEES`** : il reste au vérificateur de **transmettre le sens de la décision à
    SIGMP** (`POST /api/sigmp-transmissions`, cas 2 — `APPROUVE` + `leveeObservations=true`), ce qui le
    fait passer **`DECISION_TRANSMISE_SIGMP`** ; seul l'**archivage** du PV par l'Assistant contrôleur
    clôt ensuite le dossier (voir Module 04 du circuit, §2). OBS_LEVEES = false → ⚠️ **règle ajoutée** :
    le dossier passe en **`EN_ATTENTE_DECISION_PRMP`** (il ne reste **plus** en EN_VERIFICATION).
    L'observation est **transmise à la PRMP** du dossier (notification `OBSERVATION_VERIFICATION` :
    référence dossier, vérificateur, texte de l'observation, date) et l'événement est **tracé dans
    `t_audit_log`** (NOM_TABLE=`t_verification`, CHAMP_MODIFIE=`OBSERVATION_NON_LEVEE`,
    IM_ACTEUR=vérificateur). C'est ensuite la **PRMP** qui prend connaissance des observations,
    rectifie le dossier, puis décide de la suite.
  - **Lecture seule côté vérificateur** : un dossier `EN_ATTENTE_DECISION_PRMP` **reste visible dans « à
    vérifier »** (⚠️ **composition corrigée 2026-08-27** — `GET /api/dossiers/a-verifier` retourne
    l'ensemble **`EN_VERIFICATION` + `EN_ATTENTE_DECISION_PRMP` + `OBSERVATIONS_LEVEES`**, source unique
    serveur, ni plus ni moins) — il ne disparaît de la liste qu'à la **transmission de la décision à
    SIGMP** (`DECISION_TRANSMISE_SIGMP`), pas à la clôture : à cet instant précis il **quitte** « à
    vérifier » et **apparaît** dans « vérifiés » (les deux ensembles sont complémentaires et disjoints,
    aucun dossier concerné ne peut être dans les deux ni dans aucun). Il figure aussi dans la sous-vue
    `GET /api/dossiers/en-attente-prmp`. Dans les deux cas il est en **lecture seule** : il **ne peut
    plus être ni modifié ni re-vérifié** tant que la PRMP n'a pas statué (nouvelle vérification → 409).
  - ⚠️ **Règle ajoutée (2026-08-15) — visibilité de la rectification** : au premier `PUT /api/saisies/ppm/{id}` de chaque cycle (dossier `EN_ATTENTE_DECISION_PRMP`), l'état des lignes **avant correction** est figé (`t_snapshot_rectif_ligne`) — la rectification modifiant la version courante **en place**, c'est le seul moyen de comparer. Le **diff du dernier cycle** (avant → après, lignes `INCHANGEE`/`MODIFIEE` par `idDetail`, mêmes champs comparés que le diff des versions) est servi par `GET /api/dossiers/{id}/diff-rectification` (même `DiffDossierDto` — le front réutilise son tableau surligné) à **tous les profils qui consultent le dossier** : tout-voyant, PRMP propriétaire, contrôleurs de la localité (vérificateur titulaire ou délégué compris). Le vérificateur voit ainsi **ce que la PRMP a réellement changé** au moment de statuer la levée. Après une nouvelle transmission d'observations puis une nouvelle rectification, le **nouveau** cycle remplace l'ancien. Purge avec le circuit (retrait/annulation).
  - ⚠️ **Règle ajoutée — resoumission après rectification** : la PRMP propriétaire resoumet le dossier rectifié via `POST /api/dossiers/{id}/resoumettre` avec un **motif obligatoire** (vide → 400). Le dossier repasse en **`EN_VERIFICATION`** (retour au vérificateur). Le **vérificateur du dossier** est notifié (`RECTIFICATION_PRMP` : référence, nom PRMP, motif, date), l'événement est **tracé** dans `t_audit_log` (NOM_TABLE=t_dossier, TYPE_ACTION=RECTIFICATION_PRMP, IM_ACTEUR=PRMP, CHAMP_MODIFIE=motifRectification), et le **motif est enregistré** sur la dernière vérification (`t_verification.MOTIF_RECTIF`) pour être **visible dans les passages** côté vérificateur.
  - ⚠️ **Règle ajoutée** : la vérification n'est possible que si **PV `SIGNE` + avis `FAVR` + dossier `EN_VERIFICATION`** (sinon 403/409). Tâche du **profil Contrôleur vérificateur** — titulaire **ou délégation active** (⚠️ mise à jour 2026-08-14/15 : paires Président/CC → Vérificateur de `t_delegation_profil`, garde centrale — la mention antérieure « pas de délégation » est caduque). L'**identité** enregistrée (`IM_CTRL_VERIF`) et la **date** proviennent du **JWT / serveur**, jamais du corps de requête. L'`ID_VERIFICATION` est **auto-généré** (IDENTITY).
- Historique des échanges d'un dossier clôturé [Lecture]
  - ⚠️ **Règle ajoutée** : `GET /api/dossiers/{id}/historique-echanges` (accessible **PRMP** et **Contrôleur vérificateur**, + Admin) retourne, pour un dossier **`CLOTURE`** uniquement (sinon 403), le **fil chronologique entrelacé** (chaque observation suivie de la rectification PRMP qui y répond) : observations du vérificateur (`t_verification` : date, vérificateur, texte, `obsLevees` — dont le passage final `obsLevees=true`) et rectifications de la PRMP (`t_audit_log` : date, PRMP, motif).
  - ⚠️ **Périmètre corrigé (2026-08-27, audit §3.1)** : le contrôleur vérifiait bien le **rôle** mais le
    service n'appliquait **aucun contrôle de propriété/localité** — n'importe quelle PRMP lisait
    l'historique d'un dossier clôturé d'autrui, n'importe quel vérificateur celui d'une autre localité.
    Le contrôle de périmètre est désormais appliqué **avant** la garde de clôture (rien n'est divulgué
    hors périmètre, pas même le statut).
- Transmission de la décision à SIGMP, puis archivage [Auto + Action]
  - ⚠️ **Règle CORRIGÉE (2026-08-27)** — ce paragraphe décrivait une clôture posée directement par
    `OBS_LEVEES = true` ; ce n'est plus le circuit réel depuis la spec navette du 2026-08-01. Le
    vérificateur **transmet le sens de la décision à SIGMP** (`POST /api/sigmp-transmissions`) — dérivé
    serveur de l'avis du PV signé : dossier `EN_VERIFICATION` et avis ≠ `FAVR` → `FAV` = `APPROUVE`,
    `DEF`/`NSP` = `NON_APPROUVE` ; dossier `OBSERVATIONS_LEVEES` (fin de boucle `FAVR`) → `APPROUVE` +
    levée. Le dossier passe alors **`DECISION_TRANSMISE_SIGMP`**, et l'**Assistant contrôleur** de la
    localité est notifié (`PV_A_ARCHIVER`). C'est son geste d'**archivage** du PV
    (`POST /api/pv-examens/{id}/archiver`) qui **clôt** le dossier (`CLOTURE`) et notifie
    `CLOTURE_ELIGIBLE` au Chargé de publication — la clôture n'est donc plus un effet automatique de la
    vérification, mais l'aboutissement de deux gestes supplémentaires portés par deux acteurs distincts.

**Module 04 — Messagerie**

- Notifications reçues [Lecture]
  - Alertes PV_SIGNE et retours de dossier de sa localité.
- Messagerie interne [Action]
  - Échange avec son Membre et ses Assistants contrôleurs.

**Module 01 — Tableau de bord**

- Pipeline de ses dossiers [Lecture]
  - Vue des dossiers en attente de vérification (PV signé) et des dossiers récemment clôturés ou retournés.
  - ⚠️ **Règle ajoutée, composition corrigée le 2026-08-27** — files scopées localité, **exactement**
    (source unique serveur, aucun autre statut) : **« à vérifier »** (`GET /api/dossiers/a-verifier`) =
    **`EN_VERIFICATION` + `EN_ATTENTE_DECISION_PRMP` + `OBSERVATIONS_LEVEES`** ; **« vérifiés »**
    (`GET /api/dossiers/verifies`, paginé, lecture seule) = **`DECISION_TRANSMISE_SIGMP` + `CLOTURE`**
    (un dossier est donc « vérifié » **dès la transmission à SIGMP**, avant même l'archivage qui le
    clôturera — le travail du vérificateur y est terminé). Les deux ensembles sont **exclusifs et
    exhaustifs** pour tout dossier ayant un PV signé.

**Restrictions / contraintes :**

- Visibilité limitée à sa localité
- Ne trouve que ses Assistants subordonnés (ID_SUPERIEUR = IM_VERIFICATEUR)
- Travaille uniquement sur PV au statut SIGNE — le PV ne peut atteindre SIGNE qu'après passage par PROJET_ACCEPTE
- Pas de dispatch, pas d'accès aux KPIs ni au module anomalie

---

### 3.7. Chargé de publication

- **Rôle** : Gestion des publications du portail
- **Visibilité** : Aucun périmètre dossier CNM

Gère les publications du portail de transparence. Accès strictement cloisonné : ne voit ni le circuit interne ni les données sensibles. Notifié automatiquement à chaque clôture conforme éligible.

**Module 04 — Notifications**

- Alerte clôture éligible [Lecture]
  - Notification automatique dès qu'un dossier conforme est clôturé et éligible à publication.

**Module 09 — Portail de publication**

- Workflow de publication [Action]
  - EN_ATTENTE → PUBLIE pour les PPM et marchés clôturés conformes.
- Dépôt de documents publics [Action]
  - Mise en ligne PDF avec vérification d'intégrité SHA-256.
- Compteur de consultations [Lecture]
  - Suivi du NB_CONSULTATIONS par publication.
- Retrait avec motif [Action]
  - Dépublication documentée — MOTIF_RETRAIT + DATE_RETRAIT dans t_publication.

**Restrictions / contraintes :**

- Aucun accès au circuit interne (dispatch, examen, PV, vérification)
- Aucun accès au journal d'audit ni aux statistiques CNM
- Pas d'accès au pipeline des dossiers CNM

---

### 3.8. Administrateur

- **Rôle** : Gestion système et sécurité
- **Visibilité** : Toutes localités (accès technique)

Accès complet aux référentiels, comptes utilisateurs, journal d'audit, hiérarchie et tableaux de bord. Profil haut privilège — toutes les actions sont tracées dans t_audit_log.

**Module 03 — Référentiels & paramétrage**

- Localités et référentiels de circuit [Écriture]
  - Paramétrage complet de `tr_localite` et des référentiels de circuit. ⚠️ **Corrigé (2026-08-27)** :
    ce paragraphe citait encore `t_seuil` et `t_regle_passation` — retirés du code (commit `c432e73`,
    2026-07-04) avec la détermination automatique du mode de passation, voir Module 02 de la PRMP
    ci-dessus.
- Grilles de contrôle & règles d'anomalie [Écriture]
  - Configuration de tr_points_ctrl et t_regle_anomalie.
- Comptes budgétaires & entités contractantes [Écriture]
  - Plan comptable tr_compte et répertoire tr_entite_contract.
- Délégations de profil [Écriture]
  - Gestion des entrées t_delegation_profil — quels profils peuvent exercer les tâches d'autres profils.
- Délais standards du circuit [Écriture] ⚠️ **Règle ajoutée (2026-09-01)**
  - Référentiel administrable des **délais par étape** (`GET /api/delais-standards`,
    `PUT /api/delais-standards/{etape}`), en jours ouvrés. Il fournit la prévision des étapes **pas
    encore prises en charge**, ce qui permet d'annoncer une date à la PRMP **dès la soumission** ; chaque
    prise en charge le remplace, pour son étape, par la prévision réellement saisie.
  - **Lecture ouverte** à tout utilisateur authentifié : ces délais expliquent la date annoncée, et une
    date qu'on ne peut pas expliquer se conteste mal. **Écriture réservée à l'Administrateur.**
  - Le référentiel rend **toujours les huit étapes**, même si la table en manque une (repli à 1 jour) :
    un trou ferait disparaître un terme de la somme et la date serait silencieusement trop optimiste.
    Délai **< 1 refusé** (400), étape hors circuit refusée (404). Détail : section « Chronométrage et
    prévision des délais » en §2.
- Rattachements Membre → Vérificateur → Assistant [Écriture] ⚠️ **Règle ajoutée (2026-09-01)**
  - Écran de gestion de la chaîne nominative (§1.7) : `GET /api/controleurs/rattachements` liste les
    Membres et Vérificateurs du périmètre avec leur rattaché résolu et le **profil attendu** ;
    `PUT /api/controleurs/{im}/rattachement` pose ou retire le lien (`imRattache: null` = détacher).
    **Partagé avec le Président et le Chef de commission** — ce dernier borné à **sa** localité — via
    une **sous-ressource dédiée** : ouvrir le `PUT /api/controleurs/{id}` générique leur aurait donné
    du même coup l'écriture sur le profil et la localité de tout contrôleur.
    Un rattaché **nul** est signalé à l'écran comme une **chaîne incomplète** à combler, sans être une
    erreur : le repli localité s'applique.
- Actualités d'ouverture de session [Écriture] ⚠️ **Règle ajoutée (2026-08-19)**
  - CRUD des actualités (`/api/actualites`) : titre + contenu **markdown brut** (HTML refusé, 400), profils
    cibles (au moins un), fenêtre de dates, images JPEG redimensionnées serveur. Création **INACTIF** forcé ;
    DELETE = **archivage logique** (historique conservé) ; interrupteur global
    `PUT /api/parametres/actualites-actives` (coupe le modal pour tous, d'un coup). Détail : section
    « Actualités à l'ouverture de session (transversal) » en §2.
- ⚠️ **Règle ajoutée (2026-08-14) — délégation ascendante de profils, pilotée par les données** [Auto]
  - `t_delegation_profil` est la **source unique** de la règle permanente : la garde centrale
    `PermissionService.peutExercer(profilRequis)` autorise si **profil courant == requis** OU si la
    paire **(courant → requis)** est **active** en base. Aucune liste de profils en dur dans les
    contrôleurs/services pour les tâches hiérarchiques.
  - **Hiérarchie** (rang décroissant) : Président > Secrétaire > Chef de commission > Membre >
    Contrôleur vérificateur > Assistant contrôleur. **Hors hiérarchie** : PRMP, Administrateur,
    Chargé de publication (aucune paire ne les concerne).
  - **Les 9 paires autorisées** (seed idempotent `DelegationHierarchieSeeder`, `actif=true` à la
    création, paires existantes jamais modifiées) : **Président →** Secrétaire, Chef de commission,
    Membre, Vérificateur, Assistant (5) ; **Chef de commission →** Secrétaire, Membre, Vérificateur,
    Assistant (4).
  - ⚠️ **Pourquoi une table explicite et PAS un rang** : le Chef de commission est **sous** le
    Secrétaire dans la hiérarchie mais **hérite quand même de ses droits** parce que la paire
    CC → Secrétaire est **listée**. Un modèle « rang ≥ rang requis » casserait ce cas. La relation est
    **non transitive** : Président → Secrétaire vaut parce que la paire est listée, pas via le CC.
  - **Unicité** : une ligne par paire (contrainte `UQ_DELEGATION_PAIRE`) ; l'habilitation se pilote
    par `ACTIF` — désactiver une paire retire l'habilitation **sans changement de code**, la
    réactiver la rend (critère de recette).
  - **Invariants** : accès Administrateur inchangé (`hasRole`), périmètre par localité inchangé,
    actes d'identité non délégables (signatures du PV, signature régionale des lettres de renvoi).
  - **Garde dérivée (2026-08-15)** : l'**attributaire** d'un dispatch (`IM_CTRL_MEMBRE`) est validé par
    la même règle data-driven — Membre titulaire **ou** paire (profil → Membre) **active** — ce qui
    autorise l'**auto-attribution** du Président/CC (voir §3.2, « Dispatch vers un membre »).
  - **Décisions (2026-08-15) — passage vérificateur** : le passage vérificateur (levée/maintenue des
    observations, suite de la navette) est une **tâche de profil** (titulaire OU paire
    « → Vérificateur » active) ; dans le circuit court, le décideur peut donc être l'**attributaire du
    même dossier** (auteur des observations) — **assumé, sans garde de séparation** : la vérification
    juge la levée par la PRMP, et chaque décision est tracée avec l'identité du décideur.
    ⚠️ **La désignation du Secrétaire de séance, élargie le 2026-08-15, est CADUQUE depuis le
    2026-09-02** : la notion a été retirée du cycle du PV, garde d'éligibilité comprise (voir la règle
    dédiée en §2). Conséquence assumée qui subsiste : au bloc Signataires du PV du circuit court, la
    même personne peut porter la ligne Membre **et** les deux parts de signature (levée du verrou
    d'auto-co-signature, 2026-08-15) ; le document suffixe alors la ligne Membre de
    « **(par délégation)** ».
- ⚠️ **Règle ajoutée (2026-08-13) — catégorie des modes de passation** [Écriture]
  - Chaque mode (`tr_mode_passation`) porte une **catégorie déclarative** `CATEGORIE` :
    **`NORMAL`** (mode de droit commun — l'appel d'offres ouvert au sens du Code des marchés publics)
    ou **`DEROGATOIRE`** (modes d'exception soumis à conditions) ; **null = non classé**.
    Champ `categorie` de `ModePassationDto` (lecture partout, écriture Administrateur via l'écran
    référentiel) ; valeur hors enum → **400 (champ `categorie`)**. **Aucun comportement dérivé pour
    l'instant** (purement data-driven, comme `publiciteRequise`). Les modes créés à la volée à l'import
    PPM naissent **non classés**. Reprise au démarrage : `NORMAL` posé sur les modes marqués
    `DECLENCHE_AGPM` (marqueur AOO administré, jamais de mot-clé de libellé) dont la catégorie est null
    — sans écraser un classement admin (`app.migration.categorie-mode.enabled=false` pour désactiver).

**Module 10 — Administration & sécurité**

- Gestion des comptes contrôleurs & PRMP [Écriture]
  - Création, modification, désactivation — rattachement hiérarchique (ID_SUPERIEUR) et localité.
- Gestion de la hiérarchie [Écriture]
  - Affectation des supérieurs (ID_SUPERIEUR) — construction de l'arbre via v_hierarchie_controleurs.
- RBAC — contrôle d'accès par rôle [Auto]
  - Chaque profil n'accède qu'aux modules autorisés via tr_profile et t_delegation_profil.
- Politique de mot de passe (⚠️ règle ajoutée 2026-08-27, audit lot E) [Auto]
  - **8 à 72 caractères, dont au moins une lettre et un chiffre** (`@MotDePasseValide`, regexp Unicode
    — les caractères accentués comptent comme des lettres). Appliquée à **tout nouveau mot de passe** :
    inscription (PRMP/UGPM), création de compte par l'Administrateur, réinitialisation, et « changer mon
    mot de passe ». **Jamais** à la connexion : un mot de passe créé **avant** cette règle continue de
    fonctionner pour se connecter, et n'est contraint qu'à son **prochain changement**.
- Limitation de débit — login et inscriptions (⚠️ règle ajoutée 2026-08-27, audit lot E) [Auto]
  - Verrou par couple **(IP, identifiant)** à **5 échecs de connexion / 15 min** ; garde par **IP
    seule** à **20 échecs / 15 min** (couvre aussi un balayage de plusieurs comptes depuis la même
    adresse) ; **inscriptions publiques** (PRMP/UGPM) limitées à **10 / heure / IP**. Dépassement →
    **429** avec en-tête `Retry-After` (secondes). Un **login réussi efface le compteur du couple**
    (pas celui de l'IP, volontairement). Limiteur **en mémoire** (mono-instance par nature — voir
    `docs/deploiement.md` pour l'implication en cas de plusieurs instances).

**Module 05 — Tableau de bord global**

- Pipeline global de tous les dossiers [Lecture]
  - Vue consolidée de tous les dossiers, toutes localités via v_perimetre_controleur.
- KPIs toutes localités [Lecture]
  - Vue agrégée depuis t_snapshot_stats.
- Performance des PRMP [Lecture]
  - Vue v_performance_prmp par PRMP et exercice.
- Rapports périodiques [Action]
  - Génération PDF/Excel mensuel, trimestriel, annuel.

**Module 08 — Journal d'audit**

- Consultation & filtrage [Lecture]
  - Par table, utilisateur, type d'action ou plage de dates. ⚠️ **Lecture paginée et plafonnée
    (2026-08-27, audit lot D)** : `GET /api/audit-logs?page=&size=` accepte les filtres `table`/`acteur`
    (égalité exacte) et `du`/`au` (bornes incluses), tri `dateAction` **décroissant imposé** (le `sort`
    client est ignoré). Sans pagination, la liste plate reste **plafonnée aux 500 entrées les plus
    récentes** (`t_audit_log` croît d'une ligne par écriture API).
- Export CSV/Excel [Action]
  - Pour analyse externe ou transmission à un organe de contrôle supérieur.
- Immuabilité du journal (⚠️ **complétée 2026-08-27, audit lot A**) [Auto]
  - Seul `DELETE` était refusé jusqu'ici ; `PUT` réécrivait la **totalité** de la preuve (date, acteur,
    table, type d'action, ancienne/nouvelle valeur, IP, session) et `POST` permettait d'y **insérer des
    entrées forgées**. Les **trois verbes d'écriture** (POST, PUT, DELETE) sont désormais refusés en
    **409** — un journal réinscriptible ne prouve plus rien. La **seule** voie d'écriture réelle est
    l'intercepteur HTTP interne, appelé après chaque écriture API réussie.

**Suppression refusée des actes décidés du circuit (⚠️ règle ajoutée 2026-08-27, audit lot B)**

Les `DELETE` réservés à l'Administrateur sur les ressources du circuit rejouent désormais une garde
d'état — une trace qui a déjà produit un effet (notification, transition de statut, document) ne
s'efface plus comme un brouillon :

- **Lettre de renvoi** `SIGNE` → 409 (elle a été notifiée à la PRMP, a suspendu l'examen, a son PDF sur
  le FSX). `BROUILLON`/`SOUMIS` restent supprimables.
- **Vérification décidée** (`obsLevees` renseigné, `true` ou `false`) → 409 : le dossier a bougé et a
  été notifié. Un passage encore inachevé reste supprimable.
- **Demande de retrait traitée** (`ACCEPTEE`/`REFUSEE`) → 409 : sa lettre justificative doit lui
  survivre. Une demande `EN_ATTENTE` part avec sa pièce.
- **PV `ARCHIVE`** (dossier clôturé) → 409. **Écart signalé, laissé en l'état à dessein** : un PV
  **signé mais pas encore archivé reste supprimable** — c'est l'unique porte de sortie pour rattraper
  un PV signé par erreur (le dossier redescend à `EXAMINE`).

**Restrictions / contraintes :**

- Profil haut privilège — toutes actions tracées dans t_audit_log

---

## Légende des marqueurs

- **[Lecture]** : consultation seule
- **[Écriture]** : création / modification de données
- **[Action]** : déclenche une transition d'état ou un acte métier
- **[Auto]** : comportement automatique du système (trigger, calcul, notification)
