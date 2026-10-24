# LibreHU Dialer

Application téléphone pour autoradios LibreHU (UJC201 / AC8257, Android 9), pensée pour la voiture : grandes
cibles tactiles, paysage, thème et accent synchronisés avec [LibreHU Launcher](https://github.com/LibreHU/LibreHU-Launcher-App).
Inspirée de l'ergonomie d'Android Auto, sans en être un client ni reprendre ses éléments graphiques.

Cette branche : **`ivi`** (service Bluetooth Jancar de la ROM d'origine).

## Captures d'écran

| | |
|---|---|
| ![Favoris](docs/screenshots/favorites.png)<br>Favoris | ![Récents](docs/screenshots/recents.png)<br>Récents |
| ![Contacts](docs/screenshots/contacts.png)<br>Contacts | ![Clavier](docs/screenshots/keypad.png)<br>Clavier |
| ![Réglages](docs/screenshots/settings.png)<br>Réglages |  |

Captures sur l'émulateur Android 9 (1280×720, 160 dpi comme l'autoradio), sans autoradio branché : les valeurs du véhicule et de la MCU sont vides. Refaites-les avec l'action **Screenshots** de [LibreHU-OTA-app](https://github.com/LibreHU/LibreHU-OTA-app/tree/main/screenshots).

## Fonctions

- **Favoris** : contacts favoris du téléphone (sinon les numéros les plus appelés) et derniers appels.
- **Récents** : journal d'appels (reçus, émis, manqués en rouge, refusés), regroupement des appels consécutifs,
  filtre « Manqués ».
- **Contacts** : répertoire avec photos, index alphabétique, recherche par nom ou numéro, choix du numéro quand un
  contact en a plusieurs.
- **Clavier** : grandes touches, appui long sur 0 pour « + », appui long sur effacer pour tout effacer, suggestions
  de contacts pendant la saisie, par numéro ou par **recherche T9** (5-3-6 trouve « Jean » : début du nom, début
  d'un mot, puis initiales ; 4 suggestions) ; ouvre aussi les liens `tel:` et l'action `DIAL` des autres applis.
- **Écran d'appel** : appel entrant (répondre / refuser, double appel), durée, micro coupé, clavier DTMF, mise en
  attente, permutation entre deux appels, raccrocher ; réductible en barre au-dessus des listes.
- **Commandes flottantes** pendant un appel, par-dessus les autres applis (navigation…) : nom, durée, micro,
  raccrocher, déplaçables ; un toucher rouvre l'écran d'appel (autorisation « affichage par-dessus »).
- **Appel entrant par-dessus les applis** : carte façon Android Auto (initiale, nom, numéro, Refuser / Répondre) au
  lieu de l'écran d'appel complet, aux couleurs du launcher ; devient les commandes flottantes une fois décroché.
- **En-tête** : réseau du téléphone (barres, opérateur, itinérance, « Pas de service », batterie) lu sur le profil
  HFP, à la place de l'heure.
- **Widgets** : raccourcis (favoris), derniers appels et **clavier** (12 touches, effacer, appeler : l'appel
  passe par l'appli), thème et couleur d'accent du launcher.
- Français et anglais.

## Branches

| Branche | Moteur téléphone | Comment |
|---|---|---|
| `main` | **Android Telecom** | L'appli devient l'**appli téléphone par défaut** (Réglages → Téléphone). Les appels du téléphone Bluetooth sont des appels Telecom créés par le service HFP client d'Android (`com.android.bluetooth`) : l'`InCallService` de l'appli les reçoit, `TelecomManager.placeCall` les passe. |
| `ivi` | **Jancar `ivi-btservice`** | Binder `com.jancar.btservice.bluetooth.IBluetooth` (ROM d'origine). Voir [docs/jancar-binder-contract.md](https://github.com/LibreHU/LibreHU-dialer-app/blob/ivi/docs/jancar-binder-contract.md) sur la branche `ivi`. |
| `librehu-service` | **[LibreHU-service](https://github.com/LibreHU/LibreHU-service)** | API Bluetooth `ILibreHuBluetooth` (API 3) : appels, état du téléphone, audio voiture / téléphone. |

Seuls `phone/PhoneBackends.kt`, le moteur et le manifeste diffèrent entre branches : l'interface reste commune
(`phone/PhoneModel.kt`).

## Architecture

```
org.librehu.dialer
├── MainActivity.kt        intents (tel:, DIAL, onglet, écran d'appel), autorisations, appli par défaut
├── CallBubble.kt          commandes flottantes (fenêtre superposée)
├── phone/                 PhoneBackend (interface), moteur de la branche, Phone (instance partagée)
├── data/PhoneBook.kt      contacts, journal, favoris, recherche de nom (fournisseurs Android)
├── data/T9.kt            recherche T9 (nom → chiffres, score début de nom / de mot / initiales)
├── ui/                    écrans Compose (onglets, appel, réglages), thème partagé avec le launcher
├── DialerWidgets.kt       widgets raccourcis et derniers appels
├── DialerKeypadWidget.kt  widget clavier (numéro par widget, appel via MainActivity)
└── ThemeFollower.kt       thème du launcher (content://org.librehu.launcher.theme/theme + THEME_CHANGED)
```

Contacts et journal : sur l'autoradio, le client PBAP d'Android télécharge le répertoire et l'historique du
téléphone dans les fournisseurs `ContactsContract` / `CallLog` (ce que relisent aussi `ivi-btservice` et
LibreHU-service) : l'appli les lit directement.

Règles :

- Ne jamais ouvrir l'UART de la MCU (`/dev/ttyS1`) : LibreHU-service en est le seul propriétaire.
- Afficher honnêtement les états « non connecté » / « indisponible » ; ne jamais simuler un appel.

## Compilation

JDK 17, SDK Android API 37. GitHub Actions : `gradle assembleRelease`, artefact `LibreHU-Dialer` et release `latest-<branche>` (installable avec
[LibreHU Apps (OTA)](https://github.com/LibreHU/LibreHU-OTA-app)).

Non testé sur l'autoradio à ce stade.

## Optimisation (R8)

L'APK publié par la CI (`assembleRelease`) est **optimisé par R8** : code et ressources réduits et optimisés, ce qui
rend Jetpack Compose nettement plus fluide ([Compose performance](https://developer.android.com/develop/ui/compose/performance)),
**sans renommage** (`-dontobfuscate` dans `app/proguard-rules.pro`) : les traces des journaux restent lisibles. Les
règles gardent aussi ce qui n'est appelé que par son nom (méthodes natives, helpers root lancés par `app_process`). Si
une fonction plante avec « class / method not found », regarder `usage.txt` dans l'artefact *R8 reports* de la CI (ce
que R8 a retiré) et ajouter une règle `-keep`.

**Style rétro et police de LibreHU Launcher** : quand le launcher est en style rétro (les couleurs d'un afficheur
d'autoradio tirées de sa couleur d'accent, couleur RGB comprise), cette appli prend sa palette (jour ou nuit) ; quand
la **police d'afficheur à segments** y est choisie (dans tous les styles), cette appli la prend aussi, en majuscules
sans accents (« LibreHU Seg14 », tirée de DSEG14 © keshikan, SIL Open Font License 1.1, voir
`third_party/DSEG-LICENSE.txt`). Le launcher publie le style, la police et la palette par
`content://org.librehu.launcher.theme/theme` (colonnes `style`, `font`, `background`, `surface`, `surface_high`,
`on_accent`, `text`, `text_dim`) et par la diffusion `org.librehu.action.THEME_CHANGED` (mêmes noms en extras).
