# ADR-0014 : Le DAO complet est assemblé par Word, sur le serveur

**Statut :** Adopté
**Date :** 2026-10-06
**Origine :** demande front `frontend/docs/demande-backend-2026-10-06-dao-complet.md` (§B1-B3) ; arbitrages du pilote du 06/10 :
un seul document qui remplace les documents séparés, spécifications techniques jointes en Word, **assemblage avec Word sur le
serveur** (choix posé entre Word et un assemblage par POI seul).

## Contexte

La fiche DAO produit, à chaque validation, des documents séparés (DPAO, AE, CCAP, formulaires…) remplis sur les documents types de
l'ARMP (lots D à D4), en `.docx` par POI et en `.pdf` par OpenPDF. Les DAO réels sont **un seul document** : page de garde, sommaire
avec numéros de page, six parties dans l'ordre des documents types, en-tête et pagination continue « page n / N ». Deux de ces
parties, les Instructions aux candidats et le CCAG, sont des **textes fixes** de 30 à 60 pages fournis en `.doc`, aux tableaux et
numérotations riches ; une autre, les spécifications techniques, est un Word quelconque fourni par la PRMP (plans, images).

Trois exigences ne sont pas à la portée de POI et d'OpenPDF :
1. **fusionner des `.docx` hétérogènes** (styles, numérotations, sections, images) sans les abîmer ;
2. **paginer** : seul un moteur de mise en page sait où tombent les pages, donc remplir le sommaire du PDF et le « / N » ;
3. **convertir fidèlement** le tout en PDF (la conversion OpenPDF du lot 2a ne rend que du texte simple).

Le projet emploie déjà Word par automation (documents4j) pour les PV ; ces tests sont marqués `word` et exclus de la CI.

## Décision

1. **Word assemble et convertit.** `DaoCompletWord` écrit les parties dans un dossier temporaire et lance un script PowerShell
   (`word/assembler-dao.ps1`) qui pilote Word par COM : création du document, page de garde, champ TOC limité au style « Partie
   DAO », une section par partie (`InsertFile`), en-tête et pied communs (`PAGE` / `NUMPAGES`), mise à jour des champs, puis
   `SaveAs2` en `.docx` et en `.pdf`. Le séparateur de liste du champ TOC est lu sur la locale de Word (« ; » en français).
2. **Un processus par assemblage**, avec un délai (300 s) qui tue l'arbre de processus, et **un assemblage à la fois**
   (`synchronized`) : un Word bloqué (macro, boîte de dialogue) ne bloque ni l'application ni le suivant. Word est lancé
   invisible, `DisplayAlerts = 0`, `AutomationSecurity = 3` (macros désactivées).
3. **On assemble, on ne réécrit pas** : les documents du lot D restent la source de chaque partie (H1 de la demande), ce qui garde
   valables les preuves partie par partie.
4. **Les textes fixes** sont convertis **une fois** par Word (`.doc` → `.docx`) et rangés dans `modeles/dao-fixes/`.
5. **Dégradation sans bruit** : sans Word (ou `app.dao-complet.actif=false`, ou hors Windows), le DAO complet n'est pas produit,
   la validation passe, et les documents séparés restent servis et joints. Une tâche planifiée rattrape les versions validées
   sans DAO complet (versions antérieures, échecs passés).

## Conséquences

- **Plus facile** : rendu identique à ce que la PRMP verrait en ouvrant le Word ; sommaire et pagination exacts dans le PDF ;
  insertion des spécifications quelles qu'elles soient ; pas de dépendance ajoutée (`pom.xml` inchangé).
- **Plus difficile** :
  - **le serveur de production doit être Windows avec Microsoft Word installé et licencié** pour le compte de service ; sinon le
    DAO complet n'existe pas (les documents séparés le remplacent) ;
  - l'automation de Word côté serveur n'est pas un usage que Microsoft soutient : un délai et l'isolement par processus en
    limitent l'effet, sans l'annuler ;
  - environ 30 s par validation (90 pages), en série ;
  - la CI (Linux) ne teste pas l'assemblage : son test (`DaoCompletWordTest`) est marqué `word`, exécuté en local seulement.
- **Marche arrière** : `app.dao-complet.actif=false` revient au comportement d'avant (documents séparés). Un autre moteur
  (LibreOffice sans interface, docx4j) remplacerait `DaoCompletWord` seul : le plan (`DaoCompletService.parties`) et les points
  de service (liste, jointure, retrait) n'en dépendent pas.

## Questions qui restent ouvertes

- Les classeurs (`xlsx` : bordereau des prix, DQE, tableau de conformité) restent hors du DAO complet ; les y insérer en tableau
  demanderait de les rendre en Word.
- La mise en page des parties produites est celle des modèles du lot D, pas encore celle, stylée, des fichiers Word de l'ARMP.
