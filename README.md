# LibreHU Dialer

Application téléphone pour autoradios LibreHU (UJC201 / AC8257, Android 9), pensée pour la voiture : grandes
cibles tactiles, paysage, thème et accent synchronisés avec [LibreHU Launcher](https://github.com/LibreHU/LibreHU-Launcher-App).
Inspirée de l'ergonomie d'Android Auto, sans en être un client ni reprendre ses éléments graphiques.

Cette branche : **`ivi`** (service Bluetooth Jancar de la ROM d'origine).

## Fonctions

- **Favoris** : contacts favoris du téléphone (sinon les numéros les plus appelés) et derniers appels.
- **Récents** : journal d'appels (reçus, émis, manqués en rouge, refusés), regroupement des appels consécutifs,
  filtre « Manqués ».
- **Contacts** : répertoire avec photos, index alphabétique, recherche par nom ou numéro, choix du numéro quand un
  contact en a plusieurs.
- **Clavier** : grandes touches, appui long sur 0 pour « + », appui long sur effacer pour tout effacer, suggestions
  de contacts pendant la saisie ; ouvre aussi les liens `tel:` et l'action `DIAL` des autres applis.
- **Écran d'appel** : appel entrant (répondre / refuser, double appel), durée, micro coupé, clavier DTMF, mise en
  attente, permutation entre deux appels, raccrocher ; réductible en barre au-dessus des listes.
- **Commandes flottantes** pendant un appel, par-dessus les autres applis (navigation…) : nom, durée, micro,
  raccrocher, déplaçables ; un toucher rouvre l'écran d'appel (autorisation « affichage par-dessus »).
- **Widgets** : raccourcis et derniers appels, thème automatique.
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
├── ui/                    écrans Compose (onglets, appel, réglages), thème partagé avec le launcher
├── DialerWidgets.kt       widgets
└── ThemeFollower.kt       thème du launcher (content://org.librehu.launcher.theme/theme + THEME_CHANGED)
```

Contacts et journal : sur l'autoradio, le client PBAP d'Android télécharge le répertoire et l'historique du
téléphone dans les fournisseurs `ContactsContract` / `CallLog` (ce que relisent aussi `ivi-btservice` et
LibreHU-service) : l'appli les lit directement.

Règles :

- Ne jamais ouvrir l'UART de la MCU (`/dev/ttyS1`) : LibreHU-service en est le seul propriétaire.
- Afficher honnêtement les états « non connecté » / « indisponible » ; ne jamais simuler un appel.

## Compilation

JDK 17, SDK Android API 37. GitHub Actions : `gradle assembleDebug`, artefact `LibreHU-Dialer-debug`.

Non testé sur l'autoradio à ce stade.
