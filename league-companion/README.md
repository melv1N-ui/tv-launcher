# Rift Remote – League of Legends vom Handy steuern

Ein kleines Programm für den Gaming-PC. Es spricht mit dem League Client und stellt eine Handy-Oberfläche ins WLAN.
Damit kannst du vom Sofa (oder vom Balkon) aus:

- **Queue starten und abbrechen** und den Spielmodus wählen (Ranked Solo/Duo, Flex, Normal Draft, Swiftplay, ARAM)
- **Match annehmen oder ablehnen** – mit Countdown-Ring, Vibration und Ton; optional **Auto-Annehmen**
- **Champ Select komplett steuern:** Wunsch-Champion in der Planungsphase, **bannen**, **wählen und festlegen**,
  Champion-Suche, Teams und Banns im Blick, Timer
- **Beschwörerzauber und Runenseite** in der Vorbereitungsphase wechseln
- nach dem Spiel **„Nochmal spielen“**

## Voraussetzungen

- Windows-PC (macOS geht auch) mit League of Legends
- [Node.js](https://nodejs.org) ab Version 18 (die LTS-Version installieren). Weitere Pakete braucht es nicht.
- Handy und PC im selben WLAN

## Starten

1. Den Ordner `league-companion` auf den PC kopieren (oder das Repo klonen).
2. **`start.bat`** doppelklicken (oder im Terminal `node server.js`).
3. Fragt die Windows-Firewall nach: **„Private Netzwerke“ zulassen**.
4. Im Browser öffnet sich die Koppel-Seite mit einem **QR-Code** → mit der Handy-Kamera scannen.
5. Auf dem Handy **„Zum Home-Bildschirm“** wählen – dann startet es wie eine App.

Das Programm muss laufen, solange du es benutzen willst. League kann vorher oder nachher gestartet werden;
die Verbindung kommt automatisch.

### Ohne League ausprobieren

```bash
node server.js --demo
```

Spielt einen kompletten Ablauf mit Bots nach: Lobby → Suche → Match gefunden → Planung → Banns → Picks → Spiel.

## Einstellungen (`config.json`)

Wird beim ersten Start angelegt.

| Feld | Bedeutung |
|---|---|
| `port` | Port der Weboberfläche (Standard `8765`) |
| `token` | Geheimer Schlüssel für die Kopplung. Löschen → neuer Schlüssel, alle Handys müssen neu koppeln |
| `autoAccept` | Match automatisch annehmen (auch per Schalter in der App) |
| `leaguePath` | Nur nötig, wenn League nicht unter `C:\Riot Games\League of Legends` liegt und nicht gefunden wird |
| `ntfyTopic` | Optional: echte Push-Nachricht aufs **gesperrte** Handy über [ntfy](https://ntfy.sh). App installieren, ein zufälliges Thema abonnieren (z. B. `rift-8f3k2x`) und hier eintragen |

## Gut zu wissen

- **Gesperrtes Handy:** Browser pausieren im Hintergrund. Für „Match gefunden“ bei gesperrtem Bildschirm `ntfyTopic` nutzen
  oder Auto-Annehmen einschalten.
- **Unterwegs (anderes Netz):** geht z. B. mit [Tailscale](https://tailscale.com) auf PC und Handy – dann die Tailscale-IP
  (`100.x.x.x`) auf der Koppel-Seite auswählen.
- **Sicherheit:** Nur Geräte mit dem Schlüssel aus dem QR-Code können steuern. Die Koppel-Seite selbst ist nur am PC erreichbar.
- **Riot-Regeln:** Das Programm nutzt die lokale Client-Schnittstelle (LCU), die auch bekannte Tools wie Porofessor oder Blitz
  verwenden. Es greift nicht ins laufende Spiel ein. Die Schnittstelle ist aber nicht offiziell dokumentiert;
  nach Client-Updates kann einzelnes kurz nicht funktionieren.

## Aufbau

| Datei | Inhalt |
|---|---|
| `server.js` | Webserver, Live-Updates (Server-Sent Events), Aktionen, Kopplung |
| `lcu.js` | Findet den Client (lockfile / Prozess) und spricht mit ihm |
| `demo.js` | Nachgebauter Client für `--demo` |
| `public/` | Handy-Oberfläche (`index.html`, `app.js`, `style.css`) und Koppel-Seite |
| `public/qrcode.js` | QR-Code-Erzeugung ([qrcode-generator](https://github.com/kazuhikoarase/qrcode-generator), MIT) |
