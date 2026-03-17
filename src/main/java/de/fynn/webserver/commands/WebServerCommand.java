package de.fynn.webserver.commands;

import de.fynn.webserver.WebServerPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

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
            sendHelp(sender);
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
                                sender.sendMessage(ChatColor.GREEN + "Git-Pull erfolgreich.");
                            } else {
                                sender.sendMessage(ChatColor.RED + "✘ Git-Pull fehlgeschlagen! Prüfe die Konsole für Details.");
                            }
                        }
                        if (result.serverStarted) {
                            sender.sendMessage(ChatColor.GREEN + "✔ Webserver wurde neu geladen!");
                        } else {
                            sender.sendMessage(ChatColor.RED + "✘ Webserver konnte nicht gestartet werden! Prüfe Port und Konsole.");
                        }
                    });
                });
            }
            case "status" -> {
                boolean running = plugin.getWebServer() != null && plugin.getWebServer().isRunning();
                int port = plugin.getConfig().getInt("port", 8080);
                sender.sendMessage(ChatColor.GOLD + "=== WebServer Status ===");
                sender.sendMessage(ChatColor.WHITE + "Status: " + (running
                        ? ChatColor.GREEN + "Online"
                        : ChatColor.RED + "Offline"));
                sender.sendMessage(ChatColor.WHITE + "Port: " + ChatColor.AQUA + port);
                sender.sendMessage(ChatColor.WHITE + "Web-Root: " + ChatColor.AQUA
                        + plugin.getConfig().getString("web-root", "html"));
            }
            case "stop" -> {
                plugin.stopWebServer();
                sender.sendMessage(ChatColor.RED + "Webserver gestoppt.");
            }
            case "start" -> {
                plugin.startWebServer();
                sender.sendMessage(ChatColor.GREEN + "Webserver gestartet.");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== WebServer Hilfe ===");
        sender.sendMessage(ChatColor.WHITE + "/webserver status " + ChatColor.GRAY + "- Zeigt den Server-Status");
        sender.sendMessage(ChatColor.WHITE + "/webserver reload " + ChatColor.GRAY + "- Konfiguration neu laden");
        sender.sendMessage(ChatColor.WHITE + "/webserver start " + ChatColor.GRAY + "- Server starten");
        sender.sendMessage(ChatColor.WHITE + "/webserver stop " + ChatColor.GRAY + "- Server stoppen");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String sub : List.of("status", "reload", "start", "stop")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        return List.of();
    }
}
