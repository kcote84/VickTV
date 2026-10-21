# VickTV

Un lecteur Xtream Codes personnel pour Fire TV, pensé pour Vicky : peu de menus, de gros boutons et un petit mot d’amour avant la soirée.

## Documentation du projet

Pour reprendre le travail : [référence technique, décisions et suivi](docs/DEVELOPPEMENT.md). Ce document central décrit l’architecture, l’état validé, les limites et les prochaines étapes.

## Évolution 0.2.0 — sources en révision

L’APK public reste **0.1.0**. Les changements suivants sont préparés dans les sources pour **0.2.0 (code 2)**; ils ne seront disponibles dans une mise à jour qu’après compilation avec la clé de signature initiale et publication d’un nouvel APK.

- Lecture plein écran lorsque les commandes sont masquées; Retour du lecteur vers la liste, la recherche et la sélection précédentes, sans recharger le catalogue.
- Bouton **Options / Favoris** et touche Menu dans les listes; suppression immédiate d’un favori dans sa section.
- Filtre **Langue : Tous / Français / Québec** dans Films et Séries, combinable avec les catégories originales. Ce classement se base sur les noms du fournisseur; il ne vérifie pas la langue des pistes ni l’origine d’une œuvre. « Canada » seul ne classe plus un contenu comme francophone.
- **Horaire par chaîne** dans les options ou le lecteur : émission en cours, prochaines émissions et descriptions retournées par Xtream. Les heures suivent le fuseau du Fire Stick. Il ne s’agit pas encore d’une grille multichaîne ou d’un guide complet sur plusieurs jours.
- Préférences **Automatique / Stéréo / Cinéma 5.1**. L’option **Éviter l’audiodescription** est activée par défaut : elle écarte les pistes identifiées comme descriptives si une piste normale existe, y compris si une piste descriptive avait été mémorisée. Le choix manuel reste possible; **Choix automatique** efface la préférence du contenu. Une narration non identifiée ou intégrée au seul flux ne peut pas être retirée.

## Première installation

1. Télécharger **VickTV.apk** dans [Releases](https://github.com/kcote84/VickTV/releases).
2. Transférer l’APK sur le Fire Stick (par exemple avec un outil de transfert de fichiers) ou utiliser Downloader lorsque le lien de téléchargement est public.
3. Autoriser cette source d’installation dans **Ma Fire TV → Options développeur → Installer des applications inconnues**. Le libellé varie selon Fire OS.
4. Installer puis ouvrir VickTV. Le mode démo fonctionne sans compte.
5. Dans **Réglages → Connecter mon compte Xtream**, entrer l’URL du serveur, l’utilisateur et le mot de passe. Refaire sur la deuxième TV avec son deuxième compte.

**Compatibilité :** Android 8+ / Fire OS 7+. Les Fire Stick sous Vega OS ne peuvent pas installer cet APK. Modèles exacts des deux appareils à confirmer avant validation.

## Navigation

- Flèches : naviguer; OK : ouvrir ou regarder; Retour : revenir.
- Accueil : Français, Sports, Films, Séries, Favoris et Toutes les chaînes.
- Les regroupements Français/Sports utilisent les noms des catégories et chaînes du fournisseur; ils peuvent être imparfaits. Le bouton **Catégories** donne accès au catalogue original.
- Appui long sur OK dans une liste : ajouter/retirer un favori.
- Pendant la lecture : **Audio / Son** ou touche Menu de la télécommande pour choisir une piste.
- **Infos** décrit le flux reçu, sans prétendre mesurer la sortie HDMI.
- Le menu Réglages propose le direct MPEG-TS/HLS et la préférence pour une piste stéréo disponible.

## Les petits mots

50 messages français distincts, dans un ordre tournant conservé sur chaque appareil. Ils apparaissent au lancement et au retour dans l’app lorsque l’accueil est visible. Allumer uniquement le téléviseur ne garantit pas que Fire OS ouvre VickTV; aucun démarrage automatique du téléviseur n’est intercepté. Option désactivable dans Réglages.

## Comptes et confidentialité

Chaque Fire Stick garde son propre compte Xtream, chiffré avec Android Keystore. Aucune donnée du fournisseur n’est incluse dans le code, dans GitHub ou dans les APK. Pas de télémétrie. Un serveur HTTP transmet toutefois ses données sans chiffrement réseau; préférer HTTPS lorsque disponible. Ne jamais ajouter de vrais identifiants au dépôt.

## Mises à jour

Dans **Réglages → Vérifier les mises à jour**, l’app lit `update.json` dans la dernière release stable de `kcote84/VickTV`, puis propose le téléchargement et l’installateur système. Validation SHA-256, nom du paquet, version et certificat avant ouverture de l’installateur. Garder la même clé de signature à chaque version.

**Le dépôt et les APK sont publics depuis le 21 octobre 2026.** Le téléchargement et l’accès au manifeste de mise à jour ne demandent donc pas de compte GitHub. Aucune clé GitHub n’est embarquée dans l’APK. L’installation et la mise à jour sur Fire Stick restent à valider sur les appareils réels.

## Développement

Kotlin + vues Android natives + AndroidX Media3 ExoPlayer. Java 17, Android SDK 35, Gradle 8.9. Dépendances épinglées, base volontairement simple.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

La signature release utilise `VICKTV_KEYSTORE`, `VICKTV_STORE_PASSWORD` et `VICKTV_KEY_PASSWORD`, alias `vicktv`. Le fichier de clé reste hors Git. Les builds debug servent aux essais uniquement : leur signature est différente de la release.

Le premier APK signé est conservé dans `distribution/` et un workflow le copie dans GitHub Releases. Les mises à jour préparées localement peuvent suivre le même chemin, sans transmettre la clé de signature à GitHub. GitHub Actions comprend aussi la vérification des sources et une option de compilation signée. Pour activer la publication automatique, ajouter les trois secrets décrits dans [docs/DEVELOPPEMENT.md](docs/DEVELOPPEMENT.md), augmenter `versionCode` et `versionName`, puis pousser le tag correspondant.

## Limites du MVP

Voir les [notes de version](docs/RELEASE_NOTES.md). Compilation et tests automatisés ne remplacent pas les essais de télécommande, de lecture Xtream et de 5.1 sur Fire Stick réel.
