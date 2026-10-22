# VickTV — Référence technique et suivi du projet

Dernière mise à jour : **21 octobre 2026**. Version distribuée : **0.6.0**, `versionCode = 12`, première étape de simplification et favoris protégés (section 27). Version précédente : **0.5.2**, `versionCode = 11`. Version initiale conservée : **0.1.0**, `versionCode = 1`. Les sections historiques décrivent les étapes successives; la dernière section fait foi pour l’état actuel.

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

L’arrêt de l’activité libère le lecteur et les rappels de reconnexion. La reprise de lecture sur le même processus utilise la position gardée en mémoire. Les erreurs de lecture déclenchent jusqu’à trois relances consécutives, sauf les statuts HTTP 401, 403, 404 et 410 signalés sans relance supplémentaire depuis 0.4.2; le compteur est réinitialisé lorsque le lecteur atteint de nouveau l’état prêt. Les tentatives internes de chargement Media3 restent distinctes de ces relances.

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

## 12. Révision du 21 octobre — sources 0.2.0 en préparation

### Constats et corrections

| Priorité | Constat dans 0.1.0 | Correction dans les sources 0.2.0 |
| --- | --- | --- |
| Haute | Sans piste française normale admissible, la sélection automatique pouvait conserver une piste descriptive; une ancienne préférence descriptive passait aussi en premier | `AudioRules` classe explicitement les pistes prises en charge. L’évitement des pistes descriptives, activé par défaut, passe avant la préférence mémorisée. À défaut de français normal, une autre langue normale reste préférable à la narration. |
| Haute | Le lecteur renvoyait à l’accueil; quitter un épisode perdait le contexte | Sauvegarde en mémoire des listes, filtres, recherche et sélection. Retour sans nouvel appel catalogue. La pile n’est pas persistée après destruction du processus. |
| Moyenne | L’en-tête et les boutons réduisaient la vidéo en permanence | Titre compact et boutons masqués avec les commandes du lecteur, vidéo occupant tout l’espace; les boutons restent visibles lorsqu’ils ont le focus. |
| Moyenne | Retirer un favori dans Favoris laissait la ligne à l’écran | Filtrage par ensemble de favoris courant; bouton d’options visible, accès par touche Menu. |
| Moyenne | Aucun accès à l’horaire des émissions | `get_short_epg`, limite demandée de 24 entrées; décodage Base64, tri, exclusion des heures incohérentes, émission courante et descriptions. Le fournisseur peut retourner moins d’entrées ou aucun guide. Pas de grille multichaîne. |
| Moyenne | Films/Séries sans raccourci français ou québécois; « Canada » incluait des contenus anglophones | Filtres Tous/Français/Québec combinés aux catégories et à la recherche. Reconnaissance VF/VFQ et noms francophones. Classement heuristique, pas une preuve de langue audio ni d’origine géographique. |
| Moyenne | Une réponse réseau d’une ancienne page pouvait modifier l’état occupé de la nouvelle page | L’état occupé est réinitialisé au changement de page et les réponses obsolètes n’y touchent plus. Les requêtes ne sont pas encore annulées au changement de page. |
| Moyenne | Les workflows de publication ne vérifiaient pas explicitement le certificat initial et gardaient les premières notes par défaut dans le manifeste | Vérification `apksigner` et certificat SHA-256 initial dans les deux voies; release existante ignorée avant de comparer les sources à une ancienne distribution; ajout de `--notes-file` au générateur du manifeste. |

Le certificat public initial vérifié depuis l’APK téléchargé est `6a09826ceee768d04367e205731a23a1b03a791c25b150c4cf232db2274d2e16`. Il ne s’agit pas d’une clé privée. Aucun changement de visibilité ou remplacement de la release 0.1.0 effectué pendant cette révision.

### Vérifications et limites

- `testDebugUnitTest`, `lintDebug`, `assembleDebug` et `compileReleaseKotlin` réussis sur les sources finales 0.2.0. **10 tests réussis, zéro échec; lint sans erreur (9 avertissements).**
- Tests unitaires : règles audio (piste descriptive mémorisée, repli de langue, modes stéréo/5.1, piste unique), filtres linguistiques, guide (Base64, ordre, heures invalides et limites d’émission), extensions et rotation des 50 messages.
- Émulateur Android API 35 en format paysage 1920 × 1080; catalogue Xtream et guide synthétiques hébergés localement, sans compte Edge. Vérifiés : accueil, horaire courant/suivant, ajout puis retrait immédiat dans Favoris, filtre Québec excluant le contenu anglophone, retour du lecteur conservant ce filtre. Avec un fichier de test H.264/AAC à deux pistes (première « Français AD » par défaut, deuxième « Français normal »), la piste normale est sélectionnée automatiquement. Le serveur de QA doit diffuser le MKV sans index de recherche ou gérer les requêtes Range; une première fixture non adaptée avait provoqué une erreur de conteneur. Les captures et données de QA restent hors Git.
- Téléchargement anonyme du manifeste et de l’APK 0.1.0 : empreinte conforme au manifeste, signature v2 vérifiée avec `apksigner`. Cela valide la distribution actuelle, pas l’installation d’une seconde version sur Fire Stick.
- `assembleRelease` a été tenté et échoue faute de `VICKTV_KEYSTORE` (configuration production sans `storeFile`). Ne pas remplacer la clé initiale ni distribuer l’APK debug comme une mise à jour. La nouvelle version est une proposition de sources à relire, non une livraison APK.
- YAML des workflows chargé et 10 blocs shell vérifiés avec `bash -n`; génération du manifeste avec `--notes-file` vérifiée localement sur un APK debug, sans publication.
- Aucun Fire Stick connecté. Lecture Edge, AC-3/E-AC-3, sortie HDMI 5.1, passage 0.1.0 → 0.2.0 conservant les données et compatibilité des deux appareils restent à confirmer.

### Suite après révision

Retirer les mentions provisoires « en préparation / non publié » des notes au moment de la publication. Retrouver la sauvegarde de la clé initiale, compiler 0.2.0 avec cette clé, exécuter `apksigner verify`, puis préparer la distribution et tester la mise à jour sur un seul Fire Stick. `distribution/` conserve les fichiers historiques 0.1.0; ne pas les publier comme 0.2.0. Une grille EPG multichaîne, les affiches/synopsis VOD et la reprise persistante restent des améliorations distinctes.

## 13. Distribution 0.2.0 avec la clé originale

La sauvegarde privée fournie par le propriétaire a été récupérée hors Git. Le certificat de la clé a été exporté en mémoire et comparé au SHA-256 initial : correspondance exacte. Les mots de passe sont chargés dans l’environnement du seul processus de compilation; aucun secret n’est ajouté à GitHub.

`testDebugUnitTest lintDebug assembleRelease --no-daemon` a réussi. L’APK release 0.2.0 (code 2, paquet `ca.vicktv`) passe `apksigner verify --verbose`; son certificat est identique à celui de 0.1.0. Taille : **4 319 447 octets**. SHA-256 : `a17ba6ed169eee96c8f5ba890bb5a39f29785a669c49e2a55ab0d8b437a8f404`.

Les 15 morceaux de `distribution/` sont remplacés intégralement et leur concaténation est comparée à l’APK signé; le manifeste et SHA256SUMS sont mis à jour. Cette voie de publication n’envoie pas la clé privée aux workflows. Le suivi de la section 12 décrit le premier blocage, désormais résolu par la sauvegarde originale.

La mise à jour signée a été testée sur émulateur Android API 35 : installation de l’APK public 0.1.0, connexion à un compte Xtream synthétique local, désactivation des messages et ajout d’un favori, puis `adb install -r` de 0.2.0. L’installation réussit sans désinstallation : compte, réglage et favori sont conservés. Ce scénario prouve la compatibilité de signature et des données sur émulateur; il ne valide pas le parcours d’installation Fire OS.

La PR nº 1 a été fusionnée dans `main` au commit `f4a0fef7443461235f0af803871c770d70301bb9`. Le workflow de publication [39656017084](https://github.com/kcote84/VickTV/actions/runs/39656017084) et le contrôle Android sur `main` [39656017143](https://github.com/kcote84/VickTV/actions/runs/39656017143) ont réussi.

La release [v0.2.0](https://github.com/kcote84/VickTV/releases/tag/v0.2.0) contient `VickTV.apk`, `update.json` et `SHA256SUMS.txt`. Le manifeste `releases/latest/download/update.json` propose bien le code 2. L’APK a été téléchargé sans authentification, son empreinte et sa signature sont conformes.

Le bouton de mise à jour du véritable APK 0.1.0 détecte 0.2.0. Une première tentative de téléchargement sur émulateur a retourné une erreur réseau générique; la seconde a téléchargé et validé l’APK puis affiché la demande d’autorisation d’installation. La cause de la première erreur réseau n’a pas été établie; ne pas présenter le téléchargement comme infaillible. Le contrôle du parcours de l’installateur Android est suivi ci-dessous. Les essais Fire Stick et Edge demeurent requis.

En cas de régression sur un appareil : conserver les données, retirer la release défectueuse de la sélection stable si nécessaire, puis préparer un correctif avec un `versionCode` supérieur et la même clé. La version 0.1.0 reste archivée, mais son code inférieur ne permet pas un retour automatique sur place depuis 0.2.0.

## 14. Correctif 0.2.1 — protection du fichier d’installation

Le test complet de l’installateur de 0.1.0 vers 0.2.0 a trouvé un défaut supplémentaire : après le téléchargement validé et l’autorisation de la source, `InstallStaging` ne trouve plus `content://ca.vicktv.files/updates/vicktv.apk` (`ENOENT`). L’émulateur est à 95 % d’occupation, avec environ 327 Mio libres; les journaux `installd` montrent des purges de cache répétées visant environ 594 Mio libres. L’APK de mise à jour était stocké dans le cache privé, qui peut être supprimé par Android pendant cette attente. L’APK lui-même reste valide et s’installe sur place par `adb install -r`.

La version 0.2.1 (code 3) conserve le seul APK préparé dans `filesDir/updates`, et `FileProvider` expose uniquement ce sous-dossier privé avec une permission de lecture temporaire. Un fichier manquant demande un nouveau téléchargement au lieu d’ouvrir un installateur voué à échouer. Il ne s’agit pas d’un élargissement d’accès au reste des fichiers privés. Un seul APK est conservé et remplacé au prochain téléchargement.

La distinction entre stockage persistant et cache purgeable est décrite dans la [documentation Android](https://developer.android.com/training/data-storage/app-specific#internal-remove-cache).

Pour reproduire une mise à jour avant de publier le correctif, les mêmes sources sont compilées en APK de laboratoire avec un script Gradle d’initialisation hors Git : seul le numéro de version est abaissé à 1 (`0.1.0-probe`), avec la clé originale. Cet APK télécharge le véritable APK public 0.2.0, le valide, attend l’autorisation de la source; une purge `adb shell pm trim-caches 1G` est ensuite exécutée. Après autorisation, le fichier reste accessible et l’installateur propose bien « Do you want to update this app? ». L’APK de laboratoire n’est pas distribué.

La production est recompilée sans script d’initialisation : paquet `ca.vicktv`, **0.2.1 (code 3)**, non débogable. `testDebugUnitTest lintDebug assembleRelease` et `apksigner verify --verbose` réussissent. Certificat initial inchangé. APK : **4 319 603 octets**, SHA-256 `2cf284cd9615299ebcb7f0924cf708830d6ae76d0ba49e7aaae92b4c882edd39`. Les 15 morceaux et le manifeste de distribution sont remplacés et vérifiés. La version 0.2.0 reste archivée; la publication 0.2.1 est suivie ci-dessous. Le test de l’installateur dépasse maintenant le point de défaillance : la confirmation de mise à jour est affichée, puis l’image émulateur Google Play demande un scan Play Protect. Ce scan supplémentaire n’a pas été lancé et aucune protection n’a été contournée. Le passage à l’APK de production 0.2.1 est vérifié séparément par `adb install -r`. Le parcours Fire OS reste à confirmer sur appareil.

### Publication du correctif

La PR nº 2 est fusionnée au commit `3c8cce1386d610b8a81e1690391c9046bb06fa61`. La CI du correctif [39658303887](https://github.com/kcote84/VickTV/actions/runs/39658303887) a réussi, puis le workflow de publication [39658680986](https://github.com/kcote84/VickTV/actions/runs/39658680986) a créé la release [v0.2.1](https://github.com/kcote84/VickTV/releases/tag/v0.2.1). Cette version remplace 0.2.0 comme version stable proposée par le manifeste.

Le ZIP privé original est conservé chez le propriétaire; les copies extraites de travail ont été supprimées. Aucun fichier de clé ou de mots de passe n’est suivi par Git ni intégré aux APK. Les outils et APK de laboratoire restent hors du dépôt.

Le contrôle Android des sources finales sur `main` [39658680980](https://github.com/kcote84/VickTV/actions/runs/39658680980) est également réussi.

Le manifeste stable et l’APK 0.2.1 ont été téléchargés sans authentification après publication : code 3, 4 319 603 octets, SHA-256 conforme à la distribution et signature v2 valide. Les APK 0.1.0 et 0.2.0 utilisent encore l’ancien stockage en cache pour leur propre parcours de mise à jour : si ce premier passage échoue faute de fichier, installer manuellement l’APK 0.2.1 par-dessus la version existante, sans désinstaller. Le nouveau stockage privé s’appliquera aux téléchargements des mises à jour suivantes.

Prochaine validation matérielle : installer 0.2.1 sur un seul Fire Stick, connecter Edge, vérifier direct/film/épisode, guide et favoris, puis écouter les chaînes problématiques avec les différentes pistes stéréo/5.1. Aucun essai Fire OS ni restitution HDMI réelle n’est revendiqué par ces tests sur émulateur.


## 15. Guide TV multichaîne — 0.3.0

Demande : un guide beau et simple utilisable à la télécommande sans connaissance technique. Le bouton principal **Guide TV** est placé en premier sur l’accueil; le lecteur et les options des chaînes permettent aussi de l’ouvrir sur la chaîne concernée.

### Composants et choix

- `TvGuideGrid.kt` : vue Android Canvas, seulement cinq lignes dessinées et une fenêtre de deux heures. Le curseur horaire est conservé quand on change de chaîne; gauche/droite passe d’une émission à l’autre et fait avancer la fenêtre. Limite de douze heures depuis la demi-heure courante. Chaînes +/− saute cinq lignes. La sélection, l’émission en cours et le repère Maintenant ont des styles distincts.
- `TvGuideScreen.kt` : présentation, filtres, recherche, aide, favoris et chargement progressif. Les filtres favorisent les favoris existants au premier chargement, sinon Français, sinon Toutes. Recherche combinée au filtre courant; catégories du fournisseur disponibles. Menu propose les actions depuis chaque ligne sans remonter tout le catalogue. Les favoris démo restent en mémoire et ne polluent pas les vrais favoris.
- `Guide.kt` : programmes et découpage pur des cases. Les cases sont coupées aux limites de la fenêtre, les chevauchements bornés et les trous affichés sans inventer d’émission. Les timestamps en secondes ou millisecondes sont prioritaires. Dates avec offset acceptées; dates naïves acceptées uniquement avec `server_info.timezone`, jamais en supposant UTC.
- `Xtream.guide` demande `get_simple_data_table`; si la réponse échoue, est vide ou ne contient que du passé, repli `get_short_epg` (limite demandée 64). Le serveur peut fournir moins de données. Aucun XMLTV complet téléchargé ni aucune URL Edge intégrée.
- Trois tâches réseau au maximum, uniquement les cinq chaînes visibles. Connexion EPG limitée à 5 s, lecture à 8 s et réponse à 2 millions de caractères. Cache LRU de 96 chaînes, au plus 256 programmes par chaîne dans une plage de 24 h, fraîcheur de 3 min. Catalogue en mémoire pendant 5 min. Requêtes quittant l’écran annulées, file purgée et génération/ticket vérifiés avant de livrer une réponse; une lecture HTTP déjà bloquée peut attendre son délai avant de libérer son thread.
- Le cache est propre au compte et détruit si le compte change. Aucun nouveau stockage de compte ou horaire sur disque. Les favoris conservent maintenant le numéro de chaîne quand le fournisseur l’envoie, avec compatibilité des anciens favoris sans numéro.
- Le lecteur libère sa session en revenant au guide. Retour après lecture retrouve le filtre, la chaîne et la fenêtre en mémoire, sans nouvel appel catalogue tant que le cache est frais. Le bouton Maintenant réactive le suivi de l’heure courante. Rien ne persiste la position du guide après destruction du processus.

### Utilisation et limites

L’aide intégrée explique trois gestes : haut/bas, gauche/droite et OK. OK sur une émission en cours ou une chaîne sans horaire démarre le direct. Sur une émission future ou terminée, un dialogue affiche le synopsis et précise que Regarder démarre **le direct**, pas cette émission en rattrapage. Menu donne aussi accès aux détails complets de l’émission en cours.

Sans compte, huit chaînes et des émissions fictives permettent de tester la grille, explicitement marquées DÉMO / EXEMPLE D’HORAIRE. Aucun flux fourni. Le texte annoncé aux services d’accessibilité décrit la chaîne et la case sélectionnée; les cellules dessinées ne constituent pas encore une collection de nœuds virtuels pour exploration TalkBack individuelle. Aucun test VoiceView Fire TV revendiqué.

La vitesse et la quantité d’horaires restent liées au serveur. Pas de rattrapage, enregistrement, guide sur plusieurs jours, logo de chaîne ou synchronisation entre appareils dans ce chantier. Les catégories Français/Québec demeurent des heuristiques de noms.

### Vérification du candidat

Les cas automatisés couvrent désormais aussi les trous, chevauchements, découpages, limites exactes de cases, timestamps en millisecondes, débordements et dates avec/sans fuseau du fournisseur. Les essais sur émulateur et la publication finale sont consignés ci-dessous après vérification de l’APK définitif.


Validation locale de l’APK définitif : **16 tests réussis**, `lintDebug` sans erreur (9 avertissements existants), `assembleRelease` réussi. `apksigner verify --verbose` confirme le certificat initial. `aapt dump badging` confirme `ca.vicktv`, **0.3.0 / code 4**, sans `application-debuggable`. Taille : **4 343 635 octets**. SHA-256 : `6dd83ce2654f34e1c5ead2a6b5be6c0a683be23a16224c06fe4048cacdd37ca5`. Les 15 morceaux de distribution se reconstruisent exactement en cet APK; manifeste et SHA256SUMS concordent.

Sur émulateur API 35, format TV 960 × 540 dp : grille et aide inspectées visuellement; navigation D-pad, défilement au-delà de cinq chaînes, programme futur et retour, Français excluant une chaîne anglaise, catégories Sports, recherche du numéro 108, ajout/retrait immédiat et état Favoris vide, chargement de dates locales avec fuseau serveur, endpoint complet indisponible et repli court, chaîne sans horaire, erreur HTTP par chaîne sans dialogue bloquant. Le serveur synthétique confirme que seules cinq chaînes sont demandées au début, puis les nouvelles lignes à mesure du défilement. APK installé par-dessus 0.2.1 avec la même clé; un favori de laboratoire et le compte de test survivent à une nouvelle installation du candidat. Pas d’essai Edge ni Fire Stick.

La lecture de test depuis le guide retrouve les deux pistes AAC; après application asynchrone de la sélection, la piste « Français normal » est sélectionnée et « Éviter l’audiodescription : oui » est confirmé dans Réglages. Un menu ouvert immédiatement au premier changement de pistes peut encore refléter la sélection précédente; attendre la stabilisation avant de conclure sur la piste active. Une capture du guide démo de l’APK final est conservée dans `docs/images/guide-tv-demo.png` (émulateur Android en format TV).


### Publication 0.3.0

La [PR nº 3](https://github.com/kcote84/VickTV/pull/3) est fusionnée dans `main` au commit `bd255a48e90002f2356e6375a23fbe4f83e89817`, après réussite du contrôle Android [39662957330](https://github.com/kcote84/VickTV/actions/runs/39662957330). Le workflow de publication [39663272115](https://github.com/kcote84/VickTV/actions/runs/39663272115) a réussi et créé la release [v0.3.0](https://github.com/kcote84/VickTV/releases/tag/v0.3.0), avec APK, manifeste et SHA256SUMS.

Le manifeste stable `releases/latest/download/update.json` et l’APK ont été téléchargés **sans authentification** après publication : version 0.3.0, code 4, 4 343 635 octets, empreinte et octets identiques au fichier local testé. Le fichier SHA256SUMS concorde et `apksigner verify` confirme de nouveau le certificat original sur l’APK téléchargé. Les copies extraites de signature sont supprimées; le ZIP privé original reste hors dépôt.

Un dernier essai sur le candidat final vérifie la borne de navigation au lendemain, le retour à Maintenant et l’accès D-pad aux filtres depuis un guide Favoris vide. Le déploiement sur les deux Fire Stick n’a pas été effectué. Première action matérielle : Réglages → Vérifier les mises à jour sur une seule TV, puis vérifier Guide TV, favoris et données horaires Edge avant de mettre à jour la deuxième.


## 16. VicTV rose — 0.4.0

Demande : remplacer le vert par du rose bonbon, dessiner un logo VicTV humoristique inspiré de Viki, fournir une nouvelle capture du guide et regrouper les chaînes pornographiques Xtream dans une section dédiée.

### Identité et palette

`TvTheme.kt` centralise le fond prune `#19131F`, les panneaux `#302637`, l’accent rose bonbon `#FF91C1`, les textes secondaires et les états sélection/en cours. Accueil, listes, guide et contrôles de lecture partagent cette palette. L’accent Android et le fond de fenêtre sont harmonisés dans `styles.xml`; les valeurs du XML doivent rester synchronisées avec la palette Kotlin. Les textes blancs et le focus rose sur fond sombre sont conservés pour la lecture à distance.

Le nom affiché devient **VicTV**. Le paquet `ca.vicktv`, le dépôt `kcote84/VickTV`, l’alias de signature, les préférences, l’autorité FileProvider et le nom de l’APK distribué restent identiques. Le changement de nom visible ne requiert aucune désinstallation ni nouvelle connexion.

Les images sont créées avec l’outil intégré **imagegen**, puis copiées dans `app/src/main/res/drawable-nodpi/` : `victv_icon_art.png` (carré) et `victv_banner_art.png` (paysage). Elles représentent une télé rétro rose et une poitrine cartoon en bustier opaque, sans nudité, avec le mot VicTV. Les drawables `ic_vicktv.xml` et `tv_banner.xml` adaptent ces bitmaps aux dimensions demandées par le lanceur; le manifeste pointe sur ces ressources et porte le nom VicTV. Les prompts complets et leur relation de référence sont dans `docs/images/logo-prompts.txt`.

Le format du logo de bannière TV et l’usage de `android:icon` / `android:banner` sont décrits dans la [documentation Android](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines). L’affichage exact du lanceur Fire OS peut dépendre de son cache et reste à vérifier sur les deux Fire Stick; ne pas présenter une capture Android comme une capture Fire OS.

### Regroupement Adultes

- Accueil : **Adultes 18+**, puis Chaînes en direct, Films ou Séries. Ce sont les API habituelles du compte Xtream, filtrées localement; aucun flux externe n’est ajouté.
- Guide : filtre **Adultes** visible et accessible aussi par Menu → Choisir les chaînes affichées.
- `Entry.adult` reprend `is_adult` numérique ou booléen du fournisseur. Il reste conservé dans les détails des favoris; les anciens favoris sans ce champ se lisent avec `false`.
- `CatalogRules.isAdult` combine ce drapeau, les groupes Adult/Adults/Adulte/Adultes et des marqueurs explicites (XXX, Porno, 18+, Érotique et quelques libellés de chaînes). Les mots génériques adult/adulte dans un titre seul ne suffisent pas; Adult Swim est exclu de ce marqueur générique. Ce classement n’est pas une preuve de contenu et peut être imparfait si le fournisseur ne marque pas ses chaînes.
- Choisir une catégorie dans le catalogue Adultes conserve le filtre Adultes. Recherche, favoris et filtres Français/Québec de la VOD restent combinables.
- Le regroupement ne masque pas le catalogue Toutes ni les catégories d’origine et ne crée pas de PIN parental. Aucun contrôle parental n’a été demandé dans ce chantier. Le mode démo ne fournit ni chaîne adulte ni programme adulte.

### Vérifications

Les tests ajoutés vérifient le marquage fournisseur, les variantes de catégories, les marqueurs explicites, les faux positifs Adult Swim / Sex Education / titres ordinaires et le maintien des filtres linguistiques. Résultats du build final, captures et publication consignés ci-dessous après contrôle du paquet.


Le build définitif `testDebugUnitTest lintDebug assembleRelease --no-daemon` réussit : **20 tests**, zéro échec, lint sans erreur (8 avertissements). `apksigner verify --verbose` confirme le certificat initial. `aapt dump badging` confirme **VicTV**, paquet `ca.vicktv`, **0.4.0 / code 5**, sans mode débogable. APK : **6 812 383 octets**, SHA-256 `c481da935e6604fcd14814ba17b07dade1b0f2bb0aca4ce2d167988cf11ed18a`. Les **23 morceaux** de distribution remplacent intégralement les 15 précédents; leur concaténation est comparée à l’APK signé, au manifeste et à SHA256SUMS.

L’APK est installé par-dessus 0.3.0 sur émulateur Android API 35 au format 960 × 540 dp, sans désinstallation. Captures réelles inspectées : `docs/images/guide-tv-rose.png` (guide démo fictif) et `docs/images/home-victv-rose.png` (accueil). Les images de lancement sont incluses dans l’APK; l’intégration au lanceur Fire OS attend encore un essai matériel.


Serveur Xtream synthétique local : seize chaînes, dont trois entrées classées Adultes par trois voies (drapeau `is_adult=1` sur un nom neutre, nom XXX, catégorie Adultes) et une chaîne Adult Swim non adulte. Les trois bonnes entrées apparaissent dans le direct, les films, les séries et le filtre du guide. Un changement de catégorie vers un groupe général conserve seulement l’entrée marquée adulte; le filtre Français reste combinable. Aucun compte Edge ni contenu pornographique réel n’a été utilisé dans les tests.


### Publication 0.4.0

La [PR nº 4](https://github.com/kcote84/VickTV/pull/4) est fusionnée au commit `c9c58fa4d479b7e8c9e75d42a25df5817653076a` après réussite du contrôle Android [39665521960](https://github.com/kcote84/VickTV/actions/runs/39665521960). Le workflow [39665888565](https://github.com/kcote84/VickTV/actions/runs/39665888565) a publié [v0.4.0](https://github.com/kcote84/VickTV/releases/tag/v0.4.0), avec APK, manifeste et SHA256SUMS.

Le manifeste stable propose bien le code 5. Les trois fichiers sont téléchargés sans authentification : APK de 6 812 383 octets, empreinte et octets identiques au fichier local testé; SHA256SUMS conforme et certificat original confirmé à nouveau avec apksigner sur l’APK public. Les copies extraites de clé/mots de passe ont été retirées; seule la sauvegarde privée originale demeure hors dépôt.

L’ajout d’un favori depuis le guide Adultes est confirmé dans le guide et dans la section générale Favoris. L’écran système Android affiche correctement le nom VicTV et l’icône complète, vérifiés visuellement. Les captures automatiques attendent la fin des animations de dialogue avant de conclure sur les actions; un premier essai trop rapide avec Retour avait interrompu le clic de la fixture.

Prochaine validation sur matériel : mettre à jour un Fire Stick via Réglages → Vérifier les mises à jour, vérifier la nouvelle icône/bannière dans son lanceur, la lisibilité du rose et le classement Adultes avec le catalogue Edge. Le classement dépend des données du fournisseur; aucun horaire ni contenu adulte réel n’a été utilisé pour les essais. Les réglages audio et les limites de validation HDMI précédentes restent inchangés.


## 17. Correction du nom — 0.4.1

Le propriétaire précise l’orthographe **VickTv**, avec le k, au lieu de VicTV. Le nom visible du manifeste, l’accueil, les dialogues et les instructions d’installation sont corrigés. L’icône et la bannière sont retouchées avec imagegen uniquement pour corriger le mot-symbole en VickTv. Le rose, le dessin et les fonctions de 0.4.0 sont conservés.

Version publiée : 0.4.1 / code 6. Le paquet `ca.vicktv`, le dépôt `kcote84/VickTV`, le fichier distribué `VickTV.apk`, la signature et les données locales conservent leur identité. Résultats du build et de la publication ci-dessous.

Validation locale : 20 tests réussis, lint sans erreur, assembleRelease réussi avec la clé initiale. APK de 6960075 octets, SHA-256 `944673630f7edd837a727198de397170de96552c61471307b3fbc5ccc0bc9ca2`; tous les morceaux remplacés et reconstruction conforme au manifeste. Aucune nouvelle fonctionnalité ni nouveau test matériel dans ce correctif de nom.


La [PR nº 5](https://github.com/kcote84/VickTV/pull/5) est fusionnée au commit `d71917c4760f0387f4b8e0a89aad7540e6ccd53e`, après réussite du contrôle Android [39669368405](https://github.com/kcote84/VickTV/actions/runs/39669368405). Le workflow [39669686446](https://github.com/kcote84/VickTV/actions/runs/39669686446) a publié [v0.4.1](https://github.com/kcote84/VickTV/releases/tag/v0.4.1).

Le manifeste stable, l’APK et SHA256SUMS ont été téléchargés sans authentification : code 6, 6 960 075 octets identiques au fichier signé local, empreinte `944673630f7edd837a727198de397170de96552c61471307b3fbc5ccc0bc9ca2` conforme et certificat initial inchangé. Le paquet compilé annonce le nom exact VickTv. Les copies extraites de signature ont été supprimées; la sauvegarde privée originale reste hors dépôt. Les limites des essais matériels précédents restent applicables.


## 18. Investigation Zeste / audio unique et HLS — 0.4.1

Sur Fire Stick, le propriétaire signale une seule piste pour Zeste : `und · 2 canaux · audio/mp4a-latm`, avec le même problème de narration que dans TiviMate. Le passage en HLS échoue avec un message de lecture; le code exact reste demandé. Aucune cause attribuée définitivement à Edge ni au lecteur à ce stade.

Audit : `audioMenu` expose toutes les pistes audio de `currentTracks`, y compris celles non prises en charge. Le choix automatique et le mode Stéréo changent la sélection, pas la construction de cette liste. Une seule ligne indique une seule piste détectée par Media3 pour ce flux; l’absence réelle d’autres pistes dans le transport reste à prouver indépendamment.

Vérification supplémentaire effectuée avec l’APK signé **0.4.1 / code 6** inchangé sur émulateur API 35 : fixture HLS locale, vidéo H.264 et deux variantes audio AAC françaises séparées, dont une AD déclarée par défaut. Le lien Xtream `.m3u8` redirige vers une playlist maître puis des playlists vidéo/audio avec segments TS. ffprobe confirme les deux pistes et la vidéo. VickTv démarre la lecture, expose les deux pistes et sélectionne « Français normal » malgré AD par défaut. Capture de laboratoire `qa/hls-audio-probe.png`, données et scripts hors Git. Ceci confirme le chemin HLS général sur émulateur; aucune validation du flux Edge, de la sortie sonore réelle ni du HLS sur le Fire Stick du propriétaire.

Retour provisoire conseillé sur MPEG-TS pour conserver la lecture. Prochaine étape : relever `Code : ERROR_CODE_…` après les reconnexions sur le HLS réel; selon le code, examiner refus HTTP, manifeste/segments ou décodage. Aucun correctif de code, nouveau build ni nouvelle release produit par ce seul test. Les limites de validation précédentes demeurent.

## 19. Diagnostic HTTP et retour MPEG-TS — 0.4.2

Le code communiqué ensuite par le propriétaire est **ERROR_CODE_IO_BAD_HTTP_STATUS**. Il signifie qu’une requête de lecture reçoit une réponse HTTP inattendue, sans identifier à lui seul le numéro ni la ressource fautive (playlist, segment ou clé HLS). Ce code ne démontre ni un problème de codec ni l’absence de prise en charge HLS chez Edge. Le flux réel n’a pas été inspecté.

`PlaybackErrors` recherche `HttpDataSource.InvalidResponseCodeException.responseCode` dans une chaîne bornée de 16 causes. Le dialogue Lecture interrompue affiche le format du direct, le numéro HTTP lorsqu’il est disponible, le nom du code Media3 et une explication contrôlée. Aucun message brut d’exception, URL, corps de réponse ou en-tête n’y est ajouté afin de garder les identifiants privés. 401/403 : accès refusé; 404/410 : ressource absente; 429 : trop de demandes; 5xx : erreur serveur. Les autres erreurs gardent une explication générale.

401, 403, 404 et 410 n’attendent plus trois reconnexions automatiques. Les autres échecs conservent la stratégie existante, dont les erreurs réseau et 5xx. Pour une lecture HLS en direct, le bouton **Passer en MPEG-TS** explique puis modifie le réglage global du direct et relance la même chaîne. Le changement est explicite et conserve le contexte de navigation. MPEG-TS ne résout pas une narration déjà mélangée à l’unique piste reçue.

Version publiée : **0.4.2 / code 7**, même paquet et même certificat initial. Build local `testDebugUnitTest lintDebug assembleRelease` réussi : **23 tests**, zéro échec; lint sans erreur, 8 avertissements. `apksigner verify --verbose` confirme la signature originale et `aapt dump badging` confirme VickTv, ca.vicktv, code 7, sans mode débogable. Les trois nouveaux tests couvrent la politique de reconnexion, les explications et l’arrêt sûr sur une chaîne cyclique de causes sans exposer leurs messages.

APK signé : **6 961 359 octets**, SHA-256 `45c6d74531d837e6cb886a368b4e513752ca999220b19b9d65cbffcdd435086b`. Les 24 morceaux de distribution sont remplacés et leur reconstruction est identique à l’APK; manifeste et SHA256SUMS concordent. Les copies extraites de signature sont supprimées, sans toucher au ZIP privé original hors dépôt.

Sur émulateur API 35, APK installé par-dessus 0.4.1 : un manifeste HLS refusé en 403 affiche le numéro exact et l’explication attendue, sans URL ni texte brut du serveur, même si la fixture en contient. Un cycle de chargement Media3 réalise encore ses quatre requêtes internes; les trois relances supplémentaires par l’activité sont supprimées pour ce statut. Le bouton MPEG-TS relance la chaîne de laboratoire avec ses deux pistes AAC et la piste française normale sélectionnée; favoris, compte de test, origine de liste et réglage MPEG-TS sont conservés. Un segment HLS absent (404) est aussi correctement identifié; navigation D-pad jusqu’au bouton, activation, lecture MPEG-TS et retour à la même chaîne dans le guide confirmés. Le test vérifie le focus du bouton avant OK et la piste active avant de conclure.

Un HLS valide avec vidéo H.264 et deux pistes AAC françaises est ensuite vérifié sur **0.4.2** : les deux pistes sont exposées et « Français normal » est sélectionnée malgré AD déclarée par défaut. Les captures et scripts de laboratoire restent dans qa, hors Git. Aucun flux Edge réel ni sortie audio physique/HDMI n’a été testé; la prochaine étape sur le Fire Stick reste de relever la nouvelle ligne **HTTP : …** de Zeste en HLS, puis revenir en MPEG-TS.

### Publication 0.4.2

La [PR nº 6](https://github.com/kcote84/VickTV/pull/6) est fusionnée au commit `fd7c17e15379b822e85d49d7e468b86b36c8b69c`, après réussite du contrôle Android [39677591382](https://github.com/kcote84/VickTV/actions/runs/39677591382). Le contrôle de main [39677877884](https://github.com/kcote84/VickTV/actions/runs/39677877884) et la publication [39677878120](https://github.com/kcote84/VickTV/actions/runs/39677878120) réussissent. La release [v0.4.2](https://github.com/kcote84/VickTV/releases/tag/v0.4.2) contient l’APK, le manifeste et SHA256SUMS.

Téléchargements sans authentification : manifeste stable strictement identique à celui préparé, APK versionné et lien latest identiques au paquet signé testé (6 961 359 octets), empreinte et SHA256SUMS conformes. Le certificat initial est de nouveau confirmé par apksigner sur l’APK public. Le réglage de l’émulateur est remis à MPEG-TS puis l’application, l’émulateur et le serveur synthétique sont arrêtés. Aucun appareil réel n’a été modifié par ces tests.

## 20. Accueil épuré et guide agrandi — 0.4.3

Le propriétaire demande de retirer la barre du bas du guide illustrée dans sa capture et de réduire le texte dans les boutons de l’accueil. La rangée Plus tôt / Maintenant / Plus tard / Ajouter favori et sa ligne de raccourcis sont retirées. La grille conserve cinq lignes et récupère les 64 dp de hauteur libérés. Gauche/droite parcourt toujours les émissions; Menu et l’appui long sur OK proposent les favoris et Revenir à maintenant. Le bouton ? Aide reste en haut. Le marquage DÉMO · EXEMPLE D’HORAIRE est conservé dans les détails du guide de démonstration.

L’accueil affiche neuf actions : Guide TV, Favoris et Direct dans la première rangée, Films / Séries / Français / Sports dans la deuxième, Adultes 18+ et Réglages en bas. Direct ouvre l’ancien catalogue Toutes les chaînes. Les descriptions publicitaires et la question redondante sont retirées; les sept tuiles principales comportent des icônes vectorielles natives, un titre court et un focus rose avec icône/texte prune. Les rangées principales utilisent la hauteur disponible; les icônes et leurs couleurs ne changent aucun filtre ni route de catalogue.

`TvGuideScreen` n’a plus de bouton Favori ni de notice en bas; l’horloge est actualisée indépendamment. À la dernière chaîne, Bas garde le focus dans la grille au lieu de chercher un bouton supprimé. Haut à la première chaîne retrouve les filtres; les filtres restent aussi accessibles quand la sélection est vide. Aucun changement audio, réseau, signature, données de compte ou mécanisme de mise à jour.

Version publiée : **0.4.3 / code 8**. Build `testDebugUnitTest lintDebug assembleRelease` réussi : **23 tests**, zéro échec, lint sans erreur (8 avertissements existants). `apksigner verify --verbose` confirme le certificat initial et `aapt dump badging` confirme VickTv, ca.vicktv, version/code attendus, sans mode débogable. Aucun nouveau test unitaire qui recopierait la disposition; la vérification de l’interface et du focus se fait sur émulateur avec l’APK signé.

APK final : **6 965 063 octets**, SHA-256 `0f411da6fb5ce48565c79ae171ec74ea31154a931508584499cd64ffe3109aaf`. Les 24 morceaux sont remplacés et reconstruits à l’identique; manifeste et SHA256SUMS concordent. Les copies extraites de signature sont supprimées et le ZIP privé d’origine reste hors dépôt.

APK final installé sur émulateur API 35 par-dessus 0.4.2 : les neuf actions de l’accueil sont atteintes au D-pad, puis Réglages s’ouvre avec le compte synthétique et le format MPEG-TS conservés. Le favori de laboratoire préexistant reste présent. Dans le guide, la grille passe de 282 à 346 dp de hauteur; la barre et la ligne d’aide sont absentes. Dernière chaîne + Bas garde le focus dans la grille; Haut à la première chaîne rejoint les filtres. Les flèches horaires, Menu → Revenir à maintenant et l’ajout/retrait d’un favori sont vérifiés. Les essais ne portent sur aucun Fire Stick réel ni flux Edge.

Les tuiles de l’accueil regroupent l’icône et le texte au centre d’un seul contrôle cliquable/focalisable, annoncé comme bouton avec son libellé. Les éléments décoratifs internes ne prennent pas le focus. L’image de l’accueil provient du véritable APK signé : `docs/images/home-vicktv-simple.png`. Le guide de démonstration fictif est capturé séparément dans `docs/images/guide-tv-simple.png`.

Après validation de la conservation du compte/favori, seules les données fictives de l’émulateur sont réinitialisées pour capturer le mode démo. Depuis Favoris vide, Haut rejoint Français et OK rétablit les chaînes; ? Aide ouvre toujours les trois gestes. Les deux captures sont inspectées visuellement. L’appareil de laboratoire et le serveur synthétique sont arrêtés à la fin des essais. Aucune donnée d’un Fire Stick réel n’a été modifiée.

La touche OK est aussi vérifiée sur les nouvelles tuiles Guide TV et Films : elle ouvre la bonne destination. Les essais attendent la fin complète du démarrage Android (`sys.boot_completed=1`) afin que les transitions du système ne relancent pas un message d’accueil pendant les commandes; aucun correctif applicatif supplémentaire n’a été nécessaire.

### Publication 0.4.3

La [PR nº 7](https://github.com/kcote84/VickTV/pull/7) est fusionnée au commit `ac96499ade691b855d26ba65663b82f1cbb2ea63`, après réussite du contrôle Android [39685168596](https://github.com/kcote84/VickTV/actions/runs/39685168596). Le workflow de publication [39685589098](https://github.com/kcote84/VickTV/actions/runs/39685589098) réussit et crée la release [v0.4.3](https://github.com/kcote84/VickTV/releases/tag/v0.4.3) avec APK, manifeste et SHA256SUMS.

Le manifeste stable, les liens APK latest/versionné et SHA256SUMS sont téléchargés sans authentification. Le manifeste correspond à celui préparé (code 8); les APK sont identiques au fichier signé testé (6 965 063 octets), empreinte conforme et certificat initial à nouveau vérifié par apksigner sur le fichier public. Les sources, captures et notes décrivent la version distribuée. L’installation réelle et le rendu sur les Fire Stick restent à vérifier par le propriétaire.

## 21. Retour du diagnostic HLS — HTTP 405

Le propriétaire communique ensuite **HTTP 405** pour l’échec HLS. Selon la [RFC 9110, section 15.5.6](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.5.6), ce statut signifie que la méthode reçue n’est pas autorisée pour la ressource ciblée; une réponse conforme fournit les méthodes admises dans Allow. Le numéro seul n’identifie ni la ressource (playlist/segment/clé), ni un serveur intermédiaire, ni la cause de sa configuration.

Audit inchangé de 0.4.3 : le lien live HLS construit par Xtream utilise l’extension m3u8 et la lecture passe par DefaultHttpDataSource, sans méthode personnalisée. Le [DataSpec de Media3 1.4.1](https://github.com/androidx/media/blob/1.4.1/libraries/datasource/src/main/java/androidx/media3/datasource/DataSpec.java) utilise GET par défaut. Il n’y a donc pas de POST ajouté par VickTv à remplacer arbitrairement. Aucun vrai lien, en-tête Allow ou échange HTTP Edge n’a été capturé : ne pas conclure qu’Edge ne propose aucun HLS, ni que le lecteur est définitivement hors de cause.

Conserver MPEG-TS pour la lecture actuelle. Prochain test indépendant utile : vérifier Zeste en HLS dans TiviMate, avec le même compte et sur le même Fire Stick/réseau. S’il fonctionne, comparer les adresses et paramètres de requête des deux lecteurs; sinon, faire confirmer le lien HLS autorisé par le fournisseur. Le 405 ne démontre rien sur les autres pistes du flux MPEG-TS et ne résout pas l’audiodescription. Aucun changement de code, nouveau build ou nouvelle release produit pour ce seul résultat.

## 22. Analyse directe de Zeste MPEG-TS — 21 octobre 2026

Le propriétaire choisit de laisser HLS de côté et fournit ses accès afin d’analyser le flux lui-même. Les identifiants sont chargés dans un fichier temporaire sous qa, dont l’exclusion Git est vérifiée avant écriture, puis supprimés après le prélèvement. Aucun compte, mot de passe, lien authentifié ni réponse brute d’authentification n’est ajouté au suivi public. Les deux accès sont reconnus; un compte libre est revérifié avant d’ouvrir une seule connexion de lecture. Le catalogue consulté présente une seule entrée nommée ZESTE (FR).

Le flux MPEG-TS répond HTTP 200. Prélèvement brut de **18,52 secondes / 14 974 976 octets**, connexion refermée, sans sélection de piste ni réencodage. L’analyse locale par **ffprobe**, indépendante de Media3/VickTv, trouve un programme contenant une vidéo H.264 1280 × 720 et **une seule piste audio HE-AAC, stéréo, 48 kHz, langue und**, PID audio 0x101. Aucune autre piste audio sélectionnable n’est détectée dans ce prélèvement. Les indicateurs visual_impaired/descriptions ne sont pas renseignés à vrai; cela ne prouve pas l’absence audible de narration.

Ce résultat confirme que la liste à une seule piste de VickTv correspond au transport MPEG-TS observé, à cet instant. Il ne démontre pas par une écoute que la narration est mélangée au son, ni qu’un canal gauche/droit serait exploitable seul. Le décodage de 16 secondes confirme deux canaux différents (corrélation 0,338811), ce qui peut simplement être du son stéréo. Deux extraits mono gauche/droite sont préparés pour une écoute utilisateur, avec le même gain. Les extraits, le prélèvement et le rapport technique restent locaux sous qa, hors Git. Ne pas attribuer une signification narrative à ces seules statistiques.

Une réponse à EdgeTv Québec est préparée dans la conversation : problème persistant avec deux lecteurs, une seule piste constatée et demande de vérifier le flux Zeste ou proposer une version sans vidéodescription. Aucun courriel envoyé. Pas de modification de l’application ni de nouvelle release : la version distribuée reste 0.4.3. L’analyse porte sur cet échantillon du service fourni, sans essai de restitution HDMI ni écoute humaine confirmée.

## 23. Bordure TV, favoris et galeries — 0.5.0

Demande : éviter les boutons coupés par la télé, commencer le guide par les favoris, limiter le raccourci du direct aux chaînes québécoises, superposer les commandes à la vidéo et remplacer les listes de films/séries par des pochettes en français sans filtres.

`TvSafeArea` centralise une marge horizontale et verticale de **5 % de l’écran**, ou **8 %** avec Réglages → Bordure TV. L’accueil, les listes, galeries, réglages et guide l’appliquent; le guide en cache la réapplique au retour. La vidéo reste dans un PlayerView plein écran. Titre, boutons et contrôleur Media3 sont superposés dans la zone sûre. Le contrôleur reçoit des marges de disposition. Les menus flottants Media3 peuvent encore s’aligner sur le bord de la fenêtre malgré ces marges : les boutons réglages et sous-titres ouvrent donc des dialogues Android centrés en français (son, sous-titres, vitesse), comme le reste de l’application. AGENTS conserve la règle de bordure pour les prochaines évolutions.

Le guide commence toujours par **★ Favoris**, même si la liste est vide; l’aide indique comment ajouter une chaîne depuis Toutes. Favoris est le premier bouton. Le retour conserve le filtre/horaire/chaîne; une ouverture explicite depuis une chaîne affiche celle-ci dans Toutes.

Le raccourci Français devient **Québec** pour le direct. La consultation des catégories réelles d’Edge montre un groupe FRENCH mélangeant chaînes canadiennes et françaises. `isQuebecLive` combine les catégories locales/francophones et les marques de chaînes locales connues, en rejetant les groupes et chaînes de France reconnus. TV5 Monde et Télétoon Plus ne sont pas assimilés à TV5 Canada et Télétoon. Le périmètre comprend les chaînes francophones canadiennes distribuées au Québec; aucun géoblocage ou inventaire exhaustif garanti. Les titres inconnus restent dans Direct/Toutes.

Films et Séries utilisent un GridView à six colonnes (cinq sur écran étroit), avec recherche et Options. Aucun sélecteur de catégories/langue dans ces deux galeries. `isFrenchVod` retient les catégories françaises, québécoises et les tags VF/VFF/VFQ; exclut VOSTFR et les titres explicitement anglais sans marque française. Il ne sonde pas chaque œuvre : une métadonnée erronée peut encore mal classer un titre, et MULTI sans indication française reste exclu.

`CatalogParser` conserve stream_icon pour les films et cover pour les séries; les images relatives sont résolues sur le serveur. `PosterLoader` charge hors du thread UI avec trois travailleurs, file bornée, cache mémoire 12 Mio, images limitées à 4 Mio et décodage réduit. Les vues recyclées vérifient l’URL avant affichage; quitter l’écran invalide les rappels. Pas de cache disque. Les liens invalides/absents et erreurs HTTP gardent un pictogramme et le titre. Le champ poster est optionnel dans les favoris existants. Les recherches et sélections sont restaurées en revenant d’un épisode ou du lecteur.

Version **0.5.0 / code 9**, même paquet et certificat initial. `testDebugUnitTest lintDebug assembleRelease` : 29 tests réussis, aucun échec; lint sans erreur, neuf avertissements. Les six nouveaux tests portent sur les règles Québec/français et le parsing/validation des pochettes. Aucun nouveau compte ni clé dans Git. La consultation des métadonnées Edge ne lit aucun flux vidéo et son fichier d’accès temporaire est supprimé.

Les essais d’interface utilisent uniquement un compte et un fournisseur fictifs locaux, des affiches marquées DÉMO et une vidéo synthétique. Favoris en premier (vide puis ajout), galeries et images de remplacement, absence de filtres, navigation D-pad et bascule des marges 5/8 % sont vérifiés sur émulateur API 35. Les captures documentent ces contenus fictifs. Le rendu HDMI et l’overscan réel des deux Fire Stick restent à confirmer par le propriétaire. Aucun changement des règles audio ou du flux de Zeste dans cette évolution.

La mise à jour signée conserve le compte fictif et son favori. La hiérarchie Android mesure la surface vidéo à **0,0–1920,1080** avec commandes affichées comme masquées. Le retour lecteur → épisodes → galerie conserve la recherche « train » et la série sélectionnée. Le menu Lecture centré, les choix de vitesse, les sous-titres désactivés et la sélection automatique de la piste française normale sont vérifiés sur le flux synthétique. La sélection d’une vraie piste de sous-titres et la restitution audio HDMI restent hors de ces essais. L’émulateur et le serveur fictif sont arrêtés ensuite; les accès temporaires et copies de signature sont supprimés.

### Publication 0.5.0

La [PR nº 8](https://github.com/kcote84/VickTV/pull/8) est fusionnée au commit `0fbec6008f4cfd8d84e02d29960055d0e1793109`, après réussite du contrôle Android [39692142757](https://github.com/kcote84/VickTV/actions/runs/39692142757). Le workflow de publication [39692352870](https://github.com/kcote84/VickTV/actions/runs/39692352870) réussit et crée [v0.5.0](https://github.com/kcote84/VickTV/releases/tag/v0.5.0).

APK public : **6 975 347 octets**, SHA-256 `9e59e1bc8ab0eee6be0ff8a6ab82b5cfb9828701e32ca2cb9dc152397033d7ae`. Le manifeste stable, les liens APK latest/versionné et SHA256SUMS sont téléchargés sans authentification. Le manifeste correspond à celui préparé (code 9), et les deux téléchargements sont identiques à l’APK signé testé. `apksigner` confirme de nouveau le certificat initial sur le fichier public. Aucune désinstallation nécessaire pour passer de la version précédente à cette version avec la même signature.

## 24. Transition de chargement du guide — 0.5.1

Retour utilisateur : passer de Favoris à Québec laisse les anciennes chaînes visibles pendant un gel apparent. Le filtrage parcourait tout le catalogue sur le thread UI, avec une recherche de catégorie linéaire par chaîne et une lecture répétée des favoris. Une roue ajoutée autour de ce travail synchrone n’aurait pas pu s’animer.

`GuideFilters` travaille désormais sur un exécuteur distinct des horaires et du thread UI. Chaque requête reçoit des instantanés des chaînes, catégories indexées par ID et favoris. Dès le choix d’un onglet, le guide masque la grille, efface les détails précédents et affiche une roue rose centrée avec « Chargement des chaînes… ». Les onglets restent utilisables. La grille réapparaît lorsque le filtrage est terminé, puis les horaires se complètent comme avant; aucune attente de tous les horaires n’est imposée.

Les expressions régulières de normalisation et du tri Québec sont compilées une seule fois au lieu d’être recréées pour chaque chaîne; leurs critères sont inchangés et couverts par les tests existants.

Une nouvelle sélection annule le filtrage précédent; la boucle vérifie les interruptions. Un numéro de requête et la génération de l’écran empêchent un résultat ancien de remplacer le dernier choix. Sortir du guide annule le travail; le retour relance la sélection courante. La roue couvre aussi le chargement initial/réactualisé du catalogue et se ferme sur une erreur, avec le dialogue Réessayer habituel. Les boutons choisis pendant le chargement initial ne sont plus remplacés par le défaut Favoris à la réception du catalogue.

Aucun changement au format ou aux clés de stockage des favoris, du compte et des préférences. La conservation lors d’une installation par-dessus la version précédente devient une consigne explicite dans AGENTS. Version **0.5.1 / code 10**, paquet et signature initiaux.

`testDebugUnitTest lintDebug assembleRelease` : **33 tests réussis**, aucun échec; lint sans erreur, neuf avertissements. Les quatre nouveaux tests vérifient les résultats et l’absence de mutation lors des changements de filtre, les recherches combinées, les favoris vides et l’interruption d’un filtrage obsolète. Aucun diagnostic temporaire de laboratoire n’est livré; seul le nom de classe d’une erreur de catalogue est journalisé, sans message, URL ou identifiants.

Validation avec l’APK signé sur émulateur API 35 : favori Télé-Québec et compte fictif présents avant l’installation sur 0.5.0, puis retrouvés dans la section Favoris et le guide après mise à jour. Un catalogue fictif de **40 016 chaînes** confirme la roue à la place des anciennes lignes, le dernier choix après Québec → Toutes → Favoris, **13 345 résultats Québec**, la navigation et le retour à l’accueil pendant un filtrage. La capture `docs/images/guide-chargement.png` provient du véritable APK. Les premières grosses réponses du serveur fictif étaient coupées dans le transport local; les servir par blocs a permis le test sans modifier le réseau de l’application. Aucun essai sur Fire Stick ou flux Edge. Émulateur et serveur fictif arrêtés, copies extraites de signature supprimées; archive privée initiale conservée.

### Publication 0.5.1

La [PR nº 9](https://github.com/kcote84/VickTV/pull/9) est fusionnée au commit `3be13a205b17c0d104d4341ca4701a27637b3f05`, après réussite du contrôle Android [39695401943](https://github.com/kcote84/VickTV/actions/runs/39695401943). Le workflow de publication [39695696080](https://github.com/kcote84/VickTV/actions/runs/39695696080) réussit et crée [v0.5.1](https://github.com/kcote84/VickTV/releases/tag/v0.5.1).

APK public : **6 977 535 octets**, SHA-256 `aeebcf7258ebfafad4a535a3591ee30cf68516327cf14ff64d8c7826abde8252`. Le manifeste stable (code 10), SHA256SUMS et les deux liens APK latest/versionné sont téléchargés sans authentification. Les APK sont identiques au fichier signé testé; `apksigner` confirme le certificat initial sur le fichier public. Le paquet reste ca.vicktv, sans mode débogable; les 24 morceaux de distribution se reconstruisent à l’identique.

## 25. Retour vers les onglets du guide — 0.5.2

Demande : depuis une chaîne éloignée dans la liste, rejoindre les onglets sans remonter toutes les chaînes et sans quitter le guide. Dans la grille, **Retour** redonne maintenant le focus au dernier bouton utilisé en haut (ou au filtre courant par défaut). **Bas** retrouve la chaîne sélectionnée; **Retour depuis les onglets** ramène à l’accueil. `MainActivity` délègue ce premier Retour à `TvGuideScreen.handleBack()`.

Ce déplacement de focus ne relance aucun filtrage et ne modifie ni la sélection, ni le défilement, ni la fenêtre horaire. Un marqueur protège le focus du haut contre un résultat de filtrage encore en cours; une nouvelle action explicite ou un retour dans la grille reprend la navigation normale. Les dialogues consomment leur propre Retour comme avant, et le lecteur continue de revenir au guide. Aide intégrée, README et notes de version décrivent ces deux étapes.

Version **0.5.2 / code 11**, même paquet et signature. Aucun changement de stockage ou de contenu des favoris, comptes et préférences. Les **33 tests existants** passent; lint sans erreur (neuf avertissements), compilation release signée et vérification du certificat initial réussies. La modification de focus est vérifiée sur l’interface réelle de l’APK plutôt que par un test unitaire qui recopierait la disposition.

Sur émulateur API 35 avec fournisseur fictif, installation sur place par-dessus 0.5.1 : le favori Télé-Québec reste présent avant et après, et le compte est utilisable. Depuis la **dernière chaîne (16/16)** et une fenêtre avancée dans l’horaire, Retour rejoint le menu du haut; le libellé d’accessibilité de la sélection reste strictement identique. Bas redonne le focus à la même chaîne et à la même émission. Le second Retour depuis le menu ouvre l’accueil. Les essais couvrent aussi Retour dans un dialogue, le retour lecteur → guide → menu et le dernier bouton du haut utilisé. Les captures de laboratoire restent sous qa. Aucun essai sur Fire Stick réel; émulateur et serveur arrêtés, copies de signature extraites supprimées.

### Publication 0.5.2

La [PR nº 10](https://github.com/kcote84/VickTV/pull/10) est fusionnée au commit `5ee3dcc24d8cbeec6a57ae795081f5932c793a47`, après réussite du contrôle Android [39697866551](https://github.com/kcote84/VickTV/actions/runs/39697866551). Le workflow de publication [39698082430](https://github.com/kcote84/VickTV/actions/runs/39698082430) réussit et crée [v0.5.2](https://github.com/kcote84/VickTV/releases/tag/v0.5.2).

APK public : **6 978 127 octets**, SHA-256 `ab21f77965507e5ed8cbf4af28db10617533d254391842798894ef5053d7a197`. Manifeste stable (code 11), SHA256SUMS et liens APK latest/versionné téléchargés sans authentification; les deux APK sont identiques au fichier signé testé. Le fichier public est à nouveau vérifié par apksigner : certificat initial conservé, paquet ca.vicktv et version attendus, sans mode débogable. Les 24 morceaux se reconstruisent à l’identique.

## 26. Audit de convivialité — version 0.5.2

### Mandat et méthode

Audit demandé pour l’usage quotidien d’une personne peu intéressée par les réglages techniques, à la télécommande et depuis le divan. Ce profil décrit une préférence d’usage; il ne permet pas de déduire une capacité d’après l’âge ou le genre. L’objectif est de trouver, regarder, reprendre et corriger un petit problème sans assistance.

Base examinée : sources de 0.5.2, captures réelles de l’application et nouveau parcours de l’APK signé sur émulateur API 35. Accueil, mot de bienvenue, réglages, favoris, films, séries/épisodes, lecteur, audio et guide sont parcourus avec un compte et des médias fictifs. Les captures d’audit restent sous qa, hors Git. Les comportements d’erreur, le stockage et les mises à jour sont aussi examinés dans le code. Aucun entretien ou essai avec l’utilisatrice, aucun essai sur son Fire Stick, aucune mesure de latence Edge ni écoute HDMI : les recommandations restent à confronter à ces usages réels.

Les repères de [navigation Android TV](https://developer.android.com/training/tv/get-started/navigation) privilégient des déplacements prévisibles, peu d’écrans et un focus immédiatement identifiable. Ils confortent le Retour vers le menu déjà livré. Pour les petits textes, le [repère de contraste W3C](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) de 4,5:1 est utile; il ne constitue pas une certification d’accessibilité native de l’application.

### Diagnostic

La base visuelle est cohérente : rose/prune, grands boutons, marges TV, pochettes, commandes superposées, favoris au début du guide et conservation des données pendant les mises à jour. Les points les plus coûteux pour l’usage sont la multiplicité des routes du direct, les renseignements techniques trop présents, les gestes essentiels peu visibles et l’absence de reprise persistante des films/épisodes.

Préserver les choix déjà demandés : thème rose, identité personnelle, 50 mots en rotation, favoris en premier, français automatique pour les galeries sans remettre leurs filtres linguistiques, Québec pour le direct, rubrique Adultes disponible, disparition des anciennes chaînes pendant le chargement et absence de barre de boutons en bas du guide.

Priorités : **P1** réduit une confusion ou une frustration fréquente; **P2** enrichit nettement l’usage après les corrections principales; **P3** personnalise ou finit l’ensemble. Effort relatif **S/M/L**, sans estimation de calendrier. Les constats sont reliés au code; les gains attendus sont des hypothèses de conception, pas des résultats d’un test utilisateur.

### Recommandations détaillées

| ID | Priorité / effort | Constat et effet pour l’usage | Proposition concrète |
| --- | --- | --- | --- |
| U01 | P1 / M | Accueil : neuf actions, dont Guide TV, Direct, Québec et Favoris qui offrent des entrées proches vers les mêmes chaînes (`MainActivity.home`). L’utilisatrice doit choisir une structure avant de choisir une émission. | Unifier l’univers Télé avec un guide ouvrant les favoris; conserver Québec et Toutes à l’intérieur. Accueil proposé : Télé, Films, Séries, Mes favoris et Rechercher; réglages et accès occasionnels secondaires. Garder les raccourcis préférés si le test réel les justifie. |
| U02 | P1 / L | Chaque sélection de film/épisode appelle `play(entry, position = 0)`. La position liée au cycle de vie n’est pas une reprise enregistrée par titre. Aucun écran Continuer. | Enregistrer progression et statut vu localement; proposer Continuer à 42 min / Recommencer et une rangée Continuer. Restaurer après fermeture complète de l’app et mise à jour. |
| U03 | P1 / M | Le menu son affiche langue brute, canaux et codec, y compris audio/mp4a-latm (`audioMenu`), au lieu de répondre simplement à « quel son choisir ? ». | Afficher Français — son normal / Français — avec narration si la piste est réellement identifiée. Sinon Piste 1 — langue non précisée. Mettre les codecs dans Détails techniques. Une seule piste : expliquer qu’il n’y a pas d’autre son reçu, sans proposer une fausse désactivation de narration mélangée. |
| U04 | P1 / S–M | Réglages mélange connexion Xtream, HLS/MPEG-TS et préférences quotidiennes (`settings`). Changer de format agit immédiatement. | Afficher Son, Affichage, Petits mots, Mise à jour; déplacer Compte, Format du direct et diagnostic sous Réglages avancés. Renommer la bordure Image coupée sur les bords; offrir un aperçu plutôt que demander de comprendre 5/8 %. |
| U05 | P1 / M | Favoris : ajout dans un menu ou par appui long; le bouton du lecteur reste ★ Favori après ajout/retrait (`play`). Un retrait est immédiat, confirmé seulement par un message bref. | Montrer l’état vide/plein près du titre; Ajouter aux favoris / Dans mes favoris. Après retrait, proposer Annuler quelques secondes. Garder Menu et appui long comme raccourcis facultatifs. Aucune barre inférieure à réintroduire dans le guide. |
| U06 | P1 / M | Les erreurs de lecture exposent HTTP et ERROR_CODE sur l’écran principal; l’erreur réseau générique parle parfois de release GitHub même dans un catalogue (`playbackError`, `async`). | Message contextualisé et une action utile : Cette chaîne ne répond pas → Réessayer / Autre chaîne; Pas de connexion → Réessayer. Déplacer le code sous Détails pour assistance. Ne pas attribuer une panne réseau au fournisseur sans preuve. |
| U07 | P1 / M | Les mises à jour conservent les favoris, mais une nouvelle connexion avec serveur ou utilisateur différent les efface sans avertissement explicite (`login`, `Store.clearFavourites`). | Avant le changement de compte, expliquer l’effet et conserver une sauvegarde liée à l’ancien profil. Ne pas réaffecter automatiquement les identifiants de chaînes d’un fournisseur à un autre. Protéger ce parcours dans les réglages avancés. |
| U08 | P1 / M | Le focus est rose plein sur les boutons, bordure/teinte sur les affiches, et plusieurs bordures coexistent dans le guide. Tabulation active et action qui recevra OK peuvent se confondre. | Un même signe fort pour « OK agit ici » : contour lumineux + léger agrandissement borné. Donner à l’onglet actif un autre signe, par exemple un soulignement. Respecter les marges et éviter que l’animation coupe le contenu. |
| U09 | P1 / M–L | Pour changer de chaîne depuis le lecteur, Guide TV arrête la lecture (`openTvGuide`). Le lecteur contient un seul MediaItem : ses boutons précédent/suivant ne constituent pas un zapping des chaînes. | Proposer un mini-guide superposé avec favoris, émission actuelle et suivante, puis OK pour changer de chaîne. Réutiliser le lecteur en cours et garder Retour pour fermer ce panneau. Mesurer la fluidité sur Fire Stick avant d’enrichir davantage. |
| U10 | P1 / M | Le guide filtre hors UI, mais `renderRows` des listes/galeries parcourt encore les catalogues sur le thread d’affichage. Risque déduit du code, non mesuré sur un vrai gros catalogue VOD pendant cet audit. | Généraliser filtrage/recherche annulables en arrière-plan, sans bloquer la télécommande. Conserver la roue lors d’un vrai changement, avec un seul message de statut et sans résultats de l’ancien onglet. |
| U11 | P2 / M | Dans le guide, Revenir à maintenant est caché dans un menu de huit actions. L’heure en haut correspond à la fenêtre horaire consultée, sans libellé explicite (`updateClock`). | Ajouter Maintenant dans le menu du haut ou près de la date, un repère En direct et une séparation claire entre heure actuelle et horaire consulté. Garder le bas libre comme demandé. |
| U12 | P2 / M | Titres et descriptions du guide sont sur une ligne tronquée; Détails de l’émission se trouve dans Menu (`selection`, `options`). | Sur la sélection : titre lisible, horaire, durée restante si les données le permettent, puis un accès Détails visible et compact. Une émission future reste explicitement une diffusion en direct, jamais une reprise promise. |
| U13 | P2 / M–L | Une affiche de film démarre immédiatement; les données affichées ne donnent pas synopsis, année, durée ou contexte (`Entry`, `CatalogParser`, `buildList`). | Une fiche sobre : grande affiche, titre, année/durée et résumé fournis, boutons Regarder/Continuer et Favori. Prévoir les métadonnées absentes. Ne pas ajouter des filtres linguistiques aux galeries. |
| U14 | P2 / M–L | Les séries ouvrent une liste plate de toutes les saisons, avec S1 dans le titre, sans progression ou épisode suivant (`Xtream.episodes`, `episodes`). | Fiche série, choix simple de saison, Reprendre le prochain épisode, repères Vu/En cours. Proposer l’épisode suivant avec possibilité de refuser; choix de lecture automatique facultatif. |
| U15 | P2 / M | Le champ Rechercher exige Chercher et ne couvre que la liste courante. Dans le guide, Recherche ✓ ne montre pas le terme saisi. Un résultat vide peut paraître être un catalogue manquant. | Donner le contexte Dans mes favoris / Films en français, afficher le terme actif et Effacer. Proposer Élargir aux chaînes Québec quand pertinent. Ajouter une entrée Rechercher globale avec résultats regroupés, sans obliger à comprendre les catégories du fournisseur. |
| U16 | P2 / M | Favoris de l’accueil mélange chaînes, films, séries et épisodes dans une liste textuelle; tri alphabétique imposé (`favourites`, `Store.favouriteEntries`). | Mes chaînes / Mes films / Mes séries dans cette bibliothèque, pochettes adaptées et ordre personnalisable via Modifier. Les favoris du guide restent uniquement les chaînes. |
| U17 | P2 / S–M | Le guide vide indique un chemin Menu → Ajouter, mais n’offre pas d’action directe; l’aide est un long dialogue. | Bouton Choisir mes chaînes, explication courte et un exemple. Montrer Retour : menu et Menu : options au besoin dans un petit indice contextualisé, sans remettre la barre retirée. Remplacer le manuel par trois cartes illustrées consultables. |
| U18 | P2 / M | Les dialogues sont gris natifs alors que l’app est prune/rose. Plusieurs écrans répètent titre et sous-titre; Options / Favoris et les libellés de bas de lecteur sont très présents. | Harmoniser les dialogues avec la palette, réduire les titres répétés et garder une seule action principale par panneau. Le contenu doit gagner l’espace visuel récupéré. |
| U19 | P2 / M | Certains textes du guide sont à 11–14 unités, le texte est plafonné à fontScale 1,3. Le texte discret #C5B5C9 sur la sélection #72415D donne environ **4,12:1**, contre 7,43:1 sur le panneau normal. | Rehausser le contraste des heures sur la sélection et prévoir Texte plus grand avec mise en page adaptée. Vérifier au vrai divan, avec lunettes/lumière habituelles. La palette principale rose sur fond reste contrastée (environ 8,73:1). |
| U20 | P2 / S | Le mot d’amour ouvre un dialogue obligatoire au lancement ou au retour de l’app au premier plan sur l’accueil (`love`, `onStart`). Le geste affectueux ajoute une confirmation avant de regarder. | Conserver les 50 messages et la rotation, mais les présenter dans une carte discrète à l’accueil, sans bloquer le focus; lecture complète possible sur demande. Garder l’option existante. |
| U21 | P2 / M | Reprendre dans le lecteur relance prepare/play; ce bouton n’est pas la reprise mémorisée d’un film. Infos expose des données de diagnostic en permanence. | Réserver Continuer à la progression sauvegardée; appeler Réessayer une relance de flux. Menu principal du lecteur compact : Chaînes si direct, Son, Sous-titres, Favori; diagnostic et vitesse sous Plus. Le bouton Guide TV existe déjà pour le direct. |
| U22 | P2 / M | Une mise à jour passe par notes, roue indéterminée et installateur; pendingInstall est seulement en mémoire malgré l’APK sauvegardé (`checkUpdate`, `Updater`, `pendingInstall`). | Afficher progression de téléchargement, étapes courtes et rappel Favoris conservés; retrouver le paquet déjà vérifié après relance, avec revalidation avant installation. Garder Installer / Plus tard et les confirmations imposées par Fire OS. |
| U23 | P2 / M | Pas de cache persistant du catalogue/guide; cache des pochettes seulement en mémoire. Les retours déjà conservés en mémoire sont utiles, mais une fermeture complète nécessite des chargements. | Cache local borné et rafraîchissement contrôlé, avec état Horaire en cours de mise à jour si données anciennes. Ne pas inventer d’émissions ni présenter un horaire périmé comme actuel. Respecter le stockage limité du Fire Stick. |
| U24 | P3 / M | L’accueil est surtout un ensemble de grandes surfaces de boutons, avec peu de reconnaissance visuelle des contenus. Guide/listes n’affichent pas les logos des chaînes. | Ajouter mes chaînes avec logos et programme actuel, une carte Dernière chaîne et une rangée Continuer. Employer les vraies images du fournisseur, avec cache borné et remplacement propre. Le rose reste l’accent, la vidéo et les affiches restent prioritaires. |
| U25 | P3 / S–M | Adultes est immédiatement visible à l’accueil et dans le guide; le logo humoristique est volontaire et cohérent avec la personnalisation demandée. | Conserver l’accès demandé et le logo; offrir une option de discrétion d’affichage de la rubrique, sans la retirer arbitrairement. Une éventuelle variante de logo plus discrète serait un choix du propriétaire, pas une correction imposée. |
| U26 | P3 / M–L | Connexion à saisir à la télécommande, champs dont le libellé visuel disparaît une fois remplis; pas d’assistant d’installation. | Libellés permanents, exemple clair, afficher/masquer le mot de passe et erreurs par champ. Étudier une saisie depuis téléphone uniquement avec un mécanisme de jumelage temporaire sécurisé et une compatibilité Fire TV vérifiée. Aucune donnée de compte dans GitHub. |
| U27 | P3 / M–L | La voix, VoiceView et les très grandes polices ne sont pas validées; le guide est exposé comme un contrôle d’accessibilité unique. | Tester sur le matériel réel, annoncer correctement focus/émission/état, adapter la navigation accessible. Ajouter dictée/recherche vocale seulement si l’intégration Fire TV le permet, avec saisie classique toujours disponible. |
| U28 | P3 / M–L | Favoris et préférences sont locaux; pas d’export de restauration ni d’ordre synchronisé entre appareils. La connexion doit rester propre à chaque Fire Stick. | Sauvegarder/restaurer les préférences et favoris sans exporter le compte par défaut. Si partage entre TV, vérifier la correspondance des identifiants du catalogue et conserver leurs comptes distincts; ne pas promettre une synchronisation universelle. |

### Ordre de réalisation proposé

1. **Clarté quotidienne** : U01, U03–U08, U11 et U20. Réduire les choix, expliciter le son, rendre les favoris visibles/réversibles, harmoniser le focus, ranger les réglages et garder Maintenant accessible. U07 protège la promesse de conservation des favoris. Vérifier U10 sur un gros catalogue VOD avant livraison.
2. **Films et séries confortables** : U02, U13–U16 et U21. Reprise persistante, fiches, saisons, recherche contextualisée et vocabulaire de lecture. C’est un chantier fonctionnel plus substantiel qu’une nouvelle couleur.
3. **Fluidité et finition** : U09, U18–U19, U22–U28, selon les essais réels. Prioriser la fluidité et la lecture au divan; éviter de charger le Fire Stick de grands effets graphiques ou d’options rarement utilisées.

Architecture visée : **Télé → Guide (Favoris d’abord, Québec, Toutes)**; **Films / Séries → Continuer + pochettes → fiche → regarder**; **Mes favoris → bibliothèque**; **Rechercher → résultats regroupés**. Réglages accessibles mais secondaires. Les accès Sports et Adultes sont conservés à un niveau adapté à leur fréquence réelle.

### Validation avec l’utilisatrice, à réaliser

Une séance de 10–15 minutes sur sa télévision, sans explication préalable, permettrait de valider les hypothèses. Scénarios : retrouver Zeste depuis l’ouverture; ajouter une chaîne puis la retrouver; revenir au menu depuis le bas du guide; reprendre un épisode interrompu; trouver un film français; corriger un mauvais choix audio; réagir à une chaîne indisponible. Noter les hésitations, demandes d’aide, erreurs de parcours et nombre de gestes, pas seulement le temps.

Cibles de conception proposées, non mesurées : aucune demande d’aide sur regarder/retour/favoris; retrouver ses chaînes en deux ou trois choix après ouverture; identifier immédiatement où OK agit; aucune suppression de favori sans possibilité de récupération; reprise au bon épisode après fermeture complète; aucun réglage de protocole dans un usage normal. Mesurer les délais sur les deux Fire Stick avant de fixer une promesse de rapidité.

Cet audit formule des propositions. La version distribuée reste **0.5.2**; les recommandations ne sont pas des fonctionnalités déjà livrées.


## 27. Première étape de simplification — 0.6.0 / code 12

Chantier autorisé après l’audit de la section 26 : U01, U03–U08, U11, U20, avec traitement du risque U10. Le nom, le rose, les 50 messages, le guide commençant par les favoris, Québec pour le direct et français de toutes origines pour les galeries sont préservés. Sports et Adultes passent sous Plus à l’accueil; Adultes reste aussi dans le guide. Aucun filtre linguistique ajouté aux films/séries, aucune barre de boutons ajoutée au bas du guide.

### Décisions et comportement

- Accueil : quatre cartes Télé / Films / Séries / Mes favoris; Rechercher ouvre un choix de type de contenu puis sa recherche existante. Ce n’est pas encore une recherche globale de tous les catalogues en une requête. Les retours dans le guide conservent son dernier filtre comme auparavant; une première ouverture commence par Favoris.
- Petit mot : carte non bloquante, rotation au lancement/retour au premier plan lorsque l’accueil est visible, sans avancer à chaque reconstruction de l’accueil. Aucune interception du démarrage physique de la TV.
- Son : noms de langue lisibles, narration signalée seulement si les métadonnées l’identifient, codecs dans Détails, explication persistante quand une seule piste est reçue. L’absence d’étiquette AD n’est jamais présentée comme la preuve d’un son sans narration. Préférences auto/stéréo/5.1 et évitement de description inchangés.
- Réglages : quotidien séparé des options avancées, aperçu du cadre pour 5/8 %, Retour depuis un sous-écran rejoint les réglages. Messages réseau sans URL de compte ni texte brut d’exception, action Réessayer; détails HTTP/Media3 dans un dialogue secondaire de lecture.
- Favoris : état étoilé visible dans le guide et le lecteur; retrait appliqué puis dialogue permettant Annuler le retrait. Restauration idempotente via Store.setFavourite, y compris quand la liste s’est vidée. Ce dialogue de récupération demande Terminé ou Retour si le retrait est voulu.
- Conservation : mêmes SharedPreferences vicktv, mêmes clés account/favourites/favourite_details, même chiffrement Keystore et même certificat. Aucun effacement pour la nouvelle UI. Un changement authentifié de serveur/utilisateur conserve un instantané sous favourite_profiles, indexé par empreinte SHA-256 de l’identité serveur/utilisateur, sans mot de passe. Les identifiants d’un fournisseur ne sont pas appliqués à un autre. Le changement est confirmé avant connexion; échec d’authentification ne touche pas au profil. Les favoris restent locaux et ne survivent pas à une désinstallation.
- Guide : Maintenant dans l’en-tête, date identifiée Horaire, coche pour le filtre actif et contour blanc pour le bouton qui a le focus. Le premier Retour depuis les chaînes rejoint le dernier bouton du haut; Bas retrouve chaîne et fenêtre horaire.
- Catalogue : CatalogueSelection hors thread UI; annulation par Future et garde de génération/page avant publication. Une recherche dépassée ne remplace pas l’écran actuel. Ancienne grille masquée, roue rose puis résultats/état vide; création des libellés de listes hors UI également. Pochettes toujours bornées/cachées par PosterLoader.

### Vérification et publication

Les résultats finaux, liens de publication et empreinte de l’APK sont consignés ci-dessous après les essais. Plan de récupération : retirer une publication défectueuse au besoin, conserver le paquet/signature/données et corriger avec un versionCode supérieur; Android ne permet pas un retour automatique de code 12 vers 11. Ne pas demander de désinstaller pour récupérer une mise à jour.

Restent hors de cette étape : reprise persistante VOD, fiches films/séries, saisons, mini-guide pendant lecture, cache disque du catalogue et progression de téléchargement. Le débit Edge, la télécommande physique et la sortie HDMI des deux Fire Stick restent à valider sur place.

Validation locale terminée : **39 tests unitaires réussis**, `lintDebug` **0 erreur / 9 avertissements**, `assembleRelease` réussi. Les avertissements restants concernent la cible et les dépendances épinglées, des attributs du manifeste et la localisation déjà présente dans PosterAdapter. `apksigner verify` valide le certificat initial `6a09826ceee768d04367e205731a23a1b03a791c25b150c4cf232db2274d2e16`; aapt confirme `ca.vicktv`, code 12, version 0.6.0, libellé VickTv et absence de mode débogable.

Parcours réellement exercés sur émulateur API 35, sans compte Edge : installation signée par-dessus 0.5.2 avec favori Télé-Québec fictif conservé, aucune reconnexion nécessaire; installation de l’APK final avec le même favori et compte conservés. Retrait puis annulation vérifiés dans guide et liste; étoile réelle du lecteur inspectée. Deux profils fictifs ont chacun retrouvé leur propre liste après changement et retour. Retour depuis une sélection profonde conserve chaîne/heure; Bas revient dans la grille; Maintenant et étoile de l’en-tête sont accessibles au D-pad. Marges 5/8 %, pochettes françaises, recherche depuis l’accueil, erreur HTTP 403 avec diagnostics séparés et piste unique de langue inconnue sont vérifiés. Désactivation des petits mots, relance puis réactivation : carte remplie, sans dialogue bloquant.

Essai de volume : 40 000 titres supplémentaires sur le fournisseur fictif; Retour pendant le chargement reste à l’accueil après la réponse tardive. Le catalogue chargé demeure navigable, les titres anglais sont filtrés et la recherche 39998 retrouve le titre français attendu. L’assertion initiale du script a précédé la fin du filtrage asynchrone; l’état final a été vérifié à l’écran, puis le scénario repris avec attente appropriée. Ce test ne mesure pas la vitesse d’un Fire Stick ni d’Edge.

APK testé et préparé : **6 986 699 octets**, SHA-256 `9b531f293373425c70b6eda381b30644e1b511357feec684e20851624859fbba`. Les 24 morceaux de distribution reconstruisent exactement cet APK; le manifeste code 12 pointe vers v0.6.0. Les copies temporaires de signature ont été retirées de qa; l’archive privée originale reste hors Git. Captures d’accueil et guide actualisées avec données fictives.

Publication confirmée le 21 octobre 2026 : [PR 11](https://github.com/kcote84/VickTV/pull/11), commit de fusion `bf049c994e56dd9534aad067f6f9f953a02e32ae`. La [CI de la PR](https://github.com/kcote84/VickTV/actions/runs/39705048058) a réussi avant fusion; le [workflow de publication](https://github.com/kcote84/VickTV/actions/runs/39705294890) a réussi. [Release v0.6.0](https://github.com/kcote84/VickTV/releases/tag/v0.6.0) disponible.

Vérification externe après publication : le manifeste latest est identique au manifeste local (code 12 / 0.6.0); l’APK téléchargé par le lien versionné et par le lien stable est identique, octet pour octet, à l’APK testé. SHA256SUMS et apksigner sur le téléchargement public confirment l’empreinte ci-dessus et le certificat initial. Aucun jeton GitHub requis. Le journal AndroidRuntime de la session d’essai ne contient pas d’exception fatale; fournisseur fictif et émulateur arrêtés après validation.

Mise à jour depuis la version précédente : Réglages → Vérifier les mises à jour. Dans 0.6.0, le bouton s’appelle Mise à jour. Installer par-dessus, sans désinstaller, pour conserver compte, favoris, préférences et rotation des petits mots. [Téléchargement stable de l’APK](https://github.com/kcote84/VickTV/releases/latest/download/VickTV.apk).

## 28. Films et séries — chantier 0.7.0 / code 13

Étape autorisée après 0.6.0 : reprise persistante, fiches et choix des saisons. Préparation en cours; la version distribuée reste celle de l’en-tête jusqu’à confirmation de publication.

### Architecture et choix d’usage

- `MediaDetailsParser` lit get_vod_info / get_series_info et leurs métadonnées facultatives : résumé, année, durée, genre, distribution, réalisation, note et pochette. Une fiche reste honnête quand des champs manquent; une panne de métadonnées n’empêche pas de lancer un film avec son entrée de catalogue. Les épisodes sont regroupés par saison, triés numériquement et dédoublonnés par identifiant. Spéciaux reste une saison distincte; une nouvelle série commence par une saison régulière si disponible.
- `MediaScreen` offre une fiche native à la télécommande : affiche, titre, contexte, résumé, Regarder/Continuer/Recommencer, état favori et Plus. Pour une série : choix de saison, liste numérotée, repères Vu/En cours, résumé d’épisode et marqueur vu modifiable. Le bouton Continuer de la fiche agit directement; une sélection d’épisode déjà commencé propose Continuer/Recommencer. Fin d’épisode : proposition du prochain épisode disponible, sans lecture automatique ni compte à rebours.
- `WatchHistory` conserve position, durée, état terminé, date de lecture, contexte série/saison/épisode et prochain épisode. Sauvegarde toutes les 5 s en lecture, à la pause, aux déplacements, à l’arrière-plan et avant libération du lecteur. Une reprise devient visible à partir de 5 s; la fin réelle ou les derniers min(90 s, 5 % de la durée) indiquent Vu. Durée inconnue : aucune fin déduite de la position seule. Recommencer est volontaire et remet la progression au début lorsque la lecture démarre.
- Stockage local `watch_history:<empreinte du profil>`, séparé pour chaque serveur/utilisateur et limité aux 200 derniers titres/épisodes par compte. Aucun changement des clés `favourites`, `favourite_details`, `favourite_profiles`, `account` ou du Keystore. `EntryJson` relit les anciens favoris sans contexte de série et ajoute des champs facultatifs pour les nouveaux épisodes; les anciens épisodes favoris restent lisibles directement, sans deviner leur série.
- Dans Films/Séries : accès Continuer au titre récent et Tout reprendre pour les autres, avec pochettes et repères de progression. Le catalogue demeure français de toutes origines, sans menu de filtre linguistique. Les positions viennent des lectures commencées avec cette version; les anciennes versions ne les sauvegardaient pas. Une reprise nécessite un flux qui accepte le déplacement temporel; la synchronisation entre téléviseurs n’est pas ajoutée.
- Mes favoris est séparé en Chaînes / Films / Séries / Épisodes. Les identifiants et le tri alphabétique existants sont conservés. Les recherches indiquent leur contexte et proposent Effacer. Le lecteur regroupe son, sous-titres, favori, Plus et Retour; diagnostics, vitesse et Réessayer restent sous Plus.

Périmètre restant après cette étape : recherche globale unique, ordre personnalisé des favoris, cache disque de catalogue, mini-guide sur la vidéo et synchronisation entre appareils. L’historique local n’est pas un service de sauvegarde et une désinstallation efface ces données comme les favoris.

### Validation en cours

Première compilation : 54 tests unitaires réussis, lintDebug sans erreur (9 avertissements), assembleRelease signé avec le certificat initial. Installation 0.6.0 → première version 0.7.0 sur émulateur API 35 : compte et favori Télé-Québec fictif conservés. Les essais de reprise réelle après fermeture, de saisons et d’épisode suivant sont en cours; aucun Fire Stick ni flux Edge n’est utilisé pour ces essais.

### Résultat final des validations locales

- **57 tests unitaires réussis**, `lintDebug` **0 erreur / 9 avertissements**, `assembleRelease` réussi. Les nouveaux tests couvrent métadonnées absentes ou malformées, compatibilité des favoris, saisons triées et spéciaux, reprise et état terminé, épisode suivant, limite de 200 entrées, retrait de Continuer sans perte et corrections Vu effectuées dans un ordre différent. La recommandation saute les épisodes déjà vus; modifier un marqueur existant conserve sa date au lieu de le présenter comme une nouvelle lecture.
- Retirer de Continuer ajoute un marqueur masqué, sans effacer position ni état Vu. Le menu permet de remettre l’entrée; recommencer à regarder la rend de nouveau visible. Les favoris restent indépendants. Un épisode favori se lance directement, comme auparavant, avec Continuer/Recommencer si nécessaire; Options permet aussi Voir la série lorsque le lien est connu. Une suite inconnue n’est jamais annoncée comme le dernier épisode de la série.
- Lecture réelle de médias fictifs sur émulateur API 35 : reprise d’un film après arrêt complet, de 14 s à 22 s; redémarrage volontaire revenu à 7 s; arrêt pendant lecture avec reprise périodique à 8 s. Séries : saisons 0/1/2/10, ouverture initiale en saison 1, sélection en saison 2, retour à la bonne saison, relance retrouvant le même épisode et proposition passant de saison 1 à saison 2 sans démarrage automatique. Favori d’épisode lisible directement et lien Voir la série vérifiés.
- Métadonnées en échec HTTP 503, résumé/pochette absents, série sans épisodes, correction Vu/Non vu, galerie Continuer, marges 5/8 % et focus D-pad exercés. Une fiche lente quittée avec Retour ne réapparaît pas après sa réponse; la recherche précédente est conservée. Deux comptes fictifs retrouvent leurs favoris et historiques respectifs, sans mélange. Les captures des fiches utilisent des données et affiches fictives.
- Installation signée sur 0.6.0 : compte et favori historique conservés. Installation de l’APK définitif sur la préparation 0.7.0 : même film à **10 s**, même série **S2 E2 à 11 s**, mêmes favoris chaîne/film/série/épisode. Le fichier installé a été extrait par adb et comparé octet pour octet à l’APK de distribution. Aucun essai sur Fire Stick réel, aucun appel Edge ni écoute HDMI dans ce chantier.
- Le labo Android disposait de 281 Mo libres mais sa réserve système refusait une installation. Le seuil de réserve de cet émulateur a été abaissé uniquement pendant l’installation puis rétabli à sa valeur initiale (`null`); aucune désinstallation ni remise à zéro de données. Les scripts UI ont été adaptés aux vues compressées et aux descriptions d’accessibilité pour éviter la relecture d’une ancienne hiérarchie pendant une vidéo.

APK préparé : **7 016 323 octets**, SHA-256 `1e6c91012c33c7510aabd907c27831e4438ccba7da7b25f1575ca0c11a5166e8`, certificat initial `6a09826ceee768d04367e205731a23a1b03a791c25b150c4cf232db2274d2e16` validé par apksigner. aapt confirme ca.vicktv, 0.7.0/code 13, libellé VickTv, sans mode débogable. Les 24 morceaux reconstruisent exactement cet APK; manifeste et somme SHA-256 sont cohérents. Copies temporaires de signature retirées, archive privée originale conservée hors Git.

Plan de récupération : conserver paquet, signature et données; corriger toute régression avec un versionCode supérieur à 13. Une désinstallation effacerait les favoris et l’historique local. La publication et les téléchargements publics seront consignés après confirmation.
