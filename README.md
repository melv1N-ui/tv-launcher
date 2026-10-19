# TV Launcher

Eigener Startbildschirm für Android TV (gebaut für den Philips 55PUS8106, Android 11) im Stil von
Apple TV 4K: pechschwarz, Dock unten, Apps im Raster darunter, ein Glas-Highlight, das von Kachel zu
Kachel federt. Java, AndroidX RecyclerView, keine weiteren Bibliotheken außer DynamicAnimation für die
Federn. Kein Blur, kein Video, kein Netzwerk-Polling.

## Aufbau

- **Oben links:** Begrüßung, große dünne Uhr, Datum (Schrift: [Outfit](https://github.com/Outfitio/Outfit-Fonts), SIL Open Font License, liegt in `app/src/main/assets/fonts`)
- **Oben rechts:** Glas-Pillen für Eingänge, WLAN und Einstellungen
- **Eingänge:** Dropdown mit HDMI-Anschlüssen (inkl. Namen per CEC), AV falls vorhanden, Live TV;
  umgeschaltet wird über das TV Input Framework (`TvContract.buildChannelUriForPassthroughInput`)
- **Über dem Dock:** Name der fokussierten Dock-App mit farbigem Leuchten aus ihrem Banner
- **Dock:** vier feste Apps in einer helleren, abgerundeten Leiste
- **Raster:** alle übrigen TV-Apps (`LEANBACK_LAUNCHER`) in 4 Spalten, scrollt bei Runter nach oben

## Steuerung

Den Fokus verwaltet `MainActivity` selbst (Bereich + Index) statt Androids Fokus-Suche. Dadurch:

- Hoch/Runter wechselt immer genau eine Zeile, die Spalte bleibt erhalten
- Hoch aus dem Dock geht in die Pillen-Leiste; an deren Enden passiert nichts
- Zurück bringt aus Raster oder Pillen ins Dock
- Nach dem Zurückkommen aus einer App ist die zuletzt gewählte Kachel wieder fokussiert;
  Home im Launcher selbst springt ins Dock

## Dock ändern

Standard steht in `AppRepository.DEFAULT_DOCK`. Ohne neuen Build: eine Datei `dock.txt`
(ein Paketname pro Zeile) nach `/sdcard/Android/data/de.melvin.tvlauncher/files/dock.txt` legen.

## Hintergrundbild

Standard ist Schwarz. `wallpaper.jpg` oder `wallpaper.png` nach
`/sdcard/Android/data/de.melvin.tvlauncher/files/` legen; es wird gedimmt angezeigt.

```bash
adb push mein-bild.jpg /sdcard/Android/data/de.melvin.tvlauncher/files/wallpaper.jpg
```

## APK bekommen

Jeder Push auf `main` baut per GitHub Actions eine neue APK und hängt sie als Release an:

```
https://github.com/melv1N-ui/tv-launcher/releases/latest/download/tv-launcher.apk
```

## Installieren

`adb install` kann auf manchen Fernsehern an einer Bestätigung hängen bleiben. Sicherer:

```bash
adb connect <TV-IP>:5555
adb push tv-launcher.apk /data/local/tmp/tv-launcher.apk
adb shell pm install -r /data/local/tmp/tv-launcher.apk
adb shell cmd package set-home-activity de.melvin.tvlauncher/.MainActivity
```

Android TV fragt bei der Home-Taste nicht nach einem Launcher, und der Google-Launcher meldet sich mit
höherer Priorität an. Deshalb muss er abgeschaltet werden (rückgängig mit `pm enable`):

```bash
adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

## Lokal bauen

Braucht JDK 17 und das Android SDK (Platform 34). Gradle kommt über den Wrapper:

```bash
./gradlew :app:assembleDebug      # oder :app:assembleRelease
```

Der Signaturschlüssel `app/tvlauncher.jks` liegt bewusst im Repo, damit jede neue APK ein Update der
alten ist. Der Versionscode zählt die Minuten seit 2025, damit lokale und GitHub-Builds einander
updaten können. Für eine private Sideload-App reicht das.
