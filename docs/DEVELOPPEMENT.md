# Développement et publication

## Architecture

- MainActivity : écrans TV, navigation et lecteur Media3.
- Xtream : authentification, catégories, chaînes, VOD et épisodes; appels hors du thread d’interface.
- Store : compte chiffré AES-GCM avec clé Android Keystore; préférences locales pour favoris/audio/messages.
- Updater : métadonnées GitHub, téléchargement borné, empreinte SHA-256 et vérification du certificat APK.
- LoveNotes : exactement 50 messages, rotation persistante par appareil.

## Signature

La clé `vicktv.jks` n’est jamais commise. Le propriétaire doit conserver une sauvegarde privée. Sa perte empêche les mises à jour sur place des installations existantes.

Ajouter dans Settings → Secrets and variables → Actions :

- `VICKTV_KEYSTORE_BASE64` : fichier JKS encodé en base64.
- `VICKTV_STORE_PASSWORD` : mot de passe du JKS.
- `VICKTV_KEY_PASSWORD` : mot de passe de la clé `vicktv`.

Ne pas donner accès à ces secrets à des contributions non fiables. Le workflow de pull request ne les utilise pas.

## Publication locale (déjà configurée)

Compiler avec la clé privée initiale, placer le nouvel APK signé dans distribution/VickTV.apk et régénérer distribution/update.json et SHA256SUMS.txt. Pousser ces fichiers sur main : publish-apk.yml les publie dans GitHub Releases. Augmenter versionCode et versionName à chaque publication. Cette voie ne demande aucun secret GitHub.

## Publication par compilation GitHub (optionnelle)

1. Augmenter `versionCode` et `versionName` dans app/build.gradle.kts.
2. Modifier docs/RELEASE_NOTES.md.
3. Exécuter les tests et lint.
4. Pousser un tag `vX.Y.Z` correspondant à versionName; le workflow crée une release signée et son update.json.
5. Vérifier les téléchargements et la mise à jour sur un Fire Stick avant de l’installer sur le second.

Le premier APK peut aussi être compilé localement et ses trois fichiers ajoutés à la release : VickTV.apk, update.json et SHA256SUMS.txt. Le manifeste se génère avec tools/release_metadata.py.

## Validation sur appareils (à compléter par essais réels)

- Installation sur les deux modèles et lancement depuis l’accueil Fire TV.
- Mode démo et rotation des mots sans répétition avant le 51e affichage.
- Comptes différents, redémarrage de l’app, conservation des favoris.
- Télécommande : tous les boutons accessibles sans souris ni écran tactile.
- Flux live TS puis HLS, pistes FR/description, stéréo et AC-3/E-AC-3 si fournis.
- Interruption réseau, retour réseau, veille/reprise, arrière-plan sans lecture résiduelle.
- Films et épisodes, puis mise à jour signée conservant les données.

Les catégories FR/Sports sont heuristiques. La version initiale n’inclut pas d’EPG ou de moteur VLC; ne pas déclarer le 5.1 validé sans mesurer la sortie du matériel.
