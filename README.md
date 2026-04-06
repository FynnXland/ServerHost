# Serverhost

Ein leichtgewichtiges Minecraft-Plugin (Paper/Spigot), das einen integrierten HTTP-Webserver bereitstellt. Dient statische Websites direkt vom Minecraft-Server aus — inklusive Stats-API mit Spielerdaten, Kill-Leaderboard und optionaler Git-Integration.

## Features

- 🌐 **Statisches File-Hosting** — HTML, CSS, JS, Bilder, Fonts und mehr
- 📱 **SPA-Unterstützung** — Single Page Applications (React, Vue, etc.) mit Index-Fallback
- 📊 **Stats-API** — Live-Serverdaten (Spieler, TPS, RAM, CPU) als JSON für deine Website
- 🏆 **Kill-Leaderboard-API** — Top-Kills als JSON mit UUID für Spielerköpfe
- 🔒 **API-Key-Authentifizierung** — Schütze deine Stats-API mit einem geheimen Key
- 🌍 **CORS-Unterstützung** — Korrekte Browser-Header für Cross-Origin-Anfragen
- 🔄 **Git-Integration** — Automatisch clonen/pullen bei `/ws reload`
- ⚡ **Virtual Threads** — Modernes Java 21 Multithreading ohne Thread-Pool-Limits
- 🗜️ **GZip-Kompression** — Automatisch für alle Text-Dateien
- 📁 **ETag-Caching** — HTTP 304 Not Modified für schnelle Wiederholungsaufrufe
- 🛡️ **Rate-Limiting** — Token-Bucket pro IP gegen Überlastung
- 🔐 **HTTPS/SSL** — Optionaler TLS-Modus mit PKCS12-Keystore

## Anforderungen

- **Paper 1.21+** (oder kompatible Forks)
- **Java 21+**
- **Git** (nur bei Nutzung der Git-Integration)

## Installation

1. `Serverhost-x.x.x.jar` in den `plugins/`-Ordner kopieren
2. Server starten → Plugin legt `plugins/Serverhost/` automatisch an
3. `plugins/Serverhost/config.yml` anpassen (Port, API-Key etc.)
4. Website-Dateien in `plugins/Serverhost/html/` ablegen
5. `/ws reload` ausführen

Die Website ist danach erreichbar unter `http://<server-ip>:<port>/`

---

## Konfiguration

Die wichtigsten Einstellungen in `plugins/Serverhost/config.yml`:

```yaml
# Port des Webservers (muss in der Firewall freigegeben sein)
port: 40020

# Ordner mit deinen Website-Dateien
web-root: "html"

security:
  # API-Key für /api/stats (im Header: X-API-Key: <dein-key>)
  api-key: "dein-geheimer-key-hier"
  protect-stats-with-api-key: true

cors:
  # Alle Domains erlauben (für öffentliche Websites empfohlen)
  allowed-origins: "*"
```

Nach jeder Änderung: `/ws reload`

---

## API-Endpunkte

Alle Endpunkte geben `application/json` zurück.

### `GET /api/stats`
Vollständige Server-Statistiken inkl. Online-Spieler mit UUID.

**Header:** `X-API-Key: <api-key>` (wenn `protect-stats-with-api-key: true`)

**Beispiel-Response:**
```json
{
  "server": {
    "version": "Paper 1.21.4",
    "onlinePlayers": 3,
    "maxPlayers": 100,
    "tps": "19.95",
    "players": [
      { "name": "Steve", "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5" }
    ]
  },
  "runtime": {
    "cpuPercent": "12.30",
    "ramMb": 1024,
    "maxMemoryMb": 4096
  }
}
```

### `GET /api/stats/analytics`
Website-Traffic-Daten (Requests, Fehler, Antwortzeiten). Optional per API-Key schützbar.

### `GET /api/leaderboard`
Kill-Leaderboard (Top 3 Spieler).

```json
{
  "leaderboard": [
    { "rank": 1, "name": "Steve", "uuid": "069a79f4-...", "kills": 42 }
  ]
}
```

### `POST /api/deploy`
Löst automatisch git pull + Webserver-Neustart aus.
Erfordert `deploy.webhook-enabled: true` und `X-API-Key` Header.

---

## Befehle

Alias: `/ws` (oder `/webserver`)  
Berechtigung: `webserver.admin` (Standard: OP)

| Befehl | Beschreibung |
|--------|-------------|
| `/ws` | Alle Befehle anzeigen |
| `/ws status` | Online-Status, Port & URL |
| `/ws info` | Spieler, TPS, RAM, CPU, Traffic, Top-Pfade |
| `/ws reload` | Config neu laden, git pull, Server neustarten |
| `/ws start` / `stop` | Webserver manuell starten/stoppen |
| `/ws gui` | Interaktives Dashboard (nur für Spieler) |
| `/ws log` | Letzte Web-Zugriffe |
| `/ws reset` | Traffic-Daten zurücksetzen |
| `/ws debug <on\|off\|errors>` | Debug-Logging steuern |
| `/ws kills [list\|info\|set\|add\|remove\|reset]` | Kill-Leaderboard verwalten |

---

## Git-Integration

Das Plugin kann eine Website automatisch aus einem Git-Repository beziehen.

### Einrichtung

1. [GitHub Personal Access Token](https://github.com/settings/tokens?type=beta) erstellen (Berechtigung: **Contents: Read**)
2. In `config.yml`:
   ```yaml
   git-repo: "https://<TOKEN>@github.com/user/repo.git"
   git-branch: "dist-deploy"   # optional: nur diesen Branch
   ```
3. `/ws reload` → klont das Repo beim ersten Mal, danach `git pull`

### Mit React/Vite/Next.js

Da der Plugin-Server kein Node.js ausführen kann, muss der **gebaute Output** (z.B. `dist/`) deployt werden.

**Empfohlener Setup mit GitHub Actions** (`.github/workflows/deploy.yml`):

```yaml
name: Deploy to Minecraft Server

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: 20

      - run: npm ci
      - run: npm run build

      - name: Deploy to dist branch
        uses: peaceiris/actions-gh-pages@v4
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: ./dist
          publish_branch: dist-deploy
          force_orphan: true
```

> **Einmalig:** In den Repo-Einstellungen unter **Settings → Actions → General → Workflow permissions** auf **Read and write** setzen.

Danach in `config.yml`:
```yaml
git-repo: "https://<TOKEN>@github.com/user/repo.git"
git-branch: "dist-deploy"
```

Nach jedem Push auf `main` → `/ws reload` ausführen (oder mit `deploy.webhook-enabled: true` automatisieren).

---

## Spielerköpfe in der Website

Die UUID aus `/api/stats` kann genutzt werden, um 3D-Spielerköpfe zu laden:

```html
<!-- Skinrender via mc-heads.net -->
<img src="https://mc-heads.net/avatar/<UUID>/64" alt="Spielerkopf">

<!-- Oder als 3D-Head -->
<img src="https://mc-heads.net/head/<UUID>/64" alt="Spielerkopf">
```

---

## Troubleshooting

### 401 Unauthorized bei `/api/stats`
- Prüfe ob das Frontend den Header `X-API-Key: <key>` mitsendet
- Prüfe ob `security.api-key` in der `config.yml` korrekt gesetzt ist
- Nach Änderungen: `/ws reload`

### CORS-Fehler im Browser
- Stelle sicher dass `cors.allowed-origins: "*"` gesetzt ist
- OPTIONS-Preflight wird automatisch beantwortet
- Browser-Konsole: Prüfe welche Domain blockiert wird

### Website zeigt leere Seite / MIME-Fehler
- Stelle sicher dass du **gebaute** Dateien (`dist/`) deployest, nicht den Source-Code
- `index.html` muss im `web-root`-Ordner liegen
- Nach Upload: `/ws reload` (leert den Datei-Cache)

### Git "Write access not granted"
- Personal Access Token braucht nur **Contents: Read**-Berechtigung
- Token muss zum richtigen Repository gehören
- Teste die URL manuell mit `git clone <url>`

### Port bereits belegt
- Anderen Port in `config.yml` wählen
- Port in der Server-Firewall / Pterodactyl freigeben
- Mit `/ws status` den aktuellen Port prüfen

---

## Berechtigungen

| Permission | Beschreibung | Standard |
|------------|-------------|---------|
| `webserver.admin` | Zugriff auf alle `/ws`-Befehle | OP |

---

## Unterstützte Dateitypen

Das Plugin erkennt automatisch: `.html`, `.css`, `.js`, `.ts`, `.json`, `.png`, `.jpg`, `.gif`, `.svg`, `.ico`, `.webp`, `.woff`, `.woff2`, `.ttf`, `.xml`, `.txt`, `.webmanifest`, `.map`

Unbekannte Endungen werden als `application/octet-stream` ausgeliefert.

---

## Lizenz

MIT

