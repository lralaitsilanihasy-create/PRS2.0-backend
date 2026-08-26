# ADR-0002 : Secret JWT sans valeur par défaut (fail-fast au démarrage)

**Statut :** Adopté
**Date :** 2026-08-26

## Contexte

`application.properties` définissait `app.jwt.secret=${APP_JWT_SECRET:dev-secret-please-change-0123456789-abcdefghij}` : une valeur de repli codée en dur, versionnée dans le dépôt. Quiconque a accès au code — y compris a posteriori, via l'historique git — connaît cette chaîne exacte. Un déploiement qui oublie de positionner `APP_JWT_SECRET` ne plantait pas : il démarrait silencieusement en signant tous ses jetons avec ce secret public. N'importe qui le lisant peut alors forger un JWT valide pour n'importe quel rôle, y compris ADMINISTRATEUR — une valeur par défaut ici est pire que l'absence de valeur, car l'application semble fonctionner normalement pendant que l'authentification entière est compromise.

## Décision

Supprimer entièrement la valeur par défaut (`app.jwt.secret=${APP_JWT_SECRET:}`) et ajouter une garde dans `SecurityConfig#jwtSecretKey` qui lève une `IllegalStateException` au démarrage si le secret est absent ou vide, s'il fait moins de 32 octets (exigence HS256), ou s'il correspond encore à l'ancienne valeur de développement publiée (vérification explicite du préfixe `dev-secret-please-change`, en ceinture et bretelles, au cas où quelqu'un la recopierait littéralement dans la variable d'environnement).

En développement, `start-backend.ps1` génère un secret aléatoire de 48 octets à la première exécution et le conserve dans `.env.local` à la racine du projet (hors des deux dépôts git) — stable entre les relances pour ne pas invalider les sessions locales à chaque redémarrage.

## Conséquences

**Plus facile :**
- Un secret manquant dans n'importe quel environnement (dev, CI, prod) devient un échec de démarrage immédiat et explicite, au lieu d'une faille silencieuse.

**À surveiller :**
- La CI doit désormais fournir `APP_JWT_SECRET` explicitement (visible dans `.github/workflows/ci.yml`) : ce n'est plus une commodité optionnelle, c'est un prérequis dur au démarrage de l'application, partout.
- La perte de `.env.local` en développement invalide toutes les sessions locales — sans conséquence en dev, mais à ne pas reproduire pour un environnement partagé.
- L'ancienne valeur `dev-secret-please-change-0123456789-abcdefghij` doit être considérée comme définitivement grillée (publique) : ne plus jamais l'utiliser, même temporairement.

## Marche arrière

Restaurer la chaîne par défaut dans `application.properties` et retirer la garde de `SecurityConfig#jwtSecretKey`. Non recommandé : cela rouvre exactement la faille refermée ici. Si envisagé malgré tout, générer au minimum un secret neuf jamais committé — jamais réutiliser la valeur déjà publiée.
