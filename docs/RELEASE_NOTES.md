# 0.2.0 — en préparation, APK non publié

- Lecture plein écran lorsque les commandes sont masquées; titre et boutons réapparaissent avec les commandes.
- Retour vers la liste et la recherche précédentes après lecture; retour des épisodes vers leur liste d’origine.
- Bouton visible pour les favoris et les options de la chaîne; retrait immédiat dans Favoris.
- Filtres Français et Québec pour Films et Séries, selon les noms des catégories du fournisseur.
- Horaire par chaîne : émission en cours, prochaines émissions et descriptions lorsque Xtream les fournit. Pas encore de grille multichaîne.
- Audiodescription évitée par défaut lorsqu’une autre piste prise en charge est disponible; détection des rôles et libellés, repli vers une piste normale d’une autre langue au besoin.
- Choix audio Automatique / Stéréo / Cinéma 5.1 et possibilité de réinitialiser le choix mémorisé d’un contenu. Ces préférences ne garantissent ni conversion audio ni sortie HDMI 5.1.
- Correction d’un état de chargement obsolète et tri des épisodes par numéro.
- La publication automatique vérifie désormais le certificat initial avant de créer la release et transmet les notes à l’écran de mise à jour.

La publication nécessite la clé de signature originale. La lecture Edge, les télécommandes Fire TV et le son HDMI restent à vérifier sur les appareils réels. La version publique disponible demeure 0.1.0.

# 0.1.0 — version publiée

Première version de VickTV pour essais sur Fire Stick (Fire OS 7 ou supérieur).

- Accueil Français, Sports, Films, Séries, Favoris et toutes les chaînes.
- Connexion Xtream propre à chaque appareil, identifiants chiffrés localement.
- Recherche et catégories du fournisseur.
- Sélection des pistes audio et mémorisation par chaîne ou épisode.
- Lecture MPEG-TS / HLS et reconnexion limitée à trois tentatives.
- Cinquante mots d’amour en rotation à l’ouverture ou au retour à l’accueil.
- Vérification des mises à jour depuis GitHub Releases, avec empreinte et signature APK.

Cette version doit encore être validée sur les Fire Stick réels et les flux du fournisseur. Aucun contenu, chaîne ni compte IPTV inclus. Pas encore de guide horaire EPG, de reprise VOD persistante ou de second moteur VLC. Le codec AC-3/E-AC-3 et le 5.1 dépendent de l’appareil et de sa sortie audio. Le lecteur ne retire pas une vidéodescription intégrée à l’unique piste fournie.
