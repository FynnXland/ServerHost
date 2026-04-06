package de.fynn.webserver.stats;

import com.sun.management.OperatingSystemMXBean;
import de.fynn.webserver.analytics.AnalyticsSnapshot;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("deprecation")
public class RuntimeStatsService {

    private final JavaPlugin plugin;
    private final AtomicLong cachedStorageBytes = new AtomicLong(0);
    private final AtomicLong lastStorageRefresh = new AtomicLong(0);
    private volatile McStatsCache mcStatsCache = McStatsCache.empty();
    private volatile BukkitTask mcStatsTask;
    private volatile double smoothedCpuPercent = 0.0;
    private volatile long diskTotalGb;

    public RuntimeStatsService(JavaPlugin plugin, long diskTotalGb) {
        this.plugin = plugin;
        this.diskTotalGb = Math.max(1, diskTotalGb);
    }

    public void setDiskTotalGb(long diskTotalGb) {
        this.diskTotalGb = Math.max(1, diskTotalGb);
    }

    public void start() {
        stop();
        mcStatsTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshMcStatsCacheSync, 1L, 20L);
        refreshMcStatsCacheSync();
    }

    public void stop() {
        if (mcStatsTask != null) {
            mcStatsTask.cancel();
            mcStatsTask = null;
        }
    }

    public RuntimeStatsSnapshot snapshot(AnalyticsSnapshot analytics) {
        long now = System.currentTimeMillis();
        long storage = getStorageBytesCached(now);
        long ramBytes = getProcessMemoryBytes();
        double cpuPercent = getCpuPercent();
        McStatsCache mc = mcStatsCache;
        Runtime runtime = Runtime.getRuntime();
        HardwareInfo hardwareInfo = detectHardwareInfo();
        return new RuntimeStatsSnapshot(
                Instant.ofEpochMilli(now).toString(),
                mc.onlinePlayers(),
                mc.maxPlayers(),
                mc.motd(),
                mc.version(),
                mc.bukkitVersion(),
                mc.serverName(),
                mc.loadedWorlds(),
                mc.loadedChunks(),
                mc.loadedEntities(),
                hardwareInfo.osName(),
                hardwareInfo.osVersion(),
                hardwareInfo.osArch(),
                hardwareInfo.javaVersion(),
                hardwareInfo.cpuModel(),
                hardwareInfo.cpuLogicalCores(),
                hardwareInfo.gpuModel(),
                cpuPercent,
                ramBytes,
                runtime.maxMemory(),
                mc.onlineMode() ? 1 : 0,
                storage,
                diskTotalGb * 1024L * 1024L * 1024L,
                diskTotalGb,
                mc.tps(),
                analytics,
                mc.players()
        );
    }

    private void refreshMcStatsCacheSync() {
        try {
            int totalChunks = 0;
            int totalEntities = 0;
            for (World world : Bukkit.getWorlds()) {
                totalChunks += world.getLoadedChunks().length;
                totalEntities += world.getEntities().size();
            }
            double tps = getCurrentTpsOnMainThread();
            List<PlayerEntry> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(new PlayerEntry(p.getName(), p.getUniqueId().toString()));
            }
            mcStatsCache = new McStatsCache(
                    Bukkit.getOnlinePlayers().size(),
                    Bukkit.getMaxPlayers(),
                    ChatColor.stripColor(Bukkit.getMotd()),
                    Bukkit.getVersion(),
                    Bukkit.getBukkitVersion(),
                    Bukkit.getName(),
                    Bukkit.getWorlds().size(),
                    totalChunks,
                    totalEntities,
                    Bukkit.getOnlineMode(),
                    tps,
                    Collections.unmodifiableList(players)
            );
        } catch (Exception ignored) {
            // keep last cache
        }
    }

    private long getStorageBytesCached(long nowMillis) {
        long previous = lastStorageRefresh.get();
        if (nowMillis - previous < 60_000 && previous != 0) {
            return cachedStorageBytes.get();
        }
        long size = computeDirectorySize(new File("."));
        cachedStorageBytes.set(size);
        lastStorageRefresh.set(nowMillis);
        return size;
    }

    private long getProcessMemoryBytes() {
        Path procStatus = Path.of("/proc/self/status");
        if (Files.exists(procStatus)) {
            try {
                for (String line : Files.readAllLines(procStatus)) {
                    if (line.startsWith("VmRSS:")) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 2) {
                            return Long.parseLong(parts[1]) * 1024L;
                        }
                    }
                }
            } catch (Exception ignored) {
                // fallback below
            }
        }
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private double getCpuPercent() {
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double processLoad = osBean.getProcessCpuLoad();
            if (processLoad < 0) {
                return Math.max(0.0, smoothedCpuPercent);
            }
            // getProcessCpuLoad liefert bereits einen 0..1 Anteil für den Prozess.
            // Multiplikation mit Cores führte zu irreführenden Sprüngen (z.B. 0/10%).
            double rawPercent = Math.max(0.0, processLoad * 100.0);
            if (smoothedCpuPercent <= 0.0) {
                smoothedCpuPercent = rawPercent;
            } else {
                smoothedCpuPercent = (smoothedCpuPercent * 0.7) + (rawPercent * 0.3);
            }
            return smoothedCpuPercent;
        } catch (Exception ignored) {
            return Math.max(0.0, smoothedCpuPercent);
        }
    }

    private double getCurrentTpsOnMainThread() {
        try {
            double[] tpsValues = Bukkit.getServer().getTPS();
            if (tpsValues.length == 0) {
                return 20.0;
            }
            return tpsValues[0];
        } catch (Exception ignored) {
            return 20.0;
        }
    }

    private long computeDirectorySize(File root) {
        if (root == null || !root.exists()) {
            return 0;
        }
        if (root.isFile()) {
            return root.length();
        }
        long total = 0;
        File[] children = root.listFiles();
        if (children == null) {
            return 0;
        }
        for (File child : children) {
            total += computeDirectorySize(child);
        }
        return total;
    }

    private HardwareInfo detectHardwareInfo() {
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "unknown");
        String osArch = System.getProperty("os.arch", "unknown");
        String javaVersion = System.getProperty("java.version", "unknown");
        String cpuModel = detectCpuModel();
        int cores = Runtime.getRuntime().availableProcessors();
        String gpuModel = detectGpuModel();
        return new HardwareInfo(osName, osVersion, osArch, javaVersion, cpuModel, cores, gpuModel);
    }

    private String detectCpuModel() {
        try {
            Path cpuInfo = Path.of("/proc/cpuinfo");
            if (Files.exists(cpuInfo)) {
                for (String line : Files.readAllLines(cpuInfo)) {
                    if (line.toLowerCase(Locale.ROOT).startsWith("model name")) {
                        String[] parts = line.split(":", 2);
                        if (parts.length == 2) {
                            return parts[1].trim();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }
        String env = System.getenv("PROCESSOR_IDENTIFIER");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return "unknown";
    }

    private String detectGpuModel() {
        try {
            Path dri = Path.of("/proc/driver/nvidia/gpus");
            if (Files.exists(dri) && Files.isDirectory(dri)) {
                File[] gpuDirs = dri.toFile().listFiles();
                if (gpuDirs != null && gpuDirs.length > 0) {
                    Path info = gpuDirs[0].toPath().resolve("information");
                    if (Files.exists(info)) {
                        for (String line : Files.readAllLines(info)) {
                            if (line.startsWith("Model:")) {
                                return line.substring("Model:".length()).trim();
                            }
                        }
                    }
                    return "nvidia-detected";
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }

        String nvidiaVisible = System.getenv("NVIDIA_VISIBLE_DEVICES");
        if (nvidiaVisible != null && !nvidiaVisible.isBlank() && !"void".equalsIgnoreCase(nvidiaVisible)) {
            return "nvidia-visible (" + nvidiaVisible + ")";
        }
        return "unknown";
    }

    private record HardwareInfo(
            String osName,
            String osVersion,
            String osArch,
            String javaVersion,
            String cpuModel,
            int cpuLogicalCores,
            String gpuModel
    ) {
    }

    private record McStatsCache(
            int onlinePlayers,
            int maxPlayers,
            String motd,
            String version,
            String bukkitVersion,
            String serverName,
            int loadedWorlds,
            int loadedChunks,
            int loadedEntities,
            boolean onlineMode,
            double tps,
            List<PlayerEntry> players
    ) {
        private static McStatsCache empty() {
            return new McStatsCache(0, 0, "", "unknown", "unknown", "unknown", 0, 0, 0, false, 20.0, Collections.emptyList());
        }
    }

}
