package de.fynn.webserver;

import de.fynn.webserver.analytics.AnalyticsService;
import de.fynn.webserver.commands.WebServerCommand;
import de.fynn.webserver.debug.DebugService;
import de.fynn.webserver.gui.StatsGuiBuilder;
import de.fynn.webserver.gui.StatsGuiListener;
import de.fynn.webserver.leaderboard.KillGuiListener;
import de.fynn.webserver.leaderboard.KillStatsService;
import de.fynn.webserver.server.HttpWebServer;
import de.fynn.webserver.stats.RuntimeStatsService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class WebServerPlugin extends JavaPlugin {

    private HttpWebServer webServer;
    private AnalyticsService analyticsService;
    private DebugService debugService;
    private StatsGuiBuilder statsGuiBuilder;
    private StatsGuiListener statsGuiListener;
    private RuntimeStatsService runtimeStatsService;
    private KillStatsService killStatsService;
    private KillGuiListener killGuiListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        analyticsService = new AnalyticsService(getConfig().getInt("analytics.recent-requests-limit", 50));
        debugService = new DebugService(getConfig().getBoolean("debug-log", false), 100);
        statsGuiBuilder = new StatsGuiBuilder();
        statsGuiListener = new StatsGuiListener(this);
        runtimeStatsService = new RuntimeStatsService(this, getConfig().getLong("stats.disk-total-gb", 50));
        runtimeStatsService.start();
        killStatsService = new KillStatsService(getConfig().getBoolean("leaderboard.track-worlds", true));
        killGuiListener = new KillGuiListener(this);

        File webRoot = new File(getDataFolder(), getConfig().getString("web-root", "html"));
        if (!webRoot.exists()) {
            webRoot.mkdirs();
            getLogger().info("┌─────────────────────────────────────────────────────");
            getLogger().info("│ Web-Root erstellt: " + webRoot.getAbsolutePath());
            getLogger().info("│ → Lege deine Website-Dateien (index.html, CSS, JS)");
            getLogger().info("│   in diesen Ordner und führe /ws reload aus.");
            getLogger().info("└─────────────────────────────────────────────────────");
        }

        startWebServer();

        WebServerCommand command = new WebServerCommand(this);
        getCommand("webserver").setExecutor(command);
        getCommand("webserver").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(statsGuiListener, this);
        getServer().getPluginManager().registerEvents(killStatsService, this);
        getServer().getPluginManager().registerEvents(killGuiListener, this);

        int port = getConfig().getInt("port", 8080);
        boolean apiKeySet = !getConfig().getString("security.api-key", "").isBlank();
        getLogger().info("┌─────────────────────────────────────────────────────");
        getLogger().info("│ Serverhost gestartet auf Port " + port);
        getLogger().info("│ Website:  http://<server-ip>:" + port + "/");
        getLogger().info("│ Stats-API: GET /api/stats  (X-API-Key Header)");
        if (!apiKeySet) {
            getLogger().warning("│ ⚠ Kein API-Key gesetzt! Setze security.api-key in der config.yml");
        }
        getLogger().info("│ Hilfe: /ws  oder  /webserver");
        getLogger().info("└─────────────────────────────────────────────────────");
    }

    @Override
    public void onDisable() {
        if (runtimeStatsService != null) {
            runtimeStatsService.stop();
        }
        stopWebServer();
    }

    public void startWebServer() {
        int port = getConfig().getInt("port", 8080);
        if (port < 1 || port > 65535) {
            getLogger().severe("Ungültiger Port: " + port + " (erlaubt: 1-65535). Verwende 8080.");
            port = 8080;
        }
        String webRootPath = new File(getDataFolder(), getConfig().getString("web-root", "html")).getAbsolutePath();
        String indexFile = getConfig().getString("index-file", "index.html");
        boolean accessLog = getConfig().getBoolean("access-log", false);
        int accessLogMaxEntries = getConfig().getInt("access-log-max-entries", 120);
        int rateLimitPerSecond = getConfig().getInt("security.rate-limit.requests-per-second", 30);
        if (rateLimitPerSecond < 1) {
            getLogger().warning("rate-limit.requests-per-second muss >= 1 sein. Verwende 30.");
            rateLimitPerSecond = 30;
        }
        int rateLimitBurst = getConfig().getInt("security.rate-limit.burst", 60);
        if (rateLimitBurst < rateLimitPerSecond) {
            getLogger().warning("rate-limit.burst muss >= requests-per-second sein. Setze auf " + rateLimitPerSecond + ".");
            rateLimitBurst = rateLimitPerSecond;
        }
        int fileCacheLimit = getConfig().getInt("performance.file-cache-max-entries", 200);
        int cacheMaxAgeSeconds = getConfig().getInt("performance.cache-max-age-seconds", 3600);
        boolean gzipEnabled = getConfig().getBoolean("performance.gzip-enabled", true);
        String corsAllowedOrigins = getConfig().getString("cors.allowed-origins", "*");
        String apiKey = getConfig().getString("security.api-key", "");
        boolean protectStats = getConfig().getBoolean("security.protect-stats-with-api-key", true);
        boolean protectAnalytics = getConfig().getBoolean("security.protect-analytics-with-api-key", false);
        boolean enableDeployWebhook = getConfig().getBoolean("deploy.webhook-enabled", false);
        boolean sslEnabled = getConfig().getBoolean("ssl.enabled", false);
        String sslKeystorePathRaw = getConfig().getString("ssl.keystore-path", "");
        String sslKeystorePassword = getConfig().getString("ssl.keystore-password", "");
        String sslKeyPassword = getConfig().getString("ssl.key-password", "");
        String sslKeystoreType = getConfig().getString("ssl.keystore-type", "PKCS12");
        String sslKeystorePath = "";
        if (sslKeystorePathRaw != null && !sslKeystorePathRaw.isBlank()) {
            File ksFile = new File(sslKeystorePathRaw);
            if (!ksFile.isAbsolute()) {
                ksFile = new File(getDataFolder(), sslKeystorePathRaw);
            }
            sslKeystorePath = ksFile.getAbsolutePath();
        }

        webServer = new HttpWebServer(
                this,
                port,
                webRootPath,
                indexFile,
                accessLog,
                analyticsService,
                debugService,
                rateLimitPerSecond,
                rateLimitBurst,
                fileCacheLimit,
                apiKey,
                protectStats,
                protectAnalytics,
                enableDeployWebhook,
                sslEnabled,
                sslKeystorePath,
                sslKeystorePassword,
                sslKeyPassword,
                sslKeystoreType,
                cacheMaxAgeSeconds,
                gzipEnabled,
                corsAllowedOrigins,
                accessLogMaxEntries
        );
        webServer.start();
    }

    public void stopWebServer() {
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
    }

    public static class RestartResult {
        public boolean gitSuccess = true;
        public boolean serverStarted = false;
        public String gitError = null;
    }

    public RestartResult restartWebServer() {
        RestartResult result = new RestartResult();
        stopWebServer();
        reloadConfig();
        debugService.setDebugEnabled(getConfig().getBoolean("debug-log", false));
        runtimeStatsService.setDiskTotalGb(getConfig().getLong("stats.disk-total-gb", 50));
        killStatsService.setTrackWorlds(getConfig().getBoolean("leaderboard.track-worlds", true));

        String gitRepo = getConfig().getString("git-repo", "");
        if (gitRepo != null && !gitRepo.isEmpty()) {
            result.gitSuccess = gitPull();
            if (!result.gitSuccess) {
                result.gitError = "Git-Pull fehlgeschlagen";
            }
        }

        startWebServer();
        result.serverStarted = webServer != null && webServer.isRunning();
        return result;
    }

    /**
     * Führt git pull im web-root Verzeichnis aus.
     * Wenn das Verzeichnis noch kein Git-Repo ist, wird es geclont.
     */
    public boolean gitPull() {
        String gitRepo = getConfig().getString("git-repo", "");
        if (gitRepo == null || gitRepo.isEmpty()) {
            return false;
        }

        File webRoot = new File(getDataFolder(), getConfig().getString("web-root", "html"));
        File gitDir = new File(webRoot, ".git");

        try {
            if (!gitDir.exists()) {
                // Erstes Mal: Repo klonen
                getLogger().info("Klone Git-Repository: " + gitRepo);
                if (webRoot.exists()) {
                    deleteContents(webRoot);
                }
                String branch = getConfig().getString("git-branch", "");
                if (branch != null && !branch.isEmpty()) {
                    return runGitCommand(getDataFolder(), "git", "clone", "--branch", branch, "--single-branch", gitRepo, webRoot.getAbsolutePath());
                } else {
                    return runGitCommand(getDataFolder(), "git", "clone", gitRepo, webRoot.getAbsolutePath());
                }
            } else {
                // Repo existiert bereits: pull
                getLogger().info("Führe git pull aus...");
                return runGitCommand(webRoot, "git", "pull");
            }
        } catch (Exception e) {
            getLogger().severe("Git-Fehler: " + e.getMessage());
            return false;
        }
    }

    private boolean runGitCommand(File workDir, String... command) {
        try {
            int timeoutSeconds = getConfig().getInt("git-timeout-seconds", 30);
            if (timeoutSeconds < 5) timeoutSeconds = 5;

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            // Verhindert interaktive Prompts (z.B. Username/Passwort)
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            Process process = pb.start();
            // stdin schließen, damit git nicht auf Eingabe wartet
            process.getOutputStream().close();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    getLogger().info("[Git] " + line);
                }
            }

            if (!finished) {
                process.destroyForcibly();
                getLogger().warning("Git-Befehl abgebrochen (Timeout nach " + timeoutSeconds + "s)");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                getLogger().warning("Git-Befehl fehlgeschlagen (Exit-Code: " + exitCode + ")");
                return false;
            }
            return true;
        } catch (Exception e) {
            getLogger().severe("Git-Befehl konnte nicht ausgeführt werden: " + e.getMessage());
            return false;
        }
    }

    private void deleteContents(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteContents(file);
                }
                if (!file.delete()) {
                    getLogger().warning("Konnte nicht gelöscht werden: " + file.getAbsolutePath());
                }
            }
        }
    }

    public HttpWebServer getWebServer() {
        return webServer;
    }

    public AnalyticsService getAnalyticsService() {
        return analyticsService;
    }

    public DebugService getDebugService() {
        return debugService;
    }

    public StatsGuiBuilder getStatsGuiBuilder() {
        return statsGuiBuilder;
    }

    public RuntimeStatsService getRuntimeStatsService() {
        return runtimeStatsService;
    }

    public StatsGuiListener getStatsGuiListener() {
        return statsGuiListener;
    }

    public KillStatsService getKillStatsService() {
        return killStatsService;
    }

    public KillGuiListener getKillGuiListener() {
        return killGuiListener;
    }
}
