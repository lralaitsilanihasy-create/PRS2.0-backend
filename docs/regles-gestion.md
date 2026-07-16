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
7. **Vérification** — acteur : **Contrôleur vérificateur** (strict, ⚠️ règle ajoutée) — uniquement pour un avis `FAVR`
8. **Clôture** — automatique (à la signature pour FAV/DEF/NSP, ou après levée des observations pour FAVR)

> Statuts de navette du PV : `PROJET_PV_SOUMIS`, `PROJET_PV_RETOUR`, `PROJET_PV_ACCEPTE`, puis `SIGNE`.

> ⚠️ **Règle ajoutée (non issue de la brochure) — branchement du circuit à la signature du PV (selon l'avis).**
> À la bascule `SIGNE`, le dossier est aiguillé selon `t_pv_examen.ID_AVIS` (référentiel `tr_avis` :
> `FAV`, `FAVR`, `DEF`, `NSP`) :
> - **`FAVR`** (favorable avec réserves) → dossier **`EN_VERIFICATION`** (vérification itérative ouverte) ;
> - **`FAV`** / **`DEF`** / **`NSP`** (ne se prononce pas) → dossier **`CLOTURE`** automatique (pas de vérification).
>
> Dans **tous** les cas le PV est **transmis à la PRMP** (`PV_SIGNE`) et le **Contrôleur vérificateur** de la
> localité est notifié : `PV_A_VERIFIER` (FAVR, à vérifier) ou `PV_POUR_INFO` (FAV/DEF/NSP, lecture seule).
> Le statut `PV_SIGNE` n'est donc **plus un état de repos** du dossier.

> ⚠️ **Règle ajoutée (non issue de la brochure d'origine) — statut `DISPATCHE`.** La brochure ne nomme
> aucun statut de dossier entre `PRET_DISPATCH` et `CLOTURE`. Pour matérialiser l'étape **Dispatch (3)**
> dans le pipeline, le backend ajoute le statut **`DISPATCHE`** (« dispatché, en attente d'examen ») :
> à la **création d'un dispatch**, le dossier passe **`PRET_DISPATCH` → `DISPATCHE`** (transactionnel).
> L'**examen (4)** exige désormais que le dossier soit **`DISPATCHE`** (et non plus `PRET_DISPATCH`).
> Portée : étape Dispatch → Examen uniquement. Le frontend doit s'aligner sur ce statut.

> ⚠️ **Règle ajoutée — statuts `EXAMINE` et `PV_SIGNE`.** Même principe que `DISPATCHE`, pour matérialiser
> **Examen (4)** et **PV signé (6)** : à la **création de l'examen**, le dossier passe **`DISPATCHE` →
> `EXAMINE`** (il **quitte « à examiner »**) ; à la **signature du PV**, il passe **`EXAMINE` → `PV_SIGNE`**.
> Cycle : `… DISPATCHE → EXAMINE → PV_SIGNE → CLOTURE`. Transitions transactionnelles et idempotentes.
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

**Rectification en attente de décision PRMP (⚠️ règle ajoutée)**

- Sur un dossier au statut **`EN_ATTENTE_DECISION_PRMP`** (observations de vérification non levées), la PRMP
  propriétaire peut **corriger le contenu sans repasser par le brouillon**, via une **édition restreinte** :
  - `PATCH /api/ppms/{id}/rectifier` — en-tête du PPM ;
  - `PATCH /api/marches/{id}/rectifier` — ligne de marché (mode de passation **revalidé**).
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
- Choix du mode parmi l'ensemble autorisé [Action]
  - ⚠️ **Règle ajoutée** : pour (situation, nature, montant, localité), `t_regle_passation` calcule l'**ensemble des modes autorisés** (libellés `tr_mode`) avec un **recommandé** (règle la plus prioritaire). La PRMP **choisit** dans cet ensemble ; le serveur **valide** (mode hors ensemble → **409**) ; aucun choix → **recommandé** appliqué ; aucune règle → saisie manuelle (alerte `MODE_NON_DETERMINE`). Aperçu sans enregistrement : `POST /api/regle-passations/suggestion-mode` (renvoie l'ensemble + recommandé + `modeNonDetermine`).
- Identifiants attribués par le serveur [Auto]
  - ⚠️ **Règle ajoutée** : les PK dossier / PPM / marché sont **allouées par une séquence serveur** (`seq_dossier`/`seq_ppm`/`seq_marche`) ; tout id envoyé par le client est **ignoré** (plus de « identifiant en doublon »). Le formulaire ne saisit plus d'id. **Dette documentée** : séquence applicative (et non `IDENTITY`) pour éviter une refonte massive des fixtures de test sur ces 3 tables centrales ; bascule `IDENTITY` possible plus tard.
- Suppression d'un marché / d'un PPM [Écriture]
  - ⚠️ **Règle ajoutée** : possible **uniquement** si le **dossier rattaché est en BROUILLON** et **propriété** de la PRMP (sinon **403** « Vous n'êtes pas le propriétaire… » / **409** « Opération impossible : le dossier n'est pas un brouillon »). Supprimer un **marché** efface **en cascade** ses **dates prévisionnelles** (`t_marche_prevision`) ; supprimer un **PPM** efface **en cascade** ses **marchés** et leurs prévisions — le tout dans la **même transaction** (la cascade ne touche **que** les enfants de la cible). *(Côté SGBD, un filet de sécurité distingue désormais les violations FK / doublon / valeur obligatoire.)*

**Module 03 — Soumission & retours**

- Soumission du dossier [Action]
  - Envoi officiel avec génération de la référence unique.
  - ⚠️ **Règle ajoutée (non issue de la brochure d'origine)** : **un PPM doit comporter au moins
    un marché avant soumission**. La soumission d'un dossier de type **PPM** sans aucune ligne de
    marché est **refusée (HTTP 409)**. Ne s'applique **qu'au type PPM** — les **DAO/MAOO** ne sont
    pas concernés. Justification : un PPM est un plan de passation de marchés ; un PPM vide n'a rien
    à soumettre au contrôle. Le frontend doit s'aligner sur cette précondition.
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
    créer la pièce de code `AGPM` sur le type de dossier PPM.
- Suivi de l'état de réception [Lecture]
  - Accès à réception, date, secrétaire — en temps réel.
- Consultation du PV d'examen [Lecture]
  - Accès en lecture au PV signé : référence, avis, synthèse des observations non conformes.
- Soumission du dossier corrigé [Action]
  - Dépôt en retour avec corrections basées sur les observations du PV.

**Module 11 — Retrait de dossier**

- Demande de retrait motivée [Action]
  - Demande de retrait d'un dossier déjà enregistré. Motif obligatoire (MOTIF_RETRAIT NOT NULL dans t_demande_retrait).
  - ⚠️ **Règle ajoutée** : la PRMP demandeuse est **l'utilisateur authentifié** (JWT), jamais le corps ; l'`ID_DEMANDE_RETRAIT` est **auto-généré**. Gardes (sinon 403/409) : **être propriétaire** du dossier, dossier **`SOUMIS` ou `PRET_DISPATCH`**, et **pas de demande déjà `EN_ATTENTE`** pour ce dossier. Liste déroulante des dossiers retirables : `GET /api/dossiers/retirables`.
- Suivi de la demande [Lecture]
  - Consultation du statut : **EN_ATTENTE / ACCEPTEE / REFUSEE** (⚠️ règle ajoutée). Ses demandes : `GET /api/demande-retraits`.
- Notification décision [Lecture]
  - Reçoit **RETRAIT_ACCEPTE** ou **RETRAIT_REFUSE**. ⚠️ **Règle ajoutée** : si **accepté**, le dossier **repasse en `BROUILLON`** (et non `RETIRE`) ; si refusé, dossier inchangé (motif de refus optionnel).

**Module 04 — Calendrier & notifications**

- Calendrier des jalons [Lecture]
  - Lancement, ouverture, attribution — alertes J-7 et J-1.
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
- Mandat de 3 ans non renouvelable automatiquement — expiration = DATE_NOMIN + 3 ans (t_prmp)
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
  - Une fois le projet accepté, un Président réel co-signe en renseignant DATE_SIGNATURE_PRESIDENT **et IM_CTRL_PRESIDENT (= son matricule)** dans t_pv_examen. Le service authentifie le signataire : profil PRESIDENT requis (403 sinon), et le co-signataire doit être **différent du Membre signataire** (auto-co-signature interdite). Facultatif si c'est le CC qui co-signe — contrainte t_pv_examen_cosignataire_check garantit qu'au moins l'un des deux signe.
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
- Réception copie du dossier [Lecture]
  - Copie formelle via t_copie_dossier (TYPE_COPIE = DISPATCH_CC) + notification DISPATCH_CC.
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
  - Une fois le projet accepté, le CC **de la localité du dossier** co-signe en renseignant DATE_SIGNATURE_CC **et IM_CTRL_CC (= son matricule)**. Le service authentifie le signataire : profil CHEF_COMMISSION **et localité du dossier** requis (403 sinon), co-signataire **différent du Membre** (auto-co-signature interdite). Facultatif si c'est le Président qui co-signe — contrainte cosignataire garantit qu'au moins l'un des deux signe.

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
- Soumission du projet au Président/CC [Action]
  - Passage en PROJET_SOUMIS → insertion dans t_pv_navette (SENS = SOUMISSION, NUM_NAVETTE incrémenté) → notification PROJET_PV_SOUMIS envoyée au Président/CC destinataire.
- Rectification sur retour [Écriture]
  - Si le Président/CC retourne le projet (EN_RECTIFICATION, SENS = RETOUR_RECTIF dans t_pv_navette), le Membre corrige la synthèse et/ou l'avis puis resoumet. Le cycle peut se répéter — NB_NAVETTES incrémenté à chaque retour.
- Signature définitive du PV [Écriture]
  - Quand le projet est accepté (PROJET_ACCEPTE), **le Membre attributaire du PV** (IM_CTRL_MEMBRE) signe en renseignant DATE_SIGNATURE_MEMBRE dans t_pv_examen. Cette signature **n'est pas déléguable** : le service refuse (403) tout autre signataire que le Membre attributaire. Le PV passe à SIGNE quand DATE_SIGNATURE_MEMBRE ET (DATE_SIGNATURE_PRESIDENT ou DATE_SIGNATURE_CC) sont renseignées — le co-signataire devant être **une personne différente** du Membre (auto-co-signature interdite).
  - **Identité du signataire** : pour chaque signature, le service enregistre l'identité de l'**utilisateur authentifié** (CurrentUser, principal JWT) dans IM_CTRL_MEMBRE / IM_CTRL_PRESIDENT / IM_CTRL_CC ; le champ `imActeur` du corps de requête n'est **pas** utilisé pour l'identité (non falsifiable).

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
  - OBS_LEVEES = true → clôture automatique (CLOTURE) ; OBS_LEVEES = false → ⚠️ **règle ajoutée** : le dossier passe en **`EN_ATTENTE_DECISION_PRMP`** (il ne reste **plus** en EN_VERIFICATION). L'observation est **transmise à la PRMP** du dossier (notification `OBSERVATION_VERIFICATION` : référence dossier, vérificateur, texte de l'observation, date) et l'événement est **tracé dans `t_audit_log`** (NOM_TABLE=`t_verification`, CHAMP_MODIFIE=`OBSERVATION_NON_LEVEE`, IM_ACTEUR=vérificateur). C'est ensuite la **PRMP** qui prend connaissance des observations, rectifie le dossier, puis décide de la suite.
  - **Lecture seule côté vérificateur** : un dossier `EN_ATTENTE_DECISION_PRMP` **reste visible dans « à vérifier »** (`GET /api/dossiers/a-verifier` retourne `EN_VERIFICATION` **ou** `EN_ATTENTE_DECISION_PRMP`) — il ne disparaît de la liste qu'une fois `CLOTURE`. Il figure aussi dans la sous-vue `GET /api/dossiers/en-attente-prmp`. Dans les deux cas il est en **lecture seule** : il **ne peut plus être ni modifié ni re-vérifié** tant que la PRMP n'a pas statué (nouvelle vérification → 409).
  - ⚠️ **Règle ajoutée — resoumission après rectification** : la PRMP propriétaire resoumet le dossier rectifié via `POST /api/dossiers/{id}/resoumettre` avec un **motif obligatoire** (vide → 400). Le dossier repasse en **`EN_VERIFICATION`** (retour au vérificateur). Le **vérificateur du dossier** est notifié (`RECTIFICATION_PRMP` : référence, nom PRMP, motif, date), l'événement est **tracé** dans `t_audit_log` (NOM_TABLE=t_dossier, TYPE_ACTION=RECTIFICATION_PRMP, IM_ACTEUR=PRMP, CHAMP_MODIFIE=motifRectification), et le **motif est enregistré** sur la dernière vérification (`t_verification.MOTIF_RECTIF`) pour être **visible dans les passages** côté vérificateur.
  - ⚠️ **Règle ajoutée** : la vérification n'est possible que si **PV `SIGNE` + avis `FAVR` + dossier `EN_VERIFICATION`** (sinon 403/409). **Seul le profil Contrôleur vérificateur** peut vérifier — **pas de délégation** CC/Président pour cet acte. L'**identité** enregistrée (`IM_CTRL_VERIF`) et la **date** proviennent du **JWT / serveur**, jamais du corps de requête. L'`ID_VERIFICATION` est **auto-généré** (IDENTITY).
- Historique des échanges d'un dossier clôturé [Lecture]
  - ⚠️ **Règle ajoutée** : `GET /api/dossiers/{id}/historique-echanges` (accessible **PRMP** et **Contrôleur vérificateur**, + Admin) retourne, pour un dossier **`CLOTURE`** uniquement (sinon 403), le **fil chronologique entrelacé** (chaque observation suivie de la rectification PRMP qui y répond) : observations du vérificateur (`t_verification` : date, vérificateur, texte, `obsLevees` — dont le passage final `obsLevees=true`) et rectifications de la PRMP (`t_audit_log` : date, PRMP, motif).
- Déclenchement de la clôture [Auto]
  - ⚠️ **Règle ajoutée** : `OBS_LEVEES = true` clôture le dossier **uniquement s'il est `EN_VERIFICATION`** (`declencherCloture` conditionnelle — fin de la clôture inconditionnelle). Notifie `CLOTURE_ELIGIBLE` au Chargé de publication.

**Module 04 — Messagerie**

- Notifications reçues [Lecture]
  - Alertes PV_SIGNE et retours de dossier de sa localité.
- Messagerie interne [Action]
  - Échange avec son Membre et ses Assistants contrôleurs.

**Module 01 — Tableau de bord**

- Pipeline de ses dossiers [Lecture]
  - Vue des dossiers en attente de vérification (PV signé) et des dossiers récemment clôturés ou retournés.
  - ⚠️ **Règle ajoutée** — files scopées localité : **« à vérifier »** (`GET /api/dossiers/a-verifier` — dossiers `EN_VERIFICATION`) et **« vérifiés / clôturés »** (`GET /api/dossiers/verifies`, paginé, lecture seule — PV signés au statut `CLOTURE`, **y compris les auto-clôturés** FAV/DEF/NSP).

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

- Localités, seuils, règles de passation [Écriture]
  - Paramétrage complet de tr_localite, t_seuil, t_regle_passation.
- Grilles de contrôle & règles d'anomalie [Écriture]
  - Configuration de tr_points_ctrl et t_regle_anomalie.
- Comptes budgétaires & entités contractantes [Écriture]
  - Plan comptable tr_compte et répertoire tr_entite_contract.
- Délégations de profil [Écriture]
  - Gestion des entrées t_delegation_profil — quels profils peuvent exercer les tâches d'autres profils.

**Module 10 — Administration & sécurité**

- Gestion des comptes contrôleurs & PRMP [Écriture]
  - Création, modification, désactivation — rattachement hiérarchique (ID_SUPERIEUR) et localité.
- Gestion de la hiérarchie [Écriture]
  - Affectation des supérieurs (ID_SUPERIEUR) — construction de l'arbre via v_hierarchie_controleurs.
- RBAC — contrôle d'accès par rôle [Auto]
  - Chaque profil n'accède qu'aux modules autorisés via tr_profile et t_delegation_profil.

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
  - Par table, utilisateur, type d'action ou plage de dates.
- Export CSV/Excel [Action]
  - Pour analyse externe ou transmission à un organe de contrôle supérieur.

**Restrictions / contraintes :**

- Profil haut privilège — toutes actions tracées dans t_audit_log

---

## Légende des marqueurs

- **[Lecture]** : consultation seule
- **[Écriture]** : création / modification de données
- **[Action]** : déclenche une transition d'état ou un acte métier
- **[Auto]** : comportement automatique du système (trigger, calcul, notification)
