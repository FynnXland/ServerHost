package de.fynn.webserver;

import de.fynn.webserver.commands.WebServerCommand;
import de.fynn.webserver.server.HttpWebServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class WebServerPlugin extends JavaPlugin {

    private HttpWebServer webServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        File webRoot = new File(getDataFolder(), getConfig().getString("web-root", "html"));
        if (!webRoot.exists()) {
            webRoot.mkdirs();
            getLogger().info("Web-Root erstellt: " + webRoot.getAbsolutePath());
            getLogger().info("Lege deine statischen Dateien (HTML, CSS, JS) in diesen Ordner!");
        }

        startWebServer();

        getCommand("webserver").setExecutor(new WebServerCommand(this));
        getCommand("webserver").setTabCompleter(new WebServerCommand(this));
    }

    @Override
    public void onDisable() {
        stopWebServer();
    }

    public void startWebServer() {
        int port = getConfig().getInt("port", 8080);
        String webRootPath = new File(getDataFolder(), getConfig().getString("web-root", "html")).getAbsolutePath();
        String indexFile = getConfig().getString("index-file", "index.html");
        boolean accessLog = getConfig().getBoolean("access-log", false);

        webServer = new HttpWebServer(this, port, webRootPath, indexFile, accessLog);
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
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            // Verhindert interaktive Prompts (z.B. Username/Passwort)
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            Process process = pb.start();
            // stdin schließen, damit git nicht auf Eingabe wartet
            process.getOutputStream().close();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    getLogger().info("[Git] " + line);
                }
            }

            if (!finished) {
                process.destroyForcibly();
                getLogger().warning("Git-Befehl abgebrochen (Timeout nach 30s)");
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
                file.delete();
            }
        }
    }

    public HttpWebServer getWebServer() {
        return webServer;
    }
}
