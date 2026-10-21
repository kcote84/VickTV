# VickTV — Référence technique et suivi du projet

Dernière mise à jour : **21 octobre 2026**. Version distribuée de référence : **0.4.0**, `versionCode = 5`, thème rose, identité VicTV et regroupement Adultes (section 16). Version précédente : **0.3.0**, `versionCode = 4`. Version initiale conservée : **0.1.0**, `versionCode = 1`. Les sections historiques décrivent les étapes successives; la dernière section fait foi pour le guide actuel.

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

Version préparée : 0.4.1 / code 6. Le paquet `ca.vicktv`, le dépôt `kcote84/VickTV`, le fichier distribué `VickTV.apk`, la signature et les données locales conservent leur identité. Résultats du build et publication à consigner après vérification.

Validation locale : 20 tests réussis, lint sans erreur, assembleRelease réussi avec la clé initiale. APK de 6960075 octets, SHA-256 `944673630f7edd837a727198de397170de96552c61471307b3fbc5ccc0bc9ca2`; tous les morceaux remplacés et reconstruction conforme au manifeste. Aucune nouvelle fonctionnalité ni nouveau test matériel dans ce correctif de nom.
