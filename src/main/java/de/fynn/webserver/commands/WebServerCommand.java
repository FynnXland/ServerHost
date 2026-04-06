package de.fynn.webserver.commands;

import de.fynn.webserver.WebServerPlugin;
import de.fynn.webserver.stats.RuntimeStatsSnapshot;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class WebServerCommand implements CommandExecutor, TabCompleter {

    private final WebServerPlugin plugin;

    public WebServerCommand(WebServerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("webserver.admin")) {
            sender.sendMessage(ChatColor.RED + "Keine Berechtigung!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                sender.sendMessage(ChatColor.YELLOW + "Webserver wird neu gestartet...");
                String gitRepo = plugin.getConfig().getString("git-repo", "");
                if (gitRepo != null && !gitRepo.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Git-Pull wird ausgeführt...");
                }
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    WebServerPlugin.RestartResult result = plugin.restartWebServer();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (gitRepo != null && !gitRepo.isEmpty()) {
                            if (result.gitSuccess) {
                                sender.sendMessage(ChatColor.GREEN + "✔ Git-Pull erfolgreich.");
                            } else {
                                sender.sendMessage(ChatColor.RED + "✘ Git-Pull fehlgeschlagen! Prüfe die Konsole.");
                            }
                        }
                        if (result.serverStarted) {
                            sender.sendMessage(ChatColor.GREEN + "✔ Webserver neu gestartet.");
                        } else {
                            sender.sendMessage(ChatColor.RED + "✘ Webserver konnte nicht gestartet werden!");
                        }
                    });
                });
            }
            case "status" -> {
                boolean running = plugin.getWebServer() != null && plugin.getWebServer().isRunning();
                int port = plugin.getConfig().getInt("port", 8080);
                String webRoot = plugin.getConfig().getString("web-root", "html");
                sender.sendMessage(ChatColor.GOLD + "━━━ Status ━━━");
                sender.sendMessage((running ? ChatColor.GREEN + "● Online" : ChatColor.RED + "● Offline")
                        + ChatColor.GRAY + "  Port " + ChatColor.WHITE + port
                        + ChatColor.GRAY + "  Web-Root " + ChatColor.WHITE + webRoot);
                if (running) {
                    sender.sendMessage(ChatColor.GRAY + "URL: " + ChatColor.AQUA + "http://<server-ip>:" + port + "/");
                }
            }
            case "info" -> {
                if (plugin.getWebServer() == null || !plugin.getWebServer().isRunning()) {
                    sender.sendMessage(ChatColor.RED + "Webserver ist offline.");
                    return true;
                }
                RuntimeStatsSnapshot rt = plugin.getRuntimeStatsService().snapshot(plugin.getAnalyticsService().snapshot());
                sender.sendMessage(ChatColor.GOLD + "━━━ Server Info ━━━");
                sender.sendMessage(ChatColor.WHITE + " Spieler " + ChatColor.AQUA + rt.onlinePlayers() + "/" + rt.maxPlayers()
                        + ChatColor.GRAY + " │ " + ChatColor.WHITE + "TPS " + tpsColor(rt.tps()) + String.format("%.1f", rt.tps())
                        + ChatColor.GRAY + " │ " + ChatColor.WHITE + "Version " + ChatColor.AQUA + rt.version());
                sender.sendMessage(ChatColor.WHITE + " CPU " + ChatColor.AQUA + String.format("%.1f%%", rt.cpuPercent())
                        + ChatColor.GRAY + " │ " + ChatColor.WHITE + "RAM " + ChatColor.AQUA + (rt.ramBytes() / (1024 * 1024))
                        + ChatColor.GRAY + "/" + (rt.maxMemoryBytes() / (1024 * 1024)) + " MB"
                        + ChatColor.GRAY + " │ " + ChatColor.WHITE + "Disk " + ChatColor.AQUA + (rt.storageBytes() / (1024 * 1024))
                        + ChatColor.GRAY + "/" + (rt.storageTotalGb() * 1024) + " MB");
                sender.sendMessage(ChatColor.WHITE + " Requests " + ChatColor.AQUA + rt.analytics().totalRequests()
                        + ChatColor.GRAY + " │ " + ChatColor.WHITE + "Fehler " + (rt.analytics().totalErrors() > 0 ? ChatColor.RED : ChatColor.AQUA)
                        + rt.analytics().totalErrors()
                        + ChatColor.GRAY + " │ " + ChatColor.WHITE + "Ø " + ChatColor.AQUA + String.format("%.1f ms", rt.analytics().averageDurationNanos() / 1_000_000.0));
                Map<String, Long> topPaths = rt.analytics().topPaths();
                if (topPaths != null && !topPaths.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    topPaths.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .limit(5)
                            .forEach(e -> {
                                if (!sb.isEmpty()) sb.append(ChatColor.GRAY + ", ");
                                sb.append(ChatColor.WHITE).append(e.getKey())
                                  .append(ChatColor.GRAY).append(" (").append(e.getValue()).append("×)");
                            });
                    sender.sendMessage(ChatColor.WHITE + " Top-Pfade " + sb);
                }
            }
            case "gui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Nur Spieler können die GUI öffnen.");
                } else {
                    plugin.getStatsGuiListener().openDashboard(player);
                }
            }
            case "log" -> {
                if (plugin.getWebServer() == null) {
                    sender.sendMessage(ChatColor.RED + "Webserver ist nicht aktiv.");
                } else {
                    List<String> entries = plugin.getWebServer().getRecentAccessLog(15);
                    sender.sendMessage(ChatColor.GOLD + "━━━ Letzte Zugriffe ━━━");
                    if (entries.isEmpty()) {
                        sender.sendMessage(ChatColor.GRAY + "Noch keine Einträge.");
                    } else {
                        for (String entry : entries) {
                            sender.sendMessage(ChatColor.GRAY + entry);
                        }
                    }
                }
            }
            case "reset" -> {
                plugin.getAnalyticsService().reset();
                sender.sendMessage(ChatColor.GREEN + "✔ Analytics zurückgesetzt.");
            }
            case "debug" -> {
                if (args.length < 2) {
                    boolean enabled = plugin.getDebugService().isDebugEnabled();
                    sender.sendMessage(ChatColor.GRAY + "Debug-Logging: " + (enabled ? ChatColor.GREEN + "AN" : ChatColor.RED + "AUS"));
                    sender.sendMessage(ChatColor.GRAY + "Nutzung: /" + label + " debug <on|off|errors>");
                } else if ("on".equalsIgnoreCase(args[1])) {
                    plugin.getDebugService().setDebugEnabled(true);
                    sender.sendMessage(ChatColor.GREEN + "✔ Debug-Logging aktiviert.");
                } else if ("off".equalsIgnoreCase(args[1])) {
                    plugin.getDebugService().setDebugEnabled(false);
                    sender.sendMessage(ChatColor.YELLOW + "Debug-Logging deaktiviert.");
                } else if ("errors".equalsIgnoreCase(args[1])) {
                    List<String> errors = plugin.getDebugService().getRecentErrors(10);
                    sender.sendMessage(ChatColor.GOLD + "━━━ Letzte Fehler (" + errors.size() + ") ━━━");
                    if (errors.isEmpty()) {
                        sender.sendMessage(ChatColor.GREEN + "Keine Fehler aufgezeichnet. ✔");
                    } else {
                        for (String error : errors) {
                            sender.sendMessage(ChatColor.GRAY + error);
                        }
                    }
                } else {
                    sender.sendMessage(ChatColor.GRAY + "Nutzung: /" + label + " debug <on|off|errors>");
                }
            }
            case "start" -> {
                if (plugin.getWebServer() != null && plugin.getWebServer().isRunning()) {
                    sender.sendMessage(ChatColor.YELLOW + "Webserver läuft bereits.");
                } else {
                    plugin.startWebServer();
                    sender.sendMessage(ChatColor.GREEN + "✔ Webserver gestartet.");
                }
            }
            case "stop" -> {
                if (plugin.getWebServer() == null || !plugin.getWebServer().isRunning()) {
                    sender.sendMessage(ChatColor.YELLOW + "Webserver ist bereits gestoppt.");
                } else {
                    plugin.stopWebServer();
                    sender.sendMessage(ChatColor.YELLOW + "Webserver gestoppt.");
                }
            }
            case "kills" -> handleKillsCommand(sender, label, args);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private ChatColor tpsColor(double tps) {
        if (tps >= 18.0) return ChatColor.GREEN;
        if (tps >= 15.0) return ChatColor.YELLOW;
        return ChatColor.RED;
    }

    private void sendHelp(CommandSender sender, String label) {
        boolean running = plugin.getWebServer() != null && plugin.getWebServer().isRunning();
        int port = plugin.getConfig().getInt("port", 8080);

        sender.sendMessage(ChatColor.GOLD + "━━━ Serverhost v" + plugin.getDescription().getVersion() + " ━━━ "
                + (running ? ChatColor.GREEN + "● Online" : ChatColor.RED + "● Offline")
                + ChatColor.GRAY + " Port " + port);
        sender.sendMessage(ChatColor.AQUA + " /" + label + " status"
                + ChatColor.GRAY + " ........... " + ChatColor.WHITE + "Status & URL");
        sender.sendMessage(ChatColor.AQUA + " /" + label + " info"
                + ChatColor.GRAY + " ............. " + ChatColor.WHITE + "Spieler, TPS, RAM, Traffic");
        sender.sendMessage(ChatColor.AQUA + " /" + label + " reload"
                + ChatColor.GRAY + " ........... " + ChatColor.WHITE + "Config + Server neustarten");
        sender.sendMessage(ChatColor.AQUA + " /" + label + " start" + ChatColor.GRAY + " / "
                + ChatColor.AQUA + "stop"
                + ChatColor.GRAY + " ....... " + ChatColor.WHITE + "Manuell starten/stoppen");
        sender.sendMessage(ChatColor.AQUA + " /" + label + " gui"
                + ChatColor.GRAY + " ............... " + ChatColor.WHITE + "Dashboard-GUI (Spieler)");
        sender.sendMessage(ChatColor.AQUA + " /" + label + " log"
                + ChatColor.GRAY + " ............... " + ChatColor.WHITE + "Letzte Web-Zugriffe");
        sender.sendMessage(ChatColor.AQUA + " /" + label + " reset"
                + ChatColor.GRAY + " ............. " + ChatColor.WHITE + "Traffic-Daten zurücksetzen");
        sender.sendMessage(ChatColor.AQUA + " /" + label + " debug" + ChatColor.GRAY + " <on|off|errors>"
                + ChatColor.WHITE + "  Debug-Logging");
        sender.sendMessage(ChatColor.AQUA + " /" + label + " kills" + ChatColor.GRAY + " [...]"
                + ChatColor.WHITE + " .......... Kill-Leaderboard");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("webserver.admin")) return List.of();
        if (args.length == 1) {
            return List.of("status", "info", "reload", "start", "stop", "gui", "log", "reset", "debug", "kills")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
            return List.of("on", "off", "errors").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && "kills".equalsIgnoreCase(args[0])) {
            return List.of("list", "info", "set", "add", "remove", "reset")
                    .stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3 && "kills".equalsIgnoreCase(args[0])
                && List.of("info", "set", "add", "remove", "reset").contains(args[1].toLowerCase())) {
            var killService = plugin.getKillStatsService();
            return killService.getKillCache().keySet().stream()
                    .map(uuid -> killService.getPlayerName(uuid))
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    private void handleKillsCommand(CommandSender sender, String label, String[] args) {
        var killService = plugin.getKillStatsService();

        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            if (sender instanceof Player player) {
                plugin.getKillGuiListener().openList(player, 0);
            } else {
                var entries = killService.getAllEntriesSorted();
                sender.sendMessage(ChatColor.GOLD + "━━━ Kill-Leaderboard (" + entries.size() + " Spieler) ━━━");
                if (entries.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Noch keine Spieler getrackt.");
                } else {
                    int rank = 1;
                    for (var entry : entries) {
                        String name = killService.getPlayerName(entry.getKey());
                        sender.sendMessage(ChatColor.WHITE + " " + rank + ". " + ChatColor.AQUA + name
                                + ChatColor.GRAY + " - " + ChatColor.GOLD + entry.getValue() + " Kills");
                        rank++;
                    }
                }
            }
            return;
        }

        switch (args[1].toLowerCase()) {
            case "info" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.GRAY + "Nutzung: /" + label + " kills info <spieler>");
                    return;
                }
                UUID uuid = killService.findUuidByName(args[2]);
                if (uuid == null) {
                    sender.sendMessage(ChatColor.RED + "Spieler \"" + args[2] + "\" nicht gefunden.");
                    return;
                }
                if (sender instanceof Player player) {
                    plugin.getKillGuiListener().openDetail(player, uuid, 0);
                } else {
                    String name = killService.getPlayerName(uuid);
                    int total = killService.getKillCache().getOrDefault(uuid, 0);
                    sender.sendMessage(ChatColor.GOLD + "━━━ " + name + " ━━━");
                    sender.sendMessage(ChatColor.WHITE + " Gesamt: " + ChatColor.AQUA + total + " Kills");
                    if (killService.isTrackingWorlds()) {
                        var worlds = killService.getWorldBreakdown(uuid);
                        if (!worlds.isEmpty()) {
                            worlds.entrySet().stream()
                                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                    .forEach(e -> sender.sendMessage(ChatColor.GRAY + "  " + e.getKey()
                                            + ": " + ChatColor.AQUA + e.getValue()));
                        }
                    }
                }
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.GRAY + "Nutzung: /" + label + " kills set <spieler> <anzahl>");
                    return;
                }
                UUID uuid = killService.findUuidByName(args[2]);
                if (uuid == null) {
                    sender.sendMessage(ChatColor.RED + "Spieler \"" + args[2] + "\" nicht gefunden.");
                    return;
                }
                try {
                    int amount = Integer.parseInt(args[3]);
                    killService.setKills(uuid, amount);
                    sender.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.AQUA + killService.getPlayerName(uuid)
                            + ChatColor.GREEN + " → " + ChatColor.GOLD + Math.max(0, amount) + " Kills");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "\"" + args[3] + "\" ist keine Zahl.");
                }
            }
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.GRAY + "Nutzung: /" + label + " kills add <spieler> <anzahl>");
                    return;
                }
                UUID uuid = killService.findUuidByName(args[2]);
                if (uuid == null) {
                    sender.sendMessage(ChatColor.RED + "Spieler \"" + args[2] + "\" nicht gefunden.");
                    return;
                }
                try {
                    int amount = Integer.parseInt(args[3]);
                    killService.addKills(uuid, amount);
                    int newTotal = killService.getKillCache().getOrDefault(uuid, 0);
                    sender.sendMessage(ChatColor.GREEN + "✔ +" + amount + " → " + ChatColor.AQUA + killService.getPlayerName(uuid)
                            + ChatColor.GREEN + " hat jetzt " + ChatColor.GOLD + newTotal + " Kills");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "\"" + args[3] + "\" ist keine Zahl.");
                }
            }
            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.GRAY + "Nutzung: /" + label + " kills remove <spieler> <anzahl>");
                    return;
                }
                UUID uuid = killService.findUuidByName(args[2]);
                if (uuid == null) {
                    sender.sendMessage(ChatColor.RED + "Spieler \"" + args[2] + "\" nicht gefunden.");
                    return;
                }
                try {
                    int amount = Integer.parseInt(args[3]);
                    killService.removeKills(uuid, amount);
                    int newTotal = killService.getKillCache().getOrDefault(uuid, 0);
                    sender.sendMessage(ChatColor.GREEN + "✔ -" + amount + " → " + ChatColor.AQUA + killService.getPlayerName(uuid)
                            + ChatColor.GREEN + " hat jetzt " + ChatColor.GOLD + newTotal + " Kills");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "\"" + args[3] + "\" ist keine Zahl.");
                }
            }
            case "reset" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.GRAY + "Nutzung: /" + label + " kills reset <spieler>");
                    return;
                }
                UUID uuid = killService.findUuidByName(args[2]);
                if (uuid == null) {
                    sender.sendMessage(ChatColor.RED + "Spieler \"" + args[2] + "\" nicht gefunden.");
                    return;
                }
                killService.setKills(uuid, 0);
                sender.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.AQUA + killService.getPlayerName(uuid)
                        + ChatColor.GREEN + " → " + ChatColor.GOLD + "0 Kills");
            }
            default -> {
                sender.sendMessage(ChatColor.GOLD + "━━━ Kill-Befehle ━━━");
                sender.sendMessage(ChatColor.AQUA + " /" + label + " kills"
                        + ChatColor.GRAY + " .................. " + ChatColor.WHITE + "Leaderboard anzeigen");
                sender.sendMessage(ChatColor.AQUA + " /" + label + " kills info <spieler>"
                        + ChatColor.GRAY + " .. " + ChatColor.WHITE + "Details");
                sender.sendMessage(ChatColor.AQUA + " /" + label + " kills set <spieler> <n>"
                        + ChatColor.GRAY + "  " + ChatColor.WHITE + "Setzen");
                sender.sendMessage(ChatColor.AQUA + " /" + label + " kills add <spieler> <n>"
                        + ChatColor.GRAY + "  " + ChatColor.WHITE + "Hinzufügen");
                sender.sendMessage(ChatColor.AQUA + " /" + label + " kills remove <spieler> <n>"
                        + ChatColor.WHITE + " Entfernen");
                sender.sendMessage(ChatColor.AQUA + " /" + label + " kills reset <spieler>"
                        + ChatColor.GRAY + " . " + ChatColor.WHITE + "Zurücksetzen");
            }
        }
    }
}
