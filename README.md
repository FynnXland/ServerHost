# ServerHost

A lightweight HTTP web server plugin for Paper/Spigot Minecraft servers. Serve static websites (HTML, CSS, JS, images) directly from your Minecraft server — with optional Git integration for automatic deployments.

## Features

- 🌐 **Static file hosting** — Serve HTML, CSS, JS, images, fonts and more
- 📁 **SPA support** — Single Page Application fallback routing (serves `index.html` for unknown routes)
- 🔄 **Git integration** — Automatically clone/pull a Git repository on reload
- 🌿 **Branch support** — Clone a specific branch (e.g. a build output branch)
- 🔒 **Path traversal protection** — Prevents directory traversal attacks
- 📝 **Access logging** — Optional request logging to console
- ⚡ **Multi-threaded** — 4-thread executor pool for handling requests

## Requirements

- Paper 1.21+ (or compatible forks)
- Java 21+
- Git installed on the server (only if using Git integration)

## Installation

1. Download `WebServer-x.x.x.jar` and place it in your `plugins/` folder
2. Start/restart the server
3. Edit `plugins/WebServer/config.yml` to your needs
4. Place your static files in `plugins/WebServer/html/` (or configure a different path)
5. Run `/webserver reload` or restart the server

## Configuration

```yaml
# Port the HTTP server listens on
port: 8080

# Directory with static files (relative to plugin folder)
web-root: "html"

# Default file when a directory is requested
index-file: "index.html"

# Log requests to console?
access-log: false

# Git repository URL (optional)
# Supports HTTPS with Personal Access Token:
# https://<TOKEN>@github.com/user/repo.git
git-repo: ""

# Git branch (optional)
# Only clone this specific branch
git-branch: ""
```

### Git Integration

The plugin can automatically clone and pull a Git repository. This is useful for deploying a static website from GitHub.

**Setup:**

1. Create a [GitHub Personal Access Token](https://github.com/settings/tokens?type=beta) with **Contents: Read and write** permission for your repository
2. Set `git-repo` in `config.yml`:
   ```yaml
   git-repo: "https://<YOUR_TOKEN>@github.com/user/repo.git"
   ```
3. Run `/webserver reload` — the repo will be cloned into the web-root directory

On subsequent reloads, the plugin will `git pull` instead of cloning.

**Branch support:**

If your built files are on a separate branch (e.g. from a CI/CD pipeline), set:
```yaml
git-branch: "dist-deploy"
```

The plugin will clone only that branch using `--single-branch`.

### Using with a React/Vite project

If your website is built with React, Vite, or another build tool, you need to deploy the **built output** (e.g. the `dist/` folder), not the source code. The server cannot run Node.js.

**Recommended setup with GitHub Actions:**

1. Create `.github/workflows/build.yml` in your repo:
   ```yaml
   name: Build and Deploy

   on:
     push:
       branches: [main]

   jobs:
     build:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v4

         - uses: actions/setup-node@v4
           with:
             node-version: 20

         - name: Install dependencies
           run: npm ci

         - name: Build
           run: npm run build

         - name: Deploy to dist branch
           uses: peaceiris/actions-gh-pages@v4
           with:
             github_token: ${{ secrets.GITHUB_TOKEN }}
             publish_dir: ./dist
             publish_branch: dist-deploy
             force_orphan: true
   ```
2. In your repo settings: **Settings → Actions → General → Workflow permissions → Read and write**
3. In `config.yml` on the server:
   ```yaml
   git-repo: "https://<TOKEN>@github.com/user/repo.git"
   git-branch: "dist-deploy"
   ```
4. Run `/webserver reload` after each push

## Commands

| Command | Description |
|---------|-------------|
| `/webserver status` | Show server status (online/offline, port) |
| `/webserver reload` | Reload config, git pull, restart server |
| `/webserver start` | Start the web server |
| `/webserver stop` | Stop the web server |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `webserver.admin` | Access to all /webserver commands | OP |

## Supported MIME Types

The plugin automatically detects the following file types:

| Extension | MIME Type |
|-----------|-----------|
| `.html`, `.htm` | `text/html` |
| `.css` | `text/css` |
| `.js`, `.mjs`, `.jsx`, `.ts`, `.tsx` | `application/javascript` |
| `.json` | `application/json` |
| `.png` | `image/png` |
| `.jpg`, `.jpeg` | `image/jpeg` |
| `.gif` | `image/gif` |
| `.svg` | `image/svg+xml` |
| `.ico` | `image/x-icon` |
| `.webp` | `image/webp` |
| `.woff`, `.woff2` | `font/woff`, `font/woff2` |
| `.ttf` | `font/ttf` |
| `.xml` | `application/xml` |
| `.txt` | `text/plain` |
| `.webmanifest` | `application/manifest+json` |

Unknown file extensions are served as `application/octet-stream`.

## Troubleshooting

### White page / MIME type errors
- Make sure you're serving **built** files (from `dist/`), not source code
- If using React/Vite, set up the GitHub Actions pipeline as described above

### Git "Write access not granted"
- Check that your Personal Access Token has **Contents: Read and write** permission
- Make sure the token has access to the correct repository

### Git hangs on reload
- The plugin sets a 30-second timeout for Git commands
- Interactive prompts (username/password) are disabled automatically
- Make sure your `git-repo` URL includes the access token

### Port already in use
- Change the `port` in `config.yml` to an available port
- Make sure the port is open in your server's firewall/panel

## License

MIT
