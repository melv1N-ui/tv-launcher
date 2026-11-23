# TV Launcher

Eigener Startbildschirm für Android TV (gebaut für den Philips 55PUS8106) im Stil von Apple TV:
Sternenhimmel mit Nebel und Planet, Glas-Kacheln mit den echten App-Icons, Steuerung per Fernbedienung.

## Reihen

- **Gaming & Streaming:** Moonlight, Netflix, Disney+, Prime Video, SmartTube
- **Mediathek:** Jellyfin, Plex, Kodi, Stremio
- **Alle Apps:** alles andere, was auf dem Fernseher installiert ist

Nicht installierte Apps erscheinen abgedunkelt; OK öffnet sie im Play Store.
Die feste Auswahl steht in `app/src/main/java/de/melvin/tvlauncher/AppRepository.kt` (`Catalog`).

## APK bekommen

Jeder Push auf `main` baut per GitHub Actions eine neue APK und hängt sie als Release an.
Die neueste liegt immer hier:

```
https://github.com/melv1N-ui/tv-launcher/releases/latest/download/tv-launcher.apk
```

## Installieren

```bash
wget -O tv-launcher.apk https://github.com/melv1N-ui/tv-launcher/releases/latest/download/tv-launcher.apk
adb connect <TV-IP>:5555
adb install -r tv-launcher.apk
```

## Als Startbildschirm setzen

Android TV fragt bei der Home-Taste nicht nach einem Launcher. Zwei Wege:

**A) Philips-Launcher abschalten** (lässt sich rückgängig machen):

```bash
adb shell pm list packages | grep -i launcher
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Zurück: `adb shell pm enable com.google.android.tvlauncher`

**B) Projectivy Launcher als Weiche:** In Projectivy die Override-Funktion aktivieren und dort
„TV Launcher“ als Ziel der Home-Taste wählen.

## Lokal bauen

Braucht JDK 17, Android SDK und Gradle 8.9:

```bash
gradle :app:assembleRelease
```

Der Signaturschlüssel `app/tvlauncher.jks` liegt bewusst im Repo, damit jede neue APK ein Update der alten ist.
Für eine private Sideload-App reicht das.

## Weiteres Projekt: Rift Remote

Im Ordner [`league-companion/`](league-companion/README.md) liegt eine Handy-Fernbedienung für League of Legends
(Match annehmen, Champ Select, Queue). Läuft auf dem PC mit Node.js, siehe dortiges README.
