# VickTV — Référence technique et suivi du projet

Dernière mise à jour : **21 octobre 2026**. Version de référence : **0.1.0**, `versionCode = 1`.

Ce document est le point d’entrée pour reprendre le travail avec Kev, un développeur ou une IA. Il décrit le comportement présent, les décisions prises et les validations restantes. Le code reste l’autorité sur le comportement exécuté; toute évolution significative doit être réconciliée ici.

## 1. But et contexte

VickTV est un lecteur Xtream Codes personnel, conçu pour Vicky et Kev. Il vise une utilisation simple à la télécommande et un meilleur contrôle des pistes audio après des problèmes de son et de vidéodescription rencontrés avec TiviMate et le fournisseur Edge.

L’installation prévue comporte **deux Fire Stick, sur deux téléviseurs, avec deux comptes Xtream distincts**. Les appareils ont été achetés environ deux ans avant le début du projet; leurs modèles exacts et leurs versions de Fire OS restent à confirmer. Aucun compte réel n’a été fourni au projet pour les essais initiaux.

Le lecteur ne fournit aucune chaîne ni aucun abonnement. Il ne peut pas recréer une piste audio absente du flux ou retirer une narration déjà mélangée à l’unique piste reçue.

## 2. Carte de la documentation

| Document | Rôle |
| --- | --- |
| [README](../README.md) | Présentation, installation et utilisation |
| **Ce document** | Architecture, décisions, publication, état et prochaines étapes |
| [Notes de version](RELEASE_NOTES.md) | Comportements et limites de la version distribuée |
| [AGENTS.md](../AGENTS.md) | Consignes pour les interventions sur le dépôt |

Ne pas créer un deuxième document maître pour les mêmes informations. Le détail d’un incident peut vivre dans une issue; son résultat et les décisions durables reviennent ici.

## 3. Périmètre de la version 0.1.0

| Fonction | État dans le code |
| --- | --- |
| Connexion Xtream | Serveur, utilisateur et mot de passe; validation avant sauvegarde |
| Accueil TV | Français, Sports, Films, Séries, Favoris, Toutes les chaînes, Réglages |
| Catalogue | Catégories originales, recherche, films et liste des épisodes |
| Favoris | Chaînes, films, séries et épisodes; sauvegarde locale |
| Lecture | Moteur Media3 ExoPlayer; direct MPEG-TS par défaut, HLS en option |
| Audio | Liste des pistes, choix manuel, mémorisation et préférence FR/stéréo |
| Mode démo | Exploration sans compte; aucun flux de démonstration inclus |
| Mots d’amour | 50 textes distincts en rotation persistante, option désactivable |
| Mise à jour | Vérification manuelle, téléchargement, contrôles et ouverture de l’installateur Android |
| EPG horaire, VLC, synchronisation entre TV | Non implémentés |
| Reprise VOD après fermeture | Non persistante; position gardée en mémoire pendant la vie de l’activité |

### Affichage des mots d’amour

La demande produit est un petit mot lorsque Vicky ouvre la télévision. **Le déclencheur réellement implémenté est le lancement de VickTV ou son retour au premier plan lorsque l’accueil est visible.** Le démarrage du téléviseur n’est pas intercepté. Un retour directement dans le lecteur ne déclenche pas actuellement ce message.

L’index avance à chaque affichage, reste propre à chaque appareil, puis revient au premier texte après le cinquantième. Aucune synchronisation des deux compteurs. Tester le comportement réel de veille/réveil avant de promettre un message à chaque allumage.

## 4. Architecture actuelle

Une application Android installée localement, sans serveur personnel ni service cloud de comptes.

| Composant | Responsabilité | Fichier |
| --- | --- | --- |
| `MainActivity` | Écrans, navigation, cycle de vie, dialogues et orchestration de la lecture | [MainActivity.kt](../app/src/main/java/ca/vicktv/MainActivity.kt) |
| `Xtream` | Authentification, catégories, chaînes, VOD, séries, épisodes et URL des flux | [Xtream.kt](../app/src/main/java/ca/vicktv/Xtream.kt) |
| `CatalogRules` | Normalisation, regroupements Français/Sports et validation des extensions | Même fichier que `Xtream` |
| `Network` | HTTP et lecture bornée des réponses JSON | Même fichier que `Xtream` |
| `Store` | Compte chiffré, favoris et préférences locales | [Store.kt](../app/src/main/java/ca/vicktv/Store.kt) |
| `LoveNotes` | Les 50 messages et l’accès cyclique à leur index | [LoveNotes.kt](../app/src/main/java/ca/vicktv/LoveNotes.kt) |
| `Updater` | Lecture du manifeste, téléchargement et contrôle du paquet avant installation | [Updater.kt](../app/src/main/java/ca/vicktv/Updater.kt) |

Les appels au catalogue et les téléchargements passent dans un exécuteur à un seul thread. L’interface est mise à jour sur le thread principal. Un compteur `requestId` empêche d’afficher une réponse sur un écran devenu obsolète; il n’annule pas l’appel réseau déjà parti. Les réponses JSON sont limitées à environ 32 millions de caractères, avec des délais réseau de 15 secondes à la connexion et 25 secondes en lecture.

Les catalogues sont chargés en mémoire à la navigation. Il n’y a pas de cache persistant du catalogue ni de base de données dédiée. Les détails des favoris restent sauvegardés, mais ne garantissent pas qu’un contenu retiré par le fournisseur soit encore lisible.

L’arrêt de l’activité libère le lecteur et les rappels de reconnexion. La reprise de lecture sur le même processus utilise la position gardée en mémoire. Les erreurs de lecture déclenchent jusqu’à trois tentatives consécutives; le compteur est réinitialisé lorsque le lecteur atteint de nouveau l’état prêt.

## 5. Décisions techniques retenues

| ID | Décision | Pourquoi et conséquence |
| --- | --- | --- |
| D01 | Kotlin et vues Android natives | Base adaptée à Fire OS Android et aux interactions D-pad. Pas de moteur Web ou de framework multiplateforme pour ce MVP. |
| D02 | AndroidX Media3 ExoPlayer | Réutiliser un moteur existant plutôt que développer les codecs. La prise en charge audio reste liée aux capacités du matériel. |
| D03 | Application autonome, sans backend | Deux appareils personnels ne justifient pas un serveur de comptes, une infrastructure ou une synchronisation cloud. |
| D04 | Un compte Xtream par appareil | Les deux abonnements restent indépendants. Les identifiants sont entrés après installation, jamais intégrés à l’APK. |
| D05 | AES-GCM et Android Keystore | Chiffrer les identifiants au repos avec une clé propre à l’appareil. Les favoris et réglages restent dans les préférences ordinaires de l’app. |
| D06 | GitHub Releases pour les APK et mises à jour | Lien stable et notes de version, sans serveur d’hébergement supplémentaire. La distribution doit être publique pour fonctionner sans compte GitHub dans le lecteur. |
| D07 | Même paquet et même clé de signature | Permettre les mises à jour sur place et préserver les données. Ne jamais remplacer la clé initiale par une nouvelle pour contourner une difficulté de build. |
| D08 | Préférence de piste mémorisée par contenu | Stocker une empreinte langue/libellé/codec/canaux/rôles plutôt qu’un simple numéro de piste, qui pourrait changer dans le flux. |
| D09 | Catégories Français/Sports heuristiques | Démarrer sans personnaliser les noms propres à Edge. Les catégories originales restent accessibles; les correspondances peuvent être imparfaites. |
| D10 | Rotation locale des 50 messages | Fonctionnement hors ligne et sans service externe; deux appareils peuvent afficher des messages différents. |
| D11 | Structure simple avant découpage supplémentaire | `MainActivity` regroupe encore beaucoup de responsabilités. Extraire progressivement écrans, catalogue ou lecteur si leur évolution le justifie; ne pas présenter le MVP comme une architecture MVVM déjà réalisée. |
| D12 | Installation système confirmée par l’utilisateur | Le bouton télécharge et prépare l’APK, puis ouvre l’installateur Android. Ce n’est pas une installation silencieuse. |

### Outils et versions de référence

| Élément | Version / valeur |
| --- | --- |
| Identifiant Android | `ca.vicktv` |
| Kotlin | 2.0.21 |
| Android Gradle Plugin | 8.7.3 |
| Gradle Wrapper | 8.9 |
| JDK | 17 |
| `compileSdk` / `targetSdk` | 35 / 35 |
| `minSdk` | 26 : Android 8+; Fire OS 7+ visé |
| Media3 | 1.4.1, modules ExoPlayer, HLS et UI |
| AndroidX Core KTX | 1.13.1 |
| Tests unitaires | JUnit 4.13.2 |

Ces versions sont la base réellement compilée, pas une affirmation qu’il s’agit des dernières disponibles. Toute montée de version demande une vérification de compatibilité et un essai audio sur appareil. Les Fire Stick sous Vega OS ne peuvent pas utiliser cet APK Android.

## 6. Audio : fonctionnement et limites

1. Le lecteur préfère le français lorsque le flux le permet.
2. Une préférence manuelle sauvegardée est restaurée si son empreinte correspond à une piste disponible et prise en charge.
3. À défaut, une piste française non marquée comme décrivant la vidéo est recherchée. Le marquage dépend du fournisseur.
4. L’utilisateur peut ouvrir **Audio / Son**, ou utiliser la touche Menu, pour choisir une autre piste prise en charge.
5. Le réglage stéréo privilégie les pistes disponibles à deux canaux; il ne prouve pas qu’un Dolby reçu sera converti correctement.

Le panneau Infos décrit les pistes reçues, le codec, le nombre de canaux et la définition vidéo. **Il ne mesure pas la sortie HDMI réelle.** Ne pas déclarer le 5.1 validé sans essai du Fire Stick avec le téléviseur et, s’il y a lieu, la barre de son ou l’amplificateur.

Il n’y a pas encore de second moteur VLC, de réglage de passthrough dédié dans l’interface ou de transcodage audio logiciel ajouté au projet. La compatibilité AC-3/E-AC-3 reste à tester. VickTV peut subir une incompatibilité similaire à celle d’un autre lecteur utilisant un moteur apparenté.

## 7. Données et confidentialité

- Le compte est chiffré dans les préférences avec une clé AES du Keystore Android. Le chiffrement local et la signature de l’APK sont deux mécanismes et deux clés différents.
- `allowBackup=false` : pas de sauvegarde Android automatique du compte. Désinstaller l’application ou effacer ses données supprime les paramètres et nécessite une nouvelle connexion.
- Les favoris, choix audio et compteur des messages sont locaux. Changer de serveur ou de nom d’utilisateur vide les favoris; il n’existe pas encore de profil cloud.
- Aucune télémétrie ajoutée à l’application. Les requêtes de lecture vont au fournisseur; celles de mise à jour vont à GitHub et à son hébergement de fichiers.
- HTTP reste accepté pour les fournisseurs qui l’exigent, avec un avertissement avant connexion. Il ne chiffre pas les identifiants en transit; utiliser HTTPS quand le fournisseur le permet.
- Ne jamais inclure les comptes, URL complètes de flux authentifiés, fichiers de signature, mots de passe ou jetons GitHub dans le dépôt, les issues ou les journaux partagés.

## 8. Distribution et mise à jour

### Contrat de distribution

Dépôt : [kcote84/VickTV](https://github.com/kcote84/VickTV). Version initiale : [v0.1.0](https://github.com/kcote84/VickTV/releases/tag/v0.1.0).

Le 21 octobre 2026, Kev a autorisé explicitement la visibilité publique du code et des APK. Le passage en public a été appliqué dans GitHub après sa vérification d’identité. La visibilité publique a été confirmée, puis le manifeste et l’APK 0.1.0 ont été téléchargés sans authentification. L’APK téléchargé contient 4 310 803 octets et son SHA-256 correspond au manifeste. Refaire ces contrôles après tout changement de visibilité.

Chaque release distribuée doit contenir :

- `VickTV.apk` : APK **release** signé avec la clé initiale.
- `update.json` : `versionName`, `versionCode`, `notes`, `apkUrl` et `sha256`.
- `SHA256SUMS.txt` : empreinte du fichier téléchargeable.

Le bouton **Vérifier les mises à jour** lit :

```text
https://github.com/kcote84/VickTV/releases/latest/download/update.json
```

Il compare `versionCode`, puis propose les notes et le téléchargement. Avant l’installation, le code contrôle le préfixe de l’URL GitHub autorisée, la taille maximale de 128 Mio, l’empreinte SHA-256, le nom du paquet, sa version et le certificat correspondant à l’app installée. Le paquet est partagé avec l’installateur via `FileProvider` et une permission de lecture temporaire.

L’utilisateur doit autoriser VickTV comme source d’installation. Après ce réglage, un bouton permet de reprendre l’installation téléchargée. La permission et le parcours exact restent à valider sur les deux Fire Stick. Aucun jeton GitHub ne doit être embarqué pour accéder à un dépôt privé.

### Signature à préserver

Alias de signature : **`vicktv`**. La clé privée et ses mots de passe sont conservés hors dépôt dans une sauvegarde privée. Avant une future publication, retrouver cette sauvegarde auprès du propriétaire. Ne jamais commettre ses fichiers ou documenter leurs valeurs ici.

Variables utilisées par le build release : `VICKTV_KEYSTORE`, `VICKTV_STORE_PASSWORD`, `VICKTV_KEY_PASSWORD`. Sans elles, ne pas distribuer un build debug comme mise à jour de production. La signature debug ne correspond pas à la clé release.

### Workflows existants

| Workflow | Déclencheur | Rôle / prérequis |
| --- | --- | --- |
| `android.yml` | Push sur `main`, pull request ou lancement manuel | Tests, lint et APK debug de vérification; aucun secret de signature nécessaire |
| `publish-apk.yml` | Changement de `distribution/`, du workflow, ou lancement manuel | Vérifie l’APK déjà signé et le publie dans Releases; ne compile pas l’application |
| `release.yml` | Tag `v*` ou lancement manuel | Compile et publie un release signé; exige les trois secrets de signature configurés séparément |

Le premier transfert utilise des morceaux binaires `distribution/apk.part000` à `apk.part014`, concaténés par `publish-apk.yml` lorsque `distribution/VickTV.apk` est absent. C’est un mécanisme de transfert initial : les utilisateurs doivent télécharger **l’APK dans Releases**, jamais les morceaux. Leur empreinte reconstruite a été vérifiée avant publication.

Pour une future distribution, fournir un APK complet ou remplacer **tous** les anciens morceaux, y compris en supprimant ceux devenus inutiles. Ne jamais laisser un ancien `VickTV.apk` masquer de nouveaux morceaux. Le workflow refuse de republier un numéro de release déjà existant.

La compilation GitHub signée est une option préparée; ne pas supposer que ses secrets sont configurés. Utiliser une seule voie de publication pour une version donnée.

## 9. Reprendre et publier une évolution

1. Lire ce document, `AGENTS.md` et les notes de la version actuellement distribuée. Vérifier la branche et le dernier tag avant de modifier quoi que ce soit.
2. Vérifier l’état réel des deux appareils et les résultats d’essais; ne pas supposer qu’un problème a été corrigé parce que le code compile.
3. Réaliser une modification fonctionnelle ciblée et tenir ce document à jour si elle change un comportement ou une décision.
4. Augmenter `versionCode` et `versionName` pour tout nouvel APK distribué. Mettre à jour `docs/RELEASE_NOTES.md`.
5. Exécuter les vérifications adaptées; pour une livraison Android :

```sh
./gradlew testDebugUnitTest lintDebug assembleRelease
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

6. Avec la clé initiale, préparer le fichier distribué et le manifeste. Depuis la racine du dépôt :

```sh
mkdir -p distribution
cp app/build/outputs/apk/release/app-release.apk distribution/VickTV.apk
python3 tools/release_metadata.py --apk distribution/VickTV.apk \
  --tag vX.Y.Z --output distribution/update.json \
  --notes "Décrire les changements de cette version."
(cd distribution && sha256sum VickTV.apk > SHA256SUMS.txt)
```

`vX.Y.Z` doit correspondre exactement à `versionName`. Le script impose cette correspondance. Fournir `--notes` pour éviter de réutiliser le texte par défaut de la première version.

7. Publier via `publish-apk.yml`, ou configurer au préalable les secrets `VICKTV_KEYSTORE_BASE64`, `VICKTV_STORE_PASSWORD` et `VICKTV_KEY_PASSWORD` et utiliser `release.yml` avec un tag existant correspondant.
8. Vérifier la release, son manifeste et son téléchargement sans authentification. Une publication de sources seule ne produit pas automatiquement un nouvel APK release signé.
9. Tester la mise à jour sur le premier Fire Stick en conservant les données, puis installer sur le second après validation.

Pour un simple changement de documentation, ne pas générer de nouvel APK et ne pas augmenter la version de l’application.

## 10. État de validation et travail restant

### Éléments établis pour 0.1.0

- Build release signé généré avec JDK 17 et SDK 35.
- Quatre tests unitaires réussis : catégories françaises, catégories sportives, extensions de fichier et 50 messages distincts/rotation.
- Analyse Android `lintDebug` réussie et signature APK validée avec `apksigner`.
- Workflow de publication terminé avec succès et release 0.1.0 créée avec ses trois fichiers.
- Distribution publique confirmée : manifeste et APK accessibles sans authentification, empreinte du téléchargement conforme.
- Le contrôle CI des tests, du lint et du build debug a réussi; la finalisation de l’exécution GitHub peut être vérifiée dans Actions.

SHA-256 de l’APK initial :

```text
ef558402d469c1c18e7405d505b09dcea2449caa5c63840431b51d49d1d7f705
```

Ces contrôles ne couvrent pas encore la connexion à Edge, la restitution audio, les interactions réelles de la télécommande ou l’installation d’une seconde version sur Fire Stick.

### Prochaine étape : premier essai matériel

| Priorité | Action | Preuve attendue |
| --- | --- | --- |
| 1 | Identifier les deux modèles et leurs versions Fire OS | Modèle/OS notés, compatibilité confirmée |
| 2 | Installer 0.1.0 sur une seule TV | Lancement, navigation sans souris et mode démo fonctionnels |
| 3 | Connecter le premier compte Xtream sur l’appareil | Catégories, direct, un film et un épisode accessibles |
| 4 | Tester les chaînes avec problèmes de son | Pistes proposées, piste choisie, effet réel sur narration/stéréo/5.1 |
| 5 | Tester veille, reprise et messages d’amour | Déclenchement observé et écart éventuel avec le souhait « à chaque ouverture TV » |
| 6 | Essayer le bouton de mise à jour | Manifeste public accessible; mise à jour sur place à tester lors de la prochaine version |
| 7 | Installer sur la deuxième TV avec son propre compte | Indépendance des comptes et absence de régression |

Après ces essais, corriger les problèmes bloquants de lecture, audio ou navigation avant d’ajouter EPG, moteur VLC, saisie par téléphone ou synchronisation. Ce sont des pistes d’évolution, pas des fonctionnalités déjà réalisées ou promises pour une date.

## 11. Journal des décisions et des suivis

| Date | Événement | Suite |
| --- | --- | --- |
| 2026-10-21 | Nom VickTV choisi; MVP pour deux Fire Stick et 50 messages d’amour | Conserver l’identité et les contraintes produit |
| 2026-10-21 | Première version Kotlin/Media3 compilée, signée et publiée | Validation matérielle en attente |
| 2026-10-21 | Correction CI : retrait du paquet SDK `tools` obsolète; installation explicite de `platform-tools` | Configuration corrigée dans les trois workflows |
| 2026-10-21 | Accord de Kev, passage en public appliqué après vérification GitHub Mobile; téléchargements anonymes et SHA-256 vérifiés | Maintenir cette référence; essais sur Fire Stick et mise à jour sur place encore à faire |

À chaque séance, ajouter uniquement les changements significatifs et leurs validations. Pour un résultat matériel, consigner version de l’app, appareil/OS, scénario, résultat et prochaine action, sans identifiants ni URL authentifiées.
