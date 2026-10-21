# VickTv 0.4.2

- En cas d’erreur de lecture HTTP, affichage du numéro précis (403, 404, etc.) et d’une explication, sans exposer le lien ni les identifiants du compte.
- En HLS, bouton « Passer en MPEG-TS » pour reprendre la chaîne et conserver ce format pour le direct.
- Les refus d’accès et ressources absentes (401, 403, 404, 410) sont signalés sans trois reconnexions inutiles.
- Cette version facilite le diagnostic : elle ne garantit ni le HLS chez le fournisseur ni une autre piste audio sur Zeste.
- Même paquet et même clé de signature : mise à jour sur place, sans désinstallation.
